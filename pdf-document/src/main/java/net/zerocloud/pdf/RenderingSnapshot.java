package net.zerocloud.pdf;

import java.nio.file.Path;

/** One selected page prepared for an explicitly selected external Provider. */
final class RenderingSnapshot implements AutoCloseable {
    final int width;
    final int height;
    final float scale;
    final Path file;
    private final WorkflowResourceContext resources;
    RenderingSnapshot(int width, int height, float scale, Path file,
            WorkflowResourceContext resources) {
        this.width = width; this.height = height; this.scale = scale;
        this.file = file; this.resources = resources;
    }
    @Override public void close() { resources.releaseTemporaryFile(file); }
}
