# Spec review — issue 83 relaunch

Baseline: `42fe5c797711070b319b9419e14fcb7adfa98735`; HEAD unchanged.
Method: read-only parallel Spec sub-agent using the code-review skill and the
full sole execution contract, user relaunch decision, independent investigation,
source issue and repository domain contracts.

Initial finding (P2): the real 30-second failure diagnostic was printed only in
the mandatory transcript, but the collector validated named-case markers and
scale records without requiring the exit diagnostic. Removing that finding did
not invalidate otherwise passing execution. Governing criterion: meaningful
negative tests reject missing/altered findings and resealed invalid evidence.

Resolved: `require_contract_tests` qualifies exactly one original/fresh
`T22_WORKER_EXIT_GRACE` record. It checks elapsed 30,000–59,999 milliseconds,
grace 30, alive child, positive available PID, bounded allowlisted process/thread
states, consistent finite wait/poll observations and at most eight tasks.
Each actual time/PID/scheduling state stays in its original transcript;
cross-run equality is not claimed.

Omission, repetition, malformed/unsafe data, shortened expiry, changed grace,
liveness, missing PID/state and inconsistent sample controls were added. The
retained `worker-30s-evidence-guard-red-r1` exposes the old collector gap;
`python-recovery-worker-guards-r18` then passes all 35 guards. The reviewer
checked both logs against their recorded hashes. The delayed-parent timeout
finding is also resolved.

Final review conclusion: zero remaining applicable Spec findings. The Worker
matches the approved grace, liveness recheck, diagnostics and stop controls;
no downstream scope creep was found. Reviewers changed no files or test results.
Full verification, matrix, actual four-environment scale and fresh certification
of all fourteen selected obligations remain separate required execution gates.
