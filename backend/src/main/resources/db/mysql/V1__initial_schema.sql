-- MySQL version of the project schema.
CREATE TABLE roles (
    role_id BIGINT NOT NULL AUTO_INCREMENT,
    role_name VARCHAR(50) NOT NULL,
    description TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (role_id),
    UNIQUE KEY uk_roles_name (role_name)
) ENGINE=InnoDB;

CREATE TABLE permissions (
    permission_id BIGINT NOT NULL AUTO_INCREMENT,
    permission_name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (permission_id),
    UNIQUE KEY uk_permissions_name (permission_name)
) ENGINE=InnoDB;

CREATE TABLE users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash TEXT NOT NULL,
    phone VARCHAR(20) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_role (role_id),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (role_id),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))
) ENGINE=InnoDB;

CREATE TABLE projects (
    project_id BIGINT NOT NULL AUTO_INCREMENT,
    project_name VARCHAR(150) NOT NULL,
    description TEXT NULL,
    location VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (project_id),
    KEY idx_projects_status (status),
    CONSTRAINT fk_projects_creator FOREIGN KEY (created_by) REFERENCES users (user_id),
    CONSTRAINT chk_projects_status CHECK (status IN ('ACTIVE', 'PAUSED', 'ARCHIVED'))
) ENGINE=InnoDB;

