package net.zerocloud.pdf.acceptance;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import com.google.zxing.EncodeHintType;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.oned.Code39Writer;
import com.google.zxing.oned.CodaBarWriter;
import com.google.zxing.oned.EAN13Writer;
import com.google.zxing.oned.EAN8Writer;
import com.google.zxing.oned.UPCEWriter;
import com.google.zxing.oned.ITFWriter;
import net.zerocloud.pdf.composition.Barcode1D;

/** Independent module oracle built only with ZXing's public writer; no Okapi tables or reflection. */
final class T30BarcodeOracle {
    private static final boolean[][] CODE128 = code128Patterns();
    private T30BarcodeOracle() { }

    static boolean postal(T30BarcodeProfile.Fixture fixture) {
        return fixture.barcode.getMode() == Barcode1D.Mode.POSTNET || fixture.barcode.getMode() == Barcode1D.Mode.PLANET;
    }

    static double width(T30BarcodeProfile.Fixture fixture) {
        Barcode1D barcode = fixture.barcode;
        return (postal(fixture) ? (fixture.encoded.length() * 5 + 1) * barcode.getPostalPitch() + barcode.getModuleWidth()
                : pattern(fixture).length * barcode.getModuleWidth()) + 2 * barcode.getQuietZone();
    }

    static List<double[]> bars(T30BarcodeProfile.Fixture fixture) {
        if (postal(fixture)) { return postalBars(fixture); }
        boolean[] pattern = pattern(fixture);
        List<double[]> bars = new ArrayList<double[]>();
        Barcode1D barcode = fixture.barcode;
        for (int index = 0; index < pattern.length;) {
            int end = index + 1;
            while (end < pattern.length && pattern[end] == pattern[index]) { end++; }
            if (pattern[index]) {
                double guard = guard(barcode.getMode(), index) ? barcode.getGuardExtension() : 0;
                bars.add(new double[] {barcode.getQuietZone() + index * barcode.getModuleWidth(), -guard,
                    (end - index) * barcode.getModuleWidth(), barcode.getBarHeight() + guard});
            }
            index = end;
        }
        return bars;
    }

    private static boolean guard(Barcode1D.Mode mode, int module) {
        switch (mode) {
            case EAN13: return module < 3 || module >= 45 && module < 50 || module >= 92 && module < 95;
            case EAN8: return module < 3 || module >= 31 && module < 36 || module >= 64 && module < 67;
            case UPCA: return module < 10 || module >= 45 && module < 50 || module >= 85 && module < 95;
            case UPCE: return module < 3 || module >= 45 && module < 51;
            default: return false;
        }
    }

    /** USPS legacy digit-height table, with explicit full-height frames. */
    static List<double[]> postalBars(T30BarcodeProfile.Fixture fixture) {
        String[] postnet = {"11000", "00011", "00101", "00110", "01001", "01010", "01100", "10001", "10010", "10100"};
        StringBuilder heights = new StringBuilder("1");
        for (int index = 0; index < fixture.encoded.length(); index++) {
            String pattern = postnet[fixture.encoded.charAt(index) - '0'];
            for (int bar = 0; bar < 5; bar++) {
                boolean tall = pattern.charAt(bar) == '1';
                if (fixture.barcode.getMode() == Barcode1D.Mode.PLANET) { tall = !tall; }
                heights.append(tall ? '1' : '0');
            }
        }
        heights.append('1');
        List<double[]> bars = new ArrayList<double[]>();
        for (int index = 0; index < heights.length(); index++) {
            Barcode1D barcode = fixture.barcode;
            bars.add(new double[] {barcode.getQuietZone() + index * barcode.getPostalPitch(), 0, barcode.getModuleWidth(),
                heights.charAt(index) == '1' ? barcode.getBarHeight() : barcode.getShortBarHeight()});
        }
        return bars;
    }

    static boolean[] pattern(T30BarcodeProfile.Fixture fixture) {
        boolean[] main = mainPattern(fixture);
        if (fixture.barcode.getSupplement().isEmpty()) { return main; }
        boolean[] extension = supplement(fixture.barcode.getSupplement());
        boolean[] combined = Arrays.copyOf(main, main.length + 9 + extension.length);
        System.arraycopy(extension, 0, combined, main.length + 9, extension.length);
        return combined;
    }

