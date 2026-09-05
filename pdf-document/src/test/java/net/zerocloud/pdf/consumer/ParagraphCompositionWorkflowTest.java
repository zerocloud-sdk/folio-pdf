package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import net.zerocloud.pdf.CharacterMapping;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentPermissions;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ExtractionLimits;
import net.zerocloud.pdf.PageText;
import net.zerocloud.pdf.PasswordCredential;
import net.zerocloud.pdf.PasswordSecurityPolicy;
import net.zerocloud.pdf.PdfOutputPolicy;
import net.zerocloud.pdf.PdfVersion;
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
import net.zerocloud.pdf.TextItem;
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.WorkflowResourcePolicy;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.composition.CanvasColor;
import net.zerocloud.pdf.composition.CanvasColorSpace;
import net.zerocloud.pdf.composition.CanvasProgram;
import net.zerocloud.pdf.composition.CanvasRectangle;
import net.zerocloud.pdf.composition.CanvasResourceLimits;
import net.zerocloud.pdf.composition.CanvasTransparencyGroup;
import net.zerocloud.pdf.composition.CanvasWindingRule;
import net.zerocloud.pdf.composition.CompositionLimits;
import net.zerocloud.pdf.composition.FontLimits;
import net.zerocloud.pdf.composition.FontSelection;
import net.zerocloud.pdf.composition.FontSource;
import net.zerocloud.pdf.composition.LayoutPage;
import net.zerocloud.pdf.composition.PageMargins;
import net.zerocloud.pdf.composition.Paragraph;
import net.zerocloud.pdf.composition.ParagraphFlow;
import net.zerocloud.pdf.composition.ReferenceFontSet;
import net.zerocloud.pdf.composition.command.ComposeParagraphs;
import net.zerocloud.pdf.query.ExtractTextAndStructure;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.PageObjectReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** T24 contracts through the public workflow, publication and reopened PDF values. */
@RunWith(Parameterized.class)
public final class ParagraphCompositionWorkflowTest {
    private static final double EPSILON = 0.0001;
    private static final String CAPABILITY = "composition.layout.paragraph-areas";
    private static final byte[] SENTINEL = {31, 41, 59};
    private final WorkflowExecutionProfile profile;
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> profiles() {
        return Arrays.asList(new Object[][] {{WorkflowExecutionProfile.IN_PROCESS},
            {WorkflowExecutionProfile.HARDENED_WORKER}});
    }
    public ParagraphCompositionWorkflowTest(WorkflowExecutionProfile profile) { this.profile = profile; }

    @Test
    public void mixedParagraphFlowsAcrossTwoColumnsAndANewPage() throws Exception {
        ParagraphFlow flow = ParagraphFlow.version1(selection())
                .page(LayoutPage.version1(100, 80, margins(10),
                        CanvasRectangle.of(0, 0, 18, 20), CanvasRectangle.of(40, 0, 58, 20)))
                .page(page(80, 60, 10))
                .paragraph(Paragraph.version1(10).text("AA AA ", 10)
                        .graphic(square(), 8, 8).text("B\u03a9 B\u03a9 B\u03a9", 10).build()).build();
        Path output = publish(flow, limits().build());
        List<PageText> pages = read(output);
        assertEquals(2, pages.size());
        assertEquals("AA AA B\u03a9 ", pages.get(0).getText());
        assertEquals("B\u03a9 B\u03a9", pages.get(1).getText());
        position(pages.get(0), 0, 10, 23, 6);
        position(pages.get(0), 3, 10, 13, 6);
        position(pages.get(0), 6, 50, 12.8, 6.5);
        position(pages.get(0), 7, 56.5, 12.8, 7);
        position(pages.get(1), 0, 10, 42.8, 6.5);
        new DocumentWorkflow().execute(open(output).build(), session -> {
            // This observes published PDF drawing semantics via the public value model.
            String content = content(session, 1);
            assertTrue(content, content.contains("8 0 0 8 50 22 cm"));
            return null;
        });
    }

    @Test
    public void pageSizeMarginsAndWidthCapsDetermineIndependentBreakpoints() throws Exception {
        assertLayout(page(38, 40, 10), 0, 10, "AAA|AAA", 1);
        assertLayout(page(32, 40, 10), 0, 10, "AA|AA/AA", 2);
        assertLayout(page(38, 30, 10), 0, 10, "AAA/AAA", 2);
        assertLayout(page(38, 40, 13), 0, 10, "AA/AA/AA", 3);
        assertLayout(page(80, 40, 10), 12, 10, "AA|AA/AA", 2);
    }

