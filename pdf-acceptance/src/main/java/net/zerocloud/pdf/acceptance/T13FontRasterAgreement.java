package net.zerocloud.pdf.acceptance;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import javax.imageio.ImageIO;

/** Secondary-only differences may occur solely on the original glyph boundary shells. */
final class T13FontRasterAgreement {
    private static final int[][] ORIGINS = {
        {20, 136}, {60, 136}, {100, 136}, {20, 56}, {70, 48}, {120, 56}, {170, 48}
    };

    private T13FontRasterAgreement() { }

    static Properties inspect(Path primary, Path secondary) throws IOException {
        String primaryHash = EvidenceFiles.sha256(primary);
        String secondaryHash = EvidenceFiles.sha256(secondary);
        if (Files.size(primary) > 4 * 1024 * 1024 || Files.size(secondary) > 4 * 1024 * 1024) {
            throw new IOException("T13 raster observation exceeds its fixed bound");
        }
        PngRaster.requireProfileRaster(primary, 240, 200);
        PngRaster.requireProfileRaster(secondary, 240, 200);
        BufferedImage first = ImageIO.read(primary.toFile());
        BufferedImage second = ImageIO.read(secondary.toFile());
        long changed = 0;
        long outside = 0;
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 240; x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    changed++;
                    if (!onOriginalEdge(x, y)) { outside++; }
                }
            }
        }
        Properties result = new Properties();
        result.setProperty("profile", T13Corpus.PROFILE + "-embedded-font-kinds-page-1");
        result.setProperty("primary-sha256", primaryHash);
        result.setProperty("secondary-sha256", secondaryHash);
        result.setProperty("changed-pixels", Long.toString(changed));
        result.setProperty("outside-edge-pixels", Long.toString(outside));
        String verdict = outside == 0 && changed <= 1120 ? "pass" : "fail";
        if (!primaryHash.equals(EvidenceFiles.sha256(primary)) || !secondaryHash.equals(EvidenceFiles.sha256(secondary))) {
            verdict = "indeterminate";
        }
        result.setProperty("raster-agreement", verdict);
        return result;
    }

    private static boolean onOriginalEdge(int x, int y) {
        for (int[] origin : ORIGINS) {
            int dx = x - origin[0];
            int dy = y - origin[1];
            // The raw PDF's 16x24 ink rectangle, expanded/contracted by one
            // pixel. Interiors and all unrelated page pixels must agree.
            if (dx >= -1 && dx < 17 && dy >= -1 && dy < 25
                    && !(dx >= 1 && dx < 15 && dy >= 1 && dy < 23)) { return true; }
        }
        return false;
    }
}
