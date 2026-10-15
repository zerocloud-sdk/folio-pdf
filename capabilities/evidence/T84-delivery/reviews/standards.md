# Issue #84 Standards review

Fixed point: `dfe7eaea98533aa12449e677d9612fc4b964091a`.
Review scope: the dirty-tree diff against that baseline and the new issue-owned
sources in [the publication manifest](../publication-source-paths.json), including
`scripts/t05-evidence-pin.properties`. No intervening commits existed.

An independent Standards reviewer found **zero documented-standard violations**.
The reviewed authorities were CONTRIBUTING, AGENTS, the domain context and
ADRs 0007, 0023, 0033 and 0040. Changes preserve public behavior seams, Java 8
interfaces, acceptance/runtime separation, offline defaults, independent checks
over actual PDFs, parent-brokered Providers, exact environment scope and provenance.
Tooling-enforced findings were excluded.

One nonblocking heuristic was identified: possible Duplicated Code in the new
Provider runtime/launcher witness test and the existing recovery witness test
in `FoundationReadinessCommandTest.java`. A fixture helper could consolidate
their setup. Resolution: retain these separate, small obligation assertions in
this bounded certification slice; the duplication is maintainability advice,
does not violate a documented standard and does not compromise the behavioral
checks. No source change or candidate invalidation is required.

Result: **PASS — zero actionable findings; one resolved advisory heuristic**.
This source review does not replace final certification or publication audits.

The extended delivery review found one actionable audit gap: JSON/YAML closure
checking did not cover local Markdown links. Resolution: the staged audit now
resolves inline, image and reference-style links, requires local target files
or directories in the resulting Git tree, and checks the indexed bytes of linked
files. Its actual verification count is retained with the publication audit.
The reviewer found no preservation, scheduling or publication-bound defects.

The follow-up review identified the retained contract's original absolute link
to its historical compiler skill outside the repository. Resolution: preserve
the contract unchanged, verify that one explicit installed skill and its SHA-256
separately, and record it as an external documentary prerequisite. Every other
local link must resolve inside the publication tree; unexpected outside targets
fail the audit. Final staged execution verifies both resolved audit findings.

The resumed final review found the recovery pin's transitive reference to the
Worker pin file. Resolution: update that identity with the three exact driver
pins, and pass the complete nine-file / 1,246-entry pin preflight before staging.
Fresh r2 certification preserves the r1 observations and their original freeze
and archives. The reviewer cleared the final-base containment changes, fresh
candidate runner, pin preflight and source-freeze helper with zero actionable
findings. Final certification and publication audits remain separate gates.

The annotation completion helper received zero actionable findings. It strictly
recollects seven completed tuples without changing their original bytes, observes
fresh before/after environment facts, and retries only the failed eighth tuple
at the original bounds. Its adoption proof names the retained successful leaves
and excludes the failed leaf. The publication review identified excessive trust
in the planned directory list. Resolution: reconstruct the exact permitted set
from successful state and candidate-bound adoption proofs; reject any ancestor
or descendant overlap with a failed tuple; and require source paths to match the
owned manifest. The follow-up reviewer confirmed zero unresolved findings.
Actual completion and staged closure proofs remain required before commitment.

The first helper attempt stopped on byte comparison of native observations.
Recursive comparison established that only `created_utc` and `process_maps`
differed. The corrected helper requires both raw observations to pass and
compares every other field, including all actually loaded-file hashes and engine
identity. Both originals and the failed helper attempt remain intact. The
reviewer cleared this narrow timestamp/ASLR normalization with zero findings;
the next execution uses a fresh `annotations-r3` directory.

That fresh execution passed: the [annotation adoption proof](../../foundation/T84-final-r2/annotations-r3/adoption.json)
retains seven strictly collected original tuples and one fresh tuple, with eight
tuples / 32 chains and actual matching before/after environment observations.
The successful refresh resumed by adopting all five completed obligations.

Run3's independent Standards source review passed with zero documented-standard
breaches and zero new helper findings. `resume-recovery.py` verifies ten retained
JDK 8/11 chain records and their referenced bytes; `continue-run3.py` checks the
frozen candidate, original-index hash, declared tuple/chain coverage and actual
environment consistency before strict combined publication. The final selector
and staged audit confine replay exclusions to named JDK 17 recovery attempts;
transitive staged closure rejects exclusion of any needed evidence. Annotation
adoption, issue ownership and size bounds remain enforced. The existing possible
Duplicated Code advisory remains resolved as nonblocking. Actual certification,
readiness and staged publication results are separate final receipt gates.

The final run3 receipt and criterion review passed independent Standards review
with zero new actionable findings. Actual retained records establish 108 tuples /
452 chains, Provider 8/40, the 39 owned source paths, fifteen preserved satisfied
obligations plus Providers and 23 unrelated blockers. Scope and limitations agree
with the approved contract. The two staged audits remain required: retain the
first actual result, restore its receipt link, restage the exact final selection,
and verify the complete index before the authorized commit.

The first actual staged audit rejected required final `candidate-identity.txt`
and `candidate-probe.json` files because its candidate-directory guard searched
whole path strings. The correction checks directory components only, retaining
the development/candidate-directory ban and all exact-selection, failed-attempt,
transitive-byte, link and size checks. Both independent reviewers cleared the
correction with zero actionable findings; no candidate or certification input
changed. The failed audit and its exact indexed helper source remain intact and
untracked. Fresh staged audits are required before commitment.

The resumed staged audits caught 191 ignored final evidence log files and
historical output-layout links in 952 immutable tool narratives. Only the
explicit selected logs were added. Adding aliases beneath sealed observations
was rejected before execution because it would violate exact retained-file
manifests. Resolution: separate navigable views and an explicit SHA-bound
archival mapping, with original reports and collection trees unchanged. Every
raw local occurrence maps to retained bytes; only destinations change in the
derived copies; unknown forms fail; PDF/raster facts and compressed 1-GiB input
identities are checked. The unchanged strict verifier checks all affected sealed
trees before/after generation and during staged auditing. Both reviewers cleared
the helper, selector and audit integration, including the compressed-input
correction, with zero remaining actionable findings. Actual generation and two
complete staged audit passes remain required. All failed audit logs and their
original indexed helper sources remain intact outside publication.

Actual view generation passed in `report-view-retention-run3-r2`: 952 reports,
5,432 invocation-context destinations, and 40 original sealed collections /
31,536 files verified before and after generation. The manifest and views are
publication-only aids outside all sealed trees; original certification remains
108 tuples / 452 records for the same frozen candidate. No completed tuple was
rerun. Final closure and the two actual staged audits remain precommit gates.
