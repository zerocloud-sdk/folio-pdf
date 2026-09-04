package net.zerocloud.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/** Explicit value codec for detached document-resource inventories. */
final class WorkerResourceInventoryCodec {

    private static final int GENERIC = 1;
    private static final int IMAGE = 2;
    private static final int FONT = 3;

    private WorkerResourceInventoryCodec() {
    }

    static void write(
            WorkerCodecIO.Output output,
            DocumentResourceInventory inventory,
            WorkerReferenceRegistry references)
            throws IOException, DocumentFailure {
        List<DocumentResource> resources = inventory.getResources();
        IdentityHashMap<ImageResource, Integer> imageIndexes =
                new IdentityHashMap<ImageResource, Integer>();
        for (int index = 0; index < resources.size(); index++) {
            if (resources.get(index) instanceof ImageResource) {
                imageIndexes.put(
                        (ImageResource) resources.get(index),
                        Integer.valueOf(index));
            }
        }
        output.writeInt(resources.size());
        for (DocumentResource resource : resources) {
            if (resource instanceof ImageResource) {
                output.writeInt(IMAGE);
            } else if (resource instanceof FontResource) {
                output.writeInt(FONT);
            } else {
                output.writeInt(GENERIC);
            }
            output.writeString(resource.getKind().name());
            writeOptionalReference(
                    output,
                    resource.getObjectReference().orElse(null),
                    references);
            writeDeclarations(output, resource.getDeclarations());
            writeIntegers(output, resource.getPageUsage());
            if (resource instanceof ImageResource) {
                writeImage(
                        output,
                        (ImageResource) resource,
                        imageIndexes,
                        references);
            } else if (resource instanceof FontResource) {
                writeFont(output, (FontResource) resource);
            }
        }
    }

    static DocumentResourceInventory read(
            WorkerCodecIO.Input input,
            WorkerReferenceRegistry references) throws DocumentFailure {
        int count = WorkerCommandCodec.readCount(input, "resource result");
        List<ResourceValue> encoded = new ArrayList<ResourceValue>(count);
        for (int index = 0; index < count; index++) {
            int type = input.readInt();
            if (type != GENERIC && type != IMAGE && type != FONT) {
                throw WorkerCommandCodec.rejected(
                        "The Worker resource type is unsupported.");
            }
            DocumentResource.Kind kind = WorkerCommandCodec.enumValue(
                    DocumentResource.Kind.class,
                    input.readString(),
                    "document resource kind");
            ObjectReference reference = readOptionalReference(input, references);
            List<ResourceDeclaration> declarations = readDeclarations(input);
            List<Integer> pages = readIntegers(input, "resource page usage");
            ImageValue image = type == IMAGE ? readImage(input, references) : null;
            FontValue font = type == FONT ? readFont(input) : null;
            encoded.add(new ResourceValue(
                    type,
                    kind,
                    reference,
                    declarations,
                    pages,
                    image,
                    font));
        }
        List<DocumentResource> result = new ArrayList<DocumentResource>(count);
        DocumentResource[] built = new DocumentResource[count];
        boolean[] building = new boolean[count];
        for (int index = 0; index < count; index++) {
            result.add(build(index, encoded, built, building));
        }
        return new DocumentResourceInventory(result);
    }

