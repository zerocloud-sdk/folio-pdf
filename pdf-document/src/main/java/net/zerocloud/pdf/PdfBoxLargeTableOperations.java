package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.zerocloud.pdf.composition.CanvasRectangle;
import net.zerocloud.pdf.composition.CompositionLimits;
import net.zerocloud.pdf.composition.FontSelection;
import net.zerocloud.pdf.composition.LargeTableState;
import net.zerocloud.pdf.composition.LayoutPage;
import net.zerocloud.pdf.composition.PageMargins;
import net.zerocloud.pdf.composition.Paragraph;
import net.zerocloud.pdf.composition.ParagraphFlow;
import net.zerocloud.pdf.composition.Table;
import net.zerocloud.pdf.composition.TableCell;
import net.zerocloud.pdf.composition.TableRow;
import net.zerocloud.pdf.composition.TableWidth;
import net.zerocloud.pdf.composition.command.AppendTableRows;
import net.zerocloud.pdf.composition.command.BeginLargeTable;
import net.zerocloud.pdf.composition.command.CompleteTable;
import net.zerocloud.pdf.composition.command.FlushTable;
import net.zerocloud.pdf.composition.command.RelayoutParagraphs;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;

/** One bounded incremental table; only undecided complete row groups remain semantic state. */
final class PdfBoxLargeTableOperations {
    private final PdfBoxParagraphOperations paragraphs;
    private final PdfBoxPositionedTextOperations fonts;
    private final PdfBoxPageOperations pages;
    private final WorkflowResourceContext resources;
    private BeginLargeTable declaration;
    private PdfBoxTableLayout tables;
    private PdfBoxTableLayout.Declarations tableCounts;
    private PdfBoxParagraphOperations.DeclarationCounts textCounts;
    private List<TableRow> retained = Collections.emptyList();
    private List<PdfBoxParagraphOperations.Area> areas = Collections.emptyList();
    private List<PDPage> emittedPages = Collections.emptyList();
    private WorkflowResourceContext.MemoryReservation baseMemory;
    private WorkflowResourceContext.MemoryReservation rowMemory;
    private WorkflowResourceContext.OwnedMemoryScope fontMemory;
    private FontSelection selection;
    private LargeTableState.Stage stage = LargeTableState.Stage.NONE;
    private int acceptedRows;
    private int nextArea;
    private int attempts;
    private int lines;
    private long contentBytes;
    private long textBytes;
    private final long[] fallbackChecks = {0};

    PdfBoxLargeTableOperations(PdfBoxParagraphOperations paragraphs, PdfBoxPositionedTextOperations fonts,
            PdfBoxPageOperations pages, WorkflowResourceContext resources) {
        this.paragraphs = paragraphs; this.fonts = fonts; this.pages = pages; this.resources = resources;
    }

    static boolean supports(DocumentCommand command) {
        return command instanceof BeginLargeTable || command instanceof AppendTableRows
                || command instanceof FlushTable || command instanceof CompleteTable;
    }

    void requireCommand(DocumentCommand command) throws DocumentFailure {
        if (stage != LargeTableState.Stage.OPEN || supports(command)) { return; }
        if (command instanceof RelayoutParagraphs) {
            throw new DocumentFailure(DocumentFailureCode.COMPOSITION_RELAYOUT_UNSAFE, PdfBoxTableLayout.CAPABILITY_ID,
                    "The paragraph flow is not available for safe relayout.");
        }
        requireComplete();
    }

    void requireComplete() throws DocumentFailure {
        if (stage == LargeTableState.Stage.OPEN) {
            throw PdfBoxParagraphOperations.compositionFailure(PdfBoxParagraphOperations.invalid(), PdfBoxTableLayout.CAPABILITY_ID);
        }
    }

    void execute(DocumentCommand command) throws DocumentFailure {
        try {
            if (command instanceof BeginLargeTable) { begin((BeginLargeTable) command); }
            else {
                if (stage != LargeTableState.Stage.OPEN) { throw PdfBoxParagraphOperations.invalid(); }
                if (command instanceof AppendTableRows) { append(((AppendTableRows) command).getRows()); }
                else { emit(command instanceof CompleteTable); }
            }
        } catch (DocumentFailure failure) {
            throw PdfBoxParagraphOperations.compositionFailure(failure, PdfBoxTableLayout.CAPABILITY_ID);
        }
    }

