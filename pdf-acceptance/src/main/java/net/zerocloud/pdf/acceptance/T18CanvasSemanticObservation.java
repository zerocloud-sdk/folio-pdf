package net.zerocloud.pdf.acceptance;

import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.PublicationStatus;

/** Detached findings from the project-owned T18 semantic chain. */
final class T18CanvasSemanticObservation {

    private final PublicationStatus publicationStatus;
    private final boolean capabilityReported;
    private final boolean reopened;
    private final boolean imageSemantics;
    private final boolean colorSemantics;
    private final boolean maskSemantics;
    private final boolean transparencySemantics;
    private final boolean resourceReuse;
    private final boolean preservation;
    private final DocumentFailureCode reopenFailure;
    private final EvidenceResult result;

    private T18CanvasSemanticObservation(
            PublicationStatus publicationStatus,
            boolean capabilityReported,
            boolean reopened,
            boolean imageSemantics,
            boolean colorSemantics,
            boolean maskSemantics,
            boolean transparencySemantics,
            boolean resourceReuse,
            boolean preservation,
            DocumentFailureCode reopenFailure) {
        this.publicationStatus = publicationStatus;
        this.capabilityReported = capabilityReported;
        this.reopened = reopened;
        this.imageSemantics = imageSemantics;
        this.colorSemantics = colorSemantics;
        this.maskSemantics = maskSemantics;
        this.transparencySemantics = transparencySemantics;
        this.resourceReuse = resourceReuse;
        this.preservation = preservation;
        this.reopenFailure = reopenFailure;
        this.result = publicationStatus == PublicationStatus.COMMITTED
                && capabilityReported
                && reopened
                && imageSemantics
                && colorSemantics
                && maskSemantics
                && transparencySemantics
                && resourceReuse
                && preservation
                        ? EvidenceResult.PASS : EvidenceResult.FAIL;
    }

    static T18CanvasSemanticObservation observed(
            PublicationStatus publicationStatus,
            boolean capabilityReported,
            boolean imageSemantics,
            boolean colorSemantics,
            boolean maskSemantics,
            boolean transparencySemantics,
            boolean resourceReuse,
            boolean preservation) {
        return new T18CanvasSemanticObservation(
                publicationStatus,
                capabilityReported,
                true,
                imageSemantics,
                colorSemantics,
                maskSemantics,
                transparencySemantics,
                resourceReuse,
                preservation,
                null);
    }

    static T18CanvasSemanticObservation reopenFailed(
            PublicationStatus publicationStatus,
            boolean capabilityReported,
            DocumentFailureCode failure) {
        return new T18CanvasSemanticObservation(
                publicationStatus,
                capabilityReported,
                false,
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
            return "The public workflow could not reopen the T18 artifact; observed failure `"
                    + reopenFailure + "`.";
        }
        if (result == EvidenceResult.PASS) {
            return "The public workflow reported T18 and reopened the exact project-owned per-format image, color/profile, mask/alpha, transparency-group, reuse, and preservation expectations.";
        }
        return "One or more required T18 public observations did not match the project-owned Canvas Program.";
    }

    String findings(String inputHash, String producerVersion) {
        return "# T18 project semantic findings\n\n"
                + "Input ID-neutral SHA-256: `" + inputHash + "`\n\n"
                + "Input hash policy: `" + EvidenceFiles.inputHashPolicy()
                + "`\n\n"
                + "Producer: `folio-pdf-t18-semantic-assertions`\n\n"
                + "Producer version: `" + producerVersion + "`\n\n"
                + "Publication status: `" + publicationStatus + "`\n\n"
                + "T18 capability reported: `" + capabilityReported + "`\n\n"
                + "Public reopen completed: `" + reopened + "`\n\n"
                + "Exact JPEG dimensions, filter, color, and mask absence; exact PNG, TIFF, raw, and existing-image dimensions, filters, color, alpha, and samples: `"
                + imageSemantics + "`\n\n"
                + "Exact device/calibrated values and ICCBased digest/object identity: `"
                + colorSemantics + "`\n\n"
                + "Exact explicit/soft-mask relationships and sample bytes: `" + maskSemantics
                + "`\n\n"
                + "Exact alpha, Multiply blend mode, and transparency-group semantics: `"
                + transparencySemantics + "`\n\n"
                + "Repeated image, state, and group resource reuse: `"
                + resourceReuse + "`\n\n"
                + "Existing content and resource preservation: `"
                + preservation + "`\n\n"
                + "Expected values come from project-owned Canvas declarations; public resource and object observations are compared without relying on serialized byte order.\n";
    }
}
