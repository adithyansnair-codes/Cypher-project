-- ==========================================
-- CYPHER Database Schema
-- ==========================================

CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP
);

CREATE TABLE login_history (
    login_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(user_id),
    login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(100),
    device_info TEXT,
    login_status VARCHAR(20)
);

CREATE TABLE ai_recommendations (
    recommendation_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(user_id),
    recommendation TEXT,
    confidence_score DECIMAL(5,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notifications (
    notification_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(user_id),
    title VARCHAR(150),
    message TEXT,
    status VARCHAR(20) DEFAULT 'UNREAD',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE audit_logs (
    log_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(user_id),
    action VARCHAR(150),
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE system_logs (
    log_id BIGSERIAL PRIMARY KEY,
    log_level VARCHAR(20),
    source VARCHAR(100),
    message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email
ON users(email);

CREATE INDEX idx_login_history_user
ON login_history(user_id);

CREATE INDEX idx_notifications_user
ON notifications(user_id);

CREATE INDEX idx_ai_recommendations_user
ON ai_recommendations(user_id);