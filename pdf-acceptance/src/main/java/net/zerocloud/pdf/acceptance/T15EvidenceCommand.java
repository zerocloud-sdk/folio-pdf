package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import net.zerocloud.pdf.WorkflowExecutionProfile;

/** Repository-only certification entry point for incremental publication. */
public final class T15EvidenceCommand {
    private static final String PIN_SHA256 = "6043a95d14af6819e2e8736ddd09ce3843ba9abb1066a4efb208bca40dca133a";
    private static final String PYTHON_SHA256 = "1643dacd9feaedc58f3cc581e4d22577dfe25c09b10282936186ccf0f2e61118";
    private T15EvidenceCommand() { }

    /**
     * Produces actual Native and Facade revisions and runs independent observers.
     * @param arguments {@code <repository> <fresh-output> <execution-profile> <release>},
     *     or {@code products <repository> <fresh-output> <execution-profile>}
     * @throws Exception when any required observation is unavailable or nonpassing
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 4) {
            throw new IllegalArgumentException("Usage: T15EvidenceCommand <repository> <fresh-output> <execution-profile> <release>");
        }
        boolean productsOnly = "products".equals(arguments[0]);
        int offset = productsOnly ? 1 : 0;
        Path root = Paths.get(arguments[offset]).toAbsolutePath().normalize();
        WorkflowExecutionProfile execution = WorkflowExecutionProfile.valueOf(arguments[offset + 2]);
        if (!productsOnly && !"0.1.0".equals(arguments[3])) {
            throw new IllegalArgumentException("T15 certifies the Foundation 0.1.0 candidate");
        }
        Path python = Paths.get("/usr/bin/python3.12");
        if (!productsOnly) { verifyProducer(root, python); }
        Path output = Files.createDirectory(Paths.get(arguments[offset + 1]).toAbsolutePath().normalize());
        RetainedEvidence retained = T15IncrementalProducts.create(root, output, execution);
        if (!productsOnly) {
            ProcessResult process = ExternalProcess.run(python, root, 300000, 16 * 1024 * 1024,
                    "-I", root.resolve("scripts/t15-certification.py").toString(), root.toString(), output.toString(), execution.name());
            System.out.print(process.standardOutput);
            System.err.print(process.standardError);
            if (process.exitCode != 0 || !process.standardError.isEmpty() || !process.standardOutput.trim().equals(
                    "T15 independent syntax, standards, semantic and visual observations passed")) {
                throw new IOException("T15 independent certification failed; retained " + output);
            }
            verifyProducer(root, python);
        }
        retained.verify();
    }

    private static void verifyProducer(Path root, Path python) throws IOException {
        Path pin = root.resolve("scripts/t15-evidence-pin.properties");
        if (!PIN_SHA256.equals(EvidenceFiles.sha256(pin)) || !PYTHON_SHA256.equals(EvidenceFiles.sha256(python))) {
            throw new IOException("T15 independent producer identity mismatch");
        }
        Properties identities = new Properties();
        try (InputStream input = Files.newInputStream(pin)) { identities.load(input); }
        for (String name : new String[] {"scripts/t15-certification.py", "scripts/t15-observer.py"}) {
            if (!EvidenceFiles.sha256(root.resolve(name)).equals(identities.getProperty(name))) {
                throw new IOException("T15 independent observer identity mismatch");
            }
        }
    }
}
