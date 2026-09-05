package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Exercises first-use font discovery without the test suite's ambient cache. */
public final class RenderingColdStartTest {
    private static final String PRIVATE_FONT = "PRIVATE_UNREADABLE_INSTALLED_FONT.ttf";

    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void unreadableInstalledFontDoesNotFailOrLeakFromFirstRender() throws Exception {
        Path home = temporary.newFolder("home").toPath();
        Path fonts = Files.createDirectories(home.resolve(".fonts"));
        // Project-authored sfnt header without the mandatory font tables.
        Files.write(fonts.resolve(PRIVATE_FONT),
                new byte[] {0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        Path output = temporary.getRoot().toPath().resolve("child.log");
        Process child = new ProcessBuilder(
                Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                "-Duser.home=" + home,
                "-Dpdfbox.fontcache=" + home,
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                RenderingColdStartTest.class.getName())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue("The cold-start rendering process did not exit", child.waitFor(30L, TimeUnit.SECONDS));
            String logs = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
            assertEquals(logs, 0, child.exitValue());
            assertFalse(logs, logs.contains(PRIVATE_FONT));
            // This verifies that the isolated first-use scan actually visited
            // the malformed font, even on machines with no other invalid fonts.
            String cache = new String(Files.readAllBytes(home.resolve(".pdfbox.cache")), StandardCharsets.UTF_8);
            assertTrue("The malformed installed font was not scanned", cache.contains(PRIVATE_FONT));
        } finally {
            child.destroyForcibly();
            child.waitFor();
        }
    }

    public static void main(String[] args) throws Exception {
        // Reuse the public Workflow assertions in a fresh VM: rendering must
        // succeed with FONT_SUBSTITUTED, reject a missing resource, and preserve
        // the caller's JUL filters without exposing either private marker.
        new RenderingWorkflowContractTest(WorkflowExecutionProfile.IN_PROCESS)
                .defaultLogsKeepFontAndMissingResourceNamesPrivate();
    }
}
