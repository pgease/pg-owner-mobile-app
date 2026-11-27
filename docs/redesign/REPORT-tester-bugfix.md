# Day 5 Tester Integration Bugfix Report

Date: 2026-11-27
Branches: `main` (local commits only; no `git push`)

## Mobile App Versions
- **Owner APK (`pg-owner-mobile-app`)**: `1.0.14` (`versionCode 15`, bumped from `1.0.13` / `14`)
- **Tenant APK (`pg-tenant-mobile-app`)**: `1.0.12` (`versionCode 13`, bumped from `1.0.11` / `12`)

## Summary of Fixes (Bugs 1–14)

| Bug | Area | Frontend / Mobile Fix | Backend Handoff |
| --- | --- | --- | --- |
| **1** | OTP Resend during countdown | Disabled channel-switch resend buttons (`Get OTP via SMS/WhatsApp`) while `!canResend` in `Login.tsx`, `MobileAuthSheet.tsx`, and `LoginPage.tsx`. | Enforce 30s server cooldown (`429` + `retryAfterSeconds`). |
| **2** | Owner Onboarding mobile header | Made `OWNER ONBOARDING` pill, `Support: +91 77019 53356` (`tel:` + WhatsApp), and `English / हिन्दी` toggle visible on mobile with `env(safe-area-inset-top)` padding in `Onboarding.tsx`. | — |
| **3** | Use Current Location | Added `ACCESS_COARSE_LOCATION` & `ACCESS_FINE_LOCATION` to `pg-owner-mobile-app/android/app/src/main/AndroidManifest.xml`; updated `Onboarding.tsx` `handleUseLocation` to check `Capacitor.Plugins.Geolocation` permissions + `navigator.permissions` + structured address fill + clean error copy (no OTP mention). *(Note: `@capacitor/geolocation` npm install failed offline with `EHOSTUNREACH`; install once online if native plugin bridge is desired.)* | — |
| **4** | Toast / Snackbar contrast | Updated `toast.tsx` in both `pg-manager-web-application` and `tenant-web-app` to WCAG AA solid semantic colors: green `#15803D`/white (success), red `#DC2626`/white (error), amber `#F59E0B`/`#111827` (warning), blue `#1D4ED8`/white (info). | — |
| **5** | Aadhaar DigiLocker KYC WhatsApp | Updated `Kyc.tsx` to show "sent via WhatsApp" only when `whatsappSent === true` or `messageId` is returned; otherwise copies link and shows honest warning toast. | Verify WhatsApp dispatch & Meta template in `request-kyc`. |
| **6** | Agreement stuck as Draft | Updated `DocumentsPage.tsx` to recognize `SENT` / `READY_TO_SIGN` / `PENDING_SIGNATURE` / `ESIGN_SENT` / `INITIATED` / `signUrl` as `Pending Signature` with `View & Sign Agreement` CTA. | Ensure backend transitions dispatched agreements out of `DRAFT`. |
| **7** | Blank Pay Rent / Deposit + ₹0 Hero | Fixed `RentPaymentModal.tsx` crash when `dues` was null/undefined (`dues?.period?.label`), and updated `PaymentsPage.tsx` hero to fall back to itemized pending total when `summary.balanceDue` is 0. | — |
| **8** | Meal Schedule `HHmm` error | Updated `FoodDiningPage.tsx` `handleApplyBulkTimings` to send only `selectedDays` and `[bulkMealType]` formatted as `HHmm`, with friendly error toast. | Verify partial meal schedule updates. |
| **9** | Wi-Fi edit & multi-network | Updated `WifiManagementPage.tsx` (`handleOpenGeneralAdd` selects first unconfigured floor instead of overwriting Floor 1, `speedMbps` coerced to number, added delete button) and `WifiPage.tsx` (renders all `propertyDetails.wifi.networks`). | — |
| **10** | Tenant Complaints immediate list | Updated `ComplaintsPage.tsx` to optimistically prepend created complaint and `await loadComplaints()`. | — |
| **11** | Guest Pass status sync | Verified `GuestsPage.tsx` defaults missing status to `'pending'` (`req.status || 'pending'`). | Verify staging/prod `guest-requests` returns `'pending'`. |
| **12** | Night Out Notify Parent 404 | Updated `NightOutRequestsPage.tsx` to validate guardian phone first and replace raw URL error toast with friendly message. | Add `POST .../night-out-requests/:id/inform-parent` in `pg-ease-nest`. |
| **13** | Night Out status tabs & sync | Added `Pending`, `Approved`, `Rejected` filter tabs & counts alongside movement tabs in `NightOutRequestsPage.tsx`. | Ensure decided requests stay visible in `GET` endpoints. |
| **14** | Notice Period missing in Owner | Updated `normalizeSingleTenant` in `propertyOwner.ts` to also treat `status === 'UNDER_NOTICE'` as `isOnNotice = true`. | Verify deployed `GET .../tenants` includes `UNDER_NOTICE`. |

See [BACKEND-TESTER-BUGFIX-HANDOFF.md](./BACKEND-TESTER-BUGFIX-HANDOFF.md) for backend action items.
