from flask import Flask, request, jsonify
from flask_cors import CORS
import random
import datetime
import os

app = Flask(__name__)
CORS(app)

@app.route('/health', methods=['GET'])
def health():
    return jsonify({"status": "healthy", "service": "AI Service"})

@app.route('/recommend-slots', methods=['POST'])
def recommend_slots():
    data = request.json
    preferred_date = data.get('preferredDate')
    slot_duration = data.get('slotDurationMinutes', 30)

    slots = []
    start_hour = 8
    end_hour = 17

    # Generate slots
    for hour in range(start_hour, end_hour):
        for minute in [0, 30]:
            time_obj = datetime.time(hour, minute)
            # Skip if slot is before preferred time (optional)
            slots.append({
                "date": preferred_date,
                "time": time_obj.strftime("%H:%M"),
                "estimatedWaitMinutes": random.randint(5, 30),
                "confidence": round(0.6 + 0.4 * random.random(), 2),
                "reason": "AI suggested slot based on department schedule"
            })

    # Return top 5 slots (or all)
    return jsonify({"slots": slots[:5]})

@app.route('/predict-no-show', methods=['POST'])
def predict_no_show():
    data = request.json
    # In production, use actual ML model
    # For now, use a simple heuristic
    app_time = data.get('appointmentTime')
    if app_time:
        hour = int(app_time.split(':')[0])
        # Early morning: more likely to show
        if hour < 9:
            probability = 0.02 + 0.05 * random.random()
        # Late afternoon: higher no-show
        elif hour > 16:
            probability = 0.08 + 0.12 * random.random()
        else:
            probability = 0.03 + 0.07 * random.random()
    else:
        probability = 0.05 + 0.10 * random.random()

    # Add some randomness
    probability = min(probability + random.uniform(-0.02, 0.04), 0.35)
    probability = max(probability, 0.01)

    risk_level = "HIGH" if probability > 0.15 else "MEDIUM" if probability > 0.07 else "LOW"
    recommendation = "Send reminder and confirmation request" if risk_level == "HIGH" else "Send standard reminder" if risk_level == "MEDIUM" else "Normal"

    return jsonify({
        "probability": round(probability, 3),
        "riskLevel": risk_level,
        "recommendation": recommendation
    })

@app.route('/predict-wait-time', methods=['POST'])
def predict_wait_time():
    data = request.json
    patients_ahead = data.get('patientsAhead', 0)
    avg_duration = data.get('averageConsultationDuration', 30)
    queue_length = data.get('currentQueueLength', 0)

    # Base calculation: patients ahead * average duration
    base_wait = patients_ahead * avg_duration

    # Add some variability
    variance = random.randint(-5, 15)
    wait = max(0, base_wait + variance)

    # Confidence decreases with queue length
    confidence = max(60, 90 - (queue_length * 0.5))
    confidence = min(95, confidence)

    return jsonify({
        "predictedWaitMinutes": int(wait),
        "confidence": int(confidence)
    })

if __name__ == '__main__':
    port = int(os.environ.get('AI_PORT', 5000))
    app.run(host='0.0.0.0', port=port, debug=True)