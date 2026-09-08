package net.zerocloud.pdf.itext7.kernel.pdf;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;

/** Converts only project-owned Native PDF values into mapped objects. */
final class FacadeValues {
    private FacadeValues() {
    }

    static PdfObject direct(PdfObject value, boolean asDirect) {
        return asDirect && value instanceof PdfIndirectReference ? ((PdfIndirectReference) value).getRefersTo() : value;
    }

    static PdfValue convert(PdfObject root) {
        if (!isDetachedContainer(root)) {
            return directOrReference(root);
        }
        Map<PdfObject, PdfValue> converted = new IdentityHashMap<PdfObject, PdfValue>();
        Map<PdfObject, Boolean> ancestors = new IdentityHashMap<PdfObject, Boolean>();
        Deque<Conversion> pending = new ArrayDeque<Conversion>();
        ancestors.put(root, Boolean.TRUE);
        pending.push(new Conversion(root));
        while (!pending.isEmpty()) {
            Conversion current = pending.peek();
            if (current.children.hasNext()) {
                PdfObject child = current.children.next();
                if (converted.containsKey(child)) {
                    continue;
                }
                if (ancestors.containsKey(child)) {
                    throw new PdfException("PATCH_CYCLE_REJECTED: The Document Patch would introduce a cyclic PDF value.", null);
                }
                if (isDetachedContainer(child)) {
                    ancestors.put(child, Boolean.TRUE);
                    pending.push(new Conversion(child));
                } else {
                    converted.put(child, directOrReference(child));
                }
            } else {
                converted.put(current.value, current.finish(converted));
                ancestors.remove(current.value);
                pending.pop();
            }
        }
        return converted.get(root);
    }

    private static boolean isDetachedContainer(PdfObject value) {
        return value.getIndirectReference() == null && (value instanceof PdfArray && ((PdfArray) value).detached != null
                || value instanceof PdfDictionary && ((PdfDictionary) value).detached != null);
    }

    private static PdfValue directOrReference(PdfObject value) {
        return value.getIndirectReference() == null ? value.nativeValue() : value.getIndirectReference().nativeValue();
    }

    private static final class Conversion {
        private final PdfObject value;
        private final Iterator<PdfObject> children;

        Conversion(PdfObject value) {
            this.value = value;
            children = value instanceof PdfArray ? ((PdfArray) value).detached.iterator()
                    : ((PdfDictionary) value).detached.values().iterator();
        }

        PdfValue finish(Map<PdfObject, PdfValue> converted) {
            if (value instanceof PdfArray) {
                PdfArray array = (PdfArray) value;
                PdfValue[] entries = new PdfValue[array.detached.size()];
                for (int index = 0; index < entries.length; index++) {
                    entries[index] = converted.get(array.detached.get(index));
                }
                return net.zerocloud.pdf.PdfArray.of(entries);
            }
            net.zerocloud.pdf.PdfDictionary.Builder builder = net.zerocloud.pdf.PdfDictionary.builder();
            for (Map.Entry<PdfName, PdfObject> entry : ((PdfDictionary) value).detached.entrySet()) {
                builder.put(entry.getKey().nativeValue(), converted.get(entry.getValue()));
            }
            net.zerocloud.pdf.PdfDictionary dictionary = builder.build();
            return value instanceof PdfStream ? ((PdfStream) value).withDictionary(dictionary) : dictionary;
        }
    }

    static PdfObject wrap(PdfValue child, FacadeSession session, FacadeLocation childLocation,
            boolean asDirect, DocumentSession nativeSession) throws DocumentFailure {
        if (child == null) {
            return null;
        }
        if (child instanceof net.zerocloud.pdf.PdfNull) {
            return new PdfNull();
        }
        if (child instanceof net.zerocloud.pdf.PdfBoolean) {
            return new PdfBoolean(((net.zerocloud.pdf.PdfBoolean) child).booleanValue());
        }
        if (child instanceof net.zerocloud.pdf.PdfString) {
            return new PdfString((net.zerocloud.pdf.PdfString) child);
        }
        if (child instanceof net.zerocloud.pdf.PdfNumber) {
            return new PdfNumber((net.zerocloud.pdf.PdfNumber) child, childLocation == null ? null : session, childLocation);
        }
        if (child instanceof net.zerocloud.pdf.PdfName) {
            return new PdfName(((net.zerocloud.pdf.PdfName) child).getValue());
        }
        if (child instanceof net.zerocloud.pdf.PdfDictionary) {
            return new PdfDictionary(session, childLocation, (net.zerocloud.pdf.PdfDictionary) child);
        }
        if (child instanceof net.zerocloud.pdf.PdfArray) {
            return new PdfArray(session, childLocation, (net.zerocloud.pdf.PdfArray) child);
        }
        if (child instanceof net.zerocloud.pdf.PdfIndirectReference) {
            PdfIndirectReference reference = session.referenceFor(((net.zerocloud.pdf.PdfIndirectReference) child).getReference());
            return asDirect ? reference.inspect(nativeSession) : reference;
        }
        if (child instanceof net.zerocloud.pdf.PdfStream) {
            net.zerocloud.pdf.PdfStream source = (net.zerocloud.pdf.PdfStream) child;
            PdfIndirectReference reference = source.getReference().isPresent()
                    ? session.referenceFor(source.getReference().get()) : null;
            if (!asDirect && reference != null) {
                return reference;
            }
            PdfStream stream = new PdfStream(session, reference == null ? childLocation : reference.location, source);
            if (reference != null) {
                stream.attachReference(reference);
            }
            return stream;
        }
        throw new IllegalStateException("The PDF Value kind is unavailable.");
    }
}
