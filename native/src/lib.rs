//! External JNI consumer of the packaged C ABI. No nexus-core dependency.
//! No pointer survives a JNI call. Only SDK typed numeric handles cross Java.
#![deny(unsafe_op_in_unsafe_fn)]
#[cfg(feature = "staging")]
mod enrollment;
#[allow(non_snake_case, non_camel_case_types, dead_code)]
mod sdk;
use jni::{
    objects::{JByteBuffer, JClass},
    sys::{jint, jlong, jstring},
    JNIEnv,
};
use sdk::*;
use serde_json::{json, Value};
use std::{
    mem::size_of,
    panic::{catch_unwind, AssertUnwindSafe},
    ptr,
};

fn guard<T>(
    env: &mut JNIEnv,
    fallback: T,
    operation: impl FnOnce(&mut JNIEnv) -> Result<T, String>,
) -> T {
    match catch_unwind(AssertUnwindSafe(|| operation(env))) {
        Ok(Ok(value)) => value,
        outcome => {
            let message = match outcome {
                Ok(Err(e)) => e,
                _ => "JNI bridge panic contained".to_string(),
            };
            let _ = env.throw_new("java/lang/IllegalStateException", message);
            fallback
        }
    }
}
fn check(code: i32) -> Result<(), String> {
    if code == 0 {
        Ok(())
    } else {
        Err(format!("SDK status {code}"))
    }
}
fn handle(h: jlong) -> Result<u64, String> {
    u64::try_from(h)
        .ok()
        .filter(|h| *h != 0)
        .ok_or_else(|| "Invalid lab handle".into())
}
fn text(env: &mut JNIEnv, value: Value) -> Result<jstring, String> {
    env.new_string(value.to_string())
        .map(|s| s.into_raw())
        .map_err(|e| e.to_string())
}
fn snapshot(p: &NexusV2CaptureProgressV2, status: i32) -> Value {
    if status != 0 {
        return json!({"sdk_status": status});
    }
    let c = &p.common;
    json!({"sdk_status":0,"state":c.state,"decision":c.decision,"secured":c.secured,
        "required_checks_complete":c.required_checks_complete,"submitted":c.submitted,"processed":c.processed,
        "quality_accepted":c.accepted,"retry":c.retry,"retry_reasons":c.retry_reasons,
        "pipeline_reasons":c.pipeline_reasons,"platform_reasons":c.platform_reasons,"capture_reasons":c.reasons,
        "elapsed_ms":c.elapsed_ms,"window_samples":c.window_samples,"quality":[c.quality[0],c.quality[1],c.quality[2],c.quality[3],c.quality[4],c.quality[5]],
        "quality_order":"blur min/max/mean; luma mean; clipped low/high means",
        "sampling_accepted":p.sampling_accepted,"window_sampling_accepted":p.window_sampling_accepted,"distinct":p.distinct,
        "duplicates":p.duplicates,"cadence_excluded":p.cadence_excluded,"span_ms":p.observation_span_ms,
        "sampling_config":p.sampling_config,"sampling_reasons":p.sampling_reasons,"gap_resets":p.gap_resets,"temporal_status":p.temporal_status,
        "best_descriptor":c.best_descriptor,"best_roi":c.best_roi,"best_digest":c.best_digest.iter().map(|b| format!("{b:02x}")).collect::<String>(),"best_present":c.best_present,"best_submission":c.best_submission,"product_eligible":c.product_eligible,
        "complete_risk_analysis":c.complete_risk_analysis,"provenance":c.provenance,"manipulation":c.manipulation})
}
fn progress(capture: u64) -> Value {
    // SAFETY: generated C POD has only integer/floating/pointer fields, zero is valid.
    let mut out: NexusV2CaptureProgressV2 = unsafe { std::mem::zeroed() };
    // SAFETY: exclusive aligned exact-sized POD; SDK clears/initializes its header.
    let status = unsafe {
        nexus_v2_capture_progress_v2(
            capture,
            &mut out,
            size_of::<NexusV2CaptureProgressV2>() as u32,
        )
    };
    snapshot(&out, status)
}

