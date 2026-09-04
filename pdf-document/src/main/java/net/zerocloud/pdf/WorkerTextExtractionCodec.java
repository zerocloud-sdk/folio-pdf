package net.zerocloud.pdf;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Explicit value codec for detached text and logical-structure results. */
final class WorkerTextExtractionCodec {

    private WorkerTextExtractionCodec() {
    }

    static void write(
            WorkerCodecIO.Output output,
            TextStructureExtraction extraction)
            throws IOException, DocumentFailure {
        output.writeInt(extraction.getPages().size());
        for (PageText page : extraction.getPages()) {
            writePage(output, page);
        }
        output.writeInt(extraction.getStructureRoots().size());
        for (LogicalStructureElement root : extraction.getStructureRoots()) {
            writeStructureElement(output, root);
        }
        output.writeInt(extraction.getDiagnostics().size());
        for (ExtractionDiagnostic diagnostic : extraction.getDiagnostics()) {
            output.writeString(diagnostic.getCode().name());
            output.writeInt(diagnostic.getPageNumber());
            output.writeInt(diagnostic.getTextItemIndex());
            output.writeBytes(diagnostic.sourceCodeForWorkflow());
            output.writeString(diagnostic.getMessage());
        }
    }

    static TextStructureExtraction read(WorkerCodecIO.Input input)
            throws DocumentFailure {
        int pageCount = WorkerCommandCodec.readCount(input, "text page result");
        List<PageText> pages = new ArrayList<PageText>(pageCount);
        for (int index = 0; index < pageCount; index++) {
            pages.add(readPage(input));
        }
        int rootCount = WorkerCommandCodec.readCount(
                input,
                "logical-structure root result");
        List<LogicalStructureElement> roots =
                new ArrayList<LogicalStructureElement>(rootCount);
        for (int index = 0; index < rootCount; index++) {
            roots.add(readStructureElement(input));
        }
        int diagnosticCount = WorkerCommandCodec.readCount(
                input,
                "extraction diagnostic result");
        List<ExtractionDiagnostic> diagnostics =
                new ArrayList<ExtractionDiagnostic>(diagnosticCount);
        for (int index = 0; index < diagnosticCount; index++) {
            diagnostics.add(new ExtractionDiagnostic(
                    WorkerCommandCodec.enumValue(
                            ExtractionDiagnostic.Code.class,
                            input.readString(),
                            "extraction diagnostic code"),
                    input.readInt(),
                    input.readInt(),
                    input.readBytes(),
                    input.readString()));
        }
        return new TextStructureExtraction(pages, roots, diagnostics);
    }

    private static void writePage(
            WorkerCodecIO.Output output,
            PageText page) throws IOException {
        output.writeInt(page.getPageNumber());
        output.writeInt(page.getRotation());
        output.writeBigDecimal(page.getUserUnit());
        output.writeBigDecimal(page.getCropBoxLeft());
        output.writeBigDecimal(page.getCropBoxBottom());
        output.writeBigDecimal(page.getCropBoxRight());
        output.writeBigDecimal(page.getCropBoxTop());
        output.writeString(page.getText());
        output.writeInt(page.getTextItems().size());
        for (TextItem item : page.getTextItems()) {
            writeTextItem(output, item);
        }
        output.writeInt(page.getMarkedContentSequences().size());
        for (MarkedContentSequence sequence : page.getMarkedContentSequences()) {
            writeMarkedContentSequence(output, sequence);
        }
    }

    private static PageText readPage(WorkerCodecIO.Input input)
            throws DocumentFailure {
        int pageNumber = input.readInt();
        int rotation = input.readInt();
        java.math.BigDecimal userUnit = input.readBigDecimal();
        java.math.BigDecimal left = input.readBigDecimal();
        java.math.BigDecimal bottom = input.readBigDecimal();
        java.math.BigDecimal right = input.readBigDecimal();
        java.math.BigDecimal top = input.readBigDecimal();
        String text = input.readString();
        int itemCount = WorkerCommandCodec.readCount(input, "text item result");
        List<TextItem> items = new ArrayList<TextItem>(itemCount);
        for (int index = 0; index < itemCount; index++) {
            items.add(readTextItem(input));
        }
        int sequenceCount = WorkerCommandCodec.readCount(
                input,
                "marked-content sequence result");
        List<MarkedContentSequence> sequences =
                new ArrayList<MarkedContentSequence>(sequenceCount);
        for (int index = 0; index < sequenceCount; index++) {
            sequences.add(readMarkedContentSequence(input));
        }
        return new PageText(
                pageNumber,
                rotation,
                userUnit,
                left,
                bottom,
                right,
                top,
                text,
                items,
                sequences);
    }

