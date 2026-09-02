package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Detached image-resource metadata and explicitly selected bounded byte data.
 *
 * @since 0.1.0
 */
public final class ImageResource extends DocumentResource {

    /** Stable byte-availability outcomes. */
    public enum ByteAvailability {
        /** The source and version-1 path support this representation. */ AVAILABLE,
        /** At least one declared filter is outside bounded version-1 decoding. */
        UNSUPPORTED_FILTER,
        /** The stream delegates content to an external file, which is never resolved. */
        EXTERNAL_STREAM
    }

    /** Color metadata classifications. */
    public enum ColorStatus {
        /** The declared color family is recognized and structurally valid. */ SUPPORTED,
        /**
         * The family is recognized, but version 1 does not establish complete
         * structural validity or interpretation for the declared graph.
         */
        UNSUPPORTED,
        /** The graph is missing, cyclic, or structurally invalid. */ MALFORMED
    }

    /** Recognized PDF color-space families. */
    public enum ColorFamily {
        NONE,
        DEVICE_GRAY,
        DEVICE_RGB,
        DEVICE_CMYK,
        CAL_GRAY,
        CAL_RGB,
        LAB,
        ICC_BASED,
        INDEXED,
        SEPARATION,
        DEVICE_N,
        UNKNOWN
    }

    /** Per-filter bounded decode support. */
    public enum DecodeSupport {
        SUPPORTED,
        UNSUPPORTED
    }

    /** Validated soft-mask information declared inside image sample data. */
    public enum EmbeddedSoftMask {
        /** No embedded soft mask is declared. */ NONE,
        /** The sample data declares an embedded soft-mask channel. */ SOFT_MASK,
        /** Color channels are preblended and an opacity channel is embedded. */
        PREBLENDED_SOFT_MASK
    }

    private final int width;
    private final int height;
    private final Integer bitsPerComponent;
    private final Integer colorComponents;
    private final boolean imageMask;
    private final EmbeddedSoftMask embeddedSoftMask;
    private final ColorSpace colorSpace;
    private final List<Filter> filters;
    private final Mask explicitMask;
    private final Mask softMask;
    private final ByteData encodedData;
    private final ByteData decodedData;

