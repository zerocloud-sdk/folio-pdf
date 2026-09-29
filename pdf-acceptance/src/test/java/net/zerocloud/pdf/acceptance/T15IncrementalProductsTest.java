package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

/** Producer assertions do not substitute for the independent certification chains. */
public final class T15IncrementalProductsTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    @Category(IndependentTools.class)
    public void publicRecorderRunsAllIndependentChainsAndControlsForEveryProduct() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot", "..")).toAbsolutePath().normalize();
        Path output = temporary.getRoot().toPath().resolve("certification");
        T15EvidenceCommand.main(new String[] {root.toString(), output.toString(), "IN_PROCESS", "0.1.0"});
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T15 complete recorder");
        assertEquals("certification", result.required("phase"));
        assertEquals("IN_PROCESS", result.required("native-execution-profile"));
        assertEquals("IN_PROCESS", result.required("facade-execution-profile"));
        assertEquals(EvidenceFiles.sha256(output.resolve("retained-files.sha256")),
                result.required("retained-files-sha256"));
        for (String chain : new String[] {"syntax", "standards", "semantic", "visual"}) {
            assertEquals("pass", result.required(chain));
            for (String api : new String[] {"native", "facade"}) {
                for (String product : T15IncrementalProducts.PRODUCTS) {
                    assertEquals("pass", result.required(api + "." + product + "." + chain));
                }
            }
        }
    }

    @Test
    public void actualPublicProductsMeetTheAuthoredExpectationsAndPreserveBothUnsignedRevisions() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot", "..")).toAbsolutePath().normalize();
        Path output = temporary.newFolder("products").toPath();
        T15IncrementalProducts.create(root, output, WorkflowExecutionProfile.IN_PROCESS).verify();
        for (String api : new String[] {"native", "facade"}) {
            byte[] original = Files.readAllBytes(root.resolve("capabilities/profiles/T15-signatures/unsigned.pdf"));
            byte[] first = Files.readAllBytes(output.resolve(api + "-unsigned-first/incremental.pdf"));
            byte[] second = Files.readAllBytes(output.resolve(api + "-unsigned-second/incremental.pdf"));
            assertTrue(first.length > original.length && second.length > first.length);
            assertArrayEquals(original, Arrays.copyOf(first, original.length));
            assertArrayEquals(first, Arrays.copyOf(second, first.length));
        }
        assertEquals(6, T15IncrementalProducts.PRODUCTS.length);
        assertTrue(Files.readAllBytes(output.resolve("products.properties")).length > 0);
    }

    @Test
    public void publicRecorderRejectsUnqualifiedReleaseAndExistingOutputBeforeReplacingEvidence() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot", "..")).toAbsolutePath().normalize();
        Path output = temporary.getRoot().toPath().resolve("record");
        try {
            T15EvidenceCommand.main(new String[] {root.toString(), output.toString(), "IN_PROCESS", "other"});
            fail("An unqualified release must not produce evidence");
        } catch (IllegalArgumentException expected) {
            assertEquals("T15 certifies the Foundation 0.1.0 candidate", expected.getMessage());
        }
        assertFalse(Files.exists(output));
        T15EvidenceCommand.main(new String[] {"products", root.toString(), output.toString(), "IN_PROCESS"});
        assertFalse(Files.exists(output.resolve("result.properties")));
        byte[] original = Files.readAllBytes(output.resolve("native-create/incremental.pdf"));
        try {
            T15EvidenceCommand.main(new String[] {"products", root.toString(), output.toString(), "IN_PROCESS"});
            fail("Existing evidence must not be overwritten");
        } catch (java.nio.file.FileAlreadyExistsException expected) {
            assertArrayEquals(original, Files.readAllBytes(output.resolve("native-create/incremental.pdf")));
        }
    }
}
