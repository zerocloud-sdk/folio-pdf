# Issue #76 validation receipt

Execution contract: `/workspace/contracts/issue-76-contract.md`.
Comparison baseline: `0c8a713191d9b8d1685ca3dfbb8b56cc1e2d2f21`.
The baseline matched before implementation and the worktree was clean.

All selected Completion criteria passed. The Foundation evidence authority and
retained raw observations govern the final candidate; this receipt records the
completed commands, checks, failed attempts and remaining release limitations.

Completed command output is retained under [validation-logs](validation-logs/)
with hashes in the completed [log manifest](validation-log-manifest.json).

## Environment preparation

Commands use an explicit repository-local Maven cache, the complete pinned
HarfBuzz installation selected by `FOLIO_HARFBUZZ_HELPER`, and host PyYAML 6.0.2.
The final observer uses the separately selected, hash-verified Ubuntu Python
runtime through `FOLIO_FOUNDATION_PYTHON_ROOT`. No host Python or immutable
Foundation image was modified. Podman commands run outside the filesystem
sandbox because rootless user namespaces require that environment capability.

The repository Python provisioner reproduced the selected 626-file runtime
byte for byte. The original codec author reproduced all five retained payloads.
The qpdf, pdfcpu, Arlington, PDFium, ImageMagick and native helper pins were
provisioned before observations. Certification itself runs without networking.

The execution shell used these explicit local prerequisites:

```sh
export MAVEN_USER_HOME=/workspace/folio-pdf/.build-cache/maven
export MAVEN_OPTS=-Dmaven.repo.local=/workspace/folio-pdf/.build-cache/maven/repository
export FOLIO_HARFBUZZ_HELPER=/workspace/folio-pdf/.build-cache/issue76/harfbuzz/bin/folio-harfbuzz
export FOLIO_FOUNDATION_PYTHON_ROOT=/workspace/folio-pdf/.build-cache/issue76/python
export PYTHONPATH=/workspace/folio-pdf/.build-cache/issue76/python-tools/pyyaml-6.0.2/lib
export PYTHONDONTWRITEBYTECODE=1
```

The seven final certification commands use this exact invocation, once for each
listed obligation. Each command observes all four required JDKs and both Native
execution profiles, then verifies and atomically publishes its evidence:

```sh
for obligation in images transactions values pages metadata annotations text; do
    python3 scripts/t03-foundation.py certify \
        "capabilities/evidence/foundation/T76-final-r2/$obligation" \
        --obligation "$obligation"
done
```

The retained output directories are immutable observations. A new execution must
select fresh directories; reusing these directories is intentionally rejected.

## First staged candidate (historical publication attempt)

- Candidate: `7dc93f7e3d0c45900fb0f76883a66321d5843b13a8418275115f9f432d61fa87`.
- Contract: `a18266f96986a053ae637b22d320c1116e9b6640486c82f47877d188a3b14efb`.
- Build-input receipt SHA-256:
  `8c058f57e8edcdc0d16621962238dab9bdbe6e3f3da8808c9615748037f6be84`.
- Retained [build inputs](staged-candidate/build-inputs.json),
  [exact build command](staged-candidate/build-command.json) and
  [build output](staged-candidate/build.txt).

This first staged candidate has 23 artifacts. Its acceptance harness and selected
observer runtime have 650 recorded inputs. The 2,033 source files and 30
contract files match the inputs used for the completed full JDK verification
runs. It is retained as historical evidence; final certification uses a fresh
candidate after the acceptance catalog correction described below.

## Final staged candidate

- Candidate: `f6d1d49997c4235c005ad7e8eee293227223cbeb663e1d0542637e8af3c5769a`.
- Contract: `a18266f96986a053ae637b22d320c1116e9b6640486c82f47877d188a3b14efb`.
- Build-input receipt SHA-256:
  `818e0850b4c1b485f35ad4ef704547c52d922d74ed71fed3eae930d33b4d3243`.
- Retained [build inputs](staged-candidate-r2/build-inputs.json),
  [exact build command](staged-candidate-r2/build-command.json),
  [build output](staged-candidate-r2/build.txt),
  [final full-verification input manifest](staged-candidate-r2/full-verification-inputs.json)
  and [exact matrix input comparison](staged-candidate-r2/matrix-input-comparison.json).

