package net.zerocloud.pdf.itext7.kernel.pdf.canvas.parser;

import java.util.Objects;
import net.zerocloud.pdf.ExtractionLimits;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfPage;

/**
 * Reads deterministic Page Text through the owning document's Native Query.
 * No whitespace, reading order or layout is inferred.
 * @since 0.1.0
 */
public final class PdfTextExtractor {

    static {
        try {
            Class.forName(PdfDocument.class.getName(), true, PdfDocument.class.getClassLoader());
        } catch (ClassNotFoundException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private PdfTextExtractor() {
    }

    /**
     * Reads Page Text using the fixed finite Foundation convenience profile.
     * Bounds are 10,000 pages, 100,000 page-tree nodes and content streams,
     * content depth 32, 64 MiB decoded bytes, 1,000,000 text items,
     * 2,000,000 Unicode code points, 1,000,000 ToUnicode mappings and font-data
     * entries, 100,000 marked-content sequences and structure elements,
     * marked-content and structure depth 128, 250,000 structure items and
     * 10,000 role mappings. Bounds apply to the complete document.
     * @param page non-null open page handle
     * @return detached text in content execution order
     */
    public static String getTextFromPage(PdfPage page) {
        return getTextFromPage(page, ExtractionLimits.builder()
                .maximumPages(10000).maximumPageTreeNodes(100000)
                .maximumContentStreams(100000).maximumContentStreamDepth(32)
                .maximumDecodedBytes(64L << 20).maximumTextItems(1000000)
                .maximumUnicodeCodePoints(2000000).maximumToUnicodeMappings(1000000)
                .maximumFontDataEntries(1000000).maximumMarkedContentSequences(100000)
                .maximumMarkedContentDepth(128).maximumStructureElements(100000)
                .maximumStructureItems(250000).maximumStructureDepth(128)
                .maximumRoleMappings(10000).build());
    }

    /**
     * Returns one page's aggregate text after the complete document Query succeeds.
     * ActualText replacement and uncertain mappings follow the Native contract.
     * @param page non-null open page handle
     * @param limits non-null document-wide extraction bounds
     * @return detached text in content execution order
     */
    public static String getTextFromPage(PdfPage page, ExtractionLimits limits) {
        return Objects.requireNonNull(page, "page").getPageText(limits).getText();
    }
}
