package net.zerocloud.pdf.acceptance;

import com.google.zxing.common.BitArray;

/** Original MSI reader over observed scanlines: framing, complementary pulse widths, BCD and Luhn. */
final class T30MsiDecoder {
    private T30MsiDecoder() { }

    static String decode(BitArray row, int unit, boolean check) {
        int start = row.getNextSet(0), end = row.getSize();
        while (end > start && !row.get(end - 1)) { end--; }
        T30BarcodeAssertions.require((end - start) % unit == 0, "MSI module grid mismatch");
        int modules = (end - start) / unit;
        T30BarcodeAssertions.require(modules >= 19 && (modules - 7) % 12 == 0, "MSI length mismatch");
        T30BarcodeAssertions.require(bits(row, start, unit, 3).equals("110"), "MSI start mismatch");
        T30BarcodeAssertions.require(bits(row, end - 4 * unit, unit, 4).equals("1001"), "MSI stop mismatch");
        StringBuilder payload = new StringBuilder();
        for (int digit = 0; digit < (modules - 7) / 12; digit++) {
            int value = 0;
            for (int bit = 0; bit < 4; bit++) {
                String pulse = bits(row, start + (3 + digit * 12 + bit * 3) * unit, unit, 3);
                T30BarcodeAssertions.require(pulse.equals("100") || pulse.equals("110"), "MSI pulse mismatch");
                value = 2 * value + (pulse.equals("110") ? 1 : 0);
            }
            T30BarcodeAssertions.require(value <= 9, "MSI BCD digit mismatch"); payload.append(value);
        }
        if (check) {
            int sum = 0;
            for (int index = payload.length() - 1, place = 0; index >= 0; index--, place++) {
                int value = payload.charAt(index) - '0';
                if (place % 2 != 0) { value *= 2; if (value > 9) { value -= 9; } }
                sum += value;
            }
            T30BarcodeAssertions.require(sum % 10 == 0, "MSI Luhn check mismatch");
        }
        return payload.toString();
    }

    private static String bits(BitArray row, int start, int unit, int count) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) { result.append(row.get(start + index * unit + unit / 2) ? '1' : '0'); }
        return result.toString();
    }
}
