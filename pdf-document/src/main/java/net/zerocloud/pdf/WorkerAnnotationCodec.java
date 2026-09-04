package net.zerocloud.pdf;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Whitelisted codecs for annotation and inert local-Action values. */
final class WorkerAnnotationCodec {

    private WorkerAnnotationCodec() {
    }

    static void writeAnnotation(
            WorkerCodecIO.Output output,
            Annotation annotation) throws IOException, DocumentFailure {
        output.writeString(annotation.getType().name());
        writeProperties(output, annotation.getProperties());
        switch (annotation.getType()) {
            case TEXT:
                output.writeString(annotation.getTextIcon().get().name());
                output.writeBoolean(annotation.isOpen());
                break;
            case STAMP:
                output.writeString(annotation.getStampName().get());
                break;
            case HIGHLIGHT:
                output.writeInt(annotation.getQuads().size());
                for (AnnotationQuad quad : annotation.getQuads()) {
                    writeQuad(output, quad);
                }
                writeColor(output, annotation.getColor().get());
                break;
            case FILE_ATTACHMENT:
                WorkerCommandCodec.writeEmbeddedFile(
                        output,
                        annotation.getAttachment().get());
                output.writeString(
                        annotation.getFileAttachmentIcon().get().name());
                break;
            case WIDGET:
                break;
            case LINK:
                writeLinkActivation(
                        output,
                        annotation.getLinkActivation().get());
                break;
            default:
                throw WorkerCommandCodec.rejected(
                        "The Worker annotation type is unsupported.");
        }
    }

    static Annotation readAnnotation(WorkerCodecIO.Input input)
            throws DocumentFailure {
        Annotation.Type type = WorkerCommandCodec.enumValue(
                Annotation.Type.class,
                input.readString(),
                "annotation type");
        AnnotationProperties properties = readProperties(input);
        switch (type) {
            case TEXT:
                return Annotation.text(
                        properties,
                        WorkerCommandCodec.enumValue(
                                Annotation.TextIcon.class,
                                input.readString(),
                                "Text annotation icon"),
                        input.readBoolean());
            case STAMP:
                return Annotation.stamp(properties, input.readString());
            case HIGHLIGHT:
                int count = WorkerCommandCodec.readCount(
                        input,
                        "annotation quadrilateral");
                List<AnnotationQuad> quads =
                        new ArrayList<AnnotationQuad>(count);
                for (int index = 0; index < count; index++) {
                    quads.add(readQuad(input));
                }
                return Annotation.highlight(properties, quads, readColor(input));
            case FILE_ATTACHMENT:
                return Annotation.fileAttachment(
                        properties,
                        WorkerCommandCodec.readEmbeddedFile(input),
                        WorkerCommandCodec.enumValue(
                                Annotation.FileAttachmentIcon.class,
                                input.readString(),
                                "file-attachment icon"));
            case WIDGET:
                return Annotation.widget(properties);
            case LINK:
                return Annotation.link(properties, readLinkActivation(input));
            default:
                throw WorkerCommandCodec.rejected(
                        "The Worker annotation type is unsupported.");
        }
    }

    private static void writeProperties(
            WorkerCodecIO.Output output,
            AnnotationProperties properties) throws IOException {
        output.writeInt(properties.getVersion());
        output.writeString(properties.getIdentifier());
        output.writeInt(properties.getPageNumber());
        writeRectangle(output, properties.getRectangle());
        output.writeNullableString(properties.getContents().orElse(null));
        output.writeInt(properties.getFlags().size());
        for (AnnotationFlag flag : properties.getFlags()) {
            output.writeString(flag.name());
        }
        output.writeBoolean(properties.getAppearance().isPresent());
        if (properties.getAppearance().isPresent()) {
            AnnotationAppearance appearance = properties.getAppearance().get();
            output.writeInt(appearance.getVersion());
            writeRectangle(output, appearance.getBoundingBox());
            output.writeBytes(appearance.contentForWorkflow());
        }
    }

