package net.zerocloud.pdf;

import java.util.Objects;

/**
 * A detached logical-structure reference to an entire PDF object on a page.
 *
 * <p>The opaque identity remains comparable after Session close. Inspecting
 * the object requires the Session that issued it. This value neither executes
 * an action nor supplies rendered or replacement text.</p>
 *
 * @since 0.1.0
 */
public final class LogicalObjectReference {

    private final int pageNumber;
    private final ObjectReference objectReference;
    private final String subtype;

    LogicalObjectReference(int pageNumber, ObjectReference objectReference, String subtype) {
        this.pageNumber = pageNumber;
        this.objectReference = Objects.requireNonNull(objectReference, "objectReference");
        this.subtype = Objects.requireNonNull(subtype, "subtype");
    }

    /** @return the one-based declared or inherited page number */
    public int getPageNumber() { return pageNumber; }

    /** @return the Session-owned identity of the referenced PDF object */
    public ObjectReference getObjectReference() { return objectReference; }

    /** @return the referenced object's declared subtype */
    public String getSubtype() { return subtype; }
}
