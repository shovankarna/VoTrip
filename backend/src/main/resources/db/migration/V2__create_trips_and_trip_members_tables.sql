-- TRIP and TRIP_MEMBER entities, per docs/02-SRS-ERD.md section 5.
--
-- VARCHAR + CHECK, not native Postgres enums. docs/04-FutureScope.md section 4
-- flags native enums as cheap to change now and disruptive once data exists,
-- and calls EXPENSE.category's free-string approach the correct precedent.
-- No trip data exists yet, so this is the moment to follow that precedent
-- rather than defer it - trip_type in particular will grow when community
-- matching (docs/04-FutureScope.md section 2) adds categories. App-layer
-- validation enforces the same value sets; the CHECK constraints below are
-- the DB-level backstop, trivial to ALTER later.
CREATE TABLE trips (
    id               UUID              PRIMARY KEY,

    name             VARCHAR(255)      NOT NULL,
    cover_image_url  VARCHAR(2048),
    destination      VARCHAR(255),

    -- Reserved, per the ERD's forward-compatibility note (docs/04-FutureScope.md
    -- section 3) - no ITINERARY_TEMPLATE table exists yet, so no FK either. Same
    -- treatment as organization_id below.
    template_id      UUID,

    -- Reserved for the org-marketplace feature (docs/04-FutureScope.md section 3).
    -- No ORGANIZATION table exists yet, so deliberately no FK - column only.
    organization_id  UUID,

    created_by       UUID              NOT NULL REFERENCES users (id),

    status           VARCHAR(20)       NOT NULL DEFAULT 'planning'
                        CONSTRAINT chk_trips_status
                        CHECK (status IN ('planning', 'fixed', 'ongoing', 'ended', 'cancelled')),

    -- NOT NULL DEFAULT 'custom': FR-3.3 (02-SRS-ERD.md) already defines 'custom'
    -- as the catch-all value, so a nullable column would just give two ways to
    -- express "no specific type" and force every read site to handle null.
    trip_type        VARCHAR(20)       NOT NULL DEFAULT 'custom'
                        CONSTRAINT chk_trips_trip_type
                        CHECK (trip_type IN ('road_trip', 'bike_trip', 'mixed', 'custom')),

    -- Nullable: the status flow itself (planning -> fixed -> ...) implies dates
    -- aren't locked until a trip reaches 'fixed' - a trip in 'planning' may not
    -- have committed dates yet.
    start_date       DATE,
    end_date         DATE,

    -- FR-3.1: fixed INR for MVP, but stored per-trip (not hardcoded) so a future
    -- multi-currency change touches data, not schema.
    currency         VARCHAR(3)        NOT NULL DEFAULT 'INR',

    -- Shareable self-join code (FR-3.4). Always present - generated at creation,
    -- regenerable by a guide afterwards (old codes stop working; joined members
    -- are unaffected). UNIQUE also gives the self-join lookup-by-code path its
    -- index for free.
    invite_code      VARCHAR(32)       NOT NULL UNIQUE,

    created_at       TIMESTAMPTZ       NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ       NOT NULL DEFAULT NOW()
);

CREATE TABLE trip_members (
    id           UUID          PRIMARY KEY,

    -- No money or historical record hangs off a membership row on its own (unlike
    -- EXPENSE's protected links) - if the trip goes, the membership goes with it.
    trip_id      UUID          NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    user_id      UUID          NOT NULL REFERENCES users (id),

    role         VARCHAR(20)   NOT NULL
                    CONSTRAINT chk_trip_members_role
                    CHECK (role IN ('guide', 'member')),

    status       VARCHAR(20)   NOT NULL
                    CONSTRAINT chk_trip_members_status
                    CHECK (status IN ('invited', 'joined', 'removed')),

    -- Set only on the explicit-invite path (FR-3.4); null for self-joins via
    -- invite_code.
    invited_by   UUID          REFERENCES users (id),

    -- Null while status = 'invited'; set when the row becomes 'joined' (either
    -- immediately, for a self-join, or on invite acceptance).
    joined_at    TIMESTAMPTZ,

    -- A user can't have two membership rows on the same trip.
    CONSTRAINT uq_trip_members_trip_user UNIQUE (trip_id, user_id)
);

-- Supports "which trips is this user on" without relying on the composite
-- unique index above, which leads with trip_id and doesn't serve a user_id-only
-- lookup well.
CREATE INDEX idx_trip_members_user_id ON trip_members (user_id);
