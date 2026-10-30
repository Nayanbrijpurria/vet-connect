# VetConnect roadmap

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
