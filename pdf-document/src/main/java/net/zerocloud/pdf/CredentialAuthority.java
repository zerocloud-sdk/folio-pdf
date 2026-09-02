package net.zerocloud.pdf;

/** Effective authority established while opening a document. */
public enum CredentialAuthority {
    /** The document was not opened through password security. */
    NONE,
    /** The supplied credential is restricted by the declared user permissions. */
    USER,
    /** The supplied credential has unrestricted owner authority. */
    OWNER,
    /**
     * The credential has unrestricted effective permissions, but independent
     * owner authority was not proven.
     */
    UNRESTRICTED
}
