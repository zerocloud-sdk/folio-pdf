package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

/** Qualifies actual independent font checks using original legal and illegal PDFs. */
@Category(IndependentTools.class)
public final class T13StandardsQualificationTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void programRecorderDoesNotPublishFailureReportsThroughAReplacedAncestor() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path input = root.resolve("capabilities/profiles/T13-text/fixtures/marked-structure.pdf");
        for (boolean scopeExists : new boolean[] {false, true}) {
            Path staged = programRoot(root, scopeExists ? "replaced-scope-parent" : "replaced-input-directory");
            Path output = staged.resolve("observation");
            Path outside = Files.createDirectory(staged.resolve("unrelated"));
            Path moved = outside.resolve("moved-input");
            java.util.concurrent.atomic.AtomicReference<java.util.List<String>> before = new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.FutureTask<Void> mutation = new java.util.concurrent.FutureTask<Void>(() -> {
                Path observedDirectory = output.resolve(scopeExists ? "input/cmaps" : "input");
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
                while (!Files.isDirectory(observedDirectory)) {
                    if (System.nanoTime() >= deadline) { throw new AssertionError("No program input directory"); }
                    Thread.sleep(1);
                }
                Files.move(output.resolve("input"), moved);
                Files.createSymbolicLink(output.resolve("input"), moved);
                before.set(retainedTree(outside));
                return null;
            });
            new Thread(mutation, "t13-program-output-parent-control").start();
            try {
                T13EvidenceCommand.main(new String[] {"program-standards", staged.toString(), input.toString(), output.toString()});
            } catch (java.io.IOException refused) {
                // Refusal must still preserve the directory outside the evidence output.
            } finally {
                mutation.get(35, java.util.concurrent.TimeUnit.SECONDS);
            }
            assertEquals("Failure reporting must not create files or directories outside the output", before.get(), retainedTree(outside));
            if (Files.isRegularFile(output.resolve("result.properties"))) {
                PinProperties result = PinProperties.load(output.resolve("result.properties"), "Replaced T13 ancestor");
                assertEquals("indeterminate", result.required("program-standards"));
            }
        }
    }

    private java.util.List<String> retainedTree(Path root) throws Exception {
        java.util.List<String> entries = new java.util.ArrayList<String>();
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                entries.add(root.relativize(path).toString() + (Files.isRegularFile(path) ? ":" + EvidenceFiles.sha256(path) : "/"));
            }
        }
        java.util.Collections.sort(entries);
        return entries;
    }

    @Test
    public void programRecorderRejectsChangedProducerArtifactsBeforeAggregation() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path input = root.resolve("capabilities/profiles/T13-text/fixtures/marked-structure.pdf");
        for (String name : new String[] {"qpdf-version.txt", "qpdf.json"}) {
            Path staged = programRoot(root, "early-artifact-" + name);
            Path output = staged.resolve("observation");
            Path artifact = output.resolve("input/cmaps/" + name);
            java.util.concurrent.FutureTask<Void> mutation = new java.util.concurrent.FutureTask<Void>(() -> {
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
                while (!Files.isRegularFile(artifact) || Files.size(artifact) == 0) {
                    if (System.nanoTime() >= deadline) { throw new AssertionError("The actual qpdf artifact was not published"); }
                    Thread.sleep(1);
                }
                Files.write(artifact, "Changed after the actual producer output\n".getBytes(StandardCharsets.US_ASCII));
                return null;
            });
            new Thread(mutation, "t13-program-producer-artifact-control").start();
            try {
                T13EvidenceCommand.main(new String[] {"program-standards", staged.toString(), input.toString(), output.toString()});
            } finally {
                mutation.get(35, java.util.concurrent.TimeUnit.SECONDS);
            }
            PinProperties result = PinProperties.load(output.resolve("result.properties"), "Changed T13 producer artifact");
            assertEquals(name, "indeterminate", result.required("program-standards"));
            assertEquals(name, "0", result.required("qualified-rule-count"));
        }
    }

    @Test
    public void programRecorderRejectsMissingOrSubstitutedAuthoritiesAndControls() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path input = root.resolve("capabilities/profiles/T13-text/fixtures/marked-structure.pdf");
        for (String kind : new String[] {"missing-observer", "substitute-observer", "changed-profile", "changed-qpdf-pin",
                "missing-control", "changed-control", "missing-wheel"}) {
            Path staged = programRoot(root, kind);
            if ("missing-observer".equals(kind)) { Files.delete(staged.resolve("scripts/t13-program-standards.py")); }
            if ("substitute-observer".equals(kind)) {
                Files.write(staged.resolve("scripts/t13-program-standards.py"),
                        "print('profile=T13-text-logical-structure\\nstandards=pass')\n".getBytes(StandardCharsets.US_ASCII));
            }
            if ("changed-profile".equals(kind) || "changed-qpdf-pin".equals(kind)) {
                Path authority = staged.resolve("changed-profile".equals(kind)
                        ? "capabilities/profiles/T13-standards/program-qualification.properties" : "scripts/qpdf-pin.properties");
                Files.write(authority, "\n# Same labels, different authority bytes\n".getBytes(StandardCharsets.US_ASCII),
                        java.nio.file.StandardOpenOption.APPEND);
            }
            if ("missing-control".equals(kind) || "changed-control".equals(kind)) {
                Path fixtures = staged.resolve("capabilities/profiles/T13-standards/fixtures");
                Files.delete(fixtures);
                Files.createDirectory(fixtures);
                try (java.util.stream.Stream<Path> files = Files.list(root.resolve("capabilities/profiles/T13-standards/fixtures"))) {
                    for (Path file : (Iterable<Path>) files::iterator) { Files.createSymbolicLink(fixtures.resolve(file.getFileName()), file); }
                }
                Path control = fixtures.resolve("program-cmap-block-count.pdf");
                Files.delete(control);
                if ("changed-control".equals(kind)) { Files.copy(input, control); }
            }
            if ("missing-wheel".equals(kind)) {
                Files.delete(staged.resolve(".build-cache"));
                Files.createDirectory(staged.resolve(".build-cache"));
                Files.createSymbolicLink(staged.resolve(".build-cache/qpdf"), root.resolve(".build-cache/qpdf"));
            }
            Path output = staged.resolve("observation");
            T13EvidenceCommand.main(new String[] {"program-standards", staged.toString(), input.toString(), output.toString()});
            PinProperties result = PinProperties.load(output.resolve("result.properties"), "Unavailable T13 program authority");
            assertEquals(kind, "indeterminate", result.required("program-standards"));
            assertEquals(kind, "0", result.required("qualified-rule-count"));
            assertEquals(kind, "0", result.required("negative-control-count"));
        }
    }

    private Path programRoot(Path root, String name) throws Exception {
        Path base = Paths.get(System.getProperty("folio.t13.programControls", temporary.getRoot().toString()));
        Files.createDirectories(base);
        Path staged = Files.createDirectory(base.resolve(name));
        Path profiles = Files.createDirectories(staged.resolve("capabilities/profiles/T13-standards"));
        Files.copy(root.resolve("capabilities/profiles/T13-standards/program-qualification.properties"),
                profiles.resolve("program-qualification.properties"));
        Files.createSymbolicLink(profiles.resolve("fixtures"), root.resolve("capabilities/profiles/T13-standards/fixtures"));
        Files.createSymbolicLink(staged.resolve(".build-cache"), root.resolve(".build-cache"));
        Files.createDirectories(staged.resolve("scripts/container-bin"));
        for (String script : new String[] {"t13-program-standards.py", "qpdf-pin.properties", "t13-qpdf-runtime.sha256", "container-bin/qpdf"}) {
            Files.copy(root.resolve("scripts/" + script), staged.resolve("scripts/" + script),
                    java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
        }
        return staged;
    }

    @Test
    public void declarationRecorderDoesNotFollowLinksWhenPublishingItsFinalEvidence() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path input = root.resolve("capabilities/profiles/T13-text/fixtures/marked-structure.pdf");
        java.util.List<String> failures = new java.util.ArrayList<String>();
        for (String name : new String[] {"declarations.txt", "retained-files.sha256", "result.properties"}) {
            Path staged = declarationRoot(root, "final-link-" + name);
            Path output = staged.resolve("observation");
            Path external = staged.resolve("unrelated.txt");
            byte[] original = "Unrelated owned test file\n".getBytes(StandardCharsets.US_ASCII);
            Files.write(external, original);
            java.util.concurrent.FutureTask<Void> mutation = new java.util.concurrent.FutureTask<Void>(() -> {
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
                while (!Files.isDirectory(output.resolve("arlington-core"))) {
                    if (System.nanoTime() >= deadline) { throw new AssertionError("No second declaration group started"); }
                    Thread.sleep(5);
                }
                Files.createSymbolicLink(output.resolve(name), external);
                return null;
            });
            new Thread(mutation, "t13-final-evidence-link-control").start();
            try {
                T13EvidenceCommand.main(new String[] {"declaration-standards", staged.toString(), input.toString(), output.toString()});
                PinProperties result = PinProperties.load(output.resolve("result.properties"), "Linked T13 final evidence");
                if ("pass".equals(result.required("declarations"))) { failures.add(name + ": pass"); }
            } catch (java.io.IOException refused) {
                // Refusing publication is safe; no PASS evidence may be produced.
            } finally {
                mutation.get(35, java.util.concurrent.TimeUnit.SECONDS);
            }
            if (!java.util.Arrays.equals(original, Files.readAllBytes(external))) { failures.add(name + ": outside file changed"); }
        }
        assertEquals("Final evidence publication must preserve ownership", java.util.Collections.emptyList(), failures);
    }

    @Test
    public void declarationRecorderRejectsMissingOrChangedRetainedGroupEvidence() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path input = root.resolve("capabilities/profiles/T13-text/fixtures/marked-structure.pdf");
        java.util.List<String> failures = new java.util.ArrayList<String>();
        for (String kind : new String[] {"missing-control", "changed-control", "changed-report", "missing-group-record", "early-changed-report"}) {
            Path staged = declarationRoot(root, "retained-" + kind);
            Path output = staged.resolve("observation");
            java.util.concurrent.FutureTask<Void> mutation = new java.util.concurrent.FutureTask<Void>(() -> {
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
                Path report = output.resolve("pdfcpu/negative-annotation-contents-string.txt");
                while ("early-changed-report".equals(kind)
                        ? !Files.isRegularFile(report) || Files.size(report) == 0
                        : !Files.isDirectory(output.resolve("arlington-core"))) {
                    if (System.nanoTime() >= deadline) { throw new AssertionError("No second declaration group started"); }
                    Thread.sleep(5);
                }
                Path control = output.resolve("pdfcpu/control-annotation-contents-string.pdf");
                if ("missing-control".equals(kind)) { Files.delete(control); }
                if ("changed-control".equals(kind)) {
                    Files.copy(input, control, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                if ("changed-report".equals(kind) || "early-changed-report".equals(kind)) {
                    Files.write(report,
                            "exit=0\nvalidation ok\n".getBytes(StandardCharsets.US_ASCII));
                }
                if ("missing-group-record".equals(kind)) { Files.delete(output.resolve("pdfcpu/standards.properties")); }
                return null;
            });
            new Thread(mutation, "t13-retained-declaration-control").start();
            try {
                T13EvidenceCommand.main(new String[] {"declaration-standards", staged.toString(), input.toString(), output.toString()});
            } finally {
                mutation.get(35, java.util.concurrent.TimeUnit.SECONDS);
            }
            PinProperties result = PinProperties.load(output.resolve("result.properties"), "Retained T13 artifacts");
            if (!"indeterminate".equals(result.required("declarations")) || !"0".equals(result.required("qualified-rule-count"))) {
                failures.add(kind + ": " + result.required("declarations") + "/" + result.required("qualified-rule-count"));
            }
        }
        assertEquals("Changed retained evidence cannot supply a qualified rule union", java.util.Collections.emptyList(), failures);
    }

    @Test
    public void programRecorderQualifiesEveryScopeWithAllOriginalNegativeControls() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path input = root.resolve("capabilities/profiles/T13-text/fixtures/marked-structure.pdf");
        Path output = Paths.get(System.getProperty("folio.t13.programOutput",
                temporary.getRoot().toPath().resolve("programs").toString()));
        T13EvidenceCommand.main(new String[] {"program-standards", root.toString(), input.toString(), output.toString()});
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T13 program predicates");
        assertEquals("pass", result.required("program-standards"));
        assertEquals("programs-only", result.required("standards-scope"));
        assertEquals("42", result.required("qualified-rule-count"));
        assertEquals("165", result.required("negative-control-count"));
        assertEquals(EvidenceFiles.sha256(input), result.required("input-sha256"));
        PinProperties profile = PinProperties.load(root.resolve("capabilities/profiles/T13-standards/program-qualification.properties"),
                "T13 original program controls");
        for (String scope : profile.required("scopes").split(",")) {
            PinProperties record = PinProperties.load(output.resolve("input/" + scope + "/result.properties"), "T13 " + scope);
            assertEquals("pass", record.required("standards"));
            assertEquals(EvidenceFiles.sha256(input), record.required("input-sha256"));
        }
        for (String control : profile.required("controls").split(",")) {
            PinProperties record = PinProperties.load(output.resolve("qualifications/" + control + "/result.properties"), "T13 " + control);
            assertEquals("fail", record.required("standards"));
            assertEquals(profile.required("negative." + control + ".scope"), record.required("scope"));
            assertEquals(profile.required("negative." + control + ".rule"), record.required("rule"));
            assertEquals(profile.required("negative." + control + ".sha256"), record.required("input-sha256"));
            assertEquals(profile.required("negative." + control + ".sha256"),
                    EvidenceFiles.sha256(output.resolve("control-" + control + ".pdf")));
        }
    }

    @Test
    public void declarationRecorderRejectsWholePinChangesAfterItsGroupHasFinished() throws Exception {
        changedAfterFirstGroup(false);
    }

    @Test
    public void declarationRecorderRejectsBinaryChangesAfterItsGroupHasFinished() throws Exception {
        changedAfterFirstGroup(true);
    }

    private void changedAfterFirstGroup(boolean binary) throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path staged = declarationRoot(root, binary ? "late-binary" : "late-pin");
        final Path changed;
        if (binary) {
            Files.delete(staged.resolve(".build-cache"));
            Files.createDirectory(staged.resolve(".build-cache"));
            Files.createSymbolicLink(staged.resolve(".build-cache/arlington"), root.resolve(".build-cache/arlington"));
            changed = staged.resolve(".build-cache/pdfcpu/0.15.0/pdfcpu");
            Files.createDirectories(changed.getParent());
            Files.copy(root.resolve(".build-cache/pdfcpu/0.15.0/pdfcpu"), changed, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
        } else {
            changed = staged.resolve("scripts/pdfcpu-pin.properties");
        }
        Path output = staged.resolve("observation");
        java.util.concurrent.FutureTask<Void> mutation = new java.util.concurrent.FutureTask<Void>(() -> {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
            while (!Files.isRegularFile(output.resolve("pdfcpu/standards.properties"))) {
                if (System.nanoTime() >= deadline) { throw new AssertionError("No completed pdfcpu observation"); }
                Thread.sleep(5);
            }
            Files.write(changed, binary ? new byte[] {0} : "\ntimeout-ms=11000\n".getBytes(StandardCharsets.US_ASCII),
                    java.nio.file.StandardOpenOption.APPEND);
            return null;
        });
        Thread writer = new Thread(mutation, "t13-declaration-authority-control");
        writer.start();
        try {
            T13EvidenceCommand.main(new String[] {"declaration-standards", staged.toString(),
                root.resolve("capabilities/profiles/T13-text/fixtures/marked-structure.pdf").toString(), output.toString()});
        } finally {
            mutation.get(35, java.util.concurrent.TimeUnit.SECONDS);
        }
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "Late changed T13 authority");
        assertEquals("indeterminate", result.required("declarations"));
        assertEquals("0", result.required("qualified-rule-count"));
    }

    @Test
    public void declarationRecorderRejectsChangedWholeToolPinsEvenWithTheGenuineCheckers() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        for (String name : new String[] {"pdfcpu", "t13-arlington"}) {
            Path staged = declarationRoot(root, "changed-" + name);
            Path pin = staged.resolve("scripts/" + name + "-pin.properties");
            Files.write(pin, (new String(Files.readAllBytes(pin), StandardCharsets.US_ASCII)
                    + "\ntimeout-ms=11000\n").getBytes(StandardCharsets.US_ASCII));
            Path output = staged.resolve("observation");
            T13EvidenceCommand.main(new String[] {"declaration-standards", staged.toString(),
                root.resolve("capabilities/profiles/T13-text/fixtures/marked-structure.pdf").toString(), output.toString()});
            PinProperties result = PinProperties.load(output.resolve("result.properties"), "Changed T13 pin");
            assertEquals(name, "indeterminate", result.required("declarations"));
        }
    }

    private Path declarationRoot(Path root, String name) throws Exception {
        Path base = Paths.get(System.getProperty("folio.t13.declarationControls", temporary.getRoot().toString()));
        Files.createDirectories(base);
        Path staged = Files.createDirectory(base.resolve(name));
        for (String link : new String[] {"capabilities", "build-tools", ".build-cache"}) {
            Files.createSymbolicLink(staged.resolve(link), root.resolve(link));
        }
        Files.createDirectory(staged.resolve("scripts"));
        for (String pin : new String[] {"pdfcpu", "t13-arlington"}) {
            Files.copy(root.resolve("scripts/" + pin + "-pin.properties"), staged.resolve("scripts/" + pin + "-pin.properties"));
        }
        return staged;
    }

    @Test
    public void declarationRecorderQualifiesTheExactRuleUnionWithEveryActualNegativeControl() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path input = root.resolve("capabilities/profiles/T13-text/fixtures/marked-structure.pdf");
        Path output = Paths.get(System.getProperty("folio.t13.declarationOutput",
                temporary.getRoot().toPath().resolve("declarations").toString()));
        T13EvidenceCommand.main(new String[] {"declaration-standards", root.toString(), input.toString(), output.toString()});
        PinProperties result = PinProperties.load(output.resolve("result.properties"), "T13 declaration union");
        assertEquals("pass", result.required("declarations"));
        assertEquals("declarations-only", result.required("standards-scope"));
        assertEquals("334", result.required("qualified-rule-count"));
        assertEquals(EvidenceFiles.sha256(input), result.required("input-sha256"));
        assertEquals(EvidenceFiles.sha256(input), EvidenceFiles.sha256(output.resolve("extraction.pdf")));
        java.util.Set<String> observed = new java.util.TreeSet<String>();
        for (String group : new String[] {"pdfcpu", "arlington-core", "arlington-fonts", "arlington-text",
                "arlington-cid", "arlington-descriptors", "arlington-cmaps"}) {
            Path directory = output.resolve(group);
            PinProperties record = PinProperties.load(directory.resolve("standards.properties"), "T13 " + group);
            assertEquals("pass", record.required("result"));
            assertEquals(EvidenceFiles.sha256(input), record.required("input-sha256"));
            assertEquals(EvidenceFiles.sha256(root.resolve("capabilities/profiles/T13-standards/" + group + ".properties")),
                    record.required("profile-sha256"));
            assertEquals(EvidenceFiles.sha256(root.resolve("scripts/" + (group.startsWith("arlington") ? "t13-arlington" : group)
                    + "-pin.properties")), record.required("pin-sha256"));
            for (String rule : record.required("covered-rules").split(",")) {
                assertTrue("Duplicate rule " + rule, observed.add(rule));
                assertTrue(Files.size(directory.resolve("control-" + rule + ".pdf")) > 0);
                assertTrue(Files.size(directory.resolve("negative-" + rule + ".txt")) > 0);
            }
        }
        assertEquals(new java.util.TreeSet<String>(Files.readAllLines(
                root.resolve("capabilities/profiles/T13-standards/required-rules.txt"), StandardCharsets.US_ASCII)), observed);
        assertEquals(334, observed.size());
    }

    @Test
    public void glyphQualificationAcceptsLegalTokensAndPrecisionAndRefusesUnobservedMappings() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path records = Paths.get(System.getProperty("folio.t13.glyphStandardsOutput",
                temporary.getRoot().toPath().resolve("glyph-boundaries").toString()));
        Files.createDirectory(records);
        for (String kind : new String[] {"delimiter", "fractional", "comments", "base-encoding",
                "empty-differences", "unmapped-glyph", "numeric-range"}) {
            Path input = root.resolve("capabilities/profiles/T13-standards/fixtures/glyph-boundary-" + kind + ".pdf");
            Path output = records.resolve(kind);
            StandardsEvidenceCommand.main(new String[] {output.toString(), root.resolve("scripts/t13-arlington-pin.properties").toString(),
                root.resolve("capabilities/profiles/T13-standards/arlington-text.properties").toString(), input.toString()});
            PinProperties result = PinProperties.load(output.resolve("standards.properties"), "T13 glyph boundaries");
            String expected = "delimiter".equals(kind) || "fractional".equals(kind) || "comments".equals(kind) ? "pass" : "indeterminate";
            assertEquals(new String(Files.readAllBytes(output.resolve("findings.txt")), StandardCharsets.UTF_8),
                    expected, result.required("result"));
            assertEquals(EvidenceFiles.sha256(input), result.required("input-sha256"));
        }
    }

    @Test
    public void allOriginalCasesQualifyTheirSharedPageFormStreamAndAnnotationRules() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path records = Paths.get(System.getProperty("folio.t13.coreStandardsOutput",
                temporary.getRoot().toPath().resolve("core").toString()));
        Files.createDirectory(records);
        for (String product : T13Corpus.PRODUCTS) {
            Path input = root.resolve("capabilities/profiles/T13-text/fixtures/" + product + ".pdf");
            if ("nested-split-type3".equals(product)) {
                input = root.resolve("capabilities/profiles/T13-standards/fixtures/positive-pdf20-nested-split-type3.pdf");
            }
            for (String checker : new String[] {"pdfcpu", "arlington-core", "arlington-text", "arlington-cid", "arlington-descriptors", "arlington-cmaps"}) {
                Path output = records.resolve(product + "-" + checker);
                Path pin = root.resolve("scripts/" + (checker.startsWith("arlington") ? "t13-arlington" : checker)
                        + "-pin.properties");
                Path profile = root.resolve("capabilities/profiles/T13-standards/" + checker + ".properties");
                StandardsEvidenceCommand.main(new String[] {output.toString(), pin.toString(), profile.toString(), input.toString()});
                PinProperties result = PinProperties.load(output.resolve("standards.properties"), "T13 core rules");
                assertEquals(new String(Files.readAllBytes(output.resolve("findings.txt")), StandardCharsets.UTF_8),
                        "pass", result.required("result"));
                assertEquals("T13-text-logical-structure", result.required("profile"));
                assertEquals(EvidenceFiles.sha256(input), result.required("input-sha256"));
            }
        }
    }

    @Test
    public void originalEmbeddedFontsAndInheritedCMapsQualifyEveryOwningFontAndVerticalWidths() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path pin = root.resolve(System.getProperty("folio.t13.arlingtonPin", "scripts/t13-arlington-pin.properties"));
        Path profile = root.resolve("capabilities/profiles/T13-standards/arlington-fonts.properties");
        Path records = Paths.get(System.getProperty("folio.t13.fontStandardsOutput",
                temporary.getRoot().toPath().resolve("standards").toString()));
        Files.createDirectory(records);
        for (String kind : new String[] {"direct", "inherited", "named-type3", "unnamed-type3",
                "font-name-only-type3", "descriptor-name-only-type3"}) {
            Path input = root.resolve("capabilities/profiles/T13-standards/fixtures/positive-" + kind + "-fonts.pdf");
            Path output = records.resolve(kind);
            StandardsEvidenceCommand.main(new String[] {output.toString(), pin.toString(), profile.toString(), input.toString()});
            PinProperties result = PinProperties.load(output.resolve("standards.properties"), "T13 original fonts");
            assertEquals(new String(Files.readAllBytes(output.resolve("findings.txt")), StandardCharsets.UTF_8),
                    "pass", result.required("result"));
            assertEquals("T13-text-logical-structure", result.required("profile"));
            assertEquals(EvidenceFiles.sha256(input), result.required("input-sha256"));
            assertEquals(8, result.required("covered-rules").split(",").length);
            for (String rule : result.required("covered-rules").split(",")) {
                assertTrue(Files.size(output.resolve("control-" + rule + ".pdf")) > 0);
                assertTrue(Files.size(output.resolve("negative-" + rule + ".txt")) > 0);
            }
        }
    }
}
