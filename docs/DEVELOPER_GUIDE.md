# Developer guide

## 1. What you need
- **JDK 21** (Temurin or OpenJDK), **Git**, and for Android the **Android SDK** command-line tools with
  `platforms;android-37.2` and `build-tools;37.0.0`. Android Studio is optional; everything below works from a terminal.
- The repository: `git clone https://github.com/MS2022Singh/Diviora.git && cd Diviora`
- Android SDK location: create `local.properties` with `sdk.dir=/path/to/Android/Sdk` (Windows: `sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk`), or set `ANDROID_HOME`.

## 2. Build and run
| Task | Linux / macOS | Windows (cmd or PowerShell) |
|---|---|---|
| All tests + Android lint | `./gradlew check :desktop:test` | `gradlew.bat check :desktop:test` |
| Android debug APK (has Developer options) | `./gradlew :app:assembleDebug` | `gradlew.bat :app:assembleDebug` |
| Android release APK (what users get) | `./gradlew :app:assembleRelease` | `gradlew.bat :app:assembleRelease` |
| Install on a USB-connected phone | `adb install -r app/build/outputs/apk/debug/app-debug.apk` | same |
| Run the desktop app | `./gradlew -Pdiviora.skipAndroid=true :desktop:run` | `gradlew.bat -Pdiviora.skipAndroid=true :desktop:run` |
| Desktop installer for *this* OS | `./gradlew -Pdiviora.skipAndroid=true :desktop:packageDeb` (or `packageDmg`) | `gradlew.bat -Pdiviora.skipAndroid=true :desktop:packageMsi` |
| Desktop end-to-end self-test | `"…/DIVIORA Music" --selftest` after `:desktop:createDistributable` | same |

Outputs: APKs in `app/build/outputs/apk/…`, installers in `desktop/build/compose/binaries/main/…`.

## 3. Releasing (automatic)
1. Work on a branch (`feat/…`, `fix/…`), then merge into `develop`, then merge `develop` into `main`.
2. **Every push to `main`** runs **Actions → Build installers**: Android APK + Windows MSI + macOS DMG + Linux DEB.
   They are published at **Releases → latest** (the release is replaced each time). It takes about 15 minutes.
3. Bump `diviora.versionName` / `diviora.versionCode` in `gradle.properties` for each release (the versionCode must increase).
4. Every push to any branch also runs **Actions → CI** (unit tests, lint, debug build, secret scan).

## 4. Repository settings (GitHub → Settings → Secrets and variables → Actions)
| Name | Type | Purpose |
|---|---|---|
| `DIVIORA_CATALOG_URL` | Variable | Your content server's `catalog.json` URL, built into every installer (empty = bundled demo catalog) |
| `DIVIORA_DEV_PASSPHRASE` | Secret | Unlocks **Developer options** in release builds (see §5). Use a long passphrase. |
| `DIVIORA_KEYSTORE_BASE64`, `DIVIORA_KEYSTORE_PASSWORD`, `DIVIORA_KEY_ALIAS`, `DIVIORA_KEY_PASSWORD` | Secrets | Private signing key for **Google Play** builds (`release.yml`, on `v*` tags). Not needed for GitHub downloads. |

APKs on the Releases page are signed with the public *sideload* key (`app/sideload.keystore`), so updates install over
older versions. Never use it for the Play Store; create a private key with
`keytool -genkeypair -keystore release.jks -alias diviora -keyalg RSA -keysize 2048 -validity 10000`,
keep it backed up offline, and store it as the secrets above (`base64 -w0 release.jks`).

## 5. Developer options (content sources): developer only
- **Release builds** (what users install) show **no** content-source controls. They only load the built-in catalog
  (`DIVIORA_CATALOG_URL`). Any other source is removed on the next sync.
- **Debug builds** (`assembleDebug`) always show **Settings → Developer options**.
- **Release build on your own phone:** set the `DIVIORA_DEV_PASSPHRASE` secret. Then in the app, tap the version line under
  **Settings → About** 7 times and enter the passphrase. Only a SHA-256 hash is in the APK. **Lock developer options** turns it off again.
- **Desktop:** start the app with environment variable `DIVIORA_DEVELOPER=1` to see Developer options.
- Sources added in Developer options exist only on that device (for testing). To reach **all users**, change the
  built-in catalog URL (`DIVIORA_CATALOG_URL`) or just edit the `catalog.json` on your server.

## 6. Publishing content
Step by step: [catalog.md](catalog.md). Template: `content-server/`. Validator: `python3 scripts/validate-catalog.py catalog.json`.

## 7. Project layout
`app/` Android app · `desktop/` desktop app · `core/model` shared models · `core/catalog` catalog/link parsers ·
`scheduler/recurrence` scheduling maths (shared by Android and desktop) · `docs/` documentation · `scripts/` helper scripts ·
`.github/workflows/` CI and installers. Details: [architecture.md](architecture.md).

## 8. Restore an earlier version
See [restore-guide.md](restore-guide.md). Each version's commit is listed in [STATE.md](STATE.md).
