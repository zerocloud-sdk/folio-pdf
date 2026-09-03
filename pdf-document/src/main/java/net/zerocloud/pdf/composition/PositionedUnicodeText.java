package net.zerocloud.pdf.composition;

import java.util.Objects;
import net.zerocloud.pdf.TextRenderingMode;

/**
 * One immutable, horizontal, unshaped Unicode text run at an explicit matrix.
 *
 * @since 0.1.0
 */
public final class PositionedUnicodeText {

    /** The currently supported declaration representation version. */
    public static final int VERSION_1 = 1;

    private final String text;
    private final FontSelection fontSelection;
    private final double fontSize;
    private final TextRenderingMode renderingMode;
    private final CanvasMatrix textMatrix;

    private PositionedUnicodeText(
            String text,
            FontSelection fontSelection,
            double fontSize,
            TextRenderingMode renderingMode,
            CanvasMatrix textMatrix) {
        this.text = Objects.requireNonNull(text, "text");
        this.fontSelection = Objects.requireNonNull(
                fontSelection,
                "fontSelection");
        this.fontSize = fontSize;
        this.renderingMode = Objects.requireNonNull(
                renderingMode,
                "renderingMode");
        this.textMatrix = Objects.requireNonNull(textMatrix, "textMatrix");
    }

    /**
     * Creates one version-1 positioned Unicode text run.
     *
     * <p>The T19 command accepts the non-clipping rendering modes from
     * {@link TextRenderingMode#FILL} through
     * {@link TextRenderingMode#INVISIBLE}.</p>
     */
    public static PositionedUnicodeText version1(
            String text,
            FontSelection fontSelection,
            double fontSize,
            TextRenderingMode renderingMode,
            CanvasMatrix textMatrix) {
        return new PositionedUnicodeText(
                text,
                fontSelection,
                fontSize,
                renderingMode,
                textMatrix);
    }

    /** @return {@link #VERSION_1} */
    public int getVersion() { return VERSION_1; }
    /** @return source Unicode text */
    public String getText() { return text; }
    /** @return ordered font selection */
    public FontSelection getFontSelection() { return fontSelection; }
    /** @return positive font size */
    public double getFontSize() { return fontSize; }
    /** @return declared text rendering mode; T19 accepts only modes zero through three */
    public TextRenderingMode getRenderingMode() { return renderingMode; }
    /** @return explicit text matrix */
    public CanvasMatrix getTextMatrix() { return textMatrix; }
}
