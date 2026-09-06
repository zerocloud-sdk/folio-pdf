package net.zerocloud.pdf.acceptance;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.zerocloud.pdf.conversion.HarfBuzzCapabilityProvider;
import net.zerocloud.pdf.provider.ProviderFailure;
import net.zerocloud.pdf.provider.ProviderFailureCode;
import net.zerocloud.pdf.provider.ProviderLimits;
import net.zerocloud.pdf.provider.ProviderRequest;
import net.zerocloud.pdf.provider.ShapingRequest;
import net.zerocloud.pdf.provider.ShapingResult;

/** Compares every official hb-shape run with the project-owned external adapter. */
final class T29NativeShapingAssertions {
    private static final String FONT_ROOT = "/net/zerocloud/pdf/acceptance/fonts/noto/";

    static Observation inspect(Path helper, Path staging) throws Exception {
        HarfBuzzCapabilityProvider provider = new HarfBuzzCapabilityProvider(helper, staging, "10.2.0",
                ProviderLimits.bounded(2 << 20, 32 + 24 * 4096, Duration.ofSeconds(10)));
        Properties pins = new Properties();
        try (InputStream input = T29NativeShapingAssertions.class.getResourceAsStream(FONT_ROOT + "fonts.properties");
                InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) { pins.load(reader); }
        Map<String, List<String[]>> runs = new LinkedHashMap<String, List<String[]>>();
        for (String[] row : T29ShapingReference.rows()) {
            String key = row[0] + " line=" + row[3] + " run=" + row[4];
            List<String[]> run = runs.get(key);
            if (run == null) { run = new ArrayList<String[]>(); runs.put(key, run); }
            run.add(row);
        }
        boolean passed = true;
        StringBuilder findings = new StringBuilder("Native expected version: 10.2.0; tolerance: zero font units\n");
        try {
            for (Map.Entry<String, List<String[]>> run : runs.entrySet()) {
                String[] first = run.getValue().get(0);
                Path font = Paths.get(T29NativeShapingAssertions.class.getResource(FONT_ROOT + first[7]).toURI());
                if (!EvidenceFiles.sha256(font).equals(pins.getProperty(first[7] + ".sha256"))) {
                    throw new IllegalStateException("The explicit T29 font hash changed");
                }
                ShapingRequest.Direction direction = "rtl".equals(first[8]) ? ShapingRequest.Direction.RIGHT_TO_LEFT
                        : ShapingRequest.Direction.LEFT_TO_RIGHT;
                ShapingRequest request = ShapingRequest.version1(Files.readAllBytes(font), T29ShapingReference.unhex(first[6]),
                        first[18], first[19], direction, 4096);
                ShapingResult result = ShapingResult.decode(provider.execute(ProviderRequest.builder(ShapingRequest.CAPABILITY_ID,
                        request.encode()).timeout(Duration.ofSeconds(10)).build()), request);
                boolean match = "10.2.0".equals(result.getEngineVersion()) && result.getUnitsPerEm() == 1000
                        && result.getDirection() == direction && result.getGlyphs().size() == run.getValue().size();
                int start = Integer.parseInt(first[5]);
                for (int index = 0; index < Math.min(result.getGlyphs().size(), run.getValue().size()); index++) {
                    ShapingResult.Glyph glyph = result.getGlyphs().get(index);
                    String[] row = run.getValue().get(index);
                    boolean same = matches(glyph, row, start);
                    match &= same;
                    findings.append(run.getKey()).append(" glyph=").append(index + 1).append(" gid=").append(glyph.getGlyphId())
                            .append(" cluster=[").append(glyph.getClusterStart() + start).append(',').append(glyph.getClusterEnd() + start)
                            .append(") advance=").append(glyph.getXAdvance()).append(',').append(glyph.getYAdvance())
                            .append(" offset=").append(glyph.getXOffset()).append(',').append(glyph.getYOffset())
                            .append(" direction=").append(result.getDirection()).append(" match=").append(same).append('\n');
                }
                findings.append(run.getKey()).append(" exact native result: ").append(match).append('\n');
                passed &= match;
            }
        } catch (ProviderFailure failure) {
            findings.append("Native observation unavailable or failed: ").append(failure.getCode()).append('\n');
            return new Observation(failure.getCode() == ProviderFailureCode.PROVIDER_UNAVAILABLE
                    ? EvidenceResult.INDETERMINATE : EvidenceResult.FAIL, findings.toString());
        }
        return new Observation(passed ? EvidenceResult.PASS : EvidenceResult.FAIL, findings.toString());
    }

    private static boolean matches(ShapingResult.Glyph glyph, String[] row, int start) {
        return glyph.getGlyphId() == Integer.parseInt(row[9])
                && glyph.getClusterStart() + start == Integer.parseInt(row[10])
                && glyph.getClusterEnd() + start == Integer.parseInt(row[11])
                && glyph.getXAdvance() == Integer.parseInt(row[12]) && glyph.getYAdvance() == Integer.parseInt(row[13])
                && glyph.getXOffset() == Integer.parseInt(row[14]) && glyph.getYOffset() == Integer.parseInt(row[15]);
    }

    static final class Observation {
        final EvidenceResult result;
        final String findings;
        Observation(EvidenceResult result, String findings) { this.result = result; this.findings = findings; }
    }

    private T29NativeShapingAssertions() { }
}
