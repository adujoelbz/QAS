-- ============================================================
-- Queueless AI Appointment System - Initial Schema (PostgreSQL)
-- Version: 1.0
-- ============================================================

-- ----------------------
-- 1. ENUM Types
-- ----------------------

CREATE TYPE user_role AS ENUM ('PATIENT', 'DOCTOR', 'ADMIN');

CREATE TYPE appointment_status AS ENUM (
    'PENDING',          -- awaiting admin approval
    'APPROVED',         -- admin approved, awaiting patient confirmation
    'CONFIRMED',        -- patient confirmed, active in queue
    'CANCELLED',        -- cancelled by patient or doctor
    'COMPLETED',        -- consultation finished
    'NO_SHOW',          -- patient didn't show up
    'REJECTED'          -- admin rejected the request
    );

CREATE TYPE notification_type AS ENUM ('EMAIL', 'SMS');

CREATE TYPE notification_status AS ENUM ('PENDING', 'SENT', 'FAILED');

CREATE TYPE prediction_type AS ENUM ('NO_SHOW', 'WAIT_TIME', 'SLOT_RECOMMENDATION');

-- ----------------------
-- 2. Core Tables
-- ----------------------

-- Users (base table for all roles)
CREATE TABLE users (
                       id                BIGSERIAL PRIMARY KEY,
                       email             VARCHAR(255) NOT NULL UNIQUE,
                       password_hash     VARCHAR(255) NOT NULL,
                       role              user_role NOT NULL,
                       enabled           BOOLEAN NOT NULL DEFAULT TRUE,
                       locked            BOOLEAN NOT NULL DEFAULT FALSE,
                       created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Hospitals
CREATE TABLE hospitals (
                           id                BIGSERIAL PRIMARY KEY,
                           name              VARCHAR(255) NOT NULL,
                           address           TEXT,
                           latitude          DECIMAL(10, 8),
                           longitude         DECIMAL(11, 8),
                           phone             VARCHAR(50),
                           email             VARCHAR(255),
                           created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Departments (belong to a hospital, represent a specialty)
CREATE TABLE departments (
                             id                BIGSERIAL PRIMARY KEY,
                             hospital_id       BIGINT NOT NULL REFERENCES hospitals(id) ON DELETE CASCADE,
                             name              VARCHAR(255) NOT NULL,
                             specialty         VARCHAR(255) NOT NULL,  -- e.g., Cardiology, Pediatrics
                             estimated_consultation_duration_minutes INT NOT NULL DEFAULT 30,
                             created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Patients (extends users)
CREATE TABLE patients (
                          id                BIGSERIAL PRIMARY KEY,
                          user_id           BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
                          first_name        VARCHAR(255) NOT NULL,
                          last_name         VARCHAR(255) NOT NULL,
                          phone             VARCHAR(50) NOT NULL,
                          date_of_birth     DATE,
                          gender            VARCHAR(20),
                          address           TEXT,
    -- Store medical history as JSON (flexible, searchable via GIN indexes if needed)
                          medical_history   JSONB,
                          created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Doctors (extends users)
CREATE TABLE doctors (
                         id                BIGSERIAL PRIMARY KEY,
                         user_id           BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
                         first_name        VARCHAR(255) NOT NULL,
                         last_name         VARCHAR(255) NOT NULL,
                         specialty         VARCHAR(255) NOT NULL,
                         hospital_id       BIGINT NOT NULL REFERENCES hospitals(id) ON DELETE RESTRICT,
                         consultation_duration_minutes INT NOT NULL DEFAULT 30,
    -- JSON array of available weekdays (e.g., ["MONDAY","WEDNESDAY"]) – but detailed schedule is in doctor_availabilities
                         available_days    JSONB,
                         created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Doctor detailed availability (specific time slots per weekday)
CREATE TABLE doctor_availabilities (
                                       id                BIGSERIAL PRIMARY KEY,
                                       doctor_id         BIGINT NOT NULL REFERENCES doctors(id) ON DELETE CASCADE,
                                       day_of_week       VARCHAR(20) NOT NULL CHECK (day_of_week IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
                                       start_time        TIME NOT NULL,
                                       end_time          TIME NOT NULL,
                                       slot_duration_minutes INT,  -- if NULL, uses doctor's default consultation_duration_minutes
                                       created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Appointments – the heart of the system
CREATE TABLE appointments (
                              id                                BIGSERIAL PRIMARY KEY,
                              patient_id                        BIGINT NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
                              department_id                     BIGINT NOT NULL REFERENCES departments(id) ON DELETE RESTRICT,
                              doctor_id                         BIGINT REFERENCES doctors(id) ON DELETE SET NULL,  -- assigned after approval
                              requested_date                    DATE NOT NULL,
                              requested_time                    TIME NOT NULL,
                              status                            appointment_status NOT NULL DEFAULT 'PENDING',
                              reason                            TEXT,
                              emergency_flag                    BOOLEAN NOT NULL DEFAULT FALSE,
    -- Queue & timing
                              queue_position                    INT,                              -- computed/updated periodically
                              estimated_wait_time_minutes       INT,                              -- AI-assisted prediction
                              actual_wait_time_minutes          INT,                              -- can be filled after completion
    -- Metadata
                              created_at                        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at                        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              confirmed_at                      TIMESTAMPTZ,                      -- when patient confirmed
                              cancelled_at                      TIMESTAMPTZ,
                              completed_at                      TIMESTAMPTZ
);

-- Notifications (email/SMS logs)
CREATE TABLE notifications (
                               id                BIGSERIAL PRIMARY KEY,
                               appointment_id    BIGINT NOT NULL REFERENCES appointments(id) ON DELETE CASCADE,
                               recipient         VARCHAR(255) NOT NULL,   -- email or phone number
                               type              notification_type NOT NULL,
                               subject           VARCHAR(255),
                               content           TEXT,
                               status            notification_status NOT NULL DEFAULT 'PENDING',
                               sent_at           TIMESTAMPTZ,
                               error_message     TEXT,
                               created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Audit logs (admin actions)
CREATE TABLE audit_logs (
                            id                BIGSERIAL PRIMARY KEY,
                            admin_id          BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
                            action            VARCHAR(255) NOT NULL,
                            details           JSONB,                     -- flexible details like affected entity IDs
                            ip_address        INET,
                            created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- AI prediction logs (for monitoring and retraining)
CREATE TABLE ai_prediction_logs (
                                    id                BIGSERIAL PRIMARY KEY,
                                    appointment_id    BIGINT REFERENCES appointments(id) ON DELETE SET NULL,
                                    prediction_type   prediction_type NOT NULL,
                                    input_features    JSONB NOT NULL,            -- snapshot of features used
                                    prediction_result JSONB NOT NULL,            -- e.g., probability, recommended slots
                                    actual_outcome    JSONB,                     -- for feedback (e.g., actual no-show, actual wait time)
                                    model_version     VARCHAR(50),
                                    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ----------------------
-- 3. Indexes (performance)
-- ----------------------

-- Users
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

-- Departments
CREATE INDEX idx_departments_hospital_id ON departments(hospital_id);
CREATE INDEX idx_departments_specialty ON departments(specialty);

-- Doctors
CREATE INDEX idx_doctors_hospital_id ON doctors(hospital_id);
CREATE INDEX idx_doctors_specialty ON doctors(specialty);

-- Doctor availabilities
CREATE INDEX idx_doctor_availabilities_doctor_id ON doctor_availabilities(doctor_id);

-- Appointments – critical for queue queries
CREATE INDEX idx_appointments_patient_id ON appointments(patient_id);
CREATE INDEX idx_appointments_doctor_id ON appointments(doctor_id);
CREATE INDEX idx_appointments_department_id ON appointments(department_id);
CREATE INDEX idx_appointments_status ON appointments(status);
CREATE INDEX idx_appointments_requested_date ON appointments(requested_date);
CREATE INDEX idx_appointments_emergency_flag ON appointments(emergency_flag);
-- Composite index for queue ordering (status + emergency + requested_date + requested_time)
CREATE INDEX idx_appointments_queue_order ON appointments(status, emergency_flag DESC, requested_date, requested_time)
    WHERE status IN ('APPROVED', 'CONFIRMED');

-- Notifications
CREATE INDEX idx_notifications_appointment_id ON notifications(appointment_id);
CREATE INDEX idx_notifications_status ON notifications(status);

-- Audit logs
CREATE INDEX idx_audit_logs_admin_id ON audit_logs(admin_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);

-- AI logs
CREATE INDEX idx_ai_prediction_logs_appointment_id ON ai_prediction_logs(appointment_id);

-- ----------------------
-- 4. Automatic updated_at triggers (optional but recommended)
-- ----------------------

-- Function to update updated_at column
CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to all tables with updated_at
CREATE TRIGGER trigger_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trigger_hospitals_updated_at BEFORE UPDATE ON hospitals FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trigger_departments_updated_at BEFORE UPDATE ON departments FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trigger_patients_updated_at BEFORE UPDATE ON patients FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trigger_doctors_updated_at BEFORE UPDATE ON doctors FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trigger_doctor_availabilities_updated_at BEFORE UPDATE ON doctor_availabilities FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trigger_appointments_updated_at BEFORE UPDATE ON appointments FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trigger_notifications_updated_at BEFORE UPDATE ON notifications FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- (Optional) For tables that don't have updated_at, skip.

-- ----------------------
-- 5. Comments (documentation)
-- ----------------------

COMMENT ON TABLE users IS 'Base user table for all roles (patient, doctor, admin)';
COMMENT ON TABLE patients IS 'Patient-specific data, extends users';
COMMENT ON TABLE doctors IS 'Doctor-specific data, extends users';
COMMENT ON TABLE departments IS 'Hospital departments grouped by specialty';
COMMENT ON TABLE appointments IS 'Core appointment records with queue and AI prediction fields';
COMMENT ON TABLE ai_prediction_logs IS 'Audit trail for AI predictions to monitor performance and retrain models';