CREATE TABLE project_members (
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (project_id, user_id),
    CONSTRAINT fk_pm_project FOREIGN KEY (project_id) REFERENCES projects (project_id) ON DELETE CASCADE,
    CONSTRAINT fk_pm_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE cameras (
    camera_id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    camera_name VARCHAR(100) NOT NULL,
    camera_code VARCHAR(50) NOT NULL,
    location VARCHAR(255) NULL,
    stream_url TEXT NULL,
    camera_status VARCHAR(20) NOT NULL DEFAULT 'ONLINE',
    latitude DECIMAL(10,8) NULL,
    longitude DECIMAL(11,8) NULL,
    installed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (camera_id),
    UNIQUE KEY uk_cameras_code (camera_code),
    KEY idx_cameras_project (project_id),
    KEY idx_cameras_status (camera_status),
    CONSTRAINT fk_cameras_project FOREIGN KEY (project_id) REFERENCES projects (project_id) ON DELETE CASCADE,
    CONSTRAINT chk_cameras_status CHECK (camera_status IN ('ONLINE', 'OFFLINE', 'MAINTENANCE'))
) ENGINE=InnoDB;

CREATE TABLE ai_models (
    model_id BIGINT NOT NULL AUTO_INCREMENT,
    model_name VARCHAR(100) NOT NULL,
    version VARCHAR(50) NOT NULL,
    model_type VARCHAR(100) NOT NULL,
    description TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (model_id),
    UNIQUE KEY uk_ai_models_name_version (model_name, version)
) ENGINE=InnoDB;

CREATE TABLE inference_jobs (
    job_id BIGINT NOT NULL AUTO_INCREMENT,
    camera_id BIGINT NOT NULL,
    model_id BIGINT NOT NULL,
    started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    frames_processed BIGINT NULL DEFAULT 0,
    PRIMARY KEY (job_id),
    KEY idx_jobs_camera (camera_id),
    KEY idx_jobs_model (model_id),
    KEY idx_jobs_status (status),
    CONSTRAINT fk_job_camera FOREIGN KEY (camera_id) REFERENCES cameras (camera_id) ON DELETE CASCADE,
    CONSTRAINT fk_job_model FOREIGN KEY (model_id) REFERENCES ai_models (model_id)
) ENGINE=InnoDB;

CREATE TABLE detections (
    detection_id BIGINT NOT NULL AUTO_INCREMENT,
    camera_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    detected_object VARCHAR(100) NOT NULL,
    confidence DECIMAL(5,2) NOT NULL,
    bounding_box JSON NULL,
    image_path TEXT NULL,
    detected_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (detection_id),
    KEY idx_detections_camera (camera_id),
    KEY idx_detections_job (job_id),
    KEY idx_detections_object (detected_object),
    KEY idx_detections_time (detected_at),
    CONSTRAINT fk_detection_camera FOREIGN KEY (camera_id) REFERENCES cameras (camera_id) ON DELETE CASCADE,
    CONSTRAINT fk_detection_job FOREIGN KEY (job_id) REFERENCES inference_jobs (job_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE incidents (
    incident_id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    camera_id BIGINT NOT NULL,
    detection_id BIGINT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    assigned_to BIGINT NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (incident_id),
    KEY idx_incidents_camera (camera_id),
    KEY idx_incidents_project (project_id),
    KEY idx_incidents_severity (severity),
    KEY idx_incidents_status (status),
    CONSTRAINT fk_incident_camera FOREIGN KEY (camera_id) REFERENCES cameras (camera_id),
    CONSTRAINT fk_incident_detection FOREIGN KEY (detection_id) REFERENCES detections (detection_id),
    CONSTRAINT fk_incident_project FOREIGN KEY (project_id) REFERENCES projects (project_id),
    CONSTRAINT fk_incident_user FOREIGN KEY (assigned_to) REFERENCES users (user_id),
    CONSTRAINT chk_incidents_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')),
    CONSTRAINT chk_incidents_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
) ENGINE=InnoDB;

CREATE TABLE alerts (
    alert_id BIGINT NOT NULL AUTO_INCREMENT,
    incident_id BIGINT NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    alert_message TEXT NOT NULL,
    alert_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    sent_at TIMESTAMP(6) NULL,
    PRIMARY KEY (alert_id),
    KEY idx_alerts_incident (incident_id),
    KEY idx_alerts_status (alert_status),
    CONSTRAINT fk_alert_incident FOREIGN KEY (incident_id) REFERENCES incidents (incident_id) ON DELETE CASCADE,
    CONSTRAINT chk_alerts_status CHECK (alert_status IN ('PENDING', 'SENT', 'CLOSED'))
) ENGINE=InnoDB;

CREATE TABLE audit_logs (
    audit_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    event_type VARCHAR(20) NOT NULL,
    entity_name VARCHAR(100) NOT NULL,
    entity_id BIGINT NULL,
    old_values JSON NULL,
    new_values JSON NULL,
    ip_address VARCHAR(45) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (audit_id),
    KEY idx_audit_user (user_id),
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE media (
    media_id BIGINT NOT NULL AUTO_INCREMENT,
    incident_id BIGINT NULL,
    detection_id BIGINT NULL,
    media_type VARCHAR(20) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path TEXT NOT NULL,
    file_size BIGINT NULL,
    uploaded_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (media_id),
    CONSTRAINT fk_media_incident FOREIGN KEY (incident_id) REFERENCES incidents (incident_id) ON DELETE CASCADE,
    CONSTRAINT fk_media_detection FOREIGN KEY (detection_id) REFERENCES detections (detection_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE notifications (
    notification_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    alert_id BIGINT NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    notification_status VARCHAR(20) NOT NULL DEFAULT 'UNREAD',
    delivered_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (notification_id),
    KEY idx_notifications_status (notification_status),
    KEY idx_notifications_user (user_id),
    CONSTRAINT fk_notification_alert FOREIGN KEY (alert_id) REFERENCES alerts (alert_id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE reports (
    report_id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    report_name VARCHAR(255) NOT NULL,
    report_type VARCHAR(50) NOT NULL,
    generated_by BIGINT NOT NULL,
    generated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    report_file TEXT NULL,
    PRIMARY KEY (report_id),
    CONSTRAINT fk_report_project FOREIGN KEY (project_id) REFERENCES projects (project_id),
    CONSTRAINT fk_report_user FOREIGN KEY (generated_by) REFERENCES users (user_id)
) ENGINE=InnoDB;

CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES roles (role_id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permissions (permission_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE OR REPLACE VIEW active_alerts AS
SELECT a.alert_id, a.alert_type, a.alert_status, i.title, i.severity, i.status
FROM alerts a
JOIN incidents i ON a.incident_id = i.incident_id
WHERE a.alert_status = 'PENDING';

CREATE OR REPLACE VIEW camera_dashboard AS
SELECT c.camera_id, c.camera_name, c.camera_status, p.project_name,
       COUNT(d.detection_id) AS total_detections
FROM cameras c
LEFT JOIN detections d ON c.camera_id = d.camera_id
JOIN projects p ON c.project_id = p.project_id
GROUP BY c.camera_id, c.camera_name, c.camera_status, p.project_name;

CREATE OR REPLACE VIEW daily_detection_statistics AS
SELECT DATE(detected_at) AS detection_date, COUNT(*) AS total_detections
FROM detections
GROUP BY DATE(detected_at)
ORDER BY detection_date;

CREATE OR REPLACE VIEW incident_summary AS
SELECT i.incident_id, i.title, i.severity, i.status, c.camera_name, p.project_name,
       CONCAT_WS(' ', u.first_name, u.last_name) AS assigned_user, i.occurred_at
FROM incidents i
JOIN cameras c ON i.camera_id = c.camera_id
JOIN projects p ON i.project_id = p.project_id
LEFT JOIN users u ON i.assigned_to = u.user_id;

CREATE OR REPLACE VIEW project_statistics AS
SELECT p.project_id, p.project_name,
       COUNT(DISTINCT c.camera_id) AS cameras,
       COUNT(DISTINCT i.incident_id) AS incidents,
       COUNT(DISTINCT d.detection_id) AS detections
FROM projects p
LEFT JOIN cameras c ON p.project_id = c.project_id
LEFT JOIN incidents i ON p.project_id = i.project_id
LEFT JOIN detections d ON c.camera_id = d.camera_id
GROUP BY p.project_id, p.project_name;
