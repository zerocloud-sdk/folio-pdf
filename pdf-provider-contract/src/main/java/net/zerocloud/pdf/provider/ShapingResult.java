package net.zerocloud.pdf.provider;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Validated version 1 shaping results in visual glyph order. Advances and
 * offsets are signed font units, independent of nominal cmap widths. Cluster
 * ranges are half-open UTF-16 ranges in the original logical request and can
 * be shared by several glyphs, including glyphs without a cmap entry.
 * Native font validity, cluster membership in ICU graphemes and the expected
 * engine version are checked by the consuming Provider/Composition boundary.
 *
 * @since 0.1.0
 */
public final class ShapingResult {

    private static final int MAGIC = 0x48525331;
    private final String engineVersion;
    private final int unitsPerEm;
    private final ShapingRequest.Direction direction;
    private final List<Glyph> glyphs;

    private ShapingResult(String engineVersion, int unitsPerEm,
            ShapingRequest.Direction direction, List<Glyph> glyphs) {
        this.engineVersion = engineVersion;
        this.unitsPerEm = unitsPerEm;
        this.direction = direction;
        this.glyphs = Collections.unmodifiableList(glyphs);
    }

    /**
     * Returns the reported engine version.
     * @return the engine's reported major.minor.micro version
     */
    public String getEngineVersion() { return engineVersion; }
    /**
     * Returns the native font scale.
     * @return the native font scale, between 16 and 16384 units per em
     */
    public int getUnitsPerEm() { return unitsPerEm; }
    /**
     * Returns the validated shaping direction.
     * @return the horizontal direction, checked against the request
     */
    public ShapingRequest.Direction getDirection() { return direction; }
    /**
     * Returns the shaped glyphs in visual order.
     * @return an unmodifiable list of immutable glyphs in visual order
     */
    public List<Glyph> getGlyphs() { return glyphs; }

    /**
     * Checks external bytes against their request before exposing glyphs.
     *
     * @param result detached HRS1 bytes returned by the external Provider
     * @param request the original logical input and declared result bound
     * @return the decoded result with complete logical cluster ranges
     * @throws NullPointerException if either argument is null
     * @throws ProviderFailure for a native failure or malformed output; a
     *         Provider execution adds its registered identity to this failure
     */
    public static ShapingResult decode(ProviderResult result, ShapingRequest request)
            throws ProviderFailure {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(request, "request");
        if (result.getOutputLength() < 32
                || result.getOutputLength() > 32L + 24L * request.getMaximumGlyphs()) {
            throw failure(ProviderFailureCode.MALFORMED_OUTPUT);
        }
        try {
            byte[] bytes = result.getOutput();
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
            if (input.readInt() != MAGIC) { throw failure(ProviderFailureCode.MALFORMED_OUTPUT); }
            int status = input.readInt();
            int major = input.readInt();
            int minor = input.readInt();
            int micro = input.readInt();
            int units = input.readInt();
            int direction = input.readInt();
            int count = input.readInt();
            if (major < 0 || minor < 0 || micro < 0 || status < 0 || status > 4
                    || direction != (request.getDirection()
                            == ShapingRequest.Direction.LEFT_TO_RIGHT ? 0 : 1)
                    || count < 0 || count > request.getMaximumGlyphs()
                    || bytes.length != 32L + 24L * count) {
                throw failure(ProviderFailureCode.MALFORMED_OUTPUT);
            }
            if (status != 0) {
                if (count != 0) { throw failure(ProviderFailureCode.MALFORMED_OUTPUT); }
                throw failure(status == 2 ? ProviderFailureCode.OUTPUT_LIMIT_EXCEEDED
                        : status == 3 ? ProviderFailureCode.PROVIDER_UNAVAILABLE
                        : ProviderFailureCode.EXECUTION_FAILED);
            }
            if (units < 16 || units > 16384 || count == 0) {
                throw failure(ProviderFailureCode.MALFORMED_OUTPUT);
            }
            List<Glyph> glyphs = new ArrayList<Glyph>(count);
            TreeSet<Integer> boundaries = new TreeSet<Integer>();
            boundaries.add(request.getText().length());
            int previous = request.getDirection() == ShapingRequest.Direction.LEFT_TO_RIGHT
                    ? 0 : request.getText().length();
            for (int index = 0; index < count; index++) {
                int id = input.readInt();
                int cluster = input.readInt();
                if (id < 1 || id > 65535 || cluster < 0 || cluster >= request.getText().length()
                        || Character.isLowSurrogate(request.getText().charAt(cluster))
                        || (direction == 0 ? cluster < previous : cluster > previous)) {
                    throw failure(ProviderFailureCode.MALFORMED_OUTPUT);
                }
                previous = cluster;
                boundaries.add(cluster);
                glyphs.add(new Glyph(id, cluster, input.readInt(), input.readInt(),
                        input.readInt(), input.readInt()));
            }
            if (boundaries.first() != 0) { throw failure(ProviderFailureCode.MALFORMED_OUTPUT); }
            for (Glyph glyph : glyphs) { glyph.clusterEnd = boundaries.higher(glyph.clusterStart); }
            return new ShapingResult(major + "." + minor + "." + micro,
                    units, request.getDirection(), glyphs);
        } catch (IOException failure) {
            throw failure(ProviderFailureCode.MALFORMED_OUTPUT);
        }
    }

    private static ProviderFailure failure(ProviderFailureCode code) {
        return ProviderFailure.forProvider(code, null, ShapingRequest.CAPABILITY_ID);
    }

    /** One positioned glyph, with no public backend or native handle. */
    public static final class Glyph {
        private final int glyphId;
        private final int clusterStart;
        private int clusterEnd;
        private final int xAdvance;
        private final int yAdvance;
        private final int xOffset;
        private final int yOffset;

        private Glyph(int glyphId, int clusterStart, int xAdvance, int yAdvance,
                int xOffset, int yOffset) {
            this.glyphId = glyphId;
            this.clusterStart = clusterStart;
            this.xAdvance = xAdvance;
            this.yAdvance = yAdvance;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
        }

        /**
         * Returns the source font glyph ID.
         * @return original font glyph ID, between 1 and 65535
         */
        public int getGlyphId() { return glyphId; }
        /**
         * Returns the inclusive cluster start.
         * @return inclusive UTF-16 start in the logical request
         */
        public int getClusterStart() { return clusterStart; }
        /**
         * Returns the exclusive cluster end.
         * @return exclusive UTF-16 end at the next distinct logical cluster boundary
         */
        public int getClusterEnd() { return clusterEnd; }
        /**
         * Returns the horizontal pen advance.
         * @return signed horizontal pen advance in font units
         */
        public int getXAdvance() { return xAdvance; }
        /**
         * Returns the vertical pen advance.
         * @return signed vertical pen advance in font units
         */
        public int getYAdvance() { return yAdvance; }
        /**
         * Returns the horizontal drawing offset.
         * @return signed horizontal drawing offset from the pen in font units
         */
        public int getXOffset() { return xOffset; }
        /**
         * Returns the vertical drawing offset.
         * @return signed vertical drawing offset from the pen in font units
         */
        public int getYOffset() { return yOffset; }
    }
}
