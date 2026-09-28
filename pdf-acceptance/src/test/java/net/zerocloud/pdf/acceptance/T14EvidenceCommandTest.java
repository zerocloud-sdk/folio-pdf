package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Public producer contracts; ordinary tests are not independent certification. */
public final class T14EvidenceCommandTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void productsRetainDetachedAndReopenedObservationsForBothApisWithoutClaimingCertification() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path output = temporary.getRoot().toPath().resolve("products");
        String[] command = {"products", root.toString(), output.toString(), "IN_PROCESS"};
        T14EvidenceCommand.main(command);
        assertEquals("products-only", PinProperties.load(output.resolve("products.properties"), "T14 products").required("phase"));
        assertFalse(Files.exists(output.resolve("result.properties")));
        for (String api : new String[] {"native", "facade"}) {
            for (String name : new String[] {"inventory", "filters", "formats", "colors", "classifications"}) {
                Path product = output.resolve(api + "-" + name);
                PinProperties receipt = PinProperties.load(product.resolve("publication.properties"), "T14 receipt");
                assertEquals("IN_PROCESS", receipt.required("execution-profile"));
                assertEquals("COMMITTED", receipt.required("status"));
                assertEquals("false", receipt.required("partial-output-possible"));
                assertEquals(EvidenceFiles.sha256(product.resolve("extraction.pdf")), receipt.required("output-sha256"));
                assertEquals("pass", receipt.required("source-preserved"));
                assertEquals("pass", receipt.required("reopened"));
                assertTrue(Files.size(product.resolve("observation.properties")) > 100);
                assertTrue(Files.size(product.resolve("reopened.properties")) > 100);
            }
        }
        String original = EvidenceFiles.sha256(output.resolve("native-inventory/extraction.pdf"));
        try {
            T14EvidenceCommand.main(command);
            fail("A completed product directory must not be overwritten");
        } catch (java.nio.file.FileAlreadyExistsException expected) {
            assertEquals(original, EvidenceFiles.sha256(output.resolve("native-inventory/extraction.pdf")));
        }
    }

    @Test
    public void alteredExpectedCorpusRejectsBeforeCreatingPublicProducts() throws Exception {
        Path repository = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path root = temporary.getRoot().toPath().resolve("repository");
        Path corpus = Files.createDirectories(root.resolve("capabilities/profiles/T14-images"));
        for (String name : new String[] {"corpus.json", "corpus.properties"}) {
            Files.copy(repository.resolve("capabilities/profiles/T14-images/" + name), corpus.resolve(name));
        }
        Files.write(corpus.resolve("corpus.properties"), new byte[] {'\n'}, StandardOpenOption.APPEND);
        Path output = temporary.getRoot().toPath().resolve("altered");
        try {
            T14EvidenceCommand.main(new String[] {"products", root.toString(), output.toString(), "IN_PROCESS"});
            fail("A changed expectation must not be observed as a new baseline");
        } catch (IOException expected) {
            assertEquals("T14 frozen corpus identity mismatch", expected.getMessage());
        }
        assertFalse(Files.exists(output));
    }

    @Test
    public void unqualifiedReleaseRejectsBeforeCreatingEvidence() throws Exception {
        Path output = temporary.getRoot().toPath().resolve("wrong-release");
        try {
            T14EvidenceCommand.main(new String[] {System.getProperty("repositoryRoot"), output.toString(), "IN_PROCESS", "other"});
            fail("An unqualified release must not produce evidence");
        } catch (IllegalArgumentException expected) {
            assertEquals("T14 certifies the Foundation 0.1.0 candidate", expected.getMessage());
        }
        assertFalse(Files.exists(output));
    }
}
