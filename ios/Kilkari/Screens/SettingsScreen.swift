import SwiftUI
import SwiftData
import PhotosUI
import KilkariCore

/// The choices that change how the whole app reads, and the child's own details.
struct SettingsScreen: View {
    let baby: Baby

    @Environment(\.accent) private var accent
    @Environment(\.modelContext) private var context
    @State private var prefs = Preferences.shared
    @State private var photoItem: PhotosPickerItem?
    @State private var name: String = ""
    @State private var dob: Date = .now

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                SectionLabel("Who this is about")
                KCard {
                    VStack(spacing: 0) {
                        HStack(spacing: 14) {
                            ChildAvatar(photo: baby.photo, name: baby.name, size: 64, ring: accent.ring)
                            VStack(alignment: .leading, spacing: 4) {
                                PhotosPicker(selection: $photoItem, matching: .images) {
                                    Text(baby.photo == nil ? "Add a photo" : "Change the photo")
                                        .font(KFont.sans(14, .semibold))
                                        .foregroundStyle(accent.deep)
                                }
                                if baby.photo != nil {
                                    Button("Remove") { baby.photo = nil }
                                        .font(KFont.sans(13))
                                        .tint(KC.danger)
                                }
                            }
                            Spacer()
                        }
                        .padding(16)

                        Rectangle().fill(KC.divider).frame(height: 1)

                        VStack(alignment: .leading, spacing: 6) {
                            Text("Name").font(KFont.sans(13, .semibold)).foregroundStyle(KC.mutedStrong)
                            TextField("", text: $name, prompt: Text("Name"))
                                .font(KFont.sans(16))
                                .onSubmit { commitName() }
                        }
                        .padding(16)

                        Rectangle().fill(KC.divider).frame(height: 1)

                        DatePicker("Date of birth", selection: $dob, in: ...Date.now,
                                   displayedComponents: .date)
                            .font(KFont.sans(14, .semibold))
                            .tint(accent.main)
                            .padding(16)
                            .onChange(of: dob) { _, new in baby.dob = new }
                    }
                }

                // Sex only exists here because the growth curves need it: boys and girls have
                // visibly different weight-for-age distributions, and one averaged curve is a
                // line about nobody. The screen says that rather than just asking.
                SectionLabel("Growth curve").padding(.top, 4)
                KCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Picker("", selection: Binding(
                            get: { baby.sexRaw ?? "" },
                            set: { baby.sexRaw = $0.isEmpty ? nil : $0 }
                        )) {
                            Text("Not set").tag("")
                            Text("Girl").tag("girl")
                            Text("Boy").tag("boy")
                        }
                        .pickerStyle(.segmented)

                        Text("Sets which WHO curve the weight chart compares against. Without it the chart still draws, but no percentile is claimed.")
                            .font(KFont.sans(12)).foregroundStyle(KC.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(16)
                }

                SectionLabel("How things read").padding(.top, 4)
                KCard {
                    VStack(alignment: .leading, spacing: 14) {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Units").font(KFont.sans(13, .semibold)).foregroundStyle(KC.mutedStrong)
                            Picker("", selection: $prefs.metric) {
                                Text("kg · cm").tag(true)
                                Text("lb · in").tag(false)
                            }
                            .pickerStyle(.segmented)
                            Text("Everything is stored metric. This only changes what is shown, so switching is safe at any time.")
                                .font(KFont.sans(12)).foregroundStyle(KC.muted)
                                .fixedSize(horizontal: false, vertical: true)
                        }

                        VStack(alignment: .leading, spacing: 8) {
                            Text("Currency").font(KFont.sans(13, .semibold)).foregroundStyle(KC.mutedStrong)
                            HStack(spacing: 6) {
                                ForEach(Currency.allCases) { currency in
                                    KChip(label: "\(currency.symbol) \(currency.code)",
                                          selected: prefs.currency == currency) {
                                        prefs.currency = currency
                                    }
                                }
                            }
                        }
                    }
                    .padding(16)
                }

                SectionLabel("Vaccination schedule").padding(.top, 4)
                KCard {
                    VStack(spacing: 0) {
                        ForEach(Array(VaccineSchedules.all.enumerated()), id: \.element.id) { i, schedule in
                            Button {
                                prefs.scheduleId = schedule.id
                            } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: prefs.scheduleId == schedule.id
                                          ? "largecircle.fill.circle" : "circle")
                                        .font(.system(size: 20))
                                        .foregroundStyle(prefs.scheduleId == schedule.id ? accent.main : accent.ring)
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(schedule.name)
                                            .font(KFont.sans(14, .semibold)).foregroundStyle(KC.ink)
                                        Text(schedule.description)
                                            .font(KFont.sans(12)).foregroundStyle(KC.muted)
                                    }
                                    Spacer()
                                }
                                .padding(.horizontal, 14).padding(.vertical, 11)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            if i != VaccineSchedules.all.count - 1 {
                                Rectangle().fill(KC.divider).frame(height: 1)
                            }
                        }
                    }
                }

                Text("Changing the schedule regenerates every due date from the date of birth. Doses already recorded as given stay given.")
                    .font(KFont.sans(12)).foregroundStyle(KC.muted)
                    .padding(.horizontal, 4)
            }
            .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 110)
        }
        .onAppear {
            name = baby.name
            dob = baby.dob
        }
        .onDisappear(perform: commitName)
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            Task {
                // Loaded as data and kept in the store, so the picture survives the photo
                // being deleted from the library — a PHAsset identifier would not.
                if let data = try? await item.loadTransferable(type: Data.self) {
                    baby.photo = data
                }
            }
        }
    }

    private func commitName() {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        if !trimmed.isEmpty { baby.name = trimmed }
    }
}

