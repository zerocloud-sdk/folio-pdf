#!/usr/bin/env python3
"""Build a separately supplied, pinned engine and the project-owned helper.

Requires Python 3.12+, Meson 1.3.2+, Ninja, pkg-config, and native C/C++
compilers. Nothing is downloaded; no native binary enters a Maven artifact.
An installation receipt describes this build, not platform compatibility.
"""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import tarfile
import tempfile


ROOT = Path(__file__).resolve().parents[1]
PIN = ROOT / "scripts/harfbuzz-pin.properties"
ADAPTER = ROOT / "pdf-conversion/src/main/native"
COMPILER_ENVIRONMENT_KEYS = (
    "CC", "CXX", "CC_LD", "CXX_LD", "CFLAGS", "CXXFLAGS", "CPPFLAGS", "LDFLAGS",
    "CL", "_CL_", "LINK", "_LINK_", "LIB", "LIBPATH", "INCLUDE", "SDKROOT",
    "MACOSX_DEPLOYMENT_TARGET", "COMPILER_PATH", "GCC_EXEC_PREFIX", "LIBRARY_PATH",
    "CPATH", "C_INCLUDE_PATH", "CPLUS_INCLUDE_PATH")


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def artifact(path, relative_to=None):
    return {"path": str(path.relative_to(relative_to) if relative_to else path),
            "sha256": digest(path)}


def run(command, environment, log):
    log.write(json.dumps(command) + "\n")
    log.flush()
    subprocess.run(command, env=environment, stdout=log, stderr=subprocess.STDOUT, check=True)


def build_steps(meson, build, source, options, jobs):
    return [[meson, "setup", str(build), str(source)] + options,
            [meson, "compile", "-C", str(build), "-j", str(jobs), "--verbose"],
            [meson, "install", "-C", str(build), "--no-rebuild"]]


def meson_info(build, name):
    return json.loads((build / "meson-info" / ("intro-" + name + ".json")).read_text())


