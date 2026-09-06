package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import net.zerocloud.pdf.CharacterMapping;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ExtractionLimits;
import net.zerocloud.pdf.FontResource;
import net.zerocloud.pdf.ImageByteAccess;
import net.zerocloud.pdf.PageText;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.ResourceExtractionLimits;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.TextItem;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.WorkflowResourcePolicy;
import net.zerocloud.pdf.composition.CanvasResourceLimits;
import net.zerocloud.pdf.composition.CompositionLimits;
import net.zerocloud.pdf.composition.FontLimits;
import net.zerocloud.pdf.composition.FontSelection;
import net.zerocloud.pdf.composition.FontSource;
import net.zerocloud.pdf.composition.LayoutPage;
import net.zerocloud.pdf.composition.PageMargins;
import net.zerocloud.pdf.composition.Paragraph;
import net.zerocloud.pdf.composition.ParagraphFlow;
import net.zerocloud.pdf.composition.Table;
import net.zerocloud.pdf.composition.TableCell;
import net.zerocloud.pdf.composition.TableLimits;
import net.zerocloud.pdf.composition.TableRow;
import net.zerocloud.pdf.composition.TableWidth;
import net.zerocloud.pdf.composition.TabStop;
import net.zerocloud.pdf.composition.command.ComposeParagraphs;
import net.zerocloud.pdf.query.ExtractTextAndStructure;
import net.zerocloud.pdf.query.ExtractImagesAndResources;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Unicode contracts observed through public commands, queries and reopened publication. */
@RunWith(Parameterized.class)
public final class UnicodeCompositionWorkflowTest {
    private static final double TOLERANCE = 0.0001;
    private static final String FONT_ROOT = "/net/zerocloud/pdf/fixtures/noto/";
    private final WorkflowExecutionProfile profile;
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> profiles() {
        return Arrays.asList(new Object[][] {{WorkflowExecutionProfile.IN_PROCESS},
            {WorkflowExecutionProfile.HARDENED_WORKER}});
    }

    public UnicodeCompositionWorkflowTest(WorkflowExecutionProfile profile) { this.profile = profile; }

