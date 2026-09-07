package net.zerocloud.pdf;

import java.util.Arrays;
import net.zerocloud.pdf.composition.Barcode2D;
import uk.org.okapibarcode.backend.Symbol;

/** Original raw PDF417 framing and arithmetic over the pinned Okapi symbol table. */
final class BarcodePdf417Grid extends Symbol {
    private final int columns;
    private final int rows;
    private final int correction;
    private final int[] words;

    BarcodePdf417Grid(Barcode2D declaration) throws DocumentFailure {
        int[] data;
        if (declaration.getPdf417Encoding() == Barcode2D.Pdf417Encoding.RAW) {
            data = declaration.getRawCodewords(); BarcodePdf417Raw.validate(data);
        } else { data = BarcodePdf417Compaction.encode(declaration); }
        int[] macro = macro(declaration);
        int count = data.length + macro.length;
        correction = declaration.getPdf417ErrorCorrection() >= 0 ? declaration.getPdf417ErrorCorrection()
                : count <= 40 ? 2 : count <= 160 ? 3 : count <= 320 ? 4 : 5;
        int ecc = 1 << (correction + 1), needed = count + 1 + ecc;
        int[] dimensions = dimensions(declaration,needed);
        columns = dimensions[0]; rows = dimensions[1];
        words = new int[columns * rows];
        int descriptor = words.length - ecc;
        words[0] = descriptor;
        System.arraycopy(data,0,words,1,data.length);
        Arrays.fill(words,data.length+1,descriptor-macro.length,900);
        System.arraycopy(macro,0,words,descriptor-macro.length,macro.length);
        correct(words,descriptor,ecc);
        encode(); plotSymbol();
    }
    private static int[] macro(Barcode2D declaration) {
        if (declaration.getPdf417MacroCount() == 0) { return new int[0]; }
        String fileId = declaration.getPdf417MacroFileId();
        boolean last = declaration.getPdf417MacroSegment() == declaration.getPdf417MacroCount()-1;
        int[] result = new int[7+fileId.length()/3+(last ? 1 : 0)];
        int offset = 0, segment = 100000+declaration.getPdf417MacroSegment(), count = 100000+declaration.getPdf417MacroCount();
        result[offset++] = 928; result[offset++] = segment/900; result[offset++] = segment%900;
        for (int index = 0; index < fileId.length(); index += 3) { result[offset++] = Integer.parseInt(fileId.substring(index,index+3)); }
        result[offset++] = 923; result[offset++] = 1; result[offset++] = count/900; result[offset++] = count%900;
        if (last) { result[offset] = 922; }
        return result;
    }
    private static int[] dimensions(Barcode2D declaration, int needed) throws DocumentFailure {
        int columns = declaration.getPdf417Columns(), rows = declaration.getPdf417Rows();
        if (declaration.getPdf417AspectRatio() != 0) {
            double best = Double.POSITIVE_INFINITY;
            int area = Integer.MAX_VALUE;
            for (int c = 1; c <= 30; c++) {
                for (int r = Math.max(3,(needed+c-1)/c); r <= 90 && c*r <= 928; r++) {
                    double error = Math.abs(r * declaration.getModuleHeight() / ((69+17*c)*declaration.getModuleWidth())
                            - declaration.getPdf417AspectRatio());
                    if (error < best-1e-12 || Math.abs(error-best) <= 1e-12 && c*r < area) {
                        columns = c; rows = r; best = error; area = c*r;
                    }
                }
            }
        } else {
            if (columns == 0 && rows == 0) { columns = Math.max(1,(int)(0.5+Math.sqrt((needed-1)/3.0))); }
            if (rows == 0 && columns > 0) { rows = Math.max(3,(needed+columns-1)/columns); }
            if (columns == 0 && rows > 0) { columns = (needed+rows-1)/rows; }
            if (declaration.getPdf417Columns() == 0 && declaration.getPdf417Rows() == 0
                    && (rows > 90 || columns > 30 || columns*rows > 928)) {
                int preferred = columns, area = Integer.MAX_VALUE, distance = Integer.MAX_VALUE;
                for (int c = 1; c <= 30; c++) {
                    int r = Math.max(3,(needed+c-1)/c), candidate = c*r, offset = Math.abs(c-preferred);
                    if (r <= 90 && candidate <= 928 && (candidate < area || candidate == area && offset < distance)) {
                        columns = c; rows = r; area = candidate; distance = offset;
                    }
                }
            }
        }
        if (columns < 1 || columns > 30 || rows < 3 || rows > 90 || columns*rows > 928 || columns*rows < needed) {
            throw PdfBoxBarcode2DOperations.inputFailure();
        }
        return new int[] {columns,rows};
    }
    private static void correct(int[] words, int dataCount, int count) {
        // Coefficients of the monic polynomial with roots 3^1 ... 3^count over GF(929).
        int[] coefficients = new int[count];
        int root = 1;
        for (int degree = 1; degree <= count; degree++) {
            root = root * 3 % 929; coefficients[degree-1] = 1;
            for (int index = degree-1; index >= 0; index--) {
                coefficients[index] = (929 - root * coefficients[index] % 929 + (index == 0 ? 0 : coefficients[index-1])) % 929;
            }
        }
        int[] remainder = new int[count];
        for (int index = 0; index < dataCount; index++) {
            int factor = (words[index]+remainder[count-1]) % 929;
            for (int power = count-1; power > 0; power--) {
                remainder[power] = (remainder[power-1]+929-factor*coefficients[power]%929)%929;
            }
            remainder[0] = (929-factor*coefficients[0]%929)%929;
        }
        for (int index = 0; index < count; index++) { words[dataCount+index] = (929-remainder[count-1-index])%929; }
    }
    @Override protected void encode() {
        pattern = new String[rows]; rowHeight = new int[rows]; rowCount = rows; readable = "";
        int[] indicators = {(rows-1)/3,correction*3+(rows-1)%3,columns-1};
        for (int row = 0; row < rows; row++) {
            StringBuilder bits = new StringBuilder(69+17*columns);
            bits.append("11111111010101000");
            BarcodePdf417Patterns.append(bits,row%3,30*(row/3)+indicators[row%3]);
            for (int column = 0; column < columns; column++) { BarcodePdf417Patterns.append(bits,row%3,words[row*columns+column]); }
            BarcodePdf417Patterns.append(bits,row%3,30*(row/3)+indicators[(row+2)%3]);
            bits.append("111111101000101001");
            pattern[row] = bin2pat(bits); rowHeight[row] = 1;
        }
    }
}
