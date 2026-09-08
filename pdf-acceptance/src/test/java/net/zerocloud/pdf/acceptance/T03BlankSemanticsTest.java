package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertEquals;

import java.nio.file.Path;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.DocumentRootReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class T03BlankSemanticsTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void reopensOneBlankPageAndRejectsTheTwoPageControl() throws Exception {
        for (WorkflowExecutionProfile profile : WorkflowExecutionProfile.values()) {
            for (int count : new int[] {1, 2}) {
                Path pdf = temporary.getRoot().toPath().resolve(profile + "-" + count + ".pdf");
                new DocumentWorkflow().execute(WorkflowRequest.builder()
                        .executionProfile(profile).target("output", PublicationTarget.path(pdf))
                        .saveMode(SaveMode.REWRITE).build(), session -> {
                            for (int index = 0; index < count; index++) {
                                session.execute(AddBlankPage.INSTANCE);
                            }
                            return null;
                        });
                assertEquals(count == 1 ? EvidenceResult.PASS : EvidenceResult.FAIL,
                        T03BlankSemantics.inspect(pdf, profile));
            }
        }
    }

    @Test public void rejectsStructuresOutsideTheQualifiedBlankProfile() throws Exception {
        Path pdf = temporary.getRoot().toPath().resolve("extra-catalog-key.pdf");
        new DocumentWorkflow().execute(WorkflowRequest.create(pdf, SaveMode.REWRITE), session -> {
            session.execute(AddBlankPage.INSTANCE);
            session.execute(DocumentPatch.builder().setDictionaryEntry(
                    session.query(DocumentRootReference.INSTANCE), PdfName.of("PageMode"),
                    PdfName.of("UseNone")).build());
            return null;
        });
        assertEquals(EvidenceResult.FAIL,
                T03BlankSemantics.inspect(pdf, WorkflowExecutionProfile.IN_PROCESS));
    }
}
