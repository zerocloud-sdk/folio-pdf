# T80 Standards recovery correction, revision 7

Baseline/current HEAD: `08928c8d08de677a99f092c4ebd6bbdaa64ef6ba`.
Read-only review of `.build-cache/t80-resume-security-r7.py`, its complete delta
from reviewed R5, and the frozen certification/collector functions.
The sole issue-80 contract and revision-4 repository Standards/ADRs remain
authoritative. No product/source change is involved.

**PASS for this source/recovery snapshot: zero unresolved documented-standard
findings and zero actionable heuristic findings. The R6 finding is resolved.
Final certification remains pending.**

R7 removes the proposed completed-collection cache entirely. It does not replace
`foundation.collect_reports`; the unchanged independent collector executes for
all eight baseline tuples, including the seven previously completed ones.
Consequently, changed/resealed findings face the original external-predicate
replay before a chain record can pass. This resolves the missing trusted-digest
anchor identified in `standards-recovery-r6.md`. The parent reports that R6 was
never executed; R7 grants no authority to reuse its proposed cached verdicts.

Relative to R5, only existing-directory recovery and the exact fresh JDK21
HARDENED_WORKER scope suffix change the certification body. A startup guard
requires `jdk21-hardened_worker-r7` to be absent. That tuple must execute the
original tests, recorder and collector; the failed original directory remains
unindexed and preserved. Existing JSON must match deeply and existing command
logs require exact commands/configurations and complete suite declarations.
All four exact live environments are reobserved before and after their tuples;
the staged candidate, contracts, harness and artifacts remain identity-checked.
Clear-metadata and attachments use the original route in fresh directories.

These guards preserve ADR-0023's independent evidence and ADR-0040's actual
environment boundaries without changing timeout settings, secure defaults or
the frozen source. The original timeout's cause remains unconfirmed. This is a
prelaunch code review, not proof that the resumed certification has passed.
No source/git/tracker mutation, tests or containers were performed by this
reviewer; only this report was added.

Reviewed SHA-256 identities:

- R7 helper: `b8b7cc0e987d1cf7657316709ebb0659006209acc39f9f7b81fcf23cda2167d4`
- Frozen route: `e8fac2672a383d525520bfe7f6dc64e06971c436d18a52a3a9f4f35be589e126`
- Build receipt: `df165afd20faf26745314e35480a81e88be7f1165891fb8f1db4a318af6b84ea`
- Candidate: `58efc286fa1b4134024d452a1ecfed78111a81a41c5c117b4837d90ffb353231`
- Contract: `4dff6a8ccc1e6923bbe5142b8da5eca0f2f779e4ccf2b18b504b9bd79282ee8d`
