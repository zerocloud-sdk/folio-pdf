package net.zerocloud.pdf;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.DecryptionMaterial;
import org.apache.pdfbox.pdmodel.encryption.PDCryptFilterDictionary;
import org.apache.pdfbox.pdmodel.encryption.PDEncryption;
import org.apache.pdfbox.pdmodel.encryption.ProtectionPolicy;
import org.apache.pdfbox.pdmodel.encryption.SecurityHandler;
import org.apache.pdfbox.pdmodel.encryption.StandardSecurityHandler;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

/** PDFBox-specific password-security mechanics kept behind the Native Interface. */
final class PdfBoxPasswordSecurity {

    private static final COSName EFFECTIVE_FILE_FILTER =
            COSName.getPDFName("EFF");
    private static final COSName RC4_CRYPT_FILTER = COSName.getPDFName("V2");

    private PdfBoxPasswordSecurity() {
    }

    static PasswordSecurityInfo inspect(PDDocument document)
            throws DocumentFailure {
        return inspect(document, (char[]) null);
    }

    static PasswordSecurityInfo inspect(
            PDDocument document,
            PasswordCredential credential) throws DocumentFailure {
        if (credential == null) {
            return inspect(document, (char[]) null);
        }
        char[] characters;
        try {
            characters = credential.copyForExecution();
        } catch (IllegalStateException destroyed) {
            throw PdfBoxWorkflowEngine.versionFailure(
                    DocumentFailureCode.CREDENTIAL_DESTROYED,
                    "A password credential was destroyed before execution.");
        }
        try {
            return inspect(document, characters);
        } finally {
            Arrays.fill(characters, '\0');
        }
    }

    static PasswordSecurityInfo inspect(
            PDDocument document,
            char[] credentialCharacters) throws DocumentFailure {
        if (!document.isEncrypted()) {
            DocumentPermissions unrestricted =
                    DocumentPermissions.unrestricted();
            return new PasswordSecurityInfo(
                    null,
                    0,
                    PasswordEncryptionScope.ALL_CONTENT,
                    unrestricted,
                    unrestricted,
                    CredentialAuthority.NONE);
        }

        try {
            PDEncryption encryption = document.getEncryption();
            if (encryption == null
                    || !PDEncryption.DEFAULT_NAME.equals(
                            encryption.getFilter())) {
                throw unsupported(
                        "Only the Standard password-security handler is supported.");
            }
            validateStandardStructure(encryption);
            PasswordEncryptionAlgorithm algorithm = algorithm(encryption);
            PasswordEncryptionScope scope = scope(encryption);
            DocumentPermissions declared =
                    DocumentPermissions.fromStandardMask(
                            encryption.getPermissions());
            AccessPermission current = document.getCurrentAccessPermission();
            CredentialAuthority authority;
            if (!current.isOwnerPermission()) {
                authority = CredentialAuthority.USER;
            } else if (declared.equals(DocumentPermissions.unrestricted())) {
                authority = authenticatesOwner(
                        document,
                        encryption,
                        credentialCharacters)
                                ? CredentialAuthority.OWNER
                                : CredentialAuthority.UNRESTRICTED;
            } else {
                authority = CredentialAuthority.OWNER;
            }
            DocumentPermissions effective = current.isOwnerPermission()
                    ? DocumentPermissions.unrestricted()
                    : DocumentPermissions.fromStandardMask(
                            current.getPermissionBytes());
            return new PasswordSecurityInfo(
                    algorithm,
                    encryption.getRevision(),
                    scope,
                    declared,
                    effective,
                    authority);
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException | GeneralSecurityException
                | RuntimeException backendFailure) {
            throw unsupported(
                    "The password-security dictionary is not supported.");
        }
    }

