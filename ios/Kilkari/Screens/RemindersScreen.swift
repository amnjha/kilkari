import SwiftUI
import SwiftData

/// Reminders the parent set, and whether the system will actually deliver them.
struct RemindersScreen: View {
    @Environment(\.modelContext) private var context
    @Environment(\.accent) private var accent
    @Query(sort: \Reminder.minuteOfDay) private var reminders: [Reminder]

    @State private var adding = false
    @State private var permitted = true

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if !permitted {
                    // Worth its own card: a reminder that silently never arrives is
                    // indistinguishable from an app that does not send them.
                    KCard(background: KC.dangerBg, border: nil) {
                        VStack(alignment: .leading, spacing: 8) {
                            Label("Notifications are off", systemImage: "bell.slash.fill")
                                .font(KFont.sans(14, .bold)).foregroundStyle(KC.dangerDeep)
                            Text("Nothing below will arrive until they are allowed for Kilkari.")
                                .font(KFont.sans(13)).foregroundStyle(KC.mutedStrong)
                            Button("Open Settings") {
                                if let url = URL(string: UIApplication.openSettingsURLString) {
                                    UIApplication.shared.open(url)
                                }
                            }
                            .font(KFont.sans(14, .bold))
                            .tint(KC.dangerDeep)
                        }
                        .padding(16)
                    }
                }

                if reminders.isEmpty {
                    KCard {
                        VStack(spacing: 10) {
                            Image(systemName: "bell.fill")
                                .font(.system(size: 28)).foregroundStyle(accent.deep)
                                .frame(width: 92, height: 92)
                                .background(accent.wash.onCream(0.6))
                                .clipShape(RoundedRectangle(cornerRadius: 38, style: .continuous))
                            Text("No reminders of your own")
                                .font(KFont.display(17, .bold)).foregroundStyle(KC.ink)
                            Text("Vitamin drops at eight, tummy time after the nap.")
                                .font(KFont.sans(13)).foregroundStyle(KC.muted)
                                .multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 26).padding(.horizontal, 20)
                    }
                } else {
                    KCard {
                        VStack(spacing: 0) {
                            ForEach(Array(reminders.enumerated()), id: \.element.persistentModelID) { i, reminder in
                                HStack(spacing: 12) {
                                    IconBadge(symbol: "bell.fill", tint: accent.deep,
                                              background: accent.bg, size: 36, corner: 10, iconSize: 16)
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(reminder.title)
                                            .font(KFont.sans(14, .semibold))
                                            .foregroundStyle(reminder.enabled ? KC.ink : KC.faint)
                                        Text(reminder.describe)
                                            .font(KFont.sans(12)).foregroundStyle(KC.muted)
                                    }
                                    Spacer()
                                    Toggle("", isOn: Binding(
                                        get: { reminder.enabled },
                                        set: { reminder.enabled = $0; sync() }
                                    ))
                                    .labelsHidden()
                                    .tint(accent.main)
                                }
                                .padding(.horizontal, 14).padding(.vertical, 10)
                                .swipeActions {
                                    Button("Delete", role: .destructive) {
                                        context.delete(reminder); sync()
                                    }
                                }
                                if i != reminders.count - 1 { Rectangle().fill(KC.divider).frame(height: 1) }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 110)
        }
        .safeAreaInset(edge: .bottom) {
            PrimaryButton(label: "Add a reminder") { adding = true }
                .padding(.horizontal, 16).padding(.bottom, 96)
        }
        .sheet(isPresented: $adding) {
            AddReminderSheet { reminder in
                context.insert(reminder)
                adding = false
                Task {
                    permitted = await Notifications.requestPermission()
                    sync()
                }
            }
            .presentationDetents([.medium])
            .environment(\.accent, accent)
        }
        .task { permitted = await Notifications.authorized() || reminders.isEmpty }
    }

    private func sync() {
        let snapshot = reminders
        Task { await Notifications.reschedule(snapshot) }
    }
}

struct AddReminderSheet: View {
    let onSave: (Reminder) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent

    @State private var title = ""
    @State private var at = Calendar.current.date(from: DateComponents(hour: 8, minute: 0)) ?? .now
    @State private var cadence: Cadence = .daily
    @State private var weekday = Calendar.current.component(.weekday, from: .now)

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    sheetField("What to remind you", $title, "e.g. Vitamin D drops")

                    DatePicker("Time", selection: $at, displayedComponents: .hourAndMinute)
                        .font(KFont.sans(14, .semibold)).tint(accent.main)

                    Picker("", selection: $cadence) {
                        ForEach(Cadence.allCases) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)

                    if cadence == .weekly {
                        Picker("Day", selection: $weekday) {
                            ForEach(1...7, id: \.self) { day in
                                Text(Calendar.current.weekdaySymbols[day - 1]).tag(day)
                            }
                        }
                        .tint(accent.deep)
                    }

                    PrimaryButton(label: "Save reminder",
                                  enabled: !title.trimmingCharacters(in: .whitespaces).isEmpty) {
                        let parts = Calendar.current.dateComponents([.hour, .minute], from: at)
                        onSave(Reminder(
                            title: title.trimmingCharacters(in: .whitespaces),
                            minuteOfDay: (parts.hour ?? 8) * 60 + (parts.minute ?? 0),
                            cadence: cadence,
                            weekday: weekday
                        ))
                        dismiss()
                    }
                    .padding(.top, 4)
                }
                .padding(20)
            }
            .background(KC.screen)
            .navigationTitle("Add a reminder")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.tint(accent.deep)
                }
            }
        }
    }
}
