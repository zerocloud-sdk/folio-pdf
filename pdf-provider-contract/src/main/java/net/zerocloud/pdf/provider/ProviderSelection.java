package net.zerocloud.pdf.provider;

import java.util.Objects;

/** Immutable metadata snapshot for one deterministic Provider selection. */
public final class ProviderSelection {

    private final ProviderMetadata metadata;

    ProviderSelection(ProviderMetadata metadata) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
    }

    public String getProviderId() {
        return metadata.getProviderId();
    }

    public ProviderMetadata getMetadata() {
        return metadata;
    }
}
