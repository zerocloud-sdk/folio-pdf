package net.zerocloud.pdf.acceptance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Plain-data reader shared by the native and reopened T29 observations. */
final class T29ShapingReference {
    static List<String[]> rows() throws IOException {
        List<String[]> rows = new ArrayList<String[]>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(T29ShapingReference.class.getResourceAsStream(
                "/net/zerocloud/pdf/acceptance/shaping/T29-glyphs.tsv"), StandardCharsets.UTF_8))) {
            reader.readLine();
            for (String row; (row = reader.readLine()) != null;) { rows.add(row.split("\t")); }
        }
        return rows;
    }

    static String unhex(String text) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < text.length(); index += 4) {
            result.append((char) Integer.parseInt(text.substring(index, index + 4), 16));
        }
        return result.toString();
    }

    private T29ShapingReference() { }
}
