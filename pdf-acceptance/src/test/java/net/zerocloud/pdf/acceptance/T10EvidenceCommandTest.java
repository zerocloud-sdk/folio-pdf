package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.PageObjectReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

/** Public command and Native reopen observations; no backend test seam. */
public final class T10EvidenceCommandTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void frozenVisualProfilesCoverEveryProductPageAtExactTolerance() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        T10Corpus corpus = new T10Corpus(root);
        for (String product : new String[] {"edited", "merged", "left", "right"}) {
            int pageCount = Integer.parseInt(corpus.expected("products." + product + ".pages.count"));
            for (int page = 1; page <= pageCount; page++) {
                String pageName = corpus.expected("products." + product + ".pages." + (page - 1));
                VisualProfile profile = VisualProfile.load(root.resolve(
                        "capabilities/profiles/T10-pages/visual/" + product + "-page-" + page + ".properties"));
                assertEquals(T10Corpus.PROFILE + "-" + product + "-page-" + page, profile.profileId());
                assertEquals(pageCount, profile.pageCount());
                assertEquals(page, profile.pageNumber());
                assertEquals(144, profile.dpi());
                assertEquals(Integer.parseInt(corpus.expected("pages." + pageName + ".raster-width")),
                        profile.rasterWidth());
                assertEquals(Integer.parseInt(corpus.expected("pages." + pageName + ".raster-height")),
                        profile.rasterHeight());
                assertEquals(corpus.expected("pages." + pageName + ".raster-sha256"),
                        profile.expectedRasterSha256());
                assertEquals(0, profile.comparisonFuzzPercent());
                assertEquals(0L, profile.comparisonThreshold());
                assertEquals(0L, profile.rendererAgreementThreshold());
            }
        }
    }

    @Test
    @Category(IndependentTools.class)
    public void visualCommandRecordsEverySelectedPageAgainstOriginalPixels() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path products = temporary.getRoot().toPath().resolve("visual-products");
        T10EvidenceCommand.main(new String[] {"products", root.toString(), products.toString(), "IN_PROCESS"});
        Path pdf = products.resolve("native-edited/pages.pdf");
        Path output = temporary.getRoot().toPath().resolve("visual-observation");

        T10EvidenceCommand.main(new String[] {
            "visual", root.toString(), pdf.toString(), "edited", output.toString(), "test"});

        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T10 visual result");
        assertEquals("pass", result.required("visual"));
        assertEquals(EvidenceFiles.sha256(pdf), result.required("input-sha256"));
        assertEquals("4", result.required("page-count"));
        for (int page = 1; page <= 4; page++) {
            assertEquals("pass", result.required("page." + page + ".visual"));
            assertTrue(Files.size(output.resolve("page-" + page + "-visual.txt")) > 0);
            assertTrue(Files.size(output.resolve("page-" + page + "-visual.md")) > 0);
        }
    }

    @Test
    @Category(IndependentTools.class)
    public void observeCommandRecordsFourSeparatedChainsForOneExactProduct() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path products = temporary.getRoot().toPath().resolve("observed-products");
        T10EvidenceCommand.main(new String[] {"products", root.toString(), products.toString(), "IN_PROCESS"});
        Path pdf = products.resolve("native-edited/pages.pdf");
        Path output = temporary.getRoot().toPath().resolve("observed-native-edited");

        T10EvidenceCommand.main(new String[] {"observe", root.toString(), pdf.toString(), "edited",
            output.toString(), "IN_PROCESS", "test"});

        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T10 independent result");
        assertEquals(EvidenceFiles.sha256(pdf), result.required("input-sha256"));
        for (String chain : new String[] {"syntax", "standards", "semantic", "visual"}) {
            assertEquals(chain, "pass", result.required(chain));
            assertTrue(Files.size(output.resolve(chain + ".txt")) > 0);
        }
        assertEquals("84", result.required("standards-rule-count"));
        assertEquals("IN_PROCESS", result.required("execution-profile"));
        assertEquals("pass", PinProperties.load(output.resolve("pdfcpu/standards.properties"),
                "pdfcpu result").required("result"));
        assertEquals("pass", PinProperties.load(output.resolve("arlington/standards.properties"),
                "Arlington result").required("result"));
    }

    @Test
    @Category(IndependentTools.class)
    public void fullCommandCertifiesBothInterfacesAndRunsRealNegativeControls() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path output = temporary.getRoot().toPath().resolve("full-t10");

        T10EvidenceCommand.main(new String[] {
            root.toString(), output.toString(), "IN_PROCESS", "test"});

        PinProperties overall = PinProperties.load(output.resolve("result.properties"), "full T10 result");
        for (String chain : new String[] {"syntax", "standards", "semantic", "visual"}) {
            assertEquals(chain, "pass", overall.required(chain));
        }
        for (String api : new String[] {"native", "facade"}) {
            for (String product : new String[] {"edited", "merged", "left", "right"}) {
                Path directory = output.resolve(api + "-" + product);
                PinProperties result = PinProperties.load(directory.resolve("result.properties"),
                        api + " " + product);
                assertEquals(EvidenceFiles.sha256(directory.resolve("pages.pdf")), result.required("input-sha256"));
                assertEquals("84", result.required("standards-rule-count"));
                assertEquals("IN_PROCESS", result.required("execution-profile"));
                for (String chain : new String[] {"syntax", "standards", "semantic", "visual"}) {
                    assertEquals(api + " " + product + " " + chain, "pass", result.required(chain));
                }
            }
        }
        PinProperties controls = PinProperties.load(output.resolve("negative/result.properties"),
                "T10 negative controls");
        for (String chain : new String[] {"syntax", "standards", "semantic", "visual"}) {
            assertEquals(chain + " control", "fail", controls.required(chain));
        }
    }

    @Test
    @Category(IndependentTools.class)
    public void missingToolRuleOrFrozenProfileIdentityCannotProducePass() throws Exception {
        Path repository = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path products = temporary.getRoot().toPath().resolve("qualification-products");
        T10EvidenceCommand.main(new String[] {
            "products", repository.toString(), products.toString(), "IN_PROCESS"});
        Path product = products.resolve("native-edited/pages.pdf");
        for (String scenario : new String[] {"tool", "rule", "profile-hash"}) {
            Path root = copyObservationRoot(repository, scenario);
            if ("tool".equals(scenario)) {
                Properties pin = load(root.resolve("scripts/qpdf-pin.properties"));
                pin.setProperty("QPDF_EXECUTABLE",
                        root.resolve("unavailable-qpdf").toString());
                save(root.resolve("scripts/qpdf-pin.properties"), pin);
            } else if ("rule".equals(scenario)) {
                Path rules = root.resolve(
                        "capabilities/profiles/T10-standards/required-rules.txt");
                List<String> values = new ArrayList<String>(Files.readAllLines(
                        rules, StandardCharsets.US_ASCII));
                assertTrue(values.remove(values.size() - 1) != null);
                Files.write(rules, values, StandardCharsets.US_ASCII);
            } else {
                Files.write(root.resolve(
                        "capabilities/profiles/T10-standards/pdfcpu.properties"),
                        "# changed identity\n".getBytes(StandardCharsets.US_ASCII),
                        java.nio.file.StandardOpenOption.APPEND);
            }
            Path output = temporary.getRoot().toPath().resolve(
                    "qualification-" + scenario);
            try {
                T10EvidenceCommand.main(new String[] {
                    "observe", root.toString(), product.toString(), "edited",
                    output.toString(), "IN_PROCESS", "test"});
                assertEquals("tool", scenario);
                PinProperties result = PinProperties.load(
                        output.resolve("result.properties"), scenario);
                assertEquals("indeterminate", result.required("syntax"));
            } catch (java.io.IOException rejected) {
                assertFalse("A missing executable must retain an observation", "tool".equals(scenario));
                assertTrue(rejected.getMessage(), rejected.getMessage().contains("T10"));
                assertFalse(Files.exists(output.resolve("result.properties")));
            }
        }
    }

    @Test
    public void semanticRecordsAcceptAllProductsAndRejectARealWrongSequence() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path output = temporary.getRoot().toPath().resolve("semantic-products");
        T10EvidenceCommand.main(new String[] {"products", root.toString(), output.toString(), "IN_PROCESS"});
        for (String api : new String[] {"native", "facade"}) {
            for (String product : new String[] {"edited", "merged", "left", "right"}) {
                Path pdf = output.resolve(api + "-" + product + "/pages.pdf");
                Path observation = temporary.getRoot().toPath().resolve(api + "-" + product + "-semantic");
                T10EvidenceCommand.main(new String[] {"semantic", root.toString(), pdf.toString(), product,
                    observation.toString(), "IN_PROCESS"});
                PinProperties values = PinProperties.load(observation.resolve("result.properties"), "semantic observation");
                assertEquals(api + "-" + product + ": " + values.required("finding"), "pass", values.required("semantic"));
                assertEquals("IN_PROCESS", values.required("execution-profile"));
                assertEquals(EvidenceFiles.sha256(pdf), values.required("input-sha256"));
            }
        }
        Path wrongOrder = temporary.getRoot().toPath().resolve("wrong-order");
        T10EvidenceCommand.main(new String[] {"semantic", root.toString(), output.resolve("native-left/pages.pdf").toString(),
            "right", wrongOrder.toString(), "IN_PROCESS"});
        assertEquals("fail", PinProperties.load(wrongOrder.resolve("result.properties"), "negative").required("semantic"));
    }

    @Test
    public void bothInterfacesProduceTheFrozenPageSequencesAndActualOrderedReceipts() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path output = temporary.getRoot().toPath().resolve("products");
        Path source = root.resolve("capabilities/profiles/T10-pages/fixtures/primary.pdf");
        String before = EvidenceFiles.sha256(source);

        T10EvidenceCommand.main(new String[] {"products", root.toString(), output.toString(), "IN_PROCESS"});

        for (String api : new String[] {"native", "facade"}) {
            assertPages(output.resolve(api + "-edited/pages.pdf"), "A", "", "A", "B");
            assertPages(output.resolve(api + "-merged/pages.pdf"), "A", "B", "C", "E", "D");
            assertPages(output.resolve(api + "-left/pages.pdf"), "A", "B", "C");
            assertPages(output.resolve(api + "-right/pages.pdf"), "C", "E", "D");
            PinProperties split = PinProperties.load(output.resolve(api + "-split-publication.properties"), "split receipt");
            assertEquals("IN_PROCESS", split.required("execution-profile"));
            assertEquals("COMMAND_REJECTED", split.required("terminal-command-code"));
            assertEquals("3", split.required("receipt-count"));
            for (int index = 0; index < 3; index++) {
                String target = new String[] {"merged", "left", "right"}[index];
                assertEquals(target, split.required("receipt." + index + ".target"));
                assertEquals("COMMITTED", split.required("receipt." + index + ".status"));
                assertEquals(output.resolve(api + "-" + target + "/pages.pdf").toString(), split.required("receipt." + index + ".path"));
                assertEquals("false", split.required("receipt." + index + ".partial-output-possible"));
            }
            assertCopiedContentOnlyChanged(output.resolve(api + "-edited/pages.pdf"));
        }
        assertEquals(before, EvidenceFiles.sha256(source));
        PinProperties phase = PinProperties.load(output.resolve("products.properties"), "product generation");
        assertEquals("products-only", phase.required("phase"));
        assertFalse(java.nio.file.Files.exists(output.resolve("result.properties")));
    }

    private static void assertPages(Path path, String... expected) throws Exception {
        List<String> observed = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .source("input", DocumentSource.path(path)).primarySource("input").saveMode(SaveMode.REWRITE).build(), session -> {
                    List<String> pages = new ArrayList<String>();
                    for (int number = 1; number <= session.query(PageCount.INSTANCE); number++) {
                        PdfDictionary page = (PdfDictionary) session.query(InspectObject.version1(
                                session.query(PageObjectReference.version1(number)), PdfInspectionLimits.of(1000, 0)));
                        PdfName marker = (PdfName) page.get(PdfName.of("T10Marker"));
                        pages.add(marker == null ? "" : marker.getValue());
                    }
                    return pages;
                }).getResult();
        assertEquals(Arrays.asList(expected), observed);
    }

    private static void assertCopiedContentOnlyChanged(Path path) throws Exception {
        new DocumentWorkflow().execute(WorkflowRequest.builder().source("input", DocumentSource.path(path))
                .primarySource("input").saveMode(SaveMode.REWRITE).build(), session -> {
                    for (int pageNumber : new int[] {1, 3}) {
                        PdfDictionary page = (PdfDictionary) session.query(InspectObject.version1(
                                session.query(PageObjectReference.version1(pageNumber)), PdfInspectionLimits.of(1000, 1 << 20)));
                        PdfValue contents = page.get(PdfName.of("Contents"));
                        if (contents instanceof PdfIndirectReference) {
                            contents = session.query(InspectObject.version1(((PdfIndirectReference) contents).getReference(),
                                    PdfInspectionLimits.of(1000, 1 << 20)));
                        }
                        PdfStream stream;
                        if (contents instanceof PdfArray) {
                            PdfIndirectReference first = (PdfIndirectReference) ((PdfArray) contents).get(0);
                            stream = (PdfStream) session.query(InspectObject.version1(first.getReference(), PdfInspectionLimits.of(1000, 1 << 20)));
                        } else {
                            stream = (PdfStream) contents;
                            assertTrue(new String(stream.readBytes(), StandardCharsets.US_ASCII).contains("/Tile Do"));
                        }
                        String program = new String(stream.readBytes(), StandardCharsets.US_ASCII);
                        assertTrue(program, program.contains(pageNumber == 1 ? "1 1 0 rg 20 30 80 60" : "0 1 1 rg 20 30 80 60"));
                    }
                    return null;
                });
    }

    private Path copyObservationRoot(Path repository, String name) throws Exception {
        Path root = Files.createDirectory(
                temporary.getRoot().toPath().resolve("root-" + name));
        for (String relative : new String[] {
                "capabilities/profiles/T03-standards",
                "capabilities/profiles/T09-standards",
                "capabilities/profiles/T10-standards",
                "capabilities/profiles/T10-pages"}) {
            Path source = repository.resolve(relative);
            try (java.util.stream.Stream<Path> paths = Files.walk(source)) {
                for (Path path : (Iterable<Path>) paths::iterator) {
                    Path target = root.resolve(relative).resolve(source.relativize(path));
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target);
                    } else {
                        Files.copy(path, target);
                    }
                }
            }
        }
        Files.createDirectory(root.resolve("scripts"));
        for (String namePart : new String[] {
                "qpdf", "pdfcpu", "t10-arlington", "pdfium", "imagemagick"}) {
            Path source = repository.resolve("scripts/" + namePart + "-pin.properties");
            Properties values = load(source);
            for (String key : values.stringPropertyNames()) {
                if (key.endsWith("EXECUTABLE") || "executable".equals(key)
                        || "model".equals(key) || "patch".equals(key)) {
                    values.setProperty(key, source.getParent()
                            .resolve(values.getProperty(key)).normalize().toString());
                }
            }
            save(root.resolve("scripts/" + namePart + "-pin.properties"), values);
        }
        return root;
    }

    private static Properties load(Path path) throws Exception {
        Properties result = new Properties();
        try (java.io.InputStream input = Files.newInputStream(path)) {
            result.load(input);
        }
        return result;
    }

    private static void save(Path path, Properties values) throws Exception {
        try (java.io.OutputStream output = Files.newOutputStream(path)) {
            values.store(output, "Isolated T10 qualification control");
        }
    }
}
