package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;
import net.zerocloud.pdf.WorkflowExecutionProfile;

/** Repository-only T20 recorder; resource contracts and PDF evidence remain separate. */
public final class T20EvidenceCommand {
    private T20EvidenceCommand() { }

    /**
     * Executes fixed public resource experiments and independent blank-PDF observers.
     * All published successes in this resource profile are deliberately resource-free
     * one-page PDFs; their PDF rules and controls reuse the qualified T03 profile.
     * @param arguments repository root, fresh output, IN_PROCESS, release 0.1.0
     * @throws Exception if a mandatory experiment, tool or chain does not pass
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 4 && "contracts".equals(arguments[0]) && "IN_PROCESS".equals(arguments[3])) {
            Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
            Path output = Files.createDirectory(Paths.get(arguments[2]).toAbsolutePath().normalize());
            RetainedEvidence retained = T20ResourceContracts.record(root, output);
            retained.include(T20FacadeContracts.record(output));
            retained.verify();
            return;
        }
        if (arguments.length != 4 || !"IN_PROCESS".equals(arguments[2]) || !"0.1.0".equals(arguments[3])) {
            throw new IllegalArgumentException("Usage: T20EvidenceCommand <repository> <fresh-output> IN_PROCESS 0.1.0");
        }
        Path root = Paths.get(arguments[0]).toAbsolutePath().normalize();
        Path output = Files.createDirectory(Paths.get(arguments[1]).toAbsolutePath().normalize());
        Properties before = identities(root);
        RetainedEvidence retained = T20ResourceContracts.record(root, output);
        retained.write(output.resolve("identities-before.properties"), before);
        retained.include(T20FacadeContracts.record(output));
        T03EvidenceCommand.main(new String[] {root.toString(), output.resolve("pdf-outcomes").toString(), "IN_PROCESS", "0.1.0"});
        VisualProfile visual = VisualProfile.load(root.resolve("capabilities/profiles/T03-document-blank-visual.properties"));
        for (String product : Arrays.asList("native-first", "native-second", "native-stream-high-water",
                "native-before-failure", "facade-stream", "facade-before-failure")) {
            Properties evidence = T03EvidenceCommand.record(root, output.resolve(product), WorkflowExecutionProfile.IN_PROCESS, visual, "0.1.0");
            for (String chain : Arrays.asList("syntax", "standards", "semantic", "visual")) {
                if (!"pass".equals(evidence.getProperty(chain))) { throw new IOException("T20 published outcome did not pass " + chain); }
            }
        }
        Properties after = identities(root);
        if (!before.equals(after)) { throw new IOException("T20 acceptance inputs changed during observation"); }
        retained.write(output.resolve("identities-after.properties"), after);
        // Capture the original observations after each producer has verified its inputs.
        try (java.util.stream.Stream<Path> paths = Files.walk(output)) {
            for (Path path : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) {
                retained.retain(path, EvidenceFiles.sha256(path));
            }
        }
        String manifest = retained.publishManifest("pass");
        Properties result = new Properties();
        result.setProperty("profile", "T20-hostile-input-limits");
        result.setProperty("native-execution-profile", "IN_PROCESS");
        result.setProperty("facade-execution-profile", "IN_PROCESS");
        for (String chain : Arrays.asList("syntax", "standards", "semantic", "visual", "contract")) { result.setProperty(chain, "pass"); }
        result.setProperty("retained-files-sha256", manifest);
        retained.write(output.resolve("result.properties"), result);
        retained.verify();
        System.out.println("T20 fixed resource contracts and independent PDF observations passed");
    }

    private static Properties identities(Path root) throws IOException {
        Properties pin = new Properties();
        Path authority = root.resolve("scripts/t20-evidence-pin.properties");
        try (InputStream input = Files.newInputStream(authority)) { pin.load(input); }
        for (String name : pin.stringPropertyNames()) {
            Path path = root.resolve(name).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path) || !pin.getProperty(name).equals(EvidenceFiles.sha256(path))) {
                throw new IOException("T20 pinned input is missing or changed: " + name);
            }
        }
        pin.setProperty("scripts/t20-evidence-pin.properties", EvidenceFiles.sha256(authority));
        return pin;
    }
}