    private static DocumentResource build(
            int index,
            List<ResourceValue> encoded,
            DocumentResource[] built,
            boolean[] building) throws DocumentFailure {
        if (index < 0 || index >= encoded.size()) {
            throw WorkerCommandCodec.rejected(
                    "The Worker image-mask relationship is invalid.");
        }
        if (built[index] != null) {
            return built[index];
        }
        if (building[index]) {
            throw WorkerCommandCodec.rejected(
                    "The Worker resource graph is cyclic.");
        }
        building[index] = true;
        try {
            ResourceValue value = encoded.get(index);
            DocumentResource resource;
            if (value.type == IMAGE) {
                if (value.kind != DocumentResource.Kind.IMAGE) {
                    throw WorkerCommandCodec.rejected(
                            "The Worker image resource kind is invalid.");
                }
                ImageValue image = value.image;
                resource = new ImageResource(
                        value.reference,
                        value.declarations,
                        value.pages,
                        image.width,
                        image.height,
                        image.bits,
                        image.components,
                        image.imageMask,
                        image.embeddedSoftMask,
                        image.colorSpace,
                        image.filters,
                        buildMask(image.explicitMask, encoded, built, building),
                        buildMask(image.softMask, encoded, built, building),
                        image.encodedData,
                        image.decodedData);
            } else if (value.type == FONT) {
                if (value.kind != DocumentResource.Kind.FONT) {
                    throw WorkerCommandCodec.rejected(
                            "The Worker font resource kind is invalid.");
                }
                FontValue font = value.font;
                resource = new FontResource(
                        value.reference,
                        value.declarations,
                        value.pages,
                        font.kind,
                        font.status,
                        font.embedding,
                        font.baseFont,
                        font.subsetPrefix);
            } else {
                resource = new DocumentResource(
                        value.kind,
                        value.reference,
                        value.declarations,
                        value.pages);
            }
            built[index] = resource;
            return resource;
        } catch (IllegalArgumentException failure) {
            throw WorkerCommandCodec.rejected(
                    "A Worker resource result is invalid.");
        } finally {
            building[index] = false;
        }
    }

    private static ImageResource.Mask buildMask(
            MaskValue value,
            List<ResourceValue> encoded,
            DocumentResource[] built,
            boolean[] building) throws DocumentFailure {
        if (value == null) {
            return null;
        }
        ImageResource image = null;
        if (value.imageIndex != null) {
            DocumentResource target = build(
                    value.imageIndex.intValue(),
                    encoded,
                    built,
                    building);
            if (!(target instanceof ImageResource)) {
                throw WorkerCommandCodec.rejected(
                        "The Worker image-mask target is invalid.");
            }
            image = (ImageResource) target;
        }
        return new ImageResource.Mask(value.kind, image, value.ranges);
    }

    private static void writeImage(
            WorkerCodecIO.Output output,
            ImageResource image,
            IdentityHashMap<ImageResource, Integer> imageIndexes,
            WorkerReferenceRegistry references)
            throws IOException, DocumentFailure {
        output.writeInt(image.getWidth());
        output.writeInt(image.getHeight());
        writeOptionalInt(output, image.getBitsPerComponent().isPresent()
                ? Integer.valueOf(image.getBitsPerComponent().getAsInt()) : null);
        writeOptionalInt(output, image.getColorComponents().isPresent()
                ? Integer.valueOf(image.getColorComponents().getAsInt()) : null);
        output.writeBoolean(image.isImageMask());
        output.writeString(image.getEmbeddedSoftMask().name());
        writeColorSpace(output, image.getColorSpace(), references);
        output.writeInt(image.getFilters().size());
        for (ImageResource.Filter filter : image.getFilters()) {
            output.writeString(filter.getName().getValue());
            output.writeString(filter.getDecodeSupport().name());
            writeOptionalInt(output, optionalInt(filter.getPredictor()));
            writeOptionalInt(output, optionalInt(filter.getColors()));
            writeOptionalInt(output, optionalInt(filter.getBitsPerComponent()));
            writeOptionalInt(output, optionalInt(filter.getColumns()));
            writeOptionalInt(output, optionalInt(filter.getEarlyChange()));
        }
        writeMask(output, image.getExplicitMask().orElse(null), imageIndexes);
        writeMask(output, image.getSoftMask().orElse(null), imageIndexes);
        writeByteData(output, image.getEncodedData());
        writeByteData(output, image.getDecodedData());
    }

