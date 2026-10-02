package net.zerocloud.pdf;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSObjectKey;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.PDEncryption;

/** Routes encryption by actual file-specification EF relationships, before decoding. */
final class PdfBoxEmbeddedFileEncryption {
    private static final COSName EFF = COSName.getPDFName("EFF");

    private PdfBoxEmbeddedFileEncryption() { }

    static boolean isAttachmentScope(PDEncryption encryption) {
        return encryption.getVersion() >= 4
                && COSName.IDENTITY.equals(encryption.getStreamFilterName())
                && COSName.IDENTITY.equals(encryption.getStringFilterName());
    }

    static COSName defaultFilter(PDEncryption encryption) {
        COSBase value = encryption.getCOSObject().getDictionaryObject(EFF);
        return value == null ? encryption.getStreamFilterName() : (COSName) value;
    }

    /** Install the first explicit filter when a preserved EFF defaults to Identity. */
    static void selectStdCF(COSStream stream, WorkflowResourceContext resources) throws DocumentFailure {
        resources.checkpoint();
        COSBase original = stream.getDictionaryObject(COSName.FILTER);
        COSBase previous = stream.getDictionaryObject(COSName.DECODE_PARMS);
        int count = original instanceof COSArray ? ((COSArray) original).size() : original == null ? 0 : 1;
        resources.retainOwnedMemory(256L + 16L * count);
        COSArray filters = new COSArray();
        COSArray parameters = new COSArray();
        filters.add(COSName.CRYPT);
        COSDictionary crypt = new COSDictionary();
        crypt.setItem(COSName.NAME, COSName.STD_CF);
        parameters.add(crypt);
        if (original instanceof COSArray) {
            COSArray sequence = (COSArray) original;
            for (int index = 0; index < sequence.size(); index++) {
                resources.checkpoint();
                filters.add(sequence.get(index));
                parameters.add(previous instanceof COSArray ? ((COSArray) previous).get(index) : COSNull.NULL);
            }
        } else if (original != null) {
            filters.add(original);
            parameters.add(previous == null ? COSNull.NULL : previous);
        }
        stream.setItem(COSName.FILTER, filters);
        stream.setItem(COSName.DECODE_PARMS, parameters);
    }

    /** Only EF-referenced streams receive the default EFF; a Type name grants no access. */
    static Set<COSStream> attachments(PDDocument document, WorkflowResourceContext resources)
            throws DocumentFailure {
        Set<COSStream> result = Collections.newSetFromMap(new IdentityHashMap<COSStream, Boolean>());
        Set<COSDictionary> efDictionaries = Collections.newSetFromMap(new IdentityHashMap<COSDictionary, Boolean>());
        Set<COSBase> seen = Collections.newSetFromMap(new IdentityHashMap<COSBase, Boolean>());
        Deque<Node> pending = new ArrayDeque<Node>();
        resources.requireObjectCount(document.getDocument().getXrefTable().size());
        try (WorkflowResourceContext.OwnedMemoryScope memory = resources.ownedMemoryScope()) {
            pending.push(new Node(document.getDocument().getTrailer(), 1));
            for (COSObjectKey key : document.getDocument().getXrefTable().keySet()) {
                resources.checkpoint();
                memory.retain(48);
                pending.push(new Node(document.getDocument().getObjectFromPool(key), 1));
            }
            while (!pending.isEmpty()) {
                resources.checkpoint();
                Node node = pending.pop();
                COSBase value = node.value;
                if (value == null || !seen.add(value)) { continue; }
                resources.requireNestingDepth(node.depth);
                memory.retain(96);
                if (value instanceof COSObject) {
                    resources.observeObject((COSObject) value);
                    pending.push(new Node(((COSObject) value).getObject(), node.depth + 1));
                } else if (value instanceof COSArray) {
                    for (COSBase child : (COSArray) value) {
                        memory.retain(48);
                        pending.push(new Node(child, node.depth + 1));
                    }
                } else if (value instanceof COSDictionary) {
                    COSDictionary dictionary = (COSDictionary) value;
                    COSBase ef = dictionary.getDictionaryObject(COSName.EF);
                    if (ef != null) {
                        COSBase type = dictionary.getDictionaryObject(COSName.TYPE);
                        if (!(ef instanceof COSDictionary) || ef instanceof COSStream
                                || (type != null && !COSName.FILESPEC.equals(type))) { throw malformed(); }
                        COSDictionary entries = (COSDictionary) ef;
                        efDictionaries.add(entries);
                        for (COSName name : entries.keySet()) {
                            COSBase embedded = entries.getDictionaryObject(name);
                            if (!(embedded instanceof COSStream)) { throw malformed(); }
                            COSBase streamType = ((COSStream) embedded).getDictionaryObject(COSName.TYPE);
                            if (streamType != null && !COSName.EMBEDDED_FILE.equals(streamType)) { throw malformed(); }
                            if (result.add((COSStream) embedded)) { resources.retainOwnedMemory(64); }
                        }
                    }
                    for (COSBase child : dictionary.getValues()) {
                        memory.retain(48);
                        pending.push(new Node(child, node.depth + 1));
                    }
                }
            }
            // A protected payload cannot also masquerade as clear page content,
            // an image, document metadata, or another public stream resource.
            for (COSBase value : seen) {
                resources.checkpoint();
                if (value instanceof COSDictionary && !efDictionaries.contains(value)) {
                    for (COSBase child : ((COSDictionary) value).getValues()) {
                        if (result.contains(resolve(child))) { throw malformed(); }
                    }
                } else if (value instanceof COSArray) {
                    for (COSBase child : (COSArray) value) {
                        if (result.contains(resolve(child))) { throw malformed(); }
                    }
                }
            }
            return result;
        }
    }

