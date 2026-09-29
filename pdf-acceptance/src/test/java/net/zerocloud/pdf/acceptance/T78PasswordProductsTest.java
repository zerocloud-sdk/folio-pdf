package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

/** Public producer checks never substitute for actual independent qualification. */
public final class T78PasswordProductsTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void bothPublicApisProduceAndReopenEveryFrozenProduct() throws Exception {
        Path root = root(), output = temporary.newFolder("products").toPath();
        T78PasswordProducts.create(root, output, WorkflowExecutionProfile.IN_PROCESS).verify();
        Properties products = new Properties();
        try (InputStream input = Files.newInputStream(root.resolve("capabilities/profiles/T78-password/products.properties"))) { products.load(input); }
        for (String api : new String[] {"native", "facade"}) {
            for (String name : products.getProperty("cases").split(",")) {
                Path directory = output.resolve(api + "-" + name);
                assertTrue(Files.size(directory.resolve("product.pdf")) > 0);
                PinProperties record = PinProperties.load(directory.resolve("publication.properties"), "actual product");
                assertEquals("COMMITTED", record.required("status"));
                assertEquals(EvidenceFiles.sha256(directory.resolve("product.pdf")), record.required("output-sha256"));
                assertEquals("pass", record.required("reopened"));
            }
        }
        assertFalse(Files.exists(output.resolve("result.properties")));
    }

    @Test
    public void recorderRejectsAnUnqualifiedReleaseBeforeCreatingEvidence() throws Exception {
        Path output = temporary.getRoot().toPath().resolve("record");
        try {
            T78EvidenceCommand.main(new String[] {root().toString(), output.toString(), "IN_PROCESS", "other"});
            fail("An unqualified release must not produce evidence");
        } catch (IllegalArgumentException expected) { assertEquals("T78 certifies the Foundation 0.1.0 candidate", expected.getMessage()); }
        assertFalse(Files.exists(output));
    }

    @Test
    @Category(IndependentTools.class)
    public void actualIndependentChainsAndControlsPassForEveryProduct() throws Exception {
        Path output = temporary.getRoot().toPath().resolve("certification");
        T78EvidenceCommand.main(new String[] {root().toString(), output.toString(), "IN_PROCESS", "0.1.0"});
        PinProperties record = PinProperties.load(output.resolve("result.properties"), "T78 certification");
        for (String chain : new String[] {"syntax", "standards", "semantic", "visual"}) { assertEquals("pass", record.required(chain)); }
        assertEquals("IN_PROCESS", record.required("native-execution-profile"));
        assertEquals("IN_PROCESS", record.required("facade-execution-profile"));
        assertEquals(EvidenceFiles.sha256(output.resolve("retained-files.sha256")), record.required("retained-files-sha256"));
    }

    @Test
    public void coordinatorBudgetDoesNotRelaxIndividualToolLimits() throws Exception {
        Path java = Paths.get(System.getProperty("java.home"), "bin", "java");
        try {
            ExternalProcess.run(java, root(), 300001, 4096, "-version");
            fail("An individual tool must retain its five-minute maximum");
        } catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("300000")); }
        assertEquals(0, ExternalProcess.runCoordinator(java, root(), 900000, 4096, "-version").exitCode);
        try {
            ExternalProcess.runCoordinator(java, root(), 900001, 4096, "-version");
            fail("A coordinator must retain its fifteen-minute maximum");
        } catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("900000")); }
    }

    @Test
    public void coordinatorLimitsRemoveItsPrivateFiles() throws Exception {
        for (String mode : new String[] {"sleep", "overflow"}) {
            Path marker = temporary.getRoot().toPath().resolve(mode);
            try {
                runPrivateProbe(marker, mode, 5000);
                fail("The coordinator must stop at its bound");
            } catch (ExternalProcess.LimitExceededException expected) {
                assertTrue(expected.getMessage().contains("sleep".equals(mode) ? "time limit" : "output limit"));
            }
            assertPrivateFilesRemoved(marker);
        }
    }

    @Test
    public void interruptedCoordinatorRemovesItsPrivateFilesBeforeReturning() throws Exception {
        final Path marker = temporary.getRoot().toPath().resolve("interrupted");
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread caller = new Thread(new Runnable() {
            @Override public void run() {
                try { runPrivateProbe(marker, "sleep", 20000); }
                catch (Throwable stopped) { failure.set(stopped); }
            }
        });
        caller.start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(marker) && caller.isAlive() && System.nanoTime() < deadline) { Thread.sleep(10); }
            assertTrue("The coordinator must have started before cancellation", Files.exists(marker));
        } finally {
            caller.interrupt();
            caller.join(12000);
        }
        assertFalse("The cancelled caller must finish", caller.isAlive());
        assertTrue("Cancellation must remain observable", failure.get() instanceof InterruptedException);
        assertPrivateFilesRemoved(marker);
    }

    private static void runPrivateProbe(Path marker, String mode, long timeout) throws Exception {
        Path java = Paths.get(System.getProperty("java.home"), "bin", "java");
        String classes = Paths.get(T78PasswordProductsTest.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toString();
        ExternalProcess.runCoordinator(java, root(), timeout, 1024, "-cp", classes,
                PrivateProbe.class.getName(), marker.toString(), mode);
    }

    private static void assertPrivateFilesRemoved(Path marker) throws Exception {
        assertTrue(Files.exists(marker));
        Path privateDirectory = Paths.get(new String(Files.readAllBytes(marker), StandardCharsets.UTF_8));
        assertFalse("Private files must be removed before the caller returns", Files.exists(privateDirectory));
    }

    /** A real child process intentionally leaves its files to the Java owner. */
    public static final class PrivateProbe {
        public static void main(String[] arguments) throws Exception {
            Path directory = Paths.get(System.getenv("TMPDIR"));
            Files.write(Files.createDirectory(directory.resolve("nested")).resolve("private"), new byte[] {1, 2, 3});
            Files.write(Paths.get(arguments[0]), directory.toString().getBytes(StandardCharsets.UTF_8));
            if ("overflow".equals(arguments[1])) {
                System.out.write(new byte[4096]);
                System.out.flush();
            }
            Thread.sleep(60000);
        }
    }

    private static Path root() { return Paths.get(System.getProperty("repositoryRoot", "..")).toAbsolutePath().normalize(); }
}
