"""Explicit native installation CLI, exercised against the real external helper.

T29_HARFBUZZ_ARCHIVE names the separately acquired official 10.2.0 archive.
The glyph literals come from the pre-existing official hb-shape oracle.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class NativeInstallationTest(unittest.TestCase):
    def test_wrong_archive_fails_before_creating_an_installation(self):
        with tempfile.TemporaryDirectory() as temporary:
            archive = Path(temporary) / "wrong.tar.xz"
            archive.write_bytes(b"not the pinned upstream archive")
            installation = Path(temporary) / "native"
            result = subprocess.run([sys.executable, str(ROOT / "scripts/install-harfbuzz.py"),
                                     str(archive), str(installation)], capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("HarfBuzz archive SHA-256 mismatch", result.stderr)
            self.assertFalse(installation.exists())

    def test_existing_installation_is_not_overwritten(self):
        with tempfile.TemporaryDirectory() as temporary:
            installation = Path(temporary) / "native"
            installation.mkdir()
            marker = installation / "caller-owned"
            marker.write_bytes(b"preserve")
            result = subprocess.run([sys.executable, str(ROOT / "scripts/install-harfbuzz.py"),
                                     os.environ["T29_HARFBUZZ_ARCHIVE"], str(installation)],
                                    capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("Installation path already exists", result.stderr)
            self.assertEqual([marker], list(installation.iterdir()))
            self.assertEqual(b"preserve", marker.read_bytes())

    def test_explicit_installation_records_its_artifacts_and_shapes_through_the_external_protocol(self):
        with tempfile.TemporaryDirectory() as temporary:
            installation = Path(temporary) / "native"
            command = [sys.executable, str(ROOT / "scripts/install-harfbuzz.py"),
                       os.environ["T29_HARFBUZZ_ARCHIVE"], str(installation)]
            environment = os.environ.copy()
            unrelated_staging = Path(temporary) / "caller-staging"
            unrelated_staging.mkdir()
            environment["DESTDIR"] = str(unrelated_staging)
            result = subprocess.run(command, env=environment, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertEqual([], list(unrelated_staging.iterdir()))
            receipt = json.loads((installation / "installation.json").read_text())
            self.assertEqual("installed", receipt["status"])
            self.assertEqual("10.2.0", receipt["engine_version"])
            self.assertEqual("620e3468faec2ea8685d32c46a58469b850ef63040b3565cde05959825b48227",
                             receipt["source"]["sha256"])
            helper = installation / receipt["helper"]["path"]
            self.assertEqual(hashlib.sha256(helper.read_bytes()).hexdigest(), receipt["helper"]["sha256"])
            self.assertTrue(receipt["engine_libraries"])
            for library in receipt["engine_libraries"]:
                self.assertEqual(hashlib.sha256((installation / library["path"]).read_bytes()).hexdigest(), library["sha256"])
            self.assertTrue(receipt["build_tools"])
            self.assertTrue(receipt["build_commands"])
            self.assertTrue(receipt["adapter_source"]["sha256"])

            font = (ROOT / "pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance/fonts/noto/NotoSansArabic-Regular.ttf").read_bytes()
            text = "\u0644\u064e\u0627".encode("utf-16-be")
            payload = struct.pack(">IIIII4sI", 0x48525131, len(font), len(text) // 2, 16, 1, b"Arab", 2) + b"ar" + font + text
            capability = b"composition.shaping.harf-buzz"
            request = struct.pack(">IIH", 0x4f504451, 1, len(capability)) + capability + struct.pack(">Q", len(payload)) + payload
            # An installed helper must resolve its adjacent engine after moving
            # the explicitly installed directory, without a loader override.
            relocated = Path(temporary) / "relocated"
            installation.rename(relocated)
            helper = relocated / receipt["helper"]["path"]
            environment = os.environ.copy()
            environment.pop("LD_LIBRARY_PATH", None)
            environment.pop("DYLD_LIBRARY_PATH", None)
            response = subprocess.run([str(helper)], input=request, env=environment,
                                      capture_output=True, check=True).stdout
            self.assertEqual((0x4f504452, 1, 80), struct.unpack(">IIQ", response[:16]))
            self.assertEqual((0x48525331, 0, 10, 2, 0, 1000, 1, 2), struct.unpack(">8I", response[16:48]))
            self.assertEqual((292, 0, 0, 0, 249, 256, 704, 0, 582, 0, 0, 0), struct.unpack(">12i", response[48:]))

    def test_recorded_tools_are_used_and_effective_compiler_flags_are_retained(self):
        with tempfile.TemporaryDirectory() as temporary:
            installation = Path(temporary) / "native"
            environment = os.environ.copy()
            environment["NINJA"] = str(Path(temporary) / "wrong-ninja")
            environment["PKG_CONFIG"] = str(Path(temporary) / "wrong-pkg-config")
            environment["CFLAGS"] = "-DFOLIO_T29_PROVENANCE=1"
            environment["CXXFLAGS"] = "-DFOLIO_T29_PROVENANCE=1"
            selected_linker = None
            if sys.platform == "linux":
                # Give the compiler a real, separately identifiable GNU linker.
                # An ELF trailing note changes only the fixture's file identity.
                prefix = Path(temporary) / "tool-prefix"
                prefix.mkdir()
                selected_linker = prefix / "ld"
                shutil.copy2(shutil.which("ld"), selected_linker)
                with selected_linker.open("ab") as linker_file:
                    linker_file.write(b"\nFolio T29 linker provenance fixture\n")
                environment["CFLAGS"] += " -B" + str(prefix) + "/"
                environment["CXXFLAGS"] += " -B" + str(prefix) + "/"
            result = subprocess.run([sys.executable, str(ROOT / "scripts/install-harfbuzz.py"),
                                     os.environ["T29_HARFBUZZ_ARCHIVE"], str(installation)],
                                    env=environment, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            receipt = json.loads((installation / "installation.json").read_text())
            self.assertIn("-DFOLIO_T29_PROVENANCE=1", json.dumps(receipt["build_options"]))
            self.assertTrue(receipt["linkers"])
            for linker in receipt["linkers"].values():
                self.assertEqual(hashlib.sha256(Path(linker["path"]).read_bytes()).hexdigest(), linker["sha256"])
                self.assertTrue(linker["version"])
                if selected_linker is not None:
                    self.assertEqual(selected_linker, Path(linker["path"]))
            for name, command_record in receipt["compile_commands"].items():
                commands = installation / command_record["path"]
                self.assertEqual(hashlib.sha256(commands.read_bytes()).hexdigest(), command_record["sha256"])
                self.assertIn("-DFOLIO_T29_PROVENANCE=1", commands.read_text(), name)
            self.assertIn("-DFOLIO_T29_PROVENANCE=1", (installation / "build.log").read_text())


if __name__ == "__main__":
    unittest.main()
