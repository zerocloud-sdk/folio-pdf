package net.zerocloud.pdf.composition.command;

import net.zerocloud.pdf.DocumentCommand;

/** Emits decided large-table fragments and releases their body declarations; never publishes early. */
public final class FlushTable implements DocumentCommand {
    /** Representation version. */ public static final int VERSION_1 = 1;
    private static final FlushTable INSTANCE = new FlushTable();
    private FlushTable() { }
    /** Flushes the current open table while retaining its undecided final fragment. */
    public static FlushTable version1() { return INSTANCE; }
    /** @return representation version */ public int getVersion() { return VERSION_1; }
}
