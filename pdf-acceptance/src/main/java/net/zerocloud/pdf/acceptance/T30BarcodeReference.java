package net.zerocloud.pdf.acceptance;

import java.nio.file.Path;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.TextRenderingMode;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.composition.BarcodeText;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.composition.CanvasProgram;
import net.zerocloud.pdf.composition.CanvasWindingRule;
import net.zerocloud.pdf.composition.PositionedUnicodeText;
import net.zerocloud.pdf.composition.command.DrawCanvas;
import net.zerocloud.pdf.composition.command.DrawPositionedUnicodeText;

/** Independent module paths and source-metric label coordinates; never invokes DrawBarcode1D. */
final class T30BarcodeReference {
    private T30BarcodeReference() { }

    static void create(Path output) throws DocumentFailure {
        new DocumentWorkflow().execute(WorkflowRequest.create(output, SaveMode.REWRITE), session -> {
            int page = 0;
            for (T30BarcodeProfile.Fixture fixture : T30BarcodeProfile.fixtures()) {
                session.execute(AddBlankPage.INSTANCE);
                CanvasProgram.Builder paths = CanvasProgram.version1().saveState().transform(fixture.placement);
                for (double[] bar : T30BarcodeOracle.bars(fixture)) {
                    paths.moveTo(bar[0], bar[1]).lineTo(bar[0] + bar[2], bar[1])
                            .lineTo(bar[0] + bar[2], bar[1] + bar[3]).lineTo(bar[0], bar[1] + bar[3])
                            .closePath().fill(CanvasWindingRule.NONZERO);
                }
                session.execute(DrawCanvas.version1(++page, paths.restoreState().build()));
                if (fixture.caption.isEmpty()) { continue; }
                BarcodeText style = fixture.barcode.getHumanReadable().get();
                double size = style.getFontSize(), width = 0;
                for (int index = 0; index < fixture.caption.length(); index++) {
                    width += T30FontMetrics.NOTO.width(fixture.caption.charAt(index), size);
                }
                double remaining = T30BarcodeOracle.width(fixture) - width;
                double x = style.getAlignment() == BarcodeText.Alignment.LEFT ? 0
                        : style.getAlignment() == BarcodeText.Alignment.RIGHT ? remaining : remaining / 2;
                double y = style.getPosition() == BarcodeText.Position.ABOVE
                        ? fixture.barcode.getBarHeight() + style.getGap() + T30FontMetrics.NOTO.descent(size)
                        : -fixture.barcode.getGuardExtension() - style.getGap() - T30FontMetrics.NOTO.ascent(size);
                CanvasMatrix p = fixture.placement;
                CanvasMatrix matrix = CanvasMatrix.of(p.getA(), p.getB(), p.getC(), p.getD(),
                        p.getE() + p.getA() * x + p.getC() * y, p.getF() + p.getB() * x + p.getD() * y);
                session.execute(DrawPositionedUnicodeText.version1(page, PositionedUnicodeText.version1(fixture.caption,
                        T30FontMetrics.NOTO.selection(), size, TextRenderingMode.FILL, matrix), style.getFontLimits()));
            }
            return null;
        });
    }
}
