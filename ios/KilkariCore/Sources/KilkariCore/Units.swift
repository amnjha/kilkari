import Foundation

/// Weight and length in whichever units the parent reads them in.
///
/// Everything is stored metric — kilograms, centimetres, grams — because that is what the WHO
/// tables speak and what the growth chart compares against. Conversion happens at the edges:
/// on the way onto a screen, and on the way back out of an input. Nothing in the database
/// changes when the setting is switched.
///
/// Pounds are shown with ounces rather than as a decimal, because that is how a scale reads
/// and how a paediatrician says it: 3.4 kg is "7 lb 8 oz", not "7.5 lb".
public enum Units {

    public static let lbPerKg = 2.20462262185
    public static let cmPerIn = 2.54
    public static let gPerOz = 28.349523125
    private static let ozPerLb = 16

    public static func kgToLb(_ kg: Double) -> Double { kg * lbPerKg }
    public static func lbToKg(_ lb: Double) -> Double { lb / lbPerKg }
    public static func cmToIn(_ cm: Double) -> Double { cm / cmPerIn }
    public static func inToCm(_ inches: Double) -> Double { inches * cmPerIn }

    /// Whole pounds and the ounces left over, rounded to the nearest ounce.
    ///
    /// Rounding happens on the total, so 0.9999 lb reads "1 lb 0 oz" rather than
    /// "0 lb 16 oz".
    public static func lbOz(_ kg: Double) -> (lb: Int, oz: Int) {
        let totalOz = Int((kgToLb(kg) * Double(ozPerLb)).rounded())
        return (totalOz / ozPerLb, totalOz % ozPerLb)
    }

    public static func fromLbOz(lb: Double, oz: Double) -> Double {
        lbToKg(lb + oz / Double(ozPerLb))
    }

    /// "7 lb 8 oz" — ounces are kept even at zero, so the reading is unambiguous.
    public static func weightLabel(_ kg: Double) -> String {
        let (pounds, ounces) = lbOz(kg)
        return "\(pounds) lb \(ounces) oz"
    }

    /// "21.5 in"
    public static func lengthLabel(_ cm: Double) -> String { trim(cmToIn(cm)) + " in" }

    /// A change in weight: grams while metric, ounces otherwise. Gains between weigh-ins are
    /// small enough that pounds would round most of them away.
    public static func deltaLabel(kgDelta: Double, metric: Bool) -> String {
        let sign = kgDelta >= 0 ? "+" : "−"
        if metric {
            return sign + String(Int((abs(kgDelta * 1000)).rounded())) + " g"
        }
        return sign + trim(abs(kgDelta * 1000) / gPerOz) + " oz"
    }

    /// A change in length: "+1.5 cm" or "+0.6 in".
    public static func lengthDelta(cmDelta: Double, metric: Bool) -> String {
        let sign = cmDelta >= 0 ? "+" : "−"
        let value = abs(metric ? cmDelta : cmToIn(cmDelta))
        return sign + trim(value) + (metric ? " cm" : " in")
    }

    /// What an input asks for, so its label and its parsing cannot disagree.
    public static func lengthField(cm: Double?, metric: Bool) -> String {
        guard let cm else { return "" }
        return trim(metric ? cm : cmToIn(cm))
    }

    public static func lengthFromField(_ value: Double, metric: Bool) -> Double {
        metric ? value : inToCm(value)
    }

    private static func trim(_ v: Double) -> String {
        let rounded = v.rounded()
        if v == rounded { return String(Int(rounded)) }
        return String(format: "%.1f", locale: Locale(identifier: "en_US_POSIX"), v)
    }
}
