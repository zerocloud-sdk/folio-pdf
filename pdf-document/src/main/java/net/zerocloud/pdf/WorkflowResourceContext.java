package net.zerocloud.pdf;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.io.RandomAccess;
import org.apache.pdfbox.io.RandomAccessReadView;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.io.ScratchFile;
import org.apache.pdfbox.pdmodel.PDDocument;

/** One transaction's cooperative resource accounting and owned storage. */
final class WorkflowResourceContext implements AutoCloseable {

    static final String CAPABILITY_ID = "document.hostile-input-limits";

    private static final int PDFBOX_CACHE_PAGE_BYTES = 4096;
    private static final int CHECKPOINT_BYTES = 8192;

    private final WorkflowResourcePolicy policy;
    private final Clock clock;
    private final CancellationToken cancellationToken;
    private final Instant absoluteDeadline;
    private final Instant startedAt;
    private final Path temporaryRoot;
    private final Map<Path, Long> temporaryFiles =
            new LinkedHashMap<Path, Long>();
    private final List<RandomAccessStreamCache> streamCaches =
            new ArrayList<RandomAccessStreamCache>();
    private final Set<COSDictionary> pagesSeen =
            Collections.newSetFromMap(
                    new IdentityHashMap<COSDictionary, Boolean>());
    private final Set<COSObject> objectsSeen =
            Collections.newSetFromMap(
                    new IdentityHashMap<COSObject, Boolean>());
    private final Set<COSStream> streamsPreflighted =
            Collections.newSetFromMap(
                    new IdentityHashMap<COSStream, Boolean>());
    private final Map<COSStream, Long> materializableImageLengths =
            new IdentityHashMap<COSStream, Long>();
    private final Map<COSStream, Long> imagePixelsAccounted =
            new IdentityHashMap<COSStream, Long>();

    private long inputBytes;
    private long pageCount;
    private long objectCount;
    private long decompressedBytes;
    private long decodedPixels;
    private long ownedMemoryBytes;
    private long temporaryBytes;
    private DocumentFailure terminalFailure;
    private boolean closed;

    private WorkflowResourceContext(
            WorkflowResourcePolicy policy,
            Clock clock,
            CancellationToken cancellationToken,
            Instant absoluteDeadline,
            Path temporaryRoot) {
        this.policy = policy;
        this.clock = clock;
        this.cancellationToken = cancellationToken;
        this.absoluteDeadline = absoluteDeadline;
        this.startedAt = clock.instant();
        this.temporaryRoot = temporaryRoot;
    }

    static WorkflowResourceContext open(
            WorkflowResourcePolicy policy,
            Clock clock,
            CancellationToken cancellationToken,
            Instant absoluteDeadline,
            Path environmentTemporaryDirectory) throws DocumentFailure {
        Path root = null;
        try {
            Path parent = environmentTemporaryDirectory
                    .toAbsolutePath()
                    .normalize();
            if (!Files.isDirectory(parent)) {
                throw unavailableTemporaryStorage();
            }
            root = Files.createTempDirectory(parent, ".folio-pdf-workflow-");
            restrictToOwner(root);
            return new WorkflowResourceContext(
                    policy,
                    clock,
                    cancellationToken,
                    absoluteDeadline,
                    root);
        } catch (DocumentFailure failure) {
            deleteTreeQuietly(root);
            throw failure;
        } catch (IOException | RuntimeException failure) {
            deleteTreeQuietly(root);
            throw unavailableTemporaryStorage();
        }
    }

    WorkflowResourcePolicy getPolicy() {
        return policy;
    }

    Path getTemporaryRoot() {
        return temporaryRoot;
    }

    void checkpoint() throws DocumentFailure {
        requireOpen();
        if (terminalFailure != null) {
            throw terminalFailure;
        }
        if (cancellationToken.isCancellationRequested()) {
            throw executionStopFailure(
                    DocumentFailureCode.WORKFLOW_CANCELLED,
                    "The workflow was cancelled.");
        }
        Instant now = clock.instant();
        if (absoluteDeadline != null && !now.isBefore(absoluteDeadline)) {
            throw executionStopFailure(
                    DocumentFailureCode.DEADLINE_EXCEEDED,
                    "The workflow deadline has expired.");
        }
        Duration elapsed = now.isBefore(startedAt)
                ? Duration.ZERO : Duration.between(startedAt, now);
        if (elapsed.compareTo(policy.getMaximumElapsedTime()) > 0) {
            throw stopFailure(
                    DocumentFailureCode.ELAPSED_TIME_LIMIT_EXCEEDED,
                    "The workflow elapsed-time limit was exceeded.");
        }
    }

    void consumeInputBytes(long amount) throws DocumentFailure {
        inputBytes = checkedConsume(
                inputBytes,
                amount,
                policy.getMaximumInputBytes(),
                DocumentFailureCode.WORKFLOW_INPUT_LIMIT_EXCEEDED,
                "The workflow input-byte limit was exceeded.");
    }

    void observePage(COSDictionary page) throws DocumentFailure {
        if (pagesSeen.add(page)) {
            pageCount = checkedConsume(
                    pageCount,
                    1L,
                    policy.getMaximumPages(),
                    DocumentFailureCode.PAGE_LIMIT_EXCEEDED,
                    "The workflow page-count limit was exceeded.");
        }
    }

    void observeObject(COSObject object) throws DocumentFailure {
        if (objectsSeen.add(object)) {
            objectCount = checkedConsume(
                    objectCount,
                    1L,
                    policy.getMaximumObjects(),
                    DocumentFailureCode.OBJECT_LIMIT_EXCEEDED,
                    "The workflow PDF-object limit was exceeded.");
        }
    }

    void requireObjectCount(long count) throws DocumentFailure {
        if (count > policy.getMaximumObjects()) {
            throw stopFailure(
                    DocumentFailureCode.OBJECT_LIMIT_EXCEEDED,
                    "The workflow PDF-object limit was exceeded.");
        }
    }

