package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Immutable visual Acceptance Profile loaded from its repository authority. */
final class VisualProfile {

    private static final String SUPPORTED_PAGE_BOX =
            "effective CropBox; CropBox is absent, so MediaBox "
                    + "[0 0 612 792] points is used";
    private static final String T03_PROFILE =
            "T03-document-workflow-transaction";
    private static final String T18_PROFILE =
            "T18-canvas-images-colors-transparency";
    private static final String T03_COLOR_POLICY =
            "sRGB, opaque 8-bit RGB PNG; grayscale and alpha are disabled";
    private static final String T03_FONT_POLICY =
            "not applicable; the blank document has no text or font resources "
                    + "and uses no system fonts";
    private static final String T03_ANTIALIASING_POLICY =
            "pinned PDFium default smoothing; no marks are present for "
                    + "antialiasing to affect";
    private static final String T18_COLOR_POLICY =
            "sRGB, opaque 8-bit RGB PNG after compositing over opaque white";
    private static final String T18_FONT_POLICY =
            "not applicable; the artifact has no text or font resources and "
                    + "uses no system fonts";
    private static final String T18_ANTIALIASING_POLICY =
            "pinned PDFium default smoothing; image interpolation is disabled "
                    + "and vector edges are axis-aligned";
    private static final String SUPPORTED_BACKGROUND = "opaque white (#ffffff)";
    private static final String SUPPORTED_COMPARISON_METRIC = "AE";

    private final String profileId;
    private final String pageBox;
    private final int dpi;
    private final String colorPolicy;
    private final String fontPolicy;
    private final String antialiasingPolicy;
    private final String background;
    private final int rasterWidth;
    private final int rasterHeight;
    private final String comparisonMetric;
    private final int comparisonFuzzPercent;
    private final long comparisonThreshold;
    private final long rendererAgreementThreshold;
    private final String expectedRasterReference;
    private final Path expectedRaster;
    private final String expectedRasterSha256;

    private VisualProfile(
            String profileId,
            String pageBox,
            int dpi,
            String colorPolicy,
            String fontPolicy,
            String antialiasingPolicy,
            String background,
            int rasterWidth,
            int rasterHeight,
            String comparisonMetric,
            int comparisonFuzzPercent,
            long comparisonThreshold,
            long rendererAgreementThreshold,
            String expectedRasterReference,
            Path expectedRaster,
            String expectedRasterSha256) {
        this.profileId = profileId;
        this.pageBox = pageBox;
        this.dpi = dpi;
        this.colorPolicy = colorPolicy;
        this.fontPolicy = fontPolicy;
        this.antialiasingPolicy = antialiasingPolicy;
        this.background = background;
        this.rasterWidth = rasterWidth;
        this.rasterHeight = rasterHeight;
        this.comparisonMetric = comparisonMetric;
        this.comparisonFuzzPercent = comparisonFuzzPercent;
        this.comparisonThreshold = comparisonThreshold;
        this.rendererAgreementThreshold = rendererAgreementThreshold;
        this.expectedRasterReference = expectedRasterReference;
        this.expectedRaster = expectedRaster;
        this.expectedRasterSha256 = expectedRasterSha256;
    }

