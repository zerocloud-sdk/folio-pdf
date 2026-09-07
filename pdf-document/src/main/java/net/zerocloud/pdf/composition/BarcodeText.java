package net.zerocloud.pdf.composition;

import java.util.Objects;
import java.util.Optional;

/**
 * Explicit font and point-size declaration for a barcode's human-readable label.
 * Version 1 uses the unshaped T19 font selection, embedding and mapping contract.
 *
 * <p>Defaults are centered below the complete horizontal barcode box, with a
 * 3-point gap between the nearest bar edge and the source-font bounding box.
 * The barcode placement matrix also transforms the label. A wider caption
 * can extend beyond the bar box; it is neither wrapped nor clipped.</p>
 *
 * <p>Mandatory check digits remain visible. Optional generated checks and
 * Code39/Codabar guards are hidden by default. Alternate text replaces the
 * complete caption and is required for raw Code128 labels. Captions must be
 * nonempty and contain at most 1024 UTF-16 units; font limits also apply.
 * No label option changes encoded bars, discovers fonts or performs shaping.</p>
 * @since 0.1.0
 */
public final class BarcodeText {
    /** Current declaration version. */
    public static final int VERSION_1 = 1;
    /** Side of the bars occupied by the label. */
    public enum Position { BELOW, ABOVE }
    /** Alignment within the bar box including its horizontal quiet zones. */
    public enum Alignment { LEFT, CENTER, RIGHT }
    private final FontSelection fontSelection;
    private final FontLimits fontLimits;
    private final double fontSize;
    private final boolean showChecksum;
    private final boolean showStartStop;
    private final String alternateText;
    private final Position position;
    private final Alignment alignment;
    private final double gap;

    private BarcodeText(Builder builder) {
        fontSelection = builder.fontSelection; fontLimits = builder.fontLimits; fontSize = builder.fontSize;
        showChecksum = builder.showChecksum; showStartStop = builder.showStartStop; alternateText = builder.alternateText;
        position = builder.position; alignment = builder.alignment; gap = builder.gap;
    }
    /** Begins a label declaration with explicit font policy and bounds. */
    public static Builder builder(FontSelection selection, FontLimits limits, double size) {
        return new Builder(selection, limits, size);
    }
    /** @return declaration version */
    public int getVersion() { return VERSION_1; }
    /** @return font selection */
    public FontSelection getFontSelection() { return fontSelection; }
    /** @return font-operation bounds */
    public FontLimits getFontLimits() { return fontLimits; }
    /** @return font size in points */
    public double getFontSize() { return fontSize; }
    /** @return whether an optional generated checksum is shown; mandatory checks are always shown */
    public boolean isShowChecksum() { return showChecksum; }
    /** @return whether Code39/Codabar start and stop characters are shown */
    public boolean isShowStartStop() { return showStartStop; }
    /** @return an explicit replacement caption, required for raw Code128 labels */
    public Optional<String> getAlternateText() { return Optional.ofNullable(alternateText); }
    /** @return label side, below by default */
    public Position getPosition() { return position; }
    /** @return horizontal label alignment, centered by default */
    public Alignment getAlignment() { return alignment; }
    /** @return gap from the nearest bar edge to the font bounding box in points; default 3 */
    public double getGap() { return gap; }

    /** Records label intent without accessing font sources. */
    public static final class Builder {
        private final FontSelection fontSelection;
        private final FontLimits fontLimits;
        private final double fontSize;
        private boolean showChecksum;
        private boolean showStartStop;
        private String alternateText;
        private Position position = Position.BELOW;
        private Alignment alignment = Alignment.CENTER;
        private double gap = 3;
        private Builder(FontSelection selection, FontLimits limits, double size) {
            fontSelection = Objects.requireNonNull(selection, "fontSelection");
            fontLimits = Objects.requireNonNull(limits, "fontLimits"); fontSize = size;
        }
        /** Includes optional generated checksum characters. @return this builder */
        public Builder showChecksum(boolean value) { showChecksum = value; return this; }
        /** Includes start/stop characters for Code39 or Codabar. @return this builder */
        public Builder showStartStop(boolean value) { showStartStop = value; return this; }
        /** Replaces the entire caption without changing encoded bars. @return this builder */
        public Builder alternateText(String value) { alternateText = Objects.requireNonNull(value, "alternateText"); return this; }
        /** Selects the side of the bars. @return this builder */
        public Builder position(Position value) { position = Objects.requireNonNull(value, "position"); return this; }
        /** Aligns the caption against the complete horizontal bar box. @return this builder */
        public Builder alignment(Alignment value) { alignment = Objects.requireNonNull(value, "alignment"); return this; }
        /** Sets the nonnegative bar-to-font-box gap in points. @return this builder */
        public Builder gap(double value) { gap = value; return this; }
        /** @return an immutable snapshot */
        public BarcodeText build() { return new BarcodeText(this); }
    }
}
