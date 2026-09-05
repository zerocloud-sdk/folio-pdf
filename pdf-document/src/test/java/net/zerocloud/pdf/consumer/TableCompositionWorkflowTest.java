package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import net.zerocloud.pdf.composition.Table;
import net.zerocloud.pdf.composition.TableRow;
import net.zerocloud.pdf.composition.TableCell;
import net.zerocloud.pdf.composition.TableWidth;
import net.zerocloud.pdf.composition.TableBorders;
import net.zerocloud.pdf.composition.CellPadding;
import net.zerocloud.pdf.composition.TableLimits;

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

/** T26 contracts through the public workflow, publication and reopened PDF values. */
@RunWith(Parameterized.class)
public final class TableCompositionWorkflowTest {
    private static final double EPSILON = 0.0001;
    private static final String CAPABILITY = "composition.layout.tables";
    private static final byte[] SENTINEL = {31, 41, 59};
    private final WorkflowExecutionProfile profile;
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> profiles() {
        return Arrays.asList(new Object[][] {{WorkflowExecutionProfile.IN_PROCESS},
            {WorkflowExecutionProfile.HARDENED_WORKER}});
    }
    public TableCompositionWorkflowTest(WorkflowExecutionProfile profile) { this.profile = profile; }

    @Test
    public void fixedPointPercentageAndAutoWidthsMatchIndependentCoordinates() throws Exception {
        Table table = Table.version1(Table.Layout.FIXED, TableWidth.points(200),
                TableWidth.points(40), TableWidth.percentage(25), TableWidth.auto())
                .row(TableRow.version1(cell("A", 3).build(), cell("B", 3).build(), cell("\u03a9", 3).build())).build();
        Path path = publish(flow(table), limits().build());
        PageText text = read(path).get(0);
        assertEquals("AB\u03a9", text.getText());
        position(text, 0, 24, 129, 6); position(text, 1, 64, 129, 6.5); position(text, 2, 114, 128.8, 7);
        assertBorders(path, new double[][] {{20,120,60,140}, {60,120,110,140}, {110,120,220,140}}, 1);
    }

    @Test
    public void automaticWidthsFollowContentPreferencesAndEqualSurplus() throws Exception {
        Table table = Table.version1(Table.Layout.AUTO, TableWidth.points(100), TableWidth.auto(), TableWidth.auto())
                .row(TableRow.version1(cell("AA", 2).build(), cell("BBBB", 2).build())).build();
        Path path = publish(flow(table), limits().build());
        PageText text = read(path).get(0);
        assertEquals("AABBBB", text.getText());
        position(text, 0, 23, 130, 6); position(text, 2, 66, 130, 6.5);
        assertBorders(path, new double[][] {{20,122,63,140}, {63,122,120,140}}, 1);
    }

    @Test
    public void automaticWidthsInterpolateBetweenMinimumAndPreferred() throws Exception {
        // M=12+12.5=24.5, P=18+32=50; W=37.25 is their midpoint.
        Table table = Table.version1(Table.Layout.AUTO, TableWidth.points(37.25), TableWidth.auto(), TableWidth.auto())
                .row(TableRow.version1(cell("AA", 2).build(), cell("BBBB", 2).build())).build();
        Path path = publish(flow(table), limits().build());
        PageText text = read(path).get(0);
        assertEquals("AABBBB", text.getText());
        position(text, 0, 23, 130, 6); position(text, 1, 23, 118, 6);
        position(text, 2, 38, 130, 6.5); position(text, 4, 38, 118, 6.5);
        assertBorders(path, new double[][] {{20,110,35,140}, {35,110,57.25,140}}, 1);
    }

    @Test
    public void rowAndColumnSpansPreserveGridBordersAndCellReadingOrder() throws Exception {
        Table table = Table.version1(Table.Layout.FIXED, TableWidth.points(120),
                TableWidth.points(40), TableWidth.points(40), TableWidth.points(40))
                .row(TableRow.version1(cell("A",2).rowspan(2).build(), cell("BB",2).colspan(2).build()))
                .row(TableRow.version1(cell("B",2).build(), cell("\u03a9",2).build())).build();
        Path path = publish(flow(table), limits().build());
        PageText text = read(path).get(0);
        assertEquals("ABBB\u03a9", text.getText());
        position(text,0,23,130,6); position(text,1,63,130,6.5);
        position(text,3,63,112,6.5); position(text,4,103,111.8,7);
        assertBorders(path,new double[][] {{20,104,60,140}, {60,122,140,140}, {60,104,100,122}, {100,104,140,122}},1);
    }