    static long validateBegin(BeginLargeTable command, WorkflowResourceContext resources) throws DocumentFailure {
        ParagraphFlow flow = command.getFlow();
        if (command.getMaximumRetainedRows() < 1 || flow.getVersion() != ParagraphFlow.VERSION_4
                || flow.getItems().size() != 1 || flow.getItems().get(0).getKind() != ParagraphFlow.Item.Kind.TABLE) {
            throw PdfBoxParagraphOperations.invalid();
        }
        Table table = flow.getItems().get(0).getTable();
        if (table.getVersion() != Table.VERSION_2 || table.getLayout() != Table.Layout.FIXED
                || !table.getRows().isEmpty() || table.getWidth().getKind() == TableWidth.Kind.AUTO) {
            throw PdfBoxParagraphOperations.invalid();
        }
        return PdfBoxParagraphOperations.validateLargeDeclarations(flow, command.getLimits(), resources);
    }

    private void begin(BeginLargeTable command) throws DocumentFailure {
        if (stage == LargeTableState.Stage.OPEN) { throw PdfBoxParagraphOperations.invalid(); }
        long bytes = validateBegin(command, resources);
        pages.requireAppendPreservable();
        WorkflowResourceContext.MemoryReservation memory = resources.reserveOwnedMemory(bytes);
        WorkflowResourceContext.OwnedMemoryScope frozenMemory = resources.ownedMemoryScope();
        try {
            PdfBoxTableLayout.Declarations initialTables = new PdfBoxTableLayout.Declarations();
            Table prototype = command.getFlow().getItems().get(0).getTable();
            initialTables.accept(prototype, command.getLimits().getTableLimits(), resources, true);
            PdfBoxParagraphOperations.DeclarationCounts initialText =
                    new PdfBoxParagraphOperations.DeclarationCounts(command.getLimits(), resources);
            for (List<TableRow> group : PdfBoxTableLayout.rowGroups(prototype)) { countText(group, initialText); }
            FontSelection frozen = fonts.freezeLayoutSelection(command.getFlow().getFonts(),
                    command.getLimits().getFontLimits(), frozenMemory);
            List<PdfBoxParagraphOperations.Area> declaredAreas = new ArrayList<PdfBoxParagraphOperations.Area>();
            int index = 0;
            for (LayoutPage page : command.getFlow().getPages()) {
                PageMargins m = page.getMargins();
                if (page.getAreas().isEmpty()) {
                    declaredAreas.add(new PdfBoxParagraphOperations.Area(index, CanvasRectangle.of(m.getLeft(), m.getBottom(),
                            page.getWidth() - m.getRight(), page.getHeight() - m.getTop())));
                } else {
                    for (CanvasRectangle box : page.getAreas()) {
                        declaredAreas.add(new PdfBoxParagraphOperations.Area(index, CanvasRectangle.of(
                                m.getLeft() + box.getLowerLeftX(), m.getBottom() + box.getLowerLeftY(),
                                m.getLeft() + box.getUpperRightX(), m.getBottom() + box.getUpperRightY())));
                    }
                }
                index++;
            }
            close();
            declaration = command; areas = declaredAreas; baseMemory = memory; memory = null;
            selection = frozen; fontMemory = frozenMemory; frozenMemory = null;
            tables = new PdfBoxTableLayout(paragraphs, resources, command.getLimits().getTableLimits());
            tableCounts = initialTables; textCounts = initialText;
            acceptedRows = 0; nextArea = 0; attempts = 0; lines = 0; contentBytes = 0; textBytes = 0;
            fallbackChecks[0] = 0;
            stage = LargeTableState.Stage.OPEN;
        } finally {
            if (memory != null) { memory.close(); }
            if (frozenMemory != null) { frozenMemory.close(); }
        }
    }

    private void append(List<TableRow> rows) throws DocumentFailure {
        CompositionLimits limits = declaration.getLimits();
        Table prototype = declaration.getFlow().getItems().get(0).getTable();
        if (rows.isEmpty()) { throw PdfBoxParagraphOperations.invalid(); }
        if ((long) retained.size() + rows.size() > declaration.getMaximumRetainedRows()
                || (long) acceptedRows + rows.size() + prototype.getHeaderRows().size() + prototype.getFooterRows().size()
                    > limits.getTableLimits().getMaximumRows()) { throw PdfBoxParagraphOperations.limitFailure(); }
        PdfBoxTableLayout.Declarations nextTables = new PdfBoxTableLayout.Declarations(tableCounts);
        nextTables.countAdditionalRows(rows, prototype.getColumns().size(), limits.getTableLimits(), resources);
        PdfBoxParagraphOperations.DeclarationCounts nextText = new PdfBoxParagraphOperations.DeclarationCounts(textCounts);
        countText(rows, nextText);
        WorkflowResourceContext.MemoryReservation memory = resources.reserveOwnedMemory(
                PdfBoxParagraphOperations.addDeclarationBytes(rowBytes(retained), rowBytes(rows), resources));
        try {
            List<TableRow> replacement = new ArrayList<TableRow>(retained);
            replacement.addAll(rows);
            PdfBoxParagraphOperations.validateLargeDeclarations(flow(table(replacement, false)), limits, resources,
                    declaration.getMaximumRetainedRows());
            if (rowMemory != null) { rowMemory.close(); }
            rowMemory = memory; memory = null; retained = replacement; acceptedRows += rows.size();
            tableCounts = nextTables; textCounts = nextText;
        } finally { if (memory != null) { memory.close(); } }
    }

