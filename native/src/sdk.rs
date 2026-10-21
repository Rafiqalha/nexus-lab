// Generated from packaged header SHA256 781882be706cb85850f2efe3a286317d4efa5db243d9bde48865571af97f4888. Do not hand-edit.
#[repr(C)]
pub struct NexusV2FrameInput {
    pub struct_size: u32,
    pub version: u32,
    pub data: *const u8,
    pub data_len: u64,
    pub width: u32,
    pub height: u32,
    pub row_stride: u32,
    pub pixel_stride: u32,
    pub rotation: u32,
    pub mirrored: u32,
    pub timestamp_ns: u64,
    pub format: u32,
    pub roi_kind: u32,
    pub roi_x: u32,
    pub roi_y: u32,
    pub roi_width: u32,
    pub roi_height: u32,
    pub reserved: [u32; 4],
}
#[repr(C)]
pub struct NexusV2CaptureConfigV2 {
    pub struct_size: u32,
    pub version: u32,
    pub min_accepted: u32,
    pub max_submissions: u32,
    pub quality_window: u32,
    pub max_duration_ms: u32,
    pub context_mode: u32,
    pub min_distinct: u32,
    pub min_interval_ms: u32,
    pub min_observation_span_ms: u32,
    pub max_sample_gap_ms: u32,
    pub reserved: [u32; 3],
}
#[repr(C)]
pub struct NexusV2CaptureProgress {
    pub struct_size: u32,
    pub version: u32,
    pub capture: u64,
    pub session: u64,
    pub generation: u64,
    pub elapsed_ms: u64,
    pub state: u32,
    pub decision: u32,
    pub secured: u32,
    pub required_checks_complete: u32,
    pub submitted: u32,
    pub processed: u32,
    pub retry: u32,
    pub accepted: u32,
    pub window_samples: u32,
    pub window_accepted: u32,
    pub reasons: u32,
    pub retry_reasons: u32,
    pub pipeline_reasons: u32,
    pub platform_reasons: u32,
    pub last_error: i32,
    pub context_present: u32,
    pub config: [u32; 6],
    pub context_digest: [u8; 32],
    pub policy_digest: [u8; 32],
    pub trust_digest: [u8; 32],
    pub frame_chain: [u8; 32],
    pub quality: [f64; 6],
    pub best_present: u32,
    pub best_submission: u32,
    pub best_format: u32,
    pub reserved_flags: u32,
    pub best_attempt: u64,
    pub best_timestamp_ns: u64,
    pub best_roi: [u32; 4],
    pub best_descriptor: [u32; 6],
    pub best_quality: [f64; 4],
    pub best_digest: [u8; 32],
    pub complete_risk_analysis: u32,
    pub manipulation: u32,
    pub provenance: u32,
    pub product_eligible: u32,
    pub reserved: [u32; 4],
}
#[repr(C)]
pub struct NexusV2CaptureProgressV2 {
    pub struct_size: u32,
    pub version: u32,
    pub common: NexusV2CaptureProgress,
    pub sampling_config: [u32; 4],
    pub sampling_accepted: u32,
    pub window_sampling_accepted: u32,
    pub distinct: u32,
    pub duplicates: u32,
    pub cadence_excluded: u32,
    pub sampling_reasons: u32,
    pub temporal_status: u32,
    pub gap_resets: u32,
    pub observation_span_ms: u64,
    pub reserved: [u32; 2],
}
#[repr(C)]
pub struct NexusV2ProcessingCapabilities {
    pub struct_size: u32,
    pub version: u32,
    pub statuses: [u32; 12],
    pub fixture_authority: u32,
    pub diagnostic_runtime: u32,
    pub reserved: [u32; 2],
}
unsafe extern "C" {
    pub fn nexus_v2_abi_version() -> u32;
    pub fn nexus_v2_build_profile() -> u32;
    pub fn nexus_v2_capture_cancel(h: u64) -> i32;
    pub fn nexus_v2_capture_create_v2(
        session: u64,
        config: *const NexusV2CaptureConfigV2,
        out: *mut u64,
    ) -> i32;
    pub fn nexus_v2_capture_finalize(h: u64, out: *mut u64) -> i32;
    pub fn nexus_v2_capture_progress_v2(
        h: u64,
        out: *mut NexusV2CaptureProgressV2,
        size: u32,
    ) -> i32;
    pub fn nexus_v2_capture_release(h: u64) -> i32;
    pub fn nexus_v2_capture_result_read_v2(
        h: u64,
        out: *mut NexusV2CaptureProgressV2,
        size: u32,
    ) -> i32;
    pub fn nexus_v2_capture_result_release(h: u64) -> i32;
    pub fn nexus_v2_capture_result_validate(h: u64, r: u64) -> i32;
    pub fn nexus_v2_capture_submit_v2(
        h: u64,
        input: *const NexusV2FrameInput,
        out: *mut NexusV2CaptureProgressV2,
        size: u32,
    ) -> i32;
    pub fn nexus_v2_processing_capabilities(
        session: u64,
        out: *mut NexusV2ProcessingCapabilities,
        size: u32,
    ) -> i32;
    pub fn nexus_v2_session_create(timeout_ms: u32, out: *mut u64) -> i32;
    pub fn nexus_v2_session_destroy(handle: u64) -> i32;
    pub fn nexus_v2_session_initialize(handle: u64) -> i32;
}
