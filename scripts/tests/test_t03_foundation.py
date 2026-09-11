"""Acceptance-only candidate identity and stale-input rejection contracts."""
import contextlib
import importlib.util
import io
import json
import pathlib
import tempfile
import unittest
import zipfile
from unittest import mock


SPEC = importlib.util.spec_from_file_location(
    "t03_foundation", pathlib.Path(__file__).parents[1] / "t03-foundation.py")


class FoundationCandidateTest(unittest.TestCase):
    def test_metadata_collect_cli_requires_and_threads_selected_execution_profile(self):
        module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            run = root / "observations"
            run.mkdir()
            command = ["t03-foundation.py", "collect", str(run), "--root", str(root),
                       "--obligation", "metadata"]
            with mock.patch("sys.argv", command), contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit):
                    module.main()

            command += ["--execution-profile", "HARDENED_WORKER"]
            with mock.patch.object(module, "collect_reports", return_value={}) as collect:
                with mock.patch("sys.argv", command), contextlib.redirect_stdout(io.StringIO()):
                    module.main()
            collect.assert_called_once_with(root.resolve(), run.resolve(), "metadata",
                                            "HARDENED_WORKER")

    def test_metadata_case_freezes_t11_contract_tools_and_eight_tuple_plan(self):
        module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(module)
        case = module.certification_case("metadata")
        self.assertEqual("T11-metadata-outlines-destinations-attachments", case["profile"])
        self.assertEqual("T11", case["label"])
        self.assertEqual(73, case["test-count"])
        self.assertEqual([
            "net.zerocloud.pdf.consumer.DocumentMetadataWorkflowTest",
            "net.zerocloud.pdf.itext7.consumer.DocumentMetadataFacadeTest",
            "net.zerocloud.pdf.migration.itext7.contract.JarContractIT",
            "net.zerocloud.pdf.migration.itext7.contract.ClasspathExclusivityIT",
        ], case["test-classes"])
        self.assertEqual("arlington-t11-r1", case["standards-producer"])
        self.assertIn("capabilities/profiles/T11-metadata", case["configuration-paths"])
        self.assertIn("capabilities/profiles/T11-standards", case["configuration-paths"])
        self.assertIn("capabilities/expected/T11-standards-findings.json", case["configuration-paths"])
        self.assertIn("build-tools/acceptance/arlington/t11-r1.patch", case["configuration-paths"])
        self.assertIn("scripts/t11-arlington-pin.properties", case["configuration-paths"])

        tools = {item["id"]: item for item in module.certification_tools()}
        self.assertEqual("0.81-folio-t11-r1", tools["arlington-t11-r1"]["version"])
        self.assertEqual(["standards"], tools["arlington-t11-r1"]["chains"])
        self.assertEqual(["semantic"], tools["folio-pdf-t11"]["chains"])

        root = pathlib.Path("/workspace-source")
        scope = root / "target/metadata-plan/jdk21-hardened_worker"
        plan = module.execution_plan(root, scope, "example.invalid/image@sha256:abc",
                                     pathlib.Path("/cache/harfbuzz/bin/folio-harfbuzz"),
                                     "/workspace/candidate.jar", case, "HARDENED_WORKER")
        self.assertIn("-Dfolio.t11.executionProfile=HARDENED_WORKER", plan["java-options"])
        self.assertIn("net.zerocloud.pdf.acceptance.T11EvidenceCommand", plan["recorder-command"])
        self.assertEqual(73, plan["required-test-count"])
        self.assertEqual(case["test-classes"], plan["contract-tests-command"][-4:])

    def test_metadata_reports_bind_eight_products_three_checkers_controls_and_safety(self):
        module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            run = root / "run"
            run.mkdir()
            passing = ("syntax=pass\nstandards=pass\nsemantic=pass\nvisual=pass\nsafety=pass\n"
                       "native-execution-profile=HARDENED_WORKER\n"
                       "facade-execution-profile=IN_PROCESS\n")
            (run / "result.properties").write_text(passing)
            negative = run / "negative"
            negative.mkdir()
            (negative / "result.properties").write_text(
                "syntax=fail\nstandards=fail\nsemantic=fail\nvisual=fail\n")
            for chain in module.CHAINS:
                (negative / (chain + ".txt")).write_text("detected " + chain)
            (negative / "invalid.pdf").write_bytes(b"invalid")
            semantic_controls = negative / "semantic"
            semantic_controls.mkdir()
            for change in ("target", "operand", "packet", "payload", "retained-info"):
                (semantic_controls / (change + ".pdf")).write_bytes(change.encode())
                (semantic_controls / (change + ".properties")).write_text("semantic=fail\n")
            visual_controls = negative / "visual"
            visual_controls.mkdir()
            (visual_controls / "one-pixel-control.png").write_bytes(b"png")
            (visual_controls / "one-pixel-control.properties").write_text("visual=fail\n")

            safety = run / "safety"
            safety.mkdir()
            (safety / "result.properties").write_text(
                "result=pass\nsigned=pass\n"
                "native-execution-profile=HARDENED_WORKER\n"
                "facade-execution-profile=IN_PROCESS\n")
            (safety / "signed.properties").write_text(
                "result=pass\nnative.execution-profile=HARDENED_WORKER\n"
                "facade.execution-profile=IN_PROCESS\n")
            for api in ("native", "facade"):
                api_dir = safety / api
                api_dir.mkdir()
                worker_scenarios = "".join(
                    scenario + ".worker-file-positive-control=denied\n"
                    + scenario + ".worker-network-positive-control=denied\n"
                    + scenario + ".worker-process-isolated=pass\n"
                    for scenario in ("external-file", "external-network", "malformed",
                                     "inert-xinclude"))
                observed = ("result=pass\nexecution-profile=HARDENED_WORKER\n"
                            "worker-file-positive-control=denied\n"
                            "worker-network-positive-control=denied\n"
                            "worker-process-isolated=pass\n" + worker_scenarios) if api == "native" else (
                                "result=pass\nexecution-profile=IN_PROCESS\n"
                                "observer.file-attempts=1\nobserver.network-attempts=1\n")
                (api_dir / "safety.properties").write_text(observed)
                (api_dir / "child.log").write_text("guard installed\n")

            editions = [api + "-" + product for api in ("native", "facade")
                        for product in ("edited", "merged", "left", "right")]
            for edition in editions:
                product = run / edition
                product.mkdir()
                artifact = product / "metadata.pdf"
                artifact.write_bytes(("metadata " + edition).encode())
                (product / "result.properties").write_text(
                    passing + "execution-profile="
                    + ("HARDENED_WORKER" if edition.startswith("native-") else "IN_PROCESS")
                    + "\ninput-sha256=" + module.sha256(artifact)
                    + "\npage-count=1\npage.1.visual=pass\n")
                for chain in module.CHAINS:
                    (product / (chain + ".txt")).write_text(edition + " " + chain)
                for suffix in ("visual.md", "visual.txt", "expected.png", "pdfium.png",
                               "implementation.png", "difference.png", "renderer-difference.png"):
                    (product / ("page-1-" + suffix)).write_bytes(suffix.encode())
                for checker in ("pdfcpu", "arlington-core", "arlington-metadata"):
                    tool = product / checker
                    tool.mkdir()
                    rule = checker + "-rule"
                    (tool / "standards.properties").write_text(
                        "result=pass\ncovered-rules=" + rule + "\n")
                    (tool / "findings.txt").write_text("qualified")
                    (tool / ("negative-" + rule + ".txt")).write_text("detected")
                    (tool / ("control-" + rule + ".pdf")).write_bytes(b"illegal")

            reports = module.collect_reports(root, run, "metadata", "HARDENED_WORKER")
            self.assertEqual(8, len(reports["syntax"]["products"]))
            self.assertTrue(any(item["path"].endswith(
                "arlington-metadata/standards.properties")
                for item in reports["standards"]["findings"]))
            self.assertTrue(any(item["path"].endswith("safety/signed.properties")
                                for item in reports["semantic"]["findings"]))
            self.assertTrue(any(item["path"].endswith("negative/semantic/target.pdf")
                                for item in reports["semantic"]["negative-controls"]))
            self.assertTrue(any(item["path"].endswith("negative/visual/one-pixel-control.png")
                                for item in reports["visual"]["negative-controls"]))

            self_consistent_files = [
                run / "result.properties",
                safety / "result.properties",
                safety / "signed.properties",
                safety / "native/safety.properties",
            ] + [run / edition / "result.properties"
                 for edition in editions if edition.startswith("native-")]
            hardened_contents = {path: path.read_text() for path in self_consistent_files}
            for path in self_consistent_files:
                path.write_text(path.read_text().replace("HARDENED_WORKER", "IN_PROCESS"))
            with self.assertRaisesRegex(ValueError, "selected execution profile"):
                module.collect_reports(root, run, "metadata", "HARDENED_WORKER")
            for path, content in hardened_contents.items():
                path.write_text(content)

            native_product = run / "native-edited/result.properties"
            native_product.write_text(native_product.read_text().replace(
                "execution-profile=HARDENED_WORKER", "execution-profile=IN_PROCESS"))
            with self.assertRaisesRegex(ValueError, "native-edited.*profile"):
                module.collect_reports(root, run, "metadata", "HARDENED_WORKER")
            native_product.write_text(hardened_contents[native_product])

            (safety / "result.properties").write_text(
                "result=pass\nsigned=pass\n"
                "native-execution-profile=IN_PROCESS\n"
                "facade-execution-profile=IN_PROCESS\n")
            with self.assertRaisesRegex(ValueError, "safety.*profile"):
                module.collect_reports(root, run, "metadata", "HARDENED_WORKER")

            (safety / "result.properties").write_text(
                "result=pass\nsigned=pass\n"
                "native-execution-profile=HARDENED_WORKER\n"
                "facade-execution-profile=IN_PROCESS\n")
            native_safety = safety / "native/safety.properties"
            native_safety.write_text(native_safety.read_text().replace(
                "external-network.worker-network-positive-control=denied",
                "external-network.worker-network-positive-control=allowed"))
            with self.assertRaisesRegex(ValueError, "external-network"):
                module.collect_reports(root, run, "metadata", "HARDENED_WORKER")

    def test_snapshot_requires_the_actual_complete_artifact_set(self):
        module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root / "source").write_bytes(b"reviewed source\n")
            contract = {"release": "0.1.0", "source-roots": ["source"],
                        "source-exclusions": [], "required-artifacts": ["product.jar"]}
            with self.assertRaises(FileNotFoundError):
                module.snapshot(root, contract)
            (root / "product.jar").write_bytes(b"actual artifact\n")
            recorded = module.snapshot(root, contract)
            self.assertEqual(["product.jar"], [item["path"] for item in recorded["artifacts"]])
            (root / "source").write_bytes(b"changed source\n")
            with self.assertRaisesRegex(ValueError, "changed"):
                module.require_unchanged(root, contract, recorded)

    def test_stages_exact_bytes_in_an_unsigned_candidate_bundle(self):
        module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root / "pdf-example/target").mkdir(parents=True)
            (root / "pdf-example/.flattened-pom.xml").write_bytes(b"candidate pom")
            (root / "pdf-example/target/pdf-example-0.1.0.jar").write_bytes(b"candidate jar")
            contract = {"release": "0.1.0", "required-artifacts": [
                "target/foundation-0.1.0/artifacts/pdf-example-0.1.0.pom",
                "target/foundation-0.1.0/artifacts/pdf-example-0.1.0.jar",
                "target/foundation-0.1.0/central-bundle.zip"]}
            module.stage_products(root, contract)
            with zipfile.ZipFile(root / contract["required-artifacts"][-1]) as bundle:
                self.assertEqual(["net/zerocloud/pdf-example/0.1.0/pdf-example-0.1.0.jar",
                                  "net/zerocloud/pdf-example/0.1.0/pdf-example-0.1.0.pom"], bundle.namelist())
                self.assertEqual(b"candidate jar", bundle.read(bundle.namelist()[0]))

    def test_chain_report_generation_rejects_an_unobserved_chain(self):
        module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            run = root / "run"
            run.mkdir()
            (run / "result.properties").write_text("syntax=pass\nstandards=pass\nsemantic=pass\nvisual=indeterminate\n")
            with self.assertRaisesRegex(ValueError, "visual"):
                module.collect_reports(root, run)

    def test_chain_reports_bind_each_product_and_raw_negative(self):
        module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            run = root / "run"
            run.mkdir()
            passing = "syntax=pass\nstandards=pass\nsemantic=pass\nvisual=pass\n"
            (run / "result.properties").write_text(passing)
            for name in ["native", "facade", "negative"]:
                product = run / name
                product.mkdir()
                (product / "blank.pdf").write_bytes(b"unit test artifact")
                verdict = passing if name != "negative" else passing.replace("pass", "fail")
                (product / "result.properties").write_text(verdict + "input-sha256=" + module.sha256(product / "blank.pdf") + "\n")
                for chain in ["syntax", "standards", "semantic", "visual"]:
                    (product / (chain + ".txt")).write_text("unit test " + name + " " + chain)
            for name in ["native", "facade"]:
                for checker in ["pdfcpu", "arlington"]:
                    tool = run / name / checker
                    tool.mkdir()
                    (tool / "standards.properties").write_text("result=pass\ncovered-rules=unit-rule\n")
                    for filename in ["findings.txt", "negative-unit-rule.txt", "control-unit-rule.pdf"]:
                        (tool / filename).write_text("unit test observation")
            reports = module.collect_reports(root, run)
            self.assertEqual({"syntax", "standards", "semantic", "visual"}, set(reports))
            self.assertEqual("run/native/blank.pdf", reports["syntax"]["products"][0]["path"])
            self.assertEqual("run/negative/visual.txt", reports["visual"]["negative-controls"][0]["path"])
            (run / "native/blank.pdf").write_bytes(b"changed after checking")
            with self.assertRaisesRegex(ValueError, "changed"):
                module.collect_reports(root, run)

    def test_environment_identity_comes_from_observations(self):
        module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(module)
        expected = {"os-version": "24.04", "jdk-build": "1.8.0_502-b07"}
        actual = {"os-version": "22.04", "jdk-build": "1.8.0_502-b07"}
        with self.assertRaisesRegex(ValueError, "environment"):
            module.require_environment(expected, actual)
        self.assertEqual(expected, module.require_environment(expected, dict(expected)))

    def test_certification_rejects_sources_changed_since_the_build(self):
        module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root / "source.java").write_text("compiled source")
            (root / "product.jar").write_text("compiled product")
            (root / "facade.yaml").write_text("declared surface")
            (root / "tests.jar").write_text("compiled consumer tests")
            contract = {"release": "0.1.0", "source-roots": ["source.java"],
                        "source-exclusions": [], "required-artifacts": ["product.jar"]}
            receipt = {"candidate": module.snapshot(root, contract),
                       "contract-inputs": [module.reference(root, root / "facade.yaml")],
                       "harness": [module.reference(root, root / "tests.jar")]}
            record = root / "build-inputs.json"
            record.write_text(json.dumps(receipt))
            module.require_staged_build(root, contract, record)
            for filename in ["source.java", "product.jar", "facade.yaml", "tests.jar"]:
                path = root / filename
                original = path.read_bytes()
                path.write_bytes(b"edited after stage")
                with self.assertRaisesRegex(ValueError, "build"):
                    module.require_staged_build(root, contract, record)
                path.write_bytes(original)

    def test_staging_rejects_inputs_changed_during_the_build(self):
        module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root / "capabilities").mkdir()
            contract = {"release": "0.1.0", "source-roots": ["source.java"],
                        "source-exclusions": [], "required-artifacts": ["product.jar"],
                        "requirements": "requirements.yaml", "environments": "environments.yaml",
                        "platform-decision": "decision.md", "obligations": [{"profile-contract": "profile.md"}]}
            files = ["source.java", "product.jar", "tests.jar", "requirements.yaml", "environments.yaml",
                     "decision.md", "profile.md", "capabilities/foundation-release.yaml",
                     "capabilities/capability-matrix.yaml", "capabilities/facade-surface.yaml"]
            for name in files:
                (root / name).write_text(name)
            before = module.capture_build_inputs(root, contract)
            receipt = root / "build-inputs.json"
            (root / "profile.md").write_text("changed during compilation")
            with self.assertRaisesRegex(ValueError, "during build"):
                module.record_staged_build(root, contract, before, [root / "tests.jar"], receipt)
            self.assertFalse(receipt.exists())
            (root / "profile.md").write_text("profile.md")
            module.record_staged_build(root, contract, before, [root / "tests.jar"], receipt)
            module.require_staged_build(root, contract, receipt)

    def test_runtime_classpath_uses_only_declared_products_and_recorded_harness(self):
        module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root / "artifacts").mkdir()
            (root / "harness").mkdir()
            required = ["artifacts/pdf-document-0.1.0.jar", "artifacts/pdf-document-0.1.0-sources.jar",
                        "artifacts/pdf-document-0.1.0-javadoc.jar", "artifacts/pdf-migration-itext7-preview-0.1.0.jar"]
            for name in required + ["artifacts/stale-product.jar", "harness/tests.jar", "harness/stale-library.jar", "harness/comparator.so"]:
                (root / name).write_text(name)
            receipt = {"harness": [module.reference(root, root / name)
                                   for name in ("harness/tests.jar", "harness/comparator.so")]}
            observed = module.certification_classpath(root, {"required-artifacts": required}, receipt)
            self.assertEqual([root / "artifacts/pdf-document-0.1.0.jar", root / "harness/tests.jar"], observed)

    def test_identity_probe_restores_the_prior_evidence_index_on_success_and_failure(self):
        module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root / "scripts").mkdir()
            checker = root / "scripts/inventory"
            checker.write_text("#!/bin/sh\nprintf 'Candidate identity: " + "a" * 64
                               + "\\nContract identity: " + "b" * 64 + "\\n'\nexit 1\n")
            checker.chmod(0o755)
            authority = root / "evidence.yaml"
            previous = b"# preserve exact prior evidence\ncertifications: []\n"
            authority.write_bytes(previous)
            observed = module.candidate_identities(root, authority, {"candidate": {}}, root / "probe.txt")
            self.assertEqual({"Candidate": "a" * 64, "Contract": "b" * 64}, observed)
            self.assertEqual(previous, authority.read_bytes())
            checker.write_text("#!/bin/sh\necho failed\nexit 2\n")
            with self.assertRaisesRegex(ValueError, "identity"):
                module.candidate_identities(root, authority, {"candidate": {}}, root / "probe.txt")
            self.assertEqual(previous, authority.read_bytes())


if __name__ == "__main__":
    unittest.main()
