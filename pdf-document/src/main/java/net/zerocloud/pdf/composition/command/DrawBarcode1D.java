package net.zerocloud.pdf.composition.command;

import java.util.Objects;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.composition.Barcode1D;
import net.zerocloud.pdf.composition.CanvasMatrix;

/** Appends one vector barcode at an explicit affine placement. @since 0.1.0 */
public final class DrawBarcode1D implements DocumentCommand {
    /** Current command version. */
    public static final int VERSION_1 = 1;
    private final int pageNumber;
    private final Barcode1D barcode;
    private final CanvasMatrix placement;
    private DrawBarcode1D(int pageNumber, Barcode1D barcode, CanvasMatrix placement) {
        this.pageNumber = pageNumber;
        this.barcode = Objects.requireNonNull(barcode, "barcode");
        this.placement = Objects.requireNonNull(placement, "placement");
    }
    /** Creates a command targeting a one-based page. */
    public static DrawBarcode1D version1(int pageNumber, Barcode1D barcode, CanvasMatrix placement) {
        return new DrawBarcode1D(pageNumber, barcode, placement);
    }
    /** @return representation version */
    public int getVersion() { return VERSION_1; }
    /** @return one-based target page */
    public int getPageNumber() { return pageNumber; }
    /** @return semantic barcode declaration */
    public Barcode1D getBarcode() { return barcode; }
    /** @return transform from barcode-local coordinates to page coordinates */
    public CanvasMatrix getPlacement() { return placement; }
}
