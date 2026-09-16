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
- [x] **Phase 2** — auth (register, login, JWT, roles)
- [x] **Phase 3** — booking API with concurrency handling + concurrency test
- [x] **Phase 4** — Angular shell, routing, auth guards
- [x] **Phase 5** — calendar UI and booking flow
- [x] **Phase 6** — WebSocket live availability
- [ ] Phase 7 — reschedule, cancel, doctor and admin views
- [ ] Phase 8 — this README's full architecture write-up

## Live demo

Deployed via the Render Blueprint below — interactive API docs (there's
no frontend yet — that's Phase 4+). Register a patient, log in, then
call `/me`. _(Link added here once deployed.)_

Free-tier hosting, so: the service spins down after 15 minutes idle
(the first request after that takes ~30-50s to wake it up), and the
free Postgres database expires after 30 days (redeploying the
[Blueprint](render.yaml) recreates it — the Flyway migration rebuilds
the schema automatically, nothing to restore since it's a demo
database).

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/dharanijayachandran/clinic-booking-system)

## Running it locally

```bash
docker compose up
```

- Backend: http://localhost:8080 (Swagger UI: http://localhost:8080/swagger-ui.html)
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

**How the race is actually won**, in `BookingService.book()`:

1. `findByIdForUpdate` issues `SELECT … FOR UPDATE`. Postgres grants the
   row lock to one transaction; every other transaction asking for the
   same row *blocks there* until the winner commits.
2. Only *then* is status checked — so it reads committed state, not a
   stale snapshot. This ordering is the entire point: checking before
   locking is the TOCTOU bug the method exists to avoid.
3. The insert is still wrapped against `DataIntegrityViolationException`,
   because the partial unique index is the real guarantee. If a refactor
   ever loses the lock, the database still refuses two `CONFIRMED`
   appointments per slot and the caller still gets a 409.

**Why no lock timeout.** Postgres offers `FOR UPDATE NOWAIT` (fail
instantly) and `SKIP LOCKED` (ignore locked rows). Neither fits: the
loser blocking for a few milliseconds and then getting a definitive
"that slot is taken" beats `NOWAIT`'s ambiguous "try again".
`SKIP LOCKED` is the right tool for job queues ("grab any free row"),
not for "I want *this* slot".

**Why not `SERIALIZABLE`.** It would also prevent the double-book, but
it taxes every transaction in the application and produces serialization
failures that callers must retry. Explicit row locking solves one
contended row precisely.

**Proven, not asserted.** `BookingConcurrencyTest` holds two threads at
a `CountDownLatch`, releases them together, and asserts exactly one
succeeds, one is rejected, the database holds exactly one `CONFIRMED`
appointment, and the slot ends `BOOKED`. A separate test bypasses the
service entirely to prove the unique index stands on its own.

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

### Auth (Phase 2)

- **JWTs in httpOnly cookies, not `localStorage` + `Authorization` header.**
  `localStorage` is readable by any JS running on the page, so a single XSS
  hole anywhere (including a third-party script) can exfiltrate the token.
  An httpOnly cookie can't be read by JS at all. The trade-off: cookies are
  attached to requests automatically by the browser, which reopens CSRF —
  so CSRF protection stays on (`CookieCsrfTokenRepository`), and
  `SameSite=Lax` withholds the cookie on genuinely cross-site requests.
- **Access + refresh token pair**, discriminated by a `type` claim inside
  the token itself. The access token is short-lived (15 min default) and
  sent on every request; the refresh token is long-lived (7 days) and
  scoped via cookie `path` to only the one endpoint that reads it
  (`/api/auth/refresh`), so a stolen access token is a short-lived problem
  and the long-lived credential isn't presented to the rest of the API.
- **HS256, not RS256** — this service both issues and validates its own
  tokens, so there's no scenario yet where a *different* service needs to
  verify a token without being trusted to mint one. RS256 earns its keep
  the moment that stops being true (e.g. a separate notification service
  validating tokens it never issues).
- **Registration always creates a `PATIENT`.** `RegisterRequest` has no
  role field at all — there is nothing for a client to send that would
  create a `DOCTOR` or `ADMIN` account. The one `ADMIN` account is seeded
  once on first boot (`AdminSeeder`); every `DOCTOR` account after that is
  created by an admin, in the Phase 7 admin UI.
