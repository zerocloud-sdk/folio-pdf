package net.zerocloud.pdf.acceptance;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import net.zerocloud.pdf.ResourceExtractionLimits;
import net.zerocloud.pdf.ImageByteAccess;

/** Closed original image corpus; no product output supplies its expectations. */
final class T14Corpus {
    static final String PROFILE = "T14-image-resource-extraction";
    static final String[] PRODUCTS = {"inventory", "filters", "formats", "colors", "classifications"};
    private final Path directory;
    private final Properties expectations = new Properties();
    private final String jsonHash;
    private final String propertiesHash;

    T14Corpus(Path root) throws IOException {
        directory = root.resolve("capabilities/profiles/T14-images");
        // The final independent recorder additionally checks the frozen catalog
        // digest; this snapshot rejects changes between product construction,
        // detached observation and independently reopened observation.
        jsonHash = EvidenceFiles.sha256(directory.resolve("corpus.json"));
        byte[] properties = Files.readAllBytes(directory.resolve("corpus.properties"));
        propertiesHash = EvidenceFiles.sha256(properties);
        if (!"42dd8cdee47e5fc500d51ee954d5fc81fcb5994c1ee9e2edc37ffcc720f85356".equals(jsonHash)
                || !"6783d5fb7d28c0529baefcc18fd852c308a098f074461d52c410ce16306591c9".equals(propertiesHash)) {
            throw new IOException("T14 frozen corpus identity mismatch");
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(properties)) { expectations.load(input); }
        if (!PROFILE.equals(expected("profile"))) { throw new IOException("Unexpected T14 corpus profile"); }
        verifySources();
    }

    Path source(String product) throws IOException {
        if (!Arrays.asList(PRODUCTS).contains(product)) { throw new IOException("Unknown T14 product"); }
        return directory.resolve(expected("sources." + product + ".file"));
    }

    String expected(String key) throws IOException {
        String value = expectations.getProperty(key);
        if (value == null) { throw new IOException("Missing T14 corpus expectation: " + key); }
        return value;
    }

    Properties extraction(String product) throws IOException {
        source(product);
        Properties result = new Properties();
        String prefix = "products." + product + ".extraction.";
        for (String key : expectations.stringPropertyNames()) {
            if (key.startsWith(prefix)) { result.setProperty(key.substring(prefix.length()), expectations.getProperty(key)); }
        }
        if (result.isEmpty()) { throw new IOException("Missing T14 extraction expectations"); }
        return result;
    }

    ImageByteAccess byteAccess(String product) throws IOException {
        return ImageByteAccess.valueOf(expected("products." + product + ".byte-access"));
    }

    static ResourceExtractionLimits limits() {
        return ResourceExtractionLimits.builder().maximumPages(100).maximumPageTreeNodes(1000)
                .maximumTraversedResourceValues(100000).maximumResourceTraversalDepth(32).maximumDecodedPixels(1000000)
                .maximumDecompressedBytes(16 * 1024 * 1024).maximumReturnedBytes(16 * 1024 * 1024).build();
    }

    void verifySources() throws IOException {
        if (!jsonHash.equals(EvidenceFiles.sha256(directory.resolve("corpus.json")))
                || !propertiesHash.equals(EvidenceFiles.sha256(directory.resolve("corpus.properties")))) {
            throw new IOException("T14 corpus changed during observation");
        }
        for (String product : PRODUCTS) {
            if (!expected("sources." + product + ".sha256").equals(EvidenceFiles.sha256(source(product)))) {
                throw new IOException("T14 Source identity mismatch: " + product);
            }
        }
    }
}
