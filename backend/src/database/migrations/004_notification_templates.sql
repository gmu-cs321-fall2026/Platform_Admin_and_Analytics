\set ON_ERROR_STOP on

BEGIN;
SELECT pg_advisory_xact_lock(723214807001);
SELECT EXISTS (
    SELECT 1 FROM admin_schema_migrations WHERE version = 4
) AS already_applied \gset

\if :already_applied
    \echo Notification template migration 004 already applied
\else
    CREATE TABLE admin_notification_templates (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        name text NOT NULL CHECK (btrim(name) <> ''),
        message varchar(5000) NOT NULL CHECK (message !~ '^[[:space:]]*$')
    );
    INSERT INTO admin_schema_migrations (version) VALUES (4);
\endif
COMMIT;
