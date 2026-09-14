package net.zerocloud.pdf.itext7.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.zerocloud.pdf.CharacterMapping;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ExtractionLimits;
import net.zerocloud.pdf.ExtractionDiagnostic;
import net.zerocloud.pdf.LogicalStructureElement;
import net.zerocloud.pdf.LogicalStructureItem;
import net.zerocloud.pdf.MarkedContentReference;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PageText;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.TextItem;
import net.zerocloud.pdf.TextStructureExtraction;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfPage;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;
import net.zerocloud.pdf.itext7.kernel.pdf.canvas.parser.PdfTextExtractor;
import net.zerocloud.pdf.query.ExtractTextAndStructure;
import net.zerocloud.pdf.query.PageObjectReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class TextStructureFacadeTest {

    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void completeExtractionRetainsNativeValuesAfterCloseWithoutChangingTheSource() throws Exception {
        Path source = temporary.getRoot().toPath().resolve("detached-text.pdf");
        createTextFixture(source);
        byte[] original = Files.readAllBytes(source);
        TextStructureExtraction detached;
        PdfDocument document = new PdfDocument(new PdfReader(source.toString()));
        try {
            detached = document.getTextAndStructure(limits());
        } finally {
            document.close();
        }
        assertTrue(document.getPublicationReceipts().isEmpty());
        assertEquals(2, detached.getPages().size());
        assertEquals("AB", detached.getPages().get(0).getText());
        assertEquals("DE", detached.getPages().get(1).getText());
        assertEquals(90, detached.getPages().get(1).getRotation());
        assertTrue(detached.getStructureRoots().isEmpty());
        assertTrue(detached.getDiagnostics().isEmpty());
        TextItem first = detached.getPages().get(0).getTextItems().get(0);
        assertEquals(new BigDecimal("10"), first.getGeometry().getE());
        assertEquals(new BigDecimal("20"), first.getGeometry().getF());
        assertEquals(CharacterMapping.Confidence.EXPLICIT, first.getCharacterMapping().getConfidence());
        byte[] sourceCode = first.getCharacterMapping().getSourceCode();
        sourceCode[0] = 0;
        assertArrayEquals(new byte[] {0x41}, first.getCharacterMapping().getSourceCode());
        WorkflowOutcome<TextStructureExtraction> nativeOutcome = new DocumentWorkflow().execute(
                WorkflowRequest.open(source, SaveMode.REWRITE),
                session -> session.query(ExtractTextAndStructure.version1(limits())));
        assertTrue(nativeOutcome.getPublicationReceipts().isEmpty());
        assertPagesMatch(nativeOutcome.getResult(), detached);
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test
    public void pageTextFollowsPageIdentityAndEarlierCommandsThroughPublication() throws Exception {
        Path source = temporary.getRoot().toPath().resolve("page-source.pdf");
        Path target = temporary.getRoot().toPath().resolve("page-target.pdf");
        createTextFixture(source);
        byte[] original = Files.readAllBytes(source);
        PageText detached;
        PdfDocument document = new PdfDocument(new PdfReader(source.toString()), new PdfWriter(target.toString()));
        try {
            PdfPage retained = document.getPage(1);
            document.movePage(1, 2);
            detached = retained.getPageText(limits());
            assertEquals(2, detached.getPageNumber());
            assertEquals("AB", detached.getText());
            assertEquals("DE", document.getPage(1).getPageText(limits()).getText());
            expectFailure(DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED,
                    () -> retained.getPageText(limitsBuilder().maximumPages(1).build()));
            assertEquals("AB", retained.getPageText(limits()).getText());
        } finally {
            document.close();
        }
        assertEquals("AB", detached.getText());
        assertEquals(PublicationStatus.COMMITTED, document.getPublicationReceipts().get(0).getStatus());
        TextStructureExtraction reopened = new DocumentWorkflow().execute(
                WorkflowRequest.open(target, SaveMode.REWRITE),
                session -> session.query(ExtractTextAndStructure.version1(limits()))).getResult();
        assertEquals("DE", reopened.getPages().get(0).getText());
        assertEquals("AB", reopened.getPages().get(1).getText());
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test
    public void boundedStringExtractionRequiresTheWholeDocumentToFit() throws Exception {
        Path source = temporary.getRoot().toPath().resolve("bounded-string.pdf");
        createTextFixture(source);
        try (PdfDocument document = new PdfDocument(new PdfReader(source.toString()))) {
            PdfPage first = document.getPage(1);
            assertEquals("AB", PdfTextExtractor.getTextFromPage(first,
                    limitsBuilder().maximumTextItems(4).build()));
            expectFailure(DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED,
                    () -> PdfTextExtractor.getTextFromPage(first, limitsBuilder().maximumTextItems(3).build()));
            assertEquals("DE", PdfTextExtractor.getTextFromPage(document.getPage(2), limits()));
        }
    }

    @Test
    public void convenienceStringExtractionUsesFiniteDocumentWideDefaults() throws Exception {
        Path source = temporary.getRoot().toPath().resolve("default-string.pdf");
        createTextFixture(source);
        try (PdfDocument document = new PdfDocument(new PdfReader(source.toString()))) {
            assertEquals("AB", PdfTextExtractor.getTextFromPage(document.getPage(1)));
        }
        for (int depth : new int[] {128, 129}) {
            createStructureDepthFixture(source, depth);
            try (PdfDocument document = new PdfDocument(new PdfReader(source.toString()))) {
                PdfPage page = document.getPage(1);
                if (depth == 128) {
                    assertEquals("", PdfTextExtractor.getTextFromPage(page));
                } else {
                    expectFailure(DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED,
                            () -> PdfTextExtractor.getTextFromPage(page));
                }
                assertEquals("", PdfTextExtractor.getTextFromPage(page,
                        limitsBuilder().maximumStructureDepth(129).build()));
            }
        }
    }

    @Test
    public void boundedStructureRootsRetainRolesLanguagesReplacementsAndOrderedContent() throws Exception {
        Path source = temporary.getRoot().toPath().resolve("tagged-roots.pdf");
        createTaggedFixture(source);
        byte[] original = Files.readAllBytes(source);
        List<LogicalStructureElement> roots;
        try (PdfDocument document = new PdfDocument(new PdfReader(source.toString()))) {
            roots = document.getStructTreeRoot(limitsBuilder().maximumStructureElements(3).build());
            assertEquals("Actual", PdfTextExtractor.getTextFromPage(document.getPage(1)));
            PageText page = document.getPage(1).getPageText(limits());
            assertEquals(7, page.getTextItems().size());
            assertEquals("content alternate", page.getMarkedContentSequences().get(0).getAlternateText().get());
            assertEquals("de-DE", page.getMarkedContentSequences().get(0).getLanguage().get());
            expectFailure(DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED,
                    () -> document.getStructTreeRoot(limitsBuilder().maximumStructureElements(2).build()));
        }
        assertEquals(1, roots.size());
        LogicalStructureElement root = roots.get(0);
        assertEquals("Document", root.getResolvedRole().get());
        assertEquals("en-US", root.getEffectiveLanguage().get());
        assertEquals(LogicalStructureElement.LanguageSource.DOCUMENT, root.getLanguageSource());
        LogicalStructureElement story = root.getChildren().get(0).getElement().get();
        assertEquals("Story", story.getRole());
        assertEquals("Sect", story.getResolvedRole().get());
        assertEquals(LogicalStructureElement.RoleResolution.ROLE_MAP, story.getRoleResolution());
        assertEquals("Story alternative", story.getAlternateText().get());
        assertEquals("Structure replacement", story.getActualText().get());
        LogicalStructureElement span = story.getChildren().get(0).getElement().get();
        assertFalse(span.getDeclaredLanguage().isPresent());
        assertEquals("fr-CA", span.getEffectiveLanguage().get());
        assertEquals(LogicalStructureElement.LanguageSource.ANCESTOR, span.getLanguageSource());
        LogicalStructureItem item = span.getChildren().get(0);
        assertEquals(LogicalStructureItem.Kind.MARKED_CONTENT, item.getKind());
        MarkedContentReference reference = item.getMarkedContent().get();
        assertEquals(1, reference.getPageNumber());
        assertEquals(0, reference.getMarkedContentId());
        assertEquals(Integer.valueOf(1), reference.getMarkedContentSequenceId().get());
        try {
            roots.clear();
            fail("Returned structure roots must be immutable");
        } catch (UnsupportedOperationException expected) {
            assertEquals(1, roots.size());
        }
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test
    public void convenienceStructureRootsUseFiniteDefaultsWithoutCreatingAnAbsentTree() throws Exception {
        Path source = temporary.getRoot().toPath().resolve("default-roots.pdf");
        createTextFixture(source);
        byte[] original = Files.readAllBytes(source);
        try (PdfDocument document = new PdfDocument(new PdfReader(source.toString()))) {
            assertTrue(document.getStructTreeRoot().isEmpty());
            assertTrue(document.getStructTreeRoot(limits()).isEmpty());
            assertFalse(document.getCatalog().getPdfObject().containsKey(
                    new net.zerocloud.pdf.itext7.kernel.pdf.PdfName("StructTreeRoot")));
        }
        assertArrayEquals(original, Files.readAllBytes(source));
        for (int depth : new int[] {128, 129}) {
            createStructureDepthFixture(source, depth);
            try (PdfDocument document = new PdfDocument(new PdfReader(source.toString()))) {
                if (depth == 128) {
                    LogicalStructureElement current = document.getStructTreeRoot().get(0);
                    for (int index = 1; index < depth; index++) {
                        assertEquals("Div", current.getRole());
                        current = current.getChildren().get(0).getElement().get();
                    }
                    assertEquals("Div", current.getRole());
                    assertTrue(current.getChildren().isEmpty());
                } else {
                    expectFailure(DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED,
                            () -> document.getStructTreeRoot());
                }
                assertEquals(1, document.getStructTreeRoot(
                        limitsBuilder().maximumStructureDepth(129).build()).size());
            }
        }
    }

    @Test
    public void everyFacadeEntryRejectsMalformedLaterContentWithoutReturningAPrefix() throws Exception {
        Path source = temporary.getRoot().toPath().resolve("malformed-later-page.pdf");
        writePdf(source,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 7 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 7 0 R >> >> /Contents 6 0 R >>",
                streamObject("BT /F1 12 Tf (Visible prefix) Tj ET\n"),
                streamObject("BT /F1 12 Tf (private-extraction-payload) Tj\n"),
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        byte[] original = Files.readAllBytes(source);
        try {
            new DocumentWorkflow().execute(WorkflowRequest.open(source, SaveMode.REWRITE),
                    session -> session.query(ExtractTextAndStructure.version1(limits())));
            fail("Native extraction must reject the unbalanced later text object");
        } catch (DocumentFailure expected) {
            assertEquals(DocumentFailureCode.QUERY_FAILED, expected.getCode());
            assertTrue(expected.getPublicationReceipts().isEmpty());
        }
        PdfDocument document = new PdfDocument(new PdfReader(source.toString()));
        try {
            for (Runnable operation : allEntries(document, document.getPage(1))) {
                expectFailure(DocumentFailureCode.QUERY_FAILED, operation);
            }
        } finally {
            document.close();
        }
        assertTrue(document.getPublicationReceipts().isEmpty());
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test
    public void mappingEvidenceAndDiagnosticsRemainUncertainAcrossAllTextViews() throws Exception {
        Path source = temporary.getRoot().toPath().resolve("uncertain-mappings.pdf");
        writePdf(source,
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 5 0 R /F2 6 0 R /F3 7 0 R /F4 8 0 R >> >> "
                        + "/Contents 4 0 R >>",
                streamObject("BT /F1 10 Tf (A) Tj /F2 10 Tf (B) Tj /F3 10 Tf (C) Tj /F4 10 Tf (D) Tj ET\n"),
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding /ToUnicode 9 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /ToUnicode 10 0 R >>",
                streamObject(unicodeMapping("41", "005A")),
                streamObject(unicodeMapping("44", "03A9")));
        byte[] original = Files.readAllBytes(source);
        TextStructureExtraction detached;
        try (PdfDocument document = new PdfDocument(new PdfReader(source.toString()))) {
            PdfPage page = document.getPage(1);
            detached = document.getTextAndStructure(limits());
            assertEquals("C\u03a9", PdfTextExtractor.getTextFromPage(page));
            assertEquals("C\u03a9", PdfTextExtractor.getTextFromPage(page, limits()));
            assertEquals("C\u03a9", page.getPageText(limits()).getText());
        }
        List<TextItem> items = detached.getPages().get(0).getTextItems();
        CharacterMapping.Confidence[] confidence = {CharacterMapping.Confidence.CONTRADICTORY,
            CharacterMapping.Confidence.MISSING, CharacterMapping.Confidence.INFERRED,
            CharacterMapping.Confidence.EXPLICIT};
        assertEquals(4, items.size());
        for (int index = 0; index < items.size(); index++) {
            assertEquals(confidence[index], items.get(index).getCharacterMapping().getConfidence());
            assertArrayEquals(new byte[] {(byte) (0x41 + index)}, items.get(index).getCharacterMapping().getSourceCode());
        }
        assertFalse(items.get(0).getUnicode().isPresent());
        assertEquals("Z", items.get(0).getCharacterMapping().getExplicitUnicode().get());
        assertEquals("A", items.get(0).getCharacterMapping().getInferredUnicode().get());
        assertFalse(items.get(1).getUnicode().isPresent());
        assertEquals(2, detached.getDiagnostics().size());
        assertEquals(ExtractionDiagnostic.Code.CONTRADICTORY_UNICODE_MAPPING, detached.getDiagnostics().get(0).getCode());
        assertEquals(ExtractionDiagnostic.Code.MISSING_UNICODE_MAPPING, detached.getDiagnostics().get(1).getCode());
        TextStructureExtraction nativeResult = new DocumentWorkflow().execute(
                WorkflowRequest.open(source, SaveMode.REWRITE),
                session -> session.query(ExtractTextAndStructure.version1(limits()))).getResult();
        assertPagesMatch(nativeResult, detached);
        for (int index = 0; index < detached.getDiagnostics().size(); index++) {
            ExtractionDiagnostic left = nativeResult.getDiagnostics().get(index);
            ExtractionDiagnostic right = detached.getDiagnostics().get(index);
            assertEquals(left.getCode(), right.getCode());
            assertEquals(left.getPageNumber(), right.getPageNumber());
            assertEquals(left.getTextItemIndex(), right.getTextItemIndex());
            assertEquals(left.getMessage(), right.getMessage());
            assertArrayEquals(left.getSourceCode(), right.getSourceCode());
        }
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test
    public void callerStreamsRemainOpenAndClosedHandlesRejectEveryExtractionEntry() throws Exception {
        Path source = temporary.getRoot().toPath().resolve("owned-source.pdf");
        createTaggedFixture(source);
        byte[] original = Files.readAllBytes(source);
        CallerInput input = new CallerInput(original);
        CallerOutput output = new CallerOutput();
        PdfDocument document = new PdfDocument(new PdfReader(input), new PdfWriter(output));
        PdfPage page;
        TextStructureExtraction detached;
        try {
            page = document.getPage(1);
            detached = document.getTextAndStructure(limits());
            assertEquals(0, output.size());
        } finally {
            document.close();
        }
        assertFalse(input.closed);
        assertFalse(output.closed);
        assertTrue(output.size() > 0);
        assertEquals(PublicationStatus.COMMITTED, document.getPublicationReceipts().get(0).getStatus());
        for (Runnable operation : allEntries(document, page)) {
            try {
                operation.run();
                fail("A closed document or page handle cannot extract text");
            } catch (IllegalStateException expected) {
                assertEquals("The facade document is closed.", expected.getMessage());
            }
        }
        assertEquals("Actual", detached.getPages().get(0).getText());
        assertEquals("Document", detached.getStructureRoots().get(0).getRole());
        try (PdfDocument reopened = new PdfDocument(new PdfReader(new ByteArrayInputStream(output.toByteArray())))) {
            assertEquals("Actual", PdfTextExtractor.getTextFromPage(reopened.getPage(1)));
            assertEquals("Document", reopened.getStructTreeRoot().get(0).getRole());
        }
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    private static Runnable[] allEntries(PdfDocument document, PdfPage page) {
        return new Runnable[] {
            () -> document.getTextAndStructure(limits()),
            () -> document.getStructTreeRoot(),
            () -> document.getStructTreeRoot(limits()),
            () -> page.getPageText(limits()),
            () -> PdfTextExtractor.getTextFromPage(page),
            () -> PdfTextExtractor.getTextFromPage(page, limits())
        };
    }

    private static String unicodeMapping(String source, String destination) {
        return "begincmap\n/CMapName /FolioT13FacadeMapping def\n/CMapType 2 def\n"
                + "1 begincodespacerange\n<00> <FF>\nendcodespacerange\n1 beginbfchar\n<"
                + source + "> <" + destination + ">\nendbfchar\nendcmap\n";
    }

    private static final class CallerInput extends ByteArrayInputStream {
        private boolean closed;
        CallerInput(byte[] bytes) { super(bytes); }
        @Override public void close() { closed = true; }
    }

    private static final class CallerOutput extends ByteArrayOutputStream {
        private boolean closed;
        @Override public void close() { closed = true; }
    }

    private static void expectFailure(DocumentFailureCode code, Runnable operation) {
        try {
            operation.run();
            fail("Expected the complete extraction to fail");
        } catch (PdfException expected) {
            DocumentFailure failure = (DocumentFailure) expected.getCause();
            assertEquals(code, failure.getCode());
            assertEquals("document.text-structure.extract", failure.getCapabilityId());
            assertEquals(code.name() + ": " + failure.getDiagnostic(), expected.getMessage());
            assertFalse(expected.getMessage().contains("private-extraction-payload"));
        }
    }

    private static void assertPagesMatch(TextStructureExtraction expected, TextStructureExtraction actual) {
        assertEquals(expected.getPages().size(), actual.getPages().size());
        for (int number = 0; number < expected.getPages().size(); number++) {
            PageText left = expected.getPages().get(number);
            PageText right = actual.getPages().get(number);
            assertEquals(left.getPageNumber(), right.getPageNumber());
            assertEquals(left.getRotation(), right.getRotation());
            assertEquals(left.getUserUnit(), right.getUserUnit());
            assertEquals(left.getCropBoxLeft(), right.getCropBoxLeft());
            assertEquals(left.getCropBoxBottom(), right.getCropBoxBottom());
            assertEquals(left.getCropBoxRight(), right.getCropBoxRight());
            assertEquals(left.getCropBoxTop(), right.getCropBoxTop());
            assertEquals(left.getText(), right.getText());
            assertEquals(left.getTextItems().size(), right.getTextItems().size());
            for (int index = 0; index < left.getTextItems().size(); index++) {
                TextItem a = left.getTextItems().get(index);
                TextItem b = right.getTextItems().get(index);
                assertEquals(a.getIndex(), b.getIndex());
                assertEquals(a.getGeometry(), b.getGeometry());
                assertEquals(a.getTextContribution(), b.getTextContribution());
                assertEquals(a.getMarkedContentSequenceIds(), b.getMarkedContentSequenceIds());
                CharacterMapping x = a.getCharacterMapping();
                CharacterMapping y = b.getCharacterMapping();
                assertArrayEquals(x.getSourceCode(), y.getSourceCode());
                assertEquals(x.getConfidence(), y.getConfidence());
                assertEquals(x.getUnicode(), y.getUnicode());
                assertEquals(x.getExplicitUnicode(), y.getExplicitUnicode());
                assertEquals(x.getInferredUnicode(), y.getInferredUnicode());
            }
        }
    }

    // Reuses the project-owned T13 deterministic page/stream and matrix fixture.
    private static void createTextFixture(Path target) throws Exception {
        new DocumentWorkflow().execute(WorkflowRequest.create(target, SaveMode.REWRITE), session -> {
            session.execute(AddBlankPage.INSTANCE);
            session.execute(AddBlankPage.INSTANCE);
            ObjectReference first = session.query(PageObjectReference.version1(1));
            ObjectReference second = session.query(PageObjectReference.version1(2));
            PdfDictionary resources = resources();
            session.execute(DocumentPatch.builder()
                    .setDictionaryEntry(first, PdfName.of("Resources"), resources)
                    .setDictionaryEntry(first, PdfName.of("Contents"), PdfArray.of(
                            content("BT /F1 12 Tf 10 20 Td (A) Tj ET\n"),
                            content("BT /F1 12 Tf 30 40 Td (B) Tj ET\n")))
                    .setDictionaryEntry(second, PdfName.of("Resources"), resources)
                    .setDictionaryEntry(second, PdfName.of("Contents"), content(
                            "q 2 .5 -.25 3 40 50 cm BT /F1 10 Tf (D) Tj ET Q\n"
                                    + "BT /F1 10 Tf 0 1 -1 0 100 200 Tm (E) Tj ET\n"))
                    .setDictionaryEntry(second, PdfName.of("Rotate"), PdfNumber.of(90L))
                    .build());
            return null;
        });
    }

    private static PdfDictionary resources() {
        String cmap = "/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n"
                + "/CMapName /FolioT13Facade def\n/CMapType 2 def\n"
                + "1 begincodespacerange\n<00> <FF>\nendcodespacerange\n"
                + "5 beginbfchar\n<41> <0041>\n<42> <0042>\n<43> <0043>\n"
                + "<44> <0044>\n<45> <0045>\nendbfchar\nendcmap\nend\nend\n";
        PdfDictionary font = PdfDictionary.builder()
                .put(PdfName.of("Type"), PdfName.of("Font"))
                .put(PdfName.of("Subtype"), PdfName.of("Type1"))
                .put(PdfName.of("BaseFont"), PdfName.of("Helvetica"))
                .put(PdfName.of("Encoding"), PdfName.of("WinAnsiEncoding"))
                .put(PdfName.of("ToUnicode"), content(cmap)).build();
        return PdfDictionary.builder().put(PdfName.of("Font"),
                PdfDictionary.builder().put(PdfName.of("F1"), font).build()).build();
    }

    private static PdfStream content(String operators) {
        return PdfStream.of(PdfDictionary.builder().build(), operators.getBytes(StandardCharsets.US_ASCII));
    }

    private static void createStructureDepthFixture(Path target, int depth) throws Exception {
        List<String> objects = new ArrayList<String>();
        objects.add("<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 4 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << >> >>");
        objects.add("<< /Type /StructTreeRoot /K 5 0 R >>");
        for (int index = 0; index < depth; index++) {
            objects.add("<< /Type /StructElem /S /Div /P " + (index + 4) + " 0 R"
                    + (index + 1 < depth ? " /K " + (index + 6) + " 0 R" : "") + " >>");
        }
        writePdf(target, objects.toArray(new String[objects.size()]));
    }

    // Reuses the Native T13 role/language/ActualText fixture and its literal observations.
    private static void createTaggedFixture(Path target) throws Exception {
        String program = "/Span <</MCID 0 /Lang (de-DE) /Alt (content alternate) "
                + "/ActualText (Actual)>> BDC BT /F1 12 Tf (Visible) Tj ET EMC\n";
        writePdf(target,
                "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 6 0 R "
                        + "/MarkInfo << /Marked true >> /Lang (en-US) >>",
                "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R /StructParents 0 >>",
                streamObject(program),
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
                "<< /Type /StructTreeRoot /K 7 0 R /RoleMap << /Story /Chapter /Chapter /Sect >> "
                        + "/ParentTree 10 0 R /ParentTreeNextKey 1 >>",
                "<< /Type /StructElem /S /Document /P 6 0 R /K 8 0 R >>",
                "<< /S /Story /P 7 0 R /Lang (fr-CA) /Alt (Story alternative) "
                        + "/ActualText (Structure replacement) /K 9 0 R >>",
                "<< /Type /StructElem /S /Span /P 8 0 R /Pg 3 0 R "
                        + "/K << /Type /MCR /Pg 3 0 R /MCID 0 >> >>",
                "<< /Nums [0 [9 0 R]] >>");
    }

    private static String streamObject(String program) {
        return "<< /Length " + program.getBytes(StandardCharsets.US_ASCII).length
                + " >>\nstream\n" + program + "endstream";
    }

    // The project-owned T13 fixtures use this original, byte-counted PDF writer.
    private static void writePdf(Path target, String... objects) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII));
        int[] offsets = new int[objects.length + 1];
        for (int index = 0; index < objects.length; index++) {
            offsets[index + 1] = output.size();
            output.write(((index + 1) + " 0 obj\n" + objects[index] + "\nendobj\n")
                    .getBytes(StandardCharsets.US_ASCII));
        }
        int xref = output.size();
        output.write(("xref\n0 " + offsets.length + "\n0000000000 65535 f \n")
                .getBytes(StandardCharsets.US_ASCII));
        for (int index = 1; index < offsets.length; index++) {
            output.write(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[index])
                    .getBytes(StandardCharsets.US_ASCII));
        }
        output.write(("trailer\n<< /Size " + offsets.length + " /Root 1 0 R >>\nstartxref\n"
                + xref + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII));
        Files.write(target, output.toByteArray());
    }

    private static ExtractionLimits limits() {
        return limitsBuilder().build();
    }

    private static ExtractionLimits.Builder limitsBuilder() {
        return ExtractionLimits.builder().maximumPages(10).maximumPageTreeNodes(100)
                .maximumContentStreams(100).maximumContentStreamDepth(16).maximumDecodedBytes(1L << 20)
                .maximumTextItems(10000).maximumUnicodeCodePoints(100000).maximumToUnicodeMappings(1000)
                .maximumFontDataEntries(1000).maximumMarkedContentSequences(1000).maximumMarkedContentDepth(32)
                .maximumStructureElements(1000).maximumStructureItems(10000).maximumStructureDepth(32)
                .maximumRoleMappings(1000);
    }
}
