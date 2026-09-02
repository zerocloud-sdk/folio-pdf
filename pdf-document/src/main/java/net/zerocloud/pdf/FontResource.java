package net.zerocloud.pdf;

import java.util.List;
import java.util.Optional;

/**
 * Detached Font resource identity, embedding, subset, and Page Usage data.
 *
 * @since 0.1.0
 */
public final class FontResource extends DocumentResource {

    /** Declared PDF Font subtypes recognized by version 1. */
    public enum FontKind {
        TYPE_0,
        TYPE_1,
        MM_TYPE_1,
        TRUE_TYPE,
        TYPE_3,
        CID_FONT_TYPE_0,
        CID_FONT_TYPE_2,
        OTHER
    }

    /** Whether version 1 understands the well-formed Font subtype. */
    public enum Status {
        SUPPORTED,
        UNSUPPORTED
    }

    /** Font-program embedding classification. */
    public enum Embedding {
        EMBEDDED,
        NOT_EMBEDDED,
        UNKNOWN
    }

    private final FontKind fontKind;
    private final Status status;
    private final Embedding embedding;
    private final PdfName baseFontName;
    private final String subsetPrefix;

    FontResource(
            ObjectReference objectReference,
            List<ResourceDeclaration> declarations,
            List<Integer> pageUsage,
            FontKind fontKind,
            Status status,
            Embedding embedding,
            PdfName baseFontName,
            String subsetPrefix) {
        super(Kind.FONT, objectReference, declarations, pageUsage);
        this.fontKind = java.util.Objects.requireNonNull(fontKind, "fontKind");
        this.status = java.util.Objects.requireNonNull(status, "status");
        this.embedding = java.util.Objects.requireNonNull(embedding, "embedding");
        this.baseFontName = baseFontName;
        this.subsetPrefix = subsetPrefix;
    }

    /** Returns the declared subtype classification. @return kind */
    public FontKind getFontKind() { return fontKind; }

    /** Returns whether version 1 understands that subtype. @return status */
    public Status getStatus() { return status; }

    /** Returns the font-program embedding classification. @return state */
    public Embedding getEmbedding() { return embedding; }

    /** Returns the declared BaseFont name when present. @return name */
    public Optional<PdfName> getBaseFontName() {
        return Optional.ofNullable(baseFontName);
    }

    /** Returns whether the BaseFont name declares a subset prefix. @return value */
    public boolean isSubset() { return subsetPrefix != null; }

    /** Returns the exact six-letter subset prefix when present. @return prefix */
    public Optional<String> getSubsetPrefix() {
        return Optional.ofNullable(subsetPrefix);
    }
}
