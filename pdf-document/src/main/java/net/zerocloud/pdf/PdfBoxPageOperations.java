package net.zerocloud.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
    private final Map<String, PdfBoxWorkflowEngine.PreparedNamedSource> sources;
    private final Map<String, PublicationTarget> publicationTargets;
    private final PdfVersion publicationVersion;
    private final PasswordEncryptionAlgorithm publicationAlgorithm;
    private final PasswordEncryptionScope publicationScope;
    private final PdfBoxValueAdapter valueAdapter;
    private final PdfBoxMetadataOperations metadataOperations;
    private final PdfBoxAnnotationPageOperations annotationOperations;
    private final WorkflowResourceContext resources;
    private Map<String, PDDocument> splitDocuments;

    PdfBoxPageOperations(
            PDDocument document,
            Map<String, PdfBoxWorkflowEngine.PreparedNamedSource> sources,
            boolean libraryOwnedDocument,
            Map<String, PublicationTarget> publicationTargets,
            PdfVersion publicationVersion,
            PasswordEncryptionAlgorithm publicationAlgorithm,
            PasswordEncryptionScope publicationScope,
            PdfBoxValueAdapter valueAdapter,
            PdfBoxMetadataOperations metadataOperations,
            PdfBoxAnnotationPageOperations annotationOperations,
            WorkflowResourceContext resources) throws DocumentFailure {
        this.document = Objects.requireNonNull(document, "document");
        this.sources = Objects.requireNonNull(sources, "sources");
        this.publicationTargets = Objects.requireNonNull(
                publicationTargets,
                "publicationTargets");
        this.publicationVersion = publicationVersion;
        this.publicationAlgorithm = publicationAlgorithm;
        this.publicationScope = publicationScope;
        this.valueAdapter = Objects.requireNonNull(valueAdapter, "valueAdapter");
        this.metadataOperations = Objects.requireNonNull(
                metadataOperations,
                "metadataOperations");
        this.annotationOperations = Objects.requireNonNull(
                annotationOperations,
                "annotationOperations");
        this.resources = Objects.requireNonNull(resources, "resources");
        if (libraryOwnedDocument) {
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
            resources.checkpoint();
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
        } catch (RuntimeException backendFailure) {
            resources.rethrowResourceOrTerminalFailure(backendFailure);
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
            resources.rethrowResourceOrTerminalFailure(backendFailure);
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

    void makeLibraryOwnedPageIndirect(PDPage page) throws DocumentFailure {
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
            resources.checkpoint();
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

    private void makeLibraryOwnedPageTreeIndirect(
            PDDocument generatedDocument) throws DocumentFailure {
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
        Deque<PageTreeNode> pending = new ArrayDeque<PageTreeNode>();
        IdentityHashMap<COSDictionary, Boolean> visited =
                new IdentityHashMap<COSDictionary, Boolean>();
        pending.push(new PageTreeNode(
                catalog.getItem(COSName.PAGES),
                1));
        while (!pending.isEmpty()) {
            resources.checkpoint();
            PageTreeNode current = pending.pop();
            resources.requireNestingDepth(current.depth);
            COSBase currentValue = dereference(current.rawNode);
            if (!(currentValue instanceof COSDictionary)) {
                throw new IllegalStateException(
                        "A library-owned page tree node must be a dictionary.");
            }
            COSDictionary node = (COSDictionary) currentValue;
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
                resources.checkpoint();
                COSBase rawKid = kids.get(index);
                COSBase kidValue = dereference(rawKid);
                if (!(kidValue instanceof COSDictionary)) {
                    throw new IllegalStateException(
                            "A library-owned page tree child must be a dictionary.");
                }
                if (!(rawKid instanceof COSObject)) {
                    kids.set(index, new COSObject(kidValue));
                    rawKid = kids.get(index);
                }
                COSDictionary kid = (COSDictionary) kidValue;
                if (COSName.PAGES.equals(dereference(
                        kid.getItem(COSName.TYPE)))) {
                    pending.push(new PageTreeNode(
                            rawKid,
                            current.depth + 1));
                }
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
            resources.rethrowResourceOrTerminalFailure(backendFailure);
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The blank page could not be inserted.");
        }
    }

    void requireAppendPreservable() throws DocumentFailure {
        requirePreservable(document);
    }

    void appendComposedPages(List<PDPage> pages) throws DocumentFailure {
        for (PDPage page : pages) {
            resources.checkpoint();
            document.addPage(page);
            makeLibraryOwnedPageIndirect(page);
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
            resources.checkpoint();
            removed.add(Integer.valueOf(pageNumber - 1));
        }
        metadataOperations.requireNoDestinationConflict(document, removed);
        annotationOperations.requireNoDestinationConflict(document, removed);
        try {
            for (int pageNumber = range.getLastPageNumber();
                    pageNumber >= range.getFirstPageNumber();
                    pageNumber--) {
                resources.checkpoint();
                document.removePage(pageNumber - 1);
            }
        } catch (RuntimeException backendFailure) {
            resources.rethrowResourceOrTerminalFailure(backendFailure);
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
                resources.checkpoint();
                PDPage page = document.getPage(pageNumber - 1);
                materializeInheritedPageAttributes(page);
                selected.add(page);
            }
            for (int pageNumber = range.getLastPageNumber();
                    pageNumber >= range.getFirstPageNumber();
                    pageNumber--) {
                resources.checkpoint();
                document.removePage(pageNumber - 1);
            }
            int destination = movement.getDestinationPageNumber();
            for (PDPage page : selected) {
                resources.checkpoint();
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
            resources.rethrowResourceOrTerminalFailure(backendFailure);
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
                resources.checkpoint();
                PDPage copiedPage = document.getPage(originalPageCount + index);
                copiedPages.add(copiedPage);
            }
            for (int index = 0; index < copiedPageCount; index++) {
                resources.checkpoint();
                document.removePage(originalPageCount);
            }
            for (PDPage page : copiedPages) {
                resources.checkpoint();
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
                resources.checkpoint();
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
            annotationStructures.close();
            throw structureFailure;
        } catch (IOException | RuntimeException backendFailure) {
            closeQuietly(copiedDocuments);
            annotationStructures.close();
            resources.rethrowResourceOrTerminalFailure(backendFailure);
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The pages could not be copied.");
        }
        try {
            closeForSuccess(copiedDocuments);
        } finally {
            annotationStructures.close();
        }
    }

    private void repairPageParentReferences(PDDocument candidate)
            throws DocumentFailure {
        COSBase rawRoot = candidate.getDocumentCatalog()
                .getCOSObject().getItem(COSName.PAGES);
        Deque<PageTreeNode> pending = new ArrayDeque<PageTreeNode>();
        IdentityHashMap<COSDictionary, Boolean> visited =
                new IdentityHashMap<COSDictionary, Boolean>();
        pending.push(new PageTreeNode(rawRoot, 1));
        while (!pending.isEmpty()) {
            resources.checkpoint();
            PageTreeNode current = pending.pop();
            resources.requireNestingDepth(current.depth);
            if (!(current.rawNode instanceof COSObject)) {
                throw preservationUnsupported();
            }
            COSBase nodeValue = dereference(current.rawNode);
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
                resources.checkpoint();
                COSBase rawChild = kids.get(index);
                COSBase childValue = dereference(rawChild);
                if (!(rawChild instanceof COSObject)
                        || !(childValue instanceof COSDictionary)) {
                    throw preservationUnsupported();
                }
                COSDictionary child = (COSDictionary) childValue;
                child.setItem(COSName.PARENT, current.rawNode);
                if (COSName.PAGES.equals(
                        dereference(child.getItem(COSName.TYPE)))) {
                    pending.push(new PageTreeNode(
                            rawChild,
                            current.depth + 1));
                }
            }
        }
    }

    private void merge(MergeDocuments merge) throws DocumentFailure {
        LinkedHashSet<String> selectedSources = new LinkedHashSet<String>();
        for (String sourceName : merge.getSourceNames()) {
            resources.checkpoint();
            if (!selectedSources.add(sourceName)
                    || !sources.containsKey(sourceName)) {
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
        List<PdfBoxMetadataOperations.MergedStructures> structures =
                new ArrayList<PdfBoxMetadataOperations.MergedStructures>();
        List<PdfBoxAnnotationPageOperations.MergeStructures>
                annotationStructures =
                new ArrayList<
                        PdfBoxAnnotationPageOperations.MergeStructures>();
        try {
            PDDocument combinedSources = new PDDocument(
                    resources.streamCacheFactory());
            mergeDocuments.add(combinedSources);
            PDFMergerUtility merger = new PDFMergerUtility();
            for (String sourceName : merge.getSourceNames()) {
                resources.checkpoint();
                PdfBoxWorkflowEngine.PreparedNamedSource preparedSource =
                        sources.get(sourceName);
                preparedSource.requireMergeAllowed(
                        publicationVersion,
                        publicationAlgorithm,
                        publicationScope);
                PDDocument sourceDocument = openAdditionalSource(
                        preparedSource);
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
                resources.checkpoint();
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
            closeMetadataStructures(structures);
            closeAnnotationStructures(annotationStructures);
            resources.rethrowTerminalFailure();
            if (PdfBoxWorkflowEngine.VERSION_SECURITY_CAPABILITY_ID.equals(
                    sourceFailure.getCapabilityId())) {
                throw sourceFailure;
            }
            throw failure(
                    sourceFailure.getCode(),
                    sourceFailure.getDiagnostic());
        } catch (IOException | RuntimeException backendFailure) {
            closeQuietly(mergeDocuments);
            closeMetadataStructures(structures);
            closeAnnotationStructures(annotationStructures);
            resources.rethrowResourceOrTerminalFailure(backendFailure);
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The named Sources could not be merged safely.");
        }
        try {
            closeForSuccess(mergeDocuments);
        } finally {
            closeMetadataStructures(structures);
            closeAnnotationStructures(annotationStructures);
        }
    }

    private static PDDocument openAdditionalSource(
            PdfBoxWorkflowEngine.PreparedNamedSource source)
            throws DocumentFailure {
        return source.open();
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
            resources.checkpoint();
            requireRange(range);
        }
        requirePreservable(document);
        boolean hadInfo = document.getDocument().getTrailer()
                .getItem(COSName.INFO) != null;

        Map<String, PDDocument> created =
                new LinkedHashMap<String, PDDocument>();
        PdfBoxMetadataOperations.MergedStructures snapshot = null;
        PdfBoxAnnotationPageOperations.MergeStructures annotationSnapshot =
                null;
        try {
            snapshot = metadataOperations.snapshotManagedStructures(document);
            annotationSnapshot =
                    annotationOperations.snapshotSplitStructures(document);
            for (Map.Entry<String, PageRange> product
                    : split.getTargetRanges().entrySet()) {
                resources.checkpoint();
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
            closeQuietly(snapshot);
            closeQuietly(annotationSnapshot);
            resources.rethrowTerminalFailure();
            throw failure(
                    splitFailure.getCode(),
                    splitFailure.getDiagnostic());
        } catch (IOException | RuntimeException backendFailure) {
            closeQuietly(new ArrayList<PDDocument>(created.values()));
            closeQuietly(snapshot);
            closeQuietly(annotationSnapshot);
            resources.rethrowResourceOrTerminalFailure(backendFailure);
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The split products could not be created safely.");
        }
        closeQuietly(snapshot);
        closeQuietly(annotationSnapshot);
    }

    private static void closeAnnotationStructures(
            List<PdfBoxAnnotationPageOperations.MergeStructures> structures) {
        for (PdfBoxAnnotationPageOperations.MergeStructures structure
                : structures) {
            structure.close();
        }
    }

    private static void closeMetadataStructures(
            List<PdfBoxMetadataOperations.MergedStructures> structures) {
        for (PdfBoxMetadataOperations.MergedStructures structure
                : structures) {
            structure.close();
        }
    }

    private static void closeQuietly(
            PdfBoxMetadataOperations.MergedStructures structures) {
        if (structures != null) {
            structures.close();
        }
    }

    private static void closeQuietly(
            PdfBoxAnnotationPageOperations.MergeStructures structures) {
        if (structures != null) {
            structures.close();
        }
    }

    private int[] splitMapping(int pageCount, PageRange range)
            throws DocumentFailure {
        int[] mapping = new int[pageCount];
        int position = 0;
        for (int pageNumber = range.getFirstPageNumber();
                pageNumber <= range.getLastPageNumber();
                pageNumber++) {
            resources.checkpoint();
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

    private Splitter splitter(PageRange range) {
        Splitter splitter = new Splitter();
        splitter.setStreamCacheCreateFunction(
                resources.streamCacheFactory());
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
        Deque<PageReferenceFrame> pending =
                new ArrayDeque<PageReferenceFrame>();
        pending.push(pageReferenceFrame(
                rawNode,
                expectedParent,
                visited,
                1));
        long rootPageCount = 0L;
        while (!pending.isEmpty()) {
            resources.checkpoint();
            PageReferenceFrame current = pending.peek();
            if (current.index == current.kids.size()) {
                COSBase count = dereference(
                        current.node.getItem(COSName.COUNT));
                if (!(count instanceof COSInteger)
                        || ((COSInteger) count).longValue()
                                != current.descendantPageCount) {
                    throw invalidPageTreeForQuery();
                }
                pending.pop();
                if (pending.isEmpty()) {
                    rootPageCount = current.descendantPageCount;
                } else {
                    PageReferenceFrame parent = pending.peek();
                    if (parent.descendantPageCount
                            > Long.MAX_VALUE - current.descendantPageCount) {
                        throw invalidPageTreeForQuery();
                    }
                    parent.descendantPageCount +=
                            current.descendantPageCount;
                }
                continue;
            }

            COSBase rawChild = current.kids.get(current.index++);
            COSBase childValue = dereference(rawChild);
            if (!(childValue instanceof COSDictionary)) {
                throw invalidPageTreeForQuery();
            }
            COSDictionary child = (COSDictionary) childValue;
            COSBase type = dereference(child.getItem(COSName.TYPE));
            if (COSName.PAGES.equals(type)) {
                pending.push(pageReferenceFrame(
                        rawChild,
                        current.node,
                        visited,
                        current.depth + 1));
            } else if (COSName.PAGE.equals(type)) {
                if (!(rawChild instanceof COSObject)
                        || visited.put(child, Boolean.TRUE) != null
                        || dereference(child.getItem(COSName.PARENT))
                                != current.node) {
                    throw invalidPageTreeForQuery();
                }
                lookup.add(rawChild);
                if (current.descendantPageCount == Long.MAX_VALUE) {
                    throw invalidPageTreeForQuery();
                }
                current.descendantPageCount++;
            } else {
                throw invalidPageTreeForQuery();
            }
        }
        return rootPageCount;
    }

    private PageReferenceFrame pageReferenceFrame(
            COSBase rawNode,
            COSDictionary expectedParent,
            IdentityHashMap<COSDictionary, Boolean> visited,
            int depth) throws DocumentFailure {
        resources.checkpoint();
        resources.requireNestingDepth(depth);
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
        return new PageReferenceFrame(
                node,
                (COSArray) kidsValue,
                depth);
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
            resources.checkpoint();
            if (!COSName.TYPE.equals(name)
                    && !COSName.PAGES.equals(name)
                    && !COSName.VERSION.equals(name)
                    && !(candidate.isEncrypted()
                            && COSName.EXTENSIONS.equals(name)
                            && PdfBoxPasswordSecurity
                                    .hasOnlyPreservableAdobeSecurityExtension(
                                            candidate))
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
            resources.rethrowTerminalFailure();
            throw preservationUnsupported();
        }
        requireSafePageTree(
                catalog.getItem(COSName.PAGES),
                null,
                new IdentityHashMap<COSDictionary, Boolean>(),
                1);
        PdfBoxAnnotationDecodePolicy.Budgets annotationBudgets =
                PdfBoxAnnotationDecodePolicy.newManagedGraphPass();
        for (PDPage page : candidate.getPages()) {
            resources.checkpoint();
            COSDictionary dictionary = page.getCOSObject();
            requireSafePage(dictionary);
            try {
                annotationOperations.requireSafeAnnotations(
                        candidate,
                        dictionary,
                        dictionary.getItem(COSName.ANNOTS),
                        annotationBudgets);
            } catch (DocumentFailure unsafeAnnotations) {
                resources.rethrowTerminalFailure();
                throw preservationUnsupported();
            }
        }
    }

    private void requireSafeTrailer(PDDocument candidate)
            throws DocumentFailure {
        COSDictionary trailer = candidate.getDocument().getTrailer();
        if (trailer == null) {
            return;
        }
        for (COSName name : trailer.keySet()) {
            resources.checkpoint();
            if (!COSName.INFO.equals(name)
                    && !(candidate.isEncrypted()
                            && COSName.ENCRYPT.equals(name))
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

    private void requireSafePage(COSDictionary page)
            throws DocumentFailure {
        resources.checkpoint();
        for (COSName name : page.keySet()) {
            resources.checkpoint();
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

    private long requireSafePageTree(
            COSBase rawNode,
            COSDictionary expectedParent,
            IdentityHashMap<COSDictionary, Boolean> visited,
            int depth)
            throws DocumentFailure {
        Deque<SafePageTreeFrame> pending =
                new ArrayDeque<SafePageTreeFrame>();
        pending.push(safePageTreeFrame(
                rawNode,
                expectedParent,
                visited,
                depth));
        long rootPageCount = 0L;
        while (!pending.isEmpty()) {
            resources.checkpoint();
            SafePageTreeFrame current = pending.peek();
            if (current.index == current.kids.size()) {
                COSBase count = dereference(
                        current.node.getItem(COSName.COUNT));
                if (!(count instanceof COSInteger)
                        || ((COSInteger) count).longValue()
                                != current.descendantPageCount) {
                    throw preservationUnsupported();
                }
                pending.pop();
                if (pending.isEmpty()) {
                    rootPageCount = current.descendantPageCount;
                } else {
                    SafePageTreeFrame parent = pending.peek();
                    if (parent.descendantPageCount
                            > Long.MAX_VALUE - current.descendantPageCount) {
                        throw preservationUnsupported();
                    }
                    parent.descendantPageCount += current.descendantPageCount;
                }
                continue;
            }

            COSBase rawChild = current.kids.get(current.index++);
            COSBase childValue = dereference(rawChild);
            if (!(childValue instanceof COSDictionary)) {
                throw preservationUnsupported();
            }
            COSDictionary child = (COSDictionary) childValue;
            COSBase type = dereference(child.getItem(COSName.TYPE));
            if (COSName.PAGES.equals(type)) {
                pending.push(safePageTreeFrame(
                        rawChild,
                        current.node,
                        visited,
                        current.depth + 1));
            } else if (COSName.PAGE.equals(type)) {
                if (!(rawChild instanceof COSObject)
                        || visited.put(child, Boolean.TRUE) != null
                        || dereference(child.getItem(COSName.PARENT))
                                != current.node) {
                    throw preservationUnsupported();
                }
                if (current.descendantPageCount == Long.MAX_VALUE) {
                    throw preservationUnsupported();
                }
                current.descendantPageCount++;
            } else {
                throw preservationUnsupported();
            }
        }
        return rootPageCount;
    }

    private SafePageTreeFrame safePageTreeFrame(
            COSBase rawNode,
            COSDictionary expectedParent,
            IdentityHashMap<COSDictionary, Boolean> visited,
            int depth) throws DocumentFailure {
        resources.checkpoint();
        resources.requireNestingDepth(depth);
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
            resources.checkpoint();
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
        return new SafePageTreeFrame(
                node,
                (COSArray) kidsValue,
                depth);
    }

    private void requireSafeInheritablePageAttributes(
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

    private void requireSafeResources(COSDictionary dictionary)
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

    private void requireResourceGraphDetachedFromPageTree(
            COSBase rawValue,
            IdentityHashMap<COSBase, Boolean> visited)
            throws DocumentFailure {
        Deque<InlineValueNode> pending = new ArrayDeque<InlineValueNode>();
        pending.push(new InlineValueNode(rawValue, 1));
        while (!pending.isEmpty()) {
            resources.checkpoint();
            InlineValueNode current = pending.pop();
            resources.requireNestingDepth(current.depth);
            COSBase value = dereference(current.value);
            if (value == null) {
                throw preservationUnsupported();
            }
            if (visited.put(value, Boolean.TRUE) != null) {
                continue;
            }
            if (value instanceof COSArray) {
                COSArray array = (COSArray) value;
                for (int index = 0; index < array.size(); index++) {
                    resources.checkpoint();
                    pending.push(new InlineValueNode(
                            array.get(index),
                            current.depth + 1));
                }
                continue;
            }
            if (!(value instanceof COSDictionary)) {
                continue;
            }
            COSDictionary resource = (COSDictionary) value;
            COSBase type = dereference(resource.getItem(COSName.TYPE));
            if (COSName.PAGE.equals(type) || COSName.PAGES.equals(type)) {
                throw preservationUnsupported();
            }
            for (COSBase entry : resource.getValues()) {
                resources.checkpoint();
                pending.push(new InlineValueNode(
                        entry,
                        current.depth + 1));
            }
        }
    }

    private void requireEffectiveMediaBox(COSDictionary page)
            throws DocumentFailure {
        COSDictionary current = page;
        IdentityHashMap<COSDictionary, Boolean> visited =
                new IdentityHashMap<COSDictionary, Boolean>();
        while (visited.put(current, Boolean.TRUE) == null) {
            resources.checkpoint();
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

    private void requireSafeContents(COSBase rawContents)
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
                resources.checkpoint();
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

    private void requireSafeContentStream(COSStream stream)
            throws DocumentFailure {
        for (COSName name : stream.keySet()) {
            resources.checkpoint();
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
        try {
            PdfBoxHostileInputPreflight.decodeStream(
                    stream,
                    resources,
                    new DiscardOutputStream());
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException decodingFailure) {
            resources.rethrowResourceOrTerminalFailure(decodingFailure);
            throw preservationUnsupported();
        }
    }

    private void requireSafeContentFilters(COSStream stream)
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

    private void requireStrictFlateStream(COSStream stream)
            throws DocumentFailure {
        Inflater inflater = new Inflater();
        byte[] encodedBuffer = new byte[8192];
        byte[] decodedBuffer = new byte[8192];
        try (InputStream encoded = resources.checkpointedInput(
                stream.createRawInputStream())) {
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
                resources.consumeDecompressedBytes(decodedCount);
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
            resources.rethrowResourceOrTerminalFailure(failure);
            throw preservationUnsupported();
        } finally {
            inflater.end();
        }
    }

    private boolean isSafeInlineExtension(
            COSBase value,
            IdentityHashMap<COSBase, Boolean> visited)
            throws DocumentFailure {
        Deque<InlineValueNode> pending = new ArrayDeque<InlineValueNode>();
        pending.push(new InlineValueNode(value, 1));
        while (!pending.isEmpty()) {
            resources.checkpoint();
            InlineValueNode current = pending.pop();
            COSBase currentValue = current.value;
            if (currentValue == null) {
                continue;
            }
            if (currentValue instanceof COSObject
                    || currentValue instanceof COSStream) {
                return false;
            }
            if (currentValue instanceof COSArray) {
                resources.requireNestingDepth(current.depth);
                if (visited.put(currentValue, Boolean.TRUE) != null) {
                    return false;
                }
                COSArray array = (COSArray) currentValue;
                for (int index = array.size() - 1; index >= 0; index--) {
                    resources.checkpoint();
                    pending.push(new InlineValueNode(
                            array.get(index),
                            current.depth + 1));
                }
            } else if (currentValue instanceof COSDictionary) {
                resources.requireNestingDepth(current.depth);
                if (visited.put(currentValue, Boolean.TRUE) != null) {
                    return false;
                }
                List<COSBase> values = new ArrayList<COSBase>();
                for (COSBase entry
                        : ((COSDictionary) currentValue).getValues()) {
                    resources.checkpoint();
                    values.add(entry);
                }
                for (int index = values.size() - 1; index >= 0; index--) {
                    resources.checkpoint();
                    pending.push(new InlineValueNode(
                            values.get(index),
                            current.depth + 1));
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

    private static final class SafePageTreeFrame {

        private final COSDictionary node;
        private final COSArray kids;
        private final int depth;
        private int index;
        private long descendantPageCount;

        private SafePageTreeFrame(
                COSDictionary node,
                COSArray kids,
                int depth) {
            this.node = node;
            this.kids = kids;
            this.depth = depth;
        }
    }

    private static final class PageTreeNode {

        private final COSBase rawNode;
        private final int depth;

        private PageTreeNode(COSBase rawNode, int depth) {
            this.rawNode = rawNode;
            this.depth = depth;
        }
    }

    private static final class PageReferenceFrame {

        private final COSDictionary node;
        private final COSArray kids;
        private final int depth;
        private int index;
        private long descendantPageCount;

        private PageReferenceFrame(
                COSDictionary node,
                COSArray kids,
                int depth) {
            this.node = node;
            this.kids = kids;
            this.depth = depth;
        }
    }

    private static final class InlineValueNode {

        private final COSBase value;
        private final int depth;

        private InlineValueNode(COSBase value, int depth) {
            this.value = value;
            this.depth = depth;
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

    private static final class DiscardOutputStream extends OutputStream {

        @Override
        public void write(int value) {
            // Validation only.
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            // Validation only.
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
