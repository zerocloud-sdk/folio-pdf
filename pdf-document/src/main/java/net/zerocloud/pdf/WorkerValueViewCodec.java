package net.zerocloud.pdf;

import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/** Explicit remote-access codec preserving bounded lazy PDF Value views. */
final class WorkerValueViewCodec {

    private static final int VERSION = 1;

    private static final byte NULL = 0;
    private static final byte BOOLEAN = 1;
    private static final byte NUMBER = 2;
    private static final byte STRING = 3;
    private static final byte NAME = 4;
    private static final byte ARRAY_VIEW = 5;
    private static final byte DICTIONARY_VIEW = 6;
    private static final byte STREAM_VIEW = 7;
    private static final byte INDIRECT_REFERENCE = 8;

    private static final int ARRAY_SIZE = 1;
    private static final int ARRAY_GET = 2;
    private static final int DICTIONARY_SIZE = 3;
    private static final int DICTIONARY_GET = 4;
    private static final int DICTIONARY_ENTRY = 5;
    private static final int STREAM_DICTIONARY = 6;
    private static final int STREAM_BYTES = 7;

    private WorkerValueViewCodec() {
    }

    interface Remote {
        byte[] request(byte[] request) throws DocumentFailure;

        void requireActive() throws DocumentFailure;

        DocumentFailure terminalIfWorkerFailure(DocumentFailure failure);

        WorkflowResourceContext resources();

        int maximumMessageBytes();
    }

    static final class WorkerRegistry {

        private final WorkerReferenceRegistry references;
        private final IdentityHashMap<PdfValue, Long> identifiers =
                new IdentityHashMap<PdfValue, Long>();
        private final Map<Long, PdfValue> values =
                new HashMap<Long, PdfValue>();
        private long nextIdentifier = 1L;

        WorkerRegistry(WorkerReferenceRegistry references) {
            this.references = references;
        }

        byte[] encodeRoot(
                PdfValue value,
                WorkflowResourceContext resources,
                int maximumBytes)
                throws DocumentFailure {
            return WorkerCodecIO.encode(resources, maximumBytes, output -> {
                output.writeInt(VERSION);
                writeDescriptor(output, value);
            });
        }

        byte[] respond(
                byte[] request,
                WorkflowResourceContext resources,
                int maximumBytes)
                throws DocumentFailure {
            WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                    request,
                    resources);
            try {
                WorkerCommandCodec.requireVersion(input.readInt(), VERSION);
                long identifier = input.readLong();
                int operation = input.readInt();
                PdfValue value = values.get(Long.valueOf(identifier));
                if (value == null) {
                    throw rejected("The Worker PDF Value view is unavailable.");
                }
                final int index;
                final String name;
                switch (operation) {
                    case ARRAY_GET:
                    case DICTIONARY_ENTRY:
                        index = input.readInt();
                        name = null;
                        break;
                    case DICTIONARY_GET:
                        index = -1;
                        name = input.readString();
                        break;
                    default:
                        index = -1;
                        name = null;
                        break;
                }
                input.requireFullyConsumed();
                PdfValue arrayValueResult = null;
                PdfDictionaryEntry dictionaryEntryResult = null;
                try {
                    if (operation == ARRAY_GET) {
                        arrayValueResult = requireArray(value).get(index);
                    } else if (operation == DICTIONARY_ENTRY) {
                        dictionaryEntryResult = requireDictionary(value)
                                .getEntry(index);
                    }
                } catch (IndexOutOfBoundsException failure) {
                    return encodeIndexFailure(
                            operation,
                            resources,
                            maximumBytes);
                }
                final PdfValue indexedValue = arrayValueResult;
                final PdfDictionaryEntry indexedEntry = dictionaryEntryResult;
                return WorkerCodecIO.encode(resources, maximumBytes, output -> {
                    output.writeInt(VERSION);
                    output.writeInt(operation);
                    switch (operation) {
                        case ARRAY_SIZE:
                            output.writeInt(requireArray(value).size());
                            break;
                        case ARRAY_GET:
                            output.writeBoolean(true);
                            writeDescriptor(
                                    output,
                                    indexedValue);
                            break;
                        case DICTIONARY_SIZE:
                            output.writeInt(requireDictionary(value).size());
                            break;
                        case DICTIONARY_GET:
                            PdfValue found = requireDictionary(value).get(
                                    PdfName.of(name));
                            output.writeBoolean(found != null);
                            if (found != null) {
                                writeDescriptor(output, found);
                            }
                            break;
                        case DICTIONARY_ENTRY:
                            output.writeBoolean(true);
                            output.writeString(
                                    indexedEntry.getName().getValue());
                            writeDescriptor(output, indexedEntry.getValue());
                            break;
                        case STREAM_DICTIONARY:
                            writeDescriptor(
                                    output,
                                    requireStream(value).getDictionary());
                            break;
                        case STREAM_BYTES:
                            WorkerCommandCodec.writeStreamBytes(
                                    output,
                                    requireStream(value));
                            break;
                        default:
                            throw rejected(
                                    "The Worker PDF Value view operation is unsupported.");
                    }
                });
            } finally {
                input.releaseDecodedMemory();
            }
        }

