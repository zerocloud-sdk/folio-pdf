package net.zerocloud.pdf.itext7.kernel.pdf;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Declares the Path destination for a preview facade document.
 *
 * <p>The output is staged and committed when the owning {@link PdfDocument}
 * closes; construction never truncates an existing target.</p>
 *
 * @since 0.1.0
 */
public final class PdfWriter {

    static {
        FacadeClasspathGuard.requirePreviewOnly();
    }

    private final Path target;

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
        this.target = normalized;
    }

    Path getTarget() {
        return target;
    }
}
