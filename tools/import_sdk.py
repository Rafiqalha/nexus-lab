#!/usr/bin/env python3
"""Import one complete source-bound SDK package and generate only used declarations."""

import hashlib
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def package_file(package: Path, relative: str) -> Path:
    candidate = Path(relative)
    require(
        relative != "MANIFEST.json"
        and not candidate.is_absolute()
        and relative == candidate.as_posix()
        and ".." not in candidate.parts,
        f"Invalid package path: {relative}",
    )
    path = package / candidate
    require(path.is_file() and not path.is_symlink(), f"Missing regular package file: {relative}")
    require(path.resolve().is_relative_to(package), f"Package path escapes root: {relative}")
    return path


def validate_package(
    package: Path, source_sha: str, staging: bool
) -> tuple[dict, Path, Path, str, str]:
    package = package.resolve()
    manifest_path = package / "MANIFEST.json"
    require(
        manifest_path.is_file() and not manifest_path.is_symlink(),
        "SDK package requires a regular MANIFEST.json",
    )
    manifest = json.loads(manifest_path.read_text())
    require(
        manifest.get("source_commit") == source_sha and manifest.get("source_dirty") is False,
        "SDK package must identify the supplied clean source commit",
    )
    require(manifest.get("target") == "aarch64-linux-android", "SDK target must be Android ARM64")
    features = manifest.get("features")
    require(features in ([], ["staging-enrollment"]), "Unsupported SDK feature profile")
    require(bool(features) == staging, "SDK profile and explicit --staging selection differ")
    require(manifest.get("production_approved") is False, "Production-approved package is unsupported")

    entries = manifest.get("files")
    require(isinstance(entries, list) and entries, "SDK manifest requires a nonempty file inventory")
    expected: set[str] = set()
    for entry in entries:
        require(isinstance(entry, dict), "Malformed SDK file entry")
        relative = entry.get("path")
        recorded = entry.get("sha256")
        require(
            isinstance(relative, str)
            and isinstance(recorded, str)
            and re.fullmatch(r"[0-9a-f]{64}", recorded) is not None
            and relative not in expected,
            "Malformed or duplicate SDK file identity",
        )
        path = package_file(package, relative)
        require(sha256(path) == recorded, f"SDK package hash mismatch: {relative}")
        expected.add(relative)
    actual = {
        path.relative_to(package).as_posix()
        for path in package.rglob("*")
        if path.is_file() and path != manifest_path
    }
    require(actual == expected, "SDK package contains missing or unlisted files")

    library = package_file(package, "libnexus_sdk.so")
    binary = manifest.get("binary_audit")
    require(isinstance(binary, dict), "SDK package lacks binary audit")
    library_hash = sha256(library)
    exports = binary.get("exports")
    require(
        binary.get("sha256") == library_hash
        and isinstance(exports, list)
        and len(exports) == 60
        and len(set(exports)) == 60,
        "SDK binary identity or export count mismatch",
    )
    header = package / "nexus_v2.h"
    if not header.exists():
        header = package / "include/nexus/nexus_v2.h"
    require(header.is_file() and not header.is_symlink(), "SDK package header is missing")
    header_hash = sha256(header)
    require(
        any(
            entry["path"] == header.relative_to(package).as_posix()
            and entry["sha256"] == header_hash
            for entry in entries
        ),
        "SDK header is not bound by the manifest",
    )
    return manifest, manifest_path, header, library_hash, header_hash


def main() -> None:
    require(len(sys.argv) in (3, 4), "usage: import_sdk.py PACKAGE SOURCE_SHA [--staging]")
    staging = len(sys.argv) == 4 and sys.argv[3] == "--staging"
    require(len(sys.argv) == 3 or staging, "Only --staging is accepted as an optional argument")
    package = Path(sys.argv[1]).resolve()
    source_sha = sys.argv[2]
    manifest, manifest_path, header, library_hash, header_hash = validate_package(
        package, source_sha, staging
    )
    manifest_hash = sha256(manifest_path)
    vendor = ROOT / (
        "vendor/android-arm64-"
        + ("staging-" if staging else "default-")
        + library_hash[:12]
        + "-"
        + manifest_hash[:12]
    )
    require(not vendor.exists(), "Refuse overwrite vendor package; select a fresh lab/import")

    # Every source/profile/file check and the immutable destination check precede mutation.
    subprocess.run(
        [sys.executable, str(ROOT / "tools/generate_bindings.py"), str(header)], check=True
    )
    vendor.parent.mkdir(exist_ok=True)
    shutil.copytree(package, vendor)
    identity = {
        "source_sha": source_sha,
        "source_digest": manifest["source_digest"],
        "target": manifest["target"],
        "staging_root_public_key": manifest.get("staging_root_public_key"),
        "profile": "staging-enrollment" if staging else "default-preview",
        "abi": "2.11",
        "package_directory": str(vendor.relative_to(ROOT)),
        "exports": 60,
        "sdk_sha256": library_hash,
        "header_sha256": header_hash,
        "package_manifest_sha256": manifest_hash,
    }
    identity_tmp = ROOT / "SDK_IDENTITY.json.importing"
    require(not identity_tmp.exists(), "Refuse stale SDK identity import temporary file")
    identity_tmp.write_text(json.dumps(identity, indent=2) + "\n")
    identity_tmp.replace(ROOT / "SDK_IDENTITY.json")

    legal = ROOT / "app/src/main/assets/nexus-sdk"
    legal.mkdir(parents=True, exist_ok=True)
    notice_tmp = legal / "NOTICE.txt.importing"
    licenses_tmp = legal / "DEPENDENCY_LICENSES.importing"
    require(
        not notice_tmp.exists() and not licenses_tmp.exists(),
        "Refuse stale legal import temporary files",
    )
    shutil.copy2(vendor / "NOTICE.txt", notice_tmp)
    shutil.copytree(vendor / "DEPENDENCY_LICENSES", licenses_tmp)
    licenses = legal / "DEPENDENCY_LICENSES"
    if licenses.exists():
        shutil.rmtree(licenses)
    licenses_tmp.replace(licenses)
    notice_tmp.replace(legal / "NOTICE.txt")
    (ROOT / "native/include").mkdir(exist_ok=True)
    shutil.copy2(header, ROOT / "native/include/nexus_v2.h")
    print("SDK_IMPORTED", library_hash)


if __name__ == "__main__":
    main()
