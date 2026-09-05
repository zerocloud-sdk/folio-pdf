package net.zerocloud.pdf;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/** Validates geometry before any raster allocation or Provider disclosure. */
final class RenderGeometry {
    final PDRectangle crop;
    final float scale;
    final int width;
    final int height;

    private RenderGeometry(PDRectangle crop, float scale, int width, int height) {
        this.crop = crop;
        this.scale = scale;
        this.width = width;
        this.height = height;
    }

    static RenderGeometry of(PDPage page, RenderOptions options) throws DocumentFailure {
        if (!positive(options.getDpi()) || !positive(options.getScale())
                || (options.getBackgroundRgb() & 0xff000000) != 0) {
            throw invalid();
        }
        PDRectangle box = options.getPageBox() == RenderOptions.PageBox.MEDIA
                ? page.getMediaBox() : page.getCropBox();
        double x = box.getLowerLeftX();
        double y = box.getLowerLeftY();
        double w = box.getWidth();
        double h = box.getHeight();
        if (!finite(x) || !finite(y) || !positive(w) || !positive(h)) {
            throw invalid();
        }
        if (options.hasCrop()) {
            double cx = options.getCropX();
            double cy = options.getCropY();
            double cw = options.getCropWidth();
            double ch = options.getCropHeight();
            if (!finite(cx) || !finite(cy) || !positive(cw) || !positive(ch)
                    || cx < x || cy < y || cx + cw > x + w || cy + ch > y + h) {
                throw invalid();
            }
            x = cx; y = cy; w = cw; h = ch;
        }
        PDRectangle crop = new PDRectangle((float) x, (float) y, (float) w, (float) h);
        if (!positive(crop.getWidth()) || !positive(crop.getHeight())
                || !finite(crop.getUpperRightX()) || !finite(crop.getUpperRightY())) {
            throw invalid();
        }
        double unit = page.getUserUnit();
        if (!positive(unit)) { throw invalid(); }
        float scale = (float) (options.getDpi() / 72d * options.getScale() * unit);
        double wp = Math.max(1d, Math.floor(crop.getWidth() * scale));
        double hp = Math.max(1d, Math.floor(crop.getHeight() * scale));
        if (!positive(scale) || !finite(wp) || !finite(hp)
                || wp > Integer.MAX_VALUE || hp > Integer.MAX_VALUE
                || wp * hp > Integer.MAX_VALUE) {
            throw RenderedPage.failure(DocumentFailureCode.RENDER_DIMENSIONS_EXCEEDED,
                    "The requested raster dimensions exceed the supported address space.");
        }
        int rotation = page.getRotation();
        return new RenderGeometry(crop, scale,
                (int) (rotation == 90 || rotation == 270 ? hp : wp),
                (int) (rotation == 90 || rotation == 270 ? wp : hp));
    }

    long pixels() { return (long) width * height; }
    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
    private static boolean positive(double value) { return finite(value) && value > 0; }
    private static DocumentFailure invalid() {
        return RenderedPage.failure(DocumentFailureCode.RENDER_OPTIONS_INVALID,
                "Rendering requires finite positive dimensions, valid colors, and a contained crop.");
    }
}
