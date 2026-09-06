package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageObjectReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** All fixed T29 lines are observed after public Workflow publication and reopening. */
public final class T29ShapingEvidenceCommandTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void fourProfilesMatchTheFrozenGlyphGeometryAndLogicalRunsInBothModes() throws Exception {
        for (WorkflowExecutionProfile mode : WorkflowExecutionProfile.values()) {
            Path output = temporary.newFile().toPath();
            WorkflowOutcome<Void> creation = T29ShapingProducts.create(output, mode,
                    Paths.get(System.getProperty("folio.harfBuzzHelper")), temporary.newFolder().toPath());
            T29ShapingSemanticAssertions.Observation observed = T29ShapingSemanticAssertions.inspect(creation, output, mode);
            assertTrue(mode + "\n" + observed.findings, observed.passed);
            if (mode == WorkflowExecutionProfile.IN_PROCESS) { negativeControls(creation, output); }
        }
    }

    @Test
    public void allEightVisualSelectionsInBothModesRemainIndeterminateWhenToolsAreMissing() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot"));
        Path pins = missingPins(root);
        Path output = temporary.newFolder().toPath();
        T29ShapingEvidenceCommand.main(arguments(root, pins, output, Paths.get(System.getProperty("folio.harfBuzzHelper"))));
        assertTrue(read(output.resolve("T29-shaping-native.md")).contains("Result: `pass`"));
        assertTrue(read(output.resolve("T29-shaping-semantic.md")).contains("Result: `pass`"));
        assertTrue(read(output.resolve("T29-shaping-semantic.md")).contains("IN_PROCESS,HARDENED_WORKER"));
        assertTrue(read(output.resolve("T29-shaping-semantic.md")).contains("Worker applicability: `required on this Linux host`"));
        assertTrue(read(output.resolve("T29-shaping-semantic.md")).contains("HARDENED_WORKER outcome: `pass`"));
        for (String chain : new String[] {"syntax", "subsets", "visual", "installation"}) {
            assertTrue(read(output.resolve("T29-shaping-" + chain + ".md")).contains("Result: `indeterminate`"));
        }
        for (String mode : new String[] {"", "-worker"}) {
            assertTrue(Files.isRegularFile(output.resolve("artifacts/T29-shaping" + mode + ".pdf")));
            int page = 0;
            for (String script : new String[] {"arabic", "hebrew", "devanagari", "thai"}) {
                for (int local = 1; local <= 2; local++) {
                    page++;
                    String record = read(output.resolve("T29-shaping-" + script + "-page-" + local + mode + "-visual.md"));
                    assertTrue(record.contains("Page selection: `" + page + "` of `8`"));
                    assertTrue(record.contains("Result: `indeterminate`"));
                    assertTrue(record.contains("3000"));
                    assertFalse(record.contains("Final determination: `pass`"));
                }
            }
        }
    }

    @Test
    public void missingNativeEngineCannotProducePassingProductEvidence() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot"));
        Path output = temporary.newFolder().toPath();
        T29ShapingEvidenceCommand.main(arguments(root, missingPins(root), output,
                temporary.getRoot().toPath().resolve("missing-native-helper")));
        for (String chain : new String[] {"native", "semantic", "syntax", "subsets", "visual", "installation"}) {
            assertTrue(read(output.resolve("T29-shaping-" + chain + ".md")).contains("Result: `indeterminate`"));
            assertTrue(read(output.resolve("T29-shaping-" + chain + ".md")).contains(
                    "Worker support envelope: `Linux; executable /usr/bin/prlimit; JDK 8,11,17,21 with legacy Security Manager`"));
        }
        assertFalse(Files.exists(output.resolve("artifacts/T29-shaping.pdf")));
        assertFalse(Files.exists(output.resolve("artifacts/T29-shaping-worker.pdf")));
    }

    @Test
    public void unavailableWorkerRetainsInProcessEvidenceAndRecordsTheMissingMode() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot"));
        Path helper = Paths.get(System.getProperty("folio.harfBuzzHelper"));
        // Initialize the platform libraries through the public workflow before
        // pointing the Worker's Java launcher at an unavailable installation.
        T29ShapingProducts.create(temporary.newFile().toPath(), WorkflowExecutionProfile.IN_PROCESS,
                helper, temporary.newFolder().toPath());
        Path pins = missingPins(root);
        Path output = temporary.newFolder().toPath();
        String javaHome = System.getProperty("java.home");
        try {
            System.setProperty("java.home", temporary.newFolder().getAbsolutePath());
            T29ShapingEvidenceCommand.main(arguments(root, pins, output, helper));
        } finally { System.setProperty("java.home", javaHome); }
        assertTrue(read(output.resolve("T29-shaping-native.md")).contains("Result: `pass`"));
        for (String chain : new String[] {"semantic", "syntax", "subsets", "visual"}) {
            String record = read(output.resolve("T29-shaping-" + chain + ".md"));
            assertTrue(record.contains("Result: `indeterminate`"));
            assertTrue(record.contains("HARDENED_WORKER outcome: `indeterminate: WORKER_UNAVAILABLE`"));
            assertTrue(record.contains("Mode coverage: `IN_PROCESS`"));
        }
        assertTrue(Files.isRegularFile(output.resolve("artifacts/T29-shaping.pdf")));
        assertFalse(Files.exists(output.resolve("artifacts/T29-shaping-worker.pdf")));
        assertTrue(read(output.resolve("artifacts/T29-shaping-semantic.txt")).contains("WORKER_UNAVAILABLE"));
    }

    @Test
    public void anExecutableWithoutObserverOutputCannotProduceInstallationEvidence() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot"));
        Path output = temporary.newFolder().toPath();
        String[] input = arguments(root, missingPins(root), output,
                temporary.getRoot().toPath().resolve("missing-native-helper"));
        input[7] = "/usr/bin/true";
        T29ShapingEvidenceCommand.main(input);
        assertTrue(read(output.resolve("T29-shaping-installation.md")).contains("Result: `indeterminate`"));
        assertFalse(read(output.resolve("T29-shaping-installation.md")).contains("Final determination: `pass`"));
    }

    private Path missingPins(Path root) throws Exception {
        Path pins = temporary.newFolder().toPath();
        for (String tool : new String[] {"qpdf", "pdfium", "imagemagick"}) {
            Files.copy(root.resolve("scripts/" + tool + "-pin.properties"), pins.resolve(tool + "-pin.properties"));
        }
        return pins;
    }

    private static String[] arguments(Path root, Path pins, Path output, Path helper) {
        return new String[] {output.toString(), pins.resolve("qpdf-pin.properties").toString(),
            pins.resolve("pdfium-pin.properties").toString(), pins.resolve("imagemagick-pin.properties").toString(),
            root.resolve("capabilities/profiles").toString(), "0.1.0-SNAPSHOT", helper.toString(),
            pins.resolve("missing-python").toString()};
    }

    private static String read(Path path) throws Exception { return new String(Files.readAllBytes(path), StandardCharsets.UTF_8); }

    private void negativeControls(WorkflowOutcome<Void> creation, Path original) throws Exception {
        String[] labels = {"cluster", "direction", "offset", "fallback", "missing glyph"};
        for (int control = 0; control < labels.length; control++) {
            final int mutation = control;
            Path altered = temporary.newFile().toPath();
            new DocumentWorkflow().execute(WorkflowRequest.builder().source("primary", DocumentSource.path(original))
                    .primarySource("primary").target("result", PublicationTarget.path(altered)).saveMode(SaveMode.REWRITE).build(), session -> {
                        ObjectReference page = session.query(PageObjectReference.version1(1));
                        PdfDictionary dictionary = (PdfDictionary) dereference(session, PdfIndirectReference.of(page));
                        String content = contents(session, dictionary.get(PdfName.of("Contents")));
                        if (mutation == 0) {
                            PdfDictionary resources = (PdfDictionary) dereference(session, dictionary.get(PdfName.of("Resources")));
                            PdfDictionary fonts = (PdfDictionary) dereference(session, resources.get(PdfName.of("Font")));
                            Matcher selection = Pattern.compile("/([^\\s]+) 12 Tf").matcher(content);
                            assertTrue(selection.find());
                            PdfIndirectReference fontReference = (PdfIndirectReference) fonts.get(PdfName.of(selection.group(1)));
                            PdfDictionary font = (PdfDictionary) dereference(session, fontReference);
                            PdfStream mapping = (PdfStream) dereference(session, font.get(PdfName.of("ToUnicode")));
                            String cmap = new String(mapping.readBytes(), StandardCharsets.US_ASCII);
                            String changed = cmap.replaceFirst("(beginbfchar\\s+<[0-9A-Fa-f]{4}> )<[0-9A-Fa-f]{4,}>", "$1<0041>");
                            assertFalse(cmap.equals(changed));
                            session.execute(DocumentPatch.builder().setDictionaryEntry(fontReference.getReference(), PdfName.of("ToUnicode"),
                                    PdfStream.of(PdfDictionary.builder().build(), changed.getBytes(StandardCharsets.US_ASCII))).build());
                        } else if (mutation == 3) {
                            PdfDictionary hebrew = (PdfDictionary) dereference(session, PdfIndirectReference.of(
                                    session.query(PageObjectReference.version1(3))));
                            session.execute(DocumentPatch.builder().setDictionaryEntry(page, PdfName.of("Resources"),
                                    hebrew.get(PdfName.of("Resources"))).build());
                        } else {
                            String changed;
                            if (mutation == 1) { changed = reverseGlyphs(content); }
                            else if (mutation == 2) { changed = "1 0 0 1 1 0 cm\n" + content; }
                            else { changed = content.replaceFirst("<[0-9A-Fa-f]{4}> Tj", ""); }
                            assertFalse(content.equals(changed));
                            session.execute(DocumentPatch.builder().setDictionaryEntry(page, PdfName.of("Contents"),
                                    PdfStream.of(PdfDictionary.builder().build(), changed.getBytes(StandardCharsets.US_ASCII))).build());
                        }
                        return null;
                    });
            assertFalse("The fixed oracle must reject wrong " + labels[control],
                    T29ShapingSemanticAssertions.inspect(creation, altered, WorkflowExecutionProfile.IN_PROCESS).passed);
        }
    }

    private static String reverseGlyphs(String content) {
        Pattern pattern = Pattern.compile("<[0-9A-Fa-f]{4}> Tj");
        Matcher matches = pattern.matcher(content);
        List<String> glyphs = new ArrayList<String>();
        while (matches.find()) { glyphs.add(matches.group()); }
        matches.reset();
        StringBuffer result = new StringBuffer();
        int index = glyphs.size();
        while (matches.find()) { matches.appendReplacement(result, glyphs.get(--index)); }
        matches.appendTail(result);
        return result.toString();
    }

    private static PdfValue dereference(DocumentSession session, PdfValue value) throws DocumentFailure {
        if (value instanceof PdfIndirectReference) {
            return session.query(InspectObject.version1(((PdfIndirectReference) value).getReference(), PdfInspectionLimits.of(4096, 1 << 20)));
        }
        return value;
    }

    private static String contents(DocumentSession session, PdfValue value) throws DocumentFailure {
        value = dereference(session, value);
        if (value instanceof PdfStream) { return new String(((PdfStream) value).readBytes(), StandardCharsets.US_ASCII); }
        PdfArray array = (PdfArray) value;
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < array.size(); index++) { result.append(contents(session, array.get(index))).append('\n'); }
        return result.toString();
    }
}
