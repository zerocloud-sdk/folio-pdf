package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Collections;
import java.util.Properties;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PublicationReceipt;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.PdfOutputPolicy;
import net.zerocloud.pdf.PdfVersion;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.DocumentResourceInventory;
import net.zerocloud.pdf.ImageByteAccess;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;
import net.zerocloud.pdf.query.ExtractImagesAndResources;

/** Actual extraction, explicit rewrite and reopening through the public APIs. */
final class T14ExtractionProducts {
    private T14ExtractionProducts() { }

    static RetainedEvidence create(T14Corpus corpus, Path output, WorkflowExecutionProfile execution) throws Exception {
        RetainedEvidence retained = new RetainedEvidence(output);
        for (String product : T14Corpus.PRODUCTS) {
            Path source = corpus.source(product);
            ImageByteAccess access = corpus.byteAccess(product);
            String sourceHash = EvidenceFiles.sha256(source);
            for (String api : new String[] {"native", "facade"}) {
                Path directory = Files.createDirectory(output.resolve(api + "-" + product));
                Path pdf = directory.resolve("extraction.pdf");
                WorkflowExecutionProfile actual;
                DocumentResourceInventory result;
                List<PublicationReceipt> receipts;
                if ("native".equals(api)) {
                    WorkflowOutcome<DocumentResourceInventory> outcome = new DocumentWorkflow().execute(
                            request(source, execution).target("target", PublicationTarget.path(pdf)).build(),
                            session -> session.query(ExtractImagesAndResources.version1(T14Corpus.limits(), access)));
                    actual = outcome.getExecutionProfile();
                    if (actual != execution) { throw new IOException("T14 Native execution profile mismatch"); }
                    result = outcome.getResult();
                    receipts = outcome.getPublicationReceipts();
                } else {
                    PdfDocument produced;
                    try (PdfReader reader = new PdfReader(source.toString())) {
                        produced = new PdfDocument(Collections.singletonMap("source", reader), "source",
                                Collections.singletonMap("target", new PdfWriter(pdf.toString())), PdfVersion.PDF_2_0);
                        try (PdfDocument document = produced) { result = document.getImagesAndResources(T14Corpus.limits(), access); }
                    }
                    actual = WorkflowExecutionProfile.IN_PROCESS;
                    receipts = produced.getPublicationReceipts();
                }
                Properties observation = T14ExtractionObservation.observe(result);
                retained.write(directory.resolve("observation.properties"), observation);
                match(corpus.extraction(product), observation, product + " detached extraction");
                WorkflowOutcome<DocumentResourceInventory> reopened = new DocumentWorkflow().execute(
                        request(pdf, actual).build(),
                        session -> session.query(ExtractImagesAndResources.version1(T14Corpus.limits(), access)));
                if (reopened.getExecutionProfile() != actual || !reopened.getPublicationReceipts().isEmpty()) {
                    throw new IOException("T14 reopened Query execution or publication mismatch");
                }
                Properties reopenedValues = T14ExtractionObservation.observe(reopened.getResult());
                retained.write(directory.resolve("reopened.properties"), reopenedValues);
                match(observation, reopenedValues, product + " reopened extraction");
                if (!sourceHash.equals(EvidenceFiles.sha256(source))) { throw new IOException("T14 Source changed"); }
                publication(directory, pdf, sourceHash, actual, receipts, retained);
            }
        }
        corpus.verifySources();
        retained.verify();
        Properties phase = new Properties();
        phase.setProperty("phase", "products-only");
        phase.setProperty("native-execution-profile", execution.name());
        phase.setProperty("facade-execution-profile", "IN_PROCESS");
        retained.write(output.resolve("products.properties"), phase);
        return retained;
    }

    static WorkflowRequest.Builder request(Path source, WorkflowExecutionProfile execution) {
        return WorkflowRequest.builder().source("source", DocumentSource.path(source)).primarySource("source")
                .saveMode(SaveMode.REWRITE).outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_2_0))
                .executionProfile(execution);
    }

    private static void match(Properties expected, Properties actual, String description) throws IOException {
        if (!expected.equals(actual)) {
            java.util.TreeSet<String> keys = new java.util.TreeSet<String>(expected.stringPropertyNames());
            keys.addAll(actual.stringPropertyNames());
            for (String key : keys) {
                if (!java.util.Objects.equals(expected.getProperty(key), actual.getProperty(key))) {
                    throw new IOException("T14 " + description + " mismatch at " + key + ": expected "
                            + expected.getProperty(key) + ", observed " + actual.getProperty(key));
                }
            }
        }
    }

    private static void publication(Path directory, Path pdf, String sourceHash,
            WorkflowExecutionProfile execution, List<PublicationReceipt> receipts, RetainedEvidence retained) throws IOException {
        if (receipts.size() != 1) { throw new IOException("T14 publication receipt count mismatch"); }
        PublicationReceipt receipt = receipts.get(0);
        if (!"target".equals(receipt.getTargetName()) || receipt.getStatus() != PublicationStatus.COMMITTED
                || !receipt.getPathTarget().isPresent() || !pdf.equals(receipt.getPathTarget().get())
                || receipt.isPartialOutputPossible()) { throw new IOException("T14 publication disposition mismatch"); }
        Properties values = new Properties();
        values.setProperty("execution-profile", execution.name());
        values.setProperty("target-name", receipt.getTargetName());
        values.setProperty("status", receipt.getStatus().name());
        values.setProperty("partial-output-possible", Boolean.toString(receipt.isPartialOutputPossible()));
        values.setProperty("output-sha256", EvidenceFiles.sha256(pdf));
        retained.retain(pdf, values.getProperty("output-sha256"));
        values.setProperty("source-sha256", sourceHash);
        values.setProperty("source-preserved", "pass");
        values.setProperty("reopened", "pass");
        retained.write(directory.resolve("publication.properties"), values);
    }
}
