package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.composition.CanvasImage;
import net.zerocloud.pdf.composition.CanvasMask;
import net.zerocloud.pdf.composition.CanvasProgram;
import net.zerocloud.pdf.composition.CanvasRectangle;
import net.zerocloud.pdf.composition.CanvasTransparencyGroup;
import net.zerocloud.pdf.composition.CompositionLimits;
import net.zerocloud.pdf.composition.LayoutPage;
import net.zerocloud.pdf.composition.PageMargins;
import net.zerocloud.pdf.composition.Paragraph;
import net.zerocloud.pdf.composition.ParagraphFlow;
import net.zerocloud.pdf.composition.TabStop;
import net.zerocloud.pdf.composition.TableRow;
import net.zerocloud.pdf.composition.TableCell;
import net.zerocloud.pdf.composition.CanvasColor;
import net.zerocloud.pdf.composition.CanvasColorSpace;
import net.zerocloud.pdf.composition.CanvasWindingRule;
import net.zerocloud.pdf.composition.command.RelayoutParagraphs;
import net.zerocloud.pdf.composition.command.ComposeParagraphs;
import net.zerocloud.pdf.composition.command.DrawCanvas;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/** Finite paragraph composition; T19 owns fonts and T18 owns inline painting. */
final class PdfBoxParagraphOperations {
    static final String CAPABILITY_ID = "composition.layout.paragraph-areas";
    static final String PAGINATION_CAPABILITY_ID = "composition.layout.paragraph-pagination";
    private ComposeParagraphs buffered;
    private PdfBoxPositionedTextOperations.PreparedText bufferedText;
    private WorkflowResourceContext.MemoryReservation bufferedMemory;
    private List<PDPage> bufferedPages;
    private int relayouts;
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
        WorkflowResourceContext.MemoryReservation memory = null;
        PdfBoxPositionedTextOperations.PreparedText prepared = null;
        try {
            long modeledBytes = validateDeclarations(command, resources);
            pages.requireAppendPreservable();
            memory = resources.reserveOwnedMemory(modeledBytes);
            StringBuilder text = new StringBuilder();
            for (ParagraphFlow.Item item : command.getFlow().getItems()) {
                if (item.getKind() == ParagraphFlow.Item.Kind.PARAGRAPH) {
                    appendText(text, item.getParagraph());
                } else if (item.getKind() == ParagraphFlow.Item.Kind.TABLE) {
                    for (List<TableRow> group : PdfBoxTableLayout.rowGroups(item.getTable())) {
                      for (TableRow row : group) {
                        for (TableCell cell : row.getCells()) {
                            for (Paragraph paragraph : cell.getParagraphs()) { appendText(text, paragraph); }
                        }
                      }
                    }
                }
            }
            prepared = fonts.prepareLayoutText(text.toString(), command.getFlow().getFonts(),
                    command.getLimits().getFontLimits());
            List<PDPage> detached = compose(command, prepared);
            pages.appendComposedPages(detached);
            flush();
            if (command.getFlushMode() == ComposeParagraphs.FlushMode.BUFFERED) {
                buffered = command;
                bufferedText = prepared;
                bufferedMemory = memory;
                bufferedPages = detached;
                prepared = null;
                memory = null;
            }
        } catch (DocumentFailure failure) {
            throw compositionFailure(failure, capability(command));
        } catch (RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw new DocumentFailure(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    capability(command), "The paragraph flow could not be applied safely.");
        } finally {
            if (prepared != null) { prepared.close(); }
            if (memory != null) { memory.close(); }
        }
    }

    void appendText(StringBuilder text, Paragraph paragraph) throws DocumentFailure {
        for (Paragraph.Inline inline : paragraph.getInlines()) {
            if (inline.getKind() == Paragraph.Inline.Kind.TEXT) {
                String value = inline.getText();
                for (int index = 0; index < value.length(); index++) {
                    resources.checkpoint();
                    char cp = value.charAt(index);
                    if (cp != '\n' && cp != '\t') { text.append(cp); }
                }
            }
        }
    }

    void relayout(RelayoutParagraphs command) throws DocumentFailure {
        if (buffered == null) {
            throw new DocumentFailure(DocumentFailureCode.COMPOSITION_RELAYOUT_UNSAFE,
                    PAGINATION_CAPABILITY_ID, "The paragraph flow is not available for safe relayout.");
        }
        String capability = capability(buffered);
        if (relayouts >= buffered.getLimits().getMaximumRelayouts()) {
            throw compositionFailure(limitFailure(), capability);
        }
        relayouts++;
        WorkflowResourceContext.MemoryReservation memory = null;
        try {
            // Admit caller declarations and reserve all replacement storage before copying.
            memory = resources.reserveOwnedMemory(scanDeclarations(buffered, command.getPages(), resources));
            boolean tableFlow = buffered.getVersion() == ComposeParagraphs.VERSION_4;
            ParagraphFlow.Builder replacement = tableFlow ? ParagraphFlow.version4(buffered.getFlow().getFonts())
                    : ParagraphFlow.version2(buffered.getFlow().getFonts());
            for (LayoutPage page : command.getPages()) {
                resources.checkpoint();
                replacement.page(page);
            }
            for (ParagraphFlow.Item item : buffered.getFlow().getItems()) {
                resources.checkpoint();
                if (item.getKind() == ParagraphFlow.Item.Kind.AREA_BREAK) { replacement.areaBreak(); }
                else if (item.getKind() == ParagraphFlow.Item.Kind.TABLE) { replacement.table(item.getTable()); }
                else { replacement.paragraph(item.getParagraph()); }
            }
            ComposeParagraphs next = tableFlow ? ComposeParagraphs.version4(replacement.build(), buffered.getLimits())
                    : ComposeParagraphs.version2(replacement.build(), buffered.getLimits());
            pages.requireAppendPreservable();
            List<PDPage> detached = compose(next, bufferedText);
            pages.replaceComposedPages(bufferedPages, detached);
            bufferedMemory.close();
            bufferedMemory = memory;
            memory = null;
            buffered = next;
            bufferedPages = detached;
        } catch (DocumentFailure failure) {
            throw compositionFailure(failure, capability);
        } catch (RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw new DocumentFailure(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    capability, "The paragraph flow could not be relaid out safely.");
        } finally {
            if (memory != null) { memory.close(); }
        }
    }

    String bufferedCapability() { return buffered == null ? PAGINATION_CAPABILITY_ID : capability(buffered); }

    void flush() {
        buffered = null;
        bufferedPages = null;
        relayouts = 0;
        if (bufferedText != null) { bufferedText.close(); bufferedText = null; }
        if (bufferedMemory != null) { bufferedMemory.close(); bufferedMemory = null; }
    }

    static String capability(ComposeParagraphs command) {
        return command.getVersion() >= ComposeParagraphs.VERSION_3 ? PdfBoxTableLayout.CAPABILITY_ID
                : command.getVersion() == ComposeParagraphs.VERSION_2 ? PAGINATION_CAPABILITY_ID : CAPABILITY_ID;
    }

    private List<PDPage> compose(ComposeParagraphs command,
            PdfBoxPositionedTextOperations.PreparedText prepared) throws DocumentFailure {
        Layout layout = new Layout(command, prepared);
        layout.compose();
        List<PDPage> detached = createPages(command.getFlow(), layout.lastPage + 1);
        paint(layout.lines, layout.tablePlans, prepared, detached, command.getLimits(), 0, 0);
        fonts.finalizeFonts();
        return detached;
    }

    static DocumentFailure compositionFailure(DocumentFailure failure, String capability) {
        String source = failure.getCapabilityId();
        if (source.equals(CAPABILITY_ID) || source.equals(PAGINATION_CAPABILITY_ID) || source.equals(PdfBoxTableLayout.CAPABILITY_ID)
                || source.equals(PdfBoxPositionedTextOperations.CAPABILITY_ID)
                || source.equals(PdfBoxCanvasOperations.CAPABILITY_ID)
                || source.equals(PdfBoxCanvasResourceOperations.CAPABILITY_ID)) {
            return new DocumentFailure(failure.getCode(), capability, failure.getDiagnostic());
        }
        return failure;
    }

    /** Pure declaration scan also used before Worker transport; it never opens sources. */
    static long validateDeclarations(ComposeParagraphs command, WorkflowResourceContext resources)
            throws DocumentFailure {
        try { return scanDeclarations(command, command.getFlow().getPages(), resources); }
        catch (DocumentFailure failure) { throw compositionFailure(failure, capability(command)); }
    }

    private static long scanDeclarations(ComposeParagraphs command, List<LayoutPage> declaredPages,
            WorkflowResourceContext resources)
            throws DocumentFailure {
        return scanDeclarations(command, declaredPages, resources, false, 0);
    }

    static long validateLargeDeclarations(ParagraphFlow flow, CompositionLimits limits, WorkflowResourceContext resources)
            throws DocumentFailure {
        return validateLargeDeclarations(flow, limits, resources, 0);
    }

    static long validateLargeDeclarations(ParagraphFlow flow, CompositionLimits limits, WorkflowResourceContext resources,
            int maximumOpenRows) throws DocumentFailure {
        return scanDeclarations(ComposeParagraphs.version4(flow, limits), flow.getPages(), resources, true, maximumOpenRows);
    }

    private static long scanDeclarations(ComposeParagraphs command, List<LayoutPage> declaredPages,
            WorkflowResourceContext resources, boolean allowEmptyBody, int maximumOpenRows) throws DocumentFailure {
        ParagraphFlow flow = command.getFlow();
        CompositionLimits limits = command.getLimits();
        if (flow.getVersion() != command.getVersion() || limits.getVersion() != command.getVersion()
                || (command.getVersion() < ComposeParagraphs.VERSION_3 && limits.getTableLimits() != null)
                || (command.getVersion() == ComposeParagraphs.VERSION_3
                    && (limits.getTableLimits() == null || limits.getMaximumRelayouts() != 0))
                || (command.getVersion() == ComposeParagraphs.VERSION_4 && limits.getTableLimits() == null)
                || (command.getVersion() == ComposeParagraphs.VERSION_1
                    && (limits.getMaximumLayoutAttempts() != 0 || limits.getMaximumRelayouts() != 0))) {
            throw invalid();
        }
        if (declaredPages.isEmpty() || flow.getItems().isEmpty()) { throw invalid(); }
        if (declaredPages.size() > limits.getMaximumPages()
                || flow.getItems().size() > limits.getMaximumFlowItems()) { throw limitFailure(); }
        long areaCount = 0;
        for (LayoutPage page : declaredPages) {
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
        DeclarationCounts counts = new DeclarationCounts(limits, resources);
        PdfBoxTableLayout.Declarations tables = new PdfBoxTableLayout.Declarations();
        long graphics = 0;
        for (ParagraphFlow.Item item : flow.getItems()) {
            resources.checkpoint();
            if (item.getKind() == ParagraphFlow.Item.Kind.AREA_BREAK) { continue; }
            if (item.getKind() == ParagraphFlow.Item.Kind.TABLE) {
                if (flow.getVersion() < ParagraphFlow.VERSION_3
                        || (flow.getVersion() == ParagraphFlow.VERSION_3
                            && item.getTable().getVersion() != net.zerocloud.pdf.composition.Table.VERSION_1)) { throw invalid(); }
                tables.accept(item.getTable(), limits.getTableLimits(), resources, allowEmptyBody, maximumOpenRows);
                for (List<TableRow> group : PdfBoxTableLayout.rowGroups(item.getTable())) {
                  for (TableRow row : group) {
                    for (TableCell cell : row.getCells()) {
                        for (Paragraph paragraph : cell.getParagraphs()) {
                            counts.accept(paragraph, Paragraph.VERSION_1);
                            graphics = addDeclarationBytes(graphics, paragraphGraphicBytes(paragraph, limits, resources), resources);
                        }
                    }
                  }
                }
            } else {
                counts.accept(item.getParagraph(), Math.min(flow.getVersion(), Paragraph.VERSION_2));
                graphics = addDeclarationBytes(graphics, paragraphGraphicBytes(item.getParagraph(), limits, resources), resources);
            }
        }
        return addDeclarationBytes(graphics, 1024L * declaredPages.size() + 128L * (areaCount + flow.getItems().size())
                + counts.modeledBytes() + tables.modeledBytes(), resources);
    }

    static final class DeclarationCounts {
        private final CompositionLimits limits;
        private final WorkflowResourceContext resources;
        private long tabCount;
        private long inlineCount;
        private long scalarCount;
        private long characterCount;
        private long paragraphCount;
        DeclarationCounts(CompositionLimits limits, WorkflowResourceContext resources) {
            this.limits = limits; this.resources = resources;
        }
        DeclarationCounts(DeclarationCounts original) {
            this(original.limits, original.resources);
            tabCount = original.tabCount; inlineCount = original.inlineCount; scalarCount = original.scalarCount;
            characterCount = original.characterCount; paragraphCount = original.paragraphCount;
        }
        void accept(Paragraph paragraph, int maximumVersion) throws DocumentFailure {
            resources.checkpoint();
            paragraphCount++;
            if (!positive(paragraph.getLeading()) || !nonnegative(paragraph.getMaximumWidth())
                    || paragraph.getInlines().isEmpty()) { throw invalid(); }
            if (paragraph.getVersion() > maximumVersion) { throw invalid(); }
            validateParagraph(paragraph, resources);
            tabCount += paragraph.getTabStops().size();
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
                                || (Character.isISOControl(cp) && cp != '\n'
                                    && !(cp == '\t' && paragraph.getVersion() == Paragraph.VERSION_2))) { throw invalid(); }
                        index += Character.charCount(cp);
                        if (++scalarCount > limits.getFontLimits().getMaximumCodePoints()) {
                            throw failure(DocumentFailureCode.FONT_LIMIT_EXCEEDED,
                                    "The font operation limit was exceeded.");
                        }
                    }
                }
            }
        }
        long modeledBytes() {
            return 512L * (inlineCount + scalarCount) + 4L * characterCount + 64L * tabCount + 128L * paragraphCount;
        }
    }

    private static long paragraphGraphicBytes(Paragraph paragraph, CompositionLimits limits,
            WorkflowResourceContext resources) throws DocumentFailure {
        long bytes = 0;
        for (Paragraph.Inline inline : paragraph.getInlines()) {
            resources.checkpoint();
            if (inline.getKind() == Paragraph.Inline.Kind.GRAPHIC) {
                bytes = addDeclarationBytes(bytes, graphicBytes(inline.getGraphic(), limits, resources, 1), resources);
            }
        }
        return bytes;
    }

    /** Counts retained declarations without copying payload arrays or opening document resources.
     * Shared occurrences are conservatively charged separately, as they are in Worker transport. */
    static long graphicBytes(CanvasTransparencyGroup group, CompositionLimits limits,
            WorkflowResourceContext resources, int depth) throws DocumentFailure {
        resources.checkpoint();
        resources.requireNestingDepth(depth);
        if (depth > limits.getGraphicLimits().getMaximumTransparencyGroupDepth()) {
            throw failure(DocumentFailureCode.CANVAS_RESOURCE_LIMIT_EXCEEDED, "The Canvas resource limit was exceeded.");
        }
        long bytes = addDeclarationBytes(384, colorSpaceBytes(group.getColorSpace()), resources);
        for (CanvasProgram.Instruction instruction : group.getProgram().getInstructions()) {
            resources.checkpoint();
            // Fixed metadata covers the instruction, references, numeric operands, matrix and font handle.
            bytes = addDeclarationBytes(bytes, 512L + instruction.getGlyphCodeLength(), resources);
            if (instruction.getColor() != null) {
                CanvasColor color = instruction.getColor();
                bytes = addDeclarationBytes(bytes, 128L + 8L * color.getComponentCount()
                        + colorSpaceBytes(color.getColorSpace()), resources);
            }
            if (instruction.getImage() != null) {
                CanvasImage image = instruction.getImage();
                bytes = addDeclarationBytes(bytes, 128L + image.getByteLength(), resources);
                if (image.getColorSpace().isPresent()) {
                    bytes = addDeclarationBytes(bytes, colorSpaceBytes(image.getColorSpace().get()), resources);
                }
                CanvasMask explicit = image.getExplicitMask().orElse(null);
                CanvasMask soft = image.getSoftMask().orElse(null);
                if (explicit != null) { bytes = addDeclarationBytes(bytes, 128L + explicit.getSampleByteLength(), resources); }
                if (soft != null) { bytes = addDeclarationBytes(bytes, 128L + soft.getSampleByteLength(), resources); }
            }
            if (instruction.getTransparencyGroup() != null) {
                bytes = addDeclarationBytes(bytes,
                        graphicBytes(instruction.getTransparencyGroup(), limits, resources, depth + 1), resources);
            }
        }
        return bytes;
    }

    private static long colorSpaceBytes(CanvasColorSpace space) {
        return 128L + space.getIccProfileByteLength() + 8L * (space.getWhitePointLength()
                + (long) space.getBlackPointLength() + space.getGammaLength() + space.getMatrixLength());
    }

    static long addDeclarationBytes(long bytes, long additional, WorkflowResourceContext resources) throws DocumentFailure {
        if (additional > resources.getPolicy().getMaximumOwnedMemoryBytes() - bytes) {
            throw resources.policyFailure(DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                    "The workflow owned-memory limit was exceeded.");
        }
        return bytes + additional;
    }

    private static void validateParagraph(Paragraph paragraph, WorkflowResourceContext resources) throws DocumentFailure {
        if (!nonnegative(paragraph.getLeftIndent()) || !nonnegative(paragraph.getRightIndent())
                || !PdfBoxPageContentSupport.isValidNumber(paragraph.getFirstLineIndent())
                || !nonnegative(paragraph.getLeftIndent() + paragraph.getFirstLineIndent())
                || !positive(paragraph.getTabInterval()) || paragraph.getWidows() < 1 || paragraph.getOrphans() < 1) {
            throw invalid();
        }
        double previous = 0;
        for (TabStop stop : paragraph.getTabStops()) {
            resources.checkpoint();
            int anchor = stop.getAnchor();
            if (!positive(stop.getPosition()) || stop.getPosition() <= previous
                    || !Character.isValidCodePoint(anchor) || Character.isISOControl(anchor)
                    || (anchor >= Character.MIN_SURROGATE && anchor <= Character.MAX_SURROGATE)) { throw invalid(); }
            previous = stop.getPosition();
        }
        if (paragraph.getVersion() == Paragraph.VERSION_1
                && (paragraph.getLeftIndent() != 0 || paragraph.getRightIndent() != 0
                    || paragraph.getFirstLineIndent() != 0 || paragraph.getTabInterval() != 36
                    || !paragraph.getTabStops().isEmpty() || paragraph.isKeepWithNext()
                    || paragraph.isKeepTogether() || paragraph.getWidows() != 1 || paragraph.getOrphans() != 1
                    || paragraph.getOverflow() != Paragraph.Overflow.WRAP)) { throw invalid(); }
    }

    List<PDPage> createPages(ParagraphFlow flow, int count) throws DocumentFailure {
        List<PDPage> result = new ArrayList<PDPage>(count);
        for (int index = 0; index < count; index++) {
            result.add(createPage(flow.getPages().get(index)));
        }
        return result;
    }

    PDPage createPage(LayoutPage declaration) throws DocumentFailure {
        resources.checkpoint();
        PDPage page = new PDPage(new PDRectangle((float) declaration.getWidth(), (float) declaration.getHeight()));
        page.setResources(new PDResources());
        resources.observePage(page.getCOSObject());
        return page;
    }

    long[] paint(List<Line> lines, List<PdfBoxTableLayout.Plan> tablePlans,
            PdfBoxPositionedTextOperations.PreparedText prepared, List<PDPage> detached, CompositionLimits limits,
            long contentBytes, long textBytes) throws DocumentFailure {
        int tableIndex = 0;
        for (int lineIndex = 0; lineIndex <= lines.size(); lineIndex++) {
            while (tableIndex < tablePlans.size() && tablePlans.get(tableIndex).lineOffset == lineIndex) {
                PdfBoxTableLayout.Plan table = tablePlans.get(tableIndex++);
                for (CanvasRectangle box : table.borders) {
                    resources.checkpoint();
                    CanvasProgram border = CanvasProgram.version2()
                            .setFillColor(CanvasColor.of(CanvasColorSpace.deviceGray(), 0))
                            .moveTo(box.getLowerLeftX(), box.getLowerLeftY())
                            .lineTo(box.getUpperRightX(), box.getLowerLeftY())
                            .lineTo(box.getUpperRightX(), box.getUpperRightY())
                            .lineTo(box.getLowerLeftX(), box.getUpperRightY()).closePath()
                            .fill(CanvasWindingRule.NONZERO).build();
                    contentBytes += canvas.drawLayoutGraphic(DrawCanvas.version2(1, border, limits.getGraphicLimits()),
                            detached.get(table.area.page), limits.getMaximumGeneratedContentBytes() - contentBytes);
                }
            }
            if (lineIndex == lines.size()) { break; }
            Line line = lines.get(lineIndex);
            resources.checkpoint();
            PDPage page = detached.get(line.area.page);
            double spare = Math.max(0, line.availableWidth - line.width);
            double x = line.left;
            Paragraph.Alignment alignment = line.tabbed ? Paragraph.Alignment.LEFT : line.paragraph.getAlignment();
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
                if (atom.codePoint == '\t') { x += line.advances[index - line.first]; index++; continue; }
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
                        if (next.codePoint == '\t' || next.inline.getKind() != Paragraph.Inline.Kind.TEXT
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
        return new long[] {contentBytes, textBytes};
    }

    private final class Layout {
        private final ComposeParagraphs command;
        private final PdfBoxPositionedTextOperations.PreparedText prepared;
        private final List<Area> areas = new ArrayList<Area>();
        private final List<Line> lines = new ArrayList<Line>();
        private int areaIndex;
        private final int[] glyphIndex = {0};
        private final PdfBoxTableLayout tables;
        private final List<PdfBoxTableLayout.Plan> tablePlans = new ArrayList<PdfBoxTableLayout.Plan>();
        private final List<PdfBoxTableLayout.Content> tableContent = new ArrayList<PdfBoxTableLayout.Content>();
        private int furthestItem = -1;
        private int furthestOffset = -1;
        private boolean furthestTableKeep;
        private boolean furthestLineLimitReached;
        private int lastPage;
        private double usedHeight;
        private double heightCorrection;

        Layout(ComposeParagraphs command, PdfBoxPositionedTextOperations.PreparedText prepared)
                throws DocumentFailure {
            this.command = command;
            this.prepared = prepared;
            tables = command.getVersion() >= ComposeParagraphs.VERSION_3
                    ? new PdfBoxTableLayout(PdfBoxParagraphOperations.this, resources, command.getLimits().getTableLimits()) : null;
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
            if (command.getVersion() >= ComposeParagraphs.VERSION_2) { composeAdvanced(); return; }
            for (ParagraphFlow.Item item : command.getFlow().getItems()) {
                resources.checkpoint();
                if (item.getKind() == ParagraphFlow.Item.Kind.AREA_BREAK) {
                    nextArea();
                    continue;
                }
                Paragraph paragraph = item.getParagraph();
                List<Atom> atoms = atoms(paragraph, prepared, glyphIndex);
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

        private int attempts;
        private boolean lineLimitReached;
        private final List<List<Atom>> content = new ArrayList<List<Atom>>();

        private void attempt() throws DocumentFailure {
            resources.checkpoint();
            if (attempts >= command.getLimits().getMaximumLayoutAttempts()) { throw limitFailure(); }
            attempts++;
        }

        private void composeAdvanced() throws DocumentFailure {
            boolean constrained = false;
            for (ParagraphFlow.Item item : command.getFlow().getItems()) {
                resources.checkpoint();
                Paragraph paragraph = item.getParagraph();
                content.add(paragraph == null ? null : atoms(paragraph, prepared, glyphIndex));
                tableContent.add(item.getKind() == ParagraphFlow.Item.Kind.TABLE
                        ? tables.prepare(item.getTable(), prepared, glyphIndex) : null);
                if (paragraph != null && (paragraph.isKeepTogether() || paragraph.isKeepWithNext()
                        || paragraph.getWidows() > 1 || paragraph.getOrphans() > 1)) { constrained = true; }
            }
            Deque<Frame> stack = new ArrayDeque<Frame>();
            try {
                stack.push(new Frame(new State(0, 0, 0, 0, 0, false)));
                while (!stack.isEmpty()) {
                    Frame frame = stack.peek();
                    State next = frame.next();
                    if (next == null) { stack.pop().close(); continue; }
                    if (next.item == content.size()) {
                        lastPage = areas.get(next.area).page;
                        return;
                    }
                    stack.push(new Frame(next));
                }
            } finally {
                while (!stack.isEmpty()) { stack.pop().close(); }
            }
            if (tables == null ? lineLimitReached : furthestLineLimitReached) { throw limitFailure(); }
            if (tables != null && (tableContent.get(furthestItem) != null || furthestTableKeep)) {
                throw PdfBoxTableLayout.unsatisfied();
            }
            throw failure(constrained ? DocumentFailureCode.COMPOSITION_CONSTRAINT_UNSATISFIED
                            : DocumentFailureCode.COMPOSITION_AREA_EXHAUSTED,
                    constrained ? "The finite layout areas cannot satisfy the paragraph constraints."
                            : "The remaining layout areas cannot contain the paragraph flow.");
        }

        private final class Frame implements AutoCloseable {
            private final State state;
            private final int base;
            private final int tableBase;
            private PdfBoxTableLayout.Plan tablePlan;
            private WorkflowResourceContext.OwnedMemoryScope tableMemory;
            private boolean tableTried;
            private final List<Line> candidates = new ArrayList<Line>();
            private final WorkflowResourceContext.OwnedMemoryScope memory = resources.ownedMemoryScope();
            private int count;
            private boolean skipped;

            Frame(State state) throws DocumentFailure {
                this.state = state;
                this.base = lines.size();
                this.tableBase = tablePlans.size();
                boolean ready = false;
                try {
                    memory.retain(128);
                    if (state.item > furthestItem) {
                        furthestItem = state.item;
                        furthestOffset = -1;
                        furthestLineLimitReached = false;
                    }
                    if (state.item == furthestItem && state.first > furthestOffset) {
                        furthestOffset = state.first;
                        furthestTableKeep = state.keep && state.item > 0 && tableContent.get(state.item - 1) != null;
                    }
                    if (tableContent.get(state.item) != null) {
                        attempt();
                        tableMemory = resources.ownedMemoryScope();
                        tablePlan = tables.layout(tableContent.get(state.item), areas.get(state.area), state.used, state.first,
                                state.tableCursor, Double.POSITIVE_INFINITY, command.getLimits().getMaximumLines() - base, tableMemory);
                        if (tables.lineLimitReached && state.item == furthestItem) { furthestLineLimitReached = true; }
                    } else if (content.get(state.item) != null) {
                        Paragraph paragraph = command.getFlow().getItems().get(state.item).getParagraph();
                        List<Atom> atoms = content.get(state.item);
                        Area area = areas.get(state.area);
                        double width = area.box.getUpperRightX() - area.box.getLowerLeftX();
                        if (paragraph.getMaximumWidth() > 0) { width = Math.min(width, paragraph.getMaximumWidth()); }
                        double height = state.used;
                        double correction = state.correction;
                        int first = state.first;
                        while (first < atoms.size()) {
                            attempt();
                            Line candidate = line(atoms, first, paragraph, area, width);
                            if (candidate == null || exceeds(height + candidate.height,
                                    area.box.getUpperRightY() - area.box.getLowerLeftY())) { break; }
                            if (base + candidates.size() >= command.getLimits().getMaximumLines()) {
                                lineLimitReached = true;
                                if (state.item == furthestItem) { furthestLineLimitReached = true; }
                                break;
                            }
                            memory.retain(256L + 8L * (candidate.end - candidate.first));
                            candidate.baseline = area.box.getUpperRightY() - height - candidate.ascent;
                            double increment = candidate.height - correction;
                            double after = height + increment;
                            correction = (after - height) - increment;
                            height = after;
                            candidate.usedAfter = height;
                            candidate.correctionAfter = correction;
                            candidates.add(candidate);
                            first = candidate.next;
                        }
                    }
                    count = candidates.size();
                    ready = true;
                } finally { if (!ready) { close(); } }
            }

            State next() throws DocumentFailure {
                while (lines.size() > base) { lines.remove(lines.size() - 1); }
                while (tablePlans.size() > tableBase) { tablePlans.remove(tablePlans.size() - 1); }
                if (tableContent.get(state.item) != null) {
                    attempt();
                    if (tableTried && tablePlan != null && tablePlan.previousHeight > 0) {
                        double height = tablePlan.previousHeight;
                        tablePlan = null;
                        tableMemory.close();
                        tableMemory = resources.ownedMemoryScope();
                        tablePlan = tables.layout(tableContent.get(state.item), areas.get(state.area), state.used, state.first,
                                state.tableCursor, height, command.getLimits().getMaximumLines() - base, tableMemory);
                        if (tables.lineLimitReached && state.item == furthestItem) { furthestLineLimitReached = true; }
                        tableTried = false;
                    }
                    if (!tableTried) {
                        tableTried = true;
                        if (tablePlan != null) {
                            boolean complete = tablePlan.nextRow == tableContent.get(state.item).table.getRows().size();
                            if (!complete && state.area + 1 >= areas.size()) { return null; }
                            tablePlan.lineOffset = base;
                            tablePlans.add(tablePlan); lines.addAll(tablePlan.lines);
                            if (!complete) {
                                return new State(state.item, tablePlan.nextRow, state.area + 1, 0, 0, false, tablePlan.continuation);
                            }
                            double increment = tablePlan.height - state.correction;
                            double after = state.used + increment;
                            return new State(state.item + 1, 0, state.area, after,
                                    (after - state.used) - increment, tableContent.get(state.item).table.isKeepWithNext());
                        }
                    }
                    if (skipped || state.keep || state.area + 1 >= areas.size()) { return null; }
                    skipped = true;
                    return new State(state.item, state.first, state.area + 1, 0, 0, false, state.tableCursor);
                }
                if (content.get(state.item) == null) {
                    attempt();
                    if (skipped || state.keep || state.area + 1 >= areas.size()) { return null; }
                    skipped = true;
                    return new State(state.item + 1, 0, state.area + 1, 0, 0, false);
                }
                Paragraph paragraph = command.getFlow().getItems().get(state.item).getParagraph();
                while (count > 0) {
                    attempt();
                    int size = count--;
                    Line last = candidates.get(size - 1);
                    boolean complete = last.next == content.get(state.item).size();
                    if (state.first > 0 && size < paragraph.getWidows()) { continue; }
                    if (!complete && (paragraph.isKeepTogether() || size < paragraph.getOrphans()
                            || state.area + 1 >= areas.size())) { continue; }
                    for (int index = 0; index < size; index++) { lines.add(candidates.get(index)); }
                    if (complete) {
                        return new State(state.item + 1, 0, state.area, last.usedAfter,
                                last.correctionAfter, paragraph.isKeepWithNext());
                    }
                    return new State(state.item, last.next, state.area + 1, 0, 0, false);
                }
                attempt();
                if (skipped || state.keep || state.area + 1 >= areas.size()) { return null; }
                skipped = true;
                return new State(state.item, state.first, state.area + 1, 0, 0, false);
            }

            @Override public void close() {
                if (tableMemory != null) { tableMemory.close(); }
                memory.close();
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

    }

    List<Atom> atoms(Paragraph paragraph, PdfBoxPositionedTextOperations.PreparedText prepared, int[] glyphIndex) throws DocumentFailure {
        List<Atom> result = new ArrayList<Atom>();
        for (Paragraph.Inline inline : paragraph.getInlines()) {
            if (inline.getKind() == Paragraph.Inline.Kind.GRAPHIC) {
                result.add(new Atom(inline, -1, -1, inline.getWidth(), inline.getHeight(), 0));
            } else {
                for (int offset = 0; offset < inline.getText().length();) {
                    resources.checkpoint();
                    int cp = inline.getText().codePointAt(offset);
                    offset += Character.charCount(cp);
                    if (cp == '\n' || cp == '\t') {
                        result.add(new Atom(inline, -1, cp, 0, 0, 0));
                    } else {
                        double size = inline.getFontSize();
                        result.add(new Atom(inline, glyphIndex[0], cp, prepared.width(glyphIndex[0], size),
                                prepared.ascent(glyphIndex[0], size), prepared.descent(glyphIndex[0], size)));
                        glyphIndex[0]++;
                    }
                }
            }
        }
        return result;
    }

    Line line(List<Atom> atoms, int first, Paragraph paragraph, Area area, double available)
            throws DocumentFailure {
        return line(atoms, first, paragraph, area, available, paragraph.getOverflow());
    }

    Line line(List<Atom> atoms, int first, Paragraph paragraph, Area area, double available, Paragraph.Overflow overflow)
            throws DocumentFailure {
        double extra = first == 0 ? paragraph.getFirstLineIndent() : 0;
        double left = area.box.getLowerLeftX() + paragraph.getLeftIndent() + extra;
        available -= paragraph.getLeftIndent() + paragraph.getRightIndent() + extra;
        if (!positive(available)) { return null; }
        List<Double> advances = new ArrayList<Double>();
        int end = first;
        int lastBreak = -1;
        double width = 0;
        double correction = 0;
        while (end < atoms.size()) {
            resources.checkpoint();
            Atom atom = atoms.get(end);
            if (atom.codePoint == '\n') { break; }
            int unitEnd = end + 1;
            double advance = atom.width;
            if (atom.codePoint == '\t') {
                while (unitEnd < atoms.size() && atoms.get(unitEnd).codePoint != '\t'
                        && atoms.get(unitEnd).codePoint != '\n') { resources.checkpoint(); unitEnd++; }
                advance = tabAdvance(paragraph, atoms, end, unitEnd, width + extra);
            } else if (overflow != Paragraph.Overflow.WRAP && atom.codePoint != ' '
                    && atom.inline.getKind() == Paragraph.Inline.Kind.TEXT) {
                while (unitEnd < atoms.size()) {
                    resources.checkpoint();
                    Atom next = atoms.get(unitEnd);
                    if (next.codePoint == ' ' || next.codePoint == '\n' || next.codePoint == '\t'
                            || next.inline.getKind() == Paragraph.Inline.Kind.GRAPHIC) { break; }
                    unitEnd++;
                }
            }
            double unitWidth = advance;
            for (int index = end + 1; index < unitEnd; index++) {
                resources.checkpoint(); unitWidth += atoms.get(index).width;
            }
            double increment = unitWidth - correction;
            double nextWidth = width + increment;
            if (exceeds(nextWidth, available)
                    && !(end == first && overflow == Paragraph.Overflow.VISIBLE)) {
                if (end == first) { return null; }
                if (lastBreak > first) { end = lastBreak; }
                break;
            }
            correction = (nextWidth - width) - increment;
            width = nextWidth;
            advances.add(Double.valueOf(advance));
            for (int index = end + 1; index < unitEnd; index++) { advances.add(Double.valueOf(atoms.get(index).width)); }
            end = unitEnd;
            if (atom.codePoint == ' ' || atom.codePoint == '\t'
                    || atom.inline.getKind() == Paragraph.Inline.Kind.GRAPHIC) { lastBreak = end; }
        }
        int next = end;
        boolean automatic = end < atoms.size() && atoms.get(end).codePoint != '\n';
        if (end < atoms.size() && atoms.get(end).codePoint == '\n') { next++; }
        double ascent = 0;
        double descent = 0;
        width = 0;
        correction = 0;
        boolean tabbed = false;
        double[] measured = new double[end - first];
        for (int index = first; index < end; index++) {
            resources.checkpoint();
            Atom atom = atoms.get(index);
            measured[index - first] = advances.get(index - first).doubleValue();
            double increment = measured[index - first] - correction;
            double nextWidth = width + increment;
            correction = (nextWidth - width) - increment;
            width = nextWidth;
            tabbed |= atom.codePoint == '\t';
            ascent = Math.max(ascent, atom.ascent);
            descent = Math.max(descent, atom.descent);
        }
        Line result = new Line(atoms, paragraph, area, first, end, next, width, available,
                ascent, Math.max(paragraph.getLeading(), ascent + descent), automatic);
        result.left = left;
        result.advances = measured;
        result.tabbed = tabbed;
        return result;
    }

    private double tabAdvance(Paragraph paragraph, List<Atom> atoms, int tab, int end, double current)
            throws DocumentFailure {
        double fieldWidth = 0;
        for (int index = tab + 1; index < end; index++) {
            resources.checkpoint(); fieldWidth += atoms.get(index).width;
        }
        double lastStop = 0;
        for (TabStop stop : paragraph.getTabStops()) {
            resources.checkpoint();
            lastStop = stop.getPosition();
            double offset = 0;
            if (stop.getAlignment() == TabStop.Alignment.RIGHT) { offset = fieldWidth; }
            if (stop.getAlignment() == TabStop.Alignment.CENTER) { offset = fieldWidth / 2; }
            if (stop.getAlignment() == TabStop.Alignment.ANCHOR) {
                for (int index = tab + 1; index < end; index++) {
                    resources.checkpoint();
                    if (atoms.get(index).codePoint == stop.getAnchor()) { break; }
                    offset += atoms.get(index).width;
                }
            }
            double start = stop.getPosition() - offset;
            if (start > current + FIT_TOLERANCE) { return start - current; }
        }
        double interval = paragraph.getTabInterval();
        double start = (Math.floor(Math.max(current, lastStop) / interval) + 1) * interval;
        if (!positive(start) || !positive(start - current)) { throw invalid(); }
        return start - current;
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
    static DocumentFailure signatureFailure() { return signatureFailure(CAPABILITY_ID); }
    static DocumentFailure signatureFailure(String capability) {
        return new DocumentFailure(DocumentFailureCode.SIGNATURE_POLICY_REJECTED, capability,
                "The Existing Signature policy does not permit paragraph composition.");
    }
    static void requirePermission(PasswordSecurityInfo security) throws DocumentFailure {
        requirePermission(security, CAPABILITY_ID);
    }
    static void requirePermission(PasswordSecurityInfo security, String capability) throws DocumentFailure {
        if (security.isPasswordProtected() && (!security.getEffectivePermissions().canModify()
                || !security.getEffectivePermissions().canAssembleDocument())) {
            throw new DocumentFailure(DocumentFailureCode.DOCUMENT_PERMISSION_DENIED, capability,
                    "The Source credential does not authorize paragraph composition.");
        }
    }
    private static DocumentFailure failure(DocumentFailureCode code, String diagnostic) {
        return new DocumentFailure(code, CAPABILITY_ID, diagnostic);
    }

    private static final class State {
        final int item;
        final int first;
        final int area;
        final double used;
        final double correction;
        final boolean keep;
        final PdfBoxTableLayout.Cursor tableCursor;
        State(int item, int first, int area, double used, double correction, boolean keep) {
            this(item, first, area, used, correction, keep, null);
        }
        State(int item, int first, int area, double used, double correction, boolean keep, PdfBoxTableLayout.Cursor tableCursor) {
            this.item = item; this.first = first; this.area = area;
            this.used = used; this.correction = correction; this.keep = keep;
            this.tableCursor = tableCursor;
        }
    }

    static final class Area {
        final int page;
        final CanvasRectangle box;
        Area(int page, CanvasRectangle box) { this.page = page; this.box = box; }
    }
    static final class Atom {
        final Paragraph.Inline inline;
        final int glyph;
        final int codePoint;
        final double width;
        final double ascent;
        final double descent;
        Atom(Paragraph.Inline inline, int glyph, int codePoint, double width, double ascent, double descent) {
            this.inline = inline; this.glyph = glyph; this.codePoint = codePoint;
            this.width = width; this.ascent = ascent; this.descent = descent;
        }
    }
    static final class Line {
        final List<Atom> atoms;
        final Paragraph paragraph;
        final Area area;
        final int first;
        final int end;
        final int next;
        final double width;
        final double availableWidth;
        final double ascent;
        final double height;
        final boolean automatic;
        double baseline;
        double left;
        private double[] advances;
        boolean tabbed;
        double usedAfter;
        double correctionAfter;
        Line(List<Atom> atoms, Paragraph paragraph, Area area, int first, int end, int next,
                double width, double availableWidth, double ascent, double height, boolean automatic) {
            this.atoms = atoms; this.paragraph = paragraph; this.area = area;
            this.first = first; this.end = end; this.next = next; this.width = width;
            this.availableWidth = availableWidth; this.ascent = ascent; this.height = height;
            this.automatic = automatic;
        }
    }
}
