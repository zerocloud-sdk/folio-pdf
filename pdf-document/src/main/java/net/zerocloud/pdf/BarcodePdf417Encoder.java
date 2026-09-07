package net.zerocloud.pdf;

import net.zerocloud.pdf.composition.Barcode2D;
import uk.org.okapibarcode.backend.Pdf417;
import uk.org.okapibarcode.backend.OkapiInputException;

/** Strict literal input and module-unit rows over the pinned PDF417 encoder. */
final class BarcodePdf417Encoder extends Pdf417 {
    static BarcodePdf417Encoder create(Barcode2D declaration, WorkflowResourceContext resources) throws DocumentFailure {
        if (declaration.getPdf417AspectRatio() == 0) {
            try { return new BarcodePdf417Encoder(declaration,declaration.getPdf417Columns(),declaration.getPdf417Rows()); }
            catch (OkapiInputException sizing) {
                if (declaration.getPdf417Columns() != 0 || declaration.getPdf417Rows() != 0) { throw sizing; }
                BarcodePdf417Encoder best = null;
                int bestArea = Integer.MAX_VALUE, bestDistance = Integer.MAX_VALUE;
                for (int columns = 1; columns <= 30; columns++) {
                    resources.checkpoint();
                    BarcodePdf417Encoder candidate;
                    try { candidate = new BarcodePdf417Encoder(declaration,columns,0); }
                    catch (OkapiInputException tooSmall) { continue; }
                    int area = columns*candidate.getRows();
                    int distance = Math.abs(columns-(int)(0.5+Math.sqrt((area-1)/3.0)));
                    if (area <= 928 && (area < bestArea || area == bestArea && distance < bestDistance)) {
                        best = candidate; bestArea = area; bestDistance = distance;
                    }
                }
                if (best == null) { throw sizing; }
                return best;
            }
        }
        int bestColumns = 0, bestRows = 0, bestArea = Integer.MAX_VALUE;
        double bestError = Double.POSITIVE_INFINITY;
        for (int columns = 1; columns <= 30; columns++) {
            resources.checkpoint();
            BarcodePdf417Encoder minimum;
            try { minimum = new BarcodePdf417Encoder(declaration,columns,0); }
            catch (OkapiInputException tooSmall) { continue; }
            for (int rows = minimum.getRows(); rows <= 90 && columns * rows <= 928; rows++) {
                double ratio = rows * declaration.getModuleHeight() / (minimum.getWidth() * declaration.getModuleWidth());
                double error = Math.abs(ratio - declaration.getPdf417AspectRatio());
                int area = columns * rows;
                if (error < bestError - 1e-12 || Math.abs(error - bestError) <= 1e-12 && area < bestArea) {
                    bestColumns = columns; bestRows = rows; bestArea = area; bestError = error;
                }
            }
        }
        if (bestColumns == 0) { throw PdfBoxBarcode2DOperations.inputFailure(); }
        return new BarcodePdf417Encoder(declaration,bestColumns,bestRows);
    }
    private BarcodePdf417Encoder(Barcode2D declaration, int columns, int rows) throws DocumentFailure {
        setBarHeight(1);
        setForceByteCompaction(declaration.getPdf417Encoding() == Barcode2D.Pdf417Encoding.BINARY);
        if (columns != 0) { setDataColumns(columns); }
        if (rows != 0) { setRows(rows); }
        if (declaration.getPdf417ErrorCorrection() >= 0) { setPreferredEccLevel(declaration.getPdf417ErrorCorrection()); }
        Barcode2DEncoding encoding = Barcode2DEncoding.of(declaration.getEncoding());
        content = declaration.getContent(); inputData = encoding.encode(content); eciMode = encoding.eci;
        encode(); plotSymbol();
    }
    @Override protected void eciProcess() { /* Strict bytes already supplied. */ }
}
