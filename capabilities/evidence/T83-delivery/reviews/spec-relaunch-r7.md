# Spec review — baseline-relative R14/R15 relaunch

Read-only review against baseline `42fe5c797711070b319b9419e14fcb7adfa98735`,
the sole execution contract and the approved dirty tree. Two applicable findings
under the requirement to update the Capability Matrix, generated views and
authoritative English contracts together:

1. `docs/hardened-worker.md:18` still describes current recovery/scale status as
   experimental, contradicting the proposed compatible matrix and later current
   certification section. Correct or clearly date the statement. The dated
   GraalVM disclaimer around line 476 is historical and remains valid.
2. `capabilities/capability-matrix.yaml:2115` says all 165 mandatory cases, while
   the frozen inventory, runner, linked contract and provenance require 173.
   Synchronize this assertion and its generated view.

Both inputs are candidate-bound; corrections require restaging and fresh affected
certification. No scope creep or additional incorrect implementation was found
in the inspected Worker fix, scale retention, recorder/collector, checker,
ownership, Java 8 compatibility, Facade execution or provenance changes.

The R14 text closeout preserves the original ledger and transcript, strictly
collects eight sealed corpora, verifies existing reports, creates only the four
absent final-scope records, rechecks original hashes, obtains fresh closing
environment observations and uses guarded index publication. It creates an
actual closeout result without inventing the interrupted process exit status.

Remaining certification, readiness, audit and commit eligibility are pending
execution. The reviewer modified no files and ran no mutable helper, build or
test. These findings must be resolved and reviewed before the final freeze.
