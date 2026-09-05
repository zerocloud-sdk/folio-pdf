package net.zerocloud.pdf.acceptance;

/**
 * Hand-calculated T24 oracle, independent of the paragraph implementation.
 * At 40 points the project fonts advance A=24, B=26, space=10, omega=28.
 * Primary/fallback top extents are 28/28.8. Each declared area holds two
 * 40-point lines. The atomic graphic consumes the first line of column two.
 */
final class T24ParagraphExpectations {
    static final double TOLERANCE = 0.0001;
    static final Run[] RUNS = {
        new Run(1, "AA ", 72, 604),
        new Run(1, "AA ", 72, 564),
        new Run(1, "B\u03a9 ", 232, 563.2),
        new Run(2, "B\u03a9 B\u03a9", 72, 691.2)
    };
    static final String DECLARATION = "pages=2; MediaBox=[0,0,612,792]; fontSize=40; "
            + "runs=(1,AA ,72,604),(1,AA ,72,564),(1,B\u03a9 ,232,563.2),(2,B\u03a9 B\u03a9,72,691.2); "
            + "graphic=(1,[232,600,264,632],rgb[0.2,0.4,0.8]); "
            + "advances=A:24,B:26,space:10,omega:28; tolerance=0.0001";

    private T24ParagraphExpectations() { }

    static double advance(char value) {
        switch (value) {
            case 'A': return 24;
            case 'B': return 26;
            case ' ': return 10;
            case '\u03a9': return 28;
            default: throw new IllegalArgumentException("Unknown oracle character");
        }
    }

    static final class Run {
        final int page;
        final String text;
        final double x;
        final double y;
        Run(int page, String text, double x, double y) {
            this.page = page; this.text = text; this.x = x; this.y = y;
        }
    }
}
