#!/usr/bin/env bash
set -euo pipefail

# Builds the canonical checkout with the opt-in MySQL profile and
# replaces the DA2 JAR. Run as the ubuntu user after taking a database backup.

REPO="${CYPHER_REPO:-/home/ubuntu/cypher}"
LAUNCHER="${CYPHER_LAUNCHER:-/usr/local/sbin/cypher-start-mysql.sh}"

cd "$REPO"
git pull --ff-only origin main

mkdir -p "$REPO/.runtime/deploy-backups"
if [[ -f "$REPO/backend/target/backend-mysql-da2.jar" ]]; then
  cp "$REPO/backend/target/backend-mysql-da2.jar" "$REPO/.runtime/deploy-backups/backend-$(date -u +%Y%m%dT%H%M%SZ).jar"
fi
export MAVEN_OPTS="${MAVEN_OPTS:--Xmx256m}"
cd "$REPO/backend"
./mvnw -B -ntp clean package -DskipTests

install -m 0644 target/backend-0.0.1-SNAPSHOT.jar target/backend-mysql-da2.jar
sudo install -m 0755 "$REPO/infra/aws/cypher-start-mysql.sh" "$LAUNCHER"
cd "$REPO"
dotnet publish reporting --configuration Release --no-self-contained -o reporting/publish
sudo install -m 0644 infra/aws/cypher-reporting.service /etc/systemd/system/cypher-reporting.service
sudo install -d -m 0755 /etc/systemd/system/cypher-backend.service.d
sudo install -m 0644 infra/aws/cypher-secret-env.conf /etc/systemd/system/cypher-backend.service.d/cypher-secret-env.conf
sudo systemctl daemon-reload
sudo systemctl enable cypher-reporting
sudo systemctl restart cypher-backend cypher-reporting

for _ in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8081/actuator/health >/dev/null 2>&1; then
    curl -fsS http://127.0.0.1:8081/actuator/health
    echo
    exit 0
  fi
  sleep 2
done

sudo journalctl -u cypher-backend -n 80 --no-pager >&2
exit 1
