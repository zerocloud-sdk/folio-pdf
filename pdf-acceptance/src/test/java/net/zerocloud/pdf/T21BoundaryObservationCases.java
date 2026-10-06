package net.zerocloud.pdf;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.PageCount;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Retained real-process witnesses and paired allowed controls for T21, outside product runtime. */
public final class T21BoundaryObservationCases {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private static final byte[] SENTINEL = {17, 18, 19};

    @Test public void realLauncherAndIsolationHavePermittedControls() throws Exception {
        Path record = record("launcher"), parent = temporary.newFolder("owned-roots").toPath();
        Path marker = parent.resolve("worker-escape-probe");
        Files.write(marker, SENTINEL);
        assertArrayEquals(SENTINEL, Files.readAllBytes(marker));
        assertEquals(0, new ProcessBuilder("/bin/true").start().waitFor());
        try (ServerSocket server = new ServerSocket(0); Socket client = new Socket("127.0.0.1", server.getLocalPort());
                Socket accepted = server.accept()) {
            client.getOutputStream().write(1);
            assertEquals(1, accepted.getInputStream().read());
        }
        Path unix = record.resolve("unix-control.txt");
        Process control = new ProcessBuilder(python(), "-c",
                "import socket,tempfile,os\nwith tempfile.TemporaryDirectory(prefix='t21-unix-') as d:\n"
                + " p=os.path.join(d,'s');s=socket.socket(socket.AF_UNIX);s.bind(p);s.listen(1);c=socket.socket(socket.AF_UNIX);c.connect(p);"
                + "a,_=s.accept();c.sendall(b'1');assert a.recv(1)==b'1';a.close();c.close();s.close()\nprint('unix-permitted=pass')")
                .redirectErrorStream(true).redirectOutput(unix.toFile()).start();
        assertTrue(control.waitFor(20, TimeUnit.SECONDS)); assertEquals(0, control.exitValue());
        assertEquals("unix-permitted=pass", new String(Files.readAllBytes(unix), StandardCharsets.UTF_8).trim());
        final long[] pid = {0};
        final Path[] owned = {null};
        final boolean[] observedBeforeCleanup = {false};
        final int[] ownedFileCount = {0};
        HardenedWorkerSettings settings = HardenedWorkerSettings.builder().maximumHeapBytes(64L << 20)
                .maximumMessageBytes(1 << 20).build();
        DocumentWorkflow workflow = new DocumentWorkflow(WorkflowEnvironment.builder().temporaryDirectory(parent)
                .hardenedWorkerSettings(settings).build());
        Path target = temporary.getRoot().toPath().resolve("published.pdf");
        WorkflowRequest request = WorkflowRequest.builder().target("result", PublicationTarget.path(target)).saveMode(SaveMode.REWRITE)
                .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER).progressListener(phase -> {
                    if (phase == WorkflowProgressPhase.STAGED) {
                        try (java.util.stream.Stream<Path> paths = Files.walk(owned[0])) {
                            for (Path path : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) {
                                assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                                        Files.getPosixFilePermissions(path));
                                ownedFileCount[0]++;
                            }
                        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
                        assertTrue("A real staged Worker file must be observed", ownedFileCount[0] > 0);
                    }
                    if (phase == WorkflowProgressPhase.TARGET_COMMITTED) {
                        assertTrue(pid[0] > 0 && owned[0] != null);
                        assertFalse(Files.exists(Paths.get("/proc", Long.toString(pid[0]))));
                        assertTrue(Files.exists(owned[0]));
                        observedBeforeCleanup[0] = true;
                    }
                }).build();
        WorkflowOutcome<Integer> outcome = workflow.execute(request, session -> {
            session.execute(AddBlankPage.INSTANCE);
            HardenedWorkerEngine.IsolationProbe probe = HardenedWorkerEngine.probeIsolation(session);
            assertNotEquals(ManagementFactory.getRuntimeMXBean().getName(), probe.getWorkerProcessIdentity());
            assertTrue(probe.isOutboundNetworkDenied()); assertTrue(probe.isListeningNetworkDenied());
            assertTrue(probe.isUnixDomainConnectDenied()); assertTrue(probe.isUnixDomainListenDenied());
            assertTrue(probe.isDescendantProcessDenied()); assertTrue(probe.isFilesystemEscapeDenied());
            assertTrue(probe.isCallerClassPathDenied()); assertTrue(probe.isDeepReflectionDenied());
            assertTrue(probe.isNativePathLoadDenied()); assertTrue(probe.isNativeLibraryLoadDenied());
            pid[0] = Long.parseLong(probe.getWorkerProcessIdentity().split("@", 2)[0]);
            try {
                try (java.util.stream.Stream<Path> paths = Files.list(parent)) {
                    owned[0] = paths.filter(path -> path.getFileName().toString().startsWith(".folio-pdf-workflow-")).findFirst().get();
                }
                assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE), Files.getPosixFilePermissions(owned[0]));
                witness(record, pid[0], owned[0], settings);
                Path external = temporary.getRoot().toPath().resolve("external-link-marker");
                Files.write(external, SENTINEL);
                Files.delete(marker);
                Files.createLink(marker, external);
                assertArrayEquals(SENTINEL, Files.readAllBytes(marker));
                assertTrue(HardenedWorkerEngine.probeIsolation(session).isFilesystemEscapeDenied());
                assertArrayEquals(SENTINEL, Files.readAllBytes(external));
                Files.delete(marker);
                Files.createSymbolicLink(marker, external);
                assertArrayEquals(SENTINEL, Files.readAllBytes(marker));
                assertTrue(HardenedWorkerEngine.probeIsolation(session).isFilesystemEscapeDenied());
                assertArrayEquals(SENTINEL, Files.readAllBytes(external));
            } catch (Exception failure) {
                throw new AssertionError("T21 actual process witness failed", failure);
            }
            return session.query(PageCount.INSTANCE);
        });
        assertEquals(Integer.valueOf(1), outcome.getResult());
        assertEquals(WorkflowExecutionProfile.HARDENED_WORKER, outcome.getExecutionProfile());
        assertEquals(PublicationStatus.COMMITTED, outcome.getPublicationReceipts().get(0).getStatus());
        assertEquals("result", outcome.getPublicationReceipts().get(0).getTargetName());
        assertFalse(outcome.getPublicationReceipts().get(0).isPartialOutputPossible());
        assertTrue(observedBeforeCleanup[0]);
        assertFalse(Files.exists(Paths.get("/proc", Long.toString(pid[0]))));
        assertFalse(Files.exists(owned[0])); assertArrayEquals(SENTINEL, Files.readAllBytes(marker));
        Properties observations = new Properties();
        for (String key : Arrays.asList("permitted-filesystem", "permitted-descendant", "permitted-inet", "permitted-unix",
                "permitted-hard-link-access", "permitted-symbolic-link-access", "denied-hard-link-access", "denied-symbolic-link-access",
                "worker-separate", "owner-only-root", "owner-only-files", "denied-filesystem-read-write", "denied-descendant", "denied-inet",
                "denied-deep-reflection", "denied-native-load", "closed-classpath", "terminated-before-cleanup", "owned-root-removed")) {
            observations.setProperty(key, "pass");
        }
        int major = major();
        observations.setProperty("worker-unix", major >= 17 ? "security-denied" : "java-api-unavailable");
        observations.setProperty("owned-file-count", Integer.toString(ownedFileCount[0]));
        write(record.resolve("observations.properties"), observations);
    }

    @Test public void installedPolicyRejectsLinksAndUnixPermission() throws Exception {
        Path record = record("policy");
        List<String> command = new ArrayList<String>();
        command.add(java());
        if (HardenedWorkerEngine.requiresSecurityManagerAllowOption()) { command.add("-Djava.security.manager=allow"); }
        command.add("-cp"); command.add(System.getProperty("java.class.path"));
        command.add(T21PolicyQualificationCommand.class.getName()); command.add(record.toString());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(record.resolve("qualification.txt").toFile()).start();
        assertTrue(process.waitFor(20, TimeUnit.SECONDS)); assertEquals(0, process.exitValue());
        Properties actual = new Properties();
        try (java.io.InputStream input = Files.newInputStream(record.resolve("policy.properties"))) { actual.load(input); }
        assertEquals("net.zerocloud.pdf.HardenedWorkerSecurityManager", actual.getProperty("installed-manager"));
        for (String key : Arrays.asList("permitted-hard-link", "permitted-symbolic-link", "denied-hard-link", "denied-symbolic-link", "denied-unix-permission")) {
            assertEquals("pass", actual.getProperty(key));
        }
        assertEquals(major() >= 17 ? "available" : "unavailable", actual.getProperty("unix-java-api"));
        assertEquals(major() >= 17 ? "pass" : "api-unavailable", actual.getProperty("denied-unix-listen"));
    }

    @Test public void missingJavaAndUnsupportedOsRefuseWithoutPublication() throws Exception {
        Path record = record("prerequisites"), parent = temporary.newFolder("unavailable-roots").toPath();
        Path target = temporary.getRoot().toPath().resolve("sentinel");
        Files.write(target, SENTINEL);
        DocumentWorkflow workflow = new DocumentWorkflow(WorkflowEnvironment.builder().temporaryDirectory(parent).build());
        Properties observed = new Properties();
        for (String key : Arrays.asList("os.name", "java.home")) {
            String original = System.getProperty(key);
            try {
                System.setProperty(key, key.equals("os.name") ? "T21-unsupported-os" : parent.resolve("missing-java").toString());
                try { workflow.execute(request(target), session -> { throw new AssertionError("Unsupported launch ran caller work"); });
                    fail("Unsupported launch passed"); }
                catch (DocumentFailure failure) {
                    assertEquals(DocumentFailureCode.WORKER_UNAVAILABLE, failure.getCode());
                    observed.setProperty(key + ".code", failure.getCode().name());
                    observed.setProperty(key + ".diagnostic", failure.getDiagnostic());
                    observed.setProperty(key + ".receipts", unattemptedReceipt(failure));
                }
            } finally { System.setProperty(key, original); }
            assertArrayEquals(SENTINEL, Files.readAllBytes(target));
            empty(parent); observed.setProperty(key + ".cleanup", "pass"); observed.setProperty(key + ".sentinel", "unchanged");
        }
        write(record.resolve("observations.properties"), observed);
    }

    @Test public void crashAndMalformedResponseRetainSafeReceiptsAndCleanup() throws Exception {
        Path record = record("faults"), parent = temporary.newFolder("fault-roots").toPath();
        Path target = temporary.getRoot().toPath().resolve("sentinel");
        Files.write(target, SENTINEL);
        DocumentWorkflow workflow = new DocumentWorkflow(WorkflowEnvironment.builder().temporaryDirectory(parent).build());
        Properties observed = new Properties();
        for (String kind : Arrays.asList("crash", "malformed-response")) {
            final long[] pid = {0};
            try {
                workflow.execute(request(target), session -> {
                    pid[0] = Long.parseLong(HardenedWorkerEngine.probeIsolation(session).getWorkerProcessIdentity().split("@", 2)[0]);
                    if (kind.equals("crash")) { HardenedWorkerEngine.terminateWorkerForTest(session); session.query(PageCount.INSTANCE); }
                    else { HardenedWorkerEngine.requestMalformedResponseForTest(session); }
                    return null;
                });
                fail("Injected Worker fault passed");
            } catch (DocumentFailure failure) {
                assertEquals(kind.equals("crash") ? DocumentFailureCode.WORKER_TERMINATED : DocumentFailureCode.WORKER_PROTOCOL_REJECTED, failure.getCode());
                observed.setProperty(kind + ".code", failure.getCode().name());
                observed.setProperty(kind + ".diagnostic", failure.getDiagnostic());
                observed.setProperty(kind + ".receipts", unattemptedReceipt(failure));
            }
            assertTrue(pid[0] > 0); assertFalse(Files.exists(Paths.get("/proc", Long.toString(pid[0]))));
            assertArrayEquals(SENTINEL, Files.readAllBytes(target)); empty(parent);
            observed.setProperty(kind + ".child-terminated", "true"); observed.setProperty(kind + ".cleanup", "pass");
            observed.setProperty(kind + ".sentinel", "unchanged");
        }
        write(record.resolve("observations.properties"), observed);
    }

    @Test public void elapsedExpiryTerminatesBeforeOwnedRootCleanup() throws Exception {
        Path record = record("elapsed"), parent = temporary.newFolder("elapsed-roots").toPath();
        Path target = temporary.getRoot().toPath().resolve("sentinel"); Files.write(target, SENTINEL);
        WorkflowResourcePolicy defaults = WorkflowResourcePolicy.safeDefaults();
        WorkflowResourcePolicy policy = WorkflowResourcePolicy.builder().maximumInputBytes(defaults.getMaximumInputBytes())
                .maximumPages(defaults.getMaximumPages()).maximumObjects(defaults.getMaximumObjects())
                .maximumNestingDepth(defaults.getMaximumNestingDepth()).maximumDecompressedBytes(defaults.getMaximumDecompressedBytes())
                .maximumDecodedPixels(defaults.getMaximumDecodedPixels()).maximumOwnedMemoryBytes(defaults.getMaximumOwnedMemoryBytes())
                .maximumTemporaryStorageBytes(defaults.getMaximumTemporaryStorageBytes()).maximumElapsedTime(Duration.ofSeconds(3))
                .maximumConcurrentWorkflows(defaults.getMaximumConcurrentWorkflows()).build();
        DocumentWorkflow workflow = new DocumentWorkflow(WorkflowEnvironment.builder().temporaryDirectory(parent).defaultResourcePolicy(policy).build());
        Properties observed = new Properties();
        final long[] pid = {0};
        try {
            workflow.execute(request(target), session -> {
                pid[0] = Long.parseLong(HardenedWorkerEngine.probeIsolation(session).getWorkerProcessIdentity().split("@", 2)[0]);
                long stop = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (Files.exists(Paths.get("/proc", Long.toString(pid[0]))) && System.nanoTime() < stop) {
                    try { Thread.sleep(10); } catch (InterruptedException failure) { throw new AssertionError(failure); }
                }
                assertFalse(Files.exists(Paths.get("/proc", Long.toString(pid[0]))));
                try (java.util.stream.Stream<Path> roots = Files.list(parent)) { assertEquals(1, roots.count()); }
                catch (java.io.IOException failure) { throw new AssertionError(failure); }
                observed.setProperty("child-terminated-before-cleanup", "true");
                return session.query(PageCount.INSTANCE);
            });
            fail("Elapsed expiry passed");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.ELAPSED_TIME_LIMIT_EXCEEDED, failure.getCode());
            observed.setProperty("code", failure.getCode().name()); observed.setProperty("diagnostic", failure.getDiagnostic());
            observed.setProperty("receipts", unattemptedReceipt(failure));
        }
        assertTrue(pid[0] > 0); assertFalse(Files.exists(Paths.get("/proc", Long.toString(pid[0]))));
        assertArrayEquals(SENTINEL, Files.readAllBytes(target)); empty(parent);
        observed.setProperty("configured-elapsed-ms", "3000"); observed.setProperty("child-terminated", "true");
        observed.setProperty("cleanup", "pass"); observed.setProperty("sentinel", "unchanged");
        write(record.resolve("observations.properties"), observed);
    }

    private static void witness(Path record, long pid, Path owned, HardenedWorkerSettings settings) throws Exception {
        Path proc = Paths.get("/proc", Long.toString(pid));
        String[] arguments = new String(Files.readAllBytes(proc.resolve("cmdline")), StandardCharsets.UTF_8).split("\u0000");
        List<String> argv = Arrays.asList(arguments);
        assertTrue(argv.contains("-Xmx" + settings.getMaximumHeapBytes()));
        assertTrue(argv.contains("-XX:MaxDirectMemorySize=" + settings.getMaximumHeapBytes()));
        assertTrue(argv.contains("-Xss1m")); assertTrue(argv.contains(HardenedWorkerMain.class.getName()));
        int classpathIndex = argv.indexOf("-cp") + 1;
        assertEquals(HardenedWorkerEngine.workerClassPath(), arguments[classpathIndex]);
        assertEquals(0, Files.readAllBytes(proc.resolve("environ")).length);
        String limits = new String(Files.readAllBytes(proc.resolve("limits")), StandardCharsets.UTF_8);
        assertTrue(limits.matches("(?s).*Max cpu time\\s+300\\s+300\\s+seconds.*"));
        assertTrue(limits.matches("(?s).*Max open files\\s+64\\s+64\\s+files.*"));
        Files.write(record.resolve("cmdline.txt"), String.join("\n", arguments).getBytes(StandardCharsets.UTF_8));
        Files.write(record.resolve("limits.txt"), limits.getBytes(StandardCharsets.UTF_8));
        Files.write(record.resolve("environment.bin"), Files.readAllBytes(proc.resolve("environ")));
        Properties actual = new Properties();
        actual.setProperty("pid", Long.toString(pid)); actual.setProperty("root", owned.toString());
        actual.setProperty("java-executable", Files.readSymbolicLink(proc.resolve("exe")).toString());
        actual.setProperty("java-sha256", digest(Paths.get(actual.getProperty("java-executable"))));
        actual.setProperty("prlimit-executable", "/usr/bin/prlimit"); actual.setProperty("prlimit-sha256", digest(Paths.get("/usr/bin/prlimit")));
        actual.setProperty("java-vendor", System.getProperty("java.vendor")); actual.setProperty("java-build", System.getProperty("java.runtime.version"));
        actual.setProperty("heap-bytes", Long.toString(settings.getMaximumHeapBytes())); actual.setProperty("message-bytes", Integer.toString(settings.getMaximumMessageBytes()));
        actual.setProperty("owned-memory-bytes", Long.toString(WorkflowResourcePolicy.safeDefaults().getMaximumOwnedMemoryBytes()));
        actual.setProperty("cpu-seconds", "300"); actual.setProperty("open-files", "64"); actual.setProperty("root-mode", "0700");
        StringBuilder classpath = new StringBuilder();
        for (String entry : arguments[classpathIndex].split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            Path path = Paths.get(entry);
            assertTrue("Certification must exercise production JARs", Files.isRegularFile(path) && entry.endsWith(".jar"));
            classpath.append(entry).append('=').append(digest(path)).append('\n');
        }
        Files.write(record.resolve("classpath.properties"), classpath.toString().getBytes(StandardCharsets.UTF_8));
        Properties inventories = new Properties();
        for (String resource : Arrays.asList("document-worker-classes", "provider-contract-worker-classes")) {
            byte[] bytes;
            try (java.io.InputStream input = HardenedWorkerEngine.class.getClassLoader()
                    .getResourceAsStream("META-INF/folio-pdf/" + resource)) {
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int read;
                while ((read = input.read(buffer)) != -1) { output.write(buffer, 0, read); }
                bytes = output.toByteArray();
            }
            inventories.setProperty(resource + ".sha256", digest(bytes));
            inventories.setProperty(resource + ".entries", Integer.toString(new String(bytes, StandardCharsets.UTF_8).split("\n").length));
        }
        write(record.resolve("inventories.properties"), inventories);
        write(record.resolve("actual.properties"), actual);
    }

    private Path record(String name) throws Exception {
        String output = System.getProperty("folio.t21.observationOutput");
        Path root = output == null ? temporary.newFolder("observations").toPath() : Paths.get(output);
        Files.createDirectories(root);
        return Files.createDirectory(root.resolve(name));
    }
    private static void write(Path path, Properties value) throws Exception {
        try (java.io.OutputStream output = Files.newOutputStream(path)) { value.store(output, "Actual T21 boundary observation"); }
    }
    private static WorkflowRequest request(Path target) {
        return WorkflowRequest.builder().target("result", PublicationTarget.path(target)).saveMode(SaveMode.REWRITE)
                .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER).build();
    }
    private static String unattemptedReceipt(DocumentFailure failure) {
        assertNull(failure.getCause()); assertEquals(1, failure.getPublicationReceipts().size());
        PublicationReceipt receipt = failure.getPublicationReceipts().get(0);
        assertEquals("result", receipt.getTargetName()); assertEquals(PublicationStatus.NOT_ATTEMPTED, receipt.getStatus());
        assertFalse(receipt.isPartialOutputPossible());
        return receipt.getTargetName() + ":" + receipt.getStatus().name() + ":" + receipt.isPartialOutputPossible();
    }
    private static void empty(Path parent) throws Exception { try (java.util.stream.Stream<Path> paths = Files.list(parent)) { assertEquals(0, paths.count()); } }
    private static String java() { return Paths.get(System.getProperty("java.home"), "bin", "java").toString(); }
    private static String python() {
        String selected = System.getenv("FOLIO_FOUNDATION_PYTHON_ROOT");
        return Files.isExecutable(Paths.get("/usr/bin/python3.12")) ? "/usr/bin/python3.12"
                : selected == null ? "python3" : Paths.get(selected, "bin", "python3.12").toString();
    }
    private static int major() { String version = System.getProperty("java.specification.version"); return Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version); }
    private static String digest(Path path) throws Exception {
        return digest(Files.readAllBytes(path));
    }
    private static String digest(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder text = new StringBuilder(); for (byte value : digest) { text.append(String.format(java.util.Locale.ROOT, "%02x", value & 255)); } return text.toString();
    }
}
