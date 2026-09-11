package net.zerocloud.pdf.acceptance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import net.zerocloud.pdf.WorkflowExecutionProfile;

/** Repository-only T10 product and evidence entry point. */
public final class T10EvidenceCommand {
    private T10EvidenceCommand() {
    }

    /**
     * Produces the frozen Native and Facade page operations into a fresh directory.
     * Product generation alone makes no independent certification claim.
     * @param arguments products, repository root, fresh output, Native execution profile
     * @throws Exception if an input, operation or publication is unavailable
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 7 && "observe".equals(arguments[0])) {
            Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
            Path input = Paths.get(arguments[2]).toAbsolutePath().normalize();
            Path output = Files.createDirectory(
                    Paths.get(arguments[4]).toAbsolutePath().normalize());
            Properties result = T10IndependentEvidence.record(
                    root,
                    input,
                    arguments[3],
                    output,
                    WorkflowExecutionProfile.valueOf(arguments[5]),
                    arguments[6]);
            T10PageProducts.save(output.resolve("result.properties"), result);
            return;
        }
        if (arguments.length == 6 && "visual".equals(arguments[0])) {
            Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
            Path input = Paths.get(arguments[2]).toAbsolutePath().normalize();
            Path output = Files.createDirectory(
                    Paths.get(arguments[4]).toAbsolutePath().normalize());
            Properties result = T10IndependentEvidence.recordVisual(
                    root, input, arguments[3], output, arguments[5]);
            T10PageProducts.save(output.resolve("result.properties"), result);
            return;
        }
        if (arguments.length == 6 && "semantic".equals(arguments[0])) {
            Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
            T10Corpus corpus = new T10Corpus(root);
            Path input = Paths.get(arguments[2]).toAbsolutePath().normalize();
            String hash = EvidenceFiles.sha256(input);
            Path output = Files.createDirectory(Paths.get(arguments[4]).toAbsolutePath().normalize());
            Properties result = T10PageSemantics.inspect(corpus, input, arguments[3], WorkflowExecutionProfile.valueOf(arguments[5]));
            if (!hash.equals(EvidenceFiles.sha256(input))) {
                result.setProperty("semantic", "indeterminate");
                result.setProperty("finding", "Input changed during public Native observation.");
            }
            result.setProperty("input-sha256", hash);
            result.setProperty("profile", T10Corpus.PROFILE);
            T10PageProducts.save(output.resolve("result.properties"), result);
            EvidenceFiles.write(output.resolve("semantic.txt"), result.getProperty("finding") + "\n");
            return;
        }
        if (arguments.length == 4 && !"products".equals(arguments[0])) {
            recordAll(arguments);
            return;
        }
        if (arguments.length != 4 || !"products".equals(arguments[0])) {
            throw new IllegalArgumentException("Usage: T10EvidenceCommand products <repository> <fresh-output> <execution-profile>");
        }
        Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
        T10Corpus corpus = new T10Corpus(root);
        WorkflowExecutionProfile execution = WorkflowExecutionProfile.valueOf(arguments[3]);
        Path output = Files.createDirectory(Paths.get(arguments[2]).toAbsolutePath().normalize());
        T10PageProducts.create(corpus, output, execution);
        corpus.verifySources();
        Properties result = new Properties();
        result.setProperty("phase", "products-only");
        result.setProperty("profile", T10Corpus.PROFILE);
        result.setProperty("native-execution-profile", execution.name());
        result.setProperty("facade-execution-profile", WorkflowExecutionProfile.IN_PROCESS.name());
        T10PageProducts.save(output.resolve("products.properties"), result);
    }

    private static void recordAll(String[] arguments) throws Exception {
        Path root = Paths.get(arguments[0]).toAbsolutePath().normalize();
        Path output = Files.createDirectory(
                Paths.get(arguments[1]).toAbsolutePath().normalize());
        WorkflowExecutionProfile execution =
                WorkflowExecutionProfile.valueOf(arguments[2]);
        String release = arguments[3];
        T10Corpus corpus = new T10Corpus(root);
        T10PageProducts.create(corpus, output, execution);
        corpus.verifySources();

        Properties products = new Properties();
        products.setProperty("phase", "certification");
        products.setProperty("profile", T10Corpus.PROFILE);
        products.setProperty("native-execution-profile", execution.name());
        products.setProperty("facade-execution-profile",
                WorkflowExecutionProfile.IN_PROCESS.name());
        T10PageProducts.save(output.resolve("products.properties"), products);

        Properties overall = new Properties();
        overall.setProperty("profile", T10Corpus.PROFILE);
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
                Properties observed = T10IndependentEvidence.record(
                        root,
                        directory.resolve("pages.pdf"),
                        product,
                        directory,
                        actual,
                        release);
                T10PageProducts.save(
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
        Properties controls = T10IndependentEvidence.negativeControls(
                root,
                negative,
                output.resolve("native-left/pages.pdf"),
                output.resolve("native-edited/pages.pdf"),
                execution,
                "pass".equals(overall.getProperty("standards")),
                release);
        T10PageProducts.save(negative.resolve("result.properties"), controls);
        for (String chain : new String[] {
                "syntax", "standards", "semantic", "visual"}) {
            if (!"fail".equals(controls.getProperty(chain))) {
                overall.setProperty(chain, "indeterminate");
            }
        }
        corpus.verifySources();
        T10PageProducts.save(output.resolve("result.properties"), overall);
        for (String chain : new String[] {
                "syntax", "standards", "semantic", "visual"}) {
            if (!"pass".equals(overall.getProperty(chain))) {
                throw new java.io.IOException("T10 " + chain
                        + " observation did not pass; retained " + output);
            }
        }
    }
}
