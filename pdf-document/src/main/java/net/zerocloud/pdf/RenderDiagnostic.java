package net.zerocloud.pdf;

/** Bounded document-safe rendering observations; no resource names or content. */
public enum RenderDiagnostic {
    /** An executed font lacks an embedded usable outline program. */
    FONT_SUBSTITUTED,
    /** An executed character lacks an outline in the selected font. */
    GLYPH_SUBSTITUTED,
    /** Image decoding uses the installed platform codec selection. */
    PLATFORM_IMAGE_CODEC,
    /** An annotation has no existing normal appearance and was omitted. */
    ANNOTATION_APPEARANCE_MISSING
}
