package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ExtractionLimits;
import net.zerocloud.pdf.PageText;
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
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.composition.CanvasRectangle;
import net.zerocloud.pdf.composition.CanvasResourceLimits;
import net.zerocloud.pdf.composition.CellPadding;
import net.zerocloud.pdf.composition.CompositionLimits;
import net.zerocloud.pdf.composition.FontLimits;
import net.zerocloud.pdf.composition.FontSelection;
import net.zerocloud.pdf.composition.FontSource;
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
import net.zerocloud.pdf.composition.command.ComposeParagraphs;
import net.zerocloud.pdf.composition.command.RelayoutParagraphs;
import net.zerocloud.pdf.composition.command.FlushParagraphs;
import net.zerocloud.pdf.query.ExtractTextAndStructure;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageObjectReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** T27 observations at the public Workflow seam and after reopening publication. */
@RunWith(Parameterized.class)
public final class TablePaginationWorkflowTest {
    private static final double EPSILON = 0.0001;
    private final WorkflowExecutionProfile profile;
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> profiles() {
        return Arrays.asList(new Object[][] {{WorkflowExecutionProfile.IN_PROCESS},
            {WorkflowExecutionProfile.HARDENED_WORKER}});
    }
    public TablePaginationWorkflowTest(WorkflowExecutionProfile profile) { this.profile = profile; }

    @Test
    public void fixedAndAutomaticTablesFillOrderedAreasBeforeAdvancingToTheNextPage() throws Exception {
        for (Table.Layout layout : Table.Layout.values()) {
            Table.Builder table = Table.version2(layout, TableWidth.points(100),
                    layout == Table.Layout.FIXED ? TableWidth.points(40) : TableWidth.auto(), TableWidth.auto());
            for (int row = 0; row < 5; row++) { table.row(TableRow.version1(cell("AA"), cell("BBBB"))); }
            LayoutPage page = LayoutPage.version1(240, 112, PageMargins.of(20,20,20,20),
                    CanvasRectangle.of(0,36,100,72), CanvasRectangle.of(100,36,200,72));
            ParagraphFlow flow = ParagraphFlow.version4(selection()).page(page).page(page).table(table.build()).build();
            List<PageText> pages = read(publish(flow, limits().build()));
            assertEquals(2, pages.size());
            assertEquals("AABBBBAABBBBAABBBBAABBBB", pages.get(0).getText());
            assertEquals("AABBBB", pages.get(1).getText());
            position(pages,0,0,23,82); position(pages,0,6,23,64);
            position(pages,0,12,123,82); position(pages,0,18,123,64);
            position(pages,1,0,23,82);
            double second = layout == Table.Layout.FIXED ? 63 : 66;
            position(pages,0,2,second,82); position(pages,0,14,second+100,82);
            position(pages,1,2,second,82);
        }
    }

