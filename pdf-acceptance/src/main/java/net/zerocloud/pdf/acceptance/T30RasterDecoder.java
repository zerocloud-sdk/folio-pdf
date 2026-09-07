package net.zerocloud.pdf.acceptance;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import com.google.zxing.common.BitArray;
import net.zerocloud.pdf.composition.Barcode1D;

/** Samples only the actual 288-DPI raster; captions and reference pixels are never decoder inputs. */
final class T30RasterDecoder {
    private T30RasterDecoder() { }

    static String decode(BufferedImage raster, T30BarcodeProfile.Fixture fixture) throws Exception {
        Barcode1D barcode = fixture.barcode;
        AffineTransform placement = T30BarcodeAssertions.transform(fixture.placement);
        if (T30BarcodeOracle.postal(fixture)) {
            List<double[]> bars = new ArrayList<double[]>();
            // The profile declares the physical grid and data length, not the observed height bits.
            int count = fixture.encoded.length() * 5 + 2;
            for (int bar = 0; bar < count; bar++) {
                double x = barcode.getQuietZone() + bar * barcode.getPostalPitch();
                T30BarcodeAssertions.require(black(raster, placement, x + barcode.getModuleWidth() / 2,
                        barcode.getShortBarHeight() / 2), fixture.id + " postal lower bar missing");
                boolean tall = black(raster, placement, x + barcode.getModuleWidth() / 2,
                        (barcode.getShortBarHeight() + barcode.getBarHeight()) / 2);
                bars.add(new double[] {x, 0, barcode.getModuleWidth(), tall ? barcode.getBarHeight() : barcode.getShortBarHeight()});
            }
            String decoded = T30PostalDecoder.decode(bars, barcode.getMode() == Barcode1D.Mode.PLANET, barcode.getQuietZone(),
                    barcode.getPostalPitch(), barcode.getModuleWidth(), barcode.getBarHeight(), barcode.getShortBarHeight());
            T30BarcodeAssertions.require(decoded.equals(fixture.payload), fixture.id + " postal raster payload mismatch");
            return "payload=" + decoded + "; check=" + decoded.charAt(decoded.length() - 1);
        }
        BitArray row = new BitArray((int) Math.round(T30BarcodeOracle.width(fixture) / barcode.getModuleWidth() * 4));
        for (int sample = 0; sample < row.getSize(); sample++) {
            if (black(raster, placement, (sample + .5) * barcode.getModuleWidth() / 4, barcode.getBarHeight() / 2)) { row.set(sample); }
        }
        return T30BarcodeAssertions.decode(fixture, row);
    }

    private static boolean black(BufferedImage raster, AffineTransform placement, double x, double y) {
        Point2D point = placement.transform(new Point2D.Double(x, y), null);
        int pixelX = (int) Math.floor(point.getX() * 4);
        int pixelY = raster.getHeight() - 1 - (int) Math.floor(point.getY() * 4);
        T30BarcodeAssertions.require(pixelX >= 0 && pixelX < raster.getWidth() && pixelY >= 0 && pixelY < raster.getHeight(),
                "Barcode sampling grid leaves the declared page");
        int rgb = raster.getRGB(pixelX, pixelY);
        return ((rgb >> 16 & 255) + (rgb >> 8 & 255) + (rgb & 255)) < 3 * 128;
    }
}
