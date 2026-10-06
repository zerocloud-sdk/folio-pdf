# Standards source review — issue #81

Comparison baseline and HEAD: `05e7f546f5885680e333ad8d3dea3645b25d26b7`.
Reviewed `git diff 05e7f546f5885680e333ad8d3dea3645b25d26b7 --` plus the new
untracked T20 recorder, contracts, tests, collector, corpus, pins and guide.
This is the preliminary source review; final promotion and artifact-bound
certifications require the final delivery review.

**Hard violations: none found.**

- `T20ResourceContracts.java:172`, `T20FacadeContracts.java:28` and the recorder
  reuse in `T03EvidenceCommand.java:101` observe public Native/Facade behavior.
  They introduce no backend signature or artificial Facade policy control,
  consistent with `CONTRIBUTING.md`'s Development contract and ADR-0004,
  ADR-0013 and ADR-0040.
- `scripts/t20_foundation_reports.py:47`, `:71`, `:116` and `:165` check the
  closed inventory, retained bytes, frozen inputs and live replay. Original
  hashes remain retained; PDF IDs, verified PDF/raster hashes and observation
  paths are the declared normalization. Required PDF rules/controls reuse T03,
  while enforcement has its separate contract chain, consistent with ADR-0023.
  `scripts/t03-foundation.py:888`, `:1230` and `:1307` enforce five-chain scope
  and producer binding while preserving predecessors' execution defaults and
  invalidating stale identities.
- New Java uses Java 8 APIs and repository-only acceptance classes; no runtime
  dependency or product enforcement change is introduced (ADR-0006/0009).
  `scripts/generate-t20-corpus.py:15` authors project-owned PDF syntax and
  `PROVENANCE.md:3035` records origins, licenses and exposure consistent with
  `CONTRIBUTING.md`'s Clean-room provenance and ADR-0002. All 56 non-tool frozen
  inputs matched their pins at review.
- `docs/t20-certification.md:62` and `:69` preserve the actual Ubuntu/JDK scope,
  Native-only decision and cooperative guarantee (ADR-0016/0040); historical
  implementation statements are explicitly distinguished from certification.

**Optional smells: none warrant a change.** Finite dimension dispatch and
duplicated assertion helpers in these bounded acceptance experiments do not
justify a broader abstraction or coupling expectations to product accounting.

Review was read-only apart from this report; no validation gate is claimed by
this source review. Findings: **0 hard violations, 0 actionable optional smells**.
