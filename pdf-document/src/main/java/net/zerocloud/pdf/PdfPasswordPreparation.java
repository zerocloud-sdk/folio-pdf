package net.zerocloud.pdf;

import com.ibm.icu.text.StringPrep;
import com.ibm.icu.text.StringPrepParseException;

/** RFC 4013 preparation through the already pinned Unicode implementation. */
final class PdfPasswordPreparation {
    private static final StringPrep SASL = StringPrep.getInstance(StringPrep.RFC4013_SASLPREP);

    private PdfPasswordPreparation() {
    }

    static String saslPrepStored(String password) {
        return prepare(password, StringPrep.DEFAULT);
    }

    static String saslPrepQuery(String password) {
        return prepare(password, StringPrep.ALLOW_UNASSIGNED);
    }

    private static String prepare(String password, int options) {
        try {
            return SASL.prepare(password, options);
        } catch (StringPrepParseException invalid) {
            // The Unicode exception retains credential text. Never attach it.
            throw new IllegalArgumentException("The credential cannot be prepared for password security.");
        }
    }
}
