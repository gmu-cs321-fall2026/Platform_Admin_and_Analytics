\set ON_ERROR_STOP on
BEGIN;
SELECT pg_advisory_xact_lock(723214807001);
-- Some existing databases recorded version 2 without the event category table.
-- Repair the schema independently of that marker; preserve every existing row.
CREATE TABLE IF NOT EXISTS admin_event_categories (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(200) NOT NULL CHECK (btrim(name) <> ''),
    description varchar(5000) NOT NULL DEFAULT ''
);
ALTER TABLE admin_event_categories
    ADD COLUMN IF NOT EXISTS description varchar(5000) NOT NULL DEFAULT '';
CREATE UNIQUE INDEX IF NOT EXISTS admin_event_categories_name_idx
    ON admin_event_categories (lower(btrim(name)));
INSERT INTO admin_schema_migrations (version) VALUES (5) ON CONFLICT (version) DO NOTHING;
COMMIT;