    @Test
    public void spanningContentGrowsRowsAndEmptyContinuationRowsAreExplicit() throws Exception {
        Table table = Table.version1(Table.Layout.FIXED, TableWidth.points(40), TableWidth.auto())
                .row(TableRow.version1(cell("A\nA\nA",2).rowspan(2).build()))
                .row(TableRow.version1()).build();
        Path path = publish(flow(table), limits().build());
        PageText text = read(path).get(0);
        assertEquals("AAA",text.getText());
        position(text,0,23,130,6); position(text,2,23,106,6);
        assertBorders(path,new double[][] {{20,98,60,140}},1);
    }

    @Test
    public void tableMovesIntactAndPercentageWidthsRecomputeInLaterArea() throws Exception {
        Table table = Table.version1(Table.Layout.FIXED,TableWidth.percentage(50),TableWidth.auto())
                .row(TableRow.version1(cell("AAAA",2).build())).build();
        ParagraphFlow flow=ParagraphFlow.version3(selection())
                .page(LayoutPage.version1(100,80,margins(10),CanvasRectangle.of(0,0,20,10)))
                .page(page(240,160,20)).table(table).paragraph(Paragraph.version2(12).text("B",10).build()).build();
        List<PageText> pages=read(publish(flow,limits().build()));
        assertEquals(2,pages.size()); assertEquals("",pages.get(0).getText()); assertEquals("AAAAB",pages.get(1).getText());
        position(pages.get(1),0,23,130,6); position(pages.get(1),4,20,115,6.5);
    }

    @Test
    public void layoutsRepeatAcrossExecutionsForBothAlgorithms() throws Exception {
        for (Table.Layout layout:Table.Layout.values()) {
            Table table=Table.version1(layout,TableWidth.percentage(100),TableWidth.points(40),TableWidth.auto())
                    .row(TableRow.version1(cell("AA",2).build(),cell("B \u03a9",2).build())).build();
            List<PageText> first=read(publish(flow(table),limits().build()));
            List<PageText> second=read(publish(flow(table),limits().build()));
            assertEquals(first.get(0).getText(),second.get(0).getText());
            for(int i=0;i<first.get(0).getTextItems().size();i++) {
                TextItem item=first.get(0).getTextItems().get(i);
                position(second.get(0),i,item.getGeometry().getE().doubleValue(),item.getGeometry().getF().doubleValue(),
                        item.getGeometry().getAdvanceX().doubleValue());
            }
        }
    }

    @Test
    public void malformedSpansFailBeforeOpeningCallerFonts() throws Exception {
        List<Table> malformed=Arrays.asList(
                one(cell("A",2).rowspan(0).build()),one(cell("A",2).colspan(-1).build()),
                one(cell("A",2).rowspan(Integer.MAX_VALUE).build()),one(cell("A",2).colspan(2).build()),
                Table.version1(Table.Layout.FIXED,TableWidth.points(80),TableWidth.auto(),TableWidth.auto())
                        .row(TableRow.version1(cell("A",2).build())).build(),
                Table.version1(Table.Layout.FIXED,TableWidth.points(80),TableWidth.auto(),TableWidth.auto())
                        .row(TableRow.version1(cell("A",2).build(),cell("B",2).rowspan(2).build()))
                        .row(TableRow.version1(cell("A",2).colspan(2).build())).build());
        for(Table table:malformed) {
            TrackingStream source=new TrackingStream(font("FolioPrimary"));
            ParagraphFlow flow=ParagraphFlow.version3(FontSelection.explicit(FontSource.stream(source)))
                    .page(page(240,160,20)).table(table).build();
            expectFailure(flow,limits().build(),DocumentFailureCode.TABLE_INVALID_SPAN);
            assertEquals(972,source.available()); assertFalse(source.closed);
        }
    }

    @Test
    public void impossibleWidthsAndHeightsNeverCorruptPublication() throws Exception {
        List<Table> impossible=Arrays.asList(
                Table.version1(Table.Layout.FIXED,TableWidth.points(20),TableWidth.points(30))
                        .row(TableRow.version1(cell("A",2).build())).build(),
                Table.version1(Table.Layout.FIXED,TableWidth.points(40),TableWidth.points(30))
                        .row(TableRow.version1(cell("A",2).build())).build(),
                Table.version1(Table.Layout.AUTO,TableWidth.points(10),TableWidth.auto())
                        .row(TableRow.version1(cell("A",2).build())).build(),
                one(cell("A",2).minimumWidth(TableWidth.points(41)).build()),
                Table.version1(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto())
                        .row(TableRow.version1(121,cell("A",2).build())).build());
        for(Table table:impossible) { expectFailure(flow(table),limits().build(),DocumentFailureCode.TABLE_CONSTRAINT_UNSATISFIED); }
    }

    @Test
    public void mixedFlowFailureIdentifiesTheUnplacedTableDespiteEarlierParagraphFlags() throws Exception {
        Table table = Table.version1(Table.Layout.FIXED, TableWidth.points(500), TableWidth.auto())
                .row(TableRow.version1(cell("A",2).build())).build();
        for (int constraint = 0; constraint < 3; constraint++) {
            ParagraphFlow flow = ParagraphFlow.version3(selection()).page(page(240,160,20))
                    .paragraph(Paragraph.version2(12).text("A",10).keepTogether(constraint == 1)
                            .keepWithNext(constraint == 2).build()).table(table).build();
            expectFailure(flow,limits().build(),DocumentFailureCode.TABLE_CONSTRAINT_UNSATISFIED);
        }
    }

