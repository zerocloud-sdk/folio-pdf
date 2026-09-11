package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import net.zerocloud.pdf.Annotation;
import net.zerocloud.pdf.AnnotationAppearance;
import net.zerocloud.pdf.AnnotationColor;
import net.zerocloud.pdf.AnnotationFlag;
import net.zerocloud.pdf.AnnotationProperties;
import net.zerocloud.pdf.AnnotationQuad;
import net.zerocloud.pdf.AnnotationRectangle;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.EmbeddedFile;
import net.zerocloud.pdf.GoToAction;
import net.zerocloud.pdf.LinkActivation;
import net.zerocloud.pdf.NavigationTarget;
import net.zerocloud.pdf.PageDestination;
import net.zerocloud.pdf.PageRange;
import net.zerocloud.pdf.PdfOutputPolicy;
import net.zerocloud.pdf.PdfVersion;
import net.zerocloud.pdf.PublicationReceipt;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.CopyPages;
import net.zerocloud.pdf.command.FlattenAnnotations;
import net.zerocloud.pdf.command.MergeDocuments;
import net.zerocloud.pdf.command.SplitDocument;
import net.zerocloud.pdf.command.UpdateActions;
import net.zerocloud.pdf.command.UpdateAnnotations;

/** Performs the declared public operations without deriving expectations from their output. */
final class T12AnnotationProducts {
    static final String CAPABILITY = "document.annotations-actions.manage";
    static final String[] FLATTEN = {"note", "stamp", "highlight", "file", "direct", "named", "action", "action-named"};

    private T12AnnotationProducts() { }

    static void create(T12Corpus corpus, Path output, WorkflowExecutionProfile execution) throws Exception {
        for (String api : new String[] {"native", "facade"}) {
            for (String product : T12Corpus.PRODUCTS) { Files.createDirectory(output.resolve(api + "-" + product)); }
        }
        for (String product : T12Corpus.PRODUCTS) {
            Path input = "created".equals(product) ? corpus.source("primary")
                    : "changed".equals(product) ? pdf(output, "native", "created")
                    : "left".equals(product) || "right".equals(product) ? pdf(output, "native", "merged")
                    : pdf(output, "native", "changed");
            WorkflowOutcome<Void> outcome;
            try {
                outcome = new DocumentWorkflow().execute(request(execution)
                    .source("primary", DocumentSource.path(input)).primarySource("primary")
                    .source("appendix", DocumentSource.path(corpus.source("appendix")))
                    .target(product, PublicationTarget.path(pdf(output, "native", product))).build(), session -> {
                if ("created".equals(product) || "changed".equals(product)) {
                    boolean changed = "changed".equals(product);
                    UpdateAnnotations.Builder update = UpdateAnnotations.version1();
                    for (Annotation annotation : annotations(changed)) { update.put(annotation); }
                    session.execute(update.build());
                    session.execute(actions(changed));
                } else if ("flattened".equals(product)) {
                    session.execute(FlattenAnnotations.version1(FLATTEN[0], Arrays.copyOfRange(FLATTEN, 1, FLATTEN.length)));
                } else if ("copied".equals(product)) {
                    session.execute(CopyPages.version1(PageRange.of(1, 2), 4));
                } else if ("merged".equals(product) || "adopted".equals(product)) {
                    if ("adopted".equals(product)) { session.execute(UpdateActions.version1().removeDocumentOpenAction().build()); }
                    session.execute(MergeDocuments.version1("appendix"));
                } else {
                    session.execute(SplitDocument.version1().target(product,
                            "left".equals(product) ? PageRange.of(1, 2) : PageRange.of(3, 4)).build());
                }
                return null;
                });
            } catch (DocumentFailure failure) {
                throw new IOException("T12 Native " + product + ": " + failure.getCode() + ": " + failure.getDiagnostic(), failure);
            }
            if (outcome.getExecutionProfile() != execution) { throw new IOException("T12 Native execution identity mismatch"); }
            publication(output, "native", product, outcome.getExecutionProfile(), "workflow-outcome", outcome.getPublicationReceipts());
        }
        T12FacadeProducts.create(corpus, output);
    }

