package net.zerocloud.pdf.tools.inventory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class InventoryCommandTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void checkedInAuthoritiesRecordFacadeContracts() throws Exception {
        Path repositoryRoot = Paths.get(requiredProperty("repositoryRoot"));

        CommandResult result = runCommand("check", repositoryRoot);

        assertEquals(result.output, 0, result.exitCode);
        assertTrue(result.output, result.output.matches(
                "(?s).*Inventory validation passed: [0-9]+ capabilities, "
                        + "12 facade surfaces, [0-9]+ exclusions\\..*"));

        ValidationResult validation = new InventoryValidator().validate(
                repositoryRoot,
                repositoryRoot.resolve("capabilities/capability-matrix.yaml"),
                repositoryRoot.resolve("capabilities/facade-surface.yaml"));
        assertTrue(validation.errors().toString(), validation.isValid());
        InventoryModel.Capability mappedCapability = null;
        for (InventoryModel.Capability capability : validation.model().capabilities) {
            if ("document.blank.create-publish-reopen".equals(capability.id)) {
                mappedCapability = capability;
            }
        }
        assertNotNull(mappedCapability);
        assertEquals(0, mappedCapability.stableFacadeIds.size());
        assertEquals(12, mappedCapability.previewFacadeIds.size());
        for (InventoryModel.Exclusion exclusion : validation.model().exclusions) {
            assertFalse("T04 capability remains explicitly excluded",
                    mappedCapability.id.equals(exclusion.capability));
        }

        String capabilities = read(repositoryRoot.resolve(MarkdownGenerator.CAPABILITY_OUTPUT));
        String facades = read(repositoryRoot.resolve(MarkdownGenerator.FACADE_OUTPUT));
        assertTrue(capabilities.contains("- Status: `experimental`"));
        assertTrue(capabilities.contains("- Certified platforms: none"));
        assertTrue(capabilities.contains("- Promotion gate `T06`:"));
        assertTrue(capabilities.contains(
                "`document.blank.create-publish-reopen`"));
        assertTrue(facades.contains("- Stable entries: `0`"));
        assertTrue(facades.contains("- Preview entries: `12`"));
        assertTrue(facades.contains("- Explicit capability exclusions: `5`"));
        assertTrue(facades.contains("`document.value.inspect-patch`"));
        assertTrue(facades.contains("`document.page.manipulate-merge-split`"));
        assertTrue(facades.contains(
                "`document.metadata.outlines-destinations-attachments`"));
        assertTrue(facades.contains(
                "`document.annotations-actions.manage`"));
        assertTrue(facades.contains("`itext7.kernel.pdf-document.add-new-page`"));
    }

    @Test
    public void validFixtureExercisesAllFourEvidenceStates() throws Exception {
        Path fixture = materialize("all-states", null);

        CommandResult result = runCommand("validate", fixture);

        assertEquals(result.output, 0, result.exitCode);
        assertTrue(result.output, result.output.contains(
                "Inventory validation passed: 6 capabilities, 2 facade surfaces, 4 exclusions."));
    }

    @Test
    public void invalidFixturesFailAtTheCommandBoundary() throws Exception {
        List<InvalidCase> cases = Arrays.asList(
                invalid("unsupported-state", "unsupported capability state unsupported"),
                invalid("missing-identifier", ".id: field is required"),
                invalid("unknown-dependency",
                        "Dependency Gate references unknown capability sample.unknown"),
                invalid("cyclic-dependency", "Capability dependency cycle:"),
                invalid("blocked-compatible-dependency",
                        "requires Dependency Gate sample.experimental to be compatible"),
                invalid("unknown-facade-capability",
                        "references unknown capability sample.unknown"),
                invalid("stable-facade-noncompatible",
                        "must be compatible, but it is experimental"),
                invalid("planned-with-evidence",
                        "planned capabilities cannot claim implementation evidence"),
                invalid("experimental-without-evidence",
                        "experimental capabilities require implementation evidence"),
                invalid("compatible-without-acceptance",
                        "compatible requires passing mandatory chain"),
                invalid("limited-without-acceptance",
                        "limited requires passing mandatory chain visual"),
                invalid("conflicting-evidence-metadata",
                        "duplicate or conflicting metadata label Status:"),
                invalid("facade-suffix-mismatch",
                        "must preserve the reference suffix as"),
                invalid("shared-evidence-producer",
                        "independent chains require distinct producer names"),
                invalid("release-train-mismatch",
                        "does not match root Maven version 9.9.8-TEST"),
                invalid("symlink-path-escape",
                        "escapes the repository root through symbolic links"),
                invalid("aliased-evidence-reuse",
                        "cannot reuse implementation or profile evidence"));

        for (InvalidCase invalidCase : cases) {
            Path fixture = materialize(invalidCase.name, invalidCase.name);
            CommandResult result = runCommand("validate", fixture);

            assertTrue(invalidCase.name + " unexpectedly passed:\n" + result.output,
                    result.exitCode != 0);
            assertTrue(invalidCase.name + " did not report " + invalidCase.expected
                            + ":\n" + result.output,
                    result.output.contains(invalidCase.expected));
        }
    }

    @Test
    public void generationIsDeterministicAndLinksBothAuthorities() throws Exception {
        Path fixture = materialize("generation", null);

        CommandResult first = runCommand("generate", fixture);
        assertEquals(first.output, 0, first.exitCode);
        Path capabilityDocument = fixture.resolve(MarkdownGenerator.CAPABILITY_OUTPUT);
        Path facadeDocument = fixture.resolve(MarkdownGenerator.FACADE_OUTPUT);
        String firstCapability = read(capabilityDocument);
        String firstFacade = read(facadeDocument);

        CommandResult second = runCommand("generate", fixture);
        assertEquals(second.output, 0, second.exitCode);
        assertEquals(firstCapability, read(capabilityDocument));
        assertEquals(firstFacade, read(facadeDocument));

        assertTrue(firstCapability.contains(
                "[`surface.compatible`](facade-surface.md#facade-surface-surface_dot_compatible)"));
        assertTrue(firstCapability.contains(
                "[excluded by `T99`](facade-surface.md#excluded-capability-sample_dot_planned)"));
        assertTrue(firstFacade.contains(
                "[`sample.compatible`](capability-matrix.md#capability-sample_dot_compatible)"));
        assertTrue(firstFacade.contains(
                "[`sample.experimental`](capability-matrix.md#capability-sample_dot_experimental)"));
        assertTrue(firstFacade.contains(
                "[`sample.limited`](capability-matrix.md#capability-sample_dot_limited)"));
        assertTrue(firstCapability.contains(
                "<a id=\"capability-anchor_dot_a_dash_b\"></a>"));
        assertTrue(firstCapability.contains(
                "<a id=\"capability-anchor_dot_a_dot_b\"></a>"));
        assertTrue(firstCapability.contains(
                "producer `fixture-syntax-validator@1.0` (`external-tool`)"));

        CommandResult current = runCommand("check", fixture);
        assertEquals(current.output, 0, current.exitCode);
        assertTrue(current.output.contains("Generated inventory documentation is current."));
    }

    @Test
    public void checkRejectsStaleGeneratedDocumentation() throws Exception {
        Path fixture = materialize("stale", null);
        CommandResult generated = runCommand("generate", fixture);
        assertEquals(generated.output, 0, generated.exitCode);
        Files.write(fixture.resolve(MarkdownGenerator.CAPABILITY_OUTPUT),
                "\nstale edit\n".getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.APPEND);

        CommandResult result = runCommand("check", fixture);

        assertTrue(result.output, result.exitCode != 0);
        assertTrue(result.output, result.output.contains(
                "generated documentation is stale: docs/generated/capability-matrix.md"));
    }

    @Test
    public void releaseTrainIncludesT04ProductsButNotTheBuildTool() throws Exception {
        Path repositoryRoot = Paths.get(requiredProperty("repositoryRoot"));
        String parentPom = read(repositoryRoot.resolve("pom.xml"));
        String bomPom = read(repositoryRoot.resolve("pdf-bom/pom.xml"));

        assertTrue(parentPom.contains("<module>build-tools/inventory</module>"));
        assertTrue(parentPom.contains("<module>pdf-migration-itext7</module>"));
        assertTrue(parentPom.contains("<module>pdf-migration-itext7-preview</module>"));
        assertFalse(bomPom.contains("<artifactId>pdf-inventory-tool</artifactId>"));
        assertTrue(bomPom.contains("<artifactId>pdf-migration-itext7</artifactId>"));
        assertTrue(bomPom.contains("<artifactId>pdf-migration-itext7-preview</artifactId>"));
    }

    private Path materialize(String name, String invalidCase)
            throws IOException, URISyntaxException {
        Path root = temporaryFolder.newFolder(name).toPath();
        copyTree(resource("fixtures/base"), root);
        if (invalidCase == null) {
            return root;
        }
        if ("symlink-path-escape".equals(invalidCase)) {
            Path external = temporaryFolder.newFile(name + "-external.md").toPath();
            Files.write(external, "external evidence\n".getBytes(StandardCharsets.UTF_8));
            Path evidence = root.resolve("evidence/implementation.md");
            Files.delete(evidence);
            Files.createSymbolicLink(evidence, external);
            return root;
        }
        if ("aliased-evidence-reuse".equals(invalidCase)) {
            Path matrix = root.resolve("capabilities/capability-matrix.yaml");
            String content = read(matrix);
            String acceptanceRecord =
                    "        record: evidence/compatible-syntax.md\n";
            int record = content.indexOf(acceptanceRecord);
            assertTrue("Missing compatible Acceptance Evidence record", record >= 0);
            String replacement =
                    "        record: evidence/./compatible-profile.md\n";
            String updated = content.substring(0, record)
                    + replacement
                    + content.substring(record + acceptanceRecord.length());
            Files.write(matrix, updated.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.TRUNCATE_EXISTING);
            return root;
        }

        Path overrides = resource("fixtures/invalid/" + invalidCase);
        Path matrixOverride = overrides.resolve("capability-matrix.yaml");
        Path facadeOverride = overrides.resolve("facade-surface.yaml");
        if (Files.isRegularFile(matrixOverride)) {
            Files.copy(matrixOverride, root.resolve("capabilities/capability-matrix.yaml"),
                    StandardCopyOption.REPLACE_EXISTING);
            if (!Files.isRegularFile(facadeOverride)) {
                facadeOverride = resource("fixtures/invalid/common/facade-surface.yaml");
            }
        }
        if (Files.isRegularFile(facadeOverride)) {
            Files.copy(facadeOverride, root.resolve("capabilities/facade-surface.yaml"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        Path evidenceOverrides = overrides.resolve("evidence");
        if (Files.isDirectory(evidenceOverrides)) {
            copyTree(evidenceOverrides, root.resolve("evidence"));
        }
        Path pomOverride = overrides.resolve("pom.xml");
        if (Files.isRegularFile(pomOverride)) {
            Files.copy(pomOverride, root.resolve("pom.xml"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        return root;
    }

    private static void copyTree(Path source, Path destination) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (java.util.Iterator<Path> iterator = paths.iterator(); iterator.hasNext();) {
                Path path = iterator.next();
                Path target = destination.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static Path resource(String name) throws URISyntaxException {
        java.net.URL resource = InventoryCommandTest.class.getClassLoader().getResource(name);
        assertNotNull("Missing test resource " + name, resource);
        return Paths.get(resource.toURI());
    }

    private static CommandResult runCommand(String action, Path repositoryRoot)
            throws IOException, InterruptedException {
        Path java = Paths.get(System.getProperty("java.home"), "bin",
                isWindows() ? "java.exe" : "java");
        Process process = new ProcessBuilder(
                java.toString(),
                "-cp",
                System.getProperty("java.class.path"),
                InventoryCommand.class.getName(),
                action,
                repositoryRoot.toString())
                .redirectErrorStream(true)
                .start();
        String output;
        try (InputStream input = process.getInputStream()) {
            output = read(input);
        }
        int exitCode = process.waitFor();
        return new CommandResult(exitCode, output);
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT)
                .contains("win");
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing system property: " + name);
        }
        return value;
    }

    private static InvalidCase invalid(String name, String expected) {
        return new InvalidCase(name, expected);
    }

    private static final class InvalidCase {
        private final String name;
        private final String expected;

        InvalidCase(String name, String expected) {
            this.name = name;
            this.expected = expected;
        }
    }

    private static final class CommandResult {
        private final int exitCode;
        private final String output;

        CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
