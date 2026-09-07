package net.zerocloud.pdf.acceptance;

import java.nio.file.Path;
import java.util.List;
import com.google.zxing.common.BitMatrix;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.composition.CanvasColor;
import net.zerocloud.pdf.composition.CanvasProgram;
import net.zerocloud.pdf.composition.CanvasResourceLimits;
import net.zerocloud.pdf.composition.CanvasWindingRule;
import net.zerocloud.pdf.composition.command.DrawCanvas;

/**
 * Independent geometry reference from completely qualified modules. Different
 * legal QR masks/automatic segmentations need not match another encoder's choice.
 * Module content is requalified before use; only literal profile geometry/color
 * drives painting. Existing Canvas commands never invoke barcode generation.
 */
final class T31BarcodeReference {
    private static final CanvasResourceLimits LIMITS=CanvasResourceLimits.builder().maximumEncodedImageBytes(0)
            .maximumDecodedImagePixels(0).maximumDecodedImageBytes(0).maximumIccProfileBytes(0).maximumMaskBytes(0)
            .maximumGeneratedContentBytes(1<<20).maximumResourceDeclarations(0).maximumTransparencyGroupDepth(0).build();
    private T31BarcodeReference() { }
    static void create(Path output,T31BarcodeAssertions.Observation observed) throws Exception {
        List<T31BarcodeProfile.Fixture> fixtures=T31BarcodeProfile.fixtures();
        T31BarcodeAssertions.require(observed.passed && observed.matrices.size()==fixtures.size(),"Only completely qualified matrices can define a geometry reference");
        for (int index=0;index<fixtures.size();index++) { T31MatrixOracle.inspect(observed.matrices.get(index),fixtures.get(index)); }
        new DocumentWorkflow().execute(WorkflowRequest.create(output,SaveMode.REWRITE),session -> {
            int page=0;
            for (T31BarcodeProfile.Fixture fixture : fixtures) {
                BitMatrix matrix=observed.matrices.get(page++); session.execute(AddBlankPage.INSTANCE);
                CanvasProgram.Builder canvas=canvas(fixture); int paths=0;
                for (int row=0;row<fixture.height;row++) {
                    int column=0;
                    while (column<fixture.width) {
                        if (!matrix.get(column,row)) { column++; continue; }
                        int start=column++;
                        while (column<fixture.width && matrix.get(column,row)) { column++; }
                        double x=fixture.barcode.getQuietZone()+start*fixture.barcode.getModuleWidth();
                        double y=fixture.barcode.getQuietZone()+(fixture.height-1-row)*fixture.barcode.getModuleHeight();
                        double right=fixture.barcode.getQuietZone()+column*fixture.barcode.getModuleWidth();
                        double top=y+fixture.barcode.getModuleHeight();
                        canvas.moveTo(x,y).lineTo(right,y).lineTo(right,top).lineTo(x,top).closePath().fill(CanvasWindingRule.NONZERO);
                        if (++paths==800) {
                            session.execute(DrawCanvas.version2(page,canvas.restoreState().build(),LIMITS));
                            canvas=canvas(fixture); paths=0;
                        }
                    }
                }
                if (paths>0) { session.execute(DrawCanvas.version2(page,canvas.restoreState().build(),LIMITS)); }
            }
            return null;
        });
    }
    private static CanvasProgram.Builder canvas(T31BarcodeProfile.Fixture fixture) {
        double[] color=fixture.barcode.getForegroundRgb();
        return CanvasProgram.version2().saveState().transform(fixture.placement).setFillColor(CanvasColor.rgb(color[0],color[1],color[2]));
    }
}
