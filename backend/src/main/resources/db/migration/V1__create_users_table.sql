-- USER entity, per docs/02-SRS-ERD.md section 5.
--
-- Table is "users", not "user": USER is a reserved word in Postgres and an unquoted
-- CREATE TABLE user fails. The entity maps to it explicitly.
CREATE TABLE users (
    id             UUID         PRIMARY KEY,

    -- The identity this service keys on. Supplied by the verified Firebase token,
    -- never by a client, so it is the one column that can never be null.
    firebase_uid   VARCHAR(128) NOT NULL UNIQUE,

    -- Nullable on purpose: phone-number and anonymous Firebase providers issue tokens
    -- with no email claim. Postgres allows multiple NULLs under a UNIQUE constraint,
    -- so uniqueness still holds for the users that do have one.
    email          VARCHAR(320) UNIQUE,

    display_name   VARCHAR(255),
    phone          VARCHAR(32),
    avatar_url     VARCHAR(2048),

    is_super_admin BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Soft-delete flag for USER only: the ERD states USER rows are never hard-deleted,
    -- because nearly every table holds a user_id FK that financial and historical
    -- records depend on. This is not a blanket convention - other tables keep their own
    -- ERD-specified delete behaviour (EXPENSE -> ON DELETE SET NULL, DAY -> ITEM CASCADE).
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
