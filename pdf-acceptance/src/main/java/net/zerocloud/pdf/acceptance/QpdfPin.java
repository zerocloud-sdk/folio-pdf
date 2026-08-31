package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.nio.file.Path;

/** Immutable qpdf distribution identity loaded from the repository authority. */
final class QpdfPin {

    private final Path executable;
    private final String version;
    private final String archiveSha256;

    private QpdfPin(Path executable, String version, String archiveSha256) {
        this.executable = executable;
        this.version = version;
        this.archiveSha256 = archiveSha256;
    }

    static QpdfPin load(Path path) throws IOException {
        PinProperties properties = PinProperties.load(path, "qpdf pin");
        return new QpdfPin(
                properties.requiredExecutable("QPDF_EXECUTABLE"),
                properties.required("QPDF_VERSION"),
                properties.requiredSha256("QPDF_ARCHIVE_SHA256"));
    }

    Path executable() {
        return executable;
    }

    String version() {
        return version;
    }

    String archiveSha256() {
        return archiveSha256;
    }
}
