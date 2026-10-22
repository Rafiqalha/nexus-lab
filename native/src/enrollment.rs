//! Thin staging ABI consumer; no verifier, network or platform collector lives in Rust JNI.
use super::*;
use base64::{engine::general_purpose::STANDARD as B64, Engine};
use jni::objects::JString;
fn bytes(b: &[u8]) -> NexusV2Bytes {
    NexusV2Bytes {
        data: b.as_ptr(),
        len: b.len() as u64,
    }
}
fn empty_doc() -> NexusV2SignedDocument {
    NexusV2SignedDocument {
        bytes: bytes(&[]),
        signature: bytes(&[]),
    }
}
struct Document {
    bytes: Vec<u8>,
    signature: Vec<u8>,
}
impl Document {
    fn parse(v: &Value) -> Result<Self, String> {
        let b = v["bytes"].as_str().ok_or("Missing signed bytes")?;
        let s = v["signature"].as_str().ok_or("Missing signature")?;
        if b.len() > 24 * 1024 || s.len() > 88 {
            return Err("Document exceeds bound".into());
        }
        Ok(Self {
            bytes: B64.decode(b).map_err(|e| e.to_string())?,
            signature: B64.decode(s).map_err(|e| e.to_string())?,
        })
    }
    fn view(&self) -> NexusV2SignedDocument {
        NexusV2SignedDocument {
            bytes: bytes(&self.bytes),
            signature: bytes(&self.signature),
        }
    }
}
#[no_mangle]
pub extern "system" fn Java_org_nexus_lab_Native_stageBegin(mut env: JNIEnv, _: JClass) -> jstring {
    guard(&mut env, std::ptr::null_mut(), |env| {
        if unsafe { nexus_v2_build_profile() } != 3
            || unsafe { nexus_v2_abi_version() } != (2 << 16 | 10)
        {
            return Err("Isolated staging build required".into());
        }
        let mut session = 0;
        let mut bootstrap = 0;
        let outcome = (|| {
            // SAFETY: all output POD buffers are live, aligned, exact-sized, and disjoint.
            unsafe {
                check(nexus_v2_session_create_secured(1000, &mut session))?;
                let mut binding: NexusV2PlatformChallenge = std::mem::zeroed();
                check(nexus_v2_session_platform_challenge(
                    session,
                    &mut binding,
                    size_of::<NexusV2PlatformChallenge>() as u32,
                ))?;
                let mut challenge: NexusV2BootstrapChallenge = std::mem::zeroed();
                check(nexus_v2_bootstrap_challenge_create(
                    bytes(b"staging-android"),
                    bytes(b"nexus-core"),
                    60000,
                    &mut bootstrap,
                    &mut challenge,
                    size_of::<NexusV2BootstrapChallenge>() as u32,
                ))?;
                let nonce = std::str::from_utf8(&challenge.nonce[..challenge.nonce_len as usize])
                    .map_err(|e| e.to_string())?;
                text(
                    env,
                    json!({"session_handle":session,"bootstrap_handle":bootstrap,"bootstrap":{"bootstrap_id":challenge.bootstrap_id,"nonce":nonce,"config_id":"staging-android","sdk_scope":"nexus-core","max_age_ms":challenge.max_age_ms},"session":{"session":binding.session,"generation":binding.generation,"nonce":binding.nonce.iter().map(|b|format!("{b:02x}")).collect::<String>()}}),
                )
            }
        })();
        if outcome.is_err() {
            unsafe {
                if bootstrap != 0 {
                    nexus_v2_bootstrap_challenge_release(bootstrap);
                }
                if session != 0 {
                    nexus_v2_session_destroy(session);
                }
            }
        }
        outcome
    })
}
#[no_mangle]
pub extern "system" fn Java_org_nexus_lab_Native_stageComplete(
    mut env: JNIEnv,
    _: JClass,
    session: jlong,
    challenge: jlong,
    bundle: JString,
) -> jstring {
    guard(&mut env, std::ptr::null_mut(), |env| {
        let raw = env.get_string(&bundle).map_err(|e| e.to_string())?;
        let raw = raw.to_str().map_err(|e| e.to_string())?;
        if raw.len() > 256 * 1024 {
            return Err("Provisioning bundle too large".into());
        }
        let value: Value = serde_json::from_str(raw).map_err(|e| e.to_string())?;
        if value["domain"] != "staging-enrollment" {
            return Err("Wrong trust domain".into());
        }
        let config = Document::parse(&value["config"])?;
        let policy = Document::parse(&value["policy"])?;
        let response = Document::parse(&value["response"])?;
        let manifest = Document::parse(&value["package_manifest"])?;
        let license = Document::parse(&value["license"])?;
        let time = Document::parse(&value["time"])?;
        let platform = Document::parse(&value["platform"])?;
        let payload = B64
            .decode(value["package_payload"].as_str().ok_or("Missing package")?)
            .map_err(|e| e.to_string())?;
        let t: Value = serde_json::from_slice(&time.bytes).map_err(|e| e.to_string())?;
        let binding = t["challenge"].as_str().ok_or("Missing session binding")?;
        let session = handle(session)?;
        let challenge = handle(challenge)?;
        let mut authority = 0;
        let outcome = (|| {
            // SAFETY: borrowed C byte ranges remain owned and immutable through synchronous calls;
            // outputs are aligned exact-sized local POD. No allocation ownership crosses C ABI.
            unsafe {
                let request = NexusV2ProvisioningRequest {
                    struct_size: size_of::<NexusV2ProvisioningRequest>() as u32,
                    version: 1,
                    config: config.view(),
                    policy: policy.view(),
                    response: response.view(),
                    state_kind: 0,
                    reserved_flags: 0,
                    state: empty_doc(),
                    reserved: [0; 4],
                };
                check(nexus_v2_authority_provision(
                    2,
                    challenge,
                    &request,
                    &mut authority,
                ))?;
                let request = NexusV2SecurityConfiguration {
                    struct_size: size_of::<NexusV2SecurityConfiguration>() as u32,
                    version: 1,
                    package_manifest: manifest.view(),
                    package_payload: bytes(&payload),
                    license: license.view(),
                    time_kind: 2,
                    platform_kind: 4,
                    time: time.view(),
                    platform: platform.view(),
                    challenge: bytes(binding.as_bytes()),
                    scope: bytes(b"nexus-core"),
                    reserved: [0; 4],
                };
                check(nexus_v2_session_configure_security(
                    session, authority, &request,
                ))?;
                check(nexus_v2_session_initialize(session))?;
                let mut status: NexusV2AuthorityCheckpoint = std::mem::zeroed();
                check(nexus_v2_authority_checkpoint(
                    authority,
                    &mut status,
                    size_of::<NexusV2AuthorityCheckpoint>() as u32,
                ))?;
                // Display only metadata from the exact provisioning bytes accepted by core.
                // The outer HTTP bundle is not authenticated enrollment evidence.
                let verified: Value =
                    serde_json::from_slice(&response.bytes).map_err(|e| e.to_string())?;
                let enrollment = verified
                    .get("enrollment")
                    .ok_or("Missing verified enrollment")?;
                text(
                    env,
                    json!({"sdk_status":0,"domain":"staging-enrollment","enrollment":enrollment,"provisioning_status":status.provisioning_status,"continuity_status":status.continuity_status,"product_eligible":false}),
                )
            }
        })();
        // Session security retains its Arc authority owner. Registry handle ownership ends here.
        unsafe {
            if authority != 0 {
                nexus_v2_authority_release(authority);
            }
            nexus_v2_bootstrap_challenge_release(challenge);
        }
        outcome
    })
}
#[no_mangle]
pub extern "system" fn Java_org_nexus_lab_Native_stageAbort(
    mut env: JNIEnv,
    _: JClass,
    session: jlong,
    challenge: jlong,
) -> i32 {
    guard(&mut env, -1, |_| {
        unsafe {
            if challenge > 0 {
                nexus_v2_bootstrap_challenge_release(challenge as u64);
            }
            if session > 0 {
                nexus_v2_session_destroy(session as u64);
            }
        }
        Ok(0)
    })
}