    @Test
    public void discardedTableCandidatesDoNotMaskALaterParagraphFailure() throws Exception {
        for (TableWidth width : new TableWidth[] {TableWidth.points(40),TableWidth.percentage(100)}) {
            Table table = Table.version1(Table.Layout.FIXED,width,TableWidth.auto())
                    .row(TableRow.version1(cell("AAAA",2).build())).build();
            // The first table candidate fails on width or on the two-line bound.
            // The second fits with one line; only the following paragraph is too tall.
            ParagraphFlow flow = ParagraphFlow.version3(selection()).page(page(12,120,0))
                    .page(page(40,120,0)).table(table)
                    .paragraph(Paragraph.version2(200).text("A",10).build()).build();
            expectFailure(flow,limits().maximumLines(2).build(),DocumentFailureCode.COMPOSITION_AREA_EXHAUSTED);
        }
    }

    @Test
    public void automaticSpanningMinimumRequiresSurplusOrExplicitPositiveColumns() throws Exception {
        TableCell cell = TableCell.version1().colspan(2).minimumWidth(TableWidth.points(12))
                .paragraph(Paragraph.version1(12).text("A",10).build()).build();
        Table zeroColumn = Table.version1(Table.Layout.AUTO,TableWidth.points(12),TableWidth.auto(),TableWidth.auto())
                .row(TableRow.version1(cell)).build();
        expectFailure(flow(zeroColumn),limits().build(),DocumentFailureCode.TABLE_CONSTRAINT_UNSATISFIED);
        for (Table admitted : new Table[] {
                Table.version1(Table.Layout.AUTO,TableWidth.points(14),TableWidth.auto(),TableWidth.auto())
                        .row(TableRow.version1(cell)).build(),
                Table.version1(Table.Layout.AUTO,TableWidth.points(12),TableWidth.points(6),TableWidth.points(6))
                        .row(TableRow.version1(cell)).build()}) {
            PageText text = read(publish(flow(admitted),limits().build())).get(0);
            assertEquals("A",text.getText()); position(text,0,20,133,6);
        }
    }

    @Test
    public void aggregateTableDeclarationLimitsAdmitExactBoundsAndRejectFirstExcess() throws Exception {
        Table table=one(cell("A",2).build());
        TableLimits exact=tableLimits().maximumTables(1).maximumRows(1).maximumColumns(1).maximumCells(1).maximumGridSlots(1).build();
        publish(flow(table),limits().tableLimits(exact).build());
        TableLimits[] bounds={tableLimits().maximumTables(0).build(),tableLimits().maximumRows(0).build(),
            tableLimits().maximumColumns(0).build(),tableLimits().maximumCells(0).build(),tableLimits().maximumGridSlots(0).build()};
        for(TableLimits bound:bounds) { expectFailure(flow(table),limits().tableLimits(bound).build(),DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED); }
        ParagraphFlow two=ParagraphFlow.version3(selection()).page(page(240,160,20)).table(table).table(table).build();
        expectFailure(two,limits().tableLimits(exact).build(),DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED);
    }

    @Test
    public void tableLayoutWorkAndSearchTransitionsHaveExactIndependentBoundaries() throws Exception {
        // Single FIXED column, one A: 14 declared work units; candidate + transition = 2 attempts.
        Table table=one(cell("A",2).build());
        CompositionLimits exact=limits().maximumLayoutAttempts(2).maximumLines(1)
                .tableLimits(tableLimits().maximumLayoutWork(14).build()).build();
        publish(flow(table),exact);
        expectFailure(flow(table),limits().maximumLayoutAttempts(1).build(),DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED);
        expectFailure(flow(table),limits().tableLimits(tableLimits().maximumLayoutWork(13).build()).build(),DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED);
        expectFailure(flow(table),limits().maximumLines(0).build(),DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED);
    }

    @Test
    public void queryBarriersIncrementalAppendAndSessionExpiryArePreserved() throws Exception {
        Path source=path(); new DocumentWorkflow().execute(create(source).build(),session->{session.execute(AddBlankPage.INSTANCE);return null;});
        Path target=path(); final DocumentSession[] retained=new DocumentSession[1];
        ParagraphFlow flow=flow(one(cell("A",2).build()));
        new DocumentWorkflow().execute(open(source).target("result",PublicationTarget.path(target)).saveMode(SaveMode.INCREMENTAL).build(),session->{
            retained[0]=session; assertEquals(Integer.valueOf(1),session.query(PageCount.INSTANCE));
            session.executeBatch(Arrays.asList(ComposeParagraphs.version3(flow,limits().build())));
            assertEquals(Integer.valueOf(2),session.query(PageCount.INSTANCE));
            assertEquals("A",extract(session).get(1).getText());return null;
        });
        assertEquals(2,read(target).size());
        try {retained[0].execute(ComposeParagraphs.version3(flow,limits().build()));fail("Expired session");}
        catch(IllegalStateException expected) { }
    }

