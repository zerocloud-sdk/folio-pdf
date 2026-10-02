package net.zerocloud.pdf.itext7.kernel.pdf;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Opens a Source through the Native Interface for the mapped inspection
 * workflow.
 *
 * @since 0.1.0
 */
public final class PdfReader implements Closeable {

    static {
        FacadeClasspathGuard.requireSingleEdition();
    }

    private final FacadeSource source;
    private boolean claimed;
    private boolean closed;

    /**
     * Opens and validates a PDF filename.
     *
     * @param filename the PDF filename
     * @throws IOException if the Native Interface cannot open the source
     */
    public PdfReader(String filename) throws IOException {
        this(filename, new ReaderProperties());
    }

    /** Captures an authenticated Source and opening properties.
     * @param filename input filename @param properties caller-owned properties
     * @throws IOException if opening or authentication fails */
    public PdfReader(String filename, ReaderProperties properties) throws IOException {
        source = FacadeSource.capture(Paths.get(Objects.requireNonNull(filename, "filename"))
                .toAbsolutePath()
                .normalize(), Objects.requireNonNull(properties, "properties"));
    }

    /**
     * Reads and validates a caller-owned stream without closing it.
     * @param input PDF bytes owned by the caller
     * @throws IOException if the Source cannot be opened
     */
    public PdfReader(InputStream input) throws IOException {
        this(input, new ReaderProperties());
    }

    /** Authenticates a caller-owned stream without closing it.
     * @param input PDF bytes @param properties caller-owned opening properties
     * @throws IOException if opening or authentication fails */
    public PdfReader(InputStream input, ReaderProperties properties) throws IOException {
        source = FacadeSource.capture(Objects.requireNonNull(input, "input"), Objects.requireNonNull(properties, "properties"));
    }

    /** @return whether the Source has password protection */
    public boolean isEncrypted() { requireOpen(); return source.security.isPasswordProtected(); }

    /** @return true only for an unencrypted Source or proven owner authority */
    public boolean isOpenedWithFullPermission() {
        requireOpen();
        return !source.security.isPasswordProtected()
                || source.security.getCredentialAuthority() == net.zerocloud.pdf.CredentialAuthority.OWNER;
    }

    /** @return the unsigned 32-bit declared permission word, or zero when unencrypted */
    public long getPermissions() {
        requireOpen();
        return source.security.isPasswordProtected()
                ? source.security.getDeclaredUserPermissions().getStandardMask() & 0xffffffffL : 0L;
    }

    /** @return the algorithm selector plus the metadata or attachment scope selector,
     *     including attachment mode 25 for admitted RC4 input, or -1 when unencrypted */
    public int getCryptoMode() {
        requireOpen();
        if (!source.security.isPasswordProtected()) { return -1; }
        int mode;
        switch (source.security.getAlgorithm().get()) {
            case RC4_40: mode = EncryptionConstants.STANDARD_ENCRYPTION_40; break;
            case RC4_128: mode = EncryptionConstants.STANDARD_ENCRYPTION_128; break;
            case AES_128: mode = EncryptionConstants.ENCRYPTION_AES_128; break;
            default: mode = EncryptionConstants.ENCRYPTION_AES_256;
        }
        return mode | (source.security.getEncryptionScope() == net.zerocloud.pdf.PasswordEncryptionScope.EMBEDDED_FILES_ONLY
                ? EncryptionConstants.EMBEDDED_FILES_ONLY
                : source.security.getEncryptionScope() == net.zerocloud.pdf.PasswordEncryptionScope.ALL_EXCEPT_METADATA
                        ? EncryptionConstants.DO_NOT_ENCRYPT_METADATA : 0);
    }

    private void requireOpen() {
        if (closed) { throw new IllegalStateException("The facade reader is closed."); }
    }

    int getPageCount() {
        return source.pageCount;
    }

    FacadeSource takeSource() {
        FacadeSource available = availableSource();
        claimed = true;
        return available;
    }

    FacadeSource availableSource() {
        if (closed || claimed) {
            throw new IllegalStateException("The facade reader is closed or already owned by a document.");
        }
        return source;
    }

    /**
     * Releases an unclaimed snapshot. A Document owns a claimed snapshot until
     * that Document closes. The original Path is closed before construction returns.
     *
     * @throws IOException retained for the mapped reference call shape
     */
    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            if (!claimed) {
                source.close();
            }
        }
    }
}
