package net.zerocloud.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import net.zerocloud.pdf.command.CopyPages;
import net.zerocloud.pdf.command.InsertBlankPage;
import net.zerocloud.pdf.command.MergeDocuments;
import net.zerocloud.pdf.command.MovePages;
import net.zerocloud.pdf.command.RemovePages;
import net.zerocloud.pdf.command.SplitDocument;
import net.zerocloud.pdf.query.PageObjectReference;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;

/**
 * Owns the PDFBox-specific mechanics behind the T10 page command seam.
 */
final class PdfBoxPageOperations {

    static final String CAPABILITY_ID =
            "document.page.manipulate-merge-split";

    private final PDDocument document;
    private final Map<String, DocumentSource> sources;
    private final String primarySourceName;
    private final Map<String, PublicationTarget> publicationTargets;
    private final PdfBoxValueAdapter valueAdapter;
    private final PdfBoxMetadataOperations metadataOperations;
    private final PdfBoxAnnotationPageOperations annotationOperations;
    private Map<String, PDDocument> splitDocuments;

    PdfBoxPageOperations(
            PDDocument document,
            Map<String, DocumentSource> sources,
            String primarySourceName,
            Map<String, PublicationTarget> publicationTargets,
            PdfBoxValueAdapter valueAdapter,
            PdfBoxMetadataOperations metadataOperations,
            PdfBoxAnnotationPageOperations annotationOperations) {
        this.document = Objects.requireNonNull(document, "document");
        this.sources = Objects.requireNonNull(sources, "sources");
        this.primarySourceName = primarySourceName;
        this.publicationTargets = Objects.requireNonNull(
                publicationTargets,
                "publicationTargets");
        this.valueAdapter = Objects.requireNonNull(valueAdapter, "valueAdapter");
        this.metadataOperations = Objects.requireNonNull(
                metadataOperations,
                "metadataOperations");
        this.annotationOperations = Objects.requireNonNull(
                annotationOperations,
                "annotationOperations");
        if (primarySourceName == null) {
            makeLibraryOwnedPageTreeIndirect(document);
        }
    }

    boolean supports(DocumentCommand command) {
        return command instanceof InsertBlankPage
                || command instanceof RemovePages
                || command instanceof MovePages
                || command instanceof CopyPages
                || command instanceof MergeDocuments
                || command instanceof SplitDocument;
    }

