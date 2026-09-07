import java.nio.file.*;
import java.util.*;
import net.zerocloud.pdf.*;
import net.zerocloud.pdf.command.*;
import net.zerocloud.pdf.composition.*;
import net.zerocloud.pdf.composition.command.*;
import net.zerocloud.pdf.composition.query.*;
public class DmRawProbe {
 public static void main(String[] args)throws Exception {
  Barcode2D[] items={Barcode2D.rawDataMatrix(240,5,240).build(),Barcode2D.rawDataMatrix(240,5,240).dataMatrixSize(10,10).build(),Barcode2D.rawDataMatrix(240,5,240).dataMatrixSize(12,12).build(),Barcode2D.rawDataMatrix(240,124).build(),Barcode2D.rawDataMatrix(240,124).dataMatrixSize(12,12).build()};
  for(WorkflowExecutionProfile profile:WorkflowExecutionProfile.values())for(int i=0;i<items.length;i++){
   final Barcode2D b=items[i];String id=profile+"-dmraw-"+i;final Probe.C c=new Probe.C(id,b,"");c.page=1;Path file=Paths.get(args[0],id+".pdf");Files.write(file,new byte[]{1,2,3});
   try{
    new DocumentWorkflow().execute(WorkflowRequest.builder().target("out",PublicationTarget.path(file)).saveMode(SaveMode.REWRITE).executionProfile(profile).build(),s->{c.size=s.query(MeasureBarcode2D.version1(b));s.execute(AddBlankPage.INSTANCE);s.execute(DrawBarcode2D.version1(1,b,CanvasMatrix.IDENTITY));return null;});
    String actual=new DocumentWorkflow().execute(WorkflowRequest.open(file,SaveMode.REWRITE),s->{try{return new com.google.zxing.datamatrix.decoder.Decoder().decode(Probe.matrix(s,c)).getText();}catch(Exception e){return "decoder-error:"+e.getClass().getName();}}).getResult();
    System.out.println(id+" COMMITTED size="+c.size.getMatrixWidth()+"x"+c.size.getMatrixHeight()+" raw="+Arrays.toString(b.getRawCodewords())+" decoded="+Probe.esc(actual));
   }catch(DocumentFailure f){System.out.println(id+" REJECTED "+f.getCode()+" retained="+Arrays.equals(new byte[]{1,2,3},Files.readAllBytes(file)));}
  }
 }
}