    static VisualProfile load(Path path) throws IOException {
        Path absolutePath = path.toAbsolutePath().normalize();
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(absolutePath)) {
            properties.load(input);
        }
        String expectedReference = required(properties, "EXPECTED_RASTER");
        String profileId = required(properties, "PROFILE_ID");
        Path relativeExpected = java.nio.file.Paths.get(expectedReference);
        if (relativeExpected.isAbsolute()) {
            throw new IOException("EXPECTED_RASTER must be relative to the profile");
        }
        Path expected = absolutePath.getParent()
                .resolve(relativeExpected)
                .normalize();
        Path authorityRoot = absolutePath.getParent().getParent();
        if (authorityRoot != null && !expected.startsWith(authorityRoot)) {
            throw new IOException("EXPECTED_RASTER escapes the profile authority root");
        }
        String pageBox = required(properties, "PAGE_BOX");
        String colorPolicy = required(properties, "COLOR_POLICY");
        String fontPolicy = required(properties, "FONT_POLICY");
        String antialiasingPolicy = required(properties, "ANTIALIASING_POLICY");
        String background = required(properties, "BACKGROUND");
        String comparisonMetric = required(properties, "COMPARISON_METRIC");
        requireSupported("PAGE_BOX", pageBox, SUPPORTED_PAGE_BOX);
        String supportedColor;
        String supportedFont;
        String supportedAntialiasing;
        if (T03_PROFILE.equals(profileId)) {
            supportedColor = T03_COLOR_POLICY;
            supportedFont = T03_FONT_POLICY;
            supportedAntialiasing = T03_ANTIALIASING_POLICY;
        } else if (T18_PROFILE.equals(profileId)) {
            supportedColor = T18_COLOR_POLICY;
            supportedFont = T18_FONT_POLICY;
            supportedAntialiasing = T18_ANTIALIASING_POLICY;
        } else {
            throw new IOException("Unsupported visual profile ID: " + profileId);
        }
        requireSupported("COLOR_POLICY", colorPolicy, supportedColor);
        requireSupported("FONT_POLICY", fontPolicy, supportedFont);
        requireSupported(
                "ANTIALIASING_POLICY",
                antialiasingPolicy,
                supportedAntialiasing);
        requireSupported("BACKGROUND", background, SUPPORTED_BACKGROUND);
        requireSupported(
                "COMPARISON_METRIC",
                comparisonMetric,
                SUPPORTED_COMPARISON_METRIC);
        int fuzzPercent = nonnegativeInt(properties, "COMPARISON_FUZZ_PERCENT");
        if (fuzzPercent > 100) {
            throw new IOException(
                    "Visual profile COMPARISON_FUZZ_PERCENT must be at most 100");
        }
        return new VisualProfile(
                profileId,
                pageBox,
                positiveInt(properties, "DPI"),
                colorPolicy,
                fontPolicy,
                antialiasingPolicy,
                background,
                positiveInt(properties, "RASTER_WIDTH"),
                positiveInt(properties, "RASTER_HEIGHT"),
                comparisonMetric,
                fuzzPercent,
                nonnegativeLong(properties, "COMPARISON_THRESHOLD"),
                nonnegativeLong(properties, "RENDERER_AGREEMENT_THRESHOLD"),
                expectedReference,
                expected,
                requiredSha256(properties, "EXPECTED_RASTER_SHA256"));
    }

    String profileId() {
        return profileId;
    }

    String pageBox() {
        return pageBox;
    }

    int dpi() {
        return dpi;
    }

    String colorPolicy() {
        return colorPolicy;
    }

    String fontPolicy() {
        return fontPolicy;
    }

    String antialiasingPolicy() {
        return antialiasingPolicy;
    }

    String background() {
        return background;
    }

    int rasterWidth() {
        return rasterWidth;
    }

    int rasterHeight() {
        return rasterHeight;
    }

    String comparisonMetric() {
        return comparisonMetric;
    }

    int comparisonFuzzPercent() {
        return comparisonFuzzPercent;
    }

    String comparisonDescription() {
        return "ImageMagick absolute error count (" + comparisonMetric
                + ") with fuzz " + comparisonFuzzPercent + " percent";
    }

    long comparisonThreshold() {
        return comparisonThreshold;
    }

    long rendererAgreementThreshold() {
        return rendererAgreementThreshold;
    }

    String expectedRasterReference() {
        return expectedRasterReference;
    }

    Path expectedRaster() {
        return expectedRaster;
    }

    String expectedRasterSha256() {
        return expectedRasterSha256;
    }

    private static int positiveInt(Properties properties, String key)
            throws IOException {
        long value = nonnegativeLong(properties, key);
        if (value == 0L || value > Integer.MAX_VALUE) {
            throw new IOException("Visual profile property must be a positive integer: "
                    + key);
        }
        return (int) value;
    }

    private static int nonnegativeInt(Properties properties, String key)
            throws IOException {
        long value = nonnegativeLong(properties, key);
        if (value > Integer.MAX_VALUE) {
            throw new IOException("Visual profile property is out of integer range: "
                    + key);
        }
        return (int) value;
    }

    private static void requireSupported(
            String key,
            String actual,
            String supported) throws IOException {
        if (!supported.equals(actual)) {
            throw new IOException(
                    "Unsupported visual profile property " + key + ": " + actual);
        }
    }

    private static long nonnegativeLong(Properties properties, String key)
            throws IOException {
        String text = required(properties, key);
        try {
            long value = Long.parseLong(text);
            if (value < 0L) {
                throw new NumberFormatException("negative");
            }
            return value;
        } catch (NumberFormatException invalid) {
            throw new IOException(
                    "Visual profile property must be a nonnegative integer: " + key,
                    invalid);
        }
    }

    private static String requiredSha256(Properties properties, String key)
            throws IOException {
        String value = required(properties, key);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IOException("Invalid visual profile SHA-256 property " + key);
        }
        return value;
    }

    private static String required(Properties properties, String key)
            throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IOException("Missing visual profile property " + key);
        }
        return value.trim();
    }
}
