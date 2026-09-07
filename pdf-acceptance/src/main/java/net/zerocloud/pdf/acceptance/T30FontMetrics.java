package net.zerocloud.pdf.acceptance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.zerocloud.pdf.composition.FontSelection;
import net.zerocloud.pdf.composition.FontSource;

/** Original OpenType head/cmap-4/hmtx observer for the hash-pinned T30 source font; no font backend. */
final class T30FontMetrics {
    static final String SHA256 = "b85c38ecea8a7cfb39c24e395a4007474fa5a4fc864f6ee33309eb4948d232d5";
    static final T30FontMetrics NOTO = load();
    private final byte[] bytes;
    private final ByteBuffer data;
    private final int cmap;
    private final int hmtx;
    private final int horizontalMetrics;

    private T30FontMetrics(byte[] bytes) {
        T30BarcodeAssertions.require(EvidenceFiles.sha256(bytes).equals(SHA256), "T30 source font hash mismatch");
        this.bytes = bytes; data = ByteBuffer.wrap(bytes);
        Map<String, Integer> tables = new HashMap<String, Integer>();
        for (int index = 0; index < u16(4); index++) {
            int entry = 12 + index * 16;
            tables.put(new String(bytes, entry, 4, StandardCharsets.US_ASCII), data.getInt(entry + 8));
        }
        int head = tables.get("head");
        T30BarcodeAssertions.require(u16(head + 18) == 1000 && data.getShort(head + 38) == -389
                && data.getShort(head + 42) == 1067, "T30 predeclared font units or vertical bounds mismatch");
        hmtx = tables.get("hmtx"); horizontalMetrics = u16(tables.get("hhea") + 34);
        int table = tables.get("cmap"), selected = -1;
        for (int index = 0; index < u16(table + 2); index++) {
            int entry = table + 4 + index * 8;
            if (u16(entry) == 3 && u16(entry + 2) == 1) { selected = table + data.getInt(entry + 4); }
        }
        T30BarcodeAssertions.require(selected > 0 && u16(selected) == 4, "T30 expected Windows Unicode BMP cmap-4"); cmap = selected;
    }

    FontSelection selection() { return FontSelection.explicit(FontSource.bytes(bytes)); }
    double ascent(double size) { return 1.067 * size; }
    double descent(double size) { return 0.389 * size; }
    double width(int codePoint, double size) {
        int glyph = glyph(codePoint);
        T30BarcodeAssertions.require(glyph != 0, "Reference label glyph missing: " + codePoint);
        return u16(hmtx + 4 * Math.min(glyph, horizontalMetrics - 1)) * size / 1000;
    }

    private int glyph(int codePoint) {
        int count = u16(cmap + 6) / 2;
        int end = cmap + 14, start = end + count * 2 + 2, delta = start + count * 2, range = delta + count * 2;
        for (int index = 0; index < count; index++) {
            if (codePoint > u16(end + index * 2)) { continue; }
            int first = u16(start + index * 2);
            if (codePoint < first) { return 0; }
            int offset = u16(range + index * 2), adjustment = data.getShort(delta + index * 2);
            if (offset == 0) { return (codePoint + adjustment) & 65535; }
            int glyph = u16(range + index * 2 + offset + 2 * (codePoint - first));
            return glyph == 0 ? 0 : (glyph + adjustment) & 65535;
        }
        return 0;
    }
    private int u16(int offset) { return data.getShort(offset) & 65535; }

    private static T30FontMetrics load() {
        try (InputStream input = T30FontMetrics.class.getResourceAsStream("/net/zerocloud/pdf/acceptance/fonts/noto/NotoSans-Regular.ttf")) {
            if (input == null) { throw new IOException("Missing pinned T30 source font"); }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) { bytes.write(buffer, 0, count); }
            return new T30FontMetrics(bytes.toByteArray());
        } catch (IOException failure) { throw new ExceptionInInitializerError(failure); }
    }
}
