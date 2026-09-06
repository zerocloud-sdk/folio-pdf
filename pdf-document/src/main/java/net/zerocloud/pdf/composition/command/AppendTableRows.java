package net.zerocloud.pdf.composition.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.composition.TableRow;

/** Appends body declarations to the current open large table without publishing. */
public final class AppendTableRows implements DocumentCommand {
    /** Representation version. */ public static final int VERSION_1 = 1;
    private final List<TableRow> rows;
    private AppendTableRows(TableRow[] rows) {
        List<TableRow> copy = new ArrayList<TableRow>();
        for (TableRow row : Objects.requireNonNull(rows, "rows")) { copy.add(Objects.requireNonNull(row, "row")); }
        this.rows = Collections.unmodifiableList(copy);
    }
    /** Declares the next ordered body rows. Admission occurs during execution. */
    public static AppendTableRows version1(TableRow... rows) { return new AppendTableRows(rows); }
    /** @return representation version */ public int getVersion() { return VERSION_1; }
    /** @return immutable ordered rows */ public List<TableRow> getRows() { return rows; }
}
