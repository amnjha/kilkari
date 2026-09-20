import Foundation
import SwiftData
import SwiftUI

/// A dose that has actually been given.
///
/// Only given doses are stored. What is still due is derived from the schedule and the date
/// of birth, so switching schedules or correcting a birthday moves every outstanding dose
/// without a migration, and nothing can be left pointing at a group that no longer exists.
@Model
final class VaccineDose {
    /// The group's label in the schedule, e.g. "6 weeks".
    var groupLabel: String
    var vaccineName: String
    var givenOn: Date

    init(groupLabel: String, vaccineName: String, givenOn: Date = .now) {
        self.groupLabel = groupLabel
        self.vaccineName = vaccineName
        self.givenOn = givenOn
    }
}

/// One weigh-in. Stored metric, shown in whatever the parent reads.
@Model
final class GrowthRecord {
    var date: Date
    var weightKg: Double?
    var lengthCm: Double?
    var headCm: Double?

    init(date: Date = .now, weightKg: Double? = nil, lengthCm: Double? = nil, headCm: Double? = nil) {
        self.date = date
        self.weightKg = weightKg
        self.lengthCm = lengthCm
        self.headCm = headCm
    }
}

enum ExpenseCategory: String, Codable, CaseIterable, Identifiable {
    case medical, general

    var id: String { rawValue }
    var label: String { self == .medical ? "Medical" : "General" }
    var tint: Color { self == .medical ? KC.coral : KC.sea }
}

/// Money out. Amounts are whole units of the chosen currency, never floating point: a rupee
/// is not 0.01 of anything and a total of a hundred expenses should not drift.
@Model
final class Expense {
    var title: String
    var vendor: String?
    var categoryRaw: String
    var amount: Int
    var date: Date
    var paidFromFund: Bool

    init(
        title: String,
        vendor: String? = nil,
        category: ExpenseCategory,
        amount: Int,
        date: Date = .now,
        paidFromFund: Bool = true
    ) {
        self.title = title
        self.vendor = vendor
        self.categoryRaw = category.rawValue
        self.amount = amount
        self.date = date
        self.paidFromFund = paidFromFund
    }

    var category: ExpenseCategory { ExpenseCategory(rawValue: categoryRaw) ?? .general }
}

/// Money in. Deposits build the fund an expense is paid out of.
@Model
final class FundDeposit {
    var note: String?
    var amount: Int
    var date: Date

    init(note: String? = nil, amount: Int, date: Date = .now) {
        self.note = note
        self.amount = amount
        self.date = date
    }
}

/// A visit, booked or past.
@Model
final class Appointment {
    var title: String
    var who: String?
    var startAt: Date

    init(title: String, who: String? = nil, startAt: Date) {
        self.title = title
        self.who = who
        self.startAt = startAt
    }
}

/// A moment worth keeping.
@Model
final class Milestone {
    var title: String
    var note: String?
    var date: Date

    init(title: String, note: String? = nil, date: Date = .now) {
        self.title = title
        self.note = note
        self.date = date
    }
}

/// Someone you can call. Kept because at 2am nobody wants to search an inbox for a number.
@Model
final class Doctor {
    var name: String
    var speciality: String?
    var clinic: String?
    var phone: String?

    init(name: String, speciality: String? = nil, clinic: String? = nil, phone: String? = nil) {
        self.name = name
        self.speciality = speciality
        self.clinic = clinic
        self.phone = phone
    }
}
