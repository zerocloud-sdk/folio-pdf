"""The optional private comparator runtime must be qualified before loading it."""
import os
from pathlib import Path
import subprocess
import shutil
import tempfile
import unittest


class ImageMagickRuntimeTest(unittest.TestCase):
    def test_rejects_missing_or_unqualified_private_runtime(self):
        root = Path(__file__).resolve().parents[2]
        with tempfile.TemporaryDirectory() as directory:
            runtime = Path(directory) / "runtime"
            environment = dict(os.environ, IMAGEMAGICK_RUNTIME_DIRECTORY=str(runtime))
            for present in (False, True):
                if present:
                    runtime.mkdir()
                    (runtime / "libX11.so.6").write_bytes(b"unqualified library")
                observed = subprocess.run([str(root / "scripts/container-bin/imagemagick"), "--version"],
                                          cwd=root, env=environment, stdout=subprocess.PIPE,
                                          stderr=subprocess.STDOUT, text=True, timeout=30)
                self.assertNotEqual(0, observed.returncode, observed.stdout)
                self.assertIn("runtime", observed.stdout.lower())

    def test_rejects_extra_libraries_that_could_shadow_the_recorded_runtime(self):
        root = Path(__file__).resolve().parents[2]
        with tempfile.TemporaryDirectory() as directory:
            runtime = Path(directory)
            for name in ("libX11.so.6", "libxcb.so.1", "libharfbuzz.so.0", "libfribidi.so.0", "libgraphite2.so.3"):
                shutil.copyfile(Path("/lib/x86_64-linux-gnu") / name, runtime / name)
            (runtime / "libunqualified.so").write_bytes(b"unqualified additional dependency")
            observed = subprocess.run([str(root / "scripts/container-bin/imagemagick"), "--version"],
                                      cwd=root, env=dict(os.environ, IMAGEMAGICK_RUNTIME_DIRECTORY=str(runtime)),
                                      stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=30)
            self.assertNotEqual(0, observed.returncode, observed.stdout)
            self.assertIn("runtime", observed.stdout.lower())


if __name__ == "__main__":
    unittest.main()
