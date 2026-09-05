package net.zerocloud.pdf;

import java.awt.RenderingHints;
import java.awt.Paint;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.zerocloud.pdf.query.RenderPage;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDVectorFont;
import org.apache.pdfbox.pdmodel.font.PDType3Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDPattern;
import org.apache.pdfbox.pdmodel.graphics.pattern.PDAbstractPattern;
import org.apache.pdfbox.pdmodel.graphics.pattern.PDShadingPattern;
import org.apache.pdfbox.pdmodel.graphics.form.PDTransparencyGroup;
import org.apache.pdfbox.pdmodel.graphics.state.PDSoftMask;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.PageDrawer;
import org.apache.pdfbox.rendering.PageDrawerParameters;
import org.apache.pdfbox.rendering.RenderDestination;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;

/** Default renderer, hidden with the Document Engine's other backend work. */
final class PdfBoxRenderingOperations {
    private PdfBoxRenderingOperations() { }

    static RenderingSnapshot snapshot(PDDocument document, RenderPage query,
            WorkflowResourceContext resources) throws DocumentFailure {
        Path file = null;
        try {
            if (query.getPageNumber() < 1 || query.getPageNumber() > document.getNumberOfPages()) {
                throw RenderedPage.failure(DocumentFailureCode.PAGE_RANGE_INVALID,
                        "The rendering page is outside the current document.");
            }
            PDPage page = document.getPage(query.getPageNumber() - 1);
            RenderGeometry geometry = RenderGeometry.of(page, query.getOptions());
            resources.consumeDecodedPixels(geometry.pixels());
            file = resources.createTemporaryFile("render-input-", ".pdf");
            try (PDDocument snapshot = new PDDocument(resources.streamCacheFactory())) {
                PDPage copied = snapshot.importPage(page);
                copied.setResources(page.getResources());
                copied.setMediaBox(geometry.crop);
                copied.setCropBox(geometry.crop);
                copied.setRotation(page.getRotation());
                copied.setUserUnit(1);
                try (OutputStream output = resources.openTemporaryOutput(file)) {
                    snapshot.save(output);
                }
            }
            resources.checkpoint();
            RenderingSnapshot result = new RenderingSnapshot(geometry.width, geometry.height,
                    geometry.scale, file, resources);
            file = null;
            return result;
        } catch (IOException | RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw RenderedPage.failure(DocumentFailureCode.RENDER_FAILED,
                    "The page could not be rendered under the declared profile.");
        } finally {
            resources.releaseTemporaryFile(file);
        }
    }

