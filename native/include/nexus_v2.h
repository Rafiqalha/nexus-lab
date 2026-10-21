#ifndef NEXUS_V2_H
#define NEXUS_V2_H

#include <stdarg.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>

#define NEXUS_V2_ABI_MAJOR 2

#define NEXUS_V2_ABI_MINOR 9

#define NEXUS_V2_STRUCT_VERSION 1

typedef struct NexusV2Frame {
  uint32_t struct_size;
  uint32_t version;
  const uint8_t *data;
  uint64_t data_len;
  uint32_t width;
  uint32_t height;
  uint32_t row_stride;
  uint32_t pixel_stride;
  uint32_t rotation;
  uint32_t mirrored;
  uint64_t timestamp_ns;
  uint32_t reserved[4];
} NexusV2Frame;

typedef struct NexusV2Result {
  uint32_t struct_size;
  uint32_t version;
  int32_t status;
  uint32_t complete_risk_analysis;
  uint32_t manipulation_risk;
  uint32_t provenance;
  uint32_t quality_retry;
  float blur_variance;
  uint64_t generation;
  uint64_t attempt;
  uint64_t elapsed_us;
  uint32_t reserved[4];
} NexusV2Result;

typedef struct NexusV2SessionInfo {
  uint32_t struct_size;
  uint32_t version;
  uint64_t session_id;
  uint64_t generation;
  uint32_t mode;
  uint32_t state;
  uint32_t reserved[4];
} NexusV2SessionInfo;

/**
 * ABI 2.1 additions. Old frame/result layouts and entry points are unchanged.
 */
typedef struct NexusV2Bytes {
  const uint8_t *data;
  uint64_t len;
} NexusV2Bytes;

typedef struct NexusV2SignedDocument {
  struct NexusV2Bytes bytes;
  struct NexusV2Bytes signature;
} NexusV2SignedDocument;

typedef struct NexusV2SecurityConfiguration {
  uint32_t struct_size;
  uint32_t version;
  struct NexusV2SignedDocument package_manifest;
  struct NexusV2Bytes package_payload;
  struct NexusV2SignedDocument license;
  uint32_t time_kind;
  uint32_t platform_kind;
  struct NexusV2SignedDocument time;
  struct NexusV2SignedDocument platform;
  struct NexusV2Bytes challenge;
  struct NexusV2Bytes scope;
  uint32_t reserved[4];
} NexusV2SecurityConfiguration;

typedef struct NexusV2SecurityResult {
  uint32_t struct_size;
  uint32_t version;
  uint64_t session_id;
  uint64_t generation;
  uint64_t attempt;
  uint32_t decision;
  uint32_t gates[3];
  uint32_t time_status;
  uint32_t platform_status;
  uint32_t license_status;
  uint32_t policy_status;
  uint32_t initial_checks_complete;
  uint32_t complete_risk_analysis;
  uint32_t evidence_eligible;
  uint32_t provenance;
  uint32_t duplicate_frames;
  uint32_t temporal_samples;
  double luminance_mean;
  double clipped_low_fraction;
  double clipped_high_fraction;
  double non_dc_power;
  double high_band_fraction;
  double peak_fx;
  double peak_fy;
  uint8_t input_digest[32];
  uint8_t trust_digest[32];
  uint8_t policy_digest[32];
  uint8_t package_digest[32];
  uint32_t reserved[4];
} NexusV2SecurityResult;

/**
 * ABI 2.3 bound checkpoint. ABI 2.2's 104-byte structure is unchanged.
 * Identifier buffers contain exactly *_len UTF-8 bytes, with zero trailing storage.
 */
typedef struct NexusV2AuthorityCheckpointV2 {
  uint32_t struct_size;
  uint32_t version;
  uint64_t state_epoch;
  uint64_t min_trust_revision;
  uint64_t min_policy_revision;
  uint64_t min_package_sequence;
  uint64_t min_unix_ms;
  uint32_t provisioning_status;
  uint32_t continuity_status;
  uint8_t policy_digest[32];
  uint8_t config_digest[32];
  uint32_t config_id_len;
  uint32_t sdk_scope_len;
  uint32_t policy_id_len;
  uint32_t reserved_flags;
  uint8_t config_id[96];
  uint8_t sdk_scope[96];
  uint8_t policy_id[96];
  uint32_t reserved[4];
} NexusV2AuthorityCheckpointV2;

