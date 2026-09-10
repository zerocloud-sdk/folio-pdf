package net.zerocloud.pdf.itext7.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Path;
import java.io.IOException;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.PageRange;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.PublicationReceipt;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDictionary;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfName;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfPage;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfString;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;
import net.zerocloud.pdf.itext7.kernel.utils.PdfMerger;
import net.zerocloud.pdf.itext7.kernel.utils.PdfSplitter;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.PageObjectReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class PageManipulationFacadeTest {
    private static final PdfName LABEL = new PdfName("T72PageLabel");

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void queuedPageHandlesRetainTheirPagesAcrossInsertionAndReopen() throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve("insert.pdf");
        try (PdfDocument document = new PdfDocument(new PdfWriter(target.toString()))) {
            PdfPage first = document.addNewPage();
            PdfPage second = document.addNewPage();
            PdfPage inserted = document.addNewPage(1);
            label(first, "first");
            label(second, "second");
            label(inserted, "inserted");
            assertEquals(Arrays.asList("inserted", "first", "second"), labels(document));
        }
        assertReopenedLabels(target, "inserted", "first", "second");
    }

    @Test
    public void rangeMoveCopyAndRemovalKeepOrderAndSeparateCopiedValues() throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve("reorder.pdf");
        try (PdfDocument document = new PdfDocument(new PdfWriter(target.toString()))) {
            label(document.addNewPage(), "A");
            PdfPage originalB = document.addNewPage();
            label(originalB, "B");
            label(document.addNewPage(), "C");
            label(document.addNewPage(), "D");

            document.movePages(2, 3, 3);
            assertEquals(Arrays.asList("A", "D", "B", "C"), labels(document));
            List<PdfPage> copied = document.copyPages(2, 3, 1);
            assertEquals(2, copied.size());
            assertEquals(Arrays.asList("D", "B", "A", "D", "B", "C"), labels(document));
            label(copied.get(0), "copy-D");
            label(copied.get(1), "copy-B");
            label(originalB, "changed-B");
            assertEquals(Arrays.asList("copy-D", "copy-B", "A", "D", "changed-B", "C"), labels(document));

            document.removePages(3, 4);
            document.movePage(4, 1);
            document.removePage(2);
            assertEquals(Arrays.asList("C", "copy-B", "changed-B"), labels(document));
        }
        assertReopenedLabels(target, "C", "copy-B", "changed-B");
    }

    @Test
    public void namedMergeConsumesSelectedSnapshotsInCallOrderAndKeepsExistingViews() throws Exception {
        Path base = labeledDocument("base", "A");
        Path appendix = labeledDocument("appendix", "B");
        Path cover = labeledDocument("cover", "C");
        byte[] originalBase = Files.readAllBytes(base);
        byte[] originalCover = Files.readAllBytes(cover);
        Path target = temporaryFolder.getRoot().toPath().resolve("merged.pdf");
        Map<String, PdfReader> sources = new LinkedHashMap<String, PdfReader>();
        sources.put("appendix", new PdfReader(appendix.toString()));
        sources.put("base", new PdfReader(base.toString()));
        sources.put("cover", new PdfReader(cover.toString()));
        Map<String, PdfWriter> targets = new LinkedHashMap<String, PdfWriter>();
        targets.put("merged", new PdfWriter(target.toString()));
        PdfDocument document = new PdfDocument(sources, "base", targets);
        try {
            for (PdfReader reader : sources.values()) {
                reader.close();
            }
            Files.delete(appendix);
            sources.clear();
            targets.clear();
            PdfDictionary retained = document.getPage(1).getPdfObject();
            PdfMerger merger = document.getMerger();
            assertSame(merger, merger.merge("cover", "appendix"));
            assertEquals(Arrays.asList("A", "C", "B"), labels(document));
            retained.put(LABEL, new PdfString("changed-A"));
            assertSame(merger, merger.merge("cover"));
            assertEquals(Arrays.asList("changed-A", "C", "B", "C"), labels(document));
            try {
                document.getPublicationReceipts();
                fail("An open document has no publication outcome");
            } catch (IllegalStateException expected) {
                assertFalse(Files.exists(target));
            }
            merger.close();
        } finally {
            document.close();
        }
        List<PublicationReceipt> receipts = document.getPublicationReceipts();
        assertEquals(1, receipts.size());
        assertEquals("merged", receipts.get(0).getTargetName());
        assertEquals(PublicationStatus.COMMITTED, receipts.get(0).getStatus());
        assertEquals(target.toAbsolutePath().normalize(), receipts.get(0).getPathTarget().get());
        assertFalse(receipts.get(0).isPartialOutputPossible());
        try {
            receipts.clear();
            fail("Receipt observations must be immutable");
        } catch (UnsupportedOperationException expected) {
            assertEquals(1, document.getPublicationReceipts().size());
        }
        assertReopenedLabels(target, "changed-A", "C", "B", "C");
        assertArrayEquals(originalBase, Files.readAllBytes(base));
        assertArrayEquals(originalCover, Files.readAllBytes(cover));
    }

    @Test
    public void duplicateReaderDeclarationsFailBeforeTransferringAnyOwnership() throws Exception {
        Path source = labeledDocument("duplicate-reader", "A");
        try (PdfReader reader = new PdfReader(source.toString())) {
            Map<String, PdfReader> sources = new LinkedHashMap<String, PdfReader>();
            sources.put("first", reader);
            sources.put("second", reader);
            try {
                new PdfDocument(sources, "first", Collections.<String, PdfWriter>emptyMap());
                fail("One reader cannot transfer its snapshot twice");
            } catch (IllegalArgumentException expected) {
                try (PdfDocument stillAvailable = new PdfDocument(reader)) {
                    assertEquals(Arrays.asList("A"), labels(stillAvailable));
                }
            }
        }
    }

    @Test
    public void unavailableLaterReaderFailsBeforeClaimingEarlierReaders() throws Exception {
        Path source = labeledDocument("unavailable-reader", "A");
        try (PdfReader first = new PdfReader(source.toString());
                PdfReader unavailable = new PdfReader(source.toString())) {
            unavailable.close();
            Map<String, PdfReader> sources = new LinkedHashMap<String, PdfReader>();
            sources.put("first", first);
            sources.put("later", unavailable);
            try {
                new PdfDocument(sources, "first", Collections.<String, PdfWriter>emptyMap());
                fail("A closed reader cannot transfer its snapshot");
            } catch (IllegalStateException expected) {
                try (PdfDocument stillAvailable = new PdfDocument(first)) {
                    assertEquals(Arrays.asList("A"), labels(stillAvailable));
                }
            }
        }
    }

    @Test
    public void missingPrimarySelectionFailsBeforeClaimingReaders() throws Exception {
        Path source = labeledDocument("primary-required", "A");
        try (PdfReader reader = new PdfReader(source.toString())) {
            Map<String, PdfReader> sources = Collections.singletonMap("declared", reader);
            try {
                new PdfDocument(sources, "missing", Collections.<String, PdfWriter>emptyMap());
                fail("Nonempty Sources require an explicit declared primary");
            } catch (IllegalStateException expected) {
                try (PdfDocument stillAvailable = new PdfDocument(reader)) {
                    assertEquals(Arrays.asList("A"), labels(stillAvailable));
                }
            }
        }
    }

    @Test
    public void splitPublishesTheDeclaredGroupAndProductsReopenAndChangeIndependently() throws Exception {
        Path source = labeledDocument("split-source", "A", "B", "C", "D");
        byte[] original = Files.readAllBytes(source);
        Path left = temporaryFolder.getRoot().toPath().resolve("left.pdf");
        Path right = temporaryFolder.getRoot().toPath().resolve("right.pdf");
        Map<String, PdfWriter> targets = new LinkedHashMap<String, PdfWriter>();
        targets.put("left", new PdfWriter(left.toString()));
        targets.put("right", new PdfWriter(right.toString()));
        PdfDocument document = new PdfDocument(Collections.singletonMap("source", new PdfReader(source.toString())),
                "source", targets);
        try {
            PdfPage held = document.getPage(2);
            document.getSplitter().extractPageRanges(new String[] {"right", "left"},
                    new PageRange[] {PageRange.of(2, 4), PageRange.of(1, 2)});
            assertEquals(Arrays.asList("A", "B", "C", "D"), labels(document));
            assertEquals("B", ((PdfString) held.getPdfObject().get(LABEL)).getValue());
            expectNativeFailure(DocumentFailureCode.COMMAND_REJECTED, () -> document.removePage(1));
            expectNativeFailure(DocumentFailureCode.COMMAND_REJECTED, () -> document.addNewPage());
            expectNativeFailure(DocumentFailureCode.COMMAND_REJECTED, () -> label(held, "too-late"));
            assertFalse(Files.exists(left));
            assertFalse(Files.exists(right));
        } finally {
            document.close();
        }
        assertReceipt(document.getPublicationReceipts().get(0), "left", PublicationStatus.COMMITTED, false);
        assertReceipt(document.getPublicationReceipts().get(1), "right", PublicationStatus.COMMITTED, false);
        assertReopenedLabels(left, "A", "B");
        assertReopenedLabels(right, "B", "C", "D");
        byte[] sibling = Files.readAllBytes(right);
        try (PdfDocument rewrite = new PdfDocument(new PdfReader(left.toString()), new PdfWriter(left.toString()))) {
            label(rewrite.getPage(2), "left-B");
        }
        assertReopenedLabels(left, "A", "left-B");
        assertReopenedLabels(right, "B", "C", "D");
        assertArrayEquals(original, Files.readAllBytes(source));
        assertArrayEquals(sibling, Files.readAllBytes(right));
    }

    @Test
    public void splitRejectsMissingExtraDuplicateAndInvalidRangesWithoutChangingTargets() throws Exception {
        Path source = labeledDocument("split-selection-source", "A", "B", "C");
        Path left = temporaryFolder.getRoot().toPath().resolve("selection-left.pdf");
        Path right = temporaryFolder.getRoot().toPath().resolve("selection-right.pdf");
        byte[] previous = "previous target".getBytes(StandardCharsets.US_ASCII);
        Files.write(left, previous);
        Files.write(right, previous);
        Map<String, PdfWriter> targets = new LinkedHashMap<String, PdfWriter>();
        targets.put("left", new PdfWriter(left.toString()));
        targets.put("right", new PdfWriter(right.toString()));
        try (PdfDocument document = new PdfDocument(Collections.singletonMap("source", new PdfReader(source.toString())),
                "source", targets)) {
            PdfSplitter splitter = document.getSplitter();
            expectNativeFailure(DocumentFailureCode.SPLIT_TARGET_INVALID,
                    () -> splitter.extractPageRanges(new String[] {"left"}, new PageRange[] {PageRange.of(1, 1)}));
            expectNativeFailure(DocumentFailureCode.SPLIT_TARGET_INVALID,
                    () -> splitter.extractPageRanges(new String[] {"left", "right", "extra"},
                            new PageRange[] {PageRange.of(1, 1), PageRange.of(2, 2), PageRange.of(3, 3)}));
            expectNativeFailure(DocumentFailureCode.SPLIT_TARGET_INVALID,
                    () -> splitter.extractPageRanges(new String[] {"left", "right", "left"},
                            new PageRange[] {PageRange.of(1, 1), PageRange.of(2, 2), PageRange.of(3, 3)}));
            expectNativeFailure(DocumentFailureCode.PAGE_RANGE_INVALID,
                    () -> splitter.extractPageRanges(new String[] {"left", "right"},
                            new PageRange[] {PageRange.of(1, 1), PageRange.of(3, 4)}));
            try {
                splitter.extractPageRanges(new String[] {"left", "right"}, new PageRange[] {PageRange.of(1, 1)});
                fail("Parallel split selections must have equal lengths");
            } catch (IllegalArgumentException expected) {
                assertEquals(Arrays.asList("A", "B", "C"), labels(document));
            }
            assertArrayEquals(previous, Files.readAllBytes(left));
            assertArrayEquals(previous, Files.readAllBytes(right));
            splitter.extractPageRanges(new String[] {"right", "left"},
                    new PageRange[] {PageRange.of(2, 3), PageRange.of(1, 1)});
        }
        assertReopenedLabels(left, "A");
        assertReopenedLabels(right, "B", "C");
    }

    @Test
    public void failedSplitPublicationRetainsOrderedReceiptsAndCallerStreamOwnership() throws Exception {
        Path source = labeledDocument("failed-split-source", "A", "B", "C");
        Path first = temporaryFolder.getRoot().toPath().resolve("committed.pdf");
        Path last = temporaryFolder.getRoot().toPath().resolve("not-attempted.pdf");
        byte[] previous = "previous last target".getBytes(StandardCharsets.US_ASCII);
        Files.write(last, previous);
        FailingOutput failing = new FailingOutput();
        Map<String, PdfWriter> targets = new LinkedHashMap<String, PdfWriter>();
        targets.put("first", new PdfWriter(first.toString()));
        targets.put("middle", new PdfWriter(failing));
        targets.put("last", new PdfWriter(last.toString()));
        PdfDocument document = new PdfDocument(Collections.singletonMap("source", new PdfReader(source.toString())),
                "source", targets);
        document.getSplitter().extractPageRanges(new String[] {"last", "middle", "first"},
                new PageRange[] {PageRange.of(3, 3), PageRange.of(2, 2), PageRange.of(1, 1)});
        DocumentFailure failure = expectNativeFailure(DocumentFailureCode.PUBLICATION_FAILED, document::close);
        List<PublicationReceipt> receipts = failure.getPublicationReceipts();
        assertEquals(3, receipts.size());
        assertEquals(receipts, document.getPublicationReceipts());
        assertReceipt(receipts.get(0), "first", PublicationStatus.COMMITTED, false);
        assertReceipt(receipts.get(1), "middle", PublicationStatus.FAILED, true);
        assertReceipt(receipts.get(2), "last", PublicationStatus.NOT_ATTEMPTED, false);
        assertFalse(receipts.get(1).getPathTarget().isPresent());
        assertEquals("The validated document could not be written to its stream target.", failure.getDiagnostic());
        assertReopenedLabels(first, "A");
        assertArrayEquals(previous, Files.readAllBytes(last));
        assertEquals(32, failing.bytes.size());
        int attempts = failing.attempts;
        document.close();
        assertEquals(attempts, failing.attempts);
        assertFalse(failing.closed);
    }

    @Test
    public void queuedHandleFailureOnCloseRetainsTheCompletedNativeFailureReceipts() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("missing-page-type.pdf");
        Files.write(source, pdfFixture(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 /MediaBox [0 0 200 160] /Resources << >> >>",
                "<< /Parent 2 0 R >>"));
        Path output = temporaryFolder.getRoot().toPath().resolve("failed-initialization.pdf");
        byte[] previous = "previous target".getBytes(StandardCharsets.US_ASCII);
        Files.write(output, previous);
        PdfDocument document = new PdfDocument(new PdfReader(source.toString()), new PdfWriter(output.toString()));
        document.addNewPage();
        DocumentFailure failure = expectNativeFailure(DocumentFailureCode.QUERY_FAILED, document::close);
        assertEquals(1, failure.getPublicationReceipts().size());
        assertEquals(failure.getPublicationReceipts(), document.getPublicationReceipts());
        assertReceipt(document.getPublicationReceipts().get(0), "target", PublicationStatus.NOT_ATTEMPTED, false);
        assertArrayEquals(previous, Files.readAllBytes(output));
        document.close();
        assertArrayEquals(previous, Files.readAllBytes(output));
    }

    private static byte[] pdfFixture(String... objects) {
        StringBuilder pdf = new StringBuilder("%PDF-1.7\n");
        List<Integer> offsets = new ArrayList<Integer>();
        for (int index = 0; index < objects.length; index++) {
            offsets.add(pdf.length());
            pdf.append(index + 1).append(" 0 obj\n").append(objects[index]).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 ").append(objects.length + 1).append("\n0000000000 65535 f \n");
        for (int offset : offsets) {
            pdf.append(String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        }
        pdf.append("trailer\n<< /Size ").append(objects.length + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static DocumentFailure expectNativeFailure(DocumentFailureCode code, Runnable action) {
        try {
            action.run();
            fail("Expected Native failure " + code);
            return null;
        } catch (PdfException failure) {
            assertTrue(failure.getCause() instanceof DocumentFailure);
            DocumentFailure nativeFailure = (DocumentFailure) failure.getCause();
            assertEquals(code, nativeFailure.getCode());
            return nativeFailure;
        }
    }

    private static void assertReceipt(PublicationReceipt receipt, String name,
            PublicationStatus status, boolean partial) {
        assertEquals(name, receipt.getTargetName());
        assertEquals(status, receipt.getStatus());
        assertEquals(partial, receipt.isPartialOutputPossible());
    }

    private static final class FailingOutput extends OutputStream {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private boolean closed;
        private int attempts;

        @Override
        public void write(int value) throws IOException {
            attempts++;
            if (bytes.size() == 32) {
                throw new IOException("T72 deliberate stream failure");
            }
            bytes.write(value);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private Path labeledDocument(String filename, String... labels) throws Exception {
        Path path = temporaryFolder.getRoot().toPath().resolve(filename + ".pdf");
        try (PdfDocument document = new PdfDocument(new PdfWriter(path.toString()))) {
            for (String label : labels) {
                label(document.addNewPage(), label);
            }
        }
        return path;
    }

    private static void label(PdfPage page, String label) {
        page.getPdfObject().put(LABEL, new PdfString(label.getBytes(StandardCharsets.US_ASCII)));
    }

    private static List<String> labels(PdfDocument document) {
        List<String> labels = new ArrayList<String>();
        for (int page = 1; page <= document.getNumberOfPages(); page++) {
            labels.add(((PdfString) document.getPage(page).getPdfObject().get(LABEL)).getValue());
        }
        return labels;
    }

    private static void assertReopenedLabels(Path path, String... expected) throws Exception {
        try (PdfReader reader = new PdfReader(path.toString());
                PdfDocument document = new PdfDocument(reader)) {
            assertEquals(Arrays.asList(expected), labels(document));
        }
        List<String> nativeLabels = new DocumentWorkflow().execute(
                WorkflowRequest.open(path, SaveMode.REWRITE), session -> {
                    List<String> result = new ArrayList<String>();
                    for (int page = 1; page <= session.query(PageCount.INSTANCE).intValue(); page++) {
                        net.zerocloud.pdf.PdfDictionary dictionary = (net.zerocloud.pdf.PdfDictionary) session.query(
                                InspectObject.version1(session.query(PageObjectReference.version1(page)),
                                        PdfInspectionLimits.of(100, 0)));
                        net.zerocloud.pdf.PdfString label = (net.zerocloud.pdf.PdfString) dictionary.get(
                                net.zerocloud.pdf.PdfName.of("T72PageLabel"));
                        result.add(new String(label.getBytes(), StandardCharsets.US_ASCII));
                    }
                    return result;
                }).getResult();
        assertEquals(Arrays.asList(expected), nativeLabels);
    }
}
