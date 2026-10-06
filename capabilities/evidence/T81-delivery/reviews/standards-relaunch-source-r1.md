# Standards review — relaunch source and recovery

Baseline and HEAD: `05e7f546f5885680e333ad8d3dea3645b25d26b7`; no new
commits. Reviewed the existing dirty worktree with `git diff BASELINE --`
and inspected the new authored files named in
`validation/relaunch-review-inputs-r1.json`. All 30 recorded input hashes
matched at review completion. Manifest SHA-256:
`a79191f0e3dc1c5376b65286a813d9e13f9ea7e780bffd33e485297722cf5a4e`.

Standards sources: AGENTS.md, CONTRIBUTING.md, CONTEXT.md,
docs/agents/domain.md, ADR-0002/0006/0013/0016/0023/0025/0029/0040,
README.md, hostile-input and Foundation contracts, and PROVENANCE.md.
The Fowler smell baseline was considered as judgment guidance; tooling-enforced
matters were excluded.

**Findings: 0 documented-standard violations; 0 applicable smell findings.**

The T20 Java experiments use the public `DocumentWorkflow.execute` seam and
existing public Migration Facade operations, with independently authored
operands. Acceptance tools and observations remain in repository-only
infrastructure; no runtime dependency, backend public signature, unsupported
Facade policy API, or product implementation change is introduced. The
separate resource contract chain and explicit T03 reuse for resource-free PDF
outcomes follow ADR-0023. Public contracts retain Java 8 compatibility,
ownership, ordered publication, cooperative accounting and the exact
Ubuntu/JDK/IN_PROCESS scope. Provenance records the new code and fixtures.

The ticket-local attachment recovery retains five complete scopes and reruns
the interrupted JDK17 HARDENED_WORKER tuple, then both JDK21 tuples, in full.
It seals original files, compares observed environment/native identities,
requires unchanged candidate/staged identities, checks retained configuration
and producer bindings, and checks the complete 88-scope merge before using
the existing locked, guarded, atomic index publisher. Its bounded orchestration
does not replace the staged driver or weaken collection.

This is a source/recovery review before execution completes. Final
certification identities, validation gates, generated readiness and the
delivery receipt require the requested follow-up evidence review. No mandatory
test, tool, fixture or certification omission is endorsed by this report.
