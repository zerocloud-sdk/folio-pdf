# T23 implementation validation and review receipt

Fixed comparison base: `d6eeeb8bff7104bd370389eb835b7f6979c5fd1e`.
Delivery is an uncommitted diff on `main`; no push, PR, tracker write, or
commit is authorized or performed.

Focused validation executed the public
`RenderingWorkflowContractTest` in both IN_PROCESS and HARDENED_WORKER modes:
68 parameterized executions passed with no failure, error, or skip. The suite
covers the declared geometry, pixel profiles, annotation policy, lifecycle,
stream failure, publication, Provider, shared-resource, cancellation,
concurrency, hostile-image, and Worker-transport behavior. The acceptance
regression suite executed 22 tests with no failure, error, or skip.

`./scripts/acceptance /tmp/folio-t23-final-20260905-1202` completed successfully
against the repository-pinned qpdf 12.4.0, PDFium CLI v0.11.2 backed by
chromium-7881, and ImageMagick 7.1.2-30 distributions. All three T23 syntax and
visual chains passed. Only the T23 records and artifacts were copied from that
isolated output. The actual results and retained comparisons are linked from
[T23 evidence](T23-page-rendering.md).

`./scripts/inventory validate`, `generate`, and `check` all passed for 17
capabilities, 12 facade surfaces, and 16 exclusions. The final
`pdf-document` JAR contains 601 Worker classes; its sorted class closure is
byte-for-byte equal to `META-INF/folio-pdf/document-worker-classes`, whose
SHA-256 is
`6755b30eae00e6c0c4ee773568ef85540aa2ba96b342499f98f5040553ed6d27`.
The controller constant and hardened-Worker documentation carry that same
digest.

`./mvnw -B -ntp verify` completed successfully for all 10 reactor modules.
The Document Engine ran 617 tests with no failures or errors and skipped only
the three existing opt-in T22 scale profiles; its two JAR/public-API integration
tests also passed. The run included the 68 Rendering executions and all 22
acceptance tests. Conversion, Migration Facade, inventory, and release-rehearsal
checks also passed.

`./scripts/verify-jdk-matrix.sh` completed successfully on Eclipse Temurin JDK
8, 11, 17, and 21 container images. Each version ran the complete ordinary
reactor verification, including the same 617 Document Engine tests, 68
Rendering executions, Worker boundaries, public API checks,
acceptance regressions, generated-inventory check, and release rehearsal.
Ordinary verification skips the three opt-in T22 scale profiles and does not
claim large-scale certification. Different container JVMs conservatively make
the T23 image-profile fixture INDETERMINATE when its generated input differs
from the repository-pinned input hash; that regression verifies that a changed
input cannot be recorded as PASS and is not the isolated acceptance evidence
above.

Final Standards and Spec reviews covered all 41 tracked and 55 untracked files
from the fixed base, including the binary evidence. Both axes passed with no
unresolved actionable correctness, security, specification, or acceptance
finding. Missing formal evidence and promotion gates remain explicit in the
T23 evidence, so the capability remains experimental.
