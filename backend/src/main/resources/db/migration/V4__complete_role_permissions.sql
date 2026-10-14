-- ============================================================================
-- V4 : Complete the role_permissions matrix
-- ============================================================================
-- V2 granted SUPER_ADMIN every permission and gave OPERATOR and ANALYST their
-- working sets, but left ADMIN, SECURITY_MANAGER and VIEWER with no rows at all.
-- An administrator therefore had no rights in the permission model, which made
-- the RBAC data misleading: the code and the data disagreed about who may do
-- what. This migration finishes the matrix.
--
-- Idempotent: every insert is guarded, so re-running changes nothing.
-- ============================================================================

-- ---------------------------------------------------------------- ADMIN -----
-- Runs the platform: users, assets, incidents, reports and the audit trail.
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

-- ------------------------------------------------------- SECURITY_MANAGER ---
-- Owns the incident response: reads everything, manages cameras, no user admin.
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

-- --------------------------------------------------------------- VIEWER -----
-- Read-only: can watch the cockpit, cannot touch anything.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
  FROM roles r
  JOIN permissions p ON p.permission_name IN (
       'PROJECT_READ', 'CAMERA_READ', 'INCIDENT_READ')
 WHERE r.role_name = 'VIEWER'
   AND NOT EXISTS (
       SELECT 1 FROM role_permissions rp
        WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id);
