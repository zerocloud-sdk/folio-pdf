package net.zerocloud.pdf.provider;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable identity, engine, licensing, availability, and limit facts for a
 * Capability Provider registration.
 *
 * <p>Availability is a registration snapshot. Rebuild the owning catalog or
 * Workflow Environment when an external engine installation changes.</p>
 *
 * <p>{@link ProviderExecutionMode#REMOTE REMOTE} execution and
 * {@link ProviderDistribution#REMOTE_SERVICE REMOTE_SERVICE} distribution
 * must be declared together so disclosure policy cannot be bypassed by
 * contradictory metadata.</p>
 */
public final class ProviderMetadata {

    private final String providerId;
    private final Set<String> capabilityIds;
    private final String engineVersion;
    private final ProviderExecutionMode executionMode;
    private final ProviderAvailability availability;
    private final ProviderLimits limits;
    private final String engineLicenseSpdxIdentifier;
    private final String engineLicenseName;
    private final ProviderDistribution distribution;

    private ProviderMetadata(Builder builder) {
        this.providerId = builder.providerId;
        this.capabilityIds = Collections.unmodifiableSet(
                new LinkedHashSet<String>(builder.capabilityIds));
        this.engineVersion = builder.engineVersion;
        this.executionMode = builder.executionMode;
        this.availability = builder.availability;
        this.limits = builder.limits;
        this.engineLicenseSpdxIdentifier = builder.engineLicenseSpdxIdentifier;
        this.engineLicenseName = builder.engineLicenseName;
        this.distribution = builder.distribution;
    }

    /**
     * Begins metadata for one stable Provider identity and engine version.
     *
     * @param providerId lowercase stable Provider ID
     * @param engineVersion installed or remote engine version
     * @return a metadata builder
     */
    public static Builder builder(String providerId, String engineVersion) {
        return new Builder(providerId, engineVersion);
    }

    public String getProviderId() {
        return providerId;
    }

    public Set<String> getCapabilityIds() {
        return capabilityIds;
    }

    public String getEngineVersion() {
        return engineVersion;
    }

    public ProviderExecutionMode getExecutionMode() {
        return executionMode;
    }

    public ProviderAvailability getAvailability() {
        return availability;
    }

    public ProviderLimits getLimits() {
        return limits;
    }

    public String getEngineLicenseSpdxIdentifier() {
        return engineLicenseSpdxIdentifier;
    }

    public String getEngineLicenseName() {
        return engineLicenseName;
    }

    public ProviderDistribution getDistribution() {
        return distribution;
    }

    /** Builds immutable Provider metadata. */
    public static final class Builder {

        private final String providerId;
        private final String engineVersion;
        private final Set<String> capabilityIds = new LinkedHashSet<String>();
        private ProviderExecutionMode executionMode;
        private ProviderAvailability availability;
        private ProviderLimits limits;
        private String engineLicenseSpdxIdentifier;
        private String engineLicenseName;
        private ProviderDistribution distribution;

        private Builder(String providerId, String engineVersion) {
            this.providerId = ProviderIdentifiers.requireStableId(
                    providerId,
                    "providerId");
            this.engineVersion = ProviderIdentifiers.requireText(
                    engineVersion,
                    "engineVersion");
        }

        public Builder capability(String capabilityId) {
            capabilityIds.add(ProviderIdentifiers.requireStableId(
                    capabilityId,
                    "capabilityId"));
            return this;
        }

        public Builder executionMode(ProviderExecutionMode executionMode) {
            this.executionMode = Objects.requireNonNull(
                    executionMode,
                    "executionMode");
            return this;
        }

        public Builder availability(ProviderAvailability availability) {
            this.availability = Objects.requireNonNull(
                    availability,
                    "availability");
            return this;
        }

        public Builder limits(ProviderLimits limits) {
            this.limits = Objects.requireNonNull(limits, "limits");
            return this;
        }

        public Builder engineLicense(String spdxIdentifier, String name) {
            this.engineLicenseSpdxIdentifier = ProviderIdentifiers.requireText(
                    spdxIdentifier,
                    "spdxIdentifier");
            this.engineLicenseName = ProviderIdentifiers.requireText(
                    name,
                    "licenseName");
            return this;
        }

        public Builder distribution(ProviderDistribution distribution) {
            this.distribution = Objects.requireNonNull(
                    distribution,
                    "distribution");
            return this;
        }

        public ProviderMetadata build() {
            if (capabilityIds.isEmpty()) {
                throw new IllegalStateException(
                        "Provider metadata must declare a capability");
            }
            Objects.requireNonNull(executionMode, "executionMode");
            Objects.requireNonNull(availability, "availability");
            Objects.requireNonNull(limits, "limits");
            Objects.requireNonNull(
                    engineLicenseSpdxIdentifier,
                    "engineLicenseSpdxIdentifier");
            Objects.requireNonNull(engineLicenseName, "engineLicenseName");
            Objects.requireNonNull(distribution, "distribution");
            boolean remoteExecution = executionMode == ProviderExecutionMode.REMOTE;
            boolean remoteDistribution = distribution
                    == ProviderDistribution.REMOTE_SERVICE;
            if (remoteExecution != remoteDistribution) {
                throw new IllegalStateException(
                        "Remote execution mode and remote-service distribution must agree");
            }
            return new ProviderMetadata(this);
        }
    }
}
