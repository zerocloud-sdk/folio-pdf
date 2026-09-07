package net.zerocloud.pdf.composition;

import java.util.Objects;

/**
 * Immutable semantic input for a reusable vector barcode. Validation takes
 * place during Document Workflow execution, before page mutation. QR Model 2,
 * DataMatrix ECC200 and standard PDF417 are supported. The declaration contains
 * exact text or validated raw data codewords, with explicit encoding, dimensions,
 * control headers and DeviceRGB foreground. Quiet margins are reserved geometry;
 * no background is painted. This experimental API does not certify scanners.
 * @since 0.1.0
 */
public final class Barcode2D {
    /** Current declaration version. */
    public static final int VERSION_1 = 1;
    /** Two-dimensional symbol family. */
    public enum Mode {
        /** QR Code Model 2. */ QR,
        /** DataMatrix ECC200. */ DATA_MATRIX,
        /** Standard PDF417 with row indicators and stop pattern. */ PDF417
    }
    /** QR error correction level, preserved exactly without automatic strengthening. */
    public enum QrErrorCorrection {
        /** Low. */ L, /** Medium. */ M, /** Quartile. */ Q, /** High. */ H
    }
    /** DataMatrix high-level compaction choice. */
    public enum DataMatrixEncoding {
        /** Automatic compaction; Macro with FNC1 or nondefault ECI uses ASCII. */ AUTO,
        /** ASCII, digit pairs and upper shifts. */ ASCII,
        /** C40 compaction with ASCII remainder. */ C40,
        /** Text compaction with ASCII remainder. */ TEXT,
        /** ANSI X12 compaction with ASCII remainder. */ X12,
        /** EDIFACT compaction. */ EDIFACT,
        /** Base256 binary compaction. */ BASE256,
        /** Already compacted data words ending in ASCII state. */ RAW
    }
    /** DataMatrix envelope represented by its standard macro control word. */
    public enum DataMatrixMacro {
        /** No envelope. */ NONE,
        /** Macro 05 envelope. */ MACRO_05,
        /** Macro 06 envelope. */ MACRO_06
    }
    /** PDF417 compaction choice. */
    public enum Pdf417Encoding {
        /** Automatic text, numeric and byte compaction. */ AUTO,
        /** Force byte compaction of the strictly encoded input. */ BINARY,
        /** Caller data, excluding descriptor, padding, ECC and embedded Macro controls. */ RAW
    }
    private final Mode mode;
    private final String content;
    private final String encoding;
    private final QrErrorCorrection qrErrorCorrection;
    private final int qrVersion;
    private final int dataMatrixWidth;
    private final int dataMatrixHeight;
    private final DataMatrixEncoding dataMatrixEncoding;
    private final int[] rawCodewords;
    private final DataMatrixMacro dataMatrixMacro;
    private final boolean dataMatrixFnc1;
    private final boolean dataMatrixReaderProgramming;
    private final int dataMatrixSequencePosition;
    private final int dataMatrixSequenceTotal;
    private final int dataMatrixFileId;
    private final int pdf417Columns;
    private final int pdf417Rows;
    private final int pdf417ErrorCorrection;
    private final Pdf417Encoding pdf417Encoding;
    private final double pdf417AspectRatio;
    private final String pdf417MacroFileId;
    private final int pdf417MacroSegment;
    private final int pdf417MacroCount;
    private final double moduleWidth;
    private final double moduleHeight;
    private final double quietZone;
    private final double[] foregroundRgb;
    private Barcode2D(Builder builder) {
        mode = builder.mode; content = builder.content; encoding = builder.encoding;
        qrErrorCorrection = builder.qrErrorCorrection; qrVersion = builder.qrVersion;
        dataMatrixWidth = builder.dataMatrixWidth; dataMatrixHeight = builder.dataMatrixHeight;
        dataMatrixEncoding = builder.dataMatrixEncoding;
        rawCodewords = builder.rawCodewords.clone();
        dataMatrixMacro = builder.dataMatrixMacro; dataMatrixFnc1 = builder.dataMatrixFnc1;
        dataMatrixReaderProgramming = builder.dataMatrixReaderProgramming;
        dataMatrixSequencePosition = builder.dataMatrixSequencePosition;
        dataMatrixSequenceTotal = builder.dataMatrixSequenceTotal; dataMatrixFileId = builder.dataMatrixFileId;
        pdf417Columns = builder.pdf417Columns; pdf417Rows = builder.pdf417Rows;
        pdf417ErrorCorrection = builder.pdf417ErrorCorrection;
        pdf417Encoding = builder.pdf417Encoding;
        pdf417AspectRatio = builder.pdf417AspectRatio;
        pdf417MacroFileId = builder.pdf417MacroFileId; pdf417MacroSegment = builder.pdf417MacroSegment; pdf417MacroCount = builder.pdf417MacroCount;
        moduleWidth = builder.moduleWidth; moduleHeight = builder.moduleHeight; quietZone = builder.quietZone;
        foregroundRgb = builder.foregroundRgb.clone();
    }
    /** Begins a semantic declaration with exact input text. */
    public static Builder builder(Mode mode, String content) { return new Builder(mode, content); }
    /**
     * Begins raw ECC200 data ending in ASCII state, excluding generated padding
     * and ECC. The array is copied; typed headers and text encoding cannot also
     * be selected. Supported raw ECI controls describe already compacted bytes.
     * Automatic sizing preserves explicit EDIFACT termination by reserving enough
     * data capacity; an incompatible fixed size fails before publication.
     */
    public static Builder rawDataMatrix(int... words) {
        Builder builder = new Builder(Mode.DATA_MATRIX, "");
        builder.dataMatrixEncoding = DataMatrixEncoding.RAW;
        builder.rawCodewords = Objects.requireNonNull(words, "words").clone();
        return builder;
    }
    /**
     * Begins raw PDF417 data; descriptor, padding and ECC are generated, and the
     * array is copied. Embedded Macro controls are rejected; select a typed
     * Macro separately. Supported raw ECI controls describe already compacted bytes.
     * Charset ECI controls preserve the active 901/924 byte-compaction mode;
     * an explicit mode latch is required to resume text compaction.
     */
    public static Builder rawPdf417(int... words) {
        Builder builder = new Builder(Mode.PDF417, "");
        builder.pdf417Encoding = Pdf417Encoding.RAW;
        builder.rawCodewords = Objects.requireNonNull(words,"words").clone();
        return builder;
    }
    /** @return declaration version */
    public int getVersion() { return VERSION_1; }
    /** @return symbol family */
    public Mode getMode() { return mode; }
    /** @return exact caller text */
    public String getContent() { return content; }
    /** @return the recorded character-set name or {@code ECI:number}; no canonicalization */
    public String getEncoding() { return encoding; }
    /** @return exact QR correction level */
    public QrErrorCorrection getQrErrorCorrection() { return qrErrorCorrection; }
    /** @return QR version, or zero for automatic sizing */
    public int getQrVersion() { return qrVersion; }
    /** @return DataMatrix width in modules, or zero for automatic selection */
    public int getDataMatrixWidth() { return dataMatrixWidth; }
    /** @return DataMatrix height in modules, or zero for automatic selection */
    public int getDataMatrixHeight() { return dataMatrixHeight; }
    /** @return explicit DataMatrix compaction choice */
    public DataMatrixEncoding getDataMatrixEncoding() { return dataMatrixEncoding; }
    /** @return a defensive copy of caller-supplied raw data codewords */
    public int[] getRawCodewords() { return rawCodewords.clone(); }
    /** @return raw codeword count without making a copy */
    public int getRawCodewordCount() { return rawCodewords.length; }
    /** @return the selected DataMatrix macro envelope */
    public DataMatrixMacro getDataMatrixMacro() { return dataMatrixMacro; }
    /** @return whether a leading DataMatrix FNC1 is emitted */
    public boolean isDataMatrixFnc1() { return dataMatrixFnc1; }
    /** @return whether the DataMatrix reader programming header is emitted */
    public boolean isDataMatrixReaderProgramming() { return dataMatrixReaderProgramming; }
    /** @return one-based sequence position, or zero when absent */
    public int getDataMatrixSequencePosition() { return dataMatrixSequencePosition; }
    /** @return sequence total, or zero when absent */
    public int getDataMatrixSequenceTotal() { return dataMatrixSequenceTotal; }
    /** @return structured append file identifier, or zero when absent */
    public int getDataMatrixFileId() { return dataMatrixFileId; }
    /** @return PDF417 data columns, or zero for automatic sizing */
    public int getPdf417Columns() { return pdf417Columns; }
    /** @return PDF417 rows, or zero for automatic sizing */
    public int getPdf417Rows() { return pdf417Rows; }
    /** @return exact PDF417 ECC level 0–8, or -1 for automatic selection */
    public int getPdf417ErrorCorrection() { return pdf417ErrorCorrection; }
    /** @return the PDF417 compaction choice */
    public Pdf417Encoding getPdf417Encoding() { return pdf417Encoding; }
    /** @return requested physical matrix height/width, or zero for compact automatic sizing */
    public double getPdf417AspectRatio() { return pdf417AspectRatio; }
    /** @return Macro file identifier as concatenated numeric triplets 000–899, or empty when absent */
    public String getPdf417MacroFileId() { return pdf417MacroFileId; }
    /** @return zero-based Macro segment index */
    public int getPdf417MacroSegment() { return pdf417MacroSegment; }
    /** @return Macro segment count, or zero when absent */
    public int getPdf417MacroCount() { return pdf417MacroCount; }
    /** @return module width in points */
    public double getModuleWidth() { return moduleWidth; }
    /** @return module or PDF417 row height in points */
    public double getModuleHeight() { return moduleHeight; }
    /** @return blank margin on every side in points */
    public double getQuietZone() { return quietZone; }
    /** @return a defensive copy of the DeviceRGB foreground components */
    public double[] getForegroundRgb() { return foregroundRgb.clone(); }
    /** Builds immutable declaration snapshots without encoding. */
    public static final class Builder {
        private final Mode mode;
        private final String content;
        private String encoding = "ISO-8859-1";
        private QrErrorCorrection qrErrorCorrection = QrErrorCorrection.L;
        private int qrVersion;
        private int dataMatrixWidth;
        private int dataMatrixHeight;
        private DataMatrixEncoding dataMatrixEncoding = DataMatrixEncoding.AUTO;
        private int[] rawCodewords = new int[0];
        private DataMatrixMacro dataMatrixMacro = DataMatrixMacro.NONE;
        private boolean dataMatrixFnc1;
        private boolean dataMatrixReaderProgramming;
        private int dataMatrixSequencePosition;
        private int dataMatrixSequenceTotal;
        private int dataMatrixFileId;
        private int pdf417Columns;
        private int pdf417Rows;
        private int pdf417ErrorCorrection = -1;
        private Pdf417Encoding pdf417Encoding = Pdf417Encoding.AUTO;
        private double pdf417AspectRatio;
        private String pdf417MacroFileId = "";
        private int pdf417MacroSegment;
        private int pdf417MacroCount;
        private double moduleWidth = 1;
        private double moduleHeight = 1;
        private double quietZone = 4;
        private double[] foregroundRgb = {0,0,0};
        private Builder(Mode mode, String content) {
            this.mode = Objects.requireNonNull(mode, "mode");
            this.content = Objects.requireNonNull(content, "content");
            if (mode == Mode.DATA_MATRIX) { quietZone = 1; }
            if (mode == Mode.PDF417) { quietZone = 2; moduleHeight = 3; }
        }
        /**
         * Selects Cp437/IBM437, Shift_JIS, UTF-8, or ISO-8859-1 through -11 and
         * -13 through -16. Names ignore case and normalize underscores to hyphens.
         * This replaces any ECI selection. Unrepresentable or lossy text fails execution.
         * @return this builder
         */
        public Builder encoding(String value) { encoding = Objects.requireNonNull(value, "encoding"); return this; }
        /**
         * Selects charset ECI 2, 3–13, 15–18, 20 or 26, replacing any encoding
         * selection. Default ECI 3 omits its redundant marker.
         * @return this builder
         */
        public Builder eci(int value) { encoding = "ECI:" + value; return this; }
        /** Sets exact QR error correction. @return this builder */
        public Builder qrErrorCorrection(QrErrorCorrection value) { qrErrorCorrection = Objects.requireNonNull(value, "qrErrorCorrection"); return this; }
        /** Sets QR version 1–40, or zero for the smallest fitting version. @return this builder */
        public Builder qrVersion(int value) { qrVersion = value; return this; }
        /** Sets exact ECC200 dimensions in modules; a zero dimension is selected automatically. @return this builder */
        public Builder dataMatrixSize(int width, int height) { dataMatrixWidth = width; dataMatrixHeight = height; return this; }
        /** Sets DataMatrix compaction. @return this builder */
        public Builder dataMatrixEncoding(DataMatrixEncoding value) { dataMatrixEncoding = Objects.requireNonNull(value, "dataMatrixEncoding"); return this; }
        /** Adds the selected envelope around the exact input payload. @return this builder */
        public Builder dataMatrixMacro(DataMatrixMacro value) { dataMatrixMacro = Objects.requireNonNull(value, "dataMatrixMacro"); return this; }
        /** Emits a leading FNC1 without interpreting or rewriting the payload. @return this builder */
        public Builder dataMatrixFnc1(boolean value) { dataMatrixFnc1 = value; return this; }
        /** Emits the reader programming control header. @return this builder */
        public Builder dataMatrixReaderProgramming(boolean value) { dataMatrixReaderProgramming = value; return this; }
        /**
         * Selects position 1–total, total 2–16, and file identifier 1–64516;
         * all zero disables it. Migrating a zero-based file ID requires adding one.
         * @return this builder
         */
        public Builder dataMatrixStructuredAppend(int position, int total, int fileId) {
            dataMatrixSequencePosition = position; dataMatrixSequenceTotal = total; dataMatrixFileId = fileId; return this;
        }
        /** Sets PDF417 data columns 1–30, or zero for automatic selection. @return this builder */
        public Builder pdf417Columns(int value) { pdf417Columns = value; return this; }
        /** Sets PDF417 rows 3–90, or zero for automatic selection. @return this builder */
        public Builder pdf417Rows(int value) { pdf417Rows = value; return this; }
        /** Sets exact PDF417 ECC level 0–8, or -1 for automatic selection. @return this builder */
        public Builder pdf417ErrorCorrection(int value) { pdf417ErrorCorrection = value; return this; }
        /** Selects PDF417 compaction. @return this builder */
        public Builder pdf417Encoding(Pdf417Encoding value) { pdf417Encoding = Objects.requireNonNull(value,"pdf417Encoding"); return this; }
        /** Selects the closest fitting matrix height/width, then smallest codeword area; excludes fixed rows/columns. @return this builder */
        public Builder pdf417AspectRatio(double value) { pdf417AspectRatio = value; return this; }
        /** Selects a numeric-triplet file identifier, segment 0–count-1 and count 1–99999, including single-segment Macro files. @return this builder */
        public Builder pdf417Macro(String fileId, int segment, int count) {
            pdf417MacroFileId = Objects.requireNonNull(fileId,"fileId"); pdf417MacroSegment = segment; pdf417MacroCount = count; return this;
        }
        /** Sets module width in points; dimensions do not implicitly rescale each other. @return this builder */
        public Builder moduleWidth(double value) { moduleWidth = value; return this; }
        /** Sets module/row height in points; QR modules must remain square. @return this builder */
        public Builder moduleHeight(double value) { moduleHeight = value; return this; }
        /**
         * Sets the margin on all sides: at least four QR module widths, one
         * DataMatrix module width/height (the larger), or two PDF417 module widths.
         * Other dimensions are not implicitly rescaled, and no background is painted.
         * @return this builder
         */
        public Builder quietZone(double value) { quietZone = value; return this; }
        /** Sets DeviceRGB foreground components in [0,1]; sufficient contrast is the caller's choice. @return this builder */
        public Builder foregroundRgb(double red, double green, double blue) { foregroundRgb = new double[] {red,green,blue}; return this; }
        /** @return an immutable snapshot */
        public Barcode2D build() { return new Barcode2D(this); }
    }
}
