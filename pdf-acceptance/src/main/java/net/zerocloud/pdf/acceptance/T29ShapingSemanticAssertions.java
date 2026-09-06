package net.zerocloud.pdf.acceptance;

import java.nio.file.Path;
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

/** Reopened public observations compared with the independently frozen HarfBuzz rows. */
final class T29ShapingSemanticAssertions {
    private static final double TOLERANCE = 0.0001;

    static Observation inspect(WorkflowOutcome<Void> creation, Path artifact, WorkflowExecutionProfile mode) throws Exception {
        boolean receipt = creation.getPublicationReceipts().size() == 1
                && creation.getPublicationReceipts().get(0).getStatus() == PublicationStatus.COMMITTED
                && creation.getExecutionProfile() == mode
                && "composition.layout.paragraph-areas".equals(creation.getCapabilityId());
        List<String[]> rows = T29ShapingReference.rows();
        try {
            Observation value = new DocumentWorkflow().execute(WorkflowRequest.builder()
                    .source("primary", DocumentSource.path(artifact)).primarySource("primary")
                    .executionProfile(mode).saveMode(SaveMode.REWRITE).build(), session -> observe(session, rows)).getResult();
            return new Observation(receipt && value.passed, "Publication Receipt and mode: " + receipt + "\n" + value.findings);
        } catch (DocumentFailure failure) {
            return new Observation(false, "Public reopened observation failed: " + failure.getCode() + " / " + failure.getMessage() + "\n");
        } catch (RuntimeException failure) {
            return new Observation(false, "Public reopened observation failed: " + failure.getClass().getSimpleName() + "\n");
        }
    }

    private static Observation observe(DocumentSession session, List<String[]> rows) throws DocumentFailure {
        List<PageText> pages = session.query(ExtractTextAndStructure.version1(ExtractionLimits.builder()
                .maximumPages(8).maximumPageTreeNodes(32).maximumContentStreams(4096).maximumContentStreamDepth(8)
                .maximumDecodedBytes(4 << 20).maximumTextItems(2048).maximumUnicodeCodePoints(4096)
                .maximumToUnicodeMappings(2048).maximumFontDataEntries(32768).maximumMarkedContentSequences(512)
                .maximumMarkedContentDepth(8).maximumStructureElements(8).maximumStructureItems(8)
                .maximumStructureDepth(8).maximumRoleMappings(8).build())).getPages();
        List<FontResource> fonts = session.query(ExtractImagesAndResources.version1(ResourceExtractionLimits.builder()
                .maximumPages(8).maximumPageTreeNodes(32).maximumTraversedResourceValues(16384).maximumResourceTraversalDepth(16)
                .maximumDecodedPixels(0).maximumDecompressedBytes(4 << 20).maximumReturnedBytes(0).build(), ImageByteAccess.NONE)).getFonts();
        StringBuilder findings = new StringBuilder("Page count: ").append(pages.size()).append('\n');
        if (pages.size() != 8) { return new Observation(false, findings.toString()); }
        boolean passed = true;
        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            PageText page = pages.get(pageIndex);
            boolean match = near(page.getCropBoxLeft().doubleValue(), 0) && near(page.getCropBoxBottom().doubleValue(), 0)
                    && near(page.getCropBoxRight().doubleValue(), 240) && near(page.getCropBoxTop().doubleValue(), 192);
            Set<String> expectedFonts = new HashSet<String>();
            int count = 0;
            String previousRun = "";
            StringBuilder logical = new StringBuilder();
            for (String[] row : rows) {
                if (Integer.parseInt(row[1]) != pageIndex + 1) { continue; }
                String run = row[3] + ":" + row[4];
                String input = T29ShapingReference.unhex(row[6]);
                if (!run.equals(previousRun)) { logical.append(input); previousRun = run; }
                String cluster = input.substring(Integer.parseInt(row[10]) - Integer.parseInt(row[5]),
                        Integer.parseInt(row[11]) - Integer.parseInt(row[5]));
                expectedFonts.add(row[7].replace(".ttf", ""));
                if (count >= page.getTextItems().size()) { match = false; count++; continue; }
                TextItem item = page.getTextItems().get(count++);
                TextGeometry geometry = item.getGeometry();
                boolean glyph = item.getCharacterMapping().getConfidence() == CharacterMapping.Confidence.EXPLICIT
                        && item.getUnicode().isPresent() && cluster.equals(item.getUnicode().get())
                        && item.getCharacterMapping().getSourceCode().length == 2
                        && near(geometry.getE().doubleValue(), Double.parseDouble(row[16]))
                        && near(geometry.getF().doubleValue(), Double.parseDouble(row[17]))
                        && near(geometry.getAdvanceX().doubleValue(), Integer.parseInt(row[12]) * 0.012)
                        && near(geometry.getAdvanceY().doubleValue(), 0) && near(geometry.getA().doubleValue(), 12)
                        && near(geometry.getD().doubleValue(), 12) && near(geometry.getB().doubleValue(), 0)
                        && near(geometry.getC().doubleValue(), 0);
                match &= glyph;
                findings.append(row[0]).append(" page=").append(row[1]).append(" line=").append(row[3])
                        .append(" item=").append(count).append(" cluster=[").append(row[10]).append(',').append(row[11])
                        .append(") x=").append(geometry.getE()).append(" y=").append(geometry.getF())
                        .append(" advance=").append(geometry.getAdvanceX()).append(" match=").append(glyph).append('\n');
            }
            Set<String> actualFonts = new HashSet<String>();
            for (FontResource font : fonts) {
                if (!font.getPageUsage().contains(pageIndex + 1)) { continue; }
                match &= font.getEmbedding() == FontResource.Embedding.EMBEDDED && font.isSubset()
                        && font.getFontKind() == FontResource.FontKind.TYPE_0 && font.getBaseFontName().isPresent();
                if (font.getBaseFontName().isPresent()) {
                    actualFonts.add(font.getBaseFontName().get().getValue().replaceFirst("^[A-Z]{6}\\+", ""));
                }
            }
            match &= count == page.getTextItems().size() && logical.toString().equals(page.getText()) && expectedFonts.equals(actualFonts);
            findings.append("Page ").append(pageIndex + 1).append(" logical text, geometry and explicit embedded fonts: ")
                    .append(match).append(" fonts=").append(actualFonts).append('\n');
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

    private T29ShapingSemanticAssertions() { }
}
