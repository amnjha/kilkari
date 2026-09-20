# Illustrations

Every slot below is already wired up. The app looks for a drawable by **exact file name**; until
one exists it draws a soft blob of the screen's colour with an icon in it, so nothing is broken
while the artwork is still being made.

**To add one:** drop the file into `app/src/main/res/drawable-nodpi/` with the name in the table.
No code change, no rebuild of anything else — it appears the next time the app is built.

## What every file needs

| | |
|---|---|
| Format | **PNG, transparent background** (no white box behind the drawing) |
| Size | **1024 × 1024**, square, except the welcome piece — see below |
| Margin | Keep the drawing inside the middle **88%**; the corners get clipped on some slots |
| Detail | It is shown at **132–148dp** — roughly 400px on a phone. Nothing thinner than ~6px at 1024 will survive |
| Text | **None.** The headline is drawn by the app, in the app's font, and has to be translatable |
| Style | Pick one and keep it across all of them — flat shapes with a soft shadow, or a single-weight line, but not both |

## The palette

The app is warm cream with nine hue families. Each slot sits on a screen of a particular colour,
listed below — the drawing should use **that hue plus cream, and at most one other** from this list,
or it will fight the screen it lands on.

| Family | Main | Deep | Wash (the ground it sits on) |
|---|---|---|---|
| Coral (brand, clinical) | `#C94A30` | `#A93A24` | `#F4C9BE` |
| Clay (medicines) | `#C4703A` | `#9C5526` | `#F3CDB3` |
| Gold (money) | `#B07C11` | `#8A5F09` | `#F0D7A5` |
| Leaf (growth) | `#487F3C` | `#35622C` | `#CDF4C2` |
| Teal (health) | `#347A73` | `#26605A` | `#C5F4EE` |
| Sea (records) | `#2F7B80` | `#235F63` | `#C5F1F3` |
| Sky (the day) | `#2A72B8` | `#1F568D` | `#C5DCF3` |
| Lilac (reminders, settings) | `#6C57D6` | `#4F3DAE` | `#CEC5F3` |
| Rose (keepsakes, photos, events) | `#B93B6B` | `#96294F` | `#F5C4D4` |
| Ink / cream | `#332420` | | `#FBF5EC` |

## Start with these five

If only a few get made, these are the ones a parent sees first and most often.

| File name | Colour | What it should show |
|---|---|---|
| `welcome_family.png` | Lilac | Already in place. The other pieces should sit beside it without looking borrowed. |
| `art_onboard_baby.png` | Coral | A newborn — the very first question the app asks is who it is tracking |
| `art_empty_timeline.png` | Rose | An empty photo frame or a blank page waiting for a first memory |
| `art_empty_log.png` | Sky | A bottle, a moon and a nappy together — the day not yet recorded |
| `art_celebrate.png` | Coral | Something finished well: a small flag, confetti, a completed card |

## Onboarding — one per step

Shown at 148dp above the step's question.

| File name | Colour | Step | What it should show |
|---|---|---|---|
| `art_onboard_baby.png` | Coral | "Who are we tracking?" | A newborn, or a name tag on a cot |
| `art_onboard_measurements.png` | Leaf | "Birth measurements" | Baby scales, or a height chart with a small figure |
| `art_onboard_schedule.png` | Teal | "Vaccination schedule" | A calendar with a syringe or a plaster — gentle, not clinical |
| `art_onboard_money.png` | Gold | "Currency" | A piggy bank or a jar with coins |
| `art_onboard_done.png` | Coral | "You're set" | A parent and baby together, settled — the reward for finishing setup |

## Empty states — what a screen shows before it has anything

Shown at 132dp, centred, above a headline and a line of help text.

| File name | Colour | Screen | What it should show |
|---|---|---|---|
| `art_empty_timeline.png` | Rose | Timeline | A blank frame, or a thread with nothing on it yet |
| `art_empty_photos.png` | Rose | Photo albums | A stack of photos, or an album cover |
| `art_empty_documents.png` | Sea | Documents | A folder with a certificate half out of it |
| `art_empty_appointments.png` | Coral | Appointments | A calendar page with a stethoscope |
| `art_empty_doctors.png` | Sky | Doctors | A doctor's card, or a name plate |
| `art_empty_events.png` | Rose | Birthdays & events | A cake with one candle, or bunting |
| `art_empty_medicines.png` | Clay | Medications | A medicine bottle with a dropper |
| `art_empty_reminders.png` | Lilac | Reminders | A small bell, or an alarm clock |
| `art_empty_growth.png` | Leaf | Growth | A plant and a ruler, or a rising line |
| `art_empty_money.png` | Gold | The fund | A piggy bank, empty |
| `art_empty_log.png` | Sky | Today's entries | Bottle, moon and nappy together |

## Anything else

| File name | Colour | Where |
|---|---|---|
| `art_celebrate.png` | Coral | A vaccine course finished, a first tooth, milestones caught up |

## The welcome image

`welcome_family.png` is licensed for use in this app. It sets the tone the rest of the
artwork should match: soft shapes, warm palette, a parent and a baby rather than an icon.
