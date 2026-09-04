package net.zerocloud.pdf;

/**
 * Stable, opaque identity for one indirect PDF object within a Document
 * Session.
 *
 * <p>References from different Sessions are never equal. A reference remains
 * suitable for equality checks after its Session ends, but can be inspected or
 * patched only by the Session that issued it.</p>
 *
 * @since 0.1.0
 */
public final class ObjectReference {

    private final Object sessionIdentity;
    private final long localIdentity;

    ObjectReference(Object sessionIdentity, long localIdentity) {
        this.sessionIdentity = sessionIdentity;
        this.localIdentity = localIdentity;
    }

    Object getSessionIdentity() {
        return sessionIdentity;
    }

    long getLocalIdentity() {
        return localIdentity;
    }

    @Override
    public boolean equals(Object candidate) {
        if (!(candidate instanceof ObjectReference)) {
            return false;
        }
        ObjectReference other = (ObjectReference) candidate;
        return sessionIdentity == other.sessionIdentity
                && localIdentity == other.localIdentity;
    }

    @Override
    public int hashCode() {
        return 31 * System.identityHashCode(sessionIdentity)
                + (int) (localIdentity ^ (localIdentity >>> 32));
    }

    @Override
    public String toString() {
        return "ObjectReference[" + localIdentity + "]";
    }
}