/**
 * ABI 2.2 additions for CSPRNG bootstrap challenge and provisioned authority lifecycle.
 */
typedef struct NexusV2BootstrapChallenge {
  uint32_t struct_size;
  uint32_t version;
  uint64_t bootstrap_id;
  uint32_t max_age_ms;
  uint32_t nonce_len;
  uint8_t nonce[64];
  uint32_t reserved[4];
} NexusV2BootstrapChallenge;

typedef struct NexusV2ProvisioningRequest {
  uint32_t struct_size;
  uint32_t version;
  struct NexusV2SignedDocument config;
  struct NexusV2SignedDocument policy;
  struct NexusV2SignedDocument response;
  uint32_t state_kind;
  uint32_t reserved_flags;
  struct NexusV2SignedDocument state;
  uint32_t reserved[4];
} NexusV2ProvisioningRequest;

typedef struct NexusV2AuthorityCheckpoint {
  uint32_t struct_size;
  uint32_t version;
  uint64_t state_epoch;
  uint64_t min_trust_revision;
  uint64_t min_policy_revision;
  uint64_t min_package_sequence;
  uint64_t min_unix_ms;
  uint32_t provisioning_status;
  uint32_t continuity_status;
  uint8_t policy_digest[32];
  uint32_t reserved[4];
} NexusV2AuthorityCheckpoint;

/**
 * Canonical test-domain request bytes. This payload is not SDK execution attestation.
 */
typedef struct NexusV2CheckpointCommitRequest {
  uint32_t struct_size;
  uint32_t version;
  uint32_t bytes_len;
  uint32_t reserved_flags;
  uint8_t bytes[4096];
  uint32_t reserved[4];
} NexusV2CheckpointCommitRequest;

/**
 * Historical issuer/witness acknowledgment. Call validate before treating it as current.
 */
typedef struct NexusV2CheckpointCommitReceipt {
  uint32_t struct_size;
  uint32_t version;
  uint64_t provider_epoch;
  uint64_t anchor_epoch;
  uint8_t checkpoint_digest[32];
  uint8_t request_digest[32];
  uint8_t commit_digest[32];
  uint8_t anchor_digest[32];
  uint32_t reserved[4];
} NexusV2CheckpointCommitReceipt;

typedef struct NexusV2CaptureConfig {
  uint32_t struct_size;
  uint32_t version;
  uint32_t min_accepted;
  uint32_t max_submissions;
  uint32_t quality_window;
  uint32_t max_duration_ms;
  uint32_t context_mode;
  uint32_t reserved[3];
} NexusV2CaptureConfig;

typedef struct NexusV2FrameInput {
  uint32_t struct_size;
  uint32_t version;
  const uint8_t *data;
  uint64_t data_len;
  uint32_t width;
  uint32_t height;
  uint32_t row_stride;
  uint32_t pixel_stride;
  uint32_t rotation;
  uint32_t mirrored;
  uint64_t timestamp_ns;
  /**
   * 1 LUMA8, 2 RGBA8, 3 BGRA8, 4 external Y plane. RGB alpha ignored, straight RGB only.
   */
  uint32_t format;
  /**
   * 0 whole oriented frame (ROI fields zero), 1 explicit oriented ROI.
   */
  uint32_t roi_kind;
  uint32_t roi_x;
  uint32_t roi_y;
  uint32_t roi_width;
  uint32_t roi_height;
  uint32_t reserved[4];
} NexusV2FrameInput;

/**
 * Progress or immutable finalized historical snapshot. Completion is quality/capture scope only.
 */
