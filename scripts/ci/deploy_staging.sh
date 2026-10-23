#!/usr/bin/env bash
# Runs remotely on a separately provisioned staging host, never creates cloud resources.
set -euo pipefail
release=${1:?release directory required}
[[ "$release" =~ ^/opt/cypher-staging/releases/[0-9a-f]{40}$ ]] || exit 2
cd "$release"
test -f /opt/cypher-staging/ALLOW_STAGING_DEPLOY
test -f /opt/cypher-staging/.env
# Credentials live on the host; neither CI artifacts nor Git contain them.
set -a
source ./images.env
set +a
[[ "$BACKEND_IMAGE" =~ @sha256:[0-9a-f]{64}$ ]]
[[ "$REPORTING_IMAGE" =~ @sha256:[0-9a-f]{64}$ ]]
compose=(docker compose --project-name cypher-staging --env-file /opt/cypher-staging/.env -f compose.staging.yml)
"${compose[@]}" config --quiet
"${compose[@]}" pull
"${compose[@]}" up -d --wait --wait-timeout 180
for attempt in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:8081/health | python3 -c 'import json,sys; assert json.load(sys.stdin)["status"] == "UP"'; then
    # Check worker readiness from the container network without publishing its port.
    if "${compose[@]}" exec -T backend bash -c 'exec 3<>/dev/tcp/reporting/5001; printf "GET /health HTTP/1.0\r\nHost: reporting\r\n\r\n" >&3; cat <&3' | grep -q '"database":"UP"'; then
      ln -sfn "$release" /opt/cypher-staging/current
      echo "Staging release healthy: ${release##*/}"
      exit 0
    fi
  fi
  sleep 3
done
echo 'Staging health failed. Inspect service logs; do not blindly roll back database migrations.' >&2
exit 1
