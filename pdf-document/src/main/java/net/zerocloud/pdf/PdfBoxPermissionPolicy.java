package net.zerocloud.pdf;

/** Enforces Standard-handler user permissions before backend operations. */
final class PdfBoxPermissionPolicy {

    private PdfBoxPermissionPolicy() {
    }

    static void requireAssembly(PasswordSecurityInfo security)
            throws DocumentFailure {
        require(
                security,
                security.getEffectivePermissions().canAssembleDocument());
    }

    static void requireModification(PasswordSecurityInfo security)
            throws DocumentFailure {
        require(security, security.getEffectivePermissions().canModify());
    }

    static void requireAnnotationModification(PasswordSecurityInfo security)
            throws DocumentFailure {
        require(
                security,
                security.getEffectivePermissions().canModifyAnnotations());
    }

    static void requireExtraction(PasswordSecurityInfo security)
            throws DocumentFailure {
        if (security.getEncryptionScope() == PasswordEncryptionScope.EMBEDDED_FILES_ONLY) { return; }
        require(
                security,
                security.getEffectivePermissions().canExtractContent());
    }

    static void requireAttachmentExtraction(PasswordSecurityInfo security) throws DocumentFailure {
        require(security, security.getEffectivePermissions().canExtractContent());
    }

    static void requireMergeSource(PasswordSecurityInfo security)
            throws DocumentFailure {
        requireAttachmentExtraction(security);
    }

    private static void require(
            PasswordSecurityInfo security,
            boolean permitted) throws DocumentFailure {
        if (security.isPasswordProtected() && security.getCredentialAuthority() == CredentialAuthority.NONE) {
            throw PdfBoxWorkflowEngine.credentialFailure(true);
        }
        if (security.isPasswordProtected() && !permitted) {
            throw PdfBoxWorkflowEngine.versionFailure(
                    DocumentFailureCode.DOCUMENT_PERMISSION_DENIED,
                    "The Source credential does not authorize this document operation.");
        }
    }
}
