package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PdfBoolean;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNull;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfStreamEncoding;
import net.zerocloud.pdf.PdfString;
import net.zerocloud.pdf.PdfValuePath;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.InspectObject;

/** Repository-only recorder for T09 values, changes and preservation. */
public final class T09EvidenceCommand {
    static final String PROFILE = "T09-document-value-inspection-patch";
    private static final String SOURCE_SHA256 = "c94455cd71eaebf2e2f9f6c32f320bc04087f1e1477c58b596a48a2f106dbca6";

    private T09EvidenceCommand() {
    }

    /**
     * Records real products and observations in a new directory.
     * @param arguments repository root, fresh output, Native execution profile, release
     * @throws Exception if a required observation fails
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 4) {
            throw new IllegalArgumentException("Usage: T09EvidenceCommand <repository> <fresh-output> <execution-profile> <release>");
        }
        if ("semantic".equals(arguments[0])) {
            Path pdf = Paths.get(arguments[1]).toAbsolutePath().normalize();
            Path output = Files.createDirectory(Paths.get(arguments[2]).toAbsolutePath().normalize());
            EvidenceResult observed = T09ValuesSemantics.inspect(pdf, WorkflowExecutionProfile.valueOf(arguments[3]));
            Properties result = new Properties();
            result.setProperty("semantic", observed.recordValue());
            save(output.resolve("result.properties"), result);
            EvidenceFiles.write(output.resolve("semantic.txt"), semanticFinding(observed));
            return;
        }
        Path root = Paths.get(arguments[0]).toAbsolutePath().normalize();
        Path source = root.resolve("capabilities/profiles/T09-values/fixtures/values.pdf");
        if (!SOURCE_SHA256.equals(EvidenceFiles.sha256(source))) {
            throw new IOException("T09 Source identity mismatch");
        }
        Path output = Files.createDirectory(Paths.get(arguments[1]).toAbsolutePath().normalize());
        WorkflowExecutionProfile execution = WorkflowExecutionProfile.valueOf(arguments[2]);
        Properties result = new Properties();
        result.setProperty("source-sha256", SOURCE_SHA256);
        VisualProfile visual = T09IndependentEvidence.visualProfile(root);
        Path original = Files.createDirectory(output.resolve("source"));
        Path originalPdf = Files.copy(source, original.resolve("values.pdf"));
        Properties originalResult = new Properties();
        originalResult.setProperty("input-sha256", SOURCE_SHA256);
        originalResult.setProperty("visual", T09IndependentEvidence.recordVisual(root, originalPdf, original, visual, arguments[3]));
        save(original.resolve("result.properties"), originalResult);
        for (String chain : new String[] {"syntax", "standards", "semantic", "visual"}) {
            result.setProperty(chain, "visual".equals(chain) ? originalResult.getProperty(chain) : "pass");
        }
        for (String product : new String[] {"native-rewrite", "native-incremental", "facade"}) {
            Path directory = Files.createDirectory(output.resolve(product));
            Path pdf = directory.resolve("values.pdf");
            WorkflowExecutionProfile actual = "facade".equals(product) ? WorkflowExecutionProfile.IN_PROCESS : execution;
            if ("facade".equals(product)) {
                T09FacadeProduct.create(source, pdf);
            } else {
                createNative(source, pdf, execution, product.endsWith("incremental") ? SaveMode.INCREMENTAL : SaveMode.REWRITE);
            }
            Properties observed = T09IndependentEvidence.record(root, directory, visual, arguments[3]);
            observed.setProperty("input-sha256", EvidenceFiles.sha256(pdf));
            observed.setProperty("execution-profile", actual.name());
            EvidenceResult observation = T09ValuesSemantics.inspect(pdf, actual);
            observed.setProperty("semantic", observation.recordValue());
            for (String chain : new String[] {"syntax", "standards", "semantic", "visual"}) {
                result.setProperty(chain, EvidenceResult.combine(
                        result.getProperty(chain), observed.getProperty(chain)));
            }
            EvidenceFiles.write(directory.resolve("semantic.txt"), "Public Native reopen; execution=" + actual
                    + "\ninput-sha256=" + observed.getProperty("input-sha256") + "\n" + semanticFinding(observation)
                    + "\nAssertions: nine changed value kinds; ordered container edits; stable indirect aliases; exact byte strings;"
                    + " decoded replaced and untouched streams; private entries; page count/box/resources and original painting.\n");
            save(directory.resolve("result.properties"), observed);
        }
        Path negative = Files.createDirectory(output.resolve("negative"));
        Path unchanged = Files.copy(source, negative.resolve("unchanged-values.pdf"));
        Properties controls = new Properties();
        EvidenceResult semanticControl = T09ValuesSemantics.inspect(unchanged, execution);
        controls.setProperty("semantic", semanticControl.recordValue());
        EvidenceFiles.write(negative.resolve("semantic.txt"), "Known unchanged Source must fail the changed-value expectations.\ninput-sha256="
                + EvidenceFiles.sha256(unchanged) + "\n" + semanticFinding(semanticControl));
        T09IndependentEvidence.negativeControls(root, negative, output.resolve("native-rewrite/values.pdf"),
                visual, arguments[3], "pass".equals(result.getProperty("standards")), controls);
        save(negative.resolve("result.properties"), controls);
        for (String chain : new String[] {"syntax", "standards", "semantic", "visual"}) {
            if (!"fail".equals(controls.getProperty(chain))) {
                result.setProperty(chain, "indeterminate");
            }
        }
        if (!SOURCE_SHA256.equals(EvidenceFiles.sha256(source))) {
            throw new IOException("T09 Source changed during observation");
        }
        save(output.resolve("result.properties"), result);
        for (String chain : new String[] {"syntax", "standards", "semantic", "visual"}) {
            if (!"pass".equals(result.getProperty(chain))) {
                throw new IOException("T09 " + chain + " observation did not pass; retained " + output);
            }
        }
    }

    private static void createNative(Path source, Path output, WorkflowExecutionProfile execution, SaveMode mode) throws Exception {
        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .source("input", DocumentSource.path(source)).primarySource("input")
                .target("output", PublicationTarget.path(output)).executionProfile(execution).saveMode(mode).build(), session -> {
                    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                    PdfDictionary catalog = (PdfDictionary) session.query(InspectObject.version1(root, PdfInspectionLimits.of(1000, 0)));
                    PdfDictionary application = (PdfDictionary) ((PdfDictionary) catalog.get(PdfName.of("PieceInfo"))).get(PdfName.of("FolioPDF"));
                    ObjectReference valuesReference = ((PdfIndirectReference) application.get(PdfName.of("Private"))).getReference();
                    PdfDictionary values = (PdfDictionary) session.query(InspectObject.version1(valuesReference, PdfInspectionLimits.of(1000, 0)));
                    ObjectReference stream = ((PdfIndirectReference) values.get(PdfName.of("Stream"))).getReference();
                    ObjectReference scalar = ((PdfIndirectReference) values.get(PdfName.of("Reference"))).getReference();
                    PdfValuePath base = PdfValuePath.root(valuesReference);
                    PdfValuePath array = base.dictionaryEntry(PdfName.of("Array"));
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(valuesReference, PdfName.of("Null"), PdfBoolean.of(false))
                            .setDictionaryEntry(valuesReference, PdfName.of("Null"), PdfNull.INSTANCE)
                            .setDictionaryEntry(valuesReference, PdfName.of("Flag"), PdfBoolean.of(true))
                            .setDictionaryEntry(valuesReference, PdfName.of("Number"), PdfNumber.of(new BigDecimal("7.25")))
                            .setDictionaryEntry(valuesReference, PdfName.of("String"), PdfString.of(new byte[] {0, 40, 41, 92, (byte) 255}))
                            .setDictionaryEntry(valuesReference, PdfName.of("Name"), PdfName.of("After /# Ω"))
                            .removeArrayElement(array, 0)
                            .insertArrayElement(array, 0, PdfName.of("Inserted"))
                            .setArrayElement(array, 2, PdfNumber.of(new BigDecimal("7.25")))
                            .replaceValue(array.arrayElement(1), PdfBoolean.of(true))
                            .setDictionaryEntry(array.arrayElement(5), PdfName.of("Nested"), PdfNumber.of(2))
                            .setDictionaryEntry(base.dictionaryEntry(PdfName.of("Dictionary")), PdfName.of("Value"), PdfNumber.of(2))
                            .removeDictionaryEntry(base.dictionaryEntry(PdfName.of("Dictionary")), PdfName.of("Obsolete"))
                            .setDictionaryEntry(base.dictionaryEntry(PdfName.of("Dictionary")), PdfName.of("Added"), PdfString.of("kept".getBytes(StandardCharsets.US_ASCII)))
                            .replaceValue(PdfValuePath.root(scalar), PdfNumber.of(43))
                            .setDictionaryEntry(valuesReference, PdfName.of("Reference"), values.get(PdfName.of("RetainedStream")))
                            .replaceStreamData(PdfValuePath.root(stream), new byte[] {8, 9, 0, (byte) 255}, PdfStreamEncoding.FLATE)
                            .build());
                    return null;
                });
        if (outcome.getExecutionProfile() != execution || outcome.getPublicationReceipts().size() != 1
                || outcome.getPublicationReceipts().get(0).getStatus() != PublicationStatus.COMMITTED
                || !outcome.getPublicationReceipts().get(0).getPathTarget().get().equals(output)) {
            throw new IOException("Unexpected T09 Native publication receipt");
        }
        if (mode == SaveMode.INCREMENTAL) {
            byte[] original = Files.readAllBytes(source);
            if (!Arrays.equals(original, Arrays.copyOf(Files.readAllBytes(output), original.length))) {
                throw new IOException("Incremental publication lost its Source prefix");
            }
        }
    }

    private static void save(Path path, Properties values) throws IOException {
        try (OutputStream output = Files.newOutputStream(path)) {
            values.store(output, "Actual T09 observation");
        }
    }

    private static String semanticFinding(EvidenceResult result) {
        return "result=" + result.recordValue() + "\nReason: "
                + (result == EvidenceResult.INDETERMINATE ? "The public Native workflow did not complete."
                    : result == EvidenceResult.FAIL ? "The reopened values differ from the frozen changed-value expectations."
                    : "The reopened values satisfy the frozen changed-value expectations.") + "\n";
    }
}
