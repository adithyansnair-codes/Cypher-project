"""Confirmation and cooldown for video detections."""
from collections import defaultdict
import time

REPORTABLE = {"knife", "pistol", "gun", "weapon", "rifle", "fire", "smoke", "fall", "lying"}


class DetectionPolicy:
    def __init__(self, frames=8, cooldown=90, max_gap=5, clock=time.monotonic):
        self.frames = frames
        self.cooldown = cooldown
        self.max_gap = max_gap
        self.clock = clock
        self.streak = defaultdict(int)
        self.raised = {}
        self.last_frame = {}

    def observe(self, camera, detections):
        now = self.clock()
        best = {}
        for detection in detections:
            label = str(detection["label"]).lower()
            if label in REPORTABLE and (label not in best or
                    detection["confidence"] > best[label]["confidence"]):
                best[label] = detection
        expired = now - self.last_frame.get(camera, now) > self.max_gap
        for key in list(self.streak):
            if key[0] == camera and (expired or key[1] not in best):
                del self.streak[key]
        self.last_frame[camera] = now
        for label in best:
            self.streak[camera, label] += 1
        return best

    def on_cooldown(self, camera, label):
        last = self.raised.get((camera, label))
        return last is not None and self.clock() - last < self.cooldown

    def mark_reported(self, camera, label):
        self.raised[camera, label] = self.clock()

    def reset(self, camera=None):
        for state in (self.streak, self.raised):
            for key in list(state):
                if camera is None or key[0] == camera:
                    del state[key]
        if camera is None:
            self.last_frame.clear()
        else:
            self.last_frame.pop(camera, None)
