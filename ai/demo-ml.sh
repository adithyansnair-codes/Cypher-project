#!/usr/bin/env bash
set -euo pipefail

# One-command teacher demo:
#   ./ai/demo-ml.sh          safe preview, no database changes
#   ./ai/demo-ml.sh live     raises a real incident in the local cockpit

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCRATCH="$(cd "$ROOT/.." && pwd)"
PYTHON="${CYPHER_PYTHON:-$SCRATCH/ml/bin/python}"
WEIGHTS="${CYPHER_WEIGHTS:-$SCRATCH/training/runs/cypher-v3-6class/weights/best.pt}"
IMAGE="${CYPHER_DEMO_IMAGE:-$ROOT/ai/evidence/demo/knife.jpg}"
MODE="${1:-preview}"
COCKPIT_URL="${CYPHER_COCKPIT_URL:-http://localhost:8081}"

if [[ ! -x "$PYTHON" ]]; then
    PYTHON="python3"
fi
if [[ ! -f "$WEIGHTS" ]]; then
    printf 'Model weights not found: %s\n' "$WEIGHTS" >&2
    printf 'Set CYPHER_WEIGHTS to the trained best.pt file.\n' >&2
    exit 1
fi
if [[ ! -f "$IMAGE" ]]; then
    printf 'Demo image not found: %s\n' "$IMAGE" >&2
    printf 'Set CYPHER_DEMO_IMAGE to a photo containing a weapon.\n' >&2
    exit 1
fi

printf '\n%s\n' 'ALGORITHM'
printf '%s\n' 'YOLOv8s @ 640 px -> class-aware NMS -> one best box per class'
printf '%s\n' '-> 8-frame confirmation -> 90 s cooldown -> gateway severity rule'
printf '%s\n' '-> transactional PostgreSQL ingest -> post-commit WebSocket update'

CLI_ARGS=(
    "$ROOT/ai/cli.py"
    --source "$IMAGE"
    --weights "$WEIGHTS"
    --gateway "$COCKPIT_URL"
    --camera-id "${CYPHER_CAMERA_ID:-1}"
    --conf "${CYPHER_CONF:-0.35}"
)

case "$MODE" in
    preview|dry|dry-run)
        CLI_ARGS+=(--dry-run)
        ;;
    live|post)
        if ! curl -fsS --max-time 3 "$COCKPIT_URL/health" >/dev/null; then
            printf 'Gateway is not healthy at %s\n' "$COCKPIT_URL" >&2
            printf 'Start the backend, or run ./ai/demo-ml.sh for a dry run.\n' >&2
            exit 1
        fi
        ;;
    *)
        printf 'Usage: %s [preview|live]\n' "$0" >&2
        exit 2
        ;;
esac

"$PYTHON" "${CLI_ARGS[@]}"

printf '\nCockpit: %s\n' "$COCKPIT_URL"
printf '%s\n' 'Open: Latest signals -> Live trace -> Algorithms -> Audit trail'
