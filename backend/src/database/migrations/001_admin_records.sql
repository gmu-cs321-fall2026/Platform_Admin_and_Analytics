\set ON_ERROR_STOP on

BEGIN;
-- Serialize concurrent attempts to apply Admin & Analytics migrations.
SELECT pg_advisory_xact_lock(723214807001);

CREATE TABLE IF NOT EXISTS admin_schema_migrations (
    version integer PRIMARY KEY,
    applied_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

SELECT EXISTS (
    SELECT 1 FROM admin_schema_migrations WHERE version = 1
) AS already_applied \gset

\if :already_applied
    \echo SCRUM-48 migration 001 already applied
\else
    CREATE TABLE admin_announcements (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        sender_id text NOT NULL CHECK (btrim(sender_id) <> ''),
        title text NOT NULL CHECK (btrim(title) <> ''),
        message text NOT NULL CHECK (btrim(message) <> ''),
        audience jsonb NOT NULL CHECK (jsonb_typeof(audience) = 'object' AND audience <> '{}'::jsonb),
        dispatch_status text NOT NULL DEFAULT 'DRAFT'
            CHECK (dispatch_status IN ('DRAFT', 'PENDING', 'SENT', 'FAILED')),
        created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
        sent_at timestamptz,
        broadcast_reference text UNIQUE CHECK (btrim(broadcast_reference) <> ''),
        CHECK (
            (dispatch_status = 'SENT' AND sent_at IS NOT NULL AND broadcast_reference IS NOT NULL)
            OR (dispatch_status <> 'SENT' AND sent_at IS NULL AND broadcast_reference IS NULL)
        )
    );

    CREATE TABLE admin_moderation_decisions (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        queue_item_id text NOT NULL UNIQUE CHECK (btrim(queue_item_id) <> ''),
        target_type text NOT NULL
            CHECK (target_type IN ('FLAGGED_POST', 'ACCOUNT_FLAG', 'RESOURCE', 'PRACTICE_QUESTION')),
        target_id text NOT NULL CHECK (btrim(target_id) <> ''),
        review_status text NOT NULL DEFAULT 'PENDING'
            CHECK (review_status IN ('PENDING', 'RESOLVED')),
        action text CHECK (btrim(action) <> ''),
        admin_id text CHECK (btrim(admin_id) <> ''),
        reason text CHECK (btrim(reason) <> ''),
        decided_at timestamptz,
        CHECK (
            (review_status = 'PENDING'
                AND action IS NULL AND admin_id IS NULL AND reason IS NULL AND decided_at IS NULL)
            OR (review_status = 'RESOLVED'
                AND action IS NOT NULL AND admin_id IS NOT NULL AND reason IS NOT NULL
                AND decided_at IS NOT NULL)
        )
    );

    CREATE TABLE admin_audit_entries (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        occurred_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
        action text NOT NULL CHECK (btrim(action) <> ''),
        target_type text NOT NULL CHECK (btrim(target_type) <> ''),
        target_id text NOT NULL CHECK (btrim(target_id) <> ''),
        target_name text,
        admin_id text NOT NULL CHECK (btrim(admin_id) <> ''),
        reason text,
        previous_value text,
        new_value text
    );

    CREATE INDEX admin_audit_entries_time_idx
        ON admin_audit_entries (occurred_at DESC, id DESC);
    CREATE INDEX admin_audit_entries_action_time_idx
        ON admin_audit_entries (action, occurred_at DESC, id DESC);

    INSERT INTO admin_schema_migrations (version) VALUES (1);
    \echo SCRUM-48 migration 001 applied
\endif

COMMIT;
