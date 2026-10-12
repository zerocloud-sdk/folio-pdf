# Issue #83: T28 (and Barcode) elapsed-time failures: root-cause investigation

Investigator: orchestrator subagent, 2026-10-10 14:51–15:26 CST. /workspace/folio-pdf was only read. All scratch work is in /workspace/scratch-83-t28.

## Occurrences (all candidate-final-r9, Ubuntu/eclipse-temurin JDK 17.0.20 image, default policy limit = 5 min)
| Gate / run | Test (mode) | Reported elapsed | Wall window (CST) | Independent monotonic measure |
|---|---|---|---|---|
| full-verify-final-r10 | BarcodeWorkflowTest.literalCode128FunctionsKeepAutoControlSwitchesDecodable[HARDENED_WORKER] | 1053.888 s (surefire) | gate 10:48:36–11:20:09 | none recorded; isolated reruns 3.5–3.8 s (x10) |
| full-verify-final-r11 | T28UnicodeEvidenceCommandTest.sevenProfiles... (loop over both modes) | 557.032 s | gate 12:19:18–12:51:04 | none recorded |
| Codex exact-method replay R1 | same T28 method, alone, one fork | surefire 588.322 s; Maven "Total time 09:49" | 12:54:42–13:04:34 host wall (~592 s) | **host Python time.monotonic = 51.385 s** |
| Replay R2 x3 | same, alone | 38.3/38.7/37.1 s | 13:43–13:45 | monotonic 42.0/41.6/40.1 s = wall (no gap) |
| full-verify-final-r12 | same T28 method | 448.254 s (class 453.956 s) | gate 13:57:18–14:21:55 | none recorded |
| Pre-migration full-verify-final-r9 (2026-10-09) | all passed | — | — | — |
Normal duration of the T28 method: ~37–42 s (also 41.8 s and 42.8 s in my runs). All three stack traces are identical: DocumentWorkflow.withReceipts(276) <- executeOnce(199) <- execute <- T28UnicodeProducts.create(41).

## Mechanism (code)
- WorkflowEnvironment.systemDefaults() / new DocumentWorkflow() use Clock.systemUTC(), i.e. the **wall clock** (CLOCK_REALTIME).
- WorkflowResourceContext: startedAt = clock.instant() at context open; checkpoint(), remainingExecutionTime() and snapshotUsage() compute elapsed = Duration.between(startedAt, clock.instant()) and throw ELAPSED_TIME_LIMIT_EXCEEDED when > policy.getMaximumElapsedTime() (default 5 min; T28UnicodeProducts and BarcodeWorkflowTest use the default). DocumentWorkflow.executeOnce rewraps it via withReceipts (hence that frame).
- Only the HardenedWorkerEngine watchdog (physicalStartedNanos) uses System.nanoTime. The parent context check is wall-clock based.
- Any forward step of CLOCK_REALTIME during a workflow is therefore counted as elapsed workflow time. Surefire/Maven durations are also wall-clock based, which is why surefire showed 557/588/448/1054 s.

## Why 588 s vs 51 s
Not per-class vs per-method or cumulative time. Nothing in the test shares a deadline either. In replay R1 the host's own wall clock advanced ~592 s (started 04:54:42Z, finished 05:04:34Z, both from the outer Python datetime.now) while the host's CLOCK_MONOTONIC advanced 51.4 s. So **CLOCK_REALTIME moved ~540 s more than CLOCK_MONOTONIC during that 51 s run**. The container shares the host kernel clock, so the JVM saw the same jump.
More environment evidence: the box is a KVM microVM (kvm-clock, tsc=unstable, /dev/ptp_kvm, 8 vCPU, 15 GiB, no swap) migrated today. It has no chrony/ntpd in the guest, so the realtime corrections come from outside (the hypervisor/host side; the exact agent is unknown). Codex's clock-observation sample at 13:24:10 CST gives realtime-monotonic = 1790889436.05 s. My samples at 14:53 and 15:26 CST give 1790892001.57 s. The **wall clock gained 2565.5 s on the monotonic clock between 13:24 and 14:53 CST**, a window that contains full-verify-final-r12. Between 14:53 and 15:26 CST no step occurred (1 s monitor; offset unchanged), so steps are intermittent. The likely pattern is VM pauses or freezes followed by a realtime resync, but that is inferred, not observed.
In the container, cgroup cpu.max = max and memory.max = max, nproc = 8. No CPU/memory limit is involved. Test order and shared state are not needed to explain it, and an isolated single-method run failed once (R1).

