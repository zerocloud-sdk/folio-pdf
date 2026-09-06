package net.zerocloud.pdf.composition.command;

import java.util.Objects;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.composition.CompositionLimits;
import net.zerocloud.pdf.composition.ParagraphFlow;

/**
 * Lays out a finite paragraph/table flow and appends its reached new pages in one command.
 * Existing pages retain their sizes and content. A failed flow appends no pages.
 *
 * @since 0.1.0
 */
public final class ComposeParagraphs implements DocumentCommand {
    /** Supported command representation version. */ public static final int VERSION_1 = 1;
    /** Advanced pagination command representation. */ public static final int VERSION_2 = 2;
    /** Bounded table command representation. */ public static final int VERSION_3 = 3;
    /** Paginated table command representation. */ public static final int VERSION_4 = 4;
    /** Retention of semantic declarations after successful composition; neither mode publishes early. */
    public enum FlushMode {
        /** Retain the current declaration under finite limits for relayout. */ BUFFERED,
        /** Release the declaration and reject later relayout. */ IMMEDIATE
    }
    private final int version;
    private final FlushMode flushMode;
    private final ParagraphFlow flow;
    private final CompositionLimits limits;

    private ComposeParagraphs(int version, ParagraphFlow flow, CompositionLimits limits, FlushMode flushMode) {
        this.version = version;
        this.flushMode = Objects.requireNonNull(flushMode, "flushMode");
        this.flow = Objects.requireNonNull(flow, "flow");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /** Declares a version-1 paragraph composition command. */
    public static ComposeParagraphs version1(ParagraphFlow flow, CompositionLimits limits) {
        return new ComposeParagraphs(VERSION_1, flow, limits, FlushMode.IMMEDIATE);
    }
    /** @return command representation version */ public int getVersion() { return version; }
    /** Declares advanced pagination retaining the current flow for bounded relayout. */
    public static ComposeParagraphs version2(ParagraphFlow flow, CompositionLimits limits) {
        return version2(flow, limits, FlushMode.BUFFERED);
    }
    /** Declares advanced pagination with explicit retention. Publication remains transactional. */
    public static ComposeParagraphs version2(ParagraphFlow flow, CompositionLimits limits, FlushMode flushMode) {
        return new ComposeParagraphs(VERSION_2, flow, limits, flushMode);
    }
    /** Declares a mixed table flow with immediate semantic release and transactional publication. */
    public static ComposeParagraphs version3(ParagraphFlow flow, CompositionLimits limits) {
        return new ComposeParagraphs(VERSION_3, flow, limits, FlushMode.IMMEDIATE);
    }
    /** Declares table pagination with buffered semantic retention. */
    public static ComposeParagraphs version4(ParagraphFlow flow, CompositionLimits limits) {
        return version4(flow, limits, FlushMode.BUFFERED);
    }
    /** Declares table pagination with explicit semantic retention and transactional publication. */
    public static ComposeParagraphs version4(ParagraphFlow flow, CompositionLimits limits, FlushMode flushMode) {
        return new ComposeParagraphs(VERSION_4, flow, limits, flushMode);
    }
    /** @return semantic declaration retention policy */ public FlushMode getFlushMode() { return flushMode; }
    /** @return immutable semantic flow */ public ParagraphFlow getFlow() { return flow; }
    /** @return explicit finite bounds */ public CompositionLimits getLimits() { return limits; }
}
