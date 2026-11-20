\set ON_ERROR_STOP on

BEGIN;
SELECT pg_advisory_xact_lock(723214807001);

CREATE TABLE IF NOT EXISTS platform_account_registrations (
    user_id text PRIMARY KEY CHECK (btrim(user_id) <> ''),
    registered_at timestamptz NOT NULL
);

CREATE INDEX IF NOT EXISTS platform_account_registrations_registered_at_idx
    ON platform_account_registrations (registered_at);

COMMIT;
