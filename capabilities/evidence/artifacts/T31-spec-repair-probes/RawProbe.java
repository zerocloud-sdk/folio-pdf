import java.nio.file.*;
import java.util.*;
import net.zerocloud.pdf.*;
import net.zerocloud.pdf.command.*;
import net.zerocloud.pdf.composition.*;
import net.zerocloud.pdf.composition.command.*;
import net.zerocloud.pdf.composition.query.*;
public class RawProbe {
 public static void main(String[] args)throws Exception {
  Barcode2D[] items={
   Barcode2D.rawPdf417(901,65,927,26,300).build(),
   Barcode2D.rawPdf417(901,927,26,65).build(),
   Barcode2D.rawPdf417(924,0,0,0,0,0,927,26,300).build(),
   Barcode2D.rawDataMatrix(234,232,66).build(),
   Barcode2D.rawDataMatrix(241,27,66).build()
  };
  for(WorkflowExecutionProfile profile:WorkflowExecutionProfile.values())for(int i=0;i<items.length;i++){
   final Barcode2D b=items[i]; final String id=profile+"-"+i;final Probe.C c=new Probe.C(id,b,"");c.page=1;
   Path file=Paths.get(args[0],id+".pdf"); Files.write(file,new byte[]{1,2,3});
   try {
    new DocumentWorkflow().execute(WorkflowRequest.builder().target("out",PublicationTarget.path(file)).saveMode(SaveMode.REWRITE).executionProfile(profile).build(),s->{c.size=s.query(MeasureBarcode2D.version1(b));s.execute(AddBlankPage.INSTANCE);s.execute(DrawBarcode2D.version1(1,b,CanvasMatrix.IDENTITY));return null;});
    String actual=new DocumentWorkflow().execute(WorkflowRequest.open(file,SaveMode.REWRITE),s->{try{com.google.zxing.common.BitMatrix m=Probe.matrix(s,c);return b.getMode()==Barcode2D.Mode.PDF417?Probe.pdf(m):new com.google.zxing.datamatrix.decoder.Decoder().decode(m).getText();}catch(Exception e){return "decoder-error:"+e.getClass().getName();}}).getResult();
    System.out.println(id+" COMMITTED raw="+Arrays.toString(b.getRawCodewords())+" decoded="+Probe.esc(actual));
   } catch(DocumentFailure failure){System.out.println(id+" REJECTED "+failure.getCode()+" retained="+Arrays.equals(new byte[]{1,2,3},Files.readAllBytes(file))+" raw="+Arrays.toString(b.getRawCodewords()));}
  }
 }
}
