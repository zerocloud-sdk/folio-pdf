package net.zerocloud.pdf.composition.query;

import net.zerocloud.pdf.DocumentQuery;
import net.zerocloud.pdf.composition.CanvasImageCapabilities;

/** Queries deterministic Canvas Image format and optional-codec support. */
public final class InspectCanvasImageCapabilities
        implements DocumentQuery<CanvasImageCapabilities> {

    /** The currently supported query representation version. */
    public static final int VERSION_1 = 1;

    private static final InspectCanvasImageCapabilities INSTANCE =
            new InspectCanvasImageCapabilities();

    private InspectCanvasImageCapabilities() {
    }

    /** @return the immutable version-1 query */
    public static InspectCanvasImageCapabilities version1() { return INSTANCE; }

    /** @return {@link #VERSION_1} */
    public int getVersion() { return VERSION_1; }
}
