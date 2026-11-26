# PG Ease Polish Round 3 Report (`v1.0.12` Owner / `v1.0.10` Tenant)

## R1–R9 Summary Table

| ID | Issue | Status | Root Cause & Fix |
| --- | --- | --- | --- |
| **R1** | Status bar overlaps header (`14:01`, `57%` over avatar/bell) & dark icons on teal | Fixed | `OwnerShell.java` was calling `EdgeToEdge.enable(...)` without `OnApplyWindowInsetsListener`, while `App.tsx` called `setAppStatusBar(false)` (black icons). Added native Option A `ViewCompat.setOnApplyWindowInsetsListener(swipe, ...)` consuming `systemBars \| displayCutout \| ime`, set `BRAND` background on `content`/`swipe`/`styles.xml`, disabled `SystemBars.insetsHandling` in `capacitor.config.json`, and switched `setAppStatusBar(true)`. |
| **R2** | Blank PG avatar square in header | Fixed | Added `<PgAvatar>` in `MobileHeaders.tsx` rendering `selectedPg.photos[0]` with `onError` fallback to `pgeaseIconColor` on a crisp white tile (`bg-white`). |
| **R3** | Concatenated title (`My Property · Shivam 6 pg`) & non-sticky header | Fixed | Upgraded `MobileAppHeader` and `MobileSubHeader` to a sticky 2-row global header (`sticky top-0 z-30`): Row 1 = `<PgAvatar>` + PG Switcher + Bell; Row 2 = page title + actions (or back arrow + title + actions). |
| **R4** | Rent money card collided with header | Fixed | Removed `-mt-4` overlap on `MobileRentV2.tsx`; added clean `pt-4` (16px) spacing below sticky 2-row header. |
| **R5** | No shared `<SwipeTabs>` | Fixed | Added `<SwipeTabs>` in `Primitives.tsx` (Owner) and `SwipeTabs.tsx` (Tenant) with animated indicator, horizontal swipe, vertical scroll lock, and 28px left-edge back safety. Applied to Tenants, Rent, Gallery, and Tenant Announcements. |
| **R6** | Inline filter chips cluttering screens | Fixed | Added `<FilterButton>` + `<ActiveFilterPills>` + `<BottomSheet>` filters with Apply & Reset on Tenants (`KYC Status`, `Normal/Compact` density) and Rent (`Payment Mode`). |
| **R7** | Dark mode persistence / flash on reload | Fixed | Added `src/utils/theme.ts` (`applyTheme` + `hydrateTheme` reading Capacitor `Preferences` + `localStorage`) and pre-paint `backgroundColor` in `index.html` for both Owner and Tenant apps. |
| **R8** | Tenants FAB overlap & empty state sizing | Fixed | Added `pb-24` clearance to `MobileTenantsV2.tsx` list so `ExtendedFAB` never covers rows; kept in-list `MascotEmptyState` at `size="md"`. |
| **R9 / C10** | Token hygiene & builds | Verified | `check-tokens.mjs` (0 hardcoded hex in 13 mobile TSX files), `verify-inventory.mjs` (53 rows / 49 navRegistry), Vite production builds, and signed release APKs verified. |

## Signed Release APKs Built & Verified

- **Owner App**: `apks/pgease-owner-v1.0.12.apk` (`in.pgease.owner`, `versionCode 13`, `versionName 1.0.12`, signed by `CN=PG Ease, O=PG Ease Solutions`).
- **Tenant App**: `apks/pgease-tenant-v1.0.10.apk` (`in.pgease.tenant`, `versionCode 11`, `versionName 1.0.10`, signed by `CN=PG Ease, O=PG Ease Solutions`).

> **Environment Note**: `emulator` and Android `system-images` are not installed on this machine and `adb devices -l` showed no connected physical device, so on-device APK screenshots were not captured in this session.
