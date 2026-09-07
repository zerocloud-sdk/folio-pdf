# T30 independent Standards review — final worktree

Hard violations: **0**. Optional observations: **1**, unchanged from the initial review. **No documented-standard violation remains in the reviewed snapshot.**

Base and HEAD: `5508429ec032e8879d6d359a018f25d25efe878c`; no commits or staged files. Reviewed `git diff BASE --` and `git ls-files --others --exclude-standard`: 28 tracked modifications, 1484 snapshot additions and the subsequent scope manifest. All 1512 captured file hashes match. This reconciles the initial and remediation reports with current source, tests, documentation, inventories, generated views and retained evidence; those earlier reports remain intact.

Applied AGENTS.md, CONTRIBUTING.md, CONTEXT.md, both agent guides, capabilities/README.md, ADRs 0002/0009/0010/0020/0022/0023/0025/0029 and the full heuristic smell baseline. The private Okapi integration, Folio-owned drawing, public Workflow tests, isolated decoders, ownership/failure contracts and separate inventories remain consistent with those rules. Current inventories link the refreshed evidence and retain experimental status; historical observations are clearly identified. No iText implementation material was inspected.

Independent verification matched all 318 current acceptance-source hashes and all 787 build inputs. The latter manifest is `18bf5fd5285b998f0c27429aba5fa9f56ddf9fefdb0d6e92065b0520dd882826`. Raw root and JDK 8/11/17/21 logs each total 1085 tests, zero failures/errors and four documented skips. Inventory validate/generate/check receipts, logs and output hashes match current files.

All 713 current artifact hashes match; all 689 initial artifacts remain unchanged. Binary scope includes six PDFs and 1368 PNGs. Current findings retain 232 path decodes, 232 raster decodes, 230 captions and 232 passing AE-zero comparisons. Actual/reference PNG bytes match for every current page; dimensions and PDF identities match declarations. Sampling current Worker page 34 shows complete, separated, unclipped bars and caption.

- **Possible Primitive Obsession — optional judgement call:** `pdf-acceptance/.../T30BarcodeOracle.java:42`, `T30BarcodeReference.java:29` and `T30PostalDecoder.java:14` still carry geometry as positional `double[]`, including `bar[0] + bar[2]` and `bar[1] + bar[3]`. An acceptance-local immutable rectangle with named coordinates would improve auditability without coupling the oracle to product encoding.

The Standards code-review axis is closed for this snapshot. Independent PDF standards evidence, compatible Dependency Gates and Foundation certification remain open; this review supplies no compatibility promotion or separate Spec determination. No Maven reruns, implementation edits, external actions or completion-criterion changes were performed.
