/*
 * Copyright 2007 ZXing authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations.
 */
package net.zerocloud.pdf.acceptance;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.BitSource;
import com.google.zxing.common.DecoderResult;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.pdf417.PDF417Common;
import com.google.zxing.pdf417.PDF417Reader;
import com.google.zxing.pdf417.PDF417ResultMetadata;
import com.google.zxing.pdf417.decoder.ec.ErrorCorrection;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.decoder.Mode;
import com.google.zxing.qrcode.decoder.Version;
import net.zerocloud.pdf.composition.Barcode2D;
import static net.zerocloud.pdf.acceptance.T31BarcodeAssertions.require;

/**
 * Independent ZXing decoding plus exact structural checks. QR format/version
 * coordinates and traversal are adapted from ZXing 3.5.3 BitMatrixParser;
 * the explicit function-pattern, BCH, remainder and profile assertions are original.
 * No encoder implementation, tables or unqualified product golden is consulted.
 */
final class T31MatrixOracle {
    private T31MatrixOracle() { }

    static String inspect(BitMatrix matrix,T31BarcodeProfile.Fixture fixture) throws Exception {
        require(matrix.getWidth()==fixture.width && matrix.getHeight()==fixture.height,fixture.id + " matrix dimensions");
        String detail;
        switch (fixture.barcode.getMode()) {
            case QR: detail=qr(matrix,fixture); break;
            case DATA_MATRIX: detail=dm(matrix,fixture); break;
            case PDF417: detail=pdf(matrix,fixture); break;
            default: throw new IllegalArgumentException("Unknown profile family");
        }
        return fixture.id + ": payload=" + printable(fixture.payload) + "; " + detail;
    }

    private static String qr(BitMatrix matrix,T31BarcodeProfile.Fixture fixture) throws Exception {
        int n=matrix.getWidth(),number=(n-17)/4;
        Version version=Version.getVersionForNumber(number);
        BitMatrix function=new BitMatrix(n);
        finder(matrix,function,0,0); finder(matrix,function,n-7,0); finder(matrix,function,0,n-7);
        for (int index=8;index<n-8;index++) {
            cell(matrix,function,6,index,index%2==0,"QR timing");
            cell(matrix,function,index,6,index%2==0,"QR timing");
        }
        int[] centers=version.getAlignmentPatternCenters();
        for (int y : centers) { for (int x : centers) {
            if ((x==6 && (y==6 || y==n-7)) || (x==n-7 && y==6)) { continue; }
            for (int dy=-2;dy<=2;dy++) { for (int dx=-2;dx<=2;dx++) {
                cell(matrix,function,x+dx,y+dy,Math.max(Math.abs(dx),Math.abs(dy))!=1,"QR alignment");
            } }
        } }
        cell(matrix,function,8,n-8,true,"QR dark module");
        int[][] first=new int[15][2],second=new int[15][2]; int at=0;
        for (int x=0;x<6;x++) { first[at++]=new int[] {x,8}; }
        first[at++]=new int[] {7,8}; first[at++]=new int[] {8,8}; first[at++]=new int[] {8,7};
        for (int y=5;y>=0;y--) { first[at++]=new int[] {8,y}; }
        at=0;
        for (int y=n-1;y>=n-7;y--) { second[at++]=new int[] {8,y}; }
        for (int x=n-8;x<n;x++) { second[at++]=new int[] {x,8}; }
        int format=read(matrix,first),other=read(matrix,second),data=(format^0x5412)>>>10;
        require(format==other && format==(bch(data,10,0x537)^0x5412),"QR exact duplicated format BCH");
        require((data>>>3)==ErrorCorrectionLevel.valueOf(fixture.barcode.getQrErrorCorrection().name()).getBits(),"QR exact ECC configuration");
        for (int[] point : first) { function.set(point[0],point[1]); }
        for (int[] point : second) { function.set(point[0],point[1]); }
        if (number>=7) {
            int expected=bch(number,12,0x1f25),bit=17;
            for (int y=5;y>=0;y--) { for (int x=n-9;x>=n-11;x--) {
                boolean set=(expected&(1<<bit--))!=0;
                cell(matrix,function,x,y,set,"QR version BCH"); cell(matrix,function,y,x,set,"QR duplicate version BCH");
            } }
        }
        int payloadBits=version.getTotalCodewords()*8,count=0,remainder=0; boolean up=true;
        for (int x=n-1;x>0;x-=2) {
            if (x==6) { x--; }
            for (int step=0;step<n;step++) {
                int y=up ? n-1-step : step;
                for (int delta=0;delta<2;delta++) {
                    if (function.get(x-delta,y)) { continue; }
                    if (count++>=payloadBits) {
                        require(matrix.get(x-delta,y)==masked(data&7,x-delta,y),"QR nonzero remainder module"); remainder++;
                    }
                }
            }
            up=!up;
        }
        require(count>=payloadBits && remainder<=7,"QR complete function/data partition");
        DecoderResult decoded=new com.google.zxing.qrcode.decoder.Decoder().decode(matrix.clone());
        require(Integer.valueOf(0).equals(decoded.getErrorsCorrected()),"QR damaged data/ECC required correction");
        require(fixture.payload.equals(decoded.getText()),"QR payload mismatch");
        require(fixture.barcode.getQrErrorCorrection().name().equals(decoded.getECLevel()),"QR decoded ECC mismatch");
        String segments=qrSegments(decoded.getRawBytes(),version,fixture);
        return "QR version=" + number + "; ECC=" + decoded.getECLevel() + "; corrected=0; mask=" + (data&7)
                + "; exact functions/BCH/remainder=pass; " + segments;
    }

