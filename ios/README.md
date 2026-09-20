# Kilkari for iOS

A native SwiftUI port, sharing reference data with the Android app rather than code.

## Where it is

**Running in the Simulator:** the app builds, launches, and persists what you log. Every
screen the Android app has is here bar one: five tabs, and Vaccinations, Growth, Teeth,
Medications, Appointments, Doctors, Paperwork, Documents, Photo albums, Birthdays, the
Timeline, Insights, Reminders, Settings and Backup behind them. Reminders schedule real
system notifications; Settings changes units, currency, the schedule and the growth curve,
and everything reads back in whatever is chosen.

**Done and verified:** `KilkariCore`, the arithmetic half of the app — the WHO growth
standards, unit conversion, XIRR and maturity projection, and the vaccination schedules. It
reads the same `common/data` files the Android app is checked against, and 80 assertions run
against WHO's published tables and the same expectations the Kotlin tests make.

**Not started:** importing a backup, the multi-account side of the fund, the camera and the
in-app crop (photos come from the library only), and the printable vaccination record.

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
xcrun simctl launch booted com.kilkari --sample-data --tab health --route growth
```

`--sample-data` fills an empty store with a week of feeds, naps and nappies, three weigh-ins,
the birth vaccines, a month of spending, two holdings, two reminders and a milestone. A week
rather than a day on purpose: Insights averages over a window, and one day's worth in a
seven-day window reads as "0.3 feeds a day", which is arithmetic working correctly on data
that represents nothing.

`--tab` opens on one of `today`, `log`, `health`, `money`, `more`. `--route` pushes one of
`vaccines`, `growth`, `teeth`, `meds`, `appointments`, `doctors`, `timeline`, `insights`,
`reminders`, `paperwork`, `documents`, `albums`, `events`, `backup`, `settings` on top of it.
`--money` picks `spending`, `fund` or `invest`. `--export-check` writes both exports into the
app's Documents folder at launch, so the output can be pulled off the simulator and validated
rather than taken on trust:

```bash
xcrun simctl launch booted com.kilkari --sample-data --export-check
python3 -m json.tool "$(xcrun simctl get_app_container booted com.kilkari data)/Documents/check.json"
```

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
| Persistence | Room, schema at v12 | SwiftData: eleven models, no migration story yet |
| Screens | 27 Compose screens | 21 |
| Reminders | WorkManager + AlarmManager | Done — `UNUserNotificationCenter`, repeating calendar triggers |
| Photos | Camera, picker, in-app crop | `PhotosPicker` only; no camera and no crop |
| Backup | Zip of the DB and photos | JSON and CSV export; see `common/BACKUP.md`. No import yet |
| App icon | Adaptive, from the badge | Done — the same badge, same crop |
| Fonts | Bricolage Grotesque, Plus Jakarta Sans | System faces at the same metrics, for now |

## A note on fonts

Android pulls Bricolage Grotesque and Plus Jakarta Sans through the Google Fonts provider. iOS
has no equivalent, so the faces need bundling as files — ideally in `common/fonts` so both
platforms use the same ones. Until then `KFont` falls back to the system face at the sizes,
weights and tracking taken from `Type.kt`, so every layout here is built to the right metrics
and swapping the real faces in is a change to two functions rather than to every screen.
