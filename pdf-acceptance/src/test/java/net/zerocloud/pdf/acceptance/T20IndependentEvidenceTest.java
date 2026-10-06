package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertEquals;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

/** Mandatory real-tool execution is invoked separately on the actual Ubuntu image. */
@Category(IndependentTools.class)
public final class T20IndependentEvidenceTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void everyRequiredChainAndQualifiedControlExecutes() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot", "..")).toAbsolutePath().normalize();
        Path output = temporary.getRoot().toPath().resolve("evidence");
        T20EvidenceCommand.main(new String[] {root.toString(), output.toString(), "IN_PROCESS", "0.1.0"});
        Properties result = new Properties();
        try (InputStream input = Files.newInputStream(output.resolve("result.properties"))) { result.load(input); }
        for (String chain : new String[] {"syntax", "standards", "semantic", "visual", "contract"}) {
            assertEquals(chain, "pass", result.getProperty(chain));
        }
    }
}
