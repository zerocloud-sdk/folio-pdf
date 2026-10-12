# Supplemental timing audit for candidate10

The [original audit](timing-audit-r20.md) remains unchanged. This supplement
covers shell clock variables/epoch commands and the delivery helpers added
after that audit. [Exact searches and results](timing-audit-supplement-r23.txt)
retain the commands and exit codes; an empty `rg` search exits 1.

| Finding | Decision |
| --- | --- |
| No shell `SECONDS`, `EPOCHREALTIME`, `EPOCHSECONDS`, epoch-clock elapsed loop, `time.clock`, or C/C++ realtime clock measurement was found in the searched active repository tooling | No additional elapsed-budget change is needed. |
| `scripts/release-rehearsal` records `source_date_epoch` from Git's commit timestamp and passes it as `project.build.outputTimestamp` | Keep this absolute timestamp for reproducible build metadata. It is neither a timeout nor an elapsed measurement; the broad search matched its receipt-format string. |
| R21/R22 diagnostic timers and the R22 Java listener | Already use `time.monotonic` or `System.nanoTime`; keep the original limits and measurements. Failed diagnostics remain failed and supply no certification. |
| R21/R22/R23 helper `datetime.now` calls | Keep the UTC creation/start/finish stamps. Qualification durations come from the unchanged monotonic gate wrapper, rather than subtracting these stamps. |
| Closing audit, ownership, receipt, staged-content proof and commit controller | No wall-clock timeout or elapsed budget is introduced. Python subprocess timed waits use the previously inspected CPython monotonic timeout implementation. |

The later helpers do not alter the frozen product, contracts, candidate, test
watchdogs or independent checker limits. Their final bytes are covered by the
closing reviews and ownership manifest.
