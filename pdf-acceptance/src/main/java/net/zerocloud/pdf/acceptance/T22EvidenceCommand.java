package net.zerocloud.pdf.acceptance;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;
import net.zerocloud.pdf.WorkflowExecutionProfile;

/** Repository-only recorder; original public outcomes and actual scale artifacts stay separate. */
public final class T22EvidenceCommand {
    private T22EvidenceCommand() { }

    /** @param arguments repository, fresh output, HARDENED_WORKER, release 0.1.0
     * @throws Exception if a mandatory original observation or producer fails */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 4 || !"HARDENED_WORKER".equals(arguments[2]) || !"0.1.0".equals(arguments[3])) {
            throw new IllegalArgumentException("Usage: T22EvidenceCommand <repository> <fresh-output> HARDENED_WORKER 0.1.0");
        }
        Path root = Paths.get(arguments[0]).toAbsolutePath().normalize();
        Path output = Files.createDirectory(Paths.get(arguments[1]).toAbsolutePath().normalize());
        Properties before = identities(root);
        RetainedEvidence retained = T22RecoveryContracts.record(output);
        retained.write(output.resolve("identities-before.properties"), before);
        retained.include(T20FacadeContracts.record(output));
        T03EvidenceCommand.main(new String[] {root.toString(), output.resolve("pdf-outcomes").toString(), "HARDENED_WORKER", "0.1.0"});
        VisualProfile visual = VisualProfile.load(root.resolve("capabilities/profiles/T03-document-blank-visual.properties"));
        for (String product : Arrays.asList("native-acknowledgement", "native-acknowledgement-second", "native-stream", "native-before-failure", "native-retry",
                "facade-stream", "facade-before-failure")) {
            Properties result = T03EvidenceCommand.record(root, output.resolve(product), product.startsWith("facade-")
                    ? WorkflowExecutionProfile.IN_PROCESS : WorkflowExecutionProfile.HARDENED_WORKER, visual, "0.1.0");
            for (String chain : Arrays.asList("syntax", "standards", "semantic", "visual")) { check("pass".equals(result.getProperty(chain)), "published recovery outcome " + chain); }
        }
        Path scale = Files.createDirectory(output.resolve("scale"));
        Path original = output.getParent().resolve("scale-observations");
        for (String profile : Arrays.asList("pages", "input", "concurrency")) {
            Path source = original.resolve(profile); check(Files.isDirectory(source), "missing actual scale profile " + profile);
            Path destination = Files.createDirectory(scale.resolve(profile));
            try (java.util.stream.Stream<Path> files = Files.list(source)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    check(Files.isRegularFile(file) && !Files.isSymbolicLink(file), "unexpected scale artifact");
                    String hash = EvidenceFiles.sha256(file);
                    Path copy = Files.copy(file, destination.resolve(file.getFileName()));
                    check(hash.equals(EvidenceFiles.sha256(copy)) && hash.equals(EvidenceFiles.sha256(file)), "scale input changed while copying");
                    retained.retain(copy, hash);
                }
            }
        }
        T22ScaleDocuments.record(root, scale);
        Properties after = identities(root); check(before.equals(after), "pinned acceptance inputs changed");
        retained.write(output.resolve("identities-after.properties"), after);
        try (java.util.stream.Stream<Path> files = Files.walk(output)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) { retained.retain(file, EvidenceFiles.sha256(file)); }
        }
        String manifest = retained.publishManifest("pass");
        Properties result = new Properties(); result.setProperty("profile", "T22-worker-recovery-scale");
        result.setProperty("native-execution-profile", "HARDENED_WORKER"); result.setProperty("facade-execution-profile", "IN_PROCESS");
        for (String chain : Arrays.asList("syntax", "standards", "semantic", "visual", "contract")) { result.setProperty(chain, "pass"); }
        result.setProperty("retained-files-sha256", manifest); retained.write(output.resolve("result.properties"), result); retained.verify();
        System.out.println("T22 recovery and actual scale contracts and independent PDF observations passed");
    }

    private static Properties identities(Path root) throws Exception {
        Path authority = root.resolve("scripts/t22-evidence-pin.properties"); Properties pin = new Properties();
        try (InputStream source = Files.newInputStream(authority)) { pin.load(source); }
        for (String name : pin.stringPropertyNames()) {
            Path path = root.resolve(name).normalize();
            check(path.startsWith(root) && Files.isRegularFile(path) && pin.getProperty(name).equals(EvidenceFiles.sha256(path)), "missing/changed pinned input " + name);
        }
        pin.setProperty("scripts/t22-evidence-pin.properties", EvidenceFiles.sha256(authority)); return pin;
    }
    private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
