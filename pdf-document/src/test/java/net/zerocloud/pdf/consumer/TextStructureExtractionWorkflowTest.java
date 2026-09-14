package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import net.zerocloud.pdf.LogicalObjectReference;
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
import net.zerocloud.pdf.PdfString;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.TextItem;
import net.zerocloud.pdf.TextStructureExtraction;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowExecutionProfile;
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
    private static final String TYPE3_CONTENT = "BT /F1 10 Tf 1 0 0 1 20 30 Tm (AAA) Tj ET\n";
    private static final String TYPE3_GLYPH = "600 0 d0 0 0 500 700 re f\n";
    private static final String TYPE3_FONT =
            "<< /Type /Font /Subtype /Type3 /Name /F1 /FontBBox [0 0 600 700] "
                    + "/FontMatrix [.002 0 0 .003 0 0] /FirstChar 65 /LastChar 65 /Widths [600] "
                    + "/Encoding << /Type /Encoding /Differences [65 /A] >> "
                    + "/CharProcs << /A 6 0 R /Alias 6 0 R >> /Resources << >> >>";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void untaggedPageTextObservesEarlierPatchAndReturnsDetachedEvidence()
            throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("untagged.pdf");

        WorkflowOutcome<TextStructureExtraction> outcome =
                new DocumentWorkflow().execute(
                        requestBuilder()
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
                requestBuilder()
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
                requestBuilder()
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
    public void inheritedToUnicodeMappingsUseExactBytesAndLocalOverrides() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("inherited-to-unicode.pdf");
        String content = "BT /F1 10 Tf 1 0 0 1 20 30 Tm (ABCD) Tj /F1 10 Tf (ABCD) Tj ET\n";
        String parent = "begincmap\n1 begincodespacerange\n<00> <FF>\nendcodespacerange\n"
                + "1 beginbfrange\n<41> <42> <0058>\nendbfrange\n"
                + "2 beginbfchar\n<43> <005A>\n<44> <0044>\nendbfchar\nendcmap\n";
        String child = "begincmap\n1 beginbfchar\n<41> <0041>\nendbfchar\n"
                + "1 beginbfrange\n<42> <43> [<0042> <0043>]\nendbfrange\nendcmap\n";
        writePdf(source,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                streamObject(content, ""),
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding /ToUnicode 6 0 R >>",
                streamObject(child, "/UseCMap 7 0 R"), streamObject(parent, ""));
        byte[] before = Files.readAllBytes(source);
        long bytes = content.length() + parent.length() + child.length();
        PageText page = query(source, new BoundaryLimits().textItems(8).unicode(8).toUnicodeMappings(7)
                .fontDataEntries(3).decodedBytes(bytes).build()).getPages().get(0);
        assertEquals("ABCDABCD", page.getText());
        for (int index = 0; index < 8; index++) {
            CharacterMapping mapping = page.getTextItems().get(index).getCharacterMapping();
            assertEquals(CharacterMapping.Confidence.EXPLICIT, mapping.getConfidence());
            assertArrayEquals(new byte[] {(byte) ('A' + index % 4)}, mapping.getSourceCode());
        }
        assertEquals(20.0, page.getTextItems().get(0).getGeometry().getE().doubleValue(), 0.0001);
        assertEquals(6.67, page.getTextItems().get(0).getGeometry().getAdvanceX().doubleValue(), 0.001);
        assertLimitFailure(source, new BoundaryLimits().textItems(8).unicode(8).toUnicodeMappings(6)
                .fontDataEntries(3).decodedBytes(bytes).build());
        assertLimitFailure(source, new BoundaryLimits().textItems(8).unicode(8).toUnicodeMappings(7)
                .fontDataEntries(2).decodedBytes(bytes).build());
        assertLimitFailure(source, new BoundaryLimits().textItems(8).unicode(8).toUnicodeMappings(7)
                .fontDataEntries(3).decodedBytes(bytes - 1).build());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void toUnicodeNodesAndCodeSpaceDeclarationsShareFontDataBounds() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("cmap-codespace-budget.pdf");
        String content = "BT /F1 12 Tf (A) Tj /F1 12 Tf (A) Tj ET\n";
        String cmap = "begincmap\n/CMapName /FolioT75CodeSpaces def\n/CMapType 2 def\n"
                + "2 begincodespacerange\n<00> <7F>\n<80> <FF>\nendcodespacerange\n"
                + "1 beginbfchar\n<41> <0041>\nendbfchar\nendcmap\n";
        writePdf(source,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                streamObject(content, ""),
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding /ToUnicode 6 0 R >>",
                streamObject(cmap, ""));
        byte[] before = Files.readAllBytes(source);
        PageText page = query(source, new BoundaryLimits().textItems(2).unicode(2).toUnicodeMappings(1)
                .fontDataEntries(3).decodedBytes(content.length() + cmap.length()).build()).getPages().get(0);
        assertEquals("AA", page.getText());
        assertEquals(CharacterMapping.Confidence.EXPLICIT,
                page.getTextItems().get(0).getCharacterMapping().getConfidence());
        assertLimitFailure(source, new BoundaryLimits().textItems(2).unicode(2).toUnicodeMappings(1)
                .fontDataEntries(2).decodedBytes(content.length() + cmap.length()).build());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void unrelatedCMapMetadataDoesNotConsumeMappingEntries() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("cmap-metadata-array.pdf");
        String program = "begincmap /XUID [1 10 25404 9999] def "
                + "1 begincodespacerange <00> <FF> endcodespacerange "
                + "1 beginbfchar <41> <0041> endbfchar endcmap\n";
        writeToUnicodeHeaderFixture(source, true, program, "");
        byte[] before = Files.readAllBytes(source);
        PageText page = query(source, new BoundaryLimits().textItems(1).unicode(1)
                .toUnicodeMappings(1).fontDataEntries(3).decodedBytes(1024).build()).getPages().get(0);
        assertEquals("A", page.getText());
        assertEquals(CharacterMapping.Confidence.EXPLICIT,
                page.getTextItems().get(0).getCharacterMapping().getConfidence());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void inheritedToUnicodeCannotRedefineItsCodespace() throws Exception {
        String parent = "begincmap 1 begincodespacerange <00> <7F> endcodespacerange "
                + "1 beginbfchar <41> <0041> endbfchar endcmap\n";
        String[] localSpaces = {"<80> <FF>", "<00> <7F>"};
        for (int index = 0; index < localSpaces.length; index++) {
            Path source = temporaryFolder.getRoot().toPath().resolve("redefined-cmap-space-" + index + ".pdf");
            String child = "begincmap 1 begincodespacerange " + localSpaces[index]
                    + " endcodespacerange 1 beginbfchar <41> <0041> endbfchar endcmap\n";
            writeToUnicodeInheritanceFixture(source, "BT /F1 12 Tf (A) Tj ET\n", child, parent);
            byte[] before = Files.readAllBytes(source);
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void textualToUnicodeInheritanceResolvesTheDeclaredEmbeddedParent() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("textual-cmap-parent.pdf");
        String parent = "begincmap /CMapName /FolioParent def "
                + "1 begincodespacerange <00> <FF> endcodespacerange "
                + "2 beginbfchar <41> <0041> <42> <0058> endbfchar endcmap\n";
        String child = "begincmap /FolioParent usecmap /CMapName /FolioChild def "
                + "1 beginbfchar <42> <0042> endbfchar endcmap\n";
        writeToUnicodeInheritanceFixture(source, "BT /F1 12 Tf (AB) Tj ET\n", child, parent);
        byte[] before = Files.readAllBytes(source);
        PageText page = query(source, limits()).getPages().get(0);
        assertEquals("AB", page.getText());
        assertEquals(CharacterMapping.Confidence.EXPLICIT,
                page.getTextItems().get(0).getCharacterMapping().getConfidence());
        assertArrayEquals(before, Files.readAllBytes(source));
        String[] invalid = {
            child.replace("/FolioParent usecmap", "/Foreign usecmap"),
            child.replace("/FolioParent usecmap", "/FolioParent usecmap /FolioParent usecmap"),
            child.replace("/FolioParent usecmap", "").replace("endcmap", "/FolioParent usecmap endcmap")
        };
        for (int index = 0; index < invalid.length; index++) {
            Path malformed = temporaryFolder.getRoot().toPath().resolve("bad-textual-cmap-parent-" + index + ".pdf");
            writeToUnicodeInheritanceFixture(malformed, "BT /F1 12 Tf (AB) Tj ET\n", invalid[index], parent);
            assertQueryFailure(malformed, limits());
        }
    }

    @Test(timeout = 30000L)
    public void predefinedToUnicodeInheritanceUsesOnlyBoundedBundledResources() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("predefined-tounicode-parent.pdf");
        String content = "BT /F1 10 Tf 1 0 0 1 20 30 Tm <0001003D> Tj /F1 10 Tf <0001003D> Tj ET\n";
        String child = "begincmap /Adobe-Japan1-UCS2 usecmap /CMapName /FolioOverride def "
                + "1 beginbfchar <0001> <0041> endbfchar endcmap\n";
        writePdf(source,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                streamObject(content, ""),
                "<< /Type /Font /Subtype /Type0 /BaseFont /FolioT75 /Encoding /Identity-H "
                        + "/DescendantFonts [6 0 R] /ToUnicode 7 0 R >>",
                "<< /Type /Font /Subtype /CIDFontType2 /BaseFont /FolioT75 "
                        + "/CIDSystemInfo << /Registry (Folio) /Ordering (T75) /Supplement 0 >> /DW 500 >>",
                streamObject(child, "/UseCMap /Adobe-Japan1-UCS2"));
        byte[] before = Files.readAllBytes(source);
        // Pinned FontBox 3.0.8 resource: 284124 bytes, 23058 declared source
        // mappings and one codespace. The local override still costs one entry.
        long bytes = content.length() + child.length() + 284124 + 7889L;
        PageText page = query(source, new BoundaryLimits().textItems(4).unicode(4)
                .toUnicodeMappings(23059).fontDataEntries(65541).decodedBytes(bytes).build()).getPages().get(0);
        assertEquals("A\u00a5A\u00a5", page.getText());
        assertEquals(5.0, page.getTextItems().get(0).getGeometry().getAdvanceX().doubleValue(), 0.0001);
        assertArrayEquals(new byte[] {0, 0x3d}, page.getTextItems().get(1).getCharacterMapping().getSourceCode());
        assertEquals(CharacterMapping.Confidence.EXPLICIT,
                page.getTextItems().get(1).getCharacterMapping().getConfidence());
        assertLimitFailure(source, new BoundaryLimits().textItems(4).unicode(4)
                .toUnicodeMappings(23058).fontDataEntries(65541).decodedBytes(bytes).build());
        assertLimitFailure(source, new BoundaryLimits().textItems(4).unicode(4)
                .toUnicodeMappings(23059).fontDataEntries(65540).decodedBytes(bytes).build());
        assertLimitFailure(source, new BoundaryLimits().textItems(4).unicode(4)
                .toUnicodeMappings(23059).fontDataEntries(65541).decodedBytes(bytes - 1).build());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void predefinedUnicodeRangesRetainTheirDeclaredCarry() throws Exception {
        String[] names = {"Adobe-Japan1-UCS2", "Adobe-CNS1-UCS2"};
        String[] codes = {"55E655E7", "2F492F4A"};
        String[] expected = {"\u73ff\u7400", "\u6fff\u7000"};
        for (int index = 0; index < names.length; index++) {
            Path source = temporaryFolder.getRoot().toPath().resolve("bundled-unicode-carry-" + index + ".pdf");
            writePdf(source,
                    "<< /Type /Catalog /Pages 2 0 R >>",
                    "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                            + "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                    streamObject("BT /F1 10 Tf <" + codes[index] + "> Tj ET\n", ""),
                    "<< /Type /Font /Subtype /Type0 /BaseFont /FolioT75 /Encoding /Identity-H "
                            + "/DescendantFonts [6 0 R] /ToUnicode 7 0 R >>",
                    "<< /Type /Font /Subtype /CIDFontType2 /BaseFont /FolioT75 "
                            + "/CIDSystemInfo << /Registry (Folio) /Ordering (T75) /Supplement 0 >> /DW 500 >>",
                    streamObject("begincmap /" + names[index] + " usecmap endcmap\n", "/UseCMap /" + names[index]));
            byte[] before = Files.readAllBytes(source);
            PageText page = query(source, new BoundaryLimits().textItems(2).unicode(2)
                    .toUnicodeMappings(40000).fontDataEntries(65541).decodedBytes(400000).build()).getPages().get(0);
            assertEquals(expected[index], page.getText());
            assertEquals(CharacterMapping.Confidence.EXPLICIT,
                    page.getTextItems().get(1).getCharacterMapping().getConfidence());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 30000L)
    public void pdfTwoToUnicodeStreamTypeAndNameMatchTheirPrograms() throws Exception {
        String parent = "begincmap /CMapName /FolioParent def "
                + "1 begincodespacerange <00> <FF> endcodespacerange "
                + "1 beginbfchar <41> <0041> endbfchar endcmap\n";
        String child = "begincmap /FolioParent usecmap /CMapName /FolioChild def endcmap\n";
        String[] headers = {"", "/Type /CMap /CMapName /FolioParent", "/CMapName /Wrong",
            "/CMapName (FolioParent)", "/Type /Font"};
        for (boolean pdfTwo : new boolean[] {true, false}) {
            for (int index = 0; index < headers.length; index++) {
                Path source = temporaryFolder.getRoot().toPath().resolve("cmap-header-" + pdfTwo + "-" + index + ".pdf");
                writePdf(source,
                        "<< /Type /Catalog /Pages 2 0 R " + (pdfTwo ? "/Version /2.0" : "") + " >>",
                        "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                                + "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                        streamObject("BT /F1 12 Tf (A) Tj ET\n", ""),
                        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /ToUnicode 6 0 R >>",
                        streamObject(child, "/Type /CMap /CMapName /FolioChild /UseCMap 7 0 R"),
                        streamObject(parent, headers[index]));
                byte[] before = Files.readAllBytes(source);
                if (pdfTwo && index >= 2) {
                    assertQueryFailure(source, limits());
                } else {
                    assertEquals("A", query(source, limits()).getPages().get(0).getText());
                }
                assertArrayEquals(before, Files.readAllBytes(source));
            }
        }
    }

    @Test(timeout = 60000L)
    public void pdfTwoToUnicodeWritingModeMatchesItsProgram() throws Exception {
        String program = "begincmap /WMode 1 def "
                + "1 begincodespacerange <00> <FF> endcodespacerange "
                + "1 beginbfchar <41> <0041> endbfchar endcmap\n";
        String[] headers = {"/WMode 1", "/WMode 0", "/WMode 2", "/WMode 1.0", "/WMode (1)"};
        for (boolean pdfTwo : new boolean[] {true, false}) {
            for (int index = 0; index < headers.length; index++) {
                Path source = temporaryFolder.getRoot().toPath().resolve("cmap-mode-" + pdfTwo + "-" + index + ".pdf");
                writeToUnicodeHeaderFixture(source, pdfTwo, program, headers[index]);
                byte[] before = Files.readAllBytes(source);
                if (pdfTwo && index != 0) {
                    assertQueryFailure(source, limits());
                } else {
                    assertEquals("A", query(source, limits()).getPages().get(0).getText());
                }
                assertArrayEquals(before, Files.readAllBytes(source));
            }
        }
    }

    @Test(timeout = 60000L)
    public void pdfTwoToUnicodeCharacterCollectionMatchesItsProgram() throws Exception {
        String[] collections = {
            "<< /Registry (Folio\\(A\\)) /Ordering <543735> /Supplement 2 >>",
            "3 dict dup begin /Registry (Folio\\(A\\)) def /Ordering (T75) def /Supplement 2 def end"
        };
        String correct = "<< /Registry (Folio\\(A\\)) /Ordering (T75) /Supplement 2 >>";
        String[] headers = {"", "/CIDSystemInfo " + correct,
            "/CIDSystemInfo << /Registry (Wrong) /Ordering (T75) /Supplement 2 >>",
            "/CIDSystemInfo << /Registry (Folio\\(A\\)) /Ordering (Wrong) /Supplement 2 >>",
            "/CIDSystemInfo << /Registry (Folio\\(A\\)) /Ordering (T75) /Supplement 3 >>",
            "/CIDSystemInfo << /Registry /Folio /Ordering (T75) /Supplement 2 >>",
            "/CIDSystemInfo [" + correct + "]"
        };
        for (boolean pdfTwo : new boolean[] {true, false}) {
            for (int form = 0; form < collections.length; form++) {
                String program = "begincmap /CIDSystemInfo " + collections[form] + " def "
                        + "1 begincodespacerange <00> <FF> endcodespacerange "
                        + "1 beginbfchar <41> <0041> endbfchar endcmap\n";
                for (int index = 0; index < headers.length; index++) {
                    Path source = temporaryFolder.getRoot().toPath().resolve(
                            "cmap-collection-" + pdfTwo + "-" + form + "-" + index + ".pdf");
                    writeToUnicodeHeaderFixture(source, pdfTwo, program, headers[index]);
                    byte[] before = Files.readAllBytes(source);
                    if (pdfTwo && index >= 2) {
                        assertQueryFailure(source, limits());
                    } else {
                        assertEquals("A", query(source, limits()).getPages().get(0).getText());
                    }
                    assertArrayEquals(before, Files.readAllBytes(source));
                }
            }
        }
    }

    private static void writeToUnicodeHeaderFixture(Path source, boolean pdfTwo, String program,
            String headers) throws Exception {
        writePdf(source,
                "<< /Type /Catalog /Pages 2 0 R " + (pdfTwo ? "/Version /2.0" : "") + " >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                streamObject("BT /F1 12 Tf (A) Tj ET\n", ""),
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /ToUnicode 6 0 R >>",
                streamObject("begincmap endcmap\n", "/UseCMap 7 0 R"),
                streamObject(program, headers));
    }

    @Test(timeout = 10000L)
    public void undefinedLocalToUnicodeRangeDoesNotRestoreAnAncestorMapping() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("undefined-local-cmap-range.pdf");
        String parent = "begincmap 1 begincodespacerange <00> <FF> endcodespacerange "
                + "1 beginbfchar <02> <0042> endbfchar endcmap\n";
        String child = "begincmap 1 beginbfrange <01> <02> <00FF> endbfrange endcmap\n";
        writeToUnicodeInheritanceFixture(source, "BT /F1 12 Tf <0102> Tj ET\n", child, parent);
        byte[] before = Files.readAllBytes(source);
        PageText page = query(source, limits()).getPages().get(0);
        assertEquals("\u00ff", page.getText());
        CharacterMapping missing = page.getTextItems().get(1).getCharacterMapping();
        assertEquals(CharacterMapping.Confidence.MISSING, missing.getConfidence());
        assertFalse(missing.getExplicitUnicode().isPresent());
        assertArrayEquals(new byte[] {2}, missing.getSourceCode());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 30000L)
    public void detachedMappingTextRemainsOwnedAfterTheQueryCompletes() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("retained-mapping-text.pdf");
        StringBuilder target = new StringBuilder();
        for (int index = 0; index < 256; index++) {
            target.append("0058");
        }
        String parent = "begincmap 1 begincodespacerange <00> <FF> endcodespacerange "
                + "1 beginbfchar <41> <" + target + "> endbfchar endcmap\n";
        writeToUnicodeInheritanceFixture(source,
                "/Span << /ActualText () >> BDC BT /F1 12 Tf (A) Tj ET EMC\n",
                "begincmap endcmap\n", parent);
        byte[] before = Files.readAllBytes(source);
        WorkflowOutcome<List<TextStructureExtraction>> once = repeatedMappingQueries(source, 1);
        WorkflowOutcome<List<TextStructureExtraction>> repeated = repeatedMappingQueries(source, 64);
        // Each retained Query result contains its own 512-byte declared Unicode
        // value even though ActualText suppresses all aggregate Page Text.
        assertTrue(repeated.getResourceUsage().getPeakOwnedMemoryBytes()
                >= once.getResourceUsage().getPeakOwnedMemoryBytes() + 63L * 512L);
        CharacterMapping mapping = repeated.getResult().get(63).getPages().get(0)
                .getTextItems().get(0).getCharacterMapping();
        assertEquals(256, mapping.getExplicitUnicode().get().length());
        assertEquals("", repeated.getResult().get(63).getPages().get(0).getText());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    private static WorkflowOutcome<List<TextStructureExtraction>> repeatedMappingQueries(Path source, int count)
            throws Exception {
        return new DocumentWorkflow().execute(sourceRequest(source), session -> {
            List<TextStructureExtraction> values = new ArrayList<TextStructureExtraction>();
            for (int index = 0; index < count; index++) {
                values.add(session.query(ExtractTextAndStructure.version1(limits())));
            }
            return values;
        });
    }

    private static void writeToUnicodeInheritanceFixture(Path source, String content, String child, String parent)
            throws Exception {
        writePdf(source,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                streamObject(content, ""),
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /ToUnicode 6 0 R >>",
                streamObject(child, "/UseCMap 7 0 R"), streamObject(parent, ""));
    }

    @Test(timeout = 30000L)
    public void malformedToUnicodeProgramsCannotProduceInferredPrefixes() throws Exception {
        String[] programs = {
            "begincmap beginbfchar <41> <0041> endbfchar endcmap",
            "begincmap 1 begincidchar <41> 1 endcidchar endcmap",
            "begincmap 1 beginbfchar <41> <0041> endbfchar",
            "1 beginbfchar <41> <0041> endbfchar endcmap",
            "begincmap 1 beginbfchar <41> <0041> endbfchar endbfchar endcmap"
        };
        for (int index = 0; index < programs.length; index++) {
            Path source = temporaryFolder.getRoot().toPath().resolve("malformed-cmap-program-" + index + ".pdf");
            writePdf(source,
                    "<< /Type /Catalog /Pages 2 0 R >>",
                    "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                            + "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                    streamObject("BT /F1 12 Tf (A) Tj ET\n", ""),
                    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding /ToUnicode 6 0 R >>",
                    streamObject(programs[index], ""));
            byte[] before = Files.readAllBytes(source);
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
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
                "<00> <FF> <00FF>",
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

    // Each case starts independent Workflows; include their cumulative Worker startup time.
    @Test(timeout = 60000L)
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

        // Five metric entries, two ToUnicode entries and 65,538 Identity-H entries.
        assertEquals("AB", query(source, cidFontLimits(65545))
                .getPages().get(0).getText());
        assertLimitFailure(source, cidFontLimits(65544));
    }

    @Test(timeout = 30000L)
    public void identityEncodingsChargeTheirActualProgramsAndKeepSourceCodeMetrics() throws Exception {
        String content = "BT /F1 10 Tf 7 Tw 1 0 0 1 20 30 Tm <00410020> Tj /F1 10 Tf <0041> Tj ET\n";
        for (boolean vertical : new boolean[] {false, true}) {
            Path source = temporaryFolder.getRoot().toPath().resolve("identity-cmap-" + vertical + ".pdf");
            writePdf(source,
                    "<< /Type /Catalog /Pages 2 0 R >>",
                    "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                            + "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                    streamObject(content, ""),
                    "<< /Type /Font /Subtype /Type0 /BaseFont /FolioT75 /Encoding /Identity-"
                            + (vertical ? "V" : "H") + " /DescendantFonts [6 0 R] >>",
                    "<< /Type /Font /Subtype /CIDFontType2 /BaseFont /FolioT75 "
                            + "/CIDSystemInfo << /Registry (Folio) /Ordering (T75) /Supplement 0 >> /DW 500 >>");
            byte[] before = Files.readAllBytes(source);
            int entries = vertical ? 65539 : 65538;
            long decoded = content.length() + 7889 + (vertical ? 2688 : 0);
            PageText page = query(source, new BoundaryLimits().textItems(3).unicode(0)
                    .fontDataEntries(entries).decodedBytes(decoded).build()).getPages().get(0);
            assertEquals("", page.getText());
            assertEquals(3, page.getTextItems().size());
            for (int index = 0; index < 3; index++) {
                TextItem item = page.getTextItems().get(index);
                assertEquals(CharacterMapping.Confidence.MISSING, item.getCharacterMapping().getConfidence());
                assertArrayEquals(new byte[] {0, (byte) (index == 1 ? 32 : 65)},
                        item.getCharacterMapping().getSourceCode());
                assertEquals(vertical ? 0 : 5, item.getGeometry().getAdvanceX().doubleValue(), 0.0001);
                assertEquals(vertical ? -10 : 0, item.getGeometry().getAdvanceY().doubleValue(), 0.0001);
                assertEquals(vertical ? 17.5 : 20 + 5 * index, item.getGeometry().getE().doubleValue(), 0.0001);
                assertEquals(vertical ? 21.2 - 10 * index : 30, item.getGeometry().getF().doubleValue(), 0.0001);
            }
            assertLimitFailure(source, new BoundaryLimits().textItems(3).unicode(0)
                    .fontDataEntries(entries - 1).decodedBytes(decoded).build());
            assertLimitFailure(source, new BoundaryLimits().textItems(3).unicode(0)
                    .fontDataEntries(entries).decodedBytes(decoded - 1).build());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void embeddedEncodingUsesExactCodeLengthsAndCidDeclaredWidths() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("embedded-encoding.pdf");
        String content = "BT /F1 10 Tf 7 Tw 1 0 0 1 20 30 Tm <2021802020810001> Tj ET\n";
        String encoding = "begincmap /CMapName /FolioEncoding def /CMapType 1 def /WMode 0 def "
                + "/CIDSystemInfo << /Registry (Folio) /Ordering (T75) /Supplement 0 >> def "
                + "3 begincodespacerange <20> <21> <8000> <80FF> <810000> <81FFFF> endcodespacerange "
                + "2 begincidchar <20> 7 <8020> 0 endcidchar "
                + "1 begincidrange <21> <21> 32 endcidrange "
                + "1 begincidchar <810001> 8 endcidchar endcmap\n";
        writePdf(source,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                streamObject(content, ""),
                "<< /Type /Font /Subtype /Type0 /BaseFont /FolioT75 /Encoding 7 0 R /DescendantFonts [6 0 R] >>",
                "<< /Type /Font /Subtype /CIDFontType2 /BaseFont /FolioT75 "
                        + "/CIDSystemInfo << /Registry (Folio) /Ordering (T75) /Supplement 0 >> "
                        + "/DW 500 /W [0 [300] 7 [700 800] 32 [900]] >>",
                streamObject(encoding, "/Type /CMap /CMapName /FolioEncoding /WMode 0 "
                        + "/CIDSystemInfo << /Registry (Folio) /Ordering (T75) /Supplement 0 >>"));
        byte[] before = Files.readAllBytes(source);
        PageText page = query(source, new BoundaryLimits().textItems(5).unicode(0)
                .fontDataEntries(18).decodedBytes(content.length() + encoding.length()).build()).getPages().get(0);
        byte[][] sources = {{32}, {33}, {(byte) 0x80, 32}, {32}, {(byte) 0x81, 0, 1}};
        double[] positions = {20, 34, 43, 46, 60};
        double[] advances = {7, 9, 3, 7, 8};
        assertEquals(5, page.getTextItems().size());
        for (int index = 0; index < 5; index++) {
            TextItem item = page.getTextItems().get(index);
            assertArrayEquals(sources[index], item.getCharacterMapping().getSourceCode());
            assertEquals(CharacterMapping.Confidence.MISSING, item.getCharacterMapping().getConfidence());
            assertEquals(positions[index], item.getGeometry().getE().doubleValue(), 0.0001);
            assertEquals(advances[index], item.getGeometry().getAdvanceX().doubleValue(), 0.0001);
        }
        assertLimitFailure(source, new BoundaryLimits().textItems(5).unicode(0)
                .fontDataEntries(17).decodedBytes(content.length() + encoding.length()).build());
        assertLimitFailure(source, new BoundaryLimits().textItems(5).unicode(0)
                .fontDataEntries(18).decodedBytes(content.length() + encoding.length() - 1).build());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 30000L)
    public void predefinedEncodingUsesBoundedProgramsAndVerticalOverrides() throws Exception {
        String[] names = {"83pv-RKSJ-H", "90ms-RKSJ-H", "90ms-RKSJ-V"};
        int[] entries = {8028, 7920, 8031};
        int[] resourceBytes = {7149, 6139, 10437};
        String content = "BT /F1 10 Tf 1 0 0 1 20 30 Tm <418141> Tj ET\n";
        String unicode = "begincmap 2 begincodespacerange <00> <7F> <8000> <FFFF> endcodespacerange "
                + "2 beginbfchar <41> <0041> <8141> <3001> endbfchar endcmap\n";
        for (int index = 0; index < names.length; index++) {
            boolean vertical = index == 2;
            Path source = temporaryFolder.getRoot().toPath().resolve("predefined-encoding-" + names[index] + ".pdf");
            writePdf(source,
                    "<< /Type /Catalog /Pages 2 0 R >>",
                    "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                            + "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                    streamObject(content, ""),
                    "<< /Type /Font /Subtype /Type0 /BaseFont /FolioT75 /Encoding /" + names[index]
                            + " /DescendantFonts [6 0 R] /ToUnicode 7 0 R >>",
                    "<< /Type /Font /Subtype /CIDFontType2 /BaseFont /FolioT75 "
                            + "/CIDSystemInfo << /Registry (Adobe) /Ordering (Japan1) /Supplement 7 >> "
                            + "/DW 1000 /W [34 [400] 264 [500] 634 [600] 7887 [800]] "
                            + (vertical ? "/W2 [7887 [-900 400 700]] " : "") + ">>",
                    streamObject(unicode, ""));
            byte[] before = Files.readAllBytes(source);
            int cost = entries[index] + 12 + 3 + (vertical ? 5 : 0);
            long bytes = content.length() + unicode.length() + resourceBytes[index];
            PageText page = query(source, new BoundaryLimits().textItems(2).unicode(2)
                    .toUnicodeMappings(2).fontDataEntries(cost).decodedBytes(bytes).build()).getPages().get(0);
            assertEquals("A\u3001", page.getText());
            assertArrayEquals(new byte[] {65}, page.getTextItems().get(0).getCharacterMapping().getSourceCode());
            assertArrayEquals(new byte[] {(byte) 0x81, 65}, page.getTextItems().get(1).getCharacterMapping().getSourceCode());
            assertEquals(vertical ? -10 : (index == 0 ? 4 : 5),
                    (vertical ? page.getTextItems().get(0).getGeometry().getAdvanceY()
                            : page.getTextItems().get(0).getGeometry().getAdvanceX()).doubleValue(), 0.0001);
            assertEquals(vertical ? -9 : 6,
                    (vertical ? page.getTextItems().get(1).getGeometry().getAdvanceY()
                            : page.getTextItems().get(1).getGeometry().getAdvanceX()).doubleValue(), 0.0001);
            assertEquals(vertical ? 16 : (index == 0 ? 24 : 25),
                    page.getTextItems().get(1).getGeometry().getE().doubleValue(), 0.0001);
            assertEquals(vertical ? 13 : 30, page.getTextItems().get(1).getGeometry().getF().doubleValue(), 0.0001);
            assertLimitFailure(source, new BoundaryLimits().textItems(2).unicode(2)
                    .toUnicodeMappings(2).fontDataEntries(cost - 1).decodedBytes(bytes).build());
            assertLimitFailure(source, new BoundaryLimits().textItems(2).unicode(2)
                    .toUnicodeMappings(2).fontDataEntries(cost).decodedBytes(bytes - 1).build());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 180000L)
    public void everyStandardPredefinedEncodingAcceptsItsIndependentlyCountedBudget() throws Exception {
        // Original literal counts from the pinned resource archive, independent
        // of the product parser. Source mappings and licenses remain in FontBox.
        String[] resources = {
            "83pv-RKSJ-H,Japan1,1,8028,7149",
            "90ms-RKSJ-H,Japan1,2,7920,6139",
            "90ms-RKSJ-V,Japan1,2,8031,10437",
            "90msp-RKSJ-H,Japan1,2,7920,6070",
            "90msp-RKSJ-V,Japan1,2,8031,10351",
            "90pv-RKSJ-H,Japan1,1,7393,7884",
            "Add-RKSJ-H,Japan1,1,7379,15107",
            "Add-RKSJ-V,Japan1,1,7466,18999",
            "B5pc-H,CNS1,0,13628,7616",
            "B5pc-V,CNS1,0,13649,10621",
            "CNS-EUC-H,CNS1,0,19951,12315",
            "CNS-EUC-V,CNS1,0,19951,13347",
            "ETen-B5-H,CNS1,0,13996,7772",
            "ETen-B5-V,CNS1,0,14019,10815",
            "ETenms-B5-H,CNS1,0,14092,10570",
            "ETenms-B5-V,CNS1,0,14123,13680",
            "EUC-H,Japan1,1,7074,5144",
            "EUC-V,Japan1,1,7128,8436",
            "Ext-RKSJ-H,Japan1,2,7813,15676",
            "Ext-RKSJ-V,Japan1,2,7894,19226",
            "GB-EUC-H,GB1,0,7737,4496",
            "GB-EUC-V,GB1,0,7774,7665",
            "GBK-EUC-H,GB1,2,22153,83171",
            "GBK-EUC-V,GB1,2,22190,86329",
            "GBK2K-H,GB1,5,30260,90995",
            "GBK2K-V,GB1,5,30318,94546",
            "GBKp-EUC-H,GB1,2,22153,83152",
            "GBKp-EUC-V,GB1,2,22190,86316",
            "GBpc-EUC-H,GB1,0,7742,4524",
            "GBpc-EUC-V,GB1,0,7779,7705",
            "H,Japan1,1,6881,5010",
            "HKscs-B5-H,CNS1,6,18705,23322",
            "HKscs-B5-V,CNS1,6,18728,26352",
            "Identity-H,Identity,0,65538,7889",
            "Identity-V,Identity,0,65539,10577",
            "KSC-EUC-H,Korea1,0,8353,11797",
            "KSC-EUC-V,Korea1,0,8393,14902",
            "KSCms-UHC-H,Korea1,1,17175,16009",
            "KSCms-UHC-HW-H,Korea1,1,17175,16005",
            "KSCms-UHC-HW-V,Korea1,1,17215,19123",
            "KSCms-UHC-V,Korea1,1,17215,19128",
            "KSCpc-EUC-H,Korea1,0,9499,12624",
            "UniCNS-UCS2-H,CNS1,3,18320,326418",
            "UniCNS-UCS2-V,CNS1,3,18341,329458",
            "UniCNS-UTF16-H,CNS1,6,23712,252742",
            "UniCNS-UTF16-V,CNS1,6,23733,255771",
            "UniGB-UCS2-H,GB1,4,28875,274452",
            "UniGB-UCS2-V,GB1,4,28912,277688",
            "UniGB-UTF16-H,GB1,5,30247,199956",
            "UniGB-UTF16-V,GB1,5,30283,203086",
            "UniJIS-UCS2-H,Japan1,4,9807,168666",
            "UniJIS-UCS2-HW-H,Japan1,4,9904,171555",
            "UniJIS-UCS2-HW-V,Japan1,4,10155,175326",
            "UniJIS-UCS2-V,Japan1,4,10059,175244",
            "UniJIS-UTF16-H,Japan1,6,15812,187728",
            "UniJIS-UTF16-V,Japan1,6,16101,193490",
            "UniKS-UCS2-H,Korea1,1,17361,166078",
            "UniKS-UCS2-V,Korea1,1,17400,169223",
            "UniKS-UTF16-H,Korea1,1,17521,122974",
            "UniKS-UTF16-V,Korea1,1,17560,126073",
            "V,Japan1,1,6935,8278"
        };
        String content = "BT /F1 12 Tf ET\n";
        for (String row : resources) {
            String[] data = row.split(",");
            Path source = temporaryFolder.getRoot().toPath().resolve("standard-encoding-" + data[0] + ".pdf");
            writePdf(source,
                    "<< /Type /Catalog /Pages 2 0 R >>",
                    "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                            + "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                    streamObject(content, ""),
                    "<< /Type /Font /Subtype /Type0 /BaseFont /FolioT75 /Encoding /" + data[0]
                            + " /DescendantFonts [6 0 R] >>",
                    "<< /Type /Font /Subtype /CIDFontType2 /BaseFont /FolioT75 "
                            + "/CIDSystemInfo << /Registry (Adobe) /Ordering (" + data[1]
                            + ") /Supplement " + data[2] + " >> /DW 500 >>");
            byte[] before = Files.readAllBytes(source);
            PageText page = query(source, new BoundaryLimits().textItems(0).unicode(0)
                    .fontDataEntries(Integer.parseInt(data[3]))
                    .decodedBytes(content.length() + Long.parseLong(data[4])).build()).getPages().get(0);
            assertEquals(data[0], "", page.getText());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 60000L)
    public void cmapMappingsStayWithinTheirDeclaredOrderedCodespaces() throws Exception {
        String codespace = "1 begincodespacerange <20> <20> endcodespacerange ";
        for (boolean unicode : new boolean[] {false, true}) {
            String valid = unicode ? "1 beginbfchar <20> <0041> endbfchar "
                    : "1 begincidchar <20> 1 endcidchar ";
            String[] invalid = unicode
                    ? new String[] {"1 beginbfchar <21> <0042> endbfchar ",
                            "1 beginbfrange <20> <21> <0041> endbfrange "}
                    : new String[] {"1 begincidchar <21> 2 endcidchar ",
                            "1 begincidrange <20> <21> 1 endcidrange ",
                            "1 beginnotdefchar <21> 2 endnotdefchar ",
                            "1 beginnotdefrange <20> <21> 1 endnotdefrange "};
            List<String> programs = new ArrayList<String>();
            for (String mapping : invalid) {
                programs.add(codespace + valid + mapping);
            }
            programs.add(valid + codespace);
            programs.add("2 begincodespacerange <20> <21> <21> <22> endcodespacerange " + valid);
            for (int index = 0; index <= programs.size(); index++) {
                boolean inherited = index == programs.size();
                Path source = temporaryFolder.getRoot().toPath().resolve("cmap-domain-" + unicode + "-" + index + ".pdf");
                String metadata = "/CMapType " + (unicode ? "2" : "1") + " def "
                        + "/CIDSystemInfo << /Registry (Folio) /Ordering (T75) /Supplement 0 >> def ";
                String root = "begincmap /CMapName /Root def " + metadata
                        + (inherited ? invalid[0] : programs.get(index)) + "endcmap\n";
                String parent = "begincmap /CMapName /Base def " + metadata + codespace + valid + "endcmap\n";
                writePdf(source,
                        "<< /Type /Catalog /Pages 2 0 R >>",
                        "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                                + "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                        streamObject("BT /F1 12 Tf <20> Tj ET\n", ""),
                        unicode ? "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /ToUnicode 6 0 R >>"
                                : "<< /Type /Font /Subtype /Type0 /BaseFont /FolioT75 "
                                        + "/Encoding 6 0 R /DescendantFonts [8 0 R] >>",
                        streamObject(root, "/Type /CMap /CMapName /Root "
                                + "/CIDSystemInfo << /Registry (Folio) /Ordering (T75) /Supplement 0 >> "
                                + (inherited ? "/UseCMap 7 0 R" : "")),
                        streamObject(parent, "/Type /CMap /CMapName /Base "
                                + "/CIDSystemInfo << /Registry (Folio) /Ordering (T75) /Supplement 0 >>"),
                        "<< /Type /Font /Subtype /CIDFontType2 /BaseFont /FolioT75 "
                                + "/CIDSystemInfo << /Registry (Folio) /Ordering (T75) /Supplement 0 >> /DW 500 >>");
                byte[] before = Files.readAllBytes(source);
                assertQueryFailure(source, limits());
                assertArrayEquals(before, Files.readAllBytes(source));
            }
        }
    }

    @Test(timeout = 30000L)
    public void sharedCidMetricsChargeEverySelectedFontConstruction() throws Exception {
        String encoding = "begincmap /CMapName /SharedEncoding def /CMapType 1 def "
                + "/CIDSystemInfo << /Registry (Folio) /Ordering (T75) /Supplement 0 >> def "
                + "1 begincodespacerange <41> <41> endcodespacerange "
                + "1 begincidchar <41> 65 endcidchar endcmap\n";
        for (boolean sameFont : new boolean[] {false, true}) {
            Path source = temporaryFolder.getRoot().toPath().resolve("shared-cid-metrics-" + sameFont + ".pdf");
            String content = "BT /F1 10 Tf 1 0 0 1 20 30 Tm <41> Tj /F2 10 Tf <41> Tj ET\n";
            String font = "<< /Type /Font /Subtype /Type0 /BaseFont /FolioT75 "
                    + "/Encoding 8 0 R /DescendantFonts [7 0 R] >>";
            writePdf(source,
                    "<< /Type /Catalog /Pages 2 0 R >>",
                    "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                            + "/Resources << /Font << /F1 5 0 R /F2 " + (sameFont ? "5" : "6")
                            + " 0 R >> >> /Contents 4 0 R >>",
                    streamObject(content, ""), font, font,
                    "<< /Type /Font /Subtype /CIDFontType2 /BaseFont /FolioT75 "
                            + "/CIDSystemInfo << /Registry (Folio) /Ordering (T75) /Supplement 0 >> "
                            + "/DW 500 /W [0 4095 700] >>",
                    streamObject(encoding, "/Type /CMap /CMapName /SharedEncoding "
                            + "/CIDSystemInfo << /Registry (Folio) /Ordering (T75) /Supplement 0 >>"));
            byte[] before = Files.readAllBytes(source);
            int count = sameFont ? 1 : 2;
            int cost = 4102 * count; // 4099 W materializations + 3 Encoding entries per font.
            long bytes = content.length() + count * encoding.length();
            PageText page = query(source, new BoundaryLimits().textItems(2).unicode(0)
                    .fontDataEntries(cost).decodedBytes(bytes).build()).getPages().get(0);
            assertEquals(2, page.getTextItems().size());
            assertEquals(20, page.getTextItems().get(0).getGeometry().getE().doubleValue(), 0.0001);
            assertEquals(27, page.getTextItems().get(1).getGeometry().getE().doubleValue(), 0.0001);
            for (TextItem item : page.getTextItems()) {
                assertArrayEquals(new byte[] {65}, item.getCharacterMapping().getSourceCode());
                assertEquals(7, item.getGeometry().getAdvanceX().doubleValue(), 0.0001);
            }
            assertLimitFailure(source, new BoundaryLimits().textItems(2).unicode(0)
                    .fontDataEntries(cost - 1).decodedBytes(bytes).build());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 30000L)
    public void inheritedEncodingResolvesCidZeroNotdefAndBothDescendantKinds() throws Exception {
        String content = "BT /F1 10 Tf 1 0 0 1 20 30 Tm <2021> Tj /GS gs <22232425> Tj ET\n";
        for (boolean vertical : new boolean[] {false, true}) {
            for (String kind : new String[] {"CIDFontType0", "CIDFontType2"}) {
                Path source = temporaryFolder.getRoot().toPath().resolve("inherited-encoding-" + vertical + "-" + kind + ".pdf");
                String metadata = "/CMapType 1 def /WMode " + (vertical ? "1" : "0") + " def "
                        + "/CIDSystemInfo << /Registry (Folio) /Ordering (T75) /Supplement 0 >> def ";
                String parent = "begincmap /CMapName /Base def " + metadata
                        + "1 begincodespacerange <20> <25> endcodespacerange "
                        + "1 begincidrange <20> <22> 5 endcidrange "
                        + "1 beginnotdefrange <23> <24> 12 endnotdefrange endcmap\n";
                String child = "begincmap /Base usecmap /CMapName /Child def " + metadata
                        + "1 begincidchar <20> 0 endcidchar "
                        + "1 begincidrange <21> <22> 8 endcidrange "
                        + "1 beginnotdefchar <24> 13 endnotdefchar endcmap\n";
                String headers = "/Type /CMap /WMode " + (vertical ? "1" : "0")
                        + " /CIDSystemInfo << /Registry (Folio) /Ordering (T75) /Supplement 0 >> ";
                writePdf(source,
                        "<< /Type /Catalog /Pages 2 0 R >>",
                        "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                                + "/Resources << /Font << /F1 5 0 R >> "
                                + "/ExtGState << /GS << /Font [5 0 R 10] >> >> >> /Contents 4 0 R >>",
                        streamObject(content, ""),
                        "<< /Type /Font /Subtype /Type0 /BaseFont /FolioT75 /Encoding 7 0 R /DescendantFonts [6 0 R] >>",
                        "<< /Type /Font /Subtype /" + kind + " /BaseFont /FolioT75 "
                                + "/CIDSystemInfo << /Registry (Folio) /Ordering (T75) /Supplement 0 >> "
                                + "/DW 500 /W [0 [300] 5 [500] 8 [800 900] 12 [400 500]] "
                                + (vertical ? "/W2 [0 [-400 150 700] 8 [-600 400 800 -700 450 850] "
                                        + "12 [-800 200 750 -900 250 760]] " : "") + ">>",
                        streamObject(child, headers + "/CMapName /Child /UseCMap 8 0 R"),
                        streamObject(parent, headers + "/CMapName /Base"));
                byte[] before = Files.readAllBytes(source);
                int cost = vertical ? 47 : 26;
                long bytes = content.length() + child.length() + parent.length();
                PageText page = query(source, new BoundaryLimits().textItems(6).unicode(0)
                        .fontDataEntries(cost).decodedBytes(bytes).build()).getPages().get(0);
                double[] x = vertical ? new double[] {18.5, 16, 15.5, 18, 17.5, 18.5}
                        : new double[] {20, 23, 31, 40, 44, 49};
                double[] y = vertical ? new double[] {23, 18, 11.5, 5.5, -2.6, -11}
                        : new double[] {30, 30, 30, 30, 30, 30};
                double[] advance = vertical ? new double[] {-4, -6, -7, -8, -9, -4}
                        : new double[] {3, 8, 9, 4, 5, 3};
                assertEquals("", page.getText());
                assertEquals(6, page.getTextItems().size());
                for (int index = 0; index < 6; index++) {
                    TextItem item = page.getTextItems().get(index);
                    assertArrayEquals(new byte[] {(byte) (32 + index)}, item.getCharacterMapping().getSourceCode());
                    assertEquals(CharacterMapping.Confidence.MISSING, item.getCharacterMapping().getConfidence());
                    assertEquals(x[index], item.getGeometry().getE().doubleValue(), 0.0001);
                    assertEquals(y[index], item.getGeometry().getF().doubleValue(), 0.0001);
                    assertEquals(advance[index], (vertical ? item.getGeometry().getAdvanceY()
                            : item.getGeometry().getAdvanceX()).doubleValue(), 0.0001);
                }
                assertLimitFailure(source, new BoundaryLimits().textItems(6).unicode(0)
                        .fontDataEntries(cost - 1).decodedBytes(bytes).build());
                assertLimitFailure(source, new BoundaryLimits().textItems(6).unicode(0)
                        .fontDataEntries(cost).decodedBytes(bytes - 1).build());
                assertArrayEquals(before, Files.readAllBytes(source));
            }
        }
    }

    @Test(timeout = 10000L)
    public void fourByteUnicodeRangesUseUnsignedSourceOrdering() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("unsigned-source-cmap.pdf");
        String content = "BT /F1 10 Tf <7FFFFFFF80000000> Tj ET\n";
        String encoding = "begincmap /CMapName /UnsignedSource def /CMapType 1 def "
                + "/CIDSystemInfo << /Registry (Folio) /Ordering (T75) /Supplement 0 >> def "
                + "1 begincodespacerange <7F000000> <80FFFFFF> endcodespacerange "
                + "1 begincidrange <7FFFFFFF> <80000000> 7 endcidrange endcmap\n";
        String unicode = "begincmap 1 begincodespacerange <7F000000> <80FFFFFF> endcodespacerange "
                + "1 beginbfrange <7FFFFFFF> <80000000> <0041> endbfrange endcmap\n";
        writePdf(source,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                streamObject(content, ""),
                "<< /Type /Font /Subtype /Type0 /BaseFont /FolioT75 /Encoding 7 0 R "
                        + "/DescendantFonts [6 0 R] /ToUnicode 8 0 R >>",
                "<< /Type /Font /Subtype /CIDFontType2 /BaseFont /FolioT75 "
                        + "/CIDSystemInfo << /Registry (Folio) /Ordering (T75) /Supplement 0 >> "
                        + "/DW 500 /W [7 [700 800]] >>",
                streamObject(encoding, "/Type /CMap /CMapName /UnsignedSource "
                        + "/CIDSystemInfo << /Registry (Folio) /Ordering (T75) /Supplement 0 >>"),
                streamObject(unicode, ""));
        byte[] before = Files.readAllBytes(source);
        PageText page = query(source, new BoundaryLimits().textItems(2).unicode(2).toUnicodeMappings(2)
                .fontDataEntries(10).decodedBytes(content.length() + encoding.length() + unicode.length())
                .build()).getPages().get(0);
        assertEquals("AB", page.getText());
        assertArrayEquals(new byte[] {0x7f, -1, -1, -1}, page.getTextItems().get(0).getCharacterMapping().getSourceCode());
        assertArrayEquals(new byte[] {(byte) 0x80, 0, 0, 0}, page.getTextItems().get(1).getCharacterMapping().getSourceCode());
        assertEquals(7, page.getTextItems().get(0).getGeometry().getAdvanceX().doubleValue(), 0.0001);
        assertEquals(8, page.getTextItems().get(1).getGeometry().getAdvanceX().doubleValue(), 0.0001);
        assertArrayEquals(before, Files.readAllBytes(source));
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

        // Twelve metric entries, two ToUnicode entries and 65,538 Identity-H entries.
        assertEquals("AB", query(source, cidFontLimits(65552))
                .getPages().get(0).getText());
        assertLimitFailure(source, cidFontLimits(65551));
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
    public void type0EncodingsRequireKnownProgramsWithinCallerBounds()
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
        assertLimitFailure(predefined, limits());
        assertLimitFailure(predefined, limits());
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

        WorkflowRequest request = requestBuilder()
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

        WorkflowRequest request = requestBuilder()
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
    public void type3GlyphProgramsRespectDecodedByteLimits()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve(
                "type3-glyph-program.pdf");
        writeType3FontFixture(source, 256 * 1024);
        byte[] before = Files.readAllBytes(source);

        assertLimitFailure(source, limits());
        assertLimitFailure(source, limits());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void type3DeclaredMetricsExtractWithoutExecutingGlyphPrograms() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("type3-declared-metrics.pdf");
        writeType3MetricFixture(source, TYPE3_FONT, TYPE3_CONTENT, TYPE3_GLYPH);
        byte[] before = Files.readAllBytes(source);
        // One width, two Differences items, two CharProcs aliases; the shared stream decodes once.
        ExtractionLimits exact = new BoundaryLimits().textItems(3).unicode(3).fontDataEntries(5)
                .decodedBytes(TYPE3_CONTENT.length() + TYPE3_GLYPH.length()).build();
        PageText page = query(source, exact).getPages().get(0);
        assertEquals("AAA", page.getText());
        assertEquals(3, page.getTextItems().size());
        double[] origins = {20, 32, 44};
        for (int index = 0; index < origins.length; index++) {
            TextItem item = page.getTextItems().get(index);
            assertEquals(CharacterMapping.Confidence.INFERRED, item.getCharacterMapping().getConfidence());
            assertArrayEquals(new byte[] {65}, item.getCharacterMapping().getSourceCode());
            assertEquals(10, item.getGeometry().getA().doubleValue(), 0.0001);
            assertEquals(10, item.getGeometry().getD().doubleValue(), 0.0001);
            assertEquals(origins[index], item.getGeometry().getE().doubleValue(), 0.0001);
            assertEquals(30, item.getGeometry().getF().doubleValue(), 0.0001);
            assertEquals(12, item.getGeometry().getAdvanceX().doubleValue(), 0.0001);
            assertEquals(0, item.getGeometry().getAdvanceY().doubleValue(), 0.0001);
        }
        assertLimitFailure(source, new BoundaryLimits().textItems(3).unicode(3).fontDataEntries(4)
                .decodedBytes(TYPE3_CONTENT.length() + TYPE3_GLYPH.length()).build());
        assertLimitFailure(source, new BoundaryLimits().textItems(3).unicode(3).fontDataEntries(5)
                .decodedBytes(TYPE3_CONTENT.length() + TYPE3_GLYPH.length() - 1).build());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void type3RotatedMetricsStayHorizontalAndUndefinedWidthsStayZero() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("type3-horizontal-declared-widths.pdf");
        String font = TYPE3_FONT.replace(".002 0 0 .003", ".002 .003 0 .001")
                .replace("[65 /A]", "[65 /A /B]").replace("/Alias 6 0 R", "/B 6 0 R");
        for (boolean graphicsStateFont : new boolean[] {false, true}) {
            String content = TYPE3_CONTENT.replace("(AAA)", "(ABA)");
            if (graphicsStateFont) {
                content = content.replace("/F1 10 Tf", "/G1 gs");
            }
            writeType3MetricFixture(source, font, content, TYPE3_GLYPH);
            byte[] before = Files.readAllBytes(source);
            PageText page = query(source, limits()).getPages().get(0);
            assertEquals("ABA", page.getText());
            double[] origins = {20, 32, 32};
            double[] advances = {12, 0, 12};
            for (int index = 0; index < 3; index++) {
                TextItem item = page.getTextItems().get(index);
                assertEquals(origins[index], item.getGeometry().getE().doubleValue(), 0.0001);
                assertEquals(30, item.getGeometry().getF().doubleValue(), 0.0001);
                assertEquals(advances[index], item.getGeometry().getAdvanceX().doubleValue(), 0.0001);
                assertEquals(0, item.getGeometry().getAdvanceY().doubleValue(), 0.0001);
            }
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void type3GlyphMetricsMustAgreeWithTheDeclaredWidth() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("type3-width-agreement.pdf");
        writeType3MetricFixture(source, TYPE3_FONT, TYPE3_CONTENT, "600 0 0 0 500 700 d1 0 0 500 700 re f\n");
        assertEquals("AAA", query(source, limits()).getPages().get(0).getText());
        writeType3MetricFixture(source, TYPE3_FONT, TYPE3_CONTENT, TYPE3_GLYPH.replace("600 0 d0", "601 0 d0"));
        byte[] before = Files.readAllBytes(source);
        assertQueryFailure(source, limits());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void type3SharedGlyphStreamsAreChargedOnceAcrossDistinctSelectedFonts() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("type3-shared-font-program.pdf");
        String content = "BT /F1 10 Tf (A) Tj /F2 10 Tf (A) Tj /F1 10 Tf (A) Tj ET\n";
        String glyph = "600 0 d0 BT /Ink 10 Tf (Hidden) Tj ET\n";
        String font = TYPE3_FONT.replace("/Resources << >>", "/Resources << /Font << /Ink "
                + "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >> >> >>");
        writePdf(source,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 5 0 R /F2 7 0 R >> >> /Contents 4 0 R >>",
                streamObject(content, ""), font, streamObject(glyph, ""), font);
        byte[] before = Files.readAllBytes(source);
        PageText page = query(source, new BoundaryLimits().textItems(3).unicode(3).fontDataEntries(10)
                .decodedBytes(content.length() + glyph.length()).build()).getPages().get(0);
        assertEquals("AAA", page.getText());
        assertEquals(3, page.getTextItems().size());
        assertTrue(page.getMarkedContentSequences().isEmpty());
        assertLimitFailure(source, new BoundaryLimits().textItems(3).unicode(3).fontDataEntries(9)
                .decodedBytes(content.length() + glyph.length()).build());
        assertLimitFailure(source, new BoundaryLimits().textItems(3).unicode(3).fontDataEntries(10)
                .decodedBytes(content.length() + glyph.length() - 1).build());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 30000L)
    public void malformedType3MetricsAndProgramsFailWithoutChangingTheSource() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("malformed-type3-font.pdf");
        String[] fonts = {
            TYPE3_FONT.replace("[0 0 600 700]", "[0 0 600]"),
            TYPE3_FONT.replace("[.002 0 0 .003 0 0]", "[.002 0 0 /Wrong 0 0]"),
            TYPE3_FONT.replace("/FirstChar 65", ""),
            TYPE3_FONT.replace("/Widths [600]", ""),
            TYPE3_FONT.replace("/Widths [600]", "/Widths [600 600]"),
            TYPE3_FONT.replace("/A 6 0 R", "/A 42")
        };
        for (String font : fonts) {
            writeType3MetricFixture(source, font, TYPE3_CONTENT, TYPE3_GLYPH);
            byte[] before = Files.readAllBytes(source);
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
        for (String glyph : new String[] {"q 600 0 d0 Q\n", "600 1 d0\n", "600 0 d0 (unfinished"}) {
            writeType3MetricFixture(source, TYPE3_FONT, TYPE3_CONTENT, glyph);
            byte[] before = Files.readAllBytes(source);
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
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
            "BT BT /F1 12 Tf (A) Tj ET ET"
        };
        for (int index = 0; index < malformedOperators.length; index++) {
            Path source = temporaryFolder.getRoot().toPath().resolve(
                    "unbalanced-page-text-operator-" + index + ".pdf");
            writeSimpleTextFixture(
                    source, malformedOperators[index] + "\n");
            byte[] before = Files.readAllBytes(source);

            assertQueryFailure(source, limits());
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void unbalancedGraphicsAndPositioningStateCannotPublishPrefix()
            throws Exception {
        String[] malformedOperators = {
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
    }

    @Test(timeout = 10000L)
    public void unbalancedFormOperatorStateCannotPublishPrefix()
            throws Exception {
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

    // Each case starts independent Workflows; include their cumulative Worker startup time.
    @Test(timeout = 60000L)
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

    // Each case starts independent Workflows; include their cumulative Worker startup time.
    @Test(timeout = 60000L)
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
            "/Type (XObject) /Subtype /Form /BBox [0 0 100 100] /Resources << >>",
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

    @Test(timeout = 15000L)
    public void optionalFormTypePreservesExecutedAndReferencedFormOutcomes() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("optional-form-type.pdf");
        for (String type : new String[] {"              ", "/Type null    "}) {
            for (int kind = 0; kind < 4; kind++) {
                if (kind == 0) {
                    createNestedFormFixture(source);
                } else if (kind == 1) {
                    writeTwoPageObjectReferenceFixture(source, true);
                } else if (kind == 2) {
                    writeUnexecutedWholeObjectFixture(source, 1);
                } else {
                    writeAppearanceMcrFixture(source, "/Span <</MCID 0>> BDC EMC\n");
                }
                String original = new String(Files.readAllBytes(source), StandardCharsets.ISO_8859_1);
                String changed = original.replace("/Type /XObject", type);
                assertTrue(original.contains("/Type /XObject"));
                assertEquals(original.length(), changed.length());
                Files.write(source, changed.getBytes(StandardCharsets.ISO_8859_1));
                byte[] before = Files.readAllBytes(source);
                TextStructureExtraction result = query(source, limits());
                assertEquals(kind == 0 ? "ABC" : kind == 1 ? "Form" : "", result.getPages().get(0).getText());
                if (kind == 1 || kind == 2) {
                    assertEquals("Form", result.getStructureRoots().get(0).getChildren().get(0)
                            .getObjectReference().get().getSubtype());
                } else if (kind == 3) {
                    assertEquals(0, result.getStructureRoots().get(0).getChildren().get(0)
                            .getMarkedContent().get().getMarkedContentId());
                }
                assertArrayEquals(before, Files.readAllBytes(source));
            }
        }
    }

    @Test(timeout = 10000L)
    public void markedContentInsideRepeatedFormsRetainsOccurrencesAndOuterActualText()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("marked-form.pdf");
        writeFormFixture(source,
                "/Outer <</MCID 0 /ActualText (Outer)>> BDC /Fm Do EMC /Fm Do\n",
                "/Inner <</MCID 0 /Alt (description) /ActualText (Inner)>> BDC "
                        + "BT /F1 12 Tf 10 20 Td (A) Tj ET EMC\n",
                "/BBox [0 0 100 100] /Matrix [1 0 0 1 5 7] "
                        + "/Resources << /Font << /F1 << /Type /Font /Subtype /Type1 "
                        + "/BaseFont /Helvetica /Encoding /WinAnsiEncoding >> >> >>");
        byte[] before = Files.readAllBytes(source);

        TextStructureExtraction extraction = query(source, limits());

        PageText page = extraction.getPages().get(0);
        assertEquals("OuterInner", page.getText());
        assertEquals(2, page.getTextItems().size());
        assertEquals(3, page.getMarkedContentSequences().size());
        MarkedContentSequence outer = page.getMarkedContentSequences().get(0);
        MarkedContentSequence first = page.getMarkedContentSequences().get(1);
        MarkedContentSequence repeated = page.getMarkedContentSequences().get(2);
        assertEquals(Integer.valueOf(outer.getId()), first.getParentId().get());
        assertFalse(repeated.getParentId().isPresent());
        assertEquals("description", first.getAlternateText().get());
        assertEquals("Inner", repeated.getActualText().get());
        assertEquals(java.util.Arrays.asList(1, 2),
                page.getTextItems().get(0).getMarkedContentSequenceIds());
        assertEquals(java.util.Collections.singletonList(3),
                page.getTextItems().get(1).getMarkedContentSequenceIds());
        assertEquals(java.util.Collections.singletonList(1), first.getTextItemIndices());
        assertEquals(java.util.Collections.singletonList(2), repeated.getTextItemIndices());
        assertEquals(new BigDecimal("15"), page.getTextItems().get(0).getGeometry().getE());
        assertEquals(new BigDecimal("27"), page.getTextItems().get(0).getGeometry().getF());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void pageAndFormMcrsUseDistinctStreamScopesInBothProfiles()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("stream-mcids.pdf");
        writeScopedMcrFixture(source, "/Fm Do");
        byte[] before = Files.readAllBytes(source);
        for (WorkflowExecutionProfile profile : WorkflowExecutionProfile.values()) {
            WorkflowOutcome<TextStructureExtraction> outcome = new DocumentWorkflow().execute(
                    requestBuilder().executionProfile(profile)
                            .source("input", DocumentSource.path(source)).primarySource("input")
                            .saveMode(SaveMode.REWRITE).build(),
                    session -> session.query(ExtractTextAndStructure.version1(limits())));
            assertEquals(profile, outcome.getExecutionProfile());
            assertTrue(outcome.getPublicationReceipts().isEmpty());
            TextStructureExtraction extraction = outcome.getResult();
            PageText page = extraction.getPages().get(0);
            assertEquals("ABee", page.getText());
            MarkedContentReference pageReference = childElement(extraction.getStructureRoots().get(0), 0)
                    .getChildren().get(0).getMarkedContent().get();
            MarkedContentReference formReference = childElement(extraction.getStructureRoots().get(0), 1)
                    .getChildren().get(0).getMarkedContent().get();
            assertEquals(0, pageReference.getContentStreamId());
            assertEquals(1, formReference.getContentStreamId());
            assertEquals(Integer.valueOf(1), pageReference.getMarkedContentSequenceId().get());
            assertEquals(Integer.valueOf(2), formReference.getMarkedContentSequenceId().get());
            assertEquals(0, page.getMarkedContentSequences().get(0).getContentStreamId());
            assertEquals(1, page.getMarkedContentSequences().get(1).getContentStreamId());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void annotationObjectReferencesRetainOrderedDetachedSessionIdentityInBothProfiles()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("structure-objr.pdf");
        writePdf(source,
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 5 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Annots [4 0 R] >>",
                "<< /Type /Annot /Subtype /Link /Rect [10 10 30 30] /P 3 0 R /StructParent 0 >>",
                "<< /Type /StructTreeRoot /K 6 0 R /ParentTree 7 0 R >>",
                "<< /Type /StructElem /S /Link /P 5 0 R /Pg 3 0 R /Alt (Example link) "
                        + "/K << /Type /OBJR /Obj 4 0 R >> >>",
                "<< /Nums [0 6 0 R] >>");
        byte[] before = Files.readAllBytes(source);
        for (WorkflowExecutionProfile profile : WorkflowExecutionProfile.values()) {
            WorkflowOutcome<TextStructureExtraction> outcome = new DocumentWorkflow().execute(
                    requestBuilder().executionProfile(profile)
                            .source("input", DocumentSource.path(source)).primarySource("input")
                            .saveMode(SaveMode.REWRITE).build(), session -> {
                        TextStructureExtraction result = session.query(
                                ExtractTextAndStructure.version1(limits()));
                        LogicalStructureItem item = result.getStructureRoots().get(0).getChildren().get(0);
                        assertEquals(LogicalStructureItem.Kind.OBJECT, item.getKind());
                        assertFalse(item.getElement().isPresent());
                        assertFalse(item.getMarkedContent().isPresent());
                        ObjectReference reference = item.getObjectReference().get().getObjectReference();
                        PdfDictionary annotation = inspectedDictionary(session, reference);
                        assertEquals(PdfName.of("Link"), annotation.get(PdfName.of("Subtype")));
                        PdfDictionary page = inspectedDictionary(session,
                                session.query(PageObjectReference.version1(1)));
                        PdfArray annotations = (PdfArray) page.get(PdfName.of("Annots"));
                        assertEquals(((PdfIndirectReference) annotations.get(0)).getReference(), reference);
                        return result;
                    });
            assertEquals(profile, outcome.getExecutionProfile());
            assertTrue(outcome.getPublicationReceipts().isEmpty());
            LogicalStructureItem detached = outcome.getResult().getStructureRoots().get(0).getChildren().get(0);
            assertEquals(1, detached.getObjectReference().get().getPageNumber());
            assertEquals("Link", detached.getObjectReference().get().getSubtype());
            assertEquals("Example link", outcome.getResult().getStructureRoots().get(0).getAlternateText().get());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void wholeFormObjectReferenceCoversRepeatedRenderingOnItsPage() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("form-objr.pdf");
        writePdf(source,
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 6 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] /Resources << /XObject << /Fm 5 0 R >> >> >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R >>",
                streamObject("/Fm Do /Fm Do\n", ""),
                streamObject("/Span <</ActualText (Form)>> BDC EMC\n",
                        "/Type /XObject /Subtype /Form /BBox [0 0 100 100] /Resources << >> /StructParent 0 "),
                "<< /Type /StructTreeRoot /K 7 0 R /ParentTree 8 0 R >>",
                "<< /Type /StructElem /S /Figure /P 6 0 R /Pg 3 0 R /K << /Type /OBJR /Obj 5 0 R >> >>",
                "<< /Nums [0 7 0 R] >>");
        byte[] before = Files.readAllBytes(source);
        for (WorkflowExecutionProfile profile : WorkflowExecutionProfile.values()) {
            WorkflowOutcome<TextStructureExtraction> outcome = new DocumentWorkflow().execute(
                    requestBuilder().executionProfile(profile)
                            .source("input", DocumentSource.path(source)).primarySource("input")
                            .saveMode(SaveMode.REWRITE).build(), session -> session.query(
                                    ExtractTextAndStructure.version1(limits())));
            assertEquals(profile, outcome.getExecutionProfile());
            TextStructureExtraction result = outcome.getResult();
            assertEquals("FormForm", result.getPages().get(0).getText());
            assertEquals(1, result.getStructureRoots().get(0).getChildren().size());
            assertEquals("Form", result.getStructureRoots().get(0).getChildren().get(0)
                    .getObjectReference().get().getSubtype());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void appearanceMcrRetainsItsAnnotationOwnerWithoutInventingPageText() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("appearance-mcr.pdf");
        writeAppearanceMcrFixture(source, "/Span <</MCID 0 /ActualText (Appearance)>> BDC EMC\n");
        byte[] before = Files.readAllBytes(source);
        for (WorkflowExecutionProfile profile : WorkflowExecutionProfile.values()) {
            TextStructureExtraction extraction = new DocumentWorkflow().execute(
                    requestBuilder().executionProfile(profile)
                            .source("input", DocumentSource.path(source)).primarySource("input")
                            .saveMode(SaveMode.REWRITE).build(), session -> {
                        TextStructureExtraction result = session.query(ExtractTextAndStructure.version1(limits()));
                        MarkedContentReference reference = result.getStructureRoots().get(0)
                                .getChildren().get(0).getMarkedContent().get();
                        PdfDictionary owner = inspectedDictionary(session, reference.getStreamOwner().get());
                        assertEquals(PdfName.of("Stamp"), owner.get(PdfName.of("Subtype")));
                        return result;
                    }).getResult();
            MarkedContentReference reference = extraction.getStructureRoots().get(0)
                    .getChildren().get(0).getMarkedContent().get();
            assertEquals(1, reference.getPageNumber());
            assertEquals(1, reference.getContentStreamId());
            assertFalse(reference.getMarkedContentSequenceId().isPresent());
            assertEquals("", extraction.getPages().get(0).getText());
            assertTrue(extraction.getPages().get(0).getTextItems().isEmpty());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void appearanceMcrAcceptsAnOmittedOrNullOptionalStreamOwner() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("appearance-mcr-optional-owner.pdf");
        for (String owner : new String[] {"             ", "/StmOwn null "}) {
            writeAppearanceMcrFixture(source, "/Span <</MCID 0 /ActualText (Appearance)>> BDC EMC\n");
            String original = new String(Files.readAllBytes(source), StandardCharsets.US_ASCII);
            assertEquals("/StmOwn 4 0 R".length(), owner.length());
            Files.write(source, original.replace("/StmOwn 4 0 R", owner).getBytes(StandardCharsets.US_ASCII));
            byte[] before = Files.readAllBytes(source);
            TextStructureExtraction result = query(source, limits());
            MarkedContentReference reference = result.getStructureRoots().get(0)
                    .getChildren().get(0).getMarkedContent().get();
            assertEquals(1, reference.getPageNumber());
            assertEquals(1, reference.getContentStreamId());
            assertEquals(0, reference.getMarkedContentId());
            assertFalse(reference.getStreamOwner().isPresent());
            assertFalse(reference.getMarkedContentSequenceId().isPresent());
            assertEquals("", result.getPages().get(0).getText());
            assertTrue(result.getPages().get(0).getTextItems().isEmpty());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void inconsistentParentTreeBacklinkFailsTheWholeQueryInBothProfiles() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("wrong-parent-tree.pdf");
        writeScopedMcrFixture(source, "/Fm Do");
        byte[] original = Files.readAllBytes(source);
        String changed = new String(original, StandardCharsets.US_ASCII)
                .replace("/Nums [0 [9 0 R] 1 [10 0 R]]", "/Nums [0 [9 0 R] 1 [ 9 0 R]]");
        Files.write(source, changed.getBytes(StandardCharsets.US_ASCII));
        byte[] before = Files.readAllBytes(source);
        for (WorkflowExecutionProfile profile : WorkflowExecutionProfile.values()) {
            try {
                new DocumentWorkflow().execute(
                        requestBuilder().executionProfile(profile)
                                .source("input", DocumentSource.path(source)).primarySource("input")
                                .saveMode(SaveMode.REWRITE).build(), session -> session.query(
                                        ExtractTextAndStructure.version1(limits())));
                fail("Expected inconsistent ParentTree to fail");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.QUERY_FAILED, failure.getCode());
                assertEquals(CAPABILITY, failure.getCapabilityId());
                assertEquals("The document text and logical structure could not be extracted safely.",
                        failure.getDiagnostic());
            }
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void formMcrCannotClaimAStreamOutsideItsDeclaredPageResources() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("foreign-form-mcr.pdf");
        writeScopedMcrFixture(source, "");
        String original = new String(Files.readAllBytes(source), StandardCharsets.US_ASCII);
        Files.write(source, original.replace("/XObject << /Fm 5 0 R >>", "                        ")
                .getBytes(StandardCharsets.US_ASCII));
        byte[] before = Files.readAllBytes(source);
        assertQueryFailure(source, limits());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void unexecutedAppearanceMcrRequiresBalancedContentAndItsDeclaredMcid() throws Exception {
        String[] programs = {
            "EMC\n",
            "/Span <</MCID 0>> BDC\n",
            "/Span <</MCID 1>> BDC EMC\n",
            "/Span <</MCID 0>> BDC EMC /Span <</MCID 0>> BDC EMC\n"
        };
        for (int index = 0; index < programs.length; index++) {
            Path source = temporaryFolder.getRoot().toPath().resolve("invalid-appearance-mcr-" + index + ".pdf");
            writeAppearanceMcrFixture(source, programs[index]);
            byte[] before = Files.readAllBytes(source);
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void unexecutedAppearanceContentItemsRemainLeavesInEitherStructureOrder() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("appearance-nested-mcr.pdf");
        writeAppearanceMcrPairFixture(source, false, false);
        TextStructureExtraction result = query(source, limits());
        assertEquals("", result.getPages().get(0).getText());
        assertEquals(2, result.getStructureRoots().get(0).getChildren().size());
        for (LogicalStructureItem child : result.getStructureRoots().get(0).getChildren()) {
            assertFalse(child.getMarkedContent().get().getMarkedContentSequenceId().isPresent());
        }
        for (boolean reverse : new boolean[] {false, true}) {
            writeAppearanceMcrPairFixture(source, true, reverse);
            byte[] before = Files.readAllBytes(source);
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void wholeFormObjectReferenceRequiresEachRenderedPage() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("two-page-form-objr.pdf");
        writeTwoPageObjectReferenceFixture(source, false);
        byte[] before = Files.readAllBytes(source);
        assertQueryFailure(source, limits());
        assertArrayEquals(before, Files.readAllBytes(source));

        writeTwoPageObjectReferenceFixture(source, true);
        TextStructureExtraction extraction = query(source, limits());
        List<LogicalStructureItem> children = extraction.getStructureRoots().get(0).getChildren();
        assertEquals(2, children.size());
        assertEquals(1, children.get(0).getObjectReference().get().getPageNumber());
        assertEquals(2, children.get(1).getObjectReference().get().getPageNumber());
        assertEquals(children.get(0).getObjectReference().get().getObjectReference(),
                children.get(1).getObjectReference().get().getObjectReference());
        assertEquals("Form", extraction.getPages().get(0).getText());
        assertEquals("Form", extraction.getPages().get(1).getText());
    }

    @Test(timeout = 10000L)
    public void parentTreeRequiresAccurateRangesAndOrderedChildren() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("parent-tree-ranges.pdf");
        String[][] trees = {
            {"<< /Kids [12 0 R 13 0 R] >>", "<< /Limits [5 5] /Nums [0 [9 0 R]] >>"},
            {"<< /Kids [12 0 R 13 0 R] >>", "<< /Nums [0 [9 0 R]] >>"},
            {"<< /Kids [13 0 R 12 0 R] >>", "<< /Limits [0 0] /Nums [0 [9 0 R]] >>"}
        };
        for (String[] tree : trees) {
            writeScopedMcrFixture(source, "/Fm Do", tree[0], tree[1],
                    "<< /Limits [1 1] /Nums [1 [10 0 R]] >>");
            byte[] before = Files.readAllBytes(source);
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
        writeScopedMcrFixture(source, "/Fm Do", "<< /Kids [12 0 R 13 0 R] >>",
                "<< /Limits [0 0] /Nums [0 [9 0 R]] >>",
                "<< /Limits [1 1] /Nums [1 [10 0 R]] >>");
        assertEquals("ABee", query(source, limits()).getPages().get(0).getText());
    }

    @Test(timeout = 10000L)
    public void structuralContentItemsCannotClaimBothAnInvocationAndItsInternalMcid() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("nested-structural-content.pdf");
        for (boolean reverse : new boolean[] {false, true}) {
            writeScopedMcrFixture(source, "/Fm Do");
            String original = new String(Files.readAllBytes(source), StandardCharsets.US_ASCII);
            String nested = original.replace("ET EMC /Fm Do", "ET /Fm Do EMC");
            if (reverse) {
                nested = nested.replace("/K [9 0 R 10 0 R]", "/K [10 0 R 9 0 R]");
            }
            assertEquals(original.length(), nested.length());
            Files.write(source, nested.getBytes(StandardCharsets.US_ASCII));
            byte[] before = Files.readAllBytes(source);
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void structuralMarkedContentCannotEncloseAWholeObjectContentItem() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("mcr-encloses-objr.pdf");
        writeMcrObjectOverlapFixture(source, false, false);
        assertEquals("PageForm", query(source, limits()).getPages().get(0).getText());
        for (boolean reverse : new boolean[] {false, true}) {
            writeMcrObjectOverlapFixture(source, true, reverse);
            byte[] before = Files.readAllBytes(source);
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void wholeStructuralObjectCannotInvokeAnotherStructuralObject() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("objr-invokes-objr.pdf");
        writeObjectNestingFixture(source, false, false);
        assertEquals("OuterInner", query(source, limits()).getPages().get(0).getText());
        for (boolean reverse : new boolean[] {false, true}) {
            writeObjectNestingFixture(source, true, reverse);
            byte[] before = Files.readAllBytes(source);
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void wholeStructuralObjectCannotContainAnotherFormsStructuralMcid() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("objr-contains-mcr.pdf");
        writeObjectNestingFixture(source, false, false, true);
        assertEquals("OuterInner", query(source, limits()).getPages().get(0).getText());
        for (boolean reverse : new boolean[] {false, true}) {
            writeObjectNestingFixture(source, true, reverse, true);
            byte[] before = Files.readAllBytes(source);
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void unexecutedFormDefinitionsStillKeepStructuralContentItemsAsLeaves() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("unexecuted-structural-form.pdf");
        writeUnexecutedObjectNestingFixture(source, true, false);
        TextStructureExtraction separate = query(source, limits());
        assertEquals("", separate.getPages().get(0).getText());
        assertFalse(separate.getStructureRoots().get(0).getChildren().get(0)
                .getMarkedContent().get().getMarkedContentSequenceId().isPresent());
        for (boolean marked : new boolean[] {true, false}) {
            writeUnexecutedObjectNestingFixture(source, marked, true);
            byte[] before = Files.readAllBytes(source);
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void unexecutedFormDefinitionsChargeDecodedBytesWithoutInventingExecutions() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("unexecuted-definition-byte-limit.pdf");
        writeUnexecutedObjectNestingFixture(source, true, false);
        byte[] before = Files.readAllBytes(source);
        // The authored outer definition is 33 ASCII bytes; the inner definition is empty.
        ExtractionLimits exact = new BoundaryLimits().streams(0).streamDepth(0).textItems(0)
                .decodedBytes(33).structureElements(2).structureItems(64).build();
        TextStructureExtraction result = query(source, exact);
        assertEquals("", result.getPages().get(0).getText());
        assertTrue(result.getPages().get(0).getTextItems().isEmpty());
        assertTrue(result.getPages().get(0).getMarkedContentSequences().isEmpty());
        assertLimitFailure(source, new BoundaryLimits().streams(0).streamDepth(0).textItems(0)
                .decodedBytes(32).structureElements(2).structureItems(64).build());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void unexecutedWholeObjectFormsRequireUniqueMcidDefinitions() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("unexecuted-duplicate-mcid.pdf");
        writeUnexecutedWholeObjectFixture(source, 1);
        TextStructureExtraction result = query(source, limits());
        assertEquals("", result.getPages().get(0).getText());
        assertTrue(result.getPages().get(0).getMarkedContentSequences().isEmpty());
        assertEquals(LogicalStructureItem.Kind.OBJECT,
                result.getStructureRoots().get(0).getChildren().get(0).getKind());
        writeUnexecutedWholeObjectFixture(source, 0);
        byte[] before = Files.readAllBytes(source);
        assertQueryFailure(source, limits());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void referencedAppearancesCannotRepeatAnInternallyStructuredDescendant() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("appearance-repeated-child.pdf");
        writeAppearanceInvocationFixture(source, 1, true, false);
        TextStructureExtraction single = query(source, limits());
        assertEquals("", single.getPages().get(0).getText());
        assertFalse(single.getStructureRoots().get(1).getChildren().get(0)
                .getMarkedContent().get().getMarkedContentSequenceId().isPresent());
        writeAppearanceInvocationFixture(source, 2, false, false);
        assertEquals("", query(source, limits()).getPages().get(0).getText());
        for (boolean throughOrdinaryForm : new boolean[] {false, true}) {
            writeAppearanceInvocationFixture(source, 2, true, throughOrdinaryForm);
            byte[] before = Files.readAllBytes(source);
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void independentAppearanceEntryPointsShareStructuralInvocationCounts() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("independent-appearance-invocations.pdf");
        writeSharedAppearanceDescendantFixture(source, false, false, false, false);
        assertEquals("", query(source, limits()).getPages().get(0).getText());
        for (boolean reverse : new boolean[] {false, true}) {
            for (int scenario = 0; scenario < 3; scenario++) {
                writeSharedAppearanceDescendantFixture(source,
                        scenario == 0, scenario == 1, scenario == 2, reverse);
                byte[] before = Files.readAllBytes(source);
                assertQueryFailure(source, limits());
                assertArrayEquals(before, Files.readAllBytes(source));
            }
        }
    }

    @Test(timeout = 10000L)
    public void normalAppearanceRootsWithoutStructuralItemsShareInvocationCounts() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("unlinked-appearance-root.pdf");
        for (boolean throughOrdinaryForm : new boolean[] {false, true}) {
            writeUnlinkedAppearanceRootFixture(source, false, throughOrdinaryForm);
            byte[] before = Files.readAllBytes(source);
            TextStructureExtraction result = query(source, limits());
            assertEquals("", result.getPages().get(0).getText());
            assertFalse(result.getStructureRoots().get(0).getChildren().get(0)
                    .getMarkedContent().get().getMarkedContentSequenceId().isPresent());
            assertArrayEquals(before, Files.readAllBytes(source));

            String original = new String(before, StandardCharsets.US_ASCII);
            Files.write(source, original.replace("/Target Do\n", "          \n")
                    .getBytes(StandardCharsets.US_ASCII));
            before = Files.readAllBytes(source);
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));

            writeUnlinkedAppearanceRootFixture(source, true, throughOrdinaryForm);
            before = Files.readAllBytes(source);
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void appearanceOnlyWholeFormReferencesRetainDetachedPageAssociation() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("appearance-whole-form.pdf");
        for (boolean throughOrdinaryForm : new boolean[] {false, true}) {
            writePdf(source,
                    "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 6 0 R >>",
                    "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Annots [4 0 R] >>",
                    "<< /Type /Annot /Subtype /Stamp /Rect [0 0 100 100] /P 3 0 R /F 0 /AP << /N "
                            + (throughOrdinaryForm ? 9 : 5) + " 0 R >> >>",
                    streamObject("/Artifact BMC EMC\n", "/Type /XObject /Subtype /Form "
                            + "/BBox [0 0 100 100] /Resources << >> /StructParent 0 "),
                    "<< /Type /StructTreeRoot /K 7 0 R /ParentTree 8 0 R >>",
                    "<< /Type /StructElem /S /Figure /P 6 0 R /Pg 3 0 R "
                            + "/K << /Type /OBJR /Obj 5 0 R >> >>",
                    "<< /Nums [0 7 0 R] >>",
                    streamObject("/F Do /F Do\n", "/Type /XObject /Subtype /Form /BBox [0 0 100 100] "
                            + "/Resources << /XObject << /F 5 0 R >> >> "));
            byte[] before = Files.readAllBytes(source);
            TextStructureExtraction result = query(source, limits());
            LogicalObjectReference reference = result.getStructureRoots().get(0)
                    .getChildren().get(0).getObjectReference().get();
            assertEquals(1, reference.getPageNumber());
            assertEquals("Form", reference.getSubtype());
            assertNotNull(reference.getObjectReference());
            assertEquals("", result.getPages().get(0).getText());
            assertTrue(result.getPages().get(0).getTextItems().isEmpty());
            assertArrayEquals(before, Files.readAllBytes(source));
            if (throughOrdinaryForm) {
                String original = new String(before, StandardCharsets.US_ASCII);
                String noInvocations = "           \n";
                assertEquals("/F Do /F Do\n".length(), noInvocations.length());
                Files.write(source, original.replace("/F Do /F Do\n", noInvocations)
                        .getBytes(StandardCharsets.US_ASCII));
                before = Files.readAllBytes(source);
                assertQueryFailure(source, limits());
                assertArrayEquals(before, Files.readAllBytes(source));
            }
        }
    }

    @Test(timeout = 10000L)
    public void wholeFormObjectReferenceRequiresEachNormalAppearancePage() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("two-page-appearance-objr.pdf");
        for (boolean throughOrdinaryForm : new boolean[] {false, true}) {
            writeTwoPageAppearanceObjectReferenceFixture(source, throughOrdinaryForm, true, true);
            byte[] before = Files.readAllBytes(source);
            TextStructureExtraction result = query(source, limits());
            List<LogicalStructureItem> children = result.getStructureRoots().get(0).getChildren();
            assertEquals(2, children.size());
            assertEquals(1, children.get(0).getObjectReference().get().getPageNumber());
            assertEquals(2, children.get(1).getObjectReference().get().getPageNumber());
            assertEquals(children.get(0).getObjectReference().get().getObjectReference(),
                    children.get(1).getObjectReference().get().getObjectReference());
            assertEquals("", result.getPages().get(0).getText());
            assertEquals("", result.getPages().get(1).getText());
            assertArrayEquals(before, Files.readAllBytes(source));

            writeTwoPageAppearanceObjectReferenceFixture(source, throughOrdinaryForm, false, true);
            before = Files.readAllBytes(source);
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }

        writeTwoPageAppearanceObjectReferenceFixture(source, true, false, false);
        byte[] before = Files.readAllBytes(source);
        TextStructureExtraction result = query(source, limits());
        assertEquals(1, result.getStructureRoots().get(0).getChildren().size());
        assertEquals("", result.getPages().get(1).getText());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void distinctAppearanceOwnersRemainSeparateInvocationEntryPoints() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("distinct-appearance-owners.pdf");
        for (boolean ordinaryIntermediate : new boolean[] {false, true}) {
            writeSharedAppearanceOwnerFixture(source, false, ordinaryIntermediate, false);
            assertEquals("", query(source, limits()).getPages().get(0).getText());
            for (boolean reverse : new boolean[] {false, true}) {
                writeSharedAppearanceOwnerFixture(source, true, ordinaryIntermediate, reverse);
                byte[] before = Files.readAllBytes(source);
                assertQueryFailure(source, limits());
                assertArrayEquals(before, Files.readAllBytes(source));
            }
        }
    }

    @Test(timeout = 10000L)
    public void executedContentReferencesRequireAnExistingMcidDefinition() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("missing-executed-mcid.pdf");
        for (String changedSequence : new String[] {"/P <</MCID 0>>", "/P <</MCID 0 /ActualText"}) {
            writeScopedMcrFixture(source, "/Fm Do");
            String original = new String(Files.readAllBytes(source), StandardCharsets.US_ASCII);
            Files.write(source, original.replace(changedSequence, changedSequence.replace("MCID 0", "MCID 1"))
                    .getBytes(StandardCharsets.US_ASCII));
            byte[] before = Files.readAllBytes(source);
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void publicExtractionObserverUsesRequestedExecutionProfile() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("observer-profile.pdf");
        createBoundaryFixture(source);
        WorkflowOutcome<TextStructureExtraction> outcome = new DocumentWorkflow().execute(
                requestBuilder().source("input", DocumentSource.path(source)).primarySource("input")
                        .saveMode(SaveMode.REWRITE).build(), session -> session.query(
                                ExtractTextAndStructure.version1(new BoundaryLimits().build())));
        assertEquals(WorkflowExecutionProfile.valueOf(System.getProperty(
                "folio.t13.executionProfile", "IN_PROCESS")), outcome.getExecutionProfile());
        assertEquals("A", outcome.getResult().getPages().get(0).getText());
        assertTrue(outcome.getPublicationReceipts().isEmpty());
    }

    @Test(timeout = 10000L)
    public void internallyStructuredFormCannotBeInvokedRepeatedly() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("repeated-structural-form.pdf");
        writeScopedMcrFixture(source, "/Fm Do /Fm Do");
        byte[] before = Files.readAllBytes(source);
        assertQueryFailure(source, limits());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void duplicateMcidDefinitionsInsideOneFormFailSafely() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("duplicate-form-mcid.pdf");
        writeFormFixture(source, "/Fm Do\n",
                "/Span <</MCID 0>> BDC EMC /Span <</MCID 0>> BDC EMC\n",
                "/BBox [0 0 100 100] /Resources << >>");
        byte[] before = Files.readAllBytes(source);

        assertQueryFailure(source, limits());
        assertArrayEquals(before, Files.readAllBytes(source));
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
                .structureItems(4).build());
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
        writeContentCycleFixture(contentCycle);
        writeStructureCycleFixture(structureCycle);
        byte[] contentBefore = Files.readAllBytes(contentCycle);
        byte[] structureBefore = Files.readAllBytes(structureCycle);

        assertQueryFailure(contentCycle, limits());
        assertQueryFailure(contentCycle, limits());
        assertQueryFailure(structureCycle, limits());
        assertQueryFailure(structureCycle, limits());
        assertArrayEquals(contentBefore, Files.readAllBytes(contentCycle));
        assertArrayEquals(structureBefore, Files.readAllBytes(structureCycle));
    }

    @Test(timeout = 10000L)
    public void unqualifiedStandardNamesHonorTheDocumentRoleMap() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("remapped-standard-name.pdf");
        writeRoleFixture(source, "P", "/P /Span");
        byte[] before = Files.readAllBytes(source);
        LogicalStructureElement element = query(source, limits()).getStructureRoots().get(0);
        assertEquals("P", element.getRole());
        assertEquals("Span", element.getResolvedRole().get());
        assertEquals(LogicalStructureElement.RoleResolution.ROLE_MAP, element.getRoleResolution());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void circularUnqualifiedRolesResolveBoundedlyWithoutInventingAStandardRole() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("circular-unqualified-role.pdf");
        writeRoleFixture(source, "A", "/A /B /B /A");
        byte[] before = Files.readAllBytes(source);
        LogicalStructureElement element = query(source, limits()).getStructureRoots().get(0);
        assertEquals("A", element.getRole());
        assertFalse(element.getResolvedRole().isPresent());
        assertEquals(LogicalStructureElement.RoleResolution.UNRESOLVED, element.getRoleResolution());
        assertArrayEquals(before, Files.readAllBytes(source));
        writeRoleFixture(source, "A", "/A /P /P /A");
        assertEquals("P", query(source, limits()).getStructureRoots().get(0).getResolvedRole().get());
        writeRoleMapCycleFixture(source);
        assertTrue(query(source, limits()).getStructureRoots().isEmpty());
    }

    @Test(timeout = 10000L)
    public void explicitPdfTwoNamespaceRetainsIdentityAndResolvesItsStandardRole() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("pdf2-namespace.pdf");
        writeNamespaceFixture(source, "Title", "<< /NS (http://iso.org/pdf2/ssn) >>");
        byte[] before = Files.readAllBytes(source);
        TextStructureExtraction result = new DocumentWorkflow().execute(
                requestBuilder().source("input", DocumentSource.path(source)).primarySource("input")
                        .saveMode(SaveMode.REWRITE).build(), session -> {
                    TextStructureExtraction extraction = session.query(ExtractTextAndStructure.version1(limits()));
                    LogicalStructureElement element = extraction.getStructureRoots().get(0);
                    assertEquals(PdfString.of("http://iso.org/pdf2/ssn".getBytes(StandardCharsets.US_ASCII)),
                            inspectedDictionary(session, element.getNamespaceReference().get()).get(PdfName.of("NS")));
                    return extraction;
                }).getResult();
        LogicalStructureElement element = result.getStructureRoots().get(0);
        assertEquals("Title", element.getRole());
        assertEquals("Title", element.getResolvedRole().get());
        assertEquals(LogicalStructureElement.RoleResolution.STANDARD, element.getRoleResolution());
        assertEquals("http://iso.org/pdf2/ssn", element.getDeclaredNamespaceName().get());
        assertEquals("http://iso.org/pdf2/ssn", element.getResolvedNamespaceName().get());
        assertTrue(element.getNamespaceReference().isPresent());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void namespaceRoleChainsPreserveDeclaredIdentityAndUseOneSharedGraphBudget() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("namespace-role-chain.pdf");
        writeNamespaceChainFixture(source);
        byte[] before = Files.readAllBytes(source);
        ExtractionLimits exact = new BoundaryLimits().roleMappings(7).unicode(512)
                .structureElements(4).structureItems(4).structureDepth(2).build();
        LogicalStructureElement root = query(source, exact).getStructureRoots().get(0);
        assertEquals("Story", root.getRole());
        assertEquals("urn:folio:story", root.getDeclaredNamespaceName().get());
        assertEquals("Title", root.getResolvedRole().get());
        assertEquals("http://iso.org/pdf2/ssn", root.getResolvedNamespaceName().get());
        assertEquals(LogicalStructureElement.RoleResolution.ROLE_MAP, root.getRoleResolution());
        LogicalStructureElement term = root.getChildren().get(0).getElement().get();
        assertEquals(root.getNamespaceReference(), term.getNamespaceReference());
        assertEquals("P", term.getResolvedRole().get());
        assertEquals("http://iso.org/pdf/ssn", term.getResolvedNamespaceName().get());
        assertEquals("fr-CA", term.getEffectiveLanguage().get());
        assertEquals(LogicalStructureElement.LanguageSource.ANCESTOR, term.getLanguageSource());
        LogicalStructureElement unqualified = root.getChildren().get(1).getElement().get();
        assertFalse(unqualified.getDeclaredNamespaceName().isPresent());
        assertFalse(unqualified.getNamespaceReference().isPresent());
        assertEquals("Span", unqualified.getResolvedRole().get());
        assertEquals("http://iso.org/pdf/ssn", unqualified.getResolvedNamespaceName().get());
        assertEquals("H7", root.getChildren().get(2).getElement().get().getResolvedRole().get());
        assertLimitFailure(source, new BoundaryLimits().roleMappings(6).unicode(512)
                .structureElements(4).structureItems(4).structureDepth(2).build());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void pdfTwoUndefinedNamespaceAppliesTheWholeRoleMapBeforeResolving() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("pdf2-unqualified-role.pdf");
        writeRoleFixture(source, "P", "/P /Span /Span /Note", true);
        LogicalStructureElement element = query(source, limits()).getStructureRoots().get(0);
        assertEquals("Note", element.getResolvedRole().get());
        assertEquals("http://iso.org/pdf/ssn", element.getResolvedNamespaceName().get());
        assertEquals(LogicalStructureElement.RoleResolution.ROLE_MAP, element.getRoleResolution());
        writeRoleFixture(source, "P", "/P /Span /Span /P", true);
        element = query(source, limits()).getStructureRoots().get(0);
        assertFalse(element.getResolvedRole().isPresent());
        assertFalse(element.getResolvedNamespaceName().isPresent());
    }

    @Test(timeout = 10000L)
    public void malformedNamespacesAndRoleTargetsFailWithoutPublishingAPrefix() throws Exception {
        String[] namespaces = {
            "<< /Type /Other /NS (urn:folio:custom) >>",
            "<< /NS /WrongKind >>",
            "<< /Type /Namespace >>",
            "<< /NS (urn:folio:custom) /RoleMapNS 42 >>",
            "<< /NS (urn:folio:custom) /RoleMapNS << /Story [/P] >> >>",
            "<< /NS (urn:folio:custom) /RoleMapNS << /Story [/P << /NS (http://iso.org/pdf/ssn) >>] >> >>",
            "<< /NS (urn:folio:custom) /RoleMapNS << /Story [42 6 0 R] >> >>"
        };
        Path source = temporaryFolder.getRoot().toPath().resolve("malformed-namespace.pdf");
        for (String namespace : namespaces) {
            writeNamespaceFixture(source, "Story", namespace);
            byte[] before = Files.readAllBytes(source);
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void namespaceVocabularyAndRootMembershipControlResolution() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("namespace-vocabulary.pdf");
        for (String[] vocabulary : new String[][] {
            {"P", "urn:folio:custom"}, {"Art", "http://iso.org/pdf2/ssn"}, {"Title", "http://iso.org/pdf/ssn"}
        }) {
            writeNamespaceFixture(source, vocabulary[0], "<< /NS (" + vocabulary[1] + ") >>");
            LogicalStructureElement element = query(source, limits()).getStructureRoots().get(0);
            assertEquals(vocabulary[1], element.getDeclaredNamespaceName().get());
            assertEquals(LogicalStructureElement.RoleResolution.UNRESOLVED, element.getRoleResolution());
            assertFalse(element.getResolvedRole().isPresent());
            assertFalse(element.getResolvedNamespaceName().isPresent());
        }
        writeNamespaceFixture(source, "P", "<< /NS (http://iso.org/pdf/ssn) >>");
        String original = new String(Files.readAllBytes(source), StandardCharsets.US_ASCII);
        Files.write(source, original.replace("/Namespaces [6 0 R]", "/Namespaces [     ]")
                .getBytes(StandardCharsets.US_ASCII));
        assertQueryFailure(source, limits());
    }

    @Test(timeout = 10000L)
    public void explicitStandardNamespaceMappingsTargetAnotherNamespace() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("explicit-standard-role-map.pdf");
        writeNamespaceFixture(source, "Title",
                "<< /NS (http://iso.org/pdf2/ssn) /RoleMapNS << /Title /P >> >>");
        LogicalStructureElement element = query(source, limits()).getStructureRoots().get(0);
        assertEquals("Title", element.getResolvedRole().get());
        assertEquals("http://iso.org/pdf2/ssn", element.getResolvedNamespaceName().get());
        for (String namespace : new String[] {
            "<< /NS (http://iso.org/pdf2/ssn) /RoleMapNS << /P [/Span 6 0 R] >> >>",
            "<< /NS (http://iso.org/pdf/ssn) /RoleMapNS << /P /Span >> >>"
        }) {
            writeNamespaceFixture(source, "P", namespace);
            byte[] before = Files.readAllBytes(source);
            assertQueryFailure(source, limits());
            assertArrayEquals(before, Files.readAllBytes(source));
        }
    }

    @Test(timeout = 10000L)
    public void namespaceMappingsRemainSeparateFromTheUndefinedNamespaceRoleMap() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("namespace-default-target.pdf");
        writeNamespaceFixture(source, "Story",
                "<< /NS (urn:folio:custom) /RoleMapNS << /Story /P >> >>", "/P /Span");
        LogicalStructureElement element = query(source, limits()).getStructureRoots().get(0);
        assertEquals("P", element.getResolvedRole().get());
        assertEquals("http://iso.org/pdf/ssn", element.getResolvedNamespaceName().get());
    }

    @Test(timeout = 10000L)
    public void unknownCrossNamespaceCycleTerminatesWithAnUnresolvedObservation() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("namespace-cycle.pdf");
        writePdf(source,
                "<< /Type /Catalog /Version /2.0 /Pages 2 0 R /StructTreeRoot 4 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << >> >>",
                "<< /Type /StructTreeRoot /K 5 0 R /Namespaces [6 0 R 7 0 R] >>",
                "<< /S /Story /NS 6 0 R /P 4 0 R >>",
                "<< /NS (urn:folio:story) /RoleMapNS << /Story [/Chapter 7 0 R] >> >>",
                "<< /NS (urn:folio:chapter) /RoleMapNS << /Chapter [/Story 6 0 R] >> >>");
        byte[] before = Files.readAllBytes(source);
        LogicalStructureElement element = query(source, new BoundaryLimits().roleMappings(4).unicode(128).build())
                .getStructureRoots().get(0);
        assertEquals(LogicalStructureElement.RoleResolution.UNRESOLVED, element.getRoleResolution());
        assertEquals("urn:folio:story", element.getDeclaredNamespaceName().get());
        assertFalse(element.getResolvedRole().isPresent());
        assertFalse(element.getResolvedNamespaceName().isPresent());
        assertLimitFailure(source, new BoundaryLimits().roleMappings(3).unicode(128).build());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void distinctNamespaceObjectsShareOneRoleMapWithoutSharingTheirIdentity() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("shared-namespace-role-map.pdf");
        writePdf(source,
                "<< /Type /Catalog /Version /2.0 /Pages 2 0 R /StructTreeRoot 4 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << >> >>",
                "<< /Type /StructTreeRoot /K [5 0 R 6 0 R] /Namespaces [7 0 R 8 0 R] >>",
                "<< /S /Story /NS 7 0 R /P 4 0 R >>",
                "<< /S /Story /NS 8 0 R /P 4 0 R >>",
                "<< /NS (urn:folio:custom) /RoleMapNS 9 0 R >>",
                "<< /NS (urn:folio:custom) /RoleMapNS 9 0 R >>",
                "<< /Story /P >>");
        List<LogicalStructureElement> roots = query(source, new BoundaryLimits().roleMappings(3).unicode(128)
                .structureElements(2).build()).getStructureRoots();
        assertEquals("P", roots.get(0).getResolvedRole().get());
        assertEquals("P", roots.get(1).getResolvedRole().get());
        assertEquals(roots.get(0).getDeclaredNamespaceName(), roots.get(1).getDeclaredNamespaceName());
        assertFalse(roots.get(0).getNamespaceReference().equals(roots.get(1).getNamespaceReference()));
        assertLimitFailure(source, new BoundaryLimits().roleMappings(2).unicode(128).structureElements(2).build());
    }

    @Test(timeout = 10000L)
    public void namespaceAdmissionChecksTheFirstExcessBeforeSchedulingLaterDeclarations() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("namespace-admission.pdf");
        writePdf(source,
                "<< /Type /Catalog /Version /2.0 /Pages 2 0 R /StructTreeRoot 4 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << >> >>",
                "<< /Type /StructTreeRoot /K 5 0 R /Namespaces [6 0 R 42] >>",
                "<< /S /Title /NS 6 0 R /P 4 0 R >>",
                "<< /NS (http://iso.org/pdf2/ssn) >>");
        byte[] before = Files.readAllBytes(source);
        assertLimitFailure(source, new BoundaryLimits().roleMappings(0).unicode(128).build());
        assertQueryFailure(source, new BoundaryLimits().roleMappings(1).unicode(128).build());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test(timeout = 10000L)
    public void directRootNamespaceDeclarationsRetainIndirectElementIdentityAndExactBounds() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("direct-namespace-declaration.pdf");
        writePdf(source,
                "<< /Type /Catalog /Version /2.0 /Pages 2 0 R /StructTreeRoot 4 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Resources << >> >>",
                "<< /Type /StructTreeRoot /K 5 0 R /Namespaces ["
                        + "<< /NS (urn:folio:declaration) /RoleMapNS << /Unused /P >> >> 6 0 R] >>",
                "<< /S /Title /NS 6 0 R /P 4 0 R >>",
                "<< /NS (http://iso.org/pdf2/ssn) >>");
        byte[] before = Files.readAllBytes(source);
        LogicalStructureElement result = query(source, new BoundaryLimits().roleMappings(3).unicode(128).build())
                .getStructureRoots().get(0);
        assertEquals("Title", result.getResolvedRole().get());
        assertEquals("http://iso.org/pdf2/ssn", result.getDeclaredNamespaceName().get());
        assertTrue(result.getNamespaceReference().isPresent());
        assertLimitFailure(source, new BoundaryLimits().roleMappings(2).unicode(128).build());
        assertArrayEquals(before, Files.readAllBytes(source));
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
                        + "/Contents 4 0 R /StructParents 0 >>",
                streamObject(BOUNDED_CONTENT, ""),
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                        + "/Encoding /WinAnsiEncoding >>",
                "<< /Type /StructTreeRoot /RoleMap << /Custom /Span >> "
                        + "/K 7 0 R /ParentTree 8 0 R >>",
                "<< /Type /StructElem /S /Custom /P 6 0 R /Pg 3 0 R "
                        + "/K 0 >>",
                "<< /Nums [0 [7 0 R]] >>");
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
                requestBuilder()
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
                requestBuilder()
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
                requestBuilder()
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
                .maximumFontDataEntries(2)
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

    private static void writeType3MetricFixture(Path target, String font, String content, String glyph)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 5 0 R >> "
                        + "/ExtGState << /G1 << /Font [5 0 R 10] >> >> >> /Contents 4 0 R >>",
                streamObject(content, ""), font, streamObject(glyph, ""));
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

    private static void writeTwoPageObjectReferenceFixture(Path target, boolean bothPages) throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 7 0 R >>",
                "<< /Type /Pages /Count 2 /Kids [3 0 R 4 0 R] /Resources << /XObject << /Fm 6 0 R >> >> >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 5 0 R >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 5 0 R >>",
                streamObject("/Fm Do\n", ""),
                streamObject("/Span <</ActualText (Form)>> BDC EMC\n",
                        "/Type /XObject /Subtype /Form /BBox [0 0 100 100] /Resources << >> /StructParent 0 "),
                "<< /Type /StructTreeRoot /K 8 0 R /ParentTree 9 0 R >>",
                "<< /Type /StructElem /S /Figure /P 7 0 R /K [<< /Type /OBJR /Pg 3 0 R /Obj 6 0 R >> "
                        + (bothPages ? "<< /Type /OBJR /Pg 4 0 R /Obj 6 0 R >>" : "") + "] >>",
                "<< /Nums [0 8 0 R] >>");
    }

    private static void writeAppearanceMcrFixture(Path target, String formContent) throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 6 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Annots [4 0 R] >>",
                "<< /Type /Annot /Subtype /Stamp /Rect [10 10 30 30] /P 3 0 R /AP << /N 5 0 R >> >>",
                streamObject(formContent,
                        "/Type /XObject /Subtype /Form /BBox [0 0 100 100] /Resources << >> /StructParents 0 "),
                "<< /Type /StructTreeRoot /K 7 0 R /ParentTree 8 0 R >>",
                "<< /Type /StructElem /S /Figure /P 6 0 R /Pg 3 0 R "
                        + "/K << /Type /MCR /Stm 5 0 R /StmOwn 4 0 R /MCID 0 >> >>",
                "<< /Nums [0 [7 0 R]] >>");
    }

    private static void writeMcrObjectOverlapFixture(Path target, boolean nested, boolean reverse)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 6 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /StructParents 0 "
                        + "/Resources << /XObject << /Fm 5 0 R >> >> /Contents 4 0 R >>",
                streamObject("/Span <</MCID 0 /ActualText (Page)>> BDC "
                        + (nested ? "/Fm Do EMC\n" : "EMC /Fm Do\n"), ""),
                streamObject("/Span <</ActualText (Form)>> BDC EMC\n",
                        "/Type /XObject /Subtype /Form /BBox [0 0 100 100] /Resources << >> /StructParent 1 "),
                "<< /Type /StructTreeRoot /K [" + (reverse ? "8 0 R 7 0 R" : "7 0 R 8 0 R")
                        + "] /ParentTree 9 0 R >>",
                "<< /Type /StructElem /S /Span /P 6 0 R /Pg 3 0 R /K 0 >>",
                "<< /Type /StructElem /S /Figure /P 6 0 R /Pg 3 0 R /K << /Type /OBJR /Obj 5 0 R >> >>",
                "<< /Nums [0 [7 0 R] 1 8 0 R] >>");
    }

    private static void writeAppearanceMcrPairFixture(Path target, boolean nested, boolean reverse)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 6 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Annots [4 0 R] >>",
                "<< /Type /Annot /Subtype /Stamp /Rect [10 10 30 30] /P 3 0 R /AP << /N 5 0 R >> >>",
                streamObject("/Span /Outer BDC " + (nested ? "" : "EMC ")
                        + "/Artifact BMC /Span <</MCID 1>> BDC EMC EMC " + (nested ? "EMC\n" : "\n"),
                        "/Type /XObject /Subtype /Form /BBox [0 0 100 100] /StructParents 0 "
                                + "/Resources << /Properties << /Outer <</MCID 0>> >> >> "),
                "<< /Type /StructTreeRoot /K 7 0 R /ParentTree 8 0 R >>",
                "<< /Type /StructElem /S /Figure /P 6 0 R /Pg 3 0 R /K ["
                        + "<< /Type /MCR /Stm 5 0 R /StmOwn 4 0 R /MCID " + (reverse ? 1 : 0) + " >> "
                        + "<< /Type /MCR /Stm 5 0 R /StmOwn 4 0 R /MCID " + (reverse ? 0 : 1) + " >>] >>",
                "<< /Nums [0 [7 0 R 7 0 R]] >>");
    }

    private static void writeObjectNestingFixture(Path target, boolean nested, boolean reverse)
            throws Exception {
        writeObjectNestingFixture(target, nested, reverse, false);
    }

    private static void writeObjectNestingFixture(Path target, boolean nested, boolean reverse, boolean innerMcid)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 7 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /XObject << /Outer 5 0 R /Inner 6 0 R >> >> /Contents 4 0 R >>",
                streamObject(nested ? "/Outer Do\n" : "/Outer Do /Inner Do\n", ""),
                streamObject("/Span <</ActualText (Outer)>> BDC EMC " + (nested ? "/Inner Do\n" : "\n"),
                        "/Type /XObject /Subtype /Form /BBox [0 0 100 100] /StructParent 0 "
                                + "/Resources << /XObject << /Inner 6 0 R >> >> "),
                streamObject("/Span <<" + (innerMcid ? "/MCID 0 " : "") + "/ActualText (Inner)>> BDC EMC\n",
                        "/Type /XObject /Subtype /Form /BBox [0 0 100 100] /Resources << >> "
                                + (innerMcid ? "/StructParents 1 " : "/StructParent 1 ")),
                "<< /Type /StructTreeRoot /K [" + (reverse ? "9 0 R 8 0 R" : "8 0 R 9 0 R")
                        + "] /ParentTree 10 0 R >>",
                "<< /Type /StructElem /S /Figure /P 7 0 R /Pg 3 0 R /K << /Type /OBJR /Obj 5 0 R >> >>",
                "<< /Type /StructElem /S /Figure /P 7 0 R /Pg 3 0 R /K << /Type /"
                        + (innerMcid ? "MCR /Stm 6 0 R /MCID 0" : "OBJR /Obj 6 0 R") + " >> >>",
                "<< /Nums [0 8 0 R 1 " + (innerMcid ? "[9 0 R]" : "9 0 R") + "] >>");
    }

    private static void writeSharedAppearanceOwnerFixture(Path target, boolean separateOwner,
            boolean ordinaryIntermediate, boolean reverse) throws Exception {
        String children = ordinaryIntermediate ? "9 0 R 10 0 R" : "9 0 R 10 0 R 14 0 R";
        if (reverse) {
            children = ordinaryIntermediate ? "10 0 R 9 0 R" : "14 0 R 10 0 R 9 0 R";
        }
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 7 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Annots [8 0 R "
                        + (separateOwner ? "12 0 R" : "") + "] "
                        + "/Resources << /XObject << /Inner 6 0 R >> >> /Contents 4 0 R >>",
                streamObject("", ""),
                streamObject("/P <</MCID 0>> BDC EMC "
                                + (ordinaryIntermediate ? "" : "/P <</MCID 1>> BDC EMC ") + "/Target Do\n",
                        "/Type /XObject /Subtype /Form /BBox [0 0 100 100] /StructParents 0 "
                                + "/Resources << /XObject << /Target " + (ordinaryIntermediate ? 13 : 6)
                                + " 0 R >> >> "),
                streamObject("/P <</MCID 0>> BDC EMC\n",
                        "/Type /XObject /Subtype /Form /BBox [0 0 100 100] /StructParents 1 /Resources << >> "),
                "<< /Type /StructTreeRoot /K [" + children + "] /ParentTree 11 0 R >>",
                "<< /Type /Annot /Subtype /Stamp /Rect [0 0 100 100] /P 3 0 R /AP << /N 5 0 R >> >>",
                "<< /S /Figure /P 7 0 R /Pg 3 0 R "
                        + "/K << /Type /MCR /Stm 5 0 R /StmOwn 8 0 R /MCID 0 >> >>",
                "<< /S /Figure /P 7 0 R /Pg 3 0 R /K << /Type /MCR /Stm 6 0 R /MCID 0 >> >>",
                "<< /Nums [0 [9 0 R " + (ordinaryIntermediate ? "" : "14 0 R") + "] 1 [10 0 R]] >>",
                "<< /Type /Annot /Subtype /Stamp /Rect [0 0 100 100] /P 3 0 R /AP << /N "
                        + (ordinaryIntermediate ? 13 : 5) + " 0 R >> >>",
                streamObject("/Inner Do\n", "/Type /XObject /Subtype /Form /BBox [0 0 100 100] "
                        + "/Resources << /XObject << /Inner 6 0 R >> >> "),
                "<< /S /Figure /P 7 0 R /Pg 3 0 R /K << /Type /MCR /Stm 5 0 R /StmOwn "
                        + (separateOwner ? 12 : 8) + " 0 R /MCID 1 >> >>");
    }

    private static void writeSharedAppearanceDescendantFixture(Path target, boolean secondInvokes,
            boolean childOwned, boolean pageInvokes, boolean reverse) throws Exception {
        String children = childOwned ? "9 0 R 10 0 R" : "9 0 R 10 0 R 14 0 R";
        if (reverse) {
            children = childOwned ? "10 0 R 9 0 R" : "14 0 R 10 0 R 9 0 R";
        }
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 7 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Annots [8 0 R 12 0 R] "
                        + "/Resources << /XObject << /Inner 6 0 R >> >> /Contents 4 0 R >>",
                streamObject(pageInvokes ? "/Inner Do\n" : "", ""),
                streamObject("/P <</MCID 0>> BDC EMC /Inner Do\n",
                        "/Type /XObject /Subtype /Form /BBox [0 0 100 100] /StructParents 0 "
                                + "/Resources << /XObject << /Inner 6 0 R >> >> "),
                streamObject("/P <</MCID 0>> BDC EMC\n",
                        "/Type /XObject /Subtype /Form /BBox [0 0 100 100] /StructParents 1 /Resources << >> "),
                "<< /Type /StructTreeRoot /K [" + children + "] /ParentTree 11 0 R >>",
                "<< /Type /Annot /Subtype /Stamp /Rect [0 0 100 100] /P 3 0 R /AP << /N 5 0 R >> >>",
                "<< /S /Figure /P 7 0 R /Pg 3 0 R "
                        + "/K << /Type /MCR /Stm 5 0 R /StmOwn 8 0 R /MCID 0 >> >>",
                "<< /S /Figure /P 7 0 R /Pg 3 0 R /K << /Type /MCR /Stm 6 0 R "
                        + (childOwned ? "/StmOwn 12 0 R " : "") + "/MCID 0 >> >>",
                "<< /Nums [0 [9 0 R] 1 [10 0 R] " + (childOwned ? "" : "2 [14 0 R]") + "] >>",
                "<< /Type /Annot /Subtype /Stamp /Rect [0 0 100 100] /P 3 0 R /AP << /N "
                        + (childOwned ? 6 : 13) + " 0 R >> >>",
                streamObject("/P <</MCID 0>> BDC EMC " + (secondInvokes ? "/Inner Do\n" : "\n"),
                        "/Type /XObject /Subtype /Form /BBox [0 0 100 100] /StructParents 2 "
                                + "/Resources << /XObject << /Inner 6 0 R >> >> "),
                "<< /S /Figure /P 7 0 R /Pg 3 0 R "
                        + "/K << /Type /MCR /Stm 13 0 R /StmOwn 12 0 R /MCID 0 >> >>");
    }

    private static void writeTwoPageAppearanceObjectReferenceFixture(
            Path target, boolean throughOrdinaryForm, boolean secondPageReference,
            boolean secondPageInvokes) throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 8 0 R >>",
                "<< /Type /Pages /Count 2 /Kids [3 0 R 4 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Annots [5 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Annots [6 0 R] >>",
                "<< /Type /Annot /Subtype /Stamp /Rect [0 0 100 100] /P 3 0 R /F 0 /AP << /N "
                        + (throughOrdinaryForm ? 11 : 7) + " 0 R >> >>",
                "<< /Type /Annot /Subtype /Stamp /Rect [0 0 100 100] /P 4 0 R /F 0 /AP << /N "
                        + (throughOrdinaryForm ? 12 : 7) + " 0 R >> >>",
                streamObject("/Artifact BMC EMC\n", "/Type /XObject /Subtype /Form "
                        + "/BBox [0 0 100 100] /Resources << >> /StructParent 0 "),
                "<< /Type /StructTreeRoot /K 9 0 R /ParentTree 10 0 R >>",
                "<< /Type /StructElem /S /Figure /P 8 0 R "
                        + "/K [<< /Type /OBJR /Pg 3 0 R /Obj 7 0 R >>"
                        + (secondPageReference ? " << /Type /OBJR /Pg 4 0 R /Obj 7 0 R >>" : "") + "] >>",
                "<< /Nums [0 9 0 R] >>",
                streamObject("/F Do /F Do\n", "/Type /XObject /Subtype /Form /BBox [0 0 100 100] "
                        + "/Resources << /XObject << /F 7 0 R >> >> "),
                streamObject(secondPageInvokes ? "/F Do\n" : "", "/Type /XObject /Subtype /Form "
                        + "/BBox [0 0 100 100] /Resources << /XObject << /F 7 0 R >> >> "));
    }

    private static void writeUnlinkedAppearanceRootFixture(
            Path target, boolean pageInvokes, boolean throughOrdinaryForm) throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 7 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Annots [10 0 R] "
                        + (pageInvokes ? "/Resources << /XObject << /Inner 6 0 R >> >> " : "")
                        + "/Contents 4 0 R >>",
                streamObject(pageInvokes ? "/Inner Do\n" : "", ""),
                streamObject("/Target Do\n",
                        "/Type /XObject /Subtype /Form /BBox [0 0 100 100] "
                                + "/Resources << /XObject << /Target " + (throughOrdinaryForm ? 11 : 6)
                                + " 0 R >> >> "),
                streamObject("/Span <</MCID 0>> BDC EMC\n",
                        "/Type /XObject /Subtype /Form /BBox [0 0 100 100] "
                                + "/Resources << >> /StructParents 0 "),
                "<< /Type /StructTreeRoot /K 8 0 R /ParentTree 9 0 R >>",
                "<< /Type /StructElem /S /Span /P 7 0 R /Pg 3 0 R "
                        + "/K << /Type /MCR /Stm 6 0 R /MCID 0 >> >>",
                "<< /Nums [0 [8 0 R]] >>",
                "<< /Type /Annot /Subtype /Stamp /Rect [0 0 100 100] /P 3 0 R /F 0 /AP << /N 5 0 R >> >>",
                streamObject("/Inner Do\n", "/Type /XObject /Subtype /Form /BBox [0 0 100 100] "
                        + "/Resources << /XObject << /Inner 6 0 R >> >> "));
    }

    private static void writeAppearanceInvocationFixture(
            Path target, int invocations, boolean linked, boolean throughOrdinaryForm) throws Exception {
        StringBuilder appearance = new StringBuilder("/P <</MCID 0>> BDC EMC ");
        for (int index = 0; index < invocations; index++) {
            appearance.append("/Target Do ");
        }
        appearance.append('\n');
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 7 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Annots [8 0 R] "
                        + "/Resources << /XObject << /Inner 6 0 R >> >> /Contents 4 0 R >>",
                streamObject("", ""),
                streamObject(appearance.toString(),
                        "/Type /XObject /Subtype /Form /BBox [0 0 100 100] /StructParents 0 "
                                + "/Resources << /XObject << /Target " + (throughOrdinaryForm ? 12 : 6)
                                + " 0 R >> >> "),
                streamObject("/P <</MCID 0>> BDC EMC\n",
                        "/Type /XObject /Subtype /Form /BBox [0 0 100 100] /Resources << >> "
                                + (linked ? "/StructParents 1 " : "")),
                "<< /Type /StructTreeRoot /K [9 0 R " + (linked ? "10 0 R" : "")
                        + "] /ParentTree 11 0 R >>",
                "<< /Type /Annot /Subtype /Stamp /Rect [0 0 100 100] /P 3 0 R /AP << /N 5 0 R >> >>",
                "<< /S /Figure /P 7 0 R /Pg 3 0 R "
                        + "/K << /Type /MCR /Stm 5 0 R /StmOwn 8 0 R /MCID 0 >> >>",
                "<< /S /Figure /P 7 0 R /Pg 3 0 R /K << /Type /MCR /Stm 6 0 R /MCID 0 >> >>",
                "<< /Nums [0 [9 0 R] " + (linked ? "1 [10 0 R]" : "") + "] >>",
                streamObject("/Inner Do\n", "/Type /XObject /Subtype /Form /BBox [0 0 100 100] "
                        + "/Resources << /XObject << /Inner 6 0 R >> >> "));
    }

    private static void writeUnexecutedWholeObjectFixture(Path target, int secondMcid) throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 5 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] "
                        + "/Resources << /XObject << /Fm 4 0 R >> >> >>",
                streamObject("/P <</MCID 0>> BDC EMC /Span /Named BDC EMC\n",
                        "/Type /XObject /Subtype /Form /BBox [0 0 100 100] /StructParent 0 "
                                + "/Resources << /Properties << /Named <</MCID " + secondMcid + ">> >> >> "),
                "<< /Type /StructTreeRoot /K 6 0 R /ParentTree 7 0 R >>",
                "<< /S /Figure /P 5 0 R /Pg 3 0 R /K << /Type /OBJR /Obj 4 0 R >> >>",
                "<< /Nums [0 6 0 R] >>");
    }

    private static void writeUnexecutedObjectNestingFixture(Path target, boolean marked, boolean nested)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 6 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /XObject << /Outer 4 0 R /Inner 5 0 R >> >> >>",
                streamObject(marked ? "/P <</MCID 0>> BDC " + (nested ? "/Inner Do EMC\n" : "EMC /Inner Do\n")
                        : "/Inner Do\n",
                        "/Type /XObject /Subtype /Form /BBox [0 0 100 100] "
                                + (marked ? "/StructParents 0 " : "/StructParent 0 ")
                                + "/Resources << /XObject << /Inner 5 0 R >> >> "),
                streamObject("", "/Type /XObject /Subtype /Form /BBox [0 0 100 100] "
                        + "/StructParent 1 /Resources << >> "),
                "<< /Type /StructTreeRoot /K [7 0 R 8 0 R] /ParentTree 9 0 R >>",
                "<< /S /P /P 6 0 R /Pg 3 0 R /K << /Type /"
                        + (marked ? "MCR /Stm 4 0 R /MCID 0" : "OBJR /Obj 4 0 R") + " >> >>",
                "<< /S /Figure /P 6 0 R /Pg 3 0 R /K << /Type /OBJR /Obj 5 0 R >> >>",
                "<< /Nums [0 " + (marked ? "[7 0 R]" : "7 0 R") + " 1 8 0 R] >>");
    }

    private static void writeScopedMcrFixture(Path target, String invocations) throws Exception {
        writeScopedMcrFixture(target, invocations, "<< /Nums [0 [9 0 R] 1 [10 0 R]] >>");
    }

    private static void writeScopedMcrFixture(Path target, String invocations, String... treeNodes)
            throws Exception {
        java.util.ArrayList<String> objects = new java.util.ArrayList<String>(java.util.Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 7 0 R /MarkInfo << /Marked true >> >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /StructParents 0 "
                        + "/Resources << /Font << /F1 6 0 R >> /XObject << /Fm 5 0 R >> >> /Contents 4 0 R >>",
                streamObject("/P <</MCID 0>> BDC BT /F1 12 Tf (A) Tj ET EMC " + invocations + "\n", ""),
                streamObject("/P <</MCID 0 /ActualText (Bee)>> BDC BT /F1 12 Tf (B) Tj ET EMC\n",
                        "/Type /XObject /Subtype /Form /BBox [0 0 100 100] /StructParents 1 "
                                + "/Resources << /Font << /F1 6 0 R >> >>"),
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
                "<< /Type /StructTreeRoot /K 8 0 R /ParentTree 11 0 R >>",
                "<< /Type /StructElem /S /Document /P 7 0 R /Pg 3 0 R /K [9 0 R 10 0 R] >>",
                "<< /Type /StructElem /S /P /P 8 0 R /Pg 3 0 R /K 0 >>",
                "<< /Type /StructElem /S /P /P 8 0 R /Pg 3 0 R "
                        + "/K << /Type /MCR /Stm 5 0 R /MCID 0 >> >>"));
        objects.addAll(java.util.Arrays.asList(treeNodes));
        writePdf(target, objects.toArray(new String[objects.size()]));
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

    private static void writeRoleFixture(Path target, String role, String mappings) throws Exception {
        writeRoleFixture(target, role, mappings, false);
    }

    private static void writeRoleFixture(Path target, String role, String mappings, boolean pdfTwo) throws Exception {
        writePdf(target,
                "<< /Type /Catalog " + (pdfTwo ? "/Version /2.0 " : "") + "/Pages 2 0 R /StructTreeRoot 4 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << >> >>",
                "<< /Type /StructTreeRoot /K 5 0 R /RoleMap << " + mappings + " >> >>",
                "<< /Type /StructElem /S /" + role + " /P 4 0 R >>");
    }

    private static void writeNamespaceFixture(Path target, String role, String namespace) throws Exception {
        writeNamespaceFixture(target, role, namespace, "");
    }

    private static void writeNamespaceFixture(Path target, String role, String namespace, String rootRoleMap)
            throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Version /2.0 /Pages 2 0 R /StructTreeRoot 4 0 R >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << >> >>",
                "<< /Type /StructTreeRoot /K 5 0 R /Namespaces [6 0 R] /RoleMap << " + rootRoleMap + " >> >>",
                "<< /Type /StructElem /S /" + role + " /NS 6 0 R /P 4 0 R >>",
                namespace);
    }

    private static void writeNamespaceChainFixture(Path target) throws Exception {
        writePdf(target,
                "<< /Type /Catalog /Version /2.0 /Pages 2 0 R /StructTreeRoot 4 0 R /Lang (en-US) >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << >> >>",
                "<< /Type /StructTreeRoot /K 5 0 R /Namespaces [6 0 R 7 0 R 8 0 R 6 0 R] "
                        + "/RoleMap << /Title /Span >> >>",
                "<< /Type /StructElem /S /Story /NS 6 0 R /P 4 0 R /Lang (fr-CA) /K [9 0 R 10 0 R 11 0 R] >>",
                "<< /Type /Namespace /NS (urn:folio:story) /RoleMapNS << /Story [/Chapter 7 0 R] /Term /P >> >>",
                "<< /NS (urn:folio:chapter) /RoleMapNS << /Chapter [/Title 8 0 R] >> >>",
                "<< /NS (http://iso.org/pdf2/ssn) >>",
                "<< /S /Term /NS 6 0 R /P 5 0 R >>",
                "<< /S /Title /P 5 0 R >>",
                "<< /S /H7 /NS 8 0 R /P 5 0 R >>");
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
                requestBuilder()
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
        return requestBuilder()
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

    private static WorkflowRequest.Builder requestBuilder() {
        return WorkflowRequest.builder().executionProfile(WorkflowExecutionProfile.valueOf(
                System.getProperty("folio.t13.executionProfile", "IN_PROCESS")));
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
        // Root K + element K + ParentTree node + number-tree value + MCID slot.
        private int structureItems = 5;
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
