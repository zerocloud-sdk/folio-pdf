package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.zerocloud.pdf.composition.CellPadding;
import net.zerocloud.pdf.composition.CanvasRectangle;
import net.zerocloud.pdf.composition.Paragraph;
import net.zerocloud.pdf.composition.Table;
import net.zerocloud.pdf.composition.TableBorders;
import net.zerocloud.pdf.composition.TableCell;
import net.zerocloud.pdf.composition.TableLimits;
import net.zerocloud.pdf.composition.TableRow;
import net.zerocloud.pdf.composition.TableWidth;
import net.zerocloud.pdf.PdfBoxParagraphOperations.Area;
import net.zerocloud.pdf.PdfBoxParagraphOperations.Atom;
import net.zerocloud.pdf.PdfBoxParagraphOperations.Line;

/** Project-owned interval width solving and cell geometry; painting stays in Composition. */
final class PdfBoxTableLayout {
    static final String CAPABILITY_ID = "composition.layout.tables";
    private final PdfBoxParagraphOperations paragraphs;
    private final WorkflowResourceContext resources;
    private final TableLimits limits;
    private long work;
    boolean lineLimitReached;

    PdfBoxTableLayout(PdfBoxParagraphOperations paragraphs, WorkflowResourceContext resources, TableLimits limits) {
        this.paragraphs = paragraphs; this.resources = resources; this.limits = limits;
    }

    /** Aggregate declaration admission, before any font source or layout allocation. */
    static final class Declarations {
        private long tables;
        private long rows;
        private long cells;
        private long slots;
        private long columns;

        Declarations() { }
        Declarations(Declarations original) {
            tables = original.tables; rows = original.rows; cells = original.cells;
            slots = original.slots; columns = original.columns;
        }

        void countAdditionalRows(List<TableRow> additional, int width, TableLimits limits,
                WorkflowResourceContext resources) throws DocumentFailure {
            if ((rows += additional.size()) > limits.getMaximumRows()
                    || (slots += (long) width * additional.size()) > limits.getMaximumGridSlots()) { throw limit(); }
            for (TableRow row : additional) {
                resources.checkpoint();
                if ((cells += row.getCells().size()) > limits.getMaximumCells()) { throw limit(); }
            }
        }

        void accept(Table table, TableLimits limits, WorkflowResourceContext resources) throws DocumentFailure {
            accept(table, limits, resources, false);
        }

        void accept(Table table, TableLimits limits, WorkflowResourceContext resources, boolean allowEmptyBody)
                throws DocumentFailure {
            accept(table, limits, resources, allowEmptyBody, 0);
        }

