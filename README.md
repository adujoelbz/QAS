# QAS

## AI predictions

The local AI service supports no-show risk and estimated wait-time predictions. It loads the joblib artifacts in `ai-service/models` when it starts. The checked-in artifacts are fallback artifacts; retrain them with real appointment history before using their metrics for operational decisions.

### Export training data

While the Spring Boot API is running, an administrator can download a CSV for a date range:

```text
GET http://localhost:8080/api/admin/ai/training-data?from=2026-01-01&to=2026-09-04
```

The endpoint requires an authenticated admin request and returns a file named `training-data-<from>-<to>.csv`.

### Retrain the models

From the `ai-service` directory, install the pinned dependencies once and run:

```powershell
python -m pip install -r requirements.txt
python train_models.py path\to\training-data.csv
```

The command writes `models/no_show.joblib`, `models/wait_time.joblib`, and `models/metrics.json`. Version 2 uses temporal features (appointment hour and weekday), queue state, emergency/reminder flags, and each patient's chronological no-show history. `metrics.json` reports cross-validated accuracy, balanced accuracy, AUC, and wait-time MAE; a `lowConfidence` warning is included when the export is too small for dependable evaluation.

Restart the Flask service after retraining so it reloads the new artifacts. Check `GET http://localhost:5000/health` and confirm both model versions report `2`.
