package net.zerocloud.pdf.itext7.kernel.pdf;

/** Baseline Standard password-security selectors and permission bits. @since 0.1.0 */
public final class EncryptionConstants {
    static { FacadeClasspathGuard.requireSingleEdition(); }

    public static final int STANDARD_ENCRYPTION_40 = 0;
    public static final int STANDARD_ENCRYPTION_128 = 1;
    public static final int ENCRYPTION_AES_128 = 2;
    public static final int ENCRYPTION_AES_256 = 3;
    /** Explicitly leaves document-level XMP clear with RC4-128, AES-128 or AES-256. */
    public static final int DO_NOT_ENCRYPT_METADATA = 8;
    /** Protects embedded-file payloads with AES while ordinary document data stays clear. */
    public static final int EMBEDDED_FILES_ONLY = 24;
    /** Folio spelling for the attachment-only scope; equals the Reference Suite selector. */
    public static final int ONLY_EMBEDDED_FILES = EMBEDDED_FILES_ONLY;
    public static final int ALLOW_DEGRADED_PRINTING = 4;
    public static final int ALLOW_PRINTING = 2052;
    public static final int ALLOW_MODIFY_CONTENTS = 8;
    public static final int ALLOW_COPY = 16;
    public static final int ALLOW_MODIFY_ANNOTATIONS = 32;
    public static final int ALLOW_FILL_IN = 256;
    public static final int ALLOW_SCREENREADERS = 512;
    public static final int ALLOW_ASSEMBLY = 1024;

    private EncryptionConstants() {
    }
}
