import SwiftUI
import SwiftData

/// First run. The one question the app cannot work without, and nothing else.
///
/// The Android app asks seven things across seven steps; this asks two and gets out of the
/// way, with the rest to follow as those screens arrive.
struct WelcomeScreen: View {
    @Environment(\.modelContext) private var context

    @State private var name = ""
    @State private var dob = Date.now

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 1, green: 0.969, blue: 0.933), KC.lilacBg],
                startPoint: .top, endPoint: .bottom
            )
            .ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Spacer(minLength: 40)

                    Text("Your journey to\nconfident parenting")
                        .font(KFont.display(34, .heavy))
                        .tracking(-0.8)
                        .foregroundStyle(KC.ink)
                        .fixedSize(horizontal: false, vertical: true)

                    Text("Feeds, sleep, vaccines, growth and money — in one place, on this phone, for whoever is holding the baby.")
                        .font(KFont.sans(15))
                        .foregroundStyle(KC.mutedStrong)
                        .fixedSize(horizontal: false, vertical: true)

                    HStack(spacing: 8) {
                        promise("wifi.slash", "Works offline")
                        promise("lock.fill", "No account")
                    }

                    KCard {
                        VStack(spacing: 0) {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Name").font(KFont.sans(13, .semibold)).foregroundStyle(KC.mutedStrong)
                                TextField("", text: $name, prompt: Text("e.g. Avika"))
                                    .font(KFont.sans(17))
                            }
                            .padding(16)

                            Rectangle().fill(KC.divider).frame(height: 1)

                            DatePicker("Date of birth", selection: $dob,
                                       in: ...Date.now, displayedComponents: .date)
                                .font(KFont.sans(14, .semibold))
                                .tint(KC.coral)
                                .padding(16)
                        }
                    }

                    PrimaryButton(label: "Begin your journey",
                                  enabled: !name.trimmingCharacters(in: .whitespaces).isEmpty) {
                        context.insert(Baby(name: name.trimmingCharacters(in: .whitespaces), dob: dob))
                    }
                    .padding(.top, 4)

                    Spacer(minLength: 20)
                }
                .padding(.horizontal, 24)
            }
        }
        .environment(\.accent, KAccents.brand)
    }

    private func promise(_ symbol: String, _ text: String) -> some View {
        HStack(spacing: 6) {
            Image(systemName: symbol).font(.system(size: 12, weight: .bold))
            Text(text).font(KFont.sans(13, .semibold))
        }
        .foregroundStyle(KC.lilacDeep)
        .padding(.horizontal, 12).padding(.vertical, 8)
        .background(KC.surface)
        .clipShape(Capsule())
    }
}
