import unittest
from ai.app.policy import DetectionPolicy


class PolicyTests(unittest.TestCase):
    def setUp(self):
        self.now = 100.0
        self.policy = DetectionPolicy(clock=lambda: self.now)
        self.knife = {"label": "knife", "confidence": 80}

    def frames(self, count, camera=1, detections=None):
        for _ in range(count):
            self.now += 0.1
            self.policy.observe(camera, [self.knife] if detections is None else detections)

    def test_missing_frame_resets_streak(self):
        self.frames(7)
        self.frames(1, detections=[])
        self.frames(1)
        self.assertEqual(self.policy.streak[1, "knife"], 1)

    def test_duplicates_count_as_one_frame(self):
        self.frames(1, detections=[self.knife] * 8)
        self.assertEqual(self.policy.streak[1, "knife"], 1)

    def test_cameras_have_separate_streaks(self):
        self.frames(7)
        self.frames(1, camera=2)
        self.assertEqual(self.policy.streak[2, "knife"], 1)

    def test_person_alone_is_not_an_incident(self):
        self.frames(8, detections=[{"label": "person", "confidence": 99}])
        self.assertEqual(dict(self.policy.streak), {})

    def test_stale_frames_do_not_confirm(self):
        self.frames(7)
        self.now += 6
        self.frames(1)
        self.assertEqual(self.policy.streak[1, "knife"], 1)

    def test_cooldown_expires_and_failed_report_can_retry(self):
        self.frames(8)
        self.assertFalse(self.policy.on_cooldown(1, "knife"))
        self.policy.mark_reported(1, "knife")
        self.assertTrue(self.policy.on_cooldown(1, "knife"))
        self.assertFalse(self.policy.on_cooldown(2, "knife"))
        self.now += 90
        self.assertFalse(self.policy.on_cooldown(1, "knife"))


if __name__ == "__main__":
    unittest.main()
