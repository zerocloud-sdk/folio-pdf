package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class AcceptanceEvidenceCommandTest {

    private static final String COMMAND_CLASS =
            "net.zerocloud.pdf.acceptance.AcceptanceEvidenceCommand";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void runRecordsIndependentPassingChainsAgainstOneWorkflowOutput()
            throws Exception {
        Path output = temporaryFolder.newFolder("evidence").toPath();
        Path qpdf = qpdfFixture("qpdf", "12.4.0",
                "if [ \"${1-}\" = \"--check\" ]; then",
                "  echo 'PDF Version: 1.7'",
                "  echo 'No syntax or stream encoding errors found'",
                "  exit 0",
                "fi",
                "exit 2");

        CommandResult result = runCommand(output, qpdf);

        assertEquals(result.output, 0, result.exitCode);
        assertTrue(result.output, result.output.contains(
                "Acceptance Profile determination: indeterminate"));

        Path artifact = output.resolve("artifacts/T06-document-blank-output.pdf");
        assertTrue(Files.isRegularFile(artifact));
        assertTrue(Files.size(artifact) > 0L);

        String syntax = read(output.resolve("T06-document-blank-syntax.md"));
        String semantic = read(output.resolve("T06-document-blank-semantic.md"));
        String determination = read(output.resolve("T06-document-blank-determination.md"));

        assertMetadata(syntax, "Chain", "syntax");
        assertMetadata(syntax, "Result", "pass");
        assertMetadata(syntax, "Producer kind", "external-tool");
        assertMetadata(syntax, "Producer", "qpdf");
        assertMetadata(syntax, "Producer version", "12.4.0");
        assertMetadata(syntax, "Tool distribution SHA-256",
                "a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3");
        assertTrue(syntax.contains("Final determination: `pass`"));
        assertTrue(syntax.contains("artifacts/T06-document-blank-qpdf.txt"));

        assertMetadata(semantic, "Chain", "semantic");
        assertMetadata(semantic, "Result", "pass");
        assertMetadata(semantic, "Producer kind", "project-test");
        assertMetadata(semantic, "Producer", "folio-pdf-semantic-assertions");
        assertMetadata(semantic, "Producer version", "0.1.0-SNAPSHOT");
        assertTrue(semantic.contains("Final determination: `pass`"));
        assertTrue(semantic.contains("artifacts/T06-document-blank-semantic.txt"));

        String syntaxHash = metadata(syntax, "Input SHA-256");
        assertEquals(syntaxHash, metadata(semantic, "Input SHA-256"));
        assertTrue(syntaxHash.matches("[0-9a-f]{64}"));
        assertTrue(determination.contains("Final determination: `indeterminate`"));
        assertTrue(determination.contains("Missing mandatory chains: `standards`, `visual`"));

        String qpdfFindings = read(
                output.resolve("artifacts/T06-document-blank-qpdf.txt"));
        assertTrue(qpdfFindings.contains("Exit code: `0`"));
        assertTrue(qpdfFindings.contains(
                "Distribution SHA-256: "
                        + "`a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`"));
        assertTrue(qpdfFindings.contains("No syntax or stream encoding errors found"));

        String semanticFindings = read(
                output.resolve("artifacts/T06-document-blank-semantic.txt"));
        assertTrue(semanticFindings.contains("Publication status: `COMMITTED`"));
        assertTrue(semanticFindings.contains("Reopened page count: `1`"));
    }

    @Test
    public void semanticAssertionsReportTheObservedPageSequenceOnFailure()
            throws Exception {
        Path pdf = temporaryFolder.newFile("two-pages.pdf").toPath();
        WorkflowOutcome<Void> creation = new DocumentWorkflow().execute(
                WorkflowRequest.create(pdf, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });

        SemanticObservation observation = SemanticAssertions.inspect(creation, pdf);

        assertEquals(EvidenceResult.FAIL, observation.result());
        assertEquals("[1, 2]", observation.pageSequence());
        assertTrue(observation.recordFinding().contains(
                "observed `COMMITTED` and `2` reopened pages"));
        assertTrue(!observation.recordFinding().contains("reopened exactly one page"));
        assertTrue(observation.findings("fixture-hash", "fixture-version").contains(
                "Object graph observation: `reopened through DocumentWorkflow`"));
        assertTrue(observation.findings("fixture-hash", "fixture-version").contains(
                "Text order: not applicable; the blank-document profile emits no text."));
    }

    @Test
    public void missingQpdfRecordsIndeterminateAndNeverPass() throws Exception {
        Path output = temporaryFolder.newFolder("missing-qpdf-evidence").toPath();
        Path missingQpdf = output.resolve("missing-qpdf");

        CommandResult result = runCommand(output, missingQpdf);

        assertEquals(result.output, 0, result.exitCode);
        String syntax = read(output.resolve("T06-document-blank-syntax.md"));
        assertMetadata(syntax, "Result", "indeterminate");
        assertMetadata(syntax, "Producer version", "unavailable");
        assertTrue(syntax.contains("Final determination: `indeterminate`"));
        assertTrue(syntax.contains("The pinned qpdf tool was unavailable."));
        assertTrue(!syntax.contains("Result: `pass`"));

        String semantic = read(output.resolve("T06-document-blank-semantic.md"));
        assertMetadata(semantic, "Result", "pass");
        String determination = read(output.resolve(
                "T06-document-blank-determination.md"));
        assertTrue(determination.contains("Final determination: `indeterminate`"));
        assertTrue(determination.contains(
                "Indeterminate mandatory chains: `syntax`"));
    }

    @Test
    public void unpinnedQpdfVersionRecordsIndeterminateAndDoesNotRunCheck()
            throws Exception {
        Path output = temporaryFolder.newFolder("wrong-qpdf-evidence").toPath();
        Path qpdf = qpdfFixture("wrong-qpdf", "12.3.2",
                "echo 'unpinned qpdf check unexpectedly ran' >&2",
                "exit 99");

        CommandResult result = runCommand(output, qpdf);

        assertEquals(result.output, 0, result.exitCode);
        String syntax = read(output.resolve("T06-document-blank-syntax.md"));
        assertMetadata(syntax, "Result", "indeterminate");
        assertMetadata(syntax, "Producer version", "12.3.2");
        assertTrue(syntax.contains(
                "Expected pinned qpdf version `12.4.0`; observed `12.3.2`."));
        assertTrue(!syntax.contains("Result: `pass`"));
        String findings = read(output.resolve(
                "artifacts/T06-document-blank-qpdf.txt"));
        assertTrue(!findings.contains("unpinned qpdf check unexpectedly ran"));
    }

    @Test
    public void qpdfWarningsAreRecordedAsFailingSyntaxEvidence() throws Exception {
        Path output = temporaryFolder.newFolder("qpdf-warning-evidence").toPath();
        Path qpdf = qpdfFixture("warning-qpdf", "12.4.0",
                "echo 'WARNING: recovered malformed xref' >&2",
                "exit 3");

        CommandResult result = runCommand(output, qpdf);

        assertEquals(result.output, 0, result.exitCode);
        assertTrue(result.output, result.output.contains(
                "Acceptance Profile determination: fail"));
        String syntax = read(output.resolve("T06-document-blank-syntax.md"));
        assertMetadata(syntax, "Result", "fail");
        assertTrue(syntax.contains("Final determination: `fail`"));
        assertTrue(syntax.contains("qpdf reported warnings (exit code `3`)."));
        String findings = read(output.resolve(
                "artifacts/T06-document-blank-qpdf.txt"));
        assertTrue(findings.contains("Exit code: `3`"));
        assertTrue(findings.contains("WARNING: recovered malformed xref"));
        String determination = read(output.resolve(
                "T06-document-blank-determination.md"));
        assertTrue(determination.contains("Final determination: `fail`"));
        assertTrue(determination.contains("Failing mandatory chains: `syntax`"));
    }

    @Test
    public void qpdfErrorsAreRecordedAsFailingSyntaxEvidence() throws Exception {
        Path output = temporaryFolder.newFolder("qpdf-error-evidence").toPath();
        Path qpdf = qpdfFixture("error-qpdf", "12.4.0",
                "echo 'ERROR: unable to find trailer dictionary' >&2",
                "exit 2");

        CommandResult result = runCommand(output, qpdf);

        assertEquals(result.output, 0, result.exitCode);
        String syntax = read(output.resolve("T06-document-blank-syntax.md"));
        assertMetadata(syntax, "Result", "fail");
        assertTrue(syntax.contains("qpdf reported errors (exit code `2`)."));
        String findings = read(output.resolve(
                "artifacts/T06-document-blank-qpdf.txt"));
        assertTrue(findings.contains("Exit code: `2`"));
        assertTrue(findings.contains("ERROR: unable to find trailer dictionary"));
        String determination = read(output.resolve(
                "T06-document-blank-determination.md"));
        assertTrue(determination.contains("Final determination: `fail`"));
    }

    @Test
    public void unexpectedQpdfProcessExitRecordsIndeterminate() throws Exception {
        Path output = temporaryFolder.newFolder("qpdf-unavailable-evidence").toPath();
        Path qpdf = qpdfFixture("unavailable-qpdf", "12.4.0",
                "echo 'pinned qpdf payload is not provisioned' >&2",
                "exit 127");

        CommandResult result = runCommand(output, qpdf);

        assertEquals(result.output, 0, result.exitCode);
        String syntax = read(output.resolve("T06-document-blank-syntax.md"));
        assertMetadata(syntax, "Result", "indeterminate");
        assertTrue(syntax.contains(
                "qpdf did not return a documented inspection status (exit code `127`)."));
        assertTrue(!syntax.contains("Result: `pass`"));
        String findings = read(output.resolve(
                "artifacts/T06-document-blank-qpdf.txt"));
        assertTrue(findings.contains("Exit code: `127`"));
        assertTrue(findings.contains("pinned qpdf payload is not provisioned"));
        String determination = read(output.resolve(
                "T06-document-blank-determination.md"));
        assertTrue(determination.contains("Final determination: `indeterminate`"));
        assertTrue(determination.contains(
                "Indeterminate mandatory chains: `syntax`"));
    }

    @Test
    public void provisionerVerifiesAndStagesThePinnedQpdfArchive() throws Exception {
        Path root = Paths.get(requiredProperty("repositoryRoot"));
        Path archive = temporaryFolder.newFile(
                "qpdf-12.4.0-bin-linux-x86_64.zip").toPath();
        Path cache = temporaryFolder.newFolder("qpdf-cache").toPath();
        Path sha256 = executable("fixture-sha256sum", Arrays.asList(
                "#!/bin/sh",
                "case ${1} in",
                "  */bin/qpdf) digest=9ac787a28597e8428289a12ba3fedafd74bdfb4b4da1be814722faf76f14f21b ;;",
                "  *) digest=a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3 ;;",
                "esac",
                "printf '%s  %s\\n' \"${digest}\" \"${1}\""));
        Path unzip = executable("fixture-unzip", Arrays.asList(
                "#!/bin/sh",
                "destination=${4:?missing destination}",
                "mkdir -p \"${destination}/bin\" \"${destination}/lib\"",
                "printf '%s' 'libqpdf.so.30.4.0' > \"${destination}/lib/libqpdf.so.30\"",
                ": > \"${destination}/lib/libqpdf.so.30.4.0\"",
                "printf '%s\\n' '#!/bin/sh' "
                        + "'if [ \"${1-}\" = \"--version\" ]; then' "
                        + "'  echo \"qpdf version 12.4.0\"' "
                        + "'  echo \"Run qpdf --copyright for details.\"' "
                        + "'  exit 0' 'fi' "
                        + "'echo \"fixture qpdf\"' > \"${destination}/bin/qpdf\""));
        Map<String, String> environment = new HashMap<String, String>();
        environment.put("QPDF_CACHE_DIRECTORY", cache.toString());
        environment.put("SHA256_COMMAND", sha256.toString());
        environment.put("UNZIP_COMMAND", unzip.toString());

        CommandResult result = runProcess(
                Arrays.asList(
                        "sh",
                        root.resolve("scripts/provision-qpdf").toString(),
                        archive.toString()),
                environment);

        assertEquals(result.output, 0, result.exitCode);
        assertTrue(result.output, result.output.contains("Provisioned qpdf 12.4.0"));
        assertTrue(Files.isExecutable(cache.resolve("12.4.0/bin/qpdf")));
        assertTrue(Files.isSymbolicLink(cache.resolve("12.4.0/lib/libqpdf.so.30")));
        assertEquals(
                "9ac787a28597e8428289a12ba3fedafd74bdfb4b4da1be814722faf76f14f21b\n",
                read(cache.resolve("12.4.0/.binary-sha256")));
    }

    @Test
    public void qpdfWrapperRunsOnlyTheDigestMarkedPinnedPayload() throws Exception {
        Path root = Paths.get(requiredProperty("repositoryRoot"));
        Path cache = temporaryFolder.newFolder("wrapper-qpdf-cache").toPath();
        Path qpdfHome = cache.resolve("12.4.0");
        Files.createDirectories(qpdfHome.resolve("bin"));
        Files.createDirectories(qpdfHome.resolve("lib"));
        Path qpdf = qpdfHome.resolve("bin/qpdf");
        Files.write(qpdf, Arrays.asList(
                "#!/bin/sh",
                "echo \"${LD_LIBRARY_PATH-}|${1-}|${2-}\""), StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(qpdf, EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));
        Files.write(qpdfHome.resolve(".archive-sha256"), Arrays.asList(
                "a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3"),
                StandardCharsets.UTF_8);
        Files.write(qpdfHome.resolve(".binary-sha256"), Arrays.asList(
                "9ac787a28597e8428289a12ba3fedafd74bdfb4b4da1be814722faf76f14f21b"),
                StandardCharsets.UTF_8);
        Map<String, String> environment = new HashMap<String, String>();
        environment.put("QPDF_CACHE_DIRECTORY", cache.toString());

        CommandResult result = runProcess(
                Arrays.asList(
                        "sh",
                        root.resolve("scripts/container-bin/qpdf").toString(),
                        "--check",
                        "fixture.pdf"),
                environment);

        assertEquals(result.output, 0, result.exitCode);
        assertTrue(result.output, result.output.contains(
                qpdfHome.resolve("lib").toString() + "|--check|fixture.pdf"));
    }

    @Test
    public void qpdfWrapperRejectsCacheWithoutThePinnedBinaryMarker() throws Exception {
        Path root = Paths.get(requiredProperty("repositoryRoot"));
        Path cache = temporaryFolder.newFolder("unmarked-qpdf-cache").toPath();
        Path qpdfHome = cache.resolve("12.4.0");
        Files.createDirectories(qpdfHome.resolve("bin"));
        Path qpdf = qpdfHome.resolve("bin/qpdf");
        Files.write(qpdf, Arrays.asList("#!/bin/sh", "exit 0"), StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(qpdf, EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));
        Files.write(qpdfHome.resolve(".archive-sha256"), Arrays.asList(
                "a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3"),
                StandardCharsets.UTF_8);
        Map<String, String> environment = new HashMap<String, String>();
        environment.put("QPDF_CACHE_DIRECTORY", cache.toString());

        CommandResult result = runProcess(
                Arrays.asList(
                        "sh",
                        root.resolve("scripts/container-bin/qpdf").toString(),
                        "--version"),
                environment);

        assertEquals(result.output, 127, result.exitCode);
        assertTrue(result.output, result.output.contains("digest marker is invalid"));
    }

    @Test
    public void acceptanceRunnerCannotOverrideTheRepositoryPinnedQpdfPath()
            throws Exception {
        Path root = Paths.get(requiredProperty("repositoryRoot"));
        Path output = temporaryFolder.newFolder("runner-output").toPath();
        Path maven = executable("fixture-maven", Arrays.asList(
                "#!/bin/sh",
                "printf '%s\\n' \"$@\""));
        Map<String, String> environment = new HashMap<String, String>();
        environment.put("MAVEN_COMMAND", maven.toString());
        environment.put("QPDF_COMMAND", "/tmp/untrusted-qpdf");

        CommandResult result = runProcess(
                Arrays.asList(
                        "sh",
                        root.resolve("scripts/acceptance").toString(),
                        output.toString()),
                environment);

        assertEquals(result.output, 0, result.exitCode);
        assertTrue(result.output, result.output.contains("-pl\npdf-acceptance\n"));
        assertTrue(result.output, result.output.contains("-Pacceptance-record"));
        assertTrue(result.output, result.output.contains(
                "-Dacceptance.output=" + output.toAbsolutePath().normalize()));
        assertTrue(result.output, !result.output.contains("-Dacceptance.qpdf="));
        String acceptancePom = read(root.resolve("pdf-acceptance/pom.xml"));
        assertTrue(acceptancePom.contains(
                "${maven.multiModuleProjectDirectory}/scripts/container-bin/qpdf"));
        assertTrue(acceptancePom.contains(
                "${maven.multiModuleProjectDirectory}/scripts/qpdf-pin.properties"));
        assertTrue(result.output, result.output.endsWith("verify\n"));
    }

    private Path executable(String name, Iterable<String> lines) throws IOException {
        Path executable = temporaryFolder.newFile(name).toPath();
        Files.write(executable, lines, StandardCharsets.UTF_8);
        Set<PosixFilePermission> permissions = EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(executable, permissions);
        return executable;
    }

    private Path qpdfFixture(String name, String version, String... checkBehavior)
            throws IOException {
        List<String> lines = new ArrayList<String>();
        lines.add("#!/bin/sh");
        lines.add("if [ \"${1-}\" = \"--version\" ]; then");
        lines.add("  echo 'qpdf version " + version + "'");
        lines.add("  exit 0");
        lines.add("fi");
        lines.addAll(Arrays.asList(checkBehavior));
        return executable(name, lines);
    }

    private static CommandResult runCommand(Path output, Path qpdf)
            throws IOException, InterruptedException {
        Path repositoryRoot = Paths.get(requiredProperty("repositoryRoot"));
        Path java = Paths.get(System.getProperty("java.home"), "bin", "java");
        Process process = new ProcessBuilder(
                java.toString(),
                "-cp",
                System.getProperty("java.class.path"),
                COMMAND_CLASS,
                output.toString(),
                qpdf.toString(),
                repositoryRoot.resolve("scripts/qpdf-pin.properties").toString(),
                "0.1.0-SNAPSHOT")
                .redirectErrorStream(true)
                .start();
        String commandOutput;
        try (InputStream input = process.getInputStream()) {
            commandOutput = read(input);
        }
        return new CommandResult(process.waitFor(), commandOutput);
    }

    private static CommandResult runProcess(
            Iterable<String> command,
            Map<String, String> environment)
            throws IOException, InterruptedException {
        java.util.ArrayList<String> arguments = new java.util.ArrayList<String>();
        for (String argument : command) {
            arguments.add(argument);
        }
        ProcessBuilder builder = new ProcessBuilder(arguments).redirectErrorStream(true);
        builder.environment().putAll(environment);
        Process process = builder.start();
        String commandOutput;
        try (InputStream input = process.getInputStream()) {
            commandOutput = read(input);
        }
        return new CommandResult(process.waitFor(), commandOutput);
    }

    private static void assertMetadata(String record, String label, String expected) {
        assertEquals(expected, metadata(record, label));
    }

    private static String metadata(String record, String label) {
        String prefix = label + ": `";
        String value = null;
        for (String line : record.split("\\r?\\n")) {
            if (line.startsWith(prefix) && line.endsWith("`")) {
                assertTrue("Duplicate metadata label " + label, value == null);
                value = line.substring(prefix.length(), line.length() - 1);
            }
        }
        assertTrue("Missing metadata label " + label, value != null);
        return value;
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

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing system property: " + name);
        }
        return value;
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
