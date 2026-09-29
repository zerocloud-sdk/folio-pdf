package net.zerocloud.pdf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.DecryptionMaterial;
import org.apache.pdfbox.pdmodel.encryption.PDEncryption;
import org.apache.pdfbox.pdmodel.encryption.ProtectionPolicy;
import org.apache.pdfbox.pdmodel.encryption.SecurityHandler;
import org.apache.pdfbox.pdmodel.encryption.StandardSecurityHandler;

/** Selects the credential representation before deriving a Source decryption key. */
final class PdfBoxPasswordParser extends PDFParser {
    private final char[] credential;
    private final WorkflowResourceContext resources;
    private PDEncryption sourceEncryption;
    private SourceHandler sourceHandler;
    private DocumentFailure preparationFailure;
    private boolean decryptionPrepared;

    private PdfBoxPasswordParser(RandomAccessReadBufferedFile input, char[] credential,
            WorkflowResourceContext resources) throws IOException {
        super(input, "", null, null, resources.streamCacheFactory());
        this.credential = credential;
        this.resources = resources;
    }

    static PDDocument load(Path path, char[] credential, WorkflowResourceContext resources)
            throws IOException, DocumentFailure {
        RandomAccessReadBufferedFile input = new RandomAccessReadBufferedFile(path.toFile());
        boolean transferred = false;
        PdfBoxPasswordParser parser = null;
        try {
            parser = new PdfBoxPasswordParser(input, credential, resources);
            try {
                PDDocument result = parser.parse();
                if (parser.preparationFailure != null) { throw parser.preparationFailure; }
                transferred = true;
                return result;
            } catch (IOException failure) {
                if (parser.preparationFailure != null) { throw parser.preparationFailure; }
                throw failure;
            }
        } finally {
            if (!transferred) {
                try {
                    try { if (parser != null && parser.document != null) { parser.document.close(); } }
                    finally { input.close(); }
                }
                finally { if (parser != null && parser.sourceHandler != null) { parser.sourceHandler.close(); } }
            }
        }
    }

    @Override
    protected void prepareDecryption() throws IOException {
        if (decryptionPrepared) { return; }
        if (sourceEncryption != null) { throw new IOException("The Source decryption setup did not complete."); }
        COSDictionary dictionary = document.getEncryptionDictionary();
        if (dictionary == null) { return; }
        try {
            sourceEncryption = new PDEncryption(dictionary);
            PdfBoxPasswordSecurity.validateStandardStructure(sourceEncryption, resources, false);
            sourceHandler = new SourceHandler();
            sourceHandler.open(sourceEncryption, document.getDocumentID(), credential, resources);
            sourceEncryption.setSecurityHandler(sourceHandler);
            securityHandler = sourceHandler;
            PdfBoxPasswordSecurity.validateStandardStructure(sourceEncryption, resources, true);
            decryptionPrepared = true;
        } catch (DocumentFailure failure) {
            preparationFailure = failure;
            throw new IOException("The Source password-security declaration was rejected.");
        } catch (GeneralSecurityException failure) {
            throw new IOException("The Source password-security declaration could not be processed.");
        }
    }

    @Override
    protected PDEncryption getEncryption() throws IOException {
        if (sourceEncryption != null) {
            if (!decryptionPrepared) { throw new IOException("The Source decryption setup did not complete."); }
            return sourceEncryption;
        }
        if (super.getEncryption() != null) {
            // A repaired trailer must not bypass credential preparation and proof.
            throw new IOException("An encrypted Source requires an intact trailer.");
        }
        return null;
    }

    @Override
    protected AccessPermission getAccessPermission() throws IOException {
        return sourceEncryption == null ? super.getAccessPermission() : securityHandler.getCurrentAccessPermission();
    }

    @Override
    protected PDDocument createDocument() throws IOException {
        return new OpenedDocument(document, source, getAccessPermission(), sourceHandler);
    }

    private static final class OpenedDocument extends PDDocument {
        private final SourceHandler owner;
        OpenedDocument(COSDocument document, RandomAccessRead input, AccessPermission access, SourceHandler owner) {
            super(document, input, access);
            this.owner = owner;
        }
        @Override public void close() throws IOException {
            try { super.close(); }
            finally { if (owner != null) { owner.close(); } }
        }
    }

