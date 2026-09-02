package net.zerocloud.pdf;

/**
 * PDF text rendering modes observed and produced by the Native Interface.
 *
 * @since 0.1.0
 */
public enum TextRenderingMode {
    /** Fill glyph outlines. */ FILL(0),
    /** Stroke glyph outlines. */ STROKE(1),
    /** Fill and stroke glyph outlines. */ FILL_STROKE(2),
    /** Update text position without painting glyph outlines. */ INVISIBLE(3),
    /** Fill glyph outlines and add them to the clipping path. */ FILL_CLIP(4),
    /** Stroke glyph outlines and add them to the clipping path. */ STROKE_CLIP(5),
    /** Fill, stroke, and add glyph outlines to the clipping path. */
    FILL_STROKE_CLIP(6),
    /** Add glyph outlines to the clipping path without painting them. */
    CLIP(7);

    private final int operatorValue;

    TextRenderingMode(int operatorValue) {
        this.operatorValue = operatorValue;
    }

    /** @return the PDF {@code Tr} operand */
    public int getOperatorValue() {
        return operatorValue;
    }

    /**
     * Obtains the mode represented by one PDF {@code Tr} operand.
     *
     * @param value the integer operand from zero through seven
     * @return the corresponding mode
     * @throws IllegalArgumentException when the operand is unsupported
     */
    public static TextRenderingMode fromOperatorValue(int value) {
        for (TextRenderingMode mode : values()) {
            if (mode.operatorValue == value) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unsupported text rendering mode");
    }
}