    @Test
    public void overlappingAutomaticSpanConstraintsHaveAFeasibleDeterministicAllocation() throws Exception {
        TableCell wide = TableCell.version1().minimumWidth(TableWidth.points(48)).colspan(2)
                .paragraph(Paragraph.version1(12).text("A",10).build()).build();
        TableCell small = TableCell.version1().paragraph(Paragraph.version1(12).text("A",10).build()).build();
        Table table = Table.version1(Table.Layout.AUTO, TableWidth.points(72),
                TableWidth.auto(), TableWidth.auto(), TableWidth.auto())
                .row(TableRow.version1(wide, small)).row(TableRow.version1(small, wide)).build();
        PageText text = read(publish(flow(table), limits().build())).get(0);
        // Minimum columns [6,42,6]; preferred is identical; the 18-point surplus adds 6 each.
        assertEquals("AAAA", text.getText());
        position(text,0,20,133,6); position(text,1,80,133,6);
        position(text,2,20,121,6); position(text,3,32,121,6);
    }

    @Test
    public void automaticPointAndPercentageColumnsStayExactWithCellMinimums() throws Exception {
        Table table = Table.version1(Table.Layout.AUTO, TableWidth.points(100),
                TableWidth.points(20), TableWidth.percentage(30), TableWidth.auto())
                .row(TableRow.version1(cell("A",2).build(), cell("B",2).build(),
                        cell("A",2).minimumWidth(TableWidth.percentage(50)).build())).build();
        Path path = publish(flow(table), limits().build());
        assertBorders(path, new double[][] {{20,122,40,140}, {40,122,70,140}, {70,122,120,140}},1);
        position(read(path).get(0),2,73,130,6);
    }

    @Test
    public void cellAlignmentGraphicsMultipleParagraphsAndMinimumRowHeightAffectGeometry() throws Exception {
        TableCell cell = TableCell.version1().padding(CellPadding.of(2,4,6,8))
                .paragraph(Paragraph.version1(12).text("A",10).alignment(Paragraph.Alignment.RIGHT).build())
                .paragraph(Paragraph.version1(12).graphic(square(),10,16).text("B",10).build()).build();
        Table table = Table.version1(Table.Layout.FIXED,TableWidth.points(60),TableWidth.auto())
                .row(TableRow.version1(50,cell)).build();
        ParagraphFlow flow = ParagraphFlow.version3(selection()).page(page(240,160,20)).table(table)
                .paragraph(Paragraph.version2(12).text("A",10).build()).build();
        PageText text = read(publish(flow,limits().build())).get(0);
        assertEquals("ABA",text.getText());
        position(text,0,70,131,6); position(text,1,38,110,6.5); position(text,2,20,83,6);
    }

    @Test
    public void emptyCellsNeedNoFontAndRespectDeclaredHeight() throws Exception {
        Table table = Table.version1(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto())
                .row(TableRow.version1(20,TableCell.version1().borders(TableBorders.of(1,1,1,1)).build())).build();
        ParagraphFlow flow = ParagraphFlow.version3(FontSelection.referenceFontSet()).page(page(240,160,20)).table(table).build();
        Path path = publish(flow,limits().maximumInlines(0).maximumLines(0)
                .fontLimits(fontLimits(0,0,0,0,0)).build());
        assertEquals("",read(path).get(0).getText());
        assertBorders(path,new double[][] {{20,120,60,140}},1);
    }

    @Test
    public void generatedBytesAndAggregateInlinesScalarsAndFontBudgetsHaveExactBounds() throws Exception {
        ParagraphFlow flow = flow(one(cell("A\nA",2).build()));
        Path first = publish(flow,limits().build());
        long bytes = new DocumentWorkflow().execute(open(first).build(),session ->
                Long.valueOf(content(session,1).getBytes(StandardCharsets.US_ASCII).length)).getResult();
        publish(flow,limits().maximumGeneratedContentBytes(bytes).maximumInlines(1).maximumLines(2)
                .fontLimits(fontLimits(2,2000,3,2,4096)).build());
        expectFailure(flow,limits().maximumGeneratedContentBytes(bytes-1).build(),DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED);
        expectFailure(flow,limits().maximumInlines(0).build(),DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED);
        expectFailure(flow,limits().fontLimits(fontLimits(2,2000,2,2,4096)).build(),DocumentFailureCode.FONT_LIMIT_EXCEEDED);
        expectFailure(flow,limits().fontLimits(fontLimits(1,2000,3,2,4096)).build(),DocumentFailureCode.FONT_LIMIT_EXCEEDED);
        expectFailure(flow,limits().fontLimits(fontLimits(2,1999,3,2,4096)).build(),DocumentFailureCode.FONT_LIMIT_EXCEEDED);
        expectFailure(flow,limits().fontLimits(fontLimits(2,2000,3,1,4096)).build(),DocumentFailureCode.FONT_LIMIT_EXCEEDED);
    }

