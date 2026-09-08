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
            fail("Usage: InventoryCommand <validate|generate|check|readiness|environments> <repository-root>");
        }

        String action = arguments[0];
        String evidencePath = System.getProperty("folio.inventory.evidence");
        if (evidencePath != null && !"readiness".equals(action)) {
            fail("A separate evidence index is supported only for read-only readiness evaluation.");
        }
        if (!"validate".equals(action)
                && !"generate".equals(action)
                && !"check".equals(action)
                && !"readiness".equals(action)
                && !"environments".equals(action)) {
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

        if ("environments".equals(action)) {
            requireFoundation(model);
            for (FoundationInventory.Environment environment : model.foundation.environments.values()) {
                System.out.println("FOUNDATION_ENVIRONMENT " + environment.jdk + " "
                        + environment.identity.get("image"));
            }
            return;
        }

        FoundationReadiness readiness = null;
        if (model.foundation != null) {
            readiness = new FoundationReadiness(model.foundation);
            if (evidencePath == null) {
                readiness.evaluate();
            } else {
                System.out.println("Evidence source: " + evidencePath);
                readiness.evaluate(evidencePath);
            }
        }
        if ("readiness".equals(action)) {
            requireFoundation(model);
            System.out.println("Foundation " + model.foundation.release + ": "
                    + (readiness.ready() ? "READY" : "NOT READY"));
            System.out.println("Contract identity: " + readiness.contractIdentity);
            System.out.println("Candidate identity: "
                    + (readiness.candidateIdentity.isEmpty() ? "missing" : readiness.candidateIdentity));
            for (String error : readiness.global) {
                System.err.println("BLOCKED release: " + error);
            }
            for (Map.Entry<String, List<String>> obligation : readiness.blockers.entrySet()) {
                FoundationInventory.Obligation item = model.foundation.obligations.get(obligation.getKey());
                if (obligation.getValue().isEmpty()) {
                    System.out.println("SATISFIED " + item.id + " (#" + item.slice + ")");
                } else {
                    for (String error : obligation.getValue()) {
                        System.err.println("BLOCKED " + item.id + " (#" + item.slice + "): " + error);
                    }
                }
            }
            if (!readiness.ready()) {
                fail("Foundation release is not ready; all required obligations must be satisfied.");
            }
            return;
        }

        MarkdownGenerator generator = new MarkdownGenerator();
        Map<Path, String> documents = generator.generate(model);
        if (readiness != null) {
            documents.put(repositoryRoot.resolve(FoundationMarkdown.OUTPUT), FoundationMarkdown.generate(readiness));
        }
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

    private static void requireFoundation(InventoryModel model) {
        if (model.foundation == null) {
            fail("Missing Foundation obligation inventory; add the foundation-release authority reference.");
        }
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