    @Test
    public void fractionalAdvancesFitExactlyWithoutCreatingAnExtraLine() throws Exception {
        // Four 600-unit advances at 12.1 points occupy exactly 29.04 points.
        for (double width : new double[] {29.04, 29.04 - 0.0000001}) {
            ParagraphFlow flow = ParagraphFlow.version1(selection()).page(page(100, 100, 0))
                    .paragraph(Paragraph.version1(16).text("AAAA", 12.1).maximumWidth(width).build()).build();
            CompositionLimits oneLine = limits().maximumLines(1).build();
            if (width < 29.04) {
                expectFailure(flow, oneLine, DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED);
            } else {
                PageText text = read(publish(flow, oneLine)).get(0);
                assertEquals("AAAA", text.getText());
                for (int index = 0; index < 4; index++) { position(text, index, index * 7.26, 91.53, 7.26); }
            }
        }
    }

    @Test
    public void fractionalLeadingFitsAnExactAreaHeight() throws Exception {
        for (double height : new double[] {40.4, 40.4 - 0.0000001}) {
            ParagraphFlow flow = ParagraphFlow.version1(selection()).page(page(100, height, 0))
                    .paragraph(Paragraph.version1(10.1).text("A\nA\nA\nA", 12.1).build()).build();
            if (height < 40.4) {
                expectFailure(flow, limits().build(), DocumentFailureCode.COMPOSITION_AREA_EXHAUSTED);
            } else {
                List<PageText> pages = read(publish(flow, limits().maximumLines(4).build()));
                assertEquals(1, pages.size());
                assertEquals("AAAA", pages.get(0).getText());
                for (int index = 0; index < 4; index++) {
                    position(pages.get(0), index, 0, 31.93 - index * 10.1, 7.26);
                }
            }
        }
    }

    @Test
    public void fractionalMarginsAdmitAnExactAreaAndRejectAnExcess() throws Exception {
        double[][] upperBounds = {{40.1, 40.1}, {40.1000001, 40.1}, {40.1, 40.1000001}};
        for (double[] bounds : upperBounds) {
            ParagraphFlow flow = ParagraphFlow.version1(selection())
                    .page(LayoutPage.version1(60.3, 60.3, margins(10.1),
                            CanvasRectangle.of(0, 0, bounds[0], bounds[1])))
                    .paragraph(Paragraph.version1(10).text("A", 10).build()).build();
            if (bounds[0] > 40.1 || bounds[1] > 40.1) {
                expectFailure(flow, limits().build(), DocumentFailureCode.COMPOSITION_INVALID);
            } else {
                List<PageText> pages = read(publish(flow, limits().build()));
                assertEquals(1, pages.size());
                assertEquals("A", pages.get(0).getText());
                position(pages.get(0), 0, 10.1, 43.2, 6);
            }
        }
    }

    @Test
    public void explicitAreaBreakSkipsTheRestOfTheCurrentArea() throws Exception {
        ParagraphFlow flow = ParagraphFlow.version1(selection())
                .page(LayoutPage.version1(100, 60, margins(10),
                        CanvasRectangle.of(0, 0, 30, 40), CanvasRectangle.of(40, 0, 70, 40)))
                .page(page(100, 60, 10))
                .paragraph(Paragraph.version1(12).text("A", 10).build()).areaBreak()
                .paragraph(Paragraph.version1(12).text("B", 10).build()).areaBreak()
                .paragraph(Paragraph.version1(12).text("Z", 10).build()).build();
        List<PageText> result = read(publish(flow, limits().build()));
        assertEquals("AB", result.get(0).getText());
        assertEquals("Z", result.get(1).getText());
        position(result.get(0), 0, 10, 43, 6);
        position(result.get(0), 1, 50, 43, 6.5);
        position(result.get(1), 0, 10, 43, 6.2);
    }

    @Test
    public void alignmentPositionsTextWithinTheDeclaredWidth() throws Exception {
        Paragraph.Alignment[] modes = {Paragraph.Alignment.LEFT, Paragraph.Alignment.CENTER,
            Paragraph.Alignment.RIGHT};
        double[] x = {10, 22.5, 35}; // A + space + B = 6 + 2.5 + 6.5 = 15 points.
        for (int index = 0; index < modes.length; index++) {
            ParagraphFlow flow = ParagraphFlow.version1(selection()).page(page(60, 60, 10))
                    .paragraph(Paragraph.version1(12).text("A B", 10).alignment(modes[index]).build()).build();
            PageText page = read(publish(flow, limits().build())).get(0);
            position(page, 0, x[index], 43, 6);
            position(page, 2, x[index] + 8.5, 43, 6.5);
        }
        ParagraphFlow justified = ParagraphFlow.version1(selection()).page(page(44, 60, 10))
                .paragraph(Paragraph.version1(12).text("A B A B", 10)
                        .alignment(Paragraph.Alignment.JUSTIFIED).build()).build();
        PageText page = read(publish(justified, limits().build())).get(0);
        assertEquals("A B A B", page.getText());
        position(page, 2, 25, 43, 6.5);
        position(page, 4, 10, 31, 6);
        position(page, 6, 18.5, 31, 6.5);
    }

