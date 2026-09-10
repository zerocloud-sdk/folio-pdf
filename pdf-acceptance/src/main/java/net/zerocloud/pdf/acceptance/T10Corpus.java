package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Frozen original inputs, shared by the repository-only T10 observations. */
final class T10Corpus {
    static final String PROFILE = "T10-page-manipulation-merge-split";
    private static final String JSON_SHA256 = "7044a1036e15a0c32465e1d818f5b38f37cfec0699ce1fac9d86c7927ab94c65";
    private static final String PROPERTIES_SHA256 = "e2777799c86dcfba25173fd3ed48cf99bc9d2472c68a2ad6dd5efc0320466293";
    private final Path directory;
    private final PinProperties expectations;

    T10Corpus(Path root) throws IOException {
        directory = root.resolve("capabilities/profiles/T10-pages");
        if (!JSON_SHA256.equals(EvidenceFiles.sha256(directory.resolve("corpus.json")))
                || !PROPERTIES_SHA256.equals(EvidenceFiles.sha256(directory.resolve("corpus.properties")))) {
            throw new IOException("T10 corpus identity mismatch");
        }
        expectations = PinProperties.load(directory.resolve("corpus.properties"), "T10 corpus");
        verifySources();
    }

    Path source(String name) throws IOException {
        return directory.resolve(expectations.required("sources." + name + ".path"));
    }

    String expected(String key) throws IOException {
        String value = expectations.optional(key);
        if (value == null) {
            throw new IOException("Missing frozen T10 expectation: " + key);
        }
        return value;
    }

    byte[] recolorCopiedContent(byte[] decoded) throws IOException {
        String program = new String(decoded, StandardCharsets.US_ASCII);
        String before = "1 1 0 rg 20 30 80 60 re f";
        int offset = program.indexOf(before);
        if (offset < 0 || program.indexOf(before, offset + before.length()) >= 0) {
            throw new IOException("T10 copy lacks its unique original background program");
        }
        return (program.substring(0, offset) + "0 1 1 rg 20 30 80 60 re f"
                + program.substring(offset + before.length())).getBytes(StandardCharsets.US_ASCII);
    }

    void verifySources() throws IOException {
        for (String name : new String[] {"primary", "appendix", "cover"}) {
            if (!expectations.requiredSha256("sources." + name + ".sha256").equals(EvidenceFiles.sha256(source(name)))) {
                throw new IOException("T10 Source identity mismatch: " + name);
            }
        }
    }
}
