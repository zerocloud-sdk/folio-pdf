import java.nio.file.*;
import net.zerocloud.pdf.*;
import net.zerocloud.pdf.command.UpdateAnnotations;
public class EmptyUpdateProbe {
    public static void main(String[] args) throws Exception {
        Path source = Paths.get(args[0]), target = Paths.get(args[1]);
        try {
            WorkflowOutcome<Void> result = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .source("source", DocumentSource.path(source)).primarySource("source")
                .target("target", PublicationTarget.path(target)).saveMode(SaveMode.INCREMENTAL).build(), session -> {
                    session.execute(UpdateAnnotations.version1().build()); return null;
                });
            System.out.println("EMPTY_UPDATE_PUBLICATION=" + result.getPublicationReceipts().get(0).getStatus());
        } catch (DocumentFailure failure) {
            System.out.println("EMPTY_UPDATE_REFUSAL=" + failure.getCode());
        }
    }
}
