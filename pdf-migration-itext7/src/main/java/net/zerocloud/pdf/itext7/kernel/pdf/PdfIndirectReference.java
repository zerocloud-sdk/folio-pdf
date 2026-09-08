package net.zerocloud.pdf.itext7.kernel.pdf;

import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfValuePath;
import net.zerocloud.pdf.query.InspectObject;

/** Opaque, Session-scoped identity of an indirect PDF object. @since 0.1.0 */
public final class PdfIndirectReference extends PdfObject {
    private static final PdfInspectionLimits INSPECTION_LIMITS = PdfInspectionLimits.of(100000, 64L << 20);
    private final FacadeSession session;
    private final ObjectReference reference;
    final FacadeLocation location;

    PdfIndirectReference(FacadeSession session, ObjectReference reference) {
        this.session = session;
        this.reference = reference;
        this.location = new FacadeLocation(PdfValuePath.root(reference));
    }

    /** @return the referred value, inspected within its owning Session */
    public PdfObject getRefersTo() {
        return session.call(this::inspect);
    }

    PdfObject inspect(DocumentSession nativeSession) throws DocumentFailure {
        net.zerocloud.pdf.PdfValue inspected = nativeSession.query(InspectObject.version1(reference, INSPECTION_LIMITS));
        if (inspected instanceof net.zerocloud.pdf.PdfIndirectReference) {
            return session.referenceFor(((net.zerocloud.pdf.PdfIndirectReference) inspected).getReference());
        }
        PdfObject value = FacadeValues.wrap(inspected, session, location, true, nativeSession);
        value.attachReference(this);
        return value;
    }

    @Override
    public byte getType() {
        return INDIRECT_REFERENCE;
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof PdfIndirectReference
                && reference.equals(((PdfIndirectReference) candidate).reference);
    }

    @Override
    public int hashCode() {
        return reference.hashCode();
    }

    @Override
    net.zerocloud.pdf.PdfIndirectReference nativeValue() {
        return net.zerocloud.pdf.PdfIndirectReference.of(reference);
    }
}
