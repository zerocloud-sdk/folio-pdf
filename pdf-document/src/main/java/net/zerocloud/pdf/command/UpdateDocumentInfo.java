package net.zerocloud.pdf.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.PdfValue;

/**
 * Applies ordered entry replacements and removals to the document information
 * dictionary, creating it when absent.
 *
 * <p>Entries are small detached PDF Values; streams and indirect references
 * are rejected. Pre-existing entries that the command does not name are
 * preserved unchanged.</p>
 *
 * @since 0.1.0
 */
public final class UpdateDocumentInfo implements DocumentCommand {

    /** The currently supported command representation version. */
    public static final int VERSION_1 = 1;

    private final Map<String, PdfValue> entries;
    private final List<String> removedNames;

    private UpdateDocumentInfo(Builder builder) {
        this.entries = Collections.unmodifiableMap(
                new LinkedHashMap<String, PdfValue>(builder.entries));
        this.removedNames = Collections.unmodifiableList(
                new ArrayList<String>(builder.removedNames));
    }

    /**
     * Starts a version-1 document information update.
     *
     * @return a new ordered update builder
     */
    public static Builder version1() {
        return new Builder();
    }

    /**
     * Returns the command representation version.
     *
     * @return {@link #VERSION_1}
     */
    public int getVersion() {
        return VERSION_1;
    }

    /**
     * Returns the entries to create or replace, in declaration order.
     *
     * @return the immutable name-to-value mapping
     */
    public Map<String, PdfValue> getEntries() {
        return entries;
    }

    /**
     * Returns the entry names to remove, in declaration order.
     *
     * @return the immutable removed names
     */
    public List<String> getRemovedNames() {
        return removedNames;
    }

    /**
     * Builds an ordered version-1 document information update.
     *
     * @since 0.1.0
     */
    public static final class Builder {

        private final Map<String, PdfValue> entries =
                new LinkedHashMap<String, PdfValue>();
        private final List<String> removedNames = new ArrayList<String>();

        private Builder() {
        }

        /**
         * Creates or replaces one document information entry.
         *
         * @param name the decoded PDF name, without a leading slash
         * @param value the detached PDF Value
         * @return this builder
         */
        public Builder set(String name, PdfValue value) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            if (removedNames.contains(name)) {
                throw new IllegalArgumentException(
                        "The name is already selected for removal: " + name);
            }
            entries.put(name, value);
            return this;
        }

        /**
         * Removes one document information entry.
         *
         * @param name the decoded PDF name, without a leading slash
         * @return this builder
         */
        public Builder remove(String name) {
            Objects.requireNonNull(name, "name");
            if (entries.containsKey(name)) {
                throw new IllegalArgumentException(
                        "The name is already selected for replacement: " + name);
            }
            if (!removedNames.contains(name)) {
                removedNames.add(name);
            }
            return this;
        }

        /**
         * Builds the immutable update command.
         *
         * @return the update command
         */
        public UpdateDocumentInfo build() {
            return new UpdateDocumentInfo(this);
        }
    }
}
