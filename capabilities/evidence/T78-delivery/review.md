# T78 final implementation review

Comparison baseline: `861c4ba81c7aecf9fba15052859f8cc80fd0259d`.
Scope: the tracked diff plus new T78 source, fixtures, tooling and documentation.
The sole execution contract is `/workspace/contracts/issue-78-contract.md`.
Candidate/environment certification is a separate delivery gate; this review
does not substitute for those observations.

## Standards

The code-review skill's Standards agent reviewed the repository standards and
the implementation delta. Its product-source review concluded:

> No actionable Standards findings in this delta.
>
> The 127-byte truncation now precedes both authentication and key derivation,
> with both buffers covered by cleanup and existing reservations. Facade
> credential creation and partial property-copy failures have cleanup coverage.
> The authorship omission is resolved.
>
> The added fixtures and observer paths preserve independent expectations,
> credential redaction, and pinned acceptance-tool boundaries. No new Java 8,
> ownership, clean-room, or evidence-boundary violation found.

Earlier findings were resolved: credential preparation reserves its complete
owned workspace, partial UTF-8 conversions are cleared, source/output handler
file keys have explicit lifetime cleanup, and provenance names original
authorship. The final delta also moves Facade credential creation inside its
cleanup scope and clears a failed partial WriterProperties copy.

The subsequent acceptance-coordinator review found and resolved two P2
cleanup defects: forced termination skipped private-file cleanup, and the
pinned ImageMagick AppImage could leave its rendering descendant running.
The Java owner now bounds coordinator termination and removes its private
tree. A Linux shell supervisor terminates the complete tool process group on
coordinator death, preserves binary stdin, and resets the inherited SIGTERM
handler before arming the parent-death signal.

The final read-only Standards review of the applied correction concluded:

> No remaining actionable Standards findings in the supervisor delta.
>
> The applied implementation passed the actual pinned AppImage cancellation
> probe, exact binary stream forwarding, and exit-status preservation. The
> controlled setup-race probe confirmed that resetting SIGTERM prevents tool
> startup after parent death. Missing-tool exit 127 remains a failing observation.

The [retained probe](review-probes/t78-standards-supervisor-probe.py) and
[results](review-probes/t78-standards-supervisor-probe.json) bind the actual
observer and frozen input identities. These are development review evidence,
not candidate certification. The descendant regression failed on the old
implementation; all 12 final T78 Python tests pass. All five ordinary Java
producer/lifecycle tests pass, including timeout, output overflow and
interruption cleanup. The final real-tool and matrix runs remain separate
completion gates recorded in the delivery receipt.

## Spec

The initial independent Spec review found three implementation defects:

1. R5 preparation could be interpreted differently for owner authority.
2. An unmappable legacy character could alias a replacement question-mark byte.
3. Facade version inspection could report the input instead of explicit output.

All three have public regression coverage and corrected behavior. The primary
agent reviewed the resulting state against the complete contract: the selected
version/security/credential table, all 61 Facade mappings, aggregate/downstream
boundaries, four independent observation chains, credential ownership, safe
publication and actual execution-mode binding. Added boundary fixtures exposed
and resolved R5 authentication/key-derivation disagreement at 127 UTF-8 bytes.

The required successful profile is implemented; excluded profiles have the
source-grounded dispositions in the audit. Baseline certification cannot mark
the parent aggregate, metadata-clear or embedded-files-only obligations done.
All final certification and readiness results belong in the delivery receipt.

## Final evidence and worktree audit

The final audit passed on 2026-09-29 UTC. All 2,356 source inputs and 31
contract inputs still match the recorded freeze and staged candidate. HEAD
remains the comparison baseline on `main`; the staged diff is empty.

The [retained audit](validation/final-evidence-audit.json) verifies 72 current
certifications and 288 passing independent chain records on one candidate and
contract identity. The eight baseline combinations each passed 44 public and
artifact tests, retain 544 actual product artifacts in total, and accurately
record the Facade as IN_PROCESS. All 61 baseline Facade mappings remain linked
to the selected capability and obligation.

[Live qualification](validation/live-qualification.json) replayed the certified
products and rejected changed/resealed findings and isolated false Native and
Facade execution-mode values. It operated on a copy and verified that the
certified source evidence remained unchanged. Full Maven verification, the
four-JDK matrix, the real-tool profile and the final collector suite all passed.

Final inventory validation, generation and generated-file checks passed.
Readiness remains NOT READY for the separate unfinished obligations listed in
the audit, including #79 and #80. There are no release-wide evidence errors or
blockers for the baseline or any of the eight refreshed prior obligations.
The baseline is compatible; the security aggregate remains experimental.

The final worktree scope and diff were reviewed against the comparison baseline
after generation. Only this ticket's changes are present, and `git diff --check`
passes. No applicable review finding remains unresolved. The optional local
commit was omitted; no remote mutation or release publication occurred.
