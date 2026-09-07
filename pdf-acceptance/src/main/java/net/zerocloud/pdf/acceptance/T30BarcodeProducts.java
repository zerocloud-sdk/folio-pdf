package net.zerocloud.pdf.acceptance;

import java.nio.file.Path;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.composition.command.DrawBarcode1D;

/** T30 product generation exclusively through the public Workflow boundary. */
final class T30BarcodeProducts {
    private T30BarcodeProducts() { }

    static WorkflowOutcome<Void> create(Path output, WorkflowExecutionProfile profile) throws DocumentFailure {
        return new DocumentWorkflow().execute(WorkflowRequest.builder().target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> {
                    int page = 0;
                    for (T30BarcodeProfile.Fixture fixture : T30BarcodeProfile.fixtures()) {
                        session.execute(AddBlankPage.INSTANCE);
                        session.execute(DrawBarcode1D.version1(++page, fixture.barcode, fixture.placement));
                    }
                    return null;
                });
    }
}
