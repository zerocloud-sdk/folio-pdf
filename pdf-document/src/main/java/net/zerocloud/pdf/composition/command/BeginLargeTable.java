package net.zerocloud.pdf.composition.command;

import java.util.Objects;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.composition.CompositionLimits;
import net.zerocloud.pdf.composition.ParagraphFlow;

/** Begins one incremental FIXED table over the version-4 flow's finite pages. */
public final class BeginLargeTable implements DocumentCommand {
    /** Representation version. */ public static final int VERSION_1 = 1;
    private final ParagraphFlow flow;
    private final CompositionLimits limits;
    private final int maximumRetainedRows;
    private BeginLargeTable(ParagraphFlow flow, CompositionLimits limits, int maximumRetainedRows) {
        this.flow = Objects.requireNonNull(flow, "flow");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.maximumRetainedRows = maximumRetainedRows;
    }
    /** Declares an empty-body table, cumulative operation limits and a positive retained-row bound. */
    public static BeginLargeTable version1(ParagraphFlow flow, CompositionLimits limits, int maximumRetainedRows) {
        return new BeginLargeTable(flow, limits, maximumRetainedRows);
    }
    /** @return representation version */ public int getVersion() { return VERSION_1; }
    /** @return immutable page, font and empty-body table declarations */ public ParagraphFlow getFlow() { return flow; }
    /** @return cumulative finite limits */ public CompositionLimits getLimits() { return limits; }
    /** @return maximum body rows retained between commands */ public int getMaximumRetainedRows() { return maximumRetainedRows; }
}
