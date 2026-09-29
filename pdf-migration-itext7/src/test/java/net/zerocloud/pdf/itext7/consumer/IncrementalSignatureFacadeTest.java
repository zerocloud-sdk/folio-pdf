package net.zerocloud.pdf.itext7.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import net.zerocloud.pdf.Annotation;
import net.zerocloud.pdf.AnnotationProperties;
import net.zerocloud.pdf.AnnotationRectangle;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PageRange;
import net.zerocloud.pdf.PdfVersion;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.DocumentVersion;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfArray;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDictionary;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfName;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfString;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;
import net.zerocloud.pdf.itext7.kernel.pdf.StampingProperties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** The Facade executes IN_PROCESS, independently of the Native test selection. */
public final class IncrementalSignatureFacadeTest {
    private static final byte[] SENTINEL = {27, 18, 28};
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void propertiesCopyAndDocumentCaptureProtectDefaultRewriteBehavior() throws Exception {
        StampingProperties original = new StampingProperties();
        StampingProperties copy = new StampingProperties(original);
        Path output = path("default.pdf");
        try (PdfReader reader = new PdfReader(fixture("unsigned").toString());
                PdfDocument document = new PdfDocument(reader, new PdfWriter(output.toString()), original)) {
            original.useAppendMode();
            assertFalse(document.isAppendMode());
            document.addNewPage();
        }
        assertEquals(3, pages(output));
        try (PdfReader reader = new PdfReader(fixture("unsigned").toString());
                PdfDocument document = new PdfDocument(reader, new PdfWriter(path("copy.pdf").toString()), copy)) {
            assertFalse(document.isAppendMode());
        }
        StampingProperties appendCopy = new StampingProperties(original);
        try (PdfReader reader = new PdfReader(fixture("unsigned").toString());
                PdfDocument document = new PdfDocument(reader, new PdfWriter(path("append-copy.pdf").toString()), appendCopy)) {
            assertTrue(document.isAppendMode());
            document.addNewPage();
        }
        assertPrefix(Files.readAllBytes(fixture("unsigned")), Files.readAllBytes(path("append-copy.pdf")));
    }

    @Test
    public void appendInheritsTheSourceVersionWithoutAddingAnExplicitVersionChange() throws Exception {
        for (String version : new String[] {"1.4", "2.0"}) {
            byte[] bytes = Files.readAllBytes(fixture("unsigned"));
            bytes[5] = (byte) version.charAt(0);
            bytes[7] = (byte) version.charAt(2);
            Path source = path("version-" + version + ".pdf");
            Path target = path("version-" + version + "-append.pdf");
            Files.write(source, bytes);
            try (PdfDocument document = append(source, target)) { document.addNewPage(); }
            assertPrefix(bytes, Files.readAllBytes(target));
            assertEquals(3, pages(target));
            assertEquals(version, new DocumentWorkflow().execute(
                    WorkflowRequest.open(target, SaveMode.REWRITE),
                    session -> session.query(DocumentVersion.INSTANCE).getEffectiveVersion()).getResult().toString());
        }
    }

    @Test
    public void repeatedAppendKeepsEveryRevisionAndOrderedReceiptsWithoutClosingCallerStreams() throws Exception {
        byte[] source = Files.readAllBytes(fixture("unsigned"));
        TrackingInput input = new TrackingInput(source);
        TrackingOutput stream = new TrackingOutput();
        Path first = path("first.pdf");
        Path second = path("second.pdf");
        Map<String, PdfWriter> targets = new LinkedHashMap<String, PdfWriter>();
        targets.put("path", new PdfWriter(first.toString()));
        targets.put("stream", new PdfWriter(stream));
        PdfReader reader = new PdfReader(input);
        PdfDocument document = new PdfDocument(Collections.singletonMap("source", reader), "source", targets,
                PdfVersion.PDF_1_7, new StampingProperties().useAppendMode());
        reader.close();
        try {
            document.addNewPage();
            assertEquals(3, document.getNumberOfPages());
        } finally { document.close(); }
        assertFalse(input.closed);
        assertFalse(stream.closed);
        assertEquals("path", document.getPublicationReceipts().get(0).getTargetName());
        assertEquals("stream", document.getPublicationReceipts().get(1).getTargetName());
        for (int index = 0; index < 2; index++) {
            assertEquals(PublicationStatus.COMMITTED, document.getPublicationReceipts().get(index).getStatus());
            assertFalse(document.getPublicationReceipts().get(index).isPartialOutputPossible());
        }
        byte[] firstBytes = Files.readAllBytes(first);
        assertPrefix(source, firstBytes);
        assertArrayEquals(firstBytes, stream.toByteArray());
        try (PdfDocument next = append(first, second)) { next.addNewPage(); }
        assertPrefix(firstBytes, Files.readAllBytes(second));
        assertEquals(4, pages(second));
        assertArrayEquals(source, Files.readAllBytes(fixture("unsigned")));
    }

