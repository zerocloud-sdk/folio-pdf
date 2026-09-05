package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import net.zerocloud.pdf.CharacterMapping;
import net.zerocloud.pdf.PasswordCredential;
import net.zerocloud.pdf.PasswordSecurityPolicy;
import net.zerocloud.pdf.DocumentPermissions;
import net.zerocloud.pdf.PdfOutputPolicy;
import net.zerocloud.pdf.PdfVersion;
import net.zerocloud.pdf.RenderOptions;
import net.zerocloud.pdf.RenderedPage;
import net.zerocloud.pdf.query.RenderPage;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ExtractionLimits;
import net.zerocloud.pdf.PageText;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.TextItem;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.composition.CanvasColor;
import net.zerocloud.pdf.composition.CanvasColorSpace;
import net.zerocloud.pdf.composition.CanvasProgram;
import net.zerocloud.pdf.composition.CanvasTransparencyGroup;
import net.zerocloud.pdf.composition.CanvasWindingRule;
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
import net.zerocloud.pdf.composition.TabStop;
import net.zerocloud.pdf.composition.command.ComposeParagraphs;
import net.zerocloud.pdf.composition.command.FlushParagraphs;
import net.zerocloud.pdf.composition.command.RelayoutParagraphs;
import net.zerocloud.pdf.query.ExtractTextAndStructure;
import net.zerocloud.pdf.query.PageCount;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** T25 public transaction contracts; expected widths and coordinates are hand calculated. */
@RunWith(Parameterized.class)
public final class ParagraphPaginationWorkflowTest {
    private static final String CAPABILITY = "composition.layout.paragraph-pagination";
    private static final double EPSILON = 0.0001;
    private static final byte[] SENTINEL = {31, 41, 59};
    private final WorkflowExecutionProfile profile;
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> profiles() {
        return Arrays.asList(new Object[][] {{WorkflowExecutionProfile.IN_PROCESS},
            {WorkflowExecutionProfile.HARDENED_WORKER}});
    }
    public ParagraphPaginationWorkflowTest(WorkflowExecutionProfile profile) { this.profile = profile; }

    @Test public void indentsAndWidthCapContinueAcrossColumnsAndPages() throws Exception {
        ParagraphFlow flow = columns(24).paragraph(Paragraph.version2(10).text("AAAAAAAAAAAAA", 10)
                .maximumWidth(24).indentation(6, 6, 6).build()).build();
        List<PageText> pages = read(publish(flow));
        texts(pages, "AAAAAAA", "AAAAAA");
        position(pages, 0, 0, 22, 23);
        position(pages, 0, 1, 16, 13);
        position(pages, 0, 3, 56, 23);
        position(pages, 0, 5, 56, 13);
        position(pages, 1, 0, 16, 63);
        position(pages, 1, 2, 16, 53);
        position(pages, 1, 4, 16, 43);
    }

    @Test public void hangingFirstLineDoesNotRepeatOnTheNextPage() throws Exception {
        ParagraphFlow flow = flow(page(18, 20), page(18, 20))
                .paragraph(Paragraph.version2(10).text("AAAAAAAAA", 10).indentation(6, 0, -6).build()).build();
        List<PageText> pages = read(publish(flow));
        texts(pages, "AAAAA", "AAAA");
        position(pages, 0, 0, 10, 23);
        position(pages, 0, 3, 16, 13);
        position(pages, 1, 0, 16, 23);
    }

    @Test public void explicitTabsRemainFixedAcrossColumnsAndPages() throws Exception {
        ParagraphFlow flow = columns(36).paragraph(Paragraph.version2(10).text("A\tB\nA\tB\nA\tB\nA\tB\nA\tB", 10)
                .tabStop(TabStop.version1(18, TabStop.Alignment.LEFT)).alignment(Paragraph.Alignment.RIGHT).build()).build();
        List<PageText> pages = read(publish(flow));
        texts(pages, "ABABABAB", "AB");
        position(pages, 0, 1, 28, 23);
        position(pages, 0, 5, 68, 23);
        position(pages, 1, 0, 10, 63);
        position(pages, 1, 1, 28, 63);
    }

