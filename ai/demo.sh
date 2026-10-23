#!/usr/bin/env bash
set -euo pipefail

# Current live demo:
#   ./ai/demo.sh           Mac webcam for 45 seconds
#   ./ai/demo.sh image     bundled knife photo -> real incident
#   ./ai/demo.sh preview   bundled knife photo -> no database write

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCRATCH="$(cd "$ROOT/.." && pwd)"
PYTHON="${CYPHER_PYTHON:-$SCRATCH/ml/bin/python}"
WEIGHTS="${CYPHER_WEIGHTS:-$SCRATCH/training/runs/cypher-v3-6class/weights/best.pt}"
GATEWAY="${GATEWAY_URL:-http://localhost:8081}"
MODE="${1:-camera}"

if [[ ! -x "$PYTHON" ]]; then
    PYTHON="python3"
fi
if [[ ! -f "$WEIGHTS" ]]; then
    printf 'Model weights not found: %s\n' "$WEIGHTS" >&2
    printf 'Set CYPHER_WEIGHTS to the trained best.pt file.\n' >&2
    exit 1
fi
if ! curl -fsS --max-time 5 "$GATEWAY/health" >/dev/null; then
    printf 'Gateway is not healthy at %s\n' "$GATEWAY" >&2
    exit 1
fi

case "$MODE" in
    image|preview)
        export CYPHER_WEIGHTS="$WEIGHTS"
        export CYPHER_PYTHON="$PYTHON"
        export CYPHER_COCKPIT_URL="$GATEWAY"
        if [[ "$MODE" == "preview" ]]; then
            exec "$ROOT/ai/demo-ml.sh" preview
        fi
        exec "$ROOT/ai/demo-ml.sh" live
        ;;
    camera)
        exec "$PYTHON" "$ROOT/ai/cli.py" \
            --source "${CYPHER_CAMERA_INDEX:-0}" \
            --seconds "${CYPHER_SECONDS:-45}" \
            --weights "$WEIGHTS" \
            --gateway "$GATEWAY" \
            --camera-id "${CYPHER_CAMERA_ID:-1}" \
            --conf "${CYPHER_CONF:-0.35}"
        ;;
    *)
        printf 'Usage: %s [camera|image|preview]\n' "$0" >&2
        exit 2
        ;;
esac
