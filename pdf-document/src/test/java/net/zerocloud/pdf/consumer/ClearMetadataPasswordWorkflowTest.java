package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import net.zerocloud.pdf.CredentialAuthority;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentPermissions;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.LegacySecurityMode;
import net.zerocloud.pdf.PasswordCredential;
import net.zerocloud.pdf.PasswordEncryptionAlgorithm;
import net.zerocloud.pdf.PasswordEncryptionScope;
import net.zerocloud.pdf.PasswordSecurityInfo;
import net.zerocloud.pdf.PasswordSecurityPolicy;
import net.zerocloud.pdf.PdfOutputPolicy;
import net.zerocloud.pdf.PdfVersion;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.DocumentSecurity;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.ReadEmbeddedFile;
import net.zerocloud.pdf.query.XmpMetadata;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfString;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.command.MergeDocuments;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.PageObjectReference;
import net.zerocloud.pdf.query.InspectObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Scope-specific public behavior; independent byte proof lives in T79 acceptance. */
public final class ClearMetadataPasswordWorkflowTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private static final Path CORPUS = Paths.get(System.getProperty("repositoryRoot", ".."))
            .resolve("capabilities/profiles/T79-clear-metadata");

    @Test
    public void independentInputsAuthenticateEveryAdmittedProfileAndCredential() throws Exception {
        Properties cases = new Properties();
        try (InputStream input = Files.newInputStream(CORPUS.resolve("cases.properties"))) { cases.load(input); }
        for (String name : cases.getProperty("cases").split(",")) {
            if (!"success".equals(cases.getProperty(name + ".expected")) || "plain".equals(name)) { continue; }
            String kind = cases.getProperty(name + ".credential");
            try (PasswordCredential user = credential(user(kind));
                    PasswordCredential owner = credential("equal".equals(kind) ? "equal-baseline" : "baseline-owner")) {
                PasswordSecurityInfo security = security(CORPUS.resolve(name + ".pdf"), user);
                assertEquals(name, Boolean.parseBoolean(cases.getProperty(name + ".metadata"))
                        ? PasswordEncryptionScope.ALL_CONTENT : PasswordEncryptionScope.ALL_EXCEPT_METADATA, security.getEncryptionScope());
                assertEquals(name, Integer.parseInt(cases.getProperty(name + ".r")), security.getSecurityHandlerRevision());
                assertEquals(name, Integer.parseInt(cases.getProperty(name + ".permissions")), security.getDeclaredUserPermissions().getStandardMask());
                assertEquals(name, CredentialAuthority.OWNER, security(CORPUS.resolve(name + ".pdf"), owner).getCredentialAuthority());
                byte[] xmp = new DocumentWorkflow().execute(open(CORPUS.resolve(name + ".pdf"), owner).build(),
                        session -> session.query(XmpMetadata.version1(4096))).getResult();
                assertArrayEquals(name, Files.readAllBytes(CORPUS.resolve("metadata.xmp")), xmp);
                byte[] data = new DocumentWorkflow().execute(open(CORPUS.resolve(name + ".pdf"), owner).build(),
                        session -> session.query(ReadEmbeddedFile.version1("proof.txt", 4096)).get().getContent()).getResult();
                assertArrayEquals(name, Files.readAllBytes(CORPUS.resolve("attachment.txt")), data);
            }
        }
    }

    @Test
    public void explicitCreationAndOwnerRewriteReopenWithDeclaredScopeAndAuthority() throws Exception {
        try (PasswordCredential owner = credential("baseline-owner"); PasswordCredential user = credential("baseline-user")) {
            for (PasswordEncryptionAlgorithm algorithm : new PasswordEncryptionAlgorithm[] {
                    PasswordEncryptionAlgorithm.RC4_128, PasswordEncryptionAlgorithm.AES_128, PasswordEncryptionAlgorithm.AES_256}) {
                for (PdfVersion version : new PdfVersion[] {PdfVersion.PDF_1_7, PdfVersion.PDF_2_0}) {
                    if (version == PdfVersion.PDF_2_0 && algorithm != PasswordEncryptionAlgorithm.AES_256) { continue; }
                    PasswordSecurityPolicy policy = policy(owner, user, algorithm, version);
                    Path product = temporary.newFile().toPath();
                    WorkflowRequest.Builder request = builder().source("source", DocumentSource.path(CORPUS.resolve("plain.pdf")))
                            .primarySource("source").target("target", PublicationTarget.path(product))
                            .outputPolicy(PdfOutputPolicy.version(version).withPasswordSecurity(policy));
                    if (algorithm != PasswordEncryptionAlgorithm.AES_256) { request.legacySecurityMode(LegacySecurityMode.ALLOW_OBSOLETE_PASSWORD_ENCRYPTION); }
                    WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(request.build(), session -> null);
                    assertEquals(execution(), outcome.getExecutionProfile());
                    assertEquals(PublicationStatus.COMMITTED, outcome.getPublicationReceipts().get(0).getStatus());
                    PasswordSecurityInfo observed = security(product, user);
                    assertEquals(PasswordEncryptionScope.ALL_EXCEPT_METADATA, observed.getEncryptionScope());
                    assertEquals(algorithm, observed.getAlgorithm().get());
                    assertEquals(algorithm == PasswordEncryptionAlgorithm.AES_256 ? 6 : 4, observed.getSecurityHandlerRevision());
                    assertEquals(policy.getPermissions(), observed.getDeclaredUserPermissions());
                    assertEquals(CredentialAuthority.USER, observed.getCredentialAuthority());
                    assertEquals(CredentialAuthority.OWNER, security(product, owner).getCredentialAuthority());
                    assertArrayEquals(Files.readAllBytes(CORPUS.resolve("metadata.xmp")), new DocumentWorkflow().execute(open(product, owner).build(),
                            session -> session.query(XmpMetadata.version1(4096))).getResult());
                    Path rewritten = temporary.newFile().toPath();
                    new DocumentWorkflow().execute(open(product, owner).target("target", PublicationTarget.path(rewritten))
                            .outputPolicy(PdfOutputPolicy.version(version).withPasswordSecurity(policy(owner, user,
                                    PasswordEncryptionAlgorithm.AES_256, version))).build(), session -> null);
                    assertEquals(PasswordEncryptionScope.ALL_EXCEPT_METADATA, security(rewritten, user).getEncryptionScope());
                }
            }
        }
    }

    @Test
    public void malformedInputsAndMetadataSensitiveKeyOrPermissionDefectsFailBeforeWork() throws Exception {
        Properties cases = new Properties();
        try (InputStream input = Files.newInputStream(CORPUS.resolve("cases.properties"))) { cases.load(input); }
        try (PasswordCredential user = credential("baseline-user")) {
            for (String name : cases.getProperty("cases").split(",")) {
                String expected = cases.getProperty(name + ".expected");
                if ("success".equals(expected)) { continue; }
                assertPreflightFailure(open(CORPUS.resolve(name + ".pdf"), user), DocumentFailureCode.valueOf(expected));
            }
        }
    }

    @Test
    public void missingWrongDestroyedAndInvalidCredentialsFailSafely() throws Exception {
        Path source = CORPUS.resolve("aes-256-r6.pdf");
        assertPreflightFailure(builder().source("source", DocumentSource.path(source)).primarySource("source"), DocumentFailureCode.CREDENTIAL_REQUIRED);
        try (PasswordCredential wrong = credential("wrong-T79-secret")) {
            assertPreflightFailure(open(source, wrong), DocumentFailureCode.CREDENTIAL_REJECTED);
        }
        PasswordCredential destroyed = credential("baseline-user"); destroyed.close();
        assertPreflightFailure(open(source, destroyed), DocumentFailureCode.CREDENTIAL_DESTROYED);
        try (PasswordCredential owner = credential("baseline-owner"); PasswordCredential invalid = credential("x\u0007")) {
            assertPreflightFailure(builder().outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_1_7)
                    .withPasswordSecurity(policy(owner, invalid, PasswordEncryptionAlgorithm.AES_256, PdfVersion.PDF_1_7))),
                    DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED);
        }
    }

    @Test
    public void unselectedMetadataRemainsEncryptedAndLegacyRequestsNeedExplicitOptIn() throws Exception {
        try (PasswordCredential owner = credential("baseline-owner"); PasswordCredential user = credential("baseline-user")) {
            PasswordSecurityPolicy defaults = PasswordSecurityPolicy.builder(owner, user).build();
            assertEquals(PasswordEncryptionAlgorithm.AES_256, defaults.getAlgorithm());
            assertEquals(PasswordEncryptionScope.ALL_CONTENT, defaults.getEncryptionScope());
            for (PasswordEncryptionAlgorithm algorithm : new PasswordEncryptionAlgorithm[] {
                    PasswordEncryptionAlgorithm.AES_128, PasswordEncryptionAlgorithm.RC4_128}) {
                assertPreflightFailure(builder().outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_1_7)
                        .withPasswordSecurity(policy(owner, user, algorithm, PdfVersion.PDF_1_7))), DocumentFailureCode.LEGACY_SECURITY_MODE_REQUIRED);
            }
            assertPreflightFailure(builder().legacySecurityMode(LegacySecurityMode.ALLOW_OBSOLETE_PASSWORD_ENCRYPTION)
                    .outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_1_7).withPasswordSecurity(policy(owner, user,
                            PasswordEncryptionAlgorithm.RC4_40, PdfVersion.PDF_1_7))), DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED);
            Path product = temporary.newFile().toPath();
            new DocumentWorkflow().execute(builder().source("source", DocumentSource.path(CORPUS.resolve("plain.pdf"))).primarySource("source")
                    .target("target", PublicationTarget.path(product)).outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_1_7)
                            .withPasswordSecurity(defaults)).build(), session -> null);
            assertEquals(PasswordEncryptionScope.ALL_CONTENT, security(product, user).getEncryptionScope());
            assertFalse(new String(Files.readAllBytes(product), StandardCharsets.ISO_8859_1).contains("Folio clear XMP proof"));
        }
    }

    @Test
    public void incrementalPreservesProtectionAndDoesNotAcquireAScopeChangePath() throws Exception {
        try (PasswordCredential user = credential("baseline-user"); PasswordCredential owner = credential("baseline-owner")) {
            for (String name : new String[] {"rc4-cf", "aes-128", "aes-256-r5", "aes-256-r6"}) {
                Path source = CORPUS.resolve(name + "-assembly.pdf"), product = temporary.newFile().toPath();
                PasswordSecurityInfo before = security(source, user);
                assertEquals(CredentialAuthority.UNRESTRICTED, before.getCredentialAuthority());
                new DocumentWorkflow().execute(open(source, user).saveMode(SaveMode.INCREMENTAL)
                        .target("target", PublicationTarget.path(product)).build(), session -> { session.execute(AddBlankPage.INSTANCE); return null; });
                assertArrayEquals(Files.readAllBytes(source), Arrays.copyOf(Files.readAllBytes(product), (int) Files.size(source)));
                PasswordSecurityInfo after = security(product, user);
                assertEquals(before.getEncryptionScope(), after.getEncryptionScope());
                assertEquals(before.getSecurityHandlerRevision(), after.getSecurityHandlerRevision());
                assertEquals(Integer.valueOf(2), new DocumentWorkflow().execute(open(product, user).build(), session -> session.query(PageCount.INSTANCE)).getResult());
                assertPreflightFailure(open(source, owner).saveMode(SaveMode.INCREMENTAL).outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_1_7)
                        .withPasswordSecurity(PasswordSecurityPolicy.builder(owner, user).build())), DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED);
            }
        }
    }

    @Test
    public void permissionsAndIndependentOwnerProofConstrainProtectedRewriteAndQueries() throws Exception {
        try (PasswordCredential owner = credential("baseline-owner"); PasswordCredential user = credential("baseline-user")) {
            for (String name : new String[] {"rc4-cf", "aes-128", "aes-256-r5", "aes-256-r6", "aes-256-unrestricted"}) {
                Path source = CORPUS.resolve(name + ".pdf");
                PasswordSecurityInfo info = security(source, user);
                assertEquals(name.endsWith("unrestricted") ? CredentialAuthority.UNRESTRICTED : CredentialAuthority.USER,
                        info.getCredentialAuthority());
                assertPreflightFailure(open(source, user).outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_1_7)
                        .withPasswordSecurity(policy(owner, user, PasswordEncryptionAlgorithm.AES_256, PdfVersion.PDF_1_7))),
                        DocumentFailureCode.DOCUMENT_PERMISSION_DENIED);
                assertPreflightFailure(open(source, owner), DocumentFailureCode.PASSWORD_SECURITY_POLICY_REQUIRED);
                if (!name.endsWith("unrestricted")) {
                    for (boolean extraction : new boolean[] {false, true}) {
                        try {
                            new DocumentWorkflow().execute(open(source, user).build(), session -> {
                                if (extraction) { session.query(ReadEmbeddedFile.version1("proof.txt", 4096)); }
                                else { session.execute(AddBlankPage.INSTANCE); }
                                return null;
                            });
                            fail("Restricted clear-metadata users cannot modify or extract protected data");
                        } catch (DocumentFailure failure) {
                            assertEquals(DocumentFailureCode.DOCUMENT_PERMISSION_DENIED, failure.getCode());
                            assertNull(failure.getCause());
                        }
                    }
                }
            }
        }
    }

    @Test
    public void namedSourcesCannotWeakenAllContentDonorsAndClearDonorsCanMerge() throws Exception {
        Path baseline = CORPUS.getParent().resolve("T78-password/aes-256-r5.pdf");
        try (PasswordCredential owner = credential("baseline-owner"); PasswordCredential user = credential("baseline-user")) {
            for (Path donor : new Path[] {baseline, CORPUS.resolve("merge-donor.pdf")}) {
                Path target = temporary.newFile().toPath(); byte[] marker = {17, 18}; Files.write(target, marker);
                WorkflowRequest request = builder().source("primary", DocumentSource.path(CORPUS.getParent().resolve("T78-password/version-1-7.pdf")))
                        .primarySource("primary").source("donor", DocumentSource.path(donor).withCredential(owner))
                        .target("target", PublicationTarget.path(target)).outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_1_7)
                                .withPasswordSecurity(policy(owner, user, PasswordEncryptionAlgorithm.AES_256, PdfVersion.PDF_1_7))).build();
                try {
                    WorkflowOutcome<Integer> outcome = new DocumentWorkflow().execute(request, session -> {
                        session.execute(MergeDocuments.version1("donor")); return session.query(PageCount.INSTANCE);
                    });
                    assertFalse(donor.equals(baseline)); assertEquals(Integer.valueOf(2), outcome.getResult());
                    assertEquals(PasswordEncryptionScope.ALL_EXCEPT_METADATA, security(target, user).getEncryptionScope());
                } catch (DocumentFailure failure) {
                    assertEquals(failure.getCode() + ": " + failure.getDiagnostic(), baseline, donor);
                    assertEquals(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED, failure.getCode());
                    assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(0).getStatus());
                    assertArrayEquals(marker, Files.readAllBytes(target));
                }
            }
        }
    }

    @Test
    public void callerResourcesRemainOwnedAndOutcomesDetachOnSuccessAndFailure() throws Exception {
        byte[] bytes = Files.readAllBytes(CORPUS.resolve("aes-256-r6.pdf"));
        try (PasswordCredential user = credential("baseline-user"); PasswordCredential wrong = credential("private-T79-password")) {
            for (boolean failure : new boolean[] {false, true}) {
                TrackingInput input = new TrackingInput(bytes);
                ReadableByteChannel channel = Channels.newChannel(input);
                DocumentSource[] sources = {DocumentSource.stream(input, bytes.length), DocumentSource.channel(channel, bytes.length)};
                for (int index = 0; index < sources.length; index++) {
                    input.reset();
                    WorkflowRequest.Builder request = builder().source("source", sources[index].withCredential(failure ? wrong : user))
                            .primarySource("source");
                    if (failure) { assertPreflightFailure(request, DocumentFailureCode.CREDENTIAL_REJECTED); }
                    else {
                        WorkflowOutcome<PasswordSecurityInfo> outcome = new DocumentWorkflow().execute(request.build(),
                                session -> session.query(DocumentSecurity.INSTANCE));
                        assertEquals(execution(), outcome.getExecutionProfile());
                        assertEquals(PasswordEncryptionScope.ALL_EXCEPT_METADATA, outcome.getResult().getEncryptionScope());
                        assertEquals(DocumentPermissions.builder().build(), outcome.getResult().getDeclaredUserPermissions());
                    }
                    assertFalse(input.closed); assertTrue(channel.isOpen());
                    assertFalse(user.isDestroyed()); assertFalse(wrong.isDestroyed());
                }
                channel.close();
            }
        }
    }

    @Test
    public void publicationFailureRetainsOrderedReceiptsAndCallerStreamOwnership() throws Exception {
        Path committed = temporary.newFile().toPath(), untouched = temporary.newFile().toPath();
        byte[] marker = {20, 21}; Files.write(untouched, marker);
        final AtomicBoolean closed = new AtomicBoolean();
        OutputStream broken = new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("private-T79-output-path"); }
            @Override public void close() { closed.set(true); }
        };
        try (PasswordCredential user = credential("baseline-user"); PasswordCredential owner = credential("baseline-owner")) {
            try {
                new DocumentWorkflow().execute(open(CORPUS.resolve("aes-256-r6.pdf"), owner)
                        .outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_1_7).withPasswordSecurity(
                                policy(owner, user, PasswordEncryptionAlgorithm.AES_256, PdfVersion.PDF_1_7)))
                        .target("committed", PublicationTarget.path(committed)).target("broken", PublicationTarget.stream(broken))
                        .target("untouched", PublicationTarget.path(untouched)).build(), session -> null);
                fail("The failing output must report publication failure");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.PUBLICATION_FAILED, failure.getCode());
                assertEquals(3, failure.getPublicationReceipts().size());
                assertEquals(PublicationStatus.COMMITTED, failure.getPublicationReceipts().get(0).getStatus());
                assertEquals(PublicationStatus.FAILED, failure.getPublicationReceipts().get(1).getStatus());
                assertTrue(failure.getPublicationReceipts().get(1).isPartialOutputPossible());
                assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(2).getStatus());
                assertNull(failure.getCause()); assertFalse(failure.getDiagnostic().contains("private-T79"));
            }
            assertEquals(PasswordEncryptionScope.ALL_EXCEPT_METADATA, security(committed, user).getEncryptionScope());
            assertArrayEquals(marker, Files.readAllBytes(untouched)); assertFalse(closed.get());
            assertFalse(user.isDestroyed()); assertFalse(owner.isDestroyed());
        }
    }

    @Test
    public void ownerAuthorityStillIntersectsWithExistingSignatureRestrictions() throws Exception {
        try (PasswordCredential owner = credential("baseline-owner"); PasswordCredential user = credential("baseline-user")) {
            Path signed = temporary.newFile().toPath();
            new DocumentWorkflow().execute(open(CORPUS.resolve("aes-256-r6-assembly.pdf"), owner).saveMode(SaveMode.INCREMENTAL)
                    .target("target", PublicationTarget.path(signed)).build(), session -> {
                ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                ObjectReference page = session.query(PageObjectReference.version1(1));
                PdfDictionary dictionary = (PdfDictionary) session.query(InspectObject.version1(page, PdfInspectionLimits.of(16, 0)));
                ObjectReference content = ((PdfIndirectReference) dictionary.get(PdfName.of("Contents"))).getReference();
                PdfDictionary field = PdfDictionary.builder().put(PdfName.of("FT"), PdfName.of("Sig"))
                        .put(PdfName.of("V"), PdfIndirectReference.of(content)).build();
                session.execute(DocumentPatch.builder().setDictionaryEntry(content, PdfName.of("Contents"), PdfString.of(new byte[] {0}))
                        .setDictionaryEntry(content, PdfName.of("ByteRange"), PdfArray.of(PdfNumber.of(0), PdfNumber.of(1)))
                        .setDictionaryEntry(root, PdfName.of("AcroForm"), PdfDictionary.builder()
                                .put(PdfName.of("Fields"), PdfArray.of(field)).put(PdfName.of("SigFlags"), PdfNumber.of(3)).build()).build());
                return null;
            });
            assertPreflightFailure(open(signed, owner).outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_1_7)
                            .withPasswordSecurity(policy(owner, user, PasswordEncryptionAlgorithm.AES_256, PdfVersion.PDF_1_7))),
                    DocumentFailureCode.SIGNED_REWRITE_REJECTED, "document.incremental-signature.protect");
        }
    }

    @Test
    public void componentMetadataAndMetadataDictionaryStringsRemainOrdinaryProtectedData() throws Exception {
        final byte[] xmp = Files.readAllBytes(CORPUS.resolve("metadata.xmp"));
        try (PasswordCredential owner = credential("baseline-owner")) {
            for (String name : new String[] {"aes-128", "aes-256-r6", "metadata-crypt-array", "component-crypt"}) {
                new DocumentWorkflow().execute(open(CORPUS.resolve(name + ".pdf"), owner).build(), session -> {
                    PdfDictionary page = (PdfDictionary) session.query(InspectObject.version1(
                            session.query(PageObjectReference.version1(1)), PdfInspectionLimits.of(32, 0)));
                    PdfStream component = (PdfStream) session.query(InspectObject.version1(
                            ((PdfIndirectReference) page.get(PdfName.of("Metadata"))).getReference(), PdfInspectionLimits.of(32, 4096)));
                    assertArrayEquals(new String(xmp, StandardCharsets.UTF_8)
                            .replace("clear XMP", "protected component").getBytes(StandardCharsets.UTF_8), component.readBytes());
                    PdfDictionary root = (PdfDictionary) session.query(InspectObject.version1(
                            session.query(DocumentRootReference.INSTANCE), PdfInspectionLimits.of(32, 0)));
                    PdfStream metadata = (PdfStream) session.query(InspectObject.version1(
                            ((PdfIndirectReference) root.get(PdfName.of("Metadata"))).getReference(), PdfInspectionLimits.of(32, 4096)));
                    assertArrayEquals("Folio protected metadata-like Info value".getBytes(StandardCharsets.UTF_8),
                            ((PdfString) metadata.getDictionary().get(PdfName.of("FolioProof"))).getBytes());
                    return null;
                });
            }
        }
    }

    @Test
    public void privateTemporaryMaterialIsReleasedAfterSuccessAndCallerFailure() throws Exception {
        Path storage = temporary.newFolder().toPath();
        DocumentWorkflow workflow = new DocumentWorkflow(WorkflowEnvironment.builder().temporaryDirectory(storage).build());
        try (PasswordCredential owner = credential("baseline-owner"); PasswordCredential user = credential("baseline-user")) {
            for (boolean failWork : new boolean[] {false, true}) {
                Path target = temporary.newFile().toPath(); byte[] marker = {3, 4}; Files.write(target, marker);
                RuntimeException sentinel = new RuntimeException("private-T79-caller");
                try {
                    workflow.execute(open(CORPUS.resolve("aes-256-r6.pdf"), owner)
                            .target("target", PublicationTarget.path(target))
                            .outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_1_7).withPasswordSecurity(
                                    policy(owner, user, PasswordEncryptionAlgorithm.AES_256, PdfVersion.PDF_1_7)))
                            .build(), session -> { if (failWork) { throw sentinel; } return session.query(DocumentSecurity.INSTANCE); });
                    assertFalse(failWork);
                } catch (RuntimeException failure) {
                    assertTrue(failWork); assertTrue(failure == sentinel); assertArrayEquals(marker, Files.readAllBytes(target));
                }
                try (java.util.stream.Stream<Path> paths = Files.list(storage)) { assertEquals(0L, paths.count()); }
                assertFalse(owner.isDestroyed()); assertFalse(user.isDestroyed());
            }
        }
    }

    @Test
    public void explicitCryptSourcesCanRewriteUnderEitherSelectedScope() throws Exception {
        byte[] xmp = Files.readAllBytes(CORPUS.resolve("metadata.xmp"));
        try (PasswordCredential owner = credential("baseline-owner"); PasswordCredential user = credential("baseline-user")) {
            for (String name : new String[] {"all-content-metadata-stdcf", "plain-identity", "metadata-crypt-array-default"}) {
                for (PasswordEncryptionScope scope : new PasswordEncryptionScope[] {
                        PasswordEncryptionScope.ALL_CONTENT, PasswordEncryptionScope.ALL_EXCEPT_METADATA}) {
                    Path output = temporary.newFile().toPath();
                    DocumentSource source = DocumentSource.path(CORPUS.resolve(name + ".pdf"));
                    if (!name.startsWith("plain")) { source = source.withCredential(owner); }
                    new DocumentWorkflow().execute(builder().source("source", source).primarySource("source")
                            .target("target", PublicationTarget.path(output))
                            .outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_1_7).withPasswordSecurity(
                                    PasswordSecurityPolicy.builder(owner, user).encryptionScope(scope).build()))
                            .build(), session -> null);
                    assertEquals(scope, security(output, user).getEncryptionScope());
                    assertArrayEquals(xmp, new DocumentWorkflow().execute(open(output, owner).build(),
                            session -> session.query(XmpMetadata.version1(4096))).getResult());
                }
            }
            assertPreflightFailure(builder().source("source", DocumentSource.path(CORPUS.resolve("plain-malformed-crypt.pdf")))
                    .primarySource("source").outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_1_7).withPasswordSecurity(
                            policy(owner, user, PasswordEncryptionAlgorithm.AES_256, PdfVersion.PDF_1_7))),
                    DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED);
        }
    }

    private static final class TrackingInput extends ByteArrayInputStream {
        private boolean closed;
        TrackingInput(byte[] bytes) { super(bytes); }
        @Override public void close() throws IOException { closed = true; super.close(); }
    }

    private void assertPreflightFailure(WorkflowRequest.Builder request, DocumentFailureCode code) throws Exception {
        assertPreflightFailure(request, code, "document.version-password-security");
    }

    private void assertPreflightFailure(WorkflowRequest.Builder request, DocumentFailureCode code, String capability) throws Exception {
        Path first = temporary.newFile().toPath(), second = temporary.newFile().toPath();
        byte[] marker = {41, 42, 43}; Files.write(first, marker); Files.write(second, marker);
        AtomicBoolean work = new AtomicBoolean();
        try {
            new DocumentWorkflow().execute(request.target("first", PublicationTarget.path(first))
                    .target("second", PublicationTarget.path(second)).build(), session -> { work.set(true); return null; });
            fail("Invalid security request must fail before work");
        } catch (DocumentFailure failure) {
            assertEquals(code, failure.getCode());
            assertEquals(capability, failure.getCapabilityId());
            assertEquals(2, failure.getPublicationReceipts().size());
            for (int index = 0; index < 2; index++) {
                assertEquals(index == 0 ? "first" : "second", failure.getPublicationReceipts().get(index).getTargetName());
                assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(index).getStatus());
            }
            assertNull(failure.getCause());
            for (String secret : new String[] {"baseline-user", "baseline-owner", "wrong-T79-secret", first.toString(), "org.apache.pdfbox"}) {
                assertFalse(failure.getDiagnostic().contains(secret));
            }
        }
        assertFalse(work.get()); assertArrayEquals(marker, Files.readAllBytes(first)); assertArrayEquals(marker, Files.readAllBytes(second));
    }

    private static PasswordSecurityPolicy policy(PasswordCredential owner, PasswordCredential user,
            PasswordEncryptionAlgorithm algorithm, PdfVersion version) {
        return PasswordSecurityPolicy.builder(owner, user).algorithm(algorithm)
                .encryptionScope(PasswordEncryptionScope.ALL_EXCEPT_METADATA)
                .permissions(DocumentPermissions.builder().allowAccessibilityExtraction(version == PdfVersion.PDF_2_0).build()).build();
    }
    private static WorkflowExecutionProfile execution() {
        return WorkflowExecutionProfile.valueOf(System.getProperty("folio.t79.executionProfile", "IN_PROCESS"));
    }
    private static WorkflowRequest.Builder builder() { return WorkflowRequest.builder().executionProfile(execution()).saveMode(SaveMode.REWRITE); }
    private static WorkflowRequest.Builder open(Path path, PasswordCredential password) {
        return builder().source("source", DocumentSource.path(path).withCredential(password)).primarySource("source");
    }
    private static PasswordSecurityInfo security(Path path, PasswordCredential password) throws Exception {
        return new DocumentWorkflow().execute(open(path, password).build(), session -> session.query(DocumentSecurity.INSTANCE)).getResult();
    }
    private static PasswordCredential credential(String value) { return PasswordCredential.of(value.toCharArray()); }
    private static String user(String kind) {
        if ("empty-user".equals(kind)) { return ""; }
        if ("equal".equals(kind)) { return "equal-baseline"; }
        if ("unicode".equals(kind)) { return "I\u00adX\u00a0\u2168"; }
        char[] value;
        if ("legacy-32".equals(kind)) { value = new char[33]; Arrays.fill(value, '\u00e9'); return new String(value); }
        if ("unicode-split".equals(kind)) { value = new char[126]; Arrays.fill(value, 'a'); return new String(value) + "\u00e9"; }
        if ("long".equals(kind)) { value = new char[128]; Arrays.fill(value, 'a'); return new String(value); }
        return "baseline-user";
    }
}
