package net.zerocloud.pdf.acceptance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.zerocloud.pdf.composition.Barcode2D;
import net.zerocloud.pdf.composition.Barcode2D.DataMatrixEncoding;
import net.zerocloud.pdf.composition.Barcode2D.DataMatrixMacro;
import net.zerocloud.pdf.composition.Barcode2D.Pdf417Encoding;
import net.zerocloud.pdf.composition.Barcode2D.QrErrorCorrection;
import net.zerocloud.pdf.composition.CanvasMatrix;

/** Literal inputs and geometric expectations, declared independently of product output. */
final class T31BarcodeProfile {
    static final CanvasMatrix TRANSLATE = CanvasMatrix.of(1,0,0,1,36,144);
    private T31BarcodeProfile() { }

    static List<Fixture> fixtures() {
        List<Fixture> result = new ArrayList<Fixture>();
        String[] qrText = {"01234567", "FOLIO 31", "folio-31", "漢字"};
        String[] qrId = {"numeric", "alphanumeric", "byte", "kanji"};
        int[] qrModes = {1,2,4,8};
        for (int index = 0; index < qrText.length; index++) {
            result.add(qr("qr-" + qrId[index], qrText[index], index == 3 ? "Shift_JIS" : "ISO-8859-1", 1,
                    QrErrorCorrection.L, index == 3 ? 20 : 3, qrModes[index]));
        }
        for (QrErrorCorrection ecc : QrErrorCorrection.values()) {
            result.add(qr("qr-ecc-" + ecc, "FOLIO 31", "ISO-8859-1", 1, ecc, 3, 2));
        }
        for (int version = 1; version <= 40; version++) {
            result.add(qr("qr-version-" + version, "31", "ISO-8859-1", version, QrErrorCorrection.L, 3, 1));
        }
        result.add(qr("qr-capacity", repeat("1",7089), "ISO-8859-1",40,QrErrorCorrection.L,3,1));
        result.add(qr("qr-utf8", "Folio 二维 😀", "UTF-8",3,QrErrorCorrection.Q,26,4));

        int[][] sizes = {{10,10},{12,12},{18,8},{14,14},{32,8},{16,16},{26,12},{18,18},{20,20},{36,12},
                {22,22},{36,16},{24,24},{26,26},{48,16},{32,32},{36,36},{40,40},{44,44},{48,48},{52,52},
                {64,64},{72,72},{80,80},{88,88},{96,96},{104,104},{120,120},{132,132},{144,144}};
        for (int[] size : sizes) {
            result.add(dm("dm-size-" + size[0] + "x" + size[1], dmBuilder("AB",DataMatrixEncoding.ASCII)
                    .dataMatrixSize(size[0],size[1]),"AB",size[0],size[1],3,66,67));
        }
        DataMatrixEncoding[] modes = DataMatrixEncoding.values();
        String[] dmText = {"ABC123", "123456", "ABCABC", "abcabc", "ABC>12", "ABCD12", "\u0000\u0080\u00ff", "AB"};
        int[] latch = {-1,142,230,239,238,240,231,66};
        for (int index = 0; index < modes.length; index++) {
            Barcode2D.Builder builder = modes[index] == DataMatrixEncoding.RAW ? Barcode2D.rawDataMatrix(66,67)
                    : dmBuilder(dmText[index],modes[index]);
            result.add(dm("dm-" + modes[index].name().toLowerCase(java.util.Locale.ROOT),builder.dataMatrixSize(16,16),
                    dmText[index],16,16,3,latch[index]));
        }
        result.add(dm("dm-auto-size",dmBuilder("AB",DataMatrixEncoding.ASCII),"AB",10,10,3,66,67,129));
        result.add(dm("dm-raw-edifact-auto",Barcode2D.rawDataMatrix(240,5,240),"A",12,12,3,240,5,240));
        result.add(dm("dm-raw-edifact-fixed",Barcode2D.rawDataMatrix(240,5,240).dataMatrixSize(12,12),"A",12,12,3,240,5,240));
        result.add(dm("dm-raw-edifact-ascii",Barcode2D.rawDataMatrix(240,124,66),"A",12,12,3,240,124,66));
        result.add(dm("dm-raw-edifact-tail",Barcode2D.rawDataMatrix(66,67,240,4,32,196,5,240),"ABABCDA",32,8,3,66,67,240,4,32,196,5,240));
        result.add(dm("dm-width",dmBuilder("AB",DataMatrixEncoding.ASCII).dataMatrixSize(18,0),"AB",18,8,3,66,67));
        result.add(dm("dm-height",dmBuilder("AB",DataMatrixEncoding.ASCII).dataMatrixSize(0,8),"AB",18,8,3,66,67));
        result.add(dm("dm-capacity",dmBuilder(repeat("A",1558),DataMatrixEncoding.ASCII).dataMatrixSize(144,144),repeat("A",1558),144,144,3,66));
        for (DataMatrixEncoding mode : new DataMatrixEncoding[] {DataMatrixEncoding.ASCII,DataMatrixEncoding.C40,
                DataMatrixEncoding.TEXT,DataMatrixEncoding.BASE256}) {
            result.add(dm("dm-all-bytes-" + mode,dmBuilder(allBytes(),mode).dataMatrixSize(88,88),allBytes(),88,88,3,-1));
        }
        for (int macro : new int[] {5,6}) {
            String prefix = "[)>\u001e0" + macro + "\u001d";
            DataMatrixMacro kind = macro == 5 ? DataMatrixMacro.MACRO_05 : DataMatrixMacro.MACRO_06;
            result.add(dm("dm-macro-" + macro,dmBuilder("ABC",DataMatrixEncoding.AUTO).dataMatrixMacro(kind).dataMatrixSize(26,26),
                    prefix + "ABC\u001e\u0004",26,26,3,macro == 5 ? 236 : 237));
            result.add(dm("dm-macro-fnc1-" + macro,dmBuilder("ABC",DataMatrixEncoding.AUTO).dataMatrixMacro(kind)
                    .dataMatrixFnc1(true).dataMatrixSize(26,26),prefix + "\u001dABC\u001e\u0004",26,26,3,macro == 5 ? 236 : 237,232));
            result.add(dm("dm-macro-utf8-" + macro,dmBuilder("维😀",DataMatrixEncoding.AUTO).encoding("UTF-8")
                    .dataMatrixMacro(kind).dataMatrixSize(26,26),prefix + "维😀\u001e\u0004",26,26,26,macro == 5 ? 236 : 237,241,27));
        }
        result.add(dm("dm-fnc1",dmBuilder("0101234567890128",DataMatrixEncoding.AUTO).dataMatrixFnc1(true)
                .dataMatrixSize(26,26),"\u001d0101234567890128",26,26,3,232));
        result.add(dm("dm-reader",dmBuilder("ABC",DataMatrixEncoding.AUTO).dataMatrixReaderProgramming(true)
                .dataMatrixSize(26,26),"ABC",26,26,3,234));
        int[][] sequence = {{1,2,75},{2,2,75},{16,16,64516},{1,16,1}};
        for (int index = 0; index < sequence.length; index++) {
            int[] value = sequence[index];
            result.add(dm(index == 2 ? "dm-sequence-max" : "dm-sequence-" + index,
                    dmBuilder("PART" + (index+1),DataMatrixEncoding.ASCII).dataMatrixStructuredAppend(value[0],value[1],value[2])
                            .dataMatrixSize(26,26),"PART" + (index+1),26,26,3,233,((value[0]-1)<<4)+(17-value[1]),
                    (value[2]-1)/254+1,(value[2]-1)%254+1));
        }

        result.add(pdf("pdf-fixed",pdfBuilder("FOLIO 31").pdf417Columns(3).pdf417Rows(8),"FOLIO 31",3,8,2,3));
        result.add(pdf("pdf-numeric",pdfBuilder("12345678901234567890123456789012345678901234")
                .pdf417Columns(5).pdf417Rows(12),"12345678901234567890123456789012345678901234",5,12,2,3,902));
        result.add(pdf("pdf-binary",pdfBuilder(allBytes()).pdf417Encoding(Pdf417Encoding.BINARY).pdf417Columns(8).pdf417Rows(32),
                allBytes(),8,32,2,3,901));
        result.add(pdf("pdf-raw",Barcode2D.rawPdf417(1).pdf417ErrorCorrection(2).pdf417Columns(3).pdf417Rows(8),"AB",3,8,2,3,1));
        result.add(pdf("pdf-raw-numeric",Barcode2D.rawPdf417(902,1,624,434,632,282,200).pdf417ErrorCorrection(2)
                .pdf417Columns(3).pdf417Rows(8),"000213298174000",3,8,2,3,902,1,624,434,632,282,200));
        result.add(pdf("pdf-raw-binary",Barcode2D.rawPdf417(901,0,128,255).pdf417ErrorCorrection(2)
                .pdf417Columns(3).pdf417Rows(8),"\u0000\u0080\u00ff",3,8,2,3,901,0,128,255));
        result.add(rawPdf("pdf-raw-eci-leading","A",901,927,26,65));
        result.add(rawPdf("pdf-raw-eci-literal","AB",901,65,927,26,66));
        result.add(rawPdf("pdf-raw-eci-charset","éé",901,927,26,195,169,927,3,233));
        result.add(rawPdf("pdf-raw-eci-924-leading","\u0000\u0000\u0000\u0000\u0000A",924,927,26,0,0,0,0,65));
        result.add(rawPdf("pdf-raw-eci-924-between","\u0000\u0000\u0000\u0000\u0000A\u0000\u0000\u0000\u0000\u0000B",924,0,0,0,0,65,927,26,0,0,0,0,66));
        result.add(rawPdf("pdf-raw-eci-text-latch","ABAB",901,65,927,26,66,900,1));
        result.add(rawPdf("pdf-raw-eci-consecutive","A",901,927,26,927,3,65));
        result.add(rawPdf("pdf-raw-eci-group","\u0000\u0000\u0000\u0000\u0001,A",901,927,26,0,0,0,0,300,65));
        for (int ecc = 0; ecc <= 8; ecc++) {
            result.add(pdf("pdf-ecc-" + ecc,pdfBuilder("AB").pdf417ErrorCorrection(ecc).pdf417Columns(10).pdf417Rows(60),"AB",10,60,ecc,3,1));
        }
        for (Pdf417Encoding mode : Pdf417Encoding.values()) {
            for (int segment = 0; segment < 2; segment++) {
                Barcode2D.Builder builder = mode == Pdf417Encoding.RAW ? Barcode2D.rawPdf417(1) : pdfBuilder("PART"+(segment+1)).pdf417Encoding(mode);
                result.add(pdf("pdf-macro-" + mode + "-" + segment,builder.pdf417Macro("001075",segment,2)
                        .pdf417ErrorCorrection(2).pdf417Columns(5).pdf417Rows(12),mode == Pdf417Encoding.RAW ? "AB" : "PART"+(segment+1),5,12,2,3));
            }
        }
        result.add(pdf("pdf-macro-single",pdfBuilder("维😀").encoding("UTF-8").pdf417Macro("000899",0,1)
                .pdf417Columns(5).pdf417Rows(12),"维😀",5,12,2,26,927,26));
        result.add(pdf("pdf-macro-max",pdfBuilder("AB").pdf417Macro("000899",99998,99999)
                .pdf417Columns(5).pdf417Rows(12),"AB",5,12,2,3));
        result.add(pdf("pdf-auto-ecc",Barcode2D.builder(Barcode2D.Mode.PDF417,"AB").pdf417Columns(3).pdf417Rows(8),"AB",3,8,2,3));
        result.add(pdf("pdf-auto-ecc0",pdfBuilder("AB").pdf417ErrorCorrection(0),"AB",1,4,0,3));
        result.add(pdf("pdf-columns",pdfBuilder("AB").pdf417ErrorCorrection(0).pdf417Columns(2),"AB",2,3,0,3));
        result.add(pdf("pdf-rows",pdfBuilder("AB").pdf417ErrorCorrection(0).pdf417Rows(8),"AB",1,8,0,3));
        for (double ratio : new double[] {0.25,0.5,1}) {
            result.add(pdf("pdf-aspect-" + ratio,pdfBuilder("AB").pdf417ErrorCorrection(0).pdf417AspectRatio(ratio),"AB",3,(int)(40*ratio),0,3));
        }
        int[] capacity = new int[925]; Arrays.fill(capacity,1);
        result.add(pdf("pdf-raw-capacity",Barcode2D.rawPdf417(capacity).pdf417ErrorCorrection(0),repeat("AB",925),16,58,0,3));
        result.add(pdf("pdf-capacity",pdfBuilder(repeat("AB",925)).pdf417ErrorCorrection(0),repeat("AB",925),16,58,0,3));

        String[] encodings = {"Cp437","Shift_JIS","ISO-8859-1","ISO-8859-2","ISO-8859-3","ISO-8859-4",
                "ISO-8859-5","ISO-8859-6","ISO-8859-7","ISO-8859-8","ISO-8859-9","ISO-8859-10",
                "ISO-8859-11","ISO-8859-13","ISO-8859-14","ISO-8859-15","ISO-8859-16","UTF-8"};
        String[] texts = {"Folio é","漢字","Folio é","Folio Ł","Folio Ħ","Folio ĸ","Folio Ж","Folio ع",
                "Folio Ω","Folio א","Folio ğ","Folio ĸŊ","Folio ก","Folio Ė","Folio Ẁ","Folio €","Folio Ș","Folio 二维 😀"};
        int[] ecis = {2,20,3,4,5,6,7,8,9,10,11,12,13,15,16,17,18,26};
        for (int index = 0; index < encodings.length; index++) {
            String encoding = encodings[index], text = texts[index]; int eci = ecis[index];
            result.add(qr("qr-charset-" + encoding,text,encoding,2,QrErrorCorrection.L,eci,index == 1 ? 8 : 4));
            result.add(dm("dm-charset-" + encoding,dmBuilder(text,DataMatrixEncoding.ASCII).encoding(encoding)
                    .dataMatrixSize(26,26),text,26,26,eci,eci == 3 ? -1 : 241));
            result.add(pdf("pdf-charset-" + encoding,pdfBuilder(text).encoding(encoding).pdf417Columns(5).pdf417Rows(12),text,5,12,2,eci));
        }
        for (Barcode2D.Mode mode : Barcode2D.Mode.values()) {
            int width = mode == Barcode2D.Mode.QR ? 21 : mode == Barcode2D.Mode.DATA_MATRIX ? 10 : 120;
            int height = mode == Barcode2D.Mode.QR ? 21 : mode == Barcode2D.Mode.DATA_MATRIX ? 10 : 8;
            CanvasMatrix[] placements = {CanvasMatrix.IDENTITY,TRANSLATE,CanvasMatrix.of(0,1,-1,0,240,72),CanvasMatrix.of(2,0,0,0.5,24,96)};
            for (int index = 0; index < placements.length; index++) {
                result.add(new Fixture("placement-" + mode + "-" + index,base(mode).build(),"AB",width,height,3,2,0,new int[0],placements[index]));
            }
            result.add(new Fixture("units-" + mode,base(mode).moduleWidth(2).moduleHeight(mode == Barcode2D.Mode.PDF417 ? 8 : mode == Barcode2D.Mode.DATA_MATRIX ? 3 : 2)
                    .quietZone(12).build(),"AB",width,height,3,2,0,new int[0],TRANSLATE));
            result.add(new Fixture("color-" + mode,base(mode).foregroundRgb(0.125,0.25,0.375).build(),"AB",width,height,3,2,0,new int[0],TRANSLATE));
        }
        return Collections.unmodifiableList(result);
    }

