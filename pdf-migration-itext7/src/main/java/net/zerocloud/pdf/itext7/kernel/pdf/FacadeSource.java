package net.zerocloud.pdf.itext7.kernel.pdf;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.WorkflowResourcePolicy;
import net.zerocloud.pdf.query.PageCount;

/** A bounded, validated Source snapshot transferred from Reader to Document. */
final class FacadeSource implements AutoCloseable {
    private static final WorkflowResourcePolicy DEFAULTS = WorkflowResourcePolicy.safeDefaults();
    private final Path directory;
    final Path path;
    final int pageCount;
    final long bytes;

    private FacadeSource(Path directory, Path path, int pageCount, long bytes) {
        this.directory = directory;
        this.path = path;
        this.pageCount = pageCount;
        this.bytes = bytes;
    }

    static FacadeSource capture(Path original) throws IOException {
        FacadeSource captured = null;
        try (InputStream input = Files.newInputStream(original)) {
            captured = capture(input);
            return captured;
        } catch (IOException failure) {
            if (captured != null) {
                cleanup(captured.path, captured.directory, failure);
            }
            if (failure.getCause() instanceof DocumentFailure) {
                throw failure;
            }
            throw readFailure();
        }
    }

    static FacadeSource capture(InputStream input) throws IOException {
        Path directory = null;
        Path snapshot = null;
        boolean retained = false;
        Throwable primaryFailure = null;
        try {
            // The default temporary-directory attributes restrict access to
            // its owner on POSIX systems, before any Source bytes are written.
            directory = Files.createTempDirectory(".folio-pdf-facade-");
            snapshot = directory.resolve("source.pdf");
            int pageCount;
            try (OutputStream output = Files.newOutputStream(snapshot)) {
                InputStream copying = new FilterInputStream(input) {
                    private long copied;

                    @Override
                    public int read() throws IOException {
                        int value = in.read();
                        if (value >= 0 && copied < DEFAULTS.getMaximumInputBytes()) {
                            output.write(value);
                            copied++;
                        }
                        return value;
                    }

                    @Override
                    public int read(byte[] buffer, int offset, int length) throws IOException {
                        int count = in.read(buffer, offset, length);
                        if (count > 0) {
                            int retained = (int) Math.min(count, DEFAULTS.getMaximumInputBytes() - copied);
                            output.write(buffer, offset, retained);
                            copied += retained;
                        }
                        return count;
                    }
                };
                WorkflowOutcome<Integer> outcome = new DocumentWorkflow().execute(WorkflowRequest.builder()
                        .source("source", DocumentSource.stream(copying, DEFAULTS.getMaximumInputBytes()))
                        .primarySource("source").saveMode(SaveMode.REWRITE)
                        .resourcePolicy(policyWithSnapshot(DEFAULTS.getMaximumInputBytes())).build(),
                        session -> session.query(PageCount.INSTANCE));
                pageCount = outcome.getResult().intValue();
            }
            FacadeSource source = new FacadeSource(directory, snapshot, pageCount, Files.size(snapshot));
            retained = true;
            return source;
        } catch (DocumentFailure failure) {
            IOException mapped = new IOException(failure.getCode().name() + ": " + failure.getDiagnostic(), failure);
            primaryFailure = mapped;
            throw mapped;
        } catch (IOException failure) {
            IOException mapped = readFailure();
            primaryFailure = mapped;
            throw mapped;
        } catch (RuntimeException | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            if (!retained) {
                cleanup(snapshot, directory, primaryFailure);
            }
        }
    }

    static WorkflowResourcePolicy policyWithSnapshot(long reserved) {
        return WorkflowResourcePolicy.builder()
                .maximumInputBytes(DEFAULTS.getMaximumInputBytes())
                .maximumPages(DEFAULTS.getMaximumPages())
                .maximumObjects(DEFAULTS.getMaximumObjects())
                .maximumNestingDepth(DEFAULTS.getMaximumNestingDepth())
                .maximumDecompressedBytes(DEFAULTS.getMaximumDecompressedBytes())
                .maximumDecodedPixels(DEFAULTS.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(DEFAULTS.getMaximumOwnedMemoryBytes())
                .maximumTemporaryStorageBytes(DEFAULTS.getMaximumTemporaryStorageBytes() - reserved)
                .maximumElapsedTime(DEFAULTS.getMaximumElapsedTime())
                .maximumConcurrentWorkflows(DEFAULTS.getMaximumConcurrentWorkflows()).build();
    }

    @Override
    public void close() throws IOException {
        cleanup(path, directory, null);
    }

    private static IOException readFailure() {
        return new IOException("SOURCE_READ_FAILED: The source could not be opened as a PDF document.");
    }

    private static void cleanup(Path snapshot, Path directory, Throwable failure) throws IOException {
        IOException first = null;
        for (Path owned : new Path[] {snapshot, directory}) {
            if (owned == null) {
                continue;
            }
            try {
                Files.deleteIfExists(owned);
            } catch (IOException closingFailure) {
                IOException safe = new IOException(
                        "RESOURCE_CLOSE_FAILED: A library-owned document resource could not be closed cleanly.");
                if (failure != null) {
                    failure.addSuppressed(safe);
                } else if (first == null) {
                    first = safe;
                } else {
                    first.addSuppressed(safe);
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }
}
