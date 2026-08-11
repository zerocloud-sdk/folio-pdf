package net.zerocloud.pdf.query;

import java.util.Objects;
import net.zerocloud.pdf.DocumentQuery;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfValue;

/**
 * Opens one bounded, versioned PDF Value inspection at an Object Reference.
 * Container and stream results are lazy views valid only while the evaluating
 * Document Session remains active.
 *
 * @since 0.1.0
 */
public final class InspectObject implements DocumentQuery<PdfValue> {

    /** The currently supported query representation version. */
    public static final int VERSION_1 = 1;

    private final ObjectReference reference;
    private final PdfInspectionLimits limits;

    private InspectObject(
            ObjectReference reference,
            PdfInspectionLimits limits) {
        this.reference = Objects.requireNonNull(reference, "reference");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /**
     * Creates a version-1 bounded inspection query.
     *
     * @param reference the Session-owned object to inspect
     * @param limits the cumulative lazy-view limits
     * @return the immutable query
     */
    public static InspectObject version1(
            ObjectReference reference,
            PdfInspectionLimits limits) {
        return new InspectObject(reference, limits);
    }

    /**
     * Returns the query representation version.
     *
     * @return {@link #VERSION_1}
     */
    public int getVersion() {
        return VERSION_1;
    }

    /**
     * Returns the object to inspect.
     *
     * @return the opaque Object Reference
     */
    public ObjectReference getReference() {
        return reference;
    }

    /**
     * Returns the declared lazy-view limits.
     *
     * @return the inspection limits
     */
    public PdfInspectionLimits getLimits() {
        return limits;
    }
}
