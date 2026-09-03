package net.zerocloud.pdf.acceptance;

import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.PublicationStatus;

/** Detached findings from the project-owned T19 semantic chain. */
final class T19FontSemanticObservation {

    private final PublicationStatus publicationStatus;
    private final boolean capabilityReported;
    private final boolean reopened;
    private final T19FontSemanticChecks checks;
    private final DocumentFailureCode reopenFailure;
    private final EvidenceResult result;

    private T19FontSemanticObservation(
            PublicationStatus publicationStatus,
            boolean capabilityReported,
            boolean reopened,
            T19FontSemanticChecks checks,
            DocumentFailureCode reopenFailure) {
        this.publicationStatus = publicationStatus;
        this.capabilityReported = capabilityReported;
        this.reopened = reopened;
        this.checks = checks;
        this.reopenFailure = reopenFailure;
        this.result = publicationStatus == PublicationStatus.COMMITTED
                && capabilityReported
                && reopened
                && checks.allPass()
                        ? EvidenceResult.PASS : EvidenceResult.FAIL;
    }

    static T19FontSemanticObservation observed(
            PublicationStatus publicationStatus,
            boolean capabilityReported,
            T19FontSemanticChecks checks) {
        return new T19FontSemanticObservation(
                publicationStatus,
                capabilityReported,
                true,
                checks,
                null);
    }

    static T19FontSemanticObservation reopenFailed(
            PublicationStatus publicationStatus,
            boolean capabilityReported,
            DocumentFailureCode failure) {
        return new T19FontSemanticObservation(
                publicationStatus,
                capabilityReported,
                false,
                T19FontSemanticChecks.notObserved(),
                failure);
    }

    EvidenceResult result() {
        return result;
    }

    String recordFinding() {
        if (!reopened) {
            return "The public workflow could not reopen the T19 artifact; observed failure `"
                    + reopenFailure + "`.";
        }
        if (result == EvidenceResult.PASS) {
            return "The public workflow reported T19 and reopened the exact embedded subsets, explicit Unicode mappings, source metrics, ordered fallback, and resource reuse.";
        }
        return "One or more required T19 public observations did not match the project-owned font declarations.";
    }

    String findings(String inputHash, String producerVersion) {
        return "# T19 project semantic findings\n\n"
                + "Input ID-neutral SHA-256: `" + inputHash + "`\n\n"
                + "Input hash policy: `" + EvidenceFiles.inputHashPolicy()
                + "`\n\n"
                + "Producer: `folio-pdf-t19-semantic-assertions`\n\n"
                + "Producer version: `" + producerVersion + "`\n\n"
                + "Publication status: `" + publicationStatus + "`\n\n"
                + "T19 capability reported: `" + capabilityReported + "`\n\n"
                + "Public reopen completed: `" + reopened + "`\n\n"
                + "Two embedded Type 0 Font resources: `"
                + checks.fontResources()
                + "`\n\n"
                + "Explicit A, omega, and B ToUnicode mappings: `"
                + checks.unicodeMappings() + "`\n\n"
                + "Primary/fallback source advances: `"
                + checks.sourceMetrics()
                + "`\n\n"
                + "Embedded sfnt subsets exclude unrelated glyphs: `"
                + checks.subsetPrograms() + "`\n\n"
                + "Repeated primary use shares one resource: `"
                + checks.resourceReuse()
                + "`\n\n"
                + "All observations use public T13, T14, and bounded PDF Value inspection APIs; no backend object is an oracle.\n";
    }
}