    void requireNestingDepth(long depth) throws DocumentFailure {
        if (depth > policy.getMaximumNestingDepth()) {
            throw stopFailure(
                    DocumentFailureCode.NESTING_LIMIT_EXCEEDED,
                    "The workflow nesting-depth limit was exceeded.");
        }
    }

    void consumeDecompressedBytes(long amount) throws DocumentFailure {
        decompressedBytes = checkedConsume(
                decompressedBytes,
                amount,
                policy.getMaximumDecompressedBytes(),
                DocumentFailureCode.DECOMPRESSION_LIMIT_EXCEEDED,
                "The workflow decompression limit was exceeded.");
    }

    void consumeDecodedPixels(long amount) throws DocumentFailure {
        decodedPixels = checkedConsume(
                decodedPixels,
                amount,
                policy.getMaximumDecodedPixels(),
                DocumentFailureCode.PIXEL_LIMIT_EXCEEDED,
                "The workflow decoded-pixel limit was exceeded.");
    }

    MemoryReservation reserveOwnedMemory(long amount) throws DocumentFailure {
        reserveOwnedMemoryBytes(amount);
        return new MemoryReservation(this, amount);
    }

    OwnedBytes copyOwnedBytes(byte[] source) throws DocumentFailure {
        MemoryReservation reservation = reserveOwnedMemory(source.length);
        try {
            byte[] copy = new byte[source.length];
            for (int offset = 0; offset < source.length;
                    offset += CHECKPOINT_BYTES) {
                checkpoint();
                int length = Math.min(
                        CHECKPOINT_BYTES,
                        source.length - offset);
                System.arraycopy(source, offset, copy, offset, length);
            }
            checkpoint();
            return new OwnedBytes(copy, reservation);
        } catch (DocumentFailure failure) {
            reservation.close();
            throw failure;
        } catch (RuntimeException | Error failure) {
            reservation.close();
            throw failure;
        }
    }

    void retainOwnedMemory(long amount) throws DocumentFailure {
        reserveOwnedMemoryBytes(amount);
    }

    private void reserveOwnedMemoryBytes(long amount)
            throws DocumentFailure {
        ownedMemoryBytes = checkedConsume(
                ownedMemoryBytes,
                amount,
                policy.getMaximumOwnedMemoryBytes(),
                DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                "The workflow owned-memory limit was exceeded.");
    }

    OwnedByteAccumulator ownedByteAccumulator() {
        requireOpen();
        return new OwnedByteAccumulator(this);
    }

    OwnedTextAccumulator ownedTextAccumulator() {
        requireOpen();
        return new OwnedTextAccumulator(this);
    }

    OwnedMemoryScope ownedMemoryScope() {
        requireOpen();
        return new OwnedMemoryScope(this);
    }

    void retainOwnedMemoryAsIOException(long amount) throws IOException {
        try {
            retainOwnedMemory(amount);
        } catch (DocumentFailure failure) {
            throw new WorkflowResourceIOException(failure);
        }
    }

    MemoryReservation reserveOwnedMemoryAsIOException(long amount)
            throws IOException {
        try {
            return reserveOwnedMemory(amount);
        } catch (DocumentFailure failure) {
            throw new WorkflowResourceIOException(failure);
        }
    }

    void decodeStreamAsIOException(
            COSStream stream,
            OutputStream output) throws IOException {
        try {
            PdfBoxHostileInputPreflight.decodeStream(stream, this, output);
        } catch (DocumentFailure failure) {
            throw new WorkflowResourceIOException(failure);
        }
    }

    Path createTemporaryFile(String prefix, String suffix)
            throws DocumentFailure {
        checkpoint();
        try {
            Path file = Files.createTempFile(temporaryRoot, prefix, suffix);
            restrictFileToOwner(file);
            temporaryFiles.put(file, Long.valueOf(0L));
            return file;
        } catch (IOException | RuntimeException failure) {
            throw temporaryStorageUnavailable();
        }
    }

    void registerTemporaryFile(Path file) throws DocumentFailure {
        Path normalized = file.toAbsolutePath().normalize();
        if (temporaryFiles.containsKey(normalized)) {
            throw new IllegalStateException(
                    "Temporary file is already registered.");
        }
        long size;
        try {
            restrictFileToOwner(normalized);
            size = Files.size(normalized);
        } catch (IOException | RuntimeException failure) {
            throw temporaryStorageUnavailable();
        }
        reserveTemporaryBytes(size);
        temporaryFiles.put(normalized, Long.valueOf(size));
    }

