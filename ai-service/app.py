from flask import Flask, request, jsonify
from flask_cors import CORS
import datetime
import math
import os
from pathlib import Path

import joblib
import pandas as pd

app = Flask(__name__)
CORS(app)
MODEL_DIR = Path(__file__).resolve().parent / "models"


def load_model(name):
    path = MODEL_DIR / name
    try:
        return joblib.load(path) if path.exists() else None
    except Exception:
        return None


NO_SHOW_MODEL = load_model("no_show.joblib")
WAIT_MODEL = load_model("wait_time.joblib")

def payload():
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        raise ValueError("JSON object is required")
    return data

def bounded_number(value, default, minimum, maximum):
    try:
        return max(minimum, min(maximum, float(value)))
    except (TypeError, ValueError):
        return default


def boolean_value(value, default=False):
    """Parse JSON booleans and common CSV/form string representations."""
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return value != 0
    normalized = str(value).strip().lower()
    if normalized in {"true", "1", "yes", "y", "on"}:
        return True
    if normalized in {"false", "0", "no", "n", "off", ""}:
        return False
    return default


def model_features(data, feature_names):
    """Build the same feature order used by train_models.py.

    Keeping this explicit also lets older model artifacts continue to serve
    requests while a newly trained v2 artifact is deployed.
    """
    try:
        hour = max(0, min(23, int(str(data.get('appointmentTime', '12:00'))[:2])))
    except (TypeError, ValueError):
        hour = 12
    weekdays = {name: index for index, name in enumerate(("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"))}
    weekday = weekdays.get(str(data.get('dayOfWeek', 'MONDAY')).upper(), 0)
    values = {
        'patient_id': bounded_number(data.get('patientId'), -1, -1, 100000000),
        'doctor_id': bounded_number(data.get('doctorId'), -1, -1, 100000000),
        'appointment_hour': hour,
        'hour_sin': math.sin(2 * math.pi * hour / 24),
        'hour_cos': math.cos(2 * math.pi * hour / 24),
        'weekday_sin': math.sin(2 * math.pi * weekday / 7),
        'weekday_cos': math.cos(2 * math.pi * weekday / 7),
        'queue_position': bounded_number(data.get('queuePosition'), 0, 0, 1000),
        'emergency': 1 if boolean_value(data.get('emergencyFlag'), False) else 0,
        'reminder_sent': 1 if boolean_value(data.get('reminderSent'), False) else 0,
        'previous_no_shows': bounded_number(data.get('previousNoShows'), 0, 0, 20),
        'patients_ahead': bounded_number(data.get('patientsAhead'), 0, 0, 1000),
        'average_consultation_duration': bounded_number(data.get('averageConsultationDuration'), 30, 5, 240),
        'current_queue_length': bounded_number(data.get('currentQueueLength'), 0, 0, 1000),
    }
    return pd.DataFrame([[values.get(name, 0) for name in feature_names]], columns=feature_names)

@app.route('/health', methods=['GET'])
def health():
    return jsonify({
        "status": "healthy",
        "service": "AI Service",
        "models": {
            "noShow": NO_SHOW_MODEL is not None,
            "noShowVersion": NO_SHOW_MODEL.get('version', 1) if NO_SHOW_MODEL else None,
            "waitTime": WAIT_MODEL is not None,
            "waitTimeVersion": WAIT_MODEL.get('version', 1) if WAIT_MODEL else None,
        },
    })

