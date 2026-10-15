package net.zerocloud.pdf.acceptance;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/** Project-authored acceptance-only process; never included in a product runtime. */
public final class T05ProcessFixture {
    private T05ProcessFixture() { }

    public static void main(String[] arguments) throws Exception {
        String mode = arguments[0];
        Path observation = Paths.get(arguments[1]);
        Path staging = Paths.get(System.getenv("FOLIO_PDF_PROVIDER_STAGING"));
        Properties actual = new Properties();
        actual.setProperty("pid", ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
        actual.setProperty("mode", mode);
        actual.setProperty("staging", staging.toString());
        actual.setProperty("java-version", System.getProperty("java.runtime.version"));
        try (java.io.OutputStream output = Files.newOutputStream(observation)) { actual.store(output, "Actual process boundary"); }
        Files.write(staging.resolve("owned-marker"), new byte[] {9, 8, 7});
        if ("hold-pipe".equals(mode)) {
            // The descendant is deliberately outside the adapter's direct-child guarantee.
            // It releases the inherited descriptors after the observed per-run cleanup.
            for (int count = 0; count < 500 && Files.exists(staging); count++) { Thread.sleep(10L); }
            return;
        }
        if ("stalled-input".equals(mode)) { Thread.sleep(10000L); return; }
        DataInputStream input = new DataInputStream(System.in);
        if (input.readInt() != 0x4f504451 || input.readInt() != 1) { System.exit(65); }
        String capability = input.readUTF();
        long length = input.readLong();
        if (length < 0 || length > 1048576) { System.exit(66); }
        byte[] payload = new byte[(int) length]; input.readFully(payload);
        actual.setProperty("capability", capability);
        actual.setProperty("input-length", Long.toString(length));
        actual.setProperty("input-sha256", EvidenceFiles.sha256(payload));
        try (java.io.OutputStream output = Files.newOutputStream(observation)) { actual.store(output, "Actual process boundary"); }
        if ("nonzero".equals(mode)) { System.exit(23); }
        if ("crash".equals(mode)) { Runtime.getRuntime().halt(24); }
        if ("stalled-output".equals(mode)) { Thread.sleep(10000L); return; }
        if ("inherited-pipe".equals(mode)) {
            new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
                    System.getProperty("java.class.path"), T05ProcessFixture.class.getName(), "hold-pipe",
                    arguments[1] + ".descendant.properties")
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT).start();
            return;
        }
        java.io.ByteArrayOutputStream frame = new java.io.ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(frame);
        output.writeInt("wrong-magic".equals(mode) ? 0x12345678 : 0x4f504452);
        if (!"truncated-header".equals(mode)) {
            output.writeInt("wrong-version".equals(mode) ? 2 : 1);
            output.writeLong("negative-length".equals(mode) ? -1 : "oversized-length".equals(mode) ? 5 : payload.length);
            if (!"negative-length".equals(mode) && !"oversized-length".equals(mode)) {
                output.write(payload, 0, "truncated-payload".equals(mode) ? payload.length - 1 : payload.length);
            }
            if ("trailing-output".equals(mode)) { output.write(99); }
        }
        output.flush();
        Files.write(Paths.get(arguments[1] + ".response.bin"), frame.toByteArray());
        System.out.write(frame.toByteArray()); System.out.flush();
    }
}
