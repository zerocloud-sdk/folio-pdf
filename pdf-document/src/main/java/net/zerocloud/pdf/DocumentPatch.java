package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A versioned, ordered set of validated low-level document changes.
 *
 * <p>The Document Engine validates the complete Patch before applying its
 * changes. Callers never mutate live backend values.</p>
 *
 * @since 0.1.0
 */
public final class DocumentPatch implements DocumentCommand {

    /** The currently supported Patch representation version. */
    public static final int VERSION_1 = 1;

    private final List<DictionaryEntryChange> changes;

    private DocumentPatch(Builder builder) {
        this.changes = Collections.unmodifiableList(
                new ArrayList<DictionaryEntryChange>(builder.changes));
    }

    /**
     * Starts an ordered version-1 Document Patch.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the Patch representation version.
     *
     * @return {@link #VERSION_1}
     */
    public int getVersion() {
        return VERSION_1;
    }

    List<DictionaryEntryChange> getChanges() {
        return changes;
    }

    static final class DictionaryEntryChange {

        private final ObjectReference target;
        private final PdfName name;
        private final PdfValue value;

        DictionaryEntryChange(
                ObjectReference target,
                PdfName name,
                PdfValue value) {
            this.target = target;
            this.name = name;
            this.value = value;
        }

        ObjectReference getTarget() {
            return target;
        }

        PdfName getName() {
            return name;
        }

        PdfValue getValue() {
            return value;
        }
    }

    /**
     * Builds one immutable ordered Patch.
     *
     * @since 0.1.0
     */
    public static final class Builder {

        private final List<DictionaryEntryChange> changes =
                new ArrayList<DictionaryEntryChange>();

        private Builder() {
        }

        /**
         * Requests replacement of one dictionary entry.
         *
         * @param dictionary the Session-owned target dictionary
         * @param name the entry name
         * @param value the backend-neutral replacement value
         * @return this builder
         */
        public Builder setDictionaryEntry(
                ObjectReference dictionary,
                PdfName name,
                PdfValue value) {
            changes.add(new DictionaryEntryChange(
                    Objects.requireNonNull(dictionary, "dictionary"),
                    Objects.requireNonNull(name, "name"),
                    Objects.requireNonNull(value, "value")));
            return this;
        }

        /**
         * Builds the immutable Patch.
         *
         * @return the ordered Patch
         */
        public DocumentPatch build() {
            if (changes.isEmpty()) {
                throw new IllegalStateException(
                        "A Document Patch must contain at least one change.");
            }
            return new DocumentPatch(this);
        }
    }
}
