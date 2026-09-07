package net.zerocloud.pdf.acceptance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.zerocloud.pdf.composition.Barcode1D;
import net.zerocloud.pdf.composition.BarcodeText;
import net.zerocloud.pdf.composition.FontSelection;
import net.zerocloud.pdf.composition.FontLimits;
import net.zerocloud.pdf.composition.CanvasMatrix;

/** Literal T30 inputs, payloads and independently calculated check words, fixed before comparison. */
final class T30BarcodeProfile {
    private T30BarcodeProfile() { }

    static List<Fixture> fixtures() {
        List<Fixture> cases = new ArrayList<Fixture>();
        add(cases, "code128-b", code128("AB", Barcode1D.CodeSet.B), "AB", 102, 104, 33, 34);
        add(cases, "code128-a", code128("\u0001AB", Barcode1D.CodeSet.A), "\u0001AB", 27, 103, 65, 33, 34);
        add(cases, "code128-c", code128("123456", Barcode1D.CodeSet.C), "123456", 44, 105, 12, 34, 56);
        add(cases, "code128-auto", code128("AB123456", Barcode1D.CodeSet.AUTO), "AB123456", 26, 104, 33, 34, 99, 12, 34, 56);
        add(cases, "gs1-auto", gs1("[01]09501101530003", Barcode1D.CodeSet.AUTO), "]C10109501101530003", 71,
                105, 102, 1, 9, 50, 11, 1, 53, 0, 3);
        add(cases, "gs1-c", gs1("[01]09501101530003", Barcode1D.CodeSet.C), "]C10109501101530003", 71,
                105, 102, 1, 9, 50, 11, 1, 53, 0, 3);
        add(cases, "gs1-a", gs1("[10]LOT[21]ABC", Barcode1D.CodeSet.A), "]C110LOT\u001d21ABC", 55,
                103, 102, 17, 16, 44, 47, 52, 102, 18, 17, 33, 34, 35);
        add(cases, "gs1-b", gs1("[10]LOT[21]ABC", Barcode1D.CodeSet.B), "]C110LOT\u001d21ABC", 56,
                104, 102, 17, 16, 44, 47, 52, 102, 18, 17, 33, 34, 35);
        int[] functions = {102, 97, 96, 100};
        int[] checks = {31, 21, 19, 27};
        for (int index = 0; index < functions.length; index++) {
            String payload = index == 3 ? "A\u00c2" : "AB";
            add(cases, "fnc" + (index + 1), code128("A" + (char) (Barcode1D.FNC1 + index) + "B", Barcode1D.CodeSet.B),
                    payload, checks[index], 104, 33, functions[index], 34);
        }
        add(cases, "fnc4-latch", code128("" + Barcode1D.FNC4 + Barcode1D.FNC4 + "AB", Barcode1D.CodeSet.B),
                "\u00c1\u00c2", 21, 104, 100, 100, 33, 34);
        addRaw(cases, "raw-b", "AB", 102, 104, 33, 34);
        addRaw(cases, "raw-a", "\u0001AB", 27, 103, 65, 33, 34);
        addRaw(cases, "raw-fnc4-latch", "\u00c1\u00c2", 21, 104, 100, 100, 33, 34);
        addRaw(cases, "raw-shift", "A\u0001B", 46, 104, 33, 98, 65, 34);
        addRaw(cases, "raw-latches", "1234A\u0001", 70, 105, 12, 34, 100, 33, 101, 65);
        for (int index = 0; index < functions.length; index++) {
            addRaw(cases, "raw-fnc" + (index + 1), index == 3 ? "A\u00c2" : "AB", checks[index], 104, 33, functions[index], 34);
        }
        int[] literalChecks = {78, 84, 90, 96};
        for (int index = 0; index < 4; index++) {
            String literal = "\\<FNC" + (index + 1) + ">";
            add(cases, "literal-fnc" + (index + 1), code128(literal, Barcode1D.CodeSet.AUTO), literal,
                    literalChecks[index], 104, 60, 28, 38, 46, 35, 17 + index, 30);
        }
        String prefix = "\\<FNC1>";
        add(cases, "literal-fnc4-a-single", code128(prefix + Barcode1D.FNC4 + "\u0001A", Barcode1D.CodeSet.AUTO),
                prefix + "\u0081A", 27, 104, 60, 28, 38, 46, 35, 17, 30, 101, 101, 65, 33);
        add(cases, "literal-fnc4-a-pair", code128(prefix + Barcode1D.FNC4 + Barcode1D.FNC4 + "\u0001A", Barcode1D.CodeSet.AUTO),
                prefix + "\u0081\u00c1", 2, 104, 60, 28, 38, 46, 35, 17, 30, 101, 101, 101, 65, 33);
        add(cases, "literal-fnc4-b-single", code128(prefix + "\u0001" + Barcode1D.FNC4 + "\u007fA", Barcode1D.CodeSet.AUTO),
                prefix + "\u0001\u00ffA", 93, 104, 60, 28, 38, 46, 35, 17, 30, 101, 65, 100, 100, 95, 33);
        add(cases, "literal-fnc4-b-pair", code128(prefix + "\u0001" + Barcode1D.FNC4 + Barcode1D.FNC4 + "\u007fA", Barcode1D.CodeSet.AUTO),
                prefix + "\u0001\u00ff\u00c1", 82, 104, 60, 28, 38, 46, 35, 17, 30, 101, 65, 100, 100, 100, 95, 33);
        String fnc4 = String.valueOf(Barcode1D.FNC4);
        add(cases, "fnc4-auto-single-digits", code128(fnc4 + "123456", Barcode1D.CodeSet.AUTO),
                "\u00b123456", 27, 104, 100, 17, 18, 19, 20, 21, 22);
        add(cases, "fnc4-auto-pair-digits", code128(fnc4 + fnc4 + "123456", Barcode1D.CodeSet.AUTO),
                "\u00b1\u00b2\u00b3\u00b4\u00b5\u00b6", 35, 104, 100, 100, 17, 18, 19, 20, 21, 22);
        add(cases, "fnc4-auto-digits-before", code128("123456" + fnc4 + "123456", Barcode1D.CodeSet.AUTO),
                "123456\u00b123456", 5, 104, 17, 18, 19, 20, 21, 22, 100, 17, 18, 19, 20, 21, 22);
        add(cases, "fnc4-auto-shift-and-unlatch", code128(fnc4 + fnc4 + "12" + fnc4 + "34" + fnc4 + fnc4 + "56", Barcode1D.CodeSet.AUTO),
                "\u00b1\u00b23\u00b456", 34, 104, 100, 100, 17, 18, 100, 19, 20, 100, 100, 21, 22);
        add39(cases, "code39", false, "FOLIO", false, 2, "FOLIO");
        add39(cases, "code39-check", false, "FOLIO", true, 2, "FOLIOG");
        add39(cases, "code39-ratio3", false, "FOLIO", false, 3, "FOLIO");
        add39(cases, "code39-full", true, "abc", false, 2, "+A+B+C");
        add39(cases, "code39-full-check", true, "abc", true, 2, "+A+B+CR");
        add39(cases, "code39-full-ratio3", true, "abc", false, 3, "+A+B+C");
        add39(cases, "code39-controls", true, "\u0000a*\u007f", false, 2, "%U+A/J%T");
        add39(cases, "code39-literal", true, "\\<FNC1>", false, 2, "%L%GFNC1%I");
        cases.add(new Fixture("codabar", Barcode1D.builder(Barcode1D.Mode.CODABAR, "A1234B").build(),
                "A1234B", -1, new int[0], "A1234B"));
        cases.add(new Fixture("codabar-check", Barcode1D.builder(Barcode1D.Mode.CODABAR, "A1234B").generateChecksum(true).build(),
                "A12345B", -1, new int[0], "A12345B"));
        cases.add(new Fixture("codabar-ratio3", Barcode1D.builder(Barcode1D.Mode.CODABAR, "C12-.$:/+D").wideToNarrowRatio(3).build(),
                "C12-.$:/+D", -1, new int[0], "C12-.$:/+D"));
        retail(cases, "ean13", Barcode1D.Mode.EAN13, "590123412345", "5901234123457", "");
        retail(cases, "ean8", Barcode1D.Mode.EAN8, "9638507", "96385074", "");
        retail(cases, "upca", Barcode1D.Mode.UPCA, "04210000526", "042100005264", "");
        retail(cases, "upce", Barcode1D.Mode.UPCE, "0425261", "04252614", "042100005264");
        String[][] compressed = {{"0123450", "01234505", "012000003455"}, {"0123453", "01234531", "012300000451"},
            {"0123454", "01234543", "012340000053"}, {"0123455", "01234558", "012345000058"},
            {"1123450", "11234502", "112000003452"}, {"1123453", "11234538", "112300000458"},
            {"1123454", "11234540", "112340000050"}, {"1123455", "11234555", "112345000055"}};
        for (String[] fixture : compressed) {
            cases.add(new Fixture("upce-" + fixture[0], Barcode1D.builder(Barcode1D.Mode.UPCE, fixture[0]).build(),
                    fixture[1], -1, new int[0], fixture[1], fixture[2]));
        }
        for (String digits : new String[] {"00", "01", "02", "03", "05", "51234", "00000", "00001", "00002",
                "00003", "00004", "00005", "00006", "00007", "00008", "00009"}) {
            cases.add(new Fixture("supplement-" + digits, Barcode1D.builder(digits.length() == 2
                    ? Barcode1D.Mode.SUPPLEMENT2 : Barcode1D.Mode.SUPPLEMENT5, digits).build(), digits, -1, new int[0], digits));
        }
        Barcode1D.Mode[] modes = {Barcode1D.Mode.EAN13, Barcode1D.Mode.EAN8, Barcode1D.Mode.UPCA, Barcode1D.Mode.UPCE};
        String[] names = {"ean13", "ean8", "upca", "upce"};
        String[] bodies = {"590123412345", "9638507", "04210000526", "0425261"};
        String[] complete = {"5901234123457", "96385074", "042100005264", "04252614"};
        for (int mode = 0; mode < modes.length; mode++) {
            for (String supplement : new String[] {"05", "51234"}) {
                cases.add(new Fixture(names[mode] + "-plus-" + supplement, Barcode1D.builder(modes[mode], bodies[mode]).supplement(supplement).build(),
                        complete[mode], -1, new int[0], complete[mode], mode == 3 ? "042100005264" : ""));
            }
        }
        itf(cases, "itf", "123456", false, 2, "123456");
        itf(cases, "itf-check", "12345", true, 2, "123457");
        itf(cases, "itf-ratio3", "12345", true, 3, "123457");
        itf(cases, "itf-short", "1", true, 2, "17");
        itf(cases, "itf-leading-zero", "0123456789", false, 2, "0123456789");
        cases.add(new Fixture("msi", Barcode1D.builder(Barcode1D.Mode.MSI, "1234567").build(), "1234567", -1, new int[0], "1234567"));
        cases.add(new Fixture("msi-check", Barcode1D.builder(Barcode1D.Mode.MSI, "1234567").generateChecksum(true).build(),
                "12345674", -1, new int[0], "12345674"));
        cases.add(new Fixture("msi-leading-zero", Barcode1D.builder(Barcode1D.Mode.MSI, "0123456789").build(),
                "0123456789", -1, new int[0], "0123456789"));
        postal(cases, "postnet-5", false, "12345", "123455");
        postal(cases, "postnet-9", false, "123456789", "1234567895");
        postal(cases, "postnet-11", false, "12345678901", "123456789014");
        postal(cases, "planet-11", true, "40123456789", "401234567891");
        postal(cases, "planet-13", true, "4012345678901", "40123456789010");
        List<Fixture> labelled = new ArrayList<Fixture>();
        FontSelection font = T30FontMetrics.NOTO.selection();
        FontLimits limits = FontLimits.builder().maximumFontSources(1).maximumSourceBytes(1024 * 1024).maximumCodePoints(1024)
                .maximumFallbackChecks(1024).maximumGeneratedContentBytes(65536).build();
        for (Fixture fixture : cases) {
            String caption = caption(fixture);
            BarcodeText.Builder label = BarcodeText.builder(font, limits, 8);
            if (fixture.barcode.getMode() == Barcode1D.Mode.CODE128_RAW) { label.alternateText(caption); }
            Barcode1D barcode = withText(fixture.barcode, label.build());
            labelled.add(new Fixture(fixture.id, barcode, fixture.payload, fixture.check, fixture.words, fixture.encoded, fixture.expansion, caption));
        }
        variants(labelled, font, limits);
        return Collections.unmodifiableList(labelled);
    }

