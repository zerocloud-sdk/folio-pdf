package net.zerocloud.pdf;

import java.util.Objects;

/**
 * Request-scoped version and security policy for published PDF products.
 *
 * <p>PDF 1.7 is the workflow default. Version support is validated by the
 * workflow so a syntactically valid but unsupported publication choice
 * produces a stable {@link DocumentFailure} with publication receipts.</p>
 */
public final class PdfOutputPolicy {

    private final PdfVersion version;
    private final PasswordSecurityPolicy passwordSecurity;

    private PdfOutputPolicy(
            PdfVersion version,
            PasswordSecurityPolicy passwordSecurity) {
        this.version = Objects.requireNonNull(version, "version");
        this.passwordSecurity = passwordSecurity;
    }

    /**
     * Selects an explicit PDF version for every product of the request.
     *
     * @param version the requested serialized PDF version
     * @return an immutable output policy
     */
    public static PdfOutputPolicy version(PdfVersion version) {
        return new PdfOutputPolicy(version, null);
    }

    /** @return the requested serialized PDF version */
    public PdfVersion getVersion() {
        return version;
    }

    /**
     * Returns a copy that password-protects every published product.
     *
     * @param security the request-scoped password-security policy
     * @return a new immutable output policy
     */
    public PdfOutputPolicy withPasswordSecurity(
            PasswordSecurityPolicy security) {
        return new PdfOutputPolicy(
                version,
                Objects.requireNonNull(security, "security"));
    }

    PasswordSecurityPolicy getPasswordSecurity() {
        return passwordSecurity;
    }
}
