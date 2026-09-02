package net.zerocloud.pdf.composition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.zerocloud.pdf.TextRenderingMode;

/**
 * A versioned, immutable sequence of page Canvas instructions.
 *
 * <p>The builder records caller intent without emitting a raw PDF content
 * stream. The Document Engine validates the complete state machine, numeric
 * values, glyph codes, and resource references before a {@code DrawCanvas}
 * command mutates the document. Every shown glyph therefore has an explicit
 * Canvas Font, font size, rendering mode, and text matrix.</p>
 *
 * @since 0.1.0
 */
public final class CanvasProgram {

    /** The currently supported Canvas Program representation version. */
    public static final int VERSION_1 = 1;

    private final List<Instruction> instructions;

    private CanvasProgram(Builder builder) {
        this.instructions = Collections.unmodifiableList(
                new ArrayList<Instruction>(builder.instructions));
    }

    /** Starts an ordered version-1 Canvas Program. @return a new builder */
    public static Builder version1() {
        return new Builder();
    }

    /** @return {@link #VERSION_1} */
    public int getVersion() {
        return VERSION_1;
    }

    /** @return the number of recorded instructions */
    public int getInstructionCount() {
        return instructions.size();
    }

    /**
     * Returns the closed, immutable instruction sequence for project-owned
     * interpreters and diagnostic tooling.
     *
     * <p>Instructions are immutable data; callers cannot implement or inject
     * instruction behavior.</p>
     *
     * @return the immutable instruction sequence
     */
    public List<Instruction> getInstructions() {
        return instructions;
    }

    /** The closed version-1 instruction kinds. @since 0.1.0 */
    public enum Kind {
        SAVE_STATE,
        RESTORE_STATE,
        TRANSFORM,
        MOVE_TO,
        LINE_TO,
        CURVE_TO,
        CLOSE_PATH,
        STROKE,
        FILL,
        CLIP,
        BEGIN_TEXT,
        SET_TEXT_MATRIX,
        SHOW_GLYPH,
        END_TEXT
    }

    /** One immutable instruction in a Canvas Program. @since 0.1.0 */
    public static final class Instruction {

        private final Kind kind;
        private final double[] numbers;
        private final CanvasMatrix matrix;
        private final CanvasWindingRule windingRule;
        private final CanvasFont font;
        private final TextRenderingMode renderingMode;
        private final byte[] glyphCode;

        Instruction(
                Kind kind,
                double[] numbers,
                CanvasMatrix matrix,
                CanvasWindingRule windingRule,
                CanvasFont font,
                TextRenderingMode renderingMode,
                byte[] glyphCode) {
            this.kind = kind;
            this.numbers = numbers == null ? null : numbers.clone();
            this.matrix = matrix;
            this.windingRule = windingRule;
            this.font = font;
            this.renderingMode = renderingMode;
            this.glyphCode = glyphCode == null ? null : glyphCode.clone();
        }

        /** @return the instruction kind */
        public Kind getKind() { return kind; }
        /** @return a defensive copy of numeric operands, or {@code null} */
        public double[] getNumbers() {
            return numbers == null ? null : numbers.clone();
        }
        /** @return the matrix operand, or {@code null} */
        public CanvasMatrix getMatrix() { return matrix; }
        /** @return the winding-rule operand, or {@code null} */
        public CanvasWindingRule getWindingRule() { return windingRule; }
        /** @return the Font operand, or {@code null} */
        public CanvasFont getFont() { return font; }
        /** @return the text rendering mode, or {@code null} */
        public TextRenderingMode getRenderingMode() { return renderingMode; }
        /** @return a defensive copy of the glyph code, or {@code null} */
        public byte[] getGlyphCode() {
            return glyphCode == null ? null : glyphCode.clone();
        }
    }

    /** Builds one immutable ordered Canvas Program. */
    public static final class Builder {

        private final List<Instruction> instructions =
                new ArrayList<Instruction>();

        private Builder() {
        }

        /** Saves the current graphics state. @return this builder */
        public Builder saveState() {
            return add(Kind.SAVE_STATE);
        }

