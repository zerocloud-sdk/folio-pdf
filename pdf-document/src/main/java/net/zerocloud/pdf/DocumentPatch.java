package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Arrays;
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

    /** The original dictionary-entry Patch representation. */
    public static final int VERSION_1 = 1;

    /** The extended Patch representation supporting container and stream edits. */
    public static final int VERSION_2 = 2;

    enum Operation {
        SET_DICTIONARY_ENTRY(1, false, true),
        REMOVE_DICTIONARY_ENTRY(2, false, false),
        SET_ARRAY_ELEMENT(3, true, true),
        INSERT_ARRAY_ELEMENT(4, true, true),
        REMOVE_ARRAY_ELEMENT(5, true, false),
        REPLACE_VALUE(6, false, true),
        REPLACE_STREAM_DATA(7, false, false);

        private final int wireId;
        private final boolean array;
        private final boolean value;

        Operation(int wireId, boolean array, boolean value) {
            this.wireId = wireId;
            this.array = array;
            this.value = value;
        }

        int getWireId() {
            return wireId;
        }

        boolean targetsArray() {
            return array;
        }

        boolean hasValue() {
            return value;
        }

        static Operation fromWireId(int wireId) throws DocumentFailure {
            for (Operation operation : values()) {
                if (operation.wireId == wireId) {
                    return operation;
                }
            }
            throw new DocumentFailure(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    HardenedWorkerEngine.CAPABILITY_ID,
                    "A Worker Document Patch operation is unsupported.");
        }
    }

    private final List<Change> changes;
    private final int version;

    private DocumentPatch(Builder builder) {
        this.version = builder.version;
        this.changes = Collections.unmodifiableList(
                new ArrayList<Change>(builder.changes));
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
     * @return the version required by the requested operations
     */
    public int getVersion() {
        return version;
    }

    List<Change> getChanges() {
        return changes;
    }

    static final class Change {

        private final Operation operation;
        private final PdfValuePath path;
        private final PdfName name;
        private final int index;
        private final PdfValue value;
        private final byte[] streamData;
        private final PdfStreamEncoding streamEncoding;

        Change(
                Operation operation,
                PdfValuePath path,
                PdfName name,
                int index,
                PdfValue value) {
            this(operation, path, name, index, value, null, null);
        }

        Change(PdfValuePath path, byte[] decodedBytes, PdfStreamEncoding encoding) {
            this(Operation.REPLACE_STREAM_DATA, path, null, 0, null,
                    Arrays.copyOf(decodedBytes, decodedBytes.length), encoding);
        }

        private Change(Operation operation, PdfValuePath path, PdfName name, int index,
                PdfValue value, byte[] streamData, PdfStreamEncoding streamEncoding) {
            this.operation = operation;
            this.path = path;
            this.name = name;
            this.index = index;
            this.value = value;
            this.streamData = streamData;
            this.streamEncoding = streamEncoding;
        }

        ObjectReference getTarget() {
            return path.getRoot();
        }

        Operation getOperation() {
            return operation;
        }

        int getIndex() {
            return index;
        }

        PdfValuePath getPath() {
            return path;
        }

        PdfName getName() {
            return name;
        }

        PdfValue getValue() {
            return value;
        }

        byte[] getStreamData() {
            return streamData;
        }

        PdfStreamEncoding getStreamEncoding() {
            return streamEncoding;
        }
    }

    /**
     * Builds one immutable ordered Patch.
     *
     * @since 0.1.0
     */
    public static final class Builder {

        private final List<Change> changes = new ArrayList<Change>();
        private int version = VERSION_1;

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
            changes.add(new Change(Operation.SET_DICTIONARY_ENTRY,
                    PdfValuePath.root(Objects.requireNonNull(dictionary, "dictionary")),
                    Objects.requireNonNull(name, "name"),
                    0,
                    Objects.requireNonNull(value, "value")));
            return this;
        }

        /**
         * Requests a dictionary insertion or replacement at a nested location.
         * The location observes all earlier changes in this Patch.
         *
         * @param dictionary the dictionary location
         * @param name the entry name
         * @param value the backend-neutral replacement
         * @return this builder
         */
        public Builder setDictionaryEntry(
                PdfValuePath dictionary,
                PdfName name,
                PdfValue value) {
            changes.add(new Change(Operation.SET_DICTIONARY_ENTRY,
                    Objects.requireNonNull(dictionary, "dictionary"),
                    Objects.requireNonNull(name, "name"),
                    0,
                    Objects.requireNonNull(value, "value")));
            version = VERSION_2;
            return this;
        }

        /**
         * Requests removal of a dictionary entry. An absent entry is unchanged.
         * This operation requires the version-2 Patch representation.
         *
         * @param dictionary the Session-owned target dictionary
         * @param name the entry name
         * @return this builder
         */
        public Builder removeDictionaryEntry(
                ObjectReference dictionary,
                PdfName name) {
            return removeDictionaryEntry(PdfValuePath.root(
                    Objects.requireNonNull(dictionary, "dictionary")), name);
        }

        /**
         * Requests removal of an entry at a nested dictionary location.
         *
         * @param dictionary the dictionary location
         * @param name the entry name
         * @return this builder
         */
        public Builder removeDictionaryEntry(
                PdfValuePath dictionary,
                PdfName name) {
            changes.add(new Change(Operation.REMOVE_DICTIONARY_ENTRY,
                    Objects.requireNonNull(dictionary, "dictionary"),
                    Objects.requireNonNull(name, "name"),
                    0,
                    null));
            version = VERSION_2;
            return this;
        }

        /**
         * Replaces one array element, preserving its position and other values.
         * The Session validates the index against the preceding Patch changes.
         *
         * @param array the array location
         * @param index the zero-based element index
         * @param value the replacement value
         * @return this builder
         */
        public Builder setArrayElement(PdfValuePath array, int index, PdfValue value) {
            changes.add(new Change(Operation.SET_ARRAY_ELEMENT,
                    Objects.requireNonNull(array, "array"), null, index,
                    Objects.requireNonNull(value, "value")));
            version = VERSION_2;
            return this;
        }

        /**
         * Inserts a value before an array position, shifting later elements.
         * The index may equal the array size to append a value.
         *
         * @param array the array location
         * @param index the zero-based insertion position
         * @param value the value to insert
         * @return this builder
         */
        public Builder insertArrayElement(PdfValuePath array, int index, PdfValue value) {
            changes.add(new Change(Operation.INSERT_ARRAY_ELEMENT,
                    Objects.requireNonNull(array, "array"), null, index,
                    Objects.requireNonNull(value, "value")));
            version = VERSION_2;
            return this;
        }

        /**
         * Removes one array element and shifts later elements toward zero.
         *
         * @param array the array location
         * @param index the zero-based element index
         * @return this builder
         */
        public Builder removeArrayElement(PdfValuePath array, int index) {
            changes.add(new Change(Operation.REMOVE_ARRAY_ELEMENT,
                    Objects.requireNonNull(array, "array"), null, index, null));
            version = VERSION_2;
            return this;
        }

        /**
         * Replaces a value at a dictionary entry, array element or indirect root.
         * Root replacement preserves the Session reference and all its aliases.
         * A missing dictionary entry is inserted; array indices must exist.
         * An indirect root requires a direct value as its object body; reference
         * values may instead be stored in dictionary entries or array elements.
         *
         * @param location the value location
         * @param value the replacement value
         * @return this builder
         */
        public Builder replaceValue(PdfValuePath location, PdfValue value) {
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(value, "value");
            List<PdfValuePath.Step> steps = location.getSteps();
            if (!steps.isEmpty()) {
                PdfValuePath.Step last = steps.get(steps.size() - 1);
                return last.getName() == null
                        ? setArrayElement(location.parent(), last.getIndex(), value)
                        : setDictionaryEntry(location.parent(), last.getName(), value);
            }
            changes.add(new Change(Operation.REPLACE_VALUE, location, null, 0, value));
            version = VERSION_2;
            return this;
        }

        /**
         * Replaces a stream's decoded data, preserving its reference and custom
         * attributes. The engine updates Length, Filter and decoding parameters
         * together. The supplied byte array is copied when added to the Patch.
         *
         * @param stream the stream location, resolved after preceding changes
         * @param decodedBytes the replacement decoded data
         * @param encoding the engine-owned output encoding
         * @return this builder
         */
        public Builder replaceStreamData(PdfValuePath stream, byte[] decodedBytes, PdfStreamEncoding encoding) {
            changes.add(new Change(Objects.requireNonNull(stream, "stream"),
                    Objects.requireNonNull(decodedBytes, "decodedBytes"),
                    Objects.requireNonNull(encoding, "encoding")));
            version = VERSION_2;
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
