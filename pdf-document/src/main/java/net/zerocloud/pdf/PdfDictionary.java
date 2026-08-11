package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable PDF dictionary or a bounded lazy Session view of one.
 *
 * @since 0.1.0
 */
public final class PdfDictionary implements PdfValue {

    private final PdfDictionaryAccess access;

    PdfDictionary(PdfDictionaryAccess access) {
        this.access = Objects.requireNonNull(access, "access");
    }

    /**
     * Starts an immutable detached PDF dictionary.
     *
     * @return a new dictionary builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the number of entries in this dictionary.
     *
     * @return the entry count
     * @throws DocumentFailure if the lazy view is no longer usable
     */
    public int size() throws DocumentFailure {
        return access.size();
    }

    /**
     * Obtains one value by name without exposing the backend dictionary.
     *
     * @param name the dictionary key
     * @return the value, or {@code null} when the key is absent
     * @throws DocumentFailure if the lazy view cannot be traversed
     */
    public PdfValue get(PdfName name) throws DocumentFailure {
        return access.get(Objects.requireNonNull(name, "name"));
    }

    /**
     * Obtains one entry by traversal position. Each call on a Session view
     * consumes one value from its declared inspection bound.
     *
     * @param index the zero-based traversal position
     * @return the immutable name/value pair
     * @throws DocumentFailure if the lazy traversal cannot continue
     */
    public PdfDictionaryEntry getEntry(int index) throws DocumentFailure {
        return access.getEntry(index);
    }

    @Override
    public PdfValueKind getKind() {
        return PdfValueKind.DICTIONARY;
    }

    /**
     * Builds an immutable detached PDF dictionary.
     *
     * @since 0.1.0
     */
    public static final class Builder {

        private final Map<PdfName, PdfValue> entries =
                new LinkedHashMap<PdfName, PdfValue>();

        private Builder() {
        }

        /**
         * Adds one uniquely named dictionary entry.
         *
         * @param name the entry name
         * @param value the entry value
         * @return this builder
         */
        public Builder put(PdfName name, PdfValue value) {
            PdfName requiredName = Objects.requireNonNull(name, "name");
            if (entries.containsKey(requiredName)) {
                throw new IllegalArgumentException(
                        "Duplicate PDF dictionary name: " + requiredName);
            }
            entries.put(requiredName, Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * Builds the detached dictionary.
         *
         * @return an immutable PDF dictionary
         */
        public PdfDictionary build() {
            return new PdfDictionary(new DetachedDictionaryAccess(entries));
        }
    }

    private static final class DetachedDictionaryAccess
            implements PdfDictionaryAccess {

        private final Map<PdfName, PdfValue> entries;
        private final List<PdfDictionaryEntry> orderedEntries;

        DetachedDictionaryAccess(Map<PdfName, PdfValue> source) {
            this.entries = new LinkedHashMap<PdfName, PdfValue>(source);
            this.orderedEntries = new ArrayList<PdfDictionaryEntry>(
                    source.size());
            for (Map.Entry<PdfName, PdfValue> entry : source.entrySet()) {
                orderedEntries.add(new PdfDictionaryEntry(
                        entry.getKey(),
                        entry.getValue()));
            }
        }

        @Override
        public int size() {
            return entries.size();
        }

        @Override
        public PdfValue get(PdfName name) {
            return entries.get(name);
        }

        @Override
        public PdfDictionaryEntry getEntry(int index) {
            return orderedEntries.get(index);
        }
    }
}

interface PdfDictionaryAccess {

    int size() throws DocumentFailure;

    PdfValue get(PdfName name) throws DocumentFailure;

    PdfDictionaryEntry getEntry(int index) throws DocumentFailure;
}