    OutputStream openTemporaryOutput(Path file) throws DocumentFailure {
        checkpoint();
        Path normalized = file.toAbsolutePath().normalize();
        Long previous = temporaryFiles.get(normalized);
        if (previous == null) {
            throw new IllegalArgumentException(
                    "Temporary output must be registered before writing.");
        }
        OutputStream output;
        try {
            output = Files.newOutputStream(
                    normalized,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException | RuntimeException failure) {
            throw temporaryStorageUnavailable();
        }
        releaseTemporaryBytes(previous.longValue());
        temporaryFiles.put(normalized, Long.valueOf(0L));
        return new AccountedTemporaryOutputStream(this, normalized, output);
    }

    void releaseTemporaryFile(Path file) {
        if (file == null) {
            return;
        }
        Path normalized = file.toAbsolutePath().normalize();
        try {
            Files.deleteIfExists(normalized);
        } catch (IOException | RuntimeException ignored) {
            // Cleanup must not replace the safe primary failure.
            return;
        }
        Long size = temporaryFiles.remove(normalized);
        if (size != null) {
            releaseTemporaryBytes(size.longValue());
        }
    }

    void relinquishTemporaryFile(Path file) {
        if (file == null) {
            return;
        }
        Long size = temporaryFiles.remove(file.toAbsolutePath().normalize());
        if (size != null) {
            releaseTemporaryBytes(size.longValue());
        }
    }

    RandomAccessStreamCache.StreamCacheCreateFunction streamCacheFactory() {
        return new RandomAccessStreamCache.StreamCacheCreateFunction() {
            @Override
            public RandomAccessStreamCache create() throws IOException {
                try {
                    checkpoint();
                    AccountingStreamCache cache =
                            new AccountingStreamCache(
                                    WorkflowResourceContext.this);
                    streamCaches.add(cache);
                    return cache;
                } catch (DocumentFailure failure) {
                    throw new WorkflowResourceIOException(failure);
                }
            }
        };
    }

    InputStream checkpointedInput(InputStream input) {
        return new AccountedInputStream(this, input);
    }

    void audit(PDDocument document) throws DocumentFailure {
        try {
            PdfBoxHostileInputPreflight.audit(document, this);
        } catch (DocumentFailure failure) {
            if (CAPABILITY_ID.equals(failure.getCapabilityId())) {
                poison(failure);
            }
            throw failure;
        }
    }

    boolean markStreamPreflighted(COSStream stream) {
        return streamsPreflighted.add(stream);
    }

    void recordMaterializableImageLength(COSStream stream, long length) {
        materializableImageLengths.put(stream, Long.valueOf(length));
    }

    Long materializableImageLength(COSStream stream) {
        return materializableImageLengths.get(stream);
    }

    void consumeImagePixels(COSStream stream, long pixels)
            throws DocumentFailure {
        Long previous = imagePixelsAccounted.get(stream);
        long accounted = previous == null ? 0L : previous.longValue();
        if (pixels <= accounted) {
            return;
        }
        consumeDecodedPixels(pixels - accounted);
        imagePixelsAccounted.put(stream, Long.valueOf(pixels));
    }

    void recordConsumedImagePixels(COSStream stream, long pixels) {
        Long previous = imagePixelsAccounted.get(stream);
        if (previous == null || pixels > previous.longValue()) {
            imagePixelsAccounted.put(stream, Long.valueOf(pixels));
        }
    }

    private long checkedConsume(
            long current,
            long amount,
            long maximum,
            DocumentFailureCode code,
            String diagnostic) throws DocumentFailure {
        if (amount < 0L) {
            throw new IllegalArgumentException(
                    "Resource accounting amounts must not be negative.");
        }
        checkpoint();
        if (current > maximum - amount) {
            throw stopFailure(code, diagnostic);
        }
        return current + amount;
    }

    private void reserveTemporaryBytes(long amount) throws DocumentFailure {
        temporaryBytes = checkedConsume(
                temporaryBytes,
                amount,
                policy.getMaximumTemporaryStorageBytes(),
                DocumentFailureCode.TEMPORARY_STORAGE_LIMIT_EXCEEDED,
                "The workflow temporary-storage limit was exceeded.");
    }

    private void accountTemporaryWrite(Path file, int length)
            throws DocumentFailure {
        if (length <= 0) {
            return;
        }
        reserveTemporaryBytes(length);
        Long current = temporaryFiles.get(file);
        if (current == null) {
            throw new IllegalStateException(
                    "Temporary output is no longer registered.");
        }
        long next = current.longValue() + length;
        if (next < current.longValue()) {
            throw stopFailure(
                    DocumentFailureCode.TEMPORARY_STORAGE_LIMIT_EXCEEDED,
                    "The workflow temporary-storage limit was exceeded.");
        }
        temporaryFiles.put(file, Long.valueOf(next));
    }

    private void reserveCacheGrowth(long amount) throws IOException {
        try {
            reserveTemporaryBytes(amount);
        } catch (DocumentFailure failure) {
            throw new WorkflowResourceIOException(failure);
        }
    }

    private void releaseOwnedMemory(long amount) {
        ownedMemoryBytes -= amount;
        if (ownedMemoryBytes < 0L) {
            throw new IllegalStateException(
                    "Owned-memory accounting became negative.");
        }
    }

    void releaseRetainedOwnedMemory(long amount) {
        releaseOwnedMemory(amount);
    }

    private void releaseTemporaryBytes(long amount) {
        temporaryBytes -= amount;
        if (temporaryBytes < 0L) {
            throw new IllegalStateException(
                    "Temporary-storage accounting became negative.");
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException(
                    "Workflow resource context is closed.");
        }
    }

    DocumentFailure policyFailure(
            DocumentFailureCode code,
            String diagnostic) {
        return stopFailure(code, diagnostic);
    }

    void rethrowTerminalFailure() throws DocumentFailure {
        if (terminalFailure != null) {
            throw terminalFailure;
        }
    }

    void rethrowResourceOrTerminalFailure(Throwable failure)
            throws DocumentFailure {
        DocumentFailure resourceFailure = findResourceFailure(failure);
        if (resourceFailure != null) {
            throw resourceFailure;
        }
        rethrowTerminalFailure();
    }

    void checkpointAsIOException() throws IOException {
        try {
            checkpoint();
        } catch (DocumentFailure failure) {
            throw new WorkflowResourceIOException(failure);
        }
    }

    void writeBytesAsIOException(OutputStream output, byte[] bytes)
            throws IOException {
        writeBytesAsIOException(output, bytes, 0, bytes.length);
    }

    void writeBytesAsIOException(
            OutputStream output,
            byte[] bytes,
            int offset,
            int length) throws IOException {
        if (output == null) {
            throw new NullPointerException("output");
        }
        if (bytes == null
                || offset < 0
                || length < 0
                || offset > bytes.length - length) {
            throw new IndexOutOfBoundsException();
        }
        int end = offset + length;
        for (int position = offset; position < end;
                position += CHECKPOINT_BYTES) {
            checkpointAsIOException();
            int count = Math.min(CHECKPOINT_BYTES, end - position);
            output.write(bytes, position, count);
        }
        checkpointAsIOException();
    }

    void checkpointAsRuntimeException() {
        try {
            checkpoint();
        } catch (DocumentFailure failure) {
            throw new WorkflowResourceRuntimeException(failure);
        }
    }

    void requireNestingDepthAsIOException(long depth) throws IOException {
        try {
            requireNestingDepth(depth);
        } catch (DocumentFailure failure) {
            throw new WorkflowResourceIOException(failure);
        }
    }

    void consumeDecompressedBytesAsIOException(long amount)
            throws IOException {
        try {
            consumeDecompressedBytes(amount);
        } catch (DocumentFailure failure) {
            throw new WorkflowResourceIOException(failure);
        }
    }

    void consumeDecodedPixelsAsIOException(long amount) throws IOException {
        try {
            consumeDecodedPixels(amount);
        } catch (DocumentFailure failure) {
            throw new WorkflowResourceIOException(failure);
        }
    }

    void consumeImagePixelsAsIOException(COSStream stream, long pixels)
            throws IOException {
        try {
            consumeImagePixels(stream, pixels);
        } catch (DocumentFailure failure) {
            throw new WorkflowResourceIOException(failure);
        }
    }

    void consumeImageDimensionsAsIOException(
            COSStream stream,
            long width,
            long height) throws IOException {
        try {
            if (width < 0L
                    || height < 0L
                    || (width != 0L && height > Long.MAX_VALUE / width)) {
                throw stopFailure(
                        DocumentFailureCode.PIXEL_LIMIT_EXCEEDED,
                        "The workflow decoded-pixel limit was exceeded.");
            }
            consumeImagePixels(stream, width * height);
        } catch (DocumentFailure failure) {
            throw new WorkflowResourceIOException(failure);
        }
    }

    private DocumentFailure stopFailure(
            DocumentFailureCode code,
            String diagnostic) {
        DocumentFailure failure = new DocumentFailure(
                code,
                CAPABILITY_ID,
                diagnostic);
        poison(failure);
        return failure;
    }

    private DocumentFailure executionStopFailure(
            DocumentFailureCode code,
            String diagnostic) {
        DocumentFailure failure = PdfBoxWorkflowEngine.failure(
                code,
                diagnostic);
        poison(failure);
        return failure;
    }

    private void poison(DocumentFailure failure) {
        if (terminalFailure == null) {
            terminalFailure = failure;
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (RandomAccessStreamCache cache : streamCaches) {
            try {
                cache.close();
            } catch (IOException | RuntimeException ignored) {
                // Document cleanup owns the primary close result.
            }
        }
        streamCaches.clear();
        materializableImageLengths.clear();
        temporaryFiles.clear();
        temporaryBytes = 0L;
        ownedMemoryBytes = 0L;
        deleteTreeQuietly(temporaryRoot);
    }

    static DocumentFailure findResourceFailure(Throwable failure) {
        Throwable current = failure;
        Set<Throwable> visited = Collections.newSetFromMap(
                new IdentityHashMap<Throwable, Boolean>());
        while (current != null && visited.add(current)) {
            if (current instanceof WorkflowResourceIOException) {
                return ((WorkflowResourceIOException) current).failure;
            }
            if (current instanceof WorkflowResourceRuntimeException) {
                return ((WorkflowResourceRuntimeException) current).failure;
            }
            current = current.getCause();
        }
        return null;
    }

    private static DocumentFailure unavailableTemporaryStorage() {
        return new DocumentFailure(
                DocumentFailureCode.TEMPORARY_STORAGE_UNAVAILABLE,
                CAPABILITY_ID,
                "The workflow temporary-storage root is unavailable.");
    }

    private DocumentFailure temporaryStorageUnavailable() {
        return stopFailure(
                DocumentFailureCode.TEMPORARY_STORAGE_UNAVAILABLE,
                "The workflow temporary-storage root is unavailable.");
    }

    private static void restrictToOwner(Path root) {
        try {
            Files.setPosixFilePermissions(
                    root,
                    EnumSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE));
        } catch (IOException | UnsupportedOperationException
                | SecurityException ignored) {
            // Non-POSIX platforms retain their platform creation defaults.
        }
    }

    private static void restrictFileToOwner(Path file) {
        try {
            Files.setPosixFilePermissions(
                    file,
                    EnumSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE));
        } catch (IOException | UnsupportedOperationException
                | SecurityException ignored) {
            // Non-POSIX platforms retain their platform creation defaults.
        }
    }

