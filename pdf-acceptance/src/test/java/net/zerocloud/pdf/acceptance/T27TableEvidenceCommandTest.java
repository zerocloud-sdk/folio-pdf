package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

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
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageObjectReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Independent T27 geometry and order, observed after public Workflow publication. */
public final class T27TableEvidenceCommandTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void independentReferenceAndPaginatorMeetTheOracleAndMissingRepeatedOrMovedContentFails() throws Exception {
        Path artifact = temporary.newFile("pagination.pdf").toPath();
        WorkflowOutcome<Void> creation = T27TableProducts.create(artifact);
        T27TableSemanticAssertions.Observation actual = T27TableSemanticAssertions.inspect(creation,artifact);
        assertTrue(actual.findings,actual.passed);
        Path reference = temporary.newFile("reference.pdf").toPath();
        T27TableProducts.createReference(reference);
        T27TableSemanticAssertions.Observation handPositioned = T27TableSemanticAssertions.inspect(creation,reference);
        assertTrue(handPositioned.findings,handPositioned.passed);
        for (int control = 0; control < 3; control++) {
            final int mutation = control;
            final int page = new int[] {12,7,10}[control];
            Path changed = temporary.newFile().toPath();
            new DocumentWorkflow().execute(WorkflowRequest.builder().source("primary",DocumentSource.path(artifact)).primarySource("primary")
                    .target("result",PublicationTarget.path(changed)).saveMode(SaveMode.REWRITE).build(), session -> {
                        ObjectReference referenceToPage = session.query(PageObjectReference.version1(page));
                        PdfDictionary dictionary = (PdfDictionary) session.query(InspectObject.version1(referenceToPage,
                                PdfInspectionLimits.of(2048,1 << 20)));
                        String original = contents(session,dictionary.get(PdfName.of("Contents")));
                        String modified;
                        if (mutation == 0) { modified = original.replaceFirst("(?s)BT.*?ET",""); }
                        else if (mutation == 1) { modified = original.replaceFirst("(?s)(BT.*?ET)","$1\n$1"); }
                        else { modified = "1 0 0 1 1 0 cm\n" + original; }
                        assertFalse(original.equals(modified));
                        session.execute(DocumentPatch.builder().setDictionaryEntry(referenceToPage,PdfName.of("Contents"),
                                PdfStream.of(PdfDictionary.builder().build(),modified.getBytes(StandardCharsets.US_ASCII))).build());
                        return null;
                    });
            assertFalse("Missing scalar, incorrect repetition and one-point movement must invalidate acceptance",
                    T27TableSemanticAssertions.inspect(creation,changed).passed);
        }
    }

    @Test
    public void repeatableRecordsCoverEveryPageAndMissingToolsRemainIndeterminate() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot"));
        Path pins = temporary.newFolder("pins").toPath();
        for (String name : new String[] {"qpdf","pdfium","imagemagick"}) {
            Files.copy(root.resolve("scripts/" + name + "-pin.properties"),pins.resolve(name + "-pin.properties"));
        }
        Path first = temporary.newFolder("first").toPath();
        Path second = temporary.newFolder("second").toPath();
        for (Path output : new Path[] {first,second}) {
            T27TableEvidenceCommand.main(new String[] {output.toString(),pins.resolve("qpdf-pin.properties").toString(),
                pins.resolve("pdfium-pin.properties").toString(),pins.resolve("imagemagick-pin.properties").toString(),
                root.resolve("capabilities/profiles").toString(),"0.1.0-SNAPSHOT"});
            assertTrue(read(output.resolve("T27-table-pagination-semantic.md")).contains("Result: `pass`"));
            assertTrue(read(output.resolve("T27-table-pagination-syntax.md")).contains("Result: `indeterminate`"));
            assertTrue(read(output.resolve("T27-table-pagination-visual.md")).contains("Result: `indeterminate`"));
            for (int page = 1; page <= 19; page++) {
                String name = "T27-table-pagination-page-" + page + "-visual.md";
                String record = read(output.resolve(name));
                assertTrue(record.contains("Page selection: `" + page + "` of `19`"));
                assertTrue(record.contains("Result: `indeterminate`"));
                assertFalse(record.contains("Final determination: `pass`"));
                assertTrue(read(output.resolve("artifacts/T27-table-pagination-page-" + page + "-visual.txt"))
                        .contains("PDFium identity: tool unavailable."));
            }
        }
        for (String suffix : new String[] {"semantic","syntax","visual"}) {
            String name = "T27-table-pagination-" + suffix + ".md";
            assertArrayEquals(name,Files.readAllBytes(first.resolve(name)),Files.readAllBytes(second.resolve(name)));
        }
        for (int page = 1; page <= 19; page++) {
            String name = "T27-table-pagination-page-" + page + "-visual.md";
            assertArrayEquals(name,Files.readAllBytes(first.resolve(name)),Files.readAllBytes(second.resolve(name)));
        }
        assertEquals(EvidenceFiles.idNeutralPdfSha256(first.resolve("artifacts/T27-table-pagination.pdf")),
                EvidenceFiles.idNeutralPdfSha256(second.resolve("artifacts/T27-table-pagination.pdf")));
    }

    private static String contents(DocumentSession session,PdfValue value) throws DocumentFailure {
        if (value instanceof PdfIndirectReference) {
            value = session.query(InspectObject.version1(((PdfIndirectReference) value).getReference(),PdfInspectionLimits.of(2048,1 << 20)));
        }
        if (value instanceof PdfStream) { return new String(((PdfStream) value).readBytes(),StandardCharsets.US_ASCII); }
        PdfArray array = (PdfArray) value;
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < array.size(); i++) { text.append(contents(session,array.get(i))).append('\n'); }
        return text.toString();
    }
    private static String read(Path path) throws Exception { return new String(Files.readAllBytes(path),StandardCharsets.UTF_8); }
}
