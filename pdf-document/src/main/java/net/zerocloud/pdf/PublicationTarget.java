package net.zerocloud.pdf;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A caller-declared publication destination.
 *
 * <p>Path targets use staged replacement. Stream targets remain caller-owned
 * and are flushed but never closed by the workflow.</p>
 *
 * @since 0.1.0
 */
public final class PublicationTarget {

    enum Kind {
        PATH,
        STREAM
    }

    private final Kind kind;
    private final Path path;
    private final OutputStream stream;
    private final boolean retainTemporaryAccounting;

    private PublicationTarget(
            Kind kind,
            Path path,
            OutputStream stream,
            boolean retainTemporaryAccounting) {
        this.kind = kind;
        this.path = path;
        this.stream = stream;
        this.retainTemporaryAccounting = retainTemporaryAccounting;
    }

    /**
     * Creates a path target committed by staged replacement.
     *
     * @param path the path to replace
     * @return a path publication target
     */
    public static PublicationTarget path(Path path) {
        return new PublicationTarget(
                Kind.PATH,
                Objects.requireNonNull(path, "path"),
                null,
                false);
    }

    /**
     * Creates a caller-owned stream target.
     *
     * @param stream the stream to write and flush without closing
     * @return a stream publication target
     */
    public static PublicationTarget stream(OutputStream stream) {
        return new PublicationTarget(
                Kind.STREAM,
                null,
                Objects.requireNonNull(stream, "stream"),
                false);
    }

    static PublicationTarget workerPath(Path path) {
        return new PublicationTarget(
                Kind.PATH,
                Objects.requireNonNull(path, "path"),
                null,
                true);
    }

    Kind getKind() {
        return kind;
    }

    Path getPath() {
        return path;
    }

    OutputStream getStream() {
        return stream;
    }

    boolean retainsTemporaryAccounting() {
        return retainTemporaryAccounting;
    }
}
