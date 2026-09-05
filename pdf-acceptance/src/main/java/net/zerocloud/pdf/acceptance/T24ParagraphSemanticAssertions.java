package net.zerocloud.pdf.acceptance;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.zerocloud.pdf.CharacterMapping;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ExtractionLimits;
import net.zerocloud.pdf.FontResource;
import net.zerocloud.pdf.ImageByteAccess;
import net.zerocloud.pdf.PageText;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.ResourceExtractionLimits;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.TextGeometry;
import net.zerocloud.pdf.TextItem;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.ExtractImagesAndResources;
import net.zerocloud.pdf.query.ExtractTextAndStructure;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageObjectReference;

/** Independent, hand-specified paragraph expectations checked through public queries. */
final class T24ParagraphSemanticAssertions {
    private T24ParagraphSemanticAssertions() { }

    static Observation inspect(WorkflowOutcome<Void> creation, Path artifact) {
        boolean receipt = creation.getPublicationReceipts().size() == 1
                && creation.getPublicationReceipts().get(0).getStatus() == PublicationStatus.COMMITTED
                && T24ParagraphEvidenceCommand.CAPABILITY.equals(creation.getCapabilityId());
        try {
            Observation observation = new DocumentWorkflow().execute(WorkflowRequest.open(artifact, SaveMode.REWRITE),
                    T24ParagraphSemanticAssertions::observe).getResult();
            return new Observation(receipt && observation.passed,
                    "Publication Receipt and capability: " + receipt + "\n" + observation.findings);
        } catch (DocumentFailure | RuntimeException failure) {
            return new Observation(false, "The artifact could not be checked through the public workflow.\n");
        }
    }

    private static Observation observe(DocumentSession session) throws DocumentFailure {
        List<PageText> pages = session.query(ExtractTextAndStructure.version1(ExtractionLimits.builder()
                .maximumPages(4).maximumPageTreeNodes(32).maximumContentStreams(128).maximumContentStreamDepth(8)
                .maximumDecodedBytes(1 << 20).maximumTextItems(64).maximumUnicodeCodePoints(64)
                .maximumToUnicodeMappings(64).maximumFontDataEntries(256).maximumMarkedContentSequences(4)
                .maximumMarkedContentDepth(4).maximumStructureElements(4).maximumStructureItems(4)
                .maximumStructureDepth(4).maximumRoleMappings(4).build())).getPages();
        boolean geometry = pages.size() == 2;
        StringBuilder findings = new StringBuilder("Observed page count: ").append(pages.size()).append('\n');
        int[] offsets = new int[2];
        if (geometry) {
            geometry = "AA AA B\u03a9 ".equals(pages.get(0).getText())
                    && "B\u03a9 B\u03a9".equals(pages.get(1).getText());
            for (PageText page : pages) {
                geometry &= near(page.getCropBoxLeft().doubleValue(), 0)
                        && near(page.getCropBoxBottom().doubleValue(), 0)
                        && near(page.getCropBoxRight().doubleValue(), 612)
                        && near(page.getCropBoxTop().doubleValue(), 792);
            }
            for (T24ParagraphExpectations.Run run : T24ParagraphExpectations.RUNS) {
                List<TextItem> items = pages.get(run.page - 1).getTextItems();
                double x = run.x;
                for (int index = 0; index < run.text.length(); index++) {
                    int offset = offsets[run.page - 1]++;
                    if (offset >= items.size()) { geometry = false; break; }
                    TextItem item = items.get(offset);
                    TextGeometry value = item.getGeometry();
                    char cp = run.text.charAt(index);
                    geometry &= String.valueOf(cp).equals(item.getTextContribution())
                            && item.getCharacterMapping().getConfidence() == CharacterMapping.Confidence.EXPLICIT
                            && near(value.getE().doubleValue(), x) && near(value.getF().doubleValue(), run.y)
                            && near(value.getAdvanceX().doubleValue(), T24ParagraphExpectations.advance(cp))
                            && near(value.getAdvanceY().doubleValue(), 0)
                            && near(value.getA().doubleValue(), 40) && near(value.getD().doubleValue(), 40)
                            && near(value.getB().doubleValue(), 0) && near(value.getC().doubleValue(), 0);
                    findings.append("Glyph page=").append(run.page).append(" item=").append(offset + 1)
                            .append(" Unicode=").append(Integer.toHexString(cp))
                            .append(" x=").append(value.getE()).append(" y=").append(value.getF())
                            .append(" advance=").append(value.getAdvanceX()).append('\n');
                    x += T24ParagraphExpectations.advance(cp);
                }
            }
            geometry &= offsets[0] == pages.get(0).getTextItems().size()
                    && offsets[1] == pages.get(1).getTextItems().size();
        }
        List<FontResource> fonts = session.query(ExtractImagesAndResources.version1(
                ResourceExtractionLimits.builder().maximumPages(4).maximumPageTreeNodes(32)
                        .maximumTraversedResourceValues(512).maximumResourceTraversalDepth(16)
                        .maximumDecodedPixels(0).maximumDecompressedBytes(1 << 20).maximumReturnedBytes(0).build(),
                ImageByteAccess.NONE)).getFonts();
        boolean embedded = fonts.size() == 2;
        for (FontResource font : fonts) {
            embedded &= font.getEmbedding() == FontResource.Embedding.EMBEDDED && font.isSubset()
                    && font.getFontKind() == FontResource.FontKind.TYPE_0;
        }
        List<double[]> graphics = graphicBoxes(session, 1);
        boolean graphic = graphics.size() == 1 && graphicBoxes(session, 2).isEmpty();
        if (graphic) {
            double[] box = graphics.get(0);
            graphic = near(box[0], 232) && near(box[1], 600) && near(box[2], 264) && near(box[3], 632);
            findings.append("Graphic page=1 bounds=").append(java.util.Arrays.toString(box)).append('\n');
        }
        findings.append("Text order, page geometry, scalar metrics and baselines: ").append(geometry).append('\n')
                .append("Two embedded subset fonts: ").append(embedded).append('\n')
                .append("Inline graphic geometry: ").append(graphic).append('\n');
        return new Observation(geometry && embedded && graphic, findings.toString());
    }

