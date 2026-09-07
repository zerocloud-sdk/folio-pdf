import java.nio.file.*;
import java.nio.charset.*;
import java.util.*;
import com.google.zxing.common.*;
import net.zerocloud.pdf.*;
import net.zerocloud.pdf.command.*;
import net.zerocloud.pdf.composition.*;
import net.zerocloud.pdf.composition.command.*;
import net.zerocloud.pdf.composition.query.*;
import net.zerocloud.pdf.query.*;

public class Probe {
 static final List<C> cases=new ArrayList<C>();
 static class C { final Barcode2D b; final String expected,id; Barcode2DSize size; int page; C(String id,Barcode2D b,String expected){this.id=id;this.b=b;this.expected=expected;} }
 static void add(String id, Barcode2D b,String expected){cases.add(new C(id,b,expected));}
 static String esc(String s){StringBuilder r=new StringBuilder();for(char c:s.toCharArray())r.append(String.format(Locale.ROOT,"\\u%04x",(int)c));return r.toString();}
 public static void main(String[] args)throws Exception {
  Random r=new Random(31);
  for(Barcode2D.DataMatrixEncoding mode:Barcode2D.DataMatrixEncoding.values()){
   if(mode==Barcode2D.DataMatrixEncoding.RAW)continue;
   for(int i=0;i<120;i++){
    int len=1+r.nextInt(64); StringBuilder t=new StringBuilder();
    for(int j=0;j<len;j++)t.append((char)(mode==Barcode2D.DataMatrixEncoding.EDIFACT?32+r.nextInt(63):mode==Barcode2D.DataMatrixEncoding.X12?"\r*> 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".charAt(r.nextInt(40)):r.nextInt(256)));
    String s=t.toString();add("dm-"+mode+"-"+i,Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX,s).dataMatrixEncoding(mode).build(),s);
   }
  }
  for(boolean macro:new boolean[]{false,true})for(Barcode2D.Pdf417Encoding mode:new Barcode2D.Pdf417Encoding[]{Barcode2D.Pdf417Encoding.AUTO,Barcode2D.Pdf417Encoding.BINARY})for(int i=0;i<120;i++){
   int len=1+r.nextInt(60);StringBuilder t=new StringBuilder();for(int j=0;j<len;j++)t.append((char)r.nextInt(256));String s=t.toString();
   Barcode2D.Builder b=Barcode2D.builder(Barcode2D.Mode.PDF417,s).pdf417Encoding(mode).pdf417ErrorCorrection(2);if(macro)b.pdf417Macro("001",0,1);
   add("pdf-"+macro+"-"+mode+"-"+i,b.build(),s);
  }
  Path file=Paths.get(args[0]);int[] page={0};
  new DocumentWorkflow().execute(WorkflowRequest.create(file,SaveMode.REWRITE),session->{
   for(C c:cases){try{c.size=session.query(MeasureBarcode2D.version1(c.b));session.execute(AddBlankPage.INSTANCE);c.page=++page[0];session.execute(DrawBarcode2D.version1(c.page,c.b,CanvasMatrix.IDENTITY));}catch(DocumentFailure f){System.out.println("GENERATE_FAIL "+c.id+" "+f.getCode()+" expected="+esc(c.expected));}}
   return null;
  });
  int[] ok={0},bad={0};
  new DocumentWorkflow().execute(WorkflowRequest.open(file,SaveMode.REWRITE),session->{
   for(C c:cases){if(c.page==0)continue;try{BitMatrix m=matrix(session,c);String actual=c.b.getMode()==Barcode2D.Mode.DATA_MATRIX?new com.google.zxing.datamatrix.decoder.Decoder().decode(m).getText():pdf(m);if(!actual.equals(c.expected)){bad[0]++;System.out.println("MISMATCH "+c.id+" expected="+esc(c.expected)+" actual="+esc(actual));}else ok[0]++;}catch(Exception e){bad[0]++;System.out.println("DECODE_FAIL "+c.id+" "+e.getClass().getName()+" expected="+esc(c.expected));}}
   return null;
  });
  System.out.println("cases="+cases.size()+" published="+page[0]+" ok="+ok[0]+" bad="+bad[0]);
 }
 static PdfValue resolve(DocumentSession s,PdfValue v)throws DocumentFailure{return v instanceof PdfIndirectReference?s.query(InspectObject.version1(((PdfIndirectReference)v).getReference(),PdfInspectionLimits.of(100000,16<<20))):v;}
 static BitMatrix matrix(DocumentSession s,C c)throws DocumentFailure{
  PdfDictionary page=(PdfDictionary)s.query(InspectObject.version1(s.query(PageObjectReference.version1(c.page)),PdfInspectionLimits.of(100000,16<<20)));
  PdfDictionary res=(PdfDictionary)resolve(s,page.get(PdfName.of("Resources")));PdfDictionary xs=(PdfDictionary)resolve(s,res.get(PdfName.of("XObject")));PdfStream f=(PdfStream)resolve(s,xs.getEntry(0).getValue());
  BitMatrix m=new BitMatrix(c.size.getMatrixWidth(),c.size.getMatrixHeight());String content=new String(f.readBytes(),StandardCharsets.US_ASCII);double q=c.b.getQuietZone(),ux=c.b.getModuleWidth(),uy=c.b.getModuleHeight();
  for(String line:content.split("\n")){String[] t=line.trim().split("\\s+");if(t.length==6&&t[4].equals("re")&&t[5].equals("f")){int x=(int)Math.round((Double.parseDouble(t[0])-q)/ux),y=(int)Math.round((Double.parseDouble(t[1])-q)/uy),w=(int)Math.round(Double.parseDouble(t[2])/ux),h=(int)Math.round(Double.parseDouble(t[3])/uy);for(int dy=0;dy<h;dy++)for(int dx=0;dx<w;dx++)m.set(x+dx,m.getHeight()-1-y-dy);}}
  return m;
 }
 static String pdf(BitMatrix m)throws Exception{
  int w=m.getWidth()*3+24,h=m.getHeight()*9+24;int[] px=new int[w*h];Arrays.fill(px,0xffffff);for(int y=0;y<m.getHeight();y++)for(int x=0;x<m.getWidth();x++)if(m.get(x,y))for(int dy=0;dy<9;dy++)Arrays.fill(px,(12+y*9+dy)*w+12+x*3,(12+y*9+dy)*w+15+x*3,0);
  return new com.google.zxing.pdf417.PDF417Reader().decode(new com.google.zxing.BinaryBitmap(new HybridBinarizer(new com.google.zxing.RGBLuminanceSource(w,h,px)))).getText();
 }
}