    @Test
    public void anOversizedRowContinuesAtWholeLinesWithoutRepeatingOrDroppingText() throws Exception {
        Table table = Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto())
                .row(TableRow.version1(cell("A\nB\n\u03a9"))).build();
        LayoutPage page = LayoutPage.version1(80,100,PageMargins.of(20,20,20,20),
                CanvasRectangle.of(0,30,40,60));
        List<PageText> pages = read(publish(ParagraphFlow.version4(selection()).page(page).page(page)
                .table(table).build(),limits().build()));
        assertEquals(2,pages.size());
        assertEquals("AB",pages.get(0).getText()); assertEquals("\u03a9",pages.get(1).getText());
        position(pages,0,0,23,70); position(pages,0,1,23,58); position(pages,1,0,23,69.8);
    }

    @Test
    public void rowAndColumnSpansContinueWithIndependentFragmentBordersAndReadingOrder() throws Exception {
        TableCell spanning = TableCell.version1().paragraph(Paragraph.version1(12).text("A\nB",10).build())
                .padding(CellPadding.of(2,2,2,2)).borders(TableBorders.of(1,1,1,1)).rowspan(2).build();
        TableCell wide = TableCell.version1().paragraph(Paragraph.version1(12).text("BB",10).build())
                .padding(CellPadding.of(2,2,2,2)).borders(TableBorders.of(1,1,1,1)).colspan(2).build();
        Table table = Table.version2(Table.Layout.FIXED,TableWidth.points(120),
                TableWidth.points(40),TableWidth.points(40),TableWidth.points(40))
                .row(TableRow.version1(spanning,wide)).row(TableRow.version1(cell("A"),cell("\u03a9"))).build();
        LayoutPage page = LayoutPage.version1(160,100,PageMargins.of(20,20,20,20),
                CanvasRectangle.of(0,42,120,60));
        Path path = publish(ParagraphFlow.version4(selection()).page(page).page(page).table(table).build(),limits().build());
        List<PageText> pages = read(path);
        assertEquals(2,pages.size());
        assertEquals("ABB",pages.get(0).getText()); assertEquals("BA\u03a9",pages.get(1).getText());
        position(pages,0,0,23,70); position(pages,0,1,63,70);
        position(pages,1,0,23,70); position(pages,1,1,63,70); position(pages,1,2,103,69.8);
        assertBorders(path,1,new double[][] {{20,62,60,80},{60,62,140,80}});
        assertBorders(path,2,new double[][] {{20,62,60,80},{60,62,100,80},{100,62,140,80}});
    }

    @Test
    public void tableKeepsBacktrackToASmallerFragmentOrMoveTheCompleteTable() throws Exception {
        for (boolean together : new boolean[] {false,true}) {
            Table table = Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto())
                    .keepTogether(together).keepWithNext(true)
                    .row(TableRow.version1(cell("B"))).row(TableRow.version1(cell("B"))).build();
            LayoutPage page = LayoutPage.version1(80,88,PageMargins.of(20,20,20,20));
            ParagraphFlow flow = ParagraphFlow.version4(selection()).page(page).page(page)
                    .paragraph(Paragraph.version2(12).text("A",10).build()).table(table)
                    .paragraph(Paragraph.version2(12).text("\u03a9",10).build()).build();
            List<PageText> pages = read(publish(flow,limits().build()));
            assertEquals(2,pages.size());
            assertEquals(together ? "A" : "AB",pages.get(0).getText());
            assertEquals(together ? "BB\u03a9" : "B\u03a9",pages.get(1).getText());
            position(pages,1,0,23,58);
            position(pages,1,together ? 2 : 1,20,together ? 24.8 : 42.8);
        }
        Table kept = Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto())
                .keepTogether(true).keepWithNext(true)
                .row(TableRow.version1(cell("B"))).row(TableRow.version1(cell("B"))).build();
        LayoutPage insufficient = LayoutPage.version1(80,76,PageMargins.of(20,20,20,20));
        expectFailure(ParagraphFlow.version4(selection()).page(insufficient).page(insufficient).table(kept)
                .paragraph(Paragraph.version2(12).text("\u03a9",10).build()).build(),limits().build(),
                DocumentFailureCode.TABLE_CONSTRAINT_UNSATISFIED);
        LayoutPage enoughForKeptPrefix = LayoutPage.version1(80,88,PageMargins.of(20,20,20,20));
        expectFailure(ParagraphFlow.version4(selection()).page(enoughForKeptPrefix).page(enoughForKeptPrefix).table(kept)
                .paragraph(Paragraph.version2(12).text("A\nA\nA\nA\nA\nA",10).build()).build(),limits().build(),
                DocumentFailureCode.COMPOSITION_AREA_EXHAUSTED);
    }

    @Test
    public void aContinuedRowRewrapsItsRemainingTextAtTheNextAreasWidth() throws Exception {
        for (Table.Layout layout : Table.Layout.values()) {
            Table table = Table.version2(layout,TableWidth.percentage(100),TableWidth.auto())
                    .row(TableRow.version1(cell("AAAAAA"))).build();
            LayoutPage narrow = LayoutPage.version1(58,58,PageMargins.of(20,20,20,20));
            LayoutPage wide = LayoutPage.version1(70,58,PageMargins.of(20,20,20,20));
            Path path = publish(ParagraphFlow.version4(selection()).page(narrow).page(wide).table(table).build(),limits().build());
            List<PageText> pages = read(path);
            assertEquals(2,pages.size()); assertEquals("AA",pages.get(0).getText()); assertEquals("AAAA",pages.get(1).getText());
            position(pages,0,0,23,28); position(pages,1,0,23,28); position(pages,1,3,41,28);
            assertBorders(path,1,new double[][] {{20,20,38,38}});
            assertBorders(path,2,new double[][] {{20,20,50,38}});
        }
    }

    @Test
    public void repeatedHeadersAndFootersReserveSpaceAndFinalOmissionUsesTheFreedSpace() throws Exception {
        for (boolean skip : new boolean[] {false,true}) {
            Table table = Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto())
                    .header(TableRow.version1(cell("A"))).footer(TableRow.version1(cell("\u03a9")))
                    .skipFirstHeader(skip).skipLastFooter(skip)
                    .row(TableRow.version1(cell("B"))).row(TableRow.version1(cell("B")))
                    .row(TableRow.version1(cell("B"))).build();
            LayoutPage page = LayoutPage.version1(80,100,PageMargins.of(20,20,8,20));
            Path path = publish(ParagraphFlow.version4(selection()).page(page).page(page).table(table).build(),limits().build());
            List<PageText> pages = read(path);
            assertEquals(skip ? 1 : 2,pages.size());
            if (skip) {
                assertEquals("BBB",pages.get(0).getText());
                position(pages,0,0,23,70); position(pages,0,2,23,34);
                assertBorders(path,1,new double[][] {{20,62,60,80},{20,44,60,62},{20,26,60,44}});
            } else {
                assertEquals("ABB\u03a9",pages.get(0).getText()); assertEquals("AB\u03a9",pages.get(1).getText());
                position(pages,0,0,23,70); position(pages,0,1,23,52); position(pages,0,2,23,34);
                position(pages,0,3,23,15.8); position(pages,1,2,23,33.8);
                assertBorders(path,1,new double[][] {{20,62,60,80},{20,44,60,62},{20,26,60,44},{20,8,60,26}});
                assertBorders(path,2,new double[][] {{20,62,60,80},{20,44,60,62},{20,26,60,44}});
            }
        }
    }

    @Test
    public void bufferedTableRelayoutIsAtomicAndFlushSealsItsLastSuccessfulPages() throws Exception {
        Table table = Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto())
                .row(TableRow.version1(cell("B"))).row(TableRow.version1(cell("B")))
                .row(TableRow.version1(cell("B"))).build();
        LayoutPage small = LayoutPage.version1(80,76,PageMargins.of(20,20,20,20));
        ParagraphFlow flow = ParagraphFlow.version4(selection()).page(small).page(small).table(table).build();
        Path path = temporary.newFile().toPath();
        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .target("result",PublicationTarget.path(path)).saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> {
                    session.execute(ComposeParagraphs.version4(flow,limits().maximumRelayouts(2).build()));
                    assertEquals(2,session.query(PageCount.INSTANCE).intValue());
                    session.execute(RelayoutParagraphs.version1(LayoutPage.version1(80,94,PageMargins.of(20,20,20,20))));
                    assertEquals(1,session.query(PageCount.INSTANCE).intValue());
                    position(extract(session),0,0,23,64); position(extract(session),0,2,23,28);
                    try {
                        session.execute(RelayoutParagraphs.version1(LayoutPage.version1(80,75,PageMargins.of(20,20,20,20))));
                        fail("Insufficient replacement areas must fail");
                    } catch (DocumentFailure failure) {
                        assertEquals(DocumentFailureCode.TABLE_CONSTRAINT_UNSATISFIED,failure.getCode());
                    }
                    assertEquals(1,session.query(PageCount.INSTANCE).intValue());
                    assertEquals("BBB",extract(session).get(0).getText());
                    position(extract(session),0,2,23,28);
                    try {
                        session.execute(RelayoutParagraphs.version1(small,small));
                        fail("The failed relayout consumes the second and last allowed relayout");
                    } catch (DocumentFailure failure) {
                        assertEquals(DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED,failure.getCode());
                    }
                    assertEquals("BBB",extract(session).get(0).getText());
                    session.execute(FlushParagraphs.version1());
                    try {
                        session.execute(RelayoutParagraphs.version1(small,small));
                        fail("Flushed table declarations cannot be relaid out");
                    } catch (DocumentFailure failure) {
                        assertEquals(DocumentFailureCode.COMPOSITION_RELAYOUT_UNSAFE,failure.getCode());
                    }
                    return null;
                });
        assertEquals(PublicationStatus.COMMITTED,outcome.getPublicationReceipts().get(0).getStatus());
        List<PageText> pages = read(path);
        assertEquals(1,pages.size()); assertEquals("BBB",pages.get(0).getText());
        position(pages,0,2,23,28);
    }

    @Test
    public void laterMutationAndPublicationSealBufferedTableRelayout() throws Exception {
        Table table = Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto())
                .row(TableRow.version1(cell("A"))).build();
        LayoutPage page = LayoutPage.version1(80,76,PageMargins.of(20,20,20,20));
        ParagraphFlow flow = ParagraphFlow.version4(selection()).page(page).table(table).build();
        Path target = temporary.newFile().toPath();
        final DocumentSession[] expired = new DocumentSession[1];
        new DocumentWorkflow().execute(WorkflowRequest.builder().target("result",PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE).executionProfile(profile).build(),session -> {
                    expired[0] = session;
                    session.execute(ComposeParagraphs.version4(flow,limits().build()));
                    session.execute(AddBlankPage.INSTANCE);
                    try {
                        session.execute(RelayoutParagraphs.version1(page));
                        fail("A later page mutation seals the table layout");
                    } catch (DocumentFailure failure) {
                        assertEquals(DocumentFailureCode.COMPOSITION_RELAYOUT_UNSAFE,failure.getCode());
                    }
                    assertEquals(2,session.query(PageCount.INSTANCE).intValue());
                    return null;
                });
        try {
            expired[0].execute(RelayoutParagraphs.version1(page));
            fail("Published sessions cannot relayout their table");
        } catch (IllegalStateException expected) {
            // The public Session lifetime ends before publication returns.
        }
        List<PageText> pages = read(target);
        assertEquals(2,pages.size()); assertEquals("A",pages.get(0).getText()); assertEquals("",pages.get(1).getText());
    }

    @Test
    public void cellOverflowWrapsRejectsOrPreservesTheEntireVisibleWord() throws Exception {
        for (Paragraph.Overflow overflow : Paragraph.Overflow.values()) {
            Table table = Table.version2(Table.Layout.FIXED,TableWidth.points(18),TableWidth.auto())
                    .overflow(overflow).row(TableRow.version1(cell("AAAA"))).build();
            ParagraphFlow flow = ParagraphFlow.version4(selection())
                    .page(LayoutPage.version1(58,70,PageMargins.of(20,20,20,20))).table(table).build();
            if (overflow == Paragraph.Overflow.REJECT) {
                expectFailure(flow,limits().build(),DocumentFailureCode.TABLE_CONSTRAINT_UNSATISFIED);
            } else {
                List<PageText> pages = read(publish(flow,limits().build()));
                assertEquals(1,pages.size()); assertEquals("AAAA",pages.get(0).getText());
                position(pages,0,0,23,40);
                position(pages,0,2,overflow == Paragraph.Overflow.WRAP ? 23 : 35,
                        overflow == Paragraph.Overflow.WRAP ? 28 : 40);
            }
        }
    }

    @Test
    public void aRowFragmentFillsTheSpaceAfterEarlierCompleteRows() throws Exception {
        Table table = Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto())
                .row(TableRow.version1(cell("A"))).row(TableRow.version1(cell("B\nB\n\u03a9"))).build();
        LayoutPage page = LayoutPage.version1(80,100,PageMargins.of(20,20,20,20),CanvasRectangle.of(0,12,40,60));
        Path path = publish(ParagraphFlow.version4(selection()).page(page).page(page).table(table).build(),limits().build());
        List<PageText> pages = read(path);
        assertEquals(2,pages.size()); assertEquals("ABB",pages.get(0).getText()); assertEquals("\u03a9",pages.get(1).getText());
        position(pages,0,0,23,70); position(pages,0,1,23,52); position(pages,0,2,23,40); position(pages,1,0,23,69.8);
        assertBorders(path,1,new double[][] {{20,62,60,80},{20,32,60,62}});
        assertBorders(path,2,new double[][] {{20,62,60,80}});
    }

    @Test
    public void disablingRowSplittingMovesAnIntactRowAndRejectsAnImpossibleRow() throws Exception {
        Table table = Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto()).splitRows(false)
                .row(TableRow.version1(cell("A\nB\n\u03a9"))).build();
        LayoutPage small = LayoutPage.version1(80,70,PageMargins.of(20,20,20,20));
        LayoutPage large = LayoutPage.version1(80,82,PageMargins.of(20,20,20,20));
        List<PageText> pages = read(publish(ParagraphFlow.version4(selection()).page(small).page(large)
                .table(table).build(),limits().build()));
        assertEquals(2,pages.size()); assertEquals("",pages.get(0).getText()); assertEquals("AB\u03a9",pages.get(1).getText());
        position(pages,1,0,23,52); position(pages,1,2,23,27.8);
        expectFailure(ParagraphFlow.version4(selection()).page(small).page(small).table(table).build(),limits().build(),
                DocumentFailureCode.TABLE_CONSTRAINT_UNSATISFIED);
    }

    @Test
    public void paginatedWorkAndOutputBudgetsAdmitExactCountsAndRejectTheirFirstExcess() throws Exception {
        Table table = Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto())
                .row(TableRow.version1(cell("A"))).row(TableRow.version1(cell("A"))).build();
        LayoutPage page = LayoutPage.version1(80,58,PageMargins.of(20,20,20,20));
        ParagraphFlow flow = ParagraphFlow.version4(selection()).page(page).page(page).table(table).build();
        CompositionLimits exact = limits().maximumInlines(2).maximumLines(2).maximumLayoutAttempts(4)
                .tableLimits(TableLimits.builder().maximumTables(1).maximumRows(2).maximumCells(2)
                        .maximumColumns(1).maximumGridSlots(2).maximumLayoutWork(39).build()).build();
        assertEquals(2,read(publish(flow,exact)).size());
        expectFailure(flow,limits().maximumLines(1).build(),DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED);
        expectFailure(flow,limits().maximumLayoutAttempts(3).build(),DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED);
        expectFailure(flow,limits().tableLimits(TableLimits.builder().maximumTables(1).maximumRows(2).maximumCells(2)
                .maximumColumns(1).maximumGridSlots(2).maximumLayoutWork(38).build()).build(),DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED);
    }

    private Path publish(ParagraphFlow flow, CompositionLimits limits) throws Exception {
        Path target = temporary.newFile().toPath();
        WorkflowOutcome<Integer> outcome = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .target("result", PublicationTarget.path(target)).saveMode(SaveMode.REWRITE)
                .executionProfile(profile).build(), session -> {
                    session.execute(ComposeParagraphs.version4(flow, limits));
                    return session.query(PageCount.INSTANCE);
                });
        assertEquals("composition.layout.tables", outcome.getCapabilityId());
        assertEquals(profile, outcome.getExecutionProfile());
        assertEquals(PublicationStatus.COMMITTED, outcome.getPublicationReceipts().get(0).getStatus());
        return target;
    }
    private List<PageText> read(Path path) throws Exception {
        return new DocumentWorkflow().execute(open(path),
                TablePaginationWorkflowTest::extract).getResult();
    }
    private WorkflowRequest open(Path path) {
        return WorkflowRequest.builder().source("primary",DocumentSource.path(path)).primarySource("primary")
                .saveMode(SaveMode.REWRITE).executionProfile(profile).build();
    }
    private void expectFailure(ParagraphFlow flow, CompositionLimits bounds, DocumentFailureCode code) throws Exception {
        Path target = temporary.newFile().toPath();
        byte[] sentinel = {31,41,59};
        Files.write(target,sentinel);
        try {
            new DocumentWorkflow().execute(WorkflowRequest.builder().target("result",PublicationTarget.path(target))
                    .saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> {
                        session.execute(ComposeParagraphs.version4(flow,bounds)); return null;
                    });
            fail("Expected " + code);
        } catch (DocumentFailure failure) {
            assertEquals(code,failure.getCode());
            assertEquals("composition.layout.tables",failure.getCapabilityId());
            assertEquals(PublicationStatus.NOT_ATTEMPTED,failure.getPublicationReceipts().get(0).getStatus());
        }
        assertArrayEquals(sentinel,Files.readAllBytes(target));
    }
    private void assertBorders(Path path, int page, double[][] expectedCells) throws Exception {
        List<double[]> actual = new DocumentWorkflow().execute(open(path), session -> {
            PdfDictionary dictionary = (PdfDictionary) session.query(InspectObject.version1(
                    session.query(PageObjectReference.version1(page)),PdfInspectionLimits.of(1024,1 << 20)));
            String content = contents(session,dictionary.get(PdfName.of("Contents")));
            java.util.regex.Matcher match = java.util.regex.Pattern.compile(
                    "([-0-9.]+) ([-0-9.]+) m\\s+([-0-9.]+) ([-0-9.]+) l\\s+([-0-9.]+) ([-0-9.]+) l\\s+([-0-9.]+) ([-0-9.]+) l\\s+h\\s+f").matcher(content);
            List<double[]> boxes = new ArrayList<double[]>();
            while (match.find()) {
                boxes.add(new double[] {Double.parseDouble(match.group(1)),Double.parseDouble(match.group(2)),
                    Double.parseDouble(match.group(5)),Double.parseDouble(match.group(6))});
            }
            return boxes;
        }).getResult();
        assertEquals(expectedCells.length * 4,actual.size());
        int index = 0;
        for (double[] cell : expectedCells) {
            double l=cell[0],b=cell[1],r=cell[2],t=cell[3];
            assertArrayEquals(new double[] {l,t-1,r,t},actual.get(index++),EPSILON);
            assertArrayEquals(new double[] {r-1,b,r,t},actual.get(index++),EPSILON);
            assertArrayEquals(new double[] {l,b,r,b+1},actual.get(index++),EPSILON);
            assertArrayEquals(new double[] {l,b,l+1,t},actual.get(index++),EPSILON);
        }
    }
    private static String contents(DocumentSession session, PdfValue value) throws DocumentFailure {
        if (value instanceof PdfIndirectReference) {
            value = session.query(InspectObject.version1(((PdfIndirectReference) value).getReference(),
                    PdfInspectionLimits.of(1024,1 << 20)));
        }
        if (value instanceof PdfStream) { return new String(((PdfStream) value).readBytes(),StandardCharsets.US_ASCII); }
        StringBuilder text = new StringBuilder();
        PdfArray array = (PdfArray) value;
        for (int index = 0; index < array.size(); index++) { text.append(contents(session,array.get(index))).append('\n'); }
        return text.toString();
    }
    private static List<PageText> extract(DocumentSession session) throws DocumentFailure {
        return session.query(ExtractTextAndStructure.version1(ExtractionLimits.builder()
                .maximumPages(16).maximumPageTreeNodes(64).maximumContentStreams(1024).maximumContentStreamDepth(8)
                .maximumDecodedBytes(1 << 20).maximumTextItems(10000).maximumUnicodeCodePoints(10000)
                .maximumToUnicodeMappings(64).maximumFontDataEntries(512).maximumMarkedContentSequences(8)
                .maximumMarkedContentDepth(4).maximumStructureElements(8).maximumStructureItems(8)
                .maximumStructureDepth(4).maximumRoleMappings(4).build())).getPages();
    }
    private static TableCell cell(String text) {
        return TableCell.version1().paragraph(Paragraph.version1(12).text(text,10).build())
                .padding(CellPadding.of(2,2,2,2)).borders(TableBorders.of(1,1,1,1)).build();
    }
    private static CompositionLimits.Builder limits() {
        return CompositionLimits.version4().maximumPages(16).maximumAreas(32).maximumFlowItems(32)
                .maximumInlines(256).maximumLines(512).maximumLayoutAttempts(100000).maximumRelayouts(4)
                .maximumGeneratedContentBytes(1 << 20)
                .tableLimits(TableLimits.builder().maximumTables(16).maximumRows(64).maximumColumns(16)
                        .maximumCells(256).maximumGridSlots(1024).maximumLayoutWork(100000).build())
                .fontLimits(FontLimits.builder().maximumFontSources(2).maximumSourceBytes(2000)
                        .maximumCodePoints(10000).maximumFallbackChecks(20000).maximumGeneratedContentBytes(1 << 20).build())
                .graphicLimits(CanvasResourceLimits.builder().maximumEncodedImageBytes(1024)
                        .maximumDecodedImagePixels(1024).maximumDecodedImageBytes(4096).maximumIccProfileBytes(0)
                        .maximumMaskBytes(1024).maximumGeneratedContentBytes(4096)
                        .maximumResourceDeclarations(16).maximumTransparencyGroupDepth(4).build());
    }
    private static FontSelection selection() throws Exception {
        return FontSelection.explicit(FontSource.bytes(font("FolioPrimary")), FontSource.bytes(font("FolioFallback")));
    }
    private static byte[] font(String name) throws Exception {
        try (InputStream input = TablePaginationWorkflowTest.class.getResourceAsStream(
                "/net/zerocloud/pdf/fixtures/" + name + ".ttf.base64")) {
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) { encoded.write(buffer,0,count); }
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
    private static void position(List<PageText> pages, int page, int item, double x, double y) {
        assertEquals(x,pages.get(page).getTextItems().get(item).getGeometry().getE().doubleValue(),EPSILON);
        assertEquals(y,pages.get(page).getTextItems().get(item).getGeometry().getF().doubleValue(),EPSILON);
    }
}
