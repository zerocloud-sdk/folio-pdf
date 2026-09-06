package net.zerocloud.pdf.acceptance;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.zerocloud.pdf.CharacterMapping;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ExtractionLimits;
import net.zerocloud.pdf.FontResource;
import net.zerocloud.pdf.ImageByteAccess;
import net.zerocloud.pdf.PageText;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.ResourceExtractionLimits;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.TextGeometry;
import net.zerocloud.pdf.TextItem;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.ExtractImagesAndResources;
import net.zerocloud.pdf.query.ExtractTextAndStructure;

/** Expected Unicode, source glyph IDs and geometry are offline independent fontTools values. */
final class T28UnicodeSemanticAssertions {
    static final double TOLERANCE = 0.0001;

    static Observation inspect(WorkflowOutcome<Void> creation, Path artifact, WorkflowExecutionProfile mode) throws Exception {
        boolean receipt = creation.getPublicationReceipts().size() == 1
                && creation.getPublicationReceipts().get(0).getStatus() == PublicationStatus.COMMITTED
                && creation.getExecutionProfile() == mode && T28UnicodeProducts.CAPABILITY.equals(creation.getCapabilityId());
        List<String[]> rows = new ArrayList<String[]>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(T28UnicodeProducts.resource("unicode/T28-glyphs.tsv"),
                StandardCharsets.UTF_8))) {
            reader.readLine();
            String row;
            while ((row = reader.readLine()) != null) { rows.add(row.split("\t")); }
        }
        String[] profiles = T28UnicodeProducts.properties("unicode/T28-corpus.properties").getProperty("profiles").split(",");
        try {
            Observation value = new DocumentWorkflow().execute(WorkflowRequest.builder()
                    .source("primary", DocumentSource.path(artifact)).primarySource("primary")
                    .executionProfile(mode).saveMode(SaveMode.REWRITE).build(), session -> observe(session, rows, profiles)).getResult();
            return new Observation(receipt && value.passed, "Publication Receipt and mode: " + receipt + "\n" + value.findings);
        } catch (DocumentFailure failure) {
            return new Observation(false, "Public reopened observation failed: " + failure.getCode() + " / " + failure.getMessage() + "\n");
        } catch (RuntimeException failure) {
            return new Observation(false, "Public reopened observation failed: " + failure.getClass().getSimpleName() + "\n");
        }
    }

    private static Observation observe(DocumentSession session, List<String[]> rows, String[] profiles) throws DocumentFailure {
        List<PageText> pages = session.query(ExtractTextAndStructure.version1(ExtractionLimits.builder()
                .maximumPages(8).maximumPageTreeNodes(32).maximumContentStreams(4096).maximumContentStreamDepth(8)
                .maximumDecodedBytes(4 << 20).maximumTextItems(2048).maximumUnicodeCodePoints(2048)
                // Independent fontTools subsets retain the source GID domain, including gaps.
                .maximumToUnicodeMappings(2048).maximumFontDataEntries(500000).maximumMarkedContentSequences(4)
                .maximumMarkedContentDepth(4).maximumStructureElements(4).maximumStructureItems(4)
                .maximumStructureDepth(4).maximumRoleMappings(4).build())).getPages();
        List<FontResource> fonts = session.query(ExtractImagesAndResources.version1(ResourceExtractionLimits.builder()
                .maximumPages(8).maximumPageTreeNodes(32).maximumTraversedResourceValues(16384).maximumResourceTraversalDepth(16)
                .maximumDecodedPixels(0).maximumDecompressedBytes(4 << 20).maximumReturnedBytes(0).build(), ImageByteAccess.NONE)).getFonts();
        boolean passed = pages.size() == profiles.length;
        StringBuilder findings = new StringBuilder("Page count: ").append(pages.size()).append('\n');
        if (!passed) { return new Observation(false, findings.toString()); }
        for (int pageIndex = 0; pageIndex < profiles.length; pageIndex++) {
            PageText page = pages.get(pageIndex);
            boolean match = near(page.getCropBoxLeft().doubleValue(), 0) && near(page.getCropBoxBottom().doubleValue(), 0)
                    && near(page.getCropBoxRight().doubleValue(), 612) && near(page.getCropBoxTop().doubleValue(), 792);
            Set<String> expectedFonts = new HashSet<String>();
            int count = 0;
            StringBuilder expectedText = new StringBuilder();
            for (String[] row : rows) {
                if (!profiles[pageIndex].equals(row[0])) { continue; }
                int codePoint = Integer.parseInt(row[3], 16);
                String unicode = new String(Character.toChars(codePoint));
                expectedText.append(unicode);
                expectedFonts.add(row[4]);
                if (count >= page.getTextItems().size()) { match = false; count++; continue; }
                TextItem item = page.getTextItems().get(count++);
                TextGeometry geometry = item.getGeometry();
                byte[] code = item.getCharacterMapping().getSourceCode();
                boolean glyph = unicode.equals(item.getTextContribution())
                        && item.getCharacterMapping().getConfidence() == CharacterMapping.Confidence.EXPLICIT
                        && code.length == 2 && ((code[0] & 255) * 256 + (code[1] & 255)) == Integer.parseInt(row[5])
                        && near(geometry.getE().doubleValue(), Double.parseDouble(row[6]))
                        && near(geometry.getF().doubleValue(), Double.parseDouble(row[7]))
                        && near(geometry.getAdvanceX().doubleValue(), Double.parseDouble(row[8]))
                        && near(geometry.getAdvanceY().doubleValue(), 0) && near(geometry.getA().doubleValue(), 12)
                        && near(geometry.getD().doubleValue(), 12) && near(geometry.getB().doubleValue(), 0)
                        && near(geometry.getC().doubleValue(), 0);
                match &= glyph;
                findings.append(profiles[pageIndex]).append(" item=").append(count).append(" U+").append(row[3])
                        .append(" x=").append(geometry.getE()).append(" y=").append(geometry.getF())
                        .append(" advance=").append(geometry.getAdvanceX()).append(" match=").append(glyph).append('\n');
            }
            Set<String> actualFonts = new HashSet<String>();
            for (FontResource font : fonts) {
                if (!font.getPageUsage().contains(pageIndex + 1)) { continue; }
                match &= font.getEmbedding() == FontResource.Embedding.EMBEDDED && font.isSubset()
                        && font.getFontKind() == FontResource.FontKind.TYPE_0;
                actualFonts.add(font.getBaseFontName().get().getValue().replaceFirst("^[A-Z]{6}\\+", ""));
            }
            match &= count == page.getTextItems().size() && expectedText.toString().equals(page.getText())
                    && expectedFonts.equals(actualFonts);
            findings.append(profiles[pageIndex]).append(" semantic/geometry/explicit fonts: ").append(match)
                    .append(" fonts=").append(actualFonts).append('\n');
            passed &= match;
        }
        return new Observation(passed, findings.toString());
    }

    private static boolean near(double actual, double expected) { return Math.abs(actual - expected) <= TOLERANCE; }

    static final class Observation {
        final boolean passed;
        final String findings;
        Observation(boolean passed, String findings) { this.passed = passed; this.findings = findings; }
        EvidenceResult result() { return passed ? EvidenceResult.PASS : EvidenceResult.FAIL; }
    }

    private T28UnicodeSemanticAssertions() { }
}
