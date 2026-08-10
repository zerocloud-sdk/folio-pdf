package net.zerocloud.pdf.tools.inventory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Repository-only command boundary for validating compatibility inventories and
 * generating their human-readable views.
 */
public final class InventoryCommand {

    private InventoryCommand() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 2) {
            fail("Usage: InventoryCommand <validate|generate|check> <repository-root>");
        }

        String action = arguments[0];
        if (!"validate".equals(action)
                && !"generate".equals(action)
                && !"check".equals(action)) {
            fail("Unsupported inventory action: " + action);
        }

        Path repositoryRoot = Paths.get(arguments[1]).toAbsolutePath().normalize();
        Path matrixPath = repositoryRoot.resolve("capabilities/capability-matrix.yaml");
        Path facadePath = repositoryRoot.resolve("capabilities/facade-surface.yaml");
        InventoryValidator validator = new InventoryValidator();
        ValidationResult result = validator.validate(repositoryRoot, matrixPath, facadePath);
        if (!result.isValid()) {
            for (String error : result.errors()) {
                System.err.println("ERROR: " + error);
            }
            fail("Inventory validation failed with " + result.errors().size() + " error(s).");
        }

        InventoryModel model = result.model();
        System.out.println("Inventory validation passed: "
                + model.capabilities.size() + " capabilities, "
                + (model.stableSurfaces.size() + model.previewSurfaces.size())
                + " facade surfaces, " + model.exclusions.size() + " exclusions.");

        if ("validate".equals(action)) {
            return;
        }

        MarkdownGenerator generator = new MarkdownGenerator();
        Map<Path, String> documents = generator.generate(model);
        if ("generate".equals(action)) {
            writeDocuments(documents);
            for (Path path : sortedPaths(documents)) {
                System.out.println("Generated " + repositoryRoot.relativize(path));
            }
            return;
        }

        List<Path> stale = staleDocuments(documents);
        if (!stale.isEmpty()) {
            for (Path path : stale) {
                System.err.println("ERROR: generated documentation is stale: "
                        + repositoryRoot.relativize(path));
            }
            fail("Generated inventory documentation is stale; run ./scripts/inventory generate.");
        }
        System.out.println("Generated inventory documentation is current.");
    }

    private static void writeDocuments(Map<Path, String> documents) throws IOException {
        for (Map.Entry<Path, String> document : documents.entrySet()) {
            Files.createDirectories(document.getKey().getParent());
            Files.write(document.getKey(),
                    document.getValue().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        }
    }

    private static List<Path> staleDocuments(Map<Path, String> documents) throws IOException {
        List<Path> stale = new ArrayList<Path>();
        for (Map.Entry<Path, String> document : documents.entrySet()) {
            if (!Files.isRegularFile(document.getKey())) {
                stale.add(document.getKey());
                continue;
            }
            String actual = new String(
                    Files.readAllBytes(document.getKey()), StandardCharsets.UTF_8);
            if (!document.getValue().equals(actual)) {
                stale.add(document.getKey());
            }
        }
        return stale;
    }

    private static List<Path> sortedPaths(Map<Path, String> documents) {
        List<Path> paths = new ArrayList<Path>(documents.keySet());
        java.util.Collections.sort(paths);
        return paths;
    }

    private static void fail(String message) {
        System.err.println(message);
        throw new InventoryCommandFailure(message);
    }

    private static final class InventoryCommandFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        InventoryCommandFailure(String message) {
            super(message);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
