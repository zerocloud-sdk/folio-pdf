package net.zerocloud.pdf;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.command.CopyPages;
import net.zerocloud.pdf.command.EmbedFile;
import net.zerocloud.pdf.command.FlattenAnnotations;
import net.zerocloud.pdf.command.InsertBlankPage;
import net.zerocloud.pdf.command.MergeDocuments;
import net.zerocloud.pdf.command.MovePages;
import net.zerocloud.pdf.command.RemovePages;
import net.zerocloud.pdf.command.ReplaceOutlineTree;
import net.zerocloud.pdf.command.SetNamedDestinations;
import net.zerocloud.pdf.command.SetXmpMetadata;
import net.zerocloud.pdf.command.SplitDocument;
import net.zerocloud.pdf.command.UpdateActions;
import net.zerocloud.pdf.command.UpdateAnnotations;
import net.zerocloud.pdf.command.UpdateDocumentInfo;
import net.zerocloud.pdf.composition.command.DrawCanvas;
import net.zerocloud.pdf.composition.command.DrawPositionedUnicodeText;
import net.zerocloud.pdf.composition.command.ComposeParagraphs;
import net.zerocloud.pdf.composition.command.RelayoutParagraphs;
import net.zerocloud.pdf.composition.command.FlushParagraphs;

/** Explicit versioned whitelist for every library-owned Document Command. */
final class WorkerCommandCodec {

    private static final int BATCH_VERSION = 1;

    private static final int ADD_BLANK_PAGE = 1;
    private static final int INSERT_BLANK_PAGE = 2;
    private static final int COPY_PAGES = 3;
    private static final int MOVE_PAGES = 4;
    private static final int REMOVE_PAGES = 5;
    private static final int MERGE_DOCUMENTS = 6;
    private static final int SPLIT_DOCUMENT = 7;
    private static final int UPDATE_DOCUMENT_INFO = 8;
    private static final int SET_XMP_METADATA = 9;
    private static final int SET_NAMED_DESTINATIONS = 10;
    private static final int REPLACE_OUTLINE_TREE = 11;
    private static final int EMBED_FILE = 12;
    private static final int UPDATE_ACTIONS = 13;
    private static final int UPDATE_ANNOTATIONS = 14;
    private static final int FLATTEN_ANNOTATIONS = 15;
    private static final int DOCUMENT_PATCH = 16;
    private static final int DRAW_CANVAS = 17;
    private static final int DRAW_POSITIONED_UNICODE_TEXT = 18;
    private static final int COMPOSE_PARAGRAPHS = 19;
    private static final int RELAYOUT_PARAGRAPHS = 20;
    private static final int FLUSH_PARAGRAPHS = 21;

    static final int PREFLIGHT_UNKNOWN = 0;
    static final int PREFLIGHT_ASSEMBLY = 1;
    static final int PREFLIGHT_SPLIT = 2;
    static final int PREFLIGHT_METADATA = 3;
    static final int PREFLIGHT_OUTLINE = 4;
    static final int PREFLIGHT_ACTIONS = 5;
    static final int PREFLIGHT_ANNOTATIONS = 6;
    static final int PREFLIGHT_FLATTEN = 7;
    static final int PREFLIGHT_CANVAS_V1 = 8;
    static final int PREFLIGHT_CANVAS_V2 = 9;
    static final int PREFLIGHT_POSITIONED_TEXT = 10;
    static final int PREFLIGHT_PATCH = 11;
    static final int PREFLIGHT_PARAGRAPHS = 12;
    static final int PREFLIGHT_PAGINATION = 13;
    static final int PREFLIGHT_DETAILS_NONE = 0;
    static final int PREFLIGHT_DETAILS_ANNOTATIONS = 1;
    static final int PREFLIGHT_DETAILS_POSITIONED_TEXT = 2;
    static final int PREFLIGHT_DETAILS_CANVAS = 3;
    static final int PREFLIGHT_DETAILS_ANNOTATION_IDENTIFIERS = 4;
    static final int PREFLIGHT_DETAILS_CANVAS_PROGRAM = 5;

    private WorkerCommandCodec() {
    }

    static int atomicEncodedLength(
            List<? extends DocumentCommand> commands,
            int maximumBytes,
            long remainingOwnedMemoryBytes) {
        long encodedBytes = 8L;
        for (DocumentCommand command : commands) {
            if (command == AddBlankPage.INSTANCE) {
                encodedBytes += 8L;
            } else if (command instanceof InsertBlankPage) {
                encodedBytes += 12L;
            } else if (command instanceof CopyPages
                    || command instanceof MovePages) {
                encodedBytes += 20L;
            } else if (command instanceof RemovePages) {
                encodedBytes += 16L;
            } else {
                return -1;
            }
            if (encodedBytes > maximumBytes
                    || encodedBytes > Integer.MAX_VALUE) {
                return -1;
            }
        }
        if (encodedBytes > maximumBytes
                || encodedBytes > Integer.MAX_VALUE
                || atomicTransportPeakBytes(encodedBytes)
                > remainingOwnedMemoryBytes) {
            return -1;
        }
        return (int) encodedBytes;
    }

    private static long atomicTransportPeakBytes(long encodedBytes) {
        // Atomic batches contain only four-byte scalar writes. The accounted
        // encoder doubles its backing array, then retains an exact-sized copy.
        long capacity = 4L;
        long maximumArraySize = Integer.MAX_VALUE - 8L;
        while (capacity < encodedBytes) {
            capacity = capacity > maximumArraySize / 2L
                    ? maximumArraySize : capacity * 2L;
            if (capacity < encodedBytes
                    && capacity == maximumArraySize) {
                return Long.MAX_VALUE;
            }
        }
        long encodingPeak = capacity + encodedBytes;
        // During completion, both processes hold the batch while the child
        // retains and the parent receives the four-byte acknowledgement.
        long exchangePeak = 2L * encodedBytes + 8L;
        return Math.max(encodingPeak, exchangePeak);
    }

    static byte[] encodePreflight(
            final DocumentCommand command,
            WorkflowResourceContext resources,
            int maximumBytes) throws DocumentFailure {
        return WorkerCodecIO.encode(resources, maximumBytes, output -> {
            output.writeInt(BATCH_VERSION);
            int category = preflightCategory(command);
            output.writeInt(category);
            if (command instanceof DrawCanvas) {
                output.writeInt(((DrawCanvas) command).getPageNumber());
            } else if (command instanceof DrawPositionedUnicodeText) {
                output.writeInt(
                        ((DrawPositionedUnicodeText) command).getPageNumber());
            } else {
                output.writeInt(0);
            }
        });
    }

