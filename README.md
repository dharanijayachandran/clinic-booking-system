# Clinic Appointment Booking System

A full-stack clinic booking system built to demonstrate production-level
backend engineering — the core problem is **exactly one of two
simultaneous booking attempts for the same slot may succeed**, solved at
the database level rather than guessed at in application code.

## Stack

- **Backend:** Java 17, Spring Boot 3, PostgreSQL, Spring Security + JWT
- **Frontend:** Angular 18, standalone components, Signals, Angular Material
- **Real-time:** WebSocket (STOMP)
- **Everything runs with:** `docker compose up`

## Status

Built incrementally, phase by phase, each reviewed before moving on:

- [x] **Phase 1** — scaffold, Docker Compose, database schema
- [ ] Phase 2 — auth (register, login, JWT, roles)
- [ ] Phase 3 — booking API with concurrency handling + concurrency test
- [ ] Phase 4 — Angular shell, routing, auth guards
- [ ] Phase 5 — calendar UI and booking flow
- [ ] Phase 6 — WebSocket live availability
- [ ] Phase 7 — reschedule, cancel, doctor and admin views
- [ ] Phase 8 — this README's full architecture write-up

## Running it

```bash
docker compose up
```

- Backend: http://localhost:8080
- Postgres: localhost:5432 (db `clinic`, user/password `clinic` — dev only)

## Architecture decisions

### The double-booking race — the core problem

Two `POST /bookings` requests can arrive for the same slot within
milliseconds. A naive "check availability, then insert" has a classic
TOCTOU race: both requests can read `AVAILABLE` before either has
written anything, and nothing stops both from inserting.

**Chosen approach:** pessimistic row-level locking (`SELECT ... FOR
UPDATE`, via JPA's `@Lock(PESSIMISTIC_WRITE)`) inside a short
`@Transactional` booking method, backed by a partial `UNIQUE` index on
`appointments(slot_id) WHERE status = 'CONFIRMED'` as a database-enforced
safety net independent of the application code path.

Why pessimistic over optimistic locking: this is *high contention on a
single row* (many patients, one popular slot), not optimistic locking's
sweet spot (rare conflicts over a longer window). Pessimistic locking
makes the second transaction *wait*, then see the committed truth and
get a clean rejection — instead of racing ahead and failing after
already doing the work.

Full write-up with the concurrency test and benchmarks lands in Phase 3.

### Schema (Phase 1)

- **`users`** holds shared auth/identity for all three roles (they share
  a login flow); **`doctors`** is a 1:1 extension table for doctor-only
  attributes, so patients and admins don't carry nullable specialty/bio
  columns.
- **`slots` are pre-generated rows**, not computed on the fly from
  working hours minus bookings. This is what makes row-level locking
  possible (there has to be a concrete row to lock) and makes live
  availability simple (broadcast "slot X changed status").
- **`appointments` is separate from `slots`** — a slot is "a bookable
  unit of time," an appointment is "a booking event." Keeping them apart
  preserves history: a cancelled slot can reopen and be booked by a
  different patient without losing the record of who had it before.
- **UUID primary keys**, not auto-increment — avoids exposing row counts
  on a public API, at the (accepted) cost of slightly larger indexes.
- **`TEXT + CHECK`** instead of native Postgres `ENUM` types for
  role/status columns — enums are awkward to extend later
  (`ALTER TYPE ... ADD VALUE` has transactional quirks); a `CHECK`
  constraint is one migration away from a new status value.

Full schema: [`backend/src/main/resources/db/migration/V1__init_schema.sql`](backend/src/main/resources/db/migration/V1__init_schema.sql).

### Dependencies beyond the required stack, and why

- **Spring Data JPA** — idiomatic Postgres access, and where the
  pessimistic lock annotation for the booking race lives.
- **Flyway** — versioned, plain-SQL schema migrations. Chosen over
  Liquibase for transparency (a migration is just SQL, readable in a PR
  diff) and because it's the more common default in Spring Boot projects.
- **Testcontainers** — the concurrency test (Phase 3) needs to exercise
  Postgres's *real* row-locking behavior; H2 doesn't have the same MVCC
  semantics, so a test passing against H2 wouldn't actually prove the
  locking strategy works.
- **`io.jsonwebtoken:jjwt`** (Phase 2) — simple encode/decode API for a
  self-issued-token flow. Chosen over Spring Security's OAuth2 JOSE
  support, which is more naturally suited to a resource server trusting
  an *external* token issuer — not quite this project's shape.
- **Lombok** — cuts entity/DTO boilerplate (getters, constructors,
  builders).
