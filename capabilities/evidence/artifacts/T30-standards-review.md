# T30 independent Standards review — initial worktree

Hard violations: **0**. Optional observations: **1**.

Base: `5508429ec032e8879d6d359a018f25d25efe878c`; HEAD equals base and the commit list is empty. Reviewed `git diff 5508429ec032e8879d6d359a018f25d25efe878c --` and all untracked additions: 28 tracked modifications, 745 captured additions, and the subsequent scope manifest. Scope includes all 22 added Java files, the Python entry test, contracts, inventories, generated views, provenance, verification records, three PDFs and 672 PNGs. This report covers the initial worktree identified by source-manifest SHA-256 `6420f7d7243e94ced5bb6bc2480bf65d1bed9f8c0e2831f7c7ac4cca9e329765`.

Reviewed against AGENTS.md, CONTRIBUTING.md, CONTEXT.md, both agent guides, capabilities/README.md and ADRs 0002, 0009, 0010, 0020, 0022, 0023, 0025 and 0029. No documented-standard breach found. The private Okapi integration, Folio-owned drawing, public Workflow tests, dependency isolation, ownership/failure handling and separate inventories follow those rules. Independent standards evidence and compatible-status dependencies remain openly indeterminate; experimental status is preserved. No iText implementation material was inspected during this review.

Independent checks matched all 318 acceptance-source declarations, all 787 current build-input hashes, all 689 artifact-audit entries and 100 retained development-log hashes. Raw logs agree with the successful root verification and four JDK builds: 1083 tests each, zero failures/errors, four documented skips. Retained findings contain 224 path decodes, 224 raster decodes and 224 passing visual comparisons. All PNG dimensions match 2448×3168. Visual sampling of Worker page 104 and In-Process page 109 shows separated, visible captions and unclipped bars. These checks verify retained observations; they do not replace Spec review or standards-conformance evidence.

- **Possible Primitive Obsession — optional judgement call.** `pdf-acceptance/.../T30BarcodeOracle.java:42`, `T30BarcodeReference.java:29` and `T30PostalDecoder.java:14` carry bar geometry as positional `double[]`, including `bar[0] + bar[2]` and `bar[1] + bar[3]`. A small immutable rectangle value with named coordinates and dimensions would make independent geometry calculations easier to audit. Keep that value within acceptance code so it does not couple the oracle to product encoding. This is a readability suggestion, not a documented-standard violation.

No implementation changes or completion determinations were made by this reviewer.
