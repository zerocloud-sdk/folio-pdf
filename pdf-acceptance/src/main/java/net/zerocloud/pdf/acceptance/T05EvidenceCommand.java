package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;
import net.zerocloud.pdf.WorkflowExecutionProfile;

/** Five distinct Provider chains; actual PDF workload reuses qualified T03 predicates. */
public final class T05EvidenceCommand {
    private T05EvidenceCommand() { }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 4 || !"0.1.0".equals(arguments[3])) {
            throw new IllegalArgumentException("Usage: T05EvidenceCommand <repository> <fresh-output> <execution-profile> 0.1.0");
        }
        Path root = Paths.get(arguments[0]).toAbsolutePath().normalize();
        Path output = Files.createDirectory(Paths.get(arguments[1]).toAbsolutePath().normalize());
        WorkflowExecutionProfile execution = WorkflowExecutionProfile.valueOf(arguments[2]);
        Properties before = identities(root);
        RetainedEvidence retained = T05ProviderContracts.record(output, execution);
        retained.write(output.resolve("identities-before.properties"), before);
        recordPreview(root, output, retained);
        T03EvidenceCommand.main(new String[] {root.toString(), output.resolve("pdf-controls").toString(), execution.name(), "0.1.0"});
        VisualProfile visual = VisualProfile.load(root.resolve("capabilities/profiles/T03-document-blank-visual.properties"));
        for (String product : T05ProviderContracts.PRODUCTS) {
            WorkflowExecutionProfile actual = product.startsWith("native-") ? execution : WorkflowExecutionProfile.IN_PROCESS;
            Properties chains = T03EvidenceCommand.record(root, output.resolve(product), actual, visual, "0.1.0");
            for (String chain : Arrays.asList("syntax", "standards", "semantic", "visual")) {
                if (!"pass".equals(chains.getProperty(chain))) { throw new IOException("Provider workload PDF did not pass " + chain); }
            }
        }
        Properties after = identities(root);
        if (!before.equals(after)) { throw new IOException("Provider acceptance inputs changed during recording"); }
        retained.write(output.resolve("identities-after.properties"), after);
        try (java.util.stream.Stream<Path> paths = Files.walk(output)) {
            for (Path path : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) { retained.retain(path, EvidenceFiles.sha256(path)); }
        }
        String manifest = retained.publishManifest("pass");
        Properties result = new Properties(); result.setProperty("profile", "T05-capability-provider-contract");
        result.setProperty("native-execution-profile", execution.name()); result.setProperty("facade-execution-profile", "IN_PROCESS");
        for (String chain : Arrays.asList("syntax", "standards", "semantic", "visual", "contract")) { result.setProperty(chain, "pass"); }
        result.setProperty("retained-files-sha256", manifest); retained.write(output.resolve("result.properties"), result); retained.verify();
        System.out.println("T05 actual Provider boundaries and independent PDF observations passed");
    }

    private static void recordPreview(Path root, Path output, RetainedEvidence retained) throws Exception {
        Path products = root.resolve("target/foundation-0.1.0/artifacts");
        java.util.List<String> classpath = new java.util.ArrayList<String>();
        try (java.nio.file.DirectoryStream<Path> paths = Files.newDirectoryStream(products, "*.jar")) {
            for (Path path : paths) {
                if (!path.getFileName().toString().endsWith("-sources.jar") && !path.getFileName().toString().endsWith("-javadoc.jar")
                        && !path.getFileName().toString().equals("pdf-migration-itext7-0.1.0.jar")) { classpath.add(path.toString()); }
            }
        }
        classpath.add(Paths.get(T05EvidenceCommand.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        try (java.nio.file.DirectoryStream<Path> paths = Files.newDirectoryStream(root.resolve("target/foundation-0.1.0/harness/lib"), "*.jar")) {
            for (Path path : paths) { classpath.add(path.toString()); }
        }
        java.util.Collections.sort(classpath);
        String cp = String.join(java.io.File.pathSeparator, classpath);
        String[] arguments = {"-Xmx256m", "-Duser.language=en", "-Duser.country=US", "-Duser.timezone=UTC", "-cp", cp,
                T05PreviewFacadeCommand.class.getName(), output.resolve("preview").toString()};
        ProcessResult result = ExternalProcess.run(Paths.get(System.getProperty("java.home"), "bin", "java"), output, arguments);
        retained.write(output.resolve("preview-process.txt"), result.combinedOutput());
        if (result.exitCode != 0 || !result.combinedOutput().trim().equals("T05 Preview Facade public outcomes passed")) {
            throw new IOException("Actual isolated Preview Facade observation failed");
        }
        Properties actual = new Properties();
        actual.setProperty("classpath", cp); actual.setProperty("java", Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        actual.setProperty("main", T05PreviewFacadeCommand.class.getName()); actual.setProperty("exit", Integer.toString(result.exitCode));
        actual.setProperty("execution-profile", "IN_PROCESS");
        actual.setProperty("artifact-sha256", EvidenceFiles.sha256(products.resolve("pdf-migration-itext7-preview-0.1.0.jar")));
        actual.setProperty("argument-count", Integer.toString(arguments.length));
        for (int index = 0; index < arguments.length; index++) { actual.setProperty("argument." + index, arguments[index]); }
        retained.write(output.resolve("preview-execution.properties"), actual);
    }

    private static Properties identities(Path root) throws IOException {
        Properties result = new Properties(); Path authority = root.resolve("scripts/t05-evidence-pin.properties");
        try (InputStream input = Files.newInputStream(authority)) { result.load(input); }
        for (String name : result.stringPropertyNames()) {
            Path path = root.resolve(name).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path) || !result.getProperty(name).equals(EvidenceFiles.sha256(path))) {
                throw new IOException("Provider pinned input is missing or changed: " + name);
            }
        }
        result.setProperty("scripts/t05-evidence-pin.properties", EvidenceFiles.sha256(authority)); return result;
    }
}
