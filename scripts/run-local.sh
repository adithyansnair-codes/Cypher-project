#!/usr/bin/env bash
# Start the local services. PostgreSQL/MySQL must already be running.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env ]]; then
    set -a
    source .env
    set +a
fi
service="${1:-all}"
case "$service" in all|backend|ai|reporting) ;; *) echo 'Usage: run-local.sh [all|backend|ai|reporting]' >&2; exit 1 ;; esac
mkdir -p .runtime
if [[ "${DB_ENGINE:-postgres}" == mysql ]]; then export SPRING_PROFILES_ACTIVE=mysql; fi
export GATEWAY_URL="${GATEWAY_URL:-http://127.0.0.1:${SERVER_PORT:-8081}}"
export REPORTING_URL="${REPORTING_URL:-http://127.0.0.1:${REPORTING_PORT:-5001}}"
export SERVER_ADDRESS="${SERVER_ADDRESS:-127.0.0.1}"
export JPA_SHOW_SQL="${JPA_SHOW_SQL:-false}"
python_bin="${AI_PYTHON:-$PWD/.venv/bin/python}"
dotnet_bin="${DOTNET_BIN:-dotnet}"
if [[ "$service" == all || "$service" == backend ]]; then
    : "${JWT_SECRET:?Set JWT_SECRET in .env. Generate it with openssl rand -base64 64.}"
    (cd backend && ./mvnw -B -ntp -DskipTests package) > .runtime/backend-build.log 2>&1 || {
        tail -40 .runtime/backend-build.log; exit 1;
    }
fi
if [[ "$service" == all || "$service" == reporting ]]; then
    "$dotnet_bin" build reporting --configuration Release > .runtime/reporting-build.log 2>&1 || {
        tail -40 .runtime/reporting-build.log; exit 1;
    }
fi
if [[ "$service" == all || "$service" == ai ]]; then
    [[ -x "$python_bin" ]] || { echo "Set AI_PYTHON or create .venv first." >&2; exit 1; }
    [[ -f "${YOLO_WEIGHTS:-}" ]] || { echo "Set YOLO_WEIGHTS to the trained best.pt file." >&2; exit 1; }
fi
pids=()
cleanup() {
    trap - EXIT INT TERM
    for pid in "${pids[@]}"; do kill "$pid" 2>/dev/null || true; done
    wait || true
}
trap cleanup EXIT INT TERM
if [[ "$service" == all || "$service" == backend ]]; then
    java_cmd="${JAVA_HOME:+$JAVA_HOME/bin/}java"
    jar=$(find backend/target -maxdepth 1 -name '*.jar' -print -quit)
    "$java_cmd" -jar "$jar" > .runtime/backend.log 2>&1 & pids+=("$!")
fi
if [[ "$service" == all || "$service" == reporting ]]; then
    "$dotnet_bin" reporting/bin/Release/net10.0/Cypher.Reporter.dll > .runtime/reporting.log 2>&1 & pids+=("$!")
fi
if [[ "$service" == all || "$service" == ai ]]; then
    "$python_bin" -m uvicorn ai.app.main:app --host 127.0.0.1 --port "${AI_PORT:-5000}" > .runtime/ai.log 2>&1 & pids+=("$!")
fi
echo "Starting $service. Logs: $PWD/.runtime/ — press Ctrl+C to stop."
while true; do
    for pid in "${pids[@]}"; do
        if ! kill -0 "$pid" 2>/dev/null; then
            echo 'A service stopped. Check .runtime/*.log.' >&2
            exit 1
        fi
    done
    sleep 2
done
