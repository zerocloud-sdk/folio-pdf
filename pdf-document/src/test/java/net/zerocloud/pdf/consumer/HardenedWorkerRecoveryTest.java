package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.zerocloud.pdf.CancellationToken;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowProgressPhase;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.WorkflowTransactionId;
import net.zerocloud.pdf.WorkflowTransactionPolicy;
import net.zerocloud.pdf.WorkflowTransactionState;
import net.zerocloud.pdf.WorkflowTransactionStatus;
import net.zerocloud.pdf.WorkflowResourcePolicy;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.PageCount;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Public-seam recovery contracts for identified Hardened Worker workflows. */
public final class HardenedWorkerRecoveryTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void transactionIdentityIsBoundedOpaqueAndWorkerOnly()
            throws Exception {
        assertEquals(
                WorkflowTransactionId.of("AZaz09._~-"),
                WorkflowTransactionId.of("AZaz09._~-"));
        assertEquals(
                128,
                WorkflowTransactionId.of(repeated('a', 128))
                        .getValue().length());
        for (String invalid : new String[] {
                "", "contains space", "contains/slash", repeated('a', 129)
        }) {
            try {
                WorkflowTransactionId.of(invalid);
                fail("Expected invalid transaction identity: " + invalid);
            } catch (IllegalArgumentException expected) {
                // Stable validation category; the identifier is not admitted.
            }
        }

        WorkflowTransactionId transactionId =
                WorkflowTransactionId.of("worker-only-0001");
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .temporaryDirectory(temporaryFolder.getRoot().toPath())
                .build();
        DocumentWorkflow workflow = new DocumentWorkflow(environment);
        AtomicBoolean callbackInvoked = new AtomicBoolean();
        try {
            workflow.execute(
                    WorkflowRequest.builder()
                            .transactionId(transactionId)
                            .saveMode(SaveMode.REWRITE)
                            .build(),
                    session -> {
                        callbackInvoked.set(true);
                        return null;
                    });
            fail("Expected non-Worker transaction rejection");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.INVALID_REQUEST,
                    failure.getCode());
            assertEquals(transactionId, failure.getTransactionId().get());
        }
        assertFalse(callbackInvoked.get());
        assertFalse(workflow.lookupTransaction(transactionId).isPresent());
    }

    @Test
    public void duplicateWhileRunningIsRejectedBeforeCallerWork()
            throws Exception {
        Path target = temporaryFolder.getRoot().toPath()
                .resolve("running.pdf");
        WorkflowTransactionId transactionId =
                WorkflowTransactionId.of("running-0001");
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .temporaryDirectory(temporaryFolder.getRoot().toPath())
                .build();
        DocumentWorkflow workflow = new DocumentWorkflow(environment);
        WorkflowRequest request = create(target, transactionId);
        CountDownLatch workEntered = new CountDownLatch(1);
        CountDownLatch releaseWork = new CountDownLatch(1);
        AtomicReference<Throwable> firstFailure =
                new AtomicReference<Throwable>();
        Thread first = new Thread(() -> {
            try {
                workflow.execute(request, session -> {
                    workEntered.countDown();
                    try {
                        if (!releaseWork.await(10L, TimeUnit.SECONDS)) {
                            throw new AssertionError(
                                    "Timed out releasing first work");
                        }
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(failure);
                    }
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });
            } catch (Throwable failure) {
                firstFailure.set(failure);
            }
        }, "identified-workflow-first-attempt");
        first.start();
        try {
            assertTrue(workEntered.await(10L, TimeUnit.SECONDS));
            assertEquals(
                    WorkflowTransactionState.RUNNING,
                    workflow.lookupTransaction(transactionId).get().getState());
            AtomicBoolean duplicateWork = new AtomicBoolean();
            try {
                workflow.execute(request, session -> {
                    duplicateWork.set(true);
                    return null;
                });
                fail("Expected the running duplicate to be rejected");
            } catch (DocumentFailure failure) {
                assertEquals(
                        DocumentFailureCode.TRANSACTION_IN_PROGRESS,
                        failure.getCode());
                assertEquals(transactionId, failure.getTransactionId().get());
                assertEquals(
                        PublicationStatus.NOT_ATTEMPTED,
                        failure.getPublicationReceipts().get(0).getStatus());
            }
            assertFalse(duplicateWork.get());
        } finally {
            releaseWork.countDown();
            first.join(TimeUnit.SECONDS.toMillis(10L));
        }
        assertFalse(first.isAlive());
        if (firstFailure.get() != null) {
            throw new AssertionError(firstFailure.get());
        }
        assertEquals(
                WorkflowTransactionState.COMPLETED,
                workflow.lookupTransaction(transactionId).get().getState());
    }

    @Test
    public void completedIdentityIsQueryableAndCannotPublishTwice()
            throws Exception {
        Path target = temporaryFolder.getRoot().toPath()
                .resolve("idempotent.pdf");
        WorkflowTransactionId transactionId =
                WorkflowTransactionId.of("invoice-2026-09-04-0001");
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .temporaryDirectory(temporaryFolder.getRoot().toPath())
                .build();
        DocumentWorkflow workflow = new DocumentWorkflow(environment);
        WorkflowRequest request = create(target, transactionId);

        WorkflowOutcome<Integer> outcome = workflow.execute(
                request,
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return session.query(PageCount.INSTANCE);
                });

        assertEquals(Integer.valueOf(1), outcome.getResult());
        assertEquals(transactionId, outcome.getTransactionId().get());
        Optional<WorkflowTransactionStatus> found =
                workflow.lookupTransaction(transactionId);
        assertTrue(found.isPresent());
        assertEquals(
                WorkflowTransactionState.COMPLETED,
                found.get().getState());
        assertTrue(found.get().isFinal());
        assertFalse(found.get().getFailureCode().isPresent());
        assertEquals(1, found.get().getPublicationReceipts().size());
        assertEquals(
                PublicationStatus.COMMITTED,
                found.get().getPublicationReceipts().get(0).getStatus());

        AtomicBoolean callbackInvoked = new AtomicBoolean();
        try {
            workflow.execute(
                    request,
                    session -> {
                        callbackInvoked.set(true);
                        session.execute(AddBlankPage.INSTANCE);
                        return null;
                    });
            fail("Expected a completed transaction to reject replay");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.TRANSACTION_ALREADY_FINAL,
                    failure.getCode());
            assertEquals(transactionId, failure.getTransactionId().get());
            assertEquals(1, failure.getPublicationReceipts().size());
            assertEquals(
                    PublicationStatus.COMMITTED,
                    failure.getPublicationReceipts().get(0).getStatus());
        }
        assertFalse(callbackInvoked.get());

        assertEquals(Integer.valueOf(1), new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("published", DocumentSource.path(target))
                        .primarySource("published")
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> session.query(PageCount.INSTANCE)).getResult());
        assertTrue(Files.size(target) > 0L);
    }

    @Test
    public void identityCannotBeReusedForADifferentMaterialRequest()
            throws Exception {
        Path original = temporaryFolder.getRoot().toPath()
                .resolve("original.pdf");
        Path different = temporaryFolder.getRoot().toPath()
                .resolve("different.pdf");
        WorkflowTransactionId transactionId =
                WorkflowTransactionId.of("bound-request-0001");
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .temporaryDirectory(temporaryFolder.getRoot().toPath())
                .build();
        DocumentWorkflow workflow = new DocumentWorkflow(environment);
        workflow.execute(
                create(original, transactionId),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });

        AtomicBoolean callbackInvoked = new AtomicBoolean();
        try {
            workflow.execute(
                    create(different, transactionId),
                    session -> {
                        callbackInvoked.set(true);
                        session.execute(AddBlankPage.INSTANCE);
                        return null;
                    });
            fail("Expected request-shape reuse to be rejected");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.TRANSACTION_IDENTITY_MISMATCH,
                    failure.getCode());
            assertEquals(transactionId, failure.getTransactionId().get());
            assertEquals(
                    PublicationStatus.COMMITTED,
                    failure.getPublicationReceipts().get(0).getStatus());
        }

        assertFalse(callbackInvoked.get());
        assertFalse(Files.exists(different));
        assertEquals(
                WorkflowTransactionState.COMPLETED,
                workflow.lookupTransaction(transactionId).get().getState());
    }

    @Test
    public void cancellationBeforePublicationIsRecoverableWithFreshControls()
            throws Exception {
        Path target = temporaryFolder.getRoot().toPath()
                .resolve("cancelled-then-retried.pdf");
        WorkflowTransactionId transactionId =
                WorkflowTransactionId.of("cancel-retry-0001");
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .temporaryDirectory(temporaryFolder.getRoot().toPath())
                .build();
        DocumentWorkflow workflow = new DocumentWorkflow(environment);
        CancellationToken cancelled = CancellationToken.create();
        cancelled.cancel();

        try {
            workflow.execute(
                    WorkflowRequest.builder()
                            .transactionId(transactionId)
                            .target("result", PublicationTarget.path(target))
                            .saveMode(SaveMode.REWRITE)
                            .executionProfile(
                                    WorkflowExecutionProfile.HARDENED_WORKER)
                            .cancellationToken(cancelled)
                            .build(),
                    session -> {
                        fail("Cancelled work must not start");
                        return null;
                    });
            fail("Expected cancellation");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.WORKFLOW_CANCELLED,
                    failure.getCode());
            assertEquals(transactionId, failure.getTransactionId().get());
        }

        WorkflowTransactionStatus recoverable =
                workflow.lookupTransaction(transactionId).get();
        assertEquals(
                WorkflowTransactionState.RECOVERABLE,
                recoverable.getState());
        assertFalse(recoverable.isFinal());
        assertEquals(
                DocumentFailureCode.WORKFLOW_CANCELLED,
                recoverable.getFailureCode().get());
        assertEquals(
                PublicationStatus.NOT_ATTEMPTED,
                recoverable.getPublicationReceipts().get(0).getStatus());
        assertFalse(Files.exists(target));

        WorkflowOutcome<Void> retried = workflow.execute(
                create(target, transactionId),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });
        assertEquals(transactionId, retried.getTransactionId().get());
        assertEquals(
                WorkflowTransactionState.COMPLETED,
                workflow.lookupTransaction(transactionId).get().getState());
        assertEquals(Integer.valueOf(1), new DocumentWorkflow().execute(
                WorkflowRequest.open(target, SaveMode.REWRITE),
                session -> session.query(PageCount.INSTANCE)).getResult());
    }

    @Test
    public void activeWorkerCancellationIsRecoverableAndPreservesPath()
            throws Exception {
        Path target = temporaryFolder.getRoot().toPath()
                .resolve("active-cancel.pdf");
        byte[] original = new byte[] {41, 42, 43};
        Files.write(target, original);
        WorkflowTransactionId transactionId =
                WorkflowTransactionId.of("active-cancel-0001");
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .temporaryDirectory(temporaryFolder.getRoot().toPath())
                .build();
        DocumentWorkflow workflow = new DocumentWorkflow(environment);
        CancellationToken cancellation = CancellationToken.create();
        CountDownLatch workEntered = new CountDownLatch(1);
        Thread canceller = new Thread(() -> {
            try {
                if (workEntered.await(10L, TimeUnit.SECONDS)) {
                    cancellation.cancel();
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
            }
        }, "identified-workflow-canceller");
        canceller.start();

        try {
            workflow.execute(
                    WorkflowRequest.builder()
                            .transactionId(transactionId)
                            .target("result", PublicationTarget.path(target))
                            .saveMode(SaveMode.REWRITE)
                            .executionProfile(
                                    WorkflowExecutionProfile.HARDENED_WORKER)
                            .cancellationToken(cancellation)
                            .build(),
                    session -> {
                        workEntered.countDown();
                        try {
                            Thread.sleep(250L);
                        } catch (InterruptedException failure) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(failure);
                        }
                        session.query(PageCount.INSTANCE);
                        return null;
                    });
            fail("Expected active Worker cancellation");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.WORKFLOW_CANCELLED,
                    failure.getCode());
            assertEquals(transactionId, failure.getTransactionId().get());
        } finally {
            canceller.join(TimeUnit.SECONDS.toMillis(10L));
        }

        WorkflowTransactionStatus status =
                workflow.lookupTransaction(transactionId).get();
        assertEquals(WorkflowTransactionState.RECOVERABLE, status.getState());
        assertEquals(
                PublicationStatus.NOT_ATTEMPTED,
                status.getPublicationReceipts().get(0).getStatus());
        assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(target)));

        workflow.execute(create(target, transactionId), session -> {
            session.execute(AddBlankPage.INSTANCE);
            return null;
        });
        assertEquals(
                WorkflowTransactionState.COMPLETED,
                workflow.lookupTransaction(transactionId).get().getState());
    }

    @Test
    public void lostAcknowledgementAfterPathCommitsIsResolvedByLookup()
            throws Exception {
        Path first = temporaryFolder.getRoot().toPath().resolve("first.pdf");
        Path second = temporaryFolder.getRoot().toPath().resolve("second.pdf");
        WorkflowTransactionId transactionId =
                WorkflowTransactionId.of("lost-path-ack-0001");
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .temporaryDirectory(temporaryFolder.getRoot().toPath())
                .build();
        DocumentWorkflow workflow = new DocumentWorkflow(environment);
        RuntimeException acknowledgementLost =
                new RuntimeException("simulated caller acknowledgement loss");
        WorkflowRequest request = WorkflowRequest.builder()
                .transactionId(transactionId)
                .target("first", PublicationTarget.path(first))
                .target("second", PublicationTarget.path(second))
                .saveMode(SaveMode.REWRITE)
                .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER)
                .progressListener(phase -> {
                    if (phase == WorkflowProgressPhase.TARGET_COMMITTED
                            && Files.exists(second)) {
                        throw acknowledgementLost;
                    }
                })
                .build();

        try {
            workflow.execute(request, session -> {
                session.execute(AddBlankPage.INSTANCE);
                return null;
            });
            fail("Expected the simulated acknowledgement loss");
        } catch (RuntimeException failure) {
            assertTrue(failure == acknowledgementLost);
        }

        WorkflowTransactionStatus status =
                workflow.lookupTransaction(transactionId).get();
        assertEquals(WorkflowTransactionState.COMPLETED, status.getState());
        assertFalse(status.getFailureCode().isPresent());
        assertEquals(2, status.getPublicationReceipts().size());
        assertEquals("first",
                status.getPublicationReceipts().get(0).getTargetName());
        assertEquals("second",
                status.getPublicationReceipts().get(1).getTargetName());
        assertEquals(PublicationStatus.COMMITTED,
                status.getPublicationReceipts().get(0).getStatus());
        assertEquals(PublicationStatus.COMMITTED,
                status.getPublicationReceipts().get(1).getStatus());

        AtomicBoolean replayed = new AtomicBoolean();
        try {
            workflow.execute(request, session -> {
                replayed.set(true);
                return null;
            });
            fail("Expected retry to resolve the retained final state");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.TRANSACTION_ALREADY_FINAL,
                    failure.getCode());
        }
        assertFalse(replayed.get());
    }

    @Test
    public void transactionStatusesStayInTheirFiniteEnvironmentScope()
            throws Exception {
        WorkflowTransactionPolicy policy = WorkflowTransactionPolicy.builder()
                .maximumRetainedTransactions(1)
                .build();
        assertEquals(1, policy.getMaximumRetainedTransactions());
        assertEquals(
                WorkflowTransactionPolicy
                        .DEFAULT_MAXIMUM_RETAINED_BYTES_PER_TRANSACTION,
                policy.getMaximumRetainedBytesPerTransaction());
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .temporaryDirectory(temporaryFolder.getRoot().toPath())
                .transactionPolicy(policy)
                .build();
        assertEquals(policy, environment.getTransactionPolicy());
        DocumentWorkflow workflow = new DocumentWorkflow(environment);
        WorkflowTransactionId retainedId =
                WorkflowTransactionId.of("retained-0001");
        workflow.execute(
                create(
                        temporaryFolder.getRoot().toPath()
                                .resolve("retained.pdf"),
                        retainedId),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });

        WorkflowTransactionId excessId =
                WorkflowTransactionId.of("excess-0002");
        try {
            workflow.execute(
                    create(
                            temporaryFolder.getRoot().toPath()
                                    .resolve("excess.pdf"),
                            excessId),
                    session -> {
                        fail("A full transaction ledger must reject before work");
                        return null;
                    });
            fail("Expected the finite transaction ledger to reject excess");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.TRANSACTION_RETENTION_LIMIT_EXCEEDED,
                    failure.getCode());
            assertEquals(excessId, failure.getTransactionId().get());
            assertEquals(1, failure.getPublicationReceipts().size());
            assertEquals(
                    "result",
                    failure.getPublicationReceipts().get(0).getTargetName());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
        }

        assertEquals(
                WorkflowTransactionState.COMPLETED,
                workflow.lookupTransaction(retainedId).get().getState());
        assertFalse(workflow.lookupTransaction(excessId).isPresent());
        assertFalse(new DocumentWorkflow(WorkflowEnvironment.builder()
                .temporaryDirectory(temporaryFolder.getRoot().toPath())
                .transactionPolicy(policy)
                .build()).lookupTransaction(retainedId).isPresent());
    }

    @Test
    public void retainedMetadataIsBoundedBeforeTransactionAdmission()
            throws Exception {
        WorkflowTransactionPolicy policy = WorkflowTransactionPolicy.builder()
                .maximumRetainedTransactions(2)
                .maximumRetainedBytesPerTransaction(512L)
                .build();
        assertEquals(512L, policy.getMaximumRetainedBytesPerTransaction());
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .temporaryDirectory(temporaryFolder.getRoot().toPath())
                .transactionPolicy(policy)
                .build();
        DocumentWorkflow workflow = new DocumentWorkflow(environment);
        WorkflowTransactionId oversizedId =
                WorkflowTransactionId.of("oversized-metadata-0001");
        Path oversizedTarget = temporaryFolder.getRoot().toPath()
                .resolve("oversized-metadata.pdf");
        AtomicBoolean callbackInvoked = new AtomicBoolean();

        try {
            workflow.execute(
                    WorkflowRequest.builder()
                            .transactionId(oversizedId)
                            .target(
                                    repeated('n', 1_024),
                                    PublicationTarget.path(oversizedTarget))
                            .saveMode(SaveMode.REWRITE)
                            .executionProfile(
                                    WorkflowExecutionProfile.HARDENED_WORKER)
                            .build(),
                    session -> {
                        callbackInvoked.set(true);
                        return null;
                    });
            fail("Expected retained metadata to exceed its finite bound");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.TRANSACTION_RETENTION_LIMIT_EXCEEDED,
                    failure.getCode());
            assertEquals(oversizedId, failure.getTransactionId().get());
        }

        assertFalse(callbackInvoked.get());
        assertFalse(workflow.lookupTransaction(oversizedId).isPresent());
        assertFalse(Files.exists(oversizedTarget));

        WorkflowTransactionId admittedId =
                WorkflowTransactionId.of("bounded-metadata-0002");
        Path admittedTarget = temporaryFolder.getRoot().toPath()
                .resolve("bounded-metadata.pdf");
        workflow.execute(create(admittedTarget, admittedId), session -> {
            session.execute(AddBlankPage.INSTANCE);
            return null;
        });
        assertEquals(
                WorkflowTransactionState.COMPLETED,
                workflow.lookupTransaction(admittedId).get().getState());
    }

    @Test
    public void byteSourceContentIdentitySurvivesDescriptorReconstruction()
            throws Exception {
        Path fixture = temporaryFolder.getRoot().toPath()
                .resolve("byte-source-fixture.pdf");
        new DocumentWorkflow().execute(
                WorkflowRequest.create(fixture, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });
        byte[] pdf = Files.readAllBytes(fixture);
        WorkflowTransactionId transactionId =
                WorkflowTransactionId.of("byte-source-content-0001");
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .temporaryDirectory(temporaryFolder.getRoot().toPath())
                .build();
        DocumentWorkflow workflow = new DocumentWorkflow(environment);

        workflow.execute(
                byteSourceRequest(transactionId, pdf),
                session -> session.query(PageCount.INSTANCE));

        AtomicBoolean repeatedWork = new AtomicBoolean();
        try {
            workflow.execute(
                    byteSourceRequest(transactionId, pdf.clone()),
                    session -> {
                        repeatedWork.set(true);
                        return null;
                    });
            fail("Expected equal byte content to resolve the final identity");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.TRANSACTION_ALREADY_FINAL,
                    failure.getCode());
        }
        assertFalse(repeatedWork.get());

        byte[] different = pdf.clone();
        different[different.length - 1] ^= 1;
        try {
            workflow.execute(
                    byteSourceRequest(transactionId, different),
                    session -> {
                        fail("Mismatched byte content must not run caller work");
                        return null;
                    });
            fail("Expected different byte content to reject identity reuse");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.TRANSACTION_IDENTITY_MISMATCH,
                    failure.getCode());
        }
    }

    @Test
    public void streamCommitIsFinalAndPartialStreamFailureIsNeverReplayed()
            throws Exception {
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .temporaryDirectory(temporaryFolder.getRoot().toPath())
                .build();
        DocumentWorkflow workflow = new DocumentWorkflow(environment);
        ByteArrayOutputStream committed = new ByteArrayOutputStream();
        WorkflowTransactionId committedId =
                WorkflowTransactionId.of("stream-commit-0001");
        RuntimeException acknowledgementLost =
                new RuntimeException("stream acknowledgement lost");
        WorkflowRequest committedRequest = WorkflowRequest.builder()
                .transactionId(committedId)
                .target("response", PublicationTarget.stream(committed))
                .saveMode(SaveMode.REWRITE)
                .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER)
                .progressListener(phase -> {
                    if (phase == WorkflowProgressPhase.TARGET_COMMITTED) {
                        throw acknowledgementLost;
                    }
                })
                .build();
        try {
            workflow.execute(committedRequest, session -> {
                session.execute(AddBlankPage.INSTANCE);
                return null;
            });
            fail("Expected the simulated stream acknowledgement loss");
        } catch (RuntimeException failure) {
            assertTrue(failure == acknowledgementLost);
        }
        int committedBytes = committed.size();
        WorkflowTransactionStatus committedStatus =
                workflow.lookupTransaction(committedId).get();
        assertEquals(
                WorkflowTransactionState.COMPLETED,
                committedStatus.getState());
        assertEquals(
                PublicationStatus.COMMITTED,
                committedStatus.getPublicationReceipts().get(0).getStatus());
        assertFalse(committedStatus.getPublicationReceipts().get(0)
                .isPartialOutputPossible());
        assertFinalReplayRejected(workflow, committedRequest, committedId);
        assertEquals(committedBytes, committed.size());

        FailingOutputStream partial = new FailingOutputStream();
        WorkflowTransactionId partialId =
                WorkflowTransactionId.of("stream-partial-0002");
        WorkflowRequest partialRequest = WorkflowRequest.builder()
                .transactionId(partialId)
                .target("response", PublicationTarget.stream(partial))
                .saveMode(SaveMode.REWRITE)
                .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER)
                .build();
        try {
            workflow.execute(partialRequest, session -> {
                session.execute(AddBlankPage.INSTANCE);
                return null;
            });
            fail("Expected the partial stream failure");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.PUBLICATION_FAILED,
                    failure.getCode());
        }
        WorkflowTransactionStatus partialStatus =
                workflow.lookupTransaction(partialId).get();
        assertEquals(WorkflowTransactionState.FAILED, partialStatus.getState());
        assertEquals(
                PublicationStatus.FAILED,
                partialStatus.getPublicationReceipts().get(0).getStatus());
        assertTrue(partialStatus.getPublicationReceipts().get(0)
                .isPartialOutputPossible());
        int partialBytes = partial.written;
        assertFinalReplayRejected(workflow, partialRequest, partialId);
        assertEquals(partialBytes, partial.written);
    }

    @Test
    public void workerTimeoutIsRecoverableBeforePublication()
            throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve("timeout.pdf");
        byte[] original = new byte[] {31, 32, 33};
        Files.write(target, original);
        WorkflowTransactionId transactionId =
                WorkflowTransactionId.of("timeout-retry-0001");
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .temporaryDirectory(temporaryFolder.getRoot().toPath())
                .build();
        DocumentWorkflow workflow = new DocumentWorkflow(environment);
        WorkflowRequest timed = WorkflowRequest.builder()
                .transactionId(transactionId)
                .target("result", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER)
                .resourcePolicy(copyWithElapsed(
                        WorkflowResourcePolicy.safeDefaults(),
                        Duration.ofMillis(150L)))
                .build();
        try {
            workflow.execute(timed, session -> {
                try {
                    Thread.sleep(400L);
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(failure);
                }
                session.query(PageCount.INSTANCE);
                return null;
            });
            fail("Expected the Worker timeout");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.ELAPSED_TIME_LIMIT_EXCEEDED,
                    failure.getCode());
        }
        assertEquals(
                WorkflowTransactionState.RECOVERABLE,
                workflow.lookupTransaction(transactionId).get().getState());
        assertEquals(
                DocumentFailureCode.ELAPSED_TIME_LIMIT_EXCEEDED,
                workflow.lookupTransaction(transactionId).get()
                        .getFailureCode().get());
        assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(target)));

        workflow.execute(create(target, transactionId), session -> {
            session.execute(AddBlankPage.INSTANCE);
            return null;
        });
        assertEquals(
                WorkflowTransactionState.COMPLETED,
                workflow.lookupTransaction(transactionId).get().getState());
    }

    private static void assertFinalReplayRejected(
            DocumentWorkflow workflow,
            WorkflowRequest request,
            WorkflowTransactionId transactionId) throws Exception {
        try {
            workflow.execute(request, session -> {
                fail("A final stream transaction must not rerun caller work");
                return null;
            });
            fail("Expected final stream replay to be rejected");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.TRANSACTION_ALREADY_FINAL,
                    failure.getCode());
            assertEquals(transactionId, failure.getTransactionId().get());
        }
    }

    private static final class FailingOutputStream extends OutputStream {

        private int written;

        @Override
        public void write(int value) throws IOException {
            if (written > 0) {
                throw new IOException("simulated partial stream failure");
            }
            written++;
        }
    }

    private static WorkflowRequest create(
            Path target,
            WorkflowTransactionId transactionId) {
        return WorkflowRequest.builder()
                .transactionId(transactionId)
                .target("result", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER)
                .build();
    }

    private static WorkflowRequest byteSourceRequest(
            WorkflowTransactionId transactionId,
            byte[] bytes) {
        return WorkflowRequest.builder()
                .transactionId(transactionId)
                .source("document", DocumentSource.bytes(bytes, bytes.length))
                .primarySource("document")
                .saveMode(SaveMode.REWRITE)
                .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER)
                .build();
    }

    private static WorkflowResourcePolicy copyWithElapsed(
            WorkflowResourcePolicy value,
            Duration elapsed) {
        return WorkflowResourcePolicy.builder()
                .maximumInputBytes(value.getMaximumInputBytes())
                .maximumPages(value.getMaximumPages())
                .maximumObjects(value.getMaximumObjects())
                .maximumNestingDepth(value.getMaximumNestingDepth())
                .maximumDecompressedBytes(value.getMaximumDecompressedBytes())
                .maximumDecodedPixels(value.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(value.getMaximumOwnedMemoryBytes())
                .maximumTemporaryStorageBytes(
                        value.getMaximumTemporaryStorageBytes())
                .maximumElapsedTime(elapsed)
                .maximumConcurrentWorkflows(
                        value.getMaximumConcurrentWorkflows())
                .build();
    }

    private static String repeated(char value, int count) {
        char[] characters = new char[count];
        java.util.Arrays.fill(characters, value);
        return new String(characters);
    }
}