    @Test
    public void soleP3CreatesReplacesMovesAndRemovesNonWidgetAnnotations() throws Exception {
        Path source = fixture("p3");
        for (int operation = 0; operation < 4; operation++) {
            Path output = path("operation-" + operation + ".pdf");
            PdfDocument document = append(source, output);
            Map<String, String> observed;
            try {
                change(document, operation);
                observed = annotations(document);
            } finally { document.close(); }
            assertEquals(PublicationStatus.COMMITTED, document.getPublicationReceipts().get(0).getStatus());
            try (PdfDocument reopened = new PdfDocument(new PdfReader(output.toString()))) {
                assertEquals(observed, annotations(reopened));
                assertEquals("1:Widget:", observed.get("signature-widget"));
                if (operation == 0) { assertEquals("1:Text:T15 annotation", observed.get("added")); }
                if (operation == 1) { assertEquals("1:Text:T15 annotation", observed.get("existing")); }
                if (operation == 2) { assertEquals("2:Text:T15 annotation", observed.get("existing")); }
                if (operation == 3) { assertFalse(observed.containsKey("existing")); }
            }
            assertPrefix(Files.readAllBytes(source), Files.readAllBytes(output));
        }
    }

    @Test
    public void everyAdditionalRestrictionDeniesOtherwisePermittedAnnotationChanges() throws Exception {
        Properties cases = cases();
        for (String name : new TreeSet<String>(cases.stringPropertyNames())) {
            if (!"denied".equals(cases.getProperty(name))) { continue; }
            Path target = path(name + ".pdf");
            Files.write(target, SENTINEL);
            TrackingOutput stream = new TrackingOutput();
            Map<String, PdfWriter> targets = new LinkedHashMap<String, PdfWriter>();
            targets.put("path", new PdfWriter(target.toString()));
            targets.put("stream", new PdfWriter(stream));
            PdfDocument document = new PdfDocument(Collections.singletonMap("source", new PdfReader(fixture(name).toString())),
                    "source", targets, PdfVersion.PDF_1_7, new StampingProperties().useAppendMode());
            assertEquals(2, document.getNumberOfPages());
            for (int operation = 0; operation < 4; operation++) {
                final int selected = operation;
                expect(DocumentFailureCode.SIGNATURE_POLICY_REJECTED, () -> change(document, selected));
            }
            expect(DocumentFailureCode.SIGNATURE_POLICY_REJECTED, document::close);
            assertNotAttempted(document, "path", "stream");
            assertArrayEquals(SENTINEL, Files.readAllBytes(target));
            assertEquals(0, stream.size());
            assertFalse(stream.closed);
        }
    }

    @Test
    public void defaultSignedRewriteAndAppendWithoutMutationPreserveDestinations() throws Exception {
        for (String name : new String[] {"approval", "p3"}) {
            Path target = path(name + "-rewrite.pdf");
            Files.write(target, SENTINEL);
            PdfDocument rewrite = new PdfDocument(new PdfReader(fixture(name).toString()), new PdfWriter(target.toString()));
            assertFalse(rewrite.isAppendMode());
            expect(DocumentFailureCode.SIGNED_REWRITE_REJECTED, rewrite::close);
            assertNotAttempted(rewrite, "target");
            PdfDocument append = append(fixture(name), target);
            assertEquals(2, append.getNumberOfPages());
            expect(DocumentFailureCode.SIGNATURE_POLICY_REJECTED, append::close);
            assertNotAttempted(append, "target");
            assertArrayEquals(SENTINEL, Files.readAllBytes(target));
            try (PdfDocument readable = new PdfDocument(new PdfReader(fixture(name).toString()))) {
                assertEquals(2, readable.getNumberOfPages());
                expect(DocumentFailureCode.SIGNATURE_POLICY_REJECTED,
                        () -> readable.getCatalog().getPdfObject().put(new PdfName("FolioKeep"), new PdfString("forbidden")));
            }
        }
    }

