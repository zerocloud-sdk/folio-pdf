package net.zerocloud.pdf.query;

import net.zerocloud.pdf.DocumentQuery;

/**
 * Reports the decoded document catalog XMP metadata packet after all
 * preceding session commands, or {@code null} when no metadata stream is
 * present.
 *
 * <p>The caller declares the maximum decoded byte count; a larger packet
 * fails with
 * {@link net.zerocloud.pdf.DocumentFailureCode#METADATA_LIMIT_EXCEEDED}.
 * Malformed metadata structures fail with
 * {@link net.zerocloud.pdf.DocumentFailureCode#QUERY_FAILED}.</p>
 *
 * <p>The packet is returned directly rather than in an
 * {@link java.util.Optional} because the nullable packet mirrors the single
 * optional catalog stream it reads; named lookups that can miss, such as
 * {@link ReadEmbeddedFile}, use {@link java.util.Optional} instead.</p>
 *
 * @since 0.1.0
 */
public final class XmpMetadata implements DocumentQuery<byte[]> {

    /** The currently supported query representation version. */
    public static final int VERSION_1 = 1;

    private final long maximumBytes;

    private XmpMetadata(long maximumBytes) {
        this.maximumBytes = maximumBytes;
    }

    /**
     * Creates a version-1 XMP metadata query.
     *
     * @param maximumBytes the maximum decoded packet byte count
     * @return the immutable query
     */
    public static XmpMetadata version1(long maximumBytes) {
        if (maximumBytes < 0L) {
            throw new IllegalArgumentException(
                    "maximumBytes must not be negative");
        }
        return new XmpMetadata(maximumBytes);
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
     * Returns the caller-declared maximum decoded byte count.
     *
     * @return the byte bound
     */
    public long getMaximumBytes() {
        return maximumBytes;
    }
}
