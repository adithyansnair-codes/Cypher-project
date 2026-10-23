"""Unit-test postprocessing using a fake YOLO adapter, not model accuracy."""
import importlib
import sys
from types import SimpleNamespace
from unittest.mock import Mock

import pytest


@pytest.fixture
def detector(monkeypatch):
    model = Mock()
    model.names = {0: 'person', 1: 'knife'}
    fake = SimpleNamespace(YOLO=Mock(return_value=model))
    monkeypatch.setitem(sys.modules, 'ultralytics', fake)
    sys.modules.pop('ai.app.detector', None)
    module = importlib.import_module('ai.app.detector')
    result = module.Detector()
    yield result
    sys.modules.pop('ai.app.detector', None)


def test_postprocessing_sorts_and_converts_to_percent(detector):
    box = lambda cls, conf: SimpleNamespace(cls=cls, conf=conf, xyxy=[[1.2, 2.2, 30.2, 40.2]])
    detector.model.predict.return_value = [SimpleNamespace(boxes=[box(0, .51), box(1, .93)])]
    found = detector.detect('fake-frame', conf=.5)
    assert [row['label'] for row in found] == ['knife', 'person']
    assert found[0]['confidence'] == 93
    assert found[0]['box'] == [1, 2, 30, 40]
    assert detector.model.predict.call_args.kwargs['conf'] == .5


def test_empty_predictions(detector):
    detector.model.predict.return_value = []
    assert detector.detect('fake-frame') == []


def test_weapon_classes_are_detected(detector):
    assert detector.is_weapon_model()
    detector.classes = {0: 'person'}
    assert not detector.is_weapon_model()
