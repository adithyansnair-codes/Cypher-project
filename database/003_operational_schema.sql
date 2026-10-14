-- ==========================================
-- CYPHER Operational Schema
-- MySQL 8.4
-- ==========================================

CREATE TABLE IF NOT EXISTS projects (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cameras (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    location VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    project_id BIGINT NOT NULL,

    CONSTRAINT fk_camera_project
        FOREIGN KEY (project_id)
        REFERENCES projects(id)
        ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_cameras_project
    ON cameras(project_id);

CREATE TABLE IF NOT EXISTS incidents (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,

    incident_type VARCHAR(50) NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',

    asset_type VARCHAR(100) NOT NULL,
    confidence DOUBLE NOT NULL,
    summary TEXT NOT NULL,

    camera_id BIGINT NOT NULL,

    snapshot_url VARCHAR(500),

    bounding_box JSON,
    metadata JSON,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,

    CONSTRAINT fk_incident_camera
        FOREIGN KEY (camera_id)
        REFERENCES cameras(id)
        ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_incidents_camera
    ON incidents(camera_id);

CREATE INDEX idx_incidents_status
    ON incidents(status);

CREATE INDEX idx_incidents_priority
    ON incidents(priority);

CREATE INDEX idx_incidents_created
    ON incidents(created_at);

CREATE TABLE IF NOT EXISTS detections (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,

    camera_id BIGINT NOT NULL,

    asset_type VARCHAR(100) NOT NULL,
    confidence DOUBLE NOT NULL,

    bounding_box JSON,
    metadata JSON,

    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_detection_camera
        FOREIGN KEY (camera_id)
        REFERENCES cameras(id)
        ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_detections_camera
    ON detections(camera_id);

CREATE INDEX idx_detections_detected_at
    ON detections(detected_at);


-- ==========================================
-- Seed project
-- ==========================================

INSERT INTO projects (name)
SELECT 'CYPHER Demo Operations'
WHERE NOT EXISTS (
    SELECT 1 FROM projects WHERE name = 'CYPHER Demo Operations'
);


-- ==========================================
-- Seed cameras
-- ==========================================

INSERT INTO cameras (name, location, active, project_id)
SELECT
    'CAM-01',
    'Main Entrance',
    TRUE,
    p.id
FROM projects p
WHERE p.name = 'CYPHER Demo Operations'
  AND NOT EXISTS (
      SELECT 1 FROM cameras WHERE name = 'CAM-01'
  );

INSERT INTO cameras (name, location, active, project_id)
SELECT
    'CAM-02',
    'Parking Area',
    TRUE,
    p.id
FROM projects p
WHERE p.name = 'CYPHER Demo Operations'
  AND NOT EXISTS (
      SELECT 1 FROM cameras WHERE name = 'CAM-02'
  );

INSERT INTO cameras (name, location, active, project_id)
SELECT
    'CAM-03',
    'Server Room',
    TRUE,
    p.id
FROM projects p
WHERE p.name = 'CYPHER Demo Operations'
  AND NOT EXISTS (
      SELECT 1 FROM cameras WHERE name = 'CAM-03'
  );
