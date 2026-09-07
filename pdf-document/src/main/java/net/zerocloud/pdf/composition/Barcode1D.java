package net.zerocloud.pdf.composition;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable semantic input and point dimensions for a vector barcode.
 * Validation occurs at the Document Workflow boundary before page mutation.
 *
 * <p>The local origin is the left edge of the horizontal box at the bar
 * baseline. The first bar begins at the quiet zone and rises toward positive
 * y. Defaults are a 1-point module, 48-point full height and 10-point quiet
 * zones (11 for EAN13). Postal defaults are width 1.44, pitch 3.24, full height
 * 9, short height 3.6 and quiet zones 9 points. No label or optional checksum
 * is selected by default. Dimensions do not automatically rescale each other.</p>
 *
 * <p>Inputs are exact: no trimming, filtering, replacement or zero padding is
 * implicit. Version 1 bounds input at 256 UTF-16 units or 256 raw codewords,
 * with additional symbol capacity and generated-content limits checked during
 * execution. Mode-specific invalid input and options fail through checked
 * Document Failure rather than publishing a partial barcode.</p>
 *
 * @see net.zerocloud.pdf.composition.command.DrawBarcode1D
 * @see BarcodeText
 * @since 0.1.0
 */
public final class Barcode1D {
    /** Current declaration version. */
    public static final int VERSION_1 = 1;
    /** Explicit Code128 function 1, distinct from literal backslash text. */
    public static final char FNC1 = '\ue001';
    /** Explicit Code128 function 2. */
    public static final char FNC2 = '\ue002';
    /** Explicit Code128 function 3. */
    public static final char FNC3 = '\ue003';
    /** Explicit Code128 function 4 (extended-character shift/latch). */
    public static final char FNC4 = '\ue004';
    /** Supported one-dimensional symbol families. */
    public enum Mode {
        /** ASCII 0–127 and explicit FNC1–4, with mandatory modulo-103 check. */ CODE128,
        /** Bracketed GS1 application identifiers with validated fields and FNC1 separators. */ GS1_128,
        /** Explicit start and contextual symbols; use {@link Barcode1D#rawCode128(int...)}. */ CODE128_RAW,
        /** Digits, uppercase letters and {@code -. $/+%}; optional modulo-43 check. */ CODE39,
        /** Full ASCII 0–127 expansion into Code39; optional modulo-43 check. */ CODE39_EXTENDED,
        /** A–D guards enclosing {@code 0123456789-$:/.+}; optional modulo-16 check. */ CODABAR,
        /** Twelve data digits or thirteen including a verified mandatory check. */ EAN13,
        /** Seven data digits or eight including a verified mandatory check. */ EAN8,
        /** Eleven data digits or twelve including a verified mandatory check. */ UPCA,
        /** Number system 0/1 and six compressed digits, optionally with a verified check. */ UPCE,
        /** Exactly two digits encoded with modulo-four parity. */ SUPPLEMENT2,
        /** Exactly five digits encoded with weighted modulo-ten parity. */ SUPPLEMENT5,
        /** Even digits without a check, or odd data digits with a generated GS1 modulo-ten check. */ INTERLEAVED_2_OF_5,
        /** Decimal digits with no check or a generated Luhn modulo-ten check. */ MSI,
        /** Five, nine or eleven data digits, mandatory digit-sum check and height-coded bars. */ POSTNET,
        /** Eleven or thirteen data digits, mandatory digit-sum check and complementary postal bars. */ PLANET
    }
    /** Permitted Code128 code sets. */
    public enum CodeSet {
        /** Select and switch representable code sets; explicit FNC4 restricts selection to A/B. */ AUTO,
        /** Restrict data to ASCII 0–95, plus functions. */ A,
        /** Restrict data to ASCII 32–127, plus functions. */ B,
        /** Restrict data to decimal pairs and representable FNC1 positions. */ C
    }

    private final Mode mode;
    private final String content;
    private final int[] rawCodewords;
    private final CodeSet codeSet;
    private final double moduleWidth;
    private final double barHeight;
    private final double quietZone;
    private final boolean generateChecksum;
    private final double wideToNarrowRatio;
    private final String supplement;
    private final double guardExtension;
    private final double postalPitch;
    private final double shortBarHeight;
    private final BarcodeText humanReadable;

    private Barcode1D(Builder builder) {
        mode = builder.mode; content = builder.content; codeSet = builder.codeSet;
        rawCodewords = builder.rawCodewords.clone();
        moduleWidth = builder.moduleWidth; barHeight = builder.barHeight; quietZone = builder.quietZone;
        generateChecksum = builder.generateChecksum; wideToNarrowRatio = builder.wideToNarrowRatio;
        supplement = builder.supplement;
        guardExtension = builder.guardExtension; postalPitch = builder.postalPitch;
        shortBarHeight = builder.shortBarHeight;
        humanReadable = builder.humanReadable;
    }

