# Spec source review r2 — issue #81

Baseline and observed HEAD: `05e7f546f5885680e333ad8d3dea3645b25d26b7`.
Read-only follow-up against `/workspace/contracts/issue-81-contract.md`, reviewing the fixes to all four findings in `spec-source-r1.md`. Product implementation remains unchanged. This is source review, not artifact/environment certification.

No remaining Spec finding in the reviewed fixes:

1. **Original identity binding — resolved.** For “mismatched identities … cannot produce PASS,” `scripts/t20_foundation_reports.py:87` now derives the expected JDK tuple, checks the original environment/profile and compares the entire candidate/configuration/input closure against current staging. `:116` observes actual environments before and after replay and compares them with the original. `scripts/tests/test_t20_foundation.py:116` rejects candidate, environment, image, locale, timezone, input and artifact changes; `:172` rejects resealed altered findings.

2. **Aggregate indirect objects — resolved.** For “aggregate accounting across primary/additional Sources, Commands, Queries, Patches, products and Targets,” `T20ResourceContracts.java:213` observes at least one added indirect object after a Command under the finite default; parent aliases may increase the total. Its complementary ceiling remains three, so the first new observation must fail without resetting admitted Source usage. Primary equality three/first-excess two remain independent. `:228`, `:237` and `:512` cover donor/product aggregation and exhaustion; the product failure must precede STAGED, excluding later per-file validation as its cause. Removing the redundant positive split case preserves this proof. `coverage.json` explicitly reuses required same-candidate T10 positive split evidence with qualified content/box/rotation rules, avoiding an unsupported T03 claim. Recorder and inventory now agree on 75 cases.

3. **Publication storage and adjacent cleanup — resolved.** For “temporary-storage high-water behavior without resetting budgets between phases” and release of “target-adjacent staging,” `T20ResourceContracts.java:436` uses an authored uncompressed Source and exact snapshot-plus-live-file arithmetic, independent of backend cache capacity or quota searches. `:473` cancels only after nonempty adjacent staging is observed, checks both sentinels, and retains that observation. `:147` checks the owned root and every output directory after each Native experiment. Remaining successful T20 products receive all four qualified T03 PDF chains through `T20EvidenceCommand.java:43` and matching collector product closure.

4. **Shared-environment admission — resolved.** For “shared-environment concurrency,” `T20ResourceContracts.java:363` now uses distinct workflows sharing one environment for incoming/active ceilings and verifies admission after permit release.

The Native-only policy-control decision, cooperative guarantee, four IN_PROCESS tuple scope, existing encryption and excluded downstream work remain preserved. No tests were executed by this read-only reviewer.