    private static AnnotationProperties readProperties(
            WorkerCodecIO.Input input) throws DocumentFailure {
        WorkerCommandCodec.requireVersion(
                input.readInt(),
                AnnotationProperties.VERSION_1);
        AnnotationProperties.Builder builder = AnnotationProperties.version1(
                input.readString(),
                input.readInt(),
                readRectangle(input));
        String contents = input.readNullableString();
        if (contents != null) {
            builder.contents(contents);
        }
        int flagCount = WorkerCommandCodec.readCount(
                input,
                "annotation flag");
        for (int index = 0; index < flagCount; index++) {
            builder.flag(WorkerCommandCodec.enumValue(
                    AnnotationFlag.class,
                    input.readString(),
                    "annotation flag"));
        }
        if (input.readBoolean()) {
            WorkerCommandCodec.requireVersion(
                    input.readInt(),
                    AnnotationAppearance.VERSION_1);
            builder.appearance(AnnotationAppearance.fromOwnedContent(
                    readRectangle(input),
                    input.readBytes()));
        }
        return builder.build();
    }

    private static void writeRectangle(
            WorkerCodecIO.Output output,
            AnnotationRectangle rectangle) throws IOException {
        output.writeBigDecimal(rectangle.getLeft());
        output.writeBigDecimal(rectangle.getBottom());
        output.writeBigDecimal(rectangle.getRight());
        output.writeBigDecimal(rectangle.getTop());
    }

    private static AnnotationRectangle readRectangle(
            WorkerCodecIO.Input input) throws DocumentFailure {
        return AnnotationRectangle.of(
                input.readBigDecimal(),
                input.readBigDecimal(),
                input.readBigDecimal(),
                input.readBigDecimal());
    }

    private static void writeQuad(
            WorkerCodecIO.Output output,
            AnnotationQuad quad) throws IOException {
        for (BigDecimal coordinate : quad.getCoordinates()) {
            output.writeBigDecimal(coordinate);
        }
    }

    private static AnnotationQuad readQuad(WorkerCodecIO.Input input)
            throws DocumentFailure {
        return AnnotationQuad.of(
                input.readBigDecimal(), input.readBigDecimal(),
                input.readBigDecimal(), input.readBigDecimal(),
                input.readBigDecimal(), input.readBigDecimal(),
                input.readBigDecimal(), input.readBigDecimal());
    }

    private static void writeColor(
            WorkerCodecIO.Output output,
            AnnotationColor color) throws IOException {
        output.writeString(color.getSpace().name());
        output.writeInt(color.getComponents().size());
        for (BigDecimal component : color.getComponents()) {
            output.writeBigDecimal(component);
        }
    }

    private static AnnotationColor readColor(WorkerCodecIO.Input input)
            throws DocumentFailure {
        AnnotationColor.Space space = WorkerCommandCodec.enumValue(
                AnnotationColor.Space.class,
                input.readString(),
                "annotation color space");
        int count = WorkerCommandCodec.readCount(
                input,
                "annotation color component");
        List<BigDecimal> components = new ArrayList<BigDecimal>(count);
        for (int index = 0; index < count; index++) {
            components.add(input.readBigDecimal());
        }
        if (space == AnnotationColor.Space.GRAY && count == 1) {
            return AnnotationColor.gray(components.get(0));
        }
        if (space == AnnotationColor.Space.RGB && count == 3) {
            return AnnotationColor.rgb(
                    components.get(0),
                    components.get(1),
                    components.get(2));
        }
        if (space == AnnotationColor.Space.CMYK && count == 4) {
            return AnnotationColor.cmyk(
                    components.get(0),
                    components.get(1),
                    components.get(2),
                    components.get(3));
        }
        throw WorkerCommandCodec.rejected(
                "The Worker annotation color is invalid.");
    }

    private static void writeLinkActivation(
            WorkerCodecIO.Output output,
            LinkActivation activation) throws IOException {
        output.writeString(activation.getKind().name());
        WorkerCommandCodec.writeNavigationTarget(
                output,
                activation.getTarget());
    }

    private static LinkActivation readLinkActivation(
            WorkerCodecIO.Input input) throws DocumentFailure {
        LinkActivation.Kind kind = WorkerCommandCodec.enumValue(
                LinkActivation.Kind.class,
                input.readString(),
                "Link activation kind");
        NavigationTarget target = WorkerCommandCodec.readNavigationTarget(input);
        return kind == LinkActivation.Kind.DESTINATION
                ? LinkActivation.destination(target)
                : LinkActivation.action(GoToAction.version1(target));
    }
}
