#!/usr/bin/env bash
#
# CYPHER AI demo — one command, with a fallback.
#
#   ./ai/demo.sh              # live Mac webcam
#   ./ai/demo.sh image         # known-good test image (guaranteed to detect)
#   ./ai/demo.sh image 3       # a specific test image by index
#
# WHY THIS EXISTS
#   The fine-tuned detector has 0.345 recall on knives — small, low-contrast,
#   often occluded. A live webcam demo can simply miss. This script runs the
#   live camera by default but falls back to a labelled test image on demand,
#   so the demonstration never depends on the model getting lucky.
#
# It checks its own prerequisites first, because "the app did not start" is a
# far more common demo failure than "the model missed".

set -uo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"
# Look for a project venv first, then the shared ML venv used during development.
# Each candidate falls back to an absolute path so the script works no matter
# what directory it is invoked from.
for candidate in "$ROOT/.venv/bin/python" \
                 "$ROOT/../.dsh-scratch/ml/bin/python" \
                 "/Users/adithyansnair/CYPHER/.dsh-scratch/ml/bin/python"; do
    if [ -x "$candidate" ]; then PY="$candidate"; break; fi
done
PY="${PY:-}"

MODE="${1:-camera}"
INDEX="${2:-0}"
GATEWAY="${GATEWAY_URL:-http://localhost:8081}"
DATASET="$HOME/Downloads/archive (3)/guns-knives-yolo/guns-knives-yolo/test/images"

# 0.35 is a deliberate trade-off for a live demo. recall matters more than
# precision when a human is holding a knife at a camera, but 0.25 proved too
# loose: it surfaced a 38%-confidence pistol false-positive alongside a real
# knife. Measured detection rates on the 385-image test set:
#     0.25 -> 329 images with a detection   0.45 -> 256   0.60 -> 223
#     0.35 -> 293 (used here)
export YOLO_CONFIDENCE="${YOLO_CONFIDENCE:-0.35}"
export GATEWAY_URL="$GATEWAY"

red()   { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
bold()  { printf '\033[1m%s\033[0m\n' "$*"; }
step()  { printf '\n\033[1m==> %s\033[0m\n' "$*"; }

# --------------------------------------------------------------- preflight ---
step "Preflight"

if [ ! -x "$PY" ]; then
    red "  no python found at $PY"
    echo "  create the venv:  python3 -m venv .venv && .venv/bin/pip install -r ai/requirements.txt"
    exit 1
fi
green "  python            $($PY -c 'import sys;print(sys.version.split()[0])')"

if [ ! -f "$ROOT/ai/models/weapon_best.pt" ]; then
    red "  trained weights missing: ai/models/weapon_best.pt"
    echo "  the service will fall back to stock COCO weights (people, not weapons)"
fi

if curl -fsS -m 5 "$GATEWAY/health" >/dev/null 2>&1; then
    green "  gateway           $GATEWAY reachable"
else
    red   "  gateway           $GATEWAY NOT reachable"
    echo
    echo "  Start the backend first, in another terminal:"
    echo "      cd backend && ./mvnw spring-boot:run"
    exit 1
fi

if [ "$MODE" = "camera" ]; then
    if ! "$PY" - <<'PYEOF' 2>/dev/null
import sys
try:
    import cv2
    cap = cv2.VideoCapture(0)
    ok = cap.isOpened()
    cap.release()
    sys.exit(0 if ok else 1)
except Exception:
    sys.exit(1)
PYEOF
    then
        red "  webcam            NOT accessible"
        echo "  macOS needs camera permission for Terminal:"
        echo "    System Settings -> Privacy & Security -> Camera -> enable Terminal"
        echo
        echo "  Or run the guaranteed fallback instead:   ./ai/demo.sh image"
        exit 1
    fi
    green "  webcam            accessible"
fi

bold "  confidence        $YOLO_CONFIDENCE  (lower = catches more, false-positives more)"
bold "  mode              $MODE"

# ------------------------------------------------------------------- run -----
step "Raising an incident through the AI pipeline"

if [ "$MODE" = "image" ]; then
    if [ ! -d "$DATASET" ]; then
        red "  test images not found at:"
        echo "    $DATASET"
        exit 1
    fi

    "$PY" - "$INDEX" <<'PYEOF'
import sys, glob, cv2
sys.path.insert(0, '.')
from ai.app.detector import Detector
from ai.app.gateway import GatewayClient

index = int(sys.argv[1])
det, gw = Detector(), GatewayClient()
imgs = sorted(glob.glob(__import__('os').path.expanduser(
    "~/Downloads/archive (3)/guns-knives-yolo/guns-knives-yolo/test/images/*.jpg")))

print(f"  scanning test images for a detection (this takes a few seconds)...")
chosen = None
# Take the Nth image that actually contains a detection, so any index works.
found_so_far = 0
for p in imgs:
    frame = cv2.imread(p)
    if frame is None:
        continue
    d = det.detect(frame)
    if d:
        found_so_far += 1
        if found_so_far == index + 1:
            chosen = (p, d)
            break

if not chosen:
    print(f"  no image with a detection at position {index}")
    sys.exit(1)

path, found = chosen
print(f"\n  image   {path.split('/')[-1]}")
for d in found:
    print(f"    detected {d['label']:8} conf={d['confidence']:.1f}%")

best = {}
for d in found:
    l = d['label'].lower()
    if l not in best or d['confidence'] > best[l]['confidence']:
        best[l] = d

for label, d in best.items():
    if label in {"knife", "pistol", "gun", "weapon"}:
        inc = gw.report_incident(
            title=f"{label.capitalize()} detected",
            detected_object=label,
            confidence=d["confidence"],
            description=f"{label} detected by the CYPHER AI engine.",
        )
        print(f"\n  -> incident #{inc['incidentId']}   severity={inc['severity']}"
              f"   status={inc['status']}")
PYEOF
else
    "$PY" - <<'PYEOF'
import sys
sys.path.insert(0, '.')
# Construct the detector directly. Importing it from ai.app.main returns None,
# because main.py only builds it inside FastAPI's startup hook -- which never
# runs when the module is imported rather than served. That is what broke the
# camera path with "NoneType has no attribute detect".
from ai.app import main as ai_main
from ai.app.detector import Detector

print("  loading model...")
ai_main.detector = Detector()
print(f"  weights: {ai_main.detector.weights}")
print(f"  classes: {list(ai_main.detector.classes.values())}")
print()
result = ai_main.monitor_stream(source="0", max_seconds=45)
print(f"\n  frames processed : {result['frames_processed']}")
print(f"  objects seen     : {result['objects_seen'] or 'nothing'}")
print(f"  confirmed        : {result.get('confirmed') or 'nothing held long enough'}")
print(f"  confirmation     : a label must appear in {result.get('confirmation_frames', 8)} "
      f"consecutive frames")
print(f"  incidents raised : {result['incidents_raised']}")
if result['incidents_raised'] == 0:
    print("\n  No weapon detected. This is expected sometimes -- knife recall is 0.345.")
    print("  Try: hold the knife flat toward the camera, fill more of the frame,")
    print("       move closer, and make sure the lighting is bright.")
    print("  Guaranteed fallback:   ./ai/demo.sh image")
PYEOF
fi

step "Open the cockpit"
echo "  $GATEWAY/"
echo "  log in as admin@cypher.com / Admin@2026"
echo
green "  Any incident raised above should be at the top of the stream."
