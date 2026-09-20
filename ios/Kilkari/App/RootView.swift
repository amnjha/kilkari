import SwiftUI
import SwiftData

/// The five places the app is. Each carries the colour its screens are painted in, the way
/// the Android app's `NavTab` does.
enum Tab: String, CaseIterable, Identifiable {
    case today, log, health, money, more

    var id: String { rawValue }

    var label: String {
        switch self {
        case .today: return "Today"
        case .log: return "Log"
        case .health: return "Health"
        case .money: return "Money"
        case .more: return "More"
        }
    }

    var symbol: String {
        switch self {
        case .today: return "sun.max.fill"
        case .log: return "square.and.pencil"
        case .health: return "heart.fill"
        case .money: return "indianrupeesign.circle.fill"
        case .more: return "square.grid.2x2.fill"
        }
    }

    var accent: Accent {
        switch self {
        case .today: return KAccents.brand
        case .log: return KAccents.day
        case .health: return KAccents.health
        case .money: return KAccents.money
        case .more: return KAccents.quiet
        }
    }
}

struct RootView: View {
    @Environment(\.modelContext) private var context
    @Query private var babies: [Baby]
    @State private var tab: Tab = SampleData.initialTab ?? .today
    @State private var paths: [Tab: [Route]] = SampleData.initialRoute
        .map { [SampleData.initialTab ?? .today: [$0]] } ?? [:]

    var body: some View {
        Group {
            if let baby = babies.first {
                main(baby)
            } else {
                // Onboarding is a one-way door on Android too: once a baby exists the app
                // never shows it again.
                WelcomeScreen()
            }
        }
        .tint(KC.coral)
    }

    private func main(_ baby: Baby) -> some View {
        ZStack(alignment: .bottom) {
            KC.screen.ignoresSafeArea()

            // One stack per tab, so a pushed screen belongs to the tab it came from and
            // switching away and back does not lose where you were.
            NavigationStack(path: binding(for: tab)) {
                screen(for: tab, baby: baby)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                    .headerWash(tab.accent)
                    .background(KC.screen.ignoresSafeArea())
                    .navigationDestination(for: Route.self) { RouteView(route: $0, baby: baby) }
            }
            .tint(tab.accent.deep)

            KBottomNav(selected: $tab)
        }
        .environment(\.accent, tab.accent)
    }

    @ViewBuilder
    private func screen(for tab: Tab, baby: Baby) -> some View {
        switch tab {
        case .today: TodayScreen(baby: baby)
        case .log: LogScreen(baby: baby)
        case .health: HealthScreen(baby: baby)
        case .money: MoneyScreen()
        case .more: MoreScreen(baby: baby)
        }
    }

    private func binding(for tab: Tab) -> Binding<[Route]> {
        Binding(
            get: { paths[tab] ?? [] },
            set: { paths[tab] = $0 }
        )
    }
}

/// The five-tab bar: a floating rounded bar rather than a strip welded to the bottom edge, so
/// it reads as part of the same family of soft, raised things the rest of the app is built
/// from, and the active tab carries a pill in its own colour.
struct KBottomNav: View {
    @Binding var selected: Tab

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Tab.allCases) { tab in
                let on = tab == selected
                Button {
                    selected = tab
                } label: {
                    VStack(spacing: 3) {
                        ZStack {
                            Capsule()
                                .fill(on ? tab.accent.main : .clear)
                                .frame(width: 52, height: 32)
                            Image(systemName: tab.symbol)
                                .font(.system(size: 17, weight: .semibold))
                                .foregroundStyle(on ? .white : KC.muted)
                        }
                        Text(tab.label)
                            .font(KFont.sans(11, on ? .bold : .medium))
                            .foregroundStyle(on ? tab.accent.deep : KC.muted)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 4)
                    .contentShape(Rectangle())
                }
                .buttonStyle(SpringPress())
            }
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 8)
        .background(KC.surface)
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        .clay(corner: 28, elevation: 16)
        .padding(.horizontal, 12)
        .animation(.spring(response: 0.3, dampingFraction: 0.8), value: selected)
    }
}
