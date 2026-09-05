package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Environment-owned, finite transaction ledger. */
final class WorkflowTransactionRegistry {

    private final Map<WorkflowTransactionId, Record> records =
            new LinkedHashMap<WorkflowTransactionId, Record>();
    private final int maximumTransactions;
    private final long maximumRetainedBytesPerTransaction;

    WorkflowTransactionRegistry(WorkflowTransactionPolicy policy) {
        maximumTransactions = policy.getMaximumRetainedTransactions();
        maximumRetainedBytesPerTransaction =
                policy.getMaximumRetainedBytesPerTransaction();
    }

    Attempt begin(WorkflowRequest request)
            throws DocumentFailure {
        Optional<WorkflowTransactionId> optionalId = request.getTransactionId();
        if (!optionalId.isPresent()) {
            return null;
        }
        WorkflowTransactionId transactionId = optionalId.get();
        if (request.getExecutionProfile()
                != WorkflowExecutionProfile.HARDENED_WORKER) {
            throw failure(
                    DocumentFailureCode.INVALID_REQUEST,
                    "Transaction identities require the Hardened Worker Profile.",
                    transactionId,
                    PublicationReceipt.notAttempted(
                            request.getPublicationTargets()));
        }
        boolean retentionLimitReached;
        synchronized (this) {
            retentionLimitReached = !records.containsKey(transactionId)
                    && records.size() >= maximumTransactions;
        }
        WorkflowRequestFingerprint fingerprint =
                WorkflowRequestFingerprint.capture(
                        request,
                        transactionId,
                        maximumRetainedBytesPerTransaction);
        if (retentionLimitReached) {
            throw failure(
                    DocumentFailureCode.TRANSACTION_RETENTION_LIMIT_EXCEEDED,
                    "The transaction-status retention limit was exceeded.",
                    transactionId,
                    PublicationReceipt.notAttempted(
                            request.getPublicationTargets()));
        }
        synchronized (this) {
            Record existing = records.get(transactionId);
            if (existing != null) {
                if (!existing.fingerprint.equals(fingerprint)) {
                    throw failure(
                            DocumentFailureCode.TRANSACTION_IDENTITY_MISMATCH,
                            "The transaction identity belongs to a different workflow request.",
                            transactionId,
                            existing.status.getPublicationReceipts());
                }
                if (existing.status.getState()
                        == WorkflowTransactionState.RUNNING) {
                    throw failure(
                            DocumentFailureCode.TRANSACTION_IN_PROGRESS,
                            "The identified workflow transaction is already running.",
                            transactionId,
                            existing.status.getPublicationReceipts());
                }
                if (existing.status.isFinal()) {
                    throw failure(
                            DocumentFailureCode.TRANSACTION_ALREADY_FINAL,
                            "The identified workflow transaction is already final.",
                            transactionId,
                            existing.status.getPublicationReceipts());
                }
            } else {
                if (records.size() >= maximumTransactions) {
                    throw failure(
                            DocumentFailureCode
                                    .TRANSACTION_RETENTION_LIMIT_EXCEEDED,
                            "The transaction-status retention limit was exceeded.",
                            transactionId,
                            PublicationReceipt.notAttempted(
                                    request.getPublicationTargets()));
                }
                existing = new Record(fingerprint);
                records.put(transactionId, existing);
            }
            existing.status = new WorkflowTransactionStatus(
                    transactionId,
                    WorkflowTransactionState.RUNNING,
                    null,
                    PublicationReceipt.notAttempted(
                            request.getPublicationTargets()));
            return new Attempt(this, transactionId, existing, request);
        }
    }

    synchronized Optional<WorkflowTransactionStatus> lookup(
            WorkflowTransactionId transactionId) {
        Record record = records.get(transactionId);
        return record == null
                ? Optional.<WorkflowTransactionStatus>empty()
                : Optional.of(record.status);
    }

    private synchronized void completed(
            WorkflowTransactionId transactionId,
            Record record,
            List<PublicationReceipt> receipts) {
        if (records.get(transactionId) != record) {
            throw new IllegalStateException(
                    "The transaction record is no longer current.");
        }
        record.status = new WorkflowTransactionStatus(
                transactionId,
                WorkflowTransactionState.COMPLETED,
                null,
                receipts);
    }

    private synchronized void failed(
            WorkflowTransactionId transactionId,
            Record record,
            DocumentFailureCode failureCode,
            List<PublicationReceipt> receipts) {
        if (records.get(transactionId) != record) {
            throw new IllegalStateException(
                    "The transaction record is no longer current.");
        }
        record.status = new WorkflowTransactionStatus(
                transactionId,
                isRecoverable(failureCode, receipts)
                        ? WorkflowTransactionState.RECOVERABLE
                        : WorkflowTransactionState.FAILED,
                failureCode,
                receipts);
    }

    private synchronized void running(
            WorkflowTransactionId transactionId,
            Record record,
            List<PublicationReceipt> receipts) {
        if (records.get(transactionId) != record
                || record.status.getState()
                        != WorkflowTransactionState.RUNNING) {
            throw new IllegalStateException(
                    "The transaction record is not running.");
        }
        record.status = new WorkflowTransactionStatus(
                transactionId,
                WorkflowTransactionState.RUNNING,
                null,
                receipts);
    }

