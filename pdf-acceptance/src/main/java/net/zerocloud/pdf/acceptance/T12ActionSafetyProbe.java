package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import net.zerocloud.pdf.Annotation;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.GoToAction;
import net.zerocloud.pdf.NavigationTarget;
import net.zerocloud.pdf.PageDestination;
import net.zerocloud.pdf.PageRange;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfDictionaryEntry;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfString;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.PdfVersion;
import net.zerocloud.pdf.PublicationReceipt;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.CopyPages;
import net.zerocloud.pdf.command.UpdateActions;
import net.zerocloud.pdf.command.UpdateAnnotations;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;
import net.zerocloud.pdf.query.Actions;
import net.zerocloud.pdf.query.Annotations;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageCount;

/** Public-API Action scenarios, run under the separate Linux canary observer. */
public final class T12ActionSafetyProbe {
    private static final byte[] SENTINEL = new byte[] {70, 79, 76, 73, 79};

    private T12ActionSafetyProbe() { }

    /** Runs the probe with the observer-provided canary environment. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 3) { throw new IllegalArgumentException("Usage: T12ActionSafetyProbe <repository> <output> <execution-profile>"); }
        Path root = Paths.get(arguments[0]);
        Path output = Paths.get(arguments[1]);
        WorkflowExecutionProfile execution = WorkflowExecutionProfile.valueOf(arguments[2]);
        T12Corpus corpus = new T12Corpus(root);
        Properties result = new Properties();
        result.setProperty("profile", T12Corpus.PROFILE);
        result.setProperty("native-execution-profile", execution.name());
        result.setProperty("facade-execution-profile", WorkflowExecutionProfile.IN_PROCESS.name());
        for (String api : new String[] {"native", "facade"}) {
            WorkflowExecutionProfile actual = "native".equals(api) ? execution : WorkflowExecutionProfile.IN_PROCESS;
            for (String graph : new String[] {"unknown", "chained"}) {
                String prefix = api + "." + graph + ".";
                PdfDictionary action = action("chained".equals(graph));
                Path source = output.resolve(api + "-" + graph + "-source.pdf");
                WorkflowOutcome<Void> authored = new DocumentWorkflow().execute(request(corpus.source("primary"), source, actual), session -> {
                    session.execute(DocumentPatch.builder().setDictionaryEntry(session.query(DocumentRootReference.INSTANCE),
                            PdfName.of("OpenAction"), action).build());
                    session.execute(UpdateAnnotations.version1().put(note(false)).build());
                    return null;
                });
                require(authored.getExecutionProfile() == actual, "Action fixture execution profile");
                receipt(authored.getPublicationReceipts(), source, result, prefix + "source.");
                String sourceHash = EvidenceFiles.sha256(source);
                Path preserved = output.resolve(api + "-" + graph + "-preserved.pdf");
                Path replaced = output.resolve(api + "-" + graph + "-replaced.pdf");
                if ("native".equals(api)) {
                    nativeScenario(source, preserved, replaced, action, actual, result, prefix);
                } else {
                    facadeScenario(source, preserved, replaced, action, result, prefix);
                }
                require(sourceHash.equals(EvidenceFiles.sha256(source)), "Action source unchanged");
                result.setProperty(prefix + "source-unchanged", "pass");
            }
        }
        signed(output, execution, result);
        corpus.verifySources();
        result.setProperty("result", "pass");
        T12AnnotationProducts.save(output.resolve("result.properties"), result);
    }

    private static void nativeScenario(Path source, Path preserved, Path replaced, PdfDictionary action,
            WorkflowExecutionProfile execution, Properties result, String prefix) throws Exception {
        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(request(source, preserved, execution), session -> {
            try {
                session.query(Actions.version1(20));
                throw new IllegalStateException("Unknown/chained Action query succeeded");
            } catch (DocumentFailure failure) {
                rejection(result, prefix + "query-", failure, DocumentFailureCode.QUERY_FAILED);
            }
            try {
                session.execute(CopyPages.version1(PageRange.of(1, 2), 4));
                throw new IllegalStateException("Unknown/chained Action rewrite succeeded");
            } catch (DocumentFailure failure) {
                rejection(result, prefix + "copy-", failure, DocumentFailureCode.PRESERVATION_UNSUPPORTED);
            }
            require(session.query(PageCount.INSTANCE).intValue() == 3
                    && session.query(Annotations.version1(1, 4096, 0)).equals(Collections.singletonList(note(false))), "Atomic Action rejection");
            require(matches(session, action, catalogAction(session), 0), "In-session inert Action structure");
            session.execute(UpdateAnnotations.version1().put(note(true)).build());
            return null;
        });
        require(outcome.getExecutionProfile() == execution, "Native Action execution profile");
        result.setProperty(prefix + "execution-profile", outcome.getExecutionProfile().name());
        receipt(outcome.getPublicationReceipts(), preserved, result, prefix + "preserved.");
        preserved(preserved, action, execution);
        result.setProperty(prefix + "atomic", "pass");
        result.setProperty(prefix + "preserved", "pass");
        WorkflowOutcome<Void> replacement = new DocumentWorkflow().execute(request(preserved, replaced, execution), session -> {
            session.execute(UpdateActions.version1().setDocumentOpenAction(replacement()).build());
            return null;
        });
        require(replacement.getExecutionProfile() == execution, "Native replacement execution profile");
        receipt(replacement.getPublicationReceipts(), replaced, result, prefix + "replaced.");
        replaced(replaced, execution);
        result.setProperty(prefix + "replaced", "pass");
    }

    private static void facadeScenario(Path source, Path preserved, Path replaced, PdfDictionary action,
            Properties result, String prefix) throws Exception {
        PdfDocument document = facade(source, preserved);
        try (PdfDocument owned = document) {
            try {
                owned.getActions(20);
                throw new IllegalStateException("Facade unknown/chained Action query succeeded");
            } catch (PdfException failure) {
                rejection(result, prefix + "query-", T11FacadeProducts.nativeFailure(failure), DocumentFailureCode.QUERY_FAILED);
            }
            try {
                owned.copyPages(1, 2, 4);
                throw new IllegalStateException("Facade unknown/chained Action rewrite succeeded");
            } catch (PdfException failure) {
                rejection(result, prefix + "copy-", T11FacadeProducts.nativeFailure(failure), DocumentFailureCode.PRESERVATION_UNSUPPORTED);
            }
            require(owned.getNumberOfPages() == 3
                    && owned.getAnnotations(1, 4096, 0).equals(Collections.singletonList(note(false))), "Facade atomic Action rejection");
            owned.getPage(1).addAnnotation(note(true));
        }
        receipt(document.getPublicationReceipts(), preserved, result, prefix + "preserved.");
        preserved(preserved, action, WorkflowExecutionProfile.IN_PROCESS);
        result.setProperty(prefix + "execution-profile", WorkflowExecutionProfile.IN_PROCESS.name());
        result.setProperty(prefix + "atomic", "pass");
        result.setProperty(prefix + "preserved", "pass");
        PdfDocument replacement = facade(preserved, replaced);
        try (PdfDocument owned = replacement) { owned.getCatalog().setOpenAction(replacement()); }
        receipt(replacement.getPublicationReceipts(), replaced, result, prefix + "replaced.");
        replaced(replaced, WorkflowExecutionProfile.IN_PROCESS);
        result.setProperty(prefix + "replaced", "pass");
    }

    private static void preserved(Path path, PdfDictionary action, WorkflowExecutionProfile execution) throws DocumentFailure {
        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(request(path, null, execution), session -> {
            require(matches(session, action, catalogAction(session), 0), "Reopened inert Action structure");
            require(session.query(PageCount.INSTANCE).intValue() == 3
                    && session.query(Annotations.version1(1, 4096, 0)).equals(Collections.singletonList(note(true))), "Reopened retained Annotation");
            return null;
        });
        require(outcome.getExecutionProfile() == execution, "Preservation observation execution");
    }

    private static void replaced(Path path, WorkflowExecutionProfile execution) throws DocumentFailure {
        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(request(path, null, execution), session -> {
            require(session.query(Actions.version1(1)).getDocumentOpenAction().get().equals(replacement()), "Explicit inert replacement");
            return null;
        });
        require(outcome.getExecutionProfile() == execution, "Replacement observation execution");
    }

    private static void signed(Path output, WorkflowExecutionProfile execution, Properties result) throws Exception {
        Path source = output.resolve("signed-docmdp-p3.pdf");
        Files.write(source, T11SignedProtectionEvidence.signedFixture());
        String hash = EvidenceFiles.sha256(source);
        for (String api : new String[] {"native", "facade"}) {
            Path target = output.resolve(api + "-signed-target.pdf");
            Files.write(target, SENTINEL);
            DocumentFailure rejected = null;
            if ("native".equals(api)) {
                try {
                    new DocumentWorkflow().execute(request(source, target, execution), session -> {
                        session.execute(UpdateAnnotations.version1().put(note(false)).build());
                        return null;
                    });
                } catch (DocumentFailure failure) { rejected = failure; }
            } else {
                try (PdfDocument document = facade(source, target)) {
                    document.getPage(1).addAnnotation(note(false));
                } catch (PdfException failure) { rejected = T11FacadeProducts.nativeFailure(failure); }
            }
            require(rejected != null, "Signed Source rewrite must fail");
            rejection(result, api + ".signed-", rejected, DocumentFailureCode.SIGNED_REWRITE_REJECTED);
            require(Arrays.equals(SENTINEL, Files.readAllBytes(target)) && hash.equals(EvidenceFiles.sha256(source)), "Signed bytes unchanged");
            result.setProperty(api + ".signed-target-unchanged", "pass");
        }
        result.setProperty("signed-source-sha256", hash);
    }

    private static WorkflowRequest request(Path source, Path target, WorkflowExecutionProfile execution) {
        WorkflowRequest.Builder request = T12AnnotationProducts.request(execution).source("input", DocumentSource.path(source)).primarySource("input");
        if (target != null) { request.target("result", PublicationTarget.path(target)); }
        return request.build();
    }

    private static PdfDocument facade(Path source, Path target) throws IOException {
        return new PdfDocument(Collections.singletonMap("input", new PdfReader(source.toString())), "input",
                Collections.singletonMap("result", new PdfWriter(target.toString())), PdfVersion.PDF_2_0);
    }

    private static PdfDictionary action(boolean chained) {
        String read = environment("READ_CANARY");
        String write = environment("WRITE_CANARY");
        String script = environment("SCRIPT_CANARY");
        String url = "http://127.0.0.1:" + environment("CANARY_PORT") + "/inert-action";
        String javascript = "app.openDoc('" + quote(read) + "'); this.saveAs('" + quote(write)
                + "'); this.saveAs('" + quote(script) + "'); app.launchURL('" + url + "');";
        PdfDictionary js = PdfDictionary.builder().put(PdfName.of("S"), PdfName.of("JavaScript"))
                .put(PdfName.of("JS"), string(javascript)).build();
        PdfDictionary uri = PdfDictionary.builder().put(PdfName.of("S"), PdfName.of("URI")).put(PdfName.of("URI"), string(url)).build();
        PdfDictionary launch = PdfDictionary.builder().put(PdfName.of("S"), PdfName.of("Launch"))
                .put(PdfName.of("F"), string(environment("LAUNCH_CANARY"))).build();
        PdfDictionary remote = PdfDictionary.builder().put(PdfName.of("S"), PdfName.of("GoToR"))
                .put(PdfName.of("F"), string(read)).put(PdfName.of("D"), PdfArray.of(PdfNumber.of(0L), PdfName.of("Fit"))).build();
        PdfDictionary.Builder action = PdfDictionary.builder().put(PdfName.of("S"), PdfName.of(chained ? "GoTo" : "JavaScript"));
        action.put(PdfName.of(chained ? "D" : "JS"), string(chained ? "shared" : javascript));
        return action.put(PdfName.of("Next"), PdfArray.of(uri, launch, remote, js)).build();
    }

    private static String environment(String name) {
        String value = System.getenv("FOLIO_T12_" + name);
        if (value == null || value.isEmpty()) { throw new IllegalStateException("The qualified canary observer is required"); }
        return value;
    }

    private static String quote(String value) { return value.replace("\\", "\\\\").replace("'", "\\'"); }
    private static PdfString string(String text) { return PdfString.of(text.getBytes(StandardCharsets.US_ASCII)); }
    private static Annotation note(boolean changed) { return T12AnnotationProducts.annotations(changed).get(0); }
    private static GoToAction replacement() { return GoToAction.version1(NavigationTarget.toPage(PageDestination.fit(1))); }

    private static PdfValue catalogAction(DocumentSession session) throws DocumentFailure {
        PdfDictionary catalog = (PdfDictionary) session.query(InspectObject.version1(session.query(DocumentRootReference.INSTANCE),
                PdfInspectionLimits.of(256, 65536)));
        return catalog.get(PdfName.of("OpenAction"));
    }

    private static boolean matches(DocumentSession session, PdfValue expected, PdfValue actual, int depth) throws DocumentFailure {
        require(depth < 16, "Action inspection depth");
        if (actual instanceof PdfIndirectReference) {
            actual = session.query(InspectObject.version1(((PdfIndirectReference) actual).getReference(), PdfInspectionLimits.of(256, 65536)));
        }
        if (expected instanceof PdfDictionary && actual instanceof PdfDictionary) {
            PdfDictionary left = (PdfDictionary) expected;
            PdfDictionary right = (PdfDictionary) actual;
            if (left.size() != right.size()) { return false; }
            for (int index = 0; index < left.size(); index++) {
                PdfDictionaryEntry entry = left.getEntry(index);
                if (!matches(session, entry.getValue(), right.get(entry.getName()), depth + 1)) { return false; }
            }
            return true;
        }
        if (expected instanceof PdfArray && actual instanceof PdfArray) {
            PdfArray left = (PdfArray) expected;
            PdfArray right = (PdfArray) actual;
            if (left.size() != right.size()) { return false; }
            for (int index = 0; index < left.size(); index++) {
                if (!matches(session, left.get(index), right.get(index), depth + 1)) { return false; }
            }
            return true;
        }
        return expected.equals(actual);
    }

    private static void receipt(List<PublicationReceipt> receipts, Path target, Properties result, String prefix) throws IOException {
        require(receipts.size() == 1, "One safety publication receipt");
        PublicationReceipt receipt = receipts.get(0);
        require(receipt.getStatus() == PublicationStatus.COMMITTED && !receipt.isPartialOutputPossible()
                && target.equals(receipt.getPathTarget().orElse(null)), "Committed safety publication");
        result.setProperty(prefix + "receipt-status", receipt.getStatus().name());
        result.setProperty(prefix + "partial-output-possible", Boolean.toString(receipt.isPartialOutputPossible()));
        result.setProperty(prefix + "path", target.toString());
        result.setProperty(prefix + "sha256", EvidenceFiles.sha256(target));
    }

    private static void rejection(Properties result, String prefix, DocumentFailure failure, DocumentFailureCode expected) {
        require(failure.getCode() == expected, "Stable safe error: " + expected + " but got " + failure.getCode() + ": " + failure.getDiagnostic());
        result.setProperty(prefix + "code", failure.getCode().name());
        result.setProperty(prefix + "capability", failure.getCapabilityId());
        result.setProperty(prefix + "diagnostic", failure.getDiagnostic());
    }

    private static void require(boolean condition, String finding) {
        if (!condition) { throw new IllegalStateException(finding); }
    }
}
