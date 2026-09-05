package net.zerocloud.pdf.acceptance;

import java.nio.file.Path;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.composition.CanvasRectangle;
import net.zerocloud.pdf.composition.CanvasResourceLimits;
import net.zerocloud.pdf.composition.CompositionLimits;
import net.zerocloud.pdf.composition.FontLimits;
import net.zerocloud.pdf.composition.FontSelection;
import net.zerocloud.pdf.composition.FontSource;
import net.zerocloud.pdf.composition.LayoutPage;
import net.zerocloud.pdf.composition.PageMargins;
import net.zerocloud.pdf.composition.Paragraph;
import net.zerocloud.pdf.composition.ParagraphFlow;
import net.zerocloud.pdf.composition.PositionedUnicodeText;
import net.zerocloud.pdf.composition.ReferenceFontSet;
import net.zerocloud.pdf.composition.TabStop;
import net.zerocloud.pdf.TextRenderingMode;
import net.zerocloud.pdf.composition.command.ComposeParagraphs;
import net.zerocloud.pdf.composition.command.DrawPositionedUnicodeText;
import net.zerocloud.pdf.composition.command.RelayoutParagraphs;

/** Public-workflow authoring; the reference path positions the independent oracle without layout. */
final class T25ParagraphProducts {
    static final String PRIMARY_HASH = "e2fbb634c3c0fe78efb449bde4426d10a93aa813596ff5cf3360f02ec97673fb";
    static final String FALLBACK_HASH = "ced760bc126036779fa84ad3da4638733032512457bf599c44d8c455360f75e1";
    private T25ParagraphProducts() { }

    static WorkflowOutcome<Void> create(T25ParagraphExpectations.Profile profile, Path target) throws Exception {
        return create(profile, target, new StringBuilder());
    }

    static WorkflowOutcome<Void> create(T25ParagraphExpectations.Profile profile, Path target, StringBuilder controls) throws Exception {
        final ParagraphFlow declaration = declaration(profile.rule);
        final DocumentSession[] retained = new DocumentSession[1];
        WorkflowOutcome<Void> outcome = workflow().execute(WorkflowRequest.create(target, SaveMode.REWRITE), session -> {
            retained[0] = session;
            session.execute(ComposeParagraphs.version2(declaration, limits(), profile.rule.equals("immediate-flush")
                    ? ComposeParagraphs.FlushMode.IMMEDIATE : ComposeParagraphs.FlushMode.BUFFERED));
            if (profile.rule.equals("relayout")) {
                session.execute(RelayoutParagraphs.version1(page(144, 40, false), page(144, 80, false)));
                controls.append("Buffered relayout completed; replacement geometry is independently checked below.\n");
            }
            if (profile.rule.equals("immediate-flush")) { controls.append("Immediate flush: ").append(requireUnsafe(session)).append('\n'); }
            return null;
        });
        if (profile.rule.equals("publication")) {
            try {
                retained[0].execute(RelayoutParagraphs.version1(page(144, 40, false)));
                throw new IllegalStateException("Published Session unexpectedly admitted relayout");
            } catch (IllegalStateException expected) {
                if (!"Document Session is no longer active.".equals(expected.getMessage())) { throw expected; }
                controls.append("Published Session: IllegalStateException (Document Session is no longer active).\n");
            }
            workflow().execute(WorkflowRequest.open(target, SaveMode.REWRITE), session -> { controls.append("Reopened publication: ").append(requireUnsafe(session)).append('\n'); return null; });
        }
        return outcome;
    }

    static void createReference(T25ParagraphExpectations.Profile profile, Path target) throws Exception {
        workflow().execute(WorkflowRequest.create(target, SaveMode.REWRITE), session -> {
            session.execute(AddBlankPage.INSTANCE); session.execute(AddBlankPage.INSTANCE);
            for (T25ParagraphExpectations.Run run : profile.runs) {
                session.execute(DrawPositionedUnicodeText.version1(run.page,
                        PositionedUnicodeText.version1(run.text, FontSelection.referenceFontSet(), 40,
                                TextRenderingMode.FILL, CanvasMatrix.of(1, 0, 0, 1, run.x, run.y)), fontLimits()));
            }
            return null;
        });
    }

    private static String requireUnsafe(DocumentSession session) throws DocumentFailure {
        try {
            session.execute(RelayoutParagraphs.version1(page(144, 40, false)));
            throw new IllegalStateException("Sealed flow unexpectedly admitted relayout");
        } catch (DocumentFailure expected) {
            if (expected.getCode() != DocumentFailureCode.COMPOSITION_RELAYOUT_UNSAFE
                    || !T25ParagraphEvidenceCommand.CAPABILITY.equals(expected.getCapabilityId())) { throw expected; }
            return expected.getCode().name();
        }
    }

