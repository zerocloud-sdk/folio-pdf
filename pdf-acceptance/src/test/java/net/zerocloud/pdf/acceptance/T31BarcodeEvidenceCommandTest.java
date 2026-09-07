package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.PageObjectReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class T31BarcodeEvidenceCommandTest {
    @Rule public final TemporaryFolder temporary=new TemporaryFolder();

    @Test
    public void dedicatedRecorderRetainsEveryProfileAndMissingToolsWithoutReplacingEvidence() throws Exception {
        Path root=Paths.get(System.getProperty("repositoryRoot"));
        Path pins=temporary.newFolder("pins").toPath(),output=temporary.newFolder("output").toPath();
        for (String name : new String[] {"qpdf","pdfium","imagemagick"}) {
            Files.copy(root.resolve("scripts/"+name+"-pin.properties"),pins.resolve(name+".properties"));
        }
        Path prior=output.resolve("T30-retained.txt"); Files.write(prior,new byte[] {30});
        String[] arguments={output.toString(),pins.resolve("qpdf.properties").toString(),pins.resolve("pdfium.properties").toString(),
                pins.resolve("imagemagick.properties").toString(),root.resolve("capabilities/profiles").toString(),"0.1.0-SNAPSHOT"};
        T31BarcodeEvidenceCommand.main(arguments);
        String stem="T31-two-dimensional-barcodes";
        String semantic=read(output.resolve(stem+"-semantic.md"));
        assertTrue(semantic,semantic.contains("Result: `pass`"));
        assertTrue(semantic,semantic.contains("Decoded pages: `448`"));
        assertTrue(semantic,semantic.contains("Rejected PDF negative controls: `19`"));
        for (String chain : new String[] {"syntax","visual","standards"}) {
            assertTrue(read(output.resolve(stem+"-"+chain+".md")).contains("Result: `indeterminate`"));
        }
        for (String profile : new String[] {"in-process","hardened-worker"}) {
            assertTrue(Files.isRegularFile(output.resolve("artifacts/"+stem+"-"+profile+".pdf")));
            assertTrue(Files.isRegularFile(output.resolve("artifacts/"+stem+"-"+profile+"-reference.pdf")));
            assertTrue(read(output.resolve("artifacts/"+stem+"-"+profile+"-semantic.txt")).contains("qr-version-40: payload=31"));
        }
        Path equivalent=temporary.newFile("same-barcodes-different-document-metadata.pdf").toPath();
        addUnrelatedMetadata(output.resolve("artifacts/"+stem+"-hardened-worker.pdf"),equivalent);
        for (Path pdf : new Path[] {output.resolve("artifacts/"+stem+"-in-process.pdf"),
                output.resolve("artifacts/"+stem+"-hardened-worker.pdf"),equivalent}) {
            T31BarcodeAssertions.Observation observed=T31BarcodeAssertions.inspect(pdf);
            assertTrue(observed.findings,observed.passed);
            assertEquals(224,observed.matrices.size());
        }
        byte[] retained=Files.readAllBytes(output.resolve(stem+"-semantic.md"));
        try { T31BarcodeEvidenceCommand.main(arguments); fail("Existing T31 evidence was replaced"); }
        catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("preserved")); }
        assertArrayEquals(retained,Files.readAllBytes(output.resolve(stem+"-semantic.md")));
        assertArrayEquals(new byte[] {30},Files.readAllBytes(prior));
        assertFalse(Files.exists(output.resolve("T29-shaping-semantic.md")));
    }
    private static void addUnrelatedMetadata(Path source,Path target) throws Exception {
        new DocumentWorkflow().execute(WorkflowRequest.builder()
                .source("source",DocumentSource.path(source)).primarySource("source")
                .target("variant",PublicationTarget.path(target)).saveMode(SaveMode.REWRITE).build(),session -> {
                    session.execute(DocumentPatch.builder().setDictionaryEntry(
                            session.query(PageObjectReference.version1(1)),
                            PdfName.of("T31ObservationVariant"),PdfNumber.of(31)).build());
                    return null;
                });
    }
    private static String read(Path path) throws Exception { return new String(Files.readAllBytes(path),StandardCharsets.UTF_8); }
}