    static boolean encrypted(COSStream stream, boolean attachment, COSName defaultFilter,
            WorkflowResourceContext resources)
            throws DocumentFailure {
        COSBase filter = stream.getDictionaryObject(COSName.FILTER);
        COSBase parameters = stream.getDictionaryObject(COSName.DECODE_PARMS);
        int count = filter instanceof COSArray ? ((COSArray) filter).size() : 1;
        COSName selection = attachment ? defaultFilter : COSName.IDENTITY;
        boolean explicit = false;
        for (int index = 0; index < count; index++) {
            resources.checkpoint();
            COSBase name = filter instanceof COSArray ? ((COSArray) filter).getObject(index) : filter;
            if (!COSName.CRYPT.equals(name)) { continue; }
            if (index != 0 || explicit) { throw malformed(); }
            explicit = true;
            COSBase selected = parameters;
            if (filter instanceof COSArray && parameters != null && parameters != COSNull.NULL) {
                if (!(parameters instanceof COSArray) || ((COSArray) parameters).size() != count) { throw malformed(); }
                selected = ((COSArray) parameters).getObject(index);
            }
            if (selected != null && selected != COSNull.NULL
                    && (!(selected instanceof COSDictionary) || selected instanceof COSStream)) { throw malformed(); }
            if (selected instanceof COSDictionary) {
                COSBase type = ((COSDictionary) selected).getDictionaryObject(COSName.TYPE);
                if (type != null && !COSName.getPDFName("CryptFilterDecodeParms").equals(type)) { throw malformed(); }
            }
            COSBase raw = selected instanceof COSDictionary
                    ? ((COSDictionary) selected).getDictionaryObject(COSName.NAME) : null;
            if (raw != null && !(raw instanceof COSName)) { throw malformed(); }
            selection = raw == null ? COSName.IDENTITY : (COSName) raw;
        }
        if (!COSName.IDENTITY.equals(selection) && !COSName.STD_CF.equals(selection)) { throw malformed(); }
        if (attachment != COSName.STD_CF.equals(selection)) { throw malformed(); }
        return attachment;
    }

    private static COSBase resolve(COSBase value) {
        return value instanceof COSObject ? ((COSObject) value).getObject() : value;
    }

    private static DocumentFailure malformed() {
        return PdfBoxWorkflowEngine.versionFailure(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "The embedded-file encryption routes are inconsistent.");
    }

    private static final class Node {
        final COSBase value;
        final int depth;
        Node(COSBase value, int depth) { this.value = value; this.depth = depth; }
    }
}
