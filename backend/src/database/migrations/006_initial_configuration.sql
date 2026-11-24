\set ON_ERROR_STOP on
BEGIN;
SELECT pg_advisory_xact_lock(723214807001);
SELECT EXISTS (SELECT 1 FROM admin_schema_migrations WHERE version = 6) AS already_applied \gset
\if :already_applied
    \echo Initial configuration already applied
\else
    -- Approved starting configuration, not sample user activity or broadcasts.
    -- Existing configuration and later admin edits always take precedence.
    INSERT INTO admin_event_categories (name, description)
    SELECT name, description FROM (VALUES
        ('Career Fair', 'Events connecting students with employers and career opportunities.'),
        ('Workshop', 'Practical sessions that help students develop career preparation skills.'),
        ('Information Session', 'Sessions introducing an employer, program, or career opportunity.')
    ) AS defaults(name, description)
    WHERE NOT EXISTS (SELECT 1 FROM admin_event_categories);

    INSERT INTO admin_notification_templates (name, message)
    SELECT 'Event Reminder', 'Reminder: you have an upcoming career event. Check the event details for the time and location.'
    WHERE NOT EXISTS (SELECT 1 FROM admin_notification_templates);

    INSERT INTO admin_schema_migrations (version) VALUES (6);
\endif
COMMIT;
