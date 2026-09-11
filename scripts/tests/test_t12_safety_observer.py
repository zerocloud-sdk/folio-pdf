"""Qualifies the cross-process side-effect observer independently of Folio."""
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class T12SafetyObserverTest(unittest.TestCase):
    def test_quiet_child_and_each_deliberate_external_effect(self):
        programs = {
            "quiet": "pass",
            "read": "open(e['FOLIO_T12_READ_CANARY'],'rb').read()",
            "write": "open(e['FOLIO_T12_WRITE_CANARY'],'wb').write(b'changed')",
            "process": "subprocess.run([e['FOLIO_T12_LAUNCH_CANARY']],check=True)",
            "script": "open(e['FOLIO_T12_SCRIPT_CANARY'],'wb').write(b'changed')",
            "network": "socket.create_connection(('127.0.0.1',int(e['FOLIO_T12_CANARY_PORT']))).close()",
        }
        with tempfile.TemporaryDirectory() as tmp:
            for probe, program in programs.items():
                output = Path(tmp) / probe
                process = subprocess.run(["/usr/bin/python3", str(ROOT / "scripts/t12-safety-observer.py"), str(output),
                    "/usr/bin/python3", "-c", "import os,socket,subprocess; e=os.environ; " + program],
                    capture_output=True, text=True, timeout=20)
                self.assertEqual(0, process.returncode, process.stdout + process.stderr)
                record = json.loads((output / "observation.json").read_text())
                self.assertEqual("pass" if probe == "quiet" else "fail", record["result"], record)
                self.assertEqual({name: "pass" for name in programs if name != "quiet"}, record["qualification"])
                if probe != "quiet":
                    self.assertGreater(record["effects"][probe], 0)
                self.assertEqual(64, len(record["observer_sha256"]))
                self.assertEqual(64, len(record["python_sha256"]))
                self.assertEqual(0, record["child_exit"])


if __name__ == "__main__":
    unittest.main()
