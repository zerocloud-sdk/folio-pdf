package net.zerocloud.pdf.acceptance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;

/**
 * Repository-only command that records the T06 syntax and semantic evidence.
 */
public final class AcceptanceEvidenceCommand {

    private static final String CAPABILITY = "document.blank.create-publish-reopen";
    private static final String ACCEPTANCE_PROFILE = "T03-document-workflow-transaction";
    private static final String PROFILE_RECORD =
            "capabilities/evidence/T03-document-workflow-transaction.md";
    private static final String ARTIFACT_NAME = "T06-document-blank-output.pdf";
    private static final String QPDF_FINDINGS_NAME = "T06-document-blank-qpdf.txt";
    private static final String SEMANTIC_FINDINGS_NAME = "T06-document-blank-semantic.txt";

    private AcceptanceEvidenceCommand() {
    }

    /**
     * Runs the built-in T06 Acceptance Profile.
     *
     * @param arguments output directory, qpdf executable, and Release Train
     * @throws Exception if the evidence run cannot be completed
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 4) {
            throw new IllegalArgumentException(
                    "Usage: AcceptanceEvidenceCommand <output-directory> "
                            + "<qpdf-executable> <qpdf-pin> <release-train>");
        }

        Path output = Paths.get(arguments[0]).toAbsolutePath().normalize();
        Path qpdf = Paths.get(arguments[1]).toAbsolutePath().normalize();
        QpdfPin qpdfPin = QpdfPin.load(
                Paths.get(arguments[2]).toAbsolutePath().normalize());
        String releaseTrain = arguments[3];
        Path artifacts = output.resolve("artifacts");
        Files.createDirectories(artifacts);
        Path pdf = artifacts.resolve(ARTIFACT_NAME);

        WorkflowOutcome<Void> creation = new DocumentWorkflow().execute(
                WorkflowRequest.create(pdf, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });
        String inputHash = sha256(pdf);

        EvidenceResult syntaxResult;
        String observedVersion;
        String syntaxFinding;
        try {
            ProcessResult version = run(qpdf, output, "--version");
            observedVersion = qpdfVersion(version.combinedOutput());
            if (version.exitCode != 0 || !qpdfPin.version().equals(observedVersion)) {
                syntaxResult = EvidenceResult.INDETERMINATE;
                syntaxFinding = "Expected pinned qpdf version `" + qpdfPin.version()
                        + "`; observed `" + observedVersion + "`.";
                write(artifacts.resolve(QPDF_FINDINGS_NAME),
                        unpinnedQpdfFindings(inputHash, observedVersion, qpdfPin));
            } else {
                ProcessResult syntax = run(qpdf, artifacts, "--check", ARTIFACT_NAME);
                syntaxResult = syntax.exitCode == 0
                        ? EvidenceResult.PASS
                        : syntax.exitCode == 2 || syntax.exitCode == 3
                                ? EvidenceResult.FAIL : EvidenceResult.INDETERMINATE;
                if (syntax.exitCode == 3) {
                    syntaxFinding = "qpdf reported warnings (exit code `3`).";
                } else if (syntax.exitCode == 2) {
                    syntaxFinding = "qpdf reported errors (exit code `2`).";
                } else if (syntax.exitCode != 0) {
                    syntaxFinding = "qpdf did not return a documented inspection status "
                            + "(exit code `" + syntax.exitCode + "`).";
                } else {
                    syntaxFinding = "qpdf completed `--check` with exit code `"
                            + syntax.exitCode + "`.";
                }
                write(artifacts.resolve(QPDF_FINDINGS_NAME),
                        qpdfFindings(syntax, inputHash, qpdfPin));
            }
        } catch (IOException unavailable) {
            syntaxResult = EvidenceResult.INDETERMINATE;
            observedVersion = "unavailable";
            syntaxFinding = "The pinned qpdf tool was unavailable.";
            write(artifacts.resolve(QPDF_FINDINGS_NAME),
                    unavailableQpdfFindings(inputHash, qpdfPin));
        }

        SemanticObservation semantic = SemanticAssertions.inspect(creation, pdf);
        EvidenceResult profileDetermination = syntaxResult == EvidenceResult.FAIL
                || semantic.result() == EvidenceResult.FAIL
                        ? EvidenceResult.FAIL : EvidenceResult.INDETERMINATE;
        write(artifacts.resolve(SEMANTIC_FINDINGS_NAME),
                semantic.findings(inputHash, releaseTrain));

        write(output.resolve("T06-document-blank-syntax.md"),
                syntaxRecord(inputHash, releaseTrain, observedVersion,
                        syntaxResult, syntaxFinding, qpdfPin));
        write(output.resolve("T06-document-blank-semantic.md"),
                semanticRecord(inputHash, releaseTrain, semantic));
        write(output.resolve("T06-document-blank-determination.md"),
                determinationRecord(inputHash, releaseTrain, syntaxResult,
                        semantic.result(), profileDetermination));

        System.out.println("Acceptance Profile determination: "
                + profileDetermination.recordValue());
    }

    private static String syntaxRecord(
            String inputHash,
            String releaseTrain,
            String producerVersion,
            EvidenceResult result,
            String finding,
            QpdfPin qpdfPin) {
        return "# T06 qpdf syntax evidence\n\n"
                + metadata("Capability", CAPABILITY)
                + metadata("Acceptance Profile", ACCEPTANCE_PROFILE)
                + metadata("Profile record", PROFILE_RECORD)
                + metadata("Release train", releaseTrain)
                + metadata("Chain", "syntax")
                + metadata("Result", result.recordValue())
                + metadata("Producer kind", "external-tool")
                + metadata("Producer", "qpdf")
                + metadata("Producer version", producerVersion)
                + metadata("Tool distribution SHA-256", qpdfPin.archiveSha256())
                + metadata("Input SHA-256", inputHash)
                + "Final determination: `" + result.recordValue() + "`\n\n"
                + "## Findings and artifacts\n\n"
                + "- Input PDF: [`artifacts/" + ARTIFACT_NAME + "`](artifacts/"
                + ARTIFACT_NAME + ")\n"
                + "- qpdf findings: [`artifacts/" + QPDF_FINDINGS_NAME + "`](artifacts/"
                + QPDF_FINDINGS_NAME + ")\n"
                + "- " + finding + "\n";
    }

    private static String semanticRecord(
            String inputHash,
            String releaseTrain,
            SemanticObservation observation) {
        EvidenceResult result = observation.result();
        return "# T06 project semantic evidence\n\n"
                + metadata("Capability", CAPABILITY)
                + metadata("Acceptance Profile", ACCEPTANCE_PROFILE)
                + metadata("Profile record", PROFILE_RECORD)
                + metadata("Release train", releaseTrain)
                + metadata("Chain", "semantic")
                + metadata("Result", result.recordValue())
                + metadata("Producer kind", "project-test")
                + metadata("Producer", "folio-pdf-semantic-assertions")
                + metadata("Producer version", releaseTrain)
                + metadata("Input SHA-256", inputHash)
                + "Final determination: `" + result.recordValue() + "`\n\n"
                + "## Findings and artifacts\n\n"
                + "- Input PDF: [`artifacts/" + ARTIFACT_NAME + "`](artifacts/"
                + ARTIFACT_NAME + ")\n"
                + "- Semantic findings: [`artifacts/" + SEMANTIC_FINDINGS_NAME
                + "`](artifacts/" + SEMANTIC_FINDINGS_NAME + ")\n"
                + "- " + observation.recordFinding() + "\n";
    }

    private static String determinationRecord(
            String inputHash,
            String releaseTrain,
            EvidenceResult syntaxResult,
            EvidenceResult semanticResult,
            EvidenceResult profileDetermination) {
        return "# T06 Acceptance Profile determination\n\n"
                + metadata("Capability", CAPABILITY)
                + metadata("Acceptance Profile", ACCEPTANCE_PROFILE)
                + metadata("Profile record", PROFILE_RECORD)
                + metadata("Release train", releaseTrain)
                + metadata("Input SHA-256", inputHash)
                + "Final determination: `" + profileDetermination.recordValue() + "`\n\n"
                + passingChains(syntaxResult, semanticResult)
                + (syntaxResult == EvidenceResult.FAIL
                        ? "Failing mandatory chains: `syntax`\n\n" : "")
                + (semanticResult == EvidenceResult.FAIL
                        ? "Failing mandatory chains: `semantic`\n\n" : "")
                + (syntaxResult == EvidenceResult.INDETERMINATE
                        ? "Indeterminate mandatory chains: `syntax`\n\n" : "")
                + "Missing mandatory chains: `standards`, `visual`\n\n"
                + "The capability remains `experimental`; qpdf syntax evidence is not "
                + "a standards-compliance claim.\n";
    }

    private static String passingChains(
            EvidenceResult syntaxResult,
            EvidenceResult semanticResult) {
        if (syntaxResult == EvidenceResult.PASS && semanticResult == EvidenceResult.PASS) {
            return "Passing chains: `syntax`, `semantic`\n\n";
        }
        if (syntaxResult == EvidenceResult.PASS) {
            return "Passing chains: `syntax`\n\n";
        }
        if (semanticResult == EvidenceResult.PASS) {
            return "Passing chains: `semantic`\n\n";
        }
        return "Passing chains: none\n\n";
    }

    private static String qpdfFindings(
            ProcessResult result,
            String inputHash,
            QpdfPin qpdfPin) {
        return "# qpdf syntax findings\n\n"
                + metadata("Input SHA-256", inputHash)
                + metadata("Tool", "qpdf")
                + metadata("Tool version", qpdfPin.version())
                + metadata("Distribution SHA-256", qpdfPin.archiveSha256())
                + "Invocation: `qpdf --check " + ARTIFACT_NAME + "`\n\n"
                + "Exit code: `" + result.exitCode + "`\n\n"
                + "## Standard output\n\n```text\n"
                + result.standardOutput + fencedEnding(result.standardOutput)
                + "## Standard error\n\n```text\n"
                + result.standardError + finalFencedEnding(result.standardError);
    }

    private static String unavailableQpdfFindings(String inputHash, QpdfPin qpdfPin) {
        return "# qpdf syntax findings\n\n"
                + metadata("Input SHA-256", inputHash)
                + metadata("Tool", "qpdf")
                + metadata("Tool version", "unavailable")
                + metadata("Distribution SHA-256", qpdfPin.archiveSha256())
                + "Final determination: `indeterminate`\n\n"
                + "The pinned qpdf tool was unavailable.\n";
    }

    private static String unpinnedQpdfFindings(
            String inputHash,
            String observedVersion,
            QpdfPin qpdfPin) {
        return "# qpdf syntax findings\n\n"
                + metadata("Input SHA-256", inputHash)
                + metadata("Tool", "qpdf")
                + metadata("Tool version", observedVersion)
                + metadata("Distribution SHA-256", qpdfPin.archiveSha256())
                + "Final determination: `indeterminate`\n\n"
                + "Expected pinned qpdf version `" + qpdfPin.version()
                + "`; observed `" + observedVersion + "`.\n";
    }

    private static String fencedEnding(String value) {
        return value.endsWith("\n") ? "```\n\n" : "\n```\n\n";
    }

    private static String finalFencedEnding(String value) {
        return value.endsWith("\n") ? "```\n" : "\n```\n";
    }

    private static String metadata(String label, String value) {
        return label + ": `" + value + "`\n\n";
    }

    private static String qpdfVersion(String output) {
        String prefix = "qpdf version ";
        for (String line : output.split("\\r?\\n")) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return "unavailable";
    }

    private static ProcessResult run(Path executable, Path directory, String... arguments)
            throws IOException, InterruptedException {
        String[] command = new String[arguments.length + 1];
        command[0] = executable.toString();
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .start();
        StreamCapture standardOutput = new StreamCapture(process.getInputStream());
        StreamCapture standardError = new StreamCapture(process.getErrorStream());
        Thread outputThread = new Thread(standardOutput, "qpdf-standard-output");
        Thread errorThread = new Thread(standardError, "qpdf-standard-error");
        outputThread.start();
        errorThread.start();
        int exitCode = process.waitFor();
        outputThread.join();
        errorThread.join();
        return new ProcessResult(exitCode, standardOutput.value(), standardError.value());
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }

    private static void write(Path path, String value) throws IOException {
        Files.write(path,
                value.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private static final class StreamCapture implements Runnable {
        private final InputStream input;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private IOException failure;

        StreamCapture(InputStream input) {
            this.input = input;
        }

        @Override
        public void run() {
            try (InputStream stream = input) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) >= 0) {
                    output.write(buffer, 0, count);
                }
            } catch (IOException captureFailure) {
                failure = captureFailure;
            }
        }

        String value() throws IOException {
            if (failure != null) {
                throw failure;
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static final class ProcessResult {
        private final int exitCode;
        private final String standardOutput;
        private final String standardError;

        ProcessResult(int exitCode, String standardOutput, String standardError) {
            this.exitCode = exitCode;
            this.standardOutput = standardOutput;
            this.standardError = standardError;
        }

        String combinedOutput() {
            return standardOutput + "\n" + standardError;
        }
    }
}
