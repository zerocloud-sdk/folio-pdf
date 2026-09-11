package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.zerocloud.pdf.Annotation;
import net.zerocloud.pdf.DocumentActions;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.Actions;
import net.zerocloud.pdf.query.Annotations;
import net.zerocloud.pdf.query.NamedDestinations;

/** Independent qpdf semantics with a separate bounded public execution observation. */
final class T12AnnotationSemantics {
    private T12AnnotationSemantics() { }

    static Properties inspect(Path root, Path input, String product, Path output, WorkflowExecutionProfile execution)
            throws IOException, InterruptedException {
        T12Corpus corpus = new T12Corpus(root);
        Map<String, String> expected = corpus.product(product);
        String exactHash = EvidenceFiles.sha256(input);
        Properties result = new Properties();
        result.setProperty("profile", T12Corpus.PROFILE);
        result.setProperty("input-sha256", exactHash);
        result.setProperty("execution-profile", "unavailable");
        result.setProperty("public-observation", "indeterminate");
        result.setProperty("semantic-scope", "independent-qpdf-object-graph");
        Path python = Paths.get("/usr/bin/python3.12");
        try {
            ProcessResult command = ExternalProcess.run(python, output, 120000, 1 << 20,
                    root.resolve("scripts/t12-semantics.py").toString(), root.toString(), input.toString(), product,
                    output.resolve("qpdf").toString());
            EvidenceFiles.write(output.resolve("semantic-command.txt"),
                    "python: " + python + "\nobserver: scripts/t12-semantics.py\ninput exact SHA-256: " + exactHash
                            + "\nexit-code: " + command.exitCode + "\n" + command.combinedOutput());
            if (command.exitCode != 0) { throw new IOException("Independent qpdf semantic command failed"); }
            try (InputStream values = Files.newInputStream(output.resolve("qpdf/result.properties"))) { result.load(values); }
            if (!T12Corpus.PROFILE.equals(result.getProperty("profile"))
                    || !exactHash.equals(result.getProperty("input-sha256"))) {
                throw new IOException("Independent qpdf semantic identity mismatch");
            }
        } catch (IOException unavailable) {
            result.setProperty("semantic", "indeterminate");
            result.setProperty("finding", unavailable.getMessage());
        }
        if ("pass".equals(result.getProperty("semantic"))) {
            try {
                WorkflowOutcome<Boolean> observed = new DocumentWorkflow().execute(WorkflowRequest.builder()
                        .source("input", DocumentSource.path(input)).primarySource("input").saveMode(SaveMode.REWRITE)
                        .executionProfile(execution).build(), session -> {
                    List<Annotation> annotations = session.query(Annotations.version1(128, 1 << 20, 1 << 20));
                    if (annotations.size() != Integer.parseInt(expected.get("annotations.count"))) { return false; }
                    for (int index = 0; index < annotations.size(); index++) {
                        Annotation annotation = annotations.get(index);
                        String prefix = "annotations." + index + ".";
                        if (!annotation.getProperties().getIdentifier().equals(expected.get(prefix + "id"))
                                || !annotation.getType().name().equals(expected.get(prefix + "type"))
                                || annotation.getProperties().getPageNumber() != Integer.parseInt(expected.get(prefix + "page"))) {
                            return false;
                        }
                    }
                    DocumentActions actions = session.query(Actions.version1(128));
                    boolean hasOpen = expected.containsKey("actions.document-open.kind");
                    if (actions.getDocumentOpenAction().isPresent() != hasOpen
                            || actions.getPageActions().size() != Integer.parseInt(expected.get("actions.pages.count"))) { return false; }
                    int destinations = 0;
                    for (String key : expected.keySet()) {
                        if (key.startsWith("destinations.") && key.endsWith(".kind")) { destinations++; }
                    }
                    return session.query(NamedDestinations.version1(128)).size() == destinations;
                });
                result.setProperty("execution-profile", observed.getExecutionProfile().name());
                result.setProperty("public-observation", observed.getResult() ? "pass" : "fail");
                if (observed.getExecutionProfile() != execution) {
                    result.setProperty("semantic", "indeterminate");
                    result.setProperty("finding", "The public semantic observation used a different execution profile");
                } else if (!observed.getResult()) {
                    result.setProperty("semantic", "fail");
                    result.setProperty("finding", "The bounded public observation disagreed with the frozen corpus");
                }
            } catch (DocumentFailure failure) {
                result.setProperty("semantic", "indeterminate");
                result.setProperty("finding", "The bounded public observation was unavailable: " + failure.getCode());
            }
        }
        if (!exactHash.equals(EvidenceFiles.sha256(input))) {
            result.setProperty("semantic", "indeterminate");
            result.setProperty("finding", "Input changed during semantic observation");
        }
        EvidenceFiles.write(output.resolve("semantic.txt"), "Input exact SHA-256: " + exactHash + "\n"
                + result.getProperty("finding") + "\n");
        return result;
    }
}