    @Test
    public void malformedSourceFailuresRemainSafeAtTheReaderBoundaryAndKeepCallerOwnership() throws Exception {
        Properties cases = cases();
        for (String name : new TreeSet<String>(cases.stringPropertyNames())) {
            if (!"invalid".equals(cases.getProperty(name))) { continue; }
            byte[] original = Files.readAllBytes(fixture(name));
            TrackingInput stream = new TrackingInput(original);
            try {
                new PdfReader(stream).close();
                fail(name + " must fail during Reader validation");
            } catch (IOException failure) {
                assertTrue(name, failure.getCause() instanceof DocumentFailure);
                assertFailure(DocumentFailureCode.SIGNATURE_STRUCTURE_INVALID, (DocumentFailure) failure.getCause());
            }
            assertFalse(stream.closed);
            assertArrayEquals(original, Files.readAllBytes(fixture(name)));
        }
    }

    @Test
    public void appendWithoutSourceReturnsOrderedUnattemptedReceipts() throws Exception {
        Path output = path("no-source.pdf");
        Files.write(output, SENTINEL);
        TrackingOutput stream = new TrackingOutput();
        Map<String, PdfWriter> targets = new LinkedHashMap<String, PdfWriter>();
        targets.put("path", new PdfWriter(output.toString()));
        targets.put("stream", new PdfWriter(stream));
        PdfDocument document = new PdfDocument(Collections.<String, PdfReader>emptyMap(), null,
                targets, PdfVersion.PDF_1_7, new StampingProperties().useAppendMode());
        expect(DocumentFailureCode.INCREMENTAL_SOURCE_REQUIRED, document::close);
        assertNotAttempted(document, "path", "stream");
        assertArrayEquals(SENTINEL, Files.readAllBytes(output));
        assertEquals(0, stream.size());
        assertFalse(stream.closed);
    }