    private static Barcode1D withText(Barcode1D input, BarcodeText text) {
        return copy(input).humanReadable(text).build();
    }

    private static Barcode1D.Builder copy(Barcode1D input) {
        Barcode1D.Builder builder = input.getMode() == Barcode1D.Mode.CODE128_RAW
                ? Barcode1D.rawCode128(input.getRawCodewords()) : Barcode1D.builder(input.getMode(), input.getContent());
        return builder.codeSet(input.getCodeSet()).moduleWidth(input.getModuleWidth()).quietZone(input.getQuietZone())
                .barHeight(input.getBarHeight()).generateChecksum(input.isGenerateChecksum()).wideToNarrowRatio(input.getWideToNarrowRatio())
                .supplement(input.getSupplement()).guardExtension(input.getGuardExtension()).postalPitch(input.getPostalPitch())
                .shortBarHeight(input.getShortBarHeight());
    }

    private static void variants(List<Fixture> cases, FontSelection font, FontLimits limits) {
        String[][] checks = {{"label-code39-check", "code39-check", "FOLIOG", "false"},
            {"label-code39-guards", "code39-check", "*FOLIOG*", "true"},
            {"label-full-guards", "code39-full-check", "*abcR*", "true"},
            {"label-codabar-check", "codabar-check", "12345", "false"},
            {"label-msi-check", "msi-check", "12345674", "false"},
            {"label-itf-check", "itf-check", "123457", "false"}};
        for (String[] check : checks) {
            Fixture base = find(cases, check[1]);
            BarcodeText text = BarcodeText.builder(font, limits, 8).showChecksum(true).showStartStop(Boolean.parseBoolean(check[3])).build();
            cases.add(variant(check[0], base, withText(base.barcode, text), check[2], base.placement));
        }
        Fixture base = find(cases, "code128-b");
        for (BarcodeText.Alignment alignment : BarcodeText.Alignment.values()) {
            BarcodeText text = BarcodeText.builder(font, limits, 8).position(BarcodeText.Position.ABOVE).gap(4).alignment(alignment).build();
            cases.add(variant("label-above-" + alignment.name().toLowerCase(java.util.Locale.ROOT), base,
                    withText(base.barcode, text), "AB", base.placement));
        }
        cases.add(variant("label-rotate", base, base.barcode, "AB", CanvasMatrix.of(0, 1, -1, 0, 240, 72)));
        cases.add(variant("label-scale", base, base.barcode, "AB", CanvasMatrix.of(2, 0, 0, .5, 24, 96)));
        cases.add(variant("bars-only", base, copy(base.barcode).build(), "", base.placement));
        cases.add(variant("label-alternate", base, withText(base.barcode,
                BarcodeText.builder(font, limits, 8).alternateText("\u03a9 AB").build()), "\u03a9 AB", base.placement));
        Fixture postal = find(cases, "postnet-5");
        cases.add(variant("postal-custom", postal, copy(postal.barcode).moduleWidth(2).postalPitch(4).barHeight(12).shortBarHeight(5)
                .humanReadable(postal.barcode.getHumanReadable().get()).build(), postal.caption, postal.placement));
        for (String mode : new String[] {"ean13", "ean8", "upca", "upce"}) {
            Fixture retail = find(cases, mode.equals("ean13") ? "ean13-plus-05" : mode + "-body");
            cases.add(variant("guard-" + mode, retail, copy(retail.barcode).guardExtension(5)
                    .humanReadable(retail.barcode.getHumanReadable().get()).build(), retail.caption, retail.placement));
        }
    }