    static RenderedPage render(PDDocument document, RenderPage query,
            WorkflowResourceContext resources) throws DocumentFailure {
        Path file = null;
        try {
            int number = query.getPageNumber();
            if (number < 1 || number > document.getNumberOfPages()) {
                throw RenderedPage.failure(DocumentFailureCode.PAGE_RANGE_INVALID,
                        "The rendering page is outside the current document.");
            }
            PDPage page = document.getPage(number - 1);
            RenderOptions options = query.getOptions();
            RenderGeometry geometry = RenderGeometry.of(page, options);
            resources.consumeDecodedPixels(geometry.pixels());
            EnumSet<RenderDiagnostic> diagnostics = EnumSet.noneOf(RenderDiagnostic.class);
            file = resources.createTemporaryFile("render-", ".png");
            COSBase oldCrop = page.getCOSObject().getItem(COSName.CROP_BOX);
            COSBase oldMedia = page.getCOSObject().getItem(COSName.MEDIA_BOX);
            try (WorkflowResourceContext.MemoryReservation raster =
                    resources.reserveOwnedMemory(4L * geometry.pixels());
                    RenderingLogScope logs = RenderingLogScope.open(diagnostics)) {
                BufferedImage image = null;
                try {
                    // An effective MediaBox must contain the selected rectangle.
                    page.setMediaBox(geometry.crop);
                    page.setCropBox(geometry.crop);
                    PDFRenderer renderer = new CheckedRenderer(document, resources, diagnostics);
                    renderer.setSubsamplingAllowed(false);
                    renderer.setAnnotationsFilter(annotation ->
                            options.getAnnotationMode() == RenderOptions.AnnotationMode.SHOW);
                    RenderingHints hints = new RenderingHints(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    hints.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    renderer.setRenderingHints(hints);
                    image = renderer.renderImage(number - 1, geometry.scale,
                            ImageType.ARGB, RenderDestination.VIEW);
                    resources.checkpoint();
                    logs.requireClean();
                    if (image.getWidth() != geometry.width || image.getHeight() != geometry.height) {
                        throw new IOException("Raster geometry mismatch");
                    }
                    try (OutputStream output = resources.openTemporaryOutput(file)) {
                        RenderingPngWriter.write(image, options, output, resources);
                    }
                } finally {
                    if (image != null) { image.flush(); }
                    page.getCOSObject().setItem(COSName.CROP_BOX, oldCrop);
                    page.getCOSObject().setItem(COSName.MEDIA_BOX, oldMedia);
                }
            }
            RenderedPage result = new RenderedPage(number, geometry.width, geometry.height,
                    new ArrayList<RenderDiagnostic>(diagnostics), file, resources);
            file = null;
            return result;
        } catch (IOException | RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw RenderedPage.failure(DocumentFailureCode.RENDER_FAILED,
                    "The page could not be rendered under the declared profile.");
        } finally {
            resources.releaseTemporaryFile(file);
        }
    }

    private static final class CheckedRenderer extends PDFRenderer {
        private final WorkflowResourceContext resources;
        private final EnumSet<RenderDiagnostic> diagnostics;
        CheckedRenderer(PDDocument document, WorkflowResourceContext resources,
                EnumSet<RenderDiagnostic> diagnostics) {
            super(document); this.resources = resources; this.diagnostics = diagnostics;
        }
        @Override protected PageDrawer createPageDrawer(PageDrawerParameters parameters)
                throws IOException {
            PageDrawer drawer = new CheckedDrawer(parameters, resources, diagnostics);
            drawer.setAnnotationFilter(getAnnotationsFilter());
            return drawer;
        }
    }

    private static final class CheckedDrawer extends PageDrawer {
        private final WorkflowResourceContext resources;
        private final EnumSet<RenderDiagnostic> diagnostics;
        CheckedDrawer(PageDrawerParameters parameters, WorkflowResourceContext resources,
                EnumSet<RenderDiagnostic> diagnostics) throws IOException {
            super(parameters); this.resources = resources; this.diagnostics = diagnostics;
        }
        @Override protected void processOperator(Operator operator, List<COSBase> operands)
                throws IOException {
            resources.checkpointAsIOException();
            if ("BI".equals(operator.getName())) {
                PdfBoxRenderingImages.drawInline(operator, getResources(), this, resources, diagnostics);
                return;
            }
            super.processOperator(operator, operands);
        }
        @Override protected void operatorException(Operator operator,
                List<COSBase> operands, IOException failure) throws IOException {
            // The backend normally suppresses some decode/appearance errors.
            throw failure;
        }
        @Override protected void showFontGlyph(Matrix matrix, PDFont font,
                int code, Vector displacement) throws IOException {
            if (!font.isEmbedded() || font.isDamaged()) { diagnostics.add(RenderDiagnostic.FONT_SUBSTITUTED); }
            if (font instanceof PDVectorFont) {
                PDVectorFont vector = (PDVectorFont) font;
                if (!vector.hasGlyph(code) || vector.getNormalizedPath(code) == null) {
                    diagnostics.add(RenderDiagnostic.GLYPH_SUBSTITUTED);
                }
            }
            super.showFontGlyph(matrix, font, code, displacement);
        }
        @Override public void drawImage(PDImage image) throws IOException {
            resources.checkpointAsIOException();
            PdfBoxRenderingImages.inspect(image.getCOSObject(), resources, diagnostics);
            if (!image.getCOSObject().containsKey(COSName.MASK)
                    && !image.getCOSObject().containsKey(COSName.SMASK)) {
                requireUsableSoftMask();
            }
            super.drawImage(image);
        }
        @Override public void showTransparencyGroup(PDTransparencyGroup group)
                throws IOException {
            resources.checkpointAsIOException();
            requireUsableTransparencyGroup(group);
            requireUsableSoftMask();
            super.showTransparencyGroup(group);
        }
        @Override protected void showType3Glyph(Matrix matrix, PDType3Font font,
                int code, Vector displacement) throws IOException {
            if (font.getCharProc(code) == null) { diagnostics.add(RenderDiagnostic.GLYPH_SUBSTITUTED); }
            super.showType3Glyph(matrix, font, code, displacement);
        }
        @Override protected Paint getPaint(PDColor color) throws IOException {
            requireUsableSoftMask();
            if (color.getColorSpace() == null) { throw new IOException("Missing color space."); }
            if (color.getColorSpace() instanceof PDPattern) {
                PDAbstractPattern pattern = ((PDPattern) color.getColorSpace()).getPattern(color);
                if (pattern instanceof PDShadingPattern && ((PDShadingPattern) pattern).getShading() == null) {
                    throw new IOException("Missing pattern shading.");
                }
            }
            return super.getPaint(color);
        }
        @Override public void shadingFill(COSName name) throws IOException {
            requireUsableSoftMask();
            if (getResources().getShading(name) == null) { throw new IOException("Missing shading resource."); }
            super.shadingFill(name);
        }
        @Override public void showAnnotation(PDAnnotation annotation) throws IOException {
            resources.checkpointAsIOException();
            if (annotation.isHidden() || annotation.isNoView() || annotation.isInvisible()) { return; }
            if (annotation.getNormalAppearanceStream() == null) {
                diagnostics.add(RenderDiagnostic.ANNOTATION_APPEARANCE_MISSING);
                return;
            }
            // Isolate renderer bookkeeping and prohibit backend appearance synthesis.
            super.showAnnotation(new ExistingAppearance(annotation));
        }
        private void requireUsableSoftMask() throws IOException {
            PDSoftMask softMask = getGraphicsState().getSoftMask();
            if (softMask == null) {
                return;
            }
            COSName subtype = softMask.getSubType();
            if (!COSName.ALPHA.equals(subtype)
                    && !COSName.LUMINOSITY.equals(subtype)) {
                throw new IOException("Soft mask subtype is invalid.");
            }
            requireUsableTransparencyGroup(softMask.getGroup());
        }
        private static void requireUsableTransparencyGroup(
                PDTransparencyGroup group) throws IOException {
            if (group == null || group.getBBox() == null) {
                throw new IOException(
                        "Transparency group is missing its bounding box.");
            }
        }
    }

    private static final class ExistingAppearance extends PDAnnotation {
        ExistingAppearance(PDAnnotation source) { super(new COSDictionary(source.getCOSObject())); }
        @Override public void constructAppearances(PDDocument document) {
            throw new IllegalStateException("Rendering requires the existing annotation appearance.");
        }
        @Override public void constructAppearances() {
            throw new IllegalStateException("Rendering requires the existing annotation appearance.");
        }
    }
}
