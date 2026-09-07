package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.*;
import java.nio.file.Path;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class T31NegativeEvidenceTest {
    @Rule public final TemporaryFolder temporary=new TemporaryFolder();

    @Test
    public void publishedCorruptionsFailSemanticAndActualRasterObservations() throws Exception {
        Path pdf=temporary.newFile("original.pdf").toPath();
        T31BarcodeProducts.create(pdf,WorkflowExecutionProfile.IN_PROCESS);
        java.util.List<T31NegativeControls.Control> controls=T31NegativeControls.create(pdf,temporary.newFolder("controls").toPath());
        assertEquals(19,controls.size());
        for (T31NegativeControls.Control control : controls) {
            T31BarcodeAssertions.Observation observed=T31BarcodeAssertions.inspect(control.pdf);
            assertFalse(control.id,observed.passed);
            assertTrue(observed.findings,observed.findings.contains(control.finding));
            try (PDDocument changed=Loader.loadPDF(control.pdf.toFile())) {
                try {
                    T31RasterDecoder.decode(new PDFRenderer(changed).renderImageWithDPI(control.page-1,288,ImageType.RGB),control.fixture);
                    fail(control.id+" corrupted PDF raster accepted");
                } catch (Exception expected) { assertNotNull(expected); }
            }
        }
    }
}