        private static byte[] encodeIndexFailure(
                final int operation,
                WorkflowResourceContext resources,
                int maximumBytes) throws DocumentFailure {
            return WorkerCodecIO.encode(resources, maximumBytes, output -> {
                output.writeInt(VERSION);
                output.writeInt(operation);
                output.writeBoolean(false);
            });
        }

        private void writeDescriptor(
                WorkerCodecIO.Output output,
                PdfValue value) throws IOException, DocumentFailure {
            if (value == PdfNull.INSTANCE) {
                output.writeByte(NULL);
            } else if (value instanceof PdfBoolean) {
                output.writeByte(BOOLEAN);
                output.writeBoolean(((PdfBoolean) value).booleanValue());
            } else if (value instanceof PdfNumber) {
                output.writeByte(NUMBER);
                output.writeBigDecimal(((PdfNumber) value).decimalValue());
            } else if (value instanceof PdfString) {
                output.writeByte(STRING);
                output.writeBytes(((PdfString) value).bytesForWorkflow());
            } else if (value instanceof PdfName) {
                output.writeByte(NAME);
                output.writeString(((PdfName) value).getValue());
            } else if (value instanceof PdfArray) {
                output.writeByte(ARRAY_VIEW);
                output.writeLong(identifier(value));
            } else if (value instanceof PdfDictionary) {
                output.writeByte(DICTIONARY_VIEW);
                output.writeLong(identifier(value));
            } else if (value instanceof PdfStream) {
                PdfStream stream = (PdfStream) value;
                output.writeByte(STREAM_VIEW);
                output.writeLong(identifier(value));
                output.writeBoolean(stream.getReference().isPresent());
                if (stream.getReference().isPresent()) {
                    references.write(output, stream.getReference().get());
                }
            } else if (value instanceof PdfIndirectReference) {
                output.writeByte(INDIRECT_REFERENCE);
                references.write(
                        output,
                        ((PdfIndirectReference) value).getReference());
            } else {
                throw rejected("The Worker PDF Value kind is unsupported.");
            }
        }

