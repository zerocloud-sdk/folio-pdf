package net.zerocloud.pdf;

/**
 * The disposition of one publication target.
 *
 * @since 0.1.0
 */
public enum PublicationStatus {
    /** The validated staged output was committed to the target. */
    COMMITTED,

    /** Publication was attempted but did not commit. */
    FAILED,

    /** Publication was not attempted. */
    NOT_ATTEMPTED
}