The final candidate has 2,033 source inputs, 30 contract inputs, 23 shipped
artifacts and 650 acceptance-harness/runtime inputs. Its source and contract
inputs match the final full host verification. All 56 fresh certifications are
published under `foundation/T76-final-r2`; no first-attempt receipt is relabeled.

## Completed focused validations

| Command or check | Outcome |
| --- | --- |
| `./mvnw -B -ntp -pl pdf-document -am -Dtest=ImageResourceExtractionWorkflowTest -Dsurefire.failIfNoSpecifiedTests=false test` | Passed, 28 Native tests, including the exact ICC metadata decompression boundary |
| `./mvnw -B -ntp -pl pdf-acceptance -am -Dtest=ImageResourceFacadeTest -Dsurefire.failIfNoSpecifiedTests=false test` | Passed, eight public Facade tests |
| `./mvnw -B -ntp -pl pdf-acceptance -am -Dtest=T14EvidenceCommandTest,AcceptanceEvidenceCommandTest -Dsurefire.failIfNoSpecifiedTests=false test` | Passed, three new producer tests and 24 existing recorder regression tests |
| `python3 -m unittest scripts.tests.test_t03_foundation scripts.tests.test_t10_foundation scripts.tests.test_t14_foundation` | Passed, 23 affected script tests after the catalog correction |
| Development independent recorder and collector, `product-probe10` | Passed after the catalog correction: ten syntax/semantic products, eight standards/visual products, 102 standards controls, seven semantic controls and two visual controls; development qualification only |
| Standards and Spec review against the fixed baseline | Standards: zero findings. Spec: two corrected, zero unresolved. See [review receipt](reviews.md) |
| `./scripts/inventory validate` | Passed: 23 capabilities, 160 Facade surfaces, 16 exclusions |
| `./scripts/inventory generate` and `./scripts/inventory check` | Passed again after all final certifications were published |
| `./scripts/inventory readiness` | Exit 1 for unrelated release obligations; zero blockers for images or any of the six refreshed predecessors, and zero release identity/artifact diagnostics |
| `git diff --check` | Passed on the final diff against the recorded baseline |
| Full `./mvnw -B -ntp verify` through `./scripts/verify-jdk-matrix.sh` | All four pinned JDKs passed: 1,527 tests each, zero failures/errors, four default opt-in skips each |
| Fresh final-tree `./mvnw -B -ntp verify` | Passed after the acceptance catalog correction: all reactor modules, 1,527 tests, zero failures/errors, four default opt-in skips, 22:01 minutes |
| Pinned JDK 17 `./mvnw -B -ntp -pl pdf-document -am -Dtest=UnicodeCompositionWorkflowTest -Dsurefire.failIfNoSpecifiedTests=false test` | Passed unchanged: 48 tests, zero failures/errors/skips, 95.90 seconds |
| Verification-to-staging input comparison | Passed: all 2,033 source files and 30 contract files unchanged |
| `python3 scripts/t03-foundation.py stage` | Passed: 23 unsigned local candidate artifacts and 650 harness/runtime inputs; staged inputs match the full verification manifest |

An additional broad Python discovery run attempted 222 tests without unrelated
suite prerequisites and reported six failures and 17 errors. It was not a
selected gate: those suites require separate shaping/font tooling, writable
Maven configuration and sandbox-external canaries. The affected T10 tool-catalog
assertion was corrected; the 22 affected script tests then passed. A further
seven focused environment/catalog/canary/font-author checks passed after
selecting their pinned dependencies. This broad exploratory run is not reported
as a passing validation or substituted for Maven/JDK/certification gates.

## Full verification attempts

The complete JDK matrix passed across the two retained command attempts.
The four default skips are three opt-in Worker scale cases and the opt-in
T30 raster run (`t30.raster`). All required image certification tests are
separate explicit executions.

- The first host run completed every product/acceptance module successfully, then found
  four stale inventory count assertions. Those were corrected from 157/17 to
  160/16. All 17 inventory tests and all 11 release-tool tests passed on the
  focused reruns; the latter required sandbox-external temporary GPG sockets.
  The matrix runs execute the entire reactor again.