typedef struct NexusV2CaptureProgress {
  uint32_t struct_size;
  uint32_t version;
  uint64_t capture;
  uint64_t session;
  uint64_t generation;
  uint64_t elapsed_ms;
  uint32_t state;
  uint32_t decision;
  uint32_t secured;
  uint32_t required_checks_complete;
  uint32_t submitted;
  uint32_t processed;
  uint32_t retry;
  uint32_t accepted;
  uint32_t window_samples;
  uint32_t window_accepted;
  uint32_t reasons;
  uint32_t retry_reasons;
  uint32_t pipeline_reasons;
  uint32_t platform_reasons;
  int32_t last_error;
  uint32_t context_present;
  /**
   * version, minimum, max submissions, quality window, max duration, strict context=1.
   */
  uint32_t config[6];
  uint8_t context_digest[32];
  uint8_t policy_digest[32];
  uint8_t trust_digest[32];
  uint8_t frame_chain[32];
  /**
   * Blur min/max/mean, luminance mean, endpoint low/high clipping means, over window samples.
   */
  double quality[6];
  uint32_t best_present;
  uint32_t best_submission;
  uint32_t best_format;
  uint32_t reserved_flags;
  uint64_t best_attempt;
  uint64_t best_timestamp_ns;
  uint32_t best_roi[4];
  /**
   * Original descriptor width, height, row stride, pixel stride, rotation, mirrored.
   */
  uint32_t best_descriptor[6];
  double best_quality[4];
  uint8_t best_digest[32];
  uint32_t complete_risk_analysis;
  uint32_t manipulation;
  uint32_t provenance;
  uint32_t product_eligible;
  uint32_t reserved[4];
} NexusV2CaptureProgress;

/**
 * ABI 2.9 temporal capture contract; zero max_sample_gap_ms disables gap expiration.
 */
typedef struct NexusV2CaptureConfigV2 {
  uint32_t struct_size;
  uint32_t version;
  uint32_t min_accepted;
  uint32_t max_submissions;
  uint32_t quality_window;
  uint32_t max_duration_ms;
  uint32_t context_mode;
  uint32_t min_distinct;
  uint32_t min_interval_ms;
  uint32_t min_observation_span_ms;
  uint32_t max_sample_gap_ms;
  uint32_t reserved[3];
} NexusV2CaptureConfigV2;

/**
 * Version 2 envelope. Common accepted/window_accepted retain quality eligibility semantics.
 */
typedef struct NexusV2CaptureProgressV2 {
  uint32_t struct_size;
  uint32_t version;
  struct NexusV2CaptureProgress common;
  /**
   * Minimum distinct, minimum interval, minimum span, maximum gap (0 disabled).
   */
  uint32_t sampling_config[4];
  uint32_t sampling_accepted;
  uint32_t window_sampling_accepted;
  uint32_t distinct;
  uint32_t duplicates;
  uint32_t cadence_excluded;
  uint32_t sampling_reasons;
  /**
   * 0 not evaluated (error output), 1 pending, 2 temporal requirements satisfied.
   */
  uint32_t temporal_status;
  uint32_t gap_resets;
  uint64_t observation_span_ms;
  uint32_t reserved[2];
} NexusV2CaptureProgressV2;

typedef struct NexusV2ProcessingConfig {
  uint32_t struct_size;
  uint32_t version;
  uint32_t timeout_ms;
  uint32_t secured;
  /**
   * Bits 0 quality, 1 spectral, 2 temporal. Signed sessions require all three.
   */
  uint32_t stages_mask;
  uint32_t threshold_flags;
  uint32_t temporal_window;
  uint32_t reserved_flags;
  /**
   * Threshold flags bits 0..4 enable the corresponding five values; otherwise value must be zero.
   */
  double blur_retry_below;
  double luminance_min;
  double luminance_max;
  double clipped_low_max;
  double clipped_high_max;
  uint32_t reserved[4];
} NexusV2ProcessingConfig;

typedef struct NexusV2ProcessingResult {
  uint32_t struct_size;
  uint32_t version;
  uint64_t session;
  uint64_t generation;
  uint64_t attempt;
  uint64_t timestamp_ns;
  uint64_t elapsed_us;
  uint32_t outcome;
  uint32_t reasons;
  uint32_t initial_checks_complete;
  uint32_t complete_risk_analysis;
  uint32_t provenance;
  uint32_t manipulation;
  uint32_t format;
  uint32_t whole_frame;
  uint32_t roi[4];
  uint32_t stages[8];
  uint32_t available_mask;
  uint32_t duplicate_frames;
  uint32_t temporal_samples;
  uint32_t reserved_flags;
  double blur_variance;
  double luminance_mean;
  double clipped_low_fraction;
  double clipped_high_fraction;
  double spectral_non_dc_power;
  double spectral_high_band_fraction;
  double spectral_peak_fx;
  double spectral_peak_fy;
  uint8_t context_digest[32];
  uint8_t policy_digest[32];
  uint32_t reserved[4];
} NexusV2ProcessingResult;

