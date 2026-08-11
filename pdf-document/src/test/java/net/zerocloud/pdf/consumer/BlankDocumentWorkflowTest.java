package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.PageCount;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class BlankDocumentWorkflowTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void consumerCreatesPublishesReopensAndInspectsOneBlankPage() throws Exception {
        Path target = temporaryFolder.newFolder("publication").toPath().resolve("blank.pdf");
        DocumentWorkflow workflow = new DocumentWorkflow();

        WorkflowOutcome<Void> creation = workflow.execute(
                WorkflowRequest.create(target, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });

        assertNull(creation.getResult());
        assertEquals(1, creation.getPublicationReceipts().size());
        assertEquals(target, creation.getPublicationReceipts().get(0).getTarget());
        assertEquals(PublicationStatus.COMMITTED,
                creation.getPublicationReceipts().get(0).getStatus());
        assertTrue(Files.isRegularFile(target));
        assertTrue(Files.size(target) > 0L);

        WorkflowOutcome<Integer> inspection = workflow.execute(
                WorkflowRequest.open(target, SaveMode.REWRITE),
                session -> session.query(PageCount.INSTANCE));

        assertEquals(Integer.valueOf(1), inspection.getResult());
        assertFalse(inspection.getPublicationReceipts().iterator().hasNext());
    }
}
