package net.zerocloud.pdf.consumer;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import net.zerocloud.pdf.*;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.command.EmbedFile;
import net.zerocloud.pdf.command.MergeDocuments;
import net.zerocloud.pdf.query.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Attachment access and clear document observations through actual Workflow profiles. */
public final class EmbeddedFilesPasswordWorkflowTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private static final Path CORPUS = Paths.get(System.getProperty("repositoryRoot", ".."))
            .resolve("capabilities/profiles/T80-embedded-files-only");

    @Test public void originalInputsOpenClearContentWithoutAttachmentCredentials() throws Exception {
        Properties cases = cases();
        byte[] expectedXmp = Files.readAllBytes(CORPUS.resolve("metadata.xmp"));
        for (String name : cases.getProperty("cases").split(",")) {
            if (!"success".equals(cases.getProperty(name + ".expected")) || "plain".equals(name)) { continue; }
            if (!"EFOpen".equals(cases.getProperty(name + ".event"))) {
                try { new DocumentWorkflow().execute(open(file(name), null).build(), session -> session.query(PageCount.INSTANCE)); fail("DocOpen requires an explicit credential"); }
                catch (DocumentFailure failure) { assertEquals(DocumentFailureCode.CREDENTIAL_REQUIRED, failure.getCode()); }
                continue;
            }
            WorkflowOutcome<PasswordSecurityInfo> outcome = new DocumentWorkflow().execute(open(file(name), null).build(), session -> {
                assertEquals(Integer.valueOf(1), session.query(PageCount.INSTANCE));
                assertEquals(1, session.query(EmbeddedFiles.version1(8)).size());
                assertArrayEquals(expectedXmp, session.query(XmpMetadata.version1(4096)));
                ObjectReference page = session.query(PageObjectReference.version1(1));
                PdfDictionary dictionary = (PdfDictionary) session.query(InspectObject.version1(page, PdfInspectionLimits.of(1000, 4096)));
                PdfIndirectReference contents = (PdfIndirectReference) dictionary.get(PdfName.of("Contents"));
                PdfStream stream = (PdfStream) session.query(InspectObject.version1(contents.getReference(), PdfInspectionLimits.of(1000, 4096)));
                assertArrayEquals("0 0 1 rg 12 16 24 20 re f\n".getBytes(StandardCharsets.US_ASCII), stream.readBytes());
                return session.query(DocumentSecurity.INSTANCE);
            });
            assertEquals(name, execution(), outcome.getExecutionProfile());
            assertEquals(name, PasswordEncryptionScope.EMBEDDED_FILES_ONLY, outcome.getResult().getEncryptionScope());
            assertEquals(name, CredentialAuthority.NONE, outcome.getResult().getCredentialAuthority());
            assertEquals(name, Integer.parseInt(cases.getProperty(name + ".r")), outcome.getResult().getSecurityHandlerRevision());
        }
    }

    @Test public void correctUserAndOwnerAuthenticateEveryProfileAndCredentialExactly() throws Exception {
        Properties cases = cases();
        for (String name : cases.getProperty("cases").split(",")) {
            if (!"success".equals(cases.getProperty(name + ".expected")) || "plain".equals(name)) { continue; }
            String kind = cases.getProperty(name + ".credential");
            try (PasswordCredential owner = credential("equal".equals(kind) ? "equal-baseline" : "baseline-owner");
                    PasswordCredential user = credential(user(kind))) {
                PasswordSecurityInfo info = security(file(name), user);
                assertEquals(name, Integer.parseInt(cases.getProperty(name + ".permissions")), info.getDeclaredUserPermissions().getStandardMask());
                assertEquals(name, CredentialAuthority.OWNER, security(file(name), owner).getCredentialAuthority());
                assertArrayEquals(name, payload(), read(file(name), owner));
                if (info.getEffectivePermissions().canExtractContent()) { assertArrayEquals(name, payload(), read(file(name), user)); }
                else { assertQueryFailure(file(name), user, DocumentFailureCode.DOCUMENT_PERMISSION_DENIED); }
            }
        }
    }

    @Test public void missingWrongAndDestroyedCredentialsHaveSafeDistinctBoundaries() throws Exception {
        Path source = file("aes-256-extraction");
        assertQueryFailure(source, null, DocumentFailureCode.CREDENTIAL_REQUIRED);
        try (PasswordCredential wrong = credential("T80-invalid-secret")) {
            assertPreflightFailure(open(source, wrong), DocumentFailureCode.CREDENTIAL_REJECTED);
        }
        PasswordCredential destroyed = credential("baseline-user"); destroyed.close();
        assertPreflightFailure(open(source, destroyed), DocumentFailureCode.CREDENTIAL_DESTROYED);
        assertPreflightFailure(open(source, null), DocumentFailureCode.CREDENTIAL_REQUIRED);
    }

    @Test public void malformedRoutesAndPermissionAgreementFailBeforeWorkAndPublication() throws Exception {
        Properties cases = cases();
        try (PasswordCredential owner = credential("baseline-owner")) {
            for (String name : cases.getProperty("cases").split(",")) {
                String expected = cases.getProperty(name + ".expected");
                if (!"success".equals(expected)) { assertPreflightFailure(open(file(name), owner), DocumentFailureCode.valueOf(expected)); }
            }
        }
    }

    @Test public void explicitCreationAndOwnerRewritePreserveExactAttachmentsAndSecureDefaults() throws Exception {
        byte[] expectedPayload = payload();
        try (PasswordCredential owner = credential("baseline-owner"); PasswordCredential user = credential("baseline-user")) {
            assertEquals(PasswordEncryptionScope.ALL_CONTENT, PasswordSecurityPolicy.builder(owner, user).build().getEncryptionScope());
            assertEquals(PasswordEncryptionAlgorithm.AES_256, PasswordSecurityPolicy.builder(owner, user).build().getAlgorithm());
            for (PasswordEncryptionAlgorithm algorithm : new PasswordEncryptionAlgorithm[] {
                    PasswordEncryptionAlgorithm.RC4_128, PasswordEncryptionAlgorithm.AES_128, PasswordEncryptionAlgorithm.AES_256}) {
                for (PdfVersion version : new PdfVersion[] {PdfVersion.PDF_1_7, PdfVersion.PDF_2_0}) {
                    if (version == PdfVersion.PDF_2_0 && algorithm != PasswordEncryptionAlgorithm.AES_256) { continue; }
                    Path target = temporary.newFile().toPath();
                    WorkflowOutcome<Void> created = new DocumentWorkflow().execute(output(target, owner, user, algorithm, version).build(), session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        session.execute(EmbedFile.version1(EmbeddedFile.version1("proof.txt", expectedPayload)));
                        return null;
                    });
                    assertEquals(execution(), created.getExecutionProfile());
                    assertEquals(PublicationStatus.COMMITTED, created.getPublicationReceipts().get(0).getStatus());
                    assertEquals(PasswordEncryptionScope.EMBEDDED_FILES_ONLY, security(target, null).getEncryptionScope());
                    assertEquals(algorithm, security(target, user).getAlgorithm().get());
                    assertArrayEquals(payload(), read(target, owner));
                    assertArrayEquals(payload(), read(target, user));
                    assertQueryFailure(target, null, DocumentFailureCode.CREDENTIAL_REQUIRED);
                    Path rewritten = temporary.newFile().toPath();
                    new DocumentWorkflow().execute(output(rewritten, owner, user, PasswordEncryptionAlgorithm.AES_256, version)
                            .source("source", DocumentSource.path(target).withCredential(owner)).primarySource("source").build(), session -> null);
                    assertArrayEquals(payload(), read(rewritten, user));
                    assertEquals(CredentialAuthority.OWNER, security(rewritten, owner).getCredentialAuthority());
                }
            }
        }
    }

    @Test public void legacyAndUnrepresentablePoliciesCannotSilentlyChangeCoverage() throws Exception {
        try (PasswordCredential owner = credential("baseline-owner"); PasswordCredential user = credential("baseline-user")) {
            for (PasswordEncryptionAlgorithm algorithm : new PasswordEncryptionAlgorithm[] {PasswordEncryptionAlgorithm.RC4_128, PasswordEncryptionAlgorithm.AES_128}) {
                assertPreflightFailure(builder().outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_1_7)
                        .withPasswordSecurity(policy(owner, user, algorithm, PdfVersion.PDF_1_7))), DocumentFailureCode.LEGACY_SECURITY_MODE_REQUIRED);
            }
            assertPreflightFailure(builder().legacySecurityMode(LegacySecurityMode.ALLOW_OBSOLETE_PASSWORD_ENCRYPTION)
                    .outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_1_7).withPasswordSecurity(policy(owner, user,
                            PasswordEncryptionAlgorithm.RC4_40, PdfVersion.PDF_1_7))), DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED);
        }
    }

    @Test public void protectedRewriteAndIncrementalPublicationRetainOwnerAndPolicyRestrictions() throws Exception {
        try (PasswordCredential owner = credential("baseline-owner"); PasswordCredential user = credential("baseline-user")) {
            assertPreflightFailure(open(file("aes-256-r6"), owner), DocumentFailureCode.PASSWORD_SECURITY_POLICY_REQUIRED);
            assertPreflightFailure(open(file("aes-256-r6"), user), DocumentFailureCode.DOCUMENT_PERMISSION_DENIED);
            for (String name : new String[] {"rc4-cf", "aes-128", "aes-256-r5", "aes-256-r6"}) {
                Path source = file(name + "-assembly"), target = temporary.newFile().toPath();
                WorkflowOutcome<Void> appended = new DocumentWorkflow().execute(open(source, user).saveMode(SaveMode.INCREMENTAL)
                        .target("target", PublicationTarget.path(target)).build(), session -> { session.execute(AddBlankPage.INSTANCE); return null; });
                assertEquals(PublicationStatus.COMMITTED, appended.getPublicationReceipts().get(0).getStatus());
                assertArrayEquals(Files.readAllBytes(source), Arrays.copyOf(Files.readAllBytes(target), (int) Files.size(source)));
                assertEquals(Integer.valueOf(2), new DocumentWorkflow().execute(open(target, null).build(), s -> s.query(PageCount.INSTANCE)).getResult());
                assertArrayEquals(payload(), read(target, user));
            }
            assertPreflightFailure(open(file("aes-256-r6-assembly"), owner).saveMode(SaveMode.INCREMENTAL)
                    .outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_1_7).withPasswordSecurity(policy(owner, user,
                            PasswordEncryptionAlgorithm.AES_256, PdfVersion.PDF_1_7))), DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED);
        }
    }

    @Test public void incrementalAttachmentReplacementPreservesActualFilterRoutes() throws Exception {
        byte[] replacement = "T80 newly protected incremental attachment\n".getBytes(StandardCharsets.US_ASCII);
        try (PasswordCredential user = credential("baseline-user")) {
            for (String name : new String[] {"rc4-cf-assembly", "aes-128-assembly", "aes-256-r5-assembly", "aes-256-r6-assembly", "aes-256-explicit-assembly", "rc4-explicit-pdf15-assembly"}) {
                Path source = file(name), product = temporary.newFile().toPath();
                WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(open(source, user).saveMode(SaveMode.INCREMENTAL)
                        .target("target", PublicationTarget.path(product)).build(), session -> {
                    session.execute(EmbedFile.version1(EmbeddedFile.version1("proof.txt", replacement)));
                    return null;
                });
                assertEquals(PublicationStatus.COMMITTED, outcome.getPublicationReceipts().get(0).getStatus());
                assertArrayEquals(Files.readAllBytes(source), Arrays.copyOf(Files.readAllBytes(product), (int) Files.size(source)));
                assertArrayEquals(replacement, read(product, user));
                assertQueryFailure(product, null, DocumentFailureCode.CREDENTIAL_REQUIRED);
                assertFalse(new String(Files.readAllBytes(product), StandardCharsets.ISO_8859_1).contains(new String(replacement, StandardCharsets.US_ASCII)));
            }
        }
    }

    @Test public void pdfValuesCannotBypassAttachmentAuthenticationOrPermissions() throws Exception {
        try (PasswordCredential restricted = credential("baseline-user")) {
            for (PasswordCredential selected : new PasswordCredential[] {null, restricted}) {
                try {
                    new DocumentWorkflow().execute(open(file("aes-256-r6"), selected).build(), session -> {
                        PdfDictionary root = (PdfDictionary) session.query(InspectObject.version1(session.query(DocumentRootReference.INSTANCE), PdfInspectionLimits.of(1000, 4096)));
                        PdfDictionary names = (PdfDictionary) root.get(PdfName.of("Names"));
                        PdfDictionary tree = (PdfDictionary) names.get(PdfName.of("EmbeddedFiles"));
                        PdfArray entries = (PdfArray) tree.get(PdfName.of("Names"));
                        PdfDictionary spec = (PdfDictionary) session.query(InspectObject.version1(((PdfIndirectReference) entries.get(1)).getReference(), PdfInspectionLimits.of(1000, 4096)));
                        PdfDictionary ef = (PdfDictionary) spec.get(PdfName.of("EF"));
                        return session.query(InspectObject.version1(((PdfIndirectReference) ef.get(PdfName.of("F"))).getReference(), PdfInspectionLimits.of(1000, 4096)));
                    });
                    fail("Protected stream inspection requires attachment authority");
                } catch (DocumentFailure failure) {
                    assertEquals(selected == null ? DocumentFailureCode.CREDENTIAL_REQUIRED : DocumentFailureCode.DOCUMENT_PERMISSION_DENIED, failure.getCode());
                    assertNull(failure.getCause());
                }
            }
        }
    }

    @Test public void mixedNamedSourcesAndCallerStreamsPreserveCredentialsAndPreventWeakening() throws Exception {
        try (PasswordCredential owner = credential("baseline-owner"); PasswordCredential user = credential("baseline-user")) {
            BorrowedInput input = new BorrowedInput(Files.readAllBytes(file("aes-256-r6")));
            Path target = temporary.newFile().toPath();
            WorkflowRequest request = output(target, owner, user, PasswordEncryptionAlgorithm.AES_256, PdfVersion.PDF_1_7)
                    .source("primary", DocumentSource.path(file("plain"))).primarySource("primary")
                    .source("donor", DocumentSource.stream(input, 32768).withCredential(owner)).build();
            new DocumentWorkflow().execute(request, session -> { session.execute(MergeDocuments.version1("donor")); return null; });
            assertFalse(input.closed);
            assertEquals(CredentialAuthority.OWNER, security(file("aes-256-r6"), owner).getCredentialAuthority());
            assertPreflightFailure(builder().source("primary", DocumentSource.path(file("plain"))).primarySource("primary")
                    .source("donor", DocumentSource.path(file("aes-256-r6"))), DocumentFailureCode.PASSWORD_SECURITY_POLICY_REQUIRED);
            assertPreflightFailure(builder().outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_1_7)
                    .withPasswordSecurity(policy(owner, user, PasswordEncryptionAlgorithm.AES_256, PdfVersion.PDF_1_7)))
                    .source("primary", DocumentSource.path(file("plain"))).primarySource("primary")
                    .source("donor", DocumentSource.path(CORPUS.getParent().resolve("T78-password/aes-256-r5.pdf")).withCredential(owner)),
                    DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED);
        }
    }

    @Test public void attachmentAndWorkflowLimitsFailSafelyAndCleanTemporaryResources() throws Exception {
        try (PasswordCredential owner = credential("baseline-owner")) {
            try { new DocumentWorkflow().execute(open(file("aes-256-r6"), owner).build(), s -> s.query(ReadEmbeddedFile.version1("proof.txt", 1))); fail("Attachment limit"); }
            catch (DocumentFailure failure) { assertEquals(DocumentFailureCode.METADATA_LIMIT_EXCEEDED, failure.getCode()); }
            assertPreflightFailure(open(file("aes-256-r6"), owner).resourcePolicy(limits(16, 1048576)), DocumentFailureCode.WORKFLOW_INPUT_LIMIT_EXCEEDED);
            assertPreflightFailure(open(file("aes-256-compressed"), owner).resourcePolicy(limits(1048576, 1)), DocumentFailureCode.DECOMPRESSION_LIMIT_EXCEEDED);
        }
    }

    @Test public void callerResourcesAndAttachmentResultsRemainDetachedAndOwned() throws Exception {
        Path scratch = temporary.newFolder("owned-resources").toPath();
        DocumentWorkflow workflow = new DocumentWorkflow(WorkflowEnvironment.builder().temporaryDirectory(scratch).build());
        byte[] bytes = Files.readAllBytes(file("aes-256-extraction"));
        try (PasswordCredential user = credential("baseline-user"); PasswordCredential wrong = credential("private-T80-invalid")) {
            for (boolean failing : new boolean[] {false, true}) {
                BorrowedInput input = new BorrowedInput(bytes);
                java.nio.channels.ReadableByteChannel channel = java.nio.channels.Channels.newChannel(input);
                DocumentSource[] sources = {DocumentSource.stream(input, bytes.length), DocumentSource.channel(channel, bytes.length)};
                for (DocumentSource source : sources) {
                    input.reset();
                    WorkflowRequest.Builder request = builder().source("source", source.withCredential(failing ? wrong : user)).primarySource("source");
                    if (failing) { assertPreflightFailure(workflow, request, DocumentFailureCode.CREDENTIAL_REJECTED); }
                    else {
                        EmbeddedFileData result = workflow.execute(request.build(), session -> session.query(ReadEmbeddedFile.version1("proof.txt", 4096)).get()).getResult();
                        byte[] detached = result.getContent(); detached[0] ^= 1;
                        assertArrayEquals(payload(), result.getContent());
                        assertEquals("proof.txt", result.getName());
                        assertEquals(payload().length, result.getSize());
                    }
                    assertFalse(input.closed); assertTrue(channel.isOpen());
                    assertFalse(user.isDestroyed()); assertFalse(wrong.isDestroyed());
                    try (java.util.stream.Stream<Path> remaining = Files.list(scratch)) { assertEquals(0L, remaining.count()); }
                }
                channel.close();
            }
        }
        PdfStream expired = workflow.execute(open(file("aes-256-r6"), null).build(), session -> {
            PdfDictionary page = (PdfDictionary) session.query(InspectObject.version1(session.query(PageObjectReference.version1(1)), PdfInspectionLimits.of(64, 4096)));
            return (PdfStream) session.query(InspectObject.version1(((PdfIndirectReference) page.get(PdfName.of("Contents"))).getReference(), PdfInspectionLimits.of(64, 4096)));
        }).getResult();
        try { expired.readBytes(); fail("A Session stream view must expire"); }
        catch (DocumentFailure expected) { assertEquals(DocumentFailureCode.PDF_VALUE_VIEW_EXPIRED, expected.getCode()); }
    }

    @Test public void memoryTemporaryStorageAndElapsedLimitsCleanOwnedResources() throws Exception {
        Path scratch = temporary.newFolder("bounded-resources").toPath();
        DocumentWorkflow workflow = new DocumentWorkflow(WorkflowEnvironment.builder().temporaryDirectory(scratch).build());
        try (PasswordCredential owner = credential("baseline-owner")) {
            assertPreflightFailure(workflow, open(file("aes-256-r6"), owner).resourcePolicy(resourceLimits(16, 1048576, java.time.Duration.ofMinutes(5))), DocumentFailureCode.MEMORY_LIMIT_EXCEEDED);
            assertPreflightFailure(workflow, open(file("aes-256-r6"), owner).resourcePolicy(resourceLimits(104857600, 16, java.time.Duration.ofMinutes(5))), DocumentFailureCode.TEMPORARY_STORAGE_LIMIT_EXCEEDED);
            java.time.Clock advancing = new java.time.Clock() {
                private long calls;
                @Override public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
                @Override public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
                @Override public java.time.Instant instant() { return java.time.Instant.ofEpochSecond(calls++ * 2); }
            };
            DocumentWorkflow elapsed = new DocumentWorkflow(WorkflowEnvironment.builder().temporaryDirectory(scratch).clock(advancing).build());
            assertPreflightFailure(elapsed, open(file("aes-256-r6"), owner).resourcePolicy(resourceLimits(104857600, 104857600, java.time.Duration.ofSeconds(1))), DocumentFailureCode.ELAPSED_TIME_LIMIT_EXCEEDED);
            try (java.util.stream.Stream<Path> remaining = Files.list(scratch)) { assertEquals(0L, remaining.count()); }
            assertArrayEquals(payload(), read(file("aes-256-r6"), owner));
        }
    }

    @Test public void attachmentReplacementAndPageMutationKeepPayloadProtection() throws Exception {
        byte[] replacement = "T80 detached replacement payload\n".getBytes(StandardCharsets.US_ASCII);
        try (PasswordCredential owner = credential("baseline-owner"); PasswordCredential user = credential("baseline-user")) {
            Path product = temporary.newFile().toPath();
            new DocumentWorkflow().execute(output(product, owner, user, PasswordEncryptionAlgorithm.AES_256, PdfVersion.PDF_1_7)
                    .source("source", DocumentSource.path(file("aes-256-r6")).withCredential(owner)).primarySource("source").build(), session -> {
                session.execute(EmbedFile.version1(EmbeddedFile.version1("proof.txt", replacement)));
                session.execute(AddBlankPage.INSTANCE);
                assertArrayEquals(replacement, session.query(ReadEmbeddedFile.version1("proof.txt", 4096)).get().getContent());
                return null;
            });
            assertArrayEquals(replacement, read(product, user));
            assertEquals(Integer.valueOf(2), new DocumentWorkflow().execute(open(product, null).build(), session -> session.query(PageCount.INSTANCE)).getResult());
            assertQueryFailure(product, null, DocumentFailureCode.CREDENTIAL_REQUIRED);
        }
    }

    @Test
    public void publicationFailureRetainsOrderedReceiptsAndCallerStreamOwnership() throws Exception {
        Path committed = temporary.newFile().toPath(), untouched = temporary.newFile().toPath();
        byte[] marker = {20, 21}; Files.write(untouched, marker);
        final AtomicBoolean closed = new AtomicBoolean();
        java.io.OutputStream broken = new java.io.OutputStream() {
            @Override public void write(int value) throws java.io.IOException { throw new java.io.IOException("private-T80-output-path"); }
            @Override public void close() { closed.set(true); }
        };
        try (PasswordCredential user = credential("baseline-user"); PasswordCredential owner = credential("baseline-owner")) {
            try {
                new DocumentWorkflow().execute(open(file("aes-256-r6"), owner)
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
                assertNull(failure.getCause()); assertFalse(failure.getDiagnostic().contains("private-T80"));
            }
            assertEquals(PasswordEncryptionScope.EMBEDDED_FILES_ONLY, security(committed, user).getEncryptionScope());
            assertArrayEquals(marker, Files.readAllBytes(untouched)); assertFalse(closed.get());
            assertFalse(user.isDestroyed()); assertFalse(owner.isDestroyed());
        }
    }

    @Test
    public void ownerAuthorityStillIntersectsWithExistingSignatureRestrictions() throws Exception {
        try (PasswordCredential owner = credential("baseline-owner"); PasswordCredential user = credential("baseline-user")) {
            Path signed = temporary.newFile().toPath();
            new DocumentWorkflow().execute(open(file("aes-256-r6-assembly"), owner).saveMode(SaveMode.INCREMENTAL)
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
                    DocumentFailureCode.SIGNED_REWRITE_REJECTED);
        }
    }

    private void assertPreflightFailure(WorkflowRequest.Builder request, DocumentFailureCode code) throws Exception {
        assertPreflightFailure(new DocumentWorkflow(), request, code);
    }
    private void assertPreflightFailure(DocumentWorkflow workflow, WorkflowRequest.Builder request, DocumentFailureCode code) throws Exception {
        byte[] marker = "unchanged T80 target".getBytes(StandardCharsets.US_ASCII);
        Path first = temporary.newFile().toPath(), second = temporary.newFile().toPath();
        Files.write(first, marker); Files.write(second, marker);
        AtomicBoolean work = new AtomicBoolean();
        try {
            workflow.execute(request.target("first", PublicationTarget.path(first)).target("second", PublicationTarget.path(second)).build(), s -> { work.set(true); return null; });
            fail("Expected " + code);
        } catch (DocumentFailure failure) {
            assertEquals(code, failure.getCode()); assertNull(failure.getCause());
            assertEquals(2, failure.getPublicationReceipts().size());
            for (int i = 0; i < 2; i++) { assertEquals(i == 0 ? "first" : "second", failure.getPublicationReceipts().get(i).getTargetName()); assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(i).getStatus()); }
            assertFalse(failure.getDiagnostic().contains("baseline-")); assertFalse(failure.getDiagnostic().contains(first.toString()));
        }
        assertFalse(work.get()); assertArrayEquals(marker, Files.readAllBytes(first)); assertArrayEquals(marker, Files.readAllBytes(second));
    }
    private static void assertQueryFailure(Path source, PasswordCredential credential, DocumentFailureCode expected) throws Exception {
        try { read(source, credential); fail("Attachment extraction requires authority"); }
        catch (DocumentFailure failure) { assertEquals(expected, failure.getCode()); assertNull(failure.getCause()); }
    }
    private static byte[] read(Path source, PasswordCredential password) throws Exception {
        return new DocumentWorkflow().execute(open(source, password).build(), s -> s.query(ReadEmbeddedFile.version1("proof.txt", 4096)).get().getContent()).getResult();
    }
    private static PasswordSecurityInfo security(Path source, PasswordCredential password) throws Exception {
        return new DocumentWorkflow().execute(open(source, password).build(), s -> s.query(DocumentSecurity.INSTANCE)).getResult();
    }
    private static WorkflowRequest.Builder output(Path target, PasswordCredential owner, PasswordCredential user, PasswordEncryptionAlgorithm algorithm, PdfVersion version) {
        WorkflowRequest.Builder request = builder().target("target", PublicationTarget.path(target))
                .outputPolicy(PdfOutputPolicy.version(version).withPasswordSecurity(policy(owner, user, algorithm, version)));
        if (algorithm != PasswordEncryptionAlgorithm.AES_256) { request.legacySecurityMode(LegacySecurityMode.ALLOW_OBSOLETE_PASSWORD_ENCRYPTION); }
        return request;
    }
    private static PasswordSecurityPolicy policy(PasswordCredential owner, PasswordCredential user, PasswordEncryptionAlgorithm algorithm, PdfVersion version) {
        return PasswordSecurityPolicy.builder(owner, user).algorithm(algorithm).encryptionScope(PasswordEncryptionScope.EMBEDDED_FILES_ONLY)
                .permissions(DocumentPermissions.builder().allowContentExtraction(true).allowAccessibilityExtraction(version == PdfVersion.PDF_2_0).build()).build();
    }
    private static WorkflowExecutionProfile execution() { return WorkflowExecutionProfile.valueOf(System.getProperty("folio.t80.executionProfile", "IN_PROCESS")); }
    private static WorkflowResourcePolicy limits(long input, long decoded) {
        WorkflowResourcePolicy base = WorkflowResourcePolicy.safeDefaults();
        return WorkflowResourcePolicy.builder().maximumInputBytes(input).maximumDecompressedBytes(decoded)
                .maximumPages(base.getMaximumPages()).maximumObjects(base.getMaximumObjects()).maximumNestingDepth(base.getMaximumNestingDepth())
                .maximumDecodedPixels(base.getMaximumDecodedPixels()).maximumOwnedMemoryBytes(base.getMaximumOwnedMemoryBytes())
                .maximumTemporaryStorageBytes(base.getMaximumTemporaryStorageBytes()).maximumElapsedTime(base.getMaximumElapsedTime())
                .maximumConcurrentWorkflows(base.getMaximumConcurrentWorkflows()).build();
    }
    private static WorkflowResourcePolicy resourceLimits(long memory, long storage, java.time.Duration elapsed) {
        WorkflowResourcePolicy base = WorkflowResourcePolicy.safeDefaults();
        return WorkflowResourcePolicy.builder().maximumInputBytes(base.getMaximumInputBytes()).maximumDecompressedBytes(base.getMaximumDecompressedBytes())
                .maximumPages(base.getMaximumPages()).maximumObjects(base.getMaximumObjects()).maximumNestingDepth(base.getMaximumNestingDepth())
                .maximumDecodedPixels(base.getMaximumDecodedPixels()).maximumOwnedMemoryBytes(memory).maximumTemporaryStorageBytes(storage)
                .maximumElapsedTime(elapsed).maximumConcurrentWorkflows(base.getMaximumConcurrentWorkflows()).build();
    }
    private static WorkflowRequest.Builder builder() { return WorkflowRequest.builder().executionProfile(execution()).saveMode(SaveMode.REWRITE); }
    private static WorkflowRequest.Builder open(Path source, PasswordCredential password) { return builder().source("source", password == null ? DocumentSource.path(source) : DocumentSource.path(source).withCredential(password)).primarySource("source"); }
    private static PasswordCredential credential(String value) { return PasswordCredential.of(value.toCharArray()); }
    private static Path file(String name) { return CORPUS.resolve(name + ".pdf"); }
    private static byte[] payload() throws Exception { return Files.readAllBytes(CORPUS.resolve("attachment.txt")); }
    private static Properties cases() throws Exception { Properties result = new Properties(); try (InputStream in = Files.newInputStream(CORPUS.resolve("cases.properties"))) { result.load(in); } return result; }
    private static String user(String kind) {
        if ("empty".equals(kind)) { return ""; }
        if ("equal".equals(kind)) { return "equal-baseline"; }
        if ("unicode".equals(kind)) { return "I\u00adX\u00a0\u2168"; }
        char[] value;
        if ("legacy-32".equals(kind)) { value = new char[33]; Arrays.fill(value, '\u00e9'); return new String(value); }
        if ("unicode-split".equals(kind)) { value = new char[126]; Arrays.fill(value, 'a'); return new String(value) + "\u00e9"; }
        if ("long".equals(kind)) { value = new char[128]; Arrays.fill(value, 'a'); return new String(value); }
        return "baseline-user";
    }
    private static final class BorrowedInput extends ByteArrayInputStream {
        boolean closed;
        BorrowedInput(byte[] data) { super(data); }
        @Override public void close() { closed = true; }
    }
}
