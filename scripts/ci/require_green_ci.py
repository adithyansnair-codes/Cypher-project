"""Fail closed: deployment requires a successful push CI run for this exact SHA."""
import json
import os
import urllib.request

repo = os.environ['GITHUB_REPOSITORY']
sha = os.environ['GITHUB_SHA']
url = f'https://api.github.com/repos/{repo}/actions/workflows/ci.yml/runs?head_sha={sha}&event=push&per_page=100'
request = urllib.request.Request(url, headers={
    'Authorization': 'Bearer ' + os.environ['GH_TOKEN'],
    'Accept': 'application/vnd.github+json',
    'X-GitHub-Api-Version': '2022-11-28',
})
with urllib.request.urlopen(request, timeout=30) as result:
    runs = json.load(result)['workflow_runs']
assert runs, 'No CI push run exists for the selected commit'
latest = max(runs, key=lambda run: run['run_number'])
assert latest['head_sha'] == sha and latest['head_branch'] == 'main', 'CI is not for main at this SHA'
assert latest['status'] == 'completed' and latest['conclusion'] == 'success', 'Latest CI run is not successful'
print('Verified CI:', latest['html_url'])
