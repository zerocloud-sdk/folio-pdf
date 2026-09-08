package net.zerocloud.pdf.itext7.kernel.pdf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.zerocloud.pdf.DocumentPatch;

/** A detached array or a mutable, bounded Session view. @since 0.1.0 */
public final class PdfArray extends PdfObject {
    final List<PdfObject> detached;
    private final FacadeSession session;
    private final FacadeLocation location;
    private final net.zerocloud.pdf.PdfArray value;

    /** Creates an empty detached array. */
    public PdfArray() {
        detached = new ArrayList<PdfObject>();
        session = null;
        location = null;
        value = null;
    }

    /** @param values ordered elements, copied from the caller's list */
    public PdfArray(List<? extends PdfObject> values) {
        this();
        for (PdfObject element : Objects.requireNonNull(values, "values")) {
            detached.add(Objects.requireNonNull(element, "element"));
        }
    }

    PdfArray(FacadeSession session, FacadeLocation location, net.zerocloud.pdf.PdfArray value) {
        this.session = session;
        this.location = location;
        this.value = value;
        this.detached = null;
    }

    /** @return the number of elements */
    public int size() {
        return value == null ? detached.size() : session.view(value::size).intValue();
    }

    /** @param index zero-based element index @return the direct element */
    public PdfObject get(int index) {
        return get(index, true);
    }

    /** @param index zero-based element index @param asDirect whether to dereference @return the element */
    public PdfObject get(int index, boolean asDirect) {
        if (value == null) {
            return FacadeValues.direct(detached.get(index), asDirect);
        }
        return session.view(() -> value.get(index), (child, nativeSession) -> FacadeValues.wrap(child, session,
                location == null ? null : location.arrayElement(index), asDirect, nativeSession));
    }

    /** @param index zero-based element index @param replacement replacement value @return the previous element */
    public PdfObject set(int index, PdfObject replacement) {
        Objects.requireNonNull(replacement, "replacement");
        if (value == null) {
            return detached.set(index, replacement);
        }
        requireAttached();
        return session.call(nativeSession -> {
            PdfObject previous = index >= 0 && index < value.size()
                    ? FacadeValues.wrap(value.get(index), session, null, false, nativeSession) : null;
            nativeSession.execute(DocumentPatch.builder().setArrayElement(location.path(), index, replacement.valueForPatch()).build());
            location.arrayReplaced(index);
            return previous;
        });
    }

    /** @param element value to append */
    public void add(PdfObject element) {
        add(size(), element);
    }

    /** @param index insertion index from zero through size @param element value to insert */
    public void add(int index, PdfObject element) {
        Objects.requireNonNull(element, "element");
        if (value == null) {
            detached.add(index, element);
            return;
        }
        requireAttached();
        session.call(nativeSession -> {
            nativeSession.execute(DocumentPatch.builder().insertArrayElement(location.path(), index, element.valueForPatch()).build());
            location.arrayInserted(index);
            return null;
        });
    }

    /** @param index zero-based index to remove */
    public void remove(int index) {
        if (value == null) {
            detached.remove(index);
            return;
        }
        requireAttached();
        session.call(nativeSession -> {
            nativeSession.execute(DocumentPatch.builder().removeArrayElement(location.path(), index).build());
            location.arrayRemoved(index);
            return null;
        });
    }

    private void requireAttached() {
        if (location == null || !location.isAttached()) {
            throw new IllegalStateException("The facade value is no longer attached to this document.");
        }
    }

    @Override
    public byte getType() {
        return ARRAY;
    }

    @Override
    net.zerocloud.pdf.PdfArray nativeValue() {
        if (value != null) {
            return value;
        }
        return (net.zerocloud.pdf.PdfArray) FacadeValues.convert(this);
    }
}
