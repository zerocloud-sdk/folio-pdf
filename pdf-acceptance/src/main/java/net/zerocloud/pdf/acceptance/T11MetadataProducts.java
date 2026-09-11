package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.EmbeddedFile;
import net.zerocloud.pdf.OutlineItem;
import net.zerocloud.pdf.PageDestination;
import net.zerocloud.pdf.PageRange;
import net.zerocloud.pdf.PdfOutputPolicy;
import net.zerocloud.pdf.PdfString;
import net.zerocloud.pdf.PdfVersion;
import net.zerocloud.pdf.PublicationReceipt;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.CopyPages;
import net.zerocloud.pdf.command.EmbedFile;
import net.zerocloud.pdf.command.MergeDocuments;
import net.zerocloud.pdf.command.MovePages;
import net.zerocloud.pdf.command.RemovePages;
import net.zerocloud.pdf.command.ReplaceOutlineTree;
import net.zerocloud.pdf.command.SetNamedDestinations;
import net.zerocloud.pdf.command.SetXmpMetadata;
import net.zerocloud.pdf.command.SplitDocument;
import net.zerocloud.pdf.command.UpdateDocumentInfo;
import net.zerocloud.pdf.query.PageCount;

/** Executes the frozen operations; expectations are never derived from these outputs. */
final class T11MetadataProducts {
    static final String CAPABILITY = "document.metadata.outlines-destinations-attachments";

    private T11MetadataProducts() {
    }

    static void create(T11Corpus corpus, Path output, WorkflowExecutionProfile execution) throws Exception {
        for (String api : new String[] {"native", "facade"}) {
            for (String product : new String[] {"edited", "merged", "left", "right"}) {
                Files.createDirectory(output.resolve(api + "-" + product));
            }
        }
        WorkflowOutcome<DocumentFailure> edited = new DocumentWorkflow().execute(request(execution)
                .source("primary", DocumentSource.path(corpus.source("primary"))).primarySource("primary")
                .target("edited", PublicationTarget.path(pdf(output, "native", "edited"))).build(), session -> {
            session.execute(UpdateDocumentInfo.version1().set("Title", text("Changed title"))
                    .remove("Author").set("Custom", text("Edited info")).build());
            session.execute(SetXmpMetadata.version1(editedXmp()));
            session.execute(SetNamedDestinations.version1().set("xyz", editedDestination())
                    .set("temporary", PageDestination.fit(1)).build());
            session.execute(SetNamedDestinations.version1().remove("temporary").build());
            session.execute(ReplaceOutlineTree.version1(editedOutlines()));
            session.execute(EmbedFile.version1(editedAttachment()));
            session.execute(MovePages.version1(PageRange.of(3, 3), 1));
            session.execute(CopyPages.version1(PageRange.of(2, 2), 4));
            try {
                session.execute(RemovePages.version1(PageRange.of(3, 3)));
                throw new IllegalStateException("T11 accepted orphaning page removal");
            } catch (DocumentFailure failure) {
                requireOrphan(failure);
                if (session.query(PageCount.INSTANCE) != 4) {
                    throw new IllegalStateException("T11 rejected removal changed page count");
                }
                return failure;
            }
        });
        requireExecution(edited, execution);
        publication(output, "native", "edited", edited.getExecutionProfile(), "workflow-outcome",
                edited.getResult(), null, edited.getPublicationReceipts(), "edited");

        WorkflowOutcome<DocumentFailureCode> split = new DocumentWorkflow().execute(request(execution)
                .source("appendix", DocumentSource.path(corpus.source("appendix")))
                .source("primary", DocumentSource.path(corpus.source("primary"))).primarySource("primary")
                .target("left", PublicationTarget.path(pdf(output, "native", "left")))
                .target("merged", PublicationTarget.path(pdf(output, "native", "merged")))
                .target("right", PublicationTarget.path(pdf(output, "native", "right"))).build(), session -> {
            session.execute(MergeDocuments.version1("appendix"));
            session.execute(SplitDocument.version1().target("right", PageRange.of(3, 4))
                    .target("merged", PageRange.of(1, 4)).target("left", PageRange.of(1, 2)).build());
            try {
                session.execute(SetXmpMetadata.version1(editedXmp()));
                throw new IllegalStateException("T11 split accepted a later Command");
            } catch (DocumentFailure failure) {
                if (failure.getCode() != DocumentFailureCode.COMMAND_REJECTED
                        || session.query(PageCount.INSTANCE) != 4) {
                    throw failure;
                }
                return failure.getCode();
            }
        });
        requireExecution(split, execution);
        publication(output, "native", "split", split.getExecutionProfile(), "workflow-outcome", null,
                split.getResult(), split.getPublicationReceipts(), "left", "merged", "right");
        T11FacadeProducts.create(corpus, output);
    }

