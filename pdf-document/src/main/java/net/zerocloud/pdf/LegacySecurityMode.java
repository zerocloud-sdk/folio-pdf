package net.zerocloud.pdf;

/**
 * Explicit request-scoped authorization for obsolete password-security output.
 *
 * <p>This mode never changes the AES-256 default and does not enable public-key
 * encryption, signing, or any FIPS claim.</p>
 */
public enum LegacySecurityMode {
    /** Allows an explicitly selected AES-128 or RC4 output algorithm. */
    ALLOW_OBSOLETE_PASSWORD_ENCRYPTION
}
