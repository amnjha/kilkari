import Foundation
import KilkariCore

/// Units, returns and the shared schedules, asserting what the Kotlin tests assert.
enum CoreChecks {

    private static func date(_ y: Int, _ m: Int, _ d: Int) -> Date {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        return cal.date(from: DateComponents(year: y, month: m, day: d))!
    }

    static func runUnits() {
        Check.group("pounds and ounces read the way a scale does") {
            let a = Units.lbOz(3.402)
            Check.equal(a.lb, 7, "3.402kg pounds")
            Check.equal(a.oz, 8, "3.402kg ounces")
            let zero = Units.lbOz(0)
            Check.equal(zero.lb, 0, "zero pounds")
            Check.equal(zero.oz, 0, "zero ounces")
        }

        Check.group("a whisker under a pound rounds up to the pound, not to sixteen ounces") {
            let r = Units.lbOz(Units.lbToKg(0.9999))
            Check.equal(r.lb, 1, "pounds")
            Check.equal(r.oz, 0, "ounces")
        }

        Check.group("what is typed in pounds and ounces comes back as the same weight") {
            let kg = Units.fromLbOz(lb: 7, oz: 8)
            Check.close(kg, 3.402, accuracy: 0.001, "kg")
            let back = Units.lbOz(kg)
            Check.equal(back.lb, 7, "round trip pounds")
            Check.equal(back.oz, 8, "round trip ounces")
        }

        Check.group("inches convert back to the centimetres they came from") {
            let cm = Units.lengthFromField(Units.cmToIn(54.5), metric: false)
            Check.close(cm, 54.5, accuracy: 0.0001, "cm")
        }

        Check.group("a metric field is left alone") {
            Check.close(Units.lengthFromField(54.5, metric: true), 54.5, accuracy: 0.0001, "value")
            Check.equal(Units.lengthField(cm: 54.5, metric: true), "54.5", "label")
        }

        Check.group("a gain shows in grams or in ounces, never in pounds") {
            Check.equal(Units.deltaLabel(kgDelta: 0.15, metric: true), "+150 g", "metric gain")
            Check.equal(Units.deltaLabel(kgDelta: 0.15, metric: false), "+5.3 oz", "imperial gain")
            Check.equal(Units.deltaLabel(kgDelta: -0.15, metric: true), "−150 g", "metric loss")
        }

        Check.group("weight reads as pounds and ounces, length as inches") {
            Check.equal(Units.weightLabel(3.402), "7 lb 8 oz", "weight")
            Check.equal(Units.lengthLabel(54.5), "21.5 in", "length")
        }
    }

