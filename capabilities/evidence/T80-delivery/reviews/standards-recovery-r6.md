# T80 Standards recovery review, revision 6

Baseline/current HEAD: `08928c8d08de677a99f092c4ebd6bbdaa64ef6ba`.
Read-only review of `.build-cache/t80-resume-security-r6.py`, the unchanged
certification/collector functions and one indexed completed report set.
The complete issue-80 contract and repository Standards/ADRs from revision 4
remain authoritative. Frozen product/source review remains PASS.

**One unresolved recovery-evidence finding; zero new product/source findings or
actionable heuristic findings. Final certification remains pending.**

- **P2 — Authenticate reused completed-chain records against prior trusted
  evidence, or replay their collector.** In `recover_collection`,
  `record = json.loads(record_path.read_text())` reads each current record and
  `checked_reference(record['report'])` validates only hashes supplied by that
  mutable record. `foundation.reference(root, record_path)` then records today's
  hash without comparing it with an independently established R5 identity.
  A changed report, its referenced findings/manifest and the record's report
  hash can be resealed together and satisfy these checks without external
  replay. Restoring the certify additions with deep equality preserves those
  current bytes but does not authenticate their previous verdict. The sole
  contract requires rejection of “changed/resealed findings”; ADR-0023 requires
  independent Acceptance Evidence chains. Retain independently trusted R5
  digests for every reused chain/report, or invoke the unchanged collector.
  This finding applies to the local recovery helper, not the timeout or product.

For the inspected JDK8 IN_PROCESS tuple, the four reports cover all 5,467 raw
retained-manifest entries; missing original-file coverage was not identified.
The concern is the missing prior digest anchor, not missing nested-reference
hash checks. Original record/configuration identities and restoration checks,
the fresh `jdk21-hardened_worker-r6` route, preserved failed directory, exact
live before/after environments and fresh later obligations otherwise appear
sound. Three passing visual replays do not establish the timeout's cause or
complete another certification. No source/settings/git/tracker changes,
tests or containers were launched by this reviewer.

Reviewed SHA-256 identities:

- R6 helper: `68c5c0baa298b9e2ce0ba36885ea127453b49d94c6b8793f46e75d920e154064`
- Frozen route: `e8fac2672a383d525520bfe7f6dc64e06971c436d18a52a3a9f4f35be589e126`
- Build receipt: `df165afd20faf26745314e35480a81e88be7f1165891fb8f1db4a318af6b84ea`
- Candidate: `58efc286fa1b4134024d452a1ecfed78111a81a41c5c117b4837d90ffb353231`
- Contract: `4dff6a8ccc1e6923bbe5142b8da5eca0f2f779e4ccf2b18b504b9bd79282ee8d`
