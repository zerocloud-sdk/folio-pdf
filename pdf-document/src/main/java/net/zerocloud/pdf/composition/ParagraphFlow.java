package net.zerocloud.pdf.composition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Finite, versioned sequence of paragraphs and area breaks over explicitly declared
 * new pages. Only the prefix of pages reached by content or an area break is appended.
 * Exhausting the declarations fails the command; no implicit page template repeats.
 *
 * @since 0.1.0
 */
public final class ParagraphFlow {
    /** Supported representation version. */ public static final int VERSION_1 = 1;
    /** Advanced paragraph flow representation. */ public static final int VERSION_2 = 2;
    private final int version;
    private final FontSelection fonts;
    private final List<LayoutPage> pages;
    private final List<Item> items;

    private ParagraphFlow(Builder builder) {
        version = builder.version;
        fonts = builder.fonts;
        pages = Collections.unmodifiableList(new ArrayList<LayoutPage>(builder.pages));
        items = Collections.unmodifiableList(new ArrayList<Item>(builder.items));
    }

    /** Begins an explicitly ordered flow using one deterministic fallback selection. */
    public static Builder version1(FontSelection fonts) { return new Builder(VERSION_1, fonts); }
    /** @return representation version */ public int getVersion() { return version; }
    /** Begins a flow admitting version-1 and version-2 paragraphs. */
    public static Builder version2(FontSelection fonts) { return new Builder(VERSION_2, fonts); }
    /** @return ordered font selection */ public FontSelection getFonts() { return fonts; }
    /** @return immutable page declarations */ public List<LayoutPage> getPages() { return pages; }
    /** @return immutable flow sequence */ public List<Item> getItems() { return items; }

    /** One closed flow item. */
    public static final class Item {
        /** Supported flow operations. */ public enum Kind { PARAGRAPH, AREA_BREAK }
        private final Paragraph paragraph;
        private Item(Paragraph paragraph) { this.paragraph = paragraph; }
        /** @return operation kind */
        public Kind getKind() { return paragraph == null ? Kind.AREA_BREAK : Kind.PARAGRAPH; }
        /** @return paragraph, or null for an area break */
        public Paragraph getParagraph() { return paragraph; }
    }

    /** Records an immutable flow without opening font resources or running layout. */
    public static final class Builder {
        private final int version;
        private final FontSelection fonts;
        private final List<LayoutPage> pages = new ArrayList<LayoutPage>();
        private final List<Item> items = new ArrayList<Item>();
        private Builder(int version, FontSelection fonts) {
            this.version = version; this.fonts = Objects.requireNonNull(fonts, "fonts");
        }
        /** Appends a page declaration. @return this builder */
        public Builder page(LayoutPage page) { pages.add(Objects.requireNonNull(page, "page")); return this; }
        /** Appends a paragraph. @return this builder */
        public Builder paragraph(Paragraph paragraph) {
            items.add(new Item(Objects.requireNonNull(paragraph, "paragraph")));
            return this;
        }
        /** Advances to the next declared area, even if the current area is empty. @return this builder */
        public Builder areaBreak() { items.add(new Item(null)); return this; }
        /** @return immutable flow */ public ParagraphFlow build() { return new ParagraphFlow(this); }
    }
}