    private static Fixture find(List<Fixture> cases, String id) {
        for (Fixture fixture : cases) { if (fixture.id.equals(id)) { return fixture; } }
        throw new IllegalArgumentException("Undeclared fixture " + id);
    }

    private static Fixture variant(String id, Fixture base, Barcode1D barcode, String caption, CanvasMatrix placement) {
        return new Fixture(id, barcode, base.payload, base.check, base.words, base.encoded, base.expansion, caption, placement);
    }

    /** Literal special labels; ordinary data and fixed complete retail/postal numbers retain their declared characters. */
    private static String caption(Fixture fixture) {
        if (fixture.id.equals("code128-a")) { return "\\x01AB"; }
        if (fixture.id.equals("gs1-a") || fixture.id.equals("gs1-b")) { return "(10)LOT(21)ABC"; }
        if (fixture.id.equals("gs1-auto") || fixture.id.equals("gs1-c")) { return "(01)09501101530003"; }
        if (fixture.id.equals("fnc1")) { return "A<FNC1>B"; }
        if (fixture.id.equals("fnc2")) { return "A<FNC2>B"; }
        if (fixture.id.equals("fnc3")) { return "A<FNC3>B"; }
        if (fixture.id.equals("fnc4")) { return "A<FNC4>B"; }
        if (fixture.id.equals("fnc4-latch")) { return "<FNC4><FNC4>AB"; }
        if (fixture.id.equals("literal-fnc4-a-single")) { return "\\<FNC1><FNC4>\\x01A"; }
        if (fixture.id.equals("literal-fnc4-a-pair")) { return "\\<FNC1><FNC4><FNC4>\\x01A"; }
        if (fixture.id.equals("literal-fnc4-b-single")) { return "\\<FNC1>\\x01<FNC4>\\x7FA"; }
        if (fixture.id.equals("literal-fnc4-b-pair")) { return "\\<FNC1>\\x01<FNC4><FNC4>\\x7FA"; }
        if (fixture.id.equals("fnc4-auto-single-digits")) { return "<FNC4>123456"; }
        if (fixture.id.equals("fnc4-auto-pair-digits")) { return "<FNC4><FNC4>123456"; }
        if (fixture.id.equals("fnc4-auto-digits-before")) { return "123456<FNC4>123456"; }
        if (fixture.id.equals("fnc4-auto-shift-and-unlatch")) { return "<FNC4><FNC4>12<FNC4>34<FNC4><FNC4>56"; }
        if (fixture.id.startsWith("raw-")) { return "RAW " + fixture.id; }
        if (fixture.id.equals("code39-controls")) { return "\\x00a*\\x7F"; }
        if (fixture.id.equals("codabar") || fixture.id.equals("codabar-check")) { return "1234"; }
        if (fixture.id.equals("codabar-ratio3")) { return "12-.$:/+"; }
        switch (fixture.barcode.getMode()) {
            case EAN13: case EAN8: case UPCA: case UPCE:
                return fixture.payload + (fixture.barcode.getSupplement().isEmpty() ? "" : " " + fixture.barcode.getSupplement());
            case POSTNET: case PLANET: return fixture.payload;
            default: return fixture.barcode.getContent();
        }
    }