    private static Barcode2D.Builder base(Barcode2D.Mode mode) {
        Barcode2D.Builder builder = Barcode2D.builder(mode,"AB");
        if (mode == Barcode2D.Mode.QR) { builder.qrVersion(1); }
        if (mode == Barcode2D.Mode.DATA_MATRIX) { builder.dataMatrixEncoding(DataMatrixEncoding.ASCII).dataMatrixSize(10,10); }
        if (mode == Barcode2D.Mode.PDF417) { builder.pdf417Columns(3).pdf417Rows(8).pdf417ErrorCorrection(2); }
        return builder;
    }
    private static Barcode2D.Builder dmBuilder(String value, DataMatrixEncoding mode) {
        return Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX,value).dataMatrixEncoding(mode);
    }
    private static Barcode2D.Builder pdfBuilder(String value) {
        return Barcode2D.builder(Barcode2D.Mode.PDF417,value).pdf417ErrorCorrection(2);
    }
    private static Fixture qr(String id,String text,String encoding,int version,QrErrorCorrection ecc,int eci,int mode) {
        return new Fixture(id,Barcode2D.builder(Barcode2D.Mode.QR,text).encoding(encoding).qrVersion(version).qrErrorCorrection(ecc).build(),
                text,17+4*version,17+4*version,eci,0,mode,new int[0],TRANSLATE);
    }
    private static Fixture dm(String id,Barcode2D.Builder barcode,String text,int width,int height,int eci,int... prefix) {
        return new Fixture(id,barcode.build(),text,width,height,eci,0,0,prefix,TRANSLATE);
    }
    private static Fixture rawPdf(String id,String payload,int... words) {
        return pdf(id,Barcode2D.rawPdf417(words).pdf417Columns(3).pdf417Rows(8).pdf417ErrorCorrection(2),
                payload,3,8,2,3,words);
    }
    private static Fixture pdf(String id,Barcode2D.Builder barcode,String text,int columns,int rows,int ecc,int eci,int... prefix) {
        return new Fixture(id,barcode.build(),text,69+17*columns,rows,eci,ecc,0,prefix,TRANSLATE);
    }
    private static String allBytes() { StringBuilder text = new StringBuilder(); for (int value=0;value<256;value++) { text.append((char)value); } return text.toString(); }
    private static String repeat(String value,int count) { StringBuilder result = new StringBuilder(); for (int index=0;index<count;index++) { result.append(value); } return result.toString(); }

    static final class Fixture {
        final String id, payload;
        final Barcode2D barcode;
        final int width, height, eci, pdfEcc, qrMode;
        final int[] prefix;
        final CanvasMatrix placement;
        Fixture(String id,Barcode2D barcode,String payload,int width,int height,int eci,int pdfEcc,int qrMode,int[] prefix,CanvasMatrix placement) {
            this.id=id; this.barcode=barcode; this.payload=payload; this.width=width; this.height=height; this.eci=eci;
            this.pdfEcc=pdfEcc; this.qrMode=qrMode; this.prefix=prefix.clone(); this.placement=placement;
        }
        double widthPoints() { return barcode.getQuietZone()*2+width*barcode.getModuleWidth(); }
        double heightPoints() { return barcode.getQuietZone()*2+height*barcode.getModuleHeight(); }
    }
}
