#!/usr/bin/env bash
#
# Rebuild the CYPHER database from scratch on this machine.
#
# WHY THIS EXISTS
#   The database on the original EC2 instance was created by hand with psql, so it
#   is not in Flyway's history table. Flyway refuses to migrate a non-empty schema
#   it does not recognise and the backend will not start. This drops the database
#   and lets Flyway rebuild all 16 tables plus demo data from scratch.
#
#   Safe here because the instance has no real user data -- only demo rows.
#
# USAGE
#   ./scripts/setup-db.sh              # drop + recreate (prompts if schema exists)
#   ./scripts/setup-db.sh --force      # same, without the confirmation prompt
#
# Requires: psql on PATH, and permission to connect as a superuser.

set -euo pipefail

cd "$(dirname "$0")/.."

DB_NAME="${DB_NAME:-cypher}"
DB_USER="${DB_USER:-cypher_user}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-5432}"
FORCE=0

for arg in "$@"; do
  case "$arg" in
    --force|-y) FORCE=1 ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "unknown option: $arg" >&2; exit 1 ;;
  esac
done

if ! command -v psql >/dev/null 2>&1; then
  echo "ERROR: psql not found on PATH." >&2
  echo "  sudo apt install -y postgresql postgresql-contrib" >&2
  exit 1
fi

if [[ -z "${DB_PASSWORD:-}" ]]; then
  echo "ERROR: DB_PASSWORD is not set." >&2
  echo "  export DB_PASSWORD='<your password>'   # or put it in .env" >&2
  exit 1
fi

echo "==> target: ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}"

# ---------------------------------------------------------------------------
# How to reach a superuser.
#
# Ubuntu ships PostgreSQL with peer authentication, so `sudo -u postgres psql`
# just works. macOS/homebrew and managed databases have no such user, so we fall
# back to TCP as PGUSER/PGPASSWORD.
#
# The earlier version probed with `sudo -n -u postgres true`. On Ubuntu the
# postgres *role* has no password and `sudo -n` can be refused, so the probe
# failed and the script silently fell through to TCP -- where psql then stops and
# asks "Password for user postgres:", which looks like a hang. Probe with the
# actual command instead of a proxy for it.
# ---------------------------------------------------------------------------
SUPER_MODE=""

detect_super() {
  if [[ "$(id -un)" == "postgres" ]]; then
    SUPER_MODE="direct"
    return
  fi
  if command -v sudo >/dev/null 2>&1 \
     && sudo -n -u postgres psql -tAc "SELECT 1" >/dev/null 2>&1; then
    SUPER_MODE="sudo"
    return
  fi
  # TCP is only usable if a superuser password is actually available. Without
  # one, psql would stop and prompt -- so skip it rather than appear to hang.
  if [[ -n "${PGPASSWORD:-}" ]] \
     && PGPASSWORD="$PGPASSWORD" psql -tAc "SELECT 1" \
       -h "$DB_HOST" -p "$DB_PORT" -U "${PGUSER:-postgres}" -d postgres \
       >/dev/null 2>&1; then
    SUPER_MODE="tcp"
    return
  fi
  SUPER_MODE=""
}

as_super() {
  case "$SUPER_MODE" in
    direct) psql -v ON_ERROR_STOP=1 -q -c "$1" ;;
    sudo)   sudo -n -u postgres psql -v ON_ERROR_STOP=1 -q -c "$1" ;;
    tcp)    PGPASSWORD="${PGPASSWORD:-}" psql -v ON_ERROR_STOP=1 -q \
              -h "$DB_HOST" -p "$DB_PORT" -U "${PGUSER:-postgres}" \
              -d postgres -c "$1" ;;
    *)      echo "internal error: superuser mode not detected" >&2; exit 1 ;;
  esac
}

# Same connection, but returns the value instead of discarding it. Used for the
# post-drop verification; running that over raw TCP while the rest of the script
# uses sudo is what caused a stray "Password for user postgres:" prompt.
as_super_scalar() {
  case "$SUPER_MODE" in
    direct) psql -tAc "$1" ;;
    sudo)   sudo -n -u postgres psql -tAc "$1" ;;
    tcp)    PGPASSWORD="${PGPASSWORD:-}" psql -tAc "$1" \
              -h "$DB_HOST" -p "$DB_PORT" -U "${PGUSER:-postgres}" -d postgres ;;
    *)      echo "internal error: superuser mode not detected" >&2; exit 1 ;;
  esac
}

