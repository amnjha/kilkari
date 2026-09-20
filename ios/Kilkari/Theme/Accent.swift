import SwiftUI

/// One screen's colour, as a set rather than a single hue.
///
/// The same idea as the Android app's `Accent`: the shared controls read their colour from
/// the environment, so a screen changes hue as a whole and moving between them feels like
/// moving somewhere, rather than every button in the product being the one brand coral.
struct Accent: Equatable {
    /// Fills that carry white text: the primary button, the selected chip, the active tab.
    let main: Color
    /// Coloured text and icons, on white or on one of the pale grounds.
    let deep: Color
    /// Decoration only — never behind text.
    let light: Color
    /// The palest ground, for an icon badge behind a small glyph.
    let bg: Color
    /// The ground a whole tile or header is painted with, and unmistakably coloured.
    let wash: Color
    /// Borders, rings and tracks.
    let ring: Color

    /// Deep to main: the gradient for a hero card in this hue.
    var gradient: [Color] { [deep, main] }
}

/// The hue each part of the app answers to, grouped by what a parent is doing rather than by
/// screen, so two screens about the same thing agree.
enum KAccents {
    /// The brand, the home screen, and anything clinical or urgent.
    static let brand = Accent(main: KC.coral, deep: KC.coralDeep, light: KC.coralLight,
                              bg: KC.coralBg, wash: KC.coralWash, ring: KC.coralRing)

    /// Logging the day: feeds, sleep, nappies. The calm half.
    static let day = Accent(main: KC.sky, deep: KC.skyDeep, light: KC.skyLight,
                            bg: KC.skyBg, wash: KC.skyWash, ring: KC.skyRing)

    /// Health: vaccines, appointments, doctors.
    static let health = Accent(main: KC.teal, deep: KC.tealDeep, light: KC.tealLight,
                               bg: KC.tealBg, wash: KC.tealWash, ring: KC.tealRing)

    /// Anything that grows or completes.
    static let growth = Accent(main: KC.leaf, deep: KC.leafDeep, light: KC.leafLight,
                               bg: KC.leafBg, wash: KC.leafWash, ring: KC.leafRing)

    /// Medicines and doses. Warm, but not the brand's warm.
    static let care = Accent(main: KC.clayDeep, deep: KC.clayDeep, light: KC.clayLight,
                             bg: KC.clayBg, wash: KC.clayWash, ring: KC.clayBg2)

    /// The fund. Gold's mid step is too light for white text, so it starts at the deep one.
    static let money = Accent(main: KC.goldDeep, deep: KC.goldDeep, light: KC.goldLight,
                              bg: KC.goldBg, wash: KC.goldWash, ring: KC.goldRing)

    /// Keepsakes: the timeline, albums, birthdays.
    static let memories = Accent(main: KC.rose, deep: KC.roseDeep, light: KC.roseLight,
                                 bg: KC.roseBg, wash: KC.roseWash, ring: KC.roseRing)

    /// Paperwork and records.
    static let records = Accent(main: KC.sea, deep: KC.seaDeep, light: KC.seaLight,
                                bg: KC.seaBg, wash: KC.seaWash, ring: KC.seaRing)

    /// Reminders, insights, settings: the app talking about itself.
    static let quiet = Accent(main: KC.lilac, deep: KC.lilacDeep, light: KC.lilacLight,
                              bg: KC.lilacBg, wash: KC.lilacWash, ring: KC.lilacRing)
}

private struct AccentKey: EnvironmentKey {
    static let defaultValue = KAccents.brand
}

extension EnvironmentValues {
    var accent: Accent {
        get { self[AccentKey.self] }
        set { self[AccentKey.self] = newValue }
    }
}

extension Color {
    /// This colour mixed part of the way out of the cream ground, as an opaque colour.
    ///
    /// Softening a tint with opacity looks right until the surface wearing it is raised: the
    /// shadow behind it bleeds up through the middle and a flat tint reads as a dirty
    /// gradient. Mixing the two and handing over the result keeps the surface opaque.
    func onCream(_ amount: Double) -> Color {
        Color(uiColor: UIColor(KC.screen).blended(with: UIColor(self), amount: amount))
    }
}

private extension UIColor {
    func blended(with other: UIColor, amount: CGFloat) -> UIColor {
        var r1: CGFloat = 0, g1: CGFloat = 0, b1: CGFloat = 0, a1: CGFloat = 0
        var r2: CGFloat = 0, g2: CGFloat = 0, b2: CGFloat = 0, a2: CGFloat = 0
        getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
        other.getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
        return UIColor(
            red: r1 + (r2 - r1) * amount,
            green: g1 + (g2 - g1) * amount,
            blue: b1 + (b2 - b1) * amount,
            alpha: 1
        )
    }
}
