package net.zerocloud.pdf.composition;

import java.io.InputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * An explicit TrueType font-program source.
 *
 * <p>Byte input is copied when declared. A Path is opened and closed by the
 * Document Workflow. Streams and channels remain caller-owned and are never
 * closed by Folio PDF.</p>
 *
 * @since 0.1.0
 */
public final class FontSource {

    /** Closed version-1 source forms. */
    public enum SourceKind {
        /** A workflow-owned Path handle. */
        PATH,
        /** A caller-owned InputStream. */
        STREAM,
        /** A caller-owned ReadableByteChannel. */
        CHANNEL,
        /** Defensively copied bytes. */
        BYTES
    }

    private final SourceKind sourceKind;
    private final Path path;
    private final InputStream stream;
    private final ReadableByteChannel channel;
    private final byte[] bytes;

    private FontSource(
            SourceKind sourceKind,
            Path path,
            InputStream stream,
            ReadableByteChannel channel,
            byte[] bytes) {
        this.sourceKind = sourceKind;
        this.path = path;
        this.stream = stream;
        this.channel = channel;
        this.bytes = bytes;
    }

    /** Declares a Path that the workflow opens and closes. @return source */
    public static FontSource path(Path path) {
        return new FontSource(
                SourceKind.PATH,
                Objects.requireNonNull(path, "path"),
                null,
                null,
                null);
    }

    /** Declares a caller-owned stream that the workflow never closes. */
    public static FontSource stream(InputStream stream) {
        return new FontSource(
                SourceKind.STREAM,
                null,
                Objects.requireNonNull(stream, "stream"),
                null,
                null);
    }

    /** Declares a caller-owned channel that the workflow never closes. */
    public static FontSource channel(ReadableByteChannel channel) {
        return new FontSource(
                SourceKind.CHANNEL,
                null,
                null,
                Objects.requireNonNull(channel, "channel"),
                null);
    }

    /** Declares bytes that are copied immediately. @return source */
    public static FontSource bytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return new FontSource(
                SourceKind.BYTES,
                null,
                null,
                null,
                Arrays.copyOf(bytes, bytes.length));
    }

    static List<FontSource> validatedCopy(
            FontSource[] sources,
            String emptyMessage) {
        Objects.requireNonNull(sources, "sources");
        if (sources.length == 0) {
            throw new IllegalArgumentException(emptyMessage);
        }
        FontSource[] copy = sources.clone();
        for (FontSource source : copy) {
            Objects.requireNonNull(source, "source");
        }
        return Arrays.asList(copy);
    }

    /** @return the closed source kind */
    public SourceKind getSourceKind() {
        return sourceKind;
    }

    /** @return the declared Path when this is a Path source */
    public Optional<Path> getPath() {
        return Optional.ofNullable(path);
    }

    /** @return the caller-owned stream when this is a stream source */
    public Optional<InputStream> getStream() {
        return Optional.ofNullable(stream);
    }

    /** @return the caller-owned channel when this is a channel source */
    public Optional<ReadableByteChannel> getChannel() {
        return Optional.ofNullable(channel);
    }

    /** @return a defensive byte copy when this is a byte source */
    public Optional<byte[]> getBytes() {
        return bytes == null
                ? Optional.<byte[]>empty()
                : Optional.of(Arrays.copyOf(bytes, bytes.length));
    }

    /**
     * Returns the byte count without creating the defensive copy returned by
     * {@link #getBytes()}.
     *
     * @return the declared byte-source length, or empty for other source kinds
     */
    public OptionalLong getByteLength() {
        return bytes == null
                ? OptionalLong.empty()
                : OptionalLong.of(bytes.length);
    }
}
