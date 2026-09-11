package net.zerocloud.pdf.itext7.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.PageDestination;
import net.zerocloud.pdf.PageRange;
import net.zerocloud.pdf.PublicationReceipt;
import net.zerocloud.pdf.OutlineItem;
import net.zerocloud.pdf.EmbeddedFile;
import net.zerocloud.pdf.EmbeddedFileData;
import net.zerocloud.pdf.EmbeddedFileSummary;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfBoolean;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfString;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.command.UpdateDocumentInfo;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocumentInfo;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;
import net.zerocloud.pdf.query.DocumentInfo;
import net.zerocloud.pdf.query.XmpMetadata;
import net.zerocloud.pdf.query.NamedDestinations;
import net.zerocloud.pdf.query.OutlineTree;
import net.zerocloud.pdf.query.ReadEmbeddedFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class DocumentMetadataFacadeTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void infoChangesAndUnknownValuesSurvivePublicationAndReopen() throws Exception {
        Path source = temporary.getRoot().toPath().resolve("source.pdf");
        Path output = temporary.getRoot().toPath().resolve("changed.pdf");
        PdfDictionary unknown = PdfDictionary.builder()
                .put(PdfName.of("Flag"), PdfBoolean.of(true))
                .put(PdfName.of("Numbers"), PdfArray.of(PdfNumber.of(1), PdfNumber.of(2)))
                .build();
        new DocumentWorkflow().execute(WorkflowRequest.builder()
                .target("source", PublicationTarget.path(source)).saveMode(SaveMode.REWRITE).build(), session -> {
            session.execute(AddBlankPage.INSTANCE);
            session.execute(UpdateDocumentInfo.version1().set("Title", text("Source title"))
                    .set("Author", text("Source author")).set("T73Keep", unknown).build());
            return null;
        });
        byte[] original = Files.readAllBytes(source);
        PdfDictionary detached;
        PdfDocument document = new PdfDocument(new PdfReader(source.toString()), new PdfWriter(output.toString()));
        try {
            PdfDocumentInfo info = document.getDocumentInfo();
            info.updateEntries(Collections.<String, PdfValue>singletonMap("Title", text("Changed title")),
                    Collections.singletonList("Author"));
            detached = info.getEntries();
            assertChangedInfo(detached);
        } finally {
            document.close();
        }
        assertEquals(PublicationStatus.COMMITTED, document.getPublicationReceipts().get(0).getStatus());
        assertArrayEquals(original, Files.readAllBytes(source));
        PdfDictionary nativeReopened = new DocumentWorkflow().execute(
                WorkflowRequest.open(output, SaveMode.REWRITE),
                session -> session.query(DocumentInfo.INSTANCE)).getResult();
        assertChangedInfo(detached);
        assertChangedInfo(nativeReopened);
        try (PdfDocument reopened = new PdfDocument(new PdfReader(output.toString()))) {
            assertChangedInfo(reopened.getDocumentInfo().getEntries());
        }
    }

    @Test
    public void textInfoRoundTripsUnicodeExplicitDatesAndEntryRemoval() throws Exception {
        Path currentOutput = temporary.getRoot().toPath().resolve("current-info.pdf");
        Instant earliestDate = Instant.now().minusSeconds(5);
        String currentCreationDate;
        String currentModDate;
        try (PdfDocument document = new PdfDocument(new PdfWriter(currentOutput.toString()))) {
            document.addNewPage();
            PdfDocumentInfo info = document.getDocumentInfo();
            assertSame(info, info.addCreationDate());
            currentCreationDate = info.getMoreInfo("CreationDate");
            assertCurrentPdfDate(currentCreationDate, earliestDate);
            assertSame(info, info.addModDate());
            currentModDate = info.getMoreInfo("ModDate");
            assertCurrentPdfDate(currentModDate, earliestDate);
        }
        try (PdfDocument reopened = new PdfDocument(new PdfReader(currentOutput.toString()))) {
            assertEquals(currentCreationDate,
                    reopened.getDocumentInfo().getMoreInfo("CreationDate"));
            assertEquals(currentModDate, reopened.getDocumentInfo().getMoreInfo("ModDate"));
        }

        Path output = temporary.getRoot().toPath().resolve("text-info.pdf");
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            document.addNewPage();
            PdfDocumentInfo info = document.getDocumentInfo();
            info.setTitle("\u6807\u9898").setAuthor("Author").setSubject("Subject")
                    .setKeywords("one, two").setCreator("Creator").setProducer("Producer")
                    .setTrapped(new net.zerocloud.pdf.itext7.kernel.pdf.PdfName("False"));
            Map<String, String> extra = new LinkedHashMap<String, String>();
            extra.put("CreationDate", "D:20260910010203+08'00'");
            extra.put("ModDate", "D:20260910020304Z");
            extra.put("Temporary", "remove this");
            info.setMoreInfo(extra);
            extra.clear();
            info.setMoreInfo("Temporary", null);
            info.setMoreInfo("Custom", "\u20ac\u2014\ud83d\udcc4");
            assertTextInfo(info);
        }
        try (PdfDocument reopened = new PdfDocument(new PdfReader(output.toString()))) {
            assertTextInfo(reopened.getDocumentInfo());
            PdfString title = (PdfString) reopened.getDocumentInfo().getEntries().get(PdfName.of("Title"));
            assertArrayEquals(new byte[] {(byte) 0xfe, (byte) 0xff, 0x68, 0x07, (byte) 0x98, (byte) 0x98},
                    title.getBytes());
        }
    }

    @Test
    public void xmpPacketsAreCopiedBoundedAndRejectedAtomicallyBeforeReopen() throws Exception {
        Path output = temporary.getRoot().toPath().resolve("xmp.pdf");
        byte[] expected = ("<?xpacket begin=\"\ufeff\"?>\n"
                + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" xmlns:t=\"urn:folio:t73\" t:keep=\"yes\">"
                + "<t:private>\u672a\u77e5</t:private></x:xmpmeta>\n<?xpacket end=\"w\"?>")
                .getBytes(StandardCharsets.UTF_8);
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            document.addNewPage();
            assertNull(document.getXmpMetadata());
            byte[] supplied = expected.clone();
            document.setXmpMetadata(supplied);
            supplied[0] = 0;
            assertArrayEquals(expected, document.getXmpMetadata(expected.length));
            byte[] read = document.getXmpMetadata();
            read[0] = 0;
            assertArrayEquals(expected, document.getXmpMetadata());
            expectMetadataFailure(DocumentFailureCode.METADATA_LIMIT_EXCEEDED,
                    () -> document.getXmpMetadata(expected.length - 1));
            DocumentFailure invalid = expectMetadataFailure(DocumentFailureCode.COMMAND_REJECTED,
                    () -> document.setXmpMetadata("<x:xmpmeta>private-malformed".getBytes(StandardCharsets.UTF_8)));
            assertEquals("The XMP packet is not a well-formed XMP metadata packet.", invalid.getDiagnostic());
            assertArrayEquals(expected, document.getXmpMetadata());
        }
        assertArrayEquals(expected, new DocumentWorkflow().execute(WorkflowRequest.open(output, SaveMode.REWRITE),
                session -> session.query(XmpMetadata.version1(expected.length))).getResult());
        try (PdfDocument reopened = new PdfDocument(new PdfReader(output.toString()))) {
            assertArrayEquals(expected, reopened.getXmpMetadata());
        }
    }

    @Test
    public void outlinesAndAllDestinationStylesFollowOriginalPagesThroughMoveAndCopy() throws Exception {
        Path output = temporary.getRoot().toPath().resolve("navigation.pdf");
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            document.addNewPage();
            document.addNewPage();
            document.addNewPage();
            document.setNamedDestinations(destinations(1, 2, 3), Collections.<String>emptyList());
            document.addNamedDestination("temporary", PageDestination.fit(1));
            document.setNamedDestinations(Collections.<String, PageDestination>emptyMap(),
                    Collections.singletonList("temporary"));
            document.setOutlines(outlines(3));
            document.movePage(3, 1);
            document.copyPages(2, 2, 4);
            assertEquals(destinations(2, 3, 1), document.getNamedDestinations(8));
            assertEquals(outlines(1), document.getOutlines(3));
            expectMetadataFailure(DocumentFailureCode.METADATA_LIMIT_EXCEEDED,
                    () -> document.getNamedDestinations(7));
            expectMetadataFailure(DocumentFailureCode.METADATA_LIMIT_EXCEEDED, () -> document.getOutlines(2));
            expectMetadataFailure(DocumentFailureCode.DESTINATION_CONFLICT, () -> document.removePage(3));
            assertEquals(4, document.getNumberOfPages());
            assertEquals(destinations(2, 3, 1), document.getNamedDestinations(8));
        }
        new DocumentWorkflow().execute(WorkflowRequest.open(output, SaveMode.REWRITE), session -> {
            assertEquals(destinations(2, 3, 1), session.query(NamedDestinations.version1(8)));
            assertEquals(outlines(1), session.query(OutlineTree.version1(3)));
            return null;
        });
        try (PdfDocument reopened = new PdfDocument(new PdfReader(output.toString()))) {
            assertEquals(destinations(2, 3, 1), reopened.getNamedDestinations(8));
            assertEquals(outlines(1), reopened.getOutlines(3));
        }
    }

    @Test
    public void attachmentsRetainExactPayloadHashesDescriptionsAndRelationships() throws Exception {
        Path output = temporary.getRoot().toPath().resolve("attachments.pdf");
        EmbeddedFileData retained;
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            document.addNewPage();
            byte[] content = "abc".getBytes(StandardCharsets.US_ASCII);
            EmbeddedFile file = EmbeddedFile.version1("payload.txt", content, "text/plain", "Primary payload",
                    EmbeddedFile.Relationship.DATA);
            content[0] = 0;
            document.addFileAttachment(file);
            document.addFileAttachment(EmbeddedFile.version1("\u6d4b\u8bd5.bin", new byte[] {0, 1, (byte) 255}));
            retained = document.getFileAttachment("payload.txt", 3).get();
            assertPrimaryPayload(retained);
            retained.getContent()[0] = 0;
            assertPrimaryPayload(document.getFileAttachment("payload.txt", 3).get());
            List<EmbeddedFileSummary> files = document.getFileAttachments(2);
            assertEquals(2, files.size());
            assertEquals("payload.txt", files.get(0).getName());
            assertEquals("\u6d4b\u8bd5.bin", files.get(1).getName());
            assertEquals(3, files.get(0).getSize());
            assertEquals("900150983cd24fb0d6963f7d28e17f72", files.get(0).getMd5Hex().get());
            assertEquals("text/plain", files.get(0).getMimeSubtype().get());
            assertEquals("Primary payload", files.get(0).getDescription().get());
            assertEquals(EmbeddedFile.Relationship.DATA, files.get(0).getRelationship());
            assertTrue(!document.getFileAttachment("absent", 0).isPresent());
            expectMetadataFailure(DocumentFailureCode.METADATA_LIMIT_EXCEEDED,
                    () -> document.getFileAttachment("payload.txt", 2));
            expectMetadataFailure(DocumentFailureCode.METADATA_LIMIT_EXCEEDED,
                    () -> document.getFileAttachments(1));
            expectMetadataFailure(DocumentFailureCode.COMMAND_REJECTED,
                    () -> document.addFileAttachment(EmbeddedFile.version1("payload.txt", new byte[] {9}, "text/\u2603")));
            assertPrimaryPayload(document.getFileAttachment("payload.txt", 3).get());
        }
        assertPrimaryPayload(retained);
        EmbeddedFileData nativeReopened = new DocumentWorkflow().execute(WorkflowRequest.open(output, SaveMode.REWRITE),
                session -> session.query(ReadEmbeddedFile.version1("payload.txt", 3)).get()).getResult();
        assertPrimaryPayload(nativeReopened);
        try (PdfDocument reopened = new PdfDocument(new PdfReader(output.toString()))) {
            assertPrimaryPayload(reopened.getFileAttachment("payload.txt", 3).get());
            assertArrayEquals(new byte[] {0, 1, (byte) 255},
                    reopened.getFileAttachment("\u6d4b\u8bd5.bin", 3).get().getContent());
        }
    }

    @Test
    public void mergeRenamesCollisionsAndSplitFiltersReferencesWithoutChangingSourcesOrSiblings() throws Exception {
        Path primary = metadataSource("primary", "abc", 2);
        Path appendix = metadataSource("appendix", "hello\n", 1);
        byte[] primaryBytes = Files.readAllBytes(primary);
        byte[] appendixBytes = Files.readAllBytes(appendix);
        Path merged = temporary.getRoot().toPath().resolve("merged.pdf");
        Path left = temporary.getRoot().toPath().resolve("left.pdf");
        Path right = temporary.getRoot().toPath().resolve("right.pdf");
        Map<String, PdfReader> sources = new LinkedHashMap<String, PdfReader>();
        sources.put("primary", new PdfReader(primary.toString()));
        sources.put("appendix", new PdfReader(appendix.toString()));
        Map<String, PdfWriter> targets = new LinkedHashMap<String, PdfWriter>();
        targets.put("left", new PdfWriter(left.toString()));
        targets.put("merged", new PdfWriter(merged.toString()));
        targets.put("right", new PdfWriter(right.toString()));
        PdfDocument document = new PdfDocument(sources, "primary", targets);
        try {
            for (PdfReader reader : sources.values()) {
                reader.close();
            }
            document.getMerger().merge("appendix");
            assertEquals(PageDestination.fit(1), document.getNamedDestinations(2).get("shared"));
            assertEquals(PageDestination.fit(3), document.getNamedDestinations(2).get("shared-1"));
            assertEquals("shared-1", document.getOutlines(2).get(1).getNamedDestination().get());
            assertAppendixPayload(document.getFileAttachment("payload.txt-1", 6).get());
            document.getSplitter().extractPageRanges(new String[] {"right", "merged", "left"},
                    new PageRange[] {PageRange.of(3, 3), PageRange.of(1, 3), PageRange.of(1, 2)});
            try {
                document.setXmpMetadata(xmp("too late"));
                fail("Split makes later Commands terminal");
            } catch (PdfException expected) {
                assertEquals(DocumentFailureCode.COMMAND_REJECTED, ((DocumentFailure) expected.getCause()).getCode());
            }
        } finally {
            document.close();
        }
        List<PublicationReceipt> receipts = document.getPublicationReceipts();
        assertEquals(3, receipts.size());
        assertEquals("left", receipts.get(0).getTargetName());
        assertEquals("merged", receipts.get(1).getTargetName());
        assertEquals("right", receipts.get(2).getTargetName());
        for (PublicationReceipt receipt : receipts) {
            assertEquals(PublicationStatus.COMMITTED, receipt.getStatus());
        }
        try (PdfDocument reopened = new PdfDocument(new PdfReader(merged.toString()))) {
            assertEquals(3, reopened.getNumberOfPages());
            assertEquals(2, reopened.getNamedDestinations(2).size());
            assertEquals(PageDestination.fit(3), reopened.getNamedDestinations(2).get("shared-1"));
            assertEquals("shared-1", reopened.getOutlines(2).get(1).getNamedDestination().get());
        }
        for (Path product : Arrays.asList(left, right)) {
            try (PdfDocument reopened = new PdfDocument(new PdfReader(product.toString()))) {
                String targetName = product.equals(left) ? "shared" : "shared-1";
                assertEquals(Collections.singletonMap(targetName, PageDestination.fit(1)), reopened.getNamedDestinations(1));
                assertEquals(targetName, reopened.getOutlines(1).get(0).getNamedDestination().get());
                assertEquals("primary", reopened.getDocumentInfo().getTitle());
                assertArrayEquals(xmp("primary"), reopened.getXmpMetadata());
                assertEquals("abc", new String(reopened.getFileAttachment("payload.txt", 3).get().getContent(),
                        StandardCharsets.US_ASCII));
                assertAppendixPayload(reopened.getFileAttachment("payload.txt-1", 6).get());
            }
        }
        byte[] sibling = Files.readAllBytes(right);
        try (PdfDocument rewrite = new PdfDocument(new PdfReader(left.toString()), new PdfWriter(left.toString()))) {
            rewrite.getDocumentInfo().setTitle("only left changes");
            rewrite.setXmpMetadata(xmp("only left"));
            rewrite.addFileAttachment(EmbeddedFile.version1("payload.txt", new byte[] {0}));
        }
        assertArrayEquals(primaryBytes, Files.readAllBytes(primary));
        assertArrayEquals(appendixBytes, Files.readAllBytes(appendix));
        assertArrayEquals(sibling, Files.readAllBytes(right));
    }

    @Test
    public void explicitPdf20PublicationRetainsAttachmentRelationshipAndVersion() throws Exception {
        Path source = temporary.getRoot().toPath().resolve("pdf20-source.pdf");
        Path output = temporary.getRoot().toPath().resolve("pdf20-out.pdf");
        new DocumentWorkflow().execute(WorkflowRequest.builder()
                .target("source", PublicationTarget.path(source)).saveMode(SaveMode.REWRITE)
                .outputPolicy(net.zerocloud.pdf.PdfOutputPolicy.version(net.zerocloud.pdf.PdfVersion.PDF_2_0))
                .build(), session -> { session.execute(AddBlankPage.INSTANCE); return null; });
        PdfDocument document = new PdfDocument(Collections.singletonMap("source", new PdfReader(source.toString())),
                "source", Collections.singletonMap("out", new PdfWriter(output.toString())),
                net.zerocloud.pdf.PdfVersion.PDF_2_0);
        try {
            document.getDocumentInfo().setTitle("PDF 2.0 metadata");
            document.addFileAttachment(EmbeddedFile.version1("payload.txt", "abc".getBytes(StandardCharsets.US_ASCII),
                    "text/plain", "Primary payload", EmbeddedFile.Relationship.DATA));
        } finally {
            document.close();
        }
        assertEquals(PublicationStatus.COMMITTED, document.getPublicationReceipts().get(0).getStatus());
        assertTrue(new String(Files.readAllBytes(output), StandardCharsets.ISO_8859_1).startsWith("%PDF-2.0\n"));
        try (PdfDocument reopened = new PdfDocument(new PdfReader(output.toString()))) {
            assertEquals("PDF 2.0 metadata", reopened.getDocumentInfo().getTitle());
            assertPrimaryPayload(reopened.getFileAttachment("payload.txt", 3).get());
        }
    }

    @Test
    public void rejectedNavigationBatchesPreserveAllEntriesAndReopenedOutline() throws Exception {
        Path source = metadataSource("atomic", "abc", 1);
        Path output = temporary.getRoot().toPath().resolve("atomic-out.pdf");
        byte[] original = Files.readAllBytes(source);
        List<OutlineItem> retained;
        try (PdfDocument document = new PdfDocument(new PdfReader(source.toString()), new PdfWriter(output.toString()))) {
            retained = document.getOutlines(1);
            try {
                document.setNamedDestinations(Collections.singletonMap("new", PageDestination.fit(1)),
                        Collections.singletonList("shared"));
                fail("A removal batch cannot orphan an outline");
            } catch (PdfException expected) {
                DocumentFailure failure = (DocumentFailure) expected.getCause();
                assertEquals(DocumentFailureCode.DESTINATION_CONFLICT, failure.getCode());
                assertEquals("document.metadata.outlines-destinations-attachments", failure.getCapabilityId());
                assertEquals("A named destination removal conflicts with an existing outline.", failure.getDiagnostic());
            }
            expectMetadataFailure(DocumentFailureCode.COMMAND_REJECTED,
                    () -> document.setOutlines(Arrays.asList(OutlineItem.toPage("valid", PageDestination.fit(1),
                            Collections.<OutlineItem>emptyList()), OutlineItem.toNamedDestination("missing", "absent",
                            Collections.<OutlineItem>emptyList()))));
            assertEquals(Collections.singletonMap("shared", PageDestination.fit(1)), document.getNamedDestinations(1));
            assertEquals(retained, document.getOutlines(1));
        }
        assertArrayEquals(original, Files.readAllBytes(source));
        try (PdfDocument reopened = new PdfDocument(new PdfReader(output.toString()))) {
            assertEquals(Collections.singletonMap("shared", PageDestination.fit(1)), reopened.getNamedDestinations(1));
            assertEquals(retained, reopened.getOutlines(1));
        }
    }

    @Test
    public void metadataViewsRespectCallerOwnershipThreadConfinementAndDetachedLifetimes() throws Exception {
        Path source = metadataSource("owned", "abc", 1);
        TrackedInput input = new TrackedInput(Files.readAllBytes(source));
        TrackedOutput output = new TrackedOutput();
        PdfReader reader = new PdfReader(input);
        PdfDocument document = new PdfDocument(reader, new PdfWriter(output));
        PdfDocumentInfo info = document.getDocumentInfo();
        Map<String, PageDestination> destinations;
        List<OutlineItem> outline;
        List<EmbeddedFileSummary> files;
        EmbeddedFileData data;
        try {
            reader.close();
            Files.delete(source);
            assertFalse(input.closed);
            AtomicReference<Throwable> crossThread = new AtomicReference<Throwable>();
            Thread other = new Thread(() -> {
                try {
                    info.getTitle();
                } catch (Throwable failure) {
                    crossThread.set(failure);
                }
            });
            other.start();
            other.join(5000);
            assertFalse(other.isAlive());
            assertTrue(crossThread.get() instanceof IllegalStateException);
            info.setTitle("Updated owned title");
            destinations = document.getNamedDestinations(1);
            outline = document.getOutlines(1);
            files = document.getFileAttachments(1);
            data = document.getFileAttachment("payload.txt", 3).get();
        } finally {
            document.close();
        }
        document.close();
        assertFalse(input.closed);
        assertFalse(output.closed);
        assertEquals(PublicationStatus.COMMITTED, document.getPublicationReceipts().get(0).getStatus());
        assertEquals(PageDestination.fit(1), destinations.get("shared"));
        assertEquals("owned", outline.get(0).getTitle());
        assertEquals("payload.txt", files.get(0).getName());
        assertArrayEquals(new byte[] {97, 98, 99}, data.getContent());
        for (Runnable mutation : Arrays.<Runnable>asList(destinations::clear, outline::clear, files::clear)) {
            try {
                mutation.run();
                fail("Detached observations are immutable");
            } catch (UnsupportedOperationException expected) {
                assertEquals(1, destinations.size());
            }
        }
        expectStateFailure(() -> info.getEntries());
        expectStateFailure(() -> info.setTitle("closed"));
        expectStateFailure(() -> document.getXmpMetadata());
        try (PdfDocument reopened = new PdfDocument(new PdfReader(new ByteArrayInputStream(output.toByteArray())))) {
            assertEquals("Updated owned title", reopened.getDocumentInfo().getTitle());
            for (Runnable mutation : Arrays.<Runnable>asList(
                    () -> reopened.getDocumentInfo().setTitle("read only"),
                    () -> reopened.setXmpMetadata(xmp("read only")),
                    () -> reopened.addNamedDestination("new", PageDestination.fit(1)),
                    () -> reopened.setOutlines(Collections.<OutlineItem>emptyList()),
                    () -> reopened.addFileAttachment(EmbeddedFile.version1("new", new byte[0])))) {
                expectStateFailure(mutation);
            }
            assertEquals("Updated owned title", reopened.getDocumentInfo().getTitle());
            assertEquals(1, reopened.getOutlines(1).size());
        }
    }

    private static void expectStateFailure(Runnable operation) {
        try {
            operation.run();
            fail("Expected the facade lifecycle to reject the operation");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().startsWith("The facade document")
                    || expected.getMessage().startsWith("A read-only facade document"));
        }
    }

    private static final class TrackedInput extends ByteArrayInputStream {
        private boolean closed;

        TrackedInput(byte[] content) {
            super(content);
        }

        @Override public void close() {
            closed = true;
        }
    }

    private static final class TrackedOutput extends ByteArrayOutputStream {
        private boolean closed;

        @Override public void close() {
            closed = true;
        }
    }

    private Path metadataSource(String label, String payload, int pages) throws Exception {
        Path output = temporary.getRoot().toPath().resolve(label + ".pdf");
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            for (int page = 0; page < pages; page++) {
                document.addNewPage();
            }
            document.getDocumentInfo().setTitle(label);
            document.setXmpMetadata(xmp(label));
            document.addNamedDestination("shared", PageDestination.fit(1));
            document.setOutlines(Collections.singletonList(OutlineItem.toNamedDestination(label, "shared",
                    Collections.<OutlineItem>emptyList())));
            document.addFileAttachment(EmbeddedFile.version1("payload.txt", payload.getBytes(StandardCharsets.US_ASCII),
                    "text/plain", label + " payload", EmbeddedFile.Relationship.SUPPLEMENT));
        }
        return output;
    }

    private static byte[] xmp(String label) {
        return ("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" xmlns:t=\"urn:folio:t73\">"
                + "<t:label>" + label + "</t:label></x:xmpmeta>").getBytes(StandardCharsets.UTF_8);
    }

    private static void assertAppendixPayload(EmbeddedFileData file) {
        assertEquals("payload.txt-1", file.getName());
        assertEquals("text/plain", file.getMimeSubtype().get());
        assertEquals("appendix payload", file.getDescription().get());
        assertEquals(EmbeddedFile.Relationship.SUPPLEMENT, file.getRelationship());
        assertArrayEquals(new byte[] {104, 101, 108, 108, 111, 10}, file.getContent());
        assertEquals("b1946ac92492d2347c6235b4d2611184", file.getMd5Hex().get());
        assertEquals("5891b5b522d5df086d0ff0b110fbd9d21bb4fc7163af34d08286a2e846f6be03", file.getSha256Hex());
    }

    private static void assertPrimaryPayload(EmbeddedFileData file) {
        assertEquals("payload.txt", file.getName());
        assertEquals("text/plain", file.getMimeSubtype().get());
        assertEquals("Primary payload", file.getDescription().get());
        assertEquals(EmbeddedFile.Relationship.DATA, file.getRelationship());
        assertEquals(3, file.getSize());
        assertArrayEquals(new byte[] {97, 98, 99}, file.getContent());
        assertEquals("900150983cd24fb0d6963f7d28e17f72", file.getMd5Hex().get());
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", file.getSha256Hex());
    }

    private static Map<String, PageDestination> destinations(int pageA, int pageB, int pageC) {
        Map<String, PageDestination> targets = new LinkedHashMap<String, PageDestination>();
        targets.put("fit", PageDestination.fit(pageA));
        targets.put("fit-b", PageDestination.fitB(pageB));
        targets.put("fit-h", PageDestination.fitH(pageC, null));
        targets.put("fit-bh", PageDestination.fitBH(pageA, new BigDecimal("80.5")));
        targets.put("fit-v", PageDestination.fitV(pageB, null));
        targets.put("fit-bv", PageDestination.fitBV(pageC, new BigDecimal("12.25")));
        targets.put("fit-r", rectangle(pageB));
        targets.put("shared", PageDestination.xyz(pageB, null, new BigDecimal("77.5"), new BigDecimal("1.25")));
        return targets;
    }

    private static PageDestination rectangle(int page) {
        return PageDestination.fitR(page, BigDecimal.ONE, new BigDecimal("2"),
                new BigDecimal("90"), new BigDecimal("80"));
    }

    private static List<OutlineItem> outlines(int explicitPage) {
        return Collections.singletonList(OutlineItem.grouping("Branch", Arrays.asList(
                OutlineItem.toNamedDestination("Named", "shared", Collections.<OutlineItem>emptyList()),
                OutlineItem.toPage("Explicit", rectangle(explicitPage), Collections.<OutlineItem>emptyList()))));
    }

    private static DocumentFailure expectMetadataFailure(DocumentFailureCode code, Runnable operation) {
        try {
            operation.run();
            fail("Expected a safe metadata failure");
            return null;
        } catch (PdfException mapped) {
            assertTrue(mapped.getCause() instanceof DocumentFailure);
            DocumentFailure failure = (DocumentFailure) mapped.getCause();
            assertEquals(code, failure.getCode());
            assertEquals("document.metadata.outlines-destinations-attachments", failure.getCapabilityId());
            return failure;
        }
    }

    private static void assertTextInfo(PdfDocumentInfo info) {
        assertEquals("\u6807\u9898", info.getTitle());
        assertEquals("Author", info.getAuthor());
        assertEquals("Subject", info.getSubject());
        assertEquals("one, two", info.getKeywords());
        assertEquals("Creator", info.getCreator());
        assertEquals("Producer", info.getProducer());
        assertEquals("False", info.getTrapped().getValue());
        assertEquals("D:20260910010203+08'00'", info.getMoreInfo("CreationDate"));
        assertEquals("D:20260910020304Z", info.getMoreInfo("ModDate"));
        assertEquals("\u20ac\u2014\ud83d\udcc4", info.getMoreInfo("Custom"));
        assertNull(info.getMoreInfo("Temporary"));
        assertNull(info.getMoreInfo("Trapped"));
    }

    private static void assertCurrentPdfDate(String value, Instant earliest) {
        assertTrue(value, value.matches("D:[0-9]{14}(?:Z|[+-][0-9]{2}'[0-9]{2}')"));
        String normalized = value.substring(2).replace("'", "");
        Instant observed = OffsetDateTime.parse(normalized,
                DateTimeFormatter.ofPattern("yyyyMMddHHmmssXX", Locale.ROOT)).toInstant();
        assertFalse(observed.isBefore(earliest));
        assertFalse(observed.isAfter(Instant.now().plusSeconds(5)));
    }

    private static void assertChangedInfo(PdfDictionary entries) throws Exception {
        assertEquals(2, entries.size());
        assertEquals(text("Changed title"), entries.get(PdfName.of("Title")));
        assertNull(entries.get(PdfName.of("Author")));
        PdfDictionary kept = (PdfDictionary) entries.get(PdfName.of("T73Keep"));
        assertEquals(PdfBoolean.of(true), kept.get(PdfName.of("Flag")));
        PdfArray numbers = (PdfArray) kept.get(PdfName.of("Numbers"));
        assertEquals(2, numbers.size());
        assertEquals(PdfNumber.of(1), numbers.get(0));
        assertEquals(PdfNumber.of(2), numbers.get(1));
    }

    private static PdfString text(String value) {
        return PdfString.of(value.getBytes(StandardCharsets.US_ASCII));
    }
}
