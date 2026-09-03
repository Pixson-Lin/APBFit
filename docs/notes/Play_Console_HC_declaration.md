# Play Console — Health Connect declaration (owner checklist)

Complete in [Google Play Console](https://play.google.com/console) before or with the **1.4.20260903** internal upload.

## App content → Health apps

1. Open **App content** → **Health apps** (or **Health Connect** / data access declaration, depending on console UI).
2. Declare that APBFit **writes** the following Health Connect data types:
   - **Steps** (read + write — app requests `READ_STEPS` for write verification)
   - **Distance** (write)
   - **Exercise** (write)
3. Purpose: user-initiated simulated walking/running activity written locally to Health Connect on the device.
4. Set the **privacy policy URL** to the published policy page (GitHub Pages):
   - `https://pixson-lin.github.io/APBFit/privacy/`
   - After merging to `main`, deploy Pages (see README or owner notes below).

## Store listing

- Confirm **Privacy policy** field matches the URL above.
- Internal testing track: upload **AAB/APK** built with `versionCode` **26090301** / `versionName` **1.4.20260903** (`targetSdk` 36).

## Build & upload commands

```powershell
.\gradlew.bat :app:bundleRelease
# or for sideload smoke: .\gradlew.bat :app:assembleRelease
```

Upload artifact from `app/build/outputs/bundle/release/` (preferred) or `app/build/outputs/apk/release/`.

## After upload

- Send updated invite text from [docs/notes/Internal_test_notes.txt](notes/Internal_test_notes.txt).
- Ask testers to **uninstall old builds** before installing the HC version.
