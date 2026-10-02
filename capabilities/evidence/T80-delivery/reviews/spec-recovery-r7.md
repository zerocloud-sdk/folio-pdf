# T80 Spec recovery review, revision 7

Authority: `/workspace/contracts/issue-80-contract.md`.
Baseline/current HEAD: `08928c8d08de677a99f092c4ebd6bbdaa64ef6ba`.
**Recovery source: PASS. Final execution/evidence remains pending.**

R7 replaces the unexecuted R6 retention proposal. It preserves the contract's
“All evidence claimed for the final candidate is current” and “Never relabel
historical observations as belonging to new artifacts” requirements: the staged
candidate, source, contract, harness and artifact identities remain mandatory;
retained consumer/recorder commands and JSON records must match exactly.

Every one of the eight baseline tuples invokes the original unchanged
independent collector again. No collection return is cached or replaced. The
seven completed tuples retain their original logs/artifacts; replay must produce
the same reports and records under equality guards. The failed original JDK 21
Worker observations remain unindexed. R7 expressly refuses a pre-existing
`jdk21-hardened_worker-r7` directory, then executes fresh public tests, recorder
and collector there. At this review that directory and an R6 execution log were
absent. Actual environment checks run before and after every JDK and must match
the retained identities. The clear-metadata and attachments refreshes use the
original route and fresh sibling directories after baseline completes.

The implementation of certify changes only directory creation and the failed
tuple's fresh path. Product source, tool policies, timeouts, record semantics,
scope rules and the independent collectors remain frozen. No new requirement,
scope creep or material missing guard was identified.

The future delivery audit checks the real eleven-obligation index for exactly
88 Native tuples and 352 complete passing chain records, the four actual Ubuntu
24.04/JDK environments, matching staged candidate/configuration identities,
ordered predecessor completion, fresh failure replacement and passing inventory
completion. It cannot establish those results before the underlying route
finishes. Aggregate security and final receipt remain the parent's completion
checks; release integration/publication remain outside scope.

Reviewed SHA-256 identities:

- R7 helper: `b8b7cc0e987d1cf7657316709ebb0659006209acc39f9f7b81fcf23cda2167d4`
- Future delivery audit: `df04d24b4ea29fd292effd4f82658ba155bd702b73565d8d06d7ab61e5099b75`
- Frozen Foundation route: `e8fac2672a383d525520bfe7f6dc64e06971c436d18a52a3a9f4f35be589e126`
- Candidate: `58efc286fa1b4134024d452a1ecfed78111a81a41c5c117b4837d90ffb353231`
- Contract: `4dff6a8ccc1e6923bbe5142b8da5eca0f2f779e4ccf2b18b504b9bd79282ee8d`

Only this report was added. No source, git, tracker or container was changed.