    @Test
    public void exactFractionalGeometryFitsButFirstGeometricExcessFails() throws Exception {
        Table table = Table.version1(Table.Layout.FIXED,TableWidth.points(7.26),TableWidth.auto())
                .row(TableRow.version1(TableCell.version1().paragraph(Paragraph.version1(10.1).text("A",12.1).build()).build())).build();
        ParagraphFlow exact = ParagraphFlow.version3(selection()).page(page(7.26,10.1,0)).table(table).build();
        position(read(publish(exact,limits().build())).get(0),0,0,1.63,7.26);
        ParagraphFlow narrow = ParagraphFlow.version3(selection()).page(page(7.26-0.0000001,10.1,0)).table(table).build();
        ParagraphFlow shortArea = ParagraphFlow.version3(selection()).page(page(7.26,10.1-0.0000001,0)).table(table).build();
        expectFailure(narrow,limits().build(),DocumentFailureCode.TABLE_CONSTRAINT_UNSATISFIED);
        expectFailure(shortArea,limits().build(),DocumentFailureCode.TABLE_CONSTRAINT_UNSATISFIED);
    }

    @Test
    public void invalidNumbersCellParagraphVersionsAndFlowVersionsFailStably() throws Exception {
        for (TableWidth width : new TableWidth[] {TableWidth.points(0),TableWidth.points(Double.NaN),
                TableWidth.points(Double.POSITIVE_INFINITY),TableWidth.percentage(101),TableWidth.auto()}) {
            Table table = Table.version1(Table.Layout.FIXED,width,TableWidth.auto()).row(TableRow.version1(cell("A",2).build())).build();
            expectFailure(flow(table),limits().build(),DocumentFailureCode.COMPOSITION_INVALID);
        }
        expectFailure(flow(one(cell("A",2).padding(CellPadding.of(-1,0,0,0)).build())),limits().build(),DocumentFailureCode.COMPOSITION_INVALID);
        expectFailure(flow(one(cell("A",2).borders(TableBorders.of(0,Double.NaN,0,0)).build())),limits().build(),DocumentFailureCode.COMPOSITION_INVALID);
        expectFailure(flow(one(TableCell.version1().paragraph(Paragraph.version2(12).text("A",10).build()).build())),
                limits().build(),DocumentFailureCode.COMPOSITION_INVALID);
        ParagraphFlow legacy = ParagraphFlow.version2(selection()).page(page(240,160,20)).table(one(cell("A",2).build())).build();
        expectFailure(legacy,limits().build(),DocumentFailureCode.COMPOSITION_INVALID);
        expectFailure(flow(one(cell("A",2).build())),limits().maximumRelayouts(1).build(),DocumentFailureCode.COMPOSITION_INVALID);
    }

    @Test
    public void failedPaintingAndLayoutLeavePreviousPagesObservableWithoutPartialTables() throws Exception {
        ParagraphFlow flow = flow(one(cell("A",2).build()));
        Path target = path();
        new DocumentWorkflow().execute(create(target).build(),session -> {
            session.execute(AddBlankPage.INSTANCE);
            for (CompositionLimits bound : new CompositionLimits[] {limits().maximumLines(0).build(),
                    limits().maximumGeneratedContentBytes(1).build()}) {
                try { session.execute(ComposeParagraphs.version3(flow,bound)); fail("Expected failure"); }
                catch (DocumentFailure failure) { assertEquals(DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED,failure.getCode()); }
                assertEquals(Integer.valueOf(1),session.query(PageCount.INSTANCE));
            }
            session.execute(ComposeParagraphs.version3(flow,limits().build()));
            assertEquals(Integer.valueOf(2),session.query(PageCount.INSTANCE)); return null;
        });
        assertEquals("A",read(target).get(1).getText());
    }

    @Test
    public void oneShotFontsRemainCallerOwnedAcrossTablesParagraphsAndLaterCommands() throws Exception {
        TrackingStream source = new TrackingStream(font("FolioPrimary"));
        FontSelection selection = FontSelection.explicit(FontSource.stream(source));
        ParagraphFlow flow = ParagraphFlow.version3(selection).page(page(240,160,20))
                .paragraph(Paragraph.version2(12).text("A",10).build()).table(one(cell("B",2).build())).build();
        Path target = path();
        new DocumentWorkflow().execute(create(target).build(),session -> {
            session.execute(ComposeParagraphs.version3(flow,limits().build()));
            assertEquals("AB",extract(session).get(0).getText());
            session.execute(ComposeParagraphs.version3(flow,limits().build())); return null;
        });
        assertFalse(source.closed); assertEquals(0,source.available());
        List<PageText> pages = read(target); assertEquals(2,pages.size()); assertEquals("AB",pages.get(1).getText());
    }

