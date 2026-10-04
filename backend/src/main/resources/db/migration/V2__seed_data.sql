-- ============================================================================
-- V2 : CYPHER demo seed data
-- ============================================================================
-- Idempotent: every INSERT is guarded with WHERE NOT EXISTS, so re-running this
-- against an already-seeded database is a no-op.
--
-- All passwords are real BCrypt hashes (cost 10) generated with the reference
-- `bcrypt` library and verified by round-trip. See db/README.md for the
-- credentials. NEVER put plaintext in password_hash.
-- ============================================================================

-- ---------------------------------------------------------------- roles ----
INSERT INTO roles (role_name, description)
SELECT v.role_name, v.description
FROM (VALUES
    ('SUPER_ADMIN',      'Full system access'),
    ('ADMIN',            'Project administrator'),
    ('SECURITY_MANAGER', 'Security operations manager'),
    ('ANALYST',          'AI detection analyst'),
    ('OPERATOR',         'Camera operator'),
    ('VIEWER',           'Read-only user')
) AS v(role_name, description)
WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE r.role_name = v.role_name);

-- ---------------------------------------------------------- permissions ----
INSERT INTO permissions (permission_name, description)
SELECT v.permission_name, v.description
FROM (VALUES
    ('USER_READ',      'View users'),
    ('USER_WRITE',     'Create and modify users'),
    ('PROJECT_READ',   'View projects'),
    ('PROJECT_WRITE',  'Create and modify projects'),
    ('CAMERA_READ',    'View cameras'),
    ('CAMERA_WRITE',   'Manage cameras'),
    ('INCIDENT_READ',  'View incidents'),
    ('INCIDENT_WRITE', 'Acknowledge and resolve incidents'),
    ('REPORT_READ',    'View reports'),
    ('REPORT_WRITE',   'Generate reports'),
    ('AUDIT_READ',     'View the audit trail'),
    ('SYSTEM_ADMIN',   'Administer the system')
) AS v(permission_name, description)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.permission_name = v.permission_name);

-- ------------------------------------- SUPER_ADMIN receives every permission
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
CROSS JOIN permissions p
WHERE r.role_name = 'SUPER_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id
  );

-- ------------------------------------------------- OPERATOR permissions ----
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.permission_name IN (
    'PROJECT_READ', 'CAMERA_READ', 'INCIDENT_READ', 'INCIDENT_WRITE', 'REPORT_READ'
)
WHERE r.role_name = 'OPERATOR'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id
  );

-- -------------------------------------------------- ANALYST permissions ----
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.permission_name IN (
    'PROJECT_READ', 'CAMERA_READ', 'INCIDENT_READ', 'REPORT_READ', 'REPORT_WRITE'
)
WHERE r.role_name = 'ANALYST'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id
  );

-- ----------------------------------------------------------------- users ---
-- Development credentials -- CHANGE BEFORE ANY REVIEW:
--   admin@cypher.com     Admin@2026
--   operator@cypher.com  Operator@2026
--   analyst@cypher.com   Analyst@2026
INSERT INTO users (first_name, last_name, email, password_hash, phone, status, role_id)
SELECT v.first_name, v.last_name, v.email, v.password_hash, v.phone, 'ACTIVE',
       (SELECT role_id FROM roles WHERE role_name = v.role_name)
FROM (VALUES
    ('System', 'Administrator', 'admin@cypher.com',
     '$2b$10$bE8xXXHgtOy91fiDeXH8e.iRYRAYcisSFKtvef2pLCDisnxDEo8MS',
     '+1234567890', 'SUPER_ADMIN'),
    ('Camera', 'Operator', 'operator@cypher.com',
     '$2b$10$SutEp/CaMYgSF5Rvk/DAKOL4KTpsimoNZdCQAnl4x1pzEwT1ZX1HW',
     '+1234567891', 'OPERATOR'),
    ('AI', 'Analyst', 'analyst@cypher.com',
     '$2b$10$nOzQyatz9DIdJiOhfvfqz.NqiQZG1JrJmKpRHh2aBMekQx4zKlw.e',
     '+1234567892', 'ANALYST')
) AS v(first_name, last_name, email, password_hash, phone, role_name)
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.email = v.email);

-- -------------------------------------------------------------- projects ---
INSERT INTO projects (project_name, description, location, status, created_by)
SELECT 'Cypher Demo Project', 'Main surveillance deployment', 'Main Campus', 'ACTIVE',
       (SELECT user_id FROM users WHERE email = 'admin@cypher.com')
WHERE NOT EXISTS (SELECT 1 FROM projects WHERE project_name = 'Cypher Demo Project');

-- -------------------------------------------------------------- cameras ----
-- These three match the "MONITORED ASSETS" list in the cockpit UI.
INSERT INTO cameras (project_id, camera_name, camera_code, location, stream_url, camera_status)
SELECT (SELECT project_id FROM projects WHERE project_name = 'Cypher Demo Project'),
       v.camera_name, v.camera_code, v.location, v.stream_url, 'ONLINE'
