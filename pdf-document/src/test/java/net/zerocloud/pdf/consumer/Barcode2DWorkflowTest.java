package net.zerocloud.pdf.consumer;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DecoderResult;
import com.google.zxing.qrcode.decoder.Decoder;
import net.zerocloud.pdf.*;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.composition.Barcode2D;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.composition.command.DrawBarcode2D;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageObjectReference;
import net.zerocloud.pdf.query.PageCount;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** T31 public workflow, publication and reopened standard PDF syntax. */
@RunWith(Parameterized.class)
public final class Barcode2DWorkflowTest {
    private static final String CAPABILITY = "composition.barcodes.two-dimensional";
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private final WorkflowExecutionProfile profile;
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> profiles() {
        return Arrays.asList(new Object[][] {{WorkflowExecutionProfile.IN_PROCESS}, {WorkflowExecutionProfile.HARDENED_WORKER}});
    }
    public Barcode2DWorkflowTest(WorkflowExecutionProfile profile) { this.profile = profile; }
    private WorkflowRequest create(Path target) {
        return WorkflowRequest.builder().target("target", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE).executionProfile(profile).build();
    }
    private WorkflowRequest open(Path target) {
        return WorkflowRequest.builder().source("source", DocumentSource.path(target)).primarySource("source")
                .saveMode(SaveMode.REWRITE).executionProfile(profile).build();
    }

    @Test
    public void qrPublishesReusableVectorFormsAndIndependentlyDecodes() throws Exception {
        Path target = temporary.newFile().toPath();
        Barcode2D barcode = Barcode2D.builder(Barcode2D.Mode.QR, "FOLIO 31").build();
        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(create(target), session -> {
            session.execute(AddBlankPage.INSTANCE);
            session.execute(AddBlankPage.INSTANCE);
            session.execute(DrawBarcode2D.version1(1, barcode, CanvasMatrix.of(1, 0, 0, 1, 36, 144)));
            session.execute(DrawBarcode2D.version1(2, barcode, CanvasMatrix.of(0, 1, -1, 0, 240, 72)));
            return null;
        });
        assertEquals(CAPABILITY, outcome.getCapabilityId());
        assertEquals(PublicationStatus.COMMITTED, outcome.getPublicationReceipts().get(0).getStatus());
        new DocumentWorkflow().execute(open(target), session -> {
            PdfStream first = form(session, 1), second = form(session, 2);
            assertEquals("Repeated placements share one persisted Form", first.getReference(), second.getReference());
            PdfArray box = (PdfArray) first.getDictionary().get(PdfName.of("BBox"));
            assertEquals(0, number(box.get(0)), 0.0001);
            assertEquals(0, number(box.get(1)), 0.0001);
            assertEquals(29, number(box.get(2)), 0.0001);
            assertEquals(29, number(box.get(3)), 0.0001);
            DecoderResult decoded = decodeQr(modules(first, 21, 21, 4, 1, 1));
            assertEquals("FOLIO 31", decoded.getText());
            assertEquals("L", decoded.getECLevel());
            return null;
        });
    }

    @Test
    public void qrPreservesExplicitCorrectionVersionsAndTextEncodings() throws Exception {
        String[][] fixtures = {{"ISO-8859-1", "01234567"}, {"ISO-8859-1", "FOLIO 31"},
            {"ISO-8859-1", "folio-31"}, {"ISO-8859-1", "Crème brûlée"},
            {"Shift_JIS", "漢字"}, {"UTF-8", "Folio 二维 😀"}, {"Cp437", "Folio é╬"},
            {"ISO-8859-1", "\\<FNC1>\\<FNC2>\\<FNC3>\\<FNC4>"}};
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(create(target), session -> {
            int page = 0;
            for (Barcode2D.QrErrorCorrection ecc : Barcode2D.QrErrorCorrection.values()) {
                for (String[] fixture : fixtures) {
                    session.execute(AddBlankPage.INSTANCE);
                    session.execute(DrawBarcode2D.version1(++page, Barcode2D.builder(Barcode2D.Mode.QR, fixture[1])
                            .encoding(fixture[0]).qrErrorCorrection(ecc).qrVersion(4).build(), CanvasMatrix.IDENTITY));
                }
            }
            return null;
        });
        new DocumentWorkflow().execute(open(target), session -> {
            int page = 0;
            for (Barcode2D.QrErrorCorrection ecc : Barcode2D.QrErrorCorrection.values()) {
                for (String[] fixture : fixtures) {
                    PdfStream symbol = form(session, ++page);
                    assertEquals(41, number(((PdfArray) symbol.getDictionary().get(PdfName.of("BBox"))).get(2)), 0.0001);
                    DecoderResult decoded = decodeQr(modules(symbol, 33, 33, 4, 1, 1));
                    assertEquals(fixture[0], fixture[1], decoded.getText());
                    assertEquals(ecc.name(), decoded.getECLevel());
                }
            }
            return null;
        });
    }

    @Test
    public void invalidQrDeclarationsPreserveExistingPublicationTargets() throws Exception {
        for (String encoding : new String[] {"UTF-16", "ISO-8859-12", "unknown"}) {
            rejected(Barcode2D.builder(Barcode2D.Mode.QR, "AB").encoding(encoding).build(),
                    CanvasMatrix.IDENTITY, 1, DocumentFailureCode.BARCODE_MODE_INVALID);
        }
        for (String input : new String[] {"", "\ud800", "\udc00", "二"}) {
            rejected(Barcode2D.builder(Barcode2D.Mode.QR, input).build(), CanvasMatrix.IDENTITY, 1, DocumentFailureCode.BARCODE_INPUT_INVALID);
        }
        for (int version : new int[] {-1, 41}) {
            rejected(Barcode2D.builder(Barcode2D.Mode.QR, "AB").qrVersion(version).build(),
                    CanvasMatrix.IDENTITY, 1, DocumentFailureCode.BARCODE_GEOMETRY_INVALID);
        }
        Barcode2D valid = Barcode2D.builder(Barcode2D.Mode.QR, "AB").build();
        for (int page : new int[] {0, 2}) { rejected(valid, CanvasMatrix.IDENTITY, page, DocumentFailureCode.PAGE_RANGE_INVALID); }
        for (CanvasMatrix matrix : new CanvasMatrix[] {CanvasMatrix.of(1, 1, 1, 1, 0, 0),
                CanvasMatrix.of(Double.NaN, 0, 0, 1, 0, 0), CanvasMatrix.of(1, 0, 0, 1, Double.POSITIVE_INFINITY, 0)}) {
            rejected(valid, matrix, 1, DocumentFailureCode.BARCODE_GEOMETRY_INVALID);
        }
        rejected(Barcode2D.builder(Barcode2D.Mode.QR, new String(new char[8193]).replace('\0', 'A')).build(),
                CanvasMatrix.IDENTITY, 1, DocumentFailureCode.BARCODE_LIMIT_EXCEEDED);
        rejected(Barcode2D.builder(Barcode2D.Mode.QR, new String(new char[100]).replace('\0', 'A')).qrVersion(1).build(),
                CanvasMatrix.IDENTITY, 1, DocumentFailureCode.BARCODE_INPUT_INVALID);
    }

    @Test
    public void everyDefinedIso8859ProfileHasAnIndependentNonAsciiRoundTrip() throws Exception {
        String[] payloads = {"é", "Ł", "Ħ", "ĸ", "Ж", "ع", "Ω", "א", "ğ", "ĸŊ", "ก", null, "Ė", "Ẁ", "€", "Ș"};
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(create(target), session -> {
            int page = 0;
            for (int part = 1; part <= payloads.length; part++) {
                if (payloads[part - 1] == null) { continue; }
                session.execute(AddBlankPage.INSTANCE);
                session.execute(DrawBarcode2D.version1(++page, Barcode2D.builder(Barcode2D.Mode.QR, "Folio " + payloads[part - 1])
                        .encoding("ISO-8859-" + part).qrVersion(2).build(), CanvasMatrix.IDENTITY));
            }
            return null;
        });
        new DocumentWorkflow().execute(open(target), session -> {
            int page = 0;
            for (String payload : payloads) {
                if (payload == null) { continue; }
                assertEquals("Folio " + payload, decodeQr(modules(form(session, ++page), 25, 25, 4, 1, 1)).getText());
            }
            return null;
        });
    }

    @Test
    public void qrModuleSizingQuietZonesAndAffinePlacementAreExplicit() throws Exception {
        Path target = temporary.newFile().toPath();
        CanvasMatrix[] placements = {CanvasMatrix.of(1, 0, 0, 1, 36, 144),
            CanvasMatrix.of(0, 1, -1, 0, 240, 72), CanvasMatrix.of(2, 0, 0, 0.5, 24, 96)};
        new DocumentWorkflow().execute(create(target), session -> {
            for (int index = 0; index < placements.length; index++) {
                session.execute(AddBlankPage.INSTANCE);
                Barcode2D barcode = Barcode2D.builder(Barcode2D.Mode.QR, "FOLIO 31")
                        .moduleWidth(2).moduleHeight(2).quietZone(8).build();
                session.execute(DrawBarcode2D.version1(index + 1, barcode, placements[index]));
                if (index == 0) { session.execute(DrawBarcode2D.version1(1, barcode, CanvasMatrix.of(1, 0, 0, 1, 144, 144))); }
            }
            return null;
        });
        new DocumentWorkflow().execute(open(target), session -> {
            for (int index = 0; index < placements.length; index++) {
                PdfStream symbol = form(session, index + 1);
                PdfArray box = (PdfArray) symbol.getDictionary().get(PdfName.of("BBox"));
                assertEquals(58, number(box.get(2)), 0.0001);
                assertEquals(58, number(box.get(3)), 0.0001);
                assertEquals("FOLIO 31", decodeQr(modules(symbol, 21, 21, 8, 2, 2)).getText());
                java.awt.geom.AffineTransform matrix = placements(session, index + 1).get(0);
                double[] actual = {8, 8, 50, 50};
                matrix.transform(actual, 0, actual, 0, 2);
                double[][] expected = {{44, 152, 86, 194}, {232, 80, 190, 122}, {40, 100, 124, 121}};
                assertArrayEquals(expected[index], actual, 0.0001);
            }
            return null;
        });
        for (double invalid : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            rejected(Barcode2D.builder(Barcode2D.Mode.QR, "AB").moduleWidth(invalid).build(), CanvasMatrix.IDENTITY, 1,
                    DocumentFailureCode.BARCODE_GEOMETRY_INVALID);
        }
        rejected(Barcode2D.builder(Barcode2D.Mode.QR, "AB").quietZone(3).build(), CanvasMatrix.IDENTITY, 1,
                DocumentFailureCode.BARCODE_GEOMETRY_INVALID);
        rejected(Barcode2D.builder(Barcode2D.Mode.QR, "AB").moduleHeight(2).build(), CanvasMatrix.IDENTITY, 1,
                DocumentFailureCode.BARCODE_GEOMETRY_INVALID);
    }

