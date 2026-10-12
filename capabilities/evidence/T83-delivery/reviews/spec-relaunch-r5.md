# Spec review — Java 8 interrupted-fixture inspection

Read-only incremental review against the sole #83 execution contract. Zero
remaining applicable Spec findings in the fixture correction.

The fixture observes and requires the preserved caller interrupt flag before
temporarily clearing it for its own inspection I/O. The finally block restores
the flag, including assertion and I/O failure paths. Outer cleanup also
preserves it, and the calling test still checks isInterrupted().

Existing failure-code, receipt, transaction, lookup, unchanged-Target,
child-termination, storage-cleanup and diagnostic assertions remain intact.
There is no skip, assumption or runtime behavior change. Candidate8/R14 still
requires fresh full verification, all four JDKs, actual scale and all fourteen
certifications. The reviewer changed no files and ran no builds or tests.
