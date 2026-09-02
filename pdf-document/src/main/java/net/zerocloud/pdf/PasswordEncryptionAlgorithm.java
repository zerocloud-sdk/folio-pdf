package net.zerocloud.pdf;

/** Password-encryption algorithms recognized by the Native Interface. */
public enum PasswordEncryptionAlgorithm {
    /** AES with a 256-bit file-encryption key and security-handler revision 6. */
    AES_256,
    /** Legacy AES with a 128-bit file-encryption key. */
    AES_128,
    /** Obsolete RC4 with a 128-bit file-encryption key. */
    RC4_128,
    /** Obsolete RC4 with a 40-bit file-encryption key. */
    RC4_40
}
