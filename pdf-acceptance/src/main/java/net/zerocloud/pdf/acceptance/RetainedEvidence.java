package net.zerocloud.pdf.acceptance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/** Binds retained files to bytes observed by their producer, before later aggregation. */
final class RetainedEvidence {
    private final Path root;
    private final Map<Path, String> hashes = new TreeMap<Path, String>();

    RetainedEvidence(Path root) { this.root = root.toAbsolutePath().normalize(); }

    void retain(Path path, String expectedHash) throws IOException {
        Path file = path.toAbsolutePath().normalize();
        if (!file.startsWith(root) || file.equals(root)) {
            throw new IOException("Retained evidence is outside its output directory");
        }
        String prior = hashes.put(file, expectedHash);
        if (prior != null && !prior.equals(expectedHash)) {
            throw new IOException("Retained evidence was replaced: " + file);
        }
    }

    void write(Path path, String contents) throws IOException {
        writeBytes(path, contents.getBytes(StandardCharsets.UTF_8));
    }

    void write(Path path, byte[] contents) throws IOException {
        writeBytes(path, contents);
    }

    void write(Path path, Properties values) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        values.store(bytes, "Actual independent observation");
        writeBytes(path, bytes.toByteArray());
    }

    private void writeBytes(Path path, byte[] contents) throws IOException {
        retain(path, EvidenceFiles.sha256(contents));
        requireNoLinks(path.toAbsolutePath().normalize());
        Files.write(path, contents, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    void include(RetainedEvidence other) throws IOException {
        for (Map.Entry<Path, String> entry : other.hashes.entrySet()) {
            retain(entry.getKey(), entry.getValue());
        }
    }

    void verify() throws IOException {
        for (Map.Entry<Path, String> entry : hashes.entrySet()) {
            Path file = entry.getKey();
            requireNoLinks(file);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || !entry.getValue().equals(EvidenceFiles.sha256(file))) {
                throw new IOException("Retained evidence is missing or changed: " + file);
            }
        }
    }

    private void requireNoLinks(Path file) throws IOException {
        for (Path ancestor = file; ancestor != null; ancestor = ancestor.getParent()) {
            if (Files.isSymbolicLink(ancestor)) { throw new IOException("Retained evidence became a symbolic link: " + file); }
        }
    }

    String publishManifest(String determination) throws IOException {
        String contents = manifest();
        write(root.resolve("retained-files.sha256"), contents);
        if (!"indeterminate".equals(determination)) { verify(); }
        return EvidenceFiles.sha256(contents);
    }

    private String manifest() {
        StringBuilder contents = new StringBuilder();
        for (Map.Entry<Path, String> entry : hashes.entrySet()) {
            contents.append(entry.getValue()).append("  ")
                    .append(root.relativize(entry.getKey()).toString().replace('\\', '/')).append('\n');
        }
        return contents.toString();
    }
}