    private static void deleteTreeQuietly(Path root) {
        if (root == null) {
            return;
        }
        try {
            if (Files.isDirectory(root)) {
                List<Path> paths = new ArrayList<Path>();
                java.nio.file.DirectoryStream<Path> entries =
                        Files.newDirectoryStream(root);
                try {
                    for (Path entry : entries) {
                        paths.add(entry);
                    }
                } finally {
                    entries.close();
                }
                for (Path entry : paths) {
                    if (Files.isDirectory(entry)) {
                        deleteTreeQuietly(entry);
                    } else {
                        Files.deleteIfExists(entry);
                    }
                }
            }
            Files.deleteIfExists(root);
        } catch (IOException | RuntimeException ignored) {
            // Best-effort cleanup must not replace a safe primary failure.
        }
    }

    static final class MemoryReservation implements AutoCloseable {

        private WorkflowResourceContext context;
        private final long amount;

        private MemoryReservation(
                WorkflowResourceContext context,
                long amount) {
            this.context = context;
            this.amount = amount;
        }

        @Override
        public void close() {
            WorkflowResourceContext current = context;
            if (current != null) {
                context = null;
                current.releaseOwnedMemory(amount);
            }
        }

        private void transfer() {
            context = null;
        }
    }

    /**
     * Aggregates provisional owned-memory reservations until an operation
     * either returns its detached result/commits its mutation or fails.
     */
    static final class OwnedMemoryScope implements AutoCloseable {

        private final WorkflowResourceContext context;
        private final Map<byte[], OwnedBytes> byteArrays =
                new IdentityHashMap<byte[], OwnedBytes>();
        private final List<OwnedString> strings =
                new ArrayList<OwnedString>();
        private long retainedBytes;
        private boolean transferred;
        private boolean closed;

