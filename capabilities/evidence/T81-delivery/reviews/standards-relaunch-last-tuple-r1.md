# Standards review — final attachment tuple recovery

Baseline and HEAD: `05e7f546f5885680e333ad8d3dea3645b25d26b7`.
Read-only review of the two new ticket-local helpers against the previously
reviewed recovery/diagnostic recipes, repository standards and Fowler smell
baseline. No final execution or certification result is claimed.

Reviewed SHA-256 identities:

- `resume-attachments-last-final-r2.py`: `55ff0581f7fd32cec4678cfe91aece308234bab0a296f9dae4e997bb0a2c1e01`
- `diagnose-attachments-jdk21-worker.py`: `24f1a07ca272a9a145ecce0b31b3a9f69068445de548757943e02e8771902964`

**Findings: 0 documented-standard violations; 0 applicable smell findings.**

The recovery adds the completed JDK17 HARDENED_WORKER and JDK21 IN_PROCESS
scopes from `password-attachments-r3` to the original five retained scopes.
All seven retained directories contain the complete passing 24-test marker
and separate passing syntax, standards, semantic and visual records. The
current authority still contains 80 predecessor scopes.

Both original directories are sealed in full, including interrupted and failed
files. The existing exact source/stage, configuration-input, command, producer,
environment and native-engine checks remain intact. Every applicable original
environment is compared with fresh before/after observations. Only the entire
remaining JDK21 HARDENED_WORKER tuple executes in a fresh directory, using the
unchanged driver, suite, recorder, collector and per-command bounds. Exact
eight-attachment coverage and the 80-plus-eight scope union are required before
the existing guarded, locked, atomic publisher is called. This retains the
candidate/environment discipline of ADR-0040 without relabeling historical
evidence or expanding #81 into Worker implementation work.

The diagnostic adaptation changes only tuple/output paths and the retained
failure description. It preserves the original full command except for the
fresh writable output mount and keeps the 600-second diagnostic suite bound.
It remains explicitly diagnostic; no cause, fix or certification is inferred.
Its execution result was pending at this review.

Both sources parsed without execution. Actual successful recovery, final
scope identities, required gates and delivery receipt remain subject to the
separate final evidence review. No mandatory test, tool, fixture or
certification omission is endorsed.
