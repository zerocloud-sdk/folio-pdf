package net.zerocloud.pdf.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.OutlineItem;

/**
 * Replaces the document's whole outline tree atomically.
 *
 * <p>An empty item list removes the document outline. Explicit page
 * destinations must target pages of the current document, and
 * named-destination references must resolve in the current Dests name tree
 * at execution time; otherwise the command is rejected without changing the
 * document.</p>
 *
 * @since 0.1.0
 */
public final class ReplaceOutlineTree implements DocumentCommand {

    /** The currently supported command representation version. */
    public static final int VERSION_1 = 1;

    private final List<OutlineItem> items;

    private ReplaceOutlineTree(List<OutlineItem> items) {
        List<OutlineItem> copied = new ArrayList<OutlineItem>(items.size());
        for (OutlineItem item : items) {
            copied.add(Objects.requireNonNull(item, "items"));
        }
        this.items = Collections.unmodifiableList(copied);
    }

    /**
     * Creates a version-1 outline replacement.
     *
     * @param items the new top-level outline items
     * @return the immutable command
     */
    public static ReplaceOutlineTree version1(List<OutlineItem> items) {
        return new ReplaceOutlineTree(
                Objects.requireNonNull(items, "items"));
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
     * Returns the new top-level outline items.
     *
     * @return the immutable item list
     */
    public List<OutlineItem> getItems() {
        return items;
    }
}
