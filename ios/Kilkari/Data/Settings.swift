import Foundation
import Observation
import SwiftUI
import KilkariCore

/// What money is shown in. Rates are the ones the Android app carries: fixed, and only used
/// to relabel amounts that were entered in one currency and are being read in another.
enum Currency: String, CaseIterable, Identifiable {
    case inr = "INR"
    case usd = "USD"
    case eur = "EUR"
    case gbp = "GBP"

    var id: String { rawValue }
    var code: String { rawValue }

    var symbol: String {
        switch self {
        case .inr: return "₹"
        case .usd: return "$"
        case .eur: return "€"
        case .gbp: return "£"
        }
    }
}

/// The handful of choices that change how the whole app reads.
///
/// UserDefaults rather than the model store: these are preferences, not records. Nothing here
/// changes what is saved — amounts stay in whole units of the chosen currency, weights stay in
/// kilograms — only how it is shown, which is what makes switching safe at any time.
@Observable
final class Preferences {
    static let shared = Preferences()

    var currency: Currency {
        didSet { defaults.set(currency.rawValue, forKey: Keys.currency) }
    }

    /// Kilograms and centimetres, or pounds, ounces and inches.
    var metric: Bool {
        didSet { defaults.set(metric, forKey: Keys.metric) }
    }

    var scheduleId: String {
        didSet { defaults.set(scheduleId, forKey: Keys.schedule) }
    }

    /// What the parent calls the pot the child's costs come out of.
    var fundName: String {
        didSet { defaults.set(fundName, forKey: Keys.fundName) }
    }

    /// What they mean to move into it each month, and on which day. Zero means no plan, and
    /// the screen asks for one rather than nagging about a figure nobody set.
    var fundMonthly: Int {
        didSet { defaults.set(fundMonthly, forKey: Keys.fundMonthly) }
    }

    var fundDepositDay: Int {
        didSet { defaults.set(fundDepositDay, forKey: Keys.fundDay) }
    }

    private let defaults = UserDefaults.standard

    private enum Keys {
        static let currency = "kilkari.currency"
        static let metric = "kilkari.metric"
        static let schedule = "kilkari.schedule"
        static let fundName = "kilkari.fund.name"
        static let fundMonthly = "kilkari.fund.monthly"
        static let fundDay = "kilkari.fund.day"
    }

    private init() {
        currency = Currency(rawValue: defaults.string(forKey: Keys.currency) ?? "") ?? .inr
        metric = defaults.object(forKey: Keys.metric) as? Bool ?? true
        scheduleId = defaults.string(forKey: Keys.schedule) ?? "iap"
        fundName = defaults.string(forKey: Keys.fundName) ?? "Baby fund"
        fundMonthly = defaults.integer(forKey: Keys.fundMonthly)
        fundDepositDay = max(1, min(defaults.object(forKey: Keys.fundDay) as? Int ?? 1, 28))
    }

    /// "₹1,899".
    func money(_ amount: Int) -> String {
        currency.symbol + amount.formatted(.number.grouping(.automatic))
    }

    /// "3.68 kg" or "8 lb 2 oz", from the same stored kilograms.
    func weight(_ kg: Double?) -> String {
        guard let kg else { return "—" }
        return metric ? String(format: "%.2f kg", kg) : Units.weightLabel(kg)
    }

    /// "50.4 cm" or "19.8 in".
    func length(_ cm: Double?) -> String {
        guard let cm else { return "—" }
        return metric ? String(format: "%.1f cm", cm) : Units.lengthLabel(cm)
    }

    /// What a weigh-in gained since the last one: grams while metric, ounces otherwise,
    /// because pounds would round most of a week's gain away.
    func weightDelta(_ kgDelta: Double) -> String {
        Units.deltaLabel(kgDelta: kgDelta, metric: metric)
    }
}

private struct PreferencesKey: EnvironmentKey {
    static let defaultValue = Preferences.shared
}

extension EnvironmentValues {
    var prefs: Preferences {
        get { self[PreferencesKey.self] }
        set { self[PreferencesKey.self] = newValue }
    }
}
