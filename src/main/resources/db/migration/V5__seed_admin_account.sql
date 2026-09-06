-- Seed the initial production administrator.
-- Change this password after the first successful login.
INSERT INTO users (email, password_hash, role, enabled, locked)
SELECT
    'admin@queueless.com',
    '$2b$12$BpKwqgGk2zEU/PQft8uw8ujNP.KtGGQYxXZT5wOEPGd5QWMRftGui',
    'ADMIN'::user_role,
    TRUE,
    FALSE
WHERE NOT EXISTS (
    SELECT 1
    FROM users
    WHERE email = 'admin@queueless.com'
);
