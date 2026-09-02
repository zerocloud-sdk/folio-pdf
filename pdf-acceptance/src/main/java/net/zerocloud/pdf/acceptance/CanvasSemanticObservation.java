package net.zerocloud.pdf.acceptance;

import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.PublicationStatus;

/** Detached findings from the project-owned T17 semantic chain. */
final class CanvasSemanticObservation {

    private final PublicationStatus publicationStatus;
    private final boolean capabilityReported;
    private final boolean reopened;
    private final boolean pathSemantics;
    private final boolean stateSemantics;
    private final boolean textSemantics;
    private final boolean resourceReuse;
    private final boolean preservation;
    private final DocumentFailureCode reopenFailure;
    private final EvidenceResult result;

    private CanvasSemanticObservation(
            PublicationStatus publicationStatus,
            boolean capabilityReported,
            boolean reopened,
            boolean pathSemantics,
            boolean stateSemantics,
            boolean textSemantics,
            boolean resourceReuse,
            boolean preservation,
            DocumentFailureCode reopenFailure) {
        this.publicationStatus = publicationStatus;
        this.capabilityReported = capabilityReported;
        this.reopened = reopened;
        this.pathSemantics = pathSemantics;
        this.stateSemantics = stateSemantics;
        this.textSemantics = textSemantics;
        this.resourceReuse = resourceReuse;
        this.preservation = preservation;
        this.reopenFailure = reopenFailure;
        this.result = publicationStatus == PublicationStatus.COMMITTED
                && capabilityReported
                && reopened
                && pathSemantics
                && stateSemantics
                && textSemantics
                && resourceReuse
                && preservation
                        ? EvidenceResult.PASS : EvidenceResult.FAIL;
    }

    static CanvasSemanticObservation observed(
            PublicationStatus publicationStatus,
            boolean capabilityReported,
            boolean pathSemantics,
            boolean stateSemantics,
            boolean textSemantics,
            boolean resourceReuse,
            boolean preservation) {
        return new CanvasSemanticObservation(
                publicationStatus,
                capabilityReported,
                true,
                pathSemantics,
                stateSemantics,
                textSemantics,
                resourceReuse,
                preservation,
                null);
    }

    static CanvasSemanticObservation reopenFailed(
            PublicationStatus publicationStatus,
            boolean capabilityReported,
            DocumentFailureCode failure) {
        return new CanvasSemanticObservation(
                publicationStatus,
                capabilityReported,
                false,
                false,
                false,
                false,
                false,
                false,
                failure);
    }

    EvidenceResult result() {
        return result;
    }

    String recordFinding() {
        if (!reopened) {
            return "The public workflow could not reopen the T17 artifact; observed failure `"
                    + reopenFailure + "`.";
        }
        if (result == EvidenceResult.PASS) {
            return "The public workflow reported T17 and reopened equivalent path, state, resource, and positioned-text semantics.";
        }
        return "One or more required T17 public observations did not match the project-owned Canvas Program.";
    }

    String findings(String inputHash, String producerVersion) {
        return "# T17 project semantic findings\n\n"
                + "Input ID-neutral SHA-256: `" + inputHash + "`\n\n"
                + "Input hash policy: `" + EvidenceFiles.inputHashPolicy()
                + "`\n\n"
                + "Producer: `folio-pdf-t17-semantic-assertions`\n\n"
                + "Producer version: `" + producerVersion + "`\n\n"
                + "Publication status: `" + publicationStatus + "`\n\n"
                + "T17 capability reported: `" + capabilityReported + "`\n\n"
                + "Public reopen completed: `" + reopened + "`\n\n"
                + "Line, cubic-curve, fill, and winding semantics: `"
                + pathSemantics + "`\n\n"
                + "Transform, clipping, and nested graphics-state semantics: `"
                + stateSemantics + "`\n\n"
                + "Explicit Font, glyph, text-matrix, rendering-mode, and geometry semantics: `"
                + textSemantics + "`\n\n"
                + "Repeated Font resource reuse: `" + resourceReuse + "`\n\n"
                + "Existing content and resource preservation: `"
                + preservation + "`\n\n"
                + "Expected values come from the project-owned Canvas Program; public observations, not PDFBox object identity or serialized byte order, are compared.\n";
    }
}
