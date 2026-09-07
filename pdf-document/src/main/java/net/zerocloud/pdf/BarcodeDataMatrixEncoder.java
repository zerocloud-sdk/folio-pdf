package net.zerocloud.pdf;

import net.zerocloud.pdf.composition.Barcode2D;
import uk.org.okapibarcode.backend.DataMatrix;
import uk.org.okapibarcode.backend.OkapiInputException;
import uk.org.okapibarcode.backend.Symbol;

/** Strict literal input over Okapi's ECC200 encoder; no FNC escape syntax. */
final class BarcodeDataMatrixEncoder extends DataMatrix {
    static Symbol create(Barcode2D declaration) throws DocumentFailure {
        if (declaration.getDataMatrixEncoding() != Barcode2D.DataMatrixEncoding.AUTO
                || declaration.getDataMatrixMacro() != Barcode2D.DataMatrixMacro.NONE
                    && (declaration.isDataMatrixFnc1() || Barcode2DEncoding.of(declaration.getEncoding()).eci != 3)) {
            return new BarcodeDataMatrixGrid(BarcodeDataMatrixCompaction.encode(declaration), declaration);
        }
        if (declaration.getDataMatrixWidth() == 0 && declaration.getDataMatrixHeight() == 0) {
            return new BarcodeDataMatrixEncoder(declaration, 0);
        }
        for (BarcodeDataMatrixSize size : BarcodeDataMatrixSize.matching(declaration.getDataMatrixWidth(), declaration.getDataMatrixHeight())) {
            try { return new BarcodeDataMatrixEncoder(declaration, size.okapiIndex); }
            catch (OkapiInputException tooSmall) { /* Try the next size satisfying the declared dimension. */ }
        }
        throw PdfBoxBarcode2DOperations.inputFailure();
    }
    private BarcodeDataMatrixEncoder(Barcode2D declaration, int size) throws DocumentFailure {
        setPreferredSize(size);
        setReaderInit(declaration.isDataMatrixReaderProgramming());
        if (declaration.isDataMatrixFnc1()) { setDataType(DataType.GS1); }
        if (declaration.getDataMatrixSequenceTotal() != 0) {
            setStructuredAppendPosition(declaration.getDataMatrixSequencePosition());
            setStructuredAppendTotal(declaration.getDataMatrixSequenceTotal());
            setStructuredAppendFileId(declaration.getDataMatrixFileId());
        }
        Barcode2DEncoding encoding = Barcode2DEncoding.of(declaration.getEncoding());
        content = declaration.getContent();
        if (declaration.getDataMatrixMacro() != Barcode2D.DataMatrixMacro.NONE) {
            content = "[)>\u001e" + (declaration.getDataMatrixMacro() == Barcode2D.DataMatrixMacro.MACRO_05 ? "05" : "06")
                    + "\u001d" + content + "\u001e\u0004";
        }
        inputData = encoding.encode(content);
        eciMode = encoding.eci;
        encode();
        plotSymbol();
    }
    @Override protected void eciProcess() { /* Strict bytes already supplied. */ }
}
