import Foundation

/// A test harness in thirty lines, because XCTest is not available without Xcode.
///
/// The arithmetic in KilkariCore is the part of the app most worth checking and the part
/// least dependent on a device, so it should stay checkable on any Mac with a Swift
/// compiler. `swift run kilkari-core-checks` prints a line per case and exits non-zero on the
/// first failure. When Xcode is present these same functions can be called from XCTest
/// without changing them.
enum Check {

    nonisolated(unsafe) static var failures: [String] = []
    nonisolated(unsafe) static var passed = 0

    static func equal<T: Equatable>(_ actual: T, _ expected: T, _ what: String) {
        if actual == expected {
            passed += 1
        } else {
            failures.append("\(what): expected \(expected), got \(actual)")
        }
    }

    static func close(_ actual: Double, _ expected: Double, accuracy: Double, _ what: String) {
        if abs(actual - expected) <= accuracy {
            passed += 1
        } else {
            failures.append("\(what): expected \(expected) ± \(accuracy), got \(actual)")
        }
    }

    static func isTrue(_ condition: Bool, _ what: String) {
        if condition { passed += 1 } else { failures.append("\(what): expected true") }
    }

    static func isNil<T>(_ value: T?, _ what: String) {
        if value == nil { passed += 1 } else { failures.append("\(what): expected nil, got \(value!)") }
    }

    static func notNil<T>(_ value: T?, _ what: String) {
        if value != nil { passed += 1 } else { failures.append("\(what): expected a value, got nil") }
    }

    /// Runs one group and says so, so a failure names the area it came from.
    static func group(_ name: String, _ body: () -> Void) {
        let before = failures.count
        body()
        let broke = failures.count - before
        print(broke == 0 ? "  ok   \(name)" : "  FAIL \(name) (\(broke))")
    }

    static func report() -> Never {
        print("")
        if failures.isEmpty {
            print("\(passed) checks passed")
            exit(0)
        }
        print("\(failures.count) of \(passed + failures.count) checks failed:")
        for f in failures { print("  - \(f)") }
        exit(1)
    }
}
