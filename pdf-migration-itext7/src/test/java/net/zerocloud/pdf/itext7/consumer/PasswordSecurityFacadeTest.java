package net.zerocloud.pdf.itext7.consumer;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.LegacySecurityMode;
import net.zerocloud.pdf.PasswordCredential;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;
import net.zerocloud.pdf.itext7.kernel.pdf.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Actual Facade calls always execute IN_PROCESS, including in a Worker certification tuple. */
public final class PasswordSecurityFacadeTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private static final byte[] SENTINEL = {9, 8, 7};
    private static final byte[] OWNER = "facade-owner".getBytes(StandardCharsets.UTF_8);
    private static final byte[] USER = "facade-user".getBytes(StandardCharsets.UTF_8);
    private static final int ALL = 0xf3c;

    @Test public void independentlyAuthoredInputsUseTheMatchingReaderContract() throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("capabilities/profiles/T78-password"))) { root = root.getParent(); }
        assertNotNull("The independent security corpus is available", root);
        Path corpus = root.resolve("capabilities/profiles/T78-password");
        Properties cases = new Properties();
        try (InputStream input = Files.newInputStream(corpus.resolve("cases.properties"))) { cases.load(input); }
        for (String name : cases.stringPropertyNames()) {
            if (!"success".equals(cases.getProperty(name))) { continue; }
            Path source = corpus.resolve(name + ".pdf");
            boolean encrypted = !name.startsWith("version-");
            boolean prepared = name.endsWith("-prepared");
            boolean ambiguous = "aes-256-r5-noncanonical-owner".equals(name);
            byte[] owner = (prepared ? "owner-\u2168" : ambiguous ? "I\u00adX" : originalCredential(name, true)).getBytes(StandardCharsets.UTF_8);
            try (ReaderProperties properties = new ReaderProperties()) {
                if (encrypted) { properties.setPassword(owner); }
                try (PdfReader reader = new PdfReader(source.toString(), properties); PdfDocument document = new PdfDocument(reader)) {
                    assertEquals(name, encrypted, reader.isEncrypted());
                    assertEquals(name, !ambiguous, reader.isOpenedWithFullPermission());
                    assertEquals(name, 1, document.getNumberOfPages());
                    if (!ambiguous) { assertEquals("Folio baseline proof", document.getDocumentInfo().getTitle()); }
                }
            }
            if (encrypted) {
                byte[] user = (prepared || ambiguous ? "I\u00adX" : name.equals("legacy-literal-question") ? "?" : originalCredential(name, false))
                        .getBytes(name.endsWith("-byte-boundary") ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8);
                try (ReaderProperties properties = new ReaderProperties().setPassword(user);
                        PdfReader reader = new PdfReader(source.toString(), properties)) {
                    assertEquals(name, name.endsWith("-equal"), reader.isOpenedWithFullPermission());
                }
                rejectReader(source, null, DocumentFailureCode.CREDENTIAL_REQUIRED);
            }
        }
    }

    private static String originalCredential(String name, boolean owner) {
        if (name.endsWith("-equal")) { return "equal-baseline"; }
        if (owner) { return name.endsWith("-empty-owner") ? "" : "baseline-owner"; }
        if (name.endsWith("-empty-user")) { return ""; }
        if (name.endsWith("-boundary-split")) { return repeat('a', 126) + "\u00e9"; }
        if (name.endsWith("-bidi")) { return "\u05d0\u05d1"; }
        if (name.endsWith("-byte-boundary")) { return "\u0080" + repeat('\u00e9', 32); }
        return "baseline-user";
    }

    @Test public void versionsKeepExactMappedValuesAndReopenedOutputDeclarations() throws Exception {
        for (PdfVersion version : new PdfVersion[] {PdfVersion.PDF_1_0, PdfVersion.PDF_1_1, PdfVersion.PDF_1_2,
                PdfVersion.PDF_1_3, PdfVersion.PDF_1_4, PdfVersion.PDF_1_5, PdfVersion.PDF_1_6, PdfVersion.PDF_1_7, PdfVersion.PDF_2_0}) {
            assertEquals(version, PdfVersion.fromString(version.toString()));
            assertEquals(version, PdfVersion.fromPdfName(version.toPdfName()));
            try (PdfReader reader = new PdfReader(new ByteArrayInputStream(plain(version.toPdfName().getValue())));
                    PdfDocument document = new PdfDocument(reader)) {
                assertEquals(version, document.getPdfVersion());
                assertFalse(reader.isEncrypted());
                assertTrue(reader.isOpenedWithFullPermission());
                assertEquals(0L, reader.getPermissions());
                assertEquals(-1, reader.getCryptoMode());
            }
        }
        assertTrue(PdfVersion.PDF_1_7.compareTo(PdfVersion.PDF_2_0) < 0);
        for (PdfVersion version : new PdfVersion[] {PdfVersion.PDF_1_7, PdfVersion.PDF_2_0}) {
            Path output = path();
            try (WriterProperties choices = new WriterProperties().setPdfVersion(version);
                    PdfDocument document = new PdfDocument(new PdfWriter(output.toString(), choices))) {
                document.addNewPage();
                assertEquals(version, document.getPdfVersion());
            }
            try (PdfReader reader = new PdfReader(output.toString()); PdfDocument document = new PdfDocument(reader)) {
                assertEquals(version, document.getPdfVersion());
            }
        }
    }

    @Test public void everyAlgorithmAndPermissionFlagHasMatchingReopenedObservations() throws Exception {
        for (int algorithm = 0; algorithm < 4; algorithm++) {
            for (int permissions : new int[] {0, 4, 8, 16, 32, 256, 512, 1024, 2052, ALL}) {
                Path output = create(algorithm, permissions, USER, OWNER, PdfVersion.PDF_1_7, true);
                try (ReaderProperties properties = new ReaderProperties().setPassword(USER);
                        PdfReader user = new PdfReader(output.toString(), properties)) {
                    assertTrue(user.isEncrypted());
                    assertFalse(user.isOpenedWithFullPermission());
                    assertEquals(algorithm, user.getCryptoMode());
                    assertEquals((0xfffff0c0L | permissions), user.getPermissions());
                }
                try (ReaderProperties properties = new ReaderProperties().setPassword(OWNER);
                        PdfReader owner = new PdfReader(output.toString(), properties)) {
                    assertTrue(owner.isOpenedWithFullPermission());
                    assertEquals((0xfffff0c0L | permissions), owner.getPermissions());
                }
            }
        }
    }

    @Test public void copiedPropertiesAndEncodedPasswordsRetainExplicitOwnership() throws Exception {
        byte[] owner = OWNER.clone();
        byte[] user = USER.clone();
        Path output = path();
        PdfWriter writer;
        try (WriterProperties properties = new WriterProperties().setStandardEncryption(user, owner, ALL, 3)) {
            writer = new PdfWriter(output.toString(), properties);
            Arrays.fill(owner, (byte) 'x'); Arrays.fill(user, (byte) 'x');
            properties.setPdfVersion(PdfVersion.PDF_2_0);
        }
        try (PdfDocument document = new PdfDocument(writer)) { document.addNewPage(); }
        byte[] opening = USER.clone();
        PdfReader reader;
        try (ReaderProperties properties = new ReaderProperties().setPassword(opening)) {
            Arrays.fill(opening, (byte) 'x');
            reader = new PdfReader(output.toString(), properties);
        }
        try (PdfDocument document = new PdfDocument(reader)) {
            assertEquals(PdfVersion.PDF_1_7, document.getPdfVersion());
            assertEquals(1, document.getNumberOfPages());
        }
        reader.close();
        try { reader.isEncrypted(); fail(); } catch (IllegalStateException expected) { }
    }

    @Test public void rawLegacyBytesAndUnicodeAesCredentialsSelectOnlyTheirAuthenticatedRevision() throws Exception {
        byte[][] values = {{(byte) 0xe9}, {(byte) 0xc3, (byte) 0xa9}, {(byte) 0x80, 0, (byte) 0xff}};
        for (int algorithm = 0; algorithm < 3; algorithm++) {
            for (byte[] user : values) {
                Path output = create(algorithm, ALL, user, OWNER, PdfVersion.PDF_1_7, true);
                try (ReaderProperties properties = new ReaderProperties().setPassword(user);
                        PdfReader reader = new PdfReader(output.toString(), properties)) {
                    assertFalse(reader.isOpenedWithFullPermission());
                }
            }
            Path differentBytes = create(algorithm, ALL, new byte[] {(byte) 0xe9}, OWNER, PdfVersion.PDF_1_7, true);
            rejectReader(differentBytes, new byte[] {(byte) 0xc3, (byte) 0xa9}, DocumentFailureCode.CREDENTIAL_REJECTED);
        }
        for (String value : new String[] {"caf\u00e9", "I\u00adX", "\u2168", "\u00a0space", "\u0627\u0628"}) {
            byte[] user = value.getBytes(StandardCharsets.UTF_8);
            Path output = create(3, ALL, user, OWNER, PdfVersion.PDF_1_7, false);
            try (ReaderProperties properties = new ReaderProperties().setPassword(user);
                    PdfReader reader = new PdfReader(output.toString(), properties)) {
                assertFalse(reader.isOpenedWithFullPermission());
                assertEquals(3, reader.getCryptoMode());
            }
        }
    }

    @Test public void emptyEqualAndBoundaryPasswordsAreSuccessfulCases() throws Exception {
        for (int algorithm = 0; algorithm < 4; algorithm++) {
            for (int length : new int[] {0, 31, 32, 33, 126, 127, 128}) {
                byte[] user = new byte[length]; Arrays.fill(user, (byte) 'u');
                Path output = create(algorithm, ALL, user, OWNER, PdfVersion.PDF_1_7, true);
                try (ReaderProperties properties = new ReaderProperties().setPassword(user);
                        PdfReader reader = new PdfReader(output.toString(), properties)) { assertTrue(reader.isEncrypted()); }
                rejectReader(output, null, DocumentFailureCode.CREDENTIAL_REQUIRED);
            }
            Path equal = create(algorithm, ALL, USER, USER, PdfVersion.PDF_1_7, true);
            try (ReaderProperties properties = new ReaderProperties().setPassword(USER);
                    PdfReader reader = new PdfReader(equal.toString(), properties)) { assertTrue(reader.isOpenedWithFullPermission()); }
            Path emptyOwner = create(algorithm, 0, USER, null, PdfVersion.PDF_1_7, true);
            rejectReader(emptyOwner, new byte[0], DocumentFailureCode.CREDENTIAL_REJECTED);
            try (ReaderProperties properties = new ReaderProperties().setPassword(USER);
                    PdfReader reader = new PdfReader(emptyOwner.toString(), properties)) { assertFalse(reader.isOpenedWithFullPermission()); }
        }
    }

    @Test public void unrestrictedUsersCannotRekeyAndOwnerCannotAccidentallyPublishPlaintext() throws Exception {
        Path source = create(3, ALL, USER, OWNER, PdfVersion.PDF_1_7, false);
        for (byte[] password : new byte[][] {USER, OWNER}) {
            Path target = path(); Files.write(target, SENTINEL);
            try (ReaderProperties properties = new ReaderProperties().setPassword(password);
                    PdfReader reader = new PdfReader(source.toString(), properties)) {
                PdfDocument document = new PdfDocument(reader, new PdfWriter(target.toString()));
                try { document.close(); fail(); }
                catch (PdfException failure) {
                    assertFailure(failure, password == USER ? DocumentFailureCode.DOCUMENT_PERMISSION_DENIED
                            : DocumentFailureCode.PASSWORD_SECURITY_POLICY_REQUIRED, 1);
                    assertEquals(PublicationStatus.NOT_ATTEMPTED, document.getPublicationReceipts().get(0).getStatus());
                }
            }
            assertArrayEquals(SENTINEL, Files.readAllBytes(target));
        }
    }

    @Test public void protectedRewriteAndIncrementalKeepEncryptionAndReopen() throws Exception {
        Path source = create(3, ALL, USER, OWNER, PdfVersion.PDF_1_7, false);
        for (boolean append : new boolean[] {false, true}) {
            Path target = path();
            try (ReaderProperties opening = new ReaderProperties().setPassword(OWNER);
                    WriterProperties output = append ? new WriterProperties() : secure(3, ALL, USER, OWNER, false);
                    PdfReader reader = new PdfReader(source.toString(), opening);
                    PdfDocument document = new PdfDocument(reader, new PdfWriter(target.toString(), output),
                            append ? new StampingProperties().useAppendMode() : new StampingProperties())) {
                document.addNewPage();
            }
            if (append) { assertArrayEquals(Files.readAllBytes(source), Arrays.copyOf(Files.readAllBytes(target), (int) Files.size(source))); }
            try (ReaderProperties opening = new ReaderProperties().setPassword(USER);
                    PdfReader reader = new PdfReader(target.toString(), opening); PdfDocument document = new PdfDocument(reader)) {
                assertTrue(reader.isEncrypted()); assertEquals(2, document.getNumberOfPages());
            }
        }
    }

    @Test public void legacyOptInIsExplicitAndCannotChangeASubsequentRequest() throws Exception {
        for (int algorithm = 0; algorithm < 3; algorithm++) {
            Path target = path(); Files.write(target, SENTINEL);
            try (WriterProperties output = secure(algorithm, ALL, USER, OWNER, false)) {
                PdfDocument document = new PdfDocument(new PdfWriter(target.toString(), output));
                document.addNewPage();
                try { document.close(); fail(); }
                catch (PdfException failure) { assertFailure(failure, DocumentFailureCode.LEGACY_SECURITY_MODE_REQUIRED, 1); }
            }
            assertArrayEquals(SENTINEL, Files.readAllBytes(target));
            create(algorithm, ALL, USER, OWNER, PdfVersion.PDF_1_7, true);
        }
        Path secure = create(3, ALL, USER, OWNER, PdfVersion.PDF_1_7, true);
        try (ReaderProperties properties = new ReaderProperties().setPassword(USER);
                PdfReader reader = new PdfReader(secure.toString(), properties)) { assertEquals(3, reader.getCryptoMode()); }
    }

    @Test public void borrowedCredentialsAndStreamsRemainCallerOwnedOnSuccessAndFailure() throws Exception {
        Path source = create(3, ALL, USER, OWNER, PdfVersion.PDF_1_7, false);
        class Input extends ByteArrayInputStream {
            boolean closed;
            Input(byte[] bytes) { super(bytes); }
            @Override public void close() { closed = true; }
        }
        Input input = new Input(Files.readAllBytes(source));
        try (PasswordCredential credential = PasswordCredential.of("facade-owner".toCharArray());
                ReaderProperties properties = new ReaderProperties().setCredential(credential)) {
            PdfReader reader = new PdfReader(input, properties);
            properties.close(); assertFalse(credential.isDestroyed()); assertFalse(input.closed);
            Path target = path(); Files.write(target, SENTINEL);
            credential.close();
            PdfDocument document = new PdfDocument(reader, new PdfWriter(target.toString()), new StampingProperties().useAppendMode());
            try { document.close(); fail(); }
            catch (PdfException failure) { assertFailure(failure, DocumentFailureCode.CREDENTIAL_DESTROYED, 1); }
            assertArrayEquals(SENTINEL, Files.readAllBytes(target)); assertFalse(input.closed);
        }
        rejectReader(source, "wrong".getBytes(StandardCharsets.UTF_8), DocumentFailureCode.CREDENTIAL_REJECTED);
    }

    @Test public void namedProtectedPdf20DonorCannotBeDowngradedOrUnprotected() throws Exception {
        Path primary = create(3, ALL, USER, OWNER, PdfVersion.PDF_1_7, false);
        Path donor = create(3, ALL, USER, OWNER, PdfVersion.PDF_2_0, false);
        Path first = path(), second = path(); Files.write(first, SENTINEL); Files.write(second, SENTINEL);
        try (ReaderProperties opening = new ReaderProperties().setPassword(OWNER);
                PdfReader left = new PdfReader(primary.toString(), opening);
                PdfReader right = new PdfReader(donor.toString(), opening);
                WriterProperties protection = secure(3, ALL, USER, OWNER, false)) {
            Map<String, PdfReader> sources = new LinkedHashMap<String, PdfReader>(); sources.put("primary", left); sources.put("donor", right);
            Map<String, PdfWriter> targets = new LinkedHashMap<String, PdfWriter>();
            targets.put("first", new PdfWriter(first.toString(), protection)); targets.put("second", new PdfWriter(second.toString(), protection));
            PdfDocument document = new PdfDocument(sources, "primary", targets);
            try { document.close(); fail(); }
            catch (PdfException failure) { assertFailure(failure, DocumentFailureCode.PDF_VERSION_UNSUPPORTED, 2); }
            assertEquals("first", document.getPublicationReceipts().get(0).getTargetName());
            assertEquals("second", document.getPublicationReceipts().get(1).getTargetName());
        }
        assertArrayEquals(SENTINEL, Files.readAllBytes(first)); assertArrayEquals(SENTINEL, Files.readAllBytes(second));
    }

    private Path create(int algorithm, int permissions, byte[] user, byte[] owner, PdfVersion version, boolean legacy) throws Exception {
        Path output = path();
        try (WriterProperties properties = secure(algorithm, permissions, user, owner, legacy).setPdfVersion(version);
                PdfDocument document = new PdfDocument(new PdfWriter(output.toString(), properties))) { document.addNewPage(); }
        return output;
    }
    private static WriterProperties secure(int algorithm, int permissions, byte[] user, byte[] owner, boolean legacy) {
        WriterProperties properties = new WriterProperties().setStandardEncryption(user, owner, permissions, algorithm);
        if (legacy) { properties.setLegacySecurityMode(LegacySecurityMode.ALLOW_OBSOLETE_PASSWORD_ENCRYPTION); }
        return properties;
    }
    private Path path() throws IOException { return temporary.newFile().toPath(); }
    private static String repeat(char value, int count) {
        char[] result = new char[count];
        java.util.Arrays.fill(result, value);
        return new String(result);
    }
    private static void assertFailure(PdfException failure, DocumentFailureCode code, int targets) {
        assertTrue(failure.getCause() instanceof DocumentFailure);
        DocumentFailure nativeFailure = (DocumentFailure) failure.getCause();
        assertEquals(code, nativeFailure.getCode()); assertEquals("document.version-password-security", nativeFailure.getCapabilityId());
        assertEquals(targets, nativeFailure.getPublicationReceipts().size());
        nativeFailure.getPublicationReceipts().forEach(receipt -> assertEquals(PublicationStatus.NOT_ATTEMPTED, receipt.getStatus()));
        assertFalse(failure.toString().contains("facade-owner")); assertFalse(failure.toString().contains("facade-user"));
        assertNull(nativeFailure.getCause());
    }
    private static void rejectReader(Path source, byte[] password, DocumentFailureCode code) throws Exception {
        try (ReaderProperties properties = new ReaderProperties().setPassword(password)) {
            try { new PdfReader(source.toString(), properties).close(); fail(); }
            catch (IOException failure) {
                assertTrue(failure.getCause() instanceof DocumentFailure);
                assertEquals(code, ((DocumentFailure) failure.getCause()).getCode());
                assertFalse(failure.toString().contains(source.toString()));
                assertFalse(failure.toString().contains("facade-user"));
            }
        }
    }
    private static byte[] plain(String version) throws IOException {
        String[] objects = {"<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 72 72] /Resources << >> >>"};
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(("%PDF-" + version + "\n").getBytes(StandardCharsets.US_ASCII));
        int[] positions = new int[4];
        for (int index = 0; index < objects.length; index++) {
            positions[index + 1] = output.size();
            output.write(((index + 1) + " 0 obj\n" + objects[index] + "\nendobj\n").getBytes(StandardCharsets.US_ASCII));
        }
        int xref = output.size(); output.write("xref\n0 4\n0000000000 65535 f \n".getBytes(StandardCharsets.US_ASCII));
        for (int index = 1; index <= 3; index++) { output.write(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", positions[index]).getBytes(StandardCharsets.US_ASCII)); }
        output.write(("trailer\n<< /Root 1 0 R /Size 4 >>\nstartxref\n" + xref + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII));
        return output.toByteArray();
    }
}