        private OwnedMemoryScope(WorkflowResourceContext context) {
            this.context = context;
        }

        void retain(long amount) throws DocumentFailure {
            requireOpen();
            context.retainOwnedMemory(amount);
            retainedBytes += amount;
        }

        void retainAsIOException(long amount) throws IOException {
            try {
                retain(amount);
            } catch (DocumentFailure failure) {
                throw new WorkflowResourceIOException(failure);
            }
        }

        void release(long amount) {
            requireOpen();
            if (amount < 0L || amount > retainedBytes) {
                throw new IllegalArgumentException(
                        "Released memory must belong to this scope.");
            }
            context.releaseOwnedMemory(amount);
            retainedBytes -= amount;
        }

        byte[] hold(OwnedBytes bytes) {
            requireOpen();
            byte[] value = bytes.getBytes();
            if (byteArrays.containsKey(value)) {
                throw new IllegalArgumentException(
                        "Held bytes must be unique within this scope.");
            }
            byteArrays.put(value, bytes);
            return value;
        }

        String hold(OwnedString string) {
            requireOpen();
            String value = string.getString();
            strings.add(string);
            return value;
        }

        void releaseHeld(byte[] bytes) {
            requireOpen();
            OwnedBytes owned = byteArrays.remove(bytes);
            if (owned != null) {
                owned.close();
                return;
            }
            throw new IllegalArgumentException(
                    "Released bytes must belong to this scope.");
        }

        void transfer() throws DocumentFailure {
            requireOpen();
            for (OwnedBytes bytes : byteArrays.values()) {
                context.checkpoint();
                bytes.transfer();
            }
            for (OwnedString string : strings) {
                context.checkpoint();
                string.transfer();
            }
            byteArrays.clear();
            strings.clear();
            retainedBytes = 0L;
            transferred = true;
        }

        void transferTo(OwnedMemoryScope target) throws DocumentFailure {
            requireOpen();
            target.requireOpen();
            if (context != target.context) {
                throw new IllegalArgumentException(
                        "Owned-memory scopes must share one transaction.");
            }
            for (OwnedBytes bytes : byteArrays.values()) {
                context.checkpoint();
                target.hold(bytes);
            }
            for (OwnedString string : strings) {
                context.checkpoint();
                target.hold(string);
            }
            target.retainedBytes += retainedBytes;
            byteArrays.clear();
            strings.clear();
            retainedBytes = 0L;
            transferred = true;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (OwnedBytes bytes : byteArrays.values()) {
                bytes.close();
            }
            byteArrays.clear();
            for (OwnedString string : strings) {
                string.close();
            }
            strings.clear();
            context.releaseOwnedMemory(retainedBytes);
            retainedBytes = 0L;
        }

        private void requireOpen() {
            if (closed || transferred) {
                throw new IllegalStateException(
                        "Owned-memory scope is no longer active.");
            }
        }
    }

    /** Accounted UTF-16 text accumulation with scoped result ownership. */
    static final class OwnedTextAccumulator implements AutoCloseable {

        private final WorkflowResourceContext context;
        private final AccountedCharBuffer output;
        private boolean finished;
        private boolean closed;

        private OwnedTextAccumulator(WorkflowResourceContext context) {
            this.context = context;
            this.output = new AccountedCharBuffer(context);
        }

        void append(String value) throws DocumentFailure {
            requireWritable();
            output.append(value);
        }

        void appendAsIOException(String value) throws IOException {
            try {
                append(value);
            } catch (DocumentFailure failure) {
                throw new WorkflowResourceIOException(failure);
            }
        }

        void appendAsRuntimeException(String value) {
            try {
                append(value);
            } catch (DocumentFailure failure) {
                throw new WorkflowResourceRuntimeException(failure);
            }
        }

        void append(char value) throws DocumentFailure {
            requireWritable();
            output.append(value);
        }

        OwnedString finishWorking() throws DocumentFailure {
            requireWritable();
            MemoryReservation reservation =
                    context.reserveOwnedMemory(2L * output.size());
            try {
                String value = output.copy();
                finished = true;
                return new OwnedString(value, reservation);
            } catch (DocumentFailure | RuntimeException | Error failure) {
                reservation.close();
                throw failure;
            }
        }

        OwnedString finishWorkingAsIOException() throws IOException {
            try {
                return finishWorking();
            } catch (DocumentFailure failure) {
                throw new WorkflowResourceIOException(failure);
            }
        }

        String finishRetained() throws DocumentFailure {
            OwnedString value = finishWorking();
            String result = value.getString();
            value.transfer();
            return result;
        }

        String finishRetainedAsIOException() throws IOException {
            try {
                return finishRetained();
            } catch (DocumentFailure failure) {
                throw new WorkflowResourceIOException(failure);
            }
        }

        String finishHeld(OwnedMemoryScope ownership) throws DocumentFailure {
            OwnedString value = finishWorking();
            try {
                return ownership.hold(value);
            } catch (RuntimeException | Error failure) {
                value.close();
                throw failure;
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            output.close();
        }

        private void requireWritable() {
            if (finished || closed) {
                throw new IllegalStateException(
                        "Owned text accumulation is complete.");
            }
        }
    }

    /** A growable UTF-16 buffer whose backing capacity is accounted. */
    private static final class AccountedCharBuffer {

        private static final char[] EMPTY = new char[0];
        private static final int MAXIMUM_ARRAY_SIZE = Integer.MAX_VALUE - 8;

        private final WorkflowResourceContext context;
        private char[] buffer = EMPTY;
        private int size;

        private AccountedCharBuffer(WorkflowResourceContext context) {
            this.context = context;
        }

        private int size() {
            return size;
        }

        private void append(char value) throws DocumentFailure {
            context.checkpoint();
            ensureCapacity(1);
            buffer[size++] = value;
        }

