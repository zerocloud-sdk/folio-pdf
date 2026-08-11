package net.zerocloud.pdf.acceptance;

/** Closed result vocabulary shared by Acceptance Evidence chains. */
public enum EvidenceResult {
    /** The chain's assertions passed. */
    PASS("pass"),
    /** The chain observed a definite failure. */
    FAIL("fail"),
    /** The chain could not reach a trustworthy determination. */
    INDETERMINATE("indeterminate");

    private final String recordValue;

    EvidenceResult(String recordValue) {
        this.recordValue = recordValue;
    }

    /**
     * Returns the Capability Matrix record spelling.
     *
     * @return lowercase record value
     */
    public String recordValue() {
        return recordValue;
    }
}
