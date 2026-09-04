package net.zerocloud.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.zerocloud.pdf.composition.FontSource;

/** Session-local, resource-accounted staging for explicit font sources. */
final class WorkerFontSourceCache implements AutoCloseable {

    private static final int MAXIMUM_READ_BUFFER_BYTES = 8192;

    private final WorkflowResourceContext resources;
    private final List<FontSource> referenceFonts;
    private final Map<FontSource, CachedBytes> oneShot =
            new IdentityHashMap<FontSource, CachedBytes>();
    private final Map<Long, RemoteFont> remoteFonts =
            new java.util.HashMap<Long, RemoteFont>();
    private long nextRemoteIdentifier = 1L;

    WorkerFontSourceCache(WorkflowResourceContext resources) {
        this(resources, Collections.<FontSource>emptyList());
    }

    WorkerFontSourceCache(
            WorkflowResourceContext resources,
            List<FontSource> referenceFonts) {
        this.resources = resources;
        this.referenceFonts = Collections.unmodifiableList(
                new ArrayList<FontSource>(referenceFonts));
    }

    List<FontSource> getReferenceFonts() {
        return referenceFonts;
    }

    long registerRemoteSelection(
            List<FontSource> sources,
            long maximumBytes) throws DocumentFailure {
        if (sources.isEmpty()) {
            throw new IllegalArgumentException(
                    "Remote font selections must not be empty.");
        }
        if ((long) sources.size() > Long.MAX_VALUE - nextRemoteIdentifier) {
            throw WorkerCodecIO.workerFailure(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    "The Worker font-source identifier space was exhausted.");
        }
        RemoteGroup group = new RemoteGroup(maximumBytes);
        long firstIdentifier = nextRemoteIdentifier;
        for (int index = 0; index < sources.size(); index++) {
            long identifier = nextRemoteIdentifier++;
            remoteFonts.put(
                    Long.valueOf(identifier),
                    new RemoteFont(sources.get(index), group, index));
        }
        return firstIdentifier;
    }

    FontValue readRemoteValue(long identifier) throws DocumentFailure {
        RemoteFont font = remoteFonts.get(Long.valueOf(identifier));
        if (font == null) {
            throw WorkerCodecIO.workerFailure(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    "The Worker font-source identifier is unavailable.");
        }
        if (font.completed) {
            throw WorkerCodecIO.workerFailure(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    "The Worker font-source identifier was repeated.");
        }
        if (font.group.nextIndex != font.index) {
            throw WorkerCodecIO.workerFailure(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    "The Worker font sources were requested out of order.");
        }
        font.borrowed = read(
                font.source,
                font.group.maximumBytes - font.group.totalBytes);
        int length = font.borrowed.getBytes().length;
        if (font.group.totalBytes > font.group.maximumBytes - length) {
            font.close();
            throw limitFailure();
        }
        font.group.totalBytes += length;
        font.group.nextIndex++;
        font.completed = true;
        return new FontValue(font.borrowed.getBytes(), font);
    }

    BorrowedBytes read(FontSource source, long maximumBytes)
            throws DocumentFailure {
        if (maximumBytes < 0L) {
            throw limitFailure();
        }
        try {
            switch (source.getSourceKind()) {
                case BYTES:
                    return readDeclaredBytes(source, maximumBytes);
                case PATH:
                    try (InputStream input = Files.newInputStream(
                            source.getPath().get()
                                    .toAbsolutePath()
                                    .normalize())) {
                        return BorrowedBytes.temporary(
                                readOwned(input, maximumBytes));
                    }
                case STREAM:
                case CHANNEL:
                    CachedBytes cached = oneShot.get(source);
                    if (cached == null) {
                        WorkflowResourceContext.OwnedBytes staged =
                                source.getSourceKind()
                                        == FontSource.SourceKind.STREAM
                                ? readOwned(
                                        source.getStream().get(),
                                        maximumBytes)
                                : readOwned(
                                        source.getChannel().get(),
                                        maximumBytes);
                        try {
                            cached = new CachedBytes(staged);
                            oneShot.put(source, cached);
                        } catch (RuntimeException | Error failure) {
                            clear(staged);
                            throw failure;
                        }
                    } else if (cached.bytes.getBytes().length > maximumBytes) {
                        throw limitFailure();
                    }
                    return BorrowedBytes.cached(cached.bytes);
                default:
                    throw sourceFailure();
            }
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw sourceFailure();
        }
    }

    private BorrowedBytes readDeclaredBytes(
            FontSource source,
            long maximumBytes) throws DocumentFailure {
        long length = source.getByteLength().getAsLong();
        if (length > maximumBytes) {
            throw limitFailure();
        }
        WorkflowResourceContext.MemoryReservation reservation =
                resources.reserveOwnedMemory(length);
        byte[] bytes = null;
        try {
            bytes = source.getBytes().get();
            return BorrowedBytes.temporary(
                    new WorkflowResourceContext.OwnedBytes(
                            bytes,
                            reservation));
        } catch (RuntimeException | Error failure) {
            if (bytes != null) {
                Arrays.fill(bytes, (byte) 0);
            }
            reservation.close();
            throw failure;
        }
    }

