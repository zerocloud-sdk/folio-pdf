package net.zerocloud.pdf.acceptance;

import java.util.ArrayList;
import java.util.List;

/** Hand-declared T27 page breaks and coordinates, fixed in docs/table-pagination.md before production. */
final class T27TableExpectations {
    static final int PAGE_COUNT = 19;
    static final double TOLERANCE = 0.0001;
    static final String[] TEXT = {
        "AABBBBAABBBBAABBBBAABBBB","AABBBB","AABBBBAABBBBAABBBBAABBBB","AABBBB",
        "ABB","BA\u03a9","ABB\u03a9","AB\u03a9","ABB\u03a9","ABBB","AB","\u03a9",
        "A","BB\u03a9","BBB","AAAA","A","AB","BBB"
    };
    static final Run[] RUNS;
    // page, left, bottom, right, top; every inside border is one point wide.
    static final double[][] CELLS;
    static final String DECLARATION;

    static {
        List<Run> runs = new ArrayList<Run>();
        List<double[]> cells = new ArrayList<double[]>();
        wholeRows(runs,cells,1,40);
        wholeRows(runs,cells,3,43);
        runs.add(new Run(5,"A",75,710)); runs.add(new Run(5,"BB",115,710));
        runs.add(new Run(6,"B",75,710)); runs.add(new Run(6,"A",115,710)); runs.add(new Run(6,"\u03a9",155,709.8));
        cells.add(new double[] {5,72,702,112,720}); cells.add(new double[] {5,112,702,192,720});
        cells.add(new double[] {6,72,702,112,720}); cells.add(new double[] {6,112,702,152,720});
        cells.add(new double[] {6,152,702,192,720});
        column(runs,cells,7,new String[] {"A","B","B","\u03a9"});
        column(runs,cells,8,new String[] {"A","B","\u03a9"});
        column(runs,cells,9,new String[] {"A","B","B","\u03a9"});
        column(runs,cells,10,new String[] {"A","B","B","B"});
        runs.add(new Run(11,"A",75,710)); runs.add(new Run(11,"B",75,698));
        runs.add(new Run(12,"\u03a9",75,709.8));
        cells.add(new double[] {11,72,690,112,720}); cells.add(new double[] {12,72,702,112,720});
        runs.add(new Run(13,"A",72,713));
        column(runs,cells,14,new String[] {"B","B"}); runs.add(new Run(14,"\u03a9",72,676.8));
        column(runs,cells,15,new String[] {"B","B","B"});
        runs.add(new Run(16,"AAAA",75,710)); cells.add(new double[] {16,72,702,90,720});
        runs.add(new Run(17,"A",72,713)); runs.add(new Run(18,"A",75,710)); runs.add(new Run(18,"B",75,698));
        cells.add(new double[] {18,72,690,112,720});
        column(runs,cells,19,new String[] {"B","B","B"});
        RUNS = runs.toArray(new Run[runs.size()]); CELLS = cells.toArray(new double[cells.size()][]);
        StringBuilder declaration = new StringBuilder("pages=19; MediaBox=[0,0,612,792]; size=10; "
                + "borders=1 inside black; tolerance=0.0001; PDFium=144dpi opaque-white sRGB; "
                + "ImageMagick AE=0 fuzz=0%; secondary changed-pixels<=2500;");
        for (Run run : RUNS) {
            declaration.append(" run=(").append(run.page).append(',').append(run.text).append(',')
                    .append(run.x).append(',').append(run.y).append(");");
        }
        for (double[] cell : CELLS) { declaration.append(" cell=").append(java.util.Arrays.toString(cell)).append(';'); }
        DECLARATION = declaration.toString();
    }

    private static void wholeRows(List<Run> runs,List<double[]> cells,int firstPage,double firstWidth) {
        // The page/area assignment is an oracle input, not a call to the paginator.
        int[] pages = {firstPage,firstPage,firstPage,firstPage,firstPage + 1};
        double[] left = {72,72,172,172,72};
        double[] top = {720,702,720,702,720};
        for (int row = 0; row < 5; row++) {
            runs.add(new Run(pages[row],"AA",left[row] + 3,top[row] - 10));
            runs.add(new Run(pages[row],"BBBB",left[row] + firstWidth + 3,top[row] - 10));
            cells.add(new double[] {pages[row],left[row],top[row] - 18,left[row] + firstWidth,top[row]});
            cells.add(new double[] {pages[row],left[row] + firstWidth,top[row] - 18,left[row] + 100,top[row]});
        }
    }
    private static void column(List<Run> runs,List<double[]> cells,int page,String[] rows) {
        for (int row = 0; row < rows.length; row++) {
            double top = 720 - 18 * row;
            runs.add(new Run(page,rows[row],75,top - (rows[row].equals("\u03a9") ? 10.2 : 10)));
            cells.add(new double[] {page,72,top - 18,112,top});
        }
    }
    static double advance(char cp) {
        switch (cp) {
            case 'A': return 6;
            case 'B': return 6.5;
            case '\u03a9': return 7;
            default: throw new IllegalArgumentException("Unknown T27 oracle character");
        }
    }
    static double[][] borders(double[] cell) {
        double l=cell[1], b=cell[2], r=cell[3], t=cell[4];
        return new double[][] {{l,t-1,r,t},{r-1,b,r,t},{l,b,r,b+1},{l,b,l+1,t}};
    }
    static final class Run {
        final int page;
        final String text;
        final double x;
        final double y;
        Run(int page,String text,double x,double y) { this.page=page; this.text=text; this.x=x; this.y=y; }
    }
    private T27TableExpectations() { }
}
