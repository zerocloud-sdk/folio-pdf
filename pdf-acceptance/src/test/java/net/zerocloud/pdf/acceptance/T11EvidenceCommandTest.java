package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.PageObjectReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

/** Public recorder CLI and reopened Native observations, without backend seams. */
public final class T11EvidenceCommandTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    @Category(IndependentTools.class)
    public void observeCommandBindsFourSeparateChainsAndAll170QualifiedRulesToOneProduct() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path products = temporary.getRoot().toPath().resolve("observed-products");
        T11EvidenceCommand.main(new String[] {"products", root.toString(), products.toString(), "IN_PROCESS"});
        Path pdf = products.resolve("native-edited/metadata.pdf");
        Path output = temporary.getRoot().toPath().resolve("observed");
        T11EvidenceCommand.main(new String[] {"observe", root.toString(), pdf.toString(), "edited",
            output.toString(), "IN_PROCESS", "test"});
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T11 observation");
        assertEquals(EvidenceFiles.sha256(pdf), result.required("input-sha256"));
        assertEquals("170", result.required("standards-rule-count"));
        assertEquals("IN_PROCESS", result.required("execution-profile"));
        for (String chain : new String[] {"syntax", "standards", "semantic", "visual"}) {
            assertEquals(chain, "pass", result.required(chain));
            assertTrue(Files.size(output.resolve(chain + ".txt")) > 0);
        }
        for (String checker : new String[] {"pdfcpu", "arlington-core", "arlington-metadata"}) {
            PinProperties record = PinProperties.load(output.resolve(checker + "/standards.properties"), checker);
            assertEquals("pass", record.required("result"));
            assertEquals(EvidenceFiles.sha256(pdf), record.required("input-sha256"));
        }
    }

    @Test
    public void safetyCommandProvesXmlRejectionWithoutExternalAccessOrMutation() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path output = temporary.getRoot().toPath().resolve("xml-safety");

        T11EvidenceCommand.main(new String[] {
            "safety", root.toString(), output.toString(), "IN_PROCESS"});

        PinProperties overall = PinProperties.load(
                output.resolve("result.properties"), "T11 XML safety");
        assertEquals("pass", overall.required("result"));
        for (String api : new String[] {"native", "facade"}) {
            PinProperties result = PinProperties.load(
                    output.resolve(api + "/safety.properties"), api + " XML safety");
            assertEquals("pass", result.required("result"));
            assertEquals("IN_PROCESS", result.required("execution-profile"));
            assertEquals("1", result.required("observer.file-attempts"));
            assertEquals("1", result.required("observer.network-attempts"));
            for (String scenario : new String[] {"external-file", "external-network", "malformed"}) {
                assertEquals("COMMAND_REJECTED", result.required(scenario + ".code"));
                assertEquals("0", result.required(scenario + ".file-accesses"));
                assertEquals("0", result.required(scenario + ".network-accesses"));
                assertEquals("pass", result.required(scenario + ".unmutated"));
                assertEquals("pass", result.required(scenario + ".reopened"));
            }
            assertEquals("pass", result.required("inert-xinclude.accepted"));
            assertEquals("pass", result.required("inert-xinclude.exact"));
            assertEquals("pass", result.required("inert-xinclude.reopened"));
            assertEquals("0", result.required("inert-xinclude.file-accesses"));
            assertEquals("0", result.required("inert-xinclude.network-accesses"));
        }
    }

    @Test
    public void safetyCommandBindsHardenedWorkerObservationsToTheSelectedProfile() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path output = temporary.getRoot().toPath().resolve("worker-safety");

        T11EvidenceCommand.main(new String[] {
            "safety", root.toString(), output.toString(), "HARDENED_WORKER"});

        PinProperties overall = PinProperties.load(
                output.resolve("result.properties"), "T11 worker safety");
        assertEquals("HARDENED_WORKER",
                overall.required("native-execution-profile"));
        assertEquals("IN_PROCESS",
                overall.required("facade-execution-profile"));
        PinProperties nativeXml = PinProperties.load(
                output.resolve("native/safety.properties"),
                "T11 Native worker XML safety");
        assertEquals("HARDENED_WORKER",
                nativeXml.required("execution-profile"));
        assertEquals("denied",
                nativeXml.required("worker-file-positive-control"));
        assertEquals("denied",
                nativeXml.required("worker-network-positive-control"));
        assertEquals("pass",
                nativeXml.required("worker-process-isolated"));
        PinProperties signed = PinProperties.load(
                output.resolve("signed.properties"),
                "T11 Native worker signed safety");
        assertEquals("HARDENED_WORKER",
                signed.required("native.execution-profile"));
        assertEquals("IN_PROCESS",
                signed.required("facade.execution-profile"));
    }

    @Test
    @Category(IndependentTools.class)
    public void visualCommandObservesEveryEditedPageAgainstFrozenOriginalPixels() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path products = temporary.getRoot().toPath().resolve("visual-products");
        T11EvidenceCommand.main(new String[] {"products", root.toString(), products.toString(), "IN_PROCESS"});
        Path pdf = products.resolve("native-edited/metadata.pdf");
        Path output = temporary.getRoot().toPath().resolve("visual");
        T11EvidenceCommand.main(new String[] {"visual", root.toString(), pdf.toString(), "edited", output.toString(), "test"});
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T11 visual");
        assertEquals("pass", result.required("visual"));
        assertEquals(EvidenceFiles.sha256(pdf), result.required("input-sha256"));
        assertEquals("4", result.required("page-count"));
        for (int page = 1; page <= 4; page++) {
            assertEquals("pass", result.required("page." + page + ".visual"));
            String record = new String(Files.readAllBytes(output.resolve("page-" + page + "-visual.md")),
                    StandardCharsets.UTF_8);
            assertTrue(record.contains("document.metadata.outlines-destinations-attachments"));
            assertTrue(record.contains(EvidenceFiles.sha256(pdf)));
            assertTrue(Files.size(output.resolve("page-" + page + "-visual.txt")) > 0);
        }
    }

    @Test
    public void semanticCommandChecksAllFrozenProductsAndDetectsFiveActualMetadataChanges() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path products = temporary.getRoot().toPath().resolve("semantic-products");
        T11EvidenceCommand.main(new String[] {"products", root.toString(), products.toString(), "IN_PROCESS"});
        for (String api : new String[] {"native", "facade"}) {
            for (String product : new String[] {"edited", "merged", "left", "right"}) {
                Path pdf = products.resolve(api + "-" + product + "/metadata.pdf");
                Path result = temporary.getRoot().toPath().resolve("semantic-" + api + "-" + product);
                T11EvidenceCommand.main(new String[] {"semantic", root.toString(), pdf.toString(), product,
                    result.toString(), "IN_PROCESS"});
                PinProperties observed = PinProperties.load(result.resolve("result.properties"), "T11 semantic");
                assertEquals(observed.required("finding"), "pass", observed.required("semantic"));
                assertEquals(EvidenceFiles.sha256(pdf), observed.required("input-sha256"));
                assertEquals("IN_PROCESS", observed.required("execution-profile"));
            }
        }
        for (String change : new String[] {"target", "operand", "packet", "payload", "retained-info"}) {
            Path wrong = temporary.getRoot().toPath().resolve("wrong-" + change + ".pdf");
            new DocumentWorkflow().execute(WorkflowRequest.builder()
                    .source("input", DocumentSource.path(products.resolve("native-edited/metadata.pdf")))
                    .primarySource("input").target("out", net.zerocloud.pdf.PublicationTarget.path(wrong))
                    .outputPolicy(net.zerocloud.pdf.PdfOutputPolicy.version(net.zerocloud.pdf.PdfVersion.PDF_2_0))
                    .saveMode(SaveMode.REWRITE).build(), session -> {
                if ("target".equals(change) || "operand".equals(change)) {
                    session.execute(net.zerocloud.pdf.command.SetNamedDestinations.version1().set("xyz",
                            net.zerocloud.pdf.PageDestination.xyz("target".equals(change) ? 2 : 1,
                                    BigDecimal.valueOf("operand".equals(change) ? 11 : 10), null, BigDecimal.valueOf(2))).build());
                } else if ("packet".equals(change)) {
                    session.execute(net.zerocloud.pdf.command.SetXmpMetadata.version1(
                            new String(T11MetadataProducts.editedXmp(), StandardCharsets.UTF_8)
                                    .replace("edited", "tampered").getBytes(StandardCharsets.UTF_8)));
                } else if ("payload".equals(change)) {
                    session.execute(net.zerocloud.pdf.command.EmbedFile.version1(net.zerocloud.pdf.EmbeddedFile.version1(
                            "payload.txt", new byte[] {9}, "application/octet-stream", "Edited payload",
                            net.zerocloud.pdf.EmbeddedFile.Relationship.SOURCE)));
                } else {
                    session.execute(net.zerocloud.pdf.command.UpdateDocumentInfo.version1().set("T73Keep",
                            net.zerocloud.pdf.PdfString.of("tampered".getBytes(StandardCharsets.US_ASCII))).build());
                }
                return null;
            });
            Path result = temporary.getRoot().toPath().resolve("negative-" + change);
            T11EvidenceCommand.main(new String[] {"semantic", root.toString(), wrong.toString(), "edited",
                result.toString(), "IN_PROCESS"});
            PinProperties observed = PinProperties.load(result.resolve("result.properties"), "T11 semantic control");
            assertEquals(change + ": " + observed.required("finding"), "fail", observed.required("semantic"));
            assertEquals(EvidenceFiles.sha256(wrong), observed.required("input-sha256"));
        }
    }

    @Test
    public void bothInterfacesProduceFrozenSequencesAndOrderedReceiptsWithoutChangingSources() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path output = temporary.getRoot().toPath().resolve("products");
        Path primary = root.resolve("capabilities/profiles/T11-metadata/fixtures/primary.pdf");
        Path appendix = root.resolve("capabilities/profiles/T11-metadata/fixtures/appendix.pdf");
        String primaryHash = EvidenceFiles.sha256(primary);
        String appendixHash = EvidenceFiles.sha256(appendix);

        T11EvidenceCommand.main(new String[] {"products", root.toString(), output.toString(), "IN_PROCESS"});

        for (String api : new String[] {"native", "facade"}) {
            assertPages(output.resolve(api + "-edited/metadata.pdf"), "C", "A", "B", "A");
            assertPages(output.resolve(api + "-merged/metadata.pdf"), "A", "B", "C", "D");
            assertPages(output.resolve(api + "-left/metadata.pdf"), "A", "B");
            assertPages(output.resolve(api + "-right/metadata.pdf"), "C", "D");
            PinProperties edited = PinProperties.load(output.resolve(api + "-edited-publication.properties"), "edited receipt");
            assertEquals("DESTINATION_CONFLICT", edited.required("orphan-rejection-code"));
            assertEquals("document.metadata.outlines-destinations-attachments", edited.required("orphan-rejection-capability"));
            assertEquals("1", edited.required("receipt-count"));
            assertEquals("COMMITTED", edited.required("receipt.0.status"));
            PinProperties split = PinProperties.load(output.resolve(api + "-split-publication.properties"), "split receipts");
            assertEquals("IN_PROCESS", split.required("execution-profile"));
            assertEquals("COMMAND_REJECTED", split.required("terminal-command-code"));
            assertEquals("3", split.required("receipt-count"));
            for (int index = 0; index < 3; index++) {
                String product = new String[] {"left", "merged", "right"}[index];
                Path pdf = output.resolve(api + "-" + product + "/metadata.pdf");
                assertEquals(product, split.required("receipt." + index + ".target"));
                assertEquals("COMMITTED", split.required("receipt." + index + ".status"));
                assertEquals(pdf.toString(), split.required("receipt." + index + ".path"));
                assertEquals(EvidenceFiles.sha256(pdf), split.required("receipt." + index + ".pdf-sha256"));
                assertEquals("false", split.required("receipt." + index + ".partial-output-possible"));
            }
        }
        assertEquals(primaryHash, EvidenceFiles.sha256(primary));
        assertEquals(appendixHash, EvidenceFiles.sha256(appendix));
        PinProperties result = PinProperties.load(output.resolve("products.properties"), "product phase");
        assertEquals("products-only", result.required("phase"));
        assertFalse(Files.exists(output.resolve("result.properties")));
        assertTrue(Files.size(output.resolve("native-edited/metadata.pdf")) > 0);
    }

    @Test
    @Category(IndependentTools.class)
    public void fullCommandCertifiesBothInterfacesAndRetainsAllRealControls() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path output = temporary.getRoot().toPath().resolve("full-t11");

        T11EvidenceCommand.main(new String[] {
            root.toString(), output.toString(), "IN_PROCESS", "test"});

        PinProperties overall = PinProperties.load(
                output.resolve("result.properties"), "full T11 result");
        for (String chain : new String[] {"syntax", "standards", "semantic", "visual", "safety"}) {
            assertEquals(chain, "pass", overall.required(chain));
        }
        for (String api : new String[] {"native", "facade"}) {
            for (String product : new String[] {"edited", "merged", "left", "right"}) {
                Path directory = output.resolve(api + "-" + product);
                PinProperties result = PinProperties.load(
                        directory.resolve("result.properties"), api + " " + product);
                assertEquals(EvidenceFiles.sha256(directory.resolve("metadata.pdf")),
                        result.required("input-sha256"));
                assertEquals("170", result.required("standards-rule-count"));
                assertEquals("IN_PROCESS", result.required("execution-profile"));
                for (String chain : new String[] {"syntax", "standards", "semantic", "visual"}) {
                    assertEquals(api + " " + product + " " + chain,
                            "pass", result.required(chain));
                }
            }
        }
        PinProperties controls = PinProperties.load(
                output.resolve("negative/result.properties"), "T11 negative controls");
        for (String chain : new String[] {"syntax", "standards", "semantic", "visual"}) {
            assertEquals(chain + " control", "fail", controls.required(chain));
        }
        PinProperties safety = PinProperties.load(
                output.resolve("safety/result.properties"), "T11 safety evidence");
        assertEquals("pass", safety.required("result"));
        assertEquals("pass", safety.required("signed"));
        PinProperties signed = PinProperties.load(
                output.resolve("safety/signed.properties"), "T11 signed protection");
        assertEquals("SIGNED_REWRITE_REJECTED", signed.required("native.code"));
        assertEquals("SIGNED_REWRITE_REJECTED", signed.required("facade.code"));
        assertEquals("pass", signed.required("source-unchanged"));
        assertEquals("pass", signed.required("native.target-unchanged"));
        assertEquals("pass", signed.required("facade.target-unchanged"));
    }

    private static void assertPages(Path path, String... expected) throws Exception {
        List<String> observed = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .source("input", DocumentSource.path(path)).primarySource("input").saveMode(SaveMode.REWRITE).build(), session -> {
            List<String> pages = new ArrayList<String>();
            for (int number = 1; number <= session.query(PageCount.INSTANCE); number++) {
                PdfDictionary page = (PdfDictionary) session.query(InspectObject.version1(
                        session.query(PageObjectReference.version1(number)), PdfInspectionLimits.of(1000, 0)));
                pages.add(((PdfName) page.get(PdfName.of("T73Marker"))).getValue());
            }
            return pages;
        }).getResult();
        assertEquals(Arrays.asList(expected), observed);
    }
}
