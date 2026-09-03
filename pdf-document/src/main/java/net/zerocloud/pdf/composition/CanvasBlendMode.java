package net.zerocloud.pdf.composition;

/** Closed PDF blend modes supported by Canvas Transparency State. */
public enum CanvasBlendMode {
    NORMAL("Normal"),
    MULTIPLY("Multiply"),
    SCREEN("Screen"),
    OVERLAY("Overlay"),
    DARKEN("Darken"),
    LIGHTEN("Lighten"),
    COLOR_DODGE("ColorDodge"),
    COLOR_BURN("ColorBurn"),
    HARD_LIGHT("HardLight"),
    SOFT_LIGHT("SoftLight"),
    DIFFERENCE("Difference"),
    EXCLUSION("Exclusion"),
    HUE("Hue"),
    SATURATION("Saturation"),
    COLOR("Color"),
    LUMINOSITY("Luminosity");

    private final String pdfName;

    CanvasBlendMode(String pdfName) {
        this.pdfName = pdfName;
    }

    /** @return the standards-defined PDF name without a leading slash */
    public String getPdfName() { return pdfName; }
}
