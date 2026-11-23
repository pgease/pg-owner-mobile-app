# PG Ease Owner Android shell audit

This document describes the shell as it existed on `main`, what was missing for a Play launch, and what this branch changes. The web app (`pgease/pg-owner`, served at `https://app.pgease.com`) is out of scope. Its mobile UI is being polished separately.

## How the shell worked

The project is a **Capacitor** wrapper, not a Trusted Web Activity, a hand-rolled WebView activity, or Flutter.

| Piece | Before this branch |
| --- | --- |
| Runtime | Capacitor 6.2 (`@capacitor/android` 6) |
| Entry | `MainActivity` extends `BridgeActivity` |
| Content | Remote URL in `capacitor.config.json`: `https://app.pgease.com?app=1` |
| Identity | `applicationId` / namespace `in.pgease.owner`, label **PG Ease Owner** |
| Chrome | Capacitor's WebView. There is no address bar in the activity layout. |
| Plugins | App, Browser, Haptics, Keyboard, Network, Splash Screen, Status Bar |
| Onboarding | Three local slides, shown once, stored in `SharedPreferences` |
| Signing | No `signingConfig`. Release builds were unsigned. |
| SDK | `minSdk` 22, `compileSdk` / `targetSdk` 34 |
| Gradle | Android Gradle Plugin 8.2.1, Gradle 8.2.1 |

`server.url` means the APK does not ship the React bundle. `npm run sync:android` only copies a fallback `dist/` for the case when that URL is removed. The live site is what owners see.

The web app already treats this process as native when any of these are true (`src/lib/appShell.ts` in `pg-owner`):

- `window.Capacitor.isNativePlatform()`
- the `PGEaseApp` user-agent token (`appendUserAgent`)
- `?app=1` (also remembered in `localStorage`)

Easebuzz KYC, when that flag is on, calls `@capacitor/browser` `Browser.open` and refreshes when `browserFinished` fires (`PaymentKycModal.tsx`). Easebuzz checkout and Digio eSign run inside the page (iframe / Digio SDK with `is_iframe: true`), not as a separate browser.

## What was broken or missing

1. **Play target SDK.** As of 31 August 2026, new apps and updates must target Android 16 (API 36). The Nov 1 2026 extension has passed. This project targeted API 34. Capacitor 6 only supports API 34; Capacitor 8 is the line that targets API 36.
2. **16 KB page size.** Play requires 16 KB compatibility for apps targeting API 35+. The old AGP (8.2.1) does not zip-align native libraries on a 16 KB boundary. This app does not compile its own NDK code; packaging still has to be correct for any `.so` a plugin ships.
3. **Back button was not implemented in the shell.** The README described history-back and exit-at-root, but `MainActivity` did not handle back. The only handler lived in `scripts/sync-web-build.js`, which edits the *bundled* `index.html`. Live mode never loads that file, so the remote app did not get it. Hardware back fell through to Capacitor's default.
4. **Modals.** Radix dialogs, Vaul drawers, and the Easebuzz sandbox overlay are DOM, not Android dialogs. Nothing asked the page to close them.
5. **Status bar was white, not brand.** `StatusBar.backgroundColor` was `#FFFFFF` with dark icons. Brand teal is `#008080` (`theme-color` on the live site). The splash stays `#005045` with the existing artwork.
6. **Safe area on Android 15+.** Targeting API 35 or 36 forces edge-to-edge. The old shell did not pad the WebView for the status bar, navigation bar, cutout, or IME. The safe-area CSS in `sync-web-build.js` also never reaches the live site.
7. **File and camera upload was unreliable.** Capacitor's file chooser writes camera captures under `getExternalFilesDir(Pictures)`, but `file_paths.xml` had no `external-files-path`, so `FileProvider` rejected that URI and capture fell back to the picker. `CAMERA` was not in the manifest, so an in-page `getUserMedia` (Digio / KYC) could not be granted. There was no camera option on a normal `<input type="file" accept="image/*">`.
8. **External links.** Capacitor's default `launchIntent` sends every non-app host to `ACTION_VIEW`, including `intent:` URIs that need `Intent.parseUri`. `upi://`, `tel:`, `mailto:`, and `https://wa.me` were not given a dedicated path, and payment iframes could be kicked out of the WebView because the check did not look at `isForMainFrame()`.
9. **Gateway return.** Easebuzz, Razorpay, and Digio hosts were not allow-listed. A top-level redirect to a bank 3-D Secure page would leave the app, and the return URL would stay in the browser unless Digital Asset Links were already verified (they are not).
10. **Downloads.** No `DownloadListener`. Blob and `Content-Disposition` downloads (CSV registers, agreement PDFs opened with `window.open`) had no path into the Downloads folder. `window.open` was ignored because multiple windows were not enabled.
11. **Errors.** A failed main-frame load showed the WebView's default error page.
12. **Cookies.** The WebView cookie store is persistent, but nothing called `CookieManager.flush()` on pause, so a kill during the async write could drop the session. Third-party cookies (checkout iframes) were not turned on explicitly.
13. **Deep links.** No App Link intent filters. `https://app.pgease.com/.well-known/assetlinks.json` currently returns the SPA `index.html` because the file does not exist and `vercel.json` rewrites unknown paths to `index.html`.
14. **Push.** The owner web app does **not** use FCM, the Web Push API, or a service worker. `useOwnerNotifications` builds an in-app list from rent, complaints, guests, and night-out queries and stores read state in `localStorage`. Firebase in `pg-owner` is Auth + Firestore for project `pgease-web` (sender id `1057628082734`). There is no `google-services.json` here. Adding a push SDK without a token the web app registers would only add a Play Data safety question.
15. **Host mismatch.** `app.pgease.in` does not resolve in DNS. The live owner app is `https://app.pgease.com` (Vercel, title "PG Ease | Owner's Smart PG Management"). The shell URL was already correct and is unchanged.
16. **`minSdk` 22** is below Capacitor 8, which requires 24 (Android 7.0).

