package net.zerocloud.pdf.composition;

import java.util.Objects;

/** Immutable reusable Canvas Program isolated as a PDF transparency group. */
public final class CanvasTransparencyGroup {

    /** The currently supported declaration representation version. */
    public static final int VERSION_1 = 1;

    private final CanvasRectangle box;
    private final CanvasColorSpace colorSpace;
    private final boolean isolated;
    private final boolean knockout;
    private final CanvasProgram program;

    private CanvasTransparencyGroup(
            CanvasRectangle box,
            CanvasColorSpace colorSpace,
            boolean isolated,
            boolean knockout,
            CanvasProgram program) {
        this.box = Objects.requireNonNull(box, "box");
        this.colorSpace = Objects.requireNonNull(colorSpace, "colorSpace");
        this.isolated = isolated;
        this.knockout = knockout;
        this.program = Objects.requireNonNull(program, "program");
    }

    /** Creates a version-1 transparency-group declaration. */
    public static CanvasTransparencyGroup version1(
            CanvasRectangle box,
            CanvasColorSpace colorSpace,
            boolean isolated,
            boolean knockout,
            CanvasProgram program) {
        return new CanvasTransparencyGroup(
                box,
                colorSpace,
                isolated,
                knockout,
                program);
    }

    /** @return {@link #VERSION_1} */
    public int getVersion() { return VERSION_1; }
    /** @return group bounding box */
    public CanvasRectangle getBox() { return box; }
    /** @return explicit group color space */
    public CanvasColorSpace getColorSpace() { return colorSpace; }
    /** @return whether the group is isolated */
    public boolean isIsolated() { return isolated; }
    /** @return whether the group uses knockout compositing */
    public boolean isKnockout() { return knockout; }
    /** @return immutable group Canvas Program */
    public CanvasProgram getProgram() { return program; }
}
