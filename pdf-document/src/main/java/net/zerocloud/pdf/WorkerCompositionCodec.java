package net.zerocloud.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import net.zerocloud.pdf.composition.CanvasBlendMode;
import net.zerocloud.pdf.composition.CanvasColor;
import net.zerocloud.pdf.composition.CanvasColorSpace;
import net.zerocloud.pdf.composition.CanvasFont;
import net.zerocloud.pdf.composition.CanvasImage;
import net.zerocloud.pdf.composition.CanvasMask;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.composition.CanvasProgram;
import net.zerocloud.pdf.composition.CanvasRectangle;
import net.zerocloud.pdf.composition.CanvasResourceLimits;
import net.zerocloud.pdf.composition.CanvasTransparencyGroup;
import net.zerocloud.pdf.composition.CanvasTransparencyState;
import net.zerocloud.pdf.composition.CanvasWindingRule;
import net.zerocloud.pdf.composition.FontLimits;
import net.zerocloud.pdf.composition.FontSelection;
import net.zerocloud.pdf.composition.FontSource;
import net.zerocloud.pdf.composition.PositionedUnicodeText;
import net.zerocloud.pdf.composition.CompositionLimits;
import net.zerocloud.pdf.composition.LayoutPage;
import net.zerocloud.pdf.composition.PageMargins;
import net.zerocloud.pdf.composition.Paragraph;
import net.zerocloud.pdf.composition.ParagraphFlow;
import net.zerocloud.pdf.composition.TabStop;
import net.zerocloud.pdf.composition.command.ComposeParagraphs;
import net.zerocloud.pdf.composition.command.DrawCanvas;
import net.zerocloud.pdf.composition.command.DrawPositionedUnicodeText;

/** Whitelisted codecs for Canvas and positioned-text Commands. */
final class WorkerCompositionCodec {

    private static final int MAXIMUM_PROGRAM_DEPTH = 16;

    private WorkerCompositionCodec() {
    }

    interface RemoteFontSource {
        InputStream open(long identifier) throws DocumentFailure;
    }

    static void writeDrawCanvas(
            WorkerCodecIO.Output output,
            DrawCanvas command,
            WorkerReferenceRegistry references)
            throws IOException, DocumentFailure {
        output.writeInt(command.getVersion());
        output.writeInt(command.getPageNumber());
        writeProgram(output, command.getProgram(), references, 0);
        output.writeBoolean(command.getResourceLimits().isPresent());
        if (command.getResourceLimits().isPresent()) {
            writeResourceLimits(output, command.getResourceLimits().get());
        }
    }

    static DrawCanvas readDrawCanvas(
            WorkerCodecIO.Input input,
            WorkerReferenceRegistry references) throws DocumentFailure {
        int version = input.readInt();
        int pageNumber = input.readInt();
        CanvasProgram program = readProgram(input, references, 0);
        boolean hasLimits = input.readBoolean();
        if (version == DrawCanvas.VERSION_1 && !hasLimits) {
            return DrawCanvas.version1(pageNumber, program);
        }
        if (version == DrawCanvas.VERSION_2 && hasLimits) {
            return DrawCanvas.version2(
                    pageNumber,
                    program,
                    readResourceLimits(input));
        }
        throw WorkerCommandCodec.rejected(
                "The Worker Canvas Command version is unsupported.");
    }

    static void writePositionedTextCommand(
            WorkerCodecIO.Output output,
            DrawPositionedUnicodeText command,
            WorkerReferenceRegistry references,
            WorkerFontSourceCache fontSources)
            throws IOException, DocumentFailure {
        output.writeInt(command.getVersion());
        output.writeInt(command.getPageNumber());
        PositionedUnicodeText text = command.getPositionedUnicodeText();
        output.writeInt(text.getVersion());
        output.writeString(text.getText());
        output.writeDouble(text.getFontSize());
        output.writeString(text.getRenderingMode().name());
        writeMatrix(output, text.getTextMatrix());
        writeFontSelection(
                output,
                text.getFontSelection(),
                command.getLimits(),
                fontSources);
        writeFontLimits(output, command.getLimits());
    }

    static DrawPositionedUnicodeText readPositionedTextCommand(
            WorkerCodecIO.Input input,
            WorkerReferenceRegistry references,
            RemoteFontSource remoteFonts) throws DocumentFailure {
        WorkerCommandCodec.requireVersion(
                input.readInt(),
                DrawPositionedUnicodeText.VERSION_1);
        int pageNumber = input.readInt();
        WorkerCommandCodec.requireVersion(
                input.readInt(),
                PositionedUnicodeText.VERSION_1);
        String text = input.readString();
        double fontSize = input.readDouble();
        TextRenderingMode renderingMode = WorkerCommandCodec.enumValue(
                TextRenderingMode.class,
                input.readString(),
                "text rendering mode");
        CanvasMatrix matrix = readMatrix(input);
        FontSelection selection = readFontSelection(input, remoteFonts);
        FontLimits limits = readFontLimits(input);
        return DrawPositionedUnicodeText.version1(
                pageNumber,
                PositionedUnicodeText.version1(
                        text,
                        selection,
                        fontSize,
                        renderingMode,
                        matrix),
                limits);
    }

