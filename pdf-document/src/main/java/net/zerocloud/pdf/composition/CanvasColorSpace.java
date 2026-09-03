package net.zerocloud.pdf.composition;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable device, calibrated, or ICC-based Canvas Color Space.
 *
 * <p>Numeric and ICC compatibility checks occur when the containing
 * {@code DrawCanvas} command executes, allowing the complete request to fail
 * atomically.</p>
 *
 * @since 0.1.0
 */
public final class CanvasColorSpace {

    /** The currently supported declaration representation version. */
    public static final int VERSION_1 = 1;

    /** Closed color-space families supported by T18. */
    public enum Family {
        DEVICE_GRAY,
        DEVICE_RGB,
        DEVICE_CMYK,
        CAL_GRAY,
        CAL_RGB,
        ICC_BASED
    }

    private static final CanvasColorSpace DEVICE_GRAY = device(Family.DEVICE_GRAY);
    private static final CanvasColorSpace DEVICE_RGB = device(Family.DEVICE_RGB);
    private static final CanvasColorSpace DEVICE_CMYK = device(Family.DEVICE_CMYK);

    private final Family family;
    private final double[] whitePoint;
    private final double[] blackPoint;
    private final double[] gamma;
    private final double[] matrix;
    private final byte[] iccProfile;

    private CanvasColorSpace(
            Family family,
            double[] whitePoint,
            double[] blackPoint,
            double[] gamma,
            double[] matrix,
            byte[] iccProfile) {
        this.family = Objects.requireNonNull(family, "family");
        this.whitePoint = copy(whitePoint);
        this.blackPoint = copy(blackPoint);
        this.gamma = copy(gamma);
        this.matrix = copy(matrix);
        this.iccProfile = iccProfile == null ? null : iccProfile.clone();
    }

    /** @return the singleton DeviceGray declaration */
    public static CanvasColorSpace deviceGray() { return DEVICE_GRAY; }

    /** @return the singleton DeviceRGB declaration */
    public static CanvasColorSpace deviceRgb() { return DEVICE_RGB; }

    /** @return the singleton DeviceCMYK declaration */
    public static CanvasColorSpace deviceCmyk() { return DEVICE_CMYK; }

    /** Creates a calibrated gray declaration. */
    public static CanvasColorSpace calibratedGray(
            double[] whitePoint,
            double[] blackPoint,
            double gamma) {
        return new CanvasColorSpace(
                Family.CAL_GRAY,
                Objects.requireNonNull(whitePoint, "whitePoint"),
                Objects.requireNonNull(blackPoint, "blackPoint"),
                new double[] {gamma},
                null,
                null);
    }

    /** Creates a calibrated RGB declaration. */
    public static CanvasColorSpace calibratedRgb(
            double[] whitePoint,
            double[] blackPoint,
            double[] gamma,
            double[] matrix) {
        return new CanvasColorSpace(
                Family.CAL_RGB,
                Objects.requireNonNull(whitePoint, "whitePoint"),
                Objects.requireNonNull(blackPoint, "blackPoint"),
                Objects.requireNonNull(gamma, "gamma"),
                Objects.requireNonNull(matrix, "matrix"),
                null);
    }

    /** Creates an ICCBased declaration from a defensive profile copy. */
    public static CanvasColorSpace iccBased(byte[] profileBytes) {
        return new CanvasColorSpace(
                Family.ICC_BASED,
                null,
                null,
                null,
                null,
                Objects.requireNonNull(profileBytes, "profileBytes"));
    }

    /** @return {@link #VERSION_1} */
    public int getVersion() { return VERSION_1; }

    /** @return the closed family */
    public Family getFamily() { return family; }

    /** @return a defensive white-point copy, or an empty array */
    public double[] getWhitePoint() { return whitePoint.clone(); }

    /** @return a defensive black-point copy, or an empty array */
    public double[] getBlackPoint() { return blackPoint.clone(); }

    /** @return defensive gamma values, or an empty array */
    public double[] getGamma() { return gamma.clone(); }

    /** @return a defensive calibration-matrix copy, or an empty array */
    public double[] getMatrix() { return matrix.clone(); }

    /** @return a defensive ICC profile copy when this is ICCBased */
    public Optional<byte[]> getIccProfileBytes() {
        return iccProfile == null
                ? Optional.<byte[]>empty()
                : Optional.of(iccProfile.clone());
    }

    @Override
    public boolean equals(Object candidate) {
        if (!(candidate instanceof CanvasColorSpace)) {
            return false;
        }
        CanvasColorSpace other = (CanvasColorSpace) candidate;
        return family == other.family
                && Arrays.equals(whitePoint, other.whitePoint)
                && Arrays.equals(blackPoint, other.blackPoint)
                && Arrays.equals(gamma, other.gamma)
                && Arrays.equals(matrix, other.matrix)
                && Arrays.equals(iccProfile, other.iccProfile);
    }

    @Override
    public int hashCode() {
        int result = family.hashCode();
        result = 31 * result + Arrays.hashCode(whitePoint);
        result = 31 * result + Arrays.hashCode(blackPoint);
        result = 31 * result + Arrays.hashCode(gamma);
        result = 31 * result + Arrays.hashCode(matrix);
        result = 31 * result + Arrays.hashCode(iccProfile);
        return result;
    }

    private static CanvasColorSpace device(Family family) {
        return new CanvasColorSpace(family, null, null, null, null, null);
    }

    private static double[] copy(double[] values) {
        return values == null ? new double[0] : values.clone();
    }
}
