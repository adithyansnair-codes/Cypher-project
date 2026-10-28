# CYPHER Operations

This document describes the implemented local and AWS delivery path. A service
is listed as operational only when there is an executable configuration and a
health or build check for it.

## Runtime versions

| Environment | Database | Backend | Reporting |
|---|---|---|---|
| Local host | PostgreSQL 16.15 | Spring Boot 4 / Java 21 | .NET 10 |
| Docker Compose | PostgreSQL 16 | Spring Boot 4 / Java 21 | .NET 10 |
| AWS DA2 deployment | RDS MySQL 8.4.9 | Spring Boot / Java 21 | PostgreSQL-only worker |

The repository's canonical local schema is PostgreSQL 16. The separate DA2
staging build in AWS uses MySQL and must not be described as the same database
engine.

## Complete local stack

1. Create the environment file:

   ```bash
   cp .env.example .env
   openssl rand -base64 64
   ```

2. Put the generated value in `JWT_SECRET` and choose a non-default
   `DB_PASSWORD` and `GRAFANA_ADMIN_PASSWORD`.

3. Start the services:

   ```bash
   docker compose up -d --build
   docker compose ps
   ```

4. Verify each public surface:

   ```bash
   curl -fsS http://localhost:8081/health
   curl -fsS http://localhost:8081/actuator/prometheus | grep cypher_incidents
   curl -fsS http://localhost:9090/-/ready
   curl -fsS http://localhost:3000/api/health
   ```

The provisioned `CYPHER Operations Overview` dashboard is the Grafana home page
for anonymous viewers. It shows open incidents, request rate, 5xx responses,
JVM heap, incidents by severity, operator actions and HTTP latency.

On the systemd-managed AWS deployment, start only monitoring and scrape the
host gateway without creating a second copy of the application:

```bash
DB_PASSWORD=compose-parse-only JWT_SECRET=compose-parse-only \
  docker compose -f docker-compose.yml \
  -f docker-compose.aws-monitoring.yml up -d --no-deps prometheus grafana
```

Through code-server, Grafana is available at `/absproxy/3000/`. The absolute
proxy preserves Grafana's configured subpath; raw Prometheus is available at
`/proxy/9090/`.

## Offline S3 evidence demo

The normal application remains available without internet access; only S3
uploads require the evidence service. For a fully offline demonstration,
LocalStack implements the same S3 API:

```bash
docker compose -f docker-compose.yml -f docker-compose.media.yml \
  --profile media up -d --build
```

In the cockpit, choose **Files** beside an incident and attach a JPEG, PNG, WebP
or MP4. The object is stored under the `cypher-media` bucket, the database row
records its key, and **Open** follows a short-lived signed URL.

LocalStack is a test double. The production path uses private AWS S3 and the
EC2 instance role.

## AWS S3

The existing private bucket `cypher-da2-backups-587748224148` can hold incident
evidence under the isolated prefix `cypher/incidents/`. An AWS administrator
can attach `infra/aws/s3-evidence-access-policy.json` as an inline identity
policy on `cypher-da2-ec2-role`. The policy grants only list access to that
prefix and get, put and delete on its objects.

For a separate evidence bucket, run the bootstrap helper from an EC2 instance
with bucket-administration access:

```bash
chmod +x infra/aws/bootstrap-s3.sh
./infra/aws/bootstrap-s3.sh
```

The script creates a private, encrypted and versioned bucket, blocks all public
access, and grants the attached EC2 role only `GetObject`, `PutObject` and
`DeleteObject` under `cypher/incidents/*`. It prints:

```text
S3_BUCKET=...
AWS_REGION=ap-south-1
S3_MEDIA_PREFIX=cypher/incidents
```

For the existing bucket, use:

```text
S3_BUCKET=cypher-da2-backups-587748224148
AWS_REGION=ap-south-1
S3_MEDIA_PREFIX=cypher/incidents
```

Add the selected values to the backend service environment, restart the service, and
verify an authenticated upload, signed download and delete through the media
endpoints in [API.md](API.md). No access keys are stored in the repository.

## GitHub Actions

`.github/workflows/ci.yml` runs on pushes, pull requests and manual dispatch. It
has four independent checks:

- Java 21 backend tests against a PostgreSQL 16 service
- .NET 10 restore/build for the reporting worker
- Python compilation, JavaScript syntax, JSON and Compose validation
- Docker image builds for the gateway and reporting worker

Test and configuration failures stop the workflow.

## Jenkins

The `Jenkinsfile` runs the same backend, reporting and code checks on a Jenkins
agent. The optional Compose Jenkins controller includes Java 21, .NET 10, Node
and Python, and configuration-as-code creates the `cypher-ci` pipeline job:

```bash
docker compose --profile ci up -d --build jenkins
```

Open `http://localhost:8088`, sign in as `admin` with
`JENKINS_ADMIN_PASSWORD`, and run `cypher-ci`. Jenkins state survives container
restarts in the `cypher_jenkins_home` volume.

Run the Jenkins controller on a machine with at least 4 GB of memory. The
project's 1 GB AWS DA2 instance is sized for the application, not for a Jenkins
controller plus a build agent. Build #2 of `cypher-ci` succeeded on the
development Mac with six backend tests, the .NET 10 reporting build and all
repository checks.

## Observability contract

Prometheus scrapes `backend:8081/actuator/prometheus` every five seconds.
Application metrics include:

- `cypher_incidents_open`
- `cypher_incidents_ingested_total`
- `cypher_incidents_actions_total`
- Spring HTTP, JVM, HikariCP and executor metrics

Grafana provisioning is declared in `monitoring/grafana/provisioning/`; the
dashboard source is
`monitoring/grafana/dashboards/cypher-overview.json`.

## Role verification

Backend integration tests verify these boundaries against PostgreSQL:

- `SECURITY_MANAGER` may read the audit trail.
- `VIEWER` may not read the audit trail.
- `VIEWER` may not delete evidence.
- `OPERATOR` may reach the evidence-delete workflow.
- Upload returns a controlled `503` when S3 is not configured.

The role matrix source of truth is
`backend/src/main/java/com/cypher/backend/config/SecurityConfig.java`, matching
the `role_permissions` seed migrations.
