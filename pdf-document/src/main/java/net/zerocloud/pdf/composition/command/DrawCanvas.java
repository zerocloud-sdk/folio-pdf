package net.zerocloud.pdf.composition.command;

import java.util.Objects;
import java.util.Optional;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.composition.CanvasProgram;
import net.zerocloud.pdf.composition.CanvasResourceLimits;

/**
 * Appends one validated Canvas Program to a one-based page.
 *
 * <p>The command preserves existing page content and resources when the
 * Document Engine can prove that they can be isolated safely. The complete
 * command is rejected before page mutation otherwise.</p>
 *
 * @since 0.1.0
 */
public final class DrawCanvas implements DocumentCommand {

    /** The currently supported command representation version. */
    public static final int VERSION_1 = 1;

    /** The image, color, and transparency command representation. */
    public static final int VERSION_2 = 2;

    private final int version;
    private final int pageNumber;
    private final CanvasProgram program;
    private final CanvasResourceLimits resourceLimits;

    private DrawCanvas(
            int version,
            int pageNumber,
            CanvasProgram program,
            CanvasResourceLimits resourceLimits) {
        this.version = version;
        this.pageNumber = pageNumber;
        this.program = Objects.requireNonNull(program, "program");
        this.resourceLimits = resourceLimits;
    }

    /**
     * Creates a version-1 page-targeted Canvas command.
     *
     * @param pageNumber the one-based page number
     * @param program the immutable Canvas Program
     * @return the command
     */
    public static DrawCanvas version1(
            int pageNumber,
            CanvasProgram program) {
        return new DrawCanvas(VERSION_1, pageNumber, program, null);
    }

    /**
     * Creates a version-2 page-targeted Canvas command with complete resource
     * limits.
     */
    public static DrawCanvas version2(
            int pageNumber,
            CanvasProgram program,
            CanvasResourceLimits resourceLimits) {
        return new DrawCanvas(
                VERSION_2,
                pageNumber,
                program,
                Objects.requireNonNull(resourceLimits, "resourceLimits"));
    }

    /** @return {@link #VERSION_1} or {@link #VERSION_2} */
    public int getVersion() {
        return version;
    }

    /** @return the one-based target page */
    public int getPageNumber() {
        return pageNumber;
    }

    /** @return the immutable Canvas Program */
    public CanvasProgram getProgram() {
        return program;
    }

    /** @return complete resource limits for version 2 */
    public Optional<CanvasResourceLimits> getResourceLimits() {
        return Optional.ofNullable(resourceLimits);
    }
}