        private void append(String value) throws DocumentFailure {
            if (value == null) {
                throw new NullPointerException("value");
            }
            ensureCapacity(value.length());
            int end = value.length();
            for (int offset = 0; offset < end; offset += CHECKPOINT_BYTES) {
                context.checkpoint();
                int length = Math.min(CHECKPOINT_BYTES, end - offset);
                value.getChars(offset, offset + length, buffer, size);
                size += length;
            }
            context.checkpoint();
        }

        private String copy() throws DocumentFailure {
            context.checkpoint();
            String result = new String(buffer, 0, size);
            context.checkpoint();
            return result;
        }

        private void close() {
            int capacity = buffer.length;
            buffer = EMPTY;
            size = 0;
            context.releaseOwnedMemory(2L * capacity);
        }

        private void ensureCapacity(int additional) throws DocumentFailure {
            long requiredLong = (long) size + additional;
            if (requiredLong > MAXIMUM_ARRAY_SIZE) {
                throw context.stopFailure(
                        DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                        "The workflow owned-memory limit was exceeded.");
            }
            int required = (int) requiredLong;
            if (required <= buffer.length) {
                return;
            }
            int doubled = buffer.length > MAXIMUM_ARRAY_SIZE / 2
                    ? MAXIMUM_ARRAY_SIZE : buffer.length * 2;
            int nextCapacity = Math.max(required, Math.max(1, doubled));
            context.reserveOwnedMemoryBytes(2L * nextCapacity);
            try {
                char[] replacement = new char[nextCapacity];
                for (int offset = 0; offset < size;
                        offset += CHECKPOINT_BYTES) {
                    context.checkpoint();
                    int length = Math.min(CHECKPOINT_BYTES, size - offset);
                    System.arraycopy(buffer, offset, replacement, offset, length);
                }
                context.checkpoint();
                int previousCapacity = buffer.length;
                buffer = replacement;
                context.releaseOwnedMemory(2L * previousCapacity);
            } catch (DocumentFailure | RuntimeException | Error failure) {
                context.releaseOwnedMemory(2L * nextCapacity);
                throw failure;
            }
        }
    }

    /** Accounted byte accumulation that checks before every payload write. */
    static final class OwnedByteAccumulator extends OutputStream {

        private final WorkflowResourceContext context;
        private final AccountedByteBuffer output;
        private boolean finished;
        private boolean closed;

        private OwnedByteAccumulator(WorkflowResourceContext context) {
            this.context = context;
            this.output = new AccountedByteBuffer(context);
        }

        int size() {
            requireWritable();
            return output.size();
        }

        @Override
        public void write(int value) throws IOException {
            output.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length)
                throws IOException {
            if (bytes == null
                    || offset < 0
                    || length < 0
                    || offset > bytes.length - length) {
                throw new IndexOutOfBoundsException();
            }
            output.write(bytes, offset, length);
        }

        byte[] finishRetained() throws DocumentFailure {
            requireWritable();
            context.retainOwnedMemory(output.size());
            try {
                byte[] bytes = output.copy();
                finished = true;
                return bytes;
            } catch (DocumentFailure failure) {
                context.releaseOwnedMemory(output.size());
                throw failure;
            } catch (RuntimeException | Error failure) {
                context.releaseOwnedMemory(output.size());
                throw failure;
            }
        }

        byte[] finishRetainedAsIOException() throws IOException {
            try {
                return finishRetained();
            } catch (DocumentFailure failure) {
                throw new WorkflowResourceIOException(failure);
            }
        }

        OwnedBytes finishWorking() throws DocumentFailure {
            requireWritable();
            MemoryReservation reservation =
                    context.reserveOwnedMemory(output.size());
            byte[] bytes;
            try {
                bytes = output.copy();
            } catch (DocumentFailure failure) {
                reservation.close();
                throw failure;
            } catch (RuntimeException | Error failure) {
                reservation.close();
                throw failure;
            }
            finished = true;
            return new OwnedBytes(bytes, reservation);
        }

        OwnedBytes finishWorkingAsIOException() throws IOException {
            try {
                return finishWorking();
            } catch (DocumentFailure failure) {
                throw new WorkflowResourceIOException(failure);
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            output.close();
        }

        private void requireWritable() {
            if (finished || closed) {
                throw new IllegalStateException(
                        "Owned byte accumulation is complete.");
            }
        }
    }

    /** A growable buffer whose actual backing-array capacity is accounted. */
    private static final class AccountedByteBuffer {

        private static final byte[] EMPTY = new byte[0];
        private static final int MAXIMUM_ARRAY_SIZE = Integer.MAX_VALUE - 8;

        private final WorkflowResourceContext context;
        private byte[] buffer = EMPTY;
        private int size;

        private AccountedByteBuffer(WorkflowResourceContext context) {
            this.context = context;
        }

        private int size() {
            return size;
        }

        private void write(int value) throws IOException {
            context.checkpointAsIOException();
            ensureCapacity(1);
            buffer[size++] = (byte) value;
        }

        private void write(byte[] bytes, int offset, int length)
                throws IOException {
            ensureCapacity(length);
            int end = offset + length;
            for (int position = offset; position < end;
                    position += CHECKPOINT_BYTES) {
                context.checkpointAsIOException();
                int count = Math.min(CHECKPOINT_BYTES, end - position);
                System.arraycopy(bytes, position, buffer, size, count);
                size += count;
            }
            context.checkpointAsIOException();
        }

        private byte[] copy() throws DocumentFailure {
            byte[] result = new byte[size];
            for (int offset = 0; offset < size;
                    offset += CHECKPOINT_BYTES) {
                context.checkpoint();
                int length = Math.min(CHECKPOINT_BYTES, size - offset);
                System.arraycopy(buffer, offset, result, offset, length);
            }
            context.checkpoint();
            return result;
        }

        private void close() {
            int capacity = buffer.length;
            buffer = EMPTY;
            size = 0;
            context.releaseOwnedMemory(capacity);
        }

