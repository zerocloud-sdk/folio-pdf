package net.zerocloud.pdf.composition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A declared row; cells occupy the first available columns in reading order. */
public final class TableRow {
    /** Supported declaration version. */ public static final int VERSION_1 = 1;
    private final double minimumHeight;
    private final List<TableCell> cells;
    private TableRow(double minimumHeight, TableCell[] cells) {
        this.minimumHeight = minimumHeight;
        List<TableCell> copy = new ArrayList<TableCell>();
        for (TableCell cell : Objects.requireNonNull(cells, "cells")) {
            copy.add(Objects.requireNonNull(cell, "cell"));
        }
        this.cells = Collections.unmodifiableList(copy);
    }
    /** Declares a nonnegative minimum point height and ordered cells. */
    public static TableRow version1(double minimumHeight, TableCell... cells) { return new TableRow(minimumHeight, cells); }
    /** Declares ordered cells with zero minimum height. */
    public static TableRow version1(TableCell... cells) { return new TableRow(0, cells); }
    /** @return representation version */ public int getVersion() { return VERSION_1; }
    /** @return minimum row height in points */ public double getMinimumHeight() { return minimumHeight; }
    /** @return immutable cells beginning in this row */ public List<TableCell> getCells() { return cells; }
}
