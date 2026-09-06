package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertFalse;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ExtractionLimits;
import net.zerocloud.pdf.PageText;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.WorkflowResourcePolicy;
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.HardenedWorkerSettings;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.composition.CanvasResourceLimits;
import net.zerocloud.pdf.composition.CanvasRectangle;
import net.zerocloud.pdf.composition.CanvasColorSpace;
import net.zerocloud.pdf.composition.CanvasImage;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.composition.CanvasProgram;
import net.zerocloud.pdf.composition.CanvasTransparencyGroup;
import net.zerocloud.pdf.composition.CellPadding;
import net.zerocloud.pdf.composition.CompositionLimits;
import net.zerocloud.pdf.composition.FontLimits;
import net.zerocloud.pdf.composition.FontSelection;
import net.zerocloud.pdf.composition.FontSource;
import net.zerocloud.pdf.composition.LargeTableState;
import net.zerocloud.pdf.composition.LayoutPage;
import net.zerocloud.pdf.composition.PageMargins;
import net.zerocloud.pdf.composition.Paragraph;
import net.zerocloud.pdf.composition.ParagraphFlow;
import net.zerocloud.pdf.composition.Table;
import net.zerocloud.pdf.composition.TableBorders;
import net.zerocloud.pdf.composition.TableCell;
import net.zerocloud.pdf.composition.TableLimits;
import net.zerocloud.pdf.composition.TableRow;
import net.zerocloud.pdf.composition.TableWidth;
import net.zerocloud.pdf.composition.command.AppendTableRows;
import net.zerocloud.pdf.composition.command.BeginLargeTable;
import net.zerocloud.pdf.composition.command.CompleteTable;
import net.zerocloud.pdf.composition.command.FlushTable;
import net.zerocloud.pdf.composition.command.RelayoutParagraphs;
import net.zerocloud.pdf.composition.query.InspectLargeTable;
import net.zerocloud.pdf.query.ExtractTextAndStructure;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageObjectReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Incremental table lifecycle observed only through public Workflow Commands and Queries. */
@RunWith(Parameterized.class)
public final class LargeTableWorkflowTest {
    private final WorkflowExecutionProfile profile;
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> profiles() {
        return Arrays.asList(new Object[][] {{WorkflowExecutionProfile.IN_PROCESS},
            {WorkflowExecutionProfile.HARDENED_WORKER}});
    }
    public LargeTableWorkflowTest(WorkflowExecutionProfile profile) { this.profile = profile; }

