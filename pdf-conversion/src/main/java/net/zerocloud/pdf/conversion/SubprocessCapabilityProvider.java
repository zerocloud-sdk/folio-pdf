package net.zerocloud.pdf.conversion;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import net.zerocloud.pdf.provider.CapabilityProvider;
import net.zerocloud.pdf.provider.ProviderExecutionMode;
import net.zerocloud.pdf.provider.ProviderFailure;
import net.zerocloud.pdf.provider.ProviderFailureCode;
import net.zerocloud.pdf.provider.ProviderMetadata;
import net.zerocloud.pdf.provider.ProviderRequest;
import net.zerocloud.pdf.provider.ProviderResult;

/**
 * A local subprocess Capability Provider using a versioned, length-bounded
 * stdin/stdout protocol and one private staging directory per invocation.
 *
 * <p>The fixed command is passed directly to {@link ProcessBuilder} without
 * shell expansion. Protocol version 1 frames stdin with {@code OPDQ}, version,
 * a {@link DataOutputStream#writeUTF(String) writeUTF} capability ID, a signed
 * 64-bit length, and bounded payload bytes. Stdout returns {@code OPDR},
 * version, a signed 64-bit length, exact bounded payload bytes, and EOF. Stderr
 * is discarded. Startup, exit, crash, malformed output, byte-limit, and
 * deadline failures are normalized; direct-child termination is confirmed;
 * and process streams and per-run staging are cleaned after every tested
 * exit. A failure to confirm termination or remove owned staging becomes a
 * stable execution failure.</p>
 *
 * <p>This adapter is not the Hardened Worker Profile and does not claim hard
 * memory, CPU, filesystem, network, descendant process-tree, or hostile-input
 * isolation.</p>
 */
public final class SubprocessCapabilityProvider extends CapabilityProvider {

    private static final int REQUEST_MAGIC = 0x4f504451;
    private static final int RESPONSE_MAGIC = 0x4f504452;
    private static final int PROTOCOL_VERSION = 1;
    private static final String STAGING_ENVIRONMENT =
            "FOLIO_PDF_PROVIDER_STAGING";

    private final List<String> command;
    private final Path stagingRoot;

