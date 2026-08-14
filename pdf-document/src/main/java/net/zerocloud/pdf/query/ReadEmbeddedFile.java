package net.zerocloud.pdf.query;

import java.util.Objects;
import java.util.Optional;
import net.zerocloud.pdf.DocumentQuery;
import net.zerocloud.pdf.EmbeddedFileData;

/**
 * Reads one embedded file as a detached immutable value after all preceding
 * session commands.
 *
 * <p>The caller declares the maximum decoded byte count; larger content
 * fails with
 * {@link net.zerocloud.pdf.DocumentFailureCode#METADATA_LIMIT_EXCEEDED}.
 * A missing name yields an empty result; malformed embedded-file structures
 * fail with {@link net.zerocloud.pdf.DocumentFailureCode#QUERY_FAILED}.</p>
 *
 * @since 0.1.0
 */
public final class ReadEmbeddedFile
        implements DocumentQuery<Optional<EmbeddedFileData>> {

    /** The currently supported query representation version. */
    public static final int VERSION_1 = 1;

    private final String name;
    private final long maximumBytes;

    private ReadEmbeddedFile(String name, long maximumBytes) {
        this.name = name;
        this.maximumBytes = maximumBytes;
    }

    /**
     * Creates a version-1 embedded-file read query.
     *
     * @param name the embedded-file name
     * @param maximumBytes the maximum decoded byte count
     * @return the immutable query
     */
    public static ReadEmbeddedFile version1(
            String name,
            long maximumBytes) {
        if (maximumBytes < 0L) {
            throw new IllegalArgumentException(
                    "maximumBytes must not be negative");
        }
        return new ReadEmbeddedFile(
                Objects.requireNonNull(name, "name"),
                maximumBytes);
    }

    /**
     * Returns the query representation version.
     *
     * @return {@link #VERSION_1}
     */
    public int getVersion() {
        return VERSION_1;
    }

    /**
     * Returns the embedded-file name to read.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the caller-declared maximum decoded byte count.
     *
     * @return the byte bound
     */
    public long getMaximumBytes() {
        return maximumBytes;
    }
}
