package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.nio.file.Path;

/** Immutable ImageMagick comparator identity loaded from repository authority. */
final class ImageMagickPin {

    private final Path executable;
    private final String version;
    private final String distributionName;
    private final String archiveSha256;
    private final String executableSha256;
    private final String distributionLicense;
    private final String noticeManifest;

    private ImageMagickPin(
            Path executable,
            String version,
            String distributionName,
            String archiveSha256,
            String executableSha256,
            String distributionLicense,
            String noticeManifest) {
        this.executable = executable;
        this.version = version;
        this.distributionName = distributionName;
        this.archiveSha256 = archiveSha256;
        this.executableSha256 = executableSha256;
        this.distributionLicense = distributionLicense;
        this.noticeManifest = noticeManifest;
    }

    static ImageMagickPin load(Path path) throws IOException {
        PinProperties properties = PinProperties.load(path, "ImageMagick pin");
        return new ImageMagickPin(
                properties.requiredExecutable("IMAGEMAGICK_EXECUTABLE"),
                properties.required("IMAGEMAGICK_VERSION"),
                properties.required("IMAGEMAGICK_DISTRIBUTION_NAME"),
                properties.requiredSha256("IMAGEMAGICK_ARCHIVE_SHA256"),
                properties.requiredSha256("IMAGEMAGICK_EXECUTABLE_SHA256"),
                properties.required("IMAGEMAGICK_DISTRIBUTION_LICENSE"),
                properties.required("IMAGEMAGICK_NOTICE_MANIFEST"));
    }

    Path executable() {
        return executable;
    }

    String version() {
        return version;
    }

    String distributionName() {
        return distributionName;
    }

    String archiveSha256() {
        return archiveSha256;
    }

    String executableSha256() {
        return executableSha256;
    }

    String distributionLicense() {
        return distributionLicense;
    }

    String noticeManifest() {
        return noticeManifest;
    }
}
