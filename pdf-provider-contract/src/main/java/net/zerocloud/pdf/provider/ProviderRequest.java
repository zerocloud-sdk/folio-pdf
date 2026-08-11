package net.zerocloud.pdf.provider;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable project-owned bytes and policy for one Provider invocation.
 *
 * <p>Input bytes are defensively copied. The positive timeout may not exceed
 * the selected Provider's declared maximum duration and must be enforced by
 * its execution-mode adapter. Remote disclosure is absent by default and
 * requires an explicit builder call.</p>
 */
public final class ProviderRequest {

    private final String capabilityId;
    private final byte[] input;
    private final Duration timeout;
    private final boolean remoteDisclosureAuthorized;

    private ProviderRequest(Builder builder) {
        this.capabilityId = builder.capabilityId;
        this.input = builder.input.clone();
        this.timeout = builder.timeout;
        this.remoteDisclosureAuthorized = builder.remoteDisclosureAuthorized;
    }

    /**
     * Begins a request for one stable capability and detached byte payload.
     *
     * @param capabilityId requested capability
     * @param input request bytes copied immediately
     * @return a request builder
     */
    public static Builder builder(String capabilityId, byte[] input) {
        return new Builder(capabilityId, input);
    }

    public String getCapabilityId() {
        return capabilityId;
    }

    public byte[] getInput() {
        return input.clone();
    }

    public long getInputLength() {
        return input.length;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public boolean isRemoteDisclosureAuthorized() {
        return remoteDisclosureAuthorized;
    }

    /** Builds an immutable Provider request. */
    public static final class Builder {

        private final String capabilityId;
        private final byte[] input;
        private Duration timeout;
        private boolean remoteDisclosureAuthorized;

        private Builder(String capabilityId, byte[] input) {
            this.capabilityId = ProviderIdentifiers.requireStableId(
                    capabilityId,
                    "capabilityId");
            this.input = Objects.requireNonNull(input, "input").clone();
        }

        /**
         * Sets the positive elapsed-time deadline for this execution.
         *
         * @param timeout maximum elapsed execution time
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            Duration required = Objects.requireNonNull(timeout, "timeout");
            if (required.isZero() || required.isNegative()) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            this.timeout = required;
            return this;
        }

        /**
         * Explicitly permits this request payload to reach a REMOTE Provider.
         *
         * @return this builder
         */
        public Builder authorizeRemoteDisclosure() {
            this.remoteDisclosureAuthorized = true;
            return this;
        }

        /**
         * Builds an immutable detached request.
         *
         * @return the Provider request
         */
        public ProviderRequest build() {
            Objects.requireNonNull(timeout, "timeout");
            return new ProviderRequest(this);
        }
    }
}