    /** Begins a version-1 barcode declaration. */
    public static Builder builder(Mode mode, String content) { return new Builder(mode, content); }
    /**
     * Begins a raw Code128 declaration, including start 103/104/105 but
     * excluding the generated check and stop. Following symbols are contextual
     * values 0–102; incomplete shifts/latches fail execution. The array is copied.
     * @param codewords explicit start and subsequent symbols
     * @return a builder for raw Code128
     */
    public static Builder rawCode128(int... codewords) {
        Builder builder = new Builder(Mode.CODE128_RAW, "");
        builder.rawCodewords = Objects.requireNonNull(codewords, "codewords").clone();
        return builder;
    }
    /** @return declaration version */
    public int getVersion() { return VERSION_1; }
    /** @return symbology */
    public Mode getMode() { return mode; }
    /** @return exact caller input */
    public String getContent() { return content; }
    /** @return a defensive copy of explicit Code128 symbols, or an empty array */
    public int[] getRawCodewords() { return rawCodewords.clone(); }
    /** @return raw symbol count without materializing a copy */
    public int getRawCodewordCount() { return rawCodewords.length; }
    /** @return Code128 code-set restriction */
    public CodeSet getCodeSet() { return codeSet; }
    /** @return narrow module width in points */
    public double getModuleWidth() { return moduleWidth; }
    /** @return full bar height in points */
    public double getBarHeight() { return barHeight; }
    /** @return blank margin at each horizontal edge, in points */
    public double getQuietZone() { return quietZone; }
    /** @return whether to generate the symbology's optional checksum */
    public boolean isGenerateChecksum() { return generateChecksum; }
    /** @return wide-to-narrow module ratio for two-width symbologies */
    public double getWideToNarrowRatio() { return wideToNarrowRatio; }
    /** @return an EAN/UPC add-on, or the empty string when absent */
    public String getSupplement() { return supplement; }
    /** @return retail guard extension below the bar baseline in points */
    public double getGuardExtension() { return guardExtension; }
    /** @return postal bar-to-bar pitch in points; zero for other modes */
    public double getPostalPitch() { return postalPitch; }
    /** @return postal short bar height in points; zero for other modes */
    public double getShortBarHeight() { return shortBarHeight; }
    /** @return optional human-readable label declaration */
    public Optional<BarcodeText> getHumanReadable() { return Optional.ofNullable(humanReadable); }

    /** Records barcode intent without encoding or accessing external resources. */
    public static final class Builder {
        private final Mode mode;
        private final String content;
        private int[] rawCodewords = new int[0];
        private CodeSet codeSet = CodeSet.AUTO;
        private double moduleWidth = 1;
        private double barHeight = 48;
        private double quietZone = 10;
        private boolean generateChecksum;
        private double wideToNarrowRatio = 2;
        private String supplement = "";
        private double guardExtension;
        private double postalPitch;
        private double shortBarHeight;
        private BarcodeText humanReadable;
        private Builder(Mode mode, String content) {
            this.mode = Objects.requireNonNull(mode, "mode");
            this.content = Objects.requireNonNull(content, "content");
            if (mode == Mode.EAN13) { quietZone = 11; }
            if (mode == Mode.POSTNET || mode == Mode.PLANET) {
                moduleWidth = 1.44; barHeight = 9; quietZone = 9; postalPitch = 3.24; shortBarHeight = 3.6;
            }
        }
        /** Selects the Code128 code set. @return this builder */
        public Builder codeSet(CodeSet value) { codeSet = Objects.requireNonNull(value, "codeSet"); return this; }
        /** Sets module width in points. @return this builder */
        public Builder moduleWidth(double value) { moduleWidth = value; return this; }
        /** Sets full bar height in points. @return this builder */
        public Builder barHeight(double value) { barHeight = value; return this; }
        /** Sets each horizontal quiet zone; at least ten modules, eleven for EAN13, or nine postal points. @return this builder */
        public Builder quietZone(double value) { quietZone = value; return this; }
        /** Enables the optional Code39, Codabar, ITF or MSI checksum; other modes reject this option. @return this builder */
        public Builder generateChecksum(boolean value) { generateChecksum = value; return this; }
        /** Sets a ratio from two through three for Code39, Codabar or ITF families. @return this builder */
        public Builder wideToNarrowRatio(double value) { wideToNarrowRatio = value; return this; }
        /** Appends a two- or five-digit supplement to EAN13, EAN8, UPCA or UPCE with a nine-module gap. @return this builder */
        public Builder supplement(String value) { supplement = Objects.requireNonNull(value, "supplement"); return this; }
        /** Sets the downward retail guard extension in points. @return this builder */
        public Builder guardExtension(double value) { guardExtension = value; return this; }
        /** Sets postal bar-to-bar pitch in points, strictly greater than module width. @return this builder */
        public Builder postalPitch(double value) { postalPitch = value; return this; }
        /** Sets a positive postal short height below the full height, in points. @return this builder */
        public Builder shortBarHeight(double value) { shortBarHeight = value; return this; }
        /** Adds an explicit-font human-readable label. @return this builder */
        public Builder humanReadable(BarcodeText value) { humanReadable = Objects.requireNonNull(value, "humanReadable"); return this; }
        /** @return an immutable snapshot */
        public Barcode1D build() { return new Barcode1D(this); }
    }
}
