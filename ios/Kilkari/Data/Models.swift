import Foundation
import SwiftData
import SwiftUI

/// Where an icon comes from: the system's set, or one of the few bundled in `common/icons`
/// because the two platforms have no glyph in common for it.
enum Glyph {
    case system(String)
    case asset(String)
}

/// One icon, from wherever it comes from, tinted and sized like any other.
struct GlyphView: View {
    let glyph: Glyph
    var size: CGFloat = 20
    var weight: Font.Weight = .semibold

    var body: some View {
        switch glyph {
        case .system(let name):
            Image(systemName: name).font(.system(size: size, weight: weight))
        case .asset(let name):
            Image(name)
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                // A Material glyph is drawn edge to edge in its box while an SF Symbol leaves
                // optical padding, so matching them by height makes the Material one look
                // bigger. The ratio brings the two back to the same visual weight.
                .frame(width: size * 1.12, height: size * 1.12)
        }
    }
}

/// The six kinds of entry the Log tab records, matching the Android app's `LogKind` keys so a
/// backup written on one platform can be read by the other.
enum LogKind: String, Codable, CaseIterable, Identifiable {
    case feed, sleep, diaper, medicine, growth, tooth

    var id: String { rawValue }

    var title: String {
        switch self {
        case .feed: return "Feed"
        case .sleep: return "Sleep"
        case .diaper: return "Diaper"
        case .medicine: return "Medicine"
        case .growth: return "Growth"
        case .tooth: return "Teeth"
        }
    }

    /// SF Symbols where they say the same thing as the Material glyph Android draws, and the
    /// Material glyph itself where they do not — see `common/icons`.
    var glyph: Glyph {
        switch self {
        case .feed: return .system("drop.fill")
        case .sleep: return .system("moon.fill")
        // SF Symbols has no changing station, and the nearest is a walking child, which reads
        // as something else entirely. This is the same file Android's icon comes from.
        case .diaper: return .asset("baby_changing_station")
        case .medicine: return .system("pills.fill")
        case .growth: return .system("scalemass.fill")
        case .tooth: return .system("mouth.fill")
        }
    }

    /// Six kinds, six hues. At tile size the colour is how a parent finds "sleep" without
    /// reading, which is the one place in the app where a full spread of colour earns its
    /// keep.
    var foreground: Color {
        switch self {
        case .feed: return KC.goldDeep
        case .sleep: return KC.lilacDeep
        case .diaper: return KC.seaDeep
        case .medicine: return KC.coralDeep
        case .growth: return KC.leafDeep
        case .tooth: return KC.clayDeep
        }
    }

    var wash: Color {
        switch self {
        case .feed: return KC.goldWash
        case .sleep: return KC.lilacWash
        case .diaper: return KC.seaWash
        case .medicine: return KC.coralWash
        case .growth: return KC.leafWash
        case .tooth: return KC.clayWash
        }
    }
}

enum FeedType: String, Codable, CaseIterable {
    case breast, bottle, solid

    var label: String {
        switch self {
        case .breast: return "Breast"
        case .bottle: return "Bottle"
        case .solid: return "Solids"
        }
    }
}

enum DiaperKind: String, Codable, CaseIterable {
    case wet, dirty, both

    var label: String {
        switch self {
        case .wet: return "Wet"
        case .dirty: return "Dirty"
        case .both: return "Both"
        }
    }
}

/// The child the app is about. One per install, as on Android.
@Model
final class Baby {
    var name: String
    var dob: Date
    var sexRaw: String?
    var photo: Data?

    init(name: String, dob: Date, sexRaw: String? = nil, photo: Data? = nil) {
        self.name = name
        self.dob = dob
        self.sexRaw = sexRaw
        self.photo = photo
    }
}

/// One thing that happened, at a time. Deliberately one wide row rather than a table per kind:
/// the Android app learned that a feed, a nap and a nappy share far more than they differ, and
/// the day's list wants them interleaved.
@Model
final class LogEntry {
    var kindRaw: String
    var startAt: Date
    var endAt: Date?
    /// Millilitres for a bottle, grams for solids, minutes for a breastfeed.
    var amount: Int?
    var side: String?
    var feedTypeRaw: String?
    var diaperKindRaw: String?
    var note: String?

    init(
        kind: LogKind,
        startAt: Date = .now,
        endAt: Date? = nil,
        amount: Int? = nil,
        side: String? = nil,
        feedType: FeedType? = nil,
        diaperKind: DiaperKind? = nil,
        note: String? = nil
    ) {
        self.kindRaw = kind.rawValue
        self.startAt = startAt
        self.endAt = endAt
        self.amount = amount
        self.side = side
        self.feedTypeRaw = feedType?.rawValue
        self.diaperKindRaw = diaperKind?.rawValue
        self.note = note
    }

    var kind: LogKind { LogKind(rawValue: kindRaw) ?? .feed }
    var feedType: FeedType? { feedTypeRaw.flatMap(FeedType.init(rawValue:)) }
    var diaperKind: DiaperKind? { diaperKindRaw.flatMap(DiaperKind.init(rawValue:)) }

    /// One line describing the entry, the way the Android app's `entryText` does.
    var summary: String {
        switch kind {
        case .feed:
            let type = feedType ?? .breast
            switch type {
            case .breast:
                let bits = [side.map { $0 == "L" ? "Left" : "Right" }, amount.map { "\($0) min" }]
                return "Breast feed · " + bits.compactMap { $0 }.joined(separator: " · ")
            case .bottle: return "Bottle feed · \(amount ?? 0) ml"
            case .solid: return "Solids · \(amount ?? 0) g"
            }
        case .sleep:
            guard let endAt else { return "Fell asleep" }
            return "Slept \(Fmt.elapsed(from: startAt, to: endAt))"
        case .diaper:
            return "Diaper · \((diaperKind ?? .wet).label.lowercased())"
        case .medicine: return note ?? "Medicine"
        case .growth: return "Measurement recorded"
        case .tooth: return "Tooth appeared"
        }
    }
}