/// The people you see, and how to reach them.
struct DoctorsScreen: View {
    @Environment(\.modelContext) private var context
    @Environment(\.accent) private var accent
    @Query(sort: \Doctor.name) private var doctors: [Doctor]
    @State private var adding = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if doctors.isEmpty {
                    KCard {
                        VStack(spacing: 10) {
                            Image(systemName: "cross.case.fill")
                                .font(.system(size: 28)).foregroundStyle(accent.deep)
                                .frame(width: 92, height: 92)
                                .background(accent.wash.onCream(0.6))
                                .clipShape(RoundedRectangle(cornerRadius: 38, style: .continuous))
                            Text("No one saved yet")
                                .font(KFont.display(17, .bold)).foregroundStyle(KC.ink)
                            Text("A name and a number, so you are not searching an inbox at 2am.")
                                .font(KFont.sans(13)).foregroundStyle(KC.muted)
                                .multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 26).padding(.horizontal, 20)
                    }
                } else {
                    KCard {
                        VStack(spacing: 0) {
                            ForEach(Array(doctors.enumerated()), id: \.element.persistentModelID) { i, doctor in
                                HStack(spacing: 12) {
                                    IconBadge(symbol: "cross.case.fill", tint: accent.deep,
                                              background: accent.bg, size: 38, corner: 11, iconSize: 17)
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(doctor.name)
                                            .font(KFont.sans(15, .semibold)).foregroundStyle(KC.ink)
                                        Text([doctor.speciality, doctor.clinic]
                                            .compactMap { $0 }.joined(separator: " · "))
                                            .font(KFont.sans(12)).foregroundStyle(KC.muted)
                                    }
                                    Spacer()
                                    if let phone = doctor.phone, let url = URL(string: "tel://\(phone.filter(\.isNumber))") {
                                        Link(destination: url) {
                                            Image(systemName: "phone.fill")
                                                .font(.system(size: 16, weight: .semibold))
                                                .foregroundStyle(accent.deep)
                                                .frame(width: 40, height: 40)
                                        }
                                    }
                                }
                                .padding(.horizontal, 14).padding(.vertical, 10)
                                if i != doctors.count - 1 { Rectangle().fill(KC.divider).frame(height: 1) }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 110)
        }
        .safeAreaInset(edge: .bottom) {
            PrimaryButton(label: "Add a doctor") { adding = true }
                .padding(.horizontal, 16).padding(.bottom, 96)
        }
        .sheet(isPresented: $adding) {
            AddDoctorSheet { context.insert($0); adding = false }
                .presentationDetents([.medium])
                .environment(\.accent, accent)
        }
    }
}

struct AddDoctorSheet: View {
    let onSave: (Doctor) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent

    @State private var name = ""
    @State private var speciality = ""
    @State private var clinic = ""
    @State private var phone = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    sheetField("Name", $name, "e.g. Dr Nair")
                    sheetField("Speciality", $speciality, "Paediatrician")
                    sheetField("Clinic", $clinic, "Where they are")
                    sheetField("Phone", $phone, "Number to call")
                    PrimaryButton(label: "Save",
                                  enabled: !name.trimmingCharacters(in: .whitespaces).isEmpty) {
                        func clean(_ s: String) -> String? {
                            let t = s.trimmingCharacters(in: .whitespaces)
                            return t.isEmpty ? nil : t
                        }
                        onSave(Doctor(name: name.trimmingCharacters(in: .whitespaces),
                                      speciality: clean(speciality),
                                      clinic: clean(clinic),
                                      phone: clean(phone)))
                        dismiss()
                    }
                    .padding(.top, 4)
                }
                .padding(20)
            }
            .background(KC.surface)
            .navigationTitle("Add a doctor")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.tint(accent.deep)
                }
            }
        }
    }
}