    static void writeParagraphs(WorkerCodecIO.Output output, ComposeParagraphs command,
            WorkerReferenceRegistry references, WorkerFontSourceCache fonts)
            throws IOException, DocumentFailure {
        output.writeInt(command.getVersion());
        if (command.getVersion() == ComposeParagraphs.VERSION_2) { output.writeString(command.getFlushMode().name()); }
        CompositionLimits limits = command.getLimits();
        output.writeInt(limits.getVersion());
        output.writeInt(limits.getMaximumPages());
        output.writeInt(limits.getMaximumAreas());
        output.writeInt(limits.getMaximumFlowItems());
        output.writeInt(limits.getMaximumInlines());
        output.writeInt(limits.getMaximumLines());
        output.writeLong(limits.getMaximumGeneratedContentBytes());
        writeFontLimits(output, limits.getFontLimits());
        writeResourceLimits(output, limits.getGraphicLimits());
        if (limits.getVersion() == CompositionLimits.VERSION_2) {
            output.writeInt(limits.getMaximumLayoutAttempts());
            output.writeInt(limits.getMaximumRelayouts());
        }
        ParagraphFlow flow = command.getFlow();
        output.writeInt(flow.getVersion());
        writeFontSelection(output, flow.getFonts(), limits.getFontLimits(), fonts);
        writeLayoutPages(output, flow.getPages());
        output.writeInt(flow.getItems().size());
        for (ParagraphFlow.Item item : flow.getItems()) {
            output.writeString(item.getKind().name());
            if (item.getKind() == ParagraphFlow.Item.Kind.AREA_BREAK) { continue; }
            Paragraph paragraph = item.getParagraph();
            output.writeInt(paragraph.getVersion());
            output.writeDouble(paragraph.getLeading());
            output.writeDouble(paragraph.getMaximumWidth());
            output.writeString(paragraph.getAlignment().name());
            if (paragraph.getVersion() == Paragraph.VERSION_2) {
                output.writeDouble(paragraph.getLeftIndent());
                output.writeDouble(paragraph.getRightIndent());
                output.writeDouble(paragraph.getFirstLineIndent());
                output.writeDouble(paragraph.getTabInterval());
                output.writeBoolean(paragraph.isKeepWithNext());
                output.writeBoolean(paragraph.isKeepTogether());
                output.writeInt(paragraph.getWidows());
                output.writeInt(paragraph.getOrphans());
                output.writeString(paragraph.getOverflow().name());
                output.writeInt(paragraph.getTabStops().size());
                for (TabStop stop : paragraph.getTabStops()) {
                    output.writeInt(stop.getVersion());
                    output.writeDouble(stop.getPosition());
                    output.writeString(stop.getAlignment().name());
                    output.writeInt(stop.getAnchor());
                }
            }
            output.writeInt(paragraph.getInlines().size());
            for (Paragraph.Inline inline : paragraph.getInlines()) {
                output.writeString(inline.getKind().name());
                if (inline.getKind() == Paragraph.Inline.Kind.TEXT) {
                    output.writeString(inline.getText());
                    output.writeDouble(inline.getFontSize());
                } else {
                    output.writeDouble(inline.getWidth());
                    output.writeDouble(inline.getHeight());
                    writeTransparencyGroup(output, inline.getGraphic(), references, 0);
                }
            }
        }
    }

