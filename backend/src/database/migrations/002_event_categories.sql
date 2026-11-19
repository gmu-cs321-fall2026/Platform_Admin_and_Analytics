\set ON_ERROR_STOP on

BEGIN;
SELECT pg_advisory_xact_lock(723214807001);
SELECT EXISTS (
    SELECT 1 FROM admin_schema_migrations WHERE version = 2
) AS already_applied \gset

\if :already_applied
    \echo Story 5 migration 002 already applied
\else
    CREATE TABLE admin_event_categories (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        name varchar(200) NOT NULL CHECK (btrim(name) <> ''),
        description varchar(5000) NOT NULL DEFAULT ''
    );
    CREATE UNIQUE INDEX admin_event_categories_name_idx
        ON admin_event_categories (lower(btrim(name)));
    INSERT INTO admin_schema_migrations (version) VALUES (2);
\endif
COMMIT;
