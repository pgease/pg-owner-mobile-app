# Backend Handoff — Day 5 Tester Integration Bugfixes (`pg-ease-nest`)

> Per frontend developer workflow rules, no backend files in `pg-ease-nest` were modified. Below are the exact backend changes required so all 14 Day 5 QA integration flows work end-to-end.

## 1. Bug 1 — OTP Resend Server-Side Cooldown
- **Endpoints:** Owner & Tenant send/resend OTP endpoints (`auth` module).
- **Requirement:** Enforce a **30-second cooldown** and rate limit per phone number on send/resend OTP requests.
- **Response on early retry:** Return HTTP `429 Too Many Requests` with `{ message, retryAfterSeconds }` (without leaking whether the phone number is registered).

## 2. Bug 5 — Aadhaar DigiLocker KYC WhatsApp Dispatch
- **Endpoint:** `POST /api/property-owners/properties/:propertyId/tenants/:roomTenantId/request-kyc`
- **Requirement:**
  - Trigger the Meta-approved WhatsApp template with the DigiLocker KYC link when `mode` includes WhatsApp.
  - Return `whatsappSent: boolean` and `messageId?: string` accurately in the response (`Pg` Owner UI now only shows "sent via WhatsApp" when `whatsappSent === true` or `messageId` is present; otherwise it copies the link and warns the owner).
  - **Founder WhatsApp/Meta Note:** If no Meta-approved KYC-link WhatsApp template exists yet, submit one for Meta approval before enabling `whatsappSent: true`.

## 3. Bug 6 — Rental Agreement Status on Dispatch
- **Endpoints:** Owner agreement send/dispatch + `GET /api/tenants/agreements/my-agreements`
- **Requirement:** When an owner sends/dispatches an agreement for signature, transition status from `DRAFT` to `SENT` / `READY_TO_SIGN` (and populate `signUrl` / `signingUrl`).

## 4. Bug 8 — Meal Schedule Validation (`HHmm` 24h)
- **Endpoint:** `PUT /api/property-owners/properties/:propertyId/food-menu` (or meal schedule update).
- **Note:** Frontend `FoodDiningPage.tsx` now sends **only** the selected days and selected meal type (`breakfast` / `lunch` / `dinner`) formatted as `HHmm`. Verify backend accepts partial day/meal updates without requiring unrelated meal slots.

## 5. Bug 11 — Guest Pass Status Sync
- **Endpoints:** `POST /api/tenants/guest-requests`, `GET /api/tenants/guest-requests`, `GET /api/property-owners/properties/:propertyId/guest-requests`
- **Finding:** Schema `guest_requests.status` defaults to `'pending'` and `GuestsPage.tsx` defaults missing status to `'pending'`.
- **Requirement:** Verify staging/production DB & deployed `pg-ease-nest` build do not auto-approve on `POST /tenants/guest-requests`, and that `GET /tenants/guest-requests` returns `'pending'` until the owner calls `PATCH .../guest-requests/:requestId/status`.

## 6. Bug 12 — Night Out "Notify Parent" Endpoint Missing (404)
- **Finding:** `pg-ease-nest/src/property-owners/controllers/property-owners.controller.ts` (lines 3067–3105) only exposes `/status` and `/decision`. It does **not** have:
  `POST /api/property-owners/properties/:propertyId/night-out-requests/:requestId/inform-parent`
- **Requirement:**
  - Add `@Post('properties/:propertyId/night-out-requests/:requestId/inform-parent')` guarded by `PropertyOwnerOrStaffAuthGuard`, `ResolveEffectivePropertyOwnerGuard`, `StaffPermissionGuard`.
  - Look up the tenant's guardian/parent phone (`fatherPhone`, `motherPhone`, `guardianPhone`). If missing, return `400 Bad Request` with a clear message (`"No parent or guardian number is saved for this tenant"`).
  - Send WhatsApp/SMS notification using a Meta-approved template (or submit template to Meta if not yet approved) and persist `parentInformed = true`, `parentInformedAt = new Date()` on the night-out request row.

## 7. Bug 13 — Night Out Status Sync (Approve / Reject Vanishing)
- **Endpoints:** `PATCH /api/property-owners/properties/:propertyId/night-out-requests/:requestId/status`, `GET /api/tenants/night-out-requests`, `GET /api/property-owners/properties/:propertyId/night-out-requests`
- **Requirement:** Ensure approving or rejecting a night-out request updates `status` to `'approved'` / `'rejected'` and **keeps** the record visible in both Owner and Tenant `GET` responses (do not soft-delete or filter out decided requests).

## 8. Bug 14 — Notice Period Tenant Sync
- **Endpoints:** `POST /api/tenants/notice` and `GET /api/property-owners/properties/:propertyId/tenants`
- **Finding:** Local `pg-ease-nest` code already updates `roomTenants.status = 'UNDER_NOTICE'`, `isOnNotice = true`, and returns `notice: { isOnNotice, noticeStartedAt, vacateOn, daysUntilVacate }`. Frontend `normalizeSingleTenant` has also been updated to treat `status === 'UNDER_NOTICE'` as `isOnNotice = true`.
- **Requirement:** Confirm the deployed staging/production backend includes `UNDER_NOTICE` tenants in `GET /api/property-owners/properties/:propertyId/tenants` with `notice.isOnNotice: true`.
