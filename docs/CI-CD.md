# CYPHER CI/CD: implementation and instructor evidence

## Status — 23 October 2026

Implemented locally on `ci/practical-pipeline`, based on `700a4f808895c734a80598034bbecb090ff505be`.
The connected GitHub app could not write (403), so changes are being published through the authenticated collaborator browser session on `ci/practical-pipeline`. No Actions success is claimed until the hosted run completes. No AWS resources were created and no deployment was triggered.

Locally verified:
- Frontend deployed/source copies match; all inline JavaScript passes Node syntax checks.
- Eight Python tests pass: gateway login/cache/error handling, request payload, detector result conversion/order and class handling. YOLO itself is mocked; these are not accuracy tests.
- Seven Java tests pass, including the Spring application startup test against PostgreSQL, severity boundaries and JWT signature/expiry checks.
- All six Flyway migrations applied to a fresh isolated PostgreSQL 17.6 database; Hibernate schema validation succeeded.
- .NET 10 reporting build passes with zero warnings/errors; CSV escaping, XLSX content and populated/empty PDF export checks pass.
- Local HTTP integration passes: frontend, anonymous access rejection, role restriction, malformed payload rejection, authenticated ingest, persisted incident, acknowledge/resolve, CSV/XLSX/PDF exports.
- Seven deployment-gate tests pass: exact successful SHA accepted, absent/failed/running/wrong-SHA/wrong-branch/latest-failed evidence rejected.
- Both workflow files pass actionlint 1.7.7; Python fatal-error lint and shell syntax checks pass.

Still unverified: execution on GitHub-hosted Linux runners; Docker builds/Compose execution; Trivy scan findings; registry publishing; SSH deployment and environment approval. Local integration used actual processes and PostgreSQL, not containers. Do not describe those pending checks as complete.

## What was inspected

| Component | Repository reality | Pipeline treatment |
|---|---|---|
| Frontend | Static HTML/CSS with inline JavaScript; copies under `frontend/src` and Spring static resources | Check copies and JS syntax; packaged inside backend JAR/image |
| Backend | Java 21, Spring Boot 4.1, Maven wrapper | Compile/package, severity/JWT tests, startup test with real PostgreSQL |
| Database | PostgreSQL, six Flyway migrations, demo seed accounts | Fresh temporary DB in CI, never AWS/RDS |
| AI | FastAPI + Ultralytics; trained YOLO `.pt` checkpoint outside Git | Mocked adapter/contract tests; CPU image build; no model accuracy claim |
| Reporting | .NET 10, PostgreSQL, ClosedXML, QuestPDF | Locked restore, compiler/analyzers, export checks, container build and HTTP integration |
| Existing Compose | Backend + PostgreSQL; host reporting worker | Preserve local setup; add separate CI and staging manifests |
| Existing CI | No tracked `.github` workflows | Add CI and a disabled-by-default manual staging workflow |
| Existing deployment | README mentions EC2; no reproducible production manifest | No production deployment enabled |

The earlier Cloud DA report describes MySQL/RDS and additional gateway/stage4 containers. Those services and database topology do not match this checkout. Neither the missing stage4 implementation nor that live AWS environment was inspected here. Reconcile them before production work.

## What runs automatically

`CYPHER CI` runs on pushes to `main` or `ci/**`, pull requests, and manual dispatch.

1. **Frontend and AI contracts**: static JS validation, source-copy consistency, Python syntax/fatal-error lint, eight AI unit/contract tests and seven deployment-gate tests.
2. **Java tests and migrations**: disposable PostgreSQL service, fresh random signing key, Maven `verify` (no skipped tests).
3. **Reporting build and analyzers**: NuGet locked restore, warnings-as-errors build, real CSV/XLSX/PDF checks and publish artifact.
4. **Dependency and secret scan**: Trivy source scan; fails on fixable HIGH/CRITICAL findings. Unfixed vulnerabilities are excluded by policy, not declared absent.
5. **Container integration**: build and start backend + reporting + PostgreSQL, run HTTP smoke checks, collect logs, remove the temporary stack and volumes even on failure.
6. **Three container builds**: backend (also contains frontend), reporting and CPU AI. No push in CI. Scan each image; fail on fixable CRITICAL vulnerabilities.
7. **CI gate**: succeeds only if every required job succeeds, including image builds. Failed, cancelled or skipped dependencies cannot produce a green gate.

AI model weights are not included in the image. A real inference demonstration still requires a verified checkpoint and a separately recorded accuracy/smoke test. Runtime AI dependency ranges and base image tags remain upstream-managed; immutable deployment digests identify a built release, but do not make every rebuild byte-for-byte reproducible.

Workflow actions are pinned to resolved commit SHAs. Default token permission is read-only; no `pull_request_target` execution, AWS keys, production URLs, or deployment secrets enter PR jobs. NuGet lock files, Maven/pip/NuGet caches and per-image BuildKit caches reduce repeat work. Dependabot checks Actions, Maven, Python, NuGet and Docker weekly. Cache data is an optimization, never proof of a passing build.

