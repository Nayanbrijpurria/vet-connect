# VetConnect roadmap

## Google Sign-In

- [x] Verify Google ID tokens on the backend and issue VetConnect's existing tokens.
- [x] Restrict Google authentication to active CUSTOMER accounts; prevent email-only account linking.
- [x] Add explicit customer linking with session, Origin/CSRF, and current-password checks.
- [x] Add additive V11 identity storage and frontend GIS controls.
- [x] Add a Vercel same-origin `/api/*` proxy and keep local development pointed at local Ktor.
- [x] Add backend policy/route tests and frontend flow tests.
- [ ] Configure Google Web Client origins and Railway `GOOGLE_CLIENT_ID`/exact CORS origins.
- [ ] Verify proxy header/cookie forwarding, refresh persistence, and API no-store behavior in Vercel Preview with staging data; only then consider changing production SameSite to Lax.
- [ ] After explicit approval, deploy and verify the V11 migration, real Google tokens, and production browser session persistence.

## Pet-wise medical history

### Current milestone: core implementation in source

- [x] Reuse pets, appointments, consultations, prescriptions, documents, observations, and audit logs.
- [x] Add structured pet allergies, conditions, vaccinations, and append-only consultation amendments.
- [x] Enforce appointment customer-to-pet ownership in PostgreSQL through the V9 migration.
- [x] Expose customer-owned and veterinarian-assigned history reads.
- [x] Add customer and veterinarian dashboard history flows.
- [x] Preserve pet history when a pet is archived and make archived history reachable from the customer history page.
- [ ] Apply and validate V9 against clean and representative PostgreSQL databases.
- [ ] Add database-backed tests for ownership, pet isolation, vet appointment scope, writes, and concurrent requests.
- [ ] Complete frontend and API regression checks with a configured application environment.

### Production readiness follow-up

- [ ] Move medical document files to private durable object storage with backup and retention controls before multi-instance production deployment.
- [ ] Review audit-log retention, operational access, and monitoring for sensitive record reads and changes.
- [ ] Measure history endpoint query latency and connection-pool usage with realistic record volumes; add pagination if observed data growth requires it.
