"""Generate the used C declarations from a specified public header; strict parser."""
import sys,re,hashlib
from pathlib import Path
root=Path(__file__).resolve().parents[1]
header=Path(sys.argv[1])
hash_header=hashlib.sha256(header.read_bytes()).hexdigest()
s=re.sub(r'/\*.*?\*/','',header.read_text(),flags=re.S)
assert '#define NEXUS_V2_ABI_MINOR 11' in s
names=['NexusV2Bytes','NexusV2SignedDocument','NexusV2BootstrapChallenge','NexusV2PlatformChallenge','NexusV2ProvisioningRequest','NexusV2SecurityConfiguration','NexusV2AuthorityCheckpoint','NexusV2FrameInput','NexusV2CaptureConfigV2','NexusV2CaptureProgress','NexusV2CaptureProgressV2','NexusV2ProcessingCapabilities']
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
 lines+=['#[derive(Clone, Copy)]','#[repr(C)]','pub struct '+name+' {']
 for field in body.split(';'):
  if field.strip():n,t=declaration(field);lines+=['    pub '+n+': '+t+',']
 lines+=['}']
functions={'capture_create_bound_v1','capture_receipt_export_v1','bootstrap_challenge_create','bootstrap_challenge_release','authority_provision','authority_release','authority_checkpoint','session_create_secured','session_configure_security','session_platform_challenge','abi_version','build_profile','session_create','session_initialize','session_destroy','capture_create_v2','capture_submit_v2','capture_progress_v2','capture_finalize','capture_result_read_v2','capture_result_validate','capture_result_release','capture_cancel','capture_release','processing_capabilities'}
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

# Keep generated declarations reproducible and compatible with the repository format gate.
import subprocess
subprocess.run(["rustfmt","--edition","2021",str(root/"native/src/sdk.rs")],check=True)
