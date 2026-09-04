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

## Deploy the backend to Railway

Create a Railway project with two services: a PostgreSQL database and this repository. Railway detects `railway.toml`, builds the Spring Boot jar with Maven, and starts it on Railway's assigned `PORT`.

In the backend service Variables tab, add:

```text
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:${{Postgres.DATABASE_URL}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}
JWT_SECRET=<a-random-secret-at-least-32-characters>
CORS_ALLOWED_ORIGINS=https://<your-frontend-domain>
APP_BASE_URL=https://<your-backend-domain>
AI_ENABLED=false
```

Spring profile files are named `application-dev.yaml` and `application-prod.yaml`. Run locally with `SPRING_PROFILES_ACTIVE=dev`; Railway must use `SPRING_PROFILES_ACTIVE=prod`. The production profile intentionally requires `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, and `APP_BASE_URL` so a deployment cannot silently start with localhost or development values.

Set these variables to enable SendGrid email delivery and medical-history uploads:

```text
SENDGRID_ENABLED=true
SENDGRID_API_KEY=<sendgrid api key>
MAIL_FROM=<verified sendgrid sender address>
CLOUDINARY_CLOUD_NAME=<cloud name>
CLOUDINARY_API_KEY=<api key>
CLOUDINARY_API_SECRET=<api secret>
```

The sender address must be verified in SendGrid under **Sender Authentication**. SendGrid accepts the API request with HTTP `202`; delivery failures are recorded in the notifications table and can be retried through the admin notification retry endpoint.

Keep `SPRING_DATASOURCE_URL` on one physical line with no spaces or line breaks. It should resolve to a value like `jdbc:postgresql://postgres.railway.internal:5432/railway`. If you use the component fallback instead, set `PGHOST`, `PGPORT`, and `PGDATABASE` as separate variables; do not paste a wrapped or multi-line URL.

After the first deploy, open the generated Railway domain and verify `/api/health` returns `{"status":"UP"}`. Flyway runs the database migrations automatically at startup. Keep `AI_ENABLED=false` unless the Flask AI service is also deployed and `AI_SERVICE_URL` points to its public Railway URL; the backend's deterministic fallback remains available.
