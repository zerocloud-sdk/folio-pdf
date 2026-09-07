package net.zerocloud.pdf.acceptance;

import com.google.zxing.common.BitArray;

/** Original GS1 add-on decoder consuming actual scanlines, including start, separators and parity. */
final class T30SupplementDecoder {
    private T30SupplementDecoder() { }

    static String decode(BitArray row, int unit, int digits) {
        String[] left = {"0001101", "0011001", "0010011", "0111101", "0100011",
            "0110001", "0101111", "0111011", "0110111", "0001011"};
        int x = row.getNextSet(0);
        T30BarcodeAssertions.require(bits(row, x, unit, 4).equals("1011"), "Supplement start mismatch");
        x += 4 * unit;
        int parity = 0;
        StringBuilder payload = new StringBuilder();
        for (int index = 0; index < digits; index++) {
            String pattern = bits(row, x, unit, 7);
            int found = -1, variant = 0;
            for (int digit = 0; digit < 10; digit++) {
                String reverse = new StringBuilder(left[digit]).reverse().toString();
                String even = reverse.replace('0', 'x').replace('1', '0').replace('x', '1');
                if (pattern.equals(left[digit])) { found = digit; variant = 0; }
                if (pattern.equals(even)) { found = digit; variant = 1; }
            }
            T30BarcodeAssertions.require(found >= 0, "Unknown supplement digit");
            payload.append(found); parity = (parity << 1) | variant; x += 7 * unit;
            if (index + 1 < digits) {
                T30BarcodeAssertions.require(bits(row, x, unit, 2).equals("01"), "Supplement separator mismatch"); x += 2 * unit;
            }
        }
        int expected;
        if (digits == 2) { expected = Integer.parseInt(payload.toString()) % 4; }
        else {
            int[] parityByCheck = {24, 20, 18, 17, 12, 6, 3, 10, 9, 5};
            int odd = (payload.charAt(0) - '0') + (payload.charAt(2) - '0') + (payload.charAt(4) - '0');
            int even = (payload.charAt(1) - '0') + (payload.charAt(3) - '0');
            expected = parityByCheck[(3 * odd + 9 * even) % 10];
        }
        T30BarcodeAssertions.require(parity == expected, "Supplement parity mismatch");
        T30BarcodeAssertions.require(row.getNextSet(x) == row.getSize(), "Unexpected marks after supplement");
        return payload.toString();
    }

    private static String bits(BitArray row, int x, int unit, int count) {
        StringBuilder bits = new StringBuilder();
        for (int index = 0; index < count; index++) {
            int sample = x + index * unit + unit / 2;
            T30BarcodeAssertions.require(sample < row.getSize(), "Truncated supplement");
            bits.append(row.get(sample) ? '1' : '0');
        }
        return bits.toString();
    }
}
