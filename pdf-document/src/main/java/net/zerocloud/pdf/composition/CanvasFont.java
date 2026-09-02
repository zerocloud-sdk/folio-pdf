package net.zerocloud.pdf.composition;

import java.util.Objects;
import net.zerocloud.pdf.ObjectReference;

/**
 * An explicit existing Font resource used by positioned Canvas text.
 *
 * <p>Version 1 accepts only an indirect Font {@link ObjectReference} owned by
 * the current Document Session. It does not discover, load, embed, subset, or
 * map fonts.</p>
 *
 * @since 0.1.0
 */
public final class CanvasFont {

    /** The currently supported Canvas Font representation version. */
    public static final int VERSION_1 = 1;

    private final ObjectReference objectReference;

    private CanvasFont(ObjectReference objectReference) {
        this.objectReference = Objects.requireNonNull(
                objectReference,
                "objectReference");
    }

    /**
     * Declares an existing Session-owned indirect Font resource.
     *
     * @param objectReference the Font object identity
     * @return the immutable declaration
     */
    public static CanvasFont version1(ObjectReference objectReference) {
        return new CanvasFont(objectReference);
    }

    /** @return {@link #VERSION_1} */
    public int getVersion() {
        return VERSION_1;
    }

    /** @return the existing Font object identity */
    public ObjectReference getObjectReference() {
        return objectReference;
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof CanvasFont
                && objectReference.equals(
                        ((CanvasFont) candidate).objectReference);
    }

    @Override
    public int hashCode() {
        return objectReference.hashCode();
    }

    @Override
    public String toString() {
        return "CanvasFont[" + objectReference + "]";
    }
}
