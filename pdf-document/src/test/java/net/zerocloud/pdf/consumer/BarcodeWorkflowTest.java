package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.Arrays;
import java.util.Collection;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
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
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.ExtractionLimits;
import net.zerocloud.pdf.TextItem;
import net.zerocloud.pdf.TextStructureExtraction;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentPermissions;
import net.zerocloud.pdf.PasswordCredential;
import net.zerocloud.pdf.PasswordSecurityPolicy;
import net.zerocloud.pdf.PdfOutputPolicy;
import net.zerocloud.pdf.PdfVersion;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.composition.Barcode1D;
import net.zerocloud.pdf.composition.BarcodeText;
import net.zerocloud.pdf.composition.FontSelection;
import net.zerocloud.pdf.composition.FontSource;
import net.zerocloud.pdf.composition.FontLimits;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.composition.command.DrawBarcode1D;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.ExtractTextAndStructure;
import net.zerocloud.pdf.query.PageObjectReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** T30 behavior through publication and reopened Native Interface Queries. */
@RunWith(Parameterized.class)
public final class BarcodeWorkflowTest {
    private static final String CAPABILITY = "composition.barcodes.one-dimensional";
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private final WorkflowExecutionProfile profile;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> profiles() {
        return Arrays.asList(new Object[][] {{WorkflowExecutionProfile.IN_PROCESS}, {WorkflowExecutionProfile.HARDENED_WORKER}});
    }
    public BarcodeWorkflowTest(WorkflowExecutionProfile profile) { this.profile = profile; }

    private WorkflowRequest create(Path target) {
        return WorkflowRequest.builder().target("target", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE).executionProfile(profile).build();
    }
    private WorkflowRequest open(Path source) {
        return WorkflowRequest.builder().source("source", DocumentSource.path(source)).primarySource("source")
                .saveMode(SaveMode.REWRITE).executionProfile(profile).build();
    }

