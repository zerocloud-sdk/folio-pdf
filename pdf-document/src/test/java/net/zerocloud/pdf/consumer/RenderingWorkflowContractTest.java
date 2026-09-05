package net.zerocloud.pdf.consumer;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import net.zerocloud.pdf.*;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.PageObjectReference;
import net.zerocloud.pdf.query.RenderPage;
import net.zerocloud.pdf.query.ExtractImagesAndResources;
import net.zerocloud.pdf.provider.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** T23 public behavior shared unchanged by both Workflow execution profiles. */
@RunWith(Parameterized.class)
public final class RenderingWorkflowContractTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> profiles() {
        return Arrays.asList(new Object[][] {
            {WorkflowExecutionProfile.IN_PROCESS}, {WorkflowExecutionProfile.HARDENED_WORKER}
        });
    }
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final WorkflowExecutionProfile profile;
    public RenderingWorkflowContractTest(WorkflowExecutionProfile profile) { this.profile = profile; }

    @Test public void dimensionsCropRotationAndEarlierCommandsAreObservable() throws Exception {
        new DocumentWorkflow().execute(request().build(), session -> {
            try (RenderedPage page = session.query(RenderPage.version1(1,
                    RenderOptions.builder().dpi(144).scale(1.25).build()))) {
                assertEquals(50, page.getWidth()); assertEquals(25, page.getHeight());
            }
            ObjectReference pageRef = session.query(PageObjectReference.version1(1));
            session.execute(DocumentPatch.builder().setDictionaryEntry(pageRef,
                    PdfName.of("Rotate"), PdfNumber.of(90)).build());
            try (RenderedPage page = session.query(RenderPage.version1(1,
                    RenderOptions.builder().crop(0, 0, 7.5, 4.5).scale(1.5).build()))) {
                assertEquals(6, page.getWidth()); assertEquals(11, page.getHeight());
            }
            session.execute(AddBlankPage.INSTANCE);
            try (RenderedPage page = session.query(RenderPage.version1(2,
                    RenderOptions.builder().crop(0, 0, 8, 9).build()))) {
                assertEquals(8, page.getWidth()); assertEquals(9, page.getHeight());
            }
            return null;
        });
    }

    @Test public void colorBackgroundAlphaAndGrayHaveFixedPixels() throws Exception {
        new DocumentWorkflow().execute(request().build(), session -> {
            BufferedImage rgb = rendered(session, RenderOptions.defaults());
            assertEquals(0xffff0000, rgb.getRGB(3, 3));
            assertEquals(0xffffffff, rgb.getRGB(15, 3));
            BufferedImage blue = rendered(session, RenderOptions.builder().backgroundRgb(0x0000ff).build());
            assertEquals(0xff0000ff, blue.getRGB(15, 3));
            BufferedImage alpha = rendered(session, RenderOptions.builder()
                    .alphaMode(RenderOptions.AlphaMode.PRESERVE).build());
            assertEquals(0, alpha.getRGB(15, 3));
            assertEquals(0xffff0000, alpha.getRGB(3, 3));
            BufferedImage gray = rendered(session, RenderOptions.builder()
                    .colorMode(RenderOptions.ColorMode.GRAY).build());
            assertEquals(0xff4c4c4c, gray.getRGB(3, 3));
            return null;
        });
    }

    @Test public void duplicatePagesStreamInCallerOrderAndCloseAfterConsumption() throws Exception {
        List<Integer> sequence = new ArrayList<Integer>();
        List<RenderedPage> retained = new ArrayList<RenderedPage>();
        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(request().build(), session -> {
            session.execute(AddBlankPage.INSTANCE);
            Rendering.renderPages(session, new int[] {2, 1, 2}, RenderOptions.builder().crop(0, 0, 20, 10).build(), page -> {
                sequence.add(page.getPageNumber()); retained.add(page);
                page.writePngTo(new ByteArrayOutputStream());
            });
            return null;
        });
        assertEquals(Arrays.asList(2, 1, 2), sequence);
        assertEquals(600, outcome.getResourceUsage().getDecodedPixels());
        for (RenderedPage page : retained) {
            assertCode(DocumentFailureCode.RENDER_RESULT_EXPIRED,
                    () -> page.writePngTo(new ByteArrayOutputStream()));
        }
    }

    @Test public void numericAndPageFailuresAreStableAtTheWorkflowSeam() throws Exception {
        new DocumentWorkflow().execute(request().build(), session -> {
            for (int page : new int[] {0, -1, 2, Integer.MAX_VALUE}) {
                assertCode(DocumentFailureCode.PAGE_RANGE_INVALID,
                        () -> session.query(RenderPage.version1(page, RenderOptions.defaults())));
            }
            for (double invalid : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
                assertCode(DocumentFailureCode.RENDER_OPTIONS_INVALID, () -> session.query(
                        RenderPage.version1(1, RenderOptions.builder().dpi(invalid).build())));
                assertCode(DocumentFailureCode.RENDER_OPTIONS_INVALID, () -> session.query(
                        RenderPage.version1(1, RenderOptions.builder().scale(invalid).build())));
            }
            assertCode(DocumentFailureCode.RENDER_OPTIONS_INVALID, () -> session.query(
                    RenderPage.version1(1, RenderOptions.builder().crop(0, 0, 21, 10).build())));
            assertCode(DocumentFailureCode.RENDER_DIMENSIONS_EXCEEDED, () -> session.query(
                    RenderPage.version1(1, RenderOptions.builder().dpi(Double.MAX_VALUE).build())));
            return null;
        });
    }

    @Test public void decodedPixelsUseOneTransactionLedgerAndPoisonAfterFirstExcess() throws Exception {
        WorkflowResourcePolicy exact = policy().maximumDecodedPixels(400).build();
        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(request().resourcePolicy(exact).build(), session -> {
            Rendering.renderPages(session, new int[] {1, 1}, RenderOptions.defaults(), page -> { });
            return null;
        });
        assertEquals(400, outcome.getResourceUsage().getDecodedPixels());
        assertCode(DocumentFailureCode.PIXEL_LIMIT_EXCEEDED, () -> new DocumentWorkflow().execute(
                request().resourcePolicy(exact).build(), session -> {
                    Rendering.renderPages(session, new int[] {1, 1}, RenderOptions.defaults(), page -> { });
                    assertCode(DocumentFailureCode.PIXEL_LIMIT_EXCEEDED, () ->
                            session.query(RenderPage.version1(1, RenderOptions.defaults())));
                    return null;
                }));
    }

    @Test public void consumptionPreservesCallerOwnershipAndReportsPartialFailure() throws Exception {
        new DocumentWorkflow().execute(request().build(), session -> {
            try (RenderedPage page = session.query(RenderPage.version1(1, RenderOptions.defaults()))) {
                final boolean[] closed = {false};
                OutputStream broken = new OutputStream() {
                    @Override public void write(int value) throws IOException { throw new IOException("secret"); }
                    @Override public void close() { closed[0] = true; }
                };
                assertCode(DocumentFailureCode.RENDER_OUTPUT_FAILED, () -> page.writePngTo(broken));
                assertFalse(closed[0]);
                ByteArrayOutputStream good = new ByteArrayOutputStream();
                page.writePngTo(good);
                assertTrue(good.size() > 0);
            }
            return null;
        });
    }

    @Test public void unclosedResultsExpireAtWorkflowEnd() throws Exception {
        RenderedPage result = new DocumentWorkflow().execute(request().build(), session ->
                session.query(RenderPage.version1(1, RenderOptions.defaults()))).getResult();
        assertCode(DocumentFailureCode.RENDER_RESULT_EXPIRED, () -> result.writePngTo(new ByteArrayOutputStream()));
        result.close();
    }

    @Test public void defaultAndRegisteredProvidersExecuteAndReportTheirSelections() throws Exception {
        WorkflowOutcome<Void> defaultOutcome = new DocumentWorkflow().execute(request().build(), session -> {
            rendered(session, RenderOptions.defaults()); return null;
        });
        assertEquals(Rendering.DEFAULT_PROVIDER_ID, defaultOutcome.getProviderSelections().get(0).getProviderId());
        AtomicInteger calls = new AtomicInteger();
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .provider(provider("test.renderer", ProviderExecutionMode.IN_PROCESS,
                        ProviderAvailability.AVAILABLE, calls)).build();
        WorkflowOutcome<Void> replaced = new DocumentWorkflow(environment).execute(request().build(), session -> {
            BufferedImage image = rendered(session, RenderOptions.defaults());
            assertEquals(0xff0000ff, image.getRGB(3, 3));
            return null;
        });
        assertEquals(1, calls.get());
        assertEquals("test.renderer", replaced.getProviderSelections().get(0).getProviderId());
        assertTrue(replaced.getDiagnostics().contains("FONT_SUBSTITUTED"));
        assertEquals(200, replaced.getResourceUsage().getDecodedPixels());
    }

    @Test public void explicitProviderAvailabilityAndDisclosureNeverFallBack() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .provider(provider("test.remote", ProviderExecutionMode.REMOTE,
                        ProviderAvailability.AVAILABLE, calls))
                .provider(provider("test.unavailable", ProviderExecutionMode.IN_PROCESS,
                        ProviderAvailability.UNAVAILABLE, calls)).build();
        DocumentWorkflow workflow = new DocumentWorkflow(environment);
        assertCode(DocumentFailureCode.REMOTE_DISCLOSURE_NOT_AUTHORIZED, () -> workflow.execute(
                request().providerPreference(ProviderPreference.prefer(Rendering.CAPABILITY_ID, "test.remote"))
                        .build(), session -> { fail("Unauthorized callback"); return null; }));
        assertCode(DocumentFailureCode.CAPABILITY_PROVIDER_UNAVAILABLE, () -> workflow.execute(
                request().providerPreference(ProviderPreference.prefer(Rendering.CAPABILITY_ID, "test.unavailable"))
                        .build(), session -> { fail("Unavailable callback"); return null; }));
        assertCode(DocumentFailureCode.CAPABILITY_PROVIDER_NOT_FOUND, () -> workflow.execute(
                request().providerPreference(ProviderPreference.prefer(Rendering.CAPABILITY_ID, "test.missing"))
                        .build(), session -> { fail("Missing callback"); return null; }));
        assertEquals(0, calls.get());
        workflow.execute(request().build(), session -> {
            assertEquals(0xffff0000, rendered(session, RenderOptions.defaults()).getRGB(3, 3));
            return null;
        });
        assertEquals(0, calls.get());
        workflow.execute(request().providerPreference(ProviderPreference.prefer(Rendering.CAPABILITY_ID, "test.remote"))
                .authorizeRemoteDisclosure(Rendering.CAPABILITY_ID).build(), session -> {
                    assertEquals(0xff0000ff, rendered(session, RenderOptions.defaults()).getRGB(3, 3));
                    return null;
                });
        assertEquals(1, calls.get());
    }

    private static CapabilityProvider provider(String id, ProviderExecutionMode mode,
            ProviderAvailability availability, AtomicInteger calls) {
        ProviderMetadata metadata = ProviderMetadata.builder(id, "test-1")
                .capability(Rendering.CAPABILITY_ID).availability(availability).executionMode(mode)
                .distribution(mode == ProviderExecutionMode.REMOTE
                        ? ProviderDistribution.REMOTE_SERVICE : ProviderDistribution.CALLER_SUPPLIED)
                .engineLicense("Apache-2.0", "Apache License 2.0")
                .limits(ProviderLimits.bounded(1 << 20, 1 << 20, Duration.ofSeconds(30))).build();
        return new CapabilityProvider(metadata) {
            @Override protected ProviderResult perform(ProviderRequest request) throws ProviderFailure {
                calls.incrementAndGet();
                assertEquals(mode == ProviderExecutionMode.REMOTE, request.isRemoteDisclosureAuthorized());
                try {
                    DataInputStream input = new DataInputStream(new ByteArrayInputStream(request.getInput()));
                    assertEquals(0x46525131, input.readInt());
                    int width = input.readInt(); int height = input.readInt();
                    assertEquals(1d, input.readDouble(), 0d);
                    assertEquals(0, input.readInt());
                    int length = input.readInt();
                    byte[] pdf = new byte[length]; input.readFully(pdf);
                    assertEquals(-1, input.read());
                    assertTrue(new String(pdf, 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF-"));
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    DataOutputStream output = new DataOutputStream(bytes);
                    output.writeInt(0x46525331); output.writeInt(width); output.writeInt(height);
                    output.writeInt(1 << RenderDiagnostic.FONT_SUBSTITUTED.ordinal());
                    for (int i = 0; i < width * height; i++) { output.writeInt(0x0000ffff); }
                    return ProviderResult.of(bytes.toByteArray());
                } catch (IOException failure) {
                    throw ProviderFailure.forProvider(ProviderFailureCode.MALFORMED_OUTPUT, id, Rendering.CAPABILITY_ID);
                }
            }
        };
    }

    @Test public void annotationsUseVisibleExistingAppearancesAndCanBeHidden() throws Exception {
        String appearance = "0 1 0 rg 0 0 10 10 re f\n";
        for (int flags : new int[] {0, 2, 32}) {
            byte[] bytes = fixture("/Annots [5 0 R]", "<< >>", "",
                    "<< /Type /Annot /Subtype /Square /Rect [10 0 20 10] /F " + flags + " /AP << /N 6 0 R >> >>",
                    "<< /Type /XObject /Subtype /Form /BBox [0 0 10 10] /Resources << >> /Length "
                            + appearance.length() + " >>\nstream\n" + appearance + "endstream");
            new DocumentWorkflow().execute(request(bytes).build(), session -> {
                BufferedImage show = rendered(session, RenderOptions.defaults());
                assertEquals(flags == 0 ? 0xff00ff00 : 0xffffffff, show.getRGB(15, 3));
                BufferedImage hide = rendered(session, RenderOptions.builder()
                        .annotationMode(RenderOptions.AnnotationMode.HIDE).build());
                assertEquals(0xffffffff, hide.getRGB(15, 3));
                return null;
            });
        }
    }

    @Test public void missingAppearanceAndSubstitutedFontsAreSafeObservableDiagnostics() throws Exception {
        byte[] bytes = fixture("/Annots [5 0 R]", "<< /Font << /F1 6 0 R >> >>", "BT /F1 8 Tf 1 1 Td (A) Tj ET\n",
                "<< /Type /Annot /Subtype /Text /Rect [10 0 20 10] /Contents (private-note) >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(request(bytes).build(), session -> {
            try (RenderedPage page = session.query(RenderPage.version1(1, RenderOptions.defaults()))) {
                assertTrue(page.getDiagnostics().contains(RenderDiagnostic.FONT_SUBSTITUTED));
                assertTrue(page.getDiagnostics().contains(RenderDiagnostic.ANNOTATION_APPEARANCE_MISSING));
            }
            return null;
        });
        assertTrue(outcome.getDiagnostics().contains("FONT_SUBSTITUTED"));
        assertFalse(outcome.getDiagnostics().toString().contains("private-note"));
        assertFalse(outcome.getDiagnostics().toString().contains("Helvetica"));
    }

    @Test public void rasterMemoryBoundaryIsInclusiveAndReleasedBetweenPages() throws Exception {
        RenderOptions options = RenderOptions.builder().scale(20).build();
        DocumentWorkflow workflow = new DocumentWorkflow(WorkflowEnvironment.builder()
                .temporaryDirectory(temporary.newFolder("raster-memory").toPath()).build());
        WorkflowOutcome<Void> baseline = workflow.execute(request().build(), session -> {
            Rendering.renderPages(session, new int[] {1, 1, 1}, options, page -> { }); return null;
        });
        long peak = baseline.getResourceUsage().getPeakOwnedMemoryBytes();
        long rasterBytes = 4L * 400 * 200;
        assertTrue(peak >= rasterBytes);
        assertTrue("Closed pages must release raster storage", peak < 2L * rasterBytes);
        if (profile == WorkflowExecutionProfile.IN_PROCESS) {
            workflow.execute(request().resourcePolicy(policy().maximumOwnedMemoryBytes(peak).build()).build(),
                    session -> { Rendering.renderPages(session, new int[] {1, 1, 1}, options, page -> { }); return null; });
            assertCode(DocumentFailureCode.MEMORY_LIMIT_EXCEEDED, () -> workflow.execute(
                    request().resourcePolicy(policy().maximumOwnedMemoryBytes(peak - 1).build()).build(), session -> {
                        Rendering.renderPages(session, new int[] {1, 1, 1}, options, page -> { }); return null;
                    }));
        } else {
            workflow.execute(request().resourcePolicy(policy().maximumOwnedMemoryBytes(peak + 4096).build()).build(),
                    session -> { Rendering.renderPages(session, new int[] {1, 1, 1}, options, page -> { }); return null; });
            assertCode(DocumentFailureCode.MEMORY_LIMIT_EXCEEDED, () -> workflow.execute(
                    request().resourcePolicy(policy().maximumOwnedMemoryBytes(rasterBytes - 1).build()).build(), session -> {
                        Rendering.renderPages(session, new int[] {1, 1, 1}, options, page -> { }); return null;
                    }));
        }
    }

    @Test public void temporaryStorageBoundaryIncludesRetainedPagesAndStreamingReleasesThem() throws Exception {
        DocumentWork<Void> holdThree = session -> {
            try (RenderedPage a = session.query(RenderPage.version1(1, RenderOptions.defaults()));
                    RenderedPage b = session.query(RenderPage.version1(1, RenderOptions.defaults()));
                    RenderedPage c = session.query(RenderPage.version1(1, RenderOptions.defaults()))) {
                a.writePngTo(new ByteArrayOutputStream()); b.writePngTo(new ByteArrayOutputStream());
                c.writePngTo(new ByteArrayOutputStream());
            }
            return null;
        };
        long peak = new DocumentWorkflow().execute(request().build(), holdThree)
                .getResourceUsage().getPeakTemporaryStorageBytes();
        new DocumentWorkflow().execute(request().resourcePolicy(policy().maximumTemporaryStorageBytes(peak).build()).build(), holdThree);
        assertCode(DocumentFailureCode.TEMPORARY_STORAGE_LIMIT_EXCEEDED, () -> new DocumentWorkflow().execute(
                request().resourcePolicy(policy().maximumTemporaryStorageBytes(peak - 1).build()).build(), holdThree));
        new DocumentWorkflow().execute(request().resourcePolicy(policy().maximumTemporaryStorageBytes(peak - 1).build()).build(),
                session -> { Rendering.renderPages(session, new int[] {1, 1, 1, 1, 1}, RenderOptions.defaults(), page -> { }); return null; });
    }

    @Test public void cancellationStopsStreamingAndAbortsWorkflowPublication() throws Exception {
        CancellationToken cancel = CancellationToken.create();
        AtomicInteger consumed = new AtomicInteger();
        ByteArrayOutputStream published = new ByteArrayOutputStream();
        try {
            new DocumentWorkflow().execute(request().cancellationToken(cancel)
                    .target("pdf", PublicationTarget.stream(published)).build(), session -> {
                        Rendering.renderPages(session, new int[] {1, 1}, RenderOptions.defaults(), page -> {
                            page.writePngTo(new ByteArrayOutputStream());
                            consumed.incrementAndGet(); cancel.cancel();
                        });
                        return null;
                    });
            fail("Expected cancellation");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.WORKFLOW_CANCELLED, failure.getCode());
            assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(0).getStatus());
        }
        assertEquals(1, consumed.get()); assertEquals(0, published.size());
    }

    @Test public void elapsedTimeAndAbsoluteDeadlineStopAtTheNextRenderingCheckpoint() throws Exception {
        for (boolean deadline : new boolean[] {true, false}) {
            MutableClock clock = new MutableClock();
            WorkflowEnvironment environment = WorkflowEnvironment.builder().clock(clock).build();
            WorkflowRequest.Builder request = request().resourcePolicy(
                    policy().maximumElapsedTime(Duration.ofSeconds(10)).build());
            if (deadline) { request.deadline(clock.instant().plusSeconds(10)); }
            assertCode(deadline ? DocumentFailureCode.DEADLINE_EXCEEDED : DocumentFailureCode.ELAPSED_TIME_LIMIT_EXCEEDED,
                    () -> new DocumentWorkflow(environment).execute(request.build(), session -> {
                        Rendering.renderPages(session, new int[] {1, 1}, RenderOptions.defaults(), page -> {
                            clock.now = clock.now.plusSeconds(deadline ? 10 : 11);
                        });
                        return null;
                    }));
        }
    }

    @Test public void renderingSharesEnvironmentConcurrencyAdmission() throws Exception {
        WorkflowEnvironment environment = WorkflowEnvironment.builder().defaultResourcePolicy(
                policy().maximumConcurrentWorkflows(1).build()).build();
        DocumentWorkflow workflow = new DocumentWorkflow(environment);
        workflow.execute(request().build(), session -> {
            Rendering.renderPages(session, new int[] {1}, RenderOptions.defaults(), page -> {
                assertCode(DocumentFailureCode.CONCURRENCY_LIMIT_EXCEEDED,
                        () -> workflow.execute(request().build(), nested -> { fail("Concurrent admission"); return null; }));
            });
            return null;
        });
        workflow.execute(request().build(), session -> { rendered(session, RenderOptions.defaults()); return null; });
    }


    @Test public void effectiveBoxesUserUnitAndSmallRasterRoundingAreExplicit() throws Exception {
        byte[] bytes = fixture("/CropBox [5 2 15 8] /UserUnit 2", "<< >>", "1 0 0 rg 0 0 10 10 re f\n");
        new DocumentWorkflow().execute(request(bytes).build(), session -> {
            BufferedImage crop = rendered(session, RenderOptions.defaults());
            assertEquals(20, crop.getWidth()); assertEquals(12, crop.getHeight());
            assertEquals(0xffff0000, crop.getRGB(2, 2));
            assertEquals(0xffffffff, crop.getRGB(17, 2));
            BufferedImage media = rendered(session, RenderOptions.builder().pageBox(RenderOptions.PageBox.MEDIA).build());
            assertEquals(40, media.getWidth()); assertEquals(20, media.getHeight());
            BufferedImage tiny = rendered(session, RenderOptions.builder().scale(.01).build());
            assertEquals(1, tiny.getWidth()); assertEquals(1, tiny.getHeight());
            for (RenderOptions invalid : Arrays.asList(
                    RenderOptions.builder().crop(Double.NaN, 2, 1, 1).build(),
                    RenderOptions.builder().crop(5, 2, Double.POSITIVE_INFINITY, 1).build(),
                    RenderOptions.builder().crop(5, 2, 0, 1).build(),
                    RenderOptions.builder().crop(5, 2, -1, 1).build(),
                    RenderOptions.builder().crop(4, 2, 1, 1).build(),
                    RenderOptions.builder().backgroundRgb(-1).build())) {
                assertCode(DocumentFailureCode.RENDER_OPTIONS_INVALID,
                        () -> session.query(RenderPage.version1(1, invalid)));
            }
            return null;
        });
    }

    @Test public void fractionalAlphaIsCompositedBeforeGrayConversion() throws Exception {
        byte[] bytes = fixture("", "<< /ExtGState << /Half << /ca 0.5 >> >> >>",
                "/Half gs 1 0 0 rg 0 0 20 10 re f\n");
        new DocumentWorkflow().execute(request(bytes).build(), session -> {
            assertEquals(0x80ff0000, rendered(session, RenderOptions.builder()
                    .alphaMode(RenderOptions.AlphaMode.PRESERVE).build()).getRGB(3, 3));
            assertEquals(0xff80007f, rendered(session, RenderOptions.builder()
                    .backgroundRgb(0x0000ff).build()).getRGB(3, 3));
            assertEquals(0x804c4c4c, rendered(session, RenderOptions.builder()
                    .alphaMode(RenderOptions.AlphaMode.PRESERVE).colorMode(RenderOptions.ColorMode.GRAY)
                    .backgroundRgb(0x123456).build()).getRGB(3, 3));
            assertEquals(0xff353535, rendered(session, RenderOptions.builder()
                    .backgroundRgb(0x0000ff).colorMode(RenderOptions.ColorMode.GRAY).build()).getRGB(3, 3));
            return null;
        });
    }

    @Test public void resultsExpireBeforeCompletionProgressAndConfineConsumptionToCallerThread() throws Exception {
        AtomicReference<RenderedPage> retained = new AtomicReference<RenderedPage>();
        AtomicBoolean observed = new AtomicBoolean();
        new DocumentWorkflow().execute(request().progressListener(phase -> {
            if (phase == WorkflowProgressPhase.WORK_COMPLETED) {
                try { retained.get().writePngTo(new ByteArrayOutputStream()); fail("Live result after callback"); }
                catch (DocumentFailure failure) { assertEquals(DocumentFailureCode.RENDER_RESULT_EXPIRED, failure.getCode()); }
                observed.set(true);
            }
        }).build(), session -> {
            RenderedPage page = session.query(RenderPage.version1(1, RenderOptions.defaults()));
            retained.set(page);
            AtomicReference<Throwable> rejected = new AtomicReference<Throwable>();
            Thread thread = new Thread(() -> {
                try { page.writePngTo(new ByteArrayOutputStream()); }
                catch (Throwable failure) { rejected.set(failure); }
            });
            thread.start();
            try { thread.join(5000); } catch (InterruptedException failure) { throw new AssertionError(failure); }
            assertTrue(rejected.get() instanceof IllegalStateException);
            page.writePngTo(new ByteArrayOutputStream());
            return null;
        });
        assertTrue(observed.get());
    }

    @Test public void callbackRuntimeAndRenderingFailureCleanStagingAndLeavePathsUnpublished() throws Exception {
        Path staging = temporary.newFolder("staging").toPath();
        Path path = temporary.newFile("existing.pdf").toPath();
        byte[] sentinel = "untouched".getBytes(StandardCharsets.US_ASCII);
        Files.write(path, sentinel);
        DocumentWorkflow workflow = new DocumentWorkflow(WorkflowEnvironment.builder().temporaryDirectory(staging).build());
        RuntimeException marker = new IllegalStateException("caller marker");
        try {
            workflow.execute(request().target("pdf", PublicationTarget.path(path)).build(), session -> {
                RenderedPage page = session.query(RenderPage.version1(1, RenderOptions.defaults()));
                page.writePngTo(new OutputStream() {
                    @Override public void write(int value) { throw marker; }
                });
                return null;
            });
            fail("Expected caller runtime");
        } catch (RuntimeException failure) { assertSame(marker, failure); }
        assertArrayEquals(sentinel, Files.readAllBytes(path));
        try (java.util.stream.Stream<Path> children = Files.list(staging)) { assertEquals(0, children.count()); }
        try {
            workflow.execute(request().target("pdf", PublicationTarget.path(path)).build(), session -> {
                session.query(RenderPage.version1(1, RenderOptions.defaults()));
                session.query(RenderPage.version1(0, RenderOptions.defaults()));
                return null;
            });
            fail("Expected page failure");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.PAGE_RANGE_INVALID, failure.getCode());
            assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(0).getStatus());
        }
        assertArrayEquals(sentinel, Files.readAllBytes(path));
        try (java.util.stream.Stream<Path> children = Files.list(staging)) { assertEquals(0, children.count()); }
    }

    @Test public void pdfPublicationKeepsOrderedPathAndPartialStreamReceiptsAfterPngConsumption() throws Exception {
        Path path = temporary.getRoot().toPath().resolve("published.pdf");
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ByteArrayOutputStream later = new ByteArrayOutputStream();
        AtomicInteger writes = new AtomicInteger();
        AtomicBoolean closed = new AtomicBoolean();
        OutputStream broken = new OutputStream() {
            @Override public void write(int value) throws IOException {
                if (writes.incrementAndGet() > 16) { throw new IOException("caller stream"); }
            }
            @Override public void close() { closed.set(true); }
        };
        try {
            new DocumentWorkflow().execute(request().target("path", PublicationTarget.path(path))
                    .target("broken", PublicationTarget.stream(broken)).target("later", PublicationTarget.stream(later))
                    .build(), session -> {
                        try (RenderedPage page = session.query(RenderPage.version1(1, RenderOptions.defaults()))) {
                            page.writePngTo(png);
                        }
                        return null;
                    });
            fail("Expected publication failure");
        } catch (DocumentFailure failure) {
            assertEquals(PublicationStatus.COMMITTED, failure.getPublicationReceipts().get(0).getStatus());
            assertEquals(PublicationStatus.FAILED, failure.getPublicationReceipts().get(1).getStatus());
            assertTrue(failure.getPublicationReceipts().get(1).isPartialOutputPossible());
            assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(2).getStatus());
        }
        assertFalse(closed.get()); assertEquals(0, later.size());
        assertNotNull(ImageIO.read(new ByteArrayInputStream(png.toByteArray())));
        new DocumentWorkflow().execute(request(Files.readAllBytes(path)).build(), session -> {
            assertEquals(20, rendered(session, RenderOptions.defaults()).getWidth()); return null;
        });
    }

    @Test public void imagePixelsAndLargeResultTransferShareTheRenderingLedger() throws Exception {
        StringBuilder samples = new StringBuilder();
        java.util.Random random = new java.util.Random(23);
        for (int i = 0; i < 64 * 64 * 3; i++) { samples.append(String.format(Locale.ROOT, "%02x", random.nextInt(256))); }
        samples.append('>');
        byte[] bytes = fixture("", "<< /XObject << /I 5 0 R >> >>", "20 0 0 10 0 0 cm /I Do\n",
                "<< /Type /XObject /Subtype /Image /Width 64 /Height 64 /ColorSpace /DeviceRGB /BitsPerComponent 8"
                + " /Filter /ASCIIHexDecode /Length " + samples.length() + " >>\nstream\n" + samples + "\nendstream");
        WorkflowEnvironment environment = WorkflowEnvironment.builder().hardenedWorkerSettings(
                HardenedWorkerSettings.builder().maximumMessageBytes(2048).maximumHeapBytes(512L << 20).build()).build();
        RenderOptions options = RenderOptions.builder().scale(10).build();
        WorkflowOutcome<Void> outcome = new DocumentWorkflow(environment).execute(request(bytes).build(), session -> {
            Rendering.renderPages(session, new int[] {1, 1}, options, page -> {
                ByteArrayOutputStream png = new ByteArrayOutputStream(); page.writePngTo(png);
                assertTrue("PNG crosses multiple physical frames", png.size() > 2048);
                try {
                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(png.toByteArray()));
                    assertEquals(200, image.getWidth()); assertEquals(100, image.getHeight());
                } catch (IOException failure) { throw new AssertionError(failure); }
            });
            return null;
        });
        assertEquals(4096 + 2 * 200 * 100, outcome.getResourceUsage().getDecodedPixels());
    }

    @Test public void providerByteLimitsAndMalformedRepliesAreStable() throws Exception {
        for (int variant = 0; variant < 3; variant++) {
            AtomicInteger calls = new AtomicInteger();
            final int mode = variant;
            ProviderMetadata metadata = ProviderMetadata.builder("test.invalid", "1")
                    .capability(Rendering.CAPABILITY_ID).availability(ProviderAvailability.AVAILABLE)
                    .executionMode(ProviderExecutionMode.IN_PROCESS).distribution(ProviderDistribution.CALLER_SUPPLIED)
                    .engineLicense("Apache-2.0", "Apache License 2.0")
                    .limits(ProviderLimits.bounded(mode == 0 ? 27 : 1 << 20, mode == 1 ? 16 : 1 << 20,
                            Duration.ofSeconds(30))).build();
            CapabilityProvider provider = new CapabilityProvider(metadata) {
                @Override protected ProviderResult perform(ProviderRequest request) {
                    calls.incrementAndGet(); return ProviderResult.of(new byte[816]);
                }
            };
            DocumentWorkflow workflow = new DocumentWorkflow(WorkflowEnvironment.builder().provider(provider).build());
            assertCode(DocumentFailureCode.CAPABILITY_PROVIDER_FAILED, () -> workflow.execute(request().build(), session -> {
                session.query(RenderPage.version1(1, RenderOptions.defaults())); return null;
            }));
            assertEquals(mode == 2 ? 1 : 0, calls.get());
        }
    }


    @Test public void executedJpegAndMissingType3GlyphReportClosedDiagnostics() throws Exception {
        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(source, "JPEG", encoded));
        StringBuilder hex = new StringBuilder();
        for (byte b : encoded.toByteArray()) { hex.append(String.format(Locale.ROOT, "%02x", b & 255)); }
        hex.append('>');
        byte[] bytes = fixture("", "<< /XObject << /I 5 0 R >> /Font << /F 6 0 R >> >>",
                "q 2 0 0 2 0 0 cm /I Do Q BT /F 8 Tf (A) Tj ET\n",
                "<< /Type /XObject /Subtype /Image /Width 2 /Height 2 /ColorSpace /DeviceRGB /BitsPerComponent 8"
                        + " /Filter [/ASCIIHexDecode /DCTDecode] /Length " + hex.length() + " >>\nstream\n" + hex + "\nendstream",
                "<< /Type /Font /Subtype /Type3 /FontBBox [0 0 500 500] /FontMatrix [.001 0 0 .001 0 0]"
                        + " /CharProcs << >> /Encoding << /Type /Encoding /Differences [65 /A] >>"
                        + " /FirstChar 65 /LastChar 65 /Widths [500] /Resources << >> >>");
        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(request(bytes).build(), session -> {
            try (RenderedPage page = session.query(RenderPage.version1(1, RenderOptions.defaults()))) {
                assertTrue(page.getDiagnostics().contains(RenderDiagnostic.PLATFORM_IMAGE_CODEC));
                assertTrue(page.getDiagnostics().contains(RenderDiagnostic.GLYPH_SUBSTITUTED));
            }
            return null;
        });
        assertTrue(outcome.getDiagnostics().contains("PLATFORM_IMAGE_CODEC"));
        assertTrue(outcome.getDiagnostics().contains("GLYPH_SUBSTITUTED"));
    }

    @Test public void noRotateTransparencyCannotSilentlyRegenerateAnAppearance() throws Exception {
        String ap = "/G Do\n";
        String green = "0 1 0 rg 0 0 10 10 re f\n";
        byte[] bytes = fixture("/Rotate 90 /Annots [5 0 R]", "<< >>", "",
                "<< /Type /Annot /Subtype /Square /Rect [10 0 20 10] /F 16 /AP << /N 6 0 R >> >>",
                "<< /Type /XObject /Subtype /Form /BBox [0 0 10 10] /Resources << /XObject << /G 7 0 R >> >>"
                        + " /Length " + ap.length() + " >>\nstream\n" + ap + "endstream",
                "<< /Type /XObject /Subtype /Form /BBox [0 0 10 10] /Group << /S /Transparency >> /Resources << >>"
                        + " /Length " + green.length() + " >>\nstream\n" + green + "endstream");
        new DocumentWorkflow().execute(request(bytes).build(), session -> {
            assertCode(DocumentFailureCode.RENDER_FAILED,
                    () -> session.query(RenderPage.version1(1, RenderOptions.defaults())));
            ObjectReference pageRef = session.query(PageObjectReference.version1(1));
            session.execute(DocumentPatch.builder().setDictionaryEntry(pageRef,
                    PdfName.of("Rotate"), PdfNumber.of(0)).build());
            assertEquals(0xff00ff00, rendered(session, RenderOptions.defaults()).getRGB(15, 3));
            return null;
        });
    }

    @Test public void directAndSoftMaskGroupsFailEvenWhenPdfBoxLoggingIsOff()
            throws Exception {
        byte[] direct = fixture(
                "",
                "<< /XObject << /G 5 0 R >> >>",
                "/G Do\n",
                "<< /Type /XObject /Subtype /Form /Group << /S /Transparency >>"
                        + " /Resources << >> /Length 0 >>\nstream\n\nendstream");
        byte[] softMask = fixture(
                "",
                "<< /ExtGState << /GS 5 0 R >> >>",
                "/GS gs 1 0 0 rg 0 0 20 10 re f\n",
                "<< /Type /ExtGState /SMask << /S /Alpha /G 6 0 R >> >>",
                "<< /Type /XObject /Subtype /Form /Group << /S /Transparency >>"
                        + " /Resources << >> /Length 0 >>\nstream\n\nendstream");
        java.util.logging.Logger logger =
                java.util.logging.Logger.getLogger("org.apache.pdfbox");
        java.util.logging.Level previous = logger.getLevel();
        logger.setLevel(java.util.logging.Level.OFF);
        try {
            for (byte[] malformed : Arrays.asList(direct, softMask)) {
                assertCode(DocumentFailureCode.RENDER_FAILED,
                        () -> new DocumentWorkflow().execute(
                                request(malformed).build(),
                                session -> {
                                    rendered(session, RenderOptions.defaults());
                                    return null;
                                }));
            }
        } finally {
            logger.setLevel(previous);
        }
    }


    @Test public void inlinePixelsAndEveryFilterOutputAreAdmittedBeforeDecoding() throws Exception {
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < 30000; i++) { hex.append("00"); }
        byte[] bytes = fixture("", "<< >>", "BI /W 100 /H 100 /CS /RGB /BPC 8 /F /AHx ID\n" + hex + ">\nEI\n");
        DocumentWork<Void> render = session -> {
            Rendering.renderPages(session, new int[] {1, 1}, RenderOptions.defaults(), page -> { }); return null;
        };
        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(request(bytes).resourcePolicy(policy()
                .maximumDecodedPixels(20400).maximumDecompressedBytes(60000).build()).build(), render);
        assertEquals(20400, outcome.getResourceUsage().getDecodedPixels());
        assertEquals(60000, outcome.getResourceUsage().getDecompressedBytes());
        assertCode(DocumentFailureCode.PIXEL_LIMIT_EXCEEDED, () -> new DocumentWorkflow().execute(
                request(bytes).resourcePolicy(policy().maximumDecodedPixels(20399).build()).build(), render));
        assertCode(DocumentFailureCode.DECOMPRESSION_LIMIT_EXCEEDED, () -> new DocumentWorkflow().execute(
                request(bytes).resourcePolicy(policy().maximumDecompressedBytes(59999).build()).build(), render));
    }

    @Test public void cyclicMasksAndMismatchedInlineCodecHeadersFailSafely() throws Exception {
        byte[] cycle = fixture("", "<< /XObject << /I 5 0 R >> >>", "/I Do\n",
                "<< /Type /XObject /Subtype /Image /Width 1 /Height 1 /ColorSpace /DeviceGray /BitsPerComponent 8"
                        + " /SMask 5 0 R /Filter /ASCIIHexDecode /Length 3 >>\nstream\n00>\nendstream");
        assertCode(DocumentFailureCode.RENDER_FAILED, () -> new DocumentWorkflow().execute(request(cycle).build(), session -> {
            rendered(session, RenderOptions.defaults()); return null;
        }));
        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(source, "JPEG", jpeg));
        StringBuilder hex = new StringBuilder();
        for (byte value : jpeg.toByteArray()) { hex.append(String.format(Locale.ROOT, "%02x", value & 255)); }
        byte[] mismatch = fixture("", "<< >>", "BI /W 1 /H 1 /CS /RGB /BPC 8 /F [/AHx /DCT] ID\n" + hex + ">\nEI\n");
        assertCode(DocumentFailureCode.RENDER_FAILED, () -> new DocumentWorkflow().execute(request(mismatch).build(), session -> {
            rendered(session, RenderOptions.defaults()); return null;
        }));
        byte[] resourceMismatch = fixture("", "<< /XObject << /I 5 0 R >> >>", "/I Do\n",
                "<< /Type /XObject /Subtype /Image /Width 1 /Height 1 /ColorSpace /DeviceRGB /BitsPerComponent 8"
                        + " /Filter [/ASCIIHexDecode /DCTDecode] /Length " + (hex.length() + 1)
                        + " >>\nstream\n" + hex + ">\nendstream");
        assertCode(DocumentFailureCode.DECOMPRESSION_LIMIT_EXCEEDED,
                () -> new DocumentWorkflow().execute(
                request(resourceMismatch).resourcePolicy(policy().maximumDecompressedBytes(0).build()).build(), session -> {
                    rendered(session, RenderOptions.defaults()); return null;
                }));
        assertCode(DocumentFailureCode.RENDER_FAILED,
                () -> new DocumentWorkflow().execute(
                        request(resourceMismatch).build(),
                        session -> {
                            rendered(session, RenderOptions.defaults());
                            return null;
                        }));
    }

    @Test public void malformedUnusedPlatformImageKeepsFormatFailureSemantics()
            throws Exception {
        byte[] unused = fixture(
                "",
                "<< /XObject << /I 5 0 R >> >>",
                "",
                "<< /Type /XObject /Subtype /Image /Height 1"
                        + " /ColorSpace /DeviceGray /BitsPerComponent 8"
                        + " /Filter /DCTDecode /Length 0 >>\nstream\n\nendstream");
        new DocumentWorkflow().execute(request(unused).build(), session -> {
            try (RenderedPage page = session.query(
                    RenderPage.version1(1, RenderOptions.defaults()))) {
                assertEquals(20, page.getWidth());
            }
            return null;
        });

        byte[] used = fixture(
                "",
                "<< /XObject << /I 5 0 R >> >>",
                "/I Do\n",
                "<< /Type /XObject /Subtype /Image /Height 1"
                        + " /ColorSpace /DeviceGray /BitsPerComponent 8"
                        + " /Filter /DCTDecode /Length 0 >>\nstream\n\nendstream");
        assertCode(DocumentFailureCode.RENDER_FAILED,
                () -> new DocumentWorkflow().execute(
                        request(used).build(),
                        session -> {
                            rendered(session, RenderOptions.defaults());
                            return null;
                        }));
    }

    @Test public void correctedPlatformImageInvalidatesItsRejectedHeaderState()
            throws Exception {
        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(source, "JPEG", jpeg));
        StringBuilder hex = new StringBuilder();
        for (byte value : jpeg.toByteArray()) {
            hex.append(String.format(Locale.ROOT, "%02x", value & 255));
        }
        hex.append('>');
        byte[] bytes = fixture(
                "",
                "<< /XObject << /I 5 0 R >> >>",
                "/I Do\n",
                "<< /Type /XObject /Subtype /Image /Width 1 /Height 2"
                        + " /ColorSpace /DeviceRGB /BitsPerComponent 8"
                        + " /Filter [/ASCIIHexDecode /DCTDecode] /Length "
                        + hex.length() + " >>\nstream\n" + hex
                        + "\nendstream");
        ResourceExtractionLimits limits = ResourceExtractionLimits.builder()
                .maximumPages(10)
                .maximumPageTreeNodes(100)
                .maximumTraversedResourceValues(1000)
                .maximumResourceTraversalDepth(32)
                .maximumDecodedPixels(1000)
                .maximumDecompressedBytes(1 << 20)
                .maximumReturnedBytes(1 << 20)
                .build();
        assertCode(DocumentFailureCode.DECOMPRESSION_LIMIT_EXCEEDED,
                () -> new DocumentWorkflow().execute(
                        request(bytes).resourcePolicy(policy()
                                .maximumDecompressedBytes(jpeg.size())
                                .build()).build(),
                        session -> {
                            ImageResource original = session.query(
                                    ExtractImagesAndResources.version1(
                                            limits,
                                            ImageByteAccess.NONE))
                                    .getImages().get(0);
                            session.execute(DocumentPatch.builder()
                                    .setDictionaryEntry(
                                            original.getObjectReference().get(),
                                            PdfName.of("Width"),
                                            PdfNumber.of(2))
                                    .build());
                            return null;
                        }));
        new DocumentWorkflow().execute(request(bytes).build(), session -> {
            ImageResource original = session.query(
                    ExtractImagesAndResources.version1(
                            limits,
                            ImageByteAccess.NONE))
                    .getImages().get(0);
            session.execute(DocumentPatch.builder()
                    .setDictionaryEntry(
                            original.getObjectReference().get(),
                            PdfName.of("Width"),
                            PdfNumber.of(2))
                    .build());
            ImageResource corrected = session.query(
                    ExtractImagesAndResources.version1(
                            limits,
                            ImageByteAccess.DECODED))
                    .getImages().get(0);
            assertEquals(2, corrected.getWidth());
            assertEquals(ImageResource.ByteAvailability.UNSUPPORTED_FILTER,
                    corrected.getDecodedData().getAvailability());
            assertFalse(corrected.getDecodedData().getBytes().isPresent());
            try (RenderedPage page = session.query(
                    RenderPage.version1(1, RenderOptions.defaults()))) {
                assertTrue(page.getDiagnostics().contains(
                        RenderDiagnostic.PLATFORM_IMAGE_CODEC));
            }
            return null;
        });
    }

    @Test public void annotationAppearanceImagesShareThePixelLedger() throws Exception {
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < 100; i++) { hex.append("ff0000"); }
        hex.append('>');
        String appearance = "10 0 0 10 0 0 cm /I Do\n";
        byte[] bytes = fixture("/Annots [5 0 R]", "<< >>", "",
                "<< /Type /Annot /Subtype /Square /Rect [10 0 20 10] /AP << /N 6 0 R >> >>",
                "<< /Type /XObject /Subtype /Form /BBox [0 0 10 10] /Resources << /XObject << /I 7 0 R >> >>"
                        + " /Length " + appearance.length() + " >>\nstream\n" + appearance + "endstream",
                "<< /Type /XObject /Subtype /Image /Width 10 /Height 10 /ColorSpace /DeviceRGB /BitsPerComponent 8"
                        + " /Filter /ASCIIHexDecode /Length " + hex.length() + " >>\nstream\n" + hex + "\nendstream");
        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(request(bytes)
                .resourcePolicy(policy().maximumDecodedPixels(300).build()).build(), session -> {
                    assertEquals(0xffff0000, rendered(session, RenderOptions.defaults()).getRGB(15, 3)); return null;
                });
        assertEquals(300, outcome.getResourceUsage().getDecodedPixels());
        assertCode(DocumentFailureCode.PIXEL_LIMIT_EXCEEDED, () -> new DocumentWorkflow().execute(
                request(bytes).resourcePolicy(policy().maximumDecodedPixels(299).build()).build(), session -> {
                    rendered(session, RenderOptions.defaults()); return null;
                }));
    }

    @Test public void closeDuringConsumptionRetainsStorageUntilTheOpenReaderFinishes() throws Exception {
        StringBuilder samples = new StringBuilder();
        java.util.Random random = new java.util.Random(230);
        for (int i = 0; i < 64 * 64 * 3; i++) { samples.append(String.format(Locale.ROOT, "%02x", random.nextInt(256))); }
        samples.append('>');
        byte[] bytes = fixture("", "<< /XObject << /I 5 0 R >> >>", "20 0 0 10 0 0 cm /I Do\n",
                "<< /Type /XObject /Subtype /Image /Width 64 /Height 64 /ColorSpace /DeviceRGB /BitsPerComponent 8"
                        + " /Filter /ASCIIHexDecode /Length " + samples.length() + " >>\nstream\n" + samples + "\nendstream");
        RenderOptions options = RenderOptions.builder().scale(10).build();
        long peak = new DocumentWorkflow().execute(request(bytes).build(), session -> {
            try (RenderedPage page = session.query(RenderPage.version1(1, options))) {
                page.writePngTo(new ByteArrayOutputStream());
            }
            return null;
        }).getResourceUsage().getPeakTemporaryStorageBytes();
        assertCode(DocumentFailureCode.TEMPORARY_STORAGE_LIMIT_EXCEEDED, () -> new DocumentWorkflow().execute(
                request(bytes).resourcePolicy(policy().maximumTemporaryStorageBytes(peak).build()).build(), session -> {
                    RenderedPage page = session.query(RenderPage.version1(1, options));
                    AtomicBoolean once = new AtomicBoolean();
                    page.writePngTo(new OutputStream() {
                        @Override public void write(int value) throws IOException {
                            if (once.compareAndSet(false, true)) {
                                page.close();
                                try (RenderedPage next = session.query(RenderPage.version1(1, options))) {
                                    next.writePngTo(new ByteArrayOutputStream());
                                } catch (DocumentFailure failure) { throw new IOException(failure); }
                            }
                        }
                    });
                    return null;
                }));
    }

    @Test public void defaultLogsKeepFontAndMissingResourceNamesPrivate() throws Exception {
        java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        java.util.logging.StreamHandler capture = new java.util.logging.StreamHandler(bytes, new java.util.logging.SimpleFormatter());
        java.util.logging.Filter original = record -> true;
        capture.setFilter(original);
        root.addHandler(capture);
        try {
            byte[] font = fixture("", "<< /Font << /F 5 0 R >> >>", "BT /F 8 Tf (A) Tj ET\n",
                    "<< /Type /Font /Subtype /Type1 /BaseFont /T23_PRIVATE_FONT_MARKER /Encoding /WinAnsiEncoding >>");
            new DocumentWorkflow().execute(request(font).build(), session -> {
                try (RenderedPage page = session.query(RenderPage.version1(1, RenderOptions.defaults()))) {
                    assertTrue(page.getDiagnostics().contains(RenderDiagnostic.FONT_SUBSTITUTED));
                }
                return null;
            });
            byte[] missing = fixture("", "<< >>", "/T23_PRIVATE_RESOURCE_MARKER sh\n");
            assertCode(DocumentFailureCode.RENDER_FAILED, () -> new DocumentWorkflow().execute(request(missing).build(), session -> {
                rendered(session, RenderOptions.defaults()); return null;
            }));
            capture.flush();
            String logs = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            assertFalse(logs, logs.contains("T23_PRIVATE_FONT_MARKER"));
            assertFalse(logs, logs.contains("T23_PRIVATE_RESOURCE_MARKER"));
            assertSame(original, capture.getFilter());
        } finally { root.removeHandler(capture); capture.close(); }
    }


    @Test public void providerReplyIsReservedWhileLargeInputCopiesAreStillLive() throws Exception {
        StringBuilder noise = new StringBuilder();
        java.util.Random random = new java.util.Random(233);
        for (int i = 0; i < 100000; i++) { noise.append(String.format(Locale.ROOT, "%02x", random.nextInt(256))); }
        byte[] bytes = fixture("/FolioPrivate 5 0 R", "<< >>", "",
                "<< /Length " + noise.length() + " >>\nstream\n" + noise + "\nendstream");
        Path path = temporary.newFile("provider-input.pdf").toPath();
        Files.write(path, bytes);
        AtomicInteger calls = new AtomicInteger();
        AtomicLong inputLength = new AtomicLong();
        CapabilityProvider delegate = provider("test.renderer", ProviderExecutionMode.IN_PROCESS,
                ProviderAvailability.AVAILABLE, calls);
        CapabilityProvider observed = new CapabilityProvider(delegate.getMetadata()) {
            @Override protected ProviderResult perform(ProviderRequest request) throws ProviderFailure {
                inputLength.set(request.getInputLength()); return delegate.execute(request);
            }
        };
        DocumentWorkflow workflow = new DocumentWorkflow(WorkflowEnvironment.builder().provider(observed)
                .hardenedWorkerSettings(HardenedWorkerSettings.builder().maximumMessageBytes(2048)
                        .maximumHeapBytes(512L << 20).build()).build());
        DocumentWork<Void> render = session -> {
            ObjectReference pageRef = session.query(PageObjectReference.version1(1));
            session.execute(DocumentPatch.builder().setDictionaryEntry(pageRef, PdfName.of("MediaBox"),
                    PdfArray.of(PdfNumber.of(0), PdfNumber.of(0), PdfNumber.of(200), PdfNumber.of(100))).build());
            try (RenderedPage page = session.query(RenderPage.version1(1, RenderOptions.defaults()))) {
                page.writePngTo(new ByteArrayOutputStream());
            }
            return null;
        };
        WorkflowRequest baseline = WorkflowRequest.builder().source("input", DocumentSource.path(path))
                .primarySource("input").saveMode(SaveMode.REWRITE).executionProfile(profile).build();
        long peak = workflow.execute(baseline, render).getResourceUsage().getPeakOwnedMemoryBytes();
        assertTrue(inputLength.get() > 100000);
        long overlap = 3L * inputLength.get() + 16 + 4 * 200 * 100;
        assertTrue("Input/reply overlap must contribute to peak " + peak + " >= " + overlap, peak >= overlap);
        calls.set(0);
        WorkflowRequest limited = WorkflowRequest.builder().source("input", DocumentSource.path(path))
                .primarySource("input").saveMode(SaveMode.REWRITE).executionProfile(profile)
                .resourcePolicy(policy().maximumOwnedMemoryBytes(overlap - 1).build()).build();
        assertCode(DocumentFailureCode.MEMORY_LIMIT_EXCEEDED, () -> workflow.execute(limited, render));
        assertEquals("Reject the reply handoff before invoking the Provider", 0, calls.get());
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-09-05T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static BufferedImage rendered(DocumentSession session, RenderOptions options) throws DocumentFailure {
        try (RenderedPage page = session.query(RenderPage.version1(1, options))) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            page.writePngTo(bytes);
            try { return ImageIO.read(new ByteArrayInputStream(bytes.toByteArray())); }
            catch (IOException failure) { throw new AssertionError(failure); }
        }
    }
    private static WorkflowResourcePolicy.Builder policy() {
        WorkflowResourcePolicy d = WorkflowResourcePolicy.safeDefaults();
        return WorkflowResourcePolicy.builder().maximumInputBytes(d.getMaximumInputBytes())
                .maximumPages(d.getMaximumPages()).maximumObjects(d.getMaximumObjects())
                .maximumNestingDepth(d.getMaximumNestingDepth())
                .maximumDecompressedBytes(d.getMaximumDecompressedBytes())
                .maximumDecodedPixels(d.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(d.getMaximumOwnedMemoryBytes())
                .maximumTemporaryStorageBytes(d.getMaximumTemporaryStorageBytes())
                .maximumElapsedTime(d.getMaximumElapsedTime())
                .maximumConcurrentWorkflows(d.getMaximumConcurrentWorkflows());
    }
    private WorkflowRequest.Builder request() {
        return request(fixture());
    }
    private WorkflowRequest.Builder request(byte[] bytes) {
        return WorkflowRequest.builder().source("input", DocumentSource.bytes(bytes, bytes.length))
                .primarySource("input").executionProfile(profile).saveMode(SaveMode.REWRITE);
    }
    private static byte[] fixture() {
        return fixture("", "<< >>", "1 0 0 rg 0 0 10 10 re f\n");
    }
    private static byte[] fixture(String pageExtra, String resources, String content, String... additional) {
        List<String> objects = new ArrayList<String>(Arrays.asList(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 20 10] /Resources " + resources + " /Contents 4 0 R " + pageExtra + " >>",
            "<< /Length " + content.length() + " >>\nstream\n" + content + "endstream"
        ));
        objects.addAll(Arrays.asList(additional));
        StringBuilder pdf = new StringBuilder("%PDF-1.7\n%Folio-T23-owned\n");
        List<Integer> offsets = new ArrayList<Integer>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        for (int offset : offsets) { pdf.append(String.format(Locale.ROOT, "%010d 00000 n \n", offset)); }
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }
    private interface FailingWork { void run() throws DocumentFailure; }
    private static void assertCode(DocumentFailureCode code, FailingWork work) throws DocumentFailure {
        try { work.run(); fail("Expected " + code); }
        catch (DocumentFailure failure) { assertEquals(failure.getDiagnostic(), code, failure.getCode()); }
    }
}
