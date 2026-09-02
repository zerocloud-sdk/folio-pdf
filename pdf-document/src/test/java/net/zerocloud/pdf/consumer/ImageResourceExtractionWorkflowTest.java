package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.DeflaterOutputStream;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentResource;
import net.zerocloud.pdf.DocumentResourceInventory;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.FontResource;
import net.zerocloud.pdf.ImageByteAccess;
import net.zerocloud.pdf.ImageResource;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.ResourceDeclaration;
import net.zerocloud.pdf.ResourceExtractionLimits;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.ExtractImagesAndResources;
import net.zerocloud.pdf.query.PageObjectReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ImageResourceExtractionWorkflowTest {

    private static final String CAPABILITY =
            "document.images-resources.extract";
    private static final String LIMIT_DIAGNOSTIC =
            "The image and resource extraction limit was exceeded.";
    private static final String MALFORMED_DIAGNOSTIC =
            "The document images and resources could not be extracted safely.";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void inheritedAndNestedInventoryIsDeterministicDetachedAndComplete()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("resources.pdf");
        writeCompleteFixture(source);
        byte[] original = Files.readAllBytes(source);

        WorkflowOutcome<DocumentResourceInventory> outcome =
                new DocumentWorkflow().execute(
                        sourceRequest(source),
                        session -> session.query(
                                ExtractImagesAndResources.version1(
                                        generousLimits(),
                                        ImageByteAccess.ENCODED_AND_DECODED)));

        assertEquals(CAPABILITY, outcome.getCapabilityId());
        assertTrue(outcome.getPublicationReceipts().isEmpty());
        assertArrayEquals(original, Files.readAllBytes(source));

        DocumentResourceInventory inventory = outcome.getResult();
        assertEquals(23, inventory.getResources().size());
        assertEquals(4, inventory.getImages().size());
        assertEquals(2, inventory.getFonts().size());
        assertEquals(1, resources(
                inventory, DocumentResource.Kind.FORM).size());
        assertEquals(1, resources(
                inventory, DocumentResource.Kind.PATTERN).size());
        assertEquals(2, resources(
                inventory, DocumentResource.Kind.EXTENDED_GRAPHICS_STATE).size());
        assertEquals(2, resources(
                inventory, DocumentResource.Kind.OTHER).size());
        assertEquals(2, resources(
                inventory, DocumentResource.Kind.COLOR_SPACE).size());
        assertEquals(2, resources(
                inventory, DocumentResource.Kind.SHADING).size());
        assertEquals(2, resources(
                inventory, DocumentResource.Kind.PROPERTIES).size());
        assertEquals(4, resources(
                inventory, DocumentResource.Kind.PROCEDURE_SET).size());
        assertEquals(1, resources(
                inventory, DocumentResource.Kind.XOBJECT_OTHER).size());

        DocumentResource firstResource = inventory.getResources().get(0);
        ResourceDeclaration.Segment firstSegment = firstResource
                .getDeclarations().get(0).getPath().get(0);
        assertEquals(DocumentResource.Kind.COLOR_SPACE,
                firstResource.getKind());
        assertEquals(PdfName.of("ColorSpace"), firstSegment.getCategory());
        assertEquals(PdfName.of("CS1"), firstSegment.getName());
        assertEquals(list(1), firstResource.getPageUsage());
        assertEquals(list(1, 2), resources(
                inventory, DocumentResource.Kind.FORM).get(0).getPageUsage());

        ImageResource nested = inventory.getImages().get(0);
        ImageResource shared = inventory.getImages().get(1);
        ImageResource explicitMask = inventory.getImages().get(2);
        ImageResource softMask = inventory.getImages().get(3);
        assertEquals(1, nested.getWidth());
        assertEquals(ImageResource.ColorFamily.DEVICE_GRAY,
                nested.getColorSpace().getFamily());
        assertEquals(ImageResource.ColorFamily.DEVICE_RGB,
                shared.getColorSpace().getFamily());
        assertEquals(3, shared.getColorComponents().getAsInt());
        assertEquals(8, shared.getBitsPerComponent().getAsInt());
        assertEquals(list(1, 2), shared.getPageUsage());
        assertEquals(2, shared.getDeclarations().size());
        assertTrue(shared.getObjectReference().isPresent());
        assertEquals(ImageResource.EmbeddedSoftMask.NONE,
                shared.getEmbeddedSoftMask());
        assertTrue(shared.getFilters().isEmpty());

        assertEquals(ImageResource.Mask.Kind.EXPLICIT_IMAGE,
                shared.getExplicitMask().get().getKind());
        assertEquals(ImageResource.Mask.Kind.SOFT_IMAGE,
                shared.getSoftMask().get().getKind());
        assertSame(explicitMask,
                shared.getExplicitMask().get().getImage().get());
        assertSame(softMask,
                shared.getSoftMask().get().getImage().get());
        assertTrue(explicitMask.isImageMask());
        assertEquals(1, explicitMask.getBitsPerComponent().getAsInt());
        assertFalse(softMask.isImageMask());
        assertEquals(list(1, 2), explicitMask.getPageUsage());
        assertEquals(list(1, 2), softMask.getPageUsage());

        assertArrayEquals(new byte[] {'a', 'b', 'c'},
                shared.getEncodedData().getBytes().get());
        assertArrayEquals(new byte[] {'a', 'b', 'c'},
                shared.getDecodedData().getBytes().get());
        byte[] defensive = shared.getEncodedData().getBytes().get();
        defensive[0] = 'z';
        assertArrayEquals(new byte[] {'a', 'b', 'c'},
                shared.getEncodedData().getBytes().get());

        FontResource subset = font(inventory, "ABCDEF+Helvetica");
        FontResource embedded = font(inventory, "GHIJKL+FolioSans");
        assertEquals(FontResource.FontKind.TYPE_1, subset.getFontKind());
        assertEquals(FontResource.Embedding.NOT_EMBEDDED,
                subset.getEmbedding());
        assertTrue(subset.isSubset());
        assertEquals("ABCDEF", subset.getSubsetPrefix().get());
        assertEquals(list(1, 2), subset.getPageUsage());
        assertEquals(4, subset.getDeclarations().size());
        assertTrue(subset.getObjectReference().isPresent());
        assertEquals(FontResource.FontKind.TRUE_TYPE, embedded.getFontKind());
        assertEquals(FontResource.Embedding.EMBEDDED,
                embedded.getEmbedding());

        List<DocumentResource> colors = resources(
                inventory, DocumentResource.Kind.COLOR_SPACE);
        assertEquals(2, colors.size());
        assertFalse(colors.get(0).getObjectReference().isPresent());
        assertFalse(colors.get(1).getObjectReference().isPresent());
        assertNotEquals(
                colors.get(0).getDeclarations().get(0),
                colors.get(1).getDeclarations().get(0));
        assertEquals(list(1), colors.get(0).getPageUsage());
        assertEquals(list(2), colors.get(1).getPageUsage());

        ObjectReference sharedReference = shared.getObjectReference().get();
        assertEquals(sharedReference, shared.getObjectReference().get());
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test
    public void repeatedQueriesAndReopenRetainSemanticOrderAndSessionIdentity()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "repeated-resources.pdf");
        writeCompleteFixture(source);
        byte[] original = Files.readAllBytes(source);

        WorkflowOutcome<List<DocumentResourceInventory>> outcome =
                new DocumentWorkflow().execute(
                        sourceRequest(source),
                        session -> {
                            List<DocumentResourceInventory> inventories =
                                    new ArrayList<DocumentResourceInventory>();
                            inventories.add(session.query(
                                    ExtractImagesAndResources.version1(
                                            generousLimits(),
                                            ImageByteAccess.NONE)));
                            inventories.add(session.query(
                                    ExtractImagesAndResources.version1(
                                            generousLimits(),
                                            ImageByteAccess.NONE)));
                            return inventories;
                        });

        DocumentResourceInventory first = outcome.getResult().get(0);
        DocumentResourceInventory repeated = outcome.getResult().get(1);
        assertEquals(inventoryFingerprint(first),
                inventoryFingerprint(repeated));
        assertSameSessionReferences(first, repeated);

        DocumentResourceInventory reopened = query(
                source,
                generousLimits(),
                ImageByteAccess.NONE);
        assertEquals(inventoryFingerprint(first),
                inventoryFingerprint(reopened));
        assertNotEquals(
                first.getImages().get(0).getObjectReference(),
                reopened.getImages().get(0).getObjectReference());
        assertTrue(outcome.getPublicationReceipts().isEmpty());

        Path rewritten = temporaryFolder.getRoot().toPath().resolve(
                "rewritten-resources.pdf");
        WorkflowOutcome<DocumentResourceInventory> published =
                new DocumentWorkflow().execute(
                        WorkflowRequest.builder()
                                .source("input", DocumentSource.path(source))
                                .primarySource("input")
                                .target(
                                        "output",
                                        PublicationTarget.path(rewritten))
                                .saveMode(SaveMode.REWRITE)
                                .build(),
                        session -> session.query(
                                ExtractImagesAndResources.version1(
                                        generousLimits(),
                                        ImageByteAccess.NONE)));
        assertEquals(1, published.getPublicationReceipts().size());
        assertEquals(PublicationStatus.COMMITTED,
                published.getPublicationReceipts().get(0).getStatus());
        assertEquals(
                inventoryFingerprint(published.getResult()),
                inventoryFingerprint(query(
                        rewritten,
                        generousLimits(),
                        ImageByteAccess.NONE)));
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test
    public void nullResourceEntriesAreOmittedWithoutDisturbingSiblingOrder()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "null-resource-entries.pdf");
        writePdf(
                source,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] "
                        + "/Resources << /Font null /ProcSet null "
                        + "/FolioNullCategory null "
                        + "/FolioBag << /Gone null /Kept /Marker >> "
                        + "/XObject << /Gone null /Im 4 0 R >> >> >>",
                streamObject("x", imageEntries()));

        DocumentResourceInventory inventory = query(
                source,
                generousLimits(),
                ImageByteAccess.NONE);
        assertEquals(2, inventory.getResources().size());
        DocumentResource other = inventory.getResources().get(0);
        assertEquals(DocumentResource.Kind.OTHER, other.getKind());
        assertEquals(PdfName.of("FolioBag"), other.getDeclarations().get(0)
                .getPath().get(0).getCategory());
        assertEquals(PdfName.of("Kept"), other.getDeclarations().get(0)
                .getPath().get(0).getName());
        assertEquals(DocumentResource.Kind.IMAGE,
                inventory.getResources().get(1).getKind());
        assertEquals(PdfName.of("Im"), inventory.getResources().get(1)
                .getDeclarations().get(0).getPath().get(0).getName());
        assertEquals(1, inventory.getImages().size());
        assertTrue(inventory.getFonts().isEmpty());
    }

    @Test
    public void queryObservesEarlierPatchAndReturnsDirectDetachedImage()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "patched-in-session.pdf");
        writePdf(
                source,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] "
                        + "/Resources << >> >>");
        byte[] original = Files.readAllBytes(source);
        WorkflowOutcome<DocumentResourceInventory> outcome =
                new DocumentWorkflow().execute(
                        sourceRequest(source),
                        session -> {
                            ObjectReference page = session.query(
                                    PageObjectReference.version1(1));
                            PdfDictionary imageDictionary = PdfDictionary.builder()
                                    .put(PdfName.of("Type"), PdfName.of("XObject"))
                                    .put(PdfName.of("Subtype"), PdfName.of("Image"))
                                    .put(PdfName.of("Width"), PdfNumber.of(1L))
                                    .put(PdfName.of("Height"), PdfNumber.of(1L))
                                    .put(PdfName.of("BitsPerComponent"),
                                            PdfNumber.of(8L))
                                    .put(PdfName.of("ColorSpace"),
                                            PdfName.of("DeviceGray"))
                                    .build();
                            PdfDictionary resources = PdfDictionary.builder()
                                    .put(PdfName.of("XObject"),
                                            PdfDictionary.builder()
                                                    .put(PdfName.of("Im"),
                                                            PdfStream.of(
                                                                    imageDictionary,
                                                                    new byte[] {42}))
                                                    .build())
                                    .build();
                            session.execute(DocumentPatch.builder()
                                    .setDictionaryEntry(
                                            page,
                                            PdfName.of("Resources"),
                                            resources)
                                    .build());
                            return session.query(
                                    ExtractImagesAndResources.version1(
                                            generousLimits(),
                                            ImageByteAccess.DECODED));
                        });

        assertEquals(CAPABILITY, outcome.getCapabilityId());
        assertTrue(outcome.getPublicationReceipts().isEmpty());
        assertArrayEquals(original, Files.readAllBytes(source));
        ImageResource image = outcome.getResult().getImages().get(0);
        assertFalse(image.getObjectReference().isPresent());
        assertFalse(image.getEncodedData().isSelected());
        assertFalse(image.getEncodedData().getBytes().isPresent());
        assertTrue(image.getDecodedData().isSelected());
        assertArrayEquals(new byte[] {42},
                image.getDecodedData().getBytes().get());
    }

    @Test
    public void declaredFilterSequenceAndEffectiveParametersAreReported()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("filters.pdf");
        byte[] compressed = deflate(new byte[] {0, 42});
        String encoded = hex(compressed) + ">";
        writeSingleResourcePdf(
                source,
                "XObject",
                "Im",
                streamObject(
                        encoded,
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 /ColorSpace /DeviceGray "
                                + "/Filter [/ASCIIHexDecode /FlateDecode] "
                                + "/DecodeParms [null << /Predictor 15 /Colors 1 "
                                + "/BitsPerComponent 8 /Columns 1 >>] "));

        ImageResource image = query(
                source,
                generousLimits(),
                ImageByteAccess.DECODED).getImages().get(0);
        assertEquals(2, image.getFilters().size());
        assertEquals(PdfName.of("ASCIIHexDecode"),
                image.getFilters().get(0).getName());
        assertEquals(ImageResource.DecodeSupport.SUPPORTED,
                image.getFilters().get(0).getDecodeSupport());
        ImageResource.Filter flate = image.getFilters().get(1);
        assertEquals(PdfName.of("FlateDecode"), flate.getName());
        assertEquals(15, flate.getPredictor().getAsInt());
        assertEquals(1, flate.getColors().getAsInt());
        assertEquals(8, flate.getBitsPerComponent().getAsInt());
        assertEquals(1, flate.getColumns().getAsInt());
        assertArrayEquals(new byte[] {42},
                image.getDecodedData().getBytes().get());

        Path singleArrayFilter = temporaryFolder.getRoot().toPath().resolve(
                "single-array-filter.pdf");
        writeSingleResourcePdf(
                singleArrayFilter,
                "XObject",
                "Im",
                streamObject(
                        "x",
                        imageEntries() + "/Filter [/FlateDecode] "
                                + "/DecodeParms << /Predictor 1 >> "));
        ImageResource single = query(
                singleArrayFilter,
                generousLimits(),
                ImageByteAccess.NONE).getImages().get(0);
        assertEquals(1, single.getFilters().size());
        assertEquals(PdfName.of("FlateDecode"),
                single.getFilters().get(0).getName());
        assertEquals(1,
                single.getFilters().get(0).getPredictor().getAsInt());

        Path emptyFilters = temporaryFolder.getRoot().toPath().resolve(
                "empty-filter-array.pdf");
        writeSingleResourcePdf(
                emptyFilters,
                "XObject",
                "Im",
                streamObject(
                        "x",
                        imageEntries()
                                + "/Filter [] /DecodeParms [] "));
        ImageResource unfiltered = query(
                emptyFilters,
                generousLimits(),
                ImageByteAccess.DECODED).getImages().get(0);
        assertTrue(unfiltered.getFilters().isEmpty());
        assertArrayEquals(new byte[] {'x'},
                unfiltered.getDecodedData().getBytes().get());
    }

    @Test
    public void nullOptionalDecodeParametersUseTheirEffectiveDefaults()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "null-decode-parameters.pdf");
        writeSingleResourcePdf(
                source,
                "XObject",
                "Im",
                streamObject(
                        "encoded",
                        imageEntries()
                                + "/Filter [/FlateDecode /LZWDecode] "
                                + "/DecodeParms ["
                                + "<< /Predictor null /Colors null "
                                + "/BitsPerComponent null /Columns null >> "
                                + "<< /Predictor null /Colors null "
                                + "/BitsPerComponent null /Columns null "
                                + "/EarlyChange null >>] "));

        ImageResource image = query(
                source,
                generousLimits(),
                ImageByteAccess.NONE).getImages().get(0);
        assertEquals(2, image.getFilters().size());
        ImageResource.Filter flate = image.getFilters().get(0);
        assertEquals(1, flate.getPredictor().getAsInt());
        assertEquals(1, flate.getColors().getAsInt());
        assertEquals(8, flate.getBitsPerComponent().getAsInt());
        assertEquals(1, flate.getColumns().getAsInt());
        assertFalse(flate.getEarlyChange().isPresent());
        ImageResource.Filter lzw = image.getFilters().get(1);
        assertEquals(1, lzw.getPredictor().getAsInt());
        assertEquals(1, lzw.getColors().getAsInt());
        assertEquals(8, lzw.getBitsPerComponent().getAsInt());
        assertEquals(1, lzw.getColumns().getAsInt());
        assertEquals(1, lzw.getEarlyChange().getAsInt());
    }

    @Test
    public void encodedOnlySelectionDoesNotDecodeOrChargeDecodedLimits()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "encoded-only.pdf");
        writeSingleResourcePdf(
                source,
                "XObject",
                "Im",
                streamObject(
                        "G>",
                        imageEntries() + "/Filter /ASCIIHexDecode "));
        ResourceExtractionLimits encodedOnly = ResourceExtractionLimits.builder()
                .maximumPages(1)
                .maximumPageTreeNodes(2)
                .maximumTraversedResourceValues(4)
                .maximumResourceTraversalDepth(1)
                .maximumDecodedPixels(0)
                .maximumDecompressedBytes(0)
                .maximumReturnedBytes(2)
                .build();

        ImageResource image = query(
                source,
                encodedOnly,
                ImageByteAccess.ENCODED).getImages().get(0);
        assertTrue(image.getEncodedData().isSelected());
        assertEquals(ImageResource.ByteAvailability.AVAILABLE,
                image.getEncodedData().getAvailability());
        assertArrayEquals(new byte[] {'G', '>'},
                image.getEncodedData().getBytes().get());
        assertFalse(image.getDecodedData().isSelected());
        assertEquals(ImageResource.ByteAvailability.AVAILABLE,
                image.getDecodedData().getAvailability());
        assertFalse(image.getDecodedData().getBytes().isPresent());
        assertEquals(PdfName.of("ASCIIHexDecode"),
                image.getFilters().get(0).getName());
        assertEquals(ImageResource.DecodeSupport.SUPPORTED,
                image.getFilters().get(0).getDecodeSupport());

        assertMalformed(source, ImageByteAccess.DECODED);
    }

    @Test
    public void xObjectTypeIsOptionalButAConflictingTypeIsMalformed()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "optional-xobject-type.pdf");
        writePdf(
                source,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] "
                        + "/Resources << /XObject << /Fm 4 0 R /Im 5 0 R "
                        + ">> >> >>",
                streamObject(
                        "",
                        "/Subtype /Form /BBox [0 0 1 1] /Resources << >> "),
                streamObject(
                        "x",
                        "/Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 "
                                + "/ColorSpace /DeviceGray "));

        DocumentResourceInventory inventory = query(
                source,
                generousLimits(),
                ImageByteAccess.NONE);
        assertEquals(1, resources(
                inventory, DocumentResource.Kind.FORM).size());
        assertEquals(1, inventory.getImages().size());

        Path conflicting = temporaryFolder.getRoot().toPath().resolve(
                "conflicting-xobject-type.pdf");
        writeSingleResourcePdf(
                conflicting,
                "XObject",
                "Im",
                streamObject(
                        "x",
                        "/Type /Font /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 "
                                + "/ColorSpace /DeviceGray "));
        assertMalformed(conflicting);
    }

    @Test
    public void unsupportedFilterKeepsEncodedBytesAndReportsDecodedAvailability()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("dct.pdf");
        writeSingleResourcePdf(
                source,
                "XObject",
                "Im",
                streamObject(
                        "jpeg",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 /ColorSpace /DeviceRGB "
                                + "/Filter /DCTDecode "));

        ImageResource image = query(
                source,
                generousLimits(),
                ImageByteAccess.ENCODED_AND_DECODED).getImages().get(0);
        assertEquals(ImageResource.DecodeSupport.UNSUPPORTED,
                image.getFilters().get(0).getDecodeSupport());
        assertEquals(ImageResource.ByteAvailability.AVAILABLE,
                image.getEncodedData().getAvailability());
        assertArrayEquals("jpeg".getBytes(StandardCharsets.US_ASCII),
                image.getEncodedData().getBytes().get());
        assertEquals(ImageResource.ByteAvailability.UNSUPPORTED_FILTER,
                image.getDecodedData().getAvailability());
        assertFalse(image.getDecodedData().getBytes().isPresent());

        Path lzwSource = temporaryFolder.getRoot().toPath().resolve("lzw.pdf");
        writeSingleResourcePdf(
                lzwSource,
                "XObject",
                "Im",
                streamObject(
                        "lzw",
                        imageEntries() + "/Filter /LZWDecode "
                                + "/DecodeParms << /Predictor 2 /Colors 1 "
                                + "/BitsPerComponent 8 /Columns 1 "
                                + "/EarlyChange 0 >> "));
        ImageResource lzw = query(
                lzwSource,
                generousLimits(),
                ImageByteAccess.NONE).getImages().get(0);
        assertEquals(ImageResource.DecodeSupport.UNSUPPORTED,
                lzw.getFilters().get(0).getDecodeSupport());
        assertEquals(2, lzw.getFilters().get(0).getPredictor().getAsInt());
        assertEquals(0, lzw.getFilters().get(0).getEarlyChange().getAsInt());

        String[] oneBitFilters = {"CCITTFaxDecode", "JBIG2Decode"};
        for (String filter : oneBitFilters) {
            Path oneBit = temporaryFolder.getRoot().toPath().resolve(
                    "one-bit-" + filter.toLowerCase(Locale.ROOT) + ".pdf");
            writeSingleResourcePdf(
                    oneBit,
                    "XObject",
                    "Im",
                    streamObject(
                            "encoded",
                            "/Type /XObject /Subtype /Image "
                                    + "/Width 1 /Height 1 "
                                    + "/BitsPerComponent 1 "
                                    + "/ColorSpace /DeviceGray /Filter /"
                                    + filter + " "));
            assertEquals(ImageResource.DecodeSupport.UNSUPPORTED, query(
                    oneBit,
                    generousLimits(),
                    ImageByteAccess.NONE).getImages().get(0)
                    .getFilters().get(0).getDecodeSupport());
        }
    }

    @Test
    public void everyVersionOneFilterKindDecodesThroughThePublicQuery()
            throws Exception {
        Path ascii85 = temporaryFolder.getRoot().toPath().resolve(
                "valid-ascii85.pdf");
        writeSingleResourcePdf(
                ascii85,
                "XObject",
                "Im",
                streamObject(
                        ".K~>",
                        imageEntries() + "/Filter /ASCII85Decode "));
        assertArrayEquals(new byte[] {42}, query(
                ascii85,
                generousLimits(),
                ImageByteAccess.DECODED).getImages().get(0)
                .getDecodedData().getBytes().get());

        Path runLength = temporaryFolder.getRoot().toPath().resolve(
                "valid-runlength.pdf");
        writeSingleResourcePdf(
                runLength,
                "XObject",
                "Im",
                streamObject(
                        "002A80>",
                        imageEntries()
                                + "/Filter [/ASCIIHexDecode /RunLengthDecode] "));
        assertArrayEquals(new byte[] {42}, query(
                runLength,
                generousLimits(),
                ImageByteAccess.DECODED).getImages().get(0)
                .getDecodedData().getBytes().get());

        Path predictorRows = temporaryFolder.getRoot().toPath().resolve(
                "valid-predictor-row-overhead.pdf");
        byte[] predicted = deflate(new byte[] {0, 42, 0, 43});
        writeSingleResourcePdf(
                predictorRows,
                "XObject",
                "Im",
                streamObject(
                        hex(predicted) + ">",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 2 "
                                + "/BitsPerComponent 8 "
                                + "/ColorSpace /DeviceGray "
                                + "/Filter [/ASCIIHexDecode /FlateDecode] "
                                + "/DecodeParms [null << /Predictor 15 "
                                + "/Colors 1 /BitsPerComponent 8 "
                                + "/Columns 1 >>] "));
        ResourceExtractionLimits exact = ResourceExtractionLimits.builder()
                .maximumPages(1)
                .maximumPageTreeNodes(2)
                .maximumTraversedResourceValues(32)
                .maximumResourceTraversalDepth(1)
                .maximumDecodedPixels(2)
                .maximumDecompressedBytes(predicted.length + 2L)
                .maximumReturnedBytes(2)
                .build();
        assertArrayEquals(new byte[] {42, 43}, query(
                predictorRows,
                exact,
                ImageByteAccess.DECODED).getImages().get(0)
                .getDecodedData().getBytes().get());
    }

    @Test
    public void colorKeyMaskRetainsExactRangesWithoutFabricatedImageTarget()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "color-key-mask.pdf");
        writeSingleResourcePdf(
                source,
                "XObject",
                "Im",
                streamObject(
                        "rgb",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 /ColorSpace /DeviceRGB "
                                + "/Mask [0 0 10 20 250 255] "));

        ImageResource.Mask mask = query(
                source,
                generousLimits(),
                ImageByteAccess.NONE).getImages().get(0)
                .getExplicitMask().get();
        assertEquals(ImageResource.Mask.Kind.COLOR_KEY, mask.getKind());
        assertFalse(mask.getImage().isPresent());
        assertEquals(java.util.Arrays.asList(
                PdfNumber.of(0L),
                PdfNumber.of(0L),
                PdfNumber.of(10L),
                PdfNumber.of(20L),
                PdfNumber.of(250L),
                PdfNumber.of(255L)), mask.getColorKeyRanges());
    }

    @Test
    public void typeZeroFontUsesDescendantEmbeddingAndSubsetIdentity()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "type-zero-font.pdf");
        writePdf(
                source,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] "
                        + "/Resources << /Font << /F0 4 0 R >> >> >>",
                "<< /Type /Font /Subtype /Type0 "
                        + "/BaseFont /ABCDEF+FolioComposite "
                        + "/Encoding /Identity-H /DescendantFonts [5 0 R] >>",
                "<< /Type /Font /Subtype /CIDFontType2 "
                        + "/BaseFont /ABCDEF+FolioComposite "
                        + "/FontDescriptor 6 0 R >>",
                "<< /Type /FontDescriptor "
                        + "/FontName /ABCDEF+FolioComposite "
                        + "/FontFile2 7 0 R >>",
                streamObject("font", ""));

        FontResource font = query(
                source,
                generousLimits(),
                ImageByteAccess.NONE).getFonts().get(0);
        assertEquals(FontResource.FontKind.TYPE_0, font.getFontKind());
        assertEquals(FontResource.Status.SUPPORTED, font.getStatus());
        assertEquals(FontResource.Embedding.EMBEDDED, font.getEmbedding());
        assertEquals(PdfName.of("ABCDEF+FolioComposite"),
                font.getBaseFontName().get());
        assertEquals("ABCDEF", font.getSubsetPrefix().get());
        assertEquals(list(1), font.getPageUsage());
        assertTrue(font.getObjectReference().isPresent());
    }

    @Test
    public void typeThreeFontRequiresStreamGlyphProgramsAndIsEmbedded()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "type-three-font.pdf");
        writeSingleResourcePdf(
                source,
                "Font",
                "F3",
                "4 0 R",
                "<< /Type /Font /Subtype /Type3 /BaseFont /FolioGlyphs "
                        + "/CharProcs << /A 5 0 R >> >>",
                streamObject("0 0 m", ""));

        FontResource font = query(
                source,
                generousLimits(),
                ImageByteAccess.NONE).getFonts().get(0);
        assertEquals(FontResource.FontKind.TYPE_3, font.getFontKind());
        assertEquals(FontResource.Status.SUPPORTED, font.getStatus());
        assertEquals(FontResource.Embedding.EMBEDDED, font.getEmbedding());
        assertEquals(PdfName.of("FolioGlyphs"),
                font.getBaseFontName().get());
    }

    @Test
    public void malformedColorSpaceIsClassifiedWithoutBackendCoercion()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("color.pdf");
        writeSingleResourcePdf(
                source,
                "XObject",
                "Im",
                streamObject(
                        "x",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 "
                                + "/ColorSpace [/ICCBased << /N 3 >>] "));

        ImageResource image = query(
                source,
                generousLimits(),
                ImageByteAccess.NONE).getImages().get(0);
        assertEquals(ImageResource.ColorStatus.MALFORMED,
                image.getColorSpace().getStatus());
        assertEquals(ImageResource.ColorFamily.UNKNOWN,
                image.getColorSpace().getFamily());
        assertFalse(image.getColorComponents().isPresent());

        String[] deviceNames = {
            "DeviceGray", "DeviceRGB", "DeviceCMYK"
        };
        ImageResource.ColorFamily[] deviceFamilies = {
            ImageResource.ColorFamily.DEVICE_GRAY,
            ImageResource.ColorFamily.DEVICE_RGB,
            ImageResource.ColorFamily.DEVICE_CMYK
        };
        int[] deviceComponents = {1, 3, 4};
        for (int index = 0; index < deviceNames.length; index++) {
            Path deviceArray = temporaryFolder.getRoot().toPath().resolve(
                    "array-" + deviceNames[index].toLowerCase(Locale.ROOT)
                            + ".pdf");
            writeSingleResourcePdf(
                    deviceArray,
                    "XObject",
                    "Im",
                    streamObject(
                            "x",
                            "/Type /XObject /Subtype /Image "
                                    + "/Width 1 /Height 1 "
                                    + "/BitsPerComponent 8 /ColorSpace [/"
                                    + deviceNames[index] + "] "));
            ImageResource deviceImage = query(
                    deviceArray,
                    generousLimits(),
                    ImageByteAccess.NONE).getImages().get(0);
            assertEquals(ImageResource.ColorStatus.SUPPORTED,
                    deviceImage.getColorSpace().getStatus());
            assertEquals(deviceFamilies[index],
                    deviceImage.getColorSpace().getFamily());
            assertEquals(deviceComponents[index],
                    deviceImage.getColorComponents().getAsInt());
        }

        Path malformedDeviceArray = temporaryFolder.getRoot().toPath().resolve(
                "malformed-array-devicegray.pdf");
        writeSingleResourcePdf(
                malformedDeviceArray,
                "XObject",
                "Im",
                streamObject(
                        "x",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 "
                                + "/ColorSpace [/DeviceGray 7] "));
        assertEquals(ImageResource.ColorStatus.MALFORMED, query(
                malformedDeviceArray,
                generousLimits(),
                ImageByteAccess.NONE).getImages().get(0)
                .getColorSpace().getStatus());

        String[] calibratedFamilies = {"CalGray", "CalRGB", "Lab"};
        for (String family : calibratedFamilies) {
            Path malformed = temporaryFolder.getRoot().toPath().resolve(
                    "malformed-" + family.toLowerCase(Locale.ROOT) + ".pdf");
            writeSingleResourcePdf(
                    malformed,
                    "XObject",
                    "Im",
                    streamObject(
                            "x",
                            "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                    + "/BitsPerComponent 8 /ColorSpace [/"
                                    + family + " << >>] "));
            assertEquals(ImageResource.ColorStatus.MALFORMED, query(
                    malformed,
                    generousLimits(),
                    ImageByteAccess.NONE).getImages().get(0)
                    .getColorSpace().getStatus());
        }

        Path calibrated = temporaryFolder.getRoot().toPath().resolve(
                "valid-calrgb.pdf");
        writeSingleResourcePdf(
                calibrated,
                "XObject",
                "Im",
                streamObject(
                        "rgb",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 "
                                + "/ColorSpace [/CalRGB << "
                                + "/WhitePoint [0.9505 1 1.089] "
                                + "/BlackPoint [0 0 0] "
                                + "/Gamma [2.2 2.2 2.2] "
                                + "/Matrix [1 0 0 0 1 0 0 0 1] >>] "));
        ImageResource calibratedImage = query(
                calibrated,
                generousLimits(),
                ImageByteAccess.NONE).getImages().get(0);
        assertEquals(ImageResource.ColorStatus.SUPPORTED,
                calibratedImage.getColorSpace().getStatus());
        assertEquals(ImageResource.ColorFamily.CAL_RGB,
                calibratedImage.getColorSpace().getFamily());
        assertEquals(3, calibratedImage.getColorComponents().getAsInt());

        Path badIccAlternate = temporaryFolder.getRoot().toPath().resolve(
                "malformed-icc-alternate.pdf");
        writeSingleResourcePdf(
                badIccAlternate,
                "XObject",
                "Im",
                "4 0 R",
                streamObject(
                        "rgb",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 "
                                + "/ColorSpace [/ICCBased 5 0 R] "),
                streamObject(
                        "icc",
                        "/N 3 /Alternate /DeviceGray "));
        assertEquals(ImageResource.ColorStatus.MALFORMED, query(
                badIccAlternate,
                generousLimits(),
                ImageByteAccess.NONE).getImages().get(0)
                .getColorSpace().getStatus());

        Path badIccRange = temporaryFolder.getRoot().toPath().resolve(
                "malformed-icc-range.pdf");
        writeSingleResourcePdf(
                badIccRange,
                "XObject",
                "Im",
                "4 0 R",
                streamObject(
                        "rgb",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 "
                                + "/ColorSpace [/ICCBased 5 0 R] "),
                streamObject(
                        "icc",
                        "/N 3 /Range [0 1 0 1] "));
        assertEquals(ImageResource.ColorStatus.MALFORMED, query(
                badIccRange,
                generousLimits(),
                ImageByteAccess.NONE).getImages().get(0)
                .getColorSpace().getStatus());

        Path uncertifiedIcc = temporaryFolder.getRoot().toPath().resolve(
                "uncertified-icc-profile.pdf");
        writeSingleResourcePdf(
                uncertifiedIcc,
                "XObject",
                "Im",
                "4 0 R",
                streamObject(
                        "rgb",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 "
                                + "/ColorSpace [/ICCBased 5 0 R] "),
                streamObject(
                        "not-an-icc-profile",
                        "/N 3 /Alternate /DeviceRGB "
                                + "/Range [0 1 0 1 0 1] "));
        ImageResource iccImage = query(
                uncertifiedIcc,
                generousLimits(),
                ImageByteAccess.NONE).getImages().get(0);
        assertEquals(ImageResource.ColorStatus.UNSUPPORTED,
                iccImage.getColorSpace().getStatus());
        assertEquals(ImageResource.ColorFamily.ICC_BASED,
                iccImage.getColorSpace().getFamily());
        assertEquals(3, iccImage.getColorComponents().getAsInt());

        Path badIndexed = temporaryFolder.getRoot().toPath().resolve(
                "malformed-indexed-lookup.pdf");
        writeSingleResourcePdf(
                badIndexed,
                "XObject",
                "Im",
                streamObject(
                        "x",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 "
                                + "/ColorSpace [/Indexed /DeviceRGB 0 ()] "));
        assertEquals(ImageResource.ColorStatus.MALFORMED, query(
                badIndexed,
                generousLimits(),
                ImageByteAccess.NONE).getImages().get(0)
                .getColorSpace().getStatus());

        Path indexed = temporaryFolder.getRoot().toPath().resolve(
                "valid-indexed-lookup.pdf");
        writeSingleResourcePdf(
                indexed,
                "XObject",
                "Im",
                streamObject(
                        "x",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 "
                                + "/ColorSpace [/Indexed /DeviceRGB 0 (rgb)] "));
        ImageResource indexedImage = query(
                indexed,
                generousLimits(),
                ImageByteAccess.NONE).getImages().get(0);
        assertEquals(ImageResource.ColorStatus.SUPPORTED,
                indexedImage.getColorSpace().getStatus());
        assertEquals(ImageResource.ColorFamily.INDEXED,
                indexedImage.getColorSpace().getFamily());
        assertEquals(1, indexedImage.getColorComponents().getAsInt());

        Path indexedStream = temporaryFolder.getRoot().toPath().resolve(
                "indexed-stream-lookup.pdf");
        writeSingleResourcePdf(
                indexedStream,
                "XObject",
                "Im",
                "4 0 R",
                streamObject(
                        "x",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 /ColorSpace "
                                + "[/Indexed /DeviceRGB 0 5 0 R] "),
                streamObject("rgb", ""));
        ImageResource indexedStreamImage = query(
                indexedStream,
                generousLimits(),
                ImageByteAccess.NONE).getImages().get(0);
        assertEquals(ImageResource.ColorStatus.UNSUPPORTED,
                indexedStreamImage.getColorSpace().getStatus());
        assertEquals(ImageResource.ColorFamily.INDEXED,
                indexedStreamImage.getColorSpace().getFamily());

        String[] malformedTintSpaces = {
            "[/Separation /Spot /DeviceGray <<>>]",
            "[/DeviceN [/Cyan /Spot] /DeviceCMYK <<>>]"
        };
        for (int index = 0; index < malformedTintSpaces.length; index++) {
            Path malformed = temporaryFolder.getRoot().toPath().resolve(
                    "malformed-tint-function-" + index + ".pdf");
            writeSingleResourcePdf(
                    malformed,
                    "XObject",
                    "Im",
                    streamObject(
                            "x",
                            "/Type /XObject /Subtype /Image "
                                    + "/Width 1 /Height 1 "
                                    + "/BitsPerComponent 8 /ColorSpace "
                                    + malformedTintSpaces[index] + " "));
            assertEquals(ImageResource.ColorStatus.MALFORMED, query(
                    malformed,
                    generousLimits(),
                    ImageByteAccess.NONE).getImages().get(0)
                    .getColorSpace().getStatus());
        }

        String tintFunction = "<< /FunctionType 2 /Domain [0 1] "
                + "/C0 [0] /C1 [1] /N 1 >>";
        String separationAlternate = "[/Separation /Spot /DeviceGray "
                + tintFunction + "]";
        String[] malformedSpecialColorSpaces = {
            "[/Separation /Spot [/Indexed /DeviceRGB 0 (rgb)] "
                    + tintFunction + "]",
            "[/DeviceN [/Cyan] " + separationAlternate + " "
                    + tintFunction + "]",
            "[/DeviceN [/Cyan] /DeviceGray " + tintFunction
                    + " << /Subtype /Bogus >>]",
            "[/DeviceN [/Cyan /Cyan] /DeviceGray "
                    + tintFunction + "]",
            "[/DeviceN [/All] /DeviceGray " + tintFunction + "]",
            "[/DeviceN [/None] /DeviceGray " + tintFunction
                    + " << /Subtype /NChannel >>]"
        };
        for (int index = 0;
                index < malformedSpecialColorSpaces.length;
                index++) {
            Path malformed = temporaryFolder.getRoot().toPath().resolve(
                    "malformed-special-color-" + index + ".pdf");
            writeSingleResourcePdf(
                    malformed,
                    "XObject",
                    "Im",
                    streamObject(
                            "x",
                            "/Type /XObject /Subtype /Image "
                                    + "/Width 1 /Height 1 "
                                    + "/BitsPerComponent 8 /ColorSpace "
                                    + malformedSpecialColorSpaces[index] + " "));
            assertEquals(ImageResource.ColorStatus.MALFORMED, query(
                    malformed,
                    generousLimits(),
                    ImageByteAccess.NONE).getImages().get(0)
                    .getColorSpace().getStatus());
        }

        Path indexedSpecialBase = temporaryFolder.getRoot().toPath().resolve(
                "indexed-separation-base.pdf");
        writeSingleResourcePdf(
                indexedSpecialBase,
                "XObject",
                "Im",
                streamObject(
                        "x",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 /ColorSpace [/Indexed "
                                + separationAlternate + " 0 (x)] "));
        ImageResource indexedSpecialImage = query(
                indexedSpecialBase,
                generousLimits(),
                ImageByteAccess.NONE).getImages().get(0);
        assertEquals(ImageResource.ColorStatus.UNSUPPORTED,
                indexedSpecialImage.getColorSpace().getStatus());
        assertEquals(ImageResource.ColorFamily.INDEXED,
                indexedSpecialImage.getColorSpace().getFamily());

        Path separation = temporaryFolder.getRoot().toPath().resolve(
                "bounded-separation.pdf");
        writeSingleResourcePdf(
                separation,
                "XObject",
                "Im",
                streamObject(
                        "x",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 /ColorSpace "
                                + "[/Separation /Spot /DeviceGray "
                                + "<< /FunctionType 2 /Domain [0 1] "
                                + "/C0 [0] /C1 [1] /N 1 >>] "));
        ImageResource separationImage = query(
                separation,
                generousLimits(),
                ImageByteAccess.NONE).getImages().get(0);
        assertEquals(ImageResource.ColorStatus.UNSUPPORTED,
                separationImage.getColorSpace().getStatus());
        assertEquals(ImageResource.ColorFamily.SEPARATION,
                separationImage.getColorSpace().getFamily());
        assertEquals(1, separationImage.getColorComponents().getAsInt());

        Path unsupported = temporaryFolder.getRoot().toPath().resolve(
                "unsupported-color.pdf");
        writeSingleResourcePdf(
                unsupported,
                "XObject",
                "Im",
                streamObject(
                        "x",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 "
                                + "/ColorSpace [/FolioColor << >>] "));
        ImageResource unknown = query(
                unsupported,
                generousLimits(),
                ImageByteAccess.NONE).getImages().get(0);
        assertEquals(ImageResource.ColorStatus.UNSUPPORTED,
                unknown.getColorSpace().getStatus());
        assertEquals(ImageResource.ColorFamily.UNKNOWN,
                unknown.getColorSpace().getFamily());

        Path inlineIndexedAlias = temporaryFolder.getRoot().toPath().resolve(
                "inline-indexed-alias.pdf");
        writeSingleResourcePdf(
                inlineIndexedAlias,
                "XObject",
                "Im",
                streamObject(
                        "x",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 "
                                + "/ColorSpace [/I /DeviceRGB 0 (rgb)] "));
        ImageResource inlineAlias = query(
                inlineIndexedAlias,
                generousLimits(),
                ImageByteAccess.NONE).getImages().get(0);
        assertEquals(ImageResource.ColorStatus.MALFORMED,
                inlineAlias.getColorSpace().getStatus());
        assertEquals(ImageResource.ColorFamily.UNKNOWN,
                inlineAlias.getColorSpace().getFamily());

        String[] imagePatternSpaces = {
            "/Pattern",
            "[/Pattern /DeviceRGB]"
        };
        for (int index = 0; index < imagePatternSpaces.length; index++) {
            Path pattern = temporaryFolder.getRoot().toPath().resolve(
                    "image-pattern-color-" + index + ".pdf");
            writeSingleResourcePdf(
                    pattern,
                    "XObject",
                    "Im",
                    streamObject(
                            "x",
                            "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                    + "/BitsPerComponent 8 /ColorSpace "
                                    + imagePatternSpaces[index] + " "));
            ImageResource patternImage = query(
                    pattern,
                    generousLimits(),
                    ImageByteAccess.NONE).getImages().get(0);
            assertEquals(ImageResource.ColorStatus.MALFORMED,
                    patternImage.getColorSpace().getStatus());
            assertEquals(ImageResource.ColorFamily.UNKNOWN,
                    patternImage.getColorSpace().getFamily());
        }

        Path malformedTint = temporaryFolder.getRoot().toPath().resolve(
                "malformed-tint-color.pdf");
        writeSingleResourcePdf(
                malformedTint,
                "XObject",
                "Im",
                streamObject(
                        "x",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 "
                                + "/ColorSpace [/Separation /Spot "
                                + "/DeviceGray 7] "));
        assertEquals(ImageResource.ColorStatus.MALFORMED, query(
                malformedTint,
                generousLimits(),
                ImageByteAccess.NONE).getImages().get(0)
                .getColorSpace().getStatus());
    }

    @Test
    public void malformedKnownFilterDataFailsBeforePublishingPartialBytes()
            throws Exception {
        byte[] compressed = deflate(new byte[] {42});
        byte[] truncated = java.util.Arrays.copyOf(
                compressed,
                compressed.length - 1);
        Path flate = temporaryFolder.getRoot().toPath().resolve(
                "truncated-flate.pdf");
        writeSingleResourcePdf(
                flate,
                "XObject",
                "Im",
                streamObject(
                        hex(truncated) + ">",
                        imageEntries()
                                + "/Filter [/ASCIIHexDecode /FlateDecode] "
                                + "/DecodeParms [null << /Predictor 1 >>] "));

        Path asciiHex = temporaryFolder.getRoot().toPath().resolve(
                "bad-asciihex.pdf");
        writeSingleResourcePdf(
                asciiHex,
                "XObject",
                "Im",
                streamObject("G>", imageEntries() + "/Filter /ASCIIHexDecode "));

        Path ascii85 = temporaryFolder.getRoot().toPath().resolve(
                "bad-ascii85.pdf");
        writeSingleResourcePdf(
                ascii85,
                "XObject",
                "Im",
                streamObject("~x", imageEntries() + "/Filter /ASCII85Decode "));

        Path runLength = temporaryFolder.getRoot().toPath().resolve(
                "bad-runlength.pdf");
        writeSingleResourcePdf(
                runLength,
                "XObject",
                "Im",
                streamObject("\u0000x", imageEntries() + "/Filter /RunLengthDecode "));

        Path predictor = temporaryFolder.getRoot().toPath().resolve(
                "bad-predictor.pdf");
        writeSingleResourcePdf(
                predictor,
                "XObject",
                "Im",
                streamObject(
                        hex(deflate(new byte[] {5, 42})) + ">",
                        imageEntries()
                                + "/Filter [/ASCIIHexDecode /FlateDecode] "
                                + "/DecodeParms [null << /Predictor 15 "
                                + "/Colors 1 /BitsPerComponent 8 "
                                + "/Columns 1 >>] "));

        assertMalformed(flate, ImageByteAccess.DECODED);
        assertMalformed(asciiHex, ImageByteAccess.DECODED);
        assertMalformed(ascii85, ImageByteAccess.DECODED);
        assertMalformed(runLength, ImageByteAccess.DECODED);
        assertMalformed(predictor, ImageByteAccess.DECODED);
    }

    @Test
    public void malformedQueryLeavesAnExplicitTargetNotAttempted()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "malformed-with-target.pdf");
        Path target = temporaryFolder.getRoot().toPath().resolve(
                "must-not-be-published.pdf");
        writeSingleResourcePdf(
                source,
                "XObject",
                "Im",
                streamObject(
                        "G>",
                        imageEntries() + "/Filter /ASCIIHexDecode "));
        byte[] original = Files.readAllBytes(source);

        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source("input", DocumentSource.path(source))
                            .primarySource("input")
                            .target("output", PublicationTarget.path(target))
                            .saveMode(SaveMode.REWRITE)
                            .build(),
                    session -> session.query(
                            ExtractImagesAndResources.version1(
                                    generousLimits(),
                                    ImageByteAccess.DECODED)));
            fail("Expected malformed resource extraction to fail");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.QUERY_FAILED, failure.getCode());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(MALFORMED_DIAGNOSTIC, failure.getDiagnostic());
            assertEquals(1, failure.getPublicationReceipts().size());
            assertEquals(PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
        }
        assertFalse(Files.exists(target));
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test
    public void decompressionCountsEveryFilterStageAtTheExactBoundary()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "decompression-boundary.pdf");
        byte[] compressed = deflate(new byte[] {42});
        writeSingleResourcePdf(
                source,
                "XObject",
                "Im",
                streamObject(
                        hex(compressed) + ">",
                        imageEntries()
                                + "/Filter [/ASCIIHexDecode /FlateDecode] "
                                + "/DecodeParms [null << /Predictor 1 >>] "));

        long exact = compressed.length + 1L;
        ResourceExtractionLimits exactLimits = ResourceExtractionLimits.builder()
                .maximumPages(1)
                .maximumPageTreeNodes(2)
                .maximumTraversedResourceValues(64)
                .maximumResourceTraversalDepth(1)
                .maximumDecodedPixels(1)
                .maximumDecompressedBytes(exact)
                .maximumReturnedBytes(1)
                .build();
        assertArrayEquals(new byte[] {42}, query(
                source,
                exactLimits,
                ImageByteAccess.DECODED).getImages().get(0)
                .getDecodedData().getBytes().get());

        assertLimit(
                source,
                ResourceExtractionLimits.builder()
                        .maximumPages(1)
                        .maximumPageTreeNodes(2)
                        .maximumTraversedResourceValues(64)
                        .maximumResourceTraversalDepth(1)
                        .maximumDecodedPixels(1)
                        .maximumDecompressedBytes(exact - 1L)
                        .maximumReturnedBytes(1)
                        .build(),
                ImageByteAccess.DECODED);
    }

    @Test
    public void decodedSizeOverflowFailsBeforeStreamMaterialization()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "pixel-overflow.pdf");
        writeSingleResourcePdf(
                source,
                "XObject",
                "Im",
                streamObject(
                        "x",
                        "/Type /XObject /Subtype /Image "
                                + "/Width 2147483647 /Height 2147483647 "
                                + "/BitsPerComponent 16 "
                                + "/ColorSpace /DeviceCMYK "));
        assertLimit(
                source,
                ResourceExtractionLimits.builder()
                        .maximumPages(1)
                        .maximumPageTreeNodes(2)
                        .maximumTraversedResourceValues(3)
                        .maximumResourceTraversalDepth(1)
                        .maximumDecodedPixels(Long.MAX_VALUE)
                        .maximumDecompressedBytes(Long.MAX_VALUE)
                        .maximumReturnedBytes(Long.MAX_VALUE)
                        .build(),
                ImageByteAccess.DECODED);
    }

    @Test
    public void externalImageStreamIsInventoriedWithoutResolvingItsLocation()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("external.pdf");
        writeSingleResourcePdf(
                source,
                "XObject",
                "Im",
                streamObject(
                        "",
                        imageEntries() + "/F (must-not-be-opened.bin) "));
        ImageResource image = query(
                source,
                generousLimits(),
                ImageByteAccess.ENCODED_AND_DECODED).getImages().get(0);
        assertEquals(ImageResource.ByteAvailability.EXTERNAL_STREAM,
                image.getEncodedData().getAvailability());
        assertEquals(ImageResource.ByteAvailability.EXTERNAL_STREAM,
                image.getDecodedData().getAvailability());
        assertFalse(image.getEncodedData().getBytes().isPresent());
        assertFalse(image.getDecodedData().getBytes().isPresent());

        Path dictionary = temporaryFolder.getRoot().toPath().resolve(
                "external-filespec.pdf");
        writeSingleResourcePdf(
                dictionary,
                "XObject",
                "Im",
                streamObject(
                        "",
                        imageEntries()
                                + "/F << /Type /Filespec "
                                + "/F (must-not-be-opened.bin) >> "));
        ImageResource dictionaryImage = query(
                dictionary,
                generousLimits(),
                ImageByteAccess.ENCODED_AND_DECODED).getImages().get(0);
        assertEquals(ImageResource.ByteAvailability.EXTERNAL_STREAM,
                dictionaryImage.getEncodedData().getAvailability());
        assertEquals(ImageResource.ByteAvailability.EXTERNAL_STREAM,
                dictionaryImage.getDecodedData().getAvailability());

        Path emptyExternalFilters = temporaryFolder.getRoot().toPath().resolve(
                "external-empty-filter-array.pdf");
        writeSingleResourcePdf(
                emptyExternalFilters,
                "XObject",
                "Im",
                streamObject(
                        "",
                        imageEntries() + "/F (must-not-be-opened.bin) "
                                + "/FFilter [] /FDecodeParms [] "));
        ImageResource emptyExternal = query(
                emptyExternalFilters,
                generousLimits(),
                ImageByteAccess.ENCODED_AND_DECODED).getImages().get(0);
        assertTrue(emptyExternal.getFilters().isEmpty());
        assertEquals(ImageResource.ByteAvailability.EXTERNAL_STREAM,
                emptyExternal.getEncodedData().getAvailability());
        assertEquals(ImageResource.ByteAvailability.EXTERNAL_STREAM,
                emptyExternal.getDecodedData().getAvailability());

        Path directNull = temporaryFolder.getRoot().toPath().resolve(
                "external-direct-null.pdf");
        writeSingleResourcePdf(
                directNull,
                "XObject",
                "Im",
                streamObject(
                        "2A>",
                        imageEntries()
                                + "/F null /Filter /ASCIIHexDecode "));
        ImageResource directNullImage = query(
                directNull,
                generousLimits(),
                ImageByteAccess.DECODED).getImages().get(0);
        assertEquals(ImageResource.ByteAvailability.AVAILABLE,
                directNullImage.getDecodedData().getAvailability());
        assertArrayEquals(new byte[] {42},
                directNullImage.getDecodedData().getBytes().get());
        assertEquals(PdfName.of("ASCIIHexDecode"),
                directNullImage.getFilters().get(0).getName());

        Path indirectNull = temporaryFolder.getRoot().toPath().resolve(
                "external-indirect-null.pdf");
        writeSingleResourcePdf(
                indirectNull,
                "XObject",
                "Im",
                "4 0 R",
                streamObject(
                        "2A>",
                        imageEntries()
                                + "/F 5 0 R /Filter /ASCIIHexDecode "),
                "null");
        ImageResource indirectNullImage = query(
                indirectNull,
                generousLimits(),
                ImageByteAccess.DECODED).getImages().get(0);
        assertEquals(ImageResource.ByteAvailability.AVAILABLE,
                indirectNullImage.getDecodedData().getAvailability());
        assertArrayEquals(new byte[] {42},
                indirectNullImage.getDecodedData().getBytes().get());
    }

    @Test
    public void exactSimpleImageLimitsSucceedAndFirstExcessFails()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("bounded.pdf");
        writeSingleResourcePdf(
                source,
                "XObject",
                "Im",
                streamObject(
                        "x",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 /ColorSpace /DeviceGray "));

        DocumentResourceInventory exact = query(
                source,
                boundaryLimits().build(),
                ImageByteAccess.ENCODED_AND_DECODED);
        assertEquals(1, exact.getImages().size());
        assertArrayEquals(new byte[] {'x'},
                exact.getImages().get(0).getDecodedData().getBytes().get());

        assertLimit(source, boundaryLimits().pages(0).build(),
                ImageByteAccess.NONE);
        assertLimit(source, boundaryLimits().pageTreeNodes(1).build(),
                ImageByteAccess.NONE);
        assertLimit(source, boundaryLimits().values(2).build(),
                ImageByteAccess.NONE);
        assertLimit(source, boundaryLimits().depth(0).build(),
                ImageByteAccess.NONE);
        assertLimit(source, boundaryLimits().pixels(0).build(),
                ImageByteAccess.DECODED);
        assertLimit(source, boundaryLimits().decompressed(0).build(),
                ImageByteAccess.DECODED);
        assertLimit(source, boundaryLimits().returned(1).build(),
                ImageByteAccess.ENCODED_AND_DECODED);

        Path font = temporaryFolder.getRoot().toPath().resolve(
                "font-depth.pdf");
        writeSingleResourcePdf(
                font,
                "Font",
                "F1",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        assertLimit(font, boundaryLimits().depth(0).build(),
                ImageByteAccess.NONE);

        DocumentResourceInventory metadataOnly = query(
                source,
                boundaryLimits().pixels(0).decompressed(0).returned(0).build(),
                ImageByteAccess.NONE);
        assertEquals(1, metadataOnly.getImages().size());

        Path unknownComponents = temporaryFolder.getRoot().toPath().resolve(
                "unknown-components.pdf");
        writeSingleResourcePdf(
                unknownComponents,
                "XObject",
                "Im",
                streamObject(
                        "x",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 "
                                + "/ColorSpace [/FolioColor << >>] "));
        assertArrayEquals(new byte[] {'x'}, query(
                unknownComponents,
                boundaryLimits().values(5).returned(1).build(),
                ImageByteAccess.DECODED).getImages().get(0)
                .getDecodedData().getBytes().get());
        assertLimit(
                unknownComponents,
                boundaryLimits().values(5).returned(0).build(),
                ImageByteAccess.DECODED);
    }

    @Test
    public void everyLimitIsMandatoryAndDepthHasAHardVersionOneCeiling() {
        try {
            ResourceExtractionLimits.builder()
                    .maximumPages(1)
                    .maximumPageTreeNodes(2)
                    .maximumTraversedResourceValues(3)
                    .maximumResourceTraversalDepth(1)
                    .maximumDecodedPixels(1)
                    .maximumDecompressedBytes(1)
                    .build();
            fail("Expected an omitted returned-byte limit to fail");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Every version-1"));
        }

        try {
            ResourceExtractionLimits.builder()
                    .maximumResourceTraversalDepth(
                            ResourceExtractionLimits
                                    .MAXIMUM_RESOURCE_TRAVERSAL_DEPTH_VERSION_1
                                    + 1);
            fail("Expected the version-1 depth ceiling to fail");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("must not exceed"));
        }

        ResourceExtractionLimits maximum = ResourceExtractionLimits.builder()
                .maximumPages(0)
                .maximumPageTreeNodes(0)
                .maximumTraversedResourceValues(0)
                .maximumResourceTraversalDepth(
                        ResourceExtractionLimits
                                .MAXIMUM_RESOURCE_TRAVERSAL_DEPTH_VERSION_1)
                .maximumDecodedPixels(0)
                .maximumDecompressedBytes(0)
                .maximumReturnedBytes(0)
                .build();
        assertEquals(ResourceExtractionLimits.VERSION_1,
                maximum.getVersion());
        assertEquals(
                ResourceExtractionLimits
                        .MAXIMUM_RESOURCE_TRAVERSAL_DEPTH_VERSION_1,
                maximum.getMaximumResourceTraversalDepth());
    }

    @Test
    public void nestedFormDepthAcceptsBoundaryAndRejectsFirstExcess()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("depth.pdf");
        writePdf(
                source,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] "
                        + "/Resources << /XObject << /Fm 4 0 R >> >> >>",
                streamObject(
                        "",
                        "/Type /XObject /Subtype /Form /BBox [0 0 1 1] "
                                + "/Resources << /XObject << /Im 5 0 R >> >> "),
                streamObject(
                        "x",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 /ColorSpace /DeviceGray "));

        assertEquals(1, query(
                source,
                ResourceExtractionLimits.builder()
                        .maximumPages(1)
                        .maximumPageTreeNodes(2)
                        .maximumTraversedResourceValues(5)
                        .maximumResourceTraversalDepth(2)
                        .maximumDecodedPixels(0)
                        .maximumDecompressedBytes(0)
                        .maximumReturnedBytes(0)
                        .build(),
                ImageByteAccess.NONE).getImages().size());
        assertLimit(
                source,
                ResourceExtractionLimits.builder()
                        .maximumPages(1)
                        .maximumPageTreeNodes(2)
                        .maximumTraversedResourceValues(5)
                        .maximumResourceTraversalDepth(1)
                        .maximumDecodedPixels(0)
                        .maximumDecompressedBytes(0)
                        .maximumReturnedBytes(0)
                        .build(),
                ImageByteAccess.NONE);
    }

    @Test
    public void repeatedAcyclicFormGraphIsRevisitedAndChargedDeterministically()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "repeated-form-dag.pdf");
        writePdf(
                source,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] "
                        + "/Resources << /XObject << /B 4 0 R /A 4 0 R >> >> >>",
                streamObject(
                        "",
                        "/Type /XObject /Subtype /Form /BBox [0 0 1 1] "
                                + "/Resources << /XObject << /Im 5 0 R >> >> "),
                streamObject(
                        "x",
                        imageEntries()));

        ResourceExtractionLimits exact = ResourceExtractionLimits.builder()
                .maximumPages(1)
                .maximumPageTreeNodes(2)
                .maximumTraversedResourceValues(9)
                .maximumResourceTraversalDepth(2)
                .maximumDecodedPixels(0)
                .maximumDecompressedBytes(0)
                .maximumReturnedBytes(0)
                .build();
        DocumentResourceInventory inventory = query(
                source,
                exact,
                ImageByteAccess.NONE);
        DocumentResource form = resources(
                inventory, DocumentResource.Kind.FORM).get(0);
        ImageResource image = inventory.getImages().get(0);
        assertEquals(2, form.getDeclarations().size());
        assertEquals(2, image.getDeclarations().size());
        assertEquals(PdfName.of("A"), form.getDeclarations().get(0)
                .getPath().get(0).getName());
        assertEquals(PdfName.of("B"), form.getDeclarations().get(1)
                .getPath().get(0).getName());

        ResourceExtractionLimits firstExcess = ResourceExtractionLimits.builder()
                .maximumPages(1)
                .maximumPageTreeNodes(2)
                .maximumTraversedResourceValues(8)
                .maximumResourceTraversalDepth(2)
                .maximumDecodedPixels(0)
                .maximumDecompressedBytes(0)
                .maximumReturnedBytes(0)
                .build();
        for (int attempt = 0; attempt < 3; attempt++) {
            assertLimit(source, firstExcess, ImageByteAccess.NONE);
        }
    }

    @Test
    public void sharedType3FontIsRevalidatedAndChargedOnEveryDeclaration()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "shared-type3-font-dag.pdf");
        writePdf(
                source,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] "
                        + "/Resources << /XObject << /B 4 0 R /A 4 0 R >> >> >>",
                streamObject(
                        "",
                        "/Type /XObject /Subtype /Form /BBox [0 0 1 1] "
                                + "/Resources << /Font << /F1 5 0 R >> >> "),
                "<< /Type /Font /Subtype /Type3 /BaseFont /FolioType3 "
                        + "/CharProcs << /g 6 0 R >> >>",
                streamObject("", ""));

        ResourceExtractionLimits exact = ResourceExtractionLimits.builder()
                .maximumPages(1)
                .maximumPageTreeNodes(2)
                .maximumTraversedResourceValues(9)
                .maximumResourceTraversalDepth(2)
                .maximumDecodedPixels(0)
                .maximumDecompressedBytes(0)
                .maximumReturnedBytes(0)
                .build();
        DocumentResourceInventory inventory = query(
                source,
                exact,
                ImageByteAccess.NONE);
        assertEquals(1, inventory.getFonts().size());
        FontResource font = inventory.getFonts().get(0);
        assertEquals(2, font.getDeclarations().size());
        assertEquals(PdfName.of("A"), font.getDeclarations().get(0)
                .getPath().get(0).getName());
        assertEquals(PdfName.of("B"), font.getDeclarations().get(1)
                .getPath().get(0).getName());

        ResourceExtractionLimits firstExcess = ResourceExtractionLimits.builder()
                .maximumPages(1)
                .maximumPageTreeNodes(2)
                .maximumTraversedResourceValues(8)
                .maximumResourceTraversalDepth(2)
                .maximumDecodedPixels(0)
                .maximumDecompressedBytes(0)
                .maximumReturnedBytes(0)
                .build();
        for (int attempt = 0; attempt < 3; attempt++) {
            assertLimit(source, firstExcess, ImageByteAccess.NONE);
        }
    }

    @Test
    public void cyclicFormsAndMasksFailWithFixedSafeDiagnostic()
            throws Exception {
        Path form = temporaryFolder.getRoot().toPath().resolve("form-cycle.pdf");
        writePdf(
                form,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] "
                        + "/Resources << /XObject << /Fm 4 0 R >> >> >>",
                streamObject(
                        "",
                        "/Type /XObject /Subtype /Form /BBox [0 0 1 1] "
                                + "/Resources << /XObject << /Self 4 0 R >> >> "));
        for (int attempt = 0; attempt < 3; attempt++) {
            assertMalformed(form);
        }

        Path mask = temporaryFolder.getRoot().toPath().resolve("mask-cycle.pdf");
        writeSingleResourcePdf(
                mask,
                "XObject",
                "Im",
                "4 0 R",
                streamObject(
                        "x",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 /ColorSpace /DeviceGray "
                                + "/SMask 4 0 R "));
        for (int attempt = 0; attempt < 3; attempt++) {
            assertMalformed(mask);
        }
    }

    @Test
    public void softMaskDictionaryRestrictionsAreValidatedStably()
            throws Exception {
        Path valid = temporaryFolder.getRoot().toPath().resolve(
                "valid-soft-mask-dictionary.pdf");
        writeSingleResourcePdf(
                valid,
                "XObject",
                "Im",
                "4 0 R",
                streamObject("x", imageEntries() + "/SMask 5 0 R "),
                streamObject(
                        "s",
                        imageEntries() + "/Decode [0 1] "
                                + "/Interpolate true /Matte [0.5] "));
        assertTrue(query(
                valid,
                generousLimits(),
                ImageByteAccess.NONE).getImages().get(0)
                .getSoftMask().isPresent());

        ImageResource.EmbeddedSoftMask[] embeddedStates = {
            ImageResource.EmbeddedSoftMask.NONE,
            ImageResource.EmbeddedSoftMask.SOFT_MASK,
            ImageResource.EmbeddedSoftMask.PREBLENDED_SOFT_MASK
        };
        for (int code = 0; code <= 2; code++) {
            Path embedded = singleImage(
                    "jpx-embedded-soft-mask-" + code,
                    "/Width 1 /Height 1 /BitsPerComponent 8 "
                            + "/ColorSpace /DeviceGray /Filter /JPXDecode "
                            + "/SMaskInData " + code + " ");
            ImageResource embeddedImage = query(
                    embedded,
                    generousLimits(),
                    ImageByteAccess.NONE).getImages().get(0);
            assertEquals(embeddedStates[code],
                    embeddedImage.getEmbeddedSoftMask());
            assertFalse(embeddedImage.getSoftMask().isPresent());
        }

        Path ignoredNonJpx = singleImage(
                "non-jpx-smask-in-data-is-ignored",
                "/Width 1 /Height 1 /BitsPerComponent 8 "
                        + "/ColorSpace /DeviceGray /SMaskInData 2 ");
        assertEquals(ImageResource.EmbeddedSoftMask.NONE, query(
                ignoredNonJpx,
                generousLimits(),
                ImageByteAccess.NONE).getImages().get(0)
                .getEmbeddedSoftMask());

        List<Path> fixtures = new ArrayList<Path>();
        fixtures.add(singleImage(
                "soft-mask-none",
                "/Width 1 /Height 1 /BitsPerComponent 8 "
                        + "/ColorSpace /DeviceGray /SMask /None "));
        fixtures.add(singleImage(
                "jpx-embedded-soft-mask-wrong-kind",
                "/Width 1 /Height 1 /BitsPerComponent 8 "
                        + "/ColorSpace /DeviceGray /Filter /JPXDecode "
                        + "/SMaskInData /One "));
        fixtures.add(singleImage(
                "jpx-embedded-soft-mask-out-of-range",
                "/Width 1 /Height 1 /BitsPerComponent 8 "
                        + "/ColorSpace /DeviceGray /Filter /JPXDecode "
                        + "/SMaskInData 3 "));
        fixtures.add(singleImage(
                "image-mask-with-embedded-soft-mask",
                "/Width 1 /Height 1 /ImageMask true "
                        + "/Filter /JPXDecode /SMaskInData 1 "));

        Path conflictingEmbedded = temporaryFolder.getRoot().toPath().resolve(
                "jpx-conflicting-soft-masks.pdf");
        writeSingleResourcePdf(
                conflictingEmbedded,
                "XObject",
                "Im",
                "4 0 R",
                streamObject(
                        "jpx",
                        imageEntries() + "/Filter /JPXDecode "
                                + "/SMaskInData 1 /SMask 5 0 R "),
                streamObject("s", imageEntries()));
        fixtures.add(conflictingEmbedded);

        Path missingBits = temporaryFolder.getRoot().toPath().resolve(
                "soft-mask-missing-bits.pdf");
        writeSingleResourcePdf(
                missingBits,
                "XObject",
                "Im",
                "4 0 R",
                streamObject("x", imageEntries() + "/SMask 5 0 R "),
                streamObject(
                        "jpx",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/ColorSpace /DeviceGray /Filter /JPXDecode "));
        fixtures.add(missingBits);

        Path nestedMask = temporaryFolder.getRoot().toPath().resolve(
                "soft-mask-with-mask.pdf");
        writeSingleResourcePdf(
                nestedMask,
                "XObject",
                "Im",
                "4 0 R",
                streamObject("x", imageEntries() + "/SMask 5 0 R "),
                streamObject(
                        "s",
                        imageEntries() + "/Mask [0 0] "));
        fixtures.add(nestedMask);

        Path malformedDecode = temporaryFolder.getRoot().toPath().resolve(
                "soft-mask-bad-decode.pdf");
        writeSingleResourcePdf(
                malformedDecode,
                "XObject",
                "Im",
                "4 0 R",
                streamObject("x", imageEntries() + "/SMask 5 0 R "),
                streamObject(
                        "s",
                        imageEntries() + "/Decode [0] "));
        fixtures.add(malformedDecode);

        Path malformedInterpolate = temporaryFolder.getRoot().toPath().resolve(
                "soft-mask-bad-interpolate.pdf");
        writeSingleResourcePdf(
                malformedInterpolate,
                "XObject",
                "Im",
                "4 0 R",
                streamObject("x", imageEntries() + "/SMask 5 0 R "),
                streamObject(
                        "s",
                        imageEntries() + "/Interpolate 7 "));
        fixtures.add(malformedInterpolate);

        Path malformedMatteShape = temporaryFolder.getRoot().toPath().resolve(
                "soft-mask-bad-matte-shape.pdf");
        writeSingleResourcePdf(
                malformedMatteShape,
                "XObject",
                "Im",
                "4 0 R",
                streamObject(
                        "rgb",
                        "/Type /XObject /Subtype /Image "
                                + "/Width 1 /Height 1 /BitsPerComponent 8 "
                                + "/ColorSpace /DeviceRGB /SMask 5 0 R "),
                streamObject(
                        "s",
                        imageEntries() + "/Matte [0] "));
        fixtures.add(malformedMatteShape);

        Path malformedMatteRange = temporaryFolder.getRoot().toPath().resolve(
                "soft-mask-bad-matte-range.pdf");
        writeSingleResourcePdf(
                malformedMatteRange,
                "XObject",
                "Im",
                "4 0 R",
                streamObject("x", imageEntries() + "/SMask 5 0 R "),
                streamObject(
                        "s",
                        imageEntries() + "/Matte [2] "));
        fixtures.add(malformedMatteRange);

        Path malformedMatteDimensions = temporaryFolder.getRoot().toPath()
                .resolve("soft-mask-bad-matte-dimensions.pdf");
        writeSingleResourcePdf(
                malformedMatteDimensions,
                "XObject",
                "Im",
                "4 0 R",
                streamObject("x", imageEntries() + "/SMask 5 0 R "),
                streamObject(
                        "ss",
                        "/Type /XObject /Subtype /Image "
                                + "/Width 2 /Height 1 /BitsPerComponent 8 "
                                + "/ColorSpace /DeviceGray /Matte [0.5] "));
        fixtures.add(malformedMatteDimensions);

        Path nestedSoftMask = temporaryFolder.getRoot().toPath().resolve(
                "soft-mask-with-soft-mask.pdf");
        writeSingleResourcePdf(
                nestedSoftMask,
                "XObject",
                "Im",
                "4 0 R",
                streamObject("x", imageEntries() + "/SMask 5 0 R "),
                streamObject("s", imageEntries() + "/SMask 6 0 R "),
                streamObject("n", imageEntries()));
        fixtures.add(nestedSoftMask);

        for (Path fixture : fixtures) {
            for (int attempt = 0; attempt < 3; attempt++) {
                assertMalformed(fixture);
            }
        }
    }

    @Test
    public void malformedDimensionsFiltersMasksFontsAndCategoriesFailSafely()
            throws Exception {
        List<Path> fixtures = new ArrayList<Path>();
        fixtures.add(singleImage("bad-width", "/Width 0 /Height 1 "
                + "/BitsPerComponent 8 /ColorSpace /DeviceGray "));
        fixtures.add(singleImage("bad-filter", "/Width 1 /Height 1 "
                + "/BitsPerComponent 8 /ColorSpace /DeviceGray "
                + "/Filter /FolioUnknownDecode "));
        fixtures.add(singleImage("inline-filter-alias", "/Width 1 /Height 1 "
                + "/BitsPerComponent 8 /ColorSpace /DeviceGray "
                + "/Filter /AHx "));
        fixtures.add(singleImage("dct-bad-bits", "/Width 1 /Height 1 "
                + "/BitsPerComponent 16 /ColorSpace /DeviceGray "
                + "/Filter /DCTDecode "));
        fixtures.add(singleImage("ccitt-bad-bits", "/Width 1 /Height 1 "
                + "/BitsPerComponent 8 /ColorSpace /DeviceGray "
                + "/Filter /CCITTFaxDecode "));
        fixtures.add(singleImage("jbig2-bad-bits", "/Width 1 /Height 1 "
                + "/BitsPerComponent 8 /ColorSpace /DeviceGray "
                + "/Filter /JBIG2Decode "));
        fixtures.add(singleImage("runlength-bad-bits", "/Width 1 /Height 1 "
                + "/BitsPerComponent 4 /ColorSpace /DeviceGray "
                + "/Filter /RunLengthDecode "));
        fixtures.add(singleImage("predictor-bad-bits", "/Width 1 /Height 1 "
                + "/BitsPerComponent 4 /ColorSpace /DeviceGray "
                + "/Filter /FlateDecode /DecodeParms << /Predictor 2 "
                + "/Colors 1 /BitsPerComponent 8 /Columns 1 >> "));
        fixtures.add(singleImage("predictor-bad-components", "/Width 1 /Height 1 "
                + "/BitsPerComponent 8 /ColorSpace /DeviceRGB "
                + "/Filter /FlateDecode /DecodeParms << /Predictor 2 "
                + "/Colors 1 /BitsPerComponent 8 /Columns 1 >> "));
        fixtures.add(singleImage("predictor-bad-columns", "/Width 2 /Height 1 "
                + "/BitsPerComponent 8 /ColorSpace /DeviceGray "
                + "/Filter /FlateDecode /DecodeParms << /Predictor 2 "
                + "/Colors 1 /BitsPerComponent 8 /Columns 1 >> "));
        fixtures.add(singleImage("single-filter-decodeparms-array", "/Width 1 "
                + "/Height 1 /BitsPerComponent 8 /ColorSpace /DeviceGray "
                + "/Filter [/FlateDecode] /DecodeParms [<< >>] "));
        fixtures.add(singleImage("bad-external-file", "/Width 1 /Height 1 "
                + "/BitsPerComponent 8 /ColorSpace /DeviceGray /F 7 "));
        fixtures.add(singleImage("bad-external-filespec-type", "/Width 1 /Height 1 "
                + "/BitsPerComponent 8 /ColorSpace /DeviceGray "
                + "/F << /Type /Font >> "));
        fixtures.add(singleImage("empty-external-filespec", "/Width 1 /Height 1 "
                + "/BitsPerComponent 8 /ColorSpace /DeviceGray /F << >> "));
        fixtures.add(singleImage("bad-mask", "/Width 1 /Height 1 "
                + "/BitsPerComponent 8 /ColorSpace /DeviceGray /Mask 4 "));
        fixtures.add(singleImage("bad-color-key", "/Width 1 /Height 1 "
                + "/BitsPerComponent 8 /ColorSpace /DeviceGray "
                + "/Mask [2 1] "));

        Path explicitMask = temporaryFolder.getRoot().toPath().resolve(
                "bad-explicit-mask.pdf");
        writeSingleResourcePdf(
                explicitMask,
                "XObject",
                "Im",
                "4 0 R",
                streamObject(
                        "x",
                        imageEntries() + "/Mask 5 0 R "),
                streamObject(
                        "m",
                        imageEntries()));
        fixtures.add(explicitMask);

        Path softMask = temporaryFolder.getRoot().toPath().resolve(
                "bad-soft-mask.pdf");
        writeSingleResourcePdf(
                softMask,
                "XObject",
                "Im",
                "4 0 R",
                streamObject(
                        "x",
                        imageEntries() + "/SMask 5 0 R "),
                streamObject(
                        "rgb",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 "
                                + "/ColorSpace /DeviceRGB "));
        fixtures.add(softMask);

        String[] directCidSubtypes = {"CIDFontType0", "CIDFontType2"};
        for (String subtype : directCidSubtypes) {
            Path directCid = temporaryFolder.getRoot().toPath().resolve(
                    "direct-" + subtype.toLowerCase(Locale.ROOT) + ".pdf");
            writeSingleResourcePdf(
                    directCid,
                    "Font",
                    "F1",
                    "<< /Type /Font /Subtype /" + subtype
                            + " /BaseFont /FolioCid >>");
            fixtures.add(directCid);
        }

        Path font = temporaryFolder.getRoot().toPath().resolve("bad-font.pdf");
        writeSingleResourcePdf(
                font,
                "Font",
                "F1",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                        + "/FontDescriptor 7 >>");
        fixtures.add(font);

        Path typeZero = temporaryFolder.getRoot().toPath().resolve(
                "bad-type-zero-subset.pdf");
        writeSingleResourcePdf(
                typeZero,
                "Font",
                "F0",
                "4 0 R",
                "<< /Type /Font /Subtype /Type0 "
                        + "/BaseFont /ABCDEF+FolioComposite "
                        + "/DescendantFonts [5 0 R] >>",
                "<< /Type /Font /Subtype /CIDFontType2 "
                        + "/BaseFont /FolioComposite >>");
        fixtures.add(typeZero);

        Path typeThree = temporaryFolder.getRoot().toPath().resolve(
                "bad-type-three-glyph.pdf");
        writeSingleResourcePdf(
                typeThree,
                "Font",
                "F3",
                "4 0 R",
                "<< /Type /Font /Subtype /Type3 /BaseFont /FolioGlyphs "
                        + "/CharProcs << /A 5 0 R >> >>",
                "<< /Length 0 >>");
        fixtures.add(typeThree);

        Path category = temporaryFolder.getRoot().toPath().resolve(
                "bad-category.pdf");
        writePdf(
                category,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] "
                        + "/Resources << /XObject [] >> >>");
        fixtures.add(category);

        for (Path fixture : fixtures) {
            assertMalformed(fixture);
        }
    }

    private Path singleImage(String name, String entries) throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve(name + ".pdf");
        writeSingleResourcePdf(
                target,
                "XObject",
                "Im",
                streamObject(
                        "x",
                        "/Type /XObject /Subtype /Image " + entries));
        return target;
    }

    private static DocumentResourceInventory query(
            Path source,
            ResourceExtractionLimits limits,
            ImageByteAccess access) throws Exception {
        return new DocumentWorkflow().execute(
                sourceRequest(source),
                session -> session.query(
                        ExtractImagesAndResources.version1(limits, access)))
                .getResult();
    }

    private static void assertLimit(
            Path source,
            ResourceExtractionLimits limits,
            ImageByteAccess access) throws Exception {
        byte[] original = Files.readAllBytes(source);
        try {
            query(source, limits, access);
            fail("Expected the resource extraction limit to fail");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED,
                    failure.getCode());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(LIMIT_DIAGNOSTIC, failure.getDiagnostic());
            assertTrue(failure.getPublicationReceipts().isEmpty());
        }
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    private static void assertMalformed(Path source) throws Exception {
        assertMalformed(source, ImageByteAccess.NONE);
    }

    private static void assertMalformed(
            Path source,
            ImageByteAccess access) throws Exception {
        byte[] original = Files.readAllBytes(source);
        try {
            query(source, generousLimits(), access);
            fail("Expected malformed resource extraction to fail");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.QUERY_FAILED, failure.getCode());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(MALFORMED_DIAGNOSTIC, failure.getDiagnostic());
            assertTrue(failure.getPublicationReceipts().isEmpty());
        }
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    private static String imageEntries() {
        return "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                + "/BitsPerComponent 8 /ColorSpace /DeviceGray ";
    }

    private static FontResource font(
            DocumentResourceInventory inventory,
            String baseName) {
        for (FontResource font : inventory.getFonts()) {
            if (font.getBaseFontName().isPresent()
                    && baseName.equals(font.getBaseFontName().get().getValue())) {
                return font;
            }
        }
        throw new AssertionError("Missing Font resource " + baseName);
    }

    private static List<DocumentResource> resources(
            DocumentResourceInventory inventory,
            DocumentResource.Kind kind) {
        List<DocumentResource> result = new ArrayList<DocumentResource>();
        for (DocumentResource resource : inventory.getResources()) {
            if (resource.getKind() == kind) {
                result.add(resource);
            }
        }
        return result;
    }

    private static String inventoryFingerprint(
            DocumentResourceInventory inventory) {
        StringBuilder result = new StringBuilder();
        for (DocumentResource resource : inventory.getResources()) {
            result.append(resource.getKind()).append(':')
                    .append(resource.getObjectReference().isPresent())
                    .append(':').append(resource.getPageUsage()).append(':');
            for (ResourceDeclaration declaration : resource.getDeclarations()) {
                result.append(declaration.getPageNumber()).append('[');
                for (ResourceDeclaration.Segment segment
                        : declaration.getPath()) {
                    result.append(segment.getCategory().getValue())
                            .append('/')
                            .append(segment.getName().getValue())
                            .append(';');
                }
                result.append(']');
            }
            if (resource instanceof ImageResource) {
                ImageResource image = (ImageResource) resource;
                result.append(':').append(image.getWidth())
                        .append('x').append(image.getHeight())
                        .append(':').append(image.getColorSpace().getStatus())
                        .append(':').append(image.getColorSpace().getFamily())
                        .append(':').append(image.isImageMask())
                        .append(':').append(image.getEmbeddedSoftMask());
                for (ImageResource.Filter filter : image.getFilters()) {
                    result.append(':').append(filter.getName().getValue())
                            .append('/').append(filter.getDecodeSupport());
                }
                if (image.getExplicitMask().isPresent()) {
                    result.append(":mask=")
                            .append(image.getExplicitMask().get().getKind());
                }
                if (image.getSoftMask().isPresent()) {
                    result.append(":smask=")
                            .append(image.getSoftMask().get().getKind());
                }
            } else if (resource instanceof FontResource) {
                FontResource font = (FontResource) resource;
                result.append(':').append(font.getFontKind())
                        .append(':').append(font.getStatus())
                        .append(':').append(font.getEmbedding())
                        .append(':').append(font.getBaseFontName().isPresent()
                                ? font.getBaseFontName().get().getValue()
                                : "-")
                        .append(':').append(font.getSubsetPrefix().orElse("-"));
            }
            result.append('\n');
        }
        return result.toString();
    }

    private static void assertSameSessionReferences(
            DocumentResourceInventory first,
            DocumentResourceInventory second) {
        assertEquals(first.getResources().size(), second.getResources().size());
        for (int index = 0; index < first.getResources().size(); index++) {
            assertEquals(
                    first.getResources().get(index).getObjectReference(),
                    second.getResources().get(index).getObjectReference());
        }
    }

    private static List<Integer> list(int... values) {
        List<Integer> result = new ArrayList<Integer>(values.length);
        for (int value : values) {
            result.add(Integer.valueOf(value));
        }
        return result;
    }

    private static WorkflowRequest sourceRequest(Path source) {
        return WorkflowRequest.builder()
                .source("input", DocumentSource.path(source))
                .primarySource("input")
                .saveMode(SaveMode.REWRITE)
                .build();
    }

    private static ResourceExtractionLimits generousLimits() {
        return ResourceExtractionLimits.builder()
                .maximumPages(16)
                .maximumPageTreeNodes(128)
                .maximumTraversedResourceValues(10_000)
                .maximumResourceTraversalDepth(32)
                .maximumDecodedPixels(1_000_000L)
                .maximumDecompressedBytes(16L * 1024L * 1024L)
                .maximumReturnedBytes(16L * 1024L * 1024L)
                .build();
    }

    private static BoundaryLimits boundaryLimits() {
        return new BoundaryLimits();
    }

    private static void writeCompleteFixture(Path target) throws Exception {
        writePdf(
                target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 2 /Kids [3 0 R 4 0 R] "
                        + "/Resources 5 0 R >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] >>",
                "<< /ColorSpace << /CS1 /DeviceRGB >> "
                        + "/ExtGState << /GS1 << >> >> "
                        + "/FolioUnknown << /U1 (kept) >> "
                        + "/Font << /F1 8 0 R /F2 13 0 R >> "
                        + "/Pattern << /P1 9 0 R >> "
                        + "/ProcSet [/PDF /ImageC] "
                        + "/Properties << /MC1 << /Lang (en) >> >> "
                        + "/Shading << /Sh1 << /ShadingType 2 >> >> "
                        + "/XObject << /Other 16 0 R /ImShared 7 0 R "
                        + "/Fm 6 0 R >> >>",
                streamObject(
                        "",
                        "/Type /XObject /Subtype /Form /BBox [0 0 1 1] "
                                + "/Resources << /Font << /F1 8 0 R >> "
                                + "/XObject << /Nested 10 0 R >> >> "),
                streamObject(
                        "abc",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 /ColorSpace /CS1 "
                                + "/Mask 11 0 R /SMask 12 0 R "),
                "<< /Type /Font /Subtype /Type1 /BaseFont /ABCDEF+Helvetica >>",
                "<< /Type /Pattern /PatternType 1 >>",
                streamObject(
                        "n",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 /ColorSpace /DeviceGray "),
                streamObject(
                        "m",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/ImageMask true /BitsPerComponent 1 "),
                streamObject(
                        "s",
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 /ColorSpace /DeviceGray "),
                "<< /Type /Font /Subtype /TrueType /BaseFont /GHIJKL+FolioSans "
                        + "/FontDescriptor 14 0 R >>",
                "<< /Type /FontDescriptor /FontName /GHIJKL+FolioSans "
                        + "/FontFile2 15 0 R >>",
                streamObject("font", ""),
                streamObject("postscript", "/Type /XObject /Subtype /PS "));
    }

    private static void writeSingleResourcePdf(
            Path target,
            String category,
            String name,
            String resource) throws Exception {
        writePdf(
                target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] "
                        + "/Resources << /" + category + " << /" + name
                        + " 4 0 R >> >> >>",
                resource);
    }

    private static void writeSingleResourcePdf(
            Path target,
            String category,
            String name,
            String resourceReference,
            String... additionalObjects) throws Exception {
        List<String> objects = new ArrayList<String>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Count 1 /Kids [3 0 R] >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] "
                + "/Resources << /" + category + " << /" + name + " "
                + resourceReference + " >> >> >>");
        Collections.addAll(objects, additionalObjects);
        writePdf(target, objects.toArray(new String[objects.size()]));
    }

    private static String streamObject(String data, String entries) {
        return "<< " + entries + "/Length "
                + data.getBytes(StandardCharsets.US_ASCII).length
                + " >>\nstream\n" + data + "\nendstream";
    }

    private static byte[] deflate(byte[] value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(output)) {
            deflater.write(value);
        }
        return output.toByteArray();
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte current : value) {
            result.append(String.format(
                    Locale.ROOT,
                    "%02X",
                    Integer.valueOf(current & 0xff)));
        }
        return result.toString();
    }

    private static void writePdf(Path target, String... objectBodies)
            throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII));
        int[] offsets = new int[objectBodies.length + 1];
        for (int index = 0; index < objectBodies.length; index++) {
            offsets[index + 1] = output.size();
            output.write(((index + 1) + " 0 obj\n")
                    .getBytes(StandardCharsets.US_ASCII));
            output.write(objectBodies[index]
                    .getBytes(StandardCharsets.US_ASCII));
            output.write("\nendobj\n".getBytes(StandardCharsets.US_ASCII));
        }
        int xref = output.size();
        output.write(("xref\n0 " + (objectBodies.length + 1) + "\n")
                .getBytes(StandardCharsets.US_ASCII));
        output.write("0000000000 65535 f \n"
                .getBytes(StandardCharsets.US_ASCII));
        for (int index = 1; index < offsets.length; index++) {
            output.write(String.format(
                    Locale.ROOT,
                    "%010d 00000 n \n",
                    Integer.valueOf(offsets[index]))
                    .getBytes(StandardCharsets.US_ASCII));
        }
        output.write(("trailer\n<< /Size " + offsets.length
                + " /Root 1 0 R >>\nstartxref\n" + xref
                + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII));
        Files.write(target, output.toByteArray());
    }

    private static final class BoundaryLimits {

        private int pages = 1;
        private int pageTreeNodes = 2;
        private long values = 3L;
        private int depth = 1;
        private long pixels = 1L;
        private long decompressed = 1L;
        private long returned = 2L;

        BoundaryLimits pages(int value) { pages = value; return this; }
        BoundaryLimits pageTreeNodes(int value) {
            pageTreeNodes = value;
            return this;
        }
        BoundaryLimits values(long value) { values = value; return this; }
        BoundaryLimits depth(int value) { depth = value; return this; }
        BoundaryLimits pixels(long value) { pixels = value; return this; }
        BoundaryLimits decompressed(long value) {
            decompressed = value;
            return this;
        }
        BoundaryLimits returned(long value) { returned = value; return this; }

        ResourceExtractionLimits build() {
            return ResourceExtractionLimits.builder()
                    .maximumPages(pages)
                    .maximumPageTreeNodes(pageTreeNodes)
                    .maximumTraversedResourceValues(values)
                    .maximumResourceTraversalDepth(depth)
                    .maximumDecodedPixels(pixels)
                    .maximumDecompressedBytes(decompressed)
                    .maximumReturnedBytes(returned)
                    .build();
        }
    }
}
