package net.zerocloud.pdf.acceptance;

import java.nio.file.Path;
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
import net.zerocloud.pdf.composition.CanvasResourceLimits;
import net.zerocloud.pdf.composition.CanvasWindingRule;
import net.zerocloud.pdf.composition.CompositionLimits;
import net.zerocloud.pdf.composition.Table;
import net.zerocloud.pdf.composition.TableCell;
import net.zerocloud.pdf.composition.TableRow;
import net.zerocloud.pdf.composition.TableWidth;
import net.zerocloud.pdf.composition.TableLimits;
import net.zerocloud.pdf.composition.CellPadding;
import net.zerocloud.pdf.composition.TableBorders;

import net.zerocloud.pdf.composition.FontLimits;
import net.zerocloud.pdf.composition.FontSelection;
import net.zerocloud.pdf.composition.FontSource;
import net.zerocloud.pdf.composition.LayoutPage;
import net.zerocloud.pdf.composition.PageMargins;
import net.zerocloud.pdf.composition.Paragraph;
import net.zerocloud.pdf.composition.ParagraphFlow;
import net.zerocloud.pdf.composition.PositionedUnicodeText;
import net.zerocloud.pdf.composition.ReferenceFontSet;
import net.zerocloud.pdf.composition.command.ComposeParagraphs;
import net.zerocloud.pdf.composition.command.DrawCanvas;
import net.zerocloud.pdf.composition.command.DrawPositionedUnicodeText;

/** Public-workflow fixture authoring; the reference never invokes table layout. */
final class T26TableProducts {
    static final String PRIMARY_HASH = "e2fbb634c3c0fe78efb449bde4426d10a93aa813596ff5cf3360f02ec97673fb";
    static final String FALLBACK_HASH = "ced760bc126036779fa84ad3da4638733032512457bf599c44d8c455360f75e1";
    private T26TableProducts() { }

    static WorkflowOutcome<Void> create(Path target) throws Exception {
        ParagraphFlow.Builder flow = ParagraphFlow.version3(FontSelection.referenceFontSet());
        for (int page = 0; page < 3; page++) { flow.page(LayoutPage.version1(612,792,PageMargins.of(72,72,72,72))); }
        Table fixed = Table.version1(Table.Layout.FIXED,TableWidth.points(200),
                TableWidth.points(40),TableWidth.percentage(25),TableWidth.auto())
                .row(TableRow.version1(cell("A",3).build(),cell("B",3).build(),cell("\u03a9",3).build())).build();
        Table auto = Table.version1(Table.Layout.AUTO,TableWidth.points(100),TableWidth.auto(),TableWidth.auto())
                .row(TableRow.version1(cell("AA",2).build(),cell("BBBB",2).build())).build();
        Table spans = Table.version1(Table.Layout.FIXED,TableWidth.points(120),
                TableWidth.points(40),TableWidth.points(40),TableWidth.points(40))
                .row(TableRow.version1(cell("A",2).rowspan(2).build(),cell("BB",2).colspan(2).build()))
                .row(TableRow.version1(cell("B",2).build(),cell("\u03a9",2).build())).build();
        flow.table(fixed).areaBreak().table(auto).areaBreak().table(spans);
        return workflow().execute(WorkflowRequest.create(target,SaveMode.REWRITE),session -> {
            session.execute(ComposeParagraphs.version3(flow.build(),CompositionLimits.version3()
                    .maximumPages(3).maximumAreas(3).maximumFlowItems(5).maximumInlines(9).maximumLines(9)
                    .maximumLayoutAttempts(1000).maximumGeneratedContentBytes(1 << 16)
                    .fontLimits(fontLimits()).graphicLimits(graphicLimits())
                    .tableLimits(TableLimits.builder().maximumTables(3).maximumRows(4).maximumColumns(3)
                            .maximumCells(9).maximumGridSlots(11).maximumLayoutWork(10000).build()).build()));
            return null;
        });
    }

    private static TableCell.Builder cell(String text,double padding) {
        return TableCell.version1().paragraph(Paragraph.version1(12).text(text,10).build())
                .padding(CellPadding.of(padding,padding,padding,padding)).borders(TableBorders.of(1,1,1,1));
    }

    static void createReference(Path target) throws Exception {
        workflow().execute(WorkflowRequest.create(target, SaveMode.REWRITE), session -> {
            for (int page = 0; page < 3; page++) { session.execute(AddBlankPage.INSTANCE); }
            for (double[] cell : T26TableExpectations.CELLS) {
                for (double[] box : T26TableExpectations.borders(cell)) {
                    CanvasProgram program = CanvasProgram.version2().setFillColor(CanvasColor.of(CanvasColorSpace.deviceGray(),0))
                            .moveTo(box[0],box[1]).lineTo(box[2],box[1]).lineTo(box[2],box[3])
                            .lineTo(box[0],box[3]).closePath().fill(CanvasWindingRule.NONZERO).build();
                    session.execute(DrawCanvas.version2((int)cell[0],program,graphicLimits()));
                }
            }
            for (T26TableExpectations.Run run : T26TableExpectations.RUNS) {
                session.execute(DrawPositionedUnicodeText.version1(run.page,
                        PositionedUnicodeText.version1(run.text,FontSelection.referenceFontSet(),10,
                                TextRenderingMode.FILL,CanvasMatrix.of(1,0,0,1,run.x,run.y)),fontLimits()));
            }
            return null;
        });
    }

    private static DocumentWorkflow workflow() throws Exception {
        byte[] primary = T19FontEvidenceRecorder.font("FolioPrimary.ttf.base64");
        byte[] fallback = T19FontEvidenceRecorder.font("FolioFallback.ttf.base64");
        if (!PRIMARY_HASH.equals(EvidenceFiles.sha256(primary))
                || !FALLBACK_HASH.equals(EvidenceFiles.sha256(fallback))) {
            throw new IllegalStateException("The explicit T26 font hashes changed");
        }
        return new DocumentWorkflow(WorkflowEnvironment.builder().referenceFontSet(ReferenceFontSet.version1(
                FontSource.bytes(primary), FontSource.bytes(fallback))).build());
    }

    private static FontLimits fontLimits() {
        return FontLimits.builder().maximumFontSources(2).maximumSourceBytes(2000)
                .maximumCodePoints(32).maximumFallbackChecks(64).maximumGeneratedContentBytes(4096).build();
    }
    private static CanvasResourceLimits graphicLimits() {
        return CanvasResourceLimits.builder().maximumEncodedImageBytes(0).maximumDecodedImagePixels(0)
                .maximumDecodedImageBytes(0).maximumIccProfileBytes(0).maximumMaskBytes(0)
                .maximumGeneratedContentBytes(4096).maximumResourceDeclarations(4).maximumTransparencyGroupDepth(1).build();
    }
}
