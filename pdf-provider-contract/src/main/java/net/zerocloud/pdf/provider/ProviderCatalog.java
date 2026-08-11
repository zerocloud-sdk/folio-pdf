package net.zerocloud.pdf.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, declaration-ordered Provider registrations with deterministic
 * capability selection and execution.
 *
 * <p>Selection is stable for the same registrations, preference, availability
 * snapshot, and disclosure authorization. An explicit Provider ID never falls
 * back. An unqualified preference selects the first available eligible
 * registration and skips unauthorized remote registrations. The catalog is a
 * Provider-seam module; Workflow Environment exposes only its immutable
 * metadata and never exposes this executable catalog as a Document Session
 * service locator.</p>
 */
public final class ProviderCatalog {

    private static final ProviderCatalog EMPTY = ProviderCatalog.of(
            Collections.<CapabilityProvider>emptyList());

    private final List<CapabilityProvider> providers;
    private final Map<String, CapabilityProvider> providersById;
    private final List<ProviderMetadata> metadata;

    private ProviderCatalog(List<? extends CapabilityProvider> providers) {
        List<CapabilityProvider> copied = new ArrayList<CapabilityProvider>();
        Map<String, CapabilityProvider> indexed =
                new LinkedHashMap<String, CapabilityProvider>();
        List<ProviderMetadata> facts = new ArrayList<ProviderMetadata>();
        for (CapabilityProvider provider : providers) {
            CapabilityProvider required = Objects.requireNonNull(
                    provider,
                    "provider");
            String providerId = required.getMetadata().getProviderId();
            if (indexed.put(providerId, required) != null) {
                throw new IllegalArgumentException(
                        "Duplicate Provider registration: " + providerId);
            }
            copied.add(required);
            facts.add(required.getMetadata());
        }
        this.providers = Collections.unmodifiableList(copied);
        this.providersById = Collections.unmodifiableMap(indexed);
        this.metadata = Collections.unmodifiableList(facts);
    }

    /**
     * Returns the shared immutable empty offline catalog.
     *
     * @return an empty catalog
     */
    public static ProviderCatalog empty() {
        return EMPTY;
    }

    /**
     * Copies declaration-ordered Provider registrations.
     *
     * @param providers Providers in deterministic preference order
     * @return an immutable catalog
     * @throws IllegalArgumentException if a Provider ID is duplicated
     */
    public static ProviderCatalog of(
            List<? extends CapabilityProvider> providers) {
        return new ProviderCatalog(Objects.requireNonNull(providers, "providers"));
    }

    /**
     * Returns declaration-ordered immutable Provider metadata only.
     *
     * @return immutable metadata
     */
    public List<ProviderMetadata> getMetadata() {
        return metadata;
    }

    /**
     * Selects one eligible Provider without invoking it.
     *
     * @param preference capability and optional Provider ID
     * @param remoteDisclosureAuthorized whether that capability may use REMOTE
     * @return the immutable selected metadata
     * @throws ProviderFailure if no eligible Provider can be selected safely
     */
    public ProviderSelection select(
            ProviderPreference preference,
            boolean remoteDisclosureAuthorized) throws ProviderFailure {
        ProviderPreference required = Objects.requireNonNull(
                preference,
                "preference");
        if (required.getPreferredProviderId().isPresent()) {
            return selectPreferred(required, remoteDisclosureAuthorized);
        }
        return selectFirstEligible(required, remoteDisclosureAuthorized);
    }

    /**
     * Selects and executes one Provider at the real external seam.
     * Remote authorization is read from the request and checked before adapter
     * code executes.
     *
     * @param request immutable bounded request
     * @param preference matching capability preference
     * @return detached selection and result
     * @throws ProviderFailure if selection or execution fails
     */
    public ProviderExecution execute(
            ProviderRequest request,
            ProviderPreference preference) throws ProviderFailure {
        ProviderRequest requiredRequest = Objects.requireNonNull(request, "request");
        ProviderPreference requiredPreference = Objects.requireNonNull(
                preference,
                "preference");
        if (!requiredRequest.getCapabilityId().equals(
                requiredPreference.getCapabilityId())) {
            throw new IllegalArgumentException(
                    "Provider request and preference capabilities must match");
        }
        ProviderSelection selection = select(
                requiredPreference,
                requiredRequest.isRemoteDisclosureAuthorized());
        CapabilityProvider provider = providersById.get(selection.getProviderId());
        return new ProviderExecution(selection, provider.execute(requiredRequest));
    }

    private ProviderSelection selectPreferred(
            ProviderPreference preference,
            boolean remoteDisclosureAuthorized) throws ProviderFailure {
        String providerId = preference.getPreferredProviderId().get();
        CapabilityProvider provider = providersById.get(providerId);
        if (provider == null
                || !provider.getMetadata().getCapabilityIds().contains(
                        preference.getCapabilityId())) {
            throw failure(
                    ProviderFailureCode.PROVIDER_NOT_FOUND,
                    providerId,
                    preference.getCapabilityId());
        }
        requireEligible(
                provider.getMetadata(),
                preference.getCapabilityId(),
                remoteDisclosureAuthorized);
        return new ProviderSelection(provider.getMetadata());
    }

    private ProviderSelection selectFirstEligible(
            ProviderPreference preference,
            boolean remoteDisclosureAuthorized) throws ProviderFailure {
        boolean matchingUnavailable = false;
        boolean matchingRemoteDenied = false;
        for (CapabilityProvider provider : providers) {
            ProviderMetadata facts = provider.getMetadata();
            if (!facts.getCapabilityIds().contains(preference.getCapabilityId())) {
                continue;
            }
            if (facts.getAvailability() != ProviderAvailability.AVAILABLE) {
                matchingUnavailable = true;
                continue;
            }
            if (facts.getExecutionMode() == ProviderExecutionMode.REMOTE
                    && !remoteDisclosureAuthorized) {
                matchingRemoteDenied = true;
                continue;
            }
            return new ProviderSelection(facts);
        }
        if (matchingRemoteDenied) {
            throw failure(
                    ProviderFailureCode.REMOTE_DISCLOSURE_NOT_AUTHORIZED,
                    null,
                    preference.getCapabilityId());
        }
        if (matchingUnavailable) {
            throw failure(
                    ProviderFailureCode.PROVIDER_UNAVAILABLE,
                    null,
                    preference.getCapabilityId());
        }
        throw failure(
                ProviderFailureCode.PROVIDER_NOT_FOUND,
                null,
                preference.getCapabilityId());
    }

    private static void requireEligible(
            ProviderMetadata metadata,
            String capabilityId,
            boolean remoteDisclosureAuthorized) throws ProviderFailure {
        if (metadata.getAvailability() != ProviderAvailability.AVAILABLE) {
            throw failure(
                    ProviderFailureCode.PROVIDER_UNAVAILABLE,
                    metadata.getProviderId(),
                    capabilityId);
        }
        if (metadata.getExecutionMode() == ProviderExecutionMode.REMOTE
                && !remoteDisclosureAuthorized) {
            throw failure(
                    ProviderFailureCode.REMOTE_DISCLOSURE_NOT_AUTHORIZED,
                    metadata.getProviderId(),
                    capabilityId);
        }
    }

    private static ProviderFailure failure(
            ProviderFailureCode code,
            String providerId,
            String capabilityId) {
        return ProviderFailure.forProvider(code, providerId, capabilityId);
    }
}
