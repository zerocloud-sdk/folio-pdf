package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Validated property access shared by repository-owned tool pin authorities. */
final class PinProperties {

    private final Properties properties;
    private final String authority;
    private final Path authorityPath;

    private PinProperties(
            Properties properties,
            String authority,
            Path authorityPath) {
        this.properties = properties;
        this.authority = authority;
        this.authorityPath = authorityPath;
    }

    static PinProperties load(Path path, String authority) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return new PinProperties(
                properties,
                authority,
                path.toAbsolutePath().normalize());
    }

    String required(String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IOException("Missing " + authority + " property " + key);
        }
        return value.trim();
    }

    String requiredSha256(String key) throws IOException {
        String value = required(key);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IOException("Invalid " + authority + " SHA-256 property " + key);
        }
        return value;
    }

    Path requiredExecutable(String key) throws IOException {
        Path configured = java.nio.file.Paths.get(required(key));
        Path executable = configured.isAbsolute()
                ? configured.normalize()
                : authorityPath.getParent().resolve(configured).normalize();
        return executable.toAbsolutePath().normalize();
    }
}
