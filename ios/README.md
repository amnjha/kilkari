# Kilkari for iOS

A native SwiftUI port, sharing reference data with the Android app rather than code.

## Where it is

**Done and verified:** `KilkariCore`, the arithmetic half of the app — the WHO growth
standards, unit conversion, XIRR and maturity projection, and the vaccination schedules. It
reads the same `common/data` files the Android app is checked against, and 80 assertions run
against WHO's published tables and the same expectations the Kotlin tests make.

**Not started:** the app target, persistence, notifications, and every screen.

## Running the checks

No Xcode needed — this package builds and runs with the Command Line Tools alone, which is
the point of keeping the arithmetic separate from the interface:

```bash
./ios/tools/sync-common.sh
cd ios/KilkariCore && swift run kilkari-core-checks
```

`sync-common.sh` copies `common/data/*.json` into the package's resources. Those copies are
gitignored; the masters live in `common/data` and both platforms read from there.

XCTest ships with Xcode, not the Command Line Tools, so the checks are a small executable
target with a thirty-line harness instead. Once Xcode is installed they can be called from
XCTest unchanged.

## What the app still needs

| Piece | Android has | iOS needs |
|---|---|---|
| App target | Gradle + AGP | An Xcode project, or XcodeGen from a `project.yml` |
| Persistence | Room, schema at v12 | SwiftData or GRDB, plus an importer for Android backups |
| Reminders | WorkManager + AlarmManager | `UNUserNotificationCenter`, `BGTaskScheduler` |
| Screens | 27 Compose screens | SwiftUI equivalents |
| Design system | `ui/theme` + `Accent` | The same tokens, as a Swift `Color` set |
| Photos | Camera, picker, in-app crop | `PhotosPicker`, `AVCapture`, a crop view |

## Xcode

Not installed on the development machine as of this writing: `xcode-select -p` points at
`/Library/Developer/CommandLineTools`, so there is no `xcodebuild` and no Simulator. Swift
itself, and the macOS SDK that comes with the tools, are enough to compile and check
everything in `KilkariCore` — but nothing here has been built for an iOS target or run on a
device or simulator yet, and that is worth knowing before trusting any of it on a phone.

Install Xcode from the App Store, then:

```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
xcodebuild -runFirstLaunch
xcrun simctl list devices available
```
