package net.zerocloud.pdf.itext7.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.zerocloud.pdf.Annotation;
import net.zerocloud.pdf.AnnotationProperties;
import net.zerocloud.pdf.AnnotationAppearance;
import net.zerocloud.pdf.AnnotationColor;
import net.zerocloud.pdf.AnnotationFlag;
import net.zerocloud.pdf.AnnotationQuad;
import net.zerocloud.pdf.AnnotationRectangle;
import net.zerocloud.pdf.EmbeddedFile;
import net.zerocloud.pdf.GoToAction;
import net.zerocloud.pdf.LinkActivation;
import net.zerocloud.pdf.NavigationTarget;
import net.zerocloud.pdf.PageDestination;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.DocumentActions;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfPage;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfName;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfArray;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfStream;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfString;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDictionary;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfCatalog;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;
import net.zerocloud.pdf.query.Annotations;
import net.zerocloud.pdf.query.Actions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class AnnotationFacadeTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void documentAnnotationChangesPublishAndReopenThroughBothInterfaces() throws Exception {
        Path source = temporary.getRoot().toPath().resolve("annotations.pdf");
        Path output = temporary.getRoot().toPath().resolve("changed.pdf");
        Annotation original = Annotation.text(AnnotationProperties.version1("note", 1,
                AnnotationRectangle.of(10, 20, 40, 50)).contents("Original").build(),
                Annotation.TextIcon.NOTE, false);
        PdfDocument created = new PdfDocument(new PdfWriter(source.toString()));
        try {
            created.addNewPage();
            created.addNewPage();
            created.updateAnnotations(Collections.singletonList(original), Collections.<String>emptyList());
            assertEquals(Collections.singletonList(original), created.getAnnotations(1, 0, 0));
        } finally {
            created.close();
        }
        assertEquals(PublicationStatus.COMMITTED, created.getPublicationReceipts().get(0).getStatus());
        assertReopened(source, original);

        Annotation changed = Annotation.text(AnnotationProperties.version1("note", 2,
                AnnotationRectangle.of(30, 40, 80, 90)).contents("Changed").build(),
                Annotation.TextIcon.HELP, true);
        PdfDocument edited = new PdfDocument(new PdfReader(source.toString()), new PdfWriter(output.toString()));
        try {
            edited.updateAnnotations(Collections.singletonList(changed), Collections.<String>emptyList());
        } finally {
            edited.close();
        }
        assertEquals(PublicationStatus.COMMITTED, edited.getPublicationReceipts().get(0).getStatus());
        assertReopened(output, changed);
        assertReopened(source, original);
    }

    @Test
    public void pageAnnotationEditsFollowThePageHandleAfterReordering() throws Exception {
        Path output = temporary.getRoot().toPath().resolve("page-annotations.pdf");
        Annotation supplied = Annotation.text(AnnotationProperties.version1("note", 1,
                AnnotationRectangle.of(10, 20, 40, 50)).build(), Annotation.TextIcon.NOTE, false);
        Annotation expected = Annotation.text(AnnotationProperties.version1("note", 2,
                AnnotationRectangle.of(10, 20, 40, 50)).build(), Annotation.TextIcon.NOTE, false);
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            PdfPage retained = document.addNewPage();
            document.addNewPage();
            document.movePage(1, 2);
            assertSame(retained, retained.addAnnotation(supplied));
            assertEquals(Collections.singletonList(expected), retained.getAnnotations(1, 0, 0));
            assertTrue(document.getPage(1).getAnnotations(1, 0, 0).isEmpty());
            assertEquals(Collections.singletonList(expected), document.getPage(2).getAnnotations(1, 0, 0));
        }
        assertReopened(output, expected);
    }

    @Test
    public void everyFamilyRetainsItsPropertiesWhenOnlyItsAppearanceChanges() throws Exception {
        Path source = temporary.getRoot().toPath().resolve("six-families.pdf");
        Path output = temporary.getRoot().toPath().resolve("six-changed.pdf");
        AnnotationAppearance original = appearance("0 0 1 rg 2 3 10 10 re f\n");
        AnnotationAppearance changed = appearance("1 0 0 rg 2 3 10 10 re f\n");
        List<Annotation> before = families(original);
        try (PdfDocument document = new PdfDocument(new PdfWriter(source.toString()))) {
            PdfPage page = document.addNewPage();
            for (Annotation annotation : before) {
                page.addAnnotation(annotation);
            }
        }
        assertReopened(source, before);
        List<Annotation> after = families(changed);
        PdfDocument document = new PdfDocument(new PdfReader(source.toString()), new PdfWriter(output.toString()));
        try {
            PdfPage page = document.getPage(1);
            for (Annotation annotation : before) {
                assertSame(page, page.setNormalAppearance(annotation.getProperties().getIdentifier(), changed));
            }
            assertEquals(after, page.getAnnotations(8, 4096, 4096));
        } finally {
            document.close();
        }
        assertEquals(PublicationStatus.COMMITTED, document.getPublicationReceipts().get(0).getStatus());
        assertReopened(output, after);
        assertReopened(source, before);
    }

    @Test
    public void localActionsRemainBoundedAndFollowPageIdentityThroughPublication() throws Exception {
        Path output = temporary.getRoot().toPath().resolve("actions.pdf");
        GoToAction named = GoToAction.version1(NavigationTarget.toNamedDestination("chapter"));
        GoToAction explicit = GoToAction.version1(NavigationTarget.toPage(PageDestination.fit(2)));
        DocumentActions expected;
        PdfDocument document = new PdfDocument(new PdfWriter(output.toString()));
        try {
            PdfPage page = document.addNewPage();
            document.addNewPage();
            document.addNewPage();
            document.addNamedDestination("chapter", PageDestination.fit(3));
            assertSame(document.getCatalog(), document.getCatalog().setOpenAction(named));
            assertSame(page, page.setAdditionalAction(new PdfName("O"), explicit));
            assertSame(page, page.setAdditionalAction(new PdfName("C"), named));
            assertEquals(named, document.getCatalog().getOpenAction(3).get());
            assertEquals(explicit, page.getAdditionalActions(3).get().getOpenAction().get());
            expectFailure(DocumentFailureCode.ACTION_LIMIT_EXCEEDED, () -> document.getActions(2));
            expectFailure(DocumentFailureCode.ACTION_INVALID, () -> document.getCatalog().setOpenAction(
                    GoToAction.version1(NavigationTarget.toNamedDestination("absent"))));
            assertEquals(named, document.getCatalog().getOpenAction(3).get());
            document.movePage(3, 1);
            assertEquals(2, page.getAdditionalActions(3).get().getPageNumber());
            assertEquals(PageDestination.fit(3), page.getAdditionalActions(3).get().getOpenAction().get()
                    .getTarget().getPageDestination().get());
            assertEquals(PageDestination.fit(1), document.getNamedDestinations(1).get("chapter"));
            expected = document.getActions(3);
        } finally {
            document.close();
        }
        assertEquals(PublicationStatus.COMMITTED, document.getPublicationReceipts().get(0).getStatus());
        assertEquals(expected, new DocumentWorkflow().execute(WorkflowRequest.open(output, SaveMode.REWRITE),
                session -> session.query(Actions.version1(3))).getResult());
        try (PdfDocument reopened = new PdfDocument(new PdfReader(output.toString()))) {
            assertEquals(expected, reopened.getActions(3));
            assertFalse(reopened.getPage(1).getAdditionalActions(3).isPresent());
        }
    }

    @Test
    public void flatteningRejectsWidgetsAtomicallyThenRetainsPaintAndRemovesOnlyTheSelection() throws Exception {
        Path output = temporary.getRoot().toPath().resolve("flattened.pdf");
        String paint = "1 0 0 rg 10 20 40 30 re f\n2 0 0 2 13 17 cm\n0 0 1 1 re W n\n";
        AnnotationAppearance appearance = appearance("0 1 0 rg 2 3 10 10 re f\n");
        Annotation stamp = Annotation.stamp(AnnotationProperties.version1("stamp", 1,
                AnnotationRectangle.of(10, 20, 40, 50)).appearance(appearance).build(), "Approved");
        Annotation widget = Annotation.widget(AnnotationProperties.version1("widget", 1,
                AnnotationRectangle.of(60, 20, 90, 50)).appearance(appearance).build());
        PdfDocument document = new PdfDocument(new PdfWriter(output.toString()));
        try {
            PdfPage page = document.addNewPage();
            page.getPdfObject().put(new PdfName("Contents"), new PdfStream(paint.getBytes(StandardCharsets.US_ASCII)));
            page.addAnnotation(stamp).addAnnotation(widget);
            expectFailure(DocumentFailureCode.ANNOTATION_FLATTENING_UNSUPPORTED,
                    () -> document.flattenAnnotations("stamp", "widget"));
            assertEquals(Arrays.asList(stamp, widget), page.getAnnotations(2, 4096, 0));
            assertEquals(paint, new String(((PdfStream) page.getPdfObject().get(new PdfName("Contents"))).getBytes(),
                    StandardCharsets.US_ASCII));
            expectFailure(DocumentFailureCode.ANNOTATION_NOT_FOUND, () -> document.flattenAnnotations("absent"));
            document.flattenAnnotations("stamp");
            assertEquals(Collections.singletonList(widget), document.getAnnotations(1, 4096, 0));
        } finally {
            document.close();
        }
        assertEquals(PublicationStatus.COMMITTED, document.getPublicationReceipts().get(0).getStatus());
        assertReopened(output, widget);
        try (PdfDocument reopened = new PdfDocument(new PdfReader(output.toString()))) {
            PdfArray content = (PdfArray) reopened.getPage(1).getPdfObject().get(new PdfName("Contents"));
            StringBuilder program = new StringBuilder();
            for (int index = 0; index < content.size(); index++) {
                program.append(new String(((PdfStream) content.get(index)).getBytes(), StandardCharsets.US_ASCII));
            }
            assertTrue(program.toString().contains(paint));
            assertTrue(program.toString().contains(" Do"));
            assertFalse(reopened.getCatalog().getPdfObject().containsKey(new PdfName("AcroForm")));
        }
    }

    @Test
    public void pageRemovalCannotSelectAnotherPageAndDocumentRemovalKeepsNativeFailures() throws Exception {
        Path output = temporary.getRoot().toPath().resolve("removed.pdf");
        Annotation note = Annotation.text(AnnotationProperties.version1("note", 1,
                AnnotationRectangle.of(10, 20, 40, 50)).build(), Annotation.TextIcon.NOTE, false);
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            PdfPage page = document.addNewPage();
            PdfPage other = document.addNewPage();
            page.addAnnotation(note);
            try {
                other.removeAnnotation("note");
                fail("A page cannot remove another page's annotation");
            } catch (IllegalArgumentException expected) {
                assertEquals(Collections.singletonList(note), document.getAnnotations(1, 0, 0));
            }
            expectFailure(DocumentFailureCode.ANNOTATION_NOT_FOUND, () -> document.updateAnnotations(
                    Collections.<Annotation>emptyList(), Arrays.asList("note", "absent")));
            assertEquals(Collections.singletonList(note), document.getAnnotations(1, 0, 0));
            assertSame(page, page.removeAnnotation("note"));
            assertTrue(document.getAnnotations(0, 0, 0).isEmpty());
        }
        assertReopened(output, Collections.<Annotation>emptyList());
    }

    @Test
    public void appearanceAndAttachmentBoundsAndInvalidBatchesLeaveEveryAnnotationUnchanged() throws Exception {
        Path output = temporary.getRoot().toPath().resolve("bounded.pdf");
        List<Annotation> expected = families(appearance("0 1 0 rg 2 3 10 10 re f\n"));
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            PdfPage page = document.addNewPage();
            document.updateAnnotations(expected, Collections.<String>emptyList());
            expectFailure(DocumentFailureCode.ANNOTATION_LIMIT_EXCEEDED, () -> document.getAnnotations(5, 4096, 4096));
            expectFailure(DocumentFailureCode.ANNOTATION_LIMIT_EXCEEDED, () -> page.getAnnotations(6, 0, 4096));
            expectFailure(DocumentFailureCode.ANNOTATION_LIMIT_EXCEEDED, () -> page.getAnnotations(6, 4096, 2));
            for (String program : new String[] {"/Private Do\n", "-1 w\n", "3 J\n", "[0 0] 0 d\n", "q\n"}) {
                expectFailure(DocumentFailureCode.ANNOTATION_INVALID,
                        () -> page.setNormalAppearance("STAMP", appearance(program)));
                assertEquals(expected, document.getAnnotations(6, 4096, 3));
            }
            Annotation good = Annotation.text(AnnotationProperties.version1("new", 1,
                    AnnotationRectangle.of(10, 20, 40, 50)).build(), Annotation.TextIcon.NOTE, false);
            Annotation invalid = Annotation.stamp(AnnotationProperties.version1("invalid", 1,
                    AnnotationRectangle.of(10, 20, 40, 50)).appearance(appearance("BT ET\n")).build(), "Draft");
            expectFailure(DocumentFailureCode.ANNOTATION_INVALID, () -> document.updateAnnotations(
                    Arrays.asList(good, invalid), Collections.<String>emptyList()));
            assertEquals(expected, document.getAnnotations(6, 4096, 3));
            Annotation last = Annotation.text(AnnotationProperties.version1("new", 1,
                    AnnotationRectangle.of(10, 20, 40, 50)).contents("Last replacement").build(),
                    Annotation.TextIcon.HELP, true);
            document.updateAnnotations(Arrays.asList(good, last), Collections.<String>emptyList());
            List<Annotation> withReplacement = new ArrayList<Annotation>(expected);
            withReplacement.add(last);
            assertEquals(withReplacement, document.getAnnotations(7, 4096, 3));
            document.updateAnnotations(Collections.<Annotation>emptyList(), Collections.singletonList("new"));
            assertEquals(expected, document.getAnnotations(6, 4096, 3));
        }
        assertReopened(output, expected);
    }

    @Test
    public void unknownAndChainedActionsArePreservedOrRejectedUntilExplicitlyReplaced() throws Exception {
        Path source = temporary.getRoot().toPath().resolve("inert-source.pdf");
        Path output = temporary.getRoot().toPath().resolve("inert-output.pdf");
        PdfDictionary chained = new PdfDictionary();
        chained.put(new PdfName("S"), new PdfName("JavaScript"));
        chained.put(new PdfName("JS"), new PdfString("throw new Error('must stay inert')"));
        PdfDictionary next = new PdfDictionary();
        next.put(new PdfName("S"), new PdfName("URI"));
        next.put(new PdfName("URI"), new PdfString("https://invalid.example/must-stay-inert"));
        chained.put(new PdfName("Next"), next);
        try (PdfDocument document = new PdfDocument(new PdfWriter(source.toString()))) {
            document.addNewPage();
            document.getCatalog().getPdfObject().put(new PdfName("OpenAction"), chained);
        }
        Annotation note = Annotation.text(AnnotationProperties.version1("note", 1,
                AnnotationRectangle.of(10, 20, 40, 50)).build(), Annotation.TextIcon.NOTE, false);
        try (PdfDocument document = new PdfDocument(new PdfReader(source.toString()), new PdfWriter(output.toString()))) {
            document.getPage(1).addAnnotation(note);
            expectFailure(DocumentFailureCode.QUERY_FAILED, () -> document.getActions(8));
            try {
                document.copyPages(1, 1, 2);
                fail("An Action requiring interpretation must reject the page rewrite");
            } catch (PdfException failure) {
                assertEquals(DocumentFailureCode.PRESERVATION_UNSUPPORTED,
                        ((DocumentFailure) failure.getCause()).getCode());
            }
            assertEquals(1, document.getNumberOfPages());
        }
        assertReopened(output, note);
        try (PdfDocument reopened = new PdfDocument(new PdfReader(output.toString()))) {
            PdfDictionary observed = (PdfDictionary) reopened.getCatalog().getPdfObject().get(new PdfName("OpenAction"));
            assertEquals("JavaScript", ((PdfName) observed.get(new PdfName("S"))).getValue());
            assertEquals("throw new Error('must stay inert')", ((PdfString) observed.get(new PdfName("JS"))).getValue());
            assertEquals("https://invalid.example/must-stay-inert", ((PdfString) ((PdfDictionary)
                    observed.get(new PdfName("Next"))).get(new PdfName("URI"))).getValue());
        }
        Path cleared = temporary.getRoot().toPath().resolve("cleared.pdf");
        try (PdfDocument document = new PdfDocument(new PdfReader(output.toString()), new PdfWriter(cleared.toString()))) {
            PdfCatalog catalog = document.getCatalog();
            GoToAction local = GoToAction.version1(NavigationTarget.toPage(PageDestination.fit(1)));
            catalog.setOpenAction(local);
            PdfPage page = document.getPage(1);
            page.setAdditionalAction(new PdfName("O"), local).setAdditionalAction(new PdfName("C"), local);
            DocumentActions retained = document.getActions(3);
            try {
                page.setAdditionalAction(new PdfName("AA"), local);
                fail("An unsupported event must not replace supported bindings");
            } catch (IllegalArgumentException expected) {
                assertEquals(retained, document.getActions(3));
            }
            assertSame(page, page.setAdditionalAction(new PdfName("O"), null).setAdditionalAction(new PdfName("C"), null));
            assertSame(catalog, catalog.setOpenAction(null));
            assertFalse(catalog.getOpenAction(0).isPresent());
            assertFalse(page.getAdditionalActions(0).isPresent());
        }
        try (PdfDocument reopened = new PdfDocument(new PdfReader(cleared.toString()))) {
            assertFalse(reopened.getActions(0).getDocumentOpenAction().isPresent());
            assertTrue(reopened.getActions(0).getPageActions().isEmpty());
        }
    }

    @Test
    public void annotationPageAndCatalogOperationsRespectReadOnlyAndExpiredDocumentLifetimes() throws Exception {
        Path source = temporary.getRoot().toPath().resolve("lifetimes.pdf");
        Annotation note = Annotation.text(AnnotationProperties.version1("note", 1,
                AnnotationRectangle.of(10, 20, 40, 50)).appearance(appearance("0 g 2 3 10 10 re f\n"))
                .build(), Annotation.TextIcon.NOTE, false);
        try (PdfDocument created = new PdfDocument(new PdfWriter(source.toString()))) {
            created.addNewPage().addAnnotation(note);
        }
        PdfDocument readOnly = new PdfDocument(new PdfReader(source.toString()));
        PdfPage page = readOnly.getPage(1);
        PdfCatalog catalog = readOnly.getCatalog();
        for (Runnable operation : Arrays.<Runnable>asList(
                () -> page.addAnnotation(note), () -> page.removeAnnotation("note"),
                () -> page.setNormalAppearance("note", appearance("0 g 2 3 10 10 re f\n")),
                () -> catalog.setOpenAction(null), () -> page.setAdditionalAction(new PdfName("O"), null),
                () -> readOnly.flattenAnnotations("note"))) {
            expectIllegalState(operation);
        }
        assertEquals(Collections.singletonList(note), readOnly.getAnnotations(1, 4096, 0));
        readOnly.close();
        for (Runnable operation : Arrays.<Runnable>asList(
                () -> readOnly.getAnnotations(1, 4096, 0), () -> page.getAnnotations(1, 4096, 0),
                () -> catalog.getOpenAction(1), () -> page.getAdditionalActions(1),
                () -> page.addAnnotation(note))) {
            expectIllegalState(operation);
        }
        assertReopened(source, note);
    }

    private static void expectIllegalState(Runnable operation) {
        try {
            operation.run();
            fail("Expected an unavailable document view");
        } catch (IllegalStateException expected) {
            assertFalse(expected.getMessage().isEmpty());
        }
    }

    private static DocumentFailure expectFailure(DocumentFailureCode code, Runnable operation) {
        try {
            operation.run();
            fail("Expected " + code);
            return null;
        } catch (PdfException failure) {
            assertTrue(failure.getCause() instanceof DocumentFailure);
            DocumentFailure cause = (DocumentFailure) failure.getCause();
            assertEquals(code, cause.getCode());
            assertEquals("document.annotations-actions.manage", cause.getCapabilityId());
            return cause;
        }
    }

    private static AnnotationAppearance appearance(String program) {
        return AnnotationAppearance.version1(AnnotationRectangle.of(2, 3, 12, 13),
                program.getBytes(StandardCharsets.US_ASCII));
    }

    private static List<Annotation> families(AnnotationAppearance appearance) {
        List<Annotation> result = new ArrayList<Annotation>();
        for (Annotation.Type type : Annotation.Type.values()) {
            AnnotationProperties properties = AnnotationProperties.version1(type.name(), 1,
                    AnnotationRectangle.of(10, 20, 40, 50)).contents("Retained " + type.name())
                    .flag(AnnotationFlag.PRINT).appearance(appearance).build();
            switch (type) {
                case TEXT:
                    result.add(Annotation.text(properties, Annotation.TextIcon.HELP, true));
                    break;
                case STAMP:
                    result.add(Annotation.stamp(properties, "Approved"));
                    break;
                case HIGHLIGHT:
                    result.add(Annotation.highlight(properties, Arrays.asList(
                            AnnotationQuad.of(10, 50, 40, 50, 10, 20, 40, 20)),
                            AnnotationColor.rgb(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO)));
                    break;
                case FILE_ATTACHMENT:
                    result.add(Annotation.fileAttachment(properties, EmbeddedFile.version1("payload.txt",
                            new byte[] {0, 1, (byte) 255}, "text/plain", "Payload",
                            EmbeddedFile.Relationship.DATA), Annotation.FileAttachmentIcon.TAG));
                    break;
                case WIDGET:
                    result.add(Annotation.widget(properties));
                    break;
                case LINK:
                    result.add(Annotation.link(properties, LinkActivation.action(GoToAction.version1(
                            NavigationTarget.toPage(PageDestination.fit(1))))));
                    break;
                default:
                    throw new AssertionError(type);
            }
        }
        return result;
    }

    private static void assertReopened(Path source, Annotation expected) throws Exception {
        assertReopened(source, Collections.singletonList(expected));
    }

    private static void assertReopened(Path source, List<Annotation> expected) throws Exception {
        List<Annotation> annotations = new DocumentWorkflow().execute(WorkflowRequest.open(source, SaveMode.REWRITE),
                session -> session.query(Annotations.version1(8, 4096, 4096))).getResult();
        assertEquals(expected, annotations);
        try (PdfDocument document = new PdfDocument(new PdfReader(source.toString()))) {
            assertEquals(annotations, document.getAnnotations(8, 4096, 4096));
        }
    }
}
