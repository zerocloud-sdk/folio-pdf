package net.zerocloud.pdf.composition.command;

import net.zerocloud.pdf.DocumentCommand;

/** Releases the current buffered paragraph declaration and seals it against relayout; never publishes early. */
public final class FlushParagraphs implements DocumentCommand {
    /** Representation version. */ public static final int VERSION_1 = 1;
    private static final FlushParagraphs INSTANCE = new FlushParagraphs();
    private FlushParagraphs() { }
    /** Declares an idempotent flush of the current paragraph flow. */
    public static FlushParagraphs version1() { return INSTANCE; }
    /** @return representation version */ public int getVersion() { return VERSION_1; }
}
