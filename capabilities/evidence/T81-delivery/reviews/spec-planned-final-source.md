# Spec precheck — planned final acceptance-source fixes

Baseline: `05e7f546f5885680e333ad8d3dea3645b25d26b7`.
Read-only review of `/tmp/t81-fix-observer-roles.py` and `/tmp/t81-fast-json.py` against the sole issue-81 contract. These patches have not been applied; this is not final certification review.

No remaining concrete blocker after correcting the optimizer's test-method extraction:

- For “Collection validates actual retained artifacts and observations,” the proposed driver helper appends exact hashed raw-file references to the existing environment-observations role. T20 uses it for both live before/after directories; normal certification uses it for original observations. The driver therefore preserves all three groups instead of overwriting the live references. The existing verifier still checks path containment and file hashes before exempting only raw-observer payload contents from repository-reference traversal.
- For “altered findings … cannot produce PASS,” the proposed regression composes before/after/original raw references containing container-local paths, proves successful retention, rejects changed and missing raw files, and rejects the same references when incorrectly placed in findings. No verifier exemption is expanded.
- JSON-first parsing changes only deserialization after the original reference/hash checks. `json.JSONDecodeError` alone triggers the unchanged `yaml.safe_load` fallback; traversal, cycle detection, identity/scope/chain/producer checks and failure handling remain intact. Existing missing-contract and changed-producer rejection controls run against both repository JSON and legacy YAML representations.
- The initial optimizer selected through the next corpus-test anchor and would have nested the newly inserted raw-role regression inside another test. Its corrected next-method boundary preserves both tests. In-memory composition compiles and exposes all ten test methods, including the standalone raw-role regression; this was structural validation, not test execution.
- These are bounded acceptance-infrastructure fixes for the demonstrated envelope failure and transitive-audit parsing cost. They change no resource policy, runtime API, product implementation, cooperative guarantee, Worker behavior or predecessor execution coverage.

Apply only after initial 92-scope acceptance and exact initial-source archival as planned. Changed source requires fresh staging, complete ordered predecessor refresh, all four limits tuples, actual collector gates and final baseline-relative review.
