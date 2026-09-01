package net.zerocloud.pdf.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.zerocloud.pdf.Annotation;
import net.zerocloud.pdf.DocumentCommand;

/**
 * Atomically creates, replaces, moves, or removes supported annotations by
 * document-wide identifier.
 *
 * @since 0.1.0
 */
public final class UpdateAnnotations implements DocumentCommand {

    /** The currently supported command representation version. */
    public static final int VERSION_1 = 1;

    private final List<Annotation> annotations;
    private final List<String> removedIdentifiers;

    private UpdateAnnotations(Builder builder) {
        this.annotations = Collections.unmodifiableList(
                new ArrayList<Annotation>(builder.annotations.values()));
        this.removedIdentifiers = Collections.unmodifiableList(
                new ArrayList<String>(builder.removedIdentifiers));
    }

    /** Starts a version-1 atomic update. @return a new builder */
    public static Builder version1() {
        return new Builder();
    }

    /** Returns the command representation version. @return {@link #VERSION_1} */
    public int getVersion() {
        return VERSION_1;
    }

    /** Returns annotations in declaration order. @return immutable annotations */
    public List<Annotation> getAnnotations() {
        return annotations;
    }

    /** Returns identifiers selected for removal. @return the identifiers */
    public List<String> getRemovedIdentifiers() {
        return removedIdentifiers;
    }

    /** Builds an ordered version-1 annotation update. @since 0.1.0 */
    public static final class Builder {

        private final Map<String, Annotation> annotations =
                new LinkedHashMap<String, Annotation>();
        private final List<String> removedIdentifiers =
                new ArrayList<String>();

        private Builder() {
        }

        /**
         * Creates or replaces one annotation by its document-wide identifier.
         * @param annotation the supported annotation
         * @return this builder
         */
        public Builder put(Annotation annotation) {
            Annotation value = Objects.requireNonNull(
                    annotation,
                    "annotation");
            String identifier = value.getProperties().getIdentifier();
            if (removedIdentifiers.contains(identifier)) {
                throw new IllegalArgumentException(
                        "The identifier is already selected for removal: "
                                + identifier);
            }
            annotations.put(identifier, value);
            return this;
        }

        /**
         * Removes one existing annotation by document-wide identifier.
         * @param identifier the nonempty identifier
         * @return this builder
         */
        public Builder remove(String identifier) {
            Objects.requireNonNull(identifier, "identifier");
            if (identifier.isEmpty()) {
                throw new IllegalArgumentException(
                        "identifier must not be empty");
            }
            if (annotations.containsKey(identifier)) {
                throw new IllegalArgumentException(
                        "The identifier is already selected for replacement: "
                                + identifier);
            }
            if (!removedIdentifiers.contains(identifier)) {
                removedIdentifiers.add(identifier);
            }
            return this;
        }

        /** Builds the immutable command. @return the update */
        public UpdateAnnotations build() {
            return new UpdateAnnotations(this);
        }
    }
}
