package net.zerocloud.pdf.composition;

import java.util.Objects;

/** Detached observation of semantic body-row retention, independent of PDF storage or process RSS. */
public final class LargeTableState {
    /** Representation version. */ public static final int VERSION_1 = 1;
    /** Closed lifecycle stages. */
    public enum Stage {
        /** No large table has begun. */ NONE,
        /** Rows may be appended or flushed. */ OPEN,
        /** The table is complete and retains no semantic rows. */ COMPLETE
    }
    private final Stage stage;
    private final int acceptedRows;
    private final int retainedRows;
    private LargeTableState(Stage stage, int acceptedRows, int retainedRows) {
        this.stage = Objects.requireNonNull(stage, "stage");
        if (acceptedRows < 0 || retainedRows < 0 || retainedRows > acceptedRows) {
            throw new IllegalArgumentException("Invalid large table row counts.");
        }
        this.acceptedRows = acceptedRows; this.retainedRows = retainedRows;
    }
    /** Creates a detached observation. */
    public static LargeTableState version1(Stage stage, int acceptedRows, int retainedRows) {
        return new LargeTableState(stage, acceptedRows, retainedRows);
    }
    /** @return representation version */ public int getVersion() { return VERSION_1; }
    /** @return lifecycle stage */ public Stage getStage() { return stage; }
    /** @return cumulatively admitted body rows */ public int getAcceptedRows() { return acceptedRows; }
    /** @return body rows whose declarations remain retained, including partial rows */ public int getRetainedRows() { return retainedRows; }
    /** @return body rows fully emitted and released */ public int getFlushedRows() { return acceptedRows - retainedRows; }
}
