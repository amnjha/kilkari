# Icons

Glyphs the two apps share, for the handful where the platforms have no common equivalent.

Android draws almost everything from Compose's `material-icons-extended`, and iOS from SF
Symbols. The two sets mostly agree closely enough that nobody would notice — a bottle is a
bottle. Where they do not, the Material glyph is the one that ships on both, and the file
lives here.

| File | Why |
|---|---|
| `baby_changing_station.svg` | The nappy icon. SF Symbols has no changing-station glyph, and the nearest — a walking child — reads as something else entirely. |

Material Symbols and Material Icons are Apache 2.0, which permits redistribution. See
https://github.com/google/material-design-icons.

**iOS** carries a copy in `ios/Kilkari/Assets.xcassets`, as a template-rendered asset with its
vector representation preserved, so it tints and scales like an SF Symbol.
**Android** renders it from the icon library rather than from this file — the master is here
so that the two cannot drift, and so the next one has somewhere to go.
