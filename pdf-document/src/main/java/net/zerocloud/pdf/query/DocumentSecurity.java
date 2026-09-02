package net.zerocloud.pdf.query;

import net.zerocloud.pdf.DocumentQuery;
import net.zerocloud.pdf.PasswordSecurityInfo;

/** Reports detached password-security state for the current document. */
public final class DocumentSecurity implements DocumentQuery<PasswordSecurityInfo> {
    /** The immutable security query. */
    public static final DocumentSecurity INSTANCE = new DocumentSecurity();
    private DocumentSecurity() { }
}
