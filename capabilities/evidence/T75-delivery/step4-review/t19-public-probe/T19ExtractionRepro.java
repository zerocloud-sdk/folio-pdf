import java.nio.file.Paths;
import net.zerocloud.pdf.*;
import net.zerocloud.pdf.query.ExtractTextAndStructure;
public final class T19ExtractionRepro {
 public static void main(String[] args) throws Exception {
  ExtractionLimits limits=ExtractionLimits.builder().maximumPages(2).maximumPageTreeNodes(16)
   .maximumContentStreams(16).maximumContentStreamDepth(4).maximumDecodedBytes(Long.parseLong(args[3]))
   .maximumTextItems(16).maximumUnicodeCodePoints(32).maximumToUnicodeMappings(Integer.parseInt(args[2]))
   .maximumFontDataEntries(Integer.parseInt(args[1])).maximumMarkedContentSequences(4)
   .maximumMarkedContentDepth(2).maximumStructureElements(4).maximumStructureItems(4)
   .maximumStructureDepth(2).maximumRoleMappings(2).build();
  TextStructureExtraction result=new DocumentWorkflow().execute(WorkflowRequest.open(Paths.get(args[0]),SaveMode.REWRITE),
   session -> session.query(ExtractTextAndStructure.version1(limits))).getResult();
  String actual=result.getPages().get(0).getText();
  if (!"A\u03a9BAB".equals(actual)) throw new AssertionError(actual);
  System.out.println("Public detached extraction: "+actual);
 }
}
