package net.zerocloud.pdf.composition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable ordered source selection for positioned Unicode text.
 *
 * @since 0.1.0
 */
public final class FontSelection {

    /** Closed selection sources. */
    public enum Kind {
        /** Sources carried explicitly by this declaration. */
        EXPLICIT,
        /** The Workflow Environment's configured Reference Font Set. */
        REFERENCE_FONT_SET
    }

    private static final FontSelection REFERENCE = new FontSelection(
            Kind.REFERENCE_FONT_SET,
            Collections.<FontSource>emptyList());

    private final Kind kind;
    private final List<FontSource> sources;

    private FontSelection(Kind kind, List<FontSource> sources) {
        this.kind = kind;
        this.sources = Collections.unmodifiableList(
                new ArrayList<FontSource>(sources));
    }

    /**
     * Declares one or more explicit sources in strict fallback order.
     *
     * @param sources ordered sources, first match wins
     * @return immutable selection
     */
    public static FontSelection explicit(FontSource... sources) {
        List<FontSource> copy = FontSource.validatedCopy(
                sources,
                "At least one explicit Font Source is required.");
        return new FontSelection(Kind.EXPLICIT, copy);
    }

    /** Selects the configured Reference Font Set in declaration order. */
    public static FontSelection referenceFontSet() {
        return REFERENCE;
    }

    /** @return selection kind */
    public Kind getKind() {
        return kind;
    }

    /** @return immutable explicit sources, empty for the Reference Font Set */
    public List<FontSource> getSources() {
        return sources;
    }
}