    private static void writeTextItem(
            WorkerCodecIO.Output output,
            TextItem item) throws IOException {
        output.writeInt(item.getIndex());
        CharacterMapping mapping = item.getCharacterMapping();
        output.writeBytes(mapping.sourceCodeForWorkflow());
        output.writeString(mapping.getConfidence().name());
        output.writeNullableString(mapping.getUnicode().orElse(null));
        output.writeNullableString(mapping.getExplicitUnicode().orElse(null));
        output.writeNullableString(mapping.getInferredUnicode().orElse(null));
        output.writeString(item.getTextContribution());
        TextGeometry geometry = item.getGeometry();
        output.writeBigDecimal(geometry.getA());
        output.writeBigDecimal(geometry.getB());
        output.writeBigDecimal(geometry.getC());
        output.writeBigDecimal(geometry.getD());
        output.writeBigDecimal(geometry.getE());
        output.writeBigDecimal(geometry.getF());
        output.writeBigDecimal(geometry.getAdvanceX());
        output.writeBigDecimal(geometry.getAdvanceY());
        output.writeString(item.getRenderingMode().name());
        writeIntegers(output, item.getMarkedContentSequenceIds());
    }

    private static TextItem readTextItem(WorkerCodecIO.Input input)
            throws DocumentFailure {
        int index = input.readInt();
        CharacterMapping mapping = new CharacterMapping(
                input.readBytes(),
                WorkerCommandCodec.enumValue(
                        CharacterMapping.Confidence.class,
                        input.readString(),
                        "character-mapping confidence"),
                input.readNullableString(),
                input.readNullableString(),
                input.readNullableString());
        String contribution = input.readString();
        TextGeometry geometry = new TextGeometry(
                input.readBigDecimal(),
                input.readBigDecimal(),
                input.readBigDecimal(),
                input.readBigDecimal(),
                input.readBigDecimal(),
                input.readBigDecimal(),
                input.readBigDecimal(),
                input.readBigDecimal());
        TextRenderingMode renderingMode = WorkerCommandCodec.enumValue(
                TextRenderingMode.class,
                input.readString(),
                "text rendering mode");
        return new TextItem(
                index,
                mapping,
                contribution,
                geometry,
                renderingMode,
                readIntegers(input, "marked-content identifier"));
    }

    private static void writeMarkedContentSequence(
            WorkerCodecIO.Output output,
            MarkedContentSequence value) throws IOException {
        output.writeInt(value.getId());
        output.writeNullableString(value.getTag());
        writeNullableInteger(output, value.getMarkedContentId().orElse(null));
        writeNullableInteger(output, value.getParentId().orElse(null));
        output.writeNullableString(value.getLanguage().orElse(null));
        output.writeNullableString(value.getAlternateText().orElse(null));
        output.writeNullableString(value.getActualText().orElse(null));
        writeIntegers(output, value.getTextItemIndices());
    }

    private static MarkedContentSequence readMarkedContentSequence(
            WorkerCodecIO.Input input) throws DocumentFailure {
        return new MarkedContentSequence(
                input.readInt(),
                input.readNullableString(),
                readNullableInteger(input),
                readNullableInteger(input),
                input.readNullableString(),
                input.readNullableString(),
                input.readNullableString(),
                readIntegers(input, "marked-content text-item index"));
    }

    private static void writeStructureElement(
            WorkerCodecIO.Output output,
            LogicalStructureElement element)
            throws IOException, DocumentFailure {
        Deque<StructureWriteFrame> frames =
                new ArrayDeque<StructureWriteFrame>();
        writeStructureHeader(output, element);
        frames.push(new StructureWriteFrame(element));
        while (!frames.isEmpty()) {
            StructureWriteFrame frame = frames.peek();
            if (frame.index >= frame.element.getChildren().size()) {
                frames.pop();
                continue;
            }
            LogicalStructureItem item = frame.element.getChildren().get(
                    frame.index++);
            output.writeString(item.getKind().name());
            if (item.getKind() == LogicalStructureItem.Kind.ELEMENT) {
                LogicalStructureElement child = item.getElement().get();
                output.requireNestingDepth(frames.size());
                writeStructureHeader(output, child);
                frames.push(new StructureWriteFrame(child));
            } else {
                MarkedContentReference reference = item.getMarkedContent().get();
                output.writeInt(reference.getPageNumber());
                output.writeInt(reference.getMarkedContentId());
                writeNullableInteger(
                        output,
                        reference.getMarkedContentSequenceId().orElse(null));
            }
        }
    }

    private static void writeStructureHeader(
            WorkerCodecIO.Output output,
            LogicalStructureElement element) throws IOException {
        output.writeInt(element.getId());
        output.writeString(element.getRole());
        output.writeNullableString(element.getResolvedRole().orElse(null));
        output.writeString(element.getRoleResolution().name());
        output.writeNullableString(element.getDeclaredLanguage().orElse(null));
        output.writeNullableString(element.getEffectiveLanguage().orElse(null));
        output.writeString(element.getLanguageSource().name());
        output.writeNullableString(element.getAlternateText().orElse(null));
        output.writeNullableString(element.getActualText().orElse(null));
        output.writeInt(element.getChildren().size());
    }

