package net.zerocloud.pdf.composition;

/** Immutable width declaration. Percentages use the containing area or table, never a page. */
public final class TableWidth {
    /** Width interpretation. */
    public enum Kind { POINTS, PERCENTAGE, AUTO }
    private final Kind kind;
    private final double value;
    private TableWidth(Kind kind, double value) { this.kind = kind; this.value = value; }
    /** Declares an exact point width (or a minimum when used on a cell). */
    public static TableWidth points(double value) { return new TableWidth(Kind.POINTS, value); }
    /** Declares a percentage, expressed as 0 through 100, validated during execution. */
    public static TableWidth percentage(double value) { return new TableWidth(Kind.PERCENTAGE, value); }
    /** Declares a flexible column. AUTO is not a table width or cell minimum. */
    public static TableWidth auto() { return new TableWidth(Kind.AUTO, 0); }
    /** @return interpretation */ public Kind getKind() { return kind; }
    /** @return point or percentage value; zero for AUTO */ public double getValue() { return value; }
}
