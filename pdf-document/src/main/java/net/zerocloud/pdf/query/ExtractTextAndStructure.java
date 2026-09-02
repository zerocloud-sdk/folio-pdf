package net.zerocloud.pdf.query;

import java.util.Objects;
import net.zerocloud.pdf.DocumentQuery;
import net.zerocloud.pdf.ExtractionLimits;
import net.zerocloud.pdf.TextStructureExtraction;

/**
 * Extracts bounded detached Page Text and Tagged PDF Logical Structure.
 *
 * <p>Page and text items retain content-stream execution order. The result
 * remains usable after the Session ends. Exhausting any declared bound fails
 * with
 * {@link net.zerocloud.pdf.DocumentFailureCode#EXTRACTION_LIMIT_EXCEEDED}.</p>
 *
 * <p>The version-1 ordering, coordinate system, mapping confidence, marked-
 * content, role, language, and unsupported-case contracts are documented in
 * {@code docs/text-logical-structure.md}.</p>
 *
 * @since 0.1.0
 */
public final class ExtractTextAndStructure
        implements DocumentQuery<TextStructureExtraction> {

    /** The currently supported query representation version. */
    public static final int VERSION_1 = 1;

    private final ExtractionLimits limits;

    private ExtractTextAndStructure(ExtractionLimits limits) {
        this.limits = limits;
    }

    /** Creates a bounded version-1 query. @param limits all extraction bounds */
    public static ExtractTextAndStructure version1(ExtractionLimits limits) {
        return new ExtractTextAndStructure(
                Objects.requireNonNull(limits, "limits"));
    }

    /** @return {@link #VERSION_1} */ public int getVersion() { return VERSION_1; }
    /** @return the caller-declared limits */
    public ExtractionLimits getLimits() { return limits; }
}
