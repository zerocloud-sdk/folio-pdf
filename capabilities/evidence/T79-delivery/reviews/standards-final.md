# Standards review — final implementation

Reviewed the worktree diff and new source files against `7420a656d27d17b82c7632be7c6214f8a657a22f`; HEAD still equals that baseline. **No unresolved documented-standard breaches or actionable smell-baseline findings were found.**

The three initial findings are resolved in the inspected implementation:

1. `PdfBoxPasswordSecurity.PreparedOutput.preflight` now calls `PdfBoxMetadataEncryption.requireOutputFilters` before caller work. The writer validates and normalizes each stream again before applying a new encryption policy, including streams introduced by caller work. `ClearMetadataPasswordWorkflowTest.explicitCryptSourcesCanRewriteUnderEitherSelectedScope` checks valid rewrites and malformed-source rejection with untouched callbacks/destinations and ordered receipts. This restores the preflight contract in `docs/pdf-version-password-security.md` and ADR-0025.

2. The T79 Arlington input/output patches route Catalog Metadata to `DocumentMetadata`, leaving component Metadata on its protected-stream model. Original component StdCF success and Identity/default-name rejection controls distinguish the roles; valid catalog Identity defaults remain admitted. This matches CM-CRYPT in `docs/research/T79-clear-metadata-profile-audit.md` and ADR-0023.

3. `scripts/t79-byte-check.py:98` classifies unexpected authenticated diagnostics as `unexpected-diagnostic`; `t79-observer.py` rejects every diagnostic other than `none`. Expected unauthenticated decoding failures remain separately recorded. Unknown warnings no longer become passing observations, consistent with `docs/t78-certification.md:102`.

`review-regressions-1.txt` and `review-worker-regressions-1.txt` both record BUILD SUCCESS: 31 baseline plus 14 clear-metadata Native tests, 11 baseline plus 7 clear-metadata Facade tests, and 7 product/coordinator tests, all without skips. The supplemental original all-content Metadata/StdCF checker limitation is explicitly documented in `docs/t79-certification.md`; it does not waive a required clear-metadata input or emitted-product chain.

This was a static re-review with retained regression-log inspection; the reviewer ran no builds. Final real-tool qualification/replay, full verification, JDK matrix, same-candidate certification, and final receipt remain delivery gates. ADR-0040 permits only the actually observed Ubuntu/JDK environments; this review establishes no additional certification.

**2026-09-30 addendum:** Reviewed the two-line `JarContractIT` delta after full verification exposed its obsolete twelve-field expectation. Adding the literal `DO_NOT_ENCRYPT_METADATA` and value `8` matches `capabilities/facade-surface.yaml:2170`. Exact field count, names, values, and modifiers remain checked; Preview shares this test source. No standards finding. No build was run by this reviewer; focused and full rerun results remain required.
