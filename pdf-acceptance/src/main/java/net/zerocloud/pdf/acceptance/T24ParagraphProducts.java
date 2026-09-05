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
import net.zerocloud.pdf.composition.PositionedUnicodeText;
import net.zerocloud.pdf.composition.ReferenceFontSet;
import net.zerocloud.pdf.composition.command.ComposeParagraphs;
import net.zerocloud.pdf.composition.command.DrawCanvas;
import net.zerocloud.pdf.composition.command.DrawPositionedUnicodeText;

/** Public-workflow fixture authoring; the reference never invokes paragraph layout. */
final class T24ParagraphProducts {
    static final String PRIMARY_HASH = "e2fbb634c3c0fe78efb449bde4426d10a93aa813596ff5cf3360f02ec97673fb";
    static final String FALLBACK_HASH = "ced760bc126036779fa84ad3da4638733032512457bf599c44d8c455360f75e1";
    private T24ParagraphProducts() { }

    static WorkflowOutcome<Void> create(Path target) throws Exception {
        PageMargins margins = PageMargins.of(72, 72, 72, 72);
        ParagraphFlow flow = ParagraphFlow.version1(FontSelection.referenceFontSet())
                .page(LayoutPage.version1(612, 792, margins,
                        CanvasRectangle.of(0, 480, 72, 560), CanvasRectangle.of(160, 480, 232, 560)))
                .page(LayoutPage.version1(612, 792, margins))
                .paragraph(Paragraph.version1(40).text("AA AA ", 40)
                        .graphic(graphic(), 32, 32).text("B\u03a9 B\u03a9 B\u03a9", 40).build()).build();
        return workflow().execute(WorkflowRequest.create(target, SaveMode.REWRITE), session -> {
            session.execute(ComposeParagraphs.version1(flow, CompositionLimits.builder()
                    .maximumPages(2).maximumAreas(3).maximumFlowItems(1).maximumInlines(3)
                    .maximumLines(5).maximumGeneratedContentBytes(4096).fontLimits(fontLimits())
                    .graphicLimits(graphicLimits()).build()));
            return null;
        });
    }

    static void createReference(Path target) throws Exception {
        workflow().execute(WorkflowRequest.create(target, SaveMode.REWRITE), session -> {
            session.execute(AddBlankPage.INSTANCE);
            session.execute(AddBlankPage.INSTANCE);
            for (T24ParagraphExpectations.Run run : T24ParagraphExpectations.RUNS) {
                session.execute(DrawPositionedUnicodeText.version1(run.page,
                        PositionedUnicodeText.version1(run.text, FontSelection.referenceFontSet(),
                                40, TextRenderingMode.FILL, CanvasMatrix.of(1, 0, 0, 1, run.x, run.y)), fontLimits()));
            }
            session.execute(DrawCanvas.version2(1, CanvasProgram.version2().drawTransparencyGroup(
                    graphic(), CanvasMatrix.of(32, 0, 0, 32, 232, 600)).build(), graphicLimits()));
            return null;
        });
    }

    private static DocumentWorkflow workflow() throws Exception {
        byte[] primary = T19FontEvidenceRecorder.font("FolioPrimary.ttf.base64");
        byte[] fallback = T19FontEvidenceRecorder.font("FolioFallback.ttf.base64");
        if (!PRIMARY_HASH.equals(EvidenceFiles.sha256(primary))
                || !FALLBACK_HASH.equals(EvidenceFiles.sha256(fallback))) {
            throw new IllegalStateException("The explicit T24 font hashes changed");
        }
        return new DocumentWorkflow(WorkflowEnvironment.builder().referenceFontSet(ReferenceFontSet.version1(
                FontSource.bytes(primary), FontSource.bytes(fallback))).build());
    }

    private static CanvasTransparencyGroup graphic() {
        return CanvasTransparencyGroup.version1(CanvasRectangle.of(0, 0, 1, 1), CanvasColorSpace.deviceRgb(),
                true, false, CanvasProgram.version2().setFillColor(CanvasColor.rgb(0.2, 0.4, 0.8))
                        .moveTo(0, 0).lineTo(1, 0).lineTo(1, 1).lineTo(0, 1).closePath()
                        .fill(CanvasWindingRule.NONZERO).build());
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
