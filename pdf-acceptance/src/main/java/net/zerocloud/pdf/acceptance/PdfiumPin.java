package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.nio.file.Path;

/** Immutable PDFium renderer identity loaded from the repository authority. */
final class PdfiumPin {

    private final Path executable;
    private final String cliVersion;
    private final String engineVersion;
    private final String engineDistributionName;
    private final String engineArchiveSha256;
    private final String distributionName;
    private final String archiveSha256;
    private final String executableSha256;
    private final String distributionLicense;
    private final String engineLicense;
    private final String noticeManifest;

    private PdfiumPin(
            Path executable,
            String cliVersion,
            String engineVersion,
            String engineDistributionName,
            String engineArchiveSha256,
            String distributionName,
            String archiveSha256,
            String executableSha256,
            String distributionLicense,
            String engineLicense,
            String noticeManifest) {
        this.executable = executable;
        this.cliVersion = cliVersion;
        this.engineVersion = engineVersion;
        this.engineDistributionName = engineDistributionName;
        this.engineArchiveSha256 = engineArchiveSha256;
        this.distributionName = distributionName;
        this.archiveSha256 = archiveSha256;
        this.executableSha256 = executableSha256;
        this.distributionLicense = distributionLicense;
        this.engineLicense = engineLicense;
        this.noticeManifest = noticeManifest;
    }

    static PdfiumPin load(Path path) throws IOException {
        PinProperties properties = PinProperties.load(path, "PDFium pin");
        return new PdfiumPin(
                properties.requiredExecutable("PDFIUM_EXECUTABLE"),
                properties.required("PDFIUM_CLI_VERSION"),
                properties.required("PDFIUM_ENGINE_VERSION"),
                properties.required("PDFIUM_ENGINE_DISTRIBUTION_NAME"),
                properties.requiredSha256("PDFIUM_ENGINE_ARCHIVE_SHA256"),
                properties.required("PDFIUM_DISTRIBUTION_NAME"),
                properties.requiredSha256("PDFIUM_ARCHIVE_SHA256"),
                properties.requiredSha256("PDFIUM_EXECUTABLE_SHA256"),
                properties.required("PDFIUM_DISTRIBUTION_LICENSE"),
                properties.required("PDFIUM_ENGINE_LICENSE"),
                properties.required("PDFIUM_NOTICE_MANIFEST"));
    }

    Path executable() {
        return executable;
    }

    String cliVersion() {
        return cliVersion;
    }

    String engineVersion() {
        return engineVersion;
    }

    String engineDistributionName() {
        return engineDistributionName;
    }

    String engineArchiveSha256() {
        return engineArchiveSha256;
    }

    String producerVersion() {
        return cliVersion + "-pdfium-" + engineVersion;
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

    String engineLicense() {
        return engineLicense;
    }

    String noticeManifest() {
        return noticeManifest;
    }
}
