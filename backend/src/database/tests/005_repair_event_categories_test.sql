\set ON_ERROR_STOP on
-- CI only: run in a disposable database. Never alter public configuration.
CREATE SCHEMA sprint5_upgrade_test;
SET search_path TO sprint5_upgrade_test, public;
CREATE TABLE admin_schema_migrations (version integer PRIMARY KEY);
INSERT INTO admin_schema_migrations VALUES (2);
\ir ../migrations/005_repair_event_categories.sql
INSERT INTO admin_event_categories(id, name, description)
VALUES ('00000000-0000-0000-0000-000000000055', 'Existing category', 'Preserve this description');
\ir ../migrations/005_repair_event_categories.sql
DO $test$
BEGIN
    IF (SELECT count(*) FROM admin_event_categories
        WHERE id = '00000000-0000-0000-0000-000000000055'
        AND description = 'Preserve this description') <> 1 THEN
        RAISE EXCEPTION 'Repair migration lost or duplicated an existing category';
    END IF;
    IF (SELECT count(*) FROM admin_schema_migrations WHERE version = 5) <> 1 THEN
        RAISE EXCEPTION 'Repair migration must record version 5 exactly once';
    END IF;
END
$test$;
CREATE TABLE admin_notification_templates (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(), name text NOT NULL, message text NOT NULL
);
\ir ../migrations/006_initial_configuration.sql
DO $test$
BEGIN
    IF (SELECT count(*) FROM admin_event_categories) <> 1 THEN
        RAISE EXCEPTION 'Initial defaults must preserve existing categories';
    END IF;
END
$test$;
-- Reset only this test schema to exercise a completely empty installation.
TRUNCATE sprint5_upgrade_test.admin_event_categories, sprint5_upgrade_test.admin_notification_templates;
DELETE FROM sprint5_upgrade_test.admin_schema_migrations WHERE version = 6;
\ir ../migrations/006_initial_configuration.sql
UPDATE sprint5_upgrade_test.admin_event_categories SET description = 'Admin edit' WHERE name = 'Workshop';
UPDATE sprint5_upgrade_test.admin_notification_templates SET message = 'Admin wording';
\ir ../migrations/006_initial_configuration.sql
DO $test$
BEGIN
    IF (SELECT count(*) FROM admin_event_categories) <> 3
       OR NOT EXISTS (SELECT 1 FROM admin_event_categories WHERE name = 'Workshop' AND description = 'Admin edit') THEN
        RAISE EXCEPTION 'Defaults must be inserted once and preserve later category edits';
    END IF;
    IF (SELECT count(*) FROM admin_notification_templates) <> 1
       OR NOT EXISTS (SELECT 1 FROM admin_notification_templates WHERE message = 'Admin wording') THEN
        RAISE EXCEPTION 'Defaults must preserve later notification edits';
    END IF;
END
$test$;
SET search_path TO public;
DROP SCHEMA sprint5_upgrade_test CASCADE;
\echo Sprint 5 upgrade repair, initial defaults, and repeat-migration checks passed