    private static ImageValue readImage(
            WorkerCodecIO.Input input,
            WorkerReferenceRegistry references) throws DocumentFailure {
        int width = input.readInt();
        int height = input.readInt();
        Integer bits = readOptionalInt(input);
        Integer components = readOptionalInt(input);
        boolean imageMask = input.readBoolean();
        ImageResource.EmbeddedSoftMask embeddedSoftMask =
                WorkerCommandCodec.enumValue(
                        ImageResource.EmbeddedSoftMask.class,
                        input.readString(),
                        "embedded soft mask");
        ImageResource.ColorSpace colorSpace = readColorSpace(input, references);
        int filterCount = WorkerCommandCodec.readCount(input, "image filter result");
        List<ImageResource.Filter> filters =
                new ArrayList<ImageResource.Filter>(filterCount);
        for (int index = 0; index < filterCount; index++) {
            filters.add(new ImageResource.Filter(
                    PdfName.of(input.readString()),
                    WorkerCommandCodec.enumValue(
                            ImageResource.DecodeSupport.class,
                            input.readString(),
                            "image filter decode support"),
                    readOptionalInt(input),
                    readOptionalInt(input),
                    readOptionalInt(input),
                    readOptionalInt(input),
                    readOptionalInt(input)));
        }
        return new ImageValue(
                width,
                height,
                bits,
                components,
                imageMask,
                embeddedSoftMask,
                colorSpace,
                filters,
                readMask(input),
                readMask(input),
                readByteData(input),
                readByteData(input));
    }

    private static void writeColorSpace(
            WorkerCodecIO.Output output,
            ImageResource.ColorSpace value,
            WorkerReferenceRegistry references)
            throws IOException, DocumentFailure {
        output.writeString(value.getStatus().name());
        output.writeString(value.getFamily().name());
        writeOptionalName(output, value.getDeclaredName().orElse(null));
        writeOptionalName(output, value.getResolvedFamilyName().orElse(null));
        writeOptionalInt(output, value.getComponents().isPresent()
                ? Integer.valueOf(value.getComponents().getAsInt()) : null);
        output.writeBoolean(value.getIccProfile().isPresent());
        if (value.getIccProfile().isPresent()) {
            ImageResource.IccProfile profile = value.getIccProfile().get();
            writeOptionalReference(
                    output,
                    profile.getObjectReference().orElse(null),
                    references);
            output.writeLong(profile.getByteLength());
            output.writeString(profile.getSha256());
        }
    }

    private static ImageResource.ColorSpace readColorSpace(
            WorkerCodecIO.Input input,
            WorkerReferenceRegistry references) throws DocumentFailure {
        ImageResource.ColorStatus status = WorkerCommandCodec.enumValue(
                ImageResource.ColorStatus.class,
                input.readString(),
                "image color status");
        ImageResource.ColorFamily family = WorkerCommandCodec.enumValue(
                ImageResource.ColorFamily.class,
                input.readString(),
                "image color family");
        PdfName declared = readOptionalName(input);
        PdfName resolved = readOptionalName(input);
        Integer components = readOptionalInt(input);
        ImageResource.IccProfile profile = null;
        if (input.readBoolean()) {
            profile = new ImageResource.IccProfile(
                    readOptionalReference(input, references),
                    input.readLong(),
                    input.readString());
        }
        return new ImageResource.ColorSpace(
                status,
                family,
                declared,
                resolved,
                components,
                profile);
    }

    private static void writeMask(
            WorkerCodecIO.Output output,
            ImageResource.Mask value,
            IdentityHashMap<ImageResource, Integer> imageIndexes)
            throws IOException, DocumentFailure {
        output.writeBoolean(value != null);
        if (value == null) {
            return;
        }
        output.writeString(value.getKind().name());
        output.writeBoolean(value.getImage().isPresent());
        if (value.getImage().isPresent()) {
            Integer target = imageIndexes.get(value.getImage().get());
            if (target == null) {
                throw WorkerCommandCodec.rejected(
                        "The Worker image-mask target is unavailable.");
            }
            output.writeInt(target.intValue());
        }
        output.writeInt(value.getColorKeyRanges().size());
        for (PdfNumber range : value.getColorKeyRanges()) {
            output.writeBigDecimal(range.decimalValue());
        }
    }

