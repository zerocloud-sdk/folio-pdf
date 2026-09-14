package net.zerocloud.pdf;

import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zerocloud.pdf.query.ExtractTextAndStructure;
import org.apache.fontbox.cmap.CMap;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.MissingOperandException;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.OperatorName;
import org.apache.pdfbox.contentstream.operator.OperatorProcessor;
import org.apache.pdfbox.contentstream.operator.markedcontent.BeginMarkedContentSequence;
import org.apache.pdfbox.contentstream.operator.markedcontent.BeginMarkedContentSequenceWithProperties;
import org.apache.pdfbox.contentstream.operator.markedcontent.EndMarkedContentSequence;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;
import org.apache.pdfbox.contentstream.operator.text.BeginText;
import org.apache.pdfbox.contentstream.operator.text.EndText;
import org.apache.pdfbox.contentstream.operator.text.MoveText;
import org.apache.pdfbox.contentstream.operator.text.MoveTextSetLeading;
import org.apache.pdfbox.contentstream.operator.text.NextLine;
import org.apache.pdfbox.contentstream.operator.text.SetCharSpacing;
import org.apache.pdfbox.contentstream.operator.text.SetFontAndSize;
import org.apache.pdfbox.contentstream.operator.text.SetTextHorizontalScaling;
import org.apache.pdfbox.contentstream.operator.text.SetTextLeading;
import org.apache.pdfbox.contentstream.operator.text.SetTextRenderingMode;
import org.apache.pdfbox.contentstream.operator.text.SetTextRise;
import org.apache.pdfbox.contentstream.operator.text.SetWordSpacing;
import org.apache.pdfbox.contentstream.operator.text.ShowText;
import org.apache.pdfbox.contentstream.operator.text.ShowTextAdjusted;
import org.apache.pdfbox.contentstream.operator.text.ShowTextLine;
import org.apache.pdfbox.contentstream.operator.text.ShowTextLineAndSpace;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontFactory;
import org.apache.pdfbox.pdmodel.font.PDSimpleFont;
import org.apache.pdfbox.pdmodel.font.PDType3Font;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.encoding.Encoding;
import org.apache.pdfbox.pdmodel.font.encoding.GlyphList;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDTransparencyGroup;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;

final class PdfBoxTextStructureExtractionOperations {

    static final String CAPABILITY_ID = "document.text-structure.extract";

