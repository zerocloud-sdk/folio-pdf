package net.zerocloud.pdf.composition.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.composition.LayoutPage;

/**
 * Replaces the current buffered flow's pages using the same semantic content,
 * fonts and limits. Queries do not seal the flow; flush and later mutations do.
 * Failure preserves the preceding successful layout. Published flows are not retained.
 */
public final class RelayoutParagraphs implements DocumentCommand {
    /** Representation version. */ public static final int VERSION_1 = 1;
    private final List<LayoutPage> pages;
    private RelayoutParagraphs(LayoutPage[] pages) {
        List<LayoutPage> copy = new ArrayList<LayoutPage>(Arrays.asList(pages));
        for (LayoutPage page : copy) { Objects.requireNonNull(page, "page"); }
        this.pages = Collections.unmodifiableList(copy);
    }
    /** Declares the complete finite replacement page list. */
    public static RelayoutParagraphs version1(LayoutPage... pages) {
        return new RelayoutParagraphs(Objects.requireNonNull(pages, "pages"));
    }
    /** @return representation version */ public int getVersion() { return VERSION_1; }
    /** @return immutable replacement page declarations */ public List<LayoutPage> getPages() { return pages; }
}
