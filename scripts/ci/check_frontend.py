"""Check the deployed static frontend, including all inline JavaScript."""
from html.parser import HTMLParser
from pathlib import Path
import subprocess
import tempfile

class Scripts(HTMLParser):
    def __init__(self):
        super().__init__()
        self.active = False
        self.parts = []
    def handle_starttag(self, tag, attrs):
        self.active = tag == 'script' and 'src' not in dict(attrs)
    def handle_endtag(self, tag):
        if tag == 'script':
            self.active = False
    def handle_data(self, data):
        if self.active:
            self.parts.append(data)

for name in ('index.html', 'styles.css'):
    source = Path('frontend/src') / name
    deployed = Path('backend/src/main/resources/static') / name
    assert source.read_bytes() == deployed.read_bytes(), f'{name}: deployed copy differs'
parser = Scripts()
parser.feed(Path('frontend/src/index.html').read_text())
assert parser.parts, 'No frontend JavaScript found'
with tempfile.TemporaryDirectory() as directory:
    for index, script in enumerate(parser.parts):
        path = Path(directory) / f'inline-{index}.js'
        path.write_text(script)
        subprocess.run(['node', '--check', str(path)], check=True)
print('Frontend copies match; inline JavaScript syntax passed')
