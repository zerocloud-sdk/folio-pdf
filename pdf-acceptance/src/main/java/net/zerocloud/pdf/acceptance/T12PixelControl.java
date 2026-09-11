package net.zerocloud.pdf.acceptance;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Qualifies the frozen comparator at exactly one changed pixel using original control data. */
final class T12PixelControl {
    private T12PixelControl() { }

    static Properties record(Path root, Path output) throws Exception {
        T12Corpus corpus = new T12Corpus(root);
        Path expected = root.resolve("capabilities/profiles/T12-annotations/expected/created-page-1.png");
        String originalHash = EvidenceFiles.sha256(expected);
        if (!originalHash.equals(corpus.expected("products.created.visual.0.raster-sha256"))) {
            throw new IOException("T12 pixel control original raster identity mismatch");
        }
        PngRaster.requireProfileRaster(expected, 240, 200);
        Files.copy(expected, output.resolve("original.png"));
        BufferedImage raster = ImageIO.read(expected.toFile());
        if (raster == null) { throw new IOException("T12 original control raster is unreadable"); }
        raster.setRGB(0, 0, raster.getRGB(0, 0) == 0xff000000 ? 0xffffffff : 0xff000000);
        if (!ImageIO.write(raster, "png", output.resolve("one-pixel.png").toFile())) {
            throw new IOException("T12 changed-pixel control was not written");
        }
        ImageMagickPin pin = ImageMagickPin.load(root.resolve("scripts/imagemagick-pin.properties"));
        Path binary = root.resolve(".build-cache/imagemagick/7.1.2-30/bin/imagemagick.AppImage");
        Properties result = new Properties();
        result.setProperty("visual", "indeterminate");
        result.setProperty("threshold", "0");
        result.setProperty("fuzz-percent", "0");
        result.setProperty("metric", "AE");
        result.setProperty("original-sha256", originalHash);
        result.setProperty("changed-sha256", EvidenceFiles.sha256(output.resolve("one-pixel.png")));
        result.setProperty("comparator-sha256", EvidenceFiles.sha256(binary));
        ProcessResult version = ExternalProcess.run(pin.executable(), output, "--version");
        StringBuilder findings = new StringBuilder("magick --version\n" + version.combinedOutput());
        boolean qualified = version.exitCode == 0 && version.combinedOutput().startsWith("Version: ImageMagick 7.1.2-30 ")
                && "7.1.2-30".equals(pin.version()) && pin.executableSha256().equals(EvidenceFiles.sha256(binary));
        for (String kind : new String[] {"positive", "negative"}) {
            boolean positive = "positive".equals(kind);
            String actual = positive ? "original.png" : "one-pixel.png";
            String difference = positive ? "positive-difference.png" : "difference.png";
            ProcessResult comparison = ExternalProcess.run(pin.executable(), output, "compare", "-metric", "AE", "-fuzz", "0%",
                    "-highlight-color", "#ff0000", "-lowlight-color", "#ffffff", "-define", "png:exclude-chunk=time,date",
                    "original.png", actual, difference);
            findings.append("\nmagick compare -metric AE -fuzz 0% -highlight-color #ff0000 -lowlight-color #ffffff ")
                    .append("-define png:exclude-chunk=time,date original.png ").append(actual).append(' ').append(difference)
                    .append("\nexit=").append(comparison.exitCode).append('\n').append(comparison.combinedOutput());
            Matcher metric = Pattern.compile("^([01])(?: \\([0-9.eE+\\-]+\\))?$").matcher(comparison.standardError.trim());
            String value = metric.matches() ? metric.group(1) : "unavailable";
            result.setProperty(kind + "-absolute-error", value);
            qualified &= comparison.exitCode == (positive ? 0 : 1) && (positive ? "0" : "1").equals(value)
                    && comparison.standardOutput.trim().isEmpty() && Files.size(output.resolve(difference)) > 0;
        }
        if (qualified && originalHash.equals(EvidenceFiles.sha256(expected))) { result.setProperty("visual", "fail"); }
        EvidenceFiles.write(output.resolve("comparator.txt"), findings.toString());
        T12AnnotationProducts.save(output.resolve("result.properties"), result);
        return result;
    }
}
