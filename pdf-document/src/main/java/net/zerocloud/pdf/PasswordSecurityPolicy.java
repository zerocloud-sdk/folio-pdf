package net.zerocloud.pdf;

import java.util.Objects;

/** Immutable password-security choices for published products. */
public final class PasswordSecurityPolicy {

    private final PasswordCredential ownerCredential;
    private final PasswordCredential userCredential;
    private final PasswordEncryptionAlgorithm algorithm;
    private final PasswordEncryptionScope encryptionScope;
    private final DocumentPermissions permissions;

    private PasswordSecurityPolicy(Builder builder) {
        ownerCredential = builder.ownerCredential;
        userCredential = builder.userCredential;
        algorithm = builder.algorithm;
        encryptionScope = builder.encryptionScope;
        permissions = builder.permissions;
    }

    /** Begins a policy whose secure algorithm default is AES-256. */
    public static Builder builder(
            PasswordCredential ownerCredential,
            PasswordCredential userCredential) {
        return new Builder(ownerCredential, userCredential);
    }

    public PasswordEncryptionAlgorithm getAlgorithm() { return algorithm; }
    public PasswordEncryptionScope getEncryptionScope() { return encryptionScope; }
    public DocumentPermissions getPermissions() { return permissions; }

    PasswordCredential getOwnerCredential() { return ownerCredential; }
    PasswordCredential getUserCredential() { return userCredential; }

    @Override
    public String toString() {
        return "PasswordSecurityPolicy[algorithm=" + algorithm
                + ", encryptionScope=" + encryptionScope
                + ", permissions=" + permissions + ", credentials=redacted]";
    }

    /** Builds a password-security policy. */
    public static final class Builder {
        private final PasswordCredential ownerCredential;
        private final PasswordCredential userCredential;
        private PasswordEncryptionAlgorithm algorithm =
                PasswordEncryptionAlgorithm.AES_256;
        private PasswordEncryptionScope encryptionScope =
                PasswordEncryptionScope.ALL_CONTENT;
        private DocumentPermissions permissions =
                DocumentPermissions.builder().build();

        private Builder(
                PasswordCredential ownerCredential,
                PasswordCredential userCredential) {
            this.ownerCredential = Objects.requireNonNull(
                    ownerCredential,
                    "ownerCredential");
            this.userCredential = Objects.requireNonNull(
                    userCredential,
                    "userCredential");
        }

        public Builder algorithm(PasswordEncryptionAlgorithm algorithm) {
            this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
            return this;
        }

        public Builder encryptionScope(PasswordEncryptionScope scope) {
            this.encryptionScope = Objects.requireNonNull(scope, "scope");
            return this;
        }

        public Builder permissions(DocumentPermissions permissions) {
            this.permissions = Objects.requireNonNull(permissions, "permissions");
            return this;
        }

        public PasswordSecurityPolicy build() {
            return new PasswordSecurityPolicy(this);
        }
    }
}
