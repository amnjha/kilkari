// swift-tools-version: 5.9
import PackageDescription

/// The part of Kilkari that is arithmetic rather than interface.
///
/// Kept as its own package so it builds and tests on a Mac without Xcode or a simulator: the
/// growth standards, the return calculations and the unit conversions are the fiddliest code
/// in the app and the easiest to get quietly wrong, so they are verified on every platform
/// that can run a compiler rather than only inside a running app.
let package = Package(
    name: "KilkariCore",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "KilkariCore", targets: ["KilkariCore"]),
        .executable(name: "kilkari-core-checks", targets: ["KilkariCoreChecks"]),
    ],
    targets: [
        .target(
            name: "KilkariCore",
            // Synced from common/data by ios/tools/sync-common.sh. Not committed here: the
            // master copies live in common/ and both platforms read those.
            resources: [.copy("Resources")]
        ),
        // Checks rather than XCTest: XCTest ships with Xcode, and this package is meant to
        // stay verifiable on a Mac that only has the command line tools. `swift run
        // kilkari-core-checks` exits non-zero on the first failure, which is all CI needs.
        .executableTarget(name: "KilkariCoreChecks", dependencies: ["KilkariCore"]),
    ]
)
