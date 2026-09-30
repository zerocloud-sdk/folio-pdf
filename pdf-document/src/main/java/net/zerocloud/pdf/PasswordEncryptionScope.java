package net.zerocloud.pdf;

/** Which serialized PDF data a password-security policy encrypts. */
public enum PasswordEncryptionScope {
    /** Encrypt strings, streams, embedded files, and metadata. */
    ALL_CONTENT,
    /** Encrypt ordinary strings and streams; leave catalog document-metadata stream bytes clear.
     * Document Info strings and component metadata remain protected. */
    ALL_EXCEPT_METADATA,
    /** Encrypt only embedded-file streams. */
    EMBEDDED_FILES_ONLY
}