    static ComposeParagraphs readParagraphs(WorkerCodecIO.Input input,
            WorkerReferenceRegistry references, RemoteFontSource fonts) throws DocumentFailure {
        int version = paragraphVersion(input);
        ComposeParagraphs.FlushMode flush = version == 2 ? WorkerCommandCodec.enumValue(
                ComposeParagraphs.FlushMode.class, input.readString(), "Paragraph flush mode")
                : ComposeParagraphs.FlushMode.IMMEDIATE;
        int limitVersion = paragraphVersion(input);
        CompositionLimits.Builder bounds = limitVersion == 2 ? CompositionLimits.version2() : CompositionLimits.builder();
        bounds.maximumPages(input.readInt()).maximumAreas(input.readInt())
                .maximumFlowItems(input.readInt()).maximumInlines(input.readInt())
                .maximumLines(input.readInt()).maximumGeneratedContentBytes(input.readLong())
                .fontLimits(readFontLimits(input)).graphicLimits(readResourceLimits(input));
        if (limitVersion == 2) { bounds.maximumLayoutAttempts(input.readInt()).maximumRelayouts(input.readInt()); }
        CompositionLimits limits = bounds.build();
        int flowVersion = paragraphVersion(input);
        FontSelection selection = readFontSelection(input, fonts);
        ParagraphFlow.Builder flow = flowVersion == 2 ? ParagraphFlow.version2(selection) : ParagraphFlow.version1(selection);
        for (LayoutPage page : readLayoutPages(input)) { flow.page(page); }
        int itemCount = WorkerCommandCodec.readCount(input, "Paragraph Flow Item");
        for (int index = 0; index < itemCount; index++) {
            ParagraphFlow.Item.Kind kind = WorkerCommandCodec.enumValue(ParagraphFlow.Item.Kind.class,
                    input.readString(), "Paragraph Flow Item kind");
            if (kind == ParagraphFlow.Item.Kind.AREA_BREAK) {
                flow.areaBreak();
                continue;
            }
            int paragraphVersion = paragraphVersion(input);
            double leading = input.readDouble();
            Paragraph.Builder paragraph = paragraphVersion == 2 ? Paragraph.version2(leading) : Paragraph.version1(leading);
            paragraph.maximumWidth(input.readDouble()).alignment(WorkerCommandCodec.enumValue(
                    Paragraph.Alignment.class, input.readString(), "Paragraph alignment"));
            if (paragraphVersion == 2) {
                paragraph.indentation(input.readDouble(), input.readDouble(), input.readDouble())
                        .tabInterval(input.readDouble()).keepWithNext(input.readBoolean())
                        .keepTogether(input.readBoolean()).widows(input.readInt()).orphans(input.readInt())
                        .overflow(WorkerCommandCodec.enumValue(Paragraph.Overflow.class, input.readString(), "Paragraph overflow"));
                int stops = WorkerCommandCodec.readCount(input, "Paragraph tab stop");
                for (int stop = 0; stop < stops; stop++) {
                    WorkerCommandCodec.requireVersion(input.readInt(), TabStop.VERSION_1);
                    double position = input.readDouble();
                    TabStop.Alignment alignment = WorkerCommandCodec.enumValue(
                            TabStop.Alignment.class, input.readString(), "Tab alignment");
                    int anchor = input.readInt();
                    paragraph.tabStop(alignment == TabStop.Alignment.ANCHOR ? TabStop.anchored(position, anchor)
                            : TabStop.version1(position, alignment));
                }
            }
            int inlineCount = WorkerCommandCodec.readCount(input, "Paragraph Inline");
            for (int inline = 0; inline < inlineCount; inline++) {
                Paragraph.Inline.Kind inlineKind = WorkerCommandCodec.enumValue(Paragraph.Inline.Kind.class,
                        input.readString(), "Paragraph Inline kind");
                if (inlineKind == Paragraph.Inline.Kind.TEXT) {
                    paragraph.text(input.readString(), input.readDouble());
                } else {
                    double width = input.readDouble();
                    double height = input.readDouble();
                    paragraph.graphic(readTransparencyGroup(input, references, 0), width, height);
                }
            }
            flow.paragraph(paragraph.build());
        }
        return version == 2 ? ComposeParagraphs.version2(flow.build(), limits, flush)
                : ComposeParagraphs.version1(flow.build(), limits);
    }

    private static int paragraphVersion(WorkerCodecIO.Input input) throws DocumentFailure {
        int version = input.readInt();
        WorkerCommandCodec.requireVersion(version, version == 2 ? 2 : 1);
        return version;
    }

    static void writeLayoutPages(WorkerCodecIO.Output output, List<LayoutPage> pages)
            throws IOException, DocumentFailure {
        output.writeInt(pages.size());
        for (LayoutPage page : pages) {
            output.writeInt(page.getVersion());
            output.writeDouble(page.getWidth());
            output.writeDouble(page.getHeight());
            PageMargins margin = page.getMargins();
            output.writeDouble(margin.getTop());
            output.writeDouble(margin.getRight());
            output.writeDouble(margin.getBottom());
            output.writeDouble(margin.getLeft());
            output.writeInt(page.getAreas().size());
            for (CanvasRectangle area : page.getAreas()) {
                output.writeDouble(area.getLowerLeftX());
                output.writeDouble(area.getLowerLeftY());
                output.writeDouble(area.getUpperRightX());
                output.writeDouble(area.getUpperRightY());
            }
        }
    }