        void accept(Table table, TableLimits limits, WorkflowResourceContext resources, boolean allowEmptyBody,
                int maximumOpenRows) throws DocumentFailure {
            int width = table.getColumns().size();
            long height = (long) table.getHeaderRows().size() + table.getRows().size() + table.getFooterRows().size();
            if (++tables > limits.getMaximumTables() || width > limits.getMaximumColumns()
                    || (rows += height) > limits.getMaximumRows()
                    || (slots += (long) width * height) > limits.getMaximumGridSlots()) { throw limit(); }
            columns += width;
            if (width == 0 || (!allowEmptyBody && table.getRows().isEmpty())) { throw invalid(); }
            if (table.getVersion() == Table.VERSION_1 && (table.isKeepTogether() || table.isKeepWithNext()
                    || !table.getHeaderRows().isEmpty() || !table.getFooterRows().isEmpty()
                    || table.isSkipFirstHeader() || table.isSkipLastFooter()
                    || table.getOverflow() != Paragraph.Overflow.WRAP || !table.isSplitRows())) { throw invalid(); }
            validateWidth(table.getWidth(), false, false);
            for (TableWidth column : table.getColumns()) { resources.checkpoint(); validateWidth(column, true, false); }
            // Row endpoints encode occupancy without allocating rows times columns.
            try (WorkflowResourceContext.MemoryReservation memory = resources.reserveOwnedMemory(4L * width)) {
                int[] until = new int[width];
                for (List<TableRow> group : rowGroups(table)) {
                    Arrays.fill(until, 0);
                    for (int row = 0; row < group.size(); row++) {
                    resources.checkpoint();
                    TableRow declaration = group.get(row);
                    if (!nonnegative(declaration.getMinimumHeight())) { throw invalid(); }
                    if ((cells += declaration.getCells().size()) > limits.getMaximumCells()) { throw limit(); }
                    int column = 0;
                    for (TableCell cell : declaration.getCells()) {
                        resources.checkpoint();
                        while (column < width && until[column] > row) { resources.checkpoint(); column++; }
                        int rs = cell.getRowspan();
                        int cs = cell.getColspan();
                        if (rs < 1 || cs < 1 || cs > width - column) { throw span(); }
                        if (group == table.getRows() && maximumOpenRows > 0) {
                            if (rs > maximumOpenRows - row) { throw limit(); }
                        } else if (rs > group.size() - row) { throw span(); }
                        for (int c = column; c < column + cs; c++) {
                            resources.checkpoint();
                            if (until[c] > row) { throw span(); }
                            until[c] = row + rs;
                        }
                        column += cs;
                        CellPadding p = cell.getPadding();
                        TableBorders b = cell.getBorders();
                        if (!nonnegative(p.getTop()) || !nonnegative(p.getRight()) || !nonnegative(p.getBottom())
                                || !nonnegative(p.getLeft()) || !nonnegative(b.getTop()) || !nonnegative(b.getRight())
                                || !nonnegative(b.getBottom()) || !nonnegative(b.getLeft())) { throw invalid(); }
                        validateWidth(cell.getMinimumWidth(), false, true);
                    }
                    for (int c = 0; c < width; c++) {
                        resources.checkpoint(); if (until[c] <= row) { throw span(); }
                    }
                    }
                }
            }
        }

        long modeledBytes() { return 512L * cells + 32L * (rows + columns) + 128L * tables; }
    }

    static List<List<TableRow>> rowGroups(Table table) {
        return Arrays.asList(table.getHeaderRows(), table.getRows(), table.getFooterRows());
    }

    Content prepare(Table table, PdfBoxPositionedTextOperations.PreparedText prepared, int[] glyph) throws DocumentFailure {
        List<Cell> cells = new ArrayList<Cell>();
        int textCount = 0;
        List<TableRow> rows = new ArrayList<TableRow>();
        for (List<TableRow> group : rowGroups(table)) { rows.addAll(group); }
        int[] until = new int[table.getColumns().size()];
        for (int row = 0; row < rows.size(); row++) {
            int column = 0;
            for (TableCell declaration : rows.get(row).getCells()) {
                resources.checkpoint();
                while (until[column] > row) { resources.checkpoint(); column++; }
                Cell cell = new Cell(declaration, row, column);
                for (int c = column; c < column + declaration.getColspan(); c++) { until[c] = row + declaration.getRowspan(); }
                column += declaration.getColspan();
                for (Paragraph paragraph : declaration.getParagraphs()) {
                    List<Atom> atoms = paragraphs.atoms(paragraph, prepared, glyph);
                    cell.paragraphs.add(new Text(paragraph, atoms, textCount++));
                    double line = 0;
                    double correction = 0;
                    double cluster = 0;
                    for (Atom atom : atoms) {
                        resources.checkpoint();
                        cluster += atom.width;
                        if (atom.clusterEnd) { cell.minimum = Math.max(cell.minimum, cluster); cluster = 0; }
                        if (PdfBoxParagraphOperations.hardBreak(atom.codePoint)) {
                            cell.preferred = Math.max(cell.preferred, line); line = 0; correction = 0;
                        } else {
                            double increment = atom.width - correction;
                            double next = line + increment;
                            correction = (next - line) - increment; line = next;
                        }
                    }
                    cell.preferred = Math.max(cell.preferred, line);
                }
                cells.add(cell);
            }
        }
        return new Content(table, rows, cells, textCount);
    }

