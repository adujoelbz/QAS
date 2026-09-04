"""Train regularized local models from the backend training-data CSV export.

The exported data is usually small, so this script favors regularized models,
chronological features, and honest validation metrics over a high-variance fit.
"""
from pathlib import Path
import argparse
import json

import joblib
import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression, Ridge
from sklearn.metrics import accuracy_score, balanced_accuracy_score, mean_absolute_error, roc_auc_score
from sklearn.model_selection import StratifiedKFold, KFold, cross_val_predict
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

BASE_DIR = Path(__file__).resolve().parent
MODEL_DIR = BASE_DIR / "models"
NO_SHOW_FEATURES = [
    "appointment_hour", "hour_sin", "hour_cos", "weekday_sin", "weekday_cos",
    "queue_position", "emergency", "reminder_sent", "previous_no_shows",
]
WAIT_FEATURES = [
    "queue_position", "patients_ahead", "average_consultation_duration",
    "current_queue_length", "hour_sin", "hour_cos",
]


def number(frame, column, default=0):
    values = frame[column] if column in frame else pd.Series(default, index=frame.index)
    return pd.to_numeric(values, errors="coerce").fillna(default)


def bool_number(frame, column):
    values = frame[column] if column in frame else pd.Series(False, index=frame.index)
    if pd.api.types.is_bool_dtype(values):
        return values.astype(int)
    parsed = values.astype(str).str.strip().str.lower().map({
        "true": 1, "false": 0, "1": 1, "0": 0,
        "yes": 1, "no": 0, "y": 1, "n": 0,
        "t": 1, "f": 0,
    })
    numeric = pd.to_numeric(values, errors="coerce")
    return parsed.fillna(numeric).fillna(0).clip(0, 1).astype(int)


def feature_frame(frame):
    values = frame.copy()
    values["appointment_hour"] = number(values, "appointment_hour", 12).clip(0, 23)
    values["hour_sin"] = np.sin(2 * np.pi * values["appointment_hour"] / 24)
    values["hour_cos"] = np.cos(2 * np.pi * values["appointment_hour"] / 24)
    weekday_values = values["day_of_week"] if "day_of_week" in values else pd.Series("MONDAY", index=values.index)
    weekday = weekday_values.astype(str).str.upper().map({
        "MONDAY": 0, "TUESDAY": 1, "WEDNESDAY": 2, "THURSDAY": 3,
        "FRIDAY": 4, "SATURDAY": 5, "SUNDAY": 6,
    }).fillna(0)
    values["weekday_sin"] = np.sin(2 * np.pi * weekday / 7)
    values["weekday_cos"] = np.cos(2 * np.pi * weekday / 7)
    values["queue_position"] = number(values, "queue_position", 0).clip(0, 1000)
    values["emergency"] = bool_number(values, "emergency")
    values["reminder_sent"] = bool_number(values, "reminder_sent")
    values["previous_no_shows"] = number(values, "previous_no_shows", 0).clip(0, 20)
    values["patients_ahead"] = number(values, "patients_ahead", 0).clip(0, 1000)
    values["average_consultation_duration"] = number(values, "average_consultation_duration", 30).clip(5, 240)
    values["current_queue_length"] = number(values, "current_queue_length", 0).clip(0, 1000)
    return values


def add_chronological_history(data):
    data = data.copy()
    if "no_show" not in data:
        raise ValueError("Training CSV must contain a no_show column")
    data["appointment_date"] = pd.to_datetime(
        data["appointment_date"] if "appointment_date" in data else pd.NaT,
        errors="coerce",
    )
    data["no_show"] = bool_number(data, "no_show")
    data["appointment_hour"] = number(data, "appointment_hour", 12).clip(0, 23)
    data["previous_no_shows"] = 0
    if "patient_id" in data:
        ordered = data.sort_values(
            ["patient_id", "appointment_date", "appointment_hour"],
            na_position="first",
        )
        data.loc[ordered.index, "previous_no_shows"] = ordered.groupby("patient_id")["no_show"].cumsum().sub(ordered["no_show"]).clip(lower=0)
    return data