    static LayoutPage[] readLayoutPages(WorkerCodecIO.Input input) throws DocumentFailure {
        int count = WorkerCommandCodec.readCount(input, "Layout Page");
        LayoutPage[] pages = new LayoutPage[count];
        for (int index = 0; index < count; index++) {
            WorkerCommandCodec.requireVersion(input.readInt(), LayoutPage.VERSION_1);
            double width = input.readDouble();
            double height = input.readDouble();
            PageMargins margin = PageMargins.of(input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble());
            int areaCount = WorkerCommandCodec.readCount(input, "Layout Area");
            CanvasRectangle[] areas = new CanvasRectangle[areaCount];
            for (int area = 0; area < areaCount; area++) {
                areas[area] = CanvasRectangle.of(input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble());
            }
            pages[index] = LayoutPage.version1(width, height, margin, areas);
        }
        return pages;
    }

    private static void writeProgram(
            WorkerCodecIO.Output output,
            CanvasProgram program,
            WorkerReferenceRegistry references,
            int depth) throws IOException, DocumentFailure {
        output.requireNestingDepth(depth);
        if (depth > MAXIMUM_PROGRAM_DEPTH) {
            throw WorkerCommandCodec.rejected(
                    "The Worker Canvas Program is too deeply nested.");
        }
        output.writeInt(program.getVersion());
        output.writeInt(program.getInstructionCount());
        for (CanvasProgram.Instruction instruction
                : program.getInstructions()) {
            output.writeString(instruction.getKind().name());
            switch (instruction.getKind()) {
                case SAVE_STATE:
                case RESTORE_STATE:
                case CLOSE_PATH:
                case STROKE:
                case END_TEXT:
                    break;
                case TRANSFORM:
                case SET_TEXT_MATRIX:
                    writeMatrix(output, instruction.getMatrix());
                    break;
                case MOVE_TO:
                case LINE_TO:
                case CURVE_TO:
                    writeDoubles(output, instruction.getNumbers());
                    break;
                case FILL:
                case CLIP:
                    output.writeString(instruction.getWindingRule().name());
                    break;
                case BEGIN_TEXT:
                    references.write(
                            output,
                            instruction.getFont().getObjectReference());
                    output.writeDouble(instruction.getNumbers()[0]);
                    output.writeString(instruction.getRenderingMode().name());
                    writeMatrix(output, instruction.getMatrix());
                    break;
                case SHOW_GLYPH:
                    output.writeDefensiveBytes(
                            instruction.getGlyphCodeLength(),
                            instruction::getGlyphCode);
                    break;
                case SET_FILL_COLOR:
                case SET_STROKE_COLOR:
                    writeColor(output, instruction.getColor());
                    break;
                case SET_TRANSPARENCY:
                    writeTransparencyState(
                            output,
                            instruction.getTransparencyState());
                    break;
                case DRAW_IMAGE:
                    writeImage(output, instruction.getImage(), references);
                    writeMatrix(output, instruction.getMatrix());
                    break;
                case DRAW_TRANSPARENCY_GROUP:
                    writeTransparencyGroup(
                            output,
                            instruction.getTransparencyGroup(),
                            references,
                            depth + 1);
                    writeMatrix(output, instruction.getMatrix());
                    break;
                default:
                    throw WorkerCommandCodec.rejected(
                            "The Worker Canvas instruction is unsupported.");
            }
        }
    }

