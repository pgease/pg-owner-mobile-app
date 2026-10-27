# 📱 PG Ease Owner — Mobile Application Wrapper

This folder (`pg-owner-mobile-app`) wraps the mobile view of the **PG Ease Owner Web Application** into native Android and iOS apps using **Capacitor**.

---

## 🏗️ Project Architecture

```
/Users/vikaskuntal/Desktop/pgease-nest/
├── pg-owner-web-application/   # Source Vite + React web application
└── pg-owner-mobile-app/        # Native mobile application wrapper
    ├── capacitor.config.json   # Capacitor configuration (App ID, server mode, plugins)
    ├── .env                    # Environment configuration (Live URL vs Local Bundled)
    ├── dist/                   # Production web build bundled inside native binaries
    ├── android/                # Native Android Studio project (Gradle, Java/Kotlin)
    ├── ios/                    # Native iOS Xcode project
    ├── resources/              # App icons and splash screen graphics
    └── scripts/                # Sync and mode-switching automation scripts
```

---

## ⚡ Quick Start

### 1. Synchronize Web Build to Native Platforms
Whenever you make changes in `pg-owner-web-application`, sync them to the mobile app with:

```bash
# Sync web build to Android:
npm run sync:android

# Or sync to both Android and iOS:
npm run sync:all
```

---

## 🌐 Dual-Mode Loading (Live vs Local Bundled)

You can easily switch how the mobile app loads:

### Mode A: Local Bundled (Default / Offline First)
The mobile app directly runs the built web assets packaged inside the APK/IPA binary.
Fastest launch, works completely offline.

```bash
npm run mode:local
npm run sync:android
```

### Mode B: Live URL (Instant Updates)
The mobile app opens your deployed website (e.g. `https://owner.pgease.in` or local dev IP `http://192.168.1.x:5173`).
Any updates you deploy to your web app appear immediately in the mobile app without requiring users to update via the Play Store.

```bash
# Point to production:
npm run mode:live -- https://owner.pgease.in
npm run sync:android

# Or point to local Vite dev server on your WiFi network:
npm run mode:live -- http://192.168.1.15:5173
npm run sync:android
```

---

## 📱 Running on Devices / Emulators

### Android (Android Studio):
Open the project in Android Studio:
```bash
npm run open:android
```
- Click **Run** (green triangle) to launch on an emulator or connected USB device.
- Or use **Build > Build Bundle(s) / APK(s) > Build APK(s)** to generate a test APK.

### iOS (Xcode):
Open the project in Xcode:
```bash
npm run open:ios
```
*(Requires Xcode installed on macOS)*

---

## 🛠️ Available NPM Scripts

| Command | Description |
|---|---|
| `npm run sync:android` | Builds web app & syncs assets/plugins to Android project |
| `npm run sync:ios` | Builds web app & syncs assets/plugins to iOS project |
| `npm run sync:all` | Builds web app & syncs to both Android and iOS |
| `npm run open:android` | Opens native Android project in Android Studio |
| `npm run open:ios` | Opens native iOS project in Xcode |
| `npm run mode:local` | Switches app to load local bundled `dist/` |
| `npm run mode:live -- <url>` | Switches app to load live URL |
| `npm run build:apk` | Compiles debug APK via Gradle CLI |
| `npm run clean:android` | Cleans Android Gradle build cache |

---

## 🧩 Native Features Configured

- **App ID**: `in.pgease.owner`
- **App Name**: `PG Ease Owner`
- **Safe Area Insets**: Notch and home-bar padding automatically applied (`viewport-fit=cover`).
- **Android Back Button**: Navigates backwards through browser history, or exits if on root dashboard/login.
- **Status Bar**: Clean light status bar with native dark icons.
- **Splash Screen**: Seamless launch splash screen that auto-dismisses once app loads.
- **Keyboard Handling**: Auto-resizes view when keyboard opens to prevent input occlusion.
