package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable supported catalog and page Action bindings.
 *
 * @since 0.1.0
 */
public final class DocumentActions {

    private final GoToAction documentOpenAction;
    private final List<PageActions> pageActions;

    DocumentActions(
            GoToAction documentOpenAction,
            List<PageActions> pageActions) {
        this.documentOpenAction = documentOpenAction;
        this.pageActions = Collections.unmodifiableList(
                new ArrayList<PageActions>(pageActions));
    }

    /** Returns the catalog open Action when present. @return the Action */
    public Optional<GoToAction> getDocumentOpenAction() {
        return Optional.ofNullable(documentOpenAction);
    }

    /** Returns page Action bindings in page order. @return the bindings */
    public List<PageActions> getPageActions() {
        return pageActions;
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof DocumentActions
                && Objects.equals(documentOpenAction,
                        ((DocumentActions) candidate).documentOpenAction)
                && pageActions.equals(
                        ((DocumentActions) candidate).pageActions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentOpenAction, pageActions);
    }

    @Override
    public String toString() {
        return "DocumentActions[open=" + documentOpenAction
                + ", pages=" + pageActions + "]";
    }
}
