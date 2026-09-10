package net.zerocloud.pdf.itext7.kernel.pdf;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PageRange;
import net.zerocloud.pdf.PublicationReceipt;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.command.InsertBlankPage;
import net.zerocloud.pdf.command.CopyPages;
import net.zerocloud.pdf.command.MovePages;
import net.zerocloud.pdf.command.MergeDocuments;
import net.zerocloud.pdf.command.RemovePages;
import net.zerocloud.pdf.command.SplitDocument;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;
import net.zerocloud.pdf.itext7.kernel.utils.PdfMerger;
import net.zerocloud.pdf.itext7.kernel.utils.PdfSplitter;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.PageObjectReference;

/**
 * Lifecycle mapping of the create, publish, reopen, and inspect workflow.
 *
 * @since 0.1.0
 */
public final class PdfDocument implements Closeable {

    static {
        FacadeClasspathGuard.requireSingleEdition();
    }

    private final FacadeDeclarations declarations;
    private final Thread owner = Thread.currentThread();
    private FacadeSession valueSession;
    private boolean valueSessionRequested;
    private PdfCatalog catalog;
    private final List<PdfPage> queuedPageHandles = new ArrayList<PdfPage>();
    private int pageCount;
    private boolean closed;
    private List<PublicationReceipt> publicationReceipts = Collections.emptyList();

    /**
     * Opens a new document in writing mode.
     *
     * @param writer the destination declaration
     */
    public PdfDocument(PdfWriter writer) {
        this(Collections.<String, PdfReader>emptyMap(), null,
                Collections.singletonMap("target", Objects.requireNonNull(writer, "writer")));
    }

    /**
     * Opens a detached document view in reading mode.
     *
     * @param reader the validated source reader
     */
    public PdfDocument(PdfReader reader) {
        this(Collections.singletonMap("source", Objects.requireNonNull(reader, "reader")),
                "source", Collections.<String, PdfWriter>emptyMap());
    }

    /**
     * Opens an existing document for validated changes and publication.
     * @param reader the validated source reader
     * @param writer the destination declaration
     */
    public PdfDocument(PdfReader reader, PdfWriter writer) {
        this(Collections.singletonMap("source", Objects.requireNonNull(reader, "reader")), "source",
                Collections.singletonMap("target", Objects.requireNonNull(writer, "writer")));
    }

    /**
     * Declares all Sources and Targets before starting one Native Workflow.
     * The maps are copied in iteration order and each Reader snapshot is
     * transferred exactly once after all declarations pass validation.
     * @param sources named Reader snapshots, with no repeated Reader instance
     * @param primarySource the declared primary name, or null for no Sources
     * @param targets named publication destinations in receipt order
     */
    public PdfDocument(Map<String, PdfReader> sources, String primarySource,
            Map<String, PdfWriter> targets) {
        declarations = new FacadeDeclarations(sources, primarySource, targets);
        pageCount = declarations.sourcePageCount;
    }

    /**
     * Adds one blank page to a document opened for writing.
     *
     * @return the added page
     */
    public PdfPage addNewPage() {
        requireOpen();
        if (!declarations.hasTargets()) {
            throw new IllegalStateException(
                    "A read-only facade document cannot add a page.");
        }
        if (valueSession != null) {
            return valueSession.call(session -> {
                session.execute(AddBlankPage.INSTANCE);
                return new PdfPage(valueSession.referenceFor(session.query(
                        PageObjectReference.version1(session.query(PageCount.INSTANCE).intValue()))));
            });
        }
        pageCount++;
        PdfPage page = new PdfPage(this);
        queuedPageHandles.add(page);
        return page;
    }

    /**
     * Inserts a library-default blank page before a one-based position.
     * @param pageNumber insertion position, including one past the last page
     * @return the added page, retaining its identity through later reordering
     */
    public PdfPage addNewPage(int pageNumber) {
        requireOpen();
        if (!declarations.hasTargets()) {
            throw new IllegalStateException("A read-only facade document cannot add a page.");
        }
        openValueSession();
        return valueSession.call(session -> {
            session.execute(InsertBlankPage.version1(pageNumber));
            return new PdfPage(valueSession.referenceFor(session.query(PageObjectReference.version1(pageNumber))));
        });
    }