    Plan layout(Content content, Area area, double used, int firstRow, Cursor cursor, double maximumHeight, int remainingLines,
            WorkflowResourceContext.OwnedMemoryScope memory) throws DocumentFailure {
        lineLimitReached = false;
        Table table = content.table;
        boolean paginated = table.getVersion() == Table.VERSION_2;
        boolean header = !(table.isSkipFirstHeader() && firstRow == 0 && cursor == null);
        firstRow += content.bodyStart;
        int[] offsets = null;
        if (paginated) {
            memory.retain(32L + 4L * content.textCount);
            offsets = cursor == null ? new int[content.textCount] : cursor.offsets.clone();
        }
        int count = table.getColumns().size();
        // Retained per-candidate widths, positions, row heights, cell boxes and line lists.
        memory.retain(64L * (count + content.rows.size()) + 512L * content.cells.size());
        tick(1); // candidate
        double width = resolve(table.getWidth(), area.box.getUpperRightX() - area.box.getLowerLeftX());
        if (!positive(width) || exceeds(width, area.box.getUpperRightX() - area.box.getLowerLeftX())) { return null; }
        double[] minimum = new double[count];
        boolean[] flexible = new boolean[count];
        int auto = 0;
        for (int c = 0; c < count; c++) {
            tick(1);
            flexible[c] = table.getColumns().get(c).getKind() == TableWidth.Kind.AUTO;
            if (flexible[c]) { auto++; } else { minimum[c] = resolve(table.getColumns().get(c), width); }
        }
        if (table.getLayout() == Table.Layout.FIXED) {
            double rest = width - sum(minimum, 0, count);
            if (rest < -0.000000001 || (auto == 0 && Math.abs(rest) > 0.000000001)) { return null; }
            for (int c = 0; c < count; c++) { tick(1); if (flexible[c]) { minimum[c] = rest / auto; } }
        } else {
            for (Cell cell : content.byEnd) {
                tick(1);
                double required = required(cell, width, table);
                double deficit = required - sum(minimum, cell.column, cell.end());
                if (deficit > 0.000000001) {
                    int target = -1;
                    for (int c = cell.column; c < cell.end(); c++) { tick(1); if (flexible[c]) { target = c; } }
                    if (target < 0) { return null; }
                    minimum[target] += deficit;
                }
            }
            double totalMin = sum(minimum, 0, count);
            if (exceeds(totalMin, width)) { return null; }
            double[] preferred = minimum.clone();
            for (Cell cell : content.byEnd) {
                tick(1);
                double desired = Math.max(required(cell, width, table), cell.preferred + horizontal(cell.declaration));
                double deficit = desired - sum(preferred, cell.column, cell.end());
                int eligible = 0;
                for (int c = cell.column; c < cell.end(); c++) { tick(1); if (flexible[c]) { eligible++; } }
                if (deficit > 0 && eligible > 0) {
                    for (int c = cell.column; c < cell.end(); c++) {
                        tick(1); if (flexible[c]) { preferred[c] += deficit / eligible; }
                    }
                }
            }
            double totalPreferred = sum(preferred, 0, count);
            if (auto == 0 && Math.abs(totalMin - width) > 0.000000001) { return null; }
            double fraction = totalPreferred > width && totalPreferred > totalMin
                    ? Math.max(0, (width - totalMin) / (totalPreferred - totalMin)) : 1;
            double extra = Math.max(0, width - totalPreferred);
            for (int c = 0; c < count; c++) {
                tick(1);
                minimum[c] += fraction * (preferred[c] - minimum[c]);
                if (flexible[c]) { minimum[c] += extra / auto; }
            }
        }
        double[] x = new double[count + 1];
        x[0] = area.box.getLowerLeftX();
        for (int c = 0; c < count; c++) {
            tick(1); if (!positive(minimum[c])) { return null; } x[c + 1] = x[c] + minimum[c];
        }
        double[] heights = new double[content.rows.size()];
        for (int r = 0; r < heights.length; r++) { tick(1); heights[r] = content.rows.get(r).getMinimumHeight(); }
        if (cursor != null) { heights[firstRow] = cursor.minimumHeight; }
        List<CellPlan> cellPlans = new ArrayList<CellPlan>();
        int lineCount = 0;
        for (Cell cell : content.cells) {
            tick(1);
            if (content.body(cell) && cell.row + cell.declaration.getRowspan() <= firstRow) { continue; }
            double cellWidth = x[cell.end()] - x[cell.column];
            if (exceeds(required(cell, width, table), cellWidth)) { return null; }
            double inner = cellWidth - horizontal(cell.declaration);
            if (!positive(inner)) { return null; }
            CellPlan plan = new CellPlan(cell);
            double height = 0;
            double correction = 0;
            for (Text text : cell.paragraphs) {
                double available = text.paragraph.getMaximumWidth() > 0
                        ? Math.min(inner, text.paragraph.getMaximumWidth()) : inner;
                int first = cursor == null || !content.body(cell) ? 0 : cursor.offsets[text.index];
                while (first < text.atoms.size()) {
                    // One candidate line plus its remaining scalar/graphic suffix bounds wrapping work.
                    tick(1L + text.atoms.size() - first);
                    if (!paginated && lineCount >= remainingLines) { lineLimitReached = true; return null; }
                    memory.retain(256L + 8L * (text.atoms.size() - first));
                    Line line = paragraphs.line(text.atoms, first, text.paragraph, area, available, table.getOverflow());
                    if (line == null) { return null; }
                    line.left = x[cell.column] + cell.declaration.getPadding().getLeft()
                            + cell.declaration.getBorders().getLeft();
                    line.baseline = -height - line.ascent;
                    plan.lines.add(new CellLine(line, text.index)); lineCount++; first = line.next;
                    double increment = line.height - correction;
                    double next = height + increment;
                    correction = (next - height) - increment; height = next;
                }
            }
            plan.requiredHeight = height + vertical(cell.declaration);
            if (cell.declaration.getRowspan() == 1) { heights[cell.row] = Math.max(heights[cell.row], plan.requiredHeight); }
            cellPlans.add(plan);
        }
        List<CellPlan> bySpan = new ArrayList<CellPlan>(cellPlans);
        Collections.sort(bySpan, new Comparator<CellPlan>() {
            @Override public int compare(CellPlan a, CellPlan b) {
                return Integer.compare(a.cell.declaration.getRowspan(), b.cell.declaration.getRowspan());
            }
        });
        for (CellPlan plan : bySpan) {
            tick(1);
            Cell cell = plan.cell;
            if (cell.declaration.getRowspan() == 1) { continue; }
            int start = content.body(cell) ? Math.max(firstRow, cell.row) : cell.row;
            int end = cell.row + cell.declaration.getRowspan();
            double deficit = plan.requiredHeight - sum(heights, start, end);
            if (deficit > 0) {
                for (int r = start; r < end; r++) { tick(1); heights[r] += deficit / (end - start); }
            }
        }
        int endRow = content.bodyEnd;
        double totalHeight = sum(heights, firstRow, endRow);
        double availableHeight = Math.min(maximumHeight, area.box.getUpperRightY() - area.box.getLowerLeftY() - used);
        double headerHeight = header ? sum(heights, 0, content.bodyStart) : 0;
        boolean footer = !(table.isSkipLastFooter() && !exceeds(headerHeight + totalHeight, availableHeight));
        double footerHeight = footer ? sum(heights, content.bodyEnd, heights.length) : 0;
        double repeatedHeight = headerHeight + footerHeight;
        availableHeight -= repeatedHeight;
        boolean splitRow = false;
        double splitMinimumRemaining = 0;
        if (paginated && exceeds(totalHeight, availableHeight)) {
            if (table.isKeepTogether()) { return null; }
            endRow = firstRow;
            double height = 0;
            while (endRow < content.bodyEnd && !exceeds(height + heights[endRow], availableHeight)) {
                tick(1); height += heights[endRow++];
            }
            totalHeight = sum(heights, firstRow, endRow);
            double tailSpace = availableHeight - totalHeight;
            if (table.isSplitRows() && endRow < content.bodyEnd && positive(tailSpace)) {
                double minimumHeight = cursor != null && endRow == firstRow
                        ? cursor.minimumHeight : content.rows.get(endRow).getMinimumHeight();
                double fragmentHeight = Math.min(minimumHeight, tailSpace);
                boolean progressed = fragmentHeight > 0;
                boolean splittable = true;
                for (CellPlan plan : cellPlans) {
                    Cell cell = plan.cell;
                    if (!content.body(cell) || cell.row > endRow || cell.row + cell.declaration.getRowspan() <= endRow) { continue; }
                    double covered = sum(heights, Math.max(cell.row, firstRow), endRow);
                    double innerHeight = covered + tailSpace - vertical(cell.declaration);
                    if (innerHeight < 0) { splittable = false; break; }
                    double consumedHeight = 0;
                    int emitted = 0;
                    for (CellLine line : plan.lines) {
                        tick(1);
                        if (exceeds(consumedHeight + line.line.height, innerHeight)) { break; }
                        consumedHeight += line.line.height; emitted++;
                    }
                    plan.emittedLines = emitted;
                    double needed = Math.max(0, consumedHeight + vertical(cell.declaration) - covered);
                    progressed |= emitted > 0 && needed > 0.000000001;
                    fragmentHeight = Math.max(fragmentHeight, needed);
                }
                if (splittable && progressed) {
                    totalHeight += fragmentHeight;
                    heights[endRow] = fragmentHeight;
                    endRow++;
                    splitRow = true;
                    splitMinimumRemaining = Math.max(0, minimumHeight - fragmentHeight);
                }
            }
        }
        if (endRow == firstRow || !positive(totalHeight) || exceeds(totalHeight, availableHeight)) {
            return null;
        }
        double[] y = new double[heights.length + 1];
        y[0] = area.box.getUpperRightY() - used;
        if (header) {
            for (int row = 0; row < content.bodyStart; row++) { tick(1); y[row + 1] = y[row] - heights[row]; }
        }
        y[firstRow] = area.box.getUpperRightY() - used - headerHeight;
        for (int r = firstRow; r < endRow; r++) { tick(1); y[r + 1] = y[r] - heights[r]; }
        if (footer) {
            y[content.bodyEnd] = area.box.getUpperRightY() - used - headerHeight - totalHeight;
            for (int row = content.bodyEnd; row < heights.length; row++) { tick(1); y[row + 1] = y[row] - heights[row]; }
        }
        Plan result = new Plan(area, totalHeight + repeatedHeight);
        result.nextRow = (splitRow ? endRow - 1 : endRow) - content.bodyStart;
        if (paginated && !table.isKeepTogether()) {
            for (int row = firstRow + 1; row < endRow; row++) {
                tick(1); result.previousHeight = Math.max(result.previousHeight, repeatedHeight + y[firstRow] - y[row]);
            }
        }
        for (CellPlan plan : cellPlans) {
            tick(1);
            Cell cell = plan.cell;
            boolean body = content.body(cell);
            if ((body && cell.row >= endRow) || (!header && cell.row < content.bodyStart)
                    || (!footer && cell.row >= content.bodyEnd)) { continue; }
            TableBorders b = cell.declaration.getBorders();
            double left = x[cell.column], right = x[cell.end()];
            double top = y[body ? Math.max(firstRow, cell.row) : cell.row];
            double bottom = y[body ? Math.min(endRow, cell.row + cell.declaration.getRowspan()) : cell.row + cell.declaration.getRowspan()];
            result.border(left, top - b.getTop(), right, top);
            result.border(right - b.getRight(), bottom, right, top);
            result.border(left, bottom, right, bottom + b.getBottom());
            result.border(left, bottom, left + b.getLeft(), top);
            double consumedHeight = 0;
            for (int index = 0; index < Math.min(plan.emittedLines, plan.lines.size()); index++) {
                tick(1);
                CellLine selected = plan.lines.get(index);
                Line line = selected.line;
                if (paginated && exceeds(consumedHeight + line.height + vertical(cell.declaration), top - bottom)) { break; }
                consumedHeight += line.height;
                double candidateHeight = repeatedHeight + y[firstRow] - top + consumedHeight + vertical(cell.declaration);
                if (paginated && body && !table.isKeepTogether() && candidateHeight < result.height - 0.000000001) {
                    result.previousHeight = Math.max(result.previousHeight, candidateHeight);
                }
                line.baseline += top - cell.declaration.getPadding().getTop() - b.getTop();
                if (result.lines.size() >= remainingLines) { lineLimitReached = true; return null; }
                result.lines.add(line);
                if (offsets != null && body) { offsets[selected.text] = line.next; }
            }
        }
        if (paginated && result.nextRow < table.getRows().size()) {
            double remainingMinimum = splitRow ? splitMinimumRemaining
                    : table.getRows().get(result.nextRow).getMinimumHeight();
            result.continuation = new Cursor(offsets, remainingMinimum);
        }
        return result;
    }

