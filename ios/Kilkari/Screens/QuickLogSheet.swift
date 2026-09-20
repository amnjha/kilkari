import SwiftUI

/// What a tile opens: the smallest form that can record one entry.
///
/// Deliberately short. The Android app learned that the sheet a parent opens at 4am should be
/// answerable in one tap, with everything else optional and below the fold.
struct QuickLogSheet: View {
    let kind: LogKind
    let onSave: (LogEntry) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent

    @State private var feedType: FeedType = .breast
    @State private var side = "L"
    @State private var amount = ""
    @State private var diaper: DiaperKind = .wet
    @State private var note = ""
    @State private var at = Date.now

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    switch kind {
                    case .feed: feedFields
                    case .diaper: diaperFields
                    case .medicine, .growth, .tooth: noteField
                    case .sleep: sleepFields
                    }

                    DatePicker("When", selection: $at, displayedComponents: [.hourAndMinute])
                        .font(KFont.sans(14, .semibold))
                        .tint(accent.main)

                    PrimaryButton(label: "Save \(kind.title.lowercased())", enabled: canSave) {
                        onSave(build())
                        dismiss()
                    }
                    .padding(.top, 4)
                }
                .padding(20)
            }
            .background(KC.surface)
            .navigationTitle(kind.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.tint(accent.deep)
                }
            }
        }
        .environment(\.accent, accent)
    }

    private var canSave: Bool {
        switch kind {
        case .feed: return feedType == .breast || Int(amount) != nil
        case .medicine, .growth, .tooth: return !note.trimmingCharacters(in: .whitespaces).isEmpty
        default: return true
        }
    }

    @ViewBuilder private var feedFields: some View {
        Picker("Type", selection: $feedType) {
            ForEach(FeedType.allCases, id: \.self) { Text($0.label).tag($0) }
        }
        .pickerStyle(.segmented)

        if feedType == .breast {
            Picker("Side", selection: $side) {
                Text("Left").tag("L")
                Text("Right").tag("R")
            }
            .pickerStyle(.segmented)
            field("Minutes", placeholder: "e.g. 15")
        } else {
            field(feedType == .bottle ? "Millilitres" : "Grams", placeholder: "0")
        }
    }

    @ViewBuilder private var diaperFields: some View {
        Picker("Kind", selection: $diaper) {
            ForEach(DiaperKind.allCases, id: \.self) { Text($0.label).tag($0) }
        }
        .pickerStyle(.segmented)
    }

    @ViewBuilder private var sleepFields: some View {
        Text("Starts the nap now. End it from the card on Today.")
            .font(KFont.sans(13))
            .foregroundStyle(KC.muted)
    }

    @ViewBuilder private var noteField: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("What").font(KFont.sans(13, .semibold)).foregroundStyle(KC.mutedStrong)
            TextField("", text: $note, prompt: Text("e.g. Vitamin D drops"))
                .textFieldStyle(.plain)
                .font(KFont.sans(16))
                .padding(14)
                .background(KC.surface)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
    }

    private func field(_ label: String, placeholder: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(KFont.sans(13, .semibold)).foregroundStyle(KC.mutedStrong)
            TextField("", text: $amount, prompt: Text(placeholder))
                .keyboardType(.numberPad)
                .textFieldStyle(.plain)
                .font(KFont.sans(16))
                .padding(14)
                .background(KC.surface)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
    }

    private func build() -> LogEntry {
        switch kind {
        case .feed:
            return LogEntry(kind: .feed, startAt: at, amount: Int(amount),
                            side: feedType == .breast ? side : nil, feedType: feedType)
        case .diaper:
            return LogEntry(kind: .diaper, startAt: at, diaperKind: diaper)
        case .sleep:
            return LogEntry(kind: .sleep, startAt: at)
        default:
            return LogEntry(kind: kind, startAt: at, note: note.trimmingCharacters(in: .whitespaces))
        }
    }
}
