# T80 Spec recovery review, revision 6

Authority: `/workspace/contracts/issue-80-contract.md`.
Baseline/current HEAD: `08928c8d08de677a99f092c4ebd6bbdaa64ef6ba`.
**Recovery plan: PASS for the reviewed pre-launch state. Final evidence pending.**

The contract requires “All evidence claimed for the final candidate is current”
and “Never relabel historical observations as belonging to new artifacts.”
R6 retains seven already completed R5 independent collections under their
original candidate, environment, configuration, execution mode and chain
identities. It does not claim another independent replay for those collections.
I verified all 28 current chain records and their report hashes, followed the
exact report references, and independently checked 38,370 unique nested
reference hashes with zero mismatches. Frozen certify writes no PASS record
until its unchanged collector completes. Its preserved JSON equality guards
refuse changes when reconstructing retained configurations, reports and records.

The original failed JDK 21 HARDENED_WORKER tuple has no chain records or
certification result declaration; it remains unindexed. At review,
`jdk21-hardened_worker-r6` did not exist. The fresh path runs all original public
tests, recorder and unchanged collector. Actual environment checks precede and
follow every JDK; equality with retained environment identities is mandatory.
Subsequent clear-metadata and attachments use the original route with fresh
sibling directories. No product source, acceptance policy or external timeout
is changed to obtain PASS.

The three successful exact visual replays support retrying that isolated
observation; they do not replace the failed tuple or establish its unconfirmed
root cause. Completion still requires the fresh eighth baseline tuple, both
remaining obligation matrices, current index/inventory and delivery receipt.

One optional defensive guard was communicated: explicitly refuse a pre-existing
fresh `-r6` directory at startup. There is no such directory in the reviewed
state, so this is not a current contract finding.

Reviewed SHA-256 identities:

- Candidate: `58efc286fa1b4134024d452a1ecfed78111a81a41c5c117b4837d90ffb353231`
- Contract: `4dff6a8ccc1e6923bbe5142b8da5eca0f2f779e4ccf2b18b504b9bd79282ee8d`
- R6 helper: `68c5c0baa298b9e2ce0ba36885ea127453b49d94c6b8793f46e75d920e154064`
- R5 execution log: `63aa69b0d1a1f8ce0831c3cf94385870760fcb2236bdbbcb673eeff3c4da9182`
- Ordered 28 scope/chain record-and-report identity entries: `ffde00fb99d2681a7a698258bb27920fb61993ed358a38302044464db6fe1701`

Only this review report was added; implementation, git, tracker and containers
were untouched.