    private static Barcode1D code128(String text, Barcode1D.CodeSet set) {
        return Barcode1D.builder(Barcode1D.Mode.CODE128, text).codeSet(set).build();
    }
    private static Barcode1D gs1(String text, Barcode1D.CodeSet set) {
        return Barcode1D.builder(Barcode1D.Mode.GS1_128, text).codeSet(set).build();
    }
    private static void addRaw(List<Fixture> cases, String id, String payload, int check, int... words) {
        add(cases, id, Barcode1D.rawCode128(words).build(), payload, check, words);
    }
    private static void add(List<Fixture> cases, String id, Barcode1D barcode, String payload, int check, int... words) {
        cases.add(new Fixture(id, barcode, payload, check, words, ""));
    }
    private static void add39(List<Fixture> cases, String id, boolean extended, String input, boolean check, int ratio, String encoded) {
        Barcode1D barcode = Barcode1D.builder(extended ? Barcode1D.Mode.CODE39_EXTENDED : Barcode1D.Mode.CODE39, input)
                .generateChecksum(check).wideToNarrowRatio(ratio).build();
        cases.add(new Fixture(id, barcode, input, -1, new int[0], encoded));
    }
    private static void retail(List<Fixture> cases, String id, Barcode1D.Mode mode, String input, String complete, String expansion) {
        cases.add(new Fixture(id + "-body", Barcode1D.builder(mode, input).build(), complete, -1, new int[0], complete, expansion));
        cases.add(new Fixture(id + "-complete", Barcode1D.builder(mode, complete).build(), complete, -1, new int[0], complete, expansion));
    }
    private static void itf(List<Fixture> cases, String id, String input, boolean check, int ratio, String encoded) {
        cases.add(new Fixture(id, Barcode1D.builder(Barcode1D.Mode.INTERLEAVED_2_OF_5, input)
                .generateChecksum(check).wideToNarrowRatio(ratio).build(), encoded, -1, new int[0], encoded));
    }
    private static void postal(List<Fixture> cases, String id, boolean planet, String input, String encoded) {
        cases.add(new Fixture(id, Barcode1D.builder(planet ? Barcode1D.Mode.PLANET : Barcode1D.Mode.POSTNET, input).build(),
                encoded, -1, new int[0], encoded));
    }

    static final class Fixture {
        final String id;
        final Barcode1D barcode;
        final String payload;
        final int check;
        final int[] words;
        final String encoded;
        final String expansion;
        final String caption;
        final CanvasMatrix placement;
        Fixture(String id, Barcode1D barcode, String payload, int check, int[] words, String encoded) {
            this(id, barcode, payload, check, words, encoded, "");
        }
        Fixture(String id, Barcode1D barcode, String payload, int check, int[] words, String encoded, String expansion) {
            this(id, barcode, payload, check, words, encoded, expansion, "");
        }
        Fixture(String id, Barcode1D barcode, String payload, int check, int[] words, String encoded, String expansion, String caption) {
            this(id, barcode, payload, check, words, encoded, expansion, caption, CanvasMatrix.of(1, 0, 0, 1, 36, 144));
        }
        Fixture(String id, Barcode1D barcode, String payload, int check, int[] words, String encoded, String expansion, String caption, CanvasMatrix placement) {
            this.id = id; this.barcode = barcode; this.payload = payload; this.check = check; this.words = words.clone();
            this.encoded = encoded;
            this.expansion = expansion;
            this.caption = caption;
            this.placement = placement;
        }
    }
}
