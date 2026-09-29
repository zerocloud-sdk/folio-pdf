package net.zerocloud.pdf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
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
    private static final COSName OWNER_ENCRYPTION_KEY =
            COSName.getPDFName("OE");
    private static final COSName USER_ENCRYPTION_KEY =
            COSName.getPDFName("UE");
    private static final COSName ENCRYPTED_PERMISSIONS =
            COSName.getPDFName("Perms");

    private PdfBoxPasswordSecurity() {
    }

    static PasswordSecurityInfo inspect(PDDocument document)
            throws DocumentFailure {
        return inspect(document, (char[]) null, null);
    }

    static PasswordSecurityInfo inspect(
            PDDocument document,
            PasswordCredential credential) throws DocumentFailure {
        return inspect(document, credential, null);
    }

    static PasswordSecurityInfo inspect(
            PDDocument document,
            PasswordCredential credential,
            WorkflowResourceContext resources) throws DocumentFailure {
        if (credential == null) {
            return inspect(document, (char[]) null, resources);
        }
        try (WorkflowCredentialCharacters characters =
                WorkflowCredentialCharacters.copyOf(credential, resources)) {
            return inspect(document, characters.get(), resources);
        }
    }

    static PasswordSecurityInfo inspect(
            PDDocument document,
            char[] credentialCharacters) throws DocumentFailure {
        return inspect(document, credentialCharacters, null);
    }

    static PasswordSecurityInfo inspect(
            PDDocument document,
            char[] credentialCharacters,
            WorkflowResourceContext resources) throws DocumentFailure {
        checkpoint(resources);
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
            validateStandardStructure(encryption, resources, true);
            PasswordEncryptionAlgorithm algorithm = algorithm(encryption);
            PasswordEncryptionScope scope = scope(encryption);
            DocumentPermissions declared =
                    DocumentPermissions.fromStandardMask(
                            encryption.getPermissions());
            AccessPermission current = document.getCurrentAccessPermission();
            CredentialAuthority authority;
            if (credentialCharacters != null) {
                boolean owner = authenticates(document, encryption, credentialCharacters, resources, true);
                if (owner) {
                    authority = CredentialAuthority.OWNER;
                } else if (authenticates(document, encryption, credentialCharacters, resources, false)) {
                    authority = declared.equals(DocumentPermissions.unrestricted())
                            ? CredentialAuthority.UNRESTRICTED : CredentialAuthority.USER;
                } else {
                    throw PdfBoxWorkflowEngine.versionFailure(DocumentFailureCode.CREDENTIAL_REJECTED,
                            "The supplied Source credential was not accepted.");
                }
            } else if (!current.isOwnerPermission()) {
                authority = CredentialAuthority.USER;
            } else if (declared.equals(DocumentPermissions.unrestricted())) {
                authority = CredentialAuthority.UNRESTRICTED;
            } else {
                authority = CredentialAuthority.OWNER;
            }
            DocumentPermissions effective = authority == CredentialAuthority.OWNER
                    ? DocumentPermissions.unrestricted()
                    : declared;
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
            if (resources != null) {
                resources.rethrowResourceOrTerminalFailure(backendFailure);
            }
            throw unsupported(
                    "The password-security dictionary is not supported.");
        }
    }

    private static boolean authenticates(
            PDDocument document,
            PDEncryption encryption,
            char[] credentialCharacters,
            WorkflowResourceContext resources,
            boolean ownerPassword)
            throws IOException, DocumentFailure {
        if (credentialCharacters == null) {
            return false;
        }
        checkpoint(resources);
        COSArray identifiers = document.getDocument().getDocumentID();
        COSString rawIdentifier = identifiers != null
                && identifiers.size() > 0
                && identifiers.getObject(0) instanceof COSString
                        ? (COSString) identifiers.getObject(0) : null;
        COSString rawUser = stringEntry(encryption, COSName.U);
        COSString rawOwner = stringEntry(encryption, COSName.O);
        int keyLengthBytes = encryption.getVersion() == 1
                ? 5 : encryption.getLength() / 8;
        try (WorkflowResourceContext.MemoryReservation memory =
                reserve(resources, 96L * credentialCharacters.length + 512L)) {
            if (encryption.getRevision() < 5) {
                for (char character : credentialCharacters) { if (character > 0xff) { return false; } }
            }
            byte[] passwordBytes = (encryption.getRevision() >= 5
                    ? PdfPasswordPreparation.saslPrepQuery(new String(credentialCharacters))
                    : new String(credentialCharacters)).getBytes(encryption.getRevision() >= 5
                            ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1);
            try {
                if (resources == null) {
                    return authenticatesBytes(ownerPassword, passwordBytes, rawUser.getBytes(), rawOwner.getBytes(),
                            encryption.getPermissions(), rawIdentifier == null ? new byte[0] : rawIdentifier.getBytes(),
                            encryption.getRevision(), keyLengthBytes, encryption.isEncryptMetaData());
                }
                try (WorkflowResourceContext.OwnedBytes identifier = workingBytes(rawIdentifier, resources);
                        WorkflowResourceContext.OwnedBytes user = workingBytes(rawUser, resources);
                        WorkflowResourceContext.OwnedBytes owner = workingBytes(rawOwner, resources)) {
                    resources.checkpoint();
                    boolean authenticated = authenticatesBytes(ownerPassword, passwordBytes, user.getBytes(), owner.getBytes(),
                            encryption.getPermissions(), identifier.getBytes(), encryption.getRevision(), keyLengthBytes,
                            encryption.isEncryptMetaData());
                    resources.checkpoint();
                    return authenticated;
                }
            } finally { Arrays.fill(passwordBytes, (byte) 0); }
        }
    }

    private static boolean authenticatesBytes(boolean ownerPassword, byte[] password,
            byte[] user, byte[] owner, int permissions, byte[] identifier, int revision,
            int keyLength, boolean metadata) throws IOException {
        StandardSecurityHandler handler = new StandardSecurityHandler();
        if (!ownerPassword) {
            return handler.isUserPassword(password, user, owner, permissions, identifier, revision, keyLength, metadata);
        }
        if (handler.isOwnerPassword(password, user, owner, permissions, identifier, revision, keyLength, metadata)) { return true; }
        if (revision != 3 || keyLength != 5) { return false; }
        byte[] recovered = literalOwnerUser(password, owner);
        try { return handler.isUserPassword(recovered, user, owner, permissions, identifier, revision, keyLength, metadata); }
        finally { Arrays.fill(recovered, (byte) 0); }
    }

    /** ISO 32000-1 Algorithm 3 hashes the complete digest before its final truncation. */
    static byte[] literalOwnerUser(byte[] password, byte[] owner) throws IOException {
        return transformFortyBitOwner(password, owner, false);
    }

    private static byte[] padLegacyPassword(byte[] password) {
        byte[] padding = {(byte) 0x28, (byte) 0xbf, (byte) 0x4e, (byte) 0x5e, (byte) 0x4e, (byte) 0x75,
                (byte) 0x8a, (byte) 0x41, (byte) 0x64, 0, (byte) 0x4e, (byte) 0x56, (byte) 0xff, (byte) 0xfa,
                1, 8, (byte) 0x2e, (byte) 0x2e, 0, (byte) 0xb6, (byte) 0xd0, (byte) 0x68, (byte) 0x3e,
                (byte) 0x80, (byte) 0x2f, (byte) 0x0c, (byte) 0xa9, (byte) 0xfe, (byte) 0x64, (byte) 0x53,
                (byte) 0x69, (byte) 0x7a};
        byte[] padded = new byte[32];
        int length = Math.min(password.length, 32);
        System.arraycopy(password, 0, padded, 0, length);
        System.arraycopy(padding, 0, padded, length, 32 - length);
        return padded;
    }

    private static byte[] transformFortyBitOwner(byte[] password, byte[] value,
            boolean encrypt) throws IOException {
        byte[] padded = padLegacyPassword(password), digest = null,
                roundKey = new byte[5], recovered = value.clone();
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            digest = md5.digest(padded);
            for (int round = 0; round < 50; round++) {
                byte[] next = md5.digest(digest);
                Arrays.fill(digest, (byte) 0);
                digest = next;
            }
            Cipher rc4 = Cipher.getInstance("RC4");
            for (int step = 0; step < 20; step++) {
                int round = encrypt ? step : 19 - step;
                for (int index = 0; index < 5; index++) { roundKey[index] = (byte) (digest[index] ^ round); }
                rc4.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                        new SecretKeySpec(roundKey, "RC4"));
                byte[] next = rc4.doFinal(recovered);
                Arrays.fill(recovered, (byte) 0);
                recovered = next;
            }
            byte[] result = recovered;
            recovered = null;
            return result;
        } catch (GeneralSecurityException failure) {
            throw new IOException("The legacy credential could not be processed.");
        } finally {
            Arrays.fill(padded, (byte) 0); Arrays.fill(roundKey, (byte) 0);
            if (digest != null) { Arrays.fill(digest, (byte) 0); }
            if (recovered != null) { Arrays.fill(recovered, (byte) 0); }
        }
    }

    static void validateStandardStructure(
            PDEncryption encryption,
            WorkflowResourceContext resources, boolean checkKey)
            throws IOException, GeneralSecurityException, DocumentFailure {
        checkpoint(resources);
        if (!COSName.getPDFName("Standard").equals(encryption.getCOSObject().getDictionaryObject(COSName.FILTER))) {
            throw unsupported("Only the Standard password-security handler is supported.");
        }
        for (COSName key : new COSName[] {COSName.V, COSName.R, COSName.LENGTH}) {
            COSBase value = encryption.getCOSObject().getDictionaryObject(key);
            if ((value == null && !COSName.LENGTH.equals(key)) || (value != null && (!(value instanceof COSInteger)
                    || ((COSInteger) value).longValue() != ((COSInteger) value).intValue()))) {
                throw unsupported("A password-security algorithm declaration is malformed.");
            }
        }
        if (encryption.getSubFilter() != null) {
            throw unsupported(
                    "Standard password security does not support a SubFilter.");
        }
        COSBase rawPermissions = encryption.getCOSObject()
                .getDictionaryObject(COSName.P);
        if (!(rawPermissions instanceof COSInteger)
                || ((COSInteger) rawPermissions).longValue() != ((COSInteger) rawPermissions).intValue()) {
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
                || (version == 2 && revision == 3 && (length == 40 || length == 128))
                || (version == 4 && revision == 4 && length == 128)
                || (version == 5 && (revision == 5 || revision == 6) && length == 256))) {
            throw unsupported(
                    "The Standard password-security revision is not supported.");
        }

        int expectedPasswordEntryLength = revision >= 5 ? 48 : 32;
        if (stringLength(encryption, COSName.O)
                        != expectedPasswordEntryLength
                || stringLength(encryption, COSName.U)
                        != expectedPasswordEntryLength) {
            throw unsupported(
                    "The password-security authentication entries are malformed.");
        }
        if (revision >= 5
                && (stringLength(encryption, OWNER_ENCRYPTION_KEY) != 32
                        || stringLength(encryption, USER_ENCRYPTION_KEY) != 32
                        || stringLength(encryption, ENCRYPTED_PERMISSIONS)
                                != 16)) {
            throw unsupported(
                    "The AES-256 authentication entries are malformed.");
        }

        if (version >= 4) {
            validateCryptFilters(encryption, version);
        }
        if (revision >= 5 && checkKey) {
            validateRevisionSixPermissions(encryption, resources);
        }
    }

    private static int stringLength(PDEncryption encryption, COSName key)
            throws DocumentFailure {
        COSString value = stringEntry(encryption, key);
        return value == null ? -1 : PdfBoxStringSupport.byteLength(
                value, PdfBoxPasswordSecurity::unsupportedStructure);
    }

    private static COSString stringEntry(
            PDEncryption encryption,
            COSName key) {
        COSBase value = encryption.getCOSObject().getDictionaryObject(key);
        return value instanceof COSString ? (COSString) value : null;
    }

    static WorkflowResourceContext.OwnedBytes workingBytes(
            COSString source,
            WorkflowResourceContext resources) throws DocumentFailure {
        if (source == null) {
            return emptyWorkingBytes(resources);
        }
        return PdfBoxStringSupport.workingBytes(
                source, resources, PdfBoxPasswordSecurity::unsupportedStructure);
    }

    private static WorkflowResourceContext.OwnedBytes emptyWorkingBytes(
            WorkflowResourceContext resources) throws DocumentFailure {
        try (WorkflowResourceContext.OwnedByteAccumulator output =
                resources.ownedByteAccumulator()) {
            return output.finishWorking();
        }
    }

    private static WorkflowResourceContext.MemoryReservation reserve(
            WorkflowResourceContext resources,
            long amount) throws DocumentFailure {
        return resources.reserveOwnedMemory(amount);
    }

    private static void checkpoint(WorkflowResourceContext resources)
            throws DocumentFailure {
        if (resources != null) {
            resources.checkpoint();
        }
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
        COSBase authEvent = filter.getCOSObject().getDictionaryObject(
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
        COSBase metadata = encryption.getCOSObject().getDictionaryObject(COSName.ENCRYPT_META_DATA);
        if (metadata != null && !(metadata instanceof org.apache.pdfbox.cos.COSBoolean)) {
            throw unsupported("The metadata encryption declaration is malformed.");
        }
    }

    private static void validateRevisionSixPermissions(
            PDEncryption encryption,
            WorkflowResourceContext resources)
            throws IOException, GeneralSecurityException, DocumentFailure {
        checkpoint(resources);
        if (resources == null) {
            validateRevisionSixPermissionsUnaccounted(encryption);
            return;
        }
        COSString rawPermissions = stringEntry(
                encryption, ENCRYPTED_PERMISSIONS);
        try (WorkflowResourceContext.OwnedBytes encrypted =
                        workingBytes(rawPermissions, resources);
                WorkflowResourceContext.MemoryReservation keyMemory =
                        reserve(resources, 32L);
                WorkflowResourceContext.MemoryReservation clearMemory =
                        reserve(resources, 16L)) {
            SecurityHandler<ProtectionPolicy> handler =
                    encryption.getSecurityHandler();
            byte[] key = handler.getEncryptionKey();
            if (encrypted.getBytes().length != 16
                    || key == null || key.length != 32) {
                throw unsupported(
                        "The AES-256 permission integrity value is malformed.");
            }
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"));
            byte[] clear = cipher.doFinal(encrypted.getBytes());
            try {
                requireRevisionSixPermissions(encryption, clear);
            } finally {
                Arrays.fill(clear, (byte) 0);
            }
        }
    }

    private static void requireRevisionSixPermissions(
            PDEncryption encryption,
            byte[] clear) throws DocumentFailure {
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
    }

    private static DocumentFailure unsupportedStructure() {
        return unsupported(
                "The password-security dictionary is not supported.");
    }

    private static void validateRevisionSixPermissionsUnaccounted(
            PDEncryption encryption)
            throws IOException, GeneralSecurityException, DocumentFailure {
        byte[] encrypted = encryption.getPerms();
        byte[] key = encryption.getSecurityHandler().getEncryptionKey();
        if (encrypted == null || encrypted.length != 16
                || key == null || key.length != 32) {
            throw unsupported(
                    "The AES-256 permission integrity value is malformed.");
        }
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
        byte[] clear = cipher.doFinal(encrypted);
        try {
            requireRevisionSixPermissions(encryption, clear);
        } finally {
            Arrays.fill(clear, (byte) 0);
            Arrays.fill(encrypted, (byte) 0);
        }
    }

    static PasswordSecurityInfo requireCompatibleInput(
            PDDocument document,
            PdfVersionInfo version,
            PasswordSecurityInfo security) throws DocumentFailure {
        if (!security.isPasswordProtected()) {
            return security;
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

        if (algorithm == PasswordEncryptionAlgorithm.RC4_40 && encryptionVersion == 1) {
            int count = extendedPermissionCount(
                    security.getDeclaredUserPermissions());
            if ((revision == 2 && count != 4)
                    || (revision == 3 && count == 4)) {
                throw unsupported(
                        "The RC4-40 revision and permission mask are inconsistent.");
            }
        }
        if (algorithm == PasswordEncryptionAlgorithm.AES_256
                && effective == PdfVersion.PDF_1_7) {
            requireAdobeExtension(document, revision == 5 ? 3 : 8);
        }
        if (effective == PdfVersion.PDF_2_0 && security.getCredentialAuthority() != CredentialAuthority.OWNER) {
            DocumentPermissions permissions = DocumentPermissions.fromStandardMask(
                    security.getDeclaredUserPermissions().getStandardMask() | 512);
            return new PasswordSecurityInfo(algorithm, revision, security.getEncryptionScope(),
                    security.getDeclaredUserPermissions(), permissions,
                    permissions.equals(DocumentPermissions.unrestricted()) ? CredentialAuthority.UNRESTRICTED : CredentialAuthority.USER);
        }
        return security;
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
        return isPreservableAdobeSecurityExtension(document, (COSDictionary) rawAdobe);
    }

    private static boolean isPreservableAdobeSecurityExtension(PDDocument document, COSDictionary adobe) {
        COSBase baseVersion = adobe.getItem(COSName.BASE_VERSION);
        COSBase extensionLevel = adobe.getItem(
                COSName.EXTENSION_LEVEL);
        return adobe.keySet().size() == 2
                && (isExactAdobeSecurityExtension(
                        baseVersion,
                        extensionLevel)
                    || (document.isEncrypted() && document.getEncryption().getRevision() == 5
                        && COSName.getPDFName("1.7").equals(baseVersion)
                        && extensionLevel instanceof COSInteger
                        && ((COSInteger) extensionLevel).longValue() == 3));
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
            LegacySecurityMode legacySecurityMode,
            WorkflowResourceContext resources) throws DocumentFailure {
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
        WorkflowCredentialCharacters owner =
                WorkflowCredentialCharacters.copyOf(
                        policy.getOwnerCredential(), resources);
        WorkflowCredentialCharacters user = null;
        try {
            user = WorkflowCredentialCharacters.copyOf(
                    policy.getUserCredential(), resources);
            boolean emptyOwner = requireOutputCredential(owner.get(), policy.getAlgorithm(), resources);
            requireOutputCredential(user.get(), policy.getAlgorithm(), resources);
            if (emptyOwner) {
                owner.close();
                owner = WorkflowCredentialCharacters.randomOwner(resources);
            }
            return new PreparedOutput(
                    policy.getAlgorithm(),
                    policy.getPermissions(),
                    outputVersion,
                    owner,
                    user);
        } catch (DocumentFailure | RuntimeException | Error failure) {
            owner.close();
            if (user != null) {
                user.close();
            }
            throw failure;
        }
    }

    private static boolean requireOutputCredential(
            char[] credential,
            PasswordEncryptionAlgorithm algorithm,
            WorkflowResourceContext resources) throws DocumentFailure {
        if (algorithm == PasswordEncryptionAlgorithm.AES_256) {
            try (WorkflowResourceContext.MemoryReservation memory =
                    resources.reserveOwnedMemory(96L * credential.length + 512L)) {
                return PdfPasswordPreparation.saslPrepStored(new String(credential)).isEmpty();
            } catch (IllegalArgumentException invalid) {
                throw unsupported("The output credential cannot be prepared for password security.");
            }
        } else {
            for (char character : credential) {
                if (character > 0xff) {
                    throw unsupported("A legacy credential must contain byte-valued characters.");
                }
            }
            return credential.length == 0;
        }
    }

    static PreparedOutput preserveForIncrementalValidation(
            PasswordCredential credential,
            WorkflowResourceContext resources) throws DocumentFailure {
        WorkflowCredentialCharacters validation =
                WorkflowCredentialCharacters.copyOf(credential, resources);
        return new PreparedOutput(
                null,
                null,
                null,
                null,
                null,
                validation);
    }

    private static PasswordEncryptionAlgorithm algorithm(
            PDEncryption encryption) throws DocumentFailure {
        int version = encryption.getVersion();
        int revision = encryption.getRevision();
        int length = encryption.getLength();
        COSName method = cryptFilterMethod(encryption);

        if (version == 5 && (revision == 5 || revision == 6) && length == 256
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
        if ((version == 1 && (revision == 2 || revision == 3) && (length == 40 || length == 0))
                || (version == 2 && revision == 3 && length == 40)) {
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

    static final class PreparedPassword implements AutoCloseable {

        private char[] password;
        private WorkflowResourceContext.MemoryReservation reservation;

        private PreparedPassword(
                char[] password,
                WorkflowResourceContext.MemoryReservation reservation) {
            this.password = password;
            this.reservation = reservation;
        }

        private static PreparedPassword empty() {
            return new PreparedPassword(new char[0], null);
        }

        private static PreparedPassword from(
                WorkflowCredentialCharacters credential,
                WorkflowResourceContext resources) throws DocumentFailure {
            if (credential == null) {
                return empty();
            }
            char[] characters = credential.get();
            WorkflowResourceContext.MemoryReservation reservation =
                    resources.reserveOwnedMemory(2L * characters.length);
            char[] password = null;
            try {
                resources.checkpoint();
                password = characters.clone();
                resources.checkpoint();
                return new PreparedPassword(password, reservation);
            } catch (DocumentFailure | RuntimeException | Error failure) {
                if (password != null) { Arrays.fill(password, '\0'); }
                reservation.close();
                throw failure;
            }
        }

        char[] get() {
            if (password == null) {
                throw new IllegalStateException(
                        "The prepared password is no longer available.");
            }
            return password;
        }

        @Override
        public void close() {
            if (password != null) { Arrays.fill(password, '\0'); }
            password = null;
            if (reservation != null) {
                reservation.close();
                reservation = null;
            }
        }
    }

    static final class PreparedOutput implements AutoCloseable {

        private final PasswordEncryptionAlgorithm algorithm;
        private final DocumentPermissions permissions;
        private final PdfVersion version;
        private WorkflowCredentialCharacters owner;
        private WorkflowCredentialCharacters user;
        private WorkflowCredentialCharacters validation;
        private final java.util.List<CanonicalStandardSecurityHandler> outputHandlers =
                new java.util.ArrayList<CanonicalStandardSecurityHandler>();
        private final List<WorkflowResourceContext.MemoryReservation>
                backendPasswordMemory =
                        new ArrayList<
                                WorkflowResourceContext.MemoryReservation>();

        private PreparedOutput(
                PasswordEncryptionAlgorithm algorithm,
                DocumentPermissions permissions,
                PdfVersion version,
                WorkflowCredentialCharacters owner,
                WorkflowCredentialCharacters user) {
            this(algorithm, permissions, version, owner, user, null);
        }

        private PreparedOutput(
                PasswordEncryptionAlgorithm algorithm,
                DocumentPermissions permissions,
                PdfVersion version,
                WorkflowCredentialCharacters owner,
                WorkflowCredentialCharacters user,
                WorkflowCredentialCharacters validation) {
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

        void apply(
                PDDocument document,
                WorkflowResourceContext resources)
                throws IOException, DocumentFailure {
            if (!isPresent()) {
                return;
            }
            if (version == PdfVersion.PDF_1_7 && algorithm == PasswordEncryptionAlgorithm.AES_256) {
                declareAdobeExtensionLevelEight(document);
            } else if (version == PdfVersion.PDF_2_0) {
                removeAdobeExtensionLevelEight(document);
            }
            AccessPermission accessPermission =
                    new AccessPermission(permissions.getStandardMask());
            WorkflowResourceContext.MemoryReservation backendMemory =
                    resources.reserveOwnedMemory(96L
                            * (owner.get().length + user.get().length) + 1024L);
            boolean retained = false;
            CanonicalStandardSecurityHandler handler = null;
            try {
                resources.checkpoint();
                String ownerPassword = new String(owner.get());
                String userPassword = new String(user.get());
                if (algorithm == PasswordEncryptionAlgorithm.AES_256) {
                    ownerPassword = PdfPasswordPreparation.saslPrepStored(ownerPassword);
                    userPassword = PdfPasswordPreparation.saslPrepStored(userPassword);
                }
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
                } else if (algorithm
                        == PasswordEncryptionAlgorithm.RC4_128) {
                    protection.setEncryptionKeyLength(128);
                    protection.setPreferAES(false);
                } else {
                    protection.setEncryptionKeyLength(40);
                    protection.setPreferAES(false);
                }
                document.protect(protection);
                handler = new CanonicalStandardSecurityHandler(protection);
                document.getEncryption().setSecurityHandler(handler);
                resources.checkpoint();
                outputHandlers.add(handler);
                backendPasswordMemory.add(backendMemory);
                retained = true;
            } finally {
                if (!retained) {
                    if (handler != null) { handler.close(); }
                    backendMemory.close();
                }
            }
        }

        PreparedPassword validationPassword(
                WorkflowResourceContext resources) throws DocumentFailure {
            if (isPresent()) {
                return PreparedPassword.from(user, resources);
            }
            return PreparedPassword.from(validation, resources);
        }

        PreparedPassword ownerValidationPassword(
                WorkflowResourceContext resources) throws DocumentFailure {
            return isPresent()
                    ? PreparedPassword.from(owner, resources)
                    : PreparedPassword.empty();
        }

        void validate(
                PDDocument document,
                WorkflowResourceContext resources) throws DocumentFailure {
            if (!isPresent()) {
                return;
            }
            PasswordSecurityInfo actual = inspect(
                    document, (char[]) null, resources);
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
                expectedRevision = extendedPermissionCount(permissions) == 4 ? 2 : 3;
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

        void validateOwner(
                PDDocument document,
                WorkflowResourceContext resources) throws DocumentFailure {
            if (!isPresent()) {
                return;
            }
            PasswordSecurityInfo actual = inspect(
                    document, owner.get(), resources);
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
            return filter != null && (COSName.AESV3.equals(filter.getCryptFilterMethod())
                    ? filter.getLength() != 32 : filter.getCOSObject().containsKey(COSName.LENGTH));
        }

        @Override
        public void close() {
            if (owner != null) {
                owner.close();
                owner = null;
            }
            if (user != null) {
                user.close();
                user = null;
            }
            if (validation != null) {
                validation.close();
                validation = null;
            }
            for (CanonicalStandardSecurityHandler handler : outputHandlers) { handler.close(); }
            outputHandlers.clear();
            for (WorkflowResourceContext.MemoryReservation reservation
                    : backendPasswordMemory) {
                reservation.close();
            }
            backendPasswordMemory.clear();
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
                            || !isPreservableAdobeSecurityExtension(document,
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
                        || !isPreservableAdobeSecurityExtension(document,
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
            extends SecurityHandler<ProtectionPolicy> implements AutoCloseable {

        private final StandardSecurityHandler delegate;
        private final StandardProtectionPolicy policy;

        private CanonicalStandardSecurityHandler(
                StandardProtectionPolicy policy) {
            super(policy);
            this.policy = policy;
            delegate = new StandardSecurityHandler(policy);
        }

        @Override
        public void prepareDocumentForEncryption(PDDocument document)
                throws IOException {
            if (policy.getEncryptionKeyLength() == 40) {
                prepareFortyBit(document);
            } else {
                delegate.prepareDocumentForEncryption(document);
                copyState();
            }
            PDCryptFilterDictionary filter = document.getEncryption()
                    .getStdCryptFilterDictionary();
            if (filter != null) {
                // PDF 2.0 requires the AESV3 byte count. ISO 32000-1 permits
                // omission for AESV2; the backend emits noncanonical bit counts.
                if (COSName.AESV3.equals(filter.getCryptFilterMethod())) { filter.setLength(32); }
                else { filter.getCOSObject().removeItem(COSName.LENGTH); }
            }
        }

        private void prepareFortyBit(PDDocument document) throws IOException {
            // ISO 32000-1 table 21 selects R2 when the extended permission
            // bits are all granted, and R3 when any of them are restricted.
            int permissions = policy.getPermissions().getPermissionBytes();
            int revision = (permissions & 0xf00) == 0xf00 ? 2 : 3;
            PDEncryption encryption = document.getEncryption();
            encryption.removeV45filters();
            encryption.setFilter("Standard");
            encryption.setVersion(1);
            encryption.setRevision(revision);
            encryption.setLength(40);
            encryption.setPermissions(permissions);
            COSArray identifiers = document.getDocument().getDocumentID();
            if (identifiers == null || identifiers.size() < 2) {
                byte[] identifier = new byte[16];
                new SecureRandom().nextBytes(identifier);
                identifiers = new COSArray();
                identifiers.add(new COSString(identifier));
                identifiers.add(new COSString(identifier));
                document.getDocument().setDocumentID(identifiers);
                Arrays.fill(identifier, (byte) 0);
            }
            byte[] owner = policy.getOwnerPassword().getBytes(StandardCharsets.ISO_8859_1);
            byte[] user = policy.getUserPassword().getBytes(StandardCharsets.ISO_8859_1);
            byte[] ownerEntry = null;
            byte[] userEntry = null;
            try {
                byte[] identifier = ((COSString) identifiers.getObject(0)).getBytes();
                if (revision == 3) {
                    byte[] paddedUser = padLegacyPassword(user);
                    try { ownerEntry = transformFortyBitOwner(owner, paddedUser, true); }
                    finally { Arrays.fill(paddedUser, (byte) 0); }
                } else {
                    ownerEntry = delegate.computeOwnerPassword(owner, user, revision, 5);
                }
                userEntry = delegate.computeUserPassword(user, ownerEntry, permissions, identifier, revision, 5, true);
                setEncryptionKey(delegate.computeEncryptedKey(user, ownerEntry, userEntry,
                        null, null, permissions, identifier, revision, 5, true, false));
                encryption.setOwnerKey(ownerEntry);
                encryption.setUserKey(userEntry);
                setKeyLength(40);
                setAES(false);
                setCurrentAccessPermission(policy.getPermissions());
                document.setEncryptionDictionary(encryption);
                document.getDocument().setEncryptionDictionary(encryption.getCOSObject());
            } finally {
                Arrays.fill(owner, (byte) 0);
                Arrays.fill(user, (byte) 0);
                if (ownerEntry != null) { Arrays.fill(ownerEntry, (byte) 0); }
                if (userEntry != null) { Arrays.fill(userEntry, (byte) 0); }
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

        @Override public void close() {
            if (getEncryptionKey() != null) { Arrays.fill(getEncryptionKey(), (byte) 0); }
            if (delegate.getEncryptionKey() != null) { Arrays.fill(delegate.getEncryptionKey(), (byte) 0); }
            setEncryptionKey(null);
            delegate.setEncryptionKey(null);
        }
    }
}