@app.route('/recommend-slots', methods=['POST'])
def recommend_slots():
    try:
        data = payload()
        preferred_date = data.get('preferredDate')
        if not preferred_date:
            raise ValueError("preferredDate is required")
        duration = int(bounded_number(data.get('slotDurationMinutes'), 30, 15, 180))
        queue_length = int(bounded_number(data.get('queueLength'), 0, 0, 1000))
        preferred = data.get('preferredTime')
        preferred_minutes = None
        if preferred:
            preferred_minutes = datetime.datetime.strptime(preferred[:5], "%H:%M").hour * 60 + datetime.datetime.strptime(preferred[:5], "%H:%M").minute
        slots = []
        current = datetime.datetime.combine(datetime.date.today(), datetime.time(8, 0))
        end = datetime.datetime.combine(datetime.date.today(), datetime.time(17, 0))
        while current + datetime.timedelta(minutes=duration) <= end:
            minutes = current.hour * 60 + current.minute
            distance = abs(minutes - preferred_minutes) if preferred_minutes is not None else 0
            wait = queue_length * duration + max(0, (minutes - 8 * 60) // 60 * 2)
            confidence = max(0.55, min(0.95, 0.92 - queue_length * 0.01 - distance / 2400))
            slots.append({"date": preferred_date, "time": current.strftime("%H:%M"),
                          "estimatedWaitMinutes": wait, "confidence": round(confidence, 2),
                          "reason": "Ranked by queue load and proximity to the preferred time"})
            current += datetime.timedelta(minutes=duration)
        slots.sort(key=lambda slot: (abs((int(slot['time'][:2]) * 60 + int(slot['time'][3:])) - preferred_minutes) if preferred_minutes is not None else 0, slot['estimatedWaitMinutes']))
        return jsonify({"slots": slots[:5]})
    except (ValueError, TypeError) as error:
        return jsonify({"error": str(error)}), 400

@app.route('/predict-no-show', methods=['POST'])
def predict_no_show():
    try:
        data = payload()
        hour = int(str(data.get('appointmentTime', '12:00'))[:2])
        previous = int(bounded_number(data.get('previousNoShows'), 0, 0, 20))
        reminder_sent = boolean_value(data.get('reminderSent'), False)
        probability = 0.03 + min(previous * 0.04, 0.24)
        if hour < 9: probability -= 0.01
        if hour >= 16: probability += 0.06
        if not reminder_sent: probability += 0.03
        probability = max(0.01, min(0.45, probability))
    except (ValueError, TypeError) as error:
        return jsonify({"error": str(error)}), 400

    if NO_SHOW_MODEL:
        try:
            names = NO_SHOW_MODEL.get("features", ["patient_id", "doctor_id", "appointment_hour", "queue_position", "emergency", "reminder_sent"])
            features = model_features(data, names)
            probability = float(NO_SHOW_MODEL["model"].predict_proba(features)[0][1])
            probability = max(0.01, min(0.99, probability))
        except (KeyError, TypeError, ValueError, IndexError) as error:
            app.logger.warning("No-show model inference failed; using heuristic: %s", error)

    risk_level = "HIGH" if probability > 0.15 else "MEDIUM" if probability > 0.07 else "LOW"
    recommendation = "Send reminder and confirmation request" if risk_level == "HIGH" else "Send standard reminder" if risk_level == "MEDIUM" else "Normal"

    return jsonify({
        "probability": round(probability, 3),
        "riskLevel": risk_level,
        "recommendation": recommendation
    })

@app.route('/predict-wait-time', methods=['POST'])
def predict_wait_time():
    try:
        data = payload()
        patients_ahead = int(bounded_number(data.get('patientsAhead'), 0, 0, 1000))
        avg_duration = int(bounded_number(data.get('averageConsultationDuration'), 30, 5, 240))
        queue_length = int(bounded_number(data.get('currentQueueLength'), 0, 0, 1000))
        durations = data.get('previousAppointmentDurations') or []
        valid_durations = [float(v) for v in durations if isinstance(v, (int, float)) and 5 <= v <= 240]
        if valid_durations:
            avg_duration = round((avg_duration + sum(valid_durations) / len(valid_durations)) / 2)
        wait = patients_ahead * avg_duration
        confidence = max(50, min(95, 92 - queue_length * 0.4 - (10 if not valid_durations else 0)))
    except (ValueError, TypeError) as error:
        return jsonify({"error": str(error)}), 400

    if WAIT_MODEL:
        try:
            names = WAIT_MODEL.get("features", ["queue_position", "patients_ahead", "average_consultation_duration", "current_queue_length"])
            features = model_features({**data, "appointmentTime": data.get("time", "12:00"), "queuePosition": max(1, patients_ahead + 1), "patientsAhead": patients_ahead, "averageConsultationDuration": avg_duration, "currentQueueLength": queue_length}, names)
            wait = max(0, int(round(float(WAIT_MODEL["model"].predict(features)[0]))))
            confidence = max(50, min(95, confidence + 5))
        except (KeyError, TypeError, ValueError, IndexError) as error:
            app.logger.warning("Wait-time model inference failed; using heuristic: %s", error)

    return jsonify({
        "predictedWaitMinutes": int(wait),
        "confidence": int(confidence)
    })

if __name__ == '__main__':
    port = int(os.environ.get('PORT', os.environ.get('AI_PORT', 5000)))
    app.run(host='0.0.0.0', port=port, debug=True)
