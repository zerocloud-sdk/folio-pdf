package net.zerocloud.pdf;

import java.util.Optional;

/** Detached password-security state for the opened document. */
public final class PasswordSecurityInfo {

    private final PasswordEncryptionAlgorithm algorithm;
    private final int securityHandlerRevision;
    private final PasswordEncryptionScope encryptionScope;
    private final DocumentPermissions declaredUserPermissions;
    private final DocumentPermissions effectivePermissions;
    private final CredentialAuthority credentialAuthority;

    PasswordSecurityInfo(
            PasswordEncryptionAlgorithm algorithm,
            int securityHandlerRevision,
            PasswordEncryptionScope encryptionScope,
            DocumentPermissions declaredUserPermissions,
            DocumentPermissions effectivePermissions,
            CredentialAuthority credentialAuthority) {
        this.algorithm = algorithm;
        this.securityHandlerRevision = securityHandlerRevision;
        this.encryptionScope = encryptionScope;
        this.declaredUserPermissions = declaredUserPermissions;
        this.effectivePermissions = effectivePermissions;
        this.credentialAuthority = credentialAuthority;
    }

    public boolean isPasswordProtected() { return algorithm != null; }
    public Optional<PasswordEncryptionAlgorithm> getAlgorithm() {
        return Optional.ofNullable(algorithm);
    }
    /** @return the Standard security-handler {@code /R} value, or zero */
    public int getSecurityHandlerRevision() { return securityHandlerRevision; }
    public PasswordEncryptionScope getEncryptionScope() { return encryptionScope; }
    public DocumentPermissions getDeclaredUserPermissions() {
        return declaredUserPermissions;
    }
    public DocumentPermissions getEffectivePermissions() { return effectivePermissions; }
    public CredentialAuthority getCredentialAuthority() { return credentialAuthority; }
}
