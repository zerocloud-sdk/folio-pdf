package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfBoolean;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfDictionaryEntry;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNull;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfString;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.PdfValueKind;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.InspectObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class PdfValueWorkflowTest {

    private static final PdfName TEST_VALUE = PdfName.of("T09Value");

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void nullValueRoundTripsThroughRewriteAndReopen() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path rewritten = temporaryFolder.getRoot().toPath().resolve("rewritten.pdf");
        createBlankDocument(input);

        rewriteValue(input, rewritten, PdfNull.INSTANCE);

        PdfValue reopened = readTestValue(rewritten);
        assertEquals(PdfValueKind.NULL, reopened.getKind());
        assertSame(PdfNull.INSTANCE, reopened);
    }

    @Test
    public void booleanValueRoundTripsThroughRewriteAndReopen() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path rewritten = temporaryFolder.getRoot().toPath().resolve("rewritten.pdf");
        createBlankDocument(input);

        rewriteValue(input, rewritten, PdfBoolean.of(true));

        PdfValue reopened = readTestValue(rewritten);
        assertEquals(PdfValueKind.BOOLEAN, reopened.getKind());
        assertEquals(PdfBoolean.of(true), reopened);
    }

    @Test
    public void numberValueRoundTripsThroughRewriteAndReopen() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path rewritten = temporaryFolder.getRoot().toPath().resolve("rewritten.pdf");
        createBlankDocument(input);
        PdfNumber expected = PdfNumber.of(
                new BigDecimal("123456789.123456789"));

        rewriteValue(input, rewritten, expected);

        PdfValue reopened = readTestValue(rewritten);
        assertEquals(PdfValueKind.NUMBER, reopened.getKind());
        assertEquals(expected, reopened);
    }

    @Test
    public void successfulPatchReportsValueCapability() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path rewritten = temporaryFolder.getRoot().toPath().resolve("rewritten.pdf");
        createBlankDocument(input);

        WorkflowOutcome<Void> outcome = rewriteValue(
                input,
                rewritten,
                PdfNull.INSTANCE);

        assertEquals(
                "document.value.inspect-patch",
                outcome.getCapabilityId());
    }

    @Test
    public void stringValueRoundTripsThroughRewriteAndReopen() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path rewritten = temporaryFolder.getRoot().toPath().resolve("rewritten.pdf");
        createBlankDocument(input);
        PdfString expected = PdfString.of(new byte[] {0, 65, (byte) 255});

        rewriteValue(input, rewritten, expected);

        PdfValue reopened = readTestValue(rewritten);
        assertEquals(PdfValueKind.STRING, reopened.getKind());
        assertEquals(expected, reopened);
    }

    @Test
    public void nameValueRoundTripsThroughRewriteAndReopen() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path rewritten = temporaryFolder.getRoot().toPath().resolve("rewritten.pdf");
        createBlankDocument(input);
        PdfName expected = PdfName.of("RoundTripName");

        rewriteValue(input, rewritten, expected);

        PdfValue reopened = readTestValue(rewritten);
        assertEquals(PdfValueKind.NAME, reopened.getKind());
        assertEquals(expected, reopened);
    }

    @Test
    public void arrayValueRoundTripsThroughRewriteAndReopen() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path rewritten = temporaryFolder.getRoot().toPath().resolve("rewritten.pdf");
        createBlankDocument(input);
        PdfArray value = PdfArray.of(
                PdfNull.INSTANCE,
                PdfBoolean.of(false),
                PdfNumber.of(3L));

        rewriteValue(input, rewritten, value);

        WorkflowRequest request = sourceRequest(rewritten);
        new DocumentWorkflow().execute(request, session -> {
            PdfArray reopened = (PdfArray) inspectTestValue(session);
            assertEquals(PdfValueKind.ARRAY, reopened.getKind());
            assertEquals(3, reopened.size());
            assertSame(PdfNull.INSTANCE, reopened.get(0));
            assertEquals(PdfBoolean.of(false), reopened.get(1));
            assertEquals(PdfNumber.of(3L), reopened.get(2));
            return null;
        });
    }

    @Test
    public void dictionaryValueRoundTripsThroughRewriteAndReopen() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path rewritten = temporaryFolder.getRoot().toPath().resolve("rewritten.pdf");
        createBlankDocument(input);
        PdfDictionary value = PdfDictionary.builder()
                .put(PdfName.of("Null"), PdfNull.INSTANCE)
                .put(PdfName.of("Boolean"), PdfBoolean.of(true))
                .build();

        rewriteValue(input, rewritten, value);

        WorkflowRequest request = sourceRequest(rewritten);
        new DocumentWorkflow().execute(request, session -> {
            PdfDictionary reopened = (PdfDictionary) inspectTestValue(session);
            assertEquals(PdfValueKind.DICTIONARY, reopened.getKind());
            assertEquals(2, reopened.size());
            assertSame(PdfNull.INSTANCE, reopened.get(PdfName.of("Null")));
            assertEquals(
                    PdfBoolean.of(true),
                    reopened.get(PdfName.of("Boolean")));
            return null;
        });
    }

    @Test
    public void dictionaryTraversalDiscoversUnknownEntriesWithinBound()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path rewritten = temporaryFolder.getRoot().toPath().resolve("rewritten.pdf");
        createBlankDocument(input);
        rewriteValue(
                input,
                rewritten,
                PdfDictionary.builder()
                        .put(PdfName.of("Alpha"), PdfNull.INSTANCE)
                        .put(PdfName.of("Beta"), PdfBoolean.of(true))
                        .build());

        new DocumentWorkflow().execute(sourceRequest(rewritten), session -> {
            PdfDictionary dictionary = (PdfDictionary) inspectTestValue(session);
            Set<PdfName> discovered = new HashSet<PdfName>();
            for (int index = 0; index < dictionary.size(); index++) {
                PdfDictionaryEntry entry = dictionary.getEntry(index);
                discovered.add(entry.getName());
            }
            assertEquals(2, discovered.size());
            assertTrue(discovered.contains(PdfName.of("Alpha")));
            assertTrue(discovered.contains(PdfName.of("Beta")));
            return null;
        });
    }

    @Test
    public void indirectReferenceRoundTripsThroughRewriteAndReopen() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path rewritten = temporaryFolder.getRoot().toPath().resolve("rewritten.pdf");
        createBlankDocument(input);

        WorkflowRequest rewrite = WorkflowRequest.builder()
                .source("input", DocumentSource.path(input))
                .primarySource("input")
                .target("output", PublicationTarget.path(rewritten))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(rewrite, session -> {
            ObjectReference root = session.query(DocumentRootReference.INSTANCE);
            PdfDictionary catalog = inspectDictionary(session, root);
            PdfValue pages = catalog.get(PdfName.of("Pages"));
            assertEquals(PdfValueKind.INDIRECT_REFERENCE, pages.getKind());
            session.execute(DocumentPatch.builder()
                    .setDictionaryEntry(root, TEST_VALUE, pages)
                    .build());
            PdfIndirectReference afterPatch = (PdfIndirectReference) catalog.get(
                    TEST_VALUE);
            assertEquals(
                    ((PdfIndirectReference) pages).getReference(),
                    afterPatch.getReference());
            return null;
        });

        new DocumentWorkflow().execute(sourceRequest(rewritten), session -> {
            ObjectReference root = session.query(DocumentRootReference.INSTANCE);
            PdfDictionary catalog = inspectDictionary(session, root);
            PdfIndirectReference expected = (PdfIndirectReference) catalog.get(
                    PdfName.of("Pages"));
            PdfIndirectReference reopened = (PdfIndirectReference) catalog.get(
                    TEST_VALUE);
            assertEquals(PdfValueKind.INDIRECT_REFERENCE, reopened.getKind());
            assertEquals(expected.getReference(), reopened.getReference());
            assertEquals(
                    PdfValueKind.DICTIONARY,
                    session.query(InspectObject.version1(
                            reopened.getReference(),
                            PdfInspectionLimits.of(2, 0L))).getKind());
            return null;
        });
    }

    @Test
    public void streamValueRoundTripsThroughRewriteAndReopen() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path rewritten = temporaryFolder.getRoot().toPath().resolve("rewritten.pdf");
        createBlankDocument(input);
        byte[] expectedBytes = new byte[] {10, 20, 30, 40};
        PdfStream stream = PdfStream.of(
                PdfDictionary.builder()
                        .put(PdfName.of("Subtype"), PdfName.of("T09Data"))
                        .build(),
                expectedBytes);

        rewriteValue(input, rewritten, stream);

        new DocumentWorkflow().execute(sourceRequest(rewritten), session -> {
            PdfIndirectReference stored = (PdfIndirectReference)
                    inspectTestValue(session);
            PdfStream reopened = (PdfStream) session.query(
                    InspectObject.version1(
                            stored.getReference(),
                            PdfInspectionLimits.of(4, 16L)));
            assertEquals(PdfValueKind.STREAM, reopened.getKind());
            assertEquals(stored.getReference(), reopened.getReference().get());
            assertEquals(
                    PdfName.of("T09Data"),
                    reopened.getDictionary().get(PdfName.of("Subtype")));
            assertArrayEquals(expectedBytes, reopened.readBytes());
            return null;
        });
    }

    @Test
    public void lazyTraversalFailsPredictablyAfterSessionClose() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path rewritten = temporaryFolder.getRoot().toPath().resolve("rewritten.pdf");
        createBlankDocument(input);
        rewriteValue(input, rewritten, PdfArray.of(PdfNull.INSTANCE));

        PdfArray retained = (PdfArray) readTestValue(rewritten);

        try {
            retained.get(0);
            fail("Expected the retained traversal to expire");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.PDF_VALUE_VIEW_EXPIRED,
                    failure.getCode());
            assertEquals("document.value.inspect-patch", failure.getCapabilityId());
            assertEquals(
                    "The PDF Value view is no longer active.",
                    failure.getDiagnostic());
        }
    }

    @Test
    public void lazyStreamFailsPredictablyAfterSessionClose() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path rewritten = temporaryFolder.getRoot().toPath().resolve("rewritten.pdf");
        createBlankDocument(input);
        rewriteValue(
                input,
                rewritten,
                PdfStream.of(PdfDictionary.builder().build(), new byte[] {1}));

        PdfStream retained = new DocumentWorkflow().execute(
                sourceRequest(rewritten),
                session -> {
                    PdfIndirectReference stored = (PdfIndirectReference)
                            inspectTestValue(session);
                    return (PdfStream) session.query(InspectObject.version1(
                            stored.getReference(),
                            PdfInspectionLimits.of(1, 1L)));
                }).getResult();

        try {
            retained.readBytes();
            fail("Expected the retained stream to expire");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.PDF_VALUE_VIEW_EXPIRED,
                    failure.getCode());
            assertEquals("document.value.inspect-patch", failure.getCapabilityId());
            assertEquals(
                    "The PDF Value view is no longer active.",
                    failure.getDiagnostic());
        }
    }

    @Test
    public void lazyTraversalFailsPredictablyAfterDeclaredLimitExhaustion()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path rewritten = temporaryFolder.getRoot().toPath().resolve("rewritten.pdf");
        createBlankDocument(input);
        rewriteValue(
                input,
                rewritten,
                PdfArray.of(PdfNull.INSTANCE, PdfBoolean.of(true)));

        new DocumentWorkflow().execute(sourceRequest(rewritten), session -> {
            ObjectReference root = session.query(DocumentRootReference.INSTANCE);
            PdfDictionary catalog = (PdfDictionary) session.query(
                    InspectObject.version1(
                            root,
                            PdfInspectionLimits.of(2, 0L)));
            PdfArray array = (PdfArray) catalog.get(TEST_VALUE);
            assertSame(PdfNull.INSTANCE, array.get(0));
            try {
                array.get(1);
                fail("Expected the traversal limit to be exhausted");
            } catch (DocumentFailure failure) {
                assertEquals(
                        DocumentFailureCode.PDF_VALUE_LIMIT_EXCEEDED,
                        failure.getCode());
                assertEquals(
                        "document.value.inspect-patch",
                        failure.getCapabilityId());
                assertEquals(
                        "The PDF Value inspection limit was exceeded.",
                        failure.getDiagnostic());
            }
            return null;
        });
    }

    @Test
    public void lazyStreamFailsPredictablyAfterDeclaredLimitExhaustion()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path rewritten = temporaryFolder.getRoot().toPath().resolve("rewritten.pdf");
        createBlankDocument(input);
        rewriteValue(
                input,
                rewritten,
                PdfStream.of(
                        PdfDictionary.builder().build(),
                        new byte[] {1, 2, 3, 4}));

        new DocumentWorkflow().execute(sourceRequest(rewritten), session -> {
            PdfIndirectReference stored = (PdfIndirectReference)
                    inspectTestValue(session);
            PdfStream stream = (PdfStream) session.query(InspectObject.version1(
                    stored.getReference(),
                    PdfInspectionLimits.of(1, 3L)));
            try {
                stream.readBytes();
                fail("Expected the decoded-stream limit to be exhausted");
            } catch (DocumentFailure failure) {
                assertEquals(
                        DocumentFailureCode.PDF_VALUE_LIMIT_EXCEEDED,
                        failure.getCode());
                assertEquals(
                        "document.value.inspect-patch",
                        failure.getCapabilityId());
                assertEquals(
                        "The PDF Value inspection limit was exceeded.",
                        failure.getDiagnostic());
            }
            return null;
        });
    }

    @Test
    public void patchRejectsObjectReferenceFromAnotherSession() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        createBlankDocument(input);
        ObjectReference foreignRoot = new DocumentWorkflow().execute(
                sourceRequest(input),
                session -> session.query(DocumentRootReference.INSTANCE))
                .getResult();

        try {
            new DocumentWorkflow().execute(sourceRequest(input), session -> {
                session.execute(DocumentPatch.builder()
                        .setDictionaryEntry(
                                foreignRoot,
                                TEST_VALUE,
                                PdfNull.INSTANCE)
                        .build());
                return null;
            });
            fail("Expected the foreign Object Reference to be rejected");
        } catch (DocumentFailure failure) {
            assertSafePatchFailure(
                    failure,
                    DocumentFailureCode.OBJECT_REFERENCE_OWNERSHIP_INVALID,
                    "The Object Reference does not belong to this Session.");
        }
    }

    @Test
    public void patchRejectsReferenceCycle() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        createBlankDocument(input);

        try {
            new DocumentWorkflow().execute(sourceRequest(input), session -> {
                ObjectReference root = session.query(
                        DocumentRootReference.INSTANCE);
                session.execute(DocumentPatch.builder()
                        .setDictionaryEntry(
                                root,
                                TEST_VALUE,
                                PdfIndirectReference.of(root))
                        .build());
                return null;
            });
            fail("Expected the reference cycle to be rejected");
        } catch (DocumentFailure failure) {
            assertSafePatchFailure(
                    failure,
                    DocumentFailureCode.PATCH_CYCLE_REJECTED,
                    "The Document Patch would introduce a reference cycle.");
        }
    }

    @Test
    public void patchRejectsCycleThroughExistingObjectGraph() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        createBlankDocument(input);

        try {
            new DocumentWorkflow().execute(sourceRequest(input), session -> {
                ObjectReference root = session.query(
                        DocumentRootReference.INSTANCE);
                PdfDictionary catalog = inspectDictionary(session, root);
                ObjectReference pages = ((PdfIndirectReference) catalog.get(
                        PdfName.of("Pages"))).getReference();
                session.execute(DocumentPatch.builder()
                        .setDictionaryEntry(
                                pages,
                                TEST_VALUE,
                                PdfIndirectReference.of(root))
                        .build());
                return null;
            });
            fail("Expected the existing object graph cycle to be rejected");
        } catch (DocumentFailure failure) {
            assertSafePatchFailure(
                    failure,
                    DocumentFailureCode.PATCH_CYCLE_REJECTED,
                    "The Document Patch would introduce a reference cycle.");
        }
    }

    @Test
    public void patchFailureDoesNotApplyEarlierChanges() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path rewritten = temporaryFolder.getRoot().toPath().resolve("rewritten.pdf");
        createBlankDocument(input);
        PdfName earlierName = PdfName.of("T09EarlierValue");

        WorkflowRequest rewrite = WorkflowRequest.builder()
                .source("input", DocumentSource.path(input))
                .primarySource("input")
                .target("output", PublicationTarget.path(rewritten))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(rewrite, session -> {
            ObjectReference root = session.query(DocumentRootReference.INSTANCE);
            PdfDictionary catalog = inspectDictionary(session, root);
            try {
                session.execute(DocumentPatch.builder()
                        .setDictionaryEntry(
                                root,
                                earlierName,
                                PdfBoolean.of(true))
                        .setDictionaryEntry(
                                root,
                                PdfName.of("T09InvalidNumber"),
                                PdfNumber.of(new BigDecimal("1e1000")))
                        .build());
                fail("Expected the invalid number to reject the Patch");
            } catch (DocumentFailure failure) {
                assertSafePatchFailure(
                        failure,
                        DocumentFailureCode.COMMAND_REJECTED,
                        "The Document Patch contains an invalid PDF number.");
            }
            assertNull(catalog.get(earlierName));
            return null;
        });

        new DocumentWorkflow().execute(sourceRequest(rewritten), session -> {
            ObjectReference root = session.query(DocumentRootReference.INSTANCE);
            assertNull(inspectDictionary(session, root).get(earlierName));
            return null;
        });
    }

    @Test
    public void patchRejectsEngineOwnedStreamMetadataChange() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path withStream = temporaryFolder.getRoot().toPath().resolve("stream.pdf");
        createBlankDocument(input);
        rewriteValue(
                input,
                withStream,
                PdfStream.of(
                        PdfDictionary.builder().build(),
                        new byte[] {1, 2, 3}));

        try {
            new DocumentWorkflow().execute(sourceRequest(withStream), session -> {
                PdfIndirectReference stored = (PdfIndirectReference)
                        inspectTestValue(session);
                session.execute(DocumentPatch.builder()
                        .setDictionaryEntry(
                                stored.getReference(),
                                PdfName.of("Length"),
                                PdfNumber.of(99L))
                        .build());
                return null;
            });
            fail("Expected the stream metadata change to be rejected");
        } catch (DocumentFailure failure) {
            assertSafePatchFailure(
                    failure,
                    DocumentFailureCode.PATCH_STREAM_CHANGE_REJECTED,
                    "The Document Patch cannot change engine-owned stream metadata.");
        }
    }

    @Test
    public void patchRejectsValueImplementationNotOwnedByLibrary() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        createBlankDocument(input);
        PdfValue foreignValue = new PdfValue() {
            @Override
            public PdfValueKind getKind() {
                return PdfValueKind.NAME;
            }
        };

        try {
            new DocumentWorkflow().execute(sourceRequest(input), session -> {
                ObjectReference root = session.query(
                        DocumentRootReference.INSTANCE);
                session.execute(DocumentPatch.builder()
                        .setDictionaryEntry(root, TEST_VALUE, foreignValue)
                        .build());
                return null;
            });
            fail("Expected the foreign PDF Value to be rejected");
        } catch (DocumentFailure failure) {
            assertSafePatchFailure(
                    failure,
                    DocumentFailureCode.PATCH_VALUE_REJECTED,
                    "The Document Patch contains a value not owned by Folio PDF.");
        }
    }

    private static WorkflowOutcome<Void> rewriteValue(
            Path input,
            Path output,
            PdfValue value)
            throws Exception {
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.path(input))
                .primarySource("input")
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE)
                .build();

        return new DocumentWorkflow().execute(request, session -> {
            ObjectReference root = session.query(DocumentRootReference.INSTANCE);
            session.execute(DocumentPatch.builder()
                    .setDictionaryEntry(root, TEST_VALUE, value)
                    .build());
            return null;
        });
    }

    private static PdfValue readTestValue(Path source) throws Exception {
        WorkflowRequest request = sourceRequest(source);

        return new DocumentWorkflow().execute(request, session ->
                inspectTestValue(session)).getResult();
    }

    private static PdfValue inspectTestValue(DocumentSession session)
            throws DocumentFailure {
        ObjectReference root = session.query(DocumentRootReference.INSTANCE);
        PdfDictionary catalog = inspectDictionary(session, root);
        return catalog.get(TEST_VALUE);
    }

    private static PdfDictionary inspectDictionary(
            DocumentSession session,
            ObjectReference reference) throws DocumentFailure {
        return (PdfDictionary) session.query(
                InspectObject.version1(
                        reference,
                        PdfInspectionLimits.of(8, 0L)));
    }

    private static WorkflowRequest sourceRequest(Path source) {
        return WorkflowRequest.builder()
                .source("input", DocumentSource.path(source))
                .primarySource("input")
                .saveMode(SaveMode.REWRITE)
                .build();
    }

    private static void assertSafePatchFailure(
            DocumentFailure failure,
            DocumentFailureCode expectedCode,
            String expectedDiagnostic) {
        assertEquals(expectedCode, failure.getCode());
        assertEquals("document.value.inspect-patch", failure.getCapabilityId());
        assertEquals(expectedDiagnostic, failure.getDiagnostic());
        assertFalse(failure.getDiagnostic().contains("org.apache.pdfbox"));
        assertNull(failure.getCause());
    }

    private static void createBlankDocument(Path target) throws Exception {
        WorkflowRequest request = WorkflowRequest.builder()
                .target("output", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .build();

        new DocumentWorkflow().execute(request, session -> {
            session.execute(AddBlankPage.INSTANCE);
            return null;
        });
    }
}
