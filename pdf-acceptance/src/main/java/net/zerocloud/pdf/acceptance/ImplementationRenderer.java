package net.zerocloud.pdf.acceptance;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Version;

/** Acceptance-only implementation renderer used solely as disagreement evidence. */
final class ImplementationRenderer {

    private ImplementationRenderer() {
    }

    static String render(Path pdf, Path raster, VisualProfile profile)
            throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            if (document.getNumberOfPages() != 1) {
                throw new IOException("Implementation renderer observed a non-single-page PDF");
            }
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(
                    0,
                    profile.dpi(),
                    ImageType.RGB);
            if (image.getWidth() != profile.rasterWidth()
                    || image.getHeight() != profile.rasterHeight()) {
                throw new IOException("Implementation renderer produced unexpected dimensions");
            }
            if (!ImageIO.write(image, "png", raster.toFile())) {
                throw new IOException("No PNG writer is available for secondary evidence");
            }
        }
        return Version.getVersion();
    }
}
