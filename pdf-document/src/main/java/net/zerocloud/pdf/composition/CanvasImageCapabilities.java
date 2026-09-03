package net.zerocloud.pdf.composition;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Detached project-owned image input and conversion capability result. */
public final class CanvasImageCapabilities {

    /** The currently supported result representation version. */
    public static final int VERSION_1 = 1;

    /** Stable availability outcomes. */
    public enum Availability {
        AVAILABLE,
        OPTIONAL_CODEC_UNAVAILABLE
    }

    /** Stable embedding policies. */
    public enum Handling {
        /** Exact encoded bytes are retained behind their declared PDF filter. */
        PASS_THROUGH,
        /** ImageIO pixels become eight-bit DeviceRGB plus an optional soft mask. */
        NORMALIZE_TO_DEVICE_RGB_8,
        /** Caller samples retain their declared supported color space. */
        DIRECT_SAMPLES,
        /** A same-Session indirect image stream is referenced unchanged. */
        BORROWED_RESOURCE
    }

    private final Map<CanvasImage.SourceKind, Support> support;

    private CanvasImageCapabilities(Availability tiffAvailability) {
        EnumMap<CanvasImage.SourceKind, Support> values =
                new EnumMap<CanvasImage.SourceKind, Support>(
                        CanvasImage.SourceKind.class);
        values.put(
                CanvasImage.SourceKind.JPEG,
                new Support(Availability.AVAILABLE, Handling.PASS_THROUGH));
        values.put(
                CanvasImage.SourceKind.PNG,
                new Support(
                        Availability.AVAILABLE,
                        Handling.NORMALIZE_TO_DEVICE_RGB_8));
        values.put(
                CanvasImage.SourceKind.TIFF,
                new Support(
                        Objects.requireNonNull(
                                tiffAvailability,
                                "tiffAvailability"),
                        Handling.NORMALIZE_TO_DEVICE_RGB_8));
        values.put(
                CanvasImage.SourceKind.RAW_SAMPLES,
                new Support(Availability.AVAILABLE, Handling.DIRECT_SAMPLES));
        values.put(
                CanvasImage.SourceKind.EXISTING,
                new Support(
                        Availability.AVAILABLE,
                        Handling.BORROWED_RESOURCE));
        this.support = Collections.unmodifiableMap(values);
    }

    /** Creates the detected result used by the library-owned query. */
    public static CanvasImageCapabilities version1(
            Availability tiffAvailability) {
        return new CanvasImageCapabilities(tiffAvailability);
    }

    /** @return {@link #VERSION_1} */
    public int getVersion() { return VERSION_1; }

    /** Returns support for one closed source kind. */
    public Support getSupport(CanvasImage.SourceKind sourceKind) {
        return support.get(Objects.requireNonNull(sourceKind, "sourceKind"));
    }

    /** @return every closed source-kind mapping */
    public Map<CanvasImage.SourceKind, Support> getSupport() { return support; }

    /** One immutable availability and handling mapping. */
    public static final class Support {

        private final Availability availability;
        private final Handling handling;

        private Support(Availability availability, Handling handling) {
            this.availability = availability;
            this.handling = handling;
        }

        /** @return stable availability */
        public Availability getAvailability() { return availability; }
        /** @return stable embedding policy */
        public Handling getHandling() { return handling; }
    }
}