    static int applyPreflight(
            byte[] payload,
            PdfBoxDocumentSession session,
            WorkflowResourceContext resources) throws DocumentFailure {
        WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                payload,
                resources);
        try {
            requireVersion(input.readInt(), BATCH_VERSION);
            int category = input.readInt();
            int pageNumber = input.readInt();
            boolean pageCategory = category == PREFLIGHT_CANVAS_V1
                    || category == PREFLIGHT_CANVAS_V2
                    || category == PREFLIGHT_POSITIONED_TEXT;
            if (category < PREFLIGHT_UNKNOWN
                    || category > PREFLIGHT_PAGINATION
                    || (!pageCategory && pageNumber != 0)) {
                throw rejected("The Worker Command preflight is invalid.");
            }
            input.requireFullyConsumed();
            return session.preflightWorkerCommand(
                    category,
                    pageNumber);
        } finally {
            input.releaseDecodedMemory();
        }
    }

    static byte[] encodePreflightDetails(
            DocumentCommand command,
            int detailKind,
            WorkerFontSourceCache fontSources,
            WorkflowResourceContext resources,
            int maximumBytes) throws DocumentFailure {
        if (command instanceof DrawCanvas) {
            DrawCanvas canvas = (DrawCanvas) command;
            if (detailKind == PREFLIGHT_DETAILS_CANVAS_PROGRAM) {
                PdfBoxCanvasOperations.preflightProgramShape(
                        canvas,
                        resources);
                return WorkerCodecIO.encode(
                        resources,
                        maximumBytes,
                        output -> {
                            output.writeInt(BATCH_VERSION);
                            output.writeInt(
                                    PREFLIGHT_DETAILS_CANVAS_PROGRAM);
                            output.writeInt(1);
                        });
            }
            if (detailKind != PREFLIGHT_DETAILS_CANVAS) {
                throw rejected(
                        "The Worker Command preflight detail is inapplicable.");
            }
            return WorkerCodecIO.encode(resources, maximumBytes, output -> {
                output.writeInt(BATCH_VERSION);
                output.writeInt(PREFLIGHT_DETAILS_CANVAS);
                output.writeInt(canvas.getVersion());
                output.writeInt(canvas.getProgram().getVersion());
                output.writeBoolean(canvas.getResourceLimits().isPresent());
                output.writeInt(canvas.getResourceLimits().isPresent()
                        ? canvas.getResourceLimits().get().getVersion()
                        : 0);
            });
        }
        if (command instanceof DrawPositionedUnicodeText) {
            if (detailKind != PREFLIGHT_DETAILS_POSITIONED_TEXT) {
                throw rejected(
                        "The Worker Command preflight detail is inapplicable.");
            }
            PdfBoxPositionedTextOperations.WorkerPreflight positioned =
                    PdfBoxPositionedTextOperations.describeWorkerPreflight(
                            (DrawPositionedUnicodeText) command,
                            fontSources.getReferenceFonts().size(),
                            resources);
            return WorkerCodecIO.encode(resources, maximumBytes, output -> {
                output.writeInt(BATCH_VERSION);
                output.writeInt(PREFLIGHT_DETAILS_POSITIONED_TEXT);
                output.writeInt(positioned.commandVersion);
                output.writeInt(positioned.limitsVersion);
                output.writeInt(positioned.declarationVersion);
                output.writeInt(positioned.scanOutcome);
                output.writeBoolean(
                        positioned.hasSupplementaryCodePoint);
                output.writeInt(positioned.selectionKind);
                output.writeInt(positioned.resolvedFontSourceCount);
                output.writeInt(positioned.maximumFontSources);
            });
        }
        if (!(command instanceof UpdateAnnotations)) {
            throw rejected("The Worker Command preflight detail is inapplicable.");
        }
        UpdateAnnotations annotations = (UpdateAnnotations) command;
        if (detailKind == PREFLIGHT_DETAILS_ANNOTATIONS) {
            boolean containsWidget = false;
            for (Annotation annotation : annotations.getAnnotations()) {
                resources.checkpoint();
                if (annotation.getType() == Annotation.Type.WIDGET) {
                    containsWidget = true;
                    break;
                }
            }
            final boolean widget = containsWidget;
            return WorkerCodecIO.encode(resources, maximumBytes, output -> {
                output.writeInt(BATCH_VERSION);
                output.writeInt(PREFLIGHT_DETAILS_ANNOTATIONS);
                output.writeBoolean(widget);
            });
        }
        if (detailKind != PREFLIGHT_DETAILS_ANNOTATION_IDENTIFIERS) {
            throw rejected(
                    "The Worker Command preflight detail is inapplicable.");
        }
        return WorkerCodecIO.encode(resources, maximumBytes, output -> {
            output.writeInt(BATCH_VERSION);
            output.writeInt(PREFLIGHT_DETAILS_ANNOTATION_IDENTIFIERS);
            output.writeInt(annotations.getAnnotations().size());
            for (Annotation annotation : annotations.getAnnotations()) {
                output.writeString(
                        annotation.getProperties().getIdentifier());
            }
            writeStrings(output, annotations.getRemovedIdentifiers());
        });
    }

    static int applyPreflightDetails(
            byte[] payload,
            PdfBoxDocumentSession session,
            WorkflowResourceContext resources) throws DocumentFailure {
        WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                payload,
                resources);
        try {
            requireVersion(input.readInt(), BATCH_VERSION);
            int detailKind = input.readInt();
            if (detailKind == PREFLIGHT_DETAILS_CANVAS) {
                int commandVersion = input.readInt();
                int programVersion = input.readInt();
                boolean limitsPresent = input.readBoolean();
                int limitsVersion = input.readInt();
                if (!limitsPresent && limitsVersion != 0) {
                    throw rejected(
                            "The Worker Canvas preflight is invalid.");
                }
                input.requireFullyConsumed();
                return session.completeWorkerCanvasPreflight(
                        commandVersion,
                        programVersion,
                        limitsPresent,
                        limitsVersion);
            }
            if (detailKind == PREFLIGHT_DETAILS_POSITIONED_TEXT) {
                PdfBoxPositionedTextOperations.WorkerPreflight positioned =
                        new PdfBoxPositionedTextOperations.WorkerPreflight(
                                input.readInt(),
                                input.readInt(),
                                input.readInt(),
                                input.readInt(),
                                input.readBoolean(),
                                input.readInt(),
                                input.readInt(),
                                input.readInt());
                if (!positioned.isValidWireValue()) {
                    throw rejected(
                            "The Worker positioned-text preflight is invalid.");
                }
                input.requireFullyConsumed();
                session.completeWorkerPositionedTextPreflight(positioned);
                return PREFLIGHT_DETAILS_NONE;
            }
            if (detailKind == PREFLIGHT_DETAILS_ANNOTATIONS) {
                boolean containsWidget = input.readBoolean();
                input.requireFullyConsumed();
                return session.completeWorkerAnnotationWidgetPreflight(
                        containsWidget);
            }
            if (detailKind == PREFLIGHT_DETAILS_CANVAS_PROGRAM) {
                if (input.readInt() != 1) {
                    throw rejected(
                            "The Worker Canvas Program preflight is invalid.");
                }
                input.requireFullyConsumed();
                session.completeWorkerCanvasProgramPreflight();
                return PREFLIGHT_DETAILS_NONE;
            }
            if (detailKind
                    != PREFLIGHT_DETAILS_ANNOTATION_IDENTIFIERS) {
                throw rejected(
                        "The Worker Command preflight detail is unsupported.");
            }
            int annotationCount = readCount(
                    input,
                    "annotation preflight");
            java.util.Set<String> identifiers =
                    new java.util.HashSet<String>(annotationCount);
            for (int index = 0; index < annotationCount; index++) {
                identifiers.add(input.readString());
            }
            identifiers.addAll(readStrings(input));
            input.requireFullyConsumed();
            session.completeWorkerAnnotationPreflight(identifiers);
            return PREFLIGHT_DETAILS_NONE;
        } finally {
            input.releaseDecodedMemory();
        }
    }

    private static int preflightCategory(DocumentCommand command) {
        if (command instanceof RelayoutParagraphs || command instanceof FlushParagraphs
                || (command instanceof ComposeParagraphs && ((ComposeParagraphs) command).getVersion() == 2)) {
            return PREFLIGHT_PAGINATION;
        }
        if (command instanceof ComposeParagraphs) {
            return PREFLIGHT_PARAGRAPHS;
        }
        if (command == AddBlankPage.INSTANCE
                || command instanceof InsertBlankPage
                || command instanceof CopyPages
                || command instanceof MovePages
                || command instanceof RemovePages
                || command instanceof MergeDocuments) {
            return PREFLIGHT_ASSEMBLY;
        }
        if (command instanceof SplitDocument) {
            return PREFLIGHT_SPLIT;
        }
        if (command instanceof ReplaceOutlineTree) {
            return PREFLIGHT_OUTLINE;
        }
        if (command instanceof UpdateDocumentInfo
                || command instanceof SetXmpMetadata
                || command instanceof SetNamedDestinations
                || command instanceof EmbedFile) {
            return PREFLIGHT_METADATA;
        }
        if (command instanceof UpdateActions) {
            return PREFLIGHT_ACTIONS;
        }
        if (command instanceof UpdateAnnotations) {
            return PREFLIGHT_ANNOTATIONS;
        }
        if (command instanceof FlattenAnnotations) {
            return PREFLIGHT_FLATTEN;
        }
        if (command instanceof DrawCanvas) {
            return ((DrawCanvas) command).getVersion() == DrawCanvas.VERSION_2
                    ? PREFLIGHT_CANVAS_V2 : PREFLIGHT_CANVAS_V1;
        }
        if (command instanceof DrawPositionedUnicodeText) {
            return PREFLIGHT_POSITIONED_TEXT;
        }
        if (command instanceof DocumentPatch) {
            return PREFLIGHT_PATCH;
        }
        return PREFLIGHT_UNKNOWN;
    }

    static byte[] encodeBatch(
            final List<? extends DocumentCommand> commands,
            final WorkerReferenceRegistry references,
            final WorkerFontSourceCache fontSources,
            WorkflowResourceContext resources,
            int maximumBytes) throws DocumentFailure {
        if (commands == null) {
            throw new NullPointerException("commands");
        }
        return WorkerCodecIO.encode(resources, maximumBytes, output -> {
            output.writeInt(BATCH_VERSION);
            output.writeInt(commands.size());
            for (int index = 0; index < commands.size(); index++) {
                try {
                    writeCommand(
                            output,
                            commands.get(index),
                            references,
                            fontSources);
                } catch (DocumentFailure failure) {
                    throw new CommandEncodingFailure(index, failure);
                } catch (WorkerCodecIO.MessageLimitException failure) {
                    throw new CommandEncodingFailure(
                            index,
                            WorkerCodecIO.messageLimitFailure());
                }
            }
        });
    }

    static final class CommandEncodingFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;
        private final int commandIndex;
        private final DocumentFailure documentFailure;

        private CommandEncodingFailure(
                int commandIndex,
                DocumentFailure documentFailure) {
            super(documentFailure.getDiagnostic());
            this.commandIndex = commandIndex;
            this.documentFailure = documentFailure;
        }

        int getCommandIndex() {
            return commandIndex;
        }

        DocumentFailure getDocumentFailure() {
            return documentFailure;
        }
    }

    static List<DocumentCommand> decodeBatch(
            byte[] payload,
            WorkerReferenceRegistry references) throws DocumentFailure {
        return decodeBatch(payload, references, null);
    }

    static List<DocumentCommand> decodeBatch(
            byte[] payload,
            WorkerReferenceRegistry references,
            WorkflowResourceContext resources) throws DocumentFailure {
        WorkerCodecIO.Input input = resources == null
                ? WorkerCodecIO.input(payload)
                : WorkerCodecIO.accountedInput(payload, resources);
        return decodeBatch(input, references, null);
    }

    static List<DocumentCommand> decodeBatch(
            WorkerCodecIO.Input input,
            WorkerReferenceRegistry references) throws DocumentFailure {
        return decodeBatch(input, references, null);
    }

    static List<DocumentCommand> decodeBatch(
            WorkerCodecIO.Input input,
            WorkerReferenceRegistry references,
            WorkerCompositionCodec.RemoteFontSource remoteFonts)
            throws DocumentFailure {
        requireVersion(input.readInt(), BATCH_VERSION);
        int count = readCount(input, "Command");
        List<DocumentCommand> commands =
                new ArrayList<DocumentCommand>(count);
        try {
            for (int index = 0; index < count; index++) {
                commands.add(readCommand(input, references, remoteFonts));
            }
            input.requireFullyConsumed();
            return commands;
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw rejected("A Worker Command value is invalid.");
        }
    }

    static void executeAtomicBatch(
            WorkerCodecIO.Input input,
            DocumentSession session) throws DocumentFailure {
        requireVersion(input.readInt(), BATCH_VERSION);
        int count = input.readInt();
        if (count < 0 || count > input.available() / 8) {
            throw rejected("The Worker Command count is invalid.");
        }
        try {
            for (int index = 0; index < count; index++) {
                session.execute(readAtomicCommand(input));
            }
            input.requireFullyConsumed();
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw rejected("A Worker Command value is invalid.");
        }
    }

    private static DocumentCommand readAtomicCommand(
            WorkerCodecIO.Input input) throws DocumentFailure {
        int opcode = input.readInt();
        DocumentCommand command = readPageStructureCommand(opcode, input);
        if (command == null) {
            throw rejected(
                    "The atomic Worker Command opcode is unsupported.");
        }
        return command;
    }

    private static DocumentCommand readPageStructureCommand(
            int opcode,
            WorkerCodecIO.Input input) throws DocumentFailure {
        switch (opcode) {
            case ADD_BLANK_PAGE:
                requireVersion(input.readInt(), 1);
                return AddBlankPage.INSTANCE;
            case INSERT_BLANK_PAGE:
                requireVersion(input.readInt(), InsertBlankPage.VERSION_1);
                return InsertBlankPage.version1(input.readInt());
            case COPY_PAGES:
                requireVersion(input.readInt(), CopyPages.VERSION_1);
                PageRange copyRange = readRange(input);
                return CopyPages.version1(copyRange, input.readInt());
            case MOVE_PAGES:
                requireVersion(input.readInt(), MovePages.VERSION_1);
                PageRange moveRange = readRange(input);
                return MovePages.version1(moveRange, input.readInt());
            case REMOVE_PAGES:
                requireVersion(input.readInt(), RemovePages.VERSION_1);
                return RemovePages.version1(readRange(input));
            default:
                return null;
        }
    }

    private static void writeCommand(
            WorkerCodecIO.Output output,
            DocumentCommand command,
            WorkerReferenceRegistry references,
            WorkerFontSourceCache fontSources)
            throws IOException, DocumentFailure {
        if (command == AddBlankPage.INSTANCE) {
            output.writeInt(ADD_BLANK_PAGE);
            output.writeInt(1);
            return;
        }
        if (command instanceof InsertBlankPage) {
            InsertBlankPage value = (InsertBlankPage) command;
            output.writeInt(INSERT_BLANK_PAGE);
            output.writeInt(value.getVersion());
            output.writeInt(value.getPageNumber());
            return;
        }
        if (command instanceof CopyPages) {
            CopyPages value = (CopyPages) command;
            output.writeInt(COPY_PAGES);
            output.writeInt(value.getVersion());
            writeRange(output, value.getRange());
            output.writeInt(value.getInsertionPageNumber());
            return;
        }
        if (command instanceof MovePages) {
            MovePages value = (MovePages) command;
            output.writeInt(MOVE_PAGES);
            output.writeInt(value.getVersion());
            writeRange(output, value.getRange());
            output.writeInt(value.getDestinationPageNumber());
            return;
        }
        if (command instanceof RemovePages) {
            RemovePages value = (RemovePages) command;
            output.writeInt(REMOVE_PAGES);
            output.writeInt(value.getVersion());
            writeRange(output, value.getRange());
            return;
        }
        if (command instanceof MergeDocuments) {
            MergeDocuments value = (MergeDocuments) command;
            output.writeInt(MERGE_DOCUMENTS);
            output.writeInt(value.getVersion());
            writeStrings(output, value.getSourceNames());
            return;
        }
        if (command instanceof SplitDocument) {
            SplitDocument value = (SplitDocument) command;
            output.writeInt(SPLIT_DOCUMENT);
            output.writeInt(value.getVersion());
            output.writeInt(value.getTargetDeclarationCount());
            output.writeInt(value.getTargetRanges().size());
            for (Map.Entry<String, PageRange> entry
                    : value.getTargetRanges().entrySet()) {
                output.writeString(entry.getKey());
                writeRange(output, entry.getValue());
            }
            return;
        }
        if (command instanceof UpdateDocumentInfo) {
            UpdateDocumentInfo value = (UpdateDocumentInfo) command;
            output.writeInt(UPDATE_DOCUMENT_INFO);
            output.writeInt(value.getVersion());
            output.writeInt(value.getEntries().size());
            for (Map.Entry<String, PdfValue> entry
                    : value.getEntries().entrySet()) {
                output.writeString(entry.getKey());
                writePdfValue(output, entry.getValue(), references, 0);
            }
            writeStrings(output, value.getRemovedNames());
            return;
        }
        if (command instanceof SetXmpMetadata) {
            SetXmpMetadata value = (SetXmpMetadata) command;
            output.writeInt(SET_XMP_METADATA);
            output.writeInt(value.getVersion());
            output.writeDefensiveBytes(
                    value.getXmpPacketLength(),
                    value::getXmpPacket);
            return;
        }
        if (command instanceof SetNamedDestinations) {
            SetNamedDestinations value = (SetNamedDestinations) command;
            output.writeInt(SET_NAMED_DESTINATIONS);
            output.writeInt(value.getVersion());
            output.writeInt(value.getEntries().size());
            for (Map.Entry<String, PageDestination> entry
                    : value.getEntries().entrySet()) {
                output.writeString(entry.getKey());
                writePageDestination(output, entry.getValue());
            }
            writeStrings(output, value.getRemovedNames());
            return;
        }
        if (command instanceof ReplaceOutlineTree) {
            ReplaceOutlineTree value = (ReplaceOutlineTree) command;
            output.writeInt(REPLACE_OUTLINE_TREE);
            output.writeInt(value.getVersion());
            writeOutlineItems(output, value.getItems(), 0);
            return;
        }
        if (command instanceof EmbedFile) {
            EmbedFile value = (EmbedFile) command;
            output.writeInt(EMBED_FILE);
            output.writeInt(value.getVersion());
            writeEmbeddedFile(output, value.getFile());
            return;
        }
        if (command instanceof UpdateActions) {
            UpdateActions value = (UpdateActions) command;
            output.writeInt(UPDATE_ACTIONS);
            output.writeInt(value.getVersion());
            output.writeBoolean(value.isDocumentOpenActionUpdated());
            if (value.isDocumentOpenActionUpdated()) {
                output.writeBoolean(value.getDocumentOpenAction() != null);
                if (value.getDocumentOpenAction() != null) {
                    writeGoToAction(output, value.getDocumentOpenAction());
                }
            }
            writePageActions(output, value.getPageOpenActions());
            writePageActions(output, value.getPageCloseActions());
            writeIntegers(output, value.getRemovedPageOpenActions());
            writeIntegers(output, value.getRemovedPageCloseActions());
            return;
        }
        if (command instanceof UpdateAnnotations) {
            output.writeInt(UPDATE_ANNOTATIONS);
            writeUpdateAnnotations(output, (UpdateAnnotations) command);
            return;
        }
        if (command instanceof FlattenAnnotations) {
            FlattenAnnotations value = (FlattenAnnotations) command;
            output.writeInt(FLATTEN_ANNOTATIONS);
            output.writeInt(value.getVersion());
            writeStrings(output, value.getIdentifiers());
            return;
        }
        if (command instanceof DocumentPatch) {
            DocumentPatch value = (DocumentPatch) command;
            output.writeInt(DOCUMENT_PATCH);
            output.writeInt(value.getVersion());
            output.writeInt(value.getChanges().size());
            for (DocumentPatch.DictionaryEntryChange change
                    : value.getChanges()) {
                references.write(output, change.getTarget());
                output.writeString(change.getName().getValue());
                writePdfValue(output, change.getValue(), references, 0);
            }
            return;
        }
        if (command instanceof DrawCanvas) {
            output.writeInt(DRAW_CANVAS);
            WorkerCompositionCodec.writeDrawCanvas(
                    output,
                    (DrawCanvas) command,
                    references);
            return;
        }
        if (command instanceof DrawPositionedUnicodeText) {
            output.writeInt(DRAW_POSITIONED_UNICODE_TEXT);
            WorkerCompositionCodec.writePositionedTextCommand(
                    output,
                    (DrawPositionedUnicodeText) command,
                    references,
                    fontSources);
            return;
        }
        if (command instanceof RelayoutParagraphs) {
            output.writeInt(RELAYOUT_PARAGRAPHS);
            output.writeInt(((RelayoutParagraphs) command).getVersion());
            WorkerCompositionCodec.writeLayoutPages(output, ((RelayoutParagraphs) command).getPages());
            return;
        }
        if (command instanceof FlushParagraphs) {
            output.writeInt(FLUSH_PARAGRAPHS);
            output.writeInt(((FlushParagraphs) command).getVersion());
            return;
        }
        if (command instanceof ComposeParagraphs) {
            PdfBoxParagraphOperations.validateDeclarations((ComposeParagraphs) command, output.resources());
            output.writeInt(COMPOSE_PARAGRAPHS);
            WorkerCompositionCodec.writeParagraphs(output, (ComposeParagraphs) command, references, fontSources);
            return;
        }
        throw new DocumentFailure(
                DocumentFailureCode.COMMAND_REJECTED,
                PdfBoxWorkflowEngine.CAPABILITY_ID,
                "The command is not supported by this workflow version.");
    }

    private static DocumentCommand readCommand(
            WorkerCodecIO.Input input,
            WorkerReferenceRegistry references,
            WorkerCompositionCodec.RemoteFontSource remoteFonts)
            throws DocumentFailure {
        int opcode = input.readInt();
        DocumentCommand pageStructure = readPageStructureCommand(
                opcode,
                input);
        if (pageStructure != null) {
            return pageStructure;
        }
        switch (opcode) {
            case MERGE_DOCUMENTS:
                requireVersion(input.readInt(), MergeDocuments.VERSION_1);
                List<String> sourceNames = readStrings(input);
                return MergeDocuments.version1(
                        sourceNames.toArray(new String[sourceNames.size()]));
            case SPLIT_DOCUMENT:
                return readSplitDocument(input);
            case UPDATE_DOCUMENT_INFO:
                return readUpdateDocumentInfo(input, references);
            case SET_XMP_METADATA:
                requireVersion(input.readInt(), SetXmpMetadata.VERSION_1);
                return SetXmpMetadata.version1(input.readBytes(2));
            case SET_NAMED_DESTINATIONS:
                return readNamedDestinations(input);
            case REPLACE_OUTLINE_TREE:
                requireVersion(input.readInt(), ReplaceOutlineTree.VERSION_1);
                return ReplaceOutlineTree.version1(
                        readOutlineItems(input, 0));
            case EMBED_FILE:
                requireVersion(input.readInt(), EmbedFile.VERSION_1);
                return EmbedFile.version1(readEmbeddedFile(input));
            case UPDATE_ACTIONS:
                return readUpdateActions(input);
            case UPDATE_ANNOTATIONS:
                return readUpdateAnnotations(input);
            case FLATTEN_ANNOTATIONS:
                requireVersion(input.readInt(), FlattenAnnotations.VERSION_1);
                List<String> identifiers = readStrings(input);
                if (identifiers.isEmpty()) {
                    throw rejected("A Worker annotation identifier list is empty.");
                }
                return FlattenAnnotations.version1(
                        identifiers.get(0),
                        identifiers.subList(1, identifiers.size())
                                .toArray(new String[identifiers.size() - 1]));
            case DOCUMENT_PATCH:
                return readDocumentPatch(input, references);
            case DRAW_CANVAS:
                return WorkerCompositionCodec.readDrawCanvas(input, references);
            case COMPOSE_PARAGRAPHS:
                return WorkerCompositionCodec.readParagraphs(input, references, remoteFonts);
            case RELAYOUT_PARAGRAPHS:
                requireVersion(input.readInt(), RelayoutParagraphs.VERSION_1);
                return RelayoutParagraphs.version1(WorkerCompositionCodec.readLayoutPages(input));
            case FLUSH_PARAGRAPHS:
                requireVersion(input.readInt(), FlushParagraphs.VERSION_1);
                return FlushParagraphs.version1();
            case DRAW_POSITIONED_UNICODE_TEXT:
                return WorkerCompositionCodec.readPositionedTextCommand(
                        input,
                        references,
                        remoteFonts);
            default:
                throw rejected("The Worker Command opcode is unsupported.");
        }
    }

    private static SplitDocument readSplitDocument(WorkerCodecIO.Input input)
            throws DocumentFailure {
        requireVersion(input.readInt(), SplitDocument.VERSION_1);
        int declarations = input.readInt();
        int entries = readCount(input, "split Target");
        if (declarations < entries || declarations < 0) {
            throw rejected("The Worker split declaration count is invalid.");
        }
        SplitDocument.Builder builder = SplitDocument.version1();
        String lastName = null;
        PageRange lastRange = null;
        for (int index = 0; index < entries; index++) {
            lastName = input.readString();
            lastRange = readRange(input);
            builder.target(lastName, lastRange);
        }
        if (declarations > entries && lastName == null) {
            throw rejected("The Worker split declaration count is invalid.");
        }
        if (declarations > entries) {
            builder.target(lastName, lastRange);
        }
        return builder.build();
    }

    private static UpdateDocumentInfo readUpdateDocumentInfo(
            WorkerCodecIO.Input input,
            WorkerReferenceRegistry references) throws DocumentFailure {
        requireVersion(input.readInt(), UpdateDocumentInfo.VERSION_1);
        UpdateDocumentInfo.Builder builder = UpdateDocumentInfo.version1();
        int count = readCount(input, "document information entry");
        for (int index = 0; index < count; index++) {
            builder.set(input.readString(), readPdfValue(input, references, 0));
        }
        for (String name : readStrings(input)) {
            builder.remove(name);
        }
        return builder.build();
    }

    private static SetNamedDestinations readNamedDestinations(
            WorkerCodecIO.Input input) throws DocumentFailure {
        requireVersion(input.readInt(), SetNamedDestinations.VERSION_1);
        SetNamedDestinations.Builder builder = SetNamedDestinations.version1();
        int count = readCount(input, "named destination");
        for (int index = 0; index < count; index++) {
            builder.set(input.readString(), readPageDestination(input));
        }
        for (String name : readStrings(input)) {
            builder.remove(name);
        }
        return builder.build();
    }

    private static UpdateActions readUpdateActions(WorkerCodecIO.Input input)
            throws DocumentFailure {
        requireVersion(input.readInt(), UpdateActions.VERSION_1);
        UpdateActions.Builder builder = UpdateActions.version1();
        if (input.readBoolean()) {
            if (input.readBoolean()) {
                builder.setDocumentOpenAction(readGoToAction(input));
            } else {
                builder.removeDocumentOpenAction();
            }
        }
        readPageActions(input, builder, true);
        readPageActions(input, builder, false);
        for (Integer page : readIntegers(input)) {
            builder.removePageOpenAction(page.intValue());
        }
        for (Integer page : readIntegers(input)) {
            builder.removePageCloseAction(page.intValue());
        }
        return builder.build();
    }

    private static DocumentPatch readDocumentPatch(
            WorkerCodecIO.Input input,
            WorkerReferenceRegistry references) throws DocumentFailure {
        requireVersion(input.readInt(), DocumentPatch.VERSION_1);
        int count = readCount(input, "Document Patch change");
        DocumentPatch.Builder builder = DocumentPatch.builder();
        for (int index = 0; index < count; index++) {
            builder.setDictionaryEntry(
                    references.read(input),
                    PdfName.of(input.readString()),
                    readPdfValue(input, references, 0));
        }
        if (count == 0) {
            throw rejected("A Worker Document Patch is empty.");
        }
        return builder.build();
    }

    static void writePdfValue(
            WorkerCodecIO.Output output,
            PdfValue value,
            WorkerReferenceRegistry references,
            int depth) throws IOException, DocumentFailure {
        Deque<ValueWriteFrame> frames = new ArrayDeque<ValueWriteFrame>();
        frames.push(new ValueWriteFrame(value));
        while (!frames.isEmpty()) {
            ValueWriteFrame frame = frames.peek();
            PdfValue current = frame.value;
            if (!frame.started) {
                frame.started = true;
                if (current == PdfNull.INSTANCE) {
                    output.writeByte(0);
                    frames.pop();
                } else if (current instanceof PdfBoolean) {
                    output.writeByte(1);
                    output.writeBoolean(((PdfBoolean) current).booleanValue());
                    frames.pop();
                } else if (current instanceof PdfNumber) {
                    output.writeByte(2);
                    output.writeBigDecimal(((PdfNumber) current).decimalValue());
                    frames.pop();
                } else if (current instanceof PdfString) {
                    output.writeByte(3);
                    output.writeBytes(
                            ((PdfString) current).bytesForWorkflow());
                    frames.pop();
                } else if (current instanceof PdfName) {
                    output.writeByte(4);
                    output.writeString(((PdfName) current).getValue());
                    frames.pop();
                } else if (current instanceof PdfArray) {
                    output.requireNestingDepth(
                            (long) depth + frames.size());
                    frame.kind = 5;
                    frame.size = ((PdfArray) current).size();
                    output.writeByte(5);
                    output.writeInt(frame.size);
                } else if (current instanceof PdfDictionary) {
                    output.requireNestingDepth(
                            (long) depth + frames.size());
                    frame.kind = 6;
                    frame.size = ((PdfDictionary) current).size();
                    output.writeByte(6);
                    output.writeInt(frame.size);
                } else if (current instanceof PdfStream) {
                    output.requireNestingDepth(
                            (long) depth + frames.size());
                    frame.kind = 7;
                    output.writeByte(7);
                    frames.push(new ValueWriteFrame(
                            ((PdfStream) current).getDictionary()));
                } else if (current instanceof PdfIndirectReference) {
                    output.writeByte(8);
                    references.write(
                            output,
                            ((PdfIndirectReference) current).getReference());
                    frames.pop();
                } else {
                    throw new DocumentFailure(
                            DocumentFailureCode.PATCH_VALUE_REJECTED,
                            PdfBoxValueAdapter.CAPABILITY_ID,
                            "The PDF Value is not owned by Folio PDF.");
                }
                continue;
            }
            if (frame.kind == 5) {
                if (frame.index == frame.size) {
                    frames.pop();
                } else {
                    PdfArray array = (PdfArray) current;
                    frames.push(new ValueWriteFrame(
                            array.get(frame.index++)));
                }
            } else if (frame.kind == 6) {
                if (frame.index == frame.size) {
                    frames.pop();
                } else {
                    PdfDictionaryEntry entry = ((PdfDictionary) current)
                            .getEntry(frame.index++);
                    output.writeString(entry.getName().getValue());
                    frames.push(new ValueWriteFrame(entry.getValue()));
                }
            } else {
                PdfStream stream = (PdfStream) current;
                writeStreamBytes(output, stream);
                frames.pop();
            }
        }
    }

    static void writeStreamBytes(
            WorkerCodecIO.Output output,
            PdfStream stream) throws IOException, DocumentFailure {
        WorkflowResourceContext resources = output.resources();
        if (resources == null) {
            byte[] bytes = stream.readBytes();
            try {
                output.writeBytes(bytes);
            } finally {
                java.util.Arrays.fill(bytes, (byte) 0);
            }
            return;
        }
        try (WorkflowResourceContext.OwnedBytes bytes =
                stream.readBytesForWorkflow(resources)) {
            output.writeBytes(bytes.getBytes());
        }
    }

    static PdfValue readPdfValue(
            WorkerCodecIO.Input input,
            WorkerReferenceRegistry references,
            int depth) throws DocumentFailure {
        Deque<ValueReadFrame> frames = new ArrayDeque<ValueReadFrame>();
        PdfValue completed = null;
        while (true) {
            if (completed == null) {
                int opcode = input.readByte();
                switch (opcode) {
                    case 0:
                        completed = PdfNull.INSTANCE;
                        break;
                    case 1:
                        completed = PdfBoolean.of(input.readBoolean());
                        break;
                    case 2:
                        completed = PdfNumber.of(input.readBigDecimal());
                        break;
                    case 3:
                        completed = PdfString.fromOwnedBytes(
                                input.readBytes());
                        break;
                    case 4:
                        completed = PdfName.of(input.readString());
                        break;
                    case 5:
                        input.requireNestingDepth(
                                (long) depth + frames.size() + 1L);
                        int arraySize = readCount(input, "PDF array");
                        if (arraySize == 0) {
                            completed = PdfArray.of();
                        } else {
                            frames.push(ValueReadFrame.array(arraySize));
                        }
                        break;
                    case 6:
                        input.requireNestingDepth(
                                (long) depth + frames.size() + 1L);
                        int dictionarySize = readCount(
                                input,
                                "PDF dictionary");
                        if (dictionarySize == 0) {
                            completed = PdfDictionary.builder().build();
                        } else {
                            frames.push(ValueReadFrame.dictionary(
                                    dictionarySize,
                                    PdfName.of(input.readString())));
                        }
                        break;
                    case 7:
                        input.requireNestingDepth(
                                (long) depth + frames.size() + 1L);
                        frames.push(ValueReadFrame.stream());
                        break;
                    case 8:
                        completed = PdfIndirectReference.of(
                                references.read(input));
                        break;
                    default:
                        throw rejected(
                                "The Worker PDF Value opcode is unsupported.");
                }
                if (completed == null) {
                    continue;
                }
            }
            if (frames.isEmpty()) {
                return completed;
            }
            completed = frames.peek().accept(
                    completed,
                    input,
                    references);
            if (completed != null) {
                frames.pop();
            }
        }
    }

    private static final class ValueWriteFrame {

        private final PdfValue value;
        private boolean started;
        private int kind;
        private int size;
        private int index;

        private ValueWriteFrame(PdfValue value) {
            this.value = value;
        }
    }

    private static final class ValueReadFrame {

        private final int kind;
        private final int size;
        private final PdfValue[] array;
        private final PdfDictionary.Builder dictionary;
        private PdfName name;
        private int index;

        private ValueReadFrame(
                int kind,
                int size,
                PdfValue[] array,
                PdfDictionary.Builder dictionary,
                PdfName name) {
            this.kind = kind;
            this.size = size;
            this.array = array;
            this.dictionary = dictionary;
            this.name = name;
        }

        private static ValueReadFrame array(int size) {
            return new ValueReadFrame(
                    5,
                    size,
                    new PdfValue[size],
                    null,
                    null);
        }

        private static ValueReadFrame dictionary(int size, PdfName name) {
            ValueReadFrame frame = new ValueReadFrame(
                    6,
                    size,
                    null,
                    PdfDictionary.builder(),
                    name);
            return frame;
        }

        private static ValueReadFrame stream() {
            return new ValueReadFrame(7, 0, null, null, null);
        }

        private PdfValue accept(
                PdfValue child,
                WorkerCodecIO.Input input,
                WorkerReferenceRegistry references) throws DocumentFailure {
            if (kind == 5) {
                array[index++] = child;
                return index == array.length ? PdfArray.of(array) : null;
            }
            if (kind == 6) {
                dictionary.put(name, child);
                index++;
                if (index == size) {
                    return dictionary.build();
                }
                name = PdfName.of(input.readString());
                return null;
            }
            if (!(child instanceof PdfDictionary)) {
                throw rejected("A Worker PDF stream dictionary is invalid.");
            }
            byte[] bytes = input.readBytes(2);
            PdfStream stream;
            try {
                PdfDictionary dictionary = (PdfDictionary) child;
                stream = PdfStream.of(dictionary, bytes);
            } finally {
                java.util.Arrays.fill(bytes, (byte) 0);
            }
            return stream;
        }
    }

    static void writePageDestination(
            WorkerCodecIO.Output output,
            PageDestination destination) throws IOException {
        output.writeInt(destination.getPageNumber());
        output.writeString(destination.getStyle().name());
        output.writeInt(destination.getOperands().size());
        for (BigDecimal operand : destination.getOperands()) {
            output.writeBoolean(operand != null);
            if (operand != null) {
                output.writeBigDecimal(operand);
            }
        }
    }

    static PageDestination readPageDestination(WorkerCodecIO.Input input)
            throws DocumentFailure {
        int page = input.readInt();
        PageDestination.Style style = enumValue(
                PageDestination.Style.class,
                input.readString(),
                "page-destination style");
        int count = readCount(input, "page-destination operand");
        List<BigDecimal> operands = new ArrayList<BigDecimal>(count);
        for (int index = 0; index < count; index++) {
            operands.add(input.readBoolean() ? input.readBigDecimal() : null);
        }
        try {
            switch (style) {
                case XYZ:
                    requireOperandCount(operands, 3);
                    return PageDestination.xyz(
                            page,
                            operands.get(0),
                            operands.get(1),
                            operands.get(2));
                case FIT:
                    requireOperandCount(operands, 0);
                    return PageDestination.fit(page);
                case FIT_H:
                    requireOperandCount(operands, 1);
                    return PageDestination.fitH(page, operands.get(0));
                case FIT_V:
                    requireOperandCount(operands, 1);
                    return PageDestination.fitV(page, operands.get(0));
                case FIT_R:
                    requireOperandCount(operands, 4);
                    return PageDestination.fitR(
                            page,
                            operands.get(0),
                            operands.get(1),
                            operands.get(2),
                            operands.get(3));
                case FIT_B:
                    requireOperandCount(operands, 0);
                    return PageDestination.fitB(page);
                case FIT_BH:
                    requireOperandCount(operands, 1);
                    return PageDestination.fitBH(page, operands.get(0));
                case FIT_BV:
                    requireOperandCount(operands, 1);
                    return PageDestination.fitBV(page, operands.get(0));
                default:
                    throw rejected("The Worker page destination is unsupported.");
            }
        } catch (RuntimeException failure) {
            throw rejected("The Worker page destination is invalid.");
        }
    }

    static void writeOutlineItems(
            WorkerCodecIO.Output output,
            List<OutlineItem> items,
            int depth) throws IOException, DocumentFailure {
        output.writeInt(items.size());
        if (items.isEmpty()) {
            return;
        }
        Deque<OutlineWriteFrame> frames =
                new ArrayDeque<OutlineWriteFrame>();
        frames.push(new OutlineWriteFrame(items));
        while (!frames.isEmpty()) {
            OutlineWriteFrame frame = frames.peek();
            if (frame.index == frame.items.size()) {
                frames.pop();
                continue;
            }
            OutlineItem item = frame.items.get(frame.index++);
            output.writeString(item.getTitle());
            if (item.getDestination().isPresent()) {
                output.writeByte(1);
                writePageDestination(output, item.getDestination().get());
            } else if (item.getNamedDestination().isPresent()) {
                output.writeByte(2);
                output.writeString(item.getNamedDestination().get());
            } else {
                output.writeByte(0);
            }
            List<OutlineItem> children = item.getChildren();
            output.writeInt(children.size());
            if (!children.isEmpty()) {
                output.requireNestingDepth(
                        (long) depth + frames.size());
                frames.push(new OutlineWriteFrame(children));
            }
        }
    }

    static List<OutlineItem> readOutlineItems(
            WorkerCodecIO.Input input,
            int depth) throws DocumentFailure {
        int count = readCount(input, "outline item");
        Deque<OutlineReadFrame> frames =
                new ArrayDeque<OutlineReadFrame>();
        frames.push(new OutlineReadFrame(count));
        while (true) {
            OutlineReadFrame frame = frames.peek();
            if (frame.items.size() == frame.count) {
                List<OutlineItem> completed = frame.items;
                frames.pop();
                if (frames.isEmpty()) {
                    return completed;
                }
                frames.peek().completePending(completed);
                continue;
            }
            String title = input.readString();
            int kind = input.readByte();
            PageDestination destination = kind == 1
                    ? readPageDestination(input) : null;
            String named = kind == 2 ? input.readString() : null;
            if (kind < 0 || kind > 2) {
                throw rejected("The Worker outline target is unsupported.");
            }
            int childCount = readCount(input, "outline item");
            OutlinePendingItem pending = new OutlinePendingItem(
                    title,
                    destination,
                    named);
            if (childCount == 0) {
                frame.items.add(pending.build(
                        Collections.<OutlineItem>emptyList()));
            } else {
                input.requireNestingDepth(
                        (long) depth + frames.size());
                frame.pending = pending;
                frames.push(new OutlineReadFrame(childCount));
            }
        }
    }

    private static final class OutlineWriteFrame {

        private final List<OutlineItem> items;
        private int index;

        private OutlineWriteFrame(List<OutlineItem> items) {
            this.items = items;
        }
    }

    private static final class OutlineReadFrame {

        private final int count;
        private final List<OutlineItem> items;
        private OutlinePendingItem pending;

        private OutlineReadFrame(int count) {
            this.count = count;
            this.items = new ArrayList<OutlineItem>(count);
        }

        private void completePending(List<OutlineItem> children) {
            OutlinePendingItem current = pending;
            pending = null;
            items.add(current.build(children));
        }
    }

    private static final class OutlinePendingItem {

        private final String title;
        private final PageDestination destination;
        private final String namedDestination;

        private OutlinePendingItem(
                String title,
                PageDestination destination,
                String namedDestination) {
            this.title = title;
            this.destination = destination;
            this.namedDestination = namedDestination;
        }

        private OutlineItem build(List<OutlineItem> children) {
            if (destination != null) {
                return OutlineItem.toPage(title, destination, children);
            }
            if (namedDestination != null) {
                return OutlineItem.toNamedDestination(
                        title,
                        namedDestination,
                        children);
            }
            return OutlineItem.grouping(title, children);
        }
    }

    static void writeEmbeddedFile(
            WorkerCodecIO.Output output,
            EmbeddedFile file) throws IOException {
        output.writeInt(file.getVersion());
        output.writeString(file.getName());
        output.writeBytes(file.contentForWorkflow());
        output.writeNullableString(file.getMimeSubtype().orElse(null));
        output.writeNullableString(file.getDescription().orElse(null));
        EmbeddedFile.Relationship relationship =
                file.declaredRelationshipForWorkflow();
        output.writeNullableString(
                relationship == null ? null : relationship.name());
    }

    static EmbeddedFile readEmbeddedFile(WorkerCodecIO.Input input)
            throws DocumentFailure {
        requireVersion(input.readInt(), EmbeddedFile.VERSION_1);
        String name = input.readString();
        byte[] content = input.readBytes();
        String mime = input.readNullableString();
        String description = input.readNullableString();
        String relationshipName = input.readNullableString();
        if ((description == null) != (relationshipName == null)
                || (description != null && mime == null)) {
            throw rejected("The Worker embedded-file value is invalid.");
        }
        EmbeddedFile.Relationship relationship = relationshipName == null
                ? null : enumValue(
                        EmbeddedFile.Relationship.class,
                        relationshipName,
                        "embedded-file relationship");
        return EmbeddedFile.fromOwnedContent(
                name,
                content,
                mime,
                description,
                relationship);
    }

    static void writeNavigationTarget(
            WorkerCodecIO.Output output,
            NavigationTarget target) throws IOException {
        output.writeString(target.getKind().name());
        if (target.getKind() == NavigationTarget.Kind.PAGE) {
            writePageDestination(output, target.getPageDestination().get());
        } else {
            output.writeString(target.getNamedDestination().get());
        }
    }

    static NavigationTarget readNavigationTarget(WorkerCodecIO.Input input)
            throws DocumentFailure {
        NavigationTarget.Kind kind = enumValue(
                NavigationTarget.Kind.class,
                input.readString(),
                "navigation-target kind");
        return kind == NavigationTarget.Kind.PAGE
                ? NavigationTarget.toPage(readPageDestination(input))
                : NavigationTarget.toNamedDestination(input.readString());
    }

    static void writeGoToAction(
            WorkerCodecIO.Output output,
            GoToAction action) throws IOException {
        output.writeInt(action.getVersion());
        writeNavigationTarget(output, action.getTarget());
    }

    static GoToAction readGoToAction(WorkerCodecIO.Input input)
            throws DocumentFailure {
        requireVersion(input.readInt(), GoToAction.VERSION_1);
        return GoToAction.version1(readNavigationTarget(input));
    }

    private static void writePageActions(
            WorkerCodecIO.Output output,
            Map<Integer, GoToAction> actions) throws IOException {
        output.writeInt(actions.size());
        for (Map.Entry<Integer, GoToAction> entry : actions.entrySet()) {
            output.writeInt(entry.getKey().intValue());
            writeGoToAction(output, entry.getValue());
        }
    }

    private static void readPageActions(
            WorkerCodecIO.Input input,
            UpdateActions.Builder builder,
            boolean open) throws DocumentFailure {
        int count = readCount(input, "page Action");
        for (int index = 0; index < count; index++) {
            int page = input.readInt();
            GoToAction action = readGoToAction(input);
            if (open) {
                builder.setPageOpenAction(page, action);
            } else {
                builder.setPageCloseAction(page, action);
            }
        }
    }

    private static void writeUpdateAnnotations(
            WorkerCodecIO.Output output,
            UpdateAnnotations command) throws IOException, DocumentFailure {
        output.writeInt(command.getVersion());
        output.writeInt(command.getAnnotations().size());
        for (Annotation annotation : command.getAnnotations()) {
            WorkerAnnotationCodec.writeAnnotation(output, annotation);
        }
        writeStrings(output, command.getRemovedIdentifiers());
    }

    private static UpdateAnnotations readUpdateAnnotations(
            WorkerCodecIO.Input input) throws DocumentFailure {
        requireVersion(input.readInt(), UpdateAnnotations.VERSION_1);
        UpdateAnnotations.Builder builder = UpdateAnnotations.version1();
        int count = readCount(input, "annotation");
        for (int index = 0; index < count; index++) {
            builder.put(WorkerAnnotationCodec.readAnnotation(input));
        }
        for (String identifier : readStrings(input)) {
            builder.remove(identifier);
        }
        return builder.build();
    }

    private static void writeRange(
            WorkerCodecIO.Output output,
            PageRange range) throws IOException {
        output.writeInt(range.getFirstPageNumber());
        output.writeInt(range.getLastPageNumber());
    }

    private static PageRange readRange(WorkerCodecIO.Input input)
            throws DocumentFailure {
        return PageRange.of(input.readInt(), input.readInt());
    }

    static void writeStrings(
            WorkerCodecIO.Output output,
            List<String> values) throws IOException {
        output.writeInt(values.size());
        for (String value : values) {
            output.writeString(value);
        }
    }

    static List<String> readStrings(WorkerCodecIO.Input input)
            throws DocumentFailure {
        int count = readCount(input, "String");
        List<String> values = new ArrayList<String>(count);
        for (int index = 0; index < count; index++) {
            values.add(input.readString());
        }
        return values;
    }

    static void writeIntegers(
            WorkerCodecIO.Output output,
            List<Integer> values) throws IOException {
        output.writeInt(values.size());
        for (Integer value : values) {
            output.writeInt(value.intValue());
        }
    }

    static List<Integer> readIntegers(WorkerCodecIO.Input input)
            throws DocumentFailure {
        int count = readCount(input, "Integer");
        List<Integer> values = new ArrayList<Integer>(count);
        for (int index = 0; index < count; index++) {
            values.add(Integer.valueOf(input.readInt()));
        }
        return values;
    }

    static int readCount(WorkerCodecIO.Input input, String label)
            throws DocumentFailure {
        int count = input.readInt();
        if (count < 0 || count > input.available()) {
            throw rejected("The Worker protocol value is invalid.");
        }
        input.accountCollectionEntries(count);
        return count;
    }

    static <E extends Enum<E>> E enumValue(
            Class<E> type,
            String name,
            String label) throws DocumentFailure {
        try {
            return Enum.valueOf(type, name);
        } catch (RuntimeException failure) {
            throw rejected("The Worker protocol value is invalid.");
        }
    }

    static void requireVersion(int actual, int expected)
            throws DocumentFailure {
        if (actual != expected) {
            throw rejected("The Worker value version is unsupported.");
        }
    }

    private static void requireOperandCount(
            List<BigDecimal> values,
            int expected) throws DocumentFailure {
        if (values.size() != expected) {
            throw rejected("The Worker page destination is invalid.");
        }
    }

    static DocumentFailure rejected(String diagnostic) {
        return WorkerCodecIO.workerFailure(
                DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                diagnostic);
    }
}
