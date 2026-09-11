package net.zerocloud.pdf.itext7.kernel.pdf;

import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.zerocloud.pdf.CancellationToken;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.PdfOutputPolicy;
import net.zerocloud.pdf.PdfVersion;
import net.zerocloud.pdf.PublicationReceipt;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.WorkflowResourcePolicy;

/** Owns validated declarations and their transferred Reader snapshots. */
final class FacadeDeclarations implements AutoCloseable {
    final WorkflowRequest request;
    final CancellationToken cancellation = CancellationToken.create();
    final int sourcePageCount;
    private final Map<String, FacadeSource> sources;
    private final Map<String, PdfWriter> targets;

    FacadeDeclarations(Map<String, PdfReader> sourceReaders, String primarySource,
            Map<String, PdfWriter> targetWriters, PdfVersion outputVersion) {
        Map<String, PdfReader> readers = new LinkedHashMap<String, PdfReader>(
                Objects.requireNonNull(sourceReaders, "sources"));
        targets = Collections.unmodifiableMap(new LinkedHashMap<String, PdfWriter>(
                Objects.requireNonNull(targetWriters, "targets")));
        Map<String, FacadeSource> snapshots = new LinkedHashMap<String, FacadeSource>();
        IdentityHashMap<PdfReader, Boolean> uniqueReaders = new IdentityHashMap<PdfReader, Boolean>();
        WorkflowRequest.Builder builder = WorkflowRequest.builder().saveMode(SaveMode.REWRITE)
                .outputPolicy(PdfOutputPolicy.version(Objects.requireNonNull(outputVersion, "outputVersion")))
                .cancellationToken(cancellation);
        long reserved = 0;
        long maximum = WorkflowResourcePolicy.safeDefaults().getMaximumTemporaryStorageBytes();
        for (Map.Entry<String, PdfReader> entry : readers.entrySet()) {
            PdfReader reader = Objects.requireNonNull(entry.getValue(), "reader");
            if (uniqueReaders.put(reader, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("A facade reader may be declared only once.");
            }
            FacadeSource snapshot = reader.availableSource();
            if (snapshot.bytes > maximum - reserved) {
                throw new IllegalArgumentException("The declared facade snapshots exceed the temporary-storage budget.");
            }
            reserved += snapshot.bytes;
            snapshots.put(entry.getKey(), snapshot);
            builder.source(entry.getKey(), DocumentSource.path(snapshot.path));
        }
        if (primarySource != null) {
            if (readers.isEmpty()) {
                throw new IllegalArgumentException("An empty Source declaration cannot select a primary Source.");
            }
            builder.primarySource(primarySource);
        }
        for (Map.Entry<String, PdfWriter> entry : targets.entrySet()) {
            builder.target(entry.getKey(), Objects.requireNonNull(entry.getValue(), "writer").getTarget());
        }
        request = builder.resourcePolicy(FacadeSource.policyWithSnapshot(reserved)).build();
        sources = Collections.unmodifiableMap(snapshots);
        sourcePageCount = snapshots.isEmpty() ? 0 : snapshots.get(primarySource).pageCount;
        // All declarations and ownership states have passed before any transfer.
        for (PdfReader reader : readers.values()) {
            reader.takeSource();
        }
    }

    boolean hasSources() {
        return !sources.isEmpty();
    }

    boolean hasTargets() {
        return !targets.isEmpty();
    }

    void requireCommittedReceipts(List<PublicationReceipt> receipts) {
        if (receipts.size() != targets.size()) {
            throw invalidReceipts();
        }
        int index = 0;
        for (Map.Entry<String, PdfWriter> entry : targets.entrySet()) {
            PublicationReceipt receipt = receipts.get(index++);
            if (receipt.getStatus() != PublicationStatus.COMMITTED
                    || !entry.getKey().equals(receipt.getTargetName())
                    || !receipt.getPathTarget().equals(Optional.ofNullable(entry.getValue().getPath()))
                    || receipt.isPartialOutputPossible()) {
                throw invalidReceipts();
            }
        }
    }

    @Override
    public void close() throws IOException {
        IOException first = null;
        for (FacadeSource source : sources.values()) {
            try {
                source.close();
            } catch (IOException failure) {
                if (first == null) {
                    first = failure;
                } else {
                    first.addSuppressed(failure);
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }

    private static IllegalStateException invalidReceipts() {
        return new IllegalStateException("The Native Interface did not commit the declared publication targets.");
    }
}
