package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.zerocloud.pdf.CredentialAuthority;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentPermissions;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.LegacySecurityMode;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfVersion;
import net.zerocloud.pdf.PdfVersionInfo;
import net.zerocloud.pdf.PdfOutputPolicy;
import net.zerocloud.pdf.PdfString;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.PasswordCredential;
import net.zerocloud.pdf.PasswordEncryptionAlgorithm;
import net.zerocloud.pdf.PasswordEncryptionScope;
import net.zerocloud.pdf.PasswordSecurityInfo;
import net.zerocloud.pdf.PasswordSecurityPolicy;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.command.FlattenAnnotations;
import net.zerocloud.pdf.command.InsertBlankPage;
import net.zerocloud.pdf.command.MergeDocuments;
import net.zerocloud.pdf.command.ReplaceOutlineTree;
import net.zerocloud.pdf.command.SplitDocument;
import net.zerocloud.pdf.command.UpdateActions;
import net.zerocloud.pdf.command.UpdateAnnotations;
import net.zerocloud.pdf.command.UpdateDocumentInfo;
import net.zerocloud.pdf.query.Annotations;
import net.zerocloud.pdf.query.DocumentInfo;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.PageObjectReference;
import net.zerocloud.pdf.query.DocumentVersion;
import net.zerocloud.pdf.query.DocumentSecurity;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class PdfVersionPasswordSecurityWorkflowTest {

    private static final String CAPABILITY =
            "document.version-password-security";
    private static final byte[] PASSWORD_PADDING = new byte[] {
        0x28, (byte) 0xbf, 0x4e, 0x5e, 0x4e, 0x75, (byte) 0x8a, 0x41,
        0x64, 0x00, 0x4e, 0x56, (byte) 0xff, (byte) 0xfa, 0x01, 0x08,
        0x2e, 0x2e, 0x00, (byte) 0xb6, (byte) 0xd0, 0x68, 0x3e,
        (byte) 0x80, 0x2f, 0x0c, (byte) 0xa9, (byte) 0xfe, 0x64,
        0x53, 0x69, 0x7a
    };

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void supportedHeaderVersionsAreReportedThroughTheWorkflow()
            throws Exception {
        for (PdfVersion expected : PdfVersion.values()) {
            byte[] fixture = minimalPdf(expected, null);

            PdfVersionInfo actual = new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source(
                                    "primary",
                                    DocumentSource.bytes(
                                            fixture,
                                            fixture.length))
                            .primarySource("primary")
                            .saveMode(SaveMode.REWRITE)
                            .build(),
                    session -> session.query(DocumentVersion.INSTANCE))
                    .getResult();

            assertEquals(expected, actual.getHeaderVersion());
            assertFalse(actual.getCatalogVersion().isPresent());
            assertEquals(expected, actual.getEffectiveVersion());
        }

        byte[] pdf20 = minimalPdf(PdfVersion.PDF_2_0, null);
        byte[] prefixedPdf20 = new byte[pdf20.length + 8];
        Arrays.fill(prefixedPdf20, 0, 8, (byte) 'x');
        System.arraycopy(pdf20, 0, prefixedPdf20, 8, pdf20.length);
        assertEquals(
                PdfVersion.PDF_2_0,
                versionOf(prefixedPdf20).getEffectiveVersion());
    }

    @Test
    public void catalogVersionUsesTheLaterSupportedDeclaration()
            throws Exception {
        PdfVersionInfo upgraded = versionOf(minimalPdf(
                PdfVersion.PDF_1_4,
                "1.7"));
        assertEquals(PdfVersion.PDF_1_4, upgraded.getHeaderVersion());
        assertEquals(
                PdfVersion.PDF_1_7,
                upgraded.getCatalogVersion().get());
        assertEquals(PdfVersion.PDF_1_7, upgraded.getEffectiveVersion());

        PdfVersionInfo retained = versionOf(minimalPdf(
                PdfVersion.PDF_2_0,
                "1.7"));
        assertEquals(PdfVersion.PDF_2_0, retained.getHeaderVersion());
        assertEquals(
                PdfVersion.PDF_1_7,
                retained.getCatalogVersion().get());
        assertEquals(PdfVersion.PDF_2_0, retained.getEffectiveVersion());
    }

    @Test
    public void invalidVersionDeclarationsFailBeforeWorkOrPublication()
            throws Exception {
        assertVersionFailure(
                minimalPdf("1.x", null),
                DocumentFailureCode.PDF_VERSION_INVALID);
        assertVersionFailure(
                minimalPdf("1.8", null),
                DocumentFailureCode.PDF_VERSION_UNSUPPORTED);
        assertVersionFailure(
                minimalPdf("1.", null),
                DocumentFailureCode.PDF_VERSION_INVALID);
        assertVersionFailure(
                minimalPdf("1-7", null),
                DocumentFailureCode.PDF_VERSION_INVALID);
        assertVersionFailure(
                minimalPdf("1.70", null),
                DocumentFailureCode.PDF_VERSION_INVALID);
        assertVersionFailure(
                minimalPdf(PdfVersion.PDF_1_7, "1.x"),
                DocumentFailureCode.PDF_VERSION_INVALID);
        assertVersionFailure(
                minimalPdf(PdfVersion.PDF_1_7, "2.1"),
                DocumentFailureCode.PDF_VERSION_UNSUPPORTED);
        assertVersionFailure(
                minimalPdf(
                        PdfVersion.PDF_1_7,
                        null,
                        " /Version 17"),
                DocumentFailureCode.PDF_VERSION_INVALID);
        byte[] pdf17 = minimalPdf(PdfVersion.PDF_1_7, null);
        byte[] prefixedPdf17 = new byte[pdf17.length + 1];
        prefixedPdf17[0] = 'x';
        System.arraycopy(pdf17, 0, prefixedPdf17, 1, pdf17.length);
        assertVersionFailure(
                prefixedPdf17,
                DocumentFailureCode.PDF_VERSION_INVALID);
    }

    @Test
    public void rewritesDefaultToPdf17AndExplicit17Or20MarkersReopen()
            throws Exception {
        Path created = temporaryFolder.newFile().toPath();
        new DocumentWorkflow().execute(
                WorkflowRequest.create(created, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });
        assertOutputVersion(created, PdfVersion.PDF_1_7);

        byte[] pdf10 = minimalPdf(PdfVersion.PDF_1_0, null);
        Path rewritten = temporaryFolder.newFile().toPath();
        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("source", DocumentSource.bytes(pdf10, pdf10.length))
                        .primarySource("source")
                        .target("target", PublicationTarget.path(rewritten))
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> null);
        assertOutputVersion(rewritten, PdfVersion.PDF_1_7);

        for (PdfVersion explicit : new PdfVersion[] {
                PdfVersion.PDF_1_7,
                PdfVersion.PDF_2_0}) {
            Path target = temporaryFolder.newFile().toPath();
            WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .target("target", PublicationTarget.path(target))
                            .saveMode(SaveMode.REWRITE)
                            .outputPolicy(PdfOutputPolicy.version(explicit))
                            .build(),
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        return null;
                    });

            assertEquals(CAPABILITY, outcome.getCapabilityId());
            assertOutputVersion(target, explicit);
        }
    }

    @Test
    public void unsupportedOutputVersionFailsBeforeWorkOrPublication()
            throws Exception {
        Path target = temporaryFolder.newFile().toPath();
        byte[] sentinel = new byte[] {81, 82, 83};
        Files.write(target, sentinel);
        AtomicBoolean workRan = new AtomicBoolean();
        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .target("target", PublicationTarget.path(target))
                            .saveMode(SaveMode.REWRITE)
                            .outputPolicy(PdfOutputPolicy.version(
                                    PdfVersion.PDF_1_6))
                            .build(),
                    session -> {
                        workRan.set(true);
                        return null;
                    });
            fail("Expected the output version to be rejected");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.PDF_VERSION_UNSUPPORTED,
                    failure.getCode());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
        }
        assertFalse(workRan.get());
        assertEquals(3, Files.readAllBytes(target).length);

        byte[] pdf20 = minimalPdf(PdfVersion.PDF_2_0, null);
        Path downgraded = temporaryFolder.newFile().toPath();
        Files.write(downgraded, sentinel);
        AtomicBoolean downgradeWork = new AtomicBoolean();
        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source(
                                    "source",
                                    DocumentSource.bytes(pdf20, pdf20.length))
                            .primarySource("source")
                            .target(
                                    "target",
                                    PublicationTarget.path(downgraded))
                            .saveMode(SaveMode.REWRITE)
                            .build(),
                    session -> {
                        downgradeWork.set(true);
                        return null;
                    });
            fail("Expected the PDF 2.0 downgrade to be rejected");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.PDF_VERSION_UNSUPPORTED,
                    failure.getCode());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
        }
        assertFalse(downgradeWork.get());
        assertEquals(3, Files.readAllBytes(downgraded).length);
    }

    @Test
    public void passwordProtectionDefaultsToAes256AndReopensWithUserOrOwner()
            throws Exception {
        char[] ownerInput = "folio-owner-17".toCharArray();
        char[] userInput = "folio-user-17".toCharArray();
        PasswordCredential owner = PasswordCredential.of(ownerInput);
        PasswordCredential user = PasswordCredential.of(userInput);
        Arrays.fill(ownerInput, 'x');
        Arrays.fill(userInput, 'x');
        try {
            DocumentPermissions permissions = DocumentPermissions.builder()
                    .allowPrinting(true)
                    .allowAccessibilityExtraction(true)
                    .build();
            PasswordSecurityPolicy security = PasswordSecurityPolicy.builder(
                            owner,
                            user)
                    .permissions(permissions)
                    .build();
            Path target = temporaryFolder.newFile().toPath();

            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .target("target", PublicationTarget.path(target))
                            .saveMode(SaveMode.REWRITE)
                            .outputPolicy(PdfOutputPolicy
                                    .version(PdfVersion.PDF_1_7)
                                    .withPasswordSecurity(security))
                            .build(),
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        return null;
                    });

            assertSerializedSecurity(
                    target,
                    PasswordEncryptionAlgorithm.AES_256,
                    6);

            PasswordSecurityInfo userInfo = securityOf(target, user);
            assertEquals(
                    PasswordEncryptionAlgorithm.AES_256,
                    userInfo.getAlgorithm().get());
            assertEquals(
                    PasswordEncryptionScope.ALL_CONTENT,
                    userInfo.getEncryptionScope());
            assertEquals(permissions, userInfo.getDeclaredUserPermissions());
            assertEquals(permissions, userInfo.getEffectivePermissions());
            assertEquals(
                    CredentialAuthority.USER,
                    userInfo.getCredentialAuthority());

            PasswordSecurityInfo ownerInfo = securityOf(target, owner);
            assertEquals(
                    CredentialAuthority.OWNER,
                    ownerInfo.getCredentialAuthority());
            assertEquals(
                    DocumentPermissions.unrestricted(),
                    ownerInfo.getEffectivePermissions());
            assertEquals(permissions, ownerInfo.getDeclaredUserPermissions());
            assertFalse(owner.isDestroyed());
            assertFalse(user.isDestroyed());
        } finally {
            owner.close();
            user.close();
        }
        assertEquals(true, owner.isDestroyed());
        assertEquals(true, user.isDestroyed());
    }

    @Test
    public void ownerAuthorityIsProvenWhenTheUserPermissionWordIsUnrestricted()
            throws Exception {
        PasswordCredential owner = PasswordCredential.of(
                "unrestricted-owner-t16".toCharArray());
        PasswordCredential user = PasswordCredential.of(
                "unrestricted-user-t16".toCharArray());
        try {
            PasswordSecurityPolicy security = PasswordSecurityPolicy.builder(
                            owner,
                            user)
                    .permissions(DocumentPermissions.unrestricted())
                    .build();
            Path protectedSource = publishProtected(
                    security,
                    PdfVersion.PDF_1_7,
                    null);
            assertEquals(
                    CredentialAuthority.UNRESTRICTED,
                    securityOf(protectedSource, user)
                            .getCredentialAuthority());
            assertEquals(
                    CredentialAuthority.OWNER,
                    securityOf(protectedSource, owner)
                            .getCredentialAuthority());

            Path rewritten = temporaryFolder.newFile().toPath();
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source(
                                    "source",
                                    DocumentSource.path(protectedSource)
                                            .withCredential(owner))
                            .primarySource("source")
                            .target(
                                    "target",
                                    PublicationTarget.path(rewritten))
                            .saveMode(SaveMode.REWRITE)
                            .outputPolicy(PdfOutputPolicy
                                    .version(PdfVersion.PDF_1_7)
                                    .withPasswordSecurity(security))
                            .build(),
                    session -> null);
            assertEquals(
                    CredentialAuthority.OWNER,
                    securityOf(rewritten, owner).getCredentialAuthority());
        } finally {
            owner.close();
            user.close();
        }
    }

    @Test
    public void explicitPdf20Aes256UsesItsNormativePermissionAndVersionPolicy()
            throws Exception {
        PasswordCredential owner = PasswordCredential.of(
                "pdf20-owner-t16".toCharArray());
        PasswordCredential user = PasswordCredential.of(
                "pdf20-user-t16".toCharArray());
        try {
            DocumentPermissions permissions = DocumentPermissions.builder()
                    .allowAccessibilityExtraction(true)
                    .build();
            Path target = publishProtected(
                    PasswordSecurityPolicy.builder(owner, user)
                            .permissions(permissions)
                            .build(),
                    PdfVersion.PDF_2_0,
                    null);
            byte[] bytes = Files.readAllBytes(target);
            assertEquals(
                    "%PDF-2.0",
                    new String(bytes, 0, 8, StandardCharsets.US_ASCII));
            PasswordSecurityInfo info = securityOf(target, user);
            assertEquals(6, info.getSecurityHandlerRevision());
            assertEquals(permissions, info.getDeclaredUserPermissions());
            assertSerializedSecurity(
                    target,
                    PasswordEncryptionAlgorithm.AES_256,
                    6);
            assertFalse(new String(bytes, StandardCharsets.ISO_8859_1)
                    .contains("/Extensions"));
        } finally {
            owner.close();
            user.close();
        }
    }

    @Test
    public void pdf20RewriteRemovesTheOwnedPdf17AdobeSecurityExtension()
            throws Exception {
        DocumentPermissions permissions = DocumentPermissions.builder()
                .allowAccessibilityExtraction(true)
                .build();
        try (ProtectedFixture source = protectedFixture(
                PasswordEncryptionAlgorithm.AES_256,
                permissions,
                false)) {
            PasswordCredential owner = PasswordCredential.of(
                    "pdf20-rewrite-owner-t16".toCharArray());
            PasswordCredential user = PasswordCredential.of(
                    "pdf20-rewrite-user-t16".toCharArray());
            try {
                Path target = temporaryFolder.newFile().toPath();
                new DocumentWorkflow().execute(
                        WorkflowRequest.builder()
                                .source(
                                        "source",
                                        DocumentSource.path(source.path)
                                                .withCredential(source.owner))
                                .primarySource("source")
                                .target(
                                        "target",
                                        PublicationTarget.path(target))
                                .saveMode(SaveMode.REWRITE)
                                .outputPolicy(PdfOutputPolicy
                                        .version(PdfVersion.PDF_2_0)
                                        .withPasswordSecurity(
                                                PasswordSecurityPolicy.builder(
                                                                owner,
                                                                user)
                                                        .permissions(permissions)
                                                        .build()))
                                .build(),
                        session -> null);

                byte[] bytes = Files.readAllBytes(target);
                assertEquals(
                        "%PDF-2.0",
                        new String(
                                bytes,
                                0,
                                8,
                                StandardCharsets.US_ASCII));
                assertFalse(new String(bytes, StandardCharsets.ISO_8859_1)
                        .contains("/Extensions"));
                assertEquals(
                        PdfVersion.PDF_2_0,
                        versionOf(target, user).getEffectiveVersion());
                assertEquals(
                        PasswordEncryptionAlgorithm.AES_256,
                        securityOf(target, user).getAlgorithm().get());
            } finally {
                owner.close();
                user.close();
            }
        }
    }

    @Test
    public void missingIncorrectAndDestroyedCredentialsFailBeforeWorkOrPublish()
            throws Exception {
        try (ProtectedFixture fixture = protectedFixture(
                PasswordEncryptionAlgorithm.AES_256,
                DocumentPermissions.builder().build(),
                false)) {
            assertOpeningFailure(
                    DocumentSource.path(fixture.path),
                    DocumentFailureCode.CREDENTIAL_REQUIRED);

            PasswordCredential wrong = PasswordCredential.of(
                    "wrong-t16-password".toCharArray());
            try {
                assertOpeningFailure(
                        DocumentSource.path(fixture.path)
                                .withCredential(wrong),
                        DocumentFailureCode.CREDENTIAL_REJECTED);
            } finally {
                wrong.close();
            }

            PasswordCredential destroyed = PasswordCredential.of(
                    "destroyed-t16-password".toCharArray());
            destroyed.close();
            assertOpeningFailure(
                    DocumentSource.path(fixture.path)
                            .withCredential(destroyed),
                    DocumentFailureCode.CREDENTIAL_DESTROYED);

            byte[] emptyUserPassword = legacySecurityFixture(
                    PdfVersion.PDF_1_1,
                    1,
                    2,
                    40,
                    null,
                    DocumentPermissions.unrestricted().getStandardMask(),
                    "explicit-owner-t16",
                    "");
            assertOpeningFailure(
                    DocumentSource.bytes(
                            emptyUserPassword,
                            emptyUserPassword.length),
                    DocumentFailureCode.CREDENTIAL_REQUIRED);
        }
    }

    @Test
    public void credentialsOpenEverySourceKindWithoutTakingOwnership()
            throws Exception {
        try (ProtectedFixture fixture = protectedFixture(
                PasswordEncryptionAlgorithm.AES_256,
                DocumentPermissions.builder().allowPrinting(true).build(),
                false)) {
            byte[] bytes = Files.readAllBytes(fixture.path);
            TrackingInputStream stream = new TrackingInputStream(bytes);
            TrackingChannel channel = new TrackingChannel(bytes);
            DocumentSource[] sources = new DocumentSource[] {
                DocumentSource.path(fixture.path)
                        .withCredential(fixture.user),
                DocumentSource.bytes(bytes, bytes.length)
                        .withCredential(fixture.user),
                DocumentSource.stream(stream, bytes.length)
                        .withCredential(fixture.user),
                DocumentSource.channel(channel, bytes.length)
                        .withCredential(fixture.user)
            };
            for (DocumentSource source : sources) {
                PasswordSecurityInfo info = new DocumentWorkflow().execute(
                        WorkflowRequest.builder()
                                .source("source", source)
                                .primarySource("source")
                                .saveMode(SaveMode.REWRITE)
                                .build(),
                        session -> session.query(DocumentSecurity.INSTANCE))
                        .getResult();
                assertEquals(CredentialAuthority.USER,
                        info.getCredentialAuthority());
            }
            assertFalse(stream.closed);
            assertFalse(channel.closed);
            assertFalse(fixture.user.isDestroyed());
        }
    }

    @Test
    public void obsoleteOutputRequiresRequestScopedLegacyMode()
            throws Exception {
        for (PasswordEncryptionAlgorithm algorithm
                : new PasswordEncryptionAlgorithm[] {
                    PasswordEncryptionAlgorithm.RC4_128,
                    PasswordEncryptionAlgorithm.AES_128}) {
            PasswordCredential owner = PasswordCredential.of(
                    ("owner-" + algorithm).toCharArray());
            PasswordCredential user = PasswordCredential.of(
                    ("user-" + algorithm).toCharArray());
            try {
                PasswordSecurityPolicy security =
                        PasswordSecurityPolicy.builder(owner, user)
                                .algorithm(algorithm)
                                .build();
                assertOutputSecurityFailure(
                        security,
                        null,
                        PdfVersion.PDF_1_7,
                        DocumentFailureCode.LEGACY_SECURITY_MODE_REQUIRED);

                Path output = publishProtected(
                        security,
                        PdfVersion.PDF_1_7,
                        LegacySecurityMode
                                .ALLOW_OBSOLETE_PASSWORD_ENCRYPTION);
                PasswordSecurityInfo reopened = securityOf(output, user);
                assertEquals(algorithm, reopened.getAlgorithm().get());
                assertSerializedSecurity(
                        output,
                        algorithm,
                        algorithm == PasswordEncryptionAlgorithm.AES_128
                                ? 4 : 3);
            } finally {
                owner.close();
                user.close();
            }
        }

        PasswordCredential rc4Owner = PasswordCredential.of(
                "rc4-40-owner-t16".toCharArray());
        PasswordCredential rc4User = PasswordCredential.of(
                "rc4-40-user-t16".toCharArray());
        try {
            assertOutputSecurityFailure(
                    PasswordSecurityPolicy.builder(rc4Owner, rc4User)
                            .algorithm(PasswordEncryptionAlgorithm.RC4_40)
                            .build(),
                    LegacySecurityMode
                            .ALLOW_OBSOLETE_PASSWORD_ENCRYPTION,
                    PdfVersion.PDF_1_7,
                    DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED);
        } finally {
            rc4Owner.close();
            rc4User.close();
        }

        try (ProtectedFixture secureDefault = protectedFixture(
                PasswordEncryptionAlgorithm.AES_256,
                DocumentPermissions.builder().build(),
                false)) {
            assertEquals(
                    PasswordEncryptionAlgorithm.AES_256,
                    securityOf(secureDefault.path, secureDefault.user)
                            .getAlgorithm().get());
        }
    }

    @Test
    public void exactLegacyInputRevisionAllowlistUsesProjectAuthoredFixtures()
            throws Exception {
        assertLegacyInput(
                legacySecurityFixture(
                        PdfVersion.PDF_1_1,
                        1,
                        2,
                        40,
                        null,
                        DocumentPermissions.unrestricted()
                                .getStandardMask()),
                PasswordEncryptionAlgorithm.RC4_40,
                2,
                CredentialAuthority.UNRESTRICTED);
        assertLegacyInput(
                legacySecurityFixture(
                        PdfVersion.PDF_1_4,
                        1,
                        3,
                        40,
                        null,
                        DocumentPermissions.builder()
                                .allowAccessibilityExtraction(true)
                                .build()
                                .getStandardMask()),
                PasswordEncryptionAlgorithm.RC4_40,
                3,
                CredentialAuthority.USER);
        assertLegacyInput(
                legacySecurityFixture(
                        PdfVersion.PDF_1_4,
                        2,
                        3,
                        128,
                        null,
                        DocumentPermissions.builder().build()
                                .getStandardMask()),
                PasswordEncryptionAlgorithm.RC4_128,
                3,
                CredentialAuthority.USER);
        assertLegacyInput(
                legacySecurityFixture(
                        PdfVersion.PDF_1_5,
                        4,
                        4,
                        128,
                        "V2",
                        DocumentPermissions.builder().build()
                                .getStandardMask()),
                PasswordEncryptionAlgorithm.RC4_128,
                4,
                CredentialAuthority.USER);
        assertLegacyInput(
                legacySecurityFixture(
                        PdfVersion.PDF_1_6,
                        4,
                        4,
                        128,
                        "AESV2",
                        DocumentPermissions.builder().build()
                                .getStandardMask()),
                PasswordEncryptionAlgorithm.AES_128,
                4,
                CredentialAuthority.USER);
        PasswordSecurityInfo metadataClear = assertLegacyInput(
                legacySecurityFixture(
                        PdfVersion.PDF_1_6,
                        4,
                        4,
                        128,
                        "AESV2",
                        DocumentPermissions.builder().build()
                                .getStandardMask(),
                        false),
                PasswordEncryptionAlgorithm.AES_128,
                4,
                CredentialAuthority.USER);
        assertEquals(
                PasswordEncryptionScope.ALL_EXCEPT_METADATA,
                metadataClear.getEncryptionScope());

        PasswordCredential credential = PasswordCredential.of(
                "legacy-user-t16".toCharArray());
        try {
            byte[] revisionThreeBeforePdf14 = legacySecurityFixture(
                    PdfVersion.PDF_1_3,
                    1,
                    3,
                    40,
                    null,
                    DocumentPermissions.builder()
                            .allowAccessibilityExtraction(true)
                            .build()
                            .getStandardMask());
            assertOpeningFailure(
                    DocumentSource.bytes(
                            revisionThreeBeforePdf14,
                            revisionThreeBeforePdf14.length)
                            .withCredential(credential),
                    DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED);

            byte[] cryptFiltersBeforePdf15 = legacySecurityFixture(
                    PdfVersion.PDF_1_4,
                    4,
                    4,
                    128,
                    "V2",
                    DocumentPermissions.builder().build()
                            .getStandardMask());
            assertOpeningFailure(
                    DocumentSource.bytes(
                            cryptFiltersBeforePdf15,
                            cryptFiltersBeforePdf15.length)
                            .withCredential(credential),
                    DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED);

            byte[] unknownCryptFilter = legacySecurityFixture(
                    PdfVersion.PDF_1_6,
                    4,
                    4,
                    128,
                    "None",
                    DocumentPermissions.builder().build()
                            .getStandardMask());
            assertOpeningFailure(
                    DocumentSource.bytes(
                            unknownCryptFilter,
                            unknownCryptFilter.length)
                            .withCredential(credential),
                    DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED);
        } finally {
            credential.close();
        }
    }

    @Test
    public void inconsistentAes256PermissionIntegrityFailsBeforeWork()
            throws Exception {
        try (ProtectedFixture fixture = protectedFixture(
                PasswordEncryptionAlgorithm.AES_256,
                DocumentPermissions.builder().allowPrinting(true).build(),
                false)) {
            byte[] bytes = Files.readAllBytes(fixture.path);
            byte[] marker = "/Perms <".getBytes(StandardCharsets.US_ASCII);
            int offset = indexOf(bytes, marker);
            assertTrue(offset >= 0);
            int firstHex = offset + marker.length;
            bytes[firstHex] = bytes[firstHex] == '0' ? (byte) '1' : (byte) '0';
            AtomicBoolean workRan = new AtomicBoolean();
            try {
                new DocumentWorkflow().execute(
                        WorkflowRequest.builder()
                                .source(
                                        "source",
                                        DocumentSource.bytes(
                                                bytes,
                                                bytes.length)
                                                .withCredential(fixture.user))
                                .primarySource("source")
                                .saveMode(SaveMode.REWRITE)
                                .build(),
                        session -> {
                            workRan.set(true);
                            return null;
                        });
                fail("Expected inconsistent encrypted permissions to fail");
            } catch (DocumentFailure failure) {
                assertEquals(
                        DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                        failure.getCode());
                assertEquals(CAPABILITY, failure.getCapabilityId());
            }
            assertFalse(workRan.get());
        }
    }

    @Test
    public void malformedSecurityChoicesFailBeforeWorkOrPublication()
            throws Exception {
        PasswordCredential owner = PasswordCredential.of(
                "policy-owner-t16".toCharArray());
        PasswordCredential user = PasswordCredential.of(
                "policy-user-t16".toCharArray());
        try {
            for (PasswordEncryptionScope scope
                    : new PasswordEncryptionScope[] {
                        PasswordEncryptionScope.ALL_EXCEPT_METADATA,
                        PasswordEncryptionScope.EMBEDDED_FILES_ONLY}) {
                assertOutputSecurityFailure(
                        PasswordSecurityPolicy.builder(owner, user)
                                .encryptionScope(scope)
                                .build(),
                        null,
                        PdfVersion.PDF_1_7,
                        DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED);
            }
            assertOutputSecurityFailure(
                    PasswordSecurityPolicy.builder(owner, user)
                            .algorithm(PasswordEncryptionAlgorithm.AES_128)
                            .build(),
                    LegacySecurityMode
                            .ALLOW_OBSOLETE_PASSWORD_ENCRYPTION,
                    PdfVersion.PDF_2_0,
                    DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED);
            assertOutputSecurityFailure(
                    PasswordSecurityPolicy.builder(owner, user).build(),
                    null,
                    PdfVersion.PDF_2_0,
                    DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED);
        } finally {
            owner.close();
            user.close();
        }

        PasswordCredential same = PasswordCredential.of(
                "same-password-t16".toCharArray());
        try {
            assertOutputSecurityFailure(
                    PasswordSecurityPolicy.builder(same, same).build(),
                    null,
                    PdfVersion.PDF_1_7,
                    DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED);
        } finally {
            same.close();
        }

        assertInvalidOutputCredentials(new char[0], "nonempty-user-t16".toCharArray());
        assertInvalidOutputCredentials(
                "ascii-owner-t16".toCharArray(),
                "non-ascii-\u00e9-t16".toCharArray());
        char[] overlong = new char[128];
        Arrays.fill(overlong, 'a');
        assertInvalidOutputCredentials(
                overlong,
                "bounded-user-t16".toCharArray());

        PasswordCredential destroyed = PasswordCredential.of(
                "destroyed-output-t16".toCharArray());
        PasswordCredential live = PasswordCredential.of(
                "live-output-t16".toCharArray());
        destroyed.close();
        try {
            assertOutputSecurityFailure(
                    PasswordSecurityPolicy.builder(destroyed, live).build(),
                    null,
                    PdfVersion.PDF_1_7,
                    DocumentFailureCode.CREDENTIAL_DESTROYED);
        } finally {
            live.close();
        }
    }

    @Test
    public void everyStandardPermissionBitRoundTripsThroughProtectedOutput()
            throws Exception {
        DocumentPermissions[] variants = new DocumentPermissions[] {
            DocumentPermissions.builder().allowPrinting(true).build(),
            DocumentPermissions.builder().allowModification(true).build(),
            DocumentPermissions.builder().allowContentExtraction(true).build(),
            DocumentPermissions.builder()
                    .allowAnnotationModification(true).build(),
            DocumentPermissions.builder().allowFormFilling(true).build(),
            DocumentPermissions.builder()
                    .allowAccessibilityExtraction(true).build(),
            DocumentPermissions.builder().allowDocumentAssembly(true).build(),
            DocumentPermissions.builder().allowFaithfulPrinting(true).build()
        };
        PasswordCredential owner = PasswordCredential.of(
                "permission-owner-t16".toCharArray());
        PasswordCredential user = PasswordCredential.of(
                "permission-user-t16".toCharArray());
        try {
            for (DocumentPermissions permissions : variants) {
                Path output = publishProtected(
                        PasswordSecurityPolicy.builder(owner, user)
                                .permissions(permissions)
                                .build(),
                        PdfVersion.PDF_1_7,
                        null);
                PasswordSecurityInfo reopened = securityOf(output, user);
                assertEquals(
                        permissions,
                        reopened.getDeclaredUserPermissions());
                assertEquals(
                        permissions,
                        reopened.getEffectivePermissions());
            }
        } finally {
            owner.close();
            user.close();
        }
    }

    @Test
    public void userPermissionsRoundTripAndGateOperationsBeforeMutation()
            throws Exception {
        DocumentPermissions denied = DocumentPermissions.builder().build();
        try (ProtectedFixture fixture = protectedFixture(
                PasswordEncryptionAlgorithm.AES_256,
                denied,
                false)) {
            Integer pageCount = new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source(
                                    "source",
                                    DocumentSource.path(fixture.path)
                                            .withCredential(fixture.user))
                            .primarySource("source")
                            .saveMode(SaveMode.REWRITE)
                    .build(),
                    session -> {
                        ObjectReference root = session.query(
                                DocumentRootReference.INSTANCE);
                        assertPermissionDenied(() -> session.execute(
                                AddBlankPage.INSTANCE));
                        assertPermissionDenied(() -> session.execute(
                                SplitDocument.version1().build()));
                        assertPermissionDenied(() -> session.execute(
                                ReplaceOutlineTree.version1(
                                        Collections.emptyList())));
                        assertPermissionDenied(() -> session.execute(
                                UpdateDocumentInfo.version1().build()));
                        assertPermissionDenied(() -> session.execute(
                                UpdateActions.version1().build()));
                        assertPermissionDenied(() -> session.execute(
                                UpdateAnnotations.version1().build()));
                        assertPermissionDenied(() -> session.query(
                                DocumentInfo.INSTANCE));
                        assertPermissionDenied(() -> session.query(
                                Annotations.version1(0, 0L, 0L)));
                        assertPermissionDenied(() -> session.query(
                                InspectObject.version1(
                                        root,
                                        PdfInspectionLimits.of(1L, 0L))));
                        return session.query(PageCount.INSTANCE);
                    }).getResult();
            assertEquals(Integer.valueOf(1), pageCount);
        }

        DocumentPermissions selected = DocumentPermissions.builder()
                .allowModification(true)
                .allowAnnotationModification(true)
                .allowDocumentAssembly(true)
                .build();
        try (ProtectedFixture fixture = protectedFixture(
                PasswordEncryptionAlgorithm.AES_256,
                selected,
                false)) {
            Integer count = new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source(
                                    "source",
                                    DocumentSource.path(fixture.path)
                                            .withCredential(fixture.user))
                            .primarySource("source")
                            .saveMode(SaveMode.REWRITE)
                            .build(),
                    session -> {
                        session.execute(UpdateDocumentInfo.version1().build());
                        session.execute(UpdateAnnotations.version1().build());
                        session.execute(AddBlankPage.INSTANCE);
                        session.execute(InsertBlankPage.version1(1));
                        return session.query(PageCount.INSTANCE);
                    }).getResult();
            assertEquals(Integer.valueOf(3), count);
        }
    }

    @Test
    public void compositeOperationsRequireEveryMappedPermission()
            throws Exception {
        DocumentPermissions annotationOnly = DocumentPermissions.builder()
                .allowAnnotationModification(true)
                .build();
        try (ProtectedFixture fixture = protectedFixture(
                PasswordEncryptionAlgorithm.AES_256,
                annotationOnly,
                false)) {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source(
                                    "source",
                                    DocumentSource.path(fixture.path)
                                            .withCredential(fixture.user))
                            .primarySource("source")
                            .saveMode(SaveMode.REWRITE)
                            .build(),
                    session -> {
                        assertPermissionDenied(() -> session.execute(
                                FlattenAnnotations.version1("missing")));
                        return null;
                    });
        }

        DocumentPermissions modificationAndAnnotation =
                DocumentPermissions.builder()
                        .allowModification(true)
                        .allowAnnotationModification(true)
                        .build();
        try (ProtectedFixture fixture = protectedFixture(
                PasswordEncryptionAlgorithm.AES_256,
                modificationAndAnnotation,
                false)) {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source(
                                    "source",
                                    DocumentSource.path(fixture.path)
                                            .withCredential(fixture.user))
                            .primarySource("source")
                            .saveMode(SaveMode.REWRITE)
                            .build(),
                    session -> {
                        ObjectReference root = session.query(
                                DocumentRootReference.INSTANCE);
                        DocumentPatch patch = DocumentPatch.builder()
                                .setDictionaryEntry(
                                        root,
                                        PdfName.of("T16PermissionProbe"),
                                        PdfString.of(new byte[] {1}))
                                .build();
                        assertPermissionDenied(() -> session.execute(patch));
                        return null;
                    });
        }
    }

    @Test
    public void documentPatchCannotChangeEngineOwnedVersionState()
            throws Exception {
        byte[] source = minimalPdf(PdfVersion.PDF_1_7, null);
        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source(
                                "source",
                                DocumentSource.bytes(source, source.length))
                        .primarySource("source")
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    ObjectReference root = session.query(
                            DocumentRootReference.INSTANCE);
                    for (String reserved : new String[] {
                            "Version", "Extensions"}) {
                        DocumentPatch patch = DocumentPatch.builder()
                                .setDictionaryEntry(
                                        root,
                                        PdfName.of(reserved),
                                        PdfName.of("2.0"))
                                .build();
                        try {
                            session.execute(patch);
                            fail("Expected engine-owned state to be protected");
                        } catch (DocumentFailure failure) {
                            assertEquals(
                                    DocumentFailureCode.COMMAND_REJECTED,
                                    failure.getCode());
                            assertEquals(CAPABILITY, failure.getCapabilityId());
                        }
                    }
                    return null;
                });

        try (ProtectedFixture fixture = protectedFixture(
                PasswordEncryptionAlgorithm.AES_256,
                DocumentPermissions.builder().build(),
                false)) {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source(
                                    "source",
                                    DocumentSource.path(fixture.path)
                                            .withCredential(fixture.owner))
                            .primarySource("source")
                            .saveMode(SaveMode.REWRITE)
                            .build(),
                    session -> {
                        ObjectReference root = session.query(
                                DocumentRootReference.INSTANCE);
                        PdfDictionary catalog = (PdfDictionary) session.query(
                                InspectObject.version1(
                                        root,
                                        PdfInspectionLimits.of(4L, 0L)));
                        ObjectReference extensions = ((PdfIndirectReference)
                                catalog.get(PdfName.of("Extensions")))
                                .getReference();
                        PdfDictionary extensionDictionary =
                                (PdfDictionary) session.query(
                                        InspectObject.version1(
                                                extensions,
                                                PdfInspectionLimits.of(
                                                        2L,
                                                        0L)));
                        ObjectReference adobe = ((PdfIndirectReference)
                                extensionDictionary.get(PdfName.of("ADBE")))
                                .getReference();
                        assertReservedPatchRejected(
                                session,
                                extensions,
                                "T16ExtensionProbe",
                                PdfName.of("rejected"));
                        assertReservedPatchRejected(
                                session,
                                adobe,
                                "ExtensionLevel",
                                PdfNumber.of(9L));
                        return null;
                    });
        }

        byte[] exposedEncryption = legacySecurityFixture(
                PdfVersion.PDF_1_6,
                4,
                4,
                128,
                "AESV2",
                DocumentPermissions.builder().build().getStandardMask(),
                "legacy-owner-t16",
                "legacy-user-t16",
                true,
                true);
        PasswordCredential legacyOwner = PasswordCredential.of(
                "legacy-owner-t16".toCharArray());
        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source(
                                    "source",
                                    DocumentSource.bytes(
                                            exposedEncryption,
                                            exposedEncryption.length)
                                            .withCredential(legacyOwner))
                            .primarySource("source")
                            .saveMode(SaveMode.REWRITE)
                            .build(),
                    session -> {
                        ObjectReference root = session.query(
                                DocumentRootReference.INSTANCE);
                        PdfDictionary catalog = (PdfDictionary) session.query(
                                InspectObject.version1(
                                        root,
                                        PdfInspectionLimits.of(4L, 0L)));
                        ObjectReference encryption = ((PdfIndirectReference)
                                catalog.get(PdfName.of("T16EncryptAlias")))
                                .getReference();
                        PdfDictionary encryptionDictionary =
                                (PdfDictionary) session.query(
                                        InspectObject.version1(
                                                encryption,
                                                PdfInspectionLimits.of(
                                                        12L,
                                                        0L)));
                        ObjectReference cryptFilters = ((PdfIndirectReference)
                                encryptionDictionary.get(PdfName.of("CF")))
                                .getReference();
                        assertReservedPatchRejected(
                                session,
                                encryption,
                                "P",
                                PdfNumber.of(-4L));
                        assertReservedPatchRejected(
                                session,
                                cryptFilters,
                                "T16CryptFilterProbe",
                                PdfName.of("rejected"));
                        return null;
                    });
        } finally {
            legacyOwner.close();
        }
    }

    @Test
    public void namedPathSnapshotsRetainPrivateTemporaryPermissions()
            throws Exception {
        Assume.assumeTrue(FileSystems.getDefault()
                .supportedFileAttributeViews().contains("posix"));
        Path donor = temporaryFolder.newFile("public-mode-donor.pdf").toPath();
        Files.write(donor, minimalPdf(PdfVersion.PDF_1_7, null));
        Files.setPosixFilePermissions(
                donor,
                EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.GROUP_READ,
                        PosixFilePermission.OTHERS_READ));
        Set<Path> before = namedSourceSnapshots();
        byte[] primary = minimalPdf(PdfVersion.PDF_1_7, null);

        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source(
                                "primary",
                                DocumentSource.bytes(primary, primary.length))
                        .source("donor", DocumentSource.path(donor))
                        .primarySource("primary")
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    try {
                        Set<Path> created = namedSourceSnapshots();
                        created.removeAll(before);
                        assertEquals(2, created.size());
                        for (Path snapshot : created) {
                            assertEquals(
                                    EnumSet.of(
                                            PosixFilePermission.OWNER_READ,
                                            PosixFilePermission.OWNER_WRITE),
                                    Files.getPosixFilePermissions(snapshot));
                            assertEquals(
                                    EnumSet.of(
                                            PosixFilePermission.OWNER_READ,
                                            PosixFilePermission.OWNER_WRITE,
                                            PosixFilePermission.OWNER_EXECUTE),
                                    Files.getPosixFilePermissions(
                                            snapshot.getParent()));
                        }
                    } catch (IOException exception) {
                        throw new AssertionError(exception);
                    }
                    return null;
                });

        Set<Path> remaining = namedSourceSnapshots();
        remaining.removeAll(before);
        assertTrue(remaining.isEmpty());
    }

    @Test
    public void protectedMergeDonorRequiresExtractionWithT16FailureIdentity()
            throws Exception {
        DocumentPermissions primaryPermissions = DocumentPermissions.builder()
                .allowDocumentAssembly(true)
                .build();
        DocumentPermissions donorPermissions = DocumentPermissions.builder()
                .build();
        try (ProtectedFixture primary = protectedFixture(
                    PasswordEncryptionAlgorithm.AES_256,
                    primaryPermissions,
                    false);
                ProtectedFixture donor = protectedFixture(
                    PasswordEncryptionAlgorithm.AES_256,
                    donorPermissions,
                    false)) {
            Integer pageCount = new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source(
                                    "primary",
                                    DocumentSource.path(primary.path)
                                            .withCredential(primary.user))
                            .source(
                                    "donor",
                                    DocumentSource.path(donor.path)
                                            .withCredential(donor.user))
                            .primarySource("primary")
                            .saveMode(SaveMode.REWRITE)
                            .build(),
                    session -> {
                        assertPermissionDenied(() -> session.execute(
                                MergeDocuments.version1("donor")));
                        return session.query(PageCount.INSTANCE);
                    }).getResult();

            assertEquals(Integer.valueOf(1), pageCount);
        }
    }

    @Test
    public void everyDeclaredProtectedSourceIsAuthenticatedBeforeCallerWork()
            throws Exception {
        try (ProtectedFixture donor = protectedFixture(
                PasswordEncryptionAlgorithm.AES_256,
                DocumentPermissions.builder()
                        .allowContentExtraction(true)
                        .build(),
                false)) {
            byte[] primary = minimalPdf(PdfVersion.PDF_1_7, null);
            AtomicBoolean workRan = new AtomicBoolean();
            try {
                new DocumentWorkflow().execute(
                        WorkflowRequest.builder()
                                .source(
                                        "primary",
                                        DocumentSource.bytes(
                                                primary,
                                                primary.length))
                                .source(
                                        "donor",
                                        DocumentSource.path(donor.path))
                                .primarySource("primary")
                                .saveMode(SaveMode.REWRITE)
                                .build(),
                        session -> {
                            workRan.set(true);
                            return null;
                        });
                fail("Expected the named Source credential to be required");
            } catch (DocumentFailure failure) {
                assertEquals(
                        DocumentFailureCode.CREDENTIAL_REQUIRED,
                        failure.getCode());
                assertEquals(CAPABILITY, failure.getCapabilityId());
            }
            assertFalse(workRan.get());
        }
    }

    @Test
    public void namedSourcesCannotDowngradeVersionOrPasswordProtection()
            throws Exception {
        byte[] primary = minimalPdf(PdfVersion.PDF_1_7, null);
        byte[] pdf20 = minimalPdf(PdfVersion.PDF_2_0, null);
        Path versionTarget = temporaryFolder.newFile().toPath();
        byte[] sentinel = new byte[] {61, 62, 63};
        Files.write(versionTarget, sentinel);
        AtomicBoolean versionWork = new AtomicBoolean();
        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source(
                                    "primary",
                                    DocumentSource.bytes(
                                            primary,
                                            primary.length))
                            .source(
                                    "donor",
                                    DocumentSource.bytes(
                                            pdf20,
                                            pdf20.length))
                            .primarySource("primary")
                            .target(
                                    "target",
                                    PublicationTarget.path(versionTarget))
                            .saveMode(SaveMode.REWRITE)
                            .build(),
                    session -> {
                        versionWork.set(true);
                        session.execute(MergeDocuments.version1("donor"));
                        return null;
                    });
            fail("Expected the named PDF 2.0 Source downgrade to fail");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.PDF_VERSION_UNSUPPORTED,
                    failure.getCode());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
        }
        assertFalse(versionWork.get());
        assertTrue(Arrays.equals(sentinel, Files.readAllBytes(versionTarget)));

        DocumentPermissions extractable = DocumentPermissions.builder()
                .allowContentExtraction(true)
                .build();
        try (ProtectedFixture donor = protectedFixture(
                PasswordEncryptionAlgorithm.AES_256,
                extractable,
                false)) {
            Path plaintextTarget = temporaryFolder.newFile().toPath();
            Files.write(plaintextTarget, sentinel);
            AtomicBoolean plaintextWork = new AtomicBoolean();
            try {
                new DocumentWorkflow().execute(
                        WorkflowRequest.builder()
                                .source(
                                        "primary",
                                        DocumentSource.bytes(
                                                primary,
                                                primary.length))
                                .source(
                                        "donor",
                                        DocumentSource.path(donor.path)
                                                .withCredential(donor.user))
                                .primarySource("primary")
                                .target(
                                        "target",
                                        PublicationTarget.path(
                                                plaintextTarget))
                                .saveMode(SaveMode.REWRITE)
                                .build(),
                        session -> {
                            plaintextWork.set(true);
                            session.execute(MergeDocuments.version1("donor"));
                            return null;
                        });
                fail("Expected protected donor publication to require protection");
            } catch (DocumentFailure failure) {
                assertEquals(
                        DocumentFailureCode
                                .PASSWORD_SECURITY_POLICY_REQUIRED,
                        failure.getCode());
                assertEquals(CAPABILITY, failure.getCapabilityId());
            }
            assertFalse(plaintextWork.get());
            assertTrue(Arrays.equals(
                    sentinel,
                    Files.readAllBytes(plaintextTarget)));

            PasswordCredential outputOwner = PasswordCredential.of(
                    "merge-output-owner-t16".toCharArray());
            PasswordCredential outputUser = PasswordCredential.of(
                    "merge-output-user-t16".toCharArray());
            try {
                Path protectedTarget = temporaryFolder.newFile().toPath();
                WorkflowOutcome<Integer> outcome =
                        new DocumentWorkflow().execute(
                                WorkflowRequest.builder()
                                        .source(
                                                "primary",
                                                DocumentSource.bytes(
                                                        primary,
                                                        primary.length))
                                        .source(
                                                "donor",
                                                DocumentSource.path(donor.path)
                                                        .withCredential(
                                                                donor.user))
                                        .primarySource("primary")
                                        .target(
                                                "target",
                                                PublicationTarget.path(
                                                        protectedTarget))
                                        .saveMode(SaveMode.REWRITE)
                                        .outputPolicy(PdfOutputPolicy
                                                .version(PdfVersion.PDF_1_7)
                                                .withPasswordSecurity(
                                                        PasswordSecurityPolicy
                                                                .builder(
                                                                        outputOwner,
                                                                        outputUser)
                                                                .build()))
                                        .build(),
                                session -> {
                                    session.execute(MergeDocuments.version1(
                                            "donor"));
                                    return session.query(PageCount.INSTANCE);
                                });
                assertEquals(Integer.valueOf(2), outcome.getResult());
                assertEquals(CAPABILITY, outcome.getCapabilityId());
                assertEquals(
                        Integer.valueOf(2),
                        new DocumentWorkflow().execute(
                                WorkflowRequest.builder()
                                        .source(
                                                "source",
                                                DocumentSource.path(
                                                        protectedTarget)
                                                        .withCredential(
                                                                outputUser))
                                        .primarySource("source")
                                        .saveMode(SaveMode.REWRITE)
                                        .build(),
                                session -> session.query(PageCount.INSTANCE))
                                .getResult());
            } finally {
                outputOwner.close();
                outputUser.close();
            }
        }
    }

    @Test
    public void pageOperationsRejectUnknownEncryptedCatalogExtensions()
            throws Exception {
        byte[] vendorExtension = minimalPdf(
                PdfVersion.PDF_1_7,
                null,
                " /Extensions << /Vendor << /BaseVersion /1.7"
                        + " /ExtensionLevel 1 >> >>");
        PasswordCredential owner = PasswordCredential.of(
                "extension-owner-t16".toCharArray());
        PasswordCredential user = PasswordCredential.of(
                "extension-user-t16".toCharArray());
        try {
            Path protectedSource = temporaryFolder.newFile().toPath();
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source(
                                    "source",
                                    DocumentSource.bytes(
                                            vendorExtension,
                                            vendorExtension.length))
                            .primarySource("source")
                            .target(
                                    "target",
                                    PublicationTarget.path(protectedSource))
                            .saveMode(SaveMode.REWRITE)
                            .outputPolicy(PdfOutputPolicy
                                    .version(PdfVersion.PDF_1_7)
                                    .withPasswordSecurity(
                                            PasswordSecurityPolicy.builder(
                                                            owner,
                                                            user)
                                                    .permissions(
                                                            DocumentPermissions
                                                                    .builder()
                                                                    .allowDocumentAssembly(
                                                                            true)
                                                                    .build())
                                                    .build()))
                            .build(),
                    session -> null);

            try {
                new DocumentWorkflow().execute(
                        WorkflowRequest.builder()
                                .source(
                                        "source",
                                        DocumentSource.path(protectedSource)
                                                .withCredential(user))
                                .primarySource("source")
                                .saveMode(SaveMode.REWRITE)
                                .build(),
                        session -> {
                            session.execute(InsertBlankPage.version1(1));
                            return null;
                        });
                fail("Expected the unknown extension to fail preservation");
            } catch (DocumentFailure failure) {
                assertEquals(
                        DocumentFailureCode.PRESERVATION_UNSUPPORTED,
                        failure.getCode());
                assertEquals(
                        "document.page.manipulate-merge-split",
                        failure.getCapabilityId());
            }
        } finally {
            owner.close();
            user.close();
        }
    }

    @Test
    public void encryptedExistingSignatureStillEnforcesT15BeforeRewriteWork()
            throws Exception {
        byte[] contentSource = minimalPdfWithContentStream();
        PasswordCredential owner = PasswordCredential.of(
                "signed-owner-t16".toCharArray());
        PasswordCredential user = PasswordCredential.of(
                "signed-user-t16".toCharArray());
        try {
            PasswordSecurityPolicy security = PasswordSecurityPolicy.builder(
                            owner,
                            user)
                    .permissions(DocumentPermissions.unrestricted())
                    .build();
            Path encrypted = temporaryFolder.newFile().toPath();
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source(
                                    "source",
                                    DocumentSource.bytes(
                                            contentSource,
                                            contentSource.length))
                            .primarySource("source")
                            .target(
                                    "target",
                                    PublicationTarget.path(encrypted))
                            .saveMode(SaveMode.REWRITE)
                            .outputPolicy(PdfOutputPolicy
                                    .version(PdfVersion.PDF_1_7)
                                    .withPasswordSecurity(security))
                            .build(),
                    session -> null);

            Path encryptedSigned = temporaryFolder.newFile().toPath();
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source(
                                    "source",
                                    DocumentSource.path(encrypted)
                                            .withCredential(owner))
                            .primarySource("source")
                            .target(
                                    "target",
                                    PublicationTarget.path(encryptedSigned))
                            .saveMode(SaveMode.INCREMENTAL)
                            .build(),
                    session -> {
                        ObjectReference root = session.query(
                                DocumentRootReference.INSTANCE);
                        ObjectReference page = session.query(
                                PageObjectReference.version1(1));
                        PdfDictionary pageDictionary = (PdfDictionary)
                                session.query(InspectObject.version1(
                                        page,
                                        PdfInspectionLimits.of(
                                                16L,
                                                0L)));
                        PdfIndirectReference content = (PdfIndirectReference)
                                pageDictionary.get(PdfName.of("Contents"));
                        PdfDictionary signatureField = PdfDictionary.builder()
                                .put(PdfName.of("FT"), PdfName.of("Sig"))
                                .put(
                                        PdfName.of("V"),
                                        PdfIndirectReference.of(
                                                content.getReference()))
                                .build();
                        PdfDictionary acroForm = PdfDictionary.builder()
                                .put(
                                        PdfName.of("Fields"),
                                        PdfArray.of(signatureField))
                                .put(PdfName.of("SigFlags"), PdfNumber.of(3L))
                                .build();
                        session.execute(DocumentPatch.builder()
                                .setDictionaryEntry(
                                        content.getReference(),
                                        PdfName.of("Contents"),
                                        PdfString.of(new byte[] {0}))
                                .setDictionaryEntry(
                                        content.getReference(),
                                        PdfName.of("ByteRange"),
                                        PdfArray.of(
                                                PdfNumber.of(0L),
                                                PdfNumber.of(1L)))
                                .setDictionaryEntry(
                                        root,
                                        PdfName.of("AcroForm"),
                                        acroForm)
                                .build());
                        return null;
                    });

            Path rewrite = temporaryFolder.newFile().toPath();
            byte[] sentinel = new byte[] {71, 72, 73};
            Files.write(rewrite, sentinel);
            AtomicBoolean workRan = new AtomicBoolean();
            try {
                new DocumentWorkflow().execute(
                        WorkflowRequest.builder()
                                .source(
                                        "source",
                                        DocumentSource.path(encryptedSigned)
                                                .withCredential(owner))
                                .primarySource("source")
                                .target(
                                        "target",
                                        PublicationTarget.path(rewrite))
                                .saveMode(SaveMode.REWRITE)
                                .outputPolicy(PdfOutputPolicy
                                        .version(PdfVersion.PDF_1_7)
                                        .withPasswordSecurity(security))
                                .build(),
                        session -> {
                            workRan.set(true);
                            return null;
                        });
                fail("Expected T15 to reject encrypted signed rewrite");
            } catch (DocumentFailure failure) {
                assertEquals(
                        DocumentFailureCode.SIGNED_REWRITE_REJECTED,
                        failure.getCode());
                assertEquals(
                        "document.incremental-signature.protect",
                        failure.getCapabilityId());
                assertEquals(
                        PublicationStatus.NOT_ATTEMPTED,
                        failure.getPublicationReceipts().get(0).getStatus());
            }
            assertFalse(workRan.get());
            assertTrue(Arrays.equals(sentinel, Files.readAllBytes(rewrite)));
        } finally {
            owner.close();
            user.close();
        }
    }

    @Test
    public void protectedRewriteRequiresExplicitProtectionAndIncrementalPreservesIt()
            throws Exception {
        try (ProtectedFixture fixture = protectedFixture(
                PasswordEncryptionAlgorithm.AES_256,
                DocumentPermissions.builder().allowPrinting(true).build(),
                false)) {
            Path rewrite = temporaryFolder.newFile().toPath();
            AtomicBoolean rewriteWork = new AtomicBoolean();
            try {
                new DocumentWorkflow().execute(
                        WorkflowRequest.builder()
                                .source(
                                        "source",
                                        DocumentSource.path(fixture.path)
                                                .withCredential(fixture.owner))
                                .primarySource("source")
                                .target("target", PublicationTarget.path(rewrite))
                                .saveMode(SaveMode.REWRITE)
                                .build(),
                        session -> {
                            rewriteWork.set(true);
                            return null;
                        });
                fail("Expected protected rewrite policy to be required");
            } catch (DocumentFailure failure) {
                assertEquals(
                        DocumentFailureCode.PASSWORD_SECURITY_POLICY_REQUIRED,
                        failure.getCode());
                assertEquals(
                        PublicationStatus.NOT_ATTEMPTED,
                        failure.getPublicationReceipts().get(0).getStatus());
            }
            assertFalse(rewriteWork.get());

            PasswordCredential replacementOwner = PasswordCredential.of(
                    "rewrite-owner-t16".toCharArray());
            PasswordCredential replacementUser = PasswordCredential.of(
                    "rewrite-user-t16".toCharArray());
            try {
                PasswordSecurityPolicy replacement =
                        PasswordSecurityPolicy.builder(
                                replacementOwner,
                                replacementUser).build();
                Path userAttempt = temporaryFolder.newFile().toPath();
                AtomicBoolean userWork = new AtomicBoolean();
                try {
                    new DocumentWorkflow().execute(
                            WorkflowRequest.builder()
                                    .source(
                                            "source",
                                            DocumentSource.path(fixture.path)
                                                    .withCredential(
                                                            fixture.user))
                                    .primarySource("source")
                                    .target(
                                            "target",
                                            PublicationTarget.path(userAttempt))
                                    .saveMode(SaveMode.REWRITE)
                                    .outputPolicy(PdfOutputPolicy
                                            .version(PdfVersion.PDF_1_7)
                                            .withPasswordSecurity(replacement))
                                    .build(),
                            session -> {
                                userWork.set(true);
                                return null;
                            });
                    fail("Expected user-authority rekey to be rejected");
                } catch (DocumentFailure failure) {
                    assertEquals(
                            DocumentFailureCode.DOCUMENT_PERMISSION_DENIED,
                            failure.getCode());
                }
                assertFalse(userWork.get());

                Path protectedRewrite = temporaryFolder.newFile().toPath();
                new DocumentWorkflow().execute(
                        WorkflowRequest.builder()
                                .source(
                                        "source",
                                        DocumentSource.path(fixture.path)
                                                .withCredential(fixture.owner))
                                .primarySource("source")
                                .target(
                                        "target",
                                        PublicationTarget.path(protectedRewrite))
                                .saveMode(SaveMode.REWRITE)
                                .outputPolicy(PdfOutputPolicy
                                        .version(PdfVersion.PDF_1_7)
                                        .withPasswordSecurity(replacement))
                                .build(),
                        session -> {
                            session.execute(AddBlankPage.INSTANCE);
                            return null;
                        });
                assertEquals(
                        PasswordEncryptionAlgorithm.AES_256,
                        securityOf(protectedRewrite, replacementUser)
                                .getAlgorithm().get());
            } finally {
                replacementOwner.close();
                replacementUser.close();
            }

            Path incremental = temporaryFolder.newFile().toPath();
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source(
                                    "source",
                                    DocumentSource.path(fixture.path)
                                            .withCredential(fixture.owner))
                            .primarySource("source")
                            .target(
                                    "target",
                                    PublicationTarget.path(incremental))
                            .saveMode(SaveMode.INCREMENTAL)
                            .build(),
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        return null;
                    });
            assertTrue(Files.size(incremental) > Files.size(fixture.path));
            assertEquals(
                    PasswordEncryptionAlgorithm.AES_256,
                    securityOf(incremental, fixture.owner)
                            .getAlgorithm().get());
        }
    }

    private PasswordSecurityInfo assertLegacyInput(
            byte[] fixture,
            PasswordEncryptionAlgorithm algorithm,
            int revision,
            CredentialAuthority authority) throws Exception {
        PasswordCredential credential = PasswordCredential.of(
                "legacy-user-t16".toCharArray());
        try {
            PasswordSecurityInfo security = new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source(
                                    "source",
                                    DocumentSource.bytes(
                                            fixture,
                                            fixture.length)
                                            .withCredential(credential))
                            .primarySource("source")
                            .saveMode(SaveMode.REWRITE)
                            .build(),
                    session -> session.query(DocumentSecurity.INSTANCE))
                    .getResult();
            assertEquals(algorithm, security.getAlgorithm().get());
            assertEquals(revision, security.getSecurityHandlerRevision());
            assertEquals(authority, security.getCredentialAuthority());
            return security;
        } finally {
            credential.close();
        }
    }

    private static byte[] legacySecurityFixture(
            PdfVersion headerVersion,
            int encryptionVersion,
            int revision,
            int keyBits,
            String cryptFilterMethod,
            int permissions) throws Exception {
        return legacySecurityFixture(
                headerVersion,
                encryptionVersion,
                revision,
                keyBits,
                cryptFilterMethod,
                permissions,
                "legacy-owner-t16",
                "legacy-user-t16",
                true,
                false);
    }

    private static byte[] legacySecurityFixture(
            PdfVersion headerVersion,
            int encryptionVersion,
            int revision,
            int keyBits,
            String cryptFilterMethod,
            int permissions,
            boolean encryptMetadata) throws Exception {
        return legacySecurityFixture(
                headerVersion,
                encryptionVersion,
                revision,
                keyBits,
                cryptFilterMethod,
                permissions,
                "legacy-owner-t16",
                "legacy-user-t16",
                encryptMetadata,
                false);
    }

    private static byte[] legacySecurityFixture(
            PdfVersion headerVersion,
            int encryptionVersion,
            int revision,
            int keyBits,
            String cryptFilterMethod,
            int permissions,
            String ownerPassword,
            String userPassword) throws Exception {
        return legacySecurityFixture(
                headerVersion,
                encryptionVersion,
                revision,
                keyBits,
                cryptFilterMethod,
                permissions,
                ownerPassword,
                userPassword,
                true,
                false);
    }

    private static byte[] legacySecurityFixture(
            PdfVersion headerVersion,
            int encryptionVersion,
            int revision,
            int keyBits,
            String cryptFilterMethod,
            int permissions,
            String ownerPassword,
            String userPassword,
            boolean encryptMetadata,
            boolean exposeEncryptionGraph) throws Exception {
        byte[] identifier = new byte[] {
            0x46, 0x6f, 0x6c, 0x69, 0x6f, 0x2d, 0x54, 0x31,
            0x36, 0x2d, 0x49, 0x44, 0x2d, 0x30, 0x30, 0x31
        };
        byte[] owner = ownerEntry(
                ownerPassword,
                userPassword,
                revision,
                keyBits);
        byte[] fileKey = fileEncryptionKey(
                userPassword,
                owner,
                permissions,
                identifier,
                revision,
                keyBits,
                encryptMetadata);
        byte[] user = userEntry(fileKey, identifier, revision);
        StringBuilder encryption = new StringBuilder()
                .append("<< /Filter /Standard /V ")
                .append(encryptionVersion)
                .append(" /R ")
                .append(revision)
                .append(" /Length ")
                .append(keyBits)
                .append(" /O <")
                .append(hex(owner))
                .append("> /U <")
                .append(hex(user))
                .append("> /P ")
                .append(permissions);
        if (cryptFilterMethod != null) {
            encryption.append(" /EncryptMetadata ")
                    .append(encryptMetadata);
            if (exposeEncryptionGraph) {
                encryption.append(" /CF 5 0 R");
            } else {
                encryption.append(" /CF << /StdCF << /AuthEvent /DocOpen")
                        .append(" /CFM /")
                        .append(cryptFilterMethod)
                        .append(" /Length 16 >> >>");
            }
            encryption
                    .append(" /StmF /StdCF /StrF /StdCF");
        }
        encryption.append(" >>");

        String catalog = "<< /Type /Catalog /Pages 2 0 R"
                + (exposeEncryptionGraph
                        ? " /T16EncryptAlias 4 0 R" : "")
                + " >>";
        String[] objects;
        if (exposeEncryptionGraph) {
            objects = new String[] {
                catalog,
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 72 72]"
                        + " /Resources << >> >>",
                encryption.toString(),
                "<< /StdCF << /AuthEvent /DocOpen /CFM /"
                        + cryptFilterMethod + " /Length 16 >> >>"
            };
        } else {
            objects = new String[] {
                catalog,
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 72 72]"
                        + " /Resources << >> >>",
                encryption.toString()
            };
        }
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        write(pdf, "%PDF-" + headerVersion + "\n%FolioT16LegacyFixture\n");
        int[] offsets = new int[objects.length + 1];
        for (int index = 0; index < objects.length; index++) {
            offsets[index + 1] = pdf.size();
            write(pdf, (index + 1) + " 0 obj\n" + objects[index]
                    + "\nendobj\n");
        }
        int xref = pdf.size();
        write(pdf, "xref\n0 " + (objects.length + 1) + "\n");
        write(pdf, "0000000000 65535 f \n");
        for (int index = 1; index < offsets.length; index++) {
            write(pdf, String.format("%010d 00000 n \n", offsets[index]));
        }
        String id = hex(identifier);
        write(pdf, "trailer\n<< /Size " + (objects.length + 1)
                + " /Root 1 0 R /Encrypt 4 0 R /ID [<" + id
                + "><" + id + ">] >>\nstartxref\n" + xref
                + "\n%%EOF\n");
        return pdf.toByteArray();
    }

    private static byte[] ownerEntry(
            String ownerPassword,
            String userPassword,
            int revision,
            int keyBits) throws Exception {
        byte[] digest = md5(paddedPassword(ownerPassword));
        if (revision >= 3) {
            for (int iteration = 0; iteration < 50; iteration++) {
                digest = md5(digest);
            }
        }
        byte[] key = Arrays.copyOf(digest, keyBits / 8);
        byte[] value = rc4(key, paddedPassword(userPassword));
        if (revision >= 3) {
            for (int iteration = 1; iteration <= 19; iteration++) {
                value = rc4(xor(key, iteration), value);
            }
        }
        return value;
    }

    private static byte[] fileEncryptionKey(
            String userPassword,
            byte[] owner,
            int permissions,
            byte[] identifier,
            int revision,
            int keyBits,
            boolean encryptMetadata) throws Exception {
        ByteArrayOutputStream material = new ByteArrayOutputStream();
        material.write(paddedPassword(userPassword));
        material.write(owner);
        material.write(permissions & 0xff);
        material.write((permissions >>> 8) & 0xff);
        material.write((permissions >>> 16) & 0xff);
        material.write((permissions >>> 24) & 0xff);
        material.write(identifier);
        if (revision >= 4 && !encryptMetadata) {
            material.write(0xff);
            material.write(0xff);
            material.write(0xff);
            material.write(0xff);
        }
        byte[] digest = md5(material.toByteArray());
        int keyBytes = keyBits / 8;
        if (revision >= 3) {
            for (int iteration = 0; iteration < 50; iteration++) {
                digest = md5(Arrays.copyOf(digest, keyBytes));
            }
        }
        return Arrays.copyOf(digest, keyBytes);
    }

    private static byte[] userEntry(
            byte[] fileKey,
            byte[] identifier,
            int revision) throws Exception {
        if (revision == 2) {
            return rc4(fileKey, PASSWORD_PADDING);
        }
        ByteArrayOutputStream material = new ByteArrayOutputStream();
        material.write(PASSWORD_PADDING);
        material.write(identifier);
        byte[] value = Arrays.copyOf(md5(material.toByteArray()), 16);
        value = rc4(fileKey, value);
        for (int iteration = 1; iteration <= 19; iteration++) {
            value = rc4(xor(fileKey, iteration), value);
        }
        return Arrays.copyOf(value, 32);
    }

    private static byte[] paddedPassword(String password) {
        byte[] raw = password.getBytes(StandardCharsets.ISO_8859_1);
        byte[] padded = new byte[32];
        int copied = Math.min(raw.length, padded.length);
        System.arraycopy(raw, 0, padded, 0, copied);
        if (copied < padded.length) {
            System.arraycopy(
                    PASSWORD_PADDING,
                    0,
                    padded,
                    copied,
                    padded.length - copied);
        }
        return padded;
    }

    private static byte[] md5(byte[] value) throws Exception {
        return MessageDigest.getInstance("MD5").digest(value);
    }

    private static byte[] xor(byte[] key, int value) {
        byte[] result = Arrays.copyOf(key, key.length);
        for (int index = 0; index < result.length; index++) {
            result[index] ^= (byte) value;
        }
        return result;
    }

    private static byte[] rc4(byte[] key, byte[] input) {
        int[] state = new int[256];
        for (int index = 0; index < state.length; index++) {
            state[index] = index;
        }
        int swapIndex = 0;
        for (int index = 0; index < state.length; index++) {
            swapIndex = (swapIndex + state[index]
                    + (key[index % key.length] & 0xff)) & 0xff;
            int swap = state[index];
            state[index] = state[swapIndex];
            state[swapIndex] = swap;
        }
        byte[] result = new byte[input.length];
        int first = 0;
        int second = 0;
        for (int index = 0; index < input.length; index++) {
            first = (first + 1) & 0xff;
            second = (second + state[first]) & 0xff;
            int swap = state[first];
            state[first] = state[second];
            state[second] = swap;
            int stream = state[(state[first] + state[second]) & 0xff];
            result[index] = (byte) (input[index] ^ stream);
        }
        return result;
    }

    private static String hex(byte[] value) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] encoded = new char[value.length * 2];
        for (int index = 0; index < value.length; index++) {
            encoded[index * 2] = digits[(value[index] >>> 4) & 0xf];
            encoded[index * 2 + 1] = digits[value[index] & 0xf];
        }
        return new String(encoded);
    }

    private static int indexOf(byte[] value, byte[] target) {
        for (int offset = 0; offset + target.length <= value.length; offset++) {
            int index = 0;
            while (index < target.length
                    && value[offset + index] == target[index]) {
                index++;
            }
            if (index == target.length) {
                return offset;
            }
        }
        return -1;
    }

    private ProtectedFixture protectedFixture(
            PasswordEncryptionAlgorithm algorithm,
            DocumentPermissions permissions,
            boolean legacy) throws Exception {
        PasswordCredential owner = PasswordCredential.of(
                ("fixture-owner-" + algorithm).toCharArray());
        PasswordCredential user = PasswordCredential.of(
                ("fixture-user-" + algorithm).toCharArray());
        try {
            PasswordSecurityPolicy security = PasswordSecurityPolicy.builder(
                            owner,
                            user)
                    .algorithm(algorithm)
                    .permissions(permissions)
                    .build();
            Path path = publishProtected(
                    security,
                    PdfVersion.PDF_1_7,
                    legacy
                            ? LegacySecurityMode
                                    .ALLOW_OBSOLETE_PASSWORD_ENCRYPTION
                            : null);
            return new ProtectedFixture(path, owner, user);
        } catch (Exception failure) {
            owner.close();
            user.close();
            throw failure;
        }
    }

    private Path publishProtected(
            PasswordSecurityPolicy security,
            PdfVersion version,
            LegacySecurityMode legacyMode) throws Exception {
        Path target = temporaryFolder.newFile().toPath();
        WorkflowRequest.Builder request = WorkflowRequest.builder()
                .target("target", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .outputPolicy(PdfOutputPolicy.version(version)
                        .withPasswordSecurity(security));
        if (legacyMode != null) {
            request.legacySecurityMode(legacyMode);
        }
        new DocumentWorkflow().execute(
                request.build(),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });
        return target;
    }

    private void assertOpeningFailure(
            DocumentSource source,
            DocumentFailureCode expected) throws Exception {
        PasswordCredential owner = PasswordCredential.of(
                "replacement-owner-t16".toCharArray());
        PasswordCredential user = PasswordCredential.of(
                "replacement-user-t16".toCharArray());
        try {
            Path target = temporaryFolder.newFile().toPath();
            Files.write(target, new byte[] {31, 32, 33});
            AtomicBoolean workRan = new AtomicBoolean();
            try {
                new DocumentWorkflow().execute(
                        WorkflowRequest.builder()
                                .source("source", source)
                                .primarySource("source")
                                .target("target", PublicationTarget.path(target))
                                .saveMode(SaveMode.REWRITE)
                                .outputPolicy(PdfOutputPolicy
                                        .version(PdfVersion.PDF_1_7)
                                        .withPasswordSecurity(
                                                PasswordSecurityPolicy.builder(
                                                        owner,
                                                        user).build()))
                                .build(),
                        session -> {
                            workRan.set(true);
                            return null;
                        });
                fail("Expected the opening credential to be rejected");
            } catch (DocumentFailure failure) {
                assertEquals(expected, failure.getCode());
                assertEquals(CAPABILITY, failure.getCapabilityId());
                assertEquals(
                        PublicationStatus.NOT_ATTEMPTED,
                        failure.getPublicationReceipts().get(0).getStatus());
                assertFalse(failure.getDiagnostic().contains("password-t16"));
            }
            assertFalse(workRan.get());
            assertEquals(3, Files.readAllBytes(target).length);
        } finally {
            owner.close();
            user.close();
        }
    }

    private void assertOutputSecurityFailure(
            PasswordSecurityPolicy security,
            LegacySecurityMode legacyMode,
            PdfVersion version,
            DocumentFailureCode expected) throws Exception {
        Path target = temporaryFolder.newFile().toPath();
        Files.write(target, new byte[] {41, 42, 43});
        AtomicBoolean workRan = new AtomicBoolean();
        WorkflowRequest.Builder request = WorkflowRequest.builder()
                .target("target", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .outputPolicy(PdfOutputPolicy.version(version)
                        .withPasswordSecurity(security));
        if (legacyMode != null) {
            request.legacySecurityMode(legacyMode);
        }
        try {
            new DocumentWorkflow().execute(
                    request.build(),
                    session -> {
                        workRan.set(true);
                        return null;
                    });
            fail("Expected the password-security policy to be rejected");
        } catch (DocumentFailure failure) {
            assertEquals(expected, failure.getCode());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
        }
        assertFalse(workRan.get());
        assertEquals(3, Files.readAllBytes(target).length);
    }

    private void assertInvalidOutputCredentials(char[] ownerInput, char[] userInput)
            throws Exception {
        PasswordCredential owner = PasswordCredential.of(ownerInput);
        PasswordCredential user = PasswordCredential.of(userInput);
        try {
            assertOutputSecurityFailure(
                    PasswordSecurityPolicy.builder(owner, user).build(),
                    null,
                    PdfVersion.PDF_1_7,
                    DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED);
        } finally {
            owner.close();
            user.close();
            Arrays.fill(ownerInput, '\0');
            Arrays.fill(userInput, '\0');
        }
    }

    private static void assertPermissionDenied(ThrowingAction operation)
            throws DocumentFailure {
        try {
            operation.run();
            fail("Expected the document permission to be enforced");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.DOCUMENT_PERMISSION_DENIED,
                    failure.getCode());
            assertEquals(CAPABILITY, failure.getCapabilityId());
        }
    }

    private static void assertSerializedSecurity(
            Path path,
            PasswordEncryptionAlgorithm algorithm,
            int revision) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        String serialized = new String(bytes, StandardCharsets.ISO_8859_1);
        assertTrue(serialized.contains("/R " + revision));
        if (algorithm == PasswordEncryptionAlgorithm.AES_256) {
            assertTrue(serialized.contains("/V 5"));
            assertTrue(serialized.contains("/Length 256"));
            assertTrue(serialized.contains("/CFM /AESV3"));
        } else if (algorithm == PasswordEncryptionAlgorithm.AES_128) {
            assertTrue(serialized.contains("/V 4"));
            assertTrue(serialized.contains("/Length 128"));
            assertTrue(serialized.contains("/CFM /AESV2"));
        } else if (algorithm == PasswordEncryptionAlgorithm.RC4_128) {
            assertTrue(serialized.contains("/V 2"));
            assertTrue(serialized.contains("/Length 128"));
        }
        if (algorithm == PasswordEncryptionAlgorithm.AES_256
                || algorithm == PasswordEncryptionAlgorithm.AES_128) {
            Matcher standardFilter = Pattern.compile(
                    "/StdCF\\s*<<(.*?)>>",
                    Pattern.DOTALL).matcher(serialized);
            assertTrue(standardFilter.find());
            assertFalse(standardFilter.group(1).contains("/Length"));
        }
    }

    private PasswordSecurityInfo securityOf(
            Path path,
            PasswordCredential credential) throws Exception {
        return new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source(
                                "source",
                                DocumentSource.path(path)
                                        .withCredential(credential))
                        .primarySource("source")
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> session.query(DocumentSecurity.INSTANCE))
                .getResult();
    }

    private void assertOutputVersion(Path path, PdfVersion expected)
            throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        assertEquals(
                "%PDF-" + expected,
                new String(bytes, 0, 8, StandardCharsets.US_ASCII));
        PdfVersionInfo reopened = new DocumentWorkflow().execute(
                WorkflowRequest.open(path, SaveMode.REWRITE),
                session -> session.query(DocumentVersion.INSTANCE))
                .getResult();
        assertEquals(expected, reopened.getHeaderVersion());
        assertFalse(reopened.getCatalogVersion().isPresent());
        assertEquals(expected, reopened.getEffectiveVersion());
    }

    private PdfVersionInfo versionOf(byte[] fixture) throws Exception {
        return new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source(
                                "primary",
                                DocumentSource.bytes(fixture, fixture.length))
                        .primarySource("primary")
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> session.query(DocumentVersion.INSTANCE))
                .getResult();
    }

    private PdfVersionInfo versionOf(
            Path path,
            PasswordCredential credential) throws Exception {
        return new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source(
                                "source",
                                DocumentSource.path(path)
                                        .withCredential(credential))
                        .primarySource("source")
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> session.query(DocumentVersion.INSTANCE))
                .getResult();
    }

    private static void assertReservedPatchRejected(
            DocumentSession session,
            ObjectReference target,
            String name,
            PdfValue value) throws DocumentFailure {
        try {
            session.execute(DocumentPatch.builder()
                    .setDictionaryEntry(target, PdfName.of(name), value)
                    .build());
            fail("Expected engine-owned state to be protected");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.COMMAND_REJECTED,
                    failure.getCode());
            assertEquals(CAPABILITY, failure.getCapabilityId());
        }
    }

    private static Set<Path> namedSourceSnapshots() throws IOException {
        Set<Path> snapshots = new HashSet<Path>();
        Path temporaryDirectory = Paths.get(
                System.getProperty("java.io.tmpdir"));
        try (DirectoryStream<Path> roots = Files.newDirectoryStream(
                temporaryDirectory,
                ".folio-pdf-workflow-*")) {
            for (Path root : roots) {
                if (!Files.isDirectory(root)) {
                    continue;
                }
                try (DirectoryStream<Path> paths = Files.newDirectoryStream(
                        root,
                        ".folio-pdf-source-*.pdf")) {
                    for (Path path : paths) {
                        snapshots.add(path.toAbsolutePath().normalize());
                    }
                }
            }
        }
        return snapshots;
    }

    private void assertVersionFailure(
            byte[] fixture,
            DocumentFailureCode expectedCode) throws Exception {
        Path target = temporaryFolder.newFile().toPath();
        byte[] sentinel = new byte[] {91, 92, 93};
        Files.write(target, sentinel);
        AtomicBoolean workRan = new AtomicBoolean();
        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source(
                                    "primary",
                                    DocumentSource.bytes(
                                            fixture,
                                            fixture.length))
                            .primarySource("primary")
                            .target("target", PublicationTarget.path(target))
                            .saveMode(SaveMode.REWRITE)
                            .build(),
                    session -> {
                        workRan.set(true);
                        return null;
                    });
            fail("Expected the version declaration to be rejected");
        } catch (DocumentFailure failure) {
            assertEquals(expectedCode, failure.getCode());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(1, failure.getPublicationReceipts().size());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
        }
        assertFalse(workRan.get());
        assertEquals(3, Files.readAllBytes(target).length);
    }

    private static byte[] minimalPdfWithContentStream() {
        String[] objects = new String[] {
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 72 72]"
                    + " /Resources << >> /Contents 4 0 R >>",
            "<< /Length 0 >>\nstream\n\nendstream"
        };
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        write(pdf, "%PDF-1.7\n%FolioT16SignatureFixture\n");
        int[] offsets = new int[objects.length + 1];
        for (int index = 0; index < objects.length; index++) {
            offsets[index + 1] = pdf.size();
            write(pdf, (index + 1) + " 0 obj\n" + objects[index]
                    + "\nendobj\n");
        }
        int xref = pdf.size();
        write(pdf, "xref\n0 " + (objects.length + 1) + "\n");
        write(pdf, "0000000000 65535 f \n");
        for (int index = 1; index < offsets.length; index++) {
            write(pdf, String.format("%010d 00000 n \n", offsets[index]));
        }
        write(pdf, "trailer\n<< /Size " + (objects.length + 1)
                + " /Root 1 0 R >>\nstartxref\n" + xref
                + "\n%%EOF\n");
        return pdf.toByteArray();
    }

    private static byte[] minimalPdf(
            PdfVersion headerVersion,
            String catalogVersion) {
        return minimalPdf(headerVersion.toString(), catalogVersion);
    }

    private static byte[] minimalPdf(
            String headerVersion,
            String catalogVersion) {
        return minimalPdf(headerVersion, catalogVersion, "");
    }

    private static byte[] minimalPdf(
            PdfVersion headerVersion,
            String catalogVersion,
            String catalogEntries) {
        return minimalPdf(
                headerVersion.toString(),
                catalogVersion,
                catalogEntries);
    }

    private static byte[] minimalPdf(
            String headerVersion,
            String catalogVersion,
            String catalogEntries) {
        String catalog = "<< /Type /Catalog /Pages 2 0 R"
                + (catalogVersion == null
                        ? ""
                        : " /Version /" + catalogVersion)
                + catalogEntries
                + " >>";
        String[] objects = new String[] {
            catalog,
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 72 72]"
                    + " /Resources << >> >>"
        };
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        write(pdf, "%PDF-" + headerVersion + "\n%FolioT16Fixture\n");
        int[] offsets = new int[objects.length + 1];
        for (int index = 0; index < objects.length; index++) {
            offsets[index + 1] = pdf.size();
            write(pdf, (index + 1) + " 0 obj\n" + objects[index]
                    + "\nendobj\n");
        }
        int xref = pdf.size();
        write(pdf, "xref\n0 " + (objects.length + 1) + "\n");
        write(pdf, "0000000000 65535 f \n");
        for (int index = 1; index < offsets.length; index++) {
            write(pdf, String.format("%010d 00000 n \n", offsets[index]));
        }
        write(pdf, "trailer\n<< /Size " + (objects.length + 1)
                + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return pdf.toByteArray();
    }

    private static void write(ByteArrayOutputStream output, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        output.write(bytes, 0, bytes.length);
    }

    private interface ThrowingAction {
        void run() throws DocumentFailure;
    }

    private static final class ProtectedFixture implements AutoCloseable {
        private final Path path;
        private final PasswordCredential owner;
        private final PasswordCredential user;

        private ProtectedFixture(
                Path path,
                PasswordCredential owner,
                PasswordCredential user) {
            this.path = path;
            this.owner = owner;
            this.user = user;
        }

        @Override
        public void close() {
            owner.close();
            user.close();
        }
    }

    private static final class TrackingInputStream
            extends ByteArrayInputStream {
        private boolean closed;

        private TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class TrackingChannel
            implements ReadableByteChannel {
        private final ReadableByteChannel delegate;
        private boolean closed;

        private TrackingChannel(byte[] bytes) {
            delegate = Channels.newChannel(new ByteArrayInputStream(bytes));
        }

        @Override
        public int read(ByteBuffer destination) throws IOException {
            return delegate.read(destination);
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }

        @Override
        public void close() throws IOException {
            closed = true;
            delegate.close();
        }
    }
}
