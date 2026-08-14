package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable backend-neutral document outline item.
 *
 * <p>An item carries a title, at most one destination (an explicit page
 * destination or a named-destination reference), and zero or more children.
 * An item without a destination is a pure grouping node.</p>
 *
 * @since 0.1.0
 */
public final class OutlineItem {

    private final String title;
    private final PageDestination destination;
    private final String namedDestination;
    private final List<OutlineItem> children;

    private OutlineItem(
            String title,
            PageDestination destination,
            String namedDestination,
            List<OutlineItem> children) {
        this.title = Objects.requireNonNull(title, "title");
        this.destination = destination;
        this.namedDestination = namedDestination;
        List<OutlineItem> copied = new ArrayList<OutlineItem>(children.size());
        for (OutlineItem child : children) {
            copied.add(Objects.requireNonNull(child, "children"));
        }
        this.children = Collections.unmodifiableList(copied);
    }

    /**
     * Creates a grouping item without a destination.
     *
     * @param title the item title
     * @param children the child items
     * @return the immutable outline item
     */
    public static OutlineItem grouping(
            String title,
            List<OutlineItem> children) {
        return new OutlineItem(
                title,
                null,
                null,
                Objects.requireNonNull(children, "children"));
    }

    /**
     * Creates an item targeting an explicit page destination.
     *
     * @param title the item title
     * @param destination the explicit page destination
     * @param children the child items
     * @return the immutable outline item
     */
    public static OutlineItem toPage(
            String title,
            PageDestination destination,
            List<OutlineItem> children) {
        return new OutlineItem(
                title,
                Objects.requireNonNull(destination, "destination"),
                null,
                Objects.requireNonNull(children, "children"));
    }

    /**
     * Creates an item targeting a named destination.
     *
     * @param title the item title
     * @param namedDestination the named-destination reference
     * @param children the child items
     * @return the immutable outline item
     */
    public static OutlineItem toNamedDestination(
            String title,
            String namedDestination,
            List<OutlineItem> children) {
        return new OutlineItem(
                title,
                null,
                Objects.requireNonNull(namedDestination, "namedDestination"),
                Objects.requireNonNull(children, "children"));
    }

    /**
     * Returns the item title.
     *
     * @return the title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the explicit page destination when present.
     *
     * @return the page destination, or empty
     */
    public Optional<PageDestination> getDestination() {
        return Optional.ofNullable(destination);
    }

    /**
     * Returns the named-destination reference when present.
     *
     * @return the named-destination reference, or empty
     */
    public Optional<String> getNamedDestination() {
        return Optional.ofNullable(namedDestination);
    }

    /**
     * Returns the immutable child items in outline order.
     *
     * @return the children
     */
    public List<OutlineItem> getChildren() {
        return children;
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof OutlineItem
                && title.equals(((OutlineItem) candidate).title)
                && Objects.equals(
                        destination,
                        ((OutlineItem) candidate).destination)
                && Objects.equals(
                        namedDestination,
                        ((OutlineItem) candidate).namedDestination)
                && children.equals(((OutlineItem) candidate).children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, destination, namedDestination, children);
    }

    @Override
    public String toString() {
        return "OutlineItem[title=" + title
                + ", destination=" + destination
                + ", namedDestination=" + namedDestination
                + ", children=" + children + "]";
    }
}
