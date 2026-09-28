package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import net.zerocloud.pdf.WorkflowExecutionProfile;

/** Repository-only entry point for image and Resource Inventory evidence. */
public final class T14EvidenceCommand {
    private static final String PIN_SHA256 = "a7304b08ff1b86d9742c0462fcc8a30856cda545c269b0038f6a9fe5cd4c26c8";
    private static final String PYTHON_SHA256 = "1643dacd9feaedc58f3cc581e4d22577dfe25c09b10282936186ccf0f2e61118";
    private T14EvidenceCommand() { }

    /**
     * Creates public products and independently certifies their extraction.
     * @param arguments {@code <repository> <fresh-output> <execution-profile> <release>}
     *     or {@code products <repository> <fresh-output> <execution-profile>}
     * @throws Exception if any expected value, lifecycle or publication check fails
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 4) {
            throw new IllegalArgumentException("Usage: T14EvidenceCommand <repository> <fresh-output> <execution-profile> <release>");
        }
        boolean productsOnly = "products".equals(arguments[0]);
        int offset = productsOnly ? 1 : 0;
        Path root = Paths.get(arguments[offset]).toAbsolutePath().normalize();
        WorkflowExecutionProfile execution = WorkflowExecutionProfile.valueOf(arguments[offset + 2]);
        if (!productsOnly && !"0.1.0".equals(arguments[3])) {
            throw new IllegalArgumentException("T14 certifies the Foundation 0.1.0 candidate");
        }
        Path python = Paths.get("/usr/bin/python3.12");
        if (!productsOnly) { verifyProducer(root, python); }
        T14Corpus corpus = new T14Corpus(root);
        Path output = Files.createDirectory(Paths.get(arguments[offset + 1]).toAbsolutePath().normalize());
        RetainedEvidence retained = T14ExtractionProducts.create(corpus, output, execution);
        if (!productsOnly) {
            ProcessResult process = ExternalProcess.run(python, root, 300000, 16 * 1024 * 1024,
                    "-I", root.resolve("scripts/t14-certification.py").toString(),
                    root.toString(), output.toString(), execution.name());
            System.out.print(process.standardOutput);
            System.err.print(process.standardError);
            if (process.exitCode != 0 || !process.standardError.isEmpty()
                    || !process.standardOutput.trim().equals(
                            "T14 independent syntax, standards, semantic and visual observations passed")) {
                throw new IOException("T14 independent certification failed; retained " + output);
            }
            verifyProducer(root, python);
        }
        corpus.verifySources();
        retained.verify();
    }

    private static void verifyProducer(Path root, Path python) throws IOException {
        Path pin = root.resolve("scripts/t14-evidence-pin.properties");
        if (!PIN_SHA256.equals(EvidenceFiles.sha256(pin))
                || !PYTHON_SHA256.equals(EvidenceFiles.sha256(python))) {
            throw new IOException("T14 independent producer identity mismatch");
        }
        Properties identities = new Properties();
        try (InputStream input = Files.newInputStream(pin)) { identities.load(input); }
        for (String name : new String[] {"scripts/t14-certification.py", "scripts/t14-observer.py"}) {
            if (!EvidenceFiles.sha256(root.resolve(name)).equals(identities.getProperty(name))) {
                throw new IOException("T14 independent observer identity mismatch");
            }
        }
    }
}
