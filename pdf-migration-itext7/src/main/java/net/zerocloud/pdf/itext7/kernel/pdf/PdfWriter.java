package net.zerocloud.pdf.itext7.kernel.pdf;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import net.zerocloud.pdf.PublicationTarget;

/**
 * Declares a Path or caller-owned stream destination for a facade document.
 *
 * <p>The output is staged and committed when the owning {@link PdfDocument}
 * closes; construction never truncates an existing target.</p>
 *
 * @since 0.1.0
 */
public final class PdfWriter {

    static {
        FacadeClasspathGuard.requireSingleEdition();
    }

    private final Path path;
    private final PublicationTarget target;

    /**
     * Creates a writer for a filesystem target.
     *
     * @param filename the resulting PDF filename
     * @throws FileNotFoundException if the target cannot name a file in an
     *         existing directory
     */
    public PdfWriter(String filename) throws FileNotFoundException {
        Path requested = Paths.get(Objects.requireNonNull(filename, "filename"));
        Path normalized = requested.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null || !Files.isDirectory(parent) || Files.isDirectory(normalized)) {
            throw new FileNotFoundException(filename);
        }
        this.path = normalized;
        this.target = PublicationTarget.path(normalized);
    }

    /**
     * Declares a caller-owned stream, flushed but never closed on publication.
     * @param output the destination stream
     */
    public PdfWriter(OutputStream output) {
        path = null;
        target = PublicationTarget.stream(Objects.requireNonNull(output, "output"));
    }

    PublicationTarget getTarget() {
        return target;
    }

    Path getPath() {
        return path;
    }
}
