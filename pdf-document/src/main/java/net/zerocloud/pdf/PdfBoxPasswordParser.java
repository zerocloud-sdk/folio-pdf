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
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSBase;
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

    static void prepareIncremental(PDDocument document, WorkflowResourceContext resources) throws DocumentFailure {
        if (document instanceof OpenedDocument) {
            SourceHandler handler = ((OpenedDocument) document).owner;
            if (handler != null && handler.attachmentsOnly) {
                handler.attachments = PdfBoxEmbeddedFileEncryption.attachments(document, resources);
                for (COSStream stream : handler.attachments) {
                    resources.checkpoint();
                    // Decryption has removed an original named Crypt. New EF
                    // streams must receive the same effective protected filter.
                    PdfBoxEmbeddedFileEncryption.encrypted(stream, true, COSName.STD_CF, resources);
                    PdfBoxMetadataEncryption.normalizeForNewEncryption(stream);
                    if (!COSName.STD_CF.equals(handler.embeddedFilter)) {
                        PdfBoxEmbeddedFileEncryption.selectStdCF(stream, resources);
                    }
                }
            }
        }
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
                if (parser.sourceHandler != null) { parser.sourceHandler.finishAttachments(result, resources); }
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
            sourceHandler = new SourceHandler(document);
            sourceHandler.open(sourceEncryption, document.getDocumentID(), credential, resources);
            sourceEncryption.setSecurityHandler(sourceHandler);
            securityHandler = sourceHandler;
            PdfBoxPasswordSecurity.validateStandardStructure(sourceEncryption, resources,
                    sourceHandler.getEncryptionKey() != null);
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
        private final COSDocument document;
        private boolean clearMetadata;
        private boolean attachmentsOnly;
        private boolean attachmentAccess;
        private COSName embeddedFilter;
        private java.util.Set<COSStream> attachments;
        private final java.util.Map<COSStream, long[]> deferred = new java.util.IdentityHashMap<COSStream, long[]>();
        private WorkflowResourceContext.OwnedMemoryScope deferredMemory;
        private WorkflowResourceContext.MemoryReservation keyMemory;
        SourceHandler(COSDocument document) { this.document = document; }
        void open(PDEncryption encryption, COSArray identifiers, char[] characters,
                WorkflowResourceContext resources) throws IOException, DocumentFailure {
            int revision = encryption.getRevision();
            attachmentsOnly = PdfBoxEmbeddedFileEncryption.isAttachmentScope(encryption);
            if (attachmentsOnly) {
                embeddedFilter = PdfBoxEmbeddedFileEncryption.defaultFilter(encryption);
                deferredMemory = resources.ownedMemoryScope();
                setStreamFilterName(COSName.IDENTITY);
                setStringFilterName(COSName.IDENTITY);
                setCurrentAccessPermission(new AccessPermission(encryption.getPermissions()));
                if (characters == null) {
                    COSBase event = encryption.getStdCryptFilterDictionary().getCOSObject()
                            .getDictionaryObject(COSName.getPDFName("AuthEvent"));
                    if (event == null || COSName.getPDFName("DocOpen").equals(event)) {
                        throw PdfBoxWorkflowEngine.credentialFailure(true);
                    }
                    return;
                }
            }
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
                    attachmentAccess = ownerProof || getCurrentAccessPermission().canExtractContent();
                    setKeyLength(length * 8);
                    setAES(encryption.getVersion() >= 4 && !COSName.getPDFName("V2").equals(
                            encryption.getStdCryptFilterDictionary().getCryptFilterMethod()));
                    clearMetadata = !encryption.isEncryptMetaData();
                    setDecryptMetadata(true);
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

        @Override
        public void decryptStream(COSStream stream, long number, long generation) throws IOException {
            if (attachmentsOnly) {
                deferredMemory.retainAsIOException(96);
                deferred.put(stream, new long[] {number, generation});
            } else if (clearMetadata && PdfBoxMetadataEncryption.isDocumentMetadata(document, stream, number, generation)) {
                // Only the stream bytes are clear. Its dictionary strings still
                // follow StrF, so decrypt them through the ordinary dictionary path.
                COSDictionary dictionary = new COSDictionary(stream);
                super.decrypt(dictionary, number, generation);
                stream.addAll(dictionary);
            } else if (clearMetadata && COSName.METADATA.equals(stream.getCOSName(COSName.TYPE))) {
                // Component metadata remains encrypted. Avoid the backend's
                // permissive Metadata/XMP plaintext fallback for these streams.
                COSBase type = stream.getItem(COSName.TYPE);
                stream.removeItem(COSName.TYPE);
                try { super.decryptStream(stream, number, generation); }
                finally { stream.setItem(COSName.TYPE, type); }
            } else {
                super.decryptStream(stream, number, generation);
            }
        }

        @Override
        public void encryptStream(COSStream stream, long number, int generation) throws IOException {
            if (attachmentsOnly && !attachments.contains(stream)) { return; }
            if (clearMetadata && PdfBoxMetadataEncryption.isDocumentMetadata(document, stream, number, generation)) { return; }
            super.encryptStream(stream, number, generation);
        }

        @Override
        public void encryptString(COSString string, long number, int generation) throws IOException {
            if (!attachmentsOnly) { super.encryptString(string, number, generation); }
        }

        void finishAttachments(PDDocument opened, WorkflowResourceContext resources) throws IOException, DocumentFailure {
            if (!attachmentsOnly) { return; }
            attachments = PdfBoxEmbeddedFileEncryption.attachments(opened, resources);
            for (java.util.Map.Entry<COSStream, long[]> entry : deferred.entrySet()) {
                resources.checkpoint();
                COSStream stream = entry.getKey();
                if (!PdfBoxEmbeddedFileEncryption.encrypted(stream, attachments.contains(stream), embeddedFilter, resources)) { continue; }
                if (getEncryptionKey() == null) {
                    resources.denyProtectedStream(stream, DocumentFailureCode.CREDENTIAL_REQUIRED);
                } else {
                    setStreamFilterName(COSName.STD_CF);
                    try { super.decryptStream(stream, entry.getValue()[0], entry.getValue()[1]); }
                    finally { setStreamFilterName(COSName.IDENTITY); }
                    if (!attachmentAccess) { resources.denyProtectedStream(stream, DocumentFailureCode.DOCUMENT_PERMISSION_DENIED); }
                }
            }
            deferred.clear();
            deferredMemory.close();
            deferredMemory = null;
        }

        private static void wipe(byte[] value) { if (value != null) { Arrays.fill(value, (byte) 0); } }

        @Override public void close() {
            wipe(getEncryptionKey());
            setEncryptionKey(null);
            if (keyMemory != null) { keyMemory.close(); keyMemory = null; }
            deferred.clear();
            if (deferredMemory != null) { deferredMemory.close(); deferredMemory = null; }
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
