# T30 final independent review aggregation

Fixed base: `5508429ec032e8879d6d359a018f25d25efe878c`. Separate clean-context reviewers performed the two axes and their final follow-ups; the implementing agent only aggregated their reports. The user explicitly included staged, unstaged and untracked changes. No criterion was marked complete before both final reports arrived.

[Captured review scope](T30-final-review-scope.json) retains all 1512 pre-review changed-file hashes. The [Standards report](T30-standards-review-final.md) and [Spec report](T30-spec-review-final.md) are reproduced below without merging or reranking their findings. [Exact final reviewer probe source and output](T30-final-spec-probe-observations.txt) preserve the additional 16 direct FNC4 observations. Initial findings and focused follow-ups remain retained separately.

## Standards

Hard violations: **0**. Optional observations: **1**, unchanged from the initial review. **No documented-standard violation remains in the reviewed snapshot.**

Base and HEAD: `5508429ec032e8879d6d359a018f25d25efe878c`; no commits or staged files. Reviewed `git diff BASE --` and `git ls-files --others --exclude-standard`: 28 tracked modifications, 1484 snapshot additions and the subsequent scope manifest. All 1512 captured file hashes match. This reconciles the initial and remediation reports with current source, tests, documentation, inventories, generated views and retained evidence; those earlier reports remain intact.

Applied AGENTS.md, CONTRIBUTING.md, CONTEXT.md, both agent guides, capabilities/README.md, ADRs 0002/0009/0010/0020/0022/0023/0025/0029 and the full heuristic smell baseline. The private Okapi integration, Folio-owned drawing, public Workflow tests, isolated decoders, ownership/failure contracts and separate inventories remain consistent with those rules. Current inventories link the refreshed evidence and retain experimental status; historical observations are clearly identified. No iText implementation material was inspected.

Independent verification matched all 318 current acceptance-source hashes and all 787 build inputs. The latter manifest is `18bf5fd5285b998f0c27429aba5fa9f56ddf9fefdb0d6e92065b0520dd882826`. Raw root and JDK 8/11/17/21 logs each total 1085 tests, zero failures/errors and four documented skips. Inventory validate/generate/check receipts, logs and output hashes match current files.

All 713 current artifact hashes match; all 689 initial artifacts remain unchanged. Binary scope includes six PDFs and 1368 PNGs. Current findings retain 232 path decodes, 232 raster decodes, 230 captions and 232 passing AE-zero comparisons. Actual/reference PNG bytes match for every current page; dimensions and PDF identities match declarations. Sampling current Worker page 34 shows complete, separated, unclipped bars and caption.

- **Possible Primitive Obsession — optional judgement call:** `pdf-acceptance/.../T30BarcodeOracle.java:42`, `T30BarcodeReference.java:29` and `T30PostalDecoder.java:14` still carry geometry as positional `double[]`, including `bar[0] + bar[2]` and `bar[1] + bar[3]`. An acceptance-local immutable rectangle with named coordinates would improve auditability without coupling the oracle to product encoding.

The Standards code-review axis is closed for this snapshot. Independent PDF standards evidence, compatible Dependency Gates and Foundation certification remain open; this review supplies no compatibility promotion or separate Spec determination. No Maven reruns, implementation edits, external actions or completion-criterion changes were performed.

## Spec

**No unresolved spec gaps found; 0 scope issues. The original P1 is resolved.**

Reviewed `git diff 5508429ec032e8879d6d359a018f25d25efe878c --` and all untracked additions against #31, parent #1 and its comment, the full handoff, applicable domain/ADR contracts, and all 25 criterion-ledger rows. HEAD remains the fixed base, with no staged changes or subsequent commits. All 1,512 captured changed-file hashes match; the snapshot itself is the additional review metadata file.

The requirement “An independent decoder recovers each payload” now holds for the previously failing FNC4 numeric examples. `pdf-document/src/main/java/net/zerocloud/pdf/PdfBoxBarcodeOperations.java:133` retains the reviewed A/B selection repair. Its source hash is unchanged from the independent 240-case probe and eight both-profile controls, which passed. I additionally decoded the four final FNC4 fixtures from both retained PDFs and their actual PNGs: **16 successful observations**. Probe: `/tmp/folio-t30-final-spec-probe-8u1rkkjr/`.

- **Criteria 1–17:** The complete requested families and declared variants have public Workflow generation, documented input/check/size/text contracts, placement and failure/publication coverage in both profiles. Current evidence records 232 PDF-path decodes, 232 raster decodes and 230 labels. All 232 actual/reference PNG pairs are byte-identical. I verified 713 current artifact hashes, 689 preserved initial hashes and all 318 current source declarations. English/Javadoc/Chinese documentation, both inventories, provenance and notices agree; no decoder gap or unsupported facade stub remains.
- **Criteria 18–21:** Inventory validate/generate/check receipts and generated-file hashes match. Raw root and complete JDK 8/11/17/21 logs independently total **1085 tests per build, zero failures/errors, four documented skips**. Both unchanged 787-file manifests match current inputs. No Maven rerun was performed by this reviewer.
- **Criteria 22–25:** This completes the Spec axis. The reviewed diff is confined to T30, retained evidence and review records; commit restrictions are honored. Final ledger/user-report closeout follows the independent review results.

Closure covers #31’s experimental slice. Independent standards evidence, compatible-status Dependency Gates and Foundation platform/font certification remain explicitly INDETERMINATE; this review does not authorize compatibility promotion or publication.

Standards: 0 hard violations, 1 optional geometry-readability observation. Spec: 0 unresolved gaps, 0 scope issues; original P1 resolved.
