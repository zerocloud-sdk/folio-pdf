package net.zerocloud.pdf.query;

import net.zerocloud.pdf.DocumentQuery;

/**
 * Reports the number of pages after all preceding session commands.
 *
 * @since 0.1.0
 */
public final class PageCount implements DocumentQuery<Integer> {

    /** The immutable query instance. */
    public static final PageCount INSTANCE = new PageCount();

    private PageCount() {
    }
}