typedef struct NexusV2ProcessingCapabilities {
  uint32_t struct_size;
  uint32_t version;
  /**
   * Status order: luma, rgba, bgra, Y plane, quality, spectral, temporal, trust, platform, replay, manipulation, production.
   * 1 available, 2 needs external evidence, 3 disabled by config, 4 not implemented.
   */
  uint32_t statuses[12];
  uint32_t fixture_authority;
  uint32_t diagnostic_runtime;
  uint32_t reserved[2];
} NexusV2ProcessingCapabilities;

/**
 * Additive ABI 2.7. A core-generated nonce, not a host assertion or production enrollment.
 */
typedef struct NexusV2PlatformChallenge {
  uint32_t struct_size;
  uint32_t version;
  uint64_t session;
  uint64_t generation;
  uint8_t nonce[32];
  uint32_t max_age_ms;
  uint32_t reserved[3];
} NexusV2PlatformChallenge;

/**
 * App/device assessment only. All claim codes 0 unknown, 1 meets/absent, 2 fails/present.
 * Signer SHA-256 is over certificate DER, never an unspecified application-file digest.
 */
typedef struct NexusV2PlatformAssessment {
  uint32_t struct_size;
  uint32_t version;
  /**
   * 0 unavailable, 1 authenticated fixture legacy, 2 authenticated fixture claims, 3 unverified host, 4 unsupported raw.
   */
  uint32_t authenticity;
  /**
   * 0 unavailable, 1 valid, 2 expired, 3 not yet valid, 4 trusted time unavailable.
   */
  uint32_t freshness;
  /**
   * 0 not required, 1 requirements satisfied, 2 rejected, 3 indeterminate. Not product authorization.
   */
  uint32_t decision;
  /**
   * Reason bitmask specified in PLATFORM_ASSESSMENT_ABI_2_7.md; never raw tokens.
   */
  uint32_t reasons;
  /**
   * Application integrity, device integrity, emulator, compromise, debugger, instrumentation.
   */
  uint32_t claims[6];
  uint32_t application_id_len;
  uint32_t signer_present;
  uint8_t application_id[96];
  uint8_t signer_sha256[32];
  uint8_t evidence_sha256[32];
  uint32_t issuer_key_id_len;
  uint8_t issuer_key_id[96];
  uint32_t reserved[3];
} NexusV2PlatformAssessment;

/**
 * Returns packed major/minor. This function cannot panic.
 */
uint32_t nexus_v2_abi_version(void);

/**
 * # Safety
 * `out` must be aligned writable storage for one u64, exclusive for this call.
 */
int32_t nexus_v2_session_create(uint32_t timeout_ms, uint64_t *out);

int32_t nexus_v2_session_initialize(uint64_t handle);

int32_t nexus_v2_session_cancel(uint64_t handle);

int32_t nexus_v2_session_reset(uint64_t handle);

int32_t nexus_v2_session_destroy(uint64_t handle);

/**
 * # Safety
 * `out` must be aligned writable storage for one u32.
 */
int32_t nexus_v2_session_state(uint64_t handle, uint32_t *out);

/**
 * # Safety
 * Frame and output must be readable/writable for their advertised sizes and aligned.
 * All buffers must remain alive, immutable/exclusive as appropriate, and nonoverlapping
 * until return. Pointer readability cannot be established by an in-process library.
 */
int32_t nexus_v2_analyze(uint64_t handle,
                         const struct NexusV2Frame *frame,
                         struct NexusV2Result *out,
                         uint32_t out_size);

/**
 * Product capabilities remain unavailable; no caller-provided risk/stages are accepted.
 */
int32_t nexus_v2_product_eligibility(uint64_t handle);

/**
 * Bit 0 quality preview; bit 1 secured API boundary; bit 2 fixture authority build.
 * No bit authorizes production enrollment, replay verdicts or product evidence.
 */
uint64_t nexus_v2_capabilities(void);

/**
 * Build profile 0 default, 1 fixture, 2 diagnostic regression. None enrolls production trust.
 */
uint32_t nexus_v2_build_profile(void);

/**
 * # Safety
 * `out` is aligned writable exclusive u64 storage.
 */
int32_t nexus_v2_session_create_secured(uint32_t timeout_ms, uint64_t *out);

