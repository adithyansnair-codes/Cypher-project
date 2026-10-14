# reporting — C# reporting worker

Layer 4 of the CYPHER stack: renders the incident record into the three formats
an operator hands to a supervisor. **ASP.NET Core minimal API, .NET 10.**

The worker owns the export formats; the Spring Boot gateway proxies
`/api/reports/**` to it, so the cockpit keeps talking to one origin with one
bearer token, and this service stays on the loopback interface.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/health` | liveness plus database reachability |
| GET | `/reports/incidents.csv` | CSV, UTF-8 with BOM (Excel-safe) |
| GET | `/reports/incidents.xlsx` | Excel workbook, frozen header, date formats |
| GET | `/reports/incidents.pdf` | A4 PDF with page numbers |

All three accept `?status=`, `?severity=` and `?limit=` (default 500, max 5000),
matching the filters the cockpit already uses.

## Run

```bash
dotnet run --project reporting           # listens on 127.0.0.1:5001
```

Configuration comes entirely from the environment, using the same names as the
gateway so one `.env` serves both:

| Variable | Default |
|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `cypher` |
| `DB_USER` / `DB_PASSWORD` | `cypher_user` / `proj577!` |
| `REPORTING_PORT` | `5001` |

## Libraries

| Package | Why |
|---|---|
| Npgsql | read `incidents` joined with `cameras` and `users` |
| ClosedXML | .xlsx without Excel installed |
| QuestPDF | PDF layout (community licence, set in `Program.cs`) |

## Not done yet

- Archiving generated media/snapshots to S3 (`media` table exists, no bucket)
- Email / SMS dispatch — deliberately deferred (MoSCoW `C-06`)
