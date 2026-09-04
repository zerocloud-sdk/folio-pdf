package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import net.zerocloud.pdf.Annotation;
import net.zerocloud.pdf.AnnotationProperties;
import net.zerocloud.pdf.AnnotationRectangle;
import net.zerocloud.pdf.CancellationToken;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.DocumentPermissions;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentQuery;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.HardenedWorkerSettings;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNull;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfOutputPolicy;
import net.zerocloud.pdf.PdfString;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.PdfVersion;
import net.zerocloud.pdf.PasswordCredential;
import net.zerocloud.pdf.PasswordSecurityPolicy;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.TextRenderingMode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowProgressPhase;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.WorkflowResourcePolicy;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.command.InsertBlankPage;
import net.zerocloud.pdf.command.SetXmpMetadata;
import net.zerocloud.pdf.command.UpdateAnnotations;
import net.zerocloud.pdf.command.UpdateDocumentInfo;
import net.zerocloud.pdf.composition.CanvasColor;
import net.zerocloud.pdf.composition.CanvasColorSpace;
import net.zerocloud.pdf.composition.CanvasImage;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.composition.CanvasProgram;
import net.zerocloud.pdf.composition.CanvasRectangle;
import net.zerocloud.pdf.composition.CanvasResourceLimits;
import net.zerocloud.pdf.composition.CanvasTransparencyGroup;
import net.zerocloud.pdf.composition.FontLimits;
import net.zerocloud.pdf.composition.FontSelection;
import net.zerocloud.pdf.composition.FontSource;
import net.zerocloud.pdf.composition.PositionedUnicodeText;
import net.zerocloud.pdf.composition.ReferenceFontSet;
import net.zerocloud.pdf.composition.command.DrawCanvas;
import net.zerocloud.pdf.composition.command.DrawPositionedUnicodeText;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.ReadEmbeddedFile;
import org.junit.Rule;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.rules.TemporaryFolder;