    private static String qrSegments(byte[] bytes,Version version,T31BarcodeProfile.Fixture fixture) {
        BitSource bits=new BitSource(bytes); Set<Integer> modes=new HashSet<Integer>(); int eci=3; boolean ended=false;
        while (bits.available()>=4) {
            int value=bits.readBits(4);
            if (value==0) { ended=true; break; }
            if (value==7) {
                int first=bits.readBits(8);
                eci=(first&128)==0 ? first : (first&192)==128 ? ((first&63)<<8)|bits.readBits(8) : ((first&31)<<16)|bits.readBits(16);
                continue;
            }
            Mode mode=Mode.forBits(value); modes.add(value);
            require(value==1 || value==2 || value==4 || value==8,"Unexpected QR control/segment mode");
            int count=bits.readBits(mode.getCharacterCountBits(version));
            int length=value==1 ? (count/3)*10+(count%3==0 ? 0 : count%3==1 ? 4 : 7)
                    : value==2 ? (count/2)*11+(count%2)*6 : value==4 ? count*8 : count*13;
            skip(bits,length);
        }
        if (ended || bits.available()>0) {
            int alignment=(8-bits.getBitOffset())%8;
            if (alignment>0) { require(bits.readBits(alignment)==0,"QR terminator alignment"); }
            int pad=0xec;
            while (bits.available()>0) { require(bits.readBits(8)==pad,"QR alternating padding"); pad=pad==0xec ? 0x11 : 0xec; }
        }
        require(eci==fixture.eci,"QR ECI mismatch");
        require(fixture.qrMode==0 || modes.contains(fixture.qrMode),"QR required compaction absent");
        return "ECI="+eci+"; modes="+new java.util.TreeSet<Integer>(modes)+"; padding=pass";
    }
    private static void skip(BitSource bits,int count) { require(count<=bits.available(),"Truncated QR segment"); while (count>0) { int step=Math.min(32,count); bits.readBits(step); count-=step; } }
    private static int bch(int value,int shift,int polynomial) {
        int remainder=value<<shift;
        for (int bit=31-Integer.numberOfLeadingZeros(remainder);bit>=shift;bit--) {
            if ((remainder&(1<<bit))!=0) { remainder^=polynomial<<(bit-shift); }
        }
        return (value<<shift)|remainder;
    }
    private static int read(BitMatrix matrix,int[][] coordinates) {
        int result=0; for (int[] point : coordinates) { result=(result<<1)|(matrix.get(point[0],point[1]) ? 1 : 0); } return result;
    }
    private static void finder(BitMatrix matrix,BitMatrix function,int x,int y) {
        for (int dy=-1;dy<=7;dy++) { for (int dx=-1;dx<=7;dx++) {
            if (x+dx<0 || y+dy<0 || x+dx>=matrix.getWidth() || y+dy>=matrix.getHeight()) { continue; }
            boolean inside=dx>=0 && dx<=6 && dy>=0 && dy<=6;
            boolean set=inside && (dx==0 || dx==6 || dy==0 || dy==6 || (dx>=2 && dx<=4 && dy>=2 && dy<=4));
            cell(matrix,function,x+dx,y+dy,set,"QR finder/separator");
        } }
    }
    private static void cell(BitMatrix matrix,BitMatrix function,int x,int y,boolean expected,String label) {
        require(matrix.get(x,y)==expected,label+" at "+x+","+y); function.set(x,y);
    }
    private static boolean masked(int mask,int x,int y) {
        switch (mask) {
            case 0:return (x+y)%2==0; case 1:return y%2==0; case 2:return x%3==0;
            case 3:return (x+y)%3==0; case 4:return (y/2+x/3)%2==0;
            case 5:return (x*y)%2+(x*y)%3==0; case 6:return ((x*y)%2+(x*y)%3)%2==0;
            case 7:return ((x+y)%2+(x*y)%3)%2==0; default:throw new IllegalArgumentException("QR mask");
        }
    }

