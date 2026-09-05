package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.List;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.composition.CanvasProgram;
import net.zerocloud.pdf.composition.CanvasRectangle;
import net.zerocloud.pdf.composition.CompositionLimits;
import net.zerocloud.pdf.composition.LayoutPage;
import net.zerocloud.pdf.composition.PageMargins;
import net.zerocloud.pdf.composition.Paragraph;
import net.zerocloud.pdf.composition.ParagraphFlow;
import net.zerocloud.pdf.composition.command.ComposeParagraphs;
import net.zerocloud.pdf.composition.command.DrawCanvas;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/** Finite paragraph composition; T19 owns fonts and T18 owns inline painting. */
final class PdfBoxParagraphOperations {
    static final String CAPABILITY_ID = "composition.layout.paragraph-areas";
    // Absorb double rounding at an exact fit, well below the PDF geometry tolerance.
    private static final double FIT_TOLERANCE = 0.000000001;
    private final PdfBoxPositionedTextOperations fonts;
    private final PdfBoxCanvasOperations canvas;
    private final PdfBoxPageOperations pages;
    private final WorkflowResourceContext resources;

    PdfBoxParagraphOperations(PdfBoxPositionedTextOperations fonts,
            PdfBoxCanvasOperations canvas, PdfBoxPageOperations pages,
            WorkflowResourceContext resources) {
        this.fonts = fonts;
        this.canvas = canvas;
        this.pages = pages;
        this.resources = resources;
    }

