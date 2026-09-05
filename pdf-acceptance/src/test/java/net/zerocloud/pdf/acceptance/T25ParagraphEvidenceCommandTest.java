package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentSession;
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
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageObjectReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** The acceptance producer must detect geometry changes and never fabricate missing tool evidence. */
public final class T25ParagraphEvidenceCommandTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void handSpecifiedOracleRejectsMovedContentAndMissingText() throws Exception {
        for (T25ParagraphExpectations.Profile profile : T25ParagraphExpectations.PROFILES) {
            Path artifact = temporary.newFile().toPath();
            WorkflowOutcome<Void> creation = T25ParagraphProducts.create(profile, artifact);
            assertTrue(profile.id, T25ParagraphSemanticAssertions.inspect(profile, creation, artifact).passed);
            Path reference = temporary.newFile().toPath();
            T25ParagraphProducts.createReference(profile, reference);
            assertTrue("Independent reference " + profile.id,
                    T25ParagraphSemanticAssertions.inspect(profile, creation, reference).passed);
            for (boolean removeText : new boolean[] {false, true}) {
                Path changed = temporary.newFile().toPath();
                new DocumentWorkflow().execute(WorkflowRequest.builder()
                        .source("primary", net.zerocloud.pdf.DocumentSource.path(artifact)).primarySource("primary")
                        .target("result", PublicationTarget.path(changed)).saveMode(SaveMode.REWRITE).build(), session -> {
                            int pageNumber = profile.runs.get(0).page;
                            ObjectReference page = session.query(PageObjectReference.version1(pageNumber));
                            PdfDictionary dictionary = (PdfDictionary) session.query(InspectObject.version1(
                                    page, PdfInspectionLimits.of(1024, 1 << 20)));
                            String program = removeText ? "q Q\n" : "1 0 0 1 1 0 cm\n"
                                    + contents(session, dictionary.get(PdfName.of("Contents")));
                            session.execute(DocumentPatch.builder().setDictionaryEntry(page, PdfName.of("Contents"),
                                    PdfStream.of(PdfDictionary.builder().build(), program.getBytes(StandardCharsets.US_ASCII)))
                                    .build());
                            return null;
                        });
                assertFalse("One-point movement or removed content must invalidate " + profile.id,
                        T25ParagraphSemanticAssertions.inspect(profile, creation, changed).passed);
            }
        }
    }

    @Test
    public void repeatedRunsRetainBothPageProfilesAndMarkMissingToolsIndeterminate() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot"));
        Path pins = temporary.newFolder("pins").toPath();
        String[] names = {"qpdf", "pdfium", "imagemagick"};
        for (String name : names) {
            String content = new String(Files.readAllBytes(root.resolve("scripts/" + name + "-pin.properties")),
                    StandardCharsets.UTF_8);
            // The copied pin resolves its wrapper beneath this private directory,
            // where no executable or digest marker is installed.
            Files.write(pins.resolve(name + "-pin.properties"), content.getBytes(StandardCharsets.UTF_8));
        }
        Path first = temporary.newFolder("first").toPath();
        Path second = temporary.newFolder("second").toPath();
        for (Path output : new Path[] {first, second}) {
            T25ParagraphEvidenceCommand.main(new String[] {output.toString(),
                pins.resolve("qpdf-pin.properties").toString(), pins.resolve("pdfium-pin.properties").toString(),
                pins.resolve("imagemagick-pin.properties").toString(), root.resolve("capabilities/profiles").toString(),
                "0.1.0-SNAPSHOT"});
            assertTrue(read(output.resolve("T25-paragraph-pagination-semantic.md")).contains("Result: `pass`"));
            assertTrue(read(output.resolve("T25-paragraph-pagination-syntax.md")).contains("Result: `indeterminate`"));
            assertTrue(read(output.resolve("T25-paragraph-pagination-visual.md")).contains("Result: `indeterminate`"));
            for (T25ParagraphExpectations.Profile profile : T25ParagraphExpectations.PROFILES) {
                for (int page = 1; page <= 2; page++) {
                    String visual = read(output.resolve(profile.id + "-page-" + page + "-visual.md"));
                    assertTrue(visual.contains("Page selection: `" + page + "` of `2`"));
                    assertTrue(visual.contains("Result: `indeterminate`"));
                    assertFalse(visual.contains("Final determination: `pass`"));
                }
            }
        }
        for (String chain : new String[] {"syntax", "semantic", "visual"}) {
            String name = "T25-paragraph-pagination-" + chain + ".md";
            assertArrayEquals(name, Files.readAllBytes(first.resolve(name)), Files.readAllBytes(second.resolve(name)));
        }
        for (T25ParagraphExpectations.Profile profile : T25ParagraphExpectations.PROFILES) {
            assertEquals(profile.id, EvidenceFiles.idNeutralPdfSha256(first.resolve("artifacts/" + profile.id + ".pdf")),
                    EvidenceFiles.idNeutralPdfSha256(second.resolve("artifacts/" + profile.id + ".pdf")));
            String observations = "artifacts/" + profile.id + "-semantic.txt";
            assertArrayEquals(observations, Files.readAllBytes(first.resolve(observations)), Files.readAllBytes(second.resolve(observations)));
        }
    }

    private static String contents(DocumentSession session, PdfValue value) throws DocumentFailure {
        if (value instanceof PdfIndirectReference) {
            value = session.query(InspectObject.version1(((PdfIndirectReference) value).getReference(),
                    PdfInspectionLimits.of(1024, 1 << 20)));
        }
        if (value instanceof PdfStream) { return new String(((PdfStream) value).readBytes(), StandardCharsets.US_ASCII); }
        PdfArray array = (PdfArray) value;
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < array.size(); index++) { text.append(contents(session, array.get(index))).append('\n'); }
        return text.toString();
    }
    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