    private static boolean authenticatesOwner(
            PDDocument document,
            PDEncryption encryption,
            char[] credentialCharacters) throws IOException {
        if (credentialCharacters == null) {
            return false;
        }
        COSArray identifiers = document.getDocument().getDocumentID();
        byte[] identifier = new byte[0];
        if (identifiers != null && identifiers.size() > 0
                && identifiers.getObject(0)
                        instanceof org.apache.pdfbox.cos.COSString) {
            identifier = ((org.apache.pdfbox.cos.COSString)
                    identifiers.getObject(0)).getBytes();
        }
        int keyLengthBytes = encryption.getVersion() == 1
                ? 5 : encryption.getLength() / 8;
        // PDFBox's public predicate exactly mirrors its owner branch for the
        // supported printable-ASCII output contract. A false result remains
        // fail-closed as UNRESTRICTED for other input forms.
        return new StandardSecurityHandler().isOwnerPassword(
                new String(credentialCharacters),
                encryption.getUserKey(),
                encryption.getOwnerKey(),
                encryption.getPermissions(),
                identifier,
                encryption.getRevision(),
                keyLengthBytes,
                encryption.isEncryptMetaData());
    }

    private static void validateStandardStructure(PDEncryption encryption)
            throws IOException, GeneralSecurityException, DocumentFailure {
        if (encryption.getSubFilter() != null) {
            throw unsupported(
                    "Standard password security does not support a SubFilter.");
        }
        COSBase rawPermissions = encryption.getCOSObject()
                .getDictionaryObject(COSName.P);
        if (!(rawPermissions instanceof COSInteger)) {
            throw unsupported(
                    "The password-security permission word is malformed.");
        }
        int permissions = encryption.getPermissions();
        if ((permissions & 0xfffff0c3) != 0xfffff0c0) {
            throw unsupported(
                    "The password-security permission word has invalid reserved bits.");
        }

        int version = encryption.getVersion();
        int revision = encryption.getRevision();
        int length = encryption.getLength();
        if (!((version == 1 && (revision == 2 || revision == 3)
                        && (length == 0 || length == 40))
                || (version == 2 && revision == 3 && length == 128)
                || (version == 4 && revision == 4 && length == 128)
                || (version == 5 && revision == 6 && length == 256))) {
            throw unsupported(
                    "The Standard password-security revision is not supported.");
        }

        int expectedPasswordEntryLength = revision >= 5 ? 48 : 32;
        if (length(encryption.getOwnerKey()) != expectedPasswordEntryLength
                || length(encryption.getUserKey())
                        != expectedPasswordEntryLength) {
            throw unsupported(
                    "The password-security authentication entries are malformed.");
        }
        if (revision >= 5
                && (length(encryption.getOwnerEncryptionKey()) != 32
                        || length(encryption.getUserEncryptionKey()) != 32
                        || length(encryption.getPerms()) != 16)) {
            throw unsupported(
                    "The AES-256 authentication entries are malformed.");
        }

        if (version >= 4) {
            validateCryptFilters(encryption, version);
        }
        if (revision == 6) {
            validateRevisionSixPermissions(encryption);
        }
    }

    private static int length(byte[] value) {
        return value == null ? -1 : value.length;
    }

    private static void validateCryptFilters(
            PDEncryption encryption,
            int version) throws DocumentFailure {
        if (!COSName.STD_CF.equals(encryption.getStreamFilterName())
                || !COSName.STD_CF.equals(encryption.getStringFilterName())) {
            throw unsupported(
                    "Only whole-document Standard crypt filters are supported.");
        }
        COSBase embedded = encryption.getCOSObject()
                .getDictionaryObject(EFFECTIVE_FILE_FILTER);
        if (embedded != null && !COSName.STD_CF.equals(embedded)) {
            throw unsupported(
                    "Attachment-only password encryption is unsupported.");
        }
        PDCryptFilterDictionary filter =
                encryption.getStdCryptFilterDictionary();
        if (filter == null) {
            throw unsupported(
                    "The Standard crypt-filter dictionary is missing.");
        }
        COSName method = filter.getCryptFilterMethod();
        if (version == 4
                && !COSName.AESV2.equals(method)
                && !RC4_CRYPT_FILTER.equals(method)) {
            throw unsupported(
                    "The Standard crypt-filter method is unsupported.");
        }
        if (version == 5 && !COSName.AESV3.equals(method)) {
            throw unsupported(
                    "The Standard crypt-filter method is unsupported.");
        }
        COSName authEvent = filter.getCOSObject().getCOSName(
                COSName.getPDFName("AuthEvent"));
        if (authEvent != null
                && !COSName.getPDFName("DocOpen").equals(authEvent)) {
            throw unsupported(
                    "The Standard crypt-filter AuthEvent is unsupported.");
        }
        COSBase rawLength = filter.getCOSObject()
                .getDictionaryObject(COSName.LENGTH);
        int expectedBytes = version == 5 ? 32 : 16;
        if (rawLength != null
                && (!(rawLength instanceof COSInteger)
                        || ((COSInteger) rawLength).intValue()
                                != expectedBytes)) {
            throw unsupported(
                    "The Standard crypt-filter Length is malformed.");
        }
    }

