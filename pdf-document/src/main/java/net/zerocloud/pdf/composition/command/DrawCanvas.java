package net.zerocloud.pdf.composition.command;

import java.util.Objects;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.composition.CanvasProgram;

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

    private final int pageNumber;
    private final CanvasProgram program;

    private DrawCanvas(int pageNumber, CanvasProgram program) {
        this.pageNumber = pageNumber;
        this.program = Objects.requireNonNull(program, "program");
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
        return new DrawCanvas(pageNumber, program);
    }

    /** @return {@link #VERSION_1} */
    public int getVersion() {
        return VERSION_1;
    }

    /** @return the one-based target page */
    public int getPageNumber() {
        return pageNumber;
    }

    /** @return the immutable Canvas Program */
    public CanvasProgram getProgram() {
        return program;
    }
}
