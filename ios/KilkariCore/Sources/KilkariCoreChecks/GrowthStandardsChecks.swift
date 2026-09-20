import KilkariCore

/// The same assertions the Android app makes, against the WHO weight-for-age percentile
/// tables the LMS values come from. Two platforms reading one data file is only worth
/// anything if both are checked against the source rather than against each other.
enum GrowthStandardsChecks {

    static func run() {
        Check.group("medians match the published table") {
            Check.close(GrowthStandards.medianWeightKg(months: 0, sex: .boy)!, 3.3464, accuracy: 0.0001, "boy 0mo")
            Check.close(GrowthStandards.medianWeightKg(months: 0, sex: .girl)!, 3.2322, accuracy: 0.0001, "girl 0mo")
            Check.close(GrowthStandards.medianWeightKg(months: 12, sex: .boy)!, 9.646, accuracy: 0.0001, "boy 12mo")
            Check.close(GrowthStandards.medianWeightKg(months: 24, sex: .girl)!, 11.4741, accuracy: 0.0001, "girl 24mo")
        }

        // Against WHO's own published percentile tables, not its z-score lines, which are a
        // different thing: −2 SD is the 2.3rd percentile, not the 3rd.
        Check.group("percentile curves match the WHO percentile tables") {
            func band(_ months: Double, _ sex: Sex, _ p: Int, _ expected: Double) {
                Check.close(
                    GrowthStandards.weightAtPercentile(months: months, sex: sex, percentile: p)!,
                    expected, accuracy: 0.01, "\(sex) \(months)mo p\(p)"
                )
            }
            band(0, .boy, 3, 2.507)
            band(0, .boy, 15, 2.865)
            band(0, .boy, 85, 3.878)
            band(0, .boy, 97, 4.350)
            band(12, .boy, 3, 7.844)
            band(12, .boy, 97, 11.830)
            band(24, .boy, 3, 9.802)
            band(24, .boy, 97, 15.065)
            band(0, .girl, 3, 2.440)
            band(0, .girl, 97, 4.166)
            band(12, .girl, 3, 7.140)
            band(12, .girl, 15, 7.891)
            band(12, .girl, 85, 10.176)
            band(12, .girl, 97, 11.331)
        }

        Check.group("the median is the fiftieth percentile") {
            let median = GrowthStandards.medianWeightKg(months: 6, sex: .girl)!
            Check.close(
                GrowthStandards.weightAtPercentile(months: 6, sex: .girl, percentile: 50)!,
                median, accuracy: 0.001, "p50 is the median"
            )
            Check.equal(GrowthStandards.percentile(ofKg: median, months: 6, sex: .girl), 50, "median reads back")
        }

        Check.group("a weight on a band reads back as that percentile") {
            for p in [3, 15, 85, 97] {
                let kg = GrowthStandards.weightAtPercentile(months: 9, sex: .boy, percentile: p)!
                Check.equal(GrowthStandards.percentile(ofKg: kg, months: 9, sex: .boy), p, "round trip p\(p)")
            }
        }

        Check.group("percentiles are clamped rather than reported past the table's reach") {
            Check.equal(GrowthStandards.percentile(ofKg: 1.5, months: 6, sex: .boy), 1, "floor")
            Check.equal(GrowthStandards.percentile(ofKg: 14.0, months: 6, sex: .boy), 99, "ceiling")
        }

        Check.group("nothing is claimed beyond two years or before birth") {
            Check.isNil(GrowthStandards.medianWeightKg(months: 24.5, sex: .boy), "past the table")
            Check.isNil(GrowthStandards.medianWeightKg(months: -1, sex: .girl), "before birth")
            Check.isNil(GrowthStandards.percentile(ofKg: 9, months: 30, sex: .boy), "percentile past the table")
        }

        Check.group("a curve between two months sits between the two published rows") {
            let at6 = GrowthStandards.medianWeightKg(months: 6, sex: .boy)!
            let at7 = GrowthStandards.medianWeightKg(months: 7, sex: .boy)!
            let half = GrowthStandards.medianWeightKg(months: 6.5, sex: .boy)!
            Check.isTrue(half > at6 && half < at7, "6.5mo interpolates")
        }

        Check.group("an unrecorded sex sits between the boys' and girls' curves") {
            let boy = GrowthStandards.medianWeightKg(months: 12, sex: .boy)!
            let girl = GrowthStandards.medianWeightKg(months: 12, sex: .girl)!
            let either = GrowthStandards.medianWeightKg(months: 12, sex: nil)!
            Check.isTrue(either >= girl && either <= boy, "averaged curve")
        }

        // The point of the shared file: if the two apps ever stop reading the same table,
        // this is where it shows up.
        Check.group("the shared table is the one Android reads") {
            Check.equal(GrowthStandards.maxMonths, 24, "maxMonths")
            Check.equal(GrowthStandards.bands, [3, 15, 85, 97], "bands")
        }
    }
}