    @Test public void eachTabAlignmentAndDefaultGridCrossesPages() throws Exception {
        TabStop[] stops = {TabStop.version1(24, TabStop.Alignment.LEFT),
            TabStop.version1(24, TabStop.Alignment.CENTER), TabStop.version1(24, TabStop.Alignment.RIGHT),
            TabStop.anchored(24, 'B'), TabStop.anchored(24, '\u03a9'), null};
        double[] starts = {34, 27.75, 21.5, 28, 21.5, 46};
        for (int index = 0; index < stops.length; index++) {
            Paragraph.Builder paragraph = Paragraph.version2(10).text("A\tAB\nA\tAB", 10);
            if (stops[index] != null) { paragraph.tabStop(stops[index]); }
            List<PageText> pages = read(publish(flow(page(60, 10), page(60, 10)).paragraph(paragraph.build()).build()));
            texts(pages, "AAB", "AAB");
            position(pages, 0, 1, starts[index], 13);
            position(pages, 1, 1, starts[index], 13);
        }
    }

    @Test public void consecutiveTabsSkipStopsThatWouldMoveThePenBackwards() throws Exception {
        List<PageText> pages = read(publish(flow(page(60, 10), page(60, 10))
                .paragraph(Paragraph.version2(10).text("\t\tB\n\t\tB", 10).tabInterval(12)
                        .tabStop(TabStop.version1(12, TabStop.Alignment.LEFT))
                        .tabStop(TabStop.version1(18, TabStop.Alignment.RIGHT)).build()).build()));
        texts(pages, "B", "B");
        position(pages, 0, 0, 34, 13);
        position(pages, 1, 0, 34, 13);
    }

    @Test public void tabFieldMovesIntactToAWiderArea() throws Exception {
        List<PageText> pages = read(publish(flow(page(18, 10), page(48, 10))
                .paragraph(Paragraph.version2(10).text("\tAB", 10).tabInterval(24).build()).build()));
        texts(pages, "", "AB");
        position(pages, 1, 0, 34, 13);
    }

    @Test public void keepTogetherMovesAnEntireParagraphToTheNextPage() throws Exception {
        List<PageText> pages = read(publish(columns(36).paragraph(paragraph("A\nA\nA"))
                .paragraph(Paragraph.version2(10).text("B\nB", 10).keepTogether(true).build()).build()));
        texts(pages, "AAA", "BB");
        position(pages, 0, 2, 50, 23);
        position(pages, 1, 0, 10, 63);
        position(pages, 1, 1, 10, 53);
    }

    @Test public void keepWithNextMovesTheHeadingWithTheFollowingFragment() throws Exception {
        List<PageText> pages = read(publish(columns(36).paragraph(paragraph("A\nA\nA"))
                .paragraph(Paragraph.version2(10).text("B", 10).keepWithNext(true).build())
                .paragraph(paragraph("\u03a9\n\u03a9")).build()));
        texts(pages, "AAA", "B\u03a9\u03a9");
        position(pages, 1, 0, 10, 63);
        position(pages, 1, 1, 10, 52.8);
    }

    @Test public void keepChainAndFollowingKeepTogetherAreSolvedJointly() throws Exception {
        List<PageText> pages = read(publish(columns(36).paragraph(paragraph("A\nA\nA"))
                .paragraph(Paragraph.version2(10).text("B", 10).keepWithNext(true).build())
                .paragraph(Paragraph.version2(10).text("B", 10).keepWithNext(true).build())
                .paragraph(Paragraph.version2(10).text("\u03a9\n\u03a9", 10).keepTogether(true).build()).build()));
        texts(pages, "AAA", "BB\u03a9\u03a9");
        position(pages, 1, 2, 10, 42.8);
    }

    @Test public void widowMinimumMovesOneEarlierLineAcrossThePageBoundary() throws Exception {
        List<PageText> pages = read(publish(flow(page(36, 30), page(36, 30))
                .paragraph(Paragraph.version2(10).text("A\nA\nA\nB", 10).widows(2).build()).build()));
        texts(pages, "AA", "AB");
        position(pages, 0, 1, 10, 23);
        position(pages, 1, 0, 10, 33);
        position(pages, 1, 1, 10, 23);
    }

    @Test public void orphanMinimumMovesAParagraphPastAnInsufficientRemainder() throws Exception {
        List<PageText> pages = read(publish(flow(page(36, 30), page(36, 30))
                .paragraph(paragraph("A\nA"))
                .paragraph(Paragraph.version2(10).text("B\nB\n\u03a9", 10).orphans(2).build()).build()));
        texts(pages, "AA", "BB\u03a9");
        position(pages, 1, 0, 10, 33);
        position(pages, 1, 2, 10, 12.8);
    }

    @Test public void widowAndOrphanCountsRecomputeForDifferentPageWidths() throws Exception {
        List<PageText> pages = read(publish(flow(page(18, 30), page(12, 30), page(30, 30))
                .paragraph(Paragraph.version2(10).text("AAAAAAAAAAAAA", 10).widows(2).orphans(2).build()).build()));
        texts(pages, "AAAAAAAAA", "AAAA");
        position(pages, 0, 6, 10, 13);
        position(pages, 1, 2, 10, 23);
    }

