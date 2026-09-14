package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import net.zerocloud.pdf.ExtractionLimits;

/** Frozen original Sources and expectations, independent of extraction observations. */
final class T13Corpus {
    static final String PROFILE = "T13-text-logical-structure";
    static final String[] PRODUCTS = {"nested-split-type3", "marked-structure", "embedded-font-kinds",
        "embedded-font-inheritance", "uncertain-geometry"};
    private final Path directory;
    private static final String JSON_SHA256 = "fb348a2b139df12b93633cbf9c999490c584030aebeacc9b95bff57c9982707f";
    private static final String PROPERTIES_SHA256 = "2b6ebdd09f9ee86bae7c60e3916f82feb8ba413aab6b4d1465c53823646b79d1";
    private final Properties expectations = new Properties();

    T13Corpus(Path root) throws IOException {
        directory = root.resolve("capabilities/profiles/T13-text");
        byte[] json = Files.readAllBytes(directory.resolve("corpus.json"));
        byte[] properties = Files.readAllBytes(directory.resolve("corpus.properties"));
        if (!JSON_SHA256.equals(EvidenceFiles.sha256(json))
                || !PROPERTIES_SHA256.equals(EvidenceFiles.sha256(properties))) {
            throw new IOException("T13 frozen corpus identity mismatch");
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(properties)) { expectations.load(input); }
        verifySources();
    }

    void requireProduct(String name) {
        if (!Arrays.asList(PRODUCTS).contains(name)) {
            throw new IllegalArgumentException("Unknown frozen T13 product: " + name);
        }
    }

    String expected(String key) throws IOException {
        String value = expectations.getProperty(key);
        if (value == null) { throw new IOException("Missing frozen T13 expectation: " + key); }
        return value;
    }

    Path source(String product) throws IOException {
        requireProduct(product);
        return directory.resolve(expected("sources." + expected("products." + product + ".source") + ".path"));
    }

    Properties extraction(String product) throws IOException {
        requireProduct(product);
        Properties result = new Properties();
        String prefix = "products." + product + ".extraction.";
        for (String key : expectations.stringPropertyNames()) {
            if (key.startsWith(prefix)) { result.setProperty(key.substring(prefix.length()), expectations.getProperty(key)); }
        }
        if (result.isEmpty()) { throw new IOException("Missing T13 extraction expectations"); }
        return result;
    }

    static ExtractionLimits limits() {
        return ExtractionLimits.builder().maximumPages(100).maximumPageTreeNodes(1000)
                .maximumContentStreams(1000).maximumContentStreamDepth(32).maximumDecodedBytes(16 * 1024 * 1024)
                .maximumTextItems(100000).maximumUnicodeCodePoints(200000).maximumToUnicodeMappings(100000)
                .maximumFontDataEntries(1000000).maximumMarkedContentSequences(10000).maximumMarkedContentDepth(128)
                .maximumStructureElements(10000).maximumStructureItems(25000).maximumStructureDepth(128)
                .maximumRoleMappings(10000).build();
    }

    void verifySources() throws IOException {
        if (!JSON_SHA256.equals(EvidenceFiles.sha256(directory.resolve("corpus.json")))
                || !PROPERTIES_SHA256.equals(EvidenceFiles.sha256(directory.resolve("corpus.properties")))) {
            throw new IOException("T13 frozen corpus changed during observation");
        }
        for (String name : PRODUCTS) {
            if (!expected("sources." + name + ".sha256").equals(
                    EvidenceFiles.sha256(directory.resolve(expected("sources." + name + ".path"))))) {
                throw new IOException("T13 Source identity mismatch: " + name);
            }
        }
    }
}
