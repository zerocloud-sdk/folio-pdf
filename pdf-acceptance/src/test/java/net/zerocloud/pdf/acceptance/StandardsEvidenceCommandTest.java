package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests the repository-only standards recording command, not product internals. */
public final class StandardsEvidenceCommandTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void aDeclaredSourcePatchMustMatchTheRecordedToolOrigin() throws Exception {
        Path root = temporary.getRoot().toPath();
        String[] arguments = qualifiedArguments(root, "");
        Path pin = java.nio.file.Paths.get(arguments[1]);
        Path patch = write(root.resolve("source.patch"), "changed source patch\n");
        write(pin, read(pin) + "patch=" + patch + "\npatch-sha256=" + repeatZeroHash() + "\n");

        StandardsEvidenceCommand.main(arguments);

        Path output = java.nio.file.Paths.get(arguments[0]);
        assertTrue(read(output.resolve("standards.properties")).contains("result=indeterminate"));
        assertTrue(read(output.resolve("findings.txt")).contains("patch identity"));
    }

    @Test
    public void missingCheckerRetainsIndeterminateEvidence() throws Exception {
        Path root = temporary.getRoot().toPath();
        Path input = write(root.resolve("input.pdf"), "%PDF-1.7\n%%EOF\n");
        Path pin = write(root.resolve("tool.properties"),
                "tool=pdfcpu\nversion=0.15.0\nexecutable=missing-pdfcpu\n"
                        + "sha256=0000000000000000000000000000000000000000000000000000000000000000\n"
                        + "timeout-ms=1000\nmax-output-bytes=4096\n");
        Path profile = write(root.resolve("profile.properties"),
                "profile=T03-document-workflow-transaction\nrequired-rules=catalog-type\n");
        Path output = root.resolve("evidence");

        StandardsEvidenceCommand.main(new String[] {
                output.toString(), pin.toString(), profile.toString(), input.toString()});

        String record = read(output.resolve("standards.properties"));
        assertTrue(record, record.contains("result=indeterminate"));
        assertTrue(record, record.contains("input-sha256=" + EvidenceFiles.sha256(input)));
        assertTrue(read(output.resolve("findings.txt")).contains("unavailable"));
    }

    @Test
    public void aPinnedStrictOfflineCheckerReportsInvalidOutputAsFail() throws Exception {
        Path root = temporary.getRoot().toPath();
        Path checker = write(root.resolve("pdfcpu"), "#!/bin/sh\n"
                + "if [ \"$1\" = version ]; then echo 'version: 0.15.0'; exit 0; fi\n"
                + "[ \"$1 $2 $3 $4 $5 $6\" = 'validate --mode strict --conf disable --offline' ] || exit 9\n"
                + "echo 'validate invalid.pdf: validation error (obj#:1) (try --mode=relaxed): rootDict Type must be Catalog' >&2\nexit 1\n");
        assertTrue(checker.toFile().setExecutable(true));
        Path output = record(root, checker, "invalid");

        String record = read(output.resolve("standards.properties"));
        assertTrue(record, record.contains("result=fail"));
        assertTrue(record, record.contains("executable-sha256=" + EvidenceFiles.sha256(checker)));
        assertTrue(read(output.resolve("findings.txt")).contains("rootDict Type must be Catalog"));
    }

    @Test(timeout = 8000)
    public void checkerTimeLimitProducesAnIndeterminateRecord() throws Exception {
        Path root = temporary.getRoot().toPath();
        Path checker = write(root.resolve("stalling-pdfcpu"),
                "#!/bin/sh\nexec sleep 30\n");
        assertTrue(checker.toFile().setExecutable(true));

        Path output = record(root, checker, "timeout");

        assertTrue(read(output.resolve("standards.properties")).contains("result=indeterminate"));
        assertTrue(read(output.resolve("findings.txt")).contains("time limit"));
    }

    @Test(timeout = 8000)
    public void checkerOutputLimitCannotBecomeAValidationResult() throws Exception {
        Path root = temporary.getRoot().toPath();
        Path checker = write(root.resolve("noisy-pdfcpu"), "#!/bin/sh\n"
                + "if [ \"$1\" = version ]; then echo 'version: 0.15.0'; exit 0; fi\n"
                + "i=0; while [ $i -lt 1000 ]; do echo 'raw checker output'; i=$((i+1)); done\n"
                + "echo 'validation error: rootDict Type must be Catalog' >&2\nexit 1\n");
        assertTrue(checker.toFile().setExecutable(true));

        Path output = record(root, checker, "output-limit");

        assertTrue(read(output.resolve("standards.properties")).contains("result=indeterminate"));
        String findings = read(output.resolve("findings.txt"));
        assertTrue(findings.contains("output limit"));
        assertTrue(findings.contains("raw checker output"));
        assertTrue(findings.length() < 5000);
    }

    @Test
    public void passingEvidenceRequiresEveryDeclaredRuleAndItsObservedNegativeControl()
            throws Exception {
        Path root = temporary.getRoot().toPath();
        Path output = recordQualified(root, "");

        String record = read(output.resolve("standards.properties"));
        assertTrue(record, record.contains("result=pass"));
        assertTrue(record, record.contains("covered-rules=catalog-type"));
        assertTrue(read(output.resolve("negative-catalog-type.txt"))
                .contains("entry=Type invalid dict entry"));
        assertTrue(read(output.resolve("findings.txt")).contains(
                EvidenceFiles.sha256(root.resolve("control-catalog-type.pdf"))));
    }

    @Test
    public void anInputChangedDuringValidationCannotBeCertified() throws Exception {
        Path output = recordQualified(temporary.getRoot().toPath(), "echo changed >> \"$7\"\n");

        String record = read(output.resolve("standards.properties"));
        assertTrue(record, record.contains("result=indeterminate"));
        assertTrue(read(output.resolve("findings.txt")).contains("Input changed"));
    }

    @Test
    public void anInputRemovedDuringValidationCannotLeaveAPassingRecord() throws Exception {
        Path output = recordQualified(temporary.getRoot().toPath(), "rm \"$7\"\n");

        String record = read(output.resolve("standards.properties"));
        assertTrue(record, record.contains("result=indeterminate"));
    }

    @Test
    public void aCheckerChangedDuringValidationCannotBeCertified() throws Exception {
        Path output = recordQualified(temporary.getRoot().toPath(),
                "sed -i 's/0.15.0/0.15.1/g' \"$0\"\n");

        String record = read(output.resolve("standards.properties"));
        assertTrue(record, record.contains("result=indeterminate"));
        assertTrue(read(output.resolve("findings.txt")).contains("identity changed"));
    }

    private static Path recordQualified(Path root, String duringValidation) throws Exception {
        String[] arguments = qualifiedArguments(root, duringValidation);
        StandardsEvidenceCommand.main(arguments);
        return java.nio.file.Paths.get(arguments[0]);
    }

    private static String[] qualifiedArguments(Path root, String duringValidation) throws Exception {
        Path checker = write(root.resolve("qualified-pdfcpu"), "#!/bin/sh\n"
                + "if [ \"$1\" = version ]; then echo 'version: 0.15.0'; exit 0; fi\n"
                + "case \"${7##*/}\" in control-*) echo 'validation error: entry=Type invalid dict entry' >&2; exit 1;; esac\n"
                + duringValidation
                + "echo \"validating(mode=strict) $7 ...\" >&2\necho 'validation ok' >&2\n");
        assertTrue(checker.toFile().setExecutable(true));
        Path pin = toolPin(root, checker, "qualified");
        Path negative = write(root.resolve("control-catalog-type.pdf"),
                "%PDF-1.7\n1 0 obj\n<< /Type /Bogus >>\nendobj\n%%EOF\n");
        Path profile = write(root.resolve("qualified-profile.properties"),
                "profile=T03-document-workflow-transaction\n"
                        + "required-rules=catalog-type\ncovered-rules=catalog-type\n"
                        + "negative.catalog-type.path=" + negative + "\n"
                        + "negative.catalog-type.sha256=" + EvidenceFiles.sha256(negative) + "\n"
                        + "negative.catalog-type.finding=entry=Type invalid dict entry\n");
        Path input = write(root.resolve("valid.pdf"), "%PDF-1.7\n%%EOF\n");
        Path output = root.resolve("qualified-evidence");

        return new String[] {output.toString(), pin.toString(), profile.toString(), input.toString()};
    }

    @Test
    public void unavailableIdentitiesCoverageAndControlsNeverProducePass() throws Exception {
        String[] scenarios = {"executable", "version", "rule", "control", "control-hash",
            "accepted-control", "unknown-status", "empty-success"};
        for (String scenario : scenarios) {
            Path root = Files.createDirectory(temporary.getRoot().toPath().resolve(scenario));
            String[] arguments = qualifiedArguments(root, "");
            Path pin = java.nio.file.Paths.get(arguments[1]);
            Path profile = java.nio.file.Paths.get(arguments[2]);
            Path checker = root.resolve("qualified-pdfcpu");
            if ("executable".equals(scenario)) {
                write(pin, read(pin).replace(EvidenceFiles.sha256(checker), repeatZeroHash()));
            } else if ("version".equals(scenario)) {
                write(pin, read(pin).replace("version=0.15.0", "version=0.15.1"));
            } else if ("rule".equals(scenario)) {
                write(profile, read(profile).replace("required-rules=catalog-type",
                        "required-rules=catalog-type,uncovered-rule"));
            } else if ("control".equals(scenario)) {
                Files.delete(root.resolve("control-catalog-type.pdf"));
            } else if ("control-hash".equals(scenario)) {
                write(profile, read(profile).replace(
                        EvidenceFiles.sha256(root.resolve("control-catalog-type.pdf")), repeatZeroHash()));
            } else {
                String script = read(checker);
                if ("accepted-control".equals(scenario)) {
                    script = script.replace("exit 1;;", "exit 0;;");
                } else {
                    script = script.replace("case \"${7##*/}\"", ("unknown-status".equals(scenario)
                            ? "exit 9\n" : "exit 0\n") + "case \"${7##*/}\"");
                }
                write(checker, script);
                toolPin(root, checker, "qualified");
            }

            StandardsEvidenceCommand.main(arguments);

            String record = read(java.nio.file.Paths.get(arguments[0]).resolve("standards.properties"));
            assertTrue(scenario + ": " + record, record.contains("result=indeterminate"));
        }
    }

    private static String repeatZeroHash() {
        return "0000000000000000000000000000000000000000000000000000000000000000";
    }

    @Test
    public void arlingtonErrorFindingsFailEvenWhenTheCheckerExitsZero() throws Exception {
        Path root = temporary.getRoot().toPath();
        Path checker = write(root.resolve("arlington"), "#!/bin/sh\n"
                + "if [ \"$1\" = --help ]; then echo 'Arlington PDF Model C++ P.o.C. version v0.81 built test'; exit 0; fi\n"
                + "echo 'Processing input to stdout BEGIN - TestGrammar v0.81'\n"
                + "echo 'Error: inheritable required key does not exist: Resources (PageObject) in PDF 1.7'\n"
                + "echo END\necho 'DONE - 1 files processed'\nexit 0\n");
        assertTrue(checker.toFile().setExecutable(true));
        Path model = Files.createDirectory(root.resolve("model"));
        Path modelFile = write(model.resolve("PageObject.tsv"), "test model\n");
        String modelHash = EvidenceFiles.sha256("PageObject.tsv "
                + EvidenceFiles.sha256(modelFile) + "\n");
        Path pin = write(root.resolve("arlington.properties"), "tool=arlington\nversion=0.81\n"
                + "executable=" + checker + "\nsha256=" + EvidenceFiles.sha256(checker)
                + "\nmodel=" + model + "\nmodel-sha256=" + modelHash
                + "\ntimeout-ms=1000\nmax-output-bytes=4096\n");
        Path profile = write(root.resolve("arlington-profile.properties"),
                "profile=T03-document-workflow-transaction\npdf-version=1.7\n"
                        + "required-rules=page-resources-required\n");
        Path input = write(root.resolve("missing-resources.pdf"), "%PDF-1.7\n%%EOF\n");
        Path output = root.resolve("arlington-evidence");

        StandardsEvidenceCommand.main(new String[] {
                output.toString(), pin.toString(), profile.toString(), input.toString()});

        String record = read(output.resolve("standards.properties"));
        assertTrue(record, record.contains("result=fail"));
        assertTrue(record, record.contains("model-sha256=" + modelHash));
        assertTrue(read(output.resolve("findings.txt")).contains("Resources (PageObject)"));
    }

    private static Path record(Path root, Path checker, String name) throws Exception {
        Path pin = toolPin(root, checker, name);
        Path profile = write(root.resolve(name + "-profile.properties"),
                "profile=T03-document-workflow-transaction\nrequired-rules=catalog-type\n");
        Path input = write(root.resolve(name + ".pdf"), "%PDF-1.7\n%%EOF\n");
        Path output = root.resolve(name + "-evidence");
        StandardsEvidenceCommand.main(new String[] {
                output.toString(), pin.toString(), profile.toString(), input.toString()});
        return output;
    }

    private static Path toolPin(Path root, Path checker, String name) throws Exception {
        return write(root.resolve(name + "-tool.properties"),
                "tool=pdfcpu\nversion=0.15.0\nexecutable=" + checker + "\nsha256="
                        + EvidenceFiles.sha256(checker)
                        + "\ntimeout-ms=1000\nmax-output-bytes=4096\n");
    }

    private static Path write(Path path, String content) throws Exception {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        return path;
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
