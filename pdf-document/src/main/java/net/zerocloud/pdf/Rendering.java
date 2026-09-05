package net.zerocloud.pdf;

import java.util.Objects;
import net.zerocloud.pdf.query.RenderPage;

/** Page rendering through the Native Interface; no external engine is required. */
public final class Rendering {
    public static final String CAPABILITY_ID = "conversion.rendering";
    public static final String DEFAULT_PROVIDER_ID = "folio.pdfbox-renderer";

    private Rendering() { }

    /** Returns the facts for the bundled, offline default Rendering Provider. */
    public static net.zerocloud.pdf.provider.ProviderMetadata getDefaultProviderMetadata() {
        return RenderingCoordinator.DEFAULT_METADATA;
    }

    /** Consumes a staged page on the workflow caller thread. */
    @FunctionalInterface
    public interface PageConsumer {
        void accept(RenderedPage page) throws DocumentFailure;
    }

    /**
     * Renders and consumes each one-based page in declaration order, including
     * duplicates. Each Query observes Commands completed before that Query.
     * The next page is not rendered until its predecessor is consumed and
     * closed. Stops at the first failure; previously consumed output cannot be
     * rolled back. The supplied array must not be mutated during this call.
     */
    public static void renderPages(DocumentSession session, int[] pages,
            RenderOptions options, PageConsumer consumer) throws DocumentFailure {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(pages, "pages");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(consumer, "consumer");
        for (int page : pages) {
            try (RenderedPage result = session.query(RenderPage.version1(page, options))) {
                consumer.accept(result);
            }
        }
    }
}
