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

The custom glyph artwork is drawn with the open-source **Material Icons Extended** Compose library and custom color treatments. An MIT-licensed iOS icon pack is available at [SysAdminDoc/iOSIconPack](https://github.com/SysAdminDoc/iOSIconPack) as an optional separate Android icon-pack project; its separate APK artwork is not bundled into this launcher.

**Lock-screen limitation:** Android's secure lock screen (PIN, pattern, fingerprint, notifications and keyguard) is owned by the operating system. This project provides an iOS-inspired in-launcher lock-screen-style overlay only; it does not replace or bypass Android's real lock screen.
