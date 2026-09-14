# T75 Step 4 review and closure

The complete independent Standards and Spec reviews compare the uncommitted
implementation with fixed baseline `5b1603c435f11c40368f75b7b9a2777c9c5e9761`.
`review-archive-identities.json` retains their reports, inspected sources and
frozen review input. There were no intermediate commits.

Spec found two required successful declarations rejected by Native extraction:
absent/null optional Form Type values and direct declarations in the root
Namespaces array. The original two-case public Workflow RED observed both
failures. Minimal fixes retain strict validation of present values and required
indirect element/role-target namespace identity. Four focused cases passed;
all 109 public cases then passed in actual IN_PROCESS and HARDENED_WORKER modes.
The original initial test-compilation error is retained separately and is not
claimed as a behavioral RED.

Standards found no hard breach and one possible Mysterious Name in the private
shared CMap driver. Renaming it to inspectCMap changes no public surface or
behavior. A focused Maven verify passed 159 tests across Native, helper/Worker,
Stable/Preview and compiled artifact contracts. Spec and Standards closures
are CLEAN. The fixed fifteen-bound convenience values remain duplicated in
two Facade packages: adding a public cross-package helper would change the
frozen surface; this bounded tradeoff was reviewed and accepted.

The text plan now requires 120 cases per tuple: 109 Native, nine actual
IN_PROCESS Stable Facade and two artifact contracts. Its original expectation
RED and updated GREEN are retained. The complete frozen Foundation collector
regression passed 18 tests; its log is in the adjacent recorder-r6 group.

An early raw prequalification attempt used the preceding 107-case Native
source. Its first JDK 8 IN_PROCESS contract stage passed 118 cases, but recording
was intentionally interrupted when the two Spec findings arrived. No whole
tuple completed and no certification was published. Its original partial
output, interruption record and orchestration/logs remain under
`source-closure-archive-identities.json`. They are not passing certification.

Independent Standards and Spec sequencing reviews confirmed that Step 4 may
prepare the supported compatible candidate and its final platform contract,
then Step 5 freezes/stages it and collects the required actual eight tuples.
The goal does not require another redundant eight-tuple raw run before that
final certification. Existing qualified chains must substantively pass; the
candidate declarations alone supply no environment evidence. Acceptance,
commit, push and ticket closure still require the final eight text records,
five prior-obligation refreshes and every full validation/review gate. Any
identity-affecting later change requires refreshed certification.

The T19 acceptance observer's own font-entry allowance was also corrected to
cover its two 65,536-entry Identity mappings while preserving Native limit
enforcement. Its original public probe retains failure at 32 entries, success
at 131104 and unrelated-bound controls; 24 acceptance cases passed afterward.
The probe's original Java source and logs are retained, excluding generated
class files. This is observer compatibility, not downstream feature scope.

Every archive member was verified byte-for-byte against its original. Review
closures and sequencing decisions were read-only; reviewers did not execute
new test or mutation probes. These development observations do not replace
final candidate certification.

The final candidate metadata review is CLEAN on both axes. Standards found one
stale capabilities README paragraph grouping T13 with experimental profiles;
that paragraph now distinguishes current T12/T13 qualification and T19's
remaining dependencies. An actual focused inventory RED exposed old fixed
151-member/18-exclusion expectations. Only four counts changed to 157/17;
the unchanged validation path then passed. Final generate, validate and check
all passed. The original finding, closure sources, count RED/GREEN and both
independent metadata reports are retained with their archive identities.