    /** Reads only the q/Q/cm/Do semantics used by this closed project-owned fixture. */
    private static List<double[]> graphicBoxes(DocumentSession session, int pageNumber) throws DocumentFailure {
        PdfDictionary page = (PdfDictionary) session.query(InspectObject.version1(
                session.query(PageObjectReference.version1(pageNumber)), PdfInspectionLimits.of(1024, 1 << 20)));
        PdfDictionary resources = (PdfDictionary) resolve(session, page.get(PdfName.of("Resources")));
        PdfValue rawXobjects = resources.get(PdfName.of("XObject"));
        List<double[]> result = new ArrayList<double[]>();
        if (rawXobjects == null) { return result; }
        PdfDictionary xobjects = (PdfDictionary) resolve(session, rawXobjects);
        String content = content(session, resolve(session, page.get(PdfName.of("Contents"))));
        Deque<double[]> stack = new ArrayDeque<double[]>();
        double[] matrix = {1, 0, 0, 1, 0, 0};
        List<String> operands = new ArrayList<String>();
        for (String token : content.trim().split("\\s+")) {
            if (token.startsWith("/") || token.matches("[-+]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)")) {
                operands.add(token); continue;
            }
            if (token.equals("q")) { stack.push(matrix.clone()); }
            else if (token.equals("Q")) { matrix = stack.pop(); }
            else if (token.equals("cm")) {
                double[] next = new double[6];
                for (int index = 0; index < 6; index++) { next[index] = Double.parseDouble(operands.get(index)); }
                matrix = multiply(matrix, next);
            } else if (token.equals("Do")) {
                PdfStream form = (PdfStream) resolve(session, xobjects.get(PdfName.of(operands.get(0).substring(1))));
                PdfDictionary dictionary = form.getDictionary();
                if (!PdfName.of("Form").equals(dictionary.get(PdfName.of("Subtype")))) {
                    throw new IllegalArgumentException("Unexpected T24 XObject");
                }
                PdfArray box = (PdfArray) resolve(session, dictionary.get(PdfName.of("BBox")));
                double[] transform = matrix;
                PdfValue rawMatrix = dictionary.get(PdfName.of("Matrix"));
                if (rawMatrix != null) {
                    PdfArray local = (PdfArray) resolve(session, rawMatrix);
                    double[] values = new double[6];
                    for (int index = 0; index < 6; index++) { values[index] = number(local, index); }
                    transform = multiply(matrix, values);
                }
                double[] bounds = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
                for (int corner = 0; corner < 4; corner++) {
                    double x = number(box, (corner & 1) == 0 ? 0 : 2);
                    double y = number(box, (corner & 2) == 0 ? 1 : 3);
                    double tx = transform[0] * x + transform[2] * y + transform[4];
                    double ty = transform[1] * x + transform[3] * y + transform[5];
                    bounds[0] = Math.min(bounds[0], tx); bounds[1] = Math.min(bounds[1], ty);
                    bounds[2] = Math.max(bounds[2], tx); bounds[3] = Math.max(bounds[3], ty);
                }
                result.add(bounds);
            }
            operands.clear();
        }
        return result;
    }
    private static double[] multiply(double[] a, double[] b) {
        return new double[] {a[0] * b[0] + a[2] * b[1], a[1] * b[0] + a[3] * b[1],
            a[0] * b[2] + a[2] * b[3], a[1] * b[2] + a[3] * b[3],
            a[0] * b[4] + a[2] * b[5] + a[4], a[1] * b[4] + a[3] * b[5] + a[5]};
    }
    private static double number(PdfArray array, int index) throws DocumentFailure {
        return ((PdfNumber) array.get(index)).decimalValue().doubleValue();
    }
    private static PdfValue resolve(DocumentSession session, PdfValue value) throws DocumentFailure {
        return value instanceof PdfIndirectReference ? session.query(InspectObject.version1(
                ((PdfIndirectReference) value).getReference(), PdfInspectionLimits.of(1024, 1 << 20))) : value;
    }
    private static String content(DocumentSession session, PdfValue value) throws DocumentFailure {
        if (value instanceof PdfStream) { return new String(((PdfStream) value).readBytes(), StandardCharsets.US_ASCII); }
        PdfArray array = (PdfArray) value;
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < array.size(); index++) {
            result.append(content(session, resolve(session, array.get(index)))).append('\n');
        }
        return result.toString();
    }
    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) <= T24ParagraphExpectations.TOLERANCE;
    }
    static final class Observation {
        final boolean passed;
        final String findings;
        Observation(boolean passed, String findings) { this.passed = passed; this.findings = findings; }
        EvidenceResult result() { return passed ? EvidenceResult.PASS : EvidenceResult.FAIL; }
    }
}
