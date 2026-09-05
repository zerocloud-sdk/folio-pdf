package net.zerocloud.pdf.composition.command;

import java.util.Objects;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.composition.CompositionLimits;
import net.zerocloud.pdf.composition.ParagraphFlow;

/**
 * Lays out a finite paragraph flow and appends its reached new pages in one command.
 * Existing pages retain their sizes and content. A failed flow appends no pages.
 *
 * @since 0.1.0
 */
public final class ComposeParagraphs implements DocumentCommand {
    /** Supported command representation version. */ public static final int VERSION_1 = 1;
    private final ParagraphFlow flow;
    private final CompositionLimits limits;

    private ComposeParagraphs(ParagraphFlow flow, CompositionLimits limits) {
        this.flow = Objects.requireNonNull(flow, "flow");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /** Declares a version-1 paragraph composition command. */
    public static ComposeParagraphs version1(ParagraphFlow flow, CompositionLimits limits) {
        return new ComposeParagraphs(flow, limits);
    }
    /** @return command representation version */ public int getVersion() { return VERSION_1; }
    /** @return immutable semantic flow */ public ParagraphFlow getFlow() { return flow; }
    /** @return explicit finite bounds */ public CompositionLimits getLimits() { return limits; }
}
