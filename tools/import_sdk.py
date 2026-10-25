#!/usr/bin/env python3
"""Import an xtask-produced default ARM64 package, generate only used C declarations.
No Nexus Rust source dependency. Strict parser fails closed on unsupported C syntax.
"""
import json,re,sys,hashlib,shutil
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=Path(sys.argv[1]).resolve(); sha=sys.argv[2]
m=json.loads((p/'MANIFEST.json').read_text())
if m.get('source_commit') != sha or m.get('source_dirty') is not False:raise SystemExit('SDK package must identify the supplied clean source commit')
assert m['target']=='aarch64-linux-android' and m['features'] in [[],['staging-enrollment']] and not m['production_approved']
if m['features'] and '--staging' not in sys.argv:raise SystemExit('Staging import requires explicit --staging')
lib=p/'libnexus_sdk.so'; digest=hashlib.sha256(lib.read_bytes()).hexdigest();assert digest==m['binary_audit']['sha256'];assert len(m['binary_audit']['exports'])==60
header=p/'include/nexus/nexus_v2.h'
if not header.exists():header=p/'nexus_v2.h'
hash_header=hashlib.sha256(header.read_bytes()).hexdigest()
assert any(f['path']==header.name and f['sha256']==hash_header for f in m['files'])
import subprocess
subprocess.run([sys.executable,str(root/"tools/generate_bindings.py"),str(header)],check=True)
v=root/('vendor/android-arm64-'+('staging-' if m['features'] else 'default-')+m['binary_audit']['sha256'][:12]+'-'+hashlib.sha256((p/'MANIFEST.json').read_bytes()).hexdigest()[:12]);v.parent.mkdir(exist_ok=True)
if v.exists():raise SystemExit('Refuse overwrite vendor package; select a fresh lab/import')
shutil.copytree(p,v)
(root/'SDK_IDENTITY.json').write_text(json.dumps({'source_sha':sha,'source_digest':m['source_digest'],'target':m['target'],'staging_root_public_key':m.get('staging_root_public_key'),'profile':'staging-enrollment' if m['features'] else 'default-preview','abi':'2.11','package_directory':str(v.relative_to(root)),'exports':60,'sdk_sha256':digest,'header_sha256':hash_header,'package_manifest_sha256':hashlib.sha256((p/'MANIFEST.json').read_bytes()).hexdigest()},indent=2)+'\n')
print('SDK_IMPORTED',digest)

legal=root/'app/src/main/assets/nexus-sdk';legal.mkdir(parents=True,exist_ok=True)
shutil.copy2(v/'NOTICE.txt',legal/'NOTICE.txt')
shutil.copytree(v/'DEPENDENCY_LICENSES',legal/'DEPENDENCY_LICENSES',dirs_exist_ok=True)
(root/'native/include').mkdir(exist_ok=True)
shutil.copy2(header,root/'native/include/nexus_v2.h')
