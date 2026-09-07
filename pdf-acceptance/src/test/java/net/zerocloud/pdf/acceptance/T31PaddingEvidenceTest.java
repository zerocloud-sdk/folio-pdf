package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DecoderResult;
import net.zerocloud.pdf.*;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.composition.command.DrawBarcode2D;
import net.zerocloud.pdf.query.PageObjectReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class T31PaddingEvidenceTest {
    @Rule public final TemporaryFolder temporary=new TemporaryFolder();

    @Test
    public void validPayloadAndRecomputedEccCannotHideInvalidRandomizedPadding() throws Exception {
        T31BarcodeProfile.Fixture fixture=null;
        for (T31BarcodeProfile.Fixture candidate : T31BarcodeProfile.fixtures()) {
            if (candidate.id.equals("dm-size-12x12")) { fixture=candidate; }
        }
        final T31BarcodeProfile.Fixture declared=fixture;
        assertNotNull(declared);
        for (WorkflowExecutionProfile profile : WorkflowExecutionProfile.values()) {
            Path original=temporary.newFile().toPath(),changed=temporary.newFile().toPath();
            new DocumentWorkflow().execute(WorkflowRequest.builder().target("output",PublicationTarget.path(original))
                    .saveMode(SaveMode.REWRITE).executionProfile(profile).build(),session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        session.execute(DrawBarcode2D.version1(1,declared.barcode,declared.placement)); return null;
                    });
            new DocumentWorkflow().execute(WorkflowRequest.builder().source("source",DocumentSource.path(original)).primarySource("source")
                    .target("output",PublicationTarget.path(changed)).saveMode(SaveMode.REWRITE).executionProfile(profile).build(),session -> {
                        PdfDictionary page=T31BarcodeAssertions.page(session,1);
                        PdfDictionary resources=(PdfDictionary)T31BarcodeAssertions.resolve(session,page.get(PdfName.of("Resources")));
                        PdfDictionary objects=(PdfDictionary)T31BarcodeAssertions.resolve(session,resources.get(PdfName.of("XObject")));
                        PdfStream form=(PdfStream)T31BarcodeAssertions.resolve(session,objects.getEntry(0).getValue());
                        BitMatrix matrix=T31BarcodeAssertions.modules(new String(form.readBytes(),StandardCharsets.US_ASCII),declared);
                        try { matrix=T31NegativeControls.invalidPadding(matrix); } catch (Exception failure) { throw new AssertionError(failure); }
                        StringBuilder paths=new StringBuilder("0 g\n");
                        for (int y=0;y<12;y++) { for (int x=0;x<12;x++) { if (matrix.get(x,y)) { paths.append(x+1).append(' ').append(12-y).append(" 1 1 re f\n"); } } }
                        PdfDictionary dictionary=PdfDictionary.builder().put(PdfName.of("Type"),PdfName.of("XObject"))
                                .put(PdfName.of("Subtype"),PdfName.of("Form")).put(PdfName.of("FormType"),PdfNumber.of(1))
                                .put(PdfName.of("BBox"),PdfArray.of(PdfNumber.of(0),PdfNumber.of(0),PdfNumber.of(14),PdfNumber.of(14)))
                                .put(PdfName.of("Resources"),PdfDictionary.builder().build()).build();
                        PdfDictionary replaced=PdfDictionary.builder().put(PdfName.of("XObject"),PdfDictionary.builder()
                                .put(objects.getEntry(0).getName(),PdfStream.of(dictionary,paths.toString().getBytes(StandardCharsets.US_ASCII))).build()).build();
                        session.execute(DocumentPatch.builder().setDictionaryEntry(session.query(PageObjectReference.version1(1)),PdfName.of("Resources"),replaced).build());
                        return null;
                    });
            new DocumentWorkflow().execute(WorkflowRequest.open(changed,SaveMode.REWRITE),session -> {
                PdfDictionary page=T31BarcodeAssertions.page(session,1);
                PdfDictionary resources=(PdfDictionary)T31BarcodeAssertions.resolve(session,page.get(PdfName.of("Resources")));
                PdfDictionary objects=(PdfDictionary)T31BarcodeAssertions.resolve(session,resources.get(PdfName.of("XObject")));
                PdfStream form=(PdfStream)T31BarcodeAssertions.resolve(session,objects.getEntry(0).getValue());
                BitMatrix matrix=T31BarcodeAssertions.modules(new String(form.readBytes(),StandardCharsets.US_ASCII),declared);
                try {
                    DecoderResult decoded=new T31DataMatrixDecoder().decode(matrix);
                    assertEquals("AB",decoded.getText()); assertEquals(Integer.valueOf(0),decoded.getErrorsCorrected());
                    try { T31MatrixOracle.inspect(matrix,declared); fail("Invalid randomized padding passed independent qualification"); }
                    catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("DataMatrix randomized padding")); }
                } catch (Exception failure) { throw new AssertionError(failure); }
                return null;
            });
        }
    }


}
