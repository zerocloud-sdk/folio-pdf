package net.zerocloud.pdf;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSIncrement;
import org.apache.pdfbox.cos.COSObjectKey;

/** Includes changed Source objects outside the Catalog graph in an increment. */
final class PdfBoxIncrementalTrailer extends COSDictionary implements AutoCloseable {

    private final COSDocument document;
    private final COSDictionary original;
    private final Set<COSBase> sourceUpdates;

    private PdfBoxIncrementalTrailer(COSDocument document, Set<COSBase> sourceUpdates) {
        super(document.getTrailer());
        this.document = document;
        this.original = document.getTrailer();
        this.sourceUpdates = sourceUpdates;
    }

    static PdfBoxIncrementalTrailer prepare(COSDocument document, Set<COSBase> changedContainers,
            WorkflowResourceContext resources)
            throws DocumentFailure {
        Set<COSBase> sourceUpdates = new LinkedHashSet<COSBase>();
        for (COSObjectKey key : document.getXrefTable().keySet()) {
            resources.checkpoint();
            COSBase value = document.getObjectFromPool(key).getObject();
            if (containsDirectChange(value, changedContainers, resources)) {
                sourceUpdates.add(value);
            }
        }
        if (!sourceUpdates.isEmpty()) {
            PdfBoxIncrementalTrailer prepared = new PdfBoxIncrementalTrailer(document, sourceUpdates);
            document.setTrailer(prepared);
            return prepared;
        }
        return null;
    }

    private static boolean containsDirectChange(COSBase root, Set<COSBase> changes,
            WorkflowResourceContext resources) throws DocumentFailure {
        if (root == null) {
            return false;
        }
        Deque<COSBase> pending = new ArrayDeque<COSBase>();
        Set<COSBase> visited = Collections.newSetFromMap(new IdentityHashMap<COSBase, Boolean>());
        pending.push(root);
        while (!pending.isEmpty()) {
            resources.checkpoint();
            COSBase value = pending.pop();
            if (changes.contains(value)) {
                return true;
            }
            if (!visited.add(value)) {
                continue;
            }
            if (value instanceof COSDictionary) {
                for (COSBase child : ((COSDictionary) value).getValues()) {
                    resources.checkpoint();
                    if (child != null) {
                        pending.push(child);
                    }
                }
            } else if (value instanceof COSArray) {
                for (COSBase child : (COSArray) value) {
                    resources.checkpoint();
                    if (child != null) {
                        pending.push(child);
                    }
                }
            }
            // An indirect child has its own Source xref entry and is checked there.
        }
        return false;
    }

    @Override
    public COSIncrement toIncrement() {
        COSIncrement increment = super.toIncrement().exclude(this);
        increment.getObjects().addAll(sourceUpdates);
        return increment;
    }

    @Override
    public void close() {
        document.setTrailer(original);
    }
}
