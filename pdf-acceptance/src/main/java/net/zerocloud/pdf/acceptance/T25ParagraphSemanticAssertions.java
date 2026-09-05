package net.zerocloud.pdf.acceptance;

import java.nio.file.Path;
import java.util.List;
import net.zerocloud.pdf.CharacterMapping;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentSession;
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
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.ExtractImagesAndResources;
import net.zerocloud.pdf.query.ExtractTextAndStructure;

/** Reopened semantic and structural observations compared only with the independent numeric oracle. */
final class T25ParagraphSemanticAssertions {
    private T25ParagraphSemanticAssertions() { }
    static Observation inspect(T25ParagraphExpectations.Profile profile, WorkflowOutcome<Void> creation, Path pdf) {
        boolean receipt = creation.getPublicationReceipts().size() == 1
                && creation.getPublicationReceipts().get(0).getStatus() == PublicationStatus.COMMITTED
                && T25ParagraphEvidenceCommand.CAPABILITY.equals(creation.getCapabilityId());
        try {
            Observation observed = new DocumentWorkflow().execute(WorkflowRequest.open(pdf, SaveMode.REWRITE),
                    session -> observe(profile, session)).getResult();
            return new Observation(receipt && observed.passed,
                    "Publication Receipt and capability: " + receipt + "\n" + observed.findings);
        } catch (DocumentFailure | RuntimeException failure) {
            return new Observation(false, "The artifact could not be inspected through the public workflow.\n");
        }
    }

    private static Observation observe(T25ParagraphExpectations.Profile profile, DocumentSession session) throws DocumentFailure {
        List<PageText> pages = session.query(ExtractTextAndStructure.version1(ExtractionLimits.builder()
                .maximumPages(4).maximumPageTreeNodes(32).maximumContentStreams(256).maximumContentStreamDepth(8)
                .maximumDecodedBytes(1 << 20).maximumTextItems(128).maximumUnicodeCodePoints(128)
                .maximumToUnicodeMappings(64).maximumFontDataEntries(512).maximumMarkedContentSequences(4)
                .maximumMarkedContentDepth(4).maximumStructureElements(4).maximumStructureItems(4)
                .maximumStructureDepth(4).maximumRoleMappings(4).build())).getPages();
        boolean geometry = pages.size() == 2;
        boolean fallback = false;
        StringBuilder findings = new StringBuilder("Observed page count: ").append(pages.size()).append('\n');
        int[] offsets = new int[2];
        StringBuilder[] expectedText = {new StringBuilder(), new StringBuilder()};
        for (T25ParagraphExpectations.Run run : profile.runs) {
            expectedText[run.page - 1].append(run.text);
            fallback |= run.text.contains("\u03a9");
        }
        if (geometry) {
            for (int index = 0; index < 2; index++) {
                PageText page = pages.get(index);
                geometry &= expectedText[index].toString().equals(page.getText())
                        && near(page.getCropBoxLeft().doubleValue(), 0) && near(page.getCropBoxBottom().doubleValue(), 0)
                        && near(page.getCropBoxRight().doubleValue(), 612) && near(page.getCropBoxTop().doubleValue(), 792);
                findings.append("Page ").append(index + 1).append(" text:");
                if (!page.getText().isEmpty()) { findings.append(' ').append(page.getText()); }
                findings.append('\n');
            }
            for (T25ParagraphExpectations.Run run : profile.runs) {
                double x = run.x;
                List<TextItem> items = pages.get(run.page - 1).getTextItems();
                for (int index = 0; index < run.text.length(); index++) {
                    int offset = offsets[run.page - 1]++;
                    if (offset >= items.size()) { geometry = false; break; }
                    TextItem item = items.get(offset);
                    TextGeometry value = item.getGeometry();
                    char cp = run.text.charAt(index);
                    double advance = T25ParagraphExpectations.advance(cp);
                    geometry &= String.valueOf(cp).equals(item.getTextContribution())
                            && item.getCharacterMapping().getConfidence() == CharacterMapping.Confidence.EXPLICIT
                            && near(value.getE().doubleValue(), x) && near(value.getF().doubleValue(), run.y)
                            && near(value.getAdvanceX().doubleValue(), advance) && near(value.getAdvanceY().doubleValue(), 0)
                            && near(value.getA().doubleValue(), 40) && near(value.getD().doubleValue(), 40)
                            && near(value.getB().doubleValue(), 0) && near(value.getC().doubleValue(), 0);
                    findings.append("Glyph page=").append(run.page).append(" item=").append(offset + 1)
                            .append(" Unicode=").append(Integer.toHexString(cp)).append(" x=").append(value.getE())
                            .append(" y=").append(value.getF()).append(" advance=").append(value.getAdvanceX()).append('\n');
                    x += advance;
                }
                findings.append("Expected run advance box: [").append(run.x).append(',').append(run.y)
                        .append(',').append(x).append(',').append(run.y + (run.text.contains("\u03a9") ? 28.8 : 28))
                        .append("]\n");
            }
            for (int index = 0; index < 2; index++) { geometry &= offsets[index] == pages.get(index).getTextItems().size(); }
        }
        List<FontResource> fonts = session.query(ExtractImagesAndResources.version1(ResourceExtractionLimits.builder()
                .maximumPages(4).maximumPageTreeNodes(32).maximumTraversedResourceValues(512).maximumResourceTraversalDepth(16)
                .maximumDecodedPixels(0).maximumDecompressedBytes(1 << 20).maximumReturnedBytes(0).build(), ImageByteAccess.NONE)).getFonts();
        boolean embedded = fonts.size() == (fallback ? 2 : 1);
        for (FontResource font : fonts) {
            embedded &= font.getEmbedding() == FontResource.Embedding.EMBEDDED && font.isSubset()
                    && font.getFontKind() == FontResource.FontKind.TYPE_0;
        }
        findings.append("All page boxes, text order, breakpoints, matrices, advances and baselines: ").append(geometry)
                .append("\nExpected embedded subset count and structure: ").append(embedded).append('\n');
        return new Observation(geometry && embedded, findings.toString());
    }
    private static boolean near(double actual, double expected) { return Math.abs(actual - expected) <= T25ParagraphExpectations.TOLERANCE; }
    static final class Observation {
        final boolean passed;
        final String findings;
        Observation(boolean passed, String findings) { this.passed = passed; this.findings = findings; }
        EvidenceResult result() { return passed ? EvidenceResult.PASS : EvidenceResult.FAIL; }
    }
}
