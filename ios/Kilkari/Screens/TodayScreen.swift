import SwiftUI
import SwiftData
import KilkariCore

/// Home. Who this is, what is next, and the three things most often logged.
struct TodayScreen: View {
    let baby: Baby

    @Environment(\.modelContext) private var context
    @Environment(\.accent) private var accent
    @Query(sort: \LogEntry.startAt, order: .reverse) private var entries: [LogEntry]
    @Query private var doses: [VaccineDose]

    @State private var logging: LogKind?

    private var openSleep: LogEntry? {
        entries.first { $0.kind == .sleep && $0.endAt == nil }
    }

    private func latest(_ kind: LogKind) -> LogEntry? {
        entries.first { $0.kind == kind }
    }

    /// The oldest group still outstanding — the same answer the Vaccinations screen gives,
    /// on the schedule actually chosen and with given doses taken into account.
    private var nextGroup: VaccineGroupState? {
        VaccinePlan.next(VaccinePlan.groups(for: baby, doses: doses))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                header
                nextUp
                SectionLabel("Quick actions").padding(.top, 2)
                quickActions
                SectionLabel("Today").padding(.top, 2)
                todaysEntries
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 110)
        }
        .sheet(item: $logging) { kind in
            QuickLogSheet(kind: kind) { entry in
                context.insert(entry)
                logging = nil
            }
            .presentationDetents([.medium])
        }
    }

    // Who this is, before what is due: the name, and how old they are today. No greeting —
    // it would be addressed to someone who cannot read it.
    private var header: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 1) {
                Text(baby.name)
                    .font(KFont.display(25, .heavy))
                    .tracking(-0.5)
                    .foregroundStyle(KC.ink)
                Text("\(Fmt.age(from: baby.dob)) old today")
                    .font(KFont.sans(13, .medium))
                    .foregroundStyle(KC.mutedStrong)
            }
            Spacer()
            NavigationLink(value: Route.reminders) {
                Circle().fill(KC.surface).frame(width: 42, height: 42)
                    .overlay {
                        Image(systemName: "bell.fill")
                            .font(.system(size: 19, weight: .semibold))
                            .foregroundStyle(accent.deep)
                    }
                    .frame(width: 48, height: 48)
            }
            .buttonStyle(SpringPress())
            .accessibilityLabel("Reminders")
        }
        .padding(.top, 2)
    }

    @ViewBuilder
    private var nextUp: some View {
        if let nap = openSleep {
            HeroCard(
                label: "NAPPING NOW",
                badge: Fmt.elapsed(from: nap.startAt),
                title: "Asleep since \(Fmt.time(nap.startAt))",
                subtitle: "Tap to wake and record it",
                action: "End the nap",
                colors: [KC.coral, KC.clay],
                photo: baby.photo,
                name: baby.name
            ) {
                nap.endAt = .now
            }
        } else if let group = nextGroup {
            let outstanding = group.def.vaccines.filter { !group.given.contains($0.name) }
            NavigationLink(value: Route.vaccines) {
                HeroCard(
                    label: "NEXT UP",
                    badge: group.dueText,
                    title: "\(group.def.label) vaccines · \(outstanding.count) \(Fmt.plural(outstanding.count, "dose"))",
                    subtitle: "\(Fmt.date(group.dueDate)) · \(outstanding.map(\.name).joined(separator: ", "))",
                    action: "See schedule",
                    colors: group.status == .overdue ? [KC.danger, KC.coralDeep] : [KC.coral, KC.clay],
                    photo: baby.photo,
                    name: baby.name,
                    actionIsLink: true
                ) {}
            }
            .buttonStyle(SpringPress())
        } else {
            // Green, not the brand's coral. Coral is what the app uses for anything asking
            // something of a parent, and this card's whole point is that nothing is.
            HeroCard(
                label: "NOTHING PLANNED",
                badge: "All clear",
                title: "Nothing due today",
                subtitle: "No doses, no visits, nothing overdue.",
                action: "See schedule",
                colors: [KC.leafDeep, KC.leaf],
                photo: baby.photo,
                name: baby.name,
                actionIsLink: true
            ) {}
        }
    }

    private func dueText(_ date: Date) -> String {
        let days = Calendar.current.dateComponents(
            [.day], from: Calendar.current.startOfDay(for: .now),
            to: Calendar.current.startOfDay(for: date)
        ).day ?? 0
        if days < 0 { return "\(-days) days overdue" }
        if days == 0 { return "today" }
        return "in \(days) \(Fmt.plural(days, "day"))"
    }

    private var quickActions: some View {
        HStack(spacing: 8) {
            ForEach([LogKind.feed, .sleep, .diaper]) { kind in
                Button {
                    logging = kind
                } label: {
                    VStack(spacing: 8) {
                        ZStack(alignment: .topTrailing) {
                            Circle()
                                .fill(.white.opacity(0.85))
                                .frame(width: 46, height: 46)
                                .overlay {
                                    GlyphView(glyph: kind.glyph, size: 20)
                                        .foregroundStyle(kind.foreground)
                                }
                            // Says the card does something, without a button competing with it.
                            Circle().fill(.white).frame(width: 19, height: 19)
                                .overlay { Circle().fill(kind.foreground).frame(width: 16, height: 16) }
                                .overlay {
                                    Image(systemName: "plus")
                                        .font(.system(size: 9, weight: .black))
                                        .foregroundStyle(.white)
                                }
                                .offset(x: 6, y: -4)
                        }
                        Text(kind.title)
                            .font(KFont.sans(13, .bold))
                            .foregroundStyle(KC.ink)
                        Text(kind == .sleep && openSleep != nil
                             ? "asleep \(Fmt.elapsed(from: openSleep!.startAt))"
                             : Fmt.ago(latest(kind)?.startAt))
                            .font(KFont.sans(11))
                            .foregroundStyle(KC.mutedStrong)
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 14)
                    .background(kind.wash)
                    .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                    .clay(corner: 22)
                }
                .buttonStyle(SpringPress())
            }
        }
    }

    private var todaysEntries: some View {
        let today = Calendar.current.startOfDay(for: .now)
        let mine = entries.filter { $0.startAt >= today }
        return KCard {
            if mine.isEmpty {
                VStack(spacing: 10) {
                    Image(systemName: "square.and.pencil")
                        .font(.system(size: 30))
                        .foregroundStyle(accent.deep)
                        .frame(width: 84, height: 84)
                        .background(accent.wash.onCream(0.6))
                        .clipShape(RoundedRectangle(cornerRadius: 34, style: .continuous))
                    Text("Nothing logged today")
                        .font(KFont.display(16, .bold))
                        .foregroundStyle(KC.ink)
                    Text("Tap a tile above and it shows up here.")
                        .font(KFont.sans(13))
                        .foregroundStyle(KC.muted)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 22)
            } else {
                VStack(spacing: 0) {
                    ForEach(mine) { entry in
                        LogRow(entry: entry)
                        if entry.id != mine.last?.id {
                            Rectangle().fill(KC.divider).frame(height: 1)
                        }
                    }
                }
            }
        }
    }
}

