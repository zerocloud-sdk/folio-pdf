package net.zerocloud.pdf;

import java.io.InputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/**
 * A caller-declared document source.
 *
 * <p>Path resources are opened and closed by the workflow. Streams and
 * channels remain caller-owned and are never closed by the workflow. Byte
 * sources are defensively copied when declared.</p>
 *
 * @since 0.1.0
 */
public final class DocumentSource {

    enum Kind {
        PATH,
        STREAM,
        CHANNEL,
        BYTES
    }

    private final Kind kind;
    private final Path path;
    private final InputStream stream;
    private final ReadableByteChannel channel;
    private final byte[] bytes;
    private final long maximumBytes;
    private final PasswordCredential credential;

    private DocumentSource(
            Kind kind,
            Path path,
            InputStream stream,
            ReadableByteChannel channel,
            byte[] bytes,
            long maximumBytes,
            PasswordCredential credential) {
        this.kind = kind;
        this.path = path;
        this.stream = stream;
        this.channel = channel;
        this.bytes = bytes;
        this.maximumBytes = maximumBytes;
        this.credential = credential;
    }

    /**
     * Creates a source opened and closed by the workflow.
     *
     * @param path the PDF path
     * @return a path source
     */
    public static DocumentSource path(Path path) {
        return new DocumentSource(
                Kind.PATH,
                Objects.requireNonNull(path, "path"),
                null,
                null,
                null,
                Long.MAX_VALUE,
                null);
    }

    /**
     * Creates a bounded caller-owned stream source.
     *
     * @param stream the stream to read without closing
     * @param maximumBytes the maximum accepted byte count
     * @return a stream source
     */
    public static DocumentSource stream(InputStream stream, long maximumBytes) {
        Objects.requireNonNull(stream, "stream");
        requireMaximumBytes(maximumBytes);
        return new DocumentSource(
                Kind.STREAM,
                null,
                stream,
                null,
                null,
                maximumBytes,
                null);
    }

    /**
     * Creates a bounded caller-owned channel source.
     *
     * @param channel the channel to read without closing
     * @param maximumBytes the maximum accepted byte count
     * @return a channel source
     */
    public static DocumentSource channel(
            ReadableByteChannel channel,
            long maximumBytes) {
        Objects.requireNonNull(channel, "channel");
        requireMaximumBytes(maximumBytes);
        return new DocumentSource(
                Kind.CHANNEL,
                null,
                null,
                channel,
                null,
                maximumBytes,
                null);
    }

    /**
     * Creates a defensively copied bounded byte source.
     *
     * @param bytes the PDF bytes
     * @param maximumBytes the maximum accepted byte count
     * @return a bounded byte source
     */
    public static DocumentSource bytes(byte[] bytes, long maximumBytes) {
        Objects.requireNonNull(bytes, "bytes");
        requireMaximumBytes(maximumBytes);
        return new DocumentSource(
                Kind.BYTES,
                null,
                null,
                null,
                Arrays.copyOf(bytes, bytes.length),
                maximumBytes,
                null);
    }

    /**
     * Returns a source descriptor that uses the supplied password credential.
     * The credential remains caller-owned and is not destroyed by a workflow.
     *
     * @param credential the credential used only while opening this source
     * @return a new immutable source descriptor
     */
    public DocumentSource withCredential(PasswordCredential credential) {
        return new DocumentSource(
                kind,
                path,
                stream,
                channel,
                bytes,
                maximumBytes,
                Objects.requireNonNull(credential, "credential"));
    }

    private static void requireMaximumBytes(long maximumBytes) {
        if (maximumBytes < 0L) {
            throw new IllegalArgumentException(
                    "maximumBytes must be non-negative.");
        }
    }

    Kind getKind() {
        return kind;
    }

    Path getPath() {
        return path;
    }

    InputStream getStream() {
        return stream;
    }

    ReadableByteChannel getChannel() {
        return channel;
    }

    byte[] getBytes() {
        return bytes;
    }

    long getMaximumBytes() {
        return maximumBytes;
    }

    PasswordCredential getCredential() {
        return credential;
    }
}
