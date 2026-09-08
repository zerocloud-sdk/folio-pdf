package net.zerocloud.pdf.itext7.kernel.pdf;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.HashMap;
import java.util.Map;
import net.zerocloud.pdf.CancellationToken;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;

/** Owns one callback and marshals facade operations onto its confined thread. */
final class FacadeSession {
    interface Action<T> {
        T run(DocumentSession session) throws DocumentFailure;
    }

    interface View<T> {
        T read() throws DocumentFailure;
    }

    interface Mapping<T, R> {
        R map(T value, DocumentSession session) throws DocumentFailure;
    }

    private final Thread owner = Thread.currentThread();
    private final BlockingQueue<Call<?>> calls = new ArrayBlockingQueue<Call<?>>(1);
    private final CompletableFuture<Void> started = new CompletableFuture<Void>();
    private final CompletableFuture<WorkflowOutcome<Void>> outcome = new CompletableFuture<WorkflowOutcome<Void>>();
    private final Call<Void> finish = new Call<Void>(null);
    private final CancellationToken cancellation;
    private final Thread worker;
    private volatile boolean stopRequested;
    private final Map<ObjectReference, PdfIndirectReference> references = new HashMap<ObjectReference, PdfIndirectReference>();

    PdfIndirectReference referenceFor(ObjectReference reference) {
        PdfIndirectReference mapped = references.get(reference);
        if (mapped == null) {
            mapped = new PdfIndirectReference(this, reference);
            references.put(reference, mapped);
        }
        return mapped;
    }

    FacadeSession(WorkflowRequest request, int initialPages, CancellationToken cancellation) {
        this.cancellation = cancellation;
        worker = new Thread(() -> run(request, initialPages), "folio-pdf-facade-session");
        worker.setDaemon(true);
        worker.start();
    }

    <T> T call(Action<T> action) {
        requireOwner();
        if (stopRequested) {
            await(outcome);
            throw new IllegalStateException("The facade document is closed.");
        }
        await(started);
        if (outcome.isDone()) {
            await(outcome);
            throw new IllegalStateException("The facade document is closed.");
        }
        Call<T> call = new Call<T>(action);
        calls.add(call);
        try {
            await(CompletableFuture.anyOf(call.result, outcome));
            return await(call.result);
        } finally {
            calls.remove(call);
        }
    }

    <T> T view(View<T> read) {
        return view(read, (value, session) -> value);
    }

    <T, R> R view(View<T> read, Mapping<T, R> mapping) {
        requireOwner();
        if (!stopRequested) {
            return call(session -> mapping.map(read.read(), session));
        }
        await(outcome);
        try {
            // The actual public Native read reports expiry without retaining
            // a Session or attempting post-close mapping or dereferencing.
            read.read();
        } catch (DocumentFailure failure) {
            throw mapped(failure);
        }
        throw new IllegalStateException("The facade document is closed.");
    }

    WorkflowOutcome<Void> close() {
        requireOwner();
        requestStop(Thread.currentThread().isInterrupted());
        return await(outcome);
    }

    private void run(WorkflowRequest request, int initialPages) {
        try {
            WorkflowOutcome<Void> completed = new DocumentWorkflow().execute(request, session -> {
                for (int page = 0; page < initialPages; page++) {
                    session.execute(AddBlankPage.INSTANCE);
                }
                started.complete(null);
                while (!stopRequested) {
                    Call<?> next;
                    try {
                        next = calls.take();
                    } catch (InterruptedException interrupted) {
                        requestStop(true);
                        break;
                    }
                    if (next == finish) {
                        return null;
                    }
                    next.run(session);
                    next = null;
                }
                return null;
            });
            outcome.complete(completed);
        } catch (Throwable failure) {
            started.completeExceptionally(failure);
            outcome.completeExceptionally(failure);
        } finally {
            calls.clear();
            references.clear();
        }
    }

    private <T> T await(CompletableFuture<T> future) {
        boolean interrupted = Thread.interrupted();
        if (interrupted) {
            requestStop(true);
        }
        try {
            while (true) {
                try {
                    if (interrupted) {
                        // Publication and failure receipts belong to the actual
                        // Native outcome, including cancellation during startup.
                        outcome.get();
                        if (!future.isDone()) {
                            throw new IllegalStateException("The facade document is closed.");
                        }
                    }
                    return future.get();
                } catch (InterruptedException cancellationRequest) {
                    interrupted = true;
                    requestStop(true);
                }
            }
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof DocumentFailure) {
                throw mapped((DocumentFailure) cause);
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new PdfException("The facade workflow could not be completed.", null);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void requestStop(boolean cancel) {
        if (cancel) {
            cancellation.cancel();
        }
        stopRequested = true;
        calls.offer(finish);
        if (cancel && Thread.currentThread() != worker) {
            worker.interrupt();
        }
    }

    private static PdfException mapped(DocumentFailure failure) {
        return new PdfException(failure.getCode().name() + ": " + failure.getDiagnostic(), failure);
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("The facade document is thread-confined.");
        }
    }

    private static final class Call<T> {
        private final Action<T> action;
        private final CompletableFuture<T> result = new CompletableFuture<T>();

        Call(Action<T> action) {
            this.action = action;
        }

        void run(DocumentSession session) {
            try {
                result.complete(action.run(session));
            } catch (DocumentFailure | RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        }
    }
}