    void execute(DocumentCommand command) throws DocumentFailure {
        if (!supports(command)) {
            throw new IllegalArgumentException("Unsupported page command.");
        }
        try {
            if (command instanceof InsertBlankPage) {
                insert((InsertBlankPage) command);
            } else if (command instanceof RemovePages) {
                remove((RemovePages) command);
            } else if (command instanceof MovePages) {
                move((MovePages) command);
            } else if (command instanceof CopyPages) {
                copy((CopyPages) command);
            } else if (command instanceof MergeDocuments) {
                merge((MergeDocuments) command);
            } else {
                split((SplitDocument) command);
            }
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (CallerSourceRuntimeFailure callerFailure) {
            throw callerFailure.getCallerFailure();
        } catch (RuntimeException backendFailure) {
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The page operation could not be completed safely.");
        }
    }

    ObjectReference pageReference(PageObjectReference query)
            throws DocumentFailure {
        try {
            int pageNumber = query.getPageNumber();
            if (pageNumber < 1) {
                throw invalidPageRange();
            }
            PageReferenceLookup lookup = new PageReferenceLookup(pageNumber);
            COSBase catalogValue = dereference(
                    document.getDocument().getTrailer().getItem(COSName.ROOT));
            if (!(catalogValue instanceof COSDictionary)) {
                throw invalidPageTreeForQuery();
            }
            collectPageReferences(
                    ((COSDictionary) catalogValue).getItem(COSName.PAGES),
                    null,
                    lookup,
                    new IdentityHashMap<COSDictionary, Boolean>());
            if (lookup.getReference() == null) {
                throw invalidPageRange();
            }
            return valueAdapter.pageReference(lookup.getReference());
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (RuntimeException backendFailure) {
            throw failure(
                    DocumentFailureCode.QUERY_FAILED,
                    "The page Object Reference could not be evaluated.");
        }
    }

    Map<String, PDDocument> getSplitDocuments() {
        return splitDocuments;
    }

    void requireCommandAllowed() throws DocumentFailure {
        if (splitDocuments != null) {
            throw failure(
                    DocumentFailureCode.COMMAND_REJECTED,
                    "A successful split must be the final Document Command in its workflow.");
        }
    }

    void makeLibraryOwnedPageIndirect(PDPage page) {
        COSDictionary pageDictionary = page.getCOSObject();
        COSBase parentValue = dereference(
                pageDictionary.getItem(COSName.PARENT));
        if (!(parentValue instanceof COSDictionary)) {
            throw new IllegalStateException(
                    "A library-owned page must belong to the page tree.");
        }
        COSBase kidsValue = dereference(
                ((COSDictionary) parentValue).getItem(COSName.KIDS));
        if (!(kidsValue instanceof COSArray)) {
            throw new IllegalStateException(
                    "A library-owned page parent must have children.");
        }
        COSArray kids = (COSArray) kidsValue;
        for (int index = 0; index < kids.size(); index++) {
            COSBase rawKid = kids.get(index);
            if (dereference(rawKid) == pageDictionary) {
                if (!(rawKid instanceof COSObject)) {
                    kids.set(index, new COSObject(pageDictionary));
                }
                return;
            }
        }
        throw new IllegalStateException(
                "A library-owned page must be present in its parent.");
    }

    private static void makeLibraryOwnedPageTreeIndirect(
            PDDocument generatedDocument) {
        COSDictionary catalog = generatedDocument.getDocumentCatalog()
                .getCOSObject();
        COSBase rawRoot = catalog.getItem(COSName.PAGES);
        COSBase rootValue = dereference(rawRoot);
        if (!(rootValue instanceof COSDictionary)) {
            throw new IllegalStateException(
                    "A library-owned document must have a page tree.");
        }
        if (!(rawRoot instanceof COSObject)) {
            catalog.setItem(COSName.PAGES, new COSObject(rootValue));
        }
        makeLibraryOwnedPageTreeChildrenIndirect(
                (COSDictionary) rootValue,
                new IdentityHashMap<COSDictionary, Boolean>());
    }

    private static void makeLibraryOwnedPageTreeChildrenIndirect(
            COSDictionary node,
            IdentityHashMap<COSDictionary, Boolean> visited) {
        if (visited.put(node, Boolean.TRUE) != null) {
            throw new IllegalStateException(
                    "A library-owned page tree must be acyclic.");
        }
        COSBase kidsValue = dereference(node.getItem(COSName.KIDS));
        if (!(kidsValue instanceof COSArray)) {
            throw new IllegalStateException(
                    "A library-owned page tree node must have children.");
        }
        COSArray kids = (COSArray) kidsValue;
        for (int index = 0; index < kids.size(); index++) {
            COSBase rawKid = kids.get(index);
            COSBase kidValue = dereference(rawKid);
            if (!(kidValue instanceof COSDictionary)) {
                throw new IllegalStateException(
                        "A library-owned page tree child must be a dictionary.");
            }
            if (!(rawKid instanceof COSObject)) {
                kids.set(index, new COSObject(kidValue));
            }
            COSDictionary kid = (COSDictionary) kidValue;
            if (COSName.PAGES.equals(dereference(
                    kid.getItem(COSName.TYPE)))) {
                makeLibraryOwnedPageTreeChildrenIndirect(kid, visited);
            }
        }
    }

    private void insert(InsertBlankPage insertion) throws DocumentFailure {
        int pageNumber = insertion.getPageNumber();
        requirePosition(pageNumber, document.getNumberOfPages() + 1);
        requirePreservable(document);
        try {
            PDPage page = new PDPage();
            page.setResources(new PDResources());
            if (pageNumber == document.getNumberOfPages() + 1) {
                document.addPage(page);
            } else {
                document.getPages().insertBefore(
                        page,
                        document.getPage(pageNumber - 1));
            }
            makeLibraryOwnedPageIndirect(page);
        } catch (RuntimeException backendFailure) {
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The blank page could not be inserted.");
        }
    }

    private void remove(RemovePages removal) throws DocumentFailure {
        PageRange range = removal.getRange();
        requireRange(range);
        requirePreservable(document);
        java.util.Set<Integer> removed = new java.util.HashSet<Integer>();
        for (int pageNumber = range.getFirstPageNumber();
                pageNumber <= range.getLastPageNumber();
                pageNumber++) {
            removed.add(Integer.valueOf(pageNumber - 1));
        }
        metadataOperations.requireNoDestinationConflict(document, removed);
        annotationOperations.requireNoDestinationConflict(document, removed);
        try {
            for (int pageNumber = range.getLastPageNumber();
                    pageNumber >= range.getFirstPageNumber();
                    pageNumber--) {
                document.removePage(pageNumber - 1);
            }
        } catch (RuntimeException backendFailure) {
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The pages could not be removed.");
        }
    }

    private void move(MovePages movement) throws DocumentFailure {
        PageRange range = movement.getRange();
        requireRange(range);
        int selectedPageCount = range.getLastPageNumber()
                - range.getFirstPageNumber() + 1;
        requirePosition(
                movement.getDestinationPageNumber(),
                document.getNumberOfPages() - selectedPageCount + 1);
        requirePreservable(document);
        try {
            List<PDPage> selected = new ArrayList<PDPage>(selectedPageCount);
            for (int pageNumber = range.getFirstPageNumber();
                    pageNumber <= range.getLastPageNumber();
                    pageNumber++) {
                PDPage page = document.getPage(pageNumber - 1);
                materializeInheritedPageAttributes(page);
                selected.add(page);
            }
            for (int pageNumber = range.getLastPageNumber();
                    pageNumber >= range.getFirstPageNumber();
                    pageNumber--) {
                document.removePage(pageNumber - 1);
            }
            int destination = movement.getDestinationPageNumber();
            for (PDPage page : selected) {
                if (destination == document.getNumberOfPages() + 1) {
                    document.addPage(page);
                } else {
                    document.getPages().insertBefore(
                            page,
                            document.getPage(destination - 1));
                }
                makeLibraryOwnedPageIndirect(page);
                destination++;
            }
        } catch (RuntimeException backendFailure) {
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The pages could not be moved.");
        }
    }

    private void copy(CopyPages copy) throws DocumentFailure {
        PageRange range = copy.getRange();
        requireRange(range);
        requirePosition(
                copy.getInsertionPageNumber(),
                document.getNumberOfPages() + 1);
        requirePreservable(document);
        PdfBoxAnnotationPageOperations.CopyStructures annotationStructures =
                annotationOperations.snapshotCopyStructures(document, range);
        boolean hadInfo = document.getDocument().getTrailer()
                .getItem(COSName.INFO) != null;
        List<PDDocument> copiedDocuments = null;
        try {
            int originalPageCount = document.getNumberOfPages();
            int copiedPageCount = range.getLastPageNumber()
                    - range.getFirstPageNumber() + 1;
            Splitter splitter = splitter(range);
            copiedDocuments = splitter.split(document);
            new PDFMergerUtility().appendDocument(
                    document,
                    copiedDocuments.get(0));

            int insertion = copy.getInsertionPageNumber();
            List<PDPage> copiedPages = new ArrayList<PDPage>(copiedPageCount);
            for (int index = 0; index < copiedPageCount; index++) {
                PDPage copiedPage = document.getPage(originalPageCount + index);
                copiedPages.add(copiedPage);
            }
            for (int index = 0; index < copiedPageCount; index++) {
                document.removePage(originalPageCount);
            }
            for (PDPage page : copiedPages) {
                if (insertion == document.getNumberOfPages() + 1) {
                    document.addPage(page);
                } else {
                    document.getPages().insertBefore(
                            page,
                            document.getPage(insertion - 1));
                }
                insertion++;
            }
            for (PDPage copiedPage : copiedPages) {
                makeLibraryOwnedPageIndirect(copiedPage);
            }
            repairPageParentReferences(document);
            annotationOperations.applyCopiedStructures(
                    annotationStructures,
                    copy.getInsertionPageNumber(),
                    originalPageCount);
            if (!hadInfo) {
                removeBackendCreatedInfo(document);
            }
        } catch (DocumentFailure structureFailure) {
            closeQuietly(copiedDocuments);
            throw structureFailure;
        } catch (IOException | RuntimeException backendFailure) {
            closeQuietly(copiedDocuments);
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The pages could not be copied.");
        }
        closeForSuccess(copiedDocuments);
    }

    private static void repairPageParentReferences(PDDocument candidate)
            throws DocumentFailure {
        COSBase rawRoot = candidate.getDocumentCatalog()
                .getCOSObject().getItem(COSName.PAGES);
        repairPageParentReferences(
                rawRoot,
                new IdentityHashMap<COSDictionary, Boolean>());
    }

    private static void repairPageParentReferences(
            COSBase rawNode,
            IdentityHashMap<COSDictionary, Boolean> visited)
            throws DocumentFailure {
        if (!(rawNode instanceof COSObject)) {
            throw preservationUnsupported();
        }
        COSBase nodeValue = dereference(rawNode);
        if (!(nodeValue instanceof COSDictionary)) {
            throw preservationUnsupported();
        }
        COSDictionary node = (COSDictionary) nodeValue;
        if (visited.put(node, Boolean.TRUE) != null) {
            throw preservationUnsupported();
        }
        COSBase kidsValue = dereference(node.getItem(COSName.KIDS));
        if (!(kidsValue instanceof COSArray)) {
            throw preservationUnsupported();
        }
        COSArray kids = (COSArray) kidsValue;
        for (int index = 0; index < kids.size(); index++) {
            COSBase rawChild = kids.get(index);
            COSBase childValue = dereference(rawChild);
            if (!(rawChild instanceof COSObject)
                    || !(childValue instanceof COSDictionary)) {
                throw preservationUnsupported();
            }
            COSDictionary child = (COSDictionary) childValue;
            child.setItem(COSName.PARENT, rawNode);
            if (COSName.PAGES.equals(
                    dereference(child.getItem(COSName.TYPE)))) {
                repairPageParentReferences(rawChild, visited);
            }
        }
    }

    private void merge(MergeDocuments merge) throws DocumentFailure {
        LinkedHashSet<String> selectedSources = new LinkedHashSet<String>();
        for (String sourceName : merge.getSourceNames()) {
            if (!selectedSources.add(sourceName)
                    || !sources.containsKey(sourceName)
                    || sourceName.equals(primarySourceName)) {
                throw invalidMergeSources();
            }
        }
        if (selectedSources.isEmpty()) {
            throw invalidMergeSources();
        }
        requirePreservable(document);
        boolean primaryHadInfo = document.getDocument().getTrailer()
                .getItem(COSName.INFO) != null;

        List<PDDocument> mergeDocuments = new ArrayList<PDDocument>();
        try {
            PDDocument combinedSources = new PDDocument();
            mergeDocuments.add(combinedSources);
            PDFMergerUtility merger = new PDFMergerUtility();
            List<PdfBoxMetadataOperations.MergedStructures> structures =
                    new ArrayList<PdfBoxMetadataOperations.MergedStructures>();
            List<PdfBoxAnnotationPageOperations.MergeStructures>
                    annotationStructures =
                    new ArrayList<
                            PdfBoxAnnotationPageOperations.MergeStructures>();
            for (String sourceName : merge.getSourceNames()) {
                PDDocument sourceDocument = openAdditionalSource(
                        sources.get(sourceName));
                mergeDocuments.add(sourceDocument);
                requirePreservable(sourceDocument);
                annotationStructures.add(
                        annotationOperations.extractAndStripMergeStructures(
                                sourceDocument));
                structures.add(
                        metadataOperations.extractAndStripManagedStructures(
                                sourceDocument));
                merger.appendDocument(combinedSources, sourceDocument);
            }
            annotationOperations.requireMergeIdentifiersSafe(
                    annotationStructures);
            int originalPageCount = document.getNumberOfPages();
            merger.appendDocument(document, combinedSources);
            for (int index = originalPageCount;
                    index < document.getNumberOfPages();
                    index++) {
                makeLibraryOwnedPageIndirect(document.getPage(index));
            }
            repairPageParentReferences(document);
            List<Map<String, String>> destinationRenames =
                    metadataOperations.applyMergedStructures(
                            document,
                            structures,
                            primaryHadInfo);
            annotationOperations.applyMergedStructures(
                    annotationStructures,
                    originalPageCount,
                    destinationRenames);
        } catch (DocumentFailure sourceFailure) {
            closeQuietly(mergeDocuments);
            throw failure(
                    sourceFailure.getCode(),
                    sourceFailure.getDiagnostic());
        } catch (CallerSourceRuntimeFailure callerFailure) {
            closeQuietly(mergeDocuments);
            throw callerFailure;
        } catch (IOException | RuntimeException backendFailure) {
            closeQuietly(mergeDocuments);
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The named Sources could not be merged safely.");
        }
        closeForSuccess(mergeDocuments);
    }

    private static PDDocument openAdditionalSource(DocumentSource source)
            throws DocumentFailure {
        try {
            return PdfBoxWorkflowEngine.openDocument(source);
        } catch (RuntimeException callerFailure) {
            if (source.getKind() == DocumentSource.Kind.STREAM
                    || source.getKind() == DocumentSource.Kind.CHANNEL) {
                throw new CallerSourceRuntimeFailure(callerFailure);
            }
            throw callerFailure;
        }
    }

    private void split(SplitDocument split) throws DocumentFailure {
        if (splitDocuments != null
                || split.getTargetDeclarationCount()
                        != split.getTargetRanges().size()
                || !split.getTargetRanges().keySet().equals(
                        publicationTargets.keySet())) {
            throw failure(
                    DocumentFailureCode.SPLIT_TARGET_INVALID,
                    "The split command must define every publication Target once.");
        }
        for (PageRange range : split.getTargetRanges().values()) {
            requireRange(range);
        }
        requirePreservable(document);
        boolean hadInfo = document.getDocument().getTrailer()
                .getItem(COSName.INFO) != null;

        Map<String, PDDocument> created =
                new LinkedHashMap<String, PDDocument>();
        try {
            PdfBoxMetadataOperations.MergedStructures snapshot =
                    metadataOperations.snapshotManagedStructures(document);
            PdfBoxAnnotationPageOperations.MergeStructures
                    annotationSnapshot =
                    annotationOperations.snapshotSplitStructures(document);
            for (Map.Entry<String, PageRange> product
                    : split.getTargetRanges().entrySet()) {
                List<PDDocument> documents = splitter(product.getValue())
                        .split(document);
                PDDocument productDocument = documents.get(0);
                created.put(product.getKey(), productDocument);
                makeLibraryOwnedPageTreeIndirect(productDocument);
                repairPageParentReferences(productDocument);
                int[] mapping = splitMapping(
                        document.getNumberOfPages(),
                        product.getValue());
                metadataOperations.retargetSplitStructures(
                        productDocument,
                        snapshot,
                        mapping);
                annotationOperations.retargetSplitStructures(
                        productDocument,
                        annotationSnapshot,
                        mapping);
            }
            if (!hadInfo) {
                removeBackendCreatedInfo(document);
            }
            splitDocuments = created;
        } catch (DocumentFailure splitFailure) {
            closeQuietly(new ArrayList<PDDocument>(created.values()));
            throw failure(
                    splitFailure.getCode(),
                    splitFailure.getDiagnostic());
        } catch (IOException | RuntimeException backendFailure) {
            closeQuietly(new ArrayList<PDDocument>(created.values()));
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The split products could not be created safely.");
        }
    }

    private static int[] splitMapping(int pageCount, PageRange range) {
        int[] mapping = new int[pageCount];
        int position = 0;
        for (int pageNumber = range.getFirstPageNumber();
                pageNumber <= range.getLastPageNumber();
                pageNumber++) {
            mapping[pageNumber - 1] = ++position;
        }
        return mapping;
    }

    private static void removeBackendCreatedInfo(PDDocument candidate) {
        COSBase infoValue = dereference(
                candidate.getDocument().getTrailer().getItem(COSName.INFO));
        if (infoValue instanceof COSDictionary
                && ((COSDictionary) infoValue).size() == 0) {
            // The backend's Splitter and merger attach an empty information
            // dictionary to documents that declared none; drop it again so
            // the trailer survives the page operation unchanged.
            candidate.getDocument().getTrailer().removeItem(COSName.INFO);
        }
    }

    private void requireRange(PageRange range) throws DocumentFailure {
        if (range.getFirstPageNumber() < 1
                || range.getLastPageNumber() < range.getFirstPageNumber()
                || range.getLastPageNumber() > document.getNumberOfPages()) {
            throw failure(
                    DocumentFailureCode.PAGE_RANGE_INVALID,
                    "The page range is outside the current document.");
        }
    }

    private static void requirePosition(int pageNumber, int maximum)
            throws DocumentFailure {
        if (pageNumber < 1 || pageNumber > maximum) {
            throw failure(
                    DocumentFailureCode.PAGE_POSITION_INVALID,
                    "The page position is outside the current document.");
        }
    }

    private static Splitter splitter(PageRange range) {
        Splitter splitter = new Splitter();
        splitter.setStartPage(range.getFirstPageNumber());
        splitter.setEndPage(range.getLastPageNumber());
        splitter.setSplitAtPage(
                range.getLastPageNumber() - range.getFirstPageNumber() + 1);
        return splitter;
    }

    private long collectPageReferences(
            COSBase rawNode,
            COSDictionary expectedParent,
            PageReferenceLookup lookup,
            IdentityHashMap<COSDictionary, Boolean> visited)
            throws DocumentFailure {
        if (!(rawNode instanceof COSObject)) {
            throw invalidPageTreeForQuery();
        }
        COSBase nodeValue = dereference(rawNode);
        if (!(nodeValue instanceof COSDictionary)) {
            throw invalidPageTreeForQuery();
        }
        COSDictionary node = (COSDictionary) nodeValue;
        if (visited.put(node, Boolean.TRUE) != null
                || !COSName.PAGES.equals(dereference(
                        node.getItem(COSName.TYPE)))) {
            throw invalidPageTreeForQuery();
        }
        COSBase actualParent = dereference(node.getItem(COSName.PARENT));
        if (expectedParent == null
                ? actualParent != null
                : actualParent != expectedParent) {
            throw invalidPageTreeForQuery();
        }
        COSBase kidsValue = dereference(node.getItem(COSName.KIDS));
        if (!(kidsValue instanceof COSArray)) {
            throw invalidPageTreeForQuery();
        }
        COSArray kids = (COSArray) kidsValue;
        long descendantPageCount = 0L;
        for (int index = 0; index < kids.size(); index++) {
            COSBase rawChild = kids.get(index);
            COSBase childValue = dereference(rawChild);
            if (!(childValue instanceof COSDictionary)) {
                throw invalidPageTreeForQuery();
            }
            COSDictionary child = (COSDictionary) childValue;
            COSBase type = dereference(child.getItem(COSName.TYPE));
            if (COSName.PAGES.equals(type)) {
                descendantPageCount += collectPageReferences(
                        rawChild,
                        node,
                        lookup,
                        visited);
            } else if (COSName.PAGE.equals(type)) {
                if (!(rawChild instanceof COSObject)) {
                    throw invalidPageTreeForQuery();
                }
                if (visited.put(child, Boolean.TRUE) != null
                        || dereference(child.getItem(COSName.PARENT)) != node) {
                    throw invalidPageTreeForQuery();
                }
                lookup.add(rawChild);
                descendantPageCount++;
            } else {
                throw invalidPageTreeForQuery();
            }
        }
        COSBase count = dereference(node.getItem(COSName.COUNT));
        if (!(count instanceof COSInteger)
                || ((COSInteger) count).longValue() != descendantPageCount) {
            throw invalidPageTreeForQuery();
        }
        return descendantPageCount;
    }

    private static void materializeInheritedPageAttributes(PDPage page) {
        page.setMediaBox(page.getMediaBox());
        page.setCropBox(page.getCropBox());
        page.setRotation(page.getRotation());
        PDResources resources = page.getResources();
        if (resources != null) {
            page.setResources(resources);
        }
    }

    private void requirePreservable(PDDocument candidate)
            throws DocumentFailure {
        requireSafeTrailer(candidate);
        COSDictionary catalog = candidate.getDocumentCatalog().getCOSObject();
        for (COSName name : catalog.keySet()) {
            if (!COSName.TYPE.equals(name)
                    && !COSName.PAGES.equals(name)
                    && !COSName.VERSION.equals(name)
                    && !PdfBoxMetadataOperations.isManagedCatalogEntry(name)
                    && !PdfBoxAnnotationOperations.isManagedCatalogEntry(name)) {
                throw preservationUnsupported();
            }
        }
        try {
            metadataOperations.requireSafeCatalogStructures(candidate);
            metadataOperations.requireSafeInfoPreservable(candidate);
            annotationOperations.requireSafeActionStructures(candidate);
        } catch (DocumentFailure metadataStructure) {
            throw preservationUnsupported();
        }
        requireSafePageTree(
                catalog.getItem(COSName.PAGES),
                null,
                new IdentityHashMap<COSDictionary, Boolean>());
        PdfBoxAnnotationDecodePolicy.Budgets annotationBudgets =
                PdfBoxAnnotationDecodePolicy.newManagedGraphPass();
        for (PDPage page : candidate.getPages()) {
            COSDictionary dictionary = page.getCOSObject();
            requireSafePage(dictionary);
            try {
                annotationOperations.requireSafeAnnotations(
                        candidate,
                        dictionary,
                        dictionary.getItem(COSName.ANNOTS),
                        annotationBudgets);
            } catch (DocumentFailure unsafeAnnotations) {
                throw preservationUnsupported();
            }
        }
    }

    private static void requireSafeTrailer(PDDocument candidate)
            throws DocumentFailure {
        COSDictionary trailer = candidate.getDocument().getTrailer();
        if (trailer == null) {
            return;
        }
        for (COSName name : trailer.keySet()) {
            if (!COSName.INFO.equals(name)
                    && !isOneOf(
                            name,
                            "Size",
                            "Prev",
                            "Root",
                            "ID",
                            "XRefStm",
                            "Type",
                            "Index",
                            "W",
                            "Length",
                            "Filter",
                            "DecodeParms",
                            "F",
                            "FFilter",
                            "FDecodeParms",
                            "DL")) {
                throw preservationUnsupported();
            }
        }
    }

    private static void requireSafePage(COSDictionary page)
            throws DocumentFailure {
        for (COSName name : page.keySet()) {
            if (isOneOf(
                    name,
                    "Type",
                    "Parent",
                    "Resources",
                    "MediaBox",
                    "CropBox",
                    "BleedBox",
                    "TrimBox",
                    "ArtBox",
                    "Rotate",
                    "Contents",
                    "Annots",
                    "AA")) {
                continue;
            }
            if (isOneOf(
                    name,
                    "B",
                    "StructParents",
                    "StructParent",
                    "SeparationInfo",
                    "Metadata",
                    "PieceInfo",
                    "LastModified",
                    "PresSteps",
                    "VP",
                    "AF",
                    "Tabs",
                    "TemplateInstantiated",
                    "DPart",
                    "OutputIntents",
                    "Thumb")
                    || !isSafeInlineExtension(
                            page.getItem(name),
                            new IdentityHashMap<COSBase, Boolean>())) {
                throw preservationUnsupported();
            }
        }
        requireSafeInheritablePageAttributes(page);
        requireSafeRectangle(page, COSName.BLEED_BOX);
        requireSafeRectangle(page, COSName.TRIM_BOX);
        requireSafeRectangle(page, COSName.ART_BOX);
        requireEffectiveMediaBox(page);
        requireSafeContents(page.getItem(COSName.CONTENTS));
    }

    private static long requireSafePageTree(
            COSBase rawNode,
            COSDictionary expectedParent,
            IdentityHashMap<COSDictionary, Boolean> visited)
            throws DocumentFailure {
        if (!(rawNode instanceof COSObject)) {
            throw preservationUnsupported();
        }
        COSBase nodeValue = dereference(rawNode);
        if (!(nodeValue instanceof COSDictionary)) {
            throw preservationUnsupported();
        }
        COSDictionary node = (COSDictionary) nodeValue;
        if (visited.put(node, Boolean.TRUE) != null) {
            throw preservationUnsupported();
        }
        if (!COSName.PAGES.equals(dereference(node.getItem(COSName.TYPE)))) {
            throw preservationUnsupported();
        }
        COSBase actualParent = dereference(node.getItem(COSName.PARENT));
        if (expectedParent == null
                ? actualParent != null
                : actualParent != expectedParent) {
            throw preservationUnsupported();
        }
        for (COSName name : node.keySet()) {
            if (!isOneOf(
                    name,
                    "Type",
                    "Parent",
                    "Kids",
                    "Count",
                    "Resources",
                    "MediaBox",
                    "CropBox",
                    "Rotate")) {
                throw preservationUnsupported();
            }
        }
        requireSafeInheritablePageAttributes(node);
        COSBase kidsValue = dereference(node.getItem(COSName.KIDS));
        if (!(kidsValue instanceof COSArray)) {
            throw preservationUnsupported();
        }
        COSArray kids = (COSArray) kidsValue;
        long descendantPageCount = 0L;
        for (int index = 0; index < kids.size(); index++) {
            COSBase rawChild = kids.get(index);
            COSBase childValue = dereference(rawChild);
            if (!(childValue instanceof COSDictionary)) {
                throw preservationUnsupported();
            }
            COSDictionary child = (COSDictionary) childValue;
            COSBase type = dereference(child.getItem(COSName.TYPE));
            if (COSName.PAGES.equals(type)) {
                descendantPageCount += requireSafePageTree(
                        rawChild,
                        node,
                        visited);
            } else if (COSName.PAGE.equals(type)) {
                if (!(rawChild instanceof COSObject)) {
                    throw preservationUnsupported();
                }
                if (visited.put(child, Boolean.TRUE) != null
                        || dereference(child.getItem(COSName.PARENT)) != node) {
                    throw preservationUnsupported();
                }
                descendantPageCount++;
            } else {
                throw preservationUnsupported();
            }
        }
        COSBase count = dereference(node.getItem(COSName.COUNT));
        if (!(count instanceof COSInteger)
                || ((COSInteger) count).longValue() != descendantPageCount) {
            throw preservationUnsupported();
        }
        return descendantPageCount;
    }

    private static void requireSafeInheritablePageAttributes(
            COSDictionary dictionary) throws DocumentFailure {
        requireSafeRectangle(dictionary, COSName.MEDIA_BOX);
        requireSafeRectangle(dictionary, COSName.CROP_BOX);
        requireSafeRotation(dictionary);
        requireSafeResources(dictionary);
    }

    private static void requireSafeRectangle(
            COSDictionary dictionary,
            COSName name) throws DocumentFailure {
        COSBase rawRectangle = dictionary.getItem(name);
        if (rawRectangle == null) {
            return;
        }
        COSBase rectangleValue = dereference(rawRectangle);
        if (!(rectangleValue instanceof COSArray)) {
            throw preservationUnsupported();
        }
        COSArray rectangle = (COSArray) rectangleValue;
        if (rectangle.size() != 4) {
            throw preservationUnsupported();
        }
        for (int index = 0; index < rectangle.size(); index++) {
            if (!(dereference(rectangle.get(index)) instanceof COSNumber)) {
                throw preservationUnsupported();
            }
        }
    }

    private static void requireSafeRotation(COSDictionary dictionary)
            throws DocumentFailure {
        COSBase rawRotation = dictionary.getItem(COSName.ROTATE);
        if (rawRotation == null) {
            return;
        }
        COSBase rotationValue = dereference(rawRotation);
        if (!(rotationValue instanceof COSInteger)) {
            throw preservationUnsupported();
        }
        long rotation = ((COSInteger) rotationValue).longValue();
        if (rotation < Integer.MIN_VALUE
                || rotation > Integer.MAX_VALUE
                || rotation % 90L != 0L) {
            throw preservationUnsupported();
        }
    }

    private static void requireSafeResources(COSDictionary dictionary)
            throws DocumentFailure {
        COSBase rawResources = dictionary.getItem(COSName.RESOURCES);
        if (rawResources == null) {
            return;
        }
        COSBase resources = dereference(rawResources);
        if (!(resources instanceof COSDictionary)) {
            throw preservationUnsupported();
        }
        requireResourceGraphDetachedFromPageTree(
                resources,
                new IdentityHashMap<COSBase, Boolean>());
    }

    private static void requireResourceGraphDetachedFromPageTree(
            COSBase rawValue,
            IdentityHashMap<COSBase, Boolean> visited)
            throws DocumentFailure {
        COSBase value = dereference(rawValue);
        if (value == null) {
            throw preservationUnsupported();
        }
        if (visited.put(value, Boolean.TRUE) != null) {
            return;
        }
        if (value instanceof COSArray) {
            COSArray array = (COSArray) value;
            for (int index = 0; index < array.size(); index++) {
                requireResourceGraphDetachedFromPageTree(
                        array.get(index),
                        visited);
            }
            return;
        }
        if (!(value instanceof COSDictionary)) {
            return;
        }
        COSDictionary resource = (COSDictionary) value;
        COSBase type = dereference(resource.getItem(COSName.TYPE));
        if (COSName.PAGE.equals(type) || COSName.PAGES.equals(type)) {
            throw preservationUnsupported();
        }
        for (COSBase entry : resource.getValues()) {
            requireResourceGraphDetachedFromPageTree(entry, visited);
        }
    }

    private static void requireEffectiveMediaBox(COSDictionary page)
            throws DocumentFailure {
        COSDictionary current = page;
        IdentityHashMap<COSDictionary, Boolean> visited =
                new IdentityHashMap<COSDictionary, Boolean>();
        while (visited.put(current, Boolean.TRUE) == null) {
            if (current.getItem(COSName.MEDIA_BOX) != null) {
                return;
            }
            COSBase parentValue = dereference(
                    current.getItem(COSName.PARENT));
            if (!(parentValue instanceof COSDictionary)) {
                break;
            }
            current = (COSDictionary) parentValue;
        }
        throw preservationUnsupported();
    }

    private static void requireSafeContents(COSBase rawContents)
            throws DocumentFailure {
        if (rawContents == null) {
            return;
        }
        COSBase contents = dereference(rawContents);
        if (contents == null || contents instanceof COSNull) {
            return;
        }
        if (contents instanceof COSArray) {
            COSArray streams = (COSArray) contents;
            for (int index = 0; index < streams.size(); index++) {
                COSBase stream = dereference(streams.get(index));
                if (!(stream instanceof COSStream)) {
                    throw preservationUnsupported();
                }
                requireSafeContentStream((COSStream) stream);
            }
            return;
        }
        if (!(contents instanceof COSStream)) {
            throw preservationUnsupported();
        }
        requireSafeContentStream((COSStream) contents);
    }

    private static void requireSafeContentStream(COSStream stream)
            throws DocumentFailure {
        for (COSName name : stream.keySet()) {
            if (!isOneOf(
                    name,
                    "Length",
                    "Filter",
                    "DecodeParms",
                    "DL")) {
                throw preservationUnsupported();
            }
        }
        requireSafeContentFilters(stream);
        byte[] buffer = new byte[8192];
        try (InputStream decoded = stream.createInputStream()) {
            while (decoded.read(buffer) != -1) {
                // Exhaust the decoder before PDFBox's page importer can hide
                // a decoding failure and replace the content.
            }
        } catch (IOException | RuntimeException decodingFailure) {
            throw preservationUnsupported();
        }
    }

    private static void requireSafeContentFilters(COSStream stream)
            throws DocumentFailure {
        COSBase rawFilters = stream.getItem(COSName.FILTER);
        COSBase rawDecodeParameters = stream.getItem(COSName.DECODE_PARMS);
        if (rawFilters == null) {
            if (rawDecodeParameters != null) {
                throw preservationUnsupported();
            }
            return;
        }

        COSBase filters = dereference(rawFilters);
        if (!COSName.FLATE_DECODE.equals(filters)
                && !COSName.FLATE_DECODE_ABBREVIATION.equals(filters)) {
            throw preservationUnsupported();
        }
        if (rawDecodeParameters != null) {
            requireEmptyDecodeParameters(
                    dereference(rawDecodeParameters));
        }
        requireStrictFlateStream(stream);
    }

    private static void requireEmptyDecodeParameters(COSBase parameters)
            throws DocumentFailure {
        if (!(parameters instanceof COSDictionary)
                || ((COSDictionary) parameters).size() != 0) {
            throw preservationUnsupported();
        }
    }

    private static void requireStrictFlateStream(COSStream stream)
            throws DocumentFailure {
        Inflater inflater = new Inflater();
        byte[] encodedBuffer = new byte[8192];
        byte[] decodedBuffer = new byte[8192];
        try (InputStream encoded = stream.createRawInputStream()) {
            while (!inflater.finished()) {
                if (inflater.needsDictionary()) {
                    throw preservationUnsupported();
                }
                if (inflater.needsInput()) {
                    int encodedCount = encoded.read(encodedBuffer);
                    if (encodedCount < 0) {
                        throw preservationUnsupported();
                    }
                    if (encodedCount == 0) {
                        continue;
                    }
                    inflater.setInput(encodedBuffer, 0, encodedCount);
                }
                int decodedCount = inflater.inflate(decodedBuffer);
                if (decodedCount == 0
                        && !inflater.finished()
                        && !inflater.needsInput()
                        && !inflater.needsDictionary()) {
                    throw preservationUnsupported();
                }
            }
            if (inflater.getRemaining() != 0 || encoded.read() != -1) {
                throw preservationUnsupported();
            }
        } catch (IOException | DataFormatException | RuntimeException failure) {
            throw preservationUnsupported();
        } finally {
            inflater.end();
        }
    }

    private static boolean isSafeInlineExtension(
            COSBase value,
            IdentityHashMap<COSBase, Boolean> visited) {
        if (value == null) {
            return true;
        }
        if (value instanceof COSObject || value instanceof COSStream) {
            return false;
        }
        if (value instanceof COSArray) {
            if (visited.put(value, Boolean.TRUE) != null) {
                return false;
            }
            COSArray array = (COSArray) value;
            for (int index = 0; index < array.size(); index++) {
                if (!isSafeInlineExtension(array.get(index), visited)) {
                    return false;
                }
            }
            return true;
        }
        if (value instanceof COSDictionary) {
            if (visited.put(value, Boolean.TRUE) != null) {
                return false;
            }
            for (COSBase entry : ((COSDictionary) value).getValues()) {
                if (!isSafeInlineExtension(entry, visited)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isOneOf(COSName candidate, String... names) {
        for (String name : names) {
            if (COSName.getPDFName(name).equals(candidate)) {
                return true;
            }
        }
        return false;
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

    private static DocumentFailure invalidPageTreeForQuery() {
        return failure(
                DocumentFailureCode.QUERY_FAILED,
                "The page Object Reference could not be resolved from the page tree.");
    }

    private static DocumentFailure invalidPageRange() {
        return failure(
                DocumentFailureCode.PAGE_RANGE_INVALID,
                "The page range is outside the current document.");
    }

    private static DocumentFailure invalidMergeSources() {
        return failure(
                DocumentFailureCode.MERGE_SOURCE_INVALID,
                "The merge command must name unique declared non-primary Sources.");
    }

    private static DocumentFailure failure(
            DocumentFailureCode code,
            String diagnostic) {
        return new DocumentFailure(code, CAPABILITY_ID, diagnostic);
    }

    private static final class CallerSourceRuntimeFailure
            extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final RuntimeException callerFailure;

        CallerSourceRuntimeFailure(RuntimeException callerFailure) {
            this.callerFailure = callerFailure;
        }

        RuntimeException getCallerFailure() {
            return callerFailure;
        }
    }

    private static final class PageReferenceLookup {

        private final long requestedPageNumber;
        private long currentPageNumber;
        private COSBase reference;

        PageReferenceLookup(int requestedPageNumber) {
            this.requestedPageNumber = requestedPageNumber;
        }

        void add(COSBase pageReference) {
            currentPageNumber++;
            if (currentPageNumber == requestedPageNumber) {
                reference = pageReference;
            }
        }

        COSBase getReference() {
            return reference;
        }
    }

    private static void closeQuietly(List<PDDocument> documents) {
        if (documents == null) {
            return;
        }
        for (PDDocument document : documents) {
            try {
                document.close();
            } catch (IOException | RuntimeException ignored) {
                // A safe primary failure or completed clone takes precedence.
            }
        }
    }

    private static void closeForSuccess(List<PDDocument> documents)
            throws DocumentFailure {
        boolean closeFailed = false;
        if (documents != null) {
            for (PDDocument document : documents) {
                try {
                    document.close();
                } catch (IOException | RuntimeException failure) {
                    closeFailed = true;
                }
            }
        }
        if (closeFailed) {
            throw failure(
                    DocumentFailureCode.RESOURCE_CLOSE_FAILED,
                    "A library-owned document resource could not be closed cleanly.");
        }
    }
}
