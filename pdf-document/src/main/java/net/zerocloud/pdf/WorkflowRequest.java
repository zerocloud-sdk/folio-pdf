package net.zerocloud.pdf;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.zerocloud.pdf.provider.ProviderPreference;

/**
 * An immutable request for one Document Workflow transaction.
 *
 * <p>Sources and publication targets retain declaration order. Names are
 * unique within their respective collections. A request with sources
 * explicitly names the primary source operated on by the supplied Document
 * Session. Every request explicitly selects a Save Mode and may also supply
 * cancellation, an absolute deadline, a sanitized progress listener, and a
 * finite resource-policy override. Capability Provider preferences retain
 * declaration order. Remote document disclosure is capability-scoped, absent
 * by default, and never inferred from registration or preference.</p>
 *
 * @since 0.1.0
 */
public final class WorkflowRequest {

    private static final String DEFAULT_SOURCE_NAME = "source";
    private static final String DEFAULT_TARGET_NAME = "target";
    private static final WorkflowProgressListener NO_PROGRESS =
            new WorkflowProgressListener() {
                @Override
                public void onProgress(WorkflowProgressPhase phase) {
                    // Default requests do not observe progress.
                }
            };

    private final Map<String, DocumentSource> sources;
    private final String primarySourceName;
    private final Map<String, PublicationTarget> publicationTargets;
    private final SaveMode saveMode;
    private final PdfOutputPolicy outputPolicy;
    private final LegacySecurityMode legacySecurityMode;
    private final CancellationToken cancellationToken;
    private final Instant deadline;
    private final WorkflowProgressListener progressListener;
    private final WorkflowResourcePolicy resourcePolicy;
    private final Map<String, ProviderPreference> providerPreferences;
    private final Set<String> remoteDisclosureAuthorizations;

    private WorkflowRequest(Builder builder) {
        this.sources = Collections.unmodifiableMap(
                new LinkedHashMap<String, DocumentSource>(builder.sources));
        this.primarySourceName = builder.primarySourceName;
        this.publicationTargets = Collections.unmodifiableMap(
                new LinkedHashMap<String, PublicationTarget>(builder.publicationTargets));
        this.saveMode = builder.saveMode;
        this.outputPolicy = builder.outputPolicy;
        this.legacySecurityMode = builder.legacySecurityMode;
        this.cancellationToken = builder.cancellationToken;
        this.deadline = builder.deadline;
        this.progressListener = builder.progressListener;
        this.resourcePolicy = builder.resourcePolicy;
        this.providerPreferences = Collections.unmodifiableMap(
                new LinkedHashMap<String, ProviderPreference>(
                        builder.providerPreferences));
        this.remoteDisclosureAuthorizations = Collections.unmodifiableSet(
                new LinkedHashSet<String>(
                        builder.remoteDisclosureAuthorizations));
    }

    /**
     * Begins an explicitly configured workflow request.
     *
     * @return a new request builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a convenience request for a new document and one Path target.
     *
     * @param publicationTarget the path to replace after staging and validation
     * @param saveMode the caller-selected publication strategy
     * @return an immutable create request
     */
    public static WorkflowRequest create(
            Path publicationTarget,
            SaveMode saveMode) {
        return builder()
                .target(
                        DEFAULT_TARGET_NAME,
                        PublicationTarget.path(
                                Objects.requireNonNull(
                                        publicationTarget,
                                        "publicationTarget")))
                .saveMode(Objects.requireNonNull(saveMode, "saveMode"))
                .build();
    }

    /**
     * Creates a convenience request for one read-only Path source.
     *
     * @param source the PDF path to open
     * @param saveMode the caller-selected publication strategy
     * @return an immutable open request
     */
    public static WorkflowRequest open(Path source, SaveMode saveMode) {
        return builder()
                .source(
                        DEFAULT_SOURCE_NAME,
                        DocumentSource.path(Objects.requireNonNull(source, "source")))
                .primarySource(DEFAULT_SOURCE_NAME)
                .saveMode(Objects.requireNonNull(saveMode, "saveMode"))
                .build();
    }

