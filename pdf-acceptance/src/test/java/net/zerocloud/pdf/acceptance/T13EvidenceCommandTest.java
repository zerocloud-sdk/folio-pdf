package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

/** Exercises independent extraction evidence through its public command. */
public final class T13EvidenceCommandTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    @Category(IndependentTools.class)
    public void visualControlsRequireTheFrozenRasterReferenceIdentity() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path directory = Paths.get(System.getProperty("folio.t13.rasterAuthorityControlsOutput",
                temporary.getRoot().toPath().resolve("raster-authority").toString()));
        Path staged = Files.createDirectories(directory.resolve("repository"));
        Path text = root.resolve("capabilities/profiles/T13-text");
        try (java.util.stream.Stream<Path> paths = Files.walk(text)) {
            for (Path source : (Iterable<Path>) paths::iterator) {
                Path target = staged.resolve("capabilities/profiles/T13-text").resolve(text.relativize(source));
                if (Files.isDirectory(source)) { Files.createDirectories(target); } else { Files.copy(source, target); }
            }
        }
        Path fonts = Files.createDirectories(staged.resolve("capabilities/profiles/T13-fonts"));
        for (String name : new String[] {"embedded-font-kinds-reference.png", "embedded-font-kinds-reference.pdf"}) {
            Files.copy(root.resolve("capabilities/profiles/T13-fonts/" + name), fonts.resolve(name));
        }
        Files.createSymbolicLink(staged.resolve("scripts"), root.resolve("scripts"));
        Files.createSymbolicLink(staged.resolve(".build-cache"), root.resolve(".build-cache"));
        Path positive = directory.resolve("positive");
        T13EvidenceCommand.main(new String[] {"visual-controls", staged.toString(), positive.toString(), "test"});
        assertEquals("fail", PinProperties.load(positive.resolve("result.properties"), "T13 reference positive").required("visual"));
        BufferedImage changed = ImageIO.read(fonts.resolve("embedded-font-kinds-reference.png").toFile());
        changed.setRGB(2, 2, 0x0000ff);
        assertTrue(ImageIO.write(changed, "png", fonts.resolve("embedded-font-kinds-reference.png").toFile()));
        Path negative = directory.resolve("changed-reference");
        boolean qualified = false;
        try {
            T13EvidenceCommand.main(new String[] {"visual-controls", staged.toString(), negative.toString(), "test"});
            qualified = "fail".equals(PinProperties.load(negative.resolve("result.properties"), "T13 changed reference").required("visual"));
        } catch (java.io.IOException refused) {
            // Refusing the unqualified reference is an acceptable command outcome.
        }
        org.junit.Assert.assertFalse("A substituted reference raster cannot qualify visual controls", qualified);
    }

    @Test
    @Category(IndependentTools.class)
    public void negativeControlsCommandRetainsEveryIndependentControlChain() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path output = Paths.get(System.getProperty("folio.t13.allControlsOutput",
                temporary.getRoot().toPath().resolve("all-controls").toString()));
        T13EvidenceCommand.main(new String[] {"negative-controls", root.toString(), output.toString(), "IN_PROCESS", "test"});
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T13 complete negative controls");
        for (String chain : new String[] {"syntax", "standards", "semantic", "visual"}) {
            assertEquals(chain, "fail", result.required(chain));
            assertTrue(Files.isRegularFile(output.resolve(chain + "/result.properties")));
        }
        requireCompleteRetainedManifest(output, result);
    }

    @Test
    @Category(IndependentTools.class)
    public void visualCommandRetainsBothRendersComparisonsAndFontEdgeObservation() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path input = root.resolve("capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf");
        Path output = Paths.get(System.getProperty("folio.t13.visualRetentionOutput",
                temporary.getRoot().toPath().resolve("retained-visual").toString()));
        T13EvidenceCommand.main(new String[] {"visual", root.toString(), input.toString(), "embedded-font-kinds", output.toString(), "test"});
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T13 retained visual evidence");
        assertEquals("pass", result.required("visual"));
        assertTrue(Files.isRegularFile(output.resolve("font-raster-agreement.properties")));
        requireCompleteRetainedManifest(output, result);
    }

    @Test
    @Category(IndependentTools.class)
    public void semanticCommandRetainsEveryDecodedGraphAndPublicObservation() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path input = root.resolve("capabilities/profiles/T13-text/fixtures/marked-structure.pdf");
        Path output = Paths.get(System.getProperty("folio.t13.semanticRetentionOutput",
                temporary.getRoot().toPath().resolve("retained-semantic").toString()));
        T13EvidenceCommand.main(new String[] {"semantic", root.toString(), input.toString(), "marked-structure", output.toString(), "IN_PROCESS"});
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T13 retained semantics");
        assertEquals("pass", result.required("semantic"));
        assertEquals("pass", result.required("public-observation"));
        requireCompleteRetainedManifest(output, result);
    }

    @Test
    @Category(IndependentTools.class)
    public void syntaxCommandRetainsEveryProducerReportWithItsExactInput() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path input = root.resolve("capabilities/profiles/T13-text/fixtures/marked-structure.pdf");
        Path output = Paths.get(System.getProperty("folio.t13.syntaxRetentionOutput",
                temporary.getRoot().toPath().resolve("retained-syntax").toString()));
        T13EvidenceCommand.main(new String[] {"syntax", root.toString(), input.toString(), output.toString(), "test"});
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T13 retained syntax");
        assertEquals("pass", result.required("syntax"));
        requireCompleteRetainedManifest(output, result);
    }

    private static void requireCompleteRetainedManifest(Path output, PinProperties result) throws Exception {
        Path manifest = output.resolve("retained-files.sha256");
        assertEquals(EvidenceFiles.sha256(manifest), result.required("retained-files-sha256"));
        java.util.Map<String, String> recorded = new java.util.TreeMap<String, String>();
        for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            assertTrue("A retained identity must use the published SHA-256 format", line.matches("[0-9a-f]{64}  .+"));
            org.junit.Assert.assertNull("A retained file must occur exactly once", recorded.put(line.substring(66), line.substring(0, 64)));
        }
        java.util.Set<String> files = new java.util.TreeSet<String>();
        try (java.util.stream.Stream<Path> paths = Files.walk(output)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                if (!path.equals(manifest) && !path.equals(output.resolve("result.properties"))) {
                    files.add(output.relativize(path).toString().replace('\\', '/'));
                }
            });
        }
        java.util.Set<String> missing = new java.util.TreeSet<String>(files);
        missing.removeAll(recorded.keySet());
        java.util.Set<String> unexpected = new java.util.TreeSet<String>(recorded.keySet());
        unexpected.removeAll(files);
        assertTrue("Unbound retained artifacts: " + missing, missing.isEmpty());
        assertTrue("Unavailable retained artifacts: " + unexpected, unexpected.isEmpty());
        for (String name : files) { assertEquals(name, recorded.get(name), EvidenceFiles.sha256(output.resolve(name))); }
    }

    @Test
    public void productsCommandRejectsAnEarlyDetachedReportChangedDuringLaterGeneration() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path output = Paths.get(System.getProperty("folio.t13.productsRetentionOutput",
                temporary.getRoot().toPath().resolve("changed-product-report").toString()));
        Path source = root.resolve("capabilities/profiles/T13-text/fixtures/nested-split-type3.pdf");
        String sourceHash = EvidenceFiles.sha256(source);
        java.util.concurrent.atomic.AtomicBoolean changed = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicReference<Throwable> editorFailure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread editor = new Thread(() -> {
            try {
                Path first = output.resolve("native-nested-split-type3");
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
                while (!Files.isRegularFile(first.resolve("publication.properties")) && System.nanoTime() < deadline) {
                    Thread.sleep(1);
                }
                if (!Files.isRegularFile(first.resolve("publication.properties"))) {
                    throw new AssertionError("The first product was not published within the test bound");
                }
                Files.write(first.resolve("observation.properties"), "changed detached observation\n".getBytes(StandardCharsets.UTF_8));
                changed.set(true);
            } catch (Throwable failure) {
                editorFailure.set(failure);
            }
        }, "t13-early-report-control");
        editor.start();
        boolean refused = false;
        try {
            T13EvidenceCommand.main(new String[] {"products", root.toString(), output.toString(), "HARDENED_WORKER"});
        } catch (java.io.IOException expected) {
            refused = true;
        } finally {
            editor.join(35000);
        }
        org.junit.Assert.assertNull(editorFailure.get());
        assertTrue("The original early report must actually be changed", changed.get());
        assertEquals(sourceHash, EvidenceFiles.sha256(source));
        assertTrue("A changed early detached report cannot become the batch's trusted baseline", refused);
    }

    @Test
    @Category(IndependentTools.class)
    public void recordsTheWholeExtractionCorpusWithActualApiModesAndNegativeControls() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path output = Paths.get(System.getProperty("folio.t13.allOutput",
                temporary.getRoot().toPath().resolve("all-extraction").toString()));
        T13EvidenceCommand.main(new String[] {root.toString(), output.toString(), "IN_PROCESS", "test"});
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T13 complete observation");
        assertEquals("certification", result.required("phase"));
        assertEquals("IN_PROCESS", result.required("native-execution-profile"));
        assertEquals("IN_PROCESS", result.required("facade-execution-profile"));
        PinProperties negative = PinProperties.load(output.resolve("negative/result.properties"), "T13 complete controls");
        for (String chain : new String[] {"syntax", "standards", "semantic", "visual"}) {
            assertEquals(chain, "pass", result.required(chain));
            assertEquals(chain, "fail", negative.required(chain));
        }
        T13Corpus corpus = new T13Corpus(root);
        for (String api : new String[] {"native", "facade"}) {
            for (String product : T13Corpus.PRODUCTS) {
                String edition = api + "-" + product;
                Path directory = output.resolve(edition);
                PinProperties observation = PinProperties.load(directory.resolve("result.properties"), "T13 " + edition);
                String hash = EvidenceFiles.sha256(directory.resolve("extraction.pdf"));
                assertEquals(edition, hash, observation.required("input-sha256"));
                assertEquals(edition, "IN_PROCESS", observation.required("execution-profile"));
                assertEquals(edition, "376", observation.required("qualified-standard-rule-count"));
                assertEquals(edition, "pass", observation.required("syntax"));
                assertEquals(edition, "pass", observation.required("standards"));
                assertEquals(edition, "pass", observation.required("semantic"));
                boolean visual = "nested-split-type3".equals(product) || "marked-structure".equals(product)
                        || "embedded-font-kinds".equals(product);
                assertEquals(edition, visual ? "pass" : "not-required", observation.required("visual"));
                PinProperties publication = PinProperties.load(directory.resolve("publication.properties"), "T13 " + edition + " publication");
                assertEquals(edition, hash, publication.required("output-sha256"));
                assertEquals(edition, EvidenceFiles.sha256(corpus.source(product)), publication.required("source-sha256"));
                assertEquals(edition, "pass", publication.required("source-preserved"));
                assertEquals(edition, "pass", publication.required("reopened"));
            }
        }
        requireCompleteRetainedManifest(output, result);
    }

    @Test
    @Category(IndependentTools.class)
    public void observesFourIndependentChainsForTheExactExtractionProduct() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path input = root.resolve("capabilities/profiles/T13-text/fixtures/marked-structure.pdf");
        Path output = Paths.get(System.getProperty("folio.t13.combinedOutput",
                temporary.getRoot().toPath().resolve("combined").toString()));
        T13EvidenceCommand.main(new String[] {"observe", root.toString(), input.toString(), "marked-structure",
                output.toString(), "IN_PROCESS", "test"});
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T13 combined extraction evidence");
        for (String chain : new String[] {"syntax", "standards", "semantic", "visual"}) {
            assertEquals(chain, "pass", result.required(chain));
        }
        assertEquals("376", result.required("qualified-standard-rule-count"));
        assertEquals("IN_PROCESS", result.required("execution-profile"));
        assertEquals(EvidenceFiles.sha256(input), result.required("input-sha256"));
        for (String chain : new String[] {"syntax", "declarations", "programs", "semantic", "visual"}) {
            PinProperties evidence = PinProperties.load(output.resolve(chain + "/result.properties"), "T13 " + chain);
            assertEquals(chain, EvidenceFiles.sha256(input), evidence.required("input-sha256"));
        }
        requireCompleteRetainedManifest(output, result);
    }

    @Test
    @Category(IndependentTools.class)
    public void syntaxCommandQualifiesTheCacheSelectedInTheToolWorkingDirectory() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path directory = temporary.getRoot().toPath();
        Path staged = directory.resolve("repository");
        Files.createDirectories(staged.resolve("scripts/container-bin"));
        for (String name : new String[] {"qpdf-pin.properties", "container-bin/qpdf", "t13-qpdf-runtime.sha256"}) {
            Files.copy(root.resolve("scripts/" + name), staged.resolve("scripts/" + name),
                    java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
        }
        Path genuine = root.resolve(".build-cache/qpdf");
        Path substitute = Files.createDirectories(directory.resolve("substitute/cache/12.4.0/bin")).getParent();
        Path fake = substitute.resolve("bin/qpdf");
        Files.write(fake, "#!/bin/sh\nprintf 'qpdf version 12.4.0\\n'\n".getBytes(StandardCharsets.US_ASCII));
        assertTrue(fake.toFile().setExecutable(true));
        for (String marker : new String[] {".archive-sha256", ".binary-sha256"}) {
            Files.copy(genuine.resolve("12.4.0/" + marker), substitute.resolve(marker));
        }
        Path launcher = Files.createDirectory(directory.resolve("launcher"));
        Files.createSymbolicLink(directory.resolve("cache"), genuine);
        Files.createSymbolicLink(launcher.resolve("12.4.0"), genuine.resolve("12.4.0"));
        Files.createDirectory(staged.resolve(".build-cache"));
        Path defaultCache = Files.createSymbolicLink(staged.resolve(".build-cache/qpdf"), substitute.getParent());
        Path truncated = directory.resolve("truncated.pdf");
        Files.write(truncated, "%PDF-2.0\n1 0 obj\n".getBytes(StandardCharsets.US_ASCII));
        String[][] cases = {
            {"relative-substitute", "../cache", "substitute/relative", "indeterminate"},
            {"empty-substitute", "", "empty", "indeterminate"},
            {"absolute-substitute", substitute.getParent().toString(), "absolute-substitute", "indeterminate"},
            {"absolute-genuine", genuine.toString(), "absolute-genuine", "fail"},
            {"relative-genuine", "../cache", "relative-genuine", "fail"},
            {"empty-genuine", "", "empty-genuine", "pass"},
            {"directory-link-substitute", "", "directory-link-substitute", "fail"},
            {"directory-link-genuine", "", "directory-link-genuine", "pass"},
            {"directory-link-pin-change", genuine.toString(), "directory-link-pin-change", "indeterminate"}
        };
        StringBuilder mismatches = new StringBuilder();
        for (String[] sample : cases) {
            Path input = truncated;
            if ("empty-genuine".equals(sample[0])) {
                Files.delete(defaultCache);
                Files.createSymbolicLink(defaultCache, genuine);
                input = root.resolve("capabilities/profiles/T13-text/fixtures/nested-split-type3.pdf");
            }
            Path alternate = directory.resolve("alternate");
            if ("directory-link-substitute".equals(sample[0])) {
                Files.createDirectories(alternate.resolve("scripts/container-bin"));
                for (String name : new String[] {"qpdf-pin.properties", "container-bin/qpdf"}) {
                    Files.copy(root.resolve("scripts/" + name), alternate.resolve("scripts/" + name),
                            java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                }
                Files.createDirectory(alternate.resolve(".build-cache"));
                Files.createSymbolicLink(alternate.resolve(".build-cache/qpdf"), substitute.getParent());
                Files.delete(staged.resolve("scripts/container-bin/qpdf"));
                Files.delete(staged.resolve("scripts/container-bin"));
                Files.createSymbolicLink(staged.resolve("scripts/container-bin"), alternate.resolve("scripts/container-bin"));
            }
            if ("directory-link-genuine".equals(sample[0])) {
                Files.delete(alternate.resolve(".build-cache/qpdf"));
                Files.createSymbolicLink(alternate.resolve(".build-cache/qpdf"), genuine);
                input = root.resolve("capabilities/profiles/T13-text/fixtures/nested-split-type3.pdf");
            }
            if ("directory-link-pin-change".equals(sample[0])) {
                Files.write(alternate.resolve("scripts/qpdf-pin.properties"),
                        ("QPDF_CACHE_DIRECTORY=" + substitute.getParent() + "\n").getBytes(StandardCharsets.US_ASCII),
                        java.nio.file.StandardOpenOption.APPEND);
            }
            Path output = directory.resolve(sample[2]);
            ProcessBuilder command = new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin/java").toString(),
                    "-cp", System.getProperty("java.class.path"), T13EvidenceCommand.class.getName(), "syntax",
                    staged.toString(), input.toString(), output.toString(), "test");
            command.directory(launcher.toFile());
            command.environment().put("QPDF_CACHE_DIRECTORY", sample[1]);
            command.redirectErrorStream(true).redirectOutput(directory.resolve(sample[0] + ".log").toFile());
            Process process = command.start();
            try {
                assertTrue(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(0, process.exitValue());
            } finally { process.destroyForcibly(); }
            PinProperties result = PinProperties.load(output.resolve("result.properties"), sample[0]);
            if (!sample[3].equals(result.required("syntax"))) {
                mismatches.append(sample[0]).append(": expected ").append(sample[3])
                        .append(", observed ").append(result.required("syntax")).append('\n');
            }
        }
        assertEquals("", mismatches.toString());
    }

    @Test
    @Category(IndependentTools.class)
    public void syntaxCommandRetainsRawFindingsAndReportsAnInputIdentityChange() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        byte[] original = Files.readAllBytes(root.resolve("capabilities/profiles/T13-text/fixtures/nested-split-type3.pdf"));
        Path input = temporary.getRoot().toPath().resolve("changing-input.pdf");
        Files.write(input, original);
        Path output = temporary.getRoot().toPath().resolve("changing-syntax");
        java.util.concurrent.FutureTask<Void> mutation = new java.util.concurrent.FutureTask<Void>(() -> {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            Path retained = output.resolve("extraction.pdf");
            while ((!Files.isRegularFile(retained) || Files.size(retained) != original.length)
                    && System.nanoTime() < deadline) { Thread.sleep(1); }
            if (!Files.isRegularFile(retained) || Files.size(retained) != original.length) {
                throw new IllegalStateException("The public syntax command never retained the complete input");
            }
            Files.write(input, "%PDF-2.0\n1 0 obj\n".getBytes(StandardCharsets.US_ASCII));
            return null;
        });
        Thread writer = new Thread(mutation, "t13-syntax-input-control");
        writer.setDaemon(true);
        writer.start();
        try {
            T13EvidenceCommand.main(new String[] {"syntax", root.toString(), input.toString(), output.toString(), "test"});
        } finally { mutation.get(15, java.util.concurrent.TimeUnit.SECONDS); }
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T13 changed syntax input");
        assertEquals("indeterminate", result.required("syntax"));
        String summary = new String(Files.readAllBytes(output.resolve("syntax.md")), StandardCharsets.UTF_8);
        assertTrue(summary.contains("Final determination: `indeterminate`"));
        assertTrue(summary.contains("identity"));
        String raw = new String(Files.readAllBytes(output.resolve("qpdf-syntax.txt")), StandardCharsets.UTF_8);
        assertTrue(raw.contains("Final determination: `pass`"));
        assertTrue(raw.contains("Standard output"));
        assertTrue(Files.isRegularFile(output.resolve("qpdf-syntax.md")));
    }

    @Test
    @Category(IndependentTools.class)
    public void syntaxCommandRejectsASubstitutedSharedLibraryWithTheOriginalExecutable() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path substitute = temporary.getRoot().toPath().resolve("library-repository");
        Files.createDirectories(substitute.resolve("scripts/container-bin"));
        for (String name : new String[] {"qpdf-pin.properties", "container-bin/qpdf", "t13-qpdf-runtime.sha256"}) {
            Files.copy(root.resolve("scripts/" + name), substitute.resolve("scripts/" + name),
                    java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
        }
        Path cache = Files.createDirectories(substitute.resolve(".build-cache/qpdf/12.4.0"));
        Path original = root.resolve(".build-cache/qpdf/12.4.0");
        Files.createDirectory(cache.resolve("bin"));
        Files.createDirectory(cache.resolve("lib"));
        for (String name : new String[] {"bin/qpdf", ".archive-sha256", ".binary-sha256"}) {
            Files.copy(original.resolve(name), cache.resolve(name), java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
        }
        try (java.nio.file.DirectoryStream<Path> libraries = Files.newDirectoryStream(original.resolve("lib"))) {
            for (Path library : libraries) {
                Path copy = cache.resolve("lib").resolve(library.getFileName());
                if (Files.isSymbolicLink(library)) { Files.createSymbolicLink(copy, Files.readSymbolicLink(library)); }
                else { Files.copy(library, copy, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES); }
            }
        }
        Path library = cache.resolve("lib/libqpdf.so.30.4.0");
        assertEquals("40bc77ad1cf7a085ceb36a0c3d98315807cd5403116e346a5e9a94731351fb5e", EvidenceFiles.sha256(library));
        byte[] bytes = Files.readAllBytes(library);
        // Original independent review control: make the pinned QPDFJob entry points return success without checking.
        // Offsets and before/after identities are tied to this exact acceptance-only Linux distribution.
        bytes[1439620] = (byte) 0xc3;
        bytes[1310068] = (byte) 0x31;
        bytes[1310069] = (byte) 0xc0;
        bytes[1310070] = (byte) 0xc3;
        Files.write(library, bytes);
        assertEquals("fef4c14e859eda45839c82d70041205e1bbcafde51840ec9b716559895c3b141", EvidenceFiles.sha256(library));
        Path input = temporary.getRoot().toPath().resolve("library-truncated.pdf");
        Files.write(input, "%PDF-2.0\n1 0 obj\n".getBytes(StandardCharsets.US_ASCII));
        Path output = temporary.getRoot().toPath().resolve("library-syntax");
        T13EvidenceCommand.main(new String[] {"syntax", substitute.toString(), input.toString(), output.toString(), "test"});
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T13 substitute shared library");
        assertEquals("indeterminate", result.required("syntax"));
    }

    @Test
    @Category(IndependentTools.class)
    public void syntaxCommandRejectsPinAssignmentsThatRedirectTheActualTool() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path substitute = temporary.getRoot().toPath().resolve("redirected-repository");
        Files.createDirectories(substitute.resolve("scripts/container-bin"));
        Files.copy(root.resolve("scripts/container-bin/qpdf"), substitute.resolve("scripts/container-bin/qpdf"),
                java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
        Files.createDirectories(substitute.resolve(".build-cache"));
        Files.createSymbolicLink(substitute.resolve(".build-cache/qpdf"), root.resolve(".build-cache/qpdf"));
        Path fakeCache = temporary.getRoot().toPath().resolve("redirected-cache");
        Path fakeVersion = Files.createDirectories(fakeCache.resolve("12.4.0/bin")).getParent();
        Path fake = fakeVersion.resolve("bin/qpdf");
        Files.write(fake, "#!/bin/sh\nprintf 'qpdf version 12.4.0\\n'\n".getBytes(StandardCharsets.US_ASCII));
        assertTrue(fake.toFile().setExecutable(true));
        for (String marker : new String[] {".archive-sha256", ".binary-sha256"}) {
            Files.copy(root.resolve(".build-cache/qpdf/12.4.0/" + marker), fakeVersion.resolve(marker));
        }
        String pin = new String(Files.readAllBytes(root.resolve("scripts/qpdf-pin.properties")), StandardCharsets.US_ASCII);
        Files.write(substitute.resolve("scripts/qpdf-pin.properties"),
                (pin + "QPDF_CACHE_DIRECTORY=" + fakeCache + "\n").getBytes(StandardCharsets.US_ASCII));
        Path input = temporary.getRoot().toPath().resolve("redirected-truncated.pdf");
        Files.write(input, "%PDF-2.0\n1 0 obj\n".getBytes(StandardCharsets.US_ASCII));
        Path output = temporary.getRoot().toPath().resolve("redirected-syntax");
        T13EvidenceCommand.main(new String[] {"syntax", substitute.toString(), input.toString(), output.toString(), "test"});
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T13 redirected syntax tool");
        assertEquals("indeterminate", result.required("syntax"));
    }

    @Test
    @Category(IndependentTools.class)
    public void syntaxCommandCannotQualifyASubstituteToolByItsVersionLabel() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path substitute = temporary.getRoot().toPath().resolve("substitute-repository");
        Path scripts = Files.createDirectories(substitute.resolve("scripts/container-bin"));
        Files.copy(root.resolve("scripts/qpdf-pin.properties"), substitute.resolve("scripts/qpdf-pin.properties"));
        Path wrapper = scripts.resolve("qpdf");
        Files.write(wrapper, "#!/bin/sh\nprintf 'qpdf version 12.4.0\\n'\n".getBytes(StandardCharsets.US_ASCII));
        assertTrue(wrapper.toFile().setExecutable(true));
        Path input = root.resolve("capabilities/profiles/T13-text/fixtures/nested-split-type3.pdf");
        Path output = temporary.getRoot().toPath().resolve("substitute-syntax");
        T13EvidenceCommand.main(new String[] {"syntax", substitute.toString(), input.toString(), output.toString(), "test"});
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T13 substitute syntax tool");
        assertEquals("indeterminate", result.required("syntax"));
        assertEquals(EvidenceFiles.sha256(input), result.required("input-sha256"));
    }

    @Test
    @Category(IndependentTools.class)
    public void syntaxCommandBindsExactExtractionInputsAndDetectsTruncation() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        for (String product : new String[] {"nested-split-type3", "marked-structure", "embedded-font-kinds",
                "embedded-font-inheritance", "uncertain-geometry"}) {
            Path input = root.resolve("capabilities/profiles/T13-text/fixtures/" + product + ".pdf");
            Path output = temporary.getRoot().toPath().resolve("syntax-" + product);
            T13EvidenceCommand.main(new String[] {"syntax", root.toString(), input.toString(), output.toString(), "test"});
            PinProperties result = PinProperties.load(output.resolve("result.properties"), "T13 syntax");
            assertEquals("pass", result.required("syntax"));
            assertEquals("T13-text-logical-structure", result.required("profile"));
            assertEquals(EvidenceFiles.sha256(input), result.required("input-sha256"));
            assertEquals(EvidenceFiles.sha256(input), EvidenceFiles.sha256(output.resolve("extraction.pdf")));
            assertTrue(Files.size(output.resolve("syntax.txt")) > 0);
            assertTrue(Files.size(output.resolve("syntax.md")) > 0);
        }
        Path invalid = temporary.getRoot().toPath().resolve("truncated.pdf");
        Files.write(invalid, "%PDF-2.0\n1 0 obj\n".getBytes(StandardCharsets.US_ASCII));
        Path output = temporary.getRoot().toPath().resolve("syntax-negative");
        T13EvidenceCommand.main(new String[] {"syntax", root.toString(), invalid.toString(), output.toString(), "test"});
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T13 syntax control");
        assertEquals("fail", result.required("syntax"));
        assertEquals(EvidenceFiles.sha256(invalid), result.required("input-sha256"));
    }

    @Test
    public void fontRasterCommandRejectsSecondaryDamageOutsideTheOriginalOnePixelEdges() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path primary = root.resolve("capabilities/profiles/T13-fonts/embedded-font-kinds-reference.png");
        Path controls = Paths.get(System.getProperty("folio.t13.fontEdgeControlsOutput",
                temporary.getRoot().toPath().resolve("font-edge-controls").toString()));
        Files.createDirectory(controls);
        for (String control : new String[] {"edge-only", "missing-glyph", "interior-hole", "outside-ink"}) {
            BufferedImage raster = ImageIO.read(primary.toFile());
            if ("edge-only".equals(control)) { raster.setRGB(20, 136, 0xffffff); }
            if ("missing-glyph".equals(control)) {
                for (int y = 135; y < 161; y++) {
                    for (int x = 19; x < 37; x++) { raster.setRGB(x, y, 0xffffff); }
                }
            }
            if ("interior-hole".equals(control)) { raster.setRGB(23, 139, 0xffffff); }
            if ("outside-ink".equals(control)) { raster.setRGB(1, 1, 0); }
            Path secondary = controls.resolve(control + ".png");
            assertTrue(ImageIO.write(raster, "png", secondary.toFile()));
            Path output = controls.resolve(control);
            T13EvidenceCommand.main(new String[] {"font-raster-agreement", primary.toString(), secondary.toString(), output.toString()});
            PinProperties result = PinProperties.load(output.resolve("result.properties"), "T13 secondary-only control");
            assertEquals("edge-only".equals(control) ? "pass" : "fail", result.required("raster-agreement"));
            assertEquals(EvidenceFiles.sha256(primary), result.required("primary-sha256"));
            assertEquals(EvidenceFiles.sha256(secondary), result.required("secondary-sha256"));
            assertTrue(Long.parseLong(result.required("changed-pixels")) < 1120);
            if (!"edge-only".equals(control)) { assertTrue(Long.parseLong(result.required("outside-edge-pixels")) > 0); }
        }
    }

    @Test
    @Category(IndependentTools.class)
    public void visualCommandRendersOriginalEmbeddedSimpleAndCidFontsAtDeclaredHorizontalAndVerticalOrigins() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path input = root.resolve("capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf");
        Path output = Paths.get(System.getProperty("folio.t13.fontVisualOutput",
                temporary.getRoot().toPath().resolve("font-visual").toString()));
        T13EvidenceCommand.main(new String[] {"visual", root.toString(), input.toString(),
            "embedded-font-kinds", output.toString(), "test"});
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T13 original font geometry");
        assertEquals("pass", result.required("visual"));
        assertEquals("pass", result.required("page.1.visual"));
        assertEquals(EvidenceFiles.sha256(input), result.required("input-sha256"));
        assertTrue(Files.size(output.resolve("page-1-pdfium.png")) > 0);
        assertTrue(Files.size(output.resolve("page-1-implementation.png")) > 0);
        assertTrue(Files.size(output.resolve("page-1-difference.png")) > 0);
        assertTrue(Files.size(output.resolve("page-1-renderer-difference.png")) > 0);
        PinProperties agreement = PinProperties.load(output.resolve("font-raster-agreement.properties"), "T13 original font edges");
        assertEquals("pass", agreement.required("raster-agreement"));
        assertEquals("0", agreement.required("outside-edge-pixels"));
    }

    @Test
    @Category(IndependentTools.class)
    public void visualCommandQualifiesOriginalSplitAndNestedType3GeometryAgainstExactPixels() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path input = root.resolve("capabilities/profiles/T13-text/fixtures/nested-split-type3.pdf");
        Path output = temporary.getRoot().toPath().resolve("original-visual");
        T13EvidenceCommand.main(new String[] {"visual", root.toString(), input.toString(),
            "nested-split-type3", output.toString(), "test"});
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T13 original visual qualification");
        assertEquals(EvidenceFiles.sha256(input), result.required("input-sha256"));
        assertEquals("T13-text-logical-structure", result.required("profile"));
        assertEquals("pass", result.required("visual"));
        assertEquals("pass", result.required("page.1.visual"));
        assertEquals("1", result.required("page-count"));
        String record = new String(Files.readAllBytes(output.resolve("page-1-visual.md")), StandardCharsets.UTF_8);
        assertTrue(record.contains("Input exact SHA-256"));
        assertTrue(record.contains(EvidenceFiles.sha256(input)));
        assertTrue(Files.size(output.resolve("page-1-pdfium.png")) > 0);
        assertTrue(Files.size(output.resolve("page-1-implementation.png")) > 0);
        assertTrue(Files.size(output.resolve("page-1-difference.png")) > 0);
        assertTrue(Files.size(output.resolve("page-1-renderer-difference.png")) > 0);
        assertTrue(Files.size(output.resolve("page-1-visual.txt")) > 0);
    }

    @Test
    @Category(IndependentTools.class)
    public void semanticControlsDetectChangedContentMappingsAndStructureRelationships() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path output = Paths.get(System.getProperty("folio.t13.semanticControlsOutput",
                temporary.getRoot().toPath().resolve("semantic-controls").toString()));
        T13EvidenceCommand.main(new String[] {"semantic-controls", root.toString(), output.toString(), "HARDENED_WORKER"});
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T13 actual semantic controls");
        assertEquals("pass", result.required("positive"));
        assertEquals("fail", result.required("semantic"));
        assertEquals("12", result.required("control-count"));
        for (String defect : new String[] {"contents-order", "form-position", "font-width", "replacement", "alternate",
                "language", "mcr", "parent-tree", "object-reference", "role-target", "empty-replacement", "namespace-reference"}) {
            Path directory = output.resolve(defect);
            PinProperties control = PinProperties.load(directory.resolve("result.properties"), defect);
            assertEquals("fail", result.required(defect));
            assertEquals("fail", control.required("semantic"));
            assertEquals(EvidenceFiles.sha256(directory.resolve("extraction.pdf")), control.required("input-sha256"));
            assertTrue(Files.size(directory.resolve("qpdf/qpdf.json")) > 0);
            assertTrue(Files.size(directory.resolve("qpdf/observed.json")) > 0);
            assertTrue(Files.size(directory.resolve("semantic.txt")) > 0);
        }
        requireCompleteRetainedManifest(output, result);
    }

    @Test
    @Category(IndependentTools.class)
    public void semanticCommandBindsIndependentGraphAndCompletePublicValuesInTheSelectedNativeMode() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        for (String mode : new String[] {"IN_PROCESS", "HARDENED_WORKER"}) {
            Path products = temporary.getRoot().toPath().resolve("semantic-products-" + mode);
            T13EvidenceCommand.main(new String[] {"products", root.toString(), products.toString(), mode});
            for (String name : new String[] {"nested-split-type3", "marked-structure", "embedded-font-kinds",
                    "embedded-font-inheritance", "uncertain-geometry"}) {
                for (String api : new String[] {"native", "facade"}) {
                    String actual = "native".equals(api) ? mode : "IN_PROCESS";
                    Path input = products.resolve(api + "-" + name + "/extraction.pdf");
                    Path output = temporary.getRoot().toPath().resolve("semantic-" + api + "-" + name + "-" + mode);
                    T13EvidenceCommand.main(new String[] {"semantic", root.toString(), input.toString(),
                        name, output.toString(), actual});
                    PinProperties result = PinProperties.load(output.resolve("result.properties"), "T13 independent semantics");
                    assertEquals("pass", result.required("semantic"));
                    assertEquals("pass", result.required("public-observation"));
                    assertEquals("independent-qpdf-decoded-graph-preservation", result.required("semantic-scope"));
                    assertEquals(actual, result.required("execution-profile"));
                    assertEquals(EvidenceFiles.sha256(input), result.required("input-sha256"));
                    assertTrue(Files.size(output.resolve("qpdf/qpdf.json")) > 0);
                    assertTrue(Files.size(output.resolve("observation.properties")) > 0);
                }
            }
        }
    }

    @Test
    @Category(IndependentTools.class)
    public void visualCommandKeepsMarkedReplacementAndLogicalObjectsSeparateFromPaintedGlyphs() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path input = root.resolve("capabilities/profiles/T13-text/fixtures/marked-structure.pdf");
        Path output = temporary.getRoot().toPath().resolve("marked-visual");
        T13EvidenceCommand.main(new String[] {"visual", root.toString(), input.toString(),
            "marked-structure", output.toString(), "test"});
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T13 marked original visual qualification");
        assertEquals("pass", result.required("visual"));
        assertEquals("pass", result.required("page.1.visual"));
        assertEquals(EvidenceFiles.sha256(input), result.required("input-sha256"));
        assertTrue(Files.size(output.resolve("page-1-pdfium.png")) > 0);
        assertTrue(Files.size(output.resolve("page-1-difference.png")) > 0);
    }

    @Test
    @Category(IndependentTools.class)
    public void visualControlsDetectChangedGlyphInkAndNestedFormPlacement() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path output = Paths.get(System.getProperty("folio.t13.visualControlsOutput",
                temporary.getRoot().toPath().resolve("visual-controls").toString()));
        T13EvidenceCommand.main(new String[] {"visual-controls", root.toString(), output.toString(), "test"});
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T13 actual visual controls");
        assertEquals("pass", result.required("positive"));
        assertEquals("fail", result.required("visual"));
        assertEquals("4", result.required("control-count"));
        assertEquals("pass", result.required("positive.embedded-font-kinds"));
        for (String defect : new String[] {"glyph-ink", "form-position", "simple-font-position", "vertical-font-position"}) {
            Path directory = output.resolve(defect);
            PinProperties control = PinProperties.load(directory.resolve("result.properties"), defect);
            assertEquals("fail", result.required(defect));
            assertEquals("fail", control.required("visual"));
            assertEquals("fail", control.required("page.1.visual"));
            assertEquals(EvidenceFiles.sha256(directory.resolve("extraction.pdf")), control.required("input-sha256"));
            assertTrue(Files.size(directory.resolve("page-1-pdfium.png")) > 0);
            assertTrue(Files.size(directory.resolve("page-1-difference.png")) > 0);
        }
        assertEquals("3", result.required("raster-control-count"));
        String primaryHash = EvidenceFiles.sha256(root.resolve("capabilities/profiles/T13-fonts/embedded-font-kinds-reference.png"));
        for (String name : new String[] {"edge-only", "missing-glyph", "interior-hole", "outside-ink"}) {
            Path directory = output.resolve("raster/" + name);
            PinProperties control = PinProperties.load(directory.resolve("result.properties"), "T13 retained raster control");
            String verdict = "edge-only".equals(name) ? "pass" : "fail";
            assertEquals(verdict, result.required("raster." + name));
            assertEquals(verdict, control.required("raster-agreement"));
            assertEquals(primaryHash, control.required("primary-sha256"));
            assertEquals(EvidenceFiles.sha256(directory.resolve("secondary.png")), control.required("secondary-sha256"));
        }
        requireCompleteRetainedManifest(output, result);
    }

    @Test
    public void productsCommandPreservesEveryMappingConfidenceDiagnosticAndUnrotatedGeometry() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path output = temporary.getRoot().toPath().resolve("uncertain-products");
        T13EvidenceCommand.main(new String[] {"products", root.toString(), output.toString(), "HARDENED_WORKER"});
        for (String api : new String[] {"native", "facade"}) {
            Path directory = output.resolve(api + "-uncertain-geometry");
            PinProperties values = PinProperties.load(directory.resolve("observation.properties"), "T13 uncertainty and geometry");
            assertEquals("A AA", values.required("pages.0.text"));
            assertEquals("90", values.required("pages.0.rotation"));
            assertEquals("2", values.required("pages.0.user-unit"));
            assertEquals("39.5", values.required("pages.0.items.2.matrix.4"));
            assertEquals("73", values.required("pages.0.items.2.matrix.5"));
            assertEquals("2.5", values.required("pages.0.items.1.advance.0"));
            assertEquals("EXPLICIT", values.required("pages.0.items.3.confidence"));
            assertEquals("true", values.required("pages.0.items.3.unicode-present"));
            assertEquals("CONTRADICTORY", values.required("pages.0.items.4.confidence"));
            assertEquals("false", values.required("pages.0.items.4.unicode-present"));
            assertEquals("Z", values.required("pages.0.items.4.explicit"));
            assertEquals("A", values.required("pages.0.items.4.inferred"));
            assertEquals("MISSING", values.required("pages.0.items.5.confidence"));
            assertEquals("false", values.required("pages.0.items.5.explicit-present"));
            assertEquals("false", values.required("pages.0.items.5.inferred-present"));
            assertEquals("2", values.required("diagnostics.count"));
            assertEquals("CONTRADICTORY_UNICODE_MAPPING", values.required("diagnostics.0.code"));
            assertEquals("MISSING_UNICODE_MAPPING", values.required("diagnostics.1.code"));
            assertEquals("6", values.required("diagnostics.1.item"));
            assertEquals("42", values.required("diagnostics.1.source"));
            assertEquals("No defensible Unicode mapping is available for this character code.", values.required("diagnostics.1.message"));
            PinProperties publication = PinProperties.load(directory.resolve("publication.properties"), "T13 uncertain publication");
            assertEquals("pass", publication.required("source-preserved"));
            assertEquals("pass", publication.required("reopened"));
        }
    }

    @Test
    public void productsCommandExtractsAndReopensTheEmbeddedSimpleAndCidFontKinds() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path output = temporary.getRoot().toPath().resolve("font-kind-products");
        T13EvidenceCommand.main(new String[] {"products", root.toString(), output.toString(), "HARDENED_WORKER"});
        for (String product : new String[] {"embedded-font-kinds", "embedded-font-inheritance"}) {
        for (String api : new String[] {"native", "facade"}) {
            Path directory = output.resolve(api + "-" + product);
            PinProperties observed = PinProperties.load(directory.resolve("observation.properties"), "T13 embedded font kinds");
            assertEquals("AAAZAZA", observed.required("pages.0.text"));
            assertEquals("7", observed.required("pages.0.items.count"));
            assertEquals("INFERRED", observed.required("pages.0.items.2.confidence"));
            assertEquals("EXPLICIT", observed.required("pages.0.items.3.confidence"));
            assertEquals("Z", observed.required("pages.0.items.3.explicit"));
            assertEquals("0041", observed.required("pages.0.items.4.source"));
            assertEquals("35", observed.required("pages.0.items.4.matrix.4"));
            assertEquals("64", observed.required("pages.0.items.4.matrix.5"));
            assertEquals("-20", observed.required("pages.0.items.4.advance.1"));
            assertEquals("85", observed.required("pages.0.items.6.matrix.4"));
            assertEquals("0", observed.required("diagnostics.count"));
            PinProperties receipt = PinProperties.load(directory.resolve("publication.properties"), "T13 font publication");
            assertEquals("pass", receipt.required("reopened"));
            assertEquals("COMMITTED", receipt.required("status"));
        }
        }
    }

    @Test
    public void productsCommandRecordsCompleteMarkedStructureAndPreservesEmptyOptionalValues() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path output = temporary.getRoot().toPath().resolve("structure-products");
        T13EvidenceCommand.main(new String[] {"products", root.toString(), output.toString(), "HARDENED_WORKER"});
        for (String api : new String[] {"native", "facade"}) {
            Path directory = output.resolve(api + "-marked-structure");
            PinProperties observed = PinProperties.load(directory.resolve("observation.properties"), "T13 marked structure");
            assertEquals("OuterForm", observed.required("pages.0.text"));
            assertEquals("4", observed.required("pages.0.marked-content.count"));
            assertEquals("1", observed.required("pages.0.marked-content.2.stream"));
            assertEquals("1", observed.required("roots.count"));
            assertEquals("Document", observed.required("roots.0.resolved-role"));
            assertEquals("urn:folio:t13:roles", observed.required("roots.0.namespace"));
            assertEquals("http://iso.org/pdf2/ssn", observed.required("roots.0.resolved-namespace"));
            assertEquals("4", observed.required("roots.0.children.0.element.children.count"));
            assertEquals("3", observed.required("roots.0.children.0.element.children.1.marked-content.sequence"));
            assertEquals("Text", observed.required("roots.0.children.0.element.children.2.object-reference.subtype"));
            assertEquals("fr", observed.required("roots.0.children.0.element.effective-language"));
            assertEquals("false", observed.required("roots.0.children.0.element.actual-present"));
            assertEquals("true", observed.required("roots.0.children.0.element.children.3.element.actual-present"));
            assertEquals("", observed.optional("roots.0.children.0.element.children.3.element.actual"));
            assertEquals("2", observed.required("roots.0.children.0.element.namespace-reference"));
            assertEquals("2", observed.required("roots.0.children.0.element.children.3.element.namespace-reference"));
            PinProperties receipt = PinProperties.load(directory.resolve("publication.properties"), "T13 structure publication");
            assertEquals("pass", receipt.required("reopened"));
        }
    }

    @Test
    public void productsCommandRecordsNativeAndFacadeExtractionPublicationAndReopenInTheirActualModes() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        for (String mode : new String[] {"IN_PROCESS", "HARDENED_WORKER"}) {
            Path output = temporary.getRoot().toPath().resolve("products-" + mode);
            T13EvidenceCommand.main(new String[] {"products", root.toString(), output.toString(), mode});
            PinProperties phase = PinProperties.load(output.resolve("products.properties"), "T13 products only");
            assertEquals("products-only", phase.required("phase"));
            assertEquals(mode, phase.required("native-execution-profile"));
            assertEquals("IN_PROCESS", phase.required("facade-execution-profile"));
            for (String api : new String[] {"native", "facade"}) {
                Path directory = output.resolve(api + "-nested-split-type3");
                PinProperties receipt = PinProperties.load(directory.resolve("publication.properties"), "T13 actual publication");
                assertEquals("native".equals(api) ? mode : "IN_PROCESS", receipt.required("execution-profile"));
                assertEquals("COMMITTED", receipt.required("status"));
                assertEquals("target", receipt.required("target-name"));
                assertEquals("false", receipt.required("partial-output-possible"));
                assertEquals(EvidenceFiles.sha256(directory.resolve("extraction.pdf")), receipt.required("output-sha256"));
                assertEquals("pass", receipt.required("source-preserved"));
                assertEquals("pass", receipt.required("reopened"));
                PinProperties observed = PinProperties.load(directory.resolve("observation.properties"), "T13 detached observation");
                assertEquals("1", observed.required("pages.count"));
                assertEquals("AA", observed.required("pages.0.text"));
                assertEquals("2", observed.required("pages.0.items.count"));
                assertEquals("INFERRED", observed.required("pages.0.items.1.confidence"));
                assertEquals("FILL", observed.required("pages.0.items.1.rendering-mode"));
                assertEquals("41", observed.required("pages.0.items.1.source"));
                assertEquals("25", observed.required("pages.0.items.1.matrix.4"));
                assertEquals("40", observed.required("pages.0.items.1.matrix.5"));
                assertEquals("10", observed.required("pages.0.items.1.advance.0"));
                assertEquals("0", observed.required("roots.count"));
                assertEquals("0", observed.required("diagnostics.count"));
            }
        }
    }
}