    @Test
    public void appendRejectsSplitBeforeMutationAndKeepsSessionUsable() throws Exception {
        Path first = path("split-first.pdf");
        Path last = path("split-last.pdf");
        Files.write(first, SENTINEL);
        Files.write(last, SENTINEL);
        Map<String, PdfWriter> targets = new LinkedHashMap<String, PdfWriter>();
        targets.put("first", new PdfWriter(first.toString()));
        targets.put("last", new PdfWriter(last.toString()));
        PdfDocument document = new PdfDocument(Collections.singletonMap("source", new PdfReader(fixture("unsigned").toString())),
                "source", targets, PdfVersion.PDF_1_7, new StampingProperties().useAppendMode());
        expect(DocumentFailureCode.INCREMENTAL_COMMAND_REJECTED, () -> document.getSplitter().extractPageRanges(
                new String[] {"first", "last"}, new PageRange[] {PageRange.of(1, 1), PageRange.of(2, 2)}));
        assertArrayEquals(SENTINEL, Files.readAllBytes(first));
        assertArrayEquals(SENTINEL, Files.readAllBytes(last));
        assertEquals(2, document.getNumberOfPages());
        document.addNewPage();
        document.close();
        assertEquals(3, pages(first));
        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(last));
        assertPrefix(Files.readAllBytes(fixture("unsigned")), Files.readAllBytes(first));
        assertEquals(PublicationStatus.COMMITTED, document.getPublicationReceipts().get(0).getStatus());
        assertEquals(PublicationStatus.COMMITTED, document.getPublicationReceipts().get(1).getStatus());
    }

    @Test
    public void soleP3StillRejectsWidgetEditsFlatteningAndPageCommands() throws Exception {
        Path target = path("protected-p3.pdf");
        Files.write(target, SENTINEL);
        PdfDocument document = append(fixture("p3"), target);
        expect(DocumentFailureCode.SIGNATURE_POLICY_REJECTED, () -> document.updateAnnotations(
                Collections.<Annotation>emptyList(), Collections.<String>emptyList()));
        expect(DocumentFailureCode.SIGNATURE_POLICY_REJECTED, () -> document.updateAnnotations(
                Collections.<Annotation>emptyList(), Collections.singletonList("signature-widget")));
        Annotation replacement = Annotation.text(AnnotationProperties.version1("signature-widget", 1,
                AnnotationRectangle.of(0, 0, 10, 10)).contents("Replacement").build(), Annotation.TextIcon.NOTE, false);
        expect(DocumentFailureCode.SIGNATURE_POLICY_REJECTED, () -> document.updateAnnotations(
                Collections.singletonList(replacement), Collections.<String>emptyList()));
        expect(DocumentFailureCode.SIGNATURE_POLICY_REJECTED, () -> document.flattenAnnotations("existing"));
        expect(DocumentFailureCode.SIGNATURE_POLICY_REJECTED, document::addNewPage);
        assertEquals(2, document.getNumberOfPages());
        assertEquals("1:Widget:", annotations(document).get("signature-widget"));
        expect(DocumentFailureCode.SIGNATURE_POLICY_REJECTED, document::close);
        assertNotAttempted(document, "target");
        assertArrayEquals(SENTINEL, Files.readAllBytes(target));
    }

    @Test
    public void appendLifecycleExpiresValuesAndClosingIsIdempotent() throws Exception {
        Path output = path("lifecycle.pdf");
        PdfDocument document = append(fixture("unsigned"), output);
        PdfDictionary retained = document.getPage(1).getPdfObject();
        document.addNewPage();
        document.close();
        byte[] published = Files.readAllBytes(output);
        document.close();
        assertArrayEquals(published, Files.readAllBytes(output));
        try { retained.size(); fail("Session values must expire"); }
        catch (PdfException failure) {
            assertEquals(DocumentFailureCode.PDF_VALUE_VIEW_EXPIRED, ((DocumentFailure) failure.getCause()).getCode());
        }
        try { document.isAppendMode(); fail("Closed document access must fail"); }
        catch (IllegalStateException expected) { assertEquals(3, pages(output)); }
    }

    @Test
    public void failingStreamRetainsEarlierCommitAndLeavesLaterPathUnattempted() throws Exception {
        Path first = path("committed.pdf");
        Path last = path("untouched.pdf");
        Files.write(last, SENTINEL);
        Map<String, PdfWriter> targets = new LinkedHashMap<String, PdfWriter>();
        targets.put("first", new PdfWriter(first.toString()));
        targets.put("failed", new PdfWriter(new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("private sink detail"); }
        }));
        targets.put("last", new PdfWriter(last.toString()));
        PdfDocument document = new PdfDocument(Collections.singletonMap("source", new PdfReader(fixture("unsigned").toString())),
                "source", targets, PdfVersion.PDF_1_7, new StampingProperties().useAppendMode());
        document.addNewPage();
        try { document.close(); fail("Stream publication must fail"); }
        catch (PdfException failure) { assertFalse(failure.getMessage().contains("private sink detail")); }
        assertEquals(3, document.getPublicationReceipts().size());
        assertEquals(PublicationStatus.COMMITTED, document.getPublicationReceipts().get(0).getStatus());
        assertEquals(PublicationStatus.FAILED, document.getPublicationReceipts().get(1).getStatus());
        assertEquals(PublicationStatus.NOT_ATTEMPTED, document.getPublicationReceipts().get(2).getStatus());
        assertTrue(document.getPublicationReceipts().get(1).isPartialOutputPossible());
        assertPrefix(Files.readAllBytes(fixture("unsigned")), Files.readAllBytes(first));
        assertArrayEquals(SENTINEL, Files.readAllBytes(last));
    }

    private static void change(PdfDocument document, int operation) {
        if (operation == 3) {
            document.updateAnnotations(Collections.<Annotation>emptyList(), Collections.singletonList("existing"));
        } else {
            Annotation note = Annotation.text(AnnotationProperties.version1(operation == 0 ? "added" : "existing",
                    operation == 2 ? 2 : 1, AnnotationRectangle.of(10, 40, 30, 60)).contents("T15 annotation").build(),
                    Annotation.TextIcon.NOTE, false);
            document.updateAnnotations(Collections.singletonList(note), Collections.<String>emptyList());
        }
    }

    private static Map<String, String> annotations(PdfDocument document) {
        Map<String, String> result = new TreeMap<String, String>();
        for (int page = 1; page <= document.getNumberOfPages(); page++) {
            PdfArray annotations = (PdfArray) document.getPage(page).getPdfObject().get(new PdfName("Annots"));
            if (annotations == null) { continue; }
            for (int index = 0; index < annotations.size(); index++) {
                PdfDictionary annotation = (PdfDictionary) annotations.get(index);
                PdfString contents = (PdfString) annotation.get(new PdfName("Contents"));
                result.put(((PdfString) annotation.get(new PdfName("NM"))).getValue(), page + ":"
                        + ((PdfName) annotation.get(new PdfName("Subtype"))).getValue() + ":"
                        + (contents == null ? "" : contents.getValue()));
            }
        }
        return result;
    }

    private static void expect(DocumentFailureCode code, Runnable action) {
        try { action.run(); fail("Expected " + code); }
        catch (PdfException failure) {
            assertTrue(failure.getCause() instanceof DocumentFailure);
            assertFailure(code, (DocumentFailure) failure.getCause());
        }
    }

    private static void assertFailure(DocumentFailureCode code, DocumentFailure failure) {
        assertEquals(code, failure.getCode());
        assertEquals("document.incremental-signature.protect", failure.getCapabilityId());
        if (code == DocumentFailureCode.SIGNATURE_POLICY_REJECTED) {
            assertEquals("The Existing Signature policy does not permit this workflow.", failure.getDiagnostic());
        } else if (code == DocumentFailureCode.SIGNATURE_STRUCTURE_INVALID) {
            assertEquals("The Existing Signature policy could not be determined safely.", failure.getDiagnostic());
        }
    }

    private static void assertNotAttempted(PdfDocument document, String... names) {
        assertEquals(names.length, document.getPublicationReceipts().size());
        for (int index = 0; index < names.length; index++) {
            assertEquals(names[index], document.getPublicationReceipts().get(index).getTargetName());
            assertEquals(PublicationStatus.NOT_ATTEMPTED, document.getPublicationReceipts().get(index).getStatus());
        }
    }

    private static PdfDocument append(Path source, Path target) throws IOException {
        return new PdfDocument(new PdfReader(source.toString()), new PdfWriter(target.toString()),
                new StampingProperties().useAppendMode());
    }
    private static int pages(Path path) throws IOException {
        try (PdfDocument document = new PdfDocument(new PdfReader(path.toString()))) { return document.getNumberOfPages(); }
    }
    private static void assertPrefix(byte[] source, byte[] output) {
        assertTrue(output.length > source.length);
        assertArrayEquals(source, Arrays.copyOf(output, source.length));
    }
    private static Path fixture(String name) {
        return Paths.get(System.getProperty("repositoryRoot", "..")).resolve("capabilities/profiles/T15-signatures/" + name + ".pdf");
    }
    private static Properties cases() throws IOException {
        Properties result = new Properties();
        try (InputStream input = Files.newInputStream(fixture("p3").getParent().resolve("cases.properties"))) { result.load(input); }
        return result;
    }
    private Path path(String name) { return temporary.getRoot().toPath().resolve(name); }
    private static final class TrackingInput extends ByteArrayInputStream {
        private boolean closed;
        TrackingInput(byte[] bytes) { super(bytes); }
        @Override public void close() throws IOException { closed = true; super.close(); }
    }
    private static final class TrackingOutput extends ByteArrayOutputStream {
        private boolean closed;
        @Override public void close() throws IOException { closed = true; super.close(); }
    }
}