    @Test
    public void code128PublishesTheSpecifiedVectorModulesAndPlacement() throws Exception {
        Path target = temporary.getRoot().toPath().resolve("barcode.pdf");
        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(
                create(target), session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    session.execute(DrawBarcode1D.version1(1,
                            Barcode1D.builder(Barcode1D.Mode.CODE128, "AB")
                                    .codeSet(Barcode1D.CodeSet.B)
                                    .moduleWidth(1).barHeight(48).quietZone(10).build(),
                            CanvasMatrix.of(1, 0, 0, 1, 36, 144)));
                    return null;
                });
        assertEquals(CAPABILITY, outcome.getCapabilityId());
        assertEquals(profile, outcome.getExecutionProfile());
        assertEquals(PublicationStatus.COMMITTED,
                outcome.getPublicationReceipts().get(0).getStatus());
        new DocumentWorkflow().execute(open(target), session -> {
            String content = content(session, 1);
            assertFalse("Bars must be PDF paths", content.contains(" Do"));
            List<double[]> bars = rectangles(content);
            // GS1 Code128 table: start B, A, B, modulo-103 check 102, stop.
            String runs = "2112141113231311234111312331112";
            double x = 46;
            int bar = 0;
            for (int index = 0; index < runs.length(); index++) {
                int width = runs.charAt(index) - '0';
                if (index % 2 == 0) {
                    double[] rectangle = bars.get(bar++);
                    assertEquals(x, rectangle[0], 0.0001);
                    assertEquals(144, rectangle[1], 0.0001);
                    assertEquals(width, rectangle[2], 0.0001);
                    assertEquals(48, rectangle[3], 0.0001);
                }
                x += width;
            }
            assertEquals(103, x, 0.0001);
            assertEquals(bar, bars.size());
            return null;
        });
    }

    @Test
    public void completeFullAsciiAlphabetAndDeclaredCapacityBoundariesRemainObservable() throws Exception {
        StringBuilder alphabet = new StringBuilder();
        for (int character = 0; character < 128; character++) { alphabet.append((char) character); }
        for (boolean check : new boolean[] {false, true}) {
            Barcode1D barcode = Barcode1D.builder(Barcode1D.Mode.CODE39_EXTENDED, alphabet.toString()).generateChecksum(check).build();
            assertEquals(alphabet.toString(), new Code39Reader(check, true).decodeRow(0, scanline(publishedBars(barcode)), null).getText());
        }
        String seventyNine = new String(new char[79]).replace('\0', 'A');
        assertEquals(seventyNine, decodeCode128(publishedBars(Barcode1D.builder(Barcode1D.Mode.CODE128, seventyNine)
                .codeSet(Barcode1D.CodeSet.B).build()), false).getText());
        assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE128, seventyNine + "A").codeSet(Barcode1D.CodeSet.B).build(),
                CanvasMatrix.IDENTITY, "BARCODE_INPUT_INVALID");
        String literal = "\\<FNC1>" + new String(new char[249]).replace('\0', 'A');
        assertEquals(256, literal.length());
        assertEquals(literal, decodeCode128(publishedBars(Barcode1D.builder(Barcode1D.Mode.CODE128, literal).build()), false).getText());
        int[] raw = new int[256];
        Arrays.fill(raw, 33); raw[0] = 104;
        assertEquals(new String(new char[255]).replace('\0', 'A'), decodeCode128(publishedBars(Barcode1D.rawCode128(raw).build()), false).getText());
    }

    @Test
    public void literalCode128FunctionsKeepAutoControlSwitchesDecodable() throws Exception {
        String literal = "\\<FNC1>";
        for (int functions = 1; functions <= 2; functions++) {
            String input = literal + Barcode1D.FNC4 + (functions == 2 ? String.valueOf(Barcode1D.FNC4) : "") + "\u0001A";
            assertEquals(literal + "\u0081" + (functions == 2 ? "\u00c1" : "A"),
                    decodeCode128(publishedBars(Barcode1D.builder(Barcode1D.Mode.CODE128, input).build()), false).getText());
            String reverse = literal + "\u0001" + Barcode1D.FNC4
                    + (functions == 2 ? String.valueOf(Barcode1D.FNC4) : "") + "\u007fA";
            assertEquals(literal + "\u0001\u00ff" + (functions == 2 ? "\u00c1" : "A"),
                    decodeCode128(publishedBars(Barcode1D.builder(Barcode1D.Mode.CODE128, reverse).build()), false).getText());
        }
    }

    @Test
    public void code128AutoFunctionsPreserveNumericRunUpperShiftsAndLatches() throws Exception {
        String fnc4 = String.valueOf(Barcode1D.FNC4);
        String[][] cases = {
            {fnc4 + "123456", "\u00b123456"},
            {fnc4 + fnc4 + "123456", "\u00b1\u00b2\u00b3\u00b4\u00b5\u00b6"},
            {"123456" + fnc4 + "123456", "123456\u00b123456"},
            {fnc4 + fnc4 + "12" + fnc4 + "34" + fnc4 + fnc4 + "56", "\u00b1\u00b23\u00b456"}
        };
        for (String[] fixture : cases) {
            for (Barcode1D.CodeSet set : new Barcode1D.CodeSet[] {Barcode1D.CodeSet.AUTO, Barcode1D.CodeSet.A, Barcode1D.CodeSet.B}) {
                assertEquals(fixture[1], decodeCode128(publishedBars(Barcode1D.builder(
                        Barcode1D.Mode.CODE128, fixture[0]).codeSet(set).build()), false).getText());
            }
        }
    }

    @Test
    public void invalidBarcodeInputHasStableFailureAndDoesNotPublish() throws Exception {
        for (String input : new String[] {"", "\u00e9", "\u0001", "\ud800"}) {
            assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE128, input).codeSet(Barcode1D.CodeSet.B).build(),
                    CanvasMatrix.of(1, 0, 0, 1, 36, 144), "BARCODE_INPUT_INVALID");
        }
    }

    @Test
    public void code128VariantsRecoverPayloadAndIndependentCheckValues() throws Exception {
        Object[][] cases = {
            {Barcode1D.Mode.CODE128, Barcode1D.CodeSet.A, "\u0001AB", "\u0001AB", 27, 68},
            {Barcode1D.Mode.CODE128, Barcode1D.CodeSet.B, "AB", "AB", 102, 57},
            {Barcode1D.Mode.CODE128, Barcode1D.CodeSet.C, "123456", "123456", 44, 68},
            {Barcode1D.Mode.CODE128, Barcode1D.CodeSet.AUTO, "AB123456", "AB123456", 26, 101},
            {Barcode1D.Mode.GS1_128, Barcode1D.CodeSet.C, "[01]09501101530003", "]C10109501101530003", 71, 134},
            {Barcode1D.Mode.GS1_128, Barcode1D.CodeSet.AUTO, "[01]09501101530003", "]C10109501101530003", 71, 134}
        };
        for (Object[] fixture : cases) {
            Barcode1D barcode = Barcode1D.builder((Barcode1D.Mode) fixture[0], (String) fixture[2])
                    .codeSet((Barcode1D.CodeSet) fixture[1]).build();
            List<double[]> bars = publishedBars(barcode);
            Result decoded = decodeCode128(bars, fixture[0] == Barcode1D.Mode.GS1_128);
            assertEquals(fixture[3], decoded.getText());
            byte[] words = decoded.getRawBytes();
            assertEquals(fixture[4], Integer.valueOf(words[words.length - 2] & 255));
            assertEquals(((Integer) fixture[5]).doubleValue(), paintedWidth(bars), 0.0001);
        }
    }

    private List<double[]> publishedBars(Barcode1D barcode) throws Exception {
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(create(target), session -> {
            session.execute(AddBlankPage.INSTANCE);
            session.execute(DrawBarcode1D.version1(1, barcode, CanvasMatrix.IDENTITY));
            return null;
        });
        return new DocumentWorkflow().execute(open(target),
                session -> rectangles(content(session, 1))).getResult();
    }

    @Test
    public void rawCode128RetainsCallerSymbolsAndGeneratesItsCheck() throws Exception {
        int[] input = {104, 33, 34};
        Barcode1D barcode = Barcode1D.rawCode128(input).build();
        input[1] = 35;
        Result decoded = decodeCode128(publishedBars(barcode), false);
        assertEquals("AB", decoded.getText());
        assertArrayEquals(new byte[] {104, 33, 34, 102, 106}, decoded.getRawBytes());
        decoded = decodeCode128(publishedBars(Barcode1D.rawCode128(104, 33, 98, 65, 34).build()), false);
        assertEquals("A\u0001B", decoded.getText());
        assertArrayEquals(new byte[] {104, 33, 98, 65, 34, 46, 106}, decoded.getRawBytes());
    }

    @Test
    public void malformedRawSymbolsAndTransitionsCannotPublish() throws Exception {
        int[][] invalid = {{}, {33, 34}, {104}, {104, -1}, {104, 106}, {104, 107},
            {104, 98}, {104, 98, 101}, {104, 100}, {104, 99}, {105, 100}};
        for (int[] words : invalid) {
            assertRejected(Barcode1D.rawCode128(words).build(), CanvasMatrix.IDENTITY, "BARCODE_INPUT_INVALID");
        }
        assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE128_RAW, "AB").build(),
                CanvasMatrix.IDENTITY, "BARCODE_MODE_INVALID");
        assertRejected(Barcode1D.rawCode128(104, 33).codeSet(Barcode1D.CodeSet.A).build(),
                CanvasMatrix.IDENTITY, "BARCODE_MODE_INVALID");
    }

    @Test
    public void barcodeAdmissionBoundsApplyBeforeEncoding() throws Exception {
        char[] characters = new char[257];
        java.util.Arrays.fill(characters, 'A');
        assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE128, new String(characters)).build(),
                CanvasMatrix.IDENTITY, "BARCODE_LIMIT_EXCEEDED");
        int[] words = new int[257];
        java.util.Arrays.fill(words, 33); words[0] = 104;
        assertRejected(Barcode1D.rawCode128(words).build(), CanvasMatrix.IDENTITY, "BARCODE_LIMIT_EXCEEDED");
    }

    @Test
    public void code128FunctionsAreExplicitAndLiteralEscapeLikeTextStaysLiteral() throws Exception {
        char[] functions = {Barcode1D.FNC1, Barcode1D.FNC2, Barcode1D.FNC3, Barcode1D.FNC4};
        int[] symbols = {102, 97, 96, 100};
        int[] checks = {31, 21, 19, 27};
        for (int index = 0; index < functions.length; index++) {
            Barcode1D barcode = Barcode1D.builder(Barcode1D.Mode.CODE128, "A" + functions[index] + "B")
                    .codeSet(Barcode1D.CodeSet.B).build();
            Result decoded = decodeCode128(publishedBars(barcode), false);
            assertEquals(index == 3 ? "A\u00c2" : "AB", decoded.getText());
            assertArrayEquals(new byte[] {104, 33, (byte) symbols[index], 34, (byte) checks[index], 106},
                    decoded.getRawBytes());
        }
        for (String literal : new String[] {"\\<FNC1>", "\\<FNC2>", "\\<FNC3>", "\\<FNC4>", "a\\<FNC1>\u0001"}) {
            assertEquals(literal, decodeCode128(publishedBars(
                    Barcode1D.builder(Barcode1D.Mode.CODE128, literal).build()), false).getText());
        }
    }

    @Test
    public void code39AndFullAsciiVariantsDecodeWithOptionalModulo43() throws Exception {
        Object[][] fixtures = {
            {false, "FOLIO", false, 2d, "FOLIO", 90d},
            {false, "FOLIO", false, 3d, "FOLIO", 111d},
            {false, "FOLIO", true, 2d, "FOLIOG", 103d},
            {true, "abc", false, 2d, "+A+B+C", 103d},
            {true, "abc", true, 2d, "+A+B+CR", 116d}
        };
        for (Object[] fixture : fixtures) {
            boolean extended = (Boolean) fixture[0], check = (Boolean) fixture[2];
            Barcode1D barcode = Barcode1D.builder(extended ? Barcode1D.Mode.CODE39_EXTENDED : Barcode1D.Mode.CODE39,
                    (String) fixture[1]).generateChecksum(check).wideToNarrowRatio((Double) fixture[3]).build();
            List<double[]> bars = publishedBars(barcode);
            assertEquals(fixture[1], new Code39Reader(check, extended).decodeRow(0, scanline(bars), null).getText());
            assertEquals(fixture[4], new Code39Reader().decodeRow(0, scanline(bars), null).getText());
            assertEquals((Double) fixture[5], paintedWidth(bars), 0.0001);
        }
        String controls = "\u0000a*\u007f";
        assertEquals(controls, new Code39Reader(false, true).decodeRow(0, scanline(publishedBars(
                Barcode1D.builder(Barcode1D.Mode.CODE39_EXTENDED, controls).build())), null).getText());
        String literal = "\\<FNC1>";
        assertEquals(literal, new Code39Reader(false, true).decodeRow(0, scanline(publishedBars(
                Barcode1D.builder(Barcode1D.Mode.CODE39_EXTENDED, literal).build())), null).getText());
    }

    @Test
    public void symbologyOptionsAndCharactersAreValidatedWithoutCoercion() throws Exception {
        for (String content : new String[] {"lower", "*ABC*", "\u0001", "\u00e9"}) {
            assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE39, content).build(),
                    CanvasMatrix.IDENTITY, "BARCODE_INPUT_INVALID");
        }
        assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE39_EXTENDED, "\u0080").build(),
                CanvasMatrix.IDENTITY, "BARCODE_INPUT_INVALID");
        assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE128, "a").codeSet(Barcode1D.CodeSet.A).build(),
                CanvasMatrix.IDENTITY, "BARCODE_INPUT_INVALID");
        assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE128, "123").codeSet(Barcode1D.CodeSet.C).build(),
                CanvasMatrix.IDENTITY, "BARCODE_INPUT_INVALID");
        assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE39, "AB").codeSet(Barcode1D.CodeSet.B).build(),
                CanvasMatrix.IDENTITY, "BARCODE_MODE_INVALID");
        assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE128, "AB").generateChecksum(true).build(),
                CanvasMatrix.IDENTITY, "BARCODE_MODE_INVALID");
        assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE128, "AB").wideToNarrowRatio(3).build(),
                CanvasMatrix.IDENTITY, "BARCODE_MODE_INVALID");
        for (double ratio : new double[] {1.99, 3.01, Double.NaN}) {
            assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE39, "AB").wideToNarrowRatio(ratio).build(),
                    CanvasMatrix.IDENTITY, "BARCODE_GEOMETRY_INVALID");
        }
    }

    @Test
    public void codabarPreservesGuardsAndOptionalModulo16() throws Exception {
        Map<DecodeHintType, Object> hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        hints.put(DecodeHintType.RETURN_CODABAR_START_END, Boolean.TRUE);
        for (boolean check : new boolean[] {false, true}) {
            List<double[]> bars = publishedBars(Barcode1D.builder(Barcode1D.Mode.CODABAR, "A1234B")
                    .generateChecksum(check).build());
            assertEquals(check ? "A12345B" : "A1234B", new CodaBarReader().decodeRow(0, scanline(bars), hints).getText());
            assertEquals(check ? 71 : 61, paintedWidth(bars), 0.0001);
        }
        String punctuation = "C12-.$:/+D";
        assertEquals(punctuation, new CodaBarReader().decodeRow(0, scanline(publishedBars(
                Barcode1D.builder(Barcode1D.Mode.CODABAR, punctuation).wideToNarrowRatio(3).build())), hints).getText());
    }

    @Test
    public void retailModesGenerateOrVerifyChecksAndPreserveExactDigits() throws Exception {
        Object[][] fixtures = {
            {Barcode1D.Mode.EAN13, "590123412345", "5901234123457", 95d, new EAN13Reader()},
            {Barcode1D.Mode.EAN8, "9638507", "96385074", 67d, new EAN8Reader()},
            {Barcode1D.Mode.UPCA, "04210000526", "042100005264", 95d, new UPCAReader()},
            {Barcode1D.Mode.UPCE, "0425261", "04252614", 51d, new UPCEReader()}
        };
        for (Object[] fixture : fixtures) {
            for (int input = 1; input <= 2; input++) {
                List<double[]> bars = publishedBars(Barcode1D.builder((Barcode1D.Mode) fixture[0], (String) fixture[input]).build());
                Result decoded = ((OneDReader) fixture[4]).decodeRow(0, scanline(bars), null);
                assertEquals(fixture[2], decoded.getText());
                if (fixture[0] == Barcode1D.Mode.UPCE) {
                    assertEquals("042100005264", UPCEReader.convertUPCEtoUPCA(decoded.getText()));
                }
                assertEquals((Double) fixture[3], paintedWidth(bars), 0.0001);
            }
        }
    }

    @Test
    public void standaloneSupplementsDecodeAllParityValues() throws Exception {
        for (String digits : new String[] {"00", "01", "02", "03", "05", "51234",
                "00000", "00001", "00002", "00003", "00004", "00005", "00006", "00007", "00008", "00009"}) {
            List<double[]> bars = publishedBars(Barcode1D.builder(digits.length() == 2
                    ? Barcode1D.Mode.SUPPLEMENT2 : Barcode1D.Mode.SUPPLEMENT5, digits).build());
            assertEquals(digits, decodeSupplement(scanline(bars), 4, digits.length()));
            assertEquals(digits.length() == 2 ? 20 : 47, paintedWidth(bars), 0.0001);
        }
    }

    @Test
    public void everyRetailModeCombinesWithBothSupplementLengths() throws Exception {
        Barcode1D.Mode[] modes = {Barcode1D.Mode.EAN13, Barcode1D.Mode.EAN8, Barcode1D.Mode.UPCA, Barcode1D.Mode.UPCE};
        String[] inputs = {"590123412345", "9638507", "04210000526", "0425261"};
        String[] payloads = {"5901234123457", "96385074", "042100005264", "04252614"};
        int[] widths = {95, 67, 95, 51};
        OneDReader[] readers = {new EAN13Reader(), new EAN8Reader(), new UPCAReader(), new UPCEReader()};
        for (int mode = 0; mode < modes.length; mode++) {
            for (String supplement : new String[] {"05", "51234"}) {
                List<double[]> bars = publishedBars(Barcode1D.builder(modes[mode], inputs[mode]).supplement(supplement).build());
                Result result = readers[mode].decodeRow(0, scanline(bars), null);
                assertEquals(payloads[mode], result.getText());
                assertEquals(supplement, result.getResultMetadata().get(ResultMetadataType.UPC_EAN_EXTENSION));
                List<double[]> extension = new ArrayList<double[]>();
                for (double[] bar : bars) { if (bar[0] >= bars.get(0)[0] + widths[mode] + 9) { extension.add(bar); } }
                assertEquals(supplement, decodeSupplement(scanline(extension), 4, supplement.length()));
                assertEquals(widths[mode] + 9 + (supplement.length() == 2 ? 20 : 47), paintedWidth(bars), 0.0001);
            }
        }
    }

    @Test
    public void interleavedTwoOfFiveKeepsLeadingDigitsAndOptionalCheck() throws Exception {
        Map<DecodeHintType, Object> hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        hints.put(DecodeHintType.ALLOWED_LENGTHS, new int[] {2, 6, 10});
        Object[][] fixtures = {{"123456", false, 2d, "123456", 50d}, {"12345", true, 2d, "123457", 50d},
            {"12345", true, 3d, "123457", 63d}, {"0123456789", false, 2d, "0123456789", 78d},
            {"1", true, 2d, "17", 22d}};
        for (Object[] fixture : fixtures) {
            List<double[]> bars = publishedBars(Barcode1D.builder(Barcode1D.Mode.INTERLEAVED_2_OF_5, (String) fixture[0])
                    .generateChecksum((Boolean) fixture[1]).wideToNarrowRatio((Double) fixture[2]).build());
            assertEquals(fixture[3], new ITFReader().decodeRow(0, scanline(bars), hints).getText());
            assertEquals((Double) fixture[4], paintedWidth(bars), 0.0001);
        }
        assertRejected(Barcode1D.builder(Barcode1D.Mode.INTERLEAVED_2_OF_5, "12345").build(),
                CanvasMatrix.IDENTITY, "BARCODE_INPUT_INVALID");
        assertRejected(Barcode1D.builder(Barcode1D.Mode.INTERLEAVED_2_OF_5, "123456").generateChecksum(true).build(),
                CanvasMatrix.IDENTITY, "BARCODE_INPUT_INVALID");
    }

    @Test
    public void msiDecodesBcdAndOptionalLuhnCheck() throws Exception {
        for (boolean check : new boolean[] {false, true}) {
            List<double[]> bars = publishedBars(Barcode1D.builder(Barcode1D.Mode.MSI, "1234567").generateChecksum(check).build());
            assertEquals("1234567", decodeMsi(bars, check));
            assertEquals(check ? "12345674" : "1234567", decodeMsi(bars, false));
            assertEquals(check ? 103 : 91, paintedWidth(bars), 0.0001);
        }
        assertEquals("0123456789", decodeMsi(publishedBars(Barcode1D.builder(Barcode1D.Mode.MSI, "0123456789").build()), false));
    }

    /** Original MSI decoder: framing, complementary pulse widths, BCD, then Luhn. */
    private static String decodeMsi(List<double[]> bars, boolean check) {
        assertEquals(0, (bars.size() - 3) % 4);
        assertEquals(2, bars.get(0)[2], 0.0001);
        assertEquals(1, bars.get(1)[0] - bars.get(0)[0] - bars.get(0)[2], 0.0001);
        int end = bars.size() - 2;
        assertEquals(1, bars.get(end)[2], 0.0001);
        assertEquals(1, bars.get(end + 1)[2], 0.0001);
        assertEquals(2, bars.get(end + 1)[0] - bars.get(end)[0] - 1, 0.0001);
        StringBuilder payload = new StringBuilder();
        int digit = 0;
        for (int index = 1; index < end; index++) {
            double[] bar = bars.get(index);
            int bit = Math.abs(bar[2] - 1) < 0.0001 ? 0 : 1;
            assertEquals(bit == 0 ? 1 : 2, bar[2], 0.0001);
            assertEquals(bit == 0 ? 2 : 1, bars.get(index + 1)[0] - bar[0] - bar[2], 0.0001);
            digit = (digit << 1) | bit;
            if (index % 4 == 0) { if (digit > 9) { fail("Invalid MSI BCD digit"); } payload.append(digit); digit = 0; }
        }
        if (check) {
            int sum = 0;
            for (int index = payload.length() - 1, place = 0; index >= 0; index--, place++) {
                int value = (payload.charAt(index) - '0') * (place % 2 == 0 ? 1 : 2);
                sum += value / 10 + value % 10;
            }
            assertEquals(0, sum % 10);
            payload.setLength(payload.length() - 1);
        }
        return payload.toString();
    }

    @Test
    public void postnetAndPlanetDecodeHeightsChecksAndPhysicalSpacing() throws Exception {
        Object[][] fixtures = {{false, "12345", "123455", 32}, {false, "123456789", "1234567895", 52},
            {false, "12345678901", "123456789014", 62}, {true, "40123456789", "401234567891", 62},
            {true, "4012345678901", "40123456789010", 72}};
        for (Object[] fixture : fixtures) {
            boolean planet = (Boolean) fixture[0];
            List<double[]> bars = publishedBars(Barcode1D.builder(planet ? Barcode1D.Mode.PLANET : Barcode1D.Mode.POSTNET,
                    (String) fixture[1]).build());
            assertEquals(fixture[3], Integer.valueOf(bars.size()));
            assertEquals(fixture[2], decodePostal(bars, planet));
            assertEquals(((Integer) fixture[3] - 1) * 3.24 + 1.44, paintedWidth(bars), 0.0001);
        }
        assertRejected(Barcode1D.builder(Barcode1D.Mode.POSTNET, "123456").build(), CanvasMatrix.IDENTITY, "BARCODE_INPUT_INVALID");
        assertRejected(Barcode1D.builder(Barcode1D.Mode.PLANET, "12345").build(), CanvasMatrix.IDENTITY, "BARCODE_INPUT_INVALID");
    }

    /** Original postal decoder: 7/4/2/1/0 weights, complementary PLANET heights and digit sum. */
    private static String decodePostal(List<double[]> bars, boolean planet) {
        assertEquals(0, (bars.size() - 2) % 5);
        assertEquals(9, bars.get(0)[3], 0.0001);
        assertEquals(9, bars.get(bars.size() - 1)[3], 0.0001);
        double x = 9;
        for (double[] bar : bars) {
            assertEquals(x, bar[0], 0.0001); x += 3.24;
            assertEquals(0, bar[1], 0.0001);
            assertEquals(1.44, bar[2], 0.0001);
        }
        int[] weights = {7, 4, 2, 1, 0};
        StringBuilder payload = new StringBuilder();
        int sum = 0;
        for (int index = 1; index < bars.size() - 1; index += 5) {
            int value = 0, selected = 0;
            for (int bit = 0; bit < 5; bit++) {
                double height = bars.get(index + bit)[3];
                boolean tall = Math.abs(height - 9) < 0.0001;
                assertEquals(tall ? 9 : 3.6, height, 0.0001);
                if (tall != planet) { value += weights[bit]; selected++; }
            }
            assertEquals(2, selected);
            int digit = value == 11 ? 0 : value;
            if (digit > 9) { fail("Invalid postal digit"); }
            payload.append(digit); sum += digit;
        }
        assertEquals(0, sum % 10);
        return payload.toString();
    }

    /** Original decoder: GS1 L/G digit patterns, separators and add-on parity. */
    private static String decodeSupplement(BitArray row, int unit, int digits) {
        String[] left = {"0001101", "0011001", "0010011", "0111101", "0100011",
            "0110001", "0101111", "0111011", "0110111", "0001011"};
        int x = row.getNextSet(0);
        assertEquals("1011", bits(row, x, unit, 4));
        x += 4 * unit;
        int parity = 0;
        StringBuilder payload = new StringBuilder();
        for (int index = 0; index < digits; index++) {
            String pattern = bits(row, x, unit, 7);
            int digit = -1;
            for (int candidate = 0; candidate < 10; candidate++) {
                if (pattern.equals(left[candidate])) { digit = candidate; break; }
                StringBuilder alternate = new StringBuilder();
                for (int bit = 6; bit >= 0; bit--) { alternate.append(left[candidate].charAt(bit) == '0' ? '1' : '0'); }
                if (pattern.equals(alternate.toString())) { digit = candidate; parity |= 1 << (digits - index - 1); break; }
            }
            if (digit < 0) { fail("Invalid supplement digit pattern " + pattern); }
            payload.append(digit);
            x += 7 * unit;
            if (index + 1 < digits) { assertEquals("01", bits(row, x, unit, 2)); x += 2 * unit; }
        }
        if (digits == 2) {
            assertEquals(Integer.parseInt(payload.toString()) % 4, parity);
        } else {
            int sum = 0;
            for (int index = 0; index < 5; index++) { sum += (payload.charAt(index) - '0') * (index % 2 == 0 ? 3 : 9); }
            int[] parityByCheck = {24, 20, 18, 17, 12, 6, 3, 10, 9, 5};
            assertEquals(parityByCheck[sum % 10], parity);
        }
        return payload.toString();
    }

    private static String bits(BitArray row, int first, int unit, int count) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) { result.append(row.get(first + index * unit + unit / 2) ? '1' : '0'); }
        return result.toString();
    }


    private static double paintedWidth(List<double[]> bars) {
        double[] last = bars.get(bars.size() - 1);
        return last[0] + last[2] - bars.get(0)[0];
    }

    private static Result decodeCode128(List<double[]> bars, boolean gs1) throws Exception {
        Map<DecodeHintType, Object> hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        if (gs1) { hints.put(DecodeHintType.ASSUME_GS1, Boolean.TRUE); }
        return new Code128Reader().decodeRow(0, scanline(bars), hints);
    }

    private static BitArray scanline(List<double[]> bars) {
        double[] last = bars.get(bars.size() - 1);
        BitArray row = new BitArray((int) Math.ceil((last[0] + last[2] + 20) * 4));
        for (double[] bar : bars) {
            for (int x = (int) Math.round(bar[0] * 4); x < Math.round((bar[0] + bar[2]) * 4); x++) {
                row.set(x);
            }
        }
        return row;
    }

    private void assertRejected(Barcode1D barcode, CanvasMatrix placement, String code) throws Exception {
        assertRejected(barcode, placement, code, CAPABILITY);
    }

    private void assertRejected(Barcode1D barcode, CanvasMatrix placement, String code, String capability) throws Exception {
        Path target = temporary.newFile().toPath();
        byte[] sentinel = {31, 41, 59};
        Files.write(target, sentinel);
        ByteArrayOutputStream borrowed = new ByteArrayOutputStream();
        try {
            new DocumentWorkflow().execute(WorkflowRequest.builder().saveMode(SaveMode.REWRITE).executionProfile(profile)
                    .target("path", PublicationTarget.path(target))
                    .target("stream", PublicationTarget.stream(borrowed)).build(), session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        session.execute(DrawBarcode1D.version1(1, barcode, placement));
                        return null;
                    });
            fail("Invalid barcode was published");
        } catch (DocumentFailure failure) {
            assertEquals(code, failure.getCode().name());
            assertEquals(capability, failure.getCapabilityId());
            assertEquals(2, failure.getPublicationReceipts().size());
            assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(0).getStatus());
            assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(1).getStatus());
            assertFalse(failure.getDiagnostic().contains("uk.org"));
        }
        assertArrayEquals(sentinel, Files.readAllBytes(target));
        assertEquals(0, borrowed.size());
    }

    @Test
    public void fontFailureLeavesExistingBarsUntouchedAndPreservesDependencyFailureSemantics() throws Exception {
        Barcode1D bars = Barcode1D.builder(Barcode1D.Mode.CODE128, "AB").build();
        Barcode1D missing = Barcode1D.builder(Barcode1D.Mode.CODE128, "AB").humanReadable(
                BarcodeText.builder(primaryFont(), fontLimits(), 8).alternateText("C").build()).build();
        assertRejected(missing, CanvasMatrix.IDENTITY, "FONT_GLYPH_MISSING", "composition.fonts.load-embed-subset-fallback");
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(create(target), session -> {
            session.execute(AddBlankPage.INSTANCE);
            session.execute(DrawBarcode1D.version1(1, bars, CanvasMatrix.IDENTITY));
            String before = content(session, 1);
            try { session.execute(DrawBarcode1D.version1(1, missing, CanvasMatrix.IDENTITY)); fail("Expected a missing glyph"); }
            catch (DocumentFailure failure) { assertEquals("FONT_GLYPH_MISSING", failure.getCode().name()); }
            assertEquals(before, content(session, 1));
            return null;
        });
        assertEquals(16, new DocumentWorkflow().execute(open(target), session -> rectangles(content(session, 1))).getResult().size());
    }

    @Test
    public void malformedFamilyInputsAndSuppliedChecksCannotBePublished() throws Exception {
        Object[][] invalid = {
            {Barcode1D.Mode.GS1_128, "[10]"}, {Barcode1D.Mode.GS1_128, "[01]09501101530003[10]"},
            {Barcode1D.Mode.GS1_128, "[01]123"}, {Barcode1D.Mode.GS1_128, "[9999]ABC"},
            {Barcode1D.Mode.CODABAR, "AB"}, {Barcode1D.Mode.CODABAR, "A12aB"}, {Barcode1D.Mode.CODABAR, "1234"},
            {Barcode1D.Mode.EAN13, "5901234123458"}, {Barcode1D.Mode.EAN13, "59012341234A"},
            {Barcode1D.Mode.EAN8, "96385075"}, {Barcode1D.Mode.EAN8, "123456"},
            {Barcode1D.Mode.UPCA, "042100005265"}, {Barcode1D.Mode.UPCA, "0421000052x"},
            {Barcode1D.Mode.UPCE, "04252615"}, {Barcode1D.Mode.UPCE, "2425261"},
            {Barcode1D.Mode.SUPPLEMENT2, "5"}, {Barcode1D.Mode.SUPPLEMENT2, "0A"},
            {Barcode1D.Mode.SUPPLEMENT5, "1234"}, {Barcode1D.Mode.SUPPLEMENT5, "1234x"},
            {Barcode1D.Mode.INTERLEAVED_2_OF_5, "12x4"}, {Barcode1D.Mode.MSI, "12.3"},
            {Barcode1D.Mode.POSTNET, "12a45"}, {Barcode1D.Mode.PLANET, "4012345678x"}
        };
        for (Object[] fixture : invalid) {
            assertRejected(Barcode1D.builder((Barcode1D.Mode) fixture[0], (String) fixture[1]).build(),
                    CanvasMatrix.IDENTITY, "BARCODE_INPUT_INVALID");
        }
        for (Barcode1D.Mode mode : Barcode1D.Mode.values()) {
            assertRejected(Barcode1D.builder(mode, "").build(), CanvasMatrix.IDENTITY, "BARCODE_INPUT_INVALID");
        }
        assertRejected(Barcode1D.builder(Barcode1D.Mode.EAN13, "590123412345").supplement("0A").build(),
                CanvasMatrix.IDENTITY, "BARCODE_INPUT_INVALID");
        assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE39, "ABC").supplement("05").build(),
                CanvasMatrix.IDENTITY, "BARCODE_MODE_INVALID");
    }

    @Test
    public void code128FunctionStateAndRawLatchesPreserveTheirMeaning() throws Exception {
        for (String invalid : new String[] {"AB" + Barcode1D.FNC4, "AB" + Barcode1D.FNC4 + Barcode1D.FNC1,
                "" + Barcode1D.FNC1, "AB" + Barcode1D.FNC4 + Barcode1D.FNC4}) {
            assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE128, invalid).codeSet(Barcode1D.CodeSet.B).build(),
                    CanvasMatrix.IDENTITY, "BARCODE_INPUT_INVALID");
        }
        Result shifted = decodeCode128(publishedBars(Barcode1D.builder(Barcode1D.Mode.CODE128,
                "" + Barcode1D.FNC4 + Barcode1D.FNC4 + "AB").codeSet(Barcode1D.CodeSet.B).build()), false);
        assertEquals("\u00c1\u00c2", shifted.getText());
        assertArrayEquals(new byte[] {104, 100, 100, 33, 34, 21, 106}, shifted.getRawBytes());
        Result latched = decodeCode128(publishedBars(Barcode1D.rawCode128(105, 12, 34, 100, 33, 101, 65).build()), false);
        assertEquals("1234A\u0001", latched.getText());
        assertArrayEquals(new byte[] {105, 12, 34, 100, 33, 101, 65, 70, 106}, latched.getRawBytes());
        for (Barcode1D.CodeSet set : new Barcode1D.CodeSet[] {Barcode1D.CodeSet.A, Barcode1D.CodeSet.B, Barcode1D.CodeSet.AUTO}) {
            assertEquals("]C110LOT\u001d21ABC", decodeCode128(publishedBars(Barcode1D.builder(Barcode1D.Mode.GS1_128,
                    "[10]LOT[21]ABC").codeSet(set).build()), true).getText());
        }
    }

    @Test
    public void generatedBarcodeSizeHasAStableLimitIndependentOfCanvasInternals() throws Exception {
        char[] characters = new char[256]; Arrays.fill(characters, 'a');
        assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE39_EXTENDED, new String(characters)).build(),
                CanvasMatrix.IDENTITY, "BARCODE_LIMIT_EXCEEDED");
        Arrays.fill(characters, 'A');
        List<double[]> bars = publishedBars(Barcode1D.builder(Barcode1D.Mode.CODE39, new String(characters)).build());
        assertEquals(1290, bars.size());
    }

    @Test
    public void barcodeAdmissionRejectsSignaturesPermissionsAndPagesBeforeReadingFonts() throws Exception {
        for (byte[] fixture : new byte[][] {ProjectOwnedSignatureFixtures.ordinaryApprovalSignature(),
                ProjectOwnedSignatureFixtures.docMdpSignature(3)}) {
            Path signed = temporary.newFile().toPath(); Files.write(signed, fixture);
            assertAdmissionRejected(DocumentSource.path(signed), 1, true, "SIGNATURE_POLICY_REJECTED");
            assertArrayEquals(fixture, Files.readAllBytes(signed));
        }
        try (PasswordCredential owner = PasswordCredential.of(new char[] {'o'});
                PasswordCredential user = PasswordCredential.of(new char[] {'u'})) {
            Path encrypted = temporary.newFile().toPath();
            new DocumentWorkflow().execute(WorkflowRequest.builder().target("target", PublicationTarget.path(encrypted))
                    .saveMode(SaveMode.REWRITE).executionProfile(profile).outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_1_7)
                            .withPasswordSecurity(PasswordSecurityPolicy.builder(owner, user)
                                    .permissions(DocumentPermissions.builder().build()).build())).build(), session -> {
                                        session.execute(AddBlankPage.INSTANCE); return null;
                                    });
            assertAdmissionRejected(DocumentSource.path(encrypted).withCredential(user), 1, true, "DOCUMENT_PERMISSION_DENIED");
        }
        assertAdmissionRejected(null, 2, true, "PAGE_RANGE_INVALID");
        assertAdmissionRejected(null, 0, false, "PAGE_RANGE_INVALID");
    }

    private void assertAdmissionRejected(DocumentSource source, int page, boolean label, String code) throws Exception {
        final int[] access = {0, 0};
        InputStream borrowedFont = new InputStream() {
            @Override public int read() { access[0]++; return -1; }
            @Override public void close() { access[1]++; }
        };
        Barcode1D.Builder builder = Barcode1D.builder(Barcode1D.Mode.CODE128, "AB");
        if (label) { builder.humanReadable(BarcodeText.builder(FontSelection.explicit(FontSource.stream(borrowedFont)), fontLimits(), 8).build()); }
        Barcode1D barcode = builder.build();
        Path target = temporary.newFile().toPath(); byte[] sentinel = {2, 7, 1, 8}; Files.write(target, sentinel);
        ByteArrayOutputStream borrowedOutput = new ByteArrayOutputStream();
        WorkflowRequest.Builder request = WorkflowRequest.builder().executionProfile(profile)
                .target("path", PublicationTarget.path(target)).target("stream", PublicationTarget.stream(borrowedOutput))
                .saveMode(source == null ? SaveMode.REWRITE : SaveMode.INCREMENTAL);
        if (source != null) { request.source("source", source).primarySource("source"); }
        try {
            new DocumentWorkflow().execute(request.build(), session -> {
                if (source == null) { session.execute(AddBlankPage.INSTANCE); }
                session.execute(DrawBarcode1D.version1(page, barcode, CanvasMatrix.IDENTITY)); return null;
            });
            fail("Admission must reject the barcode");
        } catch (DocumentFailure failure) {
            assertEquals(code, failure.getCode().name());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            for (net.zerocloud.pdf.PublicationReceipt receipt : failure.getPublicationReceipts()) {
                assertEquals(PublicationStatus.NOT_ATTEMPTED, receipt.getStatus());
            }
        }
        assertArrayEquals(new int[] {0, 0}, access);
        assertArrayEquals(sentinel, Files.readAllBytes(target));
        assertEquals(0, borrowedOutput.size());
    }

    @Test
    public void unsignedIncrementalBarcodesPreserveTheOriginalRevision() throws Exception {
        Path source = temporary.newFile().toPath();
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(create(source), session -> { session.execute(AddBlankPage.INSTANCE); return null; });
        byte[] original = Files.readAllBytes(source);
        Barcode1D barcode = Barcode1D.builder(Barcode1D.Mode.CODE128, "AB").humanReadable(
                BarcodeText.builder(primaryFont(), fontLimits(), 8).build()).build();
        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .source("source", DocumentSource.path(source)).primarySource("source")
                .target("target", PublicationTarget.path(target)).saveMode(SaveMode.INCREMENTAL).executionProfile(profile).build(), session -> {
                    session.execute(DrawBarcode1D.version1(1, barcode, CanvasMatrix.IDENTITY));
                    return null;
                });
        assertEquals(CAPABILITY, outcome.getCapabilityId());
        byte[] published = Files.readAllBytes(target);
        assertFalse(published.length == original.length);
        assertArrayEquals(original, Arrays.copyOf(published, original.length));
        List<double[]> bars = new DocumentWorkflow().execute(open(target), session -> {
            assertEquals("A", session.query(ExtractTextAndStructure.version1(textLimits())).getPages().get(0)
                    .getTextItems().get(0).getTextContribution());
            return rectangles(content(session, 1));
        }).getResult();
        assertEquals("AB", decodeCode128(bars, false).getText());
    }

    @Test
    public void labelAlignmentAndAboveBarPlacementAreBoundedBeforePublication() throws Exception {
        BarcodeText.Alignment[] alignments = {BarcodeText.Alignment.LEFT, BarcodeText.Alignment.CENTER, BarcodeText.Alignment.RIGHT};
        double[] expectedX = {36, 69.5, 103};
        Path target = temporary.newFile().toPath();
        FontSelection font = primaryFont();
        new DocumentWorkflow().execute(create(target), session -> {
            for (int index = 0; index < alignments.length; index++) {
                session.execute(AddBlankPage.INSTANCE);
                BarcodeText text = BarcodeText.builder(font, fontLimits(), 8).position(BarcodeText.Position.ABOVE)
                        .alignment(alignments[index]).gap(4).build();
                session.execute(DrawBarcode1D.version1(index + 1,
                        Barcode1D.builder(Barcode1D.Mode.CODE128, "AB").humanReadable(text).build(),
                        CanvasMatrix.of(1, 0, 0, 1, 36, 144)));
            }
            return null;
        });
        new DocumentWorkflow().execute(open(target), session -> {
            TextStructureExtraction extracted = session.query(ExtractTextAndStructure.version1(textLimits()));
            for (int index = 0; index < alignments.length; index++) {
                TextItem first = extracted.getPages().get(index).getTextItems().get(0);
                assertEquals(expectedX[index], first.getGeometry().getE().doubleValue(), 0.0001);
                assertEquals(196, first.getGeometry().getF().doubleValue(), 0.0001);
            }
            return null;
        });
        for (double gap : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE128, "AB").humanReadable(
                    BarcodeText.builder(font, fontLimits(), 8).gap(gap).build()).build(), CanvasMatrix.IDENTITY, "BARCODE_MODE_INVALID");
        }
        assertRejected(Barcode1D.rawCode128(104, 33).humanReadable(BarcodeText.builder(font, fontLimits(), 8).build()).build(),
                CanvasMatrix.IDENTITY, "BARCODE_MODE_INVALID");
        assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE128, "AB").humanReadable(
                BarcodeText.builder(font, fontLimits(), 8).alternateText("").build()).build(), CanvasMatrix.IDENTITY, "BARCODE_MODE_INVALID");
        char[] excessive = new char[1025]; Arrays.fill(excessive, 'A');
        assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE128, "AB").humanReadable(
                BarcodeText.builder(font, fontLimits(), 8).alternateText(new String(excessive)).build()).build(),
                CanvasMatrix.IDENTITY, "BARCODE_LIMIT_EXCEEDED");
    }

    @Test
    public void humanReadableLabelsExposeCanonicalChecksGuardsAndAlternateText() throws Exception {
        BarcodeText plain = BarcodeText.builder(notoFont(), fontLimits(), 8).build();
        BarcodeText checked = BarcodeText.builder(notoFont(), fontLimits(), 8).showChecksum(true).build();
        BarcodeText guards = BarcodeText.builder(notoFont(), fontLimits(), 8).showChecksum(true).showStartStop(true).build();
        Object[][] fixtures = {
            {Barcode1D.builder(Barcode1D.Mode.CODE128, "A" + Barcode1D.FNC1 + "B"), plain, "A<FNC1>B"},
            {Barcode1D.builder(Barcode1D.Mode.CODE128, "\u0001AB"), plain, "\\x01AB"},
            {Barcode1D.builder(Barcode1D.Mode.GS1_128, "[01]09501101530003"), plain, "(01)09501101530003"},
            {Barcode1D.rawCode128(104, 33, 34), BarcodeText.builder(notoFont(), fontLimits(), 8).alternateText("raw AB").build(), "raw AB"},
            {Barcode1D.builder(Barcode1D.Mode.CODE39, "FOLIO").generateChecksum(true), plain, "FOLIO"},
            {Barcode1D.builder(Barcode1D.Mode.CODE39, "FOLIO").generateChecksum(true), guards, "*FOLIOG*"},
            {Barcode1D.builder(Barcode1D.Mode.CODE39_EXTENDED, "abc").generateChecksum(true), checked, "abcR"},
            {Barcode1D.builder(Barcode1D.Mode.CODABAR, "A1234B").generateChecksum(true), plain, "1234"},
            {Barcode1D.builder(Barcode1D.Mode.CODABAR, "A1234B").generateChecksum(true), guards, "A12345B"},
            {Barcode1D.builder(Barcode1D.Mode.EAN13, "590123412345"), plain, "5901234123457"},
            {Barcode1D.builder(Barcode1D.Mode.EAN8, "9638507"), plain, "96385074"},
            {Barcode1D.builder(Barcode1D.Mode.UPCA, "04210000526"), plain, "042100005264"},
            {Barcode1D.builder(Barcode1D.Mode.UPCE, "0425261"), plain, "04252614"},
            {Barcode1D.builder(Barcode1D.Mode.SUPPLEMENT2, "05"), plain, "05"},
            {Barcode1D.builder(Barcode1D.Mode.SUPPLEMENT5, "51234"), plain, "51234"},
            {Barcode1D.builder(Barcode1D.Mode.EAN13, "590123412345").supplement("05"), plain, "5901234123457 05"},
            {Barcode1D.builder(Barcode1D.Mode.INTERLEAVED_2_OF_5, "12345").generateChecksum(true), checked, "123457"},
            {Barcode1D.builder(Barcode1D.Mode.MSI, "1234567").generateChecksum(true), checked, "12345674"},
            {Barcode1D.builder(Barcode1D.Mode.POSTNET, "12345"), plain, "123455"},
            {Barcode1D.builder(Barcode1D.Mode.PLANET, "40123456789"), plain, "401234567891"}
        };
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(create(target), session -> {
            for (int index = 0; index < fixtures.length; index++) {
                session.execute(AddBlankPage.INSTANCE);
                Barcode1D barcode = ((Barcode1D.Builder) fixtures[index][0]).humanReadable((BarcodeText) fixtures[index][1]).build();
                session.execute(DrawBarcode1D.version1(index + 1, barcode, CanvasMatrix.of(1, 0, 0, 1, 36, 144)));
            }
            return null;
        });
        new DocumentWorkflow().execute(open(target), session -> {
            TextStructureExtraction extraction = session.query(ExtractTextAndStructure.version1(textLimits()));
            for (int index = 0; index < fixtures.length; index++) {
                StringBuilder caption = new StringBuilder();
                for (TextItem item : extraction.getPages().get(index).getTextItems()) { caption.append(item.getTextContribution()); }
                assertEquals("label " + index, fixtures[index][2], caption.toString());
            }
            return null;
        });
    }

    private static FontSelection notoFont() throws Exception {
        try (InputStream input = BarcodeWorkflowTest.class.getResourceAsStream("/net/zerocloud/pdf/fixtures/noto/NotoSans-Regular.ttf")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) { bytes.write(buffer, 0, count); }
            return FontSelection.explicit(FontSource.bytes(bytes.toByteArray()));
        }
    }

    @Test
    public void humanReadableTextUsesExplicitFontMetricsAndTheBarcodeTransform() throws Exception {
        Barcode1D barcode = Barcode1D.builder(Barcode1D.Mode.CODE128, "AB").codeSet(Barcode1D.CodeSet.B)
                .humanReadable(BarcodeText.builder(primaryFont(), fontLimits(), 8).build()).build();
        CanvasMatrix[] placements = {CanvasMatrix.of(1, 0, 0, 1, 36, 144), CanvasMatrix.of(0, 1, -1, 0, 240, 72)};
        // FolioPrimary: A=600, B=650, yMax=700, yMin=0, UPEM=1000.
        // 77pt symbol box, 10pt text: x=33.5; baseline=-3-5.6=-8.6.
        double[][] expected = {{69.5, 135.4, 4.8, 0}, {248.6, 105.5, 0, 4.8}};
        for (int index = 0; index < placements.length; index++) {
            final int fixture = index;
            Path target = temporary.newFile().toPath();
            new DocumentWorkflow().execute(create(target), session -> {
                session.execute(AddBlankPage.INSTANCE);
                session.execute(DrawBarcode1D.version1(1, barcode, placements[fixture]));
                return null;
            });
            new DocumentWorkflow().execute(open(target), session -> {
                List<TextItem> text = session.query(ExtractTextAndStructure.version1(textLimits()))
                        .getPages().get(0).getTextItems();
                assertEquals("AB", text.get(0).getTextContribution() + text.get(1).getTextContribution());
                assertEquals(expected[fixture][0], text.get(0).getGeometry().getE().doubleValue(), 0.0001);
                assertEquals(expected[fixture][1], text.get(0).getGeometry().getF().doubleValue(), 0.0001);
                assertEquals(expected[fixture][2], text.get(0).getGeometry().getAdvanceX().doubleValue(), 0.0001);
                assertEquals(expected[fixture][3], text.get(0).getGeometry().getAdvanceY().doubleValue(), 0.0001);
                List<double[]> bars = rectangles(content(session, 1));
                assertArrayEquals(fixture == 0 ? new double[] {46, 144, 2, 48} : new double[] {192, 82, 48, 2},
                        bars.get(0), 0.0001);
                return null;
            });
        }
    }

    private static FontSelection primaryFont() throws Exception {
        try (InputStream input = BarcodeWorkflowTest.class.getResourceAsStream("/net/zerocloud/pdf/fixtures/FolioPrimary.ttf.base64")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024]; int count;
            while ((count = input.read(buffer)) != -1) { bytes.write(buffer, 0, count); }
            return FontSelection.explicit(FontSource.bytes(java.util.Base64.getMimeDecoder().decode(bytes.toByteArray())));
        }
    }

    private static FontLimits fontLimits() {
        return FontLimits.builder().maximumFontSources(2).maximumSourceBytes(1024 * 1024)
                .maximumCodePoints(1024).maximumFallbackChecks(2048).maximumGeneratedContentBytes(65536).build();
    }

    private static ExtractionLimits textLimits() {
        return ExtractionLimits.builder().maximumPages(64).maximumPageTreeNodes(128).maximumContentStreams(128)
                .maximumContentStreamDepth(4).maximumDecodedBytes(2 * 1024 * 1024).maximumTextItems(1024)
                .maximumUnicodeCodePoints(1024).maximumToUnicodeMappings(1024).maximumFontDataEntries(4096)
                .maximumMarkedContentSequences(8).maximumMarkedContentDepth(4).maximumStructureElements(8)
                .maximumStructureItems(8).maximumStructureDepth(4).maximumRoleMappings(4).build();
    }

    @Test
    public void retailGuardsAndPostalDimensionsHaveExplicitGeometryContracts() throws Exception {
        List<double[]> retail = publishedBars(Barcode1D.builder(Barcode1D.Mode.EAN13, "590123412345")
                .guardExtension(5).build());
        assertArrayEquals(new double[] {11, -5, 1, 53}, retail.get(0), 0.0001);
        // Left digit 9 is 0001011 after the 101 guard: first data bar at module 6.
        assertArrayEquals(new double[] {17, 0, 1, 48}, retail.get(2), 0.0001);
        List<double[]> postal = publishedBars(Barcode1D.builder(Barcode1D.Mode.POSTNET, "12345")
                .moduleWidth(2).postalPitch(4).barHeight(12).shortBarHeight(5).build());
        assertArrayEquals(new double[] {9, 0, 2, 12}, postal.get(0), 0.0001);
        assertArrayEquals(new double[] {13, 0, 2, 5}, postal.get(1), 0.0001);
        assertRejected(Barcode1D.builder(Barcode1D.Mode.EAN13, "590123412345").quietZone(10).build(),
                CanvasMatrix.IDENTITY, "BARCODE_GEOMETRY_INVALID");
        assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE39, "AB").guardExtension(5).build(),
                CanvasMatrix.IDENTITY, "BARCODE_MODE_INVALID");
        assertRejected(Barcode1D.builder(Barcode1D.Mode.POSTNET, "12345").postalPitch(1).build(),
                CanvasMatrix.IDENTITY, "BARCODE_GEOMETRY_INVALID");
        assertRejected(Barcode1D.builder(Barcode1D.Mode.PLANET, "40123456789").shortBarHeight(9).build(),
                CanvasMatrix.IDENTITY, "BARCODE_GEOMETRY_INVALID");
    }

    @Test
    public void invalidBarcodeDimensionsAndPlacementFailBeforePublication() throws Exception {
        CanvasMatrix placement = CanvasMatrix.of(1, 0, 0, 1, 36, 144);
        for (double width : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE128, "AB").moduleWidth(width).build(),
                    placement, "BARCODE_GEOMETRY_INVALID");
        }
        assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE128, "AB").barHeight(0).build(),
                placement, "BARCODE_GEOMETRY_INVALID");
        assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE128, "AB").quietZone(9).build(),
                placement, "BARCODE_GEOMETRY_INVALID");
        assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE128, "AB").build(),
                CanvasMatrix.of(1, 1, 1, 1, 0, 0), "BARCODE_GEOMETRY_INVALID");
        assertRejected(Barcode1D.builder(Barcode1D.Mode.CODE128, "AB").build(),
                CanvasMatrix.of(1, 0, 0, 1, Double.NaN, 0), "BARCODE_GEOMETRY_INVALID");
    }

    private static String content(DocumentSession session, int pageNumber) throws DocumentFailure {
        PdfDictionary page = (PdfDictionary) session.query(InspectObject.version1(
                session.query(PageObjectReference.version1(pageNumber)),
                PdfInspectionLimits.of(128, 16 * 1024 * 1024)));
        return streamText(session, page.get(PdfName.of("Contents")));
    }

    private static String streamText(DocumentSession session, PdfValue value) throws DocumentFailure {
        if (value instanceof PdfIndirectReference) {
            value = session.query(InspectObject.version1(((PdfIndirectReference) value).getReference(),
                    PdfInspectionLimits.of(128, 16 * 1024 * 1024)));
        }
        if (value instanceof PdfStream) {
            return new String(((PdfStream) value).readBytes(), StandardCharsets.US_ASCII) + "\n";
        }
        StringBuilder result = new StringBuilder();
        PdfArray array = (PdfArray) value;
        for (int index = 0; index < array.size(); index++) {
            result.append(streamText(session, array.get(index)));
        }
        return result.toString();
    }

    /** Reads path geometry and PDF graphics-state transforms, without backend APIs. */
    private static List<double[]> rectangles(String content) {
        List<double[]> result = new ArrayList<double[]>();
        List<Double> operands = new ArrayList<Double>();
        List<Point2D> path = new ArrayList<Point2D>();
        Deque<AffineTransform> states = new ArrayDeque<AffineTransform>();
        AffineTransform transform = new AffineTransform();
        for (String token : content.trim().split("\\s+")) {
            if (token.matches("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)")) {
                operands.add(Double.valueOf(token)); continue;
            }
            if ("q".equals(token)) { states.push(new AffineTransform(transform)); }
            if ("Q".equals(token)) { transform = states.pop(); }
            if ("cm".equals(token)) {
                transform.concatenate(new AffineTransform(operands.get(0), operands.get(1),
                        operands.get(2), operands.get(3), operands.get(4), operands.get(5)));
            }
            if ("m".equals(token) || "l".equals(token)) {
                path.add(transform.transform(new Point2D.Double(operands.get(0), operands.get(1)), null));
            }
            if ("f".equals(token)) {
                assertEquals("Each bar is a rectangle", 4, path.size());
                double minX = Double.POSITIVE_INFINITY, minY = minX;
                double maxX = Double.NEGATIVE_INFINITY, maxY = maxX;
                for (Point2D point : path) {
                    minX = Math.min(minX, point.getX()); minY = Math.min(minY, point.getY());
                    maxX = Math.max(maxX, point.getX()); maxY = Math.max(maxY, point.getY());
                }
                result.add(new double[] {minX, minY, maxX - minX, maxY - minY});
                path.clear();
            }
            operands.clear();
        }
        return result;
    }
}