    static WorkflowRequest.Builder request(WorkflowExecutionProfile execution) {
        return WorkflowRequest.builder().executionProfile(execution).saveMode(SaveMode.REWRITE)
                .outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_2_0));
    }

    static List<Annotation> annotations(boolean changed) {
        int y = changed ? 45 : 65;
        List<Annotation> result = new ArrayList<Annotation>();
        result.add(Annotation.text(properties("note", 1, 5, y, 20, changed ? "0 1 1" : "0 0 1", changed),
                changed ? Annotation.TextIcon.HELP : Annotation.TextIcon.NOTE, changed));
        result.add(Annotation.stamp(properties("stamp", 1, 25, y, 45, changed ? "1 0 1" : "0 1 0", changed),
                changed ? "Draft" : "Approved"));
        result.add(Annotation.highlight(properties("highlight", 1, 50, y, 75, changed ? "0 0 1" : "1 1 0", changed),
                Arrays.asList(AnnotationQuad.of(50, y + 15, 75, y + 15, 50, y, 75, y)),
                AnnotationColor.rgb(BigDecimal.ONE, changed ? BigDecimal.ZERO : BigDecimal.ONE,
                        changed ? BigDecimal.ONE : BigDecimal.ZERO)));
        result.add(Annotation.fileAttachment(properties("file", 1, 80, y, 95, changed ? "1 1 0" : "1 0 1", changed),
                EmbeddedFile.version1("payload.txt", changed ? new byte[] {97, 98, 99, 0} : new byte[] {97, 98, 99},
                        changed ? "application/octet-stream" : "text/plain", changed ? "Changed payload" : "Original payload",
                        changed ? EmbeddedFile.Relationship.SOURCE : EmbeddedFile.Relationship.DATA),
                changed ? Annotation.FileAttachmentIcon.PUSHPIN : Annotation.FileAttachmentIcon.PAPERCLIP));
        result.add(Annotation.widget(properties("widget", 1, 100, y, 115, changed ? "0 1 0" : "0 1 1", changed)));
        result.add(Annotation.link(properties("direct", 2, 5, y, 30, "1 0 0", changed),
                LinkActivation.destination(NavigationTarget.toPage(rectangleTarget()))));
        result.add(Annotation.link(properties("named", 2, 35, y, 60, "0 1 0", changed),
                LinkActivation.destination(named().getTarget())));
        result.add(Annotation.link(properties("action", 2, 65, y, 90, "0 0 1", changed),
                LinkActivation.action(GoToAction.version1(NavigationTarget.toPage(PageDestination.xyz(2,
                        changed ? BigDecimal.TEN : null, BigDecimal.valueOf(changed ? 80 : 90), new BigDecimal("1.25")))))));
        result.add(Annotation.link(properties("action-named", 2, 95, y, 115, "0 0 0", changed), LinkActivation.action(named())));
        return result;
    }

    private static AnnotationProperties properties(String identifier, int page, int left, int y,
            int right, String color, boolean changed) {
        return AnnotationProperties.version1(identifier, page, AnnotationRectangle.of(left, y, right, y + 15))
                .contents(identifier + (changed ? " changed" : " original")).flag(AnnotationFlag.PRINT)
                .appearance(AnnotationAppearance.version1(AnnotationRectangle.of(2, 3, 12, 13),
                        ("q " + color + " rg 2 3 10 10 re f Q\n").getBytes(StandardCharsets.US_ASCII))).build();
    }

    static GoToAction named() { return GoToAction.version1(NavigationTarget.toNamedDestination("shared")); }

    static GoToAction pageOpen(boolean changed, int page) {
        PageDestination target = page == 1 ? (changed ? PageDestination.fitH(2, null) : PageDestination.fit(2))
                : changed ? PageDestination.xyz(1, null, BigDecimal.valueOf(90), BigDecimal.valueOf(2)) : rectangleTarget();
        return GoToAction.version1(NavigationTarget.toPage(target));
    }

    private static PageDestination rectangleTarget() {
        return PageDestination.fitR(1, BigDecimal.valueOf(5), BigDecimal.TEN, BigDecimal.valueOf(100), BigDecimal.valueOf(90));
    }

    private static UpdateActions actions(boolean changed) {
        return UpdateActions.version1().setDocumentOpenAction(named()).setPageOpenAction(1, pageOpen(changed, 1))
                .setPageCloseAction(2, named()).setPageOpenAction(3, pageOpen(changed, 3)).build();
    }

    static Path pdf(Path output, String api, String product) { return output.resolve(api + "-" + product + "/annotations.pdf"); }

    static void publication(Path output, String api, String product, WorkflowExecutionProfile execution,
            String executionSource, List<PublicationReceipt> receipts) throws IOException {
        if (receipts.size() != 1) { throw new IOException("T12 publication receipt count mismatch"); }
        PublicationReceipt receipt = receipts.get(0);
        if (!product.equals(receipt.getTargetName()) || receipt.getStatus() != PublicationStatus.COMMITTED
                || !receipt.getPathTarget().isPresent() || !pdf(output, api, product).equals(receipt.getPathTarget().get())
                || receipt.isPartialOutputPossible()) { throw new IOException("T12 publication receipt identity or status mismatch"); }
        Properties values = new Properties();
        values.setProperty("execution-profile", execution.name());
        values.setProperty("execution-profile-source", executionSource);
        values.setProperty("receipt-count", "1");
        values.setProperty("receipt.0.target", product);
        values.setProperty("receipt.0.status", receipt.getStatus().name());
        values.setProperty("receipt.0.path", receipt.getPathTarget().get().toString());
        values.setProperty("receipt.0.partial-output-possible", Boolean.toString(receipt.isPartialOutputPossible()));
        values.setProperty("receipt.0.pdf-sha256", EvidenceFiles.sha256(receipt.getPathTarget().get()));
        save(output.resolve(api + "-" + product + "-publication.properties"), values);
    }

    static void save(Path path, Properties values) throws IOException {
        try (OutputStream output = Files.newOutputStream(path)) { values.store(output, "Actual T12 observation"); }
    }
}
