package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Arrays;
import java.util.Collection;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import net.zerocloud.pdf.FontResource;
import net.zerocloud.pdf.ImageByteAccess;
import net.zerocloud.pdf.ResourceExtractionLimits;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ExtractionLimits;
import net.zerocloud.pdf.PageText;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.TextItem;
import net.zerocloud.pdf.TextRenderingMode;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.composition.PositionedUnicodeText;
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.WorkflowResourcePolicy;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.composition.CanvasResourceLimits;
import net.zerocloud.pdf.composition.CanvasRectangle;
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
import net.zerocloud.pdf.composition.command.ComposeParagraphs;
import net.zerocloud.pdf.composition.command.DrawPositionedUnicodeText;
import net.zerocloud.pdf.composition.command.RelayoutParagraphs;
import net.zerocloud.pdf.composition.command.BeginLargeTable;
import net.zerocloud.pdf.composition.command.AppendTableRows;
import net.zerocloud.pdf.composition.command.FlushTable;
import net.zerocloud.pdf.composition.command.CompleteTable;
import net.zerocloud.pdf.conversion.HarfBuzzCapabilityProvider;
import net.zerocloud.pdf.provider.ProviderLimits;
import net.zerocloud.pdf.provider.CapabilityProvider;
import net.zerocloud.pdf.provider.ProviderFailure;
import net.zerocloud.pdf.provider.ProviderRequest;
import net.zerocloud.pdf.provider.ProviderResult;
import net.zerocloud.pdf.provider.ProviderPreference;
import net.zerocloud.pdf.provider.ShapingRequest;
import net.zerocloud.pdf.query.ExtractTextAndStructure;
import net.zerocloud.pdf.query.ExtractImagesAndResources;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Shaping is observed through public Workflow execution and reopened publication. */
@RunWith(Parameterized.class)
public final class ShapingCompositionWorkflowTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private final WorkflowExecutionProfile profile;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> profiles() {
        return Arrays.asList(new Object[][] {{WorkflowExecutionProfile.IN_PROCESS},
                {WorkflowExecutionProfile.HARDENED_WORKER}});
    }

    public ShapingCompositionWorkflowTest(WorkflowExecutionProfile profile) { this.profile = profile; }

    @Test
    public void completeArabicFontAcceptsAxisReflectedCompositeOutlines() throws Exception {
        FontSource font = FontSource.path(Paths.get(getClass().getResource(
                "/net/zerocloud/pdf/fixtures/noto/NotoSansArabic-Regular.ttf").toURI()));
        ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(font))
                .page(LayoutPage.version1(240, 192, PageMargins.of(24, 24, 24, 24)))
                .paragraph(Paragraph.version1(48).text("\u0627", 12).build()).build();
        WorkflowOutcome<String> outcome = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .target("result", PublicationTarget.path(temporary.newFile().toPath()))
                .saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> {
                    session.execute(ComposeParagraphs.version1(flow, limits()));
                    return session.query(ExtractTextAndStructure.version1(extractionLimits()))
                            .getPages().get(0).getText();
                });
        assertEquals("\u0627", outcome.getResult());
        assertEquals(PublicationStatus.COMMITTED, outcome.getPublicationReceipts().get(0).getStatus());
    }

    @Test
    public void arabicMarkedLigaturePublishesTwoPositionedGlyphsAndItsLogicalInput() throws Exception {
        Path output = temporary.newFile().toPath();
        Path helper = Paths.get(System.getProperty("folio.harfBuzzHelper"));
        HarfBuzzCapabilityProvider provider = new HarfBuzzCapabilityProvider(helper,
                temporary.newFolder().toPath(), "10.2.0",
                ProviderLimits.bounded(1 << 20, 32 + 24 * 1024, Duration.ofSeconds(10)));
        FontSource font = FontSource.path(Paths.get(getClass().getResource(
                "/net/zerocloud/pdf/fixtures/noto/NotoSansArabic-Regular.ttf").toURI()));
        ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(font))
                .page(LayoutPage.version1(240, 192, PageMargins.of(24, 24, 24, 24)))
                .paragraph(Paragraph.version1(48).text("\u0644\u064e\u0627", 12).build()).build();
        WorkflowOutcome<Void> outcome = new DocumentWorkflow(WorkflowEnvironment.builder()
                .provider(provider).build()).execute(WorkflowRequest.builder()
                .providerPreference(ProviderPreference.prefer(ShapingRequest.CAPABILITY_ID,
                        HarfBuzzCapabilityProvider.PROVIDER_ID))
                .target("result", PublicationTarget.path(output)).saveMode(SaveMode.REWRITE)
                .executionProfile(profile).build(),
                session -> { session.execute(ComposeParagraphs.version1(flow, limits())); return null; });
        assertEquals(PublicationStatus.COMMITTED, outcome.getPublicationReceipts().get(0).getStatus());
        List<PageText> pages = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .source("primary", DocumentSource.path(output)).primarySource("primary")
                .saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> session.query(
                        ExtractTextAndStructure.version1(extractionLimits())).getPages()).getResult();
        assertEquals(1, pages.size());
        assertEquals("\u0644\u064e\u0627", pages.get(0).getText());
        List<TextItem> glyphs = pages.get(0).getTextItems();
        assertEquals(2, glyphs.size());
        // Official HarfBuzz 10.2.0 oracle, Noto Sans Arabic 2.009: head yMax=1359.
        assertEquals(26.988, glyphs.get(0).getGeometry().getE().doubleValue(), 0.0001);
        assertEquals(154.764, glyphs.get(0).getGeometry().getF().doubleValue(), 0.0001);
        assertEquals(0, glyphs.get(0).getGeometry().getAdvanceX().doubleValue(), 0.0001);
        assertEquals(24, glyphs.get(1).getGeometry().getE().doubleValue(), 0.0001);
        assertEquals(151.692, glyphs.get(1).getGeometry().getF().doubleValue(), 0.0001);
        assertEquals(6.984, glyphs.get(1).getGeometry().getAdvanceX().doubleValue(), 0.0001);
    }

    @Test
    public void aNativeResultSplittingAnIcuGraphemeFailsBeforePublication() throws Exception {
        final HarfBuzzCapabilityProvider nativeProvider = nativeProvider();
        CapabilityProvider altered = new CapabilityProvider(nativeProvider.getMetadata()) {
            @Override protected ProviderResult perform(ProviderRequest request) throws ProviderFailure {
                byte[] bytes = nativeProvider.execute(request).getOutput();
                // Real 10.2.0 output for ki has two glyphs in cluster [0,2).
                // Change only the second glyph's start to split that ICU grapheme.
                ByteBuffer.wrap(bytes).putInt(32 + 24 + 4, 1);
                return ProviderResult.of(bytes);
            }
        };
        Path target = temporary.newFile().toPath();
        byte[] original = new byte[] {3, 1, 4};
        Files.write(target, original);
        try {
            compose(target, altered, "NotoSansDevanagari-Regular.ttf", Paragraph.version1(36)
                    .maximumWidth(12).text("\u0915\u093f", 12).build());
            fail("The Provider result must not split an ICU grapheme");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.CAPABILITY_PROVIDER_FAILED, failure.getCode());
            assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(0).getStatus());
            assertArrayEquals(original, Files.readAllBytes(target));
        }
    }

    @Test
    public void unavailableEnginesAndNativeLimitsLeaveTheCallerTargetAndStagingIntact() throws Exception {
        for (int fault = 0; fault < 4; fault++) {
            Path staging = temporary.newFolder().toPath();
            Path marker = staging.resolve("caller-owned");
            byte[] sentinel = {3, 1, 4};
            Files.write(marker, sentinel);
            Path helper = fault == 0 ? staging.resolve("missing-helper")
                    : Paths.get(System.getProperty("folio.harfBuzzHelper"));
            HarfBuzzCapabilityProvider provider = new HarfBuzzCapabilityProvider(helper, staging,
                    fault == 1 ? "10.1.0" : "10.2.0",
                    ProviderLimits.bounded(fault == 2 ? 128 : 1 << 20,
                            fault == 3 ? 32 + 24 : 32 + 24 * 1024, Duration.ofSeconds(10)));
            Path target = temporary.newFile().toPath();
            Files.write(target, sentinel);
            try {
                compose(target, provider, "NotoSansArabic-Regular.ttf",
                        Paragraph.version1(36).text("\u0644\u064e\u0627", 12).build());
                fail("Expected unavailable engine or bounded native failure " + fault);
            } catch (DocumentFailure failure) {
                assertEquals(fault < 2 ? DocumentFailureCode.CAPABILITY_PROVIDER_UNAVAILABLE
                        : DocumentFailureCode.CAPABILITY_PROVIDER_FAILED, failure.getCode());
                assertEquals(ShapingRequest.CAPABILITY_ID, failure.getCapabilityId());
                assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(0).getStatus());
                assertFalse(failure.getDiagnostic().contains(staging.toString()));
            }
            assertArrayEquals(sentinel, Files.readAllBytes(target));
            assertArrayEquals(sentinel, Files.readAllBytes(marker));
            try (java.util.stream.Stream<Path> files = Files.list(staging)) { assertEquals(1, files.count()); }
        }
    }

    @Test
    public void positionedTextAndRegistrationWithoutPreferenceKeepTheirUnshapedContract() throws Exception {
        for (boolean positioned : new boolean[] {true, false}) {
            Path output = temporary.newFile().toPath();
            HarfBuzzCapabilityProvider provider = new HarfBuzzCapabilityProvider(
                    Paths.get(System.getProperty("folio.harfBuzzHelper")), temporary.newFolder().toPath(), "10.2.0",
                    ProviderLimits.bounded(1, 32, Duration.ofSeconds(10)));
            ParagraphFlow flow = flow("NotoSans-Regular.ttf", Paragraph.version1(36).text("ffi", 12).build());
            WorkflowRequest.Builder request = WorkflowRequest.builder().target("result", PublicationTarget.path(output))
                    .saveMode(SaveMode.REWRITE).executionProfile(profile);
            if (positioned) { request.providerPreference(ProviderPreference.prefer(
                    ShapingRequest.CAPABILITY_ID, HarfBuzzCapabilityProvider.PROVIDER_ID)); }
            new DocumentWorkflow(WorkflowEnvironment.builder().provider(provider).build()).execute(request.build(), session -> {
                if (positioned) {
                    session.execute(AddBlankPage.INSTANCE);
                    session.execute(DrawPositionedUnicodeText.version1(1, PositionedUnicodeText.version1(
                            "ffi", flow.getFonts(), 12, TextRenderingMode.FILL, CanvasMatrix.of(1, 0, 0, 1, 24, 72)),
                            limits().getFontLimits()));
                } else { session.execute(ComposeParagraphs.version1(flow, limits())); }
                return null;
            });
            PageText page = reopen(output).get(0);
            assertEquals("ffi", page.getText());
            assertEquals(3, page.getTextItems().size());
            for (int index = 0; index < 3; index++) {
                assertEquals(index == 2 ? 3.096 : 4.128,
                        page.getTextItems().get(index).getGeometry().getAdvanceX().doubleValue(), 0.0001);
            }
        }
    }

    @Test
    public void incrementalShapingRequiresPdf15EvenWithOnlyBmpMappings() throws Exception {
        byte[] font = Base64.getMimeDecoder().decode(Files.readAllBytes(Paths.get(getClass().getResource(
                "/net/zerocloud/pdf/fixtures/FolioPrimary.ttf.base64").toURI())));
        ParagraphFlow flow = ParagraphFlow.version1(FontSelection.explicit(FontSource.bytes(font)))
                .page(LayoutPage.version1(240, 192, PageMargins.of(24, 24, 24, 24)))
                .paragraph(Paragraph.version1(36).text("A", 12).build()).build();
        for (String version : new String[] {"1.2", "1.4", "1.5"}) {
            byte[] original = blankPdf(version);
            Path source = temporary.newFile().toPath();
            Files.write(source, original);
            Path output = temporary.newFile().toPath();
            byte[] sentinel = {3, 1, 4};
            Files.write(output, sentinel);
            try {
                new DocumentWorkflow(WorkflowEnvironment.builder().provider(nativeProvider()).build()).execute(
                        WorkflowRequest.builder().source("primary", DocumentSource.path(source)).primarySource("primary")
                                .target("result", PublicationTarget.path(output)).saveMode(SaveMode.INCREMENTAL)
                                .executionProfile(profile).providerPreference(ProviderPreference.prefer(
                                        ShapingRequest.CAPABILITY_ID, HarfBuzzCapabilityProvider.PROVIDER_ID)).build(),
                        session -> { session.execute(ComposeParagraphs.version1(flow, limits())); return null; });
                assertEquals("Shaping replacement text requires PDF 1.5", "1.5", version);
                List<PageText> pages = reopen(output);
                assertEquals(2, pages.size());
                assertEquals("A", pages.get(1).getText());
                assertEquals(1, pages.get(1).getTextItems().size());
            } catch (DocumentFailure failure) {
                assertFalse("PDF 1.5 admits replacement text", "1.5".equals(version));
                assertEquals(DocumentFailureCode.PDF_VERSION_UNSUPPORTED, failure.getCode());
                assertEquals("Shaped replacement text requires PDF 1.5 or newer.", failure.getDiagnostic());
                assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(0).getStatus());
                assertArrayEquals(sentinel, Files.readAllBytes(output));
            }
            assertArrayEquals(original, Files.readAllBytes(source));
        }
    }

    @Test
    public void bufferedRelayoutReshapesUsingTheBorrowedFontSnapshot() throws Exception {
        BorrowedFont stream = borrowedArabic();
        String text = "\u0628\u0628\u0628\u0628";
        ParagraphFlow flow = ParagraphFlow.version2(FontSelection.explicit(FontSource.stream(stream)))
                .page(LayoutPage.version1(60, 192, PageMargins.of(24, 24, 24, 24)))
                .paragraph(Paragraph.version1(36).text(text, 12).build()).build();
        Path output = temporary.newFile().toPath();
        new DocumentWorkflow(WorkflowEnvironment.builder().provider(nativeProvider()).build()).execute(
                WorkflowRequest.builder().target("result", PublicationTarget.path(output)).saveMode(SaveMode.REWRITE)
                        .executionProfile(profile).providerPreference(ProviderPreference.prefer(
                                ShapingRequest.CAPABILITY_ID, HarfBuzzCapabilityProvider.PROVIDER_ID)).build(), session -> {
                            session.execute(ComposeParagraphs.version2(flow, lifecycleLimits(false)));
                            assertEquals(0, stream.available());
                            session.execute(RelayoutParagraphs.version1(
                                    LayoutPage.version1(65, 192, PageMargins.of(24, 24, 24, 24))));
                            return null;
                        });
        assertFalse(stream.closed);
        PageText page = reopen(output).get(0);
        assertEquals(text, page.getText());
        assertEquals(4, page.getTextItems().size());
        for (int index = 0; index < 4; index++) {
            TextItem item = page.getTextItems().get(index);
            assertEquals(index % 2 == 0 ? 13.116 : 3.228, item.getGeometry().getAdvanceX().doubleValue(), 0.0001);
            assertEquals(151.692 - 36 * (index / 2), item.getGeometry().getF().doubleValue(), 0.0001);
        }
    }

    @Test
    public void repeatedBufferedRelayoutReleasesObsoleteInitialShapingPlans() throws Exception {
        WorkflowOutcome<Void> once = repeatedRelayout(1, temporary.newFile().toPath(),
                WorkflowResourcePolicy.safeDefaults());
        long budget = once.getResourceUsage().getPeakOwnedMemoryBytes() + 65536;
        Path output = temporary.newFile().toPath();
        WorkflowOutcome<Void> repeated = repeatedRelayout(32, output, memoryPolicy(budget));
        assertEquals(PublicationStatus.COMMITTED, repeated.getPublicationReceipts().get(0).getStatus());
        assertTrue(repeated.getResourceUsage().getPeakOwnedMemoryBytes() <= budget);
        PageText page = reopen(output).get(0);
        assertEquals(32, page.getText().length());
        assertEquals(32, page.getTextItems().size());
        for (TextItem glyph : page.getTextItems()) {
            assertEquals("\u0628", glyph.getCharacterMapping().getUnicode().get());
            assertEquals(11.916, glyph.getGeometry().getAdvanceX().doubleValue(), 0.0001);
        }
    }

    @Test
    public void shapingFallbackCountsEachScalarVisitAtTheExactBoundary() throws Exception {
        ParagraphFlow flow = flow("NotoSansDevanagari-Regular.ttf",
                Paragraph.version1(36).text("\u0915\u093f", 12).build());
        for (int visits : new int[] {3, 4}) {
            CompositionLimits bounded = CompositionLimits.builder().maximumPages(1).maximumAreas(1)
                    .maximumFlowItems(1).maximumInlines(1).maximumLines(1).maximumGeneratedContentBytes(1 << 20)
                    .fontLimits(FontLimits.builder().maximumFontSources(1).maximumSourceBytes(2 << 20)
                            .maximumCodePoints(2).maximumFallbackChecks(visits)
                            .maximumGeneratedContentBytes(1 << 20).build())
                    .graphicLimits(limits().getGraphicLimits()).build();
            Path output = temporary.newFile().toPath();
            byte[] sentinel = {3, 1, 4};
            Files.write(output, sentinel);
            try {
                new DocumentWorkflow(WorkflowEnvironment.builder().provider(nativeProvider()).build()).execute(
                        WorkflowRequest.builder().providerPreference(ProviderPreference.prefer(ShapingRequest.CAPABILITY_ID,
                                HarfBuzzCapabilityProvider.PROVIDER_ID)).target("result", PublicationTarget.path(output))
                                .saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> {
                                    session.execute(ComposeParagraphs.version1(flow, bounded)); return null;
                                });
                // Two scalar selections, then two visits to check the shared grapheme font.
                assertEquals("Every scalar coverage visit consumes the declared limit", 4, visits);
                PageText page = reopen(output).get(0);
                assertEquals("\u0915\u093f", page.getText());
                assertEquals(2, page.getTextItems().size());
            } catch (DocumentFailure failure) {
                assertEquals("Four visits admit the exact boundary", 3, visits);
                assertEquals(DocumentFailureCode.FONT_LIMIT_EXCEEDED, failure.getCode());
                assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(0).getStatus());
                assertArrayEquals(sentinel, Files.readAllBytes(output));
            }
        }
    }

    @Test
    public void incrementalTablesKeepShapedGlyphsAndBorrowedFontsAcrossFlushAndComplete() throws Exception {
        BorrowedFont stream = borrowedArabic();
        String text = "\u0644\u064e\u0627";
        TableRow row = TableRow.version1(TableCell.version1()
                .paragraph(Paragraph.version1(36).text(text, 12).build()).build());
        LayoutPage page = LayoutPage.version1(240, 120, PageMargins.of(24, 24, 24, 24));
        ParagraphFlow flow = ParagraphFlow.version4(FontSelection.explicit(FontSource.stream(stream)))
                .page(page).page(page).table(Table.version2(Table.Layout.FIXED,
                        TableWidth.points(192), TableWidth.auto()).build()).build();
        Path output = temporary.newFile().toPath();
        byte[] sentinel = {3, 1, 4};
        Files.write(output, sentinel);
        new DocumentWorkflow(WorkflowEnvironment.builder().provider(nativeProvider()).build()).execute(
                WorkflowRequest.builder().target("result", PublicationTarget.path(output)).saveMode(SaveMode.REWRITE)
                        .executionProfile(profile).providerPreference(ProviderPreference.prefer(
                                ShapingRequest.CAPABILITY_ID, HarfBuzzCapabilityProvider.PROVIDER_ID)).build(), session -> {
                            session.execute(BeginLargeTable.version1(flow, lifecycleLimits(true), 3));
                            session.execute(AppendTableRows.version1(row, row, row));
                            session.execute(FlushTable.version1());
                            assertEquals(0, stream.available());
                            try { assertArrayEquals(sentinel, Files.readAllBytes(output)); }
                            catch (java.io.IOException failure) { throw new AssertionError(failure); }
                            session.execute(AppendTableRows.version1(row));
                            session.execute(CompleteTable.version1());
                            return null;
                        });
        assertFalse(stream.closed);
        List<PageText> pages = reopen(output);
        assertEquals(2, pages.size());
        for (PageText observed : pages) {
            assertEquals(text + text, observed.getText());
            assertEquals(4, observed.getTextItems().size());
            for (int index = 0; index < 4; index++) {
                TextItem item = observed.getTextItems().get(index);
                assertEquals(index % 2 == 0 ? 0 : 6.984, item.getGeometry().getAdvanceX().doubleValue(), 0.0001);
                assertEquals((index % 2 == 0 ? 82.764 : 79.692) - 36 * (index / 2),
                        item.getGeometry().getF().doubleValue(), 0.0001);
            }
        }
    }

    @Test
    public void softWrappedArabicReshapesAndRefitsEveryLineBoundary() throws Exception {
        for (int width : new int[] {17, 15}) {
            Path output = temporary.newFile().toPath();
            compose(output, nativeProvider(), "NotoSansArabic-Regular.ttf", Paragraph.version1(36)
                    .maximumWidth(width).text("\u0628\u0628\u0628\u0628", 12).build());
            PageText page = reopen(output).get(0);
            assertEquals("\u0628\u0628\u0628\u0628", page.getText());
            assertEquals(4, page.getTextItems().size());
            for (int index = 0; index < 4; index++) {
                TextItem item = page.getTextItems().get(index);
                int line = width == 17 ? index / 2 : index;
                // Official 10.2.0: bb => [101:1093,102:269], b => [100:993].
                double advance = width == 17 ? (index % 2 == 0 ? 13.116 : 3.228) : 11.916;
                assertEquals("width=" + width + " glyph=" + index, 151.692 - 36 * line,
                        item.getGeometry().getF().doubleValue(), 0.0001);
                assertEquals(width == 17 && index % 2 == 1 ? 37.116 : 24,
                        item.getGeometry().getE().doubleValue(), 0.0001);
                assertEquals(advance, item.getGeometry().getAdvanceX().doubleValue(), 0.0001);
            }
        }
    }

    @Test
    public void narrowLinesReshapeALigatureAtOriginalGraphemeBoundaries() throws Exception {
        Path output = temporary.newFile().toPath();
        compose(output, nativeProvider(), "NotoSans-Regular.ttf", Paragraph.version1(36)
                .maximumWidth(6).text("ffi", 12).build());
        PageText page = reopen(output).get(0);
        assertEquals("ffi", page.getText());
        assertEquals(3, page.getTextItems().size());
        for (int index = 0; index < 3; index++) {
            TextItem item = page.getTextItems().get(index);
            // Official HarfBuzz 10.2.0: isolated f=GID73/344, i=GID76/258; yMax=1067.
            assertEquals(24, item.getGeometry().getE().doubleValue(), 0.0001);
            assertEquals(155.196 - 36 * index, item.getGeometry().getF().doubleValue(), 0.0001);
            assertEquals(index == 2 ? 3.096 : 4.128, item.getGeometry().getAdvanceX().doubleValue(), 0.0001);
        }
    }

    @Test
    public void tableIntrinsicMinimumUsesIndependentlyShapedGraphemes() throws Exception {
        for (Table.Layout layout : Table.Layout.values()) {
            for (boolean arabic : new boolean[] {true, false}) {
                String text = arabic ? "\u0628\u0628\u0628\u0628" : "ffi";
                FontSelection selection = flow(arabic ? "NotoSansArabic-Regular.ttf" : "NotoSans-Regular.ttf",
                        Paragraph.version1(36).text(text, 12).build()).getFonts();
                Table table = Table.version1(layout, TableWidth.points(arabic ? 12 : 6), TableWidth.auto())
                        .row(TableRow.version1(TableCell.version1()
                                .paragraph(Paragraph.version1(36).text(text, 12).build()).build())).build();
                ParagraphFlow flow = ParagraphFlow.version3(selection)
                        .page(LayoutPage.version1(240, 192, PageMargins.of(24, 24, 24, 24))).table(table).build();
                Path output = temporary.newFile().toPath();
                new DocumentWorkflow(WorkflowEnvironment.builder().provider(nativeProvider()).build()).execute(
                        WorkflowRequest.builder().providerPreference(ProviderPreference.prefer(ShapingRequest.CAPABILITY_ID,
                                HarfBuzzCapabilityProvider.PROVIDER_ID)).target("result", PublicationTarget.path(output))
                                .saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> {
                                    session.execute(ComposeParagraphs.version3(flow, tableLimits())); return null;
                                });
                PageText page = reopen(output).get(0);
                assertEquals(text, page.getText());
                assertEquals(text.length(), page.getTextItems().size());
                for (int index = 0; index < text.length(); index++) {
                    TextItem item = page.getTextItems().get(index);
                    assertEquals(24, item.getGeometry().getE().doubleValue(), 0.0001);
                    assertEquals((arabic ? 151.692 : 155.196) - 36 * index,
                            item.getGeometry().getF().doubleValue(), 0.0001);
                    assertEquals(arabic ? 11.916 : index == 2 ? 3.096 : 4.128,
                            item.getGeometry().getAdvanceX().doubleValue(), 0.0001);
                }
            }
        }
    }

    @Test
    public void tableMinimumAdmitsAJoinedLigatureNarrowerThanItsIsolatedGraphemes() throws Exception {
        for (Table.Layout layout : Table.Layout.values()) {
            Paragraph paragraph = Paragraph.version1(36).text("\u0644\u0627", 12).build();
            Table table = Table.version1(layout, TableWidth.points(7), TableWidth.auto())
                    .row(TableRow.version1(TableCell.version1().paragraph(paragraph).build())).build();
            ParagraphFlow flow = ParagraphFlow.version3(flow("NotoSansArabic-Regular.ttf", paragraph).getFonts())
                    .page(LayoutPage.version1(240, 192, PageMargins.of(24, 24, 24, 24))).table(table).build();
            Path output = temporary.newFile().toPath();
            new DocumentWorkflow(WorkflowEnvironment.builder().provider(nativeProvider()).build()).execute(
                    WorkflowRequest.builder().providerPreference(ProviderPreference.prefer(ShapingRequest.CAPABILITY_ID,
                            HarfBuzzCapabilityProvider.PROVIDER_ID)).target("result", PublicationTarget.path(output))
                            .saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> {
                                session.execute(ComposeParagraphs.version3(flow, tableLimits())); return null;
                            });
            PageText page = reopen(output).get(0);
            assertEquals("\u0644\u0627", page.getText());
            assertEquals(1, page.getTextItems().size());
            TextItem ligature = page.getTextItems().get(0);
            // Official 10.2.0: la => GID704/582; isolated lam advances 695 font units.
            assertEquals(6.984, ligature.getGeometry().getAdvanceX().doubleValue(), 0.0001);
            assertEquals(24, ligature.getGeometry().getE().doubleValue(), 0.0001);
            assertEquals(151.692, ligature.getGeometry().getF().doubleValue(), 0.0001);
        }
    }

    @Test
    public void rejectedAreasReleaseShapingWithinTheBudgetOfExplicitlySkippingThem() throws Exception {
        for (int version : new int[] {1, 2, 3}) {
            WorkflowOutcome<Void> explicit = searchAreas(version, true, temporary.newFile().toPath(),
                    WorkflowResourcePolicy.safeDefaults());
            long budget = explicit.getResourceUsage().getPeakOwnedMemoryBytes() + 65536;
            Path output = temporary.newFile().toPath();
            WorkflowOutcome<Void> automatic = searchAreas(version, false, output, memoryPolicy(budget));
            assertEquals(PublicationStatus.COMMITTED, automatic.getPublicationReceipts().get(0).getStatus());
            assertTrue("Candidate rejection must release its shaping reservation, version " + version,
                    automatic.getResourceUsage().getPeakOwnedMemoryBytes() <= budget);
            PageText page = reopen(output).get(0);
            assertEquals("A", page.getText());
            assertEquals(1, page.getTextItems().size());
            assertEquals(155.196, page.getTextItems().get(0).getGeometry().getF().doubleValue(), 0.0001);
        }
    }

    @Test
    public void fontSizeBoundariesPreserveTheIcuGraphemeAndEachDeclaredSize() throws Exception {
        for (String script : new String[] {"Arabic", "Devanagari"}) {
            boolean arabic = "Arabic".equals(script);
            String base = arabic ? "\u0628" : "\u0915";
            String mark = arabic ? "\u064e" : "\u093f";
            Path output = temporary.newFile().toPath();
            compose(output, nativeProvider(), "NotoSans" + script + "-Regular.ttf", Paragraph.version1(36)
                    .maximumWidth(17).text(base, 12).text(mark, 10).text(base, 12).build());
            PageText page = reopen(output).get(0);
            assertEquals(base + mark + base, page.getText());
            List<TextItem> glyphs = page.getTextItems();
            assertEquals(arabic ? 3 : 4, glyphs.size());
            double baseline = arabic ? 151.692 : 151.836;
            double[] advances = arabic ? new double[] {11.916, 0, 11.916} : new double[] {9.144, 2.59, 5.1, 9.144};
            for (int index = 0; index < glyphs.size(); index++) {
                TextItem item = glyphs.get(index);
                boolean finalBase = index == glyphs.size() - 1;
                assertEquals(baseline - (finalBase ? 36 : 0), item.getGeometry().getF().doubleValue(), 0.0001);
                assertEquals(index == 0 || finalBase ? 12 : 10, item.getGeometry().getA().doubleValue(), 0.0001);
                assertEquals(advances[index], item.getGeometry().getAdvanceX().doubleValue(), 0.0001);
            }
        }
    }

    @Test
    public void subsetTagsAreDeterministicDistinctAndAvoidExistingDocumentTags() throws Exception {
        ParagraphFlow[] flows = {flow("NotoSansArabic-Regular.ttf", Paragraph.version1(36).text("\u0628", 12).build()),
            flow("NotoSansArabic-Regular.ttf", Paragraph.version1(36).text("\u0644", 12).build())};
        Set<String> previous = null;
        Path original = null;
        for (int replay = 0; replay < 2; replay++) {
            Path output = temporary.newFile().toPath();
            new DocumentWorkflow(WorkflowEnvironment.builder().provider(nativeProvider()).build()).execute(
                    WorkflowRequest.builder().providerPreference(ProviderPreference.prefer(ShapingRequest.CAPABILITY_ID,
                            HarfBuzzCapabilityProvider.PROVIDER_ID)).target("result", PublicationTarget.path(output))
                            .saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> {
                                for (ParagraphFlow flow : flows) { session.execute(ComposeParagraphs.version1(flow, limits())); }
                                return null;
                            });
            Set<String> tags = subsetTags(output, 2);
            assertEquals(2, tags.size());
            if (previous != null) { assertEquals(previous, tags); }
            previous = tags; original = output;
        }
        Path appended = temporary.newFile().toPath();
        // The same source/glyph map would generate an already present tag without a collision check.
        new DocumentWorkflow(WorkflowEnvironment.builder().provider(nativeProvider()).build()).execute(
                WorkflowRequest.builder().source("primary", DocumentSource.path(original)).primarySource("primary")
                        .providerPreference(ProviderPreference.prefer(ShapingRequest.CAPABILITY_ID, HarfBuzzCapabilityProvider.PROVIDER_ID))
                        .target("result", PublicationTarget.path(appended)).saveMode(SaveMode.REWRITE).executionProfile(profile).build(),
                session -> { session.execute(ComposeParagraphs.version1(flows[0], limits())); return null; });
        assertEquals(3, subsetTags(appended, 3).size());
    }

    private Set<String> subsetTags(Path output, int count) throws Exception {
        List<FontResource> fonts = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .source("primary", DocumentSource.path(output)).primarySource("primary").saveMode(SaveMode.REWRITE)
                .executionProfile(profile).build(), session -> session.query(ExtractImagesAndResources.version1(
                        ResourceExtractionLimits.builder().maximumPages(4).maximumPageTreeNodes(16)
                                .maximumTraversedResourceValues(8192).maximumResourceTraversalDepth(16)
                                .maximumDecodedPixels(0).maximumDecompressedBytes(2 << 20).maximumReturnedBytes(0).build(),
                        ImageByteAccess.NONE)).getFonts()).getResult();
        assertEquals(count, fonts.size());
        Set<String> tags = new TreeSet<String>();
        for (FontResource font : fonts) {
            assertEquals(FontResource.Embedding.EMBEDDED, font.getEmbedding());
            tags.add(font.getSubsetPrefix().get());
        }
        return tags;
    }

    private List<PageText> reopen(Path output) throws Exception {
        return new DocumentWorkflow().execute(WorkflowRequest.builder().source("primary", DocumentSource.path(output))
                .primarySource("primary").saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> session.query(
                        ExtractTextAndStructure.version1(extractionLimits())).getPages()).getResult();
    }

    private HarfBuzzCapabilityProvider nativeProvider() throws Exception {
        return new HarfBuzzCapabilityProvider(Paths.get(System.getProperty("folio.harfBuzzHelper")),
                temporary.newFolder().toPath(), "10.2.0",
                ProviderLimits.bounded(1 << 20, 32 + 24 * 1024, Duration.ofSeconds(10)));
    }

    private BorrowedFont borrowedArabic() throws Exception {
        return new BorrowedFont(Files.readAllBytes(Paths.get(getClass().getResource(
                "/net/zerocloud/pdf/fixtures/noto/NotoSansArabic-Regular.ttf").toURI())));
    }

    private static final class BorrowedFont extends ByteArrayInputStream {
        private boolean closed;
        BorrowedFont(byte[] bytes) { super(bytes); }
        @Override public void close() { closed = true; }
    }

    private static CompositionLimits lifecycleLimits(boolean table) {
        CompositionLimits.Builder result = table ? CompositionLimits.version4().tableLimits(tableLimits().getTableLimits())
                : CompositionLimits.version2();
        return result.maximumRelayouts(1).maximumLayoutAttempts(10000).maximumPages(2).maximumAreas(2)
                .maximumFlowItems(8).maximumInlines(16).maximumLines(32).maximumGeneratedContentBytes(1 << 20)
                .fontLimits(limits().getFontLimits()).graphicLimits(limits().getGraphicLimits()).build();
    }

    private WorkflowOutcome<Void> compose(Path target, CapabilityProvider provider, String fontName,
            Paragraph paragraph) throws Exception {
        ParagraphFlow flow = flow(fontName, paragraph);
        return new DocumentWorkflow(WorkflowEnvironment.builder().provider(provider).build()).execute(WorkflowRequest.builder()
                .providerPreference(ProviderPreference.prefer(ShapingRequest.CAPABILITY_ID, HarfBuzzCapabilityProvider.PROVIDER_ID))
                .target("result", PublicationTarget.path(target)).saveMode(SaveMode.REWRITE).executionProfile(profile).build(),
                session -> { session.execute(ComposeParagraphs.version1(flow, limits())); return null; });
    }

    private ParagraphFlow flow(String fontName, Paragraph paragraph) throws Exception {
        FontSource font = FontSource.path(Paths.get(getClass().getResource(
                "/net/zerocloud/pdf/fixtures/noto/" + fontName).toURI()));
        return ParagraphFlow.version1(FontSelection.explicit(font))
                .page(LayoutPage.version1(240, 192, PageMargins.of(24, 24, 24, 24)))
                .paragraph(paragraph).build();
    }

    private static CompositionLimits limits() {
        return CompositionLimits.builder().maximumPages(2).maximumAreas(2).maximumFlowItems(8)
                .maximumInlines(16).maximumLines(32).maximumGeneratedContentBytes(1 << 20)
                .fontLimits(FontLimits.builder().maximumFontSources(2).maximumSourceBytes(2 << 20)
                        .maximumCodePoints(1024).maximumFallbackChecks(4096)
                        .maximumGeneratedContentBytes(1 << 20).build())
                .graphicLimits(CanvasResourceLimits.builder().maximumEncodedImageBytes(0)
                        .maximumDecodedImagePixels(0).maximumDecodedImageBytes(0).maximumIccProfileBytes(0)
                        .maximumMaskBytes(0).maximumGeneratedContentBytes(0).maximumResourceDeclarations(0)
                        .maximumTransparencyGroupDepth(0).build()).build();
    }

    private static CompositionLimits tableLimits() {
        return CompositionLimits.version3().maximumLayoutAttempts(10000)
                .tableLimits(TableLimits.builder().maximumTables(8).maximumRows(8).maximumColumns(8)
                        .maximumCells(8).maximumGridSlots(64).maximumLayoutWork(100000).build())
                .maximumPages(2).maximumAreas(2).maximumFlowItems(8).maximumInlines(16)
                .maximumLines(32).maximumGeneratedContentBytes(1 << 20)
                .fontLimits(limits().getFontLimits()).graphicLimits(limits().getGraphicLimits()).build();
    }

    private WorkflowOutcome<Void> searchAreas(int version, boolean explicit, Path output,
            WorkflowResourcePolicy policy) throws Exception {
        int rejected = 1000;
        CanvasRectangle[] areas = new CanvasRectangle[rejected + 1];
        Arrays.fill(areas, CanvasRectangle.of(0, 0, 192, 1));
        areas[rejected] = CanvasRectangle.of(0, 0, 192, 144);
        Paragraph paragraph = Paragraph.version1(36).text("A", 12).build();
        FontSelection selection = flow("NotoSans-Regular.ttf", paragraph).getFonts();
        ParagraphFlow.Builder flow = version == 1 ? ParagraphFlow.version1(selection)
                : version == 2 ? ParagraphFlow.version2(selection) : ParagraphFlow.version3(selection);
        flow.page(LayoutPage.version1(240, 192, PageMargins.of(24, 24, 24, 24), areas));
        if (explicit) { for (int area = 0; area < rejected; area++) { flow.areaBreak(); } }
        if (version == 3) {
            flow.table(Table.version1(Table.Layout.FIXED, TableWidth.points(192), TableWidth.auto())
                    .row(TableRow.version1(TableCell.version1().paragraph(paragraph).build())).build());
        } else { flow.paragraph(paragraph); }
        CompositionLimits.Builder limits = version == 1 ? CompositionLimits.builder()
                : version == 2 ? CompositionLimits.version2().maximumLayoutAttempts(10000).maximumRelayouts(0)
                : CompositionLimits.version3().maximumLayoutAttempts(10000).tableLimits(tableLimits().getTableLimits());
        CompositionLimits bounded = limits.maximumPages(1).maximumAreas(rejected + 1).maximumFlowItems(rejected + 1)
                .maximumInlines(1).maximumLines(1).maximumGeneratedContentBytes(1 << 20)
                .fontLimits(limits().getFontLimits()).graphicLimits(limits().getGraphicLimits()).build();
        ComposeParagraphs command = version == 1 ? ComposeParagraphs.version1(flow.build(), bounded)
                : version == 2 ? ComposeParagraphs.version2(flow.build(), bounded) : ComposeParagraphs.version3(flow.build(), bounded);
        return new DocumentWorkflow(WorkflowEnvironment.builder().provider(nativeProvider()).build()).execute(
                WorkflowRequest.builder().providerPreference(ProviderPreference.prefer(ShapingRequest.CAPABILITY_ID,
                        HarfBuzzCapabilityProvider.PROVIDER_ID)).target("result", PublicationTarget.path(output))
                        .resourcePolicy(policy).saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> {
                            session.execute(command); return null;
                        });
    }

    private static WorkflowResourcePolicy memoryPolicy(long bytes) {
        WorkflowResourcePolicy defaults = WorkflowResourcePolicy.safeDefaults();
        return WorkflowResourcePolicy.builder().maximumOwnedMemoryBytes(bytes).maximumInputBytes(defaults.getMaximumInputBytes())
                .maximumPages(defaults.getMaximumPages()).maximumObjects(defaults.getMaximumObjects())
                .maximumNestingDepth(defaults.getMaximumNestingDepth()).maximumDecompressedBytes(defaults.getMaximumDecompressedBytes())
                .maximumDecodedPixels(defaults.getMaximumDecodedPixels()).maximumTemporaryStorageBytes(defaults.getMaximumTemporaryStorageBytes())
                .maximumElapsedTime(defaults.getMaximumElapsedTime()).maximumConcurrentWorkflows(defaults.getMaximumConcurrentWorkflows()).build();
    }

    private WorkflowOutcome<Void> repeatedRelayout(int repeats, Path output, WorkflowResourcePolicy policy) throws Exception {
        char[] letters = new char[32];
        Arrays.fill(letters, '\u0628');
        LayoutPage wide = LayoutPage.version1(240, 1300, PageMargins.of(24, 24, 24, 24));
        Paragraph paragraph = Paragraph.version1(36).text(new String(letters), 12).build();
        ParagraphFlow flow = ParagraphFlow.version2(flow("NotoSansArabic-Regular.ttf", paragraph).getFonts())
                .page(wide).paragraph(paragraph).build();
        CompositionLimits bounded = CompositionLimits.version2().maximumRelayouts(33).maximumLayoutAttempts(10000)
                .maximumPages(1).maximumAreas(1).maximumFlowItems(1).maximumInlines(1).maximumLines(32)
                .maximumGeneratedContentBytes(1 << 20).fontLimits(limits().getFontLimits())
                .graphicLimits(limits().getGraphicLimits()).build();
        return new DocumentWorkflow(WorkflowEnvironment.builder().provider(nativeProvider()).build()).execute(
                WorkflowRequest.builder().providerPreference(ProviderPreference.prefer(ShapingRequest.CAPABILITY_ID,
                        HarfBuzzCapabilityProvider.PROVIDER_ID)).target("result", PublicationTarget.path(output))
                        .resourcePolicy(policy).saveMode(SaveMode.REWRITE).executionProfile(profile).build(), session -> {
                            session.execute(ComposeParagraphs.version2(flow, bounded));
                            for (int index = 0; index < repeats; index++) {
                                session.execute(RelayoutParagraphs.version1(wide));
                            }
                            // Isolated forms require a new subset after the repeated joined layouts.
                            session.execute(RelayoutParagraphs.version1(
                                    LayoutPage.version1(60, 1300, PageMargins.of(24, 24, 24, 24))));
                            return null;
                        });
    }

    private static ExtractionLimits extractionLimits() {
        return ExtractionLimits.builder().maximumPages(2).maximumPageTreeNodes(16)
                .maximumContentStreams(64).maximumContentStreamDepth(8).maximumDecodedBytes(1 << 20)
                .maximumTextItems(1024).maximumUnicodeCodePoints(1024).maximumToUnicodeMappings(1024)
                .maximumFontDataEntries(4096).maximumMarkedContentSequences(64)
                .maximumMarkedContentDepth(4).maximumStructureElements(8).maximumStructureItems(8)
                .maximumStructureDepth(4).maximumRoleMappings(4).build();
    }

    private static byte[] blankPdf(String version) {
        String[] objects = {"<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 72 72] /Resources << >> >>"};
        StringBuilder pdf = new StringBuilder("%PDF-" + version + "\n%FolioT29Fixture\n");
        int[] offsets = new int[objects.length];
        for (int index = 0; index < objects.length; index++) {
            offsets[index] = pdf.length();
            pdf.append(index + 1).append(" 0 obj\n").append(objects[index]).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 4\n0000000000 65535 f \n");
        for (int offset : offsets) { pdf.append(String.format(Locale.ROOT, "%010d 00000 n \n", offset)); }
        pdf.append("trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
