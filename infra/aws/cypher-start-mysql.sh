#!/usr/bin/env bash
set -euo pipefail

# Preserve non-database application configuration from the project checkout.
if [[ -r /home/ubuntu/cypher/.env ]]; then
  set -a
  # shellcheck disable=SC1091
  source /home/ubuntu/cypher/.env
  set +a
fi

# AWS DA2 launcher. The protected environment file is written once during
# provisioning, is never committed, and overrides checkout values.
if [[ -r /etc/cypher/backend.env ]]; then
  set -a
  # shellcheck disable=SC1091
  source /etc/cypher/backend.env
  set +a
fi

if [[ -z "${DB_PASSWORD:-}" || -z "${DB_HOST:-}" ]]; then
  echo "Database environment is missing: provision /etc/cypher/backend.env" >&2
  exit 1
fi

export SPRING_PROFILES_ACTIVE=mysql
export DB_NAME="${DB_NAME:-cypher}"
export DB_PORT="${DB_PORT:-3306}"

cd /home/ubuntu/cypher
exec /usr/bin/java \
  -Xms128m -Xmx384m \
  -jar /home/ubuntu/cypher/backend/target/backend-mysql-da2.jar \
  --spring.profiles.active=mysql
