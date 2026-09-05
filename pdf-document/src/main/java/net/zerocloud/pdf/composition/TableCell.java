package net.zerocloud.pdf.composition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** One semantic grid cell, with ordered version-1 paragraphs and rectangular spans. */
public final class TableCell {
    /** Supported declaration version. */ public static final int VERSION_1 = 1;
    private final List<Paragraph> paragraphs;
    private final int rowspan;
    private final int colspan;
    private final CellPadding padding;
    private final TableBorders borders;
    private final TableWidth minimumWidth;
    private TableCell(Builder builder) {
        paragraphs = Collections.unmodifiableList(new ArrayList<Paragraph>(builder.paragraphs));
        rowspan = builder.rowspan; colspan = builder.colspan;
        padding = builder.padding; borders = builder.borders; minimumWidth = builder.minimumWidth;
    }
    /** Begins a cell with spans of one, no content, zero padding, borders and minimum width. */
    public static Builder version1() { return new Builder(); }
    /** @return representation version */ public int getVersion() { return VERSION_1; }
    /** @return immutable content in reading order */ public List<Paragraph> getParagraphs() { return paragraphs; }
    /** @return number of covered rows */ public int getRowspan() { return rowspan; }
    /** @return number of covered columns */ public int getColspan() { return colspan; }
    /** @return inside padding */ public CellPadding getPadding() { return padding; }
    /** @return inside black borders */ public TableBorders getBorders() { return borders; }
    /** @return declared minimum border-box width */ public TableWidth getMinimumWidth() { return minimumWidth; }
    /** Records cell data without performing layout or opening resources. */
    public static final class Builder {
        private final List<Paragraph> paragraphs = new ArrayList<Paragraph>();
        private int rowspan = 1;
        private int colspan = 1;
        private CellPadding padding = CellPadding.of(0, 0, 0, 0);
        private TableBorders borders = TableBorders.of(0, 0, 0, 0);
        private TableWidth minimumWidth = TableWidth.points(0);
        private Builder() { }
        /** Appends a version-1 paragraph. @return this builder */
        public Builder paragraph(Paragraph value) { paragraphs.add(Objects.requireNonNull(value, "paragraph")); return this; }
        /** Sets a positive row span, validated at execution. @return this builder */
        public Builder rowspan(int value) { rowspan = value; return this; }
        /** Sets a positive column span, validated at execution. @return this builder */
        public Builder colspan(int value) { colspan = value; return this; }
        /** Sets padding. @return this builder */
        public Builder padding(CellPadding value) { padding = Objects.requireNonNull(value, "padding"); return this; }
        /** Sets borders. @return this builder */
        public Builder borders(TableBorders value) { borders = Objects.requireNonNull(value, "borders"); return this; }
        /** Sets a nonnegative point or percentage minimum including insets. @return this builder */
        public Builder minimumWidth(TableWidth value) { minimumWidth = Objects.requireNonNull(value, "minimumWidth"); return this; }
        /** @return immutable cell */ public TableCell build() { return new TableCell(this); }
    }
}