    private synchronized void terminalRuntimeFailure(
            WorkflowTransactionId transactionId,
            Record record,
            List<PublicationReceipt> receipts) {
        if (records.get(transactionId) != record) {
            throw new IllegalStateException(
                    "The transaction record is no longer current.");
        }
        record.status = new WorkflowTransactionStatus(
                transactionId,
                WorkflowTransactionState.FAILED,
                null,
                receipts);
    }

    private static boolean isRecoverable(
            DocumentFailureCode failureCode,
            List<PublicationReceipt> receipts) {
        for (PublicationReceipt receipt : receipts) {
            if (receipt.getStatus() != PublicationStatus.NOT_ATTEMPTED
                    || receipt.isPartialOutputPossible()) {
                return false;
            }
        }
        switch (failureCode) {
            case WORKFLOW_CANCELLED:
            case DEADLINE_EXCEEDED:
            case ELAPSED_TIME_LIMIT_EXCEEDED:
            case CONCURRENCY_LIMIT_EXCEEDED:
            case WORKER_UNAVAILABLE:
            case WORKER_AUTHENTICATION_FAILED:
            case WORKER_PROTOCOL_REJECTED:
            case WORKER_TERMINATED:
                return true;
            default:
                return false;
        }
    }

    private static DocumentFailure failure(
            DocumentFailureCode code,
            String diagnostic,
            WorkflowTransactionId transactionId,
            List<PublicationReceipt> receipts) {
        return new DocumentFailure(
                code,
                HardenedWorkerEngine.CAPABILITY_ID,
                diagnostic,
                receipts,
                transactionId);
    }

    private static final class Record {
        private final WorkflowRequestFingerprint fingerprint;
        private WorkflowTransactionStatus status;

        private Record(WorkflowRequestFingerprint fingerprint) {
            this.fingerprint = fingerprint;
        }
    }

    static final class Attempt {

        private final WorkflowTransactionRegistry registry;
        private final WorkflowTransactionId transactionId;
        private final Record record;
        private final WorkflowProgressListener progressListener;
        private final List<PublicationTarget> targets;
        private final List<PublicationReceipt> observedReceipts;
        private boolean publicationStarted;
        private boolean completedObserved;
        private int committedTargets;

        private Attempt(
                WorkflowTransactionRegistry registry,
                WorkflowTransactionId transactionId,
                Record record,
                WorkflowRequest request) {
            this.registry = registry;
            this.transactionId = transactionId;
            this.record = record;
            this.progressListener = request.getProgressListener();
            this.targets = new ArrayList<PublicationTarget>(
                    request.getPublicationTargets().values());
            this.observedReceipts = new ArrayList<PublicationReceipt>(
                    PublicationReceipt.notAttempted(
                            request.getPublicationTargets()));
        }

        WorkflowTransactionId getTransactionId() {
            return transactionId;
        }

        WorkflowRequest trackProgress(WorkflowRequest request) {
            return request.withProgressListener(phase -> {
                observe(phase);
                progressListener.onProgress(phase);
            });
        }

        private void observe(WorkflowProgressPhase phase) {
            synchronized (this) {
                if (phase == WorkflowProgressPhase.PUBLICATION_STARTED) {
                    publicationStarted = true;
                } else if (phase == WorkflowProgressPhase.TARGET_COMMITTED) {
                    if (committedTargets >= targets.size()) {
                        throw new IllegalStateException(
                                "Too many publication commits were observed.");
                    }
                    PublicationTarget target = targets.get(committedTargets);
                    PublicationReceipt previous = observedReceipts.get(
                            committedTargets);
                    observedReceipts.set(
                            committedTargets,
                            new PublicationReceipt(
                                    previous.getTargetName(),
                                    target.getKind()
                                            == PublicationTarget.Kind.PATH
                                                    ? target.getPath() : null,
                                    PublicationStatus.COMMITTED,
                                    false));
                    committedTargets++;
                } else if (phase == WorkflowProgressPhase.COMPLETED) {
                    completedObserved = true;
                }
                registry.running(
                        transactionId,
                        record,
                        observedReceipts);
            }
        }

        void completed(List<PublicationReceipt> receipts) {
            registry.completed(transactionId, record, receipts);
        }

        void failed(
                DocumentFailureCode failureCode,
                List<PublicationReceipt> receipts) {
            registry.failed(
                    transactionId,
                    record,
                    failureCode,
                    receipts);
        }

        void runtimeFailure() {
            List<PublicationReceipt> receipts;
            boolean publicationCompleted;
            synchronized (this) {
                publicationCompleted = completedObserved
                        || !targets.isEmpty()
                                && committedTargets == targets.size();
                if (!publicationCompleted
                        && publicationStarted
                        && committedTargets < targets.size()
                        && targets.get(committedTargets).getKind()
                                == PublicationTarget.Kind.STREAM) {
                    PublicationReceipt previous = observedReceipts.get(
                            committedTargets);
                    observedReceipts.set(
                            committedTargets,
                            new PublicationReceipt(
                                    previous.getTargetName(),
                                    null,
                                    PublicationStatus.FAILED,
                                    true));
                }
                receipts = new ArrayList<PublicationReceipt>(observedReceipts);
            }
            if (publicationCompleted) {
                registry.completed(transactionId, record, receipts);
            } else {
                registry.terminalRuntimeFailure(
                        transactionId,
                        record,
                        receipts);
            }
        }
    }
}
