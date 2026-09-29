package net.zerocloud.pdf.acceptance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
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
        return runBounded(executable, directory, timeoutMillis, 300000, maxOutputBytes, null, arguments);
    }

    /** Runs a repository-owned suite coordinator whose individual checks retain their own bounds. */
    static ProcessResult runCoordinator(Path executable, Path directory, long timeoutMillis, int maxOutputBytes,
            String... arguments) throws IOException, InterruptedException {
        Path privateDirectory = Files.createTempDirectory("folio-acceptance-private-");
        try {
            return runBounded(executable, directory, timeoutMillis, 900000, maxOutputBytes,
                    privateDirectory, arguments);
        } finally {
            // The Java owner also removes files when the coordinator cannot unwind.
            Files.walkFileTree(privateDirectory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path folder, IOException failure) throws IOException {
                    if (failure != null) { throw failure; }
                    Files.delete(folder);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static ProcessResult runBounded(Path executable, Path directory, long timeoutMillis,
            long maximumTimeoutMillis, int maxOutputBytes, Path privateDirectory, String... arguments)
            throws IOException, InterruptedException {
        if (timeoutMillis < 1 || timeoutMillis > maximumTimeoutMillis) {
            throw new IOException("External tool timeout must be between 1 and " + maximumTimeoutMillis + " ms");
        }
        if (maxOutputBytes < 1 || maxOutputBytes > 16 * 1024 * 1024) {
            throw new IOException("External tool output limit must be between 1 and 16777216 bytes");
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        String[] command = new String[arguments.length + 1];
        command[0] = executable.toString();
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile());
        boolean coordinator = privateDirectory != null;
        if (coordinator) { builder.environment().put("TMPDIR", privateDirectory.toString()); }
        Process process = builder.start();
        AtomicInteger remainingBytes = new AtomicInteger(maxOutputBytes);
        AtomicBoolean overflow = new AtomicBoolean();
        StreamCapture standardOutput = new StreamCapture(
                process.getInputStream(), process, remainingBytes, overflow, coordinator);
        StreamCapture standardError = new StreamCapture(
                process.getErrorStream(), process, remainingBytes, overflow, coordinator);
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
            while (!process.waitFor(Math.min(100, timeoutMillis), TimeUnit.MILLISECONDS)) {
                if (overflow.get()) { throw new LimitExceededException("External tool output limit exceeded"); }
                if (System.nanoTime() >= deadline) {
                    throw new LimitExceededException("External tool time limit exceeded");
                }
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
            if (coordinator) { stopCoordinator(process); }
            else { process.destroyForcibly(); }
        }
    }

    private static void stopCoordinator(Process process) throws IOException {
        boolean interrupted = Thread.interrupted();
        try {
            process.destroy();
            for (int phase = 0; phase < 2 && process.isAlive(); phase++) {
                if (phase == 1) { process.destroyForcibly(); }
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (process.isAlive() && System.nanoTime() < deadline) {
                    try { process.waitFor(100, TimeUnit.MILLISECONDS); }
                    catch (InterruptedException cancellation) { interrupted = true; }
                }
            }
            if (process.isAlive()) { throw new IOException("Acceptance coordinator did not terminate"); }
        } finally {
            if (interrupted) { Thread.currentThread().interrupt(); }
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
        private final boolean coordinator;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private IOException failure;

        StreamCapture(InputStream input, Process process, AtomicInteger remaining,
                AtomicBoolean overflow, boolean coordinator) {
            this.input = input;
            this.process = process;
            this.remaining = remaining;
            this.overflow = overflow;
            this.coordinator = coordinator;
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
                        if (coordinator) { process.destroy(); }
                        else { process.destroyForcibly(); }
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
