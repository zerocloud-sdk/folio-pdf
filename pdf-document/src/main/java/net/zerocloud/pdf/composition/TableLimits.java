package net.zerocloud.pdf.composition;

/** Complete finite table bounds for one version-3 flow, including discarded layout candidates. */
public final class TableLimits {
    /** Supported declaration version. */ public static final int VERSION_1 = 1;
    private final int maximumTables;
    private final int maximumRows;
    private final int maximumColumns;
    private final int maximumCells;
    private final long maximumGridSlots;
    private final long maximumLayoutWork;
    private TableLimits(Builder builder) {
        maximumTables = builder.maximumTables;
        maximumRows = builder.maximumRows;
        maximumColumns = builder.maximumColumns;
        maximumCells = builder.maximumCells;
        maximumGridSlots = builder.maximumGridSlots;
        maximumLayoutWork = builder.maximumLayoutWork;
    }
    /** @return complete-limits builder */ public static Builder builder() { return new Builder(); }
    /** @return representation version */ public int getVersion() { return VERSION_1; }
    /** @return aggregate table declaration bound */ public int getMaximumTables() { return maximumTables; }
    /** @return aggregate row declaration bound */ public int getMaximumRows() { return maximumRows; }
    /** @return column declaration per table bound */ public int getMaximumColumns() { return maximumColumns; }
    /** @return aggregate cell declaration bound */ public int getMaximumCells() { return maximumCells; }
    /** @return aggregate rectangular grid slot bound */ public long getMaximumGridSlots() { return maximumGridSlots; }
    /** @return aggregate table layout work bound */ public long getMaximumLayoutWork() { return maximumLayoutWork; }
    /** Every field is mandatory; exact nonnegative boundaries are admitted. */
    public static final class Builder {
        private int maximumTables = -1;
        private int maximumRows = -1;
        private int maximumColumns = -1;
        private int maximumCells = -1;
        private long maximumGridSlots = -1;
        private long maximumLayoutWork = -1;
        private Builder() { }
        /** Sets the aggregate table declaration bound. @return this builder */
        public Builder maximumTables(int value) {
            if (value < 0) { throw new IllegalArgumentException("A table limit must not be negative."); }
            maximumTables = value; return this;
        }
        /** Sets the aggregate row declaration bound. @return this builder */
        public Builder maximumRows(int value) {
            if (value < 0) { throw new IllegalArgumentException("A table limit must not be negative."); }
            maximumRows = value; return this;
        }
        /** Sets the column declaration per table bound. @return this builder */
        public Builder maximumColumns(int value) {
            if (value < 0) { throw new IllegalArgumentException("A table limit must not be negative."); }
            maximumColumns = value; return this;
        }
        /** Sets the aggregate cell declaration bound. @return this builder */
        public Builder maximumCells(int value) {
            if (value < 0) { throw new IllegalArgumentException("A table limit must not be negative."); }
            maximumCells = value; return this;
        }
        /** Sets the aggregate rectangular grid slot bound. @return this builder */
        public Builder maximumGridSlots(long value) {
            if (value < 0) { throw new IllegalArgumentException("A table limit must not be negative."); }
            maximumGridSlots = value; return this;
        }
        /** Sets the aggregate table layout work bound. @return this builder */
        public Builder maximumLayoutWork(long value) {
            if (value < 0) { throw new IllegalArgumentException("A table limit must not be negative."); }
            maximumLayoutWork = value; return this;
        }
        /** @return immutable complete bounds */
        public TableLimits build() {
            if (maximumTables < 0 || maximumRows < 0 || maximumColumns < 0 || maximumCells < 0 || maximumGridSlots < 0 || maximumLayoutWork < 0) {
                throw new IllegalStateException("Every table limit must be declared.");
            }
            return new TableLimits(this);
        }
    }
}
