package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PdfStreamEncoding;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.PdfValuePath;
import net.zerocloud.pdf.PageRange;
import net.zerocloud.pdf.PublicationReceipt;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.CopyPages;
import net.zerocloud.pdf.command.InsertBlankPage;
import net.zerocloud.pdf.command.MergeDocuments;
import net.zerocloud.pdf.command.MovePages;
import net.zerocloud.pdf.command.RemovePages;
import net.zerocloud.pdf.command.SplitDocument;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.PageObjectReference;

/** Produces original T10 operations through public Native and Facade interfaces. */
final class T10PageProducts {
    private T10PageProducts() {
    }

    static void create(T10Corpus corpus, Path output, WorkflowExecutionProfile execution) throws Exception {
        for (String api : new String[] {"native", "facade"}) {
            for (String product : new String[] {"edited", "merged", "left", "right"}) {
                Files.createDirectory(output.resolve(api + "-" + product));
            }
        }
        WorkflowOutcome<Void> edited = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .source("primary", DocumentSource.path(corpus.source("primary"))).primarySource("primary")
                .target("edited", PublicationTarget.path(pdf(output, "native", "edited")))
                .executionProfile(execution).saveMode(SaveMode.REWRITE).build(), session -> {
                    session.execute(InsertBlankPage.version1(2));
                    session.execute(MovePages.version1(PageRange.of(4, 4), 1));
                    session.execute(CopyPages.version1(PageRange.of(2, 2), 4));
                    session.execute(RemovePages.version1(PageRange.of(1, 1)));
                    ObjectReference pageReference = session.query(PageObjectReference.version1(3));
                    PdfDictionary page = (PdfDictionary) session.query(InspectObject.version1(
                            pageReference, PdfInspectionLimits.of(1000, 1 << 20)));
                    PdfValuePath contentPath = PdfValuePath.root(pageReference).dictionaryEntry(PdfName.of("Contents"));
                    PdfValue contents = page.get(PdfName.of("Contents"));
                    if (contents instanceof PdfIndirectReference) {
                        contents = session.query(InspectObject.version1(((PdfIndirectReference) contents).getReference(),
                                PdfInspectionLimits.of(1000, 1 << 20)));
                    }
                    if (contents instanceof PdfArray) {
                        contentPath = contentPath.arrayElement(0);
                        contents = ((PdfArray) contents).get(0);
                        if (contents instanceof PdfIndirectReference) {
                            contents = session.query(InspectObject.version1(((PdfIndirectReference) contents).getReference(),
                                    PdfInspectionLimits.of(1000, 1 << 20)));
                        }
                    }
                    byte[] changedContent;
                    try {
                        changedContent = corpus.recolorCopiedContent(((PdfStream) contents).readBytes());
                    } catch (IOException mismatch) {
                        throw new IllegalStateException(mismatch);
                    }
                    session.execute(DocumentPatch.builder().replaceStreamData(contentPath,
                            changedContent, PdfStreamEncoding.FLATE).build());
                    return null;
                });
        requireExecution(edited, execution);
        publication(output, "native", "edited", edited.getExecutionProfile(), "workflow-outcome", null,
                edited.getPublicationReceipts(), "edited");

        WorkflowOutcome<DocumentFailureCode> split = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .source("appendix", DocumentSource.path(corpus.source("appendix")))
                .source("primary", DocumentSource.path(corpus.source("primary")))
                .source("cover", DocumentSource.path(corpus.source("cover"))).primarySource("primary")
                .target("merged", PublicationTarget.path(pdf(output, "native", "merged")))
                .target("left", PublicationTarget.path(pdf(output, "native", "left")))
                .target("right", PublicationTarget.path(pdf(output, "native", "right")))
                .executionProfile(execution).saveMode(SaveMode.REWRITE).build(), session -> {
                    session.execute(MergeDocuments.version1("cover", "appendix"));
                    session.execute(SplitDocument.version1().target("right", PageRange.of(3, 5))
                            .target("merged", PageRange.of(1, 5)).target("left", PageRange.of(1, 3)).build());
                    return terminalCode(session);
                });
        requireExecution(split, execution);
        publication(output, "native", "split", split.getExecutionProfile(), "workflow-outcome", split.getResult(),
                split.getPublicationReceipts(), "merged", "left", "right");
        T10FacadeProducts.create(corpus, output);
    }

    private static DocumentFailureCode terminalCode(DocumentSession session) throws DocumentFailure {
        try {
            session.execute(RemovePages.version1(PageRange.of(1, 1)));
            throw new IllegalStateException("T10 split accepted a later Command");
        } catch (DocumentFailure failure) {
            if (failure.getCode() != DocumentFailureCode.COMMAND_REJECTED || session.query(PageCount.INSTANCE) != 5) {
                throw failure;
            }
            return failure.getCode();
        }
    }

    private static void requireExecution(WorkflowOutcome<?> outcome, WorkflowExecutionProfile requested) throws IOException {
        if (outcome.getExecutionProfile() != requested) {
            throw new IOException("T10 Native execution identity mismatch");
        }
    }

    static Path pdf(Path output, String api, String product) {
        return output.resolve(api + "-" + product + "/pages.pdf");
    }

    static void publication(Path output, String api, String group, WorkflowExecutionProfile execution,
            String executionSource, DocumentFailureCode terminalCode, List<PublicationReceipt> receipts, String... targets) throws IOException {
        if (receipts.size() != targets.length) {
            throw new IOException("T10 publication receipt count mismatch");
        }
        Properties values = new Properties();
        values.setProperty("execution-profile", execution.name());
        values.setProperty("execution-profile-source", executionSource);
        values.setProperty("receipt-count", Integer.toString(receipts.size()));
        if (terminalCode != null) {
            values.setProperty("terminal-command-code", terminalCode.name());
        }
        for (int index = 0; index < receipts.size(); index++) {
            PublicationReceipt receipt = receipts.get(index);
            if (!targets[index].equals(receipt.getTargetName()) || receipt.getStatus() != PublicationStatus.COMMITTED
                    || !receipt.getPathTarget().isPresent() || !pdf(output, api, targets[index]).equals(receipt.getPathTarget().get())
                    || receipt.isPartialOutputPossible()) {
                throw new IOException("T10 publication receipt identity or status mismatch");
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

    static void save(Path file, Properties values) throws IOException {
        try (OutputStream output = Files.newOutputStream(file)) {
            values.store(output, "Actual T10 observation");
        }
    }
}
