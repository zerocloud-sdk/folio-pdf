package net.zerocloud.pdf.provider;

import java.util.Objects;

/**
 * Project-owned seam implemented by replaceable conversion Provider adapters.
 *
 * <p>Implementations must be safe for concurrent invocation. This base class
 * enforces declared byte limits, availability, capability ownership, remote
 * disclosure authorization, and normalized adapter failures before results
 * cross the public seam. Checked adapter failures are rebuilt from their
 * stable code and the registered Provider identity so implementation details
 * cannot cross the boundary. Remote implementations cannot reach
 * {@link #perform(ProviderRequest)} unless the request explicitly authorizes
 * disclosure. The base validates that a request timeout is within the
 * Provider's declared maximum; each execution-mode adapter must enforce that
 * timeout because arbitrary in-process code cannot be forcibly terminated by
 * this contract.</p>
 */
public abstract class CapabilityProvider {

    private final ProviderMetadata metadata;

    /**
     * Creates a Provider with one immutable metadata snapshot.
     *
     * @param metadata Provider identity, capabilities, engine facts, and limits
     */
    protected CapabilityProvider(ProviderMetadata metadata) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
    }

    /**
     * Returns the immutable registration metadata.
     *
     * @return Provider metadata
     */
    public final ProviderMetadata getMetadata() {
        return metadata;
    }

    /**
     * Executes one request after capability, availability, byte-limit,
     * timeout-policy, and remote-disclosure checks.
     *
     * @param request immutable Provider request
     * @return detached bounded Provider result
     * @throws ProviderFailure if policy or Provider execution fails
     */
    public final ProviderResult execute(ProviderRequest request)
            throws ProviderFailure {
        ProviderRequest required = Objects.requireNonNull(request, "request");
        String capabilityId = required.getCapabilityId();
        if (!metadata.getCapabilityIds().contains(capabilityId)) {
            throw failure(ProviderFailureCode.PROVIDER_NOT_FOUND, capabilityId);
        }
        if (metadata.getAvailability() != ProviderAvailability.AVAILABLE) {
            throw failure(ProviderFailureCode.PROVIDER_UNAVAILABLE, capabilityId);
        }
        if (required.getInputLength()
                > metadata.getLimits().getMaximumInputBytes()) {
            throw failure(ProviderFailureCode.INPUT_LIMIT_EXCEEDED, capabilityId);
        }
        if (required.getTimeout().compareTo(
                metadata.getLimits().getMaximumDuration()) > 0) {
            throw new IllegalArgumentException(
                    "request timeout exceeds the Provider maximum duration");
        }
        if (metadata.getExecutionMode() == ProviderExecutionMode.REMOTE
                && !required.isRemoteDisclosureAuthorized()) {
            throw failure(
                    ProviderFailureCode.REMOTE_DISCLOSURE_NOT_AUTHORIZED,
                    capabilityId);
        }

        ProviderResult result;
        try {
            result = perform(required);
        } catch (ProviderFailure failure) {
            throw failure(failure.getCode(), capabilityId);
        } catch (RuntimeException failure) {
            throw failure(ProviderFailureCode.EXECUTION_FAILED, capabilityId);
        }
        if (result == null) {
            throw failure(ProviderFailureCode.MALFORMED_OUTPUT, capabilityId);
        }
        if (result.getOutputLength()
                > metadata.getLimits().getMaximumOutputBytes()) {
            throw failure(ProviderFailureCode.OUTPUT_LIMIT_EXCEEDED, capabilityId);
        }
        return result;
    }

    /**
     * Implements the real external Provider adapter after common policy checks.
     * Implementations report only stable, document-safe Provider Failures.
     * Each implementation must enforce {@link ProviderRequest#getTimeout()}
     * using the mechanism appropriate to its execution mode.
     *
     * @param request validated immutable request
     * @return detached Provider result
     * @throws ProviderFailure if the external Provider cannot complete safely
     */
    protected abstract ProviderResult perform(ProviderRequest request)
            throws ProviderFailure;

    private ProviderFailure failure(
            ProviderFailureCode code,
            String capabilityId) {
        return ProviderFailure.forProvider(
                code,
                metadata.getProviderId(),
                capabilityId);
    }
}
