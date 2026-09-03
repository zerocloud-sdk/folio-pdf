package net.zerocloud.pdf.composition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable, declaration-ordered reusable font sources for a Workflow
 * Environment.
 *
 * <p>Only byte and Path declarations are accepted because an environment may
 * be shared by concurrent workflows. Caller-owned one-shot streams and
 * channels remain available through {@link FontSelection#explicit}.</p>
 *
 * @since 0.1.0
 */
public final class ReferenceFontSet {

    /** The currently supported set representation version. */
    public static final int VERSION_1 = 1;

    private static final ReferenceFontSet EMPTY = new ReferenceFontSet(
            Collections.<FontSource>emptyList());

    private final List<FontSource> sources;

    private ReferenceFontSet(List<FontSource> sources) {
        this.sources = Collections.unmodifiableList(
                new ArrayList<FontSource>(sources));
    }

    /**
     * Declares one or more reusable sources in strict fallback order.
     *
     * @param sources byte or Path sources, first match wins
     * @return immutable reference set
     */
    public static ReferenceFontSet version1(FontSource... sources) {
        List<FontSource> copy = FontSource.validatedCopy(
                sources,
                "At least one Reference Font source is required.");
        for (FontSource source : copy) {
            if (source.getSourceKind() != FontSource.SourceKind.BYTES
                    && source.getSourceKind() != FontSource.SourceKind.PATH) {
                throw new IllegalArgumentException(
                        "Reference Font sources must be reusable.");
            }
        }
        return new ReferenceFontSet(copy);
    }

    /** Returns the empty set used by offline system defaults. */
    public static ReferenceFontSet empty() {
        return EMPTY;
    }

    /** @return {@link #VERSION_1} */
    public int getVersion() {
        return VERSION_1;
    }

    /** Returns immutable declarations in strict fallback order. */
    public List<FontSource> getSources() {
        return sources;
    }
}