    /**
     * Selects the page currently at a one-based position.
     * @param pageNumber the current page number
     * @return a Session-scoped page handle
     */
    public PdfPage getPage(int pageNumber) {
        requireOpen();
        openValueSession();
        return valueSession.call(session -> new PdfPage(valueSession.referenceFor(
                session.query(PageObjectReference.version1(pageNumber)))));
    }

    /**
     * Removes one page from the current sequence.
     * @param pageNumber the one-based page number
     */
    public void removePage(int pageNumber) {
        removePages(pageNumber, pageNumber);
    }

    /**
     * Removes an inclusive range from the current sequence.
     * @param firstPage the first one-based page number
     * @param lastPage the last one-based page number
     */
    public void removePages(int firstPage, int lastPage) {
        executePageCommand(RemovePages.version1(PageRange.of(firstPage, lastPage)));
    }

    /**
     * Moves one page to a position measured after its removal.
     * @param pageNumber the one-based page to move
     * @param destination the one-based insertion position after removal
     */
    public void movePage(int pageNumber, int destination) {
        movePages(pageNumber, pageNumber, destination);
    }

    /**
     * Moves an inclusive range to a position measured after its removal.
     * @param firstPage the first one-based page number
     * @param lastPage the last one-based page number
     * @param destination the one-based insertion position after removal
     */
    public void movePages(int firstPage, int lastPage, int destination) {
        executePageCommand(MovePages.version1(PageRange.of(firstPage, lastPage), destination));
    }

    private void executePageCommand(DocumentCommand command) {
        requirePageMutation();
        openValueSession();
        valueSession.call(session -> {
            session.execute(command);
            return null;
        });
    }

    /**
     * Copies an inclusive range within this document.
     * @param firstPage the first one-based page number
     * @param lastPage the last one-based page number
     * @param insertion the one-based position in the original sequence
     * @return immutable list of copied page handles in insertion order
     */
    public List<PdfPage> copyPages(int firstPage, int lastPage, int insertion) {
        requirePageMutation();
        openValueSession();
        return valueSession.call(session -> {
            session.execute(CopyPages.version1(PageRange.of(firstPage, lastPage), insertion));
            List<PdfPage> copies = new ArrayList<PdfPage>();
            for (int offset = 0; offset <= lastPage - firstPage; offset++) {
                copies.add(new PdfPage(valueSession.referenceFor(session.query(
                        PageObjectReference.version1(insertion + offset)))));
            }
            return Collections.unmodifiableList(copies);
        });
    }

    private void requirePageMutation() {
        requireOpen();
        if (!declarations.hasTargets()) {
            throw new IllegalStateException("A read-only facade document cannot change pages.");
        }
    }

    void materializePageHandles() {
        requireOpen();
        openValueSession();
    }

    /**
     * Returns the number of pages observed or queued in this document.
     *
     * @return the page count
     */
    public int getNumberOfPages() {
        requireOpen();
        return valueSession == null ? pageCount : valueSession.call(session -> session.query(PageCount.INSTANCE)).intValue();
    }

    /** @return the Catalog for inspection and validated low-level changes */
    public PdfCatalog getCatalog() {
        requireOpen();
        if (catalog == null) {
            openValueSession();
            catalog = valueSession.call(session -> {
                ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                return new PdfCatalog((PdfDictionary) valueSession.referenceFor(root).inspect(session));
            });
        }
        return catalog;
    }

    /** @return the ordered named-Source merger owned by this document */
    public PdfMerger getMerger() {
        requireOpen();
        return new PdfMerger(this) {
            @Override
            public PdfMerger merge(String... sourceNames) {
                executePageCommand(MergeDocuments.version1(sourceNames));
                return this;
            }
        };
    }

