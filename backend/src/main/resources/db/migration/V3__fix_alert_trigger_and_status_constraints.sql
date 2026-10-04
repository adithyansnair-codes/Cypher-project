-- ============================================================================
-- V3 : Fix the alert-closing trigger and pin down status vocabularies
-- ============================================================================
-- TWO PROBLEMS, both found by running the workflow end to end.
--
-- 1. The alert trigger never fires for a resolved incident.
--
--    close_alerts_for_closed_incidents() tests `NEW.status = 'CLOSED'`, but an
--    incident is resolved by setting status = 'RESOLVED'. So resolving an
--    incident through the API left its alert stuck on PENDING, and the
--    active_alerts view kept reporting a case the operator had already closed.
--    That is precisely the behaviour MoSCoW requirement M-08 promises.
--
--    The function now closes alerts when the incident reaches a terminal state
--    (RESOLVED or CLOSED), or whenever resolved_at is set -- so it does not
--    depend on which word the caller happens to use.
--
-- 2. incidents.status and alerts.alert_status had no CHECK constraint.
--
--    Any spelling was silently accepted. Without a constraint the trigger above
--    could drift again and nothing would complain. Both columns are now pinned
--    to a fixed vocabulary.
-- ============================================================================


-- ---------------------------------------------------------------- trigger ---
CREATE OR REPLACE FUNCTION public.close_alerts_for_closed_incidents() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    -- Terminal states, plus a guard on resolved_at so the behaviour does not
    -- depend on which status word the caller used.
    IF NEW.status IN ('RESOLVED', 'CLOSED') OR NEW.resolved_at IS NOT NULL THEN
        UPDATE alerts
           SET alert_status = 'CLOSED'
         WHERE incident_id = NEW.incident_id
           AND alert_status <> 'CLOSED';
    END IF;

    RETURN NEW;
END;
$$;


-- ------------------------------------------------------------- constraints ---
-- Added conditionally so this migration is safe to replay against a database
-- that already has them.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_incidents_status'
    ) THEN
        ALTER TABLE incidents
            ADD CONSTRAINT chk_incidents_status
            CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_incidents_severity'
    ) THEN
        ALTER TABLE incidents
            ADD CONSTRAINT chk_incidents_severity
            CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_alerts_status'
    ) THEN
        ALTER TABLE alerts
            ADD CONSTRAINT chk_alerts_status
            CHECK (alert_status IN ('PENDING', 'SENT', 'CLOSED'));
    END IF;
END $$;


-- ------------------------------------------------------------------ repair ---
-- Close alerts whose incident is already terminal but whose alert is still
-- open. This fixes rows written before the function was corrected.
UPDATE alerts a
   SET alert_status = 'CLOSED'
 WHERE a.alert_status <> 'CLOSED'
   AND EXISTS (
       SELECT 1 FROM incidents i
        WHERE i.incident_id = a.incident_id
          AND (i.status IN ('RESOLVED', 'CLOSED') OR i.resolved_at IS NOT NULL)
   );
