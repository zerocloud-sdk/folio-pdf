package net.zerocloud.pdf;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Maps opaque Object References without ever transporting a class name. */
final class WorkerReferenceRegistry {

    private final Object proxySessionIdentity;
    private final Map<Long, ObjectReference> references =
            new HashMap<Long, ObjectReference>();

    private WorkerReferenceRegistry(Object proxySessionIdentity) {
        this.proxySessionIdentity = proxySessionIdentity;
    }

    static WorkerReferenceRegistry forProxy(Object proxySessionIdentity) {
        return new WorkerReferenceRegistry(proxySessionIdentity);
    }

    static WorkerReferenceRegistry forWorker() {
        return new WorkerReferenceRegistry(null);
    }

    void write(
            WorkerCodecIO.Output output,
            ObjectReference reference) throws IOException, DocumentFailure {
        if (reference == null) {
            throw new NullPointerException("reference");
        }
        long identity = reference.getLocalIdentity();
        if (identity < 1L) {
            throw rejected();
        }
        if (proxySessionIdentity != null) {
            if (reference.getSessionIdentity() != proxySessionIdentity) {
                throw new DocumentFailure(
                        DocumentFailureCode.OBJECT_REFERENCE_OWNERSHIP_INVALID,
                        PdfBoxValueAdapter.CAPABILITY_ID,
                        "The Object Reference belongs to a different Document Session.");
            }
        } else {
            ObjectReference existing = references.get(Long.valueOf(identity));
            if (existing != null && existing != reference) {
                throw rejected();
            }
            references.put(Long.valueOf(identity), reference);
        }
        output.writeLong(identity);
    }

    ObjectReference read(WorkerCodecIO.Input input) throws DocumentFailure {
        long identity = input.readLong();
        if (identity < 1L) {
            throw rejected();
        }
        Long key = Long.valueOf(identity);
        if (proxySessionIdentity != null) {
            ObjectReference reference = references.get(key);
            if (reference == null) {
                reference = new ObjectReference(proxySessionIdentity, identity);
                references.put(key, reference);
            }
            return reference;
        }
        ObjectReference reference = references.get(key);
        if (reference == null) {
            throw rejected();
        }
        return reference;
    }

    private static DocumentFailure rejected() {
        return WorkerCodecIO.workerFailure(
                DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "A Worker Object Reference is invalid.");
    }
}
