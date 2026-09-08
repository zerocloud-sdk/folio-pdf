package net.zerocloud.pdf.acceptance;

import java.nio.file.Path;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.InspectObject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.math.BigDecimal;

/** Project-owned blank-profile observations through the public Native Interface. */
final class T03BlankSemantics {
    private T03BlankSemantics() {
    }

    static EvidenceResult inspect(Path pdf, WorkflowExecutionProfile profile) {
        try {
            return new DocumentWorkflow().execute(WorkflowRequest.builder()
                    .source("input", DocumentSource.path(pdf)).primarySource("input")
                    .executionProfile(profile).saveMode(SaveMode.REWRITE).build(),
                    session -> blank(session)
                            ? EvidenceResult.PASS : EvidenceResult.FAIL).getResult();
        } catch (DocumentFailure | ClassCastException failure) {
            return EvidenceResult.FAIL;
        }
    }

    private static boolean blank(DocumentSession session) throws DocumentFailure {
        if (session.query(PageCount.INSTANCE) != 1) {
            return false;
        }
        PdfDictionary catalog = dictionary(session, session.query(DocumentRootReference.INSTANCE));
        if (!keys(catalog, "Type", "Pages")) {
            return false;
        }
        PdfDictionary pages = (PdfDictionary) resolve(session, catalog.get(PdfName.of("Pages")));
        if (!keys(pages, "Type", "Kids", "Count")) {
            return false;
        }
        PdfArray kids = (PdfArray) pages.get(PdfName.of("Kids"));
        if (kids.size() != 1) {
            return false;
        }
        PdfDictionary page = (PdfDictionary) resolve(session, kids.get(0));
        if (!keys(page, "Type", "Parent", "MediaBox", "Resources")) {
            return false;
        }
        PdfDictionary resources = (PdfDictionary) resolve(session, page.get(PdfName.of("Resources")));
        PdfArray mediaBox = (PdfArray) page.get(PdfName.of("MediaBox"));
        if (resources.size() != 0 || mediaBox.size() != 4) {
            return false;
        }
        int[] expected = {0, 0, 612, 792};
        for (int index = 0; index < expected.length; index++) {
            if (((PdfNumber) mediaBox.get(index)).decimalValue()
                    .compareTo(BigDecimal.valueOf(expected[index])) != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean keys(PdfDictionary dictionary, String... expected) throws DocumentFailure {
        Set<String> actual = new HashSet<String>();
        for (int index = 0; index < dictionary.size(); index++) {
            actual.add(dictionary.getEntry(index).getName().getValue());
        }
        return actual.equals(new HashSet<String>(Arrays.asList(expected)));
    }

    private static PdfValue resolve(DocumentSession session, PdfValue value) throws DocumentFailure {
        return value instanceof PdfIndirectReference
                ? dictionary(session, ((PdfIndirectReference) value).getReference()) : value;
    }

    private static PdfDictionary dictionary(DocumentSession session, ObjectReference reference)
            throws DocumentFailure {
        return (PdfDictionary) session.query(InspectObject.version1(reference, PdfInspectionLimits.of(64, 0L)));
    }
}
