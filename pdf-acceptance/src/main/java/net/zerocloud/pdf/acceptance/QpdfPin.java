package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Immutable qpdf distribution identity loaded from the repository authority. */
final class QpdfPin {

    private final String version;
    private final String archiveSha256;

    private QpdfPin(String version, String archiveSha256) {
        this.version = version;
        this.archiveSha256 = archiveSha256;
    }

    static QpdfPin load(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return new QpdfPin(
                required(properties, "QPDF_VERSION"),
                required(properties, "QPDF_ARCHIVE_SHA256"));
    }

    String version() {
        return version;
    }

    String archiveSha256() {
        return archiveSha256;
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IOException("Missing qpdf pin property " + key);
        }
        return value.trim();
    }
}
