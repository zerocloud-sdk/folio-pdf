package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;
import net.zerocloud.pdf.WorkflowExecutionProfile;

/** Repository-only Worker recorder using the qualified independent blank-PDF profile. */
public final class T21EvidenceCommand {
    private T21EvidenceCommand() { }

    /**
     * Records actual public Worker products, actual IN_PROCESS Facade products and qualified controls.
     * @param arguments repository root, fresh output, HARDENED_WORKER, release 0.1.0
     * @throws Exception when any required observation or independent producer fails
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 4 || !"HARDENED_WORKER".equals(arguments[2]) || !"0.1.0".equals(arguments[3])) {
            throw new IllegalArgumentException("Usage: T21EvidenceCommand <repository> <fresh-output> HARDENED_WORKER 0.1.0");
        }
        Path root = Paths.get(arguments[0]).toAbsolutePath().normalize();
        Path output = Files.createDirectory(Paths.get(arguments[1]).toAbsolutePath().normalize());
        Properties before = identities(root);
        RetainedEvidence retained = T21WorkflowContracts.record(output);
        retained.write(output.resolve("identities-before.properties"), before);
        retained.include(T20FacadeContracts.record(output));
        T03EvidenceCommand.main(new String[] {root.toString(), output.resolve("pdf-outcomes").toString(), "HARDENED_WORKER", "0.1.0"});
        VisualProfile visual = VisualProfile.load(root.resolve("capabilities/profiles/T03-document-blank-visual.properties"));
        for (String product : Arrays.asList("native-success", "native-owned-stream", "native-before-failure", "facade-stream", "facade-before-failure")) {
            WorkflowExecutionProfile actual = product.startsWith("facade-") ? WorkflowExecutionProfile.IN_PROCESS : WorkflowExecutionProfile.HARDENED_WORKER;
            Properties evidence = T03EvidenceCommand.record(root, output.resolve(product), actual, visual, "0.1.0");
            for (String chain : Arrays.asList("syntax", "standards", "semantic", "visual")) {
                if (!"pass".equals(evidence.getProperty(chain))) { throw new IOException("T21 published outcome did not pass " + chain); }
            }
        }
        Properties after = identities(root);
        if (!before.equals(after)) { throw new IOException("T21 acceptance inputs changed during observation"); }
        retained.write(output.resolve("identities-after.properties"), after);
        try (java.util.stream.Stream<Path> paths = Files.walk(output)) {
            for (Path path : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) { retained.retain(path, EvidenceFiles.sha256(path)); }
        }
        String manifest = retained.publishManifest("pass");
        Properties result = new Properties();
        result.setProperty("profile", "T21-hardened-worker");
        result.setProperty("native-execution-profile", "HARDENED_WORKER");
        result.setProperty("facade-execution-profile", "IN_PROCESS");
        for (String chain : Arrays.asList("syntax", "standards", "semantic", "visual", "contract")) { result.setProperty(chain, "pass"); }
        result.setProperty("retained-files-sha256", manifest);
        retained.write(output.resolve("result.properties"), result);
        retained.verify();
        System.out.println("T21 fixed Worker contracts and independent PDF observations passed");
    }

    private static Properties identities(Path root) throws IOException {
        Properties pin = new Properties();
        Path authority = root.resolve("scripts/t21-evidence-pin.properties");
        try (InputStream input = Files.newInputStream(authority)) { pin.load(input); }
        for (String name : pin.stringPropertyNames()) {
            Path path = root.resolve(name).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path) || !pin.getProperty(name).equals(EvidenceFiles.sha256(path))) {
                throw new IOException("T21 pinned input is missing or changed: " + name);
            }
        }
        pin.setProperty("scripts/t21-evidence-pin.properties", EvidenceFiles.sha256(authority));
        return pin;
    }
}
