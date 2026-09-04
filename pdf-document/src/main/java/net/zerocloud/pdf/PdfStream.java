package net.zerocloud.pdf;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable detached stream or a bounded lazy Session view of one.
 *
 * <p>Stream bytes exposed by an inspection are decoded bytes. The associated
 * dictionary remains a lazy PDF dictionary view.</p>
 *
 * @since 0.1.0
 */
public final class PdfStream implements PdfValue {

    private final PdfStreamAccess access;

    PdfStream(PdfStreamAccess access) {
        this.access = Objects.requireNonNull(access, "access");
    }

    /**
     * Creates an immutable detached stream from decoded bytes.
     *
     * @param dictionary stream attributes excluding engine-owned length data
     * @param decodedBytes the decoded stream bytes
     * @return a detached PDF stream
     */
    public static PdfStream of(
            PdfDictionary dictionary,
            byte[] decodedBytes) {
        return new PdfStream(new DetachedStreamAccess(
                Objects.requireNonNull(dictionary, "dictionary"),
                Objects.requireNonNull(decodedBytes, "decodedBytes")));
    }

    /**
     * Returns the stream dictionary.
     *
     * @return the immutable dictionary or lazy Session view
     * @throws DocumentFailure if the lazy view is no longer usable
     */
    public PdfDictionary getDictionary() throws DocumentFailure {
        return access.getDictionary();
    }

    /**
     * Reads the decoded stream bytes under the inspection's cumulative bound.
     *
     * @return a new byte array
     * @throws DocumentFailure if the view expired, the bound is exhausted, or
     *     the stream cannot be decoded
     */
    public byte[] readBytes() throws DocumentFailure {
        return access.readBytes();
    }

    WorkflowResourceContext.OwnedBytes readBytesForWorkflow(
            WorkflowResourceContext resources)
            throws DocumentFailure {
        return access.readBytesForWorkflow(resources);
    }

    /**
     * Returns the Session Object Reference for an inspected stream.
     * Detached stream values have no reference until applied to a Session.
     *
     * @return the optional Session reference
     */
    public Optional<ObjectReference> getReference() {
        return access.getReference();
    }

    @Override
    public PdfValueKind getKind() {
        return PdfValueKind.STREAM;
    }

    private static final class DetachedStreamAccess implements PdfStreamAccess {

        private final PdfDictionary dictionary;
        private final byte[] decodedBytes;

        DetachedStreamAccess(
                PdfDictionary dictionary,
                byte[] decodedBytes) {
            this.dictionary = dictionary;
            this.decodedBytes = Arrays.copyOf(
                    decodedBytes,
                    decodedBytes.length);
        }

        @Override
        public PdfDictionary getDictionary() {
            return dictionary;
        }

        @Override
        public byte[] readBytes() {
            return Arrays.copyOf(decodedBytes, decodedBytes.length);
        }

        @Override
        public WorkflowResourceContext.OwnedBytes readBytesForWorkflow(
                WorkflowResourceContext resources) throws DocumentFailure {
            return resources.copyOwnedBytes(decodedBytes);
        }

        @Override
        public Optional<ObjectReference> getReference() {
            return Optional.empty();
        }
    }
}

interface PdfStreamAccess {

    PdfDictionary getDictionary() throws DocumentFailure;

    byte[] readBytes() throws DocumentFailure;

    WorkflowResourceContext.OwnedBytes readBytesForWorkflow(
            WorkflowResourceContext resources) throws DocumentFailure;

    java.util.Optional<ObjectReference> getReference();
}
