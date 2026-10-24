"""Exercise the real gateway, migrated PostgreSQL and reporting worker in CI."""
import csv
import io
import json
import time
import urllib.error
import urllib.request
import zipfile

BASE = 'http://127.0.0.1:8081'


def request(path, data=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path, data=None if data is None else json.dumps(data).encode(), headers=headers)
    with urllib.request.urlopen(req, timeout=15) as result:
        return result.status, result.read()


for attempt in range(90):
    try:
        status, body = request('/health')
        assert json.loads(body)['status'] == 'UP'
        break
    except (OSError, AssertionError):
        time.sleep(2)
else:
    raise AssertionError('Gateway did not become healthy')
assert b'<html' in request('/')[1].lower()
try:
    request('/api/incidents')
    raise AssertionError('Anonymous incident access was allowed')
except urllib.error.HTTPError as error:
    assert error.code in (401, 403)
token = json.loads(request('/auth/login', {'email': 'admin@cypher.com', 'password': 'Admin@2026'})[1])['token']
analyst = json.loads(request('/auth/login', {'email': 'analyst@cypher.com', 'password': 'Analyst@2026'})[1])['token']
try:
    request('/api/incidents/ingest', {'cameraId': 1, 'title': 'forbidden'}, analyst)
    raise AssertionError('Analyst was allowed to ingest detections')
except urllib.error.HTTPError as error:
    assert error.code == 403
try:
    request('/api/incidents/ingest', {'title': ''}, token)
    raise AssertionError('Invalid ingest payload was accepted')
except urllib.error.HTTPError as error:
    assert error.code == 400
camera = json.loads(request('/api/cameras', token=token)[1])[0]['cameraId']
status, body = request('/api/incidents/ingest', {'cameraId': camera, 'title': 'CI contract detection', 'detectedObject': 'knife', 'confidence': 38, 'boundingBox': [1, 2, 30, 40]}, token)
incident = json.loads(body)
assert status == 201 and incident['severity'] == 'CRITICAL'
identifier = incident['incidentId']
assert json.loads(request(f'/api/incidents/{identifier}', token=token)[1])['title'] == 'CI contract detection'
for action, expected in [('acknowledge', 'ACKNOWLEDGED'), ('resolve', 'RESOLVED')]:
    assert json.loads(request(f'/api/incidents/{identifier}/{action}', {}, token)[1])['status'] == expected
for extension in ('csv', 'xlsx', 'pdf'):
    data = request('/api/reports/incidents.' + extension, token=token)[1]
    if extension == 'csv':
        rows = list(csv.DictReader(io.StringIO(data.decode('utf-8-sig'))))
        assert any(row['title'] == 'CI contract detection' for row in rows)
    elif extension == 'xlsx':
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            assert 'xl/workbook.xml' in archive.namelist()
    else:
        assert data.startswith(b'%PDF-') and len(data) > 1000
print('PASS: frontend, authentication, AI ingest contract, persistence, incident lifecycle, CSV/XLSX/PDF')
