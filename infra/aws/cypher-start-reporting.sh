#!/usr/bin/env bash
set -euo pipefail
source "${CYPHER_REPO:-/home/ubuntu/cypher}/infra/aws/load-db-env.sh"
export REPORTING_BIND=127.0.0.1 REPORTING_PORT=5001
cd "$CYPHER_REPO"
exec /usr/bin/dotnet "$CYPHER_REPO/reporting/publish/Cypher.Reporter.dll"
