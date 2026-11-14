# VetConnect progress

## Google Sign-In

**Status:** Implemented on `feature/google-oauth`. Production configuration, migration execution, and live Google/browser verification remain pending approval.

- Added Google Identity Services ID-token verification through Google's Java verifier. Google credentials are exchanged for VetConnect's existing access JWT and refresh-cookie session.
- Google access is CUSTOMER-only. New accounts are created only when no VetConnect account uses the verified email. Existing accounts must authenticate normally and explicitly link Google; linking checks the current password and CSRF/Origin.
- Added the additive V11 `auth_identities` migration. It does not change existing user records or `password_hash NOT NULL`; Google-only users receive an undisclosed random-secret BCrypt hash for compatibility with existing releases.
- Added the Vercel `/api/*` proxy and relative production API URLs; local frontend servers continue to use the local Ktor server. The existing cookie SameSite setting remains unchanged pending real proxy/browser verification.
- Added the client-ID-only config endpoint, login button, customer dashboard link flow, policy tests, origin/CSRF route tests, and frontend tests.
- Verification: `gradlew test build` passed (20 tests, 0 failures); `node --test frontend/tests/*.test.mjs` passed (7 tests, 0 failures); `git diff --check` found no whitespace errors.
- Required before production: configure Railway `GOOGLE_CLIENT_ID` and exact `CORS_ALLOWED_ORIGINS` plus Google authorized origins; verify forwarded Origin, CSRF, and Set-Cookie behavior and refresh persistence in a Vercel Preview using a separate staging backend/database. Keep `SameSite=None` until browser verification passes; approve any cookie change separately.
- Real signed Google token acceptance, PostgreSQL migration/transaction/race behavior, and deployed browser cookie behavior still need integration verification. No local `DB_URL` or `GOOGLE_CLIENT_ID` was configured, so those live integrations were not executed.

## Pet-wise medical history

**Status:** Implemented in the current working tree; database/runtime verification is pending.

### Implemented in source

- Each medical-history response is scoped to one pet ID and includes the pet profile plus consultation, prescription, observation, allergy, condition, vaccination, and document records.
- Customers access history through an owner-scoped endpoint. Veterinarians access history through an appointment-scoped endpoint for an assigned, approved veterinarian and a confirmed or completed appointment.
- Consultation creation and health-record writes derive the pet and veterinarian from the authorized appointment or consultation. Prescription items are stored as separate rows.
- Consultation amendments and voided vaccination corrections preserve the original clinical record.
- Pet archival retains the history. The customer history page now includes archived pets in its selector and disables document uploads for archived pets.
- History access and clinical changes are written to the existing audit log.

### Verification

- `gradlew test build`: successful; Gradle reported tasks up to date on the first run.
- A forced test/build rerun and frontend syntax checks are still required.
- PostgreSQL migration and integration tests have not been run. `V9__pet_wise_medical_history.sql` is present in the working tree, but no database connection configuration or `psql` executable was available during the audit.
- Current authorization tests cover unauthenticated history requests. Database-backed tests for ownership, veterinarian assignment, multiple-pet isolation, migration behavior, and concurrent writes remain required.

### Operational follow-up

- Run V9 on a clean database and a representative database copy before deployment.
- Establish private, durable, backed-up document storage for production; local filesystem storage is currently used by the application.
- Add database-backed authorization and pet-isolation integration coverage before treating this feature as production-verified.