    private static MaskValue readMask(WorkerCodecIO.Input input)
            throws DocumentFailure {
        if (!input.readBoolean()) {
            return null;
        }
        ImageResource.Mask.Kind kind = WorkerCommandCodec.enumValue(
                ImageResource.Mask.Kind.class,
                input.readString(),
                "image mask kind");
        Integer imageIndex = input.readBoolean()
                ? Integer.valueOf(input.readInt()) : null;
        int count = WorkerCommandCodec.readCount(input, "color-key range");
        List<PdfNumber> ranges = new ArrayList<PdfNumber>(count);
        for (int index = 0; index < count; index++) {
            ranges.add(PdfNumber.of(input.readBigDecimal()));
        }
        return new MaskValue(kind, imageIndex, ranges);
    }

    private static void writeByteData(
            WorkerCodecIO.Output output,
            ImageResource.ByteData value) throws IOException {
        output.writeBoolean(value.isSelected());
        output.writeString(value.getAvailability().name());
        output.writeNullableBytes(value.bytesForWorkflow());
    }

    private static ImageResource.ByteData readByteData(
            WorkerCodecIO.Input input) throws DocumentFailure {
        return new ImageResource.ByteData(
                input.readBoolean(),
                WorkerCommandCodec.enumValue(
                        ImageResource.ByteAvailability.class,
                        input.readString(),
                        "image byte availability"),
                input.readNullableBytes());
    }

    private static void writeFont(
            WorkerCodecIO.Output output,
            FontResource font) throws IOException {
        output.writeString(font.getFontKind().name());
        output.writeString(font.getStatus().name());
        output.writeString(font.getEmbedding().name());
        writeOptionalName(output, font.getBaseFontName().orElse(null));
        output.writeNullableString(font.getSubsetPrefix().orElse(null));
    }

    private static FontValue readFont(WorkerCodecIO.Input input)
            throws DocumentFailure {
        return new FontValue(
                WorkerCommandCodec.enumValue(
                        FontResource.FontKind.class,
                        input.readString(),
                        "font resource kind"),
                WorkerCommandCodec.enumValue(
                        FontResource.Status.class,
                        input.readString(),
                        "font resource status"),
                WorkerCommandCodec.enumValue(
                        FontResource.Embedding.class,
                        input.readString(),
                        "font embedding status"),
                readOptionalName(input),
                input.readNullableString());
    }

    private static void writeDeclarations(
            WorkerCodecIO.Output output,
            List<ResourceDeclaration> declarations) throws IOException {
        output.writeInt(declarations.size());
        for (ResourceDeclaration declaration : declarations) {
            output.writeInt(declaration.getPageNumber());
            output.writeInt(declaration.getPath().size());
            for (ResourceDeclaration.Segment segment : declaration.getPath()) {
                output.writeString(segment.getCategory().getValue());
                output.writeString(segment.getName().getValue());
            }
        }
    }

    private static List<ResourceDeclaration> readDeclarations(
            WorkerCodecIO.Input input) throws DocumentFailure {
        int count = WorkerCommandCodec.readCount(input, "resource declaration");
        List<ResourceDeclaration> declarations =
                new ArrayList<ResourceDeclaration>(count);
        for (int index = 0; index < count; index++) {
            int page = input.readInt();
            int segmentCount = WorkerCommandCodec.readCount(
                    input,
                    "resource declaration segment");
            List<ResourceDeclaration.Segment> path =
                    new ArrayList<ResourceDeclaration.Segment>(segmentCount);
            for (int segment = 0; segment < segmentCount; segment++) {
                path.add(new ResourceDeclaration.Segment(
                        PdfName.of(input.readString()),
                        PdfName.of(input.readString())));
            }
            declarations.add(new ResourceDeclaration(page, path));
        }
        return declarations;
    }

    private static void writeOptionalReference(
            WorkerCodecIO.Output output,
            ObjectReference reference,
            WorkerReferenceRegistry references)
            throws IOException, DocumentFailure {
        output.writeBoolean(reference != null);
        if (reference != null) {
            references.write(output, reference);
        }
    }