    private static LogicalStructureElement readStructureElement(
            WorkerCodecIO.Input input) throws DocumentFailure {
        Deque<StructureReadFrame> frames =
                new ArrayDeque<StructureReadFrame>();
        frames.push(readStructureHeader(input));
        while (true) {
            StructureReadFrame frame = frames.peek();
            if (frame.children.size() == frame.childCount) {
                LogicalStructureElement completed = frame.build();
                frames.pop();
                if (frames.isEmpty()) {
                    return completed;
                }
                frames.peek().children.add(
                        LogicalStructureItem.element(completed));
                continue;
            }
            LogicalStructureItem.Kind kind = WorkerCommandCodec.enumValue(
                    LogicalStructureItem.Kind.class,
                    input.readString(),
                    "logical-structure child kind");
            if (kind == LogicalStructureItem.Kind.ELEMENT) {
                input.requireNestingDepth(frames.size());
                frames.push(readStructureHeader(input));
            } else {
                frame.children.add(LogicalStructureItem.markedContent(
                        new MarkedContentReference(
                                input.readInt(),
                                input.readInt(),
                                readNullableInteger(input))));
            }
        }
    }

    private static StructureReadFrame readStructureHeader(
            WorkerCodecIO.Input input) throws DocumentFailure {
        int id = input.readInt();
        String role = input.readString();
        String resolvedRole = input.readNullableString();
        LogicalStructureElement.RoleResolution resolution =
                WorkerCommandCodec.enumValue(
                        LogicalStructureElement.RoleResolution.class,
                        input.readString(),
                        "logical-structure role resolution");
        String declaredLanguage = input.readNullableString();
        String effectiveLanguage = input.readNullableString();
        LogicalStructureElement.LanguageSource languageSource =
                WorkerCommandCodec.enumValue(
                        LogicalStructureElement.LanguageSource.class,
                        input.readString(),
                        "logical-structure language source");
        String alternateText = input.readNullableString();
        String actualText = input.readNullableString();
        int childCount = WorkerCommandCodec.readCount(
                input,
                "logical-structure child result");
        return new StructureReadFrame(
                id,
                role,
                resolvedRole,
                resolution,
                declaredLanguage,
                effectiveLanguage,
                languageSource,
                alternateText,
                actualText,
                childCount);
    }

    private static final class StructureWriteFrame {

        private final LogicalStructureElement element;
        private int index;

        private StructureWriteFrame(LogicalStructureElement element) {
            this.element = element;
        }
    }

    private static final class StructureReadFrame {

        private final int id;
        private final String role;
        private final String resolvedRole;
        private final LogicalStructureElement.RoleResolution resolution;
        private final String declaredLanguage;
        private final String effectiveLanguage;
        private final LogicalStructureElement.LanguageSource languageSource;
        private final String alternateText;
        private final String actualText;
        private final int childCount;
        private final List<LogicalStructureItem> children;

        private StructureReadFrame(
                int id,
                String role,
                String resolvedRole,
                LogicalStructureElement.RoleResolution resolution,
                String declaredLanguage,
                String effectiveLanguage,
                LogicalStructureElement.LanguageSource languageSource,
                String alternateText,
                String actualText,
                int childCount) {
            this.id = id;
            this.role = role;
            this.resolvedRole = resolvedRole;
            this.resolution = resolution;
            this.declaredLanguage = declaredLanguage;
            this.effectiveLanguage = effectiveLanguage;
            this.languageSource = languageSource;
            this.alternateText = alternateText;
            this.actualText = actualText;
            this.childCount = childCount;
            this.children = new ArrayList<LogicalStructureItem>(childCount);
        }

        private LogicalStructureElement build() {
            return new LogicalStructureElement(
                    id,
                    role,
                    resolvedRole,
                    resolution,
                    declaredLanguage,
                    effectiveLanguage,
                    languageSource,
                    alternateText,
                    actualText,
                    children);
        }
    }

    private static void writeIntegers(
            WorkerCodecIO.Output output,
            List<Integer> values) throws IOException {
        output.writeInt(values.size());
        for (Integer value : values) {
            output.writeInt(value.intValue());
        }
    }

    private static List<Integer> readIntegers(
            WorkerCodecIO.Input input,
            String description) throws DocumentFailure {
        int count = WorkerCommandCodec.readCount(input, description);
        List<Integer> values = new ArrayList<Integer>(count);
        for (int index = 0; index < count; index++) {
            values.add(Integer.valueOf(input.readInt()));
        }
        return values;
    }

    private static void writeNullableInteger(
            WorkerCodecIO.Output output,
            Integer value) throws IOException {
        output.writeBoolean(value != null);
        if (value != null) {
            output.writeInt(value.intValue());
        }
    }

    private static Integer readNullableInteger(WorkerCodecIO.Input input)
            throws DocumentFailure {
        return input.readBoolean() ? Integer.valueOf(input.readInt()) : null;
    }
}
