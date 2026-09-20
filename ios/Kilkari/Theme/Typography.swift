import SwiftUI

/// The two faces Kilkari is set in.
///
/// Android pulls Bricolage Grotesque and Plus Jakarta Sans through the Google Fonts provider.
/// Until the same files are bundled here, both fall back to the system face at the matching
/// weight and size, which keeps every layout in this app built to the right metrics — the
/// sizes, weights and tracking below are the ones from `Type.kt`, so swapping the real faces
/// in later is a change to two functions rather than to every screen.
enum KFont {

    /// Display face — headings and numbers.
    static func display(_ size: CGFloat, _ weight: Font.Weight = .bold) -> Font {
        .system(size: size, weight: weight, design: .rounded)
    }

    /// UI face — everything else.
    static func sans(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        .system(size: size, weight: weight)
    }

    /// Screen titles: 26pt extra-bold display with the design's tight tracking.
    static var screenTitle: Font { display(26, .heavy) }

    /// Detail-screen top bar title.
    static var barTitle: Font { display(20, .bold) }

    /// Big numbers — money totals, countdowns.
    static func number(_ size: CGFloat) -> Font { display(size, .heavy) }
}