Icon, name, splash drawables, `applicationId`, and the absence of a release keystore were left as they were.

## What this branch changes

- Capacitor **8.6.2** (Android and the shared packages). Official target is API 36.
- `minSdk` 24, `compileSdk` / `targetSdk` **36**, AGP **8.13.0**, Gradle **8.14.3**.
- `packaging.jniLibs.useLegacyPackaging = false` so shared libraries are stored uncompressed and zip-aligned for 16 KB pages. This project does not ship its own `.so` files; the check after a release build is recorded below.
- Native shell in `OwnerShell` (the WebView is still Capacitor's):
  - **Back:** close the in-app popup, then run a small script that removes the Easebuzz sandbox overlay or sends Escape to an open Radix/Vaul dialog. If the dialog is still open, fall through. Then `WebView.goBack()`. At the root, a second back within 2 seconds exits. During onboarding, back moves to the previous slide and does not exit.
  - **Pull to refresh** only when the window and the touched scroll container are at the top, and not while a dialog is open.
  - **File inputs:** documents use the system picker (no broad photo permission). Image inputs also offer the camera. `capture="user"` still opens the camera directly. `FileProvider` now includes `external-files-path`. `CAMERA` is declared but not required, and is requested when the page asks for `getUserMedia` or a capture input.
  - **External links:** `tel:` opens the dialer (does not place the call), `mailto:` and `sms:` open their apps, `upi://` / `whatsapp://` / `intent://` open the matching app, `wa.me` and `api.whatsapp.com` open via `ACTION_VIEW`. Other https links open in a Custom Tab with a `#008080` toolbar. `intent:` URIs clear the explicit component before launch.
  - **Gateways** (Easebuzz, Razorpay, Digio, Cashfree, PayU, BillDesk, CCAvenue, Stripe, Juspay, PhonePe, and common 3-D Secure hosts, plus the Easebuzz static S3 host) stay in the WebView. Iframes are not sent to the browser. Once a gateway frame or page has loaded, later top-level https navigations (bank ACS pages) stay in the app until the owner is back on `app.pgease.com` / `app.pgease.in`. `window.open` of a gateway URL is an in-app page with a Close control; a navigation back to our host loads in the main WebView and closes the popup. Capacitor Browser remains for the KYC flow the web app already calls.
  - **Downloads:** http(s) attachments go through `DownloadManager` with the WebView cookie and user agent. `blob:` and `data:` URLs are written to Downloads (MediaStore on API 29+). `.pdf` navigations download instead of replacing the page. The first download asks for `POST_NOTIFICATIONS` on Android 13+ so the completion notification can show. The file is still saved if the owner denies it.
  - **Offline:** main-frame network failures and SSL failures show a teal panel with Try again. `ERR_ABORTED` (a navigation we cancelled) does not.
  - **System bars:** edge-to-edge, light icons, `#008080` behind the status and navigation bars. The WebView is padded by system bars, cutout, and the IME so content and the keyboard do not overlap. Capacitor System Bars inset injection is disabled so it does not pad twice.
  - **Cookies:** accept cookies and third-party cookies, and `CookieManager.flush()` in `onPause`. DOM storage stays enabled. Nothing clears cookies on launch.
  - **App Links:** `https://app.pgease.com` and `https://app.pgease.in` are `autoVerify` intent filters (`singleTask` was already set). A verified link is loaded in the WebView with `app=1` added when that query is absent. `app=0` is left alone.
- Splash drawables, splash background `#005045`, launcher icons, app name, and `applicationId` are unchanged.
- No release `signingConfig` was added. The test APK attached to the PR is a release-mode build signed with the debug key **outside** Gradle.
- iOS deployment target is 15, which Capacitor 8 requires, and the Browser pod is listed. iOS was not built here (no Xcode). Run `npx cap sync ios` on a Mac with Xcode 26 before shipping iOS.

`NavigationPolicy` is plain Java and is covered by `NavigationPolicyTest`.

## Push notifications (not implemented)

The web app has nowhere to receive a push token. Do not add Firebase to this shell until the web app can register one.

When that work starts:

1. In Firebase project **pgease-web** (sender `1057628082734`), add an Android app with package `in.pgease.owner`.
2. Download `google-services.json` into `android/app/`. `android/app/build.gradle` already applies the Google Services plugin when that file is present. Do not commit a file from a different Firebase project.
3. Add `@capacitor/push-notifications` at the same major version as this shell (8.x) and `npx cap sync android`.
4. In `pg-owner`, call `PushNotifications.register()`, send the token to the backend, and handle the click by routing inside the SPA. Bump the web app's `@capacitor/core` and `@capacitor/browser` to 8.x so they match this shell. Capacitor 6 JS can still see `androidBridge`, but matching majors is the supported setup.
5. The backend sends FCM to that token. Web Push / service workers do not run inside this WebView.
6. `POST_NOTIFICATIONS` is already requested the first time a file download wants a completion notification. A later push registration can request it again; Android only shows the dialog until the owner answers.

## Digital Asset Links (founder)

App Links do nothing from Chrome until each host serves a real `assetlinks.json`. Verification is per host. The two intent filters are separate so a failure on `app.pgease.in` does not void `app.pgease.com`.

`app.pgease.in` has **no DNS record** today. Links on that host cannot verify until it points at the owner app.

`https://app.pgease.com/.well-known/assetlinks.json` currently returns `index.html` (Vercel SPA rewrite). Put the file in the web app at `public/.well-known/assetlinks.json`. Vercel serves files in `public/` before the rewrite. The response must be `Content-Type: application/json` and HTTP 200, with no redirect.

Package name: `in.pgease.owner`.

```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "in.pgease.owner",
      "sha256_cert_fingerprints": [
        "PASTE_PLAY_APP_SIGNING_SHA256",
        "PASTE_UPLOAD_KEY_SHA256"
      ]
    }
  }
]
```

Fingerprints come from Play Console → App integrity → App signing (the **app signing** certificate and the **upload** certificate). Colon-separated uppercase SHA-256. The debug key used for the test APK is not the Play key. After Play App Signing is enabled, run:

```bash
adb shell pm get-app-links in.pgease.owner
```

`verified` for `app.pgease.com` means Chrome will open `https://app.pgease.in` only after that host exists and serves the same file.

Statement list tester: `https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://app.pgease.com&relation=delegate_permission/common.handle_all_urls`

## What the founder still has to do in Play Console

1. Create the app with package **`in.pgease.owner`**. Do not change the application id after the first upload.
2. Enroll in **Play App Signing**. Generate an upload keystore on a machine you control. This repo has no keystore and must not gain one. Do not upload the debug-signed test APK to production.
3. Bump `versionCode` above 1 before the first production upload if a previous artifact already used 1. `versionName` is still `1.0`.
4. Publish the asset links file above, then request link verification (or upload a build and check App links in Play Console).
5. Data safety: the app loads the owner site (account session in cookies), can use the **camera** when the owner takes a photo or a KYC page calls `getUserMedia`, and can post a **download notification**. It does not request photo-library or storage permissions. File picking uses the system picker. `tel:` uses the dialer and does not call by itself.
6. Store listing needs a privacy policy URL. The shell does not add analytics.
7. Target API is 36, which meets the current phone/tablet requirement. 16 KB packaging is enabled; confirm Play Console shows no native-library warning on the AAB you upload (`bundleRelease`, signed with the upload key).
8. `minSdk` 24 drops Android 5.1–6.0. That is required by Capacitor 8.
9. `server.cleartext` remains `true` so `npm run mode:live -- http://192.168.x.x:5173` still works for development. Production traffic is `https://app.pgease.com`.
10. Push, if wanted later, follows the section above. Do not ship a `google-services.json` from another Firebase project.
11. iOS: `npx cap sync ios` on Xcode 26. This branch only raises the deployment target to 15 and adds the Browser pod.

## Verification notes

Checked on an Android 16 (API 36) emulator against `https://app.pgease.com`. The session only opened the public sign-in screen. No account was created and no production record was edited.

- Release APK builds. `zipalign -c -P 16` passes. The APK contains no `.so` files, so there is no 4 KB-aligned native library to fix.
- `NavigationPolicyTest` passes.
- The sign-in screen loads with no address bar and no Android title. Status and navigation bars are `#008080`. The WebView starts below the status bar (128 px on this 420 dpi image, matching the display cutout).
- The first back press on that screen shows "Press back again to exit" and leaves the app open.
- With the radio off, the shell shows "Couldn't open PG Ease" and Try again, covering the WebView's `net::ERR_INTERNET_DISCONNECTED` page. After the radio is on, Try again loads the sign-in screen again.
- `adb shell am start -a android.intent.action.VIEW -d https://app.pgease.com/login` brings the app to the front. Chrome will not do that until `assetlinks.json` is hosted; see above.

The splash theme was creating an AppCompat title bar ("PG Ease Owner") and inflating the top inset by the action-bar height. `MainActivity` now applies `AppTheme.NoActionBar` before `super.onCreate` and drops any leftover action-bar container. Splash artwork and the `#005045` splash color are unchanged. Capacitor's splash plugin still covers launch.