- The first matrix attempt passed JDKs 8 and 11, then the existing
  `completeCjkFontsUseTheExplicitRegionAndPreserveSubsetMappings[HARDENED_WORKER]`
  case reported a Workflow elapsed-time failure on JDK 17. Its test record
  measured 5,080 seconds; the policy limit remains five minutes. The unchanged
  48-test Unicode suite passed on an immediate rerun in the same pinned image.
  The cause of the unusually long interruption is not established. The complete
  JDK 17 rerun passed, including all 48 Unicode tests in 92.87 seconds. JDK 21
  also passed through `./scripts/verify-jdk-matrix.sh 17 21`; the failed attempt
  is retained.

## Acceptance catalog publication regression

The first image matrix in `foundation/T76-final-r1/images` completed all eight
actual environment/profile tuples: 38 public tests and four independent chains
passed in every tuple. The final atomic index publication then rejected five
corpus-local `{path, sha256}` objects because that exact shape denotes a
repository-root evidence reference. The authority was not changed by the
failed publication, and every original observation remains untouched.

A deterministic regression invokes the public `merge-index` command with the
actual frozen catalog and explicit source-PDF references. It failed before the
fix and passed afterward. Renaming the corpus-local filename field to `file`
removed the ambiguity; the Java producer, recorder and collector still verify
the source hashes. The generic evidence traversal and publication guard are
unchanged. The regression also verifies atomic rejection of modified catalog
and PDF bytes. Every authored PDF and PNG byte remains identical.

The correction changes acceptance inputs only. The final pipeline repeated
focused Java and Python checks, the independent development recorder, full
host Maven verification and all 56 certifications; all passed.

An additional archive-byte comparison after restaging failed: 15 JARs and the
bundle changed, while all seven POMs remained identical. JAR entry timestamps
use staging wall time and no `project.build.outputTimestamp` is configured.
Previous archive bytes were not retained, so the receipt does not assert old
and new payload equality or that timestamps explain every differing byte.
The [archive comparison](archive-comparison.json) retains both sets of hashes
and the actual new timestamps.

The complete source-input comparison proves exactly ten acceptance-only
changes: the two corpus catalogs, two Java acceptance readers/pins, four T14
author/recorder/collector/pin files, the public index regression and its
collector protocol ZIP. Every shipped-code and build input remains identical
to the completed four-JDK matrix. Spec review confirmed that those matrix
runs satisfy AC8 for the unchanged shipped code/build inputs. The revised
complete source tree passed fresh full host verification; all final
certifications must bind the actual restaged artifact hashes. No archive
identity is relabeled or reused as certification.

## Final certification and readiness

All seven `certify` commands above exited successfully. The final text command
published eight new certifications and retained all 48 current certifications
from the other obligations. Its strict publication guard checked every
transitive evidence reference before atomically updating the authority.

| Obligation | Actual JDK/profile tuples | Contract-test executions | Passing chain records |
| --- | ---: | ---: | ---: |
| images | 8 | 304 | 32 |
| transactions | 8 | 272 | 32 |
| values | 8 | 664 | 32 |
| pages | 8 | 520 | 32 |
| metadata | 8 | 584 | 32 |
| annotations | 8 | 376 | 32 |
| text | 8 | 1,008 | 32 |
| Total | 56 | 3,728 | 224 |

The [final audit](final-certification-audit.json) checks the exact scope set,
candidate/contract identities, configuration and report hashes, actual modes,
passing test counts, staged inputs and historical preservation. It records the
completed strict publication's transitive-integrity result rather than repeating
that unchanged, expensive verification. All 8,289 first-attempt files and all
baseline historical receipts remain unchanged.

The final `inventory validate`, `generate` and `check` commands passed.
Live `inventory readiness` returned 1 because Foundation 0.1.0 remains globally
NOT READY. The [readiness summary](readiness-summary.json) records zero blockers
for images, transactions, values, pages, metadata, annotations and text, and zero
`BLOCKED release` identity/artifact diagnostics. Remaining diagnostics concern
other behavior obligations and separate repository/release certifications,
including #96/#97. Windows x86-64 and macOS x86-64/arm64 remain explicitly
uncertified and nonblocking for Foundation 0.1.0.

Authority SHA-256:
`31bfd8bacaa4dacf757d9b333386aab3b2acfe69450ca6541e278c0a76cae41a`.

## Completion evidence map

Every criterion below passed through these observable seams:

| Criterion | Evidence |
| --- | --- |
| AC1: complete detached inventory | Independent literal corpus expectations, qpdf graph/inventory observations, equality-based indirect identity ordinals, direct ProcSet occurrences, public Native/Facade and reopened observations, defensive-copy assertions |
| AC2: matching Facade | Three enumerated Stable members, Preview inheritance, eight public Facade tests and exact artifact signatures |
| AC3: independent fixtures and format success | Original author scripts and frozen hashes; five real encoded codec payloads; successful simple filter/predictor decoding; separate unsupported/external classifications; independently expected rasters |
| AC4: bounds and atomic failures | All 28 Native tests in each actual execution profile, including exact/first-excess limits, staged decompression, ICC metadata, overflow, cycles, conflicting reuse and unattempted failed targets; eight Facade tests |
| AC5: independent environment-bound certification | Four separate chain records for each of eight actual Ubuntu 24.04 x86-64/JDK/profile tuples; retained original process evidence, qualified controls, product/tool/environment/candidate hashes and actual Facade IN_PROCESS mode |
| AC5: predecessor freshness | Eight same-candidate certifications each for transactions, values, pages, metadata, annotations and text; historical receipts untouched |
| AC6: public behavior and ownership | Native/Facade observations, detached lifecycle, caller streams, earlier commands/patches, unchanged Sources, reopened semantics and committed Publication Receipts; no applicable external Provider seam |
| AC7: synchronized authorities and docs | Capability, Facade and Foundation authorities; generated documentation; English and Chinese contracts; provenance; final readiness diagnostics |
| AC8: required validation and review | Focused commands above, full Maven verification on all four JDKs, inventory gates, actual certification commands and zero unresolved Standards/Spec findings |
| AC9: Java 8, licensing and scope | Java 8 compilation/runtime and artifact tests, provenance and original fixture licenses; acceptance-only tooling excluded from runtime artifacts; no downstream implementation or publication |

- [x] AC1–AC4: inventory, matching Facade, independent fixtures and format success,
  all specified bounds, detached ownership and atomic safe failures.
- [x] AC5: all eight actual image tuples, four independent chains each, actual
  Facade IN_PROCESS execution, qualified controls and final artifact identities.
- [x] AC5 predecessor freshness: all 48 required predecessor tuples refreshed
  for the same final candidate; historical receipts preserved.
- [x] AC6–AC7: public behavioral equivalence, ownership and publication semantics;
  synchronized authorities, English/Chinese documentation and provenance;
  zero image-owned readiness blockers.
- [x] AC8 focused gates: exact Native command, new Facade/producer tests,
  existing recorder regression and all 23 affected Python tests passed.
- [x] AC8 inventory gates: validate, generate and check passed; live readiness
  diagnostics inspected and unrelated release work left outside scope.
- [x] AC8 full gates: fresh full host verification and required four-JDK matrix
  passed with the source-input qualification described above; actual final
  image certification and all predecessor refresh commands passed.
- [x] AC8 review: Standards and Spec findings resolved; both final closure
  reviews reported zero actionable findings.
- [x] AC9: Java 8 compatibility, explicit ownership, clean-room provenance and
  runtime separation preserved; downstream and release scope excluded.
- [x] Final diff reviewed against the baseline and contract. The
  [scope/retention audit](scope-and-retention-audit.json) records 20 changed
  tracked files, 100 reviewed new non-evidence files, no unrelated changes and
  no ignored paths among 270,866 checked new files after delivery finalization.
- [x] Commit authorization respected: no commit was created. CONTRIBUTING.md
  requires a real-name DCO sign-off; no Git identity or operator-supplied
  sign-off identity was available. HEAD remains the recorded baseline.

## Residual limits and handoff

The extraction scope retains its explicit unsupported/external decoded-data
classifications; successful supported decoding, encoded codec extraction and
independent rasters are certified. Other Foundation obligations remain blocked,
and Windows/macOS are not certified. Failed exploratory attempts and the
unexplained initial JDK 17 timeout remain documented above; the selected
validation gates and unchanged reruns passed.

Certification binds the actual local artifacts in `target/foundation-0.1.0`.
Changed source or artifact identities require fresh certification. Archive
equality with the earlier staging attempt is not claimed.

The worktree contains only this ticket's changes. Implementation and evidence
are left uncommitted for the orchestrator to sign off and commit with an
appropriate identity.

No push, pull request, merge, tracker mutation or release publication is
authorized by this execution. Those actions remain with the orchestrator.