    @Test
    public void tableCompositionSealsBufferedParagraphRelayoutAndOffersNoTableRelayout() throws Exception {
        ParagraphFlow before = ParagraphFlow.version2(selection()).page(page(240,160,20))
                .paragraph(Paragraph.version2(12).text("A",10).build()).build();
        CompositionLimits paragraphLimits = CompositionLimits.version2().maximumPages(1).maximumAreas(1)
                .maximumFlowItems(1).maximumInlines(1).maximumLines(1).maximumLayoutAttempts(4).maximumRelayouts(1)
                .maximumGeneratedContentBytes(4096).fontLimits(fontLimits(2,2000,10,20,4096)).graphicLimits(limits().build().getGraphicLimits()).build();
        ParagraphFlow table = flow(one(cell("B",2).build()));
        new DocumentWorkflow().execute(create(path()).build(),session -> {
            session.execute(ComposeParagraphs.version2(before,paragraphLimits));
            session.execute(ComposeParagraphs.version3(table,limits().build()));
            try {session.execute(net.zerocloud.pdf.composition.command.RelayoutParagraphs.version1(page(240,160,20)));fail("Sealed flow");}
            catch (DocumentFailure failure) {assertEquals(DocumentFailureCode.COMPOSITION_RELAYOUT_UNSAFE,failure.getCode());}
            assertEquals(Integer.valueOf(2),session.query(PageCount.INSTANCE));return null;
        });
    }

