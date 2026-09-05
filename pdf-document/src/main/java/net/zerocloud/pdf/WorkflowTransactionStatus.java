package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Detached public status of one identified Document Workflow transaction.
 *
 * @since 0.1.0
 */
public final class WorkflowTransactionStatus {

    private final WorkflowTransactionId transactionId;
    private final WorkflowTransactionState state;
    private final DocumentFailureCode failureCode;
    private final List<PublicationReceipt> publicationReceipts;

    WorkflowTransactionStatus(
            WorkflowTransactionId transactionId,
            WorkflowTransactionState state,
            DocumentFailureCode failureCode,
            List<PublicationReceipt> publicationReceipts) {
        this.transactionId = Objects.requireNonNull(
                transactionId,
                "transactionId");
        this.state = Objects.requireNonNull(state, "state");
        this.failureCode = failureCode;
        this.publicationReceipts = Collections.unmodifiableList(
                new ArrayList<PublicationReceipt>(Objects.requireNonNull(
                        publicationReceipts,
                        "publicationReceipts")));
    }

    /** @return the caller-supplied transaction identity */
    public WorkflowTransactionId getTransactionId() {
        return transactionId;
    }

    /** @return the current lifecycle state */
    public WorkflowTransactionState getState() {
        return state;
    }

    /** @return the terminal or recoverable failure code, when one was recorded */
    public Optional<DocumentFailureCode> getFailureCode() {
        return Optional.ofNullable(failureCode);
    }

    /** @return declaration-ordered immutable publication receipts */
    public List<PublicationReceipt> getPublicationReceipts() {
        return publicationReceipts;
    }

    /** @return whether this identity may never be executed again in its scope */
    public boolean isFinal() {
        return state == WorkflowTransactionState.COMPLETED
                || state == WorkflowTransactionState.FAILED;
    }
}
