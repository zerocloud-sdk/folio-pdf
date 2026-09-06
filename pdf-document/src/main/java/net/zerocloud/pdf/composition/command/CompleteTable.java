package net.zerocloud.pdf.composition.command;

import net.zerocloud.pdf.DocumentCommand;

/** Emits the final large-table fragment and releases all retained semantic rows. */
public final class CompleteTable implements DocumentCommand {
    /** Representation version. */ public static final int VERSION_1 = 1;
    private static final CompleteTable INSTANCE = new CompleteTable();
    private CompleteTable() { }
    /** Completes the current open table without publishing before the Workflow ends. */
    public static CompleteTable version1() { return INSTANCE; }
    /** @return representation version */ public int getVersion() { return VERSION_1; }
}
