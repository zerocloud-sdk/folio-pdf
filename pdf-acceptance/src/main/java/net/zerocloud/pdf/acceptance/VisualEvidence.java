package net.zerocloud.pdf.acceptance;

/** Complete detached output of the T07 visual evidence chain. */
final class VisualEvidence {

    private final EvidenceResult result;
    private final String record;
    private final String rawFindings;

    VisualEvidence(
            EvidenceResult result,
            String record,
            String rawFindings) {
        this.result = result;
        this.record = record;
        this.rawFindings = rawFindings;
    }

    EvidenceResult result() {
        return result;
    }

    String record() {
        return record;
    }

    String rawFindings() {
        return rawFindings;
    }
}
