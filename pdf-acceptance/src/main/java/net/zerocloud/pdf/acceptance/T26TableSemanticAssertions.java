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

/** Independent, hand-specified table expectations checked through public queries. */
final class T26TableSemanticAssertions {
    private T26TableSemanticAssertions() { }

    static Observation inspect(WorkflowOutcome<Void> creation, Path artifact) {
        boolean receipt = creation.getPublicationReceipts().size() == 1
                && creation.getPublicationReceipts().get(0).getStatus() == PublicationStatus.COMMITTED
                && T26TableEvidenceCommand.CAPABILITY.equals(creation.getCapabilityId());
        try {
            Observation observation = new DocumentWorkflow().execute(WorkflowRequest.open(artifact, SaveMode.REWRITE),
                    T26TableSemanticAssertions::observe).getResult();
            return new Observation(receipt && observation.passed,
                    "Publication Receipt and capability: " + receipt + "\n" + observation.findings);
        } catch (DocumentFailure | RuntimeException failure) {
            return new Observation(false, "The artifact could not be checked through the public workflow.\n");
        }
    }

    private static Observation observe(DocumentSession session) throws DocumentFailure {
        List<PageText> pages = session.query(ExtractTextAndStructure.version1(ExtractionLimits.builder()
                .maximumPages(4).maximumPageTreeNodes(32).maximumContentStreams(256).maximumContentStreamDepth(8)
                .maximumDecodedBytes(1 << 20).maximumTextItems(64).maximumUnicodeCodePoints(64)
                .maximumToUnicodeMappings(64).maximumFontDataEntries(256).maximumMarkedContentSequences(4)
                .maximumMarkedContentDepth(4).maximumStructureElements(4).maximumStructureItems(4)
                .maximumStructureDepth(4).maximumRoleMappings(4).build())).getPages();
        boolean geometry = pages.size() == 3;
        StringBuilder findings = new StringBuilder("Observed page count: ").append(pages.size()).append('\n');
        int[] offsets = new int[3];
        if (geometry) {
            geometry = "AB\u03a9".equals(pages.get(0).getText()) && "AABBBB".equals(pages.get(1).getText())
                    && "ABBB\u03a9".equals(pages.get(2).getText());
            for (PageText page : pages) {
                geometry &= near(page.getCropBoxLeft().doubleValue(),0) && near(page.getCropBoxBottom().doubleValue(),0)
                        && near(page.getCropBoxRight().doubleValue(),612) && near(page.getCropBoxTop().doubleValue(),792);
            }
            for (T26TableExpectations.Run run : T26TableExpectations.RUNS) {
                List<TextItem> items = pages.get(run.page-1).getTextItems();
                double x = run.x;
                for (int i = 0; i < run.text.length(); i++) {
                    int offset = offsets[run.page-1]++;
                    if (offset >= items.size()) { geometry = false; break; }
                    TextItem item = items.get(offset);
                    TextGeometry value = item.getGeometry();
                    char cp = run.text.charAt(i);
                    geometry &= String.valueOf(cp).equals(item.getTextContribution())
                            && item.getCharacterMapping().getConfidence() == CharacterMapping.Confidence.EXPLICIT
                            && near(value.getE().doubleValue(),x) && near(value.getF().doubleValue(),run.y)
                            && near(value.getAdvanceX().doubleValue(),T26TableExpectations.advance(cp))
                            && near(value.getAdvanceY().doubleValue(),0)
                            && near(value.getA().doubleValue(),10) && near(value.getD().doubleValue(),10)
                            && near(value.getB().doubleValue(),0) && near(value.getC().doubleValue(),0);
                    findings.append("Glyph page=").append(run.page).append(" item=").append(offset+1)
                            .append(" Unicode=").append(Integer.toHexString(cp)).append(" x=").append(value.getE())
                            .append(" y=").append(value.getF()).append(" advance=").append(value.getAdvanceX()).append('\n');
                    x += T26TableExpectations.advance(cp);
                }
            }
            for (int p = 0; p < 3; p++) { geometry &= offsets[p] == pages.get(p).getTextItems().size(); }
        }
        List<FontResource> fonts = session.query(ExtractImagesAndResources.version1(
                ResourceExtractionLimits.builder().maximumPages(4).maximumPageTreeNodes(32)
                        .maximumTraversedResourceValues(1024).maximumResourceTraversalDepth(16)
                        .maximumDecodedPixels(0).maximumDecompressedBytes(1 << 20).maximumReturnedBytes(0).build(),
                ImageByteAccess.NONE)).getFonts();
        boolean embedded = fonts.size() == 2;
        for (FontResource font : fonts) {
            embedded &= font.getEmbedding() == FontResource.Embedding.EMBEDDED && font.isSubset()
                    && font.getFontKind() == FontResource.FontKind.TYPE_0;
        }
        boolean borders = pages.size() == 3;
        if (borders) {
            for (int page = 1; page <= 3; page++) {
                List<double[]> actual = borderBoxes(session,page);
                List<double[]> expected = new ArrayList<double[]>();
                for (double[] cell : T26TableExpectations.CELLS) {
                    if (cell[0] == page) {
                        for (double[] box : T26TableExpectations.borders(cell)) { expected.add(box); }
                    }
                }
                borders &= actual.size() == expected.size();
                for (int i = 0; i < Math.min(actual.size(),expected.size()); i++) {
                    for (int n = 0; n < 4; n++) { borders &= near(actual.get(i)[n],expected.get(i)[n]); }
                    findings.append("Black border page=").append(page).append(" box=")
                            .append(java.util.Arrays.toString(actual.get(i))).append('\n');
                }
            }
        }
        findings.append("Complete scalar reading order, cell padding and text geometry: ").append(geometry).append('\n')
                .append("Two embedded subset fonts: ").append(embedded).append('\n')
                .append("Complete cell/span border geometry with black fill: ").append(borders).append('\n');
        return new Observation(geometry && embedded && borders,findings.toString());
    }

