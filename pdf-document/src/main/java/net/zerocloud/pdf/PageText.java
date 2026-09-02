package net.zerocloud.pdf;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Detached immutable Page Text in content-stream execution order.
 *
 * <p>No reading-order sort, whitespace, or line break is inferred. A marked-
 * content {@code ActualText} value replaces its enclosed aggregate-text
 * contributions once while all source items remain inspectable.</p>
 *
 * @since 0.1.0
 */
public final class PageText {

    private final int pageNumber;
    private final int rotation;
    private final BigDecimal userUnit;
    private final BigDecimal cropBoxLeft;
    private final BigDecimal cropBoxBottom;
    private final BigDecimal cropBoxRight;
    private final BigDecimal cropBoxTop;
    private final String text;
    private final List<TextItem> textItems;
    private final List<MarkedContentSequence> markedContentSequences;

    PageText(
            int pageNumber,
            int rotation,
            BigDecimal userUnit,
            BigDecimal cropBoxLeft,
            BigDecimal cropBoxBottom,
            BigDecimal cropBoxRight,
            BigDecimal cropBoxTop,
            String text,
            List<TextItem> textItems,
            List<MarkedContentSequence> markedContentSequences) {
        this.pageNumber = pageNumber;
        this.rotation = rotation;
        this.userUnit = Objects.requireNonNull(userUnit, "userUnit");
        this.cropBoxLeft = Objects.requireNonNull(cropBoxLeft, "cropBoxLeft");
        this.cropBoxBottom = Objects.requireNonNull(cropBoxBottom, "cropBoxBottom");
        this.cropBoxRight = Objects.requireNonNull(cropBoxRight, "cropBoxRight");
        this.cropBoxTop = Objects.requireNonNull(cropBoxTop, "cropBoxTop");
        this.text = Objects.requireNonNull(text, "text");
        this.textItems = Collections.unmodifiableList(
                new ArrayList<TextItem>(textItems));
        this.markedContentSequences = Collections.unmodifiableList(
                new ArrayList<MarkedContentSequence>(markedContentSequences));
    }

    /** @return one-based page number */ public int getPageNumber() { return pageNumber; }
    /** @return clockwise page display rotation */ public int getRotation() { return rotation; }
    /** @return default-user-space unit size in multiples of 1/72 inch */
    public BigDecimal getUserUnit() { return userUnit; }
    /** @return crop-box left */ public BigDecimal getCropBoxLeft() { return cropBoxLeft; }
    /** @return crop-box bottom */ public BigDecimal getCropBoxBottom() { return cropBoxBottom; }
    /** @return crop-box right */ public BigDecimal getCropBoxRight() { return cropBoxRight; }
    /** @return crop-box top */ public BigDecimal getCropBoxTop() { return cropBoxTop; }
    /** Returns mapped text without inferred whitespace or line breaks. @return text */
    public String getText() { return text; }
    /** Returns text-showing items in execution order. @return immutable items */
    public List<TextItem> getTextItems() { return textItems; }

    /** Returns marked-content sequences in begin-operator order. @return values */
    public List<MarkedContentSequence> getMarkedContentSequences() {
        return markedContentSequences;
    }
}
