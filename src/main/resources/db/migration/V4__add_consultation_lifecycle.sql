ALTER TABLE appointments
    ADD COLUMN consultation_started_at TIMESTAMPTZ,
    ADD COLUMN consultation_ended_at TIMESTAMPTZ;
