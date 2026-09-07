package net.zerocloud.pdf.acceptance;

import java.nio.file.Path;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.composition.Barcode2DSize;
import net.zerocloud.pdf.composition.command.DrawBarcode2D;
import net.zerocloud.pdf.composition.query.MeasureBarcode2D;

/** Product generation and detached measurement use only public Workflow operations. */
final class T31BarcodeProducts {
    private T31BarcodeProducts() { }
    static void create(Path path,WorkflowExecutionProfile profile) throws DocumentFailure {
        new DocumentWorkflow().execute(WorkflowRequest.builder().target("output",PublicationTarget.path(path))
                .saveMode(SaveMode.REWRITE).executionProfile(profile).build(),session -> {
                    int page = 0;
                    for (T31BarcodeProfile.Fixture fixture : T31BarcodeProfile.fixtures()) {
                        Barcode2DSize measured = session.query(MeasureBarcode2D.version1(fixture.barcode));
                        T31BarcodeAssertions.near(fixture.width,measured.getMatrixWidth(),fixture.id + " measured columns");
                        T31BarcodeAssertions.near(fixture.height,measured.getMatrixHeight(),fixture.id + " measured rows");
                        T31BarcodeAssertions.near(fixture.widthPoints(),measured.getWidthPoints(),fixture.id + " measured width");
                        T31BarcodeAssertions.near(fixture.heightPoints(),measured.getHeightPoints(),fixture.id + " measured height");
                        session.execute(AddBlankPage.INSTANCE);
                        session.execute(DrawBarcode2D.version1(++page,fixture.barcode,fixture.placement));
                    }
                    return null;
                });
    }
}
