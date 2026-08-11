package net.zerocloud.pdf.acceptance;

import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.PublicationStatus;

/** Detached findings from the project-owned semantic chain. */
public final class SemanticObservation {

    private final PublicationStatus publicationStatus;
    private final boolean reopened;
    private final int pageCount;
    private final DocumentFailureCode reopenFailure;
    private final EvidenceResult result;

    private SemanticObservation(
            PublicationStatus publicationStatus,
            boolean reopened,
            int pageCount,
            DocumentFailureCode reopenFailure) {
        this.publicationStatus = publicationStatus;
        this.reopened = reopened;
        this.pageCount = pageCount;
        this.reopenFailure = reopenFailure;
        this.result = reopened
                && publicationStatus == PublicationStatus.COMMITTED
                && pageCount == 1
                        ? EvidenceResult.PASS : EvidenceResult.FAIL;
    }

    static SemanticObservation reopened(
            PublicationStatus publicationStatus,
            int pageCount) {
        return new SemanticObservation(publicationStatus, true, pageCount, null);
    }

    static SemanticObservation reopenFailed(
            PublicationStatus publicationStatus,
            DocumentFailureCode failure) {
        return new SemanticObservation(publicationStatus, false, -1, failure);
    }

    /**
     * Returns the semantic chain result.
     *
     * @return pass or fail
     */
    public EvidenceResult result() {
        return result;
    }

    /**
     * Returns the ordinal page sequence actually observed after reopen.
     *
     * @return bracketed one-based page indexes, or {@code unavailable}
     */
    public String pageSequence() {
        if (!reopened) {
            return "unavailable";
        }
        StringBuilder sequence = new StringBuilder("[");
        for (int index = 1; index <= pageCount; index++) {
            if (index > 1) {
                sequence.append(", ");
            }
            sequence.append(index);
        }
        return sequence.append(']').toString();
    }

    /**
     * Returns the concise finding embedded in the semantic chain record.
     *
     * @return truthful profile finding
     */
    public String recordFinding() {
        if (!reopened) {
            return "The public workflow could not reopen the artifact; observed failure `"
                    + reopenFailure + "`.";
        }
        if (result == EvidenceResult.PASS) {
            return "The public workflow committed the artifact and reopened the observed "
                    + "one-page sequence `[1]`.";
        }
        return "Expected `COMMITTED` and one reopened page; observed `"
                + publicationStatus + "` and `" + pageCount
                + "` reopened pages with sequence `" + pageSequence() + "`.";
    }

    /**
     * Renders the raw semantic findings artifact.
     *
     * @param inputHash SHA-256 of the shared input PDF
     * @param producerVersion project Release Train
     * @return Markdown findings
     */
    public String findings(String inputHash, String producerVersion) {
        String pageCountValue = reopened ? Integer.toString(pageCount) : "unavailable";
        String objectGraph = reopened
                ? "reopened through DocumentWorkflow"
                : "reopen failed with " + reopenFailure;
        return "# Project semantic findings\n\n"
                + "Input SHA-256: `" + inputHash + "`\n\n"
                + "Producer: `open-pdf-semantic-assertions`\n\n"
                + "Producer version: `" + producerVersion + "`\n\n"
                + "Publication status: `" + publicationStatus + "`\n\n"
                + "Object graph observation: `" + objectGraph + "`\n\n"
                + "Reopened page count: `" + pageCountValue + "`\n\n"
                + "Observed page sequence: `" + pageSequence() + "`\n\n"
                + "Text order: not applicable; the blank-document profile emits no text.\n";
    }
}