    @Test public void shortUnsplitParagraphIgnoresFragmentMinimaAndFinalKeepIsVacuous() throws Exception {
        texts(read(publish(flow(page(6, 10)).paragraph(Paragraph.version2(10).text("A", 10)
                .widows(8).orphans(8).keepWithNext(true).build()).build())), "A");
    }

    @Test public void impossibleKeepsAndFragmentMinimaFailWithoutPublishing() throws Exception {
        expectFailure(flow(page(36, 20), page(36, 20)).paragraph(Paragraph.version2(10).text("A\nA\nA", 10)
                .keepTogether(true).build()).build(), limits().build(), DocumentFailureCode.COMPOSITION_CONSTRAINT_UNSATISFIED);
        expectFailure(flow(page(36, 10), page(36, 10)).paragraph(Paragraph.version2(10).text("A", 10)
                .keepWithNext(true).build()).paragraph(paragraph("B")).build(), limits().build(),
                DocumentFailureCode.COMPOSITION_CONSTRAINT_UNSATISFIED);
        expectFailure(flow(page(36, 30), page(36, 10)).paragraph(Paragraph.version2(10).text("A\nA\nA\nA", 10)
                .widows(2).orphans(3).build()).build(), limits().build(), DocumentFailureCode.COMPOSITION_CONSTRAINT_UNSATISFIED);
        expectFailure(flow(page(36, 30), page(36, 30)).paragraph(Paragraph.version2(10).text("A", 10)
                .keepWithNext(true).build()).areaBreak().paragraph(paragraph("B")).build(), limits().build(),
                DocumentFailureCode.COMPOSITION_CONSTRAINT_UNSATISFIED);
    }

    @Test public void wrapAndVisibleOverflowPreserveEveryCharacterAcrossPages() throws Exception {
        texts(read(publish(flow(page(12, 30), page(12, 30)).paragraph(paragraph("AAAAAAAAAAAA")).build())),
                "AAAAAA", "AAAAAA");
        List<PageText> visible = read(publish(columns(18).paragraph(Paragraph.version2(10)
                .text("AAAA\nAAAA\nAAAA\nAAAA\nAAAA", 10).overflow(Paragraph.Overflow.VISIBLE).build()).build()));
        texts(visible, "AAAAAAAAAAAAAAAA", "AAAA");
        position(visible, 0, 3, 28, 23);
        position(visible, 1, 3, 28, 63);
    }

    @Test public void rejectOverflowUsesAWiderPageAndExhaustionNeverTruncates() throws Exception {
        List<PageText> pages = read(publish(flow(page(18, 30), page(30, 30))
                .paragraph(Paragraph.version2(10).text("AAAA", 10).overflow(Paragraph.Overflow.REJECT).build()).build()));
        texts(pages, "", "AAAA");
        expectFailure(flow(page(18, 30), page(18, 30)).paragraph(Paragraph.version2(10)
                .text("AAAA", 10).overflow(Paragraph.Overflow.REJECT).build()).build(), limits().build(),
                DocumentFailureCode.COMPOSITION_AREA_EXHAUSTED);
        expectFailure(flow(page(6, 10), page(6, 10)).paragraph(paragraph("AAA")).build(), limits().build(),
                DocumentFailureCode.COMPOSITION_AREA_EXHAUSTED);
    }

    @Test public void relayoutCanShrinkAndGrowTheTailWithoutChangingEarlierPages() throws Exception {
        Path output = path();
        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(create(output).build(), session -> {
            session.execute(AddBlankPage.INSTANCE);
            session.execute(ComposeParagraphs.version2(flow(page(18, 30), page(18, 30))
                    .paragraph(paragraph("AAAAAAAAAAAAA")).build(), limits().build()));
            assertEquals(3, session.query(PageCount.INSTANCE).intValue());
            session.execute(RelayoutParagraphs.version1(page(36, 30)));
            texts(extract(session), "", "AAAAAAAAAAAAA");
            assertEquals(2, session.query(PageCount.INSTANCE).intValue());
            session.execute(RelayoutParagraphs.version1(page(12, 20), page(12, 20), page(12, 20), page(12, 20)));
            assertEquals(5, session.query(PageCount.INSTANCE).intValue());
            return null;
        });
        assertEquals(CAPABILITY, outcome.getCapabilityId());
        List<PageText> pages = read(output);
        texts(pages, "", "AAAA", "AAAA", "AAAA", "A");
        position(pages, 4, 0, 10, 23);
    }

