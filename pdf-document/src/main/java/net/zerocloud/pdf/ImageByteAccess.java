package net.zerocloud.pdf;

/**
 * Explicit image-byte selection for one resource extraction query.
 *
 * @since 0.1.0
 */
public enum ImageByteAccess {
    /** Inventory metadata and byte availability only. */
    NONE(false, false),

    /** Include available raw encoded stream bytes. */
    ENCODED(true, false),

    /** Include available bounded post-filter bytes. */
    DECODED(false, true),

    /** Include both available representations. */
    ENCODED_AND_DECODED(true, true);

    private final boolean encoded;
    private final boolean decoded;

    ImageByteAccess(boolean encoded, boolean decoded) {
        this.encoded = encoded;
        this.decoded = decoded;
    }

    /** Returns whether encoded bytes were selected. @return the selection */
    public boolean includesEncoded() {
        return encoded;
    }

    /** Returns whether decoded bytes were selected. @return the selection */
    public boolean includesDecoded() {
        return decoded;
    }
}
