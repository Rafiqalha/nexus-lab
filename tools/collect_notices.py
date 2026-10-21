#!/usr/bin/env python3
"""Preserve declared license/notice files for the lab Rust dependency closure."""
from pathlib import Path
import json,subprocess,shutil
root=Path(__file__).resolve().parents[1]
meta=json.loads(subprocess.check_output(['cargo','metadata','--locked','--format-version','1','--filter-platform','aarch64-linux-android'],cwd=root))
destination=root/'app/src/main/assets/nexus-jni';destination.mkdir(parents=True,exist_ok=True)
packages=[]
for package in meta['packages']:
 if package['name']=='nexus-android-lab-jni':continue
 source=Path(package['manifest_path']).parent
 files=[p for p in source.iterdir() if p.is_file() and p.name.lower().startswith(('license','licence','copying','notice','copyright'))]
 if package.get('license_file'):
  p=source/package['license_file']
  if p.is_file() and p not in files:files.append(p)
 if not files and package['name']=='jni-sys-macros' and package['repository']=='https://github.com/jni-rs/jni-sys':
  # Published subcrate omits root texts. Preserve them from its exact upstream VCS commit.
  import urllib.request
  sha=json.loads((source/'.cargo_vcs_info.json').read_text())['git']['sha1']
  target=destination/(package['name']+'-'+package['version']);target.mkdir(exist_ok=True)
  for name in ['LICENSE-APACHE','LICENSE-MIT']:
   url=f'https://raw.githubusercontent.com/jni-rs/jni-sys/{sha}/{name}'
   (target/name).write_bytes(urllib.request.urlopen(url,timeout=30).read())
  files=list(target.iterdir())
 if not files:raise RuntimeError('Missing notice for '+package['name'])
 output=destination/(package['name']+'-'+package['version']);output.mkdir(exist_ok=True)
 for p in files:
  if p.resolve()!=(output/p.name).resolve():shutil.copy2(p,output/p.name)
 packages.append({'name':package['name'],'version':package['version'],'declared_license':package.get('license'),'files':[str(p.relative_to(destination)) for p in output.iterdir()]})
(destination/'DEPENDENCIES.json').write_text(json.dumps(packages,indent=2)+'\n')
(destination/'NOTICE.txt').write_text('Third-party Rust JNI dependencies; original notices retained below. Declared metadata is not a license grant for Nexus source. CameraX/AndroidX and Kotlin runtime are Apache-2.0 components; their upstream terms also apply. No production distribution approval is implied.\n')
print('JNI_DEPENDENCY_NOTICES',len(packages))
