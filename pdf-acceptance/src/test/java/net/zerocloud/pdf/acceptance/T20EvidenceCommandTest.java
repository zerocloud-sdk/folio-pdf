package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.InputStream;
import java.util.Properties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Execute the declared independent operands; expectations are never learned from Folio. */
public final class T20EvidenceCommandTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void fixedResourceExperimentsExecuteWithoutSearchingQuotas() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot", "..")).toAbsolutePath().normalize();
        Path output = temporary.getRoot().toPath().resolve("contracts");
        T20EvidenceCommand.main(new String[] {"contracts", root.toString(), output.toString(), "IN_PROCESS"});
        Properties observations = new Properties();
        try (InputStream input = Files.newInputStream(output.resolve("observations.properties"))) { observations.load(input); }
        assertEquals("IN_PROCESS", observations.getProperty("execution-profile"));
        assertEquals("75", observations.getProperty("case-count"));
        for (String name : observations.stringPropertyNames()) {
            if (name.endsWith(".result") || name.endsWith(".cleanup")) { assertEquals(name, "pass", observations.getProperty(name)); }
        }
    }

    @Test public void unsupportedExecutionCannotCreateCertification() throws Exception {
        Path output = temporary.getRoot().toPath().resolve("worker");
        try {
            T20EvidenceCommand.main(new String[] {temporary.getRoot().toString(), output.toString(), "HARDENED_WORKER", "0.1.0"});
            throw new AssertionError("T20 admitted an uncertified execution profile");
        } catch (IllegalArgumentException expected) { assertFalse(Files.exists(output)); }
    }
}
