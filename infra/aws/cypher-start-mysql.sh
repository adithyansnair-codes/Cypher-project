#!/usr/bin/env bash
set -euo pipefail
source "${CYPHER_REPO:-/home/ubuntu/cypher}/infra/aws/load-db-env.sh"
: "${JWT_SECRET:?JWT_SECRET must be set in the protected environment file}"
export SPRING_PROFILES_ACTIVE=mysql
cd "$CYPHER_REPO"
exec /usr/bin/java -Xms64m -Xmx256m -jar "$CYPHER_REPO/backend/target/backend-mysql-da2.jar"