        private void ensureCapacity(int additional) throws IOException {
            long requiredLong = (long) size + additional;
            if (requiredLong > MAXIMUM_ARRAY_SIZE) {
                throw new WorkflowResourceIOException(context.stopFailure(
                        DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                        "The workflow owned-memory limit was exceeded."));
            }
            int required = (int) requiredLong;
            if (required <= buffer.length) {
                return;
            }
            int doubled = buffer.length > MAXIMUM_ARRAY_SIZE / 2
                    ? MAXIMUM_ARRAY_SIZE : buffer.length * 2;
            int nextCapacity = Math.max(required, Math.max(1, doubled));
            try {
                context.reserveOwnedMemoryBytes(nextCapacity);
            } catch (DocumentFailure failure) {
                throw new WorkflowResourceIOException(failure);
            }
            byte[] replacement;
            try {
                replacement = new byte[nextCapacity];
                for (int offset = 0; offset < size;
                        offset += CHECKPOINT_BYTES) {
                    context.checkpointAsIOException();
                    int length = Math.min(
                            CHECKPOINT_BYTES,
                            size - offset);
                    System.arraycopy(
                            buffer,
                            offset,
                            replacement,
                            offset,
                            length);
                }
                context.checkpointAsIOException();
            } catch (IOException | RuntimeException | Error failure) {
                context.releaseOwnedMemory(nextCapacity);
                throw failure;
            }
            int previousCapacity = buffer.length;
            buffer = replacement;
            context.releaseOwnedMemory(previousCapacity);
        }
    }

    /** One temporary accounted byte array whose reservation follows it. */
    static final class OwnedBytes implements AutoCloseable {

        private final byte[] bytes;
        private MemoryReservation reservation;

        private OwnedBytes(
                byte[] bytes,
                MemoryReservation reservation) {
            this.bytes = bytes;
            this.reservation = reservation;
        }

        byte[] getBytes() {
            return bytes;
        }

        private void transfer() {
            MemoryReservation current = reservation;
            if (current != null) {
                reservation = null;
                current.transfer();
            }
        }

        @Override
        public void close() {
            MemoryReservation current = reservation;
            if (current != null) {
                reservation = null;
                current.close();
            }
        }
    }

    /** One temporary accounted String whose reservation follows it. */
    static final class OwnedString implements AutoCloseable {

        private final String string;
        private MemoryReservation reservation;

        private OwnedString(
                String string,
                MemoryReservation reservation) {
            this.string = string;
            this.reservation = reservation;
        }

        String getString() {
            return string;
        }

        private void transfer() {
            MemoryReservation current = reservation;
            if (current != null) {
                reservation = null;
                current.transfer();
            }
        }

        @Override
        public void close() {
            MemoryReservation current = reservation;
            if (current != null) {
                reservation = null;
                current.close();
            }
        }
    }

    private static final class WorkflowResourceIOException extends IOException {

        private static final long serialVersionUID = 1L;
        private final DocumentFailure failure;

        private WorkflowResourceIOException(DocumentFailure failure) {
            super(failure.getDiagnostic());
            this.failure = failure;
        }
    }

    private static final class WorkflowResourceRuntimeException
            extends RuntimeException {

        private static final long serialVersionUID = 1L;
        private final DocumentFailure failure;

        private WorkflowResourceRuntimeException(DocumentFailure failure) {
            super(failure.getDiagnostic());
            this.failure = failure;
        }
    }

    private static final class AccountedTemporaryOutputStream
            extends FilterOutputStream {

        private final WorkflowResourceContext context;
        private final Path file;

        private AccountedTemporaryOutputStream(
                WorkflowResourceContext context,
                Path file,
                OutputStream output) {
            super(output);
            this.context = context;
            this.file = file;
        }

        @Override
        public void write(int value) throws IOException {
            account(1);
            out.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length)
                throws IOException {
            account(length);
            context.writeBytesAsIOException(out, bytes, offset, length);
        }

        private void account(int length) throws IOException {
            try {
                context.accountTemporaryWrite(file, length);
            } catch (DocumentFailure failure) {
                throw new WorkflowResourceIOException(failure);
            }
        }
    }

    private static final class AccountedInputStream extends FilterInputStream {

        private final WorkflowResourceContext context;

        private AccountedInputStream(
                WorkflowResourceContext context,
                InputStream input) {
            super(input);
            this.context = context;
        }

        @Override
        public int read() throws IOException {
            checkpoint();
            int value = in.read();
            checkpoint();
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length)
                throws IOException {
            checkpoint();
            int count = in.read(bytes, offset, length);
            checkpoint();
            return count;
        }

        private void checkpoint() throws IOException {
            try {
                context.checkpoint();
            } catch (DocumentFailure failure) {
                throw new WorkflowResourceIOException(failure);
            }
        }

    }

    private static final class AccountingStreamCache
            implements RandomAccessStreamCache {

        private final WorkflowResourceContext context;
        private final ScratchFile delegate;
        private long chargedCapacity;
        private long reusableCapacity;
        private boolean closed;

        private AccountingStreamCache(WorkflowResourceContext context)
                throws IOException {
            this.context = context;
            try {
                this.delegate = new ScratchFile(
                        MemoryUsageSetting.setupTempFileOnly()
                                .setTempDir(context.temporaryRoot.toFile()));
            } catch (IOException | RuntimeException failure) {
                throw new WorkflowResourceIOException(
                        context.temporaryStorageUnavailable());
            }
        }

        @Override
        public RandomAccess createBuffer() throws IOException {
            if (closed) {
                throw new IOException("Stream cache is closed");
            }
            RandomAccess buffer = null;
            boolean initialPageAcquired = false;
            try {
                context.checkpointAsIOException();
                acquireCapacity(PDFBOX_CACHE_PAGE_BYTES);
                initialPageAcquired = true;
                buffer = delegate.createBuffer();
                return new AccountingRandomAccess(
                        this,
                        context,
                        buffer,
                        PDFBOX_CACHE_PAGE_BYTES);
            } catch (WorkflowResourceIOException failure) {
                closeQuietly(buffer);
                if (initialPageAcquired) {
                    releaseCapacity(PDFBOX_CACHE_PAGE_BYTES);
                }
                throw failure;
            } catch (IOException | RuntimeException failure) {
                closeQuietly(buffer);
                if (initialPageAcquired) {
                    releaseCapacity(PDFBOX_CACHE_PAGE_BYTES);
                }
                throw new WorkflowResourceIOException(
                        context.temporaryStorageUnavailable());
            }
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                delegate.close();
                closed = true;
                context.releaseTemporaryBytes(chargedCapacity);
                chargedCapacity = 0L;
                reusableCapacity = 0L;
            }
        }

