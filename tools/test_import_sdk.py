import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


SPEC = importlib.util.spec_from_file_location("import_sdk", Path(__file__).with_name("import_sdk.py"))
IMPORT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(IMPORT)


class ImportSdkValidationTest(unittest.TestCase):
    def package(self, staging=False):
        temporary = tempfile.TemporaryDirectory()
        root = Path(temporary.name)
        files = {
            "libnexus_sdk.so": b"library",
            "nexus_v2.h": b"header",
            "NOTICE.txt": b"notice",
            "DEPENDENCY_LICENSES/example/LICENSE": b"license",
        }
        for relative, contents in files.items():
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(contents)
        digest = lambda value: hashlib.sha256(value).hexdigest()
        manifest = {
            "source_commit": "a" * 40,
            "source_dirty": False,
            "source_digest": "b" * 64,
            "target": "aarch64-linux-android",
            "features": ["staging-enrollment"] if staging else [],
            "production_approved": False,
            "binary_audit": {
                "sha256": digest(files["libnexus_sdk.so"]),
                "exports": [f"export_{index}" for index in range(60)],
            },
            "files": [
                {"path": relative, "sha256": digest(contents)}
                for relative, contents in files.items()
            ],
        }
        (root / "MANIFEST.json").write_text(json.dumps(manifest))
        return temporary, root

    def test_complete_manifest_is_accepted(self):
        temporary, root = self.package()
        self.addCleanup(temporary.cleanup)
        IMPORT.validate_package(root, "a" * 40, False)

    def test_tampered_or_unlisted_file_is_rejected(self):
        temporary, root = self.package()
        self.addCleanup(temporary.cleanup)
        (root / "libnexus_sdk.so").write_bytes(b"changed")
        with self.assertRaises(SystemExit):
            IMPORT.validate_package(root, "a" * 40, False)
        (root / "libnexus_sdk.so").write_bytes(b"library")
        (root / "nested").mkdir()
        (root / "nested/MANIFEST.json").write_text("unlisted")
        with self.assertRaises(SystemExit):
            IMPORT.validate_package(root, "a" * 40, False)

    def test_profile_requires_explicit_matching_selection(self):
        temporary, root = self.package(staging=True)
        self.addCleanup(temporary.cleanup)
        with self.assertRaises(SystemExit):
            IMPORT.validate_package(root, "a" * 40, False)
        IMPORT.validate_package(root, "a" * 40, True)


if __name__ == "__main__":
    unittest.main()