## Reproduction (scratch, same image digest sha256:61a942..., same mounts and options as run-gate, own vfs storage)
A LD_PRELOAD shim (walljump.c) adds an offset, read from a file, to CLOCK_REALTIME only. A host loop advances the offset by +120 s every 5 s.
1. Original code, no step: `-o verify -pl pdf-acceptance -am -Dtest=T28UnicodeEvidenceCommandTest`: PASS, 2 tests, 41.79 s.
2. Original code, with steps: **FAIL**. Surefire reports "Time elapsed: 746.8 s", real ~35 s. The DocumentFailure "The workflow elapsed-time limit was exceeded." has the same stack frames as the gates (withReceipts 276/258, executeOnce 199, T28UnicodeProducts.create 41).
3. Fixed code, with steps: **PASS**. Surefire reports 1497 s of fake wall time, all within ~70 s real.
4. Fixed code, full `./mvnw -o verify`, no steps (15:07–15:26 CST): pdf-document 1273 tests 0 fail/0 err (3 skips). That includes the clock-injection tests (HostileInputWorkflowTest elapsed/deadline, EmbeddedFilesPasswordWorkflowTest, WorkflowResourceOwnershipTest, WorkflowTransactionContractTest, RenderingWorkflowContractTest), BarcodeWorkflowTest 60/60, and Barcode2D 70/70. pdf-acceptance passed (T28 42.84 s), and so did the facades. pdf-inventory-tool failed 2 tests only because my scratch copy omits capabilities/evidence subdirectories (4.7 M files): "referenced file does not exist: capabilities/evidence/T30-r2/...". This is a scratch artifact unrelated to the fix. The release tool was skipped.
The ordered-subset replay was not needed once the clock gap was established. I did not reproduce a real host clock step during my session.

## Root cause (confidence: high for T28 r11 replay; high-medium for r11/r12 gates and r10 Barcode)
An environment trigger exposes a product bug. After migration, the new VM's CLOCK_REALTIME is intermittently stepped forward by hundreds to thousands of seconds relative to CLOCK_MONOTONIC. folio-pdf measures the workflow elapsed-time budget with the wall clock (Clock.systemUTC), so a wall step is charged to whichever workflow is running and exceeds the 300 s default. That is a product bug: elapsed-time budgets must use a monotonic source. It is not a test/harness ordering problem. For r10/r11/r12 no monotonic reference was recorded, so attributing those gate failures to the same steps rests on the identical symptom, the matching inflated surefire times, and the measured 2565 s offset growth over r12's window.

