
\set ON_ERROR_STOP on

BEGIN;

-- Sprint 5: Platform Settings

CREATE TABLE IF NOT EXISTS admin_event_categories (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS admin_readiness_templates (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    checklist jsonb NOT NULL DEFAULT '[]'::jsonb
);

CREATE TABLE IF NOT EXISTS admin_notification_templates (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    subject text NOT NULL DEFAULT '',
    message text NOT NULL DEFAULT ''
);

COMMIT;
