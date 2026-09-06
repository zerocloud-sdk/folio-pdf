package net.zerocloud.pdf.composition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A versioned bounded table with deterministic columns and a complete span grid. */
public final class Table {
    /** Supported declaration version. */ public static final int VERSION_1 = 1;
    /** Table pagination representation. */ public static final int VERSION_2 = 2;
    /** Column resolution policy. */ public enum Layout { FIXED, AUTO }
    private final Layout layout;
    private final int version;
    private final TableWidth width;
    private final List<TableWidth> columns;
    private final List<TableRow> rows;
    private final boolean keepTogether;
    private final boolean keepWithNext;
    private final List<TableRow> headerRows;
    private final List<TableRow> footerRows;
    private final boolean skipFirstHeader;
    private final boolean skipLastFooter;
    private final Paragraph.Overflow overflow;
    private final boolean splitRows;
    private Table(Builder builder) {
        version = builder.version; layout = builder.layout; width = builder.width;
        columns = Collections.unmodifiableList(new ArrayList<TableWidth>(builder.columns));
        rows = Collections.unmodifiableList(new ArrayList<TableRow>(builder.rows));
        keepTogether = builder.keepTogether; keepWithNext = builder.keepWithNext;
        headerRows = Collections.unmodifiableList(new ArrayList<TableRow>(builder.headerRows));
        footerRows = Collections.unmodifiableList(new ArrayList<TableRow>(builder.footerRows));
        skipFirstHeader = builder.skipFirstHeader; skipLastFooter = builder.skipLastFooter;
        overflow = builder.overflow;
        splitRows = builder.splitRows;
    }
    /** Begins an explicit table; AUTO is allowed only in its column declarations. */
    public static Builder version1(Layout layout, TableWidth width, TableWidth... columns) {
        return new Builder(VERSION_1, layout, width, columns);
    }
    /** Begins a table that may continue through the version-4 flow's finite areas. */
    public static Builder version2(Layout layout, TableWidth width, TableWidth... columns) {
        return new Builder(VERSION_2, layout, width, columns);
    }
    /** @return representation version */ public int getVersion() { return version; }
    /** @return column resolution policy */ public Layout getLayout() { return layout; }
    /** @return table width relative to its area */ public TableWidth getWidth() { return width; }
    /** @return immutable column widths relative to this table */ public List<TableWidth> getColumns() { return columns; }
    /** @return immutable row declarations */ public List<TableRow> getRows() { return rows; }
    /** @return whether the complete version-2 table must occupy one area */ public boolean isKeepTogether() { return keepTogether; }
    /** @return whether the final fragment must share its area with the next flow item */ public boolean isKeepWithNext() { return keepWithNext; }
    /** @return immutable repeated header grid */ public List<TableRow> getHeaderRows() { return headerRows; }
    /** @return immutable repeated footer grid */ public List<TableRow> getFooterRows() { return footerRows; }
    /** @return whether the first fragment omits its header */ public boolean isSkipFirstHeader() { return skipFirstHeader; }
    /** @return whether the final fragment omits its footer */ public boolean isSkipLastFooter() { return skipLastFooter; }
    /** @return horizontal cell-content overflow policy */ public Paragraph.Overflow getOverflow() { return overflow; }
    /** @return whether version-2 body rows may split at whole line boundaries */ public boolean isSplitRows() { return splitRows; }
    /** Records table declarations without opening resources or computing a grid. */
    public static final class Builder {
        private final int version;
        private final Layout layout;
        private final TableWidth width;
        private final List<TableWidth> columns = new ArrayList<TableWidth>();
        private final List<TableRow> rows = new ArrayList<TableRow>();
        private boolean keepTogether;
        private boolean keepWithNext;
        private final List<TableRow> headerRows = new ArrayList<TableRow>();
        private final List<TableRow> footerRows = new ArrayList<TableRow>();
        private boolean skipFirstHeader;
        private boolean skipLastFooter;
        private Paragraph.Overflow overflow = Paragraph.Overflow.WRAP;
        private boolean splitRows = true;
        private Builder(int version, Layout layout, TableWidth width, TableWidth[] columns) {
            this.version = version;
            this.layout = Objects.requireNonNull(layout, "layout");
            this.width = Objects.requireNonNull(width, "width");
            for (TableWidth column : Objects.requireNonNull(columns, "columns")) {
                this.columns.add(Objects.requireNonNull(column, "column"));
            }
        }
        /** Appends a row, including an explicitly empty span-continuation row. @return this builder */
        public Builder row(TableRow row) { rows.add(Objects.requireNonNull(row, "row")); return this; }
        /** Sets a hard version-2 whole-table keep. @return this builder */
        public Builder keepTogether(boolean value) { keepTogether = value; return this; }
        /** Keeps the version-2 final fragment with the next flow item. @return this builder */
        public Builder keepWithNext(boolean value) { keepWithNext = value; return this; }
        /** Appends a row to the version-2 repeated header grid. @return this builder */
        public Builder header(TableRow value) { headerRows.add(Objects.requireNonNull(value,"header")); return this; }
        /** Appends a row to the version-2 repeated footer grid. @return this builder */
        public Builder footer(TableRow value) { footerRows.add(Objects.requireNonNull(value,"footer")); return this; }
        /** Omits only the first version-2 header. @return this builder */
        public Builder skipFirstHeader(boolean value) { skipFirstHeader = value; return this; }
        /** Omits only the final version-2 footer. @return this builder */
        public Builder skipLastFooter(boolean value) { skipLastFooter = value; return this; }
        /** Sets version-2 horizontal cell overflow, without permitting vertical clipping. @return this builder */
        public Builder overflow(Paragraph.Overflow value) { overflow = Objects.requireNonNull(value,"overflow"); return this; }
        /** Enables or disables version-2 row splitting; defaults to true. @return this builder */
        public Builder splitRows(boolean value) { splitRows = value; return this; }
        /** @return immutable table */ public Table build() { return new Table(this); }
    }
}
