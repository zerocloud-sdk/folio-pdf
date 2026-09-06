package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
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

/** Published Unicode products are checked against the offline, independent fontTools oracle. */
public final class T28UnicodeEvidenceCommandTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void sevenProfilesMatchTheIndependentGlyphGeometryInBothExecutionModes() throws Exception {
        for (WorkflowExecutionProfile mode : WorkflowExecutionProfile.values()) {
            Path artifact = temporary.newFile().toPath();
            WorkflowOutcome<Void> creation = T28UnicodeProducts.create(artifact, mode);
            System.out.println("T28 " + mode + " peak owned memory bytes: "
                    + creation.getResourceUsage().getPeakOwnedMemoryBytes());
            T28UnicodeSemanticAssertions.Observation observation = T28UnicodeSemanticAssertions.inspect(creation, artifact, mode);
            assertTrue(mode + "\n" + observation.findings, observation.passed);
            if (mode == WorkflowExecutionProfile.IN_PROCESS) { negativeControls(creation); }
        }
    }

    @Test
    public void sevenVisualSelectionsAreRecordedAndUnavailableToolsRemainIndeterminate() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot"));
        Path pins = temporary.newFolder("pins").toPath();
        for (String tool : new String[] {"qpdf", "pdfium", "imagemagick"}) {
            Files.copy(root.resolve("scripts/" + tool + "-pin.properties"), pins.resolve(tool + "-pin.properties"));
        }
        Path output = temporary.newFolder("evidence").toPath();
        T28UnicodeEvidenceCommand.main(new String[] {output.toString(), pins.resolve("qpdf-pin.properties").toString(),
            pins.resolve("pdfium-pin.properties").toString(), pins.resolve("imagemagick-pin.properties").toString(),
            root.resolve("capabilities/profiles").toString(), "0.1.0-SNAPSHOT"});
        assertTrue(read(output.resolve("T28-unicode-semantic.md")).contains("Result: `pass`"));
        assertTrue(read(output.resolve("T28-unicode-semantic.md"))
                .contains("Producer owned-memory budget in bytes: `2147483648`"));
        assertTrue(read(output.resolve("T28-unicode-syntax.md")).contains("Result: `indeterminate`"));
        int page = 0;
        for (String profile : new String[] {"latin", "greek", "cyrillic", "cjk-sc", "cjk-tc", "cjk-jp", "cjk-kr"}) {
            page++;
            String record = read(output.resolve("T28-unicode-" + profile + "-visual.md"));
            assertTrue(record.contains("Page selection: `" + page + "` of `7`"));
            assertTrue(record.contains("Result: `indeterminate`"));
            assertFalse(record.contains("Final determination: `pass`"));
            assertTrue(record.contains("12000"));
        }
    }

    private void negativeControls(WorkflowOutcome<Void> creation) throws Exception {
        Path reference = temporary.getRoot().toPath().resolve("reference.pdf");
        T28UnicodeProducts.copyReference(reference);
        T28UnicodeSemanticAssertions.Observation original = T28UnicodeSemanticAssertions.inspect(
                creation, reference, WorkflowExecutionProfile.IN_PROCESS);
        assertTrue(original.findings, original.passed);
        for (int control = 0; control < 4; control++) {
            final int mutation = control;
            Path altered = temporary.newFile().toPath();
            new DocumentWorkflow().execute(WorkflowRequest.builder().source("primary", DocumentSource.path(reference))
                    .primarySource("primary").target("result", PublicationTarget.path(altered)).saveMode(SaveMode.REWRITE).build(), session -> {
                        ObjectReference page = session.query(PageObjectReference.version1(mutation == 2 ? 4 : 1));
                        PdfDictionary dictionary = (PdfDictionary) session.query(InspectObject.version1(page, PdfInspectionLimits.of(4096, 1 << 20)));
                        if (mutation == 2) {
                            PdfDictionary tc = (PdfDictionary) session.query(InspectObject.version1(
                                    session.query(PageObjectReference.version1(5)), PdfInspectionLimits.of(4096, 1 << 20)));
                            session.execute(DocumentPatch.builder().setDictionaryEntry(page, PdfName.of("Resources"),
                                    tc.get(PdfName.of("Resources"))).build());
                        } else {
                            String content = contents(session, dictionary.get(PdfName.of("Contents")));
                            String changed;
                            if (mutation == 0) { changed = content.replace("82.788 707.196 Tm", "72.000 659.196 Tm"); }
                            else if (mutation == 1) {
                                // Source Hebrew cmaps fix Beth=12 and Aleph=3; reverse their visual order.
                                changed = content.replace("99.636 419.196 Tm <000C>", "99.636 419.196 Tm <0003>")
                                        .replace("106.500 419.196 Tm <0003>", "106.500 419.196 Tm <000C>");
                            } else { changed = "1 0 0 1 1 0 cm\n" + content; }
                            assertFalse(content.equals(changed));
                            session.execute(DocumentPatch.builder().setDictionaryEntry(page, PdfName.of("Contents"),
                                    PdfStream.of(PdfDictionary.builder().build(), changed.getBytes(StandardCharsets.US_ASCII))).build());
                        }
                        return null;
                    });
            assertFalse("Wrong cluster, direction, regional program or one-point geometry must fail: " + mutation,
                    T28UnicodeSemanticAssertions.inspect(creation, altered, WorkflowExecutionProfile.IN_PROCESS).passed);
        }
    }

    private static String contents(DocumentSession session, PdfValue value) throws DocumentFailure {
        if (value instanceof PdfIndirectReference) {
            value = session.query(InspectObject.version1(((PdfIndirectReference) value).getReference(), PdfInspectionLimits.of(4096, 1 << 20)));
        }
        if (value instanceof PdfStream) { return new String(((PdfStream) value).readBytes(), StandardCharsets.US_ASCII); }
        PdfArray array = (PdfArray) value;
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < array.size(); index++) { result.append(contents(session, array.get(index))).append('\n'); }
        return result.toString();
    }

    private static String read(Path path) throws Exception { return new String(Files.readAllBytes(path), StandardCharsets.UTF_8); }
}
