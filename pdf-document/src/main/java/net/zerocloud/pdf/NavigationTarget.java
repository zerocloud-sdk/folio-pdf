package net.zerocloud.pdf;

import java.util.Objects;
import java.util.Optional;

/**
 * An immutable local navigation target: an explicit page destination or a
 * named-destination reference.
 *
 * @since 0.1.0
 */
public final class NavigationTarget {

    /** Supported target kinds. @since 0.1.0 */
    public enum Kind {
        /** An explicit page destination. */ PAGE,
        /** A named-destination reference. */ NAMED
    }

    private final PageDestination pageDestination;
    private final String namedDestination;

    private NavigationTarget(
            PageDestination pageDestination,
            String namedDestination) {
        this.pageDestination = pageDestination;
        this.namedDestination = namedDestination;
    }

    /**
     * Creates an explicit local page target.
     * @param destination the page destination
     * @return the immutable target
     */
    public static NavigationTarget toPage(PageDestination destination) {
        return new NavigationTarget(
                Objects.requireNonNull(destination, "destination"),
                null);
    }

    /**
     * Creates a named local target.
     * @param name the nonempty destination name
     * @return the immutable target
     */
    public static NavigationTarget toNamedDestination(String name) {
        if (Objects.requireNonNull(name, "name").isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        return new NavigationTarget(null, name);
    }

    /** Returns the target kind. @return the kind */
    public Kind getKind() {
        return pageDestination == null ? Kind.NAMED : Kind.PAGE;
    }

    /** Returns the explicit page destination when present. @return the value */
    public Optional<PageDestination> getPageDestination() {
        return Optional.ofNullable(pageDestination);
    }

    /** Returns the named-destination reference when present. @return the name */
    public Optional<String> getNamedDestination() {
        return Optional.ofNullable(namedDestination);
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof NavigationTarget
                && Objects.equals(pageDestination,
                        ((NavigationTarget) candidate).pageDestination)
                && Objects.equals(namedDestination,
                        ((NavigationTarget) candidate).namedDestination);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pageDestination, namedDestination);
    }

    @Override
    public String toString() {
        return "NavigationTarget[page=" + pageDestination
                + ", named=" + namedDestination + "]";
    }
}
