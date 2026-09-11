package net.zerocloud.pdf.acceptance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import net.zerocloud.pdf.WorkflowExecutionProfile;

/** Repository-only annotation product and evidence recorder. */
public final class T12EvidenceCommand {
    private T12EvidenceCommand() { }

    /**
     * Generates the original operation plan or records independent observations.
     * Product generation alone makes no independent certification claim.
     * Accepted forms are:
     * <ul>
     *   <li>{@code products <repository> <fresh-output> <execution-profile>}</li>
     *   <li>{@code safety <repository> <fresh-output> <execution-profile>}</li>
     *   <li>{@code pixel <repository> <fresh-output>} for the exact one-pixel comparator control</li>
     *   <li>{@code controls <repository> <products-directory> <fresh-output> <execution-profile>}</li>
     *   <li>{@code observe <repository> <input> <product> <fresh-output> <execution-profile> <release>}</li>
     *   <li>{@code semantic <repository> <input> <product> <fresh-output> <execution-profile>}</li>
     *   <li>{@code visual <repository> <input> <product> <fresh-output> <release>}</li>
     *   <li>{@code <repository> <fresh-output> <execution-profile> <release>} for all phases</li>
     * </ul>
     * @param arguments one accepted command form
     * @throws Exception when a public operation, input identity or publication fails
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 3 && "pixel".equals(arguments[0])) {
            Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
            Path output = Files.createDirectory(Paths.get(arguments[2]).toAbsolutePath().normalize());
            T12PixelControl.record(root, output);
            return;
        }
        if (arguments.length == 4 && "safety".equals(arguments[0])) {
            Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
            Path output = Files.createDirectory(Paths.get(arguments[2]).toAbsolutePath().normalize());
            T12SafetyEvidence.record(root, output, WorkflowExecutionProfile.valueOf(arguments[3]));
            return;
        }
        if (arguments.length == 5 && "controls".equals(arguments[0])) {
            Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
            Path products = Paths.get(arguments[2]).toAbsolutePath().normalize();
            Path output = Files.createDirectory(Paths.get(arguments[3]).toAbsolutePath().normalize());
            Properties result = T12NegativeControls.record(root, products, output,
                    WorkflowExecutionProfile.valueOf(arguments[4]), "T12 control qualification");
            T12AnnotationProducts.save(output.resolve("result.properties"), result);
            return;
        }
        if (arguments.length == 7 && "observe".equals(arguments[0])) {
            Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
            Path input = Paths.get(arguments[2]).toAbsolutePath().normalize();
            Path output = Files.createDirectory(Paths.get(arguments[4]).toAbsolutePath().normalize());
            Properties result = T12IndependentEvidence.record(root, input, arguments[3], output,
                    WorkflowExecutionProfile.valueOf(arguments[5]), arguments[6]);
            T12AnnotationProducts.save(output.resolve("result.properties"), result);
            return;
        }
        if (arguments.length == 6 && "semantic".equals(arguments[0])) {
            Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
            Path input = Paths.get(arguments[2]).toAbsolutePath().normalize();
            Path output = Files.createDirectory(Paths.get(arguments[4]).toAbsolutePath().normalize());
            Properties result = T12AnnotationSemantics.inspect(root, input, arguments[3], output,
                    WorkflowExecutionProfile.valueOf(arguments[5]));
            T12AnnotationProducts.save(output.resolve("result.properties"), result);
            return;
        }
        if (arguments.length == 6 && "visual".equals(arguments[0])) {
            Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
            Path input = Paths.get(arguments[2]).toAbsolutePath().normalize();
            Path output = Files.createDirectory(Paths.get(arguments[4]).toAbsolutePath().normalize());
            Properties result = T12IndependentEvidence.recordVisual(root, input, arguments[3], output, arguments[5]);
            T12AnnotationProducts.save(output.resolve("result.properties"), result);
            return;
        }
        if (arguments.length == 4 && !"products".equals(arguments[0])) {
            recordAll(arguments);
            return;
        }
        if (arguments.length != 4 || !"products".equals(arguments[0])) {
            throw new IllegalArgumentException("Usage: T12EvidenceCommand products <repository> <fresh-output> <execution-profile>");
        }
        Path root = Paths.get(arguments[1]).toAbsolutePath().normalize();
        T12Corpus corpus = new T12Corpus(root);
        WorkflowExecutionProfile execution = WorkflowExecutionProfile.valueOf(arguments[3]);
        Path output = Files.createDirectory(Paths.get(arguments[2]).toAbsolutePath().normalize());
        T12AnnotationProducts.create(corpus, output, execution);
        corpus.verifySources();
        Properties result = new Properties();
        result.setProperty("phase", "products-only");
        result.setProperty("profile", T12Corpus.PROFILE);
        result.setProperty("native-execution-profile", execution.name());
        result.setProperty("facade-execution-profile", WorkflowExecutionProfile.IN_PROCESS.name());
        T12AnnotationProducts.save(output.resolve("products.properties"), result);
    }

    private static void recordAll(String[] arguments) throws Exception {
        Path root = Paths.get(arguments[0]).toAbsolutePath().normalize();
        T12Corpus corpus = new T12Corpus(root);
        Path output = Files.createDirectory(Paths.get(arguments[1]).toAbsolutePath().normalize());
        WorkflowExecutionProfile execution = WorkflowExecutionProfile.valueOf(arguments[2]);
        String release = arguments[3];
        T12AnnotationProducts.create(corpus, output, execution);
        corpus.verifySources();
        Properties overall = new Properties();
        overall.setProperty("profile", T12Corpus.PROFILE);
        overall.setProperty("native-execution-profile", execution.name());
        overall.setProperty("facade-execution-profile", WorkflowExecutionProfile.IN_PROCESS.name());
        Properties products = new Properties();
        products.putAll(overall);
        products.setProperty("phase", "certification");
        T12AnnotationProducts.save(output.resolve("products.properties"), products);
        String[] chains = {"syntax", "standards", "semantic", "visual"};
        for (String chain : chains) { overall.setProperty(chain, "pass"); }
        for (String api : new String[] {"native", "facade"}) {
            WorkflowExecutionProfile actual = "native".equals(api) ? execution : WorkflowExecutionProfile.IN_PROCESS;
            for (String product : T12Corpus.PRODUCTS) {
                Path directory = output.resolve(api + "-" + product);
                Properties observed = T12IndependentEvidence.record(root, directory.resolve("annotations.pdf"), product, directory, actual, release);
                T12AnnotationProducts.save(directory.resolve("result.properties"), observed);
                for (String chain : chains) {
                    String value = observed.getProperty(chain);
                    overall.setProperty(api + "." + product + "." + chain, value);
                    overall.setProperty(chain, EvidenceResult.combine(overall.getProperty(chain), value));
                }
            }
        }
        Path negative = Files.createDirectory(output.resolve("negative"));
        Properties controls = T12NegativeControls.record(root, output, negative, execution, release);
        T12AnnotationProducts.save(negative.resolve("result.properties"), controls);
        for (String chain : chains) {
            if (!"fail".equals(controls.getProperty(chain))) { overall.setProperty(chain, "indeterminate"); }
        }
        Properties safety = T12SafetyEvidence.record(root, Files.createDirectory(output.resolve("safety")), execution);
        overall.setProperty("safety", safety.getProperty("result"));
        corpus.verifySources();
        T12AnnotationProducts.save(output.resolve("result.properties"), overall);
        for (String chain : new String[] {"syntax", "standards", "semantic", "visual", "safety"}) {
            if (!"pass".equals(overall.getProperty(chain))) {
                throw new java.io.IOException("T12 " + chain + " observation did not pass; retained " + output);
            }
        }
    }
}