    void execute(ComposeParagraphs command) throws DocumentFailure {
        long modeledBytes = validateDeclarations(command, resources);
        pages.requireAppendPreservable();
        try (WorkflowResourceContext.MemoryReservation memory =
                resources.reserveOwnedMemory(modeledBytes)) {
            StringBuilder text = new StringBuilder();
            for (ParagraphFlow.Item item : command.getFlow().getItems()) {
                if (item.getKind() == ParagraphFlow.Item.Kind.PARAGRAPH) {
                    for (Paragraph.Inline inline : item.getParagraph().getInlines()) {
                        if (inline.getKind() == Paragraph.Inline.Kind.TEXT) {
                            String value = inline.getText();
                            for (int index = 0; index < value.length(); index++) {
                                resources.checkpoint();
                                if (value.charAt(index) != '\n') { text.append(value.charAt(index)); }
                            }
                        }
                    }
                }
            }
            try (PdfBoxPositionedTextOperations.PreparedText prepared = fonts.prepareLayoutText(
                    text.toString(), command.getFlow().getFonts(), command.getLimits().getFontLimits())) {
                Layout layout = new Layout(command, prepared);
                layout.compose();
                List<PDPage> detached = createPages(command.getFlow(), layout.lastPage + 1);
                paint(layout, prepared, detached, command.getLimits());
                fonts.finalizeFonts();
                pages.appendComposedPages(detached);
            }
        } catch (DocumentFailure failure) {
            if (failure.getCapabilityId().equals(PdfBoxPositionedTextOperations.CAPABILITY_ID)
                    || failure.getCapabilityId().equals(PdfBoxCanvasOperations.CAPABILITY_ID)
                    || failure.getCapabilityId().equals(PdfBoxCanvasResourceOperations.CAPABILITY_ID)) {
                throw failure(failure.getCode(), failure.getDiagnostic());
            }
            throw failure;
        } catch (RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw failure(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The paragraph flow could not be applied safely.");
        }
    }

    /** Pure declaration scan also used before Worker transport; it never opens sources. */
    static long validateDeclarations(ComposeParagraphs command, WorkflowResourceContext resources)
            throws DocumentFailure {
        ParagraphFlow flow = command.getFlow();
        CompositionLimits limits = command.getLimits();
        if (flow.getPages().isEmpty() || flow.getItems().isEmpty()) { throw invalid(); }
        if (flow.getPages().size() > limits.getMaximumPages()
                || flow.getItems().size() > limits.getMaximumFlowItems()) { throw limitFailure(); }
        long areaCount = 0;
        for (LayoutPage page : flow.getPages()) {
            resources.checkpoint();
            if (!positive(page.getWidth()) || !positive(page.getHeight())
                    || page.getWidth() > 14400 || page.getHeight() > 14400) { throw invalid(); }
            PageMargins margin = page.getMargins();
            if (!nonnegative(margin.getLeft()) || !nonnegative(margin.getRight())
                    || !nonnegative(margin.getTop()) || !nonnegative(margin.getBottom())) { throw invalid(); }
            double width = page.getWidth() - margin.getLeft() - margin.getRight();
            double height = page.getHeight() - margin.getTop() - margin.getBottom();
            if (!positive(width) || !positive(height)) { throw invalid(); }
            areaCount += Math.max(1, page.getAreas().size());
            if (areaCount > limits.getMaximumAreas()) { throw limitFailure(); }
            for (CanvasRectangle area : page.getAreas()) {
                resources.checkpoint();
                if (!rectangle(area) || area.getLowerLeftX() < 0 || area.getLowerLeftY() < 0
                        || exceeds(area.getUpperRightX(), width)
                        || exceeds(area.getUpperRightY(), height)) { throw invalid(); }
            }
        }
        long inlineCount = 0;
        long scalarCount = 0;
        long characterCount = 0;
        for (ParagraphFlow.Item item : flow.getItems()) {
            resources.checkpoint();
            if (item.getKind() == ParagraphFlow.Item.Kind.AREA_BREAK) { continue; }
            Paragraph paragraph = item.getParagraph();
            if (!positive(paragraph.getLeading()) || !nonnegative(paragraph.getMaximumWidth())
                    || paragraph.getInlines().isEmpty()) { throw invalid(); }
            inlineCount += paragraph.getInlines().size();
            if (inlineCount > limits.getMaximumInlines()) { throw limitFailure(); }
            for (Paragraph.Inline inline : paragraph.getInlines()) {
                resources.checkpoint();
                if (inline.getKind() == Paragraph.Inline.Kind.GRAPHIC) {
                    if (!positive(inline.getWidth()) || !positive(inline.getHeight())
                            || !rectangle(inline.getGraphic().getBox())) { throw invalid(); }
                } else {
                    if (!positive(inline.getFontSize()) || inline.getText().isEmpty()) { throw invalid(); }
                    String value = inline.getText();
                    characterCount += value.length();
                    for (int index = 0; index < value.length();) {
                        resources.checkpoint();
                        char first = value.charAt(index);
                        int cp = value.codePointAt(index);
                        if (Character.isLowSurrogate(first)
                                || (Character.isHighSurrogate(first) && cp <= Character.MAX_VALUE)
                                || (Character.isISOControl(cp) && cp != '\n')) { throw invalid(); }
                        index += Character.charCount(cp);
                        if (++scalarCount > limits.getFontLimits().getMaximumCodePoints()) {
                            throw failure(DocumentFailureCode.FONT_LIMIT_EXCEEDED,
                                    "The font operation limit was exceeded.");
                        }
                    }
                }
            }
        }
        // Upper bound for the modeled declarations, scalar atoms, line plans and
        // temporary UTF-16 copies, held for exactly this command's lifetime.
        return 1024L * flow.getPages().size() + 128L * (areaCount + flow.getItems().size())
                + 512L * (inlineCount + scalarCount) + 4L * characterCount;
    }

    private List<PDPage> createPages(ParagraphFlow flow, int count) throws DocumentFailure {
        List<PDPage> result = new ArrayList<PDPage>(count);
        for (int index = 0; index < count; index++) {
            resources.checkpoint();
            LayoutPage declaration = flow.getPages().get(index);
            PDPage page = new PDPage(new PDRectangle(
                    (float) declaration.getWidth(), (float) declaration.getHeight()));
            page.setResources(new PDResources());
            resources.observePage(page.getCOSObject());
            result.add(page);
        }
        return result;
    }

    private void paint(Layout layout, PdfBoxPositionedTextOperations.PreparedText prepared,
            List<PDPage> detached, CompositionLimits limits) throws DocumentFailure {
        long contentBytes = 0;
        long textBytes = 0;
        for (Line line : layout.lines) {
            resources.checkpoint();
            PDPage page = detached.get(line.area.page);
            double spare = Math.max(0, line.availableWidth - line.width);
            double x = line.area.box.getLowerLeftX();
            Paragraph.Alignment alignment = line.paragraph.getAlignment();
            if (alignment == Paragraph.Alignment.CENTER) { x += spare / 2; }
            if (alignment == Paragraph.Alignment.RIGHT) { x += spare; }
            int spaces = 0;
            if (alignment == Paragraph.Alignment.JUSTIFIED && line.automatic) {
                for (int index = line.first; index < line.end - 1; index++) {
                    if (line.atoms.get(index).codePoint == ' ') { spaces++; }
                }
            }
            double gap = spaces == 0 ? 0 : spare / spaces;
            for (int index = line.first; index < line.end;) {
                resources.checkpoint();
                Atom atom = line.atoms.get(index);
                long remaining = limits.getMaximumGeneratedContentBytes() - contentBytes;
                long written;
                if (atom.inline.getKind() == Paragraph.Inline.Kind.GRAPHIC) {
                    CanvasRectangle box = atom.inline.getGraphic().getBox();
                    double sx = atom.width / (box.getUpperRightX() - box.getLowerLeftX());
                    double sy = atom.ascent / (box.getUpperRightY() - box.getLowerLeftY());
                    CanvasProgram program = CanvasProgram.version2().drawTransparencyGroup(
                            atom.inline.getGraphic(), CanvasMatrix.of(sx, 0, 0, sy,
                                    x - box.getLowerLeftX() * sx,
                                    line.baseline - box.getLowerLeftY() * sy)).build();
                    written = canvas.drawLayoutGraphic(DrawCanvas.version2(1, program,
                            limits.getGraphicLimits()), page, remaining);
                    x += atom.width;
                    index++;
                } else {
                    int end = index + 1;
                    double width = atom.width;
                    while (end < line.end && !(gap > 0 && line.atoms.get(end - 1).codePoint == ' ')) {
                        Atom next = line.atoms.get(end);
                        if (next.inline.getKind() != Paragraph.Inline.Kind.TEXT
                                || next.inline.getFontSize() != atom.inline.getFontSize()) { break; }
                        width += next.width;
                        end++;
                    }
                    long textRemaining = limits.getFontLimits().getMaximumGeneratedContentBytes() - textBytes;
                    written = prepared.draw(page, atom.glyph, line.atoms.get(end - 1).glyph + 1,
                            atom.inline.getFontSize(), x, line.baseline, textRemaining);
                    if (written > remaining) { throw limitFailure(); }
                    textBytes += written;
                    x += width;
                    if (line.atoms.get(end - 1).codePoint == ' ' && end < line.end) { x += gap; }
                    index = end;
                }
                contentBytes += written;
            }
        }
    }

    private final class Layout {
        private final ComposeParagraphs command;
        private final PdfBoxPositionedTextOperations.PreparedText prepared;
        private final List<Area> areas = new ArrayList<Area>();
        private final List<Line> lines = new ArrayList<Line>();
        private int areaIndex;
        private int glyphIndex;
        private int lastPage;
        private double usedHeight;
        private double heightCorrection;

        Layout(ComposeParagraphs command, PdfBoxPositionedTextOperations.PreparedText prepared)
                throws DocumentFailure {
            this.command = command;
            this.prepared = prepared;
            int pageIndex = 0;
            for (LayoutPage page : command.getFlow().getPages()) {
                resources.checkpoint();
                PageMargins margin = page.getMargins();
                if (page.getAreas().isEmpty()) {
                    areas.add(new Area(pageIndex, CanvasRectangle.of(margin.getLeft(), margin.getBottom(),
                            page.getWidth() - margin.getRight(), page.getHeight() - margin.getTop())));
                } else {
                    for (CanvasRectangle box : page.getAreas()) {
                        resources.checkpoint();
                        areas.add(new Area(pageIndex, CanvasRectangle.of(
                                margin.getLeft() + box.getLowerLeftX(), margin.getBottom() + box.getLowerLeftY(),
                                margin.getLeft() + box.getUpperRightX(), margin.getBottom() + box.getUpperRightY())));
                    }
                }
                pageIndex++;
            }
        }

        void compose() throws DocumentFailure {
            for (ParagraphFlow.Item item : command.getFlow().getItems()) {
                resources.checkpoint();
                if (item.getKind() == ParagraphFlow.Item.Kind.AREA_BREAK) {
                    nextArea();
                    continue;
                }
                Paragraph paragraph = item.getParagraph();
                List<Atom> atoms = atoms(paragraph);
                int first = 0;
                while (first < atoms.size()) {
                    resources.checkpoint();
                    Area area = areas.get(areaIndex);
                    double width = area.box.getUpperRightX() - area.box.getLowerLeftX();
                    if (paragraph.getMaximumWidth() > 0) { width = Math.min(width, paragraph.getMaximumWidth()); }
                    Line line = line(atoms, first, paragraph, area, width);
                    double areaHeight = area.box.getUpperRightY() - area.box.getLowerLeftY();
                    if (line == null || exceeds(usedHeight + line.height, areaHeight)) {
                        nextArea();
                        continue;
                    }
                    if (lines.size() >= command.getLimits().getMaximumLines()) { throw limitFailure(); }
                    line.baseline = area.box.getUpperRightY() - usedHeight - line.ascent;
                    lines.add(line);
                    first = line.next;
                    // Compensated accumulation avoids drift over many fractional lines.
                    double increment = line.height - heightCorrection;
                    double nextHeight = usedHeight + increment;
                    heightCorrection = (nextHeight - usedHeight) - increment;
                    usedHeight = nextHeight;
                }
            }
        }

        private void nextArea() throws DocumentFailure {
            if (areaIndex + 1 >= areas.size()) {
                throw failure(DocumentFailureCode.COMPOSITION_AREA_EXHAUSTED,
                        "The remaining layout areas cannot contain the paragraph flow.");
            }
            areaIndex++;
            Area area = areas.get(areaIndex);
            usedHeight = 0;
            heightCorrection = 0;
            lastPage = area.page;
        }

        private List<Atom> atoms(Paragraph paragraph) throws DocumentFailure {
            List<Atom> result = new ArrayList<Atom>();
            for (Paragraph.Inline inline : paragraph.getInlines()) {
                if (inline.getKind() == Paragraph.Inline.Kind.GRAPHIC) {
                    result.add(new Atom(inline, -1, -1, inline.getWidth(), inline.getHeight(), 0));
                } else {
                    for (int offset = 0; offset < inline.getText().length();) {
                        resources.checkpoint();
                        int cp = inline.getText().codePointAt(offset);
                        offset += Character.charCount(cp);
                        if (cp == '\n') {
                            result.add(new Atom(inline, -1, cp, 0, 0, 0));
                        } else {
                            double size = inline.getFontSize();
                            result.add(new Atom(inline, glyphIndex, cp, prepared.width(glyphIndex, size),
                                    prepared.ascent(glyphIndex, size), prepared.descent(glyphIndex, size)));
                            glyphIndex++;
                        }
                    }
                }
            }
            return result;
        }
    }

    private Line line(List<Atom> atoms, int first, Paragraph paragraph, Area area, double available)
            throws DocumentFailure {
        int end = first;
        int lastBreak = -1;
        double width = 0;
        double correction = 0;
        while (end < atoms.size()) {
            resources.checkpoint();
            Atom atom = atoms.get(end);
            if (atom.codePoint == '\n') { break; }
            double increment = atom.width - correction;
            double nextWidth = width + increment;
            if (exceeds(nextWidth, available)) {
                if (end == first) { return null; }
                if (lastBreak > first) { end = lastBreak; }
                break;
            }
            correction = (nextWidth - width) - increment;
            width = nextWidth;
            end++;
            if (atom.codePoint == ' ' || atom.inline.getKind() == Paragraph.Inline.Kind.GRAPHIC) {
                lastBreak = end;
            }
        }
        int next = end;
        boolean automatic = end < atoms.size() && atoms.get(end).codePoint != '\n';
        if (end < atoms.size() && atoms.get(end).codePoint == '\n') { next++; }
        double ascent = 0;
        double descent = 0;
        width = 0;
        correction = 0;
        for (int index = first; index < end; index++) {
            resources.checkpoint();
            Atom atom = atoms.get(index);
            double increment = atom.width - correction;
            double nextWidth = width + increment;
            correction = (nextWidth - width) - increment;
            width = nextWidth;
            ascent = Math.max(ascent, atom.ascent);
            descent = Math.max(descent, atom.descent);
        }
        return new Line(atoms, paragraph, area, first, end, next, width, available,
                ascent, Math.max(paragraph.getLeading(), ascent + descent), automatic);
    }

    private static boolean exceeds(double value, double available) { return value > available + FIT_TOLERANCE; }
    private static boolean positive(double value) { return value > 0 && PdfBoxPageContentSupport.isValidNumber(value); }
    private static boolean nonnegative(double value) { return value >= 0 && PdfBoxPageContentSupport.isValidNumber(value); }
    private static boolean rectangle(CanvasRectangle box) {
        return PdfBoxPageContentSupport.isValidNumber(box.getLowerLeftX())
                && PdfBoxPageContentSupport.isValidNumber(box.getLowerLeftY())
                && PdfBoxPageContentSupport.isValidNumber(box.getUpperRightX())
                && PdfBoxPageContentSupport.isValidNumber(box.getUpperRightY())
                && positive(box.getUpperRightX() - box.getLowerLeftX())
                && positive(box.getUpperRightY() - box.getLowerLeftY());
    }
    static DocumentFailure invalid() {
        return failure(DocumentFailureCode.COMPOSITION_INVALID, "The paragraph flow declaration is invalid.");
    }
    static DocumentFailure limitFailure() {
        return failure(DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED, "The paragraph composition limit was exceeded.");
    }
    static DocumentFailure signatureFailure() {
        return failure(DocumentFailureCode.SIGNATURE_POLICY_REJECTED,
                "The Existing Signature policy does not permit paragraph composition.");
    }
    static void requirePermission(PasswordSecurityInfo security) throws DocumentFailure {
        if (security.isPasswordProtected() && (!security.getEffectivePermissions().canModify()
                || !security.getEffectivePermissions().canAssembleDocument())) {
            throw failure(DocumentFailureCode.DOCUMENT_PERMISSION_DENIED,
                    "The Source credential does not authorize paragraph composition.");
        }
    }
    private static DocumentFailure failure(DocumentFailureCode code, String diagnostic) {
        return new DocumentFailure(code, CAPABILITY_ID, diagnostic);
    }

    private static final class Area {
        private final int page;
        private final CanvasRectangle box;
        Area(int page, CanvasRectangle box) { this.page = page; this.box = box; }
    }
    private static final class Atom {
        private final Paragraph.Inline inline;
        private final int glyph;
        private final int codePoint;
        private final double width;
        private final double ascent;
        private final double descent;
        Atom(Paragraph.Inline inline, int glyph, int codePoint, double width, double ascent, double descent) {
            this.inline = inline; this.glyph = glyph; this.codePoint = codePoint;
            this.width = width; this.ascent = ascent; this.descent = descent;
        }
    }
    private static final class Line {
        private final List<Atom> atoms;
        private final Paragraph paragraph;
        private final Area area;
        private final int first;
        private final int end;
        private final int next;
        private final double width;
        private final double availableWidth;
        private final double ascent;
        private final double height;
        private final boolean automatic;
        private double baseline;
        Line(List<Atom> atoms, Paragraph paragraph, Area area, int first, int end, int next,
                double width, double availableWidth, double ascent, double height, boolean automatic) {
            this.atoms = atoms; this.paragraph = paragraph; this.area = area;
            this.first = first; this.end = end; this.next = next; this.width = width;
            this.availableWidth = availableWidth; this.ascent = ascent; this.height = height;
            this.automatic = automatic;
        }
    }
}
