# Software status — 2 November 2026

## Completed in this update

- Fixed incident creation on PostgreSQL and MySQL. Each request returns its own
  inserted incident ID, including when several detections arrive together.
- Added MySQL migrations for the schema, model record, roles, audit trigger and
  alert closure trigger. The AWS RDS database has been migrated.
- Updated analytics, audit queries and the reporting worker for both databases.
  Database sessions use UTC for daily counts.
- Kept webcam monitoring running after an alert. Added per-camera/class cooldowns,
  stale-frame resets and cancellation of pending inference when monitoring stops.
- Changed image confirmation to a single-image review. It no longer repeats one
  photo eight times and calls that temporal confirmation.
- Stopped treating an ordinary person detection as an automatic incident.
- Made the trained model path configurable and removed the silent COCO fallback.
- Removed the public JWT signing-key fallback. Local configuration supplies the key.
- Return HTTP 401 for missing/invalid tokens so the browser opens sign-in again.
- Replaced the duplicate frontend files with links to the served dashboard source.
- Added a post-analysis panel and a downloadable HTML report with the annotated frame.
- Rewrote setup instructions and added a local service launcher.
- Removed the mock camera feed, invented response-time chart and placeholder
  incidents. Service status and pending alerts now use live results.

## Checks performed

- 12 backend tests passed on PostgreSQL 16 and on a separate native MySQL test server.
- Six Python policy tests and seven JavaScript detection/analysis tests passed.
- GitHub Actions passed against PostgreSQL 16 and MySQL 8.4, including exports.
  The local MySQL binary is 26.7; the CI check covers the RDS engine series.
- CSV, Excel and PDF exports returned valid files from both databases. The MySQL
  check included an incident row and a status-filter check.
- All seven Desktop demo images ran through the trained six-class model.
- An isolated eight-frame knife sequence created one incident on frame eight and
  suppressed frame nine. Acknowledge, resolve, audit, analytics and exports passed.
  This used repeated test input to check the counter; it is not a live-camera accuracy test.
- The launcher started all three services on separate test ports.

## AWS deployment

The updated backend and reporting service are running on EC2 with RDS MySQL
8.4.9. Before migration, a database backup was saved on the instance. The
existing eight incidents were preserved. The migration created the model row,
roles and the audit and alert triggers.

The cloud login, camera list, analytics, audit and CSV, Excel and PDF exports
were tested through the gateway. A labelled synthetic test incident was created
and resolved; its resolution is present in the audit history. It remains in
RDS as `DEMO TEST - simulated knife signal`.

RDS uses the `cypher-mysql84` parameter group with
`log_bin_trust_function_creators` enabled, after approval for the trigger setup.
The running cloud build includes the post-analysis panel. The later repository
commits improve the webcam analysis metadata test and deployment configuration.

Still to do:

- Configure and verify a scoped S3 media policy and a real evidence upload.
  Database backups do not prove that incident uploads work.
- Add HTTPS and review public access settings before use beyond the project demo.
- Test the physical webcam in the final presentation environment. The image demo
  and confirmation logic were tested; live-camera accuracy still needs a real run.

## Model and project limits

The detector recognises object classes, not intent or who is holding an object.
`lying` is not temporal fall detection. Confirmation reduces brief false positives
but does not prove a threat. Browser, CLI and Python monitoring keep their own
in-memory cooldown state; use one monitoring path per camera.

Embedded hardware is outside this update. The existing hardware files, datasets,
checkpoints, demo images and submitted reports have been preserved.