    @Test public void failedRelayoutPreservesLayoutAndConsumesItsAttempt() throws Exception {
        Path output = path();
        new DocumentWorkflow().execute(create(output).build(), session -> {
            session.execute(ComposeParagraphs.version2(flow(page(18, 20)).paragraph(paragraph("AAAA")).build(),
                    limits().maximumRelayouts(1).build()));
            failure(session, RelayoutParagraphs.version1(page(6, 10)), DocumentFailureCode.COMPOSITION_AREA_EXHAUSTED);
            texts(extract(session), "AAAA");
            failure(session, RelayoutParagraphs.version1(page(36, 20)), DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED);
            return null;
        });
        texts(read(output), "AAAA");
    }

    @Test public void excessiveRelayoutDeclarationsPreserveContentAndConsumeTheAttempt() throws Exception {
        Path output = path();
        new DocumentWorkflow().execute(create(output).build(), session -> {
            session.execute(ComposeParagraphs.version2(flow(page(18, 20)).paragraph(paragraph("AAAA")).build(),
                    limits().maximumPages(1).maximumRelayouts(1).build()));
            failure(session, RelayoutParagraphs.version1(page(36, 20), page(36, 20)),
                    DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED);
            texts(extract(session), "AAAA");
            failure(session, RelayoutParagraphs.version1(page(36, 20)),
                    DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED);
            return null;
        });
        texts(read(output), "AAAA");
    }

    @Test public void relayoutUsesTheOriginalFontSnapshotAndKeepsCallerStreamOpen() throws Exception {
        Path source = path(); Files.write(source, font("FolioPrimary"));
        TrackingStream stream = new TrackingStream(font("FolioFallback"));
        FontSelection selection = FontSelection.explicit(FontSource.path(source), FontSource.stream(stream));
        Path output = path();
        new DocumentWorkflow().execute(create(output).build(), session -> {
            ParagraphFlow flow = ParagraphFlow.version2(selection).page(page(18, 20)).page(page(18, 20))
                    .paragraph(paragraph("AA\u03a9AA\u03a9")).build();
            session.execute(ComposeParagraphs.version2(flow, limits().build()));
            write(source, SENTINEL);
            assertEquals(0, stream.available());
            session.execute(RelayoutParagraphs.version1(page(60, 20)));
            return null;
        });
        texts(read(output), "AA\u03a9AA\u03a9");
        assertFalse(stream.closed);
    }

    @Test public void immediateAndExplicitFlushSealTheFlowWithoutEarlyPublication() throws Exception {
        for (ComposeParagraphs.FlushMode mode : ComposeParagraphs.FlushMode.values()) {
            Path output = path(); Files.write(output, SENTINEL);
            new DocumentWorkflow().execute(create(output).build(), session -> {
                session.execute(ComposeParagraphs.version2(flow(page(18, 20)).paragraph(paragraph("AA")).build(),
                        limits().build(), mode));
                if (mode == ComposeParagraphs.FlushMode.BUFFERED) { session.execute(FlushParagraphs.version1()); }
                session.execute(FlushParagraphs.version1());
                failure(session, RelayoutParagraphs.version1(page(36, 20)), DocumentFailureCode.COMPOSITION_RELAYOUT_UNSAFE);
                assertArrayEquals(SENTINEL, bytes(output));
                return null;
            });
            texts(read(output), "AA");
        }
    }

    @Test public void laterMutationSealsRelayoutAndNewCompositionEstablishesANewBuffer() throws Exception {
        Path output = path();
        new DocumentWorkflow().execute(create(output).build(), session -> {
            ComposeParagraphs command = ComposeParagraphs.version2(flow(page(18, 20)).paragraph(paragraph("AA")).build(),
                    limits().build());
            session.executeBatch(Arrays.asList(command, AddBlankPage.INSTANCE));
            failure(session, RelayoutParagraphs.version1(page(36, 20)), DocumentFailureCode.COMPOSITION_RELAYOUT_UNSAFE);
            session.execute(command);
            session.execute(RelayoutParagraphs.version1(page(36, 20)));
            return null;
        });
        texts(read(output), "AA", "", "AA");
    }