    private static ParagraphFlow declaration(String rule) {
        ParagraphFlow.Builder flow = ParagraphFlow.version2(FontSelection.referenceFontSet());
        if (rule.equals("indentation")) {
            return flow.page(page(96, 80, true)).page(page(96, 120, false))
                    .paragraph(Paragraph.version2(40).text("AAAAAAAAAAAAA", 40)
                            .maximumWidth(96).indentation(24, 24, 24).build()).build();
        }
        if (rule.equals("tabs")) {
            flow.page(page(240, 240, false)).page(page(240, 240, false));
            for (int repeat = 0; repeat < 2; repeat++) {
                TabStop[] stops = {TabStop.version1(96, TabStop.Alignment.LEFT), TabStop.version1(96, TabStop.Alignment.CENTER),
                    TabStop.version1(96, TabStop.Alignment.RIGHT), TabStop.anchored(96, 'B'), TabStop.anchored(96, '\u03a9'), null};
                for (TabStop stop : stops) {
                    Paragraph.Builder paragraph = Paragraph.version2(40).text("A\tAB", 40).tabInterval(144);
                    if (stop != null) { paragraph.tabStop(stop); }
                    flow.paragraph(paragraph.build());
                }
            }
            return flow.build();
        }
        if (rule.equals("keep-next") || rule.equals("keep-together")) {
            flow.page(page(144, 80, true)).page(page(144, 120, false)).paragraph(paragraph("A\nA\nA"));
            if (rule.equals("keep-next")) {
                flow.paragraph(Paragraph.version2(40).text("B", 40).keepWithNext(true).build()).paragraph(paragraph("\u03a9\n\u03a9"));
            } else { flow.paragraph(Paragraph.version2(40).text("B\nB", 40).keepTogether(true).build()); }
            return flow.build();
        }
        if (rule.equals("widow")) {
            return flow.page(page(144, 120, false)).page(page(144, 120, false))
                    .paragraph(Paragraph.version2(40).text("A\nA\nA\nB", 40).widows(2).build()).build();
        }
        if (rule.equals("orphan")) {
            return flow.page(page(144, 120, false)).page(page(144, 120, false)).paragraph(paragraph("A\nA"))
                    .paragraph(Paragraph.version2(40).text("B\nB\n\u03a9", 40).orphans(2).build()).build();
        }
        if (rule.equals("overflow-wrap")) {
            return flow.page(page(48, 120, false)).page(page(48, 120, false)).paragraph(paragraph("AAAAAAAAAAAA")).build();
        }
        if (rule.equals("overflow-reject")) {
            return flow.page(page(72, 80, true)).page(page(120, 80, false))
                    .paragraph(Paragraph.version2(40).text("AAAA", 40).overflow(Paragraph.Overflow.REJECT).build()).build();
        }
        if (rule.equals("overflow-visible")) {
            return flow.page(page(72, 80, true)).page(page(120, 80, false)).paragraph(Paragraph.version2(40)
                    .text("AAAA\nAAAA\nAAAA\nAAAA\nAAAA", 40).overflow(Paragraph.Overflow.VISIBLE).build()).build();
        }
        if (rule.equals("relayout") || rule.equals("immediate-flush") || rule.equals("publication")) {
            return flow.page(page(72, 80, true)).page(page(72, 80, false)).paragraph(paragraph("AAAAAAAAAAAAA")).build();
        }
        throw new IllegalArgumentException("Unknown T25 Acceptance Profile");
    }

    private static LayoutPage page(double width, double height, boolean columns) {
        PageMargins margins = PageMargins.of(72, 72, 72, 72);
        CanvasRectangle first = CanvasRectangle.of(0, 648 - height, width, 648);
        return columns ? LayoutPage.version1(612, 792, margins, first, CanvasRectangle.of(160, 648 - height, 160 + width, 648))
                : LayoutPage.version1(612, 792, margins, first);
    }
    private static Paragraph paragraph(String text) { return Paragraph.version2(40).text(text, 40).build(); }
    private static DocumentWorkflow workflow() throws Exception {
        byte[] primary = T19FontEvidenceRecorder.font("FolioPrimary.ttf.base64");
        byte[] fallback = T19FontEvidenceRecorder.font("FolioFallback.ttf.base64");
        if (!PRIMARY_HASH.equals(EvidenceFiles.sha256(primary)) || !FALLBACK_HASH.equals(EvidenceFiles.sha256(fallback))) {
            throw new IllegalStateException("The explicit T25 font hashes changed");
        }
        return new DocumentWorkflow(WorkflowEnvironment.builder().referenceFontSet(ReferenceFontSet.version1(
                FontSource.bytes(primary), FontSource.bytes(fallback))).build());
    }
    private static FontLimits fontLimits() {
        return FontLimits.builder().maximumFontSources(2).maximumSourceBytes(2000).maximumCodePoints(128)
                .maximumFallbackChecks(256).maximumGeneratedContentBytes(1 << 16).build();
    }
    private static CompositionLimits limits() {
        return CompositionLimits.version2().maximumLayoutAttempts(1000).maximumRelayouts(2).maximumPages(2)
                .maximumAreas(3).maximumFlowItems(12).maximumInlines(12).maximumLines(16)
                .maximumGeneratedContentBytes(1 << 16).fontLimits(fontLimits())
                .graphicLimits(CanvasResourceLimits.builder().maximumEncodedImageBytes(0).maximumDecodedImagePixels(0)
                        .maximumDecodedImageBytes(0).maximumIccProfileBytes(0).maximumMaskBytes(0)
                        .maximumGeneratedContentBytes(0).maximumResourceDeclarations(0).maximumTransparencyGroupDepth(0).build()).build();
    }
}
