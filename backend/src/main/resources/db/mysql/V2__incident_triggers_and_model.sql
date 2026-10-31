INSERT INTO ai_models (model_name, version, model_type, description, is_active)
SELECT 'CYPHER-YOLOv8s', '3.0', 'Object Detection',
       'Six classes: knife, pistol, person, fire, smoke, lying', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM ai_models WHERE model_name = 'CYPHER-YOLOv8s' AND version = '3.0'
);

DELIMITER //
CREATE TRIGGER trg_close_alerts AFTER UPDATE ON incidents
FOR EACH ROW
BEGIN
    IF NEW.status IN ('RESOLVED', 'CLOSED') OR NEW.resolved_at IS NOT NULL THEN
        UPDATE alerts SET alert_status = 'CLOSED' WHERE incident_id = NEW.incident_id;
    END IF;
END//

CREATE TRIGGER trg_incident_changes AFTER UPDATE ON incidents
FOR EACH ROW
BEGIN
    INSERT INTO audit_logs (user_id, event_type, entity_name, entity_id, old_values, new_values)
    VALUES (NEW.assigned_to, 'UPDATE', 'incidents', NEW.incident_id,
        JSON_OBJECT('status', OLD.status, 'severity', OLD.severity, 'title', OLD.title,
                    'assigned_to', OLD.assigned_to, 'resolved_at', OLD.resolved_at),
        JSON_OBJECT('status', NEW.status, 'severity', NEW.severity, 'title', NEW.title,
                    'assigned_to', NEW.assigned_to, 'resolved_at', NEW.resolved_at));
END//
DELIMITER ;