    private static CanvasProgram readProgram(
            WorkerCodecIO.Input input,
            WorkerReferenceRegistry references,
            int depth) throws DocumentFailure {
        input.requireNestingDepth(depth);
        if (depth > MAXIMUM_PROGRAM_DEPTH) {
            throw WorkerCommandCodec.rejected(
                    "The Worker Canvas Program is too deeply nested.");
        }
        int version = input.readInt();
        CanvasProgram.Builder builder;
        if (version == CanvasProgram.VERSION_1) {
            builder = CanvasProgram.version1();
        } else if (version == CanvasProgram.VERSION_2) {
            builder = CanvasProgram.version2();
        } else {
            throw WorkerCommandCodec.rejected(
                    "The Worker Canvas Program version is unsupported.");
        }
        int count = WorkerCommandCodec.readCount(
                input,
                "Canvas instruction");
        for (int index = 0; index < count; index++) {
            CanvasProgram.Kind kind = WorkerCommandCodec.enumValue(
                    CanvasProgram.Kind.class,
                    input.readString(),
                    "Canvas instruction");
            switch (kind) {
                case SAVE_STATE:
                    builder.saveState();
                    break;
                case RESTORE_STATE:
                    builder.restoreState();
                    break;
                case TRANSFORM:
                    builder.transform(readMatrix(input));
                    break;
                case MOVE_TO:
                    double[] move = readDoubles(input, 2);
                    builder.moveTo(move[0], move[1]);
                    break;
                case LINE_TO:
                    double[] line = readDoubles(input, 2);
                    builder.lineTo(line[0], line[1]);
                    break;
                case CURVE_TO:
                    double[] curve = readDoubles(input, 6);
                    builder.curveTo(
                            curve[0], curve[1], curve[2],
                            curve[3], curve[4], curve[5]);
                    break;
                case CLOSE_PATH:
                    builder.closePath();
                    break;
                case STROKE:
                    builder.stroke();
                    break;
                case FILL:
                    builder.fill(readWindingRule(input));
                    break;
                case CLIP:
                    builder.clip(readWindingRule(input));
                    break;
                case BEGIN_TEXT:
                    builder.beginText(
                            CanvasFont.version1(references.read(input)),
                            input.readDouble(),
                            readRenderingMode(input),
                            readMatrix(input));
                    break;
                case SET_TEXT_MATRIX:
                    builder.setTextMatrix(readMatrix(input));
                    break;
                case SHOW_GLYPH:
                    builder.showGlyph(input.readBytes(2));
                    break;
                case END_TEXT:
                    builder.endText();
                    break;
                case SET_FILL_COLOR:
                    builder.setFillColor(readColor(input));
                    break;
                case SET_STROKE_COLOR:
                    builder.setStrokeColor(readColor(input));
                    break;
                case SET_TRANSPARENCY:
                    builder.setTransparency(readTransparencyState(input));
                    break;
                case DRAW_IMAGE:
                    builder.drawImage(
                            readImage(input, references),
                            readMatrix(input));
                    break;
                case DRAW_TRANSPARENCY_GROUP:
                    builder.drawTransparencyGroup(
                            readTransparencyGroup(
                                    input,
                                    references,
                                    depth + 1),
                            readMatrix(input));
                    break;
                default:
                    throw WorkerCommandCodec.rejected(
                            "The Worker Canvas instruction is unsupported.");
            }
        }
        return builder.build();
    }

    private static void writeImage(
            WorkerCodecIO.Output output,
            CanvasImage image,
            WorkerReferenceRegistry references)
            throws IOException, DocumentFailure {
        output.writeInt(image.getVersion());
        output.writeString(image.getSourceKind().name());
        switch (image.getSourceKind()) {
            case JPEG:
            case PNG:
            case TIFF:
                output.writeDefensiveBytes(
                        image.getByteLength(),
                        () -> image.getBytes().get());
                break;
            case RAW_SAMPLES:
                output.writeInt(image.getWidth());
                output.writeInt(image.getHeight());
                output.writeInt(image.getBitsPerComponent());
                writeColorSpace(output, image.getColorSpace().get());
                output.writeDefensiveBytes(
                        image.getByteLength(),
                        () -> image.getBytes().get());
                break;
            case EXISTING:
                references.write(output, image.getObjectReference().get());
                break;
            default:
                throw WorkerCommandCodec.rejected(
                        "The Worker Canvas Image kind is unsupported.");
        }
        output.writeBoolean(image.getExplicitMask().isPresent());
        if (image.getExplicitMask().isPresent()) {
            writeMask(output, image.getExplicitMask().get());
        }
        output.writeBoolean(image.getSoftMask().isPresent());
        if (image.getSoftMask().isPresent()) {
            writeMask(output, image.getSoftMask().get());
        }
    }

    private static CanvasImage readImage(
            WorkerCodecIO.Input input,
            WorkerReferenceRegistry references) throws DocumentFailure {
        WorkerCommandCodec.requireVersion(
                input.readInt(),
                CanvasImage.VERSION_1);
        CanvasImage.SourceKind kind = WorkerCommandCodec.enumValue(
                CanvasImage.SourceKind.class,
                input.readString(),
                "Canvas Image source kind");
        CanvasImage image;
        switch (kind) {
            case JPEG:
                image = CanvasImage.jpeg(input.readBytes(3));
                break;
            case PNG:
                image = CanvasImage.png(input.readBytes(3));
                break;
            case TIFF:
                image = CanvasImage.tiff(input.readBytes(3));
                break;
            case RAW_SAMPLES:
                image = CanvasImage.rawSamples(
                        input.readInt(),
                        input.readInt(),
                        input.readInt(),
                        readColorSpace(input),
                        input.readBytes(3));
                break;
            case EXISTING:
                image = CanvasImage.existing(references.read(input));
                break;
            default:
                throw WorkerCommandCodec.rejected(
                        "The Worker Canvas Image kind is unsupported.");
        }
        if (input.readBoolean()) {
            image = image.withExplicitMask(readMask(input));
        }
        if (input.readBoolean()) {
            image = image.withSoftMask(readMask(input));
        }
        return image;
    }