def train(csv_path):
    data = add_chronological_history(pd.read_csv(csv_path))
    required = {"actual_wait_minutes"}
    missing = sorted(required - set(data.columns))
    if missing:
        raise ValueError(f"Training CSV is missing required columns: {', '.join(missing)}")
    no_show_x = feature_frame(data)[NO_SHOW_FEATURES]
    no_show_y = data["no_show"]
    if no_show_y.nunique() < 2:
        raise ValueError("No-show training data must contain both show and no-show outcomes")

    no_show_model = Pipeline([
        ("scale", StandardScaler()),
        ("model", LogisticRegression(max_iter=2000, class_weight="balanced", C=0.5)),
    ])
    no_show_model.fit(no_show_x, no_show_y)
    positive_count = int(no_show_y.sum())
    metrics = {
        "artifactVersion": 2,
        "rows": int(len(data)),
        "noShowRows": int(len(data)),
        "noShowPositiveCount": positive_count,
        "noShowNegativeCount": int(len(data) - positive_count),
        "noShowPositiveRate": round(float(no_show_y.mean()), 4),
    }
    folds = min(5, int(no_show_y.value_counts().min()))
    if folds >= 2:
        cv = StratifiedKFold(n_splits=folds, shuffle=True, random_state=42)
        predicted = cross_val_predict(no_show_model, no_show_x, no_show_y, cv=cv, method="predict")
        probabilities = cross_val_predict(no_show_model, no_show_x, no_show_y, cv=cv, method="predict_proba")[:, 1]
        metrics.update({
            "noShowCvAccuracy": round(float(accuracy_score(no_show_y, predicted)), 4),
            "noShowCvBalancedAccuracy": round(float(balanced_accuracy_score(no_show_y, predicted)), 4),
            "noShowCvAuc": round(float(roc_auc_score(no_show_y, probabilities)), 4),
            "noShowCvFolds": folds,
        })
    else:
        metrics["noShowValidationWarning"] = "At least two examples of each outcome are required for cross-validation"

    completed = data[data["actual_wait_minutes"].notna()].copy()
    if len(completed) < 2:
        raise ValueError("At least two rows with actual_wait_minutes are required")
    completed["patients_ahead"] = number(completed, "queue_position", 1).sub(1).clip(lower=0)
    completed["average_consultation_duration"] = number(completed, "consultation_duration_minutes", 30).clip(5, 240)
    completed["current_queue_length"] = number(completed, "queue_position", 1).clip(lower=0)
    wait_x = feature_frame(completed)[WAIT_FEATURES]
    wait_y = number(completed, "actual_wait_minutes", 0).clip(lower=0)
    wait_model = Pipeline([( "scale", StandardScaler()), ("model", Ridge(alpha=2.0))])
    wait_model.fit(wait_x, wait_y)
    metrics.update({"waitRows": int(len(completed))})
    wait_folds = min(5, len(completed))
    if wait_folds >= 2:
        cv = KFold(n_splits=wait_folds, shuffle=True, random_state=42)
        wait_pred = cross_val_predict(wait_model, wait_x, wait_y, cv=cv)
        metrics["waitCvMae"] = round(float(mean_absolute_error(wait_y, np.maximum(0, wait_pred))), 4)
        metrics["waitCvFolds"] = wait_folds
    metrics["lowConfidence"] = bool(len(data) < 100 or positive_count < 10 or len(completed) < 30)
    if metrics["lowConfidence"]:
        metrics["confidenceWarning"] = "Collect more historical appointments before relying on model metrics"

    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    joblib.dump({"model": no_show_model, "features": NO_SHOW_FEATURES, "version": 2}, MODEL_DIR / "no_show.joblib")
    joblib.dump({"model": wait_model, "features": WAIT_FEATURES, "version": 2}, MODEL_DIR / "wait_time.joblib")
    with (MODEL_DIR / "metrics.json").open("w", encoding="utf-8") as metrics_file:
        json.dump(metrics, metrics_file, indent=2)
        metrics_file.write("\n")
    print(json.dumps(metrics, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("csv", type=Path, help="Training-data CSV exported by the Spring backend")
    train(parser.parse_args().csv)
