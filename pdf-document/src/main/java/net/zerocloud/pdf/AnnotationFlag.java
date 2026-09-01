package net.zerocloud.pdf;

/**
 * Version-1 flags represented by the supported annotation model.
 *
 * @since 0.1.0
 */
public enum AnnotationFlag {
    /** Do not display an annotation with an unknown type. */
    INVISIBLE,
    /** Do not display or print the annotation. */
    HIDDEN,
    /** Print the annotation when the page is printed. */
    PRINT,
    /** Keep the annotation at a fixed size while zooming. */
    NO_ZOOM,
    /** Do not rotate the annotation with the page. */
    NO_ROTATE,
    /** Do not display the annotation on screen. */
    NO_VIEW,
    /** Do not permit interactive changes to annotation properties. */
    READ_ONLY,
    /** Do not permit the annotation to be deleted or repositioned. */
    LOCKED,
    /** Invert the interpretation of the no-view flag for some events. */
    TOGGLE_NO_VIEW,
    /** Do not permit the annotation contents to be changed. */
    LOCKED_CONTENTS
}