## Proposed fix (scratch diff: /workspace/scratch-83-t28/fix.diff, file pdf-document/src/main/java/net/zerocloud/pdf/WorkflowResourceContext.java)
```diff
--- /workspace/scratch-83-t28/WorkflowResourceContext.java.orig	2026-10-10 14:55:08.713670001 +0800
+++ WorkflowResourceContext.java	2026-10-10 14:55:08.729670001 +0800
@@ -48,6 +48,9 @@
     private final CancellationToken cancellationToken;
     private final Instant absoluteDeadline;
     private final Instant startedAt;
+    /** Monotonic start for the system clock; elapsed time must not follow wall-clock steps. */
+    private final boolean monotonicElapsed;
+    private final long startedNanos;
     private final Path temporaryRoot;
     private final OwnedMemoryAuthority ownedMemoryAuthority;
     private final TemporaryStorageAuthority temporaryStorageAuthority;
@@ -127,6 +130,8 @@
         this.cancellationToken = cancellationToken;
         this.absoluteDeadline = absoluteDeadline;
         this.startedAt = clock.instant();
+        this.monotonicElapsed = Clock.systemUTC().equals(clock);
+        this.startedNanos = System.nanoTime();
         this.temporaryRoot = temporaryRoot;
         this.ownedMemoryAuthority = ownedMemoryAuthority;
         this.temporaryStorageAuthority = ownedMemoryAuthority instanceof TemporaryStorageAuthority
@@ -189,7 +194,7 @@
     Duration remainingExecutionTime() throws DocumentFailure {
         checkpoint();
         Instant now = clock.instant();
-        Duration elapsed = now.isBefore(startedAt) ? Duration.ZERO : Duration.between(startedAt, now);
+        Duration elapsed = elapsedSince(now);
         Duration remaining = policy.getMaximumElapsedTime().minus(elapsed);
         if (absoluteDeadline != null) {
             Duration deadlineRemaining = Duration.between(now, absoluteDeadline);
@@ -209,8 +214,7 @@
 
     synchronized WorkflowResourceUsage snapshotUsage() {
         Instant now = clock.instant();
-        Duration elapsed = now.isBefore(startedAt)
-                ? Duration.ZERO : Duration.between(startedAt, now);
+        Duration elapsed = elapsedSince(now);
         return new WorkflowResourceUsage(
                 inputBytes,
                 pageCount,
@@ -246,6 +250,19 @@
                 workerTemporary);
     }
 
+    /**
+     * Elapsed workflow time. The system clock is a wall clock that NTP or a
+     * hypervisor may step, so it is measured with System.nanoTime; an explicit
+     * caller Clock keeps its deterministic Instant semantics.
+     */
+    private Duration elapsedSince(Instant now) {
+        if (monotonicElapsed) {
+            long nanos = System.nanoTime() - startedNanos;
+            return nanos <= 0L ? Duration.ZERO : Duration.ofNanos(nanos);
+        }
+        return now.isBefore(startedAt) ? Duration.ZERO : Duration.between(startedAt, now);
+    }
+
     boolean isOpen() {
         return !closed;
     }
@@ -266,8 +283,7 @@
                     DocumentFailureCode.DEADLINE_EXCEEDED,
                     "The workflow deadline has expired.");
         }
-        Duration elapsed = now.isBefore(startedAt)
-                ? Duration.ZERO : Duration.between(startedAt, now);
+        Duration elapsed = elapsedSince(now);
         if (elapsed.compareTo(policy.getMaximumElapsedTime()) > 0) {
             throw stopFailure(
                     DocumentFailureCode.ELAPSED_TIME_LIMIT_EXCEEDED,
```
Semantics: when the environment clock is the system UTC clock, elapsed time uses System.nanoTime. A caller-supplied Clock (WorkflowEnvironment.withClock / builder.clock) keeps its deterministic Instant semantics, so the existing clock-driven tests still pass. Absolute deadlines (WorkflowRequest deadline) stay on the wall clock, which is correct for an absolute instant.

## Risks / open questions
- A custom Clock that wraps the system clock (not equal to Clock.systemUTC(), e.g. Clock.system(zone) with another zone) still uses wall time. Consider an explicit monotonic "ticker" in WorkflowEnvironment instead of the equals() check.
- The Hardened Worker child JVM: if it builds its own WorkflowResourceContext with the system clock, it gets the same fix from the same class. I did not separately audit every other wall-clock use (e.g. receipts, transaction registry timeouts).
- Recommended: add a regression test (e.g. a package-private clock/ticker seam, or the LD_PRELOAD style in CI is impractical). Also record monotonic+wall pairs in run-gate results to detect future steps.
- Absolute-deadline checks can still fail spuriously on forward wall steps. That is arguably correct for absolute instants, but T28/Barcode do not set one.
- I did not identify which host component steps the clock, and no step occurred during my 33-minute monitoring window.
- Contract/process: the fix changes product source on frozen candidate9. Applying it in /workspace/folio-pdf needs user/contract approval and a new candidate.

## Cleanup
The scratch containers ran with --rm under storage /workspace/scratch-83-t28/containers, and none remain. The clock monitor and jumper are stopped and jump.txt is reset to 0. Leftover scratch files: the repo copy (482 MB), the image tar, and vfs storage under /workspace/scratch-83-t28. They can be deleted when no longer needed. Side effect: an earlier `podman load` with an incomplete storage.conf loaded the same image into the default rootless overlay store, which already contained it.
