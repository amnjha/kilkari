import SwiftUI
import SwiftData
import KilkariCore

/// The health hub: where the vaccinations stand, and the five things underneath them.
struct HealthScreen: View {
    let baby: Baby

    @Environment(\.accent) private var accent
    @Query private var doses: [VaccineDose]
    @Query(sort: \GrowthRecord.date) private var growth: [GrowthRecord]
    @Query(sort: \Appointment.startAt) private var appointments: [Appointment]
    @Query(sort: \LogEntry.startAt, order: .reverse) private var entries: [LogEntry]

    private var groups: [VaccineGroupState] {
        VaccinePlan.groups(for: baby, doses: doses)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Health").font(KFont.screenTitle).foregroundStyle(KC.ink)

                NavigationLink(value: Route.vaccines) { vaccineHero }
                    .buttonStyle(SpringPress())

                // Three hues across the five, a row at a time: green for what grows, coral
                // for what asks something of you, blue for the people you keep on file. Five
                // different colours was a grid that took longer to read than one colour did.
                HStack(alignment: .top, spacing: 10) {
                    tile(Route.growth, "scalemass.fill", KC.leafDeep, KC.leafWash, "Growth", growthDetail)
                    tile(Route.teeth, "mouth.fill", KC.leafDeep, KC.leafWash, "Teeth", teethDetail)
                }
                HStack(alignment: .top, spacing: 10) {
                    tile(Route.meds, "pills.fill", KC.coralDeep, KC.coralWash, "Medications", medsDetail)
                    tile(Route.appointments, "stethoscope", KC.coralDeep, KC.coralWash, "Appointments", apptDetail)
                }
                HStack(alignment: .top, spacing: 10) {
                    tile(Route.doctors, "cross.case.fill", KC.skyDeep, KC.skyWash, "Doctors", "Add the people you see")
                    Color.clear.frame(maxWidth: .infinity)
                }

                SectionLabel("Recent").padding(.top, 4)
                recent
            }
            .padding(.horizontal, 16)
            .padding(.top, 18)
            .padding(.bottom, 110)
        }
    }

    private var vaccineHero: some View {
        let done = groups.filter(\.isComplete).count
        let next = VaccinePlan.next(groups)
        return GradientCard(colors: [KC.tealDeep, KC.seaMid]) {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Image(systemName: "syringe.fill").foregroundStyle(.white.opacity(0.9))
                    Text("Vaccinations")
                        .font(KFont.display(20, .bold))
                        .foregroundStyle(.white)
                    Spacer()
                    Text("IAP")
                        .font(KFont.sans(11, .semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 8).padding(.vertical, 3)
                        .background(.white.opacity(0.2)).clipShape(Capsule())
                }
                Text(next.map { "\(done) given · next \($0.def.label) \($0.dueText)" }
                     ?? "\(done) given · all done")
                    .font(KFont.sans(13))
                    .foregroundStyle(.white.opacity(0.9))

                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(.white.opacity(0.25))
                        Capsule().fill(.white)
                            .frame(width: geo.size.width * progress)
                    }
                }
                .frame(height: 6)
            }
        }
    }

    private var progress: Double {
        let total = groups.reduce(0) { $0 + $1.total }
        guard total > 0 else { return 0 }
        return Double(groups.reduce(0) { $0 + $1.givenCount }) / Double(total)
    }

    private func tile(
        _ route: Route, _ symbol: String, _ fg: Color, _ wash: Color,
        _ title: String, _ detail: String
    ) -> some View {
        NavigationLink(value: route) {
            VStack(alignment: .leading, spacing: 12) {
                Circle().fill(.white.opacity(0.85)).frame(width: 44, height: 44)
                    .overlay {
                        Image(systemName: symbol)
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundStyle(fg)
                    }
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(KFont.sans(16, .bold)).foregroundStyle(KC.ink)
                    Text(detail)
                        .font(KFont.sans(12)).foregroundStyle(KC.mutedStrong)
                        .lineLimit(2).multilineTextAlignment(.leading)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .frame(minHeight: 104, alignment: .topLeading)
            .background(wash)
            .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
            .clay(corner: 26)
        }
        .buttonStyle(SpringPress())
    }

    private var growthDetail: String {
        guard let last = growth.last else { return "No measurements yet" }
        return [last.weightKg.map { String(format: "%.1f kg", $0) },
                last.lengthCm.map { String(format: "%.1f cm", $0) }]
            .compactMap { $0 }.joined(separator: " · ")
    }

    private var teethDetail: String {
        let count = entries.filter { $0.kind == .tooth }.count
        return "\(count) of 20 through"
    }

    private var medsDetail: String {
        let last = entries.first { $0.kind == .medicine }
        return last.map { $0.note ?? "Recorded" } ?? "None active"
    }

    private var apptDetail: String {
        guard let next = appointments.first(where: { $0.startAt >= .now }) else { return "Nothing booked" }
        return "Next: \(Fmt.date(next.startAt)) \(Fmt.time(next.startAt))"
    }

    private var recent: some View {
        let rows = growth.suffix(3).reversed().map {
            ($0.weightKg.map { w in String(format: "Weight %.1f kg", w) } ?? "Measurement", $0.date)
        }
        return KCard {
            if rows.isEmpty {
                Text("Nothing recorded yet.")
                    .font(KFont.sans(13)).foregroundStyle(KC.muted).padding(14)
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(rows.enumerated()), id: \.offset) { i, row in
                        HStack {
                            IconBadge(symbol: "scalemass.fill", tint: KC.leafDeep,
                                      background: KC.leafBg, size: 32, corner: 10, iconSize: 15)
                            Text(row.0).font(KFont.sans(14)).foregroundStyle(KC.ink)
                            Spacer()
                            Text(Fmt.date(row.1)).font(KFont.sans(12)).foregroundStyle(KC.muted)
                        }
                        .padding(.horizontal, 14).padding(.vertical, 11)
                        if i != rows.count - 1 { Rectangle().fill(KC.divider).frame(height: 1) }
                    }
                }
            }
        }
    }
}
