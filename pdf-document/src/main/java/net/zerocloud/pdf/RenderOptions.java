package net.zerocloud.pdf;

import java.util.Objects;

/**
 * Immutable version-1 page rendering policy. Defaults are 72 DPI, scale 1,
 * CropBox, sRGB, opaque white, and visible existing annotation appearances.
 * Geometry is in unrotated PDF user coordinates. See {@code docs/rendering.md}.
 *
 * @since 0.1.0
 */
public final class RenderOptions {
    /** Effective PDF box used before an optional explicit crop. */
    public enum PageBox { MEDIA, CROP }
    /** PNG samples: sRGB or equal sRGB components using integer luma. */
    public enum ColorMode { RGB, GRAY }
    /** Composite onto the declared background, or preserve page transparency. */
    public enum AlphaMode { OPAQUE, PRESERVE }
    /** Render existing visible appearances, or omit every annotation. */
    public enum AnnotationMode { SHOW, HIDE }

    private final double dpi;
    private final double scale;
    private final PageBox pageBox;
    private final ColorMode colorMode;
    private final AlphaMode alphaMode;
    private final AnnotationMode annotationMode;
    private final int backgroundRgb;
    private final boolean cropped;
    private final double x;
    private final double y;
    private final double width;
    private final double height;

    private RenderOptions(Builder builder) {
        dpi = builder.dpi;
        scale = builder.scale;
        pageBox = builder.pageBox;
        colorMode = builder.colorMode;
        alphaMode = builder.alphaMode;
        annotationMode = builder.annotationMode;
        backgroundRgb = builder.backgroundRgb;
        cropped = builder.cropped;
        x = builder.x;
        y = builder.y;
        width = builder.width;
        height = builder.height;
    }

    public static Builder builder() { return new Builder(); }
    public static RenderOptions defaults() { return builder().build(); }
    public double getDpi() { return dpi; }
    public double getScale() { return scale; }
    public PageBox getPageBox() { return pageBox; }
    public ColorMode getColorMode() { return colorMode; }
    public AlphaMode getAlphaMode() { return alphaMode; }
    public AnnotationMode getAnnotationMode() { return annotationMode; }
    public int getBackgroundRgb() { return backgroundRgb; }
    public boolean hasCrop() { return cropped; }
    public double getCropX() { return x; }
    public double getCropY() { return y; }
    public double getCropWidth() { return width; }
    public double getCropHeight() { return height; }

    /**
     * Builds immutable options. Numeric values are validated at the workflow
     * seam, yielding a stable checked failure in both execution profiles.
     */
    public static final class Builder {
        private double dpi = 72;
        private double scale = 1;
        private PageBox pageBox = PageBox.CROP;
        private ColorMode colorMode = ColorMode.RGB;
        private AlphaMode alphaMode = AlphaMode.OPAQUE;
        private AnnotationMode annotationMode = AnnotationMode.SHOW;
        private int backgroundRgb = 0xffffff;
        private boolean cropped;
        private double x;
        private double y;
        private double width;
        private double height;

        private Builder() { }
        public Builder dpi(double value) { dpi = value; return this; }
        public Builder scale(double value) { scale = value; return this; }
        public Builder pageBox(PageBox value) {
            pageBox = Objects.requireNonNull(value, "pageBox"); return this;
        }
        public Builder colorMode(ColorMode value) {
            colorMode = Objects.requireNonNull(value, "colorMode"); return this;
        }
        public Builder alphaMode(AlphaMode value) {
            alphaMode = Objects.requireNonNull(value, "alphaMode"); return this;
        }
        public Builder annotationMode(AnnotationMode value) {
            annotationMode = Objects.requireNonNull(value, "annotationMode"); return this;
        }
        /** Sets opaque sRGB background; ignored when alpha is preserved. */
        public Builder backgroundRgb(int value) { backgroundRgb = value; return this; }
        /** Sets an explicit rectangle wholly contained in the selected box. */
        public Builder crop(double cropX, double cropY, double cropWidth, double cropHeight) {
            cropped = true; x = cropX; y = cropY; width = cropWidth; height = cropHeight;
            return this;
        }
        public RenderOptions build() { return new RenderOptions(this); }
    }
}
