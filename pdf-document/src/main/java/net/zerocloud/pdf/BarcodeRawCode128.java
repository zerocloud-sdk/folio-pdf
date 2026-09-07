package net.zerocloud.pdf;

import uk.org.okapibarcode.backend.Code128;
import java.util.ArrayList;
import java.util.List;
import net.zerocloud.pdf.composition.Barcode1D;

/** Project-owned raw input extension using Okapi's protected symbol table and plotter. */
final class BarcodeRawCode128 extends Code128 {
    private final int[] codewords;

    BarcodeRawCode128(int[] codewords) { this.codewords = codewords; }

    /** Escapes are ordinary ASCII here; only the Native Interface's markers are functions. */
    static BarcodeRawCode128 literal(Barcode1D barcode) throws DocumentFailure {
        int set = barcode.getCodeSet() == Barcode1D.CodeSet.A ? 103 : 104;
        if (barcode.getCodeSet() == Barcode1D.CodeSet.C) { throw PdfBoxBarcodeOperations.invalidInput(); }
        List<Integer> words = new ArrayList<Integer>();
        words.add(set);
        for (int index = 0; index < barcode.getContent().length(); index++) {
            char c = barcode.getContent().charAt(index);
            if (c >= Barcode1D.FNC1 && c <= Barcode1D.FNC4) {
                // FNC4 must operate on its following data symbol, not on an inserted code-set latch.
                if (c == Barcode1D.FNC4 && barcode.getCodeSet() == Barcode1D.CodeSet.AUTO) {
                    int dataIndex = index + 1;
                    while (dataIndex < barcode.getContent().length() && barcode.getContent().charAt(dataIndex) == Barcode1D.FNC4) { dataIndex++; }
                    if (dataIndex < barcode.getContent().length()) {
                        char following = barcode.getContent().charAt(dataIndex);
                        if (set == 103 && following > 95 || set == 104 && following < 32) {
                            set = set == 103 ? 104 : 103;
                            words.add(set == 103 ? 101 : 100);
                        }
                    }
                }
                words.add(c == Barcode1D.FNC1 ? 102 : c == Barcode1D.FNC2 ? 97
                        : c == Barcode1D.FNC3 ? 96 : set == 103 ? 101 : 100);
                continue;
            }
            if ((set == 103 && c > 95) || (set == 104 && c < 32)) {
                if (barcode.getCodeSet() != Barcode1D.CodeSet.AUTO) { throw PdfBoxBarcodeOperations.invalidInput(); }
                set = set == 103 ? 104 : 103;
                words.add(set == 103 ? 101 : 100);
            }
            words.add(c < 32 ? c + 64 : c - 32);
        }
        int[] values = new int[words.size()];
        for (int index = 0; index < values.length; index++) { values[index] = words.get(index); }
        validate(values);
        return new BarcodeRawCode128(values);
    }

    static void validate(int[] words) throws DocumentFailure {
        if (words.length < 2 || words[0] < 103 || words[0] > 105) { throw PdfBoxBarcodeOperations.invalidInput(); }
        int set = words[0];
        boolean data = false;
        for (int index = 1; index < words.length; index++) {
            int word = words[index];
            if (word < 0 || word > 102) { throw PdfBoxBarcodeOperations.invalidInput(); }
            if (set == 105) {
                if (word < 100) { data = true; }
                else if (word != 102) {
                    set = word == 100 ? 104 : 103;
                    requireFollowing(words, index);
                }
            } else if (word < 96) {
                data = true;
            } else if (word == 98) {
                requireFollowing(words, index);
                if (words[++index] < 0 || words[index] > 95) { throw PdfBoxBarcodeOperations.invalidInput(); }
                data = true;
            } else if (word == (set == 103 ? 101 : 100)) {
                requireFollowing(words, index);
                int next = words[index + 1];
                if (next == word) {
                    requireFollowing(words, ++index);
                } else if (next != 98 && (next < 0 || next > 95)) {
                    throw PdfBoxBarcodeOperations.invalidInput();
                }
            } else if (word == 99 || word == (set == 103 ? 100 : 101)) {
                set = word == 99 ? 105 : (set == 103 ? 104 : 103);
                requireFollowing(words, index);
            }
        }
        if (!data) { throw PdfBoxBarcodeOperations.invalidInput(); }
    }

    private static void requireFollowing(int[] words, int index) throws DocumentFailure {
        if (index + 1 == words.length) { throw PdfBoxBarcodeOperations.invalidInput(); }
    }

    @Override
    protected void encode() {
        StringBuilder modules = new StringBuilder();
        int checksum = codewords[0];
        for (int index = 0; index < codewords.length; index++) {
            modules.append(CODE128_TABLE[codewords[index]]);
            if (index > 0) { checksum += index * codewords[index]; }
        }
        modules.append(CODE128_TABLE[checksum % 103]).append(CODE128_TABLE[106]);
        pattern = new String[] {modules.toString()};
        rowHeight = new int[] {1};
        rowCount = 1;
    }
}
