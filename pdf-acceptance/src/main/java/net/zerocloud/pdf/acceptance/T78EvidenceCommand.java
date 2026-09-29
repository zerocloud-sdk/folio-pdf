package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import net.zerocloud.pdf.WorkflowExecutionProfile;

/** Repository-only certification entry point for the baseline password profile. */
public final class T78EvidenceCommand {
    private static final String PIN_SHA256 = "8a9d6255a312f44d28aa91c6614fa2218948e16e42e8059173f9d30dd3b0b17c";
    private static final String PYTHON_SHA256 = "1643dacd9feaedc58f3cc581e4d22577dfe25c09b10282936186ccf0f2e61118";
    private T78EvidenceCommand() { }

    /**
     * Produces Native and Facade products and runs the independent observers.
     * @param arguments {@code <repository> <fresh-output> <execution-profile> <release>},
     *     or {@code products <repository> <fresh-output> <execution-profile>}
     * @throws Exception when a required observation is missing or nonpassing
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 4) {
            throw new IllegalArgumentException("Usage: T78EvidenceCommand <repository> <fresh-output> <execution-profile> <release>");
        }
        boolean productsOnly = "products".equals(arguments[0]);
        int offset = productsOnly ? 1 : 0;
        Path root = Paths.get(arguments[offset]).toAbsolutePath().normalize();
        WorkflowExecutionProfile execution = WorkflowExecutionProfile.valueOf(arguments[offset + 2]);
        if (!productsOnly && !"0.1.0".equals(arguments[3])) {
            throw new IllegalArgumentException("T78 certifies the Foundation 0.1.0 candidate");
        }
        Path python = Paths.get("/usr/bin/python3.12");
        if (!productsOnly) { verifyProducer(root, python); }
        Path output = Files.createDirectory(Paths.get(arguments[offset + 1]).toAbsolutePath().normalize());
        RetainedEvidence retained = T78PasswordProducts.create(root, output, execution);
        if (!productsOnly) {
            ProcessResult process = ExternalProcess.runCoordinator(python, root, 900000, 16 * 1024 * 1024,
                    "-I", root.resolve("scripts/t78-certification.py").toString(), root.toString(), output.toString(), execution.name());
            System.out.print(process.standardOutput);
            System.err.print(process.standardError);
            if (process.exitCode != 0 || !process.standardError.isEmpty() || !process.standardOutput.trim().equals(
                    "T78 independent syntax, standards, semantic and visual observations passed")) {
                throw new IOException("T78 independent certification failed.");
            }
            verifyProducer(root, python);
        }
        retained.verify();
    }

    private static void verifyProducer(Path root, Path python) throws IOException {
        Path pin = root.resolve("scripts/t78-evidence-pin.properties");
        if (!PIN_SHA256.equals(EvidenceFiles.sha256(pin)) || !PYTHON_SHA256.equals(EvidenceFiles.sha256(python))) {
            throw new IOException("T78 independent producer identity mismatch");
        }
        Properties identities = new Properties();
        try (InputStream input = Files.newInputStream(pin)) { identities.load(input); }
        for (String name : new String[] {"scripts/t78-certification.py", "scripts/t78-observer.py"}) {
            if (!EvidenceFiles.sha256(root.resolve(name)).equals(identities.getProperty(name))) {
                throw new IOException("T78 independent observer identity mismatch");
            }
        }
    }
}
