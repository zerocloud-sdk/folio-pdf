package net.zerocloud.pdf.acceptance;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfBoolean;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNull;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfString;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageCount;

/** Project-owned T09 expectations, checked only through public Native reopen. */
final class T09ValuesSemantics {
    private T09ValuesSemantics() {
    }

    static EvidenceResult inspect(Path pdf, WorkflowExecutionProfile profile) {
        try {
            return new DocumentWorkflow().execute(WorkflowRequest.builder()
                    .source("input", DocumentSource.path(pdf)).primarySource("input")
                    .executionProfile(profile).saveMode(SaveMode.REWRITE).build(),
                    session -> matches(session) ? EvidenceResult.PASS : EvidenceResult.FAIL).getResult();
        } catch (DocumentFailure | ClassCastException | NullPointerException | IndexOutOfBoundsException invalid) {
            return EvidenceResult.INDETERMINATE;
        }
    }

    private static boolean matches(DocumentSession session) throws DocumentFailure {
        PdfDictionary catalog = (PdfDictionary) inspect(session, session.query(DocumentRootReference.INSTANCE));
        PdfDictionary pieceInfo = (PdfDictionary) catalog.get(PdfName.of("PieceInfo"));
        PdfDictionary application = (PdfDictionary) pieceInfo.get(PdfName.of("FolioPDF"));
        PdfDictionary values = (PdfDictionary) resolve(session, application.get(PdfName.of("Private")));
        if (session.query(PageCount.INSTANCE) != 1 || values.size() != 12
                || !PdfNull.INSTANCE.equals(values.get(PdfName.of("Null")))
                || !PdfBoolean.of(true).equals(values.get(PdfName.of("Flag")))
                || !PdfNumber.of(new BigDecimal("7.25")).equals(values.get(PdfName.of("Number")))
                || !PdfString.of(new byte[] {0, 40, 41, 92, (byte) 255}).equals(values.get(PdfName.of("String")))
                || !PdfName.of("After /# Ω").equals(values.get(PdfName.of("Name")))) {
            return false;
        }
        PdfDictionary dictionary = (PdfDictionary) values.get(PdfName.of("Dictionary"));
        if (dictionary.size() != 2 || !PdfNumber.of(2).equals(dictionary.get(PdfName.of("Value")))
                || !PdfString.of("kept".getBytes(StandardCharsets.US_ASCII)).equals(dictionary.get(PdfName.of("Added")))) {
            return false;
        }
        PdfArray array = (PdfArray) values.get(PdfName.of("Array"));
        if (array.size() != 7 || !PdfName.of("Inserted").equals(array.get(0))
                || !PdfBoolean.of(true).equals(array.get(1))
                || !PdfNumber.of(new BigDecimal("7.25")).equals(array.get(2))
                || !PdfString.of(new byte[] {0, 40, 92, (byte) 255}).equals(array.get(3))
                || !PdfName.of("Before").equals(array.get(4))
                || !PdfNumber.of(2).equals(((PdfDictionary) array.get(5)).get(PdfName.of("Nested")))) {
            return false;
        }
        PdfIndirectReference changedStream = (PdfIndirectReference) values.get(PdfName.of("Stream"));
        PdfIndirectReference retainedStream = (PdfIndirectReference) values.get(PdfName.of("RetainedStream"));
        if (!changedStream.equals(array.get(6)) || !retainedStream.equals(values.get(PdfName.of("Reference")))
                || !PdfNumber.of(43).equals(resolve(session, values.get(PdfName.of("ReferenceAlias"))))) {
            return false;
        }
        PdfStream stream = (PdfStream) resolve(session, changedStream);
        if (!Arrays.equals(new byte[] {8, 9, 0, (byte) 255}, stream.readBytes())
                || !PdfName.of("FlateDecode").equals(stream.getDictionary().get(PdfName.of("Filter")))
                || stream.getDictionary().get(PdfName.of("DecodeParms")) != null) {
            return false;
        }
        PdfStream retained = (PdfStream) resolve(session, retainedStream);
        if (!Arrays.equals("\u0000T09 untouched encoded bytes\u00ff".getBytes(StandardCharsets.ISO_8859_1), retained.readBytes())) {
            return false;
        }
        PdfDictionary retainedAttributes = retained.getDictionary();
        if (!PdfName.of("FlateDecode").equals(((PdfArray) retainedAttributes.get(PdfName.of("Filter"))).get(0))
                || !PdfNumber.of(1).equals(((PdfDictionary) ((PdfArray) retainedAttributes.get(PdfName.of("DecodeParms"))).get(0))
                        .get(PdfName.of("Predictor")))) {
            return false;
        }
        PdfDictionary unknown = (PdfDictionary) values.get(PdfName.of("Unknown"));
        if (unknown.size() != 2 || !PdfName.of("FolioPDF").equals(unknown.get(PdfName.of("Vendor")))
                || !PdfString.of(new byte[] {0, 84, 48, 57, (byte) 255}).equals(unknown.get(PdfName.of("Payload")))) {
            return false;
        }
        PdfDictionary pages = (PdfDictionary) resolve(session, catalog.get(PdfName.of("Pages")));
        PdfDictionary page = (PdfDictionary) resolve(session, ((PdfArray) pages.get(PdfName.of("Kids"))).get(0));
        PdfArray box = (PdfArray) page.get(PdfName.of("MediaBox"));
        int[] expectedBox = {0, 0, 612, 792};
        if (box.size() != expectedBox.length || ((PdfDictionary) page.get(PdfName.of("Resources"))).size() != 0) {
            return false;
        }
        for (int index = 0; index < expectedBox.length; index++) {
            if (((PdfNumber) box.get(index)).decimalValue().compareTo(BigDecimal.valueOf(expectedBox[index])) != 0) {
                return false;
            }
        }
        PdfStream content = (PdfStream) resolve(session, page.get(PdfName.of("Contents")));
        return Arrays.equals("q 0 0 1 rg 50 60 100 80 re f Q\n".getBytes(StandardCharsets.US_ASCII), content.readBytes());
    }

    private static PdfValue resolve(DocumentSession session, PdfValue value) throws DocumentFailure {
        return value instanceof PdfIndirectReference ? inspect(session, ((PdfIndirectReference) value).getReference()) : value;
    }

    private static PdfValue inspect(DocumentSession session, ObjectReference reference) throws DocumentFailure {
        return session.query(InspectObject.version1(reference, PdfInspectionLimits.of(1000, 1 << 20)));
    }
}