- **Wrong password and unknown email return the identical 401** with the
  identical message. Distinguishing them (e.g. "no account with that
  email" vs "wrong password") lets an attacker enumerate which emails are
  registered.
- **BCrypt over Argon2id** — Argon2id is OWASP's current top recommendation,
  but needs its own dependency and manual memory/parallelism tuning. BCrypt
  ships with Spring Security, is adaptive-cost, and remains a fully
  defensible default for this project's threat model.

### Frontend (Phase 4)

- **The app talks to `/api` through a proxy, never to `:8080` directly**
  (Angular dev server in development, nginx in the container). This is
  load-bearing: auth lives in httpOnly cookies and CSRF uses the
  double-submit pattern, and cross-origin the browser won't let JS on
  `:4200` read a cookie set by `:8080` — so the CSRF token could never
  be echoed back. Same-origin makes cookies, Angular's built-in XSRF
  interceptor, and CORS all stop being problems at once.
- **Auth state is a signal, not a `BehaviorSubject`.** Templates read
  `currentUser()` directly: no async pipe, no manual subscription, and
  OnPush components stay correct by default.
- **No token ever touches JavaScript.** The frontend never sees, stores
  or sends the JWT — the cookies are httpOnly precisely so an XSS
  payload can't read them. The app only learns *who* it is, from `/me`.
- **An `APP_INITIALIZER` restores the session before the first route
  resolves**, so a page refresh isn't mistaken for a logout.
- **Guards and `*appHasRole` are presentation only, and say so.** They
  keep users out of pointless screens and hide controls they can't use;
  they are not what protects the data. That's `@PreAuthorize` on the
  server — anything enforced only in the browser is enforced nowhere.
  The directive is structural, so hidden controls are absent from the
  DOM rather than merely invisible.

### Optimistic booking, and why the rollback isn't symmetric (Phase 5)

Clicking a slot marks it pending immediately, so the UI responds now
rather than after a round trip. What happens next depends on *why* the
server said no — and "undo the optimistic change" is not the same as
"restore the previous state":

- **409 Conflict** — someone else won the race. Restoring the slot to
  AVAILABLE would be a lie, because it genuinely isn't available any
  more. It stays taken, struck through, with an explanation.
- **Anything else** (network, 500) — the server's state is unknown, so
  the slot *is* restored and the user can retry.

### Live availability (Phase 6)

- **The broadcast fires `AFTER_COMMIT`, never inside the booking
  transaction.** Doing it inside would hold the contended row lock
  across network I/O, and a rollback would leave every client believing
  a slot was taken when it's still free, with nothing to correct them.
- **Per-doctor topics** (`/topic/slots/{doctorId}`), so browsing one
  calendar doesn't wake the client for every booking in the clinic.
- **A failed broadcast never fails the booking** — it's already
  committed; the client just finds out on its next refresh.
- **The simple broker is a known limitation, not an oversight.**
  Subscriptions live in one JVM's memory, so a second backend instance
  wouldn't see the first's events. Correct for a single instance; the
  honest answer for scaling out is a broker relay (RabbitMQ) or Redis
  pub/sub.
- **An in-flight booking is never clobbered by a live event.** The echo
  of your own booking arrives on the same topic, so letting it win would
  contradict the `201` about to arrive, or duplicate what the `409` path
  already handles.

### CSRF with a cookie-based SPA

Spring Security 6 defaults to `XorCsrfTokenRequestAttributeHandler`,
which BREACH-masks the token and expects a masked value back, while
`CookieCsrfTokenRepository` writes the **raw** token to `XSRF-TOKEN`. A
SPA echoing that cookie in `X-XSRF-TOKEN` therefore never validates, and
every state-changing request fails.

Worse, the failure is disguised: `CsrfFilter` runs *before* the JWT
filter, so the rejection happens while the request is still anonymous
and surfaces as **401, not 403** — which sends you hunting for an
authentication bug. `SpaCsrfTokenRequestHandler` applies Spring's
documented fix: mask when rendering, resolve plainly when the request
carries the header.

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