    private static void writeMask(
            WorkerCodecIO.Output output,
            CanvasMask mask) throws IOException {
        output.writeInt(mask.getVersion());
        output.writeString(mask.getKind().name());
        output.writeInt(mask.getWidth());
        output.writeInt(mask.getHeight());
        output.writeBoolean(mask.isInverted());
        output.writeDefensiveBytes(
                mask.getSampleByteLength(),
                mask::getSamples);
    }

    private static CanvasMask readMask(WorkerCodecIO.Input input)
            throws DocumentFailure {
        WorkerCommandCodec.requireVersion(input.readInt(), CanvasMask.VERSION_1);
        CanvasMask.Kind kind = WorkerCommandCodec.enumValue(
                CanvasMask.Kind.class,
                input.readString(),
                "Canvas mask kind");
        int width = input.readInt();
        int height = input.readInt();
        boolean inverted = input.readBoolean();
        byte[] samples = input.readBytes(2);
        if (kind == CanvasMask.Kind.EXPLICIT_IMAGE) {
            return CanvasMask.explicit(width, height, inverted, samples);
        }
        if (inverted) {
            throw WorkerCommandCodec.rejected(
                    "The Worker soft Canvas mask is invalid.");
        }
        return CanvasMask.soft(width, height, samples);
    }

    private static void writeColor(
            WorkerCodecIO.Output output,
            CanvasColor color) throws IOException {
        output.writeInt(color.getVersion());
        writeColorSpace(output, color.getColorSpace());
        writeDoubles(output, color.getComponents());
    }

    private static CanvasColor readColor(WorkerCodecIO.Input input)
            throws DocumentFailure {
        WorkerCommandCodec.requireVersion(input.readInt(), CanvasColor.VERSION_1);
        CanvasColorSpace space = readColorSpace(input);
        return CanvasColor.of(space, readDoubles(input, -1));
    }

    private static void writeColorSpace(
            WorkerCodecIO.Output output,
            CanvasColorSpace space) throws IOException {
        output.writeInt(space.getVersion());
        output.writeString(space.getFamily().name());
        writeDoubles(output, space.getWhitePoint());
        writeDoubles(output, space.getBlackPoint());
        writeDoubles(output, space.getGamma());
        writeDoubles(output, space.getMatrix());
        boolean hasProfile = space.getFamily()
                == CanvasColorSpace.Family.ICC_BASED;
        output.writeBoolean(hasProfile);
        if (hasProfile) {
            output.writeDefensiveBytes(
                    space.getIccProfileByteLength(),
                    () -> space.getIccProfileBytes().get());
        }
    }

    private static CanvasColorSpace readColorSpace(WorkerCodecIO.Input input)
            throws DocumentFailure {
        WorkerCommandCodec.requireVersion(
                input.readInt(),
                CanvasColorSpace.VERSION_1);
        CanvasColorSpace.Family family = WorkerCommandCodec.enumValue(
                CanvasColorSpace.Family.class,
                input.readString(),
                "Canvas Color Space family");
        double[] white = readDoubles(input, -1);
        double[] black = readDoubles(input, -1);
        double[] gamma = readDoubles(input, -1);
        double[] matrix = readDoubles(input, -1);
        byte[] profile = input.readNullableBytes(2);
        switch (family) {
            case DEVICE_GRAY:
                requireEmptyColorData(white, black, gamma, matrix, profile);
                return CanvasColorSpace.deviceGray();
            case DEVICE_RGB:
                requireEmptyColorData(white, black, gamma, matrix, profile);
                return CanvasColorSpace.deviceRgb();
            case DEVICE_CMYK:
                requireEmptyColorData(white, black, gamma, matrix, profile);
                return CanvasColorSpace.deviceCmyk();
            case CAL_GRAY:
                if (gamma.length != 1 || profile != null) {
                    throw invalidColorSpace();
                }
                return CanvasColorSpace.calibratedGray(
                        white,
                        black,
                        gamma[0]);
            case CAL_RGB:
                if (profile != null) {
                    throw invalidColorSpace();
                }
                return CanvasColorSpace.calibratedRgb(
                        white,
                        black,
                        gamma,
                        matrix);
            case ICC_BASED:
                if (profile == null
                        || white.length != 0
                        || black.length != 0
                        || gamma.length != 0
                        || matrix.length != 0) {
                    throw invalidColorSpace();
                }
                return CanvasColorSpace.iccBased(profile);
            default:
                throw invalidColorSpace();
        }
    }

