package net.zerocloud.pdf.consumer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.zip.GZIPOutputStream;
import net.zerocloud.pdf.PublicationReceipt;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowResourcePolicy;
import net.zerocloud.pdf.WorkflowResourceUsage;

/** Optional acceptance retention of the artifacts actually used by the scale tests. */
final class ScaleProfileEvidence {
    private final Path directory;
    private final Properties values = new Properties();
    private int outputs;

    private ScaleProfileEvidence(Path directory, String profile) {
        this.directory = directory;
        values.setProperty("profile", profile);
        values.setProperty("execution-profile", "HARDENED_WORKER");
    }

    static ScaleProfileEvidence open(String profile) throws IOException {
        String selected = System.getProperty("folio.pdf.t22.output");
        Path directory = selected == null ? null : Files.createDirectory(
                Paths.get(selected).toAbsolutePath().normalize().resolve(profile));
        return new ScaleProfileEvidence(directory, profile);
    }

    void observation(String name, Object value) { values.setProperty(name, String.valueOf(value)); }

    void commands(int count) throws IOException {
        StringBuilder commands = new StringBuilder();
        for (int index = 0; index < count; index++) { commands.append("AddBlankPage.version1\n"); }
        byte[] bytes = commands.toString().getBytes(StandardCharsets.US_ASCII);
        if (directory != null) { Files.write(directory.resolve("commands.txt"), bytes); }
        observation("input-construction", "declaration-ordered AddBlankPage.version1 commands");
        observation("input-command-count", count);
        observation("input-length", bytes.length);
        observation("input-sha256", hex(digest().digest(bytes)));
    }

    OutputStream inputArchive() throws IOException {
        return directory == null ? null : new GZIPOutputStream(
                Files.newOutputStream(directory.resolve("consumed-input.pdf.gz")), 65536);
    }

    void input(long length, String hash, long tailOffset, byte[] header, byte[] tail) throws IOException {
        observation("input-construction", "PDF header and one-page objects; newline fill; exact-offset xref tail");
        observation("input-length", length);
        observation("input-sha256", hash);
        observation("input-tail-offset", tailOffset);
        if (directory != null) {
            Files.write(directory.resolve("header.bin"), header);
            Files.write(directory.resolve("tail.bin"), tail);
        }
        observation("header-sha256", hex(digest().digest(header)));
        observation("tail-sha256", hex(digest().digest(tail)));
        observation("output-absence", "read-only; no Target declared; no PDF published");
    }

    void output(Path product, WorkflowOutcome<?> outcome, int reopenedPages) throws IOException {
        String prefix = "output." + outputs++ + ".";
        String name = product.getFileName().toString();
        if (directory != null) { Files.copy(product, directory.resolve(name)); }
        observation(prefix + "file", name);
        observation(prefix + "length", Files.size(product));
        observation(prefix + "sha256", hash(product));
        observation(prefix + "reopened-pages", reopenedPages);
        if (outcome.getPublicationReceipts().size() != 1) { throw new IOException("Scale receipt count changed"); }
        PublicationReceipt receipt = outcome.getPublicationReceipts().get(0);
        observation(prefix + "receipt", receipt.getTargetName() + ":" + receipt.getStatus()
                + ":" + receipt.isPartialOutputPossible());
        observation(prefix + "transaction", outcome.getTransactionId().get().getValue());
        usage(prefix, outcome.getResourceUsage());
    }

    void finish(WorkflowResourcePolicy policy, WorkflowResourceUsage usage, long wallNanos) throws IOException {
        observation("policy.input", policy.getMaximumInputBytes());
        observation("policy.pages", policy.getMaximumPages());
        observation("policy.objects", policy.getMaximumObjects());
        observation("policy.nesting", policy.getMaximumNestingDepth());
        observation("policy.decompressed", policy.getMaximumDecompressedBytes());
        observation("policy.pixels", policy.getMaximumDecodedPixels());
        observation("policy.memory", policy.getMaximumOwnedMemoryBytes());
        observation("policy.temporary", policy.getMaximumTemporaryStorageBytes());
        observation("policy.elapsed", policy.getMaximumElapsedTime());
        observation("policy.concurrency", policy.getMaximumConcurrentWorkflows());
        observation("output-count", outputs);
        observation("wall-nanos", wallNanos);
        observation("owned-storage-cleanup", "pass");
        observation("result", "pass");
        usage("usage.", usage);
        if (directory != null) {
            try (OutputStream output = Files.newOutputStream(directory.resolve("observations.properties"))) {
                values.store(output, "Actual T22 public scale observations; modeled Folio resources only");
            }
        }
    }

    private void usage(String prefix, WorkflowResourceUsage usage) {
        observation(prefix + "input", usage.getAcceptedInputBytes());
        observation(prefix + "pages", usage.getObservedPages());
        observation(prefix + "objects", usage.getObservedObjects());
        observation(prefix + "decompressed", usage.getDecompressedBytes());
        observation(prefix + "pixels", usage.getDecodedPixels());
        observation(prefix + "memory", usage.getPeakOwnedMemoryBytes());
        observation(prefix + "temporary", usage.getPeakTemporaryStorageBytes());
        observation(prefix + "elapsed", usage.getElapsedTime());
    }

    static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static String hash(Path path) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) >= 0) { digest.update(buffer, 0, count); }
        }
        return hex(digest.digest());
    }

    static String hex(byte[] bytes) {
        char[] encoded = new char[bytes.length * 2];
        char[] alphabet = "0123456789abcdef".toCharArray();
        for (int index = 0; index < bytes.length; index++) {
            encoded[index * 2] = alphabet[(bytes[index] & 255) >>> 4];
            encoded[index * 2 + 1] = alphabet[bytes[index] & 15];
        }
        return new String(encoded);
    }
}
