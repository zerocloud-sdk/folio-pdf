package net.zerocloud.pdf.acceptance;

/**
 * Independent numeric oracle, fixed before producing table output. The examples in
 * docs/table-composition.md are translated by (52,580) onto 612x792 pages.
 * Font size 10: A=6, B=6.5, omega=7; primary/fallback ascents are 7/7.2.
 */
final class T26TableExpectations {
    static final double TOLERANCE = 0.0001;
    static final Run[] RUNS = {
        new Run(1,"A",76,709), new Run(1,"B",116,709), new Run(1,"\u03a9",166,708.8),
        new Run(2,"AA",75,710), new Run(2,"BBBB",118,710),
        new Run(3,"A",75,710), new Run(3,"BB",115,710), new Run(3,"B",115,692), new Run(3,"\u03a9",155,691.8)
    };
    // page, left, bottom, right, top. Each inside border is one point wide.
    static final double[][] CELLS = {
        {1,72,700,112,720}, {1,112,700,162,720}, {1,162,700,272,720},
        {2,72,702,115,720}, {2,115,702,172,720},
        {3,72,684,112,720}, {3,112,702,192,720}, {3,112,684,152,702}, {3,152,684,192,702}
    };
    static final String DECLARATION = "pages=3; MediaBox=[0,0,612,792]; size=10; "
            + "fixed=[40,50,110],padding=3; auto=[43,57],padding=2; spans=[40,40,40],padding=2; "
            + "borders=1 inside black; rows=[20],[18],[18,18]; "
            + "runs=(1,A,76,709),(1,B,116,709),(1,omega,166,708.8),(2,AA,75,710),(2,BBBB,118,710),"
            + "(3,A,75,710),(3,BB,115,710),(3,B,115,692),(3,omega,155,691.8); "
            + "reading-order=ABomega/AABBBB/ABBBomega; tolerance=0.0001; "
            + "PDFium=144dpi opaque-white sRGB; ImageMagick AE=0 fuzz=0%; secondary changed-pixels<=2500";
    private T26TableExpectations() { }
    static double advance(char cp) {
        switch (cp) {
            case 'A': return 6;
            case 'B': return 6.5;
            case '\u03a9': return 7;
            default: throw new IllegalArgumentException("Unknown table oracle character");
        }
    }
    static double[][] borders(double[] cell) {
        double l=cell[1], b=cell[2], r=cell[3], t=cell[4];
        return new double[][] {{l,t-1,r,t}, {r-1,b,r,t}, {l,b,r,b+1}, {l,b,l+1,t}};
    }
    static final class Run {
        final int page;
        final String text;
        final double x;
        final double y;
        Run(int page,String text,double x,double y) { this.page=page;this.text=text;this.x=x;this.y=y; }
    }
}
