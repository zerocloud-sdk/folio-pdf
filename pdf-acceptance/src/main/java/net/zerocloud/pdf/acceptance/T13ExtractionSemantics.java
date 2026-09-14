package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PdfOutputPolicy;
import net.zerocloud.pdf.PdfVersion;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.TextStructureExtraction;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.ExtractTextAndStructure;

/** Independent decoded-graph preservation paired with complete public extraction values. */
final class T13ExtractionSemantics {
    private static final String OBSERVER_SHA256 = "8971a28d5f2f230c861243a2b56ec90b415bd5f9add6487b05f8940d19c42cbc";
    private static final String SCOPE = "independent-qpdf-decoded-graph-preservation";

    private T13ExtractionSemantics() { }

    static Properties inspect(Path root, Path input, String product, Path output, WorkflowExecutionProfile execution)
            throws IOException, InterruptedException {
        T13Corpus corpus = new T13Corpus(root);
        Properties expected = corpus.extraction(product);
        String exactHash = EvidenceFiles.sha256(input);
        Path observedInput = output.resolve("extraction.pdf");
        if (!input.toAbsolutePath().normalize().equals(observedInput.toAbsolutePath().normalize())) {
            Files.copy(input, observedInput, StandardCopyOption.COPY_ATTRIBUTES);
        }
        RetainedEvidence retained = new RetainedEvidence(output);
        retained.retain(observedInput, exactHash);
        Properties result = new Properties();
        result.setProperty("profile", T13Corpus.PROFILE);
        result.setProperty("input-sha256", exactHash);
        result.setProperty("semantic-scope", SCOPE);
        result.setProperty("semantic", "indeterminate");
        result.setProperty("execution-profile", "unavailable");
        result.setProperty("public-observation", "indeterminate");
        Path observer = root.resolve("scripts/t13-semantics.py");
        Path python = Paths.get("/usr/bin/python3.12");
        try {
            if (!OBSERVER_SHA256.equals(EvidenceFiles.sha256(observer))) {
                throw new IOException("Qualified T13 semantic observer identity mismatch");
            }
            ProcessResult command = ExternalProcess.run(python, output, 120000, 1 << 20,
                    "-I", observer.toString(), root.toString(), observedInput.toString(), product, output.resolve("qpdf").toString());
            retained.write(output.resolve("semantic-command.txt"), "observer: scripts/t13-semantics.py\n"
                    + "input exact SHA-256: " + exactHash + "\nexit-code: " + command.exitCode + "\n" + command.combinedOutput());
            if (command.exitCode != 0 || !command.standardError.trim().isEmpty()) {
                throw new IOException("Independent qpdf semantic command failed");
            }
            result.putAll(receive(output.resolve("qpdf"), command, retained));
            if (!T13Corpus.PROFILE.equals(result.getProperty("profile"))
                    || !exactHash.equals(result.getProperty("input-sha256"))
                    || !SCOPE.equals(result.getProperty("semantic-scope"))
                    || !OBSERVER_SHA256.equals(result.getProperty("observer-sha256"))
                    || !EvidenceFiles.sha256(python.toRealPath()).equals(result.getProperty("python-executable-sha256"))) {
                throw new IOException("Independent T13 semantic record identity mismatch");
            }
            if ("pass".equals(result.getProperty("semantic"))) {
                WorkflowOutcome<TextStructureExtraction> outcome = new DocumentWorkflow().execute(WorkflowRequest.builder()
                        .source("input", DocumentSource.path(observedInput)).primarySource("input").saveMode(SaveMode.REWRITE)
                        .outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_2_0)).executionProfile(execution).build(),
                        session -> session.query(ExtractTextAndStructure.version1(T13Corpus.limits())));
                result.setProperty("execution-profile", outcome.getExecutionProfile().name());
                if (outcome.getExecutionProfile() != execution || !outcome.getPublicationReceipts().isEmpty()) {
                    throw new IOException("T13 public Query execution or publication mismatch");
                }
                Properties observed = T13ExtractionObservation.observe(outcome.getResult());
                retained.write(output.resolve("observation.properties"), observed);
                boolean matched = expected.equals(observed);
                result.setProperty("public-observation", matched ? "pass" : "fail");
                if (!matched) {
                    result.setProperty("semantic", "fail");
                    result.setProperty("finding", "The complete public extraction differs from the authored expectations");
                }
            }
            corpus.verifySources();
            if (!OBSERVER_SHA256.equals(EvidenceFiles.sha256(observer))) {
                throw new IOException("Qualified T13 semantic observer changed during execution");
            }
        } catch (IOException unavailable) {
            result.setProperty("semantic", "indeterminate");
            result.setProperty("finding", unavailable.getMessage());
        } catch (DocumentFailure unavailable) {
            result.setProperty("semantic", "indeterminate");
            result.setProperty("finding", "The bounded public Query was unavailable: " + unavailable.getCode());
        }
        if (!exactHash.equals(EvidenceFiles.sha256(input))) {
            result.setProperty("semantic", "indeterminate");
            result.setProperty("finding", "Input changed during semantic observation");
        }
        retained.write(output.resolve("semantic.txt"), "Input exact SHA-256: " + exactHash + "\n"
                + result.getProperty("finding", "No qualified semantic finding was available") + "\n");
        result.setProperty("retained-files-sha256", retained.publishManifest(result.getProperty("semantic")));
        return result;
    }

    private static Properties receive(Path output, ProcessResult command, RetainedEvidence retained) throws IOException {
        java.util.Set<String> allowed = new java.util.HashSet<String>(java.util.Arrays.asList(
                "qpdf-version.txt", "qpdf-version-stderr.txt", "reference-qpdf.json", "reference-qpdf-stderr.txt",
                "qpdf.json", "qpdf-stderr.txt", "reference.json", "observed.json", "result.properties", "semantic.txt"));
        RetainedEvidence files = new RetainedEvidence(output);
        Properties receipt = new Properties();
        for (String line : command.standardOutput.split("\n")) {
            if (!line.matches("[0-9a-f]{64}  .+") || !allowed.contains(line.substring(66))) {
                throw new IOException("Malformed T13 semantic producer receipt");
            }
            String name = line.substring(66);
            if (receipt.setProperty(name, line.substring(0, 64)) != null) {
                throw new IOException("Duplicate T13 semantic producer artifact");
            }
            files.retain(output.resolve(name), line.substring(0, 64));
        }
        Path path = output.resolve("result.properties");
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 1024 * 1024) {
            throw new IOException("T13 semantic report is unavailable or exceeds its bound");
        }
        byte[] bytes = Files.readAllBytes(path);
        if (!EvidenceFiles.sha256(bytes).equals(receipt.getProperty("result.properties"))) {
            throw new IOException("T13 semantic report disagrees with its producer receipt");
        }
        Properties result = new Properties();
        result.load(new ByteArrayInputStream(bytes));
        if ("pass".equals(result.getProperty("semantic")) && (receipt.size() != allowed.size()
                || !receipt.getProperty("qpdf.json").equals(result.getProperty("qpdf-json-sha256"))
                || !receipt.getProperty("reference-qpdf.json").equals(result.getProperty("reference-qpdf-json-sha256")))) {
            throw new IOException("T13 semantic producer receipt is incomplete");
        }
        files.verify();
        retained.include(files);
        return result;
    }
}
