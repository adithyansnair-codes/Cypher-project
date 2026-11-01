#!/usr/bin/env bash
# Shared settings for the EC2 backend and reporting service.
set -euo pipefail
export CYPHER_REPO="${CYPHER_REPO:-/home/ubuntu/cypher}"
set -a
[[ ! -r "$CYPHER_REPO/.env" ]] || source "$CYPHER_REPO/.env"
[[ ! -r /etc/cypher/backend.env ]] || source /etc/cypher/backend.env
set +a

# Read the current RDS password at startup so managed rotation does not leave
# the services using an old password from an environment file.
if [[ -n "${DB_SECRET_ID:-}" ]]; then
    aws_bin="${AWS_CLI:-/snap/bin/aws}"
    secret_json="$($aws_bin secretsmanager get-secret-value --region "${AWS_REGION:-ap-south-1}" \
        --secret-id "$DB_SECRET_ID" --query SecretString --output text)"
    DB_USER="$(printf '%s' "$secret_json" | jq -er .username)"
    DB_PASSWORD="$(printf '%s' "$secret_json" | jq -er .password)"
    export DB_USER DB_PASSWORD
    unset secret_json
fi
: "${DB_HOST:?Set DB_HOST in /etc/cypher/backend.env}"
: "${DB_USER:?Database username is missing}"
: "${DB_PASSWORD:?Database password is missing}"
export DB_PORT="${DB_PORT:-3306}" DB_NAME="${DB_NAME:-cypher}"
export DB_ENGINE=mysql DB_SSL_MODE=REQUIRED
