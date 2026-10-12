package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

/** Public producer checks never substitute for actual independent qualification. */
public final class T80PasswordProductsTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void bothPublicApisProduceAndReopenEveryFrozenProduct() throws Exception {
        Path root = root(), output = temporary.newFolder("products").toPath();
        T80PasswordProducts.create(root, output, WorkflowExecutionProfile.IN_PROCESS).verify();
        Properties products = new Properties();
        try (InputStream input = Files.newInputStream(root.resolve("capabilities/profiles/T80-embedded-files-only/products.properties"))) { products.load(input); }
        for (String api : new String[] {"native", "facade"}) {
            for (String name : products.getProperty("cases").split(",")) {
                if ("facade".equals(api) && "native".equals(products.getProperty(name + ".apis"))) { continue; }
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
            T80EvidenceCommand.main(new String[] {root().toString(), output.toString(), "IN_PROCESS", "other"});
            fail("An unqualified release must not produce evidence");
        } catch (IllegalArgumentException expected) { assertEquals("T80 certifies the Foundation 0.1.0 candidate", expected.getMessage()); }
        assertFalse(Files.exists(output));
    }

    @Test
    public void attachmentBatchHasASeparateFiniteCoordinatorBudget() throws Exception {
        Path java = Paths.get(System.getProperty("java.home"), "bin", "java");
        assertEquals(0, ExternalProcess.runCoordinator(java, root(), 1800000, 4096,
                ExternalProcess.CoordinatorBudget.THIRTY_MINUTES, "-version").exitCode);
        try {
            ExternalProcess.runCoordinator(java, root(), 1800001, 4096,
                    ExternalProcess.CoordinatorBudget.THIRTY_MINUTES, "-version");
            fail("The attachment batch must retain its thirty-minute maximum");
        } catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("1800000")); }
        try {
            ExternalProcess.runCoordinator(java, root(), 900001, 4096, "-version");
            fail("The default coordinator must retain its fifteen-minute maximum");
        } catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("900000")); }
        try {
            ExternalProcess.run(java, root(), 300001, 4096, "-version");
            fail("An individual tool must retain its five-minute maximum");
        } catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("300000")); }
    }

    @Test
    @Category(IndependentTools.class)
    public void actualIndependentChainsAndControlsPassForEveryProduct() throws Exception {
        Path output = Paths.get(System.getProperty("folio.t80.independentOutput",
                temporary.getRoot().toPath().resolve("certification").toString()));
        T80EvidenceCommand.main(new String[] {root().toString(), output.toString(), "IN_PROCESS", "0.1.0"});
        PinProperties record = PinProperties.load(output.resolve("result.properties"), "T80 certification");
        for (String chain : new String[] {"syntax", "standards", "semantic", "visual"}) { assertEquals("pass", record.required(chain)); }
        assertEquals("IN_PROCESS", record.required("native-execution-profile"));
        assertEquals("IN_PROCESS", record.required("facade-execution-profile"));
        assertEquals(EvidenceFiles.sha256(output.resolve("retained-files.sha256")), record.required("retained-files-sha256"));
    }

    private static Path root() { return Paths.get(System.getProperty("repositoryRoot", "..")).toAbsolutePath().normalize(); }
}