    @Test
    public void existingSignaturesRejectCompositionBeforeReadingCallerFonts() throws Exception {
        for (SaveMode mode : SaveMode.values()) {
            byte[] signed = ProjectOwnedSignatureFixtures.ordinaryApprovalSignature();
            Path source = path(); Files.write(source, signed);
            Path output = path(); Files.write(output, SENTINEL);
            TrackingStream font = new TrackingStream(font("FolioPrimary"));
            ParagraphFlow flow = ParagraphFlow.version3(FontSelection.explicit(FontSource.stream(font)))
                    .page(page(80, 40, 10)).table(one(cell("A",2).build())).build();
            try {
                new DocumentWorkflow().execute(open(source).target("result", PublicationTarget.path(output))
                        .saveMode(mode).build(), session -> {
                            session.execute(ComposeParagraphs.version3(flow, limits().build())); return null;
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
        try (PasswordCredential owner = PasswordCredential.of("t26-owner".toCharArray());
                PasswordCredential user = PasswordCredential.of("t26-user".toCharArray())) {
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
                ParagraphFlow flow = ParagraphFlow.version3(FontSelection.explicit(FontSource.stream(font)))
                        .page(page(80, 40, 10)).table(one(cell("A",2).build())).build();
                WorkflowRequest request = WorkflowRequest.builder()
                        .source("primary", DocumentSource.path(source).withCredential(user)).primarySource("primary")
                        .target("result", PublicationTarget.path(output)).saveMode(SaveMode.INCREMENTAL)
                        .executionProfile(profile).build();
                try {
                    WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(request, session -> {
                        session.execute(ComposeParagraphs.version3(flow, limits().build())); return null;
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
    public void automaticLayoutWorkAdmitsItsExactBoundaryAndRejectsTheFirstExcess() throws Exception {
        Table table = Table.version1(Table.Layout.AUTO,TableWidth.points(40),TableWidth.auto())
                .row(TableRow.version1(cell("A",2).build())).build();
        // The documented AUTO single-cell example consumes 21 work units.
        publish(flow(table),limits().tableLimits(tableLimits().maximumLayoutWork(21).build()).build());
        expectFailure(flow(table),limits().tableLimits(tableLimits().maximumLayoutWork(20).build()).build(),
                DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED);
    }

    @Test
    public void rowCellAndGridSlotLimitsAreAggregateAndIncludeCoveredSlots() throws Exception {
        Table table = Table.version1(Table.Layout.FIXED,TableWidth.points(80),TableWidth.auto(),TableWidth.auto())
                .row(TableRow.version1(cell("A",2).rowspan(2).colspan(2).build())).row(TableRow.version1()).build();
        ParagraphFlow flow = ParagraphFlow.version3(selection()).page(page(240,160,20)).table(table).table(table).build();
        publish(flow,limits().tableLimits(tableLimits().maximumTables(2).maximumRows(4)
                .maximumColumns(2).maximumCells(2).maximumGridSlots(8).build()).build());
        for (TableLimits bound : new TableLimits[] {tableLimits().maximumTables(1).build(),
                tableLimits().maximumRows(3).build(),tableLimits().maximumCells(1).build(),
                tableLimits().maximumGridSlots(7).build()}) {
            expectFailure(flow,limits().tableLimits(bound).build(),DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED);
        }
    }

    @Test
    public void layoutLineLimitCanSkipANarrowAreaAndDiscardedWorkStillCounts() throws Exception {
        Table table = Table.version1(Table.Layout.FIXED,TableWidth.percentage(100),TableWidth.auto())
                .row(TableRow.version1(cell("AAAA",2).build())).build();
        ParagraphFlow flow = ParagraphFlow.version3(selection()).page(page(12,120,0))
                .page(page(40,120,0)).table(table).build();
        List<PageText> pages = read(publish(flow,limits().maximumLines(1).build()));
        assertEquals(2,pages.size()); assertEquals("",pages.get(0).getText()); assertEquals("AAAA",pages.get(1).getText());
        expectFailure(flow,limits().maximumLines(1).tableLimits(tableLimits().maximumLayoutWork(17).build()).build(),
                DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED);
    }

    @Test
    public void distinctBorderWidthsRemainInsideCellsAndAdjacentWidthsAdd() throws Exception {
        Table table = Table.version1(Table.Layout.FIXED,TableWidth.points(80),TableWidth.auto(),TableWidth.auto())
                .row(TableRow.version1(cell("A",0).borders(TableBorders.of(1,2,3,4)).build(),
                        cell("B",0).borders(TableBorders.of(0,0,0,3)).build())).build();
        Path path = publish(flow(table),limits().build());
        PageText text = read(path).get(0);
        position(text,0,24,132,6); position(text,1,63,133,6.5);
        List<double[]> rectangles = borders(path);
        double[][] expected = {{20,139,60,140},{58,124,60,140},{20,124,60,127},{20,124,24,140},{60,124,63,140}};
        assertEquals(expected.length,rectangles.size());
        for (int i = 0; i < expected.length; i++) { assertArrayEquals(expected[i],rectangles.get(i),EPSILON); }
    }

    @Test
    public void failedTableBatchSkipsLaterFontAcquisitionAndEveryTargetIsNotAttempted() throws Exception {
        Path a = path(), b = path(); Files.write(a,SENTINEL); Files.write(b,SENTINEL);
        TrackingStream source = new TrackingStream(font("FolioPrimary"));
        ParagraphFlow later = ParagraphFlow.version3(FontSelection.explicit(FontSource.stream(source)))
                .page(page(240,160,20)).table(one(cell("A",2).build())).build();
        ParagraphFlow invalid = flow(one(cell("A",2).rowspan(2).build()));
        try {
            new DocumentWorkflow().execute(create(a).target("second",PublicationTarget.path(b)).build(),session -> {
                session.executeBatch(Arrays.asList(ComposeParagraphs.version3(invalid,limits().build()),
                        ComposeParagraphs.version3(later,limits().build())));return null;
            });
            fail("Expected span failure");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.TABLE_INVALID_SPAN,failure.getCode());
            assertEquals(2,failure.getPublicationReceipts().size());
            for (net.zerocloud.pdf.PublicationReceipt receipt : failure.getPublicationReceipts()) {
                assertEquals(PublicationStatus.NOT_ATTEMPTED,receipt.getStatus());
            }
        }
        assertEquals(972,source.available()); assertFalse(source.closed);
        assertArrayEquals(SENTINEL,Files.readAllBytes(a)); assertArrayEquals(SENTINEL,Files.readAllBytes(b));
    }

    @Test
    public void tableWorkObeysWorkflowPagesAndModeledOwnedMemoryBeforePublication() throws Exception {
        expectResourceFailure(flow(one(cell("A",2).build())),limits().build(),policy(0,256L << 20),DocumentFailureCode.PAGE_LIMIT_EXCEEDED);
        TrackingStream source = new TrackingStream(font("FolioPrimary"));
        char[] text = new char[4096]; Arrays.fill(text,'A');
        ParagraphFlow flow = ParagraphFlow.version3(FontSelection.explicit(FontSource.stream(source)))
                .page(page(240,160,20)).table(one(cell(new String(text),2).build())).build();
        expectResourceFailure(flow,limits().build(),policy(16,1L << 20),DocumentFailureCode.MEMORY_LIMIT_EXCEEDED);
        assertEquals(972,source.available()); assertFalse(source.closed);
    }

    @Test
    public void oldFlowRepresentationsRejectTableItemsThroughTheirOwnCommands() throws Exception {
        Table table = one(cell("A",2).build());
        ParagraphFlow old = ParagraphFlow.version1(selection()).page(page(240,160,20)).table(table).build();
        CompositionLimits bound = CompositionLimits.builder().maximumPages(1).maximumAreas(1).maximumFlowItems(1)
                .maximumInlines(1).maximumLines(1).maximumGeneratedContentBytes(4096)
                .fontLimits(fontLimits(2,2000,10,20,4096)).graphicLimits(limits().build().getGraphicLimits()).build();
        Path target = path(); Files.write(target,SENTINEL);
        try {
            new DocumentWorkflow().execute(create(target).build(),session -> {
                session.execute(ComposeParagraphs.version1(old,bound));return null;
            });fail("Version 1 cannot silently drop a table");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.COMPOSITION_INVALID,failure.getCode());
            assertEquals(PublicationStatus.NOT_ATTEMPTED,failure.getPublicationReceipts().get(0).getStatus());
        }
        assertArrayEquals(SENTINEL,Files.readAllBytes(target));
    }

    private static TableCell.Builder cell(String text,double padding) {
        return TableCell.version1().paragraph(Paragraph.version1(12).text(text,10).build())
                .padding(CellPadding.of(padding,padding,padding,padding)).borders(TableBorders.of(1,1,1,1));
    }
    private static Table one(TableCell cell) {
        return Table.version1(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto()).row(TableRow.version1(cell)).build();
    }
    private static ParagraphFlow flow(Table table) throws Exception {
        return ParagraphFlow.version3(selection()).page(page(240,160,20)).table(table).build();
    }
    private static TableLimits.Builder tableLimits() {
        return TableLimits.builder().maximumTables(16).maximumRows(64).maximumColumns(16).maximumCells(256)
                .maximumGridSlots(1024).maximumLayoutWork(100000);
    }
    private void assertBorders(Path path,double[][] cells,double width) throws Exception {
        List<double[]> actual = borders(path);
        assertEquals(cells.length*4,actual.size());int i=0;
        for(double[] c:cells) {
            assertArrayEquals(new double[]{c[0],c[3]-width,c[2],c[3]},actual.get(i++),EPSILON);
            assertArrayEquals(new double[]{c[2]-width,c[1],c[2],c[3]},actual.get(i++),EPSILON);
            assertArrayEquals(new double[]{c[0],c[1],c[2],c[1]+width},actual.get(i++),EPSILON);
            assertArrayEquals(new double[]{c[0],c[1],c[0]+width,c[3]},actual.get(i++),EPSILON);
        }
    }

    private List<double[]> borders(Path path) throws Exception {
        return new DocumentWorkflow().execute(open(path).build(),session->{
            String stream=content(session,1);
            List<double[]> rectangles=new ArrayList<double[]>();
            java.util.regex.Matcher match=java.util.regex.Pattern.compile(
                    "([-0-9.]+) ([-0-9.]+) m\\s+([-0-9.]+) ([-0-9.]+) l\\s+([-0-9.]+) ([-0-9.]+) l\\s+([-0-9.]+) ([-0-9.]+) l\\s+h\\s+f").matcher(stream);
            while(match.find()) {
                double l=Double.parseDouble(match.group(1)),b=Double.parseDouble(match.group(2));
                double r=Double.parseDouble(match.group(5)),t=Double.parseDouble(match.group(6));
                assertEquals(l,Double.parseDouble(match.group(7)),EPSILON);assertEquals(b,Double.parseDouble(match.group(4)),EPSILON);
                assertEquals(r,Double.parseDouble(match.group(3)),EPSILON);assertEquals(t,Double.parseDouble(match.group(8)),EPSILON);
                rectangles.add(new double[]{l,b,r,t});
            }
            return rectangles;
        }).getResult();
    }

    private void expectResourceFailure(ParagraphFlow flow, CompositionLimits limits,
            WorkflowResourcePolicy policy, DocumentFailureCode code) throws Exception {
        Path output = path(); Files.write(output, SENTINEL);
        try {
            new DocumentWorkflow().execute(create(output).resourcePolicy(policy).build(), session -> {
                session.execute(ComposeParagraphs.version3(flow, limits)); return null;
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

    private Path publish(ParagraphFlow flow, CompositionLimits limits) throws Exception {
        DocumentWorkflow workflow = new DocumentWorkflow();
        Path output = path();
        WorkflowOutcome<Integer> outcome = workflow.execute(create(output).build(), session -> {
            session.execute(ComposeParagraphs.version3(flow, limits));
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
                session.execute(ComposeParagraphs.version3(flow, limits)); return null;
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
        return new DocumentWorkflow().execute(open(path).build(), TableCompositionWorkflowTest::extract).getResult();
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
    private static FontSelection selection() throws Exception {
        return FontSelection.explicit(FontSource.bytes(font("FolioPrimary")), FontSource.bytes(font("FolioFallback")));
    }
    private static byte[] font(String name) throws Exception {
        try (InputStream input = TableCompositionWorkflowTest.class.getResourceAsStream(
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
        return CompositionLimits.version3().maximumLayoutAttempts(100000).tableLimits(tableLimits().build()).maximumPages(16).maximumAreas(32).maximumFlowItems(32)
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