    // ISO 32000-1 Table 118; resources remain in the pinned FontBox package.
    private static final Set<String> BUNDLED_ENCODING_CMAPS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(
            "83pv-RKSJ-H", "90ms-RKSJ-H", "90ms-RKSJ-V", "90msp-RKSJ-H",
            "90msp-RKSJ-V", "90pv-RKSJ-H", "Add-RKSJ-H", "Add-RKSJ-V",
            "B5pc-H", "B5pc-V", "CNS-EUC-H", "CNS-EUC-V",
            "ETen-B5-H", "ETen-B5-V", "ETenms-B5-H", "ETenms-B5-V",
            "EUC-H", "EUC-V", "Ext-RKSJ-H", "Ext-RKSJ-V",
            "GB-EUC-H", "GB-EUC-V", "GBK-EUC-H", "GBK-EUC-V",
            "GBK2K-H", "GBK2K-V", "GBKp-EUC-H", "GBKp-EUC-V",
            "GBpc-EUC-H", "GBpc-EUC-V", "H", "HKscs-B5-H",
            "HKscs-B5-V", "Identity-H", "Identity-V", "KSC-EUC-H",
            "KSC-EUC-V", "KSCms-UHC-H", "KSCms-UHC-HW-H", "KSCms-UHC-HW-V",
            "KSCms-UHC-V", "KSCpc-EUC-H", "UniCNS-UCS2-H", "UniCNS-UCS2-V",
            "UniCNS-UTF16-H", "UniCNS-UTF16-V", "UniGB-UCS2-H", "UniGB-UCS2-V",
            "UniGB-UTF16-H", "UniGB-UTF16-V", "UniJIS-UCS2-H", "UniJIS-UCS2-HW-H",
            "UniJIS-UCS2-HW-V", "UniJIS-UCS2-V", "UniJIS-UTF16-H", "UniJIS-UTF16-V",
            "UniKS-UCS2-H", "UniKS-UCS2-V", "UniKS-UTF16-H", "UniKS-UTF16-V",
            "V")));

    private final PDDocument document;
    private final PdfBoxValueAdapter valueAdapter;
    private final WorkflowResourceContext resources;

    PdfBoxTextStructureExtractionOperations(
            PDDocument document,
            PdfBoxValueAdapter valueAdapter,
            WorkflowResourceContext resources) {
        this.document = document;
        this.valueAdapter = valueAdapter;
        this.resources = resources;
    }

    boolean supportsQuery(DocumentQuery<?> query) {
        return query instanceof ExtractTextAndStructure;
    }

    TextStructureExtraction evaluate(ExtractTextAndStructure query)
            throws DocumentFailure {
        resources.checkpoint();
        ExtractionState state = new ExtractionState(
                query.getLimits(),
                resources, document.getVersion() >= 2f);
        boolean completed = false;
        try {
            COSBase pageTree = document.getDocumentCatalog()
                    .getCOSObject().getDictionaryObject(COSName.PAGES);
            List<PdfBoxPageTreePreflight.PageView> pageViews =
                    state.pageViews(pageTree);
            List<PageText> pages = new ArrayList<PageText>(
                    pageViews.size());
            List<COSDictionary> pageDictionaries =
                    new ArrayList<COSDictionary>(pageViews.size());
            for (int index = 0; index < pageViews.size(); index++) {
                resources.checkpoint();
                PdfBoxPageTreePreflight.PageView pageView =
                        pageViews.get(index);
                pageDictionaries.add(pageView.source());
                PDPage page = new PDPage(pageView.effective());
                state.accountPageStreams(page);
                try (PageEngine engine = new PageEngine(index + 1, state)) {
                    engine.processPage(page);
                    pages.add(engine.result(page));
                }
            }
            List<LogicalStructureElement> structureRoots =
                    new StructureExtractor(
                            document, valueAdapter, state, pages, pageDictionaries).extract();
            TextStructureExtraction result = new TextStructureExtraction(
                    pages,
                    structureRoots,
                    state.diagnostics);
            completed = true;
            return result;
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (ExtractionLimitException
                | ExtractionLimitRuntimeException exhausted) {
            resources.rethrowTerminalFailure();
            throw failure(
                    DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED,
                    "The text and logical-structure extraction limit was exceeded.");
        } catch (ExtractionMalformedRuntimeException malformed) {
            resources.rethrowTerminalFailure();
            throw failure(
                    DocumentFailureCode.QUERY_FAILED,
                    "The document text and logical structure could not be extracted safely.");
        } catch (IOException malformed) {
            resources.rethrowResourceOrTerminalFailure(malformed);
            throw failure(
                    DocumentFailureCode.QUERY_FAILED,
                    "The document text and logical structure could not be extracted safely.");
        } catch (RuntimeException malformed) {
            resources.rethrowResourceOrTerminalFailure(malformed);
            throw failure(
                    DocumentFailureCode.QUERY_FAILED,
                    "The document text and logical structure could not be extracted safely.");
        } finally {
            state.releaseProvisionalMemory(completed);
        }
    }

    private static final class ReferencedSequence {

        private final Integer parentIndex;
        private final COSName propertyName;
        private final Integer markedContentId;

        ReferencedSequence(Integer parentIndex, COSName propertyName, Integer markedContentId) {
            this.parentIndex = parentIndex;
            this.propertyName = propertyName;
            this.markedContentId = markedContentId;
        }
    }

    private static final class ReferencedInvocation {

        private final COSName resourceName;
        private final Integer parentIndex;

        ReferencedInvocation(COSName resourceName, Integer parentIndex) {
            this.resourceName = resourceName;
            this.parentIndex = parentIndex;
        }
    }

    private static final class ExtractionState {

        private final ExtractionLimits limits;
        private final WorkflowResourceContext resources;
        private final List<ExtractionDiagnostic> diagnostics =
                new ArrayList<ExtractionDiagnostic>();
        private int contentStreams;
        private long decodedBytes;
        private int textItems;
        private int unicodeCodePoints;
        private int toUnicodeMappings;
        private int fontDataEntries;
        private int markedContentSequences;
        private int structureElements;
        private int structureItems;
        private int roleMappings;
        private final IdentityHashMap<COSStream, Integer> contentStreamIds =
                new IdentityHashMap<COSStream, Integer>();
        private final IdentityHashMap<COSStream, Integer> formInvocations =
                new IdentityHashMap<COSStream, Integer>();
        private final IdentityHashMap<COSStream, List<ReferencedSequence>> referencedSequences =
                new IdentityHashMap<COSStream, List<ReferencedSequence>>();
        private final IdentityHashMap<COSStream, List<ReferencedInvocation>> referencedInvocations =
                new IdentityHashMap<COSStream, List<ReferencedInvocation>>();
        private final IdentityHashMap<COSStream, Set<Integer>> renderedObjectPages =
                new IdentityHashMap<COSStream, Set<Integer>>();
        private final Set<Long> sequencesOverlappingStructuralObjects = new HashSet<Long>();
        private final IdentityHashMap<COSDictionary, List<PdfBoxCMapPreflight.UnicodeMappings>> explicitCMaps =
                new IdentityHashMap<COSDictionary, List<PdfBoxCMapPreflight.UnicodeMappings>>();
        private final IdentityHashMap<COSDictionary, COSDictionary> fontSources =
                new IdentityHashMap<COSDictionary, COSDictionary>();
        private final IdentityHashMap<COSDictionary, Boolean> fontsInspected =
                new IdentityHashMap<COSDictionary, Boolean>();
        private final IdentityHashMap<COSDictionary, Integer> fontMetricEntryCounts =
                new IdentityHashMap<COSDictionary, Integer>();
        private final IdentityHashMap<COSDictionary, DeclaredEncoding>
                fontEncodings =
                        new IdentityHashMap<COSDictionary, DeclaredEncoding>();
        private final IdentityHashMap<COSDictionary, DeclaredEncoding>
                encodingDictionaries =
                        new IdentityHashMap<COSDictionary, DeclaredEncoding>();
        private final IdentityHashMap<COSArray, String[]> differenceArrays =
                new IdentityHashMap<COSArray, String[]>();
        private final IdentityHashMap<COSStream, Boolean> fontDataInspected =
                new IdentityHashMap<COSStream, Boolean>();
        private final IdentityHashMap<COSStream, byte[]> fontDataHeaders =
                new IdentityHashMap<COSStream, byte[]>();
        private final IdentityHashMap<COSStream, Float> type3GlyphWidths =
                new IdentityHashMap<COSStream, Float>();
        private final IdentityHashMap<COSDictionary, PDFont> boundedFonts =
                new IdentityHashMap<COSDictionary, PDFont>();
        private final IdentityHashMap<COSStream, Boolean>
                contentStreamsInspected =
                        new IdentityHashMap<COSStream, Boolean>();
        private long fontHeaderBytes;
        private long resultSourceCodeBytes;
        private long resultTextBytes;
        private final boolean pdfTwo;
        private final IdentityHashMap<COSDictionary, EncodingPlan> encodingPlans =
                new IdentityHashMap<COSDictionary, EncodingPlan>();
        private COSStream backendEncodingStream;
        private RandomAccessStreamCache backendEncodingCache;

        ExtractionState(
                ExtractionLimits limits,
                WorkflowResourceContext resources,
                boolean pdfTwo) {
            this.limits = limits;
            this.resources = resources;
            this.pdfTwo = pdfTwo;
        }

        List<PdfBoxPageTreePreflight.PageView> pageViews(COSBase value)
                throws IOException, DocumentFailure {
            if (!(value instanceof COSDictionary)
                    || value instanceof COSStream) {
                throw new IOException("Page-tree root is malformed");
            }
            try {
                return PdfBoxPageTreePreflight.pages(
                        (COSDictionary) value,
                        limits.getMaximumPages(),
                        limits.getMaximumPageTreeNodes(),
                        resources);
            } catch (PdfBoxPageTreePreflight.LimitExceededException
                    exhausted) {
                throw new ExtractionLimitException();
            }
        }

        void accountPageStreams(PDPage page) throws IOException {
            COSBase value = page.getCOSObject().getDictionaryObject(
                    COSName.CONTENTS);
            if (value == null) {
                return;
            }
            if (value instanceof COSStream) {
                accountAndValidateStream(
                        new PDStream((COSStream) value), 1);
                return;
            }
            if (!(value instanceof COSArray)) {
                throw new IOException("Page Contents is malformed");
            }
            COSArray streams = (COSArray) value;
            if (streams.size() > limits.getMaximumContentStreams()
                    - contentStreams) {
                throw new ExtractionLimitException();
            }
            try (WorkflowResourceContext.OwnedByteAccumulator combined =
                    resources.ownedByteAccumulator()) {
                for (int index = 0; index < streams.size(); index++) {
                    COSBase stream = streams.getObject(index);
                    if (!(stream instanceof COSStream)) {
                        throw new IOException(
                                "Page Contents array member is not a stream");
                    }
                    accountStreamOccurrence(1);
                    try (WorkflowResourceContext.OwnedBytes content =
                            decodedBytes((COSStream) stream)) {
                        byte[] bytes = content.getBytes();
                        combined.write(bytes, 0, bytes.length);
                    }
                    combined.write('\n');
                }
                try (WorkflowResourceContext.OwnedBytes content =
                        combined.finishWorkingAsIOException()) {
                    PdfBoxContentStreamPreflight.validate(
                            content.getBytes(), resources);
                }
            }
        }

        void accountFormStream(PDFormXObject form, int depth)
                throws IOException {
            accountAndValidateStream(form.getContentStream(), depth);
            COSStream stream = form.getCOSObject();
            Integer previous = formInvocations.get(stream);
            formInvocations.put(stream, Integer.valueOf(previous == null ? 1 : previous.intValue() + 1));
        }

        private void accountAndValidateStream(PDStream stream, int depth)
                throws IOException {
            accountStreamOccurrence(depth);
            COSStream dictionary = stream.getCOSObject();
            if (contentStreamsInspected.containsKey(dictionary)) {
                accountDecodedStream(stream);
                return;
            }
            try (WorkflowResourceContext.OwnedBytes content =
                    decodedBytes(dictionary)) {
                PdfBoxContentStreamPreflight.validate(
                        content.getBytes(), resources);
            }
            contentStreamsInspected.put(dictionary, Boolean.TRUE);
        }

        private void accountStreamOccurrence(int depth)
                throws IOException {
            resources.checkpointAsIOException();
            resources.requireNestingDepthAsIOException(depth);
            if (depth > limits.getMaximumContentStreamDepth()) {
                throw new ExtractionLimitException();
            }
            if (contentStreams >= limits.getMaximumContentStreams()) {
                throw new ExtractionLimitException();
            }
            contentStreams++;
        }

        List<ReferencedSequence> readReferencedSequences(COSStream stream) throws IOException {
            List<ReferencedSequence> sequences = referencedSequences.get(stream);
            if (sequences == null) {
                sequences = new ArrayList<ReferencedSequence>();
                List<ReferencedInvocation> invocations = new ArrayList<ReferencedInvocation>();
                try (WorkflowResourceContext.OwnedBytes bytes = decodedBytes(stream)) {
                    PdfBoxContentStreamPreflight.validate(bytes.getBytes(), resources);
                    PDFStreamParser parser = new PDFStreamParser(bytes.getBytes());
                    PageEngine.OperatorBalance balance = new PageEngine.OperatorBalance(resources);
                    List<COSBase> operands = new ArrayList<COSBase>();
                    Deque<Integer> active = new ArrayDeque<Integer>();
                    try {
                        Object token;
                        while ((token = parser.parseNextToken()) != null) {
                            resources.checkpointAsIOException();
                            if (!(token instanceof Operator)) {
                                if (!(token instanceof COSBase)) {
                                    throw new IOException("Referenced Form operand is malformed");
                                }
                                operands.add((COSBase) token);
                                continue;
                            }
                            String operator = ((Operator) token).getName();
                            PageEngine.validateSupportedOperands(operator, operands, resources);
                            balance.accept(operator);
                            if (OperatorName.BEGIN_MARKED_CONTENT.equals(operator)
                                    || OperatorName.BEGIN_MARKED_CONTENT_SEQ.equals(operator)) {
                                nextMarkedContentSequence();
                                requireMarkedContentDepth(balance.markedContentDepth);
                                COSName propertyName = null;
                                Integer identifier = null;
                                if (OperatorName.BEGIN_MARKED_CONTENT_SEQ.equals(operator)) {
                                    COSBase property = operands.get(1);
                                    if (property instanceof COSName) {
                                        propertyName = (COSName) property;
                                    } else {
                                        identifier = optionalNonNegativeInteger(
                                                requiredDictionary(property), COSName.MCID);
                                    }
                                }
                                sequences.add(new ReferencedSequence(active.peekLast(), propertyName, identifier));
                                active.addLast(Integer.valueOf(sequences.size() - 1));
                            } else if (OperatorName.END_MARKED_CONTENT.equals(operator)) {
                                active.removeLast();
                            } else if (OperatorName.DRAW_OBJECT.equals(operator)) {
                                nextStructureItem();
                                invocations.add(new ReferencedInvocation((COSName) operands.get(0), active.peekLast()));
                            }
                            operands.clear();
                        }
                        balance.requireBalanced();
                    } finally {
                        parser.close();
                    }
                }
                referencedSequences.put(stream, sequences);
                referencedInvocations.put(stream, invocations);
            }
            return sequences;
        }

        int requireReferencedMcid(COSStream stream, int mcid, COSDictionary inheritedResources)
                throws IOException {
            Integer selected = referencedMcidDefinitions(stream, inheritedResources).get(Integer.valueOf(mcid));
            if (selected == null) {
                throw new IOException("Referenced Form does not define the MCR's MCID");
            }
            return selected.intValue();
        }

        Map<Integer, Integer> referencedMcidDefinitions(COSStream stream, COSDictionary inheritedResources)
                throws IOException {
            List<ReferencedSequence> sequences = readReferencedSequences(stream);
            COSBase declaredResources = stream.getDictionaryObject(COSName.RESOURCES);
            COSDictionary effectiveResources = declaredResources == null
                    ? inheritedResources : requiredDictionary(declaredResources);
            Map<Integer, Integer> definitions = new HashMap<Integer, Integer>();
            for (int index = 0; index < sequences.size(); index++) {
                resources.checkpointAsIOException();
                ReferencedSequence sequence = sequences.get(index);
                Integer defined = sequence.markedContentId;
                if (sequence.propertyName != null) {
                    if (effectiveResources == null) {
                        throw new IOException("Referenced Form property has no resources");
                    }
                    COSDictionary named = requiredDictionary(
                            effectiveResources.getDictionaryObject(COSName.PROPERTIES));
                    defined = optionalNonNegativeInteger(requiredDictionary(
                            named.getDictionaryObject(sequence.propertyName)), COSName.MCID);
                }
                if (defined != null && definitions.put(defined, Integer.valueOf(index)) != null) {
                    throw new IOException("Referenced Form has duplicate MCID definitions");
                }
            }
            return definitions;
        }

        private void accountDecodedStream(PDStream stream)
                throws IOException {
            resources.decodeStreamAsIOException(
                    stream.getCOSObject(),
                    new AccountedDecodedOutput(null, null));
        }

        private void accountDecodedBytes(int count)
                throws ExtractionLimitException {
            if (count < 0
                    || decodedBytes
                    > limits.getMaximumDecodedBytes() - count) {
                throw new ExtractionLimitException();
            }
            decodedBytes += count;
        }

        void inspectFontDictionary(COSDictionary dictionary)
                throws IOException {
            inspectFontDictionary(dictionary, false);
        }

        private void inspectFontDictionary(
                COSDictionary dictionary,
                boolean descendant) throws IOException {
            validateFontKind(dictionary, descendant);
            if (!fontsInspected.containsKey(dictionary)) {
                fontsInspected.put(dictionary, Boolean.TRUE);
                inspectFontInputs(dictionary);
                fontEncodings.put(dictionary, readDeclaredEncoding(dictionary));
                COSBase value = dictionary.getDictionaryObject(
                        COSName.TO_UNICODE);
                if (value != null) {
                    if (!(value instanceof COSStream)) {
                        throw new IOException("ToUnicode is not a stream");
                    }
                    inspectToUnicode(dictionary, (COSStream) value);
                }
            } else if (descendant) {
                // Validation may be shared, but every newly selected Type0
                // font constructs its own descendant metric tables.
                accountFontDataEntries(fontMetricEntryCounts.get(dictionary).intValue());
            }
        }

        private void inspectToUnicode(COSDictionary dictionary, COSStream root) throws IOException {
            List<PdfBoxCMapPreflight.UnicodeMappings> layers =
                    new ArrayList<PdfBoxCMapPreflight.UnicodeMappings>();
            explicitCMaps.put(dictionary, layers);
            IdentityHashMap<COSStream, Boolean> visited = new IdentityHashMap<COSStream, Boolean>();
            Set<COSName> visitedNames = new HashSet<COSName>();
            COSBase current = root;
            String expectedName = null;
            while (current != null) {
                resources.checkpointAsIOException();
                COSStream stream = current instanceof COSStream ? (COSStream) current : null;
                COSName bundled = current instanceof COSName ? (COSName) current : null;
                if (stream == null && (bundled == null || !isBundledUnicodeCMap(bundled))) {
                    throw new IOException("ToUnicode inheritance requires a stream or supported bundled name");
                }
                if (stream != null ? visited.containsKey(stream) : visitedNames.contains(bundled)) {
                    throw new IOException("Cyclic ToUnicode inheritance");
                }
                accountFontDataEntries(1);
                resources.requireNestingDepthAsIOException(layers.size() + 1L);
                if (stream != null) {
                    visited.put(stream, Boolean.TRUE);
                } else {
                    visitedNames.add(bundled);
                }
                COSBase parent = stream == null ? null
                        : stream.getDictionaryObject(COSName.getPDFName("UseCMap"));
                try (WorkflowResourceContext.OwnedBytes decoded = stream == null
                        ? bundledCMapBytes(bundled) : decodedBytes(stream)) {
                    PdfBoxCMapPreflight.Inspection inspection;
                    try {
                        inspection = PdfBoxCMapPreflight.parseToUnicode(decoded.getBytes(),
                                limits.getMaximumToUnicodeMappings() - toUnicodeMappings,
                                limits.getMaximumFontDataEntries() - fontDataEntries, resources, bundled != null);
                    } catch (PdfBoxCMapPreflight.LimitExceededException exhausted) {
                        throw new ExtractionLimitException();
                    }
                    layers.add(inspection.unicode);
                    if (pdfTwo && stream != null) {
                        COSBase type = stream.getDictionaryObject(COSName.TYPE);
                        COSBase name = stream.getDictionaryObject(COSName.getPDFName("CMapName"));
                        if (type != null && !COSName.getPDFName("CMap").equals(type)) {
                            throw new IOException("ToUnicode stream Type is not CMap");
                        }
                        if (name != null && (!(name instanceof COSName)
                                || !((COSName) name).getName().equals(inspection.unicode.name))) {
                            throw new IOException("ToUnicode stream name disagrees with its program");
                        }
                        COSBase mode = stream.getDictionaryObject(COSName.WMODE);
                        if (mode != null && (!(mode instanceof COSInteger)
                                || ((COSInteger) mode).longValue() != inspection.unicode.writingMode)) {
                            throw new IOException("ToUnicode stream WMode disagrees with its program");
                        }
                        COSBase collection = stream.getDictionaryObject(COSName.CIDSYSTEMINFO);
                        if (collection != null) {
                            if (!(collection instanceof COSDictionary) || collection instanceof COSStream) {
                                throw new IOException("ToUnicode stream CIDSystemInfo is not a dictionary");
                            }
                            COSDictionary info = (COSDictionary) collection;
                            COSBase registry = info.getDictionaryObject(COSName.REGISTRY);
                            COSBase ordering = info.getDictionaryObject(COSName.ORDERING);
                            COSBase supplement = info.getDictionaryObject(COSName.SUPPLEMENT);
                            if (!(registry instanceof COSString) || !(ordering instanceof COSString)
                                    || !(supplement instanceof COSInteger) || inspection.unicode.registry == null
                                    || !matchesLatin1(inspection.unicode.registry, (COSString) registry)
                                    || !matchesLatin1(inspection.unicode.ordering, (COSString) ordering)
                                    || ((COSInteger) supplement).longValue() != inspection.unicode.supplement.intValue()) {
                                throw new IOException("ToUnicode stream CIDSystemInfo disagrees with its program");
                            }
                        }
                    }
                    if ((expectedName != null && !expectedName.equals(inspection.unicode.name))
                            || (bundled != null && !bundled.getName().equals(inspection.unicode.name))) {
                        throw new IOException("ToUnicode usecmap does not identify its declared parent");
                    }
                    expectedName = inspection.unicode.parentName;
                    if (stream == null && expectedName != null) {
                        parent = COSName.getPDFName(expectedName);
                    }
                    if (expectedName != null && parent == null) {
                        throw new IOException("Textual ToUnicode usecmap has no declared parent");
                    }
                    if (inspection.hasCodespaces && parent != null) {
                        throw new IOException("Inherited ToUnicode redefines its codespace");
                    }
                    accountToUnicodeMappings(inspection.mappings);
                    accountFontDataEntries(inspection.fontDataEntries);
                }
                current = parent;
            }
            PdfBoxCMapPreflight.validateGraph(layers, resources);
        }

        private boolean matchesLatin1(String expected, COSString actual) throws IOException {
            resources.checkpointAsIOException();
            // Use the backend view, as PdfBoxStringSupport does, instead of
            // allocating an unreserved defensive byte copy from getBytes().
            String hexadecimal = actual.toHexString();
            resources.checkpointAsIOException();
            if (2L * expected.length() != hexadecimal.length()) {
                return false;
            }
            for (int index = 0; index < expected.length(); index++) {
                int high = Character.digit(hexadecimal.charAt(2 * index), 16);
                int low = Character.digit(hexadecimal.charAt(2 * index + 1), 16);
                if (expected.charAt(index) != ((high << 4) | low)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean isBundledUnicodeCMap(COSName name) {
            String value = name.getName();
            return "Adobe-CNS1-UCS2".equals(value) || "Adobe-GB1-UCS2".equals(value)
                    || "Adobe-Japan1-UCS2".equals(value) || "Adobe-Korea1-UCS2".equals(value);
        }

        private WorkflowResourceContext.OwnedBytes bundledCMapBytes(COSName name) throws IOException {
            // Only fixed names admitted above can reach the pinned dependency's
            // resource package. No document name becomes a filesystem path or URI.
            try (InputStream input = CMap.class.getResourceAsStream(name.getName())) {
                if (input == null) {
                    throw new IOException("Bundled ToUnicode CMap is unavailable");
                }
                try (WorkflowResourceContext.OwnedByteAccumulator output = resources.ownedByteAccumulator();
                        WorkflowResourceContext.MemoryReservation scratch = resources.reserveOwnedMemoryAsIOException(8192L)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        resources.checkpointAsIOException();
                        accountDecodedBytes(count);
                        resources.consumeDecompressedBytesAsIOException(count);
                        output.write(buffer, 0, count);
                    }
                    return output.finishWorkingAsIOException();
                }
            }
        }

        private void inspectFontInputs(COSDictionary dictionary)
                throws IOException {
            COSName subtype = dictionary.getCOSName(COSName.SUBTYPE);

            int remaining = limits.getMaximumFontDataEntries()
                    - fontDataEntries;
            int metricEntries;
            try {
                metricEntries = PdfBoxFontMetricPreflight.countEntries(
                        dictionary, remaining, resources);
            } catch (PdfBoxFontMetricPreflight.LimitExceededException
                    exhausted) {
                throw new ExtractionLimitException();
            }
            accountFontDataEntries(metricEntries);
            fontMetricEntryCounts.put(dictionary, Integer.valueOf(metricEntries));
            inspectFontDescriptor(dictionary.getDictionaryObject(
                    COSName.FONT_DESC));
            inspectCidToGidMap(dictionary.getDictionaryObject(
                    COSName.CID_TO_GID_MAP));
            if (COSName.TYPE3.equals(subtype)) {
                inspectType3Font(dictionary);
            }

            if (COSName.TYPE0.equals(subtype)) {
                COSBase descendants = dictionary.getDictionaryObject(
                        COSName.DESCENDANT_FONTS);
                if (!(descendants instanceof COSArray)
                        || ((COSArray) descendants).size() != 1) {
                    throw new IOException(
                            "Type0 font must have one descendant font");
                }
                COSBase descendant = ((COSArray) descendants).getObject(0);
                if (!(descendant instanceof COSDictionary)
                        || descendant instanceof COSStream) {
                    throw new IOException(
                            "Type0 descendant font is malformed");
                }
                COSDictionary descendantDictionary =
                        (COSDictionary) descendant;
                COSName descendantType = descendantDictionary.getCOSName(
                        COSName.TYPE, COSName.FONT);
                COSName descendantSubtype = descendantDictionary.getCOSName(
                        COSName.SUBTYPE);
                if (!COSName.FONT.equals(descendantType)
                        || (!COSName.CID_FONT_TYPE0.equals(descendantSubtype)
                                && !COSName.CID_FONT_TYPE2.equals(
                                        descendantSubtype))) {
                    throw new IOException(
                            "Type0 descendant font subtype is unsupported");
                }
                inspectFontDictionary(descendantDictionary, true);
                rejectBackendSubtypeRepair(
                        dictionary,
                        descendantDictionary,
                        descendantSubtype);
                inspectEncoding(dictionary, descendantDictionary);
            }
        }

        private void inspectEncoding(COSDictionary font, COSDictionary descendant) throws IOException {
            COSBase collection = descendant.getDictionaryObject(COSName.CIDSYSTEMINFO);
            requireCharacterCollection(collection);
            EncodingPlan plan = new EncodingPlan(resources);
            encodingPlans.put(font, plan);
            IdentityHashMap<COSStream, Boolean> visited = new IdentityHashMap<COSStream, Boolean>();
            Set<COSName> visitedNames = new HashSet<COSName>();
            COSBase current = font.getDictionaryObject(COSName.ENCODING);
            if (current == null) {
                throw new IOException("Type0 Encoding is missing");
            }
            String expectedName = null;
            while (current != null) {
                resources.checkpointAsIOException();
                COSStream stream = current instanceof COSStream ? (COSStream) current : null;
                COSName named = current instanceof COSName ? (COSName) current : null;
                if (stream == null && (named == null || !isBundledEncodingCMap(named))) {
                    throw new IOException("Encoding requires a stream or supported bundled CMap");
                }
                if (stream != null ? visited.containsKey(stream) : visitedNames.contains(named)) {
                    throw new IOException("Cyclic Encoding CMap inheritance");
                }
                accountFontDataEntries(1);
                resources.requireNestingDepthAsIOException(plan.layers.size() + 1L);
                if (stream == null) {
                    visitedNames.add(named);
                } else {
                    visited.put(stream, Boolean.TRUE);
                }
                COSBase parent = stream == null ? null : stream.getDictionaryObject(COSName.getPDFName("UseCMap"));
                try (WorkflowResourceContext.OwnedBytes decoded =
                        stream == null ? bundledCMapBytes(named) : decodedBytes(stream)) {
                    PdfBoxCMapPreflight.Inspection inspection;
                    try {
                        inspection = PdfBoxCMapPreflight.parseEncoding(decoded.getBytes(),
                                limits.getMaximumFontDataEntries() - fontDataEntries, resources);
                    } catch (PdfBoxCMapPreflight.LimitExceededException exhausted) {
                        throw new ExtractionLimitException();
                    }
                    PdfBoxCMapPreflight.EncodingMappings layer = inspection.encoding;
                    plan.layers.add(layer);
                    if (layer.name == null || layer.registry == null || layer.cmapType == null) {
                        throw new IOException("Encoding CMap metadata is incomplete");
                    }
                    if (stream != null) {
                        validateEncodingHeaders(stream, layer);
                    }
                    if ((expectedName != null && !expectedName.equals(layer.name))
                            || (named != null && !named.getName().equals(layer.name))) {
                        throw new IOException("Encoding usecmap does not identify its parent");
                    }
                    expectedName = layer.parentName;
                    if (stream == null && expectedName != null) {
                        parent = COSName.getPDFName(expectedName);
                    }
                    if (expectedName != null && parent == null) {
                        throw new IOException("Textual Encoding usecmap has no declared parent");
                    }
                    if (inspection.hasCodespaces && parent != null) {
                        throw new IOException("Inherited Encoding redefines its codespace");
                    }
                    accountFontDataEntries(inspection.fontDataEntries);
                }
                current = parent;
            }
            plan.finish();
            PdfBoxCMapPreflight.EncodingMappings root = plan.layers.get(0);
            COSBase sourceEncoding = font.getDictionaryObject(COSName.ENCODING);
            if (!COSName.IDENTITY_H.equals(sourceEncoding) && !COSName.IDENTITY_V.equals(sourceEncoding)) {
                requireMatchingCollection((COSDictionary) collection, root, false);
            }
        }

        private static boolean isBundledEncodingCMap(COSName name) {
            return BUNDLED_ENCODING_CMAPS.contains(name.getName());
        }

        private void validateEncodingHeaders(COSStream stream, PdfBoxCMapPreflight.EncodingMappings program)
                throws IOException {
            if (!COSName.getPDFName("CMap").equals(stream.getDictionaryObject(COSName.TYPE))) {
                throw new IOException("Encoding stream Type is not CMap");
            }
            COSBase name = stream.getDictionaryObject(COSName.getPDFName("CMapName"));
            if (!(name instanceof COSName) || !program.name.equals(((COSName) name).getName())) {
                throw new IOException("Encoding stream name disagrees with its program");
            }
            COSBase mode = stream.getDictionaryObject(COSName.WMODE);
            if ((mode == null ? 0L : exactInteger(mode)) != program.writingMode) {
                throw new IOException("Encoding stream WMode disagrees with its program");
            }
            COSBase collection = stream.getDictionaryObject(COSName.CIDSYSTEMINFO);
            requireCharacterCollection(collection);
            requireMatchingCollection((COSDictionary) collection, program, true);
        }

        private static void requireCharacterCollection(COSBase value) throws IOException {
            COSDictionary dictionary = requiredDictionary(value);
            if (!(dictionary.getDictionaryObject(COSName.REGISTRY) instanceof COSString)
                    || !(dictionary.getDictionaryObject(COSName.ORDERING) instanceof COSString)
                    || exactInteger(dictionary.getDictionaryObject(COSName.SUPPLEMENT)) < 0
                    || exactInteger(dictionary.getDictionaryObject(COSName.SUPPLEMENT)) > Integer.MAX_VALUE) {
                throw new IOException("CIDSystemInfo is malformed");
            }
        }

        private static long exactInteger(COSBase value) throws IOException {
            if (!(value instanceof COSInteger)) {
                throw new IOException("Expected an integer");
            }
            return ((COSInteger) value).longValue();
        }

        private void requireMatchingCollection(COSDictionary dictionary, PdfBoxCMapPreflight.CMapProgram program,
                boolean exactSupplement)
                throws IOException {
            if (!matchesLatin1(program.registry, (COSString) dictionary.getDictionaryObject(COSName.REGISTRY))
                    || !matchesLatin1(program.ordering, (COSString) dictionary.getDictionaryObject(COSName.ORDERING))
                    || (exactSupplement && exactInteger(dictionary.getDictionaryObject(COSName.SUPPLEMENT))
                            != program.supplement.intValue())) {
                throw new IOException("CIDSystemInfo disagrees with the Encoding program");
            }
        }

        PDFont boundedFont(COSDictionary dictionary) throws IOException {
            boolean type3 = COSName.TYPE3.equals(dictionary.getDictionaryObject(COSName.SUBTYPE));
            boolean type0 = COSName.TYPE0.equals(dictionary.getDictionaryObject(COSName.SUBTYPE));
            if (!type3 && !type0 && !explicitCMaps.containsKey(dictionary)) {
                return null;
            }
            PDFont font = boundedFonts.get(dictionary);
            if (font == null) {
                COSDictionary construction = new COSDictionary(dictionary);
                construction.removeItem(COSName.TO_UNICODE);
                if (type0) {
                    COSDictionary descendant = new COSDictionary((COSDictionary)
                            ((COSArray) dictionary.getDictionaryObject(COSName.DESCENDANT_FONTS)).getObject(0));
                    // The fixed embedded encoding prevents global named CMap
                    // lookup. Omit ROS only in the detached backend view to
                    // prevent eager auxiliary collection/UCS2 CMap loading.
                    descendant.removeItem(COSName.CIDSYSTEMINFO);
                    COSDictionary descriptor = descendant.getCOSDictionary(COSName.FONT_DESC);
                    if (descriptor != null) {
                        descendant.setItem(COSName.FONT_DESC, new COSDictionary(descriptor));
                    }
                    COSArray descendants = new COSArray();
                    descendants.add(descendant);
                    construction.setItem(COSName.DESCENDANT_FONTS, descendants);
                    construction.setItem(COSName.ENCODING, backendEncoding());
                    font = new DeclaredType0Font(construction, encodingPlans.get(dictionary));
                } else {
                    font = type3 ? new DeclaredType3Font(construction) : PDFontFactory.createFont(construction);
                }
                fontSources.put(construction, dictionary);
                boundedFonts.put(dictionary, font);
            }
            return font;
        }

        private COSStream backendEncoding() throws IOException {
            if (backendEncodingStream == null) {
                backendEncodingCache = resources.streamCacheFactory().create();
                backendEncodingStream = new COSStream(backendEncodingCache);
                String program = "begincmap 1 begincodespacerange <0000> <FFFF> endcodespacerange "
                        + "1 begincidrange <0000> <FFFF> 0 endcidrange endcmap\n";
                try (OutputStream output = backendEncodingStream.createOutputStream()) {
                    for (int index = 0; index < program.length(); index++) {
                        output.write(program.charAt(index));
                    }
                }
            }
            return backendEncodingStream;
        }

        private void inspectType3Font(COSDictionary dictionary) throws IOException {
            BoundedDrawObject.requireNumberArray(dictionary, COSName.FONT_BBOX, 4, true);
            BoundedDrawObject.requireNumberArray(dictionary, COSName.FONT_MATRIX, 6, true);
            if (dictionary.getDictionaryObject(COSName.FIRST_CHAR) == null
                    || dictionary.getDictionaryObject(COSName.LAST_CHAR) == null
                    || dictionary.getDictionaryObject(COSName.WIDTHS) == null
                    || dictionary.getDictionaryObject(COSName.ENCODING) == null) {
                throw new IOException("Type3 font is missing declared metrics or encoding");
            }
            COSBase declaredResources = dictionary.getDictionaryObject(COSName.RESOURCES);
            if (declaredResources != null) {
                requiredDictionary(declaredResources);
            }
            COSDictionary procedures = requiredDictionary(dictionary.getDictionaryObject(COSName.CHAR_PROCS));
            for (COSName name : procedures.keySet()) {
                accountFontDataEntries(1);
                COSBase value = procedures.getDictionaryObject(name);
                if (!(value instanceof COSStream)) {
                    throw new IOException("Type3 CharProcs entry is not a stream");
                }
                COSStream stream = (COSStream) value;
                if (!type3GlyphWidths.containsKey(stream)) {
                    try (WorkflowResourceContext.OwnedBytes bytes = decodedBytes(stream)) {
                        PdfBoxContentStreamPreflight.validate(bytes.getBytes(), resources);
                        type3GlyphWidths.put(stream, Float.valueOf(readType3GlyphWidth(bytes.getBytes())));
                    }
                }
            }
            DeclaredEncoding encoding = readDeclaredEncoding(dictionary);
            COSArray widths = (COSArray) dictionary.getDictionaryObject(COSName.WIDTHS);
            int first = dictionary.getInt(COSName.FIRST_CHAR);
            for (int index = 0; index < widths.size(); index++) {
                resources.checkpointAsIOException();
                String name = encoding.glyphName(first + index);
                COSBase procedure = name == null ? null : procedures.getDictionaryObject(COSName.getPDFName(name));
                if (procedure instanceof COSStream && Float.compare(
                        type3GlyphWidths.get((COSStream) procedure).floatValue(),
                        ((COSNumber) widths.getObject(index)).floatValue()) != 0) {
                    throw new IOException("Type3 glyph metrics disagree with the declared width");
                }
            }
        }

        private float readType3GlyphWidth(byte[] bytes) throws IOException {
            PDFStreamParser parser = new PDFStreamParser(bytes);
            List<COSBase> operands = new ArrayList<COSBase>();
            try {
                Object token;
                while ((token = parser.parseNextToken()) != null) {
                    resources.checkpointAsIOException();
                    if (token instanceof Operator) {
                        String operator = ((Operator) token).getName();
                        if (!"d0".equals(operator) && !"d1".equals(operator)) {
                            throw new IOException("Type3 glyph does not begin with declared metrics");
                        }
                        PageEngine.requireFiniteNumbers(operands, "d0".equals(operator) ? 2 : 6, operator);
                        if (((COSNumber) operands.get(1)).floatValue() != 0f) {
                            throw new IOException("Type3 glyph has a nonzero vertical width");
                        }
                        return ((COSNumber) operands.get(0)).floatValue();
                    }
                    if (!(token instanceof COSNumber) || operands.size() >= 6) {
                        throw new IOException("Type3 glyph metrics are malformed");
                    }
                    operands.add((COSBase) token);
                }
            } finally {
                parser.close();
            }
            throw new IOException("Type3 glyph has no declared metrics");
        }

        private static void validateFontKind(
                COSDictionary dictionary,
                boolean descendant) throws IOException {
            if (!COSName.FONT.equals(
                    dictionary.getDictionaryObject(COSName.TYPE))) {
                throw new IOException("Font Type is malformed");
            }
            COSBase subtypeValue = dictionary.getDictionaryObject(
                    COSName.SUBTYPE);
            if (!(subtypeValue instanceof COSName)) {
                throw new IOException("Font Subtype is malformed");
            }
            COSName subtype = (COSName) subtypeValue;
            boolean supported = descendant
                    ? COSName.CID_FONT_TYPE0.equals(subtype)
                            || COSName.CID_FONT_TYPE2.equals(subtype)
                    : COSName.TYPE1.equals(subtype)
                            || COSName.MM_TYPE1.equals(subtype)
                            || COSName.TRUE_TYPE.equals(subtype)
                            || COSName.TYPE3.equals(subtype)
                            || COSName.TYPE0.equals(subtype);
            if (!supported) {
                throw new IOException("Font Subtype is outside version 1");
            }
        }

        private void rejectBackendSubtypeRepair(
                COSDictionary parent,
                COSDictionary descendant,
                COSName declaredSubtype) throws IOException {
            COSDictionary descriptor = parent.getCOSDictionary(
                    COSName.FONT_DESC);
            if (descriptor == null) {
                descriptor = descendant.getCOSDictionary(COSName.FONT_DESC);
            }
            if (descriptor == null) {
                return;
            }
            COSStream program = descriptor.getCOSStream(COSName.FONT_FILE);
            if (program == null) {
                program = descriptor.getCOSStream(COSName.FONT_FILE2);
            }
            if (program == null) {
                program = descriptor.getCOSStream(COSName.FONT_FILE3);
            }
            if (program == null) {
                return;
            }
            byte[] header = fontDataHeaders.get(program);
            if (header == null) {
                throw new IOException("Embedded Type0 font was not inspected");
            }
            COSName detectedSubtype = detectedCompositeSubtype(header);
            if (detectedSubtype != null
                    && !detectedSubtype.equals(declaredSubtype)) {
                throw new IOException(
                        "Embedded Type0 font subtype is contradictory");
            }
        }

        private static COSName detectedCompositeSubtype(byte[] header) {
            if (matches(header, 0, 1, 0, 0)
                    || matches(header, 't', 'r', 'u', 'e')
                    || matches(header, 't', 't', 'c', 'f')
                    || matches(header, 'O', 'T', 'T', 'O')) {
                return COSName.CID_FONT_TYPE2;
            }
            if ((header[0] == '%' && header[1] == '!')
                    || ((header[0] & 0xff) == 0x80
                            && (header[1] == 1 || header[1] == 2))
                    || (header[0] >= 1
                            && header[3] >= 1
                            && header[3] <= 4)) {
                return COSName.CID_FONT_TYPE0;
            }
            return null;
        }

        private static boolean matches(
                byte[] bytes,
                int first,
                int second,
                int third,
                int fourth) {
            return bytes[0] == (byte) first
                    && bytes[1] == (byte) second
                    && bytes[2] == (byte) third
                    && bytes[3] == (byte) fourth;
        }

        private void inspectFontDescriptor(COSBase value)
                throws IOException {
            if (value == null) {
                return;
            }
            if (!(value instanceof COSDictionary)
                    || value instanceof COSStream) {
                throw new IOException("FontDescriptor is malformed");
            }
            COSDictionary descriptor = (COSDictionary) value;
            inspectFontData(descriptor.getDictionaryObject(COSName.FONT_FILE));
            inspectFontData(descriptor.getDictionaryObject(COSName.FONT_FILE2));
            inspectFontData(descriptor.getDictionaryObject(COSName.FONT_FILE3));
        }

        private void inspectFontData(COSBase value) throws IOException {
            if (value == null) {
                return;
            }
            if (!(value instanceof COSStream)) {
                throw new IOException("Font data entry is not a stream");
            }
            COSStream stream = (COSStream) value;
            if (fontDataInspected.containsKey(stream)) {
                return;
            }
            fontDataInspected.put(stream, Boolean.TRUE);
            resources.retainOwnedMemoryAsIOException(4L);
            boolean retained = false;
            try {
                byte[] header = new byte[4];
                resources.decodeStreamAsIOException(
                        stream,
                        new AccountedDecodedOutput(null, header));
                fontDataHeaders.put(stream, header);
                fontHeaderBytes += header.length;
                retained = true;
            } finally {
                if (!retained) {
                    resources.releaseRetainedOwnedMemory(4L);
                }
            }
        }

        byte[] provisionalSourceCode(
                byte[] source,
                int offset,
                int length) throws IOException {
            resources.retainOwnedMemoryAsIOException(length);
            try {
                byte[] bytes = Arrays.copyOfRange(
                        source, offset, offset + length);
                resultSourceCodeBytes += length;
                return bytes;
            } catch (RuntimeException | Error failure) {
                resources.releaseRetainedOwnedMemory(length);
                throw failure;
            }
        }

        String finishPageText(
                WorkflowResourceContext.OwnedTextAccumulator text)
                throws IOException {
            String result = text.finishRetainedAsIOException();
            resultTextBytes += 2L * result.length();
            return result;
        }

        void releaseProvisionalMemory(boolean resultReturned) throws DocumentFailure {
            IOException closeFailure = null;
            if (backendEncodingStream != null) {
                try {
                    backendEncodingStream.close();
                } catch (IOException failure) {
                    closeFailure = failure;
                }
                backendEncodingStream = null;
            }
            if (backendEncodingCache != null) {
                try {
                    backendEncodingCache.close();
                } catch (IOException failure) {
                    closeFailure = failure;
                }
                backendEncodingCache = null;
            }
            for (EncodingPlan plan : encodingPlans.values()) {
                for (PdfBoxCMapPreflight.EncodingMappings layer : plan.layers) {
                    layer.close();
                }
            }
            encodingPlans.clear();
            for (List<PdfBoxCMapPreflight.UnicodeMappings> layers : explicitCMaps.values()) {
                for (PdfBoxCMapPreflight.UnicodeMappings mappings : layers) {
                    mappings.close(resultReturned);
                }
            }
            explicitCMaps.clear();
            resources.releaseRetainedOwnedMemory(fontHeaderBytes);
            fontHeaderBytes = 0L;
            if (!resultReturned) {
                resources.releaseRetainedOwnedMemory(resultSourceCodeBytes);
                resources.releaseRetainedOwnedMemory(resultTextBytes);
            }
            resultSourceCodeBytes = 0L;
            resultTextBytes = 0L;
            if (closeFailure != null) {
                resources.rethrowResourceOrTerminalFailure(closeFailure);
                throw failure(DocumentFailureCode.QUERY_FAILED, "The text extraction resources could not be closed.");
            }
        }

        private void inspectCidToGidMap(COSBase value) throws IOException {
            if (value == null
                    || COSName.getPDFName("Identity").equals(value)) {
                return;
            }
            inspectFontData(value);
        }

        String inferredUnicode(PDFont font, int code) throws IOException {
            if (!(font instanceof PDSimpleFont)) {
                return null;
            }
            COSDictionary dictionary = originalFontDictionary(font);
            inspectFontDictionary(dictionary);
            DeclaredEncoding encoding = fontEncodings.get(dictionary);
            String name = encoding.glyphName(code);
            return name == null
                    ? null
                    : GlyphList.getAdobeGlyphList().toUnicode(name);
        }

        private DeclaredEncoding readDeclaredEncoding(COSDictionary font)
                throws IOException {
            if (COSName.TYPE0.equals(font.getCOSName(COSName.SUBTYPE))) {
                return DeclaredEncoding.NONE;
            }
            COSBase value = font.getDictionaryObject(COSName.ENCODING);
            if (value == null) {
                return DeclaredEncoding.NONE;
            }
            if (value instanceof COSName) {
                Encoding base = Encoding.getInstance((COSName) value);
                return base == null
                        ? DeclaredEncoding.NONE
                        : new DeclaredEncoding(base, null);
            }
            if (!(value instanceof COSDictionary)
                    || value instanceof COSStream) {
                throw new IOException("Simple-font Encoding is malformed");
            }
            COSDictionary dictionary = (COSDictionary) value;
            if (encodingDictionaries.containsKey(dictionary)) {
                return encodingDictionaries.get(dictionary);
            }

            COSBase baseValue = dictionary.getDictionaryObject(
                    COSName.BASE_ENCODING);
            Encoding base = null;
            if (baseValue != null) {
                if (!(baseValue instanceof COSName)) {
                    throw new IOException("BaseEncoding is malformed");
                }
                base = Encoding.getInstance((COSName) baseValue);
            }
            String[] differences = readDifferences(dictionary);
            DeclaredEncoding result = base == null && differences == null
                    ? DeclaredEncoding.NONE
                    : new DeclaredEncoding(base, differences);
            encodingDictionaries.put(dictionary, result);
            return result;
        }

        private String[] readDifferences(COSDictionary encoding)
                throws IOException {
            COSBase value = encoding.getDictionaryObject(COSName.DIFFERENCES);
            if (value == null) {
                return null;
            }
            if (!(value instanceof COSArray)) {
                throw new IOException("Encoding Differences is malformed");
            }
            COSArray entries = (COSArray) value;
            if (differenceArrays.containsKey(entries)) {
                return differenceArrays.get(entries);
            }
            accountFontDataEntries(entries.size());
            String[] result = new String[256];
            int current = -1;
            for (int index = 0; index < entries.size(); index++) {
                resources.checkpointAsIOException();
                COSBase entry = entries.getObject(index);
                if (entry instanceof COSInteger) {
                    long characterCode = ((COSInteger) entry).longValue();
                    if (characterCode < 0L || characterCode > 255L) {
                        throw new IOException(
                                "Encoding Differences code is out of range");
                    }
                    current = (int) characterCode;
                } else if (entry instanceof COSName) {
                    if (current < 0 || current > 255) {
                        throw new IOException(
                                "Encoding Differences has no character code");
                    }
                    result[current] = ((COSName) entry).getName();
                    current++;
                } else {
                    throw new IOException("Encoding Differences is malformed");
                }
            }
            differenceArrays.put(entries, result);
            return result;
        }

        String explicitUnicode(PDFont font, byte[] sourceCode)
                throws IOException {
            COSDictionary dictionary = originalFontDictionary(font);
            inspectFontDictionary(dictionary);
            List<PdfBoxCMapPreflight.UnicodeMappings> layers = explicitCMaps.get(dictionary);
            if (layers != null) {
                for (PdfBoxCMapPreflight.UnicodeMappings layer : layers) {
                    resources.checkpointAsIOException();
                    if (layer.containsSource(sourceCode)) {
                        return layer.lookup(sourceCode);
                    }
                }
            }
            return null;
        }

        private COSDictionary originalFontDictionary(PDFont font) {
            COSDictionary dictionary = font.getCOSObject();
            COSDictionary source = fontSources.get(dictionary);
            return source == null ? dictionary : source;
        }

        private WorkflowResourceContext.OwnedBytes decodedBytes(
                COSStream stream) throws IOException {
            try (WorkflowResourceContext.OwnedByteAccumulator output =
                    resources.ownedByteAccumulator()) {
                resources.decodeStreamAsIOException(
                        stream,
                        new AccountedDecodedOutput(output, null));
                return output.finishWorkingAsIOException();
            }
        }

        private final class AccountedDecodedOutput extends OutputStream {

            private final WorkflowResourceContext.OwnedByteAccumulator output;
            private final byte[] header;
            private int headerBytes;

            private AccountedDecodedOutput(
                    WorkflowResourceContext.OwnedByteAccumulator output,
                    byte[] header) {
                this.output = output;
                this.header = header;
            }

            @Override
            public void write(int value) throws IOException {
                accountDecodedBytes(1);
                if (header != null && headerBytes < header.length) {
                    header[headerBytes++] = (byte) value;
                }
                if (output != null) {
                    output.write(value);
                }
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
                accountDecodedBytes(length);
                if (header != null && headerBytes < header.length) {
                    int copied = Math.min(
                            length, header.length - headerBytes);
                    System.arraycopy(
                            bytes, offset, header, headerBytes, copied);
                    headerBytes += copied;
                }
                if (output != null) {
                    output.write(bytes, offset, length);
                }
            }
        }

        int nextTextItem() throws IOException {
            resources.checkpointAsIOException();
            if (textItems >= limits.getMaximumTextItems()) {
                throw new ExtractionLimitException();
            }
            textItems++;
            return textItems;
        }

        void accountUnicode(String value) throws IOException {
            int index = 0;
            while (index < value.length()) {
                if ((index & 1023) == 0) {
                    resources.checkpointAsIOException();
                }
                if (unicodeCodePoints
                        >= limits.getMaximumUnicodeCodePoints()) {
                    throw new ExtractionLimitException();
                }
                int codePoint = Character.codePointAt(value, index);
                index += Character.charCount(codePoint);
                unicodeCodePoints++;
            }
        }

        void accountToUnicodeMappings(long count)
                throws IOException {
            resources.checkpointAsIOException();
            if (count < 0L
                    || count > limits.getMaximumToUnicodeMappings()
                            - (long) toUnicodeMappings) {
                throw new ExtractionLimitException();
            }
            toUnicodeMappings += (int) count;
        }

        private void accountFontDataEntries(int count)
                throws IOException {
            resources.checkpointAsIOException();
            if (count < 0
                    || count > limits.getMaximumFontDataEntries()
                            - fontDataEntries) {
                throw new ExtractionLimitException();
            }
            fontDataEntries += count;
        }

        void nextMarkedContentSequence() throws IOException {
            resources.checkpointAsIOException();
            if (markedContentSequences
                    >= limits.getMaximumMarkedContentSequences()) {
                throw new ExtractionLimitException();
            }
            markedContentSequences++;
        }

        int contentStreamId(COSStream stream) throws IOException {
            resources.checkpointAsIOException();
            Integer id = contentStreamIds.get(stream);
            if (id == null) {
                if (contentStreamIds.size() == Integer.MAX_VALUE) {
                    throw new ExtractionLimitException();
                }
                id = Integer.valueOf(contentStreamIds.size() + 1);
                contentStreamIds.put(stream, id);
            }
            return id.intValue();
        }

        void requireMarkedContentDepth(int depth) throws IOException {
            resources.checkpointAsIOException();
            resources.requireNestingDepthAsIOException(depth);
            if (depth > limits.getMaximumMarkedContentDepth()) {
                throw new ExtractionLimitException();
            }
        }

        int nextStructureElement(int depth) throws IOException {
            resources.checkpointAsIOException();
            resources.requireNestingDepthAsIOException(depth);
            if (depth > limits.getMaximumStructureDepth()) {
                throw new ExtractionLimitException();
            }
            if (structureElements >= limits.getMaximumStructureElements()) {
                throw new ExtractionLimitException();
            }
            structureElements++;
            return structureElements;
        }

        void nextStructureItem() throws IOException {
            resources.checkpointAsIOException();
            if (structureItems >= limits.getMaximumStructureItems()) {
                throw new ExtractionLimitException();
            }
            structureItems++;
        }

        void nextRoleMapping() throws IOException {
            resources.checkpointAsIOException();
            if (roleMappings >= limits.getMaximumRoleMappings()) {
                throw new ExtractionLimitException();
            }
            roleMappings++;
        }
    }

    private static final class EncodingPlan {

        private final WorkflowResourceContext resources;
        private final List<PdfBoxCMapPreflight.EncodingMappings> layers =
                new ArrayList<PdfBoxCMapPreflight.EncodingMappings>();
        private List<PdfBoxCMapPreflight.Codespace> codespaces;
        private int writingMode;
        private int lastLength;
        private int lastCode;
        private int lastCid;

        EncodingPlan(WorkflowResourceContext resources) {
            this.resources = resources;
        }

        void finish() throws IOException {
            writingMode = layers.get(0).writingMode;
            codespaces = PdfBoxCMapPreflight.validateGraph(layers, resources);
            if (codespaces.isEmpty()) {
                throw new IOException("Encoding CMap has no codespace");
            }
        }

        int readCode(InputStream input) throws IOException {
            int code = 0;
            for (int length = 1; length <= 4; length++) {
                resources.checkpointAsIOException();
                int next = input.read();
                if (next < 0) {
                    throw new IOException("Truncated Encoding character code");
                }
                code = (code << 8) | next;
                boolean possible = false;
                for (PdfBoxCMapPreflight.Codespace codespace : codespaces) {
                    resources.checkpointAsIOException();
                    if (codespace.matchesPrefix(length, code & 0xffffffffL)) {
                        possible = true;
                        if (codespace.length == length) {
                            lastLength = length;
                            lastCode = code;
                            lastCid = resolveCid(length, code & 0xffffffffL);
                            return code;
                        }
                    }
                }
                if (!possible) {
                    throw new IOException("Character code is outside Encoding codespaces");
                }
            }
            throw new IOException("Character code exceeds four bytes");
        }

        private int resolveCid(int length, long code) throws IOException {
            for (int pass = 0; pass < 2; pass++) {
                for (PdfBoxCMapPreflight.EncodingMappings layer : layers) {
                    Integer cid = layer.lookup(length, code, pass == 1);
                    if (cid != null) {
                        return cid.intValue();
                    }
                }
            }
            return 0;
        }

        int cid(int code) throws IOException {
            if (lastLength == 0 || code != lastCode) {
                throw new IOException("CID metric request has no exact source code");
            }
            return lastCid;
        }
    }

    private static final class DeclaredType0Font extends PDType0Font {

        private final EncodingPlan plan;

        DeclaredType0Font(COSDictionary dictionary, EncodingPlan plan) throws IOException {
            super(dictionary);
            this.plan = plan;
        }

        @Override
        public int readCode(InputStream input) throws IOException {
            return plan.readCode(input);
        }

        @Override
        public boolean isVertical() {
            return plan == null ? super.isVertical() : plan.writingMode == 1;
        }

        @Override
        public float getWidth(int code) throws IOException {
            return getDescendantFont().getWidth(plan.cid(code));
        }

        @Override
        public Vector getPositionVector(int code) {
            try {
                return getDescendantFont().getPositionVector(plan.cid(code)).scale(-1 / 1000f);
            } catch (IOException missingCode) {
                throw new IllegalStateException(missingCode);
            }
        }

        @Override
        public Vector getDisplacement(int code) throws IOException {
            int cid = plan.cid(code);
            return isVertical()
                    ? new Vector(0f, getDescendantFont().getVerticalDisplacementVectorY(cid) / 1000f)
                    : new Vector(getDescendantFont().getWidth(cid) / 1000f, 0f);
        }
    }

    private static final class DeclaredType3Font extends PDType3Font {

        DeclaredType3Font(COSDictionary dictionary) throws IOException {
            super(dictionary);
        }

        @Override
        public float getWidth(int code) {
            COSDictionary dictionary = getCOSObject();
            int first = dictionary.getInt(COSName.FIRST_CHAR);
            int last = dictionary.getInt(COSName.LAST_CHAR);
            if (code < first || code > last) {
                return 0f;
            }
            COSArray widths = (COSArray) dictionary.getDictionaryObject(COSName.WIDTHS);
            return ((COSNumber) widths.getObject(code - first)).floatValue();
        }

        @Override
        public Vector getDisplacement(int code) {
            return new Vector(getFontMatrix().getScaleX() * getWidth(code), 0f);
        }
    }

    private static final class DeclaredEncoding {

        private static final DeclaredEncoding NONE =
                new DeclaredEncoding(null, null);

        private final Encoding base;
        private final String[] differences;

        DeclaredEncoding(Encoding base, String[] differences) {
            this.base = base;
            this.differences = differences;
        }

        String glyphName(int code) {
            if (code < 0 || code > 255) {
                return null;
            }
            if (differences != null && differences[code] != null) {
                return differences[code];
            }
            return base == null ? null : base.getName(code);
        }
    }

    private static final class PageEngine extends PDFStreamEngine
            implements AutoCloseable {

        private final int pageNumber;
        private final ExtractionState state;
        private final List<TextItem> items = new ArrayList<TextItem>();
        private final List<SequenceBuilder> sequences =
                new ArrayList<SequenceBuilder>();
        private final Deque<SequenceBuilder> activeSequences =
                new ArrayDeque<SequenceBuilder>();
        private final WorkflowResourceContext.OwnedTextAccumulator pageText;
        private final Deque<SourceCodeFrame> sourceFrames =
                new ArrayDeque<SourceCodeFrame>();
        private final IdentityHashMap<COSStream, Boolean> activeForms =
                new IdentityHashMap<COSStream, Boolean>();
        private final Deque<Integer> activeContentStreams = new ArrayDeque<Integer>();
        private final Deque<OperatorBalance> operatorBalances =
                new ArrayDeque<OperatorBalance>();

        PageEngine(int pageNumber, ExtractionState state) {
            this.pageNumber = pageNumber;
            this.state = state;
            this.pageText = state.resources.ownedTextAccumulator();
            operatorBalances.addLast(new OperatorBalance(state.resources));
            addOperator(new BeginText(this));
            addOperator(new BeginMarkedContentSequence(this));
            addOperator(new BeginMarkedContentSequenceWithProperties(this));
            addOperator(new Concatenate(this));
            addOperator(new BoundedDrawObject(this));
            addOperator(new EndText(this));
            addOperator(new EndMarkedContentSequence(this));
            addOperator(new SetGraphicsStateParameters(this));
            addOperator(new Save(this));
            addOperator(new Restore(this));
            addOperator(new NextLine(this));
            addOperator(new SetCharSpacing(this));
            addOperator(new MoveText(this));
            addOperator(new MoveTextSetLeading(this));
            addOperator(new SetFontAndSize(this));
            addOperator(new ShowText(this));
            addOperator(new ShowTextAdjusted(this));
            addOperator(new SetTextLeading(this));
            addOperator(new SetMatrix(this));
            addOperator(new SetTextRenderingMode(this));
            addOperator(new SetTextRise(this));
            addOperator(new SetWordSpacing(this));
            addOperator(new SetTextHorizontalScaling(this));
            addOperator(new ShowTextLine(this));
            addOperator(new ShowTextLineAndSpace(this));
        }

        void showBoundedForm(PDFormXObject form) throws IOException {
            COSStream stream = form.getCOSObject();
            if (activeForms.put(stream, Boolean.TRUE) != null) {
                throw new IOException("Cyclic form XObject graph");
            }
            try {
                state.accountFormStream(form, activeForms.size() + 1);
                OperatorBalance formBalance = new OperatorBalance(
                        state.resources);
                operatorBalances.addLast(formBalance);
                activeContentStreams.addLast(Integer.valueOf(state.contentStreamId(stream)));
                increaseLevel();
                try {
                    if (form instanceof PDTransparencyGroup) {
                        showTransparencyGroup((PDTransparencyGroup) form);
                    } else {
                        showForm(form);
                    }
                    formBalance.requireBalanced();
                } finally {
                    decreaseLevel();
                    operatorBalances.removeLast();
                    activeContentStreams.removeLast();
                }
            } finally {
                activeForms.remove(stream);
            }
        }

        @Override
        protected void operatorException(
                Operator operator,
                List<COSBase> operands,
                IOException exception) throws IOException {
            throw exception;
        }

        @Override
        protected void processOperator(
                Operator operator,
                List<COSBase> operands) throws IOException {
            state.resources.checkpointAsIOException();
            validateSupportedOperands(operator.getName(), operands, state.resources);
            operatorBalances.peekLast().accept(operator.getName());
            if (OperatorName.SET_FONT_AND_SIZE.equals(operator.getName())) {
                COSDictionary font = preflightNamedFont(operands);
                if (applyBoundedFont(font, (COSNumber) operands.get(1))) {
                    return;
                }
            } else if (OperatorName.SET_GRAPHICS_STATE_PARAMS.equals(
                    operator.getName())) {
                COSArray fontSetting = preflightGraphicsStateFont(operands);
                applyBoundedGraphicsState(fontSetting);
                return;
            } else if (OperatorName.BEGIN_MARKED_CONTENT_SEQ.equals(
                    operator.getName())) {
                preflightMarkedContentProperty(operands);
            }
            super.processOperator(operator, operands);
        }

        @Override
        public void beginMarkedContentSequence(
                COSName tag,
                COSDictionary properties) {
            if (tag == null) {
                throw new ExtractionMalformedRuntimeException();
            }
            try {
                state.nextMarkedContentSequence();
                state.requireMarkedContentDepth(activeSequences.size() + 1);
                String language = optionalString(properties, COSName.LANG);
                String alternate = optionalString(properties, COSName.ALT);
                String actual = optionalString(properties, COSName.ACTUAL_TEXT);
                state.accountUnicode(tag.getName());
                accountNullable(language);
                accountNullable(alternate);
                accountNullable(actual);
                Integer mcid = optionalNonNegativeInteger(
                        properties, COSName.MCID);
                if (mcid != null) {
                    operatorBalances.peekLast().declareMarkedContentId(mcid);
                }
                SequenceBuilder parent = activeSequences.peekLast();
                SequenceBuilder sequence = new SequenceBuilder(
                        sequences.size() + 1,
                        activeContentStreams.isEmpty() ? 0 : activeContentStreams.peekLast().intValue(),
                        tag.getName(),
                        mcid,
                        parent == null ? null : Integer.valueOf(parent.id),
                        language,
                        alternate,
                        actual);
                sequences.add(sequence);
                activeSequences.addLast(sequence);
                for (COSStream enclosing : activeForms.keySet()) {
                    if (enclosing.getDictionaryObject(StructureExtractor.STRUCT_PARENT) != null) {
                        state.nextStructureItem();
                        state.sequencesOverlappingStructuralObjects.add(
                                Long.valueOf(((long) pageNumber << 32) | sequence.id));
                    }
                }
            } catch (ExtractionLimitException exhausted) {
                throw new ExtractionLimitRuntimeException();
            } catch (IOException malformed) {
                throw new ExtractionMalformedRuntimeException();
            }
        }

        @Override
        public void endMarkedContentSequence() {
            if (activeSequences.isEmpty()) {
                throw new ExtractionMalformedRuntimeException();
            }
            SequenceBuilder completed = activeSequences.removeLast();
            if (completed.actualText != null && !hasActiveActualText()) {
                pageText.appendAsRuntimeException(completed.actualText);
            }
        }

        @Override
        protected void showText(byte[] string) throws IOException {
            PDFont font = getGraphicsState().getTextState().getFont();
            if (font == null) {
                throw new IOException("Text has no current font");
            }
            sourceFrames.push(new SourceCodeFrame(
                    font, string, state));
            try {
                super.showText(string);
                if (sourceFrames.peek().hasRemaining()) {
                    throw new IOException("Text source-code traversal was inconsistent");
                }
            } finally {
                sourceFrames.pop();
            }
        }

        @Override
        protected void showGlyph(
                Matrix textRenderingMatrix,
                PDFont font,
                int code,
                Vector displacement) throws IOException {
            state.resources.checkpointAsIOException();
            if (sourceFrames.isEmpty()) {
                throw new IOException("Text source code was unavailable");
            }
            state.nextTextItem();
            SourceCode source = sourceFrames.peek().next();
            if (source.numericValue != code) {
                throw new IOException("Text source-code traversal was inconsistent");
            }

            int pageIndex = items.size() + 1;
            String explicit = state.explicitUnicode(font, source.bytes);
            String inferred = state.inferredUnicode(font, code);
            if (explicit != null) {
                state.accountUnicode(explicit);
            }
            if (inferred != null && !inferred.equals(explicit)) {
                state.accountUnicode(inferred);
            }
            boolean contradictory = explicit != null
                    && inferred != null
                    && !explicit.equals(inferred);
            CharacterMapping.Confidence confidence;
            String selected;
            if (contradictory) {
                confidence = CharacterMapping.Confidence.CONTRADICTORY;
                selected = null;
            } else if (explicit != null) {
                confidence = CharacterMapping.Confidence.EXPLICIT;
                selected = explicit;
            } else if (inferred != null) {
                confidence = CharacterMapping.Confidence.INFERRED;
                selected = inferred;
            } else {
                confidence = CharacterMapping.Confidence.MISSING;
                selected = null;
            }
            CharacterMapping mapping = new CharacterMapping(
                    source.bytes,
                    confidence,
                    selected,
                    explicit,
                    inferred);
            String contribution = selected == null || hasActiveActualText()
                    ? ""
                    : selected;
            if (selected != null) {
                if (!hasActiveActualText()) {
                    pageText.appendAsIOException(selected);
                }
            } else if (contradictory) {
                state.diagnostics.add(new ExtractionDiagnostic(
                        ExtractionDiagnostic.Code.CONTRADICTORY_UNICODE_MAPPING,
                        pageNumber,
                        pageIndex,
                        source.bytes,
                        "Explicit and standard Unicode mappings disagree for this character code."));
            } else {
                state.diagnostics.add(new ExtractionDiagnostic(
                        ExtractionDiagnostic.Code.MISSING_UNICODE_MAPPING,
                        pageNumber,
                        pageIndex,
                        source.bytes,
                        "No defensible Unicode mapping is available for this character code."));
            }
            List<Integer> markedContentIds = new ArrayList<Integer>();
            for (SequenceBuilder sequence : activeSequences) {
                state.resources.checkpointAsIOException();
                markedContentIds.add(Integer.valueOf(sequence.id));
                sequence.textItemIndices.add(Integer.valueOf(pageIndex));
            }
            items.add(new TextItem(
                    pageIndex,
                    mapping,
                    contribution,
                    geometry(textRenderingMatrix, displacement),
                    TextRenderingMode.fromOperatorValue(
                            getGraphicsState().getTextState()
                                    .getRenderingMode().intValue()),
                    markedContentIds));
        }

        PageText result(PDPage page) throws IOException {
            if (!activeSequences.isEmpty()
                    || operatorBalances.size() != 1
                    || !operatorBalances.peekLast().isBalanced()) {
                throw new ExtractionMalformedRuntimeException();
            }
            PDRectangle cropBox = page.getCropBox();
            List<MarkedContentSequence> detachedSequences =
                    new ArrayList<MarkedContentSequence>(sequences.size());
            for (SequenceBuilder sequence : sequences) {
                state.resources.checkpointAsIOException();
                detachedSequences.add(sequence.detach());
            }
            return new PageText(
                    pageNumber,
                    page.getRotation(),
                    decimal(page.getUserUnit()),
                    decimal(cropBox.getLowerLeftX()),
                    decimal(cropBox.getLowerLeftY()),
                    decimal(cropBox.getUpperRightX()),
                    decimal(cropBox.getUpperRightY()),
                    state.finishPageText(pageText),
                    items,
                    detachedSequences);
        }

        @Override
        public void close() {
            pageText.close();
        }

        private boolean hasActiveActualText() {
            for (SequenceBuilder sequence : activeSequences) {
                try {
                    state.resources.checkpointAsIOException();
                } catch (IOException failure) {
                    throw new ExtractionMalformedRuntimeException();
                }
                if (sequence.actualText != null) {
                    return true;
                }
            }
            return false;
        }

        private void accountNullable(String value)
                throws IOException {
            if (value != null) {
                state.accountUnicode(value);
            }
        }

        private COSDictionary preflightNamedFont(List<COSBase> operands)
                throws IOException {
            if (operands.size() != 2
                    || !(operands.get(0) instanceof COSName)) {
                throw new IOException("Tf operands are malformed");
            }
            requireFiniteNumber(operands.get(1), "Tf font size");
            COSDictionary resources = resourceDictionary();
            if (resources == null) {
                throw new IOException("Font operator has no resources");
            }
            COSBase fonts = resources.getDictionaryObject(COSName.FONT);
            if (!(fonts instanceof COSDictionary)
                    || fonts instanceof COSStream) {
                throw new IOException("Font resources are malformed");
            }
            return inspectRawFont(((COSDictionary) fonts).getDictionaryObject(
                    (COSName) operands.get(0)));
        }

        private COSArray preflightGraphicsStateFont(List<COSBase> operands)
                throws IOException {
            requireSingleNameOperand(operands, "gs");
            COSDictionary resources = resourceDictionary();
            if (resources == null) {
                throw new IOException(
                        "Graphics-state operator has no resources");
            }
            COSBase states = resources.getDictionaryObject(
                    COSName.EXT_G_STATE);
            if (!(states instanceof COSDictionary)
                    || states instanceof COSStream) {
                throw new IOException("ExtGState resources are malformed");
            }
            COSBase selected = ((COSDictionary) states).getDictionaryObject(
                    (COSName) operands.get(0));
            if (!(selected instanceof COSDictionary)
                    || selected instanceof COSStream) {
                throw new IOException("ExtGState resource is malformed");
            }
            COSBase setting = ((COSDictionary) selected).getDictionaryObject(
                    COSName.FONT);
            if (setting == null) {
                return null;
            }
            if (!(setting instanceof COSArray)
                    || ((COSArray) setting).size() != 2) {
                throw new IOException("ExtGState font setting is malformed");
            }
            requireFiniteNumber(
                    ((COSArray) setting).getObject(1),
                    "ExtGState font size");
            inspectRawFont(((COSArray) setting).getObject(0));
            return (COSArray) setting;
        }

        private void applyBoundedGraphicsState(COSArray fontSetting)
                throws IOException {
            if (fontSetting == null) {
                return;
            }
            if (applyBoundedFont((COSDictionary) fontSetting.getObject(0), (COSNumber) fontSetting.getObject(1))) {
                return;
            }
            COSArray detachedSetting = new COSArray();
            detachedSetting.add(fontSetting.get(0));
            detachedSetting.add(fontSetting.get(1));
            COSDictionary extractionState = new COSDictionary();
            extractionState.setItem(COSName.FONT, detachedSetting);
            new PDExtendedGraphicsState(extractionState)
                    .copyIntoGraphicsState(getGraphicsState());
        }

        private boolean applyBoundedFont(COSDictionary dictionary, COSNumber size) throws IOException {
            PDFont font = state.boundedFont(dictionary);
            if (font == null) {
                return false;
            }
            getGraphicsState().getTextState().setFont(font);
            getGraphicsState().getTextState().setFontSize(size.floatValue());
            return true;
        }

        private void preflightMarkedContentProperty(List<COSBase> operands)
                throws IOException {
            if (operands.size() != 2
                    || !(operands.get(0) instanceof COSName)) {
                throw new IOException("BDC operands are malformed");
            }
            COSBase property = operands.get(1);
            if (property instanceof COSName) {
                COSDictionary resources = resourceDictionary();
                if (resources == null) {
                    throw new IOException(
                            "Marked-content property has no resources");
                }
                COSBase properties = resources.getDictionaryObject(
                        COSName.PROPERTIES);
                if (!(properties instanceof COSDictionary)
                        || properties instanceof COSStream) {
                    throw new IOException(
                            "Marked-content properties are malformed");
                }
                property = ((COSDictionary) properties).getDictionaryObject(
                        (COSName) property);
            }
            if (!(property instanceof COSDictionary)
                    || property instanceof COSStream) {
                throw new IOException(
                        "Marked-content property is malformed");
            }
        }

        private COSDictionary resourceDictionary() throws IOException {
            if (getResources() == null) {
                return null;
            }
            COSDictionary resources = getResources().getCOSObject();
            if (resources instanceof COSStream) {
                throw new IOException("Resources dictionary is malformed");
            }
            return resources;
        }

        private COSDictionary inspectRawFont(COSBase value) throws IOException {
            if (!(value instanceof COSDictionary)
                    || value instanceof COSStream) {
                throw new IOException("Font resource is malformed");
            }
            state.inspectFontDictionary((COSDictionary) value);
            return (COSDictionary) value;
        }

        private static void requireSingleNameOperand(
                List<COSBase> operands,
                String operator) throws IOException {
            if (operands.size() != 1
                    || !(operands.get(0) instanceof COSName)) {
                throw new IOException(operator + " operands are malformed");
            }
        }

        private static void validateSupportedOperands(
                String operator,
                List<COSBase> operands,
                WorkflowResourceContext resources) throws IOException {
            if (OperatorName.BEGIN_TEXT.equals(operator)
                    || OperatorName.END_TEXT.equals(operator)
                    || OperatorName.NEXT_LINE.equals(operator)
                    || OperatorName.SAVE.equals(operator)
                    || OperatorName.RESTORE.equals(operator)
                    || OperatorName.END_MARKED_CONTENT.equals(operator)) {
                requireNoOperands(operands, operator);
            } else if (OperatorName.SET_CHAR_SPACING.equals(operator)
                    || OperatorName.SET_TEXT_HORIZONTAL_SCALING.equals(operator)
                    || OperatorName.SET_TEXT_LEADING.equals(operator)
                    || OperatorName.SET_TEXT_RISE.equals(operator)
                    || OperatorName.SET_WORD_SPACING.equals(operator)) {
                requireFiniteNumbers(operands, 1, operator);
            } else if (OperatorName.MOVE_TEXT.equals(operator)
                    || OperatorName.MOVE_TEXT_SET_LEADING.equals(operator)) {
                requireFiniteNumbers(operands, 2, operator);
            } else if (OperatorName.CONCAT.equals(operator)
                    || OperatorName.SET_MATRIX.equals(operator)) {
                requireFiniteNumbers(operands, 6, operator);
            } else if (OperatorName.SET_TEXT_RENDERINGMODE.equals(operator)) {
                requireRenderingMode(operands);
            } else if (OperatorName.SET_FONT_AND_SIZE.equals(operator)) {
                if (operands.size() != 2
                        || !(operands.get(0) instanceof COSName)) {
                    throw new IOException("Tf operands are malformed");
                }
                requireFiniteNumber(operands.get(1), "Tf font size");
            } else if (OperatorName.SHOW_TEXT.equals(operator)
                    || OperatorName.SHOW_TEXT_LINE.equals(operator)) {
                requireSingleStringOperand(operands, operator);
            } else if (OperatorName.SHOW_TEXT_ADJUSTED.equals(operator)) {
                requireTextAdjustmentArray(operands, resources);
            } else if (OperatorName.SHOW_TEXT_LINE_AND_SPACE.equals(operator)) {
                if (operands.size() != 3
                        || !(operands.get(2) instanceof COSString)) {
                    throw new IOException("double-quote operands are malformed");
                }
                requireFiniteNumber(operands.get(0), "word spacing");
                requireFiniteNumber(operands.get(1), "character spacing");
            } else if (OperatorName.DRAW_OBJECT.equals(operator)
                    || OperatorName.SET_GRAPHICS_STATE_PARAMS.equals(operator)
                    || OperatorName.BEGIN_MARKED_CONTENT.equals(operator)) {
                requireSingleNameOperand(operands, operator);
            } else if (OperatorName.BEGIN_MARKED_CONTENT_SEQ.equals(operator)
                    && (operands.size() != 2
                            || !(operands.get(0) instanceof COSName))) {
                throw new IOException("BDC operands are malformed");
            }
        }

        private static void requireNoOperands(
                List<COSBase> operands,
                String operator) throws IOException {
            if (!operands.isEmpty()) {
                throw new IOException(operator + " operands are malformed");
            }
        }

        private static void requireFiniteNumbers(
                List<COSBase> operands,
                int expected,
                String operator) throws IOException {
            if (operands.size() != expected) {
                throw new IOException(operator + " operands are malformed");
            }
            for (COSBase operand : operands) {
                requireFiniteNumber(operand, operator + " operand");
            }
        }

        private static void requireRenderingMode(List<COSBase> operands)
                throws IOException {
            if (operands.size() != 1
                    || !(operands.get(0) instanceof COSInteger)) {
                throw new IOException("Tr operand is malformed");
            }
            long mode = ((COSInteger) operands.get(0)).longValue();
            if (mode < 0L || mode > 7L) {
                throw new IOException("Tr operand is out of range");
            }
        }

        private static void requireSingleStringOperand(
                List<COSBase> operands,
                String operator) throws IOException {
            if (operands.size() != 1
                    || !(operands.get(0) instanceof COSString)) {
                throw new IOException(operator + " operand is malformed");
            }
        }

        private static void requireTextAdjustmentArray(
                List<COSBase> operands, WorkflowResourceContext resources)
                throws IOException {
            if (operands.size() != 1
                    || !(operands.get(0) instanceof COSArray)) {
                throw new IOException("TJ operand is malformed");
            }
            COSArray adjustments = (COSArray) operands.get(0);
            for (int index = 0; index < adjustments.size(); index++) {
                resources.checkpointAsIOException();
                COSBase value = adjustments.getObject(index);
                if (value instanceof COSString) {
                    continue;
                }
                requireFiniteNumber(value, "TJ adjustment");
            }
        }

        private static void requireFiniteNumber(
                COSBase value,
                String description) throws IOException {
            if (!(value instanceof COSNumber)) {
                throw new IOException(description + " is malformed");
            }
            float converted = ((COSNumber) value).floatValue();
            if (Float.isNaN(converted) || Float.isInfinite(converted)) {
                throw new IOException(description + " is malformed");
            }
        }

        private static final class OperatorBalance {

            private final WorkflowResourceContext resources;
            private boolean inTextObject;
            private int graphicsSaves;
            private int markedContentDepth;
            private final Set<Integer> markedContentIds = new HashSet<Integer>();

            private OperatorBalance(WorkflowResourceContext resources) {
                this.resources = resources;
            }

            void declareMarkedContentId(Integer identifier) throws IOException {
                if (!markedContentIds.add(identifier)) {
                    throw new IOException("Duplicate MCID in one content-stream invocation");
                }
            }

            void accept(String operator) throws IOException {
                if (OperatorName.BEGIN_TEXT.equals(operator)) {
                    if (inTextObject) {
                        throw new IOException("Nested BT operator");
                    }
                    inTextObject = true;
                } else if (OperatorName.END_TEXT.equals(operator)) {
                    if (!inTextObject) {
                        throw new IOException("Unmatched ET operator");
                    }
                    inTextObject = false;
                } else if (OperatorName.SAVE.equals(operator)) {
                    if (graphicsSaves == Integer.MAX_VALUE) {
                        throw new IOException("Graphics-state depth overflow");
                    }
                    graphicsSaves++;
                    resources.requireNestingDepthAsIOException(
                            graphicsSaves);
                } else if (OperatorName.RESTORE.equals(operator)) {
                    if (graphicsSaves == 0) {
                        throw new IOException("Unmatched Q operator");
                    }
                    graphicsSaves--;
                } else if (OperatorName.BEGIN_MARKED_CONTENT.equals(operator)
                        || OperatorName.BEGIN_MARKED_CONTENT_SEQ.equals(operator)) {
                    if (markedContentDepth == Integer.MAX_VALUE) {
                        throw new IOException("Marked-content depth overflow");
                    }
                    markedContentDepth++;
                } else if (OperatorName.END_MARKED_CONTENT.equals(operator)) {
                    if (markedContentDepth == 0) {
                        throw new IOException("Unmatched EMC operator in content stream");
                    }
                    markedContentDepth--;
                } else if (isTextObjectOperator(operator) && !inTextObject) {
                    throw new IOException(
                            "Text operator is outside a text object");
                }
            }

            private static boolean isTextObjectOperator(String operator) {
                return OperatorName.NEXT_LINE.equals(operator)
                        || OperatorName.SET_CHAR_SPACING.equals(operator)
                        || OperatorName.MOVE_TEXT.equals(operator)
                        || OperatorName.MOVE_TEXT_SET_LEADING.equals(operator)
                        || OperatorName.SET_FONT_AND_SIZE.equals(operator)
                        || OperatorName.SHOW_TEXT.equals(operator)
                        || OperatorName.SHOW_TEXT_ADJUSTED.equals(operator)
                        || OperatorName.SET_TEXT_LEADING.equals(operator)
                        || OperatorName.SET_MATRIX.equals(operator)
                        || OperatorName.SET_TEXT_RENDERINGMODE.equals(operator)
                        || OperatorName.SET_TEXT_RISE.equals(operator)
                        || OperatorName.SET_WORD_SPACING.equals(operator)
                        || OperatorName.SET_TEXT_HORIZONTAL_SCALING.equals(
                                operator)
                        || OperatorName.SHOW_TEXT_LINE.equals(operator)
                        || OperatorName.SHOW_TEXT_LINE_AND_SPACE.equals(
                                operator);
            }

            boolean isBalanced() {
                return !inTextObject && graphicsSaves == 0 && markedContentDepth == 0;
            }

            void requireBalanced() throws IOException {
                if (!isBalanced()) {
                    throw new IOException("Content operator state is unbalanced");
                }
            }
        }

        private static TextGeometry geometry(
                Matrix matrix,
                Vector displacement) {
            Point2D.Float start = matrix.transformPoint(0f, 0f);
            Point2D.Float end = matrix.transformPoint(
                    displacement.getX(), displacement.getY());
            return new TextGeometry(
                    decimal(matrix.getScaleX()),
                    decimal(matrix.getShearY()),
                    decimal(matrix.getShearX()),
                    decimal(matrix.getScaleY()),
                    decimal(matrix.getTranslateX()),
                    decimal(matrix.getTranslateY()),
                    decimal(end.x - start.x),
                    decimal(end.y - start.y));
        }
    }

    private static final class BoundedDrawObject extends OperatorProcessor {

        private final PageEngine engine;

        BoundedDrawObject(PageEngine engine) {
            super(engine);
            this.engine = engine;
        }

        @Override
        public void process(Operator operator, List<COSBase> operands)
                throws IOException {
            if (operands.size() != 1
                    || !(operands.get(0) instanceof COSName)) {
                throw new MissingOperandException(operator, operands);
            }
            COSName name = (COSName) operands.get(0);
            COSDictionary resources = engine.resourceDictionary();
            if (resources == null) {
                throw new IOException("XObject operator has no resources");
            }
            COSBase entries = resources.getDictionaryObject(COSName.XOBJECT);
            if (!(entries instanceof COSDictionary)
                    || entries instanceof COSStream) {
                throw new IOException("XObject resources are malformed");
            }
            COSBase selected = ((COSDictionary) entries)
                    .getDictionaryObject(name);
            if (!(selected instanceof COSStream)) {
                throw new IOException("XObject resource is malformed");
            }
            COSName subtype = ((COSStream) selected).getCOSName(
                    COSName.SUBTYPE);
            if (((COSStream) selected).getDictionaryObject(StructureExtractor.STRUCT_PARENT) != null) {
                recordPage(engine.state.renderedObjectPages, (COSStream) selected, engine.pageNumber);
                for (COSStream enclosing : engine.activeForms.keySet()) {
                    engine.state.nextStructureItem();
                    if (enclosing.getDictionaryObject(StructureExtractor.STRUCT_PARENT) != null) {
                        throw new IOException("Structural XObject invokes another structural XObject");
                    }
                }
                for (SequenceBuilder sequence : engine.activeSequences) {
                    engine.state.nextStructureItem();
                    engine.state.sequencesOverlappingStructuralObjects.add(
                            Long.valueOf(((long) engine.pageNumber << 32) | sequence.id));
                }
            }
            if (COSName.IMAGE.equals(subtype)) {
                return;
            }
            if (!COSName.FORM.equals(subtype)) {
                throw new IOException("XObject subtype is unsupported");
            }
            COSStream form = (COSStream) selected;
            validateFormDictionary(form);
            COSBase rawType = form.getItem(COSName.TYPE);
            COSBase rawSubtype = form.getItem(COSName.SUBTYPE);
            try {
                PDXObject object = engine.getResources().getXObject(name);
                if (!(object instanceof PDFormXObject)) {
                    throw new IOException("Form XObject is malformed");
                }
                engine.showBoundedForm((PDFormXObject) object);
            } finally {
                form.setItem(COSName.TYPE, rawType);
                form.setItem(COSName.SUBTYPE, rawSubtype);
            }
        }

        private static void validateFormDictionary(COSStream form)
                throws IOException {
            COSBase type = form.getDictionaryObject(COSName.TYPE);
            if (type != null && !COSName.XOBJECT.equals(type)) {
                throw new IOException("Form Type is malformed");
            }
            requireNumberArray(form, COSName.BBOX, 4, true);
            requireNumberArray(form, COSName.MATRIX, 6, false);
            COSBase resources = form.getDictionaryObject(COSName.RESOURCES);
            if (resources != null
                    && (!(resources instanceof COSDictionary)
                            || resources instanceof COSStream)) {
                throw new IOException("Form Resources is malformed");
            }
            COSBase formType = form.getDictionaryObject(COSName.FORMTYPE);
            if (formType != null
                    && (!(formType instanceof COSInteger)
                            || ((COSInteger) formType).longValue() != 1L)) {
                throw new IOException("FormType is unsupported");
            }
        }

        private static void requireNumberArray(
                COSDictionary dictionary,
                COSName name,
                int size,
                boolean required) throws IOException {
            COSBase value = dictionary.getDictionaryObject(name);
            if (value == null && !required) {
                return;
            }
            if (!(value instanceof COSArray) || ((COSArray) value).size() != size) {
                throw new IOException(name.getName() + " is malformed");
            }
            COSArray values = (COSArray) value;
            for (int index = 0; index < values.size(); index++) {
                COSBase member = values.getObject(index);
                if (!(member instanceof COSNumber)) {
                    throw new IOException(name.getName() + " is malformed");
                }
                float number = ((COSNumber) member).floatValue();
                if (Float.isNaN(number) || Float.isInfinite(number)) {
                    throw new IOException(name.getName() + " is malformed");
                }
            }
        }

        @Override
        public String getName() {
            return OperatorName.DRAW_OBJECT;
        }
    }

    private static final class SequenceBuilder {

        private final int id;
        private final int contentStreamId;
        private final String tag;
        private final Integer markedContentId;
        private final Integer parentId;
        private final String language;
        private final String alternateText;
        private final String actualText;
        private final List<Integer> textItemIndices = new ArrayList<Integer>();

        SequenceBuilder(
                int id,
                int contentStreamId,
                String tag,
                Integer markedContentId,
                Integer parentId,
                String language,
                String alternateText,
                String actualText) {
            this.id = id;
            this.contentStreamId = contentStreamId;
            this.tag = tag;
            this.markedContentId = markedContentId;
            this.parentId = parentId;
            this.language = language;
            this.alternateText = alternateText;
            this.actualText = actualText;
        }

        MarkedContentSequence detach() {
            return new MarkedContentSequence(
                    id,
                    contentStreamId,
                    tag,
                    markedContentId,
                    parentId,
                    language,
                    alternateText,
                    actualText,
                    textItemIndices);
        }
    }

    private static final class StructureExtractor {

        private static final COSName STRUCT_TREE_ROOT =
                COSName.getPDFName("StructTreeRoot");
        private static final COSName ROLE_MAP = COSName.getPDFName("RoleMap");
        private static final COSName NAMESPACES =
                COSName.getPDFName("Namespaces");
        private static final COSName NAMESPACE = COSName.getPDFName("NS");
        private static final COSName STRUCTURE_TYPE = COSName.getPDFName("S");
        private static final COSName PAGE = COSName.getPDFName("Pg");
        private static final COSName CHILDREN = COSName.getPDFName("K");
        private static final COSName MCR = COSName.getPDFName("MCR");
        private static final COSName OBJR = COSName.getPDFName("OBJR");
        private static final COSName OBJECT = COSName.getPDFName("Obj");
        private static final COSName STREAM = COSName.getPDFName("Stm");
        private static final COSName STREAM_OWNER = COSName.getPDFName("StmOwn");
        private static final COSName PARENT_TREE = COSName.getPDFName("ParentTree");
        private static final COSName STRUCT_PARENT = COSName.getPDFName("StructParent");
        private static final COSName STRUCT_PARENTS = COSName.getPDFName("StructParents");
        private static final COSName ROLE_MAP_NS = COSName.getPDFName("RoleMapNS");
        private static final String PDF_NAMESPACE = "http://iso.org/pdf/ssn";
        private static final String PDF_TWO_NAMESPACE = "http://iso.org/pdf2/ssn";

        private static final Set<String> STANDARD_ROLES =
                Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
                        "Document", "Part", "Art", "Sect",
                        "Div", "BlockQuote", "Caption", "TOC",
                        "TOCI", "Index", "NonStruct", "Private", "P", "H",
                        "H1", "H2", "H3", "H4", "H5", "H6",
                        "L", "LI", "Lbl", "LBody",
                        "Table", "TR", "TH", "TD", "THead", "TBody",
                        "TFoot", "Span", "Quote", "Note",
                        "Reference", "BibEntry", "Code", "Link", "Annot",
                        "Ruby", "RB", "RT", "RP", "Warichu", "WT", "WP",
                        "Figure", "Formula", "Form")));

        private final PDDocument document;
        private final PdfBoxValueAdapter valueAdapter;
        private final ExtractionState state;
        private final List<PageText> pages;
        private final List<COSDictionary> pageDictionaries;
        private final IdentityHashMap<COSDictionary, Integer> pageNumbers =
                new IdentityHashMap<COSDictionary, Integer>();
        private final IdentityHashMap<COSDictionary, Boolean> visitedElements =
                new IdentityHashMap<COSDictionary, Boolean>();
        private final Map<String, String> roleMap =
                new HashMap<String, String>();
        private final IdentityHashMap<COSDictionary, String> namespaces =
                new IdentityHashMap<COSDictionary, String>();
        private final IdentityHashMap<COSDictionary, Boolean> declaredNamespaces =
                new IdentityHashMap<COSDictionary, Boolean>();
        private final IdentityHashMap<COSDictionary, Boolean> admittedNamespaces =
                new IdentityHashMap<COSDictionary, Boolean>();
        private final IdentityHashMap<COSDictionary, Boolean> accountedRoleMaps =
                new IdentityHashMap<COSDictionary, Boolean>();
        private final IdentityHashMap<COSDictionary, Map<String, RoleTarget>> namespaceRoleMaps =
                new IdentityHashMap<COSDictionary, Map<String, RoleTarget>>();
        private final IdentityHashMap<COSDictionary, Map<String, RoleTarget>> namespaceMappings =
                new IdentityHashMap<COSDictionary, Map<String, RoleTarget>>();
        private final Map<Integer, COSBase> parentTree = new HashMap<Integer, COSBase>();
        private final Map<Integer, COSDictionary> parentTreeOwners = new HashMap<Integer, COSDictionary>();
        private final IdentityHashMap<COSStream, Set<Integer>> referencedObjectPages =
                new IdentityHashMap<COSStream, Set<Integer>>();
        private final Set<Long> linkedSequences = new HashSet<Long>();
        private final Set<Long> enclosingSequences = new HashSet<Long>();
        private final IdentityHashMap<COSStream, Set<Integer>> referencedMcidDefinitions =
                new IdentityHashMap<COSStream, Set<Integer>>();
        private final IdentityHashMap<COSStream, Set<Integer>> definitionPages =
                new IdentityHashMap<COSStream, Set<Integer>>();
        private final IdentityHashMap<COSStream, Set<Integer>> pendingAppearancePages =
                new IdentityHashMap<COSStream, Set<Integer>>();
        private final List<DefinitionRoot> definitionRoots = new ArrayList<DefinitionRoot>();
        private final IdentityHashMap<COSStream, IdentityHashMap<COSDictionary, Boolean>> definitionOwners =
                new IdentityHashMap<COSStream, IdentityHashMap<COSDictionary, Boolean>>();

        StructureExtractor(
                PDDocument document,
                PdfBoxValueAdapter valueAdapter,
                ExtractionState state,
                List<PageText> pages,
                List<COSDictionary> pageDictionaries) throws IOException {
            this.document = document;
            this.valueAdapter = valueAdapter;
            this.state = state;
            this.pages = pages;
            this.pageDictionaries = pageDictionaries;
            for (int index = 0; index < pageDictionaries.size(); index++) {
                state.resources.checkpointAsIOException();
                pageNumbers.put(
                        pageDictionaries.get(index),
                        Integer.valueOf(index + 1));
            }
        }

        List<LogicalStructureElement> extract() throws IOException, DocumentFailure {
            COSDictionary catalog = document.getDocumentCatalog()
                    .getCOSObject();
            COSBase rootValue = catalog.getDictionaryObject(STRUCT_TREE_ROOT);
            if (rootValue == null) {
                return Collections.emptyList();
            }
            COSDictionary root = requiredDictionary(rootValue);
            if (!STRUCT_TREE_ROOT.equals(root.getDictionaryObject(COSName.TYPE))) {
                throw new IOException("StructTreeRoot Type is malformed");
            }
            readNamespaces(root);
            validateStandardNamespaceMappings();
            readRoleMap(root);
            readParentTree(root);
            String documentLanguage = optionalString(catalog, COSName.LANG);
            Language inherited = documentLanguage == null
                    ? Language.none()
                    : new Language(
                            documentLanguage,
                            LogicalStructureElement.LanguageSource.DOCUMENT);
            COSBase children = root.getDictionaryObject(CHILDREN);
            List<LogicalStructureElement> result = children == null
                    ? Collections.<LogicalStructureElement>emptyList() : rootElements(children, inherited, root);
            validateReferencedFormDefinitions();
            for (Map.Entry<COSStream, Set<Integer>> entry : state.renderedObjectPages.entrySet()) {
                Set<Integer> declaredPages = referencedObjectPages.get(entry.getKey());
                for (Integer renderedPage : entry.getValue()) {
                    state.nextStructureItem();
                    if (declaredPages == null || !declaredPages.contains(renderedPage)) {
                        throw new IOException("Structural XObject has no OBJR for a rendered page");
                    }
                }
            }
            for (Long sequence : state.sequencesOverlappingStructuralObjects) {
                state.resources.checkpointAsIOException();
                if (linkedSequences.contains(sequence)) {
                    throw new IOException("Marked and whole-object structural content items overlap");
                }
            }
            return result;
        }

        private void readParentTree(COSDictionary root) throws IOException {
            COSBase tree = root.getDictionaryObject(PARENT_TREE);
            if (tree == null) {
                return;
            }
            Deque<COSDictionary> pending = new ArrayDeque<COSDictionary>();
            IdentityHashMap<COSDictionary, Boolean> visited = new IdentityHashMap<COSDictionary, Boolean>();
            List<COSDictionary> nodes = new ArrayList<COSDictionary>();
            pending.add(requiredDictionary(tree));
            long previous = -1L;
            while (!pending.isEmpty()) {
                state.nextStructureItem();
                COSDictionary node = pending.removeFirst();
                if (visited.put(node, Boolean.TRUE) != null) {
                    throw new IOException("Cyclic or repeated ParentTree node");
                }
                nodes.add(node);
                COSBase numbers = node.getDictionaryObject(COSName.NUMS);
                COSBase children = node.getDictionaryObject(COSName.KIDS);
                if (numbers != null && children != null) {
                    throw new IOException("ParentTree has both Nums and Kids");
                }
                if (numbers != null) {
                    if (!(numbers instanceof COSArray) || ((COSArray) numbers).size() % 2 != 0) {
                        throw new IOException("ParentTree Nums is malformed");
                    }
                    COSArray entries = (COSArray) numbers;
                    for (int index = 0; index < entries.size(); index += 2) {
                        state.nextStructureItem();
                        COSBase rawKey = entries.getObject(index);
                        if (!(rawKey instanceof COSInteger)) {
                            throw new IOException("ParentTree key is not an integer");
                        }
                        long key = ((COSInteger) rawKey).longValue();
                        if (key <= previous || key > Integer.MAX_VALUE) {
                            throw new IOException("ParentTree keys are unordered or out of range");
                        }
                        previous = key;
                        COSBase value = entries.getObject(index + 1);
                        if (parentTree.put(Integer.valueOf((int) key), value) != null) {
                            throw new IOException("ParentTree key is repeated");
                        }
                        if (value instanceof COSArray) {
                            COSArray parents = (COSArray) value;
                            for (int parent = 0; parent < parents.size(); parent++) {
                                state.nextStructureItem();
                                if (parents.getObject(parent) != null && parents.getObject(parent) != COSNull.NULL) {
                                    requireStructureParentReference(parents.get(parent));
                                }
                            }
                        } else {
                            requireStructureParentReference(entries.get(index + 1));
                        }
                    }
                } else if (children instanceof COSArray && ((COSArray) children).size() > 0) {
                    COSArray array = (COSArray) children;
                    for (int index = array.size() - 1; index >= 0; index--) {
                        state.nextStructureItem();
                        if (!(array.get(index) instanceof COSObject)) {
                            throw new IOException("ParentTree child is not indirect");
                        }
                        pending.addFirst(requiredDictionary(array.getObject(index)));
                    }
                } else {
                    throw new IOException("ParentTree node has no Nums or Kids");
                }
            }
            validateParentTreeRanges(nodes);
        }

        private void validateParentTreeRanges(List<COSDictionary> nodes) throws IOException {
            IdentityHashMap<COSDictionary, long[]> ranges = new IdentityHashMap<COSDictionary, long[]>();
            for (int index = nodes.size() - 1; index >= 0; index--) {
                state.resources.checkpointAsIOException();
                COSDictionary node = nodes.get(index);
                COSBase numbers = node.getDictionaryObject(COSName.NUMS);
                long[] range;
                if (numbers != null) {
                    COSArray entries = (COSArray) numbers;
                    if (entries.size() == 0) {
                        if (index != 0 || node.getDictionaryObject(COSName.LIMITS) != null) {
                            throw new IOException("ParentTree has an empty child range");
                        }
                        continue;
                    }
                    range = new long[] {
                        ((COSInteger) entries.getObject(0)).longValue(),
                        ((COSInteger) entries.getObject(entries.size() - 2)).longValue()
                    };
                } else {
                    COSArray children = (COSArray) node.getDictionaryObject(COSName.KIDS);
                    long[] first = ranges.get(requiredDictionary(children.getObject(0)));
                    long[] last = ranges.get(requiredDictionary(children.getObject(children.size() - 1)));
                    if (first == null || last == null) {
                        throw new IOException("ParentTree child range is unavailable");
                    }
                    range = new long[] {first[0], last[1]};
                }
                COSBase declared = node.getDictionaryObject(COSName.LIMITS);
                if (index == 0) {
                    if (declared != null) {
                        throw new IOException("ParentTree root must not declare Limits");
                    }
                } else {
                    if (!(declared instanceof COSArray) || ((COSArray) declared).size() != 2) {
                        throw new IOException("ParentTree child Limits are missing or malformed");
                    }
                    COSArray limits = (COSArray) declared;
                    for (int endpoint = 0; endpoint < 2; endpoint++) {
                        COSBase value = limits.getObject(endpoint);
                        if (!(value instanceof COSInteger) || ((COSInteger) value).longValue() != range[endpoint]) {
                            throw new IOException("ParentTree Limits do not match its keys");
                        }
                    }
                }
                ranges.put(node, range);
            }
        }

        private static void requireStructureParentReference(COSBase raw) throws IOException {
            if (!(raw instanceof COSObject)) {
                throw new IOException("ParentTree value is not an indirect structure element");
            }
            requiredDictionary(((COSObject) raw).getObject());
        }

        private void verifyParent(COSDictionary container, Integer mcid, COSDictionary expectedParent)
                throws IOException {
            if (container.getDictionaryObject(STRUCT_PARENT) != null
                    && container.getDictionaryObject(STRUCT_PARENTS) != null) {
                throw new IOException("An object cannot contain both StructParent and StructParents");
            }
            Integer key = optionalNonNegativeInteger(container, mcid == null ? STRUCT_PARENT : STRUCT_PARENTS);
            if (key == null) {
                throw new IOException("Structure content item has no ParentTree key");
            }
            COSDictionary existing = parentTreeOwners.put(key, container);
            if (existing != null && existing != container) {
                throw new IOException("Different content containers share a ParentTree key");
            }
            COSBase value = parentTree.get(key);
            if (mcid != null) {
                if (!(value instanceof COSArray) || mcid.intValue() >= ((COSArray) value).size()) {
                    throw new IOException("ParentTree has no marked-content slot");
                }
                value = ((COSArray) value).getObject(mcid.intValue());
            }
            if (value != expectedParent) {
                throw new IOException("ParentTree backlink is inconsistent");
            }
        }

        private void readRoleMap(COSDictionary root) throws IOException {
            COSBase value = root.getDictionaryObject(ROLE_MAP);
            if (value == null) {
                return;
            }
            COSDictionary mappings = requiredDictionary(value);
            boolean accountEntries = accountedRoleMaps.put(mappings, Boolean.TRUE) == null;
            for (COSName key : mappings.keySet()) {
                if (accountEntries) {
                    state.nextRoleMapping();
                }
                COSBase mapped = mappings.getDictionaryObject(key);
                if (!(mapped instanceof COSName)) {
                    throw new IOException("RoleMap value is not a name");
                }
                String declared = key.getName();
                String resolved = ((COSName) mapped).getName();
                if (accountEntries) {
                    account(declared);
                    account(resolved);
                }
                roleMap.put(declared, resolved);
            }
        }

        private void readNamespaces(COSDictionary root) throws IOException {
            COSBase value = root.getDictionaryObject(NAMESPACES);
            if (value == null) {
                return;
            }
            if (!(value instanceof COSArray)) {
                throw new IOException("Namespaces is not an array");
            }
            COSArray declared = (COSArray) value;
            Deque<COSDictionary> pending = new ArrayDeque<COSDictionary>();
            for (int index = 0; index < declared.size(); index++) {
                state.resources.checkpointAsIOException();
                COSDictionary namespace = requiredDictionary(declared.getObject(index));
                admitNamespace(namespace, pending);
                declaredNamespaces.put(namespace, Boolean.TRUE);
            }
            while (!pending.isEmpty()) {
                state.resources.checkpointAsIOException();
                COSDictionary namespace = pending.removeFirst();
                if (namespaces.containsKey(namespace)) {
                    continue;
                }
                COSBase type = namespace.getDictionaryObject(COSName.TYPE);
                if (type != null && !COSName.getPDFName("Namespace").equals(type)) {
                    throw new IOException("Namespace Type is malformed");
                }
                String name = optionalString(namespace, NAMESPACE);
                if (name == null) {
                    throw new IOException("Namespace has no name");
                }
                account(name);
                namespaces.put(namespace, name);
                COSBase mapValue = namespace.getDictionaryObject(ROLE_MAP_NS);
                if (mapValue != null) {
                    COSDictionary dictionary = requiredDictionary(mapValue);
                    Map<String, RoleTarget> mappings = namespaceRoleMaps.get(dictionary);
                    if (mappings == null) {
                        mappings = new HashMap<String, RoleTarget>();
                        boolean accountEntries = accountedRoleMaps.put(dictionary, Boolean.TRUE) == null;
                        for (COSName key : dictionary.keySet()) {
                            if (accountEntries) {
                                state.nextRoleMapping();
                            }
                            COSBase target = dictionary.getDictionaryObject(key);
                            COSDictionary targetNamespace = null;
                            if (target instanceof COSArray) {
                                COSArray pair = (COSArray) target;
                                if (pair.size() != 2 || !(pair.get(1) instanceof COSObject)) {
                                    throw new IOException("Namespace role target is malformed");
                                }
                                targetNamespace = requiredDictionary(pair.getObject(1));
                                admitNamespace(targetNamespace, pending);
                                target = pair.getObject(0);
                            }
                            if (!(target instanceof COSName)) {
                                throw new IOException("Namespace role target has no role name");
                            }
                            String targetRole = ((COSName) target).getName();
                            if (accountEntries) {
                                account(key.getName());
                                account(targetRole);
                            }
                            mappings.put(key.getName(), new RoleTarget(targetRole, targetNamespace));
                        }
                        namespaceRoleMaps.put(dictionary, mappings);
                    }
                    namespaceMappings.put(namespace, mappings);
                }
            }
        }

        private void admitNamespace(COSDictionary namespace, Deque<COSDictionary> pending) throws IOException {
            if (!admittedNamespaces.containsKey(namespace)) {
                state.nextRoleMapping();
                admittedNamespaces.put(namespace, Boolean.TRUE);
                pending.addLast(namespace);
            }
        }

        private void validateStandardNamespaceMappings() throws IOException {
            for (Map.Entry<COSDictionary, Map<String, RoleTarget>> entry : namespaceMappings.entrySet()) {
                String source = namespaces.get(entry.getKey());
                if (!PDF_NAMESPACE.equals(source) && !PDF_TWO_NAMESPACE.equals(source)) {
                    continue;
                }
                for (RoleTarget target : entry.getValue().values()) {
                    state.resources.checkpointAsIOException();
                    String destination = target.namespace == null ? PDF_NAMESPACE : namespaces.get(target.namespace);
                    if (source.equals(destination)) {
                        throw new IOException("Explicit standard namespace mapping targets the same namespace");
                    }
                }
            }
        }

        private List<LogicalStructureElement> rootElements(
                COSBase value,
                Language inherited,
                COSDictionary root) throws IOException, DocumentFailure {
            List<LogicalStructureElement> roots =
                    new ArrayList<LogicalStructureElement>();
            IdentityHashMap<COSDictionary, Boolean> active =
                    new IdentityHashMap<COSDictionary, Boolean>();
            if (value instanceof COSArray) {
                COSArray array = (COSArray) value;
                for (int index = 0; index < array.size(); index++) {
                    state.nextStructureItem();
                    roots.add(element(
                            requiredDictionary(array.getObject(index)),
                            1,
                            inherited,
                            null,
                            root,
                            active));
                }
            } else {
                state.nextStructureItem();
                roots.add(element(
                        requiredDictionary(value),
                        1,
                        inherited,
                        null,
                        root,
                        active));
            }
            return roots;
        }

        private LogicalStructureElement element(
                COSDictionary dictionary,
                int depth,
                Language inheritedLanguage,
                Integer inheritedPage,
                COSDictionary expectedParent,
                IdentityHashMap<COSDictionary, Boolean> active)
                throws IOException, DocumentFailure {
            Deque<ElementFrame> stack = new ArrayDeque<ElementFrame>();
            stack.push(beginElement(
                    dictionary,
                    depth,
                    inheritedLanguage,
                    inheritedPage,
                    expectedParent,
                    active));
            while (!stack.isEmpty()) {
                state.resources.checkpointAsIOException();
                ElementFrame current = stack.peek();
                if (!current.hasNextChild()) {
                    LogicalStructureElement completed = current.detach();
                    stack.pop();
                    active.remove(current.dictionary);
                    if (stack.isEmpty()) {
                        return completed;
                    }
                    stack.peek().children.add(
                            LogicalStructureItem.element(completed));
                    continue;
                }

                COSBase value = current.nextChild();
                state.nextStructureItem();
                if (value instanceof COSInteger) {
                    long markedContentId = ((COSInteger) value).longValue();
                    if (markedContentId < 0L
                            || markedContentId > Integer.MAX_VALUE) {
                        throw new IOException(
                                "Marked-content identifier is out of range");
                    }
                    current.children.add(LogicalStructureItem.markedContent(
                            contentReference(
                                    (int) markedContentId,
                                    current.page,
                                    current.dictionary)));
                    continue;
                }
                COSDictionary child = requiredDictionary(value);
                COSBase type = child.getDictionaryObject(COSName.TYPE);
                if (type != null && !(type instanceof COSName)) {
                    throw new IOException(
                            "Logical-structure child Type is malformed");
                }
                if (MCR.equals(type)) {
                    current.children.add(LogicalStructureItem.markedContent(
                            contentReference(child, current.page, current.dictionary)));
                } else if (OBJR.equals(type)) {
                    current.children.add(LogicalStructureItem.objectReference(
                            objectReference(child, current.page, current.dictionary)));
                } else if (type == null || COSName.STRUCT_ELEM.equals(type)) {
                    stack.push(beginElement(
                            child,
                            current.depth + 1,
                            current.effectiveLanguage,
                            current.page,
                            current.dictionary,
                            active));
                } else {
                    throw new IOException(
                            "Unsupported logical-structure child");
                }
            }
            throw new IOException("Logical structure traversal failed");
        }

        private ElementFrame beginElement(
                COSDictionary dictionary,
                int depth,
                Language inheritedLanguage,
                Integer inheritedPage,
                COSDictionary expectedParent,
                IdentityHashMap<COSDictionary, Boolean> active)
                throws IOException, DocumentFailure {
            if (visitedElements.put(dictionary, Boolean.TRUE) != null) {
                throw new IOException("Logical-structure element is repeated");
            }
            if (active.put(dictionary, Boolean.TRUE) != null) {
                throw new IOException("Cyclic logical structure");
            }
            boolean accepted = false;
            try {
                COSBase type = dictionary.getDictionaryObject(COSName.TYPE);
                if (type != null && !COSName.STRUCT_ELEM.equals(type)) {
                    throw new IOException("StructElem Type is malformed");
                }
                COSBase namespaceValue = dictionary.getDictionaryObject(NAMESPACE);
                String namespaceName = null;
                ObjectReference namespaceReference = null;
                if (namespaceValue != null) {
                    namespaceName = namespaces.get(requiredDictionary(namespaceValue));
                    if (!(dictionary.getItem(NAMESPACE) instanceof COSObject)
                            || !declaredNamespaces.containsKey(namespaceValue)) {
                        throw new IOException("Element namespace is absent from Namespaces");
                    }
                    namespaceReference = valueAdapter.resourceReference((COSObject) dictionary.getItem(NAMESPACE));
                }
                if (dictionary.getDictionaryObject(COSName.P) != expectedParent) {
                    throw new IOException("StructElem parent is inconsistent");
                }
                int id = state.nextStructureElement(depth);
                String role = requiredName(dictionary, STRUCTURE_TYPE);
                RoleResult resolved = resolveRole(role, (COSDictionary) namespaceValue);
                String declaredLanguage = optionalString(
                        dictionary, COSName.LANG);
                Language effective = effectiveLanguage(
                        declaredLanguage, inheritedLanguage);
                String alternate = optionalString(dictionary, COSName.ALT);
                String actual = optionalString(
                        dictionary, COSName.ACTUAL_TEXT);
                account(role);
                if (resolved.resolvedRole != null
                        && !resolved.resolvedRole.equals(role)) {
                    account(resolved.resolvedRole);
                }
                accountNullable(declaredLanguage);
                if (declaredLanguage == null) {
                    accountNullable(effective.value);
                }
                accountNullable(alternate);
                accountNullable(actual);
                Integer page = elementPage(dictionary, inheritedPage);
                ElementFrame frame = new ElementFrame(
                        dictionary,
                        depth,
                        id,
                        role,
                        resolved,
                        namespaceName,
                        namespaceReference,
                        declaredLanguage,
                        effective,
                        alternate,
                        actual,
                        page,
                        dictionary.getDictionaryObject(CHILDREN));
                accepted = true;
                return frame;
            } finally {
                if (!accepted) {
                    active.remove(dictionary);
                }
            }
        }

        private LogicalObjectReference objectReference(
                COSDictionary dictionary, Integer inheritedPage, COSDictionary expectedParent)
                throws IOException, DocumentFailure {
            Integer page = elementPage(dictionary, inheritedPage);
            state.nextStructureItem();
            COSBase raw = dictionary.getItem(OBJECT);
            COSBase object = dictionary.getDictionaryObject(OBJECT);
            if (page == null || !(raw instanceof COSObject)) {
                throw new IOException("OBJR requires an indirect object and a page");
            }
            if (!(object instanceof COSDictionary)) {
                throw new IOException("OBJR object is not a dictionary");
            }
            COSDictionary target = (COSDictionary) object;
            verifyParent(target, null, expectedParent);
            String subtype = requiredName(target, COSName.SUBTYPE);
            COSDictionary pageDictionary = pageDictionaries.get(page.intValue() - 1);
            if (object instanceof COSStream) {
                COSBase type = target.getDictionaryObject(COSName.TYPE);
                if ((type != null && !COSName.XOBJECT.equals(type))
                        || (type == null && !"Form".equals(subtype))
                        || !("Form".equals(subtype) || "Image".equals(subtype))) {
                    throw new IOException("OBJR XObject is not associated with the declared page");
                }
                recordPageAssociation((COSStream) target, page.intValue());
                recordPage(referencedObjectPages, (COSStream) target, page.intValue());
                if ("Form".equals(subtype)) {
                    registerDefinitionRoot((COSStream) target, page.intValue(), null);
                }
            } else {
                requireAnnotationPage(target, pageDictionary);
            }
            account(subtype);
            return new LogicalObjectReference(page.intValue(),
                    valueAdapter.resourceReference((COSObject) raw), subtype);
        }

        private void requireAnnotationPage(COSDictionary annotation, COSDictionary pageDictionary)
                throws IOException {
            COSBase type = annotation.getDictionaryObject(COSName.TYPE);
            if (type != null && !COSName.ANNOT.equals(type)) {
                throw new IOException("OBJR object is not a page annotation");
            }
            COSBase ownerPage = annotation.getDictionaryObject(COSName.P);
            if (ownerPage != null && ownerPage != pageDictionary) {
                throw new IOException("OBJR annotation page is inconsistent");
            }
            COSBase annotations = pageDictionary.getDictionaryObject(COSName.ANNOTS);
            boolean found = false;
            if (annotations instanceof COSArray) {
                COSArray array = (COSArray) annotations;
                for (int index = 0; index < array.size(); index++) {
                    state.nextStructureItem();
                    if (array.getObject(index) == annotation) {
                        found = true;
                    }
                }
            }
            if (!found) {
                throw new IOException("OBJR annotation is not on the declared page");
            }
        }

        private boolean isPageXObject(COSDictionary pageDictionary, COSDictionary target)
                throws IOException {
            COSDictionary inheritedResources = pageResources(pageDictionary);
            if (inheritedResources == null) {
                return false;
            }
            Deque<COSDictionary> pending = new ArrayDeque<COSDictionary>();
            IdentityHashMap<COSDictionary, Boolean> visited = new IdentityHashMap<COSDictionary, Boolean>();
            pending.add(inheritedResources);
            while (!pending.isEmpty()) {
                COSDictionary resourceDictionary = pending.removeFirst();
                state.nextStructureItem();
                if (visited.put(resourceDictionary, Boolean.TRUE) != null) {
                    continue;
                }
                COSBase value = resourceDictionary.getDictionaryObject(COSName.XOBJECT);
                if (value == null) {
                    continue;
                }
                COSDictionary xobjects = requiredDictionary(value);
                for (COSName name : xobjects.keySet()) {
                    state.nextStructureItem();
                    COSBase xobject = xobjects.getDictionaryObject(name);
                    if (xobject == target) {
                        return true;
                    }
                    if (xobject instanceof COSStream
                            && COSName.FORM.equals(((COSStream) xobject).getDictionaryObject(COSName.SUBTYPE))) {
                        COSBase nested = ((COSStream) xobject).getDictionaryObject(COSName.RESOURCES);
                        if (nested != null) {
                            pending.add(requiredDictionary(nested));
                        }
                    }
                }
            }
            return false;
        }

        private COSDictionary pageResources(COSDictionary pageDictionary) throws IOException {
            COSBase inheritedResources = null;
            COSDictionary ancestor = pageDictionary;
            while (ancestor != null) {
                state.nextStructureItem();
                inheritedResources = ancestor.getDictionaryObject(COSName.RESOURCES);
                if (inheritedResources != null) {
                    break;
                }
                COSBase parent = ancestor.getDictionaryObject(COSName.PARENT);
                ancestor = parent instanceof COSDictionary ? (COSDictionary) parent : null;
            }
            return inheritedResources == null ? null : requiredDictionary(inheritedResources);
        }

        private boolean isPageAppearance(COSDictionary page, COSStream stream) throws IOException {
            COSBase annotations = page.getDictionaryObject(COSName.ANNOTS);
            if (annotations == null) {
                return false;
            }
            if (!(annotations instanceof COSArray)) {
                throw new IOException("Page annotations are malformed");
            }
            COSArray array = (COSArray) annotations;
            for (int index = 0; index < array.size(); index++) {
                state.nextStructureItem();
                COSDictionary annotation = requiredDictionary(array.getObject(index));
                if (annotation.getDictionaryObject(COSName.AP) != null && ownsAppearance(annotation, stream)) {
                    COSBase ownerPage = annotation.getDictionaryObject(COSName.P);
                    if (ownerPage != null && ownerPage != page) {
                        throw new IOException("Appearance owner page is inconsistent");
                    }
                    return true;
                }
            }
            return false;
        }

        private void recordPageAssociation(COSStream stream, int page) throws IOException {
            COSDictionary pageDictionary = pageDictionaries.get(page - 1);
            if (isPageXObject(pageDictionary, stream)
                    || (COSName.FORM.equals(stream.getDictionaryObject(COSName.SUBTYPE))
                            && isPageAppearance(pageDictionary, stream))) {
                return;
            }
            // Descendant associations must be proved by the later bounded appearance Do traversal.
            state.nextStructureItem();
            recordPage(pendingAppearancePages, stream, page);
        }

        private MarkedContentReference contentReference(
                COSDictionary dictionary,
                Integer inheritedPage,
                COSDictionary expectedParent) throws IOException, DocumentFailure {
            Integer page = elementPage(dictionary, inheritedPage);
            Integer mcid = optionalNonNegativeInteger(
                    dictionary, COSName.MCID);
            if (mcid == null) {
                throw new IOException("Marked-content reference has no MCID");
            }
            COSBase stream = dictionary.getDictionaryObject(STREAM);
            if (stream == null) {
                if (dictionary.getDictionaryObject(STREAM_OWNER) != null) {
                    throw new IOException("StmOwn has no associated stream");
                }
                return contentReference(mcid.intValue(), page, expectedParent);
            }
            state.nextStructureItem();
            if (!(dictionary.getItem(STREAM) instanceof COSObject)
                    || !(stream instanceof COSStream)
                    || !COSName.FORM.equals(((COSStream) stream).getDictionaryObject(COSName.SUBTYPE))) {
                throw new IOException("MCR stream is not an indirect Form");
            }
            BoundedDrawObject.validateFormDictionary((COSStream) stream);
            verifyParent((COSStream) stream, mcid, expectedParent);
            ObjectReference streamOwner = null;
            COSBase owner = dictionary.getDictionaryObject(STREAM_OWNER);
            if (owner != null) {
                state.nextStructureItem();
                COSBase rawOwner = dictionary.getItem(STREAM_OWNER);
                if (!(rawOwner instanceof COSObject) || page == null) {
                    throw new IOException("StmOwn requires an indirect owner and a page");
                }
                COSDictionary annotation = requiredDictionary(owner);
                requireAnnotationPage(annotation, pageDictionaries.get(page.intValue() - 1));
                if (!ownsAppearance(annotation, (COSStream) stream)) {
                    throw new IOException("StmOwn does not own the referenced appearance");
                }
                streamOwner = valueAdapter.resourceReference((COSObject) rawOwner);
            } else if (page == null) {
                throw new IOException("MCR Form has no declared page");
            } else {
                recordPageAssociation((COSStream) stream, page.intValue());
            }
            Integer invocations = state.formInvocations.get((COSStream) stream);
            if (invocations != null && (invocations.intValue() > 1 || streamOwner != null)) {
                throw new IOException("A Form with structural MCIDs is invoked repeatedly");
            }
            if (invocations == null) {
                registerDefinitionRoot((COSStream) stream, page.intValue(), (COSDictionary) owner);
                int index = state.requireReferencedMcid((COSStream) stream, mcid.intValue(),
                        pageResources(pageDictionaries.get(page.intValue() - 1)));
                List<ReferencedSequence> sequences = state.referencedSequences.get((COSStream) stream);
                // Negative scopes identify unexecuted stream definitions; positive scopes identify pages.
                long scope = ((long) -state.contentStreamId((COSStream) stream)) << 32;
                linkSequenceIdentity(scope, index);
                Integer parent = sequences.get(index).parentIndex;
                while (parent != null) {
                    linkEnclosingSequence(scope, parent.intValue());
                    parent = sequences.get(parent.intValue()).parentIndex;
                }
            }
            Set<Integer> definitions = referencedMcidDefinitions.get((COSStream) stream);
            if (definitions == null) {
                definitions = new HashSet<Integer>();
                referencedMcidDefinitions.put((COSStream) stream, definitions);
            }
            definitions.add(mcid);
            return contentReference(mcid.intValue(), page,
                    state.contentStreamId((COSStream) stream), streamOwner, invocations != null);
        }

        private boolean ownsAppearance(COSDictionary annotation, COSStream stream) throws IOException {
            COSDictionary appearances = requiredDictionary(annotation.getDictionaryObject(COSName.AP));
            boolean found = false;
            for (COSName kind : Arrays.asList(COSName.N, COSName.R, COSName.D)) {
                COSBase appearance = appearances.getDictionaryObject(kind);
                if (appearance == null) {
                    continue;
                }
                state.nextStructureItem();
                if (appearance instanceof COSStream) {
                    found |= appearance == stream;
                } else {
                    COSDictionary states = requiredDictionary(appearance);
                    for (COSName name : states.keySet()) {
                        state.nextStructureItem();
                        COSBase value = states.getDictionaryObject(name);
                        if (!(value instanceof COSStream)) {
                            throw new IOException("Appearance state is not a stream");
                        }
                        found |= value == stream;
                    }
                }
            }
            return found;
        }

        private void registerDefinitionRoot(COSStream stream, int page, COSDictionary owner) throws IOException {
            if (owner != null) {
                IdentityHashMap<COSDictionary, Boolean> owners = definitionOwners.get(stream);
                if (owners != null && owners.containsKey(owner)) {
                    return;
                }
                state.nextStructureItem();
                if (owners == null) {
                    owners = new IdentityHashMap<COSDictionary, Boolean>();
                    definitionOwners.put(stream, owners);
                }
                owners.put(owner, Boolean.TRUE);
                definitionRoots.add(new DefinitionRoot(stream, page, owner));
                return;
            }
            if (state.formInvocations.containsKey(stream)) {
                return;
            }
            Set<Integer> pagesForStream = definitionPages.get(stream);
            if (pagesForStream == null || !pagesForStream.contains(Integer.valueOf(page))) {
                state.nextStructureItem();
                recordPage(definitionPages, stream, page);
                definitionRoots.add(new DefinitionRoot(stream, page, null));
            }
        }

        private void validateReferencedFormDefinitions() throws IOException {
            registerNormalAppearances();
            IdentityHashMap<COSStream, Boolean> reachedDefinitions = new IdentityHashMap<COSStream, Boolean>();
            for (int rootIndex = 0; rootIndex < definitionRoots.size(); rootIndex++) {
                DefinitionRoot root = definitionRoots.get(rootIndex);
                if (root.owner != null) {
                    recordAppearanceInvocation(root.stream, root.page);
                }
                Deque<DefinitionFrame> frames = new ArrayDeque<DefinitionFrame>();
                IdentityHashMap<COSStream, Boolean> active = new IdentityHashMap<COSStream, Boolean>();
                frames.addLast(definitionFrame(root.stream,
                        pageResources(pageDictionaries.get(root.page - 1)), false));
                active.put(root.stream, Boolean.TRUE);
                while (!frames.isEmpty()) {
                    state.resources.checkpointAsIOException();
                    DefinitionFrame frame = frames.peekLast();
                    if (frame.index == frame.invocations.size()) {
                        frames.removeLast();
                        active.remove(frame.stream);
                        continue;
                    }
                    state.nextStructureItem();
                    ReferencedInvocation invocation = frame.invocations.get(frame.index++);
                    boolean inside = frame.insideItem;
                    Integer parent = invocation.parentIndex;
                    while (parent != null && !inside) {
                        state.nextStructureItem();
                        inside = frame.linkedMarkers.contains(parent);
                        parent = frame.sequences.get(parent.intValue()).parentIndex;
                    }
                    if (frame.resources == null) {
                        throw new IOException("Referenced Form invocation has no resources");
                    }
                    COSBase target = requiredDictionary(frame.resources.getDictionaryObject(COSName.XOBJECT))
                            .getDictionaryObject(invocation.resourceName);
                    if (!(target instanceof COSStream)) {
                        throw new IOException("Referenced Form invocation has no XObject stream");
                    }
                    COSStream stream = (COSStream) target;
                    if (inside && referencedObjectPages.containsKey(stream)) {
                        throw new IOException("Unexecuted structural content items overlap");
                    }
                    if (root.owner != null) {
                        recordAppearanceInvocation(stream, root.page);
                    }
                    COSBase subtype = stream.getDictionaryObject(COSName.SUBTYPE);
                    if (COSName.IMAGE.equals(subtype)) {
                        continue;
                    }
                    if (!COSName.FORM.equals(subtype) || active.containsKey(stream)) {
                        throw new IOException("Referenced Form invocation graph is malformed");
                    }
                    reachedDefinitions.put(stream, Boolean.TRUE);
                    if (referencedMcidDefinitions.containsKey(stream)
                            && (root.linkedInvocations.put(stream, Boolean.TRUE) != null
                                    || state.formInvocations.containsKey(stream))) {
                        throw new IOException("A Form with structural MCIDs is invoked repeatedly");
                    }
                    state.resources.requireNestingDepthAsIOException(frames.size() + 1);
                    frames.addLast(definitionFrame(stream, frame.resources, inside));
                    active.put(stream, Boolean.TRUE);
                }
            }
            if (!pendingAppearancePages.isEmpty()) {
                throw new IOException("Structural XObject is not associated with the declared page");
            }
            IdentityHashMap<COSStream, Boolean> invocations = new IdentityHashMap<COSStream, Boolean>();
            for (DefinitionRoot root : definitionRoots) {
                state.nextStructureItem();
                boolean appearance = root.owner != null;
                if (!appearance && (reachedDefinitions.containsKey(root.stream)
                        || definitionOwners.containsKey(root.stream))) {
                    continue;
                }
                if (appearance && referencedMcidDefinitions.containsKey(root.stream)) {
                    recordDefinitionInvocation(root.stream, invocations);
                }
                for (COSStream stream : root.linkedInvocations.keySet()) {
                    state.nextStructureItem();
                    recordDefinitionInvocation(stream, invocations);
                }
            }
        }

        private void recordAppearanceInvocation(COSStream stream, int page) throws IOException {
            if (stream.getDictionaryObject(STRUCT_PARENT) != null) {
                state.nextStructureItem();
                recordPage(state.renderedObjectPages, stream, page);
            }
            Set<Integer> pending = pendingAppearancePages.get(stream);
            if (pending != null) {
                pending.remove(Integer.valueOf(page));
                if (pending.isEmpty()) {
                    pendingAppearancePages.remove(stream);
                }
            }
        }

        private void registerNormalAppearances() throws IOException {
            if (referencedMcidDefinitions.isEmpty() && referencedObjectPages.isEmpty()
                    && pendingAppearancePages.isEmpty()) {
                return;
            }
            for (int pageIndex = 0; pageIndex < pageDictionaries.size(); pageIndex++) {
                COSDictionary page = pageDictionaries.get(pageIndex);
                COSBase annotations = page.getDictionaryObject(COSName.ANNOTS);
                if (annotations == null) {
                    continue;
                }
                if (!(annotations instanceof COSArray)) {
                    throw new IOException("Page annotations are malformed");
                }
                COSArray array = (COSArray) annotations;
                for (int index = 0; index < array.size(); index++) {
                    state.nextStructureItem();
                    COSDictionary annotation = requiredDictionary(array.getObject(index));
                    COSBase declared = annotation.getDictionaryObject(COSName.AP);
                    if (declared == null) {
                        continue;
                    }
                    state.nextStructureItem();
                    COSBase normal = requiredDictionary(declared).getDictionaryObject(COSName.N);
                    if (normal instanceof COSDictionary && !(normal instanceof COSStream)) {
                        COSBase appearanceState = annotation.getDictionaryObject(COSName.AS);
                        normal = appearanceState instanceof COSName
                                ? ((COSDictionary) normal).getDictionaryObject((COSName) appearanceState) : null;
                    }
                    if (!(normal instanceof COSStream)) {
                        continue;
                    }
                    state.nextStructureItem();
                    COSBase ownerPage = annotation.getDictionaryObject(COSName.P);
                    if (ownerPage != null && ownerPage != page) {
                        throw new IOException("Appearance owner page is inconsistent");
                    }
                    COSStream stream = (COSStream) normal;
                    state.nextStructureItem();
                    registerDefinitionRoot(stream, pageIndex + 1, annotation);
                }
            }
        }

        private void recordDefinitionInvocation(COSStream stream, IdentityHashMap<COSStream, Boolean> invocations)
                throws IOException {
            if (state.formInvocations.containsKey(stream) || invocations.put(stream, Boolean.TRUE) != null) {
                throw new IOException("A Form with structural MCIDs is invoked repeatedly");
            }
        }

        private DefinitionFrame definitionFrame(COSStream stream, COSDictionary inheritedResources, boolean inside)
                throws IOException {
            BoundedDrawObject.validateFormDictionary(stream);
            List<ReferencedSequence> sequences = state.readReferencedSequences(stream);
            COSBase declared = stream.getDictionaryObject(COSName.RESOURCES);
            COSDictionary effective = declared == null ? inheritedResources : requiredDictionary(declared);
            Map<Integer, Integer> definitions = state.referencedMcidDefinitions(stream, effective);
            Set<Integer> linked = new HashSet<Integer>();
            Set<Integer> mcids = referencedMcidDefinitions.get(stream);
            if (mcids != null) {
                for (Integer mcid : mcids) {
                    Integer index = definitions.get(mcid);
                    if (index == null) {
                        throw new IOException("Referenced Form does not define the MCR's MCID");
                    }
                    linked.add(index);
                }
            }
            boolean whole = referencedObjectPages.containsKey(stream);
            if ((inside && (whole || !linked.isEmpty())) || (whole && !linked.isEmpty())) {
                throw new IOException("Unexecuted structural content items overlap");
            }
            return new DefinitionFrame(stream, effective, sequences,
                    state.referencedInvocations.get(stream), linked, inside || whole);
        }

        private static final class DefinitionRoot {

            private final COSStream stream;
            private final int page;
            private final COSDictionary owner;
            private final IdentityHashMap<COSStream, Boolean> linkedInvocations =
                    new IdentityHashMap<COSStream, Boolean>();

            DefinitionRoot(COSStream stream, int page, COSDictionary owner) {
                this.stream = stream;
                this.page = page;
                this.owner = owner;
            }
        }

        private static final class DefinitionFrame {

            private final COSStream stream;
            private final COSDictionary resources;
            private final List<ReferencedSequence> sequences;
            private final List<ReferencedInvocation> invocations;
            private final Set<Integer> linkedMarkers;
            private final boolean insideItem;
            private int index;

            DefinitionFrame(COSStream stream, COSDictionary resources, List<ReferencedSequence> sequences,
                    List<ReferencedInvocation> invocations, Set<Integer> linkedMarkers, boolean insideItem) {
                this.stream = stream;
                this.resources = resources;
                this.sequences = sequences;
                this.invocations = invocations;
                this.linkedMarkers = linkedMarkers;
                this.insideItem = insideItem;
            }
        }

        private MarkedContentReference contentReference(
                int markedContentId,
                Integer page,
                COSDictionary expectedParent) throws IOException {
            if (page == null) {
                throw new IOException("Marked-content reference has no page");
            }
            verifyParent(pageDictionaries.get(page.intValue() - 1),
                    Integer.valueOf(markedContentId), expectedParent);
            return contentReference(markedContentId, page, 0, null, true);
        }

        private MarkedContentReference contentReference(
                int markedContentId,
                Integer page,
                int contentStreamId,
                ObjectReference streamOwner,
                boolean requireOccurrence) throws IOException {
            if (markedContentId < 0 || page == null) {
                throw new IOException("Marked-content reference has no page");
            }
            Integer sequenceId = null;
            for (MarkedContentSequence sequence : pages.get(
                    page.intValue() - 1).getMarkedContentSequences()) {
                state.resources.checkpointAsIOException();
                if (sequence.getContentStreamId() == contentStreamId
                        && sequence.getMarkedContentId().isPresent()
                        && sequence.getMarkedContentId().get().intValue()
                                == markedContentId) {
                    if (sequenceId != null) {
                        throw new IOException("Ambiguous stream MCID");
                    }
                    linkSequence(page.intValue(), sequence);
                    sequenceId = Integer.valueOf(sequence.getId());
                }
            }
            if (requireOccurrence && sequenceId == null) {
                throw new IOException("MCR does not identify an executed MCID definition");
            }
            return new MarkedContentReference(
                    page.intValue(), markedContentId, contentStreamId, sequenceId, streamOwner);
        }

        private void linkSequence(int pageNumber, MarkedContentSequence sequence) throws IOException {
            long pageScope = ((long) pageNumber) << 32;
            linkSequenceIdentity(pageScope, sequence.getId());
            Integer parent = sequence.getParentId().orElse(null);
            List<MarkedContentSequence> sequences = pages.get(pageNumber - 1).getMarkedContentSequences();
            while (parent != null) {
                linkEnclosingSequence(pageScope, parent.intValue());
                parent = sequences.get(parent.intValue() - 1).getParentId().orElse(null);
            }
        }

        private void linkSequenceIdentity(long scope, int identifier) throws IOException {
            Long identity = Long.valueOf(scope | identifier);
            if (!linkedSequences.add(identity) || enclosingSequences.contains(identity)) {
                throw new IOException("Structural content items overlap");
            }
        }

        private void linkEnclosingSequence(long scope, int identifier) throws IOException {
            state.nextStructureItem();
            Long enclosing = Long.valueOf(scope | identifier);
            if (linkedSequences.contains(enclosing)) {
                throw new IOException("Structural content items overlap");
            }
            enclosingSequences.add(enclosing);
        }

        private Integer elementPage(
                COSDictionary dictionary,
                Integer inheritedPage) throws IOException {
            COSBase value = dictionary.getDictionaryObject(PAGE);
            if (value == null) {
                return inheritedPage;
            }
            if (!(value instanceof COSDictionary)) {
                throw new IOException("Structure page is not a dictionary");
            }
            Integer number = pageNumbers.get((COSDictionary) value);
            if (number == null) {
                throw new IOException("Structure page is not in the document");
            }
            return number;
        }

        private RoleResult resolveRole(String role) throws IOException {
            if (STANDARD_ROLES.contains(role)
                    && (document.getVersion() < 1.5f || !roleMap.containsKey(role))) {
                return new RoleResult(
                        role,
                        LogicalStructureElement.RoleResolution.STANDARD);
            }
            String current = role;
            Set<String> visited = new HashSet<String>();
            while (roleMap.containsKey(current)) {
                state.resources.checkpointAsIOException();
                if (!visited.add(current)) {
                    return new RoleResult(null, LogicalStructureElement.RoleResolution.UNRESOLVED);
                }
                current = roleMap.get(current);
                if (STANDARD_ROLES.contains(current)
                        && (document.getVersion() < 2f || !roleMap.containsKey(current))) {
                    return new RoleResult(
                            current,
                            LogicalStructureElement.RoleResolution.ROLE_MAP);
                }
            }
            return new RoleResult(
                    null,
                    LogicalStructureElement.RoleResolution.UNRESOLVED);
        }

        private RoleResult resolveRole(String role, COSDictionary namespace) throws IOException {
            if (namespace == null) {
                return resolveRole(role);
            }
            IdentityHashMap<COSDictionary, Set<String>> visited = new IdentityHashMap<COSDictionary, Set<String>>();
            String currentRole = role;
            COSDictionary currentNamespace = namespace;
            boolean mapped = false;
            while (true) {
                state.resources.checkpointAsIOException();
                String name = currentNamespace == null ? PDF_NAMESPACE : namespaces.get(currentNamespace);
                if (isStandardRole(currentRole, name)) {
                    return new RoleResult(currentRole, mapped ? LogicalStructureElement.RoleResolution.ROLE_MAP
                            : LogicalStructureElement.RoleResolution.STANDARD, name);
                }
                Map<String, RoleTarget> mappings = namespaceMappings.get(currentNamespace);
                if (mappings == null || !mappings.containsKey(currentRole)) {
                    return new RoleResult(null, LogicalStructureElement.RoleResolution.UNRESOLVED);
                }
                Set<String> roles = visited.get(currentNamespace);
                if (roles == null) {
                    roles = new HashSet<String>();
                    visited.put(currentNamespace, roles);
                }
                if (!roles.add(currentRole)) {
                    return new RoleResult(null, LogicalStructureElement.RoleResolution.UNRESOLVED);
                }
                RoleTarget target = mappings.get(currentRole);
                currentRole = target.role;
                currentNamespace = target.namespace;
                mapped = true;
            }
        }

        private static boolean isStandardRole(String role, String namespace) {
            if (PDF_NAMESPACE.equals(namespace)) {
                return STANDARD_ROLES.contains(role);
            }
            if (!PDF_TWO_NAMESPACE.equals(namespace)) {
                return false;
            }
            if (Arrays.asList("Aside", "Artifact", "DocumentFragment", "Em", "FENote", "Strong", "Sub", "Title")
                    .contains(role)) {
                return true;
            }
            if (role.length() > 1 && role.charAt(0) == 'H' && role.charAt(1) >= '1' && role.charAt(1) <= '9') {
                boolean heading = true;
                for (int index = 2; index < role.length(); index++) {
                    heading &= role.charAt(index) >= '0' && role.charAt(index) <= '9';
                }
                if (heading) {
                    return true;
                }
            }
            return STANDARD_ROLES.contains(role)
                    && !Arrays.asList("Art", "BlockQuote", "BibEntry", "Code", "Index", "Note", "Private",
                            "Quote", "Reference", "TOC", "TOCI").contains(role);
        }

        private void account(String value) throws IOException {
            state.accountUnicode(value);
        }

        private void accountNullable(String value)
                throws IOException {
            if (value != null) {
                account(value);
            }
        }

        private static final class ElementFrame {

            private final COSDictionary dictionary;
            private final int depth;
            private final int id;
            private final String role;
            private final RoleResult resolved;
            private final String namespaceName;
            private final ObjectReference namespaceReference;
            private final String declaredLanguage;
            private final Language effectiveLanguage;
            private final String alternate;
            private final String actual;
            private final Integer page;
            private final COSBase childValue;
            private final List<LogicalStructureItem> children =
                    new ArrayList<LogicalStructureItem>();
            private int childIndex;

            ElementFrame(
                    COSDictionary dictionary,
                    int depth,
                    int id,
                    String role,
                    RoleResult resolved,
                    String namespaceName,
                    ObjectReference namespaceReference,
                    String declaredLanguage,
                    Language effectiveLanguage,
                    String alternate,
                    String actual,
                    Integer page,
                    COSBase childValue) {
                this.dictionary = dictionary;
                this.depth = depth;
                this.id = id;
                this.role = role;
                this.resolved = resolved;
                this.namespaceName = namespaceName;
                this.namespaceReference = namespaceReference;
                this.declaredLanguage = declaredLanguage;
                this.effectiveLanguage = effectiveLanguage;
                this.alternate = alternate;
                this.actual = actual;
                this.page = page;
                this.childValue = childValue;
            }

            boolean hasNextChild() {
                if (childValue == null) {
                    return false;
                }
                if (childValue instanceof COSArray) {
                    return childIndex < ((COSArray) childValue).size();
                }
                return childIndex == 0;
            }

            COSBase nextChild() {
                if (childValue instanceof COSArray) {
                    return ((COSArray) childValue).getObject(childIndex++);
                }
                childIndex++;
                return childValue;
            }

            LogicalStructureElement detach() {
                return new LogicalStructureElement(
                        id,
                        role,
                        resolved.resolvedRole,
                        resolved.resolution,
                        namespaceName,
                        namespaceReference,
                        resolved.namespaceName,
                        declaredLanguage,
                        effectiveLanguage.value,
                        effectiveLanguage.source,
                        alternate,
                        actual,
                        children);
            }
        }

        private static Language effectiveLanguage(
                String declared,
                Language inherited) {
            if (declared != null) {
                return new Language(
                        declared,
                        LogicalStructureElement.LanguageSource.SELF);
            }
            if (inherited.source
                    == LogicalStructureElement.LanguageSource.SELF
                    || inherited.source
                    == LogicalStructureElement.LanguageSource.ANCESTOR) {
                return new Language(
                        inherited.value,
                        LogicalStructureElement.LanguageSource.ANCESTOR);
            }
            return inherited;
        }
    }

    private static final class RoleTarget {

        private final String role;
        private final COSDictionary namespace;

        RoleTarget(String role, COSDictionary namespace) {
            this.role = role;
            this.namespace = namespace;
        }
    }

    private static final class RoleResult {

        private final String resolvedRole;
        private final LogicalStructureElement.RoleResolution resolution;
        private final String namespaceName;

        RoleResult(
                String resolvedRole,
                LogicalStructureElement.RoleResolution resolution) {
            this(resolvedRole, resolution, resolvedRole == null ? null : StructureExtractor.PDF_NAMESPACE);
        }

        RoleResult(String resolvedRole, LogicalStructureElement.RoleResolution resolution, String namespaceName) {
            this.resolvedRole = resolvedRole;
            this.resolution = resolution;
            this.namespaceName = namespaceName;
        }
    }

    private static final class Language {

        private final String value;
        private final LogicalStructureElement.LanguageSource source;

        Language(
                String value,
                LogicalStructureElement.LanguageSource source) {
            this.value = value;
            this.source = source;
        }

        static Language none() {
            return new Language(
                    null,
                    LogicalStructureElement.LanguageSource.NONE);
        }
    }

    private static void recordPage(
            IdentityHashMap<COSStream, Set<Integer>> pages, COSStream stream, int pageNumber) {
        Set<Integer> occurrences = pages.get(stream);
        if (occurrences == null) {
            occurrences = new HashSet<Integer>();
            pages.put(stream, occurrences);
        }
        occurrences.add(Integer.valueOf(pageNumber));
    }

    private static String optionalString(
            COSDictionary dictionary,
            COSName name) throws IOException {
        if (dictionary == null) {
            return null;
        }
        COSBase value = dictionary.getDictionaryObject(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof COSString)) {
            throw new IOException(name.getName() + " is not a string");
        }
        return ((COSString) value).getString();
    }

    private static Integer optionalNonNegativeInteger(
            COSDictionary dictionary,
            COSName name) throws IOException {
        if (dictionary == null) {
            return null;
        }
        COSBase value = dictionary.getDictionaryObject(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof COSInteger)) {
            throw new IOException(name.getName() + " is not an integer");
        }
        long number = ((COSInteger) value).longValue();
        if (number < 0L || number > Integer.MAX_VALUE) {
            throw new IOException(name.getName() + " is out of range");
        }
        return Integer.valueOf((int) number);
    }

    private static COSDictionary requiredDictionary(COSBase value)
            throws IOException {
        if (!(value instanceof COSDictionary)
                || value instanceof COSStream) {
            throw new IOException("Expected a structure dictionary");
        }
        return (COSDictionary) value;
    }

    private static String requiredName(
            COSDictionary dictionary,
            COSName name) throws IOException {
        COSBase value = dictionary.getDictionaryObject(name);
        if (!(value instanceof COSName)) {
            throw new IOException(name.getName() + " is not a name");
        }
        return ((COSName) value).getName();
    }

    private static final class SourceCode {

        private final int numericValue;
        private final byte[] bytes;

        SourceCode(int numericValue, byte[] bytes) {
            this.numericValue = numericValue;
            this.bytes = bytes;
        }
    }

    private static final class SourceCodeFrame {

        private final PDFont font;
        private final byte[] source;
        private final ByteArrayInputStream input;
        private final ExtractionState state;
        private int offset;

        SourceCodeFrame(
                PDFont font,
                byte[] source,
                ExtractionState state) {
            this.font = font;
            this.source = source;
            this.input = new ByteArrayInputStream(source);
            this.state = state;
        }

        boolean hasRemaining() {
            return input.available() > 0;
        }

        SourceCode next() throws IOException {
            if (!hasRemaining()) {
                throw new IOException("Text source code was unavailable");
            }
            int before = input.available();
            int code = font.readCode(input);
            int length = before - input.available();
            if (length < 1) {
                throw new IOException("Font consumed no source-code bytes");
            }
            byte[] bytes = state.provisionalSourceCode(
                    source, offset, length);
            offset += length;
            return new SourceCode(code, bytes);
        }
    }

    private static final class ExtractionLimitException extends IOException {

        private static final long serialVersionUID = 1L;
    }

    private static final class ExtractionLimitRuntimeException
            extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }

    private static final class ExtractionMalformedRuntimeException
            extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }

    private static BigDecimal decimal(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new ExtractionMalformedRuntimeException();
        }
        if (Float.compare(value, -0.0f) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal decimal = new BigDecimal(Float.toString(value))
                .stripTrailingZeros();
        return decimal.scale() < 0 ? decimal.setScale(0) : decimal;
    }

    private static DocumentFailure failure(
            DocumentFailureCode code,
            String diagnostic) {
        return new DocumentFailure(code, CAPABILITY_ID, diagnostic);
    }
}
