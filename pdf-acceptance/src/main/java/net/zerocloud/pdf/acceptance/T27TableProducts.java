package net.zerocloud.pdf.acceptance;

import java.nio.file.Path;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.TextRenderingMode;
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.composition.CanvasColor;
import net.zerocloud.pdf.composition.CanvasColorSpace;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.composition.CanvasProgram;
import net.zerocloud.pdf.composition.CanvasRectangle;
import net.zerocloud.pdf.composition.CanvasResourceLimits;
import net.zerocloud.pdf.composition.CanvasWindingRule;
import net.zerocloud.pdf.composition.CellPadding;
import net.zerocloud.pdf.composition.CompositionLimits;
import net.zerocloud.pdf.composition.FontLimits;
import net.zerocloud.pdf.composition.FontSelection;
import net.zerocloud.pdf.composition.FontSource;
import net.zerocloud.pdf.composition.LayoutPage;
import net.zerocloud.pdf.composition.PageMargins;
import net.zerocloud.pdf.composition.Paragraph;
import net.zerocloud.pdf.composition.ParagraphFlow;
import net.zerocloud.pdf.composition.PositionedUnicodeText;
import net.zerocloud.pdf.composition.ReferenceFontSet;
import net.zerocloud.pdf.composition.Table;
import net.zerocloud.pdf.composition.TableBorders;
import net.zerocloud.pdf.composition.TableCell;
import net.zerocloud.pdf.composition.TableLimits;
import net.zerocloud.pdf.composition.TableRow;
import net.zerocloud.pdf.composition.TableWidth;
import net.zerocloud.pdf.composition.command.AppendTableRows;
import net.zerocloud.pdf.composition.command.BeginLargeTable;
import net.zerocloud.pdf.composition.command.CompleteTable;
import net.zerocloud.pdf.composition.command.ComposeParagraphs;
import net.zerocloud.pdf.composition.command.DrawCanvas;
import net.zerocloud.pdf.composition.command.DrawPositionedUnicodeText;
import net.zerocloud.pdf.composition.command.FlushTable;
import net.zerocloud.pdf.composition.command.RelayoutParagraphs;

/** Public Workflow producer and a separate reference writer that never invokes composition layout. */
final class T27TableProducts {
    static final String CAPABILITY = "composition.layout.tables";
    static final String PRIMARY_HASH = "e2fbb634c3c0fe78efb449bde4426d10a93aa813596ff5cf3360f02ec97673fb";
    static final String FALLBACK_HASH = "ced760bc126036779fa84ad3da4638733032512457bf599c44d8c455360f75e1";

