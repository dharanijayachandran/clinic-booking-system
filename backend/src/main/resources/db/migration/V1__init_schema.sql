-- Extension-free: gen_random_uuid() has been built into Postgres core since v13.

-- ============================================================================
-- users — shared identity/auth table for all three roles. Patients, doctors
-- and admins all log in through the same endpoint with the same credential
-- shape, so they share one table; role-specific attributes live in extension
-- tables (see `doctors` below) rather than as nullable columns here.
-- ============================================================================
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    first_name    TEXT NOT NULL,
    last_name     TEXT NOT NULL,
    role          TEXT NOT NULL CHECK (role IN ('PATIENT', 'DOCTOR', 'ADMIN')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Case-insensitive uniqueness without needing the citext extension.
CREATE UNIQUE INDEX idx_users_email_lower ON users (LOWER(email));

-- ============================================================================
-- doctors — one row per user with role = 'DOCTOR'. Keeps specialty/bio/slot
-- duration out of the shared users table (they'd be NULL for every patient
-- and admin otherwise), while still being a clean 1:1 extension of a user.
-- ============================================================================
CREATE TABLE doctors (
    user_id               UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    specialty             TEXT NOT NULL,
    bio                   TEXT,
    slot_duration_minutes SMALLINT NOT NULL DEFAULT 30 CHECK (slot_duration_minutes > 0)
);

-- ============================================================================
-- working_hours — the recurring weekly template an admin edits per doctor.
-- Consumed by the (Phase 3) slot-generation job, not read directly by patients.
-- ============================================================================
CREATE TABLE working_hours (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id   UUID NOT NULL REFERENCES doctors(user_id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 0 AND 6), -- 0 = Sunday
    start_time  TIME NOT NULL,
    end_time    TIME NOT NULL,
    CHECK (end_time > start_time),
    UNIQUE (doctor_id, day_of_week, start_time)
);

-- ============================================================================
-- time_off — one-off exceptions (vacation, a meeting) that suppress slot
-- generation for a range, independent of the recurring weekly template.
-- ============================================================================
CREATE TABLE time_off (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id UUID NOT NULL REFERENCES doctors(user_id) ON DELETE CASCADE,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at   TIMESTAMPTZ NOT NULL,
    reason    TEXT,
    CHECK (ends_at > starts_at)
);

-- ============================================================================
-- slots — the concrete, lockable, bookable unit of time. Pre-generated from
-- working_hours minus time_off, rather than computed on the fly, because the
-- booking race (see appointments below) needs a real row to lock, and live
-- availability just becomes "broadcast when a slot's status changes."
-- ============================================================================
CREATE TABLE slots (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id UUID NOT NULL REFERENCES doctors(user_id) ON DELETE CASCADE,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at   TIMESTAMPTZ NOT NULL,
    status    TEXT NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'BOOKED', 'BLOCKED')),
    CHECK (ends_at > starts_at),
    -- Protects the generation job from ever creating the same slot twice,
    -- independent of the booking race handled at the appointments layer.
    UNIQUE (doctor_id, starts_at)
);

-- Hottest read in the app: "available slots for doctor X in a date range."
-- Partial (AVAILABLE-only) so booked/blocked history doesn't bloat the index.
CREATE INDEX idx_slots_doctor_available ON slots (doctor_id, starts_at) WHERE status = 'AVAILABLE';

-- ============================================================================
-- appointments — the booking event, kept separate from slots so history
-- survives a cancellation (the slot can reopen and be booked again by a
-- different patient without losing the record of who had it before).
-- ============================================================================
CREATE TABLE appointments (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slot_id      UUID NOT NULL REFERENCES slots(id),
    patient_id   UUID NOT NULL REFERENCES users(id),
    status       TEXT NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    cancelled_at TIMESTAMPTZ
);

-- THE double-booking guarantee: at most one CONFIRMED appointment per slot,
-- ever, enforced by Postgres — independent of whatever locking the
-- application layer does. A cancelled appointment doesn't block a new one
-- for the same (reopened) slot, because the index only covers CONFIRMED rows.
CREATE UNIQUE INDEX idx_appointments_one_confirmed_per_slot
    ON appointments (slot_id) WHERE status = 'CONFIRMED';

CREATE INDEX idx_appointments_patient ON appointments (patient_id);
