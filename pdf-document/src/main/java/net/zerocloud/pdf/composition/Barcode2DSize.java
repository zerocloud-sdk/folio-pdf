package net.zerocloud.pdf.composition;

/** Detached geometry from bounded barcode measurement; no document references. @since 0.1.0 */
public final class Barcode2DSize {
    /** Current result representation. */ public static final int VERSION_1 = 1;
    private final int matrixWidth;
    private final int matrixHeight;
    private final double widthPoints;
    private final double heightPoints;
    private Barcode2DSize(int matrixWidth, int matrixHeight, double widthPoints, double heightPoints) {
        if (matrixWidth <= 0 || matrixHeight <= 0 || !Double.isFinite(widthPoints) || !Double.isFinite(heightPoints)
                || widthPoints <= 0 || heightPoints <= 0) { throw new IllegalArgumentException("Invalid barcode size."); }
        this.matrixWidth = matrixWidth; this.matrixHeight = matrixHeight;
        this.widthPoints = widthPoints; this.heightPoints = heightPoints;
    }
    /** Creates an immutable geometry observation. */
    public static Barcode2DSize version1(int matrixWidth, int matrixHeight, double widthPoints, double heightPoints) {
        return new Barcode2DSize(matrixWidth,matrixHeight,widthPoints,heightPoints);
    }
    /** @return representation version */ public int getVersion() { return VERSION_1; }
    /** @return matrix width in modules, excluding quiet zones */ public int getMatrixWidth() { return matrixWidth; }
    /** @return matrix height in module rows, excluding quiet zones */ public int getMatrixHeight() { return matrixHeight; }
    /** @return full local box width in points, including quiet zones */ public double getWidthPoints() { return widthPoints; }
    /** @return full local box height in points, including quiet zones */ public double getHeightPoints() { return heightPoints; }
}
