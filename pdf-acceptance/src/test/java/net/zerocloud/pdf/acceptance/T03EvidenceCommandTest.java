package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Runs the qualified external tools at the repository-only recorder boundary. */
public final class T03EvidenceCommandTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void recordsFourIndependentChainsForNativeAndStableProducts() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot"));
        Path output = temporary.getRoot().toPath().resolve("evidence");
        T03EvidenceCommand.main(new String[] {root.toString(), output.toString(), "IN_PROCESS", "test"});
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T03 result");
        for (String chain : new String[] {"syntax", "standards", "semantic", "visual"}) {
            assertEquals(chain, "pass", result.required(chain));
            assertTrue(Files.size(output.resolve("native/" + chain + ".txt")) > 0);
            assertTrue(Files.size(output.resolve("facade/" + chain + ".txt")) > 0);
        }
        assertEquals("fail", PinProperties.load(output.resolve("negative/result.properties"),
                "negative controls").required("semantic"));
    }

    @Test public void perCheckerPassCannotHideAMissingRequiredRule() throws Exception {
        Path repository = Paths.get(System.getProperty("repositoryRoot"));
        Path root = temporary.newFolder("configuration").toPath();
        for (String relative : new String[] {"capabilities/profiles/T03-standards",
                "capabilities/expected/T03-document-blank-144dpi-srgb.png", "capabilities/profiles/T03-document-blank-visual.properties"}) {
            try (Stream<Path> files = Files.walk(repository.resolve(relative))) {
                for (Path source : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                    Path target = root.resolve(repository.relativize(source));
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target);
                }
            }
        }
        for (String tool : new String[] {"qpdf", "pdfium", "imagemagick", "pdfcpu", "arlington"}) {
            Path source = repository.resolve("scripts/" + tool + "-pin.properties");
            Properties values = new Properties();
            try (InputStream input = Files.newInputStream(source)) { values.load(input); }
            for (String key : values.stringPropertyNames()) {
                if (key.endsWith("EXECUTABLE") || key.equals("executable") || key.equals("model")) {
                    values.setProperty(key, source.getParent().resolve(values.getProperty(key)).normalize().toString());
                }
            }
            Path target = root.resolve("scripts/" + tool + "-pin.properties");
            Files.createDirectories(target.getParent());
            try (OutputStream output = Files.newOutputStream(target)) { values.store(output, "Test configuration"); }
        }
        Path profile = root.resolve("capabilities/profiles/T03-standards/arlington.properties");
        String original = new String(Files.readAllBytes(profile), StandardCharsets.UTF_8);
        Files.write(profile, original.replace(",pages-type-value\n", "\n").getBytes(StandardCharsets.UTF_8));
        Path output = temporary.getRoot().toPath().resolve("incomplete");
        try {
            T03EvidenceCommand.main(new String[] {root.toString(), output.toString(), "IN_PROCESS", "test"});
            fail("A rule removed from both per-checker lists must still block the T03 profile");
        } catch (IOException expected) {
            assertEquals("indeterminate", PinProperties.load(output.resolve("result.properties"),
                    "T03 result").required("standards"));
        }
    }
}
