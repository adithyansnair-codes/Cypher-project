# Database

**PostgreSQL 16.** The schema is owned by Flyway migrations — this directory no
longer holds SQL.

## Where the SQL lives

```
backend/src/main/resources/db/migration/
├── V1__initial_schema.sql   16 tables, 23 FKs, 20 indexes, 14 sequences,
│                            10 triggers, 6 views, 8 functions, 1 procedure
└── V2__seed_data.sql        idempotent demo data with real BCrypt passwords
```

Flyway applies them automatically on backend startup, in order, and records what
it ran in the `flyway_schema_history` table. **Never edit an applied migration** —
add `V3__*.sql` instead.

## Create the database

```bash
sudo -u postgres psql
```

```sql
CREATE USER cypher_user WITH PASSWORD '<choose-a-password>';
CREATE DATABASE cypher OWNER cypher_user;
\q
```

Then put that password in `.env` as `DB_PASSWORD` and start the backend — Flyway
creates everything.

```bash
cd backend && ./mvnw spring-boot:run
psql -h localhost -U cypher_user -d cypher -c "\dt"
```

## Demo credentials

Created by `V2__seed_data.sql`. **Development only — change before any review.**

| Email | Password | Role |
|---|---|---|
| `admin@cypher.com` | `Admin@2026` | SUPER_ADMIN |
| `operator@cypher.com` | `Operator@2026` | OPERATOR |
| `analyst@cypher.com` | `Analyst@2026` | ANALYST |

Passwords are BCrypt hashes (cost 10), generated with the reference `bcrypt`
library and verified by round-trip. To add or change one:

```bash
python3 -c "import bcrypt; print(bcrypt.hashpw(b'YourPassword', bcrypt.gensalt(10)).decode())"
```

Never store plaintext in `password_hash`.

## Tables

`users`, `roles`, `permissions`, `role_permissions`, `projects`,
`project_members`, `cameras`, `detections`, `incidents`, `alerts`,
`notifications`, `reports`, `media`, `audit_logs`, `ai_models`, `inference_jobs`

Full design rationale: [`docs/reference/Database-Design-Documentation.pdf`](../../docs/reference/Database-Design-Documentation.pdf).