    private static String dm(BitMatrix matrix,T31BarcodeProfile.Fixture fixture) throws Exception {
        T31DataMatrixVersion version=T31DataMatrixVersion.getVersionForDimensions(matrix.getHeight(),matrix.getWidth());
        int regionWidth=version.getDataRegionSizeColumns()+2,regionHeight=version.getDataRegionSizeRows()+2;
        for (int y=0;y<matrix.getHeight();y+=regionHeight) { for (int x=0;x<matrix.getWidth();x+=regionWidth) {
            for (int dx=0;dx<regionWidth;dx++) {
                require(matrix.get(x+dx,y)==(dx%2==0),"DataMatrix top timing");
                require(matrix.get(x+dx,y+regionHeight-1),"DataMatrix bottom finder");
            }
            for (int dy=0;dy<regionHeight;dy++) {
                require(matrix.get(x,y+dy),"DataMatrix left finder");
                require(matrix.get(x+regionWidth-1,y+dy)==(dy%2==1),"DataMatrix right timing");
            }
        } }
        T31DataMatrixBitMatrixParser parser=new T31DataMatrixBitMatrixParser(matrix);
        parser.readCodewords(); parser.requireUnusedModules();
        DecoderResult decoded=new T31DataMatrixDecoder().decode(matrix);
        require(Integer.valueOf(0).equals(decoded.getErrorsCorrected()),"DataMatrix damaged data/ECC required correction");
        require(fixture.payload.equals(decoded.getText()),"DataMatrix payload mismatch: "+printable(decoded.getText()));
        int[] words=new int[decoded.getRawBytes().length];
        for (int index=0;index<words.length;index++) { words[index]=decoded.getRawBytes()[index]&255; }
        prefix(words,0,fixture.prefix,"DataMatrix requested controls/compaction");
        int at=0;
        if (words[at]==233) { at+=4; }
        while (at<words.length && (words[at]==232 || words[at]==234 || words[at]==236 || words[at]==237)) { at++; }
        int eci=words[at]==241 ? words[at+1]-1 : 3;
        require(eci==fixture.eci,"DataMatrix ECI mismatch");
        T31DataMatrixDecoder.Headers headers=(T31DataMatrixDecoder.Headers)decoded.getOther();
        if (headers.paddingStart>=0) {
            for (int index=headers.paddingStart;index<words.length;index++) {
                int expected=129+((149*(index+1))%253)+1;
                if (expected>254) { expected-=254; }
                require(words[index]==expected,"DataMatrix randomized padding");
            }
        }
        Barcode2D barcode=fixture.barcode;
        require(headers.position==barcode.getDataMatrixSequencePosition() && headers.total==barcode.getDataMatrixSequenceTotal()
                && headers.fileId==barcode.getDataMatrixFileId(),"DataMatrix Structured Append metadata");
        require(headers.readerProgramming==barcode.isDataMatrixReaderProgramming(),"DataMatrix reader programming");
        int macro=barcode.getDataMatrixMacro()==Barcode2D.DataMatrixMacro.NONE ? 0 : barcode.getDataMatrixMacro()==Barcode2D.DataMatrixMacro.MACRO_05 ? 5 : 6;
        require(headers.macro==macro,"DataMatrix macro metadata");
        return "ECC200 data="+words.length+"; ECC="+(version.getTotalCodewords()-words.length)+"; corrected=0; region borders/unused modules/padding=pass; ECI="+eci
                +"; controls="+Arrays.toString(Arrays.copyOf(words,Math.min(words.length,12)))+"; SA="+headers.position+"/"+headers.total+"/"+headers.fileId
                +"; macro="+headers.macro+"; reader="+headers.readerProgramming;
    }

