package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Arrays;
import java.util.Collection;
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
import net.zerocloud.pdf.PdfStreamEncoding;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.PdfValueKind;
import net.zerocloud.pdf.PdfValuePath;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowResourcePolicy;
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.HardenedWorkerSettings;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageCount;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public final class PdfValueWorkflowTest {

    private static final PdfName TEST_VALUE = PdfName.of("T09Value");
    private final WorkflowExecutionProfile executionProfile;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> profiles() {
        String selected = System.getProperty("folio.t09.executionProfile");
        if (selected != null) {
            return java.util.Collections.singletonList(new Object[] {WorkflowExecutionProfile.valueOf(selected)});
        }
        return Arrays.asList(new Object[][] {
            {WorkflowExecutionProfile.IN_PROCESS},
            {WorkflowExecutionProfile.HARDENED_WORKER}
        });
    }

    public PdfValueWorkflowTest(WorkflowExecutionProfile executionProfile) {
        this.executionProfile = executionProfile;
    }

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void dictionaryRemovalIsOrderedAndSurvivesPublication()
            throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("removed.pdf");
        new DocumentWorkflow().execute(requestBuilder()
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE).build(),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(root, TEST_VALUE, PdfNumber.of(1L))
                            .removeDictionaryEntry(root, TEST_VALUE)
                            .removeDictionaryEntry(root, PdfName.of("Absent"))
                            .setDictionaryEntry(root, PdfName.of("Kept"), PdfBoolean.of(true))
                            .build());
                    assertNull(inspectTestValue(session));
                    return null;
                });
        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            PdfDictionary catalog = inspectDictionary(
                    session, session.query(DocumentRootReference.INSTANCE));
            assertNull(catalog.get(TEST_VALUE));
            assertEquals(PdfBoolean.of(true), catalog.get(PdfName.of("Kept")));
            return null;
        });
    }

    @Test
    public void nestedDictionaryPatchResolvesEarlierChangesInDeclarationOrder()
            throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("nested.pdf");
        new DocumentWorkflow().execute(requestBuilder()
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE).build(), session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(root, TEST_VALUE, PdfArray.of(
                                    PdfDictionary.builder()
                                            .put(PdfName.of("Old"), PdfNumber.of(1L)).build()))
                            .setDictionaryEntry(PdfValuePath.root(root)
                                            .dictionaryEntry(TEST_VALUE).arrayElement(0),
                                    PdfName.of("New"), PdfString.of(new byte[] {65, 66}))
                            .build());
                    PdfDictionary nested = (PdfDictionary) ((PdfArray)
                            inspectTestValue(session)).get(0);
                    assertEquals(PdfNumber.of(1L), nested.get(PdfName.of("Old")));
                    assertEquals(PdfString.of(new byte[] {65, 66}), nested.get(PdfName.of("New")));
                    return null;
                });
        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            PdfDictionary nested = (PdfDictionary) ((PdfArray) inspectTestValue(session)).get(0);
            assertEquals(PdfNumber.of(1L), nested.get(PdfName.of("Old")));
            assertEquals(PdfString.of(new byte[] {65, 66}), nested.get(PdfName.of("New")));
            return null;
        });
    }

    @Test
    public void invalidatedPathRejectsWholePatchAndAllowsPublication()
            throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("path-atomic.pdf");
        new DocumentWorkflow().execute(requestBuilder()
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE).build(), session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                    session.execute(DocumentPatch.builder().setDictionaryEntry(root, TEST_VALUE,
                            PdfDictionary.builder().put(PdfName.of("Old"), PdfNumber.of(1L)).build())
                            .build());
                    try {
                        session.execute(DocumentPatch.builder()
                                .removeDictionaryEntry(root, TEST_VALUE)
                                .setDictionaryEntry(PdfValuePath.root(root).dictionaryEntry(TEST_VALUE),
                                        PdfName.of("New"), PdfBoolean.of(true))
                                .build());
                        fail("An earlier removal must invalidate the later path");
                    } catch (DocumentFailure failure) {
                        assertSafePatchFailure(failure, DocumentFailureCode.COMMAND_REJECTED,
                                "The Document Patch target path is invalid.");
                    }
                    PdfDictionary kept = (PdfDictionary) inspectTestValue(session);
                    assertEquals(PdfNumber.of(1L), kept.get(PdfName.of("Old")));
                    assertNull(kept.get(PdfName.of("New")));
                    return null;
                });
        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            PdfDictionary kept = (PdfDictionary) inspectTestValue(session);
            assertEquals(PdfNumber.of(1L), kept.get(PdfName.of("Old")));
            assertNull(kept.get(PdfName.of("New")));
            return null;
        });
    }

    @Test
    public void nestedRemovalValidatesCyclesAgainstTheFinalPatchGraph()
            throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("final-graph.pdf");
        new DocumentWorkflow().execute(requestBuilder()
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE).build(), session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(root, TEST_VALUE, PdfDictionary.builder()
                                    .put(PdfName.of("TemporaryCycle"), PdfIndirectReference.of(root))
                                    .put(PdfName.of("Kept"), PdfNumber.of(2L)).build())
                            .removeDictionaryEntry(PdfValuePath.root(root).dictionaryEntry(TEST_VALUE),
                                    PdfName.of("TemporaryCycle"))
                            .build());
                    assertNull(((PdfDictionary) inspectTestValue(session))
                            .get(PdfName.of("TemporaryCycle")));
                    return null;
                });
        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            PdfDictionary kept = (PdfDictionary) inspectTestValue(session);
            assertNull(kept.get(PdfName.of("TemporaryCycle")));
            assertEquals(PdfNumber.of(2L), kept.get(PdfName.of("Kept")));
            return null;
        });
    }

    @Test
    public void arrayReplacementSupportsFollowingNestedChangesAndReopen()
            throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("array-set.pdf");
        new DocumentWorkflow().execute(requestBuilder()
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE).build(), session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                    PdfValuePath array = PdfValuePath.root(root).dictionaryEntry(TEST_VALUE);
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(root, TEST_VALUE,
                                    PdfArray.of(PdfNumber.of(1L), PdfNumber.of(2L)))
                            .setArrayElement(array, 1, PdfDictionary.builder()
                                    .put(PdfName.of("Kept"), PdfNumber.of(3L)).build())
                            .setDictionaryEntry(array.arrayElement(1), PdfName.of("Edited"),
                                    PdfBoolean.of(true))
                            .build());
                    assertEquals(PdfBoolean.of(true), ((PdfDictionary)
                            ((PdfArray) inspectTestValue(session)).get(1)).get(PdfName.of("Edited")));
                    return null;
                });
        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            PdfArray array = (PdfArray) inspectTestValue(session);
            assertEquals(2, array.size());
            assertEquals(PdfNumber.of(1L), array.get(0));
            PdfDictionary nested = (PdfDictionary) array.get(1);
            assertEquals(PdfNumber.of(3L), nested.get(PdfName.of("Kept")));
            assertEquals(PdfBoolean.of(true), nested.get(PdfName.of("Edited")));
            return null;
        });
    }

    @Test
    public void arrayInsertionUsesUpdatedPositionsAcrossOnePatchAndReopen()
            throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("array-insert.pdf");
        new DocumentWorkflow().execute(requestBuilder()
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE).build(), session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                    PdfValuePath array = PdfValuePath.root(root).dictionaryEntry(TEST_VALUE);
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(root, TEST_VALUE,
                                    PdfArray.of(PdfNumber.of(1L), PdfNumber.of(3L)))
                            .insertArrayElement(array, 1, PdfNumber.of(2L))
                            .insertArrayElement(array, 3, PdfNull.INSTANCE)
                            .setArrayElement(array, 0, PdfBoolean.of(false))
                            .build());
                    assertEquals(4, ((PdfArray) inspectTestValue(session)).size());
                    return null;
                });
        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            PdfArray array = (PdfArray) inspectTestValue(session);
            assertEquals(4, array.size());
            assertEquals(PdfBoolean.of(false), array.get(0));
            assertEquals(PdfNumber.of(2L), array.get(1));
            assertEquals(PdfNumber.of(3L), array.get(2));
            assertSame(PdfNull.INSTANCE, array.get(3));
            return null;
        });
    }

    @Test
    public void arrayRemovalAndInvalidLaterIndexPreserveAtomicityThroughReopen()
            throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("array-remove.pdf");
        new DocumentWorkflow().execute(requestBuilder()
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE).build(), session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                    PdfValuePath array = PdfValuePath.root(root).dictionaryEntry(TEST_VALUE);
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(root, TEST_VALUE, PdfArray.of(
                                    PdfNumber.of(1L), PdfNumber.of(2L), PdfNumber.of(3L)))
                            .removeArrayElement(array, 0)
                            .setArrayElement(array, 1, PdfNumber.of(4L))
                            .build());
                    try {
                        session.execute(DocumentPatch.builder()
                                .removeArrayElement(array, 0)
                                .removeArrayElement(array, 1)
                                .build());
                        fail("The second removal is beyond the updated array size");
                    } catch (DocumentFailure failure) {
                        assertSafePatchFailure(failure, DocumentFailureCode.COMMAND_REJECTED,
                                "The Document Patch target path is invalid.");
                    }
                    PdfArray kept = (PdfArray) inspectTestValue(session);
                    assertEquals(2, kept.size());
                    assertEquals(PdfNumber.of(2L), kept.get(0));
                    assertEquals(PdfNumber.of(4L), kept.get(1));
                    return null;
                });
        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            PdfArray kept = (PdfArray) inspectTestValue(session);
            assertEquals(2, kept.size());
            assertEquals(PdfNumber.of(2L), kept.get(0));
            assertEquals(PdfNumber.of(4L), kept.get(1));
            return null;
        });
    }

    @Test
    public void malformedCorePatchRollsBackBeforeCallerContinues()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("core-source.pdf");
        createBlankDocument(input);
        byte[] sourceBytes = Files.readAllBytes(input);
        byte[] painting = "q Q\n".getBytes(StandardCharsets.US_ASCII);
        PdfName contentsName = PdfName.of("Contents");
        PdfName resourcesName = PdfName.of("Resources");
        PdfName discarded = PdfName.of("T09Discarded");
        for (SaveMode mode : Arrays.asList(SaveMode.REWRITE, SaveMode.INCREMENTAL)) {
            Path output = temporaryFolder.getRoot().toPath().resolve("core-atomic-" + mode + ".pdf");
            WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(requestBuilder()
                .source("input", DocumentSource.path(input)).primarySource("input")
                .target("output", PublicationTarget.path(output))
                .saveMode(mode).build(), session -> {
                    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                    PdfName pagesName = PdfName.of("Pages");
                    ObjectReference pages = ((PdfIndirectReference)
                            inspectDictionary(session, root).get(pagesName)).getReference();
                    ObjectReference page = ((PdfIndirectReference) ((PdfArray)
                            inspectDictionary(session, pages).get(PdfName.of("Kids"))).get(0)).getReference();
                    session.execute(DocumentPatch.builder().setDictionaryEntry(page, contentsName,
                            PdfStream.of(PdfDictionary.builder().build(), painting)).build());
                    PdfValue originalContents = inspectDictionary(session, page).get(contentsName);
                    ObjectReference contentReference = streamReference(originalContents);
                    session.execute(DocumentPatch.builder().setDictionaryEntry(page, contentsName,
                            PdfArray.of(PdfIndirectReference.of(contentReference))).build());
                    PdfValuePath contentsPath = PdfValuePath.root(page).dictionaryEntry(contentsName);
                    for (DocumentPatch invalid : Arrays.asList(
                            DocumentPatch.builder().setDictionaryEntry(root, discarded, PdfBoolean.of(true))
                                    .removeDictionaryEntry(root, pagesName).build(),
                            DocumentPatch.builder().setDictionaryEntry(root, discarded, PdfBoolean.of(true))
                                    .setArrayElement(PdfValuePath.root(root)
                                    .dictionaryEntry(pagesName).dictionaryEntry(PdfName.of("Kids")),
                                    0, PdfNull.INSTANCE).build(),
                            DocumentPatch.builder().setDictionaryEntry(root, discarded, PdfBoolean.of(true))
                                    .setDictionaryEntry(page, contentsName, PdfNumber.of(9L)).build(),
                            DocumentPatch.builder().setDictionaryEntry(root, discarded, PdfBoolean.of(true))
                                    .setDictionaryEntry(page, contentsName, PdfDictionary.builder().build()).build(),
                            DocumentPatch.builder().setDictionaryEntry(root, discarded, PdfBoolean.of(true))
                                    .setDictionaryEntry(page, contentsName, PdfArray.of(PdfNumber.of(9L))).build(),
                            DocumentPatch.builder().setDictionaryEntry(root, discarded, PdfBoolean.of(true))
                                    .setArrayElement(contentsPath, 0, PdfNumber.of(9L)).build(),
                            DocumentPatch.builder().setDictionaryEntry(root, discarded, PdfBoolean.of(true))
                                    .insertArrayElement(contentsPath, 1, PdfNull.INSTANCE).build())) {
                        try {
                            session.execute(invalid);
                            fail("Malformed core structure must reject the entire Patch");
                        } catch (DocumentFailure failure) {
                            assertSafePatchFailure(failure, DocumentFailureCode.COMMAND_REJECTED,
                                    "The Document Patch would invalidate the document structure.");
                        }
                        assertEquals(pages, ((PdfIndirectReference)
                                inspectDictionary(session, root).get(pagesName)).getReference());
                        assertNull(inspectDictionary(session, root).get(discarded));
                        PdfArray contents = (PdfArray) inspectDictionary(session, page).get(contentsName);
                        assertEquals(1, contents.size());
                        assertEquals(contentReference, streamReference(contents.get(0)));
                        assertArrayEquals(painting, ((PdfStream) session.query(InspectObject.version1(
                                contentReference, PdfInspectionLimits.of(4, 32)))).readBytes());
                        assertTrue(inspectDictionary(session, page).get(resourcesName) != null);
                        assertEquals(Integer.valueOf(1), session.query(PageCount.INSTANCE));
                    }
                    session.execute(DocumentPatch.builder().setDictionaryEntry(root, TEST_VALUE, PdfNumber.of(7L)).build());
                    return null;
                });
            assertEquals(PublicationStatus.COMMITTED, outcome.getPublicationReceipts().get(0).getStatus());
            assertArrayEquals(sourceBytes, Files.readAllBytes(input));
            if (mode == SaveMode.INCREMENTAL) {
                assertArrayEquals(sourceBytes, Arrays.copyOf(Files.readAllBytes(output), sourceBytes.length));
            }
            new DocumentWorkflow().execute(sourceRequest(output), session -> {
                PdfDictionary catalog = inspectDictionary(session, session.query(DocumentRootReference.INSTANCE));
                assertEquals(PdfNumber.of(7L), catalog.get(TEST_VALUE));
                assertNull(catalog.get(discarded));
                ObjectReference pages = ((PdfIndirectReference) catalog.get(PdfName.of("Pages"))).getReference();
                PdfDictionary pagesDictionary = inspectDictionary(session, pages);
                ObjectReference page = ((PdfIndirectReference) ((PdfArray) pagesDictionary.get(PdfName.of("Kids")))
                        .get(0)).getReference();
                PdfDictionary pageDictionary = inspectDictionary(session, page);
                assertTrue(pageDictionary.get(resourcesName) != null);
                PdfArray contents = (PdfArray) pageDictionary.get(contentsName);
                assertEquals(1, contents.size());
                assertArrayEquals(painting, ((PdfStream) session.query(InspectObject.version1(
                        streamReference(contents.get(0)), PdfInspectionLimits.of(4, 32)))).readBytes());
                assertEquals(Integer.valueOf(1), session.query(PageCount.INSTANCE));
                return null;
            });
        }
    }

    private static ObjectReference streamReference(PdfValue value) {
        return value instanceof PdfIndirectReference
                ? ((PdfIndirectReference) value).getReference()
                : ((PdfStream) value).getReference().get();
    }

    @Test
    public void missingEffectivePageResourcesRollsBackBeforeCallerContinues() throws Exception {
        Path input = copyProtectionFixture();
        byte[] source = Files.readAllBytes(input);
        PdfName resourcesName = PdfName.of("Resources");
        PdfName discarded = PdfName.of("T09Discarded");
        for (SaveMode mode : Arrays.asList(SaveMode.REWRITE, SaveMode.INCREMENTAL)) {
            Path output = temporaryFolder.getRoot().toPath().resolve("effective-resources-" + mode + ".pdf");
            WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(requestBuilder()
                    .source("input", DocumentSource.path(input)).primarySource("input")
                    .target("output", PublicationTarget.path(output)).saveMode(mode).build(), session -> {
                ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                ObjectReference pages = ((PdfIndirectReference) inspectDictionary(session, root)
                        .get(PdfName.of("Pages"))).getReference();
                ObjectReference page = ((PdfIndirectReference) ((PdfArray) inspectDictionary(session, pages)
                        .get(PdfName.of("Kids"))).get(0)).getReference();
                for (ObjectReference owner : Arrays.asList(page, pages)) {
                    for (DocumentPatch invalid : Arrays.asList(
                            DocumentPatch.builder().setDictionaryEntry(root, discarded, PdfBoolean.of(true))
                                    .removeDictionaryEntry(owner, resourcesName).build(),
                            DocumentPatch.builder().setDictionaryEntry(root, discarded, PdfBoolean.of(true))
                                    .setDictionaryEntry(owner, resourcesName, PdfNull.INSTANCE).build())) {
                        try {
                            session.execute(invalid);
                            fail("A Page requires effective Resources, including through inheritance");
                        } catch (DocumentFailure failure) {
                            assertSafePatchFailure(failure, DocumentFailureCode.COMMAND_REJECTED,
                                    "The Document Patch would invalidate the document structure.");
                        }
                        assertNull(inspectDictionary(session, root).get(discarded));
                        assertEquals(0, ((PdfDictionary) inspectDictionary(session, owner).get(resourcesName)).size());
                        assertEquals(Integer.valueOf(1), session.query(PageCount.INSTANCE));
                    }
                    if (owner.equals(page)) {
                        session.execute(DocumentPatch.builder().setDictionaryEntry(pages, resourcesName,
                                PdfDictionary.builder().build()).removeDictionaryEntry(page, resourcesName).build());
                        assertNull(inspectDictionary(session, page).get(resourcesName));
                    }
                }
                session.execute(DocumentPatch.builder().setDictionaryEntry(root, TEST_VALUE, PdfNumber.of(7L)).build());
                return null;
            });
            assertEquals(PublicationStatus.COMMITTED, outcome.getPublicationReceipts().get(0).getStatus());
            assertArrayEquals(source, Files.readAllBytes(input));
            if (mode == SaveMode.INCREMENTAL) {
                assertArrayEquals(source, Arrays.copyOf(Files.readAllBytes(output), source.length));
            }
            new DocumentWorkflow().execute(sourceRequest(output), session -> {
                PdfDictionary root = inspectDictionary(session, session.query(DocumentRootReference.INSTANCE));
                assertNull(root.get(discarded));
                assertEquals(PdfNumber.of(7L), root.get(TEST_VALUE));
                ObjectReference pages = ((PdfIndirectReference) root.get(PdfName.of("Pages"))).getReference();
                assertEquals(0, ((PdfDictionary) inspectDictionary(session, pages).get(resourcesName)).size());
                ObjectReference page = ((PdfIndirectReference) ((PdfArray) inspectDictionary(session, pages)
                        .get(PdfName.of("Kids"))).get(0)).getReference();
                assertNull(inspectDictionary(session, page).get(resourcesName));
                assertEquals(Integer.valueOf(1), session.query(PageCount.INSTANCE));
                return null;
            });
        }
    }

    @Test
    public void nestedStreamMetadataAndAliasesStayProtected() throws Exception {
        Path input = copyProtectionFixture();
        Path output = temporaryFolder.getRoot().toPath().resolve("protected-stream.pdf");
        byte[] sourceBytes = Files.readAllBytes(input);
        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(requestBuilder()
                .source("input", DocumentSource.path(input)).primarySource("input")
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE).build(), session -> {
                    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                    PdfValuePath catalog = PdfValuePath.root(root);
                    PdfValuePath stream = catalog.dictionaryEntry(TEST_VALUE);
                    ObjectReference parameters = ((PdfIndirectReference)
                            inspectDictionary(session, root).get(PdfName.of("Parameters")))
                            .getReference();
                    for (DocumentPatch invalid : Arrays.asList(
                            DocumentPatch.builder().setArrayElement(
                                    stream.dictionaryEntry(PdfName.of("Filter")),
                                    0, PdfName.of("ASCII85Decode")).build(),
                            DocumentPatch.builder().setDictionaryEntry(
                                    stream.dictionaryEntry(PdfName.of("DecodeParms")).arrayElement(0),
                                    PdfName.of("Predictor"), PdfNumber.of(2L)).build(),
                            DocumentPatch.builder().setDictionaryEntry(parameters,
                                    PdfName.of("Predictor"), PdfNumber.of(2L)).build(),
                            DocumentPatch.builder().removeArrayElement(
                                    catalog.dictionaryEntry(PdfName.of("Encoding")), 0).build(),
                            DocumentPatch.builder().removeArrayElement(
                                    stream.dictionaryEntry(PdfName.of("DecodeParms")).arrayElement(0)
                                            .dictionaryEntry(PdfName.of("Nested")), 0).build())) {
                        try {
                            session.execute(invalid);
                            fail("Nested encoding metadata must remain engine-owned");
                        } catch (DocumentFailure failure) {
                            assertSafePatchFailure(failure,
                                    DocumentFailureCode.PATCH_STREAM_CHANGE_REJECTED,
                                    "The Document Patch cannot change engine-owned stream metadata.");
                        }
                        assertArrayEquals(new byte[] {1, 2, 3}, inspectTestStream(session).readBytes());
                    }
                    session.execute(DocumentPatch.builder().setDictionaryEntry(stream,
                            PdfName.of("Subtype"), PdfName.of("T09Preserved")).build());
                    return null;
                });
        assertEquals(PublicationStatus.COMMITTED, outcome.getPublicationReceipts().get(0).getStatus());
        assertArrayEquals(sourceBytes, Files.readAllBytes(input));
        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            PdfStream stream = inspectTestStream(session);
            assertArrayEquals(new byte[] {1, 2, 3}, stream.readBytes());
            assertEquals(PdfName.of("T09Preserved"), stream.getDictionary().get(PdfName.of("Subtype")));
            return null;
        });
    }

    @Test
    public void nestedVersionArraysAndAliasesStayProtected() throws Exception {
        Path input = copyProtectionFixture();
        Path output = temporaryFolder.getRoot().toPath().resolve("protected-version.pdf");
        new DocumentWorkflow().execute(requestBuilder()
                .source("input", DocumentSource.path(input)).primarySource("input")
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE).build(), session -> {
                    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                    PdfValuePath catalog = PdfValuePath.root(root);
                    ObjectReference alias = ((PdfIndirectReference)
                            inspectDictionary(session, root).get(PdfName.of("ExtensionItems")))
                            .getReference();
                    for (PdfValuePath path : Arrays.asList(
                            catalog.dictionaryEntry(PdfName.of("Extensions"))
                                    .dictionaryEntry(PdfName.of("T09"))
                                    .dictionaryEntry(PdfName.of("Items")),
                            PdfValuePath.root(alias))) {
                        try {
                            session.execute(DocumentPatch.builder()
                                    .removeArrayElement(path, 0).build());
                            fail("Version metadata arrays must remain engine-owned");
                        } catch (DocumentFailure failure) {
                            assertEquals(DocumentFailureCode.COMMAND_REJECTED, failure.getCode());
                            assertEquals("document.version-password-security", failure.getCapabilityId());
                            assertEquals("A Document Patch cannot change engine-owned version or password-security state.",
                                    failure.getDiagnostic());
                            assertNull(failure.getCause());
                        }
                    }
                    return null;
                });
        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            ObjectReference root = session.query(DocumentRootReference.INSTANCE);
            ObjectReference alias = ((PdfIndirectReference)
                    inspectDictionary(session, root).get(PdfName.of("ExtensionItems"))).getReference();
            PdfArray items = (PdfArray) session.query(InspectObject.version1(alias,
                    PdfInspectionLimits.of(2, 0)));
            assertEquals(1, items.size());
            assertEquals(PdfNumber.of(1L), items.get(0));
            return null;
        });
    }

    @Test
    public void indirectCatalogVersionAliasesRemainEngineOwned() throws Exception {
        Path input = copyFixture("indirect-version-alias.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve("protected-indirect-version.pdf");
        byte[] sourceBytes = Files.readAllBytes(input);
        new DocumentWorkflow().execute(requestBuilder()
                .source("input", DocumentSource.path(input)).primarySource("input")
                .target("output", PublicationTarget.path(output)).saveMode(SaveMode.INCREMENTAL).build(), session -> {
                    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                    PdfDictionary catalog = inspectDictionary(session, root);
                    ObjectReference version = ((PdfIndirectReference) catalog.get(PdfName.of("VersionAlias"))).getReference();
                    assertEquals(version, ((PdfIndirectReference) catalog.get(PdfName.of("Version"))).getReference());
                    try {
                        session.execute(DocumentPatch.builder()
                                .setDictionaryEntry(root, PdfName.of("T09Discarded"), PdfBoolean.of(true))
                                .replaceValue(PdfValuePath.root(version), PdfName.of("2.0")).build());
                        fail("Replacing an indirect Version alias must not bypass output policy");
                    } catch (DocumentFailure failure) {
                        assertEquals(DocumentFailureCode.COMMAND_REJECTED, failure.getCode());
                        assertEquals("document.version-password-security", failure.getCapabilityId());
                        assertEquals("A Document Patch cannot change engine-owned version or password-security state.",
                                failure.getDiagnostic());
                        assertNull(failure.getCause());
                    }
                    assertNull(catalog.get(PdfName.of("T09Discarded")));
                    assertEquals(PdfName.of("1.7"), session.query(InspectObject.version1(version, PdfInspectionLimits.of(1, 0))));
                    session.execute(DocumentPatch.builder().setDictionaryEntry(root, PdfName.of("T09Kept"), PdfBoolean.of(true)).build());
                    return null;
                });
        assertArrayEquals(sourceBytes, Files.readAllBytes(input));
        assertArrayEquals(sourceBytes, Arrays.copyOf(Files.readAllBytes(output), sourceBytes.length));
        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            PdfDictionary catalog = inspectDictionary(session, session.query(DocumentRootReference.INSTANCE));
            ObjectReference version = ((PdfIndirectReference) catalog.get(PdfName.of("VersionAlias"))).getReference();
            PdfValue effective = catalog.get(PdfName.of("Version"));
            if (effective instanceof PdfIndirectReference) {
                effective = session.query(InspectObject.version1(((PdfIndirectReference) effective).getReference(), PdfInspectionLimits.of(1, 0)));
            }
            assertEquals(PdfName.of("1.7"), effective);
            assertEquals(PdfName.of("1.7"), session.query(InspectObject.version1(version, PdfInspectionLimits.of(1, 0))));
            assertEquals(PdfBoolean.of(true), catalog.get(PdfName.of("T09Kept")));
            assertNull(catalog.get(PdfName.of("T09Discarded")));
            assertEquals(Integer.valueOf(1), session.query(PageCount.INSTANCE));
            return null;
        });
    }

    @Test
    public void changedStreamIsRevalidatedInsidePatch() throws Exception {
        Path input = copyProtectionFixture();
        Path output = temporaryFolder.getRoot().toPath().resolve("preflight-rejected.pdf");
        byte[] sourceBytes = Files.readAllBytes(input);
        WorkflowResourcePolicy defaults = WorkflowResourcePolicy.safeDefaults();
        WorkflowResourcePolicy policy = WorkflowResourcePolicy.builder()
                .maximumInputBytes(defaults.getMaximumInputBytes())
                .maximumPages(defaults.getMaximumPages())
                .maximumObjects(defaults.getMaximumObjects())
                .maximumNestingDepth(defaults.getMaximumNestingDepth())
                .maximumDecompressedBytes(3L)
                .maximumDecodedPixels(defaults.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(defaults.getMaximumOwnedMemoryBytes())
                .maximumTemporaryStorageBytes(defaults.getMaximumTemporaryStorageBytes())
                .maximumElapsedTime(defaults.getMaximumElapsedTime())
                .maximumConcurrentWorkflows(defaults.getMaximumConcurrentWorkflows()).build();
        try {
            new DocumentWorkflow().execute(requestBuilder()
                    .source("input", DocumentSource.path(input)).primarySource("input")
                    .target("output", PublicationTarget.path(output))
                    .resourcePolicy(policy).saveMode(SaveMode.REWRITE).build(), session -> {
                        ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                        PdfDictionary catalog = inspectDictionary(session, root);
                        ObjectReference encoding = ((PdfIndirectReference)
                                catalog.get(PdfName.of("Encoding"))).getReference();
                        PdfArray encodingView = (PdfArray) session.query(InspectObject.version1(
                                encoding, PdfInspectionLimits.of(8, 0)));
                        PdfStream streamView = inspectTestStream(session);
                        ObjectReference stream = ((PdfIndirectReference)
                                inspectTestValue(session)).getReference();
                        try {
                            session.execute(DocumentPatch.builder().setDictionaryEntry(stream,
                                    PdfName.of("Subtype"), PdfName.of("T09Changed")).build());
                            fail("Stream preflight must finish before the Patch returns");
                        } catch (DocumentFailure failure) {
                            assertEquals(DocumentFailureCode.DECOMPRESSION_LIMIT_EXCEEDED,
                                    failure.getCode());
                            for (int access = 0; access < 6; access++) {
                                try {
                                    switch (access) {
                                        case 0: catalog.size(); break;
                                        case 1: catalog.get(TEST_VALUE); break;
                                        case 2: encodingView.size(); break;
                                        case 3: encodingView.get(0); break;
                                        case 4: streamView.readBytes(); break;
                                        default: streamView.getDictionary().get(PdfName.of("Length")); break;
                                    }
                                    fail("Existing value views must reject access after a terminal limit");
                                } catch (DocumentFailure viewFailure) {
                                    assertEquals(failure.getCode(), viewFailure.getCode());
                                    assertEquals(failure.getCapabilityId(), viewFailure.getCapabilityId());
                                    assertEquals(failure.getDiagnostic(), viewFailure.getDiagnostic());
                                }
                            }
                        }
                        return null;
                    });
            fail("The resource limit must terminate the workflow");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.DECOMPRESSION_LIMIT_EXCEEDED, failure.getCode());
            assertEquals("document.hostile-input-limits", failure.getCapabilityId());
            assertEquals("The workflow decompression limit was exceeded.", failure.getDiagnostic());
            assertNull(failure.getCause());
        }
        assertArrayEquals(sourceBytes, Files.readAllBytes(input));
        assertFalse(Files.exists(output));
    }

    @Test
    public void valueReplacementKeepsIndirectAliasesAndOrderedLocations() throws Exception {
        Path input = copyProtectionFixture();
        byte[] sourceBytes = Files.readAllBytes(input);
        for (SaveMode mode : Arrays.asList(SaveMode.REWRITE, SaveMode.INCREMENTAL)) {
            Path output = temporaryFolder.getRoot().toPath().resolve("replaced-" + mode + ".pdf");
            WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(requestBuilder()
                    .source("input", DocumentSource.path(input)).primarySource("input")
                    .target("output", PublicationTarget.path(output)).saveMode(mode).build(), session -> {
                        ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                        ObjectReference original = ((PdfIndirectReference)
                                inspectDictionary(session, root).get(PdfName.of("Scalar"))).getReference();
                        assertEquals(PdfNumber.of(42L), session.query(InspectObject.version1(
                                original, PdfInspectionLimits.of(1, 0))));
                        PdfValuePath value = PdfValuePath.root(original);
                        session.execute(DocumentPatch.builder()
                                .replaceValue(value, PdfDictionary.builder()
                                        .put(PdfName.of("Items"), PdfArray.of(PdfNumber.of(1L),
                                                PdfString.of("before".getBytes(StandardCharsets.UTF_8)))).build())
                                .replaceValue(value.dictionaryEntry(PdfName.of("Items")).arrayElement(1),
                                        PdfString.of("after".getBytes(StandardCharsets.UTF_8)))
                                .replaceValue(value.dictionaryEntry(PdfName.of("Flag")), PdfBoolean.of(true))
                                .setDictionaryEntry(root, PdfName.of("Latest"), PdfIndirectReference.of(original))
                                .build());
                        PdfDictionary catalog = inspectDictionary(session, root);
                        for (String alias : new String[] {"Scalar", "ScalarAlias", "Latest"}) {
                            assertEquals(original, ((PdfIndirectReference)
                                    catalog.get(PdfName.of(alias))).getReference());
                        }
                        assertReplacementValue(inspectDictionary(session, original));
                        return null;
                    });
            assertEquals(PublicationStatus.COMMITTED, outcome.getPublicationReceipts().get(0).getStatus());
            assertArrayEquals(sourceBytes, Files.readAllBytes(input));
            if (mode == SaveMode.INCREMENTAL) {
                assertArrayEquals(sourceBytes, Arrays.copyOf(Files.readAllBytes(output), sourceBytes.length));
            }
            new DocumentWorkflow().execute(sourceRequest(output), session -> {
                PdfDictionary catalog = inspectDictionary(session, session.query(DocumentRootReference.INSTANCE));
                ObjectReference expected = ((PdfIndirectReference) catalog.get(PdfName.of("Scalar"))).getReference();
                assertEquals(expected, ((PdfIndirectReference) catalog.get(PdfName.of("ScalarAlias"))).getReference());
                assertEquals(expected, ((PdfIndirectReference) catalog.get(PdfName.of("Latest"))).getReference());
                assertReplacementValue(inspectDictionary(session, expected));
                return null;
            });
        }
    }

    private static void assertReplacementValue(PdfDictionary dictionary) throws DocumentFailure {
        assertEquals(PdfBoolean.of(true), dictionary.get(PdfName.of("Flag")));
        PdfArray items = (PdfArray) dictionary.get(PdfName.of("Items"));
        assertEquals(2, items.size());
        assertEquals(PdfNumber.of(1L), items.get(0));
        assertEquals(PdfString.of("after".getBytes(StandardCharsets.UTF_8)), items.get(1));
    }

    @Test
    public void replacingIndirectDictionaryPreservesExistingPageTreeLinks() throws Exception {
        Path input = copyProtectionFixture();
        Path output = temporaryFolder.getRoot().toPath().resolve("replaced-pages.pdf");
        new DocumentWorkflow().execute(requestBuilder()
                .source("input", DocumentSource.path(input)).primarySource("input")
                .target("output", PublicationTarget.path(output)).saveMode(SaveMode.REWRITE).build(), session -> {
                    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                    ObjectReference pages = ((PdfIndirectReference)
                            inspectDictionary(session, root).get(PdfName.of("Pages"))).getReference();
                    PdfDictionary before = inspectDictionary(session, pages);
                    PdfDictionary.Builder replacement = PdfDictionary.builder();
                    for (int index = 0; index < before.size(); index++) {
                        PdfDictionaryEntry entry = before.getEntry(index);
                        replacement.put(entry.getName(), entry.getValue());
                    }
                    replacement.put(PdfName.of("T09Note"), PdfBoolean.of(true));
                    session.execute(DocumentPatch.builder()
                            .replaceValue(PdfValuePath.root(pages), replacement.build()).build());
                    assertEquals(pages, ((PdfIndirectReference)
                            inspectDictionary(session, root).get(PdfName.of("Pages"))).getReference());
                    assertEquals(Integer.valueOf(1), session.query(PageCount.INSTANCE));
                    return null;
                });
        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            ObjectReference root = session.query(DocumentRootReference.INSTANCE);
            ObjectReference pages = ((PdfIndirectReference)
                    inspectDictionary(session, root).get(PdfName.of("Pages"))).getReference();
            assertEquals(PdfBoolean.of(true), inspectDictionary(session, pages).get(PdfName.of("T09Note")));
            assertEquals(Integer.valueOf(1), session.query(PageCount.INSTANCE));
            return null;
        });
    }

    @Test
    public void laterRootReplacementOverridesEarlierNestedEdits() throws Exception {
        Path input = copyProtectionFixture();
        Path output = temporaryFolder.getRoot().toPath().resolve("ordered-replacement.pdf");
        new DocumentWorkflow().execute(requestBuilder()
                .source("input", DocumentSource.path(input)).primarySource("input")
                .target("output", PublicationTarget.path(output)).saveMode(SaveMode.REWRITE).build(), session -> {
                    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                    ObjectReference reference = ((PdfIndirectReference)
                            inspectDictionary(session, root).get(PdfName.of("Scalar"))).getReference();
                    PdfDictionary original = PdfDictionary.builder().put(PdfName.of("Items"),
                            PdfArray.of(PdfNumber.of(1L))).build();
                    session.execute(DocumentPatch.builder().replaceValue(PdfValuePath.root(reference), original).build());
                    session.execute(DocumentPatch.builder().setArrayElement(PdfValuePath.root(reference)
                                    .dictionaryEntry(PdfName.of("Items")), 0, PdfNumber.of(9L))
                            .replaceValue(PdfValuePath.root(reference), original).build());
                    PdfArray values = (PdfArray) inspectDictionary(session, reference).get(PdfName.of("Items"));
                    assertEquals(PdfNumber.of(1L), values.get(0));
                    return null;
                });
        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            ObjectReference root = session.query(DocumentRootReference.INSTANCE);
            ObjectReference reference = ((PdfIndirectReference)
                    inspectDictionary(session, root).get(PdfName.of("Scalar"))).getReference();
            PdfArray values = (PdfArray) inspectDictionary(session, reference).get(PdfName.of("Items"));
            assertEquals(PdfNumber.of(1L), values.get(0));
            return null;
        });
    }

    @Test
    public void rejectedReplacementPreservesReferencesAndAllowsLaterPublication() throws Exception {
        Path input = copyProtectionFixture();
        Path output = temporaryFolder.getRoot().toPath().resolve("replacement-atomic.pdf");
        byte[] sourceBytes = Files.readAllBytes(input);
        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(requestBuilder()
                .source("input", DocumentSource.path(input)).primarySource("input")
                .target("output", PublicationTarget.path(output)).saveMode(SaveMode.REWRITE).build(), session -> {
                    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                    ObjectReference reference = ((PdfIndirectReference)
                            inspectDictionary(session, root).get(PdfName.of("Scalar"))).getReference();
                    DocumentPatch[] invalid = {
                        DocumentPatch.builder().replaceValue(PdfValuePath.root(reference),
                                PdfIndirectReference.of(reference)).build(),
                        DocumentPatch.builder().replaceValue(PdfValuePath.root(reference),
                                PdfArray.of(PdfIndirectReference.of(reference))).build(),
                        DocumentPatch.builder().replaceValue(PdfValuePath.root(reference), PdfArray.of(PdfNull.INSTANCE))
                                .removeArrayElement(PdfValuePath.root(reference), 2).build(),
                        DocumentPatch.builder().replaceValue(PdfValuePath.root(reference), PdfArray.of(PdfNull.INSTANCE))
                                .removeDictionaryEntry(root, PdfName.of("Pages")).build()
                    };
                    String[] diagnostics = {
                        "The Document Patch would introduce a reference cycle.",
                        "The Document Patch would introduce a reference cycle.",
                        "The Document Patch target path is invalid.",
                        "The Document Patch would invalidate the document structure."
                    };
                    for (int index = 0; index < invalid.length; index++) {
                        try {
                            session.execute(invalid[index]);
                            fail("The invalid replacement must reject the entire Patch");
                        } catch (DocumentFailure failure) {
                            assertSafePatchFailure(failure, index <= 1 ? DocumentFailureCode.PATCH_CYCLE_REJECTED
                                    : DocumentFailureCode.COMMAND_REJECTED, diagnostics[index]);
                        }
                        assertEquals(PdfNumber.of(42L), session.query(InspectObject.version1(reference,
                                PdfInspectionLimits.of(1, 0))));
                        PdfDictionary catalog = inspectDictionary(session, root);
                        assertEquals(reference, ((PdfIndirectReference) catalog.get(PdfName.of("Scalar"))).getReference());
                        assertEquals(reference, ((PdfIndirectReference) catalog.get(PdfName.of("ScalarAlias"))).getReference());
                        assertEquals(Integer.valueOf(1), session.query(PageCount.INSTANCE));
                    }
                    session.execute(DocumentPatch.builder().replaceValue(PdfValuePath.root(reference),
                            PdfNumber.of(99L)).build());
                    return null;
                });
        assertEquals(PublicationStatus.COMMITTED, outcome.getPublicationReceipts().get(0).getStatus());
        assertArrayEquals(sourceBytes, Files.readAllBytes(input));
        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            PdfDictionary catalog = inspectDictionary(session, session.query(DocumentRootReference.INSTANCE));
            ObjectReference reference = ((PdfIndirectReference) catalog.get(PdfName.of("Scalar"))).getReference();
            assertEquals(reference, ((PdfIndirectReference) catalog.get(PdfName.of("ScalarAlias"))).getReference());
            assertEquals(PdfNumber.of(99L), session.query(InspectObject.version1(reference, PdfInspectionLimits.of(1, 0))));
            return null;
        });
    }

    @Test
    public void bareReferenceBodyIsRejectedWhileContainerReferencesRemainValid() throws Exception {
        Path input = copyProtectionFixture();
        Path output = temporaryFolder.getRoot().toPath().resolve("reference-placement.pdf");
        new DocumentWorkflow().execute(requestBuilder()
                .source("input", DocumentSource.path(input)).primarySource("input")
                .target("output", PublicationTarget.path(output)).saveMode(SaveMode.REWRITE).build(), session -> {
                    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                    PdfDictionary catalog = inspectDictionary(session, root);
                    ObjectReference reference = ((PdfIndirectReference) catalog.get(PdfName.of("Scalar"))).getReference();
                    PdfIndirectReference pages = (PdfIndirectReference) catalog.get(PdfName.of("Pages"));
                    try {
                        session.execute(DocumentPatch.builder().replaceValue(PdfValuePath.root(reference), pages).build());
                        fail("A bare reference cannot be the body of an indirect object");
                    } catch (DocumentFailure failure) {
                        assertSafePatchFailure(failure, DocumentFailureCode.PATCH_VALUE_REJECTED,
                                "An indirect object replacement must contain a direct PDF value.");
                    }
                    assertEquals(PdfNumber.of(42L), session.query(InspectObject.version1(reference,
                            PdfInspectionLimits.of(1, 0))));
                    session.execute(DocumentPatch.builder().replaceValue(PdfValuePath.root(root)
                            .dictionaryEntry(PdfName.of("Forward")), pages).build());
                    return null;
                });
        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            PdfDictionary catalog = inspectDictionary(session, session.query(DocumentRootReference.INSTANCE));
            assertEquals(((PdfIndirectReference) catalog.get(PdfName.of("Pages"))).getReference(),
                    ((PdfIndirectReference) catalog.get(PdfName.of("Forward"))).getReference());
            return null;
        });
    }

    @Test
    public void replacingIndirectArrayPreservesExistingPageTreeLinks() throws Exception {
        Path input = copyFixture("indirect-and-hidden-values.pdf");
        byte[] sourceBytes = Files.readAllBytes(input);
        for (SaveMode mode : Arrays.asList(SaveMode.REWRITE, SaveMode.INCREMENTAL)) {
            Path output = temporaryFolder.getRoot().toPath().resolve("array-links-" + mode + ".pdf");
            new DocumentWorkflow().execute(requestBuilder()
                    .source("input", DocumentSource.path(input)).primarySource("input")
                    .target("output", PublicationTarget.path(output)).saveMode(mode).build(), session -> {
                        ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                        ObjectReference pages = ((PdfIndirectReference)
                                inspectDictionary(session, root).get(PdfName.of("Pages"))).getReference();
                        ObjectReference kids = ((PdfIndirectReference)
                                inspectDictionary(session, pages).get(PdfName.of("Kids"))).getReference();
                        PdfArray before = (PdfArray) session.query(InspectObject.version1(kids, PdfInspectionLimits.of(3, 0)));
                        session.execute(DocumentPatch.builder().replaceValue(PdfValuePath.root(kids),
                                PdfArray.of(before.get(0))).build());
                        assertEquals(kids, ((PdfIndirectReference)
                                inspectDictionary(session, pages).get(PdfName.of("Kids"))).getReference());
                        assertEquals(Integer.valueOf(1), session.query(PageCount.INSTANCE));
                        return null;
                    });
            assertArrayEquals(sourceBytes, Files.readAllBytes(input));
            if (mode == SaveMode.INCREMENTAL) {
                assertArrayEquals(sourceBytes, Arrays.copyOf(Files.readAllBytes(output), sourceBytes.length));
            }
            assertEquals(Integer.valueOf(1), new DocumentWorkflow().execute(sourceRequest(output),
                    session -> session.query(PageCount.INSTANCE)).getResult());
        }
    }

    @Test
    public void incrementalReplacementUpdatesPreviouslyHiddenAliases() throws Exception {
        Path input = copyFixture("indirect-and-hidden-values.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve("hidden-alias.pdf");
        byte[] sourceBytes = Files.readAllBytes(input);
        new DocumentWorkflow().execute(requestBuilder()
                .source("input", DocumentSource.path(input)).primarySource("input")
                .target("output", PublicationTarget.path(output)).saveMode(SaveMode.INCREMENTAL).build(), session -> {
                    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                    ObjectReference scalar = ((PdfIndirectReference)
                            inspectDictionary(session, root).get(PdfName.of("Scalar"))).getReference();
                    session.execute(DocumentPatch.builder().replaceValue(PdfValuePath.root(scalar), PdfNumber.of(99L)).build());
                    return null;
                });
        assertArrayEquals(sourceBytes, Arrays.copyOf(Files.readAllBytes(output), sourceBytes.length));
        for (int objectNumber : new int[] {9, 13}) {
            Path revealed = revealFixtureObject(output, objectNumber);
            new DocumentWorkflow().execute(sourceRequest(revealed), session -> {
                PdfDictionary catalog = inspectDictionary(session, session.query(DocumentRootReference.INSTANCE));
                ObjectReference hidden = ((PdfIndirectReference) catalog.get(PdfName.of("Revealed"))).getReference();
                PdfValue container = session.query(InspectObject.version1(hidden, PdfInspectionLimits.of(3, 0)));
                PdfValue alias = container instanceof PdfArray ? ((PdfArray) container).get(0)
                        : ((PdfDictionary) container).get(PdfName.of("Value"));
                ObjectReference value = ((PdfIndirectReference) alias).getReference();
                assertEquals(((PdfIndirectReference) catalog.get(PdfName.of("Scalar"))).getReference(), value);
                assertEquals(PdfNumber.of(99L), session.query(InspectObject.version1(value, PdfInspectionLimits.of(1, 0))));
                return null;
            });
        }
    }

    @Test
    public void discardedEqualReplacementValuesDoNotAccumulateMemoryCharges() throws Exception {
        Path input = copyProtectionFixture();
        Path output = temporaryFolder.getRoot().toPath().resolve("replacement-memory.pdf");
        byte[] bytes = new byte[1024 * 1024];
        Arrays.fill(bytes, (byte) 's');
        PdfString payload = PdfString.of(bytes);
        WorkflowResourcePolicy defaults = WorkflowResourcePolicy.safeDefaults();
        WorkflowResourcePolicy policy = WorkflowResourcePolicy.builder()
                .maximumInputBytes(defaults.getMaximumInputBytes())
                .maximumPages(defaults.getMaximumPages())
                .maximumObjects(defaults.getMaximumObjects())
                .maximumNestingDepth(defaults.getMaximumNestingDepth())
                .maximumDecompressedBytes(defaults.getMaximumDecompressedBytes())
                .maximumDecodedPixels(defaults.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(16L * 1024 * 1024)
                .maximumTemporaryStorageBytes(defaults.getMaximumTemporaryStorageBytes())
                .maximumElapsedTime(defaults.getMaximumElapsedTime())
                .maximumConcurrentWorkflows(defaults.getMaximumConcurrentWorkflows()).build();
        new DocumentWorkflow().execute(requestBuilder()
                .source("input", DocumentSource.path(input)).primarySource("input")
                .target("output", PublicationTarget.path(output)).resourcePolicy(policy)
                .saveMode(SaveMode.REWRITE).build(), session -> {
                    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                    ObjectReference value = ((PdfIndirectReference)
                            inspectDictionary(session, root).get(PdfName.of("Scalar"))).getReference();
                    for (int index = 0; index < 24; index++) {
                        // Alternate a partial replacement and a complete no-op.
                        PdfDictionary replacement = PdfDictionary.builder()
                                .put(PdfName.of("Payload"), payload)
                                .put(PdfName.of("Stamp"), PdfNumber.of(index / 2)).build();
                        session.execute(DocumentPatch.builder().replaceValue(PdfValuePath.root(value), replacement).build());
                    }
                    assertEquals(PdfNumber.of(11L), inspectDictionary(session, value).get(PdfName.of("Stamp")));
                    return null;
                });
        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            ObjectReference root = session.query(DocumentRootReference.INSTANCE);
            ObjectReference value = ((PdfIndirectReference)
                    inspectDictionary(session, root).get(PdfName.of("Scalar"))).getReference();
            PdfDictionary dictionary = inspectDictionary(session, value);
            assertArrayEquals(bytes, ((PdfString) dictionary.get(PdfName.of("Payload"))).getBytes());
            assertEquals(PdfNumber.of(11L), dictionary.get(PdfName.of("Stamp")));
            return null;
        });
    }

    @Test
    public void intermediateRootReplacementsReleaseTheirWorkingValues() throws Exception {
        Path input = copyProtectionFixture();
        Path output = temporaryFolder.getRoot().toPath().resolve("intermediate-replacements.pdf");
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .hardenedWorkerSettings(HardenedWorkerSettings.builder()
                        .maximumMessageBytes(HardenedWorkerSettings.DEFAULT_MAXIMUM_MESSAGE_BYTES)
                        .maximumHeapBytes(64L << 20).build()).build();
        PdfString payload = PdfString.of(new byte[1024 * 1024]);
        new DocumentWorkflow(environment).execute(requestBuilder()
                .source("input", DocumentSource.path(input)).primarySource("input")
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.INCREMENTAL).build(), session -> {
                    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                    ObjectReference value = ((PdfIndirectReference)
                            inspectDictionary(session, root).get(PdfName.of("Scalar"))).getReference();
                    for (int index = 0; index < 96; index++) {
                        session.execute(DocumentPatch.builder()
                                .replaceValue(PdfValuePath.root(value), PdfDictionary.builder()
                                        .put(PdfName.of("Payload"), payload).build())
                                .setDictionaryEntry(PdfValuePath.root(value), PdfName.of("Stamp"), PdfNumber.of(index))
                                .replaceValue(PdfValuePath.root(value), PdfNumber.of(index)).build());
                    }
                    assertEquals(PdfNumber.of(95L), session.query(InspectObject.version1(value,
                            PdfInspectionLimits.of(1, 0))));
                    return null;
                });
        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            ObjectReference root = session.query(DocumentRootReference.INSTANCE);
            ObjectReference value = ((PdfIndirectReference)
                    inspectDictionary(session, root).get(PdfName.of("Scalar"))).getReference();
            assertEquals(PdfNumber.of(95L), session.query(InspectObject.version1(value, PdfInspectionLimits.of(1, 0))));
            return null;
        });
    }

    @Test
    public void streamDataReplacementPreservesAliasesAttributesAndExplicitEncoding() throws Exception {
        Path input = copyProtectionFixture();
        byte[] sourceBytes = Files.readAllBytes(input);
        byte[] expected = new byte[] {0, 10, 13, 40, 41, 92, (byte) 255, 65};
        for (SaveMode mode : Arrays.asList(SaveMode.REWRITE, SaveMode.INCREMENTAL)) {
            for (PdfStreamEncoding encoding : PdfStreamEncoding.values()) {
                Path output = temporaryFolder.getRoot().toPath().resolve("stream-data-" + mode + "-" + encoding + ".pdf");
                new DocumentWorkflow().execute(requestBuilder()
                        .source("input", DocumentSource.path(input)).primarySource("input")
                        .target("output", PublicationTarget.path(output)).saveMode(mode).build(), session -> {
                            ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                            ObjectReference reference = ((PdfIndirectReference) inspectTestValue(session)).getReference();
                            assertArrayEquals(new byte[] {1, 2, 3}, inspectTestStream(session).readBytes());
                            PdfValuePath streamPath = PdfValuePath.root(root).dictionaryEntry(TEST_VALUE);
                            byte[] supplied = Arrays.copyOf(expected, expected.length);
                            DocumentPatch patch = DocumentPatch.builder()
                                    .setDictionaryEntry(streamPath, PdfName.of("Subtype"), PdfName.of("T09Preserved"))
                                    .setDictionaryEntry(root, PdfName.of("StreamAlias"), PdfIndirectReference.of(reference))
                                    .replaceStreamData(streamPath, supplied, encoding)
                                    .setDictionaryEntry(PdfValuePath.root(reference), PdfName.of("Updated"), PdfBoolean.of(true))
                                    .build();
                            supplied[0] = 1;
                            session.execute(patch);
                            assertEquals(reference, ((PdfIndirectReference) inspectTestValue(session)).getReference());
                            PdfStream changed = (PdfStream) session.query(InspectObject.version1(reference, PdfInspectionLimits.of(8, 32)));
                            assertArrayEquals(expected, changed.readBytes());
                            assertEquals(PdfName.of("T09Preserved"), changed.getDictionary().get(PdfName.of("Subtype")));
                            assertEquals(PdfBoolean.of(true), changed.getDictionary().get(PdfName.of("Updated")));
                            return null;
                        });
                assertArrayEquals(sourceBytes, Files.readAllBytes(input));
                if (mode == SaveMode.INCREMENTAL) {
                    assertArrayEquals(sourceBytes, Arrays.copyOf(Files.readAllBytes(output), sourceBytes.length));
                }
                new DocumentWorkflow().execute(sourceRequest(output), session -> {
                    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                    PdfDictionary catalog = inspectDictionary(session, root);
                    ObjectReference reference = ((PdfIndirectReference) catalog.get(TEST_VALUE)).getReference();
                    assertEquals(reference, ((PdfIndirectReference) catalog.get(PdfName.of("StreamAlias"))).getReference());
                    PdfStream stream = (PdfStream) session.query(InspectObject.version1(reference, PdfInspectionLimits.of(8, 32)));
                    assertArrayEquals(expected, stream.readBytes());
                    PdfDictionary attributes = stream.getDictionary();
                    assertEquals(encoding == PdfStreamEncoding.FLATE ? PdfName.of("FlateDecode") : null,
                            attributes.get(PdfName.of("Filter")));
                    assertEquals(null, attributes.get(PdfName.of("DecodeParms")));
                    assertEquals(PdfName.of("T09Preserved"), attributes.get(PdfName.of("Subtype")));
                    assertEquals(PdfBoolean.of(true), attributes.get(PdfName.of("Updated")));
                    assertEquals(Integer.valueOf(1), session.query(PageCount.INSTANCE));
                    return null;
                });
            }
        }
    }

    @Test
    public void rejectedStreamReplacementsReleaseBuffersAndPreserveTheSourceValue() throws Exception {
        Path input = copyProtectionFixture();
        Path output = temporaryFolder.getRoot().toPath().resolve("stream-rollback.pdf");
        byte[] sourceBytes = Files.readAllBytes(input);
        byte[] large = new byte[1024 * 1024];
        WorkflowResourcePolicy defaults = WorkflowResourcePolicy.safeDefaults();
        WorkflowResourcePolicy policy = WorkflowResourcePolicy.builder()
                .maximumInputBytes(defaults.getMaximumInputBytes())
                .maximumPages(defaults.getMaximumPages())
                .maximumObjects(defaults.getMaximumObjects())
                .maximumNestingDepth(defaults.getMaximumNestingDepth())
                .maximumDecompressedBytes(defaults.getMaximumDecompressedBytes())
                .maximumDecodedPixels(defaults.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(64L << 20)
                .maximumTemporaryStorageBytes(16L << 20)
                .maximumElapsedTime(defaults.getMaximumElapsedTime())
                .maximumConcurrentWorkflows(defaults.getMaximumConcurrentWorkflows()).build();
        new DocumentWorkflow().execute(requestBuilder()
                .source("input", DocumentSource.path(input)).primarySource("input")
                .target("output", PublicationTarget.path(output)).resourcePolicy(policy)
                .saveMode(SaveMode.INCREMENTAL).build(), session -> {
                    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                    PdfValuePath streamPath = PdfValuePath.root(root).dictionaryEntry(TEST_VALUE);
                    for (int attempt = 0; attempt < 24; attempt++) {
                        DocumentPatch.Builder patch = DocumentPatch.builder();
                        if (attempt % 3 == 2) {
                            PdfName transientName = PdfName.of("Transient");
                            patch.setDictionaryEntry(root, transientName, PdfStream.of(
                                    PdfDictionary.builder().put(PdfName.of("Payload"), PdfString.of(large)).build(), large));
                            patch.replaceStreamData(PdfValuePath.root(root).dictionaryEntry(transientName), large,
                                    PdfStreamEncoding.UNFILTERED);
                        } else {
                            patch.replaceStreamData(streamPath, large, PdfStreamEncoding.UNFILTERED);
                        }
                        if (attempt % 3 == 0) {
                            patch.setArrayElement(PdfValuePath.root(root), 0, PdfNull.INSTANCE);
                        } else {
                            patch.removeDictionaryEntry(root, PdfName.of("Pages"));
                        }
                        try {
                            session.execute(patch.build());
                            fail("Invalid work after stream generation must reject the whole Patch");
                        } catch (DocumentFailure failure) {
                            assertSafePatchFailure(failure, DocumentFailureCode.COMMAND_REJECTED,
                                    attempt % 3 == 0 ? "The Document Patch target path is invalid."
                                            : "The Document Patch would invalidate the document structure.");
                        }
                        assertArrayEquals(new byte[] {1, 2, 3}, inspectTestStream(session).readBytes());
                        assertEquals(null, inspectDictionary(session, root).get(PdfName.of("Transient")));
                        assertEquals(Integer.valueOf(1), session.query(PageCount.INSTANCE));
                    }
                    session.execute(DocumentPatch.builder().replaceStreamData(streamPath,
                            new byte[] {9, 8}, PdfStreamEncoding.UNFILTERED).build());
                    return null;
                });
        assertArrayEquals(sourceBytes, Files.readAllBytes(input));
        assertArrayEquals(sourceBytes, Arrays.copyOf(Files.readAllBytes(output), sourceBytes.length));
        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            assertArrayEquals(new byte[] {9, 8}, inspectTestStream(session).readBytes());
            assertEquals(Integer.valueOf(1), session.query(PageCount.INSTANCE));
            return null;
        });
    }

    @Test
    public void streamDataChangesPreserveExistingOwnerLinksAndViews() throws Exception {
        Path input = copyFixture("stream-owner-link.pdf");
        byte[] sourceBytes = Files.readAllBytes(input);
        for (SaveMode mode : Arrays.asList(SaveMode.REWRITE, SaveMode.INCREMENTAL)) {
            Path output = temporaryFolder.getRoot().toPath().resolve("stream-owner-" + mode + ".pdf");
            new DocumentWorkflow().execute(requestBuilder()
                    .source("input", DocumentSource.path(input)).primarySource("input")
                    .target("output", PublicationTarget.path(output)).saveMode(mode).build(), session -> {
                        ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                        PdfStream view = inspectTestStream(session);
                        ObjectReference reference = view.getReference().get();
                        assertEquals(root, ((PdfIndirectReference) view.getDictionary().get(PdfName.of("Owner"))).getReference());
                        session.execute(DocumentPatch.builder().replaceStreamData(PdfValuePath.root(reference),
                                new byte[] {7, 6}, PdfStreamEncoding.FLATE).build());
                        assertArrayEquals(new byte[] {7, 6}, view.readBytes());
                        assertEquals(root, ((PdfIndirectReference) view.getDictionary().get(PdfName.of("Owner"))).getReference());
                        return null;
                    });
            assertArrayEquals(sourceBytes, Files.readAllBytes(input));
            if (mode == SaveMode.INCREMENTAL) {
                assertArrayEquals(sourceBytes, Arrays.copyOf(Files.readAllBytes(output), sourceBytes.length));
            }
            new DocumentWorkflow().execute(sourceRequest(output), session -> {
                ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                PdfStream stream = inspectTestStream(session);
                assertArrayEquals(new byte[] {7, 6}, stream.readBytes());
                assertEquals(root, ((PdfIndirectReference) stream.getDictionary().get(PdfName.of("Owner"))).getReference());
                assertEquals(Integer.valueOf(1), session.query(PageCount.INSTANCE));
                return null;
            });
        }
    }

    @Test
    public void wholeStreamReplacementPreservesItsExistingOwnerAndReference() throws Exception {
        Path input = copyFixture("stream-owner-link.pdf");
        byte[] sourceBytes = Files.readAllBytes(input);
        for (SaveMode mode : Arrays.asList(SaveMode.REWRITE, SaveMode.INCREMENTAL)) {
            Path output = temporaryFolder.getRoot().toPath().resolve("whole-stream-" + mode + ".pdf");
            new DocumentWorkflow().execute(requestBuilder()
                    .source("input", DocumentSource.path(input)).primarySource("input")
                    .target("output", PublicationTarget.path(output)).saveMode(mode).build(), session -> {
                        ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                        PdfStream view = inspectTestStream(session);
                        ObjectReference reference = view.getReference().get();
                        session.execute(DocumentPatch.builder()
                                .setDictionaryEntry(reference, PdfName.of("Discarded"), PdfBoolean.of(true))
                                .replaceValue(PdfValuePath.root(reference), PdfStream.of(PdfDictionary.builder()
                                        .put(PdfName.of("Owner"), PdfIndirectReference.of(root))
                                        .put(PdfName.of("T09Added"), PdfBoolean.of(true)).build(), new byte[] {6, 5}))
                                .build());
                        assertEquals(reference, inspectTestStream(session).getReference().get());
                        assertArrayEquals(new byte[] {6, 5}, view.readBytes());
                        assertEquals(null, view.getDictionary().get(PdfName.of("Discarded")));
                        return null;
                    });
            assertArrayEquals(sourceBytes, Files.readAllBytes(input));
            if (mode == SaveMode.INCREMENTAL) {
                assertArrayEquals(sourceBytes, Arrays.copyOf(Files.readAllBytes(output), sourceBytes.length));
            }
            new DocumentWorkflow().execute(sourceRequest(output), session -> {
                ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                PdfStream stream = inspectTestStream(session);
                PdfDictionary attributes = stream.getDictionary();
                assertArrayEquals(new byte[] {6, 5}, stream.readBytes());
                assertEquals(root, ((PdfIndirectReference) attributes.get(PdfName.of("Owner"))).getReference());
                assertEquals(PdfBoolean.of(true), attributes.get(PdfName.of("T09Added")));
                assertNull(attributes.get(PdfName.of("Discarded")));
                assertNull(attributes.get(PdfName.of("Filter")));
                assertNull(attributes.get(PdfName.of("DecodeParms")));
                return null;
            });
        }
    }

    @Test
    public void settingAnExistingOwnerLinkKeepsTheValidSourceCycle() throws Exception {
        Path input = copyFixture("stream-owner-link.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve("existing-owner.pdf");
        new DocumentWorkflow().execute(requestBuilder()
                .source("input", DocumentSource.path(input)).primarySource("input")
                .target("output", PublicationTarget.path(output)).saveMode(SaveMode.INCREMENTAL).build(), session -> {
                    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                    PdfStream stream = inspectTestStream(session);
                    ObjectReference reference = stream.getReference().get();
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(reference, PdfName.of("Owner"), PdfIndirectReference.of(root))
                            .setDictionaryEntry(reference, PdfName.of("T09Changed"), PdfBoolean.of(true)).build());
                    assertEquals(root, ((PdfIndirectReference) stream.getDictionary().get(PdfName.of("Owner"))).getReference());
                    return null;
                });
        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            ObjectReference root = session.query(DocumentRootReference.INSTANCE);
            PdfStream stream = inspectTestStream(session);
            assertEquals(root, ((PdfIndirectReference) stream.getDictionary().get(PdfName.of("Owner"))).getReference());
            assertEquals(PdfBoolean.of(true), stream.getDictionary().get(PdfName.of("T09Changed")));
            assertArrayEquals(new byte[] {1, 2, 3}, stream.readBytes());
            return null;
        });
    }

    @Test
    public void unrelatedChangesPreserveUnknownEncodedStreamsAndResources() throws Exception {
        Path input = copyFixture("unknown-encoded-resource.pdf");
        byte[] sourceBytes = Files.readAllBytes(input);
        String encoded = "\u0000T09 opaque\r\n(endstream)\\\u00ff\u0001";
        byte[] painting = "q 0.2 0.4 0.8 rg 10 20 60 40 re f Q\n".getBytes(StandardCharsets.US_ASCII);
        for (SaveMode mode : Arrays.asList(SaveMode.REWRITE, SaveMode.INCREMENTAL)) {
            Path output = temporaryFolder.getRoot().toPath().resolve("preserved-unknown-" + mode + ".pdf");
            new DocumentWorkflow().execute(requestBuilder()
                    .source("input", DocumentSource.path(input)).primarySource("input")
                    .target("output", PublicationTarget.path(output)).saveMode(mode).build(), session -> {
                        PdfStream unknown = inspectTestStream(session);
                        try {
                            unknown.readBytes();
                            fail("An unknown filter must fail safely when decoding is requested");
                        } catch (DocumentFailure failure) {
                            assertSafePatchFailure(failure, DocumentFailureCode.QUERY_FAILED,
                                    "The PDF stream could not be decoded.");
                        }
                        ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                        ObjectReference scalar = ((PdfIndirectReference) inspectDictionary(session, root)
                                .get(PdfName.of("Scalar"))).getReference();
                        session.execute(DocumentPatch.builder().replaceValue(PdfValuePath.root(scalar),
                                PdfNumber.of(23L)).build());
                        return null;
                    });
            assertArrayEquals(sourceBytes, Files.readAllBytes(input));
            byte[] published = Files.readAllBytes(output);
            assertTrue(new String(published, StandardCharsets.ISO_8859_1).contains(encoded));
            if (mode == SaveMode.INCREMENTAL) {
                assertArrayEquals(sourceBytes, Arrays.copyOf(published, sourceBytes.length));
            }
            new DocumentWorkflow().execute(sourceRequest(output), session -> {
                PdfDictionary catalog = inspectDictionary(session, session.query(DocumentRootReference.INSTANCE));
                PdfStream unknown = inspectTestStream(session);
                PdfDictionary attributes = unknown.getDictionary();
                assertEquals(PdfName.of("T09Opaque"), attributes.get(PdfName.of("Filter")));
                assertEquals(PdfString.of("authored".getBytes(StandardCharsets.US_ASCII)), ((PdfDictionary) attributes.get(PdfName.of("DecodeParms")))
                        .get(PdfName.of("Token")));
                assertEquals(PdfName.of("Retained"), attributes.get(PdfName.of("T09Private")));
                ObjectReference scalar = ((PdfIndirectReference) catalog.get(PdfName.of("Scalar"))).getReference();
                assertEquals(PdfNumber.of(23L), session.query(InspectObject.version1(scalar, PdfInspectionLimits.of(1, 0))));
                ObjectReference pages = ((PdfIndirectReference) catalog.get(PdfName.of("Pages"))).getReference();
                PdfArray kids = (PdfArray) inspectDictionary(session, pages).get(PdfName.of("Kids"));
                ObjectReference page = ((PdfIndirectReference) kids.get(0)).getReference();
                PdfDictionary pageDictionary = inspectDictionary(session, page);
                PdfDictionary pageResources = (PdfDictionary) pageDictionary.get(PdfName.of("Resources"));
                PdfDictionary privateResources = (PdfDictionary) pageResources.get(PdfName.of("T09Private"));
                assertEquals(unknown.getReference().get(), ((PdfIndirectReference) privateResources.get(PdfName.of("Blob")))
                        .getReference());
                ObjectReference contents = ((PdfIndirectReference) pageDictionary.get(PdfName.of("Contents"))).getReference();
                assertArrayEquals(painting, ((PdfStream) session.query(InspectObject.version1(contents,
                        PdfInspectionLimits.of(2, 128)))).readBytes());
                return null;
            });
        }
    }

    @Test
    public void hiddenStreamEncodingAliasesRemainProtected() throws Exception {
        Path input = copyFixture("indirect-and-hidden-values.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve("hidden-metadata.pdf");
        new DocumentWorkflow().execute(requestBuilder()
                .source("input", DocumentSource.path(input)).primarySource("input")
                .target("output", PublicationTarget.path(output)).saveMode(SaveMode.INCREMENTAL).build(), session -> {
                    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                    try {
                        session.execute(DocumentPatch.builder().removeArrayElement(PdfValuePath.root(root)
                                .dictionaryEntry(PdfName.of("HiddenEncoding")), 0).build());
                        fail("Encoding metadata of xref-only streams must remain protected");
                    } catch (DocumentFailure failure) {
                        assertSafePatchFailure(failure, DocumentFailureCode.PATCH_STREAM_CHANGE_REJECTED,
                                "The Document Patch cannot change engine-owned stream metadata.");
                    }
                    return null;
                });
        Path revealed = revealFixtureObject(output, 10);
        new DocumentWorkflow().execute(sourceRequest(revealed), session -> {
            PdfDictionary catalog = inspectDictionary(session, session.query(DocumentRootReference.INSTANCE));
            ObjectReference hidden = ((PdfIndirectReference) catalog.get(PdfName.of("Revealed"))).getReference();
            PdfStream stream = (PdfStream) session.query(InspectObject.version1(hidden, PdfInspectionLimits.of(2, 3)));
            assertArrayEquals(new byte[] {1, 2, 3}, stream.readBytes());
            return null;
        });
    }

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

        WorkflowRequest rewrite = requestBuilder()
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

        WorkflowRequest rewrite = requestBuilder()
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

    private WorkflowOutcome<Void> rewriteValue(
            Path input,
            Path output,
            PdfValue value)
            throws Exception {
        WorkflowRequest request = requestBuilder()
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

    private PdfValue readTestValue(Path source) throws Exception {
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

    private static PdfStream inspectTestStream(DocumentSession session) throws DocumentFailure {
        ObjectReference stream = ((PdfIndirectReference) inspectTestValue(session)).getReference();
        return (PdfStream) session.query(InspectObject.version1(stream, PdfInspectionLimits.of(8, 16)));
    }

    private Path copyProtectionFixture() throws Exception {
        return copyFixture("protected-containers.pdf");
    }

    private Path revealFixtureObject(Path publication, int objectNumber) throws Exception {
        byte[] bytes = Files.readAllBytes(publication);
        String syntax = new String(bytes, StandardCharsets.ISO_8859_1);
        int size = 0;
        Matcher sizes = Pattern.compile("/Size\\s+(\\d+)").matcher(syntax);
        while (sizes.find()) {
            size = Math.max(size, Integer.parseInt(sizes.group(1)));
        }
        long previous = -1;
        Matcher revisions = Pattern.compile("startxref\\s+(\\d+)\\s+%%EOF").matcher(syntax);
        while (revisions.find()) {
            previous = Long.parseLong(revisions.group(1));
        }
        assertTrue("The fixture revision must declare its xref and object extent", previous >= 0 && size > objectNumber);
        // This fixture reserves object 1 for Catalog and uses plain dictionaries.
        // A separate, authored revision makes an originally hidden object visible
        // to the next public Native read without inspecting backend state.
        String currentCatalog = null;
        Matcher catalogs = Pattern.compile("(?s)\\n1 0 obj\\s*(<<.*?>>)\\s*endobj").matcher(syntax);
        while (catalogs.find()) {
            currentCatalog = catalogs.group(1);
        }
        assertTrue("The fixture must contain a plain Catalog object", currentCatalog != null);
        String catalog = "\n1 0 obj\n" + currentCatalog.substring(0, currentCatalog.length() - 2)
                + " /Revealed " + objectNumber + " 0 R >>\nendobj\n";
        long xref = bytes.length + catalog.getBytes(StandardCharsets.US_ASCII).length;
        String revision = catalog + "xref\n1 1\n"
                + String.format(Locale.ROOT, "%010d 00000 n \n", bytes.length + 1L)
                + "trailer\n<< /Size " + size + " /Root 1 0 R /Prev " + previous
                + " >>\nstartxref\n" + xref + "\n%%EOF\n";
        Path revealed = publication.resolveSibling(publication.getFileName() + ".revealed-" + objectNumber + ".pdf");
        Files.copy(publication, revealed);
        Files.write(revealed, revision.getBytes(StandardCharsets.US_ASCII), StandardOpenOption.APPEND);
        return revealed;
    }

    private Path copyFixture(String name) throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("protected-source.pdf");
        try (InputStream fixture = getClass().getResourceAsStream("t09/" + name)) {
            assertTrue("The project-owned protection fixture must be packaged", fixture != null);
            Files.copy(fixture, input);
        }
        return input;
    }

    private static PdfDictionary inspectDictionary(
            DocumentSession session,
            ObjectReference reference) throws DocumentFailure {
        return (PdfDictionary) session.query(
                InspectObject.version1(
                        reference,
                        PdfInspectionLimits.of(8, 0L)));
    }

    private WorkflowRequest sourceRequest(Path source) {
        return requestBuilder()
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

    private void createBlankDocument(Path target) throws Exception {
        WorkflowRequest request = requestBuilder()
                .target("output", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .build();

        new DocumentWorkflow().execute(request, session -> {
            session.execute(AddBlankPage.INSTANCE);
            return null;
        });
    }

    private WorkflowRequest.Builder requestBuilder() {
        return WorkflowRequest.builder().executionProfile(executionProfile);
    }
}
