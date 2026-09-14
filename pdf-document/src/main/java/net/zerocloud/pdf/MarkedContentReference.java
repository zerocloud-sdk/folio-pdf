package net.zerocloud.pdf;

import java.util.Optional;

/** A detached logical-structure reference to page or Form marked content. @since 0.1.0 */
public final class MarkedContentReference {

    private final int pageNumber;
    private final int markedContentId;
    private final int contentStreamId;
    private final Integer markedContentSequenceId;
    private final ObjectReference streamOwner;

    MarkedContentReference(
            int pageNumber,
            int markedContentId,
            int contentStreamId,
            Integer markedContentSequenceId,
            ObjectReference streamOwner) {
        this.pageNumber = pageNumber;
        this.markedContentId = markedContentId;
        this.contentStreamId = contentStreamId;
        this.markedContentSequenceId = markedContentSequenceId;
        this.streamOwner = streamOwner;
    }

    /** @return the one-based page number */
    public int getPageNumber() { return pageNumber; }

    /** @return the stream-local PDF marked-content identifier ({@code MCID}) */
    public int getMarkedContentId() { return markedContentId; }

    /**
     * @return zero for page Contents, or the positive extraction-local Form
     * stream identifier shared with {@link MarkedContentSequence#getContentStreamId()}
     */
    public int getContentStreamId() { return contentStreamId; }

    /** @return the optional Session-owned identity declared by {@code StmOwn} */
    public Optional<ObjectReference> getStreamOwner() { return Optional.ofNullable(streamOwner); }

    /**
     * Returns the matching extracted marked-content sequence, when exactly one
     * occurrence of the referenced stream's MCID was executed on this page.
     *
     * @return the optional one-based page-local sequence identifier
     */
    public Optional<Integer> getMarkedContentSequenceId() {
        return Optional.ofNullable(markedContentSequenceId);
    }
}
