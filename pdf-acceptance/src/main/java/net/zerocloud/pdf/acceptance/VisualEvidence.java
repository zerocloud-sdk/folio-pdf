package net.zerocloud.pdf.acceptance;

/** Complete detached output and retained artifacts of a visual evidence chain. */
final class VisualEvidence {

    private final EvidenceResult result;
    private final String record;
    private final String rawFindings;
    private final RetainedEvidence retainedFiles;

    VisualEvidence(
            EvidenceResult result,
            String record,
            String rawFindings,
            RetainedEvidence retainedFiles) {
        this.result = result;
        this.record = record;
        this.rawFindings = rawFindings;
        this.retainedFiles = retainedFiles;
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

    RetainedEvidence retainedFiles() {
        return retainedFiles;
    }
}
