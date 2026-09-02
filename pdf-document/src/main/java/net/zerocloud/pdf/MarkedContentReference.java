package net.zerocloud.pdf;

import java.util.Optional;

/** A detached logical-structure reference to page marked content. @since 0.1.0 */
public final class MarkedContentReference {

    private final int pageNumber;
    private final int markedContentId;
    private final Integer markedContentSequenceId;

    MarkedContentReference(
            int pageNumber,
            int markedContentId,
            Integer markedContentSequenceId) {
        this.pageNumber = pageNumber;
        this.markedContentId = markedContentId;
        this.markedContentSequenceId = markedContentSequenceId;
    }

    /** @return the one-based page number */
    public int getPageNumber() { return pageNumber; }

    /** @return the page-local PDF marked-content identifier ({@code MCID}) */
    public int getMarkedContentId() { return markedContentId; }

    /**
     * Returns the matching extracted marked-content sequence, when exactly one
     * page sequence declares this MCID.
     *
     * @return the optional one-based page-local sequence identifier
     */
    public Optional<Integer> getMarkedContentSequenceId() {
        return Optional.ofNullable(markedContentSequenceId);
    }
}
