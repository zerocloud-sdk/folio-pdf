package net.zerocloud.pdf.tools.inventory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

final class FoundationHashes {
    private FoundationHashes() {
    }

    static String text(String value) {
        return hex(digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    static String file(Path file) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        return hex(digest.digest());
    }

    static boolean requireHash(String value, String at, List<String> errors) {
        if (!value.matches("[0-9a-f]{64}")) {
            errors.add(at + ": expected a SHA-256 identity");
            return false;
        }
        return true;
    }

    static Path verify(Path root, InventoryYaml reference, List<String> errors) {
        reference.keys("path", "sha256");
        String relative = reference.string("path");
        String expected = reference.string("sha256");
        Path file = RepositoryFileResolver.resolveForRead(root, relative);
        if (file == null) {
            errors.add(reference.location + ": missing evidence/artifact file " + relative);
        } else if (requireHash(expected, reference.location, errors)) {
            try {
                if (!expected.equals(file(file))) {
                    errors.add(reference.location + ": stale or mismatched artifact identity " + relative);
                }
            } catch (IOException exception) {
                errors.add(reference.location + ": cannot hash " + relative + ": " + exception.getMessage());
            }
        }
        return file;
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) {
            int number = value & 0xff;
            result.append(Character.forDigit(number >>> 4, 16));
            result.append(Character.forDigit(number & 15, 16));
        }
        return result.toString();
    }
}
