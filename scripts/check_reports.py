"""Smoke test a running reporting worker against a disposable database."""
import csv
import io
import json
import os
import time
import urllib.request
import zipfile

base = os.getenv('REPORT_TEST_URL', 'http://127.0.0.1:5001')
for attempt in range(30):
    try:
        with urllib.request.urlopen(base + '/health', timeout=2) as response:
            assert json.load(response)['database'] == 'UP'
        break
    except (OSError, AssertionError):
        if attempt == 29:
            raise
        time.sleep(1)

for fmt in ('csv', 'xlsx', 'pdf'):
    with urllib.request.urlopen(base + '/reports/incidents.' + fmt, timeout=30) as response:
        data = response.read()
        assert response.status == 200
    if fmt == 'csv':
        rows = list(csv.reader(io.StringIO(data.decode('utf-8-sig'))))
        assert len(rows) >= 2, 'The test database needs at least one incident'
    elif fmt == 'xlsx':
        with zipfile.ZipFile(io.BytesIO(data)) as workbook:
            assert 'xl/workbook.xml' in workbook.namelist()
            assert 'xl/worksheets/sheet1.xml' in workbook.namelist()
    else:
        assert data.startswith(b'%PDF-')
    print(f'{fmt}: OK ({len(data)} bytes)')

with urllib.request.urlopen(base + '/reports/incidents.csv?status=NO_SUCH_STATUS', timeout=30) as response:
    rows = list(csv.reader(io.StringIO(response.read().decode('utf-8-sig'))))
    assert len(rows) == 1, 'Status filter should return only the header'
print('Status filter: OK')