    static boolean[] mainPattern(T30BarcodeProfile.Fixture fixture) {
        if (fixture.words.length == 0) {
            switch (fixture.barcode.getMode()) {
                case EAN13: return new EAN13Writer().encode(fixture.encoded);
                case EAN8: return new EAN8Writer().encode(fixture.encoded);
                // UPC-A has the same modules as a leading-zero EAN-13.
                case UPCA: return new EAN13Writer().encode("0" + fixture.encoded);
                case UPCE: return new UPCEWriter().encode(fixture.encoded);
                case SUPPLEMENT2: case SUPPLEMENT5: return supplement(fixture.encoded);
                case INTERLEAVED_2_OF_5: return ratio(new ITFWriter().encode(fixture.encoded), 3, (int) fixture.barcode.getWideToNarrowRatio());
                case MSI: return msi(fixture.encoded);
                case CODE39: case CODE39_EXTENDED: case CODABAR: break;
                default: throw new IllegalArgumentException("No independent writer for " + fixture.id);
            }
            boolean[] pattern = fixture.barcode.getMode() == Barcode1D.Mode.CODABAR
                    ? new CodaBarWriter().encode(fixture.encoded) : new Code39Writer().encode(fixture.encoded);
            return ratio(pattern, 2, (int) fixture.barcode.getWideToNarrowRatio());
        }
        int check = fixture.words[0];
        for (int index = 1; index < fixture.words.length; index++) { check += index * fixture.words[index]; }
        T30BarcodeAssertions.require(check % 103 == fixture.check, fixture.id + " literal check word is inconsistent");
        boolean[] result = new boolean[(fixture.words.length + 1) * 11 + 13];
        int offset = 0;
        for (int word : fixture.words) {
            System.arraycopy(CODE128[word], 0, result, offset, 11); offset += 11;
        }
        System.arraycopy(CODE128[fixture.check], 0, result, offset, 11); offset += 11;
        System.arraycopy(CODE128[106], 0, result, offset, 13);
        return result;
    }

    /** MSI BCD pulse-width coding: each data bit occupies three narrow modules. */
    private static boolean[] msi(String value) {
        StringBuilder bits = new StringBuilder("110");
        for (int index = 0; index < value.length(); index++) {
            int digit = value.charAt(index) - '0';
            for (int bit = 3; bit >= 0; bit--) { bits.append((digit & 1 << bit) == 0 ? "100" : "110"); }
        }
        bits.append("1001");
        boolean[] result = new boolean[bits.length()];
        for (int index = 0; index < result.length; index++) { result[index] = bits.charAt(index) == '1'; }
        return result;
    }

    /** GS1 add-on structure, L/G parity and digit tables; independent of the product encoder. */
    static boolean[] supplement(String value) {
        String[] left = {"0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011"};
        int[] fiveParity = {24, 20, 18, 17, 12, 6, 3, 10, 9, 5};
        int parity;
        if (value.length() == 2) { parity = Integer.parseInt(value) % 4; }
        else {
            int sum = 0;
            for (int index = 0; index < 5; index++) { sum += (value.charAt(index) - '0') * (index % 2 == 0 ? 3 : 9); }
            parity = fiveParity[sum % 10];
        }
        StringBuilder bits = new StringBuilder("1011");
        for (int index = 0; index < value.length(); index++) {
            if (index != 0) { bits.append("01"); }
            String pattern = left[value.charAt(index) - '0'];
            if ((parity & 1 << (value.length() - index - 1)) != 0) {
                for (int bit = 6; bit >= 0; bit--) { bits.append(pattern.charAt(bit) == '1' ? '0' : '1'); }
            } else { bits.append(pattern); }
        }
        boolean[] result = new boolean[bits.length()];
        for (int index = 0; index < result.length; index++) { result[index] = bits.charAt(index) == '1'; }
        return result;
    }

    /** Public writers use fixed wide modules; profile ratios rescale each standard wide run. */
    private static boolean[] ratio(boolean[] pattern, int sourceWide, int targetWide) {
        int size = 0;
        for (int index = 0; index < pattern.length;) {
            int end = index + 1;
            while (end < pattern.length && pattern[end] == pattern[index]) { end++; }
            size += end - index == sourceWide ? targetWide : end - index;
            index = end;
        }
        boolean[] result = new boolean[size];
        int offset = 0;
        for (int index = 0; index < pattern.length;) {
            int end = index + 1;
            while (end < pattern.length && pattern[end] == pattern[index]) { end++; }
            int width = end - index == sourceWide ? targetWide : end - index;
            Arrays.fill(result, offset, offset + width, pattern[index]); offset += width;
            index = end;
        }
        return result;
    }

    /** Code C exposes every word 0–99 as a decimal pair; explicit functions expose 100–102. */
    private static boolean[][] code128Patterns() {
        boolean[][] table = new boolean[107][];
        for (int word = 0; word < 100; word++) {
            boolean[] encoded = encode(Integer.toString(100 + word).substring(1), "C");
            table[word] = Arrays.copyOfRange(encoded, 11, 22);
        }
        table[100] = Arrays.copyOfRange(encode("\u00f4A", "B"), 11, 22);
        table[101] = Arrays.copyOfRange(encode("\u00f4A", "A"), 11, 22);
        table[102] = Arrays.copyOfRange(encode("\u00f1A", "B"), 11, 22);
        table[103] = Arrays.copyOfRange(encode("A", "A"), 0, 11);
        boolean[] codeB = encode("A", "B");
        table[104] = Arrays.copyOfRange(codeB, 0, 11);
        table[105] = Arrays.copyOfRange(encode("12", "C"), 0, 11);
        table[106] = Arrays.copyOfRange(codeB, codeB.length - 13, codeB.length);
        return table;
    }

    private static boolean[] encode(String input, String codeSet) {
        return new Code128Writer().encode(input, Collections.singletonMap(EncodeHintType.FORCE_CODE_SET, codeSet));
    }
}
