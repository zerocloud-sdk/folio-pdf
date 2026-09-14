import java.nio.file.*;
import java.util.*;
import net.zerocloud.pdf.*;
import net.zerocloud.pdf.query.*;
public final class SharedDescendantProbe {
  static String run(Path file, int entries) throws Exception {
    byte[] before=Files.readAllBytes(file);
    ExtractionLimits limits=ExtractionLimits.builder().maximumPages(1).maximumPageTreeNodes(10).maximumContentStreams(10).maximumContentStreamDepth(32).maximumDecodedBytes(1048576).maximumTextItems(2).maximumUnicodeCodePoints(0).maximumToUnicodeMappings(0).maximumFontDataEntries(entries).maximumMarkedContentSequences(10).maximumMarkedContentDepth(10).maximumStructureElements(10).maximumStructureItems(10).maximumStructureDepth(10).maximumRoleMappings(10).build();
    WorkflowRequest request=WorkflowRequest.builder().executionProfile(WorkflowExecutionProfile.IN_PROCESS).source("input",DocumentSource.path(file)).primarySource("input").saveMode(SaveMode.REWRITE).build();
    String outcome;
    try {
      WorkflowOutcome<TextStructureExtraction> result=new DocumentWorkflow().execute(request,s->s.query(ExtractTextAndStructure.version1(limits)));
      PageText page=result.getResult().getPages().get(0);
      if(page.getTextItems().size()!=2)throw new AssertionError("item count");
      for(int i=0;i<2;i++) {
        TextItem item=page.getTextItems().get(i);
        if(Math.abs(item.getGeometry().getAdvanceX().doubleValue()-7)>0.0001 || Math.abs(item.getGeometry().getE().doubleValue()-(20+7*i))>0.0001 || !Arrays.equals(new byte[]{65},item.getCharacterMapping().getSourceCode()))throw new AssertionError("geometry/source");
      }
      outcome="SUCCESS items=2 advances=7,7 positions=20,27";
    } catch(DocumentFailure failure) {
      outcome=failure.getCode()+" capability="+failure.getCapabilityId();
    }
    if(!Arrays.equals(before,Files.readAllBytes(file)))throw new AssertionError("source changed");
    System.out.println(file.getFileName()+" budget="+entries+" "+outcome);
    return outcome;
  }
  public static void main(String[] args)throws Exception {
    Path root=Paths.get(args[0]);
    if(args.length>1 && "regression".equals(args[1])) {
      String result=run(root.resolve("shared_descendant.pdf"),8203);
      if(!result.startsWith("EXTRACTION_LIMIT_EXCEEDED"))throw new AssertionError("Two independent backend width constructions must not pass one below the 8204-entry boundary: "+result);
      return;
    }
    run(root.resolve("reused_top_level.pdf"),4101);run(root.resolve("reused_top_level.pdf"),4102);
    run(root.resolve("shared_descendant.pdf"),4104);run(root.resolve("shared_descendant.pdf"),4105);
    run(root.resolve("shared_descendant.pdf"),8203);run(root.resolve("shared_descendant.pdf"),8204);
    run(root.resolve("distinct_descendants.pdf"),4105);run(root.resolve("distinct_descendants.pdf"),8203);run(root.resolve("distinct_descendants.pdf"),8204);
  }
}
