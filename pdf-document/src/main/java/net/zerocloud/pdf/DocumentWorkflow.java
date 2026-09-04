package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.zerocloud.pdf.provider.ProviderFailure;
import net.zerocloud.pdf.provider.ProviderFailureCode;
import net.zerocloud.pdf.provider.ProviderPreference;
import net.zerocloud.pdf.provider.ProviderSelection;

/**
 * Reusable entry point for isolated document transactions.
 *
 * <p>The workflow owns document opening, staged publication, validation, and
 * cleanup, finite resource accounting, cooperative cancellation/deadline/time
 * checks, shared-environment concurrency admission, and sanitized progress.
 * Instances contain no per-execution state and may be reused by independent
 * callers; each supplied session remains thread-confined.</p>
 *
 * @since 0.1.0
 */
public final class DocumentWorkflow {

    private final WorkflowEnvironment environment;

    /**
     * Creates a workflow using the UTC system clock.
     */
    public DocumentWorkflow() {
        this(WorkflowEnvironment.systemDefaults());
    }

    /**
     * Creates a workflow using an explicit immutable environment.
     *
     * @param environment the workflow environment
     */
    public DocumentWorkflow(WorkflowEnvironment environment) {
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    /**
     * Executes one create or open request through a caller-side callback.
     *
     * <p>A caller callback's unchecked exception propagates unchanged and
     * prevents publication. Operational failures are checked and expose any
     * known per-target receipts.</p>
     *
     * @param request the immutable workflow request
     * @param work caller work at the public Document Session seam
     * @param <R> the caller result type
     * @return the completed outcome and publication receipts; any Session-bound
     *     values retained in the caller result are expired after the callback
     * @throws DocumentFailure if an operational document step fails
     */
    public <R> WorkflowOutcome<R> execute(
            WorkflowRequest request,
            DocumentWork<R> work) throws DocumentFailure {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(work, "work");
        WorkflowResourcePolicy policy = request.getResourcePolicy()
                .orElse(environment.getDefaultResourcePolicy());
        WorkflowConcurrencyGate.Permit permit =
                environment.getConcurrencyGate().tryAcquire(
                        policy.getMaximumConcurrentWorkflows());
        if (permit == null) {
            throw withReceipts(
                    new DocumentFailure(
                            DocumentFailureCode.CONCURRENCY_LIMIT_EXCEEDED,
                            WorkflowResourceContext.CAPABILITY_ID,
                            "The workflow concurrency limit was exceeded."),
                    request);
        }

        WorkflowResourceContext resources = null;
        try {
            resources = WorkflowResourceContext.open(
                    policy,
                    environment.getClock(),
                    request.getCancellationToken(),
                    request.getDeadline(),
                    environment.getTemporaryDirectory());
            retainByteSources(request, resources);
            resources.checkpoint();
            List<ProviderSelection> providerSelections =
                    selectProviders(request, resources);
            return PdfBoxWorkflowEngine.execute(
                    new PdfBoxWorkflowEngine.ExecutionContext<R>(
                            request,
                            work,
                            providerSelections,
                            environment.getReferenceFontSet().getSources(),
                            resources));
        } catch (DocumentFailure failure) {
            throw withReceipts(failure, request);
        } finally {
            if (resources != null) {
                resources.close();
            }
            permit.close();
        }
    }

    private static void retainByteSources(
            WorkflowRequest request,
            WorkflowResourceContext resources) throws DocumentFailure {
        Set<byte[]> retainedArrays = Collections.newSetFromMap(
                new IdentityHashMap<byte[], Boolean>());
        for (DocumentSource source : request.getSources().values()) {
            resources.checkpoint();
            if (source.getKind() == DocumentSource.Kind.BYTES
                    && retainedArrays.add(source.getBytes())) {
                resources.retainOwnedMemory(source.getBytes().length);
            }
        }
    }

    private static DocumentFailure withReceipts(
            DocumentFailure failure,
            WorkflowRequest request) {
        if (request.getPublicationTargets().isEmpty()
                || !failure.getPublicationReceipts().isEmpty()) {
            return failure;
        }
        return new DocumentFailure(
                failure.getCode(),
                failure.getCapabilityId(),
                failure.getDiagnostic(),
                PublicationReceipt.notAttempted(
                        request.getPublicationTargets()));
    }

    private List<ProviderSelection> selectProviders(
            WorkflowRequest request,
            WorkflowResourceContext resources)
            throws DocumentFailure {
        List<ProviderSelection> selections = new ArrayList<ProviderSelection>();
        try {
            for (Map.Entry<String, ProviderPreference> entry
                    : request.getProviderPreferences().entrySet()) {
                resources.checkpoint();
                selections.add(environment.getProviderCatalog().select(
                        entry.getValue(),
                        request.isRemoteDisclosureAuthorized(entry.getKey())));
            }
        } catch (ProviderFailure failure) {
            throw new DocumentFailure(
                    documentFailureCode(failure.getCode()),
                    failure.getCapabilityId(),
                    failure.getDiagnostic(),
                    PublicationReceipt.notAttempted(
                            request.getPublicationTargets()));
        }
        return selections;
    }

    private static DocumentFailureCode documentFailureCode(
            ProviderFailureCode providerCode) {
        switch (providerCode) {
            case PROVIDER_NOT_FOUND:
                return DocumentFailureCode.CAPABILITY_PROVIDER_NOT_FOUND;
            case PROVIDER_UNAVAILABLE:
                return DocumentFailureCode.CAPABILITY_PROVIDER_UNAVAILABLE;
            case REMOTE_DISCLOSURE_NOT_AUTHORIZED:
                return DocumentFailureCode.REMOTE_DISCLOSURE_NOT_AUTHORIZED;
            default:
                return DocumentFailureCode.CAPABILITY_PROVIDER_FAILED;
        }
    }

}
