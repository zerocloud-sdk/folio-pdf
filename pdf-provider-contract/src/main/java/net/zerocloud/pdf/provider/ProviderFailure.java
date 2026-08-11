package net.zerocloud.pdf.provider;

import java.util.Objects;

/**
 * A checked Capability Provider failure with a stable code and safe
 * diagnostic. Raw engine and transport exceptions are never retained.
 */
public final class ProviderFailure extends Exception {

    private static final long serialVersionUID = 1L;

    private final ProviderFailureCode code;
    private final String providerId;
    private final String capabilityId;
    private final String diagnostic;

    private ProviderFailure(
            ProviderFailureCode code,
            String providerId,
            String capabilityId) {
        super(code.getSafeDiagnostic(), null, false, true);
        this.code = Objects.requireNonNull(code, "code");
        this.providerId = providerId;
        this.capabilityId = ProviderIdentifiers.requireStableId(
                capabilityId,
                "capabilityId");
        this.diagnostic = code.getSafeDiagnostic();
    }

    public static ProviderFailure forProvider(
            ProviderFailureCode code,
            String providerId,
            String capabilityId) {
        return new ProviderFailure(
                code,
                providerId == null
                        ? null
                        : ProviderIdentifiers.requireStableId(
                                providerId,
                                "providerId"),
                capabilityId);
    }

    public ProviderFailureCode getCode() {
        return code;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getCapabilityId() {
        return capabilityId;
    }

    public String getDiagnostic() {
        return diagnostic;
    }
}
