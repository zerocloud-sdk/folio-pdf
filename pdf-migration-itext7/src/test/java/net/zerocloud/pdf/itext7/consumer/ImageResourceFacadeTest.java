package net.zerocloud.pdf.itext7.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentResource;
import net.zerocloud.pdf.DocumentResourceInventory;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.FontResource;
import net.zerocloud.pdf.ImageByteAccess;
import net.zerocloud.pdf.ImageResource;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.ResourceExtractionLimits;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDictionary;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfName;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfNumber;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfPage;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfStream;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;
import net.zerocloud.pdf.query.ExtractImagesAndResources;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ImageResourceFacadeTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void completeInventoryMatchesNativeAndSurvivesDocumentClosure() throws Exception {
        Path source = fixture("detached.pdf");
        byte[] original = Files.readAllBytes(source);
        DocumentResourceInventory detached;
        PdfDocument document = new PdfDocument(new PdfReader(source.toString()));
        try {
            detached = document.getImagesAndResources(limits(), ImageByteAccess.ENCODED_AND_DECODED);
        } finally {
            document.close();
        }
        assertTrue(document.getPublicationReceipts().isEmpty());
        DocumentResourceInventory nativeResult = nativeInventory(source);
        assertInventoryEquals(nativeResult, detached);
        assertEquals(6, detached.getResources().size());
        assertEquals(2, detached.getImages().size());
        ImageResource image = detached.getImages().get(0);
        assertEquals(Arrays.asList(1, 2), image.getPageUsage());
        assertEquals(4, image.getDeclarations().size());
        assertTrue(image.getObjectReference().isPresent());
        assertEquals(ImageResource.ColorFamily.DEVICE_RGB, image.getColorSpace().getFamily());
        assertEquals(3, image.getColorComponents().getAsInt());
        assertSame(detached.getImages().get(1), image.getSoftMask().get().getImage().get());
        byte[] copy = image.getDecodedData().getBytes().get();
        copy[0] = 0;
        assertArrayEquals(new byte[] {65, 66, 67}, image.getDecodedData().getBytes().get());
        assertEquals("ABCDEF", detached.getFonts().get(0).getSubsetPrefix().get());
        assertEquals(FontResource.Embedding.NOT_EMBEDDED, detached.getFonts().get(0).getEmbedding());
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test
    public void pageResourcesIncludeUnpaintedDeclarationsAndSeparateDirectOccurrences() throws Exception {
        Path source = fixture("declarations.pdf");
        List<DocumentResource> first;
        List<DocumentResource> second;
        try (PdfDocument document = new PdfDocument(new PdfReader(source.toString()))) {
            first = document.getPage(1).getResources();
            second = document.getPage(2).getResources();
        }
        assertEquals(5, first.size());
        assertEquals(5, second.size());
        assertEquals(DocumentResource.Kind.EXTENDED_GRAPHICS_STATE, first.get(0).getKind());
        assertFalse(first.get(0).getObjectReference().isPresent());
        assertEquals(Arrays.asList(1), first.get(0).getPageUsage());
        assertEquals(Arrays.asList(2), second.get(4).getPageUsage());
        ImageResource image = (ImageResource) first.get(3);
        assertEquals(Arrays.asList(1, 2), image.getPageUsage());
        assertEquals(ImageResource.ByteAvailability.AVAILABLE, image.getDecodedData().getAvailability());
        assertFalse(image.getEncodedData().getBytes().isPresent());
        assertFalse(image.getDecodedData().getBytes().isPresent());
        try {
            first.clear();
            fail("Detached page resources must be immutable");
        } catch (UnsupportedOperationException expected) {
            assertEquals(5, first.size());
        }
    }

    @Test
    public void explicitByteSelectionAndWholeDocumentLimitsApplyBeforePageSelection() throws Exception {
        Path source = fixture("bounds.pdf");
        try (PdfDocument document = new PdfDocument(new PdfReader(source.toString()))) {
            PdfPage page = document.getPage(1);
            assertEquals(5, page.getResources(limitsBuilder().maximumPages(2)
                    .maximumReturnedBytes(8).build(), ImageByteAccess.ENCODED_AND_DECODED).size());
            expectFailure(DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED,
                    () -> page.getResources(limitsBuilder().maximumPages(1).build(), ImageByteAccess.NONE));
            expectFailure(DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED,
                    () -> page.getResources(limitsBuilder().maximumReturnedBytes(7).build(),
                            ImageByteAccess.ENCODED_AND_DECODED));
            ImageResource image = document.getImagesAndResources(limitsBuilder()
                    .maximumDecodedPixels(0).maximumDecompressedBytes(0).maximumReturnedBytes(4).build(),
                    ImageByteAccess.ENCODED).getImages().get(0);
            assertTrue(image.getEncodedData().getBytes().isPresent());
            assertFalse(image.getDecodedData().getBytes().isPresent());
            assertEquals(2, document.getImagesAndResources(limits(), ImageByteAccess.DECODED).getImages().size());
        }
    }

    @Test
    public void earlierPageCommandsAndPatchesAreVisibleAndPublishedWithReceipts() throws Exception {
        Path source = fixture("ordered.pdf");
        Path target = temporary.getRoot().toPath().resolve("published.pdf");
        byte[] original = Files.readAllBytes(source);
        PdfDocument document = new PdfDocument(new PdfReader(source.toString()), new PdfWriter(target.toString()));
        DocumentResourceInventory detached;
        try {
            PdfPage retained = document.getPage(2);
            document.removePage(1);
            assertEquals(Arrays.asList(1), retained.getResources().get(0).getPageUsage());
            PdfStream image = new PdfStream(new byte[] {42});
            image.put(new PdfName("Type"), new PdfName("XObject"));
            image.put(new PdfName("Subtype"), new PdfName("Image"));
            image.put(new PdfName("Width"), new PdfNumber(1));
            image.put(new PdfName("Height"), new PdfNumber(1));
            image.put(new PdfName("BitsPerComponent"), new PdfNumber(8));
            image.put(new PdfName("ColorSpace"), new PdfName("DeviceGray"));
            PdfDictionary xobjects = new PdfDictionary();
            xobjects.put(new PdfName("Changed"), image);
            PdfDictionary resources = new PdfDictionary();
            resources.put(new PdfName("XObject"), xobjects);
            retained.getPdfObject().put(new PdfName("Resources"), resources);
            detached = document.getImagesAndResources(limits(), ImageByteAccess.ENCODED_AND_DECODED);
            assertEquals(1, detached.getResources().size());
            assertArrayEquals(new byte[] {42}, detached.getImages().get(0).getDecodedData().getBytes().get());
        } finally {
            document.close();
        }
        assertEquals(1, document.getPublicationReceipts().size());
        assertEquals(PublicationStatus.COMMITTED, document.getPublicationReceipts().get(0).getStatus());
        assertEquals(target, document.getPublicationReceipts().get(0).getPathTarget().get());
        assertInventoryEquals(detached, nativeInventory(target));
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test
    public void callerStreamsRemainOpenAndReturnedBytesRemainDefensive() throws Exception {
        Path source = fixture("streams.pdf");
        TrackingInput input = new TrackingInput(Files.readAllBytes(source));
        TrackingOutput output = new TrackingOutput();
        DocumentResourceInventory detached;
        PdfDocument document = new PdfDocument(new PdfReader(input), new PdfWriter(output));
        try {
            detached = document.getImagesAndResources(limits(), ImageByteAccess.ENCODED_AND_DECODED);
        } finally {
            document.close();
        }
        assertFalse(input.closed);
        assertFalse(output.closed);
        assertEquals(PublicationStatus.COMMITTED, document.getPublicationReceipts().get(0).getStatus());
        byte[] copy = detached.getImages().get(0).getEncodedData().getBytes().get();
        copy[0] = 0;
        assertArrayEquals(new byte[] {65, 66, 67}, detached.getImages().get(0).getEncodedData().getBytes().get());
        try (PdfDocument reopened = new PdfDocument(new PdfReader(new ByteArrayInputStream(output.toByteArray())))) {
            assertInventoryEquals(detached, reopened.getImagesAndResources(limits(), ImageByteAccess.ENCODED_AND_DECODED));
        }
    }

    @Test
    public void closedHandlesAndNullSelectionsRejectWithoutPublication() throws Exception {
        Path source = fixture("closed.pdf");
        PdfDocument document = new PdfDocument(new PdfReader(source.toString()));
        PdfPage page = document.getPage(1);
        try {
            try {
                document.getImagesAndResources(limits(), null);
                fail("Null byte selection must reject");
            } catch (NullPointerException expected) {
                assertEquals(2, document.getNumberOfPages());
            }
            try {
                page.getResources(null, ImageByteAccess.NONE);
                fail("Null limits must reject");
            } catch (NullPointerException expected) {
                assertEquals(2, document.getNumberOfPages());
            }
        } finally {
            document.close();
        }
        for (Runnable access : Arrays.<Runnable>asList(
                () -> document.getImagesAndResources(limits(), ImageByteAccess.NONE),
                () -> page.getResources(),
                () -> page.getResources(limits(), ImageByteAccess.NONE))) {
            try {
                access.run();
                fail("Closed handles must reject");
            } catch (IllegalStateException expected) {
                assertTrue(document.getPublicationReceipts().isEmpty());
            }
        }
    }

    @Test
    public void realEncodedFormatsAndExternalMetadataMatchNativeAvailability() throws Exception {
        Path root = java.nio.file.Paths.get(System.getProperty("repositoryRoot", ".."));
        for (String name : new String[] {"formats", "classifications"}) {
            Path source = root.resolve("capabilities/profiles/T14-images/" + name + ".pdf");
            byte[] original = Files.readAllBytes(source);
            ImageByteAccess access = "classifications".equals(name) ? ImageByteAccess.ENCODED
                    : ImageByteAccess.ENCODED_AND_DECODED;
            DocumentResourceInventory nativeResult = new DocumentWorkflow().execute(WorkflowRequest.open(source, SaveMode.REWRITE),
                    session -> session.query(ExtractImagesAndResources.version1(limits(), access))).getResult();
            PdfDocument document = new PdfDocument(new PdfReader(source.toString()));
            DocumentResourceInventory detached;
            try {
                detached = document.getImagesAndResources(limits(), access);
                assertEquals(detached.getResources().size(), document.getPage(1).getResources(limits(), access).size());
            } finally {
                document.close();
            }
            assertInventoryEquals(nativeResult, detached);
            assertTrue(document.getPublicationReceipts().isEmpty());
            assertArrayEquals(original, Files.readAllBytes(source));
            if ("formats".equals(name)) {
                assertEquals(5, detached.getImages().size());
                for (ImageResource image : detached.getImages()) {
                    assertTrue(image.getEncodedData().getBytes().get().length > 0);
                    assertEquals(ImageResource.ByteAvailability.UNSUPPORTED_FILTER, image.getDecodedData().getAvailability());
                    assertFalse(image.getDecodedData().getBytes().isPresent());
                }
            } else {
                ImageResource external = detached.getImages().get(4);
                assertEquals(ImageResource.ByteAvailability.EXTERNAL_STREAM, external.getEncodedData().getAvailability());
                assertFalse(external.getEncodedData().getBytes().isPresent());
                assertEquals(ImageResource.ColorStatus.MALFORMED, detached.getImages().get(0).getColorSpace().getStatus());
            }
        }
    }

    @Test
    public void malformedInventoriesReturnSafeFailuresWithoutAResultOrPublication() throws Exception {
        Path root = java.nio.file.Paths.get(System.getProperty("repositoryRoot", ".."));
        for (String name : new String[] {"width-zero", "soft-mask-cycle", "font-program-stream"}) {
            Path source = root.resolve("capabilities/profiles/T14-standards/fixtures/" + name + ".pdf");
            byte[] original = Files.readAllBytes(source);
            PdfDocument document = new PdfDocument(new PdfReader(source.toString()));
            try {
                try {
                    document.getImagesAndResources(limits(), ImageByteAccess.ENCODED_AND_DECODED);
                    fail("A malformed inventory must fail as a whole");
                } catch (PdfException failure) {
                    assertTrue(failure.getCause() instanceof DocumentFailure);
                    DocumentFailure nativeFailure = (DocumentFailure) failure.getCause();
                    assertEquals(DocumentFailureCode.QUERY_FAILED, nativeFailure.getCode());
                    assertEquals("document.images-resources.extract", nativeFailure.getCapabilityId());
                    assertEquals("The document images and resources could not be extracted safely.", nativeFailure.getDiagnostic());
                    assertTrue(nativeFailure.getPublicationReceipts().isEmpty());
                }
            } finally {
                document.close();
            }
            assertTrue(document.getPublicationReceipts().isEmpty());
            assertArrayEquals(original, Files.readAllBytes(source));
        }
    }

    private static void assertInventoryEquals(DocumentResourceInventory expected, DocumentResourceInventory actual) {
        assertEquals(expected.getResources().size(), actual.getResources().size());
        for (int index = 0; index < expected.getResources().size(); index++) {
            DocumentResource left = expected.getResources().get(index);
            DocumentResource right = actual.getResources().get(index);
            assertEquals(left.getKind(), right.getKind());
            assertEquals(left.getDeclarations(), right.getDeclarations());
            assertEquals(left.getPageUsage(), right.getPageUsage());
        }
        assertEquals(expected.getImages().size(), actual.getImages().size());
        for (int index = 0; index < expected.getImages().size(); index++) {
            ImageResource left = expected.getImages().get(index);
            ImageResource right = actual.getImages().get(index);
            assertEquals(left.getWidth(), right.getWidth());
            assertEquals(left.getHeight(), right.getHeight());
            assertEquals(left.getBitsPerComponent(), right.getBitsPerComponent());
            assertEquals(left.getColorComponents(), right.getColorComponents());
            assertEquals(left.getColorSpace().getFamily(), right.getColorSpace().getFamily());
            assertEquals(left.getColorSpace().getStatus(), right.getColorSpace().getStatus());
            assertEquals(left.getEncodedData().getAvailability(), right.getEncodedData().getAvailability());
            assertEquals(left.getDecodedData().getAvailability(), right.getDecodedData().getAvailability());
            assertEquals(left.getEncodedData().isSelected(), right.getEncodedData().isSelected());
            assertEquals(left.getDecodedData().isSelected(), right.getDecodedData().isSelected());
            assertArrayEquals(left.getEncodedData().getBytes().orElse(null), right.getEncodedData().getBytes().orElse(null));
            assertArrayEquals(left.getDecodedData().getBytes().orElse(null), right.getDecodedData().getBytes().orElse(null));
        }
    }

    private static DocumentResourceInventory nativeInventory(Path path) throws Exception {
        return new DocumentWorkflow().execute(WorkflowRequest.open(path, SaveMode.REWRITE),
                session -> session.query(ExtractImagesAndResources.version1(
                        limits(), ImageByteAccess.ENCODED_AND_DECODED))).getResult();
    }

    private static void expectFailure(DocumentFailureCode code, Runnable operation) {
        try {
            operation.run();
            fail("Expected bounded extraction failure");
        } catch (PdfException failure) {
            assertTrue(failure.getCause() instanceof DocumentFailure);
            DocumentFailure nativeFailure = (DocumentFailure) failure.getCause();
            assertEquals(code, nativeFailure.getCode());
            assertEquals("document.images-resources.extract", nativeFailure.getCapabilityId());
            assertEquals("The image and resource extraction limit was exceeded.", nativeFailure.getMessage());
            assertTrue(nativeFailure.getPublicationReceipts().isEmpty());
        }
    }

    private static ResourceExtractionLimits limits() { return limitsBuilder().build(); }

    private static ResourceExtractionLimits.Builder limitsBuilder() {
        return ResourceExtractionLimits.builder().maximumPages(10).maximumPageTreeNodes(100)
                .maximumTraversedResourceValues(1000).maximumResourceTraversalDepth(32)
                .maximumDecodedPixels(1000).maximumDecompressedBytes(10000).maximumReturnedBytes(10000);
    }

    private Path fixture(String name) throws IOException {
        Path path = temporary.getRoot().toPath().resolve(name);
        String[] objects = {
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 /MediaBox [0 0 100 100] "
                + "/Resources << /ExtGState << /Direct << /Type /ExtGState /ca 1 >> >> "
                + "/Font << /F 9 0 R >> /XObject << /Shared 5 0 R /Form 7 0 R >> >> >>",
            "<< /Type /Page /Parent 2 0 R >>",
            "<< /Type /Page /Parent 2 0 R >>",
            stream("/Type /XObject /Subtype /Image /Width 1 /Height 1 /BitsPerComponent 8 "
                    + "/ColorSpace /DeviceRGB /SMask 6 0 R", "ABC"),
            stream("/Type /XObject /Subtype /Image /Width 1 /Height 1 /BitsPerComponent 8 "
                    + "/ColorSpace /DeviceGray", "\u00ff"),
            stream("/Type /XObject /Subtype /Form /BBox [0 0 1 1] "
                    + "/Resources << /XObject << /Nested 5 0 R >> >>", ""),
            "null",
            "<< /Type /Font /Subtype /Type1 /BaseFont /ABCDEF+Helvetica >>"
        };
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write("%PDF-1.7\n".getBytes(StandardCharsets.ISO_8859_1));
        int[] offsets = new int[objects.length + 1];
        for (int index = 0; index < objects.length; index++) {
            offsets[index + 1] = bytes.size();
            bytes.write(((index + 1) + " 0 obj\n" + objects[index] + "\nendobj\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
        }
        int xref = bytes.size();
        bytes.write(("xref\n0 " + offsets.length + "\n0000000000 65535 f \n").getBytes(StandardCharsets.US_ASCII));
        for (int index = 1; index < offsets.length; index++) {
            bytes.write(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[index]).getBytes(StandardCharsets.US_ASCII));
        }
        bytes.write(("trailer\n<< /Size " + offsets.length + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n")
                .getBytes(StandardCharsets.US_ASCII));
        Files.write(path, bytes.toByteArray());
        return path;
    }

    private static String stream(String dictionary, String bytes) {
        return "<< " + dictionary + " /Length " + bytes.length() + " >>\nstream\n" + bytes + "\nendstream";
    }

    private static final class TrackingInput extends ByteArrayInputStream {
        private boolean closed;
        private TrackingInput(byte[] bytes) { super(bytes); }
        @Override public void close() { closed = true; }
    }

    private static final class TrackingOutput extends ByteArrayOutputStream {
        private boolean closed;
        @Override public void close() { closed = true; }
    }
}
