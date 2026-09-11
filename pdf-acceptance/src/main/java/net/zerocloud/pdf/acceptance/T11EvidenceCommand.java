package net.zerocloud.pdf.acceptance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import net.zerocloud.pdf.WorkflowExecutionProfile;

/** Repository-only metadata product and evidence recorder. */
public final class T11EvidenceCommand {
    private T11EvidenceCommand() {
    }

    /**
     * Produces products or records one or all independent T11 evidence phases.
     * Product generation alone makes no independent certification claim.
     * Accepted forms are:
     * <ul>
     *   <li>{@code products <repository> <fresh-output> <execution-profile>}</li>
     *   <li>{@code safety <repository> <fresh-output> <execution-profile>}</li>
     *   <li>{@code observe <repository> <input> <product> <fresh-output> <execution-profile> <release>}</li>
     *   <li>{@code visual <repository> <input> <product> <fresh-output> <release>}</li>
     *   <li>{@code semantic <repository> <input> <product> <fresh-output> <execution-profile>}</li>
     *   <li>{@code <repository> <fresh-output> <execution-profile> <release>} for the complete recorder</li>
     * </ul>
     * @param arguments one accepted command form
     * @throws Exception if input identity, a public operation, observation, or publication fails
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 4 && "safety".equals(arguments[0])) {
            Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) {
                throw new IllegalArgumentException("T11 repository root is not a directory");
            }
            Path output = Files.createDirectory(
                    Paths.get(arguments[2]).toAbsolutePath().normalize());
            T11SafetyEvidence.record(output,
                    WorkflowExecutionProfile.valueOf(arguments[3]));
            return;
        }
        if (arguments.length == 7 && "observe".equals(arguments[0])) {
            Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
            Path input = Paths.get(arguments[2]).toAbsolutePath().normalize();
            Path output = Files.createDirectory(Paths.get(arguments[4]).toAbsolutePath().normalize());
            Properties result = T11IndependentEvidence.record(root, input, arguments[3], output,
                    WorkflowExecutionProfile.valueOf(arguments[5]), arguments[6]);
            T11MetadataProducts.save(output.resolve("result.properties"), result);
            return;
        }
        if (arguments.length == 6 && "visual".equals(arguments[0])) {
            Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
            Path input = Paths.get(arguments[2]).toAbsolutePath().normalize();
            Path output = Files.createDirectory(Paths.get(arguments[4]).toAbsolutePath().normalize());
            Properties result = T11IndependentEvidence.recordVisual(root, input, arguments[3], output, arguments[5]);
            T11MetadataProducts.save(output.resolve("result.properties"), result);
            return;
        }
        if (arguments.length == 6 && "semantic".equals(arguments[0])) {
            Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
            Path input = Paths.get(arguments[2]).toAbsolutePath().normalize();
            Path output = Files.createDirectory(Paths.get(arguments[4]).toAbsolutePath().normalize());
            Properties result = T11MetadataSemantics.inspect(new T11Corpus(root), input, arguments[3],
                    WorkflowExecutionProfile.valueOf(arguments[5]));
            T11MetadataProducts.save(output.resolve("result.properties"), result);
            EvidenceFiles.write(output.resolve("semantic.txt"), result.getProperty("finding") + "\n");
            return;
        }
        if (arguments.length == 4 && !"products".equals(arguments[0])) {
            recordAll(arguments);
            return;
        }
        if (arguments.length != 4 || !"products".equals(arguments[0])) {
            throw new IllegalArgumentException(
                    "Usage: T11EvidenceCommand products <repository> <fresh-output> <execution-profile>");
        }
        Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
        T11Corpus corpus = new T11Corpus(root);
        WorkflowExecutionProfile execution = WorkflowExecutionProfile.valueOf(arguments[3]);
        Path output = Files.createDirectory(Paths.get(arguments[2]).toAbsolutePath().normalize());
        T11MetadataProducts.create(corpus, output, execution);
        corpus.verifySources();
        Properties result = new Properties();
        result.setProperty("phase", "products-only");
        result.setProperty("profile", T11Corpus.PROFILE);
        result.setProperty("native-execution-profile", execution.name());
        result.setProperty("facade-execution-profile", WorkflowExecutionProfile.IN_PROCESS.name());
        T11MetadataProducts.save(output.resolve("products.properties"), result);
    }

    private static void recordAll(String[] arguments) throws Exception {
        Path root = Paths.get(arguments[0]).toAbsolutePath().normalize();
        Path output = Files.createDirectory(
                Paths.get(arguments[1]).toAbsolutePath().normalize());
        WorkflowExecutionProfile execution =
                WorkflowExecutionProfile.valueOf(arguments[2]);
        String release = arguments[3];
        T11Corpus corpus = new T11Corpus(root);
        T11MetadataProducts.create(corpus, output, execution);
        corpus.verifySources();

        Properties products = new Properties();
        products.setProperty("phase", "certification");
        products.setProperty("profile", T11Corpus.PROFILE);
        products.setProperty("native-execution-profile", execution.name());
        products.setProperty("facade-execution-profile",
                WorkflowExecutionProfile.IN_PROCESS.name());
        T11MetadataProducts.save(output.resolve("products.properties"), products);

        Properties overall = new Properties();
        overall.setProperty("profile", T11Corpus.PROFILE);
        overall.setProperty("native-execution-profile", execution.name());
        overall.setProperty("facade-execution-profile",
                WorkflowExecutionProfile.IN_PROCESS.name());
        for (String chain : new String[] {
                "syntax", "standards", "semantic", "visual"}) {
            overall.setProperty(chain, "pass");
        }
        for (String api : new String[] {"native", "facade"}) {
            WorkflowExecutionProfile actual = "facade".equals(api)
                    ? WorkflowExecutionProfile.IN_PROCESS : execution;
            for (String product : new String[] {
                    "edited", "merged", "left", "right"}) {
                Path directory = output.resolve(api + "-" + product);
                Properties observed = T11IndependentEvidence.record(
                        root,
                        directory.resolve("metadata.pdf"),
                        product,
                        directory,
                        actual,
                        release);
                T11MetadataProducts.save(
                        directory.resolve("result.properties"), observed);
                for (String chain : new String[] {
                        "syntax", "standards", "semantic", "visual"}) {
                    String value = observed.getProperty(chain);
                    overall.setProperty(api + "." + product + "." + chain,
                            value);
                    overall.setProperty(chain, EvidenceResult.combine(
                            overall.getProperty(chain), value));
                }
            }
        }

        Path negative = Files.createDirectory(output.resolve("negative"));
        Properties controls = T11IndependentEvidence.negativeControls(
                root,
                negative,
                output.resolve("native-edited/metadata.pdf"),
                output.resolve("native-edited/metadata.pdf"),
                execution,
                "pass".equals(overall.getProperty("standards")),
                release);
        T11MetadataProducts.save(negative.resolve("result.properties"), controls);
        for (String chain : new String[] {
                "syntax", "standards", "semantic", "visual"}) {
            if (!"fail".equals(controls.getProperty(chain))) {
                overall.setProperty(chain, "indeterminate");
            }
        }

        Path safetyOutput = Files.createDirectory(output.resolve("safety"));
        Properties safety = T11SafetyEvidence.record(safetyOutput, execution);
        overall.setProperty("safety", safety.getProperty("result"));
        corpus.verifySources();
        T11MetadataProducts.save(output.resolve("result.properties"), overall);
        for (String chain : new String[] {
                "syntax", "standards", "semantic", "visual", "safety"}) {
            if (!"pass".equals(overall.getProperty(chain))) {
                throw new java.io.IOException("T11 " + chain
                        + " observation did not pass; retained " + output);
            }
        }
    }
}
