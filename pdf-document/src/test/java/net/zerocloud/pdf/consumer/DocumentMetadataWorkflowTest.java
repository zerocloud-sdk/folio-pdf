package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.EmbeddedFile;
import net.zerocloud.pdf.EmbeddedFileData;
import net.zerocloud.pdf.EmbeddedFileSummary;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.OutlineItem;
import net.zerocloud.pdf.PageDestination;
import net.zerocloud.pdf.PageRange;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfDictionaryEntry;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfString;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.PdfValueKind;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.command.CopyPages;
import net.zerocloud.pdf.command.EmbedFile;
import net.zerocloud.pdf.command.InsertBlankPage;
import net.zerocloud.pdf.command.MergeDocuments;
import net.zerocloud.pdf.command.MovePages;
import net.zerocloud.pdf.command.RemovePages;
import net.zerocloud.pdf.command.ReplaceOutlineTree;
import net.zerocloud.pdf.command.SetNamedDestinations;
import net.zerocloud.pdf.command.SetXmpMetadata;
import net.zerocloud.pdf.command.SplitDocument;
import net.zerocloud.pdf.command.UpdateDocumentInfo;
import net.zerocloud.pdf.query.DocumentInfo;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.EmbeddedFiles;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.NamedDestinations;
import net.zerocloud.pdf.query.OutlineTree;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.ReadEmbeddedFile;
import net.zerocloud.pdf.query.XmpMetadata;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class DocumentMetadataWorkflowTest {

    private static final String CAPABILITY =
            "document.metadata.outlines-destinations-attachments";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void infoEntriesRoundTripThroughRewriteAndReopen() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve("output.pdf");
        createBlankDocument(input);

        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    assertEquals(0, readInfoEntries(session).size());
                    session.execute(UpdateDocumentInfo.version1()
                            .set("Title", PdfString.of(
                                    "Folio T11 metadata".getBytes(
                                            StandardCharsets.ISO_8859_1)))
                            .set("Author", PdfString.of(
                                    "ZeroCloud".getBytes(
                                            StandardCharsets.ISO_8859_1)))
                            .set("T11Custom", PdfName.of("CustomValue"))
                            .build());
                    Map<String, PdfValue> entries = readInfoEntries(session);
                    assertEquals(3, entries.size());
                    assertEquals(
                            PdfString.of("Folio T11 metadata".getBytes(
                                    StandardCharsets.ISO_8859_1)),
                            entries.get("Title"));
                    assertEquals(PdfName.of("CustomValue"),
                            entries.get("T11Custom"));
                    return null;
                });

        assertEquals(CAPABILITY, outcome.getCapabilityId());

        Map<String, PdfValue> reopened = new DocumentWorkflow().execute(
                sourceRequest(output),
                session -> readInfoEntries(session)).getResult();
        assertEquals(3, reopened.size());
        assertEquals(
                PdfString.of("Folio T11 metadata".getBytes(
                        StandardCharsets.ISO_8859_1)),
                reopened.get("Title"));
        assertEquals(
                PdfString.of("ZeroCloud".getBytes(StandardCharsets.ISO_8859_1)),
                reopened.get("Author"));
        assertEquals(PdfName.of("CustomValue"), reopened.get("T11Custom"));
    }

    @Test
    public void infoQueryReturnsDetachedValuesUsableAfterSessionEnds()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve("output.pdf");
        createBlankDocument(input);
        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(UpdateDocumentInfo.version1()
                            .set("Title", PdfString.of(
                                    "Detached".getBytes(
                                            StandardCharsets.ISO_8859_1)))
                            .build());
                    return null;
                });

        PdfDictionary retained = new DocumentWorkflow().execute(
                sourceRequest(output),
                session -> session.query(DocumentInfo.INSTANCE)).getResult();

        assertEquals(1, retained.size());
        assertEquals(
                PdfString.of("Detached".getBytes(StandardCharsets.ISO_8859_1)),
                retained.get(PdfName.of("Title")));
    }

    @Test
    public void infoUpdatePreservesUnknownEntriesThroughPageOpsAndReopen()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("info.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "info-output.pdf");
        java.nio.file.Files.write(input, documentInformationFixture());

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(InsertBlankPage.version1(2));
                    session.execute(UpdateDocumentInfo.version1()
                            .remove("Author")
                            .set("Title", PdfString.of(
                                    "Retitled".getBytes(
                                            StandardCharsets.ISO_8859_1)))
                            .build());
                    return null;
                });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            assertEquals(Integer.valueOf(2), session.query(PageCount.INSTANCE));
            Map<String, PdfValue> entries = readInfoEntries(session);
            assertEquals(3, entries.size());
            assertNull(entries.get("Author"));
            assertEquals(
                    PdfString.of("Retitled".getBytes(
                            StandardCharsets.ISO_8859_1)),
                    entries.get("Title"));
            assertEquals(
                    PdfString.of("keep me".getBytes(
                            StandardCharsets.ISO_8859_1)),
                    entries.get("T11Unknown"));
            assertEquals(PdfNumber.of(42L), entries.get("T11Number"));
            return null;
        });
    }

    @Test
    public void infoCommandRejectsStreamsAndReferencesAtomically()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        createBlankDocument(input);

        new DocumentWorkflow().execute(sourceRequest(input), session -> {
            try {
                session.execute(UpdateDocumentInfo.version1()
                        .set("Title", PdfString.of(
                                "must not be applied".getBytes(
                                        StandardCharsets.ISO_8859_1)))
                        .set("T11Stream", PdfStream.of(
                                PdfDictionary.builder().build(),
                                new byte[] {1, 2, 3}))
                        .build());
                fail("Expected the stream Info value to be rejected");
            } catch (DocumentFailure failure) {
                assertSafeMetadataFailure(
                        failure,
                        DocumentFailureCode.COMMAND_REJECTED,
                        "The Info command contains a value that document information cannot hold.");
            }
            assertEquals(0, readInfoEntries(session).size());
            return null;
        });
    }

    @Test
    public void infoCommandRejectsInvalidEntryNamesWithoutMutation()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        createBlankDocument(input);

        new DocumentWorkflow().execute(sourceRequest(input), session -> {
            for (String invalidName : Arrays.asList(
                    "", "Bad Name", "Bad(Name)", "Bad/Name")) {
                try {
                    session.execute(UpdateDocumentInfo.version1()
                            .set(invalidName, PdfName.of("Value"))
                            .build());
                    fail("Expected the invalid Info name to be rejected: "
                            + invalidName);
                } catch (DocumentFailure failure) {
                    assertSafeMetadataFailure(
                            failure,
                            DocumentFailureCode.COMMAND_REJECTED,
                            "The Info command contains an invalid entry name.");
                }
            }
            assertEquals(0, readInfoEntries(session).size());
            return null;
        });
    }

    @Test
    public void infoQueryRejectsUnprovenInfoGraphs() throws Exception {
        assertInfoQueryRejected(documentInformationPageReferenceFixture());
        assertInfoQueryRejected(documentInformationStreamFixture());
    }

    private void assertInfoQueryRejected(byte[] source) throws Exception {
        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("input",
                                DocumentSource.bytes(source, source.length))
                        .primarySource("input")
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    try {
                        session.query(DocumentInfo.INSTANCE);
                        fail("Expected the unproven Info graph to be rejected");
                    } catch (DocumentFailure failure) {
                        assertSafeMetadataFailure(
                                failure,
                                DocumentFailureCode.QUERY_FAILED,
                                "The document information could not be inspected safely.");
                    }
                    return null;
                });
    }

    @Test
    public void xmpRoundTripsThroughRewriteAndReopen() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve("output.pdf");
        createBlankDocument(input);
        byte[] packet = xmpPacket("Folio T11 XMP");

        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    assertNull(session.query(XmpMetadata.version1(4096L)));
                    session.execute(SetXmpMetadata.version1(packet));
                    assertArrayEquals(
                            packet,
                            session.query(XmpMetadata.version1(4096L)));
                    return null;
                });

        assertEquals(CAPABILITY, outcome.getCapabilityId());

        byte[] reopened = new DocumentWorkflow().execute(
                sourceRequest(output),
                session -> session.query(XmpMetadata.version1(4096L)))
                .getResult();
        assertArrayEquals(packet, reopened);
    }

    @Test
    public void xmpCommandAcceptsABomPrefixedPacket() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "bom-output.pdf");
        createBlankDocument(input);
        byte[] packet = xmpPacket("bom");
        byte[] bomPrefixed = new byte[packet.length + 3];
        bomPrefixed[0] = (byte) 0xEF;
        bomPrefixed[1] = (byte) 0xBB;
        bomPrefixed[2] = (byte) 0xBF;
        System.arraycopy(packet, 0, bomPrefixed, 3, packet.length);

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(SetXmpMetadata.version1(bomPrefixed));
                    assertArrayEquals(
                            bomPrefixed,
                            session.query(XmpMetadata.version1(65536L)));
                    return null;
                });

        byte[] reopened = new DocumentWorkflow().execute(
                sourceRequest(output),
                session -> session.query(XmpMetadata.version1(65536L)))
                .getResult();
        assertArrayEquals(bomPrefixed, reopened);
    }

    @Test
    public void xmpIsPreservedThroughPageOperationsAndReopen()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("xmp.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "xmp-output.pdf");
        byte[] packet = xmpPacket("pre-existing packet");
        java.nio.file.Files.write(input, xmpFixture(packet));

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(InsertBlankPage.version1(2));
                    return null;
                });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            assertEquals(Integer.valueOf(2), session.query(PageCount.INSTANCE));
            assertArrayEquals(
                    packet,
                    session.query(XmpMetadata.version1(4096L)));
            return null;
        });
    }

    @Test
    public void xmpReadHonorsTheCallerByteBound() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        createBlankDocument(input);
        byte[] packet = xmpPacket("bounded");

        new DocumentWorkflow().execute(sourceRequest(input), session -> {
            session.execute(SetXmpMetadata.version1(packet));
            try {
                session.query(XmpMetadata.version1(packet.length - 1L));
                fail("Expected the XMP byte bound to be enforced");
            } catch (DocumentFailure failure) {
                assertSafeMetadataFailure(
                        failure,
                        DocumentFailureCode.METADATA_LIMIT_EXCEEDED,
                        "The metadata access limit was exceeded.");
            }
            assertArrayEquals(
                    packet,
                    session.query(XmpMetadata.version1(packet.length)));
            return null;
        });
    }

    @Test
    public void xmpQueryRejectsMalformedMetadataStructures() throws Exception {
        assertXmpQueryRejected(malformedXmpNotAStreamFixture());
        assertXmpQueryRejected(malformedXmpWrongSubtypeFixture());
        assertXmpQueryRejected(malformedXmpMissingTypeFixture());
    }

    private void assertXmpQueryRejected(byte[] source) throws Exception {
        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("input",
                                DocumentSource.bytes(source, source.length))
                        .primarySource("input")
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    try {
                        session.query(XmpMetadata.version1(4096L));
                        fail("Expected the malformed XMP structure to fail");
                    } catch (DocumentFailure failure) {
                        assertSafeMetadataFailure(
                                failure,
                                DocumentFailureCode.QUERY_FAILED,
                                "The XMP metadata could not be inspected safely.");
                    }
                    return null;
                });
    }

    @Test
    public void xmpCommandRejectsInvalidPacketsWithoutMutation()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        createBlankDocument(input);

        new DocumentWorkflow().execute(sourceRequest(input), session -> {
            for (byte[] invalid : Arrays.asList(
                    new byte[0],
                    new byte[] {(byte) 0xFF, (byte) 0xFE, 0x01},
                    "not an xmp packet".getBytes(StandardCharsets.UTF_8))) {
                try {
                    session.execute(SetXmpMetadata.version1(invalid));
                    fail("Expected the invalid XMP packet to be rejected");
                } catch (DocumentFailure failure) {
                    assertSafeMetadataFailure(
                            failure,
                            DocumentFailureCode.COMMAND_REJECTED,
                            "The XMP packet is not a well-formed XMP metadata packet.");
                }
            }
            assertNull(session.query(XmpMetadata.version1(4096L)));
            return null;
        });
    }

    @Test
    public void xmpCommandRejectsMalformedXmlPacketsWithoutMutation()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        createBlankDocument(input);

        new DocumentWorkflow().execute(sourceRequest(input), session -> {
            for (byte[] malformed : Arrays.asList(
                    "<x:xmpmeta <unclosed".getBytes(StandardCharsets.UTF_8),
                    "<x:xmpmeta></x:wrong>".getBytes(StandardCharsets.UTF_8),
                    ("<?xml version=\"1.0\"?>"
                            + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">"
                            + "<broken></x:xmpmeta>")
                            .getBytes(StandardCharsets.UTF_8))) {
                try {
                    session.execute(SetXmpMetadata.version1(malformed));
                    fail("Expected the malformed XML packet to be rejected");
                } catch (DocumentFailure failure) {
                    assertSafeMetadataFailure(
                            failure,
                            DocumentFailureCode.COMMAND_REJECTED,
                            "The XMP packet is not a well-formed XMP metadata packet.");
                }
            }
            assertNull(session.query(XmpMetadata.version1(4096L)));
            return null;
        });
    }

    @Test
    public void xmpCommandRejectsOversizedPacketsWithoutMutation()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        createBlankDocument(input);
        byte[] oversized = new byte[64 * 1024 * 1024 + 1];
        Arrays.fill(oversized, (byte) 'x');

        new DocumentWorkflow().execute(sourceRequest(input), session -> {
            try {
                session.execute(SetXmpMetadata.version1(oversized));
                fail("Expected the oversized XMP packet to be rejected");
            } catch (DocumentFailure failure) {
                assertSafeMetadataFailure(
                        failure,
                        DocumentFailureCode.COMMAND_REJECTED,
                        "The XMP packet exceeds the supported metadata packet size.");
            }
            assertNull(session.query(XmpMetadata.version1(4096L)));
            return null;
        });
    }

    @Test
    public void xmpPreservesUnknownStreamEntriesThroughReopen()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("xmp.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "xmp-extra-output.pdf");
        byte[] packet = xmpPacket("extended");
        java.nio.file.Files.write(input, xmpExtendedFixture(packet));

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(InsertBlankPage.version1(2));
                    return null;
                });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            assertArrayEquals(
                    packet,
                    session.query(XmpMetadata.version1(4096L)));
            PdfDictionary catalog = (PdfDictionary) session.query(
                    InspectObject.version1(
                            session.query(DocumentRootReference.INSTANCE),
                            PdfInspectionLimits.of(4, 0L)));
            ObjectReference metadata = ((PdfIndirectReference) catalog.get(
                    PdfName.of("Metadata"))).getReference();
            PdfStream stream = (PdfStream) session.query(
                    InspectObject.version1(
                            metadata,
                            PdfInspectionLimits.of(8, 4096L)));
            assertEquals(
                    PdfName.of("Preserved"),
                    stream.getDictionary().get(PdfName.of("T11Extra")));
            assertArrayEquals(packet, stream.readBytes());
            return null;
        });
    }

    @Test
    public void xmpFilteredStreamRemainsReadableThroughReopen()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("xmp.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "xmp-filtered-output.pdf");
        byte[] packet = xmpPacket("filtered");
        java.nio.file.Files.write(input, xmpFilteredFixture(packet));

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(InsertBlankPage.version1(2));
                    return null;
                });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            assertArrayEquals(
                    packet,
                    session.query(XmpMetadata.version1(4096L)));
            return null;
        });
    }

    @Test
    public void namedDestinationsRoundTripThroughRewriteAndReopen()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve("output.pdf");
        createBlankDocument(input, 3);
        Map<String, PageDestination> expected =
                new LinkedHashMap<String, PageDestination>();
        expected.put("alpha", PageDestination.fit(1));
        expected.put(
                "bravo",
                PageDestination.xyz(
                        3,
                        null,
                        new BigDecimal("10"),
                        new BigDecimal("2.5")));
        expected.put(
                "charlie",
                PageDestination.fitR(
                        2,
                        new BigDecimal("0"),
                        new BigDecimal("0"),
                        new BigDecimal("100"),
                        new BigDecimal("100")));

        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    assertTrue(session.query(
                            NamedDestinations.version1(16)).isEmpty());
                    session.execute(SetNamedDestinations.version1()
                            .set("alpha", expected.get("alpha"))
                            .set("bravo", expected.get("bravo"))
                            .set("charlie", expected.get("charlie"))
                            .build());
                    assertEquals(
                            expected,
                            session.query(NamedDestinations.version1(16)));
                    return null;
                });

        assertEquals(CAPABILITY, outcome.getCapabilityId());

        Map<String, PageDestination> reopened = new DocumentWorkflow().execute(
                sourceRequest(output),
                session -> session.query(NamedDestinations.version1(16)))
                .getResult();
        assertEquals(
                new java.util.TreeMap<String, PageDestination>(expected),
                new java.util.TreeMap<String, PageDestination>(reopened));
    }

    @Test
    public void namedDestinationUpdatePreservesUnnamedEntries()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("dests.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "dests-output.pdf");
        java.nio.file.Files.write(input, namedDestinationsFixture());

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(SetNamedDestinations.version1()
                            .set("alpha", PageDestination.fit(1))
                            .remove("zebra")
                            .set(
                                    "new",
                                    PageDestination.fitH(
                                            1,
                                            new BigDecimal("600")))
                            .build());
                    return null;
                });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            Map<String, PageDestination> destinations =
                    session.query(NamedDestinations.version1(16));
            assertEquals(3, destinations.size());
            assertEquals(PageDestination.fit(1), destinations.get("alpha"));
            assertEquals(
                    PageDestination.fit(1),
                    destinations.get("middle"));
            assertEquals(
                    PageDestination.fitH(1, new BigDecimal("600")),
                    destinations.get("new"));
            assertNull(destinations.get("zebra"));
            return null;
        });
    }

    @Test
    public void namedDestinationCommandRejectsOutOfRangePagesAtomically()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        createBlankDocument(input, 1);

        new DocumentWorkflow().execute(sourceRequest(input), session -> {
            try {
                session.execute(SetNamedDestinations.version1()
                        .set("alpha", PageDestination.fit(1))
                        .set("beyond", PageDestination.fit(5))
                        .build());
                fail("Expected the out-of-range destination page to fail");
            } catch (DocumentFailure failure) {
                assertSafeMetadataFailure(
                        failure,
                        DocumentFailureCode.PAGE_RANGE_INVALID,
                        "The destination page is outside the current document.");
            }
            assertTrue(session.query(
                    NamedDestinations.version1(16)).isEmpty());
            return null;
        });
    }

    @Test
    public void namedDestinationRemovalDropsTheEmptyTree() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve("output.pdf");
        createBlankDocument(input, 2);

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(SetNamedDestinations.version1()
                            .set("alpha", PageDestination.fit(1))
                            .build());
                    session.execute(SetNamedDestinations.version1()
                            .remove("alpha")
                            .build());
                    return null;
                });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            assertTrue(session.query(
                    NamedDestinations.version1(16)).isEmpty());
            PdfDictionary catalog = (PdfDictionary) session.query(
                    InspectObject.version1(
                            session.query(DocumentRootReference.INSTANCE),
                            PdfInspectionLimits.of(4, 0L)));
            assertNull(catalog.get(PdfName.of("Names")));
            return null;
        });
    }

    @Test
    public void namedDestinationCommandPreservesUnknownNameSubtrees()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("dests.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "dests-subtrees.pdf");
        java.nio.file.Files.write(
                input,
                namedDestinationsWithUnknownSubtreeFixture());

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(SetNamedDestinations.version1()
                            .set("alpha", PageDestination.fit(1))
                            .build());
                    return null;
                });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            assertEquals(
                    PageDestination.fit(1),
                    session.query(NamedDestinations.version1(16))
                            .get("alpha"));
            PdfDictionary catalog = (PdfDictionary) session.query(
                    InspectObject.version1(
                            session.query(DocumentRootReference.INSTANCE),
                            PdfInspectionLimits.of(8, 0L)));
            PdfDictionary names = dereferenceDictionary(
                    session,
                    catalog.get(PdfName.of("Names")));
            PdfValue javaScript = names.get(PdfName.of("JavaScript"));
            if (javaScript instanceof PdfIndirectReference) {
                javaScript = session.query(InspectObject.version1(
                        ((PdfIndirectReference) javaScript).getReference(),
                        PdfInspectionLimits.of(8, 0L)));
            }
            assertEquals(PdfValueKind.DICTIONARY, javaScript.getKind());
            return null;
        });
    }

    @Test
    public void namedDestinationsQueryRejectsMalformedTrees() throws Exception {
        assertNamedDestinationsQueryRejected(unsortedNameTreeFixture());
        assertNamedDestinationsQueryRejected(badLimitsNameTreeFixture());
        assertNamedDestinationsQueryRejected(cyclicNameTreeFixture());
        assertNamedDestinationsQueryRejected(nonArrayDestinationFixture());
        assertNamedDestinationsQueryRejected(danglingDestinationFixture());
        assertNamedDestinationsQueryRejected(nonStringKeyFixture());
    }

    @Test
    public void namedDestinationsQueryRejectsDisorderedKidTrees()
            throws Exception {
        assertNamedDestinationsQueryRejected(duplicateKeyAcrossKidsFixture());
        assertNamedDestinationsQueryRejected(outOfOrderKidsFixture());
    }

    @Test
    public void namedDestinationsQueryRejectsExcessivelyDeepTrees()
            throws Exception {
        assertNamedDestinationsQueryRejected(deepNameTreeFixture(70));
    }

    @Test
    public void infoQueryRejectsExcessivelyDeepGraphs() throws Exception {
        byte[] source = deepInfoFixture(70);
        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("input",
                                DocumentSource.bytes(source, source.length))
                        .primarySource("input")
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    try {
                        session.query(DocumentInfo.INSTANCE);
                        fail("Expected the deep information graph to fail");
                    } catch (DocumentFailure failure) {
                        assertSafeMetadataFailure(
                                failure,
                                DocumentFailureCode.QUERY_FAILED,
                                "The document information could not be inspected safely.");
                    }
                    return null;
                });
    }

    @Test
    public void pageMutationsRejectExcessivelyDeepInfoGraphs()
            throws Exception {
        assertPageMutationRejects(deepInfoFixture(70), "deep-info.pdf");
    }

    private void assertNamedDestinationsQueryRejected(byte[] source)
            throws Exception {
        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("input",
                                DocumentSource.bytes(source, source.length))
                        .primarySource("input")
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    try {
                        session.query(NamedDestinations.version1(16));
                        fail("Expected the malformed name tree to fail");
                    } catch (DocumentFailure failure) {
                        assertSafeMetadataFailure(
                                failure,
                                DocumentFailureCode.QUERY_FAILED,
                                "The named destinations could not be inspected safely.");
                    }
                    return null;
                });
    }

    @Test
    public void namedDestinationsQueryHonorsTheEntryBound() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("dests.pdf");
        java.nio.file.Files.write(input, namedDestinationsFixture());

        new DocumentWorkflow().execute(sourceRequest(input), session -> {
            try {
                session.query(NamedDestinations.version1(2));
                fail("Expected the entry bound to be enforced");
            } catch (DocumentFailure failure) {
                assertSafeMetadataFailure(
                        failure,
                        DocumentFailureCode.METADATA_LIMIT_EXCEEDED,
                        "The metadata access limit was exceeded.");
            }
            assertEquals(
                    3,
                    session.query(NamedDestinations.version1(3)).size());
            return null;
        });
    }

    @Test
    public void namedDestinationCommandRejectsMalformedExistingTrees()
            throws Exception {
        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("input", DocumentSource.bytes(
                                unsortedNameTreeFixture(),
                                unsortedNameTreeFixture().length))
                        .primarySource("input")
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    try {
                        session.execute(SetNamedDestinations.version1()
                                .set("alpha", PageDestination.fit(1))
                                .build());
                        fail("Expected the malformed tree to reject the command");
                    } catch (DocumentFailure failure) {
                        assertSafeMetadataFailure(
                                failure,
                                DocumentFailureCode.COMMAND_REJECTED,
                                "The named destinations could not be updated safely.");
                    }
                    return null;
                });
    }

    @Test
    public void outlineRoundTripsThroughRewrite() throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve(
                "outline-round-trip.pdf");
        createBlankDocument(target, 3);

        List<OutlineItem> outline = Arrays.asList(
                OutlineItem.toPage(
                        "Chapter 1",
                        PageDestination.fit(1),
                        Arrays.asList(OutlineItem.toNamedDestination(
                                "Section 1.1",
                                "s11",
                                Arrays.<OutlineItem>asList()))),
                OutlineItem.grouping(
                        "Part II",
                        Arrays.asList(OutlineItem.toPage(
                                "Appendix",
                                PageDestination.xyz(
                                        3,
                                        BigDecimal.ZERO,
                                        new BigDecimal(700),
                                        BigDecimal.ONE),
                                Arrays.<OutlineItem>asList()))));
        Path updated = temporaryFolder.getRoot().toPath().resolve(
                "outline-round-trip-written.pdf");

        new DocumentWorkflow().execute(
                rewriteRequest(target, updated),
                session -> {
                    session.execute(SetNamedDestinations.version1()
                            .set("s11", PageDestination.fit(2))
                            .build());
                    session.execute(ReplaceOutlineTree.version1(outline));
                    return null;
                });

        new DocumentWorkflow().execute(sourceRequest(updated), session -> {
            assertEquals(
                    outline,
                    session.query(OutlineTree.version1(16)));
            assertEquals(
                    PageDestination.fit(2),
                    session.query(NamedDestinations.version1(4)).get("s11"));
            return null;
        });
    }

    @Test
    public void outlineQueryReturnsEmptyWhenAbsent() throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve(
                "outline-absent.pdf");
        createBlankDocument(target, 2);

        new DocumentWorkflow().execute(sourceRequest(target), session -> {
            assertTrue(session.query(OutlineTree.version1(4)).isEmpty());
            return null;
        });
    }

    @Test
    public void outlineDestinationsFollowPageIdentityThroughMove()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "outline-move.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "outline-moved.pdf");
        java.nio.file.Files.write(input, outlineFixture());

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(MovePages.version1(PageRange.of(3, 3), 1));
                    return null;
                });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            List<OutlineItem> outline = session.query(OutlineTree.version1(16));
            assertEquals(2, outline.size());
            assertEquals(
                    Optional.of("s11"),
                    outline.get(0).getNamedDestination());
            assertEquals(
                    PageDestination.fit(3),
                    session.query(NamedDestinations.version1(4)).get("s11"));
            OutlineItem appendix = outline.get(1).getChildren().get(0);
            assertEquals(
                    PageDestination.xyz(
                            2,
                            BigDecimal.ZERO,
                            new BigDecimal(700),
                            BigDecimal.ONE),
                    appendix.getDestination().get());
            return null;
        });
    }

    @Test
    public void outlineQueryRejectsMalformedTrees() throws Exception {
        assertOutlineQueryRejected(cyclicOutlineFixture());
        assertOutlineQueryRejected(outlineActionFixture());
        assertOutlineQueryRejected(outlineUnknownKeyFixture());
        assertOutlineQueryRejected(outlineBadLinkFixture());
        assertOutlineQueryRejected(outlineBadCountFixture());
        assertOutlineQueryRejected(outlineDanglingDestinationFixture());
        assertOutlineQueryRejected(outlineUnknownNamedReferenceFixture());
        assertOutlineQueryRejected(outlineNonDictionaryRootFixture());
    }

    @Test
    public void outlineCommandRejectsMalformedExistingTrees() throws Exception {
        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("input", DocumentSource.bytes(
                                cyclicOutlineFixture(),
                                cyclicOutlineFixture().length))
                        .primarySource("input")
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    try {
                        session.execute(ReplaceOutlineTree.version1(
                                Arrays.asList(OutlineItem.toPage(
                                        "Replacement",
                                        PageDestination.fit(1),
                                        Arrays.<OutlineItem>asList()))));
                        fail("Expected the malformed tree to reject the command");
                    } catch (DocumentFailure failure) {
                        assertSafeMetadataFailure(
                                failure,
                                DocumentFailureCode.COMMAND_REJECTED,
                                "The document outline could not be updated safely.");
                    }
                    return null;
                });
    }

    @Test
    public void outlineCommandValidatesDestinationsAtomically()
            throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve(
                "outline-atomic.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "outline-atomic-out.pdf");
        createBlankDocument(target, 2);

        new DocumentWorkflow().execute(
                rewriteRequest(target, output),
                session -> {
                    try {
                        session.execute(ReplaceOutlineTree.version1(
                                Arrays.asList(
                                        OutlineItem.toPage(
                                                "Valid",
                                                PageDestination.fit(1),
                                                Arrays.<OutlineItem>asList()),
                                        OutlineItem.toPage(
                                                "Invalid",
                                                PageDestination.fit(9),
                                                Arrays.<OutlineItem>asList()))));
                        fail("Expected the out-of-range page to be rejected");
                    } catch (DocumentFailure failure) {
                        assertSafeMetadataFailure(
                                failure,
                                DocumentFailureCode.PAGE_RANGE_INVALID,
                                "The destination page is outside the current document.");
                    }
                    assertTrue(session.query(OutlineTree.version1(4)).isEmpty());
                    try {
                        session.execute(ReplaceOutlineTree.version1(
                                Arrays.asList(OutlineItem.toNamedDestination(
                                        "Missing",
                                        "not-a-destination",
                                        Arrays.<OutlineItem>asList()))));
                        fail("Expected the unknown named destination to be rejected");
                    } catch (DocumentFailure failure) {
                        assertSafeMetadataFailure(
                                failure,
                                DocumentFailureCode.COMMAND_REJECTED,
                                "The document outline could not be updated safely.");
                    }
                    assertTrue(session.query(OutlineTree.version1(4)).isEmpty());
                    return null;
                });
    }

    @Test
    public void outlineQueryHonorsItemBound() throws Exception {
        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("input", DocumentSource.bytes(
                                outlineFixture(),
                                outlineFixture().length))
                        .primarySource("input")
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    try {
                        session.query(OutlineTree.version1(2));
                        fail("Expected the item bound to be enforced");
                    } catch (DocumentFailure failure) {
                        assertSafeMetadataFailure(
                                failure,
                                DocumentFailureCode.METADATA_LIMIT_EXCEEDED,
                                "The metadata access limit was exceeded.");
                    }
                    assertEquals(
                            2,
                            session.query(OutlineTree.version1(3)).size());
                    return null;
                });
    }

    @Test
    public void emptyOutlineReplacementRemovesTheOutline() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "outline-remove.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "outline-removed.pdf");
        java.nio.file.Files.write(input, outlineFixture());

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(ReplaceOutlineTree.version1(
                            Arrays.<OutlineItem>asList()));
                    return null;
                });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            assertTrue(session.query(OutlineTree.version1(4)).isEmpty());
            PdfDictionary catalog = (PdfDictionary) session.query(
                    InspectObject.version1(
                            session.query(DocumentRootReference.INSTANCE),
                            PdfInspectionLimits.of(8, 0L)));
            assertNull(catalog.get(PdfName.of("Outlines")));
            PdfDictionary names = dereferenceDictionary(
                    session,
                    catalog.get(PdfName.of("Names")));
            assertEquals(
                    PdfValueKind.DICTIONARY,
                    names.get(PdfName.of("Dests")).getKind());
            return null;
        });
    }

    private void assertOutlineQueryRejected(byte[] source) throws Exception {
        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("input", DocumentSource.bytes(
                                source, source.length))
                        .primarySource("input")
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    try {
                        session.query(OutlineTree.version1(64));
                        fail("Expected the malformed outline to be rejected");
                    } catch (DocumentFailure failure) {
                        assertSafeMetadataFailure(
                                failure,
                                DocumentFailureCode.QUERY_FAILED,
                                "The document outline could not be inspected safely.");
                    }
                    return null;
                });
    }

    @Test
    public void embeddedFileRoundTripsThroughRewrite() throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve(
                "embedded-round-trip.pdf");
        Path updated = temporaryFolder.getRoot().toPath().resolve(
                "embedded-round-trip-written.pdf");
        createBlankDocument(target, 1);

        new DocumentWorkflow().execute(
                rewriteRequest(target, updated),
                session -> {
                    session.execute(EmbedFile.version1(EmbeddedFile.version1(
                            "data.csv",
                            "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8),
                            "text/csv",
                            "source data",
                            EmbeddedFile.Relationship.SOURCE)));
                    return null;
                });

        new DocumentWorkflow().execute(sourceRequest(updated), session -> {
            List<EmbeddedFileSummary> files = session.query(
                    EmbeddedFiles.version1(8));
            assertEquals(1, files.size());
            EmbeddedFileSummary summary = files.get(0);
            assertEquals("data.csv", summary.getName());
            assertEquals(
                    Optional.of("text/csv"),
                    summary.getMimeSubtype());
            assertEquals(
                    Optional.of("source data"),
                    summary.getDescription());
            assertEquals(
                    EmbeddedFile.Relationship.SOURCE,
                    summary.getRelationship());
            assertEquals(8L, summary.getSize());

            Optional<EmbeddedFileData> data = session.query(
                    ReadEmbeddedFile.version1("data.csv", 1024L));
            assertTrue(data.isPresent());
            assertArrayEquals(
                    "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8),
                    data.get().getContent());
            assertEquals("data.csv", data.get().getName());
            assertEquals(8L, data.get().getSize());
            assertTrue(data.get().getMd5Hex().isPresent());
            assertEquals(32, data.get().getMd5Hex().get().length());
            assertEquals(64, data.get().getSha256Hex().length());
            assertEquals(
                    summary.getMd5Hex(),
                    data.get().getMd5Hex());
            return null;
        });
    }

    @Test
    public void embedFileReplacesAnExistingName() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "embedded-replace.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "embedded-replaced.pdf");
        java.nio.file.Files.write(input, embeddedFilesFixture());

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(EmbedFile.version1(EmbeddedFile.version1(
                            "data.txt",
                            "replacement".getBytes(StandardCharsets.UTF_8))));
                    return null;
                });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            List<EmbeddedFileSummary> files = session.query(
                    EmbeddedFiles.version1(8));
            assertEquals(1, files.size());
            assertEquals(11L, files.get(0).getSize());
            assertEquals(
                    Optional.empty(),
                    files.get(0).getMimeSubtype());
            assertEquals(
                    EmbeddedFile.Relationship.UNSPECIFIED,
                    files.get(0).getRelationship());
            Optional<EmbeddedFileData> data = session.query(
                    ReadEmbeddedFile.version1("data.txt", 1024L));
            assertTrue(data.isPresent());
            assertArrayEquals(
                    "replacement".getBytes(StandardCharsets.UTF_8),
                    data.get().getContent());
            return null;
        });
    }

    @Test
    public void embeddedFilesQueryReturnsEmptyWhenAbsent() throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve(
                "embedded-absent.pdf");
        createBlankDocument(target, 1);

        new DocumentWorkflow().execute(sourceRequest(target), session -> {
            assertTrue(session.query(EmbeddedFiles.version1(4)).isEmpty());
            Optional<EmbeddedFileData> missing = session.query(
                    ReadEmbeddedFile.version1("missing.bin", 1024L));
            assertEquals(Optional.empty(), missing);
            return null;
        });
    }

    @Test
    public void embeddedFileMimeTypeEscapesTheSolidus() throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve(
                "embedded-mime.pdf");
        Path updated = temporaryFolder.getRoot().toPath().resolve(
                "embedded-mime-written.pdf");
        createBlankDocument(target, 1);

        new DocumentWorkflow().execute(
                rewriteRequest(target, updated),
                session -> {
                    session.execute(EmbedFile.version1(EmbeddedFile.version1(
                            "report.csv",
                            new byte[]{1, 2, 3},
                            "text/csv")));
                    return null;
                });

        new DocumentWorkflow().execute(sourceRequest(updated), session -> {
            List<EmbeddedFileSummary> files = session.query(
                    EmbeddedFiles.version1(4));
            assertEquals(1, files.size());
            assertEquals(
                    Optional.of("text/csv"),
                    files.get(0).getMimeSubtype());
            return null;
        });
    }

    @Test
    public void embedFileRejectsNonAsciiMimeSubtypesAtomically()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "embedded-non-ascii.pdf");
        createBlankDocument(input, 1);

        new DocumentWorkflow().execute(sourceRequest(input), session -> {
            try {
                session.execute(EmbedFile.version1(EmbeddedFile.version1(
                        "data.txt",
                        "content".getBytes(StandardCharsets.UTF_8),
                        "application/vnd.€x")));
                fail("Expected the non-ASCII MIME subtype to be rejected");
            } catch (DocumentFailure failure) {
                assertSafeMetadataFailure(
                        failure,
                        DocumentFailureCode.COMMAND_REJECTED,
                        "The embedded files could not be updated safely.");
            }
            assertTrue(session.query(EmbeddedFiles.version1(4)).isEmpty());
            return null;
        });
    }

    @Test
    public void embeddedFilesQueriesRejectMalformedStructures()
            throws Exception {
        assertEmbeddedFilesListRejected(embeddedFilesThumbnailFixture());
        assertEmbeddedFilesListRejected(embeddedFilesUnknownKeyFixture());
        assertEmbeddedFilesListRejected(embeddedFilesNonStreamFixture());
        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("input", DocumentSource.bytes(
                                embeddedFilesWrongTypeFixture(),
                                embeddedFilesWrongTypeFixture().length))
                        .primarySource("input")
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    try {
                        session.query(ReadEmbeddedFile.version1(
                                "data.txt",
                                1024L));
                        fail("Expected the wrong stream type to be rejected");
                    } catch (DocumentFailure failure) {
                        assertSafeMetadataFailure(
                                failure,
                                DocumentFailureCode.QUERY_FAILED,
                                "The embedded files could not be inspected safely.");
                    }
                    return null;
                });
    }

    @Test
    public void embedFileRejectsMalformedExistingTrees() throws Exception {
        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("input", DocumentSource.bytes(
                                embeddedFilesThumbnailFixture(),
                                embeddedFilesThumbnailFixture().length))
                        .primarySource("input")
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    try {
                        session.execute(EmbedFile.version1(
                                EmbeddedFile.version1(
                                        "new.bin",
                                        new byte[]{0})));
                        fail("Expected the malformed tree to reject the command");
                    } catch (DocumentFailure failure) {
                        assertSafeMetadataFailure(
                                failure,
                                DocumentFailureCode.COMMAND_REJECTED,
                                "The embedded files could not be updated safely.");
                    }
                    return null;
                });
    }

    @Test
    public void embedFileValidatesNamesAtomically() throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve(
                "embedded-invalid.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "embedded-invalid-out.pdf");
        createBlankDocument(target, 1);

        new DocumentWorkflow().execute(
                rewriteRequest(target, output),
                session -> {
                    try {
                        session.execute(EmbedFile.version1(
                                EmbeddedFile.version1(
                                        "",
                                        new byte[]{0})));
                        fail("Expected the empty name to be rejected");
                    } catch (IllegalArgumentException expected) {
                        assertTrue(expected.getMessage().contains("name"));
                    }
                    assertTrue(session.query(
                            EmbeddedFiles.version1(4)).isEmpty());
                    return null;
                });
    }

    @Test
    public void embeddedFileQueriesHonorDeclaredBounds() throws Exception {
        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("input", DocumentSource.bytes(
                                embeddedFilesFixture(),
                                embeddedFilesFixture().length))
                        .primarySource("input")
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    assertEquals(
                            1,
                            session.query(EmbeddedFiles.version1(1)).size());
                    try {
                        session.query(EmbeddedFiles.version1(0));
                        fail("Expected the entry bound to be enforced");
                    } catch (DocumentFailure failure) {
                        assertSafeMetadataFailure(
                                failure,
                                DocumentFailureCode.METADATA_LIMIT_EXCEEDED,
                                "The metadata access limit was exceeded.");
                    }
                    try {
                        session.query(ReadEmbeddedFile.version1(
                                "data.txt",
                                10L));
                        fail("Expected the byte bound to be enforced");
                    } catch (DocumentFailure failure) {
                        assertSafeMetadataFailure(
                                failure,
                                DocumentFailureCode.METADATA_LIMIT_EXCEEDED,
                                "The metadata access limit was exceeded.");
                    }
                    Optional<EmbeddedFileData> data = session.query(
                            ReadEmbeddedFile.version1("data.txt", 11L));
                    assertTrue(data.isPresent());
                    assertEquals(11L, data.get().getSize());
                    return null;
                });
    }

    private void assertEmbeddedFilesListRejected(byte[] source)
            throws Exception {
        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("input", DocumentSource.bytes(
                                source, source.length))
                        .primarySource("input")
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    try {
                        session.query(EmbeddedFiles.version1(16));
                        fail("Expected the malformed structure to be rejected");
                    } catch (DocumentFailure failure) {
                        assertSafeMetadataFailure(
                                failure,
                                DocumentFailureCode.QUERY_FAILED,
                                "The embedded files could not be inspected safely.");
                    }
                    return null;
                });
    }

    @Test
    public void namedDestinationsFollowPageIdentityThroughCopy()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "dests-copy.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "dests-copied.pdf");
        java.nio.file.Files.write(input, namedDestinationsFixture());

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    session.execute(CopyPages.version1(PageRange.of(1, 1), 1));
                    return null;
                });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            assertEquals(
                    Integer.valueOf(2),
                    session.query(PageCount.INSTANCE));
            Map<String, PageDestination> destinations = session.query(
                    NamedDestinations.version1(8));
            assertEquals(PageDestination.fit(2), destinations.get("alpha"));
            assertEquals(PageDestination.fit(2), destinations.get("middle"));
            assertEquals(
                    PageDestination.xyz(
                            2,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ONE),
                    destinations.get("zebra"));
            return null;
        });
    }

    @Test
    public void pageRemovalConflictsWithManagedDestinations() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "dests-remove.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "dests-remove-unchanged.pdf");
        java.nio.file.Files.write(input, namedDestinationsFixture());

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    try {
                        session.execute(RemovePages.version1(
                                PageRange.of(1, 1)));
                        fail("Expected the destination conflict to be rejected");
                    } catch (DocumentFailure failure) {
                        assertSafeMetadataFailure(
                                failure,
                                DocumentFailureCode.DESTINATION_CONFLICT,
                                "A page removal conflicts with an existing managed destination.");
                    }
                    assertEquals(
                            Integer.valueOf(1),
                            session.query(PageCount.INSTANCE));
                    assertEquals(
                            PageDestination.fit(1),
                            session.query(NamedDestinations.version1(8))
                                    .get("alpha"));
                    return null;
                });
    }

    @Test
    public void outlineRemovalConflictsWithManagedDestinations()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "outline-remove-conflict.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "outline-remove-conflict-unchanged.pdf");
        java.nio.file.Files.write(input, outlineFixture());

        new DocumentWorkflow().execute(
                rewriteRequest(input, output),
                session -> {
                    try {
                        session.execute(RemovePages.version1(
                                PageRange.of(2, 2)));
                        fail("Expected the outline conflict to be rejected");
                    } catch (DocumentFailure failure) {
                        assertSafeMetadataFailure(
                                failure,
                                DocumentFailureCode.DESTINATION_CONFLICT,
                                "A page removal conflicts with an existing managed destination.");
                    }
                    assertEquals(
                            Integer.valueOf(3),
                            session.query(PageCount.INSTANCE));
                    session.execute(RemovePages.version1(
                            PageRange.of(3, 3)));
                    assertEquals(
                            Integer.valueOf(2),
                            session.query(PageCount.INSTANCE));
                    try {
                        session.execute(RemovePages.version1(
                                PageRange.of(1, 1)));
                        fail("Expected the explicit-destination conflict to be rejected");
                    } catch (DocumentFailure failure) {
                        assertSafeMetadataFailure(
                                failure,
                                DocumentFailureCode.DESTINATION_CONFLICT,
                                "A page removal conflicts with an existing managed destination.");
                    }
                    return null;
                });
    }

    @Test
    public void mergeRetargetsManagedStructuresFromAllSources()
            throws Exception {
        Path primary = temporaryFolder.getRoot().toPath().resolve(
                "merge-primary.pdf");
        Path appendix = temporaryFolder.getRoot().toPath().resolve(
                "merge-appendix.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "merge-retargeted.pdf");
        createBlankDocument(primary, 2);
        createBlankDocument(appendix, 2);

        new DocumentWorkflow().execute(
                rewriteRequest(primary, primary),
                session -> {
                    session.execute(SetNamedDestinations.version1()
                            .set("shared", PageDestination.fit(2))
                            .build());
                    session.execute(UpdateDocumentInfo.version1()
                            .set("Author", PdfString.of("Primary".getBytes(StandardCharsets.UTF_8)))
                            .build());
                    session.execute(SetXmpMetadata.version1(
                            xmpPacket("primary-xmp")));
                    session.execute(EmbedFile.version1(EmbeddedFile.version1(
                            "notes.txt",
                            "primary".getBytes(StandardCharsets.UTF_8))));
                    session.execute(ReplaceOutlineTree.version1(Arrays.asList(
                            OutlineItem.toPage(
                                    "Primary One",
                                    PageDestination.fit(1),
                                    Arrays.<OutlineItem>asList()))));
                    return null;
                });
        new DocumentWorkflow().execute(
                rewriteRequest(appendix, appendix),
                session -> {
                    session.execute(SetNamedDestinations.version1()
                            .set("shared", PageDestination.fit(1))
                            .set("extra", PageDestination.fit(2))
                            .build());
                    session.execute(UpdateDocumentInfo.version1()
                            .set("Author", PdfString.of("Source".getBytes(StandardCharsets.UTF_8)))
                            .set("T11Note", PdfString.of("from source".getBytes(StandardCharsets.UTF_8)))
                            .build());
                    session.execute(SetXmpMetadata.version1(
                            xmpPacket("appendix-xmp")));
                    session.execute(EmbedFile.version1(EmbeddedFile.version1(
                            "notes.txt",
                            "appendix".getBytes(StandardCharsets.UTF_8))));
                    session.execute(ReplaceOutlineTree.version1(Arrays.asList(
                            OutlineItem.toPage(
                                    "Appendix One",
                                    PageDestination.fit(1),
                                    Arrays.<OutlineItem>asList()))));
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
            session.execute(MergeDocuments.version1("appendix"));
            return null;
        });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            assertEquals(
                    Integer.valueOf(4),
                    session.query(PageCount.INSTANCE));

            Map<String, PageDestination> destinations = session.query(
                    NamedDestinations.version1(8));
            assertEquals(
                    PageDestination.fit(2),
                    destinations.get("shared"));
            assertEquals(
                    PageDestination.fit(3),
                    destinations.get("shared-1"));
            assertEquals(
                    PageDestination.fit(4),
                    destinations.get("extra"));

            Map<String, PdfValue> info = readInfoEntries(session);
            assertEquals(PdfString.of("Primary".getBytes(StandardCharsets.UTF_8)), info.get("Author"));
            assertEquals(PdfString.of("from source".getBytes(StandardCharsets.UTF_8)), info.get("T11Note"));

            String packet = new String(
                    session.query(XmpMetadata.version1(65536L)),
                    StandardCharsets.UTF_8);
            assertTrue(packet.contains("primary-xmp"));
            assertTrue(!packet.contains("appendix-xmp"));

            List<EmbeddedFileSummary> files = session.query(
                    EmbeddedFiles.version1(8));
            assertEquals(2, files.size());
            assertEquals("notes.txt", files.get(0).getName());
            assertEquals("notes.txt-1", files.get(1).getName());
            assertArrayEquals(
                    "primary".getBytes(StandardCharsets.UTF_8),
                    session.query(ReadEmbeddedFile.version1(
                            "notes.txt",
                            1024L)).get().getContent());
            assertArrayEquals(
                    "appendix".getBytes(StandardCharsets.UTF_8),
                    session.query(ReadEmbeddedFile.version1(
                            "notes.txt-1",
                            1024L)).get().getContent());

            List<OutlineItem> outline = session.query(
                    OutlineTree.version1(16));
            assertEquals(2, outline.size());
            assertEquals("Primary One", outline.get(0).getTitle());
            assertEquals(
                    PageDestination.fit(1),
                    outline.get(0).getDestination().get());
            assertEquals("Appendix One", outline.get(1).getTitle());
            assertEquals(
                    PageDestination.fit(3),
                    outline.get(1).getDestination().get());
            return null;
        });
    }

    @Test
    public void splitCopiesAndRetargetsManagedStructures() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "split-source.pdf");
        Path firstOutput = temporaryFolder.getRoot().toPath().resolve(
                "split-first.pdf");
        Path secondOutput = temporaryFolder.getRoot().toPath().resolve(
                "split-second.pdf");
        createBlankDocument(input, 3);

        new DocumentWorkflow().execute(
                rewriteRequest(input, input),
                session -> {
                    session.execute(SetNamedDestinations.version1()
                            .set("first", PageDestination.fit(1))
                            .set("spanning", PageDestination.fit(2))
                            .set("trailing", PageDestination.fit(3))
                            .build());
                    session.execute(UpdateDocumentInfo.version1()
                            .set("Author", PdfString.of("Split".getBytes(StandardCharsets.UTF_8)))
                            .build());
                    session.execute(SetXmpMetadata.version1(
                            xmpPacket("split-xmp")));
                    session.execute(EmbedFile.version1(EmbeddedFile.version1(
                            "data.txt",
                            "split-content".getBytes(StandardCharsets.UTF_8))));
                    session.execute(ReplaceOutlineTree.version1(Arrays.asList(
                            OutlineItem.toPage(
                                    "First Chapter",
                                    PageDestination.fit(1),
                                    Arrays.<OutlineItem>asList()),
                            OutlineItem.toPage(
                                    "Last Chapter",
                                    PageDestination.fit(3),
                                    Arrays.<OutlineItem>asList()))));
                    return null;
                });

        WorkflowRequest splitRequest = WorkflowRequest.builder()
                .source("input", DocumentSource.path(input))
                .primarySource("input")
                .target("first", PublicationTarget.path(firstOutput))
                .target("second", PublicationTarget.path(secondOutput))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(splitRequest, session -> {
            session.execute(SplitDocument.version1()
                    .target("first", PageRange.of(1, 2))
                    .target("second", PageRange.of(3, 3))
                    .build());
            return null;
        });

        new DocumentWorkflow().execute(
                sourceRequest(firstOutput),
                session -> {
                    Map<String, PageDestination> destinations = session.query(
                            NamedDestinations.version1(8));
                    assertEquals(
                            PageDestination.fit(1),
                            destinations.get("first"));
                    assertEquals(
                            PageDestination.fit(2),
                            destinations.get("spanning"));
                    assertTrue(!destinations.containsKey("trailing"));
                    List<OutlineItem> outline = session.query(
                            OutlineTree.version1(8));
                    assertEquals(1, outline.size());
                    assertEquals("First Chapter", outline.get(0).getTitle());
                    assertEquals(
                            PageDestination.fit(1),
                            outline.get(0).getDestination().get());
                    assertEquals(
                            PdfString.of("Split".getBytes(StandardCharsets.UTF_8)),
                            readInfoEntries(session).get("Author"));
                    String packet = new String(
                            session.query(XmpMetadata.version1(65536L)),
                            StandardCharsets.UTF_8);
                    assertTrue(packet.contains("split-xmp"));
                    assertArrayEquals(
                            "split-content".getBytes(StandardCharsets.UTF_8),
                            session.query(ReadEmbeddedFile.version1(
                                    "data.txt",
                                    1024L)).get().getContent());
                    return null;
                });
        new DocumentWorkflow().execute(
                sourceRequest(secondOutput),
                session -> {
                    Map<String, PageDestination> destinations = session.query(
                            NamedDestinations.version1(8));
                    assertEquals(1, destinations.size());
                    assertEquals(
                            PageDestination.fit(1),
                            destinations.get("trailing"));
                    List<OutlineItem> outline = session.query(
                            OutlineTree.version1(8));
                    assertEquals(1, outline.size());
                    assertEquals("Last Chapter", outline.get(0).getTitle());
                    assertEquals(
                            PageDestination.fit(1),
                            outline.get(0).getDestination().get());
                    assertArrayEquals(
                            "split-content".getBytes(StandardCharsets.UTF_8),
                            session.query(ReadEmbeddedFile.version1(
                                    "data.txt",
                                    1024L)).get().getContent());
                    return null;
                });
    }

    @Test
    public void mergeRewritesCollidingOutlineNamedReferences()
            throws Exception {
        Path primary = temporaryFolder.getRoot().toPath().resolve(
                "merge-named-primary.pdf");
        Path appendix = temporaryFolder.getRoot().toPath().resolve(
                "merge-named-appendix.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "merge-named-output.pdf");
        createBlankDocument(primary, 2);
        createBlankDocument(appendix, 2);

        new DocumentWorkflow().execute(
                rewriteRequest(primary, primary),
                session -> {
                    session.execute(SetNamedDestinations.version1()
                            .set("shared", PageDestination.fit(1))
                            .build());
                    session.execute(ReplaceOutlineTree.version1(Arrays.asList(
                            OutlineItem.toNamedDestination(
                                    "Primary Shared",
                                    "shared",
                                    Arrays.<OutlineItem>asList()))));
                    return null;
                });
        new DocumentWorkflow().execute(
                rewriteRequest(appendix, appendix),
                session -> {
                    session.execute(SetNamedDestinations.version1()
                            .set("shared", PageDestination.fit(1))
                            .build());
                    session.execute(ReplaceOutlineTree.version1(Arrays.asList(
                            OutlineItem.toNamedDestination(
                                    "Appendix Shared",
                                    "shared",
                                    Arrays.<OutlineItem>asList()))));
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
            session.execute(MergeDocuments.version1("appendix"));
            return null;
        });

        new DocumentWorkflow().execute(sourceRequest(output), session -> {
            Map<String, PageDestination> destinations = session.query(
                    NamedDestinations.version1(8));
            assertEquals(
                    PageDestination.fit(1),
                    destinations.get("shared"));
            assertEquals(
                    PageDestination.fit(3),
                    destinations.get("shared-1"));
            List<OutlineItem> outline = session.query(
                    OutlineTree.version1(8));
            assertEquals(2, outline.size());
            assertEquals(
                    "shared",
                    outline.get(0).getNamedDestination().get());
            assertEquals(
                    "shared-1",
                    outline.get(1).getNamedDestination().get());
            return null;
        });
    }

    @Test
    public void splitPreservesDictionaryValuedInfoEntries()
            throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve(
                "split-info-source.pdf");
        Path firstOutput = temporaryFolder.getRoot().toPath().resolve(
                "split-info-first.pdf");
        Path secondOutput = temporaryFolder.getRoot().toPath().resolve(
                "split-info-second.pdf");
        createBlankDocument(input, 3);

        new DocumentWorkflow().execute(
                rewriteRequest(input, input),
                session -> {
                    session.execute(UpdateDocumentInfo.version1()
                            .set("Author", PdfString.of(
                                    "Split".getBytes(StandardCharsets.UTF_8)))
                            .set("T11Custom", PdfDictionary.builder()
                                    .put(PdfName.of("Nested"), PdfNumber.of(7))
                                    .build())
                            .build());
                    return null;
                });

        WorkflowRequest splitRequest = WorkflowRequest.builder()
                .source("input", DocumentSource.path(input))
                .primarySource("input")
                .target("first", PublicationTarget.path(firstOutput))
                .target("second", PublicationTarget.path(secondOutput))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(splitRequest, session -> {
            session.execute(SplitDocument.version1()
                    .target("first", PageRange.of(1, 2))
                    .target("second", PageRange.of(3, 3))
                    .build());
            return null;
        });

        for (Path product : Arrays.asList(firstOutput, secondOutput)) {
            new DocumentWorkflow().execute(sourceRequest(product), session -> {
                Map<String, PdfValue> info = readInfoEntries(session);
                assertEquals(
                        PdfString.of("Split".getBytes(StandardCharsets.UTF_8)),
                        info.get("Author"));
                PdfValue custom = info.get("T11Custom");
                assertEquals(PdfValueKind.DICTIONARY, custom.getKind());
                assertEquals(
                        PdfNumber.of(7),
                        ((PdfDictionary) custom).get(PdfName.of("Nested")));
                return null;
            });
        }
    }

    @Test
    public void mergeRejectsOversizedSourceMetadata() throws Exception {
        Path primary = temporaryFolder.getRoot().toPath().resolve(
                "merge-oversize-primary.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "merge-oversize-output.pdf");
        createBlankDocument(primary);
        byte[] appendix = oversizedMetadataFixture();

        WorkflowRequest mergeRequest = WorkflowRequest.builder()
                .source("primary", DocumentSource.path(primary))
                .source("appendix",
                        DocumentSource.bytes(appendix, appendix.length))
                .primarySource("primary")
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(mergeRequest, session -> {
            try {
                session.execute(MergeDocuments.version1("appendix"));
                fail("Expected the oversized metadata to be rejected");
            } catch (DocumentFailure failure) {
                assertOversizedPageRejection(failure);
            }
            return null;
        });
    }

    @Test
    public void splitRejectsOversizedMetadataWithThePageCapability()
            throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "split-oversize-output.pdf");
        byte[] source = oversizedMetadataFixture();

        WorkflowRequest splitRequest = WorkflowRequest.builder()
                .source("input", DocumentSource.bytes(source, source.length))
                .primarySource("input")
                .target("product", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(splitRequest, session -> {
            try {
                session.execute(SplitDocument.version1()
                        .target("product", PageRange.of(1, 1))
                        .build());
                fail("Expected the oversized metadata to be rejected");
            } catch (DocumentFailure failure) {
                assertOversizedPageRejection(failure);
            }
            return null;
        });
    }

    private static void assertOversizedPageRejection(DocumentFailure failure) {
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

    @Test
    public void pageMutationsRejectUnmanagedMetadataStructures()
            throws Exception {
        assertPageMutationRejects(
                catalogDestsFixture(),
                "catalog-dests.pdf");
        assertPageMutationRejects(
                pageLabelsFixture(),
                "page-labels.pdf");
        assertPageMutationRejects(
                openActionFixture(),
                "open-action.pdf");
    }

    private void assertPageMutationRejects(byte[] source, String outputName)
            throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve(outputName);
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.bytes(source, source.length))
                .primarySource("input")
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(request, session -> {
            try {
                session.execute(MovePages.version1(PageRange.of(1, 1), 1));
                fail("Expected the unmanaged structure to be rejected");
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
            return null;
        });
    }

    private static byte[] outlineFixture() {
        return outlineFixture(
                "/Outlines 5 0 R /Names 10 0 R",
                "<< /Type /Outlines /First 6 0 R /Last 7 0 R /Count 3 >>",
                "<< /Title (Chapter 1) /Parent 5 0 R /Next 7 0 R"
                        + " /Dest (s11) >>",
                "<< /Title (Part II) /Parent 5 0 R /Prev 6 0 R"
                        + " /First 8 0 R /Last 8 0 R /Count 1 >>",
                "<< /Title (Appendix) /Parent 7 0 R /Dest [3 0 R /XYZ 0 700 1]"
                        + " >>",
                "<< /Dests << /Names [(s11) [4 0 R /Fit]] >> >>");
    }

    private static byte[] outlineFixture(
            String catalogOutlineEntry,
            String outlineRoot,
            String firstItem,
            String secondItem,
            String childItem) {
        return outlineFixture(
                catalogOutlineEntry,
                outlineRoot,
                firstItem,
                secondItem,
                childItem,
                null);
    }

    private static byte[] outlineFixture(
            String catalogOutlineEntry,
            String outlineRoot,
            String firstItem,
            String secondItem,
            String childItem,
            String namesDictionary) {
        List<String> objects = new java.util.ArrayList<String>(Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R "
                        + catalogOutlineEntry + " >>",
                "<< /Type /Pages /Kids [3 0 R 4 0 R 9 0 R] /Count 3 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                "<< /Type /Page /Parent 2 0 R >>",
                outlineRoot,
                firstItem,
                secondItem,
                childItem,
                "<< /Type /Page /Parent 2 0 R >>"));
        if (namesDictionary != null) {
            objects.add(namesDictionary);
        }
        return pdfFixture(objects, "");
    }

    private static byte[] cyclicOutlineFixture() {
        return outlineFixture(
                "/Outlines 5 0 R",
                "<< /Type /Outlines /First 6 0 R /Last 7 0 R /Count 3 >>",
                "<< /Title (Chapter 1) /Parent 5 0 R /Next 6 0 R"
                        + " /Dest [3 0 R /Fit] >>",
                "<< /Title (Part II) /Parent 5 0 R /Prev 6 0 R"
                        + " /First 8 0 R /Last 8 0 R /Count 1 >>",
                "<< /Title (Appendix) /Parent 7 0 R /Dest [3 0 R /XYZ 0 700 1]"
                        + " >>");
    }

    private static byte[] outlineActionFixture() {
        return outlineFixture(
                "/Outlines 5 0 R",
                "<< /Type /Outlines /First 6 0 R /Last 7 0 R /Count 3 >>",
                "<< /Title (Chapter 1) /Parent 5 0 R /Next 7 0 R"
                        + " /Dest [3 0 R /Fit]"
                        + " /A << /S /URI /URI (https://example.test) >> >>",
                "<< /Title (Part II) /Parent 5 0 R /Prev 6 0 R"
                        + " /First 8 0 R /Last 8 0 R /Count 1 >>",
                "<< /Title (Appendix) /Parent 7 0 R /Dest [3 0 R /XYZ 0 700 1]"
                        + " >>");
    }

    private static byte[] outlineUnknownKeyFixture() {
        return outlineFixture(
                "/Outlines 5 0 R",
                "<< /Type /Outlines /First 6 0 R /Last 7 0 R /Count 3 >>",
                "<< /Title (Chapter 1) /Parent 5 0 R /Next 7 0 R"
                        + " /Dest [3 0 R /Fit] /C [1 0 0] >>",
                "<< /Title (Part II) /Parent 5 0 R /Prev 6 0 R"
                        + " /First 8 0 R /Last 8 0 R /Count 1 >>",
                "<< /Title (Appendix) /Parent 7 0 R /Dest [3 0 R /XYZ 0 700 1]"
                        + " >>");
    }

    private static byte[] outlineBadLinkFixture() {
        return outlineFixture(
                "/Outlines 5 0 R",
                "<< /Type /Outlines /First 6 0 R /Last 7 0 R /Count 3 >>",
                "<< /Title (Chapter 1) /Parent 5 0 R /Next 7 0 R"
                        + " /Dest [3 0 R /Fit] >>",
                "<< /Title (Part II) /Parent 5 0 R /Prev 5 0 R"
                        + " /First 8 0 R /Last 8 0 R /Count 1 >>",
                "<< /Title (Appendix) /Parent 7 0 R /Dest [3 0 R /XYZ 0 700 1]"
                        + " >>");
    }

    private static byte[] outlineBadCountFixture() {
        return outlineFixture(
                "/Outlines 5 0 R",
                "<< /Type /Outlines /First 6 0 R /Last 7 0 R /Count 5 >>",
                "<< /Title (Chapter 1) /Parent 5 0 R /Next 7 0 R"
                        + " /Dest [3 0 R /Fit] >>",
                "<< /Title (Part II) /Parent 5 0 R /Prev 6 0 R"
                        + " /First 8 0 R /Last 8 0 R /Count 1 >>",
                "<< /Title (Appendix) /Parent 7 0 R /Dest [3 0 R /XYZ 0 700 1]"
                        + " >>");
    }

    private static byte[] outlineDanglingDestinationFixture() {
        return outlineFixture(
                "/Outlines 5 0 R",
                "<< /Type /Outlines /First 6 0 R /Last 7 0 R /Count 3 >>",
                "<< /Title (Chapter 1) /Parent 5 0 R /Next 7 0 R"
                        + " /Dest [3 0 R /Fit] >>",
                "<< /Title (Part II) /Parent 5 0 R /Prev 6 0 R"
                        + " /First 8 0 R /Last 8 0 R /Count 1 >>",
                "<< /Title (Appendix) /Parent 7 0 R /Dest [5 0 R /XYZ 0 700 1]"
                        + " >>");
    }

    private static byte[] outlineUnknownNamedReferenceFixture() {
        return outlineFixture(
                "/Outlines 5 0 R",
                "<< /Type /Outlines /First 6 0 R /Last 7 0 R /Count 3 >>",
                "<< /Title (Chapter 1) /Parent 5 0 R /Next 7 0 R"
                        + " /Dest [3 0 R /Fit] >>",
                "<< /Title (Part II) /Parent 5 0 R /Prev 6 0 R"
                        + " /First 8 0 R /Last 8 0 R /Count 1 >>",
                "<< /Title (Appendix) /Parent 7 0 R /Dest (missing) >>");
    }

    private static byte[] outlineNonDictionaryRootFixture() {
        return outlineFixture(
                "/Outlines (not a dictionary)",
                "<< /Type /Outlines /First 6 0 R /Last 7 0 R /Count 3 >>",
                "<< /Title (Chapter 1) /Parent 5 0 R /Next 7 0 R"
                        + " /Dest [3 0 R /Fit] >>",
                "<< /Title (Part II) /Parent 5 0 R /Prev 6 0 R"
                        + " /First 8 0 R /Last 8 0 R /Count 1 >>",
                "<< /Title (Appendix) /Parent 7 0 R /Dest [3 0 R /XYZ 0 700 1]"
                        + " >>");
    }

    private static byte[] catalogDestsFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /Dests 4 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                "<< /old [3 0 R /Fit] >>");
        return pdfFixture(objects, "");
    }

    private static byte[] pageLabelsFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /PageLabels 4 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                "<< /Nums [0 << /S /r >>] >>");
        return pdfFixture(objects, "");
    }

    private static byte[] openActionFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /OpenAction [3 0 R /Fit] >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>");
        return pdfFixture(objects, "");
    }

    private static byte[] embeddedFilesFixture() {
        return embeddedFilesFixture(
                "<< /Type /Filespec /F (data.txt) /EF << /F 6 0 R >> >>",
                "<< /Type /EmbeddedFile /Length 11 /Params"
                        + " << /Size 11 >> >>");
    }

    private static byte[] embeddedFilesThumbnailFixture() {
        return embeddedFilesFixture(
                "<< /Type /Filespec /F (data.txt) /Thumb [0 0 1 1]"
                        + " /EF << /F 6 0 R >> >>",
                "<< /Type /EmbeddedFile /Length 11 /Params"
                        + " << /Size 11 >> >>");
    }

    private static byte[] embeddedFilesUnknownKeyFixture() {
        return embeddedFilesFixture(
                "<< /Type /Filespec /F (data.txt) /Custom true"
                        + " /EF << /F 6 0 R >> >>",
                "<< /Type /EmbeddedFile /Length 11 /Params"
                        + " << /Size 11 >> >>");
    }

    private static byte[] embeddedFilesNonStreamFixture() {
        return embeddedFilesFixture(
                "<< /Type /Filespec /F (data.txt)"
                        + " /EF << /F (not a stream) >> >>",
                "<< /Type /EmbeddedFile /Length 11 /Params"
                        + " << /Size 11 >> >>");
    }

    private static byte[] embeddedFilesWrongTypeFixture() {
        return embeddedFilesFixture(
                "<< /Type /Filespec /F (data.txt) /EF << /F 6 0 R >> >>",
                "<< /Type /Image /Length 11 >>");
    }

    private static byte[] embeddedFilesFixture(
            String fileSpecification,
            String embeddedStreamDictionary) {
        byte[] content = "hello world".getBytes(StandardCharsets.ISO_8859_1);
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /Names 4 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                "<< /EmbeddedFiles << /Names [(data.txt) 5 0 R] >> >>",
                fileSpecification,
                streamObject(embeddedStreamDictionary, content));
        return pdfFixture(objects, "");
    }

    private static byte[] namedDestinationsFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /Names 4 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                "<< /Dests << /Names [ (alpha) [3 0 R /Fit] "
                        + "(middle) [3 0 R /Fit] (zebra) [3 0 R /XYZ 0 0 1] ]"
                        + " >> >>");
        return pdfFixture(objects, "");
    }

    private static byte[] namedDestinationsWithUnknownSubtreeFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /Names 4 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                "<< /Dests << /Names [ (alpha) [3 0 R /Fit] ] >>"
                        + " /JavaScript << /Names [(doc) 5 0 R] >> >>",
                "<< /S /JavaScript /JS (app.alert) >>");
        return pdfFixture(objects, "");
    }

    private static byte[] unsortedNameTreeFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /Names 4 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                "<< /Dests << /Names [ (zebra) [3 0 R /Fit] "
                        + "(alpha) [3 0 R /Fit] ] >> >>");
        return pdfFixture(objects, "");
    }

    private static byte[] badLimitsNameTreeFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /Names 4 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                "<< /Dests << /Kids [5 0 R] /Limits [(a) (c)] >> >>",
                "<< /Names [ (zebra) [3 0 R /Fit] ] /Limits [(z) (z)] >>");
        return pdfFixture(objects, "");
    }

    private static byte[] cyclicNameTreeFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /Names 4 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                "<< /Dests << /Kids [4 0 R] /Limits [(a) (z)] >> >>");
        return pdfFixture(objects, "");
    }

    private static byte[] nonArrayDestinationFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /Names 4 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                "<< /Dests << /Names [ (alpha) (not an array) ] >> >>");
        return pdfFixture(objects, "");
    }

    private static byte[] danglingDestinationFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /Names 4 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                "<< /Dests << /Names [ (alpha) [4 0 R /Fit] ] >> >>",
                "<< /NotAPage true >>");
        return pdfFixture(objects, "");
    }

    private static byte[] nonStringKeyFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /Names 4 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                "<< /Dests << /Names [ /NameKey [3 0 R /Fit] ] >> >>");
        return pdfFixture(objects, "");
    }

    private static byte[] duplicateKeyAcrossKidsFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /Names 4 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                "<< /Dests << /Kids [5 0 R 6 0 R] /Limits [(alpha) (zebra)]"
                        + " >> >>",
                "<< /Names [(alpha) [3 0 R /Fit] (middle) [3 0 R /Fit]]"
                        + " /Limits [(alpha) (middle)] >>",
                "<< /Names [(alpha) [3 0 R /Fit] (zebra) [3 0 R /Fit]]"
                        + " /Limits [(alpha) (zebra)] >>");
        return pdfFixture(objects, "");
    }

    private static byte[] outOfOrderKidsFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /Names 4 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                "<< /Dests << /Kids [5 0 R 6 0 R] /Limits [(alpha) (zebra)]"
                        + " >> >>",
                "<< /Names [(zebra) [3 0 R /Fit]] /Limits [(zebra) (zebra)] >>",
                "<< /Names [(alpha) [3 0 R /Fit]] /Limits [(alpha) (alpha)]"
                        + " >>");
        return pdfFixture(objects, "");
    }

    private static byte[] deepNameTreeFixture(int depth) {
        List<String> objects = new java.util.ArrayList<String>(Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /Names 4 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                "<< /Dests << /Kids [5 0 R] /Limits [(a) (z)] >> >>"));
        for (int level = 1; level < depth; level++) {
            objects.add("<< /Kids [" + (level + 5) + " 0 R]"
                    + " /Limits [(a) (z)] >>");
        }
        objects.add("<< /Names [(alpha) [3 0 R /Fit]] /Limits [(alpha) (alpha)] >>");
        return pdfFixture(objects, "");
    }

    private static byte[] deepInfoFixture(int depth) {
        StringBuilder information = new StringBuilder(
                "<< /Title (deep) ");
        for (int level = 0; level < depth; level++) {
            information.append("/Next << ");
        }
        for (int level = 0; level < depth; level++) {
            information.append(">> ");
        }
        information.append(">>");
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                information.toString());
        return pdfFixture(objects, " /Info 4 0 R");
    }

    private static byte[] oversizedMetadataFixture() {
        byte[] deflated = deflate(new byte[64 * 1024 * 1024 + 1]);
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /Metadata 4 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                streamObject(
                        "<< /Type /Metadata /Subtype /XML"
                                + " /Filter /FlateDecode /Length "
                                + deflated.length + " >>",
                        deflated));
        return pdfFixture(objects, "");
    }

    private static byte[] xmpPacket(String marker) {
        return ("<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n"
                + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n"
                + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n"
                + "<!-- " + marker + " -->\n"
                + "</rdf:RDF>\n"
                + "</x:xmpmeta>\n"
                + "<?xpacket end=\"w\"?>").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] xmpFixture(byte[] packet) {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /Metadata 4 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                streamObject(
                        "<< /Type /Metadata /Subtype /XML /Length "
                                + packet.length + " >>",
                        packet));
        return pdfFixture(objects, "");
    }

    private static byte[] xmpExtendedFixture(byte[] packet) {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /Metadata 4 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                streamObject(
                        "<< /Type /Metadata /Subtype /XML /T11Extra /Preserved"
                                + " /Length " + packet.length + " >>",
                        packet));
        return pdfFixture(objects, "");
    }

    private static byte[] xmpFilteredFixture(byte[] packet) {
        byte[] deflated = deflate(packet);
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /Metadata 4 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                streamObject(
                        "<< /Type /Metadata /Subtype /XML /Filter /FlateDecode"
                                + " /Length " + deflated.length + " >>",
                        deflated));
        return pdfFixture(objects, "");
    }

    private static byte[] malformedXmpNotAStreamFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /Metadata (not a stream) >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>");
        return pdfFixture(objects, "");
    }

    private static byte[] malformedXmpWrongSubtypeFixture() {
        byte[] packet = xmpPacket("wrong subtype");
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /Metadata 4 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                streamObject(
                        "<< /Type /Metadata /Subtype /PlainText /Length "
                                + packet.length + " >>",
                        packet));
        return pdfFixture(objects, "");
    }

    private static byte[] malformedXmpMissingTypeFixture() {
        byte[] packet = xmpPacket("missing type");
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /Metadata 4 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                streamObject(
                        "<< /Subtype /XML /Length " + packet.length + " >>",
                        packet));
        return pdfFixture(objects, "");
    }

    private static String streamObject(String dictionary, byte[] content) {
        ByteArrayOutputStream object = new ByteArrayOutputStream();
        writeAscii(object, dictionary);
        writeAscii(object, "\nstream\n");
        object.write(content, 0, content.length);
        return new String(object.toByteArray(), StandardCharsets.ISO_8859_1)
                + "\nendstream";
    }

    private static byte[] deflate(byte[] content) {
        ByteArrayOutputStream deflated = new ByteArrayOutputStream();
        try (java.util.zip.DeflaterOutputStream deflater =
                new java.util.zip.DeflaterOutputStream(deflated)) {
            deflater.write(content);
        } catch (java.io.IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        return deflated.toByteArray();
    }

    private static Map<String, PdfValue> readInfoEntries(DocumentSession session)
            throws DocumentFailure {
        PdfDictionary info = session.query(DocumentInfo.INSTANCE);
        Map<String, PdfValue> entries = new LinkedHashMap<String, PdfValue>();
        for (int index = 0; index < info.size(); index++) {
            PdfDictionaryEntry entry = info.getEntry(index);
            entries.put(entry.getName().getValue(), entry.getValue());
        }
        return entries;
    }

    private static PdfDictionary dereferenceDictionary(
            DocumentSession session,
            PdfValue value) throws DocumentFailure {
        if (value instanceof PdfIndirectReference) {
            return (PdfDictionary) session.query(InspectObject.version1(
                    ((PdfIndirectReference) value).getReference(),
                    PdfInspectionLimits.of(16, 0L)));
        }
        return (PdfDictionary) value;
    }

    private static void assertSafeMetadataFailure(
            DocumentFailure failure,
            DocumentFailureCode expectedCode,
            String expectedDiagnostic) {
        assertEquals(expectedCode, failure.getCode());
        assertEquals(CAPABILITY, failure.getCapabilityId());
        assertEquals(expectedDiagnostic, failure.getDiagnostic());
        assertFalse(failure.getDiagnostic().contains("org.apache.pdfbox"));
        assertNull(failure.getCause());
    }

    private static byte[] documentInformationFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                "<< /Author (Original) /T11Unknown (keep me) /T11Number 42 >>");
        return pdfFixture(objects, " /Info 4 0 R");
    }

    private static byte[] documentInformationPageReferenceFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                "<< /Title (linked) /SeePage 3 0 R >>");
        return pdfFixture(objects, " /Info 4 0 R");
    }

    private static byte[] documentInformationStreamFixture() {
        List<String> objects = Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 "
                        + "/MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R >>",
                "<< /Title (streamed) /Attachment 5 0 R >>",
                "<< /Length 4 >>\nstream\nabcd\nendstream");
        return pdfFixture(objects, " /Info 4 0 R");
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

    private static WorkflowRequest rewriteRequest(Path input, Path output) {
        return WorkflowRequest.builder()
                .source("input", DocumentSource.path(input))
                .primarySource("input")
                .target("output", PublicationTarget.path(output))
                .saveMode(SaveMode.REWRITE)
                .build();
    }

    private static WorkflowRequest sourceRequest(Path source) {
        return WorkflowRequest.builder()
                .source("input", DocumentSource.path(source))
                .primarySource("input")
                .saveMode(SaveMode.REWRITE)
                .build();
    }

    private static void createBlankDocument(Path target) throws Exception {
        createBlankDocument(target, 1);
    }

    private static void createBlankDocument(Path target, int pageCount)
            throws Exception {
        WorkflowRequest request = WorkflowRequest.builder()
                .target("output", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .build();

        new DocumentWorkflow().execute(request, session -> {
            for (int page = 0; page < pageCount; page++) {
                session.execute(AddBlankPage.INSTANCE);
            }
            return null;
        });
    }
}
