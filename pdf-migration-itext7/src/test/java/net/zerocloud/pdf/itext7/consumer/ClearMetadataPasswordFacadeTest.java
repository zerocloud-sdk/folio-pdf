package net.zerocloud.pdf.itext7.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import net.zerocloud.pdf.LegacySecurityMode;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;
import net.zerocloud.pdf.itext7.kernel.pdf.EncryptionConstants;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfVersion;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;
import net.zerocloud.pdf.itext7.kernel.pdf.ReaderProperties;
import net.zerocloud.pdf.itext7.kernel.pdf.StampingProperties;
import net.zerocloud.pdf.itext7.kernel.pdf.WriterProperties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Real reader/writer calls; the Migration Facade executes IN_PROCESS. */
public final class ClearMetadataPasswordFacadeTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private static final Path CORPUS = Paths.get(System.getProperty("repositoryRoot", ".."))
            .resolve("capabilities/profiles/T79-clear-metadata");
    private static final byte[] USER = "baseline-user".getBytes(StandardCharsets.UTF_8);
    private static final byte[] OWNER = "baseline-owner".getBytes(StandardCharsets.UTF_8);

    @Test
    public void admittedSelectorsCreateReopenAndExplicitlyRewriteClearMetadata() throws Exception {
        assertEquals(8, EncryptionConstants.DO_NOT_ENCRYPT_METADATA);
        for (int algorithm : new int[] {1, 2, 3}) {
            int selector = algorithm | EncryptionConstants.DO_NOT_ENCRYPT_METADATA;
            for (PdfVersion version : new PdfVersion[] {PdfVersion.PDF_1_7, PdfVersion.PDF_2_0}) {
                if (version == PdfVersion.PDF_2_0 && algorithm != 3) { continue; }
                Path target = temporary.newFile().toPath();
                PdfDocument created;
                try (WriterProperties writing = writing(selector, USER, OWNER).setPdfVersion(version)) {
                    created = new PdfDocument(new PdfWriter(target.toString(), writing));
                    try (PdfDocument document = created) {
                        document.addNewPage();
                        document.setXmpMetadata(Files.readAllBytes(CORPUS.resolve("metadata.xmp")));
                        document.getDocumentInfo().setTitle("Protected clear-metadata Info");
                    }
                }
                assertEquals(PublicationStatus.COMMITTED, created.getPublicationReceipts().get(0).getStatus());
                try (ReaderProperties reading = new ReaderProperties().setPassword(USER);
                        PdfReader reader = new PdfReader(target.toString(), reading); PdfDocument document = new PdfDocument(reader)) {
                    assertEquals(selector, reader.getCryptoMode());
                    assertFalse(reader.isOpenedWithFullPermission());
                    assertEquals(version, document.getPdfVersion());
                    assertEquals(0xfffff2d0L, reader.getPermissions());
                    assertArrayEquals(Files.readAllBytes(CORPUS.resolve("metadata.xmp")), document.getXmpMetadata());
                    assertEquals("Protected clear-metadata Info", document.getDocumentInfo().getTitle());
                }
                Path rewritten = temporary.newFile().toPath();
                try (ReaderProperties reading = new ReaderProperties().setPassword(OWNER);
                        WriterProperties writing = writing(11, USER, OWNER).setPdfVersion(version);
                        PdfReader reader = new PdfReader(target.toString(), reading);
                        PdfDocument document = new PdfDocument(reader, new PdfWriter(rewritten.toString(), writing))) {
                    assertTrue(reader.isOpenedWithFullPermission());
                }
                try (ReaderProperties reading = new ReaderProperties().setPassword(USER);
                        PdfReader reader = new PdfReader(rewritten.toString(), reading)) { assertEquals(11, reader.getCryptoMode()); }
            }
        }
    }

    @Test
    public void originalClearMetadataR4R5R6InputsExposeActualReaderChoices() throws Exception {
        String[] names = {"rc4-cf", "aes-128", "aes-256-r5", "aes-256-r6", "aes-256-pdf20"};
        for (int index = 0; index < names.length; index++) {
            try (ReaderProperties reading = new ReaderProperties().setPassword(OWNER);
                    PdfReader reader = new PdfReader(CORPUS.resolve(names[index] + ".pdf").toString(), reading);
                    PdfDocument document = new PdfDocument(reader)) {
                assertEquals(index == 0 ? 9 : index == 1 ? 10 : 11, reader.getCryptoMode());
                assertTrue(reader.isOpenedWithFullPermission());
                assertArrayEquals(Files.readAllBytes(CORPUS.resolve("metadata.xmp")), document.getXmpMetadata());
                assertEquals("Folio baseline proof", document.getDocumentInfo().getTitle());
            }
        }
    }

    @Test
    public void copiedPropertiesKeepScopeEncodingAndBorrowedStreamsAlive() throws Exception {
        for (int selector : new int[] {9, 10, 11}) {
            byte[] password = (selector == 11 ? "I\u00adX\u00a0\u2168" : "\u00e9\u0080").getBytes(
                    selector == 11 ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1);
            byte[] original = password.clone();
            BorrowedOutput output = new BorrowedOutput();
            PdfWriter writer;
            try (WriterProperties properties = writing(selector, password, OWNER)) { writer = new PdfWriter(output, properties); }
            Arrays.fill(password, (byte) 0);
            try (PdfDocument document = new PdfDocument(writer)) {
                document.addNewPage(); document.setXmpMetadata(Files.readAllBytes(CORPUS.resolve("metadata.xmp")));
            }
            assertFalse(output.closed);
            BorrowedInput input = new BorrowedInput(output.toByteArray());
            PdfReader reader;
            try (ReaderProperties properties = new ReaderProperties().setPassword(original)) { reader = new PdfReader(input, properties); }
            Arrays.fill(original, (byte) 0);
            try (PdfDocument document = new PdfDocument(reader)) {
                assertEquals(selector, reader.getCryptoMode());
                assertArrayEquals(Files.readAllBytes(CORPUS.resolve("metadata.xmp")), document.getXmpMetadata());
            }
            assertFalse(input.closed);
        }
    }

    @Test
    public void contradictorySelectorsAndMissingLegacyOptInFailSafely() throws Exception {
        try (WriterProperties properties = writing(11, USER, OWNER)) {
            for (int invalid : new int[] {-1, 4, 8, 12, 16, 24, 32}) {
                try { properties.setStandardEncryption(USER, OWNER, 0, invalid); fail("Invalid selector must fail"); }
                catch (IllegalArgumentException expected) { assertFalse(expected.getMessage().contains("baseline")); }
            }
            Path valid = temporary.newFile().toPath();
            try (PdfDocument document = new PdfDocument(new PdfWriter(valid.toString(), properties))) { document.addNewPage(); }
            try (ReaderProperties reading = new ReaderProperties().setPassword(USER);
                    PdfReader reader = new PdfReader(valid.toString(), reading)) { assertEquals(11, reader.getCryptoMode()); }
        }
        for (int selector : new int[] {9, 10}) {
            Path target = temporary.newFile().toPath(); byte[] marker = {7, 8, 9}; Files.write(target, marker);
            try (WriterProperties properties = new WriterProperties().setStandardEncryption(USER, OWNER, 16, selector)) {
                PdfDocument document = new PdfDocument(new PdfWriter(target.toString(), properties)); document.addNewPage();
                try { document.close(); fail("Legacy output needs opt-in"); }
                catch (PdfException failure) {
                    DocumentFailure cause = (DocumentFailure) failure.getCause();
                    assertEquals(DocumentFailureCode.LEGACY_SECURITY_MODE_REQUIRED, cause.getCode());
                    assertEquals(PublicationStatus.NOT_ATTEMPTED, cause.getPublicationReceipts().get(0).getStatus());
                    assertFalse(cause.getDiagnostic().contains(target.toString()));
                }
            }
            assertArrayEquals(marker, Files.readAllBytes(target));
        }
    }

    @Test
    public void appendKeepsTheOriginalClearMetadataScopeAndCiphertextPrefix() throws Exception {
        Path source = CORPUS.resolve("aes-256-r6-assembly.pdf"), target = temporary.newFile().toPath();
        try (ReaderProperties properties = new ReaderProperties().setPassword(USER);
                PdfReader reader = new PdfReader(source.toString(), properties);
                PdfDocument document = new PdfDocument(reader, new PdfWriter(target.toString()), new StampingProperties().useAppendMode())) {
            assertFalse(reader.isOpenedWithFullPermission()); document.addNewPage();
        }
        assertArrayEquals(Files.readAllBytes(source), Arrays.copyOf(Files.readAllBytes(target), (int) Files.size(source)));
        try (ReaderProperties properties = new ReaderProperties().setPassword(USER);
                PdfReader reader = new PdfReader(target.toString(), properties); PdfDocument document = new PdfDocument(reader)) {
            assertEquals(11, reader.getCryptoMode()); assertEquals(2, document.getNumberOfPages());
            assertArrayEquals(Files.readAllBytes(CORPUS.resolve("metadata.xmp")), document.getXmpMetadata());
        }
    }

    @Test
    public void missingInvalidAndDestroyedCredentialsFailWithoutLeakingSecrets() throws Exception {
        Path source = CORPUS.resolve("aes-256-r6.pdf");
        for (byte[] password : new byte[][] {null, "private-T79-wrong".getBytes(StandardCharsets.UTF_8)}) {
            try (ReaderProperties reading = new ReaderProperties().setPassword(password)) {
                try { new PdfReader(source.toString(), reading).close(); fail("Expected credential rejection"); }
                catch (IOException failure) {
                    assertEquals(password == null ? DocumentFailureCode.CREDENTIAL_REQUIRED : DocumentFailureCode.CREDENTIAL_REJECTED,
                            ((DocumentFailure) failure.getCause()).getCode());
                    assertFalse(failure.toString().contains(source.toString()));
                    assertFalse(failure.toString().contains("private-T79"));
                }
            }
        }
        Path target = temporary.newFile().toPath(); byte[] marker = {1, 3, 5}; Files.write(target, marker);
        try (WriterProperties writing = new WriterProperties().setStandardEncryption(new byte[] {(byte) 0xc0, (byte) 0xaf}, OWNER, 0, 11)) {
            try { new PdfDocument(new PdfWriter(target.toString(), writing)).close(); fail("Invalid UTF-8 must fail before publication"); }
            catch (IllegalArgumentException failure) { assertFalse(failure.toString().contains("baseline")); }
        }
        assertArrayEquals(marker, Files.readAllBytes(target));
        net.zerocloud.pdf.PasswordCredential destroyed = net.zerocloud.pdf.PasswordCredential.of("baseline-user".toCharArray());
        try (ReaderProperties reading = new ReaderProperties().setCredential(destroyed)) {
            destroyed.close();
            try { new PdfReader(source.toString(), reading).close(); fail("Destroyed credentials must fail"); }
            catch (IOException failure) { assertEquals(DocumentFailureCode.CREDENTIAL_DESTROYED, ((DocumentFailure) failure.getCause()).getCode()); }
        }
    }

    @Test
    public void unrestrictedUsersAreNotOwnersAndRewriteRequiresAnExplicitPolicy() throws Exception {
        Path source = CORPUS.resolve("aes-256-unrestricted.pdf");
        for (byte[] password : new byte[][] {USER, OWNER}) {
            Path target = temporary.newFile().toPath(); byte[] marker = {5, 7}; Files.write(target, marker);
            try (ReaderProperties reading = new ReaderProperties().setPassword(password);
                    PdfReader reader = new PdfReader(source.toString(), reading)) {
                assertEquals(password == OWNER, reader.isOpenedWithFullPermission());
                PdfDocument document = new PdfDocument(reader, new PdfWriter(target.toString()));
                try { document.close(); fail("Protected rewrite needs owner authority and an explicit policy"); }
                catch (PdfException failure) {
                    DocumentFailure cause = (DocumentFailure) failure.getCause();
                    assertEquals(password == USER ? DocumentFailureCode.DOCUMENT_PERMISSION_DENIED
                            : DocumentFailureCode.PASSWORD_SECURITY_POLICY_REQUIRED, cause.getCode());
                    assertEquals(PublicationStatus.NOT_ATTEMPTED, cause.getPublicationReceipts().get(0).getStatus());
                    assertFalse(cause.getDiagnostic().contains(target.toString()));
                }
            }
            assertArrayEquals(marker, Files.readAllBytes(target));
        }
    }

    private static WriterProperties writing(int selector, byte[] user, byte[] owner) {
        WriterProperties properties = new WriterProperties().setStandardEncryption(user, owner,
                EncryptionConstants.ALLOW_COPY | EncryptionConstants.ALLOW_SCREENREADERS, selector);
        if ((selector & 3) != 3) { properties.setLegacySecurityMode(LegacySecurityMode.ALLOW_OBSOLETE_PASSWORD_ENCRYPTION); }
        return properties;
    }
    private static final class BorrowedInput extends ByteArrayInputStream {
        boolean closed;
        BorrowedInput(byte[] bytes) { super(bytes); }
        @Override public void close() { closed = true; }
    }
    private static final class BorrowedOutput extends ByteArrayOutputStream {
        boolean closed;
        @Override public void close() { closed = true; }
    }
}
