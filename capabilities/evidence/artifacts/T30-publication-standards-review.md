# T30 publication delta — independent Standards supplement

Hard violations: **0**. New optional findings: **0**. The earlier optional acceptance-geometry suggestion is unchanged and outside this delta.

Reviewed only the staged `.gitattributes` addition against the final delivery snapshot. Its three rules cover top-level `capabilities/evidence/T30-*.md`, `capabilities/evidence/artifacts/T30-*`, and `capabilities/evidence/T30-r2/**`. They follow the existing T29 evidence precedent: `-text` preserves exact bytes, while `whitespace=-blank-at-eol,-blank-at-eof` permits recorded tool whitespace. This supports ADR-0023's versioned evidence and reproducibility requirements without changing product behavior.

Independent `git check-attr --cached` inspection found 1463 matching T30 evidence files, all with text conversion disabled. No product source, tests, contracts, inventories or unrelated evidence acquire these exceptions. Existing PDF binary attributes and other file rules remain intact.

All 1517 files hashed by `T30-final-workspace-audit.json` still match their recorded bytes and staged blobs. The self-excluded audit file also matches its staged blob, accounting for all 1518 prior delivery files. Both artifact audits remain exact: 689 initial and 713 current entries. Earlier reviewer reports remain unchanged. `git diff --cached --check` passes. Reviewed `.gitattributes` SHA-256: `ab143a3ac0b06bbcfe13334500e03473b09a1f845f775e49e6557a27c8eba974`.

The user's later explicit authorization permits commit, GitHub push and closing #31. Earlier no-publication statements, unstaged-worktree identities and build manifests describe the pre-publication verification snapshot; they remain preserved historical records. This supplement covers the subsequent Git-attribute change and does not imply compatibility promotion.

No documented-standard violation remains in this publication delta. No source edits, Maven commands or external actions were performed by this reviewer.
