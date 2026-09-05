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
public final class T26TableEvidenceCommandTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void handSpecifiedOracleRejectsMovedContentAndMissingText() throws Exception {
        Path artifact = temporary.newFile("table.pdf").toPath();
        WorkflowOutcome<Void> creation = T26TableProducts.create(artifact);
        assertTrue(T26TableSemanticAssertions.inspect(creation, artifact).passed);
        for (int page = 1; page <= 3; page++) {
            final int selectedPage = page;
            for (int control = 0; control < 5; control++) {
                final int mutation = control;
                Path changed = temporary.newFile().toPath();
                new DocumentWorkflow().execute(WorkflowRequest.builder()
                        .source("primary", net.zerocloud.pdf.DocumentSource.path(artifact)).primarySource("primary")
                        .target("result", PublicationTarget.path(changed)).saveMode(SaveMode.REWRITE).build(), session -> {
                            ObjectReference pageReference = session.query(PageObjectReference.version1(selectedPage));
                            PdfDictionary dictionary = (PdfDictionary) session.query(InspectObject.version1(
                                    pageReference, PdfInspectionLimits.of(1024, 1 << 20)));
                            String original = contents(session, dictionary.get(PdfName.of("Contents")));
                            String program;
                            if (mutation == 0) { program = "1 0 0 1 1 0 cm\n" + original; }
                            else if (mutation == 1) { program = original.replaceFirst("(?s)BT.*?ET", ""); }
                            else if (mutation == 2) { program = original.replaceFirst("0 g", "0 g\n1 0 0 1 1 0 cm"); }
                            else if (mutation == 3) { program = original.replaceFirst("0 g", "1 g"); }
                            else { program = "q Q\n"; }
                            assertFalse("The negative control must change its selected content", original.equals(program));
                            session.execute(DocumentPatch.builder().setDictionaryEntry(pageReference, PdfName.of("Contents"),
                                    PdfStream.of(PdfDictionary.builder().build(), program.getBytes(StandardCharsets.US_ASCII)))
                                    .build());
                            return null;
                        });
                assertFalse("Movement, missing content and incorrect border color must invalidate acceptance",
                        T26TableSemanticAssertions.inspect(creation, changed).passed);
            }
        }
    }

    @Test
    public void repeatedRunsRetainThreePageProfilesAndMarkMissingToolsIndeterminate() throws Exception {
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
            T26TableEvidenceCommand.main(new String[] {output.toString(),
                pins.resolve("qpdf-pin.properties").toString(), pins.resolve("pdfium-pin.properties").toString(),
                pins.resolve("imagemagick-pin.properties").toString(), root.resolve("capabilities/profiles").toString(),
                "0.1.0-SNAPSHOT"});
            assertTrue(read(output.resolve("T26-table-composition-semantic.md")).contains("Result: `pass`"));
            assertTrue(read(output.resolve("T26-table-composition-syntax.md")).contains("Result: `indeterminate`"));
            assertTrue(read(output.resolve("T26-table-composition-visual.md")).contains("Result: `indeterminate`"));
            for (int page = 1; page <= 3; page++) {
                String visual = read(output.resolve("T26-table-composition-page-" + page + "-visual.md"));
                assertTrue(visual.contains("Page selection: `" + page + "` of `3`"));
                assertTrue(visual.contains("Result: `indeterminate`"));
                assertFalse(visual.contains("Final determination: `pass`"));
            }
        }
        for (String name : new String[] {"T26-table-composition-syntax.md", "T26-table-composition-semantic.md",
                "T26-table-composition-visual.md", "T26-table-composition-page-1-visual.md",
                "T26-table-composition-page-2-visual.md", "T26-table-composition-page-3-visual.md", "artifacts/T26-table-composition-semantic.txt"}) {
            assertArrayEquals(name, Files.readAllBytes(first.resolve(name)), Files.readAllBytes(second.resolve(name)));
        }
        assertEquals(EvidenceFiles.idNeutralPdfSha256(first.resolve("artifacts/T26-table-composition.pdf")),
                EvidenceFiles.idNeutralPdfSha256(second.resolve("artifacts/T26-table-composition.pdf")));
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
