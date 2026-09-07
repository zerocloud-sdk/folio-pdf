package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.zerocloud.pdf.composition.Barcode2D;

/** Original explicit ECC200 compaction before Okapi-derived ECC and placement. */
final class BarcodeDataMatrixCompaction {
    private BarcodeDataMatrixCompaction() { }
    static int[] forCapacity(int[] words, Barcode2D declaration, int capacity) throws DocumentFailure {
        Barcode2D.DataMatrixEncoding mode = declaration.getDataMatrixEncoding();
        if (mode == Barcode2D.DataMatrixEncoding.RAW || mode == Barcode2D.DataMatrixEncoding.ASCII
                || mode == Barcode2D.DataMatrixEncoding.BASE256 || mode == Barcode2D.DataMatrixEncoding.AUTO) { return words; }
        if (mode != Barcode2D.DataMatrixEncoding.EDIFACT && words.length == capacity + 1 && words[words.length - 1] == 254) {
            return Arrays.copyOf(words, capacity);
        }
        int[] bytes = Barcode2DEncoding.of(declaration.getEncoding()).encode(declaration.getContent());
        if (mode == Barcode2D.DataMatrixEncoding.EDIFACT) {
            int tail = bytes.length % 4, tailWords = ((tail + 1) * 6 + 7) / 8;
            int prefix = words.length - tailWords;
            // With at most two words remaining ECC200 implicitly resumes ASCII.
            if (capacity - prefix >= tail && capacity - prefix <= 2) {
                List<Integer> result = new ArrayList<Integer>();
                for (int index = 0; index < prefix; index++) { result.add(words[index]); }
                ascii(result, bytes, bytes.length - tail);
                return array(result);
            }
        } else if (mode != Barcode2D.DataMatrixEncoding.X12 && words.length > capacity) {
            List<Integer> values = new ArrayList<Integer>();
            for (int value : bytes) { c40(values, value, mode == Barcode2D.DataMatrixEncoding.TEXT); }
            int header = headers(declaration, Barcode2DEncoding.of(declaration.getEncoding()).eci).size();
            if (values.size() % 3 == 2 && header + 1 + 2 * (values.size() + 1) / 3 == capacity) {
                values.add(0); // A final shift value is ignored at the exact symbol boundary.
                List<Integer> result = new ArrayList<Integer>();
                for (int index = 0; index < header; index++) { result.add(words[index]); }
                result.add(mode == Barcode2D.DataMatrixEncoding.TEXT ? 239 : 230);
                packTriplets(result, values, values.size());
                return array(result);
            }
        }
        return words;
    }
    static int[] encode(Barcode2D declaration) throws DocumentFailure {
        Barcode2D.DataMatrixEncoding mode = declaration.getDataMatrixEncoding();
        if (mode == Barcode2D.DataMatrixEncoding.RAW) {
            return declaration.getRawCodewords();
        }
        Barcode2DEncoding encoding = Barcode2DEncoding.of(declaration.getEncoding());
        int[] bytes = encoding.encode(declaration.getContent());
        List<Integer> words = headers(declaration, encoding.eci);
        if (mode == Barcode2D.DataMatrixEncoding.ASCII || mode == Barcode2D.DataMatrixEncoding.AUTO) { ascii(words, bytes, 0); }
        else if (mode == Barcode2D.DataMatrixEncoding.BASE256) { binary(words, bytes); }
        else if (mode == Barcode2D.DataMatrixEncoding.EDIFACT) { edifact(words, bytes); }
        else { triplets(words, bytes, mode); }
        return array(words);
    }
    private static List<Integer> headers(Barcode2D declaration, int eci) {
        List<Integer> words = new ArrayList<Integer>();
        if (declaration.getDataMatrixSequenceTotal() != 0) {
            words.add(233);
            words.add(16 * (declaration.getDataMatrixSequencePosition() - 1) + 17 - declaration.getDataMatrixSequenceTotal());
            words.add(1 + (declaration.getDataMatrixFileId() - 1) / 254);
            words.add(1 + (declaration.getDataMatrixFileId() - 1) % 254);
        }
        if (declaration.getDataMatrixMacro() != Barcode2D.DataMatrixMacro.NONE) {
            words.add(declaration.getDataMatrixMacro() == Barcode2D.DataMatrixMacro.MACRO_05 ? 236 : 237);
        }
        if (declaration.isDataMatrixFnc1()) { words.add(232); }
        if (declaration.isDataMatrixReaderProgramming()) { words.add(234); }
        if (eci != 3) { words.add(241); words.add(eci + 1); }
        return words;
    }
    private static void binary(List<Integer> words, int[] bytes) {
        words.add(231);
        if (bytes.length <= 249) { randomByte(words, bytes.length); }
        else { randomByte(words, bytes.length / 250 + 249); randomByte(words, bytes.length % 250); }
        for (int value : bytes) { randomByte(words, value); }
    }
    private static void randomByte(List<Integer> words, int value) {
        words.add((value + (149 * (words.size() + 1)) % 255 + 1) % 256);
    }
    private static void edifact(List<Integer> words, int[] bytes) throws DocumentFailure {
        words.add(240);
        int buffer = 0, bits = 0;
        for (int index = 0; index <= bytes.length; index++) {
            int value = index == bytes.length ? 31 : bytes[index];
            if (index < bytes.length && (value < 32 || value > 94)) { throw PdfBoxBarcode2DOperations.inputFailure(); }
            buffer = (buffer << 6) | (value & 63); bits += 6;
            while (bits >= 8) { bits -= 8; words.add((buffer >>> bits) & 255); }
        }
        if (bits > 0) { words.add((buffer << (8 - bits)) & 255); }
    }
    private static void triplets(List<Integer> words, int[] bytes, Barcode2D.DataMatrixEncoding mode) throws DocumentFailure {
        List<Integer> values = new ArrayList<Integer>();
        int prefix = 0, count = 0;
        boolean x12 = mode == Barcode2D.DataMatrixEncoding.X12;
        for (int index = 0; index < bytes.length; index++) {
            if (x12) {
                int value = "\r*> 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".indexOf((char) bytes[index]);
                if (value < 0) { throw PdfBoxBarcode2DOperations.inputFailure(); }
                values.add(value);
            } else { c40(values, bytes[index], mode == Barcode2D.DataMatrixEncoding.TEXT); }
            if (values.size() % 3 == 0) { prefix = index + 1; count = values.size(); }
        }
        if (count > 0) {
            words.add(x12 ? 238 : mode == Barcode2D.DataMatrixEncoding.TEXT ? 239 : 230);
            packTriplets(words, values, count);
            words.add(254);
        }
        ascii(words, bytes, prefix);
    }
    private static void packTriplets(List<Integer> words, List<Integer> values, int count) {
        for (int index = 0; index < count; index += 3) {
            int value = 1600 * values.get(index) + 40 * values.get(index + 1) + values.get(index + 2) + 1;
            words.add(value / 256); words.add(value % 256);
        }
    }
    private static int[] array(List<Integer> words) {
        int[] result = new int[words.size()];
        for (int index = 0; index < result.length; index++) { result[index] = words.get(index); }
        return result;
    }
    private static void c40(List<Integer> values, int value, boolean text) {
        if (value >= 128) { values.add(1); values.add(30); c40(values, value - 128, text); }
        else if (value < 32) { values.add(0); values.add(value); }
        else if (value == 32) { values.add(3); }
        else if (digit(value)) { values.add(value - '0' + 4); }
        else if (value >= (text ? 'a' : 'A') && value <= (text ? 'z' : 'Z')) { values.add(value - (text ? 'a' : 'A') + 14); }
        else if (value >= 33 && value <= 47) { values.add(1); values.add(value - 33); }
        else if (value >= 58 && value <= 64) { values.add(1); values.add(value - 58 + 15); }
        else if (value >= 91 && value <= 95) { values.add(1); values.add(value - 91 + 22); }
        else {
            values.add(2);
            values.add(text && value >= 'A' && value <= 'Z' ? value - 'A' + 1 : value - 96);
        }
    }
    private static void ascii(List<Integer> words, int[] bytes, int first) {
        for (int index = first; index < bytes.length; index++) {
            int value = bytes[index];
            if (digit(value) && index + 1 < bytes.length && digit(bytes[index + 1])) {
                words.add(130 + (value - '0') * 10 + bytes[++index] - '0');
            } else if (value >= 128) {
                words.add(235); words.add(value - 127);
            } else { words.add(value + 1); }
        }
    }
    private static boolean digit(int value) { return value >= '0' && value <= '9'; }
}
