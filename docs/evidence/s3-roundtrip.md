# CYPHER S3 media round-trip evidence

Recorded on 28 October 2026 against an independent S3-compatible server.

## Test

- Backend: Spring Boot 4 on port 8082 with `S3_BUCKET=cypher-media`
- Storage: Moto S3 server on `127.0.0.1:5005`, path-style addressing
- API client: authenticated `SUPER_ADMIN`
- Incident: 47
- File: `knife.jpg`, 4,568 bytes
- Upload response: `201`, generated media id 2
- Download URL: AWS Signature Version 4, 600-second expiry
- Source SHA-256: `4dd6ad0489db8790c685fc30c7d771c099e07c1747fe3c0921cecd87295554b8`
- Download SHA-256: `4dd6ad0489db8790c685fc30c7d771c099e07c1747fe3c0921cecd87295554b8`
- API metadata listing: passed
- API delete: `204`
- Final database media list: empty
- Final object count under `cypher/incidents/`: 0

## AWS boundary

The EC2 role `cypher-da2-ec2-role` currently denies `s3:ListAllMyBuckets`,
`s3:CreateBucket`, `s3:HeadBucket` and `s3:PutObject`. An administrator must
grant the bucket/prefix permissions or run `infra/aws/bootstrap-s3.sh` before
the same round-trip can be recorded against live AWS S3.
