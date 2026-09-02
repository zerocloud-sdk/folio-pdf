package net.zerocloud.pdf;

/** Which serialized PDF data a password-security policy encrypts. */
public enum PasswordEncryptionScope {
    /** Encrypt strings, streams, embedded files, and metadata. */
    ALL_CONTENT,
    /** Encrypt document content but leave metadata plaintext. */
    ALL_EXCEPT_METADATA,
    /** Encrypt only embedded-file streams. */
    EMBEDDED_FILES_ONLY
}
