package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentPermissions;
import net.zerocloud.pdf.DocumentResourceInventory;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ExtractionLimits;
import net.zerocloud.pdf.FontResource;
import net.zerocloud.pdf.ImageByteAccess;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PasswordCredential;
import net.zerocloud.pdf.PasswordSecurityPolicy;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfOutputPolicy;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.PdfVersion;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.ResourceExtractionLimits;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.TextItem;
import net.zerocloud.pdf.TextRenderingMode;
import net.zerocloud.pdf.TextStructureExtraction;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.composition.CanvasFont;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.composition.CanvasProgram;
import net.zerocloud.pdf.composition.CanvasWindingRule;
import net.zerocloud.pdf.composition.command.DrawCanvas;
import net.zerocloud.pdf.query.ExtractImagesAndResources;
import net.zerocloud.pdf.query.ExtractTextAndStructure;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageObjectReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class CanvasWorkflowTest {

    private static final String CAPABILITY =
            "composition.canvas.draw-positioned-text";
    private static final byte[] SENTINEL = new byte[] {91, 92, 93};

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void vectorAndPositionedTextRoundTripThroughPublicQueries()
            throws Exception {
        Path source = path("canvas-source.pdf");
        Path target = path("canvas-output.pdf");
        writeCanvasFixture(source, false, false);

        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(
                rewriteRequest(source, target),
                session -> {
                    CanvasFont font = canvasFont(session);
                    session.execute(DrawCanvas.version1(
                            1,
                            representativeProgram(font)));
                    return null;
                });

        assertEquals(CAPABILITY, outcome.getCapabilityId());
        assertEquals(1, outcome.getPublicationReceipts().size());
        assertEquals(
                PublicationStatus.COMMITTED,
                outcome.getPublicationReceipts().get(0).getStatus());

        new DocumentWorkflow().execute(
                WorkflowRequest.open(target, SaveMode.REWRITE),
                session -> {
                    String content = pageContent(session, 1);
                    List<ContentOperation> operations =
                            contentOperations(content);
                    assertTrue(containsSequence(
                            operations,
                            expected("m", 1d, 1d),
                            expected("l", 2d, 2d),
                            expected("S")));
                    assertTrue(containsSequence(
                            operations,
                            expected("cm", 1d, 0d, 0d, 1d, 5d, 7d),
                            expected("m", 10d, 10d),
                            expected("l", 40d, 40d),
                            expected("S")));
                    assertTrue(containsSequence(
                            operations,
                            expected("m", 10d, 10d),
                            expected("c", 20d, 20d, 30d, 40d, 50d, 50d),
                            expected("f")));
                    assertTrue(containsOperation(
                            operations,
                            expected("f*")));
                    assertTrue(containsSequence(
                            operations,
                            expected("W"),
                            expected("n"),
                            expected("m", 0d, 75d),
                            expected("l", 150d, 75d),
                            expected("S")));
                    assertTrue(containsSequence(
                            operations,
                            expected("W*"),
                            expected("n"),
                            expected("m", 75d, 0d),
                            expected("l", 75d, 150d),
                            expected("S")));
                    assertEquals(8, countOperations(operations, "Tr"));
                    assertTrue(maximumGraphicsDepth(operations) >= 3);
                    for (TextRenderingMode mode : TextRenderingMode.values()) {
                        assertTrue(containsOperation(
                                operations,
                                expected("Tr", mode.getOperatorValue())));
                    }

                    PdfDictionary page = inspectDictionary(
                            session,
                            session.query(PageObjectReference.version1(1)));
                    PdfDictionary resources = (PdfDictionary) resolve(
                            session,
                            page.get(PdfName.of("Resources")));
                    assertEquals(
                            PdfName.of("Kept"),
                            resources.get(PdfName.of("FolioKeep")));

                    DocumentResourceInventory inventory = resources(session);
                    assertEquals(1, inventory.getFonts().size());
                    assertEquals(
                            1,
                            inventory.getFonts().get(0)
                                    .getDeclarations().size());

                    TextStructureExtraction extraction = session.query(
                            ExtractTextAndStructure.version1(textLimits()));
                    List<TextItem> items = extraction.getPages().get(0)
                            .getTextItems();
                    assertEquals(8, items.size());
                    for (int index = 0; index < items.size(); index++) {
                        TextItem item = items.get(index);
                        assertEquals(
                                TextRenderingMode.values()[index],
                                item.getRenderingMode());
                        assertDecimal(20 + index * 20,
                                item.getGeometry().getE());
                        assertDecimal(60 + index * 5,
                                item.getGeometry().getF());
                        assertDecimal(12, item.getGeometry().getA());
                        assertDecimal(12, item.getGeometry().getD());
                    }
                    return null;
                });
    }

    @Test
    public void immutableProgramsAndRepeatedCommandsReuseOneFontResource()
            throws Exception {
        Path source = path("reuse-source.pdf");
        Path target = path("reuse-output.pdf");
        writeTwoPageResourceFixture(source);

        new DocumentWorkflow().execute(rewriteRequest(source, target), session -> {
            CanvasFont font = canvasFont(session);
            byte[] mutableGlyph = new byte[] {65};
            CanvasProgram.Builder builder = CanvasProgram.version1()
                    .beginText(
                            font,
                            12d,
                            TextRenderingMode.FILL,
                            CanvasMatrix.of(1d, 0d, 0d, 1d, 40d, 40d))
                    .showGlyph(mutableGlyph)
                    .setTextMatrix(CanvasMatrix.of(
                            1d, 0d, 0d, 1d, 80d, 40d))
                    .showGlyph(new byte[] {66})
                    .endText();
            CanvasProgram snapshot = builder.build();
            mutableGlyph[0] = 67;
            builder.moveTo(1d, 1d).lineTo(2d, 2d).stroke();
            assertEquals(5, snapshot.getInstructionCount());
            try {
                snapshot.getInstructions().clear();
                fail("Canvas instruction list was mutable");
            } catch (UnsupportedOperationException expected) {
                // Expected immutable view.
            }
            byte[] exposedGlyph = snapshot.getInstructions().get(1)
                    .getGlyphCode();
            exposedGlyph[0] = 68;
            assertArrayEquals(
                    new byte[] {65},
                    snapshot.getInstructions().get(1).getGlyphCode());
            session.execute(DrawCanvas.version1(1, snapshot));
            session.execute(DrawCanvas.version1(1, oneGlyph(font, 120d)));
            return null;
        });

        new DocumentWorkflow().execute(
                WorkflowRequest.open(target, SaveMode.REWRITE),
                session -> {
                    DocumentResourceInventory inventory = resources(session);
                    assertEquals(1, inventory.getFonts().size());
                    FontResource font = inventory.getFonts().get(0);
                    assertEquals(Arrays.asList(1, 2), font.getPageUsage());
                    assertEquals(2, font.getDeclarations().size());
                    List<TextItem> items = session.query(
                            ExtractTextAndStructure.version1(textLimits()))
                            .getPages().get(0).getTextItems();
                    assertEquals(3, items.size());
                    assertArrayEquals(
                            new byte[] {65},
                            items.get(0).getCharacterMapping()
                                    .getSourceCode());
                    assertArrayEquals(
                            new byte[] {66},
                            items.get(1).getCharacterMapping()
                                    .getSourceCode());
                    assertArrayEquals(
                            new byte[] {65},
                            items.get(2).getCharacterMapping()
                                    .getSourceCode());
                    assertDecimal(40, items.get(0).getGeometry().getE());
                    assertDecimal(80, items.get(1).getGeometry().getE());
                    assertDecimal(120, items.get(2).getGeometry().getE());
                    assertEquals(
                            3,
                            countOperations(
                                    contentOperations(pageContent(session, 1)),
                                    "Tj"));
                    return null;
                });
    }

    @Test
    public void identityCompositeFontAcceptsOneTwoByteGlyphCode()
            throws Exception {
        Path source = path("composite-source.pdf");
        Path target = path("composite-output.pdf");
        writeCompositeFontFixture(source);

        new DocumentWorkflow().execute(rewriteRequest(source, target), session -> {
            session.execute(DrawCanvas.version1(
                    1,
                    CanvasProgram.version1()
                            .beginText(
                                    canvasFont(session),
                                    16d,
                                    TextRenderingMode.INVISIBLE,
                                    CanvasMatrix.of(
                                            1d, 0d, 0d, 1d, 30d, 50d))
                            .showGlyph(new byte[] {0, 65})
                            .endText()
                            .build()));
            return null;
        });

        new DocumentWorkflow().execute(
                WorkflowRequest.open(target, SaveMode.REWRITE),
                session -> {
                    DocumentResourceInventory inventory = resources(session);
                    assertEquals(1, inventory.getFonts().size());
                    assertEquals(
                            FontResource.FontKind.TYPE_0,
                            inventory.getFonts().get(0).getFontKind());
                    List<ContentOperation> operations = contentOperations(
                            pageContent(session, 1));
                    assertTrue(containsOperation(
                            operations,
                            expected("Tr", TextRenderingMode.INVISIBLE
                                    .getOperatorValue())));
                    assertTrue(containsOperation(
                            operations,
                            expected("Tm", 1d, 0d, 0d, 1d, 30d, 50d)));
                    assertArrayEquals(
                            new byte[] {0, 65},
                            singleShownGlyph(operations));
                    return null;
                });
    }

    @Test
    public void unsignedIncrementalCanvasPreservesRevisionAndReportsCapability()
            throws Exception {
        Path source = path("incremental-source.pdf");
        Path target = path("incremental-output.pdf");
        writeCanvasFixture(source, false, false);
        byte[] original = Files.readAllBytes(source);

        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(
                incrementalRequest(source, target),
                session -> {
                    session.execute(DrawCanvas.version1(
                            1,
                            oneGlyph(canvasFont(session), 100d)));
                    return null;
                });

        assertEquals(CAPABILITY, outcome.getCapabilityId());
        assertEquals(
                PublicationStatus.COMMITTED,
                outcome.getPublicationReceipts().get(0).getStatus());
        byte[] published = Files.readAllBytes(target);
        assertTrue(published.length > original.length);
        assertArrayEquals(original, Arrays.copyOf(published, original.length));
        assertTrue(pageContent(target, 1).contains("<41> Tj"));
    }

    @Test
    public void everyInvalidProgramStateFailsBeforePublication()
            throws Exception {
        Path source = path("invalid-program-source.pdf");
        writeCanvasFixture(source, false, false);
        final InvalidProgramFactory[] invalid = new InvalidProgramFactory[] {
            font -> CanvasProgram.version1().build(),
            font -> CanvasProgram.version1().restoreState().build(),
            font -> CanvasProgram.version1().saveState().build(),
            font -> CanvasProgram.version1().lineTo(1d, 2d).build(),
            font -> CanvasProgram.version1()
                    .curveTo(1d, 2d, 3d, 4d, 5d, 6d).build(),
            font -> CanvasProgram.version1().closePath().build(),
            font -> CanvasProgram.version1().stroke().build(),
            font -> CanvasProgram.version1().moveTo(1d, 2d).build(),
            font -> CanvasProgram.version1().fill(
                    CanvasWindingRule.NONZERO).build(),
            font -> CanvasProgram.version1().clip(
                    CanvasWindingRule.EVEN_ODD).build(),
            font -> CanvasProgram.version1().moveTo(1d, 2d)
                    .saveState().stroke().build(),
            font -> CanvasProgram.version1().saveState()
                    .moveTo(1d, 2d).restoreState().stroke().build(),
            font -> CanvasProgram.version1().moveTo(1d, 2d)
                    .transform(CanvasMatrix.IDENTITY).stroke().build(),
            font -> CanvasProgram.version1().moveTo(1d, 2d)
                    .beginText(
                            font,
                            12d,
                            TextRenderingMode.FILL,
                            CanvasMatrix.IDENTITY)
                    .showGlyph(new byte[] {65}).endText().stroke().build(),
            font -> CanvasProgram.version1().showGlyph(new byte[] {65}).build(),
            font -> CanvasProgram.version1().setTextMatrix(
                    CanvasMatrix.IDENTITY).build(),
            font -> CanvasProgram.version1().endText().build(),
            font -> CanvasProgram.version1().beginText(
                    font, 12d, TextRenderingMode.FILL, CanvasMatrix.IDENTITY)
                    .endText().build(),
            font -> CanvasProgram.version1().beginText(
                    font, 12d, TextRenderingMode.FILL, CanvasMatrix.IDENTITY)
                    .showGlyph(new byte[] {65})
                    .showGlyph(new byte[] {66}).endText().build(),
            font -> openText(font).showGlyph(new byte[] {65})
                    .setTextMatrix(CanvasMatrix.IDENTITY).endText().build(),
            font -> openText(font).setTextMatrix(
                    CanvasMatrix.IDENTITY).showGlyph(new byte[] {65})
                    .endText().build(),
            font -> openText(font).beginText(
                    font, 12d, TextRenderingMode.FILL, CanvasMatrix.IDENTITY)
                    .showGlyph(new byte[] {65}).endText().build(),
            font -> openText(font).moveTo(1d, 1d)
                    .showGlyph(new byte[] {65}).endText().build(),
            font -> openText(font).lineTo(1d, 1d)
                    .showGlyph(new byte[] {65}).endText().build(),
            font -> openText(font).curveTo(
                    1d, 1d, 2d, 2d, 3d, 3d)
                    .showGlyph(new byte[] {65}).endText().build(),
            font -> openText(font).closePath()
                    .showGlyph(new byte[] {65}).endText().build(),
            font -> openText(font).stroke()
                    .showGlyph(new byte[] {65}).endText().build(),
            font -> openText(font).fill(CanvasWindingRule.NONZERO)
                    .showGlyph(new byte[] {65}).endText().build(),
            font -> openText(font).clip(CanvasWindingRule.EVEN_ODD)
                    .showGlyph(new byte[] {65}).endText().build(),
            font -> openText(font).transform(CanvasMatrix.IDENTITY)
                    .showGlyph(new byte[] {65}).endText().build(),
            font -> openText(font).restoreState()
                    .showGlyph(new byte[] {65}).endText().build(),
            font -> CanvasProgram.version1().beginText(
                    font, 12d, TextRenderingMode.FILL, CanvasMatrix.IDENTITY)
                    .showGlyph(new byte[] {65}).build(),
            font -> CanvasProgram.version1().beginText(
                    font, 12d, TextRenderingMode.FILL, CanvasMatrix.IDENTITY)
                    .saveState().showGlyph(new byte[] {65}).endText().build(),
            font -> CanvasProgram.version1().beginText(
                    font, 0d, TextRenderingMode.FILL, CanvasMatrix.IDENTITY)
                    .showGlyph(new byte[] {65}).endText().build(),
            font -> CanvasProgram.version1().beginText(
                    font, 12d, TextRenderingMode.FILL,
                    CanvasMatrix.of(1d, 0d, 0d, 1d,
                            Double.POSITIVE_INFINITY, 0d))
                    .showGlyph(new byte[] {65}).endText().build(),
            font -> CanvasProgram.version1()
                    .moveTo(Double.NaN, 0d).lineTo(1d, 1d).stroke().build(),
            font -> CanvasProgram.version1()
                    .transform(CanvasMatrix.of(
                            1d, 0d, 0d, 1d, 1000000001d, 0d)).build(),
            font -> CanvasProgram.version1().beginText(
                    font,
                    1000001d,
                    TextRenderingMode.FILL,
                    CanvasMatrix.IDENTITY)
                    .showGlyph(new byte[] {65}).endText().build(),
            font -> CanvasProgram.version1().beginText(
                    font, 12d, TextRenderingMode.FILL, CanvasMatrix.IDENTITY)
                    .showGlyph(new byte[0]).endText().build(),
            font -> CanvasProgram.version1().beginText(
                    font, 12d, TextRenderingMode.FILL, CanvasMatrix.IDENTITY)
                    .showGlyph(new byte[] {1, 2, 3, 4, 5}).endText().build(),
            font -> tooDeepProgram(),
            font -> tooManyInstructions()
        };

        for (int index = 0; index < invalid.length; index++) {
            Path target = path("invalid-program-target-" + index + ".pdf");
            Files.write(target, SENTINEL);
            byte[] sourceBefore = Files.readAllBytes(source);
            try {
                final InvalidProgramFactory factory = invalid[index];
                new DocumentWorkflow().execute(
                        rewriteRequest(source, target),
                        session -> {
                            session.execute(DrawCanvas.version1(
                                    1,
                                    factory.create(canvasFont(session))));
                            return null;
                        });
                fail("Expected invalid Canvas Program " + index);
            } catch (DocumentFailure failure) {
                assertCanvasFailure(
                        failure,
                        DocumentFailureCode.CANVAS_PROGRAM_INVALID,
                        "The Canvas Program is invalid.");
            }
            assertArrayEquals(sourceBefore, Files.readAllBytes(source));
            assertArrayEquals(SENTINEL, Files.readAllBytes(target));
        }
    }

    @Test
    public void invalidPageAndFontReferencesUseStableCanvasFailures()
            throws Exception {
        Path source = path("invalid-reference-source.pdf");
        writeCanvasFixture(source, false, false);

        assertRejected(
                source,
                "bad-page",
                session -> DrawCanvas.version1(2, simplePath()),
                DocumentFailureCode.PAGE_RANGE_INVALID,
                "The Canvas page selection is invalid.");

        assertRejected(
                source,
                "page-as-font",
                session -> DrawCanvas.version1(
                        1,
                        oneGlyph(
                                CanvasFont.version1(session.query(
                                        PageObjectReference.version1(1))),
                                20d)),
                DocumentFailureCode.CANVAS_RESOURCE_INVALID,
                "The Canvas Font resource is invalid.");

        CanvasFont expired = new DocumentWorkflow().execute(
                WorkflowRequest.open(source, SaveMode.REWRITE),
                this::canvasFont).getResult();
        assertRejected(
                source,
                "other-session-font",
                session -> DrawCanvas.version1(1, oneGlyph(expired, 20d)),
                DocumentFailureCode.CANVAS_RESOURCE_INVALID,
                "The Canvas Font resource is invalid.");

        assertRejected(
                source,
                "wrong-glyph-width",
                session -> DrawCanvas.version1(
                        1,
                        CanvasProgram.version1()
                                .beginText(
                                        canvasFont(session),
                                        12d,
                                        TextRenderingMode.FILL,
                                        CanvasMatrix.IDENTITY)
                                .showGlyph(new byte[] {0, 65})
                                .endText()
                                .build()),
                DocumentFailureCode.CANVAS_RESOURCE_INVALID,
                "The Canvas Font resource is invalid.");
    }

    @Test
    public void unsafeExistingContentOrResourcesAreRejectedBeforeMutation()
            throws Exception {
        Path source = path("unsafe-content-source.pdf");
        Path target = path("unsafe-content-target.pdf");
        writeCanvasFixture(source, true, false);
        byte[] sourceBefore = Files.readAllBytes(source);
        Files.write(target, SENTINEL);

        try {
            new DocumentWorkflow().execute(
                    rewriteRequest(source, target),
                    session -> {
                        session.execute(DrawCanvas.version1(1, simplePath()));
                        return null;
                    });
            fail("Expected preservation rejection");
        } catch (DocumentFailure failure) {
            assertCanvasFailure(
                    failure,
                    DocumentFailureCode.CANVAS_PRESERVATION_UNSUPPORTED,
                    "The page content or resources cannot be preserved safely for Canvas drawing.");
        }
        assertArrayEquals(sourceBefore, Files.readAllBytes(source));
        assertArrayEquals(SENTINEL, Files.readAllBytes(target));

        Path malformedResources = path("unsafe-resources-source.pdf");
        writeMalformedResourcesFixture(malformedResources);
        assertRejected(
                malformedResources,
                "unsafe-resources",
                session -> DrawCanvas.version1(1, simplePath()),
                DocumentFailureCode.CANVAS_PRESERVATION_UNSUPPORTED,
                "The page content or resources cannot be preserved safely for Canvas drawing.");
    }

    @Test
    public void signaturesAndPasswordPermissionsAreExplicitlyClassified()
            throws Exception {
        Path signed = path("signed-source.pdf");
        Path signedTarget = path("signed-target.pdf");
        Files.write(
                signed,
                ProjectOwnedSignatureFixtures.ordinaryApprovalSignature());
        Files.write(signedTarget, SENTINEL);
        byte[] signedBefore = Files.readAllBytes(signed);

        try {
            new DocumentWorkflow().execute(
                    incrementalRequest(signed, signedTarget),
                    session -> {
                        session.execute(DrawCanvas.version1(1, simplePath()));
                        return null;
                    });
            fail("Expected Existing Signature rejection");
        } catch (DocumentFailure failure) {
            assertCanvasFailure(
                    failure,
                    DocumentFailureCode.SIGNATURE_POLICY_REJECTED,
                    "The Existing Signature policy does not permit Canvas drawing.");
        }
        assertArrayEquals(signedBefore, Files.readAllBytes(signed));
        assertArrayEquals(SENTINEL, Files.readAllBytes(signedTarget));

        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.open(signed, SaveMode.REWRITE),
                    session -> {
                        session.execute(DrawCanvas.version1(1, simplePath()));
                        return null;
                    });
            fail("Expected target-free signed Canvas rewrite rejection");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.SIGNATURE_POLICY_REJECTED,
                    failure.getCode());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(
                    "The Existing Signature policy does not permit Canvas drawing.",
                    failure.getDiagnostic());
            assertTrue(failure.getPublicationReceipts().isEmpty());
        }
        assertArrayEquals(signedBefore, Files.readAllBytes(signed));

        Path certified = path("certified-source.pdf");
        Path certifiedTarget = path("certified-target.pdf");
        Files.write(certified, ProjectOwnedSignatureFixtures.docMdpSignature(3));
        Files.write(certifiedTarget, SENTINEL);
        byte[] certifiedBefore = Files.readAllBytes(certified);
        try {
            new DocumentWorkflow().execute(
                    incrementalRequest(certified, certifiedTarget),
                    session -> {
                        session.execute(DrawCanvas.version1(1, simplePath()));
                        return null;
                    });
            fail("Expected DocMDP P=3 Canvas rejection");
        } catch (DocumentFailure failure) {
            assertCanvasFailure(
                    failure,
                    DocumentFailureCode.SIGNATURE_POLICY_REJECTED,
                    "The Existing Signature policy does not permit Canvas drawing.");
        }
        assertArrayEquals(certifiedBefore, Files.readAllBytes(certified));
        assertArrayEquals(SENTINEL, Files.readAllBytes(certifiedTarget));

        Path signedRewriteTarget = path("signed-rewrite-target.pdf");
        Files.write(signedRewriteTarget, SENTINEL);
        try {
            new DocumentWorkflow().execute(
                    rewriteRequest(signed, signedRewriteTarget),
                    session -> {
                        session.execute(DrawCanvas.version1(1, simplePath()));
                        return null;
                    });
            fail("Expected signed Canvas rewrite rejection");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.SIGNED_REWRITE_REJECTED,
                    failure.getCode());
            assertEquals(
                    "document.incremental-signature.protect",
                    failure.getCapabilityId());
            assertEquals(
                    "A Source with an Existing Signature cannot be published with REWRITE.",
                    failure.getDiagnostic());
            assertEquals(1, failure.getPublicationReceipts().size());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
        }
        assertArrayEquals(signedBefore, Files.readAllBytes(signed));
        assertArrayEquals(SENTINEL, Files.readAllBytes(signedRewriteTarget));

        PasswordCredential owner = PasswordCredential.of(
                new char[] {'o', 'w', 'n', 'e', 'r'});
        PasswordCredential user = PasswordCredential.of(
                new char[] {'u', 's', 'e', 'r'});
        try {
            Path protectedSource = path("protected-source.pdf");
            PasswordSecurityPolicy security = PasswordSecurityPolicy.builder(
                            owner,
                            user)
                    .permissions(DocumentPermissions.builder().build())
                    .build();
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .target("output", PublicationTarget.path(
                                    protectedSource))
                            .saveMode(SaveMode.REWRITE)
                            .outputPolicy(PdfOutputPolicy
                                    .version(PdfVersion.PDF_1_7)
                                    .withPasswordSecurity(security))
                            .build(),
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        return null;
                    });

            Path protectedTarget = path("protected-target.pdf");
            Files.write(protectedTarget, SENTINEL);
            byte[] protectedBefore = Files.readAllBytes(protectedSource);
            try {
                new DocumentWorkflow().execute(
                        WorkflowRequest.builder()
                                .source("input", DocumentSource
                                        .path(protectedSource)
                                        .withCredential(user))
                                .primarySource("input")
                                .target("output", PublicationTarget.path(
                                        protectedTarget))
                                .saveMode(SaveMode.INCREMENTAL)
                                .build(),
                        session -> {
                            session.execute(DrawCanvas.version1(
                                    1,
                                    simplePath()));
                            return null;
                        });
                fail("Expected Canvas permission rejection");
            } catch (DocumentFailure failure) {
                assertCanvasFailure(
                        failure,
                        DocumentFailureCode.DOCUMENT_PERMISSION_DENIED,
                        "The Source credential does not authorize Canvas drawing.");
            }
            assertArrayEquals(
                    protectedBefore,
                    Files.readAllBytes(protectedSource));
            assertArrayEquals(SENTINEL, Files.readAllBytes(protectedTarget));
        } finally {
            owner.close();
            user.close();
        }
    }

    private CanvasFont canvasFont(DocumentSession session)
            throws DocumentFailure {
        DocumentResourceInventory inventory = resources(session);
        if (inventory.getFonts().size() != 1
                || !inventory.getFonts().get(0)
                        .getObjectReference().isPresent()) {
            throw new AssertionError("Expected one indirect project Font");
        }
        return CanvasFont.version1(inventory.getFonts().get(0)
                .getObjectReference().get());
    }

    private static DocumentResourceInventory resources(DocumentSession session)
            throws DocumentFailure {
        return session.query(ExtractImagesAndResources.version1(
                resourceLimits(),
                ImageByteAccess.NONE));
    }

    private static CanvasProgram representativeProgram(CanvasFont font) {
        CanvasProgram.Builder program = CanvasProgram.version1()
                .saveState()
                .transform(CanvasMatrix.of(1d, 0d, 0d, 1d, 5d, 7d))
                .moveTo(10d, 10d).lineTo(40d, 40d).stroke()
                .saveState()
                .moveTo(10d, 10d)
                .curveTo(20d, 20d, 30d, 40d, 50d, 50d)
                .fill(CanvasWindingRule.NONZERO)
                .restoreState()
                .moveTo(60d, 60d).lineTo(100d, 60d)
                .lineTo(100d, 100d).lineTo(60d, 100d).closePath()
                .moveTo(70d, 70d).lineTo(90d, 70d)
                .lineTo(90d, 90d).lineTo(70d, 90d).closePath()
                .fill(CanvasWindingRule.EVEN_ODD)
                .saveState()
                .moveTo(0d, 0d).lineTo(150d, 0d)
                .lineTo(150d, 150d).closePath()
                .clip(CanvasWindingRule.NONZERO)
                .moveTo(0d, 75d).lineTo(150d, 75d).stroke()
                .restoreState()
                .saveState()
                .moveTo(0d, 0d).lineTo(150d, 0d)
                .lineTo(150d, 150d).closePath()
                .clip(CanvasWindingRule.EVEN_ODD)
                .moveTo(75d, 0d).lineTo(75d, 150d).stroke()
                .restoreState()
                .restoreState();
        TextRenderingMode[] modes = TextRenderingMode.values();
        for (int index = 0; index < modes.length; index++) {
            program.saveState()
                    .beginText(
                            font,
                            12d,
                            modes[index],
                            CanvasMatrix.of(
                                    1d, 0d, 0d, 1d,
                                    20d + index * 20d,
                                    60d + index * 5d))
                    .showGlyph(new byte[] {65})
                    .endText()
                    .restoreState();
        }
        return program.build();
    }

    private static CanvasProgram oneGlyph(CanvasFont font, double x) {
        return CanvasProgram.version1()
                .beginText(
                        font,
                        12d,
                        TextRenderingMode.FILL,
                        CanvasMatrix.of(1d, 0d, 0d, 1d, x, 40d))
                .showGlyph(new byte[] {65})
                .endText()
                .build();
    }

    private static CanvasProgram.Builder openText(CanvasFont font) {
        return CanvasProgram.version1().beginText(
                font,
                12d,
                TextRenderingMode.FILL,
                CanvasMatrix.IDENTITY);
    }

    private static CanvasProgram tooDeepProgram() {
        CanvasProgram.Builder builder = CanvasProgram.version1();
        for (int depth = 0; depth < 65; depth++) {
            builder.saveState();
        }
        return builder.build();
    }

    private static CanvasProgram tooManyInstructions() {
        CanvasProgram.Builder builder = CanvasProgram.version1();
        for (int instruction = 0; instruction < 10001; instruction++) {
            builder.transform(CanvasMatrix.IDENTITY);
        }
        return builder.build();
    }

    private static CanvasProgram simplePath() {
        return CanvasProgram.version1()
                .moveTo(10d, 10d)
                .lineTo(20d, 20d)
                .stroke()
                .build();
    }

    private void assertRejected(
            Path source,
            String name,
            CommandFactory command,
            DocumentFailureCode code,
            String diagnostic) throws Exception {
        Path target = path(name + "-target.pdf");
        Files.write(target, SENTINEL);
        byte[] sourceBefore = Files.readAllBytes(source);
        try {
            new DocumentWorkflow().execute(
                    rewriteRequest(source, target),
                    session -> {
                        session.execute(command.create(session));
                        return null;
                    });
            fail("Expected " + name + " rejection");
        } catch (DocumentFailure failure) {
            assertCanvasFailure(failure, code, diagnostic);
        }
        assertArrayEquals(sourceBefore, Files.readAllBytes(source));
        assertArrayEquals(SENTINEL, Files.readAllBytes(target));
    }

    private static void assertCanvasFailure(
            DocumentFailure failure,
            DocumentFailureCode code,
            String diagnostic) {
        assertEquals(code, failure.getCode());
        assertEquals(CAPABILITY, failure.getCapabilityId());
        assertEquals(diagnostic, failure.getDiagnostic());
        assertEquals(1, failure.getPublicationReceipts().size());
        assertEquals(
                PublicationStatus.NOT_ATTEMPTED,
                failure.getPublicationReceipts().get(0).getStatus());
        assertFalse(failure.getDiagnostic().contains("/"));
        assertFalse(failure.getDiagnostic().contains("org.apache"));
    }

    private static String pageContent(Path source, int pageNumber)
            throws DocumentFailure {
        return new DocumentWorkflow().execute(
                WorkflowRequest.open(source, SaveMode.REWRITE),
                session -> pageContent(session, pageNumber)).getResult();
    }

    private static String pageContent(
            DocumentSession session,
            int pageNumber) throws DocumentFailure {
        PdfDictionary page = inspectDictionary(
                session,
                session.query(PageObjectReference.version1(pageNumber)));
        PdfValue contents = resolve(
                session,
                page.get(PdfName.of("Contents")));
        StringBuilder result = new StringBuilder();
        if (contents instanceof PdfStream) {
            result.append(new String(
                    ((PdfStream) contents).readBytes(),
                    StandardCharsets.US_ASCII));
        } else {
            PdfArray streams = (PdfArray) contents;
            for (int index = 0; index < streams.size(); index++) {
                PdfStream stream = (PdfStream) resolve(
                        session,
                        streams.get(index));
                result.append(new String(
                        stream.readBytes(),
                        StandardCharsets.US_ASCII));
                result.append('\n');
            }
        }
        return result.toString();
    }

    private static PdfDictionary inspectDictionary(
            DocumentSession session,
            ObjectReference reference) throws DocumentFailure {
        return (PdfDictionary) session.query(InspectObject.version1(
                reference,
                PdfInspectionLimits.of(128, 16L * 1024L * 1024L)));
    }

    private static PdfValue resolve(
            DocumentSession session,
            PdfValue value) throws DocumentFailure {
        if (value instanceof PdfIndirectReference) {
            return session.query(InspectObject.version1(
                    ((PdfIndirectReference) value).getReference(),
                    PdfInspectionLimits.of(128, 16L * 1024L * 1024L)));
        }
        return value;
    }

    private static WorkflowRequest rewriteRequest(Path source, Path target) {
        return WorkflowRequest.builder()
                .source("input", DocumentSource.path(source))
                .primarySource("input")
                .target("output", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .build();
    }

    private static WorkflowRequest incrementalRequest(
            Path source,
            Path target) {
        return WorkflowRequest.builder()
                .source("input", DocumentSource.path(source))
                .primarySource("input")
                .target("output", PublicationTarget.path(target))
                .saveMode(SaveMode.INCREMENTAL)
                .build();
    }

    private static ResourceExtractionLimits resourceLimits() {
        return ResourceExtractionLimits.builder()
                .maximumPages(4)
                .maximumPageTreeNodes(32)
                .maximumTraversedResourceValues(1024L)
                .maximumResourceTraversalDepth(16)
                .maximumDecodedPixels(0L)
                .maximumDecompressedBytes(1024L * 1024L)
                .maximumReturnedBytes(0L)
                .build();
    }

    private static ExtractionLimits textLimits() {
        return ExtractionLimits.builder()
                .maximumPages(2)
                .maximumPageTreeNodes(32)
                .maximumContentStreams(64)
                .maximumContentStreamDepth(4)
                .maximumDecodedBytes(2L * 1024L * 1024L)
                .maximumTextItems(32)
                .maximumUnicodeCodePoints(128)
                .maximumToUnicodeMappings(64)
                .maximumFontDataEntries(64)
                .maximumMarkedContentSequences(16)
                .maximumMarkedContentDepth(8)
                .maximumStructureElements(16)
                .maximumStructureItems(16)
                .maximumStructureDepth(8)
                .maximumRoleMappings(8)
                .build();
    }

    private static void assertDecimal(long expected, BigDecimal actual) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual));
    }

    private static List<ContentOperation> contentOperations(String content) {
        List<ContentOperation> operations =
                new ArrayList<ContentOperation>();
        List<String> operands = new ArrayList<String>();
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return operations;
        }
        for (String token : trimmed.split("\\s+")) {
            if (isObservedOperator(token)) {
                operations.add(new ContentOperation(token, operands));
                operands = new ArrayList<String>();
            } else {
                operands.add(token);
            }
        }
        if (!operands.isEmpty()) {
            throw new AssertionError("Unexpected trailing content operands");
        }
        return operations;
    }

    private static boolean isObservedOperator(String token) {
        return "q".equals(token) || "Q".equals(token)
                || "cm".equals(token) || "m".equals(token)
                || "l".equals(token) || "c".equals(token)
                || "h".equals(token) || "S".equals(token)
                || "f".equals(token) || "f*".equals(token)
                || "W".equals(token) || "W*".equals(token)
                || "n".equals(token) || "BT".equals(token)
                || "Tf".equals(token) || "Tr".equals(token)
                || "Tm".equals(token) || "Tj".equals(token)
                || "ET".equals(token);
    }

    private static ExpectedOperation expected(
            String operator,
            double... operands) {
        return new ExpectedOperation(operator, operands);
    }

    private static boolean containsOperation(
            List<ContentOperation> operations,
            ExpectedOperation expected) {
        for (ContentOperation operation : operations) {
            if (expected.matches(operation)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSequence(
            List<ContentOperation> operations,
            ExpectedOperation... expected) {
        for (int start = 0;
                start + expected.length <= operations.size();
                start++) {
            boolean match = true;
            for (int offset = 0; offset < expected.length; offset++) {
                if (!expected[offset].matches(operations.get(start + offset))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    private static int countOperations(
            List<ContentOperation> operations,
            String operator) {
        int count = 0;
        for (ContentOperation operation : operations) {
            if (operator.equals(operation.operator)) {
                count++;
            }
        }
        return count;
    }

    private static int maximumGraphicsDepth(
            List<ContentOperation> operations) {
        int depth = 0;
        int maximum = 0;
        for (ContentOperation operation : operations) {
            if ("q".equals(operation.operator)) {
                depth++;
                maximum = Math.max(maximum, depth);
            } else if ("Q".equals(operation.operator)) {
                depth--;
                if (depth < 0) {
                    throw new AssertionError("Unmatched graphics restore");
                }
            }
        }
        assertEquals(0, depth);
        return maximum;
    }

    private static byte[] singleShownGlyph(
            List<ContentOperation> operations) {
        byte[] glyph = null;
        for (ContentOperation operation : operations) {
            if (!"Tj".equals(operation.operator)) {
                continue;
            }
            if (glyph != null || operation.operands.size() != 1) {
                throw new AssertionError("Expected exactly one shown glyph");
            }
            glyph = decodeHexadecimalString(operation.operands.get(0));
        }
        if (glyph == null) {
            throw new AssertionError("Expected one shown glyph");
        }
        return glyph;
    }

    private static byte[] decodeHexadecimalString(String value) {
        if (value.length() < 2 || value.charAt(0) != '<'
                || value.charAt(value.length() - 1) != '>'
                || (value.length() - 2) % 2 != 0) {
            throw new AssertionError("Expected one hexadecimal string");
        }
        byte[] decoded = new byte[(value.length() - 2) / 2];
        for (int index = 0; index < decoded.length; index++) {
            int high = Character.digit(value.charAt(1 + index * 2), 16);
            int low = Character.digit(value.charAt(2 + index * 2), 16);
            if (high < 0 || low < 0) {
                throw new AssertionError("Invalid hexadecimal string");
            }
            decoded[index] = (byte) (high << 4 | low);
        }
        return decoded;
    }

    private static final class ContentOperation {

        private final String operator;
        private final List<String> operands;

        ContentOperation(String operator, List<String> operands) {
            this.operator = operator;
            this.operands = new ArrayList<String>(operands);
        }
    }

    private static final class ExpectedOperation {

        private final String operator;
        private final double[] operands;

        ExpectedOperation(String operator, double[] operands) {
            this.operator = operator;
            this.operands = operands.clone();
        }

        boolean matches(ContentOperation operation) {
            if (!operator.equals(operation.operator)
                    || operands.length != operation.operands.size()) {
                return false;
            }
            for (int index = 0; index < operands.length; index++) {
                try {
                    if (BigDecimal.valueOf(operands[index]).compareTo(
                            new BigDecimal(operation.operands.get(index)))
                            != 0) {
                        return false;
                    }
                } catch (NumberFormatException notNumeric) {
                    return false;
                }
            }
            return true;
        }
    }

    private Path path(String name) {
        return temporaryFolder.getRoot().toPath().resolve(name);
    }

    private static void writeCanvasFixture(
            Path target,
            boolean unbalanced,
            boolean noFont) throws Exception {
        String content = unbalanced
                ? "q\n"
                : "q\n1 1 m\n2 2 l\nS\nQ\n";
        String resources = noFont
                ? "<< /FolioKeep /Kept >>"
                : "<< /Font << /F1 5 0 R >> /FolioKeep /Kept >>";
        if (noFont) {
            writePdf(
                    target,
                    "<< /Type /Catalog /Pages 2 0 R >>",
                    "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] "
                            + "/Resources " + resources
                            + " /Contents 4 0 R >>",
                    streamObject(content));
        } else {
            writePdf(
                    target,
                    "<< /Type /Catalog /Pages 2 0 R >>",
                    "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] "
                            + "/Resources " + resources
                            + " /Contents 4 0 R >>",
                    streamObject(content),
                    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                            + "/Encoding /WinAnsiEncoding >>");
        }
    }

    private static void writeTwoPageResourceFixture(Path target)
            throws Exception {
        writePdf(
                target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 2 /Kids [3 0 R 4 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] "
                        + "/Resources << >> >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] "
                        + "/Resources << /Font << /F1 5 0 R >> >> >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                        + "/Encoding /WinAnsiEncoding >>");
    }

    private static void writeMalformedResourcesFixture(Path target)
            throws Exception {
        writePdf(
                target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] "
                        + "/Resources /NotADictionary >>");
    }

    private static void writeCompositeFontFixture(Path target)
            throws Exception {
        writePdf(
                target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] "
                        + "/Resources << /Font << /F0 4 0 R >> >> >>",
                "<< /Type /Font /Subtype /Type0 /BaseFont /FolioComposite "
                        + "/Encoding /Identity-H "
                        + "/DescendantFonts [5 0 R] >>",
                "<< /Type /Font /Subtype /CIDFontType2 "
                        + "/BaseFont /FolioComposite "
                        + "/CIDSystemInfo << /Registry (Adobe) "
                        + "/Ordering (Identity) /Supplement 0 >> "
                        + "/DW 1000 /CIDToGIDMap /Identity >>");
    }

    private static String streamObject(String content) {
        return "<< /Length "
                + content.getBytes(StandardCharsets.US_ASCII).length
                + " >>\nstream\n" + content + "endstream";
    }

    private static void writePdf(Path target, String... objectBodies)
            throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write("%PDF-1.7\n%FolioT17Fixture\n"
                .getBytes(StandardCharsets.US_ASCII));
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

    private interface InvalidProgramFactory {
        CanvasProgram create(CanvasFont font);
    }

    private interface CommandFactory {
        DrawCanvas create(DocumentSession session) throws DocumentFailure;
    }
}