def linker_records(compilers, options, environment):
    """Resolve the linker identified by Meson through the selected compiler."""
    values = {option["name"]: option["value"] for option in options}
    records = {}
    for language, compiler in compilers["host"].items():
        identity = compiler["linker_id"]
        flags = values.get(language + "_args", []) + values.get(language + "_link_args", [])
        if identity in ("link", "lld-link"):
            explicit = environment.get("CC_LD" if language == "c" else "CXX_LD")
            candidate = explicit or identity
            lookup = ["PATH", candidate]
        else:
            # A linker implementation id (for example ld.bfd) is not the
            # executable name. GCC normally searches for ld, honoring -B.
            selected = environment.get("CC_LD" if language == "c" else "CXX_LD")
            for flag in compiler["exelist"] + flags:
                if flag.startswith("-fuse-ld=") or flag.startswith("--ld-path="):
                    selected = flag.split("=", 1)[1]
            name = "ld"
            if selected:
                name = selected if os.path.isabs(selected) or selected.startswith("ld.") else "ld." + selected
            lookup = compiler["exelist"] + flags + ["-print-prog-name=" + name]
            candidate = subprocess.check_output(lookup, env=environment, text=True).strip()
        executable = shutil.which(candidate)
        if not executable:
            raise ValueError("Cannot identify the native linker: " + identity)
        version_flag = "/?" if identity == "link" else "-v" if identity == "ld64" else "--version"
        observed = subprocess.run([executable, version_flag], env=environment, text=True,
                                  stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        if not observed.stdout.strip():
            raise ValueError("Native linker version is unavailable: " + identity)
        records[language] = dict(artifact(Path(executable).resolve()), id=identity,
                                 version=observed.stdout.strip(), lookup_command=lookup)
    return records


def install(archive, installation, jobs):
    if installation.exists() or installation.is_symlink():
        raise ValueError("Installation path already exists: " + str(installation))
    pin = dict(line.split("=", 1) for line in PIN.read_text().splitlines()
               if line and not line.startswith("#"))
    archive_hash = digest(archive)
    if archive_hash != pin["HARFBUZZ_ARCHIVE_SHA256"]:
        raise ValueError("HarfBuzz archive SHA-256 mismatch")
    environment = os.environ.copy()
    environment.pop("DESTDIR", None)
    build_tools = {"python": dict(artifact(Path(sys.executable).resolve()), version=sys.version)}
    for name in ("meson", "ninja", "pkg-config"):
        executable = shutil.which(name)
        if not executable:
            raise ValueError("Missing native build tool: " + name)
        build_tools[name] = dict(artifact(Path(executable).resolve()), version=subprocess.check_output(
            [executable, "--version"], text=True).strip())
    # Meson honors these overrides; select exactly the executables we recorded.
    environment["NINJA"] = build_tools["ninja"]["path"]
    environment["PKG_CONFIG"] = build_tools["pkg-config"]["path"]
    meson = shutil.which("meson")
    commands = []
    installation.parent.mkdir(parents=True, exist_ok=True)
    # mkdir is exclusive; cleanup below only touches the directory we created.
    installation.mkdir()
    try:
        with tempfile.TemporaryDirectory(prefix="folio-harfbuzz-") as temporary:
            work = Path(temporary)
            with tarfile.open(archive, "r:xz") as source_archive:
                source_archive.extractall(work, filter="data")
            source = work / ("harfbuzz-" + pin["HARFBUZZ_VERSION"])
            engine_build = work / "engine-build"
            adapter_build = work / "adapter-build"
            empty_pkgconfig = work / "empty-pkgconfig"
            empty_pkgconfig.mkdir()
            # HarfBuzz 10.2.0 probes pkg-config for FreeType even when disabled.
            # Keep the unmodified source from discovering optional host bindings.
            environment["PKG_CONFIG_LIBDIR"] = str(empty_pkgconfig)
            environment["PKG_CONFIG_PATH"] = str(empty_pkgconfig)
            environment.pop("PKG_CONFIG_SYSROOT_DIR", None)
            options = ["--prefix=" + str(installation), "--libdir=lib", "--buildtype=release",
                       "--wrap-mode=nodownload", "-Dauto_features=disabled", "-Ddefault_library=shared"]
            features = ("glib", "gobject", "cairo", "chafa", "icu", "graphite2", "freetype",
                        "gdi", "directwrite", "coretext", "wasm", "tests", "introspection",
                        "docs", "utilities", "benchmark")
            commands = build_steps(meson, engine_build, source,
                                   options + ["-D" + feature + "=disabled" for feature in features], jobs)
            with (installation / "build.log").open("w", encoding="utf-8") as log:
                for command in commands:
                    run(command, environment, log)
                environment["PKG_CONFIG_PATH"] = str(installation / "lib/pkgconfig")
                adapter_commands = build_steps(meson, adapter_build, ADAPTER, options, jobs)
                for command in adapter_commands:
                    commands.append(command)
                    run(command, environment, log)
            compiler_info = meson_info(engine_build, "compilers")
            adapter_compilers = meson_info(adapter_build, "compilers")
            build_options = {name: meson_info(build, "buildoptions")
                             for name, build in (("engine", engine_build), ("adapter", adapter_build))}
            compile_commands = {}
            for name, build in (("engine", engine_build), ("adapter", adapter_build)):
                target = installation / (name + "-compile-commands.json")
                shutil.copyfile(build / "compile_commands.json", target)
                compile_commands[name] = artifact(target, installation)
            for compiler in compiler_info["host"].values():
                for executable in compiler["exelist"] + compiler["linker_exelist"]:
                    resolved = shutil.which(executable)
                    if resolved:
                        build_tools[executable] = artifact(Path(resolved).resolve())
            libraries = [path for directory in ("lib", "bin")
                         for path in (installation / directory).glob("*harfbuzz*")
                         if path.is_file() and not path.is_symlink()
                         and (".so" in path.name or path.suffix in (".dll", ".dylib"))]
            helper = installation / "bin" / ("folio-harfbuzz.exe" if os.name == "nt" else "folio-harfbuzz")
            license_directory = installation / "share/licenses/harfbuzz"
            license_directory.mkdir(parents=True)
            shutil.copyfile(source / "COPYING", license_directory / "COPYING")
            receipt = {
                "schema_version": 1, "status": "installed",
                "engine_version": pin["HARFBUZZ_VERSION"],
                "created_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                "platform": {"system": platform.system(), "release": platform.release(),
                             "machine": platform.machine()},
                "source": {"url": pin["HARFBUZZ_ARCHIVE_URL"], "sha256": archive_hash},
                "helper": artifact(helper, installation),
                "engine_libraries": [artifact(path, installation) for path in sorted(libraries)],
                "adapter_source": artifact(ADAPTER / "folio-harfbuzz.c", ROOT),
                "adapter_build": artifact(ADAPTER / "meson.build", ROOT),
                "installer": artifact(Path(__file__).resolve(), ROOT),
                "pin": artifact(PIN, ROOT), "build_tools": build_tools,
                "compilers": compiler_info, "adapter_compilers": adapter_compilers,
                "build_commands": commands, "build_options": build_options,
                "compile_commands": compile_commands,
                "linkers": linker_records(compiler_info, build_options["engine"], environment),
                "adapter_linkers": linker_records(adapter_compilers, build_options["adapter"], environment),
                "compiler_environment": {key: environment[key] for key in COMPILER_ENVIRONMENT_KEYS
                                         if key in environment},
                "dependency_search": "isolated pkg-config directory; explicit installed engine for the adapter",
                "build_log": artifact(installation / "build.log", installation),
                "license": artifact(license_directory / "COPYING", installation)}
            (installation / "installation.json").write_text(
                json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    except BaseException:
        # Preserve the build diagnostic in stderr before removing our incomplete install.
        log_file = installation / "build.log"
        if log_file.is_file():
            print(log_file.read_text(encoding="utf-8", errors="replace"), file=sys.stderr)
        shutil.rmtree(installation)
        raise
    print("Installed explicit HarfBuzz helper: " + str(helper))
    print("Build receipt: " + str(installation / "installation.json"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path, help="separately acquired harfbuzz-10.2.0.tar.xz")
    parser.add_argument("installation", type=Path, help="new explicit installation directory")
    parser.add_argument("--jobs", type=int, default=2, help="parallel compiler jobs (default: 2)")
    args = parser.parse_args()
    if args.jobs < 1:
        parser.error("--jobs must be positive")
    if sys.version_info < (3, 12):
        parser.error("Python 3.12 or newer is required")
    try:
        install(args.archive.resolve(), args.installation.absolute(), args.jobs)
    except (OSError, ValueError, subprocess.CalledProcessError, tarfile.TarError) as error:
        parser.exit(1, str(error) + "\n")


if __name__ == "__main__":
    main()
