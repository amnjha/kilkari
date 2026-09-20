# Kilkari for iOS

A native SwiftUI port, sharing reference data with the Android app rather than code.

## Where it is

**Running in the Simulator:** the app builds, launches, and persists what you log. Welcome,
Today and Log are built; Health, Money and More are honest placeholders that say so.

**Done and verified:** `KilkariCore`, the arithmetic half of the app — the WHO growth
standards, unit conversion, XIRR and maturity projection, and the vaccination schedules. It
reads the same `common/data` files the Android app is checked against, and 80 assertions run
against WHO's published tables and the same expectations the Kotlin tests make.

**Not started:** notifications, photos, backup, and the twenty-odd screens behind the last
three tabs.

## Building and running

The project file is generated, not committed — an `.xcodeproj` is a merge-conflict machine,
and everything it carries is in `project.yml` in fifty readable lines instead.

```bash
brew install xcodegen
cd ios && xcodegen generate
open Kilkari.xcodeproj
```

Or from the command line, without switching the system-wide developer directory:

```bash
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
cd ios
xcodebuild -project Kilkari.xcodeproj -scheme Kilkari \
    -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath build build
xcrun simctl install booted build/Build/Products/Debug-iphonesimulator/Kilkari.app
xcrun simctl launch booted com.kilkari
```

Two debug-only launch arguments, compiled out of release builds, so any screen is one launch
away without tapping through the app to reach it:

```bash
xcrun simctl launch booted com.kilkari --sample-data --tab log
```

`--sample-data` fills an empty store with a baby and a day's entries. `--tab` opens on one of
`today`, `log`, `health`, `money`, `more`.

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

| Piece | Android has | iOS |
|---|---|---|
| App target | Gradle + AGP | Done — XcodeGen from `project.yml` |
| Design system | `ui/theme` + `Accent` | Done — the same 87 colours, read out of the Kotlin |
| Persistence | Room, schema at v12 | SwiftData, with `Baby` and `LogEntry` so far |
| Screens | 27 Compose screens | Welcome, Today and Log |
| Reminders | WorkManager + AlarmManager | `UNUserNotificationCenter`, `BGTaskScheduler` |
| Photos | Camera, picker, in-app crop | `PhotosPicker`, `AVCapture`, a crop view |
| Backup | Zip of the DB and photos | An importer for the Android format |
| Fonts | Bricolage Grotesque, Plus Jakarta Sans | System faces at the same metrics, for now |

## A note on fonts

Android pulls Bricolage Grotesque and Plus Jakarta Sans through the Google Fonts provider. iOS
has no equivalent, so the faces need bundling as files — ideally in `common/fonts` so both
platforms use the same ones. Until then `KFont` falls back to the system face at the sizes,
weights and tracking taken from `Type.kt`, so every layout here is built to the right metrics
and swapping the real faces in is a change to two functions rather than to every screen.
