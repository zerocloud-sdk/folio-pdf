package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A checked operational failure with a stable code, capability identifier,
 * safe diagnostic, any known per-target publication outcomes, and the optional
 * identity of an identified logical transaction.
 *
 * <p>Backend exceptions and document-sensitive details are deliberately not
 * retained as causes or exposed through this value. When bounded publication
 * state is available before publication, every declared target is reported as
 * {@link PublicationStatus#NOT_ATTEMPTED}. A transaction-retention refusal
 * may omit receipts when copying the declaration would itself exceed the
 * retention policy. A caller callback's unchecked exception is not wrapped in
 * this type.</p>
 *
 * @since 0.1.0
 */
public final class DocumentFailure extends Exception {

    private static final long serialVersionUID = 1L;

    private final DocumentFailureCode code;
    private final String capabilityId;
    private final String diagnostic;
    private final List<PublicationReceipt> publicationReceipts;
    private final WorkflowTransactionId transactionId;

    DocumentFailure(
            DocumentFailureCode code,
            String capabilityId,
            String diagnostic) {
        this(
                code,
                capabilityId,
                diagnostic,
                Collections.<PublicationReceipt>emptyList(),
                null);
    }

    DocumentFailure(
            DocumentFailureCode code,
            String capabilityId,
            String diagnostic,
            List<PublicationReceipt> publicationReceipts) {
        this(code, capabilityId, diagnostic, publicationReceipts, null);
    }

    DocumentFailure(
            DocumentFailureCode code,
            String capabilityId,
            String diagnostic,
            List<PublicationReceipt> publicationReceipts,
            WorkflowTransactionId transactionId) {
        super(Objects.requireNonNull(diagnostic, "diagnostic"));
        this.code = Objects.requireNonNull(code, "code");
        this.capabilityId = Objects.requireNonNull(capabilityId, "capabilityId");
        this.diagnostic = diagnostic;
        this.publicationReceipts = Collections.unmodifiableList(
                new ArrayList<PublicationReceipt>(
                        Objects.requireNonNull(
                                publicationReceipts,
                                "publicationReceipts")));
        this.transactionId = transactionId;
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

    /** @return the optional identity of the failed logical transaction */
    public Optional<WorkflowTransactionId> getTransactionId() {
        return Optional.ofNullable(transactionId);
    }

    DocumentFailure withTransactionId(WorkflowTransactionId value) {
        if (transactionId != null) {
            return this;
        }
        DocumentFailure copy = new DocumentFailure(
                code,
                capabilityId,
                diagnostic,
                publicationReceipts,
                value);
        for (Throwable suppressed : getSuppressed()) {
            copy.addSuppressed(suppressed);
        }
        return copy;
    }
}
