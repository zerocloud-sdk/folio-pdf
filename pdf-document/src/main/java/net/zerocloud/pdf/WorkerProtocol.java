package net.zerocloud.pdf;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Version-1 authenticated, bounded framing for one local Worker process. */
final class WorkerProtocol {

    static final int MAGIC = 0x46505731; // FPW1
    static final short VERSION_1 = 1;
    static final int AUTHENTICATION_KEY_BYTES = 32;
    static final int AUTHENTICATION_TAG_BYTES = 32;
    private static final short TRANSFER_START = -1;
    private static final short TRANSFER_CHUNK = -2;
    private static final int TRANSFER_ID_BYTES = 16;
    private static final int TRANSFER_START_BYTES = 58;
    private static final int TRANSFER_CHUNK_HEADER_BYTES = 20;
    private static final byte[] EMPTY_PAYLOAD = new byte[0];

    static final short INITIALIZE = 1;
    static final short COMMAND_BATCH = 2;
    static final short QUERY = 3;
    static final short FINISH = 4;
    static final short ABORT = 5;
    static final short NETWORK_PROBE = 6;
    static final short VALUE_VIEW = 7;
    static final short COMMAND_ITEM = 8;
    static final short COMMAND_BATCH_ABORT = 9;
    static final short PROGRESS_ACKNOWLEDGED = 10;
    static final short SOURCE_MATERIALIZED = 11;
    static final short CREDENTIAL_MATERIALIZED = 12;
    static final short FONT_VALUE = 13;
    static final short FONT_VALUE_FAILED = 14;
    static final short COMMAND_PREFLIGHT = 15;
    static final short COMMANDS = 16;
    static final short QUERY_PREFLIGHT = 17;
    static final short COMMANDS_OFFER = 18;
    static final short COMMAND_PREFLIGHT_DETAILS = 19;
    static final short MEMORY_RESERVE = 20;
    static final short MEMORY_RELEASE = 21;
    static final short MEMORY_SYNCHRONIZE = 22;
    static final short MALFORMED_RESPONSE_PROBE = 23;
    static final short TEMPORARY_RESERVE = 24;
    static final short TEMPORARY_RELEASE = 25;

    static final short READY = 101;
    static final short COMMAND_COMPLETED = 102;
    static final short QUERY_COMPLETED = 103;
    static final short FINISHED = 104;
    static final short FAILURE = 105;
    static final short NETWORK_PROBE_COMPLETED = 106;
    static final short VALUE_VIEW_COMPLETED = 107;
    static final short PROGRESS = 108;
    static final short COMMAND_REQUIRED = 109;
    static final short COMMAND_BATCH_ABORTED = 110;
    static final short SOURCE_REQUIRED = 111;
    static final short CREDENTIAL_REQUIRED = 112;
    static final short FONT_REQUIRED = 113;
    static final short COMMAND_PREFLIGHT_REQUIRED = 114;
    static final short QUERY_PREFLIGHT_COMPLETED = 115;
    static final short COMMANDS_ACCEPTED = 116;
    static final short COMMANDS_DEFERRED = 117;
    static final short COMMAND_PREFLIGHT_DETAILS_REQUIRED = 118;
    static final short MEMORY_GRANTED = 119;
    static final short MEMORY_SYNCHRONIZED = 120;
    static final short MALFORMED_RESPONSE_COMPLETED = 121;
    static final short RESOURCE_USAGE = 122;
    static final short TEMPORARY_GRANTED = 123;

    private WorkerProtocol() {
    }

    static byte[] readAuthenticationKey(InputStream input)
            throws ProtocolException {
        byte[] key = new byte[AUTHENTICATION_KEY_BYTES];
        try {
            new DataInputStream(input).readFully(key);
            return key;
        } catch (IOException failure) {
            Arrays.fill(key, (byte) 0);
            throw new ProtocolException(
                    DocumentFailureCode.WORKER_AUTHENTICATION_FAILED,
                    "Worker authentication material is missing or truncated.");
        }
    }

