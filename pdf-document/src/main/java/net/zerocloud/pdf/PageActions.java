package net.zerocloud.pdf;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable supported open and close Actions for one page.
 *
 * @since 0.1.0
 */
public final class PageActions {

    private final int pageNumber;
    private final GoToAction openAction;
    private final GoToAction closeAction;

    PageActions(
            int pageNumber,
            GoToAction openAction,
            GoToAction closeAction) {
        this.pageNumber = pageNumber;
        this.openAction = openAction;
        this.closeAction = closeAction;
    }

    /** Returns the one-based page number. @return the page number */
    public int getPageNumber() {
        return pageNumber;
    }

    /** Returns the page-open Action when present. @return the Action */
    public Optional<GoToAction> getOpenAction() {
        return Optional.ofNullable(openAction);
    }

    /** Returns the page-close Action when present. @return the Action */
    public Optional<GoToAction> getCloseAction() {
        return Optional.ofNullable(closeAction);
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof PageActions
                && pageNumber == ((PageActions) candidate).pageNumber
                && Objects.equals(openAction,
                        ((PageActions) candidate).openAction)
                && Objects.equals(closeAction,
                        ((PageActions) candidate).closeAction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(pageNumber),
                openAction, closeAction);
    }

    @Override
    public String toString() {
        return "PageActions[page=" + pageNumber + ", open=" + openAction
                + ", close=" + closeAction + "]";
    }
}