    @Test
    public void staticNotoSansPreservesLatinGreekAndCyrillicThroughPublication() throws Exception {
        ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(font("NotoSans-Regular.ttf")))
                .page(page()).paragraph(Paragraph.version1(48).text("A\u03a9\u042f", 12).build()).build();
        PageText text = publishAndRead(flow, "NotoSans-Regular").get(0);
        assertEquals("A\u03a9\u042f", text.getText());
        // Noto Sans 2.008 source hmtx: A=639, Omega=717, Ya=639; head yMax=1067.
        position(text, 0, 72, 707.196, 7.668);
        position(text, 1, 79.668, 707.196, 8.604);
        position(text, 2, 88.272, 707.196, 7.668);
    }

    @Test
    public void combiningClusterAcrossInlinesCannotSplitAtItsSpace() throws Exception {
        ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(font("NotoSans-Regular.ttf")))
                .page(page()).paragraph(Paragraph.version1(48).text("A ", 12)
                        .text("\u0301BB", 12).maximumWidth(12).build()).build();
        PageText text = publishAndRead(flow, "NotoSans-Regular").get(0);
        assertEquals("A \u0301BB", text.getText());
        position(text, 0, 72, 707.196, 7.668);
        position(text, 1, 79.668, 707.196, 3.12);
        position(text, 2, 82.788, 707.196, 0);
        position(text, 3, 72, 659.196, 7.8);
        position(text, 4, 72, 611.196, 7.8);
    }

    @Test
    public void unicodeLineOpportunityPrecedesEmergencyGraphemeWrapping() throws Exception {
        ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(font("NotoSans-Regular.ttf")))
                .page(page()).paragraph(Paragraph.version1(48).text("A-B C", 12).maximumWidth(22).build()).build();
        PageText text = publishAndRead(flow, "NotoSans-Regular").get(0);
        assertEquals("A-B C", text.getText());
        position(text, 0, 72, 707.196, 7.668);
        position(text, 1, 79.668, 707.196, 3.864);
        position(text, 2, 72, 659.196, 7.8);
        position(text, 3, 79.8, 659.196, 3.12);
        position(text, 4, 82.92, 659.196, 7.584);
    }

    @Test
    public void mixedBidiRunsKeepNumbersAndPunctuationInTheReferenceOrder() throws Exception {
        ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(font("NotoSans-Regular.ttf"),
                font("NotoSansHebrew-Regular.ttf"))).page(page())
                .paragraph(Paragraph.version1(48).text("A \u05d0\u05d1 12, B", 12).build()).build();
        PageText text = publishAndRead(flow, "NotoSans-Regular", "NotoSansHebrew-Regular").get(0);
        assertEquals("A 12 \u05d1\u05d0, B", text.getText());
        position(text, 2, 82.788, 707.196, 6.864);
        position(text, 3, 89.652, 707.196, 6.864);
        position(text, 5, 99.636, 707.196, 6.864);
        position(text, 6, 106.5, 707.196, 7.584);
    }

    @Test
    public void rightToLeftBaseMirrorsPairedPunctuationWithoutReversingDigits() throws Exception {
        ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(font("NotoSans-Regular.ttf"),
                font("NotoSansHebrew-Regular.ttf"))).page(page())
                .paragraph(Paragraph.version1(48).text("\u05d0\u05d1 (12) A", 12).build()).build();
        PageText text = publishAndRead(flow, "NotoSans-Regular", "NotoSansHebrew-Regular").get(0);
        assertEquals("A (12) \u05d1\u05d0", text.getText());
        assertEquals("(", text.getTextItems().get(2).getUnicode().get());
        assertEquals(")", text.getTextItems().get(5).getUnicode().get());
    }

    @Test
    public void literalObjectReplacementCharacterIsTextRatherThanAnInlineGraphic() throws Exception {
        ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(font("NotoSans-Regular.ttf")))
                .page(page()).paragraph(Paragraph.version1(48).text("\ufffcA", 12).build()).build();
        assertEquals("\ufffcA", publishAndRead(flow, "NotoSans-Regular").get(0).getText());
    }

    @Test
    public void wordAndScriptRunsWrapAtUnicodeOpportunitiesUnderRejectOverflow() throws Exception {
        ParagraphFlow flow = ParagraphFlow.version2(FontSelection.explicit(font("NotoSans-Regular.ttf")))
                .page(page()).paragraph(Paragraph.version2(48).text("A-\u03a9 \u042f", 12)
                        .overflow(Paragraph.Overflow.REJECT).maximumWidth(18).build()).build();
        PageText text = publishAndRead(flow, "NotoSans-Regular").get(0);
        assertEquals("A-\u03a9 \u042f", text.getText());
        position(text, 0, 72, 707.196, 7.668);
        position(text, 1, 79.668, 707.196, 3.864);
        position(text, 2, 72, 659.196, 8.604);
        position(text, 3, 80.604, 659.196, 3.12);
        position(text, 4, 72, 611.196, 7.668);
    }

    @Test
    public void nonWrappingOverflowPreservesSpacesUntilTheUnicodeLineOpportunity() throws Exception {
        FontSelection fonts = FontSelection.explicit(font("NotoSans-Regular.ttf"));
        ParagraphFlow reject = ParagraphFlow.version2(fonts).page(page()).paragraph(Paragraph.version2(48)
                .text("A B", 12).maximumWidth(8).overflow(Paragraph.Overflow.REJECT).build()).build();
        expectFailure(reject, limits(2), WorkflowResourcePolicy.safeDefaults(),
                DocumentFailureCode.COMPOSITION_AREA_EXHAUSTED);
        ParagraphFlow visible = ParagraphFlow.version2(fonts).page(page()).paragraph(Paragraph.version2(48)
                .text("A B", 12).maximumWidth(8).overflow(Paragraph.Overflow.VISIBLE).build()).build();
        PageText text = publishAndRead(visible, "NotoSans-Regular").get(0);
        assertEquals("A B", text.getText());
        position(text, 0, 72, 707.196, 7.668);
        position(text, 1, 79.668, 707.196, 3.12);
        position(text, 2, 72, 659.196, 7.8);
    }

    @Test
    public void explicitBidiIsolatesAffectOrderWithoutRequestingControlGlyphs() throws Exception {
        ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(font("NotoSans-Regular.ttf"),
                font("NotoSansHebrew-Regular.ttf"))).page(page())
                .paragraph(Paragraph.version1(48).text("A \u2067\u05d0\u05d1 12\u2069 Z", 12).build()).build();
        assertEquals("A 12 \u05d1\u05d0 Z", publishAndRead(flow,
                "NotoSans-Regular", "NotoSansHebrew-Regular").get(0).getText());
    }

    @Test
    public void unicodeLineAndParagraphSeparatorsForceLinesWithoutDrawingControlGlyphs() throws Exception {
        ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(font("NotoSans-Regular.ttf")))
                .page(page()).paragraph(Paragraph.version1(48).text("A\u2028B\u2029C", 12).build()).build();
        PageText text = publishAndRead(flow, "NotoSans-Regular").get(0);
        assertEquals("ABC", text.getText());
        position(text, 0, 72, 707.196, 7.668);
        position(text, 1, 72, 659.196, 7.8);
        position(text, 2, 72, 611.196, 7.584);
    }

    @Test
    public void justificationExpandsAfterTheWholeSpaceCombiningCluster() throws Exception {
        ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(font("NotoSans-Regular.ttf")))
                .page(page()).paragraph(Paragraph.version1(48).text("A \u0301B C", 12)
                        .alignment(Paragraph.Alignment.JUSTIFIED).maximumWidth(24).build()).build();
        PageText text = publishAndRead(flow, "NotoSans-Regular").get(0);
        assertEquals("A \u0301B C", text.getText());
        position(text, 1, 79.668, 707.196, 3.12);
        position(text, 2, 82.788, 707.196, 0);
        position(text, 3, 85.08, 707.196, 7.8);
        position(text, 5, 72, 659.196, 7.584);
    }

    @Test
    public void rightToLeftTabFieldKeepsItsExplicitStopAndClusterOrder() throws Exception {
        ParagraphFlow flow = ParagraphFlow.version2(FontSelection.explicit(font("NotoSans-Regular.ttf"),
                font("NotoSansHebrew-Regular.ttf"))).page(page())
                .paragraph(Paragraph.version2(48).text("A\t\u05d0\u05d1 12", 12).tabInterval(36).build()).build();
        PageText text = publishAndRead(flow, "NotoSans-Regular", "NotoSansHebrew-Regular").get(0);
        assertEquals("A12 \u05d1\u05d0", text.getText());
        position(text, 1, 108, 707.196, 6.864);
        position(text, 2, 114.864, 707.196, 6.864);
    }

    @Test
    public void bidiTabAnchorsUseVisualOffsetsAndFieldsKeepTheirPhysicalPositions() throws Exception {
        FontSelection fonts = FontSelection.explicit(font("NotoSans-Regular.ttf"), font("NotoSansHebrew-Regular.ttf"));
        ParagraphFlow anchored = ParagraphFlow.version2(fonts).page(page()).paragraph(Paragraph.version2(48)
                .text("A\t\u05d0\u05d1" + "12", 12).tabStop(TabStop.anchored(36, '\u05d0')).build()).build();
        PageText anchorText = publishAndRead(anchored, "NotoSans-Regular", "NotoSansHebrew-Regular").get(0);
        assertEquals("A12\u05d1\u05d0", anchorText.getText());
        position(anchorText, 1, 87.408, 707.196, 6.864);
        position(anchorText, 4, 108, 707.196, 7.584);

        ParagraphFlow rtlBase = ParagraphFlow.version2(fonts).page(page()).paragraph(Paragraph.version2(48)
                .text("\u05d0\u05d1\t12", 12).tabInterval(36).build()).build();
        PageText rtlText = publishAndRead(rtlBase, "NotoSansHebrew-Regular", "NotoSans-Regular").get(0);
        assertEquals("\u05d1\u05d0" + "12", rtlText.getText());
        position(rtlText, 0, 72, 707.196, 6.864);
        position(rtlText, 2, 108, 707.196, 6.864);
    }

    @Test
    public void koreanJamoClustersWrapAtomicallyAndOverwideClustersDoNotPublish() throws Exception {
        FontSelection fonts = FontSelection.explicit(font("NotoSansCJKkr-Regular.ttf"));
        ParagraphFlow flow = ParagraphFlow.version1(fonts).page(page()).paragraph(Paragraph.version1(48)
                .text("\u1100\u1161\u1102\u1161", 12).maximumWidth(24).build()).build();
        PageText text = publishAndRead(flow, "NotoSansCJKKR-Regular").get(0);
        // Pinned CJK KR hmtx gives 920 units for each Jamo, with 1000 units/em.
        position(text, 0, 72, 698.304, 11.04);
        position(text, 1, 83.04, 698.304, 11.04);
        position(text, 2, 72, 650.304, 11.04);
        position(text, 3, 83.04, 650.304, 11.04);
        for (Paragraph.Overflow overflow : new Paragraph.Overflow[] {Paragraph.Overflow.WRAP, Paragraph.Overflow.REJECT}) {
            ParagraphFlow narrow = ParagraphFlow.version2(fonts).page(page()).paragraph(Paragraph.version2(48)
                    .text("\u1100\u1161", 12).maximumWidth(18).overflow(overflow).build()).build();
            expectFailure(narrow, limits(2), WorkflowResourcePolicy.safeDefaults(),
                    DocumentFailureCode.COMPOSITION_AREA_EXHAUSTED);
        }
    }

    @Test
    public void unicodeControlsAndFullFontParsingRespectExistingLimitsBeforePublication() throws Exception {
        FontSelection sans = FontSelection.explicit(font("NotoSans-Regular.ttf"));
        ParagraphFlow controls = ParagraphFlow.version1(sans).page(page()).paragraph(Paragraph.version1(48)
                .text("A\u2067\u2069", 12).build()).build();
        CompositionLimits scalarLimit = limitsBuilder(1).fontLimits(FontLimits.builder().maximumFontSources(1)
                .maximumSourceBytes(1 << 20).maximumCodePoints(2).maximumFallbackChecks(2)
                .maximumGeneratedContentBytes(4096).build()).build();
        expectFailure(controls, scalarLimit, WorkflowResourcePolicy.safeDefaults(), DocumentFailureCode.FONT_LIMIT_EXCEEDED);
        for (String invalid : new String[] {"A\ud800", "\udc00A", "A\u0001B"}) {
            ParagraphFlow flow = ParagraphFlow.version1(sans).page(page()).paragraph(Paragraph.version1(48)
                    .text(invalid, 12).build()).build();
            expectFailure(flow, limits(1), WorkflowResourcePolicy.safeDefaults(), DocumentFailureCode.COMPOSITION_INVALID);
        }
        ParagraphFlow largeFont = ParagraphFlow.version1(FontSelection.explicit(font("NotoSansCJKjp-Regular.ttf")))
                .page(page()).paragraph(Paragraph.version1(48).text("\u9aa8", 12).build()).build();
        expectFailure(largeFont, limits(1), memoryPolicy(32L << 20), DocumentFailureCode.MEMORY_LIMIT_EXCEEDED);
    }

    @Test
    public void simultaneouslyParsedFullFontsStayWithinTheOwnedMemoryBudget() throws Exception {
        ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(font("NotoSansCJKjp-Regular.ttf"),
                font("NotoSansCJKkr-Regular.ttf"))).page(page())
                .paragraph(Paragraph.version1(48).text("\u9aa8", 12).build()).build();
        expectFailure(flow, limits(1), memoryPolicy(160L << 20), DocumentFailureCode.MEMORY_LIMIT_EXCEEDED);
    }

    @Test
    public void unsupportedLayoutMetadataVersionFailsBeforePublication() throws Exception {
        byte[] bytes = Files.readAllBytes(font("NotoSans-Regular.ttf").getPath().get());
        ByteBuffer data = ByteBuffer.wrap(bytes);
        data.putShort(tableOffset(data, 0x47535542), (short) 2); // GSUB version 2.
        repairFontChecksums(bytes);
        ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(FontSource.bytes(bytes)))
                .page(page()).paragraph(Paragraph.version1(48).text("A", 12).build()).build();
        expectFailure(flow, limits(1), WorkflowResourcePolicy.safeDefaults(), DocumentFailureCode.FONT_FORMAT_UNSUPPORTED);
    }

    @Test
    public void layoutMetadataRejectsReferencesOutsideItsDeclaredLookupList() throws Exception {
        byte[] bytes = Files.readAllBytes(font("NotoSans-Regular.ttf").getPath().get());
        ByteBuffer data = ByteBuffer.wrap(bytes);
        int gsub = tableOffset(data, 0x47535542);
        int featureList = gsub + (data.getShort(gsub + 6) & 65535);
        int firstFeature = featureList + (data.getShort(featureList + 6) & 65535);
        int lookupList = gsub + (data.getShort(gsub + 8) & 65535);
        assertTrue((data.getShort(firstFeature + 2) & 65535) > 0);
        data.putShort(firstFeature + 4, data.getShort(lookupList)); // First index beyond the declared list.
        repairFontChecksums(bytes);
        ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(FontSource.bytes(bytes)))
                .page(page()).paragraph(Paragraph.version1(48).text("A", 12).build()).build();
        expectFailure(flow, limits(1), WorkflowResourcePolicy.safeDefaults(), DocumentFailureCode.FONT_SOURCE_INVALID);
    }

    @Test
    public void layoutMetadataRejectsDeltaSubstitutionsOutsideTheFontGlyphDomain() throws Exception {
        byte[] bytes = Files.readAllBytes(font("NotoSans-Regular.ttf").getPath().get());
        ByteBuffer data = ByteBuffer.wrap(bytes);
        int substitution = tableOffset(data, 0x47535542) + 7302; // Pinned Sans GSUB SingleSubstFormat1.
        assertEquals(1, data.getShort(substitution));
        assertEquals(1633, data.getShort(substitution + 4));
        data.putShort(substitution + 4, Short.MAX_VALUE);
        repairFontChecksums(bytes);
        ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(FontSource.bytes(bytes)))
                .page(page()).paragraph(Paragraph.version1(48).text("A", 12).build()).build();
        expectFailure(flow, limits(1), WorkflowResourcePolicy.safeDefaults(), DocumentFailureCode.FONT_SOURCE_INVALID);
    }

    @Test
    public void unusedVerticalMetricsStillRequireAValidCountAndMatchingTableLength() throws Exception {
        byte[] bytes = Files.readAllBytes(font("NotoSansCJKjp-Regular.ttf").getPath().get());
        ByteBuffer data = ByteBuffer.wrap(bytes);
        data.putShort(tableOffset(data, 0x76686561) + 34, (short) 0); // vhea.numberOfVMetrics cannot be zero.
        repairFontChecksums(bytes);
        ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(FontSource.bytes(bytes)))
                .page(page()).paragraph(Paragraph.version1(48).text("\u9aa8", 12).build()).build();
        expectFailure(flow, limits(1), memoryPolicy(1L << 30), DocumentFailureCode.FONT_SOURCE_INVALID);
    }

    @Test
    public void automaticTableWidthsReserveTheWholeGraphemeCluster() throws Exception {
        Table table = Table.version1(Table.Layout.AUTO, TableWidth.points(40), TableWidth.auto(), TableWidth.auto())
                .row(TableRow.version1(TableCell.version1().paragraph(Paragraph.version1(48)
                                .text("\u1100\u1161", 12).build()).build(),
                        TableCell.version1().paragraph(Paragraph.version1(48).text("AAA", 12).build()).build())).build();
        ParagraphFlow flow = ParagraphFlow.version3(FontSelection.explicit(font("NotoSans-Regular.ttf"),
                font("NotoSansCJKkr-Regular.ttf"))).page(page()).table(table).build();
        PageText text = publishAndRead(flow, "NotoSansCJKKR-Regular", "NotoSans-Regular").get(0);
        assertEquals("\u1100\u1161AAA", text.getText());
        position(text, 0, 72, 698.304, 11.04);
        position(text, 1, 83.04, 698.304, 11.04);
        position(text, 2, 94.08, 707.196, 7.668);
        position(text, 4, 94.08, 659.196, 7.668);
    }

    @Test
    public void tablePreferredWidthsRecognizeUnicodeHardLineBreaks() throws Exception {
        Table table = Table.version1(Table.Layout.AUTO, TableWidth.points(80), TableWidth.auto(), TableWidth.auto())
                .row(TableRow.version1(TableCell.version1().paragraph(Paragraph.version1(48)
                                .text("C\u2028C", 12).build()).build(),
                        TableCell.version1().paragraph(Paragraph.version1(48).text("BB", 12).build()).build())).build();
        ParagraphFlow flow = ParagraphFlow.version3(FontSelection.explicit(font("NotoSans-Regular.ttf")))
                .page(page()).table(table).build();
        PageText text = publishAndRead(flow, "NotoSans-Regular").get(0);
        assertEquals("CCBB", text.getText());
        position(text, 0, 72, 707.196, 7.584);
        position(text, 1, 72, 659.196, 7.584);
        position(text, 2, 107.992, 707.196, 7.8);
    }

    @Test
    public void supplementaryHanUsesOneScalarAndOneClusterAcrossWorkerTransport() throws Exception {
        ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(font("NotoSansCJKjp-Regular.ttf")))
                .page(page()).paragraph(Paragraph.version1(48).text("\ud840\udc0b\u9aa8", 12).maximumWidth(12).build()).build();
        PageText text = publishAndRead(flow, "NotoSansCJKJP-Regular").get(0);
        assertEquals("\ud840\udc0b\u9aa8", text.getText());
        assertEquals(2, text.getTextItems().size());
        position(text, 0, 72, 698.304, 12);
        position(text, 1, 72, 650.304, 12);
        byte[] code = text.getTextItems().get(0).getCharacterMapping().getSourceCode();
        assertEquals(59477, ((code[0] & 255) << 8) | (code[1] & 255)); // Pinned source cmap U+2000B.
    }

    @Test
    public void completeCjkFontsUseTheExplicitRegionAndPreserveSubsetMappings() throws Exception {
        String[] regions = {"sc", "tc", "jp", "kr"};
        String[] names = {"SC", "TC", "JP", "KR"};
        int[] boneGlyphs = {45133, 45134, 45132, 45132}; // Pinned source cmaps, independent of the producer.
        for (int index = 0; index < regions.length; index++) {
            ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(font("NotoSans-Regular.ttf"),
                    font("NotoSansCJK" + regions[index] + "-Regular.ttf"),
                    font("NotoSansCJK" + regions[(index + 1) % regions.length] + "-Regular.ttf")))
                    .page(page()).paragraph(Paragraph.version1(48).text("A\u9aa8", 12).build()).build();
            PageText text = publishAndRead(flow, "NotoSans-Regular", "NotoSansCJK" + names[index] + "-Regular").get(0);
            assertEquals("A\u9aa8", text.getText());
            position(text, 0, 72, 698.304, 7.668); // Static CJK head yMax=1808.
            position(text, 1, 79.668, 698.304, 12);
            byte[] code = text.getTextItems().get(1).getCharacterMapping().getSourceCode();
            assertEquals(boneGlyphs[index], ((code[0] & 255) << 8) | (code[1] & 255));
        }
    }

    private List<PageText> publishAndRead(ParagraphFlow flow, String... expectedFonts) throws Exception {
        Path output = temporary.newFile().toPath();
        WorkflowOutcome<List<PageText>> result = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .target("result", PublicationTarget.path(output)).saveMode(SaveMode.REWRITE)
                .executionProfile(profile).resourcePolicy(memoryPolicy(1L << 30)).build(), session -> {
                    session.execute(flow.getVersion() == ParagraphFlow.VERSION_3
                            ? ComposeParagraphs.version3(flow, limits(flow.getVersion()))
                            : flow.getVersion() == ParagraphFlow.VERSION_1
                            ? ComposeParagraphs.version1(flow, limits(flow.getVersion()))
                            : ComposeParagraphs.version2(flow, limits(flow.getVersion())));
                    return extract(session);
                });
        assertEquals(profile, result.getExecutionProfile());
        assertEquals(PublicationStatus.COMMITTED, result.getPublicationReceipts().get(0).getStatus());
        List<PageText> reopened = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .source("primary", DocumentSource.path(output)).primarySource("primary")
                .saveMode(SaveMode.REWRITE).executionProfile(profile).build(),
                session -> {
                    List<FontResource> fonts = session.query(ExtractImagesAndResources.version1(
                            ResourceExtractionLimits.builder().maximumPages(16).maximumPageTreeNodes(64)
                                    .maximumTraversedResourceValues(10000).maximumResourceTraversalDepth(8)
                                    .maximumDecodedPixels(0).maximumDecompressedBytes(1 << 20)
                                    .maximumReturnedBytes(0).build(), ImageByteAccess.NONE)).getFonts();
                    assertEquals(expectedFonts.length, fonts.size());
                    for (int index = 0; index < expectedFonts.length; index++) {
                        FontResource font = fonts.get(index);
                        assertEquals(FontResource.Embedding.EMBEDDED, font.getEmbedding());
                        assertTrue(font.isSubset());
                        assertTrue(font.getBaseFontName().get().getValue().endsWith("+" + expectedFonts[index]));
                    }
                    return extract(session);
                }).getResult();
        assertEquals(result.getResult().size(), reopened.size());
        for (int page = 0; page < reopened.size(); page++) {
            assertEquals(result.getResult().get(page).getText(), reopened.get(page).getText());
        }
        return reopened;
    }

    private static FontSource font(String name) throws Exception {
        Properties pins = new Properties();
        try (InputStream input = UnicodeCompositionWorkflowTest.class.getResourceAsStream(FONT_ROOT + "fonts.properties")) {
            pins.load(input);
        }
        Path path = Paths.get(UnicodeCompositionWorkflowTest.class.getResource(FONT_ROOT + name).toURI());
        StringBuilder hash = new StringBuilder();
        for (byte part : MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))) {
            hash.append(Character.forDigit((part & 255) >>> 4, 16)).append(Character.forDigit(part & 15, 16));
        }
        assertEquals(pins.getProperty(name + ".sha256"), hash.toString());
        return FontSource.path(path);
    }

    private static LayoutPage page() {
        return LayoutPage.version1(612, 792, PageMargins.of(72, 72, 72, 72));
    }

    private static WorkflowResourcePolicy memoryPolicy(long bytes) {
        WorkflowResourcePolicy defaults = WorkflowResourcePolicy.safeDefaults();
        return WorkflowResourcePolicy.builder().maximumOwnedMemoryBytes(bytes)
                .maximumPages(defaults.getMaximumPages()).maximumInputBytes(defaults.getMaximumInputBytes())
                .maximumObjects(defaults.getMaximumObjects()).maximumNestingDepth(defaults.getMaximumNestingDepth())
                .maximumDecompressedBytes(defaults.getMaximumDecompressedBytes())
                .maximumDecodedPixels(defaults.getMaximumDecodedPixels())
                .maximumTemporaryStorageBytes(defaults.getMaximumTemporaryStorageBytes())
                .maximumElapsedTime(defaults.getMaximumElapsedTime())
                .maximumConcurrentWorkflows(defaults.getMaximumConcurrentWorkflows()).build();
    }

    private static long checksum(byte[] bytes, int offset, int length) {
        long sum = 0;
        for (int index = 0; index < length; index += 4) {
            long word = 0;
            for (int part = 0; part < 4; part++) {
                word = (word << 8) | (index + part < length ? bytes[offset + index + part] & 255 : 0);
            }
            sum = (sum + word) & 0xffffffffL;
        }
        return sum;
    }

    private static int tableOffset(ByteBuffer data, int tag) {
        for (int record = 12; record < 12 + 16 * (data.getShort(4) & 65535); record += 16) {
            if (data.getInt(record) == tag) { return data.getInt(record + 8); }
        }
        throw new AssertionError("The pinned fixture lacks the declared table.");
    }

    private static void repairFontChecksums(byte[] bytes) {
        ByteBuffer data = ByteBuffer.wrap(bytes);
        int head = tableOffset(data, 0x68656164);
        data.putInt(head + 8, 0);
        for (int record = 12; record < 12 + 16 * (data.getShort(4) & 65535); record += 16) {
            data.putInt(record + 4, (int) checksum(bytes, data.getInt(record + 8), data.getInt(record + 12)));
        }
        data.putInt(head + 8, (int) (0xb1b0afbaL - checksum(bytes, 0, bytes.length)));
    }

    private static CompositionLimits limits(int version) {
        return limitsBuilder(version).build();
    }

    private static CompositionLimits.Builder limitsBuilder(int version) {
        CompositionLimits.Builder builder = version == ParagraphFlow.VERSION_3
                ? CompositionLimits.version3().maximumLayoutAttempts(10000)
                        .tableLimits(TableLimits.builder().maximumTables(1).maximumRows(4).maximumCells(8)
                                .maximumColumns(4).maximumGridSlots(16).maximumLayoutWork(10000).build())
                : version == ParagraphFlow.VERSION_1 ? CompositionLimits.builder()
                : CompositionLimits.version2().maximumLayoutAttempts(10000).maximumRelayouts(4);
        return builder.maximumPages(16).maximumAreas(16).maximumFlowItems(32)
                .maximumInlines(64).maximumLines(512).maximumGeneratedContentBytes(1 << 20)
                .fontLimits(FontLimits.builder().maximumFontSources(6).maximumSourceBytes(128L << 20)
                        .maximumCodePoints(10000).maximumFallbackChecks(60000)
                        .maximumGeneratedContentBytes(1 << 20).build())
                .graphicLimits(CanvasResourceLimits.builder().maximumEncodedImageBytes(0)
                        .maximumDecodedImagePixels(0).maximumDecodedImageBytes(0).maximumIccProfileBytes(0)
                        .maximumMaskBytes(0).maximumGeneratedContentBytes(0).maximumResourceDeclarations(0)
                        .maximumTransparencyGroupDepth(0).build());
    }

    private void expectFailure(ParagraphFlow flow, CompositionLimits limits,
            WorkflowResourcePolicy policy, DocumentFailureCode code) throws Exception {
        Path output = temporary.newFile().toPath();
        byte[] sentinel = {1, 2, 3, 4};
        Files.write(output, sentinel);
        try {
            new DocumentWorkflow().execute(WorkflowRequest.builder().target("result", PublicationTarget.path(output))
                    .saveMode(SaveMode.REWRITE).executionProfile(profile).resourcePolicy(policy).build(), session -> {
                        session.execute(flow.getVersion() == 1 ? ComposeParagraphs.version1(flow, limits)
                                : ComposeParagraphs.version2(flow, limits));
                        return null;
                    });
            fail("Expected " + code);
        } catch (DocumentFailure failure) {
            assertEquals(code, failure.getCode());
            assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(0).getStatus());
        }
        assertArrayEquals(sentinel, Files.readAllBytes(output));
    }

    private static List<PageText> extract(DocumentSession session) throws DocumentFailure {
        return session.query(ExtractTextAndStructure.version1(ExtractionLimits.builder()
                .maximumPages(16).maximumPageTreeNodes(64).maximumContentStreams(4096)
                .maximumContentStreamDepth(8).maximumDecodedBytes(1 << 20).maximumTextItems(10000)
                .maximumUnicodeCodePoints(10000).maximumToUnicodeMappings(10000).maximumFontDataEntries(10000)
                .maximumMarkedContentSequences(8).maximumMarkedContentDepth(4).maximumStructureElements(8)
                .maximumStructureItems(8).maximumStructureDepth(4).maximumRoleMappings(4).build())).getPages();
    }

    private static void position(PageText page, int index, double x, double y, double advance) {
        TextItem item = page.getTextItems().get(index);
        assertEquals(CharacterMapping.Confidence.EXPLICIT, item.getCharacterMapping().getConfidence());
        assertEquals(x, item.getGeometry().getE().doubleValue(), TOLERANCE);
        assertEquals(y, item.getGeometry().getF().doubleValue(), TOLERANCE);
        assertEquals(advance, item.getGeometry().getAdvanceX().doubleValue(), TOLERANCE);
        assertTrue(item.getCharacterMapping().getUnicode().isPresent());
    }
}
