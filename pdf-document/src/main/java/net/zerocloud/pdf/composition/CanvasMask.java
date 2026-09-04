package net.zerocloud.pdf.composition;

import java.util.Arrays;
import java.util.Objects;

/** Bounded immutable raw samples for an explicit or soft Canvas Image mask. */
public final class CanvasMask {

    /** The currently supported declaration representation version. */
    public static final int VERSION_1 = 1;

    /** Closed mask kinds. */
    public enum Kind {
        /** One packed bit per pixel, most-significant bit first. */
        EXPLICIT_IMAGE,
        /** One eight-bit opacity sample per pixel. */
        SOFT_IMAGE
    }

    private final Kind kind;
    private final int width;
    private final int height;
    private final boolean inverted;
    private final byte[] samples;

    private CanvasMask(
            Kind kind,
            int width,
            int height,
            boolean inverted,
            byte[] samples) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.width = width;
        this.height = height;
        this.inverted = inverted;
        this.samples = Objects.requireNonNull(samples, "samples").clone();
    }

    /** Creates a packed one-bit explicit Image Mask declaration. */
    public static CanvasMask explicit(
            int width,
            int height,
            boolean inverted,
            byte[] packedSamples) {
        return new CanvasMask(
                Kind.EXPLICIT_IMAGE,
                width,
                height,
                inverted,
                packedSamples);
    }

    /** Creates an eight-bit grayscale soft-mask declaration. */
    public static CanvasMask soft(
            int width,
            int height,
            byte[] opacitySamples) {
        return new CanvasMask(
                Kind.SOFT_IMAGE,
                width,
                height,
                false,
                opacitySamples);
    }

    /** @return {@link #VERSION_1} */
    public int getVersion() { return VERSION_1; }
    /** @return the mask kind */
    public Kind getKind() { return kind; }
    /** @return pixel width */
    public int getWidth() { return width; }
    /** @return pixel height */
    public int getHeight() { return height; }
    /** @return whether one-bit samples use an inverted Decode mapping */
    public boolean isInverted() { return inverted; }
    /** @return a defensive sample copy */
    public byte[] getSamples() { return samples.clone(); }
    /** @return the exact sample byte length without copying the samples */
    public int getSampleByteLength() { return samples.length; }

    @Override
    public boolean equals(Object candidate) {
        if (!(candidate instanceof CanvasMask)) {
            return false;
        }
        CanvasMask other = (CanvasMask) candidate;
        return kind == other.kind
                && width == other.width
                && height == other.height
                && inverted == other.inverted
                && Arrays.equals(samples, other.samples);
    }

    @Override
    public int hashCode() {
        int result = kind.hashCode();
        result = 31 * result + width;
        result = 31 * result + height;
        result = 31 * result + (inverted ? 1 : 0);
        result = 31 * result + Arrays.hashCode(samples);
        return result;
    }
}
