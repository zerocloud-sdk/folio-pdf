package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import net.zerocloud.pdf.Annotation;
import net.zerocloud.pdf.AnnotationProperties;
import net.zerocloud.pdf.AnnotationRectangle;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfString;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.command.FlattenAnnotations;
import net.zerocloud.pdf.command.UpdateAnnotations;
import net.zerocloud.pdf.command.UpdateActions;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageObjectReference;
import net.zerocloud.pdf.query.PageCount;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Public behavior against separately authored PDF bytes and literal permissions. */
public final class IncrementalSignatureMatrixTest {
    private static final String CAPABILITY = "document.incremental-signature.protect";
    private static final byte[] SENTINEL = {31, 41, 59};
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void everyAuthoredRestrictionHasItsDeclaredStructuralOrPermissionOutcome() throws Exception {
        Properties cases = new Properties();
        try (InputStream input = Files.newInputStream(corpus().resolve("cases.properties"))) { cases.load(input); }
        assertEquals(88, cases.size());
        for (String name : new TreeSet<String>(cases.stringPropertyNames())) {
            String permission = cases.getProperty(name);
            Path source = corpus().resolve(name + ".pdf");
            byte[] original = Files.readAllBytes(source);
            Path target = temporary.getRoot().toPath().resolve(name + ".pdf");
            Files.write(target, SENTINEL);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            AtomicBoolean ran = new AtomicBoolean();
            try {
                WorkflowOutcome<Integer> result = new DocumentWorkflow().execute(request(source)
                        .target("path", PublicationTarget.path(target))
                        .target("stream", PublicationTarget.stream(stream)).build(), session -> {
                            ran.set(true);
                            assertEquals(name, Integer.valueOf(2), session.query(PageCount.INSTANCE));
                            if ("denied".equals(permission)) {
                                for (DocumentCommand command : annotationChanges()) {
                                    try {
                                        session.execute(command);
                                        fail(name + " must deny an otherwise permitted annotation update");
                                    } catch (DocumentFailure failure) {
                                        assertPolicyFailure(name, failure);
                                    }
                                }
                            } else {
                                session.execute(UpdateAnnotations.version1().put(note("added", 1)).build());
                            }
                            return session.query(PageCount.INSTANCE);
                        });
                assertTrue(name, "unsigned".equals(permission) || "annotations".equals(permission));
                assertEquals(execution(), result.getExecutionProfile());
                assertEquals(SaveMode.INCREMENTAL, result.getSaveMode());
                assertEquals(CAPABILITY, result.getCapabilityId());
                assertEquals(2, result.getPublicationReceipts().size());
                assertEquals("path", result.getPublicationReceipts().get(0).getTargetName());
                assertEquals("stream", result.getPublicationReceipts().get(1).getTargetName());
                for (int index = 0; index < 2; index++) {
                    assertEquals(PublicationStatus.COMMITTED, result.getPublicationReceipts().get(index).getStatus());
                }
                byte[] output = Files.readAllBytes(target);
                assertPrefix(original, output);
                assertArrayEquals(output, stream.toByteArray());
                assertEquals("1:Text:T15 annotation", readAnnotations(target).get("added"));
            } catch (DocumentFailure failure) {
                if ("invalid".equals(permission)) {
                    assertEquals(name, DocumentFailureCode.SIGNATURE_STRUCTURE_INVALID, failure.getCode());
                    assertEquals("The Existing Signature policy could not be determined safely.", failure.getDiagnostic());
                    assertFalse(name, ran.get());
                } else {
                    assertEquals(name, "denied", permission);
                    assertPolicyFailure(name, failure);
                    assertTrue(name, ran.get());
                }
                assertEquals(name, CAPABILITY, failure.getCapabilityId());
                assertEquals(name, 2, failure.getPublicationReceipts().size());
                assertEquals("path", failure.getPublicationReceipts().get(0).getTargetName());
                assertEquals("stream", failure.getPublicationReceipts().get(1).getTargetName());
                for (int index = 0; index < 2; index++) {
                    assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(index).getStatus());
                    assertFalse(failure.getPublicationReceipts().get(index).isPartialOutputPossible());
                }
                assertArrayEquals(name, SENTINEL, Files.readAllBytes(target));
                assertEquals(name, 0, stream.size());
            }
            assertArrayEquals(name, original, Files.readAllBytes(source));
        }
    }

    @Test
    public void soleP3CreatesReplacesMovesAndRemovesAnnotationsWithPublicReopening() throws Exception {
        Path source = corpus().resolve("p3.pdf");
        byte[] original = Files.readAllBytes(source);
        DocumentCommand[] commands = annotationChanges();
        for (int operation = 0; operation < commands.length; operation++) {
            Path output = temporary.getRoot().toPath().resolve("change-" + operation + ".pdf");
            final DocumentCommand command = commands[operation];
            WorkflowOutcome<Map<String, String>> outcome = new DocumentWorkflow().execute(request(source)
                    .target("target", PublicationTarget.path(output)).build(), session -> {
                        session.execute(command);
                        return annotationValues(session);
                    });
            Map<String, String> reopened = readAnnotations(output);
            assertEquals(outcome.getResult(), reopened);
            assertEquals(execution(), outcome.getExecutionProfile());
            assertEquals(PublicationStatus.COMMITTED, outcome.getPublicationReceipts().get(0).getStatus());
            if (operation == 0) { assertEquals("1:Text:T15 annotation", reopened.get("added")); }
            if (operation == 1) { assertEquals("1:Text:T15 annotation", reopened.get("existing")); }
            if (operation == 2) { assertEquals("2:Text:T15 annotation", reopened.get("existing")); }
            if (operation == 3) { assertFalse(reopened.containsKey("existing")); }
            assertEquals("1:Widget:", reopened.get("signature-widget"));
            assertPrefix(original, Files.readAllBytes(output));
            assertArrayEquals(original, Files.readAllBytes(source));
        }
    }

    @Test
    public void p3CannotRemoveOrReplaceAnExistingWidgetOrFlattenAnAnnotation() throws Exception {
        for (DocumentCommand command : new DocumentCommand[] {
                UpdateAnnotations.version1().remove("signature-widget").build(),
                UpdateAnnotations.version1().put(note("signature-widget", 1)).build(),
                FlattenAnnotations.version1("existing"), AddBlankPage.INSTANCE}) {
            Path target = temporary.newFile().toPath();
            Files.write(target, SENTINEL);
            try {
                new DocumentWorkflow().execute(request(corpus().resolve("p3.pdf"))
                        .target("target", PublicationTarget.path(target)).build(), session -> {
                            session.execute(command);
                            return null;
                        });
                fail("P3 must reject a command outside its proven annotation footprint");
            } catch (DocumentFailure failure) {
                assertPolicyFailure("P3 footprint", failure);
                assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(0).getStatus());
            }
            assertArrayEquals(SENTINEL, Files.readAllBytes(target));
        }
    }

    @Test
    public void missingPrimarySourceFailsBeforeWorkAndAnyDestinationWrite() throws Exception {
        Path target = temporary.newFile().toPath();
        Files.write(target, SENTINEL);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            new DocumentWorkflow().execute(builder().saveMode(SaveMode.INCREMENTAL)
                    .target("path", PublicationTarget.path(target)).target("stream", PublicationTarget.stream(stream))
                    .build(), session -> { fail("Caller work must not run"); return null; });
            fail("An incremental revision needs a primary Source");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.INCREMENTAL_SOURCE_REQUIRED, failure.getCode());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(2, failure.getPublicationReceipts().size());
            assertEquals("path", failure.getPublicationReceipts().get(0).getTargetName());
            assertEquals("stream", failure.getPublicationReceipts().get(1).getTargetName());
            for (int index = 0; index < 2; index++) {
                assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(index).getStatus());
            }
        }
        assertArrayEquals(SENTINEL, Files.readAllBytes(target));
        assertEquals(0, stream.size());
    }

    @Test
    public void soleP3RejectsActionsAndArbitraryValuePatchesBeforeMutation() throws Exception {
        for (boolean patch : new boolean[] {false, true}) {
            Path target = temporary.newFile().toPath();
            Files.write(target, SENTINEL);
            try {
                new DocumentWorkflow().execute(request(corpus().resolve("p3.pdf"))
                        .target("target", PublicationTarget.path(target)).build(), session -> {
                            DocumentCommand command = patch ? DocumentPatch.builder().setDictionaryEntry(
                                    session.query(DocumentRootReference.INSTANCE), PdfName.of("FolioMutation"),
                                    PdfNumber.of(1)).build() : UpdateActions.version1().removeDocumentOpenAction().build();
                            session.execute(command);
                            return null;
                        });
                fail("P3 must deny actions and arbitrary patches");
            } catch (DocumentFailure failure) {
                assertPolicyFailure("P3 command restriction", failure);
                assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(0).getStatus());
            }
            assertArrayEquals(SENTINEL, Files.readAllBytes(target));
        }
    }

    @Test
    public void emptyP3AnnotationCommandCannotManufactureAnAdmittedMutation() throws Exception {
        Path source = corpus().resolve("p3.pdf");
        byte[] original = Files.readAllBytes(source);
        Path target = temporary.newFile().toPath();
        Files.write(target, SENTINEL);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            new DocumentWorkflow().execute(request(source).target("path", PublicationTarget.path(target))
                    .target("stream", PublicationTarget.stream(stream)).build(), session -> {
                        session.execute(UpdateAnnotations.version1().build());
                        return null;
                    });
            fail("An empty update must not authorize a signed revision");
        } catch (DocumentFailure failure) {
            assertPolicyFailure("empty P3 update", failure);
            assertEquals(2, failure.getPublicationReceipts().size());
            assertEquals("path", failure.getPublicationReceipts().get(0).getTargetName());
            assertEquals("stream", failure.getPublicationReceipts().get(1).getTargetName());
            for (int index = 0; index < 2; index++) {
                assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(index).getStatus());
            }
        }
        assertArrayEquals(original, Files.readAllBytes(source));
        assertArrayEquals(SENTINEL, Files.readAllBytes(target));
        assertEquals(0, stream.size());
    }

    private static DocumentCommand[] annotationChanges() {
        return new DocumentCommand[] {
            UpdateAnnotations.version1().put(note("added", 1)).build(),
            UpdateAnnotations.version1().put(note("existing", 1)).build(),
            UpdateAnnotations.version1().put(note("existing", 2)).build(),
            UpdateAnnotations.version1().remove("existing").build()};
    }

    private static Annotation note(String identifier, int page) {
        return Annotation.text(AnnotationProperties.version1(identifier, page,
                AnnotationRectangle.of(10, 40, 30, 60)).contents("T15 annotation").build(),
                Annotation.TextIcon.NOTE, false);
    }

    private static Map<String, String> readAnnotations(Path path) throws Exception {
        return new DocumentWorkflow().execute(request(path).saveMode(SaveMode.REWRITE).build(),
                IncrementalSignatureMatrixTest::annotationValues).getResult();
    }

    private static Map<String, String> annotationValues(DocumentSession session) throws DocumentFailure {
        Map<String, String> result = new TreeMap<String, String>();
        for (int number = 1; number <= session.query(PageCount.INSTANCE); number++) {
            PdfDictionary page = (PdfDictionary) session.query(InspectObject.version1(
                    session.query(PageObjectReference.version1(number)), PdfInspectionLimits.of(100, 4096)));
            PdfArray annotations = (PdfArray) resolve(session, page.get(PdfName.of("Annots")));
            if (annotations == null) { continue; }
            for (int index = 0; index < annotations.size(); index++) {
                PdfDictionary annotation = (PdfDictionary) resolve(session, annotations.get(index));
                String identifier = string(annotation.get(PdfName.of("NM")));
                PdfValue subtype = annotation.get(PdfName.of("Subtype"));
                String type = PdfName.of("Text").equals(subtype) ? "Text"
                        : PdfName.of("Widget").equals(subtype) ? "Widget" : "Stamp";
                assertEquals(null, result.put(identifier, number + ":" + type + ":"
                        + string(annotation.get(PdfName.of("Contents")))));
            }
        }
        return result;
    }

    private static PdfValue resolve(DocumentSession session, PdfValue value) throws DocumentFailure {
        return value instanceof PdfIndirectReference ? session.query(InspectObject.version1(
                ((PdfIndirectReference) value).getReference(), PdfInspectionLimits.of(100, 4096))) : value;
    }

    private static String string(PdfValue value) {
        return value == null ? "" : new String(((PdfString) value).getBytes(), StandardCharsets.ISO_8859_1);
    }

    private static void assertPolicyFailure(String label, DocumentFailure failure) {
        assertEquals(label, DocumentFailureCode.SIGNATURE_POLICY_REJECTED, failure.getCode());
        assertEquals(label, CAPABILITY, failure.getCapabilityId());
        assertEquals("The Existing Signature policy does not permit this workflow.", failure.getDiagnostic());
    }

    private static void assertPrefix(byte[] source, byte[] output) {
        assertTrue(output.length > source.length);
        assertArrayEquals(source, Arrays.copyOf(output, source.length));
    }

    private static WorkflowExecutionProfile execution() {
        return WorkflowExecutionProfile.valueOf(System.getProperty("folio.t15.executionProfile", "IN_PROCESS"));
    }

    private static WorkflowRequest.Builder builder() { return WorkflowRequest.builder().executionProfile(execution()); }
    private static WorkflowRequest.Builder request(Path source) {
        return builder().source("source", DocumentSource.path(source)).primarySource("source").saveMode(SaveMode.INCREMENTAL);
    }
    private static Path corpus() {
        return Paths.get(System.getProperty("repositoryRoot", "..")).resolve("capabilities/profiles/T15-signatures");
    }
}
