package net.zerocloud.pdf.acceptance;

import java.util.List;

/** Original postal reader: frame bars, physical grid, 7/4/2/1/0 weights and modulo-ten digit sum. */
final class T30PostalDecoder {
    private T30PostalDecoder() { }

    static String decode(List<double[]> bars, boolean planet, double quiet, double pitch, double width, double full, double shortHeight) {
        T30BarcodeAssertions.require(bars.size() >= 7 && (bars.size() - 2) % 5 == 0, "Postal frame count mismatch");
        T30BarcodeAssertions.near(full, bars.get(0)[3], "postal start height");
        T30BarcodeAssertions.near(full, bars.get(bars.size() - 1)[3], "postal stop height");
        for (int index = 0; index < bars.size(); index++) {
            double[] bar = bars.get(index);
            T30BarcodeAssertions.near(quiet + index * pitch, bar[0], "postal pitch");
            T30BarcodeAssertions.near(0, bar[1], "postal baseline");
            T30BarcodeAssertions.near(width, bar[2], "postal bar width");
        }
        int[] weights = {7, 4, 2, 1, 0};
        StringBuilder payload = new StringBuilder();
        int sum = 0;
        for (int index = 1; index < bars.size() - 1; index += 5) {
            int value = 0, selected = 0;
            for (int bit = 0; bit < 5; bit++) {
                double height = bars.get(index + bit)[3];
                boolean tall = Math.abs(height - full) <= 0.0001;
                T30BarcodeAssertions.near(tall ? full : shortHeight, height, "postal data bar height");
                if (tall != planet) { value += weights[bit]; selected++; }
            }
            T30BarcodeAssertions.require(selected == 2, "Postal two-of-five selection mismatch");
            int digit = value == 11 ? 0 : value;
            T30BarcodeAssertions.require(digit <= 9, "Postal digit mismatch");
            payload.append(digit); sum += digit;
        }
        T30BarcodeAssertions.require(sum % 10 == 0, "Postal check digit mismatch");
        return payload.toString();
    }
}
