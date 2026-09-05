package net.zerocloud.pdf.composition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A bounded table occupying one Layout Area, with deterministic columns and a complete span grid. */
public final class Table {
    /** Supported declaration version. */ public static final int VERSION_1 = 1;
    /** Column resolution policy. */ public enum Layout { FIXED, AUTO }
    private final Layout layout;
    private final TableWidth width;
    private final List<TableWidth> columns;
    private final List<TableRow> rows;
    private Table(Builder builder) {
        layout = builder.layout; width = builder.width;
        columns = Collections.unmodifiableList(new ArrayList<TableWidth>(builder.columns));
        rows = Collections.unmodifiableList(new ArrayList<TableRow>(builder.rows));
    }
    /** Begins an explicit table; AUTO is allowed only in its column declarations. */
    public static Builder version1(Layout layout, TableWidth width, TableWidth... columns) {
        return new Builder(layout, width, columns);
    }
    /** @return representation version */ public int getVersion() { return VERSION_1; }
    /** @return column resolution policy */ public Layout getLayout() { return layout; }
    /** @return table width relative to its area */ public TableWidth getWidth() { return width; }
    /** @return immutable column widths relative to this table */ public List<TableWidth> getColumns() { return columns; }
    /** @return immutable row declarations */ public List<TableRow> getRows() { return rows; }
    /** Records table declarations without opening resources or computing a grid. */
    public static final class Builder {
        private final Layout layout;
        private final TableWidth width;
        private final List<TableWidth> columns = new ArrayList<TableWidth>();
        private final List<TableRow> rows = new ArrayList<TableRow>();
        private Builder(Layout layout, TableWidth width, TableWidth[] columns) {
            this.layout = Objects.requireNonNull(layout, "layout");
            this.width = Objects.requireNonNull(width, "width");
            for (TableWidth column : Objects.requireNonNull(columns, "columns")) {
                this.columns.add(Objects.requireNonNull(column, "column"));
            }
        }
        /** Appends a row, including an explicitly empty span-continuation row. @return this builder */
        public Builder row(TableRow row) { rows.add(Objects.requireNonNull(row, "row")); return this; }
        /** @return immutable table */ public Table build() { return new Table(this); }
    }
}
