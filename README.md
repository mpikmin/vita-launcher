# Vita Launcher

A real Android home-screen replacement with the PS Vita system UI: peel-to-open
sheet, floating bubble grid of your actually-installed apps, and a LiveArea page
per app with Start / App info / Uninstall.

It is a native Android app (Kotlin) whose home surface is a WebView. The UI layer
is `app/src/main/assets/index.html`; everything that touches the system —
listing apps, decoding icons, firing launch intents, battery, haptics, insets —
goes through `Bridge.kt` over a `@JavascriptInterface`.

## What makes it a launcher, not just an app

`AndroidManifest.xml` declares `android.intent.category.HOME` +
`DEFAULT` on `MainActivity`, with `launchMode="singleTask"`,
`stateNotNeeded`, and `excludeFromRecents`. The `<queries>` block declares
MAIN/LAUNCHER intent visibility, which Android 11+ requires or
`queryIntentActivities` comes back empty.

## Getting an APK

### Option A — GitHub Actions (no local toolchain)
Push this folder to a GitHub repo. `.github/workflows/build-apk.yml` runs on
push and on manual dispatch. Open the run, download the **vita-launcher-debug**
artifact, unzip, and you have `app-debug.apk`.

### Option B — Android Studio
`File -> Open` this folder, let it sync, then `Build -> Build Bundle(s) / APK(s) -> Build APK(s)`.
Output lands in `app/build/outputs/apk/debug/`.

### Option C — Command line
Requires JDK 17 and the Android SDK (`ANDROID_HOME` set, platform 34 + build-tools installed).

    gradle wrapper --gradle-version 8.7
    ./gradlew assembleDebug
    adb install -r app/build/outputs/apk/debug/app-debug.apk

`assembleRelease` also works and is debug-signed, so it installs without you
creating a keystore. Generate a real keystore before shipping it anywhere.

## Installing and setting it as home

1. Install the APK (enable "install unknown apps" for your file manager).
2. Press HOME. Android asks which launcher to use — pick Vita Launcher, then
   **Always**.
3. Or open the launcher's own **Launcher** panel and tap **Set**, which opens
   the system home-app screen.

To go back to your old launcher: Settings -> Apps -> Default apps -> Home app.

## Using it

- **Peel the top-right corner** of the opening sheet to reveal the grid.
- **Swipe up/down** on the grid to change page; the dots on the right track it.
- **Tap a bubble** to open its LiveArea. **Start** launches. The corner tab
  peels the LiveArea away, or just tap it.
- **Search apps** filters by name or package; tapping a result launches it.
- **Launcher** opens preferences: theme colour, background pattern, column
  count, peel-on-open, skip-LiveArea, hide system apps, sound, haptics.

Preferences persist in the WebView's `localStorage`.

## Layout notes

Nothing is a fixed pixel size. Font size, padding, bubble diameter, column count
and rows-per-page are all derived from the live viewport, and the status bar and
gesture bar insets come from `Bridge.insets()` so content never sits under the
notch or the navigation bar.

## Files

    app/src/main/AndroidManifest.xml        HOME intent filter, package visibility
    app/src/main/java/.../MainActivity.kt   WebView host, HOME/BACK hooks, package watcher
    app/src/main/java/.../Bridge.kt         PackageManager, icons, intents, battery, insets
    app/src/main/assets/index.html          the entire UI (no libraries, no network)
    .github/workflows/build-apk.yml         CI that produces the APK
