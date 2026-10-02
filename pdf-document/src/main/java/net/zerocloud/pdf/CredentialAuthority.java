package net.zerocloud.pdf;

/** Effective authority established while opening a document. */
public enum CredentialAuthority {
    /** No credential authority; the Source is clear or only its unopened attachments are protected. */
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
