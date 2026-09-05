package net.zerocloud.pdf.acceptance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Hand-specified T25 oracle, independent of paragraph declarations and layout.
 * At 40 points: A advances 24, B 26, space 10, omega 28; top extents are
 * primary 28 and fallback 28.8. Every page is [0,0,612,792].
 * Coordinates and tolerances are fixed before rendering any T25 output.
 */
final class T25ParagraphExpectations {
    static final double TOLERANCE = 0.0001;
    static final List<Profile> PROFILES = Collections.unmodifiableList(Arrays.asList(
        profile("indentation", run(1, "A", 120, 692), run(1, "AA", 96, 652),
                run(1, "AA", 256, 692), run(1, "AA", 256, 652),
                run(2, "AA", 96, 692), run(2, "AA", 96, 652), run(2, "AA", 96, 612)),
        tabs(),
        profile("keep-next", run(1, "A", 72, 692), run(1, "A", 72, 652), run(1, "A", 232, 692),
                run(2, "B", 72, 692), run(2, "\u03a9", 72, 651.2), run(2, "\u03a9", 72, 611.2)),
        profile("keep-together", run(1, "A", 72, 692), run(1, "A", 72, 652), run(1, "A", 232, 692),
                run(2, "B", 72, 692), run(2, "B", 72, 652)),
        profile("widow", run(1, "A", 72, 692), run(1, "A", 72, 652),
                run(2, "A", 72, 692), run(2, "B", 72, 652)),
        profile("orphan", run(1, "A", 72, 692), run(1, "A", 72, 652),
                run(2, "B", 72, 692), run(2, "B", 72, 652), run(2, "\u03a9", 72, 611.2)),
        profile("overflow-wrap", run(1, "AA", 72, 692), run(1, "AA", 72, 652), run(1, "AA", 72, 612),
                run(2, "AA", 72, 692), run(2, "AA", 72, 652), run(2, "AA", 72, 612)),
        profile("overflow-reject", run(2, "AAAA", 72, 692)),
        profile("overflow-visible", run(1, "AAAA", 72, 692), run(1, "AAAA", 72, 652),
                run(1, "AAAA", 232, 692), run(1, "AAAA", 232, 652), run(2, "AAAA", 72, 692)),
        profile("relayout", run(1, "AAAAAA", 72, 692), run(2, "AAAAAA", 72, 692), run(2, "A", 72, 652)),
        profile("immediate-flush", run(1, "AAA", 72, 692), run(1, "AAA", 72, 652),
                run(1, "AAA", 232, 692), run(1, "AAA", 232, 652), run(2, "A", 72, 692)),
        profile("publication", run(1, "AAA", 72, 692), run(1, "AAA", 72, 652),
                run(1, "AAA", 232, 692), run(1, "AAA", 232, 652), run(2, "A", 72, 692))));

    private T25ParagraphExpectations() { }
    private static Profile profile(String rule, Run... runs) { return new Profile(rule, Arrays.asList(runs)); }
    private static Run run(int page, String text, double x, double y) { return new Run(page, text, x, y); }
    private static Profile tabs() {
        List<Run> runs = new ArrayList<Run>();
        // LEFT, CENTER, RIGHT, anchor B, absent anchor, default repeating grid.
        double[] fields = {168, 143, 118, 144, 118, 216};
        for (int page = 1; page <= 2; page++) {
            for (int row = 0; row < fields.length; row++) {
                runs.add(run(page, "A", 72, 692 - 40 * row));
                runs.add(run(page, "AB", fields[row], 692 - 40 * row));
            }
        }
        return new Profile("tabs", runs);
    }
    static double advance(char cp) {
        switch (cp) {
            case 'A': return 24;
            case 'B': return 26;
            case ' ': return 10;
            case '\u03a9': return 28;
            default: throw new IllegalArgumentException("Character outside the hand-specified T25 alphabet");
        }
    }
    static final class Profile {
        final String rule;
        final String id;
        final List<Run> runs;
        Profile(String rule, List<Run> runs) {
            this.rule = rule; this.id = "T25-paragraph-" + rule;
            this.runs = Collections.unmodifiableList(new ArrayList<Run>(runs));
        }
        String declaration() {
            StringBuilder result = new StringBuilder("pages=2;MediaBox=[0,0,612,792];fontSize=40;tolerance=0.0001;rule=")
                    .append(rule).append(";runs=");
            for (Run run : runs) { result.append('(').append(run.page).append(',').append(run.text)
                    .append(',').append(run.x).append(',').append(run.y).append(')'); }
            return result.toString();
        }
    }
    static final class Run {
        final int page;
        final String text;
        final double x;
        final double y;
        Run(int page, String text, double x, double y) { this.page = page; this.text = text; this.x = x; this.y = y; }
    }
}