    private void emit(boolean complete) throws DocumentFailure {
        if (retained.isEmpty()) { if (complete) { throw PdfBoxParagraphOperations.invalid(); } return; }
        CompositionLimits limits = declaration.getLimits();
        int ready = completePrefix();
        if (complete && ready != retained.size()) { throw PdfBoxTableLayout.span(); }
        if (ready == 0) { return; }
        try (WorkflowResourceContext.OwnedMemoryScope memory = resources.ownedMemoryScope()) {
            memory.retain(rowBytes(retained));
            Table table = table(retained.subList(0, ready), complete);
            StringBuilder text = new StringBuilder();
            for (List<TableRow> group : PdfBoxTableLayout.rowGroups(table)) {
                for (TableRow row : group) {
                    for (TableCell cell : row.getCells()) {
                        for (Paragraph paragraph : cell.getParagraphs()) { paragraphs.appendText(text, paragraph); }
                    }
                }
            }
            try (PdfBoxPositionedTextOperations.PreparedText prepared = fonts.prepareLayoutText(text.toString(),
                    selection, limits.getFontLimits(), fallbackChecks)) {
                PdfBoxTableLayout.Content content = tables.prepare(table, prepared, new int[] {0});
                List<PdfBoxTableLayout.Plan> plans = new ArrayList<PdfBoxTableLayout.Plan>();
                int row = 0;
                PdfBoxTableLayout.Cursor cursor = null;
                int reached = nextArea;
                while (row < ready && reached < areas.size()) {
                    if (attempts >= limits.getMaximumLayoutAttempts()) { throw PdfBoxParagraphOperations.limitFailure(); }
                    attempts++;
                    PdfBoxTableLayout.Plan plan = tables.layout(content, areas.get(reached++), 0, row, cursor,
                            Double.MAX_VALUE, limits.getMaximumLines(), memory);
                    if (plan == null) {
                        if (tables.lineLimitReached) { throw PdfBoxParagraphOperations.limitFailure(); }
                        continue;
                    }
                    plans.add(plan); row = plan.nextRow; cursor = plan.continuation;
                }
                if (complete && row < ready) { throw PdfBoxTableLayout.unsatisfied(); }
                int count = complete ? plans.size() : 0;
                if (!complete) {
                    for (int i = 0; i + 1 < plans.size(); i++) {
                        if (content.releaseBoundary(plans.get(i))) { count = i + 1; }
                    }
                }
                if (count == 0) { return; }
                List<PdfBoxTableLayout.Plan> decided = new ArrayList<PdfBoxTableLayout.Plan>(plans.subList(0, count));
                List<PdfBoxParagraphOperations.Line> output = new ArrayList<PdfBoxParagraphOperations.Line>();
                for (PdfBoxTableLayout.Plan plan : decided) {
                    plan.lineOffset = output.size(); output.addAll(plan.lines);
                }
                if (output.size() > limits.getMaximumLines() - lines) { throw PdfBoxParagraphOperations.limitFailure(); }
                PdfBoxTableLayout.Plan last = decided.get(decided.size() - 1);
                List<TableRow> remainder = new ArrayList<TableRow>(retained.subList(last.nextRow, retained.size()));
                WorkflowResourceContext.MemoryReservation replacementMemory = resources.reserveOwnedMemory(rowBytes(remainder));
                try {
                    memory.retain(16L * (last.area.page + 1));
                    List<PDPage> detached = new ArrayList<PDPage>(emittedPages);
                    int previousCount = emittedPages.size();
                    PDPage previous = previousCount == 0 ? null : emittedPages.get(previousCount - 1);
                    PDPage painted = previous;
                    if (previous != null && decided.get(0).area.page < previousCount) {
                        painted = copyPage(previous, memory); detached.set(previousCount - 1, painted);
                    }
                    for (int i = previousCount; i <= last.area.page; i++) {
                        detached.add(paragraphs.createPage(declaration.getFlow().getPages().get(i)));
                    }
                    long[] usage = paragraphs.paint(output, decided, prepared, detached, limits, contentBytes, textBytes);
                    fonts.finalizeFonts();
                    pages.appendLargeTablePages(previous, painted, detached.subList(previousCount, detached.size()));
                    if (previous != null) { detached.set(previousCount - 1, previous); }
                    emittedPages = detached;
                    lines += output.size(); contentBytes = usage[0]; textBytes = usage[1];
                    while (areas.get(nextArea) != last.area) { nextArea++; }
                    nextArea++;
                    retained = remainder;
                    if (rowMemory != null) { rowMemory.close(); }
                    rowMemory = replacementMemory; replacementMemory = null;
                    if (complete) { stage = LargeTableState.Stage.COMPLETE; close(); }
                } finally { if (replacementMemory != null) { replacementMemory.close(); } }
            }
        }
    }

