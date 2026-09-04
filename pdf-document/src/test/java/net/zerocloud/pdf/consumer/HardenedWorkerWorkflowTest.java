package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.zerocloud.pdf.CredentialAuthority;
import net.zerocloud.pdf.DocumentPermissions;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentQuery;
import net.zerocloud.pdf.DocumentResourceInventory;
import net.zerocloud.pdf.ExtractionLimits;
import net.zerocloud.pdf.EmbeddedFile;
import net.zerocloud.pdf.EmbeddedFileData;
import net.zerocloud.pdf.EmbeddedFileSummary;
import net.zerocloud.pdf.HardenedWorkerSettings;
import net.zerocloud.pdf.ImageByteAccess;
import net.zerocloud.pdf.LegacySecurityMode;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PasswordCredential;
import net.zerocloud.pdf.PasswordEncryptionAlgorithm;
import net.zerocloud.pdf.PasswordEncryptionScope;
import net.zerocloud.pdf.PasswordSecurityInfo;
import net.zerocloud.pdf.PasswordSecurityPolicy;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfOutputPolicy;
import net.zerocloud.pdf.PdfString;
import net.zerocloud.pdf.PdfVersion;
import net.zerocloud.pdf.ResourceExtractionLimits;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.TextStructureExtraction;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.WorkflowResourcePolicy;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.command.EmbedFile;
import net.zerocloud.pdf.command.UpdateDocumentInfo;
import net.zerocloud.pdf.composition.CanvasImage;
import net.zerocloud.pdf.composition.CanvasImageCapabilities;
import net.zerocloud.pdf.composition.query.InspectCanvasImageCapabilities;
import net.zerocloud.pdf.query.DocumentInfo;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.DocumentSecurity;
import net.zerocloud.pdf.query.DocumentVersion;
import net.zerocloud.pdf.query.EmbeddedFiles;
import net.zerocloud.pdf.query.ExtractImagesAndResources;
import net.zerocloud.pdf.query.ExtractTextAndStructure;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.ReadEmbeddedFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Public-seam contract checks for the opt-in process Worker profile. */
public final class HardenedWorkerWorkflowTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void createsQueriesAndPublishesThroughWorker() throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve("worker.pdf");
        WorkflowRequest request = WorkflowRequest.builder()
                .target("result", net.zerocloud.pdf.PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER)
                .build();

        WorkflowOutcome<Integer> outcome = new DocumentWorkflow().execute(
                request,
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return session.query(PageCount.INSTANCE);
                });

        assertEquals(Integer.valueOf(1), outcome.getResult());
        assertEquals(
                WorkflowExecutionProfile.HARDENED_WORKER,
                outcome.getExecutionProfile());
        assertTrue(Files.size(target) > 0L);
    }

    @Test
    public void workerClasspathRetainsTheOptionalTiffProviderGraph()
            throws Exception {
        Path target = temporaryFolder.getRoot().toPath()
                .resolve("worker-image-capabilities.pdf");

        CanvasImageCapabilities capabilities = new DocumentWorkflow().execute(
                request(target),
                session -> {
                    CanvasImageCapabilities result = session.query(
                            InspectCanvasImageCapabilities.version1());
                    session.execute(AddBlankPage.INSTANCE);
                    return result;
                }).getResult();

        assertEquals(
                CanvasImageCapabilities.Availability.AVAILABLE,
                capabilities.getSupport(CanvasImage.SourceKind.TIFF)
                        .getAvailability());
    }

    @Test
    public void transportsOrderedCommandBatchAndOptionalEmbeddedValues()
            throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve("batch.pdf");
        byte[] minimal = "minimal".getBytes(StandardCharsets.UTF_8);
        byte[] typed = "typed".getBytes(StandardCharsets.UTF_8);
        byte[] described = "described".getBytes(StandardCharsets.UTF_8);

        Integer pageCount = new DocumentWorkflow().execute(
                request(target),
                session -> {
                    session.executeBatch(Arrays.asList(
                            AddBlankPage.INSTANCE,
                            AddBlankPage.INSTANCE,
                            EmbedFile.version1(EmbeddedFile.version1(
                                    "minimal.bin",
                                    minimal)),
                            EmbedFile.version1(EmbeddedFile.version1(
                                    "typed.txt",
                                    typed,
                                    "text/plain")),
                            EmbedFile.version1(EmbeddedFile.version1(
                                    "described.bin",
                                    described,
                                    "application/octet-stream",
                                    "Explicit unspecified relationship",
                                    EmbeddedFile.Relationship.UNSPECIFIED))));
                    List<EmbeddedFileSummary> files = session.query(
                            EmbeddedFiles.version1(4));
                    assertEquals(3, files.size());
                    assertEquals("described.bin", files.get(0).getName());
                    assertEquals(Optional.of(
                            "Explicit unspecified relationship"),
                            files.get(0).getDescription());
                    assertEquals(EmbeddedFile.Relationship.UNSPECIFIED,
                            files.get(0).getRelationship());
                    assertEquals("minimal.bin", files.get(1).getName());
                    assertEquals(EmbeddedFile.Relationship.UNSPECIFIED,
                            files.get(1).getRelationship());
                    assertEquals("typed.txt", files.get(2).getName());
                    assertEquals(Optional.of("text/plain"),
                            files.get(2).getMimeSubtype());
                    Optional<EmbeddedFileData> content = session.query(
                            ReadEmbeddedFile.version1(
                                    "minimal.bin",
                                    128L));
                    assertTrue(content.isPresent());
                    assertArrayEquals(minimal, content.get().getContent());
                    return session.query(PageCount.INSTANCE);
                }).getResult();

        assertEquals(Integer.valueOf(2), pageCount);
    }

    @Test
    public void embeddedFileResultsPreserveAbsentRelationshipsAcrossProfiles()
            throws Exception {
        List<Object> inProcess = embeddedFileResults(
                temporaryFolder.getRoot().toPath().resolve(
                        "embedded-in-process.pdf"),
                WorkflowExecutionProfile.IN_PROCESS);
        List<Object> hardened = embeddedFileResults(
                temporaryFolder.getRoot().toPath().resolve(
                        "embedded-hardened.pdf"),
                WorkflowExecutionProfile.HARDENED_WORKER);

        assertEquals(inProcess, hardened);
        @SuppressWarnings("unchecked")
        List<EmbeddedFileSummary> summaries =
                (List<EmbeddedFileSummary>) inProcess.get(0);
        EmbeddedFileSummary absent = summaries.get(0);
        assertEquals(EmbeddedFile.Relationship.UNSPECIFIED,
                absent.getRelationship());
        assertTrue(absent.toString().contains("relationship=null"));
    }

    @Test
    public void transportsExplicitOutputVersionPolicy() throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve("pdf-2.pdf");
        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .target("result",
                                net.zerocloud.pdf.PublicationTarget.path(target))
                        .saveMode(SaveMode.REWRITE)
                        .outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_2_0))
                        .executionProfile(
                                WorkflowExecutionProfile.HARDENED_WORKER)
                        .build(),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });

        assertEquals(PdfVersion.PDF_2_0,
                new DocumentWorkflow().execute(
                        WorkflowRequest.open(target, SaveMode.REWRITE),
                        session -> session.query(DocumentVersion.INSTANCE))
                        .getResult().getEffectiveVersion());
    }

    @Test
    public void transportsPasswordOutputPolicyAndSourceCredential()
            throws Exception {
        PasswordCredential owner = PasswordCredential.of(
                "worker-owner".toCharArray());
        PasswordCredential user = PasswordCredential.of(
                "worker-user".toCharArray());
        try {
            DocumentPermissions permissions = DocumentPermissions.builder()
                    .allowPrinting(true)
                    .allowAccessibilityExtraction(true)
                    .build();
            PasswordSecurityPolicy security = PasswordSecurityPolicy.builder(
                            owner,
                            user)
                    .algorithm(PasswordEncryptionAlgorithm.AES_128)
                    .encryptionScope(
                            PasswordEncryptionScope.ALL_CONTENT)
                    .permissions(permissions)
                    .build();
            Path target = temporaryFolder.getRoot().toPath()
                    .resolve("worker-protected.pdf");

            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .target("result",
                                    net.zerocloud.pdf.PublicationTarget.path(
                                            target))
                            .saveMode(SaveMode.REWRITE)
                            .outputPolicy(PdfOutputPolicy
                                    .version(PdfVersion.PDF_1_7)
                                    .withPasswordSecurity(security))
                            .legacySecurityMode(LegacySecurityMode
                                    .ALLOW_OBSOLETE_PASSWORD_ENCRYPTION)
                            .executionProfile(
                                    WorkflowExecutionProfile.HARDENED_WORKER)
                            .build(),
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        return null;
                    });

            PasswordSecurityInfo reopened = new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source("source",
                                    net.zerocloud.pdf.DocumentSource.path(target)
                                            .withCredential(user))
                            .primarySource("source")
                            .saveMode(SaveMode.REWRITE)
                            .executionProfile(
                                    WorkflowExecutionProfile.HARDENED_WORKER)
                            .build(),
                    session -> session.query(DocumentSecurity.INSTANCE))
                    .getResult();

            assertEquals(
                    PasswordEncryptionAlgorithm.AES_128,
                    reopened.getAlgorithm().get());
            assertEquals(
                    PasswordEncryptionScope.ALL_CONTENT,
                    reopened.getEncryptionScope());
            assertEquals(permissions, reopened.getDeclaredUserPermissions());
            assertEquals(permissions, reopened.getEffectivePermissions());
            assertEquals(
                    CredentialAuthority.USER,
                    reopened.getCredentialAuthority());
            assertFalse(owner.isDestroyed());
            assertFalse(user.isDestroyed());
        } finally {
            owner.close();
            user.close();
        }
    }

    @Test
    public void transportsDetachedAndLazyQueryContracts() throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve("values.pdf");
        final PdfDictionary[] retained = new PdfDictionary[1];
        WorkflowRequest request = WorkflowRequest.builder()
                .target("result", net.zerocloud.pdf.PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER)
                .build();

        new DocumentWorkflow().execute(request, session -> {
            session.execute(AddBlankPage.INSTANCE);
            session.execute(UpdateDocumentInfo.version1()
                    .set("WorkerMarker", PdfString.of(new byte[] {65}))
                    .build());
            PdfDictionary information = session.query(DocumentInfo.INSTANCE);
            assertEquals(PdfString.of(new byte[] {65}),
                    information.get(PdfName.of("WorkerMarker")));

            ObjectReference root = session.query(DocumentRootReference.INSTANCE);
            PdfDictionary rootValue = (PdfDictionary) session.query(
                    InspectObject.version1(
                            root,
                            PdfInspectionLimits.of(2L, 0L)));
            assertTrue(rootValue.size() > 0);
            assertTrue(rootValue.get(PdfName.of("Pages"))
                    instanceof PdfIndirectReference);
            retained[0] = rootValue;

            TextStructureExtraction text = session.query(
                    ExtractTextAndStructure.version1(extractionLimits()));
            assertEquals(1, text.getPages().size());
            DocumentResourceInventory resources = session.query(
                    ExtractImagesAndResources.version1(
                            resourceLimits(),
                            ImageByteAccess.NONE));
            assertNotNull(resources.getResources());
            return null;
        });

        try {
            retained[0].size();
            fail("Expected the lazy Worker view to expire");
        } catch (net.zerocloud.pdf.DocumentFailure failure) {
            assertEquals(
                    net.zerocloud.pdf.DocumentFailureCode.PDF_VALUE_VIEW_EXPIRED,
                    failure.getCode());
        }
    }

    @Test
    public void incrementalPublicationAndClosedExtensionSurfaceArePreserved()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("source.pdf");
        Path incremental = temporaryFolder.getRoot().toPath()
                .resolve("incremental.pdf");
        new DocumentWorkflow().execute(
                WorkflowRequest.create(source, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });
        WorkflowOutcome<Integer> outcome = new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("source", net.zerocloud.pdf.DocumentSource.path(source))
                        .primarySource("source")
                        .target("result",
                                net.zerocloud.pdf.PublicationTarget.path(incremental))
                        .saveMode(SaveMode.INCREMENTAL)
                        .executionProfile(
                                WorkflowExecutionProfile.HARDENED_WORKER)
                        .build(),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return session.query(PageCount.INSTANCE);
                });
        assertEquals(Integer.valueOf(2), outcome.getResult());
        assertEquals(Integer.valueOf(2), new DocumentWorkflow().execute(
                WorkflowRequest.open(incremental, SaveMode.REWRITE),
                session -> session.query(PageCount.INSTANCE)).getResult());

        Path rejected = temporaryFolder.getRoot().toPath().resolve("rejected.pdf");
        DocumentCommand arbitraryCommand = new DocumentCommand() { };
        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .target("result",
                                    net.zerocloud.pdf.PublicationTarget.path(rejected))
                            .saveMode(SaveMode.REWRITE)
                            .executionProfile(
                                    WorkflowExecutionProfile.HARDENED_WORKER)
                            .build(),
                    session -> {
                        session.execute(arbitraryCommand);
                        return null;
                    });
            fail("Expected arbitrary Command rejection");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.COMMAND_REJECTED,
                    failure.getCode());
        }
        assertTrue(!Files.exists(rejected));

        DocumentQuery<Object> arbitraryQuery = new DocumentQuery<Object>() { };
        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .target("result",
                                    net.zerocloud.pdf.PublicationTarget.path(rejected))
                            .saveMode(SaveMode.REWRITE)
                            .executionProfile(
                                    WorkflowExecutionProfile.HARDENED_WORKER)
                            .build(),
                    session -> session.query(arbitraryQuery));
            fail("Expected arbitrary Query rejection");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.QUERY_REJECTED,
                    failure.getCode());
        }
        assertTrue(!Files.exists(rejected));
    }

    @Test
    public void caughtMessageLimitRemainsTerminalAndCannotPublish()
            throws Exception {
        Path target = temporaryFolder.getRoot().toPath()
                .resolve("message-limit.pdf");
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .hardenedWorkerSettings(HardenedWorkerSettings.builder()
                        .maximumMessageBytes(2_048)
                        .maximumHeapBytes(
                                HardenedWorkerSettings
                                        .DEFAULT_MAXIMUM_HEAP_BYTES)
                        .build())
                .build();
        final DocumentFailure[] observed = new DocumentFailure[1];
        try {
            new DocumentWorkflow(environment).execute(
                    WorkflowRequest.builder()
                            .target("result",
                                    net.zerocloud.pdf.PublicationTarget.path(target))
                            .saveMode(SaveMode.REWRITE)
                            .executionProfile(
                                    WorkflowExecutionProfile.HARDENED_WORKER)
                            .build(),
                    session -> {
                        try {
                            session.execute(UpdateDocumentInfo.version1()
                                    .set("large", PdfString.of(
                                            new byte[4_096]))
                                    .build());
                        } catch (DocumentFailure failure) {
                            observed[0] = failure;
                        }
                        return null;
                    });
            fail("Expected the terminal message-size failure");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.WORKER_MESSAGE_LIMIT_EXCEEDED,
                    failure.getCode());
        }
        assertNotNull(observed[0]);
        assertEquals(DocumentFailureCode.WORKER_MESSAGE_LIMIT_EXCEEDED,
                observed[0].getCode());
        assertTrue(!Files.exists(target));
    }

    @Test
    public void encodedPayloadsHonorOwnedMemoryBeforeMessageLimit()
            throws Exception {
        Path target = temporaryFolder.getRoot().toPath()
                .resolve("memory-limit.pdf");
        WorkflowResourcePolicy defaults = WorkflowResourcePolicy.safeDefaults();
        WorkflowResourcePolicy limited = WorkflowResourcePolicy.builder()
                .maximumInputBytes(defaults.getMaximumInputBytes())
                .maximumPages(defaults.getMaximumPages())
                .maximumObjects(defaults.getMaximumObjects())
                .maximumNestingDepth(defaults.getMaximumNestingDepth())
                .maximumDecompressedBytes(defaults.getMaximumDecompressedBytes())
                .maximumDecodedPixels(defaults.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(8_192L)
                .maximumTemporaryStorageBytes(
                        defaults.getMaximumTemporaryStorageBytes())
                .maximumElapsedTime(defaults.getMaximumElapsedTime())
                .maximumConcurrentWorkflows(
                        defaults.getMaximumConcurrentWorkflows())
                .build();
        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .target("result",
                                    net.zerocloud.pdf.PublicationTarget.path(target))
                            .saveMode(SaveMode.REWRITE)
                            .resourcePolicy(limited)
                            .executionProfile(
                                    WorkflowExecutionProfile.HARDENED_WORKER)
                            .build(),
                    session -> {
                        session.execute(UpdateDocumentInfo.version1()
                                .set("large", PdfString.of(new byte[16_384]))
                                .build());
                        return null;
                    });
            fail("Expected the owned-memory limit");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                    failure.getCode());
        }
        assertTrue(!Files.exists(target));
    }

    @Test
    public void multipleWorkerProductsShareOneTemporaryStorageCeiling()
            throws Exception {
        Path baseline = temporaryFolder.getRoot().toPath()
                .resolve("temporary-baseline.pdf");
        new DocumentWorkflow().execute(request(baseline), session -> {
            session.execute(AddBlankPage.INSTANCE);
            return null;
        });
        long productBytes = Files.size(baseline);
        long maximumTemporaryBytes = 4096L + 4L * productBytes - 1L;
        WorkflowResourcePolicy defaults = WorkflowResourcePolicy.safeDefaults();
        WorkflowResourcePolicy limited = WorkflowResourcePolicy.builder()
                .maximumInputBytes(defaults.getMaximumInputBytes())
                .maximumPages(defaults.getMaximumPages())
                .maximumObjects(defaults.getMaximumObjects())
                .maximumNestingDepth(defaults.getMaximumNestingDepth())
                .maximumDecompressedBytes(defaults.getMaximumDecompressedBytes())
                .maximumDecodedPixels(defaults.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(defaults.getMaximumOwnedMemoryBytes())
                .maximumTemporaryStorageBytes(maximumTemporaryBytes)
                .maximumElapsedTime(defaults.getMaximumElapsedTime())
                .maximumConcurrentWorkflows(
                        defaults.getMaximumConcurrentWorkflows())
                .build();
        byte[] original = new byte[] {17, 18, 19};
        Path first = temporaryFolder.getRoot().toPath().resolve("first.pdf");
        Path second = temporaryFolder.getRoot().toPath().resolve("second.pdf");
        Path third = temporaryFolder.getRoot().toPath().resolve("third.pdf");
        Files.write(first, original);
        Files.write(second, original);
        Files.write(third, original);

        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .target("first",
                                    net.zerocloud.pdf.PublicationTarget.path(first))
                            .target("second",
                                    net.zerocloud.pdf.PublicationTarget.path(second))
                            .target("third",
                                    net.zerocloud.pdf.PublicationTarget.path(third))
                            .saveMode(SaveMode.REWRITE)
                            .resourcePolicy(limited)
                            .executionProfile(
                                    WorkflowExecutionProfile.HARDENED_WORKER)
                            .build(),
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        return null;
                    });
            fail("Expected aggregate Worker temporary-storage exhaustion");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.TEMPORARY_STORAGE_LIMIT_EXCEEDED,
                    failure.getCode());
        }
        assertArrayEquals(original, Files.readAllBytes(first));
        assertArrayEquals(original, Files.readAllBytes(second));
        assertArrayEquals(original, Files.readAllBytes(third));
        assertFalse(Files.notExists(baseline));
    }

    private static WorkflowRequest request(Path target) {
        return WorkflowRequest.builder()
                .target("result",
                        net.zerocloud.pdf.PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER)
                .build();
    }

    private static List<Object> embeddedFileResults(
            Path target,
            WorkflowExecutionProfile profile) throws Exception {
        return new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .target("result",
                                net.zerocloud.pdf.PublicationTarget.path(target))
                        .saveMode(SaveMode.REWRITE)
                        .executionProfile(profile)
                        .build(),
                session -> {
                    session.executeBatch(Arrays.asList(
                            AddBlankPage.INSTANCE,
                            EmbedFile.version1(EmbeddedFile.version1(
                                    "absent.bin",
                                    new byte[] {1}))));
                    return Arrays.<Object>asList(
                            session.query(EmbeddedFiles.version1(4)),
                            session.query(ReadEmbeddedFile.version1(
                                    "absent.bin", 4L)));
                }).getResult();
    }

    private static ExtractionLimits extractionLimits() {
        return ExtractionLimits.builder()
                .maximumPages(10)
                .maximumPageTreeNodes(20)
                .maximumContentStreams(20)
                .maximumContentStreamDepth(10)
                .maximumDecodedBytes(1_000_000L)
                .maximumTextItems(1000)
                .maximumUnicodeCodePoints(1000)
                .maximumToUnicodeMappings(1000)
                .maximumFontDataEntries(1000)
                .maximumMarkedContentSequences(1000)
                .maximumMarkedContentDepth(20)
                .maximumStructureElements(1000)
                .maximumStructureItems(1000)
                .maximumStructureDepth(20)
                .maximumRoleMappings(1000)
                .build();
    }

    private static ResourceExtractionLimits resourceLimits() {
        return ResourceExtractionLimits.builder()
                .maximumPages(10)
                .maximumPageTreeNodes(20)
                .maximumTraversedResourceValues(1000L)
                .maximumResourceTraversalDepth(20)
                .maximumDecodedPixels(1_000_000L)
                .maximumDecompressedBytes(1_000_000L)
                .maximumReturnedBytes(1_000_000L)
                .build();
    }
}