#[no_mangle]
pub extern "system" fn Java_org_nexus_lab_Native_open(mut env: JNIEnv, _: JClass) -> jlong {
    guard(&mut env, 0, |_| {
        // SAFETY: scalar ABI queries and live output handle; initialization uses returned typed handle.
        unsafe {
            if nexus_v2_abi_version() != (2 << 16 | 11)
                || nexus_v2_build_profile() != if cfg!(feature = "staging") { 3 } else { 0 }
            {
                return Err("Expected matching ABI 2.10 default/staging build; fixture and production are disabled".into());
            }
            let mut h = 0;
            check(nexus_v2_session_create(1000, &mut h))?;
            if let Err(e) = check(nexus_v2_session_initialize(h)) {
                nexus_v2_session_destroy(h);
                return Err(e);
            }
            Ok(h as jlong)
        }
    })
}
#[no_mangle]
pub extern "system" fn Java_org_nexus_lab_Native_create(
    mut env: JNIEnv,
    _: JClass,
    session: jlong,
) -> jlong {
    guard(&mut env, 0, |_| {
        let session = handle(session)?;
        let cfg = capture_config();
        let mut h = 0;
        // SAFETY: exact live input config and exclusive disjoint output handle.
        check(unsafe { nexus_v2_capture_create_v2(session, &cfg, &mut h) })?;
        Ok(h as jlong)
    })
}
#[no_mangle]
pub extern "system" fn Java_org_nexus_lab_Native_submit(
    mut env: JNIEnv,
    _: JClass,
    capture: jlong,
    pixels: JByteBuffer,
    width: jint,
    height: jint,
    rotation: jint,
    timestamp: jlong,
) -> jstring {
    guard(&mut env, ptr::null_mut(), |env| {
        let capture = handle(capture)?;
        if !(3..=4096).contains(&width)
            || !(3..=4096).contains(&height)
            || ![0, 90, 180, 270].contains(&rotation)
            || timestamp <= 0
        {
            return Err("Invalid frame metadata".into());
        }
        let extent = (width as usize)
            .checked_mul(height as usize)
            .and_then(|v| v.checked_mul(4))
            .filter(|v| *v <= 16 * 1024 * 1024)
            .ok_or("Frame too large")?;
        if env
            .get_direct_buffer_capacity(&pixels)
            .map_err(|e| e.to_string())?
            < extent
        {
            return Err("Direct buffer truncated".into());
        }
        let position = env
            .call_method(&pixels, "position", "()I", &[])
            .and_then(|v| v.i())
            .map_err(|e| e.to_string())?;
        let limit = env
            .call_method(&pixels, "limit", "()I", &[])
            .and_then(|v| v.i())
            .map_err(|e| e.to_string())?;
        if position != 0 || limit != extent as i32 {
            return Err("Prepared buffer must have position 0 and exact payload limit".into());
        }
        let data = env
            .get_direct_buffer_address(&pixels)
            .map_err(|e| e.to_string())?;
        // SAFETY: generated numeric/raw-pointer C POD, no invalid bit patterns.
        let mut input: NexusV2FrameInput = unsafe { std::mem::zeroed() };
        input.struct_size = size_of::<NexusV2FrameInput>() as u32;
        input.version = 1;
        input.data = data;
        input.data_len = extent as u64;
        input.width = width as u32;
        input.height = height as u32;
        input.row_stride = width as u32 * 4;
        input.pixel_stride = 4;
        input.rotation = rotation as u32;
        input.timestamp_ns = timestamp as u64;
        input.format = 2;
        // SAFETY: exclusive exact-sized POD; Java direct buffer owner remains live and is
        // exclusively held on analyzer worker until this synchronous copy/processing returns.
        // Adapter always supplies position=0, limit=extent; JNI addresses the capacity base.
        let mut out: NexusV2CaptureProgressV2 = unsafe { std::mem::zeroed() };
        let status = unsafe {
            nexus_v2_capture_submit_v2(
                capture,
                &input,
                &mut out,
                size_of::<NexusV2CaptureProgressV2>() as u32,
            )
        };
        text(env, snapshot(&out, status))
    })
}
#[no_mangle]
pub extern "system" fn Java_org_nexus_lab_Native_progress(
    mut env: JNIEnv,
    _: JClass,
    capture: jlong,
) -> jstring {
    guard(&mut env, ptr::null_mut(), |env| {
        text(env, progress(handle(capture)?))
    })
}
#[no_mangle]
pub extern "system" fn Java_org_nexus_lab_Native_finish(
    mut env: JNIEnv,
    _: JClass,
    capture: jlong,
) -> jlong {
    guard(&mut env, 0, |_| {
        let mut h = 0;
        // SAFETY: live exclusive aligned output handle; core validates input handle.
        check(unsafe { nexus_v2_capture_finalize(handle(capture)?, &mut h) })?;
        Ok(h as jlong)
    })
}
#[no_mangle]
pub extern "system" fn Java_org_nexus_lab_Native_read(
    mut env: JNIEnv,
    _: JClass,
    capture: jlong,
    result: jlong,
) -> jstring {
    guard(&mut env, ptr::null_mut(), |env| {
        // SAFETY: zero-valid C POD, live exact-sized exclusive output, scalar typed handles.
        let mut out: NexusV2CaptureProgressV2 = unsafe { std::mem::zeroed() };
        let result = handle(result)?;
        let status = unsafe {
            nexus_v2_capture_result_read_v2(
                result,
                &mut out,
                size_of::<NexusV2CaptureProgressV2>() as u32,
            )
        };
        let mut v = snapshot(&out, status);
        let valid = unsafe { nexus_v2_capture_result_validate(handle(capture)?, result) };
        v["validation_status"] = json!(valid);
        v["current_result_valid"] = json!(status == 0 && valid == 0);
        text(env, v)
    })
}
#[no_mangle]
pub extern "system" fn Java_org_nexus_lab_Native_cancel(
    _env: JNIEnv,
    _: JClass,
    capture: jlong,
) -> jint {
    // SAFETY: no pointer or allocation; cancel signals in-flight core work, never frees Java input.
    unsafe { nexus_v2_capture_cancel(capture as u64) }
}
#[no_mangle]
pub extern "system" fn Java_org_nexus_lab_Native_release(
    _env: JNIEnv,
    _: JClass,
    session: jlong,
    capture: jlong,
    result: jlong,
) -> jint {
    // SAFETY: scalar typed handles, no pointer. Worker calls after in-flight work drains.
    // Always attempt every release even if one handle is already stale.
    unsafe {
        let mut status = 0;
        for code in [
            if result != 0 {
                nexus_v2_capture_result_release(result as u64)
            } else {
                0
            },
            if capture != 0 {
                nexus_v2_capture_release(capture as u64)
            } else {
                0
            },
            if session != 0 {
                nexus_v2_session_destroy(session as u64)
            } else {
                0
            },
        ] {
            if status == 0 {
                status = code;
            }
        }
        status
    }
}
#[no_mangle]
pub extern "system" fn Java_org_nexus_lab_Native_capabilities(
    mut env: JNIEnv,
    _: JClass,
    session: jlong,
) -> jstring {
    guard(&mut env, ptr::null_mut(), |env| {
        // SAFETY: numeric C POD and exact exclusive live output extent.
        let mut caps: NexusV2ProcessingCapabilities = unsafe { std::mem::zeroed() };
        check(unsafe {
            nexus_v2_processing_capabilities(
                handle(session)?,
                &mut caps,
                size_of::<NexusV2ProcessingCapabilities>() as u32,
            )
        })?;
        text(
            env,
            json!({"capability_statuses":caps.statuses,"fixture_authority":caps.fixture_authority,"diagnostic_runtime":caps.diagnostic_runtime,
            "order":"luma,rgba,bgra,Y,quality,spectral,temporal,trust,platform,replay,manipulation,production"}),
        )
    })
}

