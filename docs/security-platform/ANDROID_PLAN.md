# Spesifikasi Android, integrasi, dan acceptance

## SPEC

[I] Lab menjadi aplikasi referensi pengumpulan evidence Android dan consumer SDK.
Ia tidak menjadi otoritas akhir untuk keputusan transaksi. Backend bisnis wajib
memeriksa keputusan, grant dan context sendiri.

[C] `tools/build_android.sh` menggunakan Linux NDK host path, target
`aarch64-linux-android`, jniLibs ARM64 dan SDK package dari SDK_IDENTITY.json.
Gradle/import verification harus tetap mengikat exact profile/header/library/manifest.
Karena itu dukungan emulator x86_64 bukan perubahan tiga baris.

## PLAN: komponen yang diusulkan

Nama di bawah adalah calon struktur implementasi, bukan berkas yang sudah ada.

| Komponen | Tanggung jawab |
|---|---|
| security/Observation | Check ID/version, finding, execution status, scope, duration |
| security/Collector | Supported environment, prerequisites, bounded check interface |
| security/CollectorCoordinator | Deadline, concurrency, cancellation, session generation |
| security/PlatformCollector | Aggregate observasi environment tanpa menjadikannya verified |
| security/ProviderEvidenceAdapter | Request provider evidence dengan binding server |
| security/EvidenceTransport | Kirim bounded report melalui transport terautentikasi |
| security/SecurityUiState | Pisahkan observed, verified, stale, unavailable dan decision |
| Native adapter opsional | Own-process observations bila API yang tersedia memadai |

## Pembaruan implementasi — 30 Oktober 2026

[C] L1/L2 collector lokal tersedia pada branch `TES123` di
`app/src/main/java/org/nexus/lab/security/`:

- `Observation`: contract report schema 1, empat ID fixed, dan pemisahan execution
  dari finding.
- `CollectorCoordinator`: maksimal dua worker, batas 500 ms/check dimulai saat check
  benar-benar mulai dieksekusi dan 1500 ms keseluruhan, cooperative cancellation, reject
  hasil terlambat, output berurutan. Queue wait tidak menghabiskan budget check sendiri.
- `PlatformCollectors`: `app_identity`, debugger + `TracerPid`, environment terbatas,
  serta scan read-only `/proc/self/maps` maksimum 256 KiB/256 baris untuk dua indikator
  terbatas. Tidak memindai proses lain atau menulis sistem.
- Secured capture menjalankan collector sebelum capture lease. Hasil hanya ditampilkan
  sebagai `client_reported; not authorization`; belum dikirim karena endpoint action
  terautentikasi/provider verifier belum tersedia.

[C] Unit test mencakup schema/order, error menjadi `unknown`, timeout, bound concurrency
dan pre-cancel. Provider, transport `/v1/lab-security/*`, physical-device matrix dan
APK signer-rotation tests tetap NOT_RUN.

Tidak memasukkan private issuer key, provider server credentials, atau raw production
root signing material ke aplikasi. Tidak menambahkan C++ hanya untuk scanner bila
Rust/JNI yang ada cukup; evaluasi kebutuhan dependensi per kasus.

## Flow aplikasi

```text
User action -> authenticated server challenge
            -> coordinator [local checks | provider request | capture context]
            -> bounded report
            -> backend verification/normalization/policy
            -> scoped grant
            -> core verification + capture/session
            -> submit receipt/result
            -> backend business enforcement
```

Koneksi staging loopback bukan transport produksi. Remote device testing memerlukan
connectivity yang sesuai tanpa melonggarkan service identity/authentication.

## Kontrak observasi

Condition Unknown/Absent/Present terpisah dari Completed/Unsupported/PermissionDenied/
Timeout/Error/NotCollected. Tidak menemukan nama modul dikenal bukan bukti seluruh
instrumentation tidak ada. Report membawa check scope dan authority client_reported.
Report ID, challenge/session/action binding dan server freshness dipertahankan.
Clock lokal hanya dipakai sesuai domain elapsed; tidak menjadi otoritas expiry.

### Application identity
Gunakan package/signer/version dan platform-supported signing information sebagai
observasi lokal. Uji signer rotation dan split APK. External verified identity tetap
diperiksa server. Parser APK signing buatan sendiri bukan pekerjaan baseline.

### Environment
Build properties, ABI/translation, exposed devices dan sensor capabilities merupakan
sinyal terbatas. Akses proc/sysfs tergantung platform. Container tidak harus memiliki
jejak hypervisor. Sensor minim tidak otomatis emulator; translation tidak otomatis abuse.
Jangan memasukkan RDTSC universal classifier atau require-root collector.