        private synchronized void acquireCapacity(long amount)
                throws IOException {
            if (amount <= reusableCapacity) {
                reusableCapacity -= amount;
                return;
            }
            long growth = amount - reusableCapacity;
            context.reserveCacheGrowth(growth);
            chargedCapacity += growth;
            reusableCapacity = 0L;
        }

        private synchronized void releaseCapacity(long amount) {
            if (amount < 0L
                    || reusableCapacity > chargedCapacity - amount) {
                throw new IllegalStateException(
                        "PDFBox cache accounting became inconsistent.");
            }
            reusableCapacity += amount;
        }

        private static void closeQuietly(RandomAccess buffer) {
            if (buffer == null) {
                return;
            }
            try {
                buffer.close();
            } catch (IOException | RuntimeException ignored) {
                // Preserve the safe resource failure from buffer creation.
            }
        }
    }

    private static final class AccountingRandomAccess implements RandomAccess {

        private final WorkflowResourceContext context;
        private final AccountingStreamCache cache;
        private final RandomAccess delegate;
        private long allocatedCapacity;
        private boolean closed;

        private AccountingRandomAccess(
                AccountingStreamCache cache,
                WorkflowResourceContext context,
                RandomAccess delegate,
                long allocatedCapacity) {
            this.cache = cache;
            this.context = context;
            this.delegate = delegate;
            this.allocatedCapacity = allocatedCapacity;
        }

        @Override
        public int read() throws IOException {
            context.checkpointAsIOException();
            return delegate.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length)
                throws IOException {
            context.checkpointAsIOException();
            return delegate.read(
                    bytes,
                    offset,
                    Math.min(length, CHECKPOINT_BYTES));
        }

        @Override
        public long getPosition() throws IOException {
            context.checkpointAsIOException();
            return delegate.getPosition();
        }

        @Override
        public void seek(long position) throws IOException {
            context.checkpointAsIOException();
            delegate.seek(position);
        }

        @Override
        public long length() throws IOException {
            context.checkpointAsIOException();
            return delegate.length();
        }

        @Override
        public boolean isClosed() {
            return closed || delegate.isClosed();
        }

        @Override
        public boolean isEOF() throws IOException {
            context.checkpointAsIOException();
            return delegate.isEOF();
        }

        @Override
        public RandomAccessReadView createView(long start, long length)
                throws IOException {
            context.checkpointAsIOException();
            return new RandomAccessReadView(this, start, length);
        }

        @Override
        public void write(int value) throws IOException {
            reserveForWrite(1);
            delegate.write(value);
        }

        @Override
        public void write(byte[] bytes) throws IOException {
            write(bytes, 0, bytes.length);
        }

        @Override
        public void write(byte[] bytes, int offset, int length)
                throws IOException {
            if (bytes == null
                    || offset < 0
                    || length < 0
                    || offset > bytes.length - length) {
                throw new IndexOutOfBoundsException();
            }
            reserveForWrite(length);
            int end = offset + length;
            for (int position = offset; position < end;
                    position += CHECKPOINT_BYTES) {
                context.checkpointAsIOException();
                int count = Math.min(CHECKPOINT_BYTES, end - position);
                delegate.write(bytes, position, count);
            }
            context.checkpointAsIOException();
        }

        @Override
        public void clear() throws IOException {
            context.checkpointAsIOException();
            delegate.clear();
            long releasedCapacity =
                    allocatedCapacity - PDFBOX_CACHE_PAGE_BYTES;
            cache.releaseCapacity(releasedCapacity);
            allocatedCapacity = PDFBOX_CACHE_PAGE_BYTES;
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                delegate.close();
                closed = true;
                cache.releaseCapacity(allocatedCapacity);
                allocatedCapacity = 0L;
            }
        }

        private void reserveForWrite(int length) throws IOException {
            context.checkpointAsIOException();
            if (length <= 0) {
                return;
            }
            long position = delegate.getPosition();
            if (position > Long.MAX_VALUE - length) {
                throw new WorkflowResourceIOException(context.stopFailure(
                        DocumentFailureCode.TEMPORARY_STORAGE_LIMIT_EXCEEDED,
                        "The workflow temporary-storage limit was exceeded."));
            }
            long requestedLength = Math.max(
                    delegate.length(),
                    position + length);
            long requestedCapacity = roundedCacheCapacity(requestedLength);
            if (requestedCapacity > allocatedCapacity) {
                cache.acquireCapacity(
                        requestedCapacity - allocatedCapacity);
                allocatedCapacity = requestedCapacity;
            }
        }

        private long roundedCacheCapacity(long length)
                throws WorkflowResourceIOException {
            if (length == 0L) {
                return 0L;
            }
            long remainder = length % PDFBOX_CACHE_PAGE_BYTES;
            long extra = remainder == 0L
                    ? 0L : PDFBOX_CACHE_PAGE_BYTES - remainder;
            if (length > Long.MAX_VALUE - extra) {
                throw new WorkflowResourceIOException(context.stopFailure(
                        DocumentFailureCode.TEMPORARY_STORAGE_LIMIT_EXCEEDED,
                        "The workflow temporary-storage limit was exceeded."));
            }
            return length + extra;
        }
    }
}
