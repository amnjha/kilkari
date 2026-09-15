# Release artifacts — Kilkari 1.0 (versionCode 1)

Built from `51b2053` (bottom-nav fix included).

Built from the repo with `./gradlew :app:bundleRelease :app:assembleRelease`
(R8 + resource shrinking, targetSdk 35, minSdk 26).

| File | Size | What it is |
| --- | --- | --- |
| `kilkari-1.0-unsigned.aab` | 4.85 MB | The Android App Bundle **Play requires for new apps**. Unsigned. |
| `kilkari-1.0-unsigned.apk` | 2.42 MB | Universal APK for sideloading. Unsigned, so not installable as-is. |
| `kilkari-1.0-DEBUGSIGNED-testing-only.apk` | 2.44 MB | The same release build signed with the SDK's public debug key, purely to verify the minified build runs. **Never publish this**, and uninstall it before installing a properly signed build — the signatures differ, so an update would be rejected. |

## To make these uploadable

1. Create an upload key and keep it somewhere you will not lose it:

   ```bash
   keytool -genkeypair -v -keystore kilkari-upload.jks -alias kilkari \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Copy `keystore.properties.example` to `keystore.properties` at the repo root and fill it in.
   Both the keystore and that file are gitignored.

3. Rebuild — the signing config picks it up automatically:

   ```bash
   ./gradlew :app:bundleRelease
   ```

Upload `app/build/outputs/bundle/release/app-release.aab` to the Play Console.

**Back up the keystore and its passwords.** Without them you cannot publish an update to the
same listing, short of asking Google to reset the upload key under Play App Signing.
