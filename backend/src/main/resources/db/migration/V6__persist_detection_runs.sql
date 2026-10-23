-- ============================================================================
-- V6 : Keep the detection that caused every ingested incident
-- ============================================================================
-- The gateway previously called create_incident_with_alert(6 args), which
-- stored an incident and alert but left incidents.detection_id NULL. Analytics
-- therefore counted only seeded detections, not live AI results.
--
-- The new overload accepts a detection id and stores it on the incident. The
-- service creates the inference job and detection in the same transaction
-- before calling this procedure.
-- ============================================================================

INSERT INTO ai_models (model_name, version, model_type, description, is_active)
SELECT
    'CYPHER-YOLOv8s',
    '3.0',
    'Object Detection',
    'Six-class CYPHER detector: knife, pistol, person, fire, smoke, lying',
    TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM ai_models
    WHERE model_name = 'CYPHER-YOLOv8s' AND version = '3.0'
);

CREATE OR REPLACE PROCEDURE public.create_incident_with_alert(
    IN p_project_id bigint,
    IN p_camera_id bigint,
    IN p_title character varying,
    IN p_description text,
    IN p_severity character varying,
    IN p_assigned_to bigint,
    IN p_detection_id bigint
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_incident_id BIGINT;
BEGIN
    INSERT INTO incidents (
        project_id,
        camera_id,
        detection_id,
        title,
        description,
        severity,
        status,
        assigned_to,
        occurred_at
    )
    VALUES (
        p_project_id,
        p_camera_id,
        p_detection_id,
        p_title,
        p_description,
        p_severity,
        'OPEN',
        p_assigned_to,
        CURRENT_TIMESTAMP
    )
    RETURNING incident_id INTO v_incident_id;

    INSERT INTO alerts (
        incident_id,
        alert_type,
        alert_message,
        alert_status,
        created_at
    )
    VALUES (
        v_incident_id,
        'INCIDENT',
        'New Incident: ' || p_title,
        'PENDING',
        CURRENT_TIMESTAMP
    );
END;
$$;
