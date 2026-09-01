package net.zerocloud.pdf;

import java.util.Objects;

/**
 * The version-1 local GoTo Action.
 *
 * <p>This is the complete version-1 supported Action allowlist. It navigates
 * only inside the current document and is represented as data; Folio PDF does
 * not execute it.</p>
 *
 * @since 0.1.0
 */
public final class GoToAction {

    /** The currently supported Action representation version. */
    public static final int VERSION_1 = 1;

    private final NavigationTarget target;

    private GoToAction(NavigationTarget target) {
        this.target = target;
    }

    /**
     * Creates a version-1 local GoTo Action.
     * @param target an explicit or named destination in the current document
     * @return the immutable Action
     */
    public static GoToAction version1(NavigationTarget target) {
        return new GoToAction(Objects.requireNonNull(target, "target"));
    }

    /** Returns the representation version. @return {@link #VERSION_1} */
    public int getVersion() {
        return VERSION_1;
    }

    /** Returns the local navigation target. @return the target */
    public NavigationTarget getTarget() {
        return target;
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof GoToAction
                && target.equals(((GoToAction) candidate).target);
    }

    @Override
    public int hashCode() {
        return target.hashCode();
    }

    @Override
    public String toString() {
        return "GoToAction[target=" + target + "]";
    }
}