    @Test
    public void leadingAndExplicitLineBreaksControlBaselinesAndPagination() throws Exception {
        assertLayout(page(32, 40, 10), 0, 8, "AA|AA/AA", 2);
        assertLayout(page(32, 40, 10), 0, 12, "AA/AA/AA", 3);
        ParagraphFlow flow = ParagraphFlow.version1(selection()).page(page(80, 80, 10))
                .paragraph(Paragraph.version1(14).text("A\n\nB", 10).build()).build();
        PageText page = read(publish(flow, limits().build())).get(0);
        assertEquals("AB", page.getText());
        position(page, 0, 10, 63, 6);
        position(page, 1, 10, 35, 6.5);
    }

    @Test
    public void sameReferenceFontBytesAndDeclarationsRepeatTheEntireLayout() throws Exception {
        WorkflowEnvironment environment = WorkflowEnvironment.builder().referenceFontSet(
                ReferenceFontSet.version1(FontSource.bytes(font("FolioPrimary")),
                        FontSource.bytes(font("FolioFallback")))).build();
        ParagraphFlow.Builder builder = ParagraphFlow.version1(FontSelection.referenceFontSet());
        for (int index = 0; index < 4; index++) { builder.page(page(38, 40, 10)); }
        ParagraphFlow flow = builder.paragraph(Paragraph.version1(10).text("AA B\u03a9 AA B\u03a9", 10).build()).build();
        List<PageText> first = read(publish(new DocumentWorkflow(environment), flow, limits().build()));
        for (int repeat = 0; repeat < 2; repeat++) {
            List<PageText> next = read(publish(new DocumentWorkflow(environment), flow, limits().build()));
            assertEquals(first.size(), next.size());
            for (int page = 0; page < first.size(); page++) {
                assertEquals(first.get(page).getText(), next.get(page).getText());
                assertEquals(first.get(page).getTextItems().size(), next.get(page).getTextItems().size());
                for (int item = 0; item < first.get(page).getTextItems().size(); item++) {
                    assertEquals(first.get(page).getTextItems().get(item).getGeometry(),
                            next.get(page).getTextItems().get(item).getGeometry());
                }
            }
        }
    }

    @Test
    public void batchOrderQueryBarriersAndSessionExpiryArePreserved() throws Exception {
        ParagraphFlow flow = single("AB");
        ComposeParagraphs command = ComposeParagraphs.version1(flow, limits().build());
        Path output = path();
        DocumentSession[] retained = new DocumentSession[1];
        Object result = new Object();
        WorkflowOutcome<Object> outcome = new DocumentWorkflow().execute(create(output).build(), session -> {
            retained[0] = session;
            session.executeBatch(Arrays.asList(AddBlankPage.INSTANCE, command));
            assertEquals(Integer.valueOf(2), session.query(PageCount.INSTANCE));
            assertEquals("AB", extract(session).get(1).getText());
            session.execute(command);
            assertEquals(Integer.valueOf(3), session.query(PageCount.INSTANCE));
            return result;
        });
        assertSame(result, outcome.getResult());
        assertEquals(3, read(output).size());
        try { retained[0].query(PageCount.INSTANCE); fail("Expired Session accepted query"); }
        catch (IllegalStateException expected) { /* Public Session lifetime is a programming contract. */ }
    }

