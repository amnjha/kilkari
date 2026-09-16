# Release artifacts

Everything in this directory except this file is generated and gitignored. Build it with:

```bash
./tools/build-artifacts.sh
```

The script writes a **`BUILD-INFO.md`** next to the artifacts recording the exact commit, file
sizes and SHA-256s of the build that produced them — read that rather than trusting the notes
here, which describe the shape of a build rather than any particular one.

| File | What it is |
| --- | --- |
| `kilkari-<version>.aab` | The Android App Bundle **Play requires for new apps**. |
| `kilkari-<version>.apk` | Universal APK for sideloading. |
| `kilkari-<version>-mapping.txt` | R8 mapping. Upload it with the bundle, or Play crash reports for the minified build are unreadable. |
| `kilkari-<version>-debug.apk` | Debug build. Its applicationId is `com.kilkari.debug`, so it installs alongside the release. |

Without a signing key the two release filenames gain an `-unsigned` suffix and cannot be
uploaded, and the script additionally emits:

| File | What it is |
| --- | --- |
| `kilkari-<version>-DEBUGSIGNED-testing-only.apk` | The release build signed with the SDK's public debug key, purely so the minified build can be installed and verified. **Never publish it.** Uninstall it before installing a properly signed build — the signatures differ, so an update would be rejected. |

## To make these uploadable

1. Create an upload key and keep it somewhere you will not lose it:

   ```bash
   keytool -genkeypair -v -keystore kilkari-upload.jks -alias kilkari \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Copy `keystore.properties.example` to `keystore.properties` at the repo root and fill it in.
   Both the keystore and that file are gitignored.

3. Re-run `./tools/build-artifacts.sh`. It detects the keystore, signs the release artifacts and
   drops the `-unsigned` suffix; the verify step prints the signing state of each APK so you can
   confirm it took.

Upload the `.aab` and the `mapping.txt` to the Play Console.

**Back up the keystore and its passwords.** Without them you cannot publish an update to the
same listing, short of asking Google to reset the upload key under Play App Signing.
