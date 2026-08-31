package net.zerocloud.pdf.tools.release;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ReleaseRehearsalCommandTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void missingBundleFailsObservablyThroughRepositoryCommand() throws Exception {
        Path output = temporaryFolder.newFolder("missing-bundle").toPath();

        CommandResult result = runValidate(output);

        assertNotEquals(result.output, 0, result.exitCode);
        assertTrue(result.output, result.output.contains("central-bundle.zip is required"));
    }

    @Test
    public void completeBundleAndEveryTestSignaturePassAtTheRepositoryCommand()
            throws Exception {
        Path repositoryRoot = Paths.get(requiredProperty("repositoryRoot"));
        Path output = temporaryFolder.newFolder("complete-bundle").toPath();
        ReleaseBundleFixture.create(output, repositoryRoot);

        CommandResult result = runValidate(output);

        assertEquals(result.output, 0, result.exitCode);
        assertTrue(result.output,
                result.output.contains("Release rehearsal bundle validation passed"));
        assertTrue(result.output, result.output.contains("verified-signatures=24"));
    }

    @Test
    public void structuralFailuresAreObservableAtTheRepositoryCommand() throws Exception {
        Path repositoryRoot = Paths.get(requiredProperty("repositoryRoot"));
        Path base = temporaryFolder.newFolder("failure-base").toPath();
        ReleaseBundleFixture.create(base, repositoryRoot);
        String prefix = "net/zerocloud/pdf-document/0.1.0/pdf-document-0.1.0";
        List<InvalidCase> invalidCases = Arrays.asList(
                invalid("missing-artifact", "missing required Central artifact", output ->
                        ReleaseBundleFixture.removeCentralEntry(output,
                                prefix + "-sources.jar")),
                invalid("invalid-signature", "invalid detached signature", output ->
                        ReleaseBundleFixture.replaceCentralEntry(output,
                                prefix + ".jar.asc",
                                "not an OpenPGP signature\n"
                                        .getBytes(StandardCharsets.UTF_8))),
                invalid("invalid-checksum", "invalid published checksum", output ->
                        ReleaseBundleFixture.replaceCentralEntry(output,
                                prefix + ".jar.sha256",
                                (repeat('0', 64) + "\n").getBytes(StandardCharsets.US_ASCII))),
                invalid("incomplete-pom", "incomplete POM metadata", output -> {
                    String path = "net/zerocloud/pdf-parent/0.1.0/pdf-parent-0.1.0.pom";
                    ReleaseBundleFixture.replaceCentralEntry(output, path,
                            "<project><modelVersion>4.0.0</modelVersion></project>\n"
                                    .getBytes(StandardCharsets.UTF_8));
                }),
                invalid("unexpected-module", "unexpected Central bundle entry", output ->
                        ReleaseBundleFixture.addCentralEntry(output,
                                "net/zerocloud/pdf-unexpected/0.1.0/"
                                        + "pdf-unexpected-0.1.0.pom",
                                "unexpected\n".getBytes(StandardCharsets.UTF_8))),
                invalid("high-vulnerability", "unresolved high-severity vulnerability",
                        output -> Files.write(output.resolve(
                                "audit/dependency-check-report.xml"),
                                ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                                + "<analysis><scanInfo><engineVersion>12.2.2"
                                + "</engineVersion></scanInfo><dependencies><dependency>"
                                + "<fileName>pdfbox-3.0.8.jar</fileName><vulnerabilities>"
                                + "<vulnerability><name>CVE-TEST-HIGH</name><cvssV3>"
                                + "<baseScore>9.8</baseScore></cvssV3></vulnerability>"
                                + "</vulnerabilities></dependency></dependencies>"
                                + "</analysis>\n").getBytes(StandardCharsets.UTF_8))),
                invalid("missing-audit", "missing audit report", output ->
                        Files.delete(output.resolve("audit/dependency-check-report.xml"))));

        for (InvalidCase invalidCase : invalidCases) {
            Path output = temporaryFolder.newFolder(invalidCase.name).toPath();
            copyTree(base, output);
            invalidCase.mutation.apply(output);

            CommandResult result = runValidate(output);

            assertNotEquals(invalidCase.name + " unexpectedly passed:\n" + result.output,
                    0, result.exitCode);
            assertTrue(invalidCase.name + " did not report " + invalidCase.expected
                            + ":\n" + result.output,
                    result.output.contains(invalidCase.expected));
        }
    }

    @Test
    public void reproducibilityComparisonRecordsHashesAndSignatureExclusions()
            throws Exception {
        Path repositoryRoot = Paths.get(requiredProperty("repositoryRoot"));
        Path fixture = temporaryFolder.newFolder("reproducible-fixture").toPath();
        ReleaseBundleFixture.create(fixture, repositoryRoot);
        Path comparison = temporaryFolder.newFolder("comparison").toPath();
        Files.copy(fixture.resolve("central-bundle.zip"),
                comparison.resolve("central-bundle-a.zip"));
        Files.copy(fixture.resolve("central-bundle.zip"),
                comparison.resolve("central-bundle-b.zip"));

        CommandResult result = runCommand("compare", comparison);

        assertEquals(result.output, 0, result.exitCode);
        String report = new String(Files.readAllBytes(
                comparison.resolve("reproducibility.txt")), StandardCharsets.UTF_8);
        assertTrue(report, report.contains("result=PASS"));
        assertTrue(report, report.contains("MATCH\t"));
        assertTrue(report, report.contains("EXCLUDED\t"));
        assertTrue(report, report.contains("excluded-reason=OpenPGP"));
    }

    @Test
    public void bundleCommandAssemblesCentralLayoutFromSignedReactorOutputs()
            throws Exception {
        Path repositoryRoot = Paths.get(requiredProperty("repositoryRoot"));
        Path buildTree = temporaryFolder.newFolder("signed-build-tree").toPath();
        ReleaseBundleFixture.createSignedBuildTree(buildTree, repositoryRoot);
        Path bundle = temporaryFolder.newFolder("assembled-bundle").toPath()
                .resolve("central-bundle.zip");

        CommandResult result = runCommand("bundle", buildTree.toString(),
                bundle.toString(), ReleaseBundleFixture.VERSION);

        assertEquals(result.output, 0, result.exitCode);
        assertTrue(result.output, result.output.contains("Central bundle assembled"));
        Map<String, byte[]> entries = ReleaseBundleFixture.readZip(bundle);
        String prefix = "net/zerocloud/pdf-document/0.1.0/pdf-document-0.1.0";
        assertTrue(entries.containsKey(prefix + ".jar"));
        assertTrue(entries.containsKey(prefix + ".jar.asc"));
        assertTrue(entries.containsKey(prefix + ".jar.sha512"));
        assertTrue(entries.containsKey(prefix + ".jar.asc.sha256"));
    }

    private CommandResult runValidate(Path output) throws Exception {
        return runCommand("validate", output.toString());
    }

    private CommandResult runCommand(String command, Path output) throws Exception {
        return runCommand(command, output.toString());
    }

    private CommandResult runCommand(String... arguments) throws Exception {
        Path repositoryRoot = Paths.get(requiredProperty("repositoryRoot"));
        List<String> commandLine = new java.util.ArrayList<String>();
        commandLine.add(repositoryRoot.resolve("scripts/release-rehearsal").toString());
        commandLine.addAll(Arrays.asList(arguments));
        ProcessBuilder builder = new ProcessBuilder(commandLine);
        builder.directory(repositoryRoot.toFile());
        builder.redirectErrorStream(true);
        builder.environment().put("RELEASE_TOOL_CLASSPATH",
                requiredProperty("releaseToolClasspath"));
        Process process = builder.start();
        String commandOutput = read(process.getInputStream());
        int exitCode = process.waitFor();
        return new CommandResult(exitCode, commandOutput);
    }

    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Missing system property " + name);
        }
        return value;
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (java.util.Iterator<Path> iterator = paths.iterator(); iterator.hasNext();) {
                Path path = iterator.next();
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }

    private static InvalidCase invalid(String name, String expected, Mutation mutation) {
        return new InvalidCase(name, expected, mutation);
    }

    private static final class CommandResult {
        private final int exitCode;
        private final String output;

        private CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    private interface Mutation {
        void apply(Path output) throws Exception;
    }

    private static final class InvalidCase {
        private final String name;
        private final String expected;
        private final Mutation mutation;

        private InvalidCase(String name, String expected, Mutation mutation) {
            this.name = name;
            this.expected = expected;
            this.mutation = mutation;
        }
    }
}
