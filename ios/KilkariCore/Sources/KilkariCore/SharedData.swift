import Foundation

/// The reference data both apps read, loaded once from the bundled copies of `common/data`.
///
/// The WHO tables and the vaccination schedules are the two things in Kilkari that must not
/// drift between platforms: a schedule that differs by a week, or a median weight off in the
/// third decimal, is a bug nobody notices until a parent compares two phones. They live in
/// `common/data` as the single master, get synced into each platform's resources at build
/// time, and are parsed here rather than retyped into Swift.
enum SharedData {

    /// Decoding failures are programmer error, not something to recover from at runtime: the
    /// files ship inside the app, so if one will not parse the build is wrong.
    static func load<T: Decodable>(_ type: T.Type, from name: String) -> T {
        guard let url = Bundle.module.url(forResource: name, withExtension: "json", subdirectory: "Resources")
            ?? Bundle.module.url(forResource: name, withExtension: "json")
        else {
            fatalError("\(name).json is missing. Run ios/tools/sync-common.sh.")
        }
        do {
            return try JSONDecoder().decode(type, from: Data(contentsOf: url))
        } catch {
            fatalError("\(name).json could not be read: \(error)")
        }
    }
}
