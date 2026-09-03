package net.zerocloud.pdf.acceptance;

import static net.zerocloud.pdf.acceptance.EvidenceFiles.fencedEnding;
import static net.zerocloud.pdf.acceptance.EvidenceFiles.finalFencedEnding;
import static net.zerocloud.pdf.acceptance.EvidenceFiles.metadata;
import static net.zerocloud.pdf.acceptance.EvidenceFiles.write;

import java.io.IOException;
import java.nio.file.Path;

/** Shared pinned-qpdf syntax evidence pipeline for product capabilities. */
final class QpdfSyntaxRecorder {

    private QpdfSyntaxRecorder() {
    }

    static EvidenceResult record(
            Path output,
            Path artifacts,
            QpdfPin qpdfPin,
            String inputHash,
            String releaseTrain,
            Profile profile) throws IOException {
        EvidenceResult result;
        String observedVersion;
        String finding;
        String findings;
        try {
            ProcessResult version = ExternalProcess.run(
                    qpdfPin.executable(), output, "--version");
            observedVersion = AcceptanceEvidenceCommand.qpdfVersion(
                    version.combinedOutput());
            if (version.exitCode != 0
                    || !qpdfPin.version().equals(observedVersion)) {
                result = EvidenceResult.INDETERMINATE;
                finding = "Expected pinned qpdf version `" + qpdfPin.version()
                        + "`; observed `" + observedVersion + "`.";
                findings = indeterminateFindings(
                        inputHash, observedVersion, finding, qpdfPin, profile);
            } else {
                ProcessResult check = ExternalProcess.run(
                        qpdfPin.executable(),
                        artifacts,
                        "--check",
                        profile.artifact);
                if (check.exitCode == 0) {
                    result = EvidenceResult.PASS;
                    finding = profile.successFinding(check.exitCode);
                } else if (check.exitCode == 2 || check.exitCode == 3) {
                    result = EvidenceResult.FAIL;
                    finding = profile.failureFinding(check.exitCode);
                } else {
                    result = EvidenceResult.INDETERMINATE;
                    finding = profile.undocumentedFinding(check.exitCode);
                }
                findings = findings(inputHash, check, result, qpdfPin, profile);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            result = EvidenceResult.INDETERMINATE;
            observedVersion = "unavailable";
            finding = "The pinned qpdf process was interrupted.";
            findings = indeterminateFindings(
                    inputHash, observedVersion, finding, qpdfPin, profile);
        } catch (IOException unavailable) {
            result = EvidenceResult.INDETERMINATE;
            observedVersion = "unavailable";
            finding = "The pinned qpdf tool was unavailable.";
            findings = indeterminateFindings(
                    inputHash, observedVersion, finding, qpdfPin, profile);
        }
        write(artifacts.resolve(profile.findings), findings);
        write(output.resolve(profile.record), syntaxRecord(
                inputHash,
                releaseTrain,
                observedVersion,
                result,
                finding,
                qpdfPin,
                profile));
        return result;
    }

    private static String syntaxRecord(
            String inputHash,
            String releaseTrain,
            String producerVersion,
            EvidenceResult result,
            String finding,
            QpdfPin qpdfPin,
            Profile profile) {
        if (profile.blankDocumentStyle) {
            return blankDocumentSyntaxRecord(
                    inputHash,
                    releaseTrain,
                    producerVersion,
                    result,
                    finding,
                    qpdfPin,
                    profile);
        }
        return "# " + profile.ticket + " qpdf syntax evidence\n\n"
                + metadata("Capability", profile.capability)
                + metadata("Acceptance Profile", profile.acceptanceProfile)
                + metadata("Profile record", profile.profileRecord)
                + metadata("Release train", releaseTrain)
                + metadata("Chain", "syntax")
                + metadata("Result", result.recordValue())
                + metadata("Producer kind", "external-tool")
                + metadata("Producer", "qpdf")
                + metadata("Producer version", producerVersion)
                + metadata("Tool distribution SHA-256",
                        qpdfPin.archiveSha256())
                + metadata("Input ID-neutral SHA-256", inputHash)
                + metadata("Input hash policy", EvidenceFiles.inputHashPolicy())
                + "Final determination: `" + result.recordValue() + "`\n\n"
                + "## Findings and artifact\n\n"
                + "- Product: [`artifacts/" + profile.artifact
                + "`](artifacts/" + profile.artifact + ")\n"
                + "- qpdf findings: [`artifacts/" + profile.findings
                + "`](artifacts/" + profile.findings + ")\n"
                + "- " + finding + "\n\n"
                + profile.scopeQualification + "\n";
    }

    private static String blankDocumentSyntaxRecord(
            String inputHash,
            String releaseTrain,
            String producerVersion,
            EvidenceResult result,
            String finding,
            QpdfPin qpdfPin,
            Profile profile) {
        return "# " + profile.ticket + " qpdf syntax evidence\n\n"
                + metadata("Capability", profile.capability)
                + metadata("Acceptance Profile", profile.acceptanceProfile)
                + metadata("Profile record", profile.profileRecord)
                + metadata("Release train", releaseTrain)
                + metadata("Chain", "syntax")
                + metadata("Result", result.recordValue())
                + metadata("Producer kind", "external-tool")
                + metadata("Producer", "qpdf")
                + metadata("Producer version", producerVersion)
                + metadata("Tool distribution SHA-256",
                        qpdfPin.archiveSha256())
                + metadata("Input ID-neutral SHA-256", inputHash)
                + metadata("Input hash policy", EvidenceFiles.inputHashPolicy())
                + "Final determination: `" + result.recordValue() + "`\n\n"
                + "## Findings and artifacts\n\n"
                + "- Input PDF: [`artifacts/" + profile.artifact
                + "`](artifacts/" + profile.artifact + ")\n"
                + "- qpdf findings: [`artifacts/" + profile.findings
                + "`](artifacts/" + profile.findings + ")\n"
                + "- " + finding + "\n";
    }

    private static String findings(
            String inputHash,
            ProcessResult check,
            EvidenceResult result,
            QpdfPin qpdfPin,
            Profile profile) {
        if (profile.blankDocumentStyle) {
            return blankDocumentFindings(
                    inputHash, check, qpdfPin, profile);
        }
        return "# " + profile.ticket + " qpdf syntax findings\n\n"
                + metadata("Input ID-neutral SHA-256", inputHash)
                + metadata("Input hash policy", EvidenceFiles.inputHashPolicy())
                + metadata("Tool", "qpdf")
                + metadata("Tool version", qpdfPin.version())
                + metadata("Distribution SHA-256", qpdfPin.archiveSha256())
                + "Final determination: `" + result.recordValue() + "`\n\n"
                + "## " + profile.artifact + "\n\n"
                + "Invocation: `qpdf --check " + profile.artifact + "`\n\n"
                + "`" + profile.artifact + "` exit code: `" + check.exitCode
                + "`\n\n### Standard output\n\n```text\n"
                + check.standardOutput + fencedEnding(check.standardOutput)
                + "### Standard error\n\n```text\n" + check.standardError
                + finalFencedEnding(check.standardError);
    }

    private static String blankDocumentFindings(
            String inputHash,
            ProcessResult check,
            QpdfPin qpdfPin,
            Profile profile) {
        return "# qpdf syntax findings\n\n"
                + metadata("Input ID-neutral SHA-256", inputHash)
                + metadata("Input hash policy", EvidenceFiles.inputHashPolicy())
                + metadata("Tool", "qpdf")
                + metadata("Tool version", qpdfPin.version())
                + metadata("Distribution SHA-256", qpdfPin.archiveSha256())
                + "Invocation: `qpdf --check " + profile.artifact + "`\n\n"
                + "Exit code: `" + check.exitCode + "`\n\n"
                + "## Standard output\n\n```text\n"
                + check.standardOutput + fencedEnding(check.standardOutput)
                + "## Standard error\n\n```text\n" + check.standardError
                + finalFencedEnding(check.standardError);
    }

    private static String indeterminateFindings(
            String inputHash,
            String observedVersion,
            String finding,
            QpdfPin qpdfPin,
            Profile profile) {
        String heading = profile.blankDocumentStyle
                ? "# qpdf syntax findings\n\n"
                : "# " + profile.ticket + " qpdf syntax findings\n\n";
        return heading
                + metadata("Input ID-neutral SHA-256", inputHash)
                + metadata("Input hash policy", EvidenceFiles.inputHashPolicy())
                + metadata("Tool", "qpdf")
                + metadata("Tool version", observedVersion)
                + metadata("Distribution SHA-256", qpdfPin.archiveSha256())
                + "Final determination: `indeterminate`\n\n" + finding + "\n";
    }

    /** Immutable names and authority metadata for one syntax chain. */
    static final class Profile {
        private final String ticket;
        private final String capability;
        private final String acceptanceProfile;
        private final String profileRecord;
        private final String artifact;
        private final String record;
        private final String findings;
        private final String scopeQualification;
        private final boolean blankDocumentStyle;

        Profile(
                String ticket,
                String capability,
                String acceptanceProfile,
                String profileRecord,
                String artifact,
                String record,
                String findings) {
            this(
                    ticket,
                    capability,
                    acceptanceProfile,
                    profileRecord,
                    artifact,
                    record,
                    findings,
                    "This syntax chain does not establish PDF standards conformance.",
                    false);
        }

        Profile(
                String ticket,
                String capability,
                String acceptanceProfile,
                String profileRecord,
                String artifact,
                String record,
                String findings,
                String scopeQualification) {
            this(
                    ticket,
                    capability,
                    acceptanceProfile,
                    profileRecord,
                    artifact,
                    record,
                    findings,
                    scopeQualification,
                    false);
        }

        private Profile(
                String ticket,
                String capability,
                String acceptanceProfile,
                String profileRecord,
                String artifact,
                String record,
                String findings,
                String scopeQualification,
                boolean blankDocumentStyle) {
            this.ticket = ticket;
            this.capability = capability;
            this.acceptanceProfile = acceptanceProfile;
            this.profileRecord = profileRecord;
            this.artifact = artifact;
            this.record = record;
            this.findings = findings;
            this.scopeQualification = scopeQualification;
            this.blankDocumentStyle = blankDocumentStyle;
        }

        static Profile blankDocument(
                String ticket,
                String capability,
                String acceptanceProfile,
                String profileRecord,
                String artifact,
                String record,
                String findings) {
            return new Profile(
                    ticket,
                    capability,
                    acceptanceProfile,
                    profileRecord,
                    artifact,
                    record,
                    findings,
                    "",
                    true);
        }

        String successFinding(int exitCode) {
            if (blankDocumentStyle) {
                return "qpdf completed `--check` with exit code `"
                        + exitCode + "`.";
            }
            return "qpdf completed `--check` for the " + ticket
                    + " product with exit code `" + exitCode + "`.";
        }

        String failureFinding(int exitCode) {
            if (blankDocumentStyle) {
                return exitCode == 3
                        ? "qpdf reported warnings (exit code `3`)."
                        : "qpdf reported errors (exit code `2`).";
            }
            return "qpdf reported warnings or errors for a "
                    + ticket + " product.";
        }

        String undocumentedFinding(int exitCode) {
            if (blankDocumentStyle) {
                return "qpdf did not return a documented inspection status "
                        + "(exit code `" + exitCode + "`).";
            }
            return "qpdf returned an undocumented status for a "
                    + ticket + " product.";
        }
    }
}