    private void tick(long count) throws DocumentFailure {
        resources.checkpoint();
        if (count > limits.getMaximumLayoutWork() - work) { throw limit(); }
        work += count;
    }

    private double sum(double[] values, int first, int end) throws DocumentFailure {
        double sum = 0, correction = 0;
        for (int i = first; i < end; i++) {
            tick(1); double increment = values[i] - correction;
            double next = sum + increment; correction = (next - sum) - increment; sum = next;
        }
        return sum;
    }
    private static double required(Cell cell, double width, Table table) {
        double intrinsic = table.getOverflow() == Paragraph.Overflow.VISIBLE ? 0 : cell.minimum;
        return Math.max(intrinsic + horizontal(cell.declaration), resolve(cell.declaration.getMinimumWidth(), width));
    }
    private static double horizontal(TableCell cell) {
        return cell.getPadding().getLeft() + cell.getPadding().getRight()
                + cell.getBorders().getLeft() + cell.getBorders().getRight();
    }
    private static double vertical(TableCell cell) {
        return cell.getPadding().getTop() + cell.getPadding().getBottom()
                + cell.getBorders().getTop() + cell.getBorders().getBottom();
    }
    private static double resolve(TableWidth width, double containing) {
        return width.getKind() == TableWidth.Kind.PERCENTAGE ? containing * width.getValue() / 100 : width.getValue();
    }
    private static void validateWidth(TableWidth width, boolean auto, boolean zero) throws DocumentFailure {
        if (width.getKind() == TableWidth.Kind.AUTO) { if (!auto) { throw invalid(); } return; }
        if (!(zero ? nonnegative(width.getValue()) : positive(width.getValue()))
                || (width.getKind() == TableWidth.Kind.PERCENTAGE && width.getValue() > 100)) { throw invalid(); }
    }
    private static boolean positive(double value) { return value > 0 && PdfBoxPageContentSupport.isValidNumber(value); }
    private static boolean nonnegative(double value) { return value >= 0 && PdfBoxPageContentSupport.isValidNumber(value); }
    private static boolean exceeds(double value, double bound) { return value > bound + 0.000000001; }
    private static DocumentFailure invalid() { return PdfBoxParagraphOperations.invalid(); }
    private static DocumentFailure limit() { return PdfBoxParagraphOperations.limitFailure(); }
    static DocumentFailure span() {
        return new DocumentFailure(DocumentFailureCode.TABLE_INVALID_SPAN, CAPABILITY_ID,
                "The table span grid is invalid.");
    }
    static DocumentFailure unsatisfied() {
        return new DocumentFailure(DocumentFailureCode.TABLE_CONSTRAINT_UNSATISFIED, CAPABILITY_ID,
                "The finite layout areas cannot satisfy the table geometry.");
    }

