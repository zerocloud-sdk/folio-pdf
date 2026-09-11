package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/** Independently authored, frozen inputs and expectations for metadata observations. */
final class T11Corpus {
    static final String PROFILE = "T11-metadata-outlines-destinations-attachments";
    private final Path directory;
    private final PinProperties expectations;

    T11Corpus(Path root) throws IOException {
        directory = root.resolve("capabilities/profiles/T11-metadata");
        if (!"83617b5340205c3ed2746329aecf7c7d0009fe7dc4555141cf162f3d14f1ae25".equals(
                EvidenceFiles.sha256(directory.resolve("corpus.json")))
                || !"cf5c080324f0d18f5571d61c3019f9858dd1cd13f5c148a7aecbc5d1d4155f22".equals(
                EvidenceFiles.sha256(directory.resolve("corpus.properties")))) {
            throw new IOException("T11 frozen corpus identity mismatch");
        }
        expectations = PinProperties.load(directory.resolve("corpus.properties"), "T11 corpus");
        verifySources();
    }

    Path source(String name) throws IOException {
        return directory.resolve(expectations.required("sources." + name + ".path"));
    }

    String expected(String key) throws IOException {
        String value = expectations.optional(key);
        if (value == null) {
            throw new IOException("Missing frozen T11 expectation: " + key);
        }
        return value;
    }

    Map<String, String> product(String name) throws IOException {
        if (!java.util.Arrays.asList("edited", "merged", "left", "right").contains(name)) {
            throw new IllegalArgumentException("Unknown frozen T11 product: " + name);
        }
        Properties all = new Properties();
        try (InputStream input = Files.newInputStream(directory.resolve("corpus.properties"))) {
            all.load(input);
        }
        String prefix = "products." + name + ".";
        Map<String, String> values = new TreeMap<String, String>();
        for (String key : all.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                values.put(key.substring(prefix.length()), all.getProperty(key));
            }
        }
        int pages = Integer.parseInt(values.get("pages.count"));
        for (int index = 0; index < pages; index++) {
            String pagePrefix = "pages." + values.get("pages." + index) + ".";
            for (String key : all.stringPropertyNames()) {
                if (key.startsWith(pagePrefix)) {
                    String field = key.substring(pagePrefix.length());
                    if (field.startsWith("media-box.") || field.startsWith("crop-box.")
                            || field.startsWith("contents.") || "rotation".equals(field)) {
                        values.put("page-observations." + index + "." + field, all.getProperty(key));
                    }
                }
            }
        }
        return values;
    }

    void verifySources() throws IOException {
        for (String name : new String[] {"primary", "appendix"}) {
            if (!expectations.requiredSha256("sources." + name + ".sha256").equals(
                    EvidenceFiles.sha256(source(name)))) {
                throw new IOException("T11 Source identity mismatch: " + name);
            }
        }
    }
}
