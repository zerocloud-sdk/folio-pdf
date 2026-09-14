package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import net.zerocloud.pdf.WorkflowExecutionProfile;

/** Keeps the independent evidence chains distinct while binding one exact product. */
final class T13CombinedEvidence {
    private T13CombinedEvidence() { }

    static Properties record(Path root, Path input, String product, Path output,
            WorkflowExecutionProfile execution, String release) throws Exception {
        T13Corpus corpus = new T13Corpus(root);
        corpus.requireProduct(product);
        Set<String> required = new TreeSet<String>();
        String prefix = "products." + product + ".required-chains.";
        for (int index = 0; index < Integer.parseInt(corpus.expected(prefix + "count")); index++) {
            required.add(corpus.expected(prefix + index));
        }
        String hash = EvidenceFiles.sha256(input);
        Path observed = output.resolve("extraction.pdf");
        if (!input.toAbsolutePath().normalize().equals(observed.toAbsolutePath().normalize())) {
            Files.copy(input, observed, StandardCopyOption.COPY_ATTRIBUTES);
        }
        RetainedEvidence retained = new RetainedEvidence(output);
        retained.retain(observed, hash);
        Properties result = new Properties();
        result.setProperty("profile", T13Corpus.PROFILE);
        result.setProperty("product", product);
        result.setProperty("execution-profile", execution.name());
        result.setProperty("input-sha256", hash);
        result.setProperty("required-chains", String.join(",", required));
        Path syntaxDirectory = Files.createDirectory(output.resolve("syntax"));
        Properties syntax = T13IndependentEvidence.recordSyntax(root, observed, syntaxDirectory, release);
        retainResult(retained, syntaxDirectory, syntax, hash);
        includeManifest(retained, syntaxDirectory, syntax.getProperty("retained-files-sha256"));
        result.setProperty("syntax", syntax.getProperty("syntax"));

        Path declarationDirectory = Files.createDirectory(output.resolve("declarations"));
        Properties declarations = T13DeclarationStandards.record(root, observed, declarationDirectory);
        retainResult(retained, declarationDirectory, declarations, hash);
        includeManifest(retained, declarationDirectory, declarations.getProperty("retained-files-sha256"));
        Path programDirectory = Files.createDirectory(output.resolve("programs"));
        Properties programs = T13ProgramStandards.record(root, observed, programDirectory);
        retainResult(retained, programDirectory, programs, hash);
        includeManifest(retained, programDirectory, programs.getProperty("retained-files-sha256"));
        String standards = EvidenceResult.combine(declarations.getProperty("declarations"), programs.getProperty("program-standards"));
        if ("pass".equals(standards) && (!"334".equals(declarations.getProperty("qualified-rule-count"))
                || !"42".equals(programs.getProperty("qualified-rule-count"))
                || !"165".equals(programs.getProperty("negative-control-count")))) {
            standards = "indeterminate";
        }
        result.setProperty("standards", standards);
        result.setProperty("qualified-standard-rule-count", "pass".equals(standards) ? "376" : "0");

        Path semanticDirectory = Files.createDirectory(output.resolve("semantic"));
        Properties semantic = T13ExtractionSemantics.inspect(root, observed, product, semanticDirectory, execution);
        retainResult(retained, semanticDirectory, semantic, hash);
        includeManifest(retained, semanticDirectory, semantic.getProperty("retained-files-sha256"));
        result.setProperty("semantic", semantic.getProperty("semantic"));
        if ("pass".equals(semantic.getProperty("semantic")) && !execution.name().equals(semantic.getProperty("execution-profile"))) {
            result.setProperty("semantic", "indeterminate");
        }
        if (required.contains("visual")) {
            Path visualDirectory = Files.createDirectory(output.resolve("visual"));
            Properties visual = T13IndependentEvidence.recordVisual(root, observed, product, visualDirectory, release);
            retainResult(retained, visualDirectory, visual, hash);
            includeManifest(retained, visualDirectory, visual.getProperty("retained-files-sha256"));
            result.setProperty("visual", visual.getProperty("visual"));
        } else {
            result.setProperty("visual", "not-required");
        }
        corpus.verifySources();
        if (!hash.equals(EvidenceFiles.sha256(input))) { throw new IOException("T13 input changed between evidence chains"); }
        retained.verify();
        String overall = "pass";
        for (String chain : required) { overall = EvidenceResult.combine(overall, result.getProperty(chain)); }
        result.setProperty("retained-files-sha256", retained.publishManifest(overall));
        return result;
    }

    private static void retainResult(RetainedEvidence retained, Path directory, Properties result, String inputHash) throws IOException {
        if (!T13Corpus.PROFILE.equals(result.getProperty("profile")) || !inputHash.equals(result.getProperty("input-sha256"))) {
            throw new IOException("T13 evidence chain does not identify the exact input");
        }
        retained.write(directory.resolve("result.properties"), result);
    }

    static void includeManifest(RetainedEvidence retained, Path directory, String expectedHash) throws IOException {
        Path manifest = directory.resolve("retained-files.sha256");
        if (!Files.isRegularFile(manifest, java.nio.file.LinkOption.NOFOLLOW_LINKS) || Files.size(manifest) > 1024 * 1024) {
            throw new IOException("T13 retained manifest is unavailable or exceeds its bound");
        }
        byte[] bytes = Files.readAllBytes(manifest);
        if (!EvidenceFiles.sha256(bytes).equals(expectedHash)) { throw new IOException("T13 retained manifest identity mismatch"); }
        retained.retain(manifest, expectedHash);
        for (String line : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
            if (line.length() < 67 || !line.substring(0, 64).matches("[0-9a-f]{64}") || !"  ".equals(line.substring(64, 66))) {
                throw new IOException("T13 retained manifest has an invalid entry");
            }
            Path relative = java.nio.file.Paths.get(line.substring(66));
            if (relative.isAbsolute() || !relative.normalize().equals(relative)) { throw new IOException("T13 retained manifest path is not relative"); }
            retained.retain(directory.resolve(relative), line.substring(0, 64));
        }
    }
}
