-- Crowd Density Monitoring System - Seed Data
-- Roles and cameras only. Users are seeded by DataInitializer.java on startup.

USE crowd_monitoring;

-- Insert Roles
INSERT INTO roles (name, description) VALUES
('ADMIN',    'Full system access'),
('OPERATOR', 'Camera management and monitoring'),
('VIEWER',   'View-only access')
ON DUPLICATE KEY UPDATE description = VALUES(description);

-- Insert Sample Cameras (inserted after users exist via DataInitializer)
-- These are inserted by DataInitializer.java which runs after users are created.
