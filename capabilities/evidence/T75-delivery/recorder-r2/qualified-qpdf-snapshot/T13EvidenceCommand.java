package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/** Repository-only text and logical-structure evidence recorder. */
public final class T13EvidenceCommand {
    private T13EvidenceCommand() { }

    /**
     * Records independent extraction evidence against the frozen original corpus.
     * @param arguments {@code products <repository> <fresh-output> <execution-profile>},
     *     {@code syntax <repository> <input> <fresh-output> <release>},
     *     {@code semantic <repository> <input> <product> <fresh-output> <execution-profile>},
     *     {@code semantic-controls <repository> <fresh-output> <execution-profile>},
     *     {@code visual <repository> <input> <product> <fresh-output> <release>},
     *     {@code visual-controls <repository> <fresh-output> <release>}, or
     *     {@code font-raster-agreement <repository> <observed-raster> <fresh-output>}
     * @throws Exception when declarations, identities or tool execution fail
     */
    public static void main(String[] arguments) throws Exception {
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

    static void save(Path path, Properties values) throws IOException {
        try (OutputStream output = Files.newOutputStream(path)) { values.store(output, "Actual T13 observation"); }
    }
}
