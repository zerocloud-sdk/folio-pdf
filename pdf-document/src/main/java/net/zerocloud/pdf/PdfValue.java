package net.zerocloud.pdf;

/**
 * A backend-neutral low-level PDF Value.
 *
 * <p>This interface is not a user extension point. Document Patches accept
 * only values supplied by Open PDF.</p>
 *
 * @since 0.1.0
 */
public interface PdfValue {

    /**
     * Returns this value's PDF kind.
     *
     * @return the value kind
     */
    PdfValueKind getKind();
}
