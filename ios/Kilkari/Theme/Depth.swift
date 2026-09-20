import SwiftUI

/// The soft, slightly inflated feel the app is drawn with.
///
/// Everything a parent taps is a rounded, raised thing rather than a rectangle with a
/// hairline: shadows are tinted with the surface's own hue instead of black, so a card looks
/// lit rather than outlined, and a press squashes it a little the way a real button would.
/// The point is warmth — this is used one-handed at 4am — not decoration for its own sake.
enum KDepth {
    /// Card, tile and sheet corner radii. Generous, and consistent everywhere.
    static let card: CGFloat = 24
    static let tile: CGFloat = 26
    static let hero: CGFloat = 30

    /// How far each surface lifts off the cream.
    static let resting: CGFloat = 10
    static let heroLift: CGFloat = 18
}

extension View {

    /// A soft raised surface: the shadow is tinted rather than grey, so it reads as warmth
    /// under the card instead of a drop shadow behind it.
    func clay(
        corner: CGFloat = KDepth.card,
        elevation: CGFloat = KDepth.resting,
        tint: Color = KC.clayShadow
    ) -> some View {
        // Two shadows: a tight one for the contact edge and a wider, softer one for the lift.
        // A single blur at this radius reads as fog rather than as a raised object.
        shadow(color: tint.opacity(0.18), radius: elevation * 0.55, x: 0, y: elevation * 0.30)
            .shadow(color: tint.opacity(0.10), radius: elevation * 1.4, x: 0, y: elevation * 0.7)
    }

    /// The band of colour a screen opens with, painted behind the content rather than added
    /// to it, so the hue reaches the top of the display and has faded into the cream by the
    /// time the first card arrives.
    func headerWash(_ accent: Accent, height: CGFloat = 260) -> some View {
        background(alignment: .top) {
            LinearGradient(
                colors: [accent.wash.opacity(0.32), KC.screen.opacity(0)],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(height: height)
            .ignoresSafeArea(edges: .top)
        }
    }
}

/// The squish. Scales to 96% while held, on a spring rather than a curve, so a tap feels like
/// pressing something soft. Only the drawing scales, so nothing around it moves.
struct SpringPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.spring(response: 0.34, dampingFraction: 0.55), value: configuration.isPressed)
    }
}
