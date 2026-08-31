package net.zerocloud.pdf.tools.release;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class ReleaseReproducibility {

    Comparison compare(Path directory) {
        List<String> errors = new ArrayList<String>();
        Map<String, byte[]> first = read(directory.resolve("central-bundle-a.zip"), errors);
        Map<String, byte[]> second = read(directory.resolve("central-bundle-b.zip"), errors);
        Set<String> union = new HashSet<String>();
        union.addAll(first.keySet());
        union.addAll(second.keySet());
        List<String> names = new ArrayList<String>(union);
        Collections.sort(names);

        StringBuilder details = new StringBuilder();
        for (String name : names) {
            if (name.contains(".asc")) {
                details.append("EXCLUDED\t").append(name).append('\n');
                continue;
            }
            byte[] left = first.get(name);
            byte[] right = second.get(name);
            if (left == null || right == null) {
                details.append("MISMATCH\tmissing-in-")
                        .append(left == null ? "first" : "second")
                        .append('\t').append(name).append('\n');
                errors.add("reproducibility comparison is missing deterministic entry " + name);
            } else {
                String leftHash = sha256(left, errors);
                String rightHash = sha256(right, errors);
                if (!leftHash.equals(rightHash)) {
                    details.append("MISMATCH\t").append(leftHash).append(" != ")
                            .append(rightHash).append('\t').append(name).append('\n');
                    errors.add("reproducibility mismatch for " + name);
                } else {
                    details.append("MATCH\t").append(leftHash).append('\t')
                            .append(name).append('\n');
                }
            }
        }

        StringBuilder report = new StringBuilder();
        report.append("format-version=1\nresult=")
                .append(errors.isEmpty() ? "PASS" : "FAIL").append('\n');
        report.append("excluded-reason=OpenPGP signatures contain randomized or "
                + "time-dependent packet material; signatures and their derived checksums "
                + "are cryptographically validated separately.\n");
        report.append(details);
        try {
            Files.write(directory.resolve("reproducibility.txt"),
                    report.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            errors.add("cannot write reproducibility.txt: " + compact(e.getMessage()));
        }
        return new Comparison(errors, names.size());
    }

    private Map<String, byte[]> read(Path path, List<String> errors) {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        if (!Files.isRegularFile(path)) {
            errors.add(path.getFileName() + " is required for reproducibility comparison");
            return entries;
        }
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(path))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.startsWith("/") || name.contains("../")
                        || entries.containsKey(name)) {
                    errors.add("unsafe or duplicate reproducibility entry " + name);
                    continue;
                }
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    if (content.size() + count > 128 * 1024 * 1024) {
                        throw new IOException("entry exceeds comparison size limit");
                    }
                    content.write(buffer, 0, count);
                }
                entries.put(name, content.toByteArray());
            }
        } catch (IOException e) {
            errors.add("cannot read " + path.getFileName() + ": " + compact(e.getMessage()));
        }
        return entries;
    }

    private static String sha256(byte[] content, List<String> errors) {
        try {
            return ReleaseChecksums.digestHex("SHA-256", content);
        } catch (IllegalStateException e) {
            errors.add("JDK does not provide SHA-256");
            return "";
        }
    }

    private static String compact(String value) {
        return value == null ? "unknown error"
                : value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    static final class Comparison {
        final List<String> errors;
        final int comparedEntries;

        private Comparison(List<String> errors, int comparedEntries) {
            this.errors = Collections.unmodifiableList(new ArrayList<String>(errors));
            this.comparedEntries = comparedEntries;
        }

        boolean isReproducible() {
            return errors.isEmpty();
        }
    }
}
