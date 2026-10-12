# Issue #83: HARDENED_WORKER "The Worker did not terminate after completing its response." Investigation

Date: 2026-10-09 (CST). Investigator: Grok Bot (executor).
Scratch worktree: `/workspace/scratch-83-worker` (detached at 42fe5c79 with the #83 dirty diff applied). Experiment dir: `/workspace/scratch-83-worker/.t83x` (kept).
`/workspace/folio-pdf` was not modified: no commit, stash, reset or checkout. `git status --porcelain` still shows 67 entries and HEAD is still 42fe5c797711070b319b9419e14fcb7adfa98735 (see the side-effect note in §9).

## 0. Summary

1. **Root cause (moderate-to-high confidence):** `WorkerConnection.requireExited()` allows the Worker only **1 second** after an authenticated FINISHED to be observed as exited. That second has to cover all of the following, and none of them is bounded on this host:
   - HotSpot JVM teardown (DestroyJavaVM / VM_Exit);
   - kernel process exit;
   - the parent's `process_reaper` observing the exit.

   On this shared VM, IO writeback throttling, memory reclaim and hypervisor steal regularly stall these steps. I directly measured one whole-guest stall of about 1.1 s.
2. **Classification:** a product robustness defect (an unrealistically tight hard deadline) that only this environment exposes. It is not a protocol or logic bug in the Worker. The child had already returned from `main`; the time is spent in JVM/OS teardown.
3. **Codex's fix:** it adds `&& process.isAlive()` to the deadline check. That covers only the variant where the child has exited and the parent noticed late. It does not cover a child that is still tearing down after more than 1 s. The r9 failure (line 2051) happened **after** that fix.
4. **Proposed fix:** raise the post-FINISHED exit grace to 30 s and keep Codex's recheck. Cancellation, the caller deadline and the elapsed limit are still enforced by `checkpoint()` and the watchdog.
5. **Proof by fault injection:** freeze the test container's process tree for 1.2 s at random intervals.

   | Jar | Worker-termination failures |
   |---|---|
   | staged (current code) | 4 in 4 JDK 8 page suites, plus 3 in 1 JDK 17 text suite |
   | fix | 0 in 4 JDK 8 page suites, plus 0 in 1 JDK 17 text suite |

6. **Honest limitation:** I never reproduced the failure *naturally*. That covers 6,000+ instrumented Worker exits under CPU, IO, memory and container-churn stress. The longest natural exit wait was 844 ms, against the 1,000 ms limit.

## 1. Occurrence table

All occurrences ran inside a podman container, and the gates ran sequentially. The only concurrent activity was Codex's own monitoring commands (plus one 4-second helper during full-verify).

| # | Run | When (CST) | JDK / mode | Failed at | Line | Timing notes |
|---|---|---|---|---|---|---|
| 1 | r1 password-baseline recorder | Oct 7 ~18:13–18:17 | JDK 8 HW | T78EvidenceCommand → PasswordProducts.create:94 | 2049 | suite 195.9 s, otherwise OK |
| 2 | r2 attachments recorder | Oct 8 ~00:06–00:07 | JDK 8 HW | T80 PasswordProducts.create:193 | — | — |
| 3 | r3 attachments | Oct 8 00:52–00:57 | JDK 8 HW | test 15/25 `originalInputsOpenClearContentWithoutAttachmentCredentials` | — | suite 300.7 s (other runs: 290.8 / 305.1 s) |
| 4 | full-verify-final-r4 | Oct 8 19:16:03–19:32:03 | JDK 17 Surefire (Ubuntu JDK 17 image 61a94244) | `ShapingCompositionWorkflowTest.tableMinimum…[HARDENED_WORKER]` | — | this test took 5.721 s (siblings 0.7–5.9 s, one 25 s); isolated replay ≈3.0 s per JVM |
| 5 | r7 pages | Oct 8 20:42:33–20:45:00 | JDK 8 HW | test 11/66 `pageMovementRejectsMalformedInheritedAttributesBeforeMutation` | 2049 | suite 146.4 s, slow vs 101–129 s for other JDK 8 page runs |
| 6 | r9 text | Oct 9 04:20:10–04:24:48 | JDK 17 HW | test 3/126 `toUnicodeNodesAndCodeSpaceDeclarationsShareFontDataBounds` | 2051 | **after the isAlive fix**; suite 276.6 s (normal); failed seconds after container start |

Sources: `capabilities/evidence/T83-delivery/failed-attempts.md`, the run transcripts, `diagnostics/full-verify-worker-exit-r1` (TEST XML, 100 method replays, no failure), and the validation `*-result.json` files.

Observations:
- Failures hit both JDKs, many different test classes, and different points in a suite.
- I found no correlation with a specific workload.
- None of the three virtio_balloon "Out of puff" kernel messages (Oct 8 21:09, Oct 9 03:29 and 05:09) coincides with a failure window.

## 2. Worker lifecycle, end to end

**Parent launch.** The parent launches the Worker with a cleared environment:

`prlimit --cpu=N --nofile=64 -- java -Xmx512m -XX:MaxDirectMemorySize=… -Xss1m [-Djava.security.manager=allow] -Djava.io.tmpdir=<root> -cp … HardenedWorkerMain <root> <msgBytes> <memBytes>`

**Parent threads:**
- a daemon thread drains stderr;
- a daemon reader executor reads responses;
- a daemon watchdog checks the elapsed limit and cancellation every 25 ms;
- `close()` runs `terminateAndAwait`.

**Child shutdown path:**
1. The child sends RESOURCE_USAGE and then FINISHED.
2. It closes the resolver and the endpoint (flush) and returns from `main`.
3. The JVM exits through DestroyJavaVM. The SecurityManager denies `exitVM`, and the child creates no threads of its own.

**HotSpot's own exit waits:** `VM_Exit::wait_for_threads_in_native_to_block` waits up to 10 s for compiler threads and up to 300 ms for user threads in native code (see JDK-8349927 and JDK-8314243). So the JVM's own teardown is not bounded to 1 s by design.

**Parent wait** (`requireExited`, HardenedWorkerEngine.java ~2029–2061):

```java
long waitUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(1L);
while (process.isAlive()) {
    if (process.waitFor(25, MILLISECONDS)) break;
    resources.checkpoint();
    if (System.nanoTime() - waitUntil >= 0L && process.isAlive()) {   // isAlive added by Codex
        process.destroyForcibly();
        throw workerFailure(WORKER_TERMINATED, "The Worker did not terminate after completing its response.");
    }
}
```

`Process.isAlive()` returns a flag that the `process_reaper` thread sets after `waitpid`. It is not a live kernel query. So "exit observed" also needs the reaper thread to get scheduled.

## 3. Assessment of Codex's fix

- **Diff:** adds `&& process.isAlive()` to the deadline check, plus a comment. Codex also added `HardenedWorkerTerminationWaitTest`, which models a parent that is descheduled for 1.2 s while the child has already exited zero.
- **What it fixes:** the "child already exited, parent observed it late" variant. That variant is real; my probes saw "noproc" cases where the child was already reaped but the parent still reported `isAlive`.
- **What it does not fix:** a child that is **still alive** (tearing down, or stalled in D state / descheduled) when 1 s has passed. The deadline then still fires.
  - The r9 failure (JDK 17, Oct 9 04:20–04:24, line 2051) happened with this fix in place.
  - My injection experiment (§6) reproduces exactly this with the fix-included staged jar.
- **Verdict:** correct but insufficient.

## 4. Hypotheses and tests

All exit-wait numbers below come from an instrumented copy of the engine (`jars/pdf-document-instr3.jar` and its predecessors). It logs `waitMs` from FINISHED to observed exit, and takes /proc probes (child `comm:state:utime+stime:wchan`; parent non-sleeping threads and `process_reaper`) after 100 ms.

### 4.1 Natural stress (no injection): exit wait in ms

| Run | Conditions | n | p99 | max | ≥1000 |
|---|---|---|---|---|---|
| idle sanity | — | 185 | — | 30 | 0 |
| s1 | 6× JDK 8 page suites in parallel, load ≈12 | 1110 | 42 | 81 | 0 |
| e2 | 3 suites + IO hog | 1110 | — | 228 | 0 |
| e3 | 4 suites + iohog2 (1 GiB buffered writes + `sync` every 3 s); synchronous logging | 1450 | 81 | 844 | 0 (4 waits >500) |
| e4 page | instr3, iohog2 + podman create/rm churn | 1110 | 76 | 391 | 0 |
| e4 text (JDK 17) | instr3, iohog2 + churn | 844 | 108 | 144 | 0 |
| e7 page (JDK 8) | + host memory hog (available ≈1.2 GB) + IO + churn | 578 | 78 | 249 | 0 |
| e7 text (JDK 17) | same | 151 | 88 | 96 | 0 |

Note: e3's 535 ms and 713 ms cases include delay from the synchronous instrumentation log. From instr3 onwards, logging is async.

### 4.2 Individual hypotheses

| Hypothesis | Test | Result |
|---|---|---|
| CPU oversubscription | s1, load ≈12 on 8 vCPU | max 81 ms. Not sufficient alone. |
| IO writeback throttling | e2/e3/e4 with IO hogs + sync | Raised the tail to 844 ms. Probes showed the child's main thread already a zombie while `GC_task_thread` or `C2_CompilerThread` sat in state **D** with wchan `rq_qos_wait` (block-layer writeback throttling; WBT enabled on vda, `wbt_lat_usec` 75000). Also seen: the main thread in D with the JVM fully alive at 100 ms. **Contributing, supported.** |
| Memory pressure / reclaim | e7, host available ≈1.2 GB | max 249 ms. Not sufficient alone. Since boot: PSI memory full ≈3118 s, large allocstall/refault counters. |
| hsperfdata mmap file (`-XX:+PerfDisableSharedMem`) | ExitBench JDK 8, child last byte → `waitFor` | b1 max 112 vs 134 ms (n=800 each); b2 max 102 vs 120 ms (n=300 each). **Not supported.** |
| Parent-side GC pause | parent GC log | max 24 ms. Not supported. |
| Parent-side scheduling / reaper lag | probes | "noproc" (child reaped, parent `isAlive` still true). Parent `waitFor(25)` iterations sometimes took 182–218 ms. **Contributing** (this is the variant Codex addressed). |
| Product code still running after FINISHED | `childMainReturned` marker | ≈0 ms after FINISHED. The time is spent in JVM/OS teardown, not product code. **Rejected.** |
| Whole-guest stall (hypervisor steal / host contention) | `stallmon.py` (pure-Python 5 ms sleep loop, no IO) | At 08:18:04 CST: a monotonic gap of **1114 ms** and a wall-clock jump of 1057 ms, with steal deltas of 266 + 53 jiffies against a typical 4–18 per minute. Every other per-minute max gap was 12–71 ms (plus 219 and 112 ms at 08:17:02–03). Since boot: steal ≈9469 s, PSI io full ≈3907 s. **Supported: a stall >1 s really happens on this VM.** No exit wait happened to overlap that stall. |

### 4.3 Conclusion on root cause

The exit window after FINISHED is normally 10–80 ms. On this VM it is occasionally stretched by:
- block-IO throttling of exiting JVM threads (state D, `rq_qos_wait`);
- reaper/parent scheduling lag;
- whole-guest stalls of ≥1.1 s (steal).

A stretch of more than 1 s that lands on an exit window fails the hard 1 s deadline. That matches the observed rate: rare (a handful in many thousands of exits), random across tests and JDKs, and clustered on a box with high cumulative PSI and steal.

**Confidence: moderate-to-high.**
- **Mechanism and fix:** high. Injection reproduces the exact message and the fix removes it.
- **The specific stall source at each historical failure:** moderate. There was no instrumentation at those times.

## 5. Proposed fix

File `.t83x/proposed-fix.diff`, applied on top of the current dirty tree, which includes Codex's isAlive recheck:

```diff
--- a/pdf-document/src/main/java/net/zerocloud/pdf/HardenedWorkerEngine.java
+++ b/pdf-document/src/main/java/net/zerocloud/pdf/HardenedWorkerEngine.java
@@ -50,6 +50,16 @@
     static final String CAPABILITY_ID = "document.hardened-worker";
 
     private static final long RESPONSE_POLL_MILLIS = 25L;
+    /**
+     * Post-response exit grace. After an authenticated FINISHED the Worker only
+     * performs JVM teardown (DestroyJavaVM; HotSpot itself may wait up to 10 s for
+     * compiler threads in native code), kernel process exit and the parent's
+     * reaper observation. None of these is real-time: block-IO throttling,
+     * memory reclaim or hypervisor steal can stall them for more than a second.
+     * Cancellation, the caller deadline and the workflow elapsed-time limit stay
+     * enforced through checkpoint() and the watchdog during this wait.
+     */
+    private static final long WORKER_EXIT_GRACE_SECONDS = 30L;
     private static final int WORKER_OPEN_FILE_LIMIT = 64;
     private static final int MAXIMUM_CLASS_INVENTORY_BYTES = 64 * 1024;
     private static final String DOCUMENT_CLASS_INVENTORY =
@@ -2028,7 +2038,7 @@
 
         void requireExited() throws DocumentFailure {
             long waitUntil = System.nanoTime()
-                    + TimeUnit.SECONDS.toNanos(1L);
+                    + TimeUnit.SECONDS.toNanos(WORKER_EXIT_GRACE_SECONDS);
             while (process.isAlive()) {
```

Rationale:
- The grace period only bounds a child that has already authenticated FINISHED and whose `main` has returned.
- Security and resource limits are unchanged. `checkpoint()` still throws on cancellation and on the caller deadline. The watchdog still kills the child at the workflow elapsed limit (default 5 min). The products are still not registered until exit is confirmed, so the "no child write can race publication" invariant holds.
- 30 s is longer than HotSpot's own 10 s compiler-thread exit wait.

Build: the fix jar `.t83x/jars/pdf-document-fix.jar` is the staged jar with every `HardenedWorkerEngine*.class` replaced by classes compiled from the pristine source plus only this diff. `javap` confirms `ldc2_w 30l` in `requireExited`. The class inventory checks names only, and no class was added.

## 6. Stress results: fault injection

Method: `.t83x/pause-inject.sh`, run 08:32:43–08:49:55 CST.
- At random intervals of 1.5–3.0 s, it sends SIGSTOP to the whole process tree of every test container: the test JVM, which is the Worker's parent, and all Worker children.
- It holds the stop for 1.2 s, then sends SIGCONT. Measured stop durations were 1203–1213 ms (median 1204), with 224 events in total.
- This models the 1.1 s guest stall I observed. `CLOCK_MONOTONIC` keeps advancing during the stop, as it does during steal.

| Exp | Window (CST) | Pauses | Suite | Jar | Suites | Worker-termination failures | Other failures |
|---|---|---|---|---|---|---|---|
| F1 | 08:32:41–08:38:57 | 94 (shared F1/F2) | JDK 8 `PageManipulationWorkflowTest` (53 tests), HW | staged (current code incl. isAlive fix) | 4 | **4** (3 of 4 suites failed; tests e.g. `pageInsertionProducesRequestedOrderAfterReopen`, `invalidPagePositionsFailStablyWithoutChangingTheDocument`) | 0 |
| F2 | same | same | same | **fix** | 4 | **0** (4/4 OK) | 0 |
| F4a | 08:41:21–08:49:32 | 123 | JDK 17 `TextStructureExtractionWorkflowTest` (115 tests), HW | staged | 1 | **3** | 3 JUnit 10 s `@Test(timeout)` |
| F4b | same | same | same | **fix** | 1 | **0** | 2 JUnit 10 s `@Test(timeout)` (same tests as in F4a) |
| F3a | 08:46–08:49:57 | 52 (shared) | JDK 8 page, instr3, limit 1000 ms | instr | 2 | 0 (n=370 exits; p99 38, max 48 ms; no pause landed in an exit window) | 0 |
| F3b | same | same | same, limit 30000 ms | instr | 2 | 0 (n=370; p99 54, **max 1220 ms**; 2 waits ≥1000 ms that would have failed at 1 s, both completed normally) | 0 |

Notes:
- **JUnit timeouts in F4:** they come from 6 JVMs plus repeated freezes. Their stacks are in `WorkerConnection.exchange` (waiting for the Worker's response at HardenedWorkerEngine.java:1453), not in `requireExited`. They appear in both the staged and the fix run, so they are an injection artifact and not caused by the fix.
- **F3b probes:** both ≥1 s waits show the probe pattern `noproc || parent: java:R … process_reaper:S`. The child was already gone, but the parent had not observed it until the stop ended.
- **Without injection:** `HardenedWorkerTerminationWaitTest` (Codex's test) passes with both jars: JDK 8 staged 2.129 s / fix 2.175 s; JDK 17 staged 1.921 s / fix 1.924 s.

## 7. Non-reproduction statement

I did **not** reproduce the failure without artificial injection. That covers 6,000+ instrumented natural exits under CPU, IO, memory and churn stress on both JDKs. The natural maximum was 844 ms. The failure only appeared once I injected a ≥1.2 s freeze. The causal chain for the historical failures (stall >1 s overlapping an exit window) is therefore inferred from:
- the code path;
- the measured D-state / `rq_qos_wait` probes;
- the measured 1.1 s guest stall;
- the injection reproduction of the identical message.

It was not observed directly at a historical failure.

## 8. Instrumentation that would settle it

1. In production code, on `WORKER_TERMINATED` from `requireExited`, include in the failure detail or a debug log:
   - the elapsed ms since FINISHED, the iteration count and the max iteration ms;
   - whether `isAlive` was still true;
   - a /proc snapshot of the child (`/proc/<pid>/task/*/stat` state and wchan) taken before `destroyForcibly`.
2. Sample `/proc/pressure/{cpu,io,memory}` and `/proc/stat` steal at ≥1 Hz during gates, plus a stall heartbeat like `.t83x/stallmon.py`.
3. Log a per-test timestamp of the exit wait so that failures can be lined up with stall-monitor events.

## 9. Side effects, artifacts and cleanup

- **Scratch:** `/workspace/scratch-83-worker` (kept). Contents of `.t83x/`:
  - original sources `*.java.orig`;
  - jars `pdf-document-{staged,instr,instr2,instr3,fix}.jar`;
  - scripts `run-suite.sh`, `stress.sh`, `pause-inject.sh`, `summ.py`, `stallmon.py`, `iohog*.sh`, `churn.sh`, `memhog.py`, `bench/`;
  - all logs in `runs/` (`inject-f12.log`, `stallmon.log`, `load-monitor.log`, `f1-staged`, `f2-fix`, `f3-*`, `f4-*`, `tw/`, `s1`/`e2`/`e3`/`e4`/`e7`).
- **Git side effect:** `/workspace/folio-pdf/.git/config` now contains `extensions.worktreeconfig=true`. It was probably set by my `git sparse-checkout init` in the scratch worktree; I did not check its value beforehand. The scratch worktree is also registered in `git worktree list`. Branches, index, HEAD and the working tree of the main repo are untouched (67 status entries; HEAD 42fe5c79).
- **Cleanup at the end:**
  - all t83diag containers removed (0 left in the t83 storage);
  - stress, injector, IO hog, churn, memory hog, stall monitor and load monitor processes stopped;
  - `.t83x/io/*` scratch files deleted;
  - no processes left in stopped (T) state.
  - Pre-existing podman pause processes were left alone.

## 10. Risks and open questions

- **JUnit per-test timeouts:** a 30 s grace is longer than some tests' 10 s timeouts. In a genuinely stuck teardown, the test would fail with `TestTimedOutException` instead of WORKER_TERMINATED. Product callers are still bounded by their deadline, cancellation and the elapsed limit.
- **Effect on #83 evidence:** the 30 s value is a judgement call (it exceeds HotSpot's 10 s). A spec, ADR or evidence document that pins "1 s" would need updating, and the change needs owner review.
- **Unchanged infrastructure risk:** the host itself (WBT throttling, steal, vfs podman storage copying 280–450 MB per container start) remains a source of timing flakiness for other tight timeouts.
- **Unexplained detail:** the r4 Surefire failure had no line number in the evidence I read.
