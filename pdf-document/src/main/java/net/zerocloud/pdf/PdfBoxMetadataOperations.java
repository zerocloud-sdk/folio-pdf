package net.zerocloud.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
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

    PdfBoxMetadataOperations(PDDocument document) {
        this.document = Objects.requireNonNull(document, "document");
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

    private static int destinationPageIndex(
            COSBase rawArray,
            IdentityHashMap<COSDictionary, Integer> pageNumbers)
            throws DocumentFailure {
        COSBase pageValue = dereference(
                ((COSArray) dereference(rawArray)).get(0));
        for (Map.Entry<COSDictionary, Integer> entry
                : pageNumbers.entrySet()) {
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
        try {
            COSDictionary catalog = product.getDocumentCatalog()
                    .getCOSObject();
            applySplitInfo(product, snapshot.info);

            java.util.TreeMap<COSString, COSBase> destinations =
                    new java.util.TreeMap<COSString, COSBase>(NAME_ORDER);
            for (Map.Entry<COSString, COSBase> entry
                    : snapshot.destinations.entrySet()) {
                int mapped = mapping[destinationPageIndex(
                        entry.getValue(),
                        snapshot.pageNumbers)];
                if (mapped > 0) {
                    destinations.put(
                            entry.getKey(),
                            retargetedDestinationArray(
                                    entry.getValue(),
                                    product.getPage(mapped - 1)
                                            .getCOSObject()));
                }
            }
            java.util.TreeMap<COSString, COSDictionary> files =
                    new java.util.TreeMap<COSString, COSDictionary>(
                            NAME_ORDER);
            for (Map.Entry<COSString, COSDictionary> entry
                    : snapshot.files.entrySet()) {
                files.put(
                        entry.getKey(),
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
                    OutlineNode retargeted = retargetedOutlineNode(
                            node,
                            destinations,
                            null,
                            target);
                    if (retargeted != null) {
                        filtered.add(retargeted);
                    }
                }
                writeOutlineTree(catalog, filtered);
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
            throw preservationUnsupported();
        }
    }

    private static final long MAX_METADATA_PACKET_BYTES = 64L * 1024L * 1024L;

    private void applySplitInfo(PDDocument product, COSDictionary snapshot)
            throws DocumentFailure {
        COSDictionary trailer = product.getDocument().getTrailer();
        if (snapshot == null) {
            trailer.removeItem(COSName.INFO);
            return;
        }
        COSDictionary detached = new COSDictionary();
        for (Map.Entry<COSName, COSBase> entry : snapshot.entrySet()) {
            detached.setItem(
                    entry.getKey(),
                    cloneMetadataValue(
                            entry.getValue(),
                            new IdentityHashMap<COSBase, COSBase>(),
                            0));
        }
        trailer.setItem(COSName.INFO, detached);
    }

    private void replaceNamesDictionary(
            COSDictionary catalog,
            java.util.TreeMap<COSString, COSBase> destinations,
            java.util.TreeMap<COSString, COSDictionary> files) {
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

    private static COSDictionary flatNameTree(
            java.util.TreeMap<COSString, ? extends COSBase> entries) {
        COSArray keysAndValues = new COSArray();
        keysAndValues.setDirect(true);
        for (Map.Entry<COSString, ? extends COSBase> entry
                : entries.entrySet()) {
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
            List<OutlineNode> nodes) throws DocumentFailure {
        if (nodes.isEmpty()) {
            catalog.removeItem(COSName.OUTLINES);
            return;
        }
        COSDictionary root = new COSDictionary();
        root.setItem(COSName.TYPE, COSName.getPDFName("Outlines"));
        int visibleTotal = writeOutlineNodeLevel(nodes, root, 1);
        root.setItem(COSName.COUNT, COSInteger.get(visibleTotal));
        catalog.setItem(COSName.OUTLINES, root);
    }

    private int writeOutlineNodeLevel(
            List<OutlineNode> nodes,
            COSDictionary parent,
            int depth) throws DocumentFailure {
        if (depth > MAX_OUTLINE_DEPTH) {
            throw preservationUnsupported();
        }
        COSDictionary first = null;
        COSDictionary previous = null;
        int visibleTotal = 0;
        for (OutlineNode node : nodes) {
            COSDictionary dictionary = new COSDictionary();
            dictionary.setItem(COSName.TITLE, new COSString(node.title));
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
                    direct.add(original.get(index));
                }
                dictionary.setItem(COSName.DEST, direct);
            } else if (node.namedName != null) {
                dictionary.setItem(COSName.DEST, node.namedName);
            }
            int descendants = 0;
            if (!node.children.isEmpty()) {
                descendants = writeOutlineNodeLevel(
                        node.children,
                        dictionary,
                        depth + 1);
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
                copied.write(packet);
            }
            target.getDocumentCatalog().getCOSObject().setItem(
                    COSName.METADATA,
                    fresh);
        } catch (IOException | RuntimeException streamFailure) {
            throw preservationUnsupported();
        }
    }

    private static COSDictionary cloneFileSpecification(
            PDDocument target,
            COSDictionary fileSpecification) throws DocumentFailure {
        try {
            return (COSDictionary) new MetadataCloneUtility(target)
                    .cloneForNewDocument(fileSpecification);
        } catch (IOException | RuntimeException cloneFailure) {
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
    final class MergedStructures {

        private final int pageCount;
        private final IdentityHashMap<COSDictionary, Integer> pageNumbers;
        private final COSDictionary info;
        private final byte[] xmpPacket;
        private final java.util.TreeMap<COSString, COSBase> destinations;
        private final java.util.TreeMap<COSString, COSDictionary> files;
        private final List<OutlineNode> outline;

        private MergedStructures(
                int pageCount,
                IdentityHashMap<COSDictionary, Integer> pageNumbers,
                COSDictionary info,
                byte[] xmpPacket,
                java.util.TreeMap<COSString, COSBase> destinations,
                java.util.TreeMap<COSString, COSDictionary> files,
                List<OutlineNode> outline) {
            this.pageCount = pageCount;
            this.pageNumbers = pageNumbers;
            this.info = info;
            this.xmpPacket = xmpPacket;
            this.destinations = destinations;
            this.files = files;
            this.outline = outline;
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
        try {
            COSDictionary catalog = source.getDocumentCatalog().getCOSObject();
            IdentityHashMap<COSDictionary, Integer> pageNumbers =
                    pageNumbersByDictionary(source, StructureFailure.PRESERVE);

            COSDictionary info = snapshotInfo(source);

            byte[] packet = null;
            COSBase rawMetadata = catalog.getItem(COSName.METADATA);
            if (rawMetadata != null) {
                packet = boundedDecodedContent(
                        (COSStream) dereference(rawMetadata),
                        MAX_METADATA_PACKET_BYTES);
            }

            java.util.TreeMap<COSString, COSBase> destinations =
                    destinationEntriesByName(source);
            java.util.TreeMap<COSString, COSDictionary> files =
                    new java.util.TreeMap<COSString, COSDictionary>(
                            NAME_ORDER);
            for (Map.Entry<COSString, COSDictionary> entry
                    : embeddedFileEntriesOf(
                            source,
                            StructureFailure.PRESERVE).entrySet()) {
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
            return new MergedStructures(
                    source.getNumberOfPages(),
                    pageNumbers,
                    info,
                    packet,
                    destinations,
                    files,
                    outline);
        } catch (DocumentFailure failure) {
            if (failure.getCode() == DocumentFailureCode.QUERY_FAILED
                    || failure.getCode()
                            == DocumentFailureCode.METADATA_LIMIT_EXCEEDED) {
                throw preservationUnsupported();
            }
            throw failure;
        } catch (RuntimeException backendFailure) {
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
                new java.util.TreeMap<COSString, COSBase>(NAME_ORDER);
        for (NameTreeEntry entry : destinationEntriesOf(
                source,
                StructureFailure.PRESERVE)) {
            destinations.put(entry.key, entry.value);
        }
        return destinations;
    }

    java.util.Set<String> namedDestinationNames(PDDocument source)
            throws DocumentFailure {
        java.util.Set<String> names = new java.util.HashSet<String>();
        for (COSString name : destinationEntriesByName(source).keySet()) {
            names.add(name.getString());
        }
        return names;
    }

    private static java.util.TreeSet<COSString> namedNamesOf(
            java.util.TreeMap<COSString, COSBase> destinations) {
        java.util.TreeSet<COSString> names =
                new java.util.TreeSet<COSString>(NAME_ORDER);
        names.addAll(destinations.keySet());
        return names;
    }

    List<Map<String, String>> applyMergedStructures(
            PDDocument target,
            List<MergedStructures> sources,
            boolean primaryHadInfo) throws DocumentFailure {
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
                infoSnapshots.add(source.info);
            }
            applyMergedInfo(target, infoSnapshots, primaryHadInfo);

            if (catalog.getItem(COSName.METADATA) == null) {
                for (MergedStructures source : sources) {
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
                            NAME_ORDER);
            for (Map.Entry<COSString, COSDictionary> entry
                    : embeddedFileEntriesOf(
                            target,
                            StructureFailure.PRESERVE).entrySet()) {
                files.put(entry.getKey(), entry.getValue());
            }

            List<OutlineNode> outline =
                    new java.util.ArrayList<OutlineNode>();
            COSBase rawOutlines = catalog.getItem(COSName.OUTLINES);
            if (rawOutlines != null) {
                outline.addAll(readOutlineNodes(
                        rawOutlines,
                        pageNumbers,
                        namedNamesOf(destinations),
                        StructureFailure.PRESERVE,
                        -1L));
            }

            int pageOffset = target.getNumberOfPages();
            for (MergedStructures source : sources) {
                pageOffset -= source.pageCount;
            }
            final List<COSBase> references = pageReferences;
            int base = pageOffset;
            for (MergedStructures source : sources) {
                final int sourceBase = base;
                final IdentityHashMap<COSDictionary, Integer> sourcePages =
                        source.pageNumbers;
                java.util.TreeMap<COSString, COSString> renames =
                        new java.util.TreeMap<COSString, COSString>(
                                NAME_ORDER);
                for (Map.Entry<COSString, COSBase> entry
                        : source.destinations.entrySet()) {
                    int sourceIndex = destinationPageIndex(
                            entry.getValue(),
                            sourcePages);
                    COSString finalKey = availableKey(
                            entry.getKey(),
                            destinations.keySet());
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
                    publicRenames.put(
                            rename.getKey().getString(),
                            rename.getValue().getString());
                }
                sourceRenames.add(publicRenames);
                for (Map.Entry<COSString, COSDictionary> entry
                        : source.files.entrySet()) {
                    files.put(
                            availableKey(entry.getKey(), files.keySet()),
                            cloneFileSpecification(target, entry.getValue()));
                }
                if (source.outline != null) {
                    for (OutlineNode node : source.outline) {
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
            writeOutlineTree(catalog, outline);
        } catch (DocumentFailure failure) {
            if (failure.getCode() == DocumentFailureCode.QUERY_FAILED) {
                throw preservationUnsupported();
            }
            throw failure;
        } catch (RuntimeException backendFailure) {
            throw preservationUnsupported();
        }
        return sourceRenames;
    }

    private static COSString availableKey(
            COSString preferred,
            java.util.Set<COSString> taken) {
        COSString candidate = preferred;
        int suffix = 0;
        while (taken.contains(candidate)) {
            suffix++;
            byte[] base = preferred.getBytes();
            byte[] suffixBytes = ("-" + suffix).getBytes(
                    java.nio.charset.StandardCharsets.US_ASCII);
            byte[] combined = new byte[base.length + suffixBytes.length];
            System.arraycopy(base, 0, combined, 0, base.length);
            System.arraycopy(
                    suffixBytes,
                    0,
                    combined,
                    base.length,
                    suffixBytes.length);
            candidate = new COSString(combined);
        }
        return candidate;
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
    COSDictionary snapshotInfo(PDDocument source) throws DocumentFailure {
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
                            0));
        }
        return snapshot;
    }

    private static COSBase cloneMetadataValue(
            COSBase rawValue,
            IdentityHashMap<COSBase, COSBase> cloned,
            int depth) throws DocumentFailure {
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
            return new COSString(((COSString) value).getBytes());
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
                        depth + 1));
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
                                depth + 1));
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
            boolean primaryHadInfo) throws DocumentFailure {
        for (COSDictionary snapshot : snapshots) {
            if (snapshot == null) {
                continue;
            }
            COSDictionary info = writableInfoFor(target);
            for (Map.Entry<COSName, COSBase> entry : snapshot.entrySet()) {
                if (!info.containsKey(entry.getKey())) {
                    info.setItem(entry.getKey(), entry.getValue());
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
                    new java.util.TreeSet<COSString>(NAME_ORDER);
            for (NameTreeEntry entry : destinationEntries) {
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
        List<COSBase> pageReferences = rawPageReferences(
                document,
                StructureFailure.COMMAND);
        for (PageDestination destination : command.getEntries().values()) {
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
                if (destinationFromArray(entry.value, pageNumbers) == null) {
                    throw invalidDestinationsCommand();
                }
            }
        }

        java.util.TreeMap<COSString, COSBase> merged =
                new java.util.TreeMap<COSString, COSBase>(NAME_ORDER);
        for (NameTreeEntry entry : current) {
            merged.put(entry.key, entry.value);
        }
        for (String removedName : command.getRemovedNames()) {
            COSString probe = new COSString(removedName);
            merged.remove(probe);
        }
        for (Map.Entry<String, PageDestination> entry
                : command.getEntries().entrySet()) {
            PageDestination destination = entry.getValue();
            COSArray array = destinationToArray(
                    destination,
                    pageReferences.get(destination.getPageNumber() - 1));
            merged.put(new COSString(entry.getKey()), array);
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
        try {
            replaceOutlineTreeGuarded(command);
        } catch (DocumentFailure failure) {
            if (failure.getCode() == DocumentFailureCode.COMMAND_REJECTED) {
                throw invalidOutlineCommand();
            }
            throw failure;
        }
    }

    private void replaceOutlineTreeGuarded(ReplaceOutlineTree command)
            throws DocumentFailure {
        List<COSBase> pageReferences = rawPageReferences(
                document,
                StructureFailure.COMMAND);
        IdentityHashMap<COSDictionary, Integer> pageNumbers =
                pageNumbersByDictionary(document, StructureFailure.COMMAND);
        java.util.TreeSet<COSString> namedNames =
                new java.util.TreeSet<COSString>(NAME_ORDER);
        for (NameTreeEntry entry : destinationEntriesOf(
                document,
                StructureFailure.COMMAND)) {
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
                1);
        root.setItem(COSName.COUNT, COSInteger.get(visibleTotal));
        catalog.setItem(COSName.OUTLINES, root);
    }

    private void requireValidOutlineItems(
            List<OutlineItem> items,
            int pageCount,
            java.util.TreeSet<COSString> namedNames,
            int depth) throws DocumentFailure {
        if (depth > MAX_OUTLINE_DEPTH) {
            throw invalidOutlineCommand();
        }
        for (OutlineItem item : items) {
            if (item.getDestination().isPresent()
                    && item.getDestination().get().getPageNumber()
                            > pageCount) {
                throw failure(
                        DocumentFailureCode.PAGE_RANGE_INVALID,
                        "The destination page is outside the current document.");
            }
            if (item.getNamedDestination().isPresent()
                    && !namedNames.contains(new COSString(
                            item.getNamedDestination().get()))) {
                throw invalidOutlineCommand();
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
            int depth) throws DocumentFailure {
        if (depth > MAX_OUTLINE_DEPTH) {
            throw invalidOutlineCommand();
        }
        COSDictionary first = null;
        COSDictionary previous = null;
        int visibleTotal = 0;
        for (OutlineItem item : items) {
            COSDictionary node = new COSDictionary();
            node.setItem(COSName.TITLE, new COSString(item.getTitle()));
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
                                        destination.getPageNumber() - 1)));
            } else if (item.getNamedDestination().isPresent()) {
                node.setItem(
                        COSName.DEST,
                        new COSString(item.getNamedDestination().get()));
            }
            int descendants = 0;
            if (!item.getChildren().isEmpty()) {
                descendants = writeOutlineLevel(
                        item.getChildren(),
                        node,
                        pageReferences,
                        depth + 1);
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
                    new java.util.TreeSet<COSString>(NAME_ORDER);
            for (NameTreeEntry entry : destinationEntriesOf(
                    document,
                    StructureFailure.QUERY)) {
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
        try {
            embedFileGuarded(command);
        } catch (DocumentFailure failure) {
            if (failure.getCode() == DocumentFailureCode.COMMAND_REJECTED) {
                throw invalidEmbeddedFilesCommand();
            }
            throw failure;
        }
    }

    private void embedFileGuarded(EmbedFile command) throws DocumentFailure {
        EmbeddedFile file = command.getFile();
        byte[] content = file.getContent();

        COSDictionary catalog = document.getDocumentCatalog().getCOSObject();
        COSBase rawNames = catalog.getItem(COSName.NAMES);
        COSDictionary names;
        java.util.TreeMap<COSString, COSDictionary> current;
        if (rawNames == null) {
            names = null;
            current = new java.util.TreeMap<COSString, COSDictionary>(
                    NAME_ORDER);
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
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The metadata operation could not be completed safely.");
        }
        stream.setItem(COSName.TYPE, COSName.EMBEDDED_FILE);
        if (file.getMimeSubtype().isPresent()) {
            stream.setItem(
                    COSName.SUBTYPE,
                    mimeSubtypeName(file.getMimeSubtype().get()));
        }
        COSDictionary params = new COSDictionary();
        params.setItem(COSName.SIZE, COSInteger.get(content.length));
        params.setItem(
                COSName.getPDFName("CheckSum"),
                new COSString(md5(content)));
        stream.setItem(COSName.PARAMS, params);
        try (OutputStream embedded = stream.createOutputStream()) {
            embedded.write(content);
        } catch (IOException | RuntimeException writeFailure) {
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The metadata operation could not be completed safely.");
        }

        COSDictionary specification = new COSDictionary();
        specification.setItem(COSName.TYPE, COSName.getPDFName("Filespec"));
        specification.setItem(COSName.F, new COSString(file.getName()));
        if (file.getDescription().isPresent()) {
            specification.setItem(
                    COSName.DESC,
                    new COSString(file.getDescription().get()));
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

        current.put(new COSString(file.getName()), specification);
        if (names == null) {
            names = new COSDictionary();
            catalog.setItem(COSName.NAMES, names);
        }
        COSArray keysAndValues = new COSArray();
        keysAndValues.setDirect(true);
        for (Map.Entry<COSString, COSDictionary> entry
                : current.entrySet()) {
            keysAndValues.add(entry.getKey());
            keysAndValues.add(entry.getValue());
        }
        COSDictionary tree = new COSDictionary();
        tree.setItem(COSName.NAMES, keysAndValues);
        names.setItem(COSName.EMBEDDED_FILES, tree);
    }

    private List<EmbeddedFileSummary> embeddedFiles(EmbeddedFiles query)
            throws DocumentFailure {
        try {
            java.util.TreeMap<COSString, COSDictionary> entries =
                    embeddedFileEntriesOf(
                            document,
                            StructureFailure.QUERY);
            List<EmbeddedFileSummary> summaries =
                    new java.util.ArrayList<EmbeddedFileSummary>();
            for (Map.Entry<COSString, COSDictionary> entry
                    : entries.entrySet()) {
                if (summaries.size() >= query.getMaximumEntries()) {
                    throw failure(
                            DocumentFailureCode.METADATA_LIMIT_EXCEEDED,
                            "The metadata access limit was exceeded.");
                }
                EmbeddedFileFields fields = embeddedFileFields(
                        entry.getValue(),
                        StructureFailure.QUERY);
                summaries.add(new EmbeddedFileSummary(
                        entry.getKey().getString(),
                        fields.mimeSubtype,
                        fields.description,
                        fields.relationship,
                        fields.size,
                        fields.md5Hex));
            }
            return Collections.unmodifiableList(summaries);
        } catch (DocumentFailure failure) {
            if (failure.getCode() == DocumentFailureCode.QUERY_FAILED) {
                throw unsafeEmbeddedFilesQuery();
            }
            throw failure;
        }
    }

    private Optional<EmbeddedFileData> readEmbeddedFile(
            ReadEmbeddedFile query) throws DocumentFailure {
        try {
            java.util.TreeMap<COSString, COSDictionary> entries =
                    embeddedFileEntriesOf(
                            document,
                            StructureFailure.QUERY);
            COSDictionary specification = entries.get(
                    new COSString(query.getName()));
            if (specification == null) {
                return Optional.empty();
            }
            EmbeddedFileFields fields = embeddedFileFields(
                    specification,
                    StructureFailure.QUERY);
            byte[] content = boundedDecodedContent(
                    fields.stream,
                    query.getMaximumBytes());
            return Optional.of(new EmbeddedFileData(
                    query.getName(),
                    fields.mimeSubtype,
                    fields.description,
                    fields.relationship,
                    fields.size,
                    fields.md5Hex,
                    sha256Hex(content),
                    content));
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
                new java.util.TreeMap<COSString, COSDictionary>(NAME_ORDER);
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
            StructureFailure failureMode) throws DocumentFailure {
        COSBase ef = dereference(specification.getItem(COSName.EF));
        COSStream stream = embeddedFileStreamOf(
                ((COSDictionary) ef).getItem(COSName.F),
                failureMode);
        COSBase mime = dereference(stream.getItem(COSName.SUBTYPE));
        String mimeSubtype = mime instanceof COSName
                ? mimeSubtypeFromName((COSName) mime)
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
                md5Hex = hex(((COSString) checksum).getBytes());
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

    private byte[] boundedDecodedContent(
            COSStream stream,
            long maximumBytes) throws DocumentFailure {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try (InputStream decoded = stream.createInputStream()) {
            int read;
            while ((read = decoded.read(buffer)) != -1) {
                if (read > maximumBytes - content.size()) {
                    throw failure(
                            DocumentFailureCode.METADATA_LIMIT_EXCEEDED,
                            "The metadata access limit was exceeded.");
                }
                content.write(buffer, 0, read);
            }
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException decodeFailure) {
            throw unsafeEmbeddedFilesQuery();
        }
        return content.toByteArray();
    }

    private static COSName mimeSubtypeName(String mimeSubtype)
            throws DocumentFailure {
        StringBuilder name = new StringBuilder(mimeSubtype.length() + 4);
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
            return COSName.getPDFName(name.toString());
        } catch (RuntimeException invalidName) {
            throw failure(
                    DocumentFailureCode.COMMAND_REJECTED,
                    "The embedded files could not be updated safely.");
        }
    }

    private static String mimeSubtypeFromName(COSName name) {
        String encoded = name.getName();
        StringBuilder decoded = new StringBuilder(encoded.length());
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
        return decoded.toString();
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

    private static byte[] md5(byte[] content) throws DocumentFailure {
        try {
            return java.security.MessageDigest.getInstance("MD5")
                    .digest(content);
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The metadata operation could not be completed safely.");
        }
    }

    private static String sha256Hex(byte[] content) throws DocumentFailure {
        try {
            return hex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(content));
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw failure(
                    DocumentFailureCode.QUERY_FAILED,
                    "The metadata query could not be evaluated safely.");
        }
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
        COSArray kids = (COSArray) kidsValue;
        long descendantPageCount = 0L;
        for (int index = 0; index < kids.size(); index++) {
            COSBase rawChild = kids.get(index);
            COSBase childValue = dereference(rawChild);
            if (!(childValue instanceof COSDictionary)) {
                throw failureMode.destinationFailure();
            }
            COSDictionary child = (COSDictionary) childValue;
            COSBase type = dereference(child.getItem(COSName.TYPE));
            if (COSName.PAGES.equals(type)) {
                descendantPageCount += collectRawPageReferences(
                        rawChild,
                        node,
                        pages,
                        visited,
                        failureMode);
            } else if (COSName.PAGE.equals(type)) {
                if (visited.put(child, Boolean.TRUE) != null
                        || dereference(child.getItem(COSName.PARENT)) != node) {
                    throw failureMode.destinationFailure();
                }
                pages.add(rawChild);
                descendantPageCount++;
            } else {
                throw failureMode.destinationFailure();
            }
        }
        COSBase count = dereference(node.getItem(COSName.COUNT));
        if (!(count instanceof COSInteger)
                || ((COSInteger) count).longValue() != descendantPageCount) {
            throw failureMode.destinationFailure();
        }
        return descendantPageCount;
    }

    private static final class NameTreeEntry {

        private final COSString key;
        private final COSBase value;

        NameTreeEntry(COSString key, COSBase value) {
            this.key = key;
            this.value = value;
        }
    }

    private static final java.util.Comparator<COSString> NAME_ORDER =
            new java.util.Comparator<COSString>() {
                @Override
                public int compare(COSString left, COSString right) {
                    return compareStringBytes(
                            left.getBytes(),
                            right.getBytes());
                }
            };

    private static int compareStringBytes(byte[] left, byte[] right) {
        int common = Math.min(left.length, right.length);
        for (int index = 0; index < common; index++) {
            int difference = (left[index] & 0xFF) - (right[index] & 0xFF);
            if (difference != 0) {
                return difference;
            }
        }
        return left.length - right.length;
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
            byte[] minimumKey,
            byte[] maximumKey,
            List<NameTreeEntry> entries,
            IdentityHashMap<COSBase, Boolean> visited,
            StructureFailure failureMode,
            long maximumEntries,
            int depth) throws DocumentFailure {
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
            byte[] lower = ((COSString) dereference(
                    ((COSArray) limitsValue).get(0))).getBytes();
            byte[] upper = ((COSString) dereference(
                    ((COSArray) limitsValue).get(1))).getBytes();
            if (compareStringBytes(lower, upper) > 0) {
                throw failureMode.destinationFailure();
            }
            if (minimumKey != null
                    && compareStringBytes(lower, minimumKey) < 0) {
                throw failureMode.destinationFailure();
            }
            if (maximumKey != null
                    && compareStringBytes(upper, maximumKey) > 0) {
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
                COSBase keyValue = dereference(pairs.get(index));
                if (!(keyValue instanceof COSString)) {
                    throw failureMode.destinationFailure();
                }
                COSString key = (COSString) keyValue;
                byte[] keyBytes = key.getBytes();
                if (!entries.isEmpty()
                        && compareStringBytes(
                                entries.get(entries.size() - 1)
                                        .key.getBytes(),
                                keyBytes) >= 0) {
                    throw failureMode.destinationFailure();
                }
                if (minimumKey != null
                        && compareStringBytes(keyBytes, minimumKey) < 0) {
                    throw failureMode.destinationFailure();
                }
                if (maximumKey != null
                        && compareStringBytes(keyBytes, maximumKey) > 0) {
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

    PageDestination destinationFromArray(
            COSBase rawValue,
            IdentityHashMap<COSDictionary, Integer> pageNumbers) {
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
            COSBase pageReference) throws DocumentFailure {
        COSArray array = new COSArray();
        array.setDirect(true);
        array.add(pageReference);
        array.add(COSName.getPDFName(destinationStyleName(
                destination.getStyle())));
        for (java.math.BigDecimal operand : destination.getOperands()) {
            if (operand == null) {
                array.add(COSNull.NULL);
            } else {
                array.add(backendDestinationNumber(operand));
            }
        }
        return array;
    }

    private static COSBase backendDestinationNumber(
            java.math.BigDecimal decimal) throws DocumentFailure {
        if (decimal.scale() <= 0) {
            try {
                return COSInteger.get(decimal.longValueExact());
            } catch (ArithmeticException outsideIntegerRange) {
                // A valid PDF number outside the backend integer range is
                // represented as a lexical real.
            }
        }
        try {
            // COSFloat keeps the lexical form, so the round trip is exact.
            return new COSFloat(decimal.toPlainString());
        } catch (IOException | NumberFormatException invalidNumber) {
            throw invalidDestinationsCommand();
        }
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

    private static java.math.BigDecimal decimalValue(COSBase value) {
        if (value instanceof COSInteger) {
            return java.math.BigDecimal.valueOf(
                    ((COSInteger) value).longValue());
        }
        if (value instanceof COSFloat) {
            try {
                return PdfBoxValueAdapter.serializedNumber((COSFloat) value);
            } catch (IOException | NumberFormatException invalidNumber) {
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
        byte[] packet = command.getXmpPacket();
        requireWellFormedXmpPacket(packet);
        try {
            COSStream metadata = document.getDocument().createCOSStream();
            metadata.setItem(COSName.TYPE, COSName.METADATA);
            metadata.setItem(
                    COSName.SUBTYPE,
                    COSName.getPDFName("XML"));
            try (OutputStream output = metadata.createOutputStream()) {
                output.write(packet);
            }
            document.getDocumentCatalog().getCOSObject().setItem(
                    COSName.METADATA,
                    metadata);
        } catch (IOException streamFailure) {
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The XMP metadata could not be updated safely.");
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
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try (InputStream decoded = ((COSStream) metadata).createInputStream()) {
            int read;
            while ((read = decoded.read(buffer)) != -1) {
                if (read > maximumBytes - packet.size()) {
                    throw failure(
                            DocumentFailureCode.METADATA_LIMIT_EXCEEDED,
                            "The metadata access limit was exceeded.");
                }
                packet.write(buffer, 0, read);
            }
        } catch (IOException | RuntimeException decodeFailure) {
            throw unsafeXmpQuery();
        }
        return packet.toByteArray();
    }

    private static void requireWellFormedXmpPacket(byte[] packet)
            throws DocumentFailure {
        if (packet.length == 0) {
            throw invalidXmpPacket();
        }
        if (packet.length > MAX_METADATA_PACKET_BYTES) {
            throw failure(
                    DocumentFailureCode.COMMAND_REJECTED,
                    "The XMP packet exceeds the supported metadata packet size.");
        }
        String text;
        try {
            text = java.nio.charset.Charset.forName("UTF-8")
                    .newDecoder()
                    .onMalformedInput(
                            java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(
                            java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(packet))
                    .toString();
        } catch (java.nio.charset.CharacterCodingException invalidUtf8) {
            throw invalidXmpPacket();
        }
        if (text.length() > 0 && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        if (!text.contains("<x:xmpmeta")) {
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
            builder.parse(new org.xml.sax.InputSource(
                    new java.io.StringReader(text)));
        } catch (javax.xml.parsers.ParserConfigurationException
                | org.xml.sax.SAXException
                | IOException invalidPacket) {
            throw invalidXmpPacket();
        }
    }

    private void updateInfo(UpdateDocumentInfo update) throws DocumentFailure {
        for (Map.Entry<String, PdfValue> entry : update.getEntries().entrySet()) {
            requireValidInfoName(entry.getKey());
            requireInfoCommandValue(entry.getValue());
        }
        for (String removedName : update.getRemovedNames()) {
            requireValidInfoName(removedName);
        }
        if (update.getEntries().isEmpty() && update.getRemovedNames().isEmpty()) {
            return;
        }

        COSDictionary info = writableInfo();
        for (String removedName : update.getRemovedNames()) {
            info.removeItem(COSName.getPDFName(removedName));
        }
        for (Map.Entry<String, PdfValue> entry : update.getEntries().entrySet()) {
            info.setItem(
                    COSName.getPDFName(entry.getKey()),
                    backendScalarValue(entry.getValue()));
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
        try {
            return detachedInfoDictionary(
                    (COSDictionary) infoValue,
                    new IdentityHashMap<COSBase, Boolean>(),
                    0);
        } catch (RuntimeException conversionFailure) {
            throw unsafeInfoQuery();
        }
    }

    private PdfDictionary detachedInfoDictionary(
            COSDictionary dictionary,
            IdentityHashMap<COSBase, Boolean> visited,
            int depth) {
        if (visited.put(dictionary, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("cycle");
        }
        PdfDictionary.Builder detached = PdfDictionary.builder();
        for (Map.Entry<COSName, COSBase> entry : dictionary.entrySet()) {
            PdfValue value = detachedInfoValue(
                    entry.getValue(),
                    visited,
                    depth);
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
            int depth) {
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
                        (COSFloat) value));
            } catch (IOException | NumberFormatException invalidNumber) {
                return null;
            }
        }
        if (value instanceof COSString) {
            return PdfString.of(((COSString) value).getBytes());
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
                        depth + 1);
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
            return detachedInfoDictionary(nested, visited, depth + 1);
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
    private static void requireMetadataSafeGraph(COSDictionary info)
            throws DocumentFailure {
        requireMetadataSafeValue(
                info,
                new IdentityHashMap<COSBase, Boolean>(),
                0);
    }

    private static void requireMetadataSafeValue(
            COSBase rawValue,
            IdentityHashMap<COSBase, Boolean> visited,
            int depth) throws DocumentFailure {
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

    private static void requireInfoCommandValue(PdfValue value)
            throws DocumentFailure {
        if (value instanceof PdfStream
                || value instanceof PdfIndirectReference) {
            throw invalidInfoValue();
        }
        if (value instanceof PdfArray) {
            PdfArray array = (PdfArray) value;
            for (int index = 0; index < array.size(); index++) {
                requireInfoCommandValue(array.get(index));
            }
            return;
        }
        if (value instanceof PdfDictionary) {
            PdfDictionary dictionary = (PdfDictionary) value;
            for (int index = 0; index < dictionary.size(); index++) {
                requireInfoCommandValue(dictionary.getEntry(index).getValue());
            }
        }
    }

    private COSBase backendScalarValue(PdfValue value) throws DocumentFailure {
        if (value == PdfNull.INSTANCE) {
            return COSNull.NULL;
        }
        if (value instanceof PdfBoolean) {
            return COSBoolean.getBoolean(((PdfBoolean) value).booleanValue());
        }
        if (value instanceof PdfNumber) {
            return backendNumber((PdfNumber) value);
        }
        if (value instanceof PdfString) {
            return new COSString(((PdfString) value).getBytes());
        }
        if (value instanceof PdfName) {
            return COSName.getPDFName(((PdfName) value).getValue());
        }
        if (value instanceof PdfArray) {
            PdfArray array = (PdfArray) value;
            COSArray converted = new COSArray();
            converted.setDirect(true);
            for (int index = 0; index < array.size(); index++) {
                converted.add(backendScalarValue(array.get(index)));
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
                        backendScalarValue(entry.getValue()));
            }
            return converted;
        }
        throw invalidInfoValue();
    }

    private static COSBase backendNumber(PdfNumber number)
            throws DocumentFailure {
        java.math.BigDecimal decimal = number.decimalValue();
        if (decimal.scale() <= 0) {
            try {
                return COSInteger.get(decimal.longValueExact());
            } catch (ArithmeticException outsideIntegerRange) {
                // A valid PDF number outside the backend integer range is
                // represented as a lexical real.
            }
        }
        try {
            // COSFloat keeps the lexical form, so the round trip is exact.
            return new COSFloat(decimal.toPlainString());
        } catch (IOException | NumberFormatException invalidNumber) {
            throw invalidInfoValue();
        }
    }

    private static COSBase dereference(COSBase value) {
        return value instanceof COSObject
                ? ((COSObject) value).getObject()
                : value;
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
