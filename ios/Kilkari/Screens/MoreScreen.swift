import SwiftUI
import SwiftData
import KilkariCore

/// The drawer: the child's details, and everything that does not live in a tab.
///
/// A list, not a grid. The Android app tried tiles here and they were prettier and worse —
/// eight of them pushed the last two below the fold, and a drawer of occasional things is
/// read down the titles rather than aimed at. The grouping survives in the badges: rose for
/// the keepsakes, sea for the records, lilac for the app's own settings.
struct MoreScreen: View {
    let baby: Baby

    @Environment(\.accent) private var accent
    @State private var prefs = Preferences.shared
    @Query private var milestones: [Milestone]

    private var items: [(route: Route, symbol: String, title: String, subtitle: String, tint: Color, bg: Color)] {
        [
            (.timeline, "chart.line.uptrend.xyaxis", "Timeline",
             milestones.isEmpty ? "Milestones and moments" : "\(milestones.count) \(Fmt.plural(milestones.count, "moment"))",
             KC.roseDeep, KC.roseWash),
            (.doctors, "cross.case.fill", "Doctors", "The people you see", KC.seaDeep, KC.seaWash),
            (.reminders, "bell.fill", "Reminders", "Times you want telling", KC.lilacDeep, KC.lilacWash),
            (.settings, "gearshape.fill", "Settings",
             "\(prefs.currency.symbol) \(prefs.currency.code) · \(VaccineSchedules.byId(prefs.scheduleId).shortName) schedule",
             KC.lilacDeep, KC.lilacWash),
        ]
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("More").font(KFont.screenTitle).foregroundStyle(KC.ink)

                HStack(spacing: 12) {
                    ChildAvatar(photo: baby.photo, name: baby.name, size: 48)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(baby.name).font(KFont.sans(16, .bold)).foregroundStyle(KC.ink)
                        Text("Born \(Fmt.date(baby.dob)) · \(Fmt.age(from: baby.dob))")
                            .font(KFont.sans(12)).foregroundStyle(KC.mutedStrong)
                    }
                    Spacer()
                }
                .padding(14)
                .background(
                    LinearGradient(colors: [KC.coralWash, KC.goldWash],
                                   startPoint: .leading, endPoint: .trailing)
                )
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))

                KCard {
                    VStack(spacing: 0) {
                        ForEach(Array(items.enumerated()), id: \.offset) { i, item in
                            NavigationLink(value: item.route) {
                                HStack(spacing: 12) {
                                    IconBadge(symbol: item.symbol, tint: item.tint,
                                              background: item.bg, size: 36, corner: 10, iconSize: 17)
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(item.title)
                                            .font(KFont.sans(14, .semibold)).foregroundStyle(KC.ink)
                                        Text(item.subtitle)
                                            .font(KFont.sans(12)).foregroundStyle(KC.muted)
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundStyle(KC.stoneLight)
                                }
                                .padding(.horizontal, 14).padding(.vertical, 13)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            if i != items.count - 1 { Rectangle().fill(KC.divider).frame(height: 1) }
                        }
                    }
                }

                KCard(background: KC.surfaceWarm, border: nil) {
                    VStack(alignment: .leading, spacing: 6) {
                        Label("Everything stays on this phone", systemImage: "lock.fill")
                            .font(KFont.sans(14, .semibold)).foregroundStyle(KC.ink)
                        Text("No account, no server, no analytics. Backup and export are on the Android app and coming here.")
                            .font(KFont.sans(12)).foregroundStyle(KC.mutedStrong)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(16)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 18)
            .padding(.bottom, 110)
        }
    }
}

/// Moments worth keeping, newest first.
struct TimelineScreen: View {
    @Environment(\.modelContext) private var context
    @Environment(\.accent) private var accent
    @Query(sort: \Milestone.date, order: .reverse) private var milestones: [Milestone]
    @State private var adding = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if milestones.isEmpty {
                    KCard {
                        VStack(spacing: 10) {
                            Image(systemName: "sparkles")
                                .font(.system(size: 28)).foregroundStyle(accent.deep)
                                .frame(width: 92, height: 92)
                                .background(accent.wash.onCream(0.6))
                                .clipShape(RoundedRectangle(cornerRadius: 38, style: .continuous))
                            Text("The story starts here")
                                .font(KFont.display(17, .bold)).foregroundStyle(KC.ink)
                            Text("First smile, first outing, the day they rolled over.")
                                .font(KFont.sans(13)).foregroundStyle(KC.muted)
                                .multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 26).padding(.horizontal, 20)
                    }
                } else {
                    ForEach(milestones) { milestone in
                        KCard {
                            HStack(spacing: 12) {
                                IconBadge(symbol: "sparkles", tint: KC.roseDeep,
                                          background: KC.roseBg, size: 36, corner: 10, iconSize: 16)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(milestone.title)
                                        .font(KFont.sans(15, .semibold)).foregroundStyle(KC.ink)
                                    if let note = milestone.note, !note.isEmpty {
                                        Text(note).font(KFont.sans(12)).foregroundStyle(KC.muted)
                                    }
                                }
                                Spacer()
                                Text(Fmt.date(milestone.date))
                                    .font(KFont.sans(12)).foregroundStyle(KC.muted)
                            }
                            .padding(14)
                        }
                    }
                }
            }
            .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 110)
        }
        .safeAreaInset(edge: .bottom) {
            PrimaryButton(label: "Add a moment") { adding = true }
                .padding(.horizontal, 16).padding(.bottom, 96)
        }
        .sheet(isPresented: $adding) {
            AddMilestoneSheet { context.insert($0); adding = false }
                .presentationDetents([.medium])
                .environment(\.accent, accent)
        }
    }
}

struct AddMilestoneSheet: View {
    let onSave: (Milestone) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent

    private let suggestions = ["First smile", "First laugh", "Rolled over", "First outing", "Festival"]

    @State private var title = ""
    @State private var note = ""
    @State private var date = Date.now

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    // The five most common answers as chips, because typing "First smile" at
                    // 4am with one hand is not the experience.
                    LazyVGrid(columns: [.init(.adaptive(minimum: 100), spacing: 6)], spacing: 6) {
                        ForEach(suggestions, id: \.self) { s in
                            KChip(label: s, selected: title == s) { title = s }
                        }
                    }
                    sheetField("What happened", $title, "or type your own")
                    sheetField("Note", $note, "A line to remember it by")
                    DatePicker("When", selection: $date, in: ...Date.now, displayedComponents: .date)
                        .font(KFont.sans(14, .semibold)).tint(accent.main)
                    PrimaryButton(label: "Add to the timeline",
                                  enabled: !title.trimmingCharacters(in: .whitespaces).isEmpty) {
                        onSave(Milestone(title: title.trimmingCharacters(in: .whitespaces),
                                         note: note.trimmingCharacters(in: .whitespaces),
                                         date: date))
                        dismiss()
                    }
                    .padding(.top, 4)
                }
                .padding(20)
            }
            .background(KC.screen)
            .navigationTitle("Add a moment")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.tint(accent.deep)
                }
            }
        }
    }
}
