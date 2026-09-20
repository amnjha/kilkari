import Foundation

/// Which growth curve a reading is compared against.
public enum Sex: String, Sendable, Codable, CaseIterable {
    case boy
    case girl
}

/// Weight-for-age from the WHO Child Growth Standards (2006), birth to two years.
///
/// The standards are published as an LMS triple per age: a skew (L), the median (M) and a
/// coefficient of variation (S). Together they describe the whole distribution, not just its
/// middle, which is what lets the chart draw the percentile bands and say where one reading
/// sits. The values are read from `common/data/who-weight-for-age.json`, the same file the
/// Android app reads, so the two can never disagree.
///
/// A percentile is a position among healthy children, not a grade: the 15th and the 85th are
/// both ordinary. What matters clinically is the shape of a child's own line over time, and
/// only a clinician reading the full chart can say whether a reading needs attention.
public enum GrowthStandards {

    /// One age's published parameters: skew, median in kg, and coefficient of variation.
    public struct LMS: Decodable, Sendable, Equatable {
        public let l: Double
        public let m: Double
        public let s: Double
    }

    private struct Table: Decodable {
        let maxMonths: Int
        let bands: [Int]
        let boys: [LMS]
        let girls: [LMS]
    }

    private static let table = SharedData.load(Table.self, from: "who-weight-for-age")

    /// The oldest age the published table covers.
    public static var maxMonths: Int { table.maxMonths }

    /// The bands the chart draws, as the WHO 0-2 years percentile chart does. The 50th is
    /// drawn separately as the median line.
    public static var bands: [Int] { table.bands }

    /// Median expected weight at an age in months. Nil beyond the end of the table.
    public static func medianWeightKg(months: Double, sex: Sex?) -> Double? {
        lms(months: months, sex: sex)?.m
    }

    /// The weight at a percentile for a child of this age — the 3rd, 15th, 85th and 97th are
    /// what the chart shades between.
    public static func weightAtPercentile(months: Double, sex: Sex?, percentile: Int) -> Double? {
        guard let p = lms(months: months, sex: sex) else { return nil }
        let z = zForPercentile(percentile)
        if abs(p.l) < 1e-7 {
            return p.m * exp(p.s * z)
        }
        return p.m * pow(1 + p.l * p.s * z, 1 / p.l)
    }

    /// Where a reading sits among healthy children of the same age and sex, 1 to 99.
    ///
    /// Clamped at the ends rather than reported precisely: past three standard deviations the
    /// distribution is extrapolated, and "0.2nd" would read as precision the number does not
    /// have. Needs the sex — a percentile averaged across boys and girls would be a number
    /// about nobody.
    public static func percentile(ofKg kg: Double, months: Double, sex: Sex) -> Int? {
        guard let z = zScore(kg: kg, months: months, sex: sex) else { return nil }
        return min(99, max(1, Int((percentileForZ(z) * 100).rounded())))
    }

    /// Standard deviations from the median, by the WHO's own LMS formula.
    public static func zScore(kg: Double, months: Double, sex: Sex?) -> Double? {
        guard let p = lms(months: months, sex: sex), kg > 0 else { return nil }
        if abs(p.l) < 1e-7 {
            return log(kg / p.m) / p.s
        }
        return (pow(kg / p.m, p.l) - 1) / (p.l * p.s)
    }

    /// The published parameters at an age, straight-lined between the monthly rows so a chart
    /// drawn at any age is smooth. Averaging the two sexes is a fallback for a child whose sex
    /// is not recorded: it gives the curve roughly the right shape, and the screen says so.
    private static func lms(months: Double, sex: Sex?) -> LMS? {
        guard months >= 0, months <= Double(maxMonths) else { return nil }
        let lower = min(Int(months), maxMonths)
        let current = at(month: lower, sex: sex)
        if lower == maxMonths { return current }
        let next = at(month: lower + 1, sex: sex)
        let f = months - Double(lower)
        return LMS(
            l: current.l + (next.l - current.l) * f,
            m: current.m + (next.m - current.m) * f,
            s: current.s + (next.s - current.s) * f
        )
    }

    private static func at(month: Int, sex: Sex?) -> LMS {
        switch sex {
        case .boy: return table.boys[month]
        case .girl: return table.girls[month]
        case nil:
            let b = table.boys[month]
            let g = table.girls[month]
            return LMS(l: (b.l + g.l) / 2, m: (b.m + g.m) / 2, s: (b.s + g.s) / 2)
        }
    }

    /// The z a percentile sits at: the 3rd is 1.88 standard deviations below the median.
    ///
    /// Newton on the normal CDF — a handful of steps from a decent guess is exact enough for a
    /// chart line, and keeps a table of constants out of the file.
    static func zForPercentile(_ percentile: Int) -> Double {
        let target = Double(percentile) / 100.0
        var z = 0.0
        for _ in 0..<40 {
            let density = exp(-z * z / 2) / (2 * Double.pi).squareRoot()
            if density < 1e-12 { return z }
            z += (target - percentileForZ(z)) / density
        }
        return z
    }

    /// The normal CDF, to about seven decimal places (Abramowitz & Stegun 7.1.26).
    static func percentileForZ(_ z: Double) -> Double {
        let x = z / 2.0.squareRoot()
        let t = 1.0 / (1.0 + 0.3275911 * abs(x))
        let y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592)
            * t * exp(-x * x)
        let erf = (x < 0 ? -1.0 : (x > 0 ? 1.0 : 0.0)) * y
        return 0.5 * (1 + erf)
    }
}
