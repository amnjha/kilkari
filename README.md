# Kilkari

An offline-first Android baby tracker. Everything lives on the phone — no account, no server.
Built in Kotlin with Jetpack Compose and Room, from the Claude Design canvas in
[`design/Kilkari Baby Tracker.dc.html`](design/Kilkari%20Baby%20Tracker.dc.html).

## What's in it

**Five tabs** — Today · Log · Health · Money · More, plus fourteen pushed screens.

| Area | Screens |
| --- | --- |
| Today | Three interchangeable layouts: **Agenda** (default), **Hero**, **Checklist**. Switch in Settings. |
| Log | Six quick-log tiles (feed, sleep, diaper, medicine, growth, teeth) over the day's entries |
| Health | Hub → Vaccines, Vaccine group detail, Growth, Teeth, Medications, Appointments |
| Money | Three views: **Spending** (monthly split, ledger), **Fund** (the savings account everything is paid from), **Invest** (FD, RD, SIP, PPF, Sukanya Samriddhi, gold) |
| More | Timeline, Documents, Document detail, Photo albums, Birthdays & events, Reminders, Backup & export, Settings |

**Vaccination schedules** are generated from the baby's date of birth against one of four
published schedules — IAP (India private), UIP (India government), WHO, or CDC. Changing the
schedule regenerates due dates; doses already recorded stay marked.

**Recording a dose** works the same whether you tap one vaccine's *Mark given* or *Mark all
given*: both open the same sheet — date, clinic, doctor, an optional **brand** per vaccine, and
a cost. Recording a dose writes a Timeline entry and, if you enter a cost, posts a Medical
expense to Money. Marking part of a group and returning later accumulates the group's cost
rather than replacing it.

**The fund** is the savings account the child's costs come out of. You set a standing monthly
top-up (amount, day, account name) and Kilkari nudges you when it is due. The balance is
**derived, never mirrored**: deposits, less manual withdrawals, less expenses marked *paid from
fund*, less investment contributions funded from it. Deleting an expense or a holding restores
the balance on its own — there are no duplicate rows to keep in sync.

**Investments** cover the instruments a parent actually opens for a child: fixed and recurring
deposits, mutual fund SIPs, PPF, Sukanya Samriddhi, gold, or anything else. Each holding tracks
what has been put in (its contribution ledger), what it is worth now (you restate the value when
it moves), and — for fixed instruments — what the bank says it will be worth at maturity. The app
does not project returns or invent numbers; it records what you tell it.

**Currency** is a setting (₹ default, plus $ / € / £). Amounts are stored in whole rupees and
converted for display, so switching currency reformats every screen at once.

## Running it

Requires JDK 17 and the Android SDK (compileSdk 35, minSdk 26).

```bash
./gradlew :app:installDebug
```

Or open the project in Android Studio and hit Run. `local.properties` is generated locally and
is not committed — Android Studio writes it for you, or:

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

Build a release APK (R8 + resource shrinking, ~1.5 MB) with:

```bash
./gradlew :app:assembleRelease
```

## Layout

```
app/src/main/java/com/kilkari/
├── data/
│   ├── db/         Room entities, DAOs, database, type converters
│   ├── prefs/      DataStore settings (currency, schedule, units, Today variant)
│   ├── repo/       KilkariRepository — the single data entry point; BackupManager
│   └── seed/       The four vaccination schedules
├── domain/         Models, enums, and all date/money/age formatting
├── ui/
│   ├── theme/      Colour tokens, type scale, theme
│   ├── components/ Shared kit — cards, chips, sheets, nav bar, icon mapping
│   ├── nav/        Routes and NavHost
│   ├── screens/    One file per screen
│   └── sheets/     Bottom-sheet contents, grouped by area
└── work/           Daily reminder worker
```

### A few decisions worth knowing

- **One ViewModel.** Kilkari is a single-baby surface with heavy cross-screen coupling, so
  `KilkariViewModel` holds shared state rather than splitting per screen.
- **Icons.** The design names Material Symbols Rounded glyphs. `KIcons` maps those names onto
  Compose's icon set so screen code keeps the design's vocabulary; `dentistry` has no Compose
  equivalent and is drawn locally as `KIcons.Tooth`.
- **Fonts.** Bricolage Grotesque (display) and Plus Jakarta Sans (UI) load through the Google
  Fonts downloadable-font provider, with a platform sans fallback.
- **Light only.** The design has one ground colour and its tinted accent cards read incorrectly
  inverted, so the app does not follow the system dark theme.
- **Money precision.** Amounts are stored as whole INR (`amountInr`) and converted at display
  time. Rates are fixed constants, matching the prototype — there is no FX lookup.

## Data, backup and privacy

Nothing leaves the device unless you export it.

- **Backup** writes a `.kilkari` zip (SQLite database + scanned pages) through the Storage
  Access Framework, so you choose where it lands. Backups are **not encrypted**.
- **Restore** replaces the current database and scans, and needs an app restart to take effect.
- **CSV export** writes logs, growth, vaccines, expenses, timeline, events, fund movements,
  investments and contributions as one file.
- **Vaccination record PDF** is a one-page A4 immunisation record for a school or clinic.
- **Documents** are camera captures stored in app-private storage (`files/documents`), shared
  only through Android's share sheet.
- **Photo albums** are Google Photos links; the app never stores the photos.

## Not done yet

- Multi-baby support — the schema has a `babyId` throughout but the UI assumes one baby.
- Unit conversion. Settings exposes a kg·cm / lb·in toggle; the display formatters are still
  metric-only, so switching it currently changes only the label.
- Growth percentile curves. The design shows "55th pct" copy; the app charts raw weights
  without WHO reference data.
- Editing existing entries — most screens support add and delete, not edit.
- The fund assumes a single account. Multiple accounts, transfers between them, and reconciling
  against a bank statement are not modelled.
- Investment values are whatever you last entered. There is no price feed, no XIRR, and no
  maturity projection — an FD's maturity value is a field you fill in from the bank, not a
  calculation.
- No tests yet.
