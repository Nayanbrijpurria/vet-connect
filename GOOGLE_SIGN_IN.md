# Google Sign-In for VetConnect

## Current implementation

Google Identity Services provides an ID token in the browser. VetConnect sends that assertion to the backend, which verifies its signature, issuer, audience, expiry, subject, and verified email using Google's Java token verifier. Google's token is never accepted as a VetConnect API token.

The backend then issues the same 15-minute VetConnect access JWT and 7-day rotating refresh session used by password login. The access token stays in JavaScript memory. The refresh token is returned only as the existing HttpOnly cookie; PostgreSQL stores only the existing refresh-token hash. No Google ID token is stored in the browser after the request or in PostgreSQL.

Google authentication is limited to active CUSTOMER accounts. An unlinked Google identity creates a CUSTOMER only when its verified email is not already attached to any VetConnect account. A matching email never links accounts automatically. Existing customers sign in with their current VetConnect method, then link Google from the customer dashboard. Linking requires the authenticated customer session, a valid Google assertion, the trusted Origin and CSRF header, and the current VetConnect password. VET and ADMIN accounts cannot sign in or link through Google.

V11 adds `auth_identities` with a provider and Google's stable `sub` value, unique provider/subject and user/provider indexes, and a user foreign key. The migration does not alter `users` or existing password records. Google-only users receive a BCrypt hash derived from a generated 256-bit random value that is never returned or stored in plaintext. This preserves the existing `password_hash NOT NULL` constraint and keeps password authentication unable to authenticate those accounts.

## Endpoints

- `GET /api/auth/google/config` returns only `{ "clientId": "..." }`. When Google is not configured it returns an empty client ID with HTTP 503.
- `POST /api/auth/google` accepts `{ "credential": "<Google ID token>" }`, and requires the exact allowlisted Origin and `X-VetConnect-CSRF: 1`. A successful response keeps the existing `AuthResponse` fields and sets the existing refresh cookie.
- `POST /api/auth/google/link` accepts `{ "credential": "<Google ID token>", "currentPassword": "..." }`. It requires an active CUSTOMER access token, exact allowlisted Origin, CSRF header, and password re-verification.

Existing password login, customer/veterinarian registration, administrator login, JWT validation, refresh rotation, logout, and role checks are unchanged.

## Google Cloud setup

1. Create an OAuth client with application type **Web application** in Google Cloud Console.
2. Add each exact frontend origin to **Authorized JavaScript origins**. The currently configured production frontend origin is `https://vet-connect-frontend-reemltg48.vercel.app`. Add only the local development origins you use; the current defaults include `http://localhost:5500`, `http://127.0.0.1:5500`, `http://localhost:63342`, and `http://127.0.0.1:63342`.
3. If production uses a custom frontend domain, authorize that exact origin instead of assuming the Vercel default hostname is the final site.
4. Copy the Web Client ID to the backend setting below. Do not create, copy, or expose a client secret for this GIS ID-token flow.

## Deployment configuration

Set these values through the deployment provider, not in Git:

| Provider | Variable | Value |
| --- | --- | --- |
| Railway backend | `GOOGLE_CLIENT_ID` | The Google Web Client ID from the OAuth client |
| Railway backend | `CORS_ALLOWED_ORIGINS` | Comma-separated exact frontend origins; no wildcard |
| Railway backend | `APP_ENV` | `production` |
| Railway backend | `JWT_SECRET` | Existing strong secret, at least 32 bytes; do not replace it as part of this feature |
| Railway backend | `DB_URL`, `DB_USER`, `DB_PASSWORD` | Existing database settings; do not replace them as part of this feature |
| Vercel frontend | None new | GIS reads the public client ID from the backend config endpoint |

In production, `frontend/js/config.js` now uses relative API URLs. The browser calls the Vercel frontend origin and `frontend/vercel.json` rewrites `/api/:path*` to the existing Railway API. The rewrite disables Vercel caching for `/api/*` and sends `Cache-Control: no-store`. Localhost frontend servers use the local Ktor server at `http://localhost:8080` (or `127.0.0.1:8080`). The Railway service remains directly accessible for operations. Do not put a Google client secret or JWT/database secret in Vercel JavaScript.

Before enabling the feature, confirm the frontend origin appears exactly in Railway's `CORS_ALLOWED_ORIGINS` and in Google's authorized JavaScript origins. Add any custom frontend origin explicitly. Never enable wildcard CORS with credentials.