    /** Interprets only the closed fixture's filled rectangular paths, graphics state and text wrappers. */
    private static List<double[]> borderBoxes(DocumentSession session,int pageNumber) throws DocumentFailure {
        PdfDictionary page = (PdfDictionary) session.query(InspectObject.version1(
                session.query(PageObjectReference.version1(pageNumber)),PdfInspectionLimits.of(1024,1 << 20)));
        String stream = content(session,resolve(session,page.get(PdfName.of("Contents"))));
        List<double[]> boxes = new ArrayList<double[]>();
        List<double[]> vertices = new ArrayList<double[]>();
        List<String> operands = new ArrayList<String>();
        Deque<State> stack = new ArrayDeque<State>();
        State state = new State(new double[] {1,0,0,1,0,0},true);
        boolean closed = false;
        for (String token : stream.trim().split("\\s+")) {
            if (token.startsWith("/") || token.matches("[-+]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)")) {
                operands.add(token); continue;
            }
            if (token.equals("q")) { stack.push(state); }
            else if (token.equals("Q")) { state = stack.pop(); }
            else if (token.equals("cm")) {
                double[] local = new double[6];
                for (int i = 0; i < 6; i++) { local[i] = Double.parseDouble(operands.get(i)); }
                state = new State(multiply(state.matrix,local),state.black);
            } else if (token.equals("g") || token.equals("rg") || token.equals("k")) {
                boolean black = token.equals("k") ? operands.size() == 4 && near(Double.parseDouble(operands.get(3)),1) : true;
                for (int i = 0; i < (token.equals("g") ? 1 : 3); i++) { black &= near(Double.parseDouble(operands.get(i)),0); }
                state = new State(state.matrix,black);
            } else if (token.equals("m") || token.equals("l")) {
                if (token.equals("m")) { vertices.clear(); closed = false; }
                double x = Double.parseDouble(operands.get(0)), y = Double.parseDouble(operands.get(1));
                double[] m = state.matrix;
                vertices.add(new double[] {m[0]*x+m[2]*y+m[4],m[1]*x+m[3]*y+m[5]});
            } else if (token.equals("h")) { closed = true; }
            else if (token.equals("f") || token.equals("f*")) {
                if (!closed || vertices.size() != 4 || !state.black) { throw new IllegalArgumentException("Invalid table border path"); }
                double[] a=vertices.get(0), b=vertices.get(1), c=vertices.get(2), d=vertices.get(3);
                if (!near(a[1],b[1]) || !near(b[0],c[0]) || !near(c[1],d[1]) || !near(d[0],a[0])
                        || c[0] <= a[0] || c[1] <= a[1]) { throw new IllegalArgumentException("Nonrectangular table border"); }
                boxes.add(new double[] {a[0],a[1],c[0],c[1]}); vertices.clear(); closed = false;
            } else if (token.equals("S") || token.equals("s") || token.equals("B") || token.equals("Do")
                    || token.equals("re") || token.equals("W") || token.equals("W*")) {
                throw new IllegalArgumentException("Unexpected table fixture painting");
            }
            operands.clear();
        }
        if (!stack.isEmpty() || !vertices.isEmpty()) { throw new IllegalArgumentException("Incomplete table graphics state"); }
        return boxes;
    }
    private static final class State {
        final double[] matrix;
        final boolean black;
        State(double[] matrix,boolean black) { this.matrix=matrix;this.black=black; }
    }
    private static double[] multiply(double[] a, double[] b) {
        return new double[] {a[0] * b[0] + a[2] * b[1], a[1] * b[0] + a[3] * b[1],
            a[0] * b[2] + a[2] * b[3], a[1] * b[2] + a[3] * b[3],
            a[0] * b[4] + a[2] * b[5] + a[4], a[1] * b[4] + a[3] * b[5] + a[5]};
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
        return Math.abs(actual - expected) <= T26TableExpectations.TOLERANCE;
    }
    static final class Observation {
        final boolean passed;
        final String findings;
        Observation(boolean passed, String findings) { this.passed = passed; this.findings = findings; }
        EvidenceResult result() { return passed ? EvidenceResult.PASS : EvidenceResult.FAIL; }
    }
}
