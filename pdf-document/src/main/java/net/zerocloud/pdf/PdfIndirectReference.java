package net.zerocloud.pdf;

import java.util.Objects;

/**
 * An immutable PDF indirect-reference value scoped to one Document Session.
 *
 * @since 0.1.0
 */
public final class PdfIndirectReference implements PdfValue {

    private final ObjectReference reference;

    private PdfIndirectReference(ObjectReference reference) {
        this.reference = Objects.requireNonNull(reference, "reference");
    }

    /**
     * Creates an indirect-reference value for a Session-owned object.
     *
     * @param reference the opaque object identity
     * @return an immutable indirect-reference value
     */
    public static PdfIndirectReference of(ObjectReference reference) {
        return new PdfIndirectReference(reference);
    }

    /**
     * Returns the referenced object identity.
     *
     * @return the Object Reference
     */
    public ObjectReference getReference() {
        return reference;
    }

    @Override
    public PdfValueKind getKind() {
        return PdfValueKind.INDIRECT_REFERENCE;
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof PdfIndirectReference
                && reference.equals(
                        ((PdfIndirectReference) candidate).reference);
    }

    @Override
    public int hashCode() {
        return reference.hashCode();
    }

    @Override
    public String toString() {
        return "PdfIndirectReference[" + reference + "]";
    }
}
