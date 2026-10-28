#!/usr/bin/env bash
set -euo pipefail

# Builds the canonical checkout with the opt-in MySQL profile and atomically
# replaces the running DA2 JAR. Run as the ubuntu user on the EC2 host.

REPO="${CYPHER_REPO:-/home/ubuntu/cypher}"
LAUNCHER="${CYPHER_LAUNCHER:-/usr/local/bin/cypher-start-mysql.sh}"

cd "$REPO"
git pull --ff-only origin main

cd "$REPO/backend"
./mvnw -B -ntp clean package -DskipTests

install -m 0644 target/backend-0.0.1-SNAPSHOT.jar target/backend-mysql-da2.jar
sudo install -m 0755 "$REPO/infra/aws/cypher-start-mysql.sh" "$LAUNCHER"
sudo systemctl restart cypher-backend

for _ in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8081/health >/dev/null 2>&1; then
    curl -fsS http://127.0.0.1:8081/health
    echo
    exit 0
  fi
  sleep 2
done

sudo journalctl -u cypher-backend -n 80 --no-pager >&2
exit 1