    /**
     * Returns the path source used by the compatibility request shape.
     *
     * @return the optional primary path source
     */
    public Optional<Path> getSource() {
        DocumentSource primary = sources.get(primarySourceName);
        if (primary == null || primary.getKind() != DocumentSource.Kind.PATH) {
            return Optional.empty();
        }
        return Optional.of(primary.getPath());
    }

    /**
     * Returns the first path target used by the compatibility request shape.
     *
     * @return the optional first path target
     */
    public Optional<Path> getPublicationTarget() {
        for (PublicationTarget target : publicationTargets.values()) {
            if (target.getKind() == PublicationTarget.Kind.PATH) {
                return Optional.of(target.getPath());
            }
        }
        return Optional.empty();
    }

    Map<String, DocumentSource> getSources() {
        return sources;
    }

    String getPrimarySourceName() {
        return primarySourceName;
    }

    Map<String, PublicationTarget> getPublicationTargets() {
        return publicationTargets;
    }

    SaveMode getSaveMode() {
        return saveMode;
    }

    PdfOutputPolicy getOutputPolicy() {
        return outputPolicy;
    }

    LegacySecurityMode getLegacySecurityMode() {
        return legacySecurityMode;
    }

    CancellationToken getCancellationToken() {
        return cancellationToken;
    }

    Instant getDeadline() {
        return deadline;
    }

    WorkflowProgressListener getProgressListener() {
        return progressListener;
    }

    /**
     * Returns the request-level resource policy override.
     *
     * <p>When absent, the finite default policy from the executing
     * {@link WorkflowEnvironment} applies.</p>
     *
     * @return the optional immutable resource policy
     */
    public Optional<WorkflowResourcePolicy> getResourcePolicy() {
        return Optional.ofNullable(resourcePolicy);
    }

    Map<String, ProviderPreference> getProviderPreferences() {
        return providerPreferences;
    }

    boolean isRemoteDisclosureAuthorized(String capabilityId) {
        return remoteDisclosureAuthorizations.contains(capabilityId);
    }

    /**
     * Builds an immutable workflow request.
     *
     * @since 0.1.0
     */
    public static final class Builder {

        private final Map<String, DocumentSource> sources =
                new LinkedHashMap<String, DocumentSource>();
        private final Map<String, PublicationTarget> publicationTargets =
                new LinkedHashMap<String, PublicationTarget>();
        private String primarySourceName;
        private SaveMode saveMode;
        private PdfOutputPolicy outputPolicy;
        private LegacySecurityMode legacySecurityMode;
        private CancellationToken cancellationToken = CancellationToken.none();
        private Instant deadline;
        private WorkflowProgressListener progressListener = NO_PROGRESS;
        private WorkflowResourcePolicy resourcePolicy;
        private final Map<String, ProviderPreference> providerPreferences =
                new LinkedHashMap<String, ProviderPreference>();
        private final Set<String> remoteDisclosureAuthorizations =
                new LinkedHashSet<String>();

        private Builder() {
        }

        /**
         * Declares one named source.
         *
         * @param name the request-local source name
         * @param source the source descriptor
         * @return this builder
         */
        public Builder source(String name, DocumentSource source) {
            String requiredName = Objects.requireNonNull(name, "name");
            if (sources.containsKey(requiredName)) {
                throw new IllegalArgumentException(
                        "Duplicate source name: " + requiredName);
            }
            sources.put(requiredName, Objects.requireNonNull(source, "source"));
            return this;
        }

        /**
         * Selects the source operated on by the Document Session.
         *
         * @param name a declared source name
         * @return this builder
         */
        public Builder primarySource(String name) {
            this.primarySourceName = Objects.requireNonNull(name, "name");
            return this;
        }

        /**
         * Declares one named publication target.
         *
         * @param name the request-local target name
         * @param target the target descriptor
         * @return this builder
         */
        public Builder target(String name, PublicationTarget target) {
            String requiredName = Objects.requireNonNull(name, "name");
            if (publicationTargets.containsKey(requiredName)) {
                throw new IllegalArgumentException(
                        "Duplicate target name: " + requiredName);
            }
            publicationTargets.put(
                    requiredName,
                    Objects.requireNonNull(target, "target"));
            return this;
        }

