import SwiftUI

/// Every screen that is pushed rather than tabbed to.
enum Route: Hashable {
    case vaccines
    case growth
    case teeth
    case meds
    case appointments
    case doctors
    case timeline
    case insights
    case reminders
    case paperwork
    case documents
    case albums
    case events
    case backup
    case settings

    /// The colour the destination wears, grouped by what the screen is about rather than by
    /// which tab it hangs off — the same map the Android app keeps in `Routes.accentFor`.
    var accent: Accent {
        switch self {
        case .vaccines, .appointments: return KAccents.health
        case .growth, .teeth: return KAccents.growth
        case .meds: return KAccents.care
        case .doctors, .paperwork, .documents, .backup: return KAccents.records
        case .timeline, .albums, .events: return KAccents.memories
        case .insights, .reminders: return KAccents.quiet
        case .settings: return KAccents.quiet
        }
    }

    var title: String {
        switch self {
        case .vaccines: return "Vaccinations"
        case .growth: return "Growth"
        case .teeth: return "Teeth"
        case .meds: return "Medications"
        case .appointments: return "Appointments"
        case .doctors: return "Doctors"
        case .timeline: return "Timeline"
        case .insights: return "Insights"
        case .reminders: return "Reminders"
        case .paperwork: return "Paperwork"
        case .documents: return "Documents"
        case .albums: return "Photo albums"
        case .events: return "Birthdays & events"
        case .backup: return "Backup & export"
        case .settings: return "Settings"
        }
    }
}

/// Resolves a route to its screen, and paints everything inside in that route's colour so a
/// pushed screen still feels like where it came from.
struct RouteView: View {
    let route: Route
    let baby: Baby

    var body: some View {
        Group {
            switch route {
            case .vaccines: VaccinesScreen(baby: baby)
            case .growth: GrowthScreen(baby: baby)
            case .teeth: TeethScreen()
            case .meds: MedsScreen()
            case .appointments: AppointmentsScreen()
            case .doctors: DoctorsScreen()
            case .timeline: TimelineScreen()
            case .insights: InsightsScreen()
            case .reminders: RemindersScreen()
            case .paperwork: PaperworkScreen(baby: baby)
            case .documents: DocumentsScreen()
            case .albums: AlbumsScreen()
            case .events: EventsScreen(baby: baby)
            case .backup: BackupScreen(baby: baby)
            case .settings: SettingsScreen(baby: baby)
            }
        }
        .environment(\.accent, route.accent)
        .background(KC.screen.ignoresSafeArea())
        .navigationTitle(route.title)
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// A screen the Android app has and this one does not, saying so plainly rather than being
/// left out of the navigation and looking like a dead end.
struct NotYetScreen: View {
    let route: Route
    let note: String
    @Environment(\.accent) private var accent

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                Image(systemName: "hammer.fill")
                    .font(.system(size: 30))
                    .foregroundStyle(accent.deep)
                    .frame(width: 100, height: 100)
                    .background(accent.wash.onCream(0.7))
                    .clipShape(RoundedRectangle(cornerRadius: 40, style: .continuous))
                Text("Not built yet")
                    .font(KFont.display(17, .bold)).foregroundStyle(KC.ink)
                Text(note)
                    .font(KFont.sans(13)).foregroundStyle(KC.muted)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 32)
            .padding(.top, 60)
        }
    }
}