/** Observable T03/T20 contracts shared by both execution boundaries. */
@RunWith(Parameterized.class)
public final class WorkflowExecutionProfileContractTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> profiles() {
        return Arrays.asList(new Object[][] {
            {WorkflowExecutionProfile.IN_PROCESS},
            {WorkflowExecutionProfile.HARDENED_WORKER}
        });
    }

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final WorkflowExecutionProfile profile;

    public WorkflowExecutionProfileContractTest(
            WorkflowExecutionProfile profile) {
        this.profile = profile;
    }

    @Test
    public void callbackResultOrderingQueryBarrierAndProgressArePreserved()
            throws Exception {
        Path target = path("ordered.pdf");
        List<WorkflowProgressPhase> progress =
                new ArrayList<WorkflowProgressPhase>();
        Object marker = new Object();

        WorkflowOutcome<Object> outcome = new DocumentWorkflow().execute(
                create(target)
                        .progressListener(progress::add)
                        .build(),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    assertEquals(Integer.valueOf(1),
                            session.query(PageCount.INSTANCE));
                    session.execute(AddBlankPage.INSTANCE);
                    assertEquals(Integer.valueOf(2),
                            session.query(PageCount.INSTANCE));
                    return marker;
                });

        assertSame(marker, outcome.getResult());
        assertEquals(profile, outcome.getExecutionProfile());
        assertEquals(PublicationStatus.COMMITTED,
                outcome.getPublicationReceipts().get(0).getStatus());
        assertEquals(Arrays.asList(
                WorkflowProgressPhase.STARTED,
                WorkflowProgressPhase.WORK_STARTED,
                WorkflowProgressPhase.WORK_COMPLETED,
                WorkflowProgressPhase.STAGED,
                WorkflowProgressPhase.VALIDATED,
                WorkflowProgressPhase.PUBLICATION_STARTED,
                WorkflowProgressPhase.TARGET_COMMITTED,
                WorkflowProgressPhase.COMPLETED), progress);
    }

    @Test
    public void canvasVersionShapeFailurePrecedesPageSelection()
            throws Exception {
        Path target = path("canvas-shape-order.pdf");
        CanvasResourceLimits limits = CanvasResourceLimits.builder()
                .maximumEncodedImageBytes(1024L)
                .maximumDecodedImagePixels(1024L)
                .maximumDecodedImageBytes(4096L)
                .maximumIccProfileBytes(1024L)
                .maximumMaskBytes(1024L)
                .maximumGeneratedContentBytes(4096L)
                .maximumResourceDeclarations(8)
                .maximumTransparencyGroupDepth(2)
                .build();
        try {
            new DocumentWorkflow().execute(
                    create(target).build(),
                    session -> {
                        session.execute(DrawCanvas.version2(
                                999,
                                CanvasProgram.version1().build(),
                                limits));
                        return null;
                    });
            fail("Expected the Canvas shape to be rejected first");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.CANVAS_PROGRAM_INVALID,
                    failure.getCode());
            assertEquals(
                    "composition.canvas.images-colors-transparency",
                    failure.getCapabilityId());
            assertEquals("The Canvas Program is invalid.",
                    failure.getDiagnostic());
        }
        assertFalse(Files.exists(target));
    }

    @Test
    public void canvasSemanticFailurePrecedesOversizedImageTransport()
            throws Exception {
        Path target = path("canvas-semantic-before-image.pdf");
        CanvasProgram program = CanvasProgram.version2()
                .restoreState()
                .drawImage(
                        CanvasImage.jpeg(new byte[4_096]),
                        CanvasMatrix.IDENTITY)
                .build();

        try {
            new DocumentWorkflow(lowMessageEnvironment()).execute(
                    create(target).build(),
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        session.execute(DrawCanvas.version2(
                                1,
                                program,
                                canvasLimits()));
                        return null;
                    });
            fail("Expected the Canvas semantic failure");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.CANVAS_PROGRAM_INVALID,
                    failure.getCode());
            assertEquals("The Canvas Program is invalid.",
                    failure.getDiagnostic());
        }
        assertFalse(Files.exists(target));
    }

    @Test
    public void canvasPreservationFailurePrecedesOversizedImageTransport()
            throws Exception {
        Path source = path("canvas-preservation-source.pdf");
        Path target = path("canvas-preservation-target.pdf");
        byte[] sentinel = new byte[] {81, 82, 83};
        writeMalformedCanvasResourcesFixture(source);
        Files.write(target, sentinel);
        CanvasProgram program = CanvasProgram.version2()
                .drawImage(
                        CanvasImage.jpeg(new byte[4_096]),
                        CanvasMatrix.IDENTITY)
                .build();

        try {
            new DocumentWorkflow(lowMessageEnvironment()).execute(
                    WorkflowRequest.builder()
                            .source("primary", DocumentSource.path(source))
                            .primarySource("primary")
                            .target(
                                    "result",
                                    PublicationTarget.path(target))
                            .saveMode(SaveMode.REWRITE)
                            .executionProfile(profile)
                            .build(),
                    session -> {
                        session.execute(DrawCanvas.version2(
                                1,
                                program,
                                canvasLimits()));
                        return null;
                    });
            fail("Expected the Canvas preservation failure");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.CANVAS_PRESERVATION_UNSUPPORTED,
                    failure.getCode());
            assertEquals(
                    "The page content or resources cannot be preserved safely for Canvas drawing.",
                    failure.getDiagnostic());
        }
        assertArrayEquals(sentinel, Files.readAllBytes(target));
    }

    @Test
    public void canvasPreservationContentIsDecodedOnlyOncePerCommand()
            throws Exception {
        assertCanvasPreservationDecompressionBoundary(
                "canvas-v1-preservation-accounting.pdf",
                DrawCanvas.version1(
                        1,
                        CanvasProgram.version1()
                                .moveTo(10d, 10d)
                                .lineTo(20d, 20d)
                                .stroke()
                                .build()),
                8L);
        assertCanvasPreservationDecompressionBoundary(
                "canvas-v2-preservation-accounting.pdf",
                DrawCanvas.version2(
                        1,
                        CanvasProgram.version2()
                                .setFillColor(CanvasColor.rgb(1d, 0d, 0d))
                                .moveTo(0d, 0d)
                                .lineTo(1d, 1d)
                                .stroke()
                                .build(),
                        canvasLimits()),
                8L);
    }

    @Test
    public void nestedTransparencyHonorsLoweredWorkflowNestingLimit()
            throws Exception {
        Path target = path("canvas-lowered-nesting.pdf");
        CanvasProgram nested = CanvasProgram.version2()
                .setFillColor(CanvasColor.gray(0.5d))
                .build();
        for (int depth = 0; depth < 9; depth++) {
            CanvasTransparencyGroup group =
                    CanvasTransparencyGroup.version1(
                            CanvasRectangle.of(0d, 0d, 10d, 10d),
                            CanvasColorSpace.deviceRgb(),
                            false,
                            false,
                            nested);
            nested = CanvasProgram.version2()
                    .drawTransparencyGroup(group, CanvasMatrix.IDENTITY)
                    .build();
        }
        final CanvasProgram excessive = nested;

        try {
            new DocumentWorkflow().execute(
                    create(target)
                            .resourcePolicy(policyWithNesting(8))
                            .build(),
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        session.execute(DrawCanvas.version2(
                                1,
                                excessive,
                                canvasLimits()));
                        return null;
                    });
            fail("Expected the lowered nesting limit");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.NESTING_LIMIT_EXCEEDED,
                    failure.getCode());
            assertEquals("The workflow nesting-depth limit was exceeded.",
                    failure.getDiagnostic());
        }
        assertFalse(Files.exists(target));
    }

    @Test
    public void protectedDocumentPermissionPrecedesOversizedCommandAndQuery()
            throws Exception {
        PasswordCredential owner = PasswordCredential.of(
                "profile-owner".toCharArray());
        PasswordCredential user = PasswordCredential.of(
                "profile-user".toCharArray());
        try {
            Path protectedSource = path("permission-source.pdf");
            PasswordSecurityPolicy security = PasswordSecurityPolicy.builder(
                            owner,
                            user)
                    .permissions(DocumentPermissions.builder().build())
                    .build();
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .target(
                                    "result",
                                    PublicationTarget.path(protectedSource))
                            .saveMode(SaveMode.REWRITE)
                            .outputPolicy(PdfOutputPolicy
                                    .version(PdfVersion.PDF_1_7)
                                    .withPasswordSecurity(security))
                            .build(),
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        return null;
                    });

            String hugeName = repeated('n', 4_096);
            List<DocumentCommand> oversized = Arrays.asList(
                    SetXmpMetadata.version1(new byte[4_096]),
                    UpdateAnnotations.version1()
                            .remove(hugeName)
                            .build());
            WorkflowEnvironment environment = lowMessageEnvironment();
            int index = 0;
            for (DocumentCommand command : oversized) {
                Path target = path("permission-command-" + index + ".pdf");
                try {
                    new DocumentWorkflow(environment).execute(
                            protectedRequest(
                                    protectedSource,
                                    user,
                                    target),
                            session -> {
                                session.execute(command);
                                return null;
                            });
                    fail("Expected protected Command rejection");
                } catch (DocumentFailure failure) {
                    assertPermissionDenied(failure);
                }
                assertFalse(Files.exists(target));
                index++;
            }

            Path queryTarget = path("permission-query.pdf");
            try {
                new DocumentWorkflow(environment).execute(
                        protectedRequest(
                                protectedSource,
                                user,
                                queryTarget),
                        session -> session.query(
                                ReadEmbeddedFile.version1(hugeName, 1L)));
                fail("Expected protected Query rejection");
            } catch (DocumentFailure failure) {
                assertPermissionDenied(failure);
            }
            assertFalse(Files.exists(queryTarget));
            assertFalse(user.isDestroyed());
        } finally {
            owner.close();
            user.close();
        }
    }

    @Test
    public void signedWidgetFailurePrecedesLaterOversizedIdentifiers()
            throws Exception {
        Path source = path("signed-widget-source.pdf");
        Path target = path("signed-widget-target.pdf");
        byte[] signed = ProjectOwnedSignatureFixtures.docMdpSignature(3);
        byte[] sentinel = new byte[] {71, 72, 73};
        Files.write(source, signed);
        Files.write(target, sentinel);
        Annotation widget = Annotation.widget(
                AnnotationProperties.version1(
                                "first-widget",
                                1,
                                AnnotationRectangle.of(
                                        10L,
                                        10L,
                                        30L,
                                        30L))
                        .build());
        UpdateAnnotations update = UpdateAnnotations.version1()
                .put(widget)
                .remove(repeated('z', 4_096))
                .build();
        WorkflowEnvironment environment = lowMessageEnvironment();

        try {
            new DocumentWorkflow(environment).execute(
                    WorkflowRequest.builder()
                            .source("primary", DocumentSource.path(source))
                            .primarySource("primary")
                            .target("result", PublicationTarget.path(target))
                            .saveMode(SaveMode.INCREMENTAL)
                            .executionProfile(profile)
                            .build(),
                    session -> {
                        session.execute(update);
                        return null;
                    });
            fail("Expected the signed Widget update to fail");
        } catch (DocumentFailure failure) {
            assertEquals(
                    "SIGNATURE_POLICY_REJECTED",
                    failure.getCode().name());
            assertEquals(
                    "The Existing Signature policy does not permit this workflow.",
                    failure.getDiagnostic());
        }
        assertArrayEquals(signed, Files.readAllBytes(source));
        assertArrayEquals(sentinel, Files.readAllBytes(target));
    }

    @Test
    public void sessionLifecycleAndThreadConfinementArePreserved()
            throws Exception {
        final DocumentSession[] retained = new DocumentSession[1];
        AtomicReference<Throwable> crossThread =
                new AtomicReference<Throwable>();
        AtomicReference<Throwable> emptyBatchCrossThread =
                new AtomicReference<Throwable>();
        new DocumentWorkflow().execute(create(path("lifecycle.pdf")).build(),
                session -> {
                    retained[0] = session;
                    Thread other = new Thread(() -> {
                        try {
                            session.query(PageCount.INSTANCE);
                        } catch (Throwable failure) {
                            crossThread.set(failure);
                        }
                    });
                    other.start();
                    Thread emptyBatch = new Thread(() -> {
                        try {
                            session.executeBatch(
                                    Collections.<DocumentCommand>emptyList());
                        } catch (Throwable failure) {
                            emptyBatchCrossThread.set(failure);
                        }
                    });
                    emptyBatch.start();
                    try {
                        other.join();
                        emptyBatch.join();
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(failure);
                    }
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });

        assertEquals(IllegalStateException.class,
                crossThread.get().getClass());
        assertEquals("Document Session is thread-confined.",
                crossThread.get().getMessage());
        assertEquals(IllegalStateException.class,
                emptyBatchCrossThread.get().getClass());
        assertEquals("Document Session is thread-confined.",
                emptyBatchCrossThread.get().getMessage());
        try {
            retained[0].query(PageCount.INSTANCE);
            fail("Expected an expired Session");
        } catch (IllegalStateException expected) {
            assertEquals("Document Session is no longer active.",
                    expected.getMessage());
        }
        try {
            retained[0].executeBatch(
                    Collections.<DocumentCommand>emptyList());
            fail("Expected an expired Session for an empty batch");
        } catch (IllegalStateException expected) {
            assertEquals("Document Session is no longer active.",
                    expected.getMessage());
        }
    }

    @Test
    public void invalidLazyViewIndexesDoNotTerminateTheSession()
            throws Exception {
        Path target = path("lazy-index.pdf");
        Integer pages = new DocumentWorkflow().execute(
                create(target).build(),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    ObjectReference rootReference = session.query(
                            DocumentRootReference.INSTANCE);
                    PdfDictionary root = (PdfDictionary) session.query(
                            InspectObject.version1(
                                    rootReference,
                                    PdfInspectionLimits.of(10L, 0L)));
                    try {
                        root.getEntry(root.size());
                        fail("Expected a dictionary index failure");
                    } catch (IndexOutOfBoundsException expected) {
                        // The caller-visible runtime failure is recoverable.
                    }
                    PdfIndirectReference pagesReference =
                            (PdfIndirectReference) root.get(PdfName.of("Pages"));
                    PdfDictionary pagesDictionary =
                            (PdfDictionary) session.query(
                                    InspectObject.version1(
                                            pagesReference.getReference(),
                                            PdfInspectionLimits.of(10L, 0L)));
                    PdfArray kids = (PdfArray) pagesDictionary.get(
                            PdfName.of("Kids"));
                    try {
                        kids.get(kids.size());
                        fail("Expected an array index failure");
                    } catch (IndexOutOfBoundsException expected) {
                        // The caller-visible runtime failure is recoverable.
                    }
                    return session.query(PageCount.INSTANCE);
                }).getResult();

        assertEquals(Integer.valueOf(1), pages);
        assertTrue(Files.exists(target));
    }

    @Test
    public void lazyValuesCanBeUsedByALaterCommand() throws Exception {
        Path target = path("lazy-command-value.pdf");

        Integer copiedKids = new DocumentWorkflow().execute(
                create(target).build(),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    ObjectReference rootReference = session.query(
                            DocumentRootReference.INSTANCE);
                    PdfDictionary root = (PdfDictionary) session.query(
                            InspectObject.version1(
                                    rootReference,
                                    PdfInspectionLimits.of(20L, 0L)));
                    PdfIndirectReference pagesReference =
                            (PdfIndirectReference) root.get(PdfName.of("Pages"));
                    PdfDictionary pages = (PdfDictionary) session.query(
                            InspectObject.version1(
                                    pagesReference.getReference(),
                                    PdfInspectionLimits.of(20L, 0L)));
                    PdfArray kids = (PdfArray) pages.get(PdfName.of("Kids"));

                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(
                                    rootReference,
                                    PdfName.of("CopiedKids"),
                                    kids)
                            .build());
                    PdfDictionary updated = (PdfDictionary) session.query(
                            InspectObject.version1(
                                    rootReference,
                                    PdfInspectionLimits.of(20L, 0L)));
                    return Integer.valueOf(((PdfArray) updated.get(
                            PdfName.of("CopiedKids"))).size());
                }).getResult();

        assertEquals(Integer.valueOf(1), copiedKids);
        assertTrue(Files.exists(target));
    }

    @Test
    public void aLaterRejectedBatchCommandPreservesEarlierEffects()
            throws Exception {
        Path target = path("partial-batch.pdf");
        DocumentCommand unsupported = new DocumentCommand() { };

        Integer pages = new DocumentWorkflow().execute(
                create(target).build(),
                session -> {
                    try {
                        session.executeBatch(Arrays.asList(
                                AddBlankPage.INSTANCE,
                                unsupported));
                        fail("Expected the second Command to be rejected");
                    } catch (DocumentFailure failure) {
                        assertEquals(
                                DocumentFailureCode.COMMAND_REJECTED,
                                failure.getCode());
                    }
                    return session.query(PageCount.INSTANCE);
                }).getResult();

        assertEquals(Integer.valueOf(1), pages);
        assertTrue(Files.exists(target));
    }

    @Test
    public void aFailedCommandDoesNotConsumeALaterOneShotFont()
            throws Exception {
        Path target = path("failed-before-font.pdf");
        OneByteInputStream font = new OneByteInputStream(4);
        FontSelection selection = FontSelection.explicit(
                FontSource.stream(font));

        try {
            new DocumentWorkflow().execute(create(target).build(), session -> {
                session.executeBatch(Arrays.asList(
                        InsertBlankPage.version1(0),
                        positioned("A", selection, 1)));
                return null;
            });
            fail("Expected the first Command to fail");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.PAGE_POSITION_INVALID,
                    failure.getCode());
        }
        assertEquals(0, font.getReadCount());
        assertFalse(font.isClosed());
        assertFalse(Files.exists(target));
    }

    @Test
    public void invalidPositionedTextPageDoesNotConsumeItsFont()
            throws Exception {
        Path target = path("invalid-page-before-font.pdf");
        OneByteInputStream font = new OneByteInputStream(4);
        FontSelection selection = FontSelection.explicit(
                FontSource.stream(font));

        try {
            new DocumentWorkflow().execute(create(target).build(), session -> {
                session.execute(AddBlankPage.INSTANCE);
                session.execute(positionedAtPage("A", selection, 2, 1, 100L));
                return null;
            });
            fail("Expected the positioned-text page to fail");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.PAGE_RANGE_INVALID,
                    failure.getCode());
        }
        assertEquals(0, font.getReadCount());
        assertFalse(font.isClosed());
        assertFalse(Files.exists(target));
    }

    @Test
    public void caughtFontTransportFailureLeavesTheSessionUsable()
            throws Exception {
        Path target = path("font-failure-recovery.pdf");
        OneByteInputStream font = new OneByteInputStream(2);

        Integer pages = new DocumentWorkflow().execute(
                create(target).build(),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    try {
                        session.execute(positioned(
                                "A",
                                FontSelection.explicit(
                                        FontSource.stream(font)),
                                1,
                                1L));
                        fail("Expected the font byte limit");
                    } catch (DocumentFailure failure) {
                        assertEquals(DocumentFailureCode.FONT_LIMIT_EXCEEDED,
                                failure.getCode());
                    }
                    session.execute(AddBlankPage.INSTANCE);
                    return session.query(PageCount.INSTANCE);
                }).getResult();

        assertEquals(Integer.valueOf(2), pages);
        assertEquals(2, font.getReadCount());
        assertFalse(font.isClosed());
        assertTrue(Files.exists(target));
    }

    @Test
    public void firstBackendFailurePrecedesLaterTerminalEncodingFailure()
            throws Exception {
        Path target = path("failed-before-memory.pdf");
        WorkflowResourcePolicy limited = policyWithOwnedMemory(8_192L);
        UpdateDocumentInfo later = UpdateDocumentInfo.version1()
                .set("large", PdfString.of(new byte[16_384]))
                .build();

        try {
            new DocumentWorkflow().execute(
                    create(target).resourcePolicy(limited).build(),
                    session -> {
                        session.executeBatch(Arrays.asList(
                                InsertBlankPage.version1(0),
                                later));
                        return null;
                    });
            fail("Expected the first Command to fail");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.PAGE_POSITION_INVALID,
                    failure.getCode());
        }
        assertFalse(Files.exists(target));
    }

    @Test
    public void hugeScaleDecimalFailsMemoryPolicyBeforeLexicalAllocation()
            throws Exception {
        Path target = path("huge-scale-decimal.pdf");
        PdfNumber hugeScale = PdfNumber.of(
                BigDecimal.ONE.scaleByPowerOfTen(-100_000));

        try {
            new DocumentWorkflow().execute(
                    create(target)
                            .resourcePolicy(policyWithOwnedMemory(8_192L))
                            .build(),
                    session -> {
                        ObjectReference root = session.query(
                                DocumentRootReference.INSTANCE);
                        session.execute(DocumentPatch.builder()
                                .setDictionaryEntry(
                                        root,
                                        PdfName.of("HugeScale"),
                                        hugeScale)
                                .build());
                        return null;
                    });
            fail("Expected the owned-memory limit");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                    failure.getCode());
        }
        assertFalse(Files.exists(target));
    }

    @Test
    public void workerCodecHonorsALoweredWorkflowNestingLimit()
            throws Exception {
        Path target = path("lowered-worker-nesting.pdf");
        PdfValue nested = PdfNull.INSTANCE;
        for (int index = 0; index <= 100; index++) {
            nested = PdfArray.of(nested);
        }
        final PdfValue excessive = nested;

        try {
            new DocumentWorkflow().execute(
                    create(target)
                            .resourcePolicy(policyWithNesting(100))
                            .build(),
                    session -> {
                        ObjectReference root = session.query(
                                DocumentRootReference.INSTANCE);
                        session.execute(DocumentPatch.builder()
                                .setDictionaryEntry(
                                        root,
                                        PdfName.of("Deep"),
                                        excessive)
                                .build());
                        return null;
                    });
            fail("Expected the lowered nesting limit");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.NESTING_LIMIT_EXCEEDED,
                    failure.getCode());
            assertEquals("The workflow nesting-depth limit was exceeded.",
                    failure.getDiagnostic());
        }
        assertFalse(Files.exists(target));
    }

    @Test
    public void callerOwnedSourceAndTargetRemainOpen() throws Exception {
        Path seed = path("seed.pdf");
        new DocumentWorkflow().execute(
                WorkflowRequest.create(seed, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });
        TrackingInputStream input = new TrackingInputStream(
                Files.readAllBytes(seed));
        TrackingOutputStream output = new TrackingOutputStream();
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.stream(input, 1_000_000L))
                .primarySource("input")
                .target("output", PublicationTarget.stream(output))
                .saveMode(SaveMode.REWRITE)
                .executionProfile(profile)
                .build();

        WorkflowOutcome<Integer> outcome = new DocumentWorkflow().execute(
                request,
                session -> session.query(PageCount.INSTANCE));

        assertEquals(Integer.valueOf(1), outcome.getResult());
        assertFalse(input.closed);
        assertFalse(output.closed);
        assertTrue(output.flushed);
        assertTrue(output.size() > 0);
        assertFalse(outcome.getPublicationReceipts().get(0)
                .getPathTarget().isPresent());
    }

    @Test
    public void invalidPrimaryWinsBeforeDeferredSourcesAndCredentials()
            throws Exception {
        OneByteInputStream later = new OneByteInputStream(8);
        assertMalformedPrimaryWins(
                malformedPrimaryRequest(path("deferred-source.pdf"))
                        .source("later", DocumentSource.stream(later, 8L))
                        .build());
        assertEquals(0, later.getReadCount());
        assertFalse(later.isClosed());

        PasswordCredential laterCredential = PasswordCredential.of(
                "destroyed-later".toCharArray());
        laterCredential.close();
        OneByteInputStream credentialSource = new OneByteInputStream(8);
        assertMalformedPrimaryWins(
                malformedPrimaryRequest(path("deferred-source-secret.pdf"))
                        .source(
                                "later",
                                DocumentSource.stream(credentialSource, 8L)
                                        .withCredential(laterCredential))
                        .build());
        assertEquals(0, credentialSource.getReadCount());
        assertTrue(laterCredential.isDestroyed());

        PasswordCredential owner = PasswordCredential.of(
                "destroyed-owner".toCharArray());
        PasswordCredential user = PasswordCredential.of(
                "destroyed-user".toCharArray());
        owner.close();
        user.close();
        assertMalformedPrimaryWins(
                malformedPrimaryRequest(path("deferred-output-secret.pdf"))
                        .outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_1_7)
                                .withPasswordSecurity(
                                        PasswordSecurityPolicy.builder(
                                                owner,
                                                user).build()))
                        .build());
        assertTrue(owner.isDestroyed());
        assertTrue(user.isDestroyed());
    }

    @Test
    public void callerFailurePropagatesUnchangedAndAbortsPublication()
            throws Exception {
        Path target = path("preserved.pdf");
        byte[] original = new byte[] {9, 8, 7};
        Files.write(target, original);
        RuntimeException expected = new RuntimeException("caller marker");
        try {
            new DocumentWorkflow().execute(create(target).build(), session -> {
                session.execute(AddBlankPage.INSTANCE);
                throw expected;
            });
            fail("Expected caller exception");
        } catch (RuntimeException actual) {
            assertSame(expected, actual);
        }
        assertArrayEquals(original, Files.readAllBytes(target));
    }

    @Test
    public void cleanupFailureDoesNotMutateACallerRuntimeException()
            throws Exception {
        Path temporaryParent = temporaryFolder.newFolder(
                "caller-failure-roots").toPath();
        Assume.assumeTrue(Files.getFileStore(temporaryParent)
                .supportsFileAttributeView("posix"));
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .temporaryDirectory(temporaryParent)
                .build();
        RuntimeException expected = new RuntimeException("caller marker");
        final Path[] root = new Path[1];
        try {
            new DocumentWorkflow(environment).execute(
                    create(path("cleanup-caller-failure.pdf")).build(),
                    session -> {
                        root[0] = onlyChild(temporaryParent);
                        prepareCleanupFailure(root[0]);
                        throw expected;
                    });
            fail("Expected caller exception");
        } catch (RuntimeException actual) {
            assertSame(expected, actual);
            assertEquals(0, actual.getSuppressed().length);
        } finally {
            if (root[0] != null && Files.exists(root[0])) {
                Files.setPosixFilePermissions(root[0], EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
                deleteTree(root[0]);
            }
        }
    }

    @Test
    public void cleanupFailureIsSuppressedOnTheCheckedPrimaryFailure()
            throws Exception {
        Path temporaryParent = temporaryFolder.newFolder(
                "checked-failure-roots").toPath();
        Assume.assumeTrue(Files.getFileStore(temporaryParent)
                .supportsFileAttributeView("posix"));
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .temporaryDirectory(temporaryParent)
                .build();
        final Path[] root = new Path[1];
        try {
            new DocumentWorkflow(environment).execute(
                    create(path("cleanup-checked-failure.pdf")).build(),
                    session -> {
                        root[0] = onlyChild(temporaryParent);
                        prepareCleanupFailure(root[0]);
                        session.execute(InsertBlankPage.version1(0));
                        return null;
                    });
            fail("Expected the checked operational failure");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.PAGE_POSITION_INVALID,
                    failure.getCode());
            assertEquals(1, failure.getSuppressed().length);
            assertTrue(failure.getSuppressed()[0] instanceof DocumentFailure);
            DocumentFailure cleanup =
                    (DocumentFailure) failure.getSuppressed()[0];
            assertEquals(DocumentFailureCode.TEMPORARY_STORAGE_UNAVAILABLE,
                    cleanup.getCode());
        } finally {
            if (root[0] != null && Files.exists(root[0])) {
                Files.setPosixFilePermissions(root[0], EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
                deleteTree(root[0]);
            }
        }
    }

    @Test
    public void cleanupFailureAfterPublicationPreservesCommittedReceipts()
            throws Exception {
        Path temporaryParent = temporaryFolder.newFolder(
                "published-cleanup-roots").toPath();
        Assume.assumeTrue(Files.getFileStore(temporaryParent)
                .supportsFileAttributeView("posix"));
        Path target = path("published-before-cleanup-failure.pdf");
        final Path[] root = new Path[1];
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .temporaryDirectory(temporaryParent)
                .build();
        WorkflowRequest request = create(target)
                .progressListener(phase -> {
                    if (phase == WorkflowProgressPhase.TARGET_COMMITTED) {
                        root[0] = onlyChild(temporaryParent);
                        prepareCleanupFailure(root[0]);
                    }
                })
                .build();

        try {
            new DocumentWorkflow(environment).execute(
                    request,
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        return null;
                    });
            fail("Expected cleanup failure after publication");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.TEMPORARY_STORAGE_UNAVAILABLE,
                    failure.getCode());
            assertEquals(1, failure.getPublicationReceipts().size());
            assertEquals(PublicationStatus.COMMITTED,
                    failure.getPublicationReceipts().get(0).getStatus());
            assertFalse(failure.getPublicationReceipts().get(0)
                    .isPartialOutputPossible());
            assertTrue(Files.exists(target));
        } finally {
            if (root[0] != null && Files.exists(root[0])) {
                Files.setPosixFilePermissions(root[0], EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
                deleteTree(root[0]);
            }
        }
    }

    @Test
    public void cancellationAndDeadlineFailBeforeCallerWork() throws Exception {
        CancellationToken token = CancellationToken.create();
        token.cancel();
        assertNeverRuns(
                create(path("cancelled.pdf"))
                        .cancellationToken(token)
                        .build(),
                DocumentFailureCode.WORKFLOW_CANCELLED);
        assertNeverRuns(
                create(path("expired.pdf"))
                        .deadline(Instant.now().minusSeconds(1L))
                        .build(),
                DocumentFailureCode.DEADLINE_EXCEEDED);
    }

    @Test
    public void aFixedEnvironmentClockDoesNotBecomeAWallClockDeadline()
            throws Exception {
        Instant logicalNow = Instant.parse("2000-01-01T00:00:00Z");
        WorkflowEnvironment environment = WorkflowEnvironment.withClock(
                Clock.fixed(logicalNow, ZoneOffset.UTC));
        Path target = path("logical-deadline.pdf");

        Integer result = new DocumentWorkflow(environment).execute(
                create(target)
                        .deadline(logicalNow.plusMillis(100L))
                        .build(),
                session -> {
                    sleep(200L);
                    session.execute(AddBlankPage.INSTANCE);
                    return session.query(PageCount.INSTANCE);
                }).getResult();

        assertEquals(Integer.valueOf(1), result);
        assertTrue(Files.exists(target));
    }

    @Test
    public void environmentClockIsObservedOnlyOnTheCallerThread()
            throws Exception {
        ThreadRecordingClock clock = new ThreadRecordingClock(
                Instant.parse("2000-01-01T00:00:00Z"),
                Thread.currentThread());
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .clock(clock)
                .build();

        Integer result = new DocumentWorkflow(environment).execute(
                create(path("clock-thread.pdf")).build(),
                session -> {
                    sleep(100L);
                    session.execute(AddBlankPage.INSTANCE);
                    return session.query(PageCount.INSTANCE);
                }).getResult();

        assertEquals(Integer.valueOf(1), result);
        assertEquals(null, clock.getForeignThread());
    }

    @Test
    public void advancingTheEnvironmentClockExpiresAnActiveDeadline()
            throws Exception {
        Instant logicalNow = Instant.parse("2000-01-01T00:00:00Z");
        MutableClock clock = new MutableClock(logicalNow);
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .clock(clock)
                .build();
        Path target = path("advanced-deadline.pdf");

        try {
            new DocumentWorkflow(environment).execute(
                    create(target)
                            .deadline(logicalNow.plusSeconds(60L))
                            .build(),
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        clock.set(logicalNow.plusSeconds(61L));
                        session.query(PageCount.INSTANCE);
                        return null;
                    });
            fail("Expected the advanced deadline");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.DEADLINE_EXCEEDED,
                    failure.getCode());
        }
        assertFalse(Files.exists(target));
    }

    @Test
    public void stagingProgressPrecedesTheNextDeadlineCheck()
            throws Exception {
        Instant logicalNow = Instant.parse("2000-01-01T00:00:00Z");
        MutableClock clock = new MutableClock(logicalNow);
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .clock(clock)
                .build();
        Path target = path("staged-deadline.pdf");
        List<WorkflowProgressPhase> progress =
                new ArrayList<WorkflowProgressPhase>();

        try {
            new DocumentWorkflow(environment).execute(
                    create(target)
                            .deadline(logicalNow.plusSeconds(60L))
                            .progressListener(phase -> {
                                progress.add(phase);
                                if (phase == WorkflowProgressPhase.STAGED) {
                                    clock.set(logicalNow.plusSeconds(61L));
                                }
                            })
                            .build(),
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        return null;
                    });
            fail("Expected the post-staging deadline");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.DEADLINE_EXCEEDED,
                    failure.getCode());
        }
        assertEquals(Arrays.asList(
                WorkflowProgressPhase.STARTED,
                WorkflowProgressPhase.WORK_STARTED,
                WorkflowProgressPhase.WORK_COMPLETED,
                WorkflowProgressPhase.STAGED), progress);
        assertFalse(Files.exists(target));
    }

    @Test
    public void anUnusedReferenceFontPathIsNotOpenedBeforeItsCommand()
            throws Exception {
        Path missing = path("unused-reference-font.ttf");
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .referenceFontSet(ReferenceFontSet.version1(
                        FontSource.path(missing)))
                .build();
        Path target = path("unused-reference-font.pdf");

        Integer result = new DocumentWorkflow(environment).execute(
                create(target).build(),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return session.query(PageCount.INSTANCE);
                }).getResult();

        assertEquals(Integer.valueOf(1), result);
        assertTrue(Files.exists(target));
    }

    @Test
    public void unpairedTextSurrogateKeepsTheStablePositionedTextFailure()
            throws Exception {
        Path target = path("unpaired-text-surrogate.pdf");
        try {
            new DocumentWorkflow().execute(create(target).build(), session -> {
                session.execute(AddBlankPage.INSTANCE);
                session.execute(positioned("\ud800", FontSelection.referenceFontSet(),
                        0));
                return null;
            });
            fail("Expected the unpaired surrogate to be rejected");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.POSITIONED_TEXT_INVALID,
                    failure.getCode());
            assertEquals(
                    "The positioned Unicode text declaration is invalid.",
                    failure.getDiagnostic());
        }
        assertFalse(Files.exists(target));
    }

    @Test
    public void fontSourceCountFailsBeforeOpeningAnySource()
            throws Exception {
        Path target = path("font-source-count.pdf");
        FontSelection selection = FontSelection.explicit(
                FontSource.path(path("missing-first.ttf")),
                FontSource.path(path("missing-second.ttf")));
        try {
            new DocumentWorkflow().execute(create(target).build(), session -> {
                session.execute(AddBlankPage.INSTANCE);
                session.execute(positioned("A", selection, 1));
                return null;
            });
            fail("Expected the source-count limit");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.FONT_LIMIT_EXCEEDED,
                    failure.getCode());
            assertEquals(
                    "The font operation limit was exceeded.",
                    failure.getDiagnostic());
        }
        assertFalse(Files.exists(target));
    }

    @Test
    public void fontSourceByteLimitKeepsTheStableFailure()
            throws Exception {
        Path target = path("font-source-bytes.pdf");
        FontSelection selection = FontSelection.explicit(
                FontSource.bytes(new byte[] {1, 2}));
        try {
            new DocumentWorkflow().execute(create(target).build(), session -> {
                session.execute(AddBlankPage.INSTANCE);
                session.execute(positioned("A", selection, 1, 1L));
                return null;
            });
            fail("Expected the font-source byte limit");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.FONT_LIMIT_EXCEEDED,
                    failure.getCode());
            assertEquals(
                    "The font operation limit was exceeded.",
                    failure.getDiagnostic());
        }
        assertFalse(Files.exists(target));
    }

    @Test
    public void aggregateFontLimitStopsAtTheFirstExcessByte()
            throws Exception {
        Path target = path("aggregate-font-source-bytes.pdf");
        byte[] first = primaryFontBytes();
        OneByteInputStream second = new OneByteInputStream(100);
        FontSelection selection = FontSelection.explicit(
                FontSource.bytes(first),
                FontSource.stream(second));
        try {
            new DocumentWorkflow().execute(create(target).build(), session -> {
                session.execute(AddBlankPage.INSTANCE);
                session.execute(positioned(
                        "A",
                        selection,
                        2,
                        first.length + 10L));
                return null;
            });
            fail("Expected the aggregate font-source byte limit");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.FONT_LIMIT_EXCEEDED,
                    failure.getCode());
        }
        assertEquals(11, second.getReadCount());
        assertFalse(second.isClosed());
        assertFalse(Files.exists(target));
    }

    @Test
    public void resourceFailureIsStableAndDoesNotPublish() throws Exception {
        Path target = path("page-limit.pdf");
        WorkflowResourcePolicy defaults = WorkflowResourcePolicy.safeDefaults();
        WorkflowResourcePolicy pageFree = WorkflowResourcePolicy.builder()
                .maximumInputBytes(defaults.getMaximumInputBytes())
                .maximumPages(0)
                .maximumObjects(defaults.getMaximumObjects())
                .maximumNestingDepth(defaults.getMaximumNestingDepth())
                .maximumDecompressedBytes(defaults.getMaximumDecompressedBytes())
                .maximumDecodedPixels(defaults.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(defaults.getMaximumOwnedMemoryBytes())
                .maximumTemporaryStorageBytes(
                        defaults.getMaximumTemporaryStorageBytes())
                .maximumElapsedTime(defaults.getMaximumElapsedTime())
                .maximumConcurrentWorkflows(
                        defaults.getMaximumConcurrentWorkflows())
                .build();
        try {
            new DocumentWorkflow().execute(
                    create(target).resourcePolicy(pageFree).build(),
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        return null;
                    });
            fail("Expected the page limit");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.PAGE_LIMIT_EXCEEDED,
                    failure.getCode());
            assertFalse(failure.getDiagnostic().contains(target.toString()));
            assertEquals(PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
        }
        assertFalse(Files.exists(target));
    }

    @Test
    public void terminalResourceFailurePrecedesCaughtCallbackException()
            throws Exception {
        String encoded = "0241424380>";
        Path source = path("terminal-resource-source.pdf");
        Path target = path("terminal-resource-target.pdf");
        writePdfFixture(source, new String[] {
            "<< /Type /Catalog /Pages 2 0 R /Payload 4 0 R >>",
            "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] >>",
            "<< /Length " + encoded.length()
                    + " /Filter [/ASCIIHexDecode /RunLengthDecode] >>\n"
                    + "stream\n" + encoded + "\nendstream"
        });
        RuntimeException marker = new IllegalStateException(
                "callback marker");

        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source("primary", DocumentSource.path(source))
                            .primarySource("primary")
                            .target("result", PublicationTarget.path(target))
                            .saveMode(SaveMode.REWRITE)
                            .resourcePolicy(policyWithDecompressed(16L))
                            .executionProfile(profile)
                            .build(),
                    session -> {
                        ObjectReference root = session.query(
                                DocumentRootReference.INSTANCE);
                        PdfDictionary catalog = (PdfDictionary) session.query(
                                InspectObject.version1(
                                        root,
                                        PdfInspectionLimits.of(20L, 20L)));
                        ObjectReference payload = ((PdfIndirectReference)
                                catalog.get(PdfName.of("Payload")))
                                .getReference();
                        PdfStream stream = (PdfStream) session.query(
                                InspectObject.version1(
                                        payload,
                                        PdfInspectionLimits.of(20L, 20L)));
                        assertArrayEquals(
                                new byte[] {65, 66, 67},
                                stream.readBytes());
                        try {
                            stream.readBytes();
                            fail("Expected repeated decoding to exhaust the budget");
                        } catch (DocumentFailure failure) {
                            assertEquals(
                                    DocumentFailureCode
                                            .DECOMPRESSION_LIMIT_EXCEEDED,
                                    failure.getCode());
                            throw marker;
                        }
                        return null;
                    });
            fail("Expected the terminal resource failure");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.DECOMPRESSION_LIMIT_EXCEEDED,
                    failure.getCode());
            assertEquals(
                    "The workflow decompression limit was exceeded.",
                    failure.getDiagnostic());
        }
        assertFalse(Files.exists(target));
    }

    @Test
    public void incrementalSaveModeAndMultiTargetReceiptsArePreserved()
            throws Exception {
        Path source = path("incremental-source.pdf");
        Path archive = path("incremental-archive.pdf");
        TrackingOutputStream response = new TrackingOutputStream();
        new DocumentWorkflow().execute(
                WorkflowRequest.create(source, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });

        WorkflowOutcome<Integer> outcome = new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("source", DocumentSource.path(source))
                        .primarySource("source")
                        .target("archive", PublicationTarget.path(archive))
                        .target("response", PublicationTarget.stream(response))
                        .saveMode(SaveMode.INCREMENTAL)
                        .executionProfile(profile)
                        .build(),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return session.query(PageCount.INSTANCE);
                });

        assertEquals(Integer.valueOf(2), outcome.getResult());
        assertEquals(SaveMode.INCREMENTAL, outcome.getSaveMode());
        assertEquals(2, outcome.getPublicationReceipts().size());
        assertEquals("archive",
                outcome.getPublicationReceipts().get(0).getTargetName());
        assertEquals(PublicationStatus.COMMITTED,
                outcome.getPublicationReceipts().get(0).getStatus());
        assertEquals("response",
                outcome.getPublicationReceipts().get(1).getTargetName());
        assertEquals(PublicationStatus.COMMITTED,
                outcome.getPublicationReceipts().get(1).getStatus());
        assertFalse(response.closed);
        assertTrue(response.flushed);
        assertEquals(Integer.valueOf(2), new DocumentWorkflow().execute(
                WorkflowRequest.open(archive, SaveMode.REWRITE),
                session -> session.query(PageCount.INSTANCE)).getResult());
    }

    @Test
    public void multiTargetFailureReceiptsKeepDeclarationOrder()
            throws Exception {
        Path committed = path("committed.pdf");
        Path untouched = path("untouched.pdf");
        byte[] original = new byte[] {31, 32, 33};
        Files.write(untouched, original);
        FailingOutputStream failing = new FailingOutputStream();

        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .target("committed",
                                    PublicationTarget.path(committed))
                            .target("failing",
                                    PublicationTarget.stream(failing))
                            .target("untouched",
                                    PublicationTarget.path(untouched))
                            .saveMode(SaveMode.REWRITE)
                            .executionProfile(profile)
                            .build(),
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        return null;
                    });
            fail("Expected publication failure");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.PUBLICATION_FAILED,
                    failure.getCode());
            assertEquals(3, failure.getPublicationReceipts().size());
            assertEquals(PublicationStatus.COMMITTED,
                    failure.getPublicationReceipts().get(0).getStatus());
            assertEquals(PublicationStatus.FAILED,
                    failure.getPublicationReceipts().get(1).getStatus());
            assertEquals(PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(2).getStatus());
        }
        assertTrue(Files.exists(committed));
        assertArrayEquals(original, Files.readAllBytes(untouched));
        assertFalse(failing.closed);
    }

    @Test
    public void activeCancellationStopsAtTheNextSessionBoundary()
            throws Exception {
        Path target = path("active-cancel.pdf");
        CancellationToken token = CancellationToken.create();
        try {
            new DocumentWorkflow().execute(
                    create(target).cancellationToken(token).build(),
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        token.cancel();
                        session.query(PageCount.INSTANCE);
                        return null;
                    });
            fail("Expected active cancellation");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.WORKFLOW_CANCELLED,
                    failure.getCode());
            assertEquals(PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
        }
        assertFalse(Files.exists(target));
    }

    @Test
    public void progressFailurePropagatesUnchangedBeforeCallerWork()
            throws Exception {
        Path target = path("progress-failure.pdf");
        RuntimeException expected = new IllegalStateException(
                "progress marker");
        final boolean[] ran = {false};
        try {
            new DocumentWorkflow().execute(
                    create(target).progressListener(phase -> {
                        if (phase == WorkflowProgressPhase.WORK_STARTED) {
                            throw expected;
                        }
                    }).build(),
                    session -> {
                        ran[0] = true;
                        return null;
                    });
            fail("Expected progress-listener failure");
        } catch (RuntimeException actual) {
            assertSame(expected, actual);
        }
        assertFalse(ran[0]);
        assertFalse(Files.exists(target));
    }

    @Test
    public void closedCommandAndQuerySurfaceUsesStableSafeFailures()
            throws Exception {
        Path target = path("closed-surface.pdf");
        DocumentCommand command = new DocumentCommand() { };
        try {
            new DocumentWorkflow().execute(create(target).build(), session -> {
                session.execute(command);
                return null;
            });
            fail("Expected Command rejection");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.COMMAND_REJECTED,
                    failure.getCode());
            assertFalse(failure.getDiagnostic().contains(target.toString()));
        }

        DocumentQuery<Object> query = new DocumentQuery<Object>() { };
        try {
            new DocumentWorkflow().execute(create(target).build(),
                    session -> session.query(query));
            fail("Expected Query rejection");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.QUERY_REJECTED,
                    failure.getCode());
            assertFalse(failure.getDiagnostic().contains(target.toString()));
        }
        assertFalse(Files.exists(target));
    }

    private void assertNeverRuns(
            WorkflowRequest request,
            DocumentFailureCode expectedCode) throws Exception {
        final boolean[] ran = {false};
        try {
            new DocumentWorkflow().execute(request, session -> {
                ran[0] = true;
                return null;
            });
            fail("Expected workflow failure");
        } catch (DocumentFailure failure) {
            assertEquals(expectedCode, failure.getCode());
        }
        assertFalse(ran[0]);
    }

    private void assertMalformedPrimaryWins(WorkflowRequest request)
            throws Exception {
        final boolean[] ran = new boolean[1];
        try {
            new DocumentWorkflow().execute(request, session -> {
                ran[0] = true;
                return null;
            });
            fail("Expected malformed primary Source rejection");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.PDF_VERSION_INVALID,
                    failure.getCode());
        }
        assertFalse(ran[0]);
    }

    private WorkflowRequest.Builder malformedPrimaryRequest(Path target) {
        byte[] malformed = "%PDF-x\n".getBytes(StandardCharsets.US_ASCII);
        return WorkflowRequest.builder()
                .source(
                        "primary",
                        DocumentSource.bytes(malformed, malformed.length))
                .primarySource("primary")
                .target("result", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .executionProfile(profile);
    }

    private WorkflowRequest.Builder create(Path target) {
        return WorkflowRequest.builder()
                .target("result", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .executionProfile(profile);
    }

    private void assertCanvasPreservationDecompressionBoundary(
            String name,
            DocumentCommand command,
            long decompressedBytes) throws Exception {
        Path source = path("source-" + name);
        Path target = path(name);
        writeCanvasContentFixture(source, "q\nQ\n");

        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("primary", DocumentSource.path(source))
                        .primarySource("primary")
                        .target("result", PublicationTarget.path(target))
                        .saveMode(SaveMode.REWRITE)
                        .resourcePolicy(policyWithDecompressed(
                                decompressedBytes))
                        .executionProfile(profile)
                        .build(),
                session -> {
                    session.execute(command);
                    return null;
                });

        assertTrue(Files.exists(target));
    }

    private WorkflowRequest protectedRequest(
            Path source,
            PasswordCredential credential,
            Path target) {
        return WorkflowRequest.builder()
                .source(
                        "source",
                        DocumentSource.path(source).withCredential(credential))
                .primarySource("source")
                .target("result", PublicationTarget.path(target))
                .saveMode(SaveMode.INCREMENTAL)
                .executionProfile(profile)
                .build();
    }

    private static void assertPermissionDenied(DocumentFailure failure) {
        assertEquals(
                DocumentFailureCode.DOCUMENT_PERMISSION_DENIED,
                failure.getCode());
        assertEquals(
                "The Source credential does not authorize this document operation.",
                failure.getDiagnostic());
    }

    private static String repeated(char value, int count) {
        char[] characters = new char[count];
        Arrays.fill(characters, value);
        return new String(characters);
    }

    private static WorkflowEnvironment lowMessageEnvironment() {
        return WorkflowEnvironment.builder()
                .hardenedWorkerSettings(HardenedWorkerSettings.builder()
                        .maximumMessageBytes(2_048)
                        .maximumHeapBytes(HardenedWorkerSettings
                                .DEFAULT_MAXIMUM_HEAP_BYTES)
                        .build())
                .build();
    }

    private static CanvasResourceLimits canvasLimits() {
        return CanvasResourceLimits.builder()
                .maximumEncodedImageBytes(8_192L)
                .maximumDecodedImagePixels(8_192L)
                .maximumDecodedImageBytes(32_768L)
                .maximumIccProfileBytes(8_192L)
                .maximumMaskBytes(8_192L)
                .maximumGeneratedContentBytes(32_768L)
                .maximumResourceDeclarations(16)
                .maximumTransparencyGroupDepth(
                        CanvasResourceLimits
                                .MAXIMUM_TRANSPARENCY_GROUP_DEPTH_VERSION_1)
                .build();
    }

    private static WorkflowResourcePolicy policyWithOwnedMemory(long bytes) {
        WorkflowResourcePolicy defaults = WorkflowResourcePolicy.safeDefaults();
        return WorkflowResourcePolicy.builder()
                .maximumInputBytes(defaults.getMaximumInputBytes())
                .maximumPages(defaults.getMaximumPages())
                .maximumObjects(defaults.getMaximumObjects())
                .maximumNestingDepth(defaults.getMaximumNestingDepth())
                .maximumDecompressedBytes(defaults.getMaximumDecompressedBytes())
                .maximumDecodedPixels(defaults.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(bytes)
                .maximumTemporaryStorageBytes(
                        defaults.getMaximumTemporaryStorageBytes())
                .maximumElapsedTime(defaults.getMaximumElapsedTime())
                .maximumConcurrentWorkflows(
                        defaults.getMaximumConcurrentWorkflows())
                .build();
    }

    private static WorkflowResourcePolicy policyWithNesting(int depth) {
        WorkflowResourcePolicy defaults = WorkflowResourcePolicy.safeDefaults();
        return WorkflowResourcePolicy.builder()
                .maximumInputBytes(defaults.getMaximumInputBytes())
                .maximumPages(defaults.getMaximumPages())
                .maximumObjects(defaults.getMaximumObjects())
                .maximumNestingDepth(depth)
                .maximumDecompressedBytes(defaults.getMaximumDecompressedBytes())
                .maximumDecodedPixels(defaults.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(defaults.getMaximumOwnedMemoryBytes())
                .maximumTemporaryStorageBytes(
                        defaults.getMaximumTemporaryStorageBytes())
                .maximumElapsedTime(defaults.getMaximumElapsedTime())
                .maximumConcurrentWorkflows(
                        defaults.getMaximumConcurrentWorkflows())
                .build();
    }

    private static WorkflowResourcePolicy policyWithDecompressed(long bytes) {
        WorkflowResourcePolicy defaults = WorkflowResourcePolicy.safeDefaults();
        return WorkflowResourcePolicy.builder()
                .maximumInputBytes(defaults.getMaximumInputBytes())
                .maximumPages(defaults.getMaximumPages())
                .maximumObjects(defaults.getMaximumObjects())
                .maximumNestingDepth(defaults.getMaximumNestingDepth())
                .maximumDecompressedBytes(bytes)
                .maximumDecodedPixels(defaults.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(defaults.getMaximumOwnedMemoryBytes())
                .maximumTemporaryStorageBytes(
                        defaults.getMaximumTemporaryStorageBytes())
                .maximumElapsedTime(defaults.getMaximumElapsedTime())
                .maximumConcurrentWorkflows(
                        defaults.getMaximumConcurrentWorkflows())
                .build();
    }

    private static DrawPositionedUnicodeText positioned(
            String text,
            FontSelection selection,
            int maximumSources) {
        return positioned(text, selection, maximumSources, 1_000L);
    }

    private static DrawPositionedUnicodeText positioned(
            String text,
            FontSelection selection,
            int maximumSources,
            long maximumSourceBytes) {
        return positionedAtPage(
                text,
                selection,
                1,
                maximumSources,
                maximumSourceBytes);
    }

    private static DrawPositionedUnicodeText positionedAtPage(
            String text,
            FontSelection selection,
            int pageNumber,
            int maximumSources,
            long maximumSourceBytes) {
        return DrawPositionedUnicodeText.version1(
                pageNumber,
                PositionedUnicodeText.version1(
                        text,
                        selection,
                        12d,
                        TextRenderingMode.FILL,
                        CanvasMatrix.of(1d, 0d, 0d, 1d, 36d, 72d)),
                FontLimits.builder()
                        .maximumFontSources(maximumSources)
                        .maximumSourceBytes(maximumSourceBytes)
                        .maximumCodePoints(2)
                        .maximumFallbackChecks(2L)
                        .maximumGeneratedContentBytes(1_000L)
                        .build());
    }

    private Path path(String name) {
        return temporaryFolder.getRoot().toPath().resolve(name);
    }

    private static void writeMalformedCanvasResourcesFixture(Path target)
            throws IOException {
        writePdfFixture(target, new String[] {
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] "
                    + "/Resources /NotADictionary >>"
        });
    }

    private static void writeCanvasContentFixture(
            Path target,
            String content) throws IOException {
        byte[] contentBytes = content.getBytes(StandardCharsets.US_ASCII);
        StringBuilder encoded = new StringBuilder(
                contentBytes.length * 2 + 1);
        for (byte value : contentBytes) {
            int unsigned = value & 0xff;
            encoded.append(Character.forDigit(unsigned >>> 4, 16));
            encoded.append(Character.forDigit(unsigned & 0xf, 16));
        }
        encoded.append('>');
        writePdfFixture(target, new String[] {
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] "
                    + "/Resources << >> /Contents 4 0 R >>",
            "<< /Filter /ASCIIHexDecode /Length " + encoded.length()
                    + " >>\nstream\n" + encoded + "\nendstream"
        });
    }

    private static void writePdfFixture(Path target, String[] bodies)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write("%PDF-1.7\n%FolioT21Fixture\n"
                .getBytes(StandardCharsets.US_ASCII));
        int[] offsets = new int[bodies.length + 1];
        for (int index = 0; index < bodies.length; index++) {
            offsets[index + 1] = output.size();
            output.write(((index + 1) + " 0 obj\n")
                    .getBytes(StandardCharsets.US_ASCII));
            output.write(bodies[index]
                    .getBytes(StandardCharsets.US_ASCII));
            output.write("\nendobj\n"
                    .getBytes(StandardCharsets.US_ASCII));
        }
        int xref = output.size();
        output.write(("xref\n0 " + offsets.length + "\n")
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

    private static byte[] primaryFontBytes() throws IOException {
        String resource =
                "/net/zerocloud/pdf/fixtures/FolioPrimary.ttf.base64";
        try (InputStream input = WorkflowExecutionProfileContractTest.class
                .getResourceAsStream(resource)) {
            if (input == null) {
                throw new AssertionError("Missing test font " + resource);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
            return Base64.getMimeDecoder().decode(bytes.toByteArray());
        }
    }

    private static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private static Path onlyChild(Path directory) {
        try (java.util.stream.Stream<Path> children = Files.list(directory)) {
            List<Path> paths = children.collect(
                    java.util.stream.Collectors.toList());
            assertEquals(1, paths.size());
            return paths.get(0);
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void prepareCleanupFailure(Path root) {
        try {
            Files.write(root.resolve("retained-marker"), new byte[] {1});
            Files.setPosixFilePermissions(root, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_EXECUTE));
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            List<Path> ordered = paths.sorted(
                    java.util.Comparator.reverseOrder()).collect(
                            java.util.stream.Collectors.toList());
            for (Path path : ordered) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static final class TrackingInputStream
            extends ByteArrayInputStream {
        private boolean closed;

        private TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class TrackingOutputStream
            extends ByteArrayOutputStream {
        private boolean closed;
        private boolean flushed;

        @Override
        public void flush() throws IOException {
            flushed = true;
            super.flush();
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class OneByteInputStream extends InputStream {

        private final int length;
        private int offset;
        private boolean closed;

        private OneByteInputStream(int length) {
            this.length = length;
        }

        @Override
        public int read() {
            if (offset == length) {
                return -1;
            }
            offset++;
            return 0;
        }

        @Override
        public int read(byte[] bytes, int start, int count) {
            if (count == 0) {
                return 0;
            }
            int value = read();
            if (value < 0) {
                return -1;
            }
            bytes[start] = (byte) value;
            return 1;
        }

        @Override
        public void close() {
            closed = true;
        }

        private int getReadCount() {
            return offset;
        }

        private boolean isClosed() {
            return closed;
        }
    }

    private static final class FailingOutputStream extends OutputStream {

        private boolean closed;

        @Override
        public void write(int value) throws IOException {
            throw new IOException("intentional test failure");
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> instant;

        private MutableClock(Instant instant) {
            this.instant = new AtomicReference<Instant>(instant);
        }

        private void set(Instant value) {
            instant.set(value);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("Only UTC is supported");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }

    private static final class ThreadRecordingClock extends Clock {

        private final Instant instant;
        private final Thread owner;
        private final AtomicReference<Thread> foreignThread =
                new AtomicReference<Thread>();

        private ThreadRecordingClock(Instant instant, Thread owner) {
            this.instant = instant;
            this.owner = owner;
        }

        private Thread getForeignThread() {
            return foreignThread.get();
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("Only UTC is supported");
            }
            return this;
        }

        @Override
        public Instant instant() {
            Thread current = Thread.currentThread();
            if (current != owner) {
                foreignThread.compareAndSet(null, current);
            }
            return instant;
        }
    }
}