    static WorkflowOutcome<Void> create(Path target) throws Exception {
        return workflow().execute(WorkflowRequest.create(target,SaveMode.REWRITE), session -> {
            compose(session,wholeRows(Table.Layout.FIXED),twoAreas(),twoAreas());
            compose(session,wholeRows(Table.Layout.AUTO),twoAreas(),twoAreas());
            Table spans = Table.version2(Table.Layout.FIXED,TableWidth.points(120),
                    TableWidth.points(40),TableWidth.points(40),TableWidth.points(40))
                    .row(TableRow.version1(cell("A\nB").rowspan(2).build(),cell("BB").colspan(2).build()))
                    .row(TableRow.version1(cell("A").build(),cell("\u03a9").build())).build();
            compose(session,spans,area(120,18),area(120,18));
            compose(session,repeating(3,false,false),area(40,72),area(40,72));
            session.execute(BeginLargeTable.version1(flow(area(40,72),area(40,72),area(40,72))
                    .table(repeating(0,false,true)).build(),limits(),3));
            session.execute(AppendTableRows.version1(row("B"),row("B"),row("B")));
            session.execute(FlushTable.version1());
            session.execute(AppendTableRows.version1(row("B"),row("B")));
            session.execute(CompleteTable.version1());
            compose(session,Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto())
                    .row(row("A\nB\n\u03a9")).build(),area(40,30),area(40,30));
            Table kept = Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto())
                    .row(row("B")).row(row("B")).keepTogether(true).keepWithNext(true).build();
            session.execute(ComposeParagraphs.version4(flow(area(40,48),area(40,48)).paragraph(paragraph("A"))
                    .table(kept).paragraph(paragraph("\u03a9")).build(),limits(),ComposeParagraphs.FlushMode.IMMEDIATE));
            Table relayout = Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto())
                    .row(row("B")).row(row("B")).row(row("B")).build();
            session.execute(ComposeParagraphs.version4(flow(area(40,36),area(40,36)).table(relayout).build(),limits()));
            session.execute(RelayoutParagraphs.version1(area(40,54)));
            compose(session,Table.version2(Table.Layout.FIXED,TableWidth.points(18),TableWidth.auto())
                    .overflow(Paragraph.Overflow.VISIBLE).row(row("AAAA")).build(),area(40,36));
            Table unsplit = Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto())
                    .splitRows(false).row(row("A\nB")).build();
            session.execute(ComposeParagraphs.version4(flow(area(40,36),area(40,36)).paragraph(paragraph("A"))
                    .table(unsplit).build(),limits(),ComposeParagraphs.FlushMode.IMMEDIATE));
            compose(session,repeating(3,true,true),area(40,72));
            return null;
        });
    }

    private static void compose(DocumentSession session,Table table,LayoutPage... pages) throws DocumentFailure {
        session.execute(ComposeParagraphs.version4(flow(pages).table(table).build(),limits(),ComposeParagraphs.FlushMode.IMMEDIATE));
    }
    private static ParagraphFlow.Builder flow(LayoutPage... pages) {
        ParagraphFlow.Builder flow = ParagraphFlow.version4(FontSelection.referenceFontSet());
        for (LayoutPage page : pages) { flow.page(page); }
        return flow;
    }
    private static LayoutPage area(double width,double height) {
        return LayoutPage.version1(612,792,PageMargins.of(72,72,72,72),CanvasRectangle.of(0,648-height,width,648));
    }
    private static LayoutPage twoAreas() {
        return LayoutPage.version1(612,792,PageMargins.of(72,72,72,72),
                CanvasRectangle.of(0,612,100,648),CanvasRectangle.of(100,612,200,648));
    }
    private static Table wholeRows(Table.Layout layout) {
        Table.Builder table = Table.version2(layout,TableWidth.points(100),
                layout == Table.Layout.FIXED ? TableWidth.points(40) : TableWidth.auto(),TableWidth.auto());
        for (int i = 0; i < 5; i++) { table.row(TableRow.version1(cell("AA").build(),cell("BBBB").build())); }
        return table.build();
    }
    private static Table repeating(int rows,boolean first,boolean last) {
        Table.Builder table = Table.version2(Table.Layout.FIXED,TableWidth.points(40),TableWidth.auto())
                .header(row("A")).footer(row("\u03a9")).skipFirstHeader(first).skipLastFooter(last);
        for (int i = 0; i < rows; i++) { table.row(row("B")); }
        return table.build();
    }
    private static Paragraph paragraph(String text) { return Paragraph.version1(12).text(text,10).build(); }
    private static TableCell.Builder cell(String text) {
        return TableCell.version1().paragraph(paragraph(text)).padding(CellPadding.of(2,2,2,2)).borders(TableBorders.of(1,1,1,1));
    }
    private static TableRow row(String text) { return TableRow.version1(cell(text).build()); }

    static void createReference(Path target) throws Exception {
        workflow().execute(WorkflowRequest.create(target,SaveMode.REWRITE), session -> {
            for (int i = 0; i < T27TableExpectations.PAGE_COUNT; i++) { session.execute(AddBlankPage.INSTANCE); }
            for (double[] cell : T27TableExpectations.CELLS) {
                for (double[] box : T27TableExpectations.borders(cell)) {
                    CanvasProgram program = CanvasProgram.version2().setFillColor(CanvasColor.of(CanvasColorSpace.deviceGray(),0))
                            .moveTo(box[0],box[1]).lineTo(box[2],box[1]).lineTo(box[2],box[3])
                            .lineTo(box[0],box[3]).closePath().fill(CanvasWindingRule.NONZERO).build();
                    session.execute(DrawCanvas.version2((int) cell[0],program,graphicLimits()));
                }
            }
            for (T27TableExpectations.Run run : T27TableExpectations.RUNS) {
                session.execute(DrawPositionedUnicodeText.version1(run.page,
                        PositionedUnicodeText.version1(run.text,FontSelection.referenceFontSet(),10,TextRenderingMode.FILL,
                                CanvasMatrix.of(1,0,0,1,run.x,run.y)),fontLimits()));
            }
            return null;
        });
    }
    private static DocumentWorkflow workflow() throws Exception {
        byte[] primary = T19FontEvidenceRecorder.font("FolioPrimary.ttf.base64");
        byte[] fallback = T19FontEvidenceRecorder.font("FolioFallback.ttf.base64");
        if (!PRIMARY_HASH.equals(EvidenceFiles.sha256(primary)) || !FALLBACK_HASH.equals(EvidenceFiles.sha256(fallback))) {
            throw new IllegalStateException("The explicit T27 font hashes changed");
        }
        return new DocumentWorkflow(WorkflowEnvironment.builder().referenceFontSet(ReferenceFontSet.version1(
                FontSource.bytes(primary),FontSource.bytes(fallback))).build());
    }
    private static CompositionLimits limits() {
        return CompositionLimits.version4().maximumPages(3).maximumAreas(6).maximumFlowItems(8).maximumInlines(256)
                .maximumLines(256).maximumLayoutAttempts(10000).maximumRelayouts(4).maximumGeneratedContentBytes(1 << 20)
                .tableLimits(TableLimits.builder().maximumTables(16).maximumRows(128).maximumCells(256).maximumColumns(4)
                        .maximumGridSlots(256).maximumLayoutWork(1000000).build())
                .fontLimits(fontLimits()).graphicLimits(graphicLimits()).build();
    }
    private static FontLimits fontLimits() {
        return FontLimits.builder().maximumFontSources(2).maximumSourceBytes(2000).maximumCodePoints(512)
                .maximumFallbackChecks(10000).maximumGeneratedContentBytes(1 << 16).build();
    }
    private static CanvasResourceLimits graphicLimits() {
        return CanvasResourceLimits.builder().maximumEncodedImageBytes(0).maximumDecodedImagePixels(0).maximumDecodedImageBytes(0)
                .maximumIccProfileBytes(0).maximumMaskBytes(0).maximumGeneratedContentBytes(4096).maximumResourceDeclarations(16)
                .maximumTransparencyGroupDepth(1).build();
    }
    private T27TableProducts() { }
}
