# Issue #83 diagnostic-helper Spec review

Zero actionable findings in the bounded read-only review of
`preserve-failed-text-r21.py`, `diagnose-text-timeout-r22.py` and
`diagnostics/T83TextReplayR22.java`. No product, frozen input, original artifact
or existing report was changed.

Preservation metadata seals 138,077 original files/900,290,576 bytes, zero text
exports and an unchanged forty-tuple/160-chain index. Its manifest, prior-index,
gate, ledger, failure, freeze, driver and preflight reference hashes match.
The full original-file rehash was not repeated here. After index advancement,
closing preservation validation must use immutable prior-index copies:
the helper's unchanged `--validate` mode currently requires the live index to
remain at the failed attempt's identity.

Diagnostics preserve the original staged tests, annotation timeout,
image/options/classpath and read-only failed scope. Twenty exact-method replays
passed. Three eighteen-case prefixes passed; prefix four failed, and the gate
stopped with exit one. The target reached its unchanged 10,000-ms watchdog during
`ProxyDocumentSession.query`; the listener measured 10,685 monotonic ms, versus
39.660 monotonic seconds for the parent and 75.426 wall seconds reported by
JUnit. The requested ten-prefix loop did not complete. These are diagnostics,
not certification or a causal/performance guarantee.

The target test asserts Unicode carry, mapping confidence and unchanged Source
bytes; it asserts no ten-second workflow policy. Its annotation is a test
watchdog. A justified finite watchdog correction can preserve those semantics,
but requires a new source/harness freeze, staging and all fresh selected gates
and certifications. Matching a thirty-second annotation to one exit grace is
insufficient justification for this method's two workflows or the observed
initialization/query waits.

Retaining seven complete original candidate10 profiles unchanged and generating
a fresh eighth can satisfy the contract: “Every certification is bound to the
actual final candidate” and historical records “cannot be relabeled for a new
candidate.” Preserve original paths/configurations/reports/hashes; bind mixed
roots explicitly. The minimum equivalent completion can revalidate all seven
complete originals with unchanged `collect_reports`/manifests, execute only the
missing eighth profile freshly, and require exact fresh before/after environment
equality. Require the complete eight-tuple/four-chain text set before unchanged
atomic `publish_index`. Eight additional full replays were an earlier proposed
plan condition, not a T13 contract requirement; standard text certify does not
execute them. No failed scope or diagnostic may supply PASS. The concrete
resume driver and final all-fourteen evidence remain unreviewed.
