package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A checked operational failure with a stable code, capability identifier,
 * safe diagnostic, and any known per-target publication outcomes.
 *
 * <p>Backend exceptions and document-sensitive details are deliberately not
 * retained as causes or exposed through this value. When failure occurs before
 * publication, every declared target is reported as
 * {@link PublicationStatus#NOT_ATTEMPTED}. A caller callback's unchecked
 * exception is not wrapped in this type.</p>
 *
 * @since 0.1.0
 */
public final class DocumentFailure extends Exception {

    private static final long serialVersionUID = 1L;

    private final DocumentFailureCode code;
    private final String capabilityId;
    private final String diagnostic;
    private final List<PublicationReceipt> publicationReceipts;

    DocumentFailure(
            DocumentFailureCode code,
            String capabilityId,
            String diagnostic) {
        this(
                code,
                capabilityId,
                diagnostic,
                Collections.<PublicationReceipt>emptyList());
    }

    DocumentFailure(
            DocumentFailureCode code,
            String capabilityId,
            String diagnostic,
            List<PublicationReceipt> publicationReceipts) {
        super(Objects.requireNonNull(diagnostic, "diagnostic"));
        this.code = Objects.requireNonNull(code, "code");
        this.capabilityId = Objects.requireNonNull(capabilityId, "capabilityId");
        this.diagnostic = diagnostic;
        this.publicationReceipts = Collections.unmodifiableList(
                new ArrayList<PublicationReceipt>(
                        Objects.requireNonNull(
                                publicationReceipts,
                                "publicationReceipts")));
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

    /**
     * Returns the disposition of every declared target when publication state
     * is available.
     *
     * @return immutable publication receipts
     */
    public List<PublicationReceipt> getPublicationReceipts() {
        return publicationReceipts;
    }
}
