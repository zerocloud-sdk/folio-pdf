package net.zerocloud.pdf;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.zerocloud.pdf.query.RenderPage;

/** Closed rendering values inside the authenticated, bounded Worker protocol. */
final class WorkerRenderingCodec {
    private WorkerRenderingCodec() { }
    static void writeQuery(WorkerCodecIO.Output out, RenderPage query)
            throws IOException {
        out.writeInt(query.getVersion());
        out.writeInt(query.getPageNumber());
        RenderOptions options = query.getOptions();
        out.writeDouble(options.getDpi());
        out.writeDouble(options.getScale());
        out.writeInt(options.getPageBox().ordinal());
        out.writeInt(options.getColorMode().ordinal());
        out.writeInt(options.getAlphaMode().ordinal());
        out.writeInt(options.getAnnotationMode().ordinal());
        out.writeInt(options.getBackgroundRgb());
        out.writeBoolean(options.hasCrop());
        if (options.hasCrop()) {
            out.writeDouble(options.getCropX());
            out.writeDouble(options.getCropY());
            out.writeDouble(options.getCropWidth());
            out.writeDouble(options.getCropHeight());
        }
    }
    static RenderPage readQuery(WorkerCodecIO.Input in) throws DocumentFailure {
        WorkerCommandCodec.requireVersion(in.readInt(), RenderPage.VERSION_1);
        int page = in.readInt();
        RenderOptions.Builder options = RenderOptions.builder()
                .dpi(in.readDouble()).scale(in.readDouble())
                .pageBox(value(RenderOptions.PageBox.values(), in.readInt()))
                .colorMode(value(RenderOptions.ColorMode.values(), in.readInt()))
                .alphaMode(value(RenderOptions.AlphaMode.values(), in.readInt()))
                .annotationMode(value(RenderOptions.AnnotationMode.values(), in.readInt()))
                .backgroundRgb(in.readInt());
        if (in.readBoolean()) {
            options.crop(in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble());
        }
        return RenderPage.version1(page, options.build());
    }
    static void writeResult(WorkerCodecIO.Output out, RenderedPage page)
            throws IOException, DocumentFailure {
        try {
            out.writeInt(page.getPageNumber());
            out.writeInt(page.getWidth());
            out.writeInt(page.getHeight());
            int mask = 0;
            for (RenderDiagnostic diagnostic : page.getDiagnostics()) {
                mask |= 1 << diagnostic.ordinal();
            }
            out.writeInt(mask);
            out.writeFile(page.fileForWorkflow());
        } finally {
            page.close();
        }
    }
    static RenderedPage readResult(WorkerCodecIO.Input in, RenderPage query,
            WorkflowResourceContext resources) throws DocumentFailure {
        int page = in.readInt();
        int width = in.readInt();
        int height = in.readInt();
        int mask = in.readInt();
        if (page != query.getPageNumber() || width <= 0 || height <= 0
                || (long) width * height > resources.getPolicy().getMaximumDecodedPixels()
                || mask < 0 || mask >= 1 << RenderDiagnostic.values().length) {
            throw rejected();
        }
        List<RenderDiagnostic> diagnostics = new ArrayList<RenderDiagnostic>();
        for (RenderDiagnostic diagnostic : RenderDiagnostic.values()) {
            if ((mask & 1 << diagnostic.ordinal()) != 0) { diagnostics.add(diagnostic); }
        }
        Path file = resources.createTemporaryFile("render-", ".png");
        try {
            in.readFile(file, resources);
            RenderedPage result = new RenderedPage(page, width, height, diagnostics, file, resources);
            file = null;
            return result;
        } finally {
            resources.releaseTemporaryFile(file);
        }
    }
    private static <T> T value(T[] values, int index) throws DocumentFailure {
        if (index < 0 || index >= values.length) { throw rejected(); }
        return values[index];
    }

    static void writeSnapshot(WorkerCodecIO.Output out, RenderingSnapshot snapshot)
            throws IOException, DocumentFailure {
        try {
            out.writeInt(snapshot.width);
            out.writeInt(snapshot.height);
            out.writeDouble(snapshot.scale);
            out.writeFile(snapshot.file);
        } finally { snapshot.close(); }
    }

    static RenderingSnapshot readSnapshot(WorkerCodecIO.Input in,
            WorkflowResourceContext resources) throws DocumentFailure {
        int width = in.readInt();
        int height = in.readInt();
        float scale = (float) in.readDouble();
        if (width <= 0 || height <= 0 || (long) width * height > Integer.MAX_VALUE
                || !Float.isFinite(scale) || scale <= 0) { throw rejected(); }
        Path file = resources.createTemporaryFile("render-input-", ".pdf");
        try {
            in.readFile(file, resources);
            RenderingSnapshot result = new RenderingSnapshot(width, height, scale, file, resources);
            file = null;
            return result;
        } finally { resources.releaseTemporaryFile(file); }
    }
    private static DocumentFailure rejected() {
        return WorkerCommandCodec.rejected("The Worker rendering value is invalid.");
    }
}
