# CYPHER — ML teacher demonstration

## What the teacher should see

The demonstration proves the complete chain, not only a model predicting a box:

```text
photo / camera
    -> YOLOv8s detection
    -> class-aware NMS
    -> one best box per class
    -> temporal confirmation and cooldown
    -> gateway severity rule
    -> transactional database ingest
    -> live cockpit update
    -> audit trail and export
```

The most important sentence is: **YOLO supplies the detector; our project turns
it into a reliable, auditable incident system.**

## Before the demonstration

Open the local cockpit at `http://localhost:8081` and sign in. The gateway and
AI engine run as local background services; no terminal is needed during the
presentation.

The trained checkpoint is stored outside Git because it is 21 MB:

```text
~/CYPHER/.dsh-scratch/training/runs/cypher-v3-6class/weights/best.pt
```

## Three-minute presentation

### 1. State the algorithm — 25 seconds

Open **Algorithms** in the cockpit and point to the first two cards.

> CYPHER uses YOLOv8s, a single-stage detector. Each 640-pixel frame produces
> boxes, classes and confidences in one forward pass. Ultralytics applies
> class-aware non-maximum suppression. We then keep one box per class and
> require temporal evidence before an incident is created.

### 2. Show measured evidence — 35 seconds

Open [`ai/evidence/README.md`](../ai/evidence/README.md) and show the headline
table and one precision–recall or confusion-matrix image.

> On the same fixed 385-image test set, mAP@50 improved from 0.589 to 0.890 and
> knife recall improved from 0.345 to 0.843. We report the wider six-class
> validation result separately: mAP@50 0.744. We have also included the
> limitations rather than hiding them.

### 3. Run the real pipeline from the website — 45 seconds

1. Open **Live detection** in the cockpit.
2. Start the Mac webcam, or select an image from the Mac.
3. Hold the object in good light and press **Analyze webcam frame** or
   **Analyze selected image**.
4. Show the annotated frame with its bounding box and confidence.
5. Press **Confirm detection and raise incident**.

Webcam analysis samples five frames and keeps only detections present in at
least two. Confirmation then checks eight consecutive frames before posting to
the gateway.

Reliable manual upload examples are bundled under `ai/evidence/demo/`:

- `smoke.jpg` — smoke detected at approximately 90% confidence
- `crowd.jpg` — 53 person boxes, best confidence approximately 91%
- `knife.jpg` — held-out knife image, approximately 92% confidence

Use **Choose another image** and select one of these files when demonstrating a
specific class. They are manual uploads; no sample is run automatically.

### 4. Show the result in the cockpit — 45 seconds

At `http://localhost:8081`:

1. Open **Latest signals** and show the new incident.
2. Open **Live trace** to show detection, de-duplication, streak, cooldown and
   severity decisions.
3. Open **Analytics** to show the live aggregate.
4. Open **Audit trail** to show that the incident change was recorded.
5. Export CSV/Excel/PDF from the reporting surface.

### 5. Answer “AI can do this, what is your contribution?” — 30 seconds

Use the **Our engineering contribution** card:

- We merged and normalised five differently structured dataset sources, mapped
  their class names, corrected skewed splits and preserved a fixed test set.
- We replaced the small 416-pixel two-class prototype with a measured
  six-class 640-pixel model.
- We observed 1,282 frames of false pistol detections on an empty webcam, then
  introduced class-aware de-duplication, eight-frame confirmation and a
  90-second cooldown.
- We moved safety semantics into the gateway, made incident/alert writes
  transactional and publish live updates only after commit.
- We exposed runtime decisions through Live trace and added RBAC, audit history
  and multi-format reporting.
- We connected the same model to a browser workspace, terminal CLI, FastAPI
  service and ESP32-CAM stream through one incident contract.

## Honest questions to expect

**“Why is the six-class score lower?”**

The 0.890 number is a focused weapon comparison on a fixed held-out set. The
0.744 number covers all six classes on the merged validation split. They measure
different populations, so they must not be presented as the same benchmark.

**“Is lying/fall accurate?”**

The report shows a high validation score, but only 15 validation boxes support
it. It proves the integration path; more real fall footage is required before
calling it production-ready.

**“Did you train YOLO from scratch?”**

No. We used transfer learning from COCO weights. Our contribution is dataset
engineering, evaluation, false-positive handling, incident semantics,
transactional integration, observability and the complete working product.

## Supporting material

- Model card: [`ai/evidence/README.md`](../ai/evidence/README.md)
- Machine-readable metrics: [`ai/evidence/metrics.json`](../ai/evidence/metrics.json)
- Training pipeline: [`ai/training/README.md`](../ai/training/README.md)
- Browser detection workspace: `backend/src/main/resources/static/index.html`
- Same contract used by all inputs: [`ai/cli.py`](../ai/cli.py)
