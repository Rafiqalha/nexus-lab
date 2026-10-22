# Post-push provenance

The original device validation report was written before this lab had a remote and remains unchanged.
The repository now uses the verified origin `https://github.com/Rafiqalha/nexus-lab.git` on main.
Its pre-enrollment remote baseline is `494221c55dc3e6736bc075826c02115ffd28382f`.

The authenticated enrollment milestone consumes SDK main
`ad41f5fde927de5eed2c9d7a104bc67fc22b8fb6` (ABI 2.10, 58 exports).
`SDK_IDENTITY.json` binds the exact packaged ARM64 source digest and library/header/manifest hashes.
New device and local checks are recorded separately in ENROLLMENT_VALIDATION.md/json.
The enrollment service is a separate sibling repository; it has no remote until the owner assigns one.

Enrollment implementation commit `1f9cbd222c5c0306a5d04c68df13182b1ed1396c` was pushed
to origin/main without force. GitHub Actions queried for that exact SHA returned zero runs;
this lab has no workflow configured. Local seven-test physical-device PASS is not hosted CI.
SDK run 39580027152 on ad41f5f completed all six jobs successfully.
