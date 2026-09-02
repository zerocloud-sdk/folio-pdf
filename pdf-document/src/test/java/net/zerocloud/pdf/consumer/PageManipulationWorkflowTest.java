package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PageRange;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfString;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.PdfValueKind;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.command.CopyPages;
import net.zerocloud.pdf.command.InsertBlankPage;
import net.zerocloud.pdf.command.MergeDocuments;
import net.zerocloud.pdf.command.MovePages;
import net.zerocloud.pdf.command.RemovePages;
import net.zerocloud.pdf.command.SplitDocument;
import net.zerocloud.pdf.query.DocumentInfo;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.PageObjectReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class PageManipulationWorkflowTest {

    private static final PdfName PAGE_MARKER = PdfName.of("T10Marker");
    private static final PdfName PARENT = PdfName.of("Parent");

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void pageObjectQuerySupportsPatchAndReopenAtTheWorkflowSeam()
            throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve("tagged.pdf");
        WorkflowRequest creation = WorkflowRequest.builder()
                .target("output", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .build();

        new DocumentWorkflow().execute(creation, session -> {
            session.execute(AddBlankPage.INSTANCE);
            ObjectReference page = session.query(
                    PageObjectReference.version1(1));
            session.execute(DocumentPatch.builder()
                    .setDictionaryEntry(page, PAGE_MARKER, PdfName.of("alpha"))
                    .build());
            return null;
        });

        WorkflowRequest reopening = WorkflowRequest.builder()
                .source("input", DocumentSource.path(target))
                .primarySource("input")
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(reopening, session -> {
            ObjectReference page = session.query(
                    PageObjectReference.version1(1));
            PdfDictionary dictionary = (PdfDictionary) session.query(
                    InspectObject.version1(
                            page,
                            PdfInspectionLimits.of(8, 0L)));
            assertEquals(PdfName.of("alpha"), dictionary.get(PAGE_MARKER));
            return null;
        });
    }

    @Test
    public void libraryCreatedPageReferencesStayIndirectBeforeAndAfterReopen()
            throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve(
                "indirect-page-reference.pdf");
        PdfName catalogPage = PdfName.of("T10PageReference");
        WorkflowRequest creation = WorkflowRequest.builder()
                .target("output", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .build();

        new DocumentWorkflow().execute(creation, session -> {
            session.execute(AddBlankPage.INSTANCE);
            ObjectReference page = session.query(
                    PageObjectReference.version1(1));
            ObjectReference catalog = session.query(
                    DocumentRootReference.INSTANCE);
            session.execute(DocumentPatch.builder()
                    .setDictionaryEntry(
                            catalog,
                            catalogPage,
                            PdfIndirectReference.of(page))
                    .build());

            PdfValue stored = inspectDictionary(session, catalog).get(
                    catalogPage);
            assertEquals(PdfValueKind.INDIRECT_REFERENCE, stored.getKind());
            assertEquals(
                    page,
                    ((PdfIndirectReference) stored).getReference());
            return null;
        });

        WorkflowRequest reopening = WorkflowRequest.builder()
                .source("input", DocumentSource.path(target))
                .primarySource("input")
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(reopening, session -> {
            ObjectReference page = session.query(
                    PageObjectReference.version1(1));
            PdfValue stored = inspectDictionary(
                    session,
                    session.query(DocumentRootReference.INSTANCE))
                    .get(catalogPage);
            assertEquals(PdfValueKind.INDIRECT_REFERENCE, stored.getKind());
            assertEquals(
                    page,
                    ((PdfIndirectReference) stored).getReference());
            return null;
        });
    }

    @Test
    public void pageObjectQueryRejectsAnOutOfRangePageStably()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        createTaggedDocument(input, "alpha");
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.path(input))
                .primarySource("input")
                .saveMode(SaveMode.REWRITE)
                .build();

        try {
            new DocumentWorkflow().execute(
                    request,
                    session -> session.query(
                            PageObjectReference.version1(2)));
            fail("Expected the page Object Reference query to fail");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.PAGE_RANGE_INVALID,
                    failure.getCode());
            assertEquals(
                    "document.page.manipulate-merge-split",
                    failure.getCapabilityId());
            assertEquals(
                    "The page range is outside the current document.",
                    failure.getDiagnostic());
            assertNull(failure.getCause());
        }
    }

    @Test
    public void pageObjectQueryRejectsMalformedPageNodesWithoutRepairingThem()
            throws Exception {
        byte[] source = missingPageTypeFixture();
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.bytes(source, source.length))
                .primarySource("input")
                .saveMode(SaveMode.REWRITE)
                .build();

        new DocumentWorkflow().execute(request, session -> {
            try {
                session.query(PageObjectReference.version1(1));
                fail("Expected malformed page query rejection.");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.QUERY_FAILED, failure.getCode());
            }
            assertPreservationRejected(() -> session.execute(
                    InsertBlankPage.version1(2)));
            return null;
        });
    }

    @Test
    public void pageObjectQueryRejectsDirectPageNodesWithoutCreatingReferences()
            throws Exception {
        byte[] source = directPageNodeFixture();
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.bytes(source, source.length))
                .primarySource("input")
                .saveMode(SaveMode.REWRITE)
                .build();

        new DocumentWorkflow().execute(request, session -> {
            try {
                session.query(PageObjectReference.version1(1));
                fail("Expected direct page query rejection.");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.QUERY_FAILED, failure.getCode());
            }
            assertPreservationRejected(() -> session.execute(
                    InsertBlankPage.version1(2)));
            return null;
        });
    }

    @Test
    public void directIntermediatePageTreeNodesAreRejectedWithoutRepair()
            throws Exception {
        byte[] source = directIntermediatePageTreeNodeFixture();
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.bytes(source, source.length))
                .primarySource("input")
                .saveMode(SaveMode.REWRITE)
                .build();

        List<DocumentFailureCode> observed = new DocumentWorkflow().execute(
                request,
                session -> {
                    List<DocumentFailureCode> failures =
                            new ArrayList<DocumentFailureCode>();
                    try {
                        session.query(PageObjectReference.version1(1));
                    } catch (DocumentFailure failure) {
                        failures.add(failure.getCode());
                    }
                    try {
                        session.execute(InsertBlankPage.version1(2));
                    } catch (DocumentFailure failure) {
                        failures.add(failure.getCode());
                    }
                    return failures;
                })
                .getResult();

        assertEquals(
                Arrays.asList(
                        DocumentFailureCode.QUERY_FAILED,
                        DocumentFailureCode.PRESERVATION_UNSUPPORTED),
                observed);
    }

    @Test
    public void pageObjectQueryRejectsMismatchedParentsAndCountsWithoutRepair()
            throws Exception {
        assertFixturePageQueryRejected(mismatchedPageParentFixture());
        assertFixturePageQueryRejected(mismatchedPageCountFixture());
    }

    @Test
    public void pageObjectQuerySharesIdentityWithThePageTreeReference()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("identity.pdf");
        createTaggedDocument(input, "alpha");
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.path(input))
                .primarySource("input")
                .saveMode(SaveMode.REWRITE)
                .build();

        new DocumentWorkflow().execute(request, session -> {
            ObjectReference queriedPage = session.query(
                    PageObjectReference.version1(1));
            PdfDictionary catalog = inspectDictionary(
                    session,
                    session.query(DocumentRootReference.INSTANCE));
            PdfDictionary pages = dictionaryValue(
                    session,
                    catalog.get(PdfName.of("Pages")));
            PdfArray kids = (PdfArray) pages.get(PdfName.of("Kids"));
            ObjectReference traversedPage = ((PdfIndirectReference) kids.get(0))
                    .getReference();

            assertEquals(traversedPage, queriedPage);
            return null;
        });
    }

    @Test
    public void distinctIndirectScalarsKeepDistinctSessionIdentities()
            throws Exception {
        byte[] source = duplicateIndirectScalarFixture();
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.bytes(source, source.length))
                .primarySource("input")
                .saveMode(SaveMode.REWRITE)
                .build();

        new DocumentWorkflow().execute(request, session -> {
            PdfDictionary catalog = inspectDictionary(
                    session,
                    session.query(DocumentRootReference.INSTANCE));
            ObjectReference first = ((PdfIndirectReference) catalog.get(
                    PdfName.of("First"))).getReference();
            ObjectReference second = ((PdfIndirectReference) catalog.get(
                    PdfName.of("Second"))).getReference();

            assertNotEquals(first, second);
            assertEquals(
                    PdfName.of("Same"),
                    session.query(InspectObject.version1(
                            first,
                            PdfInspectionLimits.of(4, 0L))));
            assertEquals(
                    PdfName.of("Same"),
                    session.query(InspectObject.version1(
                            second,
                            PdfInspectionLimits.of(4, 0L))));
            return null;
        });
    }

    @Test
    public void pageIdentityRemainsStableWhenThePageIsReparented()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "reparented-identity.pdf");
        createTaggedDocument(input, "alpha", "bravo");
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.path(input))
                .primarySource("input")
                .saveMode(SaveMode.REWRITE)
                .build();

        new DocumentWorkflow().execute(request, session -> {
            ObjectReference before = session.query(
                    PageObjectReference.version1(1));
            session.execute(MovePages.version1(PageRange.of(1, 1), 2));
            ObjectReference after = session.query(
                    PageObjectReference.version1(2));

            assertEquals(before, after);
            return null;
        });
    }

    @Test
    public void splitRejectsALinkDestinationItCannotSafelyRetarget()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("linked.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "linked-unchanged.pdf");
        createTaggedDocument(input, "alpha", "bravo");

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    ObjectReference firstPage = session.query(
                            PageObjectReference.version1(1));
                    PdfDictionary link = PdfDictionary.builder()
                            .put(PdfName.of("Type"), PdfName.of("Annot"))
                            .put(PdfName.of("Subtype"), PdfName.of("Link"))
                            .put(PdfName.of("Rect"), PdfArray.of(
                                    PdfNumber.of(0L),
                                    PdfNumber.of(0L),
                                    PdfNumber.of(10L),
                                    PdfNumber.of(10L)))
                            .put(PdfName.of("Dest"), PdfName.of("chapter-two"))
                            .build();
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(
                                    firstPage,
                                    PdfName.of("Annots"),
                                    PdfArray.of(link))
                            .build());

                    assertPreservationRejected(() -> session.execute(
                            SplitDocument.version1()
                                    .target("output", PageRange.of(1, 1))
                                    .build()));
                    return null;
                });

        assertEquals(
                Arrays.<PdfValue>asList(
                        PdfName.of("alpha"),
                        PdfName.of("bravo")),
                readPageMarkers(output));
    }

    @Test
    public void splitRejectsCrossPageSeparationInformationBeforeMutation()
            throws Exception {
        assertFixtureSplitPreservationRejected(
                crossPageSeparationFixture(),
                "separation-info-unchanged.pdf");
    }

    @Test
    public void splitRejectsAnnotationReplyRelationshipsBeforeMutation()
            throws Exception {
        assertFixtureSplitPreservationRejected(
                annotationReplyFixture(),
                "annotation-reply-unchanged.pdf");
    }

    @Test
    public void splitRejectsResourceGraphsReachingExcludedPagesBeforeMutation()
            throws Exception {
        assertFixtureSplitPreservationRejected(
                crossPageResourceReferenceFixture(),
                "cross-page-resource-unchanged.pdf");
    }

    @Test
    public void pageMutationRejectsUnprovenCatalogAndPageStructures()
            throws Exception {
        assertUnsupportedStructureRejected(
                "outlines-malformed",
                true,
                PdfName.of("Outlines"),
                PdfDictionary.builder()
                        .put(PdfName.of("Type"), PdfName.of("Outlines"))
                        .put(PdfName.of("Count"), PdfNumber.of(1L))
                        .build());
        assertUnsupportedStructureRejected(
                "names-unknown-subtree",
                true,
                PdfName.of("Names"),
                PdfDictionary.builder()
                        .put(
                                PdfName.of("JavaScript"),
                                PdfDictionary.builder().build())
                        .build());
        assertUnsupportedStructureRejected(
                "catalog-dests",
                true,
                PdfName.of("Dests"),
                PdfDictionary.builder().build());
        assertUnsupportedStructureRejected(
                "forms",
                true,
                PdfName.of("AcroForm"),
                PdfDictionary.builder().build());
        assertUnsupportedStructureRejected(
                "tag-tree",
                true,
                PdfName.of("StructTreeRoot"),
                PdfDictionary.builder().build());
        assertUnsupportedStructureRejected(
                "threads",
                true,
                PdfName.of("Threads"),
                PdfArray.of());
        assertUnsupportedStructureRejected(
                "beads",
                false,
                PdfName.of("B"),
                PdfArray.of());
        assertUnsupportedStructureRejected(
                "tagged-page",
                false,
                PdfName.of("StructParents"),
                PdfNumber.of(0L));
        assertUnsupportedStructureRejected(
                "page-actions",
                false,
                PdfName.of("AA"),
                PdfDictionary.builder().build());
        assertUnsupportedStructureRejected(
                "widget",
                false,
                PdfName.of("Annots"),
                PdfArray.of(PdfDictionary.builder()
                        .put(PdfName.of("Type"), PdfName.of("Annot"))
                        .put(PdfName.of("Subtype"), PdfName.of("Widget"))
                        .put(PdfName.of("Rect"), PdfArray.of(
                                PdfNumber.of(0L),
                                PdfNumber.of(0L),
                                PdfNumber.of(10L),
                                PdfNumber.of(10L)))
                        .build()));
    }

    @Test
    public void splitRejectsUnknownPageTreeNodeDataBeforeMutation()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "unknown-page-tree.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "unknown-page-tree-unchanged.pdf");
        createTaggedDocument(input, "alpha", "bravo");

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    PdfDictionary catalog = inspectDictionary(
                            session,
                            session.query(DocumentRootReference.INSTANCE));
                    ObjectReference pageTree = ((PdfIndirectReference) catalog.get(
                            PdfName.of("Pages"))).getReference();
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(
                                    pageTree,
                                    PdfName.of("T10UnknownTreeData"),
                                    PdfName.of("must-not-be-lost"))
                            .build());
                    assertPreservationRejected(() -> session.execute(
                            SplitDocument.version1()
                                    .target("output", PageRange.of(1, 1))
                                    .build()));
                    return null;
                });

        assertEquals(
                Arrays.<PdfValue>asList(
                        PdfName.of("alpha"),
                        PdfName.of("bravo")),
                readPageMarkers(output));
    }

    @Test
    public void pageMutationRejectsMismatchedPageTreeParents()
            throws Exception {
        assertFixturePageTreePreservationRejected(
                mismatchedPageParentFixture());
    }

    @Test
    public void pageMutationRejectsMismatchedPageTreeCounts()
            throws Exception {
        assertFixturePageTreePreservationRejected(
                mismatchedPageCountFixture());
    }

    @Test
    public void pageMutationRejectsRepeatedPageTreeNodes()
            throws Exception {
        assertFixturePageTreePreservationRejected(
                repeatedPageNodeFixture());
    }

    @Test
    public void splitRejectsUnknownTrailerDataBeforeMutation()
            throws Exception {
        byte[] source = simpleDocumentFixture(
                " /T10UnknownTrailer /must-not-be-lost");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "unknown-trailer-unchanged.pdf");
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.bytes(source, source.length))
                .primarySource("input")
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE)
                .build();

        new DocumentWorkflow().execute(request, session -> {
            assertPreservationRejected(() -> session.execute(
                    SplitDocument.version1()
                            .target("output", PageRange.of(1, 1))
                            .build()));
            return null;
        });

        assertEquals(
                Arrays.<PdfValue>asList((PdfValue) null),
                readPageMarkers(output));
    }

    @Test
    public void pageInsertionProducesRequestedOrderAfterReopen()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve("inserted.pdf");
        createTaggedDocument(input, "alpha", "charlie");

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(InsertBlankPage.version1(2));
                    return null;
                });

        assertEquals(
                Arrays.<PdfValue>asList(
                        PdfName.of("alpha"),
                        null,
                        PdfName.of("charlie")),
                readPageMarkers(output));
    }

    @Test
    public void pageRemovalProducesRequestedOrderAfterReopen()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve("removed.pdf");
        createTaggedDocument(input, "alpha", "bravo", "charlie", "delta");

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(RemovePages.version1(PageRange.of(2, 3)));
                    return null;
                });

        assertEquals(
                Arrays.<PdfValue>asList(
                        PdfName.of("alpha"),
                        PdfName.of("delta")),
                readPageMarkers(output));
    }

    @Test
    public void pageMovementUsesThePostRemovalDestinationPosition()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve("moved.pdf");
        createTaggedDocument(input, "alpha", "bravo", "charlie", "delta", "echo");

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(MovePages.version1(
                            PageRange.of(2, 3),
                            4));
                    return null;
                });

        assertEquals(
                Arrays.<PdfValue>asList(
                        PdfName.of("alpha"),
                        PdfName.of("delta"),
                        PdfName.of("echo"),
                        PdfName.of("bravo"),
                        PdfName.of("charlie")),
                readPageMarkers(output));
    }

    @Test
    public void pageMovementPreservesInheritedAndPageOwnedSemantics()
            throws Exception {
        byte[] source = inheritedPageFixture();
        Path output = temporaryFolder.getRoot().toPath()
                .resolve("preserved-move.pdf");
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.bytes(source, source.length))
                .primarySource("input")
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE)
                .build();

        new DocumentWorkflow().execute(request, session -> {
            session.execute(MovePages.version1(PageRange.of(1, 1), 2));
            return null;
        });

        assertEquals(
                Arrays.<Object>asList(
                        PdfName.of("alpha"),
                        Arrays.asList(
                                PdfNumber.of(0L),
                                PdfNumber.of(0L),
                                PdfNumber.of(300L),
                                PdfNumber.of(400L)),
                        Arrays.asList(
                                PdfNumber.of(10L),
                                PdfNumber.of(20L),
                                PdfNumber.of(290L),
                                PdfNumber.of(380L)),
                        PdfNumber.of(90L),
                        PdfName.of("nested"),
                        PdfString.of("note-alpha".getBytes(
                                StandardCharsets.ISO_8859_1)),
                        "q\nQ\n",
                        Boolean.TRUE),
                readPageSemantics(output, 2));
    }

    @Test
    public void pageMovementRejectsMalformedInheritedAttributesBeforeMutation()
            throws Exception {
        assertMovePreservationRejected(
                malformedInheritedAttributeFixture(
                        "/MediaBox /UnknownMediaBox"));
        assertMovePreservationRejected(
                malformedInheritedAttributeFixture(
                        "/MediaBox [0 0 612 792] /CropBox /UnknownCropBox"));
        assertMovePreservationRejected(
                malformedInheritedAttributeFixture(
                        "/MediaBox [0 0 612 792] /Rotate 45"));
        assertMovePreservationRejected(
                malformedInheritedAttributeFixture(
                        "/MediaBox [0 0 612 792] /Resources /UnknownResources"));
    }

    @Test
    public void pageCopyPreservesOrderAndPageSemanticsAfterReopen()
            throws Exception {
        byte[] source = inheritedPageFixture();
        Path output = temporaryFolder.getRoot().toPath()
                .resolve("preserved-copy.pdf");
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.bytes(source, source.length))
                .primarySource("input")
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE)
                .build();

        new DocumentWorkflow().execute(request, session -> {
            session.execute(CopyPages.version1(PageRange.of(1, 1), 3));
            return null;
        });

        assertEquals(
                Arrays.<PdfValue>asList(
                        PdfName.of("alpha"),
                        PdfName.of("bravo"),
                        PdfName.of("alpha")),
                readPageMarkers(output));
        assertEquals(
                Arrays.<Object>asList(
                        PdfName.of("alpha"),
                        Arrays.asList(
                                PdfNumber.of(0L),
                                PdfNumber.of(0L),
                                PdfNumber.of(300L),
                                PdfNumber.of(400L)),
                        Arrays.asList(
                                PdfNumber.of(10L),
                                PdfNumber.of(20L),
                                PdfNumber.of(290L),
                                PdfNumber.of(380L)),
                        PdfNumber.of(90L),
                        PdfName.of("nested"),
                        PdfString.of("note-alpha".getBytes(
                                StandardCharsets.ISO_8859_1)),
                        "q\nQ\n",
                        Boolean.TRUE),
                readPageSemantics(output, 3));
    }

    @Test
    public void invalidPageRangeFailsStablyWithoutChangingTheDocument()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path output = temporaryFolder.getRoot().toPath()
                .resolve("unchanged-after-range-failure.pdf");
        createTaggedDocument(input, "alpha", "bravo");

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    try {
                        session.execute(RemovePages.version1(PageRange.of(2, 3)));
                        fail("Expected the page range to be rejected");
                    } catch (DocumentFailure failure) {
                        assertEquals(
                                DocumentFailureCode.PAGE_RANGE_INVALID,
                                failure.getCode());
                        assertEquals(
                                "document.page.manipulate-merge-split",
                                failure.getCapabilityId());
                        assertEquals(
                                "The page range is outside the current document.",
                                failure.getDiagnostic());
                        assertNull(failure.getCause());
                    }
                    return null;
                });

        assertEquals(
                Arrays.<PdfValue>asList(
                        PdfName.of("alpha"),
                        PdfName.of("bravo")),
                readPageMarkers(output));
    }

    @Test
    public void invalidPagePositionsFailStablyWithoutChangingTheDocument()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        createTaggedDocument(input, "alpha", "bravo");

        assertRejectedCommandLeavesMarkers(
                input,
                temporaryFolder.getRoot().toPath().resolve("insert-invalid.pdf"),
                InsertBlankPage.version1(0),
                DocumentFailureCode.PAGE_POSITION_INVALID,
                "The page position is outside the current document.");
        assertRejectedCommandLeavesMarkers(
                input,
                temporaryFolder.getRoot().toPath().resolve("move-invalid.pdf"),
                MovePages.version1(PageRange.of(1, 1), 3),
                DocumentFailureCode.PAGE_POSITION_INVALID,
                "The page position is outside the current document.");
        assertRejectedCommandLeavesMarkers(
                input,
                temporaryFolder.getRoot().toPath().resolve("copy-invalid.pdf"),
                CopyPages.version1(PageRange.of(1, 1), 4),
                DocumentFailureCode.PAGE_POSITION_INVALID,
                "The page position is outside the current document.");
    }

    @Test
    public void invalidRangesAreRejectedConsistentlyByRangeCommands()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        createTaggedDocument(input, "alpha", "bravo");

        assertRejectedCommandLeavesMarkers(
                input,
                temporaryFolder.getRoot().toPath().resolve("move-range-invalid.pdf"),
                MovePages.version1(PageRange.of(2, 3), 1),
                DocumentFailureCode.PAGE_RANGE_INVALID,
                "The page range is outside the current document.");
        assertRejectedCommandLeavesMarkers(
                input,
                temporaryFolder.getRoot().toPath().resolve("copy-range-invalid.pdf"),
                CopyPages.version1(PageRange.of(0, 1), 1),
                DocumentFailureCode.PAGE_RANGE_INVALID,
                "The page range is outside the current document.");
    }

    @Test
    public void mergeConsumesMultipleNamedSourcesInDeterministicOrder()
            throws Exception {
        Path primary = temporaryFolder.getRoot().toPath().resolve("primary.pdf");
        Path exhibits = temporaryFolder.getRoot().toPath().resolve("exhibits.pdf");
        Path merged = temporaryFolder.getRoot().toPath().resolve("merged.pdf");
        createTaggedDocument(primary, "primary");
        createTaggedDocument(exhibits, "omega");
        byte[] appendix = inheritedPageFixture();

        WorkflowRequest request = WorkflowRequest.builder()
                .source("primary", DocumentSource.path(primary))
                .source(
                        "appendix",
                        DocumentSource.bytes(appendix, appendix.length))
                .source("exhibits", DocumentSource.path(exhibits))
                .primarySource("primary")
                .target("merged", PublicationTarget.path(merged))
                .saveMode(SaveMode.REWRITE)
                .build();
        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(
                request,
                session -> {
                    session.execute(MergeDocuments.version1(
                            "appendix",
                            "exhibits"));
                    return null;
                });

        assertEquals(
                "document.page.manipulate-merge-split",
                outcome.getCapabilityId());
        assertEquals(
                Arrays.<PdfValue>asList(
                        PdfName.of("primary"),
                        PdfName.of("alpha"),
                        PdfName.of("bravo"),
                        PdfName.of("omega")),
                readPageMarkers(merged));
        assertEquals(
                Arrays.<Object>asList(
                        PdfName.of("alpha"),
                        Arrays.asList(
                                PdfNumber.of(0L),
                                PdfNumber.of(0L),
                                PdfNumber.of(300L),
                                PdfNumber.of(400L)),
                        Arrays.asList(
                                PdfNumber.of(10L),
                                PdfNumber.of(20L),
                                PdfNumber.of(290L),
                                PdfNumber.of(380L)),
                        PdfNumber.of(90L),
                        PdfName.of("nested"),
                        PdfString.of("note-alpha".getBytes(
                                StandardCharsets.ISO_8859_1)),
                        "q\nQ\n",
                        Boolean.TRUE),
                readPageSemantics(merged, 2));
        rewriteContentMarker(
                merged,
                2,
                PdfName.of("destination-only"));
        assertEquals(
                PdfName.of("destination-only"),
                readContentMarker(DocumentSource.path(merged), 2));
        assertNull(readContentMarker(
                DocumentSource.bytes(appendix, appendix.length),
                1));
    }

    @Test
    public void mergeRejectsInvalidNamedSourceSelectionsWithoutMutation()
            throws Exception {
        Path primary = temporaryFolder.getRoot().toPath().resolve("primary.pdf");
        Path appendix = temporaryFolder.getRoot().toPath().resolve("appendix.pdf");
        createTaggedDocument(primary, "primary");
        createTaggedDocument(appendix, "appendix");

        assertMergeSelectionRejected(
                primary,
                appendix,
                temporaryFolder.getRoot().toPath().resolve("empty-merge.pdf"),
                MergeDocuments.version1());
        assertMergeSelectionRejected(
                primary,
                appendix,
                temporaryFolder.getRoot().toPath().resolve("primary-merge.pdf"),
                MergeDocuments.version1("primary"));
        assertMergeSelectionRejected(
                primary,
                appendix,
                temporaryFolder.getRoot().toPath().resolve("missing-merge.pdf"),
                MergeDocuments.version1("missing"));
        assertMergeSelectionRejected(
                primary,
                appendix,
                temporaryFolder.getRoot().toPath().resolve("duplicate-merge.pdf"),
                MergeDocuments.version1("appendix", "appendix"));
    }

    @Test
    public void invalidNamedSourceFailsBeforeCallerWorkOrMutation()
            throws Exception {
        Path primary = temporaryFolder.getRoot().toPath().resolve("primary.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve("output.pdf");
        createTaggedDocument(primary, "primary");
        byte[] malformed = "not-a-pdf".getBytes(StandardCharsets.US_ASCII);

        WorkflowRequest request = WorkflowRequest.builder()
                .source("primary", DocumentSource.path(primary))
                .source(
                        "malformed",
                        DocumentSource.bytes(malformed, malformed.length))
                .primarySource("primary")
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE)
                .build();
        final boolean[] workRan = new boolean[1];
        try {
            new DocumentWorkflow().execute(
                    request,
                    session -> {
                        workRan[0] = true;
                        return null;
                    });
            fail("Expected the additional Source to be rejected");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.SOURCE_READ_FAILED,
                    failure.getCode());
            assertEquals(
                    "document.blank.create-publish-reopen",
                    failure.getCapabilityId());
            assertEquals(
                    "The source could not be opened as a PDF document.",
                    failure.getDiagnostic());
            assertNull(failure.getCause());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
        }

        assertFalse(workRan[0]);
        assertFalse(Files.exists(output));
    }

    @Test
    public void mergeRejectsUnprovenStructuresInAnAdditionalSource()
            throws Exception {
        Path primary = temporaryFolder.getRoot().toPath().resolve("primary.pdf");
        Path appendix = temporaryFolder.getRoot().toPath().resolve(
                "unsafe-appendix.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "unsafe-merge-unchanged.pdf");
        createTaggedDocument(primary, "primary");
        WorkflowRequest unsafeSource = WorkflowRequest.builder()
                .target("appendix", PublicationTarget.path(appendix))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(unsafeSource, session -> {
            session.execute(AddBlankPage.INSTANCE);
            ObjectReference catalog = session.query(
                    DocumentRootReference.INSTANCE);
            session.execute(DocumentPatch.builder()
                    .setDictionaryEntry(
                            catalog,
                            PdfName.of("Outlines"),
                            PdfDictionary.builder()
                                    .put(
                                            PdfName.of("Type"),
                                            PdfName.of("Outlines"))
                                    .put(
                                            PdfName.of("Count"),
                                            PdfNumber.of(1L))
                                    .build())
                    .build());
            return null;
        });

        WorkflowRequest mergeRequest = WorkflowRequest.builder()
                .source("primary", DocumentSource.path(primary))
                .source("appendix", DocumentSource.path(appendix))
                .primarySource("primary")
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(mergeRequest, session -> {
            assertPreservationRejected(() -> session.execute(
                    MergeDocuments.version1("appendix")));
            return null;
        });

        assertEquals(
                Arrays.<PdfValue>asList(PdfName.of("primary")),
                readPageMarkers(output));
    }

    @Test
    public void copyAndSplitRejectUnprovenContentStreamMetadata()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "custom-content-stream.pdf");
        Path copyOutput = temporaryFolder.getRoot().toPath().resolve(
                "custom-content-copy-unchanged.pdf");
        Path splitOutput = temporaryFolder.getRoot().toPath().resolve(
                "custom-content-split-unchanged.pdf");
        PdfName marker = PdfName.of("sentinel");
        WorkflowRequest creation = WorkflowRequest.builder()
                .target("output", PublicationTarget.path(input))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(creation, session -> {
            session.execute(AddBlankPage.INSTANCE);
            ObjectReference page = session.query(
                    PageObjectReference.version1(1));
            session.execute(DocumentPatch.builder()
                    .setDictionaryEntry(
                            page,
                            PdfName.of("Contents"),
                            PdfStream.of(
                                    PdfDictionary.builder()
                                            .put(
                                                    PdfName.of("T10GraphMarker"),
                                                    marker)
                                            .build(),
                                    "q\nQ\n".getBytes(
                                            StandardCharsets.ISO_8859_1)))
                    .build());
            return null;
        });

        new DocumentWorkflow().execute(
                rewriteRequest(input, copyOutput),
                session -> {
                    assertPreservationRejected(() -> session.execute(
                            CopyPages.version1(PageRange.of(1, 1), 2)));
                    return null;
                });
        assertEquals(
                marker,
                readContentMarker(DocumentSource.path(copyOutput), 1));

        new DocumentWorkflow().execute(
                rewriteRequest(input, splitOutput),
                session -> {
                    assertPreservationRejected(() -> session.execute(
                            SplitDocument.version1()
                                    .target("output", PageRange.of(1, 1))
                                    .build()));
                    return null;
                });
        assertEquals(
                marker,
                readContentMarker(DocumentSource.path(splitOutput), 1));
    }

    @Test
    public void copyRejectsNestedContentStreamArraysBeforeMutation()
            throws Exception {
        assertFixtureCopyPreservationRejected(nestedContentArrayFixture());
    }

    @Test
    public void copyRejectsExternalContentStreamsBeforeMutation()
            throws Exception {
        assertFixtureCopyPreservationRejected(externalContentStreamFixture());
    }

    @Test
    public void copyAndSplitRejectUndecodableContentStreamsBeforeMutation()
            throws Exception {
        byte[] source = undecodableContentStreamFixture();

        assertFixtureCopyPreservationRejected(source);
        assertFixtureSplitPreservationRejected(
                source,
                "undecodable-content-split-unchanged.pdf");
    }

    @Test
    public void copyAndSplitRejectNonNameContentStreamFiltersBeforeMutation()
            throws Exception {
        byte[] source = contentStreamFilterFixture(
                "/Filter 42",
                "q\nQ\n");

        assertFixtureCopyPreservationRejected(source);
        assertFixtureSplitPreservationRejected(
                source,
                "non-name-content-filter-split-unchanged.pdf");
    }

    @Test
    public void copyAndSplitRejectNonDictionaryDecodeParametersBeforeMutation()
            throws Exception {
        byte[] source = contentStreamFilterFixture(
                "/Filter /FlateDecode /DecodeParms /Bogus",
                deflate("q\nQ\n"));

        assertFixtureCopyPreservationRejected(source);
        assertFixtureSplitPreservationRejected(
                source,
                "non-dictionary-decode-parameters-split-unchanged.pdf");
    }

    @Test
    public void copyAndSplitCoverEveryStrictFlateRejectionBranch()
            throws Exception {
        String validFlate = deflate("q\nQ\n");
        List<byte[]> sources = Arrays.asList(
                contentStreamFilterFixture(
                        "/Filter [/FlateDecode]",
                        validFlate),
                contentStreamFilterFixture(
                        "/Filter /FlateDecode "
                                + "/DecodeParms << /Predictor 1 >>",
                        validFlate),
                contentStreamFilterFixture(
                        "/Filter /FlateDecode",
                        validFlate + "x"),
                contentStreamFilterFixture(
                        "/Filter /FlateDecode",
                        deflateWithPresetDictionary("q\nQ\n")));

        for (int index = 0; index < sources.size(); index++) {
            byte[] source = sources.get(index);
            assertFixtureCopyPreservationRejected(source);
            assertFixtureSplitPreservationRejected(
                    source,
                    "strict-flate-branch-" + index + "-unchanged.pdf");
        }
    }

    @Test
    public void copyAndSplitRejectMalformedFlateContentBeforeMutation()
            throws Exception {
        byte[] source = contentStreamFilterFixture(
                "/Filter /FlateDecode",
                "q\nQ\n");

        assertFixtureCopyPreservationRejected(source);
        assertFixtureSplitPreservationRejected(
                source,
                "malformed-flate-content-split-unchanged.pdf");
    }

    @Test
    public void copyAndSplitRejectUnprovenNonFlateFiltersBeforeMutation()
            throws Exception {
        byte[] source = contentStreamFilterFixture(
                "/Filter /ASCIIHexDecode",
                "710a510a>");

        assertFixtureCopyPreservationRejected(source);
        assertFixtureSplitPreservationRejected(
                source,
                "non-flate-content-split-unchanged.pdf");
    }

    @Test
    public void copyAndSplitPreserveStrictFlateContent() throws Exception {
        String decodedContent = "q\nQ\n";
        byte[] source = contentStreamFilterFixture(
                "/Filter /FlateDecode",
                deflate(decodedContent));
        Path copyOutput = temporaryFolder.getRoot().toPath().resolve(
                "strict-flate-copy.pdf");
        Path splitOutput = temporaryFolder.getRoot().toPath().resolve(
                "strict-flate-split.pdf");

        WorkflowRequest copyRequest = WorkflowRequest.builder()
                .source("input", DocumentSource.bytes(source, source.length))
                .primarySource("input")
                .target("output", PublicationTarget.path(copyOutput))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(copyRequest, session -> {
            session.execute(CopyPages.version1(PageRange.of(1, 1), 2));
            return null;
        });
        assertArrayEquals(
                decodedContent.getBytes(StandardCharsets.ISO_8859_1),
                readPageContent(DocumentSource.path(copyOutput), 2));

        WorkflowRequest splitRequest = WorkflowRequest.builder()
                .source("input", DocumentSource.bytes(source, source.length))
                .primarySource("input")
                .target("output", PublicationTarget.path(splitOutput))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(splitRequest, session -> {
            session.execute(SplitDocument.version1()
                    .target("output", PageRange.of(1, 1))
                    .build());
            return null;
        });
        assertArrayEquals(
                decodedContent.getBytes(StandardCharsets.ISO_8859_1),
                readPageContent(DocumentSource.path(splitOutput), 1));
    }

    @Test
    public void mergePreservesDocumentInformationFromAdditionalSources()
            throws Exception {
        Path primary = temporaryFolder.getRoot().toPath().resolve("primary.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "metadata-merged.pdf");
        Files.write(primary, primaryDocumentInformationFixture());
        byte[] appendix = documentInformationFixture();

        WorkflowRequest request = WorkflowRequest.builder()
                .source("primary", DocumentSource.path(primary))
                .source(
                        "appendix",
                        DocumentSource.bytes(appendix, appendix.length))
                .primarySource("primary")
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(request, session -> {
            session.execute(MergeDocuments.version1("appendix"));
            return null;
        });

        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("input", DocumentSource.path(output))
                        .primarySource("input")
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    assertEquals(
                            Integer.valueOf(2),
                            session.query(PageCount.INSTANCE));
                    PdfDictionary info = session.query(DocumentInfo.INSTANCE);
                    assertEquals(
                            PdfString.of("Primary".getBytes(
                                    StandardCharsets.ISO_8859_1)),
                            info.get(PdfName.of("Author")));
                    assertEquals(
                            PdfString.of("primary wins".getBytes(
                                    StandardCharsets.ISO_8859_1)),
                            info.get(PdfName.of("T11Shared")));
                    assertEquals(
                            PdfString.of("from source".getBytes(
                                    StandardCharsets.ISO_8859_1)),
                            info.get(PdfName.of("T11Note")));
                    return null;
                });
    }

    @Test
    public void mergeLeavesAnAdditionalCallerOwnedSourceStreamOpen()
            throws Exception {
        Path primary = temporaryFolder.getRoot().toPath().resolve("primary.pdf");
        Path appendix = temporaryFolder.getRoot().toPath().resolve("appendix.pdf");
        Path merged = temporaryFolder.getRoot().toPath().resolve("stream-merged.pdf");
        createTaggedDocument(primary, "primary");
        createTaggedDocument(appendix, "appendix");
        TrackingInputStream callerStream = new TrackingInputStream(
                Files.readAllBytes(appendix));

        WorkflowRequest request = WorkflowRequest.builder()
                .source("primary", DocumentSource.path(primary))
                .source(
                        "appendix",
                        DocumentSource.stream(callerStream, callerStream.available()))
                .primarySource("primary")
                .target("merged", PublicationTarget.path(merged))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(request, session -> {
            session.execute(MergeDocuments.version1("appendix"));
            return null;
        });

        assertFalse(callerStream.closeCalled);
        assertEquals(
                Arrays.<PdfValue>asList(
                        PdfName.of("primary"),
                        PdfName.of("appendix")),
                readPageMarkers(merged));
    }

    @Test
    public void mergePropagatesAdditionalCallerStreamRuntimeFailureUnchanged()
            throws Exception {
        Path primary = temporaryFolder.getRoot().toPath().resolve("primary.pdf");
        createTaggedDocument(primary, "primary");
        RuntimeException expected = new IllegalStateException(
                "project-authored additional stream failure");
        RuntimeFailingInputStream source = new RuntimeFailingInputStream(
                expected);
        WorkflowRequest request = WorkflowRequest.builder()
                .source("primary", DocumentSource.path(primary))
                .source("appendix", DocumentSource.stream(source, 1024L))
                .primarySource("primary")
                .saveMode(SaveMode.REWRITE)
                .build();

        try {
            new DocumentWorkflow().execute(request, session -> {
                session.execute(MergeDocuments.version1("appendix"));
                return null;
            });
            fail("Expected the caller stream runtime failure");
        } catch (DocumentFailure failure) {
            fail("Caller programming errors must remain unchecked");
        } catch (RuntimeException actual) {
            assertSame(expected, actual);
        }
        assertFalse(source.closeCalled);
    }

    @Test
    public void mergePropagatesAdditionalCallerChannelRuntimeFailureUnchanged()
            throws Exception {
        Path primary = temporaryFolder.getRoot().toPath().resolve("primary.pdf");
        createTaggedDocument(primary, "primary");
        RuntimeException expected = new IllegalStateException(
                "project-authored additional channel failure");
        RuntimeFailingChannel source = new RuntimeFailingChannel(expected);
        WorkflowRequest request = WorkflowRequest.builder()
                .source("primary", DocumentSource.path(primary))
                .source("appendix", DocumentSource.channel(source, 1024L))
                .primarySource("primary")
                .saveMode(SaveMode.REWRITE)
                .build();

        try {
            new DocumentWorkflow().execute(request, session -> {
                session.execute(MergeDocuments.version1("appendix"));
                return null;
            });
            fail("Expected the caller channel runtime failure");
        } catch (DocumentFailure failure) {
            fail("Caller programming errors must remain unchecked");
        } catch (RuntimeException actual) {
            assertSame(expected, actual);
        }
        assertTrue(source.isOpen());
    }

    @Test
    public void splitPublishesNamedRangesThatReopenIndependently()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path first = temporaryFolder.getRoot().toPath().resolve("first.pdf");
        Path middle = temporaryFolder.getRoot().toPath().resolve("middle.pdf");
        Path last = temporaryFolder.getRoot().toPath().resolve("last.pdf");
        createTaggedDocument(input, "alpha", "bravo", "charlie", "delta");

        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.path(input))
                .primarySource("input")
                .target("first", PublicationTarget.path(first))
                .target("middle", PublicationTarget.path(middle))
                .target("last", PublicationTarget.path(last))
                .saveMode(SaveMode.REWRITE)
                .build();
        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(
                request,
                session -> {
                    session.execute(SplitDocument.version1()
                            .target("first", PageRange.of(1, 1))
                            .target("middle", PageRange.of(2, 3))
                            .target("last", PageRange.of(4, 4))
                            .build());
                    return null;
                });

        assertEquals(3, outcome.getPublicationReceipts().size());
        for (int index = 0; index < 3; index++) {
            assertEquals(
                    PublicationStatus.COMMITTED,
                    outcome.getPublicationReceipts().get(index).getStatus());
        }
        assertEquals("first", outcome.getPublicationReceipts().get(0).getTargetName());
        assertEquals("middle", outcome.getPublicationReceipts().get(1).getTargetName());
        assertEquals("last", outcome.getPublicationReceipts().get(2).getTargetName());
        assertEquals(
                Arrays.<PdfValue>asList(PdfName.of("alpha")),
                readPageMarkers(first));
        assertEquals(
                Arrays.<PdfValue>asList(
                        PdfName.of("bravo"),
                        PdfName.of("charlie")),
                readPageMarkers(middle));
        assertEquals(
                Arrays.<PdfValue>asList(PdfName.of("delta")),
                readPageMarkers(last));
    }

    @Test
    public void successfulSplitRejectsEveryLaterDocumentCommand()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "terminal-split-input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "terminal-split-output.pdf");
        createTaggedDocument(input, "alpha");
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.path(input))
                .primarySource("input")
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE)
                .build();

        new DocumentWorkflow().execute(request, session -> {
            session.execute(SplitDocument.version1()
                    .target("output", PageRange.of(1, 1))
                    .build());
            try {
                session.execute(AddBlankPage.INSTANCE);
                fail("Expected a successful split to be terminal");
            } catch (DocumentFailure failure) {
                assertEquals(
                        DocumentFailureCode.COMMAND_REJECTED,
                        failure.getCode());
                assertEquals(
                        "document.page.manipulate-merge-split",
                        failure.getCapabilityId());
                assertEquals(
                        "A successful split must be the final Document Command in its workflow.",
                        failure.getDiagnostic());
                assertNull(failure.getCause());
            }
            assertEquals(Integer.valueOf(1), session.query(PageCount.INSTANCE));
            return null;
        });

        assertEquals(
                Arrays.<PdfValue>asList(PdfName.of("alpha")),
                readPageMarkers(output));
    }

    @Test
    public void splitFailureIdentifiesCommittedFailedAndUnattemptedProducts()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path committed = temporaryFolder.getRoot().toPath()
                .resolve("committed.pdf");
        Path untouched = temporaryFolder.getRoot().toPath()
                .resolve("untouched.pdf");
        byte[] existing = new byte[] {41, 42, 43};
        Files.write(untouched, existing);
        FailingOutputStream failing = new FailingOutputStream();
        createTaggedDocument(input, "alpha", "bravo", "charlie");

        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.path(input))
                .primarySource("input")
                .target("committed", PublicationTarget.path(committed))
                .target("failed", PublicationTarget.stream(failing))
                .target("unattempted", PublicationTarget.path(untouched))
                .saveMode(SaveMode.REWRITE)
                .build();
        try {
            new DocumentWorkflow().execute(request, session -> {
                session.execute(SplitDocument.version1()
                        .target("committed", PageRange.of(1, 1))
                        .target("failed", PageRange.of(2, 2))
                        .target("unattempted", PageRange.of(3, 3))
                        .build());
                return null;
            });
            fail("Expected split publication to fail");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.PUBLICATION_FAILED,
                    failure.getCode());
            assertEquals(3, failure.getPublicationReceipts().size());
            assertEquals(
                    PublicationStatus.COMMITTED,
                    failure.getPublicationReceipts().get(0).getStatus());
            assertEquals(
                    PublicationStatus.FAILED,
                    failure.getPublicationReceipts().get(1).getStatus());
            assertTrue(failure.getPublicationReceipts().get(1)
                    .isPartialOutputPossible());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(2).getStatus());
        }

        assertEquals(
                Arrays.<PdfValue>asList(PdfName.of("alpha")),
                readPageMarkers(committed));
        assertTrue(failing.size() > 0);
        assertFalse(failing.closeCalled);
        assertArrayEquals(existing, Files.readAllBytes(untouched));
    }

    @Test
    public void splitProductsPreserveSemanticsAndRemainRewriteIsolated()
            throws Exception {
        byte[] source = inheritedPageFixture();
        Path first = temporaryFolder.getRoot().toPath().resolve("rich-first.pdf");
        Path complete = temporaryFolder.getRoot().toPath()
                .resolve("rich-complete.pdf");
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.bytes(source, source.length))
                .primarySource("input")
                .target("first", PublicationTarget.path(first))
                .target("complete", PublicationTarget.path(complete))
                .saveMode(SaveMode.REWRITE)
                .build();

        new DocumentWorkflow().execute(request, session -> {
            session.execute(SplitDocument.version1()
                    .target("first", PageRange.of(1, 1))
                    .target("complete", PageRange.of(1, 2))
                    .build());
            return null;
        });

        List<Object> expected = Arrays.<Object>asList(
                PdfName.of("alpha"),
                Arrays.asList(
                        PdfNumber.of(0L),
                        PdfNumber.of(0L),
                        PdfNumber.of(300L),
                        PdfNumber.of(400L)),
                Arrays.asList(
                        PdfNumber.of(10L),
                        PdfNumber.of(20L),
                        PdfNumber.of(290L),
                        PdfNumber.of(380L)),
                PdfNumber.of(90L),
                PdfName.of("nested"),
                PdfString.of("note-alpha".getBytes(
                        StandardCharsets.ISO_8859_1)),
                "q\nQ\n",
                Boolean.TRUE);
        assertEquals(expected, readPageSemantics(first, 1));
        assertEquals(expected, readPageSemantics(complete, 1));

        rewriteContentMarker(first, 1, PdfName.of("first-product-only"));
        assertEquals(
                PdfName.of("first-product-only"),
                readContentMarker(DocumentSource.path(first), 1));
        assertNull(readContentMarker(DocumentSource.path(complete), 1));
        assertNull(readContentMarker(
                DocumentSource.bytes(source, source.length),
                1));
    }

    @Test
    public void splitRejectsTargetMappingsThatDoNotMatchTheRequest()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        createTaggedDocument(input, "alpha", "bravo");

        assertSplitTargetsRejected(
                input,
                temporaryFolder.getRoot().toPath().resolve("missing-first.pdf"),
                temporaryFolder.getRoot().toPath().resolve("missing-last.pdf"),
                SplitDocument.version1()
                        .target("first", PageRange.of(1, 1))
                        .build());
        assertSplitTargetsRejected(
                input,
                temporaryFolder.getRoot().toPath().resolve("unknown-first.pdf"),
                temporaryFolder.getRoot().toPath().resolve("unknown-last.pdf"),
                SplitDocument.version1()
                        .target("first", PageRange.of(1, 1))
                        .target("unknown", PageRange.of(2, 2))
                        .build());
        assertSplitTargetsRejected(
                input,
                temporaryFolder.getRoot().toPath().resolve("empty-first.pdf"),
                temporaryFolder.getRoot().toPath().resolve("empty-last.pdf"),
                SplitDocument.version1().build());
        assertSplitTargetsRejected(
                input,
                temporaryFolder.getRoot().toPath().resolve("duplicate-first.pdf"),
                temporaryFolder.getRoot().toPath().resolve("duplicate-last.pdf"),
                SplitDocument.version1()
                        .target("first", PageRange.of(1, 1))
                        .target("first", PageRange.of(2, 2))
                        .target("last", PageRange.of(2, 2))
                        .build());
    }

    private static void createTaggedDocument(Path target, String... markers)
            throws Exception {
        WorkflowRequest creation = WorkflowRequest.builder()
                .target("output", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(creation, session -> {
            for (int index = 0; index < markers.length; index++) {
                session.execute(AddBlankPage.INSTANCE);
                ObjectReference page = session.query(
                        PageObjectReference.version1(index + 1));
                session.execute(DocumentPatch.builder()
                        .setDictionaryEntry(
                                page,
                                PAGE_MARKER,
                                PdfName.of(markers[index]))
                        .build());
            }
            return null;
        });
    }

    private void assertUnsupportedStructureRejected(
            String name,
            boolean catalogEntry,
            PdfName entryName,
            PdfValue entryValue) throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "unsupported-" + name + ".pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "unsupported-" + name + "-unchanged.pdf");
        createTaggedDocument(input, "alpha", "bravo");

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    ObjectReference target = catalogEntry
                            ? session.query(DocumentRootReference.INSTANCE)
                            : session.query(PageObjectReference.version1(1));
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(target, entryName, entryValue)
                            .build());
                    assertPreservationRejected(() -> session.execute(
                            RemovePages.version1(PageRange.of(1, 1))));
                    return null;
                });

        assertEquals(
                Arrays.<PdfValue>asList(
                        PdfName.of("alpha"),
                        PdfName.of("bravo")),
                readPageMarkers(output));
    }

    private void assertFixtureSplitPreservationRejected(
            byte[] source,
            String outputName) throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve(outputName);
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.bytes(source, source.length))
                .primarySource("input")
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE)
                .build();

        new DocumentWorkflow().execute(request, session -> {
            assertPreservationRejected(() -> session.execute(
                    SplitDocument.version1()
                            .target("output", PageRange.of(1, 1))
                            .build()));
            return null;
        });

        assertEquals(
                Arrays.<PdfValue>asList(
                        PdfName.of("alpha"),
                        PdfName.of("bravo")),
                readPageMarkers(output));
    }

    private static void assertFixtureCopyPreservationRejected(byte[] source)
            throws Exception {
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.bytes(source, source.length))
                .primarySource("input")
                .saveMode(SaveMode.REWRITE)
                .build();

        new DocumentWorkflow().execute(request, session -> {
            assertPreservationRejected(() -> session.execute(
                    CopyPages.version1(PageRange.of(1, 1), 2)));
            return null;
        });
    }

    private static void assertMovePreservationRejected(byte[] source)
            throws Exception {
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.bytes(source, source.length))
                .primarySource("input")
                .saveMode(SaveMode.REWRITE)
                .build();

        new DocumentWorkflow().execute(request, session -> {
            assertPreservationRejected(() -> session.execute(
                    MovePages.version1(PageRange.of(1, 1), 2)));
            assertEquals(Integer.valueOf(2), session.query(PageCount.INSTANCE));
            assertEquals(
                    PdfName.of("alpha"),
                    inspectDictionary(
                            session,
                            session.query(PageObjectReference.version1(1)))
                            .get(PAGE_MARKER));
            return null;
        });
    }

    private static void assertFixturePageTreePreservationRejected(
            byte[] source) throws Exception {
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.bytes(source, source.length))
                .primarySource("input")
                .saveMode(SaveMode.REWRITE)
                .build();

        new DocumentWorkflow().execute(request, session -> {
            assertPreservationRejected(() -> session.execute(
                    RemovePages.version1(PageRange.of(1, 1))));
            return null;
        });
    }

    private static void assertFixturePageQueryRejected(byte[] source)
            throws Exception {
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.bytes(source, source.length))
                .primarySource("input")
                .saveMode(SaveMode.REWRITE)
                .build();

        new DocumentWorkflow().execute(request, session -> {
            try {
                session.query(PageObjectReference.version1(1));
                fail("Expected inconsistent page-tree query rejection.");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.QUERY_FAILED, failure.getCode());
            }
            assertPreservationRejected(() -> session.execute(
                    InsertBlankPage.version1(1)));
            return null;
        });
    }

    private static void assertPreservationRejected(FailingAction action)
            throws DocumentFailure {
        try {
            action.run();
            fail("Expected preservation-sensitive page content to be rejected");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.PRESERVATION_UNSUPPORTED,
                    failure.getCode());
            assertEquals(
                    "document.page.manipulate-merge-split",
                    failure.getCapabilityId());
            assertEquals(
                    "The document contains structures that this page operation cannot preserve safely.",
                    failure.getDiagnostic());
            assertNull(failure.getCause());
        }
    }

    private static void assertRejectedCommandLeavesMarkers(
            Path input,
            Path output,
            DocumentCommand command,
            DocumentFailureCode expectedCode,
            String expectedDiagnostic) throws Exception {
        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    try {
                        session.execute(command);
                        fail("Expected the page command to be rejected");
                    } catch (DocumentFailure failure) {
                        assertEquals(expectedCode, failure.getCode());
                        assertEquals(
                                "document.page.manipulate-merge-split",
                                failure.getCapabilityId());
                        assertEquals(expectedDiagnostic, failure.getDiagnostic());
                        assertNull(failure.getCause());
                    }
                    return null;
                });
        assertEquals(
                Arrays.<PdfValue>asList(
                        PdfName.of("alpha"),
                        PdfName.of("bravo")),
                readPageMarkers(output));
    }

    private static void assertMergeSelectionRejected(
            Path primary,
            Path appendix,
            Path output,
            MergeDocuments command) throws Exception {
        WorkflowRequest request = WorkflowRequest.builder()
                .source("primary", DocumentSource.path(primary))
                .source("appendix", DocumentSource.path(appendix))
                .primarySource("primary")
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(request, session -> {
            try {
                session.execute(command);
                fail("Expected the merge Source selection to be rejected");
            } catch (DocumentFailure failure) {
                assertEquals(
                        DocumentFailureCode.MERGE_SOURCE_INVALID,
                        failure.getCode());
                assertEquals(
                        "document.page.manipulate-merge-split",
                        failure.getCapabilityId());
                assertEquals(
                        "The merge command must name unique declared non-primary Sources.",
                        failure.getDiagnostic());
                assertNull(failure.getCause());
            }
            return null;
        });
        assertEquals(
                Arrays.<PdfValue>asList(PdfName.of("primary")),
                readPageMarkers(output));
    }

    private static void assertSplitTargetsRejected(
            Path input,
            Path first,
            Path last,
            SplitDocument command) throws Exception {
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.path(input))
                .primarySource("input")
                .target("first", PublicationTarget.path(first))
                .target("last", PublicationTarget.path(last))
                .saveMode(SaveMode.REWRITE)
                .build();
        try {
            new DocumentWorkflow().execute(request, session -> {
                session.execute(command);
                return null;
            });
            fail("Expected the split Target mapping to be rejected");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.SPLIT_TARGET_INVALID,
                    failure.getCode());
            assertEquals(
                    "document.page.manipulate-merge-split",
                    failure.getCapabilityId());
            assertEquals(
                    "The split command must define every publication Target once.",
                    failure.getDiagnostic());
            assertEquals(2, failure.getPublicationReceipts().size());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(1).getStatus());
            assertNull(failure.getCause());
        }
        assertFalse(Files.exists(first));
        assertFalse(Files.exists(last));
    }

    private static WorkflowRequest rewriteRequest(Path input, Path output) {
        return WorkflowRequest.builder()
                .source("input", DocumentSource.path(input))
                .primarySource("input")
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE)
                .build();
    }

    private static List<PdfValue> readPageMarkers(Path source)
            throws Exception {
        WorkflowRequest reopening = WorkflowRequest.builder()
                .source("input", DocumentSource.path(source))
                .primarySource("input")
                .saveMode(SaveMode.REWRITE)
                .build();
        return new DocumentWorkflow().execute(reopening, session -> {
            int pageCount = session.query(PageCount.INSTANCE);
            List<PdfValue> markers = new ArrayList<PdfValue>(pageCount);
            for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
                ObjectReference page = session.query(
                        PageObjectReference.version1(pageNumber));
                PdfDictionary dictionary = (PdfDictionary) session.query(
                        InspectObject.version1(
                                page,
                                PdfInspectionLimits.of(8, 0L)));
                markers.add(dictionary.get(PAGE_MARKER));
            }
            return markers;
        }).getResult();
    }

    private static List<Object> readPageSemantics(Path source, int pageNumber)
            throws Exception {
        WorkflowRequest reopening = WorkflowRequest.builder()
                .source("input", DocumentSource.path(source))
                .primarySource("input")
                .saveMode(SaveMode.REWRITE)
                .build();
        return new DocumentWorkflow().execute(reopening, session -> {
            ObjectReference pageReference = session.query(
                    PageObjectReference.version1(pageNumber));
            PdfDictionary page = inspectDictionary(session, pageReference);
            PdfValue mediaBox = inheritable(session, page, PdfName.of("MediaBox"));
            PdfValue cropBox = inheritable(session, page, PdfName.of("CropBox"));
            PdfValue rotation = inheritable(session, page, PdfName.of("Rotate"));
            PdfDictionary resources = dictionaryValue(
                    session,
                    inheritable(session, page, PdfName.of("Resources")));

            PdfArray annotations = (PdfArray) page.get(PdfName.of("Annots"));
            PdfDictionary annotation = dictionaryValue(session, annotations.get(0));
            PdfString annotationContents = (PdfString) annotation.get(
                    PdfName.of("Contents"));
            ObjectReference annotationPage = ((PdfIndirectReference) annotation.get(
                    PdfName.of("P"))).getReference();
            PdfStream contents = streamValue(
                    session,
                    page.get(PdfName.of("Contents")));

            return Arrays.<Object>asList(
                    page.get(PAGE_MARKER),
                    detachedArray((PdfArray) mediaBox),
                    detachedArray((PdfArray) cropBox),
                    rotation,
                    resources.get(PdfName.of("T10Resource")),
                    annotationContents,
                    new String(contents.readBytes(), StandardCharsets.ISO_8859_1),
                    Boolean.valueOf(pageReference.equals(annotationPage)));
        }).getResult();
    }

    private static void rewriteContentMarker(
            Path document,
            int pageNumber,
            PdfName marker) throws Exception {
        new DocumentWorkflow().execute(
                rewriteRequest(document, document),
                session -> {
                    PdfDictionary page = inspectDictionary(
                            session,
                            session.query(PageObjectReference.version1(
                                    pageNumber)));
                    ObjectReference contents = ((PdfIndirectReference) page.get(
                            PdfName.of("Contents"))).getReference();
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(
                                    contents,
                                    PdfName.of("T10GraphMarker"),
                                    marker)
                            .build());
                    return null;
                });
    }

    private static PdfValue readContentMarker(
            DocumentSource source,
            int pageNumber) throws Exception {
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", source)
                .primarySource("input")
                .saveMode(SaveMode.REWRITE)
                .build();
        return new DocumentWorkflow().execute(request, session -> {
            PdfDictionary page = inspectDictionary(
                    session,
                    session.query(PageObjectReference.version1(pageNumber)));
            ObjectReference contents = ((PdfIndirectReference) page.get(
                    PdfName.of("Contents"))).getReference();
            PdfStream stream = (PdfStream) session.query(InspectObject.version1(
                    contents,
                    PdfInspectionLimits.of(8, 16L)));
            return stream.getDictionary().get(PdfName.of("T10GraphMarker"));
        }).getResult();
    }

    private static byte[] readPageContent(
            DocumentSource source,
            int pageNumber) throws Exception {
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", source)
                .primarySource("input")
                .saveMode(SaveMode.REWRITE)
                .build();
        return new DocumentWorkflow().execute(request, session -> {
            PdfDictionary page = inspectDictionary(
                    session,
                    session.query(PageObjectReference.version1(pageNumber)));
            return streamValue(
                    session,
                    page.get(PdfName.of("Contents"))).readBytes();
        }).getResult();
    }

    private static PdfValue inheritable(
            DocumentSession session,
            PdfDictionary dictionary,
            PdfName name) throws DocumentFailure {
        PdfDictionary current = dictionary;
        while (current != null) {
            PdfValue value = current.get(name);
            if (value != null && value.getKind() != PdfValueKind.NULL) {
                return value;
            }
            PdfValue parent = current.get(PARENT);
            current = parent == null ? null : dictionaryValue(session, parent);
        }
        return null;
    }

    private static PdfDictionary dictionaryValue(
            DocumentSession session,
            PdfValue value) throws DocumentFailure {
        if (value instanceof PdfIndirectReference) {
            return inspectDictionary(
                    session,
                    ((PdfIndirectReference) value).getReference());
        }
        return (PdfDictionary) value;
    }

    private static PdfStream streamValue(
            DocumentSession session,
            PdfValue value) throws DocumentFailure {
        if (value instanceof PdfIndirectReference) {
            return (PdfStream) session.query(InspectObject.version1(
                    ((PdfIndirectReference) value).getReference(),
                    PdfInspectionLimits.of(16, 64L)));
        }
        return (PdfStream) value;
    }

    private static PdfDictionary inspectDictionary(
            DocumentSession session,
            ObjectReference reference) throws DocumentFailure {
        return (PdfDictionary) session.query(InspectObject.version1(
                reference,
                PdfInspectionLimits.of(64, 64L)));
    }

    private static List<PdfValue> detachedArray(
            PdfArray array) throws DocumentFailure {
        List<PdfValue> values = new ArrayList<PdfValue>(array.size());
        for (int index = 0; index < array.size(); index++) {
            values.add(array.get(index));
        }
        return values;
    }

    private static byte[] inheritedPageFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R 5 0 R] /Count 2 "
                        + "/MediaBox [0 0 612 792] "
                        + "/Resources << /T10Resource /root >> >>",
                "<< /Type /Pages /Parent 2 0 R /Kids [4 0 R] /Count 1 "
                        + "/MediaBox [0 0 300 400] "
                        + "/CropBox [10 20 290 380] /Rotate 90 "
                        + "/Resources << /T10Resource /nested >> >>",
                "<< /Type /Page /Parent 3 0 R /T10Marker /alpha "
                        + "/Annots [6 0 R] /Contents 7 0 R >>",
                "<< /Type /Page /Parent 2 0 R /T10Marker /bravo "
                        + "/Contents 8 0 R >>",
                "<< /Type /Annot /Subtype /Text /Rect [1 2 20 30] "
                        + "/Contents (note-alpha) /P 4 0 R >>",
                "<< /Length 4 >>\nstream\nq\nQ\nendstream",
                "<< /Length 4 >>\nstream\nq\nQ\nendstream");
        return pdfFixture(objects, "");
    }

    private static byte[] duplicateIndirectScalarFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /First 4 0 R /Second 5 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                "/Same",
                "/Same");
        return pdfFixture(objects, "");
    }

    private static byte[] missingPageTypeFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Parent 2 0 R >>");
        return pdfFixture(objects, "");
    }

    private static byte[] directPageNodeFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids ["
                        + "<< /Type /Page /Parent 2 0 R >>"
                        + "] /Count 1 /MediaBox [0 0 612 792] >>");
        return pdfFixture(objects, "");
    }

    private static byte[] directIntermediatePageTreeNodeFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids ["
                        + "<< /Type /Pages /Parent 2 0 R /Kids [] /Count 0 >> "
                        + "3 0 R] /Count 1 /MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>");
        return pdfFixture(objects, "");
    }

    private static byte[] malformedInheritedAttributeFixture(
            String inheritedAttribute) {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 "
                        + inheritedAttribute + " >>",
                "<< /Type /Page /Parent 2 0 R /T10Marker /alpha >>",
                "<< /Type /Page /Parent 2 0 R /T10Marker /bravo >>");
        return pdfFixture(objects, "");
    }

    private static byte[] nestedContentArrayFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R /Contents [[4 0 R]] >>",
                "<< /Length 4 >>\nstream\nq\nQ\nendstream");
        return pdfFixture(objects, "");
    }

    private static byte[] externalContentStreamFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R /Contents 4 0 R >>",
                "<< /Length 4 /F (external-content.bin) >>\n"
                        + "stream\nq\nQ\nendstream");
        return pdfFixture(objects, "");
    }

    private static byte[] undecodableContentStreamFixture() {
        return contentStreamFilterFixture(
                "/Filter /Unknown",
                "q\nQ\n");
    }

    private static byte[] contentStreamFilterFixture(
            String filterEntries,
            String encodedContent) {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R /T10Marker /alpha "
                        + "/Contents 5 0 R >>",
                "<< /Type /Page /Parent 2 0 R /T10Marker /bravo >>",
                "<< /Length "
                        + encodedContent.getBytes(
                                StandardCharsets.ISO_8859_1).length
                        + " " + filterEntries + " >>\n"
                        + "stream\n" + encodedContent + "\nendstream");
        return pdfFixture(objects, "");
    }

    private static String deflate(String value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DeflaterOutputStream compressed =
                new DeflaterOutputStream(bytes)) {
            compressed.write(value.getBytes(StandardCharsets.ISO_8859_1));
        }
        return new String(
                bytes.toByteArray(),
                StandardCharsets.ISO_8859_1);
    }

    private static String deflateWithPresetDictionary(String value) {
        byte[] input = value.getBytes(StandardCharsets.ISO_8859_1);
        byte[] dictionary = "t10-dictionary".getBytes(
                StandardCharsets.ISO_8859_1);
        Deflater compressor = new Deflater();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[128];
        try {
            compressor.setDictionary(dictionary);
            compressor.setInput(input);
            compressor.finish();
            while (!compressor.finished()) {
                int count = compressor.deflate(buffer);
                bytes.write(buffer, 0, count);
            }
        } finally {
            compressor.end();
        }
        return new String(
                bytes.toByteArray(),
                StandardCharsets.ISO_8859_1);
    }

    private static byte[] mismatchedPageParentFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R 5 0 R] /Count 2 >>",
                "<< /Type /Pages /Parent 2 0 R /Kids [4 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 5 0 R >>",
                "<< /Type /Pages /Parent 2 0 R /Kids [6 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 5 0 R >>");
        return pdfFixture(objects, "");
    }

    private static byte[] mismatchedPageCountFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 2 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>");
        return pdfFixture(objects, "");
    }

    private static byte[] repeatedPageNodeFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R 3 0 R] /Count 2 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>");
        return pdfFixture(objects, "");
    }

    private static byte[] crossPageSeparationFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R /T10Marker /alpha "
                        + "/SeparationInfo 5 0 R >>",
                "<< /Type /Page /Parent 2 0 R /T10Marker /bravo >>",
                "<< /Pages [3 0 R 4 0 R] /DeviceColorant /Cyan >>");
        return pdfFixture(objects, "");
    }

    private static byte[] crossPageResourceReferenceFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R /T10Marker /alpha "
                        + "/Resources << /Leak 4 0 R >> >>",
                "<< /Type /Page /Parent 2 0 R /T10Marker /bravo "
                        + "/T10Secret /secret >>");
        return pdfFixture(objects, "");
    }

    private static byte[] annotationReplyFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R /T10Marker /alpha "
                        + "/Annots [5 0 R 6 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /T10Marker /bravo >>",
                "<< /Type /Annot /Subtype /Text /Rect [1 2 20 30] "
                        + "/Contents (first) /P 3 0 R >>",
                "<< /Type /Annot /Subtype /Text /Rect [1 2 20 30] "
                        + "/Contents (reply) /P 3 0 R /IRT 5 0 R >>");
        return pdfFixture(objects, "");
    }

    private static byte[] documentInformationFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                "<< /Author (Source) /T11Note (from source) >>");
        return pdfFixture(objects, " /Info 4 0 R");
    }

    private static byte[] primaryDocumentInformationFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                "<< /Author (Primary) /T11Shared (primary wins) >>");
        return pdfFixture(objects, " /Info 4 0 R");
    }

    private static byte[] simpleDocumentFixture(String trailerEntries) {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>");
        return pdfFixture(objects, trailerEntries);
    }

    private static byte[] pdfFixture(
            List<String> objects,
            String trailerEntries) {
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        writeAscii(pdf, "%PDF-1.4\n");
        int[] offsets = new int[objects.size() + 1];
        for (int index = 0; index < objects.size(); index++) {
            offsets[index + 1] = pdf.size();
            writeAscii(pdf, Integer.toString(index + 1));
            writeAscii(pdf, " 0 obj\n");
            writeAscii(pdf, objects.get(index));
            writeAscii(pdf, "\nendobj\n");
        }
        int xrefOffset = pdf.size();
        writeAscii(pdf, "xref\n0 ");
        writeAscii(pdf, Integer.toString(offsets.length));
        writeAscii(pdf, "\n0000000000 65535 f \n");
        for (int index = 1; index < offsets.length; index++) {
            writeAscii(pdf, String.format(
                    Locale.ROOT,
                    "%010d 00000 n \n",
                    Integer.valueOf(offsets[index])));
        }
        writeAscii(pdf, "trailer\n<< /Size ");
        writeAscii(pdf, Integer.toString(offsets.length));
        writeAscii(pdf, " /Root 1 0 R");
        writeAscii(pdf, trailerEntries);
        writeAscii(pdf, " >>\nstartxref\n");
        writeAscii(pdf, Integer.toString(xrefOffset));
        writeAscii(pdf, "\n%%EOF\n");
        return pdf.toByteArray();
    }

    private static void writeAscii(
            ByteArrayOutputStream output,
            String value) {
        byte[] bytes = value.getBytes(StandardCharsets.ISO_8859_1);
        output.write(bytes, 0, bytes.length);
    }

    private static final class FailingOutputStream extends OutputStream {

        private final ByteArrayOutputStream written =
                new ByteArrayOutputStream();
        private boolean closeCalled;

        @Override
        public void write(int value) throws IOException {
            written.write(value);
            throw new IOException("project-authored split target failure");
        }

        @Override
        public void write(byte[] bytes, int offset, int length)
                throws IOException {
            if (length > 0) {
                written.write(bytes[offset]);
            }
            throw new IOException("project-authored split target failure");
        }

        @Override
        public void close() {
            closeCalled = true;
        }

        private int size() {
            return written.size();
        }
    }

    private interface FailingAction {

        void run() throws DocumentFailure;
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {

        private boolean closeCalled;

        private TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closeCalled = true;
            super.close();
        }
    }

    private static final class RuntimeFailingInputStream extends InputStream {

        private final RuntimeException failure;
        private boolean closeCalled;

        private RuntimeFailingInputStream(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public int read() {
            throw failure;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            throw failure;
        }

        @Override
        public void close() {
            closeCalled = true;
        }
    }

    private static final class RuntimeFailingChannel
            implements ReadableByteChannel {

        private final RuntimeException failure;
        private boolean open = true;

        private RuntimeFailingChannel(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public int read(ByteBuffer destination) {
            throw failure;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }
}
