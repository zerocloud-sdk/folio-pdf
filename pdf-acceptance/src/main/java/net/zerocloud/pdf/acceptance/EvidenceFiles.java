package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** File, hash, and Markdown helpers shared by repository evidence chains. */
final class EvidenceFiles {

    private static final Pattern PDF_DOCUMENT_ID = Pattern.compile(
            "/ID\\s*\\[\\s*<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>\\s*\\]");
    private static final String INPUT_HASH_POLICY =
            "SHA-256 of the exact PDF bytes after replacing only the two "
                    + "hexadecimal trailer /ID values with ASCII zeroes";
    private static final String REVISION_INPUT_HASH_POLICY =
            "SHA-256 of the exact PDF bytes after replacing every hexadecimal "
                    + "two-value trailer /ID with equal-length ASCII zeroes";

    private EvidenceFiles() {
    }
    static String sha256(Path path) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, count);
            }
        }
        return hex(digest.digest());
    }

    static String sha256(String value) {
        MessageDigest digest = sha256Digest();
        return hex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    static String sha256(byte[] value) {
        return hex(sha256Digest().digest(value));
    }

    /**
     * Hashes a freshly created acceptance PDF while excluding its non-semantic
     * trailer identifier values. All other bytes retain their exact positions
     * and values, and the unmodified file remains the tool input.
     */
    static String idNeutralPdfSha256(Path path) throws IOException {
        return idNeutralPdfSha256(path, IdentifierCount.EXACTLY_ONE);
    }

    static String inputHashPolicy() {
        return INPUT_HASH_POLICY;
    }

    static String revisionIdNeutralPdfSha256(Path path) throws IOException {
        return idNeutralPdfSha256(path, IdentifierCount.AT_LEAST_ONE);
    }

    private static String idNeutralPdfSha256(
            Path path,
            IdentifierCount requiredCount) throws IOException {
        byte[] normalized = Files.readAllBytes(path);
        String pdf = new String(normalized, StandardCharsets.ISO_8859_1);
        Matcher identifiers = PDF_DOCUMENT_ID.matcher(pdf);
        int matches = 0;
        while (identifiers.find()) {
            zeroHexadecimal(normalized, identifiers.start(1), identifiers.end(1));
            zeroHexadecimal(normalized, identifiers.start(2), identifiers.end(2));
            matches++;
        }
        if (!requiredCount.accepts(matches)) {
            throw new IOException(requiredCount.diagnostic);
        }
        return hex(sha256Digest().digest(normalized));
    }

    static String revisionInputHashPolicy() {
        return REVISION_INPUT_HASH_POLICY;
    }

    static void write(Path path, String value) throws IOException {
        Files.write(path,
                value.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    static String metadata(String label, String value) {
        return label + ": `" + value + "`\n\n";
    }

    static String fencedEnding(String value) {
        return value.endsWith("\n") ? "```\n\n" : "\n```\n\n";
    }

    static String finalFencedEnding(String value) {
        return value.endsWith("\n") ? "```\n" : "\n```\n";
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void zeroHexadecimal(byte[] bytes, int start, int end)
            throws IOException {
        if (start == end || (end - start) % 2 != 0) {
            throw new IOException("Acceptance PDF trailer /ID is malformed");
        }
        for (int index = start; index < end; index++) {
            bytes[index] = (byte) '0';
        }
    }

    private static String hex(byte[] digest) {
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }

    private enum IdentifierCount {
        EXACTLY_ONE(
                "Acceptance PDF must contain exactly one two-value trailer /ID") {
            @Override
            boolean accepts(int count) {
                return count == 1;
            }
        },
        AT_LEAST_ONE("Acceptance PDF must contain a two-value trailer /ID") {
            @Override
            boolean accepts(int count) {
                return count >= 1;
            }
        };

        private final String diagnostic;

        IdentifierCount(String diagnostic) {
            this.diagnostic = diagnostic;
        }

        abstract boolean accepts(int count);
    }
}
