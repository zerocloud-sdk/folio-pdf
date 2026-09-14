package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import net.zerocloud.pdf.WorkflowExecutionProfile;

/** Repository-only text and logical-structure evidence recorder. */
public final class T13EvidenceCommand {
    private T13EvidenceCommand() { }

    /**
     * Records independent extraction evidence against the frozen original corpus.
     * @param arguments {@code <repository> <fresh-output> <execution-profile> <release>},
     *     {@code negative-controls <repository> <fresh-output> <execution-profile> <release>},
     *     {@code products <repository> <fresh-output> <execution-profile>},
     *     {@code syntax <repository> <input> <fresh-output> <release>},
     *     {@code declaration-standards <repository> <input> <fresh-output>},
     *     {@code program-standards <repository> <input> <fresh-output>},
     *     {@code observe <repository> <input> <product> <fresh-output> <execution-profile> <release>},
     *     {@code semantic <repository> <input> <product> <fresh-output> <execution-profile>},
     *     {@code semantic-controls <repository> <fresh-output> <execution-profile>},
     *     {@code visual <repository> <input> <product> <fresh-output> <release>},
     *     {@code visual-controls <repository> <fresh-output> <release>}, or
     *     {@code font-raster-agreement <original-raster> <observed-raster> <fresh-output>}
     * @throws Exception when declarations, identities or tool execution fail
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 5 && "negative-controls".equals(arguments[0])) {
            Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
            Path output = Files.createDirectory(Paths.get(arguments[2]).toAbsolutePath().normalize());
            save(output.resolve("result.properties"), T13NegativeControls.record(root, output,
                    WorkflowExecutionProfile.valueOf(arguments[3]), arguments[4]));
            return;
        }
        if (arguments.length == 7 && "observe".equals(arguments[0])) {
            Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
            Path input = Paths.get(arguments[2]).toAbsolutePath().normalize();
            Path output = Files.createDirectory(Paths.get(arguments[4]).toAbsolutePath().normalize());
            save(output.resolve("result.properties"), T13CombinedEvidence.record(root, input, arguments[3], output,
                    net.zerocloud.pdf.WorkflowExecutionProfile.valueOf(arguments[5]), arguments[6]));
            return;
        }
        if (arguments.length == 4 && "program-standards".equals(arguments[0])) {
            Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
            Path input = Paths.get(arguments[2]).toAbsolutePath().normalize();
            Path output = Files.createDirectory(Paths.get(arguments[3]).toAbsolutePath().normalize());
            save(output.resolve("result.properties"), T13ProgramStandards.record(root, input, output));
            return;
        }
        if (arguments.length == 4 && "declaration-standards".equals(arguments[0])) {
            Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
            Path input = Paths.get(arguments[2]).toAbsolutePath().normalize();
            Path output = Files.createDirectory(Paths.get(arguments[3]).toAbsolutePath().normalize());
            save(output.resolve("result.properties"), T13DeclarationStandards.record(root, input, output));
            return;
        }
        if (arguments.length == 5 && "syntax".equals(arguments[0])) {
            Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
            Path input = Paths.get(arguments[2]).toAbsolutePath().normalize();
            Path output = Files.createDirectory(Paths.get(arguments[3]).toAbsolutePath().normalize());
            save(output.resolve("result.properties"), T13IndependentEvidence.recordSyntax(root, input, output, arguments[4]));
            return;
        }
        if (arguments.length == 4 && "font-raster-agreement".equals(arguments[0])) {
            Path output = Files.createDirectory(Paths.get(arguments[3]).toAbsolutePath().normalize());
            save(output.resolve("result.properties"), T13FontRasterAgreement.inspect(
                    Paths.get(arguments[1]), Paths.get(arguments[2])));
            return;
        }
        if (arguments.length == 4 && "semantic-controls".equals(arguments[0])) {
            Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
            Path output = Files.createDirectory(Paths.get(arguments[2]).toAbsolutePath().normalize());
            save(output.resolve("result.properties"), T13NegativeControls.semantic(root, output,
                    net.zerocloud.pdf.WorkflowExecutionProfile.valueOf(arguments[3])));
            return;
        }
        if (arguments.length == 6 && "semantic".equals(arguments[0])) {
            Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
            Path input = Paths.get(arguments[2]).toAbsolutePath().normalize();
            Path output = Files.createDirectory(Paths.get(arguments[4]).toAbsolutePath().normalize());
            save(output.resolve("result.properties"), T13ExtractionSemantics.inspect(root, input,
                    arguments[3], output, net.zerocloud.pdf.WorkflowExecutionProfile.valueOf(arguments[5])));
            return;
        }
        if (arguments.length == 4 && "products".equals(arguments[0])) {
            Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
            T13Corpus corpus = new T13Corpus(root);
            Path output = Files.createDirectory(Paths.get(arguments[2]).toAbsolutePath().normalize());
            T13ExtractionProducts.create(corpus, output,
                    net.zerocloud.pdf.WorkflowExecutionProfile.valueOf(arguments[3]));
            return;
        }
        if (arguments.length == 4 && "visual-controls".equals(arguments[0])) {
            Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
            Path output = Files.createDirectory(Paths.get(arguments[2]).toAbsolutePath().normalize());
            save(output.resolve("result.properties"), T13NegativeControls.visual(root, output, arguments[3]));
            return;
        }
        if (arguments.length == 4) {
            recordAll(arguments);
            return;
        }
        if (arguments.length != 6 || !"visual".equals(arguments[0])) {
            throw new IllegalArgumentException(
                    "Usage: T13EvidenceCommand visual <repository> <input> <product> <fresh-output> <release>");
        }
        Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
        Path input = Paths.get(arguments[2]).toAbsolutePath().normalize();
        Path output = Files.createDirectory(Paths.get(arguments[4]).toAbsolutePath().normalize());
        save(output.resolve("result.properties"),
                T13IndependentEvidence.recordVisual(root, input, arguments[3], output, arguments[5]));
    }

    private static void recordAll(String[] arguments) throws Exception {
        Path root = Paths.get(arguments[0]).toAbsolutePath().normalize();
        T13Corpus corpus = new T13Corpus(root);
        Path output = Files.createDirectory(Paths.get(arguments[1]).toAbsolutePath().normalize());
        WorkflowExecutionProfile execution = WorkflowExecutionProfile.valueOf(arguments[2]);
        String release = arguments[3];
        RetainedEvidence retained = T13ExtractionProducts.create(corpus, output, execution);
        Properties overall = new Properties();
        overall.setProperty("phase", "certification");
        overall.setProperty("profile", T13Corpus.PROFILE);
        overall.setProperty("native-execution-profile", execution.name());
        overall.setProperty("facade-execution-profile", WorkflowExecutionProfile.IN_PROCESS.name());
        String[] chains = {"syntax", "standards", "semantic", "visual"};
        for (String chain : chains) { overall.setProperty(chain, "pass"); }
        for (String api : new String[] {"native", "facade"}) {
            WorkflowExecutionProfile actual = "native".equals(api) ? execution : WorkflowExecutionProfile.IN_PROCESS;
            for (String product : T13Corpus.PRODUCTS) {
                Path directory = output.resolve(api + "-" + product);
                Properties observed = T13CombinedEvidence.record(root, directory.resolve("extraction.pdf"),
                        product, directory, actual, release);
                retained.write(directory.resolve("result.properties"), observed);
                T13CombinedEvidence.includeManifest(retained, directory, observed.getProperty("retained-files-sha256"));
                java.util.Set<String> required = new java.util.HashSet<String>(
                        java.util.Arrays.asList(observed.getProperty("required-chains").split(",")));
                for (String chain : chains) {
                    String value = observed.getProperty(chain);
                    overall.setProperty(api + "." + product + "." + chain, value);
                    if (required.contains(chain)) {
                        overall.setProperty(chain, EvidenceResult.combine(overall.getProperty(chain), value));
                    }
                }
            }
        }
        Path negative = Files.createDirectory(output.resolve("negative"));
        Properties controls = T13NegativeControls.record(root, negative, execution, release);
        retained.write(negative.resolve("result.properties"), controls);
        T13CombinedEvidence.includeManifest(retained, negative, controls.getProperty("retained-files-sha256"));
        String determination = "pass";
        for (String chain : chains) {
            if (!"fail".equals(controls.getProperty(chain))) { overall.setProperty(chain, "indeterminate"); }
            determination = EvidenceResult.combine(determination, overall.getProperty(chain));
        }
        corpus.verifySources();
        retained.verify();
        overall.setProperty("retained-files-sha256", retained.publishManifest(determination));
        save(output.resolve("result.properties"), overall);
        for (String chain : chains) {
            if (!"pass".equals(overall.getProperty(chain))) {
                throw new IOException("T13 " + chain + " observation did not pass; retained " + output);
            }
        }
    }

    static void save(Path path, Properties values) throws IOException {
        new RetainedEvidence(path.getParent()).write(path, values);
    }
}
