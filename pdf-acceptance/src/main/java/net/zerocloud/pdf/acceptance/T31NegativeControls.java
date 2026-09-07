package net.zerocloud.pdf.acceptance;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.reedsolomon.GenericGF;
import com.google.zxing.common.reedsolomon.ReedSolomonEncoder;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.PageObjectReference;

/** Deliberate, retained corruptions of real public Workflow PDFs, using only DocumentPatch. */
final class T31NegativeControls {
    private T31NegativeControls() { }
    static List<Control> create(Path source,Path artifacts) throws Exception {
        T31BarcodeAssertions.Observation original=T31BarcodeAssertions.inspect(source);
        T31BarcodeAssertions.require(original.passed,"Negative controls require a passing source");
        List<T31BarcodeProfile.Fixture> fixtures=T31BarcodeProfile.fixtures();
        List<Control> controls=new ArrayList<Control>();
        String[][] declarations={
            {"qr-payload","qr-numeric","QR payload mismatch"},
            {"qr-data","qr-numeric","QR damaged data/ECC"},
            {"qr-ecc","qr-numeric","QR damaged data/ECC"},
            {"qr-finder","qr-numeric","QR finder/separator"},
            {"qr-timing","qr-numeric","QR timing"},
            {"qr-format","qr-numeric","QR exact duplicated format BCH"},
            {"qr-quiet","qr-numeric","Paint outside matrix/inside quiet zone"},
            {"qr-move","qr-numeric","placement component"},
            {"qr-scale","qr-numeric","placement component"},
            {"qr-rotation","qr-numeric","placement component"},
            {"qr-box","qr-numeric","Form bounds"},
            {"dm-finder","dm-size-10x10","DataMatrix top timing"},
            {"dm-timing","dm-size-10x10","DataMatrix top timing"},
            {"dm-ecc","dm-size-10x10","DataMatrix damaged data/ECC"},
            {"dm-padding","dm-size-12x12","DataMatrix randomized padding"},
            {"pdf-start","pdf-fixed","PDF417 start/stop"},
            {"pdf-cluster","pdf-fixed","PDF417 row cluster"},
            {"pdf-ecc","pdf-fixed","PDF417 damaged data/ECC"},
            {"pdf-indicator","pdf-fixed","PDF417 left row indicator"}
        };
        for (String[] declaration : declarations) {
            int index=find(fixtures,declaration[1]);
            T31BarcodeProfile.Fixture fixture=fixtures.get(index);
            Path changed=artifacts.resolve("T31-negative-"+declaration[0]+".pdf");
            T31BarcodeAssertions.require(!Files.exists(changed),"Existing T31 negative evidence must be preserved");
            BitMatrix matrix=original.matrices.get(index).clone();
            String id=declaration[0];
            if (id.equals("qr-payload")) { matrix=original.matrices.get(find(fixtures,"qr-alphanumeric")).clone(); }
            else if (id.equals("qr-data")) { matrix.flip(20,20); }
            else if (id.equals("qr-ecc")) { matrix.flip(0,9); }
            else if (id.endsWith("finder") || id.equals("pdf-start")) { matrix.flip(0,0); }
            else if (id.equals("qr-timing")) { matrix.flip(6,10); }
            else if (id.equals("qr-format")) { matrix.flip(8,0); }
            else if (id.equals("dm-timing")) { matrix.flip(2,0); }
            else if (id.equals("dm-ecc")) { flipDmEcc(matrix); }
            else if (id.equals("dm-padding")) { matrix=invalidPadding(matrix); }
            else if (id.equals("pdf-cluster")) { copyWord(matrix,34,0,34,1); }
            else if (id.equals("pdf-ecc")) { copyWord(matrix,34,1,34,7); }
            else if (id.equals("pdf-indicator")) { copyWord(matrix,34,0,17,0); }
            String paths=paths(matrix,fixture)+(id.equals("qr-quiet") ? "0 0 1 1 re f\n" : "");
            final int page=index+1;
            new DocumentWorkflow().execute(WorkflowRequest.builder().source("source",DocumentSource.path(source)).primarySource("source")
                    .target("output",PublicationTarget.path(changed)).saveMode(SaveMode.REWRITE).build(),session -> {
                        PdfDictionary dictionary=T31BarcodeAssertions.page(session,page);
                        if (id.equals("qr-move") || id.equals("qr-scale") || id.equals("qr-rotation")) {
                            String prefix=id.equals("qr-move") ? "1 0 0 1 1 0 cm\n" : id.equals("qr-scale") ? "1.125 0 0 1 0 0 cm\n" : "0 1 -1 0 500 0 cm\n";
                            String content=prefix+T31BarcodeAssertions.streamText(session,dictionary.get(PdfName.of("Contents")));
                            session.execute(DocumentPatch.builder().setDictionaryEntry(session.query(PageObjectReference.version1(page)),PdfName.of("Contents"),
                                    PdfStream.of(PdfDictionary.builder().build(),content.getBytes(StandardCharsets.US_ASCII))).build());
                        } else {
                            PdfDictionary resources=(PdfDictionary)T31BarcodeAssertions.resolve(session,dictionary.get(PdfName.of("Resources")));
                            PdfDictionary objects=(PdfDictionary)T31BarcodeAssertions.resolve(session,resources.get(PdfName.of("XObject")));
                            PdfName name=objects.getEntry(0).getName();
                            PdfDictionary form=PdfDictionary.builder().put(PdfName.of("Type"),PdfName.of("XObject"))
                                    .put(PdfName.of("Subtype"),PdfName.of("Form")).put(PdfName.of("FormType"),PdfNumber.of(1))
                                    .put(PdfName.of("BBox"),PdfArray.of(PdfNumber.of(0),PdfNumber.of(0),PdfNumber.of((long)(id.equals("qr-box") ? 17 : fixture.widthPoints())),
                                            PdfNumber.of((long)fixture.heightPoints()))).put(PdfName.of("Resources"),PdfDictionary.builder().build()).build();
                            PdfStream stream=PdfStream.of(form,paths.getBytes(StandardCharsets.US_ASCII));
                            PdfDictionary replaced=PdfDictionary.builder().put(PdfName.of("XObject"),PdfDictionary.builder().put(name,stream).build()).build();
                            session.execute(DocumentPatch.builder().setDictionaryEntry(session.query(PageObjectReference.version1(page)),PdfName.of("Resources"),replaced).build());
                        }
                        return null;
                    });
            controls.add(new Control(id,changed,page,fixture,declaration[2]));
        }
        return controls;
    }
    private static int find(List<T31BarcodeProfile.Fixture> fixtures,String id) {
        for (int index=0;index<fixtures.size();index++) { if (fixtures.get(index).id.equals(id)) { return index; } }
        throw new IllegalArgumentException("Unknown negative fixture: "+id);
    }
    private static void flipDmEcc(BitMatrix matrix) throws Exception {
        byte[] words=new T31DataMatrixBitMatrixParser(matrix).readCodewords();
        for (int y=1;y<matrix.getHeight()-1;y++) { for (int x=1;x<matrix.getWidth()-1;x++) {
            BitMatrix candidate=matrix.clone(); candidate.flip(x,y);
            byte[] altered=new T31DataMatrixBitMatrixParser(candidate).readCodewords();
            boolean dataUnchanged=true,eccChanged=false;
            for (int index=0;index<words.length;index++) {
                if (words[index]!=altered[index]) { if (index<3) { dataUnchanged=false; } else { eccChanged=true; } }
            }
            if (dataUnchanged && eccChanged) { matrix.flip(x,y); return; }
        } }
        throw new IllegalArgumentException("Independent placement did not locate an ECC bit");
    }
    private static void copyWord(BitMatrix matrix,int sourceX,int sourceY,int targetX,int targetY) {
        boolean changed=false;
        for (int bit=0;bit<17;bit++) {
            boolean value=matrix.get(sourceX+bit,sourceY); changed|=value!=matrix.get(targetX+bit,targetY);
            if (value) { matrix.set(targetX+bit,targetY); } else { matrix.unset(targetX+bit,targetY); }
        }
        T31BarcodeAssertions.require(changed,"Negative codeword replacement was a no-op");
    }
    private static String paths(BitMatrix matrix,T31BarcodeProfile.Fixture fixture) {
        StringBuilder result=new StringBuilder("0 g\n");
        for (int y=0;y<fixture.height;y++) { for (int x=0;x<fixture.width;x++) { if (matrix.get(x,y)) {
            result.append(fixture.barcode.getQuietZone()+x*fixture.barcode.getModuleWidth()).append(' ')
                    .append(fixture.barcode.getQuietZone()+(fixture.height-1-y)*fixture.barcode.getModuleHeight()).append(' ')
                    .append(fixture.barcode.getModuleWidth()).append(' ').append(fixture.barcode.getModuleHeight()).append(" re f\n");
        } } }
        return result.toString();
    }
    /** Original negative fixture; independent ZXing placement and RS encoder only. */
    static BitMatrix invalidPadding(BitMatrix original) throws Exception {
        byte[] raw=new T31DataMatrixBitMatrixParser(original).readCodewords();
        T31BarcodeAssertions.require(raw.length==12,"Expected independent 12x12 ECC200 fixture");
        int[] changed=new int[raw.length]; for (int index=0;index<raw.length;index++) { changed[index]=raw[index]&255; }
        changed[3]^=1;
        new ReedSolomonEncoder(GenericGF.DATA_MATRIX_FIELD_256).encode(changed,7);
        BitMatrix result=original.clone();
        for (int y=1;y<11;y++) { for (int x=1;x<11;x++) {
            BitMatrix candidate=original.clone(); candidate.flip(x,y);
            byte[] words=new T31DataMatrixBitMatrixParser(candidate).readCodewords();
            for (int index=0;index<words.length;index++) {
                int mask=(raw[index]^words[index])&255;
                if (mask!=0 && ((changed[index]^(raw[index]&255))&mask)!=0) { result.flip(x,y); }
            }
        } }
        return result;
    }
    static final class Control {
        final String id,finding;
        final Path pdf;
        final int page;
        final T31BarcodeProfile.Fixture fixture;
        Control(String id,Path pdf,int page,T31BarcodeProfile.Fixture fixture,String finding) {
            this.id=id; this.pdf=pdf; this.page=page; this.fixture=fixture; this.finding=finding;
        }
    }
}