    @Test
    public void qrHonorsSignatureAndPasswordModificationPolicies() throws Exception {
        for (byte[] fixture : new byte[][] {ProjectOwnedSignatureFixtures.ordinaryApprovalSignature(),
                ProjectOwnedSignatureFixtures.docMdpSignature(3)}) {
            Path source = temporary.newFile().toPath();
            java.nio.file.Files.write(source, fixture);
            for (SaveMode save : SaveMode.values()) {
                admissionRejected(DocumentSource.path(source), save, save == SaveMode.REWRITE
                        ? DocumentFailureCode.SIGNED_REWRITE_REJECTED : DocumentFailureCode.SIGNATURE_POLICY_REJECTED);
            }
            assertArrayEquals(fixture, java.nio.file.Files.readAllBytes(source));
        }
        try (PasswordCredential owner = PasswordCredential.of(new char[] {'o'});
                PasswordCredential user = PasswordCredential.of(new char[] {'u'})) {
            Path encrypted = temporary.newFile().toPath();
            new DocumentWorkflow().execute(WorkflowRequest.builder().target("target", PublicationTarget.path(encrypted))
                    .executionProfile(profile).saveMode(SaveMode.REWRITE).outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_1_7)
                            .withPasswordSecurity(PasswordSecurityPolicy.builder(owner, user)
                                    .permissions(DocumentPermissions.builder().build()).build())).build(), session -> {
                                        session.execute(AddBlankPage.INSTANCE); return null;
                                    });
            for (SaveMode save : SaveMode.values()) {
                admissionRejected(DocumentSource.path(encrypted).withCredential(user), save, DocumentFailureCode.DOCUMENT_PERMISSION_DENIED);
            }
        }
    }

    private void admissionRejected(DocumentSource source, SaveMode save, DocumentFailureCode code) throws Exception {
        Path target = temporary.newFile().toPath();
        byte[] original = {3, 1, 4, 1}; java.nio.file.Files.write(target, original);
        try {
            new DocumentWorkflow().execute(WorkflowRequest.builder().source("source", source).primarySource("source")
                    .target("target", PublicationTarget.path(target)).saveMode(save).executionProfile(profile).build(), session -> {
                        session.execute(DrawBarcode2D.version1(1, Barcode2D.builder(Barcode2D.Mode.QR, "AB").build(), CanvasMatrix.IDENTITY));
                        return null;
                    });
            fail("Expected " + code);
        } catch (DocumentFailure failure) {
            assertEquals(code, failure.getCode());
            String capability = code == DocumentFailureCode.SIGNED_REWRITE_REJECTED ? "document.incremental-signature.protect"
                    : code == DocumentFailureCode.DOCUMENT_PERMISSION_DENIED && save == SaveMode.REWRITE
                            ? "document.version-password-security" : CAPABILITY;
            assertEquals(capability, failure.getCapabilityId());
        }
        assertArrayEquals(original, java.nio.file.Files.readAllBytes(target));
    }

    @Test
    public void qrCanBeAppendedToAnUnsignedIncrementalRevision() throws Exception {
        Path source = temporary.newFile().toPath(), target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(create(source), session -> { session.execute(AddBlankPage.INSTANCE); return null; });
        byte[] original = java.nio.file.Files.readAllBytes(source);
        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .source("source", DocumentSource.path(source)).primarySource("source")
                .target("target", PublicationTarget.path(target)).saveMode(SaveMode.INCREMENTAL).executionProfile(profile).build(), session -> {
                    session.execute(DrawBarcode2D.version1(1, Barcode2D.builder(Barcode2D.Mode.QR, "AB").build(), CanvasMatrix.IDENTITY));
                    return null;
                });
        assertEquals(CAPABILITY, outcome.getCapabilityId());
        assertArrayEquals(original, Arrays.copyOf(java.nio.file.Files.readAllBytes(target), original.length));
        new DocumentWorkflow().execute(open(target), session -> {
            assertEquals("AB", decodeQr(modules(form(session, 1), 21, 21, 4, 1, 1)).getText()); return null;
        });
    }

    @Test
    public void dataMatrixAutoPublishesEcc200VectorsAndDecodesLiteralWords() throws Exception {
        Path target = temporary.newFile().toPath();
        String[] inputs = {"AB", "123456", "Folio 二维 😀", "\\<FNC1>\\<FNC2>"};
        new DocumentWorkflow().execute(create(target), session -> {
            for (int index = 0; index < inputs.length; index++) {
                session.execute(AddBlankPage.INSTANCE);
                session.execute(DrawBarcode2D.version1(index + 1, Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX, inputs[index])
                        .encoding(index == 2 ? "UTF-8" : "ISO-8859-1").build(), CanvasMatrix.IDENTITY));
            }
            return null;
        });
        new DocumentWorkflow().execute(open(target), session -> {
            for (int index = 0; index < inputs.length; index++) {
                PdfStream symbol = form(session, index + 1);
                PdfArray box = (PdfArray) symbol.getDictionary().get(PdfName.of("BBox"));
                int width = (int) number(box.get(2)) - 2, height = (int) number(box.get(3)) - 2;
                DecoderResult decoded = decodeDataMatrix(modules(symbol, width, height, 1, 1, 1));
                assertEquals(inputs[index], decoded.getText());
                assertEquals(Integer.valueOf(0), decoded.getErrorsCorrected());
                if (index < 2) {
                    assertEquals(10, width); assertEquals(10, height);
                    assertArrayEquals(index == 0 ? new byte[] {66, 67, (byte) 129} : new byte[] {(byte) 142, (byte) 164, (byte) 186},
                            decoded.getRawBytes());
                }
            }
            return null;
        });
    }

    @Test
    public void dataMatrixSupportsEveryEcc200SizeAndOneDimensionConstraints() throws Exception {
        int[][] sizes = {{10,10},{12,12},{18,8},{14,14},{32,8},{16,16},{26,12},{18,18},{20,20},{36,12},
            {22,22},{36,16},{24,24},{26,26},{48,16},{32,32},{36,36},{40,40},{44,44},{48,48},{52,52},
            {64,64},{72,72},{80,80},{88,88},{96,96},{104,104},{120,120},{132,132},{144,144},
            {36,0},{0,8},{0,16}};
        int[] capacities = {3,5,5,8,10,12,16,18,22,22,30,32,36,44,49,62,86,114,144,174,204,
            280,368,456,576,696,816,1050,1304,1558,22,5,12};
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(create(target), session -> {
            for (int index = 0; index < sizes.length; index++) {
                session.execute(AddBlankPage.INSTANCE);
                session.execute(DrawBarcode2D.version1(index + 1, Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX, "AB")
                        .dataMatrixSize(sizes[index][0], sizes[index][1]).moduleWidth(2).moduleHeight(3).quietZone(3).build(),
                        CanvasMatrix.IDENTITY));
            }
            return null;
        });
        new DocumentWorkflow().execute(open(target), session -> {
            for (int index = 0; index < sizes.length; index++) {
                int width = index < 30 ? sizes[index][0] : index == 30 ? 36 : index == 31 ? 18 : 16;
                int height = index < 30 ? sizes[index][1] : index == 30 ? 12 : index == 31 ? 8 : 16;
                PdfStream symbol = form(session, index + 1);
                PdfArray box = (PdfArray) symbol.getDictionary().get(PdfName.of("BBox"));
                assertEquals(width * 2 + 6, number(box.get(2)), 0.0001);
                assertEquals(height * 3 + 6, number(box.get(3)), 0.0001);
                DecoderResult decoded = decodeDataMatrix(modules(symbol, width, height, 3, 2, 3));
                assertEquals("AB", decoded.getText());
                assertEquals(capacities[index], decoded.getRawBytes().length);
                assertEquals(Integer.valueOf(0), decoded.getErrorsCorrected());
            }
            return null;
        });
        for (int[] size : new int[][] {{-1,10},{11,11},{10,12},{0,9},{145,144}}) {
            rejected(Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX, "AB").dataMatrixSize(size[0],size[1]).build(),
                    CanvasMatrix.IDENTITY, 1, DocumentFailureCode.BARCODE_GEOMETRY_INVALID);
        }
        rejected(Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX, "ABCDEFGH").dataMatrixSize(10,10).build(),
                CanvasMatrix.IDENTITY, 1, DocumentFailureCode.BARCODE_INPUT_INVALID);
        rejected(Barcode2D.builder(Barcode2D.Mode.QR, "AB").dataMatrixSize(10,10).build(),
                CanvasMatrix.IDENTITY, 1, DocumentFailureCode.BARCODE_MODE_INVALID);
    }

    @Test
    public void dataMatrixAsciiCompactionIsExplicitAndPreservesEveryByte() throws Exception {
        StringBuilder alphabet = new StringBuilder();
        for (int value = 0; value < 256; value++) { alphabet.append((char) value); }
        String[] inputs = {"ABCABC", alphabet.toString(), "Folio 二维 😀"};
        int[] sizes = {16, 80, 32};
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(create(target), session -> {
            for (int index = 0; index < inputs.length; index++) {
                session.execute(AddBlankPage.INSTANCE);
                session.execute(DrawBarcode2D.version1(index + 1, Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX, inputs[index])
                        .encoding(index == 2 ? "UTF-8" : "ISO-8859-1").dataMatrixEncoding(Barcode2D.DataMatrixEncoding.ASCII)
                        .dataMatrixSize(sizes[index], sizes[index]).build(), CanvasMatrix.IDENTITY));
            }
            return null;
        });
        new DocumentWorkflow().execute(open(target), session -> {
            for (int index = 0; index < inputs.length; index++) {
                DecoderResult decoded = decodeDataMatrix(modules(form(session, index + 1), sizes[index], sizes[index], 1, 1, 1));
                assertEquals(inputs[index], decoded.getText());
                assertEquals(Integer.valueOf(0), decoded.getErrorsCorrected());
                if (index == 0) { assertArrayEquals(new byte[] {66,67,68,66,67,68,(byte)129}, Arrays.copyOf(decoded.getRawBytes(), 7)); }
            }
            return null;
        });
    }

    @Test
    public void dataMatrixExplicitCompactionsAndRawWordsRecoverTheirPayloads() throws Exception {
        Barcode2D.DataMatrixEncoding[] encodings = {Barcode2D.DataMatrixEncoding.C40, Barcode2D.DataMatrixEncoding.TEXT,
            Barcode2D.DataMatrixEncoding.X12, Barcode2D.DataMatrixEncoding.EDIFACT, Barcode2D.DataMatrixEncoding.BASE256};
        String[] inputs = {"ABCABC", "abcabc", "ABC>12", "ABCD12", "\u0000\u0080\u00ff"};
        int[][] prefixes = {{230,89,233,89,233,254},{239,89,233,89,233,254},{238,89,233,13,79,254},
            {240,4,32,196},{231,47,193,215,235}};
        Path target = temporary.newFile().toPath();
        int[] raw = {66,67};
        Barcode2D rawSymbol = Barcode2D.rawDataMatrix(raw).dataMatrixSize(16,16).build();
        raw[0] = 68; rawSymbol.getRawCodewords()[0] = 69;
        new DocumentWorkflow().execute(create(target), session -> {
            for (int index = 0; index < encodings.length; index++) {
                session.execute(AddBlankPage.INSTANCE);
                session.execute(DrawBarcode2D.version1(index + 1, Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX, inputs[index])
                        .dataMatrixEncoding(encodings[index]).dataMatrixSize(16,16).build(), CanvasMatrix.IDENTITY));
            }
            session.execute(AddBlankPage.INSTANCE);
            session.execute(DrawBarcode2D.version1(6, rawSymbol, CanvasMatrix.IDENTITY));
            return null;
        });
        new DocumentWorkflow().execute(open(target), session -> {
            for (int index = 0; index < encodings.length; index++) {
                DecoderResult decoded = decodeDataMatrix(modules(form(session, index + 1),16,16,1,1,1));
                assertEquals(inputs[index], decoded.getText());
                assertEquals(Integer.valueOf(0), decoded.getErrorsCorrected());
                byte[] words = decoded.getRawBytes();
                for (int offset = 0; offset < prefixes[index].length; offset++) {
                    assertEquals(encodings[index].name(), prefixes[index][offset], words[offset] & 255);
                }
            }
            DecoderResult decoded = decodeDataMatrix(modules(form(session, 6),16,16,1,1,1));
            assertEquals("AB", decoded.getText());
            assertArrayEquals(new byte[] {66,67,(byte)129}, Arrays.copyOf(decoded.getRawBytes(),3));
            return null;
        });
    }

    @Test
    public void dataMatrixRawRejectsMalformedOrAmbiguousDeclarationsBeforePublication() throws Exception {
        int[][] invalid = {{},{0},{256},{-1},{129},{254},{255},{235},{235,129},
            {230},{230,89},{230,89,233},{230,255,255,254},{238,1,1},
            {240,4},{231},{231,44},{231,47,193},{241},{241,255}};
        for (int[] words : invalid) {
            rejected(Barcode2D.rawDataMatrix(words).build(), CanvasMatrix.IDENTITY, 1, DocumentFailureCode.BARCODE_INPUT_INVALID);
        }
        Barcode2D[] ambiguous = {
            Barcode2D.builder(Barcode2D.Mode.QR, "AB").dataMatrixEncoding(Barcode2D.DataMatrixEncoding.RAW).build(),
            Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX, "AB").dataMatrixEncoding(Barcode2D.DataMatrixEncoding.RAW).build(),
            Barcode2D.rawDataMatrix(66).dataMatrixEncoding(Barcode2D.DataMatrixEncoding.ASCII).build(),
            Barcode2D.rawDataMatrix(66).encoding("UTF-8").build()};
        for (Barcode2D barcode : ambiguous) {
            rejected(barcode, CanvasMatrix.IDENTITY, 1, DocumentFailureCode.BARCODE_MODE_INVALID);
        }
    }

    @Test
    public void dataMatrixRawEdifactSelectsCapacityThatPreservesExplicitTermination() throws Exception {
        int[][] words = {{240,5,240}, {240,5,240}, {240,124,66}, {66,67,240,4,32,196,5,240}};
        int[] widths = {12,12,12,32}, heights = {12,12,12,8};
        String[] expected = {"A", "A", "A", "ABABCDA"};
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(create(target), session -> {
            for (int index = 0; index < words.length; index++) {
                Barcode2D.Builder builder = Barcode2D.rawDataMatrix(words[index]);
                if (index == 1) { builder.dataMatrixSize(12,12); }
                Barcode2D symbol = builder.build();
                net.zerocloud.pdf.composition.Barcode2DSize size = session.query(
                        net.zerocloud.pdf.composition.query.MeasureBarcode2D.version1(symbol));
                assertEquals(widths[index], size.getMatrixWidth());
                assertEquals(heights[index], size.getMatrixHeight());
                session.execute(AddBlankPage.INSTANCE);
                session.execute(DrawBarcode2D.version1(index + 1, symbol, CanvasMatrix.IDENTITY));
            }
            return null;
        });
        new DocumentWorkflow().execute(open(target), session -> {
            for (int index = 0; index < words.length; index++) {
                assertEquals(expected[index], decodeDataMatrix(modules(form(session,index + 1),
                        widths[index],heights[index],1,1,1)).getText());
            }
            return null;
        });
    }

    @Test
    public void dataMatrixRawEdifactRejectsFixedCapacityThatWouldChangePayload() throws Exception {
        int[][] words = {{240,5,240}, {240,124,66}, {66,67,240,4,32,196,5,240}};
        int[] sizes = {10,10,14};
        for (int index = 0; index < words.length; index++) {
            rejected(Barcode2D.rawDataMatrix(words[index]).dataMatrixSize(sizes[index],sizes[index]).build(),
                    CanvasMatrix.IDENTITY,1,DocumentFailureCode.BARCODE_INPUT_INVALID);
        }
    }

    @Test
    public void dataMatrixCompactionTailsFitCapacityAndRecoverEveryByte() throws Exception {
        List<Barcode2D> symbols = new ArrayList<Barcode2D>();
        List<String> payloads = new ArrayList<String>();
        List<Integer> dimensions = new ArrayList<Integer>();
        for (Barcode2D.DataMatrixEncoding mode : new Barcode2D.DataMatrixEncoding[] {
                Barcode2D.DataMatrixEncoding.C40, Barcode2D.DataMatrixEncoding.TEXT, Barcode2D.DataMatrixEncoding.X12,
                Barcode2D.DataMatrixEncoding.EDIFACT}) {
            for (int length = 1; length <= 12; length++) {
                String input = (mode == Barcode2D.DataMatrixEncoding.TEXT ? "abcdefghijkl" : "ABCDEFGHIJKL").substring(0, length);
                int size = length <= 3 && mode != Barcode2D.DataMatrixEncoding.EDIFACT ? 10 : length <= 6 ? 12 : 16;
                if (mode == Barcode2D.DataMatrixEncoding.X12 && length == 5 || mode == Barcode2D.DataMatrixEncoding.EDIFACT && length == 6) { size = 16; }
                symbols.add(Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX, input).dataMatrixEncoding(mode).dataMatrixSize(size,size).build());
                payloads.add(input); dimensions.add(size);
            }
        }
        StringBuilder allBytes = new StringBuilder();
        for (int value = 0; value < 256; value++) { allBytes.append((char) value); }
        for (Barcode2D.DataMatrixEncoding mode : new Barcode2D.DataMatrixEncoding[] {
                Barcode2D.DataMatrixEncoding.C40, Barcode2D.DataMatrixEncoding.TEXT, Barcode2D.DataMatrixEncoding.BASE256}) {
            symbols.add(Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX, allBytes.toString()).dataMatrixEncoding(mode).dataMatrixSize(88,88).build());
            payloads.add(allBytes.toString()); dimensions.add(88);
        }
        for (int length : new int[] {249,250,1555}) {
            String input = new String(new char[length]).replace('\0', '\u00ff');
            symbols.add(Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX, input).dataMatrixEncoding(Barcode2D.DataMatrixEncoding.BASE256).dataMatrixSize(144,144).build());
            payloads.add(input); dimensions.add(144);
        }
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(create(target), session -> {
            for (int index = 0; index < symbols.size(); index++) {
                session.execute(AddBlankPage.INSTANCE);
                try { session.execute(DrawBarcode2D.version1(index + 1, symbols.get(index), CanvasMatrix.IDENTITY)); }
                catch (DocumentFailure failure) { throw new AssertionError(symbols.get(index).getDataMatrixEncoding() + " length "
                        + payloads.get(index).length() + " size " + dimensions.get(index), failure); }
            }
            return null;
        });
        new DocumentWorkflow().execute(open(target), session -> {
            for (int index = 0; index < symbols.size(); index++) {
                int size = dimensions.get(index);
                DecoderResult decoded = decodeDataMatrix(modules(form(session,index+1),size,size,1,1,1));
                assertEquals(symbols.get(index).getDataMatrixEncoding() + " " + payloads.get(index).length(), payloads.get(index), decoded.getText());
                assertEquals(Integer.valueOf(0), decoded.getErrorsCorrected());
            }
            return null;
        });
        rejected(Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX, new String(new char[1556]).replace('\0','x'))
                .dataMatrixEncoding(Barcode2D.DataMatrixEncoding.BASE256).build(), CanvasMatrix.IDENTITY, 1, DocumentFailureCode.BARCODE_INPUT_INVALID);
        for (Barcode2D.DataMatrixEncoding mode : new Barcode2D.DataMatrixEncoding[] {Barcode2D.DataMatrixEncoding.X12, Barcode2D.DataMatrixEncoding.EDIFACT}) {
            rejected(Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX, "abc").dataMatrixEncoding(mode).build(), CanvasMatrix.IDENTITY, 1,
                    DocumentFailureCode.BARCODE_INPUT_INVALID);
        }
    }

    @Test
    public void dataMatrixTypedHeadersPreserveMacroFnc1ReaderAndSequenceSemantics() throws Exception {
        List<Barcode2D> symbols = new ArrayList<Barcode2D>();
        for (Barcode2D.DataMatrixEncoding mode : Barcode2D.DataMatrixEncoding.values()) {
            if (mode == Barcode2D.DataMatrixEncoding.RAW) { continue; }
            for (int header = 0; header < 6; header++) {
                Barcode2D.Builder builder = Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX, "ABC").dataMatrixEncoding(mode).dataMatrixSize(26,26);
                if (header < 2) { builder.dataMatrixMacro(header == 0 ? Barcode2D.DataMatrixMacro.MACRO_05 : Barcode2D.DataMatrixMacro.MACRO_06); }
                if (header == 2) { builder.dataMatrixFnc1(true); }
                if (header == 3) { builder.dataMatrixReaderProgramming(true); }
                if (header >= 4) { builder.dataMatrixStructuredAppend(header - 3,2,75); }
                symbols.add(builder.build());
            }
        }
        symbols.add(Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX, "Folio 二维 😀").eci(26)
                .dataMatrixMacro(Barcode2D.DataMatrixMacro.MACRO_06).dataMatrixSize(32,32).build());
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(create(target), session -> {
            for (int index = 0; index < symbols.size(); index++) {
                session.execute(AddBlankPage.INSTANCE);
                session.execute(DrawBarcode2D.version1(index+1,symbols.get(index),CanvasMatrix.IDENTITY));
            }
            return null;
        });
        new DocumentWorkflow().execute(open(target), session -> {
            for (int index = 0; index < symbols.size() - 1; index++) {
                DecoderResult decoded = decodeDataMatrix(modules(form(session,index+1),26,26,1,1,1));
                int header = index % 6;
                String[] expected = {"[)>\u001e05\u001dABC\u001e\u0004", "[)>\u001e06\u001dABC\u001e\u0004", "\u001dABC", "ABC",
                    "\u000e\u0000JABC", "\u001e\u0000JABC"};
                // ZXing 3.5.3 ignores the SA latch but renders its three header words as ASCII.
                // Assert that documented decoder limitation explicitly, alongside the independent corrected header words.
                assertEquals(symbols.get(index).getDataMatrixEncoding() + " header " + header, expected[header], decoded.getText());
                assertEquals(Integer.valueOf(0), decoded.getErrorsCorrected());
                assertEquals(new int[] {236,237,232,234,233,233}[header], decoded.getRawBytes()[0] & 255);
                if (header >= 4) {
                    assertArrayEquals(new byte[] {(byte)233,(byte)(header == 4 ? 15 : 31),1,75}, Arrays.copyOf(decoded.getRawBytes(),4));
                    assertEquals("ABC", decoded.getText().substring(3));
                }
                if (header == 2) { assertEquals(2,decoded.getSymbologyModifier()); }
            }
            DecoderResult unicode = decodeDataMatrix(modules(form(session,symbols.size()),32,32,1,1,1));
            assertEquals("[)>\u001e06\u001dFolio 二维 😀\u001e\u0004",unicode.getText());
            assertArrayEquals(new byte[] {(byte)237,(byte)241,27},Arrays.copyOf(unicode.getRawBytes(),3));
            return null;
        });
    }

    @Test
    public void dataMatrixHeaderValidationRejectsContradictoryAndOutOfRangeControls() throws Exception {
        Barcode2D[] invalid = {
            Barcode2D.builder(Barcode2D.Mode.QR,"ABC").dataMatrixFnc1(true).build(),
            Barcode2D.builder(Barcode2D.Mode.QR,"ABC").dataMatrixReaderProgramming(true).build(),
            Barcode2D.builder(Barcode2D.Mode.QR,"ABC").dataMatrixMacro(Barcode2D.DataMatrixMacro.MACRO_05).build(),
            Barcode2D.builder(Barcode2D.Mode.QR,"ABC").dataMatrixStructuredAppend(1,2,75).build(),
            Barcode2D.rawDataMatrix(66).dataMatrixFnc1(true).build(),
            Barcode2D.rawDataMatrix(66).dataMatrixMacro(Barcode2D.DataMatrixMacro.MACRO_05).build(),
            Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX,"ABC").dataMatrixFnc1(true).dataMatrixReaderProgramming(true).build(),
            Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX,"ABC").dataMatrixMacro(Barcode2D.DataMatrixMacro.MACRO_05).dataMatrixStructuredAppend(1,2,75).build(),
            Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX,"ABC").dataMatrixMacro(Barcode2D.DataMatrixMacro.MACRO_05).dataMatrixReaderProgramming(true).build()};
        for (Barcode2D symbol : invalid) { rejected(symbol,CanvasMatrix.IDENTITY,1,DocumentFailureCode.BARCODE_MODE_INVALID); }
        for (int[] sequence : new int[][] {{0,2,1},{3,2,1},{1,1,1},{1,17,1},{1,2,0},{1,2,64517},{1,0,0},{0,0,1},{-1,2,1}}) {
            rejected(Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX,"ABC").dataMatrixStructuredAppend(sequence[0],sequence[1],sequence[2]).build(),
                    CanvasMatrix.IDENTITY,1,DocumentFailureCode.BARCODE_MODE_INVALID);
        }
        for (int eci : new int[] {-1,0,1,14,19,21,25,27,999999,Integer.MAX_VALUE}) {
            rejected(Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX,"ABC").eci(eci).build(),CanvasMatrix.IDENTITY,1,DocumentFailureCode.BARCODE_MODE_INVALID);
        }
    }

    @Test
    public void measurementReportsDetachedGeometryWithoutCreatingPageContent() throws Exception {
        Path target = temporary.newFile().toPath();
        net.zerocloud.pdf.composition.Barcode2DSize measured = new DocumentWorkflow().execute(create(target), session -> {
            assertEquals(Integer.valueOf(0),session.query(PageCount.INSTANCE));
            net.zerocloud.pdf.composition.Barcode2DSize qr = session.query(net.zerocloud.pdf.composition.query.MeasureBarcode2D.version1(
                    Barcode2D.builder(Barcode2D.Mode.QR,"FOLIO31").build()));
            assertEquals(21,qr.getMatrixWidth()); assertEquals(21,qr.getMatrixHeight());
            assertEquals(29,qr.getWidthPoints(),0); assertEquals(29,qr.getHeightPoints(),0);
            net.zerocloud.pdf.composition.Barcode2DSize dm = session.query(net.zerocloud.pdf.composition.query.MeasureBarcode2D.version1(
                    Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX,"AB").dataMatrixSize(18,8).moduleWidth(2).moduleHeight(3).quietZone(3).build()));
            assertEquals(Integer.valueOf(0),session.query(PageCount.INSTANCE));
            session.execute(AddBlankPage.INSTANCE);
            return dm;
        }).getResult();
        assertEquals(18,measured.getMatrixWidth()); assertEquals(8,measured.getMatrixHeight());
        assertEquals(42,measured.getWidthPoints(),0); assertEquals(30,measured.getHeightPoints(),0);
        new DocumentWorkflow().execute(open(target), session -> {
            PdfDictionary page = (PdfDictionary)session.query(InspectObject.version1(session.query(PageObjectReference.version1(1)),
                    PdfInspectionLimits.of(100000,16*1024*1024)));
            assertNull(page.get(PdfName.of("Contents")));
            PdfValue resources = page.get(PdfName.of("Resources"));
            if (resources instanceof PdfDictionary) { assertNull(((PdfDictionary)resources).get(PdfName.of("XObject"))); }
            return null;
        });
        byte[] original = java.nio.file.Files.readAllBytes(target);
        try {
            new DocumentWorkflow().execute(create(target), session -> session.query(net.zerocloud.pdf.composition.query.MeasureBarcode2D.version1(
                    Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX,"AB").dataMatrixSize(11,11).build())));
            fail("Invalid measurement must fail");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.BARCODE_GEOMETRY_INVALID,failure.getCode());
            assertEquals(CAPABILITY,failure.getCapabilityId());
            assertEquals(PublicationStatus.NOT_ATTEMPTED,failure.getPublicationReceipts().get(0).getStatus());
        }
        assertArrayEquals(original,java.nio.file.Files.readAllBytes(target));
    }

    @Test
    public void explicitForegroundColorSurvivesPublicationWithTheSameModules() throws Exception {
        Path target = temporary.newFile().toPath();
        Barcode2D[] symbols = {Barcode2D.builder(Barcode2D.Mode.QR,"ABC").foregroundRgb(0.1,0.2,0.3).build(),
            Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX,"AB").foregroundRgb(0.2,0.3,0.1).build()};
        symbols[0].getForegroundRgb()[0] = 0.9;
        new DocumentWorkflow().execute(create(target), session -> {
            for (int index = 0; index < symbols.length; index++) {
                session.execute(AddBlankPage.INSTANCE);
                session.execute(DrawBarcode2D.version1(index+1,symbols[index],CanvasMatrix.IDENTITY));
                reformatColorForm(session,index + 1);
            }
            return null;
        });
        new DocumentWorkflow().execute(open(target), session -> {
            PdfStream qr = form(session,1), dm = form(session,2);
            assertPaintedRgb(qr,0.1,0.2,0.3);
            assertPaintedRgb(dm,0.2,0.3,0.1);
            assertEquals("ABC",decodeQr(modules(qr,21,21,4,1,1)).getText());
            assertEquals("AB",decodeDataMatrix(modules(dm,10,10,1,1,1)).getText());
            return null;
        });
        for (double component : new double[] {-0.1,1.1,Double.NaN,Double.POSITIVE_INFINITY}) {
            rejected(Barcode2D.builder(Barcode2D.Mode.QR,"ABC").foregroundRgb(component,0,0).build(),CanvasMatrix.IDENTITY,1,
                    DocumentFailureCode.BARCODE_INPUT_INVALID);
        }
    }

    @Test
    public void pdf417PublishesTheDeclaredRectangleAndIndependentlyDecodesItsCorrectionLevel() throws Exception {
        Barcode2D symbol = Barcode2D.builder(Barcode2D.Mode.PDF417,"FOLIO 31")
                .pdf417Columns(3).pdf417Rows(8).pdf417ErrorCorrection(2).build();
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(create(target),session -> {
            net.zerocloud.pdf.composition.Barcode2DSize size = session.query(net.zerocloud.pdf.composition.query.MeasureBarcode2D.version1(symbol));
            assertEquals(120,size.getMatrixWidth()); assertEquals(8,size.getMatrixHeight());
            assertEquals(124,size.getWidthPoints(),0); assertEquals(28,size.getHeightPoints(),0);
            session.execute(AddBlankPage.INSTANCE);
            session.execute(DrawBarcode2D.version1(1,symbol,CanvasMatrix.IDENTITY));
            return null;
        });
        new DocumentWorkflow().execute(open(target),session -> {
            PdfStream form = form(session,1);
            PdfArray box = (PdfArray)form.getDictionary().get(PdfName.of("BBox"));
            assertEquals(124,number(box.get(2)),0.0001); assertEquals(28,number(box.get(3)),0.0001);
            com.google.zxing.Result decoded = decodePdf417(modules(form,120,8,2,1,3));
            assertEquals("FOLIO 31",decoded.getText());
            assertEquals("2",decoded.getResultMetadata().get(com.google.zxing.ResultMetadataType.ERROR_CORRECTION_LEVEL));
            return null;
        });
    }

    @Test
    public void pdf417SupportsAllCorrectionLevelsAndStrictBinaryAndTextEncodings() throws Exception {
        List<Barcode2D> symbols = new ArrayList<Barcode2D>();
        List<String> inputs = new ArrayList<String>();
        for (int ecc = -1; ecc <= 8; ecc++) {
            symbols.add(Barcode2D.builder(Barcode2D.Mode.PDF417,"FOLIO 31").pdf417Columns(8).pdf417Rows(70).pdf417ErrorCorrection(ecc).build());
            inputs.add("FOLIO 31");
        }
        StringBuilder binary = new StringBuilder();
        for (int value = 0; value < 256; value++) { binary.append((char)value); }
        String[] payloads = {binary.toString(), "12345678901234567890123456789012345678901234", "Folio 二维 😀", "\\<FNC1>ABC"};
        for (int index = 0; index < payloads.length; index++) {
            symbols.add(Barcode2D.builder(Barcode2D.Mode.PDF417,payloads[index]).pdf417Columns(8).pdf417Rows(70)
                    .pdf417Encoding(index == 0 ? Barcode2D.Pdf417Encoding.BINARY : Barcode2D.Pdf417Encoding.AUTO)
                    .encoding(index == 2 ? "UTF-8" : "ISO-8859-1").build());
            inputs.add(payloads[index]);
        }
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(create(target),session -> {
            for (int index = 0; index < symbols.size(); index++) {
                session.execute(AddBlankPage.INSTANCE); session.execute(DrawBarcode2D.version1(index+1,symbols.get(index),CanvasMatrix.IDENTITY));
            }
            return null;
        });
        new DocumentWorkflow().execute(open(target),session -> {
            for (int index = 0; index < symbols.size(); index++) {
                com.google.zxing.Result result = decodePdf417(modules(form(session,index+1),205,70,2,1,3));
                assertEquals("page " + (index+1),inputs.get(index),result.getText());
                if (index < 10) {
                    assertEquals(Integer.toString(index == 0 ? 2 : index-1),result.getResultMetadata().get(com.google.zxing.ResultMetadataType.ERROR_CORRECTION_LEVEL));
                }
            }
            return null;
        });
    }

    @Test
    public void pdf417RejectsInvalidDimensionsAndInapplicableOptionsBeforePublication() throws Exception {
        for (int columns : new int[] {-1,31,Integer.MAX_VALUE}) {
            rejected(Barcode2D.builder(Barcode2D.Mode.PDF417,"AB").pdf417Columns(columns).build(),CanvasMatrix.IDENTITY,1,
                    DocumentFailureCode.BARCODE_GEOMETRY_INVALID);
        }
        for (int rows : new int[] {-1,1,2,91,Integer.MAX_VALUE}) {
            rejected(Barcode2D.builder(Barcode2D.Mode.PDF417,"AB").pdf417Rows(rows).build(),CanvasMatrix.IDENTITY,1,
                    DocumentFailureCode.BARCODE_GEOMETRY_INVALID);
        }
        for (int ecc : new int[] {-2,9,Integer.MAX_VALUE}) {
            rejected(Barcode2D.builder(Barcode2D.Mode.PDF417,"AB").pdf417ErrorCorrection(ecc).build(),CanvasMatrix.IDENTITY,1,
                    DocumentFailureCode.BARCODE_MODE_INVALID);
        }
        Barcode2D[] modes = {
            Barcode2D.builder(Barcode2D.Mode.QR,"AB").pdf417Columns(3).build(),
            Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX,"AB").pdf417Rows(8).build(),
            Barcode2D.builder(Barcode2D.Mode.QR,"AB").pdf417ErrorCorrection(2).build(),
            Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX,"AB").pdf417Encoding(Barcode2D.Pdf417Encoding.BINARY).build(),
            Barcode2D.builder(Barcode2D.Mode.PDF417,"AB").dataMatrixSize(16,16).build(),
            Barcode2D.builder(Barcode2D.Mode.PDF417,"AB").dataMatrixEncoding(Barcode2D.DataMatrixEncoding.ASCII).build()};
        for (Barcode2D symbol : modes) { rejected(symbol,CanvasMatrix.IDENTITY,1,DocumentFailureCode.BARCODE_MODE_INVALID); }
        rejected(Barcode2D.builder(Barcode2D.Mode.PDF417,"AB").pdf417Columns(30).pdf417Rows(90).build(),CanvasMatrix.IDENTITY,1,
                DocumentFailureCode.BARCODE_GEOMETRY_INVALID);
        rejected(Barcode2D.builder(Barcode2D.Mode.PDF417,"AB").moduleHeight(2).build(),CanvasMatrix.IDENTITY,1,
                DocumentFailureCode.BARCODE_GEOMETRY_INVALID);
        rejected(Barcode2D.builder(Barcode2D.Mode.PDF417,"AB").quietZone(1).build(),CanvasMatrix.IDENTITY,1,
                DocumentFailureCode.BARCODE_GEOMETRY_INVALID);
        rejected(Barcode2D.builder(Barcode2D.Mode.PDF417,"FOLIO 31").pdf417Columns(1).pdf417Rows(3).build(),CanvasMatrix.IDENTITY,1,
                DocumentFailureCode.BARCODE_INPUT_INVALID);
    }

    @Test
    public void pdf417AutomaticAndAspectSizingHaveDeterministicPhysicalGeometry() throws Exception {
        Barcode2D[] symbols = {
            Barcode2D.builder(Barcode2D.Mode.PDF417,"AB").pdf417ErrorCorrection(0).build(),
            Barcode2D.builder(Barcode2D.Mode.PDF417,"AB").pdf417ErrorCorrection(0).pdf417Columns(2).build(),
            Barcode2D.builder(Barcode2D.Mode.PDF417,"AB").pdf417ErrorCorrection(0).pdf417Rows(8).build(),
            Barcode2D.builder(Barcode2D.Mode.PDF417,"AB").pdf417ErrorCorrection(0).pdf417AspectRatio(0.25).build(),
            Barcode2D.builder(Barcode2D.Mode.PDF417,"AB").pdf417ErrorCorrection(0).pdf417AspectRatio(0.5).build(),
            Barcode2D.builder(Barcode2D.Mode.PDF417,"AB").pdf417ErrorCorrection(0).pdf417AspectRatio(1).build()};
        int[][] expected = {{86,4},{103,3},{86,8},{120,10},{120,20},{120,40}};
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(create(target),session -> {
            for (int index = 0; index < symbols.length; index++) {
                net.zerocloud.pdf.composition.Barcode2DSize size = session.query(net.zerocloud.pdf.composition.query.MeasureBarcode2D.version1(symbols[index]));
                assertEquals(expected[index][0],size.getMatrixWidth()); assertEquals(expected[index][1],size.getMatrixHeight());
                assertEquals(expected[index][0]+4,size.getWidthPoints(),0); assertEquals(expected[index][1]*3+4,size.getHeightPoints(),0);
                session.execute(AddBlankPage.INSTANCE); session.execute(DrawBarcode2D.version1(index+1,symbols[index],CanvasMatrix.IDENTITY));
            }
            return null;
        });
        new DocumentWorkflow().execute(open(target),session -> {
            for (int index = 0; index < symbols.length; index++) {
                assertEquals("AB",decodePdf417(modules(form(session,index+1),expected[index][0],expected[index][1],2,1,3)).getText());
            }
            return null;
        });
        for (double ratio : new double[] {-1,Double.NaN,Double.POSITIVE_INFINITY}) {
            rejected(Barcode2D.builder(Barcode2D.Mode.PDF417,"AB").pdf417AspectRatio(ratio).build(),CanvasMatrix.IDENTITY,1,
                    DocumentFailureCode.BARCODE_GEOMETRY_INVALID);
        }
        rejected(Barcode2D.builder(Barcode2D.Mode.PDF417,"AB").pdf417AspectRatio(0.5).pdf417Columns(3).build(),CanvasMatrix.IDENTITY,1,
                DocumentFailureCode.BARCODE_MODE_INVALID);
        rejected(Barcode2D.builder(Barcode2D.Mode.QR,"AB").pdf417AspectRatio(0.5).build(),CanvasMatrix.IDENTITY,1,
                DocumentFailureCode.BARCODE_MODE_INVALID);
    }

    @Test
    public void pdf417RawDataGetsIndependentPaddingRowIndicatorsAndEveryCorrectionLevel() throws Exception {
        int[] input = {1};
        List<Barcode2D> symbols = new ArrayList<Barcode2D>();
        for (int ecc = 0; ecc <= 8; ecc++) {
            symbols.add(Barcode2D.rawPdf417(input).pdf417Columns(8).pdf417Rows(70).pdf417ErrorCorrection(ecc).build());
        }
        input[0] = 2; symbols.get(0).getRawCodewords()[0] = 3;
        symbols.add(Barcode2D.rawPdf417(902,1,624,434,632,282,200).pdf417Columns(8).pdf417Rows(70).pdf417ErrorCorrection(2).build());
        symbols.add(Barcode2D.rawPdf417(901,0,128,255).pdf417Columns(8).pdf417Rows(70).pdf417ErrorCorrection(2).build());
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(create(target),session -> {
            for (int index = 0; index < symbols.size(); index++) {
                session.execute(AddBlankPage.INSTANCE); session.execute(DrawBarcode2D.version1(index+1,symbols.get(index),CanvasMatrix.IDENTITY));
            }
            return null;
        });
        new DocumentWorkflow().execute(open(target),session -> {
            for (int index = 0; index < symbols.size(); index++) {
                BitMatrix matrix = modules(form(session,index+1),205,70,2,1,3);
                String expected = index < 9 ? "AB" : index == 9 ? "000213298174000" : "\u0000\u0080\u00ff";
                assertEquals(expected,decodePdf417(matrix).getText());
                int[] corrected = pdf417Words(matrix,index < 9 ? index : 2);
                assertEquals(560-(1 << ((index < 9 ? index : 2)+1)),corrected[0]);
                if (index < 9) { assertEquals(1,corrected[1]); assertEquals(900,corrected[2]); }
            }
            return null;
        });
    }

    @Test
    public void pdf417RawRejectsIncompleteCompactionAndAmbiguousDeclarations() throws Exception {
        int[][] invalid = {{},{-1},{929},{900},{899},{901},{901,256},{924,1},{924,899,899,899,899,899},
            {902},{902,2},{902,1},{913},{913,256},{927},{927,19},{925,1},{926,0,1},{928,1},{922},{923,1}};
        for (int[] words : invalid) { rejected(Barcode2D.rawPdf417(words).build(),CanvasMatrix.IDENTITY,1,DocumentFailureCode.BARCODE_INPUT_INVALID); }
        Barcode2D[] ambiguous = {
            Barcode2D.builder(Barcode2D.Mode.QR,"AB").pdf417Encoding(Barcode2D.Pdf417Encoding.RAW).build(),
            Barcode2D.builder(Barcode2D.Mode.PDF417,"AB").pdf417Encoding(Barcode2D.Pdf417Encoding.RAW).build(),
            Barcode2D.rawPdf417(1).pdf417Encoding(Barcode2D.Pdf417Encoding.BINARY).build(),
            Barcode2D.rawPdf417(1).encoding("UTF-8").build()};
        for (Barcode2D symbol : ambiguous) { rejected(symbol,CanvasMatrix.IDENTITY,1,DocumentFailureCode.BARCODE_MODE_INVALID); }
    }

    @Test
    public void pdf417RawEciPreservesByteCompactionUntilAnExplicitLatch() throws Exception {
        int[][] words = {{901,927,26,65}, {901,65,927,26,66},
            {901,927,26,195,169,927,3,233}, {924,927,26,0,0,0,0,65},
            {924,0,0,0,0,65,927,26,0,0,0,0,66}, {901,65,927,26,66,900,1},
            {901,927,26,927,3,65}, {901,927,26,0,0,0,0,300,65}};
        String[] expected = {"A", "AB", "\u00e9\u00e9", "\0\0\0\0\0A",
            "\0\0\0\0\0A\0\0\0\0\0B", "ABAB", "A", "\0\0\0\0\1,A"};
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(create(target), session -> {
            for (int index = 0; index < words.length; index++) {
                session.execute(AddBlankPage.INSTANCE);
                session.execute(DrawBarcode2D.version1(index + 1, Barcode2D.rawPdf417(words[index])
                        .pdf417Columns(3).pdf417Rows(8).pdf417ErrorCorrection(2).build(), CanvasMatrix.IDENTITY));
            }
            return null;
        });
        new DocumentWorkflow().execute(open(target), session -> {
            for (int index = 0; index < words.length; index++) {
                assertEquals(expected[index], decodePdf417(modules(form(session,index + 1),120,8,2,1,3)).getText());
            }
            return null;
        });
    }

    @Test
    public void pdf417RawEciRejectsMalformedByteGroupsBeforePublication() throws Exception {
        int[][] invalid = {{901,65,927,26,300}, {924,0,0,0,0,0,927,26,300},
            {901,65,927,26,0,0,0,0,300}, {924,0,0,0,0,0,927,26,899,899,899,899,899},
            {901,65,927,26,0,0,0,0,300,65},
            {901,927,26}, {901,65,927}, {924,927,26,65}, {901,65,927,19,66}};
        for (int[] words : invalid) {
            rejected(Barcode2D.rawPdf417(words).build(), CanvasMatrix.IDENTITY, 1, DocumentFailureCode.BARCODE_INPUT_INVALID);
        }
    }

    @Test
    public void pdf417MacroPreservesFileSegmentAndLastSegmentMetadataIncludingSingleSegmentFiles() throws Exception {
        List<Barcode2D> symbols = new ArrayList<Barcode2D>();
        List<String> payloads = new ArrayList<String>();
        for (Barcode2D.Pdf417Encoding encoding : Barcode2D.Pdf417Encoding.values()) {
            for (int index = 0; index < 2; index++) {
                String payload = encoding == Barcode2D.Pdf417Encoding.RAW ? "AB" : "PART" + (index+1);
                Barcode2D.Builder builder = encoding == Barcode2D.Pdf417Encoding.RAW ? Barcode2D.rawPdf417(1)
                        : Barcode2D.builder(Barcode2D.Mode.PDF417,payload).pdf417Encoding(encoding);
                symbols.add(builder.pdf417Macro("001075",index,2).pdf417Columns(8).pdf417Rows(40).pdf417ErrorCorrection(2).build());
                payloads.add(payload);
            }
        }
        symbols.add(Barcode2D.builder(Barcode2D.Mode.PDF417,"Folio 二维 😀").eci(26).pdf417Macro("001075",0,1)
                .pdf417Columns(8).pdf417Rows(40).pdf417ErrorCorrection(2).build()); payloads.add("Folio 二维 😀");
        symbols.add(Barcode2D.builder(Barcode2D.Mode.PDF417,"LAST").pdf417Macro("000899",99998,99999)
                .pdf417Columns(8).pdf417Rows(40).pdf417ErrorCorrection(2).build()); payloads.add("LAST");
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(create(target),session -> {
            for (int index = 0; index < symbols.size(); index++) {
                session.execute(AddBlankPage.INSTANCE); session.execute(DrawBarcode2D.version1(index+1,symbols.get(index),CanvasMatrix.IDENTITY));
            }
            return null;
        });
        new DocumentWorkflow().execute(open(target),session -> {
            for (int index = 0; index < symbols.size(); index++) {
                BitMatrix matrix = modules(form(session,index+1),205,40,2,1,3);
                com.google.zxing.Result result = decodePdf417(matrix);
                assertEquals(payloads.get(index),result.getText());
                com.google.zxing.pdf417.PDF417ResultMetadata metadata = (com.google.zxing.pdf417.PDF417ResultMetadata)
                        result.getResultMetadata().get(com.google.zxing.ResultMetadataType.PDF417_EXTRA_METADATA);
                assertEquals(index == 7 ? "000899" : "001075",metadata.getFileId());
                assertEquals(index < 6 ? index%2 : index == 6 ? 0 : 99998,metadata.getSegmentIndex());
                assertEquals(index < 6 ? 2 : index == 6 ? 1 : 99999,metadata.getSegmentCount());
                assertEquals(index >= 6 || index%2 == 1,metadata.isLastSegment());
                pdf417Words(matrix,2);
            }
            return null;
        });
    }

    @Test
    public void pdf417MacroRejectsInvalidIdentifiersAndSegmentRangesBeforePublication() throws Exception {
        rejected(Barcode2D.builder(Barcode2D.Mode.QR,"ABC").pdf417Macro("001075",0,2).build(),CanvasMatrix.IDENTITY,1,
                DocumentFailureCode.BARCODE_MODE_INVALID);
        for (int[] sequence : new int[][] {{-1,2},{2,2},{0,0},{0,-1},{0,100000},{Integer.MAX_VALUE,2}}) {
            rejected(Barcode2D.builder(Barcode2D.Mode.PDF417,"ABC").pdf417Macro("001075",sequence[0],sequence[1]).build(),CanvasMatrix.IDENTITY,1,
                    DocumentFailureCode.BARCODE_MODE_INVALID);
        }
        for (String fileId : new String[] {"","AB","1234","900","-01","000\n075"}) {
            rejected(Barcode2D.builder(Barcode2D.Mode.PDF417,"ABC").pdf417Macro(fileId,0,2).build(),CanvasMatrix.IDENTITY,1,
                    DocumentFailureCode.BARCODE_INPUT_INVALID);
        }
        rejected(Barcode2D.builder(Barcode2D.Mode.PDF417,"ABC").pdf417Macro(new String(new char[2703]).replace('\0','0'),0,2).build(),
                CanvasMatrix.IDENTITY,1,DocumentFailureCode.BARCODE_LIMIT_EXCEEDED);
    }

    @Test
    public void everyFamilyPreservesItsDeclaredCharacterSetsAndRejectsLossyAliases() throws Exception {
        String[][] encodings = {{"ISO-8859-1","Folio é"},{"ISO-8859-2","Folio Ł"},{"ISO-8859-3","Folio Ħ"},
            {"ISO-8859-4","Folio ĸ"},{"ISO-8859-5","Folio Ж"},{"ISO-8859-6","Folio ع"},{"ISO-8859-7","Folio Ω"},
            {"ISO-8859-8","Folio א"},{"ISO-8859-9","Folio ğ"},{"ISO-8859-10","Folio ĸŊ"},{"ISO-8859-11","Folio ก"},
            {"ISO-8859-13","Folio Ė"},{"ISO-8859-14","Folio Ẁ"},{"ISO-8859-15","Folio €"},{"ISO-8859-16","Folio Ș"},
            {"Cp437","Folio Ç"},{"Shift_JIS","A漢字ｶﾅ"},{"UTF-8","Folio 二维 😀"}};
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(create(target),session -> {
            int page = 0;
            for (Barcode2D.Mode mode : Barcode2D.Mode.values()) {
                for (String[] encoding : encodings) {
                    Barcode2D.Builder builder = Barcode2D.builder(mode,encoding[1]).encoding(encoding[0]);
                    if (mode == Barcode2D.Mode.QR) { builder.qrVersion(4); }
                    if (mode == Barcode2D.Mode.DATA_MATRIX) { builder.dataMatrixSize(32,32); }
                    if (mode == Barcode2D.Mode.PDF417) { builder.pdf417Columns(4).pdf417Rows(40); }
                    session.execute(AddBlankPage.INSTANCE); session.execute(DrawBarcode2D.version1(++page,builder.build(),CanvasMatrix.IDENTITY));
                }
            }
            return null;
        });
        new DocumentWorkflow().execute(open(target),session -> {
            int page = 0;
            for (Barcode2D.Mode mode : Barcode2D.Mode.values()) {
                for (String[] encoding : encodings) {
                    PdfStream form = form(session,++page);
                    String text = mode == Barcode2D.Mode.QR ? decodeQr(modules(form,33,33,4,1,1)).getText()
                            : mode == Barcode2D.Mode.DATA_MATRIX ? decodeDataMatrix(modules(form,32,32,1,1,1)).getText()
                            : decodePdf417(modules(form,137,40,2,1,3)).getText();
                    assertEquals(mode+" "+encoding[0],encoding[1],text);
                }
            }
            return null;
        });
        for (Barcode2D.Mode mode : Barcode2D.Mode.values()) {
            for (String lossy : new String[] {"¥","‾"}) {
                rejected(Barcode2D.builder(mode,lossy).encoding("Shift_JIS").build(),CanvasMatrix.IDENTITY,1,DocumentFailureCode.BARCODE_INPUT_INVALID);
            }
        }
    }

    @Test
    public void dataMatrixMacroAndFnc1ComposeIdenticallyWithAutomaticAndExplicitCompaction() throws Exception {
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(create(target),session -> {
            int page = 0;
            for (Barcode2D.DataMatrixEncoding mode : new Barcode2D.DataMatrixEncoding[] {Barcode2D.DataMatrixEncoding.AUTO,
                    Barcode2D.DataMatrixEncoding.ASCII,Barcode2D.DataMatrixEncoding.BASE256}) {
                session.execute(AddBlankPage.INSTANCE);
                session.execute(DrawBarcode2D.version1(++page,Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX,"ABC")
                        .dataMatrixMacro(Barcode2D.DataMatrixMacro.MACRO_05).dataMatrixFnc1(true).dataMatrixEncoding(mode)
                        .dataMatrixSize(26,26).build(),CanvasMatrix.IDENTITY));
            }
            return null;
        });
        new DocumentWorkflow().execute(open(target),session -> {
            for (int page = 1; page <= 3; page++) {
                DecoderResult decoded = decodeDataMatrix(modules(form(session,page),26,26,1,1,1));
                assertArrayEquals(new byte[] {(byte)236,(byte)232},Arrays.copyOf(decoded.getRawBytes(),2));
                assertEquals("[)>\u001e05\u001d\u001dABC\u001e\u0004",decoded.getText());
                assertEquals(Integer.valueOf(0),decoded.getErrorsCorrected());
            }
            return null;
        });
    }

    @Test
    public void pdf417CapacityBoundaryStillFindsALegalAutomaticRectangle() throws Exception {
        int[] data = new int[925]; Arrays.fill(data,1);
        StringBuilder payload = new StringBuilder();
        for (int index = 0; index < 925; index++) { payload.append("AB"); }
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(create(target),session -> {
            for (int index = 0; index < 4; index++) {
                Barcode2D.Builder builder = index < 2 ? Barcode2D.rawPdf417(data) : Barcode2D.builder(Barcode2D.Mode.PDF417,payload.toString());
                builder.pdf417ErrorCorrection(0);
                if (index%2 == 0) { builder.pdf417Columns(16).pdf417Rows(58); }
                session.execute(AddBlankPage.INSTANCE); session.execute(DrawBarcode2D.version1(index+1,builder.build(),CanvasMatrix.IDENTITY));
            }
            return null;
        });
        new DocumentWorkflow().execute(open(target),session -> {
            for (int page = 1; page <= 4; page++) {
                BitMatrix matrix = modules(form(session,page),341,58,2,1,3);
                assertEquals(payload.toString(),decodePdf417(matrix).getText());
                int[] observed = pdf417Words(matrix,0); assertEquals(928,observed.length); assertEquals(926,observed[0]);
            }
            return null;
        });
        int[] excessive = new int[926]; Arrays.fill(excessive,1);
        rejected(Barcode2D.rawPdf417(excessive).pdf417ErrorCorrection(0).build(),CanvasMatrix.IDENTITY,1,DocumentFailureCode.BARCODE_INPUT_INVALID);
        rejected(Barcode2D.builder(Barcode2D.Mode.PDF417,payload+"AB").pdf417ErrorCorrection(0).build(),CanvasMatrix.IDENTITY,1,
                DocumentFailureCode.BARCODE_INPUT_INVALID);
    }

    @Test
    public void allQrVersionsAndMaximumQrAndDataMatrixPayloadsDecodeWithoutCorrection() throws Exception {
        String numeric = new String(new char[7089]).replace('\0','7');
        String ascii = new String(new char[1558]).replace('\0','A');
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(create(target),session -> {
            for (int version = 1; version <= 40; version++) {
                session.execute(AddBlankPage.INSTANCE);
                session.execute(DrawBarcode2D.version1(version,Barcode2D.builder(Barcode2D.Mode.QR,"AB").qrVersion(version).build(),CanvasMatrix.IDENTITY));
            }
            session.execute(AddBlankPage.INSTANCE);
            session.execute(DrawBarcode2D.version1(41,Barcode2D.builder(Barcode2D.Mode.QR,numeric).qrVersion(40).build(),CanvasMatrix.IDENTITY));
            session.execute(AddBlankPage.INSTANCE);
            session.execute(DrawBarcode2D.version1(42,Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX,ascii)
                    .dataMatrixEncoding(Barcode2D.DataMatrixEncoding.ASCII).dataMatrixSize(144,144).build(),CanvasMatrix.IDENTITY));
            return null;
        });
        new DocumentWorkflow().execute(open(target),session -> {
            for (int version = 1; version <= 40; version++) {
                int size = 17+4*version;
                DecoderResult decoded = decodeQr(modules(form(session,version),size,size,4,1,1));
                assertEquals("AB",decoded.getText()); assertEquals(Integer.valueOf(0),decoded.getErrorsCorrected());
            }
            DecoderResult qr = decodeQr(modules(form(session,41),177,177,4,1,1));
            assertEquals(numeric,qr.getText()); assertEquals(Integer.valueOf(0),qr.getErrorsCorrected());
            DecoderResult dm = decodeDataMatrix(modules(form(session,42),144,144,1,1,1));
            assertEquals(ascii,dm.getText()); assertEquals(Integer.valueOf(0),dm.getErrorsCorrected());
            return null;
        });
        rejected(Barcode2D.builder(Barcode2D.Mode.QR,numeric+"7").qrVersion(40).build(),CanvasMatrix.IDENTITY,1,DocumentFailureCode.BARCODE_INPUT_INVALID);
        rejected(Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX,ascii+"A").dataMatrixEncoding(Barcode2D.DataMatrixEncoding.ASCII)
                .dataMatrixSize(144,144).build(),CanvasMatrix.IDENTITY,1,DocumentFailureCode.BARCODE_INPUT_INVALID);
    }

    @Test
    public void caughtInvalidCommandsPreserveExistingContentAndEveryFamilyReusesFormsAcrossPages() throws Exception {
        for (Barcode2D.Mode mode : Barcode2D.Mode.values()) {
            Barcode2D symbol = Barcode2D.builder(mode,"AB").build();
            Path target = temporary.newFile().toPath();
            new DocumentWorkflow().execute(create(target),session -> {
                session.execute(AddBlankPage.INSTANCE);
                session.execute(DrawBarcode2D.version1(1,symbol,CanvasMatrix.IDENTITY));
                PdfDictionary page = page(session,1);
                String content = streamText(session,page.get(PdfName.of("Contents")));
                try {
                    session.execute(DrawBarcode2D.version1(1,Barcode2D.builder(mode,"AB").quietZone(0).build(),CanvasMatrix.IDENTITY));
                    fail("Invalid command must fail");
                } catch (DocumentFailure failure) { assertEquals(DocumentFailureCode.BARCODE_GEOMETRY_INVALID,failure.getCode()); }
                assertEquals(content,streamText(session,page(session,1).get(PdfName.of("Contents"))));
                session.execute(DrawBarcode2D.version1(1,symbol,CanvasMatrix.of(1,0,0,1,40,40)));
                session.execute(AddBlankPage.INSTANCE); session.execute(DrawBarcode2D.version1(2,symbol,CanvasMatrix.IDENTITY));
                return null;
            });
            new DocumentWorkflow().execute(open(target),session -> {
                PdfDictionary one = (PdfDictionary)resolve(session,((PdfDictionary)resolve(session,page(session,1).get(PdfName.of("Resources")))).get(PdfName.of("XObject")));
                PdfDictionary two = (PdfDictionary)resolve(session,((PdfDictionary)resolve(session,page(session,2).get(PdfName.of("Resources")))).get(PdfName.of("XObject")));
                assertEquals(1,one.size()); assertEquals(1,two.size());
                assertEquals(((PdfIndirectReference)one.getEntry(0).getValue()).getReference(),((PdfIndirectReference)two.getEntry(0).getValue()).getReference());
                assertEquals(2,placements(session,1).size());
                PdfStream form = form(session,1);
                String text = mode == Barcode2D.Mode.QR ? decodeQr(modules(form,21,21,4,1,1)).getText()
                        : mode == Barcode2D.Mode.DATA_MATRIX ? decodeDataMatrix(modules(form,10,10,1,1,1)).getText()
                        : decodePdf417(modules(form,103,5,2,1,3)).getText();
                assertEquals("AB",text); return null;
            });
        }
    }

    @Test
    public void memoryLimitRemainsTerminalEvenWhenTheBarcodeFailureIsCaught() throws Exception {
        WorkflowResourcePolicy defaults = WorkflowResourcePolicy.safeDefaults();
        WorkflowResourcePolicy low = WorkflowResourcePolicy.builder().maximumOwnedMemoryBytes(1024*1024)
                .maximumInputBytes(defaults.getMaximumInputBytes()).maximumPages(defaults.getMaximumPages()).maximumObjects(defaults.getMaximumObjects())
                .maximumNestingDepth(defaults.getMaximumNestingDepth()).maximumDecompressedBytes(defaults.getMaximumDecompressedBytes())
                .maximumDecodedPixels(0).maximumTemporaryStorageBytes(defaults.getMaximumTemporaryStorageBytes())
                .maximumElapsedTime(defaults.getMaximumElapsedTime()).maximumConcurrentWorkflows(defaults.getMaximumConcurrentWorkflows()).build();
        for (Barcode2D.Mode mode : Barcode2D.Mode.values()) {
            Path target = temporary.newFile().toPath(); byte[] original = {9,3,1}; java.nio.file.Files.write(target,original);
            final boolean[] caught = {false};
            try {
                new DocumentWorkflow().execute(WorkflowRequest.builder().target("target",PublicationTarget.path(target)).saveMode(SaveMode.REWRITE)
                        .executionProfile(profile).resourcePolicy(low).build(),session -> {
                            session.execute(AddBlankPage.INSTANCE);
                            try { session.execute(DrawBarcode2D.version1(1,Barcode2D.builder(mode,"AB").build(),CanvasMatrix.IDENTITY)); }
                            catch (DocumentFailure failure) { assertEquals(DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,failure.getCode()); caught[0] = true; }
                            return null;
                        });
                fail("A caught resource failure cannot permit publication");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,failure.getCode());
                assertEquals("document.hostile-input-limits",failure.getCapabilityId());
                assertEquals(PublicationStatus.NOT_ATTEMPTED,failure.getPublicationReceipts().get(0).getStatus());
            }
            assertTrue(caught[0]); assertArrayEquals(original,java.nio.file.Files.readAllBytes(target));
        }
    }

    private static PdfDictionary page(DocumentSession session, int page) throws DocumentFailure {
        return (PdfDictionary)session.query(InspectObject.version1(session.query(PageObjectReference.version1(page)),PdfInspectionLimits.of(100000,16*1024*1024)));
    }

    private static int[] pdf417Words(BitMatrix matrix, int correction) {
        int columns = (matrix.getWidth()-69)/17, rows = matrix.getHeight();
        assertTrue(columns >= 1 && columns <= 30 && rows >= 3 && rows <= 90 && columns*rows <= 928);
        int[] words = new int[columns*rows];
        int[] indicators = {(rows-1)/3,3*correction+(rows-1)%3,columns-1};
        for (int row = 0; row < rows; row++) {
            assertEquals(0x1fea8,pdfBits(matrix,0,row,17));
            assertEquals(0x3fa29,pdfBits(matrix,matrix.getWidth()-18,row,18));
            assertEquals(30*(row/3)+indicators[row%3],com.google.zxing.pdf417.PDF417Common.getCodeword(pdfBits(matrix,17,row,17)));
            assertEquals(30*(row/3)+indicators[(row+2)%3],com.google.zxing.pdf417.PDF417Common.getCodeword(pdfBits(matrix,34+17*columns,row,17)));
            for (int col = 0; col < columns; col++) {
                int word = com.google.zxing.pdf417.PDF417Common.getCodeword(pdfBits(matrix,34+17*col,row,17));
                assertTrue(word >= 0); words[row*columns+col] = word;
            }
        }
        try { assertEquals(0,new com.google.zxing.pdf417.decoder.ec.ErrorCorrection().decode(words,1 << (correction+1),new int[0])); }
        catch (com.google.zxing.ChecksumException failure) { throw new AssertionError("Independent PDF417 ECC validation failed",failure); }
        return words;
    }
    private static int pdfBits(BitMatrix matrix, int x, int y, int count) {
        int value = 0;
        for (int bit = 0; bit < count; bit++) { value = (value << 1) | (matrix.get(x+bit,y) ? 1 : 0); }
        return value;
    }

    private static com.google.zxing.Result decodePdf417(BitMatrix modules) {
        int width = modules.getWidth()*3+24, height = modules.getHeight()*9+24;
        int[] pixels = new int[width*height]; Arrays.fill(pixels,0xffffff);
        for (int y = 0; y < modules.getHeight(); y++) {
            for (int x = 0; x < modules.getWidth(); x++) {
                if (modules.get(x,y)) {
                    for (int dy = 0; dy < 9; dy++) { Arrays.fill(pixels,(12+y*9+dy)*width+12+x*3,(12+y*9+dy)*width+15+x*3,0); }
                }
            }
        }
        try {
            return new com.google.zxing.pdf417.PDF417Reader().decode(new com.google.zxing.BinaryBitmap(
                    new com.google.zxing.common.HybridBinarizer(new com.google.zxing.RGBLuminanceSource(width,height,pixels))));
        } catch (com.google.zxing.ReaderException failure) { throw new AssertionError("Independent PDF417 decoding failed",failure); }
    }

    private static List<java.awt.geom.AffineTransform> placements(DocumentSession session, int pageNumber) throws DocumentFailure {
        PdfDictionary page = (PdfDictionary) session.query(InspectObject.version1(
                session.query(PageObjectReference.version1(pageNumber)), PdfInspectionLimits.of(100000, 16 * 1024 * 1024)));
        String content = streamText(session, page.get(PdfName.of("Contents")));
        java.util.Deque<java.awt.geom.AffineTransform> stack = new java.util.ArrayDeque<java.awt.geom.AffineTransform>();
        java.awt.geom.AffineTransform matrix = new java.awt.geom.AffineTransform();
        List<java.awt.geom.AffineTransform> result = new ArrayList<java.awt.geom.AffineTransform>();
        List<Double> operands = new ArrayList<Double>();
        for (String token : content.trim().split("\\s+")) {
            if (token.matches("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)")) { operands.add(Double.valueOf(token)); continue; }
            if ("q".equals(token)) { stack.push(new java.awt.geom.AffineTransform(matrix)); }
            if ("Q".equals(token)) { matrix = stack.pop(); }
            if ("cm".equals(token)) { matrix.concatenate(new java.awt.geom.AffineTransform(operands.get(0), operands.get(1),
                    operands.get(2), operands.get(3), operands.get(4), operands.get(5))); }
            if ("Do".equals(token)) { result.add(new java.awt.geom.AffineTransform(matrix)); }
            operands.clear();
        }
        assertTrue(stack.isEmpty());
        return result;
    }
    private static String streamText(DocumentSession session, PdfValue value) throws DocumentFailure {
        PdfValue resolved = resolve(session, value);
        if (resolved instanceof PdfStream) { return new String(((PdfStream) resolved).readBytes(), StandardCharsets.US_ASCII) + "\n"; }
        PdfArray array = (PdfArray) resolved;
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < array.size(); index++) { result.append(streamText(session, array.get(index))); }
        return result.toString();
    }

    private void rejected(Barcode2D barcode, CanvasMatrix placement, int page, DocumentFailureCode code) throws Exception {
        Path target = temporary.newFile().toPath();
        byte[] original = {7, 3, 1, 9};
        java.nio.file.Files.write(target, original);
        try {
            new DocumentWorkflow().execute(create(target), session -> {
                session.execute(AddBlankPage.INSTANCE);
                session.execute(DrawBarcode2D.version1(page, barcode, placement));
                return null;
            });
            fail("Expected " + code);
        } catch (DocumentFailure failure) {
            assertEquals(code, failure.getCode());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(1, failure.getPublicationReceipts().size());
            assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(0).getStatus());
        }
        assertArrayEquals(original, java.nio.file.Files.readAllBytes(target));
    }

    private static double number(PdfValue value) { return ((PdfNumber) value).decimalValue().doubleValue(); }
    private static DecoderResult decodeQr(BitMatrix matrix) {
        try { return new Decoder().decode(matrix); }
        catch (com.google.zxing.ReaderException failure) { throw new AssertionError("Independent QR decoding failed", failure); }
    }
    private static DecoderResult decodeDataMatrix(BitMatrix matrix) {
        try { return new com.google.zxing.datamatrix.decoder.Decoder().decode(matrix); }
        catch (com.google.zxing.ReaderException failure) { throw new AssertionError("Independent DataMatrix decoding failed", failure); }
    }
    private static PdfValue resolve(DocumentSession session, PdfValue value) throws DocumentFailure {
        if (value instanceof PdfIndirectReference) {
            return session.query(InspectObject.version1(((PdfIndirectReference) value).getReference(),
                    PdfInspectionLimits.of(100000, 16 * 1024 * 1024)));
        }
        return value;
    }
    private static PdfStream form(DocumentSession session, int pageNumber) throws DocumentFailure {
        PdfDictionary page = (PdfDictionary) session.query(InspectObject.version1(
                session.query(PageObjectReference.version1(pageNumber)), PdfInspectionLimits.of(100000, 16 * 1024 * 1024)));
        PdfDictionary resources = (PdfDictionary) resolve(session, page.get(PdfName.of("Resources")));
        PdfDictionary objects = (PdfDictionary) resolve(session, resources.get(PdfName.of("XObject")));
        assertEquals(1, objects.size());
        PdfStream form = (PdfStream) resolve(session, objects.getEntry(0).getValue());
        assertEquals(PdfName.of("Form"), form.getDictionary().get(PdfName.of("Subtype")));
        return form;
    }
    private static void reformatColorForm(DocumentSession session, int pageNumber) throws DocumentFailure {
        PdfStream original = form(session,pageNumber);
        PdfDictionary resources = (PdfDictionary) resolve(session,page(session,pageNumber).get(PdfName.of("Resources")));
        PdfDictionary objects = (PdfDictionary) resolve(session,resources.get(PdfName.of("XObject")));
        String content = new String(original.readBytes(),StandardCharsets.US_ASCII);
        String equivalent = "q\n" + content.replace(" "," \t").replace("rg\n","rg\nq\n0 0 0 rg\nQ\n") + "\nQ\n";
        PdfDictionary dictionary = PdfDictionary.builder().put(PdfName.of("Type"),PdfName.of("XObject"))
                .put(PdfName.of("Subtype"),PdfName.of("Form")).put(PdfName.of("BBox"),original.getDictionary().get(PdfName.of("BBox")))
                .put(PdfName.of("Resources"),PdfDictionary.builder().build()).build();
        PdfStream changed = PdfStream.of(dictionary,equivalent.getBytes(StandardCharsets.US_ASCII));
        session.execute(DocumentPatch.builder().setDictionaryEntry(session.query(PageObjectReference.version1(pageNumber)),
                PdfName.of("Resources"),PdfDictionary.builder().put(PdfName.of("XObject"),PdfDictionary.builder()
                        .put(objects.getEntry(0).getName(),changed).build()).build()).build());
    }
    private static void assertPaintedRgb(PdfStream form, double... expected) throws DocumentFailure {
        String content = new String(form.readBytes(),StandardCharsets.US_ASCII);
        List<Double> operands = new ArrayList<Double>();
        java.util.Deque<double[]> saved = new java.util.ArrayDeque<double[]>();
        double[] rgb = {0,0,0};
        int fills = 0;
        for (String token : content.trim().split("\\s+")) {
            if (token.matches("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)")) { operands.add(Double.valueOf(token)); continue; }
            if ("q".equals(token)) { saved.push(rgb.clone()); }
            else if ("Q".equals(token)) { rgb = saved.pop(); }
            else if ("rg".equals(token)) { rgb = new double[] {operands.get(0),operands.get(1),operands.get(2)}; }
            else if ("g".equals(token)) { rgb = new double[] {operands.get(0),operands.get(0),operands.get(0)}; }
            else if ("f".equals(token) || "F".equals(token) || "f*".equals(token)) {
                assertArrayEquals("Foreground active when the module path is painted",expected,rgb,0.000001);
                fills++;
            }
            operands.clear();
        }
        assertTrue("The symbol must paint modules",fills > 0);
        assertTrue("Balanced graphics state",saved.isEmpty());
    }
    private static BitMatrix modules(PdfStream form, int width, int height, double quiet, double unitX, double unitY)
            throws DocumentFailure {
        String content = new String(form.readBytes(), StandardCharsets.US_ASCII);
        assertFalse("No raster images or nested Forms", content.contains("Do"));
        assertFalse("No barcode font", content.contains("BT"));
        BitMatrix result = new BitMatrix(width, height);
        List<Double> operands = new ArrayList<Double>();
        for (String token : content.trim().split("\\s+")) {
            if (token.matches("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)")) { operands.add(Double.valueOf(token)); continue; }
            if ("re".equals(token)) {
                double x = (operands.get(0) - quiet) / unitX;
                double y = (operands.get(1) - quiet) / unitY;
                double w = operands.get(2) / unitX, h = operands.get(3) / unitY;
                for (double value : new double[] {x, y, w, h}) { assertEquals(Math.rint(value), value, 0.0001); }
                assertTrue(x >= 0 && y >= 0 && x + w <= width && y + h <= height);
                result.setRegion((int) Math.rint(x), height - (int) Math.rint(y + h), (int) Math.rint(w), (int) Math.rint(h));
            }
            operands.clear();
        }
        return result;
    }
}
