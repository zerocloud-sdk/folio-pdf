import java.nio.file.*;
import net.zerocloud.pdf.*;
import net.zerocloud.pdf.query.*;
public class CoreStructureProbe {
  static PdfDictionary dict(DocumentSession s, ObjectReference r) throws DocumentFailure {
    return (PdfDictionary)s.query(InspectObject.version1(r, PdfInspectionLimits.of(1000, 0)));
  }
  public static void main(String[] args) throws Exception {
    Path source = Paths.get(args[0]).toAbsolutePath();
    Path out = Paths.get(args[1]).toAbsolutePath();
    WorkflowExecutionProfile profile = WorkflowExecutionProfile.valueOf(args[2]);
    String variant = args[3];
    WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(WorkflowRequest.builder()
      .source("in", DocumentSource.path(source)).primarySource("in")
      .target("out", PublicationTarget.path(out)).executionProfile(profile).saveMode(SaveMode.REWRITE).build(), s -> {
        PdfDictionary root = dict(s, s.query(DocumentRootReference.INSTANCE));
        ObjectReference pagesRef = ((PdfIndirectReference)root.get(PdfName.of("Pages"))).getReference();
        PdfArray kids = (PdfArray)dict(s, pagesRef).get(PdfName.of("Kids"));
        ObjectReference pageRef = ((PdfIndirectReference)kids.get(0)).getReference();
        DocumentPatch.Builder patch = DocumentPatch.builder();
        if ("direct-kid".equals(variant)) {
          PdfDictionary inspectedPage = dict(s,pageRef);
          PdfDictionary.Builder copy = PdfDictionary.builder();
          for (int i=0;i<inspectedPage.size();i++) {
            PdfDictionaryEntry e=inspectedPage.getEntry(i); copy.put(e.getName(),e.getValue());
          }
          patch.setArrayElement(PdfValuePath.root(pagesRef).dictionaryEntry(PdfName.of("Kids")),0,copy.build());
        }
        else if ("resources-remove".equals(variant)) patch.removeDictionaryEntry(pageRef, PdfName.of("Resources"));
        else if ("resources-null".equals(variant)) patch.setDictionaryEntry(pageRef, PdfName.of("Resources"), PdfNull.INSTANCE);
        else if ("contents-array-number".equals(variant)) patch.setDictionaryEntry(pageRef, PdfName.of("Contents"), PdfArray.of(PdfNumber.of(9L)));
        else if ("pages-empty".equals(variant)) patch.setDictionaryEntry(s.query(DocumentRootReference.INSTANCE), PdfName.of("Pages"), PdfDictionary.builder().put(PdfName.of("Type"),PdfName.of("Pages")).put(PdfName.of("Kids"),PdfArray.of()).put(PdfName.of("Count"),PdfNumber.of(0)).build());
        else patch.setDictionaryEntry(pageRef, PdfName.of("Contents"), PdfNumber.of(9L));
        try { s.execute(patch.build()); }
        catch (DocumentFailure rejected) { System.out.println("Patch rejected: " + rejected.getCode() + ": " + rejected.getDiagnostic()); throw rejected; }
        System.out.println("Patch accepted: " + variant);
        return null;
      });
    System.out.println("Publication=" + outcome.getPublicationReceipts().get(0).getStatus());
    System.out.println("Reopened pages=" + new DocumentWorkflow().execute(WorkflowRequest.open(out, SaveMode.REWRITE), s -> s.query(PageCount.INSTANCE)).getResult());
  }
}
