package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DecoderResult;
import net.zerocloud.pdf.*;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.composition.Barcode2D;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.composition.command.DrawBarcode2D;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageObjectReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Independent ECC200 decoding of actual Workflow output, including standard control headers. */
public final class T31DataMatrixEvidenceTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void structuredAppendHeaderBytesNeverBecomePayloadEvenAtTheIdentifierLimits() throws Exception {
        for (WorkflowExecutionProfile profile : WorkflowExecutionProfile.values()) {
            Path pdf = temporary.newFile().toPath();
            int[][] headers = {{1,2,75},{2,2,75},{16,16,64516},{1,16,1}};
            new DocumentWorkflow().execute(WorkflowRequest.builder().target("pdf",PublicationTarget.path(pdf)).saveMode(SaveMode.REWRITE)
                    .executionProfile(profile).build(),session -> {
                        for (int index = 0; index < headers.length; index++) {
                            int[] header = headers[index];
                            session.execute(AddBlankPage.INSTANCE);
                            session.execute(DrawBarcode2D.version1(index+1,Barcode2D.builder(Barcode2D.Mode.DATA_MATRIX,"PART"+(index+1))
                                    .dataMatrixStructuredAppend(header[0],header[1],header[2]).dataMatrixSize(26,26).build(),CanvasMatrix.IDENTITY));
                        }
                        return null;
                    });
            new DocumentWorkflow().execute(WorkflowRequest.builder().source("pdf",DocumentSource.path(pdf)).primarySource("pdf")
                    .saveMode(SaveMode.REWRITE).executionProfile(profile).build(),session -> {
                        for (int index = 0; index < headers.length; index++) {
                            DecoderResult decoded;
                            try { decoded = new T31DataMatrixDecoder().decode(matrix(session,index+1)); }
                            catch (com.google.zxing.ReaderException failure) { throw new AssertionError("Independent ECC200 decoding failed",failure); }
                            assertEquals("PART"+(index+1),decoded.getText());
                            assertEquals(Integer.valueOf(0),decoded.getErrorsCorrected());
                            T31DataMatrixDecoder.Headers observed = (T31DataMatrixDecoder.Headers)decoded.getOther();
                            assertEquals(headers[index][0],observed.position);
                            assertEquals(headers[index][1],observed.total);
                            assertEquals(headers[index][2],observed.fileId);
                        }
                        return null;
                    });
        }
    }

    private static BitMatrix matrix(DocumentSession session,int page) throws DocumentFailure {
        PdfDictionary dictionary = (PdfDictionary)session.query(InspectObject.version1(session.query(PageObjectReference.version1(page)),PdfInspectionLimits.of(100000,16<<20)));
        PdfDictionary resources = (PdfDictionary)resolve(session,dictionary.get(PdfName.of("Resources")));
        PdfDictionary objects = (PdfDictionary)resolve(session,resources.get(PdfName.of("XObject")));
        assertEquals(1,objects.size());
        PdfStream form = (PdfStream)resolve(session,objects.getEntry(0).getValue());
        assertEquals(PdfName.of("Form"),form.getDictionary().get(PdfName.of("Subtype")));
        BitMatrix result = new BitMatrix(26,26);
        List<Double> operands = new ArrayList<Double>();
        for (String token : new String(form.readBytes(),StandardCharsets.US_ASCII).trim().split("\\s+")) {
            if (token.matches("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)")) { operands.add(Double.valueOf(token)); continue; }
            if (token.equals("re")) {
                double x = operands.get(0)-1,y = operands.get(1)-1,w = operands.get(2),h = operands.get(3);
                for (double value : new double[] {x,y,w,h}) { assertEquals(Math.rint(value),value,0.0001); }
                assertTrue(x >= 0 && y >= 0 && x+w <= 26 && y+h <= 26);
                result.setRegion((int)x,26-(int)(y+h),(int)w,(int)h);
            }
            operands.clear();
        }
        return result;
    }
    private static PdfValue resolve(DocumentSession session,PdfValue value) throws DocumentFailure {
        return value instanceof PdfIndirectReference ? session.query(InspectObject.version1(((PdfIndirectReference)value).getReference(),PdfInspectionLimits.of(100000,16<<20))) : value;
    }
}
