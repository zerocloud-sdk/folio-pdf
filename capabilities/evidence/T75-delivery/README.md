# T75 extraction delivery evidence

Issue: [#75](https://github.com/zerocloud-sdk/folio-pdf/issues/75).
Fixed implementation and review baseline:
`5b1603c435f11c40368f75b7b9a2777c9c5e9761`.

This directory preserves development observations and independent review
history. The frozen public contract is [T13 certification](../../../docs/t13-certification.md).
Candidate-specific certification is governed by the separate
[Foundation evidence authority](../../foundation-evidence.yaml).
Development PASS records, historical archives and synthetic collector protocol
fixtures do not certify a later candidate.

The original baseline passed the focused Native extraction CI suites on all
four JDKs but failed the inventory generated-readiness check in all four jobs.
That failure was reproduced locally and remains part of the delivery history;
the baseline full build is not represented as passing.

The main evidence groups are:

- `encoding-independent-review`, `facade-independent-review` and
  `corpus-independent-review`: public behavior, frozen member set and original
  corpus review.
- `font-corpus-r1` and `font-corpus-r2`: original embedded-font qualification.
- `standards-r1` through `standards-r12`: independently reviewed declaration,
  font-program, content and logical-structure predicates and retained controls.
- `semantic-controls-r1` and `visual-controls-r1`: source-derived defects and
  separate independent graph and raster expectations.
- `recorder-r1` through `recorder-r5`: original production, original-byte
  retention, tool/publication identity and whole-run evidence qualification.
- [recorder-r6](recorder-r6/README.md): public Foundation collector RED/GREEN
  history, detailed four-chain checks and independent review closure.
- [step4-review](step4-review/README.md): complete review against the fixed
  baseline, optional-declaration fixes, public regressions and candidate
  sequencing review.
- [final-review](final-review/README.md): original final-source findings,
  appearance association and cross-page regressions, independent closure and
  the byte-verified interrupted first candidate record.
- [observer-budget](observer-budget/README.md): full-verification failures,
  public budget controls and revalidation of existing extraction observers
  under the approved Identity CMap accounting.
- [delivery-readiness-review](delivery-readiness-review/README.md): independent
  development-evidence packaging and 28-criterion coverage checks before final
  candidate certification and delivery.
- [final-validation](final-validation/README.md): complete validation logs,
  original test reports and the tested source/contract identities.
- [git-retention](git-retention/README.md): original-byte retention attributes,
  actual Git whitespace controls and independent review closure.
- [final-candidate-r2](final-candidate-r2/README.md): the fresh unsigned candidate
  and acceptance harness built after the final source review closures.
- [worker-batch-timeout](worker-batch-timeout/README.md): the original JDK 8
  Worker method timeout, measured diagnosis, preserved scenario split and
  independent closure of the complete 126-case development regression.
- [final-validation-r3](final-validation-r3/README.md): refreshed complete
  validation after the Worker batch-test and contract-count closure.
- [final-candidate-r3](final-candidate-r3/README.md): the fresh candidate and
  126-case text contract built after the complete refreshed validation.
- [certification-review-r3](certification-review-r3/README.md): independent
  review of completed actual text tuples on the fresh candidate, with precise
  scope boundaries and original evidence identities.
- [final-delivery-review-r3](final-delivery-review-r3/README.md): historical r3 source
  review continuity, evidence-retention preflight and final delivery review
  records with their explicit remaining gates.
- [final-certification-r3](final-certification-r3/README.md): historical actual
  certification commands, original process results and published-index
  observations on the unchanged candidate.
- [inventory-read-bound](inventory-read-bound/README.md): the final inventory
  text-readiness failure, bounded reader correction, original RED/GREEN results
  and independent five-file review closure.
- [final-validation-r4](final-validation-r4/README.md): new complete validation
  after the independently reviewed inventory reader correction.
- [final-candidate-r4](final-candidate-r4/README.md): the refreshed candidate,
  artifacts and harness bound to the complete r4 validation inputs.
- [final-certification-r4](final-certification-r4/README.md): actual refreshed
  certification commands, results and dated authority snapshots.
- [certification-review-r4](certification-review-r4/README.md): independent
  current raw-evidence and separate actual-publication reviews.
- [final-inventory-r4](final-inventory-r4/README.md): actual generate, validate
  and check results, with all six required readiness rows explicitly satisfied.
- [final-delivery-review-r4](final-delivery-review-r4/README.md): final independent
  review continuity and the separately required completed-evidence/index scope.
- [execution receipt](final-execution-receipt.md): current validation, all 28
  completion criteria, remaining delivery gates and limitations.

Each archive identity file records the original location, archive digest and
member digests. Archive retention compared every member byte-for-byte with its
original source. Local paths in original logs and receipts are preserved as
historical observations. The corresponding archives retain their original
bytes even after the local temporary directories are removed.

Two early derived annotations graph summaries were overwritten during the
historical r3 review. Their original bytes are unavailable; the
[incident and closure record](certification-review-r3/annotations-reader-output-incident-and-closure.json)
preserves the precise exception. Certification originals remain intact, and a
fresh inspection is recorded as a new read, not recovery of those summaries.

The historical `recorder-r2` review archives retain 24 project-owned Java class
snapshots used to bind observer identities and identity negative controls. Nine
Python import caches remain inside the original `recorder-r3`, `recorder-r4`
and `standards-r12` review archives as part of their original helper history,
including the recorded import-shadowing failure. Their original manifest
identities and archive bytes remain unchanged. These archived historical
entries are not inputs to the current candidate or acceptance harness runtime.
Six archived hard-link entries preserve original publication negative controls;
each target is an actual regular member within the same archive.

Final candidate certification, complete validation and fresh Standards and Spec
reviews are separate delivery gates. The authorized DCO commit, push and issue
closure require all of them to pass; development evidence cannot replace them.
