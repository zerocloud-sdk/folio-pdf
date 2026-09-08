package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Exercises the repository-only T09 command through its emitted products and records. */
public final class T09EvidenceCommandTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void failedSemanticExecutionDoesNotQualifyAsADetectedValueDifference() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot"));
        for (String input : new String[] {"missing.pdf", "malformed.pdf", "unchanged.pdf"}) {
            Path pdf = temporary.getRoot().toPath().resolve(input);
            if ("malformed.pdf".equals(input)) {
                Files.write(pdf, "This is not a PDF.".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            } else if ("unchanged.pdf".equals(input)) {
                Files.copy(root.resolve("capabilities/profiles/T09-values/fixtures/values.pdf"), pdf);
            }
            Path output = temporary.getRoot().toPath().resolve(input + "-observed");
            T09EvidenceCommand.main(new String[] {"semantic", pdf.toString(), output.toString(), "IN_PROCESS"});
            PinProperties result = PinProperties.load(output.resolve("result.properties"), "semantic observation");
            assertEquals(input, "unchanged.pdf".equals(input) ? "fail" : "indeterminate", result.required("semantic"));
            assertTrue(Files.size(output.resolve("semantic.txt")) > 0);
        }
    }

    private void assertChangedProducts(Path root, Path output) throws Exception {
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T09 result");
        assertEquals("pass", result.required("semantic"));
        for (String product : new String[] {"native-rewrite", "native-incremental", "facade"}) {
            Path directory = output.resolve(product);
            assertTrue(Files.size(directory.resolve("values.pdf")) > 0);
            PinProperties recorded = PinProperties.load(directory.resolve("result.properties"), "product result");
            assertEquals("pass", recorded.required("semantic"));
            assertEquals(EvidenceFiles.sha256(directory.resolve("values.pdf")), recorded.required("input-sha256"));
            assertEquals("IN_PROCESS", recorded.required("execution-profile"));
        }
        assertEquals("fail", PinProperties.load(output.resolve("negative/result.properties"), "negative result").required("semantic"));
        assertEquals(EvidenceFiles.sha256(root.resolve("capabilities/profiles/T09-values/fixtures/values.pdf")),
                result.required("source-sha256"));
    }

    @Test
    public void recordsQualifiedIndependentChainsAndRealNegativeControls() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot"));
        Path output = temporary.getRoot().toPath().resolve("four-chains");
        T09EvidenceCommand.main(new String[] {root.toString(), output.toString(), "IN_PROCESS", "test"});
        assertChangedProducts(root, output);
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T09 result");
        PinProperties controls = PinProperties.load(output.resolve("negative/result.properties"), "controls");
        for (String chain : new String[] {"syntax", "standards", "semantic", "visual"}) {
            assertEquals(chain, "pass", result.required(chain));
            assertEquals(chain + " control", "fail", controls.required(chain));
            for (String product : new String[] {"native-rewrite", "native-incremental", "facade"}) {
                assertTrue(Files.size(output.resolve(product + "/" + chain + ".txt")) > 0);
            }
        }
        for (String product : new String[] {"native-rewrite", "native-incremental", "facade"}) {
            for (String chain : new String[] {"syntax", "visual"}) {
                String report = new String(Files.readAllBytes(output.resolve(product + "/" + chain + ".md")), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(report.contains("document.value.inspect-patch"));
                assertTrue(report.contains("Input exact SHA-256: `" + EvidenceFiles.sha256(output.resolve(product + "/values.pdf")) + "`"));
                java.util.regex.Matcher links = java.util.regex.Pattern.compile("\\]\\(([^)]+)\\)").matcher(report);
                while (links.find()) {
                    assertTrue("Missing report link: " + links.group(1),
                            Files.isRegularFile(output.resolve(product).resolve(links.group(1))));
                }
            }
        }
        assertEquals("pass", PinProperties.load(output.resolve("source/result.properties"), "original page").required("visual"));
    }

    @Test
    public void missingT09RuleOrActualCheckerCannotQualify() throws Exception {
        for (String changed : new String[] {"rule", "tool"}) {
            Path root = copyObservationRoot(changed);
            Path file = root.resolve("rule".equals(changed) ? "capabilities/profiles/T09-standards/pdfcpu.properties"
                    : "scripts/pdfcpu-pin.properties");
            java.util.Properties values = load(file);
            if ("rule".equals(changed)) {
                for (String key : new String[] {"required-rules", "covered-rules"}) {
                    java.util.List<String> rules = new java.util.ArrayList<String>(java.util.Arrays.asList(values.getProperty(key).split(",")));
                    assertTrue(rules.remove("data-lastmodified-null"));
                    values.setProperty(key, String.join(",", rules));
                }
            } else {
                values.setProperty("executable", root.resolve("unavailable-pdfcpu").toString());
            }
            save(file, values);
            Path output = temporary.getRoot().toPath().resolve(changed + "-observations");
            try {
                T09EvidenceCommand.main(new String[] {root.toString(), output.toString(), "IN_PROCESS", "test"});
                org.junit.Assert.fail("Missing " + changed + " cannot qualify T09");
            } catch (java.io.IOException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("standards observation did not pass"));
            }
            assertEquals("indeterminate", PinProperties.load(output.resolve("result.properties"), "unqualified T09").required("standards"));
            assertEquals("indeterminate", PinProperties.load(output.resolve("negative/result.properties"), "unqualified controls").required("standards"));
        }
    }

    @Test
    public void changedSourceGoldenAndThresholdCannotReplaceTheFrozenContract() throws Exception {
        for (String changed : new String[] {"source", "golden", "threshold"}) {
            Path root = copyObservationRoot(changed);
            if ("source".equals(changed)) {
                Files.write(root.resolve("capabilities/profiles/T09-values/fixtures/values.pdf"), new byte[] {0});
            } else {
                Path file = root.resolve("capabilities/profiles/T09-values-visual.properties");
                java.util.Properties values = load(file);
                values.setProperty("golden".equals(changed) ? "EXPECTED_RASTER_SHA256" : "COMPARISON_THRESHOLD",
                        "golden".equals(changed) ? String.join("", java.util.Collections.nCopies(64, "0")) : "1");
                save(file, values);
            }
            try {
                T09EvidenceCommand.main(new String[] {root.toString(), temporary.getRoot().toPath().resolve(changed + "-rejected").toString(), "IN_PROCESS", "test"});
                org.junit.Assert.fail("Changed " + changed + " cannot replace T09's frozen identity");
            } catch (java.io.IOException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("T09"));
            }
        }
    }

    private Path copyObservationRoot(String name) throws Exception {
        Path original = Paths.get(System.getProperty("repositoryRoot"));
        Path root = Files.createDirectory(temporary.getRoot().toPath().resolve(name));
        for (String relative : new String[] {"capabilities/profiles/T03-standards", "capabilities/profiles/T09-standards", "capabilities/profiles/T09-values"}) {
            Path source = original.resolve(relative);
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
        Files.createDirectories(root.resolve("capabilities/expected"));
        Files.copy(original.resolve("capabilities/profiles/T09-values-visual.properties"), root.resolve("capabilities/profiles/T09-values-visual.properties"));
        Files.copy(original.resolve("capabilities/expected/T09-values-144dpi-srgb.png"), root.resolve("capabilities/expected/T09-values-144dpi-srgb.png"));
        Files.createDirectory(root.resolve("scripts"));
        for (String checker : new String[] {"qpdf", "pdfcpu", "arlington", "pdfium", "imagemagick"}) {
            Path pin = original.resolve("scripts/" + checker + "-pin.properties");
            java.util.Properties values = load(pin);
            for (String key : values.stringPropertyNames()) {
                if (key.endsWith("EXECUTABLE") || "executable".equals(key) || "model".equals(key)) {
                    values.setProperty(key, pin.getParent().resolve(values.getProperty(key)).normalize().toString());
                }
            }
            save(root.resolve("scripts/" + checker + "-pin.properties"), values);
        }
        return root;
    }

    private static java.util.Properties load(Path path) throws Exception {
        java.util.Properties result = new java.util.Properties();
        try (java.io.InputStream input = Files.newInputStream(path)) {
            result.load(input);
        }
        return result;
    }

    private static void save(Path path, java.util.Properties values) throws Exception {
        try (java.io.OutputStream output = Files.newOutputStream(path)) {
            values.store(output, "Isolated public-command control; canonical inputs remain unchanged");
        }
    }
}