    static func runReturns() {
        Check.group("a lump sum that doubles in a year is about a hundred percent") {
            let rate = Returns.holdingReturn(
                contributions: [(date(2025, 1, 1), 100_000)],
                valueInr: 200_000,
                valuedOn: date(2026, 1, 1)
            )
            Check.notNil(rate, "rate")
            if let rate { Check.close(rate, 1.0, accuracy: 0.01, "rate") }
        }

        Check.group("a flat holding has no return") {
            let rate = Returns.holdingReturn(
                contributions: [(date(2025, 1, 1), 50_000)],
                valueInr: 50_000,
                valuedOn: date(2026, 1, 1)
            )
            Check.notNil(rate, "rate")
            if let rate { Check.close(rate, 0.0, accuracy: 0.001, "rate") }
        }

        Check.group("a loss reads as a negative rate") {
            let rate = Returns.holdingReturn(
                contributions: [(date(2025, 1, 1), 100_000)],
                valueInr: 90_000,
                valuedOn: date(2026, 1, 1)
            )
            Check.notNil(rate, "rate")
            if let rate {
                Check.isTrue(rate < 0, "is a loss")
                Check.close(rate, -0.10, accuracy: 0.01, "rate")
            }
        }

        Check.group("monthly payments are annualised over the time each one was in") {
            // Twelve monthly 10k payments worth 130k at the end: the money was in for an
            // average of about half the year, so the annual rate is well above the 8% the
            // total grew by.
            var cal = Calendar(identifier: .gregorian)
            cal.timeZone = TimeZone(identifier: "UTC")!
            let start = date(2025, 1, 1)
            let contributions = (0..<12).map {
                (cal.date(byAdding: .month, value: $0, to: start)!, Int64(10_000))
            }
            let rate = Returns.holdingReturn(
                contributions: contributions,
                valueInr: 130_000,
                valuedOn: date(2025, 12, 31)
            )
            Check.notNil(rate, "rate")
            if let rate {
                Check.isTrue(rate > 0.08, "above the 8% the total grew by, got \(rate)")
                Check.isTrue(rate < 0.40, "below 40%, got \(rate)")
            }
        }

        Check.group("nothing is claimed from too little to go on") {
            Check.isNil(
                Returns.holdingReturn(contributions: [], valueInr: 1000, valuedOn: date(2026, 1, 1)),
                "no contributions"
            )
            Check.isNil(
                Returns.holdingReturn(
                    contributions: [(date(2026, 1, 1), 1000)], valueInr: 0, valuedOn: date(2026, 6, 1)
                ),
                "worth nothing"
            )
            // Three weeks is not a year's worth of anything.
            Check.isNil(
                Returns.holdingReturn(
                    contributions: [(date(2026, 1, 1), 1000)], valueInr: 1100, valuedOn: date(2026, 1, 21)
                ),
                "three weeks"
            )
        }

        Check.group("a fixed deposit compounds quarterly to the published figure") {
            // 100,000 at 7% for 5 years, compounded quarterly: 100000 * (1.0175)^20 = 141,478.
            let value = Returns.projectedMaturityInr(
                kind: .fd,
                investedInr: 100_000,
                monthlyInr: nil,
                ratePercent: 7,
                start: date(2025, 1, 1),
                maturity: date(2030, 1, 1)
            )
            Check.notNil(value, "value")
            if let value { Check.close(Double(value), 141_478, accuracy: 200, "maturity") }
        }

        Check.group("a recurring plan compounds each instalment from the month it goes in") {
            let value = Returns.projectedMaturityInr(
                kind: .rd,
                investedInr: 0,
                monthlyInr: 5_000,
                ratePercent: 7,
                start: date(2025, 1, 1),
                maturity: date(2026, 1, 1)
            )
            Check.notNil(value, "value")
            if let value {
                // Twelve 5k instalments is 60k in; a year at 7% adds a few thousand, not none
                // and not a second year's worth.
                Check.isTrue(value > 60_000, "earns something, got \(value)")
                Check.isTrue(value < 64_000, "not a full year on every rupee, got \(value)")
            }
        }

        Check.group("a projection needs a rate and a maturity date") {
            Check.isNil(
                Returns.projectedMaturityInr(
                    kind: .fd, investedInr: 100_000, monthlyInr: nil,
                    ratePercent: nil, start: date(2025, 1, 1), maturity: date(2030, 1, 1)
                ),
                "no rate"
            )
            Check.isNil(
                Returns.projectedMaturityInr(
                    kind: .fd, investedInr: 100_000, monthlyInr: nil,
                    ratePercent: 7, start: date(2025, 1, 1), maturity: nil
                ),
                "no maturity"
            )
        }
    }

    static func runSchedules() {
        Check.group("the shared schedules are the ones Android compiles") {
            Check.equal(VaccineSchedules.all.map(\.id), ["iap", "uip", "who", "cdc"], "ids")
            Check.equal(VaccineSchedules.all.map { $0.groups.count }, [10, 6, 6, 6], "group counts")
            let total = VaccineSchedules.all.reduce(0) { sum, s in
                sum + s.groups.reduce(0) { $0 + $1.vaccines.count }
            }
            Check.equal(total, 103, "total vaccines")
        }

        Check.group("the first group is the one given at birth") {
            let iap = VaccineSchedules.byId("iap")
            Check.equal(iap.groups[0].label, "Birth", "label")
            Check.equal(iap.groups[0].dayOffset, 0, "offset")
            Check.equal(iap.groups[0].vaccines.map(\.name), ["BCG", "OPV-0", "Hep B-1"], "vaccines")
            Check.equal(iap.groups[0].vaccines[0].description, "Tuberculosis", "first description")
        }

        Check.group("the short name is what a chip shows") {
            Check.equal(VaccineSchedules.byId("iap").shortName, "IAP", "iap")
            Check.equal(VaccineSchedules.byId("cdc").shortName, "CDC", "cdc")
        }

        Check.group("an unknown schedule id falls back rather than failing") {
            Check.equal(VaccineSchedules.byId("nonsense").id, "iap", "fallback")
        }
    }
}
