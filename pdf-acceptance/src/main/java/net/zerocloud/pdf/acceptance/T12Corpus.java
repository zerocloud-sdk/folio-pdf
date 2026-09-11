package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/** Original, frozen annotation inputs and expectations, independent of product generation. */
final class T12Corpus {
    static final String PROFILE = "T12-annotations-document-actions";
    static final String[] PRODUCTS = {"created", "changed", "flattened", "copied", "merged", "adopted", "left", "right"};
    private final Path directory;
    private final PinProperties expectations;

    T12Corpus(Path root) throws IOException {
        directory = root.resolve("capabilities/profiles/T12-annotations");
        if (!"fa6834bb206298743aba8f54af658315b1dee54b86319afbddb427af8676ad9a".equals(
                EvidenceFiles.sha256(directory.resolve("corpus.json")))
                || !"c1e18a08e9479ab881c6f3d1ebb431348680b4fb7a01fc976add795344c5ecb1".equals(
                EvidenceFiles.sha256(directory.resolve("corpus.properties")))) {
            throw new IOException("T12 frozen corpus identity mismatch");
        }
        expectations = PinProperties.load(directory.resolve("corpus.properties"), "T12 corpus");
        verifySources();
    }

    Path source(String name) throws IOException {
        return directory.resolve(expectations.required("sources." + name + ".path"));
    }

    String expected(String key) throws IOException {
        String value = expectations.optional(key);
        if (value == null) { throw new IOException("Missing frozen T12 expectation: " + key); }
        return value;
    }

    Map<String, String> product(String name) throws IOException {
        if (!Arrays.asList(PRODUCTS).contains(name)) {
            throw new IllegalArgumentException("Unknown frozen T12 product: " + name);
        }
        Properties all = new Properties();
        try (InputStream input = Files.newInputStream(directory.resolve("corpus.properties"))) { all.load(input); }
        String prefix = "products." + name + ".";
        Map<String, String> values = new TreeMap<String, String>();
        for (String key : all.stringPropertyNames()) {
            if (key.startsWith(prefix)) { values.put(key.substring(prefix.length()), all.getProperty(key)); }
        }
        return values;
    }

    void verifySources() throws IOException {
        for (String name : new String[] {"primary", "appendix"}) {
            if (!expectations.requiredSha256("sources." + name + ".sha256").equals(EvidenceFiles.sha256(source(name)))) {
                throw new IOException("T12 Source identity mismatch: " + name);
            }
        }
    }
}
