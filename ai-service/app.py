from flask import Flask, request, jsonify
from flask_cors import CORS
import datetime
import os

app = Flask(__name__)
CORS(app)

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

@app.route('/health', methods=['GET'])
def health():
    return jsonify({"status": "healthy", "service": "AI Service"})

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
        reminder_sent = bool(data.get('reminderSent', False))
        probability = 0.03 + min(previous * 0.04, 0.24)
        if hour < 9: probability -= 0.01
        if hour >= 16: probability += 0.06
        if not reminder_sent: probability += 0.03
        probability = max(0.01, min(0.45, probability))
    except (ValueError, TypeError) as error:
        return jsonify({"error": str(error)}), 400

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

    return jsonify({
        "predictedWaitMinutes": int(wait),
        "confidence": int(confidence)
    })

if __name__ == '__main__':
    port = int(os.environ.get('AI_PORT', 5000))
    app.run(host='0.0.0.0', port=port, debug=True)