## Cookie domain requirement

The current Vercel and Railway default domains are cross-site. The proxy is intended to make the browser receive the refresh-cookie response from the Vercel frontend origin, but forwarding of `Set-Cookie` has not yet been verified. The backend still sets the existing host-only cookie with `HttpOnly`, production `Secure`, and path `/api/auth`; it currently retains `SameSite=None`. Do not change it to `Lax` until a Vercel Preview and real browser confirm the original Origin, CSRF header, `Set-Cookie`, and refresh behavior.

Do not work around browser cookie restrictions by exposing refresh tokens to JavaScript or putting them in web storage. The Vercel rewrite is a proxy, not an authorization boundary; Railway authorization, JWT validation, CSRF, and Origin checks remain enabled. Keep the exact frontend origin in `CORS_ALLOWED_ORIGINS` because the backend uses it for cookie-request Origin validation. Never use wildcard origins with credentials.

## Local verification

1. Configure a development Google Web Client ID in the local backend environment as `GOOGLE_CLIENT_ID`.
2. Serve the frontend from an origin listed in both Google Cloud and `CORS_ALLOWED_ORIGINS`. Local `localhost` and `127.0.0.1` frontends call the local Ktor server on port 8080; run the backend with `APP_ENV=development` for the existing local cookie behavior.
3. Do not use a Vercel Preview pointed at the production Railway API for login, registration, Google account creation/linking, logout, refresh, or other state-changing authentication checks. Point an isolated Preview at a separate staging backend and PostgreSQL database first.
4. Production cookies require HTTPS.
5. In staging, verify a new customer, an existing password customer after explicit linking, logout, and page reload/session refresh. Confirm VET and ADMIN identities are rejected.

Automated verification completed: `gradlew test build` passed 20 tests with no failures; `node --test frontend/tests/*.test.mjs` passed 7 tests with no failures; `git diff --check` reported no whitespace errors. Coverage includes Google account policy, explicit linking/password checks, malformed credentials and missing configuration, origin/CSRF rejection, public configuration response shape, existing refresh/API-client behavior, and frontend submission/storage behavior. Tests did not contact Google or PostgreSQL. A signed-token verifier integration, migration/transaction/race behavior, and Railway/Vercel cookie flow still require deployment-like verification.

## Deployment and rollback

Do not deploy or run production migrations until explicitly approved. V11 is forward-only and preserves existing rows. The application runs Flyway during startup, so a backend release that includes V11 will apply it automatically when that release starts.

For a safe Preview test, first confirm that the Vercel project's Root Directory is `frontend`. Use an isolated Preview project/deployment whose `/api/*` rewrite targets the staging backend and whose staging database contains only test data; the production rewrite in this branch targets production Railway. Allowlist that exact Preview Origin in staging only. Verify the browser request remains on the Preview origin, the backend receives Origin, Authorization and CSRF headers, query strings/methods/bodies pass through, and login/refresh `Set-Cookie` is stored for the Preview host. Confirm refresh after reload, logout revocation, and `Cache-Control: no-store` plus no Vercel cache hit. Do not run these state-changing checks against production.

For production rollout, retain the Railway backend and production database, configure the exact production Vercel Origin in Railway's existing CORS allowlist, and deploy the Vercel frontend with the existing production Railway rewrite target. A user's existing host-only Railway refresh cookie will not be sent to Vercel; expect one sign-in after rollout. The old Railway cookie may remain until it expires, but the proxy must create and clear its new host-only cookie under the Vercel host. No Railway variable or database migration is required for the proxy.

To roll back the proxy, restore the previous Vercel frontend deployment. The Railway backend remains available and no database change is involved. The previous frontend sends requests directly to Railway, so sessions created through the proxy are scoped to the Vercel host and users may need to sign in again after rollback. Do not disable the Railway backend or delete either host's cookies manually. Google Sign-In and its V11 data remain unchanged by a proxy rollback.

To disable Google Sign-In separately, clear or omit `GOOGLE_CLIENT_ID` and deploy a compatible application version. Do not drop `auth_identities`, delete Google-linked users, or reverse V11. Existing Google-only customers can sign in again when the feature is restored; their data remains intact. Password hashes for these accounts are intentionally unusable and are not a recovery credential.
