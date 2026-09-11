package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.zerocloud.pdf.Annotation;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.Annotations;
import net.zerocloud.pdf.query.PageCount;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

/** Exercises the public evidence command and public reopened document observations. */
public final class T12EvidenceCommandTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    @Category(IndependentTools.class)
    public void pixelControlQualifiesOneChangedPixelAtTheFrozenZeroThreshold() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path output = temporary.getRoot().toPath().resolve("pixel");
        T12EvidenceCommand.main(new String[] {"pixel", root.toString(), output.toString()});
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T12 exact pixel control");
        assertEquals("fail", result.required("visual"));
        assertEquals("0", result.required("positive-absolute-error"));
        assertEquals("1", result.required("negative-absolute-error"));
        assertEquals("0", result.required("threshold"));
        assertEquals("0", result.required("fuzz-percent"));
        assertTrue(Files.size(output.resolve("difference.png")) > 0);
        assertTrue(Files.size(output.resolve("comparator.txt")) > 0);
    }

    @Test
    @Category(IndependentTools.class)
    public void completeCommandRequiresAllSixteenProductsEveryChainEffectiveControlsAndSafety() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path output = Paths.get(System.getProperty("folio.t12.fullOutput", temporary.getRoot().toPath().resolve("complete").toString()));
        T12EvidenceCommand.main(new String[] {root.toString(), output.toString(), "IN_PROCESS", "test"});
        PinProperties overall = PinProperties.load(output.resolve("result.properties"), "T12 complete evidence");
        assertEquals("certification", PinProperties.load(output.resolve("products.properties"), "T12 phases").required("phase"));
        assertEquals("pass", overall.required("safety"));
        PinProperties negative = PinProperties.load(output.resolve("negative/result.properties"), "T12 controls");
        for (String chain : new String[] {"syntax", "standards", "semantic", "visual"}) {
            assertEquals("pass", overall.required(chain));
            assertEquals("fail", negative.required(chain));
            for (String api : new String[] {"native", "facade"}) {
                for (String product : new String[] {"created", "changed", "flattened", "copied", "merged", "adopted", "left", "right"}) {
                    assertEquals("pass", overall.required(api + "." + product + "." + chain));
                    Path directory = output.resolve(api + "-" + product);
                    PinProperties record = PinProperties.load(directory.resolve("result.properties"), "T12 product");
                    assertEquals(EvidenceFiles.sha256(directory.resolve("annotations.pdf")), record.required("input-sha256"));
                    assertEquals("IN_PROCESS", record.required("execution-profile"));
                }
            }
        }
    }

    @Test
    @Category(IndependentTools.class)
    public void safetyCommandObservesInertPreservationAtomicRejectionReplacementAndSignedProtectionInBothModes() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        for (String mode : new String[] {"IN_PROCESS", "HARDENED_WORKER"}) {
            Path output = temporary.getRoot().toPath().resolve("safety-" + mode);
            T12EvidenceCommand.main(new String[] {"safety", root.toString(), output.toString(), mode});
            PinProperties record = PinProperties.load(output.resolve("result.properties"), "T12 safety");
            assertEquals("pass", record.required("result"));
            assertEquals(mode, record.required("native-execution-profile"));
            assertEquals("IN_PROCESS", record.required("facade-execution-profile"));
            for (String api : new String[] {"native", "facade"}) {
                for (String graph : new String[] {"unknown", "chained"}) {
                    String key = api + "." + graph + ".";
                    assertEquals("pass", record.required(key + "preserved"));
                    assertEquals("QUERY_FAILED", record.required(key + "query-code"));
                    assertEquals("PRESERVATION_UNSUPPORTED", record.required(key + "copy-code"));
                    assertEquals("pass", record.required(key + "replaced"));
                    assertEquals("pass", record.required(key + "atomic"));
                }
                assertEquals("SIGNED_REWRITE_REJECTED", record.required(api + ".signed-code"));
                assertEquals("pass", record.required(api + ".signed-target-unchanged"));
            }
            PinProperties effects = PinProperties.load(output.resolve("effects/observation.properties"), "T12 OS observations");
            assertEquals("pass", effects.required("result"));
            for (String effect : new String[] {"read", "write", "script", "process", "network"}) {
                assertEquals("pass", effects.required("qualification." + effect));
                assertEquals("0", effects.required("effects." + effect));
            }
            assertTrue(Files.size(output.resolve("effects/observation.json")) > 0);
        }
    }

    @Test
    @Category(IndependentTools.class)
    public void controlsCommandDetectsActualProductChangesWithoutWeakeningAnyFrozenExpectation() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path products = temporary.getRoot().toPath().resolve("control-products");
        T12EvidenceCommand.main(new String[] {"products", root.toString(), products.toString(), "IN_PROCESS"});
        Path output = temporary.getRoot().toPath().resolve("controls");
        T12EvidenceCommand.main(new String[] {"controls", root.toString(), products.toString(), output.toString(), "IN_PROCESS"});
        PinProperties record = PinProperties.load(output.resolve("result.properties"), "T12 actual negative controls");
        for (String chain : new String[] {"syntax", "standards", "semantic", "visual"}) {
            assertEquals(chain, "fail", record.required(chain));
            assertTrue(Files.size(output.resolve(chain + ".txt")) > 0);
        }
        for (String change : new String[] {"order", "identifier", "rectangle", "appearance", "appearance-box", "payload", "icon",
                "direct-target", "action-operand", "named-target", "copy-target", "copy-external", "merge-name", "split-survival",
                "open-adoption", "flatten-removal", "flatten-placement", "retained-paint"}) {
            PinProperties control = PinProperties.load(output.resolve("semantic/" + change + "/result.properties"), change);
            assertEquals(change + ": " + control.required("finding"), "fail", control.required("semantic"));
            assertEquals(EvidenceFiles.sha256(output.resolve("semantic/" + change + ".pdf")), control.required("input-sha256"));
        }
        for (String change : new String[] {"appearance", "rectangle", "retained-paint"}) {
            PinProperties control = PinProperties.load(output.resolve("visual/" + change + "/result.properties"), change);
            assertEquals("fail", control.required("visual"));
            assertEquals("fail", control.required("page.1.visual"));
        }
    }

    @Test
    @Category(IndependentTools.class)
    public void observeCommandBindsFourSeparateChainsAndAll174QualifiedRulesToEachExactProduct() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path products = temporary.getRoot().toPath().resolve("observed-products");
        T12EvidenceCommand.main(new String[] {"products", root.toString(), products.toString(), "IN_PROCESS"});
        for (String product : new String[] {"created", "flattened"}) {
            Path input = products.resolve("native-" + product + "/annotations.pdf");
            Path output = temporary.getRoot().toPath().resolve("observed-" + product);
            T12EvidenceCommand.main(new String[] {"observe", root.toString(), input.toString(), product,
                output.toString(), "IN_PROCESS", "test"});
            PinProperties record = PinProperties.load(output.resolve("result.properties"), "T12 four chains");
            assertEquals(EvidenceFiles.sha256(input), record.required("input-sha256"));
            assertEquals("174", record.required("standards-rule-count"));
            assertEquals("IN_PROCESS", record.required("execution-profile"));
            for (String chain : new String[] {"syntax", "standards", "semantic", "visual"}) {
                assertEquals(product + " " + chain, "pass", record.required(chain));
                assertTrue(Files.size(output.resolve(chain + ".txt")) > 0);
            }
            for (String checker : new String[] {"pdfcpu", "arlington-core", "arlington-annotations"}) {
                PinProperties standards = PinProperties.load(output.resolve(checker + "/standards.properties"), checker);
                assertEquals("pass", standards.required("result"));
                assertEquals(EvidenceFiles.sha256(input), standards.required("input-sha256"));
            }
        }
    }

    @Test
    @Category(IndependentTools.class)
    public void semanticCommandObservesBothInterfacesIndependentlyAndRecordsTheActualNativeProfile() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path products = temporary.getRoot().toPath().resolve("semantic-products");
        T12EvidenceCommand.main(new String[] {"products", root.toString(), products.toString(), "IN_PROCESS"});
        for (String api : new String[] {"native", "facade"}) {
            for (String product : new String[] {"created", "changed", "flattened", "copied", "merged", "adopted", "left", "right"}) {
                Path input = products.resolve(api + "-" + product + "/annotations.pdf");
                Path output = temporary.getRoot().toPath().resolve("semantic-" + api + "-" + product);
                String mode = "native".equals(api) ? "HARDENED_WORKER" : "IN_PROCESS";
                T12EvidenceCommand.main(new String[] {"semantic", root.toString(), input.toString(), product, output.toString(), mode});
                PinProperties record = PinProperties.load(output.resolve("result.properties"), "T12 independent semantics");
                assertEquals(api + " " + product + ": " + record.required("finding"), "pass", record.required("semantic"));
                assertEquals(EvidenceFiles.sha256(input), record.required("input-sha256"));
                assertEquals(mode, record.required("execution-profile"));
                assertEquals("independent-qpdf-object-graph", record.required("semantic-scope"));
                assertEquals("pass", record.required("public-observation"));
                assertTrue(Files.size(output.resolve("qpdf/observed.json")) > 0);
            }
        }
    }

    @Test
    @Category(IndependentTools.class)
    public void visualCommandQualifiesAllOriginalAppearancesIncludingStandaloneWidgetAgainstExactPixels() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        for (String product : new String[] {"created", "changed", "flattened", "copied", "merged", "adopted", "left", "right"}) {
            Path input = root.resolve("capabilities/profiles/T12-annotations/fixtures/reference-" + product + ".pdf");
            Path output = temporary.getRoot().toPath().resolve("visual-" + product);
            T12EvidenceCommand.main(new String[] {"visual", root.toString(), input.toString(), product, output.toString(), "test"});
            PinProperties record = PinProperties.load(output.resolve("result.properties"), "T12 visual qualification");
            assertEquals(product, "pass", record.required("visual"));
            assertEquals(EvidenceFiles.sha256(input), record.required("input-sha256"));
            int count = Integer.parseInt(record.required("page-count"));
            for (int page = 1; page <= count; page++) {
                assertEquals("pass", record.required("page." + page + ".visual"));
                String report = new String(Files.readAllBytes(output.resolve("page-" + page + "-visual.md")), StandardCharsets.UTF_8);
                String findings = new String(Files.readAllBytes(output.resolve("page-" + page + "-visual.txt")), StandardCharsets.UTF_8);
                assertTrue(report.contains("document.annotations-actions.manage"));
                assertTrue(report.contains(EvidenceFiles.sha256(input)));
                assertTrue(findings.contains("PDFium appearance projection"));
                assertTrue(findings.contains("Projection exact SHA-256"));
                assertTrue(findings.contains("AE: `0`"));
            }
        }
    }

    @Test
    public void productsCommandPublishesEightFrozenProductsWithOrderedReceiptsForBothInterfaces() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path output = temporary.getRoot().toPath().resolve("products");
        Path sources = root.resolve("capabilities/profiles/T12-annotations/fixtures");
        String primaryHash = EvidenceFiles.sha256(sources.resolve("primary.pdf"));
        String appendixHash = EvidenceFiles.sha256(sources.resolve("appendix.pdf"));
        T12EvidenceCommand.main(new String[] {"products", root.toString(), output.toString(), "IN_PROCESS"});
        String[] products = {"created", "changed", "flattened", "copied", "merged", "adopted", "left", "right"};
        int[] pages = {3, 3, 3, 5, 4, 4, 2, 2};
        int[] counts = {9, 9, 1, 18, 11, 11, 7, 2};
        for (String api : new String[] {"native", "facade"}) {
            for (int index = 0; index < products.length; index++) {
                String product = products[index];
                Path pdf = output.resolve(api + "-" + product + "/annotations.pdf");
                assertEquals(Integer.valueOf(pages[index]), new DocumentWorkflow().execute(
                        WorkflowRequest.open(pdf, SaveMode.REWRITE), session -> session.query(PageCount.INSTANCE)).getResult());
                List<Annotation> observed = new DocumentWorkflow().execute(WorkflowRequest.open(pdf, SaveMode.REWRITE),
                        session -> session.query(Annotations.version1(100, 65536, 65536))).getResult();
                assertEquals(product, counts[index], observed.size());
                List<Annotation> equivalent = new DocumentWorkflow().execute(
                        WorkflowRequest.open(output.resolve("native-" + product + "/annotations.pdf"), SaveMode.REWRITE),
                        session -> session.query(Annotations.version1(100, 65536, 65536))).getResult();
                assertEquals(api + " " + product, equivalent, observed);
                if ("created".equals(product) || "changed".equals(product)) {
                    assertEquals("note", observed.get(0).getProperties().getIdentifier());
                    assertEquals("created".equals(product) ? Annotation.TextIcon.NOTE : Annotation.TextIcon.HELP,
                            observed.get(0).getTextIcon().get());
                    assertEquals("created".equals(product) ? "Approved" : "Draft", observed.get(1).getStampName().get());
                    assertEquals(Annotation.Type.HIGHLIGHT, observed.get(2).getType());
                    assertEquals(Annotation.Type.FILE_ATTACHMENT, observed.get(3).getType());
                    assertEquals(Annotation.Type.WIDGET, observed.get(4).getType());
                    assertEquals(Annotation.Type.LINK, observed.get(5).getType());
                    assertEquals("shared", observed.get(8).getLinkActivation().get().getTarget().getNamedDestination().get());
                }
                PinProperties receipts = PinProperties.load(output.resolve(api + "-" + product + "-publication.properties"), "T12 receipt");
                assertEquals("1", receipts.required("receipt-count"));
                assertEquals(product, receipts.required("receipt.0.target"));
                assertEquals("COMMITTED", receipts.required("receipt.0.status"));
                assertEquals("false", receipts.required("receipt.0.partial-output-possible"));
                assertEquals(EvidenceFiles.sha256(pdf), receipts.required("receipt.0.pdf-sha256"));
                assertEquals("IN_PROCESS", receipts.required("execution-profile"));
            }
        }
        assertEquals(primaryHash, EvidenceFiles.sha256(sources.resolve("primary.pdf")));
        assertEquals(appendixHash, EvidenceFiles.sha256(sources.resolve("appendix.pdf")));
        PinProperties record = PinProperties.load(output.resolve("products.properties"), "T12 products");
        assertEquals("products-only", record.required("phase"));
        assertFalse(Files.exists(output.resolve("result.properties")));
    }
}