    private static ObjectReference readOptionalReference(
            WorkerCodecIO.Input input,
            WorkerReferenceRegistry references) throws DocumentFailure {
        return input.readBoolean() ? references.read(input) : null;
    }

    private static void writeOptionalName(
            WorkerCodecIO.Output output,
            PdfName value) throws IOException {
        output.writeNullableString(value == null ? null : value.getValue());
    }

    private static PdfName readOptionalName(WorkerCodecIO.Input input)
            throws DocumentFailure {
        String value = input.readNullableString();
        return value == null ? null : PdfName.of(value);
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

    private static void writeOptionalInt(
            WorkerCodecIO.Output output,
            Integer value) throws IOException {
        output.writeBoolean(value != null);
        if (value != null) {
            output.writeInt(value.intValue());
        }
    }

    private static Integer readOptionalInt(WorkerCodecIO.Input input)
            throws DocumentFailure {
        return input.readBoolean() ? Integer.valueOf(input.readInt()) : null;
    }

    private static Integer optionalInt(java.util.OptionalInt value) {
        return value.isPresent() ? Integer.valueOf(value.getAsInt()) : null;
    }

    private static final class ResourceValue {
        private final int type;
        private final DocumentResource.Kind kind;
        private final ObjectReference reference;
        private final List<ResourceDeclaration> declarations;
        private final List<Integer> pages;
        private final ImageValue image;
        private final FontValue font;

        private ResourceValue(
                int type,
                DocumentResource.Kind kind,
                ObjectReference reference,
                List<ResourceDeclaration> declarations,
                List<Integer> pages,
                ImageValue image,
                FontValue font) {
            this.type = type;
            this.kind = kind;
            this.reference = reference;
            this.declarations = declarations;
            this.pages = pages;
            this.image = image;
            this.font = font;
        }
    }

    private static final class ImageValue {
        private final int width;
        private final int height;
        private final Integer bits;
        private final Integer components;
        private final boolean imageMask;
        private final ImageResource.EmbeddedSoftMask embeddedSoftMask;
        private final ImageResource.ColorSpace colorSpace;
        private final List<ImageResource.Filter> filters;
        private final MaskValue explicitMask;
        private final MaskValue softMask;
        private final ImageResource.ByteData encodedData;
        private final ImageResource.ByteData decodedData;

        private ImageValue(
                int width,
                int height,
                Integer bits,
                Integer components,
                boolean imageMask,
                ImageResource.EmbeddedSoftMask embeddedSoftMask,
                ImageResource.ColorSpace colorSpace,
                List<ImageResource.Filter> filters,
                MaskValue explicitMask,
                MaskValue softMask,
                ImageResource.ByteData encodedData,
                ImageResource.ByteData decodedData) {
            this.width = width;
            this.height = height;
            this.bits = bits;
            this.components = components;
            this.imageMask = imageMask;
            this.embeddedSoftMask = embeddedSoftMask;
            this.colorSpace = colorSpace;
            this.filters = filters;
            this.explicitMask = explicitMask;
            this.softMask = softMask;
            this.encodedData = encodedData;
            this.decodedData = decodedData;
        }
    }

    private static final class MaskValue {
        private final ImageResource.Mask.Kind kind;
        private final Integer imageIndex;
        private final List<PdfNumber> ranges;

        private MaskValue(
                ImageResource.Mask.Kind kind,
                Integer imageIndex,
                List<PdfNumber> ranges) {
            this.kind = kind;
            this.imageIndex = imageIndex;
            this.ranges = ranges;
        }
    }

    private static final class FontValue {
        private final FontResource.FontKind kind;
        private final FontResource.Status status;
        private final FontResource.Embedding embedding;
        private final PdfName baseFont;
        private final String subsetPrefix;

        private FontValue(
                FontResource.FontKind kind,
                FontResource.Status status,
                FontResource.Embedding embedding,
                PdfName baseFont,
                String subsetPrefix) {
            this.kind = kind;
            this.status = status;
            this.embedding = embedding;
            this.baseFont = baseFont;
            this.subsetPrefix = subsetPrefix;
        }
    }
}