    static void writeAuthenticationKey(OutputStream output, byte[] key)
            throws IOException {
        if (key == null || key.length != AUTHENTICATION_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "Worker authentication keys must contain 32 bytes.");
        }
        output.write(key);
        output.flush();
    }

    static Endpoint endpoint(
            InputStream input,
            OutputStream output,
            byte[] key,
            int maximumPayloadBytes) {
        return new Endpoint(
                input,
                output,
                key,
                maximumPayloadBytes,
                maximumPayloadBytes,
                maximumPayloadBytes,
                null);
    }

    static Endpoint endpoint(
            InputStream input,
            OutputStream output,
            byte[] key,
            int maximumPayloadBytes,
            long maximumUnaccountedPayloadBytes) {
        return new Endpoint(
                input,
                output,
                key,
                maximumPayloadBytes,
                maximumUnaccountedPayloadBytes,
                maximumUnaccountedPayloadBytes,
                null);
    }

    static Endpoint endpoint(
            InputStream input,
            OutputStream output,
            byte[] key,
            int maximumPayloadBytes,
            WorkflowResourceContext resources) {
        return new Endpoint(
                input,
                output,
                key,
                maximumPayloadBytes,
                maximumPayloadBytes,
                resources.getPolicy().getMaximumOwnedMemoryBytes(),
                resources);
    }

    static final class Endpoint implements AutoCloseable {

        private final DataInputStream input;
        private final DataOutputStream output;
        private final byte[] key;
        private final int maximumPayloadBytes;
        private final long maximumUnaccountedPayloadBytes;
        private final long maximumTransferMemoryBytes;
        private WorkflowResourceContext resources;
        private MemoryGrantConsumer memoryGrantConsumer;
        private long nextSendSequence = 1L;
        private long nextReceiveSequence = 1L;
        private boolean closed;

        private Endpoint(
                InputStream input,
                OutputStream output,
                byte[] key,
                int maximumPayloadBytes,
                long maximumUnaccountedPayloadBytes,
                long maximumTransferMemoryBytes,
                WorkflowResourceContext resources) {
            if (maximumPayloadBytes < 0) {
                throw new IllegalArgumentException(
                        "maximumPayloadBytes must not be negative");
            }
            if (key == null || key.length != AUTHENTICATION_KEY_BYTES) {
                throw new IllegalArgumentException(
                        "Worker authentication keys must contain 32 bytes.");
            }
            if (maximumUnaccountedPayloadBytes < 0L) {
                throw new IllegalArgumentException(
                        "maximumUnaccountedPayloadBytes must not be negative");
            }
            if (maximumTransferMemoryBytes < 0L) {
                throw new IllegalArgumentException(
                        "maximumTransferMemoryBytes must not be negative");
            }
            this.input = new DataInputStream(input);
            this.output = new DataOutputStream(output);
            this.key = key.clone();
            this.maximumPayloadBytes = maximumPayloadBytes;
            this.maximumUnaccountedPayloadBytes =
                    maximumUnaccountedPayloadBytes;
            this.maximumTransferMemoryBytes = maximumTransferMemoryBytes;
            this.resources = resources;
        }

        synchronized void accountWith(WorkflowResourceContext context) {
            if (resources != null && resources != context) {
                throw new IllegalStateException(
                        "Worker protocol accounting is already configured.");
            }
            resources = context;
        }

        synchronized void acceptMemoryGrantsWith(
                MemoryGrantConsumer consumer) {
            if (memoryGrantConsumer != null
                    && memoryGrantConsumer != consumer) {
                throw new IllegalStateException(
                        "Worker memory grants are already configured.");
            }
            memoryGrantConsumer = consumer;
        }

        synchronized long nextSendSequence() {
            return nextSendSequence;
        }

        synchronized void send(short opcode, byte[] payload)
                throws IOException, ProtocolException {
            requireOpen();
            byte[] requiredPayload = payload == null ? EMPTY_PAYLOAD : payload;
            if (requiredPayload.length <= maximumPayloadBytes) {
                sendFrame(opcode, requiredPayload);
                return;
            }
            if (isResourceControl(opcode)
                    || isTransferOpcode(opcode)
                    || maximumPayloadBytes < TRANSFER_START_BYTES
                    || maximumPayloadBytes <= TRANSFER_CHUNK_HEADER_BYTES
                    || requiredReceiveMemory(requiredPayload.length)
                            > maximumTransferMemoryBytes) {
                throw new ProtocolException(
                        DocumentFailureCode.WORKER_MESSAGE_LIMIT_EXCEEDED,
                        "The Worker message-size limit was exceeded.");
            }
            sendTransfer(opcode, requiredPayload);
        }

        synchronized long requiredReceiveMemory(int payloadLength) {
            if (payloadLength <= maximumPayloadBytes) {
                return payloadLength;
            }
            return payloadLength > Long.MAX_VALUE - maximumPayloadBytes
                    ? Long.MAX_VALUE
                    : (long) payloadLength + maximumPayloadBytes;
        }

        private void sendTransfer(short opcode, byte[] payload)
                throws IOException, ProtocolException {
            int chunkCapacity = maximumPayloadBytes
                    - TRANSFER_CHUNK_HEADER_BYTES;
            long requiredChunkCount = ((long) payload.length
                    + chunkCapacity - 1L) / chunkCapacity;
            if (requiredChunkCount > Integer.MAX_VALUE) {
                throw new ProtocolException(
                        DocumentFailureCode.WORKER_MESSAGE_LIMIT_EXCEEDED,
                        "The Worker transfer-size limit was exceeded.");
            }
            int chunkCount = (int) requiredChunkCount;
            long frameCount = 1L + chunkCount;
            if (nextSendSequence > Long.MAX_VALUE - frameCount) {
                throw new ProtocolException(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker frame sequence was exhausted.");
            }
            byte[] transferId = new byte[TRANSFER_ID_BYTES];
            writeLong(transferId, 0, nextSendSequence);
            writeLong(
                    transferId,
                    8,
                    ((long) opcode << 48) ^ (long) payload.length);
            byte[] digest = transferDigest(payload, chunkCapacity);
            byte[] start = new byte[TRANSFER_START_BYTES];
            writeShort(start, 0, opcode);
            System.arraycopy(
                    transferId,
                    0,
                    start,
                    2,
                    transferId.length);
            writeInt(start, 18, payload.length);
            writeInt(start, 22, chunkCount);
            System.arraycopy(digest, 0, start, 26, digest.length);
            try {
                sendFrame(TRANSFER_START, start);
                int offset = 0;
                for (int index = 0; index < chunkCount; index++) {
                    checkpointTransfer();
                    int count = Math.min(
                            chunkCapacity,
                            payload.length - offset);
                    sendChunkFrame(
                            transferId,
                            index,
                            payload,
                            offset,
                            count);
                    offset += count;
                }
            } finally {
                Arrays.fill(start, (byte) 0);
                Arrays.fill(digest, (byte) 0);
                Arrays.fill(transferId, (byte) 0);
            }
        }

        private byte[] transferDigest(byte[] payload, int chunkCapacity)
                throws ProtocolException {
            MessageDigest digest = sha256();
            int offset = 0;
            while (offset < payload.length) {
                checkpointTransfer();
                int count = Math.min(chunkCapacity, payload.length - offset);
                digest.update(payload, offset, count);
                offset += count;
            }
            return digest.digest();
        }

        private void checkpointTransfer() throws ProtocolException {
            if (resources == null) {
                return;
            }
            try {
                resources.checkpoint();
            } catch (DocumentFailure failure) {
                throw new ProtocolException(failure);
            }
        }

        private void sendFrame(short opcode, byte[] payload)
                throws IOException, ProtocolException {
            long sequence = nextSendSequence;
            if (sequence == Long.MAX_VALUE) {
                throw new ProtocolException(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker frame sequence was exhausted.");
            }
            byte[] tag = authenticate(
                    key,
                    VERSION_1,
                    opcode,
                    sequence,
                    payload);
            try {
                output.writeInt(MAGIC);
                output.writeShort(VERSION_1);
                output.writeShort(opcode);
                output.writeLong(sequence);
                output.writeInt(payload.length);
                output.write(payload);
                output.write(tag);
                output.flush();
                nextSendSequence = sequence + 1L;
            } finally {
                Arrays.fill(tag, (byte) 0);
            }
        }

        /** Writes a chunk from the retained logical payload without copying it. */
        private void sendChunkFrame(
                byte[] transferId,
                int index,
                byte[] payload,
                int offset,
                int count) throws IOException, ProtocolException {
            long sequence = nextSendSequence;
            if (sequence == Long.MAX_VALUE) {
                throw new ProtocolException(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker frame sequence was exhausted.");
            }
            int framePayloadLength = TRANSFER_CHUNK_HEADER_BYTES + count;
            byte[] tag = authenticateChunk(
                    key,
                    sequence,
                    transferId,
                    index,
                    payload,
                    offset,
                    count);
            try {
                output.writeInt(MAGIC);
                output.writeShort(VERSION_1);
                output.writeShort(TRANSFER_CHUNK);
                output.writeLong(sequence);
                output.writeInt(framePayloadLength);
                output.write(transferId);
                output.writeInt(index);
                output.write(payload, offset, count);
                output.write(tag);
                output.flush();
                nextSendSequence = sequence + 1L;
            } finally {
                Arrays.fill(tag, (byte) 0);
            }
        }

        synchronized Frame receive() throws IOException, ProtocolException {
            while (true) {
                Frame frame = receiveFrame();
                if (frame.getOpcode() == TRANSFER_START) {
                    frame = receiveTransfer(frame);
                } else if (frame.getOpcode() == TRANSFER_CHUNK) {
                    frame.clear();
                    throw new ProtocolException(
                            DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                            "The Worker transfer sequence is invalid.");
                }
                if (memoryGrantConsumer == null
                        || frame.getOpcode() != MEMORY_GRANTED) {
                    if (memoryGrantConsumer != null) {
                        try {
                            memoryGrantConsumer.requireMemoryGrantConsumed();
                        } catch (DocumentFailure failure) {
                            frame.clear();
                            throw new ProtocolException(failure);
                        }
                    }
                    return frame;
                }
                try {
                    memoryGrantConsumer.acceptMemoryGrant(
                            WorkerMessages.decodeMemoryAmount(
                                    frame.getPayload()));
                } catch (DocumentFailure failure) {
                    throw new ProtocolException(failure);
                } finally {
                    frame.clear();
                }
            }
        }

        private Frame receiveTransfer(Frame startFrame)
                throws IOException, ProtocolException {
            WorkflowResourceContext.MemoryReservation scratchReservation =
                    startFrame.takeReservation();
            byte[] transferId = null;
            byte[] expectedDigest = null;
            WorkflowResourceContext.MemoryReservation payloadReservation = null;
            byte[] payload = null;
            try {
                byte[] start = startFrame.getPayload();
                short applicationOpcode;
                int totalLength;
                int chunkCount;
                try {
                    if (start.length != TRANSFER_START_BYTES) {
                        throw new ProtocolException(
                                DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                                "The Worker transfer declaration is invalid.");
                    }
                    applicationOpcode = readShort(start, 0);
                    if (isTransferOpcode(applicationOpcode)
                            || isResourceControl(applicationOpcode)) {
                        throw new ProtocolException(
                                DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                                "The Worker transfer opcode is invalid.");
                    }
                    transferId = Arrays.copyOfRange(
                            start,
                            2,
                            2 + TRANSFER_ID_BYTES);
                    totalLength = readInt(start, 18);
                    chunkCount = readInt(start, 22);
                    expectedDigest = Arrays.copyOfRange(
                            start,
                            26,
                            TRANSFER_START_BYTES);
                } finally {
                    startFrame.clear();
                }

                int chunkCapacity = maximumPayloadBytes
                        - TRANSFER_CHUNK_HEADER_BYTES;
                long requiredMemory = requiredReceiveMemory(totalLength);
                long expectedChunkCount = totalLength <= 0
                                || chunkCapacity <= 0
                        ? -1
                        : ((long) totalLength + chunkCapacity - 1L)
                                / chunkCapacity;
                if (totalLength <= maximumPayloadBytes
                        || chunkCount != expectedChunkCount) {
                    throw new ProtocolException(
                            DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                            "The Worker transfer declaration is invalid.");
                }
                if (requiredMemory > maximumTransferMemoryBytes
                        || resources == null
                                && requiredMemory
                                        > maximumUnaccountedPayloadBytes) {
                    throw new ProtocolException(
                            DocumentFailureCode.WORKER_MESSAGE_LIMIT_EXCEEDED,
                            "The Worker transfer-size limit was exceeded.");
                }

                if (resources != null) {
                    try {
                        payloadReservation = resources
                                .reserveProtocolPayloadMemory(totalLength);
                    } catch (DocumentFailure failure) {
                        throw new ProtocolException(failure);
                    }
                }
                payload = new byte[totalLength];
                MessageDigest actualDigest = sha256();
                int offset = 0;
                for (int index = 0; index < chunkCount; index++) {
                    Frame chunkFrame = receiveFrame(true);
                    try {
                        byte[] chunk = chunkFrame.getPayload();
                        int count = Math.min(
                                chunkCapacity,
                                totalLength - offset);
                        if (chunkFrame.getOpcode() != TRANSFER_CHUNK
                                || chunk.length
                                        != TRANSFER_CHUNK_HEADER_BYTES + count
                                || !matches(
                                        transferId,
                                        chunk,
                                        0)
                                || readInt(chunk, TRANSFER_ID_BYTES) != index) {
                            throw new ProtocolException(
                                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                                    "The Worker transfer order or length is invalid.");
                        }
                        actualDigest.update(
                                chunk,
                                TRANSFER_CHUNK_HEADER_BYTES,
                                count);
                        System.arraycopy(
                                chunk,
                                TRANSFER_CHUNK_HEADER_BYTES,
                                payload,
                                offset,
                                count);
                        offset += count;
                    } finally {
                        chunkFrame.clear();
                    }
                }
                byte[] actual = actualDigest.digest();
                boolean intact = MessageDigest.isEqual(expectedDigest, actual);
                Arrays.fill(actual, (byte) 0);
                if (!intact) {
                    throw new ProtocolException(
                            DocumentFailureCode.WORKER_AUTHENTICATION_FAILED,
                            "Worker transfer integrity validation failed.");
                }
                Frame result = new Frame(
                        applicationOpcode,
                        payload,
                        payloadReservation);
                payload = null;
                payloadReservation = null;
                return result;
            } finally {
                clear(payload);
                clear(transferId);
                clear(expectedDigest);
                closeReservation(scratchReservation);
                closeReservation(payloadReservation);
            }
        }

        synchronized void receiveMemoryGrant(long expectedAmount)
                throws IOException, ProtocolException {
            receiveResourceGrant(MEMORY_GRANTED, expectedAmount);
        }

        synchronized void receiveResourceGrant(short expectedOpcode, long expectedAmount)
                throws IOException, ProtocolException {
            Frame frame = receiveFrame();
            try {
                if (frame.getOpcode() != expectedOpcode
                        || WorkerMessages.decodeMemoryAmount(
                                frame.getPayload()) != expectedAmount) {
                    throw new ProtocolException(
                            DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                            "The Worker memory grant is inapplicable.");
                }
            } catch (DocumentFailure failure) {
                throw new ProtocolException(failure);
            } finally {
                frame.clear();
            }
        }

        private Frame receiveFrame() throws IOException, ProtocolException {
            return receiveFrame(false);
        }

        private Frame receiveFrame(boolean transferScratchReserved)
                throws IOException, ProtocolException {
            requireOpen();
            int magic;
            short version;
            short opcode;
            long sequence;
            int payloadLength;
            try {
                magic = input.readInt();
                version = input.readShort();
                opcode = input.readShort();
                sequence = input.readLong();
                payloadLength = input.readInt();
            } catch (EOFException failure) {
                throw new ProtocolException(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker frame header was truncated.");
            }
            if (magic != MAGIC || version != VERSION_1) {
                throw new ProtocolException(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker protocol version is unsupported.");
            }
            if (sequence != nextReceiveSequence) {
                throw new ProtocolException(
                        DocumentFailureCode.WORKER_AUTHENTICATION_FAILED,
                        "The Worker frame sequence is not applicable.");
            }
            if (payloadLength < 0) {
                throw new ProtocolException(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker frame length is invalid.");
            }
            boolean memoryControl = isResourceControl(opcode);
            boolean transferControl = isTransferOpcode(opcode);
            if (memoryControl
                    && payloadLength != WorkerMessages.MEMORY_AMOUNT_BYTES) {
                throw new ProtocolException(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker memory-control length is invalid.");
            }
            if (payloadLength > maximumPayloadBytes) {
                throw new ProtocolException(
                        DocumentFailureCode.WORKER_MESSAGE_LIMIT_EXCEEDED,
                        "The Worker message-size limit was exceeded.");
            }
            long reservationBytes = transferControl
                    && !transferScratchReserved
                    ? maximumPayloadBytes : payloadLength;
            boolean requiresReservation = !memoryControl
                    && (!transferControl || !transferScratchReserved);
            if (resources == null && requiresReservation
                    && reservationBytes > maximumUnaccountedPayloadBytes) {
                throw new ProtocolException(
                        DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                        "The workflow owned-memory limit was exceeded.");
            }
            WorkflowResourceContext.MemoryReservation reservation = null;
            if (resources != null && requiresReservation) {
                try {
                    reservation = resources.reserveProtocolPayloadMemory(
                            reservationBytes);
                } catch (DocumentFailure failure) {
                    throw new ProtocolException(failure);
                }
            }
            byte[] payload;
            try {
                payload = new byte[payloadLength];
            } catch (RuntimeException | Error failure) {
                closeReservation(reservation);
                throw failure;
            }
            byte[] receivedTag;
            try {
                receivedTag = new byte[AUTHENTICATION_TAG_BYTES];
            } catch (RuntimeException | Error failure) {
                Arrays.fill(payload, (byte) 0);
                closeReservation(reservation);
                throw failure;
            }
            try {
                input.readFully(payload);
                input.readFully(receivedTag);
            } catch (EOFException failure) {
                Arrays.fill(payload, (byte) 0);
                Arrays.fill(receivedTag, (byte) 0);
                closeReservation(reservation);
                throw new ProtocolException(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker frame body was truncated.");
            } catch (IOException failure) {
                Arrays.fill(payload, (byte) 0);
                Arrays.fill(receivedTag, (byte) 0);
                closeReservation(reservation);
                throw failure;
            }
            byte[] expectedTag;
            try {
                expectedTag = authenticate(
                        key,
                        version,
                        opcode,
                        sequence,
                        payload);
            } catch (ProtocolException failure) {
                Arrays.fill(payload, (byte) 0);
                Arrays.fill(receivedTag, (byte) 0);
                closeReservation(reservation);
                throw failure;
            }
            boolean authenticated = MessageDigest.isEqual(
                    expectedTag,
                    receivedTag);
            Arrays.fill(expectedTag, (byte) 0);
            Arrays.fill(receivedTag, (byte) 0);
            if (!authenticated) {
                Arrays.fill(payload, (byte) 0);
                closeReservation(reservation);
                throw new ProtocolException(
                        DocumentFailureCode.WORKER_AUTHENTICATION_FAILED,
                        "Worker frame authentication failed.");
            }
            nextReceiveSequence = sequence + 1L;
            return new Frame(opcode, payload, reservation);
        }

        private void requireOpen() {
            if (closed) {
                throw new IllegalStateException(
                        "The Worker protocol endpoint is closed.");
            }
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            Arrays.fill(key, (byte) 0);
            try {
                output.flush();
            } catch (IOException ignored) {
                // Process cleanup owns the primary result.
            }
        }
    }

    interface MemoryGrantConsumer {

        void acceptMemoryGrant(long amount) throws DocumentFailure;

        void requireMemoryGrantConsumed() throws DocumentFailure;
    }

    static final class Frame {

        private final short opcode;
        private byte[] payload;
        private WorkflowResourceContext.MemoryReservation reservation;

        private Frame(
                short opcode,
                byte[] payload,
                WorkflowResourceContext.MemoryReservation reservation) {
            this.opcode = opcode;
            this.payload = payload;
            this.reservation = reservation;
        }

        short getOpcode() {
            return opcode;
        }

        byte[] getPayload() {
            return payload;
        }

        private WorkflowResourceContext.MemoryReservation takeReservation() {
            WorkflowResourceContext.MemoryReservation current = reservation;
            reservation = null;
            return current;
        }

        void clear() {
            byte[] current = payload;
            if (current != null) {
                Arrays.fill(current, (byte) 0);
                payload = null;
                WorkflowResourceContext.MemoryReservation currentReservation =
                        reservation;
                reservation = null;
                closeReservation(currentReservation);
            }
        }
    }

    static final class ProtocolException extends Exception {

        private static final long serialVersionUID = 1L;

        private final DocumentFailureCode code;
        private final DocumentFailure documentFailure;

        private ProtocolException(
                DocumentFailureCode code,
                String diagnostic) {
            super(diagnostic);
            this.code = code;
            this.documentFailure = null;
        }

        private ProtocolException(DocumentFailure failure) {
            super(failure.getDiagnostic());
            this.code = failure.getCode();
            this.documentFailure = failure;
        }

        DocumentFailureCode getCode() {
            return code;
        }

        DocumentFailure getDocumentFailure() {
            return documentFailure;
        }
    }

    private static void closeReservation(
            WorkflowResourceContext.MemoryReservation reservation) {
        if (reservation != null) {
            reservation.close();
        }
    }

    private static boolean isResourceControl(short opcode) {
        return opcode == MEMORY_RESERVE
                || opcode == MEMORY_RELEASE
                || opcode == MEMORY_GRANTED
                || opcode == TEMPORARY_RESERVE
                || opcode == TEMPORARY_RELEASE
                || opcode == TEMPORARY_GRANTED;
    }

    private static boolean isTransferOpcode(short opcode) {
        return opcode == TRANSFER_START || opcode == TRANSFER_CHUNK;
    }

    private static boolean matches(
            byte[] expected,
            byte[] actual,
            int offset) {
        if (expected == null || actual.length - offset < expected.length) {
            return false;
        }
        int difference = 0;
        for (int index = 0; index < expected.length; index++) {
            difference |= expected[index] ^ actual[offset + index];
        }
        return difference == 0;
    }

    private static MessageDigest sha256() throws ProtocolException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (GeneralSecurityException failure) {
            throw new ProtocolException(
                    DocumentFailureCode.WORKER_UNAVAILABLE,
                    "The Worker integrity primitive is unavailable.");
        }
    }

    private static void clear(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }

    private static void writeShort(byte[] target, int offset, short value) {
        target[offset] = (byte) (value >>> 8);
        target[offset + 1] = (byte) value;
    }

    private static short readShort(byte[] source, int offset) {
        return (short) (((source[offset] & 0xff) << 8)
                | (source[offset + 1] & 0xff));
    }

    private static void writeInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    private static int readInt(byte[] source, int offset) {
        return (source[offset] & 0xff) << 24
                | (source[offset + 1] & 0xff) << 16
                | (source[offset + 2] & 0xff) << 8
                | source[offset + 3] & 0xff;
    }

    private static void writeLong(byte[] target, int offset, long value) {
        for (int index = 0; index < 8; index++) {
            target[offset + index] = (byte) (value >>> (56 - 8 * index));
        }
    }

    private static byte[] authenticate(
            byte[] key,
            short version,
            short opcode,
            long sequence,
            byte[] payload) throws ProtocolException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            updateShort(mac, version);
            updateShort(mac, opcode);
            updateLong(mac, sequence);
            updateInt(mac, payload.length);
            mac.update(payload);
            return mac.doFinal();
        } catch (GeneralSecurityException failure) {
            throw new ProtocolException(
                    DocumentFailureCode.WORKER_UNAVAILABLE,
                    "The Worker authentication primitive is unavailable.");
        }
    }

    private static byte[] authenticateChunk(
            byte[] key,
            long sequence,
            byte[] transferId,
            int index,
            byte[] payload,
            int offset,
            int count) throws ProtocolException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            updateShort(mac, VERSION_1);
            updateShort(mac, TRANSFER_CHUNK);
            updateLong(mac, sequence);
            updateInt(mac, TRANSFER_CHUNK_HEADER_BYTES + count);
            mac.update(transferId);
            updateInt(mac, index);
            mac.update(payload, offset, count);
            return mac.doFinal();
        } catch (GeneralSecurityException failure) {
            throw new ProtocolException(
                    DocumentFailureCode.WORKER_UNAVAILABLE,
                    "The Worker authentication primitive is unavailable.");
        }
    }

    private static void updateShort(Mac mac, short value) {
        mac.update((byte) (value >>> 8));
        mac.update((byte) value);
    }

    private static void updateInt(Mac mac, int value) {
        mac.update((byte) (value >>> 24));
        mac.update((byte) (value >>> 16));
        mac.update((byte) (value >>> 8));
        mac.update((byte) value);
    }

    private static void updateLong(Mac mac, long value) {
        mac.update((byte) (value >>> 56));
        mac.update((byte) (value >>> 48));
        mac.update((byte) (value >>> 40));
        mac.update((byte) (value >>> 32));
        mac.update((byte) (value >>> 24));
        mac.update((byte) (value >>> 16));
        mac.update((byte) (value >>> 8));
        mac.update((byte) value);
    }
}
