import SwiftUI

/// The white, generously rounded card that carries almost every list in the app.
struct KCard<Content: View>: View {
    var corner: CGFloat = KDepth.card
    var background: Color = KC.surface
    var border: Color? = KC.border
    /// Lifts the card off the cream. Off for cards drawn inside another raised surface.
    var raised: Bool = true
    @ViewBuilder var content: Content

    var body: some View {
        content
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(background)
            .clipShape(RoundedRectangle(cornerRadius: corner, style: .continuous))
            .overlay {
                if let border {
                    RoundedRectangle(cornerRadius: corner, style: .continuous)
                        .strokeBorder(border, lineWidth: 1)
                }
            }
            .modifier(RaiseIf(raised: raised, elevation: KDepth.resting))
    }
}

private struct RaiseIf: ViewModifier {
    let raised: Bool
    let elevation: CGFloat

    func body(content: Content) -> some View {
        if raised { content.clay(elevation: elevation) } else { content }
    }
}

/// Card whose ground is a gradient — the next-up and vaccination heroes.
struct GradientCard<Content: View>: View {
    let colors: [Color]
    var corner: CGFloat = KDepth.hero
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing)
            )
            .clipShape(RoundedRectangle(cornerRadius: corner, style: .continuous))
            // Tinted with its own colour so the glow under it belongs to the card rather than
            // sitting behind it.
            .clay(corner: corner, elevation: KDepth.heroLift, tint: colors[0])
    }
}

/// Section heading: the rhythm marker between card groups.
struct SectionLabel: View {
    let text: String

    init(_ text: String) { self.text = text }

    var body: some View {
        Text(text)
            .font(KFont.display(17, .bold))
            .tracking(-0.3)
            .foregroundStyle(KC.ink)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Full-width pill — the primary action at the bottom of every sheet.
struct PrimaryButton: View {
    let label: String
    var enabled: Bool = true
    let action: () -> Void

    @Environment(\.accent) private var accent

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(KFont.sans(15, .bold))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(enabled ? accent.main : accent.ring)
                .clipShape(Capsule())
                .clay(corner: 999, elevation: enabled ? 8 : 0, tint: accent.main)
        }
        .buttonStyle(SpringPress())
        .disabled(!enabled)
    }
}

/// Pill used for filters and choices.
struct KChip: View {
    let label: String
    let selected: Bool
    let action: () -> Void

    @Environment(\.accent) private var accent

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(KFont.sans(13, .semibold))
                .foregroundStyle(selected ? .white : accent.deep)
                .padding(.horizontal, 14)
                .frame(height: 36)
                .background(selected ? accent.main : KC.surface)
                .clipShape(Capsule())
                .overlay(Capsule().strokeBorder(selected ? accent.main : accent.ring, lineWidth: 1))
        }
        .buttonStyle(SpringPress())
    }
}

/// Rounded square icon chip used as the leading element of most rows.
struct IconBadge: View {
    let symbol: String
    let tint: Color
    let background: Color
    var size: CGFloat = 40
    var corner: CGFloat = 12
    var iconSize: CGFloat = 20

    var body: some View {
        RoundedRectangle(cornerRadius: corner, style: .continuous)
            .fill(background)
            .frame(width: size, height: size)
            .overlay {
                Image(systemName: symbol)
                    .font(.system(size: iconSize, weight: .semibold))
                    .foregroundStyle(tint)
            }
    }
}

/// The child's picture, round, falling back to their initial until one is taken.
struct ChildAvatar: View {
    let photo: Data?
    let name: String
    var size: CGFloat = 52
    var ring: Color?

    var body: some View {
        Group {
            if let photo, let image = UIImage(data: photo) {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                LinearGradient(colors: [KC.coralLight, KC.goldLight],
                               startPoint: .topLeading, endPoint: .bottomTrailing)
                    .overlay {
                        Text(String(name.prefix(1)).uppercased())
                            .font(KFont.sans(size / 2.4, .heavy))
                            .foregroundStyle(.white)
                    }
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .overlay { if let ring { Circle().strokeBorder(ring, lineWidth: 2) } }
    }
}

/// A circular icon button on a tinted ground — the header actions. Sized to the 44pt touch
/// minimum even though the circle itself is smaller.
struct RoundIconButton: View {
    let symbol: String
    let tint: Color
    let background: Color
    let accessibilityLabel: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Circle()
                .fill(background)
                .frame(width: 42, height: 42)
                .overlay {
                    Image(systemName: symbol)
                        .font(.system(size: 19, weight: .semibold))
                        .foregroundStyle(tint)
                }
                .frame(width: 48, height: 48)
        }
        .buttonStyle(SpringPress())
        .accessibilityLabel(accessibilityLabel)
    }
}
