# iOS Style Launcher — Native Android Project

This is a native Android launcher project written in **Kotlin + Jetpack Compose**. It is not a WebView wrapper around the old HTML project.

## What it does
- Declares the Android `MAIN` + `HOME` + `DEFAULT` intent filter. After installation, Android can offer it as the device's Home app. Select it under **Settings → Apps → Default apps → Home app** (wording varies by Android manufacturer).
- Reads installed launcher activities from Android `PackageManager` and launches the real installed apps with their real icons and labels.
- Includes an iOS-inspired Home screen, dock, app library/search, horizontal Home pages, swipe-up app library, top-area Control Center, and a Settings customization screen.
- Includes a native Android WebView browser activity with URL/search, back/forward/reload and external-open actions. It does not use the PHP proxy from the web version.
- Includes a native Notes activity backed by local SQLite.
- Uses Android `SQLiteOpenHelper` (`launcher_state.db`) to persist launcher preferences, notes and activity events.
- Reads actual connectivity type, online status, Wi-Fi SSID (subject to Android permissions/location policy), Bluetooth enabled state and mobile data radio type where Android grants access.
- Brightness slider changes the launcher's window brightness; volume slider changes media stream volume. Connectivity/Focus shortcuts open Android system panels where Android restricts direct toggling.

## Build a debug APK
1. Install **Android Studio** (current stable) with Android SDK Platform 35 and Build Tools.
2. Open this folder (`ios_native_launcher`) in Android Studio.
3. Allow Gradle sync and install any requested SDK components.
4. Connect an Android phone with USB debugging enabled, or use an emulator.
5. Choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
6. Android Studio creates the debug APK under `app/build/outputs/apk/debug/app-debug.apk`.
7. Transfer that APK to the phone and install it. Android may ask you to allow installation from that source.
8. Open the app once, then choose it as the default Home app in Android settings.

## Why an APK is not included here
The build environment used to prepare this project has Java/Kotlin but does not have the Android SDK, Android Gradle Plugin cache, `aapt2`, D8, or APK signing tools, and external downloads are unavailable. A valid installable APK cannot be compiled or signed in this environment. This ZIP is the complete native Android Studio source project, not an APK.

## Android platform boundaries
- Android's system Recents screen is owned by the OS. A third-party launcher cannot replace or selectively dismiss system recent tasks. The normal Android gesture-navigation swipe-up-and-hold continues to open system Recents.
- The OS notification shade / Quick Settings is also owned by Android. This app's Control Center is an iOS-inspired launcher panel inside the Home app; it cannot replace the system shade or silently toggle restricted radios.
- Wi-Fi SSID access can require location permission and Location Services enabled, depending on Android version/OEM. Bluetooth state requires `BLUETOOTH_CONNECT` on Android 12+. Cellular details may be restricted by carrier/OEM and phone-state permission.
- Apps such as Camera, Photos, Maps, Phone, Messages, Calendar, Music, Health and Store are opened as the actual apps installed on the device when available. This launcher does not counterfeit or replace those separate system applications.
- The included browser is a basic WebView browser activity, not a complete replacement for Chrome/Safari. Sites may still require Chrome Custom Tabs or external browser support for some sign-in, DRM or payment flows.

## Project layout
- `app/src/main/java/.../MainActivity.kt` — Home screen, app library, Control Center and Settings UI
- `DeviceState.kt` — Android network/Wi-Fi/Bluetooth/cellular state reader
- `LauncherDb.kt` — SQLite persistence
- `BrowserActivity.kt` — native WebView browser
- `NotesActivity.kt` — SQLite-backed notes
- `AndroidManifest.xml` — launcher HOME intent and permissions


## iOS-inspired visual refresh

The Home screen now includes animated horizontal page transitions, staggered icon entrance motion, rounded-square gradient app glyphs, weather/calendar summary widgets, page dots, a translucent dock, and a swipe-up launcher lock-screen-style welcome overlay.

