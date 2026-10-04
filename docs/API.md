# CYPHER REST API

Base URL `http://<host>:8081`. All routes except `/auth/**` require a bearer token:

```
Authorization: Bearer <jwt>
```

Obtain a token from `POST /auth/login`.

---

## Authentication

### `POST /auth/register` — public

```json
{ "fullName": "Jane Doe", "email": "jane@example.com", "password": "Secret@2026", "phone": "+911234567890" }
```

| Status | Meaning |
|---|---|
| `201` | created |
| `400` | validation failed — body lists the offending fields |
| `409` | email already registered |

New users always receive the **VIEWER** role. Elevated roles are assigned by an
administrator, never by the registration endpoint.

### `POST /auth/login` — public

```json
{ "email": "admin@cypher.com", "password": "Admin@2026" }
```

`200` returns `{"token": "<jwt>"}`. `401` on bad credentials or a non-ACTIVE account.
The JWT carries the email as `sub` and the role as a `role` claim, and maps to a
`ROLE_<role>` authority for authorization.

---

## Incidents

### `GET /api/incidents`

The cockpit's incident stream, newest first. Camera name, code and location are
joined in so the browser does not need a second request per row.

| Query param | Values |
|---|---|
| `status` | `OPEN` `ACKNOWLEDGED` `IN_PROGRESS` `RESOLVED` `CLOSED` |
| `severity` | `LOW` `MEDIUM` `HIGH` `CRITICAL` |

Both are optional and case-insensitive. Unknown values return an empty list
rather than an error.

```bash
curl -H "Authorization: Bearer $TOKEN" "$BASE/api/incidents?severity=CRITICAL"
```

```json
[
  {
    "incidentId": 1,
    "title": "Motion in Restricted Area",
    "description": "Person detected inside the lab after operating hours.",
    "severity": "CRITICAL",
    "status": "OPEN",
    "occurredAt": "2026-10-04T09:42:18.270092Z",
    "resolvedAt": null,
    "assignedTo": null,
    "cameraId": 2,
    "cameraName": "Lab Entrance Camera",
    "cameraCode": "CAM-002",
    "location": "Lab Entrance",
    "detectionId": null
  }
]
```

### `GET /api/incidents/{id}`

One incident, same shape. `404` if it does not exist.

### `POST /api/incidents/{id}/acknowledge`

`OPEN` → `ACKNOWLEDGED`. Assigns the incident to the calling operator.

| Status | Meaning |
|---|---|
| `200` | updated incident |
| `404` | no such incident |
| `409` | not currently `OPEN` |

### `POST /api/incidents/{id}/resolve`

`OPEN` or `ACKNOWLEDGED` → `RESOLVED`, and stamps `resolvedAt`.

Resolving also fires the `trg_close_alerts` database trigger, which sets the
incident's alerts to `CLOSED`. This is MoSCoW requirement **M-08**.

| Status | Meaning |
|---|---|
| `200` | updated incident |
| `404` | no such incident |
| `409` | already resolved |

---

## Dashboard and assets

### `GET /api/dashboard/stats`

The four tiles across the top of the cockpit, plus asset counts.

```json
{
  "openIncidents": 1,
  "criticalPriority": 1,
  "highPriority": 1,
  "resolvedToday": 1,
  "camerasOnline": 3,
  "camerasTotal": 3,
  "pendingAlerts": 1
}
```

`resolvedToday` counts from midnight UTC and is computed in the database.

### `GET /api/cameras`

Monitored assets for the sidebar, ordered by name. Returns `Camera` rows
including `cameraCode`, `location`, `streamUrl` and `cameraStatus`
(`ONLINE` / `OFFLINE` / `MAINTENANCE`).

---

## Errors

Validation failures return the field names and messages:

```json
{
  "status": 400,
  "error": "Validation failed",
  "fields": {
    "password": "password must be between 8 and 72 characters",
    "email": "email must be a valid address"
  }
}
```

Stack traces are never returned.

---

## Status vocabularies

Enforced by CHECK constraints (`V3__fix_alert_trigger_and_status_constraints.sql`),
so an invalid value is rejected by the database rather than silently stored.

| Column | Allowed values |
|---|---|
| `incidents.status` | `OPEN` `ACKNOWLEDGED` `IN_PROGRESS` `RESOLVED` `CLOSED` |
| `incidents.severity` | `LOW` `MEDIUM` `HIGH` `CRITICAL` |
| `alerts.alert_status` | `PENDING` `SENT` `CLOSED` |
| `cameras.camera_status` | `ONLINE` `OFFLINE` `MAINTENANCE` |

---

## Not yet implemented

| Endpoint | For |
|---|---|
| `POST /api/incidents/ingest` | the AI engine to submit a detection |
| `GET /api/incidents/{id}/alerts` | alert history per incident |
| `GET /api/reports/*` | CSV / Excel / PDF export (needs the C# worker) |
| WebSocket channel | live push to the cockpit (MoSCoW M-03) |
| Project and camera administration | MoSCoW S-07 |
