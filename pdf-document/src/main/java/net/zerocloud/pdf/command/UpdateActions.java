package net.zerocloud.pdf.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.GoToAction;

/**
 * Atomically updates supported inert local GoTo bindings in the catalog and
 * page additional-action dictionaries.
 *
 * <p>The library stores these Actions as data and never executes them. Named
 * targets must resolve in the current document.</p>
 *
 * @since 0.1.0
 */
public final class UpdateActions implements DocumentCommand {

    /** The currently supported command representation version. */
    public static final int VERSION_1 = 1;

    private final boolean documentOpenActionUpdated;
    private final GoToAction documentOpenAction;
    private final Map<Integer, GoToAction> pageOpenActions;
    private final Map<Integer, GoToAction> pageCloseActions;
    private final List<Integer> removedPageOpenActions;
    private final List<Integer> removedPageCloseActions;

    private UpdateActions(Builder builder) {
        documentOpenActionUpdated = builder.documentOpenActionUpdated;
        documentOpenAction = builder.documentOpenAction;
        pageOpenActions = immutableMap(builder.pageOpenActions);
        pageCloseActions = immutableMap(builder.pageCloseActions);
        removedPageOpenActions = immutableList(builder.removedPageOpenActions);
        removedPageCloseActions = immutableList(builder.removedPageCloseActions);
    }

    private static Map<Integer, GoToAction> immutableMap(
            Map<Integer, GoToAction> source) {
        return Collections.unmodifiableMap(
                new LinkedHashMap<Integer, GoToAction>(source));
    }

    private static List<Integer> immutableList(Set<Integer> source) {
        return Collections.unmodifiableList(new ArrayList<Integer>(source));
    }

    /** Starts a version-1 Action update. @return a new builder */
    public static Builder version1() {
        return new Builder();
    }

    /** Returns the command version. @return {@link #VERSION_1} */
    public int getVersion() {
        return VERSION_1;
    }

    /** Returns whether the catalog open Action is updated or removed. */
    public boolean isDocumentOpenActionUpdated() {
        return documentOpenActionUpdated;
    }

    /** Returns the replacement catalog open Action, or {@code null}. */
    public GoToAction getDocumentOpenAction() {
        return documentOpenAction;
    }

    /** Returns page-open replacements in declaration order. */
    public Map<Integer, GoToAction> getPageOpenActions() {
        return pageOpenActions;
    }

    /** Returns page-close replacements in declaration order. */
    public Map<Integer, GoToAction> getPageCloseActions() {
        return pageCloseActions;
    }

    /** Returns page numbers whose open Action is removed. */
    public List<Integer> getRemovedPageOpenActions() {
        return removedPageOpenActions;
    }

    /** Returns page numbers whose close Action is removed. */
    public List<Integer> getRemovedPageCloseActions() {
        return removedPageCloseActions;
    }

    /** Builds an ordered version-1 Action update. @since 0.1.0 */
    public static final class Builder {

        private boolean documentOpenActionUpdated;
        private GoToAction documentOpenAction;
        private final Map<Integer, GoToAction> pageOpenActions =
                new LinkedHashMap<Integer, GoToAction>();
        private final Map<Integer, GoToAction> pageCloseActions =
                new LinkedHashMap<Integer, GoToAction>();
        private final Set<Integer> removedPageOpenActions =
                new LinkedHashSet<Integer>();
        private final Set<Integer> removedPageCloseActions =
                new LinkedHashSet<Integer>();

        private Builder() {
        }

        /** Sets the catalog open Action. @return this builder */
        public Builder setDocumentOpenAction(GoToAction action) {
            documentOpenActionUpdated = true;
            documentOpenAction = Objects.requireNonNull(action, "action");
            return this;
        }

        /** Removes the catalog open Action. @return this builder */
        public Builder removeDocumentOpenAction() {
            documentOpenActionUpdated = true;
            documentOpenAction = null;
            return this;
        }

        /** Sets one page-open Action. @return this builder */
        public Builder setPageOpenAction(int pageNumber, GoToAction action) {
            requirePageNumber(pageNumber);
            Integer key = Integer.valueOf(pageNumber);
            pageOpenActions.put(key, Objects.requireNonNull(action, "action"));
            removedPageOpenActions.remove(key);
            return this;
        }

        /** Removes one page-open Action. @return this builder */
        public Builder removePageOpenAction(int pageNumber) {
            requirePageNumber(pageNumber);
            Integer key = Integer.valueOf(pageNumber);
            pageOpenActions.remove(key);
            removedPageOpenActions.add(key);
            return this;
        }

        /** Sets one page-close Action. @return this builder */
        public Builder setPageCloseAction(int pageNumber, GoToAction action) {
            requirePageNumber(pageNumber);
            Integer key = Integer.valueOf(pageNumber);
            pageCloseActions.put(key, Objects.requireNonNull(action, "action"));
            removedPageCloseActions.remove(key);
            return this;
        }

        /** Removes one page-close Action. @return this builder */
        public Builder removePageCloseAction(int pageNumber) {
            requirePageNumber(pageNumber);
            Integer key = Integer.valueOf(pageNumber);
            pageCloseActions.remove(key);
            removedPageCloseActions.add(key);
            return this;
        }

        /** Builds the immutable update. @return the command */
        public UpdateActions build() {
            return new UpdateActions(this);
        }

        private static void requirePageNumber(int pageNumber) {
            if (pageNumber < 1) {
                throw new IllegalArgumentException(
                        "pageNumber must be at least 1");
            }
        }
    }
}
