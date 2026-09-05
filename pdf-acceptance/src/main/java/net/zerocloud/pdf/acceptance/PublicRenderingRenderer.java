package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.RenderOptions;
import net.zerocloud.pdf.RenderedPage;
import net.zerocloud.pdf.Rendering;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.RenderPage;

/** The production Rendering capability is the implementation under acceptance. */
final class PublicRenderingRenderer {
    private PublicRenderingRenderer() { }
    static String render(Path pdf, Path raster, VisualProfile profile) throws IOException {
        try (OutputStream output = Files.newOutputStream(raster)) {
            new DocumentWorkflow().execute(WorkflowRequest.builder()
                    .source("input", DocumentSource.path(pdf)).primarySource("input")
                    .saveMode(SaveMode.REWRITE).build(), session -> {
                        try (RenderedPage page = session.query(RenderPage.version1(1,
                                RenderOptions.builder().dpi(profile.dpi()).scale(1)
                                        .pageBox(RenderOptions.PageBox.CROP)
                                        .colorMode(RenderOptions.ColorMode.RGB)
                                        .alphaMode(RenderOptions.AlphaMode.OPAQUE)
                                        .annotationMode(RenderOptions.AnnotationMode.SHOW)
                                        .backgroundRgb(0xffffff).build()))) {
                            if (page.getWidth() != profile.rasterWidth() || page.getHeight() != profile.rasterHeight()) {
                                throw new AssertionError("Public Rendering dimensions differ from the pinned profile");
                            }
                            if (!page.getDiagnostics().toString().equals(profile.renderDiagnostics())) {
                                throw new AssertionError("Rendering diagnostics differ from the pinned profile: " + page.getDiagnostics());
                            }
                            page.writePngTo(output);
                        }
                        return null;
                    });
        } catch (DocumentFailure | AssertionError failure) {
            throw new IOException("The public Rendering capability failed its pinned profile", failure);
        }
        return Rendering.getDefaultProviderMetadata().getEngineVersion();
    }
}
