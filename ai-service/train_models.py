"""Train the local AI models from the backend training-data CSV export."""
from pathlib import Path
import argparse

import joblib
import pandas as pd
from sklearn.linear_model import LogisticRegression, LinearRegression
from sklearn.metrics import accuracy_score, mean_absolute_error

BASE_DIR = Path(__file__).resolve().parent
MODEL_DIR = BASE_DIR / "models"

NO_SHOW_FEATURES = ["patient_id", "doctor_id", "appointment_hour", "queue_position", "emergency", "reminder_sent"]
WAIT_FEATURES = ["queue_position", "patients_ahead", "average_consultation_duration", "current_queue_length"]


def numeric_frame(frame, columns):
    values = frame.reindex(columns=columns).copy()
    for column in columns:
        if column in ("emergency", "reminder_sent"):
            values[column] = values[column].astype(str).str.lower().map({"true": 1, "false": 0}).fillna(0)
        else:
            values[column] = pd.to_numeric(values[column], errors="coerce").fillna(0)
    return values


def train(csv_path):
    data = pd.read_csv(csv_path)
    data["no_show"] = data["no_show"].astype(str).str.lower().eq("true").astype(int)
    data["appointment_hour"] = pd.to_numeric(data["appointment_hour"], errors="coerce").fillna(12)
    data["patient_id"] = pd.to_numeric(data["patient_id"], errors="coerce").fillna(-1)
    data["doctor_id"] = pd.to_numeric(data["doctor_id"], errors="coerce").fillna(-1)

    no_show_x = numeric_frame(data, NO_SHOW_FEATURES)
    no_show_y = data["no_show"]
    if no_show_y.nunique() < 2:
        raise ValueError("No-show training data must contain both show and no-show outcomes")
    no_show_model = LogisticRegression(max_iter=1000, class_weight="balanced").fit(no_show_x, no_show_y)

    completed = data[data["actual_wait_minutes"].notna()].copy()
    if len(completed) < 2:
        raise ValueError("At least two rows with actual_wait_minutes are required")
    completed["patients_ahead"] = pd.to_numeric(completed["queue_position"], errors="coerce").fillna(1).sub(1).clip(lower=0)
    completed["average_consultation_duration"] = pd.to_numeric(completed["consultation_duration_minutes"], errors="coerce").fillna(30)
    completed["current_queue_length"] = pd.to_numeric(completed["queue_position"], errors="coerce").fillna(1)
    wait_x = numeric_frame(completed, WAIT_FEATURES)
    wait_y = pd.to_numeric(completed["actual_wait_minutes"], errors="coerce")
    wait_model = LinearRegression().fit(wait_x, wait_y)

    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    joblib.dump({"model": no_show_model, "features": NO_SHOW_FEATURES}, MODEL_DIR / "no_show.joblib")
    joblib.dump({"model": wait_model, "features": WAIT_FEATURES}, MODEL_DIR / "wait_time.joblib")
    metrics = {
        "rows": int(len(data)),
        "noShowRows": int(len(data)),
        "noShowAccuracy": round(float(accuracy_score(no_show_y, no_show_model.predict(no_show_x))), 4),
        "waitRows": int(len(completed)),
        "waitMeanAbsoluteError": round(float(mean_absolute_error(wait_y, wait_model.predict(wait_x))), 4),
    }
    pd.Series(metrics).to_json(MODEL_DIR / "metrics.json", indent=2)
    print(pd.Series(metrics).to_json(indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("csv", type=Path, help="Training-data CSV exported by the Spring backend")
    args = parser.parse_args()
    train(args.csv)