FROM (VALUES
    ('Main Entrance Camera',  'CAM-001', 'Main Gate',      'rtsp://192.168.1.100/live'),
    ('Lab Entrance Camera',   'CAM-002', 'Lab Entrance',   'rtsp://192.168.1.101/live'),
    ('North Corridor Camera', 'CAM-003', 'North Corridor', 'rtsp://192.168.1.102/live')
) AS v(camera_name, camera_code, location, stream_url)
WHERE NOT EXISTS (SELECT 1 FROM cameras c WHERE c.camera_code = v.camera_code);

-- ------------------------------------------------------------ ai_models ----
INSERT INTO ai_models (model_name, version, model_type, description, is_active)
SELECT v.model_name, v.version, v.model_type, v.description, true
FROM (VALUES
    ('YOLOv11', '1.0', 'Object Detection', 'General object detection'),
    ('YOLOv11', '2.0', 'Weapon Detection', 'Knife and firearm detection'),
    ('FaceNet', '1.2', 'Face Recognition', 'Face identification model')
) AS v(model_name, version, model_type, description)
WHERE NOT EXISTS (
    SELECT 1 FROM ai_models m
    WHERE m.model_name = v.model_name AND m.version = v.version
);

-- ----------------------------------------------------- project_members -----
INSERT INTO project_members (project_id, user_id)
SELECT (SELECT project_id FROM projects WHERE project_name = 'Cypher Demo Project'),
       u.user_id
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM project_members pm
    WHERE pm.project_id = (SELECT project_id FROM projects WHERE project_name = 'Cypher Demo Project')
      AND pm.user_id = u.user_id
);

-- ------------------------------------------------------- inference_jobs ----
-- Must come before `detections`: every detection references a job_id, and the
-- column is NOT NULL, so seeding them in the wrong order fails.
INSERT INTO inference_jobs (camera_id, model_id, started_at, status, frames_processed)
SELECT (SELECT camera_id FROM cameras WHERE camera_code = 'CAM-001'),
       (SELECT model_id FROM ai_models WHERE model_name = 'YOLOv11' AND version = '1.0'),
       CURRENT_TIMESTAMP, 'COMPLETED', 0
WHERE NOT EXISTS (SELECT 1 FROM inference_jobs);

-- ---------------------------------------------------------- detections -----
-- Two sample detections attached to the running inference job, so the incident
-- and media rows below have something real to reference.
INSERT INTO detections (camera_id, job_id, detected_object, confidence, bounding_box, image_path)
SELECT (SELECT camera_id FROM cameras WHERE camera_code = 'CAM-002'),
       (SELECT job_id FROM inference_jobs LIMIT 1),
       v.detected_object, v.confidence, v.bounding_box::jsonb, v.image_path
FROM (VALUES
    ('person', 94.60, '{"x": 120, "y": 240, "width": 60, "height": 150}',
     '/media/demo/detection-cam002-person.jpg'),
    ('smoke',  88.25, '{"x": 400, "y": 180, "width": 90, "height": 70}',
     '/media/demo/detection-cam002-smoke.jpg')
) AS v(detected_object, confidence, bounding_box, image_path)
WHERE NOT EXISTS (
    SELECT 1 FROM detections d WHERE d.image_path = v.image_path
);

-- ----------------------------------------------------------- incidents -----
-- Created through the schema's own stored procedure, which also raises the
-- matching alert row. This exercises the incident -> alert trigger path that
-- MoSCoW requirement M-08 depends on.
DO $$
DECLARE
    v_project_id BIGINT;
    v_camera_id  BIGINT;
    v_admin_id   BIGINT;
BEGIN
    SELECT project_id INTO v_project_id
      FROM projects WHERE project_name = 'Cypher Demo Project';

    SELECT camera_id INTO v_camera_id
      FROM cameras WHERE camera_code = 'CAM-002';

    SELECT user_id INTO v_admin_id
      FROM users WHERE email = 'admin@cypher.com';

    IF v_project_id IS NULL OR v_camera_id IS NULL THEN
        RAISE NOTICE 'seed: skipping demo incidents, prerequisites missing';
        RETURN;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM incidents WHERE title = 'Motion in Restricted Area') THEN
        CALL create_incident_with_alert(
            v_project_id, v_camera_id,
            'Motion in Restricted Area',
            'Person detected inside the lab after operating hours.',
            'CRITICAL', v_admin_id
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM incidents WHERE title = 'Smoke Detected') THEN
        CALL create_incident_with_alert(
            v_project_id, v_camera_id,
            'Smoke Detected',
            'Smoke signature detected near the north corridor vent.',
            'HIGH', v_admin_id
        );
    END IF;
END $$;

-- ----------------------------------------------------------------- media ---
-- Media hangs off a detection (there is no camera_id on this table).
INSERT INTO media (detection_id, media_type, file_name, file_path, file_size)
SELECT (SELECT detection_id FROM detections
         WHERE image_path = '/media/demo/detection-cam002-person.jpg'),
       'SNAPSHOT', 'detection-cam002-person.jpg',
       '/media/demo/detection-cam002-person.jpg', 245760
WHERE NOT EXISTS (
    SELECT 1 FROM media WHERE file_path = '/media/demo/detection-cam002-person.jpg'
);

