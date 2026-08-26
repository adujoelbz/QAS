ALTER TABLE appointments ADD COLUMN standby_requested BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_appointments_standby ON appointments(department_id, standby_requested, created_at)
    WHERE status = 'PENDING' AND standby_requested = TRUE;
