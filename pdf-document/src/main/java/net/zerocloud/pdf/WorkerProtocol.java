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
                resources);
    }

    static final class Endpoint implements AutoCloseable {

        private final DataInputStream input;
        private final DataOutputStream output;
        private final byte[] key;
        private final int maximumPayloadBytes;
        private final long maximumUnaccountedPayloadBytes;
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
            this.input = new DataInputStream(input);
            this.output = new DataOutputStream(output);
            this.key = key.clone();
            this.maximumPayloadBytes = maximumPayloadBytes;
            this.maximumUnaccountedPayloadBytes =
                    maximumUnaccountedPayloadBytes;
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
            if (requiredPayload.length > maximumPayloadBytes) {
                throw new ProtocolException(
                        DocumentFailureCode.WORKER_MESSAGE_LIMIT_EXCEEDED,
                        "The Worker message-size limit was exceeded.");
            }
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
                    requiredPayload);
            try {
                output.writeInt(MAGIC);
                output.writeShort(VERSION_1);
                output.writeShort(opcode);
                output.writeLong(sequence);
                output.writeInt(requiredPayload.length);
                output.write(requiredPayload);
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

        synchronized void receiveMemoryGrant(long expectedAmount)
                throws IOException, ProtocolException {
            Frame frame = receiveFrame();
            try {
                if (frame.getOpcode() != MEMORY_GRANTED
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
            boolean memoryControl = opcode == MEMORY_RESERVE
                    || opcode == MEMORY_RELEASE
                    || opcode == MEMORY_GRANTED;
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
            if (resources == null && !memoryControl
                    && payloadLength > maximumUnaccountedPayloadBytes) {
                throw new ProtocolException(
                        DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                        "The workflow owned-memory limit was exceeded.");
            }
            WorkflowResourceContext.MemoryReservation reservation = null;
            if (resources != null && !memoryControl) {
                try {
                    reservation = resources.reserveProtocolPayloadMemory(
                            payloadLength);
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
        private final WorkflowResourceContext.MemoryReservation reservation;

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

        void clear() {
            byte[] current = payload;
            if (current != null) {
                Arrays.fill(current, (byte) 0);
                payload = null;
                closeReservation(reservation);
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
