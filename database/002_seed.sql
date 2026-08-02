-- ==========================================
-- CYPHER Sample Data
-- ==========================================

INSERT INTO users
(full_name,email,password_hash,phone,status)
VALUES
('Harsh Singh',
'harsh@example.com',
'$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx',
'9876543210',
'ACTIVE'),

('Satyanarayanan Sai',
'satya@example.com',
'$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx',
'9999999999',
'ACTIVE'),

('Aditya S Nair',
'aditya@example.com',
'$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx',
'8888888888',
'ACTIVE');

INSERT INTO notifications
(user_id,title,message)
VALUES
(1,'Welcome','Welcome to CYPHER!');

INSERT INTO ai_recommendations
(user_id,recommendation,confidence_score)
VALUES
(1,'Enable Two Factor Authentication',96.40);

INSERT INTO login_history
(user_id,ip_address,device_info,login_status)
VALUES
(1,'192.168.1.10','Windows 11 Chrome','SUCCESS');

INSERT INTO audit_logs
(user_id,action,details)
VALUES
(1,'LOGIN','User logged into system.');

INSERT INTO system_logs
(log_level,source,message)
VALUES
('INFO','Spring Boot','Application Started');