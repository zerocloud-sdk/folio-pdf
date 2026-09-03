package net.zerocloud.pdf.composition;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import net.zerocloud.pdf.ObjectReference;

/**
 * An immutable encoded, raw-sample, or borrowed existing Canvas Image.
 *
 * <p>Encoded and sample inputs are defensively copied. Existing images borrow
 * only a same-Session {@link ObjectReference}; they never expose a live
 * backend object.</p>
 *
 * @since 0.1.0
 */
public final class CanvasImage {

    /** The currently supported declaration representation version. */
    public static final int VERSION_1 = 1;

    /** Closed input kinds and format policy keys. */
    public enum SourceKind {
        JPEG,
        PNG,
        TIFF,
        RAW_SAMPLES,
        EXISTING
    }

    private final SourceKind sourceKind;
    private final byte[] bytes;
    private final int width;
    private final int height;
    private final int bitsPerComponent;
    private final CanvasColorSpace colorSpace;
    private final ObjectReference objectReference;
    private final CanvasMask explicitMask;
    private final CanvasMask softMask;

    private CanvasImage(
            SourceKind sourceKind,
            byte[] bytes,
            int width,
            int height,
            int bitsPerComponent,
            CanvasColorSpace colorSpace,
            ObjectReference objectReference,
            CanvasMask explicitMask,
            CanvasMask softMask) {
        this.sourceKind = Objects.requireNonNull(sourceKind, "sourceKind");
        this.bytes = bytes == null ? null : bytes.clone();
        this.width = width;
        this.height = height;
        this.bitsPerComponent = bitsPerComponent;
        this.colorSpace = colorSpace;
        this.objectReference = objectReference;
        this.explicitMask = explicitMask;
        this.softMask = softMask;
    }

    /** Declares JPEG bytes for byte-preserving DCT embedding. */
    public static CanvasImage jpeg(byte[] encodedBytes) {
        return encoded(SourceKind.JPEG, encodedBytes);
    }

    /** Declares PNG bytes for bounded normalization to 8-bit DeviceRGB. */
    public static CanvasImage png(byte[] encodedBytes) {
        return encoded(SourceKind.PNG, encodedBytes);
    }

    /** Declares TIFF bytes for optional-codec normalization to 8-bit DeviceRGB. */
    public static CanvasImage tiff(byte[] encodedBytes) {
        return encoded(SourceKind.TIFF, encodedBytes);
    }

    /**
     * Declares packed row-major samples. Version 1 accepts eight-bit samples
     * and a supported Canvas Color Space.
     */
    public static CanvasImage rawSamples(
            int width,
            int height,
            int bitsPerComponent,
            CanvasColorSpace colorSpace,
            byte[] samples) {
        return new CanvasImage(
                SourceKind.RAW_SAMPLES,
                Objects.requireNonNull(samples, "samples"),
                width,
                height,
                bitsPerComponent,
                Objects.requireNonNull(colorSpace, "colorSpace"),
                null,
                null,
                null);
    }

    /** Declares an existing indirect Image Resource in the current Session. */
    public static CanvasImage existing(ObjectReference objectReference) {
        return new CanvasImage(
                SourceKind.EXISTING,
                null,
                0,
                0,
                0,
                null,
                Objects.requireNonNull(objectReference, "objectReference"),
                null,
                null);
    }

    /** Returns a copy with the supplied explicit Image Mask relationship. */
    public CanvasImage withExplicitMask(CanvasMask mask) {
        return new CanvasImage(
                sourceKind,
                bytes,
                width,
                height,
                bitsPerComponent,
                colorSpace,
                objectReference,
                Objects.requireNonNull(mask, "mask"),
                softMask);
    }

    /** Returns a copy with the supplied soft-mask relationship. */
    public CanvasImage withSoftMask(CanvasMask mask) {
        return new CanvasImage(
                sourceKind,
                bytes,
                width,
                height,
                bitsPerComponent,
                colorSpace,
                objectReference,
                explicitMask,
                Objects.requireNonNull(mask, "mask"));
    }

    /** @return {@link #VERSION_1} */
    public int getVersion() { return VERSION_1; }
    /** @return the closed source kind */
    public SourceKind getSourceKind() { return sourceKind; }
    /** @return a defensive encoded or sample byte copy when present */
    public Optional<byte[]> getBytes() {
        return bytes == null ? Optional.<byte[]>empty() : Optional.of(bytes.clone());
    }
    /** @return declared width for raw samples, otherwise zero */
    public int getWidth() { return width; }
    /** @return declared height for raw samples, otherwise zero */
    public int getHeight() { return height; }
    /** @return declared bit depth for raw samples, otherwise zero */
    public int getBitsPerComponent() { return bitsPerComponent; }
    /** @return the raw-sample color space when present */
    public Optional<CanvasColorSpace> getColorSpace() {
        return Optional.ofNullable(colorSpace);
    }
    /** @return the borrowed existing Image Resource identity when present */
    public Optional<ObjectReference> getObjectReference() {
        return Optional.ofNullable(objectReference);
    }
    /** @return the explicit Image Mask when present */
    public Optional<CanvasMask> getExplicitMask() {
        return Optional.ofNullable(explicitMask);
    }
    /** @return the soft mask when present */
    public Optional<CanvasMask> getSoftMask() {
        return Optional.ofNullable(softMask);
    }

    @Override
    public boolean equals(Object candidate) {
        if (!(candidate instanceof CanvasImage)) {
            return false;
        }
        CanvasImage other = (CanvasImage) candidate;
        return sourceKind == other.sourceKind
                && width == other.width
                && height == other.height
                && bitsPerComponent == other.bitsPerComponent
                && Arrays.equals(bytes, other.bytes)
                && Objects.equals(colorSpace, other.colorSpace)
                && Objects.equals(objectReference, other.objectReference)
                && Objects.equals(explicitMask, other.explicitMask)
                && Objects.equals(softMask, other.softMask);
    }

    @Override
    public int hashCode() {
        int result = sourceKind.hashCode();
        result = 31 * result + Arrays.hashCode(bytes);
        result = 31 * result + width;
        result = 31 * result + height;
        result = 31 * result + bitsPerComponent;
        result = 31 * result + Objects.hashCode(colorSpace);
        result = 31 * result + Objects.hashCode(objectReference);
        result = 31 * result + Objects.hashCode(explicitMask);
        result = 31 * result + Objects.hashCode(softMask);
        return result;
    }

    private static CanvasImage encoded(
            SourceKind sourceKind,
            byte[] encodedBytes) {
        return new CanvasImage(
                sourceKind,
                Objects.requireNonNull(encodedBytes, "encodedBytes"),
                0,
                0,
                0,
                null,
                null,
                null,
                null);
    }
}
