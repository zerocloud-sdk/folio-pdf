#!/usr/bin/env python3
"""Record an explicit installation and its Linux helper's live startup mappings.

JSON is written to stdout. Exit codes: 0 pass, 1 mismatch, 2 unavailable.
This observes a separate invocation in the inherited loader environment; it
does not certify other platforms or inspect every Workflow subprocess.
"""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import time


ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    checksum = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(65536), b""):
            checksum.update(block)
    return checksum.hexdigest()


def artifact(path):
    return {"path": str(path), "sha256": digest(path)}


def installed_file(installation, item):
    path = (installation / item["path"]).resolve()
    path.relative_to(installation)
    if digest(path) != item["sha256"]:
        raise ValueError("Installed artifact SHA-256 mismatch: " + item["path"])
    return path


def live_files(helper):
    process = subprocess.Popen([str(helper)], stdin=subprocess.PIPE,
                               stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    try:
        maps_path = Path("/proc") / str(process.pid) / "maps"
        deadline = time.monotonic() + 5
        previous = None
        while time.monotonic() < deadline and process.poll() is None:
            maps = maps_path.read_text()
            if maps == previous and "libharfbuzz.so." in maps:
                files = set()
                for line in maps.splitlines():
                    fields = line.split(maxsplit=5)
                    if len(fields) == 6 and fields[5].startswith("/"):
                        path = Path(fields[5]).resolve()
                        if path.stat().st_ino != int(fields[4]):
                            raise OSError("A mapped file changed during observation")
                        files.add(path)
                return maps, [artifact(path) for path in sorted(files)]
            previous = maps
            time.sleep(0.02)
        raise OSError("The helper's live HarfBuzz mappings were unavailable")
    finally:
        # EOF is an incomplete request; the owned helper exits without shaping.
        try:
            process.communicate(b"", timeout=2)
        except subprocess.TimeoutExpired:
            process.kill()
            process.communicate()


def observe(helper):
    report = {
        "schema_version": 1,
        "created_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "platform": {"system": platform.system(), "release": platform.release(), "machine": platform.machine()},
        "observer": artifact(Path(__file__).resolve()),
        "python": dict(artifact(Path(sys.executable).resolve()), version=sys.version),
        "loader_environment": {key: os.environ[key] for key in ("LD_LIBRARY_PATH", "LD_PRELOAD", "LD_AUDIT")
                               if key in os.environ}}
    try:
        installation = helper.parent.parent.resolve()
        receipt_path = installation / "installation.json"
        if not helper.is_file() or not os.access(helper, os.X_OK) or not receipt_path.is_file():
            raise OSError("The explicit helper or installation receipt is unavailable")
        receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
        report["installation"] = dict(artifact(receipt_path), receipt=receipt)
        if (type(receipt["schema_version"]) is not int or receipt["schema_version"] != 1
                or not isinstance(receipt["compile_commands"], dict)
                or set(receipt["compile_commands"]) != {"engine", "adapter"}):
            raise ValueError("Unsupported or malformed installation receipt schema")
        report["helper"] = artifact(helper)
        if report["helper"]["sha256"] != receipt["helper"]["sha256"]:
            raise ValueError("Native helper SHA-256 mismatch")
        if installed_file(installation, receipt["helper"]) != helper:
            raise ValueError("Native helper does not match the recorded installation path")
        pin = dict(line.split("=", 1) for line in (ROOT / "scripts/harfbuzz-pin.properties").read_text().splitlines()
                   if line and not line.startswith("#"))
        if (receipt["status"] != "installed" or receipt["engine_version"] != pin["HARFBUZZ_VERSION"]
                or receipt["source"]["sha256"] != pin["HARFBUZZ_ARCHIVE_SHA256"]):
            raise ValueError("The installation does not identify the pinned HarfBuzz source")
        for field in ("adapter_source", "adapter_build", "pin"):
            installed_file(ROOT, receipt[field])
        for item in receipt["engine_libraries"]:
            installed_file(installation, item)
        for item in [receipt["license"], receipt["build_log"]] + list(receipt["compile_commands"].values()):
            installed_file(installation, item)
        if platform.system() != "Linux":
            raise OSError("Live engine observation is unavailable on this platform; actual platform evidence is required")
        maps, loaded = live_files(helper)
        report["process_maps"] = maps
        report["loaded_files"] = loaded
        if os.environ.get("LD_PRELOAD") or os.environ.get("LD_AUDIT"):
            raise OSError("Loader interposition prevents engine attribution from startup mappings")
        engines = [item for item in loaded if Path(item["path"]).name.startswith("libharfbuzz.so.")]
        if len(engines) != 1:
            raise OSError("One live HarfBuzz engine could not be identified")
        report["loaded_engine"] = engines[0]
        expected = {item["sha256"] for item in receipt["engine_libraries"]
                    if Path(item["path"]).name.startswith("libharfbuzz.so.")}
        if engines[0]["sha256"] not in expected:
            raise ValueError("Native loaded engine SHA-256 mismatch")
        report.update(result="pass", finding="The live startup engine matches the explicit installation receipt")
    except (OSError, subprocess.SubprocessError) as unavailable:
        report.update(result="indeterminate", finding=str(unavailable))
    except (ValueError, KeyError, TypeError, AttributeError) as mismatch:
        report.update(result="fail", finding=str(mismatch))
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("helper", type=Path)
    args = parser.parse_args()
    report = observe(args.helper.resolve())
    print(json.dumps(report, indent=2))
    return {"pass": 0, "fail": 1, "indeterminate": 2}[report["result"]]


if __name__ == "__main__":
    sys.exit(main())
