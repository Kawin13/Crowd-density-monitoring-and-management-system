-- Crowd Density Monitoring System - Database Schema
-- MySQL 8.0 Compatible

CREATE DATABASE IF NOT EXISTS crowd_monitoring;
USE crowd_monitoring;

-- Roles Table
CREATE TABLE IF NOT EXISTS roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Users Table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(200),
    role_id BIGINT NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    is_email_verified BOOLEAN DEFAULT FALSE,
    reset_token VARCHAR(255),
    reset_token_expiry TIMESTAMP,
    last_login TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (role_id) REFERENCES roles(id)
);

-- Refresh Tokens Table
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    token VARCHAR(512) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Cameras Table
CREATE TABLE IF NOT EXISTS cameras (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    camera_name VARCHAR(200) NOT NULL,
    camera_type ENUM('MOBILE', 'CCTV', 'VIDEO_UPLOAD') NOT NULL,
    location_name VARCHAR(300) NOT NULL,
    maximum_capacity INT NOT NULL DEFAULT 100,
    stream_url VARCHAR(1000),
    status ENUM('ACTIVE', 'INACTIVE', 'MONITORING', 'ERROR') DEFAULT 'INACTIVE',
    description TEXT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id)
);

-- Crowd Data Table
CREATE TABLE IF NOT EXISTS crowd_data (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    camera_id BIGINT NOT NULL,
    people_count INT NOT NULL DEFAULT 0,
    occupancy_percentage DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    crowd_level ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL', 'OVERCROWDED') NOT NULL DEFAULT 'LOW',
    frame_data LONGBLOB,
    heatmap_data LONGBLOB,
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (camera_id) REFERENCES cameras(id) ON DELETE CASCADE,
    INDEX idx_camera_recorded (camera_id, recorded_at),
    INDEX idx_recorded_at (recorded_at),
    INDEX idx_crowd_level (crowd_level)
);

-- Alerts Table
CREATE TABLE IF NOT EXISTS alerts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    camera_id BIGINT NOT NULL,
    crowd_data_id BIGINT,
    alert_type ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL', 'OVERCROWDED') NOT NULL,
    message TEXT NOT NULL,
    people_count INT NOT NULL,
    occupancy_percentage DECIMAL(5,2) NOT NULL,
    is_acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_by BIGINT,
    acknowledged_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (camera_id) REFERENCES cameras(id) ON DELETE CASCADE,
    FOREIGN KEY (crowd_data_id) REFERENCES crowd_data(id),
    FOREIGN KEY (acknowledged_by) REFERENCES users(id),
    INDEX idx_camera_alert (camera_id, created_at),
    INDEX idx_alert_type (alert_type),
    INDEX idx_is_acknowledged (is_acknowledged)
);

-- Reports Table
CREATE TABLE IF NOT EXISTS reports (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_name VARCHAR(300) NOT NULL,
    report_type ENUM('DAILY', 'WEEKLY', 'MONTHLY', 'CUSTOM') NOT NULL,
    format ENUM('PDF', 'EXCEL') NOT NULL,
    camera_id BIGINT,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    file_path VARCHAR(1000),
    generated_by BIGINT,
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (camera_id) REFERENCES cameras(id),
    FOREIGN KEY (generated_by) REFERENCES users(id)
);

-- Audit Logs Table
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    action VARCHAR(200) NOT NULL,
    entity_type VARCHAR(100),
    entity_id BIGINT,
    details TEXT,
    ip_address VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_user_action (user_id, created_at),
    INDEX idx_entity (entity_type, entity_id)
);
