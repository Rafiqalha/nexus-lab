#!/usr/bin/env python3
"""Import an xtask-produced default ARM64 package, generate only used C declarations.
No Nexus Rust source dependency. Strict parser fails closed on unsupported C syntax.
"""
import json,re,sys,hashlib,shutil
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=Path(sys.argv[1]).resolve(); sha=sys.argv[2]
m=json.loads((p/'MANIFEST.json').read_text());assert m['target']=='aarch64-linux-android' and m['features']==[] and not m['production_approved']
lib=p/'libnexus_sdk.so'; digest=hashlib.sha256(lib.read_bytes()).hexdigest();assert digest==m['binary_audit']['sha256'];assert len(m['binary_audit']['exports'])==58
header=p/'include/nexus/nexus_v2.h'
if not header.exists():header=p/'nexus_v2.h'
hash_header=hashlib.sha256(header.read_bytes()).hexdigest()
assert any(f['path']==header.name and f['sha256']==hash_header for f in m['files'])
s=re.sub(r'/\*.*?\*/','',header.read_text(),flags=re.S)
assert '#define NEXUS_V2_ABI_MINOR 9' in s
names=['NexusV2FrameInput','NexusV2CaptureConfigV2','NexusV2CaptureProgress','NexusV2CaptureProgressV2','NexusV2ProcessingCapabilities']
base={'uint32_t':'u32','uint64_t':'u64','uint8_t':'u8','int32_t':'i32','double':'f64','void':'()'}
def typ(t):
 t=' '.join(t.split())
 a=re.fullmatch(r'(.+)\[(\d+)\]',t)
 if a:return f'[{typ(a[1].strip())}; {a[2]}]'
 if t.endswith('*'):
  inner=t[:-1].strip();c=inner.startswith('const ')
  return ('*const ' if c else '*mut ')+typ(inner.removeprefix('const '))
 if t.startswith('struct '):t=t[7:]
 if t in names:return t
 return base[t]
def declaration(t):
 match=re.fullmatch(r'(.+?[\s*])(\w+)(\[\d+\])?',t.strip());assert match,t
 return match[2],typ(match[1].strip()+(match[3] or ''))
lines=['// Generated from packaged header SHA256 '+hash_header+'. Do not hand-edit.']
for name in names:
 body=re.search(r'typedef struct '+name+r'\s*\{(.*?)\}\s*'+name+';',s,re.S)[1]
 lines+=['#[repr(C)]','pub struct '+name+' {']
 for field in body.split(';'):
  if field.strip():n,t=declaration(field);lines+=['    pub '+n+': '+t+',']
 lines+=['}']
functions={'abi_version','build_profile','session_create','session_initialize','session_destroy','capture_create_v2','capture_submit_v2','capture_progress_v2','capture_finalize','capture_result_read_v2','capture_result_validate','capture_result_release','capture_cancel','capture_release','processing_capabilities'}
lines+=['unsafe extern "C" {']
for suffix in sorted(functions):
 name='nexus_v2_'+suffix
 match=re.search(r'(uint32_t|int32_t)\s+'+name+r'\s*\((.*?)\)\s*;',s,re.S);assert match,name
 args=[]
 for arg in match[2].split(','):
  if arg.strip()!='void':n,t=declaration(arg);args+=['%s: %s'%(n,t)]
 lines+=['    pub fn '+name+'('+', '.join(args)+') -> '+typ(match[1])+';']
lines+=['}']
(root/'native/src/sdk.rs').write_text('\n'.join(lines)+'\n')
v=root/'vendor/android-arm64';v.parent.mkdir(exist_ok=True)
if v.exists():raise SystemExit('Refuse overwrite vendor package; select a fresh lab/import')
shutil.copytree(p,v)
(root/'SDK_IDENTITY.json').write_text(json.dumps({'source_sha':sha,'source_digest':m['source_digest'],'target':m['target'],'profile':'default-preview','abi':'2.9','exports':58,'sdk_sha256':digest,'header_sha256':hash_header,'package_manifest_sha256':hashlib.sha256((p/'MANIFEST.json').read_bytes()).hexdigest()},indent=2)+'\n')
print('SDK_IMPORTED',digest)

legal=root/'app/src/main/assets/nexus-sdk';legal.mkdir(parents=True,exist_ok=True)
shutil.copy2(v/'NOTICE.txt',legal/'NOTICE.txt')
shutil.copytree(v/'DEPENDENCY_LICENSES',legal/'DEPENDENCY_LICENSES',dirs_exist_ok=True)
(root/'native/include').mkdir(exist_ok=True)
shutil.copy2(header,root/'native/include/nexus_v2.h')
