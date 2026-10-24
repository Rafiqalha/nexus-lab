"""Allowlisted CI evidence: exit codes, test counts, source/binary hashes; no runtime data."""
from pathlib import Path
import hashlib
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
out = root / 'build/ci-evidence'
out.mkdir(parents=True, exist_ok=True)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


if len(sys.argv) > 2:
    label, command = sys.argv[1], sys.argv[2:]
    if command[0] == '--':
        command = command[1:]
    if not re.fullmatch(r'[a-z0-9-]+', label):
        raise SystemExit('Invalid evidence label')
    process = subprocess.Popen(command, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    summaries = []
    migration = []
    for line in process.stdout:
        print(line, end='', flush=True)
        if re.match(r'test result: (ok|FAILED)\.|Ran \d+ tests?\b|^OK$|^FAILED \(', line):
            summaries.append(line.strip())
        if label == 'migration':
            migration.append(line)
    code = process.wait()
    record = {'command': command, 'exit_code': code, 'test_summaries': summaries}
    if migration and code == 0:
        payload = ''.join(migration)
        record['migration_result'] = json.JSONDecoder().raw_decode(payload[payload.index('{'):])[0]
    (out / (label + '.json')).write_text(json.dumps(record, indent=2) + '\n')
    raise SystemExit(code)

files = subprocess.check_output(['git', 'ls-files', '-z'], cwd=root).decode().split('\0')
source = {f: digest(root / f) for f in files if f and (root / f).is_file()}
binaries = {}
for pattern in ('target/release/nexus-enrollment', 'target/release/libnexus_sdk.*',
                'target/release/nexus_sdk.dll', 'target/release/libnexus_lab.*',
                'target/*/release/libnexus_lab.so', 'app/build/outputs/apk/**/*.apk'):
    for path in root.glob(pattern):
        if path.is_file():
            binaries[str(path.relative_to(root))] = digest(path)
junit = []
for path in (root / 'app/build/test-results').glob('**/TEST-*.xml'):
    node = ET.parse(path).getroot()
    junit.append({key: node.get(key) for key in ('name', 'tests', 'failures', 'errors', 'skipped')})
manifest = {'schema': 1, 'commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
            'run_id': os.environ.get('GITHUB_RUN_ID'), 'runner': os.environ.get('RUNNER_OS'),
            'source_files_sha256': source, 'binary_sha256': binaries, 'junit_summaries': junit,
            'physical_device': 'NOT_RUN', 'production_approved': False}
manifest['evidence_files_sha256'] = {p.name: digest(p) for p in sorted(out.glob('*.json')) if p.name != 'MANIFEST.json'}
(out / 'MANIFEST.json').write_text(json.dumps(manifest, indent=2) + '\n')