    /**
     * Creates a bounded subprocess Provider registration.
     *
     * @param metadata immutable SUBPROCESS metadata
     * @param command fixed executable and arguments, with no shell expansion
     * @param stagingRoot caller-selected root for library-owned per-run staging
     */
    public SubprocessCapabilityProvider(
            ProviderMetadata metadata,
            List<String> command,
            Path stagingRoot) {
        super(requireSubprocess(metadata));
        Objects.requireNonNull(command, "command");
        if (command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        List<String> copied = new ArrayList<String>(command.size());
        for (String argument : command) {
            String required = Objects.requireNonNull(argument, "command argument");
            if (required.isEmpty()) {
                throw new IllegalArgumentException(
                        "command arguments must not be empty");
            }
            copied.add(required);
        }
        this.command = Collections.unmodifiableList(copied);
        this.stagingRoot = Objects.requireNonNull(
                stagingRoot,
                "stagingRoot").toAbsolutePath().normalize();
    }

    @Override
    protected ProviderResult perform(ProviderRequest request)
            throws ProviderFailure {
        long started = System.nanoTime();
        long timeoutNanos = durationNanos(request);
        Path staging = null;
        Process process = null;
        ExecutorService ioExecutor = null;
        Future<Void> requestWrite = null;
        Future<ProviderResult> resultRead = null;
        Future<Void> errorDrain = null;
        try {
            Files.createDirectories(stagingRoot);
            staging = Files.createTempDirectory(stagingRoot, ".folio-pdf-provider-");
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(staging.toFile());
            builder.environment().put(STAGING_ENVIRONMENT, staging.toString());
            process = start(builder, request);
            ioExecutor = Executors.newFixedThreadPool(3, daemonThreadFactory());
            final Process runningProcess = process;
            requestWrite = ioExecutor.submit(new Callable<Void>() {
                @Override
                public Void call() throws ProviderFailure {
                    writeRequest(runningProcess.getOutputStream(), request);
                    return null;
                }
            });
            resultRead = ioExecutor.submit(new Callable<ProviderResult>() {
                @Override
                public ProviderResult call() throws ProviderFailure {
                    return readResult(runningProcess.getInputStream(), request);
                }
            });
            errorDrain = ioExecutor.submit(new Callable<Void>() {
                @Override
                public Void call() {
                    drain(runningProcess.getErrorStream());
                    return null;
                }
            });
            return awaitCompletion(
                    process,
                    requestWrite,
                    resultRead,
                    request,
                    started,
                    timeoutNanos);
        } catch (ProviderFailure failure) {
            throw failure;
        } catch (IOException failure) {
            throw failure(ProviderFailureCode.EXECUTION_FAILED, request);
        } finally {
            boolean terminated = terminate(process);
            closeProcessStreams(process);
            cancel(requestWrite);
            cancel(resultRead);
            cancel(errorDrain);
            boolean cleaned = deleteRecursively(staging);
            shutdown(ioExecutor);
            if (!terminated || !cleaned) {
                throw failure(ProviderFailureCode.EXECUTION_FAILED, request);
            }
        }
    }

    private Process start(ProcessBuilder builder, ProviderRequest request)
            throws ProviderFailure {
        try {
            return builder.start();
        } catch (IOException | RuntimeException failure) {
            throw failure(ProviderFailureCode.STARTUP_FAILED, request);
        }
    }

    private void writeRequest(
            OutputStream output,
            ProviderRequest request) throws ProviderFailure {
        try (DataOutputStream data = new DataOutputStream(output)) {
            byte[] payload = request.getInput();
            data.writeInt(REQUEST_MAGIC);
            data.writeInt(PROTOCOL_VERSION);
            data.writeUTF(request.getCapabilityId());
            data.writeLong(payload.length);
            data.write(payload);
            data.flush();
        } catch (IOException failure) {
            throw failure(ProviderFailureCode.EXECUTION_FAILED, request);
        }
    }

    private ProviderResult readResult(
            InputStream input,
            ProviderRequest request) throws ProviderFailure {
        try (DataInputStream data = new DataInputStream(input)) {
            if (data.readInt() != RESPONSE_MAGIC
                    || data.readInt() != PROTOCOL_VERSION) {
                throw failure(ProviderFailureCode.MALFORMED_OUTPUT, request);
            }
            long length = data.readLong();
            if (length < 0L) {
                throw failure(ProviderFailureCode.MALFORMED_OUTPUT, request);
            }
            if (length > getMetadata().getLimits().getMaximumOutputBytes()) {
                throw failure(ProviderFailureCode.OUTPUT_LIMIT_EXCEEDED, request);
            }
            if (length > Integer.MAX_VALUE) {
                throw failure(ProviderFailureCode.OUTPUT_LIMIT_EXCEEDED, request);
            }
            byte[] payload = new byte[(int) length];
            data.readFully(payload);
            if (data.read() != -1) {
                throw failure(ProviderFailureCode.MALFORMED_OUTPUT, request);
            }
            return ProviderResult.of(payload);
        } catch (EOFException failure) {
            throw failure(ProviderFailureCode.MALFORMED_OUTPUT, request);
        } catch (IOException failure) {
            throw failure(ProviderFailureCode.EXECUTION_FAILED, request);
        }
    }

    private ProviderResult awaitCompletion(
            Process process,
            Future<Void> requestWrite,
            Future<ProviderResult> resultRead,
            ProviderRequest request,
            long started,
            long timeoutNanos) throws ProviderFailure {
        ProviderResult completedResult = null;
        boolean resultCompleted = false;
        while (true) {
            long remaining = remainingNanos(started, timeoutNanos);
            if (remaining <= 0L) {
                throw failure(ProviderFailureCode.DEADLINE_EXCEEDED, request);
            }
            try {
                if (process.waitFor(
                        Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(10L)),
                        TimeUnit.NANOSECONDS)) {
                    if (process.exitValue() != 0) {
                        throw failure(
                                ProviderFailureCode.EXECUTION_FAILED,
                                request);
                    }
                    await(requestWrite, request, started, timeoutNanos);
                    return resultCompleted
                            ? completedResult
                            : await(resultRead, request, started, timeoutNanos);
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw failure(ProviderFailureCode.EXECUTION_FAILED, request);
            }

            if (!resultCompleted && resultRead.isDone()) {
                try {
                    completedResult = await(
                            resultRead,
                            request,
                            started,
                            timeoutNanos);
                    resultCompleted = true;
                } catch (ProviderFailure resultFailure) {
                    boolean exitedNonzero;
                    if (resultFailure.getCode()
                            == ProviderFailureCode.MALFORMED_OUTPUT) {
                        exitedNonzero = awaitNonzeroExit(
                                process,
                                started,
                                timeoutNanos,
                                request);
                    } else {
                        exitedNonzero = observeNonzeroExit(
                                process,
                                remaining,
                                request);
                    }
                    if (exitedNonzero) {
                        throw failure(
                                ProviderFailureCode.EXECUTION_FAILED,
                                request);
                    }
                    if (remainingNanos(started, timeoutNanos) <= 0L) {
                        throw failure(
                                ProviderFailureCode.DEADLINE_EXCEEDED,
                                request);
                    }
                    throw resultFailure;
                }
            }
            if (requestWrite.isDone()) {
                await(requestWrite, request, started, timeoutNanos);
            }
        }
    }

    private boolean observeNonzeroExit(
            Process process,
            long remainingNanos,
            ProviderRequest request) throws ProviderFailure {
        long observation = Math.min(
                remainingNanos,
                TimeUnit.MILLISECONDS.toNanos(100L));
        try {
            return process.waitFor(observation, TimeUnit.NANOSECONDS)
                    && process.exitValue() != 0;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw failure(ProviderFailureCode.EXECUTION_FAILED, request);
        }
    }

    /**
     * Waits up to the remaining request deadline for the process exit status
     * to arbitrate malformed or truncated output. Pipe EOF reaches the reader
     * as soon as the kernel closes the process streams, while the exit status
     * only becomes visible once the process reaper runs, so a short fixed
     * observation window misclassifies crashes as malformed output.
     *
     * @return true when the process exited with a nonzero status
     */
    private boolean awaitNonzeroExit(
            Process process,
            long started,
            long timeoutNanos,
            ProviderRequest request) throws ProviderFailure {
        long remaining = remainingNanos(started, timeoutNanos);
        try {
            return process.waitFor(Math.max(remaining, 0L), TimeUnit.NANOSECONDS)
                    && process.exitValue() != 0;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw failure(ProviderFailureCode.EXECUTION_FAILED, request);
        }
    }

    private static ProviderMetadata requireSubprocess(ProviderMetadata metadata) {
        ProviderMetadata required = Objects.requireNonNull(metadata, "metadata");
        if (required.getExecutionMode() != ProviderExecutionMode.SUBPROCESS) {
            throw new IllegalArgumentException(
                    "SubprocessCapabilityProvider requires SUBPROCESS metadata");
        }
        return required;
    }

    private ProviderFailure failure(
            ProviderFailureCode code,
            ProviderRequest request) {
        return ProviderFailure.forProvider(
                code,
                getMetadata().getProviderId(),
                request.getCapabilityId());
    }

    private static boolean terminate(Process process) {
        if (process == null) {
            return true;
        }
        try {
            if (!process.isAlive()) {
                return true;
            }
            process.destroy();
            if (process.waitFor(100L, TimeUnit.MILLISECONDS)) {
                return true;
            }
            process.destroyForcibly();
            return process.waitFor(1L, TimeUnit.SECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            try {
                process.destroyForcibly();
            } catch (RuntimeException ignored) {
                // The stable lifecycle failure below remains authoritative.
            }
            return false;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static ThreadFactory daemonThreadFactory() {
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable task) {
                Thread thread = new Thread(
                        task,
                        "folio-pdf-provider-subprocess-io");
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    private static void drain(InputStream input) {
        byte[] buffer = new byte[4096];
        try {
            while (input.read(buffer) != -1) {
                // Diagnostics are intentionally discarded to avoid data leakage.
            }
        } catch (IOException | RuntimeException ignored) {
            // Process termination commonly closes this stream asynchronously.
        }
    }

    private <T> T await(
            Future<T> future,
            ProviderRequest request,
            long started,
            long timeoutNanos)
            throws ProviderFailure {
        long remaining = remainingNanos(started, timeoutNanos);
        if (remaining <= 0L) {
            throw failure(ProviderFailureCode.DEADLINE_EXCEEDED, request);
        }
        try {
            return future.get(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw failure(ProviderFailureCode.EXECUTION_FAILED, request);
        } catch (TimeoutException failure) {
            throw failure(ProviderFailureCode.DEADLINE_EXCEEDED, request);
        } catch (CancellationException failure) {
            throw failure(ProviderFailureCode.EXECUTION_FAILED, request);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof ProviderFailure) {
                throw (ProviderFailure) cause;
            }
            throw failure(ProviderFailureCode.EXECUTION_FAILED, request);
        }
    }

    private static long durationNanos(ProviderRequest request) {
        try {
            return request.getTimeout().toNanos();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static long remainingNanos(long started, long timeoutNanos) {
        long elapsed = System.nanoTime() - started;
        if (elapsed < 0L) {
            return 0L;
        }
        return timeoutNanos - elapsed;
    }

    private static void cancel(Future<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }

    private static void shutdown(ExecutorService executor) {
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        try {
            executor.awaitTermination(200L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeProcessStreams(Process process) {
        if (process == null) {
            return;
        }
        closeQuietly(process.getInputStream());
        closeQuietly(process.getErrorStream());
        closeQuietly(process.getOutputStream());
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException | RuntimeException ignored) {
            // The stable primary result or failure remains authoritative.
        }
    }

    private static boolean deleteRecursively(Path root) {
        if (root == null || Files.notExists(root)) {
            return true;
        }
        boolean cleaned = true;
        try (Stream<Path> paths = Files.walk(root)) {
            Iterator<Path> ordered = paths.sorted(Comparator.reverseOrder())
                    .iterator();
            while (ordered.hasNext()) {
                Path path = ordered.next();
                try {
                    Files.deleteIfExists(path);
                } catch (IOException | RuntimeException ignored) {
                    cleaned = false;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
        return cleaned;
    }
}
