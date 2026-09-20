# Fonts

The two faces Kilkari is set in, as files, so both platforms use the same ones.

| Face | Used for | Weights here |
|---|---|---|
| Bricolage Grotesque | Display — headings, names, big numbers | Medium, Bold, ExtraBold |
| Plus Jakarta Sans | Everything else | Regular, Medium, SemiBold, Bold, ExtraBold |

Both are licensed under the SIL Open Font License 1.1, which permits bundling in an app.
The licences are beside the files, as the OFL requires.

- Bricolage Grotesque — https://github.com/ateliertriay/bricolage
- Plus Jakarta Sans — https://github.com/tokotype/PlusJakartaSans

Static instances rather than the variable fonts: SwiftUI's `Font.custom` picks a variable
font's default instance and cannot easily move along its weight axis, so a variable file would
render every weight the same.

**iOS** lists them in `UIAppFonts` and asks for them by PostScript name
(`BricolageGrotesque-ExtraBold`, and so on).
**Android** used to pull them from the Google Fonts provider, which needs Play Services and a
network the first time. It now bundles these same files, so the app looks right on a phone
with neither.