    static final class Content {
        final Table table;
        final List<Cell> cells;
        final List<Cell> byEnd;
        final int textCount;
        final List<TableRow> rows;
        final int bodyStart;
        final int bodyEnd;
        Content(Table table, List<TableRow> rows, List<Cell> cells, int textCount) {
            this.textCount = textCount;
            this.rows = rows;
            bodyStart = table.getHeaderRows().size();
            bodyEnd = bodyStart + table.getRows().size();
            this.table = table; this.cells = cells; byEnd = new ArrayList<Cell>(cells);
            Collections.sort(byEnd, new Comparator<Cell>() {
                @Override public int compare(Cell a, Cell b) {
                    int end = Integer.compare(a.end(), b.end());
                    return end != 0 ? end : Integer.compare(b.column, a.column);
                }
            });
        }
        boolean body(Cell cell) { return cell.row >= bodyStart && cell.row < bodyEnd; }
        boolean releaseBoundary(Plan plan) {
            int boundary = bodyStart + plan.nextRow;
            if (plan.continuation != null && plan.continuation.minimumHeight
                    < rows.get(boundary).getMinimumHeight() - 0.000000001) { return false; }
            for (Cell cell : cells) {
                if (!body(cell)) { continue; }
                if (cell.row < boundary && cell.row + cell.declaration.getRowspan() > boundary) { return false; }
                if (cell.row == boundary && plan.continuation != null) {
                    for (Text text : cell.paragraphs) {
                        if (plan.continuation.offsets[text.index] != 0) { return false; }
                    }
                }
            }
            return boundary > bodyStart;
        }
    }
    static final class Plan {
        final Area area;
        final double height;
        final List<Line> lines = new ArrayList<Line>();
        final List<CanvasRectangle> borders = new ArrayList<CanvasRectangle>();
        int lineOffset;
        int nextRow;
        Cursor continuation;
        double previousHeight;
        Plan(Area area, double height) { this.area = area; this.height = height; }
        void border(double left, double bottom, double right, double top) {
            if (right > left && top > bottom) { borders.add(CanvasRectangle.of(left, bottom, right, top)); }
        }
    }
    private static final class Cell {
        final TableCell declaration;
        final int row;
        final int column;
        final List<Text> paragraphs = new ArrayList<Text>();
        double minimum;
        double preferred;
        Cell(TableCell declaration, int row, int column) { this.declaration = declaration; this.row = row; this.column = column; }
        int end() { return column + declaration.getColspan(); }
    }
    private static final class Text {
        final Paragraph paragraph;
        final List<Atom> atoms;
        final int index;
        Text(Paragraph paragraph, List<Atom> atoms, int index) {
            this.paragraph = paragraph; this.atoms = atoms; this.index = index;
        }
    }
    private static final class CellPlan {
        final Cell cell;
        final List<CellLine> lines = new ArrayList<CellLine>();
        int emittedLines = Integer.MAX_VALUE;
        double requiredHeight;
        CellPlan(Cell cell) { this.cell = cell; }
    }
    private static final class CellLine {
        final Line line;
        final int text;
        CellLine(Line line, int text) { this.line = line; this.text = text; }
    }
    static final class Cursor {
        final int[] offsets;
        final double minimumHeight;
        Cursor(int[] offsets, double minimumHeight) { this.offsets = offsets; this.minimumHeight = minimumHeight; }
    }
}