    @Test
    public void callerFontStreamIsStagedOnceAndRemainsOpenAcrossFlows() throws Exception {
        byte[] bytes = font("FolioPrimary");
        TrackingStream source = new TrackingStream(bytes);
        ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(FontSource.stream(source)))
                .page(page(80, 40, 10)).paragraph(Paragraph.version1(10).text("AB", 10).build()).build();
        Path output = path();
        new DocumentWorkflow().execute(create(output).build(), session -> {
            session.execute(ComposeParagraphs.version1(flow, limits().build()));
            session.query(PageCount.INSTANCE);
            session.execute(ComposeParagraphs.version1(flow, limits().build()));
            return null;
        });
        assertFalse(source.closed);
        assertEquals(0, source.available());
        assertEquals(2, read(output).size());
    }

    @Test
    public void exhaustionOversizedGraphicsAndMissingGlyphsDoNotPublish() throws Exception {
        ParagraphFlow exhausted = ParagraphFlow.version1(selection()).page(page(26, 30, 10))
                .paragraph(Paragraph.version1(10).text("AA", 10).build()).build();
        expectFailure(exhausted, limits().build(), DocumentFailureCode.COMPOSITION_AREA_EXHAUSTED);
        ParagraphFlow graphic = ParagraphFlow.version1(selection()).page(page(80, 40, 10))
                .paragraph(Paragraph.version1(10).graphic(square(), 70, 5).build()).build();
        expectFailure(graphic, limits().build(), DocumentFailureCode.COMPOSITION_AREA_EXHAUSTED);
        expectFailure(single("Q"), limits().build(), DocumentFailureCode.FONT_GLYPH_MISSING);
    }

    @Test
    public void invalidDimensionsControlsAndUnpairedSurrogatesFailBeforeOpeningFonts() throws Exception {
        for (double width : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, 14401}) {
            expectFailure(ParagraphFlow.version1(selection()).page(page(width, 80, 10))
                    .paragraph(Paragraph.version1(10).text("A", 10).build()).build(),
                    limits().build(), DocumentFailureCode.COMPOSITION_INVALID);
        }
        for (String text : new String[] {"", "A\tB", "A\rB", "\ud800", "\udc00"}) {
            TrackingStream source = new TrackingStream(font("FolioPrimary"));
            ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(FontSource.stream(source)))
                    .page(page(80, 40, 10)).paragraph(Paragraph.version1(10).text(text, 10).build()).build();
            expectFailure(flow, limits().build(), DocumentFailureCode.COMPOSITION_INVALID);
            assertEquals(972, source.available());
            assertFalse(source.closed);
        }
        expectFailure(ParagraphFlow.version1(selection()).page(page(20, 20, 10))
                .paragraph(Paragraph.version1(10).text("A", 10).build()).build(),
                limits().build(), DocumentFailureCode.COMPOSITION_INVALID);
    }

    @Test
    public void declarationLineAndOperatorLimitsHaveExactBoundaries() throws Exception {
        ParagraphFlow flow = single("AB");
        CompositionLimits exact = limits().maximumPages(1).maximumAreas(1).maximumFlowItems(1)
                .maximumInlines(1).maximumLines(1).build();
        Path output = publish(flow, exact);
        long bytes = new DocumentWorkflow().execute(open(output).build(), session ->
                Long.valueOf(content(session, 1).getBytes(StandardCharsets.US_ASCII).length)).getResult();
        publish(flow, limits().maximumGeneratedContentBytes(bytes).build());
        expectFailure(flow, limits().maximumGeneratedContentBytes(bytes - 1).build(),
                DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED);
        CompositionLimits[] zero = {limits().maximumPages(0).build(), limits().maximumAreas(0).build(),
            limits().maximumFlowItems(0).build(), limits().maximumInlines(0).build(), limits().maximumLines(0).build()};
        for (CompositionLimits bound : zero) { expectFailure(flow, bound, DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED); }
        publish(flow, limits().fontLimits(fontLimits(2, 2000, 2, 2, 4096)).build());
        expectFailure(flow, limits().fontLimits(fontLimits(2, 2000, 1, 2, 4096)).build(),
                DocumentFailureCode.FONT_LIMIT_EXCEEDED);
        expectFailure(flow, limits().fontLimits(fontLimits(2, 1999, 2, 2, 4096)).build(),
                DocumentFailureCode.FONT_LIMIT_EXCEEDED);
        expectFailure(flow, limits().fontLimits(fontLimits(2, 2000, 2, 1, 4096)).build(),
                DocumentFailureCode.FONT_LIMIT_EXCEEDED);
    }

    @Test
    public void failedLayoutLeavesEarlierPagesObservableWithoutAppendingAPartialFlow() throws Exception {
        Path output = path();
        ParagraphFlow flow = ParagraphFlow.version1(selection()).page(page(26, 30, 10))
                .paragraph(Paragraph.version1(10).text("AA", 10).build()).build();
        new DocumentWorkflow().execute(create(output).build(), session -> {
            session.execute(AddBlankPage.INSTANCE);
            try { session.execute(ComposeParagraphs.version1(flow, limits().build())); fail("Expected exhaustion"); }
            catch (DocumentFailure expected) { assertEquals(DocumentFailureCode.COMPOSITION_AREA_EXHAUSTED, expected.getCode()); }
            assertEquals(Integer.valueOf(1), session.query(PageCount.INSTANCE));
            return null;
        });
        assertEquals("", read(output).get(0).getText());
    }

    @Test
    public void unsignedIncrementalAppendPreservesExistingPageContent() throws Exception {
        Path source = publish(single("A"), limits().build());
        byte[] original = Files.readAllBytes(source);
        Path output = path();
        ParagraphFlow flow = single("B");
        new DocumentWorkflow().execute(open(source).target("result", PublicationTarget.path(output))
                .saveMode(SaveMode.INCREMENTAL).build(), session -> {
                    session.execute(ComposeParagraphs.version1(flow, limits().build()));
                    return null;
                });
        List<PageText> pages = read(output);
        assertEquals(2, pages.size());
        assertEquals("A", pages.get(0).getText());
        assertEquals("B", pages.get(1).getText());
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test
    public void existingSignaturesRejectCompositionBeforeReadingCallerFonts() throws Exception {
        for (SaveMode mode : SaveMode.values()) {
            byte[] signed = ProjectOwnedSignatureFixtures.ordinaryApprovalSignature();
            Path source = path(); Files.write(source, signed);
            Path output = path(); Files.write(output, SENTINEL);
            TrackingStream font = new TrackingStream(font("FolioPrimary"));
            ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(FontSource.stream(font)))
                    .page(page(80, 40, 10)).paragraph(Paragraph.version1(10).text("A", 10).build()).build();
            try {
                new DocumentWorkflow().execute(open(source).target("result", PublicationTarget.path(output))
                        .saveMode(mode).build(), session -> {
                            session.execute(ComposeParagraphs.version1(flow, limits().build())); return null;
                        });
                fail("Signed composition accepted");
            } catch (DocumentFailure failure) {
                assertEquals(mode == SaveMode.REWRITE ? DocumentFailureCode.SIGNED_REWRITE_REJECTED
                        : DocumentFailureCode.SIGNATURE_POLICY_REJECTED, failure.getCode());
            }
            assertEquals(972, font.available());
            assertFalse(font.closed);
            assertArrayEquals(SENTINEL, Files.readAllBytes(output));
        }
    }

    @Test
    public void passwordUserRequiresBothModificationAndAssemblyPermission() throws Exception {
        try (PasswordCredential owner = PasswordCredential.of("t24-owner".toCharArray());
                PasswordCredential user = PasswordCredential.of("t24-user".toCharArray())) {
            for (int permissions = 0; permissions < 4; permissions++) {
                Path source = path();
                PasswordSecurityPolicy security = PasswordSecurityPolicy.builder(owner, user)
                        .permissions(DocumentPermissions.builder().allowModification((permissions & 1) != 0)
                                .allowDocumentAssembly((permissions & 2) != 0).build()).build();
                new DocumentWorkflow().execute(create(source).outputPolicy(
                        PdfOutputPolicy.version(PdfVersion.PDF_1_7).withPasswordSecurity(security)).build(),
                        session -> { session.execute(AddBlankPage.INSTANCE); return null; });
                Path output = path(); Files.write(output, SENTINEL);
                TrackingStream font = new TrackingStream(font("FolioPrimary"));
                ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(FontSource.stream(font)))
                        .page(page(80, 40, 10)).paragraph(Paragraph.version1(10).text("A", 10).build()).build();
                WorkflowRequest request = WorkflowRequest.builder()
                        .source("primary", DocumentSource.path(source).withCredential(user)).primarySource("primary")
                        .target("result", PublicationTarget.path(output)).saveMode(SaveMode.INCREMENTAL)
                        .executionProfile(profile).build();
                try {
                    WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(request, session -> {
                        session.execute(ComposeParagraphs.version1(flow, limits().build())); return null;
                    });
                    assertEquals("Both permissions are necessary", 3, permissions);
                    assertEquals(PublicationStatus.COMMITTED, outcome.getPublicationReceipts().get(0).getStatus());
                    int pages = new DocumentWorkflow().execute(WorkflowRequest.builder()
                            .source("primary", DocumentSource.path(output).withCredential(owner)).primarySource("primary")
                            .saveMode(SaveMode.REWRITE).executionProfile(profile).build(),
                            session -> session.query(PageCount.INSTANCE)).getResult();
                    assertEquals(2, pages);
                } catch (DocumentFailure failure) {
                    if (permissions == 3) { throw failure; }
                    assertEquals(DocumentFailureCode.DOCUMENT_PERMISSION_DENIED, failure.getCode());
                    assertEquals(CAPABILITY, failure.getCapabilityId());
                    assertEquals(972, font.available());
                    assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(0).getStatus());
                    assertArrayEquals(SENTINEL, Files.readAllBytes(output));
                }
                assertFalse(font.closed);
            }
        }
    }

    @Test
    public void workflowPageAndModeledMemoryLimitsAbortBeforePublication() throws Exception {
        ParagraphFlow simple = single("A");
        expectResourceFailure(simple, limits().build(), policy(0, 256L << 20), DocumentFailureCode.PAGE_LIMIT_EXCEEDED);
        TrackingStream font = new TrackingStream(font("FolioPrimary"));
        char[] many = new char[4096]; Arrays.fill(many, 'A');
        ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(FontSource.stream(font)))
                .page(page(80, 40, 10)).paragraph(Paragraph.version1(10).text(new String(many), 10).build()).build();
        expectResourceFailure(flow, limits().build(), policy(16, 1L << 20), DocumentFailureCode.MEMORY_LIMIT_EXCEEDED);
        assertEquals("Layout planning is bounded before font materialization", 972, font.available());
        assertFalse(font.closed);
    }

    @Test
    public void batchFailureDoesNotConsumeALaterOneShotFont() throws Exception {
        Path output = path(); Files.write(output, SENTINEL);
        TrackingStream font = new TrackingStream(font("FolioPrimary"));
        ParagraphFlow later = ParagraphFlow.version1(FontSelection.explicit(FontSource.stream(font)))
                .page(page(80, 40, 10)).paragraph(Paragraph.version1(10).text("A", 10).build()).build();
        ParagraphFlow first = ParagraphFlow.version1(selection()).page(page(0, 40, 10))
                .paragraph(Paragraph.version1(10).text("A", 10).build()).build();
        try {
            new DocumentWorkflow().execute(create(output).build(), session -> {
                session.executeBatch(Arrays.asList(ComposeParagraphs.version1(first, limits().build()),
                        ComposeParagraphs.version1(later, limits().build())));
                return null;
            });
            fail("Expected declaration failure");
        } catch (DocumentFailure failure) { assertEquals(DocumentFailureCode.COMPOSITION_INVALID, failure.getCode()); }
        assertEquals(972, font.available());
        assertFalse(font.closed);
        assertArrayEquals(SENTINEL, Files.readAllBytes(output));
    }

    @Test
    public void tallGraphicsAndDifferentTextSizesDetermineLineBoxes() throws Exception {
        ParagraphFlow flow = ParagraphFlow.version1(selection()).page(page(100, 100, 10))
                .paragraph(Paragraph.version1(10).text("A", 10).graphic(square(), 8, 20)
                        .text("B\nA", 20).build()).build();
        PageText page = read(publish(flow, limits().build())).get(0);
        position(page, 0, 10, 70, 6);
        position(page, 1, 24, 70, 13);
        position(page, 2, 10, 56, 12);
    }

    private void expectResourceFailure(ParagraphFlow flow, CompositionLimits limits,
            WorkflowResourcePolicy policy, DocumentFailureCode code) throws Exception {
        Path output = path(); Files.write(output, SENTINEL);
        try {
            new DocumentWorkflow().execute(create(output).resourcePolicy(policy).build(), session -> {
                session.execute(ComposeParagraphs.version1(flow, limits)); return null;
            });
            fail("Expected workflow limit");
        } catch (DocumentFailure failure) {
            assertEquals(code, failure.getCode());
            assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(0).getStatus());
        }
        assertArrayEquals(SENTINEL, Files.readAllBytes(output));
    }
    private static WorkflowResourcePolicy policy(int pages, long memory) {
        WorkflowResourcePolicy defaults = WorkflowResourcePolicy.safeDefaults();
        return WorkflowResourcePolicy.builder().maximumPages(pages).maximumOwnedMemoryBytes(memory)
                .maximumInputBytes(defaults.getMaximumInputBytes()).maximumObjects(defaults.getMaximumObjects())
                .maximumNestingDepth(defaults.getMaximumNestingDepth())
                .maximumDecompressedBytes(defaults.getMaximumDecompressedBytes())
                .maximumDecodedPixels(defaults.getMaximumDecodedPixels())
                .maximumTemporaryStorageBytes(defaults.getMaximumTemporaryStorageBytes())
                .maximumElapsedTime(defaults.getMaximumElapsedTime())
                .maximumConcurrentWorkflows(defaults.getMaximumConcurrentWorkflows()).build();
    }

    private void assertLayout(LayoutPage page, double width, double leading, String expected, int pageCount)
            throws Exception {
        ParagraphFlow.Builder flow = ParagraphFlow.version1(selection());
        for (int index = 0; index < 4; index++) { flow.page(page); }
        flow.paragraph(Paragraph.version1(leading).text("AAAAAA", 10).maximumWidth(width).build());
        List<PageText> actual = read(publish(flow.build(), limits().build()));
        assertEquals(pageCount, actual.size());
        StringBuilder breaks = new StringBuilder();
        for (PageText text : actual) {
            if (breaks.length() > 0) { breaks.append('/'); }
            double y = Double.NaN;
            for (TextItem item : text.getTextItems()) {
                double baseline = item.getGeometry().getF().doubleValue();
                if (!Double.isNaN(y) && Math.abs(y - baseline) > EPSILON) {
                    breaks.append('|');
                    assertEquals(leading, y - baseline, EPSILON);
                }
                y = baseline;
                breaks.append(item.getTextContribution());
            }
            assertEquals(page.getWidth(), text.getCropBoxRight().doubleValue(), EPSILON);
            assertEquals(page.getHeight(), text.getCropBoxTop().doubleValue(), EPSILON);
        }
        assertEquals(expected, breaks.toString());
    }

    private Path publish(ParagraphFlow flow, CompositionLimits limits) throws Exception {
        return publish(new DocumentWorkflow(), flow, limits);
    }
    private Path publish(DocumentWorkflow workflow, ParagraphFlow flow, CompositionLimits limits) throws Exception {
        Path output = path();
        WorkflowOutcome<Integer> outcome = workflow.execute(create(output).build(), session -> {
            session.execute(ComposeParagraphs.version1(flow, limits));
            return session.query(PageCount.INSTANCE);
        });
        assertEquals(CAPABILITY, outcome.getCapabilityId());
        assertEquals(profile, outcome.getExecutionProfile());
        assertEquals(1, outcome.getPublicationReceipts().size());
        assertEquals(PublicationStatus.COMMITTED, outcome.getPublicationReceipts().get(0).getStatus());
        assertTrue(outcome.getResult() > 0);
        return output;
    }
    private void expectFailure(ParagraphFlow flow, CompositionLimits limits, DocumentFailureCode code) throws Exception {
        Path output = path(); Files.write(output, SENTINEL);
        try {
            new DocumentWorkflow().execute(create(output).build(), session -> {
                session.execute(ComposeParagraphs.version1(flow, limits)); return null;
            });
            fail("Expected " + code);
        } catch (DocumentFailure failure) {
            assertEquals(code, failure.getCode());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(0).getStatus());
        }
        assertArrayEquals(SENTINEL, Files.readAllBytes(output));
    }
    private List<PageText> read(Path path) throws Exception {
        return new DocumentWorkflow().execute(open(path).build(), ParagraphCompositionWorkflowTest::extract).getResult();
    }
    private static List<PageText> extract(DocumentSession session) throws DocumentFailure {
        return session.query(ExtractTextAndStructure.version1(ExtractionLimits.builder()
                .maximumPages(16).maximumPageTreeNodes(64).maximumContentStreams(512)
                .maximumContentStreamDepth(8).maximumDecodedBytes(1 << 20).maximumTextItems(10000)
                .maximumUnicodeCodePoints(10000).maximumToUnicodeMappings(64).maximumFontDataEntries(512)
                .maximumMarkedContentSequences(8).maximumMarkedContentDepth(4).maximumStructureElements(8)
                .maximumStructureItems(8).maximumStructureDepth(4).maximumRoleMappings(4).build())).getPages();
    }
    private WorkflowRequest.Builder create(Path output) {
        return WorkflowRequest.builder().target("result", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE).executionProfile(profile);
    }
    private WorkflowRequest.Builder open(Path source) {
        return WorkflowRequest.builder().source("primary", DocumentSource.path(source)).primarySource("primary")
                .saveMode(SaveMode.REWRITE).executionProfile(profile);
    }
    private Path path() throws Exception { return temporary.newFile().toPath(); }
    private static PageMargins margins(double value) { return PageMargins.of(value, value, value, value); }
    private static LayoutPage page(double width, double height, double margin) {
        return LayoutPage.version1(width, height, margins(margin));
    }
    private static ParagraphFlow single(String text) throws Exception {
        return ParagraphFlow.version1(selection()).page(page(80, 40, 10))
                .paragraph(Paragraph.version1(10).text(text, 10).build()).build();
    }
    private static FontSelection selection() throws Exception {
        return FontSelection.explicit(FontSource.bytes(font("FolioPrimary")), FontSource.bytes(font("FolioFallback")));
    }
    private static byte[] font(String name) throws Exception {
        try (InputStream input = ParagraphCompositionWorkflowTest.class.getResourceAsStream(
                "/net/zerocloud/pdf/fixtures/" + name + ".ttf.base64")) {
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) { encoded.write(buffer, 0, count); }
            byte[] bytes = Base64.getMimeDecoder().decode(encoded.toByteArray());
            String expected = name.equals("FolioPrimary")
                    ? "e2fbb634c3c0fe78efb449bde4426d10a93aa813596ff5cf3360f02ec97673fb"
                    : "ced760bc126036779fa84ad3da4638733032512457bf599c44d8c455360f75e1";
            StringBuilder hash = new StringBuilder();
            for (byte part : MessageDigest.getInstance("SHA-256").digest(bytes)) {
                hash.append(Character.forDigit((part & 255) >>> 4, 16)).append(Character.forDigit(part & 15, 16));
            }
            assertEquals(expected, hash.toString());
            return bytes;
        }
    }
    private static FontLimits fontLimits(int sources, long bytes, int scalars, long checks, long generated) {
        return FontLimits.builder().maximumFontSources(sources).maximumSourceBytes(bytes)
                .maximumCodePoints(scalars).maximumFallbackChecks(checks).maximumGeneratedContentBytes(generated).build();
    }
    private static CompositionLimits.Builder limits() {
        return CompositionLimits.builder().maximumPages(16).maximumAreas(32).maximumFlowItems(32)
                .maximumInlines(64).maximumLines(512).maximumGeneratedContentBytes(1 << 20)
                .fontLimits(fontLimits(2, 2000, 10000, 20000, 1 << 20))
                .graphicLimits(CanvasResourceLimits.builder().maximumEncodedImageBytes(1024)
                        .maximumDecodedImagePixels(1024).maximumDecodedImageBytes(4096).maximumIccProfileBytes(0)
                        .maximumMaskBytes(1024).maximumGeneratedContentBytes(4096)
                        .maximumResourceDeclarations(16).maximumTransparencyGroupDepth(4).build());
    }
    private static CanvasTransparencyGroup square() {
        return CanvasTransparencyGroup.version1(CanvasRectangle.of(0, 0, 1, 1), CanvasColorSpace.deviceRgb(),
                true, false, CanvasProgram.version2().setFillColor(CanvasColor.rgb(0.2, 0.4, 0.8))
                        .moveTo(0, 0).lineTo(1, 0).lineTo(1, 1).lineTo(0, 1).closePath()
                        .fill(CanvasWindingRule.NONZERO).build());
    }
    private static void position(PageText page, int index, double x, double y, double advance) {
        TextItem item = page.getTextItems().get(index);
        assertEquals(CharacterMapping.Confidence.EXPLICIT, item.getCharacterMapping().getConfidence());
        assertEquals(x, item.getGeometry().getE().doubleValue(), EPSILON);
        assertEquals(y, item.getGeometry().getF().doubleValue(), EPSILON);
        assertEquals(advance, item.getGeometry().getAdvanceX().doubleValue(), EPSILON);
    }
    private static PdfValue resolve(DocumentSession session, PdfValue value) throws DocumentFailure {
        return value instanceof PdfIndirectReference ? session.query(InspectObject.version1(
                ((PdfIndirectReference) value).getReference(), PdfInspectionLimits.of(1024, 1 << 20))) : value;
    }
    private static String content(DocumentSession session, int number) throws DocumentFailure {
        PdfDictionary page = (PdfDictionary) session.query(InspectObject.version1(
                session.query(PageObjectReference.version1(number)), PdfInspectionLimits.of(1024, 1 << 20)));
        PdfValue contents = resolve(session, page.get(PdfName.of("Contents")));
        List<PdfStream> streams = new ArrayList<PdfStream>();
        if (contents instanceof PdfStream) { streams.add((PdfStream) contents); }
        else {
            PdfArray array = (PdfArray) contents;
            for (int index = 0; index < array.size(); index++) { streams.add((PdfStream) resolve(session, array.get(index))); }
        }
        StringBuilder result = new StringBuilder();
        for (PdfStream stream : streams) { result.append(new String(stream.readBytes(), StandardCharsets.US_ASCII)); }
        return result.toString();
    }
    private static final class TrackingStream extends ByteArrayInputStream {
        private boolean closed;
        TrackingStream(byte[] bytes) { super(bytes); }
        @Override public void close() { closed = true; }
    }
}