### Debugging/instrumentation
Mulai dari platform debugging status lalu own-process observations yang diizinkan.
Batas byte/entry/time wajib. Thread/module names hanya indikator. Self-ptrace lockout,
driver kernel dan scanning proses lain bukan bagian baseline. Integrity scan library
sendiri memerlukan relocation/layout/build-aware evaluation sebelum dapat dipercaya.

### Capture dan UI
Pertahankan buffer bounds, stride, crop, rotation, timestamps dan generation ownership.
Tambahkan consistency observations hanya bila metadata tersedia. Jangan menyatakan
Camera2 metadata, camera ID, distinct frames atau signed receipt sebagai sensor proof.
Gunakan API UI/capture supported per Android version; accessibility sah bukan malware.
FLAG_SECURE merupakan kontrol tampilan, bukan universal capture detector.
Tidak ada asumsi bahwa app dapat melihat semua MediaProjection/overlay aplikasi lain.

## Lifecycle dan failure handling

| Event | Perilaku wajib |
|---|---|
| onStop/cancel/session close | Cancel tasks, close resources, reject late publication |
| Process death | Metadata/key recovery sesuai enrollment; fresh authorization |
| Key missing/changed | Explicit enrollment state; tidak silent replacement |
| Permission denied/revoked | Unknown + execution reason; bukan clean result |
| Provider timeout/no-GMS | Expose unavailable; server memilih retry/step-up/review/deny |
| Network lost after submit | Idempotent retry identitas request sama |
| Evidence/grant expired | Fresh server challenge; tidak memperpanjang di aplikasi |
| Repeated tap/concurrent session | Session/generation isolation dan bounded admission |
| Preview selected | Mode eksplisit; tidak menggantikan failed protected action |

## Packaging dan ABI

Jika x86_64 consumer dipilih, audit target linker/host tools, importer target check,
package manifest, Gradle abiFilters, library lookup, staged identity, generated
bindings, loaded ABI smoke dan instrumentation APK. Tidak melonggarkan hash/profile
gates supaya library sembarang bisa dimuat. x86_64 default preview atau negative
tests tidak menjadi bukti successful hardware enrollment.

SDK contract extension disepakati dahulu: strict schema parsers tidak otomatis
menerima extra fields. FFI changes harus additive/versioned sesuai compatibility
review, disertai output bounds, errors, ownership dan old/new consumer checks.

## Tickets dan acceptance

| Ticket | Output | Exit criterion |
|---|---|---|
| L0 | Exact baseline host/Android report | Commands, hashes, profile, exit code tercatat |
| L1 | Shared observation contract | Tidak ada Unknown -> Absent atau local -> hardware promotion |
| L2 | Coordinator + bounded collectors | Cancellation, concurrency, resource limits tested |
| L3 | Provider/transport integration | Wrong context/expiry/error handling teruji dengan backend |
| L4 | UX + lifecycle | Preview/protected/unavailable tidak tertukar |
| L5 | Optional x86_64 path | Exact package/ABI/load + instrumentation acceptance |
| L6 | Device/adversarial matrix | Per-case observations dan detection limits tercatat |
| L7 | Performance/shadow report | Overhead dan legitimate-user rejection diketahui |

## Test matrix minimum (semua baru NOT_RUN)

- Original/re-signed app, supported signer rotation, split APK dan unsupported version.
- Perangkat fisik normal, supported emulator, selected container jika dalam scope.
- No-GMS, provider outage/rate limit, time skew, server unreachable, lost response.
- Debugger/instrumentation pada app lab; forged local report tidak mengotorisasi server.
- Background/resume, permission revoke, process restart, key lost/revoked, concurrent sessions.
- Synthetic/replayed frame harness: provenance tetap Unverified.
- Legitimate accessibility/overlay scenarios dan unsupported APIs.
- Loaded library identity/ABI mismatch harus gagal dengan reason yang tepat.

Record setiap run: commits tiga repo, package/APK/library hashes, profile/ABI/API,
environment, test case, expected/observed result, timestamps dan bounded logs.
Tidak memasukkan account secrets, raw tokens, frames, full process maps atau device
global identifiers ke laporan umum.

## Optimasi dan finishing

Ukur p50/p95/p99 checks dan time-to-grant, CPU/PSS, allocations, ANR/crash, timeout.
Fast checks dipisah dari action-triggered checks; tidak full-scan per frame.
Cache hanya state stabil dengan invalidation/TTL jelas. Set budget berdasarkan
baseline, bukan angka kesiapan buatan. Remote runner/device dapat mengurangi beban
pengguna; periksa provider mendukung kamera/attestation/connectivity yang dibutuhkan.

Selesai untuk scope lab bila build identity konsisten, contract/lifecycle/device tests
lulus, limits/unsupported cases terdokumentasi, integrasi service enforced di server,
serta tidak ada klaim liveness/camera origin yang belum dibuktikan.
