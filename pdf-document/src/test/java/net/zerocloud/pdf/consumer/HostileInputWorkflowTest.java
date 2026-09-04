package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import net.zerocloud.pdf.Annotation;
import net.zerocloud.pdf.AnnotationAppearance;
import net.zerocloud.pdf.AnnotationProperties;
import net.zerocloud.pdf.AnnotationRectangle;
import net.zerocloud.pdf.CancellationToken;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWork;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ExtractionLimits;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.WorkflowProgressPhase;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.WorkflowResourcePolicy;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.command.SetXmpMetadata;
import net.zerocloud.pdf.command.UpdateAnnotations;
import net.zerocloud.pdf.composition.CanvasColorSpace;
import net.zerocloud.pdf.composition.CanvasImage;
import net.zerocloud.pdf.composition.CanvasMask;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.composition.CanvasProgram;
import net.zerocloud.pdf.composition.CanvasResourceLimits;
import net.zerocloud.pdf.composition.command.DrawCanvas;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.ExtractTextAndStructure;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.XmpMetadata;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class HostileInputWorkflowTest {

    private static final String LIMIT_CAPABILITY =
            "document.hostile-input-limits";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void policyIsCompleteImmutableAndEnvironmentDefaultIsApplied()
            throws Exception {
        WorkflowResourcePolicy defaults =
                WorkflowResourcePolicy.safeDefaults();
        assertEquals(1L << 30, defaults.getMaximumInputBytes());
        assertEquals(5000, defaults.getMaximumPages());
        assertEquals(2_000_000L, defaults.getMaximumObjects());
        assertEquals(16384, defaults.getMaximumNestingDepth());
        assertEquals(4L << 30, defaults.getMaximumDecompressedBytes());
        assertEquals(1_000_000_000L, defaults.getMaximumDecodedPixels());
        assertEquals(256L << 20, defaults.getMaximumOwnedMemoryBytes());
        assertEquals(
                4L << 30,
                defaults.getMaximumTemporaryStorageBytes());
        assertEquals(
                Duration.ofMinutes(5L),
                defaults.getMaximumElapsedTime());
        assertEquals(4, defaults.getMaximumConcurrentWorkflows());
        assertEquals(
                defaults,
                WorkflowEnvironment.systemDefaults()
                        .getDefaultResourcePolicy());

        WorkflowRequest request = WorkflowRequest.builder()
                .target("target", PublicationTarget.stream(
                        new ByteArrayOutputStream()))
                .saveMode(SaveMode.REWRITE)
                .resourcePolicy(defaults)
                .build();
        assertEquals(defaults, request.getResourcePolicy().get());
        assertFalse(WorkflowRequest.builder()
                .target("target", PublicationTarget.stream(
                        new ByteArrayOutputStream()))
                .saveMode(SaveMode.REWRITE)
                .build()
                .getResourcePolicy()
                .isPresent());

        byte[] pdf = pdf(null, new String[0]);
        WorkflowRequest inherited = WorkflowRequest.builder()
                .source("source", DocumentSource.bytes(pdf, pdf.length))
                .primarySource("source")
                .saveMode(SaveMode.REWRITE)
                .build();
        WorkflowEnvironment pageFree = WorkflowEnvironment.builder()
                .defaultResourcePolicy(policyWithPages(0))
                .build();
        assertNeverRuns(
                new DocumentWorkflow(pageFree),
                inherited,
                DocumentFailureCode.PAGE_LIMIT_EXCEEDED,
                "The workflow page-count limit was exceeded.");

        assertEquals(Integer.valueOf(1), new DocumentWorkflow(pageFree)
                .execute(
                        WorkflowRequest.builder()
                                .source(
                                        "source",
                                        DocumentSource.bytes(
                                                pdf,
                                                pdf.length))
                                .primarySource("source")
                                .saveMode(SaveMode.REWRITE)
                                .resourcePolicy(policyWithPages(1))
                                .build(),
                        session -> session.query(PageCount.INSTANCE))
                .getResult());
    }

    @Test
    public void invalidIncompleteAndOverflowingDeclarationsAreRejected() {
        try {
            WorkflowResourcePolicy.builder().maximumInputBytes(-1L);
            fail("Expected a negative limit to be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals(
                    "maximumInputBytes must not be negative",
                    expected.getMessage());
        }
        try {
            WorkflowResourcePolicy.builder()
                    .maximumElapsedTime(Duration.ofSeconds(-1L));
            fail("Expected a negative duration to be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals(
                    "maximumElapsedTime must not be negative",
                    expected.getMessage());
        }
        try {
            WorkflowResourcePolicy.builder()
                    .maximumElapsedTime(Duration.ofSeconds(Long.MAX_VALUE));
            fail("Expected an overflowing duration to be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals(
                    "maximumElapsedTime is too large for exact accounting",
                    expected.getMessage());
        }
        try {
            WorkflowResourcePolicy.builder().maximumNestingDepth(
                    WorkflowResourcePolicy.MAXIMUM_NESTING_DEPTH_VERSION_1
                            + 1);
            fail("Expected the stack-safe nesting ceiling to be enforced");
        } catch (IllegalArgumentException expected) {
            assertEquals(
                    "maximumNestingDepth must not exceed "
                            + WorkflowResourcePolicy
                                    .MAXIMUM_NESTING_DEPTH_VERSION_1
                            + " in version 1",
                    expected.getMessage());
        }
        try {
            WorkflowResourcePolicy.builder().maximumInputBytes(1L).build();
            fail("Expected an incomplete declaration to be rejected");
        } catch (IllegalStateException expected) {
            assertEquals(
                    "Every version-1 workflow resource limit must be declared.",
                    expected.getMessage());
        }
    }

    @Test
    public void aggregateInputBoundaryCoversEverySourceKindAndNamedSources()
            throws Exception {
        byte[] pdf = pdf(null, new String[0]);
        Path path = write("input.pdf", pdf);

        assertPageCount(DocumentSource.path(path), policyWithInput(pdf.length));
        assertLimit(
                request(DocumentSource.path(path), policyWithInput(pdf.length - 1L)),
                DocumentFailureCode.WORKFLOW_INPUT_LIMIT_EXCEEDED,
                "The workflow input-byte limit was exceeded.");

        assertPageCount(
                DocumentSource.stream(
                        new ByteArrayInputStream(pdf),
                        pdf.length),
                policyWithInput(pdf.length));
        assertLimit(
                request(
                        DocumentSource.stream(
                                new ByteArrayInputStream(pdf),
                                pdf.length),
                        policyWithInput(pdf.length - 1L)),
                DocumentFailureCode.WORKFLOW_INPUT_LIMIT_EXCEEDED,
                "The workflow input-byte limit was exceeded.");

        assertPageCount(
                DocumentSource.channel(
                        Channels.newChannel(new ByteArrayInputStream(pdf)),
                        pdf.length),
                policyWithInput(pdf.length));
        assertLimit(
                request(
                        DocumentSource.channel(
                                Channels.newChannel(
                                        new ByteArrayInputStream(pdf)),
                                pdf.length),
                        policyWithInput(pdf.length - 1L)),
                DocumentFailureCode.WORKFLOW_INPUT_LIMIT_EXCEEDED,
                "The workflow input-byte limit was exceeded.");

        assertPageCount(
                DocumentSource.bytes(pdf, pdf.length),
                policyWithInput(pdf.length));
        assertLimit(
                request(
                        DocumentSource.bytes(pdf, pdf.length),
                        policyWithInput(pdf.length - 1L)),
                DocumentFailureCode.WORKFLOW_INPUT_LIMIT_EXCEEDED,
                "The workflow input-byte limit was exceeded.");

        WorkflowRequest exactAggregate = WorkflowRequest.builder()
                .source("primary", DocumentSource.path(path))
                .source("additional", DocumentSource.bytes(pdf, pdf.length))
                .primarySource("primary")
                .saveMode(SaveMode.REWRITE)
                .resourcePolicy(policyWithInput(pdf.length * 2L))
                .build();
        assertEquals(Integer.valueOf(1), new DocumentWorkflow().execute(
                exactAggregate,
                session -> session.query(PageCount.INSTANCE)).getResult());

        WorkflowRequest firstAggregateExcess = WorkflowRequest.builder()
                .source("primary", DocumentSource.path(path))
                .source("private-source-name",
                        DocumentSource.bytes(pdf, pdf.length))
                .primarySource("primary")
                .saveMode(SaveMode.REWRITE)
                .resourcePolicy(policyWithInput(pdf.length * 2L - 1L))
                .build();
        assertLimit(
                firstAggregateExcess,
                DocumentFailureCode.WORKFLOW_INPUT_LIMIT_EXCEEDED,
                "The workflow input-byte limit was exceeded.");
    }

    @Test
    public void pageAndObjectBoundariesFailBeforeCallerWork() throws Exception {
        byte[] pdf = pdf(null, new String[0]);
        Path path = write("counts.pdf", pdf);

        assertPageCount(DocumentSource.path(path), policyWithPages(1));
        assertNeverRuns(
                request(DocumentSource.path(path), policyWithPages(0)),
                DocumentFailureCode.PAGE_LIMIT_EXCEEDED,
                "The workflow page-count limit was exceeded.");

        assertPageCount(DocumentSource.path(path), policyWithObjects(3L));
        assertNeverRuns(
                request(DocumentSource.path(path), policyWithObjects(2L)),
                DocumentFailureCode.OBJECT_LIMIT_EXCEEDED,
                "The workflow PDF-object limit was exceeded.");
    }

    @Test
    public void nestingBoundaryIsStackSafeAndCannotBeResetByPatch()
            throws Exception {
        String nested = "0";
        for (int depth = 0; depth < 7; depth++) {
            nested = "[" + nested + "]";
        }
        Path path = write("nested.pdf", pdf("/Deep " + nested, new String[0]));

        assertPageCount(DocumentSource.path(path), policyWithNesting(10));
        assertNeverRuns(
                request(DocumentSource.path(path), policyWithNesting(9)),
                DocumentFailureCode.NESTING_LIMIT_EXCEEDED,
                "The workflow nesting-depth limit was exceeded.");

        String shared = "0";
        for (int depth = 0; depth < 7; depth++) {
            shared = "[" + shared + "]";
        }
        Path sharedPath = write(
                "shared-graph.pdf",
                pdf(
                        "/Shallow 4 0 R /Deep [[4 0 R]]",
                        new String[] {shared}));
        assertPageCount(
                DocumentSource.path(sharedPath),
                policyWithNesting(13));
        assertNeverRuns(
                request(
                        DocumentSource.path(sharedPath),
                        policyWithNesting(12)),
                DocumentFailureCode.NESTING_LIMIT_EXCEEDED,
                "The workflow nesting-depth limit was exceeded.");
    }

    @Test
    public void everySupportedFilterStageConsumesOneAggregateBudget()
            throws Exception {
        String encoded = "0241424380>";
        String stream = "<< /Length " + encoded.length()
                + " /Filter [/ASCIIHexDecode /RunLengthDecode] >>\nstream\n"
                + encoded + "\nendstream";
        Path path = write(
                "filters.pdf",
                pdf("/Payload 4 0 R", new String[] {stream}));

        assertPageCount(
                DocumentSource.path(path),
                policyWithDecompression(8L));
        assertNeverRuns(
                request(
                        DocumentSource.path(path),
                        policyWithDecompression(7L)),
                DocumentFailureCode.DECOMPRESSION_LIMIT_EXCEEDED,
                "The workflow decompression limit was exceeded.");
    }

    @Test
    public void repeatedLazyDecodingCannotResetTheWorkflowBudget()
            throws Exception {
        String encoded = "0241424380>";
        String stream = "<< /Length " + encoded.length()
                + " /Filter [/ASCIIHexDecode /RunLengthDecode] >>\nstream\n"
                + encoded + "\nendstream";
        Path path = write(
                "repeated-filters.pdf",
                pdf("/Payload 4 0 R", new String[] {stream}));
        WorkflowRequest request = request(
                DocumentSource.path(path),
                policyWithDecompression(16L));

        try {
            new DocumentWorkflow().execute(request, session -> {
                ObjectReference root = session.query(
                        DocumentRootReference.INSTANCE);
                PdfValue rootValue = session.query(InspectObject.version1(
                        root,
                        PdfInspectionLimits.of(20L, 20L)));
                PdfValue payload = ((PdfDictionary) rootValue).get(
                        PdfName.of("Payload"));
                ObjectReference payloadReference =
                        ((PdfIndirectReference) payload).getReference();
                PdfStream payloadStream = (PdfStream) session.query(
                        InspectObject.version1(
                                payloadReference,
                                PdfInspectionLimits.of(20L, 20L)));
                assertArrayEquals(
                        new byte[] {65, 66, 67},
                        payloadStream.readBytes());
                payloadStream.readBytes();
                return null;
            });
            fail("Expected repeated decoding to exhaust the shared budget");
        } catch (DocumentFailure failure) {
            assertSafeLimitFailure(
                    failure,
                    DocumentFailureCode.DECOMPRESSION_LIMIT_EXCEEDED,
                    "The workflow decompression limit was exceeded.");
        }
    }

    @Test
    public void patchNestingIsRejectedBeforeRecursiveMaterialization()
            throws Exception {
        Path sharedPath = write(
                "shared-patch-dag.pdf",
                pdf(null, new String[0]));
        new DocumentWorkflow().execute(
                request(
                        DocumentSource.path(sharedPath),
                        policyWithNesting(16)),
                HostileInputWorkflowTest::applySharedDagPatch);
        try {
            new DocumentWorkflow().execute(
                    request(
                            DocumentSource.path(sharedPath),
                            policyWithNesting(8)),
                    HostileInputWorkflowTest::applySharedDagPatch);
            fail("Expected the deeper shared path to exhaust policy");
        } catch (DocumentFailure failure) {
            assertSafeLimitFailure(
                    failure,
                    DocumentFailureCode.NESTING_LIMIT_EXCEEDED,
                    "The workflow nesting-depth limit was exceeded.");
        }

        Path target = write("nested-patch-target.pdf", new byte[] {9, 8, 7});
        byte[] existing = Files.readAllBytes(target);
        WorkflowRequest request = WorkflowRequest.builder()
                .target("target", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .resourcePolicy(policyWithNesting(10))
                .build();

        try {
            new DocumentWorkflow().execute(request, session -> {
                session.execute(AddBlankPage.INSTANCE);
                ObjectReference root = session.query(
                        DocumentRootReference.INSTANCE);
                PdfValue nested = PdfNumber.of(0L);
                for (int depth = 0; depth < 11; depth++) {
                    nested = PdfArray.of(nested);
                }
                session.execute(DocumentPatch.builder()
                        .setDictionaryEntry(
                                root,
                                PdfName.of("Deep"),
                                nested)
                        .build());
                return null;
            });
            fail("Expected the nested Patch to exhaust policy");
        } catch (DocumentFailure failure) {
            assertSafeLimitFailure(
                    failure,
                    DocumentFailureCode.NESTING_LIMIT_EXCEEDED,
                    "The workflow nesting-depth limit was exceeded.");
        }
        assertArrayEquals(existing, Files.readAllBytes(target));
    }

    private static Void applySharedDagPatch(DocumentSession session)
            throws DocumentFailure {
        PdfValue shared = PdfNumber.of(0L);
        for (int depth = 0; depth < 7; depth++) {
            shared = PdfArray.of(shared);
        }
        PdfValue sharedDag = PdfArray.of(
                shared,
                PdfArray.of(shared));
        ObjectReference root = session.query(DocumentRootReference.INSTANCE);
        session.execute(DocumentPatch.builder()
                .setDictionaryEntry(
                        root,
                        PdfName.of("SharedDag"),
                        sharedDag)
                .build());
        return null;
    }

    @Test
    public void sharedNestingCeilingComposesWithSupportedContentScopes()
            throws Exception {
        CanvasProgram nestedGraphics = CanvasProgram.version1()
                .saveState()
                .saveState()
                .restoreState()
                .restoreState()
                .build();
        assertWorkNestingFailure(
                blankRequest(policyWithNesting(1)),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    session.execute(DrawCanvas.version1(1, nestedGraphics));
                    return null;
                });

        Path nestedContent = temporaryFolder.getRoot().toPath()
                .resolve("nested-content.pdf");
        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .target("target", PublicationTarget.path(nestedContent))
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    session.execute(DrawCanvas.version1(1, nestedGraphics));
                    return null;
                });
        WorkflowRequest lowNesting = request(
                DocumentSource.path(nestedContent),
                policyWithNesting(1));
        assertWorkNestingFailure(lowNesting, session -> {
            session.query(ExtractTextAndStructure.version1(textLimits()));
            return null;
        });
        assertWorkNestingFailure(lowNesting, session -> {
            session.execute(DrawCanvas.version1(
                    1,
                    CanvasProgram.version1()
                            .moveTo(0d, 0d)
                            .lineTo(1d, 1d)
                            .stroke()
                            .build()));
            return null;
        });

        Annotation nestedAppearance = Annotation.text(
                AnnotationProperties.version1(
                                "nested-appearance",
                                1,
                                AnnotationRectangle.of(0L, 0L, 10L, 10L))
                        .appearance(AnnotationAppearance.version1(
                                AnnotationRectangle.of(0L, 0L, 10L, 10L),
                                "q q Q Q\n".getBytes(
                                        StandardCharsets.US_ASCII)))
                        .build(),
                Annotation.TextIcon.NOTE,
                false);
        assertWorkNestingFailure(
                blankRequest(policyWithNesting(1)),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    session.execute(UpdateAnnotations.version1()
                            .put(nestedAppearance)
                            .build());
                    return null;
                });
    }

    @Test
    public void decodedPixelBoundaryCoversImagePreparationMetadata()
            throws Exception {
        String samples = "abcdefghijklmnopqr";
        String image = "<< /Type /XObject /Subtype /Image /Width 2 /Height 3"
                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Length "
                + samples.length() + " >>"
                + "\nstream\n" + samples + "\nendstream";
        Path path = write(
                "pixels.pdf",
                pdfWithPageResources(
                        "/XObject << /ImageFixture 4 0 R >>",
                        new String[] {image}));

        assertPageCount(DocumentSource.path(path), policyWithPixels(6L));
        assertNeverRuns(
                request(DocumentSource.path(path), policyWithPixels(5L)),
                DocumentFailureCode.PIXEL_LIMIT_EXCEEDED,
                "The workflow decoded-pixel limit was exceeded.");

        String malformed = "<< /Type /XObject /Subtype /Image"
                + " /Width 2 /Height 3 /ColorSpace /DeviceRGB"
                + " /BitsPerComponent 8 /Filter /ASCIIHexDecode /Length 0 >>"
                + "\nstream\n\nendstream";
        Path malformedPath = write(
                "malformed-pixels.pdf",
                pdfWithPageResources(
                        "/XObject << /Malformed 4 0 R >>",
                        new String[] {malformed}));
        assertPageCount(
                DocumentSource.path(malformedPath),
                policyWithPixels(0L));

        Path mixedPath = write(
                "mixed-pixels.pdf",
                pdfWithPageResources(
                        "/XObject << /Broken 4 0 R /Valid 5 0 R >>",
                        new String[] {malformed, image}));
        assertNeverRuns(
                request(DocumentSource.path(mixedPath), policyWithPixels(5L)),
                DocumentFailureCode.PIXEL_LIMIT_EXCEEDED,
                "The workflow decoded-pixel limit was exceeded.");

        String invalidMaskOwner = "<< /Type /XObject /Subtype /Image"
                + " /Width 1 /Height 1 /ColorSpace /DeviceRGB"
                + " /BitsPerComponent 8 /Mask 5 0 R"
                + " /Filter /ASCIIHexDecode /Length 7 >>"
                + "\nstream\n726762>\nendstream";
        String invalidMask = "<< /Type /XObject /Subtype /Image"
                + " /Width 1 /Height 1 /ColorSpace /DeviceGray"
                + " /BitsPerComponent 8 /Length 1 >>"
                + "\nstream\ng\nendstream";
        Path invalidMaskPath = write(
                "invalid-mask-pixels.pdf",
                pdfWithPageResources(
                        "/XObject << /Masked 4 0 R >>",
                        new String[] {invalidMaskOwner, invalidMask}));
        assertPageCount(
                DocumentSource.path(invalidMaskPath),
                policyWithPixels(0L));

        BufferedImage raster = new BufferedImage(
                2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream encodedJpeg = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(raster, "JPEG", encodedJpeg));
        byte[] jpegBytes = encodedJpeg.toByteArray();
        String dctImage = "<< /Type /XObject /Subtype /Image"
                + " /Width 2 /Height 2 /ColorSpace /DeviceRGB"
                + " /BitsPerComponent 8 /Filter /DCTDecode /Length "
                + jpegBytes.length + " >>\nstream\n"
                + new String(jpegBytes, StandardCharsets.ISO_8859_1)
                + "\nendstream";
        Path dctPath = write(
                "dct-pixels.pdf",
                pdfWithPageResources(
                        "/XObject << /Dct 4 0 R >>",
                        new String[] {dctImage}));
        assertPageCount(DocumentSource.path(dctPath), policyWithPixels(4L));
        assertNeverRuns(
                request(DocumentSource.path(dctPath), policyWithPixels(3L)),
                DocumentFailureCode.PIXEL_LIMIT_EXCEEDED,
                "The workflow decoded-pixel limit was exceeded.");

        String shallowUnfiltered = "<< /Type /XObject /Subtype /Image"
                + " /Width 2 /Height 3 /Length 0 >>"
                + "\nstream\n\nendstream";
        String shallowFlate = "<< /Type /XObject /Subtype /Image"
                + " /Width 2 /Height 3 /Filter /FlateDecode /Length 0 >>"
                + "\nstream\n\nendstream";
        String shallowDct = "<< /Type /XObject /Subtype /Image"
                + " /Width 2 /Height 3 /Filter [/DCTDecode] /Length 0 >>"
                + "\nstream\n\nendstream";
        Path shallowPath = write(
                "canvas-existing-pixels.pdf",
                pdfWithPageResources(
                        "/XObject << /Raw 4 0 R /Flate 5 0 R /Dct 6 0 R >>",
                        new String[] {
                            shallowUnfiltered, shallowFlate, shallowDct
                        }));
        assertPageCount(
                DocumentSource.path(shallowPath),
                policyWithPixels(18L));
        assertNeverRuns(
                request(
                        DocumentSource.path(shallowPath),
                        policyWithPixels(17L)),
                DocumentFailureCode.PIXEL_LIMIT_EXCEEDED,
                "The workflow decoded-pixel limit was exceeded.");
    }

    @Test
    public void ownedMemoryAndTemporaryStorageHaveExactSourceBoundaries()
            throws Exception {
        byte[] pdf = pdf(null, new String[0]);

        assertPageCount(
                DocumentSource.bytes(pdf, pdf.length),
                policyWithMemory(pdf.length));
        assertNeverRuns(
                request(
                        DocumentSource.bytes(pdf, pdf.length),
                        policyWithMemory(pdf.length - 1L)),
                DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                "The workflow owned-memory limit was exceeded.");

        DocumentSource sharedBytes = DocumentSource.bytes(pdf, pdf.length);
        WorkflowRequest sharedBytesRequest = WorkflowRequest.builder()
                .source("primary", sharedBytes)
                .source("additional", sharedBytes)
                .primarySource("primary")
                .saveMode(SaveMode.REWRITE)
                .resourcePolicy(policyWithMemory(pdf.length))
                .build();
        assertEquals(Integer.valueOf(1), new DocumentWorkflow().execute(
                sharedBytesRequest,
                session -> session.query(PageCount.INSTANCE)).getResult());

        String xmp = "<< /Type /Metadata /Subtype /XML /Length 4 >>"
                + "\nstream\n<x/>\nendstream";
        Path xmpPath = write(
                "owned-memory-xmp.pdf",
                pdf("/Metadata 4 0 R", new String[] {xmp}));
        assertArrayEquals(
                "<x/>".getBytes(StandardCharsets.US_ASCII),
                new DocumentWorkflow().execute(
                        request(
                                DocumentSource.path(xmpPath),
                                policyWithMemory(8L)),
                        session -> session.query(
                                XmpMetadata.version1(4L)))
                        .getResult());
        try {
            new DocumentWorkflow().execute(
                    request(
                            DocumentSource.path(xmpPath),
                            policyWithMemory(7L)),
                    session -> session.query(XmpMetadata.version1(4L)));
            fail("Expected returned XMP bytes to exhaust owned memory");
        } catch (DocumentFailure failure) {
            assertSafeLimitFailure(
                    failure,
                    DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                    "The workflow owned-memory limit was exceeded.");
        }

        String unevenXmp = "<< /Type /Metadata /Subtype /XML /Length 5 >>"
                + "\nstream\n<x />\nendstream";
        Path unevenXmpPath = write(
                "uneven-owned-memory-xmp.pdf",
                pdf("/Metadata 4 0 R", new String[] {unevenXmp}));
        assertArrayEquals(
                "<x />".getBytes(StandardCharsets.US_ASCII),
                new DocumentWorkflow().execute(
                        request(
                                DocumentSource.path(unevenXmpPath),
                                policyWithMemory(10L)),
                        session -> session.query(XmpMetadata.version1(5L)))
                        .getResult());
        try {
            new DocumentWorkflow().execute(
                    request(
                            DocumentSource.path(unevenXmpPath),
                            policyWithMemory(9L)),
                    session -> session.query(XmpMetadata.version1(5L)));
            fail("Expected returned XMP copy to exhaust owned memory");
        } catch (DocumentFailure failure) {
            assertSafeLimitFailure(
                    failure,
                    DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                    "The workflow owned-memory limit was exceeded.");
        }

        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .target(
                                    "target",
                                    PublicationTarget.stream(
                                            new ByteArrayOutputStream()))
                            .saveMode(SaveMode.REWRITE)
                            .resourcePolicy(policyWithMemory(0L))
                            .build(),
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        session.execute(DrawCanvas.version1(
                                1,
                                CanvasProgram.version1()
                                        .moveTo(0d, 0d)
                                        .lineTo(1d, 1d)
                                        .stroke()
                                        .build()));
                        return null;
                    });
            fail("Expected Canvas serialization to exhaust owned memory");
        } catch (DocumentFailure failure) {
            assertSafeLimitFailure(
                    failure,
                    DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                    "The workflow owned-memory limit was exceeded.");
        }

        Path storageRoot = temporaryFolder.newFolder("workflow-temp")
                .toPath();
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .temporaryDirectory(storageRoot)
                .build();
        assertPageCount(
                new DocumentWorkflow(environment),
                DocumentSource.stream(
                        new ByteArrayInputStream(pdf),
                        pdf.length),
                policyWithTemporaryStorage(pdf.length));
        assertDirectoryEmpty(storageRoot);

        assertNeverRuns(
                new DocumentWorkflow(environment),
                request(
                        DocumentSource.stream(
                                new ByteArrayInputStream(pdf),
                                pdf.length),
                        policyWithTemporaryStorage(pdf.length - 1L)),
                DocumentFailureCode.TEMPORARY_STORAGE_LIMIT_EXCEEDED,
                "The workflow temporary-storage limit was exceeded.");
        assertDirectoryEmpty(storageRoot);
    }

    @Test
    public void pdfNumberSerializationHasExactBoundariesAndReusesMemory()
            throws Exception {
        Path source = write(
                "owned-memory-number.pdf",
                pdf("/T20Number 4 0 R", new String[] {"1.25"}));
        DocumentWork<PdfNumber> inspectNumber = session -> {
            ObjectReference root = session.query(
                    DocumentRootReference.INSTANCE);
            PdfDictionary catalog = (PdfDictionary) session.query(
                    InspectObject.version1(
                            root,
                            PdfInspectionLimits.of(20L, 20L)));
            ObjectReference number = ((PdfIndirectReference) catalog.get(
                    PdfName.of("T20Number"))).getReference();
            PdfNumber first = (PdfNumber) session.query(
                    InspectObject.version1(
                            number,
                            PdfInspectionLimits.of(1L, 1L)));
            PdfNumber second = (PdfNumber) session.query(InspectObject.version1(
                    number,
                    PdfInspectionLimits.of(1L, 1L)));
            assertEquals(first, second);
            return second;
        };

        assertEquals(
                PdfNumber.of(new BigDecimal("1.25")),
                new DocumentWorkflow().execute(
                        request(
                                DocumentSource.path(source),
                                policyWithMemory(16L)),
                        inspectNumber).getResult());
        try {
            new DocumentWorkflow().execute(
                    request(
                            DocumentSource.path(source),
                            policyWithMemory(15L)),
                    inspectNumber);
            fail("Expected source number conversion to exhaust owned memory");
        } catch (DocumentFailure failure) {
            assertSafeLimitFailure(
                    failure,
                    DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                    "The workflow owned-memory limit was exceeded.");
        }

        DocumentWork<Void> patchNumber = session -> {
            session.execute(AddBlankPage.INSTANCE);
            ObjectReference root = session.query(
                    DocumentRootReference.INSTANCE);
            session.execute(DocumentPatch.builder()
                    .setDictionaryEntry(
                            root,
                            PdfName.of("T20Number"),
                            PdfNumber.of(new BigDecimal("1.25")))
                    .build());
            return null;
        };
        new DocumentWorkflow().execute(
                blankRequest(policyWithMemory(16L)),
                patchNumber);
        try {
            new DocumentWorkflow().execute(
                    blankRequest(policyWithMemory(15L)),
                    patchNumber);
            fail("Expected request number conversion to exhaust owned memory");
        } catch (DocumentFailure failure) {
            assertSafeLimitFailure(
                    failure,
                    DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                    "The workflow owned-memory limit was exceeded.");
        }
    }

    @Test
    public void commandAndCanvasPayloadCopiesReserveMemoryBeforeCloning()
            throws Exception {
        try {
            new DocumentWorkflow().execute(
                    blankRequest(policyWithMemory(3L)),
                    session -> {
                        session.execute(SetXmpMetadata.version1(
                                "<x/>".getBytes(StandardCharsets.US_ASCII)));
                        return null;
                    });
            fail("Expected XMP input copying to exhaust owned memory");
        } catch (DocumentFailure failure) {
            assertSafeLimitFailure(
                    failure,
                    DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                    "The workflow owned-memory limit was exceeded.");
        }

        byte[] profile = ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData();
        CanvasProgram profiledImage = CanvasProgram.version2()
                .drawImage(
                        CanvasImage.rawSamples(
                                1,
                                1,
                                8,
                                CanvasColorSpace.iccBased(profile),
                                new byte[] {1, 2, 3}),
                        CanvasMatrix.IDENTITY)
                .build();
        assertCanvasMemoryFailure(profiledImage, profile.length - 1L);

        int width = 32;
        int height = 32;
        byte[] samples = new byte[width * height * 3];
        byte[] opacity = new byte[width * height];
        CanvasProgram maskedImage = CanvasProgram.version2()
                .drawImage(
                        CanvasImage.rawSamples(
                                width,
                                height,
                                8,
                                CanvasColorSpace.deviceRgb(),
                                samples)
                                .withSoftMask(CanvasMask.soft(
                                        width, height, opacity)),
                        CanvasMatrix.IDENTITY)
                .build();
        assertCanvasMemoryFailure(
                maskedImage,
                samples.length + opacity.length - 1L);
    }

    @Test
    public void stagedProductsAndEveryPathTargetShareTemporaryQuota()
            throws Exception {
        Path probeFirst = write("probe-first.pdf", new byte[] {1});
        Path probeSecond = write("probe-second.pdf", new byte[] {2});
        long exactBoundary = minimumTemporaryStorageForTwoPathTargets(
                probeFirst,
                probeSecond);
        assertTrue(exactBoundary > 0L);

        Path exactFirst = write("exact-first.pdf", new byte[] {1});
        Path exactSecond = write("exact-second.pdf", new byte[] {2});
        WorkflowRequest exact = WorkflowRequest.builder()
                .target("first", PublicationTarget.path(exactFirst))
                .target("second", PublicationTarget.path(exactSecond))
                .saveMode(SaveMode.REWRITE)
                .resourcePolicy(policyWithTemporaryStorage(
                        exactBoundary))
                .build();
        new DocumentWorkflow().execute(exact, session -> {
            session.execute(AddBlankPage.INSTANCE);
            return null;
        });
        assertTrue(Files.size(exactFirst) > 1L);
        assertArrayEquals(
                Files.readAllBytes(exactFirst),
                Files.readAllBytes(exactSecond));

        byte[] firstSentinel = new byte[] {3, 4, 5};
        byte[] secondSentinel = new byte[] {6, 7, 8};
        Path excessFirst = write("excess-first.pdf", firstSentinel);
        Path excessSecond = write("excess-second.pdf", secondSentinel);
        WorkflowRequest excess = WorkflowRequest.builder()
                .target("secret-first", PublicationTarget.path(excessFirst))
                .target("secret-second", PublicationTarget.path(excessSecond))
                .saveMode(SaveMode.REWRITE)
                .resourcePolicy(policyWithTemporaryStorage(
                        exactBoundary - 1L))
                .build();
        try {
            new DocumentWorkflow().execute(excess, session -> {
                session.execute(AddBlankPage.INSTANCE);
                return null;
            });
            fail("Expected temporary storage exhaustion at first excess");
        } catch (DocumentFailure failure) {
            assertSafeLimitFailure(
                    failure,
                    DocumentFailureCode.TEMPORARY_STORAGE_LIMIT_EXCEEDED,
                    "The workflow temporary-storage limit was exceeded.");
            assertEquals(2, failure.getPublicationReceipts().size());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(1).getStatus());
        }
        assertArrayEquals(firstSentinel, Files.readAllBytes(excessFirst));
        assertArrayEquals(secondSentinel, Files.readAllBytes(excessSecond));
    }

    private long minimumTemporaryStorageForTwoPathTargets(
            Path first,
            Path second) throws Exception {
        if (publishesWithTemporaryStorage(0L, first, second)) {
            return 0L;
        }
        long lowerBound = 0L;
        long upperBound = 1L;
        while (!publishesWithTemporaryStorage(
                upperBound,
                first,
                second)) {
            lowerBound = upperBound;
            if (upperBound > Long.MAX_VALUE / 2L) {
                fail("Could not discover a successful temporary-storage bound");
            }
            upperBound *= 2L;
        }
        while (upperBound - lowerBound > 1L) {
            long candidate = lowerBound
                    + (upperBound - lowerBound) / 2L;
            if (publishesWithTemporaryStorage(candidate, first, second)) {
                upperBound = candidate;
            } else {
                lowerBound = candidate;
            }
        }
        return upperBound;
    }

    private boolean publishesWithTemporaryStorage(
            long maximum,
            Path first,
            Path second) throws Exception {
        WorkflowRequest request = WorkflowRequest.builder()
                .target("first", PublicationTarget.path(first))
                .target("second", PublicationTarget.path(second))
                .saveMode(SaveMode.REWRITE)
                .resourcePolicy(policyWithTemporaryStorage(maximum))
                .build();
        try {
            new DocumentWorkflow().execute(request, session -> {
                session.execute(AddBlankPage.INSTANCE);
                return null;
            });
            return true;
        } catch (DocumentFailure failure) {
            if (failure.getCode()
                    == DocumentFailureCode.TEMPORARY_STORAGE_LIMIT_EXCEEDED) {
                assertSafeLimitFailure(
                        failure,
                        DocumentFailureCode.TEMPORARY_STORAGE_LIMIT_EXCEEDED,
                        "The workflow temporary-storage limit was exceeded.");
                return false;
            }
            throw failure;
        }
    }

    @Test
    public void executionStopsInterruptOwnedSourceReadsAtExactBoundaries()
            throws Exception {
        byte[] pdf = pdf(null, new String[0]);
        byte[] existing = new byte[] {71, 72, 73};

        CancellationToken token = CancellationToken.create();
        Path cancelledTarget = write("cancelled-target.pdf", existing);
        WorkflowRequest cancelled = WorkflowRequest.builder()
                .source("secret-source",
                        DocumentSource.stream(
                                new CancellingInputStream(pdf, token),
                                pdf.length))
                .primarySource("secret-source")
                .target("secret-target",
                        PublicationTarget.path(cancelledTarget))
                .saveMode(SaveMode.REWRITE)
                .cancellationToken(token)
                .build();
        assertLimit(
                cancelled,
                DocumentFailureCode.WORKFLOW_CANCELLED,
                "The workflow was cancelled.");
        assertArrayEquals(existing, Files.readAllBytes(cancelledTarget));

        MutableClock exactClock = new MutableClock(
                Instant.parse("2026-09-03T00:00:00Z"),
                ZoneId.of("UTC"));
        assertEquals(Integer.valueOf(1), new DocumentWorkflow(
                WorkflowEnvironment.withClock(exactClock)).execute(
                        request(
                                DocumentSource.stream(
                                        new AdvancingInputStream(
                                                pdf,
                                                exactClock,
                                                Duration.ofSeconds(1L)),
                                        pdf.length),
                                policyWithElapsed(Duration.ofSeconds(1L))),
                        session -> session.query(PageCount.INSTANCE))
                .getResult());

        MutableClock elapsedClock = new MutableClock(
                Instant.parse("2026-09-03T01:00:00Z"),
                ZoneId.of("UTC"));
        Path elapsedTarget = write("elapsed-target.pdf", existing);
        WorkflowRequest elapsed = WorkflowRequest.builder()
                .source("source",
                        DocumentSource.stream(
                                new AdvancingInputStream(
                                        pdf,
                                        elapsedClock,
                                        Duration.ofSeconds(1L).plusNanos(1L)),
                                pdf.length))
                .primarySource("source")
                .target("target", PublicationTarget.path(elapsedTarget))
                .saveMode(SaveMode.REWRITE)
                .resourcePolicy(policyWithElapsed(Duration.ofSeconds(1L)))
                .build();
        assertLimit(
                new DocumentWorkflow(
                        WorkflowEnvironment.withClock(elapsedClock)),
                elapsed,
                DocumentFailureCode.ELAPSED_TIME_LIMIT_EXCEEDED,
                "The workflow elapsed-time limit was exceeded.");
        assertArrayEquals(existing, Files.readAllBytes(elapsedTarget));

        MutableClock deadlineClock = new MutableClock(
                Instant.parse("2026-09-03T02:00:00Z"),
                ZoneId.of("UTC"));
        Path deadlineTarget = write("deadline-target.pdf", existing);
        WorkflowRequest deadline = WorkflowRequest.builder()
                .source("source",
                        DocumentSource.stream(
                                new AdvancingInputStream(
                                        pdf,
                                        deadlineClock,
                                        Duration.ofSeconds(1L)),
                                pdf.length))
                .primarySource("source")
                .target("target", PublicationTarget.path(deadlineTarget))
                .saveMode(SaveMode.REWRITE)
                .deadline(deadlineClock.instant().plusSeconds(1L))
                .build();
        assertLimit(
                new DocumentWorkflow(
                        WorkflowEnvironment.withClock(deadlineClock)),
                deadline,
                DocumentFailureCode.DEADLINE_EXCEEDED,
                "The workflow deadline has expired.");
        assertArrayEquals(existing, Files.readAllBytes(deadlineTarget));
    }

    @Test
    public void cancellationDuringStreamPublicationReportsPartialFailure()
            throws Exception {
        CancellationToken token = CancellationToken.create();
        CancellingOutputStream output = new CancellingOutputStream(token);
        WorkflowRequest request = WorkflowRequest.builder()
                .target("response", PublicationTarget.stream(output))
                .target("later", PublicationTarget.stream(
                        new ByteArrayOutputStream()))
                .saveMode(SaveMode.REWRITE)
                .cancellationToken(token)
                .build();

        try {
            new DocumentWorkflow().execute(request, session -> {
                session.execute(AddBlankPage.INSTANCE);
                return null;
            });
            fail("Expected cancellation during publication");
        } catch (DocumentFailure failure) {
            assertSafeLimitFailure(
                    failure,
                    DocumentFailureCode.WORKFLOW_CANCELLED,
                    "The workflow was cancelled.");
            assertEquals(2, failure.getPublicationReceipts().size());
            assertEquals(
                    PublicationStatus.FAILED,
                    failure.getPublicationReceipts().get(0).getStatus());
            assertTrue(failure.getPublicationReceipts().get(0)
                    .isPartialOutputPossible());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(1).getStatus());
        }
        assertTrue(output.size() > 0);

        CancellationToken flushToken = CancellationToken.create();
        FlushCancellingOutputStream flushOutput =
                new FlushCancellingOutputStream(flushToken);
        WorkflowRequest flushRequest = WorkflowRequest.builder()
                .target("response", PublicationTarget.stream(flushOutput))
                .saveMode(SaveMode.REWRITE)
                .cancellationToken(flushToken)
                .build();
        try {
            new DocumentWorkflow().execute(flushRequest, session -> {
                session.execute(AddBlankPage.INSTANCE);
                return null;
            });
            fail("Expected cancellation during the final stream flush");
        } catch (DocumentFailure failure) {
            assertSafeLimitFailure(
                    failure,
                    DocumentFailureCode.WORKFLOW_CANCELLED,
                    "The workflow was cancelled.");
            assertEquals(1, failure.getPublicationReceipts().size());
            assertEquals(
                    PublicationStatus.FAILED,
                    failure.getPublicationReceipts().get(0).getStatus());
            assertTrue(failure.getPublicationReceipts().get(0)
                    .isPartialOutputPossible());
        }
        assertTrue(flushOutput.size() > 0);

        CancellationToken committedToken = CancellationToken.create();
        byte[] sentinel = new byte[] {11, 12, 13};
        Path committedTarget = write("committed-then-cancelled.pdf", sentinel);
        WorkflowRequest committedRequest = WorkflowRequest.builder()
                .target("target", PublicationTarget.path(committedTarget))
                .saveMode(SaveMode.REWRITE)
                .cancellationToken(committedToken)
                .progressListener(phase -> {
                    if (phase == WorkflowProgressPhase.TARGET_COMMITTED) {
                        committedToken.cancel();
                    }
                })
                .build();
        try {
            new DocumentWorkflow().execute(committedRequest, session -> {
                session.execute(AddBlankPage.INSTANCE);
                return null;
            });
            fail("Expected cancellation after the final target commitment");
        } catch (DocumentFailure failure) {
            assertSafeLimitFailure(
                    failure,
                    DocumentFailureCode.WORKFLOW_CANCELLED,
                    "The workflow was cancelled.");
            assertEquals(1, failure.getPublicationReceipts().size());
            assertEquals(
                    PublicationStatus.COMMITTED,
                    failure.getPublicationReceipts().get(0).getStatus());
            assertFalse(failure.getPublicationReceipts().get(0)
                    .isPartialOutputPossible());
        }
        assertFalse(java.util.Arrays.equals(
                sentinel,
                Files.readAllBytes(committedTarget)));
    }

    @Test
    public void sharedConcurrencyGateRejectsFirstExcessAndReleasesPermit()
            throws Exception {
        byte[] pdf = pdf(null, new String[0]);
        Path path = write("concurrency.pdf", pdf);
        WorkflowResourcePolicy oneAtATime = policyWithConcurrency(1);
        WorkflowRequest request = request(
                DocumentSource.path(path),
                oneAtATime);
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .defaultResourcePolicy(oneAtATime)
                .build();
        DocumentWorkflow workflow = new DocumentWorkflow(environment);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> backgroundFailure =
                new AtomicReference<Throwable>();

        Thread first = new Thread(() -> {
            try {
                workflow.execute(request, session -> {
                    entered.countDown();
                    try {
                        if (!release.await(10L, TimeUnit.SECONDS)) {
                            throw new AssertionError(
                                    "Timed out awaiting release");
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(
                                "Interrupted awaiting release",
                                interrupted);
                    }
                    return session.query(PageCount.INSTANCE);
                });
            } catch (Throwable failure) {
                backgroundFailure.set(failure);
            }
        }, "hostile-input-concurrency-fixture");
        first.start();
        assertTrue(entered.await(10L, TimeUnit.SECONDS));

        assertNeverRuns(
                workflow,
                request,
                DocumentFailureCode.CONCURRENCY_LIMIT_EXCEEDED,
                "The workflow concurrency limit was exceeded.");
        release.countDown();
        first.join(10000L);
        assertFalse(first.isAlive());
        if (backgroundFailure.get() != null) {
            throw new AssertionError(
                    "First workflow failed",
                    backgroundFailure.get());
        }

        assertEquals(Integer.valueOf(1), workflow.execute(
                request,
                session -> session.query(PageCount.INSTANCE)).getResult());
    }

    @Test
    public void policyFailurePoisonsCaughtOperationAndPreventsPublication()
            throws Exception {
        Path target = temporaryFolder.getRoot().toPath()
                .resolve("poisoned.pdf");
        byte[] existing = new byte[] {41, 42, 43};
        Files.write(target, existing);
        WorkflowRequest request = WorkflowRequest.builder()
                .target("target", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .resourcePolicy(policyWithPages(1))
                .build();

        try {
            new DocumentWorkflow().execute(request, session -> {
                session.execute(AddBlankPage.INSTANCE);
                try {
                    session.execute(AddBlankPage.INSTANCE);
                    fail("Expected the second page to exhaust policy");
                } catch (DocumentFailure expected) {
                    assertEquals(
                            DocumentFailureCode.PAGE_LIMIT_EXCEEDED,
                            expected.getCode());
                }
                return null;
            });
            fail("A caught policy exhaustion must still abort publication");
        } catch (DocumentFailure failure) {
            assertSafeLimitFailure(
                    failure,
                    DocumentFailureCode.PAGE_LIMIT_EXCEEDED,
                    "The workflow page-count limit was exceeded.");
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
        }
        assertArrayEquals(existing, Files.readAllBytes(target));
    }

    @Test
    public void unavailableEnvironmentStorageFailsSafely() throws Exception {
        Path missing = temporaryFolder.getRoot().toPath()
                .resolve("missing-storage-root");
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .temporaryDirectory(missing)
                .build();
        WorkflowRequest request = WorkflowRequest.builder()
                .target("private-target-name",
                        PublicationTarget.stream(new ByteArrayOutputStream()))
                .saveMode(SaveMode.REWRITE)
                .build();

        assertNeverRuns(
                new DocumentWorkflow(environment),
                request,
                DocumentFailureCode.TEMPORARY_STORAGE_UNAVAILABLE,
                "The workflow temporary-storage root is unavailable.");
    }

    private static WorkflowRequest request(
            DocumentSource source,
            WorkflowResourcePolicy policy) {
        return WorkflowRequest.builder()
                .source("source", source)
                .primarySource("source")
                .saveMode(SaveMode.REWRITE)
                .resourcePolicy(policy)
                .build();
    }

    private static WorkflowRequest blankRequest(
            WorkflowResourcePolicy policy) {
        return WorkflowRequest.builder()
                .target(
                        "target",
                        PublicationTarget.stream(new ByteArrayOutputStream()))
                .saveMode(SaveMode.REWRITE)
                .resourcePolicy(policy)
                .build();
    }

    private void assertCanvasMemoryFailure(
            CanvasProgram program,
            long maximumMemory) throws Exception {
        try {
            new DocumentWorkflow().execute(
                    blankRequest(policyWithMemory(maximumMemory)),
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        session.execute(DrawCanvas.version2(
                                1, program, canvasResourceLimits()));
                        return null;
                    });
            fail("Expected Canvas payload copying to exhaust owned memory");
        } catch (DocumentFailure failure) {
            assertSafeLimitFailure(
                    failure,
                    DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                    "The workflow owned-memory limit was exceeded.");
        }
    }

    private static CanvasResourceLimits canvasResourceLimits() {
        return CanvasResourceLimits.builder()
                .maximumEncodedImageBytes(1L << 20)
                .maximumDecodedImagePixels(1L << 20)
                .maximumDecodedImageBytes(4L << 20)
                .maximumIccProfileBytes(1L << 20)
                .maximumMaskBytes(1L << 20)
                .maximumGeneratedContentBytes(1L << 20)
                .maximumResourceDeclarations(32)
                .maximumTransparencyGroupDepth(4)
                .build();
    }

    private static ExtractionLimits textLimits() {
        return ExtractionLimits.builder()
                .maximumPages(2)
                .maximumPageTreeNodes(256)
                .maximumContentStreams(8)
                .maximumContentStreamDepth(4)
                .maximumDecodedBytes(64L * 1024L)
                .maximumTextItems(128)
                .maximumUnicodeCodePoints(1024)
                .maximumMarkedContentSequences(32)
                .maximumMarkedContentDepth(8)
                .maximumStructureElements(32)
                .maximumStructureItems(64)
                .maximumStructureDepth(8)
                .maximumRoleMappings(16)
                .maximumToUnicodeMappings(64)
                .maximumFontDataEntries(64)
                .build();
    }

    private static void assertWorkNestingFailure(
            WorkflowRequest request,
            DocumentWork<Void> work) throws Exception {
        try {
            new DocumentWorkflow().execute(request, work);
            fail("Expected the shared nesting ceiling to be exceeded");
        } catch (DocumentFailure failure) {
            assertSafeLimitFailure(
                    failure,
                    DocumentFailureCode.NESTING_LIMIT_EXCEEDED,
                    "The workflow nesting-depth limit was exceeded.");
        }
    }

    private static void assertPageCount(
            DocumentSource source,
            WorkflowResourcePolicy policy) throws Exception {
        assertPageCount(new DocumentWorkflow(), source, policy);
    }

    private static void assertPageCount(
            DocumentWorkflow workflow,
            DocumentSource source,
            WorkflowResourcePolicy policy) throws Exception {
        assertEquals(Integer.valueOf(1), workflow.execute(
                request(source, policy),
                session -> session.query(PageCount.INSTANCE)).getResult());
    }

    private static void assertLimit(
            WorkflowRequest request,
            DocumentFailureCode code,
            String diagnostic) throws Exception {
        assertLimit(new DocumentWorkflow(), request, code, diagnostic);
    }

    private static void assertLimit(
            DocumentWorkflow workflow,
            WorkflowRequest request,
            DocumentFailureCode code,
            String diagnostic) throws Exception {
        try {
            workflow.execute(request, session -> null);
            fail("Expected " + code);
        } catch (DocumentFailure failure) {
            assertSafeLimitFailure(failure, code, diagnostic);
        }
    }

    private static void assertNeverRuns(
            WorkflowRequest request,
            DocumentFailureCode code,
            String diagnostic) throws Exception {
        assertNeverRuns(new DocumentWorkflow(), request, code, diagnostic);
    }

    private static void assertNeverRuns(
            DocumentWorkflow workflow,
            WorkflowRequest request,
            DocumentFailureCode code,
            String diagnostic) throws Exception {
        try {
            workflow.execute(request, session -> {
                fail("Caller work must not observe an over-limit document");
                return null;
            });
            fail("Expected " + code);
        } catch (DocumentFailure failure) {
            assertSafeLimitFailure(failure, code, diagnostic);
        }
    }

    private static void assertSafeLimitFailure(
            DocumentFailure failure,
            DocumentFailureCode code,
            String diagnostic) {
        assertEquals(code, failure.getCode());
        if (code == DocumentFailureCode.WORKFLOW_CANCELLED
                || code == DocumentFailureCode.DEADLINE_EXCEEDED) {
            assertEquals(
                    "document.blank.create-publish-reopen",
                    failure.getCapabilityId());
        } else {
            assertEquals(LIMIT_CAPABILITY, failure.getCapabilityId());
        }
        assertEquals(diagnostic, failure.getDiagnostic());
        assertFalse(failure.getDiagnostic().contains("secret"));
        assertFalse(failure.getDiagnostic().contains(".pdf"));
        assertFalse(failure.getDiagnostic().contains("Exception"));
        assertFalse(failure.getDiagnostic().contains("PDFBox"));
        assertEquals(diagnostic, failure.getMessage());
        assertEquals(null, failure.getCause());
    }

    private WorkflowResourcePolicy policyWithInput(long maximum) {
        return policy(
                maximum,
                defaults().getMaximumPages(),
                defaults().getMaximumObjects(),
                defaults().getMaximumNestingDepth(),
                defaults().getMaximumDecompressedBytes(),
                defaults().getMaximumDecodedPixels(),
                defaults().getMaximumOwnedMemoryBytes(),
                defaults().getMaximumTemporaryStorageBytes(),
                defaults().getMaximumElapsedTime(),
                defaults().getMaximumConcurrentWorkflows());
    }

    private WorkflowResourcePolicy policyWithPages(int maximum) {
        WorkflowResourcePolicy value = defaults();
        return policy(value.getMaximumInputBytes(), maximum,
                value.getMaximumObjects(), value.getMaximumNestingDepth(),
                value.getMaximumDecompressedBytes(),
                value.getMaximumDecodedPixels(),
                value.getMaximumOwnedMemoryBytes(),
                value.getMaximumTemporaryStorageBytes(),
                value.getMaximumElapsedTime(),
                value.getMaximumConcurrentWorkflows());
    }

    private WorkflowResourcePolicy policyWithObjects(long maximum) {
        WorkflowResourcePolicy value = defaults();
        return policy(value.getMaximumInputBytes(), value.getMaximumPages(),
                maximum, value.getMaximumNestingDepth(),
                value.getMaximumDecompressedBytes(),
                value.getMaximumDecodedPixels(),
                value.getMaximumOwnedMemoryBytes(),
                value.getMaximumTemporaryStorageBytes(),
                value.getMaximumElapsedTime(),
                value.getMaximumConcurrentWorkflows());
    }

    private WorkflowResourcePolicy policyWithNesting(int maximum) {
        WorkflowResourcePolicy value = defaults();
        return policy(value.getMaximumInputBytes(), value.getMaximumPages(),
                value.getMaximumObjects(), maximum,
                value.getMaximumDecompressedBytes(),
                value.getMaximumDecodedPixels(),
                value.getMaximumOwnedMemoryBytes(),
                value.getMaximumTemporaryStorageBytes(),
                value.getMaximumElapsedTime(),
                value.getMaximumConcurrentWorkflows());
    }

    private WorkflowResourcePolicy policyWithDecompression(long maximum) {
        WorkflowResourcePolicy value = defaults();
        return policy(value.getMaximumInputBytes(), value.getMaximumPages(),
                value.getMaximumObjects(), value.getMaximumNestingDepth(),
                maximum, value.getMaximumDecodedPixels(),
                value.getMaximumOwnedMemoryBytes(),
                value.getMaximumTemporaryStorageBytes(),
                value.getMaximumElapsedTime(),
                value.getMaximumConcurrentWorkflows());
    }

    private WorkflowResourcePolicy policyWithPixels(long maximum) {
        WorkflowResourcePolicy value = defaults();
        return policy(value.getMaximumInputBytes(), value.getMaximumPages(),
                value.getMaximumObjects(), value.getMaximumNestingDepth(),
                value.getMaximumDecompressedBytes(), maximum,
                value.getMaximumOwnedMemoryBytes(),
                value.getMaximumTemporaryStorageBytes(),
                value.getMaximumElapsedTime(),
                value.getMaximumConcurrentWorkflows());
    }

    private WorkflowResourcePolicy policyWithMemory(long maximum) {
        WorkflowResourcePolicy value = defaults();
        return policy(value.getMaximumInputBytes(), value.getMaximumPages(),
                value.getMaximumObjects(), value.getMaximumNestingDepth(),
                value.getMaximumDecompressedBytes(),
                value.getMaximumDecodedPixels(), maximum,
                value.getMaximumTemporaryStorageBytes(),
                value.getMaximumElapsedTime(),
                value.getMaximumConcurrentWorkflows());
    }

    private WorkflowResourcePolicy policyWithTemporaryStorage(long maximum) {
        WorkflowResourcePolicy value = defaults();
        return policy(value.getMaximumInputBytes(), value.getMaximumPages(),
                value.getMaximumObjects(), value.getMaximumNestingDepth(),
                value.getMaximumDecompressedBytes(),
                value.getMaximumDecodedPixels(),
                value.getMaximumOwnedMemoryBytes(), maximum,
                value.getMaximumElapsedTime(),
                value.getMaximumConcurrentWorkflows());
    }

    private WorkflowResourcePolicy policyWithElapsed(Duration maximum) {
        WorkflowResourcePolicy value = defaults();
        return policy(value.getMaximumInputBytes(), value.getMaximumPages(),
                value.getMaximumObjects(), value.getMaximumNestingDepth(),
                value.getMaximumDecompressedBytes(),
                value.getMaximumDecodedPixels(),
                value.getMaximumOwnedMemoryBytes(),
                value.getMaximumTemporaryStorageBytes(), maximum,
                value.getMaximumConcurrentWorkflows());
    }

    private WorkflowResourcePolicy policyWithConcurrency(int maximum) {
        WorkflowResourcePolicy value = defaults();
        return policy(value.getMaximumInputBytes(), value.getMaximumPages(),
                value.getMaximumObjects(), value.getMaximumNestingDepth(),
                value.getMaximumDecompressedBytes(),
                value.getMaximumDecodedPixels(),
                value.getMaximumOwnedMemoryBytes(),
                value.getMaximumTemporaryStorageBytes(),
                value.getMaximumElapsedTime(), maximum);
    }

    private static WorkflowResourcePolicy defaults() {
        return WorkflowResourcePolicy.safeDefaults();
    }

    private static WorkflowResourcePolicy policy(
            long input,
            int pages,
            long objects,
            int nesting,
            long decompression,
            long pixels,
            long memory,
            long temporaryStorage,
            Duration elapsed,
            int concurrency) {
        return WorkflowResourcePolicy.builder()
                .maximumInputBytes(input)
                .maximumPages(pages)
                .maximumObjects(objects)
                .maximumNestingDepth(nesting)
                .maximumDecompressedBytes(decompression)
                .maximumDecodedPixels(pixels)
                .maximumOwnedMemoryBytes(memory)
                .maximumTemporaryStorageBytes(temporaryStorage)
                .maximumElapsedTime(elapsed)
                .maximumConcurrentWorkflows(concurrency)
                .build();
    }

    private Path write(String name, byte[] bytes) throws IOException {
        Path path = temporaryFolder.getRoot().toPath().resolve(name);
        Files.write(path, bytes);
        return path;
    }

    private static byte[] pdf(
            String extraCatalogEntries,
            String[] extraObjects) {
        return pdf(extraCatalogEntries, null, extraObjects);
    }

    private static byte[] pdfWithPageResources(
            String resourceEntries,
            String[] extraObjects) {
        return pdf(null, resourceEntries, extraObjects);
    }

    private static byte[] pdf(
            String extraCatalogEntries,
            String resourceEntries,
            String[] extraObjects) {
        List<String> objects = new ArrayList<String>();
        String extras = extraCatalogEntries == null
                || extraCatalogEntries.isEmpty()
                ? "" : " " + extraCatalogEntries;
        String resources = resourceEntries == null
                || resourceEntries.isEmpty()
                ? "" : " " + resourceEntries;
        objects.add("<< /Type /Catalog /Pages 2 0 R" + extras + " >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R"
                + " /MediaBox [0 0 100 100] /Resources <<"
                + resources + " >> >>");
        for (String object : extraObjects) {
            objects.add(object);
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeAscii(output, "%PDF-1.7\n%âãÏÓ\n");
        List<Integer> offsets = new ArrayList<Integer>();
        for (int index = 0; index < objects.size(); index++) {
            offsets.add(Integer.valueOf(output.size()));
            writeAscii(output, Integer.toString(index + 1));
            writeAscii(output, " 0 obj\n");
            writeAscii(output, objects.get(index));
            writeAscii(output, "\nendobj\n");
        }
        int xref = output.size();
        writeAscii(output, "xref\n0 ");
        writeAscii(output, Integer.toString(objects.size() + 1));
        writeAscii(output, "\n0000000000 65535 f \n");
        for (Integer offset : offsets) {
            writeAscii(output, String.format(
                    java.util.Locale.ROOT,
                    "%010d 00000 n \n",
                    offset));
        }
        writeAscii(output, "trailer\n<< /Size ");
        writeAscii(output, Integer.toString(objects.size() + 1));
        writeAscii(output, " /Root 1 0 R >>\nstartxref\n");
        writeAscii(output, Integer.toString(xref));
        writeAscii(output, "\n%%EOF\n");
        return output.toByteArray();
    }

    private static void writeAscii(
            ByteArrayOutputStream output,
            String value) {
        byte[] bytes = value.getBytes(StandardCharsets.ISO_8859_1);
        output.write(bytes, 0, bytes.length);
    }

    private static void assertDirectoryEmpty(Path directory)
            throws IOException {
        try (java.nio.file.DirectoryStream<Path> entries =
                Files.newDirectoryStream(directory)) {
            assertFalse(entries.iterator().hasNext());
        }
    }

    private static final class CancellingInputStream extends InputStream {

        private final byte[] bytes;
        private final CancellationToken token;
        private boolean delivered;

        private CancellingInputStream(
                byte[] bytes,
                CancellationToken token) {
            this.bytes = bytes;
            this.token = token;
        }

        @Override
        public int read(byte[] target, int offset, int length) {
            if (delivered) {
                return -1;
            }
            int count = Math.min(length, bytes.length);
            System.arraycopy(bytes, 0, target, offset, count);
            delivered = true;
            token.cancel();
            return count;
        }

        @Override
        public int read() {
            throw new AssertionError("Bulk reads are required");
        }
    }

    private static final class AdvancingInputStream extends InputStream {

        private final byte[] bytes;
        private final MutableClock clock;
        private final Duration advance;
        private boolean delivered;

        private AdvancingInputStream(
                byte[] bytes,
                MutableClock clock,
                Duration advance) {
            this.bytes = bytes;
            this.clock = clock;
            this.advance = advance;
        }

        @Override
        public int read(byte[] target, int offset, int length) {
            if (delivered) {
                return -1;
            }
            int count = Math.min(length, bytes.length);
            System.arraycopy(bytes, 0, target, offset, count);
            delivered = true;
            clock.advance(advance);
            return count;
        }

        @Override
        public int read() {
            throw new AssertionError("Bulk reads are required");
        }
    }

    private static final class CancellingOutputStream
            extends ByteArrayOutputStream {

        private final CancellationToken token;
        private boolean cancelled;

        private CancellingOutputStream(CancellationToken token) {
            this.token = token;
        }

        @Override
        public synchronized void write(
                byte[] bytes,
                int offset,
                int length) {
            super.write(bytes, offset, length);
            if (!cancelled) {
                cancelled = true;
                token.cancel();
            }
        }
    }

    private static final class FlushCancellingOutputStream
            extends ByteArrayOutputStream {

        private final CancellationToken token;

        private FlushCancellingOutputStream(CancellationToken token) {
            this.token = token;
        }

        @Override
        public void flush() {
            token.cancel();
        }
    }

    private static final class MutableClock extends Clock {

        private Instant current;
        private final ZoneId zone;

        private MutableClock(Instant current, ZoneId zone) {
            this.current = current;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId requestedZone) {
            return new MutableClock(current, requestedZone);
        }

        @Override
        public synchronized Instant instant() {
            return current;
        }

        private synchronized void advance(Duration duration) {
            current = current.plus(duration);
        }
    }
}
