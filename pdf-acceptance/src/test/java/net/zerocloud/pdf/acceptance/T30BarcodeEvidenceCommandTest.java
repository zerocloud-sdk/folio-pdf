package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.PageObjectReference;
import com.google.zxing.EncodeHintType;
import com.google.zxing.oned.Code128Writer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Assume;
import org.junit.rules.TemporaryFolder;

/** T30 acceptance consumes actual public Workflow PDFs and independent symbol oracles. */
public final class T30BarcodeEvidenceCommandTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void recorderRetainsBothProfilesAndRepeatableObservationsWithoutOverwritingEvidence() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot"));
        Path pins = temporary.newFolder("recorder-pins").toPath();
        for (String name : new String[] {"qpdf", "pdfium", "imagemagick"}) {
            Files.copy(root.resolve("scripts/" + name + "-pin.properties"), pins.resolve(name + ".properties"));
        }
        Path first = temporary.newFolder("first-record").toPath();
        Path second = temporary.newFolder("second-record").toPath();
        for (Path output : new Path[] {first, second}) {
            T30BarcodeEvidenceCommand.main(new String[] {output.toString(), pins.resolve("qpdf.properties").toString(),
                pins.resolve("pdfium.properties").toString(), pins.resolve("imagemagick.properties").toString(),
                root.resolve("capabilities/profiles").toString(), "0.1.0-SNAPSHOT"});
            assertTrue(read(output.resolve("T30-one-dimensional-barcodes-semantic.md")).contains("Result: `pass`"));
            assertTrue(read(output.resolve("T30-one-dimensional-barcodes-semantic.md")).contains("Decoded pages: `232`"));
            assertTrue(read(output.resolve("T30-one-dimensional-barcodes-syntax.md")).contains("Result: `indeterminate`"));
            assertTrue(read(output.resolve("T30-one-dimensional-barcodes-visual.md")).contains("Result: `indeterminate`"));
            assertTrue(read(output.resolve("T30-one-dimensional-barcodes-standards.md")).contains("Result: `indeterminate`"));
        }
        for (String suffix : new String[] {"in-process", "hardened-worker", "reference"}) {
            String file = "artifacts/T30-one-dimensional-barcodes-" + suffix + ".pdf";
            assertEquals(EvidenceFiles.idNeutralPdfSha256(first.resolve(file)), EvidenceFiles.idNeutralPdfSha256(second.resolve(file)));
        }
        for (String suffix : new String[] {"in-process", "hardened-worker"}) {
            String file = "artifacts/T30-one-dimensional-barcodes-" + suffix + "-semantic.txt";
            assertEquals(read(first.resolve(file)), read(second.resolve(file)));
        }
        byte[] retained = Files.readAllBytes(first.resolve("T30-one-dimensional-barcodes-semantic.md"));
        try {
            T30BarcodeEvidenceCommand.main(new String[] {first.toString(), pins.resolve("qpdf.properties").toString(),
                pins.resolve("pdfium.properties").toString(), pins.resolve("imagemagick.properties").toString(),
                root.resolve("capabilities/profiles").toString(), "0.1.0-SNAPSHOT"});
            fail("Existing T30 evidence must not be overwritten");
        } catch (java.io.IOException expected) { }
        org.junit.Assert.assertArrayEquals(retained, Files.readAllBytes(first.resolve("T30-one-dimensional-barcodes-semantic.md")));
    }

    private static String read(Path path) throws Exception { return new String(Files.readAllBytes(path), StandardCharsets.UTF_8); }

    @Test
    public void actualPdfAndRasterNegativeControlsRejectMissingBarsLabelsMovementAndBadChecks() throws Exception {
        Path pdf = temporary.newFile("negative-source.pdf").toPath();
        T30BarcodeProducts.create(pdf, WorkflowExecutionProfile.IN_PROCESS);
        java.util.List<Integer> accepted = new java.util.ArrayList<Integer>();
        for (int control = 0; control < 5; control++) {
            final int mutation = control;
            Path changed = temporary.newFile().toPath();
            new DocumentWorkflow().execute(WorkflowRequest.builder().source("primary", DocumentSource.path(pdf)).primarySource("primary")
                    .target("output", PublicationTarget.path(changed)).saveMode(SaveMode.REWRITE).build(), session -> {
                        ObjectReference page = session.query(PageObjectReference.version1(1));
                        String original = T30BarcodeAssertions.content(session, 1);
                        String modified = mutation == 0 ? original.replaceFirst("(?s)[-+0-9.]+ [-+0-9.]+ m\\s+.*?\\bf\\b\\s*", "")
                                : mutation == 1 ? original.replaceFirst("(?s)BT.*?ET", "")
                                : mutation == 2 ? "1 0 0 1 1 0 cm\n" + original
                                : mutation == 3 ? original.replaceFirst("1 0 0 1 (\\S+) (\\S+) Tm", "1 0 .25 1 $1 $2 Tm")
                                : original.replaceFirst(" Tm", " Tm\n3 Tr");
                        assertFalse(original.equals(modified));
                        session.execute(DocumentPatch.builder().setDictionaryEntry(page, PdfName.of("Contents"),
                                PdfStream.of(PdfDictionary.builder().build(), modified.getBytes(StandardCharsets.US_ASCII))).build());
                        return null;
                    });
            if (T30BarcodeAssertions.inspect(changed).passed) { accepted.add(control); }
        }
        assertEquals("Missing/moved bars, missing/sheared/invisible labels must fail", Collections.emptyList(), accepted);
        // PDFBox is only a convenient test raster producer. The retained visual authority uses pinned PDFium.
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (String id : new String[] {"code128-b", "supplement-05", "supplement-51234", "msi-check", "postnet-5"}) {
                int page = 0;
                for (T30BarcodeProfile.Fixture fixture : T30BarcodeProfile.fixtures()) {
                    if (!fixture.id.equals(id)) { page++; continue; }
                    BufferedImage raster = renderer.renderImageWithDPI(page, 288, ImageType.RGB);
                    assertTrue(T30RasterDecoder.decode(raster, fixture).startsWith("payload="));
                    if (id.equals("code128-b")) {
                        // Replace the mandatory word 102 with valid symbol 99; framing remains intact.
                        boolean[] ninetyNine = new Code128Writer().encode("99", Collections.singletonMap(EncodeHintType.FORCE_CODE_SET, "C"));
                        for (int module = 0; module < 11; module++) { paint(raster, 46 + 33 + module, 144, 1, 48, ninetyNine[11 + module]); }
                    } else if (id.startsWith("supplement-")) {
                        // Reverse and complement the first seven-module digit to change only its L/G parity.
                        boolean[] digit = new boolean[7];
                        for (int module = 0; module < 7; module++) { digit[module] = (raster.getRGB((50 + module) * 4 + 2, raster.getHeight() - 1 - 160 * 4) & 255) < 128; }
                        for (int module = 0; module < 7; module++) { paint(raster, 50 + module, 144, 1, 48, !digit[6 - module]); }
                    } else if (id.equals("msi-check")) {
                        // Change the final BCD pulse 100 to 110: the observed check digit becomes 5 instead of 4.
                        paint(raster, 46 + 97, 144, 1, 48, true);
                    } else {
                        // Change the POSTNET check digit from 5 (01010) to 6 (01100), retaining two tall bars.
                        paint(raster, 45 + 28 * 3.24, 147.6, 1.44, 5.4, true);
                        paint(raster, 45 + 29 * 3.24, 147.6, 1.44, 5.4, false);
                    }
                    try { T30RasterDecoder.decode(raster, fixture); fail(id + " corrupted check/parity was accepted"); }
                    catch (com.google.zxing.ChecksumException expected) { assertEquals("code128-b", id); }
                    catch (IllegalArgumentException expected) { assertFalse("code128-b".equals(id)); }
                    break;
                }
            }
        }
    }

    private static void paint(BufferedImage raster, double x, double y, double width, double height, boolean black) {
        for (int px = (int) Math.ceil(x * 4); px < Math.floor((x + width) * 4); px++) {
            for (int py = (int) Math.ceil(y * 4); py < Math.floor((y + height) * 4); py++) {
                raster.setRGB(px, raster.getHeight() - 1 - py, black ? 0xff000000 : 0xffffffff);
            }
        }
    }

    @Test
    public void missingRasterToolsAreIndeterminate() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot"));
        Path pins = temporary.newFolder("pins").toPath();
        for (String name : new String[] {"pdfium", "imagemagick"}) {
            Files.copy(root.resolve("scripts/" + name + "-pin.properties"), pins.resolve(name + ".properties"));
        }
        Path output = temporary.newFolder("rasters").toPath();
        T30RasterEvidence.Observation observed = T30RasterEvidence.record(output.resolve("product.pdf"), output.resolve("reference.pdf"),
                output, PdfiumPin.load(pins.resolve("pdfium.properties")), ImageMagickPin.load(pins.resolve("imagemagick.properties")));
        assertEquals(EvidenceResult.INDETERMINATE, observed.result);
        assertEquals(0, observed.decodedPages);
    }

    @Test
    public void pinnedRastersDecodeAndMatchEveryDeclaredVariant() throws Exception {
        Assume.assumeTrue("Explicit offline tools are required for this acceptance run", Boolean.getBoolean("t30.raster"));
        Path root = Paths.get(System.getProperty("repositoryRoot"));
        Path output = temporary.newFolder("actual-rasters").toPath();
        Path reference = output.resolve("reference.pdf");
        T30BarcodeReference.create(reference);
        for (WorkflowExecutionProfile profile : WorkflowExecutionProfile.values()) {
            Path pdf = output.resolve(profile.name() + ".pdf");
            T30BarcodeProducts.create(pdf, profile);
            T30RasterEvidence.Observation observed = T30RasterEvidence.record(pdf, reference, output,
                    PdfiumPin.load(root.resolve("scripts/pdfium-pin.properties")),
                    ImageMagickPin.load(root.resolve("scripts/imagemagick-pin.properties")));
            assertEquals(observed.findings, EvidenceResult.PASS, observed.result);
            assertEquals(116, observed.decodedPages);
        }
    }

    @Test
    public void independentlyPositionedReferenceMeetsTheSameDeclaredProfile() throws Exception {
        Path reference = temporary.newFile("reference.pdf").toPath();
        T30BarcodeReference.create(reference);
        T30BarcodeAssertions.Observation observed = T30BarcodeAssertions.inspect(reference);
        assertTrue(observed.findings, observed.passed);
        assertEquals(116, observed.decodedPages);
        assertEquals(115, observed.labelledPages);
    }

    @Test
    public void declaredVariantsMatchIndependentModulesAndDecoding() throws Exception {
        for (WorkflowExecutionProfile profile : WorkflowExecutionProfile.values()) {
            Path pdf = temporary.newFile().toPath();
            T30BarcodeProducts.create(pdf, profile);
            T30BarcodeAssertions.Observation observed = T30BarcodeAssertions.inspect(pdf);
            assertTrue(observed.findings, observed.passed);
            assertEquals(116, observed.decodedPages);
            assertEquals(115, observed.labelledPages);
            assertTrue(observed.findings.contains("code128-b: payload=AB; check=102"));
            for (String id : new String[] {"code128-a", "code128-c", "code128-auto", "gs1-auto", "gs1-a", "gs1-b", "gs1-c",
                    "fnc1", "fnc2", "fnc3", "fnc4", "fnc4-latch", "raw-b", "raw-shift", "raw-latches",
                    "raw-fnc1", "raw-fnc2", "raw-fnc3", "raw-fnc4", "literal-fnc1", "literal-fnc2", "literal-fnc3", "literal-fnc4",
                    "literal-fnc4-a-single", "literal-fnc4-a-pair", "literal-fnc4-b-single", "literal-fnc4-b-pair",
                    "fnc4-auto-single-digits", "fnc4-auto-pair-digits", "fnc4-auto-digits-before", "fnc4-auto-shift-and-unlatch",
                    "code39", "code39-check", "code39-ratio3", "code39-full", "code39-full-check", "code39-full-ratio3",
                    "code39-controls", "code39-literal", "codabar", "codabar-check", "codabar-ratio3",
                    "ean13-body", "ean13-complete", "ean8-body", "ean8-complete", "upca-body", "upca-complete", "upce-body", "upce-complete",
                    "upce-0123450", "upce-0123453", "upce-0123454", "upce-0123455",
                    "upce-1123450", "upce-1123453", "upce-1123454", "upce-1123455",
                    "itf", "itf-check", "itf-ratio3", "itf-short", "itf-leading-zero", "msi", "msi-check", "msi-leading-zero",
                    "postnet-5", "postnet-9", "postnet-11", "planet-11", "planet-13", "raw-a", "raw-fnc4-latch",
                    "label-code39-check", "label-code39-guards", "label-full-guards", "label-codabar-check", "label-msi-check", "label-itf-check",
                    "label-above-left", "label-above-center", "label-above-right", "label-rotate", "label-scale", "postal-custom",
                    "guard-ean13", "guard-ean8", "guard-upca", "guard-upce", "bars-only", "label-alternate"}) {
                assertTrue(id, observed.findings.contains(id + ": payload="));
            }
            for (String supplement : new String[] {"00", "01", "02", "03", "05", "51234", "00000", "00001", "00002",
                    "00003", "00004", "00005", "00006", "00007", "00008", "00009"}) {
                assertTrue(supplement, observed.findings.contains("supplement-" + supplement + ": payload=" + supplement));
            }
            for (String mode : new String[] {"ean13", "ean8", "upca", "upce"}) {
                for (String supplement : new String[] {"05", "51234"}) {
                    assertTrue(observed.findings.contains(mode + "-plus-" + supplement + ": payload="));
                }
            }
        }
    }
}
