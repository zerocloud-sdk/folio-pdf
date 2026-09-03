package net.zerocloud.pdf;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.zerocloud.pdf.composition.ReferenceFontSet;
import net.zerocloud.pdf.provider.CapabilityProvider;
import net.zerocloud.pdf.provider.ProviderCatalog;
import net.zerocloud.pdf.provider.ProviderMetadata;

/**
 * Immutable environment shared by reusable Document Workflow instances.
 *
 * <p>The environment owns deadline time, declaration-ordered Capability
 * Provider registrations, and an explicit reusable Reference Font Set instead
 * of exposing them as generic lookups on Document Session. The system defaults
 * contain neither Providers nor fonts and therefore cannot select a remote
 * engine, scan installed fonts, or perform implicit network access. A caller-
 * supplied Clock and every registered Provider must be safe for the way the
 * resulting environment is shared.</p>
 *
 * @since 0.1.0
 */
public final class WorkflowEnvironment {

    private static final WorkflowEnvironment SYSTEM_DEFAULTS =
            new WorkflowEnvironment(
                    Clock.systemUTC(),
                    ProviderCatalog.empty(),
                    ReferenceFontSet.empty());

    private final Clock clock;
    private final ProviderCatalog providerCatalog;
    private final ReferenceFontSet referenceFontSet;

    private WorkflowEnvironment(
            Clock clock,
            ProviderCatalog providerCatalog,
            ReferenceFontSet referenceFontSet) {
        this.clock = clock;
        this.providerCatalog = providerCatalog;
        this.referenceFontSet = referenceFontSet;
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
                ProviderCatalog.empty(),
                ReferenceFontSet.empty());
    }

    /**
     * Begins explicit immutable Clock, Provider, and font configuration.
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

    ReferenceFontSet getReferenceFontSet() {
        return referenceFontSet;
    }

    /** Builds immutable, declaration-ordered workflow configuration. */
    public static final class Builder {

        private Clock clock = Clock.systemUTC();
        private final List<CapabilityProvider> providers =
                new ArrayList<CapabilityProvider>();
        private ReferenceFontSet referenceFontSet = ReferenceFontSet.empty();

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
         * Sets the reusable declaration-ordered Reference Font Set.
         *
         * @param referenceFontSet the explicit reusable font declarations
         * @return this builder
         */
        public Builder referenceFontSet(ReferenceFontSet referenceFontSet) {
            this.referenceFontSet = Objects.requireNonNull(
                    referenceFontSet,
                    "referenceFontSet");
            return this;
        }

        /**
         * Builds a detached immutable environment.
         *
         * @return the configured environment
         */
        public WorkflowEnvironment build() {
            return new WorkflowEnvironment(
                    clock,
                    ProviderCatalog.of(providers),
                    referenceFontSet);
        }
    }
}
