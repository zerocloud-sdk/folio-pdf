package net.zerocloud.pdf.query;

import java.util.Objects;
import net.zerocloud.pdf.DocumentQuery;
import net.zerocloud.pdf.DocumentResourceInventory;
import net.zerocloud.pdf.ImageByteAccess;
import net.zerocloud.pdf.ResourceExtractionLimits;

/**
 * Extracts a bounded detached Image and Document Resource Inventory.
 *
 * <p>Ordering, identity, byte selection, lifecycle, Page Usage, limits, and
 * unsupported cases are documented in
 * {@code docs/image-resource-extraction.md}.</p>
 *
 * @since 0.1.0
 */
public final class ExtractImagesAndResources
        implements DocumentQuery<DocumentResourceInventory> {

    /** The currently supported query representation version. */
    public static final int VERSION_1 = 1;

    private final ResourceExtractionLimits limits;
    private final ImageByteAccess byteAccess;

    private ExtractImagesAndResources(
            ResourceExtractionLimits limits,
            ImageByteAccess byteAccess) {
        this.limits = limits;
        this.byteAccess = byteAccess;
    }

    /**
     * Creates a bounded version-1 query.
     *
     * @param limits all mandatory extraction bounds
     * @param byteAccess explicit whole-query image-byte selection
     * @return the query
     */
    public static ExtractImagesAndResources version1(
            ResourceExtractionLimits limits,
            ImageByteAccess byteAccess) {
        return new ExtractImagesAndResources(
                Objects.requireNonNull(limits, "limits"),
                Objects.requireNonNull(byteAccess, "byteAccess"));
    }

    /** Returns the query version. @return {@link #VERSION_1} */
    public int getVersion() { return VERSION_1; }

    /** Returns all mandatory limits. @return limits */
    public ResourceExtractionLimits getLimits() { return limits; }

    /** Returns the explicit image-byte selection. @return selection */
    public ImageByteAccess getByteAccess() { return byteAccess; }
}
