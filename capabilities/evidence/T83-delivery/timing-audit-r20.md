# Elapsed-time audit for candidate10

All shipped Java code, acceptance tooling, build-tools, shell/Python gates and
current T83 delivery helpers were searched for Clock/Instant, currentTimeMillis,
Date/ZonedDateTime, time.time/datetime, timestamp conversion, gettimeofday and
CLOCK_REALTIME. [Raw findings](timing-audit-r20.txt) retain the source scan;
[local CPython timeout source](python-timeout-source-r20.txt) confirms the
subprocess timeout clock. No elapsed policy, timeout or budget in the audited
active tooling uses an unintentional wall-clock measurement.

| Finding | Decision and observable consequence |
| --- | --- |
| WorkflowResourceContext checkpoint, remainingExecutionTime and snapshotUsage used Duration.between wall instants | All three now use the environment's independent nanosecond ticker by default, with signed subtraction across wraparound. Wall adjustments do not exhaust or extend elapsed limits, Provider budgets or usage. New public regressions cover both execution profiles. |
| WorkflowEnvironment Clock injection drives existing deterministic time tests | Preserve explicitly configured Clock-only logical elapsed semantics for compatibility. An explicit monotonicTicker overrides that logical source regardless of setter order. Every ordinary builder/default environment uses System.nanoTime. Both configurations are documented; no Clock type/equality special case exists. |
| Absolute WorkflowRequest deadlines and the deadline portion of remainingExecutionTime | Retain wall-clock Instant semantics; the parent Clock remains authoritative and caller-thread confined. A forward step reaching the deadline still returns DEADLINE_EXCEEDED, covered in both profiles. |
| WorkerMessages initialization v2 accepted a relative duration and converted it to Instant.now | Remove the relative-to-wall conversion. The v2 encoder always leaves deadline ownership in the parent; a complete formerly accepted relative-deadline envelope is now safely rejected at the real codec boundary. |
| HardenedWorkerEngine lifetime watchdog, response polling, 30-second post-FINISHED grace and exit diagnostic elapsed durations | Already System.nanoTime. Preserve independent physical Worker ceiling, CPU ceiling, cancellation, bounded polling, liveness recheck, termination and publication ownership. |
| Rendering/Shaping Provider requests | They obtain the corrected remainingExecutionTime budget. Absolute deadline may shorten it; expiration still wins at a workflow checkpoint. Provider timeout values remain positive at the inclusive elapsed boundary. |
| ExternalProcess and acceptance process cleanup | Already nanoTime plus timed Process/Future waits. Preserve finite timeout and output limits; no wall-clock budget. |
| Python independent observers, Foundation certification/collection, native installs and matrix/scale shell gates | Explicit elapsed loops already use time.monotonic; subprocess.run/Popen timeouts use CPython monotonic deadlines. Java acceptance scale invocation timers already use nanoTime. Keep all tool limits unchanged. |
| T83 run-gate timestamps | Retain original run-gate.py and its historical hashes. Fresh run-gate-monotonic.py records primary monotonic duration, separate wall duration, their difference and UTC timestamps; subprocess timeout remains monotonic. |
| Native-install receipts, observer creation stamps and Facade PDF CreationDate/ModDate | Retain wall timestamps because they identify an absolute creation/modification time, not a budget. |
| Existing diagnostic clock-observation and Unicode replay helpers | Their paired time.time durations deliberately expose wall drift alongside time.monotonic, and do not control a timeout or certify a candidate. Preserve their immutable original sources/results. |
| Historical validate-attachments-duration-r1.py uses filesystem mtime minus a started UTC timestamp | This immutable, superseded diagnostic interprets original historical stamps; it is excluded from the new qualification path and makes no active elapsed-budget or timing certification claim. Do not rewrite historical evidence. |
| Maven/Surefire elapsed values | Third-party diagnostic reporting may still follow wall time. Retain original logs/reports without normalization and use the outer monotonic gate duration for actual elapsed claims. They no longer drive the product workflow limit. |

No runtime/test dependency or copied external implementation was added.
System.nanoTime intervals intentionally have the JDK subtraction contract
(less than 2^63 nanoseconds); all finite policy durations already fit that bound.
Negative ticker deltas are clamped to zero. Explicit test tickers must be
thread-safe, nonblocking and monotonic. VM suspension and scheduling can still
affect actual performance; no universal timing/failure-rate guarantee is made.