    @Test
    public void flushReleasesRowsBeforeAllRowsArriveAndCompleteUsesFinalFooterSpace() throws Exception {
        Path target = temporary.newFile().toPath();
        byte[] sentinel = {31,41,59};
        Files.write(target,sentinel);
        Table table = Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto())
                .header(row("A")).footer(row("\u03a9")).skipLastFooter(true).build();
        LayoutPage page = LayoutPage.version1(80,100,PageMargins.of(20,20,8,20));
        ParagraphFlow flow = ParagraphFlow.version4(selection()).page(page).page(page).page(page).table(table).build();
        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .target("result",PublicationTarget.path(target)).saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> {
                    session.execute(BeginLargeTable.version1(flow,limits(),3));
                    session.execute(AppendTableRows.version1(row("B"),row("B"),row("B")));
                    assertEquals(3,session.query(InspectLargeTable.version1()).getRetainedRows());
                    session.execute(FlushTable.version1());
                    LargeTableState state = session.query(InspectLargeTable.version1());
                    assertEquals(LargeTableState.Stage.OPEN,state.getStage());
                    assertEquals(3,state.getAcceptedRows()); assertEquals(1,state.getRetainedRows()); assertEquals(2,state.getFlushedRows());
                    assertEquals(1,session.query(PageCount.INSTANCE).intValue());
                    assertEquals("ABB\u03a9",session.query(ExtractTextAndStructure.version1(extraction())).getPages().get(0).getText());
                    try { assertArrayEquals(sentinel,Files.readAllBytes(target)); }
                    catch (java.io.IOException failure) { throw new AssertionError(failure); }
                    session.execute(AppendTableRows.version1(row("B"),row("B")));
                    assertEquals(3,session.query(InspectLargeTable.version1()).getRetainedRows());
                    session.execute(CompleteTable.version1());
                    state = session.query(InspectLargeTable.version1());
                    assertEquals(LargeTableState.Stage.COMPLETE,state.getStage());
                    assertEquals(5,state.getAcceptedRows()); assertEquals(0,state.getRetainedRows()); assertEquals(5,state.getFlushedRows());
                    return null;
                });
        assertEquals(PublicationStatus.COMMITTED,outcome.getPublicationReceipts().get(0).getStatus());
        List<PageText> pages = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .source("primary",DocumentSource.path(target)).primarySource("primary").saveMode(SaveMode.REWRITE)
                .executionProfile(profile).build(), session -> session.query(ExtractTextAndStructure.version1(extraction())).getPages()).getResult();
        assertEquals(2,pages.size()); assertEquals("ABB\u03a9",pages.get(0).getText()); assertEquals("ABBB",pages.get(1).getText());
        position(pages,0,0,70); position(pages,0,1,52); position(pages,0,2,34); position(pages,0,3,15.8);
        position(pages,1,0,70); position(pages,1,1,52); position(pages,1,2,34); position(pages,1,3,16);
    }

    @Test
    public void cumulativeDeclarationLimitsSurviveFlushAndRejectTheFirstExtraBatchAtomically() throws Exception {
        Table table = Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto()).build();
        LayoutPage page = LayoutPage.version1(80,76,PageMargins.of(20,20,20,20));
        ParagraphFlow flow = ParagraphFlow.version4(selection()).page(page).page(page).page(page).table(table).build();
        for (int counter = -1; counter < 5; counter++) {
            int[] bounds = {5,5,5,5,5};
            if (counter >= 0) { bounds[counter] = 4; }
            CompositionLimits budget = counterLimits(bounds[0],bounds[1],bounds[2],bounds[3],bounds[4]);
            final int dimension = counter;
            Path target = temporary.newFile().toPath();
            new DocumentWorkflow().execute(WorkflowRequest.builder().target("result",PublicationTarget.path(target))
                    .saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> {
                        session.execute(BeginLargeTable.version1(flow,budget,3));
                        session.execute(AppendTableRows.version1(row("A"),row("A"),row("A")));
                        session.execute(FlushTable.version1());
                        assertEquals(2,session.query(InspectLargeTable.version1()).getFlushedRows());
                        if (dimension < 0) {
                            session.execute(AppendTableRows.version1(row("B"),row("B")));
                        } else {
                            try {
                                session.execute(AppendTableRows.version1(row("B"),row("B")));
                                fail("Cumulative dimension "+dimension+" reset after flush");
                            } catch (DocumentFailure expected) {
                                assertEquals(dimension == 4 ? DocumentFailureCode.FONT_LIMIT_EXCEEDED
                                        : DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED,expected.getCode());
                                assertEquals("composition.layout.tables",expected.getCapabilityId());
                            }
                            LargeTableState preserved = session.query(InspectLargeTable.version1());
                            assertEquals(3,preserved.getAcceptedRows()); assertEquals(1,preserved.getRetainedRows());
                            session.execute(AppendTableRows.version1(row("B")));
                        }
                        session.execute(CompleteTable.version1());
                        return null;
                    });
            List<PageText> output = new DocumentWorkflow().execute(WorkflowRequest.builder()
                    .source("primary",DocumentSource.path(target)).primarySource("primary").saveMode(SaveMode.REWRITE)
                    .executionProfile(profile).build(), session -> session.query(ExtractTextAndStructure.version1(extraction())).getPages()).getResult();
            assertEquals(dimension < 0 ? 3 : 2,output.size());
            assertEquals("AA",output.get(0).getText()); assertEquals("AB",output.get(1).getText());
            if (dimension < 0) { assertEquals("B",output.get(2).getText()); }
        }
    }

    @Test
    public void incompleteSpanWaitsForLaterRowsAndFlushReleasesTheCompletedSpanGroup() throws Exception {
        Table table = Table.version2(Table.Layout.FIXED,TableWidth.points(80),TableWidth.points(40),TableWidth.auto()).build();
        LayoutPage page = LayoutPage.version1(120,58,PageMargins.of(20,20,20,20));
        ParagraphFlow flow = ParagraphFlow.version4(selection()).page(page).page(page).page(page).table(table).build();
        CompositionLimits budget = baseLimits(7,8).tableLimits(TableLimits.builder().maximumTables(1).maximumRows(3)
                .maximumColumns(2).maximumCells(5).maximumGridSlots(6).maximumLayoutWork(100000).build()).build();
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(WorkflowRequest.builder().target("result",PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> {
                    session.execute(BeginLargeTable.version1(flow,budget,3));
                    session.execute(AppendTableRows.version1(TableRow.version1(cell("A\nB",2),cell("B",1))));
                    session.execute(FlushTable.version1());
                    assertEquals(0,session.query(PageCount.INSTANCE).intValue());
                    assertEquals(1,session.query(InspectLargeTable.version1()).getRetainedRows());
                    session.execute(AppendTableRows.version1(TableRow.version1(cell("\u03a9",1)),
                            TableRow.version1(cell("A",1),cell("B",1))));
                    session.execute(FlushTable.version1());
                    assertEquals(2,session.query(PageCount.INSTANCE).intValue());
                    LargeTableState state = session.query(InspectLargeTable.version1());
                    assertEquals(3,state.getAcceptedRows()); assertEquals(1,state.getRetainedRows()); assertEquals(2,state.getFlushedRows());
                    session.execute(CompleteTable.version1());
                    return null;
                });
        List<PageText> output = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .source("primary",DocumentSource.path(target)).primarySource("primary").saveMode(SaveMode.REWRITE)
                .executionProfile(profile).build(), session -> session.query(ExtractTextAndStructure.version1(extraction())).getPages()).getResult();
        assertEquals(3,output.size());
        assertEquals("AB",output.get(0).getText()); assertEquals("B\u03a9",output.get(1).getText());
        assertEquals("AB",output.get(2).getText());
        for (PageText outputPage : output) {
            assertEquals(23,outputPage.getTextItems().get(0).getGeometry().getE().doubleValue(),0.0001);
            assertEquals(63,outputPage.getTextItems().get(1).getGeometry().getE().doubleValue(),0.0001);
            assertEquals(28,outputPage.getTextItems().get(0).getGeometry().getF().doubleValue(),0.0001);
        }
    }

    @Test
    public void openTableRejectsInterleavedMutationAndPublicationAndFlushedTableRejectsRelayout() throws Exception {
        Table table = Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto()).build();
        LayoutPage page = LayoutPage.version1(80,76,PageMargins.of(20,20,20,20));
        ParagraphFlow flow = ParagraphFlow.version4(selection()).page(page).page(page).table(table).build();
        for (boolean complete : new boolean[] {false,true}) {
            Path target = temporary.newFile().toPath();
            byte[] sentinel = {12,34,56}; Files.write(target,sentinel);
            try {
                WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(WorkflowRequest.builder()
                        .target("result",PublicationTarget.path(target)).saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> {
                            session.execute(BeginLargeTable.version1(flow,limits(),3));
                            session.execute(AppendTableRows.version1(row("A"),row("B"),row("A")));
                            session.execute(FlushTable.version1());
                            try {
                                session.execute(AddBlankPage.INSTANCE);
                                fail("An unrelated mutation must not displace the open table's pages");
                            } catch (DocumentFailure expected) {
                                assertEquals(DocumentFailureCode.COMPOSITION_INVALID,expected.getCode());
                                assertEquals("composition.layout.tables",expected.getCapabilityId());
                            }
                            try {
                                session.execute(RelayoutParagraphs.version1(page));
                                fail("Flushed table declarations cannot be reconstructed");
                            } catch (DocumentFailure expected) {
                                assertEquals(DocumentFailureCode.COMPOSITION_RELAYOUT_UNSAFE,expected.getCode());
                                assertEquals("composition.layout.tables",expected.getCapabilityId());
                            }
                            assertEquals(1,session.query(PageCount.INSTANCE).intValue());
                            assertEquals(1,session.query(InspectLargeTable.version1()).getRetainedRows());
                            if (complete) { session.execute(CompleteTable.version1()); }
                            return null;
                        });
                if (!complete) { fail("An open table must not publish a truncated document"); }
                assertEquals(PublicationStatus.COMMITTED,outcome.getPublicationReceipts().get(0).getStatus());
            } catch (DocumentFailure expected) {
                if (complete) { throw expected; }
                assertEquals(DocumentFailureCode.COMPOSITION_INVALID,expected.getCode());
                assertEquals("composition.layout.tables",expected.getCapabilityId());
                assertEquals(PublicationStatus.NOT_ATTEMPTED,expected.getPublicationReceipts().get(0).getStatus());
                assertArrayEquals(sentinel,Files.readAllBytes(target));
            }
        }
    }

    @Test
    public void fallbackWorkIsCumulativeIncludingRepeatedHeadersAndRetainedRows() throws Exception {
        Table table = Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto())
                .header(row("A")).footer(row("\u03a9")).skipLastFooter(true).build();
        LayoutPage page = LayoutPage.version1(80,100,PageMargins.of(20,20,8,20));
        ParagraphFlow flow = ParagraphFlow.version4(selection()).page(page).page(page).page(page).table(table).build();
        for (int checks : new int[] {12,11}) {
            CompositionLimits budget = baseLimits(7,7).tableLimits(limits().getTableLimits())
                    .fontLimits(fontLimits(7,checks)).build();
            Path target = temporary.newFile().toPath();
            byte[] sentinel = {8,9,10}; Files.write(target,sentinel);
            try {
                new DocumentWorkflow().execute(WorkflowRequest.builder().target("result",PublicationTarget.path(target))
                        .saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> {
                            session.execute(BeginLargeTable.version1(flow,budget,3));
                            session.execute(AppendTableRows.version1(row("B"),row("B"),row("B")));
                            session.execute(FlushTable.version1());
                            session.execute(AppendTableRows.version1(row("B"),row("B")));
                            try { session.execute(CompleteTable.version1()); }
                            catch (DocumentFailure expected) {
                                assertEquals(1,session.query(PageCount.INSTANCE).intValue());
                                LargeTableState state = session.query(InspectLargeTable.version1());
                                assertEquals(5,state.getAcceptedRows()); assertEquals(3,state.getRetainedRows());
                                throw expected;
                            }
                            return null;
                        });
                if (checks == 11) { fail("Fallback visits reset between flush and complete"); }
            } catch (DocumentFailure expected) {
                if (checks == 12) { throw expected; }
                assertEquals(DocumentFailureCode.FONT_LIMIT_EXCEEDED,expected.getCode());
                assertEquals("composition.layout.tables",expected.getCapabilityId());
                assertEquals(PublicationStatus.NOT_ATTEMPTED,expected.getPublicationReceipts().get(0).getStatus());
                assertArrayEquals(sentinel,Files.readAllBytes(target));
            }
        }
    }

    @Test
    public void generatedRowsRemainBoundedAndContinueInLaterAreasOfTheSamePhysicalPage() throws Exception {
        Table table = Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto()).build();
        LayoutPage page = LayoutPage.version1(120,76,PageMargins.of(20,20,20,20),
                CanvasRectangle.of(0,0,40,36),CanvasRectangle.of(40,0,80,36));
        ParagraphFlow.Builder builder = ParagraphFlow.version4(selection());
        for (int i = 0; i < 32; i++) { builder.page(page); }
        ParagraphFlow flow = builder.table(table).build();
        CompositionLimits budget = baseLimits(125,125).maximumPages(32).maximumAreas(64).maximumLines(125)
                .tableLimits(TableLimits.builder().maximumTables(1).maximumRows(125).maximumCells(125).maximumColumns(1)
                        .maximumGridSlots(125).maximumLayoutWork(1000000).build()).build();
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(WorkflowRequest.builder().target("result",PublicationTarget.path(target))
                .resourcePolicy(policy(32)).saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> {
                    session.execute(BeginLargeTable.version1(flow,budget,3));
                    session.execute(AppendTableRows.version1(row("A"),row("B"),row("\u03a9")));
                    try {
                        session.execute(AppendTableRows.version1(row("A")));
                        fail("The first row beyond the retained bound must fail before flush");
                    } catch (DocumentFailure expected) {
                        assertEquals(DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED,expected.getCode());
                    }
                    session.execute(FlushTable.version1());
                    assertEquals(1,session.query(InspectLargeTable.version1()).getRetainedRows());
                    for (int next = 3; next < 125; next += 2) {
                        session.execute(AppendTableRows.version1(row("AB\u03a9".substring(next % 3,next % 3 + 1)),
                                row("AB\u03a9".substring((next + 1) % 3,(next + 1) % 3 + 1))));
                        LargeTableState before = session.query(InspectLargeTable.version1());
                        assertEquals(next + 2,before.getAcceptedRows()); assertEquals(3,before.getRetainedRows());
                        session.execute(FlushTable.version1());
                        LargeTableState after = session.query(InspectLargeTable.version1());
                        assertEquals(1,after.getRetainedRows()); assertEquals(next + 1,after.getFlushedRows());
                        assertEquals((next + 4) / 4,session.query(PageCount.INSTANCE).intValue());
                    }
                    session.execute(CompleteTable.version1());
                    LargeTableState state = session.query(InspectLargeTable.version1());
                    assertEquals(125,state.getFlushedRows()); assertEquals(0,state.getRetainedRows());
                    return null;
                });
        List<PageText> output = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .source("primary",DocumentSource.path(target)).primarySource("primary").saveMode(SaveMode.REWRITE)
                .executionProfile(profile).build(), session -> session.query(ExtractTextAndStructure.version1(extraction())).getPages()).getResult();
        assertEquals(32,output.size());
        String[] pageTexts = {"AB\u03a9A","B\u03a9AB","\u03a9AB\u03a9"};
        for (int p = 0; p < 31; p++) {
            assertEquals(pageTexts[p % 3],output.get(p).getText());
            for (int item = 0; item < 4; item++) {
                assertEquals(item < 2 ? 23 : 63,output.get(p).getTextItems().get(item).getGeometry().getE().doubleValue(),0.0001);
                double y = item % 2 == 0 ? 46 : 28;
                if (pageTexts[p % 3].charAt(item) == '\u03a9') { y -= 0.2; }
                assertEquals(y,output.get(p).getTextItems().get(item).getGeometry().getF().doubleValue(),0.0001);
            }
        }
        assertEquals("B",output.get(31).getText());
    }

    @Test
    public void minimumOnlyRowFragmentRemainsRetainedUntilItsCompleteGroupCanBeReleased() throws Exception {
        Table table = Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto()).build();
        LayoutPage page = LayoutPage.version1(80,76,PageMargins.of(20,20,20,20));
        ParagraphFlow flow = ParagraphFlow.version4(selection()).page(page).page(page).page(page).table(table).build();
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(WorkflowRequest.builder().target("result",PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> {
                    session.execute(BeginLargeTable.version1(flow,limits(),2));
                    session.execute(AppendTableRows.version1(row("A"),TableRow.version1(36,
                            TableCell.version1().padding(CellPadding.of(2,2,2,2)).borders(TableBorders.of(1,1,1,1)).build())));
                    session.execute(FlushTable.version1());
                    assertEquals(0,session.query(PageCount.INSTANCE).intValue());
                    assertEquals(2,session.query(InspectLargeTable.version1()).getRetainedRows());
                    session.execute(CompleteTable.version1());
                    return null;
                });
        List<PageText> output = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .source("primary",DocumentSource.path(target)).primarySource("primary").saveMode(SaveMode.REWRITE)
                .executionProfile(profile).build(), session -> session.query(ExtractTextAndStructure.version1(extraction())).getPages()).getResult();
        assertEquals(2,output.size()); assertEquals("A",output.get(0).getText()); assertEquals("",output.get(1).getText());
    }

    @Test
    public void layoutAndOutputBudgetsAccumulateAcrossFlushAtExactAndFirstExcessBounds() throws Exception {
        Table table = Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto()).build();
        LayoutPage page = LayoutPage.version1(80,58,PageMargins.of(20,20,20,20));
        ParagraphFlow flow = ParagraphFlow.version4(selection()).page(page).page(page).table(table).build();
        Path observed = temporary.newFile().toPath();
        streamTwoRows(flow,limits(),observed);
        long[] bytes = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .source("primary",DocumentSource.path(observed)).primarySource("primary").saveMode(SaveMode.REWRITE)
                .executionProfile(profile).build(), session -> {
                    long[] totals = {0,0};
                    for (int p = 1; p <= 2; p++) {
                        PdfDictionary dictionary = (PdfDictionary) session.query(InspectObject.version1(
                                session.query(PageObjectReference.version1(p)),PdfInspectionLimits.of(1024,1 << 20)));
                        countOperatorBytes(session,dictionary.get(PdfName.of("Contents")),totals);
                    }
                    return totals;
                }).getResult();
        for (int dimension = -1; dimension < 5; dimension++) {
            CompositionLimits budget = baseLimits(2,2).maximumLayoutAttempts(dimension == 0 ? 2 : 3)
                    .maximumLines(dimension == 2 ? 1 : 2).maximumGeneratedContentBytes(dimension == 3 ? bytes[0] - 1 : bytes[0])
                    .tableLimits(TableLimits.builder().maximumTables(1).maximumRows(2).maximumCells(2).maximumColumns(1)
                            .maximumGridSlots(2).maximumLayoutWork(dimension == 1 ? 52 : 53).build())
                    .fontLimits(FontLimits.builder().maximumFontSources(2).maximumSourceBytes(2000).maximumCodePoints(2)
                            .maximumFallbackChecks(3).maximumGeneratedContentBytes(dimension == 4 ? bytes[1] - 1 : bytes[1]).build()).build();
            Path target = temporary.newFile().toPath(); byte[] sentinel = {1,3,5}; Files.write(target,sentinel);
            try {
                streamTwoRows(flow,budget,target);
                if (dimension >= 0) { fail("Cumulative budget dimension "+dimension+" reset after flush"); }
            } catch (DocumentFailure expected) {
                if (dimension < 0) { throw expected; }
                assertEquals(dimension == 4 ? DocumentFailureCode.FONT_LIMIT_EXCEEDED
                        : DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED,expected.getCode());
                assertEquals("composition.layout.tables",expected.getCapabilityId());
                assertEquals(PublicationStatus.NOT_ATTEMPTED,expected.getPublicationReceipts().get(0).getStatus());
                assertArrayEquals(sentinel,Files.readAllBytes(target));
            }
        }
    }

    @Test
    public void beginFreezesPathAndBorrowedStreamFontsForLaterFlushes() throws Exception {
        Path primary = temporary.newFile().toPath(); Files.write(primary,font("FolioPrimary"));
        TrackingStream fallback = new TrackingStream(font("FolioFallback"));
        FontSelection fonts = FontSelection.explicit(FontSource.path(primary),FontSource.stream(fallback));
        LayoutPage page = LayoutPage.version1(80,58,PageMargins.of(20,20,20,20));
        ParagraphFlow flow = ParagraphFlow.version4(fonts).page(page).page(page)
                .table(Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto()).build()).build();
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(WorkflowRequest.builder().target("result",PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> {
                    session.execute(BeginLargeTable.version1(flow,limits(),2));
                    try { Files.write(primary,new byte[] {1,2,3}); }
                    catch (java.io.IOException failure) { throw new AssertionError(failure); }
                    assertEquals(0,fallback.available());
                    session.execute(AppendTableRows.version1(row("A"),row("\u03a9")));
                    session.execute(FlushTable.version1()); session.execute(CompleteTable.version1());
                    return null;
                });
        assertFalse(fallback.closed);
        List<PageText> output = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .source("primary",DocumentSource.path(target)).primarySource("primary").saveMode(SaveMode.REWRITE)
                .executionProfile(profile).build(), session -> session.query(ExtractTextAndStructure.version1(extraction())).getPages()).getResult();
        assertEquals(2,output.size()); assertEquals("A",output.get(0).getText()); assertEquals("\u03a9",output.get(1).getText());
    }

    @Test
    public void fixedLayoutAndOpenSpanAdmissionRejectInvalidDeclarationsWithoutConsumingTheValidStream() throws Exception {
        LayoutPage page = LayoutPage.version1(80,100,PageMargins.of(20,20,20,20));
        FontSelection fonts = selection();
        Table prototype = Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto()).build();
        Table[] invalid = {
            Table.version2(Table.Layout.AUTO,TableWidth.points(40),TableWidth.auto()).build(),
            Table.version2(Table.Layout.FIXED,TableWidth.auto(),TableWidth.auto()).build(),
            Table.version1(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto()).build(),
            Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto()).row(row("A")).build()
        };
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(WorkflowRequest.builder().target("result",PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> {
                    for (Table table : invalid) {
                        try {
                            session.execute(BeginLargeTable.version1(ParagraphFlow.version4(fonts).page(page).table(table).build(),limits(),3));
                            fail("Invalid large table admitted");
                        } catch (DocumentFailure expected) {
                            assertEquals(DocumentFailureCode.COMPOSITION_INVALID,expected.getCode());
                            assertEquals("composition.layout.tables",expected.getCapabilityId());
                        }
                        assertEquals(LargeTableState.Stage.NONE,session.query(InspectLargeTable.version1()).getStage());
                    }
                    session.execute(BeginLargeTable.version1(ParagraphFlow.version4(fonts).page(page).table(prototype).build(),limits(),3));
                    try {
                        session.execute(AppendTableRows.version1(TableRow.version1(cell("A",4))));
                        fail("An incomplete span must not exceed the retained-row bound");
                    } catch (DocumentFailure expected) {
                        assertEquals(DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED,expected.getCode());
                    }
                    assertEquals(0,session.query(InspectLargeTable.version1()).getAcceptedRows());
                    session.execute(AppendTableRows.version1(TableRow.version1(cell("A",3))));
                    try { session.execute(CompleteTable.version1()); fail("Incomplete grid completed"); }
                    catch (DocumentFailure expected) { assertEquals(DocumentFailureCode.TABLE_INVALID_SPAN,expected.getCode()); }
                    assertEquals(1,session.query(InspectLargeTable.version1()).getRetainedRows());
                    session.execute(AppendTableRows.version1(TableRow.version1(),TableRow.version1()));
                    session.execute(CompleteTable.version1());
                    assertEquals(3,session.query(InspectLargeTable.version1()).getFlushedRows());
                    return null;
                });
    }

    @Test
    public void largeTableFlushParticipatesInIncrementalPublicationWithoutChangingTheSourcePrefix() throws Exception {
        Path source = temporary.newFile().toPath();
        new DocumentWorkflow().execute(WorkflowRequest.create(source,SaveMode.REWRITE), session -> {
            session.execute(AddBlankPage.INSTANCE); return null;
        });
        byte[] original = Files.readAllBytes(source);
        Path target = temporary.newFile().toPath();
        LayoutPage page = LayoutPage.version1(80,58,PageMargins.of(20,20,20,20));
        ParagraphFlow flow = ParagraphFlow.version4(selection()).page(page).page(page)
                .table(Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto()).build()).build();
        new DocumentWorkflow().execute(WorkflowRequest.builder().source("primary",DocumentSource.path(source)).primarySource("primary")
                .target("result",PublicationTarget.path(target)).saveMode(SaveMode.INCREMENTAL).executionProfile(profile).build(), session -> {
                    session.execute(BeginLargeTable.version1(flow,limits(),2));
                    session.execute(AppendTableRows.version1(row("A"),row("B")));
                    session.execute(FlushTable.version1()); session.execute(CompleteTable.version1());
                    return null;
                });
        assertArrayEquals(original,Arrays.copyOf(Files.readAllBytes(target),original.length));
        List<PageText> output = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .source("primary",DocumentSource.path(target)).primarySource("primary").saveMode(SaveMode.REWRITE)
                .executionProfile(profile).build(), session -> session.query(ExtractTextAndStructure.version1(extraction())).getPages()).getResult();
        assertEquals(3,output.size()); assertEquals("",output.get(0).getText());
        assertEquals("A",output.get(1).getText()); assertEquals("B",output.get(2).getText());
    }

    @Test
    public void chunkedWorkerRowsCannotResetTheCumulativeScalarBudget() throws Exception {
        char[] letters = new char[4096]; Arrays.fill(letters,'A');
        String text = new String(letters);
        TableRow large = TableRow.version1(TableCell.version1().paragraph(Paragraph.version1(12).text(text,0.005).build())
                .padding(CellPadding.of(2,2,2,2)).borders(TableBorders.of(1,1,1,1)).build());
        LayoutPage page = LayoutPage.version1(80,58,PageMargins.of(20,20,20,20));
        ParagraphFlow flow = ParagraphFlow.version4(selection()).page(page).page(page)
                .table(Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto()).build()).build();
        CompositionLimits budget = baseLimits(3,8192).fontLimits(fontLimits(8192,12288))
                .tableLimits(limits().getTableLimits()).build();
        WorkflowEnvironment environment = WorkflowEnvironment.builder().hardenedWorkerSettings(HardenedWorkerSettings.builder()
                .maximumMessageBytes(2048).maximumHeapBytes(HardenedWorkerSettings.DEFAULT_MAXIMUM_HEAP_BYTES).build()).build();
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow(environment).execute(WorkflowRequest.builder().target("result",PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> {
                    session.execute(BeginLargeTable.version1(flow,budget,2));
                    session.execute(AppendTableRows.version1(large,large));
                    session.execute(FlushTable.version1());
                    try {
                        session.execute(AppendTableRows.version1(large));
                        fail("Transport chunks must not reset the logical table budget");
                    } catch (DocumentFailure expected) {
                        assertEquals(DocumentFailureCode.FONT_LIMIT_EXCEEDED,expected.getCode());
                        assertEquals("composition.layout.tables",expected.getCapabilityId());
                    }
                    LargeTableState state = session.query(InspectLargeTable.version1());
                    assertEquals(2,state.getAcceptedRows()); assertEquals(1,state.getRetainedRows());
                    session.execute(CompleteTable.version1());
                    return null;
                });
        List<PageText> output = new DocumentWorkflow(environment).execute(WorkflowRequest.builder()
                .source("primary",DocumentSource.path(target)).primarySource("primary").saveMode(SaveMode.REWRITE)
                .executionProfile(profile).build(), session -> session.query(ExtractTextAndStructure.version1(extraction())).getPages()).getResult();
        assertEquals(2,output.size()); assertEquals(text,output.get(0).getText()); assertEquals(text,output.get(1).getText());
    }

    @Test
    public void fontlessRowsFlushUnderZeroFontAndLineBudgets() throws Exception {
        LayoutPage page = LayoutPage.version1(80,58,PageMargins.of(20,20,20,20));
        ParagraphFlow flow = ParagraphFlow.version4(FontSelection.referenceFontSet()).page(page).page(page)
                .table(Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto()).build()).build();
        CompositionLimits budget = baseLimits(0,0).maximumLines(0).tableLimits(limits().getTableLimits())
                .fontLimits(FontLimits.builder().maximumFontSources(0).maximumSourceBytes(0).maximumCodePoints(0)
                        .maximumFallbackChecks(0).maximumGeneratedContentBytes(0).build()).build();
        TableRow row = TableRow.version1(18,TableCell.version1().borders(TableBorders.of(1,1,1,1)).build());
        Path target = temporary.newFile().toPath();
        new DocumentWorkflow().execute(WorkflowRequest.builder().target("result",PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> {
                    session.execute(BeginLargeTable.version1(flow,budget,2));
                    session.execute(AppendTableRows.version1(row,row));
                    session.execute(FlushTable.version1());
                    assertEquals(1,session.query(PageCount.INSTANCE).intValue());
                    assertEquals(1,session.query(InspectLargeTable.version1()).getRetainedRows());
                    session.execute(CompleteTable.version1());
                    return null;
                });
        List<PageText> output = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .source("primary",DocumentSource.path(target)).primarySource("primary").saveMode(SaveMode.REWRITE)
                .executionProfile(profile).build(), session -> session.query(ExtractTextAndStructure.version1(extraction())).getPages()).getResult();
        assertEquals(2,output.size()); assertEquals("",output.get(0).getText()); assertEquals("",output.get(1).getText());
    }

    @Test
    public void retainedGraphicPayloadsAreChargedBeforeAnotherAppend() throws Exception {
        LayoutPage page = LayoutPage.version1(100,100,PageMargins.of(10,10,10,10));
        ParagraphFlow flow = ParagraphFlow.version4(FontSelection.referenceFontSet()).page(page)
                .table(Table.version2(Table.Layout.FIXED,TableWidth.points(80),TableWidth.auto()).build()).build();
        CompositionLimits bounds = graphicLimits();
        WorkflowEnvironment environment = WorkflowEnvironment.builder().hardenedWorkerSettings(HardenedWorkerSettings.builder()
                .maximumMessageBytes(1 << 20).maximumHeapBytes(128L << 20).build()).build();
        Path oneRow = temporary.newFile().toPath();
        WorkflowOutcome<Void> positive = new DocumentWorkflow(environment).execute(WorkflowRequest.builder()
                .target("result",PublicationTarget.path(oneRow)).saveMode(SaveMode.REWRITE).executionProfile(profile)
                .resourcePolicy(graphicPolicy(8L << 20)).build(),session -> {
                    session.execute(BeginLargeTable.version1(flow,bounds,64));
                    session.execute(AppendTableRows.version1(graphicRow()));
                    assertEquals(1,session.query(InspectLargeTable.version1()).getRetainedRows());
                    session.execute(CompleteTable.version1());
                    return null;
                });
        assertEquals(PublicationStatus.COMMITTED,positive.getPublicationReceipts().get(0).getStatus());
        assertEquals(Integer.valueOf(1),new DocumentWorkflow(environment).execute(WorkflowRequest.builder()
                .source("primary",DocumentSource.path(oneRow)).primarySource("primary").saveMode(SaveMode.REWRITE)
                .executionProfile(profile).build(),session -> session.query(PageCount.INSTANCE)).getResult());
        // Worker request/response overlap can change the aggregate peak; its retained-payload
        // bound is asserted below. The in-process public peak provides a deterministic byte boundary.
        if (profile == WorkflowExecutionProfile.IN_PROCESS) {
            long peak = positive.getResourceUsage().getPeakOwnedMemoryBytes();
            assertTrue(peak >= 512L * 512);
            for (int reduction : new int[] {0,1}) {
                byte[] previous = Files.readAllBytes(oneRow);
                try {
                    new DocumentWorkflow(environment).execute(WorkflowRequest.builder()
                            .target("result",PublicationTarget.path(oneRow)).saveMode(SaveMode.REWRITE).executionProfile(profile)
                            .resourcePolicy(graphicPolicy(peak - reduction)).build(),session -> {
                                session.execute(BeginLargeTable.version1(flow,bounds,64));
                                session.execute(AppendTableRows.version1(graphicRow()));
                                assertEquals(1,session.query(InspectLargeTable.version1()).getRetainedRows());
                                session.execute(CompleteTable.version1());
                                return null;
                            });
                    if (reduction == 1) { fail("One byte below the observed owned-memory peak must fail"); }
                } catch (DocumentFailure failure) {
                    if (reduction == 0) { throw failure; }
                    assertEquals(DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,failure.getCode());
                    assertEquals(PublicationStatus.NOT_ATTEMPTED,failure.getPublicationReceipts().get(0).getStatus());
                    assertArrayEquals(previous,Files.readAllBytes(oneRow));
                }
            }
        }
        Path target = temporary.newFile().toPath();
        byte[] sentinel = {31,41,59}; Files.write(target,sentinel);
        try {
            new DocumentWorkflow(environment).execute(WorkflowRequest.builder().target("result",PublicationTarget.path(target))
                    .saveMode(SaveMode.REWRITE).executionProfile(profile).resourcePolicy(graphicPolicy(8L << 20)).build(),session -> {
                        session.execute(BeginLargeTable.version1(flow,bounds,64));
                        for (int row = 0; row < 64; row++) {
                            // Fresh immutable images also exercise independent payload retention in-process.
                            session.execute(AppendTableRows.version1(graphicRow()));
                            assertEquals(row + 1,session.query(InspectLargeTable.version1()).getRetainedRows());
                        }
                        fail("16 MiB of retained image samples cannot fit an 8 MiB owned-memory budget before flush");
                        return null;
                    });
            fail("Expected retained graphic memory rejection during append");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,failure.getCode());
            assertEquals(PublicationStatus.NOT_ATTEMPTED,failure.getPublicationReceipts().get(0).getStatus());
        }
        assertArrayEquals(sentinel,Files.readAllBytes(target));
    }

    @Test
    public void beginChargesGraphicsInBothRepeatedSections() throws Exception {
        WorkflowEnvironment environment = WorkflowEnvironment.builder().hardenedWorkerSettings(HardenedWorkerSettings.builder()
                .maximumMessageBytes(32 << 20).maximumHeapBytes(128L << 20).build()).build();
        for (boolean header : new boolean[] {true,false}) {
            for (int count : new int[] {1,64}) {
                Table.Builder table = Table.version2(Table.Layout.FIXED,TableWidth.points(80),TableWidth.auto());
                for (int row = 0; row < count; row++) {
                    if (header) { table.header(graphicRow()); } else { table.footer(graphicRow()); }
                }
                ParagraphFlow flow = ParagraphFlow.version4(FontSelection.referenceFontSet())
                        .page(LayoutPage.version1(100,100,PageMargins.of(10,10,10,10))).table(table.build()).build();
                Path target = temporary.newFile().toPath(); byte[] sentinel = {26,53,58}; Files.write(target,sentinel);
                try {
                    WorkflowOutcome<Void> outcome = new DocumentWorkflow(environment).execute(WorkflowRequest.builder()
                            .target("result",PublicationTarget.path(target)).saveMode(SaveMode.REWRITE).executionProfile(profile)
                            .resourcePolicy(graphicPolicy(8L << 20)).build(),session -> {
                                session.execute(BeginLargeTable.version1(flow,graphicLimits(),64));
                                if (count == 64) { fail("Begin must charge 16 MiB of repeated-section graphics before retaining them"); }
                                session.execute(AppendTableRows.version1(graphicRow()));
                                session.execute(CompleteTable.version1());
                                return null;
                            });
                    assertEquals(PublicationStatus.COMMITTED,outcome.getPublicationReceipts().get(0).getStatus());
                    assertEquals(Integer.valueOf(1),new DocumentWorkflow(environment).execute(WorkflowRequest.builder()
                            .source("primary",DocumentSource.path(target)).primarySource("primary").saveMode(SaveMode.REWRITE)
                            .executionProfile(profile).build(),session -> session.query(PageCount.INSTANCE)).getResult());
                } catch (DocumentFailure failure) {
                    if (count == 1) { throw failure; }
                    assertEquals(DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,failure.getCode());
                    assertEquals(PublicationStatus.NOT_ATTEMPTED,failure.getPublicationReceipts().get(0).getStatus());
                    assertArrayEquals(sentinel,Files.readAllBytes(target));
                }
            }
        }
    }

    @Test
    public void incrementalGraphicsReleaseAllowsAStreamLargerThanTheWorkerHeap() throws Exception {
        ParagraphFlow.Builder pages = ParagraphFlow.version4(FontSelection.referenceFontSet());
        for (int page = 0; page < 64; page++) {
            pages.page(LayoutPage.version1(100,22,PageMargins.of(10,10,10,10)));
        }
        ParagraphFlow flow = pages.table(Table.version2(Table.Layout.FIXED,TableWidth.points(80),TableWidth.auto()).build()).build();
        WorkflowEnvironment environment = WorkflowEnvironment.builder().hardenedWorkerSettings(HardenedWorkerSettings.builder()
                .maximumMessageBytes(1 << 20).maximumHeapBytes(32L << 20).build()).build();
        Path target = temporary.newFile().toPath();
        WorkflowOutcome<Void> outcome = new DocumentWorkflow(environment).execute(WorkflowRequest.builder()
                .target("result",PublicationTarget.path(target)).saveMode(SaveMode.REWRITE).executionProfile(profile)
                .resourcePolicy(graphicPolicy(8L << 20,64)).build(),session -> {
                    session.execute(BeginLargeTable.version1(flow,graphicLimits(128,64),3));
                    for (int row = 0; row < 128; row++) {
                        // 32 MiB of fresh sample payloads must pass through a 32 MiB Worker heap
                        // in small released groups, in addition to the JVM and PDF's other live data.
                        session.execute(AppendTableRows.version1(graphicRow()));
                        LargeTableState state = session.query(InspectLargeTable.version1());
                        assertEquals(row + 1,state.getAcceptedRows());
                        assertTrue(state.getRetainedRows() <= 3);
                        if (state.getRetainedRows() == 3) {
                            session.execute(FlushTable.version1());
                            assertEquals(1,session.query(InspectLargeTable.version1()).getRetainedRows());
                            assertEquals(row,session.query(InspectLargeTable.version1()).getFlushedRows());
                            assertEquals(row / 2,session.query(PageCount.INSTANCE).intValue());
                        }
                    }
                    session.execute(CompleteTable.version1());
                    LargeTableState complete = session.query(InspectLargeTable.version1());
                    assertEquals(LargeTableState.Stage.COMPLETE,complete.getStage());
                    assertEquals(0,complete.getRetainedRows()); assertEquals(128,complete.getFlushedRows());
                    return null;
                });
        assertEquals(PublicationStatus.COMMITTED,outcome.getPublicationReceipts().get(0).getStatus());
        assertTrue(outcome.getResourceUsage().getPeakOwnedMemoryBytes() <= (8L << 20));
        assertEquals(Integer.valueOf(64),new DocumentWorkflow(environment).execute(WorkflowRequest.builder()
                .source("primary",DocumentSource.path(target)).primarySource("primary").saveMode(SaveMode.REWRITE)
                .executionProfile(profile).build(),session -> session.query(PageCount.INSTANCE)).getResult());
    }

    private static TableRow graphicRow() {
        CanvasImage image = CanvasImage.rawSamples(512,512,8,CanvasColorSpace.deviceGray(),new byte[512 * 512]);
        CanvasTransparencyGroup inner = CanvasTransparencyGroup.version1(CanvasRectangle.of(0,0,1,1),
                CanvasColorSpace.deviceGray(),true,false,CanvasProgram.version2()
                        .drawImage(image,CanvasMatrix.of(1,0,0,1,0,0)).build());
        CanvasTransparencyGroup outer = CanvasTransparencyGroup.version1(CanvasRectangle.of(0,0,1,1),
                CanvasColorSpace.deviceGray(),true,false,CanvasProgram.version2()
                        .drawTransparencyGroup(inner,CanvasMatrix.of(1,0,0,1,0,0)).build());
        return TableRow.version1(TableCell.version1().paragraph(Paragraph.version1(1).graphic(outer,1,1).build()).build());
    }

    private static CompositionLimits graphicLimits() {
        return graphicLimits(64,1);
    }

    private static CompositionLimits graphicLimits(int rows,int pages) {
        return baseLimits(rows,0).maximumPages(pages).maximumAreas(pages).maximumLines(rows).maximumGeneratedContentBytes(16L << 20)
                .fontLimits(FontLimits.builder().maximumFontSources(0).maximumSourceBytes(0).maximumCodePoints(0)
                        .maximumFallbackChecks(0).maximumGeneratedContentBytes(0).build())
                .tableLimits(TableLimits.builder().maximumTables(1).maximumRows(rows).maximumCells(rows)
                        .maximumColumns(1).maximumGridSlots(rows).maximumLayoutWork(1000000).build())
                .graphicLimits(CanvasResourceLimits.builder().maximumEncodedImageBytes(64L << 20)
                        .maximumDecodedImagePixels(64L << 20).maximumDecodedImageBytes(64L << 20)
                        .maximumIccProfileBytes(0).maximumMaskBytes(0).maximumGeneratedContentBytes(16L << 20)
                        .maximumResourceDeclarations(512).maximumTransparencyGroupDepth(4).build()).build();
    }

    private static WorkflowResourcePolicy graphicPolicy(long memory) {
        return graphicPolicy(memory,1);
    }

    private static WorkflowResourcePolicy graphicPolicy(long memory,int pages) {
        return WorkflowResourcePolicy.builder().maximumInputBytes(1 << 20).maximumPages(pages).maximumObjects(10000)
                .maximumNestingDepth(64).maximumDecompressedBytes(256L << 20).maximumDecodedPixels(64L << 20)
                .maximumOwnedMemoryBytes(memory).maximumTemporaryStorageBytes(64L << 20)
                .maximumElapsedTime(Duration.ofSeconds(90)).maximumConcurrentWorkflows(1).build();
    }

    private void streamTwoRows(ParagraphFlow flow,CompositionLimits budget,Path target) throws DocumentFailure {
        new DocumentWorkflow().execute(WorkflowRequest.builder().target("result",PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> {
                    session.execute(BeginLargeTable.version1(flow,budget,2));
                    session.execute(AppendTableRows.version1(row("A"),row("A")));
                    session.execute(FlushTable.version1());
                    assertEquals(1,session.query(PageCount.INSTANCE).intValue());
                    try { session.execute(CompleteTable.version1()); }
                    catch (DocumentFailure expected) {
                        assertEquals(1,session.query(PageCount.INSTANCE).intValue());
                        assertEquals(1,session.query(InspectLargeTable.version1()).getRetainedRows());
                        throw expected;
                    }
                    assertEquals(2,session.query(PageCount.INSTANCE).intValue());
                    return null;
                });
    }

    private static void countOperatorBytes(DocumentSession session,PdfValue value,long[] totals) throws DocumentFailure {
        if (value instanceof PdfIndirectReference) {
            value = session.query(InspectObject.version1(((PdfIndirectReference) value).getReference(),PdfInspectionLimits.of(1024,1 << 20)));
        }
        if (value instanceof PdfStream) {
            byte[] bytes = ((PdfStream) value).readBytes(); totals[0] += bytes.length;
            if (new String(bytes,StandardCharsets.US_ASCII).contains("BT")) { totals[1] += bytes.length; }
        } else {
            PdfArray array = (PdfArray) value;
            for (int i = 0; i < array.size(); i++) { countOperatorBytes(session,array.get(i),totals); }
        }
    }

    private static void position(List<PageText> pages,int page,int item,double y) {
        assertEquals(23,pages.get(page).getTextItems().get(item).getGeometry().getE().doubleValue(),0.0001);
        assertEquals(y,pages.get(page).getTextItems().get(item).getGeometry().getF().doubleValue(),0.0001);
    }
    private static TableRow row(String text) {
        return TableRow.version1(cell(text,1));
    }
    private static TableCell cell(String text,int rowspan) {
        return TableCell.version1().paragraph(Paragraph.version1(12).text(text,10).build()).rowspan(rowspan)
                .padding(CellPadding.of(2,2,2,2)).borders(TableBorders.of(1,1,1,1)).build();
    }
    private static CompositionLimits limits() {
        return counterLimits(7,7,7,7,7);
    }
    private static CompositionLimits counterLimits(int rows,int cells,long slots,int inlines,int scalars) {
        return baseLimits(inlines,scalars)
                .tableLimits(TableLimits.builder().maximumTables(1).maximumRows(rows).maximumCells(cells)
                        .maximumColumns(1).maximumGridSlots(slots).maximumLayoutWork(100000).build()).build();
    }
    private static CompositionLimits.Builder baseLimits(int inlines,int scalars) {
        return CompositionLimits.version4().maximumPages(3).maximumAreas(3).maximumFlowItems(1)
                .maximumInlines(inlines).maximumLines(8).maximumLayoutAttempts(10000).maximumRelayouts(4)
                .maximumGeneratedContentBytes(1 << 20)
                .fontLimits(fontLimits(scalars,1000))
                .graphicLimits(CanvasResourceLimits.builder().maximumEncodedImageBytes(1024).maximumDecodedImagePixels(1024)
                        .maximumDecodedImageBytes(4096).maximumIccProfileBytes(0).maximumMaskBytes(1024)
                        .maximumGeneratedContentBytes(4096).maximumResourceDeclarations(16).maximumTransparencyGroupDepth(4).build());
    }
    private static FontLimits fontLimits(int scalars,long checks) {
        return FontLimits.builder().maximumFontSources(2).maximumSourceBytes(2000).maximumCodePoints(scalars)
                .maximumFallbackChecks(checks).maximumGeneratedContentBytes(1 << 20).build();
    }
    private static FontSelection selection() throws Exception {
        return FontSelection.explicit(FontSource.bytes(font("FolioPrimary")),FontSource.bytes(font("FolioFallback")));
    }
    private static byte[] font(String name) throws Exception {
        try (InputStream input = LargeTableWorkflowTest.class.getResourceAsStream("/net/zerocloud/pdf/fixtures/"+name+".ttf.base64")) {
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024]; int count;
            while ((count=input.read(buffer))!=-1) { encoded.write(buffer,0,count); }
            byte[] bytes = Base64.getMimeDecoder().decode(encoded.toByteArray());
            String expected = name.equals("FolioPrimary")
                    ? "e2fbb634c3c0fe78efb449bde4426d10a93aa813596ff5cf3360f02ec97673fb"
                    : "ced760bc126036779fa84ad3da4638733032512457bf599c44d8c455360f75e1";
            StringBuilder hash = new StringBuilder();
            for (byte part : MessageDigest.getInstance("SHA-256").digest(bytes)) {
                hash.append(Character.forDigit((part & 255) >>> 4,16)).append(Character.forDigit(part & 15,16));
            }
            assertEquals(expected,hash.toString());
            return bytes;
        }
    }
    private static ExtractionLimits extraction() {
        return ExtractionLimits.builder().maximumPages(64).maximumPageTreeNodes(256).maximumContentStreams(1024)
                .maximumContentStreamDepth(8).maximumDecodedBytes(1 << 20).maximumTextItems(10000)
                .maximumUnicodeCodePoints(10000).maximumToUnicodeMappings(64).maximumFontDataEntries(512)
                .maximumMarkedContentSequences(8).maximumMarkedContentDepth(4).maximumStructureElements(8)
                .maximumStructureItems(8).maximumStructureDepth(4).maximumRoleMappings(4).build();
    }
    private static WorkflowResourcePolicy policy(int pages) {
        return WorkflowResourcePolicy.builder().maximumInputBytes(1 << 20).maximumPages(pages).maximumObjects(10000)
                .maximumNestingDepth(64).maximumDecompressedBytes(16 << 20).maximumDecodedPixels(1024)
                .maximumOwnedMemoryBytes(32 << 20).maximumTemporaryStorageBytes(16 << 20)
                .maximumElapsedTime(Duration.ofSeconds(120)).maximumConcurrentWorkflows(2).build();
    }
    private static final class TrackingStream extends ByteArrayInputStream {
        private boolean closed;
        TrackingStream(byte[] bytes) { super(bytes); }
        @Override public void close() { closed = true; }
    }
}
