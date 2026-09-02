package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import net.zerocloud.pdf.Annotation;
import net.zerocloud.pdf.AnnotationProperties;
import net.zerocloud.pdf.AnnotationRectangle;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PageRange;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.command.SplitDocument;
import net.zerocloud.pdf.command.UpdateAnnotations;
import net.zerocloud.pdf.query.Annotations;
import net.zerocloud.pdf.query.PageCount;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class IncrementalSignatureWorkflowTest {

    private static final String CAPABILITY =
            "document.incremental-signature.protect";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void unsignedMutationPublishesAnAppendedRevisionToOrderedTargets()
            throws Exception {
        Path source = path("unsigned-source.pdf");
        Path pathTarget = path("unsigned-incremental.pdf");
        ByteArrayOutputStream streamTarget = new ByteArrayOutputStream();
        createDocument(source, 1);
        byte[] originalRevision = Files.readAllBytes(source);

        WorkflowOutcome<Integer> outcome = new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("primary", DocumentSource.path(source))
                        .primarySource("primary")
                        .target("path", PublicationTarget.path(pathTarget))
                        .target("stream", PublicationTarget.stream(streamTarget))
                        .saveMode(SaveMode.INCREMENTAL)
                        .build(),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return session.query(PageCount.INSTANCE);
                });

        assertEquals(Integer.valueOf(2), outcome.getResult());
        assertEquals(CAPABILITY, outcome.getCapabilityId());
        assertEquals(SaveMode.INCREMENTAL, outcome.getSaveMode());
        assertEquals(2, outcome.getPublicationReceipts().size());
        assertEquals("path", outcome.getPublicationReceipts().get(0).getTargetName());
        assertEquals(
                PublicationStatus.COMMITTED,
                outcome.getPublicationReceipts().get(0).getStatus());
        assertEquals("stream", outcome.getPublicationReceipts().get(1).getTargetName());
        assertEquals(
                PublicationStatus.COMMITTED,
                outcome.getPublicationReceipts().get(1).getStatus());

        byte[] incremental = Files.readAllBytes(pathTarget);
        assertTrue(incremental.length > originalRevision.length);
        assertArrayEquals(
                originalRevision,
                Arrays.copyOf(incremental, originalRevision.length));
        assertArrayEquals(incremental, streamTarget.toByteArray());

        WorkflowOutcome<Integer> reopened = new DocumentWorkflow().execute(
                WorkflowRequest.open(pathTarget, SaveMode.REWRITE),
                session -> session.query(PageCount.INSTANCE));
        assertEquals(Integer.valueOf(2), reopened.getResult());
    }

    @Test
    public void splitIsRejectedBeforeIncrementalPublication() throws Exception {
        Path source = path("split-source.pdf");
        Path front = path("split-front.pdf");
        Path back = path("split-back.pdf");
        byte[] frontBytes = new byte[] {11, 12, 13};
        byte[] backBytes = new byte[] {21, 22, 23};
        createDocument(source, 2);
        Files.write(front, frontBytes);
        Files.write(back, backBytes);

        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source("primary", DocumentSource.path(source))
                            .primarySource("primary")
                            .target("front", PublicationTarget.path(front))
                            .target("back", PublicationTarget.path(back))
                            .saveMode(SaveMode.INCREMENTAL)
                            .build(),
                    session -> {
                        session.execute(SplitDocument.version1()
                                .target("front", PageRange.of(1, 1))
                                .target("back", PageRange.of(2, 2))
                                .build());
                        return null;
                    });
            fail("Expected SplitDocument to be rejected for INCREMENTAL");
        } catch (DocumentFailure failure) {
            assertEquals("INCREMENTAL_COMMAND_REJECTED", failure.getCode().name());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(
                    "The command is not supported for INCREMENTAL publication.",
                    failure.getDiagnostic());
            assertEquals(2, failure.getPublicationReceipts().size());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(1).getStatus());
        }

        assertArrayEquals(frontBytes, Files.readAllBytes(front));
        assertArrayEquals(backBytes, Files.readAllBytes(back));
    }

    @Test
    public void ordinarySignedSourceIsQueryableButCannotBeRewritten()
            throws Exception {
        Path source = path("ordinary-signature.pdf");
        Path target = path("ordinary-rewrite.pdf");
        byte[] existingTarget = new byte[] {31, 32, 33};
        Files.write(source,
                ProjectOwnedSignatureFixtures.ordinaryApprovalSignature());
        Files.write(target, existingTarget);

        WorkflowOutcome<Integer> query = new DocumentWorkflow().execute(
                WorkflowRequest.open(source, SaveMode.REWRITE),
                session -> session.query(PageCount.INSTANCE));
        assertEquals(Integer.valueOf(1), query.getResult());

        AtomicBoolean callerWorkRan = new AtomicBoolean();
        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source("signed", DocumentSource.path(source))
                            .primarySource("signed")
                            .target("preserved", PublicationTarget.path(target))
                            .saveMode(SaveMode.REWRITE)
                            .build(),
                    session -> {
                        callerWorkRan.set(true);
                        return null;
                    });
            fail("Expected signed REWRITE publication to be rejected");
        } catch (DocumentFailure failure) {
            assertEquals("SIGNED_REWRITE_REJECTED", failure.getCode().name());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(
                    "A Source with an Existing Signature cannot be published with REWRITE.",
                    failure.getDiagnostic());
            assertEquals(1, failure.getPublicationReceipts().size());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
        }

        assertTrue(!callerWorkRan.get());
        assertArrayEquals(existingTarget, Files.readAllBytes(target));
    }

    @Test
    public void signedTargetFreeRewriteRejectsCommandsAtTheBoundary()
            throws Exception {
        Path ordinary = path("ordinary-target-free-rewrite.pdf");
        byte[] ordinaryBytes =
                ProjectOwnedSignatureFixtures.ordinaryApprovalSignature();
        Files.write(ordinary, ordinaryBytes);
        assertTargetFreeRewriteCommandRejected(
                ordinary,
                AddBlankPage.INSTANCE);
        assertArrayEquals(ordinaryBytes, Files.readAllBytes(ordinary));

        Path p3 = path("p3-target-free-rewrite.pdf");
        byte[] p3Bytes = ProjectOwnedSignatureFixtures.docMdpSignature(3);
        Files.write(p3, p3Bytes);
        Annotation note = Annotation.text(
                AnnotationProperties.version1(
                                "target-free-p3-note",
                                1,
                                AnnotationRectangle.of(10L, 20L, 70L, 80L))
                        .build(),
                Annotation.TextIcon.NOTE,
                false);
        assertTargetFreeRewriteCommandRejected(
                p3,
                UpdateAnnotations.version1().put(note).build());
        assertArrayEquals(p3Bytes, Files.readAllBytes(p3));
    }

    @Test
    public void ordinarySignatureRejectsIncrementalMutationBeforePublication()
            throws Exception {
        assertSignedMutationRejected(
                "ordinary-incremental",
                ProjectOwnedSignatureFixtures.ordinaryApprovalSignature(),
                AddBlankPage.INSTANCE);
    }

    @Test
    public void docMdpP1AndP2RejectCurrentNonFormMutations()
            throws Exception {
        assertSignedMutationRejected(
                "docmdp-p1",
                ProjectOwnedSignatureFixtures.docMdpSignature(1),
                AddBlankPage.INSTANCE);
        assertSignedMutationRejected(
                "docmdp-p2",
                ProjectOwnedSignatureFixtures.docMdpSignature(2),
                AddBlankPage.INSTANCE);
        assertSignedMutationRejected(
                "docmdp-default-p2",
                ProjectOwnedSignatureFixtures.docMdpDefaultPermission(),
                AddBlankPage.INSTANCE);
    }

    @Test
    public void docMdpP3PermitsNonWidgetAnnotationInAppendedRevision()
            throws Exception {
        Path source = path("docmdp-p3.pdf");
        Path target = path("docmdp-p3-incremental.pdf");
        byte[] signedRevision = ProjectOwnedSignatureFixtures.docMdpSignature(3);
        Files.write(source, signedRevision);
        Annotation note = Annotation.text(
                AnnotationProperties.version1(
                                "p3-note",
                                1,
                                AnnotationRectangle.of(10L, 20L, 70L, 80L))
                        .contents("P3 project-owned note")
                        .build(),
                Annotation.TextIcon.NOTE,
                false);

        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(
                incrementalRequest(source, target),
                session -> {
                    session.execute(UpdateAnnotations.version1()
                            .put(note)
                            .build());
                    return null;
                });

        assertEquals(CAPABILITY, outcome.getCapabilityId());
        assertEquals(
                PublicationStatus.COMMITTED,
                outcome.getPublicationReceipts().get(0).getStatus());
        byte[] published = Files.readAllBytes(target);
        assertTrue(published.length > signedRevision.length);
        assertArrayEquals(
                signedRevision,
                Arrays.copyOf(published, signedRevision.length));
        assertTrue(
                new DocumentWorkflow().execute(
                        WorkflowRequest.open(target, SaveMode.REWRITE),
                        session -> session.query(Annotations.version1(
                                8,
                                4096L,
                                4096L)))
                        .getResult().contains(note));
    }

    @Test
    public void malformedSignatureFailsBeforeCallerWorkOrPublication()
            throws Exception {
        Path source = path("malformed-signature.pdf");
        Path target = path("malformed-target.pdf");
        ByteArrayOutputStream streamTarget = new ByteArrayOutputStream();
        byte[] sentinel = new byte[] {41, 42, 43};
        Files.write(source, ProjectOwnedSignatureFixtures.outOfBoundsByteRange());
        Files.write(target, sentinel);
        AtomicBoolean callerWorkRan = new AtomicBoolean();

        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source("primary", DocumentSource.path(source))
                            .primarySource("primary")
                            .target("path", PublicationTarget.path(target))
                            .target(
                                    "stream",
                                    PublicationTarget.stream(streamTarget))
                            .saveMode(SaveMode.INCREMENTAL)
                            .build(),
                    session -> {
                        callerWorkRan.set(true);
                        return null;
                    });
            fail("Expected malformed signature structure to fail closed");
        } catch (DocumentFailure failure) {
            assertEquals("SIGNATURE_STRUCTURE_INVALID", failure.getCode().name());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(
                    "The Existing Signature policy could not be determined safely.",
                    failure.getDiagnostic());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
            assertEquals(2, failure.getPublicationReceipts().size());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(1).getStatus());
        }

        assertTrue(!callerWorkRan.get());
        assertArrayEquals(sentinel, Files.readAllBytes(target));
        assertEquals(0, streamTarget.size());
    }

    @Test
    public void cyclicAndContradictorySignatureGraphsFailBeforeCallerWork()
            throws Exception {
        assertSignatureStructureRejected(
                "cyclic-fields",
                ProjectOwnedSignatureFixtures.cyclicSignatureFieldTree());
        assertSignatureStructureRejected(
                "contradictory-docmdp",
                ProjectOwnedSignatureFixtures.contradictoryDocMdpReference());
        assertSignatureStructureRejected(
                "indirect-docmdp-reference",
                ProjectOwnedSignatureFixtures.indirectDocMdpReference());
    }

    @Test
    public void signaturePolicyTraversalLimitsFailBeforeCallerWork()
            throws Exception {
        assertSignatureStructureRejected(
                "excessive-field-roots",
                ProjectOwnedSignatureFixtures.excessiveFieldRoots());
        assertSignatureStructureRejected(
                "excessive-field-children",
                ProjectOwnedSignatureFixtures.excessiveFieldChildren());
        assertSignatureStructureRejected(
                "excessive-field-depth",
                ProjectOwnedSignatureFixtures.excessiveFieldDepth());
        assertSignatureStructureRejected(
                "excessive-byte-range",
                ProjectOwnedSignatureFixtures.excessiveByteRangeEntries());
        assertSignatureStructureRejected(
                "excessive-references",
                ProjectOwnedSignatureFixtures.excessiveSignatureReferences());
        assertSignatureStructureRejected(
                "excessive-signature-dictionary-entries",
                ProjectOwnedSignatureFixtures
                        .excessiveSignatureDictionaryEntries());
        assertSignatureStructureRejected(
                "excessive-permission-entries",
                ProjectOwnedSignatureFixtures.excessivePermissionEntries());
        assertSignatureStructureRejected(
                "nested-indirect-resolution",
                ProjectOwnedSignatureFixtures.excessiveIndirectResolution());
    }

    @Test
    public void signaturePolicyExactTraversalLimitsRemainReadable()
            throws Exception {
        assertSignatureStructureReadable(
                "maximum-field-roots",
                ProjectOwnedSignatureFixtures.maximumFieldRoots());
        assertSignatureStructureReadable(
                "maximum-field-children",
                ProjectOwnedSignatureFixtures.maximumFieldChildren());
        assertSignatureStructureReadable(
                "maximum-field-depth",
                ProjectOwnedSignatureFixtures.maximumFieldDepth());
        assertSignatureStructureReadable(
                "maximum-byte-range",
                ProjectOwnedSignatureFixtures.maximumByteRangeEntries());
        assertSignatureStructureReadable(
                "maximum-references",
                ProjectOwnedSignatureFixtures.maximumSignatureReferences());
        assertSignatureStructureReadable(
                "maximum-signature-dictionary-entries",
                ProjectOwnedSignatureFixtures
                        .maximumSignatureDictionaryEntries());
        assertSignatureStructureReadable(
                "maximum-permission-entries",
                ProjectOwnedSignatureFixtures.maximumPermissionEntries());
        assertSignatureStructureReadable(
                "maximum-indirect-resolution",
                ProjectOwnedSignatureFixtures.maximumIndirectResolution());
    }

    @Test
    public void multipleAndUnsupportedSignatureRestrictionsDenyMutation()
            throws Exception {
        assertSignedMutationRejected(
                "multiple-signatures",
                ProjectOwnedSignatureFixtures.docMdpP3WithOrdinaryApproval(),
                AddBlankPage.INSTANCE);
        assertSignedMutationRejected(
                "unsupported-transform",
                ProjectOwnedSignatureFixtures.unsupportedTransform(),
                AddBlankPage.INSTANCE);
    }

    @Test
    public void docMdpP3RejectsOtherCommandsAndWidgetAnnotations()
            throws Exception {
        byte[] p3 = ProjectOwnedSignatureFixtures.docMdpSignature(3);
        assertSignedMutationRejected("p3-page-change", p3, AddBlankPage.INSTANCE);
        Annotation widget = Annotation.widget(
                AnnotationProperties.version1(
                                "p3-widget",
                                1,
                                AnnotationRectangle.of(10L, 10L, 30L, 30L))
                        .build());
        assertSignedMutationRejected(
                "p3-widget-change",
                p3,
                UpdateAnnotations.version1().put(widget).build());
    }

    @Test
    public void signedIncrementalPublicationRequiresAnAdmittedMutation()
            throws Exception {
        assertSignedNoOpRejected(
                "ordinary-no-op",
                ProjectOwnedSignatureFixtures.ordinaryApprovalSignature());
        assertSignedNoOpRejected(
                "p3-no-op",
                ProjectOwnedSignatureFixtures.docMdpSignature(3));
    }

    private void assertSignedNoOpRejected(String stem, byte[] signed)
            throws Exception {
        Path source = path(stem + ".pdf");
        Path target = path(stem + "-target.pdf");
        byte[] sentinel = new byte[] {61, 62, 63};
        Files.write(source, signed);
        Files.write(target, sentinel);

        try {
            new DocumentWorkflow().execute(
                    incrementalRequest(source, target),
                    session -> session.query(PageCount.INSTANCE));
            fail("Expected signed incremental publication to require a mutation");
        } catch (DocumentFailure failure) {
            assertEquals("SIGNATURE_POLICY_REJECTED", failure.getCode().name());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
        }
        assertArrayEquals(signed, Files.readAllBytes(source));
        assertArrayEquals(sentinel, Files.readAllBytes(target));
    }

    @Test
    public void callerDefinedCommandIsNotInTheIncrementalPolicy()
            throws Exception {
        Path source = path("foreign-command-source.pdf");
        Path target = path("foreign-command-target.pdf");
        createDocument(source, 1);
        DocumentCommand callerDefined = new DocumentCommand() { };

        try {
            new DocumentWorkflow().execute(
                    incrementalRequest(source, target),
                    session -> {
                        session.execute(callerDefined);
                        return null;
                    });
            fail("Expected caller-defined command to be rejected");
        } catch (DocumentFailure failure) {
            assertEquals("INCREMENTAL_COMMAND_REJECTED", failure.getCode().name());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
        }
    }

    @Test
    public void boundedSourceKindsPreservePrefixAndCallerOwnership()
            throws Exception {
        Path fixture = path("bounded-source-fixture.pdf");
        createDocument(fixture, 1);
        byte[] sourceBytes = Files.readAllBytes(fixture);

        assertBoundedIncremental(
                "bytes",
                DocumentSource.bytes(sourceBytes, sourceBytes.length),
                sourceBytes);

        TrackingInputStream stream = new TrackingInputStream(sourceBytes);
        assertBoundedIncremental(
                "stream",
                DocumentSource.stream(stream, sourceBytes.length),
                sourceBytes);
        assertTrue(!stream.closed);

        TrackingChannel channel = new TrackingChannel(sourceBytes);
        assertBoundedIncremental(
                "channel",
                DocumentSource.channel(channel, sourceBytes.length),
                sourceBytes);
        assertTrue(channel.isOpen());
    }

    private void assertBoundedIncremental(
            String stem,
            DocumentSource source,
            byte[] sourceBytes) throws Exception {
        Path target = path(stem + "-incremental.pdf");
        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("primary", source)
                        .primarySource("primary")
                        .target("target", PublicationTarget.path(target))
                        .saveMode(SaveMode.INCREMENTAL)
                        .build(),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });
        byte[] published = Files.readAllBytes(target);
        assertTrue(published.length > sourceBytes.length);
        assertArrayEquals(
                sourceBytes,
                Arrays.copyOf(published, sourceBytes.length));
    }

    private void assertSignatureStructureRejected(String stem, byte[] sourceBytes)
            throws Exception {
        Path source = path(stem + ".pdf");
        Path target = path(stem + "-target.pdf");
        byte[] sentinel = new byte[] {71, 72, 73};
        Files.write(source, sourceBytes);
        Files.write(target, sentinel);
        AtomicBoolean callerWorkRan = new AtomicBoolean();
        try {
            new DocumentWorkflow().execute(
                    incrementalRequest(source, target),
                    session -> {
                        callerWorkRan.set(true);
                        return null;
                    });
            fail("Expected signature structure to fail closed");
        } catch (DocumentFailure failure) {
            assertEquals("SIGNATURE_STRUCTURE_INVALID", failure.getCode().name());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
        }
        assertTrue(!callerWorkRan.get());
        assertArrayEquals(sourceBytes, Files.readAllBytes(source));
        assertArrayEquals(sentinel, Files.readAllBytes(target));
    }

    private void assertSignatureStructureReadable(String stem, byte[] sourceBytes)
            throws Exception {
        Path source = path(stem + ".pdf");
        Files.write(source, sourceBytes);
        assertEquals(
                Integer.valueOf(1),
                new DocumentWorkflow().execute(
                        WorkflowRequest.open(source, SaveMode.REWRITE),
                        session -> session.query(PageCount.INSTANCE))
                        .getResult());
        assertArrayEquals(sourceBytes, Files.readAllBytes(source));
    }

    private void assertSignedMutationRejected(
            String stem,
            byte[] sourceBytes,
            DocumentCommand command) throws Exception {
        Path source = path(stem + ".pdf");
        Path target = path(stem + "-target.pdf");
        byte[] sentinel = new byte[] {51, 52, 53};
        Files.write(source, sourceBytes);
        Files.write(target, sentinel);

        try {
            new DocumentWorkflow().execute(
                    incrementalRequest(source, target),
                    session -> {
                        session.execute(command);
                        return null;
                    });
            fail("Expected signed mutation to be rejected");
        } catch (DocumentFailure failure) {
            assertEquals("SIGNATURE_POLICY_REJECTED", failure.getCode().name());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(
                    "The Existing Signature policy does not permit this workflow.",
                    failure.getDiagnostic());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
        }

        assertArrayEquals(sentinel, Files.readAllBytes(target));
        assertArrayEquals(sourceBytes, Files.readAllBytes(source));
    }

    private static void assertTargetFreeRewriteCommandRejected(
            Path source,
            DocumentCommand command) throws Exception {
        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.open(source, SaveMode.REWRITE),
                    session -> {
                        session.execute(command);
                        return null;
                    });
            fail("Expected target-free signed REWRITE mutation to be rejected");
        } catch (DocumentFailure failure) {
            assertEquals("SIGNATURE_POLICY_REJECTED", failure.getCode().name());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(
                    "The Existing Signature policy does not permit this workflow.",
                    failure.getDiagnostic());
            assertTrue(failure.getPublicationReceipts().isEmpty());
        }
    }

    private static WorkflowRequest incrementalRequest(Path source, Path target) {
        return WorkflowRequest.builder()
                .source("primary", DocumentSource.path(source))
                .primarySource("primary")
                .target("target", PublicationTarget.path(target))
                .saveMode(SaveMode.INCREMENTAL)
                .build();
    }

    private Path path(String name) {
        return temporaryFolder.getRoot().toPath().resolve(name);
    }

    private static void createDocument(Path target, int pageCount)
            throws Exception {
        new DocumentWorkflow().execute(
                WorkflowRequest.create(target, SaveMode.REWRITE),
                session -> {
                    for (int page = 0; page < pageCount; page++) {
                        session.execute(AddBlankPage.INSTANCE);
                    }
                    return null;
                });
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {

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

    private static final class TrackingChannel implements ReadableByteChannel {

        private final ReadableByteChannel delegate;
        private boolean open = true;

        private TrackingChannel(byte[] bytes) {
            delegate = Channels.newChannel(new ByteArrayInputStream(bytes));
        }

        @Override
        public int read(ByteBuffer destination) throws IOException {
            return delegate.read(destination);
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() throws IOException {
            open = false;
            delegate.close();
        }
    }
}
