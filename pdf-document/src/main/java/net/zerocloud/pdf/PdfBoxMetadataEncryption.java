package net.zerocloud.pdf;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSObjectKey;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;

/** Identifies the document-level stream; a metadata-like name grants no exemption. */
final class PdfBoxMetadataEncryption {
    private PdfBoxMetadataEncryption() { }

    static COSStream requireDocumentMetadata(PDDocument document) throws DocumentFailure {
        COSBase value = document.getDocumentCatalog().getCOSObject().getDictionaryObject(COSName.METADATA);
        if (value == null) { return null; }
        if (!(value instanceof COSStream) || !COSName.METADATA.equals(((COSStream) value).getCOSName(COSName.TYPE))
                || !COSName.getPDFName("XML").equals(((COSStream) value).getCOSName(COSName.SUBTYPE))) {
            throw PdfBoxWorkflowEngine.versionFailure(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                    "The document metadata declaration is malformed.");
        }
        return (COSStream) value;
    }

    static void requireClearScope(PDDocument document, WorkflowResourceContext resources) throws DocumentFailure {
        COSStream metadata = requireDocumentMetadata(document);
        inspectFilters(document, resources, metadata, true);
    }

    static void requireOutputFilters(PDDocument document, WorkflowResourceContext resources) throws DocumentFailure {
        inspectFilters(document, resources, null, false);
    }

    private static void inspectFilters(PDDocument document, WorkflowResourceContext resources,
            COSStream metadata, boolean input) throws DocumentFailure {
        COSDocument objects = document.getDocument();
        // The bounded hostile-input audit has already materialized this graph.
        for (COSObjectKey key : objects.getXrefTable().keySet()) {
            resources.checkpoint();
            COSBase object = objects.getObjectFromPool(key).getObject();
            if (object instanceof COSStream) {
                requireCryptFilter((COSStream) object, !input || object == metadata, !input || object != metadata);
            }
        }
    }

    private static void requireCryptFilter(COSStream stream, boolean identity, boolean standard) throws DocumentFailure {
        COSBase filter = stream.getDictionaryObject(COSName.FILTER);
        COSBase parameters = stream.getDictionaryObject(COSName.DECODE_PARMS);
        int count = filter instanceof COSArray ? ((COSArray) filter).size() : 1;
        for (int index = 0; index < count; index++) {
            COSBase name = filter instanceof COSArray ? ((COSArray) filter).getObject(index) : filter;
            if (!COSName.CRYPT.equals(name)) { continue; }
            if (index != 0) { throw malformedFilter(); }
            COSBase selected = parameters;
            if (filter instanceof COSArray && parameters != null && parameters != COSNull.NULL) {
                if (!(parameters instanceof COSArray) || ((COSArray) parameters).size() != count) { throw malformedFilter(); }
                selected = ((COSArray) parameters).getObject(index);
            }
            if (selected != null && selected != COSNull.NULL && !(selected instanceof COSDictionary)) { throw malformedFilter(); }
            COSBase selection = selected instanceof COSDictionary ? ((COSDictionary) selected).getDictionaryObject(COSName.NAME) : null;
            if (!((identity && (selection == null || COSName.IDENTITY.equals(selection)))
                    || (standard && COSName.STD_CF.equals(selection)))) { throw malformedFilter(); }
        }
    }

    static void normalizeForNewEncryption(COSStream stream) throws DocumentFailure {
        // The writer applies the newly selected policy itself. Retaining an
        // explicit Identity would exempt encrypted bytes from reader decryption.
        // StdCF is also redundant after this single encryption pass.
        requireCryptFilter(stream, true, true);
        COSBase filter = stream.getDictionaryObject(COSName.FILTER);
        COSBase first = filter instanceof COSArray && ((COSArray) filter).size() > 0
                ? ((COSArray) filter).getObject(0) : filter;
        if (!COSName.CRYPT.equals(first)) { return; }
        if (filter instanceof COSArray && ((COSArray) filter).size() > 1) {
            ((COSArray) filter).remove(0);
            COSBase parameters = stream.getDictionaryObject(COSName.DECODE_PARMS);
            if (parameters instanceof COSArray) { ((COSArray) parameters).remove(0); }
        } else {
            stream.removeItem(COSName.FILTER);
            stream.removeItem(COSName.DECODE_PARMS);
        }
    }

    private static DocumentFailure malformedFilter() {
        return PdfBoxWorkflowEngine.versionFailure(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "The stream crypt-filter declaration conflicts with the metadata scope.");
    }

    static boolean isDocumentMetadata(COSDocument document, COSStream stream, long number, long generation) {
        COSBase root = document.getTrailer().getDictionaryObject(COSName.ROOT);
        if (!(root instanceof COSDictionary)) { return false; }
        COSBase metadata = ((COSDictionary) root).getItem(COSName.METADATA);
        if (metadata instanceof COSObject) {
            COSObject reference = (COSObject) metadata;
            return reference.getObjectNumber() == number && reference.getGenerationNumber() == generation;
        }
        return metadata == stream;
    }
}
