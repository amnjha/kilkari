import Foundation

/// What a holding has actually put in, and when.
///
/// A contribution is not a typed row here: a SIP is a standing instruction, so the instalments
/// are worked out from the plan rather than entered one by one. That keeps the invest screen
/// honest with no data entry, and it is also what lets a fund-paid holding appear on the fund's
/// ledger — each instalment becomes a line without anyone recording one.
///
/// One definition, used by both screens. Working it out twice is how "in ₹55,000" ends up
/// beside a return calculated on ₹50,000.
enum Money {

    struct Instalment {
        /// Position in the series, so a derived ledger line keeps the same id between renders.
        let index: Int
        let date: Date
        let amount: Int
    }

    static func instalments(of holding: Investment, upTo end: Date = .now,
                            calendar: Calendar = .current) -> [Instalment] {
        guard holding.kind.recurring, let monthly = holding.monthlyAmount else {
            guard holding.startedOn <= end else { return [] }
            return [Instalment(index: 0, date: holding.startedOn, amount: holding.investedAmount)]
        }
        let months = calendar.dateComponents([.month], from: holding.startedOn, to: end).month ?? 0
        guard months >= 0 else { return [] }
        return (0...months).compactMap { m in
            calendar.date(byAdding: .month, value: m, to: holding.startedOn)
                .map { Instalment(index: m, date: $0, amount: monthly) }
        }
    }

    /// The same series as pairs, for the return maths.
    static func contributions(of holding: Investment, upTo end: Date = .now)
        -> [(date: Date, amountInr: Int64)] {
        instalments(of: holding, upTo: end).map { ($0.date, Int64($0.amount)) }
    }

    static func paidIn(_ holding: Investment, upTo end: Date = .now) -> Int {
        instalments(of: holding, upTo: end).reduce(0) { $0 + $1.amount }
    }
}