        /** Restores the most recently saved graphics state. @return this builder */
        public Builder restoreState() {
            return add(Kind.RESTORE_STATE);
        }

        /** Concatenates an affine transformation. @return this builder */
        public Builder transform(CanvasMatrix matrix) {
            instructions.add(new Instruction(
                    Kind.TRANSFORM,
                    null,
                    Objects.requireNonNull(matrix, "matrix"),
                    null,
                    null,
                    null,
                    null));
            return this;
        }

        /** Starts a new path subpath. @return this builder */
        public Builder moveTo(double x, double y) {
            return addNumbers(Kind.MOVE_TO, x, y);
        }

        /** Adds a straight path segment. @return this builder */
        public Builder lineTo(double x, double y) {
            return addNumbers(Kind.LINE_TO, x, y);
        }

        /** Adds a cubic Bézier path segment. @return this builder */
        public Builder curveTo(
                double control1X,
                double control1Y,
                double control2X,
                double control2Y,
                double endX,
                double endY) {
            return addNumbers(
                    Kind.CURVE_TO,
                    control1X,
                    control1Y,
                    control2X,
                    control2Y,
                    endX,
                    endY);
        }

        /** Closes the current path subpath. @return this builder */
        public Builder closePath() {
            return add(Kind.CLOSE_PATH);
        }

        /** Strokes and consumes the current path. @return this builder */
        public Builder stroke() {
            return add(Kind.STROKE);
        }

        /** Fills and consumes the current path. @return this builder */
        public Builder fill(CanvasWindingRule windingRule) {
            return addWinding(Kind.FILL, windingRule);
        }

        /** Applies and consumes the current path as a clipping path. */
        public Builder clip(CanvasWindingRule windingRule) {
            return addWinding(Kind.CLIP, windingRule);
        }

        /**
         * Begins an explicitly configured text scope. The supplied matrix
         * positions the first glyph; each later glyph requires a fresh
         * {@link #setTextMatrix(CanvasMatrix)} instruction.
         *
         * @return this builder
         */
        public Builder beginText(
                CanvasFont font,
                double fontSize,
                TextRenderingMode renderingMode,
                CanvasMatrix textMatrix) {
            instructions.add(new Instruction(
                    Kind.BEGIN_TEXT,
                    new double[] {fontSize},
                    Objects.requireNonNull(textMatrix, "textMatrix"),
                    null,
                    Objects.requireNonNull(font, "font"),
                    Objects.requireNonNull(renderingMode, "renderingMode"),
                    null));
            return this;
        }

        /** Positions the next glyph with an explicit text matrix. */
        public Builder setTextMatrix(CanvasMatrix textMatrix) {
            instructions.add(new Instruction(
                    Kind.SET_TEXT_MATRIX,
                    null,
                    Objects.requireNonNull(textMatrix, "textMatrix"),
                    null,
                    null,
                    null,
                    null));
            return this;
        }

        /** Shows exactly one already encoded font character code. */
        public Builder showGlyph(byte[] encodedCharacterCode) {
            instructions.add(new Instruction(
                    Kind.SHOW_GLYPH,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Objects.requireNonNull(
                            encodedCharacterCode,
                            "encodedCharacterCode")));
            return this;
        }

        /** Ends the current text scope. @return this builder */
        public Builder endText() {
            return add(Kind.END_TEXT);
        }

        /**
         * Builds the immutable program. Cross-instruction validation occurs
         * when the containing Document Command executes.
         *
         * @return the Canvas Program
         */
        public CanvasProgram build() {
            return new CanvasProgram(this);
        }

        private Builder add(Kind kind) {
            instructions.add(new Instruction(
                    kind, null, null, null, null, null, null));
            return this;
        }

        private Builder addNumbers(Kind kind, double... numbers) {
            instructions.add(new Instruction(
                    kind, numbers, null, null, null, null, null));
            return this;
        }

        private Builder addWinding(
                Kind kind,
                CanvasWindingRule windingRule) {
            instructions.add(new Instruction(
                    kind,
                    null,
                    null,
                    Objects.requireNonNull(windingRule, "windingRule"),
                    null,
                    null,
                    null));
            return this;
        }
    }
}
