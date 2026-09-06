"""Observe a real separately installed helper through the public evidence CLI.

These Linux process-map checks use FOLIO_HARFBUZZ_HELPER; they do not supply
shaping expectations or certify another platform.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class NativeObservationTest(unittest.TestCase):
    def observe(self, helper, environment=None):
        result = subprocess.run([sys.executable, str(ROOT / "scripts/t29-native-observation.py"),
                                 str(helper)], env=environment, capture_output=True, text=True)
        self.assertTrue(result.stdout.strip(), result.stderr)
        return result, json.loads(result.stdout)

    def test_actual_loaded_engine_and_installation_receipt_are_traceable(self):
        helper = Path(os.environ["FOLIO_HARFBUZZ_HELPER"])
        result, record = self.observe(helper)
        self.assertEqual(0, result.returncode, result.stderr + result.stdout)
        self.assertEqual("pass", record["result"])
        self.assertEqual("Linux", record["platform"]["system"])
        receipt = helper.parent.parent / "installation.json"
        self.assertEqual(hashlib.sha256(receipt.read_bytes()).hexdigest(), record["installation"]["sha256"])
        self.assertEqual(hashlib.sha256(helper.read_bytes()).hexdigest(), record["helper"]["sha256"])
        engine = record["loaded_engine"]
        self.assertEqual(hashlib.sha256(Path(engine["path"]).read_bytes()).hexdigest(), engine["sha256"])
        self.assertIn("libharfbuzz", record["process_maps"])
        self.assertTrue(record["loaded_files"])
        self.assertEqual("10.2.0", record["installation"]["receipt"]["engine_version"])

    def test_missing_installation_is_indeterminate(self):
        with tempfile.TemporaryDirectory() as temporary:
            result, record = self.observe(Path(temporary) / "bin/folio-harfbuzz")
            self.assertEqual(2, result.returncode)
            self.assertEqual("indeterminate", record["result"])
            self.assertIn("unavailable", record["finding"])

    def test_changed_helper_cannot_reuse_an_installation_receipt(self):
        helper = Path(os.environ["FOLIO_HARFBUZZ_HELPER"])
        with tempfile.TemporaryDirectory() as temporary:
            installation = Path(temporary) / "native"
            shutil.copytree(helper.parent.parent, installation, symlinks=True)
            changed = installation / "bin/folio-harfbuzz"
            with changed.open("ab") as output:
                output.write(b"\nFolio T29 changed helper fixture\n")
            result, record = self.observe(changed)
            self.assertEqual(1, result.returncode)
            self.assertEqual("fail", record["result"])
            self.assertIn("helper SHA-256 mismatch", record["finding"])

    def test_loader_override_cannot_claim_the_installed_engine_hash(self):
        helper = Path(os.environ["FOLIO_HARFBUZZ_HELPER"])
        receipt = json.loads((helper.parent.parent / "installation.json").read_text())
        library = next(item for item in receipt["engine_libraries"]
                       if Path(item["path"]).name.startswith("libharfbuzz.so."))
        with tempfile.TemporaryDirectory() as temporary:
            changed = Path(temporary) / "libharfbuzz.so.0"
            shutil.copy2(helper.parent.parent / library["path"], changed)
            with changed.open("ab") as output:
                output.write(b"\nFolio T29 changed engine fixture\n")
            environment = os.environ.copy()
            environment["LD_LIBRARY_PATH"] = temporary
            result, record = self.observe(helper, environment)
            self.assertEqual(1, result.returncode)
            self.assertEqual("fail", record["result"])
            self.assertIn("loaded engine SHA-256 mismatch", record["finding"])
            self.assertEqual(str(changed), record["loaded_engine"]["path"])

    def test_preloaded_engine_with_another_soname_cannot_produce_passing_traceability(self):
        helper = Path(os.environ["FOLIO_HARFBUZZ_HELPER"])
        receipt = json.loads((helper.parent.parent / "installation.json").read_text())
        library = next(item for item in receipt["engine_libraries"]
                       if Path(item["path"]).name.startswith("libharfbuzz.so."))
        with tempfile.TemporaryDirectory() as temporary:
            preload = Path(temporary) / "libforeignx.so.0"
            original = (helper.parent.parent / library["path"]).read_bytes()
            self.assertEqual(1, original.count(b"libharfbuzz.so.0\0"))
            preload.write_bytes(original.replace(b"libharfbuzz.so.0\0", b"libforeignx.so.0\0"))
            environment = os.environ.copy()
            environment["LD_PRELOAD"] = str(preload)
            result, record = self.observe(helper, environment)
            self.assertEqual(2, result.returncode)
            self.assertEqual("indeterminate", record["result"])
            self.assertIn("interposition", record["finding"])

    def test_malformed_or_unknown_receipt_schema_is_a_failure_record(self):
        helper = Path(os.environ["FOLIO_HARFBUZZ_HELPER"])
        with tempfile.TemporaryDirectory() as temporary:
            installation = Path(temporary) / "native"
            shutil.copytree(helper.parent.parent, installation, symlinks=True)
            receipt = installation / "installation.json"
            original = receipt.read_text()
            for key, value in (("compile_commands", []), ("schema_version", 2),
                               ("schema_version", True), ("compile_commands", {})):
                with self.subTest(field=key, value=value):
                    changed = json.loads(original)
                    if key == "compile_commands" and value == {}:
                        for item in changed["compile_commands"].values():
                            (installation / item["path"]).unlink()
                    changed[key] = value
                    receipt.write_text(json.dumps(changed))
                    result, record = self.observe(installation / "bin/folio-harfbuzz")
                    self.assertEqual(1, result.returncode)
                    self.assertEqual("fail", record["result"])
                    self.assertIn("installation receipt", record["finding"])


if __name__ == "__main__":
    unittest.main()