The Home screen bundles selected iOS 18 icon PNGs from [SysAdminDoc/iOSIconPack](https://github.com/SysAdminDoc/iOSIconPack), released under the MIT License. Matching installed apps (Messages, Calendar, Photos, Camera, Weather, Clock, Settings, Notes, Maps, Music, Files, Health, Phone, Safari/Chrome, Amazon, Gmail, WhatsApp, YouTube, Calculator and App Store) use the themed artwork; unmatched apps keep their installed Android icon. The required MIT notice is in `THIRD_PARTY_LICENSES/iOSIconPack-MIT.txt`.

**Lock-screen limitation:** Android's secure lock screen (PIN, pattern, fingerprint, notifications and keyguard) is owned by the operating system. This project provides an iOS-inspired in-launcher lock-screen-style overlay only; it does not replace or bypass Android's real lock screen.


## POCO / MIUI-style Home screen settings and performance

The Home screen settings now include:
- Home screen mode: With App drawer / Without App drawer
- Left-side screen: Google Discover / None (when enabled, the left page opens the installed Google app)
- Home screen search style
- Swipe-down action: App finder / Control Center / None
- Animation speed: Fast / Normal / Slow
- Double-tap empty Home area to show the launcher's lock overlay
- Fill remaining grid cells, long-press to move apps left/right, lock-layout preference, hide labels, and grid presets including 4×7
- System navigation preference: Buttons / Gestures
- Recents arrangement preference and a high-refresh-rate preference

The launcher requests the highest display mode reported by Android when **Prefer highest supported refresh rate** is enabled, and caches fallback app icon bitmaps to reduce repeated work during Compose recomposition. Android may still cap refresh rate because of the phone's battery, thermal, display or per-app policies.

**System-owned features:** The Buttons/Gestures choice opens Android Settings so the user can change the actual system navigation mode. A launcher cannot silently switch Android's navigation mode or draw over the system navigation controls. Similarly, Android owns the Recents screen; the Recents arrangement preference is saved in the launcher but cannot rearrange system Recents. The "Fill cells" preference fills unused cells on the last displayed grid page; it does not retain an app's old slot after uninstalling it.


## Latest launcher interaction fixes

- Swiping down over the Home screen no longer opens App Library. The Control Center opens from a downward swipe at the upper-right edge; choose **None** in Home screen settings to disable that shortcut.
- The App Library's own scroll container is allowed to receive normal vertical swipes, without the Home screen gesture recognizer competing with it.
- Back closes an open overlay (Control Center, App Library or launcher settings). When already on the Home screen, Back is consumed so it does not exit the selected launcher.
- Selecting **Gestures** hides the launcher's navigation bar while the launcher is visible and allows Android to reveal transient bars with an edge swipe. For system-wide gesture navigation, the Android / MIUI navigation mode still has to be changed in device Settings; third-party apps cannot change that protected OS setting.
- Control Center uses open-source Material Design vector icons, a 20%-transparent glass surface, a 20 dp blurred Home screen backdrop on Android 12+, and swipe-up dismissal.
- Long-press and drag an app icon to reorder it anywhere on the visible Home page. Drag it to the red **Uninstall** target at the top to open Android's standard uninstall confirmation for that app. System apps and the launcher's built-in shortcuts cannot be uninstalled this way.


## Control Center and Home grid update

The latest Control Center has been redesigned around the iOS Control Center composition: a combined connectivity cluster, media panel, orientation lock and screen-mirroring controls, vertical brightness and volume sliders, Focus, and circular utility shortcuts. It uses a dark translucent glass overlay over the blurred Home screen. App icon taps no longer show the rectangular Compose ripple highlight. Home grid horizontal swipes are handled by the grid itself, separately from vertical grid scrolling and long-press icon drag.


## Native Android widgets and appearance

- The **Settings → Widgets** row launches Android's native App Widget picker, which lists widget providers installed by the system and third-party apps. Select a widget and it is placed on the currently selected Home page.
- Widgets are hosted with Android `AppWidgetHost` / `AppWidgetHostView`; they remain interactive and are not screenshots or simulated cards. Provider configuration screens are launched when required.
- Widget IDs and page placement are persisted in `launcher_state.db`. The launcher restores them after restart.
- Long-press and drag a hosted widget to the red **Remove** target at the top to remove it. The built-in Weather and Today cards can also be removed in the same way.
- Home pages animate as a single page surface: the first-page Weather/Today cards, hosted widgets, and app grid move together when swiping horizontally. Hosted widgets are added to the page that was active when the picker was opened.
- **Settings → Appearance** supports System, Light, and Dark. System follows Android's current UI mode; explicit Light/Dark choices override it. The Home screen, dock surface and launcher settings adapt to the selected mode.
- Messages and Settings in the dock now use the bundled iOS-style icon artwork rather than generic Material glyphs.

Widget availability, configuration and resize behaviour depend on each installed widget provider and Android/OEM restrictions. The native Android widget picker is used so both built-in and third-party providers can participate.