/**
 * # Safety
 * `out` is aligned writable storage for out_size bytes, exclusive for the call.
 */
int32_t nexus_v2_session_info(uint64_t handle, struct NexusV2SessionInfo *out, uint32_t out_size);

/**
 * # Safety
 * Inputs are live readable SignedDocument structures/ranges; out is exclusive u64.
 * Domain 0 production always denies; 1 fixture only in the explicit fixture build.
 */
int32_t nexus_v2_authority_create(uint32_t domain,
                                  const struct NexusV2SignedDocument *config,
                                  const struct NexusV2SignedDocument *policy,
                                  uint64_t *out);

/**
 * # Safety
 * Both SignedDocument structures and their ranges are readable and immutable.
 */
int32_t nexus_v2_authority_update(uint64_t handle,
                                  const struct NexusV2SignedDocument *config,
                                  const struct NexusV2SignedDocument *policy);

int32_t nexus_v2_authority_release(uint64_t handle);

/**
 * # Safety
 * Request/header and all advertised byte ranges remain readable and immutable.
 * No trust boolean: platform kind 2 is legacy context, kind 3 signed app/device claims (TEST ONLY).
 */
int32_t nexus_v2_session_configure_security(uint64_t handle,
                                            uint64_t authority_handle,
                                            const struct NexusV2SecurityConfiguration *request);

/**
 * # Safety
 * Same frame/output contract as analyze; report_out is separate writable u64 storage.
 * Report capacity is admitted before processing. Error leaves zero report handle.
 */
int32_t nexus_v2_analyze_secured(uint64_t handle,
                                 const struct NexusV2Frame *frame,
                                 struct NexusV2Result *out,
                                 uint32_t out_size,
                                 uint64_t *report_out);

/**
 * # Safety
 * out is live aligned exclusive writable storage for out_size bytes.
 * This returns an immutable historical snapshot; validate separately for current use.
 */
int32_t nexus_v2_report_read(uint64_t handle, struct NexusV2SecurityResult *out, uint32_t out_size);

int32_t nexus_v2_report_validate(uint64_t session_handle, uint64_t report_handle);

int32_t nexus_v2_report_release(uint64_t handle);

/**
 * Export an identity-bound, single-snapshot checkpoint for an external state issuer.
 * This is historical data, not an authenticated commit acknowledgment or a durable save.
 * # Safety
 * `out` must be aligned writable exclusive storage of `out_size` bytes, disjoint from inputs.
 * Error clears payload only after pointer alignment and exact size validation succeed.
 */
int32_t nexus_v2_authority_checkpoint_v2(uint64_t handle,
                                         struct NexusV2AuthorityCheckpointV2 *out,
                                         uint32_t out_size);

/**
 * # Safety
 * `config_id` and `scope` must be readable byte ranges; `challenge_out` and `out` must be
 * aligned, writable, nonoverlapping storage exclusive for this call.
 */
int32_t nexus_v2_bootstrap_challenge_create(struct NexusV2Bytes config_id,
                                            struct NexusV2Bytes scope,
                                            uint32_t max_age_ms,
                                            uint64_t *challenge_out,
                                            struct NexusV2BootstrapChallenge *out,
                                            uint32_t out_size);

int32_t nexus_v2_bootstrap_challenge_release(uint64_t handle);

/**
 * # Safety
 * `request` and all advertised byte ranges must remain readable and immutable; `out` is
 * aligned writable exclusive u64 storage. Domain 0 production always denies; 1 fixture only.
 */
int32_t nexus_v2_authority_provision(uint32_t domain,
                                     uint64_t challenge_handle,
                                     const struct NexusV2ProvisioningRequest *request,
                                     uint64_t *out);

/**
 * # Safety
 * `request` and all advertised byte ranges must remain readable and immutable.
 */
int32_t nexus_v2_authority_update_provisioned(uint64_t handle,
                                              uint64_t challenge_handle,
                                              const struct NexusV2ProvisioningRequest *request);

/**
 * # Safety
 * `out` must be aligned, writable, nonoverlapping storage for `out_size` bytes, exclusive for this call.
 * Error clears payload only after alignment and exact output size validation succeed.
 */
int32_t nexus_v2_authority_checkpoint(uint64_t handle,
                                      struct NexusV2AuthorityCheckpoint *out,
                                      uint32_t out_size);

