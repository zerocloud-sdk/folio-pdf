package net.zerocloud.pdf;

/**
 * Caller-declared bounds for one lazy PDF Value inspection view.
 *
 * @since 0.1.0
 */
public final class PdfInspectionLimits {

    private final long maximumTraversedValues;
    private final long maximumDecodedStreamBytes;

    private PdfInspectionLimits(
            long maximumTraversedValues,
            long maximumDecodedStreamBytes) {
        this.maximumTraversedValues = maximumTraversedValues;
        this.maximumDecodedStreamBytes = maximumDecodedStreamBytes;
    }

    /**
     * Declares the cumulative traversal and decoded-stream bounds.
     *
     * @param maximumTraversedValues maximum values obtained from containers
     * @param maximumDecodedStreamBytes maximum decoded stream bytes read
     * @return immutable inspection limits
     */
    public static PdfInspectionLimits of(
            long maximumTraversedValues,
            long maximumDecodedStreamBytes) {
        if (maximumTraversedValues < 0L) {
            throw new IllegalArgumentException(
                    "maximumTraversedValues must be non-negative.");
        }
        if (maximumDecodedStreamBytes < 0L) {
            throw new IllegalArgumentException(
                    "maximumDecodedStreamBytes must be non-negative.");
        }
        return new PdfInspectionLimits(
                maximumTraversedValues,
                maximumDecodedStreamBytes);
    }

    /**
     * Returns the maximum values obtainable through this view's containers.
     *
     * @return the traversal bound
     */
    public long getMaximumTraversedValues() {
        return maximumTraversedValues;
    }

    /**
     * Returns the maximum decoded bytes readable through this view's streams.
     *
     * @return the decoded-stream byte bound
     */
    public long getMaximumDecodedStreamBytes() {
        return maximumDecodedStreamBytes;
    }
}
