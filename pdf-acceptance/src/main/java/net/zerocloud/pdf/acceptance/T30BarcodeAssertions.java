package net.zerocloud.pdf.acceptance;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.common.BitArray;
import com.google.zxing.oned.Code128Reader;
import com.google.zxing.oned.Code39Reader;
import com.google.zxing.oned.CodaBarReader;
import com.google.zxing.oned.EAN13Reader;
import com.google.zxing.oned.EAN8Reader;
import com.google.zxing.oned.UPCAReader;
import com.google.zxing.oned.UPCEReader;
import com.google.zxing.oned.OneDReader;
import com.google.zxing.oned.ITFReader;
import net.zerocloud.pdf.composition.Barcode1D;
import net.zerocloud.pdf.composition.BarcodeText;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ExtractionLimits;
import net.zerocloud.pdf.TextStructureExtraction;
import net.zerocloud.pdf.TextItem;
import net.zerocloud.pdf.TextGeometry;
import net.zerocloud.pdf.TextRenderingMode;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.PageObjectReference;
import net.zerocloud.pdf.query.ExtractTextAndStructure;

/** Reopened PDF observations; independent ZXing writers and readers own the symbol oracle. */
final class T30BarcodeAssertions {
    private T30BarcodeAssertions() { }

    static Observation inspect(Path pdf) {
        try {
            return new DocumentWorkflow().execute(WorkflowRequest.open(pdf, SaveMode.REWRITE), session -> {
                try {
                    List<T30BarcodeProfile.Fixture> fixtures = T30BarcodeProfile.fixtures();
                    require(session.query(PageCount.INSTANCE) == fixtures.size(), "Unexpected barcode page count");
                    TextStructureExtraction text = session.query(ExtractTextAndStructure.version1(textLimits()));
                    StringBuilder findings = new StringBuilder();
                    int page = 0, labels = 0;
                    for (T30BarcodeProfile.Fixture fixture : fixtures) {
                        List<double[]> world = rectangles(content(session, ++page));
                        List<double[]> actual = local(world, fixture.placement);
                        List<double[]> expected = T30BarcodeOracle.bars(fixture);
                        require(actual.size() == expected.size(), fixture.id + " bar count mismatch");
                        for (int bar = 0; bar < actual.size(); bar++) {
                            for (int component = 0; component < 4; component++) {
                                near(expected.get(bar)[component], actual.get(bar)[component], fixture.id + " bar " + bar + " geometry " + component);
                            }
                        }
                        List<TextItem> items = text.getPages().get(page - 1).getTextItems();
                        label(fixture, items, T30BarcodeOracle.width(fixture));
                        observedGeometry(findings, page, fixture.id, world, items);
                        if (!fixture.caption.isEmpty()) { labels++; }
                        if (T30BarcodeOracle.postal(fixture)) {
                            Barcode1D barcode = fixture.barcode;
                            String decoded = T30PostalDecoder.decode(actual, barcode.getMode() == Barcode1D.Mode.PLANET,
                                    barcode.getQuietZone(), barcode.getPostalPitch(), barcode.getModuleWidth(), barcode.getBarHeight(), barcode.getShortBarHeight());
                            require(decoded.equals(fixture.payload), fixture.id + " postal payload mismatch");
                            findings.append(fixture.id).append(": payload=").append(decoded).append("; check=")
                                    .append(decoded.charAt(decoded.length() - 1)).append("; exact independent heights, pitch and placement=pass; label=")
                                    .append(printable(fixture.caption)).append("; label metrics=pass\n");
                            continue;
                        }
                        double module = fixture.barcode.getModuleWidth();
                        BitArray row = new BitArray((int) Math.round(T30BarcodeOracle.width(fixture) / module * 4));
                        for (double[] value : actual) {
                            int start = (int) Math.round(value[0] / module * 4);
                            int end = (int) Math.round((value[0] + value[2]) / module * 4);
                            for (int pixel = start; pixel < end; pixel++) { row.set(pixel); }
                        }
                        findings.append(fixture.id).append(": ").append(decode(fixture, row))
                                .append("; exact independent modules and placement=pass; label=").append(printable(fixture.caption))
                                .append("; label metrics=pass\n");
                    }
                    return new Observation(true, page, labels, findings.toString());
                } catch (Exception failure) {
                    return new Observation(false, 0, "FAIL: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
                }
            }).getResult();
        } catch (DocumentFailure failure) { return new Observation(false, 0, "PDF observation failed: " + failure.getCode()); }
    }

    static String decode(T30BarcodeProfile.Fixture fixture, BitArray row) throws Exception {
        double quiet = fixture.barcode.getQuietZone();
        double module = fixture.barcode.getModuleWidth();
        Result decoded;
        String originalDecoderPayload = null;
        String check;
        if (fixture.words.length != 0) {
            decoded = new Code128Reader().decodeRow(0, row, fixture.barcode.getMode() == Barcode1D.Mode.GS1_128
                    ? Collections.singletonMap(DecodeHintType.ASSUME_GS1, Boolean.TRUE) : null);
            require(decoded.getText().equals(fixture.payload), fixture.id + " decoded payload mismatch");
            byte[] words = decoded.getRawBytes();
            require(words.length == fixture.words.length + 2, fixture.id + " raw word count mismatch");
            for (int index = 0; index < fixture.words.length; index++) {
                require((words[index] & 255) == fixture.words[index], fixture.id + " raw word mismatch at " + index);
            }
            require((words[words.length - 2] & 255) == fixture.check, fixture.id + " checksum mismatch");
            check = Integer.toString(fixture.check);
        } else if (fixture.barcode.getMode() == Barcode1D.Mode.SUPPLEMENT2 || fixture.barcode.getMode() == Barcode1D.Mode.SUPPLEMENT5) {
            decoded = new Result(T30SupplementDecoder.decode(row, 4, fixture.payload.length()), null, null, BarcodeFormat.UPC_EAN_EXTENSION);
            require(decoded.getText().equals(fixture.payload), fixture.id + " supplement payload mismatch");
            check = "parity-verified";
        } else if (fixture.barcode.getMode() == Barcode1D.Mode.MSI) {
            decoded = null;
            originalDecoderPayload = T30MsiDecoder.decode(row, 4, fixture.barcode.isGenerateChecksum());
            require(originalDecoderPayload.equals(fixture.payload), fixture.id + " MSI payload mismatch");
            check = fixture.barcode.isGenerateChecksum() ? fixture.encoded.substring(fixture.encoded.length() - 1) : "none";
        } else if (fixture.barcode.getMode() == Barcode1D.Mode.INTERLEAVED_2_OF_5) {
            decoded = new ITFReader().decodeRow(0, row, Collections.singletonMap(DecodeHintType.ALLOWED_LENGTHS, new int[] {2, 6, 10}));
            require(decoded.getText().equals(fixture.payload), fixture.id + " ITF payload mismatch");
            check = "none";
            if (fixture.barcode.isGenerateChecksum()) {
                int sum = 0;
                for (int index = decoded.getText().length() - 1, place = 0; index >= 0; index--, place++) {
                    sum += (decoded.getText().charAt(index) - '0') * (place % 2 == 0 ? 1 : 3);
                }
                require(sum % 10 == 0, fixture.id + " ITF check mismatch");
                check = fixture.encoded.substring(fixture.encoded.length() - 1);
            }
        } else if (fixture.barcode.getMode() == Barcode1D.Mode.CODABAR) {
            decoded = new CodaBarReader().decodeRow(0, row, Collections.singletonMap(DecodeHintType.RETURN_CODABAR_START_END, Boolean.TRUE));
            require(decoded.getText().equals(fixture.payload), fixture.id + " Codabar payload mismatch");
            check = "none";
            if (fixture.barcode.isGenerateChecksum()) {
                int total = 0;
                String alphabet = "0123456789-$:/.+ABCD";
                for (int index = 0; index < decoded.getText().length(); index++) { total += alphabet.indexOf(decoded.getText().charAt(index)); }
                require(total % 16 == 0, fixture.id + " Codabar check mismatch");
                check = fixture.encoded.substring(fixture.encoded.length() - 2, fixture.encoded.length() - 1);
            }
        } else if (fixture.barcode.getMode() == Barcode1D.Mode.EAN13 || fixture.barcode.getMode() == Barcode1D.Mode.EAN8
                || fixture.barcode.getMode() == Barcode1D.Mode.UPCA || fixture.barcode.getMode() == Barcode1D.Mode.UPCE) {
            OneDReader reader = fixture.barcode.getMode() == Barcode1D.Mode.EAN13 ? new EAN13Reader()
                    : fixture.barcode.getMode() == Barcode1D.Mode.EAN8 ? new EAN8Reader()
                    : fixture.barcode.getMode() == Barcode1D.Mode.UPCA ? new UPCAReader() : new UPCEReader();
            decoded = reader.decodeRow(0, row, null);
            require(decoded.getText().equals(fixture.payload), fixture.id + " retail payload mismatch");
            if (!fixture.expansion.isEmpty()) {
                require(UPCEReader.convertUPCEtoUPCA(decoded.getText()).equals(fixture.expansion), fixture.id + " UPC-A expansion mismatch");
            }
            if (!fixture.barcode.getSupplement().isEmpty()) {
                String supplement = fixture.barcode.getSupplement();
                require(decoded.getResultMetadata() != null && supplement.equals(
                        decoded.getResultMetadata().get(ResultMetadataType.UPC_EAN_EXTENSION)), fixture.id + " ZXing add-on mismatch");
                int start = (int) ((quiet / module + T30BarcodeOracle.mainPattern(fixture).length + 9) * 4);
                BitArray extension = new BitArray(row.getSize() - start);
                for (int bit = start; bit < row.getSize(); bit++) { if (row.get(bit)) { extension.set(bit - start); } }
                require(T30SupplementDecoder.decode(extension, 4, supplement.length()).equals(supplement), fixture.id + " independent add-on mismatch");
            }
            check = fixture.encoded.substring(fixture.encoded.length() - 1);
        } else {
            decoded = new Code39Reader(fixture.barcode.isGenerateChecksum(), fixture.barcode.getMode() == Barcode1D.Mode.CODE39_EXTENDED)
                    .decodeRow(0, row, null);
            require(decoded.getText().equals(fixture.payload), fixture.id + " Full ASCII or plain payload mismatch");
            require(new Code39Reader().decodeRow(0, row, null).getText().equals(fixture.encoded), fixture.id + " raw Code39 symbols mismatch");
            check = fixture.barcode.isGenerateChecksum() ? fixture.encoded.substring(fixture.encoded.length() - 1) : "none";
        }
        return "payload=" + printable(originalDecoderPayload == null ? decoded.getText() : originalDecoderPayload)
                + "; check=" + check + "; codewords=" + java.util.Arrays.toString(fixture.words)
                + "; supplement=" + fixture.barcode.getSupplement();
    }

    private static void label(T30BarcodeProfile.Fixture fixture, List<TextItem> items, double symbolWidth) {
        require(items.size() == fixture.caption.length(), fixture.id + " label scalar count mismatch");
        if (fixture.caption.isEmpty()) { return; }
        BarcodeText style = fixture.barcode.getHumanReadable().get();
        double size = style.getFontSize();
        double width = 0;
        for (int index = 0; index < fixture.caption.length(); index++) { width += T30FontMetrics.NOTO.width(fixture.caption.charAt(index), size); }
        double x = style.getAlignment() == BarcodeText.Alignment.LEFT ? 0
                : style.getAlignment() == BarcodeText.Alignment.RIGHT ? symbolWidth - width : (symbolWidth - width) / 2;
        double y = style.getPosition() == BarcodeText.Position.BELOW
                ? -fixture.barcode.getGuardExtension() - style.getGap() - T30FontMetrics.NOTO.ascent(size)
                : fixture.barcode.getBarHeight() + style.getGap() + T30FontMetrics.NOTO.descent(size);
        AffineTransform placement = transform(fixture.placement);
        for (int index = 0; index < items.size(); index++) {
            TextItem item = items.get(index);
            require(item.getTextContribution().equals(fixture.caption.substring(index, index + 1)), fixture.id + " label character mismatch");
            require(item.getRenderingMode() == TextRenderingMode.FILL, fixture.id + " label rendering mode mismatch");
            near(size * fixture.placement.getA(), item.getGeometry().getA().doubleValue(), fixture.id + " label matrix a");
            near(size * fixture.placement.getB(), item.getGeometry().getB().doubleValue(), fixture.id + " label matrix b");
            near(size * fixture.placement.getC(), item.getGeometry().getC().doubleValue(), fixture.id + " label matrix c");
            near(size * fixture.placement.getD(), item.getGeometry().getD().doubleValue(), fixture.id + " label matrix d");
            Point2D point = placement.transform(new Point2D.Double(x, y), null);
            near(point.getX(), item.getGeometry().getE().doubleValue(), fixture.id + " label x");
            near(point.getY(), item.getGeometry().getF().doubleValue(), fixture.id + " label baseline");
            double advance = T30FontMetrics.NOTO.width(fixture.caption.charAt(index), size);
            Point2D delta = placement.deltaTransform(new Point2D.Double(advance, 0), null);
            near(delta.getX(), item.getGeometry().getAdvanceX().doubleValue(), fixture.id + " label advance");
            near(delta.getY(), item.getGeometry().getAdvanceY().doubleValue(), fixture.id + " label y advance");
            x += advance;
        }
    }

    private static void observedGeometry(StringBuilder findings, int page, String id, List<double[]> bars, List<TextItem> items) {
        findings.append("\nPage ").append(page).append(" ").append(id).append("\nObserved PDF rectangles [x,y,width,height]:\n");
        for (double[] bar : bars) { findings.append(java.util.Arrays.toString(bar)).append('\n'); }
        findings.append("Observed labels: character, matrix [a,b,c,d,e,f], advance [x,y], rendering mode:\n");
        for (TextItem item : items) {
            TextGeometry geometry = item.getGeometry();
            findings.append(printable(item.getTextContribution())).append(' ')
                    .append(java.util.Arrays.toString(new double[] {geometry.getA().doubleValue(), geometry.getB().doubleValue(),
                        geometry.getC().doubleValue(), geometry.getD().doubleValue(), geometry.getE().doubleValue(), geometry.getF().doubleValue()}))
                    .append(' ').append(java.util.Arrays.toString(new double[] {geometry.getAdvanceX().doubleValue(), geometry.getAdvanceY().doubleValue()}))
                    .append(' ').append(item.getRenderingMode()).append('\n');
        }
    }

    static AffineTransform transform(CanvasMatrix matrix) {
        return new AffineTransform(matrix.getA(), matrix.getB(), matrix.getC(), matrix.getD(), matrix.getE(), matrix.getF());
    }

    /** Declared acceptance placements preserve axis alignment (including quarter turns). */
    private static List<double[]> local(List<double[]> actual, CanvasMatrix placement) throws java.awt.geom.NoninvertibleTransformException {
        AffineTransform inverse = transform(placement).createInverse();
        List<double[]> result = new ArrayList<double[]>();
        for (double[] rectangle : actual) {
            double minX = Double.POSITIVE_INFINITY, minY = minX, maxX = Double.NEGATIVE_INFINITY, maxY = maxX;
            for (int corner = 0; corner < 4; corner++) {
                Point2D point = inverse.transform(new Point2D.Double(rectangle[0] + (corner % 2) * rectangle[2],
                        rectangle[1] + (corner / 2) * rectangle[3]), null);
                minX = Math.min(minX, point.getX()); minY = Math.min(minY, point.getY());
                maxX = Math.max(maxX, point.getX()); maxY = Math.max(maxY, point.getY());
            }
            result.add(new double[] {minX, minY, maxX - minX, maxY - minY});
        }
        return result;
    }

    private static ExtractionLimits textLimits() {
        return ExtractionLimits.builder().maximumPages(128).maximumPageTreeNodes(256).maximumContentStreams(1024)
                .maximumContentStreamDepth(4).maximumDecodedBytes(16 * 1024 * 1024).maximumTextItems(16384).maximumUnicodeCodePoints(16384)
                .maximumToUnicodeMappings(16384).maximumFontDataEntries(32768).maximumMarkedContentSequences(8).maximumMarkedContentDepth(4)
                .maximumStructureElements(8).maximumStructureItems(8).maximumStructureDepth(4).maximumRoleMappings(4).build();
    }

    private static String printable(String value) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 32 || character > 126) {
                String hex = Integer.toHexString(character);
                result.append("\\u"); for (int zero = hex.length(); zero < 4; zero++) { result.append('0'); }
                result.append(hex);
            } else { result.append(character); }
        }
        return result.toString();
    }

    static String content(DocumentSession session, int pageNumber) throws DocumentFailure {
        PdfDictionary page = (PdfDictionary) session.query(InspectObject.version1(
                session.query(PageObjectReference.version1(pageNumber)), PdfInspectionLimits.of(256, 16 * 1024 * 1024)));
        return streamText(session, page.get(PdfName.of("Contents")));
    }

    private static String streamText(DocumentSession session, PdfValue value) throws DocumentFailure {
        if (value instanceof PdfIndirectReference) {
            value = session.query(InspectObject.version1(((PdfIndirectReference) value).getReference(),
                    PdfInspectionLimits.of(256, 16 * 1024 * 1024)));
        }
        if (value instanceof PdfStream) { return new String(((PdfStream) value).readBytes(), StandardCharsets.US_ASCII) + "\n"; }
        require(value instanceof PdfArray, "Expected indirect content streams");
        StringBuilder content = new StringBuilder();
        PdfArray array = (PdfArray) value;
        for (int index = 0; index < array.size(); index++) { content.append(streamText(session, array.get(index))); }
        return content.toString();
    }

    /** Original path observer shared in concept with the public consumer tests, without PDFBox parsing. */
    static List<double[]> rectangles(String content) {
        List<double[]> result = new ArrayList<double[]>();
        List<Double> operands = new ArrayList<Double>();
        List<Point2D> path = new ArrayList<Point2D>();
        Deque<AffineTransform> states = new ArrayDeque<AffineTransform>();
        AffineTransform transform = new AffineTransform();
        for (String token : content.trim().split("\\s+")) {
            if (token.matches("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)")) { operands.add(Double.valueOf(token)); continue; }
            if ("q".equals(token)) { states.push(new AffineTransform(transform)); }
            if ("Q".equals(token)) { transform = states.pop(); }
            if ("cm".equals(token)) {
                transform.concatenate(new AffineTransform(operands.get(0), operands.get(1), operands.get(2),
                        operands.get(3), operands.get(4), operands.get(5)));
            }
            if ("m".equals(token) || "l".equals(token)) {
                path.add(transform.transform(new Point2D.Double(operands.get(0), operands.get(1)), null));
            }
            if ("f".equals(token)) {
                require(path.size() == 4, "Expected four corners per bar");
                double minX = Double.POSITIVE_INFINITY, minY = minX;
                double maxX = Double.NEGATIVE_INFINITY, maxY = maxX;
                for (Point2D point : path) {
                    minX = Math.min(minX, point.getX()); minY = Math.min(minY, point.getY());
                    maxX = Math.max(maxX, point.getX()); maxY = Math.max(maxY, point.getY());
                }
                result.add(new double[] {minX, minY, maxX - minX, maxY - minY}); path.clear();
            }
            require(!"Do".equals(token), "Barcode bars must not use image or Form XObjects");
            operands.clear();
        }
        require(path.isEmpty() && states.isEmpty(), "Unbalanced barcode content");
        return result;
    }

    static void require(boolean condition, String message) { if (!condition) { throw new IllegalArgumentException(message); } }
    static void near(double expected, double actual, String label) { require(Math.abs(expected - actual) <= 0.0001, label + ": " + expected + " != " + actual); }

    static final class Observation {
        final boolean passed;
        final int decodedPages;
        final int labelledPages;
        final String findings;
        Observation(boolean passed, int decodedPages, String findings) {
            this(passed, decodedPages, 0, findings);
        }
        Observation(boolean passed, int decodedPages, int labelledPages, String findings) {
            this.passed = passed; this.decodedPages = decodedPages; this.labelledPages = labelledPages; this.findings = findings;
        }
    }
}