    private static final class SourceHandler extends SecurityHandler<ProtectionPolicy> implements AutoCloseable {
        private WorkflowResourceContext.MemoryReservation keyMemory;
        void open(PDEncryption encryption, COSArray identifiers, char[] characters,
                WorkflowResourceContext resources) throws IOException, DocumentFailure {
            int revision = encryption.getRevision();
            int length = encryption.getVersion() == 1 ? 5 : encryption.getLength() / 8;
            char[] selected = characters == null ? new char[0] : characters;
            COSString identifier = identifiers != null && identifiers.size() > 0
                    && identifiers.getObject(0) instanceof COSString ? (COSString) identifiers.getObject(0) : null;
            try (WorkflowResourceContext.MemoryReservation memory =
                    resources.reserveOwnedMemory(96L * selected.length + 1024L);
                    WorkflowResourceContext.OwnedBytes id = PdfBoxPasswordSecurity.workingBytes(identifier, resources)) {
                resources.checkpoint();
                String password = new String(selected);
                if (revision >= 5) {
                    try { password = PdfPasswordPreparation.saslPrepQuery(password); }
                    catch (IllegalArgumentException invalid) { throw PdfBoxWorkflowEngine.credentialFailure(characters == null); }
                } else {
                    for (char value : selected) {
                        if (value > 0xff) { throw PdfBoxWorkflowEngine.credentialFailure(characters == null); }
                    }
                }
                byte[] encoded = password.getBytes(revision >= 5 ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1);
                byte[] user = null, owner = null, recovered = null, key = null, oe = null, ue = null;
                try {
                    if (revision >= 5 && encoded.length > 127) {
                        byte[] truncated = Arrays.copyOf(encoded, 127);
                        wipe(encoded);
                        encoded = truncated;
                    }
                    user = encryption.getUserKey();
                    owner = encryption.getOwnerKey();
                    oe = revision >= 5 ? encryption.getOwnerEncryptionKey() : null;
                    ue = revision >= 5 ? encryption.getUserEncryptionKey() : null;
                    StandardSecurityHandler algorithms = new StandardSecurityHandler();
                    boolean ownerProof = algorithms.isOwnerPassword(encoded, user, owner, encryption.getPermissions(),
                            id.getBytes(), revision, length, encryption.isEncryptMetaData());
                    if (ownerProof && revision <= 4) {
                        recovered = algorithms.getUserPassword(encoded, owner, revision, length);
                    } else if (!ownerProof && revision == 3 && length == 5) {
                        recovered = PdfBoxPasswordSecurity.literalOwnerUser(encoded, owner);
                        ownerProof = algorithms.isUserPassword(recovered, user, owner, encryption.getPermissions(),
                                id.getBytes(), revision, length, encryption.isEncryptMetaData());
                    }
                    if (!ownerProof && !algorithms.isUserPassword(encoded, user, owner, encryption.getPermissions(),
                            id.getBytes(), revision, length, encryption.isEncryptMetaData())) {
                        throw PdfBoxWorkflowEngine.credentialFailure(characters == null);
                    }
                    keyMemory = resources.reserveOwnedMemory(length);
                    key = algorithms.computeEncryptedKey(ownerProof && revision <= 4 ? recovered : encoded,
                            owner, user, oe, ue, encryption.getPermissions(), id.getBytes(), revision, length,
                            encryption.isEncryptMetaData(), ownerProof);
                    setCurrentAccessPermission(ownerProof ? AccessPermission.getOwnerAccessPermission()
                            : new AccessPermission(encryption.getPermissions()));
                    setKeyLength(length * 8);
                    setAES(encryption.getVersion() >= 4 && !COSName.getPDFName("V2").equals(
                            encryption.getStdCryptFilterDictionary().getCryptFilterMethod()));
                    setDecryptMetadata(encryption.isEncryptMetaData());
                    if (encryption.getVersion() >= 4) {
                        setStreamFilterName(encryption.getStreamFilterName());
                        setStringFilterName(encryption.getStringFilterName());
                    }
                    setEncryptionKey(key);
                    key = null; // Handler owns the key for the opened document's lifetime.
                    resources.checkpoint();
                } finally {
                    wipe(encoded); wipe(user); wipe(owner); wipe(recovered); wipe(key); wipe(oe); wipe(ue);
                }
            }
        }

        private static void wipe(byte[] value) { if (value != null) { Arrays.fill(value, (byte) 0); } }

        @Override public void close() {
            wipe(getEncryptionKey());
            setEncryptionKey(null);
            if (keyMemory != null) { keyMemory.close(); keyMemory = null; }
        }

        @Override
        public void prepareForDecryption(PDEncryption encryption, COSArray identifiers, DecryptionMaterial material)
                throws IOException {
            throw new IOException("The Source handler has already been initialized.");
        }

        @Override
        public void prepareDocumentForEncryption(PDDocument document) throws IOException {
            throw new IOException("The Source handler cannot select a new output policy.");
        }
    }
}
