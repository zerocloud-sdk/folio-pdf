package net.zerocloud.pdf;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.zerocloud.pdf.command.EmbedFile;
import net.zerocloud.pdf.command.ReplaceOutlineTree;
import net.zerocloud.pdf.command.SetNamedDestinations;
import net.zerocloud.pdf.command.SetXmpMetadata;
import net.zerocloud.pdf.command.UpdateDocumentInfo;
import net.zerocloud.pdf.query.DocumentInfo;
import net.zerocloud.pdf.query.EmbeddedFiles;
import net.zerocloud.pdf.query.NamedDestinations;
import net.zerocloud.pdf.query.OutlineTree;
import net.zerocloud.pdf.query.ReadEmbeddedFile;
import net.zerocloud.pdf.query.XmpMetadata;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSBoolean;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.multipdf.PDFCloneUtility;
import org.apache.pdfbox.pdmodel.PDDocument;

/**
 * Owns the PDFBox-specific mechanics behind the T11 metadata, outline,
 * destination, and attachment seam.
 */
final class PdfBoxMetadataOperations {

    static final String CAPABILITY_ID =
            "document.metadata.outlines-destinations-attachments";

    private static final int MAX_OUTLINE_DEPTH = 64;

    private static final int MAX_NAME_TREE_DEPTH = 64;

    private static final int MAX_METADATA_GRAPH_DEPTH = 64;

    private final PDDocument document;
    private final WorkflowResourceContext resources;
    private final java.util.Comparator<COSString> nameOrder;

    PdfBoxMetadataOperations(
            PDDocument document,
            WorkflowResourceContext resources) {
        this.document = Objects.requireNonNull(document, "document");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.nameOrder = new java.util.Comparator<COSString>() {
            @Override
            public int compare(COSString left, COSString right) {
                return PdfBoxStringSupport.compare(
                        left,
                        right,
                        PdfBoxMetadataOperations.this.resources);
            }
        };
    }

    boolean supports(DocumentCommand command) {
        return command instanceof UpdateDocumentInfo
                || command instanceof SetXmpMetadata
                || command instanceof SetNamedDestinations
                || command instanceof ReplaceOutlineTree
                || command instanceof EmbedFile;
    }

    boolean supportsQuery(DocumentQuery<?> query) {
        return query instanceof DocumentInfo
                || query instanceof XmpMetadata
                || query instanceof NamedDestinations
                || query instanceof OutlineTree
                || query instanceof EmbeddedFiles
                || query instanceof ReadEmbeddedFile;
    }

