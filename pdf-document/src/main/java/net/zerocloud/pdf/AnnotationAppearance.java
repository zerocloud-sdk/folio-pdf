package net.zerocloud.pdf;

import java.util.Arrays;
import java.util.Objects;

/**
 * An immutable resource-free normal appearance for a supported annotation.
 *
 * <p>Version 1 represents one Form appearance with an identity matrix, an
 * empty Resources dictionary, and caller-supplied PDF graphics instructions.
 * Content is limited to 1 MiB and to resource-free graphics-state, matrix,
 * path, paint, clip, line, rendering-intent, and device-color operators.
 * Numeric graphics-state and device-color operands must also satisfy their
 * PDF-defined ranges. Text, external objects, inline images, resource
 * lookups, rollover, down, and named appearance states are not represented.</p>
 *
 * @since 0.1.0
 */
public final class AnnotationAppearance {

    /** The currently supported representation version. */
    public static final int VERSION_1 = 1;

    private final AnnotationRectangle boundingBox;
    private final byte[] content;

    private AnnotationAppearance(
            AnnotationRectangle boundingBox,
            byte[] content) {
        this.boundingBox = Objects.requireNonNull(
                boundingBox,
                "boundingBox");
        this.content = Objects.requireNonNull(content, "content").clone();
    }

    /**
     * Creates a version-1 normal appearance.
     *
     * @param boundingBox the Form bounding box
     * @param content the resource-free PDF graphics instructions
     * @return the immutable appearance
     */
    public static AnnotationAppearance version1(
            AnnotationRectangle boundingBox,
            byte[] content) {
        return new AnnotationAppearance(boundingBox, content);
    }

    /** Returns the representation version. @return {@link #VERSION_1} */
    public int getVersion() {
        return VERSION_1;
    }

    /** Returns the Form bounding box. @return the bounding box */
    public AnnotationRectangle getBoundingBox() {
        return boundingBox;
    }

    /** Returns a copy of the appearance instructions. @return the content */
    public byte[] getContent() {
        return content.clone();
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof AnnotationAppearance
                && boundingBox.equals(
                        ((AnnotationAppearance) candidate).boundingBox)
                && Arrays.equals(
                        content,
                        ((AnnotationAppearance) candidate).content);
    }

    @Override
    public int hashCode() {
        return 31 * boundingBox.hashCode() + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "AnnotationAppearance[boundingBox=" + boundingBox
                + ", content=" + content.length + " bytes]";
    }
}
