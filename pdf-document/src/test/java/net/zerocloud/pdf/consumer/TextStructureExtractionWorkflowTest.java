package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import net.zerocloud.pdf.CharacterMapping;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.ExtractionDiagnostic;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ExtractionLimits;
import net.zerocloud.pdf.LogicalStructureElement;
import net.zerocloud.pdf.LogicalStructureItem;
import net.zerocloud.pdf.MarkedContentReference;
import net.zerocloud.pdf.MarkedContentSequence;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PageText;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.TextItem;
import net.zerocloud.pdf.TextStructureExtraction;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.ExtractTextAndStructure;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageObjectReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class TextStructureExtractionWorkflowTest {

    private static final String CAPABILITY = "document.text-structure.extract";
    private static final String BOUNDED_CONTENT =
            "/Span <</MCID 0>> BDC BT /F1 12 Tf (A) Tj ET EMC\n";
    private static final String PAGE_FORM_CONTENT =
            "BT /F1 12 Tf (A) Tj ET /Middle Do "
                    + "BT /F1 12 Tf (C) Tj ET\n";
    private static final String MIDDLE_FORM_CONTENT = "/Leaf Do\n";
    private static final String LEAF_FORM_CONTENT =
            "BT /F1 12 Tf (B) Tj ET\n";
    private static final String NESTED_MARKED_CONTENT =
            "/Outer <</MCID 0 /ActualText (Outer)>> BDC "
                    + "/Inner <</MCID 1 /ActualText (Inner)>> BDC "
                    + "BT /F1 12 Tf (A) Tj ET EMC EMC\n";
    private static final byte[] EMBEDDED_FONT_PROGRAM =
            "Folio T13 bounded font program".getBytes(
                    StandardCharsets.US_ASCII);

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void untaggedPageTextObservesEarlierPatchAndReturnsDetachedEvidence()
            throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("untagged.pdf");

        WorkflowOutcome<TextStructureExtraction> outcome =
                new DocumentWorkflow().execute(
                        WorkflowRequest.builder()
                                .target("output", PublicationTarget.path(output))
                                .saveMode(SaveMode.REWRITE)
                                .build(),
                        session -> {
                            session.execute(AddBlankPage.INSTANCE);
                            ObjectReference page = session.query(
                                    PageObjectReference.version1(1));
                            session.execute(DocumentPatch.builder()
                                    .setDictionaryEntry(
                                            page,
                                            PdfName.of("Resources"),
                                            resourcesWithWinAnsiHelvetica())
                                    .setDictionaryEntry(
                                            page,
                                            PdfName.of("Contents"),
                                            PdfStream.of(
                                                    PdfDictionary.builder().build(),
                                                    "BT /F1 12 Tf 72 720 Td (Hi) Tj ET\n"
                                                            .getBytes(
                                                                    StandardCharsets.US_ASCII)))
                                    .build());
                            return session.query(
                                    ExtractTextAndStructure.version1(limits()));
                        });

        assertEquals(CAPABILITY, outcome.getCapabilityId());
        TextStructureExtraction extraction = outcome.getResult();
        assertEquals(1, extraction.getPages().size());
        assertTrue(extraction.getStructureRoots().isEmpty());
        assertTrue(extraction.getDiagnostics().isEmpty());

        PageText page = extraction.getPages().get(0);
        assertEquals(1, page.getPageNumber());
        assertEquals("Hi", page.getText());
        assertEquals(2, page.getTextItems().size());
        assertEquals(0, page.getRotation());
        assertEquals(BigDecimal.ZERO, page.getCropBoxLeft());
        assertEquals(new BigDecimal("612"), page.getCropBoxRight());

        TextItem first = page.getTextItems().get(0);
        assertEquals(1, first.getIndex());
        assertEquals("H", first.getUnicode().get());
        assertEquals("H", first.getTextContribution());
        assertTrue(first.getMarkedContentSequenceIds().isEmpty());
        assertEquals(new BigDecimal("72"), first.getGeometry().getE());
        assertEquals(new BigDecimal("720"), first.getGeometry().getF());

        CharacterMapping mapping = first.getCharacterMapping();
        assertEquals(
                CharacterMapping.Confidence.INFERRED,
                mapping.getConfidence());
        assertArrayEquals(new byte[] {0x48}, mapping.getSourceCode());
        assertFalse(mapping.getExplicitUnicode().isPresent());
        assertEquals("H", mapping.getInferredUnicode().get());
        assertEquals("H", mapping.getUnicode().get());
    }

    @Test
    public void pageAndStreamOrderGeometryAndExplicitMappingsAreDeterministic()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "deterministic.pdf");
        createDeterministicFixture(source);
        byte[] beforeQueries = Files.readAllBytes(source);

        WorkflowOutcome<String> firstOutcome = new DocumentWorkflow().execute(
                sourceRequest(source),
                session -> {
                    TextStructureExtraction first = session.query(
                            ExtractTextAndStructure.version1(limits()));
                    TextStructureExtraction repeated = session.query(
                            ExtractTextAndStructure.version1(limits()));
                    assertDeterministicGeometry(first);
                    assertEquals(fingerprint(first), fingerprint(repeated));
                    return fingerprint(first);
                });
        String firstFingerprint = firstOutcome.getResult();
        assertTrue(firstOutcome.getPublicationReceipts().isEmpty());

        WorkflowOutcome<String> reopenedOutcome = new DocumentWorkflow().execute(
                sourceRequest(source),
                session -> fingerprint(session.query(
                        ExtractTextAndStructure.version1(limits()))));
        String reopenedFingerprint = reopenedOutcome.getResult();
        assertTrue(reopenedOutcome.getPublicationReceipts().isEmpty());

        assertEquals(firstFingerprint, reopenedFingerprint);
        assertArrayEquals(beforeQueries, Files.readAllBytes(source));
    }

    @Test
    public void geometrySeparatesGlyphAdvanceFromSpacingAndTjAdjustments()
            throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "spaced-text.pdf");

        TextStructureExtraction extraction = new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .target("output", PublicationTarget.path(output))
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    ObjectReference page = session.query(
                            PageObjectReference.version1(1));
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(
                                    page,
                                    PdfName.of("Resources"),
                                    resourcesWithToUnicodeHelvetica())
                            .setDictionaryEntry(
                                    page,
                                    PdfName.of("Contents"),
                                    content("BT /F1 1000 Tf 20 Tc 30 Tw "
                                            + "10 20 Td "
                                            + "[(A) -100 ( ) 50 (B)] "
                                            + "TJ ET\n"))
                            .build());
                    return session.query(
                            ExtractTextAndStructure.version1(limits()));
                }).getResult();

        List<TextItem> items = extraction.getPages().get(0).getTextItems();
        assertEquals("A B", extraction.getPages().get(0).getText());
        assertEquals(3, items.size());
        assertEquals(new BigDecimal("10"), items.get(0).getGeometry().getE());
        assertEquals(new BigDecimal("667"),
                items.get(0).getGeometry().getAdvanceX());
        assertEquals(new BigDecimal("797"),
                items.get(1).getGeometry().getE());
        assertEquals(new BigDecimal("278"),
                items.get(1).getGeometry().getAdvanceX());
        assertEquals(new BigDecimal("1075"),
                items.get(2).getGeometry().getE());
    }

    @Test
    public void taggedHierarchyKeepsContentLanguageRolesAndAlternatesDistinct()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("tagged.pdf");
        writeTaggedHierarchyFixture(source);

        TextStructureExtraction extraction = query(source, limits());

        PageText page = extraction.getPages().get(0);
        assertEquals("Actual", page.getText());
        assertEquals(7, page.getTextItems().size());
        assertEquals(1, page.getMarkedContentSequences().size());
        MarkedContentSequence sequence = page.getMarkedContentSequences().get(0);
        assertEquals(1, sequence.getId());
        assertEquals("Span", sequence.getTag());
        assertEquals(Integer.valueOf(0), sequence.getMarkedContentId().get());
        assertEquals("de-DE", sequence.getLanguage().get());
        assertEquals("content alternate", sequence.getAlternateText().get());
        assertEquals("Actual", sequence.getActualText().get());
        assertEquals(7, sequence.getTextItemIndices().size());
        assertEquals(Integer.valueOf(1),
                page.getTextItems().get(0).getMarkedContentSequenceIds().get(0));
        assertEquals("", page.getTextItems().get(0).getTextContribution());

        assertEquals(1, extraction.getStructureRoots().size());
        LogicalStructureElement document = extraction.getStructureRoots().get(0);
        assertEquals("Document", document.getRole());
        assertEquals("Document", document.getResolvedRole().get());
        assertEquals(LogicalStructureElement.RoleResolution.STANDARD,
                document.getRoleResolution());
        assertEquals("en-US", document.getEffectiveLanguage().get());
        assertEquals(LogicalStructureElement.LanguageSource.DOCUMENT,
                document.getLanguageSource());

        LogicalStructureElement story = childElement(document, 0);
        assertEquals("Story", story.getRole());
        assertEquals("Sect", story.getResolvedRole().get());
        assertEquals(LogicalStructureElement.RoleResolution.ROLE_MAP,
                story.getRoleResolution());
        assertEquals("fr-CA", story.getDeclaredLanguage().get());
        assertEquals(LogicalStructureElement.LanguageSource.SELF,
                story.getLanguageSource());
        assertEquals("Story alternative", story.getAlternateText().get());
        assertEquals("Structure replacement", story.getActualText().get());

        LogicalStructureElement span = childElement(story, 0);
        assertEquals("Span", span.getResolvedRole().get());
        assertFalse(span.getDeclaredLanguage().isPresent());
        assertEquals("fr-CA", span.getEffectiveLanguage().get());
        assertEquals(LogicalStructureElement.LanguageSource.ANCESTOR,
                span.getLanguageSource());
        LogicalStructureItem contentItem = span.getChildren().get(0);
        assertEquals(LogicalStructureItem.Kind.MARKED_CONTENT,
                contentItem.getKind());
        MarkedContentReference reference = contentItem.getMarkedContent().get();
        assertEquals(1, reference.getPageNumber());
        assertEquals(0, reference.getMarkedContentId());
        assertEquals(Integer.valueOf(1),
                reference.getMarkedContentSequenceId().get());
    }

    @Test
    public void pdf2OnlyUnqualifiedRoleRemainsUnresolvedInVersion1()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "pdf2-only-role.pdf");
        writeUnqualifiedRoleFixture(source, "Title");
        byte[] before = Files.readAllBytes(source);

        LogicalStructureElement first = query(source, limits())
                .getStructureRoots().get(0);
        LogicalStructureElement repeated = query(source, limits())
                .getStructureRoots().get(0);
        assertEquals("Title", first.getRole());
        assertEquals(LogicalStructureElement.RoleResolution.UNRESOLVED,
                first.getRoleResolution());
        assertFalse(first.getResolvedRole().isPresent());
        assertEquals(first.getRoleResolution(), repeated.getRoleResolution());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test
    public void nestedMarkedContentPreservesOrderParentsAndActualTextPrecedence()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "nested-marked-content.pdf");
        writeNestedMarkedContentFixture(source);

        PageText page = query(source, nestedMarkedLimits(2))
                .getPages().get(0);
        assertEquals("Outer", page.getText());
        assertEquals(2, page.getMarkedContentSequences().size());
        MarkedContentSequence outer = page.getMarkedContentSequences().get(0);
        MarkedContentSequence inner = page.getMarkedContentSequences().get(1);
        assertEquals(1, outer.getId());
        assertFalse(outer.getParentId().isPresent());
        assertEquals(2, inner.getId());
        assertEquals(Integer.valueOf(1), inner.getParentId().get());
        assertEquals(1, page.getTextItems().size());
        assertEquals("", page.getTextItems().get(0).getTextContribution());
        assertEquals(2,
                page.getTextItems().get(0).getMarkedContentSequenceIds().size());
        assertEquals(Integer.valueOf(1), page.getTextItems().get(0)
                .getMarkedContentSequenceIds().get(0));
        assertEquals(Integer.valueOf(2), page.getTextItems().get(0)
                .getMarkedContentSequenceIds().get(1));
        assertLimitFailure(source, nestedMarkedLimits(1));
    }

    @Test
    public void missingAndContradictoryMappingsRemainExplicitUncertainty()
            throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve(
                "uncertain-mappings.pdf");

        TextStructureExtraction extraction = new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .target("output", PublicationTarget.path(output))
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    ObjectReference page = session.query(
                            PageObjectReference.version1(1));
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(
                                    page,
                                    PdfName.of("Resources"),
                                    uncertainMappingResources())
                            .setDictionaryEntry(
                                    page,
                                    PdfName.of("Contents"),
                                    content("BT /F1 12 Tf (A) Tj "
                                            + "/F2 12 Tf (B) Tj "
                                            + "/F3 12 Tf (C) Tj "
                                            + "/F4 12 Tf (D) Tj "
                                            + "/F5 12 Tf (E) Tj ET\n"))
                            .build());
                    return session.query(
                            ExtractTextAndStructure.version1(limits()));
                }).getResult();

        PageText page = extraction.getPages().get(0);
        assertEquals("DE", page.getText());
        assertEquals(5, page.getTextItems().size());
        CharacterMapping contradictory = page.getTextItems().get(0)
                .getCharacterMapping();
        assertEquals(CharacterMapping.Confidence.CONTRADICTORY,
                contradictory.getConfidence());
        assertFalse(contradictory.getUnicode().isPresent());
        assertEquals("Z", contradictory.getExplicitUnicode().get());
        assertEquals("A", contradictory.getInferredUnicode().get());
        byte[] sourceCode = contradictory.getSourceCode();
        sourceCode[0] = 0;
        assertArrayEquals(new byte[] {0x41}, contradictory.getSourceCode());

        CharacterMapping missing = page.getTextItems().get(1)
                .getCharacterMapping();
        assertEquals(CharacterMapping.Confidence.MISSING,
                missing.getConfidence());
        assertFalse(missing.getUnicode().isPresent());
        assertFalse(missing.getExplicitUnicode().isPresent());
        assertFalse(missing.getInferredUnicode().isPresent());
        assertArrayEquals(new byte[] {0x42}, missing.getSourceCode());

        CharacterMapping backendFallback = page.getTextItems().get(2)
                .getCharacterMapping();
        assertEquals(CharacterMapping.Confidence.MISSING,
                backendFallback.getConfidence());
        assertFalse(backendFallback.getUnicode().isPresent());
        assertFalse(backendFallback.getExplicitUnicode().isPresent());
        assertFalse(backendFallback.getInferredUnicode().isPresent());
        assertArrayEquals(new byte[] {0x43}, backendFallback.getSourceCode());

        for (int index = 3; index < 5; index++) {
            CharacterMapping explicitDifference = page.getTextItems().get(index)
                    .getCharacterMapping();
            String expected = index == 3 ? "D" : "E";
            assertEquals(CharacterMapping.Confidence.INFERRED,
                    explicitDifference.getConfidence());
            assertEquals(expected, explicitDifference.getUnicode().get());
            assertEquals(expected,
                    explicitDifference.getInferredUnicode().get());
        }

        assertEquals(3, extraction.getDiagnostics().size());
        assertEquals(ExtractionDiagnostic.Code.CONTRADICTORY_UNICODE_MAPPING,
                extraction.getDiagnostics().get(0).getCode());
        assertEquals(ExtractionDiagnostic.Code.MISSING_UNICODE_MAPPING,
                extraction.getDiagnostics().get(1).getCode());
        assertEquals(1, extraction.getDiagnostics().get(0).getPageNumber());
        assertEquals(1,
                extraction.getDiagnostics().get(0).getTextItemIndex());
    }

    @Test(timeout = 10000L)
    public void toUnicodeRangeExpansionIsCallerBoundedBeforeParsing()
            throws Exception {
        Path exact = temporaryFolder.getRoot().toPath().resolve(
                "bounded-cmap.pdf");
        Path hostile = temporaryFolder.getRoot().toPath().resolve(
                "hostile-cmap.pdf");
        Path decimal = temporaryFolder.getRoot().toPath().resolve(
                "decimal-cmap.pdf");
        Path embeddedCarry = temporaryFolder.getRoot().toPath().resolve(
                "embedded-cmap-carry.pdf");
        createToUnicodeRangeFixture(
                exact,
                "1",
                "<41> <42> <0041>",
                "AB");
        createToUnicodeRangeFixture(
                hostile,
                "1",
                "<00000000> <7FFFFFFF> <0041>",
                "A");
        createToUnicodeRangeFixture(
                decimal,
                "1.0",
                "<41> <43> <0041>",
                "A");
        createToUnicodeRangeFixture(
                embeddedCarry,
                "1",
                "<0000> <00FF> <00FF>",
                "A");

        assertEquals("AB", query(exact, mappingLimits(2))
                .getPages().get(0).getText());
        assertLimitFailure(exact, mappingLimits(1));
        assertLimitFailure(hostile, mappingLimits(2));
        assertLimitFailure(decimal, mappingLimits(2));
        assertLimitFailure(embeddedCarry, mappingLimits(1));
        assertEquals(1, query(embeddedCarry, mappingLimits(256))
                .getPages().size());
    }

    @Test(timeout = 10000L)
    public void invalidToUnicodeNamesFailBeforeBackendCoercion()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "named-cmap-destination.pdf");
        createToUnicodeRangeFixture(
                source,
                "1",
                "<41> <41> /NotUnicode",
                "A");
        byte[] before = Files.readAllBytes(source);

        assertQueryFailure(source, mappingLimits(1));
        assertQueryFailure(source, mappingLimits(1));
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void isolatedToUnicodeSurrogatesFailThroughThePublicQuery()
            throws Exception {
        String[] destinations = {"<D800>", "<DC00>"};
        for (int index = 0; index < destinations.length; index++) {
            Path source = temporaryFolder.getRoot().toPath().resolve(
                    "isolated-surrogate-" + index + ".pdf");
            createToUnicodeBodyFixture(
                    source,
                    "1 beginbfchar\n<41> " + destinations[index]
                            + "\nendbfchar",
                    "A");
            byte[] before = Files.readAllBytes(source);

            assertQueryFailure(source, mappingLimits(1));
            assertQueryFailure(source, mappingLimits(1));
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void pairedToUnicodeSurrogatesReachPublicMappingEvidence()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "paired-surrogate.pdf");
        createToUnicodeBodyFixture(
                source,
                "1 beginbfchar\n<41> <D83DDE00>\nendbfchar",
                "A");

        TextStructureExtraction extraction = query(
                source, mappingLimits(1));

        CharacterMapping mapping = extraction.getPages().get(0)
                .getTextItems().get(0).getCharacterMapping();
        assertEquals("\uD83D\uDE00", mapping.getExplicitUnicode().get());
        assertEquals(CharacterMapping.Confidence.CONTRADICTORY,
                mapping.getConfidence());
        assertFalse(mapping.getUnicode().isPresent());
        assertEquals("", extraction.getPages().get(0).getText());
    }

    @Test(timeout = 10000L)
    public void toUnicodePreflightStopsAtEndCMapThroughThePublicQuery()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "endcmap-cutoff.pdf");
        createToUnicodeBodyFixture(
                source,
                "1 beginbfchar\n<41> <0041>\nendbfchar\n"
                        + "endcmap\n"
                        + "1 beginbfrange\n"
                        + "<00000000> <7FFFFFFF> <0041>\n"
                        + "endbfrange",
                "A");
        byte[] before = Files.readAllBytes(source);

        assertEquals("A", query(source, mappingLimits(1))
                .getPages().get(0).getText());
        assertEquals("A", query(source, mappingLimits(1))
                .getPages().get(0).getText());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void malformedToUnicodeCountsAndRangesFailBeforeBackendCoercion()
            throws Exception {
        Path earlyCharacter = temporaryFolder.getRoot().toPath().resolve(
                "early-bfchar-terminator.pdf");
        Path earlyRange = temporaryFolder.getRoot().toPath().resolve(
                "early-bfrange-terminator.pdf");
        Path reversedRange = temporaryFolder.getRoot().toPath().resolve(
                "reversed-bfrange.pdf");
        createToUnicodeBodyFixture(
                earlyCharacter,
                "2 beginbfchar\n<41> <0041>\nendbfchar",
                "A");
        createToUnicodeBodyFixture(
                earlyRange,
                "2 beginbfrange\n<41> <41> <0041>\nendbfrange",
                "A");
        createToUnicodeBodyFixture(
                reversedRange,
                "1 beginbfrange\n<42> <41> <0041>\nendbfrange",
                "A");
        Path[] malformed = {earlyCharacter, earlyRange, reversedRange};
        for (Path source : malformed) {
            byte[] before = Files.readAllBytes(source);
            assertQueryFailure(source, limits());
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test
    public void fontProgramsAndEncodingEntriesShareCallerBoundsAndCaches()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "bounded-font-inputs.pdf");
        createBoundedFontInputFixture(source);
        long exactBytes = "BT /F1 12 Tf (AAAA) Tj ET\n".length()
                + EMBEDDED_FONT_PROGRAM.length;

        TextStructureExtraction exact = query(
                source, fontInputLimits(exactBytes, 2));
        assertEquals("AAAA", exact.getPages().get(0).getText());
        assertLimitFailure(source, fontInputLimits(exactBytes - 1L, 2));
        assertLimitFailure(source, fontInputLimits(exactBytes, 1));
    }

    @Test(timeout = 10000L)
    public void malformedFontKindsAndMetricsFailBeforeBackendCoercion()
            throws Exception {
        String cidPrefix = "<< /Type /Font /Subtype /Type0 /BaseFont /FolioT13 "
                + "/Encoding /Identity-H /DescendantFonts [<< /Type /Font "
                + "/Subtype /CIDFontType2 /BaseFont /FolioT13 "
                + "/CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) "
                + "/Supplement 0 >> ";
        String[] fonts = {
            "<< /Subtype /Type1 /BaseFont /Helvetica "
                    + "/Encoding /WinAnsiEncoding >>",
            "<< /Type /NotFont /Subtype /Type1 /BaseFont /Helvetica "
                    + "/Encoding /WinAnsiEncoding >>",
            "<< /Type /Font /BaseFont /Helvetica "
                    + "/Encoding /WinAnsiEncoding >>",
            "<< /Type /Font /Subtype /Unknown /BaseFont /Helvetica "
                    + "/Encoding /WinAnsiEncoding >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                    + "/Encoding /WinAnsiEncoding /FirstChar /Bad "
                    + "/LastChar 65 /Widths [500] >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                    + "/Encoding /WinAnsiEncoding /FontDescriptor "
                    + "<< /Type /FontDescriptor /FontName /Helvetica "
                    + "/FontFile /NotAStream >> >>",
            cidPrefix + "/DW /Bad >>] >>",
            cidPrefix + "/DW 1000 /W [65.5 [500]] >>] >>",
            cidPrefix + "/DW2 [880] >>] >>",
            cidPrefix + "/DW2 [880 -1000] /W2 [65 66] >>] >>",
            cidPrefix + "/CIDToGIDMap /NotIdentity >>] >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                    + "/Encoding << /BaseEncoding /WinAnsiEncoding "
                    + "/Differences [4294967361 /A] >> >>"
        };
        for (int index = 0; index < fonts.length; index++) {
            Path source = temporaryFolder.getRoot().toPath().resolve(
                    "malformed-font-" + index + ".pdf");
            writeFontDictionaryFixture(source, fonts[index]);
            byte[] before = Files.readAllBytes(source);

            assertQueryFailure(source, limits());
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void irrelevantExtendedGraphicsStateArraysAreNotTraversed()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "ignored-large-graphics-state.pdf");
        String operators = "BT /GS gs (A) Tj ET\n";
        writeLargeGraphicsStateFixture(source, operators, 4096);
        byte[] before = Files.readAllBytes(source);

        assertEquals("A", query(
                source, fontInputLimits(operators.length(), 0))
                .getPages().get(0).getText());
        assertEquals("A", query(
                source, fontInputLimits(operators.length(), 0))
                .getPages().get(0).getText());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test
    public void sharedDifferencesArrayIsInspectedAndChargedOnce()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "shared-differences.pdf");
        String operators = "BT /F1 12 Tf (A) Tj /F2 12 Tf (A) Tj ET\n";
        writePdf(source,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> "
                        + "/Contents 4 0 R >>",
                streamObject(operators, ""),
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                        + "/Encoding << /BaseEncoding /WinAnsiEncoding "
                        + "/Differences 7 0 R >> >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                        + "/Encoding << /BaseEncoding /WinAnsiEncoding "
                        + "/Differences 7 0 R >> >>",
                "[65 /A]");

        assertEquals("AA", query(
                source, fontInputLimits(operators.length(), 2))
                .getPages().get(0).getText());
        assertLimitFailure(
                source, fontInputLimits(operators.length(), 1));
    }

    @Test
    public void cidFontMetricsAcceptTheirExactBudgetAndFailOnExhaustion()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "bounded-cid-metrics.pdf");
        writeCidMetricFixture(source, "[65 66 500]");

        assertEquals("AB", query(source, cidFontLimits(5))
                .getPages().get(0).getText());
        assertLimitFailure(source, cidFontLimits(4));
    }

    @Test
    public void verticalCidMetricsUseThePublicFontDataEntryBudget()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "bounded-vertical-cid-metrics.pdf");
        writeCidMetricFixture(
                source,
                "[65 66 500]",
                "/DW2 [880 -1000] "
                        + "/W2 [65 66 -1000 250 880]");

        assertEquals("AB", query(source, cidFontLimits(12))
                .getPages().get(0).getText());
        assertLimitFailure(source, cidFontLimits(11));
    }

    @Test(timeout = 10000L)
    public void hostileCidWidthRangeIsRejectedBeforeBackendMaterialization()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "hostile-cid-metrics.pdf");
        writeCidMetricFixture(source, "[0 2147483647 500]");
        byte[] before = Files.readAllBytes(source);

        assertLimitFailure(source, cidFontLimits(8));
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void nestedType0DescendantsFailSafelyWithoutRecursiveAcceptance()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "nested-type0-descendants.pdf");
        writeNestedType0DescendantFixture(source, 128);
        byte[] before = Files.readAllBytes(source);

        assertQueryFailure(source, limits());
        assertQueryFailure(source, limits());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void type0EncodingsAreRestrictedBeforeBackendCMapLoading()
            throws Exception {
        Path missing = temporaryFolder.getRoot().toPath().resolve(
                "missing-type0-encoding.pdf");
        Path predefined = temporaryFolder.getRoot().toPath().resolve(
                "predefined-type0-encoding.pdf");
        writeType0EncodingFixture(missing, "");
        writeType0EncodingFixture(predefined, "/Encoding /83pv-RKSJ-H ");
        byte[] missingBefore = Files.readAllBytes(missing);
        byte[] predefinedBefore = Files.readAllBytes(predefined);

        assertQueryFailure(missing, limits());
        assertQueryFailure(predefined, limits());
        assertQueryFailure(predefined, limits());
        assertArrayEquals(missingBefore, Files.readAllBytes(missing));
        assertArrayEquals(predefinedBefore, Files.readAllBytes(predefined));
    }

    @Test(timeout = 10000L)
    public void mismatchedEmbeddedType0FontCannotRepairTheLiveCosGraph()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "mismatched-embedded-type0.pdf");
        Path target = temporaryFolder.getRoot().toPath().resolve(
                "mismatched-embedded-type0-rewritten.pdf");
        writeMismatchedEmbeddedType0Fixture(source);
        byte[] before = Files.readAllBytes(source);

        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.path(source))
                .primarySource("input")
                .target("output", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(request, session -> {
            ObjectReference descendant = type0Descendant(session);
            PdfName subtypeBefore = fontSubtype(session, descendant);
            assertEquals(PdfName.of("CIDFontType0"), subtypeBefore);
            try {
                session.query(ExtractTextAndStructure.version1(limits()));
                fail("Expected mismatched embedded Type0 rejection");
            } catch (DocumentFailure expected) {
                assertEquals(DocumentFailureCode.QUERY_FAILED,
                        expected.getCode());
            }
            assertEquals(subtypeBefore, fontSubtype(session, descendant));
            session.execute(AddBlankPage.INSTANCE);
            return null;
        });

        assertArrayEquals(before, Files.readAllBytes(source));
        assertTrue(Files.exists(target));
        new DocumentWorkflow().execute(sourceRequest(target), session -> {
            assertEquals(
                    PdfName.of("CIDFontType0"),
                    fontSubtype(session, type0Descendant(session)));
            return null;
        });
    }

    @Test(timeout = 10000L)
    public void indirectFormNamesRemainReferencesInTheLiveAndRewrittenGraph()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "indirect-form-names.pdf");
        Path target = temporaryFolder.getRoot().toPath().resolve(
                "indirect-form-names-rewritten.pdf");
        writeIndirectFormNameFixture(source);

        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.path(source))
                .primarySource("input")
                .target("output", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(request, session -> {
            PdfValue typeBefore = formEntry(session, PdfName.of("Type"));
            PdfValue subtypeBefore = formEntry(
                    session, PdfName.of("Subtype"));
            assertIndirectName(session, typeBefore, PdfName.of("XObject"));
            assertIndirectName(session, subtypeBefore, PdfName.of("Form"));

            assertEquals("", session.query(
                    ExtractTextAndStructure.version1(limits()))
                    .getPages().get(0).getText());

            assertEquals(typeBefore, formEntry(session, PdfName.of("Type")));
            assertEquals(subtypeBefore,
                    formEntry(session, PdfName.of("Subtype")));
            session.execute(AddBlankPage.INSTANCE);
            return null;
        });

        new DocumentWorkflow().execute(sourceRequest(target), session -> {
            assertIndirectName(
                    session,
                    formEntry(session, PdfName.of("Type")),
                    PdfName.of("XObject"));
            assertIndirectName(
                    session,
                    formEntry(session, PdfName.of("Subtype")),
                    PdfName.of("Form"));
            return null;
        });
    }

    @Test(timeout = 10000L)
    public void type3GlyphProgramsAreRejectedWithoutDecoding()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "type3-glyph-program.pdf");
        writeType3FontFixture(source, 256 * 1024);
        byte[] before = Files.readAllBytes(source);

        assertQueryFailure(source, limits());
        assertQueryFailure(source, limits());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void missingResourcesFailWithStablePublicDiagnostics()
            throws Exception {
        Path text = temporaryFolder.getRoot().toPath().resolve(
                "missing-font-resources.pdf");
        Path form = temporaryFolder.getRoot().toPath().resolve(
                "missing-xobject-resources.pdf");
        Path graphicsCategory = temporaryFolder.getRoot().toPath().resolve(
                "missing-graphics-state-category.pdf");
        Path graphicsName = temporaryFolder.getRoot().toPath().resolve(
                "missing-graphics-state-name.pdf");
        writeMissingResourcesFixture(text, "BT /F1 12 Tf (A) Tj ET\n");
        writeMissingResourcesFixture(form, "/Fm Do\n");
        writeMissingGraphicsStateFixture(graphicsCategory, "<< >>");
        writeMissingGraphicsStateFixture(
                graphicsName, "<< /ExtGState << >> >>");
        byte[] textBefore = Files.readAllBytes(text);
        byte[] formBefore = Files.readAllBytes(form);
        byte[] graphicsCategoryBefore = Files.readAllBytes(graphicsCategory);
        byte[] graphicsNameBefore = Files.readAllBytes(graphicsName);

        assertQueryFailure(text, limits());
        assertQueryFailure(text, limits());
        assertQueryFailure(form, limits());
        assertQueryFailure(form, limits());
        assertQueryFailure(graphicsCategory, limits());
        assertQueryFailure(graphicsName, limits());
        assertArrayEquals(textBefore, Files.readAllBytes(text));
        assertArrayEquals(formBefore, Files.readAllBytes(form));
        assertArrayEquals(
                graphicsCategoryBefore,
                Files.readAllBytes(graphicsCategory));
        assertArrayEquals(
                graphicsNameBefore,
                Files.readAllBytes(graphicsName));
    }

    @Test(timeout = 10000L)
    public void resourceCategoryStreamsFailBeforeBackendOperators()
            throws Exception {
        Path font = temporaryFolder.getRoot().toPath().resolve(
                "font-resource-stream.pdf");
        Path graphics = temporaryFolder.getRoot().toPath().resolve(
                "graphics-state-resource-stream.pdf");
        Path property = temporaryFolder.getRoot().toPath().resolve(
                "property-resource-stream.pdf");
        Path xobject = temporaryFolder.getRoot().toPath().resolve(
                "xobject-resource-stream.pdf");
        writeFontResourceStreamFixture(font);
        writeGraphicsStateResourceStreamFixture(graphics);
        writePropertyResourceStreamFixture(property);
        writeXObjectResourceStreamFixture(xobject);
        byte[] fontBefore = Files.readAllBytes(font);
        byte[] graphicsBefore = Files.readAllBytes(graphics);
        byte[] propertyBefore = Files.readAllBytes(property);
        byte[] xobjectBefore = Files.readAllBytes(xobject);

        assertQueryFailure(font, limits());
        assertQueryFailure(graphics, limits());
        assertQueryFailure(property, limits());
        assertQueryFailure(xobject, limits());
        assertArrayEquals(fontBefore, Files.readAllBytes(font));
        assertArrayEquals(graphicsBefore, Files.readAllBytes(graphics));
        assertArrayEquals(propertyBefore, Files.readAllBytes(property));
        assertArrayEquals(xobjectBefore, Files.readAllBytes(xobject));
    }

    @Test(timeout = 10000L)
    public void contentArraysAndNamedPropertiesFailBeforeBackendRuntime()
            throws Exception {
        Path contents = temporaryFolder.getRoot().toPath().resolve(
                "malformed-content-array.pdf");
        Path property = temporaryFolder.getRoot().toPath().resolve(
                "missing-marked-content-property.pdf");
        writeMalformedContentsFixture(contents);
        writeMissingResourcesFixture(
                property, "/Span /Missing BDC EMC\n");
        byte[] contentsBefore = Files.readAllBytes(contents);
        byte[] propertyBefore = Files.readAllBytes(property);

        assertQueryFailure(contents, limits());
        assertQueryFailure(contents, limits());
        assertQueryFailure(property, limits());
        assertQueryFailure(property, limits());
        assertArrayEquals(contentsBefore, Files.readAllBytes(contents));
        assertArrayEquals(propertyBefore, Files.readAllBytes(property));
    }

    @Test(timeout = 10000L)
    public void malformedContentSyntaxCannotPublishAValidLookingPrefix()
            throws Exception {
        String[] malformedSuffixes = {"[", "1 0"};
        for (int index = 0; index < malformedSuffixes.length; index++) {
            Path source = temporaryFolder.getRoot().toPath().resolve(
                    "unterminated-content-" + index + ".pdf");
            writeSimpleTextFixture(
                    source,
                    "BT /F1 12 Tf (A) Tj ET\n"
                            + malformedSuffixes[index]);
            byte[] before = Files.readAllBytes(source);

            assertQueryFailure(source, limits());
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void malformedResourceOperatorOperandsCannotPublishPrefix()
            throws Exception {
        String[] malformedOperators = {
            "1 Do",
            "1 12 Tf",
            "1 gs",
            "1 <<>> BDC EMC",
            "1 Tj",
            "1.5 Tr",
            "1 q"
        };
        for (int index = 0; index < malformedOperators.length; index++) {
            Path source = temporaryFolder.getRoot().toPath().resolve(
                    "malformed-resource-operator-" + index + ".pdf");
            writeSimpleTextFixture(
                    source,
                    "BT /F1 12 Tf (A) Tj ET\n"
                            + malformedOperators[index] + "\n");
            byte[] before = Files.readAllBytes(source);

            assertQueryFailure(source, limits());
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void contentSyntaxCanSpanContentsMembersAsOneCombinedStream()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "split-content-token.pdf");
        writeSplitContentTokenFixture(source);
        byte[] before = Files.readAllBytes(source);

        assertEquals("A", query(source, limits()).getPages().get(0).getText());
        assertEquals("A", query(source, limits()).getPages().get(0).getText());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void unbalancedSupportedOperatorStateCannotPublishPrefix()
            throws Exception {
        String[] malformedOperators = {
            "BT /F1 12 Tf (A) Tj",
            "BT /F1 12 Tf (A) Tj ET ET",
            "BT BT /F1 12 Tf (A) Tj ET ET",
            "BT /F1 12 Tf (A) Tj ET q",
            "BT /F1 12 Tf (A) Tj ET Q",
            "1 0 0 1 0 0 Tm",
            "BT /F1 12 Tf (A) Tj ET 1 0 Td"
        };
        for (int index = 0; index < malformedOperators.length; index++) {
            Path source = temporaryFolder.getRoot().toPath().resolve(
                    "unbalanced-page-operator-" + index + ".pdf");
            writeSimpleTextFixture(
                    source, malformedOperators[index] + "\n");
            byte[] before = Files.readAllBytes(source);

            assertQueryFailure(source, limits());
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }

        Path form = temporaryFolder.getRoot().toPath().resolve(
                "unbalanced-form-operator.pdf");
        writeFormFixture(
                form,
                "/Fm Do\n",
                "q\n",
                "/BBox [0 0 100 100] /Resources << >>");
        byte[] formBefore = Files.readAllBytes(form);
        assertQueryFailure(form, limits());
        assertQueryFailure(form, limits());
        assertArrayEquals(formBefore, Files.readAllBytes(form));
    }

    @Test(timeout = 10000L)
    public void inconsistentAndCyclicPageTreesFailBeforeBackendTraversal()
            throws Exception {
        Path count = temporaryFolder.getRoot().toPath().resolve(
                "false-page-count.pdf");
        Path negative = temporaryFolder.getRoot().toPath().resolve(
                "negative-page-count.pdf");
        Path wrapped = temporaryFolder.getRoot().toPath().resolve(
                "wrapped-page-count.pdf");
        Path cycle = temporaryFolder.getRoot().toPath().resolve(
                "cyclic-page-tree.pdf");
        writeFalsePageCountFixture(count);
        writeNegativePageCountFixture(negative);
        writeWrappedPageCountFixture(wrapped);
        writeCyclicPageTreeFixture(cycle);

        assertQueryFailure(count, limits());
        assertQueryFailure(negative, limits());
        assertQueryFailure(wrapped, limits());
        assertQueryFailure(cycle, limits());
    }

    @Test(timeout = 10000L)
    public void malformedPageGeometryAttributesFailBeforeBackendCoercion()
            throws Exception {
        String[] attributes = {
            "/Resources [] /MediaBox [0 0 612 792]",
            "/Resources << >>",
            "/Resources << >> /MediaBox []",
            "/Resources << >> /MediaBox [0 0 /Bad 792]",
            "/Resources << >> /MediaBox [0 0 612 792] /CropBox []",
            "/Resources << >> /MediaBox [0 0 612 792] /Rotate /Ninety",
            "/Resources << >> /MediaBox [0 0 612 792] /Rotate 45"
        };
        for (int index = 0; index < attributes.length; index++) {
            Path source = temporaryFolder.getRoot().toPath().resolve(
                    "malformed-inherited-page-attribute-" + index + ".pdf");
            writeInheritedPageAttributesFixture(source, attributes[index]);
            byte[] before = Files.readAllBytes(source);

            assertQueryFailure(source, pageTreeLimits(2));
            assertQueryFailure(source, pageTreeLimits(2));
            assertArrayEquals(before, Files.readAllBytes(source));
        }
        String[] userUnits = {
            "/UserUnit /Bad", "/UserUnit 0", "/UserUnit -1", "/UserUnit 75001"
        };
        for (int index = 0; index < userUnits.length; index++) {
            Path source = temporaryFolder.getRoot().toPath().resolve(
                    "malformed-page-user-unit-" + index + ".pdf");
            writeDirectPageAttributesFixture(source, userUnits[index]);
            byte[] before = Files.readAllBytes(source);

            assertQueryFailure(source, pageTreeLimits(2));
            assertQueryFailure(source, pageTreeLimits(2));
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void malformedFormGeometryAndResourcesFailBeforeBackendCoercion()
            throws Exception {
        String[] entries = {
            "/Resources << >>",
            "/BBox [] /Resources << >>",
            "/BBox [0 0 /Bad 100] /Resources << >>",
            "/BBox [0 0 100 100] /Matrix [] /Resources << >>",
            "/BBox [0 0 100 100] /Matrix [1 0 0 /Bad 0 0] "
                    + "/Resources << >>",
            "/BBox [0 0 100 100] /Resources []",
            "/BBox [0 0 100 100] /FormType /One /Resources << >>",
            "/BBox [0 0 100 100] /FormType 2 /Resources << >>",
            "/BBox [0 0 100 100] /FormType 4294967297 "
                    + "/Resources << >>"
        };
        for (int index = 0; index < entries.length; index++) {
            Path source = temporaryFolder.getRoot().toPath().resolve(
                    "malformed-form-attribute-" + index + ".pdf");
            writeFormFixture(source, "/Fm Do\n", "", entries[index]);
            byte[] before = Files.readAllBytes(source);

            assertQueryFailure(source, limits());
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
        String[] types = {
            "/Subtype /Form /BBox [0 0 100 100] /Resources << >>",
            "/Type /NotXObject /Subtype /Form /BBox [0 0 100 100] "
                    + "/Resources << >>"
        };
        for (int index = 0; index < types.length; index++) {
            Path source = temporaryFolder.getRoot().toPath().resolve(
                    "malformed-form-type-" + index + ".pdf");
            writeRawFormFixture(source, "/Fm Do\n", "", types[index]);
            byte[] before = Files.readAllBytes(source);

            assertQueryFailure(source, limits());
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void markedContentEndInsideFormFailsWithoutClosingPageSequence()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "form-closes-page-marked-content.pdf");
        writeFormFixture(
                source,
                "/Span BMC /Fm Do\n",
                "EMC\n",
                "/BBox [0 0 100 100] /Resources << >>");
        byte[] before = Files.readAllBytes(source);

        assertQueryFailure(source, limits());
        assertQueryFailure(source, limits());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void repeatedAndInconsistentStructureLinksFailSafely()
            throws Exception {
        Path repeated = temporaryFolder.getRoot().toPath().resolve(
                "repeated-structure-element.pdf");
        Path parent = temporaryFolder.getRoot().toPath().resolve(
                "inconsistent-structure-parent.pdf");
        Path objectReference = temporaryFolder.getRoot().toPath().resolve(
                "object-reference-structure-child.pdf");
        Path namespace = temporaryFolder.getRoot().toPath().resolve(
                "namespaced-structure-element.pdf");
        Path streamOwner = temporaryFolder.getRoot().toPath().resolve(
                "stream-owner-mcr.pdf");
        Path oversizedMcid = temporaryFolder.getRoot().toPath().resolve(
                "oversized-mcid.pdf");
        writeStructureLinkFixture(repeated, "[6 0 R 6 0 R]", "5 0 R");
        writeStructureLinkFixture(parent, "6 0 R", "2 0 R");
        writeObjectReferenceStructureFixture(objectReference);
        writeStructureLinkFixture(
                namespace, "6 0 R", "5 0 R", "/NS << >>");
        writeStreamOwnerMcrFixture(streamOwner);
        writeStructureLinkFixture(
                oversizedMcid,
                "6 0 R",
                "5 0 R",
                "/Pg 3 0 R /K 4294967297");
        byte[] repeatedBefore = Files.readAllBytes(repeated);
        byte[] parentBefore = Files.readAllBytes(parent);
        byte[] objectReferenceBefore = Files.readAllBytes(objectReference);
        byte[] namespaceBefore = Files.readAllBytes(namespace);
        byte[] streamOwnerBefore = Files.readAllBytes(streamOwner);
        byte[] oversizedMcidBefore = Files.readAllBytes(oversizedMcid);

        assertQueryFailure(repeated, limits());
        assertQueryFailure(repeated, limits());
        assertQueryFailure(parent, limits());
        assertQueryFailure(parent, limits());
        assertQueryFailure(objectReference, limits());
        assertQueryFailure(objectReference, limits());
        assertQueryFailure(namespace, limits());
        assertQueryFailure(namespace, limits());
        assertQueryFailure(streamOwner, limits());
        assertQueryFailure(streamOwner, limits());
        assertQueryFailure(oversizedMcid, limits());
        assertQueryFailure(oversizedMcid, limits());
        assertArrayEquals(repeatedBefore, Files.readAllBytes(repeated));
        assertArrayEquals(parentBefore, Files.readAllBytes(parent));
        assertArrayEquals(
                objectReferenceBefore, Files.readAllBytes(objectReference));
        assertArrayEquals(namespaceBefore, Files.readAllBytes(namespace));
        assertArrayEquals(streamOwnerBefore, Files.readAllBytes(streamOwner));
        assertArrayEquals(
                oversizedMcidBefore, Files.readAllBytes(oversizedMcid));
    }

    @Test(timeout = 10000L)
    public void pageTreeAndContentsArraysAreBoundedBeforeMaterialization()
            throws Exception {
        Path tree = temporaryFolder.getRoot().toPath().resolve(
                "deep-page-tree.pdf");
        Path contents = temporaryFolder.getRoot().toPath().resolve(
                "large-contents-array.pdf");
        writeDeepPageTreeFixture(tree, 4096);
        writeRepeatedContentsFixture(contents, 4096);
        byte[] contentsBefore = Files.readAllBytes(contents);

        PageText inherited = query(tree, pageTreeLimits(4097))
                .getPages().get(0);
        assertEquals(90, inherited.getRotation());
        assertEquals(new BigDecimal("2"), inherited.getUserUnit());
        assertEquals(new BigDecimal("10"), inherited.getCropBoxLeft());
        assertEquals(new BigDecimal("600"), inherited.getCropBoxRight());
        assertLimitFailure(tree, pageTreeLimits(4096));
        assertLimitFailure(
                contents, new BoundaryLimits().streams(0).build());
        assertArrayEquals(contentsBefore, Files.readAllBytes(contents));
    }

    @Test
    public void everyLimitAcceptsItsExactBoundaryAndFailsOnExhaustion()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "bounded-extraction.pdf");
        createBoundaryFixture(source);

        TextStructureExtraction exact = query(source,
                new BoundaryLimits().build());
        assertEquals("A", exact.getPages().get(0).getText());
        assertEquals(1, exact.getPages().get(0)
                .getMarkedContentSequences().size());
        assertEquals(1, exact.getStructureRoots().size());

        assertLimitFailure(source, new BoundaryLimits().pages(0).build());
        assertLimitFailure(
                source, new BoundaryLimits().pageTreeNodes(1).build());
        assertLimitFailure(source, new BoundaryLimits().streams(0).build());
        assertLimitFailure(source, new BoundaryLimits().streamDepth(0).build());
        assertLimitFailure(source, new BoundaryLimits()
                .decodedBytes(BOUNDED_CONTENT.length() - 1L).build());
        assertLimitFailure(source, new BoundaryLimits().textItems(0).build());
        assertLimitFailure(source, new BoundaryLimits().unicode(24).build());
        assertLimitFailure(source, new BoundaryLimits()
                .markedSequences(0).build());
        assertLimitFailure(source, new BoundaryLimits()
                .markedDepth(0).build());
        assertLimitFailure(source, new BoundaryLimits()
                .structureElements(0).build());
        assertLimitFailure(source, new BoundaryLimits()
                .structureItems(1).build());
        assertLimitFailure(source, new BoundaryLimits()
                .structureDepth(0).build());
        assertLimitFailure(source, new BoundaryLimits()
                .roleMappings(0).build());
    }

    @Test(timeout = 10000L)
    public void textItemLimitPromptlyStopsLargeStringsBeforeResultPublication()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "large-text-string.pdf");
        StringBuilder text = new StringBuilder(1_000_000);
        for (int index = 0; index < 1_000_000; index++) {
            text.append('A');
        }
        String operators = "BT /F1 12 Tf (" + text + ") Tj ET\n";
        writeSimpleTextFixture(source, operators);
        byte[] before = Files.readAllBytes(source);

        assertLimitFailure(source, textItemLimits(operators.length(), 0));
        assertLimitFailure(source, textItemLimits(operators.length(), 0));
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test
    public void nestedFormsAreOrderedAndBoundedByTraversalDepthAndBytes()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "nested-forms.pdf");
        createNestedFormFixture(source);
        int decodedBytes = PAGE_FORM_CONTENT.length()
                + MIDDLE_FORM_CONTENT.length()
                + LEAF_FORM_CONTENT.length();

        ExtractionLimits exact = formLimits(3, 3, decodedBytes);
        PageText page = query(source, exact).getPages().get(0);
        assertEquals("ABC", page.getText());
        assertEquals(new BigDecimal("35"),
                page.getTextItems().get(1).getGeometry().getE());
        assertEquals(new BigDecimal("55"),
                page.getTextItems().get(1).getGeometry().getF());
        assertLimitFailure(source, formLimits(2, 3, decodedBytes));
        assertLimitFailure(source, formLimits(3, 2, decodedBytes));
        assertLimitFailure(source, formLimits(3, 3, decodedBytes - 1));
    }

    @Test(timeout = 10000L)
    public void formDepthHasStackSafeVersion1CeilingAndExactBoundary()
            throws Exception {
        String operator = "/Fm Do\n";
        int exactForms = ExtractionLimits
                .MAXIMUM_CONTENT_STREAM_DEPTH_VERSION_1 - 1;
        Path exact = temporaryFolder.getRoot().toPath().resolve(
                "maximum-form-depth.pdf");
        Path excess = temporaryFolder.getRoot().toPath().resolve(
                "excess-form-depth.pdf");
        writeDeepFormFixture(exact, exactForms);
        writeDeepFormFixture(excess, exactForms + 1);
        byte[] exactBefore = Files.readAllBytes(exact);
        byte[] excessBefore = Files.readAllBytes(excess);

        assertEquals(1, query(
                exact,
                deepFormLimits(
                        exactForms + 1,
                        (long) exactForms * operator.length()))
                .getPages().size());
        assertLimitFailure(
                excess,
                deepFormLimits(
                        exactForms + 2,
                        (long) (exactForms + 1) * operator.length()));
        try {
            ExtractionLimits.builder().maximumContentStreamDepth(
                    ExtractionLimits
                            .MAXIMUM_CONTENT_STREAM_DEPTH_VERSION_1 + 1);
            fail("Expected the version-1 Form-depth ceiling");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("must not exceed"));
        }
        assertArrayEquals(exactBefore, Files.readAllBytes(exact));
        assertArrayEquals(excessBefore, Files.readAllBytes(excess));
    }

    @Test
    public void cyclicContentAndStructureGraphsFailSafelyAndDeterministically()
            throws Exception {
        Path contentCycle = temporaryFolder.getRoot().toPath().resolve(
                "content-cycle.pdf");
        Path structureCycle = temporaryFolder.getRoot().toPath().resolve(
                "structure-cycle.pdf");
        Path roleMapCycle = temporaryFolder.getRoot().toPath().resolve(
                "role-map-cycle.pdf");
        writeContentCycleFixture(contentCycle);
        writeStructureCycleFixture(structureCycle);
        writeRoleMapCycleFixture(roleMapCycle);
        byte[] contentBefore = Files.readAllBytes(contentCycle);
        byte[] structureBefore = Files.readAllBytes(structureCycle);
        byte[] roleMapBefore = Files.readAllBytes(roleMapCycle);

        assertQueryFailure(contentCycle, limits());
        assertQueryFailure(contentCycle, limits());
        assertQueryFailure(structureCycle, limits());
        assertQueryFailure(structureCycle, limits());
        assertQueryFailure(roleMapCycle, limits());
        assertQueryFailure(roleMapCycle, limits());
        assertArrayEquals(contentBefore, Files.readAllBytes(contentCycle));
        assertArrayEquals(structureBefore, Files.readAllBytes(structureCycle));
        assertArrayEquals(roleMapBefore, Files.readAllBytes(roleMapCycle));
    }

    @Test(timeout = 10000L)
    public void deepLogicalStructureUsesDeclaredDepthWithoutJvmRecursion()
            throws Exception {
        int depth = 4096;
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "deep-logical-structure.pdf");
        writeDeepStructureFixture(source, depth);
        byte[] before = Files.readAllBytes(source);

        assertEquals(1, query(source, deepStructureLimits(depth))
                .getStructureRoots().size());
        assertLimitFailure(source, deepStructureLimits(depth - 1));
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    private static TextStructureExtraction query(
            Path source,
            ExtractionLimits extractionLimits) throws Exception {
        return new DocumentWorkflow().execute(
                sourceRequest(source),
                session -> session.query(
                        ExtractTextAndStructure.version1(extractionLimits)))
                .getResult();
    }

    private static void assertQueryFailure(
            Path source,
            ExtractionLimits extractionLimits) throws Exception {
        try {
            query(source, extractionLimits);
            fail("Expected safe query failure");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.QUERY_FAILED, failure.getCode());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(
                    "The document text and logical structure could not be extracted safely.",
                    failure.getDiagnostic());
            assertEquals(null, failure.getCause());
        }
    }

    private static void assertLimitFailure(
            Path source,
            ExtractionLimits extractionLimits) throws Exception {
        try {
            query(source, extractionLimits);
            fail("Expected the extraction limit to be exhausted");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED,
                    failure.getCode());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(
                    "The text and logical-structure extraction limit was exceeded.",
                    failure.getDiagnostic());
            assertEquals(null, failure.getCause());
        }
    }

    private static void createBoundaryFixture(Path target) throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 6 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 5 0 R >> >> "
                        + "/Contents 4 0 R >>",
                streamObject(BOUNDED_CONTENT, ""),
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                        + "/Encoding /WinAnsiEncoding >>",
                "<< /Type /StructTreeRoot /RoleMap << /Custom /Span >> "
                        + "/K 7 0 R >>",
                "<< /Type /StructElem /S /Custom /P 6 0 R /Pg 3 0 R "
                        + "/K 0 >>");
    }

    private static void createNestedFormFixture(Path target) throws Exception {
        PdfDictionary baseResources = resourcesWithWinAnsiHelvetica();
        PdfStream leaf = form(
                LEAF_FORM_CONTENT, baseResources, translation(25L, 35L));
        PdfDictionary middleResources = resourcesWithFontAndXObject(
                "Leaf", leaf);
        PdfStream middle = form(
                MIDDLE_FORM_CONTENT,
                middleResources,
                translation(10L, 20L));
        PdfDictionary pageResources = resourcesWithFontAndXObject(
                "Middle", middle);

        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .target("output", PublicationTarget.path(target))
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    ObjectReference page = session.query(
                            PageObjectReference.version1(1));
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(page, PdfName.of("Resources"),
                                    pageResources)
                            .setDictionaryEntry(page, PdfName.of("Contents"),
                                    content(PAGE_FORM_CONTENT))
                            .build());
                    return null;
                });
    }

    private static void createToUnicodeRangeFixture(
            Path target,
            String rangeCount,
            String range,
            String text) throws Exception {
        createToUnicodeBodyFixture(
                target,
                rangeCount + " beginbfrange\n"
                        + range + "\nendbfrange",
                text);
    }

    private static void createToUnicodeBodyFixture(
            Path target,
            String mappings,
            String text) throws Exception {
        String cmap = "/CIDInit /ProcSet findresource begin\n"
                + "12 dict begin\nbegincmap\n"
                + "/CMapName /FolioT13BoundedRange def\n/CMapType 2 def\n"
                + "1 begincodespacerange\n<00> <FF>\nendcodespacerange\n"
                + mappings + "\n"
                + "endcmap\nend\nend\n";
        PdfDictionary font = PdfDictionary.builder()
                .put(PdfName.of("Type"), PdfName.of("Font"))
                .put(PdfName.of("Subtype"), PdfName.of("Type1"))
                .put(PdfName.of("BaseFont"), PdfName.of("Helvetica"))
                .put(PdfName.of("Encoding"),
                        PdfName.of("WinAnsiEncoding"))
                .put(PdfName.of("ToUnicode"), PdfStream.of(
                        PdfDictionary.builder().build(),
                        cmap.getBytes(StandardCharsets.US_ASCII)))
                .build();
        PdfDictionary resources = PdfDictionary.builder()
                .put(PdfName.of("Font"), PdfDictionary.builder()
                        .put(PdfName.of("F1"), font)
                        .build())
                .build();
        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .target("output", PublicationTarget.path(target))
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    ObjectReference page = session.query(
                            PageObjectReference.version1(1));
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(
                                    page, PdfName.of("Resources"), resources)
                            .setDictionaryEntry(
                                    page,
                                    PdfName.of("Contents"),
                                    content("BT /F1 12 Tf (" + text
                                            + ") Tj ET\n"))
                            .build());
                    return null;
                });
    }

    private static void createBoundedFontInputFixture(Path target)
            throws Exception {
        PdfDictionary encoding = PdfDictionary.builder()
                .put(PdfName.of("Type"), PdfName.of("Encoding"))
                .put(PdfName.of("BaseEncoding"),
                        PdfName.of("WinAnsiEncoding"))
                .put(PdfName.of("Differences"), PdfArray.of(
                        PdfNumber.of(65L), PdfName.of("A")))
                .build();
        PdfStream fontProgram = PdfStream.of(
                PdfDictionary.builder()
                        .put(PdfName.of("Length1"), PdfNumber.of(
                                EMBEDDED_FONT_PROGRAM.length))
                        .put(PdfName.of("Length2"), PdfNumber.of(0L))
                        .build(),
                EMBEDDED_FONT_PROGRAM);
        PdfDictionary descriptor = PdfDictionary.builder()
                .put(PdfName.of("Type"), PdfName.of("FontDescriptor"))
                .put(PdfName.of("FontName"), PdfName.of("Helvetica"))
                .put(PdfName.of("FontFile"), fontProgram)
                .build();
        PdfDictionary font = PdfDictionary.builder()
                .put(PdfName.of("Type"), PdfName.of("Font"))
                .put(PdfName.of("Subtype"), PdfName.of("Type1"))
                .put(PdfName.of("BaseFont"), PdfName.of("Helvetica"))
                .put(PdfName.of("Encoding"), encoding)
                .put(PdfName.of("FontDescriptor"), descriptor)
                .build();
        PdfDictionary resources = PdfDictionary.builder()
                .put(PdfName.of("Font"), PdfDictionary.builder()
                        .put(PdfName.of("F1"), font)
                        .build())
                .build();
        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .target("output", PublicationTarget.path(target))
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    ObjectReference page = session.query(
                            PageObjectReference.version1(1));
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(
                                    page, PdfName.of("Resources"), resources)
                            .setDictionaryEntry(
                                    page,
                                    PdfName.of("Contents"),
                                    content("BT /F1 12 Tf (AAAA) Tj ET\n"))
                            .build());
                    return null;
                });
    }

    private static PdfStream form(
            String operators,
            PdfDictionary resources,
            PdfArray matrix) {
        return PdfStream.of(
                PdfDictionary.builder()
                        .put(PdfName.of("Type"), PdfName.of("XObject"))
                        .put(PdfName.of("Subtype"), PdfName.of("Form"))
                        .put(PdfName.of("BBox"), PdfArray.of(
                                PdfNumber.of(0L), PdfNumber.of(0L),
                                PdfNumber.of(100L), PdfNumber.of(100L)))
                        .put(PdfName.of("Matrix"), matrix)
                        .put(PdfName.of("Resources"), resources)
                        .build(),
                operators.getBytes(StandardCharsets.US_ASCII));
    }

    private static PdfArray translation(long x, long y) {
        return PdfArray.of(
                PdfNumber.of(1L), PdfNumber.of(0L), PdfNumber.of(0L),
                PdfNumber.of(1L), PdfNumber.of(x), PdfNumber.of(y));
    }

    private static PdfDictionary resourcesWithFontAndXObject(
            String xobjectName,
            PdfStream xobject) {
        PdfDictionary font = PdfDictionary.builder()
                .put(PdfName.of("Type"), PdfName.of("Font"))
                .put(PdfName.of("Subtype"), PdfName.of("Type1"))
                .put(PdfName.of("BaseFont"), PdfName.of("Helvetica"))
                .put(PdfName.of("Encoding"), PdfName.of("WinAnsiEncoding"))
                .build();
        return PdfDictionary.builder()
                .put(PdfName.of("Font"), PdfDictionary.builder()
                        .put(PdfName.of("F1"), font)
                        .build())
                .put(PdfName.of("XObject"), PdfDictionary.builder()
                        .put(PdfName.of(xobjectName), xobject)
                        .build())
                .build();
    }

    private static ExtractionLimits formLimits(
            int streams,
            int depth,
            long decodedBytes) {
        return ExtractionLimits.builder()
                .maximumPages(1)
                .maximumPageTreeNodes(2)
                .maximumContentStreams(streams)
                .maximumContentStreamDepth(depth)
                .maximumDecodedBytes(decodedBytes)
                .maximumTextItems(3)
                .maximumUnicodeCodePoints(3)
                .maximumMarkedContentSequences(0)
                .maximumMarkedContentDepth(0)
                .maximumStructureElements(0)
                .maximumStructureItems(0)
                .maximumStructureDepth(0)
                .maximumRoleMappings(0)
                .maximumToUnicodeMappings(0)
                .maximumFontDataEntries(0)
                .build();
    }

    private static ExtractionLimits mappingLimits(int mappings) {
        return ExtractionLimits.builder()
                .maximumPages(1)
                .maximumPageTreeNodes(2)
                .maximumContentStreams(1)
                .maximumContentStreamDepth(1)
                .maximumDecodedBytes(64L * 1024L)
                .maximumTextItems(2)
                .maximumUnicodeCodePoints(4)
                .maximumMarkedContentSequences(0)
                .maximumMarkedContentDepth(0)
                .maximumStructureElements(0)
                .maximumStructureItems(0)
                .maximumStructureDepth(0)
                .maximumRoleMappings(0)
                .maximumToUnicodeMappings(mappings)
                .maximumFontDataEntries(0)
                .build();
    }

    private static ExtractionLimits nestedMarkedLimits(int depth) {
        return ExtractionLimits.builder()
                .maximumPages(1)
                .maximumPageTreeNodes(2)
                .maximumContentStreams(1)
                .maximumContentStreamDepth(1)
                .maximumDecodedBytes(NESTED_MARKED_CONTENT.length())
                .maximumTextItems(1)
                .maximumUnicodeCodePoints(21)
                .maximumToUnicodeMappings(0)
                .maximumFontDataEntries(0)
                .maximumMarkedContentSequences(2)
                .maximumMarkedContentDepth(depth)
                .maximumStructureElements(0)
                .maximumStructureItems(0)
                .maximumStructureDepth(0)
                .maximumRoleMappings(0)
                .build();
    }

    private static ExtractionLimits textItemLimits(
            long decodedBytes,
            int textItems) {
        return ExtractionLimits.builder()
                .maximumPages(1)
                .maximumPageTreeNodes(2)
                .maximumContentStreams(1)
                .maximumContentStreamDepth(1)
                .maximumDecodedBytes(decodedBytes)
                .maximumTextItems(textItems)
                .maximumUnicodeCodePoints(0)
                .maximumToUnicodeMappings(0)
                .maximumFontDataEntries(0)
                .maximumMarkedContentSequences(0)
                .maximumMarkedContentDepth(0)
                .maximumStructureElements(0)
                .maximumStructureItems(0)
                .maximumStructureDepth(0)
                .maximumRoleMappings(0)
                .build();
    }

    private static ExtractionLimits deepFormLimits(
            int streams,
            long decodedBytes) {
        return ExtractionLimits.builder()
                .maximumPages(1)
                .maximumPageTreeNodes(2)
                .maximumContentStreams(streams)
                .maximumContentStreamDepth(ExtractionLimits
                        .MAXIMUM_CONTENT_STREAM_DEPTH_VERSION_1)
                .maximumDecodedBytes(decodedBytes)
                .maximumTextItems(0)
                .maximumUnicodeCodePoints(0)
                .maximumToUnicodeMappings(0)
                .maximumFontDataEntries(0)
                .maximumMarkedContentSequences(0)
                .maximumMarkedContentDepth(0)
                .maximumStructureElements(0)
                .maximumStructureItems(0)
                .maximumStructureDepth(0)
                .maximumRoleMappings(0)
                .build();
    }

    private static ExtractionLimits fontInputLimits(
            long decodedBytes,
            int fontDataEntries) {
        return ExtractionLimits.builder()
                .maximumPages(1)
                .maximumPageTreeNodes(2)
                .maximumContentStreams(1)
                .maximumContentStreamDepth(1)
                .maximumDecodedBytes(decodedBytes)
                .maximumTextItems(4)
                .maximumUnicodeCodePoints(4)
                .maximumToUnicodeMappings(0)
                .maximumFontDataEntries(fontDataEntries)
                .maximumMarkedContentSequences(0)
                .maximumMarkedContentDepth(0)
                .maximumStructureElements(0)
                .maximumStructureItems(0)
                .maximumStructureDepth(0)
                .maximumRoleMappings(0)
                .build();
    }

    private static ExtractionLimits cidFontLimits(int fontDataEntries) {
        return ExtractionLimits.builder()
                .maximumPages(1)
                .maximumPageTreeNodes(2)
                .maximumContentStreams(1)
                .maximumContentStreamDepth(1)
                .maximumDecodedBytes(64L * 1024L)
                .maximumTextItems(2)
                .maximumUnicodeCodePoints(4)
                .maximumToUnicodeMappings(2)
                .maximumFontDataEntries(fontDataEntries)
                .maximumMarkedContentSequences(0)
                .maximumMarkedContentDepth(0)
                .maximumStructureElements(0)
                .maximumStructureItems(0)
                .maximumStructureDepth(0)
                .maximumRoleMappings(0)
                .build();
    }

    private static ExtractionLimits pageTreeLimits(int pageTreeNodes) {
        return ExtractionLimits.builder()
                .maximumPages(1)
                .maximumPageTreeNodes(pageTreeNodes)
                .maximumContentStreams(0)
                .maximumContentStreamDepth(0)
                .maximumDecodedBytes(0)
                .maximumTextItems(0)
                .maximumUnicodeCodePoints(0)
                .maximumToUnicodeMappings(0)
                .maximumFontDataEntries(0)
                .maximumMarkedContentSequences(0)
                .maximumMarkedContentDepth(0)
                .maximumStructureElements(0)
                .maximumStructureItems(0)
                .maximumStructureDepth(0)
                .maximumRoleMappings(0)
                .build();
    }

    private static ExtractionLimits deepStructureLimits(int depth) {
        return ExtractionLimits.builder()
                .maximumPages(1)
                .maximumPageTreeNodes(2)
                .maximumContentStreams(0)
                .maximumContentStreamDepth(0)
                .maximumDecodedBytes(0)
                .maximumTextItems(0)
                .maximumUnicodeCodePoints(depth * 3)
                .maximumToUnicodeMappings(0)
                .maximumFontDataEntries(0)
                .maximumMarkedContentSequences(0)
                .maximumMarkedContentDepth(0)
                .maximumStructureElements(depth)
                .maximumStructureItems(depth)
                .maximumStructureDepth(depth)
                .maximumRoleMappings(0)
                .build();
    }

    private static void writeContentCycleFixture(Path target)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /XObject << /Fm 5 0 R >> >> "
                        + "/Contents 4 0 R >>",
                streamObject("/Fm Do\n", ""),
                streamObject("/Fm Do\n",
                        "/Type /XObject /Subtype /Form "
                                + "/BBox [0 0 100 100] "
                                + "/Resources << /XObject << /Fm 5 0 R >> >> "));
    }

    private static void writeNestedMarkedContentFixture(Path target)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 5 0 R >> >> "
                        + "/Contents 4 0 R >>",
                streamObject(NESTED_MARKED_CONTENT, ""),
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                        + "/Encoding /WinAnsiEncoding >>");
    }

    private static void writeSimpleTextFixture(Path target, String operators)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 5 0 R >> >> "
                        + "/Contents 4 0 R >>",
                streamObject(operators, ""),
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                        + "/Encoding /WinAnsiEncoding >>");
    }

    private static void writeFormFixture(
            Path target,
            String pageOperators,
            String formOperators,
            String formEntries) throws Exception {
        writeRawFormFixture(
                target,
                pageOperators,
                formOperators,
                "/Type /XObject /Subtype /Form " + formEntries);
    }

    private static void writeIndirectFormNameFixture(Path target)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /XObject << /Fm 5 0 R >> >> "
                        + "/Contents 4 0 R >>",
                streamObject("/Fm Do\n", ""),
                streamObject(
                        "",
                        "/Type 6 0 R /Subtype 7 0 R "
                                + "/BBox [0 0 100 100] "
                                + "/Resources << >> "),
                "/XObject",
                "/Form");
    }

    private static void writeRawFormFixture(
            Path target,
            String pageOperators,
            String formOperators,
            String formEntries) throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /XObject << /Fm 5 0 R >> >> "
                        + "/Contents 4 0 R >>",
                streamObject(pageOperators, ""),
                streamObject(
                        formOperators,
                        formEntries + " "));
    }

    private static void writeFontDictionaryFixture(Path target, String font)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 5 0 R >> >> "
                        + "/Contents 4 0 R >>",
                streamObject("BT /F1 12 Tf (A) Tj ET\n", ""),
                font);
    }

    private static void writeLargeGraphicsStateFixture(
            Path target,
            String operators,
            int dashEntries) throws Exception {
        StringBuilder dash = new StringBuilder(dashEntries * 2);
        for (int index = 0; index < dashEntries; index++) {
            dash.append("1 ");
        }
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 5 0 R >> "
                        + "/ExtGState << /GS 6 0 R >> >> "
                        + "/Contents 4 0 R >>",
                streamObject(operators, ""),
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                        + "/Encoding /WinAnsiEncoding >>",
                "<< /Type /ExtGState /Font [5 0 R 12] /D [["
                        + dash + "] 0] >>");
    }

    private static void writeDeepFormFixture(Path target, int formCount)
            throws Exception {
        String operator = "/Fm Do\n";
        String[] bodies = new String[4 + formCount];
        bodies[0] = "<< /Type /Catalog /Pages 2 0 R >>";
        bodies[1] = "<< /Type /Pages /Count 1 /Kids [3 0 R] >>";
        bodies[2] = "<< /Type /Page /Parent 2 0 R "
                + "/MediaBox [0 0 612 792] "
                + "/Resources << /XObject << /Fm 5 0 R >> >> "
                + "/Contents 4 0 R >>";
        bodies[3] = streamObject(operator, "");
        for (int index = 0; index < formCount; index++) {
            boolean leaf = index + 1 == formCount;
            String resources = leaf
                    ? "<< >>"
                    : "<< /XObject << /Fm " + (6 + index) + " 0 R >> >>";
            bodies[4 + index] = streamObject(
                    leaf ? "" : operator,
                    "/Type /XObject /Subtype /Form "
                            + "/BBox [0 0 100 100] "
                            + "/Resources " + resources + " ");
        }
        writePdf(target, bodies);
    }

    private static void writeCidMetricFixture(Path target, String widths)
            throws Exception {
        writeCidMetricFixture(target, widths, "");
    }

    private static void writeCidMetricFixture(
            Path target,
            String widths,
            String additionalMetrics) throws Exception {
        String operators = "BT /F1 12 Tf <00410042> Tj ET\n";
        String cmap = "/CIDInit /ProcSet findresource begin\n"
                + "12 dict begin\nbegincmap\n"
                + "/CIDSystemInfo << /Registry (Folio) /Ordering (T13) "
                + "/Supplement 0 >> def\n"
                + "/CMapName /FolioT13CIDUnicode def\n/CMapType 2 def\n"
                + "1 begincodespacerange\n<0000> <FFFF>\n"
                + "endcodespacerange\n"
                + "2 beginbfchar\n<0041> <0041>\n<0042> <0042>\n"
                + "endbfchar\nendcmap\n"
                + "CMapName currentdict /CMap defineresource pop\n"
                + "end\nend\n";
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 5 0 R >> >> "
                        + "/Contents 4 0 R >>",
                streamObject(operators, ""),
                "<< /Type /Font /Subtype /Type0 /BaseFont /FolioT13 "
                        + "/Encoding /Identity-H "
                        + "/DescendantFonts [6 0 R] /ToUnicode 7 0 R >>",
                "<< /Type /Font /Subtype /CIDFontType2 "
                        + "/BaseFont /FolioT13 "
                        + "/CIDSystemInfo << /Registry (Folio) "
                        + "/Ordering (T13) /Supplement 0 >> "
                        + "/DW 1000 /W " + widths + " "
                        + additionalMetrics + " >>",
                streamObject(cmap, ""));
    }

    private static void writeNestedType0DescendantFixture(
            Path target,
            int nestedType0Fonts) throws Exception {
        String[] bodies = new String[6 + nestedType0Fonts];
        bodies[0] = "<< /Type /Catalog /Pages 2 0 R >>";
        bodies[1] = "<< /Type /Pages /Count 1 /Kids [3 0 R] >>";
        bodies[2] = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                + "/Resources << /Font << /F1 5 0 R >> >> "
                + "/Contents 4 0 R >>";
        bodies[3] = streamObject("BT /F1 12 Tf <0041> Tj ET\n", "");
        for (int index = 0; index <= nestedType0Fonts; index++) {
            int objectNumber = 5 + index;
            int descendantNumber = objectNumber + 1;
            bodies[4 + index] = "<< /Type /Font /Subtype /Type0 "
                    + "/BaseFont /FolioT13 /Encoding /Identity-H "
                    + "/DescendantFonts [" + descendantNumber + " 0 R] >>";
        }
        bodies[bodies.length - 1] =
                "<< /Type /Font /Subtype /CIDFontType2 "
                        + "/BaseFont /FolioT13 "
                        + "/CIDSystemInfo << /Registry (Folio) "
                        + "/Ordering (T13) /Supplement 0 >> /DW 1000 >>";
        writePdf(target, bodies);
    }

    private static void writeType0EncodingFixture(
            Path target,
            String encodingEntry) throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 5 0 R >> >> "
                        + "/Contents 4 0 R >>",
                streamObject("BT /F1 12 Tf ET\n", ""),
                "<< /Type /Font /Subtype /Type0 /BaseFont /FolioT13 "
                        + encodingEntry
                        + "/DescendantFonts [6 0 R] >>",
                "<< /Type /Font /Subtype /CIDFontType2 "
                        + "/BaseFont /FolioT13 "
                        + "/CIDSystemInfo << /Registry (Adobe) "
                        + "/Ordering (Japan1) /Supplement 7 >> /DW 1000 >>");
    }

    private static void writeMismatchedEmbeddedType0Fixture(Path target)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 5 0 R >> >> "
                        + "/Contents 4 0 R >>",
                streamObject("BT /F1 12 Tf ET\n", ""),
                "<< /Type /Font /Subtype /Type0 /BaseFont /FolioT13 "
                        + "/Encoding /Identity-H "
                        + "/DescendantFonts [6 0 R] >>",
                "<< /Type /Font /Subtype /CIDFontType0 "
                        + "/BaseFont /FolioT13 "
                        + "/CIDSystemInfo << /Registry (Folio) "
                        + "/Ordering (T13) /Supplement 0 >> /DW 1000 "
                        + "/FontDescriptor 7 0 R >>",
                "<< /Type /FontDescriptor /FontName /FolioT13 "
                        + "/Flags 4 /FontBBox [0 0 1000 1000] "
                        + "/ItalicAngle 0 /Ascent 800 /Descent -200 "
                        + "/CapHeight 700 /StemV 80 /FontFile2 8 0 R >>",
                streamObject("\u0000\u0001\u0000\u0000", ""));
    }

    private static void writeType3FontFixture(
            Path target,
            int glyphProgramBytes) throws Exception {
        StringBuilder glyphProgram = new StringBuilder(glyphProgramBytes);
        glyphProgram.append("1000 0 d0\n");
        while (glyphProgram.length() < glyphProgramBytes) {
            glyphProgram.append(' ');
        }
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 5 0 R >> >> "
                        + "/Contents 4 0 R >>",
                streamObject("BT /F1 12 Tf (A) Tj ET\n", ""),
                "<< /Type /Font /Subtype /Type3 /Name /F1 "
                        + "/FontBBox [0 0 1000 1000] "
                        + "/FontMatrix [.001 0 0 .001 0 0] "
                        + "/CharProcs << /A 6 0 R >> "
                        + "/Encoding << /Type /Encoding "
                        + "/Differences [65 /A] >> "
                        + "/FirstChar 65 /LastChar 65 /Widths [1000] "
                        + "/Resources << >> >>",
                streamObject(glyphProgram.toString(), ""));
    }

    private static void writeMissingResourcesFixture(
            Path target,
            String operators) throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Contents 4 0 R >>",
                streamObject(operators, ""));
    }

    private static void writeMissingGraphicsStateFixture(
            Path target,
            String resources) throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources " + resources + " /Contents 4 0 R >>",
                streamObject("/Missing gs\n", ""));
    }

    private static void writeFontResourceStreamFixture(Path target)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font 5 0 R >> /Contents 4 0 R >>",
                streamObject("BT /F1 12 Tf (A) Tj ET\n", ""),
                streamObject("", "/F1 6 0 R "),
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                        + "/Encoding /WinAnsiEncoding >>");
    }

    private static void writeGraphicsStateResourceStreamFixture(Path target)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /ExtGState << /Missing 5 0 R >> >> "
                        + "/Contents 4 0 R >>",
                streamObject("/Missing gs\n", ""),
                streamObject("", ""));
    }

    private static void writePropertyResourceStreamFixture(Path target)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Properties 5 0 R >> "
                        + "/Contents 4 0 R >>",
                streamObject("/Span /Missing BDC EMC\n", ""),
                streamObject("", "/Missing << /MCID 0 >> "));
    }

    private static void writeXObjectResourceStreamFixture(Path target)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /XObject 5 0 R >> "
                        + "/Contents 4 0 R >>",
                streamObject("/Fm Do\n", ""),
                streamObject("", "/Fm 6 0 R "),
                streamObject("", "/Type /XObject /Subtype /Form "
                        + "/BBox [0 0 100 100] /Resources << >> "));
    }

    private static void writeMalformedContentsFixture(Path target)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << >> /Contents [4 0 R 5 0 R] >>",
                streamObject("", ""),
                "0");
    }

    private static void writeSplitContentTokenFixture(Path target)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 6 0 R >> >> "
                        + "/Contents [4 0 R 5 0 R] >>",
                streamObject("BT /F1 12 Tf <4", ""),
                streamObject("1> Tj ET\n", ""),
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                        + "/Encoding /WinAnsiEncoding >>");
    }

    private static void writeFalsePageCountFixture(Path target)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R 4 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << >> >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << >> >>");
    }

    private static void writeNegativePageCountFixture(Path target)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count -1 /Kids [] >>");
    }

    private static void writeWrappedPageCountFixture(Path target)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 4294967297 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << >> >>");
    }

    private static void writeCyclicPageTreeFixture(Path target)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Pages /Parent 2 0 R /Count 1 "
                        + "/Kids [2 0 R] >>");
    }

    private static void writeInheritedPageAttributesFixture(
            Path target,
            String attributes) throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] "
                        + attributes + " >>",
                "<< /Type /Page /Parent 2 0 R >>");
    }

    private static void writeDirectPageAttributesFixture(
            Path target,
            String attributes) throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] "
                        + "/Resources << >> /MediaBox [0 0 612 792] >>",
                "<< /Type /Page /Parent 2 0 R " + attributes + " >>");
    }

    private static void writeDeepPageTreeFixture(
            Path target,
            int internalNodes) throws Exception {
        String[] bodies = new String[internalNodes + 2];
        bodies[0] = "<< /Type /Catalog /Pages 2 0 R >>";
        for (int index = 0; index < internalNodes; index++) {
            int objectNumber = index + 2;
            int childNumber = objectNumber + 1;
            String parent = index == 0
                    ? ""
                    : "/Parent " + (objectNumber - 1) + " 0 R ";
            String inherited = index == 0
                    ? "/MediaBox [0 0 612 792] "
                            + "/CropBox [10 20 600 700] "
                            + "/Resources << >> /Rotate 90 "
                    : "";
            bodies[index + 1] = "<< /Type /Pages " + parent + inherited
                    + "/Count 1 /Kids [" + childNumber + " 0 R] >>";
        }
        bodies[bodies.length - 1] =
                "<< /Type /Page /Parent " + (internalNodes + 1)
                        + " 0 R /UserUnit 2 >>";
        writePdf(target, bodies);
    }

    private static void writeRepeatedContentsFixture(
            Path target,
            int occurrences) throws Exception {
        StringBuilder contents = new StringBuilder("[");
        for (int index = 0; index < occurrences; index++) {
            contents.append("4 0 R ");
        }
        contents.append(']');
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << >> /Contents " + contents + " >>",
                streamObject("", ""));
    }

    private static void writeStructureCycleFixture(Path target)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 5 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << >> >>",
                "<< >>",
                "<< /Type /StructTreeRoot /K 6 0 R >>",
                "<< /Type /StructElem /S /Document /P 5 0 R /K 6 0 R >>");
    }

    private static void writeRoleMapCycleFixture(Path target)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 4 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << >> >>",
                "<< /Type /StructTreeRoot /RoleMap << /A /B /B /A >> >>");
    }

    private static void writeStructureLinkFixture(
            Path target,
            String rootChildren,
            String parent) throws Exception {
        writeStructureLinkFixture(target, rootChildren, parent, "");
    }

    private static void writeStructureLinkFixture(
            Path target,
            String rootChildren,
            String parent,
            String extra) throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 5 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << >> >>",
                "<< >>",
                "<< /Type /StructTreeRoot /K " + rootChildren + " >>",
                "<< /Type /StructElem /S /Document /P " + parent + " "
                        + extra + " >>");
    }

    private static void writeObjectReferenceStructureFixture(Path target)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 6 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << >> /Contents 4 0 R >>",
                streamObject("/Span <</MCID 0>> BDC EMC\n", ""),
                "<< >>",
                "<< /Type /StructTreeRoot /K 7 0 R >>",
                "<< /Type /StructElem /S /Document /P 6 0 R /Pg 3 0 R "
                        + "/K << /Type /OBJR /Pg 3 0 R /MCID 0 >> >>");
    }

    private static void writeStreamOwnerMcrFixture(Path target)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 6 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << >> /Contents 4 0 R >>",
                streamObject("/Span <</MCID 0>> BDC EMC\n", ""),
                "<< >>",
                "<< /Type /StructTreeRoot /K 7 0 R >>",
                "<< /Type /StructElem /S /Document /P 6 0 R /Pg 3 0 R "
                        + "/K << /Type /MCR /MCID 0 /StmOwn 7 0 R >> >>");
    }

    private static void writeDeepStructureFixture(Path target, int depth)
            throws Exception {
        String[] bodies = new String[5 + depth];
        bodies[0] = "<< /Type /Catalog /Pages 2 0 R "
                + "/StructTreeRoot 5 0 R >>";
        bodies[1] = "<< /Type /Pages /Count 1 /Kids [3 0 R] >>";
        bodies[2] = "<< /Type /Page /Parent 2 0 R "
                + "/MediaBox [0 0 612 792] /Resources << >> >>";
        bodies[3] = "<< >>";
        bodies[4] = "<< /Type /StructTreeRoot /K 6 0 R >>";
        for (int index = 0; index < depth; index++) {
            int objectNumber = 6 + index;
            String page = index == 0 ? "/Pg 3 0 R " : "";
            String child = index + 1 < depth
                    ? "/K " + (objectNumber + 1) + " 0 R "
                    : "";
            int parent = index == 0 ? 5 : objectNumber - 1;
            bodies[5 + index] = "<< /Type /StructElem /S /Div /P "
                    + parent + " 0 R "
                    + page + child + ">>";
        }
        writePdf(target, bodies);
    }

    private static void writeTaggedHierarchyFixture(Path target)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 6 0 R "
                        + "/MarkInfo << /Marked true >> /Lang (en-US) >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 5 0 R >> >> "
                        + "/Contents 4 0 R /StructParents 0 >>",
                streamObject(
                        "/Span <</MCID 0 /Lang (de-DE) "
                                + "/Alt (content alternate) "
                                + "/ActualText (Actual)>> BDC "
                                + "BT /F1 12 Tf (Visible) Tj ET EMC\n",
                        ""),
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                        + "/Encoding /WinAnsiEncoding >>",
                "<< /Type /StructTreeRoot /K 7 0 R "
                        + "/RoleMap << /Story /Chapter /Chapter /Sect >> "
                        + "/ParentTree 10 0 R /ParentTreeNextKey 1 >>",
                "<< /Type /StructElem /S /Document /P 6 0 R /K 8 0 R >>",
                "<< /S /Story /P 7 0 R "
                        + "/Lang (fr-CA) /Alt (Story alternative) "
                        + "/ActualText (Structure replacement) /K 9 0 R >>",
                "<< /Type /StructElem /S /Span /P 8 0 R /Pg 3 0 R "
                        + "/K << /Type /MCR /Pg 3 0 R /MCID 0 >> >>",
                "<< /Nums [0 [9 0 R]] >>");
    }

    private static void writeUnqualifiedRoleFixture(
            Path target,
            String role) throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 4 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << >> >>",
                "<< /Type /StructTreeRoot /K 5 0 R >>",
                "<< /Type /StructElem /S /" + role + " /P 4 0 R >>");
    }

    private static String streamObject(String data, String entries) {
        return "<< " + entries + "/Length "
                + data.getBytes(StandardCharsets.US_ASCII).length
                + " >>\nstream\n" + data + "endstream";
    }

    private static void writePdf(Path target, String... objectBodies)
            throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII));
        int[] offsets = new int[objectBodies.length + 1];
        for (int index = 0; index < objectBodies.length; index++) {
            offsets[index + 1] = output.size();
            output.write(((index + 1) + " 0 obj\n")
                    .getBytes(StandardCharsets.US_ASCII));
            output.write(objectBodies[index]
                    .getBytes(StandardCharsets.US_ASCII));
            output.write("\nendobj\n".getBytes(StandardCharsets.US_ASCII));
        }
        int xref = output.size();
        output.write(("xref\n0 " + (objectBodies.length + 1) + "\n")
                .getBytes(StandardCharsets.US_ASCII));
        output.write("0000000000 65535 f \n"
                .getBytes(StandardCharsets.US_ASCII));
        for (int index = 1; index < offsets.length; index++) {
            output.write(String.format(
                    Locale.ROOT,
                    "%010d 00000 n \n",
                    Integer.valueOf(offsets[index]))
                    .getBytes(StandardCharsets.US_ASCII));
        }
        output.write(("trailer\n<< /Size " + offsets.length
                + " /Root 1 0 R >>\nstartxref\n" + xref
                + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII));
        Files.write(target, output.toByteArray());
    }

    private static LogicalStructureElement childElement(
            LogicalStructureElement parent,
            int index) {
        LogicalStructureItem item = parent.getChildren().get(index);
        assertEquals(LogicalStructureItem.Kind.ELEMENT, item.getKind());
        return item.getElement().get();
    }

    private static void assertDeterministicGeometry(
            TextStructureExtraction extraction) {
        assertEquals(2, extraction.getPages().size());
        PageText firstPage = extraction.getPages().get(0);
        PageText secondPage = extraction.getPages().get(1);
        assertEquals("AB", firstPage.getText());
        assertEquals("DE", secondPage.getText());
        assertEquals(2, firstPage.getTextItems().size());
        assertEquals(2, secondPage.getTextItems().size());
        assertEquals(90, secondPage.getRotation());

        CharacterMapping firstMapping = firstPage.getTextItems().get(0)
                .getCharacterMapping();
        assertEquals(CharacterMapping.Confidence.EXPLICIT,
                firstMapping.getConfidence());
        assertEquals("A", firstMapping.getExplicitUnicode().get());
        assertEquals("A", firstMapping.getInferredUnicode().get());

        TextItem transformed = secondPage.getTextItems().get(0);
        assertEquals(new BigDecimal("20"), transformed.getGeometry().getA());
        assertEquals(new BigDecimal("5"), transformed.getGeometry().getB());
        assertEquals(new BigDecimal("-2.5"), transformed.getGeometry().getC());
        assertEquals(new BigDecimal("30"), transformed.getGeometry().getD());
        assertEquals(new BigDecimal("40"), transformed.getGeometry().getE());
        assertEquals(new BigDecimal("50"), transformed.getGeometry().getF());

        TextItem rotated = secondPage.getTextItems().get(1);
        assertEquals(BigDecimal.ZERO, rotated.getGeometry().getA());
        assertEquals(new BigDecimal("10"), rotated.getGeometry().getB());
        assertEquals(new BigDecimal("-10"), rotated.getGeometry().getC());
        assertEquals(BigDecimal.ZERO, rotated.getGeometry().getD());
        assertEquals(new BigDecimal("100"), rotated.getGeometry().getE());
        assertEquals(new BigDecimal("200"), rotated.getGeometry().getF());
    }

    private static String fingerprint(TextStructureExtraction extraction) {
        StringBuilder value = new StringBuilder();
        for (PageText page : extraction.getPages()) {
            value.append(page.getPageNumber()).append(':')
                    .append(page.getText()).append(':')
                    .append(page.getRotation()).append(';');
            for (TextItem item : page.getTextItems()) {
                value.append(item.getCharacterMapping().getConfidence())
                        .append('@')
                        .append(item.getGeometry().getA().toPlainString())
                        .append(',')
                        .append(item.getGeometry().getB().toPlainString())
                        .append(',')
                        .append(item.getGeometry().getC().toPlainString())
                        .append(',')
                        .append(item.getGeometry().getD().toPlainString())
                        .append(',')
                        .append(item.getGeometry().getE().toPlainString())
                        .append(',')
                        .append(item.getGeometry().getF().toPlainString())
                        .append(';');
            }
        }
        return value.toString();
    }

    private static void createDeterministicFixture(Path target) throws Exception {
        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .target("output", PublicationTarget.path(target))
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    session.execute(AddBlankPage.INSTANCE);
                    PdfDictionary resources = resourcesWithToUnicodeHelvetica();
                    ObjectReference first = session.query(
                            PageObjectReference.version1(1));
                    ObjectReference second = session.query(
                            PageObjectReference.version1(2));
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(first, PdfName.of("Resources"), resources)
                            .setDictionaryEntry(first, PdfName.of("Contents"), PdfArray.of(
                                    content("BT /F1 12 Tf 10 20 Td (A) Tj ET\n"),
                                    content("BT /F1 12 Tf 30 40 Td (B) Tj ET\n")))
                            .setDictionaryEntry(second, PdfName.of("Resources"), resources)
                            .setDictionaryEntry(second, PdfName.of("Contents"), content(
                                    "q 2 .5 -.25 3 40 50 cm "
                                            + "BT /F1 10 Tf (D) Tj ET Q\n"
                                            + "BT /F1 10 Tf 0 1 -1 0 100 200 Tm "
                                            + "(E) Tj ET\n"))
                            .setDictionaryEntry(second, PdfName.of("Rotate"),
                                    PdfNumber.of(90L))
                            .build());
                    return null;
                });
    }

    private static PdfStream content(String operators) {
        return PdfStream.of(
                PdfDictionary.builder().build(),
                operators.getBytes(StandardCharsets.US_ASCII));
    }

    private static PdfDictionary resourcesWithToUnicodeHelvetica() {
        String cmap = "/CIDInit /ProcSet findresource begin\n"
                + "12 dict begin\nbegincmap\n"
                + "/CIDSystemInfo << /Registry (Folio) /Ordering (T13) "
                + "/Supplement 0 >> def\n"
                + "/CMapName /FolioT13 def\n/CMapType 2 def\n"
                + "1 begincodespacerange\n<00> <FF>\nendcodespacerange\n"
                + "5 beginbfchar\n<41> <0041>\n<42> <0042>\n"
                + "<43> <0043>\n<44> <0044>\n<45> <0045>\n"
                + "endbfchar\nendcmap\n"
                + "CMapName currentdict /CMap defineresource pop\n"
                + "end\nend\n";
        PdfDictionary font = PdfDictionary.builder()
                .put(PdfName.of("Type"), PdfName.of("Font"))
                .put(PdfName.of("Subtype"), PdfName.of("Type1"))
                .put(PdfName.of("BaseFont"), PdfName.of("Helvetica"))
                .put(PdfName.of("Encoding"), PdfName.of("WinAnsiEncoding"))
                .put(PdfName.of("ToUnicode"), PdfStream.of(
                        PdfDictionary.builder().build(),
                        cmap.getBytes(StandardCharsets.US_ASCII)))
                .build();
        return PdfDictionary.builder()
                .put(PdfName.of("Font"), PdfDictionary.builder()
                        .put(PdfName.of("F1"), font)
                        .build())
                .build();
    }

    private static PdfDictionary uncertainMappingResources() {
        String cmap = "/CIDInit /ProcSet findresource begin\n"
                + "12 dict begin\nbegincmap\n"
                + "/CMapName /FolioT13Contradiction def\n/CMapType 2 def\n"
                + "1 begincodespacerange\n<00> <FF>\nendcodespacerange\n"
                + "1 beginbfchar\n<41> <005A>\nendbfchar\n"
                + "endcmap\nend\nend\n";
        PdfDictionary contradictory = PdfDictionary.builder()
                .put(PdfName.of("Type"), PdfName.of("Font"))
                .put(PdfName.of("Subtype"), PdfName.of("Type1"))
                .put(PdfName.of("BaseFont"), PdfName.of("Helvetica"))
                .put(PdfName.of("Encoding"), PdfName.of("WinAnsiEncoding"))
                .put(PdfName.of("ToUnicode"), PdfStream.of(
                        PdfDictionary.builder().build(),
                        cmap.getBytes(StandardCharsets.US_ASCII)))
                .build();
        PdfDictionary unknownEncoding = PdfDictionary.builder()
                .put(PdfName.of("Type"), PdfName.of("Encoding"))
                .put(PdfName.of("BaseEncoding"),
                        PdfName.of("WinAnsiEncoding"))
                .put(PdfName.of("Differences"), PdfArray.of(
                        PdfNumber.of(66L),
                        PdfName.of("UnknownT13Glyph")))
                .build();
        PdfDictionary missing = PdfDictionary.builder()
                .put(PdfName.of("Type"), PdfName.of("Font"))
                .put(PdfName.of("Subtype"), PdfName.of("Type1"))
                .put(PdfName.of("BaseFont"), PdfName.of("Helvetica"))
                .put(PdfName.of("Encoding"), unknownEncoding)
                .build();
        PdfDictionary backendFallback = PdfDictionary.builder()
                .put(PdfName.of("Type"), PdfName.of("Font"))
                .put(PdfName.of("Subtype"), PdfName.of("Type1"))
                .put(PdfName.of("BaseFont"), PdfName.of("Helvetica"))
                .build();
        PdfDictionary noBaseEncoding = PdfDictionary.builder()
                .put(PdfName.of("Type"), PdfName.of("Encoding"))
                .put(PdfName.of("Differences"), PdfArray.of(
                        PdfNumber.of(68L), PdfName.of("D")))
                .build();
        PdfDictionary noBase = PdfDictionary.builder()
                .put(PdfName.of("Type"), PdfName.of("Font"))
                .put(PdfName.of("Subtype"), PdfName.of("Type1"))
                .put(PdfName.of("BaseFont"), PdfName.of("Helvetica"))
                .put(PdfName.of("Encoding"), noBaseEncoding)
                .build();
        PdfDictionary unknownBaseEncoding = PdfDictionary.builder()
                .put(PdfName.of("Type"), PdfName.of("Encoding"))
                .put(PdfName.of("BaseEncoding"),
                        PdfName.of("FolioUnknownEncoding"))
                .put(PdfName.of("Differences"), PdfArray.of(
                        PdfNumber.of(69L), PdfName.of("E")))
                .build();
        PdfDictionary unknownBase = PdfDictionary.builder()
                .put(PdfName.of("Type"), PdfName.of("Font"))
                .put(PdfName.of("Subtype"), PdfName.of("Type1"))
                .put(PdfName.of("BaseFont"), PdfName.of("Helvetica"))
                .put(PdfName.of("Encoding"), unknownBaseEncoding)
                .build();
        return PdfDictionary.builder()
                .put(PdfName.of("Font"), PdfDictionary.builder()
                        .put(PdfName.of("F1"), contradictory)
                        .put(PdfName.of("F2"), missing)
                        .put(PdfName.of("F3"), backendFallback)
                        .put(PdfName.of("F4"), noBase)
                        .put(PdfName.of("F5"), unknownBase)
                        .build())
                .build();
    }

    private static WorkflowRequest sourceRequest(Path source) {
        return WorkflowRequest.builder()
                .source("input", DocumentSource.path(source))
                .primarySource("input")
                .saveMode(SaveMode.REWRITE)
                .build();
    }

    private static ObjectReference type0Descendant(DocumentSession session)
            throws DocumentFailure {
        ObjectReference pageReference = session.query(
                PageObjectReference.version1(1));
        PdfDictionary page = inspectedDictionary(session, pageReference);
        PdfDictionary resources = (PdfDictionary) page.get(
                PdfName.of("Resources"));
        PdfDictionary fonts = (PdfDictionary) resources.get(
                PdfName.of("Font"));
        ObjectReference parentReference = ((PdfIndirectReference) fonts.get(
                PdfName.of("F1"))).getReference();
        PdfDictionary parent = inspectedDictionary(session, parentReference);
        PdfArray descendants = (PdfArray) parent.get(
                PdfName.of("DescendantFonts"));
        PdfValue descendant = descendants.get(0);
        return ((PdfIndirectReference) descendant).getReference();
    }

    private static PdfName fontSubtype(
            DocumentSession session,
            ObjectReference font) throws DocumentFailure {
        return (PdfName) inspectedDictionary(session, font).get(
                PdfName.of("Subtype"));
    }

    private static PdfValue formEntry(
            DocumentSession session,
            PdfName name) throws DocumentFailure {
        ObjectReference pageReference = session.query(
                PageObjectReference.version1(1));
        PdfDictionary page = inspectedDictionary(session, pageReference);
        PdfDictionary resources = (PdfDictionary) page.get(
                PdfName.of("Resources"));
        PdfDictionary xObjects = (PdfDictionary) resources.get(
                PdfName.of("XObject"));
        ObjectReference formReference = ((PdfIndirectReference) xObjects.get(
                PdfName.of("Fm"))).getReference();
        PdfStream form = (PdfStream) session.query(InspectObject.version1(
                formReference,
                PdfInspectionLimits.of(16, 16L)));
        return form.getDictionary().get(name);
    }

    private static void assertIndirectName(
            DocumentSession session,
            PdfValue value,
            PdfName expected) throws DocumentFailure {
        assertTrue(value instanceof PdfIndirectReference);
        PdfIndirectReference reference = (PdfIndirectReference) value;
        assertEquals(expected, session.query(InspectObject.version1(
                reference.getReference(),
                PdfInspectionLimits.of(1, 0L))));
    }

    private static PdfDictionary inspectedDictionary(
            DocumentSession session,
            ObjectReference reference) throws DocumentFailure {
        return (PdfDictionary) session.query(InspectObject.version1(
                reference,
                PdfInspectionLimits.of(16, 16L)));
    }

    private static PdfDictionary resourcesWithWinAnsiHelvetica() {
        PdfDictionary font = PdfDictionary.builder()
                .put(PdfName.of("Type"), PdfName.of("Font"))
                .put(PdfName.of("Subtype"), PdfName.of("Type1"))
                .put(PdfName.of("BaseFont"), PdfName.of("Helvetica"))
                .put(PdfName.of("Encoding"), PdfName.of("WinAnsiEncoding"))
                .build();
        return PdfDictionary.builder()
                .put(PdfName.of("Font"), PdfDictionary.builder()
                        .put(PdfName.of("F1"), font)
                        .build())
                .build();
    }

    private static ExtractionLimits limits() {
        return ExtractionLimits.builder()
                .maximumPages(2)
                .maximumPageTreeNodes(256)
                .maximumContentStreams(8)
                .maximumContentStreamDepth(4)
                .maximumDecodedBytes(64 * 1024L)
                .maximumTextItems(128)
                .maximumUnicodeCodePoints(1024)
                .maximumMarkedContentSequences(32)
                .maximumMarkedContentDepth(8)
                .maximumStructureElements(32)
                .maximumStructureItems(64)
                .maximumStructureDepth(8)
                .maximumRoleMappings(16)
                .maximumToUnicodeMappings(64)
                .maximumFontDataEntries(64)
                .build();
    }

    private static final class BoundaryLimits {

        private int pages = 1;
        private int pageTreeNodes = 2;
        private int streams = 1;
        private int streamDepth = 1;
        private long decodedBytes = BOUNDED_CONTENT.length();
        private int textItems = 1;
        private int unicode = 25;
        private int markedSequences = 1;
        private int markedDepth = 1;
        private int structureElements = 1;
        private int structureItems = 2;
        private int structureDepth = 1;
        private int roleMappings = 1;
        private int toUnicodeMappings;
        private int fontDataEntries;

        BoundaryLimits pages(int value) { pages = value; return this; }
        BoundaryLimits pageTreeNodes(int value) {
            pageTreeNodes = value;
            return this;
        }
        BoundaryLimits streams(int value) { streams = value; return this; }
        BoundaryLimits streamDepth(int value) {
            streamDepth = value;
            return this;
        }
        BoundaryLimits decodedBytes(long value) {
            decodedBytes = value;
            return this;
        }
        BoundaryLimits textItems(int value) {
            textItems = value;
            return this;
        }
        BoundaryLimits unicode(int value) { unicode = value; return this; }
        BoundaryLimits markedSequences(int value) {
            markedSequences = value;
            return this;
        }
        BoundaryLimits markedDepth(int value) {
            markedDepth = value;
            return this;
        }
        BoundaryLimits structureElements(int value) {
            structureElements = value;
            return this;
        }
        BoundaryLimits structureItems(int value) {
            structureItems = value;
            return this;
        }
        BoundaryLimits structureDepth(int value) {
            structureDepth = value;
            return this;
        }
        BoundaryLimits roleMappings(int value) {
            roleMappings = value;
            return this;
        }
        BoundaryLimits toUnicodeMappings(int value) {
            toUnicodeMappings = value;
            return this;
        }
        BoundaryLimits fontDataEntries(int value) {
            fontDataEntries = value;
            return this;
        }

        ExtractionLimits build() {
            return ExtractionLimits.builder()
                    .maximumPages(pages)
                    .maximumPageTreeNodes(pageTreeNodes)
                    .maximumContentStreams(streams)
                    .maximumContentStreamDepth(streamDepth)
                    .maximumDecodedBytes(decodedBytes)
                    .maximumTextItems(textItems)
                    .maximumUnicodeCodePoints(unicode)
                    .maximumMarkedContentSequences(markedSequences)
                    .maximumMarkedContentDepth(markedDepth)
                    .maximumStructureElements(structureElements)
                    .maximumStructureItems(structureItems)
                    .maximumStructureDepth(structureDepth)
                    .maximumRoleMappings(roleMappings)
                    .maximumToUnicodeMappings(toUnicodeMappings)
                    .maximumFontDataEntries(fontDataEntries)
                    .build();
        }
    }
}
