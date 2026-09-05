package net.zerocloud.pdf.query;

import java.util.Objects;
import net.zerocloud.pdf.DocumentQuery;
import net.zerocloud.pdf.RenderOptions;
import net.zerocloud.pdf.RenderedPage;

/**
 * Renders one one-based page after all earlier Commands. The staged result is
 * thread-confined, closeable, and expires when its workflow callback ends.
 * Invalid page numbers and numeric options fail at the workflow seam.
 *
 * @since 0.1.0
 */
public final class RenderPage implements DocumentQuery<RenderedPage> {
    public static final int VERSION_1 = 1;
    private final int pageNumber;
    private final RenderOptions options;

    private RenderPage(int pageNumber, RenderOptions options) {
        this.pageNumber = pageNumber;
        this.options = Objects.requireNonNull(options, "options");
    }
    public static RenderPage version1(int pageNumber, RenderOptions options) {
        return new RenderPage(pageNumber, options);
    }
    public int getVersion() { return VERSION_1; }
    public int getPageNumber() { return pageNumber; }
    public RenderOptions getOptions() { return options; }
}