    private static WorkflowRequest.Builder request(WorkflowExecutionProfile execution) {
        return WorkflowRequest.builder().executionProfile(execution).saveMode(SaveMode.REWRITE)
                .outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_2_0));
    }

    private static PdfString text(String value) {
        return PdfString.of(value.getBytes(StandardCharsets.US_ASCII));
    }

    static byte[] editedXmp() {
        return ("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n"
                + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n"
                + "<rdf:Description rdf:about=\"\" xmlns:u=\"urn:folio:t73:unknown\" u:opaque=\"keep\">"
                + "<u:value>edited</u:value></rdf:Description>\n</rdf:RDF>\n</x:xmpmeta>\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    static PageDestination editedDestination() {
        return PageDestination.xyz(3, BigDecimal.TEN, null, BigDecimal.valueOf(2));
    }

    static List<OutlineItem> editedOutlines() {
        List<OutlineItem> none = Collections.emptyList();
        return Arrays.asList(OutlineItem.grouping("Edited sections", Arrays.asList(
                OutlineItem.toNamedDestination("Named B", "shared", none),
                OutlineItem.toPage("Rectangle A", PageDestination.fitR(1, BigDecimal.ONE,
                        BigDecimal.valueOf(2), BigDecimal.valueOf(80), BigDecimal.valueOf(90)), none))),
                OutlineItem.toPage("Explicit C", editedDestination(), none));
    }

    static EmbeddedFile editedAttachment() {
        return EmbeddedFile.version1("payload.txt", new byte[] {97, 98, 99, 0}, "application/octet-stream",
                "Edited payload", EmbeddedFile.Relationship.SOURCE);
    }

    static void requireOrphan(DocumentFailure failure) throws DocumentFailure {
        if (failure.getCode() != DocumentFailureCode.DESTINATION_CONFLICT
                || !CAPABILITY.equals(failure.getCapabilityId())) {
            throw failure;
        }
    }

    private static void requireExecution(WorkflowOutcome<?> outcome, WorkflowExecutionProfile requested) throws IOException {
        if (outcome.getExecutionProfile() != requested) {
            throw new IOException("T11 Native execution identity mismatch");
        }
    }

    static Path pdf(Path output, String api, String product) {
        return output.resolve(api + "-" + product + "/metadata.pdf");
    }

    static void publication(Path output, String api, String group, WorkflowExecutionProfile execution,
            String executionSource, DocumentFailure orphan, DocumentFailureCode terminal,
            List<PublicationReceipt> receipts, String... targets) throws IOException {
        if (receipts.size() != targets.length) {
            throw new IOException("T11 publication receipt count mismatch");
        }
        Properties values = new Properties();
        values.setProperty("execution-profile", execution.name());
        values.setProperty("execution-profile-source", executionSource);
        values.setProperty("receipt-count", Integer.toString(receipts.size()));
        if (orphan != null) {
            values.setProperty("orphan-rejection-code", orphan.getCode().name());
            values.setProperty("orphan-rejection-capability", orphan.getCapabilityId());
            values.setProperty("orphan-rejection-diagnostic", orphan.getDiagnostic());
        }
        if (terminal != null) {
            values.setProperty("terminal-command-code", terminal.name());
        }
        for (int index = 0; index < receipts.size(); index++) {
            PublicationReceipt receipt = receipts.get(index);
            if (!targets[index].equals(receipt.getTargetName()) || receipt.getStatus() != PublicationStatus.COMMITTED
                    || !receipt.getPathTarget().isPresent()
                    || !pdf(output, api, targets[index]).equals(receipt.getPathTarget().get())
                    || receipt.isPartialOutputPossible()) {
                throw new IOException("T11 publication receipt identity or status mismatch");
            }
            String prefix = "receipt." + index + ".";
            values.setProperty(prefix + "target", receipt.getTargetName());
            values.setProperty(prefix + "status", receipt.getStatus().name());
            values.setProperty(prefix + "path", receipt.getPathTarget().get().toString());
            values.setProperty(prefix + "partial-output-possible", Boolean.toString(receipt.isPartialOutputPossible()));
            values.setProperty(prefix + "pdf-sha256", EvidenceFiles.sha256(receipt.getPathTarget().get()));
        }
        save(output.resolve(api + "-" + group + "-publication.properties"), values);
    }

    static void save(Path path, Properties values) throws IOException {
        try (OutputStream output = Files.newOutputStream(path)) {
            values.store(output, "Actual T11 observation");
        }
    }
}
