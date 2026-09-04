package net.zerocloud.pdf;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSObjectKey;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.filter.DecodeOptions;
import org.apache.pdfbox.filter.Filter;
import org.apache.pdfbox.filter.FilterFactory;
import org.apache.pdfbox.pdmodel.PDDocument;

/** Stack-safe whole-document policy preflight at the backend adapter seam. */
final class PdfBoxHostileInputPreflight {

    private PdfBoxHostileInputPreflight() {
    }

    static void audit(
            PDDocument document,
            WorkflowResourceContext resources) throws DocumentFailure {
        resources.checkpoint();
        try {
            accountPages(document, resources);
            traverse(document, resources);
            PdfBoxImageResourceExtractionOperations
                    .accountMaterializableImagePixels(document, resources);
            resources.checkpoint();
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (PreflightResourceIOException failure) {
            throw failure.failure;
        } catch (IOException | RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw PdfBoxWorkflowEngine.failure(
                    DocumentFailureCode.SOURCE_READ_FAILED,
                    "The source could not be preflighted safely.");
        }
    }

    private static void accountPages(
            PDDocument document,
            WorkflowResourceContext resources)
            throws IOException, DocumentFailure {
        COSBase pagesValue = document.getDocumentCatalog().getCOSObject()
                .getDictionaryObject(COSName.PAGES);
        if (!(pagesValue instanceof COSDictionary)
                || pagesValue instanceof COSStream) {
            throw new IOException("Page-tree root is malformed");
        }
        List<PdfBoxPageTreePreflight.PageView> pages;
        try {
            pages = PdfBoxPageTreePreflight.pages(
                    (COSDictionary) pagesValue,
                    resources.getPolicy().getMaximumPages(),
                    Integer.MAX_VALUE,
                    resources);
        } catch (PdfBoxPageTreePreflight.LimitExceededException exhausted) {
            throw resources.policyFailure(
                    DocumentFailureCode.PAGE_LIMIT_EXCEEDED,
                    "The workflow page-count limit was exceeded.");
        } catch (IOException | RuntimeException malformed) {
            resources.rethrowResourceOrTerminalFailure(malformed);
            // Resource accounting must not replace an operation's established
            // diagnostic for a malformed page tree. The generic object walk
            // still accounts the underlying graph before caller work begins.
            return;
        }
        for (PdfBoxPageTreePreflight.PageView page : pages) {
            resources.observePage(page.source());
        }
    }

    private static void traverse(
            PDDocument document,
            WorkflowResourceContext resources)
            throws DocumentFailure, PreflightResourceIOException {
        COSDocument cosDocument = document.getDocument();
        resources.requireObjectCount(cosDocument.getXrefTable().size());

        Deque<TraversalFrame> pending = new ArrayDeque<TraversalFrame>();
        pending.push(new TraversalFrame(cosDocument.getTrailer(), 1));
        Iterator<Map.Entry<COSObjectKey, Long>> xrefRoots =
                cosDocument.getXrefTable().entrySet().iterator();

        IdentityHashMap<COSBase, Integer> maximumExpandedDepth =
                new IdentityHashMap<COSBase, Integer>();
        IdentityHashMap<COSBase, Boolean> activePath =
                new IdentityHashMap<COSBase, Boolean>();
        while (!pending.isEmpty() || xrefRoots.hasNext()) {
            resources.checkpoint();
            if (pending.isEmpty()) {
                COSObject object = cosDocument.getObjectFromPool(
                        xrefRoots.next().getKey());
                resources.observeObject(object);
                pending.push(new TraversalFrame(object, 1));
            }
            TraversalFrame frame = pending.peek();
            if (!frame.entered) {
                frame.entered = true;
                COSBase value = frame.value;
                if (value == null) {
                    pending.pop();
                    continue;
                }
                boolean expandable = value instanceof COSObject
                        || value instanceof COSArray
                        || value instanceof COSDictionary;
                if (!expandable) {
                    pending.pop();
                    continue;
                }
                resources.requireNestingDepth(frame.depth);
                if (activePath.containsKey(value)) {
                    pending.pop();
                    continue;
                }
                Integer previousDepth = maximumExpandedDepth.get(value);
                if (previousDepth != null
                        && previousDepth.intValue() >= frame.depth) {
                    pending.pop();
                    continue;
                }
                maximumExpandedDepth.put(value, Integer.valueOf(frame.depth));
                activePath.put(value, Boolean.TRUE);
                frame.expanded = true;
                if (value instanceof COSObject) {
                    frame.childDepth = nextDepth(frame.depth);
                    COSObject object = (COSObject) value;
                    resources.observeObject(object);
                    frame.children = Collections.singletonList(
                            object.getObject()).iterator();
                } else if (value instanceof COSArray) {
                    frame.childDepth = nextDepth(frame.depth);
                    frame.children = ((COSArray) value).iterator();
                } else if (value instanceof COSDictionary) {
                    frame.childDepth = nextDepth(frame.depth);
                    COSDictionary dictionary = (COSDictionary) value;
                    if (dictionary instanceof COSStream) {
                        auditStream((COSStream) dictionary, resources);
                    }
                    frame.children = dictionary.getValues().iterator();
                } else {
                    pending.pop();
                    continue;
                }
            }
            if (frame.children.hasNext()) {
                pending.push(new TraversalFrame(
                        frame.children.next(),
                        frame.childDepth));
            } else {
                pending.pop();
                if (frame.expanded) {
                    activePath.remove(frame.value);
                }
            }
        }
    }

    private static void auditStream(
            COSStream stream,
            WorkflowResourceContext resources)
            throws DocumentFailure, PreflightResourceIOException {
        resources.checkpoint();
        if (!resources.markStreamPreflighted(stream)) {
            return;
        }
        try {
            preflightFilterStages(stream, resources);
        } catch (PreflightResourceIOException failure) {
            throw failure;
        } catch (IOException | RuntimeException malformed) {
            PreflightResourceIOException exhausted =
                    wrappedResourceOrTerminalFailure(
                            malformed, resources);
            if (exhausted != null) {
                throw exhausted;
            }
            // Format and operation adapters retain their more specific
            // failure semantics for malformed or unsupported declarations.
        }
    }

    private static int nextDepth(int depth) throws DocumentFailure {
        if (depth == Integer.MAX_VALUE) {
            throw new DocumentFailure(
                    DocumentFailureCode.NESTING_LIMIT_EXCEEDED,
                    WorkflowResourceContext.CAPABILITY_ID,
                    "The workflow nesting-depth limit was exceeded.");
        }
        return depth + 1;
    }

    private static void preflightFilterStages(
            COSStream stream,
            WorkflowResourceContext resources)
            throws IOException, DocumentFailure {
        COSBase filterDeclaration = stream.getFilters();
        int filterCount = filterCount(filterDeclaration);
        if (filterCount == 0) {
            return;
        }

        decodeFilterStages(
                stream,
                filterDeclaration,
                filterCount,
                resources,
                new CountingDiscardOutputStream());
    }

    /** Decodes one stream while charging every declared filter-stage output. */
    static void decodeStream(
            COSStream stream,
            WorkflowResourceContext resources,
            OutputStream output) throws IOException, DocumentFailure {
        COSBase filterDeclaration = stream.getFilters();
        int filterCount = filterCount(filterDeclaration);
        try {
            if (filterCount == 0) {
                copyUnfiltered(stream, resources, output);
            } else {
                decodeFilterStages(
                        stream,
                        filterDeclaration,
                        filterCount,
                        resources,
                        output);
            }
        } catch (PreflightResourceIOException failure) {
            throw failure.failure;
        } catch (IOException | RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw failure;
        }
    }

    private static void copyUnfiltered(
            COSStream stream,
            WorkflowResourceContext resources,
            OutputStream output) throws IOException {
        try (InputStream input = resources.checkpointedInput(
                stream.createRawInputStream())) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        }
    }

