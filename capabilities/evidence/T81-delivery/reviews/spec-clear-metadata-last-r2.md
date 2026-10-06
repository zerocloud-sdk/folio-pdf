# Spec review — final-r2 clear-metadata recovery

Baseline/current HEAD: `05e7f546f5885680e333ad8d3dea3645b25d26b7`. Sole source: `/workspace/contracts/issue-81-contract.md`. Prospective assessment only; no tests, recovery execution, bound-source edits or final-delivery review.

Candidate: `73afedc590cf4764c32ef21a980a2c51ac7d12738951a6aa5a4470728aaba8e3`.
Contract: `5a5214f1734f0ae454abb49845bcb0823b055a1aca927079590a0c2c1b3830a0`.

Reviewed helper SHA-256:

- `resume-clear-metadata-last-r2.py`: `b05a2a3053f4f0745b13b73ce648d583525791b9f310c257b45ada675671a599`.
- `diagnose-clear-metadata-jdk21-worker.py`: `4e529a5829416c2987327094df1a6614dacc00a37b0ac8a73a22bea10e31677e`.

No concrete Spec execution blocker:

- For “Preserve their declared execution coverage and historical records,” the adaptation changes only original root, retained/fresh counts and current journal. Seven actual original scopes show 23-test PASS without failures and four expected-producer PASS records under the stated candidate/contract. Environment hashes match their original records. Original configuration inputs, exact commands, settings, staged/source guards and whole-tree byte/hash seal remain checked, including the failed suite.
- The failed JDK21 HARDENED_WORKER scope has no certification envelopes. Its complete 23-test suite, entire recorder and four-chain collector/live replay must run afresh. All four actual environments receive new opening/closing observations; original-before equality and every available original closing record remain checked. Missing original JDK21 closing observations are completed by fresh observations without rewriting originals.
- For “mismatched identities, altered findings or changed producer labels cannot produce PASS,” exact eight-scope/four-chain closure and unchanged transitive verification require the exact 80-scope union preserving all 72 prior scopes before unchanged locked publication. Audit PASS follows publication. Order remains clear-metadata after the first nine predecessors, before attachments and limits.
- Diagnostic execution reads the exact failed scope's full-suite command and replaces its sole writable mount with a fresh diagnostic directory. It records original command/failure hashes, actual exit and full-suite marker, and writes no certification/index. Its 1,800-second outer diagnostic wait supplies no acceptance; certification still uses unchanged 600/1,800-second suite/recorder ceilings and original product limits. Pressure snapshots do not establish cause or alter the cooperative guarantee.

The original 1,273.116-second suite retains its elapsed-limit failure at owner rewrite. Inspected `validation/clear-metadata-jdk21-worker-probe-r1-result.json` records diagnostic-only exit 0; its intact log reports all 23 tests passing in 161.438 seconds, with the original failure hash preserved. Neither that diagnostic pass nor pressure readings identify cause or substitute for fresh certification. Attachments, all four limits tuples and final gates/reviews remain required.