    private WorkflowResourceContext.OwnedBytes readOwned(
            InputStream input,
            long maximumBytes) throws IOException, DocumentFailure {
        return readOwned(input, null, maximumBytes);
    }

    private WorkflowResourceContext.OwnedBytes readOwned(
            ReadableByteChannel channel,
            long maximumBytes) throws IOException, DocumentFailure {
        return readOwned(null, channel, maximumBytes);
    }

    private WorkflowResourceContext.OwnedBytes readOwned(
            InputStream input,
            ReadableByteChannel channel,
            long maximumBytes) throws IOException, DocumentFailure {
        int bufferLength = bufferLength(maximumBytes);
        WorkflowResourceContext.MemoryReservation bufferReservation =
                resources.reserveOwnedMemory(bufferLength);
        byte[] buffer = null;
        try (WorkflowResourceContext.OwnedByteAccumulator output =
                resources.ownedByteAccumulator()) {
            buffer = new byte[bufferLength];
            ByteBuffer view = channel == null ? null : ByteBuffer.wrap(buffer);
            long total = 0L;
            while (true) {
                resources.checkpoint();
                int count = input == null
                        ? channel.read(view) : input.read(buffer);
                if (count < 0) {
                    return output.finishWorking();
                }
                if (count > 0) {
                    total = checkedTotal(total, count, maximumBytes);
                    output.write(buffer, 0, count);
                }
                if (view != null) {
                    view.clear();
                }
            }
        } finally {
            if (buffer != null) {
                Arrays.fill(buffer, (byte) 0);
            }
            bufferReservation.close();
        }
    }

    private int bufferLength(long maximumBytes) {
        long declared = maximumBytes == Long.MAX_VALUE
                ? MAXIMUM_READ_BUFFER_BYTES : maximumBytes + 1L;
        long remaining = resources.getRemainingOwnedMemoryBytes();
        return (int) Math.max(
                1L,
                Math.min(
                        Math.min(declared, MAXIMUM_READ_BUFFER_BYTES),
                        Math.max(1L, remaining)));
    }

    private static long checkedTotal(
            long total,
            int count,
            long maximumBytes) throws DocumentFailure {
        if (total > maximumBytes - count) {
            throw limitFailure();
        }
        return total + count;
    }

    @Override
    public void close() {
        for (RemoteFont font : remoteFonts.values()) {
            font.close();
        }
        remoteFonts.clear();
        for (CachedBytes value : oneShot.values()) {
            clear(value.bytes);
        }
        oneShot.clear();
    }

    private static void clear(WorkflowResourceContext.OwnedBytes bytes) {
        Arrays.fill(bytes.getBytes(), (byte) 0);
        bytes.close();
    }

    private static DocumentFailure sourceFailure() {
        return PdfBoxFontFailures.sourceInvalid();
    }

    private static DocumentFailure limitFailure() {
        return PdfBoxFontFailures.operationLimitExceeded();
    }

    static final class BorrowedBytes implements AutoCloseable {

        private final WorkflowResourceContext.OwnedBytes bytes;
        private final boolean temporary;

        private BorrowedBytes(
                WorkflowResourceContext.OwnedBytes bytes,
                boolean temporary) {
            this.bytes = bytes;
            this.temporary = temporary;
        }

        private static BorrowedBytes temporary(
                WorkflowResourceContext.OwnedBytes bytes) {
            return new BorrowedBytes(bytes, true);
        }

        private static BorrowedBytes cached(
                WorkflowResourceContext.OwnedBytes bytes) {
            return new BorrowedBytes(bytes, false);
        }

        byte[] getBytes() {
            return bytes.getBytes();
        }

        @Override
        public void close() {
            if (temporary) {
                clear(bytes);
            }
        }
    }

    private static final class CachedBytes {
        private final WorkflowResourceContext.OwnedBytes bytes;

        private CachedBytes(WorkflowResourceContext.OwnedBytes bytes) {
            this.bytes = bytes;
        }
    }

    static final class FontValue implements AutoCloseable {

        private final byte[] bytes;
        private RemoteFont releaseAfterUse;

        private FontValue(
                byte[] bytes,
                RemoteFont releaseAfterUse) {
            this.bytes = bytes;
            this.releaseAfterUse = releaseAfterUse;
        }

        byte[] getBytes() {
            return bytes;
        }

        @Override
        public void close() {
            if (releaseAfterUse != null) {
                releaseAfterUse.close();
                releaseAfterUse = null;
            }
        }
    }

    private static final class RemoteGroup {

        private final long maximumBytes;
        private long totalBytes;
        private int nextIndex;

        private RemoteGroup(long maximumBytes) {
            this.maximumBytes = maximumBytes;
        }
    }

    private static final class RemoteFont {

        private final FontSource source;
        private final RemoteGroup group;
        private final int index;
        private BorrowedBytes borrowed;
        private boolean completed;

        private RemoteFont(
                FontSource source,
                RemoteGroup group,
                int index) {
            this.source = source;
            this.group = group;
            this.index = index;
        }

        private void close() {
            if (borrowed != null) {
                borrowed.close();
                borrowed = null;
            }
            completed = true;
        }
    }
}