    private static void writeTransparencyState(
            WorkerCodecIO.Output output,
            CanvasTransparencyState state) throws IOException {
        output.writeInt(state.getVersion());
        output.writeDouble(state.getFillAlpha());
        output.writeDouble(state.getStrokeAlpha());
        output.writeString(state.getBlendMode().name());
    }

    private static CanvasTransparencyState readTransparencyState(
            WorkerCodecIO.Input input) throws DocumentFailure {
        WorkerCommandCodec.requireVersion(
                input.readInt(),
                CanvasTransparencyState.VERSION_1);
        return CanvasTransparencyState.version1(
                input.readDouble(),
                input.readDouble(),
                WorkerCommandCodec.enumValue(
                        CanvasBlendMode.class,
                        input.readString(),
                        "Canvas blend mode"));
    }

    private static void writeTransparencyGroup(
            WorkerCodecIO.Output output,
            CanvasTransparencyGroup group,
            WorkerReferenceRegistry references,
            int depth) throws IOException, DocumentFailure {
        output.writeInt(group.getVersion());
        CanvasRectangle box = group.getBox();
        output.writeDouble(box.getLowerLeftX());
        output.writeDouble(box.getLowerLeftY());
        output.writeDouble(box.getUpperRightX());
        output.writeDouble(box.getUpperRightY());
        writeColorSpace(output, group.getColorSpace());
        output.writeBoolean(group.isIsolated());
        output.writeBoolean(group.isKnockout());
        writeProgram(output, group.getProgram(), references, depth);
    }

    private static CanvasTransparencyGroup readTransparencyGroup(
            WorkerCodecIO.Input input,
            WorkerReferenceRegistry references,
            int depth) throws DocumentFailure {
        WorkerCommandCodec.requireVersion(
                input.readInt(),
                CanvasTransparencyGroup.VERSION_1);
        CanvasRectangle box = CanvasRectangle.of(
                input.readDouble(),
                input.readDouble(),
                input.readDouble(),
                input.readDouble());
        CanvasColorSpace colorSpace = readColorSpace(input);
        boolean isolated = input.readBoolean();
        boolean knockout = input.readBoolean();
        return CanvasTransparencyGroup.version1(
                box,
                colorSpace,
                isolated,
                knockout,
                readProgram(input, references, depth));
    }

    private static void writeResourceLimits(
            WorkerCodecIO.Output output,
            CanvasResourceLimits limits) throws IOException {
        output.writeInt(limits.getVersion());
        output.writeLong(limits.getMaximumEncodedImageBytes());
        output.writeLong(limits.getMaximumDecodedImagePixels());
        output.writeLong(limits.getMaximumDecodedImageBytes());
        output.writeLong(limits.getMaximumIccProfileBytes());
        output.writeLong(limits.getMaximumMaskBytes());
        output.writeLong(limits.getMaximumGeneratedContentBytes());
        output.writeInt(limits.getMaximumResourceDeclarations());
        output.writeInt(limits.getMaximumTransparencyGroupDepth());
    }

    private static CanvasResourceLimits readResourceLimits(
            WorkerCodecIO.Input input) throws DocumentFailure {
        WorkerCommandCodec.requireVersion(
                input.readInt(),
                CanvasResourceLimits.VERSION_1);
        return CanvasResourceLimits.builder()
                .maximumEncodedImageBytes(input.readLong())
                .maximumDecodedImagePixels(input.readLong())
                .maximumDecodedImageBytes(input.readLong())
                .maximumIccProfileBytes(input.readLong())
                .maximumMaskBytes(input.readLong())
                .maximumGeneratedContentBytes(input.readLong())
                .maximumResourceDeclarations(input.readInt())
                .maximumTransparencyGroupDepth(input.readInt())
                .build();
    }

    private static void writeFontSelection(
            WorkerCodecIO.Output output,
            FontSelection selection,
            FontLimits limits,
            WorkerFontSourceCache fontSources)
            throws IOException, DocumentFailure {
        List<FontSource> sources = selection.getKind()
                == FontSelection.Kind.REFERENCE_FONT_SET
                ? fontSources.getReferenceFonts()
                : selection.getSources();
        if (selection.getKind() == FontSelection.Kind.REFERENCE_FONT_SET
                && sources.isEmpty()) {
            output.writeString(FontSelection.Kind.REFERENCE_FONT_SET.name());
            output.writeInt(0);
            return;
        }
        output.writeString(FontSelection.Kind.EXPLICIT.name());
        output.writeInt(sources.size());
        output.requireCapacity(8L * sources.size());
        long identifier = fontSources.registerRemoteSelection(
                sources,
                limits.getMaximumSourceBytes());
        for (int index = 0; index < sources.size(); index++) {
            output.writeLong(identifier + index);
        }
    }

