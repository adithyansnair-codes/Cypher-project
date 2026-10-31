-- Add missing role and permission definitions. Existing users are unchanged.

INSERT INTO roles (role_name, description)
SELECT 'SUPER_ADMIN', 'Full system access' WHERE NOT EXISTS (SELECT 1 FROM roles WHERE role_name = 'SUPER_ADMIN');

INSERT INTO roles (role_name, description)
SELECT 'ADMIN', 'Project administrator' WHERE NOT EXISTS (SELECT 1 FROM roles WHERE role_name = 'ADMIN');

INSERT INTO roles (role_name, description)
SELECT 'SECURITY_MANAGER', 'Security operations manager' WHERE NOT EXISTS (SELECT 1 FROM roles WHERE role_name = 'SECURITY_MANAGER');

INSERT INTO roles (role_name, description)
SELECT 'ANALYST', 'AI detection analyst' WHERE NOT EXISTS (SELECT 1 FROM roles WHERE role_name = 'ANALYST');

INSERT INTO roles (role_name, description)
SELECT 'OPERATOR', 'Camera operator' WHERE NOT EXISTS (SELECT 1 FROM roles WHERE role_name = 'OPERATOR');

INSERT INTO roles (role_name, description)
SELECT 'VIEWER', 'Read-only user' WHERE NOT EXISTS (SELECT 1 FROM roles WHERE role_name = 'VIEWER');

INSERT INTO permissions (permission_name, description)
SELECT 'USER_READ', 'View users' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_name = 'USER_READ');

INSERT INTO permissions (permission_name, description)
SELECT 'USER_WRITE', 'Create and modify users' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_name = 'USER_WRITE');

INSERT INTO permissions (permission_name, description)
SELECT 'PROJECT_READ', 'View projects' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_name = 'PROJECT_READ');

INSERT INTO permissions (permission_name, description)
SELECT 'PROJECT_WRITE', 'Create and modify projects' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_name = 'PROJECT_WRITE');

INSERT INTO permissions (permission_name, description)
SELECT 'CAMERA_READ', 'View cameras' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_name = 'CAMERA_READ');

INSERT INTO permissions (permission_name, description)
SELECT 'CAMERA_WRITE', 'Manage cameras' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_name = 'CAMERA_WRITE');

INSERT INTO permissions (permission_name, description)
SELECT 'INCIDENT_READ', 'View incidents' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_name = 'INCIDENT_READ');

INSERT INTO permissions (permission_name, description)
SELECT 'INCIDENT_WRITE', 'Acknowledge and resolve incidents' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_name = 'INCIDENT_WRITE');

INSERT INTO permissions (permission_name, description)
SELECT 'REPORT_READ', 'View reports' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_name = 'REPORT_READ');

INSERT INTO permissions (permission_name, description)
SELECT 'REPORT_WRITE', 'Generate reports' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_name = 'REPORT_WRITE');

INSERT INTO permissions (permission_name, description)
SELECT 'AUDIT_READ', 'View the audit trail' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_name = 'AUDIT_READ');

INSERT INTO permissions (permission_name, description)
SELECT 'SYSTEM_ADMIN', 'Administer the system' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_name = 'SYSTEM_ADMIN');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
CROSS JOIN permissions p
WHERE r.role_name = 'SUPER_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id
  );

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



INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
  FROM roles r
  JOIN permissions p ON p.permission_name IN (
       'USER_READ', 'USER_WRITE',
       'PROJECT_READ', 'PROJECT_WRITE',
       'CAMERA_READ', 'CAMERA_WRITE',
       'INCIDENT_READ', 'INCIDENT_WRITE',
       'REPORT_READ', 'REPORT_WRITE',
       'AUDIT_READ')
 WHERE r.role_name = 'ADMIN'
   AND NOT EXISTS (
       SELECT 1 FROM role_permissions rp
        WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
  FROM roles r
  JOIN permissions p ON p.permission_name IN (
       'PROJECT_READ',
       'CAMERA_READ', 'CAMERA_WRITE',
       'INCIDENT_READ', 'INCIDENT_WRITE',
       'REPORT_READ',
       'AUDIT_READ')
 WHERE r.role_name = 'SECURITY_MANAGER'
   AND NOT EXISTS (
       SELECT 1 FROM role_permissions rp
        WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
  FROM roles r
  JOIN permissions p ON p.permission_name IN (
       'PROJECT_READ', 'CAMERA_READ', 'INCIDENT_READ')
 WHERE r.role_name = 'VIEWER'
   AND NOT EXISTS (
       SELECT 1 FROM role_permissions rp
        WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id);
