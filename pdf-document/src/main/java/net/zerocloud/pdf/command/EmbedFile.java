package net.zerocloud.pdf.command;

import java.util.Objects;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.EmbeddedFile;

/**
 * Embeds one file into the document's EmbeddedFiles name tree.
 *
 * <p>Embedding a name that already exists replaces the existing file
 * atomically. Other embedded files and other Names subtrees are preserved
 * unchanged.</p>
 *
 * @since 0.1.0
 */
public final class EmbedFile implements DocumentCommand {

    /** The currently supported command representation version. */
    public static final int VERSION_1 = 1;

    private final EmbeddedFile file;

    private EmbedFile(EmbeddedFile file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /**
     * Creates a version-1 embed command.
     *
     * @param file the file to embed
     * @return the immutable command
     */
    public static EmbedFile version1(EmbeddedFile file) {
        return new EmbedFile(file);
    }

    /**
     * Returns the command representation version.
     *
     * @return {@link #VERSION_1}
     */
    public int getVersion() {
        return VERSION_1;
    }

    /**
     * Returns the file to embed.
     *
     * @return the embedded-file specification
     */
    public EmbeddedFile getFile() {
        return file;
    }
}
