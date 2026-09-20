import SwiftUI
import SwiftData

@main
struct KilkariApp: App {
    // Everything stays on the phone, as on Android: a local store, no account, no sync.
    private let container = try! ModelContainer(
        for: Baby.self, LogEntry.self, VaccineDose.self, GrowthRecord.self,
        Expense.self, FundDeposit.self, Appointment.self, Milestone.self, Doctor.self,
        Reminder.self, Investment.self,
        PaperworkRecord.self, ScannedDocument.self, Album.self, CalendarEvent.self
    )

    var body: some Scene {
        WindowGroup {
            RootView()
                .task {
                    if SampleData.requested {
                        SampleData.seed(into: container.mainContext)
                    }
                }
                .task {
                    // Runs against the store rather than a rendered screen, which is the
                    // point of keeping the writing out of the view.
                    if SampleData.exportOnLaunch {
                        Backup.writeForChecking(context: container.mainContext)
                    }
                }
        }
        .modelContainer(container)
    }
}
