package net.zerocloud.pdf.itext7.consumer;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.EmbeddedFile;
import net.zerocloud.pdf.LegacySecurityMode;
import net.zerocloud.pdf.PasswordCredential;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;
import net.zerocloud.pdf.itext7.kernel.pdf.EncryptionConstants;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfVersion;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;
import net.zerocloud.pdf.itext7.kernel.pdf.ReaderProperties;
import net.zerocloud.pdf.itext7.kernel.pdf.WriterProperties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Actual Stable/Preview reader, writer and attachment calls execute IN_PROCESS. */
public final class EmbeddedFilesPasswordFacadeTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private static final Path CORPUS = Paths.get(System.getProperty("repositoryRoot", ".."))
            .resolve("capabilities/profiles/T80-embedded-files-only");
    private static final byte[] USER = "baseline-user".getBytes(StandardCharsets.UTF_8);
    private static final byte[] OWNER = "baseline-owner".getBytes(StandardCharsets.UTF_8);

    @Test public void aesSelectorsCreateAndReopenWithClearContentAndExactAttachments() throws Exception {
        assertEquals(24, EncryptionConstants.EMBEDDED_FILES_ONLY);
        assertEquals(24, EncryptionConstants.ONLY_EMBEDDED_FILES);
        for (int selector : new int[] {26, 27}) {
            Path target = temporary.newFile().toPath();
            PdfDocument created;
            try (WriterProperties writing = writing(selector)) {
                created = new PdfDocument(new PdfWriter(target.toString(), writing));
                try (PdfDocument document = created) {
                    document.addNewPage();
                    document.getDocumentInfo().setTitle("Clear T80 title");
                    document.setXmpMetadata(Files.readAllBytes(CORPUS.resolve("metadata.xmp")));
                    document.addFileAttachment(EmbeddedFile.version1("proof.txt", payload()));
                }
            }
            assertEquals(PublicationStatus.COMMITTED, created.getPublicationReceipts().get(0).getStatus());
            try (PdfReader reader = new PdfReader(target.toString()); PdfDocument document = new PdfDocument(reader)) {
                assertTrue(reader.isEncrypted()); assertFalse(reader.isOpenedWithFullPermission());
                assertEquals(selector, reader.getCryptoMode());
                assertEquals("Clear T80 title", document.getDocumentInfo().getTitle());
                assertArrayEquals(Files.readAllBytes(CORPUS.resolve("metadata.xmp")), document.getXmpMetadata());
                assertEquals(1, document.getNumberOfPages()); assertEquals(1, document.getFileAttachments(8).size());
                expect(DocumentFailureCode.CREDENTIAL_REQUIRED, () -> document.getFileAttachment("proof.txt", 4096));
            }
            for (byte[] password : new byte[][] {USER, OWNER}) {
                try (ReaderProperties reading = new ReaderProperties().setPassword(password);
                        PdfReader reader = new PdfReader(target.toString(), reading); PdfDocument document = new PdfDocument(reader)) {
                    assertEquals(Arrays.equals(password, OWNER), reader.isOpenedWithFullPermission());
                    assertEquals(selector, reader.getCryptoMode());
                    assertArrayEquals(payload(), document.getFileAttachment("proof.txt", 4096).get().getContent());
                }
            }
        }
    }

    @Test public void originalR4R5R6InputsExposeScopeWithoutAttachmentAuthentication() throws Exception {
        String[] names = {"rc4-cf", "aes-128", "aes-256-r5", "aes-256-r6", "aes-256-pdf20", "aes-256-r6-untyped", "aes-256-r6-crypt-array"};
        for (int index = 0; index < names.length; index++) {
            int mode = index == 0 ? 25 : index == 1 ? 26 : 27;
            try (PdfReader reader = new PdfReader(file(names[index]).toString()); PdfDocument document = new PdfDocument(reader)) {
                assertEquals(mode, reader.getCryptoMode());
                assertFalse(reader.isOpenedWithFullPermission());
                assertEquals("Folio T80 clear document title", document.getDocumentInfo().getTitle());
                assertEquals(1, document.getFileAttachments(8).size());
            }
            try (ReaderProperties reading = new ReaderProperties().setPassword(OWNER);
                    PdfReader reader = new PdfReader(file(names[index]).toString(), reading); PdfDocument document = new PdfDocument(reader)) {
                assertTrue(reader.isOpenedWithFullPermission());
                assertArrayEquals(payload(), document.getFileAttachment("proof.txt", 4096).get().getContent());
            }
        }
    }

    @Test public void malformedSelectorsCannotDiscardTheExplicitAttachmentScope() throws Exception {
        for (int selector : new int[] {8, 16, 17, 18, 19, 24, 25, 28, 32, -1}) {
            try (WriterProperties properties = new WriterProperties()) {
                try { properties.setStandardEncryption(USER, OWNER, EncryptionConstants.ALLOW_COPY, selector); fail("Unsupported selector " + selector); }
                catch (IllegalArgumentException expected) { }
            }
        }
    }

    @Test public void propertiesCopyCredentialsAndKeepCallerStreamsAndBorrowedCredentialsAlive() throws Exception {
        byte[] original = "I\u00adX\u00a0\u2168".getBytes(StandardCharsets.UTF_8);
        byte[] password = original.clone();
        BorrowedOutput output = new BorrowedOutput();
        PdfWriter writer;
        try (WriterProperties writing = new WriterProperties().setStandardEncryption(password, OWNER, EncryptionConstants.ALLOW_COPY, 27)) {
            writer = new PdfWriter(output, writing);
        }
        Arrays.fill(password, (byte) 0);
        try (PdfDocument document = new PdfDocument(writer)) { document.addNewPage(); document.addFileAttachment(EmbeddedFile.version1("proof.txt", payload())); }
        assertFalse(output.closed);
        BorrowedInput input = new BorrowedInput(output.toByteArray());
        PdfReader reader;
        try (ReaderProperties reading = new ReaderProperties().setPassword(original)) { reader = new PdfReader(input, reading); }
        Arrays.fill(original, (byte) 0);
        try (PdfDocument document = new PdfDocument(reader)) { assertEquals(27, reader.getCryptoMode()); assertArrayEquals(payload(), document.getFileAttachment("proof.txt", 4096).get().getContent()); }
        assertFalse(input.closed);
        try (PasswordCredential credential = PasswordCredential.of("baseline-owner".toCharArray())) {
            try (ReaderProperties reading = new ReaderProperties().setCredential(credential);
                    PdfReader borrowed = new PdfReader(file("aes-256-r6").toString(), reading)) { assertTrue(borrowed.isOpenedWithFullPermission()); }
            try (ReaderProperties reading = new ReaderProperties().setCredential(credential);
                    PdfReader borrowed = new PdfReader(file("aes-256-r6").toString(), reading)) { assertTrue(borrowed.isOpenedWithFullPermission()); }
        }
    }

    @Test public void wrongCredentialsAndDeniedAttachmentPermissionsMapSafeFailures() throws Exception {
        try (ReaderProperties reading = new ReaderProperties().setPassword("T80-wrong-secret".getBytes(StandardCharsets.UTF_8))) {
            try (PdfReader reader = new PdfReader(file("aes-256-r6").toString(), reading)) { fail("Wrong attachment credential"); }
            catch (IOException failure) { assertTrue(failure.getMessage().startsWith("CREDENTIAL_REJECTED:")); assertFalse(failure.getMessage().contains("T80-wrong-secret")); }
        }
        try (ReaderProperties reading = new ReaderProperties().setPassword(USER);
                PdfReader reader = new PdfReader(file("aes-256-r6").toString(), reading); PdfDocument document = new PdfDocument(reader)) {
            assertEquals("Folio T80 clear document title", document.getDocumentInfo().getTitle());
            expect(DocumentFailureCode.DOCUMENT_PERMISSION_DENIED, () -> document.getFileAttachment("proof.txt", 4096));
        }
    }

    @Test public void ownerRewritePreservesAttachmentsAndPdf20UsesExplicitSecureOutput() throws Exception {
        Path target = temporary.newFile().toPath();
        PdfDocument rewritten;
        try (ReaderProperties reading = new ReaderProperties().setPassword(OWNER);
                WriterProperties writing = writing(27).setPdfVersion(PdfVersion.PDF_2_0);
                PdfReader reader = new PdfReader(file("aes-256-r6").toString(), reading)) {
            rewritten = new PdfDocument(reader, new PdfWriter(target.toString(), writing));
            try (PdfDocument document = rewritten) { assertArrayEquals(payload(), document.getFileAttachment("proof.txt", 4096).get().getContent()); }
        }
        assertEquals(PublicationStatus.COMMITTED, rewritten.getPublicationReceipts().get(0).getStatus());
        try (ReaderProperties reading = new ReaderProperties().setPassword(USER);
                PdfReader reader = new PdfReader(target.toString(), reading); PdfDocument document = new PdfDocument(reader)) {
            assertEquals(27, reader.getCryptoMode()); assertEquals(PdfVersion.PDF_2_0, document.getPdfVersion());
            assertArrayEquals(payload(), document.getFileAttachment("proof.txt", 4096).get().getContent());
        }
    }

    private interface Action { void run(); }
    private static void expect(DocumentFailureCode expected, Action action) {
        try { action.run(); fail("Expected " + expected); }
        catch (PdfException failure) { assertTrue(failure.getCause() instanceof DocumentFailure); assertEquals(expected, ((DocumentFailure) failure.getCause()).getCode()); }
    }
    private static WriterProperties writing(int selector) { return new WriterProperties().setStandardEncryption(USER, OWNER,
            EncryptionConstants.ALLOW_COPY | EncryptionConstants.ALLOW_SCREENREADERS, selector)
            .setLegacySecurityMode(LegacySecurityMode.ALLOW_OBSOLETE_PASSWORD_ENCRYPTION); }
    private static Path file(String name) { return CORPUS.resolve(name + ".pdf"); }
    private static byte[] payload() throws IOException { return Files.readAllBytes(CORPUS.resolve("attachment.txt")); }
    private static final class BorrowedInput extends ByteArrayInputStream { boolean closed; BorrowedInput(byte[] bytes) { super(bytes); } @Override public void close() { closed = true; } }
    private static final class BorrowedOutput extends ByteArrayOutputStream { boolean closed; @Override public void close() { closed = true; } }
}