detect_super

if [[ -z "$SUPER_MODE" ]]; then
  cat >&2 <<EOF
ERROR: cannot reach PostgreSQL as a superuser.

Tried:
  1. connecting as the local 'postgres' system user
  2. sudo -n -u postgres psql        (needs passwordless sudo)
  3. TCP to ${DB_HOST}:${DB_PORT} as ${PGUSER:-postgres}

Fix on Ubuntu/Debian (the usual case):
  sudo -u postgres psql -c "ALTER ROLE ${DB_USER} WITH PASSWORD '${DB_PASSWORD}';"
  -- then either grant passwordless sudo for your user, or create the database
     by hand and run:  cd backend && ./mvnw spring-boot:run

Fix on macOS/homebrew or a managed database: export the superuser credentials
first, then re-run:
  export PGUSER=postgres PGPASSWORD='<superuser password>'
EOF
  exit 1
fi

echo "==> superuser access: ${SUPER_MODE}"

echo "==> ensuring role '${DB_USER}' exists"
as_super "DO \$\$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${DB_USER}') THEN
    CREATE ROLE ${DB_USER} LOGIN PASSWORD '${DB_PASSWORD}';
  ELSE
    ALTER ROLE ${DB_USER} WITH LOGIN PASSWORD '${DB_PASSWORD}';
  END IF;
END \$\$;"

# Refuse to drop if a Flyway history table exists AND the caller has not
# confirmed, so a typo cannot wipe a database that is actually in use.
if [[ "$FORCE" -ne 1 ]]; then
  HAS_DATA=$(PGPASSWORD="$DB_PASSWORD" psql -tAc \
    "SELECT to_regclass('public.flyway_schema_history') IS NOT NULL" \
    -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" 2>/dev/null || echo "f")
  if [[ "$HAS_DATA" == "t" ]]; then
    echo "==> '${DB_NAME}' already contains a Flyway-managed schema."
    echo "    Re-running will DROP it and rebuild from the migrations."
    read -r -p "    Continue? [y/N] " reply
    [[ "$reply" =~ ^[Yy]$ ]] || { echo "aborted."; exit 0; }
  fi
fi

echo "==> dropping and recreating '${DB_NAME}' (clean start)"

# WITH (FORCE) terminates other sessions. Without it the drop fails whenever the
# backend is still running and holding a connection, the old schema survives, and
# Flyway then baselines at V1 and skips the migration that creates the tables.
# Requires PostgreSQL 13+.
as_super "DROP DATABASE IF EXISTS ${DB_NAME} WITH (FORCE)"

# Verify the drop actually happened rather than assuming it did.
REMAINS=$(as_super_scalar "SELECT 1 FROM pg_database WHERE datname = '${DB_NAME}'" 2>/dev/null || true)
if [[ -n "${REMAINS// /}" ]]; then
  echo "ERROR: could not drop '${DB_NAME}' -- it still exists." >&2
  echo "  Stop anything connected (the Spring Boot app, open psql sessions), then re-run." >&2
  exit 1
fi

as_super "CREATE DATABASE ${DB_NAME} OWNER ${DB_USER}"

echo "==> granting schema privileges"
PGPASSWORD="$DB_PASSWORD" psql -v ON_ERROR_STOP=1 -q \
  -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
  -c "GRANT ALL ON SCHEMA public TO ${DB_USER};"

echo
echo "Done. The database is now EMPTY and owned by ${DB_USER}."
echo "Flyway will create all 16 tables and load the demo data on next startup:"
echo
echo "    cd backend && ./mvnw spring-boot:run"
echo
echo "Then confirm with:"
echo "    psql -h ${DB_HOST} -p ${DB_PORT} -U ${DB_USER} -d ${DB_NAME} -c '\\dt'"
echo
echo "Demo logins (change before any review):"
echo "    admin@cypher.com     Admin@2026"
echo "    operator@cypher.com  Operator@2026"
echo "    analyst@cypher.com   Analyst@2026"
