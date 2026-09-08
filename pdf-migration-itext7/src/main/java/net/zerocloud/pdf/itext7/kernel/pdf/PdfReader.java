package net.zerocloud.pdf.itext7.kernel.pdf;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Opens a Source through the Native Interface for the mapped inspection
 * workflow.
 *
 * @since 0.1.0
 */
public final class PdfReader implements Closeable {

    static {
        FacadeClasspathGuard.requireSingleEdition();
    }

    private final FacadeSource source;
    private boolean claimed;
    private boolean closed;

    /**
     * Opens and validates a PDF filename.
     *
     * @param filename the PDF filename
     * @throws IOException if the Native Interface cannot open the source
     */
    public PdfReader(String filename) throws IOException {
        source = FacadeSource.capture(Paths.get(Objects.requireNonNull(filename, "filename"))
                .toAbsolutePath()
                .normalize());
    }

    /**
     * Reads and validates a caller-owned stream without closing it.
     * @param input PDF bytes owned by the caller
     * @throws IOException if the Source cannot be opened
     */
    public PdfReader(InputStream input) throws IOException {
        source = FacadeSource.capture(Objects.requireNonNull(input, "input"));
    }

    int getPageCount() {
        return source.pageCount;
    }

    FacadeSource takeSource() {
        if (closed || claimed) {
            throw new IllegalStateException("The facade reader is closed or already owned by a document.");
        }
        claimed = true;
        return source;
    }

    /**
     * Releases an unclaimed snapshot. A Document owns a claimed snapshot until
     * that Document closes. The original Path is closed before construction returns.
     *
     * @throws IOException retained for the mapped reference call shape
     */
    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            if (!claimed) {
                source.close();
            }
        }
    }
}
