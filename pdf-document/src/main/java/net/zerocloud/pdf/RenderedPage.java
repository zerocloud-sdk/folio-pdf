package net.zerocloud.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A completed PNG backed by transaction-owned temporary storage. Metadata is
 * detached. Byte consumption is thread-confined and expires at close or at
 * callback completion. Caller streams are never closed or flushed.
 *
 * @since 0.1.0
 */
public final class RenderedPage implements AutoCloseable {
    private final int pageNumber;
    private final int width;
    private final int height;
    private final List<RenderDiagnostic> diagnostics;
    private final Path file;
    private final WorkflowResourceContext resources;
    private final Thread owner = Thread.currentThread();
    private boolean closed;
    private int activeConsumers;
    private boolean released;

    RenderedPage(int pageNumber, int width, int height,
            List<RenderDiagnostic> diagnostics, Path file,
            WorkflowResourceContext resources) throws DocumentFailure {
        resources.retainRenderedPage(this);
        this.pageNumber = pageNumber;
        this.width = width;
        this.height = height;
        this.diagnostics = Collections.unmodifiableList(
                new ArrayList<RenderDiagnostic>(diagnostics));
        this.file = file;
        this.resources = resources;
        resources.recordRenderingDiagnostics(diagnostics);
    }

    public int getPageNumber() { return pageNumber; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public List<RenderDiagnostic> getDiagnostics() { return diagnostics; }

    /**
     * Copies PNG bytes with bounded working memory and cooperative stop checks.
     * A failed caller stream may contain partial PNG bytes. This is immediate
     * consumption, not a Workflow publication target or a Publication Receipt.
     */
    public void writePngTo(OutputStream output) throws DocumentFailure {
        Objects.requireNonNull(output, "output");
        requireActive();
        activeConsumers++;
        try (WorkflowResourceContext.MemoryReservation memory =
                resources.reserveOwnedMemory(8192);
                InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                resources.checkpoint();
                output.write(buffer, 0, count);
            }
            resources.checkpoint();
        } catch (IOException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw failure(DocumentFailureCode.RENDER_OUTPUT_FAILED,
                    "PNG consumption failed; the caller stream may contain partial output.");
        } finally {
            activeConsumers--;
            releaseIfClosed();
        }
    }

    void requireActive() throws DocumentFailure {
        if (closed || !resources.isOpen()) {
            throw failure(DocumentFailureCode.RENDER_RESULT_EXPIRED,
                    "The rendered page is no longer active.");
        }
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Rendered pages are thread-confined.");
        }
        resources.checkpoint();
    }

    Path fileForWorkflow() throws DocumentFailure { requireActive(); return file; }

    /** Releases owned PNG staging early. Safe to call again after expiry. */
    @Override
    public void close() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Rendered pages are thread-confined.");
        }
        if (!closed) {
            closed = true;
            releaseIfClosed();
        }
    }

    private void releaseIfClosed() {
        if (closed && activeConsumers == 0 && !released) {
            released = true;
            resources.releaseTemporaryFile(file);
            resources.releaseRenderedPage(this);
        }
    }

    static DocumentFailure failure(DocumentFailureCode code, String message) {
        return new DocumentFailure(code, Rendering.CAPABILITY_ID, message);
    }
}
