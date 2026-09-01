package net.zerocloud.pdf;

import java.util.Objects;

/**
 * The immutable activation behavior of a supported Link annotation.
 *
 * @since 0.1.0
 */
public final class LinkActivation {

    /** Supported link activation representations. @since 0.1.0 */
    public enum Kind {
        /** A Link Dest entry containing a local navigation target. */
        DESTINATION,

        /** A Link A entry containing the supported local GoTo Action. */
        GO_TO_ACTION
    }

    private final Kind kind;
    private final NavigationTarget target;

    private LinkActivation(Kind kind, NavigationTarget target) {
        this.kind = kind;
        this.target = target;
    }

    /**
     * Creates a Link destination activation.
     * @param target the local navigation target
     * @return the immutable activation
     */
    public static LinkActivation destination(NavigationTarget target) {
        return new LinkActivation(
                Kind.DESTINATION,
                Objects.requireNonNull(target, "target"));
    }

    /**
     * Creates a Link activation using the supported local GoTo Action.
     * @param action the local GoTo Action
     * @return the immutable activation
     */
    public static LinkActivation action(GoToAction action) {
        return new LinkActivation(
                Kind.GO_TO_ACTION,
                Objects.requireNonNull(action, "action").getTarget());
    }

    /** Returns the activation representation. @return the kind */
    public Kind getKind() {
        return kind;
    }

    /** Returns the local navigation target. @return the target */
    public NavigationTarget getTarget() {
        return target;
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof LinkActivation
                && kind == ((LinkActivation) candidate).kind
                && target.equals(((LinkActivation) candidate).target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, target);
    }

    @Override
    public String toString() {
        return "LinkActivation[kind=" + kind + ", target=" + target + "]";
    }
}
