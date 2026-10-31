# CYPHER

CYPHER is our incident reporting and surveillance project for Cloud Architecture,
Software Engineering, Machine Learning and Embedded Systems at VIT Chennai.
It combines a camera/image detector with a dashboard where an operator can review,
acknowledge and resolve incidents.

## What is implemented

- Login, registration and role-based API access.
- Incident dashboard, camera/project management, analytics and audit history.
- Live updates through WebSockets.
- Image upload and webcam detection using our six-class YOLOv8s model:
  `knife`, `pistol`, `person`, `fire`, `smoke` and `lying`.
- Continuous webcam monitoring with eight-frame confirmation and a 90-second
  cooldown per camera and object class. Uploaded images use manual confirmation.
- CSV, Excel and PDF exports through a separate C# reporting service.
- PostgreSQL support locally and MySQL support for the AWS RDS setup.
- Optional incident evidence uploads to private S3 storage.

A detection is a reason for an operator to review the scene. The model detects
objects; it does not establish who is holding a weapon or whether someone intends
harm. `lying` is a pose class, not proof of a fall. A person detection alone does
not create an incident. See the [model results and limitations](ai/evidence/README.md).

## Stack

| Part | Technology |
|---|---|
| Dashboard | HTML, CSS and JavaScript, served by Spring Boot |
| Backend | Java 21, Spring Boot 4, Spring Security, JPA, Flyway |
| Database | PostgreSQL 16 locally; MySQL 8.4 on RDS |
| ML service | Python, Ultralytics YOLOv8s, OpenCV, FastAPI |
| Reporting | .NET 10, Npgsql/MySqlConnector, ClosedXML, QuestPDF |
| Cloud | EC2, RDS, S3, IAM and scheduled backups |

## Run locally

Install Java 21, Python 3.11+, .NET 10 and PostgreSQL. The Maven wrapper is included.
There is no React app or npm development server to install.

1. Create an empty PostgreSQL database and a database user, or use your existing
   CYPHER database. Do not run the database reset script against data you need.
2. Copy `.env.example` to `.env`. Set the database values and generate a signing
   key with `openssl rand -base64 64`. Put it in `JWT_SECRET`.
3. Create the Python environment and install the ML dependencies:

   ```bash
   python3 -m venv .venv
   .venv/bin/pip install -r ai/requirements.txt
   ```

4. Set `YOLO_WEIGHTS` in `.env` to the trained `best.pt` file. Weights and datasets
   are kept outside Git. Set `PERSON_WEIGHTS` if using the optional COCO person model.
5. Start the services:

   ```bash
   ./scripts/run-local.sh
   ```

Open **http://localhost:8081**. The launcher starts the backend, ML service and
reporting service, and stops them when you press Ctrl+C. PostgreSQL must already
be running. You can also start one service with `./scripts/run-local.sh backend`,
`ai` or `reporting`.

PostgreSQL's demo migration includes `admin@cypher.com` / `Admin@2026`.
These are demo credentials and must be changed before exposing the app publicly.
The MySQL migrations add roles and the schema, but do not add demo accounts.

Docker users can run the backend, database and reporting stack with
`docker compose up -d --build` after setting `.env`. Run the ML service on the host
for access to the webcam. Details: [operations](docs/OPERATIONS.md).

## Project folders

- `backend/src/main/java/` — API, security and incident workflow.
- `backend/src/main/resources/static/` — the dashboard source.
- `frontend/src/` — links to the same dashboard files; no separate build.
- `backend/src/main/resources/db/` — PostgreSQL and MySQL migrations.
- `ai/` — inference, CLI, training scripts, model results and demo images.
- `reporting/` — export service.
- `docs/` — design, API and deployment notes.
- `hardware/` — embedded work, currently outside the software completion scope.

## Checks

Use a separate test database. The backend tests apply migrations and create test data.

```bash
cd backend
DB_HOST=localhost DB_PORT=5432 DB_NAME=cypher_test DB_USER=cypher_test DB_PASSWORD=... ./mvnw verify
```

For MySQL, also set `SPRING_PROFILES_ACTIVE=mysql` and the MySQL connection values.
Use `DB_SSL_MODE=DISABLED` only with a local test server.

```bash
python3 -m unittest discover -s ai/tests -v
node --test scripts/test_detection.mjs
node scripts/check_frontend_js.mjs
dotnet build reporting
```

GitHub Actions checks PostgreSQL 16, MySQL 8.4, reporting, detection behaviour and
builds. [Software status](docs/SOFTWARE-STATUS.md) separates tested code from work
still needed on the live AWS deployment.

## Team

| Name | Responsibility |
|---|---|
| Harsh Singh | Backend and authentication |
| Satyanarayanan Sai | Frontend and integration |
| Adithyan S. Nair | Database and cloud deployment |
