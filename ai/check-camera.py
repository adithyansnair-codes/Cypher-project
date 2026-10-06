#!/usr/bin/env python3
"""Check the webcam and the detector together. Run this in YOUR terminal.

Prints a verdict instead of a traceback so the demo never fails cryptically.
"""
import sys, time, os

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))

# The ML packages live in a virtualenv, not system python. If cv2 is missing,
# re-run ourselves under a known interpreter rather than failing with a bare
# ModuleNotFoundError that says nothing about what to do.
try:
    import cv2  # noqa: F401
except ModuleNotFoundError:
    CANDIDATES = [
        os.path.join(HERE, "..", ".venv", "bin", "python"),
        os.path.join(HERE, "..", ".dsh-scratch", "ml", "bin", "python"),
        "/Users/adithyansnair/CYPHER/.dsh-scratch/ml/bin/python",
    ]
    for candidate in CANDIDATES:
        candidate = os.path.abspath(candidate)
        if os.path.exists(candidate):
            os.execv(candidate, [candidate, os.path.abspath(__file__), *sys.argv[1:]])
    print("FAIL  opencv is not installed for this python.")
    print(f"      interpreter: {sys.executable}")
    print()
    print("      Use the project virtualenv instead:")
    print("          .venv/bin/python ai/check-camera.py")
    print("      or create one:")
    print("          python3 -m venv .venv")
    print("          .venv/bin/pip install -r ai/requirements.txt")
    raise SystemExit(1)

import cv2

def main() -> int:
    cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        print("FAIL  the camera did not open.")
        print()
        print("      If the message above says 'not authorized to capture video',")
        print("      open System Settings -> Privacy & Security -> Camera and")
        print("      enable the app you are running this from (Terminal, iTerm,")
        print("      VS Code...). Then QUIT and REOPEN that app -- macOS only")
        print("      asks once per app.")
        return 1

    ok, frame = cap.read()
    if not ok or frame is None:
        print("FAIL  the camera opened but returned no frame.")
        print("      Another app is probably using it (Zoom, Photo Booth, a browser tab).")
        cap.release()
        return 1

    print(f"OK    webcam opened, frame is {frame.shape[1]}x{frame.shape[0]}")

    from ai.app.detector import Detector
    det = Detector()
    print(f"OK    model loaded: {os.path.basename(det.weights)} -> {list(det.classes.values())}")

    t0 = time.time()
    det.detect(frame)
    print(f"OK    inference: {(time.time() - t0) * 1000:.0f} ms")

    n, t0 = 0, time.time()
    for _ in range(15):
        ok, f = cap.read()
        if not ok:
            break
        det.detect(f)
        n += 1
    cap.release()
    if n:
        print(f"OK    sustained {n / (time.time() - t0):.1f} fps over {n} frames")

    print()
    print("Everything is ready. Run:  ./ai/demo.sh")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