    ImageResource(
            ObjectReference objectReference,
            List<ResourceDeclaration> declarations,
            List<Integer> pageUsage,
            int width,
            int height,
            Integer bitsPerComponent,
            Integer colorComponents,
            boolean imageMask,
            EmbeddedSoftMask embeddedSoftMask,
            ColorSpace colorSpace,
            List<Filter> filters,
            Mask explicitMask,
            Mask softMask,
            ByteData encodedData,
            ByteData decodedData) {
        super(Kind.IMAGE, objectReference, declarations, pageUsage);
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Image dimensions must be positive");
        }
        this.width = width;
        this.height = height;
        this.bitsPerComponent = bitsPerComponent;
        this.colorComponents = colorComponents;
        this.imageMask = imageMask;
        this.embeddedSoftMask = Objects.requireNonNull(
                embeddedSoftMask,
                "embeddedSoftMask");
        this.colorSpace = Objects.requireNonNull(colorSpace, "colorSpace");
        this.filters = Collections.unmodifiableList(
                new ArrayList<Filter>(Objects.requireNonNull(filters, "filters")));
        this.explicitMask = explicitMask;
        this.softMask = softMask;
        if (this.embeddedSoftMask != EmbeddedSoftMask.NONE
                && (imageMask || softMask != null)) {
            throw new IllegalArgumentException(
                    "Embedded soft masks conflict with image or subsidiary masks");
        }
        this.encodedData = Objects.requireNonNull(encodedData, "encodedData");
        this.decodedData = Objects.requireNonNull(decodedData, "decodedData");
    }

    /** Returns the exact declared width. @return width */
    public int getWidth() { return width; }

    /** Returns the exact declared height. @return height */
    public int getHeight() { return height; }

    /** Returns declared bits per component when available. @return value */
    public OptionalInt getBitsPerComponent() {
        return bitsPerComponent == null
                ? OptionalInt.empty()
                : OptionalInt.of(bitsPerComponent.intValue());
    }

    /** Returns the resolved color-component count when available. @return value */
    public OptionalInt getColorComponents() {
        return colorComponents == null
                ? OptionalInt.empty()
                : OptionalInt.of(colorComponents.intValue());
    }

    /** Returns whether this stream is itself an Image Mask. @return value */
    public boolean isImageMask() { return imageMask; }

    /**
     * Returns the validated SMaskInData declaration. Non-NONE values describe
     * embedded JPX sample information; the entry is meaningless for other
     * filter sequences.
     * @return embedded soft-mask declaration
     */
    public EmbeddedSoftMask getEmbeddedSoftMask() { return embeddedSoftMask; }

    /** Returns classified color information. @return color information */
    public ColorSpace getColorSpace() { return colorSpace; }

    /** Returns the exact declared filter sequence. @return filters */
    public List<Filter> getFilters() { return filters; }

    /** Returns the explicit image or color-key mask. @return optional mask */
    public Optional<Mask> getExplicitMask() { return Optional.ofNullable(explicitMask); }

    /** Returns the soft-mask image relationship. @return optional mask */
    public Optional<Mask> getSoftMask() { return Optional.ofNullable(softMask); }

    /** Returns encoded selection, availability, and detached bytes. @return data */
    public ByteData getEncodedData() { return encodedData; }

    /** Returns decoded selection, availability, and detached bytes. @return data */
    public ByteData getDecodedData() { return decodedData; }

    /** Detached classified image color information. */
    public static final class ColorSpace {

        private final ColorStatus status;
        private final ColorFamily family;
        private final PdfName declaredName;
        private final PdfName resolvedFamilyName;
        private final Integer components;

        ColorSpace(
                ColorStatus status,
                ColorFamily family,
                PdfName declaredName,
                PdfName resolvedFamilyName,
                Integer components) {
            this.status = Objects.requireNonNull(status, "status");
            this.family = Objects.requireNonNull(family, "family");
            this.declaredName = declaredName;
            this.resolvedFamilyName = resolvedFamilyName;
            this.components = components;
        }

        /** Returns supported, unsupported, or malformed classification. @return status */
        public ColorStatus getStatus() { return status; }

        /** Returns the recognized family or UNKNOWN. @return family */
        public ColorFamily getFamily() { return family; }

        /** Returns the name declared by the image when present. @return name */
        public Optional<PdfName> getDeclaredName() {
            return Optional.ofNullable(declaredName);
        }

        /** Returns the resolved family name when present. @return name */
        public Optional<PdfName> getResolvedFamilyName() {
            return Optional.ofNullable(resolvedFamilyName);
        }

        /** Returns the proven component count when available. @return count */
        public OptionalInt getComponents() {
            return components == null
                    ? OptionalInt.empty()
                    : OptionalInt.of(components.intValue());
        }
    }

    /** One declared image filter and supported effective decode metadata. */
    public static final class Filter {

        private final PdfName name;
        private final DecodeSupport decodeSupport;
        private final Integer predictor;
        private final Integer colors;
        private final Integer bitsPerComponent;
        private final Integer columns;
        private final Integer earlyChange;

        Filter(
                PdfName name,
                DecodeSupport decodeSupport,
                Integer predictor,
                Integer colors,
                Integer bitsPerComponent,
                Integer columns,
                Integer earlyChange) {
            this.name = Objects.requireNonNull(name, "name");
            this.decodeSupport = Objects.requireNonNull(
                    decodeSupport,
                    "decodeSupport");
            this.predictor = predictor;
            this.colors = colors;
            this.bitsPerComponent = bitsPerComponent;
            this.columns = columns;
            this.earlyChange = earlyChange;
        }

        /** Returns the exact declared filter name. @return name */
        public PdfName getName() { return name; }

        /** Returns version-1 bounded decoding support. @return support */
        public DecodeSupport getDecodeSupport() { return decodeSupport; }

        /** Returns the effective Predictor parameter when applicable. @return value */
        public OptionalInt getPredictor() { return optional(predictor); }

        /** Returns the effective Colors parameter when applicable. @return value */
        public OptionalInt getColors() { return optional(colors); }

        /** Returns the effective BitsPerComponent parameter. @return value */
        public OptionalInt getBitsPerComponent() {
            return optional(bitsPerComponent);
        }

        /** Returns the effective Columns parameter when applicable. @return value */
        public OptionalInt getColumns() { return optional(columns); }

        /** Returns the effective EarlyChange parameter when applicable. @return value */
        public OptionalInt getEarlyChange() { return optional(earlyChange); }

        private static OptionalInt optional(Integer value) {
            return value == null
                    ? OptionalInt.empty()
                    : OptionalInt.of(value.intValue());
        }
    }

    /** Selection, availability, and an optional defensive byte copy. */
    public static final class ByteData {

        private final boolean selected;
        private final ByteAvailability availability;
        private final byte[] bytes;

        ByteData(
                boolean selected,
                ByteAvailability availability,
                byte[] bytes) {
            this.selected = selected;
            this.availability = Objects.requireNonNull(
                    availability,
                    "availability");
            this.bytes = bytes == null ? null : bytes.clone();
            if (this.bytes != null
                    && (!selected || availability != ByteAvailability.AVAILABLE)) {
                throw new IllegalArgumentException(
                        "Bytes require selected available data");
            }
        }

        /** Returns whether the query selected this representation. @return selection */
        public boolean isSelected() { return selected; }

        /** Returns source and version-1 availability. @return availability */
        public ByteAvailability getAvailability() { return availability; }

        /** Returns a fresh detached copy when selected and available. @return bytes */
        public Optional<byte[]> getBytes() {
            return bytes == null
                    ? Optional.<byte[]>empty()
                    : Optional.of(bytes.clone());
        }
    }

    /** An explicit color-key or stable image-record mask relationship. */
    public static final class Mask {

        /** Mask relationship kinds. */
        public enum Kind {
            COLOR_KEY,
            EXPLICIT_IMAGE,
            SOFT_IMAGE
        }

        private final Kind kind;
        private final ImageResource image;
        private final List<PdfNumber> colorKeyRanges;

        Mask(Kind kind, ImageResource image, List<PdfNumber> colorKeyRanges) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.image = image;
            this.colorKeyRanges = Collections.unmodifiableList(
                    new ArrayList<PdfNumber>(Objects.requireNonNull(
                            colorKeyRanges,
                            "colorKeyRanges")));
        }

        /** Returns the relationship kind. @return kind */
        public Kind getKind() { return kind; }

        /** Returns the detached target record for image masks. @return target */
        public Optional<ImageResource> getImage() { return Optional.ofNullable(image); }

        /** Returns ordered low/high color-key values. @return values */
        public List<PdfNumber> getColorKeyRanges() { return colorKeyRanges; }
    }
}
