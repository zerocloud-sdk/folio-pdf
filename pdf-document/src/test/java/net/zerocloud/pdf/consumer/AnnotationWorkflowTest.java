package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.zerocloud.pdf.Annotation;
import net.zerocloud.pdf.AnnotationAppearance;
import net.zerocloud.pdf.AnnotationColor;
import net.zerocloud.pdf.AnnotationFlag;
import net.zerocloud.pdf.AnnotationProperties;
import net.zerocloud.pdf.AnnotationQuad;
import net.zerocloud.pdf.AnnotationRectangle;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentActions;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.EmbeddedFile;
import net.zerocloud.pdf.LinkActivation;
import net.zerocloud.pdf.GoToAction;
import net.zerocloud.pdf.NavigationTarget;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PageDestination;
import net.zerocloud.pdf.PageActions;
import net.zerocloud.pdf.PageRange;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfString;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.command.CopyPages;
import net.zerocloud.pdf.command.UpdateAnnotations;
import net.zerocloud.pdf.command.UpdateActions;
import net.zerocloud.pdf.command.FlattenAnnotations;
import net.zerocloud.pdf.command.InsertBlankPage;
import net.zerocloud.pdf.command.MergeDocuments;
import net.zerocloud.pdf.command.MovePages;
import net.zerocloud.pdf.command.RemovePages;
import net.zerocloud.pdf.command.SetNamedDestinations;
import net.zerocloud.pdf.command.SplitDocument;
import net.zerocloud.pdf.query.Annotations;
import net.zerocloud.pdf.query.Actions;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.NamedDestinations;
import net.zerocloud.pdf.query.PageObjectReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class AnnotationWorkflowTest {

    private static final String CAPABILITY =
            "document.annotations-actions.manage";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void textAnnotationRoundTripsGeometryAndAppearanceThroughReopen()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve("output.pdf");
        createBlankDocument(input);

        Annotation expected = Annotation.text(
                AnnotationProperties.version1(
                                "note-1",
                                1,
                                AnnotationRectangle.of(10L, 20L, 70L, 80L))
                        .contents("A project-owned T12 note")
                        .flag(AnnotationFlag.PRINT)
                        .appearance(AnnotationAppearance.version1(
                                AnnotationRectangle.of(0L, 0L, 60L, 60L),
                                ("q 0.9 0.9 0.2 rg "
                                        + "0 0 60 60 re f Q\n")
                                        .getBytes(StandardCharsets.US_ASCII)))
                        .build(),
                Annotation.TextIcon.NOTE,
                true);

        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(UpdateAnnotations.version1()
                            .put(expected)
                            .build());
                    assertEquals(
                            Collections.singletonList(expected),
                            session.query(Annotations.version1(
                                    8,
                                    4096L,
                                    4096L)));
                    return null;
                });

        assertEquals(CAPABILITY, outcome.getCapabilityId());
        assertEquals(1, outcome.getPublicationReceipts().size());
        assertEquals(
                PublicationStatus.COMMITTED,
                outcome.getPublicationReceipts().get(0).getStatus());

        List<Annotation> reopened = new DocumentWorkflow().execute(
                sourceRequest(output),
                session -> session.query(Annotations.version1(
                        8,
                        4096L,
                        4096L))).getResult();
        assertEquals(Collections.singletonList(expected), reopened);
    }

    @Test
    public void stampAnnotationRoundTripsGeometryAndAppearanceThroughReopen()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "stamp-input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "stamp-output.pdf");
        createBlankDocument(input);

        Annotation expected = Annotation.stamp(
                AnnotationProperties.version1(
                                "stamp-1",
                                1,
                                AnnotationRectangle.of(40L, 500L, 220L, 560L))
                        .contents("Approved for T12")
                        .flag(AnnotationFlag.PRINT)
                        .appearance(AnnotationAppearance.version1(
                                AnnotationRectangle.of(0L, 0L, 180L, 60L),
                                ("q 0.1 0.5 0.1 RG 3 w "
                                        + "2 2 176 56 re S Q\n")
                                        .getBytes(StandardCharsets.US_ASCII)))
                        .build(),
                "Approved");

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(UpdateAnnotations.version1()
                            .put(expected)
                            .build());
                    return null;
                });

        List<Annotation> reopened = new DocumentWorkflow().execute(
                sourceRequest(output),
                session -> session.query(Annotations.version1(
                        8,
                        4096L,
                        4096L))).getResult();
        assertEquals(Collections.singletonList(expected), reopened);
    }

    @Test
    public void highlightAnnotationRoundTripsQuadsColorAndAppearance()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "highlight-input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "highlight-output.pdf");
        createBlankDocument(input);

        Annotation expected = Annotation.highlight(
                AnnotationProperties.version1(
                                "highlight-1",
                                1,
                                AnnotationRectangle.of(50L, 400L, 250L, 430L))
                        .contents("T12 highlight")
                        .flag(AnnotationFlag.PRINT)
                        .appearance(AnnotationAppearance.version1(
                                AnnotationRectangle.of(0L, 0L, 200L, 30L),
                                ("q 1 1 0 rg 0 0 200 30 re f Q\n")
                                        .getBytes(StandardCharsets.US_ASCII)))
                        .build(),
                Arrays.asList(AnnotationQuad.of(
                        50L, 430L,
                        250L, 430L,
                        50L, 400L,
                        250L, 400L)),
                AnnotationColor.rgb(
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ZERO));

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(UpdateAnnotations.version1()
                            .put(expected)
                            .build());
                    return null;
                });

        List<Annotation> reopened = new DocumentWorkflow().execute(
                sourceRequest(output),
                session -> session.query(Annotations.version1(
                        8,
                        4096L,
                        4096L))).getResult();
        assertEquals(Collections.singletonList(expected), reopened);
    }

    @Test
    public void fileAttachmentAnnotationRoundTripsRelationshipAndAppearance()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "attachment-input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "attachment-output.pdf");
        createBlankDocument(input);

        Annotation expected = Annotation.fileAttachment(
                AnnotationProperties.version1(
                                "attachment-1",
                                1,
                                AnnotationRectangle.of(20L, 300L, 50L, 330L))
                        .contents("Project-owned attachment")
                        .flag(AnnotationFlag.PRINT)
                        .appearance(AnnotationAppearance.version1(
                                AnnotationRectangle.of(0L, 0L, 30L, 30L),
                                ("q 0 0 1 RG 2 w "
                                        + "4 2 22 26 re S Q\n")
                                        .getBytes(StandardCharsets.US_ASCII)))
                        .build(),
                EmbeddedFile.version1(
                        "evidence.txt",
                        "T12 attachment".getBytes(StandardCharsets.UTF_8),
                        "text/plain",
                        "T12 relationship fixture",
                        EmbeddedFile.Relationship.DATA),
                Annotation.FileAttachmentIcon.PAPERCLIP);

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(UpdateAnnotations.version1()
                            .put(expected)
                            .build());
                    return null;
                });

        List<Annotation> reopened = new DocumentWorkflow().execute(
                sourceRequest(output),
                session -> session.query(Annotations.version1(
                        8,
                        4096L,
                        4096L))).getResult();
        assertEquals(Collections.singletonList(expected), reopened);
    }

    @Test
    public void widgetRoundTripsWithoutCreatingAcroFormFieldBehavior()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "widget-input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "widget-output.pdf");
        createBlankDocument(input);

        Annotation expected = Annotation.widget(
                AnnotationProperties.version1(
                                "widget-1",
                                1,
                                AnnotationRectangle.of(100L, 200L, 260L, 240L))
                        .flag(AnnotationFlag.PRINT)
                        .appearance(AnnotationAppearance.version1(
                                AnnotationRectangle.of(0L, 0L, 160L, 40L),
                                ("q 0.2 0.2 0.2 RG 1 w "
                                        + "0.5 0.5 159 39 re S Q\n")
                                        .getBytes(StandardCharsets.US_ASCII)))
                        .build());

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(UpdateAnnotations.version1()
                            .put(expected)
                            .build());
                    return null;
                });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            assertEquals(
                    Collections.singletonList(expected),
                    session.query(Annotations.version1(
                            8,
                            4096L,
                            4096L)));
            PdfDictionary catalog = (PdfDictionary) session.query(
                    InspectObject.version1(
                            session.query(DocumentRootReference.INSTANCE),
                            PdfInspectionLimits.of(16, 0L)));
            assertEquals(null, catalog.get(PdfName.of("AcroForm")));
            return null;
        });
    }

    @Test
    public void linkAnnotationRoundTripsADirectPageDestinationAndAppearance()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "link-input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "link-output.pdf");
        createBlankDocument(input, 2);

        Annotation expected = Annotation.link(
                AnnotationProperties.version1(
                                "link-1",
                                1,
                                AnnotationRectangle.of(30L, 100L, 230L, 125L))
                        .contents("Go to page two")
                        .flag(AnnotationFlag.PRINT)
                        .appearance(AnnotationAppearance.version1(
                                AnnotationRectangle.of(0L, 0L, 200L, 25L),
                                ("q 0 0 1 RG 1 w 0 1 m 200 1 l S Q\n")
                                        .getBytes(StandardCharsets.US_ASCII)))
                        .build(),
                LinkActivation.destination(
                        NavigationTarget.toPage(PageDestination.fit(2))));

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(UpdateAnnotations.version1()
                            .put(expected)
                            .build());
                    return null;
                });

        List<Annotation> reopened = new DocumentWorkflow().execute(
                sourceRequest(output),
                session -> session.query(Annotations.version1(
                        8,
                        4096L,
                        4096L))).getResult();
        assertEquals(Collections.singletonList(expected), reopened);
    }

    @Test
    public void linkAnnotationRoundTripsANamedLocalGoToAction()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "link-action-input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "link-action-output.pdf");
        createBlankDocument(input, 2);

        Annotation expected = Annotation.link(
                AnnotationProperties.version1(
                                "link-action-1",
                                1,
                                AnnotationRectangle.of(30L, 150L, 230L, 175L))
                        .appearance(AnnotationAppearance.version1(
                                AnnotationRectangle.of(0L, 0L, 200L, 25L),
                                ("q 0.1 0.3 0.8 RG 1 w "
                                        + "0 1 m 200 1 l S Q\n")
                                        .getBytes(StandardCharsets.US_ASCII)))
                        .build(),
                LinkActivation.action(GoToAction.version1(
                        NavigationTarget.toNamedDestination("chapter-two"))));

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(SetNamedDestinations.version1()
                            .set("chapter-two", PageDestination.fit(2))
                            .build());
                    session.execute(UpdateAnnotations.version1()
                            .put(expected)
                            .build());
                    return null;
                });

        List<Annotation> reopened = new DocumentWorkflow().execute(
                sourceRequest(output),
                session -> session.query(Annotations.version1(
                        8,
                        4096L,
                        4096L))).getResult();
        assertEquals(Collections.singletonList(expected), reopened);
    }

    @Test
    public void catalogAndPageGoToActionsRoundTripThroughReopen()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "actions-input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "actions-output.pdf");
        createBlankDocument(input, 3);

        GoToAction documentOpen = GoToAction.version1(
                NavigationTarget.toPage(PageDestination.fit(2)));
        GoToAction pageOpen = GoToAction.version1(
                NavigationTarget.toNamedDestination("chapter-three"));
        GoToAction pageClose = GoToAction.version1(
                NavigationTarget.toPage(PageDestination.fit(1)));

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(SetNamedDestinations.version1()
                            .set("chapter-three", PageDestination.fit(3))
                            .build());
                    session.execute(UpdateActions.version1()
                            .setDocumentOpenAction(documentOpen)
                            .setPageOpenAction(2, pageOpen)
                            .setPageCloseAction(2, pageClose)
                            .build());
                    return null;
                });

        DocumentActions reopened = new DocumentWorkflow().execute(
                sourceRequest(output),
                session -> session.query(Actions.version1(8))).getResult();
        assertEquals(documentOpen, reopened.getDocumentOpenAction().get());
        assertEquals(1, reopened.getPageActions().size());
        PageActions secondPage = reopened.getPageActions().get(0);
        assertEquals(2, secondPage.getPageNumber());
        assertEquals(pageOpen, secondPage.getOpenAction().get());
        assertEquals(pageClose, secondPage.getCloseAction().get());
    }

    @Test
    public void annotationUpdateMovesReplacesAndRemovesByIdentifier()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "update-input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "update-output.pdf");
        createBlankDocument(input, 2);

        Annotation original = Annotation.text(
                AnnotationProperties.version1(
                                "moving-note",
                                1,
                                AnnotationRectangle.of(10L, 10L, 40L, 40L))
                        .contents("before")
                        .build(),
                Annotation.TextIcon.NOTE,
                false);
        Annotation removed = Annotation.stamp(
                AnnotationProperties.version1(
                                "removed-stamp",
                                1,
                                AnnotationRectangle.of(50L, 10L, 100L, 40L))
                        .build(),
                "Draft");
        Annotation replacement = Annotation.text(
                AnnotationProperties.version1(
                                "moving-note",
                                2,
                                AnnotationRectangle.of(20L, 20L, 80L, 80L))
                        .contents("after")
                        .build(),
                Annotation.TextIcon.COMMENT,
                true);

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(UpdateAnnotations.version1()
                            .put(original)
                            .put(removed)
                            .build());
                    session.execute(UpdateAnnotations.version1()
                            .put(replacement)
                            .remove("removed-stamp")
                            .build());
                    assertEquals(
                            Collections.singletonList(replacement),
                            session.query(Annotations.version1(
                                    8,
                                    4096L,
                                    4096L)));
                    return null;
                });

        List<Annotation> reopened = new DocumentWorkflow().execute(
                sourceRequest(output),
                session -> session.query(Annotations.version1(
                        8,
                        4096L,
                        4096L))).getResult();
        assertEquals(Collections.singletonList(replacement), reopened);
    }

    @Test
    public void nonFormFlatteningIncorporatesAppearanceThenRemovesAnnotation()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "flatten-input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "flatten-output.pdf");
        createBlankDocument(input);

        Annotation stamp = Annotation.stamp(
                AnnotationProperties.version1(
                                "flatten-stamp",
                                1,
                                AnnotationRectangle.of(100L, 500L, 280L, 560L))
                        .appearance(AnnotationAppearance.version1(
                                AnnotationRectangle.of(0L, 0L, 180L, 60L),
                                ("q 0.8 0.1 0.1 RG 4 w "
                                        + "2 2 176 56 re S Q\n")
                                        .getBytes(StandardCharsets.US_ASCII)))
                        .build(),
                "Approved");

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(
                                    session.query(
                                            PageObjectReference.version1(1)),
                                    PdfName.of("Contents"),
                                    PdfStream.of(
                                            PdfDictionary.builder().build(),
                                            ("2 0 0 2 0 0 cm\n")
                                                    .getBytes(StandardCharsets
                                                            .US_ASCII)))
                            .build());
                    session.execute(UpdateAnnotations.version1()
                            .put(stamp)
                            .build());
                    session.execute(FlattenAnnotations.version1(
                            "flatten-stamp"));
                    assertEquals(
                            Collections.<Annotation>emptyList(),
                            session.query(Annotations.version1(
                                    8,
                                    4096L,
                                    4096L)));
                    return null;
                });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            assertEquals(
                    Collections.<Annotation>emptyList(),
                    session.query(Annotations.version1(
                            8,
                            4096L,
                            4096L)));
            PdfDictionary page = (PdfDictionary) session.query(
                    InspectObject.version1(
                            session.query(PageObjectReference.version1(1)),
                            PdfInspectionLimits.of(32, 4096L)));
            assertEquals(null, page.get(PdfName.of("Annots")));
            PdfArray contents = (PdfArray) page.get(PdfName.of("Contents"));
            assertEquals(4, contents.size());
            assertEquals("q\n", new String(
                    streamValue(session, contents.get(0)).readBytes(),
                    StandardCharsets.US_ASCII));
            assertEquals("2 0 0 2 0 0 cm\n", new String(
                    streamValue(session, contents.get(1)).readBytes(),
                    StandardCharsets.US_ASCII));
            assertEquals("Q\n", new String(
                    streamValue(session, contents.get(2)).readBytes(),
                    StandardCharsets.US_ASCII));
            String invocation = new String(
                    streamValue(session, contents.get(3)).readBytes(),
                    StandardCharsets.US_ASCII);
            assertEquals(true, invocation.startsWith("q\n"));
            assertEquals(true, invocation.endsWith(" Do\nQ\n"));
            PdfDictionary resources = (PdfDictionary) page.get(
                    PdfName.of("Resources"));
            PdfDictionary xObjects = (PdfDictionary) resources.get(
                    PdfName.of("XObject"));
            assertEquals(1, xObjects.size());
            return null;
        });
    }

    @Test
    public void namedDestinationRemovalRejectsManagedReferencesAtomically()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "named-action-removal-input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "named-action-removal-output.pdf");
        createBlankDocument(input, 2);

        new DocumentWorkflow().execute(rewriteRequest(input, output), session -> {
            session.execute(SetNamedDestinations.version1()
                    .set("chapter", PageDestination.fit(2))
                    .build());
            session.execute(UpdateAnnotations.version1()
                    .put(Annotation.link(
                            AnnotationProperties.version1(
                                            "chapter-link",
                                            1,
                                            AnnotationRectangle.of(
                                                    10L, 10L, 80L, 30L))
                                    .build(),
                            LinkActivation.destination(
                                    NavigationTarget.toNamedDestination(
                                            "chapter"))))
                    .build());
            session.execute(UpdateActions.version1()
                    .setDocumentOpenAction(GoToAction.version1(
                            NavigationTarget.toNamedDestination("chapter")))
                    .build());

            try {
                session.execute(SetNamedDestinations.version1()
                        .remove("chapter")
                        .build());
                fail("Expected referenced destination removal rejection");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.DESTINATION_CONFLICT,
                        failure.getCode());
                assertEquals(CAPABILITY, failure.getCapabilityId());
            }
            assertEquals(PageDestination.fit(2),
                    session.query(NamedDestinations.version1(8))
                            .get("chapter"));
            return null;
        });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            assertEquals(PageDestination.fit(2),
                    session.query(NamedDestinations.version1(8))
                            .get("chapter"));
            assertEquals("chapter",
                    session.query(Annotations.version1(8, 4096L, 4096L))
                            .get(0).getLinkActivation().get().getTarget()
                            .getNamedDestination().get());
            assertEquals("chapter",
                    session.query(Actions.version1(8))
                            .getDocumentOpenAction().get().getTarget()
                            .getNamedDestination().get());
            return null;
        });
    }

    @Test
    public void directAnnotationAndActionsFollowPageIdentityThroughMove()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "move-actions-input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "move-actions-output.pdf");
        createBlankDocument(input, 3);

        Annotation link = Annotation.link(
                AnnotationProperties.version1(
                                "move-link",
                                1,
                                AnnotationRectangle.of(10L, 10L, 100L, 30L))
                        .build(),
                LinkActivation.action(GoToAction.version1(
                        NavigationTarget.toPage(PageDestination.fit(2)))));
        GoToAction documentOpen = GoToAction.version1(
                NavigationTarget.toPage(PageDestination.fit(3)));
        GoToAction pageClose = GoToAction.version1(
                NavigationTarget.toPage(PageDestination.fit(2)));

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(UpdateAnnotations.version1()
                            .put(link)
                            .build());
                    session.execute(UpdateActions.version1()
                            .setDocumentOpenAction(documentOpen)
                            .setPageCloseAction(1, pageClose)
                            .build());
                    session.execute(MovePages.version1(
                            PageRange.of(2, 2),
                            1));
                    return null;
                });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            Annotation movedLink = session.query(Annotations.version1(
                    8,
                    4096L,
                    4096L)).get(0);
            assertEquals(2, movedLink.getProperties().getPageNumber());
            assertEquals(
                    PageDestination.fit(1),
                    movedLink.getLinkActivation().get().getTarget()
                            .getPageDestination().get());

            DocumentActions actions = session.query(Actions.version1(8));
            assertEquals(
                    PageDestination.fit(3),
                    actions.getDocumentOpenAction().get().getTarget()
                            .getPageDestination().get());
            assertEquals(2, actions.getPageActions().get(0).getPageNumber());
            assertEquals(
                    PageDestination.fit(1),
                    actions.getPageActions().get(0).getCloseAction().get()
                            .getTarget().getPageDestination().get());
            return null;
        });
    }

    @Test
    public void pageRemovalRejectsManagedAnnotationAndActionTargetsBeforeMutation()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "remove-target-input.pdf");
        createBlankDocument(input, 3);

        new DocumentWorkflow().execute(sourceRequest(input), session -> {
            session.execute(UpdateAnnotations.version1()
                    .put(Annotation.link(
                            AnnotationProperties.version1(
                                            "remove-link",
                                            1,
                                            AnnotationRectangle.of(
                                                    10L, 10L, 100L, 30L))
                                    .build(),
                            LinkActivation.destination(
                                    NavigationTarget.toPage(
                                            PageDestination.fit(2)))))
                    .build());
            session.execute(UpdateActions.version1()
                    .setDocumentOpenAction(GoToAction.version1(
                            NavigationTarget.toPage(
                                    PageDestination.fit(2))))
                    .setPageOpenAction(3, GoToAction.version1(
                            NavigationTarget.toPage(
                                    PageDestination.fit(2))))
                    .build());

            try {
                session.execute(RemovePages.version1(PageRange.of(2, 2)));
                fail("Expected a managed destination conflict");
            } catch (DocumentFailure failure) {
                assertEquals(
                        DocumentFailureCode.DESTINATION_CONFLICT,
                        failure.getCode());
                assertEquals(CAPABILITY, failure.getCapabilityId());
            }

            assertEquals(
                    PageDestination.fit(2),
                    session.query(Annotations.version1(
                                    8, 4096L, 4096L))
                            .get(0)
                            .getLinkActivation().get()
                            .getTarget().getPageDestination().get());
            assertEquals(
                    PageDestination.fit(2),
                    session.query(Actions.version1(8))
                            .getDocumentOpenAction().get()
                            .getTarget().getPageDestination().get());
            return null;
        });
    }

    @Test
    public void pageRemovalPreservesLegacyTextOnASurvivingPage()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "remove-legacy-input.pdf");
        createBlankDocument(input, 2);

        new DocumentWorkflow().execute(sourceRequest(input), session -> {
            session.execute(DocumentPatch.builder()
                    .setDictionaryEntry(
                            session.query(PageObjectReference.version1(1)),
                            PdfName.of("Annots"),
                            PdfArray.of(legacyText("surviving legacy note")))
                    .build());
            session.execute(RemovePages.version1(PageRange.of(2, 2)));

            PdfDictionary page = dictionaryValue(
                    session,
                    session.query(PageObjectReference.version1(1)));
            PdfArray annotations = (PdfArray) page.get(PdfName.of("Annots"));
            assertEquals(1, annotations.size());
            assertEquals(
                    PdfName.of("Text"),
                    dictionaryValue(session, annotations.get(0)).get(
                            PdfName.of("Subtype")));
            try {
                session.query(PageObjectReference.version1(2));
                fail("The removed page must no longer exist");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.PAGE_RANGE_INVALID,
                        failure.getCode());
            }
            return null;
        });
    }

    @Test
    public void copiedAnnotationsAndPageActionsRetargetSelectedPagesAndIds()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "copy-actions-input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "copy-actions-output.pdf");
        createBlankDocument(input, 3);

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(UpdateAnnotations.version1()
                            .put(Annotation.link(
                                    AnnotationProperties.version1(
                                                    "copy-link",
                                                    1,
                                                    AnnotationRectangle.of(
                                                            10L, 10L,
                                                            100L, 30L))
                                            .build(),
                                    LinkActivation.action(
                                            GoToAction.version1(
                                                    NavigationTarget.toPage(
                                                            PageDestination.fit(
                                                                    2))))))
                            .put(Annotation.link(
                                    AnnotationProperties.version1(
                                                    "external-link",
                                                    2,
                                                    AnnotationRectangle.of(
                                                            10L, 40L,
                                                            100L, 60L))
                                            .build(),
                                    LinkActivation.destination(
                                            NavigationTarget.toPage(
                                                    PageDestination.fit(3)))))
                            .build());
                    session.execute(UpdateActions.version1()
                            .setDocumentOpenAction(GoToAction.version1(
                                    NavigationTarget.toPage(
                                            PageDestination.fit(3))))
                            .setPageOpenAction(1, GoToAction.version1(
                                    NavigationTarget.toPage(
                                            PageDestination.fit(2))))
                            .build());
                    session.execute(CopyPages.version1(
                            PageRange.of(1, 2),
                            4));
                    return null;
                });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            Map<String, Annotation> byIdentifier =
                    new LinkedHashMap<String, Annotation>();
            for (Annotation annotation : session.query(
                    Annotations.version1(16, 8192L, 8192L))) {
                byIdentifier.put(
                        annotation.getProperties().getIdentifier(),
                        annotation);
            }
            assertEquals(4, byIdentifier.size());
            assertEquals(
                    PageDestination.fit(2),
                    byIdentifier.get("copy-link")
                            .getLinkActivation().get().getTarget()
                            .getPageDestination().get());
            assertEquals(
                    4,
                    byIdentifier.get("copy-link-1")
                            .getProperties().getPageNumber());
            assertEquals(
                    PageDestination.fit(5),
                    byIdentifier.get("copy-link-1")
                            .getLinkActivation().get().getTarget()
                            .getPageDestination().get());
            assertEquals(
                    PageDestination.fit(3),
                    byIdentifier.get("external-link-1")
                            .getLinkActivation().get().getTarget()
                            .getPageDestination().get());

            DocumentActions actions = session.query(Actions.version1(16));
            assertEquals(
                    PageDestination.fit(3),
                    actions.getDocumentOpenAction().get().getTarget()
                            .getPageDestination().get());
            assertEquals(2, actions.getPageActions().size());
            assertEquals(1, actions.getPageActions().get(0).getPageNumber());
            assertEquals(
                    PageDestination.fit(2),
                    actions.getPageActions().get(0).getOpenAction().get()
                            .getTarget().getPageDestination().get());
            assertEquals(4, actions.getPageActions().get(1).getPageNumber());
            assertEquals(
                    PageDestination.fit(5),
                    actions.getPageActions().get(1).getOpenAction().get()
                            .getTarget().getPageDestination().get());
            return null;
        });
    }

    @Test
    public void copyRetargetsAManagedLinkAfterLegacyTextOnTheSamePage()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "mixed-copy-input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "mixed-copy-output.pdf");
        createBlankDocument(input, 2);

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(
                                    session.query(
                                            PageObjectReference.version1(1)),
                                    PdfName.of("Annots"),
                                    PdfArray.of(legacyText("copy legacy")))
                            .build());
                    session.execute(UpdateAnnotations.version1()
                            .put(Annotation.link(
                                    AnnotationProperties.version1(
                                                    "mixed-copy-link",
                                                    1,
                                                    AnnotationRectangle.of(
                                                            10L, 10L,
                                                            100L, 30L))
                                            .build(),
                                    LinkActivation.destination(
                                            NavigationTarget.toPage(
                                                    PageDestination.fit(2)))))
                            .build());
                    session.execute(CopyPages.version1(
                            PageRange.of(1, 2), 3));
                    return null;
                });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            PdfDictionary copiedPage = dictionaryValue(
                    session,
                    session.query(PageObjectReference.version1(3)));
            PdfArray annotations = (PdfArray) copiedPage.get(
                    PdfName.of("Annots"));
            assertEquals(2, annotations.size());
            assertEquals(
                    PdfName.of("Text"),
                    dictionaryValue(session, annotations.get(0)).get(
                            PdfName.of("Subtype")));
            PdfDictionary copiedLink = dictionaryValue(
                    session,
                    annotations.get(1));
            assertEquals(
                    PdfString.of("mixed-copy-link-1".getBytes(
                            StandardCharsets.US_ASCII)),
                    copiedLink.get(PdfName.of("NM")));
            PdfArray destination = (PdfArray) copiedLink.get(
                    PdfName.of("Dest"));
            assertEquals(
                    session.query(PageObjectReference.version1(4)),
                    ((PdfIndirectReference) destination.get(0))
                            .getReference());
            return null;
        });
    }

    @Test
    public void copySuffixesALegacyTextIdentifierWithoutChangingTheOriginal()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "legacy-copy-identifier-input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "legacy-copy-identifier-output.pdf");
        createBlankDocument(input);

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(
                                    session.query(
                                            PageObjectReference.version1(1)),
                                    PdfName.of("Annots"),
                                    PdfArray.of(legacyText(
                                            "copy legacy identifier",
                                            "legacy-copy-id")))
                            .build());
                    session.execute(CopyPages.version1(
                            PageRange.of(1, 1), 2));
                    return null;
                });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            PdfDictionary original = dictionaryValue(
                    session,
                    ((PdfArray) dictionaryValue(
                            session,
                            session.query(PageObjectReference.version1(1)))
                                    .get(PdfName.of("Annots"))).get(0));
            PdfDictionary copied = dictionaryValue(
                    session,
                    ((PdfArray) dictionaryValue(
                            session,
                            session.query(PageObjectReference.version1(2)))
                                    .get(PdfName.of("Annots"))).get(0));
            assertEquals(
                    PdfString.of("legacy-copy-id".getBytes(
                            StandardCharsets.US_ASCII)),
                    original.get(PdfName.of("NM")));
            assertEquals(
                    PdfString.of("legacy-copy-id-1".getBytes(
                            StandardCharsets.US_ASCII)),
                    copied.get(PdfName.of("NM")));
            return null;
        });
    }

    @Test
    public void mergedNamedActionsFollowDestinationCollisionRenaming()
            throws Exception {
        Path primaryInput = temporaryFolder.getRoot().toPath().resolve(
                "merge-primary-input.pdf");
        Path primary = temporaryFolder.getRoot().toPath().resolve(
                "merge-primary.pdf");
        Path appendixInput = temporaryFolder.getRoot().toPath().resolve(
                "merge-appendix-input.pdf");
        Path appendix = temporaryFolder.getRoot().toPath().resolve(
                "merge-appendix.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "merge-actions-output.pdf");
        createBlankDocument(primaryInput, 2);
        createBlankDocument(appendixInput, 2);

        new DocumentWorkflow().execute(
                rewriteRequest(primaryInput, primary),
                session -> {
                    session.execute(SetNamedDestinations.version1()
                            .set("chapter", PageDestination.fit(1))
                            .build());
                    session.execute(UpdateAnnotations.version1()
                            .put(Annotation.link(
                                    AnnotationProperties.version1(
                                                    "merge-link",
                                                    1,
                                                    AnnotationRectangle.of(
                                                            10L, 10L,
                                                            100L, 30L))
                                            .build(),
                                    LinkActivation.destination(
                                            NavigationTarget.toNamedDestination(
                                                    "chapter"))))
                            .build());
                    return null;
                });
        new DocumentWorkflow().execute(
                rewriteRequest(appendixInput, appendix),
                session -> {
                    session.execute(SetNamedDestinations.version1()
                            .set("chapter", PageDestination.fit(2))
                            .build());
                    session.execute(UpdateAnnotations.version1()
                            .put(Annotation.link(
                                    AnnotationProperties.version1(
                                                    "merge-link",
                                                    1,
                                                    AnnotationRectangle.of(
                                                            20L, 20L,
                                                            110L, 40L))
                                            .build(),
                                    LinkActivation.action(
                                            GoToAction.version1(
                                                    NavigationTarget
                                                            .toNamedDestination(
                                                                    "chapter")))))
                            .put(Annotation.link(
                                    AnnotationProperties.version1(
                                                    "merge-direct",
                                                    1,
                                                    AnnotationRectangle.of(
                                                            20L, 50L,
                                                            110L, 70L))
                                            .build(),
                                    LinkActivation.destination(
                                            NavigationTarget.toPage(
                                                    PageDestination.fit(2)))))
                            .build());
                    GoToAction namedAction = GoToAction.version1(
                            NavigationTarget.toNamedDestination("chapter"));
                    session.execute(UpdateActions.version1()
                            .setDocumentOpenAction(namedAction)
                            .setPageOpenAction(1, namedAction)
                            .build());
                    return null;
                });

        WorkflowRequest request = WorkflowRequest.builder()
                .source("primary", DocumentSource.path(primary))
                .source("appendix", DocumentSource.path(appendix))
                .primarySource("primary")
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(request, session -> {
            session.execute(MergeDocuments.version1("appendix"));
            return null;
        });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            Map<String, PageDestination> destinations = session.query(
                    NamedDestinations.version1(8));
            assertEquals(PageDestination.fit(1), destinations.get("chapter"));
            assertEquals(
                    PageDestination.fit(4),
                    destinations.get("chapter-1"));

            Map<String, Annotation> annotations =
                    new LinkedHashMap<String, Annotation>();
            for (Annotation annotation : session.query(
                    Annotations.version1(8, 4096L, 4096L))) {
                annotations.put(
                        annotation.getProperties().getIdentifier(),
                        annotation);
            }
            Annotation merged = annotations.get("merge-link-1");
            assertEquals(3, merged.getProperties().getPageNumber());
            assertEquals(
                    "chapter-1",
                    merged.getLinkActivation().get().getTarget()
                            .getNamedDestination().get());
            assertEquals(
                    PageDestination.fit(4),
                    annotations.get("merge-direct")
                            .getLinkActivation().get().getTarget()
                            .getPageDestination().get());

            DocumentActions actions = session.query(Actions.version1(8));
            assertEquals(
                    "chapter-1",
                    actions.getDocumentOpenAction().get().getTarget()
                            .getNamedDestination().get());
            assertEquals(3, actions.getPageActions().get(0).getPageNumber());
            assertEquals(
                    "chapter-1",
                    actions.getPageActions().get(0).getOpenAction().get()
                            .getTarget().getNamedDestination().get());
            return null;
        });
    }

    @Test
    public void mergeRetargetsAManagedNamedLinkAfterLegacyText()
            throws Exception {
        Path primaryInput = temporaryFolder.getRoot().toPath().resolve(
                "mixed-merge-primary-input.pdf");
        Path primary = temporaryFolder.getRoot().toPath().resolve(
                "mixed-merge-primary.pdf");
        Path appendixInput = temporaryFolder.getRoot().toPath().resolve(
                "mixed-merge-appendix-input.pdf");
        Path appendix = temporaryFolder.getRoot().toPath().resolve(
                "mixed-merge-appendix.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "mixed-merge-output.pdf");
        createBlankDocument(primaryInput);
        createBlankDocument(appendixInput);

        new DocumentWorkflow().execute(
                rewriteRequest(primaryInput, primary),
                session -> {
                    session.execute(SetNamedDestinations.version1()
                            .set("chapter", PageDestination.fit(1))
                            .build());
                    return null;
                });
        new DocumentWorkflow().execute(
                rewriteRequest(appendixInput, appendix),
                session -> {
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(
                                    session.query(
                                            PageObjectReference.version1(1)),
                                    PdfName.of("Annots"),
                                    PdfArray.of(legacyText("merge legacy")))
                            .build());
                    session.execute(SetNamedDestinations.version1()
                            .set("chapter", PageDestination.fit(1))
                            .build());
                    session.execute(UpdateAnnotations.version1()
                            .put(Annotation.link(
                                    AnnotationProperties.version1(
                                                    "mixed-merge-link",
                                                    1,
                                                    AnnotationRectangle.of(
                                                            10L, 10L,
                                                            100L, 30L))
                                            .build(),
                                    LinkActivation.action(GoToAction.version1(
                                            NavigationTarget
                                                    .toNamedDestination(
                                                            "chapter")))))
                            .build());
                    return null;
                });

        WorkflowRequest request = WorkflowRequest.builder()
                .source("primary", DocumentSource.path(primary))
                .source("appendix", DocumentSource.path(appendix))
                .primarySource("primary")
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(request, session -> {
            session.execute(MergeDocuments.version1("appendix"));
            return null;
        });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            assertEquals(
                    PageDestination.fit(2),
                    session.query(NamedDestinations.version1(8)).get(
                            "chapter-1"));
            PdfDictionary mergedPage = dictionaryValue(
                    session,
                    session.query(PageObjectReference.version1(2)));
            PdfArray annotations = (PdfArray) mergedPage.get(
                    PdfName.of("Annots"));
            assertEquals(2, annotations.size());
            assertEquals(
                    PdfName.of("Text"),
                    dictionaryValue(session, annotations.get(0)).get(
                            PdfName.of("Subtype")));
            PdfDictionary link = dictionaryValue(
                    session,
                    annotations.get(1));
            PdfDictionary action = dictionaryValue(
                    session,
                    link.get(PdfName.of("A")));
            assertEquals(
                    PdfString.of("chapter-1".getBytes(
                            StandardCharsets.US_ASCII)),
                    action.get(PdfName.of("D")));
            return null;
        });
    }

    @Test
    public void mergeRejectsLegacyIdentifierCollisionsBeforeMutation()
            throws Exception {
        Path primaryInput = temporaryFolder.getRoot().toPath().resolve(
                "legacy-id-primary-input.pdf");
        Path primary = temporaryFolder.getRoot().toPath().resolve(
                "legacy-id-primary.pdf");
        Path appendixInput = temporaryFolder.getRoot().toPath().resolve(
                "legacy-id-appendix-input.pdf");
        Path appendix = temporaryFolder.getRoot().toPath().resolve(
                "legacy-id-appendix.pdf");
        createBlankDocument(primaryInput);
        createBlankDocument(appendixInput);

        new DocumentWorkflow().execute(
                rewriteRequest(primaryInput, primary),
                session -> {
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(
                                    session.query(
                                            PageObjectReference.version1(1)),
                                    PdfName.of("Annots"),
                                    PdfArray.of(legacyText(
                                            "primary legacy",
                                            "shared-legacy-id")))
                            .build());
                    return null;
                });
        new DocumentWorkflow().execute(
                rewriteRequest(appendixInput, appendix),
                session -> {
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(
                                    session.query(
                                            PageObjectReference.version1(1)),
                                    PdfName.of("Annots"),
                                    PdfArray.of(legacyText(
                                            "appendix legacy",
                                            "shared-legacy-id")))
                            .build());
                    return null;
                });

        WorkflowRequest request = WorkflowRequest.builder()
                .source("primary", DocumentSource.path(primary))
                .source("appendix", DocumentSource.path(appendix))
                .primarySource("primary")
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(request, session -> {
            try {
                session.execute(MergeDocuments.version1("appendix"));
                fail("Expected an unrenamable legacy identifier collision");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.PRESERVATION_UNSUPPORTED,
                        failure.getCode());
                assertEquals(
                        "document.page.manipulate-merge-split",
                        failure.getCapabilityId());
            }
            try {
                session.query(PageObjectReference.version1(2));
                fail("The rejected merge must not append its page");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.PAGE_RANGE_INVALID,
                        failure.getCode());
            }
            return null;
        });
    }

    @Test
    public void splitProductsFilterTargetsThatDoNotSurviveTheirRange()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "split-actions-input.pdf");
        Path configured = temporaryFolder.getRoot().toPath().resolve(
                "split-actions-configured.pdf");
        Path first = temporaryFolder.getRoot().toPath().resolve(
                "split-actions-first.pdf");
        Path second = temporaryFolder.getRoot().toPath().resolve(
                "split-actions-second.pdf");
        createBlankDocument(input, 4);

        new DocumentWorkflow().execute(
                rewriteRequest(input, configured),
                session -> {
                    session.execute(SetNamedDestinations.version1()
                            .set("kept", PageDestination.fit(2))
                            .set("dropped", PageDestination.fit(4))
                            .build());
                    session.execute(UpdateAnnotations.version1()
                            .put(Annotation.link(
                                    AnnotationProperties.version1(
                                                    "keep-direct",
                                                    1,
                                                    AnnotationRectangle.of(
                                                            10L, 10L,
                                                            100L, 30L))
                                            .build(),
                                    LinkActivation.destination(
                                            NavigationTarget.toPage(
                                                    PageDestination.fit(2)))))
                            .put(Annotation.link(
                                    AnnotationProperties.version1(
                                                    "filter-direct",
                                                    1,
                                                    AnnotationRectangle.of(
                                                            10L, 40L,
                                                            100L, 60L))
                                            .build(),
                                    LinkActivation.destination(
                                            NavigationTarget.toPage(
                                                    PageDestination.fit(4)))))
                            .put(Annotation.link(
                                    AnnotationProperties.version1(
                                                    "keep-named",
                                                    3,
                                                    AnnotationRectangle.of(
                                                            20L, 10L,
                                                            110L, 30L))
                                            .build(),
                                    LinkActivation.action(
                                            GoToAction.version1(
                                                    NavigationTarget
                                                            .toNamedDestination(
                                                                    "dropped")))))
                            .put(Annotation.link(
                                    AnnotationProperties.version1(
                                                    "filter-named",
                                                    3,
                                                    AnnotationRectangle.of(
                                                            20L, 40L,
                                                            110L, 60L))
                                            .build(),
                                    LinkActivation.action(
                                            GoToAction.version1(
                                                    NavigationTarget
                                                            .toNamedDestination(
                                                                    "kept")))))
                            .build());
                    session.execute(UpdateActions.version1()
                            .setDocumentOpenAction(GoToAction.version1(
                                    NavigationTarget.toPage(
                                            PageDestination.fit(2))))
                            .setPageOpenAction(1, GoToAction.version1(
                                    NavigationTarget.toPage(
                                            PageDestination.fit(2))))
                            .setPageCloseAction(1, GoToAction.version1(
                                    NavigationTarget.toPage(
                                            PageDestination.fit(4))))
                            .setPageOpenAction(3, GoToAction.version1(
                                    NavigationTarget.toNamedDestination(
                                            "dropped")))
                            .setPageCloseAction(3, GoToAction.version1(
                                    NavigationTarget.toNamedDestination(
                                            "kept")))
                            .build());
                    return null;
                });

        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.path(configured))
                .primarySource("input")
                .target("first", PublicationTarget.path(first))
                .target("second", PublicationTarget.path(second))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(request, session -> {
            session.execute(SplitDocument.version1()
                    .target("first", PageRange.of(1, 2))
                    .target("second", PageRange.of(3, 4))
                    .build());
            return null;
        });

        new DocumentWorkflow().execute(sourceRequest(first), session -> {
            List<Annotation> annotations = session.query(
                    Annotations.version1(8, 4096L, 4096L));
            assertEquals(1, annotations.size());
            assertEquals(
                    "keep-direct",
                    annotations.get(0).getProperties().getIdentifier());
            assertEquals(
                    PageDestination.fit(2),
                    annotations.get(0).getLinkActivation().get().getTarget()
                            .getPageDestination().get());
            DocumentActions actions = session.query(Actions.version1(8));
            assertEquals(
                    PageDestination.fit(2),
                    actions.getDocumentOpenAction().get().getTarget()
                            .getPageDestination().get());
            assertEquals(1, actions.getPageActions().size());
            assertEquals(true, actions.getPageActions().get(0)
                    .getOpenAction().isPresent());
            assertEquals(false, actions.getPageActions().get(0)
                    .getCloseAction().isPresent());
            return null;
        });

        new DocumentWorkflow().execute(sourceRequest(second), session -> {
            List<Annotation> annotations = session.query(
                    Annotations.version1(8, 4096L, 4096L));
            assertEquals(1, annotations.size());
            assertEquals(
                    "keep-named",
                    annotations.get(0).getProperties().getIdentifier());
            assertEquals(1, annotations.get(0).getProperties().getPageNumber());
            assertEquals(
                    "dropped",
                    annotations.get(0).getLinkActivation().get().getTarget()
                            .getNamedDestination().get());
            DocumentActions actions = session.query(Actions.version1(8));
            assertEquals(false, actions.getDocumentOpenAction().isPresent());
            assertEquals(1, actions.getPageActions().size());
            assertEquals(true, actions.getPageActions().get(0)
                    .getOpenAction().isPresent());
            assertEquals(false, actions.getPageActions().get(0)
                    .getCloseAction().isPresent());
            return null;
        });
    }

    @Test
    public void splitFiltersAManagedLinkBeforeLegacyTextOnTheSamePage()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "mixed-split-input.pdf");
        Path configured = temporaryFolder.getRoot().toPath().resolve(
                "mixed-split-configured.pdf");
        Path first = temporaryFolder.getRoot().toPath().resolve(
                "mixed-split-first.pdf");
        Path second = temporaryFolder.getRoot().toPath().resolve(
                "mixed-split-second.pdf");
        createBlankDocument(input, 2);

        new DocumentWorkflow().execute(
                rewriteRequest(input, configured),
                session -> {
                    session.execute(SetNamedDestinations.version1()
                            .set("outside", PageDestination.fit(2))
                            .build());
                    PdfDictionary managedLink = PdfDictionary.builder()
                            .put(PdfName.of("Type"), PdfName.of("Annot"))
                            .put(PdfName.of("Subtype"), PdfName.of("Link"))
                            .put(PdfName.of("Rect"), PdfArray.of(
                                    PdfNumber.of(10L),
                                    PdfNumber.of(10L),
                                    PdfNumber.of(100L),
                                    PdfNumber.of(30L)))
                            .put(PdfName.of("NM"), PdfString.of(
                                    "mixed-split-link".getBytes(
                                            StandardCharsets.US_ASCII)))
                            .put(PdfName.of("Dest"), PdfString.of(
                                    "outside".getBytes(
                                            StandardCharsets.US_ASCII)))
                            .put(PdfName.of("Border"), PdfArray.of(
                                    PdfNumber.of(0L),
                                    PdfNumber.of(0L),
                                    PdfNumber.of(0L)))
                            .build();
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(
                                    session.query(
                                            PageObjectReference.version1(1)),
                                    PdfName.of("Annots"),
                                    PdfArray.of(
                                            managedLink,
                                            legacyText("split legacy")))
                            .build());
                    return null;
                });

        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.path(configured))
                .primarySource("input")
                .target("first", PublicationTarget.path(first))
                .target("second", PublicationTarget.path(second))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(request, session -> {
            session.execute(SplitDocument.version1()
                    .target("first", PageRange.of(1, 1))
                    .target("second", PageRange.of(2, 2))
                    .build());
            return null;
        });

        new DocumentWorkflow().execute(sourceRequest(first), session -> {
            assertEquals(false, session.query(
                    NamedDestinations.version1(8)).containsKey("outside"));
            PdfDictionary page = dictionaryValue(
                    session,
                    session.query(PageObjectReference.version1(1)));
            PdfArray annotations = (PdfArray) page.get(PdfName.of("Annots"));
            assertEquals(1, annotations.size());
            assertEquals(
                    PdfName.of("Text"),
                    dictionaryValue(session, annotations.get(0)).get(
                            PdfName.of("Subtype")));
            return null;
        });
    }

    @Test
    public void unsupportedActionGraphIsInertPreservedOrRejectedByContext()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "unsupported-action-input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "unsupported-action-output.pdf");
        Path marker = temporaryFolder.getRoot().toPath().resolve(
                "action-must-not-execute.marker");
        Path processMarker = temporaryFolder.getRoot().toPath().resolve(
                "process-must-not-execute.marker");
        Path launcher = temporaryFolder.getRoot().toPath().resolve(
                "must-not-launch.sh");
        Files.write(launcher, Arrays.asList(
                "#!/bin/sh",
                "touch " + processMarker.toString()),
                StandardCharsets.UTF_8);
        if (!launcher.toFile().setExecutable(true)) {
            fail("Could not prepare the process sentinel");
        }
        ServerSocket listener = new ServerSocket(
                0, 1, InetAddress.getLoopbackAddress());
        listener.setSoTimeout(250);
        createBlankDocument(input);
        String script = "this.saveAs('" + marker.toString() + "')";
        PdfDictionary action = PdfDictionary.builder()
                .put(PdfName.of("S"), PdfName.of("JavaScript"))
                .put(PdfName.of("JS"), PdfString.of(
                        script.getBytes(StandardCharsets.US_ASCII)))
                .put(PdfName.of("Next"), PdfArray.of(
                        PdfDictionary.builder()
                                .put(PdfName.of("S"), PdfName.of("URI"))
                                .put(PdfName.of("URI"), PdfString.of(
                                        ("http://127.0.0.1:"
                                                + listener.getLocalPort()
                                                + "/must-not-request")
                                                .getBytes(
                                                        StandardCharsets
                                                                .US_ASCII)))
                                .build(),
                        PdfDictionary.builder()
                                .put(PdfName.of("S"), PdfName.of("Launch"))
                                .put(PdfName.of("F"), PdfString.of(
                                        launcher.toString()
                                                .getBytes(
                                                        StandardCharsets
                                                                .US_ASCII)))
                                .build()))
                .build();

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    ObjectReference root = session.query(
                            DocumentRootReference.INSTANCE);
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(
                                    root,
                                    PdfName.of("OpenAction"),
                                    action)
                            .build());
                    session.execute(UpdateAnnotations.version1()
                            .put(Annotation.text(
                                    AnnotationProperties.version1(
                                                    "inert-note",
                                                    1,
                                                    AnnotationRectangle.of(
                                                            10L, 10L,
                                                            40L, 40L))
                                            .build(),
                                    Annotation.TextIcon.NOTE,
                                    false))
                            .build());
                    return null;
                });

        assertFalse(Files.exists(marker));
        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            PdfDictionary catalog = (PdfDictionary) session.query(
                    InspectObject.version1(
                            session.query(DocumentRootReference.INSTANCE),
                            PdfInspectionLimits.of(64, 8192L)));
            PdfDictionary preserved = (PdfDictionary) catalog.get(
                    PdfName.of("OpenAction"));
            assertEquals(PdfName.of("JavaScript"), preserved.get(
                    PdfName.of("S")));
            assertEquals(
                    PdfString.of(script.getBytes(StandardCharsets.US_ASCII)),
                    preserved.get(PdfName.of("JS")));
            assertEquals(2, ((PdfArray) preserved.get(
                    PdfName.of("Next"))).size());

            try {
                session.query(Actions.version1(8));
                fail("Expected the unsupported Action query to fail");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.QUERY_FAILED,
                        failure.getCode());
                assertEquals(CAPABILITY, failure.getCapabilityId());
            }
            try {
                session.execute(MovePages.version1(
                        PageRange.of(1, 1), 1));
                fail("Expected page mutation to reject the Action graph");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.PRESERVATION_UNSUPPORTED,
                        failure.getCode());
            }
            return null;
        });
        assertFalse(Files.exists(marker));
        assertFalse(Files.exists(processMarker));
        try {
            Socket unexpected = listener.accept();
            unexpected.close();
            fail("The unsupported URI Action opened a network connection");
        } catch (SocketTimeoutException expected) {
            // No connection proves URI data remained inert.
        } finally {
            listener.close();
        }
    }

    @Test
    public void malformedGoToActionFailsWithStableSafeDiagnostics()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "malformed-action-input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "malformed-action-output.pdf");
        createBlankDocument(input);
        PdfDictionary malformed = PdfDictionary.builder()
                .put(PdfName.of("S"), PdfName.of("GoTo"))
                .build();

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(
                                    session.query(
                                            DocumentRootReference.INSTANCE),
                                    PdfName.of("OpenAction"),
                                    malformed)
                            .build());
                    return null;
                });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            try {
                session.query(Actions.version1(8));
                fail("Expected malformed GoTo Action rejection");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.QUERY_FAILED,
                        failure.getCode());
                assertEquals(CAPABILITY, failure.getCapabilityId());
                assertEquals(
                        "The supported Actions could not be inspected safely.",
                        failure.getDiagnostic());
            }
            try {
                session.execute(CopyPages.version1(
                        PageRange.of(1, 1), 2));
                fail("Expected page mutation to reject malformed GoTo Action");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.PRESERVATION_UNSUPPORTED,
                        failure.getCode());
                assertEquals(
                        "document.page.manipulate-merge-split",
                        failure.getCapabilityId());
                assertEquals(
                        "The document contains structures that this page operation cannot preserve safely.",
                        failure.getDiagnostic());
            }
            try {
                session.query(PageObjectReference.version1(2));
                fail("The failed copy must not add a page");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.PAGE_RANGE_INVALID,
                        failure.getCode());
            }
            return null;
        });
    }

    @Test
    public void malformedAnnotationGraphFailsWithStableSafeDiagnostics()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "malformed-annotation-input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "malformed-annotation-output.pdf");
        createBlankDocument(input);
        PdfDictionary malformed = PdfDictionary.builder()
                .put(PdfName.of("Type"), PdfName.of("Annot"))
                .put(PdfName.of("Subtype"), PdfName.of("Text"))
                .put(PdfName.of("NM"), PdfString.of(
                        "missing-rectangle".getBytes(
                                StandardCharsets.US_ASCII)))
                .put(PdfName.of("Name"), PdfName.of("Note"))
                .build();

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(
                                    session.query(
                                            PageObjectReference.version1(1)),
                                    PdfName.of("Annots"),
                                    PdfArray.of(malformed))
                            .build());
                    return null;
                });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            try {
                session.query(Annotations.version1(8, 4096L, 4096L));
                fail("Expected malformed annotation rejection");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.QUERY_FAILED,
                        failure.getCode());
                assertEquals(CAPABILITY, failure.getCapabilityId());
                assertEquals(
                        "The supported annotations could not be inspected safely.",
                        failure.getDiagnostic());
            }
            try {
                session.execute(UpdateAnnotations.version1()
                        .remove("missing-rectangle")
                        .build());
                fail("Expected malformed annotation removal rejection");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.ANNOTATION_INVALID,
                        failure.getCode());
                assertEquals(CAPABILITY, failure.getCapabilityId());
                assertEquals(
                        "The supported annotations could not be updated safely.",
                        failure.getDiagnostic());
            }
            try {
                session.execute(CopyPages.version1(
                        PageRange.of(1, 1), 2));
                fail("Expected page mutation to reject malformed annotation");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.PRESERVATION_UNSUPPORTED,
                        failure.getCode());
                assertEquals(
                        "document.page.manipulate-merge-split",
                        failure.getCapabilityId());
                assertEquals(
                        "The document contains structures that this page operation cannot preserve safely.",
                        failure.getDiagnostic());
            }
            try {
                session.query(PageObjectReference.version1(2));
                fail("The failed copy must not add a page");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.PAGE_RANGE_INVALID,
                        failure.getCode());
            }
            return null;
        });
    }

    @Test
    public void annotationUpdateNormalizesANonDictionaryEntryBeforeMutation()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "scalar-annotation-input.pdf");
        createBlankDocument(input);

        new DocumentWorkflow().execute(sourceRequest(input), session -> {
            session.execute(DocumentPatch.builder()
                    .setDictionaryEntry(
                            session.query(PageObjectReference.version1(1)),
                            PdfName.of("Annots"),
                            PdfArray.of(PdfName.of("not-an-annotation")))
                    .build());
            try {
                session.execute(UpdateAnnotations.version1()
                        .put(Annotation.text(
                                AnnotationProperties.version1(
                                                "must-not-be-added",
                                                1,
                                                AnnotationRectangle.of(
                                                        10L, 10L, 40L, 40L))
                                        .build(),
                                Annotation.TextIcon.NOTE,
                                false))
                        .build());
                fail("Expected malformed annotation entry rejection");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.ANNOTATION_INVALID,
                        failure.getCode());
                assertEquals(CAPABILITY, failure.getCapabilityId());
                assertEquals(
                        "The supported annotations could not be updated safely.",
                        failure.getDiagnostic());
            }
            PdfDictionary page = dictionaryValue(
                    session,
                    session.query(PageObjectReference.version1(1)));
            PdfArray annotations = (PdfArray) page.get(PdfName.of("Annots"));
            assertEquals(1, annotations.size());
            assertEquals(PdfName.of("not-an-annotation"), annotations.get(0));
            return null;
        });
    }

    @Test
    public void flattenNormalizesANonDictionaryEntryBeforeMutation()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "scalar-flatten-input.pdf");
        createBlankDocument(input);

        new DocumentWorkflow().execute(sourceRequest(input), session -> {
            session.execute(DocumentPatch.builder()
                    .setDictionaryEntry(
                            session.query(PageObjectReference.version1(1)),
                            PdfName.of("Annots"),
                            PdfArray.of(PdfName.of("not-an-annotation")))
                    .build());
            try {
                session.execute(FlattenAnnotations.version1(
                        "must-not-flatten"));
                fail("Expected malformed flattening entry rejection");
            } catch (DocumentFailure failure) {
                assertEquals(
                        DocumentFailureCode
                                .ANNOTATION_FLATTENING_UNSUPPORTED,
                        failure.getCode());
                assertEquals(CAPABILITY, failure.getCapabilityId());
                assertEquals(
                        "The annotation cannot be flattened safely under "
                                + "the version-1 contract.",
                        failure.getDiagnostic());
            }
            PdfDictionary page = dictionaryValue(
                    session,
                    session.query(PageObjectReference.version1(1)));
            PdfArray annotations = (PdfArray) page.get(PdfName.of("Annots"));
            assertEquals(1, annotations.size());
            assertEquals(PdfName.of("not-an-annotation"), annotations.get(0));
            return null;
        });
    }

    @Test
    public void annotationSubtypeRejectsAnIrrelevantActionBeforePageRewrite()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "irrelevant-action-input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "irrelevant-action-output.pdf");
        createBlankDocument(input);
        PdfDictionary action = PdfDictionary.builder()
                .put(PdfName.of("S"), PdfName.of("JavaScript"))
                .put(PdfName.of("JS"), PdfString.of(
                        "must-not-run".getBytes(StandardCharsets.US_ASCII)))
                .build();
        PdfDictionary text = PdfDictionary.builder()
                .put(PdfName.of("Type"), PdfName.of("Annot"))
                .put(PdfName.of("Subtype"), PdfName.of("Text"))
                .put(PdfName.of("Rect"), PdfArray.of(
                        PdfNumber.of(10L),
                        PdfNumber.of(10L),
                        PdfNumber.of(40L),
                        PdfNumber.of(40L)))
                .put(PdfName.of("NM"), PdfString.of(
                        "text-with-action".getBytes(
                                StandardCharsets.US_ASCII)))
                .put(PdfName.of("Name"), PdfName.of("Note"))
                .put(PdfName.of("A"), action)
                .build();

        new DocumentWorkflow().execute(rewriteRequest(input, output), session -> {
            session.execute(DocumentPatch.builder()
                    .setDictionaryEntry(
                            session.query(PageObjectReference.version1(1)),
                            PdfName.of("Annots"),
                            PdfArray.of(text))
                    .build());
            return null;
        });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            try {
                session.query(Annotations.version1(8, 4096L, 4096L));
                fail("Expected irrelevant Action rejection");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.QUERY_FAILED,
                        failure.getCode());
            }
            Annotation replacement = Annotation.text(
                    AnnotationProperties.version1(
                                    "text-with-action",
                                    1,
                                    AnnotationRectangle.of(
                                            10L, 10L, 40L, 40L))
                            .build(),
                    Annotation.TextIcon.NOTE,
                    false);
            try {
                session.execute(UpdateAnnotations.version1()
                        .remove("text-with-action")
                        .build());
                fail("Expected unsafe existing annotation removal rejection");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.ANNOTATION_INVALID,
                        failure.getCode());
            }
            try {
                session.execute(UpdateAnnotations.version1()
                        .put(replacement)
                        .build());
                fail("Expected unsafe existing annotation replacement rejection");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.ANNOTATION_INVALID,
                        failure.getCode());
            }
            try {
                session.execute(CopyPages.version1(
                        PageRange.of(1, 1), 2));
                fail("Expected page copy to preserve or reject the Action");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.PRESERVATION_UNSUPPORTED,
                        failure.getCode());
            }
            return null;
        });
    }

    @Test
    public void malformedAppearanceGraphFailsWithStableSafeDiagnostics()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "malformed-appearance-input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "malformed-appearance-output.pdf");
        createBlankDocument(input);
        PdfDictionary malformed = PdfDictionary.builder()
                .put(PdfName.of("Type"), PdfName.of("Annot"))
                .put(PdfName.of("Subtype"), PdfName.of("Text"))
                .put(PdfName.of("Rect"), PdfArray.of(
                        PdfNumber.of(10L),
                        PdfNumber.of(10L),
                        PdfNumber.of(40L),
                        PdfNumber.of(40L)))
                .put(PdfName.of("NM"), PdfString.of(
                        "bad-appearance".getBytes(
                                StandardCharsets.US_ASCII)))
                .put(PdfName.of("Name"), PdfName.of("Note"))
                .put(PdfName.of("AP"), PdfDictionary.builder()
                        .put(PdfName.of("N"), PdfDictionary.builder()
                                .put(PdfName.of("NotAStream"),
                                        PdfName.of("true"))
                                .build())
                        .build())
                .build();

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(
                                    session.query(
                                            PageObjectReference.version1(1)),
                                    PdfName.of("Annots"),
                                    PdfArray.of(malformed))
                            .build());
                    return null;
                });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            try {
                session.query(Annotations.version1(8, 4096L, 4096L));
                fail("Expected malformed appearance rejection");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.QUERY_FAILED,
                        failure.getCode());
                assertEquals(CAPABILITY, failure.getCapabilityId());
                assertEquals(
                        "The supported annotations could not be inspected safely.",
                        failure.getDiagnostic());
            }
            try {
                session.execute(MovePages.version1(
                        PageRange.of(1, 1), 1));
                fail("Expected page mutation to reject malformed appearance");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.PRESERVATION_UNSUPPORTED,
                        failure.getCode());
            }
            return null;
        });
    }

    @Test
    public void commandsAndQueriesFailAtomicallyAtDeclaredSafetyBounds()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "annotation-bounds-input.pdf");
        createBlankDocument(input);

        new DocumentWorkflow().execute(sourceRequest(input), session -> {
            Annotation unsafeAppearance = Annotation.stamp(
                    AnnotationProperties.version1(
                                    "unsafe-appearance",
                                    1,
                                    AnnotationRectangle.of(
                                            10L, 10L, 50L, 50L))
                            .appearance(AnnotationAppearance.version1(
                                    AnnotationRectangle.of(
                                            0L, 0L, 40L, 40L),
                                    "/External Do\n".getBytes(
                                            StandardCharsets.US_ASCII)))
                            .build(),
                    "Draft");
            try {
                session.execute(UpdateAnnotations.version1()
                        .put(unsafeAppearance)
                        .build());
                fail("Expected resource-referencing appearance rejection");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.ANNOTATION_INVALID,
                        failure.getCode());
                assertEquals(CAPABILITY, failure.getCapabilityId());
            }
            assertEquals(Collections.<Annotation>emptyList(),
                    session.query(Annotations.version1(
                            8, 4096L, 4096L)));

            String[] invalidGraphicsPrograms = {
                "-1 w\n",
                "9 J\n",
                "[0 0] 0 d\n"
            };
            for (int index = 0;
                    index < invalidGraphicsPrograms.length;
                    index++) {
                Annotation invalidGraphics = Annotation.stamp(
                        AnnotationProperties.version1(
                                        "invalid-graphics-" + index,
                                        1,
                                        AnnotationRectangle.of(
                                                10L, 10L, 50L, 50L))
                                .appearance(AnnotationAppearance.version1(
                                        AnnotationRectangle.of(
                                                0L, 0L, 40L, 40L),
                                        invalidGraphicsPrograms[index]
                                                .getBytes(StandardCharsets
                                                        .US_ASCII)))
                                .build(),
                        "Draft");
                try {
                    session.execute(UpdateAnnotations.version1()
                            .put(invalidGraphics)
                            .build());
                    fail("Expected malformed graphics operand rejection");
                } catch (DocumentFailure failure) {
                    assertEquals(DocumentFailureCode.ANNOTATION_INVALID,
                            failure.getCode());
                }
            }
            assertEquals(Collections.<Annotation>emptyList(),
                    session.query(Annotations.version1(
                            8, 4096L, 4096L)));

            try {
                session.execute(UpdateAnnotations.version1()
                        .put(Annotation.link(
                                AnnotationProperties.version1(
                                                "missing-named-link",
                                                1,
                                                AnnotationRectangle.of(
                                                        10L, 10L, 50L, 30L))
                                        .build(),
                                LinkActivation.destination(
                                        NavigationTarget
                                                .toNamedDestination(
                                                        "missing"))))
                        .build());
                fail("Expected unresolved named Link rejection");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.ANNOTATION_INVALID,
                        failure.getCode());
            }

            try {
                session.execute(UpdateActions.version1()
                        .setDocumentOpenAction(GoToAction.version1(
                                NavigationTarget.toPage(
                                        PageDestination.fit(2))))
                        .build());
                fail("Expected out-of-range Action rejection");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.ACTION_INVALID,
                        failure.getCode());
                assertEquals(CAPABILITY, failure.getCapabilityId());
            }
            try {
                session.execute(UpdateActions.version1()
                        .setDocumentOpenAction(GoToAction.version1(
                                NavigationTarget.toNamedDestination(
                                        "missing")))
                        .build());
                fail("Expected unresolved named Action rejection");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.ACTION_INVALID,
                        failure.getCode());
            }

            Annotation widget = Annotation.widget(
                    AnnotationProperties.version1(
                                    "bounded-widget",
                                    1,
                                    AnnotationRectangle.of(
                                            20L, 20L, 60L, 60L))
                            .appearance(AnnotationAppearance.version1(
                                    AnnotationRectangle.of(
                                            0L, 0L, 40L, 40L),
                                    "q 0 0 40 40 re S Q\n".getBytes(
                                            StandardCharsets.US_ASCII)))
                            .build());
            session.execute(UpdateAnnotations.version1()
                    .put(widget)
                    .build());
            session.execute(UpdateActions.version1()
                    .setDocumentOpenAction(GoToAction.version1(
                            NavigationTarget.toPage(
                                    PageDestination.fit(1))))
                    .build());

            try {
                session.query(Annotations.version1(0, 4096L, 4096L));
                fail("Expected annotation count bound rejection");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.ANNOTATION_LIMIT_EXCEEDED,
                        failure.getCode());
            }
            try {
                session.query(Annotations.version1(8, 0L, 4096L));
                fail("Expected appearance byte bound rejection");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.ANNOTATION_LIMIT_EXCEEDED,
                        failure.getCode());
            }
            try {
                session.query(Actions.version1(0));
                fail("Expected Action count bound rejection");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.ACTION_LIMIT_EXCEEDED,
                        failure.getCode());
            }
            try {
                session.execute(FlattenAnnotations.version1(
                        "bounded-widget"));
                fail("Expected Widget flattening rejection");
            } catch (DocumentFailure failure) {
                assertEquals(
                        DocumentFailureCode
                                .ANNOTATION_FLATTENING_UNSUPPORTED,
                        failure.getCode());
            }
            assertEquals("bounded-widget",
                    session.query(Annotations.version1(
                                    8, 4096L, 4096L))
                            .get(0).getProperties().getIdentifier());
            return null;
        });
    }

    @Test
    public void pageOperationsBoundDecodedAttachmentMemory()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "bounded-page-attachment-input.pdf");
        createBlankDocument(input);
        byte[] oversized = new byte[8 * 1024 * 1024 + 1];

        new DocumentWorkflow().execute(sourceRequest(input), session -> {
            session.execute(UpdateAnnotations.version1()
                    .put(Annotation.fileAttachment(
                            AnnotationProperties.version1(
                                            "oversized-attachment",
                                            1,
                                            AnnotationRectangle.of(
                                                    10L, 10L, 40L, 40L))
                                    .build(),
                            EmbeddedFile.version1("oversized.bin", oversized),
                            Annotation.FileAttachmentIcon.PAPERCLIP))
                    .build());
            try {
                session.execute(CopyPages.version1(
                        PageRange.of(1, 1), 2));
                fail("Expected bounded page-operation decoding");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.PRESERVATION_UNSUPPORTED,
                        failure.getCode());
                assertEquals("document.page.manipulate-merge-split",
                        failure.getCapabilityId());
            }
            try {
                session.query(PageObjectReference.version1(2));
                fail("The failed copy must not add a page");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.PAGE_RANGE_INVALID,
                        failure.getCode());
            }
            return null;
        });
    }

    @Test
    public void pageOperationsBoundAggregateDecodedAppearanceMemory()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "bounded-page-appearance-input.pdf");
        createBlankDocument(input);
        byte[] appearanceContent = new byte[1024 * 1024];
        Arrays.fill(appearanceContent, (byte) ' ');

        new DocumentWorkflow().execute(sourceRequest(input), session -> {
            UpdateAnnotations.Builder update = UpdateAnnotations.version1();
            for (int index = 0; index < 9; index++) {
                update.put(Annotation.stamp(
                        AnnotationProperties.version1(
                                        "bounded-appearance-" + index,
                                        1,
                                        AnnotationRectangle.of(
                                                10L, 10L, 40L, 40L))
                                .appearance(AnnotationAppearance.version1(
                                        AnnotationRectangle.of(
                                                0L, 0L, 30L, 30L),
                                        appearanceContent))
                                .build(),
                        "Approved"));
            }
            session.execute(update.build());
            try {
                session.execute(CopyPages.version1(
                        PageRange.of(1, 1), 2));
                fail("Expected aggregate appearance decoding bound");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.PRESERVATION_UNSUPPORTED,
                        failure.getCode());
                assertEquals("document.page.manipulate-merge-split",
                        failure.getCapabilityId());
            }
            try {
                session.query(PageObjectReference.version1(2));
                fail("The failed copy must not add a page");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.PAGE_RANGE_INVALID,
                        failure.getCode());
            }
            return null;
        });
    }

    @Test
    public void pagePreservationSharesAppearanceBudgetAcrossPages()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "bounded-multipage-appearance-input.pdf");
        createBlankDocument(input, 9);
        byte[] appearanceContent = new byte[1024 * 1024];
        Arrays.fill(appearanceContent, (byte) ' ');

        new DocumentWorkflow().execute(sourceRequest(input), session -> {
            UpdateAnnotations.Builder update = UpdateAnnotations.version1();
            for (int pageNumber = 1; pageNumber <= 9; pageNumber++) {
                update.put(Annotation.stamp(
                        AnnotationProperties.version1(
                                        "multipage-appearance-" + pageNumber,
                                        pageNumber,
                                        AnnotationRectangle.of(
                                                10L, 10L, 40L, 40L))
                                .appearance(AnnotationAppearance.version1(
                                        AnnotationRectangle.of(
                                                0L, 0L, 30L, 30L),
                                        appearanceContent))
                                .build(),
                        "Approved"));
            }
            session.execute(update.build());
            try {
                session.execute(InsertBlankPage.version1(10));
                fail("Expected a document-wide preservation decode bound");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.PRESERVATION_UNSUPPORTED,
                        failure.getCode());
                assertEquals("document.page.manipulate-merge-split",
                        failure.getCapabilityId());
            }
            try {
                session.query(PageObjectReference.version1(10));
                fail("The failed insertion must not add a page");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.PAGE_RANGE_INVALID,
                        failure.getCode());
            }
            return null;
        });
    }

    private static void createBlankDocument(Path target) throws Exception {
        createBlankDocument(target, 1);
    }

    private static PdfStream streamValue(
            DocumentSession session,
            PdfValue value) throws DocumentFailure {
        if (value instanceof PdfIndirectReference) {
            return (PdfStream) session.query(InspectObject.version1(
                    ((PdfIndirectReference) value).getReference(),
                    PdfInspectionLimits.of(16, 4096L)));
        }
        return (PdfStream) value;
    }

    private static PdfDictionary dictionaryValue(
            DocumentSession session,
            Object value) throws DocumentFailure {
        if (value instanceof ObjectReference) {
            return (PdfDictionary) session.query(InspectObject.version1(
                    (ObjectReference) value,
                    PdfInspectionLimits.of(32, 4096L)));
        }
        if (value instanceof PdfIndirectReference) {
            return (PdfDictionary) session.query(InspectObject.version1(
                    ((PdfIndirectReference) value).getReference(),
                    PdfInspectionLimits.of(32, 4096L)));
        }
        return (PdfDictionary) value;
    }

    private static PdfDictionary legacyText(String contents) {
        return legacyText(contents, null);
    }

    private static PdfDictionary legacyText(
            String contents,
            String identifier) {
        PdfDictionary.Builder builder = PdfDictionary.builder()
                .put(PdfName.of("Type"), PdfName.of("Annot"))
                .put(PdfName.of("Subtype"), PdfName.of("Text"))
                .put(PdfName.of("Rect"), PdfArray.of(
                        PdfNumber.of(10L),
                        PdfNumber.of(10L),
                        PdfNumber.of(40L),
                        PdfNumber.of(40L)))
                .put(PdfName.of("Contents"), PdfString.of(
                        contents.getBytes(StandardCharsets.UTF_8)))
                .put(PdfName.of("M"), PdfString.of(
                        "D:20260901000000Z".getBytes(
                                StandardCharsets.US_ASCII)));
        if (identifier != null) {
            builder.put(PdfName.of("NM"), PdfString.of(
                    identifier.getBytes(StandardCharsets.US_ASCII)));
        }
        return builder.build();
    }

    private static void createBlankDocument(Path target, int pageCount)
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

    private static WorkflowRequest rewriteRequest(Path input, Path output) {
        return WorkflowRequest.builder()
                .source("input", DocumentSource.path(input))
                .primarySource("input")
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE)
                .build();
    }

    private static WorkflowRequest sourceRequest(Path input) {
        return WorkflowRequest.builder()
                .source("input", DocumentSource.path(input))
                .primarySource("input")
                .saveMode(SaveMode.REWRITE)
                .build();
    }
}
