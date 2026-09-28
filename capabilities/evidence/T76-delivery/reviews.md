# Issue #76 Standards and Spec review

Fixed comparison baseline: `0c8a713191d9b8d1685ca3dfbb8b56cc1e2d2f21`.
The implementation was uncommitted during review because the execution
contract permits one local commit only after all selected criteria pass.
Both reviewers inspected `git diff <baseline> --` and all nonignored new files.
The code-review skill ran its two independent axes in parallel.

## Standards

No actionable Standards findings. The changes preserve Java 8 and
backend-neutral public signatures, bounded detached values and ownership,
public Workflow/Facade tests, separate behavior/surface authorities,
acceptance-only tools and fixtures, actual Facade IN_PROCESS execution and
clean-room provenance. No baseline code smell warranted a refactor.

The reviewer rechecked the ProcSet and identity corrections below, the three
explicit artifact signatures, control counts and documentation. No new
documented-standard violation or actionable smell was found.

## Spec

Two initial findings were corrected before final certification:

1. AC1's independent Resource Inventory ordering coverage omitted ProcSet
   arrays. The original colors fixture now includes unsorted duplicate names,
   the observer preserves their declared order and direct identity, and two
   malformed array/member controls must fail as `resource-maps`.
2. AC1's detached indirect identities were recorded only as present/absent.
   Public ObjectReference equality now produces first-occurrence ordinals,
   compared with independently authored expectations and qpdf identities.
   A deliberate alias at `resources.6.identity` must fail. Collector regression
   coverage also changes the public identity from 5 to 0.

The reviewer independently collected the corrected development receipt:
syntax and semantic passed for ten products; standards and visual passed for
eight conforming products. The three new members are enumerated in
`JarContractIT`. No further scope creep or substantive implementation finding
remained. Development receipts do not certify the final candidate or its JDK
matrix; those remain separate required execution gates.

Final review totals: Standards 0 findings; Spec 2 corrected, 0 unresolved.

The full verification run later exposed four stale inventory-test count
assertions. Both reviewers checked the delta from 157 to 160 Stable/Preview
entries and from 17 to 16 exclusions. Both reported zero findings: these counts
follow the three new mappings and removed image exclusion without weakening
the existing assertions or changing implementation.

The first full image observation matrix passed its tests and all four chains,
then index publication rejected five corpus-local `{path, sha256}` objects as
missing repository-root references. Both reviewers checked the correction to
the unambiguous corpus-local `file` field and the public `merge-index`
regression. It preserves SHA-256 checks and the existing strict traversal;
altered catalogs and source PDFs still fail atomically. No new Standards or
Spec findings remained.

The acceptance-only correction requires fresh focused tests, full host Maven
verification and all 56 final-candidate certifications. The earlier matrix
runs are not represented as runs of the revised complete source tree.

An additional archive-byte comparison failed after restaging: 15 JARs and the
bundle changed; all seven POMs remained identical. JAR entries use staging-time
timestamps, but previous binary bytes were not retained, so neither payload
equality nor timestamps as the explanation for every differing byte is
claimed. The complete input comparison showed exactly ten acceptance-only
changes and unchanged shipped-code/build inputs. Spec review confirmed that
AC8's completed JDK matrix applies to those unchanged code/build inputs;
no additional matrix run or reproducible-build change is required on these
facts. Fresh certifications must bind the actual final artifact hashes.

The final Standards recheck independently collected all eight
`T76-final-r2/images` receipts and checked all 32 chain/report hashes against
candidate `f6d1d49997c4235c005ad7e8eee293227223cbeb663e1d0542637e8af3c5769a`.
Each tuple retains ten syntax/semantic and eight standards/visual products;
the semantic report also includes exactly the two hashed contract-test
records. The reviewer reproduced the ten acceptance-only input differences,
unchanged contract inputs and the T76-only log-retention exception. There
were zero actionable Standards findings. This read-only review changed no
files and did not rerun tests; remaining predecessor and readiness gates
were explicitly still pending at that point.

The final Spec recheck found no new actionable issue. Its read-only audit
verified all eight image tuples against the actual Ubuntu 24.04/JDK
8/11/17/21 identities, 38 passing public tests per tuple, all four passing
chains and 102 standards controls per tuple. Native execution matches the
tuple and Facade execution is explicitly IN_PROCESS. The reviewer checked
8,259 distinct file hashes across image manifests, referenced reports and
all 23 actual staged artifacts, and independently confirmed the ten declared
acceptance-only input changes and unchanged contract inputs. Pending
predecessor/readiness gates were not represented as completed.

## Final closure

After all 56 certifications were published and final inventory validation,
generation and consistency checks passed, both reviewers performed a read-only
closure review. Standards and Spec each reported zero actionable findings.
They independently confirmed eight certifications for images and each of the
six predecessors, 224 passing chain records, the final candidate identities,
seven satisfied readiness obligations and explicit Windows/macOS exclusions.
Spec also matched authority SHA-256
`31bfd8bacaa4dacf757d9b333386aab3b2acfe69450ca6541e278c0a76cae41a`.
The remaining global readiness blockers are unrelated to this completion.
No tests, source edits or duplicate transitive evidence scans were performed
during these closure reviews. The root's live readiness and aggregate audits
also passed for the selected obligations.
