package net.zerocloud.pdf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.function.Supplier;

/** Allocation-bounded primitives shared by the explicit Worker codecs. */
final class WorkerCodecIO {

    private static final int JAVA_SERIALIZATION_MAGIC = 0xaced;
    private static final long DECIMAL_OBJECT_OVERHEAD_BYTES = 256L;
    private static final long DECIMAL_BYTES_PER_CHARACTER = 8L;
    static final long DECODED_COLLECTION_ENTRY_BYTES = 64L;

    private WorkerCodecIO() {
    }

    interface Encoder {
        void encode(Output output) throws IOException, DocumentFailure;
    }

    static byte[] encode(int maximumBytes, Encoder encoder)
            throws DocumentFailure {
        BoundedOutputStream bytes = new BoundedOutputStream(maximumBytes);
        Output output = new Output(bytes, bytes, null);
        try {
            encoder.encode(output);
            output.flush();
            return bytes.toByteArray();
        } catch (MessageLimitException failure) {
            throw messageLimitFailure();
        } catch (IOException failure) {
            throw workerFailure(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    "A Worker message could not be encoded.");
        } finally {
            bytes.clear();
        }
    }

    static byte[] encode(
            WorkflowResourceContext resources,
            int maximumBytes,
            Encoder encoder) throws DocumentFailure {
        if (resources == null) {
            throw new NullPointerException("resources");
        }
        try (WorkflowResourceContext.OwnedByteAccumulator bytes =
                resources.ownedByteAccumulator()) {
            BoundedForwardingOutputStream bounded =
                    new BoundedForwardingOutputStream(bytes, maximumBytes);
            Output output = new Output(bounded, bounded, resources);
            try {
                encoder.encode(output);
                output.flush();
                return bytes.finishRetained();
            } catch (MessageLimitException failure) {
                throw messageLimitFailure();
            } catch (IOException failure) {
                DocumentFailure resourceFailure =
                        WorkflowResourceContext.findResourceFailure(failure);
                if (resourceFailure != null) {
                    throw resourceFailure;
                }
                throw workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "A Worker message could not be encoded.");
            }
        }
    }

    static void clearRetained(
            WorkflowResourceContext resources,
            byte[] payload) {
        Arrays.fill(payload, (byte) 0);
        resources.releaseRetainedOwnedMemory(payload.length);
    }

    static Input input(byte[] payload) throws DocumentFailure {
        if (payload.length >= 2
                && (((payload[0] & 0xff) << 8) | (payload[1] & 0xff))
                        == JAVA_SERIALIZATION_MAGIC) {
            throw workerFailure(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    "Java object serialization is not accepted by the Worker.");
        }
        return new Input(payload, null, null, -1L);
    }

    static Input boundedInput(
            byte[] payload,
            long maximumOwnedMemoryBytes) throws DocumentFailure {
        rejectSerialization(payload);
        if (maximumOwnedMemoryBytes < 0L
                || payload.length > maximumOwnedMemoryBytes) {
            throw workerFailure(
                    DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                    "The workflow owned-memory limit was exceeded.");
        }
        return new Input(
                payload,
                null,
                null,
                maximumOwnedMemoryBytes);
    }

    static Input accountedInput(
            byte[] payload,
            WorkflowResourceContext resources) throws DocumentFailure {
        rejectSerialization(payload);
        return new Input(payload, null, resources, -1L);
    }

    static Input retainedInput(
            byte[] payload,
            WorkflowResourceContext resources) throws DocumentFailure {
        try {
            rejectSerialization(payload);
            return new Input(payload, resources, resources, -1L);
        } catch (DocumentFailure | RuntimeException | Error failure) {
            clearRetained(resources, payload);
            throw failure;
        }
    }

    private static void rejectSerialization(byte[] payload)
            throws DocumentFailure {
        if (payload.length >= 2
                && (((payload[0] & 0xff) << 8) | (payload[1] & 0xff))
                        == JAVA_SERIALIZATION_MAGIC) {
            throw workerFailure(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    "Java object serialization is not accepted by the Worker.");
        }
    }

    static DocumentFailure workerFailure(
            DocumentFailureCode code,
            String diagnostic) {
        return new DocumentFailure(
                code,
                HardenedWorkerEngine.CAPABILITY_ID,
                diagnostic);
    }

    static DocumentFailure messageLimitFailure() {
        return workerFailure(
                DocumentFailureCode.WORKER_MESSAGE_LIMIT_EXCEEDED,
                "The Worker message-size limit was exceeded.");
    }

    static IOException transportedFailure(DocumentFailure failure) {
        return new TransportedFailureIOException(failure);
    }

    static DocumentFailure findTransportedFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof TransportedFailureIOException) {
                return ((TransportedFailureIOException) current).failure;
            }
            current = current.getCause();
        }
        return null;
    }

    private static DocumentFailure nestingLimitFailure() {
        return workerFailure(
                DocumentFailureCode.NESTING_LIMIT_EXCEEDED,
                "The workflow nesting-depth limit was exceeded.");
    }

    private static final class TransportedFailureIOException
            extends IOException {

        private static final long serialVersionUID = 1L;
        private final DocumentFailure failure;

        private TransportedFailureIOException(DocumentFailure failure) {
            super("A demand-driven Worker value could not be transported.",
                    failure);
            this.failure = failure;
        }
    }

    static final class Output {

        private final DataOutputStream output;
        private final CapacityBound capacity;
        private final WorkflowResourceContext resources;

        private Output(
                OutputStream bytes,
                CapacityBound capacity,
                WorkflowResourceContext resources) {
            output = new DataOutputStream(bytes);
            this.capacity = capacity;
            this.resources = resources;
        }

        void writeBoolean(boolean value) throws IOException {
            output.writeBoolean(value);
        }

        void writeByte(int value) throws IOException {
            output.writeByte(value);
        }

        void writeShort(int value) throws IOException {
            output.writeShort(value);
        }

        void writeInt(int value) throws IOException {
            output.writeInt(value);
        }

        void writeLong(long value) throws IOException {
            output.writeLong(value);
        }

        void writeDouble(double value) throws IOException {
            output.writeDouble(value);
        }

        void writeString(String value) throws IOException {
            if (value.length() > Integer.MAX_VALUE / 2) {
                throw new MessageLimitException();
            }
            capacity.requireCapacity(4L + 2L * value.length());
            output.writeInt(value.length() * 2);
            for (int index = 0; index < value.length(); index++) {
                output.writeChar(value.charAt(index));
            }
        }

        void writeNullableString(String value) throws IOException {
            writeBoolean(value != null);
            if (value != null) {
                writeString(value);
            }
        }

        void writeBytes(byte[] value) throws IOException {
            writeInt(value.length);
            output.write(value);
        }

        void writeFile(java.nio.file.Path file) throws IOException, DocumentFailure {
            long length = java.nio.file.Files.size(file);
            if (length > Integer.MAX_VALUE) { throw new MessageLimitException(); }
            capacity.requireCapacity(4L + length);
            try (WorkflowResourceContext.MemoryReservation memory =
                    resources.reserveOwnedMemory(8192);
                    java.io.InputStream source = java.nio.file.Files.newInputStream(file)) {
                writeInt((int) length);
                byte[] buffer = new byte[8192];
                long remaining = length;
                while (remaining > 0) {
                    resources.checkpoint();
                    int count = source.read(buffer, 0, (int) Math.min(remaining, buffer.length));
                    if (count < 0) { throw new EOFException(); }
                    output.write(buffer, 0, count);
                    remaining -= count;
                }
                if (source.read() != -1) { throw new IOException("Staging length changed"); }
            }
        }

        void writeBytes(byte[] value, int offset, int length)
                throws IOException {
            if (offset < 0 || length < 0
                    || offset > value.length - length) {
                throw new IndexOutOfBoundsException();
            }
            writeInt(length);
            output.write(value, offset, length);
        }

        void writeNullableBytes(byte[] value) throws IOException {
            writeBoolean(value != null);
            if (value != null) {
                writeBytes(value);
            }
        }

        void writeDefensiveBytes(
                int expectedLength,
                Supplier<byte[]> supplier) throws IOException {
            if (expectedLength < 0) {
                throw new IllegalArgumentException(
                        "expectedLength must not be negative");
            }
            capacity.requireCapacity(4L + expectedLength);
            WorkflowResourceContext.MemoryReservation reservation =
                    resources == null ? null
                    : resources.reserveOwnedMemoryAsIOException(
                            expectedLength);
            byte[] value = null;
            try {
                value = supplier.get();
                if (value.length != expectedLength) {
                    throw new IOException(
                            "A defensive byte value changed while encoding.");
                }
                writeBytes(value);
            } finally {
                if (value != null) {
                    Arrays.fill(value, (byte) 0);
                }
                if (reservation != null) {
                    reservation.close();
                }
            }
        }

        WorkflowResourceContext resources() {
            return resources;
        }

        void requireCapacity(long bytes) throws IOException {
            capacity.requireCapacity(bytes);
        }

        void requireNestingDepth(long depth) throws DocumentFailure {
            if (resources != null) {
                resources.requireNestingDepth(depth);
            } else if (depth
                    > WorkflowResourcePolicy.MAXIMUM_NESTING_DEPTH_VERSION_1) {
                throw nestingLimitFailure();
            }
        }

        void writeBigDecimal(BigDecimal value) throws IOException {
            long characters = PdfBoxValueAdapter.plainStringLength(value);
            if (characters > (Integer.MAX_VALUE - 4L) / 2L) {
                throw new MessageLimitException();
            }
            capacity.requireCapacity(4L + 2L * characters);
            WorkflowResourceContext.MemoryReservation serialization =
                    resources == null ? null
                    : resources.reserveOwnedMemoryAsIOException(
                            2L * characters);
            try {
                String lexical = value.toPlainString();
                if (lexical.length() != (int) characters) {
                    throw new IOException(
                            "A decimal value changed while encoding.");
                }
                writeString(lexical);
            } finally {
                if (serialization != null) {
                    serialization.close();
                }
            }
        }

        void flush() throws IOException {
            output.flush();
        }
    }

    static final class Input {

        private final byte[] payload;
        private final WorkflowResourceContext retainedBy;
        private final WorkflowResourceContext decodedBy;
        private final long maximumStandaloneMemory;
        private final ByteArrayInputStream bytes;
        private final DataInputStream input;
        private long standaloneMemory;
        private long standaloneRetainedMemory;
        private long decodedMemory;
        private boolean cleared;

        private Input(
                byte[] payload,
                WorkflowResourceContext retainedBy,
                WorkflowResourceContext decodedBy,
                long maximumStandaloneMemory) {
            this.payload = payload;
            this.retainedBy = retainedBy;
            this.decodedBy = decodedBy;
            this.maximumStandaloneMemory = maximumStandaloneMemory;
            this.standaloneMemory = maximumStandaloneMemory < 0L
                    ? 0L : payload.length;
            bytes = new ByteArrayInputStream(payload);
            input = new DataInputStream(bytes);
        }

        void requireNestingDepth(long depth) throws DocumentFailure {
            if (decodedBy != null) {
                decodedBy.requireNestingDepth(depth);
            } else if (depth
                    > WorkflowResourcePolicy.MAXIMUM_NESTING_DEPTH_VERSION_1) {
                throw nestingLimitFailure();
            }
        }

        boolean readBoolean() throws DocumentFailure {
            try {
                int value = input.readUnsignedByte();
                if (value > 1) {
                    throw workerFailure(
                            DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                            "A Worker boolean value is invalid.");
                }
                return value == 1;
            } catch (IOException failure) {
                throw truncated();
            }
        }

        byte readByte() throws DocumentFailure {
            try {
                return input.readByte();
            } catch (IOException failure) {
                throw truncated();
            }
        }

        short readShort() throws DocumentFailure {
            try {
                return input.readShort();
            } catch (IOException failure) {
                throw truncated();
            }
        }

        int readInt() throws DocumentFailure {
            try {
                return input.readInt();
            } catch (IOException failure) {
                throw truncated();
            }
        }

        long readLong() throws DocumentFailure {
            try {
                return input.readLong();
            } catch (IOException failure) {
                throw truncated();
            }
        }

        double readDouble() throws DocumentFailure {
            try {
                return input.readDouble();
            } catch (IOException failure) {
                throw truncated();
            }
        }

        String readString() throws DocumentFailure {
            int length = readLength();
            if ((length & 1) != 0) {
                throw workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "A Worker string representation is invalid.");
            }
            WorkflowResourceContext.MemoryReservation characterReservation =
                    reserveDecoded(length);
            WorkflowResourceContext.MemoryReservation stringReservation = null;
            char[] characters = null;
            try {
                stringReservation = reserveDecoded(length);
                characters = new char[length / 2];
                for (int index = 0; index < characters.length; index++) {
                    characters[index] = (char) (readShort() & 0xffff);
                }
                String value = new String(characters);
                if (stringReservation != null) {
                    stringReservation.transfer();
                    retainDecodedReservation(length);
                } else if (maximumStandaloneMemory >= 0L) {
                    retainStandaloneDecoded(length);
                }
                return value;
            } finally {
                if (characters != null) {
                    Arrays.fill(characters, '\0');
                }
                closeReservation(characterReservation);
                closeReservation(stringReservation);
            }
        }

        String readNullableString() throws DocumentFailure {
            return readBoolean() ? readString() : null;
        }

        byte[] readBytes() throws DocumentFailure {
            return readBytes(1);
        }

        byte[] readBytes(int simultaneousCopies) throws DocumentFailure {
            if (simultaneousCopies < 1) {
                throw new IllegalArgumentException(
                        "simultaneousCopies must be positive");
            }
            int length = readLength();
            long decodedAmount = (long) length * simultaneousCopies;
            if (decodedBy != null) {
                decodedBy.retainOwnedMemory(decodedAmount);
            } else {
                reserveStandalone(decodedAmount);
            }
            try {
                byte[] result = readArray(length);
                retainDecodedReservation(decodedAmount);
                if (decodedBy == null && maximumStandaloneMemory >= 0L) {
                    retainStandaloneDecoded(decodedAmount);
                }
                return result;
            } catch (DocumentFailure | RuntimeException | Error failure) {
                if (decodedBy != null) {
                    decodedBy.releaseRetainedOwnedMemory(decodedAmount);
                }
                throw failure;
            }
        }

        int readBytesInto(byte[] target, int offset, int maximumLength)
                throws DocumentFailure {
            if (offset < 0 || maximumLength < 0
                    || offset > target.length - maximumLength) {
                throw new IndexOutOfBoundsException();
            }
            int length = readLength();
            if (length > maximumLength) {
                throw workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "A Worker byte value exceeds its requested bound.");
            }
            try {
                input.readFully(target, offset, length);
                return length;
            } catch (IOException failure) {
                throw truncated();
            }
        }

        byte[] readNullableBytes() throws DocumentFailure {
            return readBoolean() ? readBytes() : null;
        }

        byte[] readNullableBytes(int simultaneousCopies)
                throws DocumentFailure {
            return readBoolean() ? readBytes(simultaneousCopies) : null;
        }

        WorkflowResourceContext.OwnedBytes readWorkingBytes()
                throws DocumentFailure {
            int length = readLength();
            WorkflowResourceContext.MemoryReservation reservation =
                    reserveDecoded(length);
            try {
                return new WorkflowResourceContext.OwnedBytes(
                        readArray(length),
                        reservation);
            } catch (DocumentFailure | RuntimeException | Error failure) {
                closeReservation(reservation);
                throw failure;
            }
        }

        void readFile(java.nio.file.Path file, WorkflowResourceContext resources)
                throws DocumentFailure {
            int remaining = readLength();
            try (WorkflowResourceContext.MemoryReservation memory =
                    resources.reserveOwnedMemory(8192);
                    OutputStream target = resources.openTemporaryOutput(file)) {
                byte[] buffer = new byte[8192];
                while (remaining > 0) {
                    resources.checkpoint();
                    int count = Math.min(remaining, buffer.length);
                    input.readFully(buffer, 0, count);
                    target.write(buffer, 0, count);
                    remaining -= count;
                }
            } catch (IOException failure) {
                resources.rethrowResourceOrTerminalFailure(failure);
                throw truncated();
            }
        }

        BigDecimal readBigDecimal() throws DocumentFailure {
            String value = readString();
            requirePlainDecimal(value);
            accountDecodedMemory(decodedBigDecimalMemoryBytes(
                    value.length()));
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException failure) {
                throw workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "A Worker decimal value is invalid.");
            }
        }

        private static void requirePlainDecimal(String value)
                throws DocumentFailure {
            int index = 0;
            boolean negative = false;
            if (!value.isEmpty() && value.charAt(0) == '-') {
                negative = true;
                index++;
            }
            int integerStart = index;
            while (index < value.length()
                    && value.charAt(index) >= '0'
                    && value.charAt(index) <= '9') {
                index++;
            }
            int integerDigits = index - integerStart;
            if (integerDigits == 0
                    || (integerDigits > 1
                            && value.charAt(integerStart) == '0')) {
                throw invalidDecimal();
            }
            if (index < value.length() && value.charAt(index) == '.') {
                int fractionStart = ++index;
                while (index < value.length()
                        && value.charAt(index) >= '0'
                        && value.charAt(index) <= '9') {
                    index++;
                }
                if (index == fractionStart) {
                    throw invalidDecimal();
                }
            }
            if (index != value.length()) {
                throw invalidDecimal();
            }
            if (negative) {
                boolean nonzero = false;
                for (int digit = integerStart; digit < value.length(); digit++) {
                    char current = value.charAt(digit);
                    nonzero |= current >= '1' && current <= '9';
                }
                if (!nonzero) {
                    throw invalidDecimal();
                }
            }
        }

        private static DocumentFailure invalidDecimal() {
            return workerFailure(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    "A Worker decimal value is invalid.");
        }

        int available() {
            return bytes.available();
        }

        void requireFullyConsumed() throws DocumentFailure {
            if (bytes.available() != 0) {
                throw workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "A Worker message contains trailing data.");
            }
        }

        void clear() {
            if (cleared) {
                return;
            }
            cleared = true;
            Arrays.fill(payload, (byte) 0);
            if (retainedBy != null) {
                retainedBy.releaseRetainedOwnedMemory(payload.length);
            }
        }

        void releaseDecodedMemory() {
            if (decodedBy != null && decodedMemory != 0L) {
                long amount = decodedMemory;
                decodedMemory = 0L;
                decodedBy.releaseRetainedOwnedMemory(amount);
            }
        }

        long transferDecodedMemory() {
            if (decodedBy == null) {
                throw new IllegalStateException(
                        "Standalone decoded memory cannot be transferred.");
            }
            long amount = decodedMemory;
            decodedMemory = 0L;
            return amount;
        }

        void accountDecodedMemory(long amount) throws DocumentFailure {
            if (decodedBy != null) {
                decodedBy.retainOwnedMemory(amount);
                decodedMemory += amount;
            } else {
                reserveStandalone(amount);
            }
        }

        void accountCollectionEntries(int count) throws DocumentFailure {
            if (count < 0) {
                throw new IllegalArgumentException(
                        "count must not be negative");
            }
            // Covers reference/primitive backing arrays and their fixed
            // headers before any capacity-bearing collection is created.
            long amount = DECODED_COLLECTION_ENTRY_BYTES * count;
            accountDecodedMemory(amount);
            if (decodedBy == null && maximumStandaloneMemory >= 0L) {
                retainStandaloneDecoded(amount);
            }
        }

        long getStandaloneRetainedMemory() {
            return standaloneRetainedMemory;
        }

        private static DocumentFailure truncated() {
            return workerFailure(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    "A Worker value is truncated.");
        }

        private static DocumentFailure invalidLength() {
            return workerFailure(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    "A Worker value length is invalid.");
        }

        private int readLength() throws DocumentFailure {
            int length = readInt();
            if (length < 0 || length > bytes.available()) {
                throw invalidLength();
            }
            return length;
        }

        private byte[] readArray(int length) throws DocumentFailure {
            byte[] value = new byte[length];
            try {
                input.readFully(value);
                return value;
            } catch (EOFException failure) {
                Arrays.fill(value, (byte) 0);
                throw truncated();
            } catch (IOException failure) {
                Arrays.fill(value, (byte) 0);
                throw truncated();
            }
        }

        private WorkflowResourceContext.MemoryReservation reserveDecoded(
                long amount) throws DocumentFailure {
            if (decodedBy != null) {
                return decodedBy.reserveOwnedMemory(amount);
            }
            reserveStandalone(amount);
            return null;
        }

        private void retainDecodedReservation(long amount) {
            if (decodedBy != null) {
                decodedMemory += amount;
            }
        }

        private void reserveStandalone(long amount) throws DocumentFailure {
            if (maximumStandaloneMemory < 0L) {
                return;
            }
            if (amount < 0L
                    || standaloneMemory > maximumStandaloneMemory - amount) {
                throw workerFailure(
                        DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                        "The workflow owned-memory limit was exceeded.");
            }
            standaloneMemory += amount;
        }

        private void retainStandaloneDecoded(long amount)
                throws DocumentFailure {
            if (amount < 0L
                    || standaloneRetainedMemory > Long.MAX_VALUE - amount) {
                throw workerFailure(
                        DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                        "The workflow owned-memory limit was exceeded.");
            }
            standaloneRetainedMemory += amount;
        }

        private static void closeReservation(
                WorkflowResourceContext.MemoryReservation reservation) {
            if (reservation != null) {
                reservation.close();
            }
        }
    }

    static long decodedBigDecimalMemoryBytes(int characterCount) {
        if (characterCount < 0) {
            throw new IllegalArgumentException(
                    "characterCount must not be negative");
        }
        return DECIMAL_OBJECT_OVERHEAD_BYTES
                + DECIMAL_BYTES_PER_CHARACTER * characterCount;
    }

    private interface CapacityBound {
        void requireCapacity(long additional);
    }

    private static final class BoundedOutputStream
            extends ByteArrayOutputStream implements CapacityBound {

        private final int maximumBytes;

        private BoundedOutputStream(int maximumBytes) {
            super(Math.min(maximumBytes, 1024));
            if (maximumBytes < 0) {
                throw new IllegalArgumentException(
                        "maximumBytes must not be negative");
            }
            this.maximumBytes = maximumBytes;
        }

        @Override
        public synchronized void write(int value) {
            requireCapacity(1);
            super.write(value);
        }

        @Override
        public synchronized void write(byte[] value, int offset, int length) {
            if (value == null
                    || offset < 0
                    || length < 0
                    || offset > value.length - length) {
                throw new IndexOutOfBoundsException();
            }
            requireCapacity(length);
            super.write(value, offset, length);
        }

        @Override
        public void requireCapacity(long additional) {
            if (additional > maximumBytes - count) {
                throw new MessageLimitException();
            }
        }

        private void clear() {
            Arrays.fill(buf, (byte) 0);
            reset();
        }
    }

    private static final class BoundedForwardingOutputStream
            extends OutputStream implements CapacityBound {

        private final OutputStream output;
        private final int maximumBytes;
        private int count;

        private BoundedForwardingOutputStream(
                OutputStream output,
                int maximumBytes) {
            if (maximumBytes < 0) {
                throw new IllegalArgumentException(
                        "maximumBytes must not be negative");
            }
            this.output = output;
            this.maximumBytes = maximumBytes;
        }

        @Override
        public void write(int value) throws IOException {
            requireCapacity(1);
            output.write(value);
            count++;
        }

        @Override
        public void write(byte[] value, int offset, int length)
                throws IOException {
            if (value == null
                    || offset < 0
                    || length < 0
                    || offset > value.length - length) {
                throw new IndexOutOfBoundsException();
            }
            requireCapacity(length);
            output.write(value, offset, length);
            count += length;
        }

        @Override
        public void requireCapacity(long additional) {
            if (additional > maximumBytes - count) {
                throw new MessageLimitException();
            }
        }
    }

    static final class MessageLimitException
            extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }
}
