# T80 initial Standards review

Comparison: current worktree against
`08928c8d08de677a99f092c4ebd6bbdaa64ef6ba`; no intervening commits.
Reviewed tracked changes and new T80 product code, public tests, acceptance
scripts, model overlays, inventories, profile contracts and documentation.
The sole execution contract remains `/workspace/contracts/issue-80-contract.md`.
Mandatory validation and certification are still running separately; their
unfinished status is not a finding in this source review.

## Documented-standard findings

- **P2 — Account incremental filter-copy allocations and work.**
  `pdf-document/src/main/java/net/zerocloud/pdf/PdfBoxEmbeddedFileEncryption.java:29`
  allocates two retained `COSArray` instances and fills them from an input-sized
  filter sequence without an owned-memory charge or elapsed-work checkpoint.
  `prepareIncremental` invokes this after the final document audit for preserved
  Identity EFF routes. ADR-0016 requires explicit owned-memory and elapsed-work
  limits; ADR-0031 retains the shared Worker grant ledger. Pass the resource
  context, charge the retained arrays before allocation and checkpoint copying.
  The new `encrypted` filter scan also needs checkpoints for input-sized work.
- **P3 — Identify T80 authorship.** The new T80 section in `PROVENANCE.md`
  records sources, fixture/tool licenses and clean-room exclusions, but does not
  identify who authored this contribution. `CONTRIBUTING.md`, “Clean-room
  provenance,” explicitly requires that information. Add a factual authorship
  statement before freezing the final source identity.

## Heuristic assessment

**Possible Duplicated Code, nonblocking:**
`scripts/t80_foundation_reports.py:14` closely repeats the T79 collector's
manifest/replay and report-packaging logic. Shared security fixes will require
parallel edits. A narrowly shared packaging helper could reduce drift; retaining
separate immutable profile adapters is also reasonable if its identity/provenance
rationale is recorded. This is a judgment call, not a documented-standard breach.

No additional backend-neutral public-signature, Java 8 API, ownership or dependency
license violation was identified. Existing credential-string preparation was
already present at the baseline and is not attributed to this change.

Initial result: two documented-standard findings and one nonblocking possible
smell; final recheck required after the parent resolves the findings.