/// The card at the top that says what is happening, or what is next, or that nothing is.
struct HeroCard: View {
    let label: String
    let badge: String
    let title: String
    let subtitle: String
    let action: String
    let colors: [Color]
    let photo: Data?
    let name: String
    /// True when the card itself is wrapped in a NavigationLink: a button inside a link
    /// swallows the tap and the card stops working, so the action becomes a label.
    var actionIsLink: Bool = false
    let onAction: () -> Void

    var body: some View {
        GradientCard(colors: colors) {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text(label)
                        .font(KFont.sans(12, .semibold))
                        .tracking(0.5)
                        .foregroundStyle(.white.opacity(0.85))
                    Spacer()
                    Text(badge)
                        .font(KFont.sans(12))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 9).padding(.vertical, 3)
                        .background(.white.opacity(0.18))
                        .clipShape(Capsule())
                }
                HStack(spacing: 14) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(title)
                            .font(KFont.display(20, .bold))
                            .foregroundStyle(.white)
                            .fixedSize(horizontal: false, vertical: true)
                        Text(subtitle)
                            .font(KFont.sans(13))
                            .foregroundStyle(.white.opacity(0.9))
                            .fixedSize(horizontal: false, vertical: true)
                        Group {
                            if actionIsLink {
                                actionLabel
                            } else {
                                Button(action: onAction) { actionLabel }
                                    .buttonStyle(SpringPress())
                            }
                        }
                    }
                    Spacer(minLength: 0)
                    ChildAvatar(photo: photo, name: name, size: 78, ring: .white.opacity(0.55))
                }
            }
        }
    }
}

extension HeroCard {
    var actionLabel: some View {
        Text(action)
            .font(KFont.sans(13, .bold))
            .foregroundStyle(colors[0])
            .padding(.horizontal, 14).padding(.vertical, 8)
            .background(.white)
            .clipShape(Capsule())
    }
}

/// One line in the day's list.
struct LogRow: View {
    let entry: LogEntry

    var body: some View {
        HStack(spacing: 12) {
            GlyphView(glyph: entry.kind.glyph, size: 17)
                .foregroundStyle(entry.kind.foreground)
                .frame(width: 22)
            Text(entry.summary)
                .font(KFont.sans(14))
                .foregroundStyle(KC.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(Fmt.time(entry.startAt))
                .font(KFont.sans(12))
                .foregroundStyle(KC.muted)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
    }
}
