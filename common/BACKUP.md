# The backup format

A Kilkari backup is a single JSON file: one object, one array per kind of record, readable in
a text editor without the app. The point is that a parent's data outlives the app that wrote
it — and that moving from one phone to the other should not mean retyping a year of feeds.

## Where the two platforms stand

**iOS writes it.** Settings → Backup & export → *Everything, as JSON*, which hands the file to
the share sheet so it lands wherever you choose.

**Android writes and reads it too.** More → Backup & export → *Moving to or from an iPhone*.
Its own `.kilkari` zip is still there and is still the right thing for moving between Android
phones: that one is a zip around the Room database, complete with photographs and scanned
pages, and restorable only by the Android app.

**Both read it back.** A file written on either phone restores on either phone. A restore
replaces everything rather than merging — merging two histories of the same baby produces
duplicate feeds nobody can tell apart — and both apps say so and ask before doing it.

Android additionally leaves out, because the format has no place for them: medicines and the
doses taken of them, teeth, and savings accounts, transfers and contributions. The screen
lists them rather than dropping them quietly.

## What is not in it

Photographs and scanned pages. They are large, they already live in the photo library and in
the app's own storage, and putting them in would turn a 40KB file into a 400MB one. A backup
is the record, not the album — the screen says so rather than letting anyone believe
otherwise.

A real file written by the iOS app lives at `common/samples/from-ios.json`, and an Android
test reads it. Not a hand-typed approximation of the format — an actual export — so the test
fails if either side drifts from what the other produces.

## Shape

```json
{
  "format": "kilkari-backup",
  "version": 1,
  "writtenBy": "ios",
  "writtenAt": "2026-09-20T14:24:04Z",
  "settings": { "currency": "INR", "metric": true, "scheduleId": "iap" },
  "baby": { "name": "Ira", "dob": "2026-09-03T14:24:04Z", "sex": "girl" },
  "logEntries": [ … ],
  "doses": [ … ],
  "growth": [ … ],
  "expenses": [ … ],
  "deposits": [ … ],
  "investments": [ … ],
  "appointments": [ … ],
  "milestones": [ … ],
  "events": [ … ],
  "albums": [ … ],
  "doctors": [ … ],
  "reminders": [ … ],
  "paperwork": [ … ]
}
```

Dates are ISO 8601 with a timezone. A field that has no value is `null`, never absent, so a
reader can tell "not recorded" from "this version did not write that field".

| Array | Fields |
|---|---|
| `logEntries` | `kind` (feed, sleep, diaper, medicine, growth, tooth), `startAt`, `endAt`, `amount`, `side` (L/R), `feedType`, `diaperKind`, `note` |
| `doses` | `group` (the label in the schedule), `vaccine`, `givenOn` |
| `growth` | `date`, `weightKg`, `lengthCm`, `headCm` |
| `expenses` | `title`, `vendor`, `category` (medical/general), `amount`, `date`, `paidFromFund` |
| `deposits` | `note`, `kind` (deposit/withdrawal), `amount` (always positive), `date` |
| `investments` | `name`, `kind`, `invested`, `monthly`, `rate`, `startedOn`, `maturesOn`, `value`, `valuedOn` |
| `appointments` | `title`, `who`, `startAt` |
| `milestones` | `title`, `note`, `date` |
| `events` | `title`, `note`, `date`, `annual` |
| `albums` | `title`, `note`, `url` |
| `doctors` | `name`, `speciality`, `clinic`, `phone` |
| `reminders` | `title`, `minuteOfDay`, `cadence` (daily/weekly), `weekday`, `enabled` |
| `paperwork` | `key`, `obtainedOn`, `skipped` |

## Two things a reader has to know

**Amounts are whole units of the currency**, never decimal. A rupee is not 0.01 of anything,
and a total of a hundred expenses should not drift. `currency` in `settings` says which unit.

**Measurements are metric**, always — kilograms and centimetres — whatever the `metric`
setting says. That setting is about what a screen shows, not about what is stored, because
the WHO growth tables speak metric and converting on the way in would lose precision on every
weigh-in.

## Spending, as CSV

The same screen writes `date,title,vendor,category,amount,currency,paid_from_fund`, sorted
oldest first, with commas and quotes escaped properly. For a spreadsheet, not for a restore.
