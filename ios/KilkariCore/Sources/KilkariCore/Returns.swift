import Foundation

/// The kinds of holding the fund can carry. `recurring` ones are paid monthly.
public enum InvestmentKind: String, Sendable, Codable, CaseIterable {
    case fd
    case rd
    case sip
    case ppf
    case ssy
    case other

    /// Whether money goes in every month rather than once at the start.
    public var recurring: Bool {
        switch self {
        case .rd, .sip, .ppf, .ssy: return true
        case .fd, .other: return false
        }
    }
}

/// What a holding has actually earned, and what it should come to if the rate holds.
///
/// Both are arithmetic on numbers the parent has already entered — contributions, the value
/// they last wrote down, the rate the bank quoted. Nothing is fetched: the app has no network
/// and no price feed, so a SIP's return is only as current as the last value entered by hand,
/// and the screen says so rather than implying a live number.
public enum Returns {

    /// A year, for annualising. The average Gregorian year, so leap years do not skew a rate.
    public static let daysInYear = 365.2425

    /// Below this, a return is arithmetic noise on a few weeks of holding.
    public static let minDaysForRate = 30

    /// Money in on a day. Contributions are negative, what it is worth today is positive.
    public struct Flow: Sendable, Equatable {
        public let date: Date
        public let amount: Double

        public init(date: Date, amount: Double) {
            self.date = date
            self.amount = amount
        }
    }

    /// The annualised return that makes these dated flows add up to nothing — the standard
    /// XIRR, and the only fair way to compare a lump sum against money paid in monthly.
    ///
    /// Nil when there is nothing to solve: no flows on both sides, too short a span, or a
    /// series with no rate that balances it (a holding that lost everything, for instance).
    public static func xirr(_ flows: [Flow]) -> Double? {
        guard flows.count >= 2,
              flows.contains(where: { $0.amount > 0 }),
              flows.contains(where: { $0.amount < 0 })
        else { return nil }

        let first = flows.map(\.date).min()!
        let last = flows.map(\.date).max()!
        guard Calendar.days(from: first, to: last) >= minDaysForRate else { return nil }

        func npv(_ rate: Double) -> Double {
            flows.reduce(0) { total, flow in
                let years = Double(Calendar.days(from: first, to: flow.date)) / daysInYear
                return total + flow.amount / pow(1 + rate, years)
            }
        }

        // Bisection rather than Newton: slower, but it cannot run away from a sane bracket,
        // and the bracket here is every rate worth showing a parent.
        var low = -0.9999
        var high = 10.0
        var lowValue = npv(low)
        let highValue = npv(high)
        guard lowValue.isFinite, highValue.isFinite, lowValue * highValue <= 0 else { return nil }

        for _ in 0..<200 {
            let mid = (low + high) / 2
            let value = npv(mid)
            if abs(value) < 0.01 { return mid }
            if lowValue * value <= 0 {
                high = mid
            } else {
                low = mid
                lowValue = value
            }
        }
        return (low + high) / 2
    }

    /// XIRR for a holding: every contribution as money out, and what it is worth now as money
    /// back on the day that value was written down.
    public static func holdingReturn(
        contributions: [(date: Date, amountInr: Int64)],
        valueInr: Int64,
        valuedOn: Date
    ) -> Double? {
        guard !contributions.isEmpty, valueInr > 0 else { return nil }
        let flows = contributions.map { Flow(date: $0.date, amount: -Double($0.amountInr)) }
            + [Flow(date: valuedOn, amount: Double(valueInr))]
        return xirr(flows)
    }

    /// What a fixed instrument comes to at maturity on the rate entered.
    ///
    /// Compounded quarterly, which is how Indian deposits are quoted, and stated as such on
    /// the screen — a projection whose assumptions are hidden is just a number. Recurring
    /// plans compound each instalment from the month it is paid.
    public static func projectedMaturityInr(
        kind: InvestmentKind,
        investedInr: Int64,
        monthlyInr: Int64?,
        ratePercent: Double?,
        start: Date,
        maturity: Date?
    ) -> Int64? {
        guard let rate = ratePercent, rate > 0, let end = maturity, end > start else { return nil }
        let r = rate / 100.0

        if !kind.recurring {
            let years = Double(Calendar.days(from: start, to: end)) / daysInYear
            return Int64(Double(investedInr) * pow(1 + r / quarters, quarters * years))
        }

        guard let monthly = monthlyInr, monthly > 0 else { return nil }
        let months = Calendar.wholeMonths(from: start, to: end)
        guard months > 0 else { return nil }

        // Each instalment earns from the month it goes in, so the first compounds longest.
        var total = 0.0
        for month in 0..<months {
            let years = Double(months - month) / 12.0
            total += Double(monthly) * pow(1 + r / quarters, quarters * years)
        }
        return Int64(total)
    }

    /// Indian deposits are quoted on quarterly compounding.
    private static let quarters = 4.0
}

extension Calendar {

    /// Whole days between two instants, on the calendar rather than by dividing seconds, so a
    /// daylight-saving boundary does not turn a day into 23 hours and round the wrong way.
    static func days(from: Date, to: Date) -> Int {
        let cal = Calendar(identifier: .gregorian)
        let a = cal.startOfDay(for: from)
        let b = cal.startOfDay(for: to)
        return cal.dateComponents([.day], from: a, to: b).day ?? 0
    }

    /// Whole months between the first of each date's month, matching the Android side's
    /// `withDayOfMonth(1)` before counting.
    static func wholeMonths(from: Date, to: Date) -> Int {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC") ?? .current
        let a = cal.dateComponents([.year, .month], from: from)
        let b = cal.dateComponents([.year, .month], from: to)
        guard let ay = a.year, let am = a.month, let by = b.year, let bm = b.month else { return 0 }
        return (by - ay) * 12 + (bm - am)
    }
}