fn capture_config() -> NexusV2CaptureConfigV2 {
    // SAFETY: all-zero reserved numeric POD fields are valid; no ownership in config.
    let mut cfg: NexusV2CaptureConfigV2 = unsafe { std::mem::zeroed() };
    cfg.struct_size = size_of::<NexusV2CaptureConfigV2>() as u32;
    cfg.version = 2;
    cfg.min_accepted = 5;
    cfg.max_submissions = 256;
    cfg.quality_window = 32;
    cfg.max_duration_ms = 15000;
    cfg.context_mode = 1;
    cfg.min_distinct = 3;
    cfg.min_interval_ms = 100;
    cfg.min_observation_span_ms = 400;
    cfg.max_sample_gap_ms = 1500;
    cfg
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::mem::{align_of, offset_of};
    #[test]
    fn imported_header_layout_and_live_c_abi_ownership() {
        assert_eq!(size_of::<NexusV2FrameInput>(), 96);
        assert_eq!(size_of::<NexusV2CaptureConfigV2>(), 56);
        assert_eq!(size_of::<NexusV2CaptureProgress>(), 472);
        assert_eq!(size_of::<NexusV2CaptureProgressV2>(), 544);
        assert_eq!(size_of::<NexusV2ProcessingCapabilities>(), 72);
        assert_eq!(align_of::<NexusV2CaptureProgressV2>(), 8);
        assert_eq!(offset_of!(NexusV2CaptureProgressV2, common), 8);
        assert_eq!(offset_of!(NexusV2CaptureProgress, context_digest), 128);
        // SAFETY: only scalar handles and live aligned exclusive POD/handle outputs.
        unsafe {
            assert_eq!(nexus_v2_abi_version(), 2 << 16 | 11);
            assert_eq!(
                nexus_v2_build_profile(),
                if cfg!(feature = "staging") { 3 } else { 0 }
            );
            for _ in 0..80 {
                let mut session = 0;
                assert_eq!(nexus_v2_session_create(1000, &mut session), 0);
                assert_eq!(nexus_v2_session_initialize(session), 0);
                let cfg = NexusV2CaptureConfigV2 {
                    struct_size: size_of::<NexusV2CaptureConfigV2>() as u32,
                    version: 2,
                    min_accepted: 3,
                    max_submissions: 32,
                    quality_window: 8,
                    max_duration_ms: 10000,
                    context_mode: 1,
                    min_distinct: 2,
                    min_interval_ms: 100,
                    min_observation_span_ms: 200,
                    max_sample_gap_ms: 0,
                    reserved: [0; 3],
                };
                let mut capture = 0;
                assert_eq!(nexus_v2_capture_create_v2(session, &cfg, &mut capture), 0);
                assert_eq!(progress(capture)["state"], 1);
                assert_eq!(nexus_v2_capture_cancel(capture), 0);
                assert_eq!(progress(capture)["state"], 4);
                assert_eq!(nexus_v2_capture_release(capture), 0);
                assert_ne!(progress(capture)["sdk_status"], 0);
                assert_eq!(nexus_v2_session_destroy(session), 0);
                assert_ne!(nexus_v2_capture_release(capture), 0);
            }
        }
    }
}