    @Test public void publicationExpiresSessionAndReopenedPdfHasNoRelayoutState() throws Exception {
        Path output = path(); final DocumentSession[] retained = new DocumentSession[1];
        new DocumentWorkflow().execute(create(output).build(), session -> {
            retained[0] = session;
            session.execute(ComposeParagraphs.version2(flow(page(18, 20)).paragraph(paragraph("AA")).build(), limits().build()));
            return null;
        });
        try { retained[0].execute(RelayoutParagraphs.version1(page(36, 20))); fail("Expected expired Session"); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("no longer active")); }
        new DocumentWorkflow().execute(open(output).build(), session -> {
            failure(session, RelayoutParagraphs.version1(page(36, 20)), DocumentFailureCode.COMPOSITION_RELAYOUT_UNSAFE);
            return null;
        });
        texts(read(output), "AA");
    }

    @Test public void keepTogetherIncludesGraphicHeightAndRelayoutRetainsTheGraphic() throws Exception {
        CanvasTransparencyGroup graphic = CanvasTransparencyGroup.version1(CanvasRectangle.of(0, 0, 1, 1),
                CanvasColorSpace.deviceRgb(), true, false, CanvasProgram.version2().setFillColor(CanvasColor.rgb(0.2, 0.4, 0.8))
                        .moveTo(0, 0).lineTo(1, 0).lineTo(1, 1).lineTo(0, 1).closePath().fill(CanvasWindingRule.NONZERO).build());
        Paragraph paragraph = Paragraph.version2(10).text("A\n", 10).graphic(graphic, 8, 15).text("B", 10).keepTogether(true).build();
        Path output = path();
        new DocumentWorkflow().execute(create(output).build(), session -> {
            session.execute(ComposeParagraphs.version2(flow(page(24, 20), page(24, 30)).paragraph(paragraph).build(), limits().build()));
            texts(extract(session), "", "AB");
            position(extract(session), 1, 1, 18, 15);
            session.execute(RelayoutParagraphs.version1(page(24, 30)));
            return null;
        });
        List<PageText> pages = read(output);
        texts(pages, "AB");
        position(pages, 0, 0, 10, 33);
        position(pages, 0, 1, 18, 15);
        byte[] png = new DocumentWorkflow().execute(open(output).build(), session -> {
            try (RenderedPage rendered = session.query(RenderPage.version1(1, RenderOptions.builder().dpi(72).build()))) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(); rendered.writePngTo(bytes); return bytes.toByteArray();
            }
        }).getResult();
        assertEquals(0x3366cc, javax.imageio.ImageIO.read(new ByteArrayInputStream(png)).getRGB(14, 28) & 0xffffff);
    }

    @Test public void unsignedIncrementalRelayoutPreservesTheOriginalRevisionAndEarlierPages() throws Exception {
        Path source = publish(flow(page(12, 20)).paragraph(paragraph("AA")).build());
        byte[] original = bytes(source); Path output = path();
        new DocumentWorkflow().execute(open(source).target("result", PublicationTarget.path(output))
                .saveMode(SaveMode.INCREMENTAL).build(), session -> {
                    session.execute(ComposeParagraphs.version2(flow(page(12, 20), page(12, 20))
                            .paragraph(paragraph("BBBB")).build(), limits().build()));
                    session.execute(RelayoutParagraphs.version1(page(30, 20)));
                    return null;
                });
        texts(read(output), "AA", "BBBB");
        assertArrayEquals(original, bytes(source));
        assertArrayEquals(original, Arrays.copyOf(bytes(output), original.length));
    }

    @Test public void failedRelayoutStopsBatchBeforeReadingLaterCallerFonts() throws Exception {
        TrackingStream later = new TrackingStream(font("FolioPrimary"));
        ParagraphFlow next = ParagraphFlow.version2(FontSelection.explicit(FontSource.stream(later)))
                .page(page(24, 20)).paragraph(paragraph("B")).build();
        Path output = path();
        new DocumentWorkflow().execute(create(output).build(), session -> {
            session.execute(ComposeParagraphs.version2(flow(page(12, 20)).paragraph(paragraph("AAA")).build(), limits().build()));
            try {
                session.executeBatch(Arrays.asList(RelayoutParagraphs.version1(page(6, 10)), ComposeParagraphs.version2(next, limits().build())));
                fail("Expected first batch command to fail");
            } catch (DocumentFailure expected) { assertEquals(DocumentFailureCode.COMPOSITION_AREA_EXHAUSTED, expected.getCode()); }
            assertEquals(972, later.available());
            assertFalse(later.closed);
            return null;
        });
        texts(read(output), "AAA");
    }

    @Test public void incomingAndOutgoingFragmentMinimaApplyToEveryPage() throws Exception {
        texts(read(publish(flow(page(18, 30), page(18, 20), page(18, 30))
                .paragraph(Paragraph.version2(10).text("A\nA\nA\nA\nA\nB\nB", 10).widows(2).orphans(2).build()).build())),
                "AAA", "AA", "BB");
    }

    @Test public void exactAttemptBoundSucceedsAndTheFirstExcessFails() throws Exception {
        ParagraphFlow flow = flow(page(6, 10)).paragraph(paragraph("A")).build();
        texts(read(publish(flow, limits().maximumLayoutAttempts(2).build())), "A");
        expectFailure(flow, limits().maximumLayoutAttempts(1).build(), DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED);
    }

    @Test public void finiteSearchAndLineBoundsReportStableFailures() throws Exception {
        ParagraphFlow flow = flow(page(12, 20), page(12, 20)).paragraph(paragraph("AAAA")).build();
        texts(read(publish(flow, limits().maximumLines(2).build())), "AAAA");
        expectFailure(flow, limits().maximumLines(1).build(), DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED);
        expectFailure(flow, limits().maximumLayoutAttempts(0).build(), DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED);
        expectFailure(flow(page(6, 10), page(6, 10)).paragraph(Paragraph.version2(10).text("AAA", 10)
                .keepTogether(true).build()).build(), limits().maximumLayoutAttempts(1).build(),
                DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED);
    }

    @Test public void invalidAdvancedDeclarationsFailBeforePublication() throws Exception {
        Paragraph[] invalid = {Paragraph.version2(10).text("A", 10).indentation(0, 0, -1).build(),
            Paragraph.version2(10).text("A", 10).widows(0).build(),
            Paragraph.version2(10).text("A", 10).orphans(-1).build(),
            Paragraph.version2(10).text("A", 10).tabInterval(Double.NaN).build(),
            Paragraph.version2(10).text("A", 10).tabStop(TabStop.version1(12, TabStop.Alignment.LEFT))
                    .tabStop(TabStop.version1(12, TabStop.Alignment.RIGHT)).build(),
            Paragraph.version1(10).text("A", 10).keepTogether(true).build(),
            Paragraph.version2(10).text("A\rB", 10).build()};
        for (Paragraph paragraph : invalid) {
            expectFailure(flow(page(36, 30)).paragraph(paragraph).build(), limits().build(), DocumentFailureCode.COMPOSITION_INVALID);
        }
    }

    @Test
    public void existingSignaturesRejectCompositionBeforeReadingCallerFonts() throws Exception {
        for (SaveMode mode : SaveMode.values()) {
            byte[] signed = ProjectOwnedSignatureFixtures.ordinaryApprovalSignature();
            Path source = path(); Files.write(source, signed);
            Path output = path(); Files.write(output, SENTINEL);
            TrackingStream font = new TrackingStream(font("FolioPrimary"));
            ParagraphFlow flow = ParagraphFlow.version2(FontSelection.explicit(FontSource.stream(font)))
                    .page(page(60, 20)).paragraph(Paragraph.version2(10).text("A", 10).build()).build();
            try {
                new DocumentWorkflow().execute(open(source).target("result", PublicationTarget.path(output))
                        .saveMode(mode).build(), session -> {
                            session.execute(ComposeParagraphs.version2(flow, limits().build()));
                        session.execute(RelayoutParagraphs.version1(page(72, 20)));
                        session.execute(FlushParagraphs.version1()); return null;
                        });
                fail("Signed composition accepted");
            } catch (DocumentFailure failure) {
                assertEquals(mode == SaveMode.REWRITE ? DocumentFailureCode.SIGNED_REWRITE_REJECTED
                        : DocumentFailureCode.SIGNATURE_POLICY_REJECTED, failure.getCode());
            }
            assertEquals(972, font.available());
            assertFalse(font.closed);
            assertArrayEquals(SENTINEL, Files.readAllBytes(output));
        }
    }

    @Test
    public void passwordUserRequiresBothModificationAndAssemblyPermission() throws Exception {
        try (PasswordCredential owner = PasswordCredential.of("t25-owner".toCharArray());
                PasswordCredential user = PasswordCredential.of("t25-user".toCharArray())) {
            for (int permissions = 0; permissions < 4; permissions++) {
                Path source = path();
                PasswordSecurityPolicy security = PasswordSecurityPolicy.builder(owner, user)
                        .permissions(DocumentPermissions.builder().allowModification((permissions & 1) != 0)
                                .allowDocumentAssembly((permissions & 2) != 0).build()).build();
                new DocumentWorkflow().execute(create(source).outputPolicy(
                        PdfOutputPolicy.version(PdfVersion.PDF_1_7).withPasswordSecurity(security)).build(),
                        session -> { session.execute(AddBlankPage.INSTANCE); return null; });
                Path output = path(); Files.write(output, SENTINEL);
                TrackingStream font = new TrackingStream(font("FolioPrimary"));
                ParagraphFlow flow = ParagraphFlow.version2(FontSelection.explicit(FontSource.stream(font)))
                        .page(page(60, 20)).paragraph(Paragraph.version2(10).text("A", 10).build()).build();
                WorkflowRequest request = WorkflowRequest.builder()
                        .source("primary", DocumentSource.path(source).withCredential(user)).primarySource("primary")
                        .target("result", PublicationTarget.path(output)).saveMode(SaveMode.INCREMENTAL)
                        .executionProfile(profile).build();
                try {
                    WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(request, session -> {
                        session.execute(ComposeParagraphs.version2(flow, limits().build()));
                        session.execute(RelayoutParagraphs.version1(page(72, 20)));
                        session.execute(FlushParagraphs.version1()); return null;
                    });
                    assertEquals("Both permissions are necessary", 3, permissions);
                    assertEquals(PublicationStatus.COMMITTED, outcome.getPublicationReceipts().get(0).getStatus());
                    int pages = new DocumentWorkflow().execute(WorkflowRequest.builder()
                            .source("primary", DocumentSource.path(output).withCredential(owner)).primarySource("primary")
                            .saveMode(SaveMode.REWRITE).executionProfile(profile).build(),
                            session -> session.query(PageCount.INSTANCE)).getResult();
                    assertEquals(2, pages);
                } catch (DocumentFailure failure) {
                    if (permissions == 3) { throw failure; }
                    assertEquals(DocumentFailureCode.DOCUMENT_PERMISSION_DENIED, failure.getCode());
                    assertEquals(CAPABILITY, failure.getCapabilityId());
                    assertEquals(972, font.available());
                    assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(0).getStatus());
                    assertArrayEquals(SENTINEL, Files.readAllBytes(output));
                }
                assertFalse(font.closed);
            }
        }
    }

    private Path publish(ParagraphFlow flow) throws Exception { return publish(flow, limits().build()); }
    private Path publish(ParagraphFlow flow, CompositionLimits limits) throws Exception {
        Path output = path();
        WorkflowOutcome<Integer> result = new DocumentWorkflow().execute(create(output).build(), session -> {
            session.execute(ComposeParagraphs.version2(flow, limits)); return session.query(PageCount.INSTANCE);
        });
        assertEquals(CAPABILITY, result.getCapabilityId());
        assertEquals(profile, result.getExecutionProfile());
        assertEquals(PublicationStatus.COMMITTED, result.getPublicationReceipts().get(0).getStatus());
        return output;
    }
    private void expectFailure(ParagraphFlow flow, CompositionLimits limits, DocumentFailureCode code) throws Exception {
        Path output = path(); Files.write(output, SENTINEL);
        try {
            new DocumentWorkflow().execute(create(output).build(), session -> {
                session.execute(ComposeParagraphs.version2(flow, limits)); return null;
            });
            fail("Expected " + code);
        } catch (DocumentFailure failure) {
            assertEquals(code, failure.getCode()); assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(0).getStatus());
        }
        assertArrayEquals(SENTINEL, bytes(output));
    }
    private static void failure(DocumentSession session, DocumentCommand command, DocumentFailureCode code) throws DocumentFailure {
        try { session.execute(command); fail("Expected " + code); }
        catch (DocumentFailure failure) { assertEquals(code, failure.getCode()); assertEquals(CAPABILITY, failure.getCapabilityId()); }
    }
    private List<PageText> read(Path source) throws Exception {
        return new DocumentWorkflow().execute(open(source).build(), ParagraphPaginationWorkflowTest::extract).getResult();
    }
    private static List<PageText> extract(DocumentSession session) throws DocumentFailure {
        return session.query(ExtractTextAndStructure.version1(ExtractionLimits.builder()
                .maximumPages(16).maximumPageTreeNodes(64).maximumContentStreams(512).maximumContentStreamDepth(8)
                .maximumDecodedBytes(1 << 20).maximumTextItems(10000).maximumUnicodeCodePoints(10000)
                .maximumToUnicodeMappings(64).maximumFontDataEntries(512).maximumMarkedContentSequences(8)
                .maximumMarkedContentDepth(4).maximumStructureElements(8).maximumStructureItems(8)
                .maximumStructureDepth(4).maximumRoleMappings(4).build())).getPages();
    }
    private static void texts(List<PageText> pages, String... expected) {
        assertEquals(expected.length, pages.size());
        for (int index = 0; index < expected.length; index++) {
            StringBuilder text = new StringBuilder();
            for (TextItem item : pages.get(index).getTextItems()) { text.append(item.getCharacterMapping().getUnicode().get()); }
            assertEquals("Page " + (index + 1), expected[index], text.toString());
        }
    }
    private static void position(List<PageText> pages, int page, int glyph, double x, double y) {
        TextItem item = pages.get(page).getTextItems().get(glyph);
        assertEquals(CharacterMapping.Confidence.EXPLICIT, item.getCharacterMapping().getConfidence());
        assertEquals(x, item.getGeometry().getE().doubleValue(), EPSILON);
        assertEquals(y, item.getGeometry().getF().doubleValue(), EPSILON);
        String cp = item.getCharacterMapping().getUnicode().get();
        assertEquals(cp.equals("A") ? 6 : cp.equals("B") ? 6.5 : 7, item.getGeometry().getAdvanceX().doubleValue(), EPSILON);
    }
    private WorkflowRequest.Builder create(Path output) {
        return WorkflowRequest.builder().target("result", PublicationTarget.path(output)).saveMode(SaveMode.REWRITE).executionProfile(profile);
    }
    private WorkflowRequest.Builder open(Path source) {
        return WorkflowRequest.builder().source("primary", DocumentSource.path(source)).primarySource("primary")
                .saveMode(SaveMode.REWRITE).executionProfile(profile);
    }
    private Path path() throws Exception { return temporary.newFile().toPath(); }
    private static Paragraph paragraph(String text) { return Paragraph.version2(10).text(text, 10).build(); }
    private static LayoutPage page(double width, double height) {
        return LayoutPage.version1(width + 20, height + 20, PageMargins.of(10, 10, 10, 10));
    }
    private static ParagraphFlow.Builder columns(double width) {
        return flow(LayoutPage.version1(100, 80, PageMargins.of(10, 10, 10, 10),
                CanvasRectangle.of(0, 0, width, 20), CanvasRectangle.of(40, 0, 40 + width, 20)), page(80, 60));
    }
    private static ParagraphFlow.Builder flow(LayoutPage... pages) {
        ParagraphFlow.Builder flow = ParagraphFlow.version2(FontSelection.explicit(
                FontSource.bytes(font("FolioPrimary")), FontSource.bytes(font("FolioFallback"))));
        for (LayoutPage page : pages) { flow.page(page); }
        return flow;
    }
    private static CompositionLimits.Builder limits() {
        return CompositionLimits.version2().maximumLayoutAttempts(10000).maximumRelayouts(4)
                .maximumPages(16).maximumAreas(32).maximumFlowItems(32).maximumInlines(64).maximumLines(512)
                .maximumGeneratedContentBytes(1 << 20).fontLimits(FontLimits.builder().maximumFontSources(2)
                        .maximumSourceBytes(2000).maximumCodePoints(10000).maximumFallbackChecks(20000)
                        .maximumGeneratedContentBytes(1 << 20).build())
                .graphicLimits(CanvasResourceLimits.builder().maximumEncodedImageBytes(0).maximumDecodedImagePixels(0)
                        .maximumDecodedImageBytes(0).maximumIccProfileBytes(0).maximumMaskBytes(0)
                        .maximumGeneratedContentBytes(4096).maximumResourceDeclarations(16).maximumTransparencyGroupDepth(4).build());
    }
    private static byte[] font(String name) {
        try (InputStream input = ParagraphPaginationWorkflowTest.class.getResourceAsStream(
                "/net/zerocloud/pdf/fixtures/" + name + ".ttf.base64")) {
            ByteArrayOutputStream encoded = new ByteArrayOutputStream(); byte[] buffer = new byte[1024]; int count;
            while ((count = input.read(buffer)) != -1) { encoded.write(buffer, 0, count); }
            byte[] bytes = Base64.getMimeDecoder().decode(encoded.toByteArray());
            String expected = name.equals("FolioPrimary") ? "e2fbb634c3c0fe78efb449bde4426d10a93aa813596ff5cf3360f02ec97673fb"
                    : "ced760bc126036779fa84ad3da4638733032512457bf599c44d8c455360f75e1";
            StringBuilder hash = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
                hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            }
            assertEquals(expected, hash.toString()); return bytes;
        } catch (Exception failure) { throw new AssertionError(failure); }
    }
    private static void write(Path path, byte[] data) {
        try { Files.write(path, data); } catch (java.io.IOException failure) { throw new AssertionError(failure); }
    }
    private static byte[] bytes(Path path) {
        try { return Files.readAllBytes(path); } catch (java.io.IOException failure) { throw new AssertionError(failure); }
    }
    private static final class TrackingStream extends ByteArrayInputStream {
        private boolean closed;
        TrackingStream(byte[] bytes) { super(bytes); }
        @Override public void close() { closed = true; }
    }
}