    private static void validateRevisionSixPermissions(
            PDEncryption encryption)
            throws IOException, GeneralSecurityException, DocumentFailure {
        byte[] encrypted = encryption.getPerms();
        SecurityHandler<ProtectionPolicy> handler =
                encryption.getSecurityHandler();
        byte[] key = handler.getEncryptionKey();
        if (encrypted == null || encrypted.length != 16
                || key == null || key.length != 32) {
            throw unsupported(
                    "The AES-256 permission integrity value is malformed.");
        }
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(key, "AES"));
        byte[] clear = cipher.doFinal(encrypted);
        try {
            int copiedPermissions = (clear[0] & 0xff)
                    | ((clear[1] & 0xff) << 8)
                    | ((clear[2] & 0xff) << 16)
                    | ((clear[3] & 0xff) << 24);
            byte metadataMarker = encryption.isEncryptMetaData()
                    ? (byte) 'T' : (byte) 'F';
            if (copiedPermissions != encryption.getPermissions()
                    || clear[4] != (byte) 0xff
                    || clear[5] != (byte) 0xff
                    || clear[6] != (byte) 0xff
                    || clear[7] != (byte) 0xff
                    || clear[8] != metadataMarker
                    || clear[9] != (byte) 'a'
                    || clear[10] != (byte) 'd'
                    || clear[11] != (byte) 'b') {
                throw unsupported(
                        "The AES-256 permission integrity value is inconsistent.");
            }
        } finally {
            Arrays.fill(clear, (byte) 0);
        }
    }

    static void requireCompatibleInput(
            PDDocument document,
            PdfVersionInfo version,
            PasswordSecurityInfo security) throws DocumentFailure {
        if (!security.isPasswordProtected()) {
            return;
        }
        PasswordEncryptionAlgorithm algorithm = security.getAlgorithm().get();
        PdfVersion effective = version.getEffectiveVersion();
        PDEncryption encryption = document.getEncryption();
        int encryptionVersion = encryption.getVersion();
        int revision = encryption.getRevision();
        if (security.getEncryptionScope()
                        == PasswordEncryptionScope.ALL_EXCEPT_METADATA
                && revision != 4) {
            throw unsupported(
                    "Metadata-clear input is supported only for revision 4 password security.");
        }
        PdfVersion minimum;
        if (algorithm == PasswordEncryptionAlgorithm.RC4_40) {
            minimum = revision == 2
                    ? PdfVersion.PDF_1_1 : PdfVersion.PDF_1_4;
        } else if (algorithm == PasswordEncryptionAlgorithm.RC4_128) {
            minimum = encryptionVersion == 4
                    ? PdfVersion.PDF_1_5 : PdfVersion.PDF_1_4;
        } else if (algorithm == PasswordEncryptionAlgorithm.AES_128) {
            minimum = PdfVersion.PDF_1_6;
        } else {
            minimum = PdfVersion.PDF_1_7;
        }
        if (effective.ordinal() < minimum.ordinal()) {
            throw unsupported(
                    "The password-security revision is incompatible with the effective PDF version.");
        }

        if (algorithm == PasswordEncryptionAlgorithm.RC4_40) {
            int count = extendedPermissionCount(
                    security.getDeclaredUserPermissions());
            if ((revision == 2 && count != 4)
                    || (revision == 3 && count == 4)) {
                throw unsupported(
                        "The RC4-40 revision and permission mask are inconsistent.");
            }
        }
        if (effective == PdfVersion.PDF_2_0
                && (revision != 6
                        || !security.getDeclaredUserPermissions()
                                .canExtractForAccessibility())) {
            throw unsupported(
                    "PDF 2.0 requires revision 6 and accessibility permission bit 10.");
        }
        if (algorithm == PasswordEncryptionAlgorithm.AES_256
                && effective == PdfVersion.PDF_1_7) {
            requireAdobeExtension(document, 8);
        }
    }

    private static void requireAdobeExtension(
            PDDocument document,
            int requiredLevel) throws DocumentFailure {
        COSBase rawExtensions = document.getDocumentCatalog().getCOSObject()
                .getDictionaryObject(COSName.EXTENSIONS);
        if (!(rawExtensions instanceof COSDictionary)) {
            throw unsupported(
                    "PDF 1.7 AES-256 requires a supported ADBE extension declaration.");
        }
        COSBase rawAdobe = ((COSDictionary) rawExtensions)
                .getDictionaryObject(COSName.ADBE);
        if (!(rawAdobe instanceof COSDictionary)) {
            throw unsupported(
                    "PDF 1.7 AES-256 requires a supported ADBE extension declaration.");
        }
        COSDictionary adobe = (COSDictionary) rawAdobe;
        if (!"1.7".equals(adobe.getNameAsString(COSName.BASE_VERSION))
                || adobe.getInt(COSName.EXTENSION_LEVEL, 0)
                        < requiredLevel) {
            throw unsupported(
                    "PDF 1.7 AES-256 requires a supported ADBE extension declaration.");
        }
    }

    static boolean hasOnlyPreservableAdobeSecurityExtension(
            PDDocument document) {
        COSBase rawExtensions = document.getDocumentCatalog().getCOSObject()
                .getDictionaryObject(COSName.EXTENSIONS);
        if (!(rawExtensions instanceof COSDictionary)) {
            return false;
        }
        COSDictionary extensions = (COSDictionary) rawExtensions;
        if (extensions.keySet().size() != 1
                || !extensions.containsKey(COSName.ADBE)) {
            return false;
        }
        COSBase rawAdobe = extensions.getDictionaryObject(COSName.ADBE);
        if (!(rawAdobe instanceof COSDictionary)) {
            return false;
        }
        COSDictionary adobe = (COSDictionary) rawAdobe;
        COSBase baseVersion = adobe.getItem(COSName.BASE_VERSION);
        COSBase extensionLevel = adobe.getItem(
                COSName.EXTENSION_LEVEL);
        return adobe.keySet().size() == 2
                && isExactAdobeSecurityExtension(
                        baseVersion,
                        extensionLevel);
    }

    private static boolean isExactAdobeSecurityExtension(
            COSDictionary adobe) {
        return adobe.keySet().size() == 2
                && isExactAdobeSecurityExtension(
                        adobe.getItem(COSName.BASE_VERSION),
                        adobe.getItem(COSName.EXTENSION_LEVEL));
    }

    private static boolean isExactAdobeSecurityExtension(
            COSBase baseVersion,
            COSBase extensionLevel) {
        return baseVersion instanceof COSName
                && "1.7".equals(((COSName) baseVersion).getName())
                && extensionLevel instanceof COSInteger
                && ((COSInteger) extensionLevel).intValue() == 8;
    }

    static PreparedOutput prepare(
            PasswordSecurityPolicy policy,
            PdfVersion outputVersion,
            SaveMode saveMode,
            LegacySecurityMode legacySecurityMode) throws DocumentFailure {
        if (policy == null) {
            return PreparedOutput.none();
        }
        if (saveMode != SaveMode.REWRITE) {
            throw unsupported(
                    "Changing password security is supported only for REWRITE publication.");
        }
        if (policy.getEncryptionScope()
                != PasswordEncryptionScope.ALL_CONTENT) {
            throw unsupported(
                    "Only encryption of all document content is supported for output.");
        }
        if (policy.getAlgorithm() != PasswordEncryptionAlgorithm.AES_256
                && legacySecurityMode
                        != LegacySecurityMode.ALLOW_OBSOLETE_PASSWORD_ENCRYPTION) {
            throw PdfBoxWorkflowEngine.versionFailure(
                    DocumentFailureCode.LEGACY_SECURITY_MODE_REQUIRED,
                    "Obsolete password-security output requires Legacy Security Mode.");
        }
        if (policy.getAlgorithm() == PasswordEncryptionAlgorithm.AES_256
                && outputVersion != PdfVersion.PDF_1_7
                && outputVersion != PdfVersion.PDF_2_0) {
            throw unsupported(
                    "AES-256 output requires PDF 1.7 or PDF 2.0.");
        }
        if (policy.getAlgorithm() != PasswordEncryptionAlgorithm.AES_256
                && outputVersion != PdfVersion.PDF_1_7) {
            throw unsupported(
                    "Obsolete password-security output is supported only with PDF 1.7.");
        }
        if (outputVersion == PdfVersion.PDF_2_0
                && !policy.getPermissions()
                        .canExtractForAccessibility()) {
            throw unsupported(
                    "PDF 2.0 password security requires accessibility permission bit 10.");
        }
        if (policy.getAlgorithm() == PasswordEncryptionAlgorithm.RC4_40) {
            throw unsupported(
                    "RC4-40 output is unsupported because its security-handler revision cannot be selected reliably.");
        }

        char[] owner = copyCredential(policy.getOwnerCredential());
        char[] user = null;
        try {
            user = copyCredential(policy.getUserCredential());
            if (owner.length == 0 || user.length == 0) {
                throw unsupported(
                        "Owner and user credentials must both be non-empty.");
            }
            int maximumCharacters = policy.getAlgorithm()
                    == PasswordEncryptionAlgorithm.AES_256 ? 127 : 32;
            requireCanonicalOutputCredential(owner, maximumCharacters);
            requireCanonicalOutputCredential(user, maximumCharacters);
            if (Arrays.equals(owner, user)) {
                throw unsupported(
                        "Owner and user credentials must be distinct.");
            }
            return new PreparedOutput(
                    policy.getAlgorithm(),
                    policy.getPermissions(),
                    outputVersion,
                    owner,
                    user);
        } catch (DocumentFailure | RuntimeException failure) {
            Arrays.fill(owner, '\0');
            if (user != null) {
                Arrays.fill(user, '\0');
            }
            throw failure;
        }
    }

    private static void requireCanonicalOutputCredential(
            char[] credential,
            int maximumCharacters) throws DocumentFailure {
        if (credential.length > maximumCharacters) {
            throw unsupported(
                    "A password credential exceeds the supported output length.");
        }
        for (char character : credential) {
            if (character < 0x20 || character > 0x7e) {
                throw unsupported(
                        "Output password credentials must use printable ASCII characters.");
            }
        }
    }

    static PreparedOutput preserveForIncrementalValidation(
            PasswordCredential credential) throws DocumentFailure {
        char[] validation = copyCredential(credential);
        return new PreparedOutput(
                null,
                null,
                null,
                null,
                null,
                validation);
    }

    private static char[] copyCredential(PasswordCredential credential)
            throws DocumentFailure {
        try {
            return credential.copyForExecution();
        } catch (IllegalStateException destroyed) {
            throw PdfBoxWorkflowEngine.versionFailure(
                    DocumentFailureCode.CREDENTIAL_DESTROYED,
                    "A password credential was destroyed before execution.");
        }
    }

    private static PasswordEncryptionAlgorithm algorithm(
            PDEncryption encryption) throws DocumentFailure {
        int version = encryption.getVersion();
        int revision = encryption.getRevision();
        int length = encryption.getLength();
        COSName method = cryptFilterMethod(encryption);

        if (version == 5 && revision == 6 && length == 256
                && COSName.AESV3.equals(method)) {
            return PasswordEncryptionAlgorithm.AES_256;
        }
        if (version == 4 && revision == 4 && length == 128
                && COSName.AESV2.equals(method)) {
            return PasswordEncryptionAlgorithm.AES_128;
        }
        if (version == 4 && revision == 4 && length == 128
                && RC4_CRYPT_FILTER.equals(method)) {
            return PasswordEncryptionAlgorithm.RC4_128;
        }
        if (version == 2 && revision == 3 && length == 128) {
            return PasswordEncryptionAlgorithm.RC4_128;
        }
        if (version == 1 && (revision == 2 || revision == 3)
                && (length == 40 || length == 0)) {
            return PasswordEncryptionAlgorithm.RC4_40;
        }
        throw unsupported(
                "The Standard password-security revision is not supported.");
    }

    private static int extendedPermissionCount(
            DocumentPermissions permissions) {
        int count = 0;
        count += permissions.canFillForms() ? 1 : 0;
        count += permissions.canExtractForAccessibility() ? 1 : 0;
        count += permissions.canAssembleDocument() ? 1 : 0;
        count += permissions.canPrintFaithfully() ? 1 : 0;
        return count;
    }

    private static COSName cryptFilterMethod(PDEncryption encryption) {
        PDCryptFilterDictionary filter =
                encryption.getDefaultCryptFilterDictionary();
        if (filter == null) {
            filter = encryption.getStdCryptFilterDictionary();
        }
        return filter == null ? null : filter.getCryptFilterMethod();
    }

    private static PasswordEncryptionScope scope(PDEncryption encryption)
            throws DocumentFailure {
        if (encryption.getVersion() < 4) {
            return PasswordEncryptionScope.ALL_CONTENT;
        }
        COSName streamFilter = encryption.getStreamFilterName();
        COSName stringFilter = encryption.getStringFilterName();
        COSBase embeddedFilter = encryption.getCOSObject()
                .getDictionaryObject(EFFECTIVE_FILE_FILTER);
        if (COSName.IDENTITY.equals(streamFilter)
                && COSName.IDENTITY.equals(stringFilter)
                && embeddedFilter instanceof COSName
                && !COSName.IDENTITY.equals(embeddedFilter)) {
            return PasswordEncryptionScope.EMBEDDED_FILES_ONLY;
        }
        if (!encryption.isEncryptMetaData()) {
            return PasswordEncryptionScope.ALL_EXCEPT_METADATA;
        }
        if ((streamFilter == null || !COSName.IDENTITY.equals(streamFilter))
                && (stringFilter == null
                        || !COSName.IDENTITY.equals(stringFilter))) {
            return PasswordEncryptionScope.ALL_CONTENT;
        }
        throw unsupported(
                "The password-encryption filter combination is not supported.");
    }

    private static DocumentFailure unsupported(String diagnostic) {
        return PdfBoxWorkflowEngine.versionFailure(
                DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                diagnostic);
    }

    static final class PreparedOutput implements AutoCloseable {

        private final PasswordEncryptionAlgorithm algorithm;
        private final DocumentPermissions permissions;
        private final PdfVersion version;
        private char[] owner;
        private char[] user;
        private char[] validation;

        private PreparedOutput(
                PasswordEncryptionAlgorithm algorithm,
                DocumentPermissions permissions,
                PdfVersion version,
                char[] owner,
                char[] user) {
            this(algorithm, permissions, version, owner, user, null);
        }

        private PreparedOutput(
                PasswordEncryptionAlgorithm algorithm,
                DocumentPermissions permissions,
                PdfVersion version,
                char[] owner,
                char[] user,
                char[] validation) {
            this.algorithm = algorithm;
            this.permissions = permissions;
            this.version = version;
            this.owner = owner;
            this.user = user;
            this.validation = validation;
        }

        private static PreparedOutput none() {
            return new PreparedOutput(null, null, null, null, null, null);
        }

        boolean isPresent() {
            return algorithm != null;
        }

        boolean preservesExistingSecurity() {
            return validation != null;
        }

        void preflight(PDDocument document) throws DocumentFailure {
            if (!isPresent()
                    || algorithm != PasswordEncryptionAlgorithm.AES_256) {
                return;
            }
            if (version == PdfVersion.PDF_2_0) {
                requireRemovableAdobeSecurityExtension(document);
                return;
            }
            if (version != PdfVersion.PDF_1_7) {
                return;
            }
            COSBase extensions = document.getDocumentCatalog().getCOSObject()
                    .getDictionaryObject(COSName.EXTENSIONS);
            if (extensions != null && !(extensions instanceof COSDictionary)) {
                throw unsupported(
                        "The catalog Extensions entry is not a dictionary.");
            }
            if (extensions instanceof COSDictionary) {
                COSBase adobe = ((COSDictionary) extensions)
                        .getDictionaryObject(COSName.ADBE);
                if (adobe != null && !(adobe instanceof COSDictionary)) {
                    throw unsupported(
                            "The catalog ADBE extension entry is not a dictionary.");
                }
            }
        }

        void apply(PDDocument document) throws IOException, DocumentFailure {
            if (!isPresent()) {
                return;
            }
            if (version == PdfVersion.PDF_1_7) {
                declareAdobeExtensionLevelEight(document);
            } else if (version == PdfVersion.PDF_2_0) {
                removeAdobeExtensionLevelEight(document);
            }
            AccessPermission accessPermission =
                    new AccessPermission(permissions.getStandardMask());
            String ownerPassword = new String(owner);
            String userPassword = new String(user);
            StandardProtectionPolicy protection =
                    new StandardProtectionPolicy(
                            ownerPassword,
                            userPassword,
                            accessPermission);
            if (algorithm == PasswordEncryptionAlgorithm.AES_256) {
                protection.setEncryptionKeyLength(256);
                protection.setPreferAES(true);
            } else if (algorithm == PasswordEncryptionAlgorithm.AES_128) {
                protection.setEncryptionKeyLength(128);
                protection.setPreferAES(true);
            } else if (algorithm == PasswordEncryptionAlgorithm.RC4_128) {
                protection.setEncryptionKeyLength(128);
                protection.setPreferAES(false);
            } else {
                protection.setEncryptionKeyLength(40);
                protection.setPreferAES(false);
            }
            document.protect(protection);
            document.getEncryption().setSecurityHandler(
                    new CanonicalStandardSecurityHandler(protection));
        }

        String validationPassword() {
            if (isPresent()) {
                return new String(user);
            }
            return validation == null ? "" : new String(validation);
        }

        String ownerValidationPassword() {
            return isPresent() ? new String(owner) : "";
        }

        void validate(PDDocument document) throws DocumentFailure {
            if (!isPresent()) {
                return;
            }
            PasswordSecurityInfo actual = inspect(document);
            PDEncryption encryption = document.getEncryption();
            int expectedVersion;
            int expectedRevision;
            int expectedLength;
            if (algorithm == PasswordEncryptionAlgorithm.AES_256) {
                expectedVersion = 5;
                expectedRevision = 6;
                expectedLength = 256;
            } else if (algorithm == PasswordEncryptionAlgorithm.AES_128) {
                expectedVersion = 4;
                expectedRevision = 4;
                expectedLength = 128;
            } else if (algorithm == PasswordEncryptionAlgorithm.RC4_128) {
                expectedVersion = 2;
                expectedRevision = 3;
                expectedLength = 128;
            } else {
                expectedVersion = 1;
                expectedRevision = 3;
                expectedLength = 40;
            }
            if (actual.getAlgorithm().orElse(null) != algorithm
                    || actual.getEncryptionScope()
                            != PasswordEncryptionScope.ALL_CONTENT
                    || !actual.getDeclaredUserPermissions()
                            .equals(permissions)
                    || encryption == null
                    || encryption.getVersion() != expectedVersion
                    || encryption.getRevision() != expectedRevision
                    || encryption.getLength() != expectedLength
                    || hasNoncanonicalCryptFilterLength(encryption)) {
                throw PdfBoxWorkflowEngine.versionFailure(
                        DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                        "The staged password-security dictionary does not match the output policy.");
            }
        }

        void validateOwner(PDDocument document) throws DocumentFailure {
            if (!isPresent()) {
                return;
            }
            PasswordSecurityInfo actual = inspect(document, owner);
            if (actual.getCredentialAuthority()
                            != CredentialAuthority.OWNER
                    || !actual.getEffectivePermissions().equals(
                            DocumentPermissions.unrestricted())) {
                throw PdfBoxWorkflowEngine.versionFailure(
                        DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                        "The staged owner credential does not have unrestricted authority.");
            }
        }

        private static boolean hasNoncanonicalCryptFilterLength(
                PDEncryption encryption) {
            PDCryptFilterDictionary filter =
                    encryption.getStdCryptFilterDictionary();
            return filter != null
                    && filter.getCOSObject().containsKey(COSName.LENGTH);
        }

        @Override
        public void close() {
            if (owner != null) {
                Arrays.fill(owner, '\0');
                owner = null;
            }
            if (user != null) {
                Arrays.fill(user, '\0');
                user = null;
            }
            if (validation != null) {
                Arrays.fill(validation, '\0');
                validation = null;
            }
        }

        private static void declareAdobeExtensionLevelEight(
                PDDocument document) throws DocumentFailure {
            COSDictionary catalog = document.getDocumentCatalog().getCOSObject();
            COSBase rawExtensions = catalog.getDictionaryObject(
                    COSName.EXTENSIONS);
            COSDictionary extensions;
            if (rawExtensions == null) {
                extensions = new COSDictionary();
                catalog.setItem(COSName.EXTENSIONS, extensions);
            } else if (rawExtensions instanceof COSDictionary) {
                extensions = (COSDictionary) rawExtensions;
            } else {
                throw unsupported(
                        "The catalog Extensions entry is not a dictionary.");
            }

            COSBase rawAdobe = extensions.getDictionaryObject(COSName.ADBE);
            COSDictionary adobe;
            if (rawAdobe == null) {
                adobe = new COSDictionary();
                extensions.setItem(COSName.ADBE, adobe);
            } else if (rawAdobe instanceof COSDictionary) {
                adobe = (COSDictionary) rawAdobe;
            } else {
                throw unsupported(
                        "The catalog ADBE extension entry is not a dictionary.");
            }
            adobe.setItem(COSName.BASE_VERSION, COSName.getPDFName("1.7"));
            int currentLevel = adobe.getInt(COSName.EXTENSION_LEVEL, 0);
            if (currentLevel < 8) {
                adobe.setItem(COSName.EXTENSION_LEVEL, COSInteger.get(8));
            }
        }

        private static void requireRemovableAdobeSecurityExtension(
                PDDocument document) throws DocumentFailure {
            COSBase rawExtensions = document.getDocumentCatalog()
                    .getCOSObject().getDictionaryObject(COSName.EXTENSIONS);
            if (rawExtensions == null) {
                return;
            }
            if (!(rawExtensions instanceof COSDictionary)) {
                throw unsupported(
                        "The catalog Extensions entry is not a dictionary.");
            }
            COSBase rawAdobe = ((COSDictionary) rawExtensions)
                    .getDictionaryObject(COSName.ADBE);
            if (rawAdobe != null
                    && (!(rawAdobe instanceof COSDictionary)
                            || !isExactAdobeSecurityExtension(
                                    (COSDictionary) rawAdobe))) {
                throw unsupported(
                        "The catalog ADBE extension entry is not safely removable.");
            }
        }

        private static void removeAdobeExtensionLevelEight(
                PDDocument document) throws DocumentFailure {
            COSDictionary catalog = document.getDocumentCatalog().getCOSObject();
            COSBase rawExtensions = catalog.getDictionaryObject(
                    COSName.EXTENSIONS);
            if (rawExtensions == null) {
                return;
            }
            if (!(rawExtensions instanceof COSDictionary)) {
                throw unsupported(
                        "The catalog Extensions entry is not a dictionary.");
            }
            COSDictionary extensions = (COSDictionary) rawExtensions;
            COSBase rawAdobe = extensions.getDictionaryObject(COSName.ADBE);
            if (rawAdobe != null) {
                if (!(rawAdobe instanceof COSDictionary)
                        || !isExactAdobeSecurityExtension(
                                (COSDictionary) rawAdobe)) {
                    throw unsupported(
                            "The catalog ADBE extension entry is not safely removable.");
                }
                extensions.removeItem(COSName.ADBE);
            }
            if (extensions.size() == 0) {
                catalog.removeItem(COSName.EXTENSIONS);
            }
        }
    }

    private static final class CanonicalStandardSecurityHandler
            extends SecurityHandler<ProtectionPolicy> {

        private final StandardSecurityHandler delegate;

        private CanonicalStandardSecurityHandler(
                StandardProtectionPolicy policy) {
            super(policy);
            delegate = new StandardSecurityHandler(policy);
        }

        @Override
        public void prepareDocumentForEncryption(PDDocument document)
                throws IOException {
            delegate.prepareDocumentForEncryption(document);
            copyState();
            PDCryptFilterDictionary filter = document.getEncryption()
                    .getStdCryptFilterDictionary();
            if (filter != null) {
                // The crypt-filter method fixes the key size; omission avoids
                // PDFBox's noncanonical bits-vs-bytes Length value.
                filter.getCOSObject().removeItem(COSName.LENGTH);
            }
        }

        @Override
        public void prepareForDecryption(
                PDEncryption encryption,
                COSArray documentIdentifiers,
                DecryptionMaterial material) throws IOException {
            delegate.prepareForDecryption(
                    encryption,
                    documentIdentifiers,
                    material);
            copyState();
        }

        private void copyState() {
            setKeyLength(delegate.getKeyLength());
            setAES(delegate.isAES());
            setEncryptionKey(delegate.getEncryptionKey());
            setCurrentAccessPermission(delegate.getCurrentAccessPermission());
        }
    }
}
