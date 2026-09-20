import SwiftUI

/// The two faces Kilkari is set in.
///
/// Bricolage Grotesque for display — headings, names, the big numbers — and Plus Jakarta Sans
/// for everything else. Both are bundled from `common/fonts`, the same files the Android app
/// draws with, so the two apps are set in one typeface rather than two that look similar.
///
/// Static instances rather than variable fonts, and asked for by PostScript name: SwiftUI's
/// `Font.custom` takes a variable font's default instance and cannot move along its weight
/// axis, so a variable file would render every weight identically.
enum KFont {

    /// Display face — headings and numbers.
    static func display(_ size: CGFloat, _ weight: Font.Weight = .bold) -> Font {
        .custom(displayFace(weight), size: size)
    }

    /// UI face — everything else.
    static func sans(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        .custom(sansFace(weight), size: size)
    }

    /// Screen titles, with the design's tight tracking applied where they are used.
    static var screenTitle: Font { display(26, .heavy) }

    /// Detail-screen top bar title.
    static var barTitle: Font { display(20, .bold) }

    /// Big numbers — money totals, countdowns.
    static func number(_ size: CGFloat) -> Font { display(size, .heavy) }

    /// Bricolage ships three weights here. Anything lighter than medium rounds up rather than
    /// falling back to the system face, which would be a different typeface mid-sentence.
    private static func displayFace(_ weight: Font.Weight) -> String {
        switch weight {
        case .black, .heavy: return "BricolageGrotesque-ExtraBold"
        case .bold, .semibold: return "BricolageGrotesque-Bold"
        default: return "BricolageGrotesque-Medium"
        }
    }

    private static func sansFace(_ weight: Font.Weight) -> String {
        switch weight {
        case .black, .heavy: return "PlusJakartaSans-ExtraBold"
        case .bold: return "PlusJakartaSans-Bold"
        case .semibold: return "PlusJakartaSans-SemiBold"
        case .medium: return "PlusJakartaSans-Medium"
        default: return "PlusJakartaSans-Regular"
        }
    }

    /// Every face the app asks for, for the check that they actually loaded.
    static let bundledFaces = [
        "BricolageGrotesque-Medium", "BricolageGrotesque-Bold", "BricolageGrotesque-ExtraBold",
        "PlusJakartaSans-Regular", "PlusJakartaSans-Medium", "PlusJakartaSans-SemiBold",
        "PlusJakartaSans-Bold", "PlusJakartaSans-ExtraBold",
    ]

    /// Names the system could not find.
    ///
    /// A missing font does not fail loudly — SwiftUI quietly draws the system face instead,
    /// which is a change nobody notices in a screenshot and everybody notices on a phone. This
    /// is checked at launch in debug builds.
    static func missingFaces() -> [String] {
        bundledFaces.filter { UIFont(name: $0, size: 12) == nil }
    }
}
