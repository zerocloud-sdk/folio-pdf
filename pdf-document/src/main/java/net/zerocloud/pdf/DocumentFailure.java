package net.zerocloud.pdf;

import java.util.Objects;

/**
 * A checked operational failure with a stable code and safe diagnostic.
 *
 * <p>Backend exceptions and document-sensitive details are deliberately not
 * retained as causes or exposed through this value.</p>
 *
 * @since 0.1.0
 */
public final class DocumentFailure extends Exception {

    private static final long serialVersionUID = 1L;

    private final DocumentFailureCode code;
    private final String capabilityId;
    private final String diagnostic;

    DocumentFailure(
            DocumentFailureCode code,
            String capabilityId,
            String diagnostic) {
        super(Objects.requireNonNull(diagnostic, "diagnostic"));
        this.code = Objects.requireNonNull(code, "code");
        this.capabilityId = Objects.requireNonNull(capabilityId, "capabilityId");
        this.diagnostic = diagnostic;
    }

    /**
     * Returns the stable failure category.
     *
     * @return the failure code
     */
    public DocumentFailureCode getCode() {
        return code;
    }

    /**
     * Returns the stable capability identifier associated with the failure.
     *
     * @return the capability identifier
     */
    public String getCapabilityId() {
        return capabilityId;
    }

    /**
     * Returns a diagnostic safe for normal application handling and logging.
     *
     * @return the safe diagnostic
     */
    public String getDiagnostic() {
        return diagnostic;
    }
}
