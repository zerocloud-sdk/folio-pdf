# Interim Facade Standards review

Scope: newly implemented page Facade and its public-consumer tests against
`e5053749e35e517fe74f81bd3ebb39d8b41e28ce`, including untracked implementation and
`docs/page-manipulation.md`. Reviewer: clean-context independent agent
`t72_facade_standards`; no implementation edits. This is not the final #72 review.

## Initial report

One blocking Standards finding:

- **P1 — Always shut down the Session when queued-handle binding fails.** Closing
  a Reader+Writer document with queued pages called `openValueSession()` before
  `valueSession.close()`. An accepted Source whose existing page lacked `/Type`
  caused the new reference lookup to throw `QUERY_FAILED`. Close then released
  declarations while the Native callback remained blocked; repeating close could
  not release it and receipts remained empty. This violated ADR-0013 ownership of
  publication/cleanup and the documented Session lifetime. The public Facade
  probe reproduced the leak and interrupted the leaked worker after observation.

No additional concrete documented violations or heuristic findings in the bounded
review. Package suffixes and Native delegation remain intact; utility factories
and receipts were treated as explicit Folio extensions. The reviewer did not
rerun the initial 45-test result.

Probe: [initial observation](development/standards-startup-probe.txt).

## Independent correction check

P1 is resolved. Initialization failures now escape the Native callback, so
`submit()` receives the completed Native failure after cleanup and receipt
attachment. Ordinary operation failures retain their recoverable behavior.

Rerunning the original public Facade probe confirmed `QUERY_FAILED`, one actual
receipt after repeated close, no published output, and zero remaining Facade
worker threads. The reviewer inspected the public regression and the 46-test
green log. No additional Standards findings in this correction.

Probe: [corrected observation](development/standards-startup-fixed-probe.txt).
Public regression: [RED](development/facade-initialization-red.txt),
[GREEN with all 46 related tests](development/facade-initialization-green.txt).

Independent Spec review, final full-goal Standards/Spec review, actual-jar surface
checks, T10 certification and complete validation remain pending. No completion
criterion is marked satisfied by this interim review.
