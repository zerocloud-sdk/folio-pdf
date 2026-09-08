# T70 development checks

These are historical development observations, not final candidate certification.
The current candidate and all eight actual certification tuples are referenced by
`capabilities/foundation-evidence.yaml`. Logs here retain the Red→Green history;
no old finding is relabeled with a later candidate's hash.

- The initial Native baseline passed 39 tests; acceptance/Preview passed 24 + 3.
- Standards recorder cycles are numbered in the retained `standards-*` logs.
  Synthetic shell checkers exercise fail-closed control flow only. Qualification
  of real pdfcpu/Arlington executions is separately retained in
  `../T70-standards-qualification/`.
- `stable-red-01` observes the empty Stable artifact; `stable-green-01c` and
  `step3-focused` run actual Stable/Preview consumers and artifact contracts.
- `semantic-red-01` is the intentionally missing new inspector; `semantic-red-02`
  rejects an unqualified extra Catalog key after the initial one-page-only
  implementation. `semantic-green-02` checks both modes and the scope restriction.
- `receipts-red-02` reproduces a real Worker nested-Workflow receipt leak;
  `receipts-green-02` passes all 27 expanded Native contracts and the semantic
  test after the Worker callback failure is assigned outer receipts.
- `t03-red-01` precedes the new four-chain command. `t03-surface-green` runs the
  real qualified tools against Native and Stable products plus actual jars.
  `t03-red-02` detects the removed-rule coverage gap; `t03-green-02` passes the
  complete-union requirement and all 14 related acceptance tests.
- `foundation-red/green-01` covers complete candidate files and source changes;
  `02` covers staging byte-identical Maven artifacts into an unsigned zip;
  `03/04` cover missing chains and binding products/raw negative findings;
  `05` covers exact observed environment identity. Intermediate green attempts
  ending without a successful test result are not counted as verification.
- `preview-union-red/green` checks the manifest's disjoint tiers and effective
  Preview union against actual jars. `inventory-view-red-corrected/green` checks
  the generated effective Preview count. An earlier mistyped test selector ran
  zero tests and is deliberately not included as Red or Green proof.
- `build-binding-red/green` rejects source, artifact, contract or harness changes
  after staging. `stage-integrity-red/green/refactor` rejects changes during the
  build, excludes leftover jars from the runtime classpath, and restores the
  prior evidence index after successful or failed identity probes. Both fixes
  arose from the independent Standards/Spec review; all nine Python checks pass.
- `full-verify-01-failed` records a complete reactor attempt whose product,
  Facade and acceptance modules passed, followed by an obsolete inventory-test
  assertion that Stable mappings were still missing. `readiness-red` isolates
  that assertion; `build-tools-green` verifies all inventory/release-tool tests
  after updating the pre-T70 repository-state expectations. The failed full run
  is not counted as a passing delivery gate.
- `full-verify-02-stopped` was stopped by the implementing agent after the first
  container certification exposed missing ImageMagick host libraries. The
  comparator wrapper and its staged runtime identity needed updating before a
  new candidate could be built; this incomplete run is not counted as PASS.
- `imagemagick-wrapper-red/green` rejects absent, changed, and extra private
  comparator libraries; `comparator-classpath-red/green` keeps recorded native
  comparator dependencies outside the Java classpath. All eleven related Python
  checks pass. Actual ImageMagick startup in the four pinned images is retained
  in `../T70-comparator-runtime/`; it is a dependency qualification observation,
  not a replacement for the final four-chain candidate certification.

One early pair of overlapping Maven builds competed for the document test output
and produced a ClassNotFoundException. Those build results were discarded. All
subsequent reactor builds are serialized, and the successful focused rerun is
retained above. No test result from a skipped/mistyped selector is counted.
