package net.zerocloud.pdf.itext7.kernel.pdf;

import java.util.Arrays;
import java.util.Objects;
import net.zerocloud.pdf.PasswordCredential;

/** Caller-owned opening properties. Close to clear the retained password copy. @since 0.1.0 */
public final class ReaderProperties implements AutoCloseable {
    static { FacadeClasspathGuard.requireSingleEdition(); }

    private byte[] password;
    private PasswordCredential credential;
    private boolean closed;

    public ReaderProperties() { }

    /**
     * Copies raw legacy password bytes or UTF-8 AES-256 bytes. Null removes
     * the credential; an empty array explicitly supplies the empty password.
     * @param password caller-owned encoded bytes
     * @return these properties
     */
    public ReaderProperties setPassword(byte[] password) {
        requireOpen();
        clear();
        this.password = password == null ? null : password.clone();
        credential = null;
        return this;
    }

    /** Borrows a Native credential; closing these properties never destroys it.
     * @param credential caller-owned credential @return these properties */
    public ReaderProperties setCredential(PasswordCredential credential) {
        requireOpen();
        clear();
        this.credential = Objects.requireNonNull(credential, "credential");
        return this;
    }

    byte[] password() { requireOpen(); return password == null ? null : password.clone(); }
    PasswordCredential credential() { requireOpen(); return credential; }
    private void clear() { if (password != null) { Arrays.fill(password, (byte) 0); password = null; } }
    private void requireOpen() { if (closed) { throw new IllegalStateException("The reader properties are closed."); } }
    @Override public void close() { clear(); credential = null; closed = true; }
}
