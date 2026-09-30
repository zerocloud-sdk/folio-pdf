package net.zerocloud.pdf.itext7.kernel.pdf;

import java.util.Arrays;
import java.util.Objects;
import net.zerocloud.pdf.DocumentPermissions;
import net.zerocloud.pdf.LegacySecurityMode;
import net.zerocloud.pdf.PasswordCredential;
import net.zerocloud.pdf.PasswordEncryptionAlgorithm;
import net.zerocloud.pdf.PasswordEncryptionScope;
import net.zerocloud.pdf.PasswordSecurityPolicy;
import net.zerocloud.pdf.PdfOutputPolicy;

/** Caller-owned output choices, copied by a Writer. @since 0.1.0 */
public final class WriterProperties implements AutoCloseable {
    static { FacadeClasspathGuard.requireSingleEdition(); }

    private PdfVersion version;
    private byte[] owner;
    private byte[] user;
    private int algorithm = EncryptionConstants.ENCRYPTION_AES_256;
    private int permissions;
    private boolean encrypted;
    private LegacySecurityMode legacy;
    private boolean closed;

    public WriterProperties() { }

    /** @param version explicit output version @return these properties */
    public WriterProperties setPdfVersion(PdfVersion version) {
        requireOpen(); this.version = Objects.requireNonNull(version, "version"); return this;
    }

    /**
     * Copies both encoded passwords. Null/empty user means an explicitly empty
     * opening password; null/empty owner generates an independent random owner
     * for each Native request. Legacy bytes are preserved exactly; AES-256 uses
     * strict UTF-8 and RFC 4013 preparation. The caller arrays remain caller-owned.
     * RC4-40 cannot represent clear metadata; combining it with bit 8 is
     * rejected instead of silently discarding the explicit scope choice.
     * @param userPassword encoded user password
     * @param ownerPassword encoded owner password
     * @param permissions permitted operations, combined with bitwise OR
     * @param encryptionAlgorithm an algorithm constant, optionally combined
     *     with {@link EncryptionConstants#DO_NOT_ENCRYPT_METADATA}
     * @return these properties
     */
    public WriterProperties setStandardEncryption(byte[] userPassword, byte[] ownerPassword,
            int permissions, int encryptionAlgorithm) {
        requireOpen();
        if ((encryptionAlgorithm & ~11) != 0 || encryptionAlgorithm == EncryptionConstants.DO_NOT_ENCRYPT_METADATA) {
            throw new IllegalArgumentException("The encryption selector is unsupported.");
        }
        clear();
        user = userPassword == null ? new byte[0] : userPassword.clone();
        owner = ownerPassword == null ? new byte[0] : ownerPassword.clone();
        this.permissions = permissions;
        algorithm = encryptionAlgorithm;
        encrypted = true;
        return this;
    }

    /** Explicit request-scoped opt-in; selecting it never changes the algorithm.
     * @param mode legacy output selection @return these properties */
    public WriterProperties setLegacySecurityMode(LegacySecurityMode mode) {
        requireOpen(); legacy = Objects.requireNonNull(mode, "mode"); return this;
    }

    WriterProperties copy() {
        requireOpen();
        WriterProperties copy = new WriterProperties();
        try {
            copy.version = version; copy.legacy = legacy;
            if (encrypted) { copy.setStandardEncryption(user, owner, permissions, algorithm); }
            return copy;
        } catch (RuntimeException | Error failure) { copy.close(); throw failure; }
    }

    boolean sameChoices(WriterProperties other) {
        return Objects.equals(version, other.version) && legacy == other.legacy && encrypted == other.encrypted
                && algorithm == other.algorithm && permissions == other.permissions
                && Arrays.equals(owner, other.owner) && Arrays.equals(user, other.user);
    }

    LegacySecurityMode legacy() { requireOpen(); return legacy; }

    Output output(PdfOutputPolicy explicit) {
        requireOpen();
        if (explicit != null && version != null && explicit.getVersion() != version.nativeVersion) {
            throw new IllegalArgumentException("The output version declarations conflict.");
        }
        PdfOutputPolicy selected = explicit != null ? explicit : version != null
                ? PdfOutputPolicy.version(version.nativeVersion) : null;
        if (!encrypted) { return new Output(selected, null, null); }
        int selectedAlgorithm = algorithm & 3;
        PasswordCredential ownerCredential = FacadePasswords.credential(owner, selectedAlgorithm == 3);
        PasswordCredential userCredential = null;
        try {
            userCredential = FacadePasswords.credential(user, selectedAlgorithm == 3);
            DocumentPermissions mask = DocumentPermissions.builder()
                    .allowPrinting((permissions & 4) != 0).allowModification((permissions & 8) != 0)
                    .allowContentExtraction((permissions & 16) != 0).allowAnnotationModification((permissions & 32) != 0)
                    .allowFormFilling((permissions & 256) != 0).allowAccessibilityExtraction((permissions & 512) != 0)
                    .allowDocumentAssembly((permissions & 1024) != 0).allowFaithfulPrinting((permissions & 2048) != 0).build();
            PasswordEncryptionAlgorithm[] choices = {PasswordEncryptionAlgorithm.RC4_40,
                PasswordEncryptionAlgorithm.RC4_128, PasswordEncryptionAlgorithm.AES_128, PasswordEncryptionAlgorithm.AES_256};
            PasswordSecurityPolicy security = PasswordSecurityPolicy.builder(ownerCredential, userCredential)
                    .algorithm(choices[selectedAlgorithm]).permissions(mask)
                    .encryptionScope((algorithm & EncryptionConstants.DO_NOT_ENCRYPT_METADATA) != 0
                            ? PasswordEncryptionScope.ALL_EXCEPT_METADATA : PasswordEncryptionScope.ALL_CONTENT).build();
            if (selected == null) { selected = PdfOutputPolicy.version(net.zerocloud.pdf.PdfVersion.PDF_1_7); }
            return new Output(selected.withPasswordSecurity(security), ownerCredential, userCredential);
        } catch (RuntimeException | Error failure) {
            ownerCredential.close(); if (userCredential != null) { userCredential.close(); } throw failure;
        }
    }

    private void clear() {
        if (owner != null) { Arrays.fill(owner, (byte) 0); owner = null; }
        if (user != null) { Arrays.fill(user, (byte) 0); user = null; }
    }
    private void requireOpen() { if (closed) { throw new IllegalStateException("The writer properties are closed."); } }
    @Override public void close() { clear(); closed = true; }

    static final class Output implements AutoCloseable {
        final PdfOutputPolicy policy;
        private final PasswordCredential owner;
        private final PasswordCredential user;
        Output(PdfOutputPolicy policy, PasswordCredential owner, PasswordCredential user) {
            this.policy = policy; this.owner = owner; this.user = user;
        }
        @Override public void close() {
            if (owner != null) { owner.close(); }
            if (user != null) { user.close(); }
        }
    }
}
