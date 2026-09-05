package net.zerocloud.pdf;

import java.util.ArrayList;
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

        void accept(Table table, TableLimits limits, WorkflowResourceContext resources) throws DocumentFailure {
            int width = table.getColumns().size();
            int height = table.getRows().size();
            if (++tables > limits.getMaximumTables() || width > limits.getMaximumColumns()
                    || (rows += height) > limits.getMaximumRows()
                    || (slots += (long) width * height) > limits.getMaximumGridSlots()) { throw limit(); }
            columns += width;
            if (width == 0 || height == 0) { throw invalid(); }
            validateWidth(table.getWidth(), false, false);
            for (TableWidth column : table.getColumns()) { resources.checkpoint(); validateWidth(column, true, false); }
            // Row endpoints encode occupancy without allocating rows times columns.
            try (WorkflowResourceContext.MemoryReservation memory = resources.reserveOwnedMemory(4L * width)) {
                int[] until = new int[width];
                for (int row = 0; row < height; row++) {
                    resources.checkpoint();
                    TableRow declaration = table.getRows().get(row);
                    if (!nonnegative(declaration.getMinimumHeight())) { throw invalid(); }
                    if ((cells += declaration.getCells().size()) > limits.getMaximumCells()) { throw limit(); }
                    int column = 0;
                    for (TableCell cell : declaration.getCells()) {
                        resources.checkpoint();
                        while (column < width && until[column] > row) { resources.checkpoint(); column++; }
                        int rs = cell.getRowspan();
                        int cs = cell.getColspan();
                        if (rs < 1 || cs < 1 || rs > height - row || cs > width - column) { throw span(); }
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

        long modeledBytes() { return 512L * cells + 32L * (rows + columns) + 128L * tables; }
    }

    Content prepare(Table table, PdfBoxPositionedTextOperations.PreparedText prepared, int[] glyph) throws DocumentFailure {
        List<Cell> cells = new ArrayList<Cell>();
        int[] until = new int[table.getColumns().size()];
        for (int row = 0; row < table.getRows().size(); row++) {
            int column = 0;
            for (TableCell declaration : table.getRows().get(row).getCells()) {
                resources.checkpoint();
                while (until[column] > row) { resources.checkpoint(); column++; }
                Cell cell = new Cell(declaration, row, column);
                for (int c = column; c < column + declaration.getColspan(); c++) { until[c] = row + declaration.getRowspan(); }
                column += declaration.getColspan();
                for (Paragraph paragraph : declaration.getParagraphs()) {
                    List<Atom> atoms = paragraphs.atoms(paragraph, prepared, glyph);
                    cell.paragraphs.add(new Text(paragraph, atoms));
                    double line = 0;
                    double correction = 0;
                    for (Atom atom : atoms) {
                        resources.checkpoint();
                        cell.minimum = Math.max(cell.minimum, atom.width);
                        if (atom.codePoint == '\n') {
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
        return new Content(table, cells);
    }

    Plan layout(Content content, Area area, double used, int remainingLines,
            WorkflowResourceContext.OwnedMemoryScope memory) throws DocumentFailure {
        lineLimitReached = false;
        Table table = content.table;
        int count = table.getColumns().size();
        // Retained per-candidate widths, positions, row heights, cell boxes and line lists.
        memory.retain(64L * (count + table.getRows().size()) + 512L * content.cells.size());
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
                double required = required(cell, width);
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
                double desired = Math.max(required(cell, width), cell.preferred + horizontal(cell.declaration));
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
        double[] heights = new double[table.getRows().size()];
        for (int r = 0; r < heights.length; r++) { tick(1); heights[r] = table.getRows().get(r).getMinimumHeight(); }
        List<CellPlan> cellPlans = new ArrayList<CellPlan>();
        int lineCount = 0;
        for (Cell cell : content.cells) {
            tick(1);
            double cellWidth = x[cell.end()] - x[cell.column];
            if (exceeds(required(cell, width), cellWidth)) { return null; }
            double inner = cellWidth - horizontal(cell.declaration);
            if (!positive(inner)) { return null; }
            CellPlan plan = new CellPlan(cell);
            double height = 0;
            double correction = 0;
            for (Text text : cell.paragraphs) {
                double available = text.paragraph.getMaximumWidth() > 0
                        ? Math.min(inner, text.paragraph.getMaximumWidth()) : inner;
                int first = 0;
                while (first < text.atoms.size()) {
                    // One candidate line plus its remaining scalar/graphic suffix bounds wrapping work.
                    tick(1L + text.atoms.size() - first);
                    if (lineCount >= remainingLines) { lineLimitReached = true; return null; }
                    memory.retain(256L + 8L * (text.atoms.size() - first));
                    Line line = paragraphs.line(text.atoms, first, text.paragraph, area, available);
                    if (line == null) { return null; }
                    line.left = x[cell.column] + cell.declaration.getPadding().getLeft()
                            + cell.declaration.getBorders().getLeft();
                    line.baseline = -height - line.ascent;
                    plan.lines.add(line); lineCount++; first = line.next;
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
            int end = cell.row + cell.declaration.getRowspan();
            double deficit = plan.requiredHeight - sum(heights, cell.row, end);
            if (deficit > 0) {
                for (int r = cell.row; r < end; r++) { tick(1); heights[r] += deficit / cell.declaration.getRowspan(); }
            }
        }
        double totalHeight = sum(heights, 0, heights.length);
        if (!positive(totalHeight) || exceeds(used + totalHeight, area.box.getUpperRightY() - area.box.getLowerLeftY())) {
            return null;
        }
        double[] y = new double[heights.length + 1];
        y[0] = area.box.getUpperRightY() - used;
        for (int r = 0; r < heights.length; r++) { tick(1); y[r + 1] = y[r] - heights[r]; }
        Plan result = new Plan(area, totalHeight);
        for (CellPlan plan : cellPlans) {
            tick(1);
            Cell cell = plan.cell;
            TableBorders b = cell.declaration.getBorders();
            double left = x[cell.column], right = x[cell.end()];
            double top = y[cell.row], bottom = y[cell.row + cell.declaration.getRowspan()];
            result.border(left, top - b.getTop(), right, top);
            result.border(right - b.getRight(), bottom, right, top);
            result.border(left, bottom, right, bottom + b.getBottom());
            result.border(left, bottom, left + b.getLeft(), top);
            for (Line line : plan.lines) {
                tick(1);
                line.baseline += top - cell.declaration.getPadding().getTop() - b.getTop();
                result.lines.add(line);
            }
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
    private static double required(Cell cell, double width) {
        return Math.max(cell.minimum + horizontal(cell.declaration), resolve(cell.declaration.getMinimumWidth(), width));
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
    private static DocumentFailure span() {
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
        Content(Table table, List<Cell> cells) {
            this.table = table; this.cells = cells; byEnd = new ArrayList<Cell>(cells);
            Collections.sort(byEnd, new Comparator<Cell>() {
                @Override public int compare(Cell a, Cell b) {
                    int end = Integer.compare(a.end(), b.end());
                    return end != 0 ? end : Integer.compare(b.column, a.column);
                }
            });
        }
    }
    static final class Plan {
        final Area area;
        final double height;
        final List<Line> lines = new ArrayList<Line>();
        final List<CanvasRectangle> borders = new ArrayList<CanvasRectangle>();
        int lineOffset;
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
        Text(Paragraph paragraph, List<Atom> atoms) { this.paragraph = paragraph; this.atoms = atoms; }
    }
    private static final class CellPlan {
        final Cell cell;
        final List<Line> lines = new ArrayList<Line>();
        double requiredHeight;
        CellPlan(Cell cell) { this.cell = cell; }
    }
}
