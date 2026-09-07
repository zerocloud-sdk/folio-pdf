package net.zerocloud.pdf.composition.query;

import java.util.Objects;
import net.zerocloud.pdf.DocumentQuery;
import net.zerocloud.pdf.composition.Barcode2D;
import net.zerocloud.pdf.composition.Barcode2DSize;

/** Validates and measures a symbol without creating pages or painting content. @since 0.1.0 */
public final class MeasureBarcode2D implements DocumentQuery<Barcode2DSize> {
    /** Current query representation. */ public static final int VERSION_1 = 1;
    private final Barcode2D barcode;
    private MeasureBarcode2D(Barcode2D barcode) { this.barcode = Objects.requireNonNull(barcode,"barcode"); }
    /** Creates a query that uses the same encoding, sizing and limits as drawing. */
    public static MeasureBarcode2D version1(Barcode2D barcode) { return new MeasureBarcode2D(barcode); }
    /** @return representation version */ public int getVersion() { return VERSION_1; }
    /** @return immutable input declaration */ public Barcode2D getBarcode() { return barcode; }
}
