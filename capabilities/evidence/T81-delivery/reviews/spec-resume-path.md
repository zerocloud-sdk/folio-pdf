# Spec assessment — predecessor resume path

Baseline and observed HEAD: `05e7f546f5885680e333ad8d3dea3645b25d26b7`.
Prospective read-only assessment against `/workspace/contracts/issue-81-contract.md`; no final-certification claim.

**Permitted with the existing checks preserved.** “Refresh already certified predecessors … for the same candidate” and “Use fresh directories and preserve historical evidence” require complete current evidence and intact history; they do not require repeating passing tuples after another tuple fails. Continuing password-attachments before limits preserves the specified obligation order.

The three original scopes under `capabilities/evidence/foundation/T81-initial/password-attachments-r2` contain passing 24-test transcripts and all four original chain records. Each records candidate `c8a23d678179bceabc88f6f93bad21a8dd4f35aed46450fdf3fdaa85e7ca1271`, contract `7d25974dee348230484a7536a764c81b8beec69ae9cd13e322f74a97ea77f6ee`, its original environment hash, and unchanged qpdf-t80-r1/arlington/folio-pdf-t80/pdfium-cli producers. This inspection is not a transitive hash audit.

The safe execution path must retain these safeguards:

- Keep original paths, commands, configurations, reports and failures intact. The standalone failed-suite replay is diagnostic; the failed tuple still needs its complete successful suite, recorder and collection in a fresh scope. Never reuse its failed transcript as certification.
- Use `require_staged_build`, `candidate_identities`, `certification_inputs`, `execution_plan`, `run_logged`, `observe_environment`, `collect_reports` and normal record construction as in `scripts/t03-foundation.py:1247`. Preserve original scope-specific command/configuration hashes for the three retained tuples. Verify full input closure and current Candidate/Contract identities.
- Repeat actual environment observation around resumed work and require equality with the retained environment records. JDK8 has its original after-observation; JDK11 currently lacks one because certification stopped during Worker tests. Complete that unchanged driver check before retaining JDK11 IN_PROCESS.
- Assemble exactly the Foundation-declared four environments × IN_PROCESS/HARDENED_WORKER, each with four chains. Explicitly compare the expected eight scope keys: `publish_index` checks uniqueness and transitive integrity but does not independently require all eight scopes.
- Use unchanged `publish_index`/`merge_evidence` for complete transitive hash/reference/identity verification and the stale-authority guard; retain the resulting audit and live inventory result. Restoring identical pinned cache bytes does not change bound identity; changed identities require fresh affected evidence.

No source edits or tests executed. All four limits tuples remain required after the complete predecessor refresh.
