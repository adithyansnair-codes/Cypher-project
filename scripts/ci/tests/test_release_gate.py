"""Deployment must fail closed for missing, failed or wrong-branch CI evidence."""
import io
import json
from pathlib import Path
import runpy
import urllib.request

import pytest

SCRIPT = Path(__file__).parents[1] / 'require_green_ci.py'
SHA = 'a' * 40


def execute(monkeypatch, runs):
    monkeypatch.setenv('GITHUB_REPOSITORY', 'example/cypher')
    monkeypatch.setenv('GITHUB_SHA', SHA)
    monkeypatch.setenv('GH_TOKEN', 'test-token')
    monkeypatch.setattr(urllib.request, 'urlopen', lambda *args, **kwargs: io.BytesIO(json.dumps({'workflow_runs': runs}).encode()))
    runpy.run_path(str(SCRIPT), run_name='__main__')


def run(number=1, **changes):
    result = dict(run_number=number, head_sha=SHA, head_branch='main', status='completed', conclusion='success', html_url='https://example.test/run')
    return result | changes


def test_success_is_accepted(monkeypatch):
    execute(monkeypatch, [run()])


@pytest.mark.parametrize('runs', [[], [run(conclusion='failure')], [run(status='in_progress')], [run(head_sha='b' * 40)], [run(head_branch='feature')], [run(), run(2, conclusion='failure')]])
def test_missing_or_unsuccessful_evidence_is_rejected(monkeypatch, runs):
    with pytest.raises(AssertionError):
        execute(monkeypatch, runs)
