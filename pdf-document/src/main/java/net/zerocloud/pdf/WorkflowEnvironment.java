package net.zerocloud.pdf;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.zerocloud.pdf.provider.CapabilityProvider;
import net.zerocloud.pdf.provider.ProviderCatalog;
import net.zerocloud.pdf.provider.ProviderMetadata;

/**
 * Immutable environment shared by reusable Document Workflow instances.
 *
 * <p>The environment owns deadline time and declaration-ordered Capability
 * Provider registrations instead of exposing either as a generic lookup on
 * Document Session. The system defaults contain no Provider registration and
 * therefore cannot select a remote engine or perform implicit network access.
 * A caller-supplied Clock and every registered Provider must be safe for the
 * way the resulting environment is shared.</p>
 *
 * @since 0.1.0
 */
public final class WorkflowEnvironment {

    private static final WorkflowEnvironment SYSTEM_DEFAULTS =
            new WorkflowEnvironment(Clock.systemUTC(), ProviderCatalog.empty());

    private final Clock clock;
    private final ProviderCatalog providerCatalog;

    private WorkflowEnvironment(Clock clock, ProviderCatalog providerCatalog) {
        this.clock = clock;
        this.providerCatalog = providerCatalog;
    }

    /**
     * Returns the immutable environment using the UTC system clock.
     *
     * @return the system-default environment
     */
    public static WorkflowEnvironment systemDefaults() {
        return SYSTEM_DEFAULTS;
    }

    /**
     * Creates an immutable environment using an explicit deadline Clock.
     *
     * @param clock the execution clock
     * @return an environment owning the supplied Clock
     */
    public static WorkflowEnvironment withClock(Clock clock) {
        return new WorkflowEnvironment(
                Objects.requireNonNull(clock, "clock"),
                ProviderCatalog.empty());
    }

    /**
     * Begins explicit immutable Clock and Provider configuration.
     *
     * @return a detached environment builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns declaration-ordered immutable metadata for registered Providers.
     * Executable Provider instances and the selection catalog remain hidden.
     *
     * @return immutable Provider metadata, empty for the offline defaults
     */
    public List<ProviderMetadata> getProviderMetadata() {
        return providerCatalog.getMetadata();
    }

    Clock getClock() {
        return clock;
    }

    ProviderCatalog getProviderCatalog() {
        return providerCatalog;
    }

    /** Builds immutable, declaration-ordered workflow configuration. */
    public static final class Builder {

        private Clock clock = Clock.systemUTC();
        private final List<CapabilityProvider> providers =
                new ArrayList<CapabilityProvider>();

        private Builder() {
        }

        /**
         * Sets the Clock used to evaluate absolute workflow deadlines.
         *
         * @param clock the shared deadline Clock
         * @return this builder
         */
        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        /**
         * Registers one thread-safe Capability Provider in selection order.
         * Registration does not authorize remote disclosure.
         *
         * @param provider the Provider registration
         * @return this builder
         */
        public Builder provider(CapabilityProvider provider) {
            providers.add(Objects.requireNonNull(provider, "provider"));
            return this;
        }

        /**
         * Builds a detached immutable environment.
         *
         * @return the configured environment
         */
        public WorkflowEnvironment build() {
            return new WorkflowEnvironment(clock, ProviderCatalog.of(providers));
        }
    }
}
