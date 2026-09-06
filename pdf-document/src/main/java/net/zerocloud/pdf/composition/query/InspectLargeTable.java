package net.zerocloud.pdf.composition.query;

import net.zerocloud.pdf.DocumentQuery;
import net.zerocloud.pdf.composition.LargeTableState;

/** Observes the current large table's lifecycle and semantic retention. */
public final class InspectLargeTable implements DocumentQuery<LargeTableState> {
    /** Representation version. */ public static final int VERSION_1 = 1;
    private static final InspectLargeTable INSTANCE = new InspectLargeTable();
    private InspectLargeTable() { }
    /** @return the version-1 state query */ public static InspectLargeTable version1() { return INSTANCE; }
    /** @return representation version */ public int getVersion() { return VERSION_1; }
}