    private static String pdf(BitMatrix matrix,T31BarcodeProfile.Fixture fixture) throws Exception {
        int columns=(matrix.getWidth()-69)/17,rows=matrix.getHeight(),ecc=1<<(fixture.pdfEcc+1);
        require(columns>=1 && columns<=30 && rows>=3 && rows<=90 && columns*rows<=928,"PDF417 dimensions");
        int[] words=new int[columns*rows],indicators={(rows-1)/3,3*fixture.pdfEcc+(rows-1)%3,columns-1};
        for (int row=0;row<rows;row++) {
            require(pdfBits(matrix,0,row,17)==0x1fea8 && pdfBits(matrix,matrix.getWidth()-18,row,18)==0x3fa29,"PDF417 start/stop");
            require(pdfWord(matrix,17,row)==30*(row/3)+indicators[row%3],"PDF417 left row indicator");
            require(pdfWord(matrix,34+17*columns,row)==30*(row/3)+indicators[(row+2)%3],"PDF417 right row indicator");
            for (int column=0;column<columns;column++) { words[row*columns+column]=pdfWord(matrix,34+17*column,row); }
        }
        require(new ErrorCorrection().decode(words,ecc,new int[0])==0,"PDF417 damaged data/ECC required correction");
        require(words[0]==words.length-ecc,"PDF417 length descriptor");
        prefix(words,1,fixture.prefix,"PDF417 requested compaction");
        require((words[1]==927 ? words[2] : 3)==fixture.eci,"PDF417 ECI mismatch");
        Result decoded=pdfDecode(matrix);
        require(fixture.payload.equals(decoded.getText()),"PDF417 payload mismatch");
        require(Integer.toString(fixture.pdfEcc).equals(decoded.getResultMetadata().get(ResultMetadataType.ERROR_CORRECTION_LEVEL)),"PDF417 decoder ECC metadata");
        PDF417ResultMetadata macro=(PDF417ResultMetadata)decoded.getResultMetadata().get(ResultMetadataType.PDF417_EXTRA_METADATA);
        Barcode2D barcode=fixture.barcode;
        if (barcode.getPdf417MacroCount()>0) {
            require(macro!=null && barcode.getPdf417MacroFileId().equals(macro.getFileId())
                    && barcode.getPdf417MacroSegment()==macro.getSegmentIndex() && barcode.getPdf417MacroCount()==macro.getSegmentCount()
                    && macro.isLastSegment()==(barcode.getPdf417MacroSegment()==barcode.getPdf417MacroCount()-1),"PDF417 Macro metadata");
        } else { require(macro==null || macro.getFileId()==null,"Unexpected PDF417 Macro"); }
        return "columns="+columns+"; rows="+rows+"; ECC level="+fixture.pdfEcc+"; ECC words="+ecc+"; corrected=0; start/stop/row indicators/clusters=pass; ECI="+fixture.eci
                +"; descriptor="+words[0]+"; controls="+Arrays.toString(Arrays.copyOfRange(words,1,Math.min(words.length,13)))
                +"; Macro="+barcode.getPdf417MacroFileId()+"/"+barcode.getPdf417MacroSegment()+"/"+barcode.getPdf417MacroCount();
    }
    private static int pdfWord(BitMatrix matrix,int x,int row) {
        int pattern=pdfBits(matrix,x,row,17),word=PDF417Common.getCodeword(pattern);
        require(word>=0,"PDF417 unrecognized symbol");
        int[] runs=new int[8]; boolean last=true; int run=0;
        for (int bit=0;bit<17;bit++) {
            boolean value=matrix.get(x+bit,row);
            if (value!=last) { run++; last=value; }
            require(run<8,"PDF417 symbol runs"); runs[run]++;
        }
        require(run==7 && (runs[0]-runs[2]+runs[4]-runs[6]+27)%9==(row%3)*3,"PDF417 row cluster");
        return word;
    }
    private static int pdfBits(BitMatrix matrix,int x,int y,int count) {
        int result=0; for (int bit=0;bit<count;bit++) { result=(result<<1)|(matrix.get(x+bit,y) ? 1 : 0); } return result;
    }
    private static Result pdfDecode(BitMatrix matrix) throws Exception {
        int width=matrix.getWidth()*3+24,height=matrix.getHeight()*9+24;
        int[] pixels=new int[width*height]; Arrays.fill(pixels,0xffffff);
        for (int y=0;y<matrix.getHeight();y++) { for (int x=0;x<matrix.getWidth();x++) { if (matrix.get(x,y)) {
            for (int dy=0;dy<9;dy++) { Arrays.fill(pixels,(12+y*9+dy)*width+12+x*3,(12+y*9+dy)*width+15+x*3,0); }
        } } }
        return new PDF417Reader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width,height,pixels))));
    }
    private static void prefix(int[] words,int start,int[] expected,String label) {
        for (int index=0;index<expected.length;index++) { if (expected[index]>=0) { require(words[start+index]==expected[index],label+" at "+index); } }
    }
    static String printable(String text) {
        StringBuilder result=new StringBuilder();
        for (int index=0;index<text.length();index++) {
            char value=text.charAt(index);
            if (value<32 || value>126) { result.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)value)); }
            else { result.append(value); }
        }
        return result.toString();
    }
}
