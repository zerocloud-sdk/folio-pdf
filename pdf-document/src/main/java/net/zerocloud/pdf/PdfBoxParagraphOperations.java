package net.zerocloud.pdf;

import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.text.Bidi;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UScript;
import com.ibm.icu.lang.UProperty;
import com.ibm.icu.util.ULocale;
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
import net.zerocloud.pdf.provider.ShapingRequest;
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
        String unicode = paragraphUnicode(paragraph);
        Bidi bidi = paragraphBidi(unicode);
        int offset = 0;
        for (Paragraph.Inline inline : paragraph.getInlines()) {
            resources.checkpoint();
            if (inline.getKind() == Paragraph.Inline.Kind.GRAPHIC) { offset++; continue; }
            for (int index = 0; index < inline.getText().length();) {
                resources.checkpoint();
                int cp = inline.getText().codePointAt(index);
                if (!hardBreak(cp) && cp != '\t' && !bidiControl(cp)) {
                    text.appendCodePoint(resources.shapesComposition() || (bidi.getLevelAt(offset) & 1) == 0
                            ? cp : UCharacter.getMirror(cp));
                }
                int length = Character.charCount(cp);
                index += length;
                offset += length;
            }
        }
    }

    private String paragraphUnicode(Paragraph paragraph) throws DocumentFailure {
        StringBuilder text = new StringBuilder();
        for (Paragraph.Inline inline : paragraph.getInlines()) {
            if (inline.getKind() == Paragraph.Inline.Kind.TEXT) {
                String value = inline.getText();
                for (int index = 0; index < value.length(); index++) {
                    resources.checkpoint();
                    text.append(value.charAt(index));
                }
            } else { text.append('\ufffc'); }
        }
        return text.toString();
    }

    private Bidi paragraphBidi(String unicode) throws DocumentFailure {
        resources.checkpoint();
        Bidi bidi = new Bidi();
        bidi.setPara(unicode, Bidi.LEVEL_DEFAULT_LTR, null);
        resources.checkpoint();
        return bidi;
    }

    private static boolean bidiControl(int codePoint) {
        return UCharacter.hasBinaryProperty(codePoint, UProperty.BIDI_CONTROL);
    }

    static boolean hardBreak(int codePoint) {
        return codePoint == '\n' || codePoint == 0x2028 || codePoint == 0x2029;
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
        try (Layout layout = new Layout(command, prepared)) {
            layout.compose();
            List<PDPage> detached = createPages(command.getFlow(), layout.lastPage + 1);
            paint(layout.lines, layout.tablePlans, prepared, detached, command.getLimits(), 0, 0);
            fonts.finalizeFonts();
            return detached;
        }
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
            int[] order = visualOrder(line);
            if (alignment == Paragraph.Alignment.JUSTIFIED && line.automatic) {
                for (int index = 0; index < order.length - 1; index++) {
                    if (line.atoms.get(order[index]).justificationSpace) { spaces++; }
                }
            }
            double gap = spaces == 0 ? 0 : spare / spaces;
            for (int index = 0; index < order.length;) {
                resources.checkpoint();
                Atom atom = line.atoms.get(order[index]);
                if (atom.shapedContinuation) { index++; continue; }
                if (atom.codePoint == '\t') { x += line.advances[order[index] - line.first]; index++; continue; }
                if (atom.glyph < 0 && atom.inline.getKind() == Paragraph.Inline.Kind.TEXT) { index++; continue; }
                long remaining = limits.getMaximumGeneratedContentBytes() - contentBytes;
                long written;
                if (atom.shaped != null) {
                    List<PdfBoxPositionedTextOperations.ShapedCluster> clusters =
                            new ArrayList<PdfBoxPositionedTextOperations.ShapedCluster>();
                    clusters.add(atom.shaped);
                    int end = index + 1;
                    double width = atom.width;
                    while (end < order.length) {
                        Atom next = line.atoms.get(order[end]);
                        if (next.shapedContinuation) { end++; continue; }
                        if (next.shaped == null || !atom.shaped.sameRun(next.shaped)
                                || (gap > 0 && line.atoms.get(order[end - 1]).justificationSpace)) { break; }
                        clusters.add(next.shaped);
                        width += next.width;
                        end++;
                    }
                    long textRemaining = limits.getFontLimits().getMaximumGeneratedContentBytes() - textBytes;
                    written = atom.shaped.draw(page, clusters, atom.inline.getFontSize(), x, line.baseline, textRemaining);
                    if (written > remaining) { throw limitFailure(); }
                    textBytes += written;
                    x += width;
                    if (line.atoms.get(order[end - 1]).justificationSpace && end < order.length) { x += gap; }
                    index = end;
                } else if (atom.inline.getKind() == Paragraph.Inline.Kind.GRAPHIC) {
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
                    while (end < order.length && !line.atoms.get(order[end - 1]).wordBreakAfter
                            && !(gap > 0 && line.atoms.get(order[end - 1]).justificationSpace)) {
                        Atom next = line.atoms.get(order[end]);
                        if (next.codePoint == '\t' || next.inline.getKind() != Paragraph.Inline.Kind.TEXT
                                || next.glyph != line.atoms.get(order[end - 1]).glyph + 1
                                || next.script != atom.script
                                || next.inline.getFontSize() != atom.inline.getFontSize()) { break; }
                        width += next.width;
                        end++;
                    }
                    long textRemaining = limits.getFontLimits().getMaximumGeneratedContentBytes() - textBytes;
                    written = prepared.draw(page, atom.glyph, line.atoms.get(order[end - 1]).glyph + 1,
                            atom.inline.getFontSize(), x, line.baseline, textRemaining);
                    if (written > remaining) { throw limitFailure(); }
                    textBytes += written;
                    x += width;
                    if (line.atoms.get(order[end - 1]).justificationSpace && end < order.length) { x += gap; }
                    index = end;
                }
                contentBytes += written;
            }
        }
        return new long[] {contentBytes, textBytes};
    }

    /** Absolute tab fields retain declaration order; each field has its own visual order. */
    private int[] visualOrder(Line line) throws DocumentFailure {
        int[] order = new int[line.end - line.first];
        int first = line.first;
        int target = 0;
        for (int end = first; end <= line.end; end++) {
            resources.checkpoint();
            if (end == line.end || line.atoms.get(end).codePoint == '\t') {
                int[] field = visualOrder(line.atoms, first, end);
                for (int index : field) { order[target++] = index; }
                if (end < line.end) { order[target++] = end; }
                first = end + 1;
            }
        }
        return order;
    }

    /** UAX #9 line reordering operates on complete UAX #29 clusters (L3). */
    private int[] visualOrder(List<Atom> atoms, int from, int to) throws DocumentFailure {
        int length = to - from;
        int[] order = new int[length];
        if (length == 0) { return order; }
        Atom first = atoms.get(from);
        Atom last = atoms.get(to - 1);
        int end = last.characterOffset + Character.charCount(last.codePoint < 0 ? 0xfffc : last.codePoint);
        Bidi bidi = first.bidi.setLine(first.characterOffset, end);
        int[] starts = new int[length + 1];
        byte[] levels = new byte[length];
        int count = 0;
        for (int index = from; index < to; index++) {
            resources.checkpoint();
            if (index == from || atoms.get(index - 1).clusterEnd) {
                starts[count] = index;
                levels[count++] = bidi.getLevelAt(atoms.get(index).characterOffset - first.characterOffset);
            }
        }
        starts[count] = to;
        int[] clusters = Bidi.reorderVisual(java.util.Arrays.copyOf(levels, count));
        int target = 0;
        for (int cluster : clusters) {
            resources.checkpoint();
            for (int index = starts[cluster]; index < starts[cluster + 1]; index++) { order[target++] = index; }
        }
        return order;
    }

    private final class Layout implements AutoCloseable {
        private final WorkflowResourceContext.OwnedMemoryScope shapingPlans = resources.ownedMemoryScope();
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
                List<Atom> atoms = atoms(paragraph, prepared, glyphIndex, shapingPlans);
                int first = 0;
                while (first < atoms.size()) {
                    resources.checkpoint();
                    Area area = areas.get(areaIndex);
                    double width = area.box.getUpperRightX() - area.box.getLowerLeftX();
                    if (paragraph.getMaximumWidth() > 0) { width = Math.min(width, paragraph.getMaximumWidth()); }
                    Line line = line(atoms, first, paragraph, area, width);
                    try {
                        double areaHeight = area.box.getUpperRightY() - area.box.getLowerLeftY();
                        if (line == null || exceeds(usedHeight + line.height, areaHeight)) {
                            nextArea();
                            continue;
                        }
                        if (lines.size() >= command.getLimits().getMaximumLines()) { throw limitFailure(); }
                        retainCandidate(line, shapingPlans);
                        line.baseline = area.box.getUpperRightY() - usedHeight - line.ascent;
                        lines.add(line);
                        first = line.next;
                        // Compensated accumulation avoids drift over many fractional lines.
                        double increment = line.height - heightCorrection;
                        double nextHeight = usedHeight + increment;
                        heightCorrection = (nextHeight - usedHeight) - increment;
                        usedHeight = nextHeight;
                    } finally {
                        releaseCandidate(line);
                    }
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
                content.add(paragraph == null ? null : atoms(paragraph, prepared, glyphIndex, shapingPlans));
                tableContent.add(item.getKind() == ParagraphFlow.Item.Kind.TABLE
                        ? tables.prepare(item.getTable(), prepared, glyphIndex, shapingPlans) : null);
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
                        for (Line line : lines) { retainCandidate(line, shapingPlans); }
                        for (Frame selected : stack) {
                            if (selected.tableSelected) { selected.tableMemory.transferTo(shapingPlans); }
                        }
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
            private boolean tableSelected;
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
                        if (tablePlan == null) { tableMemory.close(); tableMemory = null; }
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
                            boolean retained = false;
                            try {
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
                                retained = true;
                                first = candidate.next;
                            } finally { if (!retained) { releaseCandidate(candidate); } }
                        }
                    }
                    count = candidates.size();
                    ready = true;
                } finally { if (!ready) { close(); } }
            }

            State next() throws DocumentFailure {
                tableSelected = false;
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
                        if (tablePlan == null) { tableMemory.close(); tableMemory = null; }
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
                            tableSelected = true;
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
                for (Line candidate : candidates) { releaseCandidate(candidate); }
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

        @Override public void close() { shapingPlans.close(); }

    }

    List<Atom> atoms(Paragraph paragraph, PdfBoxPositionedTextOperations.PreparedText prepared, int[] glyphIndex,
            WorkflowResourceContext.OwnedMemoryScope ownership) throws DocumentFailure {
        List<Atom> result = new ArrayList<Atom>();
        for (Paragraph.Inline inline : paragraph.getInlines()) {
            if (inline.getKind() == Paragraph.Inline.Kind.GRAPHIC) {
                result.add(new Atom(inline, -1, -1, inline.getWidth(), inline.getHeight(), 0));
            } else {
                for (int offset = 0; offset < inline.getText().length();) {
                    resources.checkpoint();
                    int cp = inline.getText().codePointAt(offset);
                    offset += Character.charCount(cp);
                    if (hardBreak(cp) || cp == '\t' || bidiControl(cp)) {
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
        BreakIterator clusters = BreakIterator.getCharacterInstance(ULocale.ROOT);
        String value = paragraphUnicode(paragraph);
        clusters.setText(value);
        BreakIterator breaks = BreakIterator.getLineInstance(ULocale.ROOT);
        breaks.setText(value);
        BreakIterator words = BreakIterator.getWordInstance(ULocale.ROOT);
        words.setText(value);
        Bidi bidi = paragraphBidi(value);
        int boundary = clusters.next();
        int lineBoundary = breaks.next();
        int wordBoundary = words.next();
        int offset = 0;
        int script = UScript.COMMON;
        boolean spaceCluster = false;
        for (Atom atom : result) {
            resources.checkpoint();
            atom.characterOffset = offset;
            atom.bidi = bidi;
            offset += Character.charCount(atom.codePoint < 0 ? 0xfffc : atom.codePoint);
            while (boundary < offset && boundary != BreakIterator.DONE) { boundary = clusters.next(); }
            while (lineBoundary < offset && lineBoundary != BreakIterator.DONE) { lineBoundary = breaks.next(); }
            while (wordBoundary < offset && wordBoundary != BreakIterator.DONE) { wordBoundary = words.next(); }
            atom.graphemeEnd = boundary == offset;
            atom.clusterEnd = atom.graphemeEnd;
            spaceCluster |= atom.codePoint == ' ';
            atom.justificationSpace = atom.clusterEnd && spaceCluster;
            if (atom.clusterEnd) { spaceCluster = false; }
            atom.lineBreakAfter = atom.clusterEnd && lineBoundary == offset;
            atom.wordBreakAfter = atom.clusterEnd && wordBoundary == offset;
            atom.unicodeLineBreakAfter = atom.lineBreakAfter;
            atom.unicodeWordBreakAfter = atom.wordBreakAfter;
            atom.shapingSource = prepared.shapes() ? prepared : null;
            atom.shapingLevel = bidi.getLevelAt(atom.characterOffset);
            int resolved = atom.codePoint < 0 ? UScript.COMMON : UScript.getScript(atom.codePoint);
            if (resolved != UScript.COMMON && resolved != UScript.INHERITED) { script = resolved; }
            atom.script = script;
            if (atom.wordBreakAfter) { script = UScript.COMMON; }
        }
        if (prepared.shapes()) { shapeAtoms(result, prepared, ownership); }
        return result;
    }

    private void shapeAtoms(List<Atom> atoms, PdfBoxPositionedTextOperations.PreparedText prepared,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        int inheritedScript = UScript.LATIN;
        for (int first = 0; first < atoms.size();) {
            Atom atom = atoms.get(first);
            if (atom.glyph < 0) { first++; continue; }
            int end = first + 1;
            while (end < atoms.size() && !atoms.get(end - 1).clusterEnd) { end++; }
            int script = inheritedScript;
            for (int index = first; index < end; index++) {
                int candidate = UScript.getScript(atoms.get(index).codePoint);
                if (candidate != UScript.COMMON && candidate != UScript.INHERITED) { script = candidate; break; }
            }
            inheritedScript = script;
            int font = prepared.fontForCluster(atom.glyph, atoms.get(end - 1).glyph + 1);
            for (int index = first; index < end; index++) {
                Atom member = atoms.get(index);
                if (member.glyph < 0) { throw ShapingCoordinator.failed(); }
                member.shapingFont = font;
                member.script = script;
            }
            first = end;
        }
        shapeRuns(atoms, prepared, ownership);
    }

    private void shapeRuns(List<Atom> atoms, PdfBoxPositionedTextOperations.PreparedText prepared,
            WorkflowResourceContext.OwnedMemoryScope ownership) throws DocumentFailure {
        for (int first = 0; first < atoms.size();) {
            Atom atom = atoms.get(first);
            if (atom.glyph < 0) { first++; continue; }
            int end = first + 1;
            int level = atom.shapingLevel;
            while (end < atoms.size()) {
                Atom next = atoms.get(end);
                if (next.glyph < 0 || next.shapingFont != atom.shapingFont || next.script != atom.script
                        || next.inline.getFontSize() != atom.inline.getFontSize()
                        || next.shapingLevel != level) { break; }
                end++;
            }
            List<PdfBoxPositionedTextOperations.ShapedCluster> clusters = prepared.shape(atom.glyph,
                    atoms.get(end - 1).glyph + 1, atom.shapingFont, UScript.getShortName(atom.script),
                    shapingLanguage(atom.script), (level & 1) == 0 ? ShapingRequest.Direction.LEFT_TO_RIGHT
                            : ShapingRequest.Direction.RIGHT_TO_LEFT, atom.inline.getFontSize(), ownership);
            int next = first;
            for (PdfBoxPositionedTextOperations.ShapedCluster cluster : clusters) {
                int startOffset = atom.characterOffset + cluster.start;
                int endOffset = atom.characterOffset + cluster.end;
                if (atoms.get(next).characterOffset != startOffset) { throw ShapingCoordinator.failed(); }
                int clusterLimit = next;
                while (clusterLimit < end && atoms.get(clusterLimit).characterOffset < endOffset) { clusterLimit++; }
                // A style run may end inside a grapheme. Its internal native
                // clusters still must respect ICU; layout retains the outer grapheme.
                if (clusterLimit == next || (clusterLimit < end && !atoms.get(clusterLimit - 1).graphemeEnd)
                        || (clusterLimit < end && atoms.get(clusterLimit).characterOffset != endOffset)) {
                    throw ShapingCoordinator.failed();
                }
                Atom owner = atoms.get(next);
                owner.shaped = cluster;
                while (next < clusterLimit) {
                    Atom member = atoms.get(next++);
                    member.width = member == owner ? cluster.width : 0;
                    member.ascent = cluster.ascent;
                    member.descent = cluster.descent;
                    member.shapedContinuation = member != owner;
                    member.clusterEnd = member.graphemeEnd && next == clusterLimit;
                    member.lineBreakAfter &= member.clusterEnd;
                    member.wordBreakAfter &= member.clusterEnd;
                }
            }
            if (next != end) { throw ShapingCoordinator.failed(); }
            first = end;
        }
    }

    private static String shapingLanguage(int script) {
        if (script == UScript.ARABIC) { return "ar"; }
        if (script == UScript.HEBREW) { return "he"; }
        if (script == UScript.DEVANAGARI) { return "hi"; }
        if (script == UScript.THAI) { return "th"; }
        return "und";
    }

    Line line(List<Atom> atoms, int first, Paragraph paragraph, Area area, double available)
            throws DocumentFailure {
        return line(atoms, first, paragraph, area, available, paragraph.getOverflow());
    }

    Line line(List<Atom> atoms, int first, Paragraph paragraph, Area area, double available, Paragraph.Overflow overflow)
            throws DocumentFailure {
        if (first < atoms.size() && atoms.get(first).shapingSource != null && !hardBreak(atoms.get(first).codePoint)) {
            return shapedLine(atoms, first, paragraph, area, available, overflow);
        }
        return measureLine(atoms, first, paragraph, area, available, overflow, first == 0);
    }

    private Line measureLine(List<Atom> atoms, int first, Paragraph paragraph, Area area, double available,
            Paragraph.Overflow overflow, boolean initial) throws DocumentFailure {
        double extra = initial ? paragraph.getFirstLineIndent() : 0;
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
            if (hardBreak(atom.codePoint)) { break; }
            int unitEnd = nextUnit(atoms, end, atoms.size(), overflow, false);
            double advance = atom.codePoint == '\t' ? tabAdvance(paragraph, atoms, end, unitEnd, width + extra) : atom.width;
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
            if (atoms.get(end - 1).lineBreakAfter || atom.codePoint == '\t'
                    || atom.inline.getKind() == Paragraph.Inline.Kind.GRAPHIC) { lastBreak = end; }
        }
        int next = end;
        boolean automatic = end < atoms.size() && !hardBreak(atoms.get(end).codePoint);
        if (end < atoms.size() && hardBreak(atoms.get(end).codePoint)) { next++; }
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

    /** Candidate lines own their shaping data; rejected attempts release it immediately. */
    private Line shapedLine(List<Atom> atoms, int first, Paragraph paragraph, Area area,
            double available, Paragraph.Overflow overflow) throws DocumentFailure {
        int limit = first;
        while (limit < atoms.size() && !hardBreak(atoms.get(limit).codePoint)) { resources.checkpoint(); limit++; }
        Line estimate = measureLine(atoms, first, paragraph, area, available, overflow, first == 0);
        int end = estimate == null || estimate.end == first ? nextUnit(atoms, first, limit, overflow, true) : estimate.end;
        Line best = null;
        try {
            // Whole-run metrics provide a starting estimate. Refit after every changed boundary.
            best = fitShapedCandidate(atoms, first, end, paragraph, area, available, overflow);
            if (best == null) { return null; }
            end = first + best.end;
            while (end < limit) {
                int longer = nextUnit(atoms, end, limit, overflow, true);
                Line candidate = shapeCandidate(atoms, first, longer, paragraph, area, available, overflow);
                if (candidate == null || candidate.end != longer - first) { releaseCandidate(candidate); break; }
                releaseCandidate(best);
                best = candidate;
                end = longer;
            }
            // At an automatic wrap, preserve the established preference for Unicode opportunities.
            if (end < limit) {
                int preferred = first;
                for (int unit = first; unit < end;) {
                    int next = nextUnit(atoms, unit, end, overflow, true);
                    Atom atom = atoms.get(unit);
                    if (atoms.get(next - 1).unicodeLineBreakAfter || atom.codePoint == '\t'
                            || atom.inline.getKind() == Paragraph.Inline.Kind.GRAPHIC) { preferred = next; }
                    unit = next;
                }
                if (preferred > first && preferred < end) {
                    releaseCandidate(best); best = null; end = preferred;
                    best = fitShapedCandidate(atoms, first, end, paragraph, area, available, overflow);
                    if (best == null) { return null; }
                    end = first + best.end;
                }
            }
            int next = end < atoms.size() && hardBreak(atoms.get(end).codePoint) ? end + 1 : end;
            Line result = new Line(best.atoms, paragraph, area, 0, best.end, next, best.width, best.availableWidth,
                    best.ascent, best.height, end < limit);
            result.left = best.left; result.advances = best.advances; result.tabbed = best.tabbed;
            result.shapingMemory = best.shapingMemory;
            best.shapingMemory = null;
            return result;
        } finally { releaseCandidate(best); }
    }

    private Line fitShapedCandidate(List<Atom> atoms, int first, int end, Paragraph paragraph, Area area,
            double available, Paragraph.Overflow overflow) throws DocumentFailure {
        while (end > first) {
            Line candidate = shapeCandidate(atoms, first, end, paragraph, area, available, overflow);
            if (candidate != null && candidate.end == end - first) { return candidate; }
            int shorter = candidate == null ? previousUnit(atoms, first, end, overflow) : first + candidate.end;
            releaseCandidate(candidate);
            end = shorter;
        }
        return null;
    }

    private Line shapeCandidate(List<Atom> atoms, int first, int end, Paragraph paragraph, Area area,
            double available, Paragraph.Overflow overflow) throws DocumentFailure {
        WorkflowResourceContext.OwnedMemoryScope memory = resources.ownedMemoryScope();
        boolean retained = false;
        try {
            List<Atom> copy = shapeFragment(atoms, first, end, memory);
            Line result = measureLine(copy, 0, paragraph, area, available, overflow, first == 0);
            if (result != null) { result.shapingMemory = memory; retained = true; }
            return result;
        } finally { if (!retained) { memory.close(); } }
    }

    private List<Atom> shapeFragment(List<Atom> atoms, int first, int end,
            WorkflowResourceContext.OwnedMemoryScope memory) throws DocumentFailure {
        memory.retain(256L + 192L * (end - first));
        List<Atom> copy = new ArrayList<Atom>(end - first);
        Atom initial = atoms.get(first);
        Atom last = atoms.get(end - 1);
        Bidi bidi = initial.bidi.setLine(initial.characterOffset,
                last.characterOffset + Character.charCount(last.codePoint < 0 ? 0xfffc : last.codePoint));
        for (int index = first; index < end; index++) {
            resources.checkpoint();
            Atom source = atoms.get(index);
            Atom atom = new Atom(source.inline, source.glyph, source.codePoint, source.width, source.ascent, source.descent);
            atom.bidi = source.bidi; atom.characterOffset = source.characterOffset;
            atom.shapingSource = source.shapingSource; atom.shapingFont = source.shapingFont; atom.script = source.script;
            atom.shapingLevel = bidi.getLevelAt(source.characterOffset - initial.characterOffset);
            atom.graphemeEnd = source.graphemeEnd; atom.clusterEnd = source.graphemeEnd;
            atom.justificationSpace = source.justificationSpace;
            atom.lineBreakAfter = source.unicodeLineBreakAfter; atom.wordBreakAfter = source.unicodeWordBreakAfter;
            copy.add(atom);
        }
        shapeRuns(copy, initial.shapingSource, memory);
        return copy;
    }

    /** Measures one original-grapheme-aligned fragment with its actual shaping boundaries. */
    double shapedFragmentAdvance(List<Atom> atoms, int first, int end) throws DocumentFailure {
        try (WorkflowResourceContext.OwnedMemoryScope memory = resources.ownedMemoryScope()) {
            List<Atom> fragment = shapeFragment(atoms, first, end, memory);
            double width = 0, correction = 0;
            for (Atom atom : fragment) {
                resources.checkpoint();
                double increment = atom.width - correction;
                double next = width + increment;
                correction = (next - width) - increment;
                width = next;
            }
            return width;
        }
    }

    static void retainCandidate(Line line, WorkflowResourceContext.OwnedMemoryScope ownership) throws DocumentFailure {
        if (line.shapingMemory != null) {
            line.shapingMemory.transferTo(ownership);
            releaseCandidate(line);
        }
    }

    static void releaseCandidate(Line line) {
        if (line != null && line.shapingMemory != null) {
            line.shapingMemory.close(); line.shapingMemory = null;
        }
    }

    private int previousUnit(List<Atom> atoms, int first, int end, Paragraph.Overflow overflow) throws DocumentFailure {
        int previous = first;
        for (int next = first; next < end;) { previous = next; next = nextUnit(atoms, next, end, overflow, true); }
        return previous;
    }

    /** Uses source boundaries for reshaping and native cluster boundaries for measuring a fixed fragment. */
    private int nextUnit(List<Atom> atoms, int first, int limit, Paragraph.Overflow overflow,
            boolean originalBoundaries) throws DocumentFailure {
        int end = first + 1;
        Atom atom = atoms.get(first);
        if (atom.codePoint == '\t') {
            while (end < limit && atoms.get(end).codePoint != '\t' && !hardBreak(atoms.get(end).codePoint)) {
                resources.checkpoint(); end++;
            }
        } else if (overflow != Paragraph.Overflow.WRAP && atom.inline.getKind() == Paragraph.Inline.Kind.TEXT) {
            while (end < limit && !(originalBoundaries ? atoms.get(end - 1).unicodeLineBreakAfter : atoms.get(end - 1).lineBreakAfter)
                    && !hardBreak(atoms.get(end).codePoint)
                    && atoms.get(end).codePoint != '\t' && atoms.get(end).inline.getKind() != Paragraph.Inline.Kind.GRAPHIC) {
                resources.checkpoint(); end++;
            }
        }
        // A line fragment can break a native ligature, but never an original ICU grapheme.
        while (end < limit && !(originalBoundaries ? atoms.get(end - 1).graphemeEnd : atoms.get(end - 1).clusterEnd)) {
            resources.checkpoint(); end++;
        }
        return end;
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
                int anchor = tab + 1;
                while (anchor < end && atoms.get(anchor).codePoint != stop.getAnchor()) {
                    resources.checkpoint(); anchor++;
                }
                for (int index : visualOrder(atoms, tab + 1, end)) {
                    resources.checkpoint();
                    if (index == anchor) { break; }
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
        double width;
        double ascent;
        double descent;
        PdfBoxPositionedTextOperations.ShapedCluster shaped;
        boolean shapedContinuation;
        int shapingFont;
        boolean clusterEnd = true;
        boolean graphemeEnd = true;
        boolean unicodeLineBreakAfter;
        boolean unicodeWordBreakAfter;
        int shapingLevel;
        PdfBoxPositionedTextOperations.PreparedText shapingSource;
        boolean justificationSpace;
        boolean lineBreakAfter;
        boolean wordBreakAfter;
        int script;
        int characterOffset;
        Bidi bidi;
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
        private WorkflowResourceContext.OwnedMemoryScope shapingMemory;
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