Artifact retention: Java JAR/test results, reporting output and integration logs 7 days; Python results and security/image scan reports 14 days; approved staging digest manifest 30 days. Download evidence before expiry. No recurring billable AWS automation is configured. GitHub runner, cache, artifact and registry limits still depend on the repository's plan/settings.

## First priority: obtain a real green Actions run

The authenticated collaborator browser session can publish the branch. The app permission change below is optional for future connector access; it is not required for browser publishing:
1. In GitHub's installed app settings, make this repository accessible to the connected app and approve requested **Contents: write**, **Workflows: write**, and **Pull requests: write** permissions where supported. A collaborator's push permission alone does not give the app these permissions. If the app cannot request them, use an authenticated local clone/GitHub CLI instead. Never send a token in chat.
2. Apply the supplied patch in a clean clone (commands below), push `ci/practical-pipeline`, then open a draft PR into `main`. Existing workflows on `main` were absent at inspection; these workflows do not deploy on push.
3. In **Actions → CYPHER CI**, inspect the run for that exact commit. Resolve any failed Linux/container/security checks; rerun only after a fix or a diagnosed transient failure. Do not blanket-ignore scanner findings to obtain a green badge.
4. Download test results, integration logs and image-scan artifacts. Record the commit SHA and run URL next to each screenshot.
5. After review, the owner can protect `main` using a branch rule/ruleset: require PR review and the **CI gate** check, block force pushes/deletions, and prevent unauthorized bypass. Select the real check after its first run exists. Require a current branch before merging.

Patch application (from a clean repository clone; substitute the downloaded patch path):

```sh
git fetch origin
git switch -c ci/practical-pipeline 700a4f808895c734a80598034bbecb090ff505be
git apply --check /path/to/CYPHER-CICD.patch
git apply /path/to/CYPHER-CICD.patch
git add .
git commit -m "Add tested CI and approval-gated staging pipeline"
git push -u origin ci/practical-pipeline
```

If `main` has moved, merge/rebase it into the branch, resolve conflicts, and rerun CI before merging. The patch adds no credentials. CI requires **no custom secrets**; GitHub supplies `GITHUB_TOKEN`.

## Enable staging only after CI is green and deployment is approved

Staging deploys backend/frontend + reporting + a dedicated PostgreSQL database. AI is built/tested in CI but is not automatically deployed without its trained model and service-account setup. This is a partial application staging environment, not proof of the full AI production topology.

The workflow uses **manual dispatch only**, requires `main`, the exact text `DEPLOY STAGING`, and a successful push CI run for that exact SHA. It then waits on the `staging` environment. It rebuilds and scans images after approval, pushes SHA-tagged images to GHCR, and deploys the resulting **digests**, not mutable `latest` tags. This is a rebuild of tested source, not promotion of the exact CI image bytes; production should promote the tested staging digests.

Only the repository owner/admin can configure environment protections:
1. Create **Settings → Environments → staging** before enabling the workflow.
2. Add a required reviewer, enable prevention of self-review and disable administrator bypass where available. Restrict deployment branches to `main`. GitHub's reviewer gate must actually be configured; an environment name or variable alone is not approval.
3. Set repository variable `STAGING_ENABLED=true` only when the host is ready and deployment has been authorized. Leave it unset otherwise.
4. Set environment variable `STAGING_APPROVAL_CONFIGURED=true` only after verifying those protection rules. This is an administrative attestation, not an API verification of the rules.
5. Add the following **staging environment secrets**, never repository files:

| Secret | Exact purpose |
|---|---|
| `STAGING_HOST` | Hostname/IP of a dedicated, already-approved staging host; port 22 |
| `STAGING_USER` | Restricted deployment account on that host |
| `STAGING_SSH_KEY` | Dedicated private deployment key; corresponding public key installed for that account |
| `STAGING_KNOWN_HOSTS` | OpenSSH known_hosts line for that host, fingerprint verified out of band; do not disable strict checking |

No AWS access key, production credential or GitHub PAT is needed in CI. `GITHUB_TOKEN` publishes GHCR images. If GHCR packages are private, provision a read-only registry login on the staging host separately, or explicitly make these non-secret application images public after review. Do not put that host registry credential in a release artifact.

Host prerequisites: dedicated Linux x86_64 machine, Docker Engine + Compose v2 with `up --wait`, Python 3, curl, Bash, outbound GHCR access, and authorized SSH ingress. The deployment account needs Docker access (a powerful host permission) and ownership of `/opt/cypher-staging`. The workflow never provisions a host or opens security groups.

On that host, an administrator provisions:

```sh
mkdir -p /opt/cypher-staging/releases
chmod 700 /opt/cypher-staging
# Create .env through a secure editor; do not paste its values into GitHub logs.
# Required contents: DB_PASSWORD=<random staging password>
#                    JWT_SECRET=<base64-encoded 64 random bytes>
chmod 600 /opt/cypher-staging/.env
touch /opt/cypher-staging/ALLOW_STAGING_DEPLOY
```

Generate each value with a cryptographic generator, e.g. `openssl rand -hex 32` and `openssl rand -base64 64 | tr -d '\n'`. Demo seed accounts are publicly known in the existing migrations; this staging manifest binds the application to loopback and publishes neither DB nor reporting ports. Keep it private; do not expose it publicly with demo accounts. A local review can use `ssh -L 8081:127.0.0.1:8081 user@staging-host`.

After the owner and reviewer are ready: **Actions → Approved staging deployment → Run workflow → main → DEPLOY STAGING**. Review the pending environment job and approve it deliberately. Collect the deployment record, digest artifact and health output. Do not run it merely to take a screenshot before the host and credentials are approved.

Rollback: releases remain under `/opt/cypher-staging/releases/<sha>` and `current` advances only after health checks. A failed startup can leave changed containers running. Inspect logs and database state; restarting a previous release script can restore previous image digests **only if the schema is backward compatible**. No automatic schema rollback, destructive volume cleanup or backup creation is performed by staging. Test restore/rollback separately before relying on it.

## Production safeguards and remaining work

There is intentionally no executable production deployment workflow. Before adding one:
- Resolve PostgreSQL repository versus MySQL/RDS live deployment drift and document all gateway/AI/stage4 services.
- Remove/rotate demo accounts and development signing keys; verify private service boundaries and TLS.
- Version and verify the real trained checkpoint; add an actual inference test and model artifact provenance.
- Establish reviewed migration procedures, verified backups/restores and a rollback plan.
- Promote staging-approved image digests without rebuilding; require a protected version tag and separate production environment reviewers/secrets.
- If AWS deployment is later approved, use short-lived OIDC credentials scoped to this repo/environment, define costs first, and do not reuse staging authorization as production approval.

## Instructor explanation (about one minute)

“CYPHER has a CI workflow covering its static frontend, Java gateway, Python AI integration code and C# reporting worker. A push or pull request runs checks automatically. We test against a temporary PostgreSQL database, apply Flyway migrations, build the application and test authentication, incident ingestion and report exports. Container builds and vulnerability scans are required before the final CI gate can pass. Test reports and logs are kept as evidence. Staging delivery is manual and protected by an environment reviewer, with credentials stored outside Git and image digests recorded for traceability. Production remains disabled until the live database and service topology are reconciled.”

Until the first green GitHub run, add: **“The implementation and local tests pass; execution on GitHub and container/security checks are still pending.”** Do not say “fully deployed CI/CD” until that is evidenced.

Viva answers:
- **CI vs CD?** CI validates changes automatically. Our CD is approved delivery to staging, not unattended production deployment.
- **What happens on failure?** The CI gate fails; deployment's exact-SHA CI check refuses the commit. Logs/test reports explain the failure.
- **Why a real DB?** PostgreSQL-specific migrations, constraints and persistence cannot be proved by mocked repositories alone.
- **How are secrets handled?** CI uses disposable values; staging SSH secrets are environment-scoped; database/JWT secrets stay on the host. No AWS keys are used.
- **How do you identify a release?** Source commit, workflow run, image digest and deployment record.
- **Does a green CI prove AI accuracy?** No. Adapter/contract tests prove code behavior; trained-model evaluation is separate.
- **Can you roll back?** Images are identifiable by digest, but database changes require compatibility or a tested restore; rollback has not been demonstrated yet.

## Evidence checklist and finish-early plan

- [ ] Draft PR showing the workflows, tests and Docker changes.
- [ ] Green **CI gate** with commit SHA and run URL.
- [ ] Expanded Java test/migration, Python test and reporting export output.
- [ ] Integration log showing the test stack and passing HTTP assertions.
- [ ] Three successful container builds and their vulnerability reports.
- [ ] Downloaded artifacts before retention expires.
- [ ] A deliberate failing test in a separate temporary PR demonstrates that the gate blocks; restore/revert the test and show green again. Do not break main.
- [ ] `main` protection and staging reviewer settings, with secret values hidden.
- [ ] Optional approved staging deployment record, digest manifest and health/demo screen.
- [ ] Limitations slide: actual model accuracy, AWS topology reconciliation and production remain separate work.

Day 1–2: publish through the collaborator session, resolve runner differences and obtain green CI. Day 3: capture the failure/recovery demonstration and evidence. Day 4–5: optionally configure a private approved staging host and demonstrate reviewer gating. Days 6–10 are buffer for model/deployment reconciliation and rehearsal. A truthful CI demonstration is ready earlier than a safe full production deployment.

References: [GitHub environment protection](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments), [GitHub secure workflow use](https://docs.github.com/en/actions/reference/security/secure-use).
