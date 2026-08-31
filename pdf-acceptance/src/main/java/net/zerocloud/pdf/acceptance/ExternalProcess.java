package net.zerocloud.pdf.acceptance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Captures one repository-controlled external-tool invocation. */
final class ExternalProcess {

    private ExternalProcess() {
    }

    static ProcessResult run(Path executable, Path directory, String... arguments)
            throws IOException, InterruptedException {
        String[] command = new String[arguments.length + 1];
        command[0] = executable.toString();
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .start();
        StreamCapture standardOutput = new StreamCapture(process.getInputStream());
        StreamCapture standardError = new StreamCapture(process.getErrorStream());
        Thread outputThread = new Thread(
                standardOutput,
                "acceptance-tool-standard-output");
        Thread errorThread = new Thread(
                standardError,
                "acceptance-tool-standard-error");
        outputThread.start();
        errorThread.start();
        int exitCode = process.waitFor();
        outputThread.join();
        errorThread.join();
        return new ProcessResult(
                exitCode,
                standardOutput.value(),
                standardError.value());
    }

    private static final class StreamCapture implements Runnable {
        private final InputStream input;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private IOException failure;

        StreamCapture(InputStream input) {
            this.input = input;
        }

        @Override
        public void run() {
            try (InputStream stream = input) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) >= 0) {
                    output.write(buffer, 0, count);
                }
            } catch (IOException captureFailure) {
                failure = captureFailure;
            }
        }

        String value() throws IOException {
            if (failure != null) {
                throw failure;
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}

/** Detached output from an external-tool invocation. */
final class ProcessResult {
    final int exitCode;
    final String standardOutput;
    final String standardError;

    ProcessResult(int exitCode, String standardOutput, String standardError) {
        this.exitCode = exitCode;
        this.standardOutput = standardOutput;
        this.standardError = standardError;
    }

    String combinedOutput() {
        return standardOutput + "\n" + standardError;
    }
}
