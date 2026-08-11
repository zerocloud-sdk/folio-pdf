package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An immutable PDF array or a bounded lazy Session view of one.
 *
 * @since 0.1.0
 */
public final class PdfArray implements PdfValue {

    private final PdfArrayAccess access;

    PdfArray(PdfArrayAccess access) {
        this.access = Objects.requireNonNull(access, "access");
    }

    /**
     * Creates an immutable detached PDF array.
     *
     * @param values the ordered values
     * @return a detached PDF array
     */
    public static PdfArray of(PdfValue... values) {
        Objects.requireNonNull(values, "values");
        List<PdfValue> copy = new ArrayList<PdfValue>(values.length);
        for (PdfValue value : values) {
            copy.add(Objects.requireNonNull(value, "value"));
        }
        return new PdfArray(new DetachedArrayAccess(copy));
    }

    /**
     * Returns the number of array elements.
     *
     * @return the array size
     * @throws DocumentFailure if the lazy view is no longer usable
     */
    public int size() throws DocumentFailure {
        return access.size();
    }

    /**
     * Obtains one array element lazily.
     *
     * @param index the zero-based element index
     * @return the element value
     * @throws DocumentFailure if the lazy view cannot be traversed
     */
    public PdfValue get(int index) throws DocumentFailure {
        return access.get(index);
    }

    @Override
    public PdfValueKind getKind() {
        return PdfValueKind.ARRAY;
    }

    private static final class DetachedArrayAccess implements PdfArrayAccess {

        private final List<PdfValue> values;

        DetachedArrayAccess(List<PdfValue> values) {
            this.values = Collections.unmodifiableList(
                    new ArrayList<PdfValue>(values));
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public PdfValue get(int index) {
            return values.get(index);
        }
    }
}

interface PdfArrayAccess {

    int size() throws DocumentFailure;

    PdfValue get(int index) throws DocumentFailure;
}
