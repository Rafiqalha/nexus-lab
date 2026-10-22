# Post-push provenance

The original device validation report was written before this lab had a remote and remains unchanged.
The repository now uses the verified origin `https://github.com/Rafiqalha/nexus-lab.git` on main.
Its pre-enrollment remote baseline is `494221c55dc3e6736bc075826c02115ffd28382f`.

The authenticated enrollment milestone consumes SDK main
`ad41f5fde927de5eed2c9d7a104bc67fc22b8fb6` (ABI 2.10, 58 exports).
`SDK_IDENTITY.json` binds the exact packaged ARM64 source digest and library/header/manifest hashes.
New device and local checks are recorded separately in ENROLLMENT_VALIDATION.md/json.
The enrollment service is a separate sibling repository; it has no remote until the owner assigns one.
