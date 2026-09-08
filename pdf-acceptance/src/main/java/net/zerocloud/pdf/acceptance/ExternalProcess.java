package net.zerocloud.pdf.acceptance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Captures one repository-controlled external-tool invocation. */
final class ExternalProcess {

    private ExternalProcess() {
    }

    static ProcessResult run(Path executable, Path directory, String... arguments)
            throws IOException, InterruptedException {
        return run(executable, directory, 30000, 4 * 1024 * 1024, arguments);
    }

    static ProcessResult run(Path executable, Path directory, long timeoutMillis, int maxOutputBytes,
            String... arguments) throws IOException, InterruptedException {
        if (timeoutMillis < 1 || timeoutMillis > 300000) {
            throw new IOException("External tool timeout must be between 1 and 300000 ms");
        }
        if (maxOutputBytes < 1 || maxOutputBytes > 16 * 1024 * 1024) {
            throw new IOException("External tool output limit must be between 1 and 16777216 bytes");
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        String[] command = new String[arguments.length + 1];
        command[0] = executable.toString();
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .start();
        AtomicInteger remainingBytes = new AtomicInteger(maxOutputBytes);
        AtomicBoolean overflow = new AtomicBoolean();
        StreamCapture standardOutput = new StreamCapture(
                process.getInputStream(), process, remainingBytes, overflow);
        StreamCapture standardError = new StreamCapture(
                process.getErrorStream(), process, remainingBytes, overflow);
        Thread outputThread = new Thread(
                standardOutput,
                "acceptance-tool-standard-output");
        Thread errorThread = new Thread(
                standardError,
                "acceptance-tool-standard-error");
        outputThread.setDaemon(true);
        errorThread.setDaemon(true);
        outputThread.start();
        errorThread.start();
        try {
            process.getOutputStream().close();
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                throw new LimitExceededException("External tool time limit exceeded");
            }
            joinBefore(outputThread, deadline);
            joinBefore(errorThread, deadline);
            if (overflow.get()) {
                throw new LimitExceededException("External tool output limit exceeded");
            }
            return new ProcessResult(process.exitValue(),
                    standardOutput.value(), standardError.value());
        } catch (LimitExceededException limit) {
            throw new LimitExceededException(limit.getMessage(),
                    standardOutput.snapshot() + "\n" + standardError.snapshot());
        } finally {
            process.destroyForcibly();
        }
    }

    private static void joinBefore(Thread thread, long deadline)
            throws InterruptedException, LimitExceededException {
        long remaining = deadline - System.nanoTime();
        if (remaining > 0) {
            thread.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
        }
        if (thread.isAlive()) {
            throw new LimitExceededException("External tool time limit exceeded while draining output");
        }
    }

    static final class LimitExceededException extends IOException {
        private static final long serialVersionUID = 1L;
        private final String retainedOutput;

        LimitExceededException(String message) {
            this(message, "");
        }

        LimitExceededException(String message, String retainedOutput) {
            super(message);
            this.retainedOutput = retainedOutput;
        }

        String retainedOutput() {
            return retainedOutput;
        }
    }

    private static final class StreamCapture implements Runnable {
        private final InputStream input;
        private final Process process;
        private final AtomicInteger remaining;
        private final AtomicBoolean overflow;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private IOException failure;

        StreamCapture(InputStream input, Process process, AtomicInteger remaining,
                AtomicBoolean overflow) {
            this.input = input;
            this.process = process;
            this.remaining = remaining;
            this.overflow = overflow;
        }

        @Override
        public void run() {
            try (InputStream stream = input) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) >= 0) {
                    int reserved;
                    int available;
                    do {
                        available = remaining.get();
                        reserved = Math.min(available, count);
                    } while (!remaining.compareAndSet(available, available - reserved));
                    output.write(buffer, 0, reserved);
                    if (reserved < count) {
                        overflow.set(true);
                        process.destroyForcibly();
                        break;
                    }
                }
            } catch (IOException captureFailure) {
                failure = captureFailure;
            }
        }

        String value() throws IOException {
            if (failure != null) {
                throw failure;
            }
            return snapshot();
        }

        String snapshot() {
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