/**
 * Begin a one-shot request bound to the current authority snapshot and checkpoint.
 * # Safety
 * Outputs must be live, aligned, writable/exclusive and disjoint. Exact POD extent
 * is required. Errors clear payload only after pointer/size validation succeeds.
 */
int32_t nexus_v2_checkpoint_commit_begin(uint64_t authority_handle,
                                         uint32_t max_age_ms,
                                         uint64_t *out_handle,
                                         struct NexusV2CheckpointCommitRequest *out,
                                         uint32_t out_size);

/**
 * Verify signed State-purpose commit and CheckpointAnchor-purpose witness documents.
 * # Safety
 * Input prefixes/ranges must be live readable immutable storage. Output handle/POD
 * must be aligned, writable/exclusive and disjoint from inputs and each other.
 * Valid output buffers are cleared on error; invalid extent/alignment has no write promise.
 */
int32_t nexus_v2_checkpoint_commit_complete(uint64_t request_handle,
                                            const struct NexusV2SignedDocument *commit,
                                            const struct NexusV2SignedDocument *anchor,
                                            uint64_t *out_handle,
                                            struct NexusV2CheckpointCommitReceipt *out,
                                            uint32_t out_size);

int32_t nexus_v2_checkpoint_commit_validate(uint64_t handle);

int32_t nexus_v2_checkpoint_commit_request_release(uint64_t handle);

int32_t nexus_v2_checkpoint_commit_receipt_release(uint64_t handle);

/**
 * # Safety
 * Config is readable/aligned for its declared extent; output is live writable/exclusive u64, disjoint.
 */
int32_t nexus_v2_capture_create(uint64_t session,
                                const struct NexusV2CaptureConfig *config,
                                uint64_t *out);

/**
 * # Safety
 * Frame/pixels are live readable immutable disjoint ranges; output is live aligned writable/exclusive exact size.
 */
int32_t nexus_v2_capture_submit(uint64_t h,
                                const struct NexusV2FrameInput *input,
                                struct NexusV2CaptureProgress *out,
                                uint32_t size);

/**
 * # Safety
 * Output is live aligned writable/exclusive exact-sized storage; invalid size/pointer has no clearing promise.
 */
int32_t nexus_v2_capture_progress(uint64_t h,
                                  struct NexusV2CaptureProgress *out,
                                  uint32_t size);

/**
 * Idempotent core finalization; repeated calls may return handles to the same immutable result.
 * # Safety
 * Output is live aligned writable/exclusive u64.
 */
int32_t nexus_v2_capture_finalize(uint64_t h, uint64_t *out);

int32_t nexus_v2_capture_cancel(uint64_t h);

int32_t nexus_v2_capture_release(uint64_t h);

/**
 * Historical reading only; use capture_result_validate before current use.
 * # Safety
 * Output is live aligned writable/exclusive exact-sized storage; unsafe extents have no clearing promise.
 */
int32_t nexus_v2_capture_result_read(uint64_t h,
                                     struct NexusV2CaptureProgress *out,
                                     uint32_t size);

int32_t nexus_v2_capture_result_validate(uint64_t h, uint64_t r);

int32_t nexus_v2_capture_result_release(uint64_t h);

/**
 * Uses the same typed capture registry and core evaluator as v1.
 * # Safety
 * Config prefix/full extent is readable/aligned; out is live exclusive writable u64, disjoint.
 */
int32_t nexus_v2_capture_create_v2(uint64_t session,
                                   const struct NexusV2CaptureConfigV2 *config,
                                   uint64_t *out);

/**
 * # Safety
 * Same frame/pixels contract as capture_submit; output is live exclusive aligned exact-sized POD.
 */
int32_t nexus_v2_capture_submit_v2(uint64_t h,
                                   const struct NexusV2FrameInput *input,
                                   struct NexusV2CaptureProgressV2 *out,
                                   uint32_t size);

/**
 * # Safety
 * Output is live exclusive aligned exact-sized POD; invalid extent/alignment has no clearing promise.
 */
int32_t nexus_v2_capture_progress_v2(uint64_t h,
                                     struct NexusV2CaptureProgressV2 *out,
                                     uint32_t size);