        /**
         * Selects the publication strategy.
         *
         * @param saveMode the explicit save mode
         * @return this builder
         */
        public Builder saveMode(SaveMode saveMode) {
            this.saveMode = Objects.requireNonNull(saveMode, "saveMode");
            return this;
        }

        /**
         * Selects the PDF version and security policy for published products.
         * When absent, REWRITE products use the secure PDF 1.7 default and
         * INCREMENTAL products preserve their Source version.
         *
         * @param outputPolicy the request-scoped output policy
         * @return this builder
         */
        public Builder outputPolicy(PdfOutputPolicy outputPolicy) {
            this.outputPolicy = Objects.requireNonNull(
                    outputPolicy,
                    "outputPolicy");
            return this;
        }

        /**
         * Explicitly permits obsolete password-security output for this
         * request only. It does not select an obsolete algorithm by itself.
         *
         * @param mode the request-scoped legacy authorization
         * @return this builder
         */
        public Builder legacySecurityMode(LegacySecurityMode mode) {
            this.legacySecurityMode = Objects.requireNonNull(mode, "mode");
            return this;
        }

        /**
         * Supplies the explicit cancellation signal for this request.
         *
         * @param cancellationToken the cancellation token
         * @return this builder
         */
        public Builder cancellationToken(CancellationToken cancellationToken) {
            this.cancellationToken = Objects.requireNonNull(
                    cancellationToken,
                    "cancellationToken");
            return this;
        }

        /**
         * Sets the absolute deadline checked against the workflow's Clock.
         *
         * @param deadline the deadline instant
         * @return this builder
         */
        public Builder deadline(Instant deadline) {
            this.deadline = Objects.requireNonNull(deadline, "deadline");
            return this;
        }

        /**
         * Supplies the synchronous sanitized progress listener.
         *
         * @param progressListener the listener
         * @return this builder
         */
        public Builder progressListener(
                WorkflowProgressListener progressListener) {
            this.progressListener = Objects.requireNonNull(
                    progressListener,
                    "progressListener");
            return this;
        }

        /**
         * Overrides the environment's finite resource policy for this
         * request. The same policy governs every named Source, operation,
         * staged product, and publication target in the transaction.
         *
         * @param resourcePolicy the complete immutable request policy
         * @return this builder
         */
        public Builder resourcePolicy(
                WorkflowResourcePolicy resourcePolicy) {
            this.resourcePolicy = Objects.requireNonNull(
                    resourcePolicy,
                    "resourcePolicy");
            return this;
        }

        /**
         * Declares one capability-scoped Provider preference.
         *
         * @param preference the deterministic Provider preference
         * @return this builder
         */
        public Builder providerPreference(ProviderPreference preference) {
            ProviderPreference required = Objects.requireNonNull(
                    preference,
                    "preference");
            String capabilityId = required.getCapabilityId();
            if (providerPreferences.containsKey(capabilityId)) {
                throw new IllegalArgumentException(
                        "Duplicate Provider preference for capability: "
                                + capabilityId);
            }
            providerPreferences.put(capabilityId, required);
            return this;
        }

        /**
         * Explicitly authorizes disclosure to a remote Provider for one
         * capability. Authorization is absent by default.
         *
         * @param capabilityId the capability allowed to disclose request data
         * @return this builder
         */
        public Builder authorizeRemoteDisclosure(String capabilityId) {
            String requiredCapabilityId = ProviderPreference.any(capabilityId)
                    .getCapabilityId();
            remoteDisclosureAuthorizations.add(requiredCapabilityId);
            return this;
        }

        /**
         * Builds the immutable request.
         *
         * @return the request
         */
        public WorkflowRequest build() {
            if (!sources.isEmpty()
                    && (primarySourceName == null
                            || !sources.containsKey(primarySourceName))) {
                throw new IllegalStateException(
                        "A request with sources must select a declared primary source.");
            }
            if (saveMode == null) {
                throw new IllegalStateException(
                        "A workflow request must select a Save Mode.");
            }
            return new WorkflowRequest(this);
        }
    }
}