    private static FontSelection readFontSelection(
            WorkerCodecIO.Input input,
            RemoteFontSource remoteFonts)
            throws DocumentFailure {
        FontSelection.Kind kind = WorkerCommandCodec.enumValue(
                FontSelection.Kind.class,
                input.readString(),
                "Font Selection kind");
        int count = WorkerCommandCodec.readCount(input, "Font Source");
        if (kind == FontSelection.Kind.REFERENCE_FONT_SET) {
            if (count != 0) {
                throw WorkerCommandCodec.rejected(
                        "The Worker Reference Font Set selection is invalid.");
            }
            return FontSelection.referenceFontSet();
        }
        if (count == 0) {
            throw WorkerCommandCodec.rejected(
                    "The Worker explicit Font Selection is empty.");
        }
        if (remoteFonts == null) {
            throw WorkerCommandCodec.rejected(
                    "The Worker remote Font Source is unavailable.");
        }
        FontSource[] sources = new FontSource[count];
        for (int index = 0; index < count; index++) {
            long identifier = input.readLong();
            if (identifier < 1L) {
                throw WorkerCommandCodec.rejected(
                        "The Worker Font Source identifier is invalid.");
            }
            sources[index] = FontSource.stream(
                    remoteFonts.open(identifier));
        }
        return FontSelection.explicit(sources);
    }

    private static void writeFontLimits(
            WorkerCodecIO.Output output,
            FontLimits limits) throws IOException {
        output.writeInt(limits.getVersion());
        output.writeInt(limits.getMaximumFontSources());
        output.writeLong(limits.getMaximumSourceBytes());
        output.writeInt(limits.getMaximumCodePoints());
        output.writeLong(limits.getMaximumFallbackChecks());
        output.writeLong(limits.getMaximumGeneratedContentBytes());
    }

    private static FontLimits readFontLimits(WorkerCodecIO.Input input)
            throws DocumentFailure {
        WorkerCommandCodec.requireVersion(input.readInt(), FontLimits.VERSION_1);
        return FontLimits.builder()
                .maximumFontSources(input.readInt())
                .maximumSourceBytes(input.readLong())
                .maximumCodePoints(input.readInt())
                .maximumFallbackChecks(input.readLong())
                .maximumGeneratedContentBytes(input.readLong())
                .build();
    }

    private static void writeMatrix(
            WorkerCodecIO.Output output,
            CanvasMatrix matrix) throws IOException {
        output.writeDouble(matrix.getA());
        output.writeDouble(matrix.getB());
        output.writeDouble(matrix.getC());
        output.writeDouble(matrix.getD());
        output.writeDouble(matrix.getE());
        output.writeDouble(matrix.getF());
    }

    private static CanvasMatrix readMatrix(WorkerCodecIO.Input input)
            throws DocumentFailure {
        return CanvasMatrix.of(
                input.readDouble(),
                input.readDouble(),
                input.readDouble(),
                input.readDouble(),
                input.readDouble(),
                input.readDouble());
    }

    private static void writeDoubles(
            WorkerCodecIO.Output output,
            double[] values) throws IOException {
        output.writeInt(values.length);
        for (double value : values) {
            output.writeDouble(value);
        }
    }

    private static double[] readDoubles(
            WorkerCodecIO.Input input,
            int expected) throws DocumentFailure {
        int count = WorkerCommandCodec.readCount(input, "numeric operand");
        if (expected >= 0 && count != expected) {
            throw WorkerCommandCodec.rejected(
                    "The Worker numeric operand count is invalid.");
        }
        double[] values = new double[count];
        for (int index = 0; index < count; index++) {
            values[index] = input.readDouble();
        }
        return values;
    }

    private static CanvasWindingRule readWindingRule(
            WorkerCodecIO.Input input) throws DocumentFailure {
        return WorkerCommandCodec.enumValue(
                CanvasWindingRule.class,
                input.readString(),
                "Canvas winding rule");
    }

    private static TextRenderingMode readRenderingMode(
            WorkerCodecIO.Input input) throws DocumentFailure {
        return WorkerCommandCodec.enumValue(
                TextRenderingMode.class,
                input.readString(),
                "text rendering mode");
    }

    private static void requireEmptyColorData(
            double[] white,
            double[] black,
            double[] gamma,
            double[] matrix,
            byte[] profile) throws DocumentFailure {
        if (white.length != 0
                || black.length != 0
                || gamma.length != 0
                || matrix.length != 0
                || profile != null) {
            throw invalidColorSpace();
        }
    }

    private static DocumentFailure invalidColorSpace() {
        return WorkerCommandCodec.rejected(
                "The Worker Canvas Color Space is invalid.");
    }
}
