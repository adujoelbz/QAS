-- ============================================================
-- Version: 3.0
-- Description: Create questions table for patient-doctor Q&A
-- ============================================================

CREATE TABLE questions (
                           id                BIGSERIAL PRIMARY KEY,
                           appointment_id    BIGINT NOT NULL REFERENCES appointments(id) ON DELETE CASCADE,
                           patient_id        BIGINT NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
                           doctor_id         BIGINT NOT NULL REFERENCES doctors(id) ON DELETE CASCADE,
                           question          TEXT NOT NULL,
                           answer            TEXT,
                           answered_at       TIMESTAMPTZ,
                           created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_questions_appointment_id ON questions(appointment_id);
CREATE INDEX idx_questions_patient_id ON questions(patient_id);
CREATE INDEX idx_questions_doctor_id ON questions(doctor_id);

-- Add trigger for updated_at
CREATE TRIGGER trigger_questions_updated_at
    BEFORE UPDATE ON questions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE questions IS 'Questions and answers between patients and doctors for appointments';