    private static PDPage copyPage(PDPage page, WorkflowResourceContext.OwnedMemoryScope memory) throws DocumentFailure {
        COSBase contents = page.getCOSObject().getItem(COSName.CONTENTS);
        memory.retain(1024L + (contents instanceof COSArray ? 8L * ((COSArray) contents).size() : 0));
        COSDictionary dictionary = new COSDictionary(page.getCOSObject());
        COSBase original = dictionary.getItem(COSName.CONTENTS);
        if (original instanceof COSArray) {
            COSArray copy = new COSArray(); copy.setDirect(true); copy.addAll((COSArray) original);
            dictionary.setItem(COSName.CONTENTS, copy);
        }
        return new PDPage(dictionary);
    }

    private int completePrefix() throws DocumentFailure {
        int ready = 0;
        int end = 0;
        for (int row = 0; row < retained.size(); row++) {
            resources.checkpoint();
            for (TableCell cell : retained.get(row).getCells()) { end = Math.max(end, row + cell.getRowspan()); }
            if (end == row + 1) { ready = end; }
        }
        return ready;
    }

    private Table table(List<TableRow> body, boolean complete) {
        Table original = declaration.getFlow().getItems().get(0).getTable();
        Table.Builder builder = Table.version2(Table.Layout.FIXED, original.getWidth(),
                original.getColumns().toArray(new TableWidth[original.getColumns().size()]))
                .keepTogether(original.isKeepTogether()).keepWithNext(original.isKeepWithNext())
                .skipFirstHeader(nextArea == 0 && original.isSkipFirstHeader())
                .skipLastFooter(complete && original.isSkipLastFooter()).splitRows(original.isSplitRows()).overflow(original.getOverflow());
        for (TableRow row : original.getHeaderRows()) { builder.header(row); }
        for (TableRow row : original.getFooterRows()) { builder.footer(row); }
        for (TableRow row : body) { builder.row(row); }
        return builder.build();
    }

    private ParagraphFlow flow(Table table) {
        ParagraphFlow.Builder builder = ParagraphFlow.version4(declaration.getFlow().getFonts());
        for (LayoutPage page : declaration.getFlow().getPages()) { builder.page(page); }
        return builder.table(table).build();
    }

    private long rowBytes(List<TableRow> rows) throws DocumentFailure {
        long bytes = 32L * rows.size();
        for (TableRow row : rows) {
            resources.checkpoint(); bytes += 512L * row.getCells().size();
            for (TableCell cell : row.getCells()) {
                for (Paragraph paragraph : cell.getParagraphs()) {
                    bytes += 128L + 512L * paragraph.getInlines().size();
                    for (Paragraph.Inline inline : paragraph.getInlines()) {
                        resources.checkpoint();
                        if (inline.getKind() == Paragraph.Inline.Kind.TEXT) { bytes += 516L * inline.getText().length(); }
                        else {
                            bytes = PdfBoxParagraphOperations.addDeclarationBytes(bytes,
                                    PdfBoxParagraphOperations.graphicBytes(inline.getGraphic(), declaration.getLimits(), resources, 1),
                                    resources);
                        }
                    }
                }
            }
        }
        return bytes;
    }

    private void countText(List<TableRow> rows, PdfBoxParagraphOperations.DeclarationCounts counts) throws DocumentFailure {
        for (TableRow row : rows) {
            resources.checkpoint();
            for (TableCell cell : row.getCells()) {
                for (Paragraph paragraph : cell.getParagraphs()) { counts.accept(paragraph, Paragraph.VERSION_1); }
            }
        }
    }

    LargeTableState state() { return LargeTableState.version1(stage, acceptedRows, retained.size()); }

    void close() {
        declaration = null; tables = null; tableCounts = null; textCounts = null;
        retained = Collections.emptyList(); areas = Collections.emptyList(); emittedPages = Collections.emptyList();
        if (baseMemory != null) { baseMemory.close(); baseMemory = null; }
        if (rowMemory != null) { rowMemory.close(); rowMemory = null; }
        if (fontMemory != null) { fontMemory.close(); fontMemory = null; }
        selection = null;
    }
}
