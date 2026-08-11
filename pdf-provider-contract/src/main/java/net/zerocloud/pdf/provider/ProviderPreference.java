package net.zerocloud.pdf.provider;

import java.util.Optional;

/** Immutable capability-scoped Provider selection preference. */
public final class ProviderPreference {

    private final String capabilityId;
    private final String preferredProviderId;

    private ProviderPreference(
            String capabilityId,
            String preferredProviderId) {
        this.capabilityId = ProviderIdentifiers.requireStableId(
                capabilityId,
                "capabilityId");
        this.preferredProviderId = preferredProviderId;
    }

    public static ProviderPreference any(String capabilityId) {
        return new ProviderPreference(capabilityId, null);
    }

    public static ProviderPreference prefer(
            String capabilityId,
            String providerId) {
        return new ProviderPreference(
                capabilityId,
                ProviderIdentifiers.requireStableId(providerId, "providerId"));
    }

    public String getCapabilityId() {
        return capabilityId;
    }

    public Optional<String> getPreferredProviderId() {
        return Optional.ofNullable(preferredProviderId);
    }
}
