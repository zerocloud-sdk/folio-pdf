package net.zerocloud.pdf.conversion;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ProviderProcessFixture {

    private static final int REQUEST_MAGIC = 0x4f504451;
    private static final int RESPONSE_MAGIC = 0x4f504452;
    private static final int PROTOCOL_VERSION = 1;

    private ProviderProcessFixture() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            System.exit(64);
        }
        String staging = System.getenv("OPEN_PDF_PROVIDER_STAGING");
        if (staging != null) {
            Path marker = Paths.get(staging).resolve("fixture-marker");
            Files.write(marker, "fixture".getBytes(StandardCharsets.UTF_8));
        }
        if ("hold-stdout".equals(arguments[0])) {
            Path stagingPath = staging == null ? null : Paths.get(staging);
            for (int attempt = 0;
                    attempt < 200
                            && stagingPath != null
                            && Files.exists(stagingPath);
                    attempt++) {
                Thread.sleep(10L);
            }
            return;
        }

        DataInputStream input = new DataInputStream(System.in);
        if (input.readInt() != REQUEST_MAGIC
                || input.readInt() != PROTOCOL_VERSION) {
            System.exit(65);
        }
        input.readUTF();
        long length = input.readLong();
        if (length < 0L || length > Integer.MAX_VALUE) {
            System.exit(66);
        }
        byte[] payload = new byte[(int) length];
        input.readFully(payload);

        if ("oversized-output".equals(arguments[0])) {
            writeResponse(new byte[1024]);
            Thread.sleep(2000L);
            return;
        }
        if ("sleep".equals(arguments[0])) {
            Thread.sleep(2000L);
            writeResponse(payload);
            return;
        }
        if ("exit-with-inherited-stdout".equals(arguments[0])) {
            ProcessBuilder helper = new ProcessBuilder(
                    javaExecutable().toString(),
                    "-cp",
                    System.getProperty("java.class.path"),
                    ProviderProcessFixture.class.getName(),
                    "hold-stdout");
            helper.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            helper.redirectError(ProcessBuilder.Redirect.INHERIT);
            if (staging != null) {
                helper.directory(Paths.get(staging).getParent().toFile());
            }
            helper.start();
            return;
        }
        if ("nonzero".equals(arguments[0])) {
            System.exit(23);
        }
        if ("crash".equals(arguments[0])) {
            Runtime.getRuntime().halt(24);
        }
        if ("malformed".equals(arguments[0])) {
            DataOutputStream output = new DataOutputStream(System.out);
            output.writeInt(0x12345678);
            output.flush();
            return;
        }
        if (!"echo".equals(arguments[0])) {
            System.exit(64);
        }
        writeResponse(payload);
    }

    private static void writeResponse(byte[] payload) throws Exception {
        DataOutputStream output = new DataOutputStream(System.out);
        output.writeInt(RESPONSE_MAGIC);
        output.writeInt(PROTOCOL_VERSION);
        output.writeLong(payload.length);
        output.write(payload);
        output.flush();
    }

    private static Path javaExecutable() {
        String executable = System.getProperty("os.name")
                .toLowerCase()
                .contains("win") ? "java.exe" : "java";
        return Paths.get(System.getProperty("java.home"), "bin", executable);
    }
}
