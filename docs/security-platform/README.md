# Android security-platform integration plan

Tanggal: 2026-10-28. Branch: `TES123`. Status: rancangan, belum implementasi.
Baseline lab: `e393c1c4eeb1d96070f5ea1197a21c9d16f6ac71`.

## Hubungan tiga repository

| Repository | Tanggung jawab |
|---|---|
| nexus-sdk | Kontrak evidence/core, signature/binding/freshness, FFI dan packaging |
| nexus-lab | Collector Android reference, coordinator, capture/UI, integration tests |
| nexus-enrollment | Enrollment/PoP, verifikasi evidence, normalisasi, policy dan grants |

Rancangan pusat: `Rafiqalha/nexus-sdk`, commit dokumentasi
`3174ffa`, folder `docs/security-platform/`.
Lihat terutama ARCHITECTURE.md, EVIDENCE_AND_COLLECTORS.md,
MVP_SPEC.md, WIRE_CONTRACT_V1.md, POLICY_AND_RELEASE.md,
BASELINE_AND_CORRECTIONS.md, VALIDATION_AND_OPERATIONS.md, dan DESIGN_CLOSURE.md.
Referensi lintas repo ini bukan dependency build dan tidak mengubah SDK_IDENTITY.json.

Dokumen lokal:
- [Spesifikasi Android dan acceptance](ANDROID_PLAN.md)
- [Handoff implementasi MVP-1](MVP_HANDOFF.md)

Rancangan service counterpart: `nexus-enrollment/docs/security-platform/BACKEND_PLAN.md`
dan `MVP_HANDOFF.md` pada branch pekerjaan `TES123`; belum merupakan API yang sudah tersedia.

## Status dan cara membaca

- [C] Fakta dari source yang diperiksa; [I] rancangan/inferensi; [S] hipotesis uji.
- Dokumen ini tidak menyatakan collector, provider adapter, atau x86_64 consumer
  telah diimplementasikan. Runtime build/test baru: NOT_RUN.
- Source baseline memiliki jalur enrollment dan verifikasi package identity;
  required security signals tambahan belum memiliki pipeline produksi lengkap.
- Tidak ada klaim emulator/Frida pasti terdeteksi atau manusia terbukti dari TEE.
- Collector lokal tetap client-reported meskipun diteruskan dan ditandatangani server.

## Urutan kerja

1. Reproduce host JNI dan Android build dengan exact SDK package identity.
2. Finalisasi kontrak evidence bersama SDK/service.
3. Bangun coordinator dan collector observasional, bukan enforcement langsung.
4. Integrasikan backend verification serta fresh grant.
5. Uji lifecycle, error/fallback, perangkat sah, emulator dan controlled modifications.
6. Ukur performance/false rejection sebelum shadow lalu limited enforcement.

## Git dan preservasi

Penulisan hanya di `TES123`. Worktree ini terpisah karena checkout audit lab pada
main memiliki perubahan lokal `gradlew.bat`. Perubahan tersebut tidak dimasukkan
ke commit dokumentasi. Main dan branch lain tidak boleh menjadi target edit/commit.
Push remote dilakukan terpisah setelah diminta; commit lokal bukan bukti remote branch.