        private long identifier(PdfValue value) throws DocumentFailure {
            Long existing = identifiers.get(value);
            if (existing != null) {
                return existing.longValue();
            }
            if (nextIdentifier == Long.MAX_VALUE) {
                throw rejected("The Worker PDF Value view space is exhausted.");
            }
            long identifier = nextIdentifier++;
            Long boxed = Long.valueOf(identifier);
            identifiers.put(value, boxed);
            values.put(boxed, value);
            return identifier;
        }
    }

    static PdfValue decodeRoot(
            byte[] payload,
            WorkerReferenceRegistry references,
            Remote remote) throws DocumentFailure {
        WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                payload,
                remote.resources());
        WorkerCommandCodec.requireVersion(input.readInt(), VERSION);
        PdfValue result = readDescriptor(input, references, remote);
        input.requireFullyConsumed();
        return result;
    }

    private static PdfValue readDescriptor(
            WorkerCodecIO.Input input,
            WorkerReferenceRegistry references,
            Remote remote) throws DocumentFailure {
        switch (input.readByte()) {
            case NULL:
                return PdfNull.INSTANCE;
            case BOOLEAN:
                return PdfBoolean.of(input.readBoolean());
            case NUMBER:
                return PdfNumber.of(input.readBigDecimal());
            case STRING:
                return PdfString.fromOwnedBytes(input.readBytes());
            case NAME:
                return PdfName.of(input.readString());
            case ARRAY_VIEW:
                return new PdfArray(new RemoteArrayAccess(
                        input.readLong(), references, remote));
            case DICTIONARY_VIEW:
                return new PdfDictionary(new RemoteDictionaryAccess(
                        input.readLong(), references, remote));
            case STREAM_VIEW:
                long identifier = input.readLong();
                ObjectReference reference = input.readBoolean()
                        ? references.read(input) : null;
                return new PdfStream(new RemoteStreamAccess(
                        identifier,
                        reference,
                        references,
                        remote));
            case INDIRECT_REFERENCE:
                return PdfIndirectReference.of(references.read(input));
            default:
                throw rejected("The Worker PDF Value descriptor is unsupported.");
        }
    }

    private static WorkerCodecIO.Input request(
            Remote remote,
            long identifier,
            int operation,
            Integer index,
            String name) throws DocumentFailure {
        remote.requireActive();
        byte[] request;
        try {
            request = WorkerCodecIO.encode(
                    remote.resources(),
                    remote.maximumMessageBytes(),
                    output -> {
                        output.writeInt(VERSION);
                        output.writeLong(identifier);
                        output.writeInt(operation);
                        if (index != null) {
                            output.writeInt(index.intValue());
                        }
                        if (name != null) {
                            output.writeString(name);
                        }
                    });
        } catch (DocumentFailure failure) {
            throw remote.terminalIfWorkerFailure(failure);
        }
        byte[] response;
        try {
            response = remote.request(request);
        } finally {
            WorkerCodecIO.clearRetained(remote.resources(), request);
        }
        WorkerCodecIO.Input input = WorkerCodecIO.retainedInput(
                response,
                remote.resources());
        try {
            WorkerCommandCodec.requireVersion(input.readInt(), VERSION);
            if (input.readInt() != operation) {
                throw rejected("The Worker PDF Value response is inapplicable.");
            }
            return input;
        } catch (DocumentFailure | RuntimeException failure) {
            input.clear();
            if (failure instanceof DocumentFailure) {
                throw remote.terminalIfWorkerFailure(
                        (DocumentFailure) failure);
            }
            throw failure;
        }
    }

    private static final class RemoteArrayAccess implements PdfArrayAccess {

        private final long identifier;
        private final WorkerReferenceRegistry references;
        private final Remote remote;

        private RemoteArrayAccess(
                long identifier,
                WorkerReferenceRegistry references,
                Remote remote) {
            this.identifier = identifier;
            this.references = references;
            this.remote = remote;
        }

        @Override
        public int size() throws DocumentFailure {
            WorkerCodecIO.Input input = request(
                    remote, identifier, ARRAY_SIZE, null, null);
            try {
                int result = input.readInt();
                input.requireFullyConsumed();
                return result;
            } finally {
                input.clear();
            }
        }

        @Override
        public PdfValue get(int index) throws DocumentFailure {
            WorkerCodecIO.Input input = request(
                    remote,
                    identifier,
                    ARRAY_GET,
                    Integer.valueOf(index),
                    null);
            try {
                if (!input.readBoolean()) {
                    throw new IndexOutOfBoundsException("index: " + index);
                }
                PdfValue result = readDescriptor(input, references, remote);
                input.requireFullyConsumed();
                return result;
            } finally {
                input.clear();
            }
        }
    }

    private static final class RemoteDictionaryAccess
            implements PdfDictionaryAccess {

        private final long identifier;
        private final WorkerReferenceRegistry references;
        private final Remote remote;

        private RemoteDictionaryAccess(
                long identifier,
                WorkerReferenceRegistry references,
                Remote remote) {
            this.identifier = identifier;
            this.references = references;
            this.remote = remote;
        }

        @Override
        public int size() throws DocumentFailure {
            WorkerCodecIO.Input input = request(
                    remote, identifier, DICTIONARY_SIZE, null, null);
            try {
                int result = input.readInt();
                input.requireFullyConsumed();
                return result;
            } finally {
                input.clear();
            }
        }

        @Override
        public PdfValue get(PdfName name) throws DocumentFailure {
            WorkerCodecIO.Input input = request(
                    remote,
                    identifier,
                    DICTIONARY_GET,
                    null,
                    name.getValue());
            try {
                PdfValue result = input.readBoolean()
                        ? readDescriptor(input, references, remote) : null;
                input.requireFullyConsumed();
                return result;
            } finally {
                input.clear();
            }
        }

        @Override
        public PdfDictionaryEntry getEntry(int index) throws DocumentFailure {
            WorkerCodecIO.Input input = request(
                    remote,
                    identifier,
                    DICTIONARY_ENTRY,
                    Integer.valueOf(index),
                    null);
            try {
                if (!input.readBoolean()) {
                    throw new IndexOutOfBoundsException("index: " + index);
                }
                PdfDictionaryEntry result = new PdfDictionaryEntry(
                        PdfName.of(input.readString()),
                        readDescriptor(input, references, remote));
                input.requireFullyConsumed();
                return result;
            } finally {
                input.clear();
            }
        }
    }

    private static final class RemoteStreamAccess implements PdfStreamAccess {

        private final long identifier;
        private final ObjectReference reference;
        private final WorkerReferenceRegistry references;
        private final Remote remote;

        private RemoteStreamAccess(
                long identifier,
                ObjectReference reference,
                WorkerReferenceRegistry references,
                Remote remote) {
            this.identifier = identifier;
            this.reference = reference;
            this.references = references;
            this.remote = remote;
        }

        @Override
        public PdfDictionary getDictionary() throws DocumentFailure {
            WorkerCodecIO.Input input = request(
                    remote,
                    identifier,
                    STREAM_DICTIONARY,
                    null,
                    null);
            try {
                PdfValue result = readDescriptor(input, references, remote);
                input.requireFullyConsumed();
                if (!(result instanceof PdfDictionary)) {
                    throw rejected("The Worker stream dictionary is invalid.");
                }
                return (PdfDictionary) result;
            } finally {
                input.clear();
            }
        }

        @Override
        public byte[] readBytes() throws DocumentFailure {
            WorkerCodecIO.Input input = request(
                    remote,
                    identifier,
                    STREAM_BYTES,
                    null,
                    null);
            try {
                byte[] result = input.readBytes();
                input.requireFullyConsumed();
                return result;
            } finally {
                input.clear();
            }
        }

        @Override
        public WorkflowResourceContext.OwnedBytes readBytesForWorkflow(
                WorkflowResourceContext resources) throws DocumentFailure {
            WorkerCodecIO.Input input = request(
                    remote,
                    identifier,
                    STREAM_BYTES,
                    null,
                    null);
            try {
                WorkflowResourceContext.OwnedBytes source =
                        input.readWorkingBytes();
                boolean returnedSource = false;
                try {
                    input.requireFullyConsumed();
                    if (resources == remote.resources()) {
                        returnedSource = true;
                        return source;
                    }
                    return resources.copyOwnedBytes(source.getBytes());
                } finally {
                    if (!returnedSource) {
                        source.close();
                    }
                }
            } finally {
                input.clear();
            }
        }

        @Override
        public Optional<ObjectReference> getReference() {
            return Optional.ofNullable(reference);
        }
    }

    private static PdfArray requireArray(PdfValue value)
            throws DocumentFailure {
        if (!(value instanceof PdfArray)) {
            throw rejected("The Worker PDF array view is invalid.");
        }
        return (PdfArray) value;
    }

    private static PdfDictionary requireDictionary(PdfValue value)
            throws DocumentFailure {
        if (!(value instanceof PdfDictionary)) {
            throw rejected("The Worker PDF dictionary view is invalid.");
        }
        return (PdfDictionary) value;
    }

    private static PdfStream requireStream(PdfValue value)
            throws DocumentFailure {
        if (!(value instanceof PdfStream)) {
            throw rejected("The Worker PDF stream view is invalid.");
        }
        return (PdfStream) value;
    }

    private static DocumentFailure rejected(String diagnostic) {
        return WorkerCodecIO.workerFailure(
                DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                diagnostic);
    }
}