/**
 * Historical read; existing finalize/validate/cancel/release operations support both contracts.
 * # Safety
 * Output is live exclusive aligned exact-sized POD; invalid extent/alignment has no clearing promise.
 */
int32_t nexus_v2_capture_result_read_v2(uint64_t h,
                                        struct NexusV2CaptureProgressV2 *out,
                                        uint32_t size);

/**
 * Create an immutable processing configuration; initialize/configure using existing lifecycle APIs.
 * # Safety
 * Config is readable/aligned for its declared extent; output is live writable, exclusive and disjoint.
 */
int32_t nexus_v2_session_create_configured(const struct NexusV2ProcessingConfig *c,
                                           uint64_t *out);

/**
 * Process through the same Rust pipeline as Session::process. Output is a typed immutable result handle.
 * # Safety
 * Frame prefix/declared extent and pixel range are live readable immutable aligned storage;
 * output handle is live writable/exclusive and disjoint. Pixel range is bounded at 16 MiB.
 */
int32_t nexus_v2_process_frame(uint64_t handle,
                               const struct NexusV2FrameInput *input,
                               uint64_t *out);

/**
 * Historical data only; validate the retained handle against the session before using it as current.
 * # Safety
 * Output must be live, aligned, exclusive/writable and exact-sized; error clears valid output only.
 */
int32_t nexus_v2_processing_result_read(uint64_t h,
                                        struct NexusV2ProcessingResult *out,
                                        uint32_t size);

int32_t nexus_v2_processing_result_validate(uint64_t session, uint64_t h);

int32_t nexus_v2_processing_result_release(uint64_t h);

/**
 * # Safety
 * Output must be live aligned writable/exclusive of exact size; invalid extent has no clearing promise.
 */
int32_t nexus_v2_processing_capabilities(uint64_t session,
                                         struct NexusV2ProcessingCapabilities *out,
                                         uint32_t size);

/**
 * Idempotent nonce lookup for a Created session generation; successful configure consumes it.
 * # Safety
 * Output is live, aligned, writable/exclusive exact-sized storage, disjoint from inputs.
 */
int32_t nexus_v2_session_platform_challenge(uint64_t session,
                                            struct NexusV2PlatformChallenge *out,
                                            uint32_t size);

/**
 * Read historical core-owned assessment using the existing processing result handle.
 * # Safety
 * Output is live, aligned, writable/exclusive exact-sized storage. Invalid extents have no clearing promise.
 */
int32_t nexus_v2_processing_platform_read(uint64_t h,
                                          struct NexusV2PlatformAssessment *out,
                                          uint32_t size);

/**
 * ABI 2.5: fresh recovery of an exact historical signed operation.
 * Reuses the ABI 2.4 POD layouts with distinct typed recovery handles.
 * # Safety
 * Inputs must be live readable immutable views. Outputs must be live, aligned, writable/exclusive and disjoint. Exact POD extent
 * is required. Errors clear payload only after pointer/size validation succeeds.
 */
int32_t nexus_v2_checkpoint_recovery_begin(uint64_t authority_handle,
                                           struct NexusV2Bytes original_request,
                                           const struct NexusV2SignedDocument *original_commit,
                                           uint32_t max_age_ms,
                                           uint64_t *out_handle,
                                           struct NexusV2CheckpointCommitRequest *out,
                                           uint32_t out_size);

/**
 * Verify signed State-purpose commit and CheckpointAnchor-purpose witness documents.
 * # Safety
 * Input prefixes/ranges must be live readable immutable storage. Output handle/POD
 * must be aligned, writable/exclusive and disjoint from inputs and each other.
 * Valid output buffers are cleared on error; invalid extent/alignment has no write promise.
 */
int32_t nexus_v2_checkpoint_recovery_complete(uint64_t request_handle,
                                              const struct NexusV2SignedDocument *commit,
                                              const struct NexusV2SignedDocument *anchor,
                                              uint64_t *out_handle,
                                              struct NexusV2CheckpointCommitReceipt *out,
                                              uint32_t out_size);

int32_t nexus_v2_checkpoint_recovery_validate(uint64_t handle);

int32_t nexus_v2_checkpoint_recovery_request_release(uint64_t handle);

int32_t nexus_v2_checkpoint_recovery_receipt_release(uint64_t handle);

#endif  /* NEXUS_V2_H */
