\set ON_ERROR_STOP on

BEGIN;

DO $test$
DECLARE
    demo_reference text := gen_random_uuid()::text;
    announcement_id uuid;
    audit_id uuid;
BEGIN
    IF (SELECT count(*) FROM admin_schema_migrations WHERE version = 1) <> 1 THEN
        RAISE EXCEPTION 'Expected one migration 001 entry';
    END IF;

    INSERT INTO admin_announcements (sender_id, title, message, audience)
    VALUES ('test-staff', 'Test announcement', 'Test body', '{"kind":"all_students"}')
    RETURNING id INTO announcement_id;

    UPDATE admin_announcements
    SET dispatch_status = 'SENT', sent_at = CURRENT_TIMESTAMP, broadcast_reference = demo_reference
    WHERE id = announcement_id;

    INSERT INTO admin_moderation_decisions (queue_item_id, target_type, target_id)
    VALUES (demo_reference, 'ACCOUNT_FLAG', '1');

    UPDATE admin_moderation_decisions
    SET review_status = 'RESOLVED', action = 'ACCOUNT_SUSPENDED', admin_id = 'test-admin',
        reason = 'Test reason', decided_at = CURRENT_TIMESTAMP
    WHERE queue_item_id = demo_reference;

    INSERT INTO admin_audit_entries (action, target_type, target_id, admin_id)
    VALUES ('ACCOUNT_SUSPENDED', 'USER', '1', 'test-admin')
    RETURNING id INTO audit_id;

    BEGIN
        INSERT INTO admin_audit_entries (id, action, target_type, target_id, admin_id)
        VALUES (audit_id, 'ACCOUNT_SUSPENDED', 'USER', '1', 'test-admin');
        RAISE EXCEPTION 'Duplicate audit ID was accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO admin_moderation_decisions (queue_item_id, target_type, target_id)
        VALUES (demo_reference, 'ACCOUNT_FLAG', '1');
        RAISE EXCEPTION 'Duplicate moderation decision was accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO admin_moderation_decisions (queue_item_id, target_type, target_id, review_status)
        VALUES (demo_reference || '-bad-status', 'ACCOUNT_FLAG', '1', 'INVALID');
        RAISE EXCEPTION 'Invalid review status was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO admin_moderation_decisions (queue_item_id, target_type, target_id, review_status)
        VALUES (demo_reference || '-missing-reason', 'ACCOUNT_FLAG', '1', 'RESOLVED');
        RAISE EXCEPTION 'Incomplete moderation resolution was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO admin_audit_entries (action, target_type, admin_id)
        VALUES ('ROLE_CHANGED', 'USER', 'test-admin');
        RAISE EXCEPTION 'Missing audit target was accepted';
    EXCEPTION WHEN not_null_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO admin_announcements (sender_id, title, message, audience)
        VALUES ('test-staff', ' ', 'Test body', '{"kind":"all_students"}');
        RAISE EXCEPTION 'Blank title was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
END
$test$;

ROLLBACK;
\echo SCRUM-48 schema checks passed
