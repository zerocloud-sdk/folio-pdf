package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** PDFBox supplies disposable unit rasters; retained T31 evidence uses actual pinned PDFium. */
public final class T31RasterEvidenceTest {
    @Rule public final TemporaryFolder temporary=new TemporaryFolder();

    @Test
    public void rastersDecodeWithoutVectorAnswersAndIndependentGeometryAgrees() throws Exception {
        Path product=temporary.newFile("product.pdf").toPath(),reference=temporary.newFile("reference.pdf").toPath();
        T31BarcodeProducts.create(product,WorkflowExecutionProfile.IN_PROCESS);
        T31BarcodeAssertions.Observation observed=T31BarcodeAssertions.inspect(product);
        assertTrue(observed.findings,observed.passed);
        T31BarcodeReference.create(reference,observed);
        List<T31BarcodeProfile.Fixture> fixtures=T31BarcodeProfile.fixtures();
        try (PDDocument actual=Loader.loadPDF(product.toFile()); PDDocument expected=Loader.loadPDF(reference.toFile())) {
            assertEquals(fixtures.size(),expected.getNumberOfPages());
            PDFRenderer first=new PDFRenderer(actual),second=new PDFRenderer(expected);
            for (int page=0;page<fixtures.size();page++) {
                T31BarcodeProfile.Fixture fixture=fixtures.get(page);
                if (!(fixture.id.startsWith("placement-") || fixture.id.startsWith("color-") || fixture.id.startsWith("units-")
                        || fixture.id.equals("qr-version-40") || fixture.id.equals("dm-sequence-max") || fixture.id.equals("pdf-macro-single"))) { continue; }
                BufferedImage raster=first.renderImageWithDPI(page,288,ImageType.RGB);
                assertTrue(fixture.id,T31RasterDecoder.decode(raster,fixture).contains("payload="));
                BufferedImage oracle=second.renderImageWithDPI(page,288,ImageType.RGB);
                for (int y=0;y<raster.getHeight();y++) {
                    int[] row=raster.getRGB(0,y,raster.getWidth(),1,null,0,raster.getWidth());
                    assertArrayEquals(fixture.id+" raster row "+y,row,oracle.getRGB(0,y,oracle.getWidth(),1,null,0,oracle.getWidth()));
                }
                // A finder/start module painted white must be rejected even if the payload is recoverable.
                paintModule(raster,fixture,0,0,0xffffffff);
                try { T31RasterDecoder.decode(raster,fixture); fail(fixture.id+" damaged function accepted"); }
                catch (Exception rejected) { assertNotNull(rejected); }
            }
        }
        observed.matrices.get(0).flip(0,0);
        try { T31BarcodeReference.create(temporary.newFile().toPath(),observed); fail("Unqualified matrix became a geometry reference"); }
        catch (IllegalArgumentException rejected) { assertTrue(rejected.getMessage().contains("finder")); }
    }

    private static void paintModule(BufferedImage raster,T31BarcodeProfile.Fixture fixture,int x,int y,int rgb) {
        double left=fixture.barcode.getQuietZone()+x*fixture.barcode.getModuleWidth();
        double bottom=fixture.barcode.getQuietZone()+(fixture.height-1-y)*fixture.barcode.getModuleHeight();
        for (double dx=0.125;dx<fixture.barcode.getModuleWidth();dx+=0.25) {
            for (double dy=0.125;dy<fixture.barcode.getModuleHeight();dy+=0.25) {
                Point2D point=T31BarcodeAssertions.transform(fixture).transform(new Point2D.Double(left+dx,bottom+dy),null);
                raster.setRGB((int)(point.getX()*4),raster.getHeight()-1-(int)(point.getY()*4),rgb);
            }
        }
    }
}
