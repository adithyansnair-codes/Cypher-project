-- ============================================================================
-- V5 : Pin the remaining status vocabularies
-- ============================================================================
-- V3 pinned incidents.status, incidents.severity and alerts.alert_status, but
-- left cameras.camera_status and projects.status completely unconstrained --
-- any spelling was accepted, and docs/API.md claimed otherwise. This closes
-- that gap so the words the application and the database use cannot drift.
--
-- Guarded, so re-running against a database that already has them is a no-op.
-- ============================================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_cameras_status'
    ) THEN
        ALTER TABLE cameras
            ADD CONSTRAINT chk_cameras_status
            CHECK (camera_status IN ('ONLINE', 'OFFLINE', 'MAINTENANCE'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_projects_status'
    ) THEN
        ALTER TABLE projects
            ADD CONSTRAINT chk_projects_status
            CHECK (status IN ('ACTIVE', 'PAUSED', 'ARCHIVED'));
    END IF;
END $$;