    void execute(DocumentCommand command) throws DocumentFailure {
        if (!supports(command)) {
            throw new IllegalArgumentException("Unsupported metadata command.");
        }
        try {
            resources.checkpoint();
            if (command instanceof UpdateDocumentInfo) {
                updateInfo((UpdateDocumentInfo) command);
            } else if (command instanceof SetXmpMetadata) {
                setXmpMetadata((SetXmpMetadata) command);
            } else if (command instanceof SetNamedDestinations) {
                setNamedDestinations((SetNamedDestinations) command);
            } else if (command instanceof ReplaceOutlineTree) {
                replaceOutlineTree((ReplaceOutlineTree) command);
            } else {
                embedFile((EmbedFile) command);
            }
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (RuntimeException backendFailure) {
            resources.rethrowResourceOrTerminalFailure(backendFailure);
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The metadata operation could not be completed safely.");
        }
    }

    Object evaluate(DocumentQuery<?> query) throws DocumentFailure {
        if (!supportsQuery(query)) {
            throw new IllegalArgumentException("Unsupported metadata query.");
        }
        try {
            resources.checkpoint();
            if (query instanceof DocumentInfo) {
                return documentInfo();
            }
            if (query instanceof XmpMetadata) {
                return xmpMetadata((XmpMetadata) query);
            }
            if (query instanceof NamedDestinations) {
                return namedDestinations((NamedDestinations) query);
            }
            if (query instanceof OutlineTree) {
                return outlineTree((OutlineTree) query);
            }
            if (query instanceof EmbeddedFiles) {
                return embeddedFiles((EmbeddedFiles) query);
            }
            if (query instanceof ReadEmbeddedFile) {
                return readEmbeddedFile((ReadEmbeddedFile) query);
            }
            throw new IllegalStateException("Unsupported metadata query.");
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (RuntimeException backendFailure) {
            resources.rethrowResourceOrTerminalFailure(backendFailure);
            throw failure(
                    DocumentFailureCode.QUERY_FAILED,
                    "The metadata query could not be evaluated safely.");
        }
    }

    /**
     * States whether one catalog entry is managed by this seam.
     *
     * @param name the catalog entry name
     * @return true when the entry is a T11-managed structure
     */
    static boolean isManagedCatalogEntry(COSName name) {
        return COSName.METADATA.equals(name)
                || COSName.NAMES.equals(name)
                || COSName.OUTLINES.equals(name);
    }

    /**
     * Rejects a page removal that would orphan a managed destination.
     *
     * @param candidate the document about to lose pages
     * @param removed the zero-based removed page indexes
     * @throws DocumentFailure when a managed destination targets a removed page
     */
    void requireNoDestinationConflict(
            PDDocument candidate,
            java.util.Set<Integer> removed) throws DocumentFailure {
        IdentityHashMap<COSDictionary, Integer> pageNumbers =
                pageNumbersByDictionary(candidate, StructureFailure.PRESERVE);
        java.util.TreeMap<COSString, COSBase> destinations =
                destinationEntriesByName(candidate);
        for (Map.Entry<COSString, COSBase> entry : destinations.entrySet()) {
            resources.checkpoint();
            if (removed.contains(Integer.valueOf(destinationPageIndex(
                    entry.getValue(),
                    pageNumbers)))) {
                throw destinationConflict();
            }
        }
        COSBase rawOutlines = candidate.getDocumentCatalog().getCOSObject()
                .getItem(COSName.OUTLINES);
        if (rawOutlines != null) {
            List<OutlineNode> nodes = readOutlineNodes(
                    rawOutlines,
                    pageNumbers,
                    namedNamesOf(destinations),
                    StructureFailure.PRESERVE,
                    -1L);
            requireNoOutlineDestinationConflict(
                    nodes,
                    pageNumbers,
                    destinations,
                    removed);
        }
    }

    private void requireNoOutlineDestinationConflict(
            List<OutlineNode> nodes,
            IdentityHashMap<COSDictionary, Integer> pageNumbers,
            java.util.TreeMap<COSString, COSBase> destinations,
            java.util.Set<Integer> removed) throws DocumentFailure {
        for (OutlineNode node : nodes) {
            resources.checkpoint();
            if (node.destinationArray != null
                    && removed.contains(Integer.valueOf(
                            node.destinationPageIndex(pageNumbers)))) {
                throw destinationConflict();
            }
            if (node.namedName != null) {
                COSBase resolved = destinations.get(node.namedName);
                if (resolved != null
                        && removed.contains(Integer.valueOf(
                                destinationPageIndex(
                                        resolved,
                                        pageNumbers)))) {
                    throw destinationConflict();
                }
            }
            requireNoOutlineDestinationConflict(
                    node.children,
                    pageNumbers,
                    destinations,
                    removed);
        }
    }

    private int destinationPageIndex(
            COSBase rawArray,
            IdentityHashMap<COSDictionary, Integer> pageNumbers)
            throws DocumentFailure {
        COSBase pageValue = dereference(
                ((COSArray) dereference(rawArray)).get(0));
        for (Map.Entry<COSDictionary, Integer> entry
                : pageNumbers.entrySet()) {
            resources.checkpoint();
            if (dereference(entry.getKey()) == pageValue) {
                return entry.getValue().intValue() - 1;
            }
        }
        throw preservationUnsupported();
    }

    private static DocumentFailure destinationConflict() {
        return failure(
                DocumentFailureCode.DESTINATION_CONFLICT,
                "A page removal conflicts with an existing managed destination.");
    }

    /**
     * Copies and retargets every managed structure of a split product whose
     * pages were imported in selection order.
     *
     * @param product the split product document
     * @param mapping source page index (zero-based, in selection order) to
     *        product page index, or zero for pages outside the selection
     * @throws DocumentFailure when a managed structure cannot be retargeted
     */
    /**
     * Rebuilds the managed structures of one split product from a snapshot
     * of the source document, keeping only the entries whose destination
     * page survives in the product.
     *
     * @param product the split product to rebuild
     * @param snapshot the managed structures captured from the source
     * @param mapping source page index to one-based product page index
     * @throws DocumentFailure when a structure cannot be rebuilt safely
     */
    void retargetSplitStructures(
            PDDocument product,
            MergedStructures snapshot,
            int[] mapping) throws DocumentFailure {
        try (WorkflowResourceContext.OwnedMemoryScope ownership =
                resources.ownedMemoryScope()) {
            retargetSplitStructures(product, snapshot, mapping, ownership);
            ownership.transfer();
        }
    }

    private void retargetSplitStructures(
            PDDocument product,
            MergedStructures snapshot,
            int[] mapping,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        try {
            COSDictionary catalog = product.getDocumentCatalog()
                    .getCOSObject();
            applySplitInfo(product, snapshot.info, ownership);

            java.util.TreeMap<COSString, COSBase> destinations =
                    new java.util.TreeMap<COSString, COSBase>(nameOrder);
            for (Map.Entry<COSString, COSBase> entry
                    : snapshot.destinations.entrySet()) {
                resources.checkpoint();
                int mapped = mapping[destinationPageIndex(
                        entry.getValue(),
                        snapshot.pageNumbers)];
                if (mapped > 0) {
                    destinations.put(
                            PdfBoxStringSupport.backendCopy(
                                    entry.getKey(),
                                    resources,
                                    ownership,
                                    PdfBoxMetadataOperations::preservationUnsupported),
                            retargetedDestinationArray(
                                    entry.getValue(),
                                    product.getPage(mapped - 1)
                                            .getCOSObject()));
                }
            }
            java.util.TreeMap<COSString, COSDictionary> files =
                    new java.util.TreeMap<COSString, COSDictionary>(
                            nameOrder);
            for (Map.Entry<COSString, COSDictionary> entry
                    : snapshot.files.entrySet()) {
                resources.checkpoint();
                files.put(
                        PdfBoxStringSupport.backendCopy(
                                entry.getKey(),
                                resources,
                                ownership,
                                PdfBoxMetadataOperations::preservationUnsupported),
                        cloneFileSpecification(product, entry.getValue()));
            }
            replaceNamesDictionary(catalog, destinations, files);

            if (snapshot.outline != null) {
                List<OutlineNode> filtered = new java.util.ArrayList<
                        OutlineNode>();
                SplitTarget target = new SplitTarget(
                        product,
                        mapping,
                        snapshot.pageNumbers);
                for (OutlineNode node : snapshot.outline) {
                    resources.checkpoint();
                    OutlineNode retargeted = retargetedOutlineNode(
                            node,
                            destinations,
                            null,
                            target);
                    if (retargeted != null) {
                        filtered.add(retargeted);
                    }
                }
                writeOutlineTree(catalog, filtered, ownership);
            }

            if (snapshot.xmpPacket != null) {
                replaceMetadataStream(product, snapshot.xmpPacket);
            }
        } catch (DocumentFailure failure) {
            if (failure.getCode() == DocumentFailureCode.QUERY_FAILED) {
                throw preservationUnsupported();
            }
            throw failure;
        } catch (RuntimeException backendFailure) {
            resources.rethrowResourceOrTerminalFailure(backendFailure);
            throw preservationUnsupported();
        }
    }

    private static final long MAX_METADATA_PACKET_BYTES = 64L * 1024L * 1024L;

    private void applySplitInfo(
            PDDocument product,
            COSDictionary snapshot,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        COSDictionary trailer = product.getDocument().getTrailer();
        if (snapshot == null) {
            trailer.removeItem(COSName.INFO);
            return;
        }
        COSDictionary detached = new COSDictionary();
        for (Map.Entry<COSName, COSBase> entry : snapshot.entrySet()) {
            resources.checkpoint();
            detached.setItem(
                    entry.getKey(),
                    cloneMetadataValue(
                            entry.getValue(),
                            new IdentityHashMap<COSBase, COSBase>(),
                            0,
                            ownership));
        }
        trailer.setItem(COSName.INFO, detached);
    }

    private void replaceNamesDictionary(
            COSDictionary catalog,
            java.util.TreeMap<COSString, COSBase> destinations,
            java.util.TreeMap<COSString, COSDictionary> files)
            throws DocumentFailure {
        if (destinations.isEmpty() && files.isEmpty()) {
            catalog.removeItem(COSName.NAMES);
            return;
        }
        COSDictionary names = new COSDictionary();
        if (!destinations.isEmpty()) {
            names.setItem(COSName.DESTS, flatNameTree(destinations));
        }
        if (!files.isEmpty()) {
            names.setItem(COSName.EMBEDDED_FILES, flatNameTree(files));
        }
        catalog.setItem(COSName.NAMES, names);
    }

    private COSDictionary flatNameTree(
            java.util.TreeMap<COSString, ? extends COSBase> entries)
            throws DocumentFailure {
        COSArray keysAndValues = new COSArray();
        keysAndValues.setDirect(true);
        for (Map.Entry<COSString, ? extends COSBase> entry
                : entries.entrySet()) {
            resources.checkpoint();
            keysAndValues.add(entry.getKey());
            keysAndValues.add(entry.getValue());
        }
        COSDictionary tree = new COSDictionary();
        tree.setItem(COSName.NAMES, keysAndValues);
        return tree;
    }

    private COSArray retargetedDestinationArray(
            COSBase rawArray,
            COSBase productPageReference) throws DocumentFailure {
        COSArray original = (COSArray) dereference(rawArray);
        COSArray retargeted = new COSArray();
        retargeted.setDirect(true);
        retargeted.add(productPageReference);
        for (int index = 1; index < original.size(); index++) {
            resources.checkpoint();
            retargeted.add(original.get(index));
        }
        return retargeted;
    }

    private interface OutlineTarget {

        COSBase pageReference(int sourcePageIndex)
                throws DocumentFailure;

        IdentityHashMap<COSDictionary, Integer> sourcePageNumbers();
    }

    private static final class SplitTarget implements OutlineTarget {

        private final PDDocument product;
        private final int[] mapping;
        private final IdentityHashMap<COSDictionary, Integer> pageNumbers;

        private SplitTarget(
                PDDocument product,
                int[] mapping,
                IdentityHashMap<COSDictionary, Integer> pageNumbers) {
            this.product = product;
            this.mapping = mapping;
            this.pageNumbers = pageNumbers;
        }

        @Override
        public COSBase pageReference(int sourcePageIndex) {
            int mapped = mapping[sourcePageIndex];
            return mapped > 0
                    ? product.getPage(mapped - 1).getCOSObject()
                    : null;
        }

        @Override
        public IdentityHashMap<COSDictionary, Integer>
                sourcePageNumbers() {
            return pageNumbers;
        }
    }

    private OutlineNode retargetedOutlineNode(
            OutlineNode node,
            java.util.TreeMap<COSString, COSBase> destinations,
            java.util.TreeMap<COSString, COSString> renames,
            OutlineTarget target) throws DocumentFailure {
        resources.checkpoint();
        List<OutlineNode> children = new java.util.ArrayList<OutlineNode>();
        for (OutlineNode child : node.children) {
            OutlineNode retargeted = retargetedOutlineNode(
                    child,
                    destinations,
                    renames,
                    target);
            if (retargeted != null) {
                children.add(retargeted);
            }
        }
        COSBase destinationArray = null;
        COSString namedName = node.namedName;
        if (node.destinationArray != null) {
            COSBase pageReference = target.pageReference(
                    node.destinationPageIndex(target.sourcePageNumbers()));
            if (pageReference == null) {
                if (children.isEmpty()) {
                    return null;
                }
            } else {
                destinationArray = retargetedDestinationArray(
                        node.destinationArray,
                        pageReference);
                namedName = null;
            }
        } else if (namedName != null) {
            COSString rewritten = renames == null
                    ? namedName
                    : renames.get(namedName);
            COSBase resolved = rewritten == null
                    ? null
                    : destinations.get(rewritten);
            if (resolved == null) {
                if (children.isEmpty()) {
                    return null;
                }
                namedName = null;
            } else {
                namedName = rewritten;
            }
        }
        return new OutlineNode(node.title, destinationArray, namedName,
                children);
    }

    private void writeOutlineTree(
            COSDictionary catalog,
            List<OutlineNode> nodes,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        if (nodes.isEmpty()) {
            catalog.removeItem(COSName.OUTLINES);
            return;
        }
        COSDictionary root = new COSDictionary();
        root.setItem(COSName.TYPE, COSName.getPDFName("Outlines"));
        int visibleTotal = writeOutlineNodeLevel(
                nodes, root, 1, ownership);
        root.setItem(COSName.COUNT, COSInteger.get(visibleTotal));
        catalog.setItem(COSName.OUTLINES, root);
    }

    private int writeOutlineNodeLevel(
            List<OutlineNode> nodes,
            COSDictionary parent,
            int depth,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        resources.checkpoint();
        resources.requireNestingDepth(depth);
        if (depth > MAX_OUTLINE_DEPTH) {
            throw preservationUnsupported();
        }
        COSDictionary first = null;
        COSDictionary previous = null;
        int visibleTotal = 0;
        for (OutlineNode node : nodes) {
            resources.checkpoint();
            COSDictionary dictionary = new COSDictionary();
            dictionary.setItem(COSName.TITLE,
                    PdfBoxStringSupport.backendString(
                            node.title,
                            resources,
                            ownership,
                            PdfBoxMetadataOperations::preservationUnsupported));
            dictionary.setItem(COSName.PARENT, parent);
            if (previous != null) {
                previous.setItem(COSName.NEXT, dictionary);
                dictionary.setItem(COSName.PREV, previous);
            }
            if (first == null) {
                first = dictionary;
            }
            if (node.destinationArray != null) {
                COSArray direct = new COSArray();
                direct.setDirect(true);
                COSArray original = (COSArray) dereference(
                        node.destinationArray);
                for (int index = 0; index < original.size(); index++) {
                    resources.checkpoint();
                    direct.add(original.get(index));
                }
                dictionary.setItem(COSName.DEST, direct);
            } else if (node.namedName != null) {
                dictionary.setItem(COSName.DEST,
                        PdfBoxStringSupport.backendCopy(
                                node.namedName,
                                resources,
                                ownership,
                                PdfBoxMetadataOperations::preservationUnsupported));
            }
            int descendants = 0;
            if (!node.children.isEmpty()) {
                descendants = writeOutlineNodeLevel(
                        node.children,
                        dictionary,
                        depth + 1,
                        ownership);
                dictionary.setItem(COSName.COUNT, COSInteger.get(descendants));
            }
            visibleTotal += 1 + descendants;
            previous = dictionary;
        }
        parent.setItem(COSName.FIRST, first);
        parent.setItem(COSName.LAST, previous);
        return visibleTotal;
    }

    private void replaceMetadataStream(
            PDDocument target,
            byte[] packet) throws DocumentFailure {
        try {
            COSStream fresh = target.getDocument().createCOSStream();
            fresh.setItem(COSName.TYPE, COSName.METADATA);
            fresh.setItem(COSName.SUBTYPE, COSName.getPDFName("XML"));
            try (OutputStream copied = fresh.createOutputStream()) {
                resources.writeBytesAsIOException(copied, packet);
            }
            target.getDocumentCatalog().getCOSObject().setItem(
                    COSName.METADATA,
                    fresh);
        } catch (IOException | RuntimeException streamFailure) {
            resources.rethrowResourceOrTerminalFailure(streamFailure);
            throw preservationUnsupported();
        }
    }

    private COSDictionary cloneFileSpecification(
            PDDocument target,
            COSDictionary fileSpecification) throws DocumentFailure {
        try {
            return (COSDictionary) new MetadataCloneUtility(target)
                    .cloneForNewDocument(fileSpecification);
        } catch (IOException | RuntimeException cloneFailure) {
            resources.rethrowResourceOrTerminalFailure(cloneFailure);
            throw preservationUnsupported();
        }
    }

    private static final class MetadataCloneUtility
            extends PDFCloneUtility {

        private MetadataCloneUtility(PDDocument target) {
            super(target);
        }
    }

    /**
     * The managed structures captured from one merge source.
     */
    final class MergedStructures implements AutoCloseable {

        private final int pageCount;
        private final IdentityHashMap<COSDictionary, Integer> pageNumbers;
        private final COSDictionary info;
        private WorkflowResourceContext.OwnedMemoryScope infoOwnership;
        private final byte[] xmpPacket;
        private WorkflowResourceContext.OwnedBytes xmpPacketBytes;
        private final java.util.TreeMap<COSString, COSBase> destinations;
        private final java.util.TreeMap<COSString, COSDictionary> files;
        private final List<OutlineNode> outline;

        private MergedStructures(
                int pageCount,
                IdentityHashMap<COSDictionary, Integer> pageNumbers,
                COSDictionary info,
                WorkflowResourceContext.OwnedMemoryScope infoOwnership,
                WorkflowResourceContext.OwnedBytes xmpPacketBytes,
                java.util.TreeMap<COSString, COSBase> destinations,
                java.util.TreeMap<COSString, COSDictionary> files,
                List<OutlineNode> outline) {
            this.pageCount = pageCount;
            this.pageNumbers = pageNumbers;
            this.info = info;
            this.infoOwnership = infoOwnership;
            this.xmpPacketBytes = xmpPacketBytes;
            this.xmpPacket = xmpPacketBytes == null
                    ? null : xmpPacketBytes.getBytes();
            this.destinations = destinations;
            this.files = files;
            this.outline = outline;
        }

        @Override
        public void close() {
            WorkflowResourceContext.OwnedMemoryScope currentInfo =
                    infoOwnership;
            if (currentInfo != null) {
                infoOwnership = null;
                currentInfo.close();
            }
            WorkflowResourceContext.OwnedBytes current = xmpPacketBytes;
            if (current != null) {
                xmpPacketBytes = null;
                current.close();
            }
        }
    }

    /**
     * Captures the managed structures of a source document without
     * modifying it.
     *
     * @param source the document to capture
     * @return the captured structures
     * @throws DocumentFailure when a structure cannot be captured safely
     */
    MergedStructures snapshotManagedStructures(PDDocument source)
            throws DocumentFailure {
        WorkflowResourceContext.OwnedBytes packet = null;
        WorkflowResourceContext.OwnedMemoryScope infoOwnership =
                resources.ownedMemoryScope();
        try {
            COSDictionary catalog = source.getDocumentCatalog().getCOSObject();
            IdentityHashMap<COSDictionary, Integer> pageNumbers =
                    pageNumbersByDictionary(source, StructureFailure.PRESERVE);

            COSDictionary info = snapshotInfo(source, infoOwnership);

            COSBase rawMetadata = catalog.getItem(COSName.METADATA);
            if (rawMetadata != null) {
                packet = boundedDecodedContentWorking(
                        (COSStream) dereference(rawMetadata),
                        MAX_METADATA_PACKET_BYTES);
            }

            java.util.TreeMap<COSString, COSBase> destinations =
                    destinationEntriesByName(source);
            java.util.TreeMap<COSString, COSDictionary> files =
                    new java.util.TreeMap<COSString, COSDictionary>(
                            nameOrder);
            for (Map.Entry<COSString, COSDictionary> entry
                    : embeddedFileEntriesOf(
                            source,
                            StructureFailure.PRESERVE).entrySet()) {
                resources.checkpoint();
                files.put(entry.getKey(), entry.getValue());
            }

            List<OutlineNode> outline = null;
            COSBase rawOutlines = catalog.getItem(COSName.OUTLINES);
            if (rawOutlines != null) {
                outline = readOutlineNodes(
                        rawOutlines,
                        pageNumbers,
                        namedNamesOf(destinations),
                        StructureFailure.PRESERVE,
                        -1L);
            }
            MergedStructures result = new MergedStructures(
                    source.getNumberOfPages(),
                    pageNumbers,
                    info,
                    infoOwnership,
                    packet,
                    destinations,
                    files,
                    outline);
            packet = null;
            infoOwnership = null;
            return result;
        } catch (DocumentFailure failure) {
            closeQuietly(packet);
            closeQuietly(infoOwnership);
            if (failure.getCode() == DocumentFailureCode.QUERY_FAILED
                    || failure.getCode()
                            == DocumentFailureCode.METADATA_LIMIT_EXCEEDED) {
                throw preservationUnsupported();
            }
            throw failure;
        } catch (RuntimeException backendFailure) {
            closeQuietly(packet);
            closeQuietly(infoOwnership);
            resources.rethrowResourceOrTerminalFailure(backendFailure);
            throw preservationUnsupported();
        }
    }

    MergedStructures extractAndStripManagedStructures(PDDocument source)
            throws DocumentFailure {
        MergedStructures snapshot = snapshotManagedStructures(source);
        COSDictionary catalog = source.getDocumentCatalog().getCOSObject();
        source.getDocument().getTrailer().removeItem(COSName.INFO);
        catalog.removeItem(COSName.METADATA);
        catalog.removeItem(COSName.NAMES);
        catalog.removeItem(COSName.OUTLINES);
        return snapshot;
    }

    private java.util.TreeMap<COSString, COSBase> destinationEntriesByName(
            PDDocument source) throws DocumentFailure {
        java.util.TreeMap<COSString, COSBase> destinations =
                new java.util.TreeMap<COSString, COSBase>(nameOrder);
        for (NameTreeEntry entry : destinationEntriesOf(
                source,
                StructureFailure.PRESERVE)) {
            resources.checkpoint();
            destinations.put(entry.key, entry.value);
        }
        return destinations;
    }

    java.util.Set<String> namedDestinationNames(PDDocument source)
            throws DocumentFailure {
        java.util.Set<String> names = new java.util.HashSet<String>();
        for (COSString name : destinationEntriesByName(source).keySet()) {
            resources.checkpoint();
            names.add(name.getString());
        }
        return names;
    }

    private java.util.TreeSet<COSString> namedNamesOf(
            java.util.TreeMap<COSString, COSBase> destinations)
            throws DocumentFailure {
        java.util.TreeSet<COSString> names =
                new java.util.TreeSet<COSString>(nameOrder);
        for (COSString name : destinations.keySet()) {
            resources.checkpoint();
            names.add(name);
        }
        return names;
    }

    List<Map<String, String>> applyMergedStructures(
            PDDocument target,
            List<MergedStructures> sources,
            boolean primaryHadInfo) throws DocumentFailure {
        try (WorkflowResourceContext.OwnedMemoryScope ownership =
                resources.ownedMemoryScope()) {
            List<Map<String, String>> result = applyMergedStructures(
                    target, sources, primaryHadInfo, ownership);
            ownership.transfer();
            return result;
        }
    }

    private List<Map<String, String>> applyMergedStructures(
            PDDocument target,
            List<MergedStructures> sources,
            boolean primaryHadInfo,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        List<Map<String, String>> sourceRenames =
                new java.util.ArrayList<Map<String, String>>();
        try {
            COSDictionary catalog = target.getDocumentCatalog().getCOSObject();
            IdentityHashMap<COSDictionary, Integer> pageNumbers =
                    pageNumbersByDictionary(target, StructureFailure.PRESERVE);
            List<COSBase> pageReferences = rawPageReferences(
                    target,
                    StructureFailure.PRESERVE);

            List<COSDictionary> infoSnapshots =
                    new java.util.ArrayList<COSDictionary>();
            for (MergedStructures source : sources) {
                resources.checkpoint();
                infoSnapshots.add(source.info);
            }
            applyMergedInfo(
                    target, infoSnapshots, primaryHadInfo, ownership);

            if (catalog.getItem(COSName.METADATA) == null) {
                for (MergedStructures source : sources) {
                    resources.checkpoint();
                    if (source.xmpPacket != null) {
                        replaceMetadataStream(target, source.xmpPacket);
                        break;
                    }
                }
            }

            java.util.TreeMap<COSString, COSBase> destinations =
                    destinationEntriesByName(target);
            java.util.TreeMap<COSString, COSDictionary> files =
                    new java.util.TreeMap<COSString, COSDictionary>(
                            nameOrder);
            for (Map.Entry<COSString, COSDictionary> entry
                    : embeddedFileEntriesOf(
                            target,
                            StructureFailure.PRESERVE).entrySet()) {
                resources.checkpoint();
                files.put(entry.getKey(), entry.getValue());
            }

            List<OutlineNode> outline =
                    new java.util.ArrayList<OutlineNode>();
            COSBase rawOutlines = catalog.getItem(COSName.OUTLINES);
            if (rawOutlines != null) {
                List<OutlineNode> existingOutline = readOutlineNodes(
                        rawOutlines,
                        pageNumbers,
                        namedNamesOf(destinations),
                        StructureFailure.PRESERVE,
                        -1L);
                for (OutlineNode node : existingOutline) {
                    resources.checkpoint();
                    outline.add(node);
                }
            }

            int pageOffset = target.getNumberOfPages();
            for (MergedStructures source : sources) {
                resources.checkpoint();
                pageOffset -= source.pageCount;
            }
            final List<COSBase> references = pageReferences;
            int base = pageOffset;
            for (MergedStructures source : sources) {
                resources.checkpoint();
                final int sourceBase = base;
                final IdentityHashMap<COSDictionary, Integer> sourcePages =
                        source.pageNumbers;
                java.util.TreeMap<COSString, COSString> renames =
                        new java.util.TreeMap<COSString, COSString>(
                                nameOrder);
                for (Map.Entry<COSString, COSBase> entry
                        : source.destinations.entrySet()) {
                    resources.checkpoint();
                    int sourceIndex = destinationPageIndex(
                            entry.getValue(),
                            sourcePages);
                    COSString finalKey = availableKey(
                            entry.getKey(),
                            destinations.keySet(),
                            ownership);
                    renames.put(entry.getKey(), finalKey);
                    destinations.put(
                            finalKey,
                            retargetedDestinationArray(
                                    entry.getValue(),
                                    references.get(sourceBase + sourceIndex)));
                }
                Map<String, String> publicRenames =
                        new java.util.LinkedHashMap<String, String>();
                for (Map.Entry<COSString, COSString> rename
                        : renames.entrySet()) {
                    resources.checkpoint();
                    publicRenames.put(
                            rename.getKey().getString(),
                            rename.getValue().getString());
                }
                sourceRenames.add(publicRenames);
                for (Map.Entry<COSString, COSDictionary> entry
                        : source.files.entrySet()) {
                    resources.checkpoint();
                    files.put(
                            availableKey(
                                    entry.getKey(),
                                    files.keySet(),
                                    ownership),
                            cloneFileSpecification(target, entry.getValue()));
                }
                if (source.outline != null) {
                    for (OutlineNode node : source.outline) {
                        resources.checkpoint();
                        outline.add(retargetedOutlineNode(
                                node,
                                destinations,
                                renames,
                                new OutlineTarget() {
                                    @Override
                                    public COSBase pageReference(
                                            int sourcePageIndex) {
                                        return references.get(
                                                sourceBase + sourcePageIndex);
                                    }

                                    @Override
                                    public IdentityHashMap<COSDictionary,
                                            Integer> sourcePageNumbers() {
                                        return sourcePages;
                                    }
                                }));
                    }
                }
                base += source.pageCount;
            }

            replaceNamesDictionary(catalog, destinations, files);
            writeOutlineTree(catalog, outline, ownership);
        } catch (DocumentFailure failure) {
            if (failure.getCode() == DocumentFailureCode.QUERY_FAILED) {
                throw preservationUnsupported();
            }
            throw failure;
        } catch (RuntimeException backendFailure) {
            resources.rethrowResourceOrTerminalFailure(backendFailure);
            throw preservationUnsupported();
        }
        return sourceRenames;
    }

    private COSString availableKey(
            COSString preferred,
            java.util.Set<COSString> taken,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        int suffix = 0;
        while (true) {
            resources.checkpoint();
            try (WorkflowResourceContext.OwnedMemoryScope candidateOwnership =
                    resources.ownedMemoryScope()) {
                COSString candidate = suffix == 0
                        ? PdfBoxStringSupport.backendCopy(
                                preferred,
                                resources,
                                candidateOwnership,
                                PdfBoxMetadataOperations::preservationUnsupported)
                        : PdfBoxStringSupport.backendCopyWithAsciiSuffix(
                                preferred,
                                "-" + suffix,
                                resources,
                                candidateOwnership,
                                PdfBoxMetadataOperations::preservationUnsupported);
                if (!taken.contains(candidate)) {
                    candidateOwnership.transferTo(ownership);
                    return candidate;
                }
            }
            suffix++;
        }
    }

    /**
     * Proves that the trailer information dictionary of a candidate document
     * can be preserved unchanged by page operations.
     *
     * @param candidate the document about to change
     * @throws DocumentFailure when the information graph is not provably safe
     */
    void requireSafeInfoPreservable(PDDocument candidate)
            throws DocumentFailure {
        COSDictionary trailer = candidate.getDocument().getTrailer();
        if (trailer == null) {
            return;
        }
        COSBase rawInfo = trailer.getItem(COSName.INFO);
        if (rawInfo == null) {
            return;
        }
        COSBase infoValue = dereference(rawInfo);
        if (!(infoValue instanceof COSDictionary)) {
            throw preservationUnsupported();
        }
        COSDictionary info = (COSDictionary) infoValue;
        if (info == candidate.getDocumentCatalog().getCOSObject()) {
            throw preservationUnsupported();
        }
        requireMetadataSafeGraph(info);
    }

    /**
     * Captures the trailer information dictionary of a merge source as an
     * independent graph before the source is stripped.
     *
     * @param source a validated merge source
     * @return the detached information snapshot, or {@code null} when absent
     * @throws DocumentFailure when the information graph is not provably safe
     */
    COSDictionary snapshotInfo(
            PDDocument source,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        COSBase rawInfo = source.getDocument().getTrailer().getItem(
                COSName.INFO);
        COSBase infoValue = dereference(rawInfo);
        if (!(infoValue instanceof COSDictionary)
                || ((COSDictionary) infoValue).size() == 0) {
            return null;
        }
        COSDictionary snapshot = new COSDictionary();
        for (Map.Entry<COSName, COSBase> entry
                : ((COSDictionary) infoValue).entrySet()) {
            snapshot.setItem(
                    entry.getKey(),
                    cloneMetadataValue(
                            entry.getValue(),
                            new IdentityHashMap<COSBase, COSBase>(),
                            0,
                            ownership));
        }
        return snapshot;
    }

    private COSBase cloneMetadataValue(
            COSBase rawValue,
            IdentityHashMap<COSBase, COSBase> cloned,
            int depth,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        resources.checkpoint();
        resources.requireNestingDepth(depth);
        if (depth > MAX_METADATA_GRAPH_DEPTH) {
            throw preservationUnsupported();
        }
        COSBase value = dereference(rawValue);
        if (value == null) {
            return COSNull.NULL;
        }
        COSBase existing = cloned.get(value);
        if (existing != null) {
            return existing;
        }
        if (value instanceof COSString) {
            return PdfBoxStringSupport.backendCopy(
                    (COSString) value,
                    resources,
                    ownership,
                    PdfBoxMetadataOperations::preservationUnsupported);
        }
        if (value instanceof COSArray) {
            COSArray array = (COSArray) value;
            COSArray copy = new COSArray();
            copy.setDirect(array.isDirect());
            cloned.put(array, copy);
            for (int index = 0; index < array.size(); index++) {
                copy.add(cloneMetadataValue(
                            array.get(index),
                            cloned,
                            depth + 1,
                            ownership));
            }
            return copy;
        }
        if (value instanceof COSDictionary) {
            COSDictionary dictionary = (COSDictionary) value;
            COSDictionary copy = new COSDictionary();
            copy.setDirect(dictionary.isDirect());
            cloned.put(dictionary, copy);
            for (Map.Entry<COSName, COSBase> entry : dictionary.entrySet()) {
                copy.setItem(
                        entry.getKey(),
                        cloneMetadataValue(
                                entry.getValue(),
                                cloned,
                                depth + 1,
                                ownership));
            }
            return copy;
        }
        return value;
    }

    /**
     * Merges source information snapshots into the primary document,
     * keeping the primary's own entries on name collisions.
     *
     * @param target the primary document
     * @param snapshots ordered source information snapshots
     * @param primaryHadInfo whether the primary trailer declared an
     *        information dictionary before the backend page import
     * @throws DocumentFailure when the information cannot be merged safely
     */
    void applyMergedInfo(
            PDDocument target,
            List<COSDictionary> snapshots,
            boolean primaryHadInfo,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        for (COSDictionary snapshot : snapshots) {
            resources.checkpoint();
            if (snapshot == null) {
                continue;
            }
            COSDictionary info = writableInfoFor(target);
            for (Map.Entry<COSName, COSBase> entry : snapshot.entrySet()) {
                resources.checkpoint();
                if (!info.containsKey(entry.getKey())) {
                    info.setItem(
                            entry.getKey(),
                            cloneMetadataValue(
                                    entry.getValue(),
                                    new IdentityHashMap<COSBase, COSBase>(),
                                    0,
                                    ownership));
                }
            }
        }
        if (!primaryHadInfo) {
            COSBase infoValue = dereference(
                    target.getDocument().getTrailer().getItem(COSName.INFO));
            if (infoValue instanceof COSDictionary
                    && ((COSDictionary) infoValue).size() == 0) {
                target.getDocument().getTrailer().removeItem(COSName.INFO);
            }
        }
    }

    private COSDictionary writableInfoFor(PDDocument target)
            throws DocumentFailure {
        COSDictionary trailer = target.getDocument().getTrailer();
        COSBase rawInfo = trailer.getItem(COSName.INFO);
        COSBase infoValue = dereference(rawInfo);
        if (infoValue instanceof COSDictionary) {
            return (COSDictionary) infoValue;
        }
        if (rawInfo != null) {
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The document information could not be updated safely.");
        }
        COSDictionary info = new COSDictionary();
        trailer.setItem(COSName.INFO, info);
        return info;
    }

    /**
     * Proves that every managed catalog structure of a candidate document can
     * be preserved unchanged by page operations.
     *
     * @param candidate the document about to change
     * @throws DocumentFailure when a managed structure is not provably safe
     */
    void requireSafeCatalogStructures(PDDocument candidate)
            throws DocumentFailure {
        COSDictionary catalog = candidate.getDocumentCatalog().getCOSObject();
        COSBase rawMetadata = catalog.getItem(COSName.METADATA);
        if (rawMetadata != null) {
            COSBase metadata = dereference(rawMetadata);
            if (!(metadata instanceof COSStream)
                    || !COSName.METADATA.equals(dereference(
                            ((COSStream) metadata).getItem(COSName.TYPE)))
                    || !COSName.getPDFName("XML").equals(dereference(
                            ((COSStream) metadata).getItem(COSName.SUBTYPE)))) {
                throw preservationUnsupported();
            }
            for (COSBase entry : ((COSStream) metadata).getValues()) {
                requireMetadataSafeValue(
                        entry,
                        new IdentityHashMap<COSBase, Boolean>(),
                        0);
            }
        }
        COSBase rawNames = catalog.getItem(COSName.NAMES);
        List<NameTreeEntry> destinationEntries =
                new java.util.ArrayList<NameTreeEntry>();
        IdentityHashMap<COSDictionary, Integer> pageNumbers = null;
        if (rawNames != null) {
            COSBase names = dereference(rawNames);
            if (!(names instanceof COSDictionary)) {
                throw preservationUnsupported();
            }
            pageNumbers = pageNumbersByDictionary(candidate);
            for (Map.Entry<COSName, COSBase> subtree
                    : ((COSDictionary) names).entrySet()) {
                if (COSName.DESTS.equals(subtree.getKey())) {
                    destinationEntries = readNameTreeEntries(
                            subtree.getValue(),
                            StructureFailure.PRESERVE,
                            -1L);
                    for (NameTreeEntry entry : destinationEntries) {
                        requireSafeDestinationArray(entry.value, pageNumbers);
                    }
                } else if (COSName.EMBEDDED_FILES.equals(
                        subtree.getKey())) {
                    embeddedFileEntriesOf(candidate, StructureFailure.PRESERVE);
                } else {
                    throw preservationUnsupported();
                }
            }
        }
        COSBase rawOutlines = catalog.getItem(COSName.OUTLINES);
        if (rawOutlines != null) {
            if (pageNumbers == null) {
                pageNumbers = pageNumbersByDictionary(candidate);
            }
            java.util.TreeSet<COSString> namedNames =
                    new java.util.TreeSet<COSString>(nameOrder);
            for (NameTreeEntry entry : destinationEntries) {
                resources.checkpoint();
                namedNames.add(entry.key);
            }
            readOutlineItems(
                    rawOutlines,
                    pageNumbers,
                    namedNames,
                    StructureFailure.PRESERVE,
                    -1L);
        }
    }

    private void requireSafeDestinationArray(
            COSBase rawValue,
            IdentityHashMap<COSDictionary, Integer> pageNumbers)
            throws DocumentFailure {
        if (destinationFromArray(rawValue, pageNumbers) == null) {
            throw preservationUnsupported();
        }
    }

    private void setNamedDestinations(SetNamedDestinations command)
            throws DocumentFailure {
        try (WorkflowResourceContext.OwnedMemoryScope ownership =
                resources.ownedMemoryScope()) {
            setNamedDestinations(command, ownership);
            ownership.transfer();
        }
    }

    private void setNamedDestinations(
            SetNamedDestinations command,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        List<COSBase> pageReferences = rawPageReferences(
                document,
                StructureFailure.COMMAND);
        for (PageDestination destination : command.getEntries().values()) {
            resources.checkpoint();
            if (destination.getPageNumber() > pageReferences.size()) {
                throw failure(
                        DocumentFailureCode.PAGE_RANGE_INVALID,
                        "The destination page is outside the current document.");
            }
        }

        COSDictionary catalog = document.getDocumentCatalog().getCOSObject();
        COSBase rawNames = catalog.getItem(COSName.NAMES);
        COSDictionary names;
        List<NameTreeEntry> current;
        if (rawNames == null) {
            names = null;
            current = new java.util.ArrayList<NameTreeEntry>();
        } else {
            COSBase namesValue = dereference(rawNames);
            if (!(namesValue instanceof COSDictionary)) {
                throw invalidDestinationsCommand();
            }
            names = (COSDictionary) namesValue;
            COSBase rawTree = names.getItem(COSName.DESTS);
            current = rawTree == null
                    ? new java.util.ArrayList<NameTreeEntry>()
                    : readNameTreeEntries(
                            rawTree,
                            StructureFailure.COMMAND,
                            -1L);
            IdentityHashMap<COSDictionary, Integer> pageNumbers =
                    pageNumbersByDictionary(document, StructureFailure.COMMAND);
            for (NameTreeEntry entry : current) {
                resources.checkpoint();
                if (destinationFromArray(entry.value, pageNumbers) == null) {
                    throw invalidDestinationsCommand();
                }
            }
        }

        java.util.TreeMap<COSString, COSBase> merged =
                new java.util.TreeMap<COSString, COSBase>(nameOrder);
        for (NameTreeEntry entry : current) {
            resources.checkpoint();
            merged.put(entry.key, entry.value);
        }
        for (String removedName : command.getRemovedNames()) {
            resources.checkpoint();
            try (WorkflowResourceContext.OwnedMemoryScope probeOwnership =
                    resources.ownedMemoryScope()) {
                COSString probe = PdfBoxStringSupport.backendString(
                        removedName,
                        resources,
                        probeOwnership,
                        PdfBoxMetadataOperations::invalidDestinationsCommand);
                merged.remove(probe);
            }
        }
        for (Map.Entry<String, PageDestination> entry
                : command.getEntries().entrySet()) {
            resources.checkpoint();
            PageDestination destination = entry.getValue();
            COSArray array = destinationToArray(
                    destination,
                    pageReferences.get(destination.getPageNumber() - 1),
                    ownership);
            merged.put(PdfBoxStringSupport.backendString(
                    entry.getKey(),
                    resources,
                    ownership,
                    PdfBoxMetadataOperations::invalidDestinationsCommand),
                    array);
        }

        if (merged.isEmpty()) {
            if (names != null) {
                names.removeItem(COSName.DESTS);
                if (names.size() == 0) {
                    catalog.removeItem(COSName.NAMES);
                }
            }
            return;
        }
        if (names == null) {
            names = new COSDictionary();
            catalog.setItem(COSName.NAMES, names);
        }
        COSArray keysAndValues = new COSArray();
        keysAndValues.setDirect(true);
        for (Map.Entry<COSString, COSBase> entry : merged.entrySet()) {
            resources.checkpoint();
            keysAndValues.add(entry.getKey());
            keysAndValues.add(entry.getValue());
        }
        COSDictionary tree = new COSDictionary();
        tree.setItem(COSName.NAMES, keysAndValues);
        names.setItem(COSName.DESTS, tree);
    }

    private Map<String, PageDestination> namedDestinations(
            NamedDestinations query) throws DocumentFailure {
        COSBase rawNames = document.getDocumentCatalog().getCOSObject()
                .getItem(COSName.NAMES);
        if (rawNames == null) {
            return Collections.emptyMap();
        }
        COSBase names = dereference(rawNames);
        if (!(names instanceof COSDictionary)) {
            throw unsafeDestinationsQuery();
        }
        COSBase rawTree = ((COSDictionary) names).getItem(COSName.DESTS);
        if (rawTree == null) {
            return Collections.emptyMap();
        }
        List<NameTreeEntry> entries = readNameTreeEntries(
                rawTree,
                StructureFailure.QUERY,
                query.getMaximumEntries());
        IdentityHashMap<COSDictionary, Integer> pageNumbers =
                pageNumbersByDictionary(document);
        Map<String, PageDestination> destinations =
                new LinkedHashMap<String, PageDestination>();
        for (NameTreeEntry entry : entries) {
            resources.checkpoint();
            PageDestination destination = destinationFromArray(
                    entry.value,
                    pageNumbers);
            if (destination == null) {
                throw unsafeDestinationsQuery();
            }
            destinations.put(entry.key.getString(), destination);
        }
        return Collections.unmodifiableMap(destinations);
    }

    private void replaceOutlineTree(ReplaceOutlineTree command)
            throws DocumentFailure {
        try (WorkflowResourceContext.OwnedMemoryScope ownership =
                resources.ownedMemoryScope()) {
            replaceOutlineTreeGuarded(command, ownership);
            ownership.transfer();
        } catch (DocumentFailure failure) {
            if (failure.getCode() == DocumentFailureCode.COMMAND_REJECTED) {
                throw invalidOutlineCommand();
            }
            throw failure;
        }
    }

    private void replaceOutlineTreeGuarded(
            ReplaceOutlineTree command,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        List<COSBase> pageReferences = rawPageReferences(
                document,
                StructureFailure.COMMAND);
        IdentityHashMap<COSDictionary, Integer> pageNumbers =
                pageNumbersByDictionary(document, StructureFailure.COMMAND);
        java.util.TreeSet<COSString> namedNames =
                new java.util.TreeSet<COSString>(nameOrder);
        for (NameTreeEntry entry : destinationEntriesOf(
                document,
                StructureFailure.COMMAND)) {
            resources.checkpoint();
            namedNames.add(entry.key);
        }

        COSDictionary catalog = document.getDocumentCatalog().getCOSObject();
        COSBase rawOutlines = catalog.getItem(COSName.OUTLINES);
        if (rawOutlines != null) {
            readOutlineItems(
                    rawOutlines,
                    pageNumbers,
                    namedNames,
                    StructureFailure.COMMAND,
                    -1L);
        }

        requireValidOutlineItems(
                command.getItems(),
                pageReferences.size(),
                namedNames,
                1);

        if (command.getItems().isEmpty()) {
            catalog.removeItem(COSName.OUTLINES);
            return;
        }
        COSDictionary root = new COSDictionary();
        root.setItem(COSName.TYPE, COSName.getPDFName("Outlines"));
        int visibleTotal = writeOutlineLevel(
                command.getItems(),
                root,
                pageReferences,
                1,
                ownership);
        root.setItem(COSName.COUNT, COSInteger.get(visibleTotal));
        catalog.setItem(COSName.OUTLINES, root);
    }

    private void requireValidOutlineItems(
            List<OutlineItem> items,
            int pageCount,
            java.util.TreeSet<COSString> namedNames,
            int depth) throws DocumentFailure {
        resources.checkpoint();
        resources.requireNestingDepth(depth);
        if (depth > MAX_OUTLINE_DEPTH) {
            throw invalidOutlineCommand();
        }
        for (OutlineItem item : items) {
            resources.checkpoint();
            if (item.getDestination().isPresent()
                    && item.getDestination().get().getPageNumber()
                            > pageCount) {
                throw failure(
                        DocumentFailureCode.PAGE_RANGE_INVALID,
                        "The destination page is outside the current document.");
            }
            if (item.getNamedDestination().isPresent()
                    ) {
                try (WorkflowResourceContext.OwnedMemoryScope probeOwnership =
                        resources.ownedMemoryScope()) {
                    COSString probe = PdfBoxStringSupport.backendString(
                            item.getNamedDestination().get(),
                            resources,
                            probeOwnership,
                            PdfBoxMetadataOperations::invalidOutlineCommand);
                    if (!namedNames.contains(probe)) {
                        throw invalidOutlineCommand();
                    }
                }
            }
            requireValidOutlineItems(
                    item.getChildren(),
                    pageCount,
                    namedNames,
                    depth + 1);
        }
    }

    private int writeOutlineLevel(
            List<OutlineItem> items,
            COSDictionary parent,
            List<COSBase> pageReferences,
            int depth,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        resources.checkpoint();
        resources.requireNestingDepth(depth);
        if (depth > MAX_OUTLINE_DEPTH) {
            throw invalidOutlineCommand();
        }
        COSDictionary first = null;
        COSDictionary previous = null;
        int visibleTotal = 0;
        for (OutlineItem item : items) {
            resources.checkpoint();
            COSDictionary node = new COSDictionary();
            node.setItem(COSName.TITLE,
                    PdfBoxStringSupport.backendString(
                            item.getTitle(),
                            resources,
                            ownership,
                            PdfBoxMetadataOperations::invalidOutlineCommand));
            node.setItem(COSName.PARENT, parent);
            if (previous != null) {
                previous.setItem(COSName.NEXT, node);
                node.setItem(COSName.PREV, previous);
            }
            if (first == null) {
                first = node;
            }
            if (item.getDestination().isPresent()) {
                PageDestination destination = item.getDestination().get();
                node.setItem(
                        COSName.DEST,
                        destinationToArray(
                                destination,
                                pageReferences.get(
                                        destination.getPageNumber() - 1),
                                ownership));
            } else if (item.getNamedDestination().isPresent()) {
                node.setItem(
                        COSName.DEST,
                        PdfBoxStringSupport.backendString(
                                item.getNamedDestination().get(),
                                resources,
                                ownership,
                                PdfBoxMetadataOperations::invalidOutlineCommand));
            }
            int descendants = 0;
            if (!item.getChildren().isEmpty()) {
                descendants = writeOutlineLevel(
                        item.getChildren(),
                        node,
                        pageReferences,
                        depth + 1,
                        ownership);
                node.setItem(COSName.COUNT, COSInteger.get(descendants));
            }
            visibleTotal += 1 + descendants;
            previous = node;
        }
        parent.setItem(COSName.FIRST, first);
        parent.setItem(COSName.LAST, previous);
        return visibleTotal;
    }

    private List<OutlineItem> outlineTree(OutlineTree query)
            throws DocumentFailure {
        COSBase rawOutlines = document.getDocumentCatalog().getCOSObject()
                .getItem(COSName.OUTLINES);
        if (rawOutlines == null) {
            return Collections.emptyList();
        }
        try {
            IdentityHashMap<COSDictionary, Integer> pageNumbers =
                    pageNumbersByDictionary(document, StructureFailure.QUERY);
            java.util.TreeSet<COSString> namedNames =
                    new java.util.TreeSet<COSString>(nameOrder);
            for (NameTreeEntry entry : destinationEntriesOf(
                    document,
                    StructureFailure.QUERY)) {
                resources.checkpoint();
                namedNames.add(entry.key);
            }
            return Collections.unmodifiableList(readOutlineItems(
                    rawOutlines,
                    pageNumbers,
                    namedNames,
                    StructureFailure.QUERY,
                    query.getMaximumItems()));
        } catch (DocumentFailure failure) {
            if (failure.getCode() == DocumentFailureCode.QUERY_FAILED) {
                throw unsafeOutlineQuery();
            }
            throw failure;
        }
    }

    private List<NameTreeEntry> destinationEntriesOf(
            PDDocument source,
            StructureFailure failureMode) throws DocumentFailure {
        COSBase rawNames = source.getDocumentCatalog().getCOSObject()
                .getItem(COSName.NAMES);
        if (rawNames == null) {
            return new java.util.ArrayList<NameTreeEntry>();
        }
        COSBase names = dereference(rawNames);
        if (!(names instanceof COSDictionary)) {
            throw failureMode.destinationFailure();
        }
        COSBase rawTree = ((COSDictionary) names).getItem(COSName.DESTS);
        if (rawTree == null) {
            return new java.util.ArrayList<NameTreeEntry>();
        }
        return readNameTreeEntries(rawTree, failureMode, -1L);
    }

    private List<OutlineNode> readOutlineNodes(
            COSBase rawRoot,
            IdentityHashMap<COSDictionary, Integer> pageNumbers,
            java.util.TreeSet<COSString> namedNames,
            StructureFailure failureMode,
            long maximumItems) throws DocumentFailure {
        COSBase root = dereference(rawRoot);
        if (!(root instanceof COSDictionary)) {
            throw failureMode.outlineFailure();
        }
        COSDictionary rootDictionary = (COSDictionary) root;
        for (COSName key : rootDictionary.keySet()) {
            resources.checkpoint();
            if (!COSName.TYPE.equals(key)
                    && !COSName.FIRST.equals(key)
                    && !COSName.LAST.equals(key)
                    && !COSName.COUNT.equals(key)) {
                throw failureMode.outlineFailure();
            }
        }
        COSBase type = dereference(rootDictionary.getItem(COSName.TYPE));
        if (type != null && !COSName.getPDFName("Outlines").equals(type)) {
            throw failureMode.outlineFailure();
        }
        COSBase rawFirst = rootDictionary.getItem(COSName.FIRST);
        COSBase rawLast = rootDictionary.getItem(COSName.LAST);
        COSBase rawCount = rootDictionary.getItem(COSName.COUNT);
        if (rawFirst == null) {
            if (rawLast != null || outlineInteger(rawCount, failureMode) != 0L) {
                throw failureMode.outlineFailure();
            }
            return new java.util.ArrayList<OutlineNode>();
        }
        if (rawLast == null) {
            throw failureMode.outlineFailure();
        }
        long count = outlineInteger(rawCount, failureMode);
        if (count <= 0L) {
            throw failureMode.outlineFailure();
        }
        OutlineLevel level = readOutlineLevel(
                rawFirst,
                rootDictionary,
                pageNumbers,
                namedNames,
                failureMode,
                maximumItems,
                new long[1],
                1);
        if (dereference(rawLast) != level.lastNode
                || count != level.visibleTotal) {
            throw failureMode.outlineFailure();
        }
        return level.nodes;
    }

    private List<OutlineItem> readOutlineItems(
            COSBase rawRoot,
            IdentityHashMap<COSDictionary, Integer> pageNumbers,
            java.util.TreeSet<COSString> namedNames,
            StructureFailure failureMode,
            long maximumItems) throws DocumentFailure {
        List<OutlineItem> items = new java.util.ArrayList<OutlineItem>();
        for (OutlineNode node : readOutlineNodes(
                rawRoot,
                pageNumbers,
                namedNames,
                failureMode,
                maximumItems)) {
            resources.checkpoint();
            items.add(node.toItem(pageNumbers));
        }
        return items;
    }

    private OutlineLevel readOutlineLevel(
            COSBase rawFirst,
            COSDictionary parent,
            IdentityHashMap<COSDictionary, Integer> pageNumbers,
            java.util.TreeSet<COSString> namedNames,
            StructureFailure failureMode,
            long maximumItems,
            long[] itemCounter,
            int depth) throws DocumentFailure {
        resources.checkpoint();
        resources.requireNestingDepth(depth);
        if (depth > MAX_OUTLINE_DEPTH) {
            throw failureMode.outlineFailure();
        }
        COSBase firstValue = dereference(rawFirst);
        if (!(firstValue instanceof COSDictionary)) {
            throw failureMode.outlineFailure();
        }
        List<OutlineNode> nodes = new java.util.ArrayList<OutlineNode>();
        IdentityHashMap<COSDictionary, Boolean> visited =
                new IdentityHashMap<COSDictionary, Boolean>();
        COSDictionary node = (COSDictionary) firstValue;
        COSDictionary previous = null;
        long visibleTotal = 0L;
        while (node != null) {
            resources.checkpoint();
            if (visited.put(node, Boolean.TRUE) != null) {
                throw failureMode.outlineFailure();
            }
            itemCounter[0]++;
            if (maximumItems >= 0L && itemCounter[0] > maximumItems) {
                throw failure(
                        DocumentFailureCode.METADATA_LIMIT_EXCEEDED,
                        "The metadata access limit was exceeded.");
            }
            for (COSName key : node.keySet()) {
                resources.checkpoint();
                if (!COSName.TITLE.equals(key)
                        && !COSName.PARENT.equals(key)
                        && !COSName.PREV.equals(key)
                        && !COSName.NEXT.equals(key)
                        && !COSName.FIRST.equals(key)
                        && !COSName.LAST.equals(key)
                        && !COSName.COUNT.equals(key)
                        && !COSName.DEST.equals(key)) {
                    throw failureMode.outlineFailure();
                }
            }
            COSBase titleValue = dereference(node.getItem(COSName.TITLE));
            if (!(titleValue instanceof COSString)
                    || dereference(node.getItem(COSName.PARENT)) != parent) {
                throw failureMode.outlineFailure();
            }
            COSBase rawPrevious = node.getItem(COSName.PREV);
            if (previous == null
                    ? rawPrevious != null
                    : rawPrevious == null
                            || dereference(rawPrevious) != previous) {
                throw failureMode.outlineFailure();
            }
            COSBase destinationArray = null;
            COSString namedName = null;
            COSBase rawDestination = node.getItem(COSName.DEST);
            if (rawDestination != null) {
                COSBase destinationValue = dereference(rawDestination);
                if (destinationValue instanceof COSArray) {
                    if (destinationFromArray(
                            destinationValue,
                            pageNumbers) == null) {
                        throw failureMode.outlineFailure();
                    }
                    destinationArray = destinationValue;
                } else if (destinationValue instanceof COSString) {
                    if (!namedNames.contains(destinationValue)) {
                        throw failureMode.outlineFailure();
                    }
                    namedName = (COSString) destinationValue;
                } else {
                    throw failureMode.outlineFailure();
                }
            }
            List<OutlineNode> children =
                    new java.util.ArrayList<OutlineNode>();
            COSBase rawChildFirst = node.getItem(COSName.FIRST);
            if (rawChildFirst == null) {
                if (node.getItem(COSName.LAST) != null
                        || outlineInteger(
                                node.getItem(COSName.COUNT),
                                failureMode) != 0L) {
                    throw failureMode.outlineFailure();
                }
            } else {
                COSBase rawChildLast = node.getItem(COSName.LAST);
                long childCount = outlineInteger(
                        node.getItem(COSName.COUNT),
                        failureMode);
                if (rawChildLast == null || childCount == 0L) {
                    throw failureMode.outlineFailure();
                }
                OutlineLevel childLevel = readOutlineLevel(
                        rawChildFirst,
                        node,
                        pageNumbers,
                        namedNames,
                        failureMode,
                        maximumItems,
                        itemCounter,
                        depth + 1);
                if (dereference(rawChildLast) != childLevel.lastNode
                        || Math.abs(childCount) != childLevel.visibleTotal) {
                    throw failureMode.outlineFailure();
                }
                children = childLevel.nodes;
                if (childCount > 0L) {
                    visibleTotal += childLevel.visibleTotal;
                }
            }
            nodes.add(new OutlineNode(
                    ((COSString) titleValue).getString(),
                    destinationArray,
                    namedName,
                    children));
            visibleTotal++;
            previous = node;
            COSBase rawNext = node.getItem(COSName.NEXT);
            if (rawNext == null) {
                node = null;
            } else {
                COSBase nextValue = dereference(rawNext);
                if (!(nextValue instanceof COSDictionary)) {
                    throw failureMode.outlineFailure();
                }
                node = (COSDictionary) nextValue;
            }
        }
        return new OutlineLevel(nodes, previous, visibleTotal);
    }

    private long outlineInteger(
            COSBase rawValue,
            StructureFailure failureMode) throws DocumentFailure {
        if (rawValue == null) {
            return 0L;
        }
        COSBase value = dereference(rawValue);
        if (!(value instanceof COSInteger)) {
            throw failureMode.outlineFailure();
        }
        return ((COSInteger) value).longValue();
    }

    final class OutlineNode {

        private final String title;
        private final COSBase destinationArray;
        private final COSString namedName;
        private final List<OutlineNode> children;

        private OutlineNode(
                String title,
                COSBase destinationArray,
                COSString namedName,
                List<OutlineNode> children) {
            this.title = title;
            this.destinationArray = destinationArray;
            this.namedName = namedName;
            this.children = children;
        }

        int destinationPageIndex(
                IdentityHashMap<COSDictionary, Integer> pageNumbers)
                throws DocumentFailure {
            COSBase pageValue = dereference(
                    ((COSArray) destinationArray).get(0));
            for (Map.Entry<COSDictionary, Integer> entry
                    : pageNumbers.entrySet()) {
                resources.checkpoint();
                if (dereference(entry.getKey()) == pageValue) {
                    return entry.getValue().intValue() - 1;
                }
            }
            throw preservationUnsupported();
        }

        OutlineItem toItem(
                IdentityHashMap<COSDictionary, Integer> pageNumbers)
                throws DocumentFailure {
            List<OutlineItem> converted =
                    new java.util.ArrayList<OutlineItem>();
            for (OutlineNode child : children) {
                resources.checkpoint();
                converted.add(child.toItem(pageNumbers));
            }
            if (destinationArray != null) {
                return OutlineItem.toPage(
                        title,
                        destinationFromArray(destinationArray, pageNumbers),
                        converted);
            }
            if (namedName != null) {
                return OutlineItem.toNamedDestination(
                        title,
                        namedName.getString(),
                        converted);
            }
            return OutlineItem.grouping(title, converted);
        }
    }

    private static final class OutlineLevel {

        private final List<OutlineNode> nodes;
        private final COSDictionary lastNode;
        private final long visibleTotal;

        private OutlineLevel(
                List<OutlineNode> nodes,
                COSDictionary lastNode,
                long visibleTotal) {
            this.nodes = nodes;
            this.lastNode = lastNode;
            this.visibleTotal = visibleTotal;
        }
    }

    private void embedFile(EmbedFile command) throws DocumentFailure {
        try (WorkflowResourceContext.OwnedMemoryScope ownership =
                resources.ownedMemoryScope()) {
            embedFileGuarded(command, ownership);
            ownership.transfer();
        } catch (DocumentFailure failure) {
            if (failure.getCode() == DocumentFailureCode.COMMAND_REJECTED) {
                throw invalidEmbeddedFilesCommand();
            }
            throw failure;
        }
    }

    private void embedFileGuarded(
            EmbedFile command,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        EmbeddedFile file = command.getFile();
        byte[] content = file.contentForWorkflow();

        COSDictionary catalog = document.getDocumentCatalog().getCOSObject();
        COSBase rawNames = catalog.getItem(COSName.NAMES);
        COSDictionary names;
        java.util.TreeMap<COSString, COSDictionary> current;
        if (rawNames == null) {
            names = null;
            current = new java.util.TreeMap<COSString, COSDictionary>(
                    nameOrder);
        } else {
            COSBase namesValue = dereference(rawNames);
            if (!(namesValue instanceof COSDictionary)) {
                throw StructureFailure.COMMAND.embeddedFilesFailure();
            }
            names = (COSDictionary) namesValue;
            current = embeddedFileEntriesOf(
                    document,
                    StructureFailure.COMMAND);
        }

        COSStream stream;
        try {
            stream = document.getDocument().createCOSStream();
        } catch (RuntimeException backendFailure) {
            resources.rethrowResourceOrTerminalFailure(backendFailure);
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The metadata operation could not be completed safely.");
        }
        stream.setItem(COSName.TYPE, COSName.EMBEDDED_FILE);
        if (file.getMimeSubtype().isPresent()) {
            stream.setItem(
                    COSName.SUBTYPE,
                    mimeSubtypeName(
                            file.getMimeSubtype().get(), ownership));
        }
        COSDictionary params = new COSDictionary();
        params.setItem(COSName.SIZE, COSInteger.get(content.length));
        try (WorkflowResourceContext.MemoryReservation digestMemory =
                resources.reserveOwnedMemory(16L)) {
            params.setItem(
                    COSName.getPDFName("CheckSum"),
                    PdfBoxStringSupport.backendBytes(
                            md5(content),
                            resources,
                            ownership,
                            PdfBoxMetadataOperations::invalidEmbeddedFilesCommand));
        }
        stream.setItem(COSName.PARAMS, params);
        try (OutputStream embedded = stream.createOutputStream()) {
            resources.writeBytesAsIOException(embedded, content);
        } catch (IOException | RuntimeException writeFailure) {
            resources.rethrowResourceOrTerminalFailure(writeFailure);
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The metadata operation could not be completed safely.");
        }

        COSDictionary specification = new COSDictionary();
        specification.setItem(COSName.TYPE, COSName.getPDFName("Filespec"));
        specification.setItem(COSName.F,
                PdfBoxStringSupport.backendString(
                        file.getName(),
                        resources,
                        ownership,
                        PdfBoxMetadataOperations::invalidEmbeddedFilesCommand));
        if (file.getDescription().isPresent()) {
            specification.setItem(
                    COSName.DESC,
                    PdfBoxStringSupport.backendString(
                            file.getDescription().get(),
                            resources,
                            ownership,
                            PdfBoxMetadataOperations::invalidEmbeddedFilesCommand));
        }
        COSDictionary efDictionary = new COSDictionary();
        efDictionary.setItem(COSName.F, stream);
        specification.setItem(COSName.EF, efDictionary);
        if (file.getRelationship()
                != EmbeddedFile.Relationship.UNSPECIFIED) {
            specification.setItem(
                    COSName.getPDFName("AFRelationship"),
                    afRelationshipName(file.getRelationship()));
        }

        current.put(PdfBoxStringSupport.backendString(
                file.getName(),
                resources,
                ownership,
                PdfBoxMetadataOperations::invalidEmbeddedFilesCommand),
                specification);
        if (names == null) {
            names = new COSDictionary();
            catalog.setItem(COSName.NAMES, names);
        }
        COSArray keysAndValues = new COSArray();
        keysAndValues.setDirect(true);
        for (Map.Entry<COSString, COSDictionary> entry
                : current.entrySet()) {
            resources.checkpoint();
            keysAndValues.add(entry.getKey());
            keysAndValues.add(entry.getValue());
        }
        COSDictionary tree = new COSDictionary();
        tree.setItem(COSName.NAMES, keysAndValues);
        names.setItem(COSName.EMBEDDED_FILES, tree);
    }

    private List<EmbeddedFileSummary> embeddedFiles(EmbeddedFiles query)
            throws DocumentFailure {
        try (WorkflowResourceContext.OwnedMemoryScope ownership =
                resources.ownedMemoryScope()) {
            java.util.TreeMap<COSString, COSDictionary> entries =
                    embeddedFileEntriesOf(
                            document,
                            StructureFailure.QUERY);
            List<EmbeddedFileSummary> summaries =
                    new java.util.ArrayList<EmbeddedFileSummary>();
            for (Map.Entry<COSString, COSDictionary> entry
                    : entries.entrySet()) {
                resources.checkpoint();
                if (summaries.size() >= query.getMaximumEntries()) {
                    throw failure(
                            DocumentFailureCode.METADATA_LIMIT_EXCEEDED,
                            "The metadata access limit was exceeded.");
                }
                EmbeddedFileFields fields = embeddedFileFields(
                        entry.getValue(),
                        StructureFailure.QUERY,
                        ownership);
                summaries.add(new EmbeddedFileSummary(
                        entry.getKey().getString(),
                        fields.mimeSubtype,
                        fields.description,
                        fields.relationship,
                        fields.size,
                        fields.md5Hex));
            }
            List<EmbeddedFileSummary> result =
                    Collections.unmodifiableList(summaries);
            ownership.transfer();
            return result;
        } catch (DocumentFailure failure) {
            if (failure.getCode() == DocumentFailureCode.QUERY_FAILED) {
                throw unsafeEmbeddedFilesQuery();
            }
            throw failure;
        }
    }

    private Optional<EmbeddedFileData> readEmbeddedFile(
            ReadEmbeddedFile query) throws DocumentFailure {
        try (WorkflowResourceContext.OwnedMemoryScope ownership =
                resources.ownedMemoryScope()) {
            java.util.TreeMap<COSString, COSDictionary> entries =
                    embeddedFileEntriesOf(
                            document,
                            StructureFailure.QUERY);
            COSDictionary specification;
            try (WorkflowResourceContext.OwnedMemoryScope probeOwnership =
                    resources.ownedMemoryScope()) {
                specification = entries.get(
                        PdfBoxStringSupport.backendString(
                                query.getName(),
                                resources,
                                probeOwnership,
                                PdfBoxMetadataOperations::unsafeEmbeddedFilesQuery));
            }
            if (specification == null) {
                return Optional.empty();
            }
            EmbeddedFileFields fields = embeddedFileFields(
                    specification,
                    StructureFailure.QUERY,
                    ownership);
            byte[] content = ownership.hold(boundedDecodedContentWorking(
                    fields.stream, query.getMaximumBytes()));
            Optional<EmbeddedFileData> result = Optional.of(
                    new EmbeddedFileData(
                    query.getName(),
                    fields.mimeSubtype,
                    fields.description,
                    fields.relationship,
                    fields.size,
                    fields.md5Hex,
                    sha256Hex(content),
                    content));
            ownership.transfer();
            return result;
        } catch (DocumentFailure failure) {
            if (failure.getCode() == DocumentFailureCode.QUERY_FAILED) {
                throw unsafeEmbeddedFilesQuery();
            }
            throw failure;
        }
    }

    private java.util.TreeMap<COSString, COSDictionary>
            embeddedFileEntriesOf(
                    PDDocument source,
                    StructureFailure failureMode) throws DocumentFailure {
        COSBase rawNames = source.getDocumentCatalog().getCOSObject()
                .getItem(COSName.NAMES);
        java.util.TreeMap<COSString, COSDictionary> entries =
                new java.util.TreeMap<COSString, COSDictionary>(nameOrder);
        if (rawNames == null) {
            return entries;
        }
        COSBase names = dereference(rawNames);
        if (!(names instanceof COSDictionary)) {
            throw failureMode.embeddedFilesFailure();
        }
        COSBase rawTree = ((COSDictionary) names).getItem(
                COSName.EMBEDDED_FILES);
        if (rawTree == null) {
            return entries;
        }
        for (NameTreeEntry entry : readNameTreeEntries(
                rawTree,
                failureMode,
                -1L)) {
            resources.checkpoint();
            entries.put(
                    entry.key,
                    validateFileSpecification(entry.value, failureMode));
        }
        return entries;
    }

    private COSDictionary validateFileSpecification(
            COSBase rawSpecification,
            StructureFailure failureMode) throws DocumentFailure {
        COSBase specificationValue = dereference(rawSpecification);
        if (!(specificationValue instanceof COSDictionary)) {
            throw failureMode.embeddedFilesFailure();
        }
        COSDictionary specification = (COSDictionary) specificationValue;
        for (COSName key : specification.keySet()) {
            resources.checkpoint();
            if (!COSName.TYPE.equals(key)
                    && !COSName.F.equals(key)
                    && !COSName.UF.equals(key)
                    && !COSName.EF.equals(key)
                    && !COSName.DESC.equals(key)
                    && !COSName.getPDFName("AFRelationship").equals(key)) {
                throw failureMode.embeddedFilesFailure();
            }
        }
        COSBase type = dereference(specification.getItem(COSName.TYPE));
        if (type != null && !COSName.getPDFName("Filespec").equals(type)) {
            throw failureMode.embeddedFilesFailure();
        }
        if (dereference(specification.getItem(COSName.F))
                instanceof COSString) {
            // The filename is declared but its content is not validated here.
        } else {
            throw failureMode.embeddedFilesFailure();
        }
        COSBase uf = dereference(specification.getItem(COSName.UF));
        if (uf != null && !(uf instanceof COSString)) {
            throw failureMode.embeddedFilesFailure();
        }
        COSBase description = dereference(
                specification.getItem(COSName.DESC));
        if (description != null && !(description instanceof COSString)) {
            throw failureMode.embeddedFilesFailure();
        }
        COSBase relationship = dereference(
                specification.getItem(COSName.getPDFName("AFRelationship")));
        if (relationship != null
                && afRelationshipFromName(relationship) == null) {
            throw failureMode.embeddedFilesFailure();
        }
        COSBase rawEf = specification.getItem(COSName.EF);
        if (rawEf == null) {
            throw failureMode.embeddedFilesFailure();
        }
        COSBase efValue = dereference(rawEf);
        if (!(efValue instanceof COSDictionary)) {
            throw failureMode.embeddedFilesFailure();
        }
        COSDictionary ef = (COSDictionary) efValue;
        for (COSName key : ef.keySet()) {
            resources.checkpoint();
            if (!COSName.F.equals(key) && !COSName.UF.equals(key)) {
                throw failureMode.embeddedFilesFailure();
            }
        }
        embeddedFileStreamOf(ef.getItem(COSName.F), failureMode);
        COSBase ufStream = ef.getItem(COSName.UF);
        if (ufStream != null) {
            embeddedFileStreamOf(ufStream, failureMode);
        }
        return specification;
    }

    private COSStream embeddedFileStreamOf(
            COSBase rawStream,
            StructureFailure failureMode) throws DocumentFailure {
        COSBase streamValue = dereference(rawStream);
        if (!(streamValue instanceof COSStream)) {
            throw failureMode.embeddedFilesFailure();
        }
        COSStream stream = (COSStream) streamValue;
        for (COSName key : stream.keySet()) {
            resources.checkpoint();
            if (!COSName.TYPE.equals(key)
                    && !COSName.SUBTYPE.equals(key)
                    && !COSName.LENGTH.equals(key)
                    && !COSName.FILTER.equals(key)
                    && !COSName.getPDFName("DecodeParms").equals(key)
                    && !COSName.F_FILTER.equals(key)
                    && !COSName.getPDFName("FDecodeParms").equals(key)
                    && !COSName.DL.equals(key)
                    && !COSName.PARAMS.equals(key)) {
                throw failureMode.embeddedFilesFailure();
            }
        }
        COSBase type = dereference(stream.getItem(COSName.TYPE));
        if (type != null && !COSName.EMBEDDED_FILE.equals(type)) {
            throw failureMode.embeddedFilesFailure();
        }
        COSBase rawParams = stream.getItem(COSName.PARAMS);
        if (rawParams != null) {
            COSBase paramsValue = dereference(rawParams);
            if (!(paramsValue instanceof COSDictionary)) {
                throw failureMode.embeddedFilesFailure();
            }
            COSDictionary params = (COSDictionary) paramsValue;
            for (COSName key : params.keySet()) {
                resources.checkpoint();
                if (!COSName.SIZE.equals(key)
                        && !COSName.getPDFName("CreationDate").equals(key)
                        && !COSName.getPDFName("ModDate").equals(key)
                        && !COSName.getPDFName("CheckSum").equals(key)) {
                    throw failureMode.embeddedFilesFailure();
                }
            }
            COSBase size = dereference(params.getItem(COSName.SIZE));
            if (size != null && !(size instanceof COSInteger)) {
                throw failureMode.embeddedFilesFailure();
            }
            COSBase checksum = dereference(
                    params.getItem(COSName.getPDFName("CheckSum")));
            if (checksum != null && !(checksum instanceof COSString)) {
                throw failureMode.embeddedFilesFailure();
            }
            COSBase creationDate = dereference(
                    params.getItem(COSName.getPDFName("CreationDate")));
            COSBase modDate = dereference(
                    params.getItem(COSName.getPDFName("ModDate")));
            if ((creationDate != null && !(creationDate instanceof COSString))
                    || (modDate != null && !(modDate instanceof COSString))) {
                throw failureMode.embeddedFilesFailure();
            }
        }
        return stream;
    }

    private EmbeddedFileFields embeddedFileFields(
            COSDictionary specification,
            StructureFailure failureMode,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        COSBase ef = dereference(specification.getItem(COSName.EF));
        COSStream stream = embeddedFileStreamOf(
                ((COSDictionary) ef).getItem(COSName.F),
                failureMode);
        COSBase mime = dereference(stream.getItem(COSName.SUBTYPE));
        String mimeSubtype = mime instanceof COSName
                ? mimeSubtypeFromName((COSName) mime, ownership)
                : null;
        COSBase descriptionValue = dereference(
                specification.getItem(COSName.DESC));
        String description = descriptionValue instanceof COSString
                ? ((COSString) descriptionValue).getString()
                : null;
        EmbeddedFile.Relationship relationship = afRelationshipFromName(
                dereference(specification.getItem(
                        COSName.getPDFName("AFRelationship"))));
        long size;
        String md5Hex = null;
        COSBase rawParams = stream.getItem(COSName.PARAMS);
        if (rawParams != null) {
            COSDictionary params = (COSDictionary) dereference(rawParams);
            COSBase sizeValue = dereference(params.getItem(COSName.SIZE));
            if (sizeValue instanceof COSInteger) {
                size = ((COSInteger) sizeValue).longValue();
            } else {
                throw failureMode.embeddedFilesFailure();
            }
            COSBase checksum = dereference(
                    params.getItem(COSName.getPDFName("CheckSum")));
            if (checksum instanceof COSString) {
                md5Hex = ((COSString) checksum).toHexString()
                        .toLowerCase(java.util.Locale.ROOT);
            }
        } else {
            COSBase lengthValue = dereference(stream.getItem(COSName.LENGTH));
            if (!(lengthValue instanceof COSInteger)) {
                throw failureMode.embeddedFilesFailure();
            }
            size = ((COSInteger) lengthValue).longValue();
        }
        if (size < 0L) {
            throw failureMode.embeddedFilesFailure();
        }
        return new EmbeddedFileFields(
                stream,
                mimeSubtype,
                description,
                relationship,
                size,
                md5Hex);
    }

    private WorkflowResourceContext.OwnedBytes boundedDecodedContentWorking(
            COSStream stream,
            long maximumBytes) throws DocumentFailure {
        try (WorkflowResourceContext.OwnedByteAccumulator content =
                resources.ownedByteAccumulator()) {
            PdfBoxHostileInputPreflight.decodeStream(
                    stream,
                    resources,
                    new BoundedMetadataOutput(content, maximumBytes));
            return content.finishWorking();
        } catch (MetadataLimitIOException exhausted) {
            throw failure(
                    DocumentFailureCode.METADATA_LIMIT_EXCEEDED,
                    "The metadata access limit was exceeded.");
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException decodeFailure) {
            resources.rethrowResourceOrTerminalFailure(decodeFailure);
            throw unsafeEmbeddedFilesQuery();
        }
    }

    private static void closeQuietly(
            WorkflowResourceContext.OwnedBytes bytes) {
        if (bytes != null) {
            bytes.close();
        }
    }

    private static void closeQuietly(
            WorkflowResourceContext.OwnedMemoryScope ownership) {
        if (ownership != null) {
            ownership.close();
        }
    }

    private COSName mimeSubtypeName(
            String mimeSubtype,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        try (WorkflowResourceContext.OwnedTextAccumulator name =
                resources.ownedTextAccumulator()) {
            for (int index = 0; index < mimeSubtype.length(); index++) {
                char character = mimeSubtype.charAt(index);
                if (character > 0x7E) {
                    // MIME subtypes are printable ASCII; anything wider cannot
                    // survive the PDF name escaping without truncation.
                    throw failure(
                            DocumentFailureCode.COMMAND_REJECTED,
                            "The embedded files could not be updated safely.");
                }
                if (character <= 0x20
                        || "()<>[]{}/%#".indexOf(character) >= 0) {
                    name.append('#');
                    name.append(HEX_DIGITS[(character >> 4) & 0xF]);
                    name.append(HEX_DIGITS[character & 0xF]);
                } else {
                    name.append(character);
                }
            }
            try {
                return COSName.getPDFName(name.finishHeld(ownership));
            } catch (RuntimeException invalidName) {
                resources.rethrowResourceOrTerminalFailure(invalidName);
                throw failure(
                        DocumentFailureCode.COMMAND_REJECTED,
                        "The embedded files could not be updated safely.");
            }
        }
    }

    private String mimeSubtypeFromName(
            COSName name,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        String encoded = name.getName();
        try (WorkflowResourceContext.OwnedTextAccumulator decoded =
                resources.ownedTextAccumulator()) {
            for (int index = 0; index < encoded.length(); index++) {
                char character = encoded.charAt(index);
                if (character == '#' && index + 2 < encoded.length()) {
                    int high = Character.digit(encoded.charAt(index + 1), 16);
                    int low = Character.digit(encoded.charAt(index + 2), 16);
                    if (high >= 0 && low >= 0) {
                        decoded.append((char) (high * 16 + low));
                        index += 2;
                        continue;
                    }
                }
                decoded.append(character);
            }
            return decoded.finishHeld(ownership);
        }
    }

    private static COSName afRelationshipName(
            EmbeddedFile.Relationship relationship) {
        switch (relationship) {
            case SOURCE:
                return COSName.getPDFName("Source");
            case DATA:
                return COSName.getPDFName("Data");
            case ALTERNATIVE:
                return COSName.getPDFName("Alternative");
            default:
                return COSName.getPDFName("Supplement");
        }
    }

    private static EmbeddedFile.Relationship afRelationshipFromName(
            COSBase value) {
        if (!(value instanceof COSName)) {
            return null;
        }
        String name = ((COSName) value).getName();
        if ("Source".equals(name)) {
            return EmbeddedFile.Relationship.SOURCE;
        }
        if ("Data".equals(name)) {
            return EmbeddedFile.Relationship.DATA;
        }
        if ("Alternative".equals(name)) {
            return EmbeddedFile.Relationship.ALTERNATIVE;
        }
        if ("Supplement".equals(name)) {
            return EmbeddedFile.Relationship.SUPPLEMENT;
        }
        if ("Unspecified".equals(name)) {
            return EmbeddedFile.Relationship.UNSPECIFIED;
        }
        return null;
    }

    private byte[] md5(byte[] content) throws DocumentFailure {
        try {
            java.security.MessageDigest digest =
                    java.security.MessageDigest.getInstance("MD5");
            updateDigest(digest, content);
            return digest.digest();
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The metadata operation could not be completed safely.");
        }
    }

    private String sha256Hex(byte[] content) throws DocumentFailure {
        try {
            java.security.MessageDigest digest =
                    java.security.MessageDigest.getInstance("SHA-256");
            updateDigest(digest, content);
            return hex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw failure(
                    DocumentFailureCode.QUERY_FAILED,
                    "The metadata query could not be evaluated safely.");
        }
    }

    private void updateDigest(
            java.security.MessageDigest digest,
            byte[] content) throws DocumentFailure {
        for (int offset = 0; offset < content.length; offset += 8192) {
            resources.checkpoint();
            int length = Math.min(8192, content.length - offset);
            digest.update(content, offset, length);
        }
        resources.checkpoint();
    }

    private static String hex(byte[] bytes) {
        StringBuilder encoded = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            encoded.append(HEX_DIGITS[(value >> 4) & 0xF]);
            encoded.append(HEX_DIGITS[value & 0xF]);
        }
        return encoded.toString();
    }

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private static final class EmbeddedFileFields {

        private final COSStream stream;
        private final String mimeSubtype;
        private final String description;
        private final EmbeddedFile.Relationship relationship;
        private final long size;
        private final String md5Hex;

        private EmbeddedFileFields(
                COSStream stream,
                String mimeSubtype,
                String description,
                EmbeddedFile.Relationship relationship,
                long size,
                String md5Hex) {
            this.stream = stream;
            this.mimeSubtype = mimeSubtype;
            this.description = description;
            this.relationship = relationship;
            this.size = size;
            this.md5Hex = md5Hex;
        }
    }

    private IdentityHashMap<COSDictionary, Integer> pageNumbersByDictionary(
            PDDocument source) throws DocumentFailure {
        return pageNumbersByDictionary(source, StructureFailure.PRESERVE);
    }

    private IdentityHashMap<COSDictionary, Integer> pageNumbersByDictionary(
            PDDocument source,
            StructureFailure failureMode) throws DocumentFailure {
        List<COSBase> pageReferences = rawPageReferences(
                source,
                failureMode);
        IdentityHashMap<COSDictionary, Integer> pageNumbers =
                new IdentityHashMap<COSDictionary, Integer>();
        for (int index = 0; index < pageReferences.size(); index++) {
            resources.checkpoint();
            COSBase page = dereference(pageReferences.get(index));
            if (page instanceof COSDictionary) {
                pageNumbers.put(
                        (COSDictionary) page,
                        Integer.valueOf(index + 1));
            }
        }
        return pageNumbers;
    }

    /**
     * Resolves the strictly validated raw page references of one document in
     * page order.
     *
     * @param source the document to walk
     * @param failureMode the failure translation for structural problems
     * @return the ordered raw page kid references
     * @throws DocumentFailure when the page tree is structurally inconsistent
     */
    List<COSBase> rawPageReferences(
            PDDocument source,
            StructureFailure failureMode) throws DocumentFailure {
        COSBase catalogValue = dereference(
                source.getDocument().getTrailer().getItem(COSName.ROOT));
        if (!(catalogValue instanceof COSDictionary)) {
            throw failureMode.destinationFailure();
        }
        List<COSBase> pages = new java.util.ArrayList<COSBase>();
        collectRawPageReferences(
                ((COSDictionary) catalogValue).getItem(COSName.PAGES),
                null,
                pages,
                new IdentityHashMap<COSDictionary, Boolean>(),
                failureMode);
        return pages;
    }

    private long collectRawPageReferences(
            COSBase rawNode,
            COSDictionary expectedParent,
            List<COSBase> pages,
            IdentityHashMap<COSDictionary, Boolean> visited,
            StructureFailure failureMode) throws DocumentFailure {
        Deque<RawPageTreeFrame> pending =
                new ArrayDeque<RawPageTreeFrame>();
        pending.push(rawPageTreeFrame(
                rawNode,
                expectedParent,
                visited,
                failureMode,
                1));
        long rootPageCount = 0L;
        while (!pending.isEmpty()) {
            resources.checkpoint();
            RawPageTreeFrame current = pending.peek();
            if (current.index == current.kids.size()) {
                COSBase count = dereference(
                        current.node.getItem(COSName.COUNT));
                if (!(count instanceof COSInteger)
                        || ((COSInteger) count).longValue()
                                != current.descendantPageCount) {
                    throw failureMode.destinationFailure();
                }
                pending.pop();
                if (pending.isEmpty()) {
                    rootPageCount = current.descendantPageCount;
                } else {
                    RawPageTreeFrame parent = pending.peek();
                    if (parent.descendantPageCount
                            > Long.MAX_VALUE - current.descendantPageCount) {
                        throw failureMode.destinationFailure();
                    }
                    parent.descendantPageCount +=
                            current.descendantPageCount;
                }
                continue;
            }

            COSBase rawChild = current.kids.get(current.index++);
            COSBase childValue = dereference(rawChild);
            if (!(childValue instanceof COSDictionary)) {
                throw failureMode.destinationFailure();
            }
            COSDictionary child = (COSDictionary) childValue;
            COSBase type = dereference(child.getItem(COSName.TYPE));
            if (COSName.PAGES.equals(type)) {
                pending.push(rawPageTreeFrame(
                        rawChild,
                        current.node,
                        visited,
                        failureMode,
                        current.depth + 1));
            } else if (COSName.PAGE.equals(type)) {
                if (visited.put(child, Boolean.TRUE) != null
                        || dereference(child.getItem(COSName.PARENT))
                                != current.node) {
                    throw failureMode.destinationFailure();
                }
                pages.add(rawChild);
                if (current.descendantPageCount == Long.MAX_VALUE) {
                    throw failureMode.destinationFailure();
                }
                current.descendantPageCount++;
            } else {
                throw failureMode.destinationFailure();
            }
        }
        return rootPageCount;
    }

    private RawPageTreeFrame rawPageTreeFrame(
            COSBase rawNode,
            COSDictionary expectedParent,
            IdentityHashMap<COSDictionary, Boolean> visited,
            StructureFailure failureMode,
            int depth) throws DocumentFailure {
        resources.checkpoint();
        resources.requireNestingDepth(depth);
        if (!(rawNode instanceof COSObject)) {
            throw failureMode.destinationFailure();
        }
        COSBase nodeValue = dereference(rawNode);
        if (!(nodeValue instanceof COSDictionary)) {
            throw failureMode.destinationFailure();
        }
        COSDictionary node = (COSDictionary) nodeValue;
        if (visited.put(node, Boolean.TRUE) != null
                || !COSName.PAGES.equals(dereference(
                        node.getItem(COSName.TYPE)))) {
            throw failureMode.destinationFailure();
        }
        COSBase actualParent = dereference(node.getItem(COSName.PARENT));
        if (expectedParent == null
                ? actualParent != null
                : actualParent != expectedParent) {
            throw failureMode.destinationFailure();
        }
        COSBase kidsValue = dereference(node.getItem(COSName.KIDS));
        if (!(kidsValue instanceof COSArray)) {
            throw failureMode.destinationFailure();
        }
        return new RawPageTreeFrame(
                node,
                (COSArray) kidsValue,
                depth);
    }

    private static final class NameTreeEntry {

        private final COSString key;
        private final COSBase value;

        NameTreeEntry(COSString key, COSBase value) {
            this.key = key;
            this.value = value;
        }
    }

    private static final class RawPageTreeFrame {

        private final COSDictionary node;
        private final COSArray kids;
        private final int depth;
        private int index;
        private long descendantPageCount;

        private RawPageTreeFrame(
                COSDictionary node,
                COSArray kids,
                int depth) {
            this.node = node;
            this.kids = kids;
            this.depth = depth;
        }
    }

    private List<NameTreeEntry> readNameTreeEntries(
            COSBase rawRoot,
            StructureFailure failureMode,
            long maximumEntries) throws DocumentFailure {
        List<NameTreeEntry> entries = new java.util.ArrayList<NameTreeEntry>();
        collectNameTreeEntries(
                rawRoot,
                null,
                null,
                entries,
                new IdentityHashMap<COSBase, Boolean>(),
                failureMode,
                maximumEntries,
                1);
        return entries;
    }

    private void collectNameTreeEntries(
            COSBase rawNode,
            String minimumKey,
            String maximumKey,
            List<NameTreeEntry> entries,
            IdentityHashMap<COSBase, Boolean> visited,
            StructureFailure failureMode,
            long maximumEntries,
            int depth) throws DocumentFailure {
        resources.checkpoint();
        resources.requireNestingDepth(depth);
        if (depth > MAX_NAME_TREE_DEPTH) {
            throw failureMode.destinationFailure();
        }
        COSBase nodeValue = dereference(rawNode);
        if (!(nodeValue instanceof COSDictionary)
                || visited.put(nodeValue, Boolean.TRUE) != null) {
            throw failureMode.destinationFailure();
        }
        COSDictionary node = (COSDictionary) nodeValue;

        COSBase limitsValue = dereference(node.getItem(COSName.LIMITS));
        if (limitsValue != null) {
            if (!(limitsValue instanceof COSArray)
                    || ((COSArray) limitsValue).size() != 2
                    || !(dereference(((COSArray) limitsValue).get(0))
                            instanceof COSString)
                    || !(dereference(((COSArray) limitsValue).get(1))
                            instanceof COSString)) {
                throw failureMode.destinationFailure();
            }
            String lower = checkpointedHexadecimal((COSString) dereference(
                    ((COSArray) limitsValue).get(0)));
            String upper = checkpointedHexadecimal((COSString) dereference(
                    ((COSArray) limitsValue).get(1)));
            if (PdfBoxStringSupport.compareHexadecimal(
                    lower, upper, resources) > 0) {
                throw failureMode.destinationFailure();
            }
            if (minimumKey != null
                    && PdfBoxStringSupport.compareHexadecimal(
                            lower, minimumKey, resources) < 0) {
                throw failureMode.destinationFailure();
            }
            if (maximumKey != null
                    && PdfBoxStringSupport.compareHexadecimal(
                            upper, maximumKey, resources) > 0) {
                throw failureMode.destinationFailure();
            }
            minimumKey = lower;
            maximumKey = upper;
        }

        COSBase namesValue = dereference(node.getItem(COSName.NAMES));
        if (namesValue != null) {
            if (!(namesValue instanceof COSArray)
                    || ((COSArray) namesValue).size() % 2 != 0) {
                throw failureMode.destinationFailure();
            }
            COSArray pairs = (COSArray) namesValue;
            for (int index = 0; index < pairs.size(); index += 2) {
                resources.checkpoint();
                COSBase keyValue = dereference(pairs.get(index));
                if (!(keyValue instanceof COSString)) {
                    throw failureMode.destinationFailure();
                }
                COSString key = (COSString) keyValue;
                String keyBytes = checkpointedHexadecimal(key);
                if (!entries.isEmpty()
                        && PdfBoxStringSupport.compareHexadecimal(
                                checkpointedHexadecimal(
                                        entries.get(entries.size() - 1).key),
                                keyBytes,
                                resources) >= 0) {
                    throw failureMode.destinationFailure();
                }
                if (minimumKey != null
                        && PdfBoxStringSupport.compareHexadecimal(
                                keyBytes, minimumKey, resources) < 0) {
                    throw failureMode.destinationFailure();
                }
                if (maximumKey != null
                        && PdfBoxStringSupport.compareHexadecimal(
                                keyBytes, maximumKey, resources) > 0) {
                    throw failureMode.destinationFailure();
                }
                if (maximumEntries >= 0L
                        && entries.size() >= maximumEntries) {
                    throw failure(
                            DocumentFailureCode.METADATA_LIMIT_EXCEEDED,
                            "The metadata access limit was exceeded.");
                }
                entries.add(new NameTreeEntry(key, pairs.get(index + 1)));
            }
        }

        COSBase kidsValue = dereference(node.getItem(COSName.KIDS));
        if (kidsValue != null) {
            if (!(kidsValue instanceof COSArray)
                    || minimumKey == null
                    || maximumKey == null) {
                throw failureMode.destinationFailure();
            }
            COSArray kids = (COSArray) kidsValue;
            for (int index = 0; index < kids.size(); index++) {
                resources.checkpoint();
                collectNameTreeEntries(
                        kids.get(index),
                        minimumKey,
                        maximumKey,
                        entries,
                        visited,
                        failureMode,
                        maximumEntries,
                        depth + 1);
            }
        }
        if (namesValue == null && kidsValue == null) {
            throw failureMode.destinationFailure();
        }
    }

    private String checkpointedHexadecimal(COSString value)
            throws DocumentFailure {
        resources.checkpoint();
        String result = value.toHexString();
        resources.checkpoint();
        return result;
    }

    PageDestination destinationFromArray(
            COSBase rawValue,
            IdentityHashMap<COSDictionary, Integer> pageNumbers)
            throws DocumentFailure {
        COSBase value = dereference(rawValue);
        if (!(value instanceof COSArray)) {
            return null;
        }
        COSArray array = (COSArray) value;
        if (array.size() < 2) {
            return null;
        }
        COSBase pageValue = dereference(array.get(0));
        Integer pageNumber = pageValue instanceof COSDictionary
                ? pageNumbers.get(pageValue)
                : null;
        if (pageNumber == null) {
            return null;
        }
        COSBase styleValue = dereference(array.get(1));
        if (!(styleValue instanceof COSName)) {
            return null;
        }
        String style = ((COSName) styleValue).getName();
        PageDestination.Style destinationStyle = DESTINATION_STYLES.get(style);
        if (destinationStyle == null
                || array.size() != 2 + operandCount(destinationStyle)) {
            return null;
        }
        java.util.List<java.math.BigDecimal> operands =
                new java.util.ArrayList<java.math.BigDecimal>();
        for (int index = 2; index < array.size(); index++) {
            COSBase operand = dereference(array.get(index));
            if (operand == null || operand instanceof COSNull) {
                if (destinationStyle != PageDestination.Style.XYZ) {
                    return null;
                }
                operands.add(null);
                continue;
            }
            java.math.BigDecimal number = decimalValue(operand);
            if (number == null) {
                return null;
            }
            operands.add(number);
        }
        switch (destinationStyle) {
            case FIT:
                return PageDestination.fit(pageNumber.intValue());
            case FIT_B:
                return PageDestination.fitB(pageNumber.intValue());
            case FIT_H:
                return PageDestination.fitH(
                        pageNumber.intValue(),
                        operands.get(0));
            case FIT_BH:
                return PageDestination.fitBH(
                        pageNumber.intValue(),
                        operands.get(0));
            case FIT_V:
                return PageDestination.fitV(
                        pageNumber.intValue(),
                        operands.get(0));
            case FIT_BV:
                return PageDestination.fitBV(
                        pageNumber.intValue(),
                        operands.get(0));
            case FIT_R:
                return PageDestination.fitR(
                        pageNumber.intValue(),
                        operands.get(0),
                        operands.get(1),
                        operands.get(2),
                        operands.get(3));
            default:
                return PageDestination.xyz(
                        pageNumber.intValue(),
                        operands.get(0),
                        operands.get(1),
                        operands.get(2));
        }
    }

    COSArray destinationToArray(
            PageDestination destination,
            COSBase pageReference,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        COSArray array = new COSArray();
        array.setDirect(true);
        array.add(pageReference);
        array.add(COSName.getPDFName(destinationStyleName(
                destination.getStyle())));
        for (java.math.BigDecimal operand : destination.getOperands()) {
            if (operand == null) {
                array.add(COSNull.NULL);
            } else {
                array.add(backendDestinationNumber(operand, ownership));
            }
        }
        return array;
    }

    private COSBase backendDestinationNumber(
            java.math.BigDecimal decimal,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        return PdfBoxValueAdapter.backendNumber(
                decimal,
                resources,
                ownership,
                PdfBoxMetadataOperations::invalidDestinationsCommand);
    }

    private static String destinationStyleName(PageDestination.Style style) {
        switch (style) {
            case XYZ:
                return "XYZ";
            case FIT:
                return "Fit";
            case FIT_H:
                return "FitH";
            case FIT_V:
                return "FitV";
            case FIT_R:
                return "FitR";
            case FIT_B:
                return "FitB";
            case FIT_BH:
                return "FitBH";
            default:
                return "FitBV";
        }
    }

    private static int operandCount(PageDestination.Style style) {
        switch (style) {
            case XYZ:
                return 3;
            case FIT_H:
            case FIT_V:
            case FIT_BH:
            case FIT_BV:
                return 1;
            case FIT_R:
                return 4;
            default:
                return 0;
        }
    }

    private static final Map<String, PageDestination.Style> DESTINATION_STYLES =
            destinationStyles();

    private static Map<String, PageDestination.Style> destinationStyles() {
        Map<String, PageDestination.Style> styles =
                new LinkedHashMap<String, PageDestination.Style>();
        for (PageDestination.Style style : PageDestination.Style.values()) {
            styles.put(destinationStyleName(style), style);
        }
        return styles;
    }

    private java.math.BigDecimal decimalValue(COSBase value)
            throws DocumentFailure {
        if (value instanceof COSInteger) {
            return java.math.BigDecimal.valueOf(
                    ((COSInteger) value).longValue());
        }
        if (value instanceof COSFloat) {
            try {
                return PdfBoxValueAdapter.serializedNumber(
                        (COSFloat) value, resources);
            } catch (DocumentFailure failure) {
                throw failure;
            } catch (IOException | NumberFormatException invalidNumber) {
                resources.rethrowResourceOrTerminalFailure(invalidNumber);
                return null;
            }
        }
        return null;
    }

    /**
     * The failure translation for managed-structure validation problems.
     */
    enum StructureFailure {

        /** Preflight rejection before page mutation. */
        PRESERVE,

        /** Safe failure while evaluating a metadata query. */
        QUERY,

        /** Safe failure while applying a metadata command. */
        COMMAND;

        DocumentFailure destinationFailure() {
            switch (this) {
                case PRESERVE:
                    return preservationUnsupported();
                case QUERY:
                    return unsafeDestinationsQuery();
                default:
                    return invalidDestinationsCommand();
            }
        }

        DocumentFailure outlineFailure() {
            switch (this) {
                case PRESERVE:
                    return preservationUnsupported();
                case QUERY:
                    return unsafeOutlineQuery();
                default:
                    return invalidOutlineCommand();
            }
        }

        DocumentFailure embeddedFilesFailure() {
            switch (this) {
                case PRESERVE:
                    return preservationUnsupported();
                case QUERY:
                    return unsafeEmbeddedFilesQuery();
                default:
                    return invalidEmbeddedFilesCommand();
            }
        }
    }

    private static DocumentFailure unsafeEmbeddedFilesQuery() {
        return failure(
                DocumentFailureCode.QUERY_FAILED,
                "The embedded files could not be inspected safely.");
    }

    private static DocumentFailure invalidEmbeddedFilesCommand() {
        return failure(
                DocumentFailureCode.COMMAND_REJECTED,
                "The embedded files could not be updated safely.");
    }

    private static DocumentFailure unsafeOutlineQuery() {
        return failure(
                DocumentFailureCode.QUERY_FAILED,
                "The document outline could not be inspected safely.");
    }

    private static DocumentFailure invalidOutlineCommand() {
        return failure(
                DocumentFailureCode.COMMAND_REJECTED,
                "The document outline could not be updated safely.");
    }

    private static DocumentFailure unsafeDestinationsQuery() {
        return failure(
                DocumentFailureCode.QUERY_FAILED,
                "The named destinations could not be inspected safely.");
    }

    private static DocumentFailure invalidDestinationsCommand() {
        return failure(
                DocumentFailureCode.COMMAND_REJECTED,
                "The named destinations could not be updated safely.");
    }

    private void setXmpMetadata(SetXmpMetadata command)
            throws DocumentFailure {
        try (WorkflowResourceContext.MemoryReservation packetMemory =
                resources.reserveOwnedMemory(
                        command.getXmpPacketLength())) {
            byte[] packet = command.getXmpPacket();
            requireWellFormedXmpPacket(packet);
            try {
                COSStream metadata = document.getDocument().createCOSStream();
                metadata.setItem(COSName.TYPE, COSName.METADATA);
                metadata.setItem(
                        COSName.SUBTYPE,
                        COSName.getPDFName("XML"));
                try (OutputStream output = metadata.createOutputStream()) {
                    resources.writeBytesAsIOException(output, packet);
                }
                document.getDocumentCatalog().getCOSObject().setItem(
                        COSName.METADATA,
                        metadata);
            } catch (IOException streamFailure) {
                resources.rethrowResourceOrTerminalFailure(streamFailure);
                throw failure(
                        DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                        "The XMP metadata could not be updated safely.");
            }
        }
    }

    private byte[] xmpMetadata(XmpMetadata query) throws DocumentFailure {
        COSBase rawMetadata = document.getDocumentCatalog().getCOSObject()
                .getItem(COSName.METADATA);
        if (rawMetadata == null) {
            return null;
        }
        COSBase metadata = dereference(rawMetadata);
        if (!(metadata instanceof COSStream)
                || !COSName.METADATA.equals(dereference(
                        ((COSStream) metadata).getItem(COSName.TYPE)))
                || !COSName.getPDFName("XML").equals(dereference(
                        ((COSStream) metadata).getItem(COSName.SUBTYPE)))) {
            throw unsafeXmpQuery();
        }
        long maximumBytes = query.getMaximumBytes();
        try (WorkflowResourceContext.OwnedByteAccumulator packet =
                resources.ownedByteAccumulator()) {
            PdfBoxHostileInputPreflight.decodeStream(
                    (COSStream) metadata,
                    resources,
                    new BoundedMetadataOutput(packet, maximumBytes));
            return packet.finishRetained();
        } catch (MetadataLimitIOException exhausted) {
            throw failure(
                    DocumentFailureCode.METADATA_LIMIT_EXCEEDED,
                    "The metadata access limit was exceeded.");
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException decodeFailure) {
            resources.rethrowResourceOrTerminalFailure(decodeFailure);
            throw unsafeXmpQuery();
        }
    }

    private void requireWellFormedXmpPacket(byte[] packet)
            throws DocumentFailure {
        if (packet.length == 0) {
            throw invalidXmpPacket();
        }
        if (packet.length > MAX_METADATA_PACKET_BYTES) {
            throw failure(
                    DocumentFailureCode.COMMAND_REJECTED,
                    "The XMP packet exceeds the supported metadata packet size.");
        }
        if (!containsAscii(packet, "<x:xmpmeta")) {
            throw invalidXmpPacket();
        }
        try {
            javax.xml.parsers.DocumentBuilderFactory factory =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true);
            factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities",
                    false);
            factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities",
                    false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            javax.xml.parsers.DocumentBuilder builder =
                    factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.ErrorHandler() {
                @Override
                public void warning(
                        org.xml.sax.SAXParseException warning) {
                    // Warnings do not affect well-formedness.
                }

                @Override
                public void error(org.xml.sax.SAXParseException error)
                        throws org.xml.sax.SAXException {
                    throw error;
                }

                @Override
                public void fatalError(
                        org.xml.sax.SAXParseException fatal)
                        throws org.xml.sax.SAXException {
                    throw fatal;
                }
            });
            try (java.io.InputStream input = resources.checkpointedInput(
                    new java.io.ByteArrayInputStream(packet))) {
                org.xml.sax.InputSource source =
                        new org.xml.sax.InputSource(input);
                source.setEncoding("UTF-8");
                builder.parse(source);
            }
        } catch (javax.xml.parsers.ParserConfigurationException
                | org.xml.sax.SAXException
                | IOException invalidPacket) {
            resources.rethrowResourceOrTerminalFailure(invalidPacket);
            throw invalidXmpPacket();
        }
    }

    private boolean containsAscii(byte[] bytes, String needle)
            throws DocumentFailure {
        if (bytes.length < needle.length()) {
            return false;
        }
        int finalStart = bytes.length - needle.length();
        for (int start = 0; start <= finalStart; start++) {
            if ((start & 1023) == 0) {
                resources.checkpoint();
            }
            int index = 0;
            while (index < needle.length()
                    && (bytes[start + index] & 0xff)
                            == needle.charAt(index)) {
                index++;
            }
            if (index == needle.length()) {
                return true;
            }
        }
        return false;
    }

    private void updateInfo(UpdateDocumentInfo update) throws DocumentFailure {
        for (Map.Entry<String, PdfValue> entry : update.getEntries().entrySet()) {
            resources.checkpoint();
            requireValidInfoName(entry.getKey());
            requireInfoCommandValue(entry.getValue(), 1);
        }
        for (String removedName : update.getRemovedNames()) {
            resources.checkpoint();
            requireValidInfoName(removedName);
        }
        if (update.getEntries().isEmpty() && update.getRemovedNames().isEmpty()) {
            return;
        }

        try (WorkflowResourceContext.OwnedMemoryScope ownership =
                resources.ownedMemoryScope()) {
            COSDictionary prepared = new COSDictionary();
            for (Map.Entry<String, PdfValue> entry
                    : update.getEntries().entrySet()) {
                resources.checkpoint();
                prepared.setItem(
                        COSName.getPDFName(entry.getKey()),
                        backendScalarValue(entry.getValue(), ownership));
            }

            COSDictionary info = writableInfo();
            for (String removedName : update.getRemovedNames()) {
                resources.checkpoint();
                info.removeItem(COSName.getPDFName(removedName));
            }
            for (Map.Entry<COSName, COSBase> entry : prepared.entrySet()) {
                resources.checkpoint();
                info.setItem(entry.getKey(), entry.getValue());
            }
            ownership.transfer();
        }
    }

    private COSDictionary writableInfo() throws DocumentFailure {
        COSDictionary trailer = document.getDocument().getTrailer();
        COSBase rawInfo = trailer.getItem(COSName.INFO);
        COSBase infoValue = dereference(rawInfo);
        if (infoValue instanceof COSDictionary
                && infoValue != document.getDocumentCatalog().getCOSObject()) {
            return (COSDictionary) infoValue;
        }
        if (rawInfo != null) {
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The document information could not be updated safely.");
        }
        COSDictionary info = new COSDictionary();
        trailer.setItem(COSName.INFO, info);
        return info;
    }

    private PdfDictionary documentInfo() throws DocumentFailure {
        COSDictionary trailer = document.getDocument().getTrailer();
        COSBase rawInfo = trailer == null
                ? null
                : trailer.getItem(COSName.INFO);
        if (rawInfo == null) {
            return PdfDictionary.builder().build();
        }
        COSBase infoValue = dereference(rawInfo);
        if (!(infoValue instanceof COSDictionary)
                || infoValue == document.getDocumentCatalog().getCOSObject()) {
            throw unsafeInfoQuery();
        }
        try (WorkflowResourceContext.OwnedMemoryScope ownership =
                resources.ownedMemoryScope()) {
            PdfDictionary result = detachedInfoDictionary(
                    (COSDictionary) infoValue,
                    new IdentityHashMap<COSBase, Boolean>(),
                    0,
                    ownership);
            ownership.transfer();
            return result;
        } catch (RuntimeException conversionFailure) {
            resources.rethrowResourceOrTerminalFailure(conversionFailure);
            throw unsafeInfoQuery();
        }
    }

    private PdfDictionary detachedInfoDictionary(
            COSDictionary dictionary,
            IdentityHashMap<COSBase, Boolean> visited,
            int depth,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        resources.checkpoint();
        resources.requireNestingDepth(depth);
        if (visited.put(dictionary, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("cycle");
        }
        PdfDictionary.Builder detached = PdfDictionary.builder();
        for (Map.Entry<COSName, COSBase> entry : dictionary.entrySet()) {
            resources.checkpoint();
            PdfValue value = detachedInfoValue(
                    entry.getValue(),
                    visited,
                    depth,
                    ownership);
            if (value == null) {
                throw new IllegalArgumentException("unproven");
            }
            detached.put(PdfName.of(entry.getKey().getName()), value);
        }
        visited.remove(dictionary);
        return detached.build();
    }

    private PdfValue detachedInfoValue(
            COSBase rawValue,
            IdentityHashMap<COSBase, Boolean> visited,
            int depth,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        resources.checkpoint();
        resources.requireNestingDepth(depth);
        if (depth > MAX_METADATA_GRAPH_DEPTH) {
            return null;
        }
        COSBase value = dereference(rawValue);
        if (value == null || value instanceof COSNull) {
            return PdfNull.INSTANCE;
        }
        if (value instanceof COSStream) {
            return null;
        }
        if (value instanceof COSBoolean) {
            return PdfBoolean.of(((COSBoolean) value).getValue());
        }
        if (value instanceof COSInteger) {
            return PdfNumber.of(((COSInteger) value).longValue());
        }
        if (value instanceof COSFloat) {
            try {
                return PdfNumber.of(PdfBoxValueAdapter.serializedNumber(
                        (COSFloat) value, resources));
            } catch (DocumentFailure failure) {
                throw failure;
            } catch (IOException | NumberFormatException invalidNumber) {
                resources.rethrowResourceOrTerminalFailure(invalidNumber);
                return null;
            }
        }
        if (value instanceof COSString) {
            return PdfBoxStringSupport.detached(
                    (COSString) value,
                    resources,
                    ownership,
                    PdfBoxMetadataOperations::unsafeInfoQuery);
        }
        if (value instanceof COSName) {
            return PdfName.of(((COSName) value).getName());
        }
        if (value instanceof COSArray) {
            COSArray array = (COSArray) value;
            if (visited.put(array, Boolean.TRUE) != null) {
                return null;
            }
            PdfValue[] elements = new PdfValue[array.size()];
            for (int index = 0; index < array.size(); index++) {
                elements[index] = detachedInfoValue(
                        array.get(index),
                        visited,
                        depth + 1,
                        ownership);
                if (elements[index] == null) {
                    return null;
                }
            }
            visited.remove(array);
            return PdfArray.of(elements);
        }
        if (value instanceof COSDictionary) {
            COSDictionary nested = (COSDictionary) value;
            COSBase type = dereference(nested.getItem(COSName.TYPE));
            if (COSName.PAGE.equals(type) || COSName.PAGES.equals(type)) {
                return null;
            }
            return detachedInfoDictionary(
                    nested, visited, depth + 1, ownership);
        }
        return null;
    }

    /**
     * Proves recursively that one document information graph contains no
     * stream, no cycle, and no reference to page or page-tree structures.
     *
     * @param info the information dictionary
     * @throws DocumentFailure when the graph is not provably safe
     */
    private void requireMetadataSafeGraph(COSDictionary info)
            throws DocumentFailure {
        requireMetadataSafeValue(
                info,
                new IdentityHashMap<COSBase, Boolean>(),
                0);
    }

    private void requireMetadataSafeValue(
            COSBase rawValue,
            IdentityHashMap<COSBase, Boolean> visited,
            int depth) throws DocumentFailure {
        resources.checkpoint();
        resources.requireNestingDepth(depth);
        if (depth > MAX_METADATA_GRAPH_DEPTH) {
            throw preservationUnsupported();
        }
        COSBase value = dereference(rawValue);
        if (value == null || value instanceof COSStream) {
            throw preservationUnsupported();
        }
        if (value instanceof COSArray) {
            if (visited.put(value, Boolean.TRUE) != null) {
                throw preservationUnsupported();
            }
            COSArray array = (COSArray) value;
            for (int index = 0; index < array.size(); index++) {
                requireMetadataSafeValue(
                        array.get(index),
                        visited,
                        depth + 1);
            }
            return;
        }
        if (value instanceof COSDictionary) {
            if (visited.put(value, Boolean.TRUE) != null) {
                throw preservationUnsupported();
            }
            COSDictionary dictionary = (COSDictionary) value;
            COSBase type = dereference(dictionary.getItem(COSName.TYPE));
            if (COSName.PAGE.equals(type) || COSName.PAGES.equals(type)) {
                throw preservationUnsupported();
            }
            for (COSBase entry : dictionary.getValues()) {
                requireMetadataSafeValue(entry, visited, depth + 1);
            }
        }
    }

    private static void requireValidInfoName(String name)
            throws DocumentFailure {
        if (name.isEmpty() || name.length() > 127) {
            throw invalidInfoName();
        }
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (character <= 0x20
                    || character == 0x7F
                    || character > 0x7E
                    || "()<>[]{}/%#".indexOf(character) >= 0) {
                throw invalidInfoName();
            }
        }
    }

    private void requireInfoCommandValue(PdfValue value, int depth)
            throws DocumentFailure {
        resources.checkpoint();
        resources.requireNestingDepth(depth);
        if (depth > MAX_METADATA_GRAPH_DEPTH) {
            throw invalidInfoValue();
        }
        if (value instanceof PdfStream
                || value instanceof PdfIndirectReference) {
            throw invalidInfoValue();
        }
        if (value instanceof PdfArray) {
            PdfArray array = (PdfArray) value;
            for (int index = 0; index < array.size(); index++) {
                requireInfoCommandValue(array.get(index), depth + 1);
            }
            return;
        }
        if (value instanceof PdfDictionary) {
            PdfDictionary dictionary = (PdfDictionary) value;
            for (int index = 0; index < dictionary.size(); index++) {
                requireInfoCommandValue(
                        dictionary.getEntry(index).getValue(),
                        depth + 1);
            }
        }
    }

    private COSBase backendScalarValue(
            PdfValue value,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        resources.checkpoint();
        if (value == PdfNull.INSTANCE) {
            return COSNull.NULL;
        }
        if (value instanceof PdfBoolean) {
            return COSBoolean.getBoolean(((PdfBoolean) value).booleanValue());
        }
        if (value instanceof PdfNumber) {
            return backendNumber((PdfNumber) value, ownership);
        }
        if (value instanceof PdfString) {
            return PdfBoxStringSupport.backendCopy(
                    (PdfString) value,
                    resources,
                    ownership,
                    PdfBoxMetadataOperations::invalidInfoValue);
        }
        if (value instanceof PdfName) {
            return COSName.getPDFName(((PdfName) value).getValue());
        }
        if (value instanceof PdfArray) {
            PdfArray array = (PdfArray) value;
            COSArray converted = new COSArray();
            converted.setDirect(true);
            for (int index = 0; index < array.size(); index++) {
                converted.add(backendScalarValue(
                        array.get(index), ownership));
            }
            return converted;
        }
        if (value instanceof PdfDictionary) {
            PdfDictionary dictionary = (PdfDictionary) value;
            COSDictionary converted = new COSDictionary();
            converted.setDirect(true);
            for (int index = 0; index < dictionary.size(); index++) {
                PdfDictionaryEntry entry = dictionary.getEntry(index);
                converted.setItem(
                        COSName.getPDFName(entry.getName().getValue()),
                        backendScalarValue(entry.getValue(), ownership));
            }
            return converted;
        }
        throw invalidInfoValue();
    }

    private COSBase backendNumber(
            PdfNumber number,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        return PdfBoxValueAdapter.backendNumber(
                number.decimalValue(),
                resources,
                ownership,
                PdfBoxMetadataOperations::invalidInfoValue);
    }

    private static COSBase dereference(COSBase value) {
        return value instanceof COSObject
                ? ((COSObject) value).getObject()
                : value;
    }

    private static final class BoundedMetadataOutput extends OutputStream {

        private final WorkflowResourceContext.OwnedByteAccumulator output;
        private final long maximum;
        private long size;

        private BoundedMetadataOutput(
                WorkflowResourceContext.OwnedByteAccumulator output,
                long maximum) {
            this.output = output;
            this.maximum = maximum;
        }

        @Override
        public void write(int value) throws IOException {
            requireCapacity(1);
            output.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length)
                throws IOException {
            if (bytes == null
                    || offset < 0
                    || length < 0
                    || offset > bytes.length - length) {
                throw new IndexOutOfBoundsException();
            }
            requireCapacity(length);
            output.write(bytes, offset, length);
        }

        private void requireCapacity(int amount) throws IOException {
            if (amount < 0 || size > maximum - amount) {
                throw new MetadataLimitIOException();
            }
            size += amount;
        }
    }

    private static final class MetadataLimitIOException extends IOException {

        private static final long serialVersionUID = 1L;
    }

    private static DocumentFailure preservationUnsupported() {
        return failure(
                DocumentFailureCode.PRESERVATION_UNSUPPORTED,
                "The document contains structures that this page operation cannot preserve safely.");
    }

    private static DocumentFailure unsafeInfoQuery() {
        return failure(
                DocumentFailureCode.QUERY_FAILED,
                "The document information could not be inspected safely.");
    }

    private static DocumentFailure unsafeXmpQuery() {
        return failure(
                DocumentFailureCode.QUERY_FAILED,
                "The XMP metadata could not be inspected safely.");
    }

    private static DocumentFailure invalidXmpPacket() {
        return failure(
                DocumentFailureCode.COMMAND_REJECTED,
                "The XMP packet is not a well-formed XMP metadata packet.");
    }

    private static DocumentFailure invalidInfoName() {
        return failure(
                DocumentFailureCode.COMMAND_REJECTED,
                "The Info command contains an invalid entry name.");
    }

    private static DocumentFailure invalidInfoValue() {
        return failure(
                DocumentFailureCode.COMMAND_REJECTED,
                "The Info command contains a value that document information cannot hold.");
    }

    static DocumentFailure failure(
            DocumentFailureCode code,
            String diagnostic) {
        return new DocumentFailure(code, CAPABILITY_ID, diagnostic);
    }
}
