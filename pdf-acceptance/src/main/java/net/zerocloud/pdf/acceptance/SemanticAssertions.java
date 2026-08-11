package net.zerocloud.pdf.acceptance;

import java.nio.file.Path;
import java.util.List;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PublicationReceipt;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.PageCount;

/** Project-owned semantic assertions for the built-in blank-document profile. */
public final class SemanticAssertions {

    private SemanticAssertions() {
    }

    /**
     * Reopens a published artifact through the public workflow and records its
     * observable object-graph and page-sequence semantics.
     *
     * @param creation the workflow outcome that published the artifact
     * @param artifact the exact artifact inspected by the syntax chain
     * @return the detached semantic observation
     */
    public static SemanticObservation inspect(
            WorkflowOutcome<Void> creation,
            Path artifact) {
        PublicationStatus publicationStatus = publicationStatus(creation);
        try {
            WorkflowOutcome<Integer> inspection = new DocumentWorkflow().execute(
                    WorkflowRequest.open(artifact, SaveMode.REWRITE),
                    session -> session.query(PageCount.INSTANCE));
            return SemanticObservation.reopened(
                    publicationStatus,
                    inspection.getResult().intValue());
        } catch (DocumentFailure failure) {
            return SemanticObservation.reopenFailed(
                    publicationStatus,
                    failure.getCode());
        }
    }

    private static PublicationStatus publicationStatus(WorkflowOutcome<Void> outcome) {
        List<PublicationReceipt> receipts = outcome.getPublicationReceipts();
        if (receipts.size() != 1) {
            return PublicationStatus.FAILED;
        }
        return receipts.get(0).getStatus();
    }
}