    private static void decodeFilterStages(
            COSStream stream,
            COSBase filterDeclaration,
            int filterCount,
            WorkflowResourceContext resources,
            OutputStream finalOutput)
            throws IOException, DocumentFailure {

        InputStream input = null;
        Path previousStage = null;
        Path pendingStage = null;
        try {
            input = resources.checkpointedInput(
                    stream.createRawInputStream());
            for (int index = 0; index < filterCount; index++) {
                resources.checkpoint();
                Filter filter = filterAt(filterDeclaration, index);
                boolean finalStage = index == filterCount - 1;
                Path outputStage = null;
                OutputStream destination;
                if (finalStage) {
                    destination = new CountingFilterOutputStream(
                            resources,
                            new NonClosingOutputStream(finalOutput));
                } else {
                    outputStage = resources.createTemporaryFile(
                            ".filter-stage-",
                            ".bin");
                    pendingStage = outputStage;
                    destination = new CountingFilterOutputStream(
                            resources,
                            resources.openTemporaryOutput(outputStage));
                }
                try {
                    filter.decode(
                            input,
                            destination,
                            stream,
                            index,
                            DecodeOptions.DEFAULT);
                } finally {
                    try {
                        destination.close();
                    } finally {
                        input.close();
                    }
                }
                resources.releaseTemporaryFile(previousStage);
                previousStage = outputStage;
                pendingStage = null;
                if (!finalStage) {
                    input = resources.checkpointedInput(
                            Files.newInputStream(outputStage));
                } else {
                    input = null;
                }
            }
        } catch (PreflightResourceIOException failure) {
            throw failure;
        } catch (IOException failure) {
            PreflightResourceIOException exhausted =
                    wrappedResourceOrTerminalFailure(failure, resources);
            if (exhausted != null) {
                throw exhausted;
            }
            throw failure;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                    // The primary failure remains authoritative.
                }
            }
            resources.releaseTemporaryFile(pendingStage);
            resources.releaseTemporaryFile(previousStage);
        }
    }

    private static int filterCount(COSBase declaration) throws IOException {
        if (declaration == null) {
            return 0;
        }
        if (declaration instanceof COSName) {
            return 1;
        }
        if (!(declaration instanceof COSArray)) {
            throw new IOException("Stream Filter declaration is malformed");
        }
        return ((COSArray) declaration).size();
    }

    private static PreflightResourceIOException
            wrappedResourceOrTerminalFailure(
                    Throwable failure,
                    WorkflowResourceContext resources) {
        DocumentFailure resourceFailure =
                WorkflowResourceContext.findResourceFailure(failure);
        if (resourceFailure != null) {
            return new PreflightResourceIOException(resourceFailure);
        }
        try {
            resources.rethrowTerminalFailure();
            return null;
        } catch (DocumentFailure terminalFailure) {
            return new PreflightResourceIOException(terminalFailure);
        }
    }

    private static Filter filterAt(COSBase declaration, int index)
            throws IOException {
        COSBase filter = declaration instanceof COSName
                ? declaration : ((COSArray) declaration).getObject(index);
        if (!(filter instanceof COSName)) {
            throw new IOException("Stream Filter entry is malformed");
        }
        return FilterFactory.INSTANCE.getFilter((COSName) filter);
    }

    private static final class TraversalFrame {

        private final COSBase value;
        private final int depth;
        private boolean entered;
        private boolean expanded;
        private int childDepth;
        private Iterator<COSBase> children;

        private TraversalFrame(COSBase value, int depth) {
            this.value = value;
            this.depth = depth;
        }
    }

    private static final class PreflightResourceIOException
            extends IOException {

        private static final long serialVersionUID = 1L;
        private final DocumentFailure failure;

        private PreflightResourceIOException(DocumentFailure failure) {
            super(failure.getDiagnostic());
            this.failure = failure;
        }
    }

    private static final class CountingDiscardOutputStream
            extends OutputStream {

        private long count;

        @Override
        public void write(int value) throws IOException {
            add(1L);
        }

        @Override
        public void write(byte[] bytes, int offset, int length)
                throws IOException {
            add(length);
        }

        private void add(long amount) throws IOException {
            if (amount < 0L || count > Long.MAX_VALUE - amount) {
                throw new IOException("Decoded stream length overflowed");
            }
            count += amount;
        }

        private long count() {
            return count;
        }
    }

    private static final class NonClosingOutputStream
            extends FilterOutputStream {

        private NonClosingOutputStream(OutputStream output) {
            super(output);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }

    private static final class CountingFilterOutputStream
            extends FilterOutputStream {

        private final WorkflowResourceContext resources;

        private CountingFilterOutputStream(
                WorkflowResourceContext resources,
                OutputStream output) {
            super(output);
            this.resources = resources;
        }

        @Override
        public void write(int value) throws IOException {
            account(1);
            out.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length)
                throws IOException {
            account(length);
            resources.writeBytesAsIOException(out, bytes, offset, length);
        }

        private void account(int length) throws IOException {
            try {
                resources.consumeDecompressedBytes(length);
            } catch (DocumentFailure failure) {
                throw new PreflightResourceIOException(failure);
            }
        }
    }
}
