# CYPHER continuous-integration evidence

Recorded on 28 October 2026 from the checked-in workflows.

## GitHub Actions

- Initial integration run: 40651469386 on commit 6b34fed
- Final documentation run: 40657080455 on commit c1b288b
- Conclusion: success
- Jobs: Java 21 backend tests with PostgreSQL 16, .NET 10 reporting build,
  AI/frontend/configuration checks, and gateway/reporting image builds.
- URL: https://github.com/adithyansnair-codes/Cypher-project/actions/runs/40657080455

## Jenkins

- Controller: Jenkins 2.580.1 LTS, configuration-as-code
- Job: cypher-ci
- Build: #2
- Conclusion: success
- Backend: 6 tests passed
- Reporting: .NET 10 Release build succeeded with 0 warnings and 0 errors
- Checks: Python compilation, cockpit JavaScript syntax, JSON validation and
  artifact archiving passed
- Controller sizing note: run on an agent with at least 4 GB RAM. The 1 GB AWS
  DA2 instance is reserved for the deployed application.

## Observability

- AWS Prometheus target `cypher-backend`: UP
- AWS Grafana dashboard screenshot: `docs/evidence/grafana-dashboard.jpg`
