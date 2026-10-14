# CYPHER Keras Stage 4 model

Experimental CYPHER person/knife detector trained separately using TensorFlow/KerasCV.

## Training stack

- TensorFlow 2.13.0
- Keras 2.13.1
- KerasCV 0.6.4
- YOLOv8 XS backbone pretrained on COCO

## Classes

- 0: person
- 1: knife

## Dataset

- Training images: 7,326
- Validation images: 362
- Training boxes: 28,325
- Validation boxes: 1,395

## Stage 4 result

Validation loss: 2.9555

## Important

This file is a Keras `.weights.h5` checkpoint.

The current CYPHER production AI engine on `main` uses Ultralytics/PyTorch `.pt`
weights, so this checkpoint is preserved as an experimental trained model and
cannot be loaded by the current Ultralytics `YOLO()` loader without a separate
Keras inference adapter.
