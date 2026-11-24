\set ON_ERROR_STOP on

BEGIN;
SELECT pg_advisory_xact_lock(723214807001);
SELECT EXISTS (
    SELECT 1 FROM admin_schema_migrations WHERE version = 3
) AS already_applied \gset

\if :already_applied
    \echo Readiness checklist migration 003 already applied
\else
    CREATE TABLE admin_readiness_templates (
        id text PRIMARY KEY CHECK (btrim(id) <> '')
    );
    CREATE TABLE admin_readiness_template_items (
        template_id text NOT NULL REFERENCES admin_readiness_templates(id),
        position integer NOT NULL CHECK (position >= 0),
        item_text varchar(1000) NOT NULL CHECK (item_text !~ '^[[:space:]]*$'),
        PRIMARY KEY (template_id, position)
    );
    -- The default template starts empty; admins supply the checklist guidance.
    INSERT INTO admin_readiness_templates (id) VALUES ('default');
    INSERT INTO admin_schema_migrations (version) VALUES (3);
\endif
COMMIT;