    /** @return the complete Target-group splitter owned by this document */
    public PdfSplitter getSplitter() {
        requireOpen();
        return new PdfSplitter(this) {
            @Override
            public void extractPageRanges(String[] targetNames, PageRange[] ranges) {
                String[] names = Objects.requireNonNull(targetNames, "targetNames").clone();
                PageRange[] selections = Objects.requireNonNull(ranges, "ranges").clone();
                if (names.length != selections.length) {
                    throw new IllegalArgumentException("Each split Target must have one corresponding page range.");
                }
                SplitDocument.Builder split = SplitDocument.version1();
                for (int index = 0; index < names.length; index++) {
                    split.target(names[index], selections[index]);
                }
                executePageCommand(split.build());
            }
        };
    }

    /**
     * Observes the actual Native publication result after this document closes.
     * @return immutable receipts in Target declaration order
     */
    public List<PublicationReceipt> getPublicationReceipts() {
        requireOwner();
        if (!closed) {
            throw new IllegalStateException("Publication receipts are available after the facade document closes.");
        }
        return publicationReceipts;
    }

    private void openValueSession() {
        if (valueSession != null) {
            return;
        }
        if (valueSessionRequested) {
            throw new IllegalStateException("The facade workflow could not be initialized.");
        }
        valueSessionRequested = true;
        int queuedPages = pageCount - declarations.sourcePageCount;
        valueSession = new FacadeSession(declarations.request, queuedPages, declarations.cancellation);
        if (!queuedPageHandles.isEmpty()) {
            valueSession.initialize(session -> {
                int pageNumber = declarations.sourcePageCount;
                for (PdfPage page : queuedPageHandles) {
                    page.bind(valueSession.referenceFor(session.query(PageObjectReference.version1(++pageNumber))));
                }
                return null;
            });
            queuedPageHandles.clear();
        }
    }

    /**
     * Publishes a writing document and closes this facade view.
     */
    @Override
    public void close() {
        requireOwner();
        if (closed) {
            return;
        }
        closed = true;
        Throwable primaryFailure = null;
        try {
            finishPublication();
        } catch (RuntimeException | Error failure) {
            primaryFailure = failure;
            if (failure.getCause() instanceof DocumentFailure) {
                publicationReceipts = ((DocumentFailure) failure.getCause()).getPublicationReceipts();
            }
            throw failure;
        } finally {
            queuedPageHandles.clear();
            try {
                declarations.close();
            } catch (IOException failure) {
                PdfException mapped = new PdfException(failure.getMessage(), null);
                if (primaryFailure == null) {
                    throw mapped;
                }
                primaryFailure.addSuppressed(mapped);
            }
        }
    }

    private void finishPublication() {
        if (valueSession == null && !valueSessionRequested && declarations.hasSources() && declarations.hasTargets()) {
            openValueSession();
        }
        if (valueSession != null) {
            WorkflowOutcome<Void> outcome = valueSession.close();
            publicationReceipts = outcome.getPublicationReceipts();
            declarations.requireCommittedReceipts(publicationReceipts);
            return;
        }
        if (valueSessionRequested) {
            return;
        }
        if (!declarations.hasTargets()) {
            return;
        }

        try {
            WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(
                    declarations.request,
                    session -> {
                        for (int page = 0; page < pageCount; page++) {
                            session.execute(AddBlankPage.INSTANCE);
                        }
                        return null;
                    });
            publicationReceipts = outcome.getPublicationReceipts();
            declarations.requireCommittedReceipts(publicationReceipts);
        } catch (DocumentFailure failure) {
            throw new PdfException(
                    failure.getCode().name() + ": " + failure.getDiagnostic(),
                    failure);
        }
    }

    private void requireOpen() {
        requireOwner();
        if (closed) {
            throw new IllegalStateException("The facade document is closed.");
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("The facade document is thread-confined.");
        }
    }
